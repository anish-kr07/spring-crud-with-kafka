package com.example.spring_crud.repository;

import com.example.spring_crud.entity.Employee;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

// JpaRepository<Employee, Long> gives us save/findById/findAll/delete/count for free
// via a runtime proxy. We only declare the custom finders below.
public interface EmployeeRepository extends JpaRepository<Employee, Long> {

    // Derived query: Spring Data parses the method NAME into JPQL at startup.
    // Naming: findBy<Field>[Operator]. Examples:
    //   findByEmailIgnoreCase, findByDepartmentAndLastName,
    //   countByDepartment, existsByEmail, deleteByDepartment.
    List<Employee> findByDepartment(String department);

    // Optional<T> for unique lookups -> service uses orElseThrow(NotFoundException::new).
    Optional<Employee> findByEmail(String email);

    // Derived form of "email = ? AND salary > ?". Prefer this over @Query when readable.
    List<Employee> findByEmailAndSalaryGreaterThan(String email, int salary);

    // Same logic via JPQL. Use @Query when the derived name would be unreadable
    // (joins, projections, complex WHERE).
    // JPQL gotchas:
    //   - 'select e' (entity alias), NOT 'select *' (that's SQL).
    //   - Entity name is case-sensitive ('Employee', not 'employees').
    //   - :name binds to method param of same name; @Param makes it explicit & safer.
    @Query("select e from Employee e where e.email = :email and e.salary > :minSalary")
    List<Employee> searchByEmailAboveSalary(@Param("email") String email,
                                            @Param("minSalary") int minSalary);

    @Query("select e from Employee e where e.email = :email and e.salary > :minSalary")
    List<Employee> searchByEmailAboveSalaryPaged(@Param("email") String email,
                                                 @Param("minSalary") int minSalary,
                                                 Pageable pageable);
}
