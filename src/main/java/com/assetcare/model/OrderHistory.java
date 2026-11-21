package com.assetcare.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "order_history")
public class OrderHistory {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "work_order_id")
  private WorkOrder workOrder;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "actor_id")
  private AppUser actor;

  @Column(nullable = false, length = 20)
  private String kind;

  @Column(length = 160)
  private String oldValue;

  @Column(nullable = false, length = 160)
  private String newValue;

  @Column(nullable = false)
  private Instant createdAt;

  public OrderHistory() {}

  public Long getId() {
    return id;
  }

  public WorkOrder getWorkOrder() {
    return workOrder;
  }

  public void setWorkOrder(WorkOrder workOrder) {
    this.workOrder = workOrder;
  }

  public AppUser getActor() {
    return actor;
  }

  public void setActor(AppUser actor) {
    this.actor = actor;
  }

  public String getKind() {
    return kind;
  }

  public void setKind(String kind) {
    this.kind = kind;
  }

  public String getOldValue() {
    return oldValue;
  }

  public void setOldValue(String oldValue) {
    this.oldValue = oldValue;
  }

  public String getNewValue() {
    return newValue;
  }

  public void setNewValue(String newValue) {
    this.newValue = newValue;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }
}
