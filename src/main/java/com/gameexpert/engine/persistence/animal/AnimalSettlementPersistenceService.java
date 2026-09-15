package com.gameexpert.engine.persistence.animal;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;
import com.gameexpert.ground.dto.GroundItemSnapshot;
import com.gameexpert.ground.entity.WorldGroundItem;
import com.gameexpert.ground.repository.WorldGroundItemRepository;
import com.gameexpert.mob.dto.MobPersistenceSnapshot;
import com.gameexpert.mob.entity.WorldMob;
import com.gameexpert.mob.repository.WorldMobRepository;
import lombok.RequiredArgsConstructor;
import tools.jackson.databind.json.JsonMapper;

/** Atomic cross-aggregate settlement lane for animal-created mobs/items. */
@Service
@RequiredArgsConstructor
public class AnimalSettlementPersistenceService {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private final WorldAnimalSettlementRepository settlements;
    private final WorldMobRepository mobs;
    private final WorldGroundItemRepository groundItems;
    private final WorldAnimalBlockTickRepository scheduledTicks;
    private final WorldCopperGolemStatueRepository copperStatues;
    private final JdbcTemplate jdbc;

    public record SnifferIntent(String key, long mobId, long token, long entityId,
            short itemType, double x, double y, double z, boolean completed) { }
    public record HatchIntent(String key, long mobId, String spawnKind,
            int x, int y, int z, boolean completed) { }
    public record MobDeathIntent(String key, long mobId,
            List<GroundItemSnapshot> items, boolean completed) {
        public MobDeathIntent {
            items = List.copyOf(items);
        }
    }

    /** Durable write-ahead payload for the complete ordered item output of one mob death. */
    @Transactional
    public MobDeathIntent beginMobDeath(Long worldId, long mobId,
            List<GroundItemSnapshot> items) {
        if (mobId <= 0 || items == null) {
            throw new IllegalArgumentException("mob death settlement requires an exact drop list");
        }
        List<GroundItemSnapshot> exactItems = validatedMobDeathItems(items);
        String key = "mob-death:" + mobId;
        String payload;
        try {
            payload = JSON.writeValueAsString(exactItems);
        } catch (RuntimeException failure) {
            throw new IllegalArgumentException("mob death payload is not serializable", failure);
        }
        WorldAnimalSettlement row = settlements.findByWorldIdAndSettlementKey(worldId, key)
                .orElseGet(() -> {
                    GroundItemSnapshot first = exactItems.isEmpty() ? null : exactItems.getFirst();
                    WorldAnimalSettlement created = new WorldAnimalSettlement(worldId, key,
                            "MOB_DEATH", mobId, exactItems.size(),
                            first == null ? 0 : first.entityId(),
                            first == null ? (short) 0 : first.itemType(),
                            first == null ? 0 : first.x(), first == null ? 0 : first.y(),
                            first == null ? 0 : first.z());
                    created.setPayload(payload);
                    return settlements.save(created);
                });
        if (!"MOB_DEATH".equals(row.getKind()) || row.getSourceMobId() != mobId
                || !payload.equals(row.getPayload())) {
            throw new IllegalStateException("mob death settlement identity collision");
        }
        settlements.flush();
        return new MobDeathIntent(key, mobId, exactItems, row.isCompleted());
    }

    /** Ground identities, source-mob retirement, and receipt completion are one transaction. */
    @Transactional
    public boolean commitMobDeath(Long worldId, MobDeathIntent intent) {
        WorldAnimalSettlement row = settlements.findByWorldIdAndSettlementKey(worldId,
                intent.key()).orElse(null);
        if (row == null) return false;
        if (row.isCompleted()) return true;
        List<GroundItemSnapshot> items = decodeMobDeath(row);
        if (row.getSourceMobId() != intent.mobId() || !items.equals(intent.items())) return false;
        mobs.findLockedByWorldIdAndMobId(worldId, intent.mobId()).ifPresent(mobs::delete);
        // A published hatch can die before a rejected acknowledgment is retried.
        // Its consumption remains durable; pending-snapshot retention must not recreate that dead mob.
        for (var pending : settlements.findAllByWorldIdAndCompletedFalseOrderByIdAsc(worldId)) {
            if (FleshNetherPersistenceService.HATCH.equals(pending.getKind())
                    && pending.getEntityId() == intent.mobId()) pending.complete();
        }
        for (GroundItemSnapshot item : items) {
            WorldGroundItem ground = groundItems.findByWorldIdAndEntityId(worldId, item.entityId())
                    .orElseGet(() -> new WorldGroundItem(worldId, item));
            ground.apply(item);
            groundItems.save(ground);
        }
        groundItems.flush();
        settlementBoundary("MOB_DEATH");
        row.complete();
        settlements.save(row);
        return true;
    }

    /** Startup recovery closes the begin→commit crash window before the runtime loads mobs/items. */
    @Transactional
    public void recoverPendingMobDeaths(Long worldId) {
        for (WorldAnimalSettlement row
                : settlements.findAllByWorldIdAndCompletedFalseOrderByIdAsc(worldId)) {
            if (!"MOB_DEATH".equals(row.getKind())) continue;
            commitMobDeath(worldId, new MobDeathIntent(row.getSettlementKey(),
                    row.getSourceMobId(), decodeMobDeath(row), false));
        }
    }

    private static List<GroundItemSnapshot> decodeMobDeath(WorldAnimalSettlement row) {
        try {
            GroundItemSnapshot[] decoded = JSON.readValue(
                    row.getPayload(), GroundItemSnapshot[].class);
            return List.of(decoded);
        } catch (RuntimeException failure) {
            throw new IllegalStateException("malformed durable mob death payload", failure);
        }
    }

    private static List<GroundItemSnapshot> validatedMobDeathItems(
            List<GroundItemSnapshot> items) {
        java.util.HashSet<Long> identities = new java.util.HashSet<>(items.size());
        for (GroundItemSnapshot item : items) {
            if (item == null || item.entityId() <= 0 || !identities.add(item.entityId())) {
                throw new IllegalArgumentException("mob death drop identities must be positive and unique");
            }
        }
        return List.copyOf(items);
    }

    public static final class FroglightIntent {
        private final String key;
        private final long frogMobId;
        private final long sulfurCubeMobId;
        private final long entityId;
        private final short itemType;
        private final double x;
        private final double y;
        private final double z;
        private final boolean completed;
        private FroglightIntent(String key, long frogMobId, long sulfurCubeMobId,
                long entityId, short itemType, double x, double y, double z,
                boolean completed) {
            this.key = key; this.frogMobId = frogMobId; this.sulfurCubeMobId = sulfurCubeMobId;
            this.entityId = entityId; this.itemType = itemType;
            this.x = x; this.y = y; this.z = z; this.completed = completed;
        }
        public String key() { return key; }
        public long frogMobId() { return frogMobId; }
        public long sulfurCubeMobId() { return sulfurCubeMobId; }
        public long entityId() { return entityId; }
        public short itemType() { return itemType; }
        public double x() { return x; }
        public double y() { return y; }
        public double z() { return z; }
        public boolean completed() { return completed; }
    }
    public static final class CopperGolemIntent {
        private final String key; private final long mobId;
        private final int x; private final int headY; private final int z;
        private CopperGolemIntent(String key, long mobId, int x, int headY, int z) {
            this.key = key; this.mobId = mobId; this.x = x; this.headY = headY; this.z = z;
        }
        public String key() { return key; }
        public long mobId() { return mobId; }
        public int x() { return x; }
        public int headY() { return headY; }
        public int z() { return z; }
    }
    public static final class CopperStatueIntent {
        private final String key; private final long mobId;
        private final int x; private final int y; private final int z; private final int state;
        private CopperStatueIntent(String key, long mobId, int x, int y, int z, int state) {
            this.key = key; this.mobId = mobId; this.x = x; this.y = y; this.z = z;
            this.state = state;
        }
        public String key() { return key; }
        public long mobId() { return mobId; }
        public int x() { return x; }
        public int y() { return y; }
        public int z() { return z; }
        public int state() { return state; }
    }
    public static final class CopperRevivalIntent {
        private final String key; private final long mobId;
        private final int x; private final int y; private final int z; private final int pose;
        private final String customName; private final short heldItem; private final int heldDurability;
        private CopperRevivalIntent(String key, WorldCopperGolemStatue statue) {
            this.key = key; this.mobId = statue.getMobId(); this.x = statue.getX();
            this.y = statue.getY(); this.z = statue.getZ(); this.pose = statue.getPose();
            this.customName = statue.getCustomName(); this.heldItem = statue.getHeldItem();
            this.heldDurability = statue.getHeldItemDurability();
        }
        public String key() { return key; }
        public long mobId() { return mobId; }
        public int x() { return x; }
        public int y() { return y; }
        public int z() { return z; }
        public int pose() { return pose; }
        public String customName() { return customName; }
        public short heldItem() { return heldItem; }
        public int heldDurability() { return heldDurability; }
    }

    @Transactional
    public HatchIntent beginHatch(Long worldId, String spawnKind, int x, int y, int z,
            long proposedMobId) {
        return beginHatchBatch(worldId, spawnKind, x, y, z, List.of(proposedMobId)).getFirst();
    }

    /** Every member identity of one hatch is durable before the first runtime spawn occurs. */
    @Transactional
    public List<HatchIntent> beginHatchBatch(Long worldId, String spawnKind, int x, int y, int z,
            List<Long> proposedMobIds) {
        if (proposedMobIds == null || proposedMobIds.isEmpty()) {
            throw new IllegalArgumentException("hatch batch requires at least one mob id");
        }
        java.util.ArrayList<HatchIntent> result = new java.util.ArrayList<>(proposedMobIds.size());
        String base = "hatch:" + spawnKind + ":" + x + ":" + y + ":" + z;
        for (int index = 0; index < proposedMobIds.size(); index++) {
            String key = index == 0 ? base : base + ":" + index;
            long proposed = proposedMobIds.get(index);
            WorldAnimalSettlement row = settlements.findByWorldIdAndSettlementKey(worldId, key)
                    .orElseGet(() -> settlements.save(new WorldAnimalSettlement(worldId, key,
                            "HATCH_" + spawnKind, 0, 0, proposed, (short) 0, x, y, z)));
            result.add(hatch(row));
        }
        settlements.flush();
        return List.copyOf(result);
    }

    @Transactional(readOnly = true)
    public List<HatchIntent> pendingHatches(Long worldId) {
        return settlements.findAllByWorldIdAndCompletedFalseOrderByIdAsc(worldId).stream()
                .filter(row -> row.getKind().startsWith("HATCH_"))
                .map(AnimalSettlementPersistenceService::hatch).toList();
    }

    /** Spawned mob, source AIR diff, and schedule retirement are one commit. */
    @Transactional
    public boolean commitHatch(Long worldId, HatchIntent intent, MobPersistenceSnapshot mob) {
        WorldAnimalSettlement row = settlements.findByWorldIdAndSettlementKey(worldId, intent.key())
                .orElse(null);
        if (row == null || row.isCompleted()) return row != null;
        if (row.getEntityId() != mob.getMobId()) return false;
        WorldMob stored = mobs.findLockedByWorldIdAndMobId(worldId, mob.getMobId())
                .orElseGet(() -> new WorldMob(worldId, mob));
        stored.apply(mob);
        mobs.save(stored);
        mobs.flush();
        settlementBoundary("HATCH");
        jdbc.update("""
                INSERT INTO world_block_diffs
                    (world_id,x,y,z,chunk_x,chunk_z,block_type,block_state,mob_mutation_key,updated_at)
                VALUES (?,?,?,?,?,?,0,0,NULL,CURRENT_TIMESTAMP)
                ON DUPLICATE KEY UPDATE block_type=0,block_state=0,mob_mutation_key=NULL,
                    updated_at=CURRENT_TIMESTAMP
                """, worldId, intent.x(), intent.y(), intent.z(),
                Math.floorDiv(intent.x(), 16), Math.floorDiv(intent.z(), 16));
        scheduledTicks.deleteAt(worldId, intent.x(), intent.y(), intent.z());
        row.complete(); settlements.save(row);
        return true;
    }

    @Transactional
    public SnifferIntent beginSniffer(Long worldId, long mobId, long token, long proposedEntityId,
            short itemType, double x, double y, double z) {
        String key = "sniffer:" + mobId + ":" + token;
        WorldAnimalSettlement row = settlements.findByWorldIdAndSettlementKey(worldId, key)
                .orElseGet(() -> settlements.save(new WorldAnimalSettlement(worldId, key,
                        "SNIFFER_DROP", mobId, token, proposedEntityId, itemType, x, y, z)));
        return sniffer(row);
    }

    @Transactional(readOnly = true)
    public List<SnifferIntent> pendingSniffer(Long worldId) {
        return settlements.findAllByWorldIdAndCompletedFalseOrderByIdAsc(worldId).stream()
                .filter(row -> "SNIFFER_DROP".equals(row.getKind()))
                .map(AnimalSettlementPersistenceService::sniffer).toList();
    }

    @Transactional
    public FroglightIntent beginFroglight(Long worldId, long frogMobId,
            long sulfurCubeMobId, long proposedEntityId, short itemType,
            double x, double y, double z) {
        String key = "froglight:" + frogMobId + ':' + sulfurCubeMobId;
        WorldAnimalSettlement row = settlements.findByWorldIdAndSettlementKey(worldId, key)
                .orElseGet(() -> settlements.save(new WorldAnimalSettlement(worldId, key,
                        "FROGLIGHT_DROP", sulfurCubeMobId, frogMobId, proposedEntityId,
                        itemType, x, y, z)));
        return froglight(row);
    }

    @Transactional
    public CopperGolemIntent beginCopperGolem(Long worldId, int x, int headY, int z,
            long proposedMobId) {
        String key = "copper-golem:" + x + ':' + headY + ':' + z;
        WorldAnimalSettlement row = settlements.findByWorldIdAndSettlementKey(worldId, key)
                .orElseGet(() -> settlements.save(new WorldAnimalSettlement(worldId, key,
                        "COPPER_GOLEM", 0, 0, proposedMobId, (short) 0, x, headY, z)));
        return new CopperGolemIntent(row.getSettlementKey(), row.getEntityId(),
                (int) row.getX(), (int) row.getY(), (int) row.getZ());
    }

    @Transactional
    public CopperRevivalIntent beginCopperGolemRevival(Long worldId, int x, int y, int z) {
        WorldCopperGolemStatue statue = copperStatues.findByWorldIdAndXAndYAndZ(worldId, x, y, z)
                .orElse(null);
        if (statue == null) return null;
        String key = "copper-golem-revival:" + x + ':' + y + ':' + z;
        WorldAnimalSettlement row = settlements.findByWorldIdAndSettlementKey(worldId, key)
                .orElseGet(() -> settlements.save(new WorldAnimalSettlement(worldId, key,
                        "COPPER_GOLEM_REVIVAL", 0, 0, statue.getMobId(),
                        (short) 0, x, y, z)));
        if (row.getEntityId() != statue.getMobId()) {
            throw new IllegalStateException("Copper Golem revival identity collision");
        }
        return new CopperRevivalIntent(row.getSettlementKey(), statue);
    }

    @Transactional
    public boolean commitCopperGolem(Long worldId, CopperGolemIntent intent,
            MobPersistenceSnapshot mob) {
        WorldAnimalSettlement row = settlements.findByWorldIdAndSettlementKey(worldId,
                intent.key()).orElse(null);
        if (row == null || row.isCompleted()) return row != null;
        if (mob.getMobId() != intent.mobId() || !"COPPER_GOLEM".equals(mob.getType())) return false;
        WorldMob stored = mobs.findLockedByWorldIdAndMobId(worldId, mob.getMobId())
                .orElseGet(() -> new WorldMob(worldId, mob));
        stored.apply(mob); mobs.saveAndFlush(stored);
        settlementBoundary("COPPER_GOLEM");
        writeAirDiff(worldId, intent.x(), intent.headY(), intent.z());
        writeAirDiff(worldId, intent.x(), intent.headY() - 1, intent.z());
        row.complete(); settlements.save(row);
        return true;
    }

    @Transactional
    public boolean commitCopperGolemRevival(Long worldId, CopperRevivalIntent intent,
            MobPersistenceSnapshot mob) {
        WorldAnimalSettlement row = settlements.findByWorldIdAndSettlementKey(worldId,
                intent.key()).orElse(null);
        if (row == null || row.isCompleted()) return row != null;
        if (!"COPPER_GOLEM_REVIVAL".equals(row.getKind())
                || mob.getMobId() != intent.mobId()
                || !"COPPER_GOLEM".equals(mob.getType())) return false;
        WorldMob stored = mobs.findLockedByWorldIdAndMobId(worldId, mob.getMobId())
                .orElseGet(() -> new WorldMob(worldId, mob));
        stored.apply(mob); mobs.saveAndFlush(stored);
        settlementBoundary("COPPER_GOLEM_REVIVAL");
        writeAirDiff(worldId, intent.x(), intent.y(), intent.z());
        copperStatues.findByWorldIdAndXAndYAndZ(worldId, intent.x(), intent.y(), intent.z())
                .filter(statue -> statue.getMobId() == intent.mobId())
                .ifPresent(copperStatues::delete);
        row.complete(); settlements.save(row);
        return true;
    }

    @Transactional
    public CopperStatueIntent beginCopperGolemStatue(Long worldId, long mobId,
            int x, int y, int z, int state) {
        String key = "copper-golem-statue:" + mobId;
        WorldAnimalSettlement row = settlements.findByWorldIdAndSettlementKey(worldId, key)
                .orElseGet(() -> settlements.save(new WorldAnimalSettlement(worldId, key,
                        "COPPER_GOLEM_STATUE", mobId, state & 0xff, mobId,
                        (short) 0, x, y, z)));
        if (row.getSourceMobId() != mobId || row.getToken() != (state & 0xff)
                || (int) row.getX() != x || (int) row.getY() != y || (int) row.getZ() != z) {
            throw new IllegalStateException("Copper Golem statue settlement identity collision");
        }
        return new CopperStatueIntent(row.getSettlementKey(), mobId, x, y, z, state & 0xff);
    }

    /** Mob removal, retained statue identity, and exact block/state are one transaction. */
    @Transactional
    public boolean commitCopperGolemStatue(Long worldId, CopperStatueIntent intent,
            MobPersistenceSnapshot mob) {
        WorldAnimalSettlement row = settlements.findByWorldIdAndSettlementKey(worldId,
                intent.key()).orElse(null);
        if (row == null || row.isCompleted()) return row != null;
        if (mob.getMobId() != intent.mobId() || !"COPPER_GOLEM".equals(mob.getType())
                || !mob.isCopperGolemStatuePending()) return false;
        WorldCopperGolemStatue statue = copperStatues.findByWorldIdAndXAndYAndZ(
                worldId, intent.x(), intent.y(), intent.z())
                .orElseGet(() -> new WorldCopperGolemStatue(
                        worldId, intent.x(), intent.y(), intent.z(), mob));
        if (statue.getMobId() != mob.getMobId()) return false;
        copperStatues.save(statue);
        mobs.findLockedByWorldIdAndMobId(worldId, mob.getMobId()).ifPresent(mobs::delete);
        mobs.flush();
        settlementBoundary("COPPER_GOLEM_STATUE");
        writeBlockDiff(worldId, intent.x(), intent.y(), intent.z(),
                com.gameexpert.terrain.Blocks.OXIDIZED_COPPER_GOLEM_STATUE, intent.state());
        row.complete(); settlements.save(row);
        return true;
    }

    private void writeAirDiff(Long worldId, int x, int y, int z) {
        writeBlockDiff(worldId, x, y, z, 0, 0);
    }

    private void writeBlockDiff(Long worldId, int x, int y, int z, int blockId, int state) {
        jdbc.update("""
                INSERT INTO world_block_diffs
                    (world_id,x,y,z,chunk_x,chunk_z,block_type,block_state,mob_mutation_key,updated_at)
                VALUES (?,?,?,?,?,?,?,?,NULL,CURRENT_TIMESTAMP)
                ON DUPLICATE KEY UPDATE block_type=VALUES(block_type),
                    block_state=VALUES(block_state),mob_mutation_key=NULL,
                    updated_at=CURRENT_TIMESTAMP
                """, worldId, x, y, z, Math.floorDiv(x, 16), Math.floorDiv(z, 16),
                blockId, state & 0xff);
    }

    @Transactional(readOnly = true)
    public List<FroglightIntent> pendingFroglights(Long worldId) {
        return settlements.findAllByWorldIdAndCompletedFalseOrderByIdAsc(worldId).stream()
                .filter(row -> "FROGLIGHT_DROP".equals(row.getKind()))
                .map(AnimalSettlementPersistenceService::froglight).toList();
    }

    @Transactional
    public boolean commitFroglight(Long worldId, FroglightIntent intent,
            GroundItemSnapshot item) {
        WorldAnimalSettlement row = settlements.findByWorldIdAndSettlementKey(worldId,
                intent.key()).orElse(null);
        if (row == null || row.isCompleted()) return row != null;
        if (row.getSourceMobId() != intent.sulfurCubeMobId()
                || row.getToken() != intent.frogMobId()
                || row.getEntityId() != item.entityId()
                || row.getItemType() != item.itemType()) return false;
        mobs.findLockedByWorldIdAndMobId(worldId, intent.sulfurCubeMobId())
                .ifPresent(mobs::delete);
        mobs.flush();
        settlementBoundary("FROGLIGHT_DROP");
        WorldGroundItem ground = groundItems.findByWorldIdAndEntityId(worldId, item.entityId())
                .orElseGet(() -> new WorldGroundItem(worldId, item));
        ground.apply(item);
        groundItems.save(ground);
        row.complete(); settlements.save(row);
        return true;
    }

    /** Ground entity and cleared mob token commit in the same database transaction. */
    @Transactional
    public boolean commitSniffer(Long worldId, SnifferIntent intent,
            MobPersistenceSnapshot clearedMob, GroundItemSnapshot item) {
        WorldAnimalSettlement row = settlements.findByWorldIdAndSettlementKey(worldId, intent.key())
                .orElse(null);
        if (row == null || row.isCompleted()) return row != null;
        if (row.getSourceMobId() != clearedMob.getMobId() || row.getEntityId() != item.entityId()
                || row.getToken() != intent.token() || row.getItemType() != item.itemType()) return false;
        WorldMob mob = mobs.findLockedByWorldIdAndMobId(worldId, clearedMob.getMobId())
                .orElseGet(() -> new WorldMob(worldId, clearedMob));
        mob.apply(clearedMob);
        mobs.save(mob);
        mobs.flush();
        settlementBoundary("SNIFFER_DROP");
        WorldGroundItem ground = groundItems.findByWorldIdAndEntityId(worldId, item.entityId())
                .orElseGet(() -> new WorldGroundItem(worldId, item));
        ground.apply(item);
        groundItems.save(ground);
        row.complete();
        settlements.save(row);
        return true;
    }

    private static SnifferIntent sniffer(WorldAnimalSettlement row) {
        return new SnifferIntent(row.getSettlementKey(), row.getSourceMobId(), row.getToken(),
                row.getEntityId(), row.getItemType(), row.getX(), row.getY(), row.getZ(),
                row.isCompleted());
    }

    private static FroglightIntent froglight(WorldAnimalSettlement row) {
        return new FroglightIntent(row.getSettlementKey(), row.getToken(),
                row.getSourceMobId(), row.getEntityId(), row.getItemType(),
                row.getX(), row.getY(), row.getZ(), row.isCompleted());
    }

    private static HatchIntent hatch(WorldAnimalSettlement row) {
        return new HatchIntent(row.getSettlementKey(), row.getEntityId(),
                row.getKind().substring("HATCH_".length()), (int) row.getX(),
                (int) row.getY(), (int) row.getZ(), row.isCompleted());
    }

    void settlementBoundary(String kind) { }

    @Transactional
    public void retireCompleted(Long worldId, String key) {
        settlements.findByWorldIdAndSettlementKey(worldId, key)
                .filter(WorldAnimalSettlement::isCompleted).ifPresent(settlements::delete);
    }

    @Transactional
    public void deleteWorld(Long worldId) {
        copperStatues.deleteAllByWorldId(worldId);
        settlements.deleteAllByWorldId(worldId);
    }
}
