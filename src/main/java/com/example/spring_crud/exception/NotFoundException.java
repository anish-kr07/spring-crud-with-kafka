package com.example.spring_crud.exception;

// RuntimeException = unchecked. Service signature stays clean (no `throws` clause).
// GlobalExceptionHandler will map this -> HTTP 404 + ProblemDetail (next file).
public class NotFoundException extends RuntimeException {
    public NotFoundException(String message) {
        super(message);
    }
}
