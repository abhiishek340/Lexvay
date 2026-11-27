package com.lexvay.common;

import java.sql.SQLException;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/** RFC 9457 problem details for every error the API returns. */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    /** SQLSTATE raised by the immutability triggers (V003). */
    static final String IMMUTABLE_SQLSTATE = "LX001";

    /** SQLSTATE class 22 (data exception): a value the database cannot store, such as a NUL character. */
    private static final String DATA_EXCEPTION_CLASS = "22";

    @ExceptionHandler(NotFoundException.class)
    ProblemDetail notFound(NotFoundException e) {
        return problem(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail badRequest(IllegalArgumentException e) {
        return problem(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(jakarta.validation.ConstraintViolationException.class)
    ProblemDetail constraintViolation(jakarta.validation.ConstraintViolationException e) {
        return problem(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(ConflictException.class)
    ProblemDetail conflict(ConflictException e) {
        ProblemDetail problem = problem(HttpStatus.CONFLICT, e.getMessage());
        if (e instanceof com.lexvay.document.DuplicateDocumentException duplicate) {
            problem.setProperty("existingDocumentId", duplicate.existingDocumentId().toString());
        }
        return problem;
    }

    @ExceptionHandler(DataAccessException.class)
    ProblemDetail dataAccess(DataAccessException e) {
        if (e.getCause() instanceof SQLException sql && IMMUTABLE_SQLSTATE.equals(sql.getSQLState())) {
            return problem(HttpStatus.CONFLICT, sql.getMessage().lines().findFirst().orElse("Record is immutable"));
        }
        if (e.getCause() instanceof SQLException sql && sql.getSQLState() != null && sql.getSQLState().startsWith(DATA_EXCEPTION_CLASS)) {
            return problem(HttpStatus.BAD_REQUEST, "The request contains a value that cannot be stored");
        }
        if (e instanceof org.springframework.dao.DataIntegrityViolationException) {
            return problem(HttpStatus.CONFLICT, "The request conflicts with existing data");
        }
        throw e;
    }

    private static ProblemDetail problem(HttpStatus status, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(status.getReasonPhrase());
        return problem;
    }
}
