package com.gameexpert.block.service;

import com.gameexpert.banner.entity.WorldBannerSettlement;
import com.gameexpert.banner.repository.WorldBannerSettlementRepository;
import com.gameexpert.block.dto.PlayerBlockSettlementCommand;
import com.gameexpert.block.dto.BlockStateSettlementCommand;
import com.gameexpert.block.entity.WorldBlockDiff;
import com.gameexpert.block.repository.WorldBlockDiffRepository;
import com.gameexpert.state.service.PlayerWorldStateService;
import com.gameexpert.api.persistence.WorldStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Atomic exact-CAS boundary for a player mutation coupled to one ordinary block state. */
@Service
public class PlayerBlockSettlementService {
    public enum Outcome { COMMITTED, IDEMPOTENT, STALE }

    private final PlayerWorldStateService players;
    private final WorldBlockDiffRepository blockDiffs;
    private final WorldStore worlds;
    private final WorldBannerSettlementRepository receipts;

    public PlayerBlockSettlementService(PlayerWorldStateService players,
            WorldBlockDiffRepository blockDiffs, WorldStore worlds,
            WorldBannerSettlementRepository receipts) {
        this.players = players;
        this.blockDiffs = blockDiffs;
        this.worlds = worlds;
        this.receipts = receipts;
    }

    @Transactional
    public Outcome settle(PlayerBlockSettlementCommand command) {
        Long worldId = command.player().worldId();
        // Lock before the first consistent read. MySQL REPEATABLE READ must not pin the receipt
        // query's old view before a concurrent checkpoint commits the lazy inventory collection.
        long persisted = players.lockInventoryPersistenceRevisionJoiningTransaction(
                command.player().playerId(), worldId);
        var receipt = receipts.findByWorldIdAndSettlementId(worldId, command.settlementId());
        if (receipt.isPresent()) {
            if (!receipt.get().matches(command.fingerprint())) {
                throw new IllegalStateException("player block settlement identity collision");
            }
            return Outcome.IDEMPOTENT;
        }
        // A live runtime mutation such as hotbar selection may advance the in-memory revision
        // before the asynchronous checkpoint reaches MySQL. An older durable baseline can still
        // join this atomic block settlement; only a newer durable branch makes the command stale.
        if (persisted > command.expectedPlayerRevision()) return Outcome.STALE;
        var world = worlds.getReferenceById(worldId);
        WorldBlockDiff diff = blockDiffs.findByWorldIdAndXAndYAndZ(
                worldId, command.x(), command.y(), command.z())
                .orElseGet(() -> new WorldBlockDiff(world, command.x(), command.y(), command.z(),
                        command.blockType(), command.blockState()));
        diff.replace(command.blockType(), command.blockState(), null);
        blockDiffs.save(diff);
        players.replaceExactSnapshotJoiningTransaction(command.player());
        receipts.saveAndFlush(new WorldBannerSettlement(
                worldId, command.settlementId(), command.fingerprint()));
        return Outcome.COMMITTED;
    }

    @Transactional
    public Outcome settleBlockState(BlockStateSettlementCommand command) {
        var receipt = receipts.findByWorldIdAndSettlementId(
                command.worldId(), command.settlementId());
        if (receipt.isPresent()) {
            if (!receipt.get().matches(command.fingerprint())) {
                throw new IllegalStateException("block settlement identity collision");
            }
            return Outcome.IDEMPOTENT;
        }
        var world = worlds.getReferenceById(command.worldId());
        WorldBlockDiff diff = blockDiffs.findByWorldIdAndXAndYAndZ(
                command.worldId(), command.x(), command.y(), command.z())
                .orElseGet(() -> new WorldBlockDiff(world, command.x(), command.y(), command.z(),
                        command.blockType(), command.blockState()));
        diff.replace(command.blockType(), command.blockState(), null);
        blockDiffs.save(diff);
        receipts.saveAndFlush(new WorldBannerSettlement(
                command.worldId(), command.settlementId(), command.fingerprint()));
        return Outcome.COMMITTED;
    }
}
