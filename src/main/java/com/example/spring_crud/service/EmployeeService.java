package com.example.spring_crud.service;

import com.example.spring_crud.dto.request.EmployeePatchRequest;
import com.example.spring_crud.dto.request.EmployeeRequest;
import com.example.spring_crud.dto.response.EmployeeResponse;
import com.example.spring_crud.entity.Department;
import com.example.spring_crud.entity.Employee;
import com.example.spring_crud.exception.NotFoundException;
import com.example.spring_crud.outbox.OutboxPublisher;
import com.example.spring_crud.repository.DepartmentRepository;
import com.example.spring_crud.repository.EmployeeRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

// @Service — stereotype of @Component; signals "business logic" intent.
//
// @Transactional(readOnly = true) at class level:
//   - Every public method runs in a TX; reads are read-only by default.
//   - readOnly is a HINT: Hibernate skips dirty-checking on flush, JDBC drivers may optimize.
//   - Write methods (create/update/delete) will override with @Transactional later.
//
// Constructor injection (no @Autowired) + final field:
//   - 'final' = immutable, thread-safe; Spring auto-injects when there's a single ctor.
@Service
@Transactional(readOnly = true)
public class EmployeeService {

    private static final String AGGREGATE_TYPE = "Employee";

    private final EmployeeRepository employeeRepository;
    private final DepartmentRepository departmentRepository;
    private final OutboxPublisher outboxPublisher;

    public EmployeeService(EmployeeRepository employeeRepository,
                           DepartmentRepository departmentRepository,
                           OutboxPublisher outboxPublisher) {
        this.employeeRepository = employeeRepository;
        this.departmentRepository = departmentRepository;
        this.outboxPublisher = outboxPublisher;
    }

    // Helper for FK assignment.
    //
    // getReferenceById vs findById — interview-relevant trade-off:
    //   - findById(id)         -> SELECT issued, returns full entity (or empty Optional).
    //   - getReferenceById(id) -> NO SELECT, returns a lazy proxy. Hibernate just
    //                             trusts the id and uses it for the FK.
    //
    // For "set the parent on a child" we don't need the parent's columns — only
    // its id. getReferenceById is cheaper. Catch: if the id doesn't exist,
    // the failure surfaces LATER (on flush, as EntityNotFoundException). To get
    // a clean 404 up front, we existsById first.
    private Department resolveDepartment(Long id) {
        if (id == null) return null;
        if (!departmentRepository.existsById(id)) {
            throw new NotFoundException("Department " + id + " not found");
        }
        return departmentRepository.getReferenceById(id);
    }

    // Returns DTOs, NOT entities. Why:
    //   - Entities are JPA-managed; serializing them touches lazy associations
    //     (LazyInitializationException once the TX ends — open-in-view=false).
    //   - DTO decouples API from schema; renaming a column doesn't break the API.
    //   - Avoids leaking internal fields (version, audit timestamps, etc.).
    public List<EmployeeResponse> findAll() {
        return employeeRepository.findAll().stream()
                .map(EmployeeResponse::getEmployee)
                .toList();
    }

    // Optional.map(...).orElseThrow(...) is the idiomatic chain:
    //   - .map() runs ONLY if the Optional is non-empty (mapping entity -> DTO).
    //   - .orElseThrow() either unwraps the value or throws on empty.
    // Don't .stream() on an Optional unless you actually need Stream semantics —
    // it forces you to use findFirst()/findAny(), which loses the orElseThrow shortcut.
    public EmployeeResponse findByEmployeeId(Long id) {

//         -Two database hits (existsById then findById) — wasteful.                                                                              ─
//        -Race condition: a concurrent delete between the two calls makes .get() throw NoSuchElementException.
//        if (!employeeRepository.existsById(id)) {
//            throw new NotFoundException("Employee " + id + " not found");
//        }
//        Employee e = employeeRepository.findById(id).get();
//        return EmployeeResponse.getEmployee(e);


        return employeeRepository.findById(id)
                .map(EmployeeResponse::getEmployee)
                .orElseThrow(() -> new NotFoundException("Employee " + id + " not found"));
    }

    // Reuses the JPQL @Query already in the repository:
    //   select e from Employee e where e.email = :email and e.salary > :minSalary
    // Returns a List (possibly empty). Empty list != "not found" — searches that
    // legitimately match no rows should return 200 + [] (NOT 404).
    public List<EmployeeResponse> searchByEmailAndMinSalary(String email, int minSalary) {
        return employeeRepository.searchByEmailAboveSalary(email, minSalary).stream()
                .map(EmployeeResponse::getEmployee)
                .toList();
    }

    public List<EmployeeResponse> searchPaged(String email, int minSalary, int pageNumber, int pageSize) {
        PageRequest page = PageRequest.of(pageNumber, pageSize);
        return employeeRepository.searchByEmailAboveSalaryPaged(email, minSalary, page).stream()
                .map(EmployeeResponse::getEmployee)
                .toList();
    }

    // @Transactional overrides class-level readOnly=true. Bare @Transactional defaults
    // to readOnly=false, so 'readOnly=false' explicitly is redundant.
    //
    // Validation note: @Valid on a service method param does NOTHING by default — Spring MVC
    // honors @Valid on @RequestBody (controller boundary). Service-level method validation
    // requires @Validated on the class + an AOP proxy. We rely on the controller-side @Valid.
    @Transactional
    public EmployeeResponse create(EmployeeRequest req) {
        Employee e = new Employee();
        e.setFirstName(req.getFirstName());
        e.setLastName(req.getLastName());
        e.setEmail(req.getEmail());
        e.setDepartment(resolveDepartment(req.getDepartmentId()));
        e.setSalary(req.getSalary());
        e.setJoinDate(req.getJoinDate());
        e.setDateOfBirth(req.getDateOfBirth());

        // save() returns the persisted entity with the IDENTITY-generated id assigned.
        // Capture and use the return value (also safe with merge / saveAll semantics).
        Employee saved = employeeRepository.save(e);
        EmployeeResponse response = EmployeeResponse.getEmployee(saved);

        // Atomic with the INSERT above — same @Transactional, same DB tx.
        // If the outbox write fails, the employee insert rolls back too.
        outboxPublisher.record(AGGREGATE_TYPE, String.valueOf(saved.getId()),
                "EmployeeCreated", response);

        return response;
    }

    // PUT — full replace. Client sends the complete resource; every field overwrites.
    // No explicit save(): inside an @Transactional, the entity returned by findById with
    // @Transactional, the EntityManager stays open, with each .set()
    // is MANAGED. Hibernate dirty-checks at flush/commit and issues UPDATE automatically.
    // (save() would also work but is redundant here.)
    @Transactional
    public EmployeeResponse update(Long id, EmployeeRequest req) {
        Employee e = employeeRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Employee " + id + " not found"));

        e.setFirstName(req.getFirstName());
        e.setLastName(req.getLastName());
        e.setEmail(req.getEmail());
        e.setDepartment(resolveDepartment(req.getDepartmentId()));
        e.setSalary(req.getSalary());
        e.setJoinDate(req.getJoinDate());
        e.setDateOfBirth(req.getDateOfBirth());

        EmployeeResponse response = EmployeeResponse.getEmployee(e);
        outboxPublisher.record(AGGREGATE_TYPE, String.valueOf(id), "EmployeeUpdated", response);
        return response;
    }

    // PATCH — partial update. Only non-null fields in the DTO are applied.
    // Null means "leave alone", which is why EmployeePatchRequest uses boxed
    // Integer for salary (null is distinguishable from 0).
    //
    // Limitation: this pattern can't distinguish "field absent" from "field
    // explicitly set to null" — both arrive as null. For a true RFC 7396 / JSON
    // Merge Patch with explicit nulls, use JsonNullable<T> or JsonNode.
    @Transactional
    public EmployeeResponse patch(Long id, EmployeePatchRequest req) {
        Employee e = employeeRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Employee " + id + " not found"));

        if (req.getFirstName() != null)    e.setFirstName(req.getFirstName());
        if (req.getLastName() != null)     e.setLastName(req.getLastName());
        if (req.getEmail() != null)        e.setEmail(req.getEmail());
        if (req.getDepartmentId() != null) e.setDepartment(resolveDepartment(req.getDepartmentId()));
        if (req.getSalary() != null)       e.setSalary(req.getSalary());
        if (req.getJoinDate() != null)     e.setJoinDate(req.getJoinDate());
        if (req.getDateOfBirth() != null)  e.setDateOfBirth(req.getDateOfBirth());

        EmployeeResponse response = EmployeeResponse.getEmployee(e);
        outboxPublisher.record(AGGREGATE_TYPE, String.valueOf(id), "EmployeePatched", response);
        return response;
    }

    // DELETE. existsById + deleteById = 2 queries but gives a clean 404.
    // Alternative: deleteById and catch EmptyResultDataAccessException — 1 query
    // happy-path but ties controller logic to a Spring-Data exception type.
    @Transactional
    public void delete(Long id) {
        // findById (not existsById) so we can snapshot the entity for the
        // outbox payload BEFORE deletion. After delete the row is gone and
        // we couldn't reconstruct it.
        Employee e = employeeRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Employee " + id + " not found"));
        EmployeeResponse snapshot = EmployeeResponse.getEmployee(e);

        employeeRepository.delete(e);
        outboxPublisher.record(AGGREGATE_TYPE, String.valueOf(id), "EmployeeDeleted", snapshot);
    }
}
