package com.assetcare.repository;

import com.assetcare.model.Asset;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface AssetRepository
    extends JpaRepository<Asset, Long>, JpaSpecificationExecutor<Asset> {
  boolean existsByCode(String code);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select a from Asset a where a.id = :id")
  Optional<Asset> findForUpdate(@Param("id") Long id);
}
