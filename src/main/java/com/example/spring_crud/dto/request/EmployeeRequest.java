package com.example.spring_crud.dto.request;

import com.example.spring_crud.common.DobFormat;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

// @Data on a DTO is FINE (the trap is on JPA entities — equals/hashCode + lazy proxies).
// @Data = @Getter + @Setter + @ToString + @EqualsAndHashCode + @RequiredArgsConstructor.
// @NoArgsConstructor needed by Jackson for JSON deserialization (default ctor + setters).
// @AllArgsConstructor is convenient for tests.
//
// Bean validation annotations (jakarta.validation):
//   @NotBlank -> not null AND not "" AND not "   "  (Strings only)
//   @NotNull  -> only null check (use for non-Strings)
//   @Email    -> accepts "" — combine with @NotBlank
//   @Size     -> min/max length on String/Collection
//   @Positive -> > 0 ; @PositiveOrZero -> >= 0
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EmployeeRequest {

    @NotBlank
    @Size(max = 50)
    private String firstName;

    @NotBlank
    @Size(max = 50)
    private String lastName;

    @NotBlank
    @Email
    @Size(max = 100)
    private String email;

    // The API takes a Long departmentId, not a free-text department name.
    // Why an id and not a name:
    //   - Names can change (rename "Engineering" -> "Eng"). Ids don't.
    //   - Lookup by id is O(1) on a primary key; lookup by name is a string scan.
    //   - Forces the client to know about the existing department list (list
    //     endpoint), which is a good API design — no typos creating new "depts".
    // Optional (no @NotNull): allow employees without a department.
    private Long departmentId;

    @Positive
    private int salary;

    // @NotNull because LocalDate is a non-String reference type (NotBlank only works for Strings).
    // @PastOrPresent rejects future join dates.
    // Wire format ("yyyy-MM-dd") is set globally in application.properties via
    // spring.jackson.date-format, so no per-field @JsonFormat needed.
    @NotNull
    @PastOrPresent
    private LocalDate joinDate;

    // @Past (NOT @PastOrPresent) — a person can't be born today.
    // @DobFormat = our meta-annotation -> Jackson reads its embedded @JsonFormat
    // and uses pattern "dd-MM-yyyy" for THIS field only. Other dates still use
    // the global "yyyy-MM-dd" from application.properties.
    @NotNull
    @Past
    @DobFormat
    private LocalDate dateOfBirth;
}
