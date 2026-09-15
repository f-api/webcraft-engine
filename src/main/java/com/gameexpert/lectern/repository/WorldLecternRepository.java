package com.gameexpert.lectern.repository;

import com.gameexpert.lectern.entity.WorldLectern;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface WorldLecternRepository extends JpaRepository<WorldLectern, Long> {
    List<WorldLectern> findAllByWorldId(Long worldId);
    List<WorldLectern> findAllByWorldIdAndChunkXAndChunkZ(Long worldId, int chunkX, int chunkZ);
    Optional<WorldLectern> findByWorldIdAndXAndYAndZ(Long worldId, int x, int y, int z);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT l FROM WorldLectern l WHERE l.world.id = :worldId "
            + "AND l.x = :x AND l.y = :y AND l.z = :z")
    Optional<WorldLectern> findLockedAt(@Param("worldId") Long worldId,
            @Param("x") int x, @Param("y") int y, @Param("z") int z);

    @Modifying
    @Query("DELETE FROM WorldLectern l WHERE l.world.id = :worldId")
    void deleteAllByWorldId(@Param("worldId") Long worldId);
}
