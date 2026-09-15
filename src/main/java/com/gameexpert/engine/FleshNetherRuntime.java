package com.gameexpert.engine;

import com.gameexpert.engine.persistence.animal.FleshNetherPersistenceService;
import com.gameexpert.engine.persistence.animal.FleshNetherPersistenceService.Batch;
import com.gameexpert.engine.persistence.animal.FleshNetherPersistenceService.Cell;
import com.gameexpert.mob.dto.MobPersistenceSnapshot;
import com.gameexpert.mob.service.MobPersistenceService;
import com.gameexpert.terrain.Blocks;
import java.util.*;
import com.gameexpert.world.dimension.flesh.FleshColonyLayout;
import com.gameexpert.world.dimension.flesh.FleshColonyEcology;

/** 상주 코어4개/한 번의 writer 작업만 소유한다. 지형 생성·DB 작업은 게임 틱에서 실행하지 않는다. */
final class FleshNetherRuntime {
    private final WorldRuntime rt;
    private final FleshNetherPersistenceService persistence;
    private final Map<BlockPos, Long> lastHatch = new HashMap<>();
    private final Set<Long> pendingAcks = new HashSet<>();
    private long lastGlobal = -1, lastCocoonStep = -1, nextAttempt;
    private int coreCursor, cocoonCursor;
    private Batch pending;
    private Set<BlockPos> reserved = Set.of();
    private boolean inFlight, acknowledging, installing;
    private final Map<BlockPos,Integer> scanPages = new HashMap<>();
    private final Map<BlockPos, Alarm> alarms = new LinkedHashMap<>();
    private List<Batch> alarmBatches;
    private BlockPos pendingAlarmAnchor;
    private long pendingAlarmRevision;
    private long lastDetectorTick = Long.MIN_VALUE;

    private static final class Alarm {
        final BlockPos anchor, core;
        final long tick;
        final List<BlockPos> sites;
        Alarm(BlockPos anchor, BlockPos core, long tick, List<BlockPos> sites) {
            this.anchor=anchor; this.core=core; this.tick=tick; this.sites=sites;
        }
    }

    List<BlockPos> onColonySettlement(BlockPos anchor) {
        var layout=FleshColonyLayout.forCoordinate(rt.seed(),anchor.x(),anchor.z());
        BlockPos core=colonyCore(anchor.x(),anchor.z());
        if(rt.fleshColonyState(anchor).coreDestroyed()) {
            alarms.values().removeIf(alarm->alarm.core.equals(core));
            return List.of();
        }
        if(!layout.anchors().contains(new FleshColonyLayout.Point(anchor.x(),anchor.y(),anchor.z()))
                || alarms.containsKey(anchor))return List.of();
        List<BlockPos> sites=new ArrayList<>();
        for(int y=Math.max(65,anchor.y()-24);y<=Math.min(96,anchor.y()+24);y++) {
            for(int z=anchor.z()-24;z<=anchor.z()+24;z++)for(int x=anchor.x()-24;x<=anchor.x()+24;x++) {
                BlockPos pos=new BlockPos(x,y,z);
                if(distanceSquared(anchor,pos)>24*24)continue;
                int type=block(x,y,z);
                if((type==Blocks.FLESH_COCOON||type==Blocks.FLESH_LARGE_COCOON)
                        &&state(pos)==2&&!edited(pos)&&colonyCore(x,z).equals(core))sites.add(pos);
            }
        }
        sites.sort(Comparator.comparingInt((BlockPos p)->distanceSquared(anchor,p))
                .thenComparingInt(BlockPos::x).thenComparingInt(BlockPos::y).thenComparingInt(BlockPos::z));
        List<BlockPos> selected=List.copyOf(sites.subList(0,Math.min(2,sites.size())));
        alarms.put(anchor,new Alarm(anchor,core,rt.clock().gameTimeMcTicks()/2,selected));
        return selected;
    }

    private static int distanceSquared(BlockPos a, BlockPos b) {
        int dx=a.x()-b.x(),dy=a.y()-b.y(),dz=a.z()-b.z();
        return dx*dx+dy*dy+dz*dz;
    }

    private BlockPos colonyCore(int x, int z) {
        var core = FleshColonyLayout.forCoordinate(rt.seed(), x, z).core();
        return new BlockPos(core.x(), core.y(), core.z());
    }

    FleshNetherRuntime(WorldRuntime rt, FleshNetherPersistenceService persistence,
            MobPersistenceService mobPersistence) {
        this.rt = rt; this.persistence = persistence;
        mobPersistence.registerSnapshotRetention(rt.worldId(),
                snapshot -> persistence.retainPending(rt.worldId(), snapshot));
        long highWater = 0, latest = persistence.lastGrowthTick(rt.worldId());
        for (var receipt : persistence.hatchHistory(rt.worldId())) {
            BlockPos core = colonyCore((int) receipt.getX(), (int) receipt.getZ());
            lastHatch.merge(core, receipt.getToken(), Math::max);
            lastGlobal = Math.max(lastGlobal, receipt.getToken());
            highWater = Math.max(highWater, receipt.getEntityId());
            if (!receipt.isCompleted()) {
                // MobSystem has already loaded the exact durable row before this attachment.
                if (rt.mobSystem().persistenceSnapshot(receipt.getEntityId()) == null) {
                    throw new IllegalStateException("pending flesh hatch missing from restored mob owner");
                }
                pendingAcks.add(receipt.getEntityId());
            }
        }
        rt.mobSystem().reserveMobIdThrough(highWater);
        long latestStep = Math.max(latest, lastGlobal);
        lastCocoonStep = latestStep < 0 ? -1 : latestStep / FleshNetherRules.COCOON_INTERVAL;
    }

    boolean reserved(int x, int y, int z) {
        return !installing && pending != null && reserved.contains(new BlockPos(x, y, z));
    }

    boolean pending() { return pending != null || inFlight || !pendingAcks.isEmpty(); }

    void tick() {
        flushAcks();
        long tick = rt.clock().gameTimeMcTicks() / 2;
        if (lastDetectorTick == Long.MIN_VALUE || tick - lastDetectorTick >= 10) {
            lastDetectorTick = tick;
            for (var player : rt.players().values()) {
                var inventory = player.inventory();
                if (player.isDead() || inventory.itemType(inventory.selectedSlot()) != Blocks.FLESH_DETECTOR
                        && inventory.offhand().itemType() != Blocks.FLESH_DETECTOR) continue;
                var core = FleshDetector.nearest(rt.seed(),player.x(),player.y(),player.z(),
                        pos -> rt.fleshColonyState(pos).coreDestroyed());
                var session = rt.session(player.nickname());
                if (session != null) rt.ctx().broadcaster().enqueueSendToFromTick(rt.worldId(),session,
                        new com.gameexpert.ws.dto.WsMessages.FleshDetectorTarget(core != null,
                                core == null ? 0 : core.x(),core == null ? 0 : core.y(),core == null ? 0 : core.z()));
            }
        }
        if (inFlight || acknowledging || tick < nextAttempt || rt.ctx().persistenceExecutor() == null) return;
        if (pending != null) {
            if (!(alarmBatches == null ? stillMatches(pending) : alarmBatches.stream().allMatch(this::stillMatches))) {
                pending = null; alarmBatches = null; reserved = Set.of();
            }
            else { submit(); return; }
        }
        if (tick % 20 != 0) return;
        List<BlockPos> cores = residentCores();
        alarms.values().removeIf(alarm->!cores.contains(alarm.core));
        if (cores.isEmpty()) return;
        if (planAlarm(tick)) { submit(); return; }
        LinkedHashSet<BlockPos> chosen = new LinkedHashSet<>();
        for (int i = 0; i < Math.min(4, cores.size()); i++) chosen.add(cores.get((coreCursor + i) % cores.size()));
        // Keep the hatch probes from aliasing the independent twenty-second growth turns.
        if (tick % FleshNetherRules.GROWTH_INTERVAL == 0) {
            coreCursor = (coreCursor + chosen.size()) % cores.size();
        }
        List<BlockPos> selected = List.copyOf(chosen);
        scanPages.keySet().retainAll(cores);
        Map<BlockPos,FleshColonyEcology.Scan> scans = new HashMap<>();
        for (BlockPos core : selected) {
            int page = scanPages.getOrDefault(core, 0);
            scans.put(core, FleshColonyEcology.scan(FleshColonyLayout.forCoordinate(rt.seed(), core.x(), core.z()),
                    page, this::block, this::edited));
            scanPages.put(core, (page + 1) % 256);
        }
        LinkedHashMap<BlockPos, Cell> cells = new LinkedHashMap<>();
        List<BlockPos> growth = tick % FleshNetherRules.GROWTH_INTERVAL == 0
                ? selected.stream().flatMap(core -> scans.get(core).growth().stream()).toList() : List.of();
        for (var cell : growth) {
            change(cells, cell, Blocks.FLESH_BLOCK, 0);
            if (cell.y() == 64) {
                for (int[] side : new int[][] {{0, -1}, {1, 0}, {0, 1}, {-1, 0}}) {
                    BlockPos source = new BlockPos(cell.x() + side[0], 64, cell.z() + side[1]);
                    int type = block(source.x(), source.y(), source.z());
                    if (FleshColonyEcology.tissueSupport(type)) {
                        check(cells, source);
                        break;
                    }
                }
            }
        }
        MobPersistenceSnapshot hatchMob = null;
        BlockPos hatch = null;
        boolean cocoonStep = tick % FleshNetherRules.COCOON_INTERVAL == 0
                && tick / FleshNetherRules.COCOON_INTERVAL > lastCocoonStep;
        List<BlockPos> cocoons = selected.stream().flatMap(core -> scans.get(core).cocoons().stream()).toList();
        Map<BlockPos, FleshColonyProgress.PopulationCounts> populations = new HashMap<>();
        int matured = 0;
        for (int i = 0; i < cocoons.size(); i++) {
            BlockPos p = cocoons.get((cocoonCursor + i) % cocoons.size());
            int state = state(p);
            boolean large=block(p.x(),p.y(),p.z())==Blocks.FLESH_LARGE_COCOON;
            var parts=large?FleshCocoonParts.resolve(p.x(),p.y(),p.z(),state,this::block,
                    (x,y,z)->state(new BlockPos(x,y,z))).cells():List.of(p);
            if(large&&(state>2||parts.size()!=3||parts.stream().anyMatch(this::edited)))continue;
            BlockPos core = colonyCore(p.x(), p.z());
            var population = populations.computeIfAbsent(core, rt.mobSystem()::fleshPopulation);
            var admission = large?FleshColonyProgress.admitLargeHatch(population)
                    :FleshColonyProgress.admitOrdinaryHatch(FleshNetherRules.hatchRoll(rt.seed(), p.x(), p.z()), population);
            long last = lastHatch.getOrDefault(core, -1L);
            boolean hatchReady = state == 2 && (large?FleshCocoonParts.guardianClearance(p.x(),p.y(),p.z(),this::block,
                    (x,y,z)->state(new BlockPos(x,y,z))):block(p.x(), p.y() + 1, p.z()) == Blocks.AIR)
                    && (lastGlobal < 0 || tick - lastGlobal >= 20) && (last < 0 || tick - last >= 100);
            if (hatchMob == null && admission.accepted() && naturalDistanceAllowed(p) && hatchReady) {
                hatch = p;
                hatchMob = rt.mobSystem().prepareFleshMob(admission.mobKind(), p.x() + .5, p.y(), p.z() + .5);
                for(var part:parts)change(cells,part,Blocks.AIR,0);
                check(cells, new BlockPos(p.x(), p.y() - 1, p.z()));
                if(large)for(var pos:FleshCocoonParts.guardianWitnesses(p.x(),p.y(),p.z()))check(cells,pos);
                else check(cells, new BlockPos(p.x(), p.y() + 1, p.z()));
            } else if (cocoonStep && state < 2 && matured < 4) {
                for(var part:parts)change(cells,part,large?Blocks.FLESH_LARGE_COCOON:Blocks.FLESH_COCOON,
                        FleshNetherRules.nextCocoonStage(state)|((part.y()-p.y())<<2));
                check(cells, new BlockPos(p.x(), p.y() - 1, p.z()));
                matured++;
            }
        }
        if (!cocoons.isEmpty()) cocoonCursor = (cocoonCursor + Math.max(1, matured)) % cocoons.size();
        if (cocoonStep) {
            for (BlockPos core : selected) {
                var cell = scans.get(core).newCocoon();
                if (cell != null) {
                    BlockPos p = new BlockPos(cell.x(), cell.y(), cell.z());
                    if (!cells.containsKey(p)) change(cells, p, Blocks.FLESH_COCOON, 0);
                    check(cells, new BlockPos(p.x(), p.y() - 1, p.z()));
                }
            }
        }
        if (cells.values().stream().noneMatch(Cell::changes)) return;
        pending = new Batch("flesh:" + UUID.randomUUID(), tick, selected, List.copyOf(cells.values()), hatchMob, hatch);
        Set<BlockPos> leased = new HashSet<>(cells.keySet());
        leased.addAll(selected);
        reserved = Set.copyOf(leased);
        submit();
    }

    private void submit() {
        Batch command = pending;
        List<Batch> event = alarmBatches;
        BlockPos eventAnchor = pendingAlarmAnchor;
        long eventRevision = pendingAlarmRevision;
        inFlight = true;
        boolean accepted = rt.ctx().persistenceExecutor().trySubmit(() -> {
            try {
                // Earlier coupled block/TNT snapshots stay ahead of this command on the same FIFO.
                // Never submit-and-wait recursively on the single persistence writer.
                boolean committed = event == null ? persistence.commit(rt.worldId(), rt.seed(), command)
                        : persistence.commitAlarm(rt.worldId(), rt.seed(), eventAnchor, eventRevision, event);
                rt.enqueuePersistenceCompletion(() -> complete(command, committed));
            } catch (RuntimeException failure) {
                rt.enqueuePersistenceCompletion(() -> {
                    if (pending == command) { inFlight = false; nextAttempt = rt.clock().gameTimeMcTicks() / 2 + 20; }
                });
                throw failure;
            }
        });
        if (!accepted) { inFlight = false; nextAttempt = rt.clock().gameTimeMcTicks() / 2 + 20; }
    }

    private void complete(Batch command, boolean committed) {
        if (pending != command) throw new IllegalStateException("flesh owner token changed");
        if (committed) {
            List<Batch> installedBatches = alarmBatches == null ? List.of(command) : alarmBatches;
            if (!installedBatches.stream().allMatch(this::stillMatches)) throw new IllegalStateException("committed flesh source lease lost");
            installing = true;
            try {
                for (Batch installed : installedBatches) {
                    for (Cell cell : installed.cells()) if (cell.changes()) rt.installFleshCell(cell, installed.key());
                    if (installed.mob() != null) {
                        rt.mobSystem().installCommittedStructureEntities(List.of(installed.mob()));
                        pendingAcks.add(installed.mob().getMobId());
                        lastGlobal = installed.tick();
                        lastHatch.put(colonyCore(installed.hatch().x(), installed.hatch().z()), installed.tick());
                    }
                }
                lastCocoonStep = Math.max(lastCocoonStep, command.tick() / FleshNetherRules.COCOON_INTERVAL);
            } finally { installing = false; }
        }
        pending = null; alarmBatches = null; inFlight = false; reserved = Set.of();
        flushAcks();
    }

    private void flushAcks() {
        if (acknowledging || pendingAcks.isEmpty() || rt.ctx().persistenceExecutor() == null) return;
        Set<Long> ids = Set.copyOf(pendingAcks);
        acknowledging = true;
        if (!rt.ctx().persistenceExecutor().trySubmit(() -> {
            try {
                persistence.acknowledge(rt.worldId(), ids);
                rt.enqueuePersistenceCompletion(() -> { pendingAcks.removeAll(ids); acknowledging = false; });
            } catch (RuntimeException failure) {
                rt.enqueuePersistenceCompletion(() -> acknowledging = false);
                throw failure;
            }
        })) acknowledging = false;
    }

    private boolean stillMatches(Batch batch) {
        for (BlockPos core : batch.cores()) {
            int actual = block(core.x(), core.y(), core.z());
            if (edited(core) || actual != WorldTickLoop.UNAVAILABLE_BLOCK && actual != Blocks.HEART_CORE) return false;
        }
        for (Cell cell : batch.cells()) {
            int actual = block(cell.pos().x(), cell.pos().y(), cell.pos().z());
            if (actual != WorldTickLoop.UNAVAILABLE_BLOCK
                    && (actual != cell.before() || state(cell.pos()) != cell.beforeState())
                    || cell.changes() && edited(cell.pos())) return false;
        }
        return true;
    }

    private List<BlockPos> residentCores() {
        Set<BlockPos> cores = new HashSet<>();
        for (long chunk : rt.activeSimulationChunksForMobTick()) {
            int cx = (int) (chunk >> 32), cz = (int) chunk;
            BlockPos core = colonyCore(cx * 16, cz * 16);
            if (heartAlive(core)) cores.add(core);
        }
        return cores.stream().sorted(Comparator.comparingInt(BlockPos::z).thenComparingInt(BlockPos::x)).toList();
    }

    private boolean heartAlive(BlockPos core) {
        return !edited(core) && block(core.x(), core.y(), core.z()) == Blocks.HEART_CORE;
    }

    private boolean planAlarm(long tick) {
        for (var iterator=alarms.values().iterator();iterator.hasNext();) {
            Alarm alarm=iterator.next();
            if(tick-alarm.tick<FleshColonyProgress.ALARM_TELEGRAPH_TICKS)continue;
            iterator.remove();
            var colony=rt.fleshColonyState(alarm.anchor);
            List<BlockPos> sites=alarm.sites.stream().filter(p->{
                int type=block(p.x(),p.y(),p.z());
                if(state(p)!=2||edited(p)||!FleshColonyEcology.tissueSupport(block(p.x(),p.y()-1,p.z())))return false;
                if(type==Blocks.FLESH_COCOON)return block(p.x(),p.y()+1,p.z())==Blocks.AIR;
                return type==Blocks.FLESH_LARGE_COCOON
                        &&FleshCocoonParts.guardianClearance(p.x(),p.y(),p.z(),this::block,(x,y,z)->state(new BlockPos(x,y,z)))
                        &&FleshCocoonParts.resolve(p.x(),p.y(),p.z(),2,this::block,(x,y,z)->state(new BlockPos(x,y,z))).cells().size()==3
                        &&java.util.stream.IntStream.range(0,3).noneMatch(dy->edited(new BlockPos(p.x(),p.y()+dy,p.z())));
            }).toList();
            var decisions=FleshColonyProgress.resolveAlarm(colony,tick,lastHatch.getOrDefault(alarm.core,-1L),
                    rt.mobSystem().fleshPopulation(alarm.core),sites.stream().map(p->new FleshColonyProgress.AlarmCandidate(
                            true,2,tick-alarm.tick,block(p.x(),p.y(),p.z())==Blocks.FLESH_LARGE_COCOON,
                            FleshNetherRules.hatchRoll(rt.seed(),p.x(),p.z()))).toList());
            List<Batch> batches=new ArrayList<>();
            Map<BlockPos,Cell> combined=new HashMap<>();
            for(int index=0;index<sites.size();index++) {
                var decision=decisions.get(index);
                if(!decision.accepted()||lastGlobal>=0&&tick-lastGlobal<20)continue;
                BlockPos p=sites.get(index);
                boolean large=block(p.x(),p.y(),p.z())==Blocks.FLESH_LARGE_COCOON;
                Map<BlockPos,Cell> cells=new LinkedHashMap<>();
                for(int dy=0;dy<(large?3:1);dy++)change(cells,new BlockPos(p.x(),p.y()+dy,p.z()),Blocks.AIR,0);
                check(cells,new BlockPos(p.x(),p.y()-1,p.z()));
                if(large)for(var pos:FleshCocoonParts.guardianWitnesses(p.x(),p.y(),p.z()))check(cells,pos);
                else check(cells,new BlockPos(p.x(),p.y()+1,p.z()));
                if(cells.entrySet().stream().anyMatch(e->{var old=combined.get(e.getKey());
                    return old!=null&&(old.after()!=e.getValue().after()||old.afterState()!=e.getValue().afterState());}))continue;
                Set<BlockPos> total=new HashSet<>(combined.keySet());total.addAll(cells.keySet());
                if(total.size()>128)continue;
                combined.putAll(cells);
                var mob=rt.mobSystem().prepareFleshMob(decision.mobKind(),p.x()+.5,p.y(),p.z()+.5);
                batches.add(new Batch("flesh:"+UUID.randomUUID(),tick,List.of(alarm.core),List.copyOf(cells.values()),mob,p));
            }
            alarmBatches=List.copyOf(batches);pendingAlarmAnchor=alarm.anchor;pendingAlarmRevision=colony.revision();
            pending=batches.isEmpty()?new Batch("flesh:"+UUID.randomUUID(),tick,List.of(alarm.core),List.of(
                    new Cell(alarm.core,Blocks.HEART_CORE,0,Blocks.HEART_CORE,0)),null,null):batches.getFirst();
            Set<BlockPos> leased=new HashSet<>(combined.keySet());leased.add(alarm.core);leased.add(alarm.anchor);
            reserved=Set.copyOf(leased);
            return true;
        }
        return false;
    }

    private int block(int x, int y, int z) { return WorldTickLoop.residentBlockType(rt.accessor(), x, y, z); }
    private boolean naturalDistanceAllowed(BlockPos cocoon) {
        var players = rt.players().values();
        return !players.isEmpty() && players.stream().allMatch(player -> {
            double dx = player.x() - cocoon.x() - .5, dy = player.y() - cocoon.y(),
                    dz = player.z() - cocoon.z() - .5;
            double minimum = FleshColonyProgress.MIN_NATURAL_PLAYER_DISTANCE;
            return dx * dx + dy * dy + dz * dz >= minimum * minimum;
        });
    }
    private int state(BlockPos p) { int block = block(p.x(), p.y(), p.z()); return block < 0 ? -1 : rt.blockStates().get(p.x(), p.y(), p.z(), block); }
    private boolean edited(BlockPos p) { return rt.fleshPlayerEdited(p); }
    private void change(Map<BlockPos, Cell> cells, BlockPos p, int after, int afterState) {
        cells.put(p, new Cell(p, block(p.x(), p.y(), p.z()), state(p), after, afterState));
    }
    private void check(Map<BlockPos, Cell> cells, BlockPos p) {
        cells.putIfAbsent(p, new Cell(p, block(p.x(), p.y(), p.z()), state(p), block(p.x(), p.y(), p.z()), state(p)));
    }
}
