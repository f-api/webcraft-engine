package com.gameexpert.brewing.repository;

import java.util.Collection;
import java.util.List;

import com.gameexpert.brewing.entity.WorldBrewingStand;

public interface WorldBrewingStandRepositoryCustom {
    List<WorldBrewingStand> findDirtyByWorldId(Long worldId, Collection<int[]> positions);
}
