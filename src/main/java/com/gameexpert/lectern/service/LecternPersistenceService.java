package com.gameexpert.lectern.service;

import com.gameexpert.engine.inventory.PlayerInventory;
import com.gameexpert.lectern.dto.LecternBlockData;
import com.gameexpert.lectern.entity.WorldLectern;
import com.gameexpert.lectern.repository.WorldLecternRepository;
import com.gameexpert.api.persistence.WorldStore;
import com.gameexpert.state.service.PlayerWorldStateService;
import com.gameexpert.state.service.inventory.PlayerInventoryMutationSnapshot;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Transactional durable store for the exact lectern book stack and selected page. */
@Service
public class LecternPersistenceService {
    private final WorldLecternRepository lecterns;
    private final WorldStore worlds;
    private final PlayerWorldStateService players;

    public LecternPersistenceService(WorldLecternRepository lecterns, WorldStore worlds,
            PlayerWorldStateService players) {
        this.lecterns = lecterns;
        this.worlds = worlds;
        this.players = players;
    }

    public enum PlayerSettlementOutcome { COMMITTED, STALE }

    /** Atomically consumes the player's exact book stack and installs it into the lectern. */
    @Transactional
    public PlayerSettlementOutcome insertWithPlayer(long expectedPlayerRevision,
            PlayerInventoryMutationSnapshot player, int x, int y, int z,
            PlayerInventory.StackSnapshot book) {
        if (player == null || player.revision() != Math.addExact(expectedPlayerRevision, 1)) {
            throw new IllegalArgumentException("exact lectern player settlement is required");
        }
        long persisted = players.lockInventoryPersistenceRevisionJoiningTransaction(
                player.playerId(), player.worldId());
        if (persisted != expectedPlayerRevision) return PlayerSettlementOutcome.STALE;
        insertJoiningTransaction(player.worldId(), x, y, z, book, 0);
        players.replaceExactSnapshotJoiningTransaction(player);
        return PlayerSettlementOutcome.COMMITTED;
    }

    @Transactional(readOnly = true)
    public List<LecternBlockData> loadWorld(Long worldId) {
        return lecterns.findAllByWorldId(worldId).stream().map(WorldLectern::snapshot).toList();
    }

    @Transactional(readOnly = true)
    public List<LecternBlockData> loadChunk(Long worldId, int chunkX, int chunkZ) {
        return lecterns.findAllByWorldIdAndChunkXAndChunkZ(worldId, chunkX, chunkZ).stream()
                .map(WorldLectern::snapshot).toList();
    }

    @Transactional(readOnly = true)
    public Optional<LecternBlockData> load(Long worldId, int x, int y, int z) {
        return lecterns.findByWorldIdAndXAndYAndZ(worldId, x, y, z).map(WorldLectern::snapshot);
    }

    @Transactional
    public LecternBlockData insert(Long worldId, int x, int y, int z,
            PlayerInventory.StackSnapshot book, int page) {
        return insertJoiningTransaction(worldId, x, y, z, book, page);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public LecternBlockData insertJoiningTransaction(Long worldId, int x, int y, int z,
            PlayerInventory.StackSnapshot book, int page) {
        if (lecterns.findByWorldIdAndXAndYAndZ(worldId, x, y, z).isPresent()) {
            throw new IllegalStateException("lectern already contains a book");
        }
        LecternBlockData state = new LecternBlockData(x, y, z, book, page);
        return lecterns.saveAndFlush(new WorldLectern(
                worlds.getReferenceById(worldId), state)).snapshot();
    }

    @Transactional
    public LecternBlockData replace(Long worldId, int x, int y, int z,
            PlayerInventory.StackSnapshot book, int page) {
        return replaceJoiningTransaction(worldId, x, y, z, book, page);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public LecternBlockData replaceJoiningTransaction(Long worldId, int x, int y, int z,
            PlayerInventory.StackSnapshot book, int page) {
        WorldLectern lectern = require(worldId, x, y, z);
        lectern.replace(book, page);
        return lecterns.saveAndFlush(lectern).snapshot();
    }

    @Transactional
    public LecternBlockData turnPage(Long worldId, int x, int y, int z, int page) {
        return turnPageJoiningTransaction(worldId, x, y, z, page);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public LecternBlockData turnPageJoiningTransaction(
            Long worldId, int x, int y, int z, int page) {
        WorldLectern lectern = require(worldId, x, y, z);
        lectern.turnPage(page);
        return lecterns.saveAndFlush(lectern).snapshot();
    }

    @Transactional
    public Optional<LecternBlockData> remove(Long worldId, int x, int y, int z) {
        return removeJoiningTransaction(worldId, x, y, z);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<LecternBlockData> removeJoiningTransaction(
            Long worldId, int x, int y, int z) {
        Optional<WorldLectern> existing = lecterns.findByWorldIdAndXAndYAndZ(worldId, x, y, z);
        if (existing.isEmpty()) return Optional.empty();
        LecternBlockData removed = existing.get().snapshot();
        lecterns.delete(existing.get());
        lecterns.flush();
        return Optional.of(removed);
    }

    /** Locks and removes only the exact book/page aggregate captured by a mining plan. */
    @Transactional(propagation = Propagation.MANDATORY)
    public LecternBlockData removeExactJoiningTransaction(
            Long worldId, LecternBlockData expected) {
        WorldLectern existing = lecterns.findLockedAt(
                worldId, expected.x(), expected.y(), expected.z())
                .orElseThrow(() -> new IllegalStateException("lectern mining source is absent"));
        LecternBlockData current = existing.snapshot();
        if (current.page() != expected.page() || !current.book().equals(expected.book())) {
            throw new IllegalStateException("lectern mining source changed");
        }
        lecterns.delete(existing);
        lecterns.flush();
        return current;
    }

    @Transactional
    public void deleteWorld(Long worldId) {
        lecterns.deleteAllByWorldId(worldId);
    }

    private WorldLectern require(Long worldId, int x, int y, int z) {
        return lecterns.findByWorldIdAndXAndYAndZ(worldId, x, y, z)
                .orElseThrow(() -> new IllegalStateException("lectern has no book"));
    }
}
