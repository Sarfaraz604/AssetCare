package com.assetcare.service;

import com.assetcare.dto.Requests.*;
import com.assetcare.dto.Views.*;
import com.assetcare.model.*;
import com.assetcare.repository.*;
import java.time.Clock;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AssetService {
  private final AssetRepository assets;
  private final WorkOrderRepository orders;
  private final CurrentUser current;
  private final Clock clock;

  public AssetService(
      AssetRepository assets, WorkOrderRepository orders, CurrentUser current, Clock clock) {
    this.assets = assets;
    this.orders = orders;
    this.current = current;
    this.clock = clock;
  }

  public AssetView get(Long id) {
    current.get();
    return new AssetView(assets.findById(id).orElseThrow(Problem::missing));
  }

  public PageView<AssetView> list(
      String search,
      AssetStatus status,
      String category,
      int page,
      int size,
      String sort,
      String direction) {
    current.get();
    Specification<Asset> spec = (root, query, cb) -> cb.conjunction();
    if (!search.isBlank())
      spec =
          spec.and(
              (root, query, cb) ->
                  cb.or(
                      cb.like(cb.lower(root.get("name")), Paging.contains(search), '\\'),
                      cb.like(cb.lower(root.get("code")), Paging.contains(search), '\\'),
                      cb.like(cb.lower(root.get("location")), Paging.contains(search), '\\')));
    if (status != null) spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), status));
    if (!category.isBlank())
      spec = spec.and((root, query, cb) -> cb.equal(root.get("category"), category));
    Page<Asset> result =
        assets.findAll(
            spec,
            Paging.page(
                page,
                size,
                sort,
                direction,
                Set.of(
                    "id",
                    "code",
                    "name",
                    "category",
                    "location",
                    "status",
                    "createdAt",
                    "updatedAt")));
    List<AssetView> content = new ArrayList<>();
    for (Asset a : result) content.add(new AssetView(a));
    return new PageView<>(content, result.getTotalElements(), result.getTotalPages(), page);
  }

  @Transactional
  public AssetView create(AssetInput input) {
    current.requireAdmin();
    if (input.status == AssetStatus.RETIRED)
      throw Problem.rule("Create an active asset first; retirement is a separate action.");
    Asset asset = new Asset();
    copy(asset, input);
    asset.setCreatedAt(clock.instant());
    asset.setUpdatedAt(clock.instant());
    return new AssetView(assets.saveAndFlush(asset));
  }

  @Transactional
  public AssetView update(Long id, AssetUpdate input) {
    current.requireAdmin();
    Asset asset = assets.findForUpdate(id).orElseThrow(Problem::missing);
    Problem.checkVersion(asset.getVersion(), input.version);
    if (asset.getStatus() == AssetStatus.RETIRED || input.status == AssetStatus.RETIRED)
      throw Problem.rule("Retired assets are read-only. Use the separate retirement action.");
    copy(asset, input);
    asset.setUpdatedAt(clock.instant());
    assets.flush();
    return new AssetView(asset);
  }

  @Transactional
  public AssetView retire(Long id, Long version) {
    current.requireAdmin();
    Asset asset = assets.findForUpdate(id).orElseThrow(Problem::missing);
    Problem.checkVersion(asset.getVersion(), version);
    if (asset.getStatus() == AssetStatus.RETIRED)
      throw Problem.rule("This asset is already retired.");
    if (orders.existsByAssetIdAndStatusIn(id, List.of(OrderStatus.OPEN, OrderStatus.IN_PROGRESS)))
      throw Problem.rule("Resolve or cancel all active work orders before retiring this asset.");
    asset.setStatus(AssetStatus.RETIRED);
    asset.setUpdatedAt(clock.instant());
    assets.flush();
    return new AssetView(asset);
  }

  private void copy(Asset asset, AssetInput input) {
    asset.setCode(input.code.trim().toUpperCase(Locale.ROOT));
    asset.setName(input.name.trim());
    asset.setCategory(input.category.trim());
    asset.setLocation(input.location.trim());
    asset.setStatus(input.status);
  }
}
