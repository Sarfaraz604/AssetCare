package com.assetcare.repository;

import com.assetcare.model.OrderHistory;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HistoryRepository extends JpaRepository<OrderHistory, Long> {
  List<OrderHistory> findByWorkOrderIdOrderByIdAsc(Long workOrderId);
}
