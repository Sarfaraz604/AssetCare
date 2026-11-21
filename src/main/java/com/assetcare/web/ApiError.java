package com.assetcare.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.*;

public class ApiError {
  public String code;
  public String message;
  public Map<String, String> fieldErrors;
  public String requestId;

  public ApiError(String code, String message, Map<String, String> fields, String requestId) {
    this.code = code;
    this.message = message;
    fieldErrors = fields;
    this.requestId = requestId;
  }

  public static void write(
      ObjectMapper mapper,
      HttpServletRequest request,
      HttpServletResponse response,
      int status,
      String code,
      String message)
      throws IOException {
    response.setStatus(status);
    response.setContentType("application/json");
    mapper.writeValue(
        response.getOutputStream(),
        new ApiError(code, message, Map.of(), (String) request.getAttribute("requestId")));
  }
}
