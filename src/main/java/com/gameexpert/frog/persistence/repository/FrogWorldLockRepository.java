package com.gameexpert.frog.persistence.repository;

import com.gameexpert.api.persistence.WorldAccess;
import com.gameexpert.api.persistence.WorldStore;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/** Serializes colony reservation, including first creation where no colony row exists yet. */
@Repository
@RequiredArgsConstructor
public class FrogWorldLockRepository {
    private final WorldStore worlds;

    public Optional<WorldAccess> lockById(Long worldId) {
        return worlds.findByIdForUpdate(worldId);
    }
}
