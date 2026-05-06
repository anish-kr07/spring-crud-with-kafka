package com.example.spring_crud.kafka;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

// Demo consumer. In a real system this is where you'd:
//   - update a read model / projection
//   - send a notification (welcome email, slack ping)
//   - emit a metric
//   - trigger a downstream workflow
// Anything that should happen "in reaction to" employee changes WITHOUT
// being on the synchronous request path.
//
// @KafkaListener wires this method to the consumer container Spring
// auto-configured from application.properties. It runs on a dedicated thread,
// not on the HTTP thread.
@Component
public class EmployeeEventListener {

    private static final Logger log = LoggerFactory.getLogger(EmployeeEventListener.class);

    @KafkaListener(topics = KafkaTopics.EMPLOYEE_EVENTS, groupId = "spring-crud")
    public void onEvent(ConsumerRecord<String, String> record) {
        String eventType = headerValue(record, "event-type");
        log.info("[consumer] event-type={} key={} partition={} offset={} payload={}",
                eventType, record.key(), record.partition(), record.offset(), record.value());

        // Idempotency hook — in a real consumer:
        //   1. Read the outbox-id header (or use partition+offset).
        //   2. Check a "processed_events" table.
        //   3. Skip if already processed; otherwise process + mark.
    }

    private String headerValue(ConsumerRecord<?, ?> record, String name) {
        Header h = record.headers().lastHeader(name);
        return h == null ? null : new String(h.value(), StandardCharsets.UTF_8);
    }
}
