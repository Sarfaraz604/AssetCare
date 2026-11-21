package com.assetcare.web;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.assetcare.config.SecurityConfig;
import com.assetcare.model.*;
import com.assetcare.repository.*;
import com.assetcare.service.*;
import java.util.Optional;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = {AuthController.class, AssetController.class})
@Import({SecurityConfig.class, CurrentUser.class, AssetService.class, RequestIdFilter.class})
class SecurityTest {
  @Autowired MockMvc mvc;
  @Autowired PasswordEncoder encoder;
  @MockitoBean UserRepository users;
  @MockitoBean AssetRepository assets;
  @MockitoBean WorkOrderRepository orders;

  @BeforeEach
  void user() {
    AppUser user = new AppUser();
    user.setUsername("viewer");
    user.setDisplayName("Viewer");
    user.setRole(Role.VIEWER);
    user.setPasswordHash(encoder.encode("synthetic-test-password"));
    when(users.findByUsername("viewer")).thenReturn(Optional.of(user));
  }

  @Test
  void anonymousRequestReturnsJson401() throws Exception {
    mvc.perform(get("/api/assets"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
        .andExpect(jsonPath("$.requestId").isNotEmpty());
  }

  @Test
  void csrfEndpointReturnsSessionToken() throws Exception {
    mvc.perform(get("/api/auth/csrf"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.token").isNotEmpty())
        .andExpect(jsonPath("$.headerName").value("X-CSRF-TOKEN"));
  }

  @Test
  void loginWithoutCsrfIsForbidden() throws Exception {
    mvc.perform(
            post("/api/auth/login")
                .param("username", "viewer")
                .param("password", "synthetic-test-password"))
        .andExpect(status().isForbidden());
  }

  @Test
  void loginCreatesAuthenticatedSession() throws Exception {
    var result =
        mvc.perform(
                post("/api/auth/login")
                    .with(csrf())
                    .param("username", "viewer")
                    .param("password", "synthetic-test-password"))
            .andExpect(status().isNoContent())
            .andReturn();
    mvc.perform(
            get("/api/auth/me").session((MockHttpSession) result.getRequest().getSession(false)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.role").value("VIEWER"))
        .andExpect(jsonPath("$.passwordHash").doesNotExist());
  }

  @Test
  void wrongPasswordReturns401() throws Exception {
    mvc.perform(
            post("/api/auth/login")
                .with(csrf())
                .param("username", "viewer")
                .param("password", "incorrect"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
  }

  @Test
  @WithMockUser(username = "viewer", roles = "VIEWER")
  void authenticatedMutationRequiresCsrf() throws Exception {
    mvc.perform(post("/api/assets").contentType("application/json").content("{}"))
        .andExpect(status().isForbidden());
  }

  @Test
  @WithMockUser(username = "viewer", roles = "VIEWER")
  void viewerCannotPerformAdminAction() throws Exception {
    mvc.perform(
            post("/api/assets")
                .with(csrf())
                .contentType("application/json")
                .content(
                    "{\"code\":\"A-1\",\"name\":\"Pump\",\"category\":\"Plant\",\"location\":\"Roof\",\"status\":\"ACTIVE\"}"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    verify(assets, never()).saveAndFlush(any());
  }

  @Test
  @WithMockUser(username = "viewer", roles = "VIEWER")
  void validationIncludesFieldErrors() throws Exception {
    mvc.perform(post("/api/assets").with(csrf()).contentType("application/json").content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors.code").exists())
        .andExpect(jsonPath("$.requestId").isNotEmpty());
  }

  @Test
  void sensitiveActuatorEndpointsAreDenied() throws Exception {
    mvc.perform(get("/actuator/env")).andExpect(status().isUnauthorized());
  }

  @Test
  @WithMockUser(username = "admin", roles = "ADMIN")
  void staleUpdateReturns409WithRequestId() throws Exception {
    AppUser admin = new AppUser();
    admin.setRole(Role.ADMIN);
    when(users.findByUsername("admin")).thenReturn(Optional.of(admin));
    Asset asset = new Asset();
    asset.setVersion(2L);
    when(assets.findForUpdate(1L)).thenReturn(Optional.of(asset));
    mvc.perform(
            put("/api/assets/1")
                .with(csrf())
                .contentType("application/json")
                .content(
                    "{\"code\":\"A1\",\"name\":\"Pump\",\"category\":\"Plant\",\"location\":\"Roof\",\"status\":\"ACTIVE\",\"version\":1}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("STALE_UPDATE"))
        .andExpect(jsonPath("$.requestId").isNotEmpty());
  }

  @Test
  void jsonCsrfTokenWorksForLoginAndIsRotatedAfterAuthentication() throws Exception {
    var before = mvc.perform(get("/api/auth/csrf")).andExpect(status().isOk()).andReturn();
    var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
    String token = mapper.readTree(before.getResponse().getContentAsString()).get("token").asText();
    MockHttpSession session = (MockHttpSession) before.getRequest().getSession(false);
    mvc.perform(
            post("/api/auth/login")
                .session(session)
                .header("X-CSRF-TOKEN", token)
                .param("username", "viewer")
                .param("password", "synthetic-test-password"))
        .andExpect(status().isNoContent());
    var after =
        mvc.perform(get("/api/auth/csrf").session(session)).andExpect(status().isOk()).andReturn();
    String next = mapper.readTree(after.getResponse().getContentAsString()).get("token").asText();
    org.junit.jupiter.api.Assertions.assertNotEquals(token, next);
    mvc.perform(post("/api/auth/logout").session(session).header("X-CSRF-TOKEN", token))
        .andExpect(status().isForbidden());
    mvc.perform(post("/api/auth/logout").session(session).header("X-CSRF-TOKEN", next))
        .andExpect(status().isNoContent());
    mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
  }
}
