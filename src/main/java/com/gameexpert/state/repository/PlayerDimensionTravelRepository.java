package com.gameexpert.state.repository;

import com.gameexpert.state.entity.PlayerDimensionTravel;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlayerDimensionTravelRepository extends JpaRepository<PlayerDimensionTravel, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from PlayerDimensionTravel t where t.root.id = :root and t.player.id = :player")
    Optional<PlayerDimensionTravel> findLocked(@Param("root") Long root, @Param("player") Long player);
    @Query("select t from PlayerDimensionTravel t where t.root.id = :root and t.player.id = :player")
    Optional<PlayerDimensionTravel> findCurrent(@Param("root") Long root, @Param("player") Long player);
    @Modifying
    @Query("delete from PlayerDimensionTravel t where t.root.id = :root")
    void deleteForRoot(@Param("root") Long root);
}
