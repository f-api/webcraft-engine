package com.gameexpert.banner.repository;

import com.gameexpert.banner.entity.WorldBannerSettlement;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WorldBannerSettlementRepository extends JpaRepository<WorldBannerSettlement, Long> {
    Optional<WorldBannerSettlement> findByWorldIdAndSettlementId(Long worldId, long settlementId);

    @Modifying
    @Query("DELETE FROM WorldBannerSettlement s WHERE s.worldId = :worldId")
    void deleteAllByWorldId(@Param("worldId") Long worldId);
}
