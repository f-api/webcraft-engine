package com.gameexpert.lectern.service;

import com.gameexpert.block.entity.WorldBlockDiff;
import com.gameexpert.block.repository.WorldBlockDiffRepository;
import com.gameexpert.ground.service.GroundMutationOutcome;
import com.gameexpert.ground.service.GroundMutationSettlementService;
import com.gameexpert.lectern.dto.LecternBlockData;
import com.gameexpert.lectern.dto.LecternMiningSettlementCommand;
import com.gameexpert.terrain.Blocks;
import com.gameexpert.api.persistence.WorldStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Atomically removes a lectern block entity and creates its exact durable ground drops. */
@Service
public class LecternMiningSettlementService {
    public enum Outcome { COMMITTED, IDEMPOTENT, STALE }

    private final GroundMutationSettlementService ground;
    private final LecternPersistenceService lecterns;
    private final WorldBlockDiffRepository blockDiffs;
    private final WorldStore worlds;

    public LecternMiningSettlementService(GroundMutationSettlementService ground,
            LecternPersistenceService lecterns, WorldBlockDiffRepository blockDiffs,
            WorldStore worlds) {
        this.ground = ground;
        this.lecterns = lecterns;
        this.blockDiffs = blockDiffs;
        this.worlds = worlds;
    }

    @Transactional
    public Outcome settle(LecternMiningSettlementCommand command) {
        GroundMutationOutcome groundOutcome = ground.settle(command.groundMutation());
        if (groundOutcome == GroundMutationOutcome.STALE) return Outcome.STALE;
        if (groundOutcome == GroundMutationOutcome.IDEMPOTENT) {
            LecternBlockData expected = command.expectedLectern();
            boolean sourceGone = lecterns.load(command.groundMutation().worldId(),
                    expected.x(), expected.y(), expected.z()).isEmpty();
            boolean blockGone = blockDiffs.findByWorldIdAndXAndYAndZ(
                    command.groundMutation().worldId(), expected.x(), expected.y(), expected.z())
                    .filter(diff -> diff.getBlockType() == (short) Blocks.AIR
                            && diff.getBlockState() == 0)
                    .isPresent();
            if (!sourceGone || !blockGone) {
                throw new IllegalStateException(
                        "ground mutation is not a completed lectern mining settlement");
            }
            return Outcome.IDEMPOTENT;
        }

        Long worldId = command.groundMutation().worldId();
        LecternBlockData expected = command.expectedLectern();
        lecterns.removeExactJoiningTransaction(worldId, expected);

        var world = worlds.getReferenceById(worldId);
        WorldBlockDiff diff = blockDiffs.findByWorldIdAndXAndYAndZ(
                worldId, expected.x(), expected.y(), expected.z())
                .orElseGet(() -> new WorldBlockDiff(world,
                        expected.x(), expected.y(), expected.z(), (short) Blocks.AIR, (short) 0));
        diff.replace((short) Blocks.AIR, (short) 0, null);
        blockDiffs.saveAndFlush(diff);
        return Outcome.COMMITTED;
    }
}
