package com.gameexpert.engine.persistence.animal;

import com.gameexpert.block.repository.WorldBlockDiffRepository;
import com.gameexpert.engine.BlockPos;
import com.gameexpert.engine.FleshColonyProgress;
import com.gameexpert.ground.dto.GroundItemSnapshot;
import com.gameexpert.ground.dto.GroundMutationCommand;
import com.gameexpert.ground.service.GroundEntityPersistenceService;
import com.gameexpert.ground.service.GroundMutationOutcome;
import com.gameexpert.ground.service.GroundMutationSettlementService;
import com.gameexpert.terrain.Blocks;
import com.gameexpert.world.dimension.flesh.FleshColonyLayout;
import com.gameexpert.world.repository.WorldDimensionRepository;
import com.gameexpert.api.persistence.WorldStore;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** One world-row lock covers the severed cell, irreversible progress and exact ground reward. */
@Service
@RequiredArgsConstructor
public class FleshColonySettlementService {
    public static final String KIND = "FLESH_COLONY";
    private final WorldStore worlds;
    private final WorldDimensionRepository dimensions;
    private final WorldBlockDiffRepository blocks;
    private final WorldAnimalSettlementRepository receipts;
    private final GroundEntityPersistenceService ground;
    private final JdbcTemplate jdbc;
    private final GroundMutationSettlementService groundMutations;

    public static String key(String cell, int anchor) { return "flesh-colony:" + cell + ":" + anchor; }

    @Transactional(readOnly = true)
    public java.util.Map<String, FleshColonyProgress.State> loadStates(long worldId, int seed) {
        var result = new java.util.HashMap<String, FleshColonyProgress.State>();
        for (var receipt : receipts.findAllByWorldIdAndKindOrderByIdAsc(worldId, KIND)) {
            String cell = FleshColonyLayout.forCoordinate(seed,
                    (int) Math.floor(receipt.getX()), (int) Math.floor(receipt.getZ())).cellKey();
            result.computeIfAbsent(cell, ignored -> state(worldId, cell));
        }
        return java.util.Map.copyOf(result);
    }

    @Transactional(readOnly = true)
    public FleshColonyProgress.State state(long worldId, String cell) {
        int mask = 0;
        for (int index = 0; index < 3; index++) {
            if (receipts.findByWorldIdAndSettlementKey(worldId, key(cell, index)).isPresent()) mask |= 1 << index;
        }
        boolean retired = receipts.findByWorldIdAndSettlementKey(worldId, key(cell, -1)).isPresent();
        return new FleshColonyProgress.State(mask, retired, Integer.bitCount(mask) + (retired ? 1 : 0));
    }

    /** -1 means a stale/locked request; nonnegative is the exact committed ground revision.
     * Retry is allowed only with the same reserved drop identity. A spent receipt never recreates an item.
     */
    @Transactional
    public long settle(long worldId, int seed, BlockPos pos, long expectedRevision, GroundItemSnapshot reward) {
        return settleJoiningTransaction(worldId, seed, pos, expectedRevision, reward, "",
                () -> ground.insertStableItemJoiningTransaction(worldId, reward));
    }

    /** Player tool wear, one drop, organ AIR and colony progress share one durable transaction. */
    @Transactional
    public long settleMining(int seed, BlockPos pos, long expectedRevision, GroundMutationCommand command) {
        if (command == null || command.kind() != GroundMutationCommand.Kind.BLOCK_DROP
                || command.committedPlayer() == null || command.insertedItems().size() != 1
                || !command.removedItemIds().isEmpty() || !command.insertedXpOrbs().isEmpty()
                || !command.removedXpOrbIds().isEmpty()) {
            throw new IllegalArgumentException("colony mining requires one player-owned block drop");
        }
        return settleJoiningTransaction(command.worldId(), seed, pos, expectedRevision,
                command.insertedItems().getFirst(), expectedRevision + ":" + command.fingerprint(), () -> {
                    GroundMutationOutcome outcome = groundMutations.settle(command);
                    if (outcome == GroundMutationOutcome.STALE) return -1L;
                    if (outcome != GroundMutationOutcome.COMMITTED) {
                        throw new IllegalStateException("ground receipt has no matching colony receipt");
                    }
                    return command.committedGroundRevision();
                });
    }

    private long settleJoiningTransaction(long worldId, int seed, BlockPos pos, long expectedRevision,
            GroundItemSnapshot reward, String binding, java.util.function.LongSupplier commitGround) {
        var world = worlds.findByIdForUpdate(worldId).orElseThrow();
        var dimension = dimensions.findByChildId(worldId).orElseThrow();
        if (world.getSeed() != seed || !"flesh_nether".equals(dimension.getDimensionKey())) {
            throw new IllegalArgumentException("bound flesh child required");
        }
        var layout = FleshColonyLayout.forCoordinate(seed, pos.x(), pos.z());
        var point = new FleshColonyLayout.Point(pos.x(), pos.y(), pos.z());
        int anchor = layout.anchors().indexOf(point);
        if (anchor < 0 && !layout.core().equals(point)) return -1;
        String key = key(layout.cellKey(), anchor);
        int item = anchor < 0 ? Blocks.HEART_CORE : Blocks.FLESH_CLOT_SAC;
        int count = anchor < 0 ? 1 : 2;
        if (reward == null || reward.itemType() != item || reward.count() != count
                || reward.x() != pos.x() + .5 || reward.y() != pos.y() + .5 || reward.z() != pos.z() + .5
                || reward.durability() != 0 || reward.enchantments() != 0 || reward.mapId() != 0
                || reward.shulkerId() != 0 || reward.bucketMobData() != null || reward.itemComponentData() != null
                || reward.playerThrown() || reward.age() != 0 || reward.excludedAllayId() != 0) {
            throw new IllegalArgumentException("exact unmodified colony reward required");
        }
        var prior = receipts.findByWorldIdAndSettlementKey(worldId, key).orElse(null);
        if (prior != null) {
            if (prior.getEntityId() != reward.entityId()) return -1;
            String[] receipt = prior.getPayload().split("\\|", -1);
            if (receipt.length != 2 || !receipt[1].equals(binding)) return -1;
            return Long.parseLong(receipt[0]);
        }
        var current = state(worldId, layout.cellKey());
        var transition = anchor < 0 ? FleshColonyProgress.extractCore(current, expectedRevision)
                : FleshColonyProgress.severAnchor(current, anchor, expectedRevision);
        if (!transition.accepted()) return -1;
        // Any player edit (even an identical replacement) cannot impersonate a natural organ.
        if (blocks.findLockedAt(worldId, pos.x(), pos.y(), pos.z()).isPresent()) return -1;
        long groundRevision = commitGround.getAsLong();
        if (groundRevision < 0) return -1;
        jdbc.update("""
                INSERT INTO world_block_diffs
                    (world_id,x,y,z,chunk_x,chunk_z,block_type,block_state,mob_mutation_key,updated_at)
                VALUES (?,?,?,?,?,?,0,0,?,CURRENT_TIMESTAMP)
                """, worldId, pos.x(), pos.y(), pos.z(), Math.floorDiv(pos.x(), 16), Math.floorDiv(pos.z(), 16), key);
        var receipt = new WorldAnimalSettlement(worldId, key, KIND, 0, transition.next().revision(),
                reward.entityId(), (short) item, pos.x(), pos.y(), pos.z());
        receipt.setPayload(groundRevision + "|" + binding);
        receipt.complete();
        receipts.saveAndFlush(receipt);
        settlementBoundary();
        return groundRevision;
    }

    protected void settlementBoundary() { }
}
