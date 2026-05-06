package com.example.spring_crud.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

// Why @Getter/@Setter and NOT @Data on a JPA entity:
// @Data generates equals/hashCode using ALL fields. For JPA entities this is a trap:
//   - Before persist, id is null; after persist, id is assigned -> hashCode changes,
//     so the same entity in a HashSet "disappears".
//   - Lazy associations get touched by equals/hashCode -> surprise SQL or
//     LazyInitializationException.
// Safe options: id-based equals/hashCode, or skip them entirely (as we do here).
@Entity
@Table(name = "employees")
@Getter
@Setter
public class Employee {

    // IDENTITY = DB auto-increment column. Simple, but Hibernate cannot batch
    // inserts because it needs the generated id back per row. For high-throughput
    // inserts, prefer SEQUENCE (Postgres) with an allocationSize > 1.
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    // @Column(name = "...") decouples the Java field name from the DB column name.
    // Without it, Hibernate uses the field name with its naming strategy
    // (default = camelCase -> snake_case, so firstName -> first_name).
    // Being explicit makes the schema unambiguous and survives field renames.
    @Column(name = "first_name", nullable = false, length = 50)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 50)
    private String lastName;

    // unique=true adds a UNIQUE constraint at the DDL level. App-level uniqueness
    // checks are still racy under concurrency — rely on the DB constraint and
    // translate the resulting DataIntegrityViolationException in the handler.
    @Column(name = "email", nullable = false, unique = true, length = 100)
    private String email;

    // OWNING side of Department <-> Employee. The FK column DEPARTMENT_ID lives on
    // this table because Employee is the "many" side.
    //
    // fetch = LAZY is CRITICAL on @ManyToOne. The default is EAGER — every
    // employee load would also fire a SELECT on departments. With LAZY, the
    // department is only loaded when getDepartment().getName() is called.
    //
    // @JoinColumn(name="department_id"): the FK column name. Without it Hibernate
    // would default to "department_id" anyway via naming strategy, but being
    // explicit survives field renames.
    //
    // No cascade here: an Employee shouldn't drag a Department into persistence.
    // Cascade lives on Department.employees (the parent side).
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id")
    private Department department;

    // BigDecimal would be more correct for money (no float rounding), but int keeps
    // the demo simple. Real-world rule: NEVER use float/double for currency.
    @Column(name = "salary", nullable = false)
    private int salary;

    // LocalDate -> SQL DATE. Hibernate handles java.time types natively since 6.x;
    // no need for @Temporal (that was for java.util.Date).
    @Column(name = "join_date", nullable = false)
    private LocalDate joinDate;

    @Column(name = "date_of_birth", nullable = false)
    private LocalDate dateOfBirth;

    // @Transient = not persisted, not a column. Computed value derived from
    // other fields. Useful for view-model-ish getters that aren't worth a DTO.
    // field/getter is ignored by JPA. Different from transient (Java keyword for serialization).
    @Transient
    public String getFullName() {
        return firstName + " " + lastName;
    }

    // Helper for DTO mapping. Returns null if no department assigned, otherwise
    // the name. Touching department.getName() initializes the lazy proxy — this
    // is safe ONLY when called inside a @Transactional method (persistence
    // context still open). With open-in-view=false, calling this from a
    // controller would throw LazyInitializationException.
    @Transient
    public String getDepartmentName() {
        return department == null ? null : department.getName();
    }
}
