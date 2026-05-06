package com.example.spring_crud.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

// Transactional outbox row. Written in the SAME @Transactional as the
// business write (Employee insert/update/delete), so the event row is
// committed iff the business change is committed. This is what makes the
// pattern atomic — no dual-write to two systems.
//
// Lifecycle:
//   1. EmployeeService.create(...)  -> save(employee) + outboxPublisher.record(...)
//                                      both in one tx -> committed atomically
//   2. OutboxRelay (scheduled poller) -> reads unpublished rows, sends to Kafka,
//                                        sets publishedAt
//
// The index on (published_at, created_at) makes the relay's
// "find oldest unpublished" query an index range scan instead of a full table scan.
@Entity
@Table(name = "outbox_events", indexes = {
        @Index(name = "idx_outbox_unpublished", columnList = "published_at, created_at")
})
@Getter
@Setter
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // What domain entity this event is about — "Employee", "Department", etc.
    @Column(name = "aggregate_type", nullable = false, length = 64)
    private String aggregateType;

    // The id of that aggregate as a String. String (not Long) so this table can
    // serve any aggregate type, regardless of id strategy (long, UUID, etc.).
    // Used as the Kafka MESSAGE KEY -> all events for the same employee land
    // on the same partition -> ordering preserved per employee.
    @Column(name = "aggregate_id", nullable = false, length = 64)
    private String aggregateId;

    // "EmployeeCreated" / "EmployeeUpdated" / "EmployeeDeleted" / etc.
    // Sent as a Kafka header so consumers can route without parsing the body.
    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    // JSON payload. @Lob => CLOB column; large payloads don't bloat the row.
    @Lob
    @Column(name = "payload", nullable = false)
    private String payload;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    // Null = not yet published. Set to now() once the broker acks the send.
    // Older entries can be purged by a separate cleanup job.
    @Column(name = "published_at")
    private Instant publishedAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
