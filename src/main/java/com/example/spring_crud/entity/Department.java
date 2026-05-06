package com.example.spring_crud.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

// One Department -> Many Employees.
//
// Owning vs inverse side (the most-asked OneToMany interview topic):
//   - The OWNING side is the one that holds the foreign key column.
//     Here that's Employee (it has DEPARTMENT_ID).
//   - This side (Department) is the INVERSE side. We declare it with
//     mappedBy = "department" to tell JPA "look at Employee.department for the FK,
//     I'm just a mirror of it".
//   - Forgetting mappedBy is the classic trap: JPA assumes BOTH sides own the
//     relationship and creates a JOIN TABLE (department_employees) — extra table,
//     extra inserts, no one wants this.
//
// cascade = CascadeType.ALL:
//   - persist/merge/remove on Department flow to its Employees.
//   - Convenient on a true parent-child relationship like this one. Avoid on
//     associations where the "child" has its own lifecycle (e.g. Employee -> Project
//     where projects can outlive employees).
//
// orphanRemoval = true:
//   - If you do dept.getEmployees().remove(emp), that employee is DELETED.
//   - Different from cascade=REMOVE: that only deletes children when the parent
//     itself is deleted. orphanRemoval also deletes children when they are
//     unlinked from the collection.
//
// Initialize the collection field to new ArrayList<>():
//   - Avoids NullPointerException on getEmployees().add(...).
//   - Hibernate replaces the field with its own PersistentBag wrapper after load,
//     but pre-load (and on transient instances) the field needs a real list.
//
// Why @Getter/@Setter and NOT @Data — same reason as Employee: @Data's
// equals/hashCode would walk the lazy 'employees' collection on every comparison,
// which can trigger SQL or LazyInitializationException.
@Entity
@Table(name = "departments")
@Getter
@Setter
public class Department {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    // unique=true: department names are user-facing identifiers. Constraint at
    // the DB level, not just the app level — race-safe under concurrent inserts.
    @Column(name = "name", nullable = false, unique = true, length = 100)
    private String name;

    @OneToMany(
            mappedBy = "department",
            cascade = CascadeType.ALL,
            orphanRemoval = true)
    private List<Employee> employees = new ArrayList<>();

    // Bidirectional helper — keeps both sides in sync. Without this, you can
    // set employee.setDepartment(dept) but forget dept.getEmployees().add(emp),
    // and the in-memory object graph is inconsistent until the next reload.
    // Hibernate persists from the OWNING side (Employee), so the DB ends up
    // correct either way — but the Java object graph wouldn't be.
    public void addEmployee(Employee e) {
        employees.add(e);
        e.setDepartment(this);
    }

    public void removeEmployee(Employee e) {
        employees.remove(e);
        e.setDepartment(null);
    }
}
