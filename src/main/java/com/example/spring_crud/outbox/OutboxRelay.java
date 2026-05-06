package com.example.spring_crud.outbox;

import com.example.spring_crud.entity.OutboxEvent;
import com.example.spring_crud.kafka.KafkaTopics;
import com.example.spring_crud.repository.OutboxEventRepository;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Limit;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

// The relay: every second, find unpublished outbox rows and push them to Kafka.
//
// Why this is "at-least-once":
//   - We send to Kafka, then mark the row published. If the JVM dies between
//     the broker ack and the mark-published commit, the next poll re-sends.
//   - Consumers MUST be idempotent. Use the message key + offset + your own
//     dedup table to handle this in real systems.
//
// Why this is the textbook event-publishing pattern:
//   - The outbox row was committed atomically with the business write.
//   - No dual-write to two systems (DB + Kafka) at the same time.
//   - If Kafka is down, business writes still succeed; rows pile up; the
//     relay catches up when Kafka returns.
//
// Alternative: Debezium / CDC. Instead of polling, you stream the database
// WAL/binlog into Kafka. Same outcome, no app code, but operationally heavier.
@Component
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);
    private static final int BATCH_SIZE = 50;
    private static final long SEND_TIMEOUT_SECONDS = 10;

    private final OutboxEventRepository repository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public OutboxRelay(OutboxEventRepository repository,
                       KafkaTemplate<String, String> kafkaTemplate) {
        this.repository = repository;
        this.kafkaTemplate = kafkaTemplate;
    }

    // fixedDelay = "wait this long AFTER the previous run finishes"
    // (vs fixedRate which is "every N ms regardless of duration").
    // fixedDelay is safer — no overlapping runs piling up if Kafka is slow.
    //
    // @Transactional: the SELECT FOR UPDATE in the repository requires it.
    // Also: if any send fails inside the loop, the whole tx rolls back, so
    // ALL marks-as-published are undone -> next poll retries the whole batch.
    //
    // DEV-ONLY: slowed to 60s so you have time to inspect the outbox_events
    // table (published_at = NULL) before the relay drains it. Set back to
    // 1000ms for normal operation.
    @Scheduled(fixedDelay = 60_000)
    @Transactional
    public void publishPending() {
        List<OutboxEvent> batch = repository.findUnpublishedForUpdate(Limit.of(BATCH_SIZE));
        if (batch.isEmpty()) return;

        for (OutboxEvent e : batch) {
            ProducerRecord<String, String> record = new ProducerRecord<>(
                    KafkaTopics.EMPLOYEE_EVENTS, e.getAggregateId(), e.getPayload());

            // Headers carry metadata so consumers can dispatch without parsing
            // the body. Standard pattern: event-type, aggregate-type, source.
            record.headers().add("event-type", e.getEventType().getBytes(StandardCharsets.UTF_8));
            record.headers().add("aggregate-type", e.getAggregateType().getBytes(StandardCharsets.UTF_8));
            record.headers().add("outbox-id", String.valueOf(e.getId()).getBytes(StandardCharsets.UTF_8));

            try {
                // .get(timeout) — synchronously wait for broker ack. Blocks the
                // poller thread, but we want to KNOW the broker has it before
                // marking published. With acks=all, "ack" means all in-sync
                // replicas wrote it.
                kafkaTemplate.send(record).get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                e.setPublishedAt(Instant.now());
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted publishing outbox id=" + e.getId(), ie);
            } catch (ExecutionException | TimeoutException ex) {
                log.error("Kafka send failed for outbox id={} - tx will rollback, will retry next poll", e.getId(), ex);
                // Throw -> tx rolls back -> publishedAt updates are undone for
                // earlier successful sends in this batch. Those will be re-sent
                // on the next poll (at-least-once). Consumers must be idempotent.
                throw new RuntimeException("Kafka send failed", ex);
            }
        }
        log.info("Outbox relay published {} events", batch.size());
    }
}
