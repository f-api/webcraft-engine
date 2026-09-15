package com.gameexpert.tnt.repository;

import com.gameexpert.tnt.entity.WorldExplosionSettlement;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Durable lookup, allocator high-water and world-purge access for explosion receipts. */
public interface WorldExplosionSettlementRepository
        extends JpaRepository<WorldExplosionSettlement, Long> {

    Optional<WorldExplosionSettlement> findByWorldIdAndExplosionId(
            Long worldId, long explosionId);

    @Query("select max(receipt.highestGroundEntityId) from WorldExplosionSettlement receipt "
            + "where receipt.worldId = :worldId")
    Long findMaximumGroundEntityIdByWorldId(@Param("worldId") Long worldId);

    @Query("select max(receipt.highestTntId) from WorldExplosionSettlement receipt "
            + "where receipt.worldId = :worldId")
    Long findMaximumTntIdByWorldId(@Param("worldId") Long worldId);

    void deleteAllByWorldId(Long worldId);
}
