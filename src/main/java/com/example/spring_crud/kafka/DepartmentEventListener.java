package com.example.spring_crud.kafka;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

// Consumer for the department.events topic. Single topic, but multiple
// event types share it — we dispatch on the "event-type" header instead of
// having one topic per type. (Same idiom as the rest of the codebase.)
//
// EmployeeEventListener handles employee.events independently — keeping the
// two listeners separate avoids the same-group / same-topic partition split
// that would happen if both subscribed to employee.events.
@Component
public class DepartmentEventListener {

    private static final Logger log = LoggerFactory.getLogger(DepartmentEventListener.class);

    @KafkaListener(topics = KafkaTopics.DEPARTMENT_EVENTS, groupId = "spring-crud")
    public void onEvent(ConsumerRecord<String, String> record) {
        String eventType = headerValue(record, "event-type");

        // Dispatch by event type. Each branch can do something different
        // (notify, project, audit). For now we just log a recognizable
        // message per type.
        switch (eventType == null ? "" : eventType) {
            case "DepartmentLookedUp" ->
                    log.info("findByDeprtId was called — key={} partition={} offset={} payload={}",
                            record.key(), record.partition(), record.offset(), record.value());
            case "DepartmentCreated" ->
                    log.info("Department created — key={} partition={} offset={} payload={}",
                            record.key(), record.partition(), record.offset(), record.value());
            default ->
                    log.warn("[consumer] unknown department event-type={} key={} payload={}",
                            eventType, record.key(), record.value());
        }
    }

    private String headerValue(ConsumerRecord<?, ?> record, String name) {
        Header h = record.headers().lastHeader(name);
        return h == null ? null : new String(h.value(), StandardCharsets.UTF_8);
    }
}
