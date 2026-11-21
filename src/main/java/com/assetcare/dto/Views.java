package com.assetcare.dto;

import com.assetcare.model.*;
import java.time.*;
import java.util.*;

public class Views {
  public static class UserView {
    public Long id;
    public String username;
    public String displayName;
    public Role role;

    public UserView(AppUser u) {
      id = u.getId();
      username = u.getUsername();
      displayName = u.getDisplayName();
      role = u.getRole();
    }
  }

  public static class AssetView {
    public Long id;
    public String code;
    public String name;
    public String category;
    public String location;
    public AssetStatus status;
    public Long version;
    public Instant createdAt;
    public Instant updatedAt;

    public AssetView(Asset a) {
      id = a.getId();
      code = a.getCode();
      name = a.getName();
      category = a.getCategory();
      location = a.getLocation();
      status = a.getStatus();
      version = a.getVersion();
      createdAt = a.getCreatedAt();
      updatedAt = a.getUpdatedAt();
    }
  }

  public static class OrderView {
    public Long id;
    public Long assetId;
    public String assetCode;
    public String assetName;
    public String title;
    public String description;
    public Priority priority;
    public OrderStatus status;
    public UserView requester;
    public UserView technician;
    public LocalDate dueDate;
    public String completionNotes;
    public Instant completedAt;
    public Long version;
    public Instant createdAt;
    public Instant updatedAt;

    public OrderView(WorkOrder o) {
      id = o.getId();
      assetId = o.getAsset().getId();
      assetCode = o.getAsset().getCode();
      assetName = o.getAsset().getName();
      title = o.getTitle();
      description = o.getDescription();
      priority = o.getPriority();
      status = o.getStatus();
      requester = new UserView(o.getRequester());
      if (o.getTechnician() != null) technician = new UserView(o.getTechnician());
      dueDate = o.getDueDate();
      completionNotes = o.getCompletionNotes();
      completedAt = o.getCompletedAt();
      version = o.getVersion();
      createdAt = o.getCreatedAt();
      updatedAt = o.getUpdatedAt();
    }
  }

  public static class HistoryView {
    public Long id;
    public String actor;
    public String kind;
    public String oldValue;
    public String newValue;
    public Instant createdAt;

    public HistoryView(OrderHistory h) {
      id = h.getId();
      actor = h.getActor().getDisplayName();
      kind = h.getKind();
      oldValue = h.getOldValue();
      newValue = h.getNewValue();
      createdAt = h.getCreatedAt();
    }
  }

  public static class PageView<T> {
    public List<T> content;
    public long totalElements;
    public int totalPages;
    public int number;

    public PageView(List<T> content, long total, int pages, int number) {
      this.content = content;
      totalElements = total;
      totalPages = pages;
      this.number = number;
    }
  }

  public static class DashboardView {
    public long totalAssets;
    public long open;
    public long inProgress;
    public long overdue;
    public long completed;
    public List<OrderView> recent;
  }
}
