package com.assetcare.web;

import com.assetcare.service.Problem;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import org.slf4j.*;
import org.springframework.dao.*;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class ExceptionAdvice {
  private static final Logger log = LoggerFactory.getLogger(ExceptionAdvice.class);

  private ResponseEntity<ApiError> response(
      HttpStatus status,
      String code,
      String message,
      Map<String, String> fields,
      HttpServletRequest request) {
    return ResponseEntity.status(status)
        .body(new ApiError(code, message, fields, (String) request.getAttribute("requestId")));
  }

  @ExceptionHandler(Problem.class)
  public ResponseEntity<ApiError> problem(Problem e, HttpServletRequest request) {
    return response(e.getStatus(), e.getCode(), e.getMessage(), Map.of(), request);
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ApiError> validation(
      MethodArgumentNotValidException e, HttpServletRequest request) {
    Map<String, String> fields = new LinkedHashMap<>();
    for (var error : e.getBindingResult().getFieldErrors())
      fields.putIfAbsent(error.getField(), error.getDefaultMessage());
    return response(
        HttpStatus.BAD_REQUEST,
        "VALIDATION",
        "Please correct the highlighted fields.",
        fields,
        request);
  }

  @ExceptionHandler({
    HttpMessageNotReadableException.class,
    MethodArgumentTypeMismatchException.class
  })
  public ResponseEntity<ApiError> badInput(Exception e, HttpServletRequest request) {
    return response(
        HttpStatus.BAD_REQUEST,
        "INVALID_INPUT",
        "Invalid JSON, date, enum or query parameter.",
        Map.of(),
        request);
  }

  @ExceptionHandler(OptimisticLockingFailureException.class)
  public ResponseEntity<ApiError> stale(Exception e, HttpServletRequest request) {
    return response(
        HttpStatus.CONFLICT,
        "STALE_UPDATE",
        "This record changed. Reload it and review your changes before retrying.",
        Map.of(),
        request);
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  public ResponseEntity<ApiError> duplicate(Exception e, HttpServletRequest request) {
    return response(
        HttpStatus.CONFLICT,
        "DATA_CONFLICT",
        "Asset code must be unique. A related record or database constraint prevented this change.",
        Map.of(),
        request);
  }

  @ExceptionHandler(PessimisticLockingFailureException.class)
  public ResponseEntity<ApiError> busy(Exception e, HttpServletRequest request) {
    return response(
        HttpStatus.CONFLICT,
        "RECORD_BUSY",
        "Another request is updating this record. Please reload and retry.",
        Map.of(),
        request);
  }

  @ExceptionHandler(NoResourceFoundException.class)
  public ResponseEntity<ApiError> missing(Exception e, HttpServletRequest request) {
    return response(HttpStatus.NOT_FOUND, "NOT_FOUND", "Endpoint not found.", Map.of(), request);
  }

  @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
  public ResponseEntity<ApiError> method(Exception e, HttpServletRequest request) {
    return response(
        HttpStatus.METHOD_NOT_ALLOWED,
        "METHOD_NOT_ALLOWED",
        "HTTP method not supported.",
        Map.of(),
        request);
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiError> unexpected(Exception e, HttpServletRequest request) {
    // Log only exception type, because database exceptions can contain submitted values.
    log.error(
        "requestId={} exceptionType={}", request.getAttribute("requestId"), e.getClass().getName());
    return response(
        HttpStatus.INTERNAL_SERVER_ERROR,
        "INTERNAL_ERROR",
        "An unexpected error occurred. Use the request ID when reporting it.",
        Map.of(),
        request);
  }
}
