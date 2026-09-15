package com.gameexpert.cushion.repository;

import com.gameexpert.cushion.entity.WorldCushion;
import java.util.List;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorldCushionRepository extends JpaRepository<WorldCushion, Long> {
    List<WorldCushion> findAllByWorldId(Long worldId);

    @Modifying
    @Query("delete from WorldCushion cushion where cushion.worldId = :worldId")
    void deleteAllByWorldId(@Param("worldId") Long worldId);
}
