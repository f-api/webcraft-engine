package com.gameexpert.engine.qa;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gameexpert.block.entity.WorldBlockDiff;
import com.gameexpert.block.repository.WorldBlockDiffRepository;
import com.gameexpert.ground.dto.GroundItemSnapshot;
import com.gameexpert.ground.dto.GroundXpOrbSnapshot;
import com.gameexpert.ground.service.GroundEntityPersistenceService;
import com.gameexpert.ground.service.GroundMutationOutcome;
import com.gameexpert.mob.dto.MobPersistenceSnapshot;
import com.gameexpert.mob.service.MobPersistenceService;
import com.gameexpert.state.service.PlayerWorldStateService;
import com.gameexpert.state.service.inventory.PlayerInventoryMutationSnapshot;
import com.gameexpert.api.persistence.WorldAccess;
import com.gameexpert.api.persistence.WorldStore;
import com.gameexpert.world.service.WorldTimePersistenceService;

/** One real database transaction for every durable aggregate created by content-v1. */
@Service
public class ContentQaFixtureSettlementService {
    public enum Outcome { COMMITTED, IDEMPOTENT, STALE }

    public record Command(Long worldId, ContentQaFixturePlan plan,
            long expectedPlayerRevision, PlayerInventoryMutationSnapshot player,
            List<MobPersistenceSnapshot> mobs,
            long expectedGroundRevision, long committedGroundRevision,
            List<GroundItemSnapshot> groundItems, List<GroundXpOrbSnapshot> xpOrbs,
            WorldTimePersistenceService.ClockState clock) {
        public Command {
            if (worldId == null || plan == null || player == null || mobs == null
                    || groundItems == null || xpOrbs == null || clock == null
                    || expectedPlayerRevision < 0
                    || committedGroundRevision != expectedGroundRevision + 1) {
                throw new IllegalArgumentException("complete QA fixture settlement is required");
            }
            mobs = List.copyOf(mobs);
            groundItems = List.copyOf(groundItems);
            xpOrbs = List.copyOf(xpOrbs);
        }
    }

    private final WorldQaFixtureReceiptRepository receipts;
    private final WorldStore worlds;
    private final WorldBlockDiffRepository blocks;
    private final PlayerWorldStateService playerStates;
    private final MobPersistenceService mobs;
    private final GroundEntityPersistenceService ground;
    private final WorldTimePersistenceService time;

    public ContentQaFixtureSettlementService(WorldQaFixtureReceiptRepository receipts,
            WorldStore worlds, WorldBlockDiffRepository blocks,
            PlayerWorldStateService playerStates, MobPersistenceService mobs,
            GroundEntityPersistenceService ground, WorldTimePersistenceService time) {
        this.receipts = receipts;
        this.worlds = worlds;
        this.blocks = blocks;
        this.playerStates = playerStates;
        this.mobs = mobs;
        this.ground = ground;
        this.time = time;
    }

    @Transactional(readOnly = true)
    public boolean alreadySettled(Long worldId, ContentQaFixturePlan plan) {
        var prior = receipts.findByWorldIdAndFixtureId(worldId, plan.id());
        if (prior.isEmpty()) return false;
        if (!prior.get().matches(plan)) {
            throw new IllegalStateException("QA fixture identity collision");
        }
        return true;
    }

    @Transactional
    public Outcome settle(Command command) {
        var prior = receipts.findByWorldIdAndFixtureId(
                command.worldId(), command.plan().id());
        if (prior.isPresent()) {
            if (!prior.get().matches(command.plan())) {
                throw new IllegalStateException("QA fixture identity collision");
            }
            return Outcome.IDEMPOTENT;
        }
        long lockedPlayerRevision = playerStates.lockInventoryPersistenceRevisionJoiningTransaction(
                command.player().playerId(), command.worldId());
        if (lockedPlayerRevision != command.expectedPlayerRevision()) return Outcome.STALE;

        // This CAS must happen before any other aggregate is touched. Returning STALE after staging
        // block/player/mob rows would otherwise commit a partial fixture in the outer transaction.
        GroundMutationOutcome groundOutcome = ground.replaceWorld(command.worldId(),
                command.expectedGroundRevision(), command.committedGroundRevision(),
                command.groundItems(), command.xpOrbs());
        if (groundOutcome != GroundMutationOutcome.COMMITTED) return Outcome.STALE;

        WorldAccess world = worlds.getReferenceById(command.worldId());
        // 픽스처가 덮는 좌표에 이미 durable 행이 있을 수 있다(이전 세션의 플레이어 편집이
        // BlockDiffFlusher upsert로 남긴 행, 또는 영수증 없이 남은 이전 픽스처 행).
        // 맹목 insert는 uq_world_xyz 중복으로 정산 전체를 롤백시키므로, 상자 범위를 한 번에
        // 읽어 같은 좌표는 갱신하고 없는 좌표만 새로 만든다.
        ContentQaFixturePlan.Bounds bounds = command.plan().bounds();
        Map<Long, WorldBlockDiff> existing = new HashMap<>();
        for (WorldBlockDiff durable : blocks.findWithinColumnBox(command.worldId(),
                bounds.anchorX(), bounds.anchorX() + bounds.width() - 1,
                bounds.anchorZ(), bounds.anchorZ() + bounds.depth() - 1)) {
            existing.put(cellKey(durable.getX(), durable.getY(), durable.getZ()), durable);
        }
        List<WorldBlockDiff> rows = new ArrayList<>(command.plan().cells().size());
        for (ContentQaFixturePlan.Cell cell : command.plan().cells()) {
            int x = cell.point().x();
            int y = cell.point().y();
            int z = cell.point().z();
            WorldBlockDiff durable = existing.get(cellKey(x, y, z));
            if (durable == null) {
                rows.add(new WorldBlockDiff(world, x, y, z,
                        (short) cell.blockId(), (short) cell.state()));
                continue;
            }
            durable.replace((short) cell.blockId(), (short) cell.state(), null);
            rows.add(durable);
        }
        blocks.saveAll(rows);
        playerStates.replaceExactSnapshotJoiningTransaction(command.player());
        mobs.flushWorld(command.worldId(), command.mobs());
        time.saveClockState(command.worldId(), command.clock());
        receipts.saveAndFlush(new WorldQaFixtureReceipt(command.worldId(), command.plan().id(),
                command.plan().checksum(), command.plan().bounds().anchorX(),
                command.plan().bounds().floorY(), command.plan().bounds().anchorZ()));
        return Outcome.COMMITTED;
    }

    /** 한 월드 안에서 (x, y, z)를 유일하게 식별하는 조회 키. y는 -64..319, x/z는 21비트로 충분하다. */
    private static long cellKey(int x, int y, int z) {
        return ((long) (x & 0x1fffff) << 42) | ((long) (z & 0x1fffff) << 21) | (y & 0x1fffff);
    }
}
