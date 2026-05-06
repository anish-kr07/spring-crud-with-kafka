package com.example.spring_crud.config;

import com.example.spring_crud.entity.Department;
import com.example.spring_crud.entity.Employee;
import com.example.spring_crud.repository.DepartmentRepository;
import com.example.spring_crud.repository.EmployeeRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.time.LocalDate;
import java.util.List;

// Seeding strategies in Spring Boot:
//   1. data.sql / data-h2.sql in src/main/resources (set spring.jpa.defer-datasource-initialization=true
//      so Hibernate creates tables BEFORE Spring runs the SQL).
//   2. CommandLineRunner / ApplicationRunner bean (this file). Type-safe, uses your repository,
//      easy to gate by @Profile so prod doesn't get seed data.
//   3. Flyway / Liquibase migrations — production-grade. Versioned schema + data.
//
// We chose CommandLineRunner because it shows two extra Spring concepts:
// CommandLineRunner (run after context is up) and @Profile (env-aware beans).
@Configuration
public class DataSeeder {

    // @Profile({"dev", "test"}) — bean is created when EITHER profile is active.
    // Other forms worth knowing:
    //   @Profile("!prod")           -> active for any profile except prod
    //   @Profile("dev & local")     -> requires BOTH (Spring 5.1+ expression syntax)
    //   @Profile("dev | local")     -> either (alternative syntax to comma list)
    // CommandLineRunner runs AFTER the application context is fully initialized.
    @Bean
    @Profile({"dev", "test"})
    public CommandLineRunner seedData(DepartmentRepository departments,
                                      EmployeeRepository employees) {
        return args -> {
            // Idempotent: only seed if both tables are empty.
            if (departments.count() > 0 || employees.count() > 0) {
                return;
            }

            // 1) Create departments first. CascadeType.ALL on Department.employees
            //    means we COULD attach employees here and a single saveAll(departments)
            //    would persist both. We do it the explicit way below for clarity.
            Department engineering = new Department();
            engineering.setName("Engineering");

            Department research = new Department();
            research.setName("Research");

            Department finance = new Department();
            finance.setName("Finance");

            departments.saveAll(List.of(engineering, research, finance));

            // 2) Build employees and link to their department via the OWNING side
            //    (Employee.department). The bidirectional helper on Department
            //    keeps both sides of the in-memory graph consistent.
            Employee a = new Employee();
            a.setFirstName("Ada");
            a.setLastName("Lovelace");
            a.setEmail("ada@example.com");
            a.setSalary(120000);
            a.setJoinDate(LocalDate.of(2022, 3, 15));
            a.setDateOfBirth(LocalDate.of(1990, 12, 10));
            engineering.addEmployee(a);

            Employee b = new Employee();
            b.setFirstName("Linus");
            b.setLastName("Torvalds");
            b.setEmail("linus@example.com");
            b.setSalary(150000);
            b.setJoinDate(LocalDate.of(2021, 8, 1));
            b.setDateOfBirth(LocalDate.of(1985, 6, 28));
            engineering.addEmployee(b);

            Employee c = new Employee();
            c.setFirstName("Grace");
            c.setLastName("Hopper");
            c.setEmail("grace@example.com");
            c.setSalary(135000);
            c.setJoinDate(LocalDate.of(2023, 1, 10));
            c.setDateOfBirth(LocalDate.of(1992, 9, 9));
            research.addEmployee(c);

            employees.saveAll(List.of(a, b, c));
            System.out.println(">>> Seeded " + departments.count() + " departments, "
                    + employees.count() + " employees");
        };
    }
}
