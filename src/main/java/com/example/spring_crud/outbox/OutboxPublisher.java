package com.example.spring_crud.outbox;

import com.example.spring_crud.entity.OutboxEvent;
import com.example.spring_crud.repository.OutboxEventRepository;
import tools.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

// The service-side half of the transactional outbox.
//
// Called from EmployeeService.create/update/patch/delete to record a domain
// event. The Kafka send happens later, in OutboxRelay's scheduled poller.
//
// Propagation.MANDATORY is the key bit:
//   - "I REQUIRE an existing transaction; throw if there isn't one."
//   - Guarantees this call lives inside the caller's @Transactional, so the
//     outbox INSERT is committed atomically with the Employee write.
//   - If we used REQUIRED (the default), and a developer forgot @Transactional
//     on the caller, this would silently start its own tx and we'd lose
//     atomicity. MANDATORY makes that mistake fail-loud.
@Component
public class OutboxPublisher {

    private final OutboxEventRepository repository;
    private final ObjectMapper objectMapper;

    public OutboxPublisher(OutboxEventRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(String aggregateType, String aggregateId,
                       String eventType, Object payload) {
        OutboxEvent event = new OutboxEvent();
        event.setAggregateType(aggregateType);
        event.setAggregateId(aggregateId);
        event.setEventType(eventType);
        event.setPayload(toJson(payload));
        repository.save(event);
    }

    private String toJson(Object payload) {
        // Jackson 3's writeValueAsString throws unchecked exceptions
        // (tools.jackson.core.JacksonException extends RuntimeException),
        // so no try/catch needed here. If serialization fails, the runtime
        // exception bubbles up and rolls back the @Transactional boundary
        // — which is exactly what we want (no half-written state).
        return objectMapper.writeValueAsString(payload);
    }
}
