package com.gameexpert.container.persistence;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorldContainerSettlementRepository
        extends JpaRepository<WorldContainerSettlement, Long> {
    Optional<WorldContainerSettlement> findByWorldIdAndSettlementId(
            Long worldId, long settlementId);

    void deleteAllByWorldId(Long worldId);
}
