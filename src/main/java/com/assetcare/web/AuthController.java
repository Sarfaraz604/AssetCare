package com.assetcare.web;

import com.assetcare.dto.Views.UserView;
import com.assetcare.service.CurrentUser;
import java.util.Map;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
  private final CurrentUser current;

  public AuthController(CurrentUser current) {
    this.current = current;
  }

  @GetMapping("/csrf")
  public Map<String, String> csrf(CsrfToken token) {
    return Map.of("token", token.getToken(), "headerName", token.getHeaderName());
  }

  @GetMapping("/me")
  public UserView me() {
    return new UserView(current.get());
  }
}
