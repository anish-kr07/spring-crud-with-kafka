package com.example.spring_crud.dto.response;

import com.example.spring_crud.common.DobFormat;
import com.example.spring_crud.entity.Employee;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

// Outbound DTO — what we send back in JSON.
// Keep it separate from EmployeeRequest because:
//   - Response carries fields the client never sends (id, fullName).
//   - You can add fields here (e.g. createdAt) without affecting the input contract.
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EmployeeResponse {

    private Long id;
    private String firstName;
    private String lastName;
    private String fullName;
    private String email;

    // Department is denormalized into id + name on the response. Two reasons:
    //   - Clients usually want both: id for follow-up calls, name for display.
    //   - Avoids nesting a DepartmentResponse here, which would explode payload
    //     size on list endpoints. If clients need the full department, they call
    //     GET /v1/departments/{id}.
    private Long departmentId;
    private String departmentName;

    private int salary;
    private LocalDate joinDate;

    // Same meta-annotation on the way out — DOB serialized as "dd-MM-yyyy".
    @DobFormat
    private LocalDate dateOfBirth;

    // Static factory keeps mapping logic with the type that knows the shape.
    // CRITICAL: must run inside the @Transactional service method that loaded
    // the Employee, otherwise getDepartment() throws LazyInitializationException
    // (open-in-view=false). EmployeeService methods are all @Transactional.
    public static EmployeeResponse getEmployee(Employee e) {
        return new EmployeeResponse(
                e.getId(),
                e.getFirstName(),
                e.getLastName(),
                e.getFullName(),
                e.getEmail(),
                e.getDepartment() == null ? null : e.getDepartment().getId(),
                e.getDepartmentName(),
                e.getSalary(),
                e.getJoinDate(),
                e.getDateOfBirth()
        );
    }
}
