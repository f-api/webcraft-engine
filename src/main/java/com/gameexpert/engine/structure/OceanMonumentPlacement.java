package com.gameexpert.engine.structure;

/**
 * [MONUMENT] 해저 신전 배치 — 바닐라 1.21.4 원본 로직·원본 확률을 그대로 옮긴 것.
 *
 * <p>수치 근거(고정 태그 {@code 1.21.4-data-json}, {@code docs/research/mc-vanilla-1214/SOURCE.md}
 * 와 같은 misode/mcmeta 핀):
 *
 * <pre>
 * data/minecraft/worldgen/structure_set/ocean_monuments.json
 * { "placement": { "type":"minecraft:random_spread", "salt":10387313,
 *                  "separation":5, "spacing":32, "spread_type":"triangular" },
 *   "structures":[{"structure":"minecraft:monument","weight":1}] }
 *
 * data/minecraft/worldgen/structure/monument.json
 * { "type":"minecraft:ocean_monument", "biomes":"#minecraft:has_structure/ocean_monument",
 *   "step":"surface_structures",
 *   "spawn_overrides":{ "monster":{ "bounding_box":"full",
 *       "spawns":[{"type":"minecraft:guardian","minCount":2,"maxCount":4,"weight":1}] },
 *     "axolotls":{...,"spawns":[]}, "underground_water_creature":{...,"spawns":[]} } }
 *
 * tags/worldgen/biome/has_structure/ocean_monument.json      = ["#minecraft:is_deep_ocean"]
 * tags/worldgen/biome/required_ocean_monument_surrounding.json = ["#minecraft:is_ocean","#minecraft:is_river"]
 * tags/worldgen/biome/is_deep_ocean.json = deep_frozen_ocean, deep_cold_ocean, deep_ocean, deep_lukewarm_ocean
 * tags/worldgen/biome/is_ocean.json      = #is_deep_ocean + frozen_ocean, ocean, cold_ocean, lukewarm_ocean, warm_ocean
 * tags/worldgen/biome/is_river.json      = river, frozen_river
 * </pre>
 *
 * <p>난수원은 바닐라 {@code LegacyRandomSource} 다. JDK 기본 난수(`Random`)와 완전히 같은
 * LCG·같은 {@code nextInt} 이지만, 이 패키지는 런타임 RNG 클래스 사용을 금지하는 계약
 * ({@code StructureGenerationBaseTest.structureSourcesHaveNoRuntimeRngOrForbiddenMath})이라
 * 아래 {@link LegacyRandom} 으로 직접 구현한다. 두 구현의 동치는 테스트가
 * JDK 기본 난수와 대조해 못 박는다(테스트 쪽만 그 클래스를 import 한다).
 *
 * <p>격자 결정은 {@code RandomSpreadStructurePlacement#getPotentialFeatureChunk} 와 동일하다 —
 * {@code WorldgenRandom(LegacyRandomSource)} 에 {@code setLargeFeatureWithSalt(seed, gridX, gridZ, salt)}
 * 를 걸고 {@code TRIANGULAR} 확산으로 {@code (nextInt(27)+nextInt(27))/2} 를 두 번 뽑는다.
 *
 * <p>이 저장소의 seed 는 int 다. 바닐라 {@code levelSeed} 자리에 {@code (long) seed} 를 넣는다 —
 * 시드 공간만 좁고 계산은 동일하다.
 */
public final class OceanMonumentPlacement {

    /** {@code ocean_monuments.json} placement.spacing (청크). */
    public static final int SPACING = 32;
    /** {@code ocean_monuments.json} placement.separation (청크). */
    public static final int SEPARATION = 5;
    /** {@code ocean_monuments.json} placement.salt. */
    public static final int SALT = 10_387_313;
    /** {@code spread_type: "triangular"} — 두 번 뽑아 평균한다. */
    public static final boolean TRIANGULAR_SPREAD = true;
    /** 격자 한 칸의 블록 크기. 32청크 × 16블록. */
    public static final int CELL_SIZE = SPACING * 16;

    /** 바닐라 신전 본체는 {@code y=39} 에서 시작하는 58 × 23 × 58 상자다. */
    public static final int BASE_Y = 39;
    public static final int SIZE_XZ = 58;
    public static final int SIZE_Y = 23;
    /** 본체 최소 모서리는 신전 청크 시작 블록에서 {@code -29} 다. */
    public static final int BASE_OFFSET = -29;
    /**
     * 바닐라 spawn_overrides 의 몬스터 표: guardian 하나뿐이라 weight 1 은 선택에 영향이 없고
     * 무리 크기만 의미가 있다 — minCount 2, maxCount 4.
     */
    public static final int GUARDIAN_MIN_COUNT = 2;
    public static final int GUARDIAN_MAX_COUNT = 4;

    private OceanMonumentPlacement() {}

    /**
     * {@code WorldgenRandom#setLargeFeatureWithSalt} 와 동일한 시드 유도.
     * 바닐라: {@code x*341873128712L + z*132897987541L + levelSeed + salt}.
     */
    public static long largeFeatureSaltSeed(long levelSeed, int gridX, int gridZ, int salt) {
        return gridX * 341_873_128_712L + gridZ * 132_897_987_541L + levelSeed + salt;
    }

    /** {@code WorldgenRandom#setLargeFeatureSeed} — 구조물 조각 난수의 시드. */
    public static long largeFeatureSeed(long levelSeed, int chunkX, int chunkZ) {
        LegacyRandom random = new LegacyRandom(levelSeed);
        long a = random.nextLong();
        long b = random.nextLong();
        return chunkX * a ^ chunkZ * b ^ levelSeed;
    }

    /** 격자 칸 하나가 지목하는 후보 청크 X. */
    public static int featureChunkX(long levelSeed, int gridX, int gridZ) {
        return gridX * SPACING + spreadOffsets(levelSeed, gridX, gridZ)[0];
    }

    /** 격자 칸 하나가 지목하는 후보 청크 Z. */
    public static int featureChunkZ(long levelSeed, int gridX, int gridZ) {
        return gridZ * SPACING + spreadOffsets(levelSeed, gridX, gridZ)[1];
    }

    /**
     * {@code getPotentialFeatureChunk} 의 두 오프셋. 삼각 확산이므로 각 축마다
     * {@code nextInt(spacing - separation)} 을 두 번 뽑아 평균한다.
     */
    public static int[] spreadOffsets(long levelSeed, int gridX, int gridZ) {
        LegacyRandom random = new LegacyRandom(largeFeatureSaltSeed(levelSeed, gridX, gridZ, SALT));
        int range = SPACING - SEPARATION;
        int offsetX = (random.nextInt(range) + random.nextInt(range)) / 2;
        int offsetZ = (random.nextInt(range) + random.nextInt(range)) / 2;
        return new int[] {offsetX, offsetZ};
    }

    /** {@code RandomSpreadStructurePlacement#isPlacementChunk} 와 같은 판정. */
    public static boolean isMonumentChunk(long levelSeed, int chunkX, int chunkZ) {
        int gridX = Math.floorDiv(chunkX, SPACING);
        int gridZ = Math.floorDiv(chunkZ, SPACING);
        return featureChunkX(levelSeed, gridX, gridZ) == chunkX
                && featureChunkZ(levelSeed, gridX, gridZ) == chunkZ;
    }

    /**
     * {@code Direction.Plane.HORIZONTAL.getRandomDirection(context.random())} 과 같은 방위 추첨.
     * 네 방위 균등 — 반환값은 이 저장소 생성기의 direction 인덱스(0..3)로 그대로 쓴다.
     */
    public static int orientation(long levelSeed, int chunkX, int chunkZ) {
        return new LegacyRandom(largeFeatureSeed(levelSeed, chunkX, chunkZ)).nextInt(4);
    }

    /**
     * {@code #minecraft:is_deep_ocean} — {@code has_structure/ocean_monument} 가 이 태그 하나다.
     * raw ID 는 {@code McBiomeRegistry.pathForId} 표를 따른다.
     */
    public static boolean isDeepOcean(int biome) {
        return biome == 24    // deep_ocean
                || biome == 48 // deep_lukewarm_ocean
                || biome == 49 // deep_cold_ocean
                || biome == 50; // deep_frozen_ocean
    }

    /** {@code #minecraft:is_ocean} = {@code #is_deep_ocean} + 얕은 바다 5종. */
    public static boolean isOcean(int biome) {
        return isDeepOcean(biome)
                || biome == 0   // ocean
                || biome == 10  // frozen_ocean
                || biome == 44  // warm_ocean
                || biome == 45  // lukewarm_ocean
                || biome == 46; // cold_ocean
    }

    /** {@code #minecraft:is_river}. */
    public static boolean isRiver(int biome) {
        return biome == 7 || biome == 11;
    }

    /** {@code #minecraft:required_ocean_monument_surrounding} = is_ocean ∪ is_river. */
    public static boolean isRequiredSurrounding(int biome) {
        return isOcean(biome) || isRiver(biome);
    }

    /**
     * 바닐라 {@code LegacyRandomSource} 의 LCG(= JDK 기본 난수와 같은 상수).
     * 상수·비트폭·{@code nextInt} 재추첨까지 원본과 같다.
     */
    public static final class LegacyRandom {
        private static final long MULTIPLIER = 0x5DEECE66DL;
        private static final long ADDEND = 0xBL;
        private static final long MASK = (1L << 48) - 1;

        private long seed;

        public LegacyRandom(long seed) {
            this.seed = (seed ^ MULTIPLIER) & MASK;
        }

        private int next(int bits) {
            seed = (seed * MULTIPLIER + ADDEND) & MASK;
            return (int) (seed >>> (48 - bits));
        }

        public int nextInt(int bound) {
            if (bound <= 0) throw new IllegalArgumentException("bound must be positive");
            if ((bound & -bound) == bound) {
                return (int) ((bound * (long) next(31)) >> 31);
            }
            int bits;
            int value;
            do {
                bits = next(31);
                value = bits % bound;
            } while (bits - value + (bound - 1) < 0);
            return value;
        }

        public long nextLong() {
            return ((long) next(32) << 32) + next(32);
        }

        public double nextDouble() {
            return ((long) next(26) << 27 | next(27)) * 0x1.0p-53;
        }

        public boolean nextBoolean() {
            return next(1) != 0;
        }
    }
}
