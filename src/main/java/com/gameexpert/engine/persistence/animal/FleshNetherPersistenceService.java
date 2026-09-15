package com.gameexpert.engine.persistence.animal;

import com.gameexpert.block.repository.WorldBlockDiffRepository;
import com.gameexpert.engine.BlockPos;
import com.gameexpert.engine.FleshNetherRules;
import com.gameexpert.engine.FleshColonyProgress;
import com.gameexpert.engine.FleshCocoonParts;
import com.gameexpert.terrain.Blocks;
import com.gameexpert.mob.dto.MobPersistenceSnapshot;
import com.gameexpert.mob.entity.WorldMob;
import com.gameexpert.mob.repository.WorldMobRepository;
import com.gameexpert.world.repository.WorldDimensionRepository;
import com.gameexpert.api.persistence.WorldStore;
import java.util.*;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;
import com.gameexpert.world.dimension.flesh.FleshColonyLayout;
import com.gameexpert.world.dimension.flesh.FleshColonyTerrain;
import com.gameexpert.world.dimension.flesh.FleshColonyEcology;

/** 기존 공간 diff/동물 receipt/몹 행을 하나의 트랜잭션으로 묶는다. canonical receipt는 만들지 않는다. */
@Service
@RequiredArgsConstructor
public class FleshNetherPersistenceService {
    public static final String SOURCE = "flesh-growth:";
    public static final String HATCH = "FLESH_HATCH";
    public static final String GROWTH = "FLESH_GROWTH";
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private final WorldStore worlds;
    private final WorldDimensionRepository dimensions;
    private final WorldBlockDiffRepository blocks;
    private final WorldMobRepository mobs;
    private final WorldAnimalSettlementRepository receipts;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;

    @Getter @Accessors(fluent = true)
    public static final class Cell {
        private final BlockPos pos;
        private final int before, beforeState, after, afterState;
        public Cell(BlockPos pos, int before, int beforeState, int after, int afterState) {
            this.pos = Objects.requireNonNull(pos);
            if (before < 0 || after < 0 || beforeState < 0 || beforeState > 255
                    || afterState < 0 || afterState > 255) throw new IllegalArgumentException("known flesh source required");
            this.before = before; this.beforeState = beforeState;
            this.after = after; this.afterState = afterState;
        }
        public boolean changes() { return before != after || beforeState != afterState; }
    }

    @Getter @Accessors(fluent = true)
    public static final class Batch {
        private final String key;
        private final long tick;
        private final List<BlockPos> cores;
        private final List<Cell> cells;
        private final MobPersistenceSnapshot mob;
        private final BlockPos hatch;
        public Batch(String key, long tick, List<BlockPos> cores, List<Cell> cells,
                MobPersistenceSnapshot mob, BlockPos hatch) {
            if (key == null || !key.startsWith("flesh:") || key.length() > 160 || tick < 0
                    // A guardian contributes 51 collision witnesses in addition to the bounded growth batch.
                    || cells == null || cells.isEmpty() || cells.size() > 128
                    || cores == null || cores.isEmpty() || cores.size() > 4
                    || (mob == null) != (hatch == null)) throw new IllegalArgumentException("bounded flesh batch required");
            this.key = key; this.tick = tick; this.cores = List.copyOf(cores);
            this.cells = List.copyOf(cells); this.mob = mob; this.hatch = hatch;
            if (new HashSet<>(cells.stream().map(Cell::pos).toList()).size() != cells.size()) {
                throw new IllegalArgumentException("duplicate flesh cell");
            }
            for (Cell cell : cells) {
                BlockPos core = cores.stream().filter(c -> Math.floorDiv(c.x(),256)==Math.floorDiv(cell.pos().x(),256)
                        && Math.floorDiv(c.z(),256)==Math.floorDiv(cell.pos().z(),256)).findFirst().orElse(null);
                if (core == null || cell.pos().y() < 64 || cell.pos().y() > 96) {
                    throw new IllegalArgumentException("cell outside selected flesh cluster");
                }
                if (!cell.changes()) continue;
                boolean flesh = cell.before() == 0 && cell.after() == com.gameexpert.terrain.Blocks.FLESH_BLOCK
                        && cell.pos().y() <= 67 && cell.afterState() == 0;
                boolean cocoon = cell.after() == com.gameexpert.terrain.Blocks.FLESH_COCOON
                        && cell.pos().y() >= 65 && cell.pos().y() <= 96
                        && (cell.before() == 0 && cell.afterState() == 0
                            || cell.before() == com.gameexpert.terrain.Blocks.FLESH_COCOON
                                && cell.beforeState() < 2 && cell.afterState() == cell.beforeState() + 1);
                boolean consumed = cell.pos().equals(hatch) && cell.before() == com.gameexpert.terrain.Blocks.FLESH_COCOON
                        && cell.beforeState() == 2 && cell.after() == 0 && cell.afterState() == 0;
                boolean largeStage=cell.before()==Blocks.FLESH_LARGE_COCOON&&cell.after()==Blocks.FLESH_LARGE_COCOON
                        &&(cell.beforeState()&3)<2&&(cell.beforeState()>>>2)<3&&cell.afterState()==cell.beforeState()+1
                        &&java.util.stream.IntStream.range(0,3).allMatch(segment->cells.stream().anyMatch(c->
                            c.pos().x()==cell.pos().x()&&c.pos().z()==cell.pos().z()
                            &&c.pos().y()==cell.pos().y()-(cell.beforeState()>>>2)+segment
                            &&c.before()==Blocks.FLESH_LARGE_COCOON&&c.beforeState()==((cell.beforeState()&3)|(segment<<2))
                            &&c.after()==Blocks.FLESH_LARGE_COCOON&&c.afterState()==c.beforeState()+1));
                boolean largeConsumed=mob!=null&&"BONE_PROCESSION".equals(mob.getType())&&hatch!=null
                        &&cell.pos().x()==hatch.x()&&cell.pos().z()==hatch.z()
                        &&cell.pos().y()>=hatch.y()&&cell.pos().y()<=hatch.y()+2
                        &&cell.before()==Blocks.FLESH_LARGE_COCOON&&cell.beforeState()==(2|((cell.pos().y()-hatch.y())<<2))
                        &&cell.after()==0&&cell.afterState()==0;
                if (!flesh && !cocoon && !consumed && !largeStage && !largeConsumed) throw new IllegalArgumentException("invalid flesh cell transition");
                if (flesh && cell.pos().y() == 64 && cells.stream().noneMatch(source ->
                        !source.changes() && source.pos().y() == 64
                        && Math.abs(source.pos().x() - cell.pos().x()) + Math.abs(source.pos().z() - cell.pos().z()) == 1
                        && FleshColonyEcology.tissueSupport(source.before()))) {
                    throw new IllegalArgumentException("exact growth attachment required");
                }
            }
            if (hatch != null) {
                boolean support = cells.stream().anyMatch(c -> c.pos().equals(new BlockPos(hatch.x(), hatch.y() - 1, hatch.z()))
                        && !c.changes() && FleshColonyEcology.tissueSupport(c.before()));
                boolean headroom = cells.stream().anyMatch(c -> c.pos().equals(new BlockPos(hatch.x(), hatch.y() + 1, hatch.z()))
                        && !c.changes() && c.before() == 0);
                if(mob!=null&&"BONE_PROCESSION".equals(mob.getType())) {
                    headroom=FleshCocoonParts.guardianClearance(hatch.x(),hatch.y(),hatch.z(),
                        (x,y,z)->cells.stream().filter(c->c.pos().equals(new BlockPos(x,y,z))).mapToInt(Cell::before).findFirst().orElse(-1),
                        (x,y,z)->cells.stream().filter(c->c.pos().equals(new BlockPos(x,y,z))).mapToInt(Cell::beforeState).findFirst().orElse(-1))
                        &&java.util.stream.IntStream.range(0,3).allMatch(segment->cells.stream().anyMatch(c->
                            c.pos().equals(new BlockPos(hatch.x(),hatch.y()+segment,hatch.z()))&&c.after()==0&&c.afterState()==0));
                }
                if (!support || !headroom) throw new IllegalArgumentException("exact hatch support/headroom required");
            }
            if (mob != null && (!FleshNetherRules.isFleshMob(mob.getType()) || mob.getMobId() <= 0
                    || mob.isFinalCarrierBinding() || !mob.isPersistenceRequired()
                    || mob.getX() != hatch.x() + .5 || mob.getY() != hatch.y()
                    || mob.getZ() != hatch.z() + .5)) throw new IllegalArgumentException("exact detached flesh mob required");
        }
        String payload() {
            StringBuilder value = new StringBuilder().append(tick).append('|');
            for (BlockPos core : cores) value.append(core.x()).append(',').append(core.y()).append(',').append(core.z()).append(';');
            for (Cell c : cells) value.append('|').append(c.pos.x()).append(',').append(c.pos.y())
                    .append(',').append(c.pos.z()).append(':').append(c.before).append(':').append(c.beforeState)
                    .append('>').append(c.after).append(':').append(c.afterState);
            return value.append('|').append(mob == null ? "" : JSON.writeValueAsString(mob)).toString();
        }
    }

    /** Invoked on the existing persistence writer. False means the exact source was already replaced. */
    @Transactional
    public boolean commit(long worldId, int seed, Batch batch) {
        return commitEvent(worldId, seed, List.of(batch), null, -1);
    }

    /** One alarm owns up to two ordinary hatch receipts, preserving native retention and ACK. */
    @Transactional
    public boolean commitAlarm(long worldId, int seed, BlockPos anchor, long expectedRevision,
            List<Batch> batches) {
        if (anchor == null || expectedRevision < 1 || batches == null || batches.size() > 2
                || batches.stream().anyMatch(batch -> batch.mob() == null)) {
            throw new IllegalArgumentException("bounded anchor alarm required");
        }
        return commitEvent(worldId, seed, List.copyOf(batches), anchor, expectedRevision);
    }

    private boolean commitEvent(long worldId, int seed, List<Batch> batches,
            BlockPos anchor, long expectedRevision) {
        var world = worlds.findByIdForUpdate(worldId).orElseThrow();
        var mapping = dimensions.findByChildId(worldId).orElseThrow();
        if (!"flesh_nether".equals(mapping.getDimensionKey()) || world.getSeed() != seed) {
            throw new IllegalArgumentException("bound flesh child required");
        }
        var layout = anchor == null ? null : FleshColonyLayout.forCoordinate(seed, anchor.x(), anchor.z());
        int anchorIndex = anchor == null ? -1 : layout.anchors().indexOf(
                new FleshColonyLayout.Point(anchor.x(), anchor.y(), anchor.z()));
        if (anchor != null && anchorIndex < 0) throw new IllegalArgumentException("exact colony anchor required");
        String eventKey = anchor == null ? batches.getFirst().key()
                : "flesh:alarm:" + layout.cellKey() + ":" + anchorIndex;
        String payload = anchor == null ? batches.getFirst().payload()
                : expectedRevision + "|" + batches.stream().map(batch -> batch.key() + "=" + batch.payload())
                        .collect(java.util.stream.Collectors.joining("\n"));
        var prior = receipts.findByWorldIdAndSettlementKey(worldId, eventKey);
        if (prior.isPresent()) {
            if (!payload.equals(prior.get().getPayload())) throw new IllegalStateException("flesh receipt collision");
            return true; // Never rewrite a consumed source or an already moving/dead mob.
        }
        if (anchor != null) {
            int mask = 0;
            for (int index = 0; index < 3; index++) {
                if (receipts.findByWorldIdAndSettlementKey(worldId,
                        FleshColonySettlementService.key(layout.cellKey(), index)).isPresent()) mask |= 1 << index;
            }
            boolean retired = receipts.findByWorldIdAndSettlementKey(worldId,
                    FleshColonySettlementService.key(layout.cellKey(), -1)).isPresent();
            if ((mask & (1 << anchorIndex)) == 0 || Integer.bitCount(mask) + (retired ? 1 : 0) != expectedRevision
                    || retired && !batches.isEmpty()) return false;
            Set<String> keys = new HashSet<>();
            Set<BlockPos> hatchSites = new HashSet<>();
            Map<BlockPos, Cell> combined = new HashMap<>();
            long tick = batches.isEmpty() ? -1 : batches.getFirst().tick();
            for (Batch batch : batches) {
                if (!keys.add(batch.key()) || !hatchSites.add(batch.hatch()) || batch.key().equals(eventKey) || batch.tick() != tick
                        || !FleshColonyLayout.forCoordinate(seed, batch.hatch().x(), batch.hatch().z()).cellKey()
                                .equals(layout.cellKey())
                        || Math.pow(batch.hatch().x() - anchor.x(), 2)
                            + Math.pow(batch.hatch().y() - anchor.y(), 2)
                            + Math.pow(batch.hatch().z() - anchor.z(), 2) > 24 * 24) {
                    throw new IllegalArgumentException("one local alarm event required");
                }
                for (Cell cell : batch.cells()) {
                    Cell old = combined.putIfAbsent(cell.pos(), cell);
                    if (old != null && (old.before() != cell.before() || old.beforeState() != cell.beforeState()
                            || old.after() != cell.after() || old.afterState() != cell.afterState())) {
                        throw new IllegalArgumentException("conflicting alarm witnesses");
                    }
                }
            }
            if (combined.size() > 128) throw new IllegalArgumentException("bounded alarm witnesses required");
        }
        List<MobPersistenceSnapshot> reservedMobs = new ArrayList<>();
        for (Batch batch : batches) {
            if (anchor != null && receipts.findByWorldIdAndSettlementKey(worldId, batch.key()).isPresent()) {
                throw new IllegalStateException("alarm child receipt without envelope");
            }
            if (!validateBatch(worldId, seed, batch, reservedMobs)) return false;
            if (batch.mob() != null) reservedMobs.add(batch.mob());
        }
        // Every source, population reservation and identity is checked before the first write.
        for (Batch batch : batches) writeBatch(worldId, batch);
        if (anchor != null) {
            WorldAnimalSettlement event = new WorldAnimalSettlement(worldId, eventKey, "FLESH_ALARM", 0,
                    batches.isEmpty() ? 0 : batches.getFirst().tick(), 0, (short) 0,
                    anchor.x(), anchor.y(), anchor.z());
            event.setPayload(payload);
            event.complete();
            receipts.saveAndFlush(event);
        }
        settlementBoundary();
        return true;
    }

    private boolean validateBatch(long worldId, int seed, Batch batch, List<MobPersistenceSnapshot> reservedMobs) {
        for (BlockPos core : batch.cores()) {
            if ((FleshColonyTerrain.cell(seed, core.x(), core.y(), core.z()) & 65535)
                        != com.gameexpert.terrain.Blocks.HEART_CORE) return false;
            // Any edit of the original core, including an identical replacement, is a stop tombstone.
            if (blocks.findLockedAt(worldId, core.x(), core.y(), core.z()).isPresent()) return false;
        }
        for (Cell cell : batch.cells()) {
            BlockPos p = cell.pos();
            var row = blocks.findLockedAt(worldId, p.x(), p.y(), p.z()).orElse(null);
            int initial = row == null ? FleshColonyTerrain.cell(seed, p.x(), p.y(), p.z()) : 0;
            int type = row == null ? initial & 65535 : row.getBlockType();
            int state = row == null ? initial >>> 16 : row.getBlockState();
            var layout = FleshColonyLayout.forCoordinate(seed,p.x(),p.z());
            long offsetX=(long)p.x()-layout.centerX(),offsetZ=(long)p.z()-layout.centerZ();
            if(offsetX*offsetX+offsetZ*offsetZ>(long)layout.radius()*layout.radius()) return false;
            if (type != cell.before() || state != cell.beforeState()) return false;
            if (cell.changes() && row != null
                    && (row.getBlockType() == 0 || row.getMobMutationKey() == null
                        || !row.getMobMutationKey().startsWith(SOURCE))) return false;
        }
        if (batch.mob() != null) {
            var center = FleshColonyLayout.forCoordinate(seed,batch.hatch().x(),batch.hatch().z()).core();
            BlockPos core = new BlockPos(center.x(),center.y(),center.z());
            var population = new FleshNetherRules.Population(core);
            for (var mob : mobs.findAllByWorldId(worldId)) population.include(mob.getType(), mob.getPosX(), mob.getPosZ());
            for (var mob : reservedMobs) population.include(mob.getType(), mob.getX(), mob.getZ());
            var admission = "BONE_PROCESSION".equals(batch.mob().getType())?FleshColonyProgress.admitLargeHatch(population.counts())
                    :FleshColonyProgress.admitOrdinaryHatch(
                    FleshNetherRules.hatchRoll(seed, batch.hatch().x(), batch.hatch().z()), population.counts());
            if (!admission.accepted() || !batch.mob().getType().equals(admission.mobKind().toUpperCase(Locale.ROOT))) return false;
            long global = receipts.maximumToken(worldId, HATCH).orElse(-1L);
            long cluster = receipts.maximumTokenInColumnBox(worldId, HATCH,
                    Math.floorDiv(core.x(),256)*256, Math.floorDiv(core.x(),256)*256+255,
                    Math.floorDiv(core.z(),256)*256, Math.floorDiv(core.z(),256)*256+255).orElse(-1L);
            if (batch.tick() % FleshNetherRules.GLOBAL_HATCH_INTERVAL != 0
                    || global >= 0 && batch.tick() - global < FleshNetherRules.GLOBAL_HATCH_INTERVAL
                    || cluster >= 0 && batch.tick() - cluster < FleshNetherRules.HATCH_COOLDOWN) return false;
            Cell consumed = batch.cells().stream().filter(c -> c.pos().equals(batch.hatch())).findFirst().orElseThrow();
            int expectedCocoon = "BONE_PROCESSION".equals(batch.mob().getType())
                    ? Blocks.FLESH_LARGE_COCOON : Blocks.FLESH_COCOON;
            if (consumed.before() != expectedCocoon
                    || consumed.beforeState() != 2 || consumed.after() != 0 || consumed.afterState() != 0) {
                throw new IllegalArgumentException("mature cocoon AIR consumption required");
            }
            if (reservedMobs.stream().anyMatch(mob -> mob.getMobId() == batch.mob().getMobId())
                    || mobs.findLockedByWorldIdAndMobId(worldId, batch.mob().getMobId()).isPresent()) {
                throw new IllegalStateException("reserved flesh mob identity already occupied");
            }
        }
        return true;
    }

    private void writeBatch(long worldId, Batch batch) {
        if (batch.mob() != null) {
            mobs.save(new WorldMob(worldId, batch.mob()));
            mobs.flush();
        }
        for (Cell cell : batch.cells()) {
            if (!cell.changes()) continue;
            BlockPos p = cell.pos();
            jdbc.update("""
                    INSERT INTO world_block_diffs
                        (world_id,x,y,z,chunk_x,chunk_z,block_type,block_state,mob_mutation_key,updated_at)
                    VALUES (?,?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP)
                    ON DUPLICATE KEY UPDATE block_type=VALUES(block_type),block_state=VALUES(block_state),
                        mob_mutation_key=VALUES(mob_mutation_key),updated_at=CURRENT_TIMESTAMP
                    """, worldId, p.x(), p.y(), p.z(), Math.floorDiv(p.x(), 16), Math.floorDiv(p.z(), 16),
                    cell.after(), cell.afterState(), SOURCE + batch.key());
        }
        blocks.flush();
        BlockPos origin = batch.hatch() == null ? batch.cores().getFirst() : batch.hatch();
        WorldAnimalSettlement receipt = new WorldAnimalSettlement(worldId, batch.key(),
                batch.mob() == null ? GROWTH : HATCH, 0, batch.tick(),
                batch.mob() == null ? 0 : batch.mob().getMobId(), (short) 0,
                origin.x(), origin.y(), origin.z());
        receipt.setPayload(batch.payload());
        if (batch.mob() == null) receipt.complete();
        receipts.saveAndFlush(receipt);
    }

    /** Same world-row lock as commit. Old COMPLETE/delta snapshots must include committed pending hatch rows. */
    @Transactional(propagation = Propagation.MANDATORY)
    public List<MobPersistenceSnapshot> retainPending(long worldId, List<MobPersistenceSnapshot> snapshot) {
        worlds.findByIdForUpdate(worldId).orElseThrow();
        Map<Long, MobPersistenceSnapshot> merged = new TreeMap<>();
        for (var row : snapshot) merged.put(row.getMobId(), row);
        for (var receipt : receipts.findAllByWorldIdAndCompletedFalseOrderByIdAsc(worldId)) {
            if (!HATCH.equals(receipt.getKind())) continue;
            var mob = mobs.findLockedByWorldIdAndMobId(worldId, receipt.getEntityId()).orElseThrow(
                    () -> new IllegalStateException("pending committed hatch lost its mob row"));
            merged.putIfAbsent(receipt.getEntityId(), mob.toSnapshot());
        }
        return List.copyOf(merged.values());
    }

    /** Queue only after owner installation, behind every previously submitted complete snapshot. */
    @Transactional
    public void acknowledge(long worldId, Collection<Long> installedIds) {
        worlds.findByIdForUpdate(worldId).orElseThrow();
        for (var receipt : receipts.findAllByWorldIdAndCompletedFalseOrderByIdAsc(worldId)) {
            if (HATCH.equals(receipt.getKind()) && installedIds.contains(receipt.getEntityId())) receipt.complete();
        }
    }

    @Transactional(readOnly = true)
    public List<WorldAnimalSettlement> hatchHistory(long worldId) {
        return receipts.findAllByWorldIdAndKindOrderByIdAsc(worldId, HATCH);
    }

    @Transactional(readOnly = true)
    public long lastGrowthTick(long worldId) {
        return receipts.maximumToken(worldId, GROWTH).orElse(-1L);
    }

    protected void settlementBoundary() { }
}
