package com.gameexpert.ground.repository;

import com.gameexpert.ground.entity.WorldGroundMutationReceipt;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WorldGroundMutationReceiptRepository
    extends JpaRepository<WorldGroundMutationReceipt, Long> {
    Optional<WorldGroundMutationReceipt> findByWorldIdAndMutationId(Long worldId, long mutationId);

    @Query("select max(receipt.highestEntityId) from WorldGroundMutationReceipt receipt "
            + "where receipt.worldId = :worldId")
    Long findMaximumEntityIdByWorldId(@Param("worldId") Long worldId);

    long countByWorldId(Long worldId);

    @Modifying
    @Query("delete from WorldGroundMutationReceipt receipt where receipt.worldId = :worldId")
    void deleteAllByWorldId(@Param("worldId") Long worldId);
}
