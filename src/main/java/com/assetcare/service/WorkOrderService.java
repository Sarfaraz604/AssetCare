package com.assetcare.service;

import com.assetcare.dto.Requests.*;
import com.assetcare.dto.Views.*;
import com.assetcare.model.*;
import com.assetcare.repository.*;
import java.time.*;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class WorkOrderService {
  private final WorkOrderRepository orders;
  private final AssetRepository assets;
  private final UserRepository users;
  private final HistoryRepository history;
  private final CurrentUser current;
  private final Clock clock;

  public WorkOrderService(
      WorkOrderRepository orders,
      AssetRepository assets,
      UserRepository users,
      HistoryRepository history,
      CurrentUser current,
      Clock clock) {
    this.orders = orders;
    this.assets = assets;
    this.users = users;
    this.history = history;
    this.current = current;
    this.clock = clock;
  }

  public Specification<WorkOrder> visible(AppUser user) {
    if (user.getRole() == Role.ADMIN) return (root, query, cb) -> cb.conjunction();
    if (user.getRole() == Role.TECHNICIAN)
      return (root, query, cb) -> cb.equal(root.get("technician").get("id"), user.getId());
    return (root, query, cb) -> cb.equal(root.get("requester").get("id"), user.getId());
  }

  private WorkOrder accessible(Long id, AppUser user) {
    WorkOrder order = orders.findById(id).orElseThrow(Problem::missing);
    if (user.getRole() == Role.ADMIN) return order;
    boolean allowed;
    if (user.getRole() == Role.TECHNICIAN)
      allowed = order.getTechnician() != null && order.getTechnician().getId().equals(user.getId());
    else allowed = order.getRequester().getId().equals(user.getId());
    if (!allowed) throw Problem.forbidden();
    return order;
  }

  public OrderView get(Long id) {
    return new OrderView(accessible(id, current.get()));
  }

  public List<HistoryView> history(Long id) {
    accessible(id, current.get());
    List<HistoryView> result = new ArrayList<>();
    for (OrderHistory h : history.findByWorkOrderIdOrderByIdAsc(id)) result.add(new HistoryView(h));
    return result;
  }

  public List<UserView> technicians() {
    current.requireAdmin();
    List<UserView> result = new ArrayList<>();
    for (AppUser u : users.findByRoleOrderByDisplayName(Role.TECHNICIAN))
      result.add(new UserView(u));
    return result;
  }

  public PageView<OrderView> list(
      String search,
      OrderStatus status,
      Priority priority,
      Long assetId,
      int page,
      int size,
      String sort,
      String direction) {
    Specification<WorkOrder> spec = visible(current.get());
    if (!search.isBlank())
      spec =
          spec.and(
              (root, query, cb) ->
                  cb.like(cb.lower(root.get("title")), Paging.contains(search), '\\'));
    if (status != null) spec = spec.and(statusIs(status));
    if (priority != null)
      spec = spec.and((root, query, cb) -> cb.equal(root.get("priority"), priority));
    if (assetId != null)
      spec = spec.and((root, query, cb) -> cb.equal(root.get("asset").get("id"), assetId));
    Page<WorkOrder> result =
        orders.findAll(
            spec,
            Paging.page(
                page,
                size,
                sort,
                direction,
                Set.of("id", "title", "priority", "status", "dueDate", "createdAt", "updatedAt")));
    List<OrderView> content = new ArrayList<>();
    for (WorkOrder o : result) content.add(new OrderView(o));
    return new PageView<>(content, result.getTotalElements(), result.getTotalPages(), page);
  }

  @Transactional
  public OrderView create(OrderInput input) {
    AppUser actor = current.get();
    if (actor.getRole() == Role.TECHNICIAN) throw Problem.forbidden();
    // Share the asset row lock with retirement to prevent a create/retire race.
    Asset asset = assets.findForUpdate(input.assetId).orElseThrow(Problem::missing);
    if (asset.getStatus() == AssetStatus.RETIRED)
      throw Problem.rule("Retired assets cannot receive new maintenance requests.");
    WorkOrder order = new WorkOrder();
    order.setAsset(asset);
    order.setTitle(input.title.trim());
    order.setDescription(input.description.trim());
    order.setPriority(input.priority);
    order.setStatus(OrderStatus.OPEN);
    order.setRequester(actor);
    order.setDueDate(input.dueDate);
    order.setCreatedAt(clock.instant());
    order.setUpdatedAt(clock.instant());
    orders.saveAndFlush(order);
    record(order, actor, "STATUS", null, "OPEN");
    return new OrderView(order);
  }

  @Transactional
  public OrderView assign(Long id, AssignmentInput input) {
    AppUser actor = current.requireAdmin();
    WorkOrder order = accessible(id, actor);
    Problem.checkVersion(order.getVersion(), input.version);
    requireActive(order);
    AppUser tech = null;
    if (input.technicianId != null) {
      tech = users.findById(input.technicianId).orElseThrow(Problem::missing);
      if (tech.getRole() != Role.TECHNICIAN)
        throw Problem.rule("Only a technician can be assigned.");
    }
    Long oldId = order.getTechnician() == null ? null : order.getTechnician().getId();
    if (Objects.equals(oldId, input.technicianId))
      throw Problem.rule("Choose a different technician or unassign this order.");
    String oldName =
        order.getTechnician() == null ? "Unassigned" : order.getTechnician().getUsername();
    order.setTechnician(tech);
    order.setUpdatedAt(clock.instant());
    record(order, actor, "ASSIGNMENT", oldName, tech == null ? "Unassigned" : tech.getUsername());
    orders.flush();
    return new OrderView(order);
  }

  @Transactional
  public OrderView changeStatus(Long id, StatusInput input) {
    AppUser actor = current.get();
    WorkOrder order = accessible(id, actor);
    Problem.checkVersion(order.getVersion(), input.version);
    requireActive(order);
    OrderStatus old = order.getStatus();
    if (input.status == OrderStatus.CANCELLED) {
      if (actor.getRole() != Role.ADMIN) throw Problem.forbidden();
    } else {
      if (actor.getRole() != Role.TECHNICIAN
          || order.getTechnician() == null
          || !order.getTechnician().getId().equals(actor.getId())) throw Problem.forbidden();
      boolean valid =
          (old == OrderStatus.OPEN && input.status == OrderStatus.IN_PROGRESS)
              || (old == OrderStatus.IN_PROGRESS && input.status == OrderStatus.COMPLETED);
      if (!valid)
        throw Problem.rule(
            "Allowed transitions are OPEN to IN_PROGRESS and IN_PROGRESS to COMPLETED.");
      if (input.status == OrderStatus.COMPLETED) {
        if (input.completionNotes == null || input.completionNotes.isBlank())
          throw Problem.rule("Completion notes are required.");
        order.setCompletionNotes(input.completionNotes.trim());
        order.setCompletedAt(clock.instant());
      }
    }
    order.setStatus(input.status);
    order.setUpdatedAt(clock.instant());
    record(order, actor, "STATUS", old.name(), input.status.name());
    orders.flush();
    return new OrderView(order);
  }

  private void requireActive(WorkOrder order) {
    if (order.getStatus() == OrderStatus.COMPLETED || order.getStatus() == OrderStatus.CANCELLED)
      throw Problem.rule("Completed and cancelled work orders cannot be changed or reopened.");
  }

  private void record(
      WorkOrder order, AppUser actor, String kind, String oldValue, String newValue) {
    OrderHistory entry = new OrderHistory();
    entry.setWorkOrder(order);
    entry.setActor(actor);
    entry.setKind(kind);
    entry.setOldValue(oldValue);
    entry.setNewValue(newValue);
    entry.setCreatedAt(clock.instant());
    history.save(entry);
  }

  public Specification<WorkOrder> statusIs(OrderStatus status) {
    return (root, query, cb) -> cb.equal(root.get("status"), status);
  }

  public DashboardView dashboard() {
    AppUser user = current.get();
    Specification<WorkOrder> scope = visible(user);
    DashboardView view = new DashboardView();
    // All signed-in roles can see the asset catalogue; orders use record-level scope.
    view.totalAssets = assets.count();
    view.open = orders.count(scope.and(statusIs(OrderStatus.OPEN)));
    view.inProgress = orders.count(scope.and(statusIs(OrderStatus.IN_PROGRESS)));
    view.completed = orders.count(scope.and(statusIs(OrderStatus.COMPLETED)));
    LocalDate today = LocalDate.now(clock);
    Specification<WorkOrder> overdue =
        (root, query, cb) ->
            cb.and(
                root.get("status").in(OrderStatus.OPEN, OrderStatus.IN_PROGRESS),
                cb.lessThan(root.get("dueDate"), today));
    view.overdue = orders.count(scope.and(overdue));
    view.recent = new ArrayList<>();
    for (WorkOrder o :
        orders.findAll(
            scope, PageRequest.of(0, 5, Sort.by(Sort.Direction.DESC, "createdAt", "id"))))
      view.recent.add(new OrderView(o));
    return view;
  }
}
