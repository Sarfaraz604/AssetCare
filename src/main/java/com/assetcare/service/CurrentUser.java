package com.assetcare.service;

import com.assetcare.model.*;
import com.assetcare.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class CurrentUser {
  private final UserRepository users;

  public CurrentUser(UserRepository users) {
    this.users = users;
  }

  public AppUser get() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getName()))
      throw new Problem(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Please sign in.");
    return users.findByUsername(auth.getName()).orElseThrow(Problem::missing);
  }

  public AppUser requireAdmin() {
    AppUser u = get();
    if (u.getRole() != Role.ADMIN) throw Problem.forbidden();
    return u;
  }
}
