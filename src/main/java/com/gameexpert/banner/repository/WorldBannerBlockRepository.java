package com.gameexpert.banner.repository;

import com.gameexpert.banner.entity.WorldBannerBlock;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WorldBannerBlockRepository extends JpaRepository<WorldBannerBlock, Long> {
    List<WorldBannerBlock> findAllByWorldId(Long worldId);

    List<WorldBannerBlock> findAllByWorldIdAndChunkXAndChunkZ(Long worldId, int chunkX, int chunkZ);

    Optional<WorldBannerBlock> findByWorldIdAndXAndYAndZ(Long worldId, int x, int y, int z);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM WorldBannerBlock b WHERE b.world.id = :worldId "
            + "AND b.x = :x AND b.y = :y AND b.z = :z")
    Optional<WorldBannerBlock> findLockedAt(@Param("worldId") Long worldId,
            @Param("x") int x, @Param("y") int y, @Param("z") int z);

    @Modifying
    @Query("DELETE FROM WorldBannerBlock b WHERE b.world.id = :worldId AND b.x = :x AND b.y = :y AND b.z = :z")
    int deleteAt(@Param("worldId") Long worldId, @Param("x") int x,
            @Param("y") int y, @Param("z") int z);

    @Modifying
    @Query("DELETE FROM WorldBannerBlock b WHERE b.world.id = :worldId")
    void deleteAllByWorldId(@Param("worldId") Long worldId);
}
