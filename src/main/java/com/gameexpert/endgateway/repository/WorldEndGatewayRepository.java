package com.gameexpert.endgateway.repository;

import com.gameexpert.endgateway.entity.WorldEndGateway;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** [END-GATEWAY] 월드별 엔드 관문 블록 엔티티 행. */
public interface WorldEndGatewayRepository extends JpaRepository<WorldEndGateway, Long> {
    @Query("SELECT g FROM WorldEndGateway g WHERE g.world.id = :worldId")
    List<WorldEndGateway> findAllByWorldId(@Param("worldId") Long worldId);

    @Query("SELECT g FROM WorldEndGateway g WHERE g.world.id = :worldId AND g.x = :x AND g.y = :y AND g.z = :z")
    Optional<WorldEndGateway> findAt(@Param("worldId") Long worldId, @Param("x") int x,
            @Param("y") int y, @Param("z") int z);

    @Modifying
    @Query("DELETE FROM WorldEndGateway g WHERE g.world.id = :worldId")
    void deleteAllByWorldId(@Param("worldId") Long worldId);
}
