package com.example.spring_crud.kafka;

// Single source of truth for topic names. Both the producer relay and the
// consumer listener should reference these constants — typo-safe.
public final class KafkaTopics {

    public static final String EMPLOYEE_EVENTS = "employee.events";
    public static final String DEPARTMENT_EVENTS = "department.events";

    private KafkaTopics() {}
}
