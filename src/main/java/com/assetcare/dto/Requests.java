package com.assetcare.dto;

import com.assetcare.model.*;
import jakarta.validation.constraints.*;
import java.time.LocalDate;

// Simple request objects keep HTTP input separate from persistent entities.
public class Requests {
  public static class AssetInput {
    @NotBlank
    @Size(max = 40)
    @Pattern(regexp = "[A-Za-z0-9_-]+")
    public String code;

    @NotBlank
    @Size(max = 120)
    public String name;

    @NotBlank
    @Size(max = 80)
    public String category;

    @NotBlank
    @Size(max = 120)
    public String location;

    @NotNull public AssetStatus status;
  }

  public static class AssetUpdate extends AssetInput {
    @NotNull @PositiveOrZero public Long version;
  }

  public static class VersionInput {
    @NotNull @PositiveOrZero public Long version;
  }

  public static class OrderInput {
    @NotNull @Positive public Long assetId;

    @NotBlank
    @Size(max = 160)
    public String title;

    @NotBlank
    @Size(max = 4000)
    public String description;

    @NotNull public Priority priority;
    public LocalDate dueDate;
  }

  public static class AssignmentInput extends VersionInput {
    // Null explicitly unassigns an order.
    @Positive public Long technicianId;
  }

  public static class StatusInput extends VersionInput {
    @NotNull public OrderStatus status;

    @Size(max = 4000)
    public String completionNotes;
  }
}
