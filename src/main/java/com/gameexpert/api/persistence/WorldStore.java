package com.gameexpert.api.persistence;

import java.util.List;
import java.util.Optional;
import com.gameexpert.engine.Difficulty;
import com.gameexpert.world.WorldGenerationProfile;

public interface WorldStore {
    Optional<WorldAccess> findById(Long id);
    WorldAccess getReferenceById(Long id);
    Optional<WorldAccess> findByIdForUpdate(Long id);
    Optional<WorldAccess> findByIdForShare(Long id);
    Optional<WorldAccess> findByIdForShareNoWait(Long id);
    List<WorldAccess> findAll();
    List<WorldAccess> findRootWorlds();
    boolean existsById(Long id);
    boolean isDimensionChild(Long id);
    long countRootWorlds();
    WorldAccess save(WorldAccess world);
    WorldAccess saveAndFlush(WorldAccess world);
    void delete(WorldAccess world);
    WorldAccess create(String name, long seed, Difficulty difficulty, String ownerNickname,
            WorldGenerationProfile profile);
}
