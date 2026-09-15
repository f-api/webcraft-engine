package com.gameexpert.enchanting.repository;

import com.gameexpert.enchanting.entity.WorldEnchantingTable;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Repository for durable coordinate-owned enchanting-table contents. */
public interface WorldEnchantingTableRepository
        extends JpaRepository<WorldEnchantingTable, Long> {

    @Query("select distinct enchanting from WorldEnchantingTable enchanting "
            + "left join fetch enchanting.items where enchanting.worldId = :worldId")
    List<WorldEnchantingTable> findAllByWorldId(@Param("worldId") Long worldId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select distinct enchanting from WorldEnchantingTable enchanting "
            + "left join fetch enchanting.items where enchanting.worldId = :worldId "
            + "and enchanting.posX = :x and enchanting.posY = :y and enchanting.posZ = :z")
    Optional<WorldEnchantingTable> findLockedAt(@Param("worldId") Long worldId,
            @Param("x") int x, @Param("y") int y, @Param("z") int z);

    void deleteAllByWorldId(Long worldId);
}
