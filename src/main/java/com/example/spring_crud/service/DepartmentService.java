package com.example.spring_crud.service;

import com.example.spring_crud.dto.request.DepartmentRequest;
import com.example.spring_crud.dto.response.DepartmentResponse;
import com.example.spring_crud.entity.Department;
import com.example.spring_crud.exception.NotFoundException;
import com.example.spring_crud.kafka.DepartmentEventPublisher;
import com.example.spring_crud.repository.DepartmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class DepartmentService {

    private final DepartmentRepository departmentRepository;
    private final DepartmentEventPublisher departmentEventPublisher;

    public DepartmentService(DepartmentRepository departmentRepository, DepartmentEventPublisher departmentEventPublisher) {
        this.departmentRepository = departmentRepository;
        this.departmentEventPublisher = departmentEventPublisher;
    }

    // findAll() resolves to DepartmentRepository.findAll() — the override with
    // @EntityGraph(attributePaths="employees"). One SQL query fetches
    // departments + employees together, so the .map(...) below doesn't trigger
    // N+1 even though we touch dept.getEmployees() inside DepartmentResponse.from.
    public List<DepartmentResponse> findAll() {
        return departmentRepository.findAll().stream()
                .map(DepartmentResponse::from)
                .toList();
    }

    // findWithEmployeesById uses the same EntityGraph — single SELECT.
    // findById (vanilla) would issue a second SELECT for employees on access.
    public DepartmentResponse findById(Long id) {
        Department department = departmentRepository.findWithEmployeesById(id)
                .orElseThrow(() -> new NotFoundException("Department " + id + " not found"));
        departmentEventPublisher.publishLookup(department);

        return DepartmentResponse.from(department);

    }

    @Transactional
    public DepartmentResponse create(DepartmentRequest req) {
        Department d = new Department();
        d.setName(req.getName());
        Department saved = departmentRepository.save(d);

        // Direct publish (no outbox) — async fire-and-forget. If Kafka is down
        // the create still succeeds but the event is lost. For state changes
        // this is technically incorrect; we accept it here because department
        // events are observability/audit, not critical state distribution.
        // For Employee, we use the outbox pattern instead (see EmployeeService).
        departmentEventPublisher.publishCreated(saved);

        return DepartmentResponse.from(saved);
    }
}
