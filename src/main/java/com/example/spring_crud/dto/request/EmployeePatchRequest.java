package com.example.spring_crud.dto.request;

import com.example.spring_crud.common.DobFormat;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

// PATCH-specific DTO. Why not reuse EmployeeRequest?
//   - PATCH semantics: a missing/null field means "leave it alone".
//   - EmployeeRequest has @NotBlank/@NotNull on every field, so a typical PATCH
//     payload (e.g. {"salary": 150000}) would fail validation.
//
// Constraints kept here only fire when the field IS present:
//   @Email/@Size/@Past — JSR-380 validators short-circuit on null.
// No @NotBlank, no @NotNull. Boxed Integer for salary so 'null' is distinguishable
// from 0 (primitive int defaults to 0 and would silently overwrite the salary).
@Data
@NoArgsConstructor
public class EmployeePatchRequest {

    @Size(max = 50)
    private String firstName;

    @Size(max = 50)
    private String lastName;

    @Email
    @Size(max = 100)
    private String email;

    // PATCH variant of departmentId. Boxed Long, no validation — null means "skip".
    // To CLEAR a department on patch, this design can't distinguish "skip" from
    // "set to null". RFC 7396 / JsonNullable<Long> would handle that; out of scope here.
    private Long departmentId;

    @Positive
    private Integer salary;

    @PastOrPresent
    private LocalDate joinDate;

    @Past
    @DobFormat
    private LocalDate dateOfBirth;
}
