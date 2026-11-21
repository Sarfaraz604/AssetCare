package com.assetcare.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.assetcare.dto.Requests.*;
import com.assetcare.model.*;
import com.assetcare.repository.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WorkOrderServiceTest {
  @Mock WorkOrderRepository orders;
  @Mock AssetRepository assets;
  @Mock UserRepository users;
  @Mock HistoryRepository history;
  @Mock CurrentUser current;
  WorkOrderService service;
  WorkOrder order;
  AppUser technician;
  AppUser admin;
  final Clock clock = Clock.fixed(Instant.parse("2026-03-10T12:00:00Z"), ZoneOffset.UTC);

  @BeforeEach
  void setup() {
    service = new WorkOrderService(orders, assets, users, history, current, clock);
    technician = mock(AppUser.class);
    admin = mock(AppUser.class);
    // Lenient identity stubs are shared across tests with different permission paths.
    lenient().when(technician.getId()).thenReturn(2L);
    lenient().when(technician.getRole()).thenReturn(Role.TECHNICIAN);
    lenient().when(technician.getUsername()).thenReturn("tech");
    lenient().when(admin.getRole()).thenReturn(Role.ADMIN);
    order = new WorkOrder();
    order.setAsset(new Asset());
    order.setRequester(admin);
    order.setTechnician(technician);
    order.setStatus(OrderStatus.OPEN);
    order.setVersion(0L);
    order.setTitle("Repair pump");
    order.setPriority(Priority.HIGH);
    lenient().when(orders.findById(10L)).thenReturn(Optional.of(order));
  }

  StatusInput status(OrderStatus status, String notes) {
    StatusInput input = new StatusInput();
    input.status = status;
    input.completionNotes = notes;
    input.version = 0L;
    return input;
  }

  @Test
  void assignedTechnicianStartsWorkAndRecordsHistory() {
    when(current.get()).thenReturn(technician);
    var result = service.changeStatus(10L, status(OrderStatus.IN_PROGRESS, null));
    assertEquals(OrderStatus.IN_PROGRESS, result.status);
    ArgumentCaptor<OrderHistory> entry = ArgumentCaptor.forClass(OrderHistory.class);
    verify(history).save(entry.capture());
    assertEquals("OPEN", entry.getValue().getOldValue());
    assertEquals("IN_PROGRESS", entry.getValue().getNewValue());
    assertEquals(clock.instant(), entry.getValue().getCreatedAt());
  }

  @Test
  void completionStoresTrimmedNotesAndTimestamp() {
    when(current.get()).thenReturn(technician);
    order.setStatus(OrderStatus.IN_PROGRESS);
    var result = service.changeStatus(10L, status(OrderStatus.COMPLETED, " Replaced seal "));
    assertEquals("Replaced seal", result.completionNotes);
    assertEquals(clock.instant(), result.completedAt);
  }

  @Test
  void completionRequiresNotes() {
    when(current.get()).thenReturn(technician);
    order.setStatus(OrderStatus.IN_PROGRESS);
    assertThrows(
        Problem.class, () -> service.changeStatus(10L, status(OrderStatus.COMPLETED, "   ")));
    assertEquals(OrderStatus.IN_PROGRESS, order.getStatus());
    verifyNoInteractions(history);
  }

  @ParameterizedTest
  @EnumSource(
      value = OrderStatus.class,
      names = {"OPEN", "COMPLETED"})
  void rejectsInvalidTransitionsFromOpen(OrderStatus target) {
    when(current.get()).thenReturn(technician);
    assertThrows(Problem.class, () -> service.changeStatus(10L, status(target, "notes")));
    verifyNoInteractions(history);
  }

  @ParameterizedTest
  @EnumSource(
      value = OrderStatus.class,
      names = {"COMPLETED", "CANCELLED"})
  void terminalOrdersCannotBeReopened(OrderStatus state) {
    when(current.get()).thenReturn(admin);
    order.setStatus(state);
    assertThrows(Problem.class, () -> service.changeStatus(10L, status(OrderStatus.OPEN, null)));
    verifyNoInteractions(history);
  }

  @Test
  void technicianCannotCancel() {
    when(current.get()).thenReturn(technician);
    assertThrows(
        Problem.class, () -> service.changeStatus(10L, status(OrderStatus.CANCELLED, null)));
    verifyNoInteractions(history);
  }

  @Test
  void adminCancelsActiveOrder() {
    when(current.get()).thenReturn(admin);
    assertEquals(
        OrderStatus.CANCELLED,
        service.changeStatus(10L, status(OrderStatus.CANCELLED, null)).status);
    verify(history).save(any());
  }

  @Test
  void anotherTechnicianCannotReadOrUpdateOrder() {
    AppUser other = mock(AppUser.class);
    when(other.getRole()).thenReturn(Role.TECHNICIAN);
    when(other.getId()).thenReturn(3L);
    when(current.get()).thenReturn(other);
    assertThrows(Problem.class, () -> service.get(10L));
    assertThrows(
        Problem.class, () -> service.changeStatus(10L, status(OrderStatus.IN_PROGRESS, null)));
  }

  @Test
  void viewerCanReadOnlyTheirOwnRequests() {
    AppUser viewer = mock(AppUser.class);
    when(viewer.getRole()).thenReturn(Role.VIEWER);
    when(viewer.getId()).thenReturn(4L);
    when(current.get()).thenReturn(viewer);
    when(admin.getId()).thenReturn(1L);
    assertThrows(Problem.class, () -> service.get(10L));
    order.setRequester(viewer);
    assertNotNull(service.get(10L));
    assertThrows(
        Problem.class, () -> service.changeStatus(10L, status(OrderStatus.IN_PROGRESS, null)));
  }

  @Test
  void assignmentRejectsNonTechnician() {
    when(current.requireAdmin()).thenReturn(admin);
    when(users.findById(1L)).thenReturn(Optional.of(admin));
    AssignmentInput input = new AssignmentInput();
    input.version = 0L;
    input.technicianId = 1L;
    assertThrows(Problem.class, () -> service.assign(10L, input));
    verifyNoInteractions(history);
  }

  @Test
  void adminAssignsTechnicianWithHistory() {
    when(current.requireAdmin()).thenReturn(admin);
    when(users.findById(2L)).thenReturn(Optional.of(technician));
    order.setTechnician(null);
    AssignmentInput input = new AssignmentInput();
    input.version = 0L;
    input.technicianId = 2L;
    assertNotNull(service.assign(10L, input).technician);
    verify(history).save(any());
  }

  @Test
  void staleVersionRejectsBeforeMutation() {
    when(current.get()).thenReturn(technician);
    order.setVersion(1L);
    Problem error =
        assertThrows(
            Problem.class, () -> service.changeStatus(10L, status(OrderStatus.IN_PROGRESS, null)));
    assertEquals("STALE_UPDATE", error.getCode());
    assertEquals(OrderStatus.OPEN, order.getStatus());
    verifyNoInteractions(history);
  }

  @Test
  void retiredAssetRejectsNewRequest() {
    when(current.get()).thenReturn(admin);
    Asset asset = new Asset();
    asset.setStatus(AssetStatus.RETIRED);
    when(assets.findForUpdate(5L)).thenReturn(Optional.of(asset));
    OrderInput input = new OrderInput();
    input.assetId = 5L;
    assertThrows(Problem.class, () -> service.create(input));
    verify(orders, never()).saveAndFlush(any());
    verifyNoInteractions(history);
  }

  @ParameterizedTest
  @EnumSource(
      value = OrderStatus.class,
      names = {"OPEN", "IN_PROGRESS"})
  void inProgressCannotMoveBackOrRepeat(OrderStatus target) {
    when(current.get()).thenReturn(technician);
    order.setStatus(OrderStatus.IN_PROGRESS);
    assertThrows(Problem.class, () -> service.changeStatus(10L, status(target, null)));
    verifyNoInteractions(history);
  }

  @Test
  void adminCannotPerformTechnicianStatusActions() {
    when(current.get()).thenReturn(admin);
    assertThrows(
        Problem.class, () -> service.changeStatus(10L, status(OrderStatus.IN_PROGRESS, null)));
    verifyNoInteractions(history);
  }

  @Test
  void terminalOrderCannotBeReassigned() {
    when(current.requireAdmin()).thenReturn(admin);
    order.setStatus(OrderStatus.COMPLETED);
    AssignmentInput input = new AssignmentInput();
    input.version = 0L;
    input.technicianId = 2L;
    assertThrows(Problem.class, () -> service.assign(10L, input));
    verifyNoInteractions(history);
  }
}
