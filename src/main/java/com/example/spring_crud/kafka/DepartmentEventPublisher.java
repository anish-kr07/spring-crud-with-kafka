package com.example.spring_crud.kafka;

import com.example.spring_crud.entity.Department;
import com.example.spring_crud.event.DepartmentEvent;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

// Thin wrapper around KafkaTemplate for department events. Sits beside the
// service layer (not inside it) so the service stays focused on domain logic
// and the Kafka plumbing lives in one place.
//
// Async fire-and-forget — failures are logged and dropped. NOT suitable for
// state-change events that must not be lost (those should use the outbox
// pattern, see EmployeeService + OutboxRelay). For now we accept the trade-off
// for DepartmentCreated too: simpler code, but events lost if Kafka is down.
@Component
public class DepartmentEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(DepartmentEventPublisher.class);

    // Event-type strings — kept private + named-method public API so callers
    // can't typo them at the call site.
    private static final String EVENT_LOOKED_UP = "DepartmentLookedUp";
    private static final String EVENT_CREATED   = "DepartmentCreated";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public DepartmentEventPublisher(KafkaTemplate<String, String> kafkaTemplate,
                                    ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    // Public API — named methods make call sites self-documenting.
    public void publishLookup(Department dept) {
        publish(dept, EVENT_LOOKED_UP);
    }

    public void publishCreated(Department dept) {
        publish(dept, EVENT_CREATED);
    }

    // Shared implementation. Builds the event, serializes, sends async with
    // a failure-logging callback. Same pattern for every event type.
    private void publish(Department dept, String eventType) {
        DepartmentEvent event = new DepartmentEvent(
                dept.getId(), dept.getName(), Instant.now());

        String payload;
        try {
            payload = objectMapper.writeValueAsString(event);
        } catch (Exception e) {
            log.warn("Failed to serialize DepartmentEvent type={} id={}", eventType, dept.getId(), e);
            return;
        }

        // Department id as the message KEY -> all events for the same
        // department land on the same partition -> per-aggregate ordering.
        ProducerRecord<String, String> record = new ProducerRecord<>(
                KafkaTopics.DEPARTMENT_EVENTS,
                String.valueOf(dept.getId()),
                payload);

        record.headers().add("event-type", eventType.getBytes(StandardCharsets.UTF_8));

        // Async send. The caller (e.g. DepartmentService.create or .findById)
        // returns to the user without waiting for the broker. If Kafka is down,
        // the event is logged and dropped — no impact on the API response.
        kafkaTemplate.send(record).whenComplete((result, ex) -> {
            if (ex != null) {
                log.warn("Failed to publish department event type={} id={}", eventType, dept.getId(), ex);
            }
        });
    }
}
