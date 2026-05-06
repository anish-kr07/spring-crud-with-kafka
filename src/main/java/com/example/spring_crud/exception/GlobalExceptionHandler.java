package com.example.spring_crud.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;

// @RestControllerAdvice = @ControllerAdvice + @ResponseBody.
// Spring auto-discovers it at startup and routes any exception thrown from a
// @RequestMapping controller through the @ExceptionHandler that most closely
// matches the exception's runtime type.
//
// Returning ProblemDetail (RFC 7807, built into Spring 6+):
//   - Content-Type is set to "application/problem+json" automatically.
//   - HTTP status is taken from ProblemDetail.status — no ResponseEntity needed.
//   - Use setProperty(...) to add extension fields (timestamp, errors map, etc.).
//
// Verification helper convention used here: each handler stamps the response
// body with `"handler": "<methodName>"` AND logs at INFO. That way you can
// confirm which handler fired from either the curl output OR the server log.
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // 404 — service threw NotFoundException via findById().orElseThrow(...).
    // ex.getMessage() already says "Employee 5 not found", so reuse it.
    @ExceptionHandler(NotFoundException.class)
    public ProblemDetail handleNotFound(NotFoundException ex) {
        log.info("[handleNotFound] {}", ex.getMessage());
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        pd.setProperty("handler", "handleNotFound");
        pd.setProperty("timestamp", Instant.now());
        return pd;
    }

    // 400 — Jackson couldn't parse the body (malformed JSON, wrong date format,
    // type mismatch like a String where an int was expected).
    // Do NOT echo ex.getMessage() — it leaks Jackson internals like
    // "Cannot deserialize value of type `java.time.LocalDate` from String ...".
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleUnreadable(HttpMessageNotReadableException ex) {
        log.info("[handleUnreadable] {}", ex.getMostSpecificCause().getMessage());
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "Malformed JSON or invalid field format");
        pd.setProperty("handler", "handleUnreadable");
        pd.setProperty("timestamp", Instant.now());
        return pd;
    }

    // 409 Conflict — DB constraint violation. Most common trigger here is the
    // UNIQUE(email) constraint when creating two employees with the same email.
    //
    // Why catch this instead of doing an existsByEmail() check first:
    //   - The pre-check is racy: two concurrent requests both see "not taken",
    //     both insert, one wins with a constraint violation.
    //   - Trusting the DB constraint + translating the exception is atomic and
    //     correct under any concurrency.
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleConflict(DataIntegrityViolationException ex) {
        log.info("[handleConflict] {}", ex.getMostSpecificCause().getMessage());
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT, "Data conflict (likely a unique constraint violation)");
        pd.setProperty("handler", "handleConflict");
        pd.setProperty("timestamp", Instant.now());
        return pd;
    }

    // 500 — last-resort catch-all. Spring picks the MOST SPECIFIC handler, so
    // this only runs for exceptions not matched above. Two non-negotiables:
    //   1. Log the exception with stack trace server-side (debugging).
    //   2. Return a generic message — never the exception message or stack trace.
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleAny(Exception ex) {
        log.error("[handleAny] Unhandled exception", ex);
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error");
        pd.setProperty("handler", "handleAny");
        pd.setProperty("timestamp", Instant.now());
        return pd;
    }
}
