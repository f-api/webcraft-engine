package com.gameexpert.world.dimension.voidend;

import com.gameexpert.engine.blocks.VoidEndBlockRules;
import com.gameexpert.terrain.Blocks;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 엔드 차원({@code void_end}) 결정적 생성기. 정적판
 * {@code client/src/world/dimensions/voidEnd/VoidEndGenerator.ts} 와 한 셀도 다르면 안 된다 — 두 파일은
 * 같은 순서·같은 연산을 줄 단위로 옮긴 사본이다.
 *
 * <p>연산 규약(두 언어 동일성의 근거): 정수 해시는 32비트 wrap 곱과 {@code >>>} 만, 실수는 IEEE double 의
 * +,-,*,/,sqrt,floor 만 쓴다(sin/cos/pow 없음 — 바닐라 가시 기둥·관문 고리 좌표는 Java 로 계산한 정수 표).
 * 바닐라 난수 흐름은 {@code java.util.Random} 과 같은 48비트 LCG 다. 섬은 |x|,|z| &lt; 2^24 안에서만 만든다.
 *
 * <p>바닐라 근거(핀 26.3 client jar):
 * <ul>
 * <li>지형: {@link VoidEndTerrain} — {@code noise_settings/end} 밀도 함수의 비트 단위 이식(본섬과
 * 1024 블록 밖 바깥 섬, {@code EndIslandFunction}). 공식 26.3 {@code NoiseBasedChunkGenerator.getBaseColumn}
 * 과 대조했다.</li>
 * <li>{@code TheEndBiomeSource}(청크 중심 침식 → the_end · highlands · midlands · small islands ·
 * barrens), highlands 의 {@code chorus_plant}(count 0..4, 청크 안 무작위 열의 heightmap) 과
 * {@code end_gateway_return}(rarity 700, heightmap + 3..9, 정확 출구 100,50,0), small islands 의
 * {@code end_island_decorated}(rarity 14, 개수 1(3)/2(1), y 55..70, {@code EndIslandFeature}).
 * 뽑기는 바닐라 그대로다: 청크마다 Xoroshiro {@code WorldgenRandom} 장식 시드와 FeatureSorter 순번의
 * {@code setFeatureSeed}, 3×3 청크 구역 바이옴 합집합으로 돌릴 장식을 고르고, {@code BiomeFilter} 는
 * {@code BiomeManager} fuzzy zoom 바이옴을 본다({@link VoidEndDecoration}). 같은 단계 안에서 청크 사이의
 * 순서(바닐라는 생성 순서에 달렸다)는 이웃 3×3 을 (z, x) 순으로 둔다.</li>
 * <li>엔드 도시·엔드 배: {@link VoidEndCity}(surface_structures 구조물, 장식 전에 청크 구역 안의 조각을 쓴다).</li>
 * <li>{@code SpikeFeature}(10개·반지름 42·반지름 2+j/3·높이 76+3j·j∈{1,2} 철창; 수정 개체는 [DRAGON]
 * {@code VoidEndDimension.initialMobs}), {@code EndPodiumFeature(false)}(heightmap 꼭대기 칸에 2.5/3.5 구·기반암
 * 기둥·벽 횃불, 포털 없음 — 바닐라 {@code EnderDragonFight.scanState} 의 {@code spawnExitPortal(false)}),
 * {@code EndPlatformFeature}(흑요석 y48), {@code ChorusFlowerBlock.generatePlant(…, 8)}.</li>
 * <li>엔드 관문: 바닐라처럼 [DRAGON] 드래곤 처치마다 {@code EnderDragonFight.spawnNewGateway} 가 반지름 96·y75
 * 고리의 20자리 중 시드 셔플({@link #gatewayOrder})의 마지막 자리에 {@code EndGatewayFeature}(DELAYED) 를 놓는다.
 * 생성은 관문을 굽지 않는다. [END-GATEWAY] 출구 섬·출구 관문도 생성 단계에서 굽지 않는다 — 바닐라처럼 첫 사용 때
 * <b>살아 있는 월드</b>(플레이어 편집 포함)에서 {@link VoidEndGateways} 로 찾고 세운다
 * ({@code TheEndGatewayBlockEntity.findOrCreateValidTeleportPos}).</li>
 * </ul>
 * 보물 탑(반지름 208 의 작은 섬 위)은 WebCraft 게임 목표다.
 */
public final class VoidEndGenerator {
    // ── 블록 state 어휘(정본은 engine.blocks.VoidEndBlockRules) ──
    public static final int CHORUS_PLANT_NORTH = VoidEndBlockRules.CHORUS_PLANT_NORTH;
    public static final int CHORUS_PLANT_EAST = VoidEndBlockRules.CHORUS_PLANT_EAST;
    public static final int CHORUS_PLANT_SOUTH = VoidEndBlockRules.CHORUS_PLANT_SOUTH;
    public static final int CHORUS_PLANT_WEST = VoidEndBlockRules.CHORUS_PLANT_WEST;
    public static final int CHORUS_PLANT_UP = VoidEndBlockRules.CHORUS_PLANT_UP;
    public static final int CHORUS_PLANT_DOWN = VoidEndBlockRules.CHORUS_PLANT_DOWN;
    public static final int CHORUS_FLOWER_MAX_AGE = VoidEndBlockRules.CHORUS_FLOWER_MAX_AGE;
    public static final int END_ROD_FACING_UP = VoidEndBlockRules.END_ROD_FACING_UP;
    public static final int END_ROD_FACING_DOWN = VoidEndBlockRules.END_ROD_FACING_DOWN;

    public static final int ISLAND_LIMIT = 1 << 24;
    /** 바닐라 SpikeFeature 좌표 floor(42·cos(2(-π+πi/10))), floor(42·sin(…)). */
    private static final int[][] SPIKE_CENTERS = {
        {42, 0}, {33, 24}, {12, 39}, {-13, 39}, {-34, 24},
        {-42, -1}, {-34, -25}, {-13, -40}, {12, -40}, {33, -25},
    };
    /**
     * {@code EnderDragonFight.spawnNewGateway}: floor(96·cos(2(−π + 0.15707963267948966·i))),
     * floor(96·sin(…)) — 핀 26.3 jar 의 {@code Mth.floor}·{@code Math.cos/sin} 로 계산한 표.
     */
    private static final int[][] GATEWAY_RING = {
        {96, 0}, {91, 29}, {77, 56}, {56, 77}, {29, 91}, {-1, 96}, {-30, 91}, {-57, 77}, {-78, 56}, {-92, 29},
        {-96, -1}, {-92, -30}, {-78, -57}, {-57, -78}, {-30, -92}, {0, -96}, {29, -92}, {56, -78}, {77, -57},
        {91, -30},
    };
    public static final int GATEWAY_Y = 75;
    public static final int PLATFORM_X = 100;
    public static final int PLATFORM_Y = 48;
    public static final int PLATFORM_Z = 0;
    private static final int[][] TREASURE_DISTANCE_TABLE = {
        {208, 0}, {147, 147}, {0, 208}, {-147, 147}, {-208, 0}, {-147, -147}, {0, -208}, {147, -147},
    };
    private static final int TREASURE_RADIUS = 22;
    private static final int TREASURE_TOP = 60;
    /** 바닐라 End 차원 높이(0..255)의 바닥: heightmap 이 비었을 때의 지표. */
    private static final int END_MIN_Y = 0;
    private static final int END_MAX_Y = 255;

    private static final int SALT_TREASURE = 0x4001;

    private VoidEndGenerator() {
    }

    // ── 정수 해시 ──
    /** 시드·두 정수 좌표·용도 salt 의 32비트 해시(정본은 {@link VoidEndBlockRules#hash}; 정적판 {@code voidEndHash}). */
    public static int hash(int seed, int a, int b, int salt) {
        return VoidEndBlockRules.hash(seed, a, b, salt);
    }

    private static double unit(int hash) {
        return (hash >>> 8) / 16777216.0;
    }

    /** 장식이 쓰는 바닐라 {@code RandomSource} 의 부분(Legacy 48비트와 Xoroshiro WorldgenRandom 이 구현한다). */
    public interface DecorationRandom {
        int nextInt();

        int nextInt(int bound);

        long nextLong();

        boolean nextBoolean();

        float nextFloat();
    }

    /** {@code java.util.Random}/{@code SingleThreadedRandomSource} 와 같은 48비트 LCG. */
    public static final class Random48 implements DecorationRandom {
        private long seed;

        public Random48(long seed) {
            this.seed = (seed ^ 0x5DEECE66DL) & ((1L << 48) - 1);
        }

        public int next(int bits) {
            seed = seed * 0x5DEECE66DL + 0xBL & ((1L << 48) - 1);
            return (int) (seed >>> 48 - bits);
        }

        @Override
        public int nextInt() {
            return next(32);
        }

        @Override
        public boolean nextBoolean() {
            return next(1) != 0;
        }

        @Override
        public float nextFloat() {
            return next(24) * 5.9604645E-8F;
        }

        @Override
        public int nextInt(int bound) {
            if ((bound & -bound) == bound) return (int) ((long) bound * next(31) >> 31);
            int bits;
            int value;
            do {
                bits = next(31);
                value = bits % bound;
            } while (bits - value + (bound - 1) < 0);
            return value;
        }

        @Override
        public long nextLong() {
            int high = next(32);
            int low = next(32);
            return ((long) high << 32) + low;
        }
    }

    // ── 가시 기둥 ──
    public record Spike(int centerX, int centerZ, int radius, int height, boolean guarded) {
    }

    /** 바닐라 {@code EndSpikeFeature.getSpikesForLevel}: seed → nextLong()&65535 → 0..9 셔플. */
    public static Spike[] spikes(int seed) {
        long shuffleSeed = new Random48(seed).nextLong() & 65535L;
        Random48 random = new Random48(shuffleSeed);
        int[] order = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9};
        for (int i = order.length; i > 1; i--) {
            int j = random.nextInt(i);
            int swap = order[i - 1];
            order[i - 1] = order[j];
            order[j] = swap;
        }
        Spike[] spikes = new Spike[SPIKE_CENTERS.length];
        for (int index = 0; index < spikes.length; index++) {
            int j = order[index];
            spikes[index] = new Spike(SPIKE_CENTERS[index][0], SPIKE_CENTERS[index][1],
                    2 + j / 3, 76 + j * 3, j == 1 || j == 2);
        }
        return spikes;
    }

    // ── 지형 기둥 ──
    private record Column(int bottom, int top) {
    }

    private static final Column NO_COLUMN = new Column(1, 0);

    /**
     * 귀환 포털 기준점: {@code EnderDragonFight.spawnExitPortal} — (0,0) heightmap 의 한 칸 아래(꼭대기
     * 엔드 돌 칸)에 포털 층이 선다(생성 직후라 기반암 하강 반복은 일어나지 않는다, 하한 minY+1).
     */
    public static int podiumY(int seed) {
        return Math.max(END_MIN_Y + 1, VoidEndTerrain.forSeed(seed).sampler().top(0, 0));
    }

    /** 보물 탑 위치와 겉날개 상자(꼭대기 방 북쪽 벽 앞, 남쪽을 보는 state 2). */
    public record TreasureSite(int centerX, int centerZ, int baseY, int chestX, int chestY, int chestZ,
            int chestState) {
    }

    public static TreasureSite treasureSite(int seed) {
        int[] offset = TREASURE_DISTANCE_TABLE[hash(seed, 0, 0, SALT_TREASURE) & 7];
        int baseY = TREASURE_TOP + 1;
        return new TreasureSite(offset[0], offset[1], baseY, offset[0], baseY + 15, offset[1] - 2, 2);
    }

    private static Column treasureColumn(TreasureSite site, int x, int z) {
        int dx = x - site.centerX();
        int dz = z - site.centerZ();
        if (dx < -TREASURE_RADIUS || dx > TREASURE_RADIUS || dz < -TREASURE_RADIUS || dz > TREASURE_RADIUS) {
            return NO_COLUMN;
        }
        double distance = Math.sqrt((double) dx * dx + (double) dz * dz);
        double t = 1 - distance / TREASURE_RADIUS;
        if (t <= 0) return NO_COLUMN;
        int top = TREASURE_TOP - (t < 0.2 ? 1 : 0);
        return new Column(top - (2 + (int) Math.floor(t * Math.sqrt(t) * 30)), top);
    }

    // ── 바깥 지역 장식(바닐라 applyBiomeDecoration) ──
    private static boolean outsideLimit(int chunkX, int chunkZ) {
        long originX = (long) chunkX * 16;
        long originZ = (long) chunkZ * 16;
        return originX <= -ISLAND_LIMIT || originX + 16 >= ISLAND_LIMIT
                || originZ <= -ISLAND_LIMIT || originZ + 16 >= ISLAND_LIMIT;
    }

    /** 칸 쓰기 대상(청크 평면 또는 한 청크의 장식 세계). */
    /** 칸 쓰기 포트. 생성기는 청크 평면에, [DRAGON] 드래곤전은 살아 있는 월드 편집에 쓴다. */
    public interface CellSink {
        void set(int x, int y, int z, int block, int state);
    }

    // ── 청크 조립 ──
    private static final class ChunkWriter implements CellSink {
        final short[] blocks = new short[Blocks.CHUNK_BLOCKS];
        final byte[] states = new byte[Blocks.CHUNK_BLOCKS];
        final int originX;
        final int originZ;

        ChunkWriter(int originX, int originZ) {
            this.originX = originX;
            this.originZ = originZ;
        }

        @Override
        public void set(int x, int y, int z, int block, int state) {
            int lx = x - originX;
            int lz = z - originZ;
            if (lx < 0 || lx >= 16 || lz < 0 || lz >= 16 || y < Blocks.MIN_Y || y > Blocks.MAX_Y) return;
            int index = Blocks.blockIndex(lx, y, lz);
            blocks[index] = (short) block;
            states[index] = (byte) (block == Blocks.AIR ? 0 : state);
        }

        void set(int x, int y, int z, int block) {
            set(x, y, z, block, 0);
        }

        void fillColumn(int lx, int lz, Column column, int block) {
            int bottom = Math.max(column.bottom(), Blocks.MIN_Y);
            int top = Math.min(column.top(), Blocks.MAX_Y);
            for (int y = bottom; y <= top; y++) {
                int index = Blocks.blockIndex(lx, y, lz);
                blocks[index] = (short) block;
                states[index] = 0;
            }
        }

        void write(Map<Long, int[]> cells) {
            for (int[] cell : cells.values()) set(cell[0], cell[1], cell[2], cell[3], cell[4]);
        }
    }

    private static long packCell(int x, int y, int z) {
        return ((long) x << 38) ^ ((long) (y + 512) << 26) ^ (z & 0x3ffffffL);
    }

    /**
     * 한 청크 N 의 장식이 쓴 칸(단계별, 쓴 순서; 값 [x, y, z, 블록, state]).
     *
     * <p>바닐라 {@code applyBiomeDecoration} 은 3×3 청크 구역에 쓰고, 나중에 장식되는 청크는 먼저 장식된 이웃이 쓴
     * 칸(후렴 나무·섬·관문·엔드 도시 조각)을 본다 — 이웃 나무에 가로막힌 후렴 나무는 거기서 멈춘다
     * ({@code ChorusFlowerBlock.allNeighborsEmpty}). 바닐라의 청크 장식 순서는 생성 스케줄에 달렸으므로, 이 저장소는
     * 순서를 청크 좌표의 2×2 위상({@link #decorationPhase}: (짝 x, 짝 z) → (홀 x, 짝 z) → (짝 x, 홀 z) →
     * (홀 x, 홀 z))으로 고정한다. 한 장식이 닿는 칸은 자기 청크 ±8 칸(후렴 퍼짐 8 · 섬 반지름 ≤ 7 · 관문 ±1 · 이웃
     * 검사 1) 안이라 같은 위상의 두 청크(2 청크 떨어짐)는 서로의 칸을 보지 않으므로, 어느 청크를 먼저 만들든 결과가
     * 같다(두 권위 동일). 핀 26.3 전용 서버의 시드 4118 End 월드에서 서쪽 이웃 나무가 먼저 서 있던 청크 경계 후렴
     * 나무 두 그루가 이 순서와 같다.
     */
    static final class Decoration {
        final int chunkX;
        final int chunkZ;
        final Map<Long, int[]> islands = new LinkedHashMap<>();
        final Map<Long, int[]> gateways = new LinkedHashMap<>();
        final Map<Long, int[]> trees = new LinkedHashMap<>();

        Decoration(int chunkX, int chunkZ) {
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
        }
    }

    /** 청크 장식 위상(작을수록 먼저 장식된다). 정적판 {@code voidEndDecorationPhase}. */
    public static int decorationPhase(int chunkX, int chunkZ) {
        return (chunkZ & 1) << 1 | chunkX & 1;
    }

    /** 이 청크보다 먼저 장식되는 3×3 이웃(위상 순, 같은 위상은 (z, x) 순 — 서로 겹치지 않는다). */
    private static List<int[]> earlierNeighbors(int chunkX, int chunkZ) {
        int phase = decorationPhase(chunkX, chunkZ);
        List<int[]> out = new ArrayList<>();
        for (int p = 0; p < phase; p++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int dx = -1; dx <= 1; dx++) {
                    if (decorationPhase(chunkX + dx, chunkZ + dz) == p) out.add(new int[] {chunkX + dx, chunkZ + dz});
                }
            }
        }
        return out;
    }

    /**
     * N 의 장식 세계: 지형 위에 먼저 장식된 이웃들(위상 순)과 N 자신이 바닐라 단계 순서(작은 섬 → 자기 청크의
     * 엔드 도시 조각 → 귀환 관문 → 후렴)로 쓴 칸을 겹친다. 조회는 마지막에 쓴 층부터 본다.
     */
    private static final class DecorationWorld implements CellSink {
        private final int seed;
        private final VoidEndTerrain.Sampler terrain;
        private final Decoration out;
        private final List<Decoration> earlier;
        private final Map<Long, VoidEndCity.ChunkCells> cities = new HashMap<>();
        private Map<Long, int[]> layer;

        DecorationWorld(int seed, VoidEndTerrain.Sampler terrain, Decoration out, List<Decoration> earlier) {
            this.seed = seed;
            this.terrain = terrain;
            this.out = out;
            this.earlier = earlier;
            this.layer = out.islands;
        }

        @Override
        public void set(int x, int y, int z, int block, int state) {
            if (y < Blocks.MIN_Y || y > Blocks.MAX_Y) return;
            layer.put(packCell(x, y, z), new int[] {x, y, z, block, block == Blocks.AIR ? 0 : state});
        }

        private int cityCell(int x, int y, int z) {
            int chunkX = x >> 4;
            int chunkZ = z >> 4;
            long key = (long) chunkX << 32 ^ chunkZ & 0xffffffffL;
            VoidEndCity.ChunkCells cells;
            if (cities.containsKey(key)) {
                cells = cities.get(key);
            } else {
                cells = VoidEndCity.chunkCells(seed, chunkX, chunkZ);
                cities.put(key, cells);
            }
            return cells == null ? -1 : cells.at(x & 15, y, z & 15);
        }

        /** 한 장식 층 묶음(후렴 → 관문 → 그 청크의 엔드 도시 → 작은 섬)에서 칸을 찾는다. 없으면 −1. */
        private int layered(Decoration entry, long key, int x, int y, int z) {
            int[] cell = entry.trees.get(key);
            if (cell != null) return cell[3];
            cell = entry.gateways.get(key);
            if (cell != null) return cell[3];
            if (x >> 4 == entry.chunkX && z >> 4 == entry.chunkZ) {
                int city = cityCell(x, y, z);
                if (city >= 0) return city >>> 8;
            }
            cell = entry.islands.get(key);
            return cell == null ? -1 : cell[3];
        }

        int block(int x, int y, int z) {
            if (y < Blocks.MIN_Y || y > Blocks.MAX_Y) return Blocks.AIR;
            long key = packCell(x, y, z);
            int found = layered(out, key, x, y, z);
            for (int i = earlier.size() - 1; found < 0 && i >= 0; i--) found = layered(earlier.get(i), key, x, y, z);
            if (found >= 0) return found;
            return terrain.isSolid(x, y, z) ? Blocks.END_STONE : Blocks.AIR;
        }

        boolean isEmpty(int x, int y, int z) {
            return block(x, y, z) == Blocks.AIR;
        }

        /**
         * {@code Heightmap.Types.MOTION_BLOCKING}: 가장 높은 {@code blocksMotion} 칸 + 1(빈 기둥은 minY 0).
         * 공기와 {@code forceSolidOff}(사다리·엔드 막대·후렴 식물·꽃)·부분 모양(드래곤 머리)·충돌 없는 관문은
         * 막지 않는다(핀 26.3 Blocks 속성).
         */
        int motionBlockingHeight(int x, int z) {
            for (int y = END_MAX_Y; y >= END_MIN_Y; y--) {
                if (blocksMotion(block(x, y, z))) return y + 1;
            }
            return END_MIN_Y;
        }
    }

    private static boolean blocksMotion(int block) {
        return block != Blocks.AIR && block != Blocks.LADDER && block != Blocks.END_ROD
                && block != Blocks.CHORUS_PLANT && block != Blocks.CHORUS_FLOWER && block != Blocks.DRAGON_HEAD
                && block != Blocks.END_GATEWAY;
    }

    private static final ConcurrentHashMap<Integer, ConcurrentHashMap<Long, Decoration>> DECORATIONS =
            new ConcurrentHashMap<>();
    private static final int DECORATION_CACHE_LIMIT = 4096;

    /** 청크 N 의 장식(시드별 캐시). 같은 입력은 언제나 같은 칸을 낸다. */
    static Decoration decoration(int seed, int chunkX, int chunkZ, VoidEndTerrain.Sampler sampler) {
        ConcurrentHashMap<Long, Decoration> bySeed = DECORATIONS.get(seed);
        if (bySeed == null) {
            if (DECORATIONS.size() >= 8) DECORATIONS.clear();
            bySeed = DECORATIONS.computeIfAbsent(seed, ignored -> new ConcurrentHashMap<>());
        }
        long key = (long) chunkX << 32 ^ chunkZ & 0xffffffffL;
        Decoration cached = bySeed.get(key);
        if (cached != null) return cached;
        Decoration computed = decorate(seed, chunkX, chunkZ, sampler);
        if (bySeed.size() > DECORATION_CACHE_LIMIT) bySeed.clear();
        bySeed.put(key, computed);
        return computed;
    }

    /**
     * {@code ChunkGenerator.applyBiomeDecoration} 의 End 장식. 돌릴 장식은 3×3 청크 구역 바이옴 합집합의
     * 목록({@link VoidEndDecoration#biomeSet}), 장식마다 {@code setFeatureSeed(장식 시드, 순번, 단계)} 의 Xoroshiro
     * {@code WorldgenRandom} 이고, 배치 수정자는 바닐라 {@code FeaturePlacer} 의 깊이 우선 순서로 난수를 쓴다:
     * <ul>
     * <li>{@code end_island_decorated}(0, raw_generation): rarity_filter 1/14(nextFloat) → count weighted
     * [1×3, 2×1](nextInt(4)) → 개수마다 in_square(x, z) → height_range uniform 55..70 → biome → EndIslandFeature.</li>
     * <li>{@code end_gateway_return}(0, surface_structures): rarity 1/700 → in_square → heightmap MOTION_BLOCKING
     * (minY 이하면 버림) → offset y uniform 3..9 → biome → EndGatewayFeature(exact 100,50,0).</li>
     * <li>{@code chorus_plant}(0, vegetal_decoration): count uniform 0..4 → 개수마다 in_square → heightmap →
     * biome → ChorusPlantFeature(빈 칸 ∧ 아래 엔드 돌이면 {@code ChorusFlowerBlock.generatePlant(…, 8)}).</li>
     * </ul>
     */
    private static Decoration decorate(int seed, int chunkX, int chunkZ, VoidEndTerrain.Sampler sampler) {
        Decoration out = new Decoration(chunkX, chunkZ);
        if (outsideLimit(chunkX, chunkZ)) return out;
        int biomes = VoidEndDecoration.biomeSet(seed, chunkX, chunkZ);
        long decorationSeed = VoidEndDecoration.decorationSeed(seed, chunkX, chunkZ);
        List<Decoration> earlier = new ArrayList<>();
        for (int[] neighbor : earlierNeighbors(chunkX, chunkZ)) {
            earlier.add(decoration(seed, neighbor[0], neighbor[1], sampler));
        }
        DecorationWorld world = new DecorationWorld(seed, sampler, out, earlier);
        int minX = chunkX * 16;
        int minZ = chunkZ * 16;
        if ((biomes & 1 << VoidEndTerrain.BIOME_SMALL_ISLANDS) != 0) {
            world.layer = out.islands;
            VoidEndDecoration.WorldgenRandom random = VoidEndDecoration.featureRandom(decorationSeed,
                    VoidEndDecoration.INDEX_END_ISLAND_DECORATED, VoidEndDecoration.STEP_RAW_GENERATION);
            if (random.nextFloat() < 1.0F / 14.0F) {
                int count = random.nextInt(4) < 3 ? 1 : 2;
                for (int i = 0; i < count; i++) {
                    int x = random.nextInt(16) + minX;
                    int z = random.nextInt(16) + minZ;
                    int y = random.nextInt(16) + 55;
                    if (VoidEndDecoration.biomeAt(seed, x, y, z) != VoidEndTerrain.BIOME_SMALL_ISLANDS) continue;
                    endIsland((bx, by, bz, block) -> world.set(bx, by, bz, block, 0), random, x, y, z);
                }
            }
        }
        if ((biomes & 1 << VoidEndTerrain.BIOME_HIGHLANDS) != 0) {
            world.layer = out.gateways;
            VoidEndDecoration.WorldgenRandom random = VoidEndDecoration.featureRandom(decorationSeed,
                    VoidEndDecoration.INDEX_END_GATEWAY_RETURN, VoidEndDecoration.STEP_SURFACE_STRUCTURES);
            if (random.nextFloat() < 1.0F / 700.0F) {
                int x = random.nextInt(16) + minX;
                int z = random.nextInt(16) + minZ;
                int height = world.motionBlockingHeight(x, z);
                if (height > END_MIN_Y) {
                    int y = height + random.nextInt(7) + 3;
                    if (VoidEndDecoration.biomeAt(seed, x, y, z) == VoidEndTerrain.BIOME_HIGHLANDS) {
                        writeGateway(world, x, y, z, VoidEndBlockRules.GATEWAY_RETURN_TO_CENTER);
                    }
                }
            }
            world.layer = out.trees;
            random = VoidEndDecoration.featureRandom(decorationSeed, VoidEndDecoration.INDEX_CHORUS_PLANT,
                    VoidEndDecoration.STEP_VEGETAL_DECORATION);
            int count = random.nextInt(5);
            for (int i = 0; i < count; i++) {
                int x = random.nextInt(16) + minX;
                int z = random.nextInt(16) + minZ;
                int y = world.motionBlockingHeight(x, z);
                if (y <= END_MIN_Y) continue;
                if (VoidEndDecoration.biomeAt(seed, x, y, z) != VoidEndTerrain.BIOME_HIGHLANDS) continue;
                if (world.isEmpty(x, y, z) && world.block(x, y - 1, z) == Blocks.END_STONE) {
                    world.set(x, y, z, Blocks.CHORUS_PLANT, chorusConnections(world, x, y, z));
                    growTree(world, random, x, y, z, x, z, 8, 0);
                }
            }
        }
        return out;
    }

    private static final int[][] HORIZONTAL = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};

    private static boolean allNeighborsEmpty(DecorationWorld world, int x, int y, int z, int ignore) {
        for (int direction = 0; direction < 4; direction++) {
            if (direction == ignore) continue;
            if (!world.isEmpty(x + HORIZONTAL[direction][0], y, z + HORIZONTAL[direction][1])) return false;
        }
        return true;
    }

    private static boolean isChorus(int block) {
        return block == Blocks.CHORUS_PLANT || block == Blocks.CHORUS_FLOWER;
    }

    /** 바닐라 {@code ChorusPlantBlock.getStateWithConnections}: 쓰는 순간의 이웃으로 정한다. */
    private static int chorusConnections(DecorationWorld world, int x, int y, int z) {
        int state = 0;
        if (isChorus(world.block(x, y, z - 1))) state |= CHORUS_PLANT_NORTH;
        if (isChorus(world.block(x + 1, y, z))) state |= CHORUS_PLANT_EAST;
        if (isChorus(world.block(x, y, z + 1))) state |= CHORUS_PLANT_SOUTH;
        if (isChorus(world.block(x - 1, y, z))) state |= CHORUS_PLANT_WEST;
        if (isChorus(world.block(x, y + 1, z))) state |= CHORUS_PLANT_UP;
        int below = world.block(x, y - 1, z);
        if (isChorus(below) || below == Blocks.END_STONE) state |= CHORUS_PLANT_DOWN;
        return state;
    }

    /** 바닐라 {@code ChorusFlowerBlock.growTreeRecursive}(setBlock 마다 그 순간의 연결). */
    private static void growTree(DecorationWorld world, DecorationRandom random, int bx, int by, int bz,
            int ox, int oz, int spread, int iterations) {
        int height = random.nextInt(4) + 1;
        if (iterations == 0) height++;
        for (int j = 0; j < height; j++) {
            if (!allNeighborsEmpty(world, bx, by + j + 1, bz, -1)) return;
            world.set(bx, by + j + 1, bz, Blocks.CHORUS_PLANT, chorusConnections(world, bx, by + j + 1, bz));
            world.set(bx, by + j, bz, Blocks.CHORUS_PLANT, chorusConnections(world, bx, by + j, bz));
        }
        boolean branched = false;
        if (iterations < 4) {
            int branches = random.nextInt(4);
            if (iterations == 0) branches++;
            for (int l = 0; l < branches; l++) {
                int direction = random.nextInt(4);
                int dx = HORIZONTAL[direction][0];
                int dz = HORIZONTAL[direction][1];
                int nx = bx + dx;
                int ny = by + height;
                int nz = bz + dz;
                if (Math.abs(nx - ox) < spread && Math.abs(nz - oz) < spread
                        && world.isEmpty(nx, ny, nz) && world.isEmpty(nx, ny - 1, nz)
                        && allNeighborsEmpty(world, nx, ny, nz, direction + 2 & 3)) {
                    branched = true;
                    world.set(nx, ny, nz, Blocks.CHORUS_PLANT, chorusConnections(world, nx, ny, nz));
                    world.set(nx - dx, ny, nz - dz, Blocks.CHORUS_PLANT, chorusConnections(world, nx - dx, ny, nz - dz));
                    growTree(world, random, nx, ny, nz, ox, oz, spread, iterations + 1);
                }
            }
        }
        if (!branched) world.set(bx, by + height, bz, Blocks.CHORUS_FLOWER, CHORUS_FLOWER_MAX_AGE);
    }

    /** 바닐라 {@code EndIslandFeature}: 반지름 nextInt(3)+4, 층마다 nextInt(2)+0.5 감소. */
    interface BlockSink {
        void set(int x, int y, int z, int block);
    }

    static void endIsland(BlockSink sink, DecorationRandom random, int x, int y, int z) {
        double radius = random.nextInt(3) + 4;
        for (int layer = 0; radius > 0.5; layer--) {
            int low = (int) Math.floor(-radius);
            int high = (int) Math.ceil(radius);
            for (int dx = low; dx <= high; dx++) {
                for (int dz = low; dz <= high; dz++) {
                    if (dx * dx + dz * dz <= (radius + 1) * (radius + 1)) sink.set(x + dx, y + layer, z + dz, Blocks.END_STONE);
                }
            }
            radius -= random.nextInt(2) + 0.5;
        }
    }

    /** 바닐라 {@code EndGatewayFeature.place}: ±1·±2·±1 상자, 가운데 관문, 같은 층 공기, 십자 기반암. */
    private static void writeGateway(CellSink writer, int cx, int cy, int cz, int state) {
        for (int x = cx - 1; x <= cx + 1; x++) {
            for (int y = cy - 2; y <= cy + 2; y++) {
                for (int z = cz - 1; z <= cz + 1; z++) {
                    boolean bx = x == cx;
                    boolean by = y == cy;
                    boolean bz = z == cz;
                    boolean edge = Math.abs(y - cy) == 2;
                    if (bx && by && bz) writer.set(x, y, z, Blocks.END_GATEWAY, state);
                    else if (by) writer.set(x, y, z, Blocks.AIR, 0);
                    else if (edge && bx && bz) writer.set(x, y, z, Blocks.BEDROCK, 0);
                    else if ((bx || bz) && !edge) writer.set(x, y, z, Blocks.BEDROCK, 0);
                    else writer.set(x, y, z, Blocks.AIR, 0);
                }
            }
        }
    }

    private static void writeSpike(ChunkWriter writer, Spike spike) {
        int r = spike.radius();
        int x0 = Math.max(spike.centerX() - r, writer.originX);
        int x1 = Math.min(spike.centerX() + r, writer.originX + 15);
        int z0 = Math.max(spike.centerZ() - r, writer.originZ);
        int z1 = Math.min(spike.centerZ() + r, writer.originZ + 15);
        if (x0 > x1 || z0 > z1) return;
        writeSpike(writer, spike, x0, x1, z0, z1);
    }

    /**
     * [DRAGON] {@code EndSpikeFeature.placeSpike} 의 블록 전부(부활 연출 SUMMONING_PILLARS 가 가시 기둥을 다시 세운다).
     * 수정 개체는 호출자가 {@code (centerX + 0.5, height + 1, centerZ + 0.5)} 에 둔다.
     */
    public static void spikeCells(Spike spike, CellSink sink) {
        int r = spike.radius();
        writeSpike(sink, spike, spike.centerX() - r, spike.centerX() + r, spike.centerZ() - r, spike.centerZ() + r);
    }

    private static void writeSpike(CellSink writer, Spike spike, int x0, int x1, int z0, int z1) {
        int r = spike.radius();
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                int dx = x - spike.centerX();
                int dz = z - spike.centerZ();
                boolean inside = dx * dx + dz * dz <= r * r + 1;
                for (int y = 0; y <= spike.height() + 10; y++) {
                    if (inside && y < spike.height()) writer.set(x, y, z, Blocks.OBSIDIAN, 0);
                    else if (y > 65) writer.set(x, y, z, Blocks.AIR, 0);
                }
            }
        }
        if (spike.guarded()) {
            for (int j = -2; j <= 2; j++) {
                for (int k = -2; k <= 2; k++) {
                    for (int l = 0; l <= 3; l++) {
                        boolean edgeX = Math.abs(j) == 2;
                        boolean edgeZ = Math.abs(k) == 2;
                        boolean top = l == 3;
                        if (!edgeX && !edgeZ && !top) continue;
                        boolean alongZ = j == -2 || j == 2 || top;
                        boolean alongX = k == -2 || k == 2 || top;
                        int state = 0;
                        if (alongZ && k != -2) state |= 1;
                        if (alongX && j != 2) state |= 2;
                        if (alongZ && k != 2) state |= 4;
                        if (alongX && j != -2) state |= 8;
                        writer.set(spike.centerX() + j, spike.height() + l, spike.centerZ() + k,
                                Blocks.IRON_BARS, state);
                    }
                }
            }
        }
        writer.set(spike.centerX(), spike.height(), spike.centerZ(), Blocks.BEDROCK, 0);
    }

    private static void writePodium(ChunkWriter writer, int podiumY) {
        if (writer.originX > 4 || writer.originX + 15 < -4 || writer.originZ > 4 || writer.originZ + 15 < -4) return;
        podiumCells(podiumY, false, writer);
    }

    /**
     * [DRAGON] {@code EndPodiumFeature.place(origin = (0, podiumY, 0))}: 반지름 2.5 안은 아래 기반암, 3.5 안은 아래
     * 엔드 돌 · 위 공기 · 포털 층 테두리 기반암, 포털 층 안쪽은 {@code active} 이면 END_PORTAL 아니면 공기.
     * 가운데 기반암 기둥 4칸과 벽 횃불 넷. 바닐라처럼 생성은 비활성 포털이고({@code EnderDragonFight.scanState} 의
     * {@code spawnExitPortal(false)}), 드래곤을 처치하면 {@code spawnExitPortal(true)} 가 같은 칸을 다시 쓴다.
     */
    public static void podiumCells(int podiumY, boolean active, CellSink sink) {
        for (int x = -4; x <= 4; x++) {
            for (int y = podiumY - 1; y <= podiumY + 32; y++) {
                for (int z = -4; z <= 4; z++) {
                    int dy = y - podiumY;
                    double distance = x * x + dy * dy + z * z;
                    boolean inside = distance < 6.25;
                    if (!inside && !(distance < 12.25)) continue;
                    if (y < podiumY) sink.set(x, y, z, inside ? Blocks.BEDROCK : Blocks.END_STONE, 0);
                    else if (y > podiumY) sink.set(x, y, z, Blocks.AIR, 0);
                    else if (!inside) sink.set(x, y, z, Blocks.BEDROCK, 0);
                    else sink.set(x, y, z, active ? Blocks.END_PORTAL : Blocks.AIR, 0);
                }
            }
        }
        for (int i = 0; i < 4; i++) sink.set(0, podiumY + i, 0, Blocks.BEDROCK, 0);
        // 바닐라 WallTorchBlock.FACING=dir 은 기둥 반대편 벽에 붙는다: N→벽 +Z, E→벽 −X, S→벽 −Z, W→벽 +X.
        sink.set(0, podiumY + 2, -1, Blocks.WALL_TORCH_S, 0);
        sink.set(1, podiumY + 2, 0, Blocks.WALL_TORCH_W, 0);
        sink.set(0, podiumY + 2, 1, Blocks.WALL_TORCH_N, 0);
        sink.set(-1, podiumY + 2, 0, Blocks.WALL_TORCH_E, 0);
    }

    private static void writePlatform(ChunkWriter writer) {
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                writer.set(PLATFORM_X + dx, PLATFORM_Y, PLATFORM_Z + dz, Blocks.OBSIDIAN);
                for (int dy = 1; dy <= 3; dy++) writer.set(PLATFORM_X + dx, PLATFORM_Y + dy, PLATFORM_Z + dz, Blocks.AIR);
            }
        }
    }

    private static void writeTreasureTower(ChunkWriter writer, TreasureSite site) {
        int cx = site.centerX();
        int cz = site.centerZ();
        int base = site.baseY();
        if (writer.originX > cx + 4 || writer.originX + 15 < cx - 4
                || writer.originZ > cz + 4 || writer.originZ + 15 < cz - 4) {
            return;
        }
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) writer.set(cx + dx, base - 1, cz + dz, Blocks.END_STONE_BRICKS);
        }
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                boolean ring = Math.abs(dx) == 3 || Math.abs(dz) == 3;
                boolean corner = Math.abs(dx) == 3 && Math.abs(dz) == 3;
                for (int y = base; y <= base + 18; y++) {
                    int level = y - base;
                    if (ring) writer.set(cx + dx, y, cz + dz, corner ? Blocks.PURPUR_PILLAR : Blocks.PURPUR_BLOCK, 0);
                    else if (level == 4 || level == 9 || level == 14) writer.set(cx + dx, y, cz + dz, Blocks.PURPUR_BLOCK);
                    else writer.set(cx + dx, y, cz + dz, Blocks.AIR);
                }
                writer.set(cx + dx, base + 19, cz + dz, Blocks.PURPUR_BLOCK);
            }
        }
        writer.set(cx, base, cz + 3, Blocks.AIR);
        writer.set(cx, base + 1, cz + 3, Blocks.AIR);
        writer.set(cx - 1, base, cz + 4, Blocks.END_STONE_BRICK_WALL);
        writer.set(cx + 1, base, cz + 4, Blocks.END_STONE_BRICK_WALL);
        writer.set(cx - 1, base + 1, cz + 4, Blocks.END_ROD, END_ROD_FACING_UP);
        writer.set(cx + 1, base + 1, cz + 4, Blocks.END_ROD, END_ROD_FACING_UP);
        for (int level : new int[] {6, 7, 11, 12}) {
            writer.set(cx, base + level, cz - 3, Blocks.MAGENTA_STAINED_GLASS);
            writer.set(cx - 3, base + level, cz, Blocks.MAGENTA_STAINED_GLASS);
        }
        for (int level : new int[] {16, 17}) {
            writer.set(cx, base + level, cz - 3, Blocks.MAGENTA_STAINED_GLASS);
            writer.set(cx - 3, base + level, cz, Blocks.MAGENTA_STAINED_GLASS);
            writer.set(cx, base + level, cz + 3, Blocks.MAGENTA_STAINED_GLASS);
            writer.set(cx + 3, base + level, cz - 1, Blocks.MAGENTA_STAINED_GLASS);
        }
        for (int level = 0; level <= 14; level++) writer.set(cx + 2, base + level, cz, Blocks.LADDER);
        for (int level : new int[] {3, 8, 13, 18}) {
            writer.set(cx - 2, base + level, cz - 2, Blocks.END_ROD, END_ROD_FACING_DOWN);
            writer.set(cx - 2, base + level, cz + 2, Blocks.END_ROD, END_ROD_FACING_DOWN);
        }
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                boolean outer = Math.abs(dx) == 4 || Math.abs(dz) == 4;
                boolean outerCorner = Math.abs(dx) == 4 && Math.abs(dz) == 4;
                if (outer) writer.set(cx + dx, base + 9, cz + dz, Blocks.PURPUR_SLAB, 1);
                writer.set(cx + dx, base + 20, cz + dz, Blocks.PURPUR_BLOCK);
                if (outer) {
                    writer.set(cx + dx, base + 21, cz + dz, outerCorner ? Blocks.END_ROD : Blocks.PURPUR_SLAB, 0);
                } else {
                    writer.set(cx + dx, base + 21, cz + dz, Blocks.AIR);
                }
                if (outerCorner) {
                    writer.set(cx + dx, base, cz + dz, Blocks.END_STONE_BRICK_WALL);
                    writer.set(cx + dx, base + 1, cz + dz, Blocks.END_ROD, END_ROD_FACING_UP);
                }
            }
        }
        for (int level = 21; level <= 24; level++) writer.set(cx, base + level, cz, Blocks.PURPUR_PILLAR, 0);
        writer.set(cx, base + 25, cz, Blocks.END_ROD, END_ROD_FACING_UP);
        writer.set(site.chestX(), site.chestY(), site.chestZ(), Blocks.CHEST, site.chestState());
    }

    // ── 엔드 관문 ──
    /**
     * [DRAGON] {@code EnderDragonFight.init} 의 관문 순서: 0..19 를 {@code Util.shuffle(list,
     * RandomSource.createThreadLocalInstance(seed))}(LegacyRandom, i = size..2 에서 nextInt(i) 와 i-1 교환)로 섞는다.
     * 처치마다 {@code spawnNewGateway} 가 목록의 <b>마지막</b> 원소를 꺼낸다.
     */
    public static int[] gatewayOrder(int seed) {
        int[] order = new int[GATEWAY_RING.length];
        for (int i = 0; i < order.length; i++) order[i] = i;
        Random48 random = new Random48(seed);
        for (int i = order.length; i > 1; i--) {
            int j = random.nextInt(i);
            int swap = order[i - 1];
            order[i - 1] = order[j];
            order[j] = swap;
        }
        return order;
    }

    /** [DRAGON] 고리 자리 {@code i} 의 관문 좌표(y 75). */
    public static int[] gatewayRing(int index) {
        int[] ring = GATEWAY_RING[index];
        return new int[] {ring[0], GATEWAY_Y, ring[1]};
    }

    /** 첫 처치 관문 자리: 20칸 고리를 LegacyRandom(seed) 로 셔플한 목록의 마지막 원소. */
    public static int[] ringGateway(int seed) {
        int[] order = gatewayOrder(seed);
        return gatewayRing(order[order.length - 1]);
    }

    /** 한 청크의 블록/state 평면. 반환 배열은 호출자 소유다. */
    public static Planes generate(int seed, int chunkX, int chunkZ) {
        if (outsideLimit(chunkX, chunkZ)) {
            return new Planes(new short[Blocks.CHUNK_BLOCKS], new byte[Blocks.CHUNK_BLOCKS]);
        }
        int originX = chunkX * 16;
        int originZ = chunkZ * 16;
        ChunkWriter writer = new ChunkWriter(originX, originZ);
        VoidEndTerrain terrain = VoidEndTerrain.forSeed(seed);
        VoidEndTerrain.Sampler sampler = terrain.sampler();
        TreasureSite site = treasureSite(seed);
        // 1) 바닐라 지형 + 보물 섬.
        for (int lz = 0; lz < 16; lz++) {
            for (int lx = 0; lx < 16; lx++) {
                int x = originX + lx;
                int z = originZ + lz;
                int[] runs = sampler.runs(x, z);
                for (int i = 0; i < runs.length; i += 2) writer.fillColumn(lx, lz, new Column(runs[i], runs[i + 1]), Blocks.END_STONE);
                writer.fillColumn(lx, lz, treasureColumn(site, x, z), Blocks.END_STONE);
            }
        }
        // 2) 바깥 지역 장식: 3×3 이웃과 이 청크를 장식 위상 순서(decorationPhase)로 차례로 쓴다. 청크마다
        //    RAW_GENERATION(작은 섬) → SURFACE_STRUCTURES(엔드 도시 조각 — 자기 청크 구역 안만, VoidEndCity.writeChunk
        //    — 그다음 귀환 관문) → VEGETAL(후렴 나무).
        for (int p = 0; p < 4; p++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int dx = -1; dx <= 1; dx++) {
                    if (decorationPhase(chunkX + dx, chunkZ + dz) != p) continue;
                    Decoration decoration = decoration(seed, chunkX + dx, chunkZ + dz, sampler);
                    writer.write(decoration.islands);
                    if (dx == 0 && dz == 0) VoidEndCity.writeChunk(seed, chunkX, chunkZ, writer::set);
                    writer.write(decoration.gateways);
                    writer.write(decoration.trees);
                }
            }
        }
        // 3) 본섬 구조물: 가시 기둥, 비활성 귀환 포털, 도착 발판, 보물 탑. 고리 관문은 [DRAGON] 드래곤 처치가 세운다.
        if (originX <= 60 && originX + 15 >= -60 && originZ <= 60 && originZ + 15 >= -60) {
            for (Spike spike : spikes(seed)) writeSpike(writer, spike);
            writePodium(writer, podiumY(seed));
        }
        writePlatform(writer);
        writeTreasureTower(writer, site);
        return new Planes(writer.blocks, writer.states);
    }

    /** 생성 결과 평면(사본 소유는 {@code DimensionChunk} 가 한다). */
    public record Planes(short[] blocks, byte[] states) {
    }
}
