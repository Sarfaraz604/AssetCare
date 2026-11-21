package com.assetcare.web;

import com.assetcare.dto.Requests.*;
import com.assetcare.dto.Views.*;
import com.assetcare.model.*;
import com.assetcare.service.WorkOrderService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class WorkOrderController {
  private final WorkOrderService service;

  public WorkOrderController(WorkOrderService service) {
    this.service = service;
  }

  @GetMapping("/work-orders")
  public PageView<OrderView> list(
      @RequestParam(defaultValue = "") String search,
      @RequestParam(required = false) OrderStatus status,
      @RequestParam(required = false) Priority priority,
      @RequestParam(required = false) Long assetId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "15") int size,
      @RequestParam(defaultValue = "createdAt") String sort,
      @RequestParam(defaultValue = "desc") String direction) {
    return service.list(search, status, priority, assetId, page, size, sort, direction);
  }

  @GetMapping("/work-orders/{id}")
  public OrderView get(@PathVariable Long id) {
    return service.get(id);
  }

  @GetMapping("/work-orders/{id}/history")
  public List<HistoryView> history(@PathVariable Long id) {
    return service.history(id);
  }

  @PostMapping("/work-orders")
  @ResponseStatus(HttpStatus.CREATED)
  public OrderView create(@Valid @RequestBody OrderInput input) {
    return service.create(input);
  }

  @PostMapping("/work-orders/{id}/assignment")
  public OrderView assign(@PathVariable Long id, @Valid @RequestBody AssignmentInput input) {
    return service.assign(id, input);
  }

  @PostMapping("/work-orders/{id}/status")
  public OrderView status(@PathVariable Long id, @Valid @RequestBody StatusInput input) {
    return service.changeStatus(id, input);
  }

  @GetMapping("/technicians")
  public List<UserView> technicians() {
    return service.technicians();
  }

  @GetMapping("/dashboard")
  public DashboardView dashboard() {
    return service.dashboard();
  }
}
