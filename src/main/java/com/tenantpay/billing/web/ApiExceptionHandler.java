package com.tenantpay.billing.web;

import com.tenantpay.billing.application.exception.ConflictException;
import com.tenantpay.billing.application.exception.NotFoundException;
import com.tenantpay.billing.application.exception.UnauthorizedException;
import com.tenantpay.billing.application.port.GatewayException;
import com.tenantpay.billing.domain.DomainValidationException;
import com.tenantpay.billing.domain.InvalidStateTransitionException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.net.URI;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Maps every error to RFC 7807 {@code application/problem+json}. */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(NotFoundException.class)
    ResponseEntity<ProblemDetail> notFound(NotFoundException e, HttpServletRequest request) {
        return problem(HttpStatus.NOT_FOUND, e.getMessage(), request);
    }

    @ExceptionHandler({ConflictException.class, InvalidStateTransitionException.class})
    ResponseEntity<ProblemDetail> conflict(RuntimeException e, HttpServletRequest request) {
        return problem(HttpStatus.CONFLICT, e.getMessage(), request);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ProblemDetail> integrity(DataIntegrityViolationException e, HttpServletRequest request) {
        log.debug("Integrity violation", e);
        return problem(HttpStatus.CONFLICT, "The request conflicts with existing data", request);
    }

    @ExceptionHandler({DomainValidationException.class, InvalidRequestException.class})
    ResponseEntity<ProblemDetail> badRequest(RuntimeException e, HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST, e.getMessage(), request);
    }

    @ExceptionHandler(UnauthorizedException.class)
    ResponseEntity<ProblemDetail> unauthorized(UnauthorizedException e, HttpServletRequest request) {
        return problem(HttpStatus.UNAUTHORIZED, e.getMessage(), request);
    }

    @ExceptionHandler(GatewayException.class)
    ResponseEntity<ProblemDetail> gateway(GatewayException e, HttpServletRequest request) {
        log.warn("Payment gateway failure: {}", e.getMessage());
        return problem(HttpStatus.BAD_GATEWAY, "Payment gateway unavailable, try again later", request);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> unexpected(Exception e, HttpServletRequest request) {
        log.error("Unhandled error on {} {}", request.getMethod(), request.getRequestURI(), e);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error", request);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                  HttpHeaders headers, HttpStatusCode status,
                                                                  WebRequest request) {
        ProblemDetail body = ex.getBody();
        body.setDetail("Validation failed");
        List<Map<String, String>> errors = ex.getBindingResult().getFieldErrors().stream()
                .sorted(Comparator.comparing(FieldError::getField))
                .map(error -> Map.of("field", error.getField(),
                        "message", error.getDefaultMessage() == null ? "is invalid" : error.getDefaultMessage()))
                .toList();
        body.setProperty("errors", errors);
        return handleExceptionInternal(ex, body, headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, @Nullable Object body,
                                                             HttpHeaders headers, HttpStatusCode statusCode,
                                                             WebRequest request) {
        if (body instanceof ProblemDetail problem && problem.getInstance() == null
                && request instanceof ServletWebRequest servlet) {
            problem.setInstance(URI.create(servlet.getRequest().getRequestURI()));
        }
        return super.handleExceptionInternal(ex, body, headers, statusCode, request);
    }

    private static ResponseEntity<ProblemDetail> problem(HttpStatus status, String detail,
                                                         HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setInstance(URI.create(request.getRequestURI()));
        return ResponseEntity.status(status).body(problem);
    }
}
