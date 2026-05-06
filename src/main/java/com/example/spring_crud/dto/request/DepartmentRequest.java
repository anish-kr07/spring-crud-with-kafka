package com.example.spring_crud.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;

// Minimal create payload — name only. Department.employees is built up via
// the Employee endpoints (set departmentId on POST /v1/employees).
@Data
@NoArgsConstructor
public class DepartmentRequest {

    @NotBlank
    @Size(max = 100)
    private String name;
}
