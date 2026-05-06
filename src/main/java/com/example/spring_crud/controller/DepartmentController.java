package com.example.spring_crud.controller;

import com.example.spring_crud.dto.request.DepartmentRequest;
import com.example.spring_crud.dto.response.DepartmentResponse;
import com.example.spring_crud.service.DepartmentService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/v1/departments")
public class DepartmentController {

    private final DepartmentService service;

    public DepartmentController(DepartmentService service) {
        this.service = service;
    }

    // GET /v1/departments — returns each department with its employees nested.
    // This is the "grouped by departments" view: clients get the data already
    // organized, no client-side grouping needed.
    @GetMapping
    public List<DepartmentResponse> list() {
        return service.findAll();
    }

    @GetMapping("/{id}")
    public DepartmentResponse get(@PathVariable Long id) {
        return service.findById(id);
    }

    @PostMapping
    public ResponseEntity<DepartmentResponse> create(@Valid @RequestBody DepartmentRequest req) {
        DepartmentResponse created = service.create(req);
        URI location = URI.create("/v1/departments/" + created.getId());
        return ResponseEntity.created(location).body(created);
    }
}
