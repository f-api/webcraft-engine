package com.gameexpert.jukebox.repository;

import com.gameexpert.jukebox.entity.WorldJukebox;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WorldJukeboxRepository extends JpaRepository<WorldJukebox, Long> {
    List<WorldJukebox> findAllByWorldId(Long worldId);
    Optional<WorldJukebox> findByWorldIdAndXAndYAndZ(Long worldId, int x, int y, int z);

    @Modifying
    @Query("DELETE FROM WorldJukebox j WHERE j.world.id = :worldId")
    void deleteAllByWorldId(@Param("worldId") Long worldId);
}
