package com.gameexpert.engine.persistence.animal;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 동물 블록 scheduled tick 저장소입니다. */
public interface WorldAnimalBlockTickRepository
        extends JpaRepository<WorldAnimalBlockTick, Long> {

    List<WorldAnimalBlockTick> findAllByWorldIdOrderByDueTickAsc(Long worldId);

    Optional<WorldAnimalBlockTick> findByWorldIdAndXAndYAndZAndKind(
            Long worldId, int x, int y, int z, String kind);

    @Modifying
    @Query("delete from WorldAnimalBlockTick row where row.worldId = :worldId "
            + "and row.x = :x and row.y = :y and row.z = :z and row.kind = :kind")
    int deleteOne(@Param("worldId") Long worldId,
            @Param("x") int x, @Param("y") int y, @Param("z") int z,
            @Param("kind") String kind);

    @Modifying
    @Query("delete from WorldAnimalBlockTick row where row.worldId = :worldId "
            + "and row.x = :x and row.y = :y and row.z = :z")
    int deleteAt(@Param("worldId") Long worldId, @Param("x") int x,
            @Param("y") int y, @Param("z") int z);

    @Modifying
    @Query("delete from WorldAnimalBlockTick row where row.worldId = :worldId")
    void deleteAllByWorldId(@Param("worldId") Long worldId);
}
