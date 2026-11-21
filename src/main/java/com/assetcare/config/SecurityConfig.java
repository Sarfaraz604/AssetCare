package com.assetcare.config;

import com.assetcare.repository.UserRepository;
import com.assetcare.web.ApiError;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.*;

@Configuration
public class SecurityConfig {
  @Bean
  public Clock clock() {
    return Clock.systemUTC();
  }

  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  @Bean
  public UserDetailsService userDetailsService(UserRepository users) {
    return username -> {
      var user =
          users
              .findByUsername(username)
              .orElseThrow(() -> new UsernameNotFoundException("Invalid credentials"));
      return User.withUsername(user.getUsername())
          .password(user.getPasswordHash())
          .roles(user.getRole().name())
          .build();
    };
  }

  @Bean
  public SecurityFilterChain security(HttpSecurity http, ObjectMapper mapper) throws Exception {
    HttpSessionCsrfTokenRepository tokens = new HttpSessionCsrfTokenRepository();
    // The token is returned in JSON, not stored in a JavaScript-readable cookie.
    http.csrf(
        csrf ->
            csrf.csrfTokenRepository(tokens)
                .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()));
    http.authorizeHttpRequests(
        auth ->
            auth.requestMatchers(
                    "/",
                    "/index.html",
                    "/assets/**",
                    "/favicon.svg",
                    "/api/auth/csrf",
                    "/api/auth/login",
                    "/actuator/health",
                    "/actuator/health/liveness",
                    "/actuator/health/readiness",
                    "/error")
                .permitAll()
                .requestMatchers("/api/**")
                .authenticated()
                .anyRequest()
                .denyAll());
    http.exceptionHandling(
        errors ->
            errors
                .authenticationEntryPoint(
                    (req, res, e) ->
                        ApiError.write(mapper, req, res, 401, "UNAUTHORIZED", "Please sign in."))
                .accessDeniedHandler(
                    (req, res, e) ->
                        ApiError.write(
                            mapper,
                            req,
                            res,
                            403,
                            "FORBIDDEN",
                            "Permission denied or missing/expired CSRF token.")));
    http.formLogin(
        login ->
            login
                .loginProcessingUrl("/api/auth/login")
                .successHandler(
                    (req, res, auth) -> {
                      res.setStatus(204);
                    })
                .failureHandler(
                    (req, res, e) ->
                        ApiError.write(
                            mapper,
                            req,
                            res,
                            401,
                            "INVALID_CREDENTIALS",
                            "Incorrect username or password.")));
    http.logout(
        logout ->
            logout
                .logoutUrl("/api/auth/logout")
                .logoutSuccessHandler((req, res, auth) -> res.setStatus(204))
                .deleteCookies("JSESSIONID"));
    http.requestCache(cache -> cache.disable());
    return http.build();
  }
}
