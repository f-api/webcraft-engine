package com.gameexpert.sign.repository;

import com.gameexpert.sign.entity.WorldSignBlock;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WorldSignBlockRepository extends JpaRepository<WorldSignBlock, Long> {
    List<WorldSignBlock> findAllByWorldId(Long worldId);
    Optional<WorldSignBlock> findByWorldIdAndXAndYAndZ(Long worldId, int x, int y, int z);
    @Modifying
    @Query("DELETE FROM WorldSignBlock s WHERE s.world.id=:worldId AND s.x=:x AND s.y=:y AND s.z=:z")
    void deleteAt(@Param("worldId") Long worldId, @Param("x") int x,
            @Param("y") int y, @Param("z") int z);
    @Modifying
    @Query("DELETE FROM WorldSignBlock s WHERE s.world.id=:worldId")
    void deleteAllByWorldId(@Param("worldId") Long worldId);
}
