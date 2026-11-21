package com.assetcare.web;

import com.assetcare.dto.Requests.*;
import com.assetcare.dto.Views.*;
import com.assetcare.model.AssetStatus;
import com.assetcare.service.AssetService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/assets")
public class AssetController {
  private final AssetService service;

  public AssetController(AssetService service) {
    this.service = service;
  }

  @GetMapping
  public PageView<AssetView> list(
      @RequestParam(defaultValue = "") String search,
      @RequestParam(required = false) AssetStatus status,
      @RequestParam(defaultValue = "") String category,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "15") int size,
      @RequestParam(defaultValue = "code") String sort,
      @RequestParam(defaultValue = "asc") String direction) {
    return service.list(search, status, category, page, size, sort, direction);
  }

  @GetMapping("/{id}")
  public AssetView get(@PathVariable Long id) {
    return service.get(id);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public AssetView create(@Valid @RequestBody AssetInput input) {
    return service.create(input);
  }

  @PutMapping("/{id}")
  public AssetView update(@PathVariable Long id, @Valid @RequestBody AssetUpdate input) {
    return service.update(id, input);
  }

  @PostMapping("/{id}/retire")
  public AssetView retire(@PathVariable Long id, @Valid @RequestBody VersionInput input) {
    return service.retire(id, input.version);
  }
}
