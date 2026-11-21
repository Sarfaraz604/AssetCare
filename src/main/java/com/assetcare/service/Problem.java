package com.assetcare.service;

import org.springframework.http.HttpStatus;

public class Problem extends RuntimeException {
  private final HttpStatus status;
  private final String code;

  public Problem(HttpStatus status, String code, String message) {
    super(message);
    this.status = status;
    this.code = code;
  }

  public HttpStatus getStatus() {
    return status;
  }

  public String getCode() {
    return code;
  }

  public static Problem missing() {
    return new Problem(HttpStatus.NOT_FOUND, "NOT_FOUND", "Record not found.");
  }

  public static Problem forbidden() {
    return new Problem(
        HttpStatus.FORBIDDEN, "FORBIDDEN", "You do not have permission for this action.");
  }

  public static Problem rule(String message) {
    return new Problem(HttpStatus.CONFLICT, "BUSINESS_RULE", message);
  }

  public static void checkVersion(Long actual, Long supplied) {
    if (!java.util.Objects.equals(actual, supplied))
      throw new Problem(
          HttpStatus.CONFLICT,
          "STALE_UPDATE",
          "This record changed. Reload it and review your changes before retrying.");
  }
}
