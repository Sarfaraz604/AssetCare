package com.assetcare.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.assetcare.dto.Requests.*;
import com.assetcare.model.*;
import com.assetcare.repository.*;
import java.time.Clock;
import java.util.*;
import org.junit.jupiter.api.*;

class AssetServiceTest {
  AssetRepository assets = mock(AssetRepository.class);
  WorkOrderRepository orders = mock(WorkOrderRepository.class);
  CurrentUser current = mock(CurrentUser.class);
  AssetService service = new AssetService(assets, orders, current, Clock.systemUTC());

  @Test
  void retirementBlockedByUnfinishedOrders() {
    Asset asset = new Asset();
    asset.setStatus(AssetStatus.ACTIVE);
    asset.setVersion(0L);
    when(assets.findForUpdate(1L)).thenReturn(Optional.of(asset));
    when(orders.existsByAssetIdAndStatusIn(eq(1L), anyList())).thenReturn(true);
    assertThrows(Problem.class, () -> service.retire(1L, 0L));
    assertEquals(AssetStatus.ACTIVE, asset.getStatus());
  }

  @Test
  void retirementPreservesRecord() {
    Asset asset = new Asset();
    asset.setStatus(AssetStatus.ACTIVE);
    asset.setVersion(0L);
    when(assets.findForUpdate(1L)).thenReturn(Optional.of(asset));
    assertEquals(AssetStatus.RETIRED, service.retire(1L, 0L).status);
    verify(assets, never()).delete(any(Asset.class));
  }

  @Test
  void viewerCannotCreateAsset() {
    when(current.requireAdmin()).thenThrow(Problem.forbidden());
    assertThrows(Problem.class, () -> service.create(new AssetInput()));
    verifyNoInteractions(assets);
  }

  @Test
  void paginationRejectsUnsafeFieldsAndOversizePages() {
    assertThrows(Problem.class, () -> Paging.page(0, 101, "code", "asc", Set.of("code")));
    assertThrows(Problem.class, () -> Paging.page(0, 10, "passwordHash", "asc", Set.of("code")));
  }
}
