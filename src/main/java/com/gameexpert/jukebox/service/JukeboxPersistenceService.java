package com.gameexpert.jukebox.service;

import com.gameexpert.jukebox.dto.JukeboxBlockData;
import com.gameexpert.jukebox.entity.WorldJukebox;
import com.gameexpert.jukebox.repository.WorldJukeboxRepository;
import com.gameexpert.state.service.PlayerWorldStateService;
import com.gameexpert.state.service.inventory.PlayerInventoryMutationSnapshot;
import com.gameexpert.api.persistence.WorldStore;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * [JUKEBOX] Transactional durable store for the jukebox block entity: the exact disc stack and the
 * song clock. The runtime keeps the live clock in memory and checkpoints it here.
 */
@Service
public class JukeboxPersistenceService {
    private final WorldJukeboxRepository jukeboxes;
    private final WorldStore worlds;
    private final PlayerWorldStateService players;

    public JukeboxPersistenceService(WorldJukeboxRepository jukeboxes, WorldStore worlds,
            PlayerWorldStateService players) {
        this.jukeboxes = jukeboxes;
        this.worlds = worlds;
        this.players = players;
    }

    public enum PlayerSettlementOutcome { COMMITTED, STALE }

    /** Atomically consumes the player's disc and installs it (song clock 0) into the jukebox. */
    @Transactional
    public PlayerSettlementOutcome insertWithPlayer(long expectedPlayerRevision,
            PlayerInventoryMutationSnapshot player, JukeboxBlockData state) {
        if (player == null || state == null
                || player.revision() != Math.addExact(expectedPlayerRevision, 1)) {
            throw new IllegalArgumentException("exact jukebox player settlement is required");
        }
        long persisted = players.lockInventoryPersistenceRevisionJoiningTransaction(
                player.playerId(), player.worldId());
        if (persisted != expectedPlayerRevision) return PlayerSettlementOutcome.STALE;
        saveJoiningTransaction(player.worldId(), state);
        players.replaceExactSnapshotJoiningTransaction(player);
        return PlayerSettlementOutcome.COMMITTED;
    }

    @Transactional(readOnly = true)
    public List<JukeboxBlockData> loadWorld(Long worldId) {
        return jukeboxes.findAllByWorldId(worldId).stream().map(WorldJukebox::snapshot).toList();
    }

    @Transactional(readOnly = true)
    public Optional<JukeboxBlockData> load(Long worldId, int x, int y, int z) {
        return jukeboxes.findByWorldIdAndXAndYAndZ(worldId, x, y, z).map(WorldJukebox::snapshot);
    }

    /** Inserts or replaces the row at the state's position. */
    @Transactional
    public JukeboxBlockData save(Long worldId, JukeboxBlockData state) {
        return saveJoiningTransaction(worldId, state);
    }

    /** One transaction for every song-clock checkpoint of a tick. */
    @Transactional
    public void saveAll(Long worldId, Collection<JukeboxBlockData> states) {
        for (JukeboxBlockData state : states) saveJoiningTransaction(worldId, state);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public JukeboxBlockData saveJoiningTransaction(Long worldId, JukeboxBlockData state) {
        Optional<WorldJukebox> existing = jukeboxes.findByWorldIdAndXAndYAndZ(
                worldId, state.x(), state.y(), state.z());
        WorldJukebox row;
        if (existing.isPresent()) {
            row = existing.get();
            row.replace(state);
        } else {
            row = new WorldJukebox(worlds.getReferenceById(worldId), state);
        }
        return jukeboxes.saveAndFlush(row).snapshot();
    }

    @Transactional
    public Optional<JukeboxBlockData> remove(Long worldId, int x, int y, int z) {
        Optional<WorldJukebox> existing = jukeboxes.findByWorldIdAndXAndYAndZ(worldId, x, y, z);
        if (existing.isEmpty()) return Optional.empty();
        JukeboxBlockData removed = existing.get().snapshot();
        jukeboxes.delete(existing.get());
        jukeboxes.flush();
        return Optional.of(removed);
    }

    @Transactional
    public void deleteWorld(Long worldId) {
        jukeboxes.deleteAllByWorldId(worldId);
    }
}
