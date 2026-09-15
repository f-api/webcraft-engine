package com.gameexpert.world.dimension.voidend;

import com.gameexpert.terrain.mc.McRandom;
import com.gameexpert.terrain.mc.surface.McBiomeZoom;
import java.util.concurrent.ConcurrentHashMap;

/**
 * [VOID-END] 바닐라 End 장식({@code ChunkGenerator.applyBiomeDecoration})의 난수·바이옴 규칙. 정적판
 * {@code client/src/world/dimensions/voidEnd/VoidEndDecoration.ts} 와 같은 값을 낸다(두 파일은 줄 단위 사본).
 *
 * <p>근거(핀 26.3 client jar, javap):
 * <ul>
 * <li>{@code ChunkGenerator.applyBiomeDecoration}: {@code new WorldgenRandom(new XoroshiroRandomSource(…))},
 * {@code setDecorationSeed(level.getSeed(), 청크 최소 x, 최소 z)}, 단계마다 구조물 목록에
 * {@code setFeatureSeed(decorationSeed, 구조물 순번, 단계)}, 장식마다 {@code setFeatureSeed(decorationSeed, 장식 순번,
 * 단계)} 뒤 {@code FeaturePlacer.placeWithBiomeCheck}. 돌릴 장식은 {@code ChunkPos.rangeClosed(청크, 1)} 3×3 청크
 * 구역에 저장된 바이옴들의 목록 합집합이다(End 는 청크마다 한 바이옴).</li>
 * <li>{@code WorldgenRandom.next(bits)} = {@code (int)(xoroshiro.nextLong() >>> 64 − bits)}, 나머지는
 * {@code BitRandomSource} 기본 구현(nextInt(bound) 는 {@code java.util.Random} 알고리즘, nextLong =
 * next(32)&lt;&lt;32 + next(32), nextFloat = next(24)·2^-24). {@code setDecorationSeed}:
 * setSeed(s); a = nextLong()|1; b = nextLong()|1; setSeed(x·a + z·b ^ s). {@code setFeatureSeed}:
 * setSeed(d + index + 10000·step). setSeed 는 {@code XoroshiroRandomSource.setSeed}
 * ({@code RandomSupport.upgradeSeedTo128bit}) — {@link McRandom#McRandom(long)} 과 같다.</li>
 * <li>{@code FeatureSorter} 결과(핀 jar 로 {@code NoiseBasedChunkGenerator(TheEndBiomeSource, END)} 의
 * {@code featuresPerStep} 를 직접 읽은 값): step 0 [end_island_decorated], step 4 [end_gateway_return, end_spike],
 * step 9 [chorus_plant], step 10 [end_platform].</li>
 * <li>{@code BiomeFilter}: {@code level.getBiome(pos)} = {@code BiomeManager.getBiome}(SHA-256 난독 시드의 fuzzy
 * zoom, {@link McBiomeZoom}) 가 고른 quart 의 저장 바이옴 = {@code TheEndBiomeSource} 의 그 quart 청크
 * 바이옴.</li>
 * </ul>
 * WebCraft 시드는 signed int 이고 바닐라 월드 시드는 그 부호 확장 long 이다({@link VoidEndTerrain} 와 같다).
 */
public final class VoidEndDecoration {
    public static final int STEP_RAW_GENERATION = 0;
    public static final int STEP_SURFACE_STRUCTURES = 4;
    public static final int STEP_VEGETAL_DECORATION = 9;
    /** featuresPerStep[0] 의 end_island_decorated 순번. */
    public static final int INDEX_END_ISLAND_DECORATED = 0;
    /** featuresPerStep[4] 의 end_gateway_return 순번(end_spike 는 1). */
    public static final int INDEX_END_GATEWAY_RETURN = 0;
    /** featuresPerStep[9] 의 chorus_plant 순번. */
    public static final int INDEX_CHORUS_PLANT = 0;

    private VoidEndDecoration() {
    }

    /** 바닐라 {@code WorldgenRandom(XoroshiroRandomSource)}. */
    public static final class WorldgenRandom implements VoidEndGenerator.DecorationRandom {
        private McRandom source;

        public WorldgenRandom(long seed) {
            setSeed(seed);
        }

        public void setSeed(long seed) {
            source = new McRandom(seed);
        }

        public int next(int bits) {
            return (int) (source.nextLong() >>> 64 - bits);
        }

        @Override
        public int nextInt() {
            return next(32);
        }

        @Override
        public int nextInt(int bound) {
            if (bound <= 0) throw new IllegalArgumentException("Bound must be positive");
            if ((bound & bound - 1) == 0) return (int) ((long) bound * next(31) >> 31);
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

        @Override
        public boolean nextBoolean() {
            return next(1) != 0;
        }

        @Override
        public float nextFloat() {
            return next(24) * 5.9604645E-8F;
        }

        /** {@code WorldgenRandom.setDecorationSeed}. */
        public long setDecorationSeed(long worldSeed, int x, int z) {
            setSeed(worldSeed);
            long a = nextLong() | 1L;
            long b = nextLong() | 1L;
            long seed = (long) x * a + (long) z * b ^ worldSeed;
            setSeed(seed);
            return seed;
        }

        /** {@code WorldgenRandom.setFeatureSeed}. */
        public void setFeatureSeed(long decorationSeed, int index, int step) {
            setSeed(decorationSeed + index + 10000 * step);
        }
    }

    /** 한 청크의 장식 시드(청크 최소 블록 좌표). */
    public static long decorationSeed(int seed, int chunkX, int chunkZ) {
        return new WorldgenRandom(0L).setDecorationSeed(seed, chunkX * 16, chunkZ * 16);
    }

    /** 이 청크·단계·순번의 장식 난수. */
    public static WorldgenRandom featureRandom(long decorationSeed, int index, int step) {
        WorldgenRandom random = new WorldgenRandom(0L);
        random.setFeatureSeed(decorationSeed, index, step);
        return random;
    }

    // ── 바이옴 ──
    private record ZoomSeed(int seed, long value) {
    }

    private static volatile ZoomSeed zoom = new ZoomSeed(0, McBiomeZoom.zoomSeed(0L));
    /** 시드별 청크 바이옴 캐시(여러 월드가 동시에 돌 수 있어 시드를 키에 둔다). */
    private static final ConcurrentHashMap<Integer, ConcurrentHashMap<Long, Integer>> BIOMES = new ConcurrentHashMap<>();
    private static final int BIOME_CACHE_LIMIT = 1 << 16;

    /** {@code BiomeManager.obfuscateSeed(seed)}. */
    public static long zoomSeed(int seed) {
        ZoomSeed current = zoom;
        if (current.seed() == seed) return current.value();
        ZoomSeed created = new ZoomSeed(seed, McBiomeZoom.zoomSeed(seed));
        zoom = created;
        return created.value();
    }

    /** 청크 바이옴(캐시). 순수 함수 {@link VoidEndTerrain#biome} 와 같은 값이다. */
    public static int chunkBiome(int seed, int chunkX, int chunkZ) {
        ConcurrentHashMap<Long, Integer> biomes = BIOMES.get(seed);
        if (biomes == null) {
            if (BIOMES.size() >= 8) BIOMES.clear();
            biomes = BIOMES.computeIfAbsent(seed, ignored -> new ConcurrentHashMap<>());
        }
        long key = (long) chunkX << 32 ^ chunkZ & 0xffffffffL;
        Integer cached = biomes.get(key);
        if (cached != null) return cached;
        int biome = VoidEndTerrain.forSeed(seed).biome(chunkX, chunkZ);
        if (biomes.size() > BIOME_CACHE_LIMIT) biomes.clear();
        biomes.put(key, biome);
        return biome;
    }

    /** {@code level.getBiome(BlockPos)}: fuzzy zoom 이 고른 quart 의 청크 바이옴. */
    public static int biomeAt(int seed, int x, int y, int z) {
        int corner = McBiomeZoom.corner(zoomSeed(seed), x, y, z);
        int quartX = (x - 2 >> 2) + ((corner & 4) == 0 ? 0 : 1);
        int quartZ = (z - 2 >> 2) + ((corner & 1) == 0 ? 0 : 1);
        return chunkBiome(seed, quartX >> 2, quartZ >> 2);
    }

    /** zoom 이 고른 quart 좌표 [qx, qy, qz](시험용). */
    public static int[] zoomQuart(int seed, int x, int y, int z) {
        int corner = McBiomeZoom.corner(zoomSeed(seed), x, y, z);
        return new int[] {(x - 2 >> 2) + ((corner & 4) == 0 ? 0 : 1), (y - 2 >> 2) + ((corner & 2) == 0 ? 0 : 1),
            (z - 2 >> 2) + ((corner & 1) == 0 ? 0 : 1)};
    }

    /** 3×3 청크 구역 바이옴 합집합 비트(1 &lt;&lt; biome). */
    public static int biomeSet(int seed, int chunkX, int chunkZ) {
        int set = 0;
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) set |= 1 << chunkBiome(seed, chunkX + dx, chunkZ + dz);
        }
        return set;
    }
}
