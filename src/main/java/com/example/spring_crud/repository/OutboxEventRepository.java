package com.example.spring_crud.repository;

import com.example.spring_crud.entity.OutboxEvent;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    // Find next batch of unpublished events, ordered oldest-first.
    //
    // Why the lock:
    //   With multiple JVM instances of this app, two pollers might read the
    //   same rows at the same time -> double-publish. PESSIMISTIC_WRITE acquires
    //   a row lock (SELECT ... FOR UPDATE in SQL).
    //
    // Why this isn't perfect for multi-instance YET:
    //   PESSIMISTIC_WRITE alone makes other transactions WAIT, not skip. With
    //   N pollers, only one runs at a time -> serialized.
    //   The textbook solution is "SELECT ... FOR UPDATE SKIP LOCKED" (Postgres,
    //   MySQL 8+, H2 supports it via Hibernate 6's lock options). Hibernate's
    //   way to enable it: hint "jakarta.persistence.lock.timeout" = "-2"
    //   (LockOptions.SKIP_LOCKED). Add when you go multi-instance:
    //
    //     @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    //
    //   For our single-JVM dev setup, the plain pessimistic lock is enough.
    //
    // Limit (Spring Data 3.2+) is a fluent way to bound the result set without
    // declaring a Pageable.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from OutboxEvent e where e.publishedAt is null order by e.createdAt asc")
    List<OutboxEvent> findUnpublishedForUpdate(Limit limit);
}
