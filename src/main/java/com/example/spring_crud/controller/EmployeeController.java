package com.example.spring_crud.controller;

import com.example.spring_crud.dto.request.EmployeePatchRequest;
import com.example.spring_crud.dto.request.EmployeeRequest;
import com.example.spring_crud.dto.response.EmployeeResponse;
import com.example.spring_crud.service.EmployeeService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/v1/employees")
public class EmployeeController {

    private final EmployeeService service;

    public EmployeeController(EmployeeService service) {
        this.service = service;
    }

    @GetMapping
    public List<EmployeeResponse> getAllEmployee(){
        return service.findAll();
    }

    // Returns EmployeeResponse directly (NOT Optional).
    //  - Success: 200 OK + DTO.
    //  - Missing : service throws NotFoundException -> GlobalExceptionHandler -> 404.
    // Optional from a controller is an anti-pattern: empty Optional serializes to an
    // empty/null body with 200 OK, lying about the resource state.
    @GetMapping("/{id}")
    public EmployeeResponse getEmployeeById(@PathVariable Long id) {
        return service.findByEmployeeId(id);
    }

    // GET /v1/employees/search?email=ada@example.com&minSalary=100000
    //
    // @RequestParam knobs (interview-relevant):
    //   - required = true  (default): missing param -> 400 Bad Request automatically.
    //   - required = false           : null is bound; the param type must be a wrapper
    //                                  (Integer not int) or a default must be supplied.
    //   - defaultValue = "X"         : used when the param is absent. Implies required=false.
    //                                  Values are Strings; Spring converts to the target type.
    //
    // Place query params on the URL line (?email=...&minSalary=...). They differ from:
    //   - @PathVariable (part of the path: /employees/{id})
    //   - @RequestBody  (JSON in the body, used for POST/PUT)
    //   - @RequestHeader / @CookieValue (other request locations)
    @GetMapping("/search")
    public List<EmployeeResponse> searchEmployees(
            @RequestParam String email,
            @RequestParam(defaultValue = "0") int minSalary) {
        return service.searchByEmailAndMinSalary(email, minSalary);
    }

    // @RequestBody is REQUIRED for JSON-body input. Without it, Spring binds 'req'
    // from query params, not the body, so a curl -d '{...}' arrives as an empty DTO.
    // @Valid triggers JSR-380 bean validation on the DTO; failures throw
    // MethodArgumentNotValidException -> mapped to 400 by GlobalExceptionHandler.
   //    sample curl -i -u user:password -X POST http://localhost:8080/v1/employees -H "Content-Type: application/json" -d '{"firstName":"Hedy","lastName":
   //            "Lamarr","email":"hedy@example.com","department":"Research","salary":140000,"joinDate":"2024-02-01","dateOfBirth":"1984-11-14"}'
    @PostMapping
    public ResponseEntity<EmployeeResponse> addEmployee(@Valid @RequestBody EmployeeRequest req) {
        EmployeeResponse created = service.create(req);
        URI location = URI.create("/v1/employees/" + created.getId());
        return ResponseEntity.created(location).body(created);
    }

    // PUT = full replace. Reuses EmployeeRequest, so all @NotBlank/@NotNull fields
    // are required — sending a partial body returns 400.
    // Returning the updated DTO with 200 OK is the common convention; 204 No Content
    // is also valid if you don't need to return the new state.
    @PutMapping("/{id}")
    public EmployeeResponse updateEmployee(@PathVariable Long id,
                                           @Valid @RequestBody EmployeeRequest req) {
        return service.update(id, req);
    }

    // PATCH = partial update. Uses EmployeePatchRequest where every field is optional;
    // only non-null fields in the body are applied. Validation constraints
    // (@Email, @Size, @Positive) still run on the fields that ARE present.
    @PatchMapping("/{id}")
    public EmployeeResponse patchEmployee(@PathVariable Long id,
                                          @Valid @RequestBody EmployeePatchRequest req) {
        return service.patch(id, req);
    }

    // DELETE — 204 No Content on success (no body). 404 if the id doesn't exist
    // (thrown from the service, mapped by GlobalExceptionHandler).
    // Idempotent: deleting an already-deleted id giving 404 is a design choice;
    // some APIs return 204 either way. Pick one and document it.
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteEmployee(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
