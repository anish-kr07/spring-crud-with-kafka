package com.example.spring_crud.controller;

import com.example.spring_crud.dto.response.EmployeeResponse;
import com.example.spring_crud.service.EmployeeService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/v2/employees")
public class EmployeeV2Controller {

    private final EmployeeService service;

    public EmployeeV2Controller(EmployeeService service) {
        this.service = service;
    }

    @GetMapping("/search")
    public List<EmployeeResponse> search(
            @RequestParam String email,
            @RequestParam(defaultValue = "0") int minSalary,
            @RequestParam(defaultValue = "0") int pageNumber,
            @RequestParam(defaultValue = "10") int pageSize) {
        return service.searchPaged(email, minSalary, pageNumber, pageSize);
    }
}
