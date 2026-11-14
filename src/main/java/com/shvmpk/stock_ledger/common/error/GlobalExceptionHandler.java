package com.shvmpk.stock_ledger.common.error;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final String PROBLEM_BASE_URL = "https://api.stockledger.io/errors/";

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidationException(MethodArgumentNotValidException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "One or more fields failed validation constraints."
        );
        problem.setTitle("Validation Failed");
        problem.setType(URI.create(PROBLEM_BASE_URL + "validation-failed"));
        problem.setProperty("code", "VALIDATION_FAILED");
        problem.setProperty("timestamp", Instant.now());

        List<InvalidParam> invalidParams = new ArrayList<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            invalidParams.add(new InvalidParam(
                    fieldError.getField(),
                    fieldError.getRejectedValue(),
                    fieldError.getDefaultMessage()
            ));
        }
        problem.setProperty("invalidParams", invalidParams);

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemDetail> handleMessageNotReadable(HttpMessageNotReadableException ex) {
        return validationProblem(
                "The request body is missing or malformed.",
                List.of()
        );
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ProblemDetail> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return validationProblem(
                String.format("Parameter '%s' has an invalid value.", ex.getName()),
                List.of(new InvalidParam(
                        ex.getName(),
                        ex.getValue(),
                        "Expected a valid value for parameter '" + ex.getName() + "'."
                ))
        );
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ProblemDetail> handleMissingParameter(MissingServletRequestParameterException ex) {
        return validationProblem(
                String.format("Required parameter '%s' is missing.", ex.getParameterName()),
                List.of(new InvalidParam(
                        ex.getParameterName(),
                        null,
                        "Required parameter is missing."
                ))
        );
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ProblemDetail> handleHandlerMethodValidation(HandlerMethodValidationException ex) {
        List<InvalidParam> invalidParams = ex.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream()
                        .map(error -> new InvalidParam(
                                result.getMethodParameter().getParameterName(),
                                result.getArgument(),
                                error.getDefaultMessage()
                        )))
                .toList();

        return validationProblem(
                "One or more parameters failed validation constraints.",
                invalidParams
        );
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ProblemDetail> handleConstraintViolation(ConstraintViolationException ex) {
        List<InvalidParam> invalidParams = ex.getConstraintViolations().stream()
                .map(violation -> new InvalidParam(
                        violation.getPropertyPath().toString(),
                        violation.getInvalidValue(),
                        violation.getMessage()
                ))
                .toList();

        return validationProblem(
                "One or more parameters failed validation constraints.",
                invalidParams
        );
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ProblemDetail> handleMaxUploadSize(MaxUploadSizeExceededException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONTENT_TOO_LARGE,
                "The uploaded file exceeds the maximum allowed size."
        );
        problem.setTitle("Payload Too Large");
        problem.setType(URI.create(PROBLEM_BASE_URL + "payload-too-large"));
        problem.setProperty("code", "PAYLOAD_TOO_LARGE");
        problem.setProperty("timestamp", Instant.now());
        problem.setProperty("invalidParams", List.of());

        return ResponseEntity
                .status(HttpStatus.CONTENT_TOO_LARGE)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ProblemDetail> handleDomainException(DomainException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(ex.getHttpStatus(), ex.getMessage());
        problem.setTitle(formatTitle(ex.getErrorCode()));
        problem.setType(URI.create(PROBLEM_BASE_URL + ex.getErrorCode().toLowerCase().replace('_', '-')));
        problem.setProperty("code", ex.getErrorCode());
        problem.setProperty("timestamp", Instant.now());
        problem.setProperty("invalidParams", List.of());
        ex.getExtensions().forEach(problem::setProperty);

        return ResponseEntity
                .status(ex.getHttpStatus())
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ProblemDetail> handleOptimisticLockingFailure(
            OptimisticLockingFailureException ex
    ) {
        return handleDomainException(new ConcurrentChangeException());
    }

    @ExceptionHandler(ProductValidationException.class)
    public ResponseEntity<ProblemDetail> handleProductValidation(ProductValidationException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(ex.getHttpStatus(), ex.getMessage());
        problem.setTitle("Validation Failed");
        problem.setType(URI.create(PROBLEM_BASE_URL + "validation-failed"));
        problem.setProperty("code", ex.getErrorCode());
        problem.setProperty("timestamp", Instant.now());
        problem.setProperty("invalidParams", ex.getInvalidParams());

        return ResponseEntity
                .status(ex.getHttpStatus())
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    @ExceptionHandler(WeakPasswordException.class)
    public ResponseEntity<ProblemDetail> handleWeakPassword(WeakPasswordException ex) {        ProblemDetail problem = ProblemDetail.forStatusAndDetail(ex.getHttpStatus(), ex.getMessage());
        problem.setTitle("Weak Password");
        problem.setType(URI.create(PROBLEM_BASE_URL + "weak-password"));
        problem.setProperty("code", ex.getErrorCode());
        problem.setProperty("timestamp", Instant.now());
        problem.setProperty("invalidParams", ex.getViolations().stream()
                .map(message -> new InvalidParam(ex.getField(), null, message))
                .toList());

        return ResponseEntity
                .status(ex.getHttpStatus())
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ProblemDetail> handleAccessDeniedException(AccessDeniedException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.FORBIDDEN,
                "You do not have permission to access this resource."
        );
        problem.setTitle("Forbidden");
        problem.setType(URI.create(PROBLEM_BASE_URL + "forbidden"));
        problem.setProperty("code", "INSUFFICIENT_PERMISSIONS");
        problem.setProperty("timestamp", Instant.now());
        problem.setProperty("invalidParams", List.of());

        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ProblemDetail> handleAuthenticationException(AuthenticationException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNAUTHORIZED,
                "Full authentication is required to access this resource."
        );
        problem.setTitle("Unauthorized");
        problem.setType(URI.create(PROBLEM_BASE_URL + "unauthorized"));
        problem.setProperty("code", "AUTHENTICATION_REQUIRED");
        problem.setProperty("timestamp", Instant.now());
        problem.setProperty("invalidParams", List.of());

        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleGenericException(Exception ex, HttpServletRequest request) {
        // The client only ever sees the generic problem below — the real cause,
        // with its full stack trace, belongs to the server log (the requestId in
        // the console pattern correlates it with the failed request).
        log.error("Unhandled exception on {} {}", request.getMethod(), request.getRequestURI(), ex);

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected internal error occurred."
        );
        problem.setTitle("Internal Server Error");
        problem.setType(URI.create(PROBLEM_BASE_URL + "internal-error"));
        problem.setProperty("code", "INTERNAL_SERVER_ERROR");
        problem.setProperty("timestamp", Instant.now());
        problem.setProperty("invalidParams", List.of());

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    private ResponseEntity<ProblemDetail> validationProblem(String detail, List<InvalidParam> invalidParams) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
        problem.setTitle("Validation Failed");
        problem.setType(URI.create(PROBLEM_BASE_URL + "validation-failed"));
        problem.setProperty("code", "VALIDATION_FAILED");
        problem.setProperty("timestamp", Instant.now());
        problem.setProperty("invalidParams", invalidParams);

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    private String formatTitle(String errorCode) {
        if (errorCode == null || errorCode.isBlank()) {
            return "Domain Error";
        }
        String[] words = errorCode.toLowerCase().split("_");
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            if (!word.isEmpty()) {
                sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1)).append(" ");
            }
        }
        return sb.toString().trim();
    }
}
