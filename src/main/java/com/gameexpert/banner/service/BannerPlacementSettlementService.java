package com.gameexpert.banner.service;

import com.gameexpert.banner.dto.BannerPlacementSettlementCommand;
import com.gameexpert.banner.dto.BannerMiningSettlementCommand;
import com.gameexpert.banner.entity.WorldBannerSettlement;
import com.gameexpert.banner.repository.WorldBannerSettlementRepository;
import com.gameexpert.state.service.PlayerWorldStateService;
import com.gameexpert.ground.service.GroundMutationOutcome;
import com.gameexpert.ground.service.GroundMutationSettlementService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Atomically commits source-stack consumption, the block diff, banner payload, and retry receipt. */
@Service
public class BannerPlacementSettlementService {
    public enum Outcome { COMMITTED, IDEMPOTENT, STALE }

    private final PlayerWorldStateService players;
    private final BannerBlockPersistenceService banners;
    private final WorldBannerSettlementRepository settlements;
    private final GroundMutationSettlementService ground;

    public BannerPlacementSettlementService(PlayerWorldStateService players,
            BannerBlockPersistenceService banners,
            WorldBannerSettlementRepository settlements, GroundMutationSettlementService ground) {
        this.players = players;
        this.banners = banners;
        this.settlements = settlements;
        this.ground = ground;
    }

    @Transactional
    public Outcome settle(BannerPlacementSettlementCommand command) {
        Long worldId = command.player().worldId();
        var existing = settlements.findByWorldIdAndSettlementId(worldId, command.settlementId());
        if (existing.isPresent()) return idempotentOrCollision(existing.get(), command.fingerprint());
        long persistedRevision = players.lockInventoryPersistenceRevisionJoiningTransaction(
                command.player().playerId(), worldId);
        if (persistedRevision != command.expectedPlayerRevision()) return Outcome.STALE;
        banners.placeJoiningTransaction(worldId, command.x(), command.y(), command.z(),
                command.blockType(), command.blockState(), command.patterns());
        players.replaceExactSnapshotJoiningTransaction(command.player());
        settlements.saveAndFlush(new WorldBannerSettlement(
                worldId, command.settlementId(), command.fingerprint()));
        return Outcome.COMMITTED;
    }

    /** The same durable receipt authority binds mining, including exact source state and one drop. */
    @Transactional
    public Outcome settleMining(BannerMiningSettlementCommand command) {
        Long worldId = command.groundMutation().worldId();
        var existing = settlements.findByWorldIdAndSettlementId(worldId, command.settlementId());
        if (existing.isPresent()) return idempotentOrCollision(existing.get(), command.fingerprint());
        GroundMutationOutcome outcome = ground.settle(command.groundMutation());
        if (outcome == GroundMutationOutcome.STALE) return Outcome.STALE;
        if (outcome == GroundMutationOutcome.IDEMPOTENT) {
            throw new IllegalStateException("ground mutation is not a completed banner mining settlement");
        }
        banners.removeExactJoiningTransaction(worldId, command.expectedBanner(),
                command.blockType(), command.blockState());
        settlements.saveAndFlush(new WorldBannerSettlement(
                worldId, command.settlementId(), command.fingerprint()));
        return Outcome.COMMITTED;
    }

    private static Outcome idempotentOrCollision(
            WorldBannerSettlement existing, String fingerprint) {
        if (!existing.matches(fingerprint)) {
            throw new IllegalStateException("banner placement settlement identity collision");
        }
        return Outcome.IDEMPOTENT;
    }
}
