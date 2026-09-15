package com.gameexpert.engine;

import static com.gameexpert.terrain.NoiseSuite.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import com.gameexpert.common.LongOpenHashMap;
import com.gameexpert.engine.structure.AcceptedStructureSite;
import com.gameexpert.engine.structure.StructureAabb;
import com.gameexpert.engine.structure.RuinGenerator;
import com.gameexpert.engine.structure.RuinLootProfile;
import com.gameexpert.engine.structure.StructureGeneratorCatalog;
import com.gameexpert.engine.structure.StructureSiteDescriptor;
import com.gameexpert.engine.structure.StructureTerrainRules;
import com.gameexpert.engine.mob.SpawnerRules;
import com.gameexpert.terrain.Blocks;
import com.gameexpert.terrain.ChunkGenerator;

/**
 * 동결 지형을 바꾸지 않고 이미 생성된 던전·갱도에 상자와 횃불을 얹는 결정론적 장식기.
 * 구조물의 고유 블록 패턴을 읽으므로 ChunkGenerator의 난수 상수를 복제하지 않는다.
 */
public final class ExplorationDecorator {

    /* 계획 결과는 청크가 아니라 site에 귀속한다. 도시 하나가 여러 청크에서 다시 raster되지 않게 한다. */
    private static final int MAX_CACHED_SITE_PLANS = 64;
    private static final int MAX_CACHED_PLACEMENTS = 524_288;
    /** Two compressed 850k-cap cities survive an out-and-back traversal without full-site rerasterization. */
    private static final int MAX_OVERSIZED_SITE_PLANS = 2;
    private static final int MAX_OVERSIZED_SITE_PLACEMENTS = 850_000;
    private static final int MAX_OVERSIZED_CACHED_PLACEMENTS =
            MAX_OVERSIZED_SITE_PLANS * MAX_OVERSIZED_SITE_PLACEMENTS;
    private static final int LARGE_PLAN_THRESHOLD = 100_000;
    private static final int MAX_PINNED_LARGE_PLANS = 4;
    private static final int MINER_CAMP_CORE = 0;
    private static final int MINER_CAMP_AWNING = 1;
    private static final int MINER_CAMP_FALLBACK = 2;
    private static final int MINER_CAMP_STYLE_SALT = 0x6d11;
    private static final int MINER_CAMP_TURN_SALT = 0x6d12;
    private static final int MINER_CAMP_DETAIL_SALT = 0x6d13;
    /** Canonical runtime passes are derived from the descriptor policy, never duplicated here. */
    private static final List<StructureSiteDescriptor.Kind> ACTIVATION_STRUCTURE_KINDS =
            StructureSiteDescriptor.Kind.placedIn(
                    StructureSiteDescriptor.PlacementStage.ACTIVATION);
    private static final List<StructureSiteDescriptor.Kind> DEFERRED_STRUCTURE_KINDS =
            StructureSiteDescriptor.Kind.placedIn(
                    StructureSiteDescriptor.PlacementStage.DEFERRED);
    /**
     * 다른 청크 워커가 같은 site를 이미 계산 중임을 알리는 내부 제어 신호다. 완료 future를 함께
     * 넘겨 호출자가 플랫폼 워커를 점유한 채 join하지 않고, 계산이 끝난 다음 다시 계획할 수 있게 한다.
     */
    static final class SitePlanPendingException extends RuntimeException {
        private final CompletableFuture<?> completion;

        private SitePlanPendingException(CompletableFuture<?> completion) {
            super(null, null, false, false);
            this.completion = completion;
        }

        CompletableFuture<?> completion() {
            return completion;
        }
    }

    public enum Kind {
        DUNGEON, MINESHAFT, CAMPING_SITE, CAMP, MINER_CAMP,
        SMALL_RUIN, MEDIUM_RUIN, FLOODED_RUIN, TOMB_RUIN, GENERAL_RUIN,
        VILLAGE, PILLAGER_OUTPOST, DESERT_TOMB, METEOR_CRATER, RUINED_TOWER, ALTAR,
        OCEAN_RUIN, SHIPWRECK, UNDERWATER_RUIN, UNDERGROUND_RUIN,
        UNDERGROUND_DUNGEON, BURIED_RUIN, SEALED_CHAMBER, UNDERGROUND_CITY,
        UNDERGROUND_PRISON, GLITCH_DUNGEON, MONSTER_SPAWN_ZONE,
        // [MONUMENT] 해저 신전. 새 kind 는 항상 끝에 붙여 기존 ordinal 을 흔들지 않는다.
        OCEAN_MONUMENT,
        WOODLAND_MANSION,
        // [TRIAL] 트라이얼 챔버. 새 kind 는 항상 끝에 붙여 기존 ordinal 을 흔들지 않는다.
        TRIAL_CHAMBER,
        // [DEEP-DARK] 딥다크 도시. 새 kind 는 항상 끝에 붙여 기존 ordinal 을 흔들지 않는다.
        DEEP_DARK_CITY,
        // [SULFUR] 유황 동굴 지대. 새 kind 는 항상 끝에 붙여 기존 ordinal 을 흔들지 않는다.
        SULFUR_CAVERN,
        // [GROVE] 제거된 자작 군락 ID와 얼룩덜룩한 숲 지대. 새 kind 는 항상 끝에 붙인다.
        BIRCH_GROVE,
        DAPPLED_FOREST,
        // [OCEAN-RUINS] 바닐라 해저 유적. 새 kind 는 항상 끝에 붙여 기존 ordinal 을 흔들지 않는다.
        OCEAN_RUINS,
        // [BURIED-TREASURE] 묻힌 보물. 새 kind 는 항상 끝에 붙인다.
        BURIED_TREASURE,
        // [CORAL-REEF] 온수 바다 산호초. 새 kind 는 항상 끝에 붙인다.
        CORAL_REEF,
        RUINED_PORTAL
    }

    public static final class Placement {
        private final BlockPos pos;
        private final int blockType;
        private final int blockState;
        private final Kind kind;
        private final long priority;
        private final long siteKey;
        private final int siteCellX;
        private final int siteCellZ;
        private final boolean siteIdentity;

        Placement(BlockPos pos, int blockType, Kind kind) {
            this(pos, blockType, 0, kind, ((long) kind.ordinal() << 32)
                    | Integer.toUnsignedLong(hashPosition(pos.x(), pos.y(), pos.z())), 0L);
        }

        Placement(BlockPos pos, int blockType, Kind kind, long priority) {
            this(pos, blockType, 0, kind, priority, 0L);
        }

        Placement(BlockPos pos, int blockType, Kind kind, long priority, long siteKey) {
            this(pos, blockType, 0, kind, priority, siteKey);
        }

        Placement(BlockPos pos, int blockType, int blockState, Kind kind,
                long priority, long siteKey) {
            this(pos, blockType, blockState, kind, priority, siteKey, 0, 0, false);
        }

        Placement(BlockPos pos, int blockType, int blockState, Kind kind,
                long priority, long siteKey, int siteCellX, int siteCellZ) {
            this(pos, blockType, blockState, kind, priority, siteKey,
                    siteCellX, siteCellZ, true);
        }

        private Placement(BlockPos pos, int blockType, int blockState, Kind kind,
                long priority, long siteKey, int siteCellX, int siteCellZ,
                boolean siteIdentity) {
            this.pos = pos;
            this.blockType = blockType;
            this.blockState = blockState;
            this.kind = kind;
            this.priority = priority;
            this.siteKey = siteKey;
            this.siteCellX = siteCellX;
            this.siteCellZ = siteCellZ;
            this.siteIdentity = siteIdentity;
        }

        public BlockPos pos() { return pos; }
        public int blockType() { return blockType; }
        public int blockState() { return blockState; }
        public Kind kind() { return kind; }
        public long priority() { return priority; }
        public long siteKey() { return siteKey; }
        public int siteCellX() { return siteCellX; }
        public int siteCellZ() { return siteCellZ; }
        public boolean hasSiteIdentity() { return siteIdentity; }

        private static int hashPosition(int x, int y, int z) {
            int value = x * 0x9e3779b9 ^ y * 0x85ebca6b ^ z * 0xc2b2ae35;
            value ^= value >>> 16;
            value *= 0x7feb352d;
            return value ^ value >>> 15;
        }
    }

    /** Pure, rotated host-camp command used by both runtime placement and parity tests. */
    record MinerCampCommand(int dx, int dy, int dz, int blockType, int blockState, int group) {}

    private final int seed;
    private final RuinGenerator ruinGenerator = new RuinGenerator();
    private final ConcurrentHashMap<SiteIdentity, CompletableFuture<CachedSitePlan>> inFlightPlans =
            new ConcurrentHashMap<>();
    /* TreeMap의 키 순서로만 퇴거하므로 워커 완료 순서가 캐시 상태에 영향을 주지 않는다. */
    private final Map<SiteIdentity, CachedSitePlan> cachedSitePlans = new TreeMap<>();
    private final Map<SiteIdentity, CachedSitePlan> oversizedSitePlans = new TreeMap<>();
    private int cachedPlacementCount;
    private int oversizedPlacementCount;
    private int pinnedLargePlanCount;
    public ExplorationDecorator(int seed) {
        this.seed = seed;
    }


    /** 현재 청크의 스포너 방(중앙 spawner)과 64×64 갱도 셀 교차점을 장식한다. */
    public List<Placement> placements(int chunkX, int chunkZ, SurfaceDecorator.BlockView world) {
        return completePlacements(chunkX, chunkZ, world, null);
    }

    public List<Placement> placements(int chunkX, int chunkZ, SurfaceDecorator.BlockView world,
            Consumer<AcceptedStructureSite> acceptedSiteConsumer) {
        return completePlacements(chunkX, chunkZ, world,
                Objects.requireNonNull(acceptedSiteConsumer, "acceptedSiteConsumer"));
    }

    private List<Placement> completePlacements(int chunkX, int chunkZ,
            SurfaceDecorator.BlockView world,
            Consumer<AcceptedStructureSite> acceptedSiteConsumer) {
        List<AcceptedStructureSite> acceptedCandidates =
                acceptedSiteConsumer == null ? null : new ArrayList<>();
        Set<SiteIdentity> emittedSites = acceptedSiteConsumer == null ? null : new HashSet<>();
        List<Placement> out = basePlacements(chunkX, chunkZ, world,
                acceptedCandidates == null ? null : acceptedCandidates::add, emittedSites);
        decorateExpandedRuins(out, chunkX, chunkZ, world,
                acceptedCandidates == null ? null : acceptedCandidates::add, emittedSites);
        addJackpotSpillMarkers(out, chunkX, chunkZ, world);
        List<Placement> winners = canonicalCollisionOrder(out);
        emitAcceptedWinners(winners, acceptedCandidates, acceptedSiteConsumer);
        return winners;
    }

    /** 기본 던전·갱도·캠프·폐허는 최초 활성화 직후 완성되어야 하는 현재 계약입니다. */
    public List<Placement> activationBasePlacements(int chunkX, int chunkZ,
            SurfaceDecorator.BlockView world) {
        return activationBasePlacementsInternal(chunkX, chunkZ, world, null);
    }

    public List<Placement> activationBasePlacements(int chunkX, int chunkZ,
            SurfaceDecorator.BlockView world,
            Consumer<AcceptedStructureSite> acceptedSiteConsumer) {
        return activationBasePlacementsInternal(chunkX, chunkZ, world,
                Objects.requireNonNull(acceptedSiteConsumer, "acceptedSiteConsumer"));
    }

    private List<Placement> activationBasePlacementsInternal(int chunkX, int chunkZ,
            SurfaceDecorator.BlockView world,
            Consumer<AcceptedStructureSite> acceptedSiteConsumer) {
        List<AcceptedStructureSite> acceptedCandidates =
                acceptedSiteConsumer == null ? null : new ArrayList<>();
        Set<SiteIdentity> emittedSites = acceptedSiteConsumer == null ? null : new HashSet<>();
        List<Placement> out = basePlacements(chunkX, chunkZ, world,
                acceptedCandidates == null ? null : acceptedCandidates::add, emittedSites);
        addJackpotSpillMarkers(out, chunkX, chunkZ, world);
        List<Placement> winners = canonicalCollisionOrder(out);
        emitAcceptedWinners(winners, acceptedCandidates, acceptedSiteConsumer);
        return winners;
    }

    /** Plans only expanded structures and removes coordinates already won by activation-time base structures. */
    public List<Placement> deferredPlacements(int chunkX, int chunkZ,
            SurfaceDecorator.BlockView world, Set<BlockPos> activationWinners) {
        return deferredPlacementsInternal(chunkX, chunkZ, world, activationWinners, null);
    }

    public List<Placement> deferredPlacements(int chunkX, int chunkZ,
            SurfaceDecorator.BlockView world, Set<BlockPos> activationWinners,
            Consumer<AcceptedStructureSite> acceptedSiteConsumer) {
        return deferredPlacementsInternal(chunkX, chunkZ, world, activationWinners,
                Objects.requireNonNull(acceptedSiteConsumer, "acceptedSiteConsumer"));
    }

    private List<Placement> deferredPlacementsInternal(int chunkX, int chunkZ,
            SurfaceDecorator.BlockView world, Set<BlockPos> activationWinners,
            Consumer<AcceptedStructureSite> acceptedSiteConsumer) {
        List<AcceptedStructureSite> acceptedCandidates =
                acceptedSiteConsumer == null ? null : new ArrayList<>();
        Set<SiteIdentity> emittedSites = acceptedSiteConsumer == null ? null : new HashSet<>();
        List<Placement> expanded = new ArrayList<>();
        decorateExpandedRuins(expanded, chunkX, chunkZ, world,
                acceptedCandidates == null ? null : acceptedCandidates::add, emittedSites);
        List<Placement> collisionWinners = canonicalCollisionOrder(expanded);
        if (activationWinners.isEmpty()) {
            List<Placement> winners = List.copyOf(collisionWinners);
            emitAcceptedWinners(winners, acceptedCandidates, acceptedSiteConsumer);
            return winners;
        }
        List<Placement> deferred = new ArrayList<>(collisionWinners.size());
        for (Placement placement : collisionWinners) {
            if (!activationWinners.contains(placement.pos())) deferred.add(placement);
        }
        List<Placement> winners = List.copyOf(deferred);
        emitAcceptedWinners(winners, acceptedCandidates, acceptedSiteConsumer);
        return winners;
    }

    private List<Placement> basePlacements(int chunkX, int chunkZ,
            SurfaceDecorator.BlockView world,
            Consumer<AcceptedStructureSite> acceptedSiteConsumer,
            Set<SiteIdentity> emittedSites) {
        List<Placement> out = new ArrayList<>();
        int baseX = chunkX * Blocks.CHUNK_X;
        int baseZ = chunkZ * Blocks.CHUNK_Z;
        // The legacy raster dungeon roll was removed with the legacy final raster. The canonical
        // 26.3 product owns dungeon placement, so this pass only decorates a spawner that the
        // served chunk actually contains.
        decorateDungeonAtSpawner(out, baseX + 8, baseZ + 8, world);

        // 갱도 셀 중심은 (mx*64+32,mz*64+32), 즉 로컬 청크 (2 mod 4)의 (0,0)이다.
        if ((chunkX & 3) == 2 && (chunkZ & 3) == 2) {
            int centerX = baseX;
            int centerZ = baseZ;
            for (int y = 14; y <= 29; y++) {
                if (isMineIntersection(centerX, y, centerZ, world)) {
                    decorateMineshaft(out, centerX, y, centerZ, world);
                    break;
                }
            }
        }
        decorateIndependentStructures(
                out, ACTIVATION_STRUCTURE_KINDS, chunkX, chunkZ, world,
                acceptedSiteConsumer, emittedSites);
        return out;
    }

    private void decorateDungeonAtSpawner(List<Placement> out, int x, int z,
            SurfaceDecorator.BlockView world) {
        for (int y = 9; y <= 38; y++) {
            int block = world.getBlock(x, y, z);
            if (block >= Blocks.SPAWNER_BASE && block <= Blocks.SPAWNER_BASE + 2) {
                decorateDungeon(out, x, y, z, world);
            }
        }
    }

    /** 트랩도어 조각과 작은 버섯 군집으로 열린 자루에서 내용물이 흩어진 흔적을 남긴다. */
    private void addJackpotSpillMarkers(List<Placement> out, int chunkX, int chunkZ,
            SurfaceDecorator.BlockView world) {
        List<Placement> chests = out.stream()
                .filter(placement -> Blocks.isChestShaped(placement.blockType()))
                .filter(placement -> hasConfiguredLoot(placement.kind()))
                .toList();
        Set<BlockPos> occupied = new HashSet<>();
        for (Placement placement : out) occupied.add(placement.pos());
        int[][] offsets = {{1, 0}, {0, 1}, {-1, 0}, {0, -1}, {1, 1}, {-1, -1}};
        int[] props = {Blocks.WOOD_TRAPDOOR, Blocks.MUSHROOM_BROWN, Blocks.MUSHROOM_RED};
        for (Placement chest : chests) {
            BlockPos origin = chest.pos();
            if (!ExplorationLoot.hasJackpotPayout(seed, origin.x(), origin.y(), origin.z(), chest.kind())) {
                continue;
            }
            int start = bounded(origin.x(), origin.y(), origin.z(), 0x5a, offsets.length);
            int placed = 0;
            for (int i = 0; i < offsets.length && placed < props.length; i++) {
                int[] offset = offsets[(start + i) % offsets.length];
                BlockPos pos = new BlockPos(origin.x() + offset[0], origin.y(), origin.z() + offset[1]);
                if (floorDiv(pos.x(), Blocks.CHUNK_X) != chunkX
                        || floorDiv(pos.z(), Blocks.CHUNK_Z) != chunkZ) continue;
                if (occupied.contains(pos) || world.getBlock(pos.x(), pos.y(), pos.z()) != Blocks.AIR
                        || world.getBlock(pos.x(), pos.y() - 1, pos.z()) == Blocks.AIR) continue;
                out.add(new Placement(pos, props[placed], chest.kind()));
                occupied.add(pos);
                placed++;
            }
        }
    }

    private void decorateDungeon(List<Placement> out, int x, int y, int z,
            SurfaceDecorator.BlockView world) {
        int count = RuinLootProfile.hasLoot(seed, x, z, RuinLootProfile.Profile.DUNGEON)
                ? 1 + bounded(x, y, z, 0x31, 2) : 0;
        // 최소 5×5 방에서도 ±2는 벽이다. 중앙 스포너를 제외한 내부 3×3 둘레만 사용한다.
        // (0,±1)은 횃불 예약 칸이므로 상자 후보에서 제외한다.
        int[][] candidates = {{-1, -1}, {1, 1}, {-1, 1}, {1, -1}, {-1, 0}, {1, 0}};
        int start = bounded(x, y, z, 0x32, candidates.length);
        for (int i = 0; i < candidates.length && count > 0; i++) {
            int[] offset = candidates[(start + i) % candidates.length];
            int cx = x + offset[0], cz = z + offset[1];
            int floor = world.getBlock(cx, y - 1, cz);
            if (world.getBlock(cx, y, cz) == Blocks.AIR
                    && (floor == Blocks.COBBLE || floor == Blocks.MOSSY_COBBLE)) {
                out.add(new Placement(new BlockPos(cx, y, cz), Blocks.CHEST, Kind.DUNGEON));
                count--;
            }
        }
        addTorchIfFree(out, x, y, z - 1, Kind.DUNGEON, world);
        addTorchIfFree(out, x, y, z + 1, Kind.DUNGEON, world);
    }

    private boolean isMineIntersection(int x, int y, int z, SurfaceDecorator.BlockView world) {
        for (int dz = 0; dz < 3; dz++) {
            for (int dx = 0; dx < 3; dx++) {
                if (world.getBlock(x + dx, y - 1, z + dz) != Blocks.PLANK
                        || world.getBlock(x + dx, y, z + dz) != Blocks.AIR
                        || world.getBlock(x + dx, y + 1, z + dz) != Blocks.AIR) return false;
            }
        }
        return true;
    }

    private void decorateMineshaft(List<Placement> out, int x, int y, int z,
            SurfaceDecorator.BlockView world) {
        int count = RuinLootProfile.hasLoot(seed, x, z, RuinLootProfile.Profile.MINESHAFT)
                ? bounded(x, y, z, 0x41, 3) : 0; // 보유 site 안에서 셀당 0~2
        int[][] candidates = {{0, 0}, {2, 2}, {0, 2}, {2, 0}};
        int start = bounded(x, y, z, 0x42, candidates.length);
        for (int i = 0; i < candidates.length && count > 0; i++) {
            int[] offset = candidates[(start + i) % candidates.length];
            int cx = x + offset[0], cz = z + offset[1];
            if (world.getBlock(cx, y, cz) == Blocks.AIR) {
                out.add(new Placement(new BlockPos(cx, y, cz), Blocks.CHEST, Kind.MINESHAFT));
                count--;
            }
        }
        addTorchIfFree(out, x + 1, y, z, Kind.MINESHAFT, world);
        addTorchIfFree(out, x + 1, y, z + 2, Kind.MINESHAFT, world);

        // Vanilla corridor constructor: rail 1/3, otherwise cave-spider corridor 1/23.
        if (bounded(x, y, z, 0x43, 3) != 0 && bounded(x, y, z, 0x44, 23) == 0
                && world.getBlock(x + 1, y, z + 1) == Blocks.AIR) {
            BlockPos spawner = new BlockPos(x + 1, y, z + 1);
            out.add(new Placement(spawner, Blocks.SPAWNER_BASE + 1,
                    SpawnerRules.CAVE_SPIDER, Kind.MINESHAFT,
                    ((long) Kind.MINESHAFT.ordinal() << 32)
                            | Integer.toUnsignedLong(Placement.hashPosition(
                                    spawner.x(), spawner.y(), spawner.z())),
                    0L));
            int[][] webs = {{0, 0}, {2, 0}, {0, 2}, {2, 2}, {1, 0}, {1, 2}};
            for (int[] web : webs) {
                int wx = x + web[0], wz = z + web[1];
                if (world.getBlock(wx, y, wz) == Blocks.AIR) {
                    out.add(new Placement(new BlockPos(wx, y, wz), Blocks.COBWEB,
                            Kind.MINESHAFT));
                }
            }
        }

        // The miner camp is host-bound: it is emitted only for a verified generated mine cavity.
        StructureSiteDescriptor descriptor = StructureSiteDescriptor.atCell(seed,
                StructureSiteDescriptor.Kind.MINER_CAMP, floorDiv(x, 31), floorDiv(z, 31));
        addMinerCamp(out, descriptor, x, y, z, world);
    }

    private void addMinerCamp(List<Placement> out, StructureSiteDescriptor descriptor,
            int x, int y, int z, SurfaceDecorator.BlockView world) {
        List<MinerCampCommand> grammar = minerCampGrammar(seed, x, y, z);
        if (!minerCampGroupFits(grammar, MINER_CAMP_CORE, x, y, z, world)) return;
        boolean awningFits = minerCampGroupFits(
                grammar, MINER_CAMP_AWNING, x, y, z, world);
        int optionalGroup = awningFits ? MINER_CAMP_AWNING : MINER_CAMP_FALLBACK;
        for (MinerCampCommand command : grammar) {
            if (command.group() != MINER_CAMP_CORE && command.group() != optionalGroup) continue;
            int px = x + command.dx(), py = y + command.dy(), pz = z + command.dz();
            out.add(new Placement(new BlockPos(px, py, pz), command.blockType(),
                    command.blockState(), Kind.MINER_CAMP,
                    descriptor.collisionKey(px, py, pz), 0L));
        }
    }

    private static boolean minerCampGroupFits(List<MinerCampCommand> grammar, int group,
            int x, int y, int z, SurfaceDecorator.BlockView world) {
        boolean present = false;
        for (MinerCampCommand command : grammar) {
            if (command.group() != group) continue;
            present = true;
            int px = x + command.dx(), py = y + command.dy(), pz = z + command.dz();
            if (world.getBlock(px, py, pz) != Blocks.AIR
                    || command.dy() == 0 && world.getBlock(px, py - 1, pz) == Blocks.AIR) {
                return false;
            }
        }
        return present;
    }

    /**
     * A 3x3 mine intersection is deliberately kept traversable through its centre and one outer
     * entrance. The low roof is optional as a unit, so a natural low ceiling yields an open camp
     * instead of floating fragments. Horizontal logs and directional states rotate with the plan.
     */
    static List<MinerCampCommand> minerCampGrammar(int seed, int x, int y, int z) {
        int style = minerCampBounded(seed, x, y, z, MINER_CAMP_STYLE_SALT, 4);
        // The host intersection owns torches at north/south edge centres. East/west mirroring
        // keeps both bed halves off that fixed axis while retaining two silhouettes per style.
        int turn = minerCampBounded(seed, x, y, z, MINER_CAMP_TURN_SALT, 2) * 2;
        int detail = minerCampBounded(seed, x, y, z, MINER_CAMP_DETAIL_SALT, 3);
        List<MinerCampCommand> commands = new ArrayList<>(18);

        addMinerCampCommand(commands, turn, 0, 0, 0, Blocks.BED, 2, MINER_CAMP_CORE);
        addMinerCampCommand(commands, turn, 0, 0, 1, Blocks.BED,
                2 | BuildingBlockRules.BED_HEAD, MINER_CAMP_CORE);
        addMinerCampCommand(commands, turn, 2, 0, 0, Blocks.CRAFTING_TABLE, 0,
                MINER_CAMP_CORE);
        addMinerCampCommand(commands, turn, 2, 0, 1, Blocks.FURNACE, 3,
                MINER_CAMP_CORE);
        addMinerCampCommand(commands, turn, 0, 0, 2, Blocks.LOG_X, 0,
                MINER_CAMP_CORE);

        switch (style) {
            case 0 -> {
                // Long lean-to: a sleeping-side fly reaches back to two grounded rear posts.
                addMinerCampRearPosts(commands, turn);
                addMinerCampRoof(commands, turn,
                        new int[][] {{0, 0}, {0, 1}, {0, 2}, {1, 2}, {2, 2}});
                addMinerCampFallback(commands, turn, detail);
            }
            case 1 -> {
                // Low work awning: a two-deep slab plane shelters both bedroll and worksite.
                addMinerCampRearPosts(commands, turn);
                addMinerCampRoof(commands, turn,
                        new int[][] {{0, 1}, {1, 1}, {2, 1}, {0, 2}, {1, 2}, {2, 2}});
                addMinerCampFallback(commands, turn, detail);
                addMinerCampCommand(commands, turn, 0, 1, 2, Blocks.WOOD_FENCE, 0,
                        MINER_CAMP_FALLBACK);
            }
            case 2 -> {
                // Open bivouac: loose supplies and occasional old webbing replace a roof.
                addMinerCampCommand(commands, turn, 2, 0, 2,
                        detail == 0 ? Blocks.MOSSY_COBBLE_SLAB : Blocks.HAY_BLOCK,
                        0, MINER_CAMP_CORE);
                if (detail == 2) {
                    addMinerCampCommand(commands, turn, 0, 1, 2, Blocks.COBWEB, 0,
                            MINER_CAMP_CORE);
                }
            }
            default -> {
                // Partly collapsed fly: the intact half remains tied to the log post.
                addMinerCampCommand(commands, turn, 0, 1, 2, Blocks.WOOD_FENCE, 0,
                        MINER_CAMP_AWNING);
                addMinerCampRoof(commands, turn,
                        new int[][] {{0, 1}, {0, 2}, {1, 2}});
                addMinerCampCommand(commands, turn, 2, 0, 2, Blocks.HAY_BLOCK, 0,
                        MINER_CAMP_AWNING);
                addMinerCampCommand(commands, turn, 2, 1, 2, Blocks.COBWEB, 0,
                        MINER_CAMP_AWNING);
                addMinerCampFallback(commands, turn, detail);
                addMinerCampCommand(commands, turn, 2, 1, 2, Blocks.COBWEB, 0,
                        MINER_CAMP_FALLBACK);
            }
        }
        return List.copyOf(commands);
    }

    private static void addMinerCampRearPosts(List<MinerCampCommand> commands, int turn) {
        addMinerCampCommand(commands, turn, 0, 1, 2, Blocks.WOOD_FENCE, 0,
                MINER_CAMP_AWNING);
        addMinerCampCommand(commands, turn, 2, 0, 2, Blocks.WOOD_FENCE, 0,
                MINER_CAMP_AWNING);
        addMinerCampCommand(commands, turn, 2, 1, 2, Blocks.WOOD_FENCE, 0,
                MINER_CAMP_AWNING);
    }

    private static void addMinerCampRoof(List<MinerCampCommand> commands, int turn,
            int[][] columns) {
        for (int[] column : columns) {
            addMinerCampCommand(commands, turn, column[0], 2, column[1],
                    Blocks.PLANK_SLAB, 0, MINER_CAMP_AWNING);
        }
    }

    private static void addMinerCampFallback(List<MinerCampCommand> commands, int turn,
            int detail) {
        addMinerCampCommand(commands, turn, 2, 0, 2,
                detail == 0 ? Blocks.MOSSY_COBBLE_SLAB : Blocks.HAY_BLOCK,
                0, MINER_CAMP_FALLBACK);
    }

    private static void addMinerCampCommand(List<MinerCampCommand> commands, int turn,
            int dx, int dy, int dz, int blockType, int blockState, int group) {
        int rotatedX;
        int rotatedZ;
        switch (turn) {
            case 1 -> {
                rotatedX = 2 - dz;
                rotatedZ = dx;
            }
            case 2 -> {
                rotatedX = 2 - dx;
                rotatedZ = 2 - dz;
            }
            case 3 -> {
                rotatedX = dz;
                rotatedZ = 2 - dx;
            }
            default -> {
                rotatedX = dx;
                rotatedZ = dz;
            }
        }
        if (blockType == Blocks.LOG_X && (turn & 1) != 0) blockType = Blocks.LOG_Z;
        if (blockType == Blocks.BED || blockType == Blocks.FURNACE) {
            blockState = blockState & ~BuildingBlockRules.FACING_MASK
                    | (blockState + turn) & BuildingBlockRules.FACING_MASK;
        }
        commands.add(new MinerCampCommand(
                rotatedX, dy, rotatedZ, blockType, blockState, group));
    }

    static long minerCampGrammarFingerprint() {
        long hash = 0xcbf29ce484222325L;
        for (int sample = 0; sample < 256; sample++) {
            List<MinerCampCommand> commands = minerCampGrammar(0x13579bdf,
                    sample * 37 - 5_000, 14 + (sample & 15), 3_000 - sample * 53);
            hash = minerCampFingerprintInt(hash, commands.size());
            for (MinerCampCommand command : commands) {
                hash = minerCampFingerprintInt(hash, command.dx());
                hash = minerCampFingerprintInt(hash, command.dy());
                hash = minerCampFingerprintInt(hash, command.dz());
                hash = minerCampFingerprintInt(hash, command.blockType());
                hash = minerCampFingerprintInt(hash, command.blockState());
                hash = minerCampFingerprintInt(hash, command.group());
            }
        }
        return hash;
    }

    private static long minerCampFingerprintInt(long hash, int value) {
        for (int shift = 0; shift < 32; shift += 8) {
            hash ^= value >>> shift & 0xff;
            hash *= 0x100000001b3L;
        }
        return hash;
    }

    private static int minerCampBounded(int seed, int x, int y, int z, int salt, int bound) {
        long h = seed;
        h = (h ^ x * 0x9E3779B97F4A7C15L) * 0xBF58476D1CE4E5B9L;
        h = (h ^ y * 0x94D049BB133111EBL) * 0x94D049BB133111EBL;
        h ^= z * 0x632BE59BD9B4E019L ^ salt;
        h ^= h >>> 30;
        return (int) Long.remainderUnsigned(h, bound);
    }

    private void decorateIndependentStructures(List<Placement> out,
            List<StructureSiteDescriptor.Kind> kinds, int chunkX, int chunkZ,
            SurfaceDecorator.BlockView world,
            Consumer<AcceptedStructureSite> acceptedSiteConsumer,
            Set<SiteIdentity> emittedSites) {
        StructureAabb chunk = new StructureAabb(chunkX * Blocks.CHUNK_X, Blocks.MIN_Y,
                chunkZ * Blocks.CHUNK_Z, chunkX * Blocks.CHUNK_X + Blocks.CHUNK_X - 1,
                Blocks.MAX_Y, chunkZ * Blocks.CHUNK_Z + Blocks.CHUNK_Z - 1);
        for (StructureSiteDescriptor.Kind kind : kinds) {
            addRuinKind(out, kind, chunk, world, acceptedSiteConsumer, emittedSites);
        }
    }

    /** Larger independent structures are planned only by the deferred worker pass. */
    private void decorateExpandedRuins(List<Placement> out, int chunkX, int chunkZ,
            SurfaceDecorator.BlockView world,
            Consumer<AcceptedStructureSite> acceptedSiteConsumer,
            Set<SiteIdentity> emittedSites) {
        decorateIndependentStructures(
                out, DEFERRED_STRUCTURE_KINDS, chunkX, chunkZ, world,
                acceptedSiteConsumer, emittedSites);
    }

    private void addRuinKind(List<Placement> out, StructureSiteDescriptor.Kind kind,
            StructureAabb chunk, SurfaceDecorator.BlockView world,
            Consumer<AcceptedStructureSite> acceptedSiteConsumer,
            Set<SiteIdentity> emittedSites) {
        visitRuinCandidates(kind, chunk, site -> {
            TickSafetyTelemetry.record(TickSafetyTelemetry.Event.STRUCTURE_PLANNING);
            Consumer<AcceptedStructureSite> siteConsumer = null;
            if (acceptedSiteConsumer != null) {
                siteConsumer = accepted -> {
                    SiteIdentity identity = new SiteIdentity(
                            site.kind(), site.cellX(), site.cellZ());
                    if (emittedSites.add(identity)) acceptedSiteConsumer.accept(accepted);
                };
            }
            cachedPlan(site, world).appendPlacementsForChunk(out,
                    floorDiv(chunk.minX(), Blocks.CHUNK_X),
                    floorDiv(chunk.minZ(), Blocks.CHUNK_Z), placementKind(kind), site,
                    siteConsumer,
                    // 앵커 바이옴은 채택이 확정된 사이트에서만 한 번 샘플한다. 후보를 훑을 때마다
                    // 지형을 읽으면 계획 비용이 커지므로 지연 평가로 넘긴다.
                    () -> world.noiseBiomeAt(site.anchorX(),
                            canonicalSurfaceY(site.anchorX(), site.anchorZ(), world),
                            site.anchorZ()));
            return false;
        });
    }

    /** Reads the accepted custom site's anchor only from the canonical chunk product view. */
    private static int canonicalSurfaceY(int x, int z, SurfaceDecorator.BlockView world) {
        for (int y = Blocks.MAX_Y; y >= Blocks.MIN_Y; y--) {
            int block = world.getBlock(x, y, z);
            if (block == Blocks.AIR || StructureTerrainRules.isVegetation(block)) continue;
            return y;
        }
        return Blocks.MIN_Y - 1;
    }

    /**
     * Exact cheap preflight for deferred work. A false result guarantees that the full planner would inspect no
     * provisionally present site whose possible write bounds reach this chunk.
     */
    boolean hasDeferredCandidate(int chunkX, int chunkZ) {
        StructureAabb chunk = new StructureAabb(chunkX * Blocks.CHUNK_X, Blocks.MIN_Y,
                chunkZ * Blocks.CHUNK_Z, chunkX * Blocks.CHUNK_X + Blocks.CHUNK_X - 1,
                Blocks.MAX_Y, chunkZ * Blocks.CHUNK_Z + Blocks.CHUNK_Z - 1);
        for (StructureSiteDescriptor.Kind kind : DEFERRED_STRUCTURE_KINDS) {
            if (visitRuinCandidates(kind, chunk, site -> true)) return true;
        }
        return false;
    }

    /** Returns as soon as the visitor accepts a candidate; otherwise visits all exact candidates in cell order. */
    private boolean visitRuinCandidates(StructureSiteDescriptor.Kind kind, StructureAabb chunk,
            java.util.function.Predicate<StructureSiteDescriptor> visitor) {
        if (!kind.independentlyPlaced()) {
            throw new IllegalArgumentException(kind + " requires a verified host");
        }
        int cellSize = kind.cellSize();
        int radius = (kind.maxReach() + cellSize - 1) / cellSize + 1;
        int minCellX = floorDiv(chunk.minX(), cellSize) - radius;
        int maxCellX = floorDiv(chunk.maxX(), cellSize) + radius;
        int minCellZ = floorDiv(chunk.minZ(), cellSize) - radius;
        int maxCellZ = floorDiv(chunk.maxZ(), cellSize) + radius;
        for (int cellZ = minCellZ; cellZ <= maxCellZ; cellZ++) {
            for (int cellX = minCellX; cellX <= maxCellX; cellX++) {
                StructureSiteDescriptor site = StructureSiteDescriptor.atCell(seed, kind, cellX, cellZ);
                if (!site.provisionallyExists()) continue;
                if (!StructureGeneratorCatalog.possibleHorizontalWriteIntersects(site, chunk)) continue;
                if (visitor.test(site)) return true;
            }
        }
        return false;
    }

    /**
     * Admission-only probe for the one structure kind that can allocate more than 100k voxels.
     * Cached and already-running sites do not need another heavy permit.
     */
    boolean requiresUncachedHeavyPlan(int chunkX, int chunkZ) {
        StructureSiteDescriptor.Kind kind = StructureSiteDescriptor.Kind.UNDERGROUND_CITY;
        StructureAabb chunk = new StructureAabb(chunkX * Blocks.CHUNK_X, Blocks.MIN_Y,
                chunkZ * Blocks.CHUNK_Z, chunkX * Blocks.CHUNK_X + Blocks.CHUNK_X - 1,
                Blocks.MAX_Y, chunkZ * Blocks.CHUNK_Z + Blocks.CHUNK_Z - 1);
        return visitRuinCandidates(kind, chunk, site -> {
            SiteIdentity identity = new SiteIdentity(kind,
                    site.cellX(), site.cellZ());
            synchronized (cachedSitePlans) {
                if (cachedSitePlans.containsKey(identity)
                        || oversizedSitePlans.containsKey(identity)) {
                    return false;
                }
            }
            return !inFlightPlans.containsKey(identity);
        });
    }

    /** 동일 site의 동시 요구는 하나의 in-flight 계획을 공유하고, 완료 결과는 청크별로 잘라 쓴다. */
    private CachedSitePlan cachedPlan(StructureSiteDescriptor site,
            SurfaceDecorator.BlockView world) {
        SiteIdentity identity = new SiteIdentity(site.kind(),
                site.cellX(), site.cellZ());
        CachedSitePlan cached;
        synchronized (cachedSitePlans) {
            cached = cachedSitePlans.get(identity);
            if (cached == null) cached = oversizedSitePlans.get(identity);
            if (cached != null) {
                return cached;
            }
        }

        CompletableFuture<CachedSitePlan> created = new CompletableFuture<>();
        CompletableFuture<CachedSitePlan> future = inFlightPlans.putIfAbsent(identity, created);
        if (future == null) {
            try {
                List<RuinGenerator.Voxel> planned = deriveBuildingStates(
                        ruinGenerator.plan(site, world), world);
                CachedSitePlan result = new CachedSitePlan(
                        planned, planned.size() >= LARGE_PLAN_THRESHOLD);
                synchronized (cachedSitePlans) {
                    admitSitePlan(identity, result);
                }
                created.complete(result);
                inFlightPlans.remove(identity, created);
                return result;
            } catch (RuntimeException | Error failure) {
                created.completeExceptionally(failure);
                inFlightPlans.remove(identity, created);
                throw failure;
            }
        }
        if (!future.isDone()) {
            throw new SitePlanPendingException(future);
        }
        return future.join();
    }

    /**
     * Runtime structures are planned as one site, so derive connected/directional state before
     * chunk slicing. This keeps panes, fences and stair corners continuous across chunk borders.
     */
    List<RuinGenerator.Voxel> deriveBuildingStates(List<RuinGenerator.Voxel> voxels,
            SurfaceDecorator.BlockView world) {
        boolean needsState = false;
        for (RuinGenerator.Voxel voxel : voxels) {
            int block = voxel.blockType();
            if (BuildingBlockRules.isStairs(block) || BuildingBlockRules.isWall(block)
                    || BuildingBlockRules.isPane(block) || Blocks.isFence(block)
                    || Blocks.isFenceGate(block) || Blocks.isDoor(block)
                    || block == Blocks.RAIL || Blocks.isBed(block)
                    || block == Blocks.VINE
                    || block >= Blocks.SPAWNER_BASE && block <= Blocks.SPAWNER_BASE + 2
                    || voxel.blockState() != 0) {
                needsState = true;
                break;
            }
        }
        if (!needsState) return List.copyOf(voxels);

        LongOpenHashMap planned = new LongOpenHashMap(Math.max(16, voxels.size()), 0.85f);
        for (int index = 0; index < voxels.size(); index++) {
            RuinGenerator.Voxel voxel = voxels.get(index);
            planned.put(packedPosition(voxel.x(), voxel.y(), voxel.z()), index);
        }
        List<RuinGenerator.Voxel> derived = new ArrayList<>(voxels);
        BuildingBlockRules.StateLookup lookup = new BuildingBlockRules.StateLookup() {
            @Override
            public int block(int x, int y, int z) {
                RuinGenerator.Voxel voxel = plannedVoxel(planned, voxels, x, y, z);
                return voxel == null ? world.getBlock(x, y, z) : voxel.blockType();
            }

            @Override
            public int state(int x, int y, int z, int blockId) {
                RuinGenerator.Voxel voxel = plannedVoxel(planned, derived, x, y, z);
                return voxel != null && voxel.blockType() == blockId ? voxel.blockState() : 0;
            }
        };

        List<Integer> vineIndices = new ArrayList<>();
        for (int index = 0; index < voxels.size(); index++) {
            RuinGenerator.Voxel voxel = voxels.get(index);
            int x = voxel.x();
            int y = voxel.y();
            int z = voxel.z();
            int block = voxel.blockType();
            int state = voxel.blockState();
            if (block == Blocks.VINE) {
                vineIndices.add(index);
                continue;
            }
            if (block >= Blocks.SPAWNER_BASE && block <= Blocks.SPAWNER_BASE + 2) {
                state = SpawnerRules.normalizeState(block, state);
            } else if (Blocks.isFence(block) || BuildingBlockRules.isWall(block)
                    || BuildingBlockRules.isPane(block)) {
                state = BuildingBlockRules.connectionMask(block, x, y, z, lookup);
            } else if (BuildingBlockRules.isStairs(block)) {
                int shape = BuildingBlockRules.stairShape(x, y, z, state, lookup);
                state = (state & 0x07) | shape << BuildingBlockRules.STAIR_SHAPE_SHIFT;
            } else if (Blocks.isFenceGate(block)) {
                int facing = state & BuildingBlockRules.FACING_MASK;
                boolean inWall = (facing & 1) != 0
                        ? BuildingBlockRules.isWall(lookup.block(x, y, z - 1))
                                || BuildingBlockRules.isWall(lookup.block(x, y, z + 1))
                        : BuildingBlockRules.isWall(lookup.block(x - 1, y, z))
                                || BuildingBlockRules.isWall(lookup.block(x + 1, y, z));
                state = inWall ? state | BuildingBlockRules.GATE_IN_WALL
                        : state & ~BuildingBlockRules.GATE_IN_WALL;
            } else if (Blocks.isDoor(block)) {
                boolean doorBelow = lookup.block(x, y - 1, z) == block;
                boolean doorAbove = lookup.block(x, y + 1, z) == block;
                if (doorBelow) state |= BuildingBlockRules.DOOR_UPPER;
                else if (doorAbove) state &= ~BuildingBlockRules.DOOR_UPPER;
                int lowerY = (state & BuildingBlockRules.DOOR_UPPER) != 0 ? y - 1 : y;
                int hinge = BuildingBlockRules.doorHinge(
                        x, lowerY, z, state & BuildingBlockRules.FACING_MASK, lookup);
                state = state & ~BuildingBlockRules.DOOR_HINGE_RIGHT | hinge;
            }
            if (state != voxel.blockState()) {
                derived.set(index, RuinGenerator.Voxel.at(x, y, z, block, state));
            }
        }
        vineIndices.sort(Comparator.comparingInt(
                (Integer index) -> derived.get(index).y()).reversed());
        for (int index : vineIndices) {
            RuinGenerator.Voxel voxel = derived.get(index);
            int state = BuildingBlockRules.vineState(
                    voxel.x(), voxel.y(), voxel.z(), lookup);
            if (state != voxel.blockState()) {
                derived.set(index, RuinGenerator.Voxel.at(
                        voxel.x(), voxel.y(), voxel.z(), Blocks.VINE, state));
            }
        }
        boolean invalidBed = false;
        boolean invalidVine = false;
        for (RuinGenerator.Voxel voxel : derived) {
            if (voxel.blockType() == Blocks.VINE && voxel.blockState() == 0) {
                invalidVine = true;
                continue;
            }
            if (!Blocks.isBed(voxel.blockType())) continue;
            int otherX = BuildingBlockRules.bedOtherX(voxel.x(), voxel.blockState());
            int otherZ = BuildingBlockRules.bedOtherZ(voxel.z(), voxel.blockState());
            RuinGenerator.Voxel other = plannedVoxel(planned, derived, otherX, voxel.y(), otherZ);
            if (other == null || other.blockType() != voxel.blockType()
                    || !BuildingBlockRules.matchingBedStates(
                            voxel.blockState(), other.blockState())) {
                invalidBed = true;
                break;
            }
        }
        if (!invalidBed && !invalidVine) return List.copyOf(derived);
        List<RuinGenerator.Voxel> valid = new ArrayList<>(derived.size());
        for (RuinGenerator.Voxel voxel : derived) {
            if (voxel.blockType() == Blocks.VINE && voxel.blockState() == 0) {
                continue;
            }
            if (!Blocks.isBed(voxel.blockType())) {
                valid.add(voxel);
                continue;
            }
            int otherX = BuildingBlockRules.bedOtherX(voxel.x(), voxel.blockState());
            int otherZ = BuildingBlockRules.bedOtherZ(voxel.z(), voxel.blockState());
            RuinGenerator.Voxel other = plannedVoxel(planned, derived, otherX, voxel.y(), otherZ);
            if (other != null && other.blockType() == voxel.blockType()
                    && BuildingBlockRules.matchingBedStates(
                            voxel.blockState(), other.blockState())) {
                valid.add(voxel);
            }
        }
        return List.copyOf(valid);
    }

    private static RuinGenerator.Voxel plannedVoxel(LongOpenHashMap planned,
            List<RuinGenerator.Voxel> voxels, int x, int y, int z) {
        long index = planned.get(packedPosition(x, y, z), -1L);
        return index < 0 ? null : voxels.get((int) index);
    }

    private static long packedPosition(int x, int y, int z) {
        return ((long) (x & 0x3ffffff) << 35)
                | ((long) (z & 0x3ffffff) << 9)
                | ((y - Blocks.MIN_Y) & 0x1ffL);
    }

    private void admitSitePlan(SiteIdentity identity, CachedSitePlan plan) {
        if (cachedSitePlans.containsKey(identity) || oversizedSitePlans.containsKey(identity)) return;
        if (plan.placementCount > MAX_OVERSIZED_SITE_PLACEMENTS) {
            return;
        }
        if (plan.placementCount > MAX_CACHED_PLACEMENTS
                || (plan.large && (pinnedLargePlanCount >= MAX_PINNED_LARGE_PLANS
                        || cachedPlacementCount + plan.placementCount > MAX_CACHED_PLACEMENTS))) {
            if (oversizedSitePlans.size() >= MAX_OVERSIZED_SITE_PLANS
                    || oversizedPlacementCount + plan.placementCount
                            > MAX_OVERSIZED_CACHED_PLACEMENTS) {
                return;
            }
            oversizedSitePlans.put(identity, plan);
            oversizedPlacementCount += plan.placementCount;
            return;
        }
        cachedSitePlans.put(identity, plan);
        cachedPlacementCount += plan.placementCount;
        if (plan.large) pinnedLargePlanCount++;
        evictDeterministically();
    }

    private void evictDeterministically() {
        while (cachedSitePlans.size() > MAX_CACHED_SITE_PLANS
                || cachedPlacementCount > MAX_CACHED_PLACEMENTS) {
            SiteIdentity victim = null;
            for (Map.Entry<SiteIdentity, CachedSitePlan> entry : cachedSitePlans.entrySet()) {
                if (!entry.getValue().large) {
                    victim = entry.getKey();
                    break;
                }
            }
            /* 대형 결과는 상한 안에서 고정한다. 그래야 교차 청크 수만큼 재계획되지 않는다. */
            if (victim == null) break;
            CachedSitePlan removed = cachedSitePlans.remove(victim);
            cachedPlacementCount -= removed.placementCount;
        }
    }

    private static final class CachedSitePlan {
        private final int placementCount;
        private final StructureAabb bounds;
        private final Map<Long, PackedChunkVoxels> voxelsByChunk;
        private final boolean large;

        private CachedSitePlan(List<RuinGenerator.Voxel> voxels, boolean large) {
            this.placementCount = voxels.size();
            this.large = large;
            int minX = Integer.MAX_VALUE;
            int minY = Integer.MAX_VALUE;
            int minZ = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE;
            int maxY = Integer.MIN_VALUE;
            int maxZ = Integer.MIN_VALUE;
            Map<Long, PackedChunkBuilder> grouped = new HashMap<>();
            for (RuinGenerator.Voxel voxel : voxels) {
                minX = Math.min(minX, voxel.x());
                minY = Math.min(minY, voxel.y());
                minZ = Math.min(minZ, voxel.z());
                maxX = Math.max(maxX, voxel.x());
                maxY = Math.max(maxY, voxel.y());
                maxZ = Math.max(maxZ, voxel.z());
                int chunkX = floorDiv(voxel.x(), Blocks.CHUNK_X);
                int chunkZ = floorDiv(voxel.z(), Blocks.CHUNK_Z);
                grouped.computeIfAbsent(chunkKey(chunkX, chunkZ),
                        ignored -> new PackedChunkBuilder()).add(voxel);
            }
            this.bounds = placementCount == 0 ? null
                    : new StructureAabb(minX, minY, minZ, maxX, maxY, maxZ);
            Map<Long, PackedChunkVoxels> immutable = new HashMap<>(grouped.size());
            for (Map.Entry<Long, PackedChunkBuilder> entry : grouped.entrySet()) {
                immutable.put(entry.getKey(), entry.getValue().build());
            }
            this.voxelsByChunk = Map.copyOf(immutable);
        }

        private void appendPlacementsForChunk(List<Placement> out, int chunkX, int chunkZ,
                Kind placementKind, StructureSiteDescriptor site,
                Consumer<AcceptedStructureSite> acceptedSiteConsumer,
                java.util.function.IntSupplier anchorBiome) {
            PackedChunkVoxels voxels = voxelsByChunk.get(chunkKey(chunkX, chunkZ));
            if (voxels == null) return;
            long siteKey = Integer.toUnsignedLong(site.siteKey());
            boolean contributed = false;
            for (int index = 0; index < voxels.cells.length; index++) {
                int cell = voxels.cells[index];
                int blockType = Short.toUnsignedInt(voxels.blockTypes[index]);
                if (Blocks.isChestShaped(blockType) && !hasConfiguredLoot(placementKind)) continue;
                int x = chunkX * Blocks.CHUNK_X + (cell & 15);
                int z = chunkZ * Blocks.CHUNK_Z + (cell >>> 4 & 15);
                int y = Blocks.MIN_Y + (cell >>> 8);
                int blockState = Byte.toUnsignedInt(voxels.blockStates[index]);
                BlockPos pos = new BlockPos(x, y, z);
                out.add(new Placement(pos, blockType, blockState, placementKind,
                        site.collisionKey(x, y, z), siteKey, site.cellX(), site.cellZ()));
                contributed = true;
            }
            if (contributed && acceptedSiteConsumer != null) {
                acceptedSiteConsumer.accept(new AcceptedStructureSite(
                        site.kind(), site.cellX(), site.cellZ(), siteKey,
                        site.anchorX(), site.anchorZ(), bounds, placementCount,
                        anchorBiome.getAsInt()));
            }
        }

        private static long chunkKey(int chunkX, int chunkZ) {
            return ((long) chunkX << 32) ^ (chunkZ & 0xffff_ffffL);
        }
    }

    /** Seven packed bytes per retained voxel instead of retaining Voxel+BlockPos object graphs. */
    private static final class PackedChunkVoxels {
        private final int[] cells;
        private final short[] blockTypes;
        private final byte[] blockStates;

        private PackedChunkVoxels(int[] cells, short[] blockTypes, byte[] blockStates) {
            this.cells = cells;
            this.blockTypes = blockTypes;
            this.blockStates = blockStates;
        }
    }

    private static final class PackedChunkBuilder {
        private int[] cells = new int[64];
        private short[] blockTypes = new short[64];
        private byte[] blockStates = new byte[64];
        private int size;

        private void add(RuinGenerator.Voxel voxel) {
            if (size == cells.length) {
                int capacity = cells.length << 1;
                cells = Arrays.copyOf(cells, capacity);
                blockTypes = Arrays.copyOf(blockTypes, capacity);
                blockStates = Arrays.copyOf(blockStates, capacity);
            }
            int y = voxel.y() - Blocks.MIN_Y;
            cells[size] = Math.floorMod(voxel.x(), Blocks.CHUNK_X)
                    | Math.floorMod(voxel.z(), Blocks.CHUNK_Z) << 4 | y << 8;
            blockTypes[size] = (short) voxel.blockType();
            blockStates[size] = (byte) voxel.blockState();
            size++;
        }

        private PackedChunkVoxels build() {
            return new PackedChunkVoxels(Arrays.copyOf(cells, size),
                    Arrays.copyOf(blockTypes, size), Arrays.copyOf(blockStates, size));
        }
    }

    private static final class SiteIdentity implements Comparable<SiteIdentity> {
        private final StructureSiteDescriptor.Kind kind;
        private final int cellX;
        private final int cellZ;

        private SiteIdentity(StructureSiteDescriptor.Kind kind,
                int cellX, int cellZ) {
            this.kind = kind;
            this.cellX = cellX;
            this.cellZ = cellZ;
        }

        @Override
        public int compareTo(SiteIdentity other) {
            int result = Integer.compare(kind.ordinal(), other.kind.ordinal());
            if (result != 0) return result;
            result = Integer.compare(cellX, other.cellX);
            return result != 0 ? result : Integer.compare(cellZ, other.cellZ);
        }

        @Override
        public boolean equals(Object value) {
            if (!(value instanceof SiteIdentity other)) return false;
            return kind == other.kind && cellX == other.cellX && cellZ == other.cellZ;
        }

        @Override
        public int hashCode() {
            int result = kind.ordinal();
            result = 31 * result + cellX;
            return 31 * result + cellZ;
        }
    }

    /**
     * Compile-time catalog lock: adding a descriptor kind requires an explicit placement mapping.
     * Do not replace this with name/valueOf coupling or add a default branch.
     */
    public static Kind placementKind(StructureSiteDescriptor.Kind kind) {
        return switch (kind) {
            case DUNGEON -> Kind.DUNGEON;
            case MINESHAFT -> Kind.MINESHAFT;
            case CAMPING_SITE -> Kind.CAMPING_SITE;
            case CAMP -> Kind.CAMP;
            case MINER_CAMP -> Kind.MINER_CAMP;
            case SMALL_RUIN -> Kind.SMALL_RUIN;
            case MEDIUM_RUIN -> Kind.MEDIUM_RUIN;
            case FLOODED_RUIN -> Kind.FLOODED_RUIN;
            case TOMB_RUIN -> Kind.TOMB_RUIN;
            case GENERAL_RUIN -> Kind.GENERAL_RUIN;
            case VILLAGE -> Kind.VILLAGE;
            case PILLAGER_OUTPOST -> Kind.PILLAGER_OUTPOST;
            case DESERT_TOMB -> Kind.DESERT_TOMB;
            case METEOR_CRATER -> Kind.METEOR_CRATER;
            case RUINED_TOWER -> Kind.RUINED_TOWER;
            case ALTAR -> Kind.ALTAR;
            case OCEAN_RUIN -> Kind.OCEAN_RUIN;
            case SHIPWRECK -> Kind.SHIPWRECK;
            case UNDERWATER_RUIN -> Kind.UNDERWATER_RUIN;
            case UNDERGROUND_RUIN -> Kind.UNDERGROUND_RUIN;
            case UNDERGROUND_DUNGEON -> Kind.UNDERGROUND_DUNGEON;
            case BURIED_RUIN -> Kind.BURIED_RUIN;
            case SEALED_CHAMBER -> Kind.SEALED_CHAMBER;
            case UNDERGROUND_CITY -> Kind.UNDERGROUND_CITY;
            case UNDERGROUND_PRISON -> Kind.UNDERGROUND_PRISON;
            case GLITCH_DUNGEON -> Kind.GLITCH_DUNGEON;
            case MONSTER_SPAWN_ZONE -> Kind.MONSTER_SPAWN_ZONE;
            case OCEAN_MONUMENT -> Kind.OCEAN_MONUMENT;
            case WOODLAND_MANSION -> Kind.WOODLAND_MANSION;
            case TRIAL_CHAMBER -> Kind.TRIAL_CHAMBER;
            case DEEP_DARK_CITY -> Kind.DEEP_DARK_CITY;
            case SULFUR_CAVERN -> Kind.SULFUR_CAVERN;
            case BIRCH_GROVE -> Kind.BIRCH_GROVE;
            case DAPPLED_FOREST -> Kind.DAPPLED_FOREST;
            case OCEAN_RUINS -> Kind.OCEAN_RUINS;
            case BURIED_TREASURE -> Kind.BURIED_TREASURE;
            case CORAL_REEF -> Kind.CORAL_REEF;
            case RUINED_PORTAL -> Kind.RUINED_PORTAL;
        };
    }

    public static StructureSiteDescriptor.Kind descriptorKind(Kind kind) {
        return switch (kind) {
            case DUNGEON -> StructureSiteDescriptor.Kind.DUNGEON;
            case MINESHAFT -> StructureSiteDescriptor.Kind.MINESHAFT;
            case CAMPING_SITE -> StructureSiteDescriptor.Kind.CAMPING_SITE;
            case CAMP -> StructureSiteDescriptor.Kind.CAMP;
            case MINER_CAMP -> StructureSiteDescriptor.Kind.MINER_CAMP;
            case SMALL_RUIN -> StructureSiteDescriptor.Kind.SMALL_RUIN;
            case MEDIUM_RUIN -> StructureSiteDescriptor.Kind.MEDIUM_RUIN;
            case FLOODED_RUIN -> StructureSiteDescriptor.Kind.FLOODED_RUIN;
            case TOMB_RUIN -> StructureSiteDescriptor.Kind.TOMB_RUIN;
            case GENERAL_RUIN -> StructureSiteDescriptor.Kind.GENERAL_RUIN;
            case VILLAGE -> StructureSiteDescriptor.Kind.VILLAGE;
            case PILLAGER_OUTPOST -> StructureSiteDescriptor.Kind.PILLAGER_OUTPOST;
            case DESERT_TOMB -> StructureSiteDescriptor.Kind.DESERT_TOMB;
            case METEOR_CRATER -> StructureSiteDescriptor.Kind.METEOR_CRATER;
            case RUINED_TOWER -> StructureSiteDescriptor.Kind.RUINED_TOWER;
            case ALTAR -> StructureSiteDescriptor.Kind.ALTAR;
            case OCEAN_RUIN -> StructureSiteDescriptor.Kind.OCEAN_RUIN;
            case SHIPWRECK -> StructureSiteDescriptor.Kind.SHIPWRECK;
            case UNDERWATER_RUIN -> StructureSiteDescriptor.Kind.UNDERWATER_RUIN;
            case UNDERGROUND_RUIN -> StructureSiteDescriptor.Kind.UNDERGROUND_RUIN;
            case UNDERGROUND_DUNGEON -> StructureSiteDescriptor.Kind.UNDERGROUND_DUNGEON;
            case BURIED_RUIN -> StructureSiteDescriptor.Kind.BURIED_RUIN;
            case SEALED_CHAMBER -> StructureSiteDescriptor.Kind.SEALED_CHAMBER;
            case UNDERGROUND_CITY -> StructureSiteDescriptor.Kind.UNDERGROUND_CITY;
            case UNDERGROUND_PRISON -> StructureSiteDescriptor.Kind.UNDERGROUND_PRISON;
            case GLITCH_DUNGEON -> StructureSiteDescriptor.Kind.GLITCH_DUNGEON;
            case MONSTER_SPAWN_ZONE -> StructureSiteDescriptor.Kind.MONSTER_SPAWN_ZONE;
            case OCEAN_MONUMENT -> StructureSiteDescriptor.Kind.OCEAN_MONUMENT;
            case WOODLAND_MANSION -> StructureSiteDescriptor.Kind.WOODLAND_MANSION;
            case TRIAL_CHAMBER -> StructureSiteDescriptor.Kind.TRIAL_CHAMBER;
            case DEEP_DARK_CITY -> StructureSiteDescriptor.Kind.DEEP_DARK_CITY;
            case SULFUR_CAVERN -> StructureSiteDescriptor.Kind.SULFUR_CAVERN;
            case BIRCH_GROVE -> StructureSiteDescriptor.Kind.BIRCH_GROVE;
            case DAPPLED_FOREST -> StructureSiteDescriptor.Kind.DAPPLED_FOREST;
            case OCEAN_RUINS -> StructureSiteDescriptor.Kind.OCEAN_RUINS;
            case BURIED_TREASURE -> StructureSiteDescriptor.Kind.BURIED_TREASURE;
            case CORAL_REEF -> StructureSiteDescriptor.Kind.CORAL_REEF;
            case RUINED_PORTAL -> StructureSiteDescriptor.Kind.RUINED_PORTAL;
        };
    }

    private List<Placement> canonicalCollisionOrder(List<Placement> input) {
        input.sort(Comparator.comparingInt((Placement p) -> p.pos().x())
                .thenComparingInt(p -> p.pos().y()).thenComparingInt(p -> p.pos().z())
                .thenComparingInt(p -> isActivationBaseKind(p.kind()) ? 0 : 1)
                .thenComparingLong(Placement::priority)
                .thenComparingInt(p -> p.kind().ordinal())
                .thenComparingLong(Placement::siteKey)
                .thenComparingInt(Placement::blockType)
                .thenComparingInt(Placement::blockState));
        Map<BlockPos, Placement> winners = new LinkedHashMap<>();
        for (Placement placement : input) winners.putIfAbsent(placement.pos(), placement);
        return new ArrayList<>(winners.values());
    }

    private static void emitAcceptedWinners(List<Placement> winners,
            List<AcceptedStructureSite> candidates,
            Consumer<AcceptedStructureSite> consumer) {
        if (consumer == null || candidates == null || candidates.isEmpty()) return;
        for (AcceptedStructureSite candidate : candidates) {
            boolean retained = false;
            for (Placement winner : winners) {
                if (winner.hasSiteIdentity()
                        && descriptorKind(winner.kind()) == candidate.kind()
                        && winner.siteCellX() == candidate.cellX()
                        && winner.siteCellZ() == candidate.cellZ()) {
                    retained = true;
                    break;
                }
            }
            if (retained) consumer.accept(candidate);
        }
    }

    /** 전리품 표가 구성된 구조물만 상자 내용물 및 jackpot 환경 표식에 참여한다. */
    public static boolean hasConfiguredLoot(Kind kind) {
        return switch (kind) {
            case DUNGEON, MINESHAFT, CAMP, SMALL_RUIN, MEDIUM_RUIN, FLOODED_RUIN,
                    TOMB_RUIN, GENERAL_RUIN -> true;
            // [SHIPWRECK] 바닐라 1.21.4 상자 3종 표가 착지했다.
            case SHIPWRECK, WOODLAND_MANSION -> true;
            // 자체 지하 유적 생성기의 상자 표식은 canonical 공동 위에서 일반 폐허 표를 쓴다.
            case UNDERGROUND_RUIN -> true;
            case CAMPING_SITE, MINER_CAMP, VILLAGE, PILLAGER_OUTPOST, DESERT_TOMB,
                    METEOR_CRATER, RUINED_TOWER, ALTAR,
                    OCEAN_RUIN, UNDERWATER_RUIN,
                    UNDERGROUND_DUNGEON, BURIED_RUIN, SEALED_CHAMBER, UNDERGROUND_CITY,
                    UNDERGROUND_PRISON, GLITCH_DUNGEON, MONSTER_SPAWN_ZONE,
                    // [MONUMENT] 바닐라 해저 신전에는 상자가 없다.
                    OCEAN_MONUMENT,
                    // [TRIAL] 트라이얼 챔버는 상자가 아니라 금고가 전리품을 배출한다 —
                    // 좌표 해시 상자 표에 참여하지 않는다.
                    TRIAL_CHAMBER -> false;
            // [DEEP-DARK] 딥다크 도시는 전용 전리품 표(ExplorationLoot.fillDeepDarkCity)를 가진다.
            case DEEP_DARK_CITY -> true;
            // [SULFUR] 유황 동굴 지대에는 상자가 없다 — 보상은 광석·수정 채굴 그 자체다.
            // [GROVE] 제거 ID와 얼룩덜룩한 숲 지대에는 상자를 놓지 않는다.
            // [CORAL-REEF] 산호초에도 상자가 없다 — 보상은 산호·해초 채집 그 자체다.
            case SULFUR_CAVERN, BIRCH_GROVE, DAPPLED_FOREST, CORAL_REEF, RUINED_PORTAL -> false;
            // [OCEAN-RUINS] 바닐라 underwater_ruin_{small,big} 표가 착지했다.
            // [BURIED-TREASURE] 바닐라 buried_treasure 표(pool 6개)가 착지했다.
            case OCEAN_RUINS, BURIED_TREASURE -> true;
        };
    }

    /** 활성화 시점의 기본 구조물이 지연 계획 구조물보다 먼저 충돌 좌표를 차지한다. */
    private static boolean isActivationBaseKind(Kind kind) {
        return descriptorKind(kind).placementStage()
                != StructureSiteDescriptor.PlacementStage.DEFERRED;
    }

    private static int floorDiv(int value, int divisor) {
        int quotient = value / divisor;
        int remainder = value % divisor;
        return remainder != 0 && ((value ^ divisor) < 0) ? quotient - 1 : quotient;
    }

    private void addTorchIfFree(List<Placement> out, int x, int y, int z, Kind kind,
            SurfaceDecorator.BlockView world) {
        if (world.getBlock(x, y, z) == Blocks.AIR
                && world.getBlock(x, y - 1, z) != Blocks.AIR) {
            out.add(new Placement(new BlockPos(x, y, z), Blocks.TORCH, kind));
        }
    }

    private int bounded(int x, int y, int z, int salt, int bound) {
        long h = seed;
        h = (h ^ x * 0x9E3779B97F4A7C15L) * 0xBF58476D1CE4E5B9L;
        h = (h ^ y * 0x94D049BB133111EBL) * 0x94D049BB133111EBL;
        h ^= z * 0x632BE59BD9B4E019L ^ salt;
        h ^= h >>> 30;
        return (int) Long.remainderUnsigned(h, bound);
    }
}
