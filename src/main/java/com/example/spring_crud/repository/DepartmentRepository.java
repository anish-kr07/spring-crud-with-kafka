package com.example.spring_crud.repository;

import com.example.spring_crud.entity.Department;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DepartmentRepository extends JpaRepository<Department, Long> {

    // Same name as JpaRepository.findAll() — Spring Data uses THIS one because
    // the @EntityGraph adds the fetch hint "also fetch employees in the same SELECT".
    //
    // Why this matters (the N+1 problem — most-asked JPA interview question):
    //   Without this override:
    //     1. SELECT * FROM departments              <- 1 query
    //     2. SELECT * FROM employees WHERE dept_id=?  <- 1 query PER department
    //   For 100 departments that's 101 queries.
    //
    //   With @EntityGraph(attributePaths = "employees"):
    //     1. SELECT d.*, e.* FROM departments d LEFT JOIN employees e ON ...
    //   ONE query, regardless of department count.
    //
    // Three ways to fix N+1 (know all of them):
    //   1. @EntityGraph (this — declarative, type-safe, JPA standard)
    //   2. JPQL with JOIN FETCH:
    //        @Query("select d from Department d left join fetch d.employees")
    //   3. @BatchSize(size=N) on the collection (Hibernate-specific; loads
    //      lazy collections in batches of N rather than 1 at a time)
    @Override
    @EntityGraph(attributePaths = "employees")
    List<Department> findAll();

    // Bonus: same EntityGraph for single-fetch.
    @EntityGraph(attributePaths = "employees")
    Optional<Department> findWithEmployeesById(Long id);

    Optional<Department> findByName(String name);
}
