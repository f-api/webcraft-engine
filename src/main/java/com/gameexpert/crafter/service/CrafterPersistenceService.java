package com.gameexpert.crafter.service;

import com.gameexpert.crafter.entity.WorldCrafter;
import com.gameexpert.crafter.repository.WorldCrafterRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * [CONTAINER-MENUS] Durable crafter {@code disabled_slots} masks. The owner tick keeps the
 * resident masks and hands this service the latest mask of every changed cell through the
 * shared serial persistence writer, so the writes of one cell land in the order they were made.
 */
@Service
public class CrafterPersistenceService {

    /** One crafter cell's disabled-slot mask; mask 0 removes the row. */
    @lombok.Value
    @lombok.experimental.Accessors(fluent = true)
    public static class CrafterState {
        int x;
        int y;
        int z;
        int disabledSlots;

        public CrafterState(int x, int y, int z, int disabledSlots) {
            if ((disabledSlots & ~WorldCrafter.ALL_SLOTS) != 0) {
                throw new IllegalArgumentException("crafter mask out of range: " + disabledSlots);
            }

            this.x = x;
            this.y = y;
            this.z = z;
            this.disabledSlots = disabledSlots;
        }

    }

    private final WorldCrafterRepository crafters;

    public CrafterPersistenceService(WorldCrafterRepository crafters) {
        this.crafters = crafters;
    }

    @Transactional(readOnly = true)
    public List<CrafterState> loadWorld(Long worldId) {
        return crafters.findAllByWorldId(worldId).stream()
                .map(row -> new CrafterState(row.getPosX(), row.getPosY(), row.getPosZ(),
                        row.getDisabledSlots()))
                .toList();
    }

    /** Upserts (or, for mask 0, deletes) every given cell in one transaction. */
    @Transactional
    public void replaceAll(Long worldId, List<CrafterState> states) {
        if (worldId == null) throw new IllegalArgumentException("world id is required");
        for (CrafterState state : states) {
            var existing = crafters.findByWorldIdAndPosXAndPosYAndPosZ(
                    worldId, state.x(), state.y(), state.z());
            if (state.disabledSlots() == 0) {
                existing.ifPresent(crafters::delete);
                continue;
            }
            if (existing.isPresent()) {
                existing.get().replaceDisabledSlots(state.disabledSlots());
            } else {
                crafters.save(new WorldCrafter(
                        worldId, state.x(), state.y(), state.z(), state.disabledSlots()));
            }
        }
        crafters.flush();
    }

    @Transactional
    public void deleteWorld(Long worldId) {
        crafters.deleteAllByWorldId(worldId);
    }
}
