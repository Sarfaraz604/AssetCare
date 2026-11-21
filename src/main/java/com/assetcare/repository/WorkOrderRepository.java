package com.assetcare.repository;

import com.assetcare.model.*;
import java.util.List;
import org.springframework.data.jpa.repository.*;

public interface WorkOrderRepository
    extends JpaRepository<WorkOrder, Long>, JpaSpecificationExecutor<WorkOrder> {
  boolean existsByAssetIdAndStatusIn(Long assetId, List<OrderStatus> statuses);
}
