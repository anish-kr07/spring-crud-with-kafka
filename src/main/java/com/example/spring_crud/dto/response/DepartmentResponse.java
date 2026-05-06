package com.example.spring_crud.dto.response;

import com.example.spring_crud.entity.Department;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

// Outbound DTO. Includes nested employees so GET /v1/departments returns
// "department -> employees" already grouped — that's the whole point of having
// a Department endpoint.
//
// Why a List<EmployeeResponse> here instead of List<Employee>:
//   - Same DTO-vs-entity rule as elsewhere: never serialize JPA entities directly.
//     Lazy proxies serialize as garbage; serializing touches the persistence
//     context which may be closed by the time Jackson runs.
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DepartmentResponse {

    private Long id;
    private String name;
    private List<EmployeeResponse> employees;

    // Static factory. Must be called inside the @Transactional method that
    // loaded the department (open-in-view=false) — getEmployees() initializes
    // the lazy collection. With the EntityGraph fetch in the repository,
    // employees are already loaded, so iterating doesn't trigger N+1.
    public static DepartmentResponse from(Department d) {
        List<EmployeeResponse> employees = d.getEmployees().stream()
                .map(EmployeeResponse::getEmployee)
                .toList();
        return new DepartmentResponse(d.getId(), d.getName(), employees);
    }
}
