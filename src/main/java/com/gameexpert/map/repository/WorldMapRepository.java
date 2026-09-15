package com.gameexpert.map.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.gameexpert.map.entity.WorldMap;

public interface WorldMapRepository extends JpaRepository<WorldMap, Long> {

    List<WorldMap> findByWorld_IdOrderByMapIdAsc(Long worldId);

    Optional<WorldMap> findByWorld_IdAndMapId(Long worldId, int mapId);

    void deleteAllByWorld_Id(Long worldId);
}
