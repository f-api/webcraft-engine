package com.gameexpert.engine;

import java.util.List;
import java.util.function.Supplier;

import com.gameexpert.common.LongOpenHashMap;
import com.gameexpert.block.BlockStateStorage;
import com.gameexpert.engine.mob.MobMutationJournal;
import com.gameexpert.engine.mob.MobSpawner;
import com.gameexpert.engine.mob.MobWorldView;
import com.gameexpert.engine.mob.PlayerSnapshot;
import com.gameexpert.terrain.TerrainAccessor;
import com.gameexpert.terrain.mc.biome.McClimateSampler;

/**
 * 몹 순수 로직({@link MobWorldView} 포트)을 서버 월드 런타임에 연결하는 어댑터(틱 스레드 전용).
 *
 * <ul>
 *   <li>{@code getBlock} → {@link TerrainAccessor#residentBlock}: 상주 snapshot만 읽고 미준비 셀은 unknown(-1)으로 반환.</li>
 *   <li>{@code isSolid} → 서버 블록 규칙(§2: 공기·유체 40~51 비솔리드, 잎/유리/스포너는 솔리드) = {@link Fluids#isSolid}.</li>
 *   <li>{@code surfaceHeight} → 현재 resident snapshot의 실제 최고 점유 셀(물 포함).</li>
 *   <li>{@code worldTime} → {@link WorldClock}.</li>
 *   <li>{@code players} → 이번 틱에 갱신된 접속 플레이어 발좌표 + 생존 여부 스냅샷.</li>
 * </ul>
 *
 * 플레이어 스냅샷은 매 틱 {@link #setPlayers} 로 한 번 교체되며, 한 틱 동안 몹/스포너/화살이 같은 목록을 봅니다.
 */
final class MobWorldViewAdapter implements MobWorldView {

    private final TerrainAccessor accessor;
    private final WorldClock clock;
    private final WeatherSystem weather;
    private final BlockStateStorage states;
    private final McClimateSampler climateSampler;
    private final MobLightEngine lightEngine;
    private final int seed;
    private final long biomeZoomSeed;
    private final Supplier<int[]> worldSpawn;
    private final Difficulty difficulty;
    private boolean endDimension;
    interface GatewayAvailability { boolean available(int x,int y,int z); }
    private GatewayAvailability gatewayAvailability = (x,y,z) -> false;
    void setGatewayAvailability(GatewayAvailability value) { gatewayAvailability=value; }
    @Override public boolean endGatewayAvailable(int x,int y,int z) { return gatewayAvailability.available(x,y,z); }
    void setEndDimension(boolean value) { endDimension = value; }
    @Override public boolean endDimension() { return endDimension; }

    interface CollisionLookup { void boxes(int x, int y, int z, BuildingBlockRules.CollisionBoxVisitor visitor); }
    private CollisionLookup collisionLookup;
    void setCollisionLookup(CollisionLookup lookup) { collisionLookup = lookup; }
    @Override public void forBlockCollisionBoxes(int x, int y, int z, BuildingBlockRules.CollisionBoxVisitor visitor) {
        if (collisionLookup != null) collisionLookup.boxes(x,y,z,visitor);
        else MobWorldView.super.forBlockCollisionBoxes(x,y,z,visitor);
    }

    interface PlacedProjectileLookup {
        PlacedProjectileHit first(double ax, double ay, double az, double bx, double by, double bz);
    }
    private PlacedProjectileLookup placedProjectileLookup = (ax, ay, az, bx, by, bz) -> null;
    void setPlacedProjectileLookup(PlacedProjectileLookup lookup) { placedProjectileLookup = lookup; }
    interface InflatedPlacedProjectileLookup {
        PlacedProjectileHit first(double ax, double ay, double az, double bx, double by, double bz, double inflation);
    }
    private InflatedPlacedProjectileLookup inflatedPlacedProjectileLookup =
            (ax, ay, az, bx, by, bz, inflation) -> placedProjectileLookup.first(ax, ay, az, bx, by, bz);
    void setPlacedProjectileLookup(InflatedPlacedProjectileLookup lookup) {
        inflatedPlacedProjectileLookup = lookup;
        placedProjectileLookup = (ax, ay, az, bx, by, bz) -> lookup.first(ax, ay, az, bx, by, bz, 0.0);
    }
    @Override public PlacedProjectileHit placedProjectileHit(double ax, double ay, double az,
            double bx, double by, double bz, double inflation) {
        return inflatedPlacedProjectileLookup.first(ax, ay, az, bx, by, bz, inflation);
    }

    @Override public PlacedProjectileHit placedProjectileHit(double ax, double ay, double az,
            double bx, double by, double bz) {
        return placedProjectileLookup.first(ax, ay, az, bx, by, bz);
    }

    private List<PlayerSnapshot> players = List.of();
    private Supplier<MobMutationJournal> mobMutationJournal = () -> null;
    private SupportRules.StateLookup candleStates;
    void setCandleStates(SupportRules.StateLookup lookup) { candleStates = lookup; }
    private java.util.Map<Long, int[]> villagerJobWalkTargets = java.util.Map.of();
    private com.gameexpert.engine.mob.villager.VillagerActivityLedger villagerActivityLedger;
    private HiveLookup hiveLookup = (x, y, z, horizontal, vertical) -> null;
    interface HiveLookup { int[] nearest(int x, int y, int z, int horizontal, int vertical); }
    void setHiveLookup(HiveLookup lookup) { hiveLookup = lookup; }
    @Override public int[] nearestBeeHive(int x, int y, int z, int horizontal, int vertical) {
        int[] nearest = hiveLookup.nearest(x, y, z, horizontal, vertical);
        return nearest == null ? EMPTY_HIVE_POSITION : nearest;
    }
    private static final double[] EMPTY_POSITIONS = new double[0];
    private static final int[] EMPTY_HIVE_POSITION = new int[0];
    interface RafflesiaLookup { long nearest(int x, int y, int z, int horizontal, int vertical); }
    private RafflesiaLookup rafflesiaLookup = (x, y, z, horizontal, vertical) -> 0L;
    void setRafflesiaLookup(RafflesiaLookup lookup) { rafflesiaLookup = lookup; }
    @Override public long nearestRafflesia(int x, int y, int z,
            int horizontalRadius, int verticalRadius) {
        return rafflesiaLookup.nearest(x, y, z, horizontalRadius, verticalRadius);
    }
    private double[] catPositions = EMPTY_POSITIONS;
    private int catPositionCoordinateCount;
    interface FireflyLookup { long nearest(double x, double y, double z, int horizontal, int vertical); }
    private FireflyLookup fireflyLookup = (x, y, z, horizontal, vertical) -> 0L;
    void setFireflyLookup(FireflyLookup lookup) { fireflyLookup = lookup; }
    @Override public long nearestFireflyBush(double x, double y, double z,
            int horizontal, int vertical) {
        return fireflyLookup.nearest(x, y, z, horizontal, vertical);
    }
    interface PoisonFrogColonyLookup {
        boolean nearest(double x, double y, double z, int[] out);
    }
    private PoisonFrogColonyLookup poisonFrogColonyLookup = (x, y, z, out) -> false;
    void setPoisonFrogColonyLookup(PoisonFrogColonyLookup lookup) {
        poisonFrogColonyLookup = lookup;
    }
    @Override public boolean nearestPoisonFrogColony(double x, double y, double z, int[] out) {
        return poisonFrogColonyLookup.nearest(x, y, z, out);
    }

    private static final int BIOME_CACHE_CAP = 200_000;
    private static final long CACHE_ABSENT = Long.MIN_VALUE;
    private final LongOpenHashMap biomeCache = new LongOpenHashMap();

    MobWorldViewAdapter(TerrainAccessor accessor, WorldClock clock, WeatherSystem weather,
                        int seed, BlockStateStorage states, Supplier<int[]> worldSpawn,
                        Difficulty difficulty) {
        this.accessor = accessor;
        this.clock = clock;
        this.weather = weather;
        this.states = states;
        this.seed = seed;
        this.biomeZoomSeed = MobSpawner.biomeZoomSeed(seed);
        this.climateSampler = new McClimateSampler(seed);
        this.lightEngine = new MobLightEngine(accessor);
        this.worldSpawn = worldSpawn;
        this.difficulty = Difficulty.orDefault(difficulty);
    }

    @Override
    public Difficulty difficulty() {
        return difficulty;
    }

    @Override public long gameTimeMcTicks() { return clock.gameTimeMcTicks(); }

    /** 저널은 런타임 생성 뒤 durable 저장소로 교체되므로 매 조회마다 현재 인스턴스를 읽습니다. */
    void setMobMutationJournal(Supplier<MobMutationJournal> supplier) {
        this.mobMutationJournal = supplier;
    }

    @Override
    public MobMutationJournal mobMutationJournal() {
        return mobMutationJournal.get();
    }

    /** 주민 직업 배정 lane 이 이번 틱에 확정한 걷기 목표(틱 스레드). */
    void setVillagerJobWalkTargets(java.util.Map<Long, int[]> targets) {
        this.villagerJobWalkTargets = targets == null ? java.util.Map.of() : targets;
    }

    @Override
    public int[] villagerJobWalkTarget(long mobId) {
        return villagerJobWalkTargets.get(mobId);
    }

    /** 주민 활동 원장(틱 스레드). 몹 이동은 그 결정을 읽기만 한다. */
    void setVillagerActivityLedger(
            com.gameexpert.engine.mob.villager.VillagerActivityLedger ledger) {
        this.villagerActivityLedger = ledger;
    }

    @Override
    public com.gameexpert.engine.mob.villager.VillagerActivityLedger.Snapshot villagerActivity(
            long mobId) {
        return villagerActivityLedger == null ? null : villagerActivityLedger.snapshot(mobId);
    }

    /**
     * [CAT] 이번 틱 살아 있는 고양이의 발좌표를 3개씩 이어 붙인 grow-only scratch(틱 스레드).
     * 크리퍼 회피와 팬텀 급강하 중단은 capacity가 아니라 명시적 논리 길이만 읽는다.
     */
    void setCatPositions(double[] positions, int coordinateCount) {
        if (positions == null || coordinateCount < 0 || coordinateCount > positions.length
                || coordinateCount % 3 != 0) {
            throw new IllegalArgumentException("invalid cat coordinate snapshot");
        }
        this.catPositions = positions;
        this.catPositionCoordinateCount = coordinateCount;
    }

    @Override
    public boolean catWithinBox(double minX, double minY, double minZ,
                                double maxX, double maxY, double maxZ) {
        for (int i = 0; i + 2 < catPositionCoordinateCount; i += 3) {
            double x = catPositions[i], y = catPositions[i + 1], z = catPositions[i + 2];
            if (x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ) {
                return true;
            }
        }
        return false;
    }

    @Override
    public double[] nearestCat(double x, double y, double z,
                               double horizontalRange, double verticalRange) {
        double[] nearest = null;
        double bestDistanceSquared = Double.POSITIVE_INFINITY;
        for (int i = 0; i + 2 < catPositionCoordinateCount; i += 3) {
            double catX = catPositions[i], catY = catPositions[i + 1], catZ = catPositions[i + 2];
            if (Math.abs(catX - x) > horizontalRange || Math.abs(catY - y) > verticalRange
                    || Math.abs(catZ - z) > horizontalRange) continue;
            double dx = catX - x, dy = catY - y, dz = catZ - z;
            double distanceSquared = dx * dx + dy * dy + dz * dz;
            if (distanceSquared >= bestDistanceSquared) continue;
            bestDistanceSquared = distanceSquared;
            nearest = new double[] { catX, catY, catZ };
        }
        return nearest;
    }

    /** 틱 시작 시 접속 플레이어 스냅샷을 교체합니다(틱 스레드). */
    void setPlayers(List<PlayerSnapshot> players) {
        lightEngine.beginTick();
        this.players = players;
    }

    void invalidateLightColumn(int x, int z) {
        lightEngine.invalidateColumn(x, z);
    }

    void invalidateLightChunk(int chunkX, int chunkZ) {
        lightEngine.invalidateChunk(chunkX, chunkZ);
    }

    long lightCandidateQueries() { return lightEngine.candidateLightQueries(); }

    long lightMemoHits() { return lightEngine.memoHits(); }

    long lightMemoMisses() { return lightEngine.memoMisses(); }

    long lightBfsVisitedCells() { return lightEngine.bfsVisitedCells(); }

    int lightMaxBfsVisitCount() { return lightEngine.maxBfsVisitCount(); }

    @Override
    public short getBlock(int x, int y, int z) {
        TerrainAccessor.ResidentBlock resident = accessor.residentBlock(x, y, z);
        // 사용할 수 없는 경계는 가짜 AIR가 아니라 통과 불가능한 장벽으로 취급해 상태 전진을 막는다.
        return resident.isAvailable() ? (short) resident.blockType() : (short) -1;
    }

    @Override
    public boolean waterAt(int x, int y, int z) {
        int block = getBlock(x, y, z);
        if (block < 0) return false;
        int state = blockState(x, y, z, block);
        return MobWorldView.super.waterAt(x, y, z)
                || WaterloggedStates.isWaterloggedAt(accessor, block, state, x, y, z);
    }

    @Override
    public boolean isSolid(short blockId) {
        return Fluids.isSolid(blockId & 0xFFFF);
    }

    @Override
    public int blockState(int x, int y, int z, int blockId) {
        if (candleStates != null && com.gameexpert.engine.blocks.CandleRules.isCandle(blockId)) {
            return candleStates.getState(x, y, z, blockId);
        }
        return states.get(x, y, z, blockId);
    }

    @Override
    public int surfaceHeight(int x, int z) {
        return lightEngine.worldSurfaceHeight(x, z);
    }

    @Override
    public int worldSurfaceHeight(int x, int z) {
        return lightEngine.worldSurfaceHeight(x, z);
    }

    @Override
    public int motionBlockingNoLeavesHeight(int x, int z) {
        return lightEngine.motionBlockingNoLeavesHeight(x, z);
    }

    @Override
    public boolean isChunkActive(int chunkX, int chunkZ) {
        return accessor.isChunkActivated(chunkX, chunkZ);
    }

    @Override
    public int noiseBiomeAtQuart(int quartX, int quartY, int quartZ) {
        long key = ((long) (quartX & 0x3ff_ffff) << 38)
                | ((long) (quartY & 0xfff) << 26)
                | (quartZ & 0x3ff_ffffL);
        long cached = biomeCache.get(key, CACHE_ABSENT);
        if (cached != CACHE_ABSENT) return (int) cached;
        int biome = climateSampler.biomeAtQuart(quartX, quartY, quartZ);
        if (biomeCache.size() >= BIOME_CACHE_CAP) biomeCache.clear();
        biomeCache.put(key, biome);
        return biome;
    }

    @Override
    public long worldTime() {
        return clock.worldTime();
    }

    /**
     * [SULFUR] 간헐천 위상의 시계. 환경 틱이 분출을 판정할 때 쓰는 {@code tickNo} 를 몹 틱
     * 진입점({@code MobSystem.tick})이 그대로 실어 주므로, QA 20배속에서도 잠복자의 등장
     * 타이밍이 플레이어의 밀어올림과 한 틱도 갈리지 않는다.
     */
    @Override
    public long worldTick() {
        return worldTick;
    }

    /** 틱 스레드 전용. 이번 틱의 절대 월드 틱을 고정한다. */
    void setWorldTick(long tickNo) {
        this.worldTick = tickNo;
    }

    private long worldTick;

    @Override
    public long dayCount() {
        return clock.dayCount();
    }

    @Override
    public int worldSeed() {
        return seed;
    }

    @Override
    public long biomeZoomSeed() {
        return biomeZoomSeed;
    }

    @Override
    public int[] worldSpawn() {
        return worldSpawn.get();
    }

    @Override
    public int rawSkyLight(int x, int y, int z) {
        return lightEngine.rawSkyLight(x, y, z);
    }

    @Override
    public int blockLight(int x, int y, int z) {
        return lightEngine.blockLight(x, y, z);
    }

    @Override
    public int localBrightness(int x, int y, int z) {
        return lightEngine.lightLevel(x, y, z, clock.worldTime());
    }

    @Override
    public int lightLevel(int x, int y, int z) {
        return lightEngine.lightLevel(x, y, z, clock.worldTime());
    }

    @Override
    public int sunlightLevel(int x, int y, int z) {
        return lightEngine.sunlightLevel(x, y, z, clock.worldTime());
    }

    @Override
    public boolean sunlightAbove(int x, int y, int z, int threshold) {
        return lightEngine.sunlightAbove(x, y, z, clock.worldTime(), threshold);
    }

    @Override
    public boolean isRainingAt(int x, int y, int z) {
        return weather.isRaining() && openToSky(x, y, z);
    }

    @Override
    public List<PlayerSnapshot> players() {
        return players;
    }
}
