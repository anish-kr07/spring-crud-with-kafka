package com.example.spring_crud.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

// Generic envelope for any "something happened to a Department" event.
// The event TYPE (Created, LookedUp, Renamed, ...) is carried in the Kafka
// header "event-type" — this class only carries the data shape, which is
// identical across types: which department + when.
//
// @Data is fine on a DTO / event (the trap is only on JPA entities).
// @NoArgsConstructor is needed by Jackson to deserialize JSON.
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DepartmentEvent {

    private Long departmentId;
    private String departmentName;
    private Instant occurredAt;
}
