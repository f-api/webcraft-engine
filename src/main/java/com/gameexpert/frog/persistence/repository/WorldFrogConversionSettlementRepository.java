package com.gameexpert.frog.persistence.repository;

import com.gameexpert.frog.persistence.entity.WorldFrogConversionSettlement;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorldFrogConversionSettlementRepository
        extends JpaRepository<WorldFrogConversionSettlement, Long> {
    Optional<WorldFrogConversionSettlement> findByWorldIdAndSourceMobIdAndSequence(
            Long worldId, long sourceMobId, long sequence);

    List<WorldFrogConversionSettlement> findAllByWorldIdAndStatusNotOrderByIdAsc(
            Long worldId, WorldFrogConversionSettlement.Status excludedStatus);

    void deleteAllByWorldId(Long worldId);
}
