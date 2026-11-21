package com.assetcare;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import com.assetcare.dto.Requests.*;
import com.assetcare.dto.Views.*;
import com.assetcare.model.*;
import com.assetcare.repository.*;
import com.assetcare.service.*;
import jakarta.persistence.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;

@SpringBootTest
@Import(PostgreSqlIT.FixedTime.class)
@Testcontainers(disabledWithoutDocker = true)
class PostgreSqlIT {
  @Container
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17.7-alpine");

  @DynamicPropertySource
  static void database(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
    registry.add("app.seed-enabled", () -> false);
  }

  @TestConfiguration
  static class FixedTime {
    @Bean
    @Primary
    Clock testClock() {
      return Clock.fixed(Instant.parse("2026-03-10T12:00:00Z"), ZoneOffset.UTC);
    }
  }

  @Autowired AssetService assetService;
  @Autowired WorkOrderService orderService;
  @Autowired UserRepository users;
  @Autowired AssetRepository assets;
  @Autowired WorkOrderRepository orders;
  @MockitoSpyBean HistoryRepository history;
  @Autowired PasswordEncoder encoder;
  @Autowired EntityManagerFactory emf;
  AppUser admin;
  AppUser technician;
  AppUser viewer;
  AppUser otherViewer;
  AppUser otherTech;

  @BeforeEach
  void setup() {
    history.deleteAll();
    orders.deleteAll();
    assets.deleteAll();
    users.deleteAll();
    admin = user("admin", Role.ADMIN);
    technician = user("tech", Role.TECHNICIAN);
    viewer = user("viewer", Role.VIEWER);
    otherViewer = user("other-viewer", Role.VIEWER);
    otherTech = user("other-tech", Role.TECHNICIAN);
    login(admin);
  }

  @AfterEach
  void clearSecurity() {
    SecurityContextHolder.clearContext();
  }

  AppUser user(String name, Role role) {
    AppUser u = new AppUser();
    u.setUsername(name);
    u.setDisplayName(name);
    u.setRole(role);
    u.setPasswordHash(encoder.encode(UUID.randomUUID().toString()));
    return users.saveAndFlush(u);
  }

  void login(AppUser user) {
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken(
                user.getUsername(),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole()))));
  }

  AssetInput assetInput(String code) {
    AssetInput input = new AssetInput();
    input.code = code;
    input.name = "Water pump " + code;
    input.category = "Mechanical";
    input.location = "Plant room";
    input.status = AssetStatus.ACTIVE;
    return input;
  }

  AssetView asset(String code) {
    return assetService.create(assetInput(code));
  }

  OrderView request(Long assetId, LocalDate dueDate) {
    OrderInput input = new OrderInput();
    input.assetId = assetId;
    input.title = "Replace seal";
    input.description = "Leak at coupling";
    input.priority = Priority.HIGH;
    input.dueDate = dueDate;
    return orderService.create(input);
  }

  OrderView assign(OrderView order, AppUser user) {
    AssignmentInput input = new AssignmentInput();
    input.version = order.version;
    input.technicianId = user.getId();
    return orderService.assign(order.id, input);
  }

  StatusInput status(OrderView order, OrderStatus state, String notes) {
    StatusInput input = new StatusInput();
    input.version = order.version;
    input.status = state;
    input.completionNotes = notes;
    return input;
  }

  @Test
  void migrationsAndUniqueCodeAreEnforcedByPostgres() {
    asset("PUMP-1");
    assertThrows(DataIntegrityViolationException.class, () -> asset("pump-1"));
    assertEquals(1, assets.count());
  }

  @Test
  void searchesFiltersAndPaginationRunInDatabase() {
    asset("P-1");
    asset("P-2");
    AssetView retired = asset("X-3");
    assetService.retire(retired.id, retired.version);
    var page = assetService.list("pump", AssetStatus.ACTIVE, "Mechanical", 0, 1, "code", "asc");
    assertEquals(2, page.totalElements);
    assertEquals(2, page.totalPages);
    assertEquals("P-1", page.content.get(0).code);
    var second = assetService.list("pump", AssetStatus.ACTIVE, "Mechanical", 1, 1, "code", "asc");
    assertEquals("P-2", second.content.get(0).code);
    assertEquals(0, assetService.list("%", null, "", 0, 15, "code", "asc").totalElements);
  }

  @Test
  void roleScopedListsDetailsAndHistoryProtectRecords() {
    AssetView asset = asset("P-1");
    login(viewer);
    OrderView order = request(asset.id, null);
    final Long requestedId = order.id;
    assertEquals(1, orderService.list("", null, null, null, 0, 15, "id", "asc").totalElements);
    login(otherViewer);
    assertEquals(0, orderService.dashboard().open);
    assertThrows(Problem.class, () -> orderService.get(requestedId));
    assertThrows(Problem.class, () -> orderService.history(requestedId));
    login(admin);
    order = assign(order, technician);
    Long id = order.id;
    login(otherTech);
    assertEquals(0, orderService.list("", null, null, null, 0, 15, "id", "asc").totalElements);
    assertThrows(Problem.class, () -> orderService.get(id));
    login(technician);
    assertNotNull(orderService.get(id));
    assertEquals(1, orderService.dashboard().open);
  }

  @Test
  void overdueUsesInjectedUtcClockAndOnlyActiveOrders() {
    AssetView asset = asset("P-1");
    request(asset.id, LocalDate.of(2026, 3, 9));
    request(asset.id, LocalDate.of(2026, 3, 10));
    request(asset.id, null);
    OrderView cancelled = request(asset.id, LocalDate.of(2026, 3, 8));
    orderService.changeStatus(cancelled.id, status(cancelled, OrderStatus.CANCELLED, null));
    OrderView complete = assign(request(asset.id, LocalDate.of(2026, 3, 7)), technician);
    login(technician);
    complete =
        orderService.changeStatus(complete.id, status(complete, OrderStatus.IN_PROGRESS, null));
    orderService.changeStatus(
        complete.id, status(complete, OrderStatus.COMPLETED, "Seal replaced"));
    login(admin);
    DashboardView totals = orderService.dashboard();
    assertEquals(1, totals.overdue);
    assertEquals(3, totals.open);
    assertEquals(1, totals.completed);
    assertEquals(0, totals.inProgress);
  }

  @Test
  void stateAndHistoryCommitTogether() {
    OrderView order = assign(request(asset("P-1").id, null), technician);
    login(technician);
    order = orderService.changeStatus(order.id, status(order, OrderStatus.IN_PROGRESS, null));
    order = orderService.changeStatus(order.id, status(order, OrderStatus.COMPLETED, "Repaired"));
    assertEquals(OrderStatus.COMPLETED, orders.findById(order.id).orElseThrow().getStatus());
    var audit = orderService.history(order.id);
    assertEquals(4, audit.size());
    assertEquals("COMPLETED", audit.get(3).newValue);
  }

  @Test
  void failureToWriteHistoryRollsBackStateChange() {
    OrderView order = assign(request(asset("P-1").id, null), technician);
    login(technician);
    doThrow(new IllegalStateException("Simulated audit write failure"))
        .when(history)
        .save(any(OrderHistory.class));
    assertThrows(
        IllegalStateException.class,
        () -> orderService.changeStatus(order.id, status(order, OrderStatus.IN_PROGRESS, null)));
    assertEquals(OrderStatus.OPEN, orders.findById(order.id).orElseThrow().getStatus());
    assertEquals(2, history.findByWorkOrderIdOrderByIdAsc(order.id).size());
  }

  @Test
  void staleApiVersionRejectsChangeWithoutExtraHistory() {
    OrderView original = request(asset("P-1").id, null);
    assign(original, technician);
    Problem error =
        assertThrows(
            Problem.class,
            () ->
                orderService.changeStatus(
                    original.id, status(original, OrderStatus.CANCELLED, null)));
    assertEquals("STALE_UPDATE", error.getCode());
    assertEquals(2, history.findByWorkOrderIdOrderByIdAsc(original.id).size());
  }

  @Test
  void jpaVersionDetectsConcurrentDatabaseWrites() {
    Long id = asset("P-1").id;
    EntityManager first = emf.createEntityManager();
    EntityManager second = emf.createEntityManager();
    try {
      first.getTransaction().begin();
      second.getTransaction().begin();
      Asset a = first.find(Asset.class, id);
      Asset b = second.find(Asset.class, id);
      a.setLocation("Roof");
      first.getTransaction().commit();
      b.setLocation("Basement");
      assertThrows(RollbackException.class, () -> second.getTransaction().commit());
    } finally {
      if (first.getTransaction().isActive()) first.getTransaction().rollback();
      if (second.getTransaction().isActive()) second.getTransaction().rollback();
      first.close();
      second.close();
    }
    assertEquals("Roof", assetService.get(id).location);
  }

  @Test
  void retirementPreservesHistoryAndBlocksNewOrders() {
    AssetView asset = asset("P-1");
    OrderView order = request(asset.id, null);
    assertThrows(Problem.class, () -> assetService.retire(asset.id, asset.version));
    orderService.changeStatus(order.id, status(order, OrderStatus.CANCELLED, null));
    assetService.retire(asset.id, asset.version);
    assertThrows(Problem.class, () -> request(asset.id, null));
    assertEquals(2, orderService.history(order.id).size());
  }

  @Test
  void onlyTechniciansCanBeAssignedAndViewersCannotAssign() {
    OrderView order = request(asset("P-1").id, null);
    assertThrows(Problem.class, () -> assign(order, viewer));
    login(viewer);
    assertThrows(Problem.class, () -> assign(order, technician));
  }

  @Test
  void orderSearchAndStatusPriorityAssetFiltersCombineWithScope() {
    AssetView asset = asset("P-1");
    request(asset.id, null);
    request(asset("P-2").id, null);
    var page =
        orderService.list(
            "seal", OrderStatus.OPEN, Priority.HIGH, asset.id, 0, 1, "createdAt", "desc");
    assertEquals(1, page.totalElements);
    assertEquals(asset.id, page.content.get(0).assetId);
  }
}
