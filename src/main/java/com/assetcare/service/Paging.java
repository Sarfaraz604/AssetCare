package com.assetcare.service;

import java.util.Set;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;

public class Paging {
  public static Pageable page(
      int page, int size, String sort, String direction, Set<String> allowed) {
    if (page < 0
        || size < 1
        || size > 100
        || !allowed.contains(sort)
        || !(direction.equalsIgnoreCase("asc") || direction.equalsIgnoreCase("desc")))
      throw new Problem(
          HttpStatus.BAD_REQUEST,
          "INVALID_PAGE",
          "Use page >= 0, size 1-100, an allowed sort field and asc/desc direction.");
    Sort ordering = Sort.by(Sort.Direction.fromString(direction), sort);
    if (!sort.equals("id")) ordering = ordering.and(Sort.by("id"));
    return PageRequest.of(page, size, ordering);
  }

  public static String contains(String value) {
    // Escape SQL LIKE wildcards so user searches are literal text.
    return "%"
        + value
            .trim()
            .toLowerCase(java.util.Locale.ROOT)
            .replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_")
        + "%";
  }
}
