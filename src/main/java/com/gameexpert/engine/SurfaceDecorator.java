package com.gameexpert.engine;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Predicate;

import com.gameexpert.common.LongOpenHashMap;
import com.gameexpert.terrain.Blocks;
import com.gameexpert.terrain.ChunkGenerator;
import com.gameexpert.terrain.mc.biome.McClimateSampler;
import com.gameexpert.terrain.mc.biome.McBiomeRegistry;
import com.gameexpert.terrain.mc.surface.McFreezeTopLayer;
import com.gameexpert.terrain.mc.surface.McSurfaceRuleEngine;

/**
 * 생성기 결과를 바꾸지 않는 결정론적 표면 장식기. 좌표만 계산하며 오버레이/방송은 WorldRuntime이 담당한다.
 *
 * <p>이 장식기는 이제 프로젝트 고유 자연물(내륙 pond, Rafflesia)만 계획한다. 바닐라 표면/동굴/광물
 * lane은 모두 26.3 FEATURES 산출물(Mc263FeaturesPrefixStage 및 Mc263FeaturesStep3~10Stage)이 소유한다.</p>
 */
public final class SurfaceDecorator {

    public static final int UNKNOWN_SURFACE_HEIGHT = Integer.MIN_VALUE;
    public static final int UNKNOWN_NOISE_BIOME = Integer.MIN_VALUE;
    static final int POND_SITE_SIZE = 128;
    static final int POND_SITE_MARGIN = 12;
    static final int POND_RARITY = 1;
    static final int POND_MIN_RADIUS = 4;
    static final int POND_MAX_RADIUS = 7;
    /**
     * Adjacent chunk activations and snapshot composition ask for the same pure terrain columns repeatedly.
     * Keeping one bounded world-local cache avoids re-running the density/climate sampler without retaining
     * column metadata for the entire explored world.
     */
    private static final int COLUMN_CACHE_LIMIT = 32_768;

    // debugColumn이 반환하는 vanilla numeric biome ID. 판정값 자체는 debugColumn에서 조회한다.
    static final int SWAMP = 6;
    static final int JUNGLE = 21;
    static final int BAMBOO_JUNGLE = 168;
    // McBiomeRegistry's pinned btree21wd mapping is the local source for these two raw IDs.
    static final int MANGROVE_SWAMP = 184;
    static final int RIVER = 7;
    static final int FROZEN_RIVER = 11;
    static final int BEACH = 16;
    static final int SNOWY_BEACH = 26;
    static final int DEEP_DARK = 183;
    static final int MUSHROOM_FIELDS = 14;
    static final int SNOWY_SLOPES = 179;
    static final int JAGGED_PEAKS = 180;
    static final int FROZEN_PEAKS = 181;

    @FunctionalInterface
    public interface BlockView {
        int getBlock(int x, int y, int z);

        /**
         * Base generated terrain's highest solid cell, excluding runtime decoration and fluids.
         * Production snapshot views provide this from a cached chunk heightmap; lightweight test
         * views may leave it unknown and use the authoritative density fallback.
         */
        default int terrainSurfaceHeight(int x, int z) {
            return UNKNOWN_SURFACE_HEIGHT;
        }

        /**
         * Vanilla noise biome at a block coordinate, i.e. the raw {@code btree21wd} ID that
         * {@code McClimateSampler.biomeAtBlock} emits. This is the biome oracle a structure's
         * placement gate reads at its anchor, matching vanilla {@code Structure#generate}, which
         * samples the noise biome once at the start position. It is deliberately not the fuzzy
         * mob-spawn lookup.
         *
         * <p>Every production planning view resolves this from the world seed. Lightweight test
         * views may leave it unknown; a gate that reads it must then fail open so a fixture view
         * keeps its previous placements.</p>
         */
        default int noiseBiomeAt(int x, int y, int z) {
            return UNKNOWN_NOISE_BIOME;
        }

        /**
         * Natural features spanning a chunk boundary must not recreate a source or basin through
         * a player-authored cell that belongs to a neighboring target chunk.
         */
        default boolean isProtectedEdit(int x, int y, int z) {
            return false;
        }

        /** Reads terrain needed while constructing a boundary-spanning tree candidate. */
        default int getTreePlanningBlock(int x, int y, int z) {
            return getBlock(x, y, z);
        }

        /**
         * Reads stable source terrain while building a source-keyed tree cache entry. Target-local
         * overlays must not affect this value or the first activated target would poison the cache.
         */
        default int getTreeSourceBlock(int x, int y, int z) {
            return getTreePlanningBlock(x, y, z);
        }

        /**
         * Complete-tree collision checks can report an unavailable block as a negative value. The
         * decorator then uses the exact generated surface column instead of materializing a chunk
         * solely to prove that a crown cell above that surface is empty.
         */
        default int getTreeValidationBlock(int x, int y, int z) {
            return getTreePlanningBlock(x, y, z);
        }

        /**
         * Optional cave features may reject a candidate when its prepared source terrain is not
         * available. This keeps their wide random searches from materializing hidden chunks while
         * tree validation can still inspect a complete cross-chunk shape.
         */
        default int getFeaturePlanningBlock(int x, int y, int z) {
            return getTreePlanningBlock(x, y, z);
        }

        /** Stable source terrain counterpart of {@link #getFeaturePlanningBlock(int, int, int)}. */
        default int getFeatureSourceBlock(int x, int y, int z) {
            return getFeaturePlanningBlock(x, y, z);
        }
    }

    /** WorldRuntime이 overlay와 장식 종류별 방송 정책에 사용하는 불변 배치 값. */
    public static final class Decoration {
        private final BlockPos pos;
        private final int blockType;
        private final int state;

        Decoration(BlockPos pos, int blockType) {
            this(pos, blockType, 0);
        }

        Decoration(BlockPos pos, int blockType, int state) {
            this.pos = pos;
            this.blockType = blockType;
            this.state = state;
        }

        public BlockPos pos() {
            return pos;
        }

        public int blockType() {
            return blockType;
        }

        public int state() {
            return state;
        }
    }

    /**
     * The vegetation pass can hold thousands of bamboo/tree cells. Keeping its position index
     * beside the ordered output avoids repeatedly scanning that list for every later patch
     * candidate while preserving insertion order and the existing Decoration carrier.
     */
    private static final class DecorationList extends ArrayList<Decoration> {
        private static final long ABSENT = -1L;
        private final LongOpenHashMap entries;

        DecorationList(int initialCapacity) {
            super(initialCapacity);
            entries = new LongOpenHashMap(Math.max(16, initialCapacity * 2));
        }

        @Override
        public boolean add(Decoration decoration) {
            entries.put(positionKey(decoration.pos()),
                    packedEntry(size(), decoration.blockType()));
            return super.add(decoration);
        }

        @Override
        public boolean addAll(Collection<? extends Decoration> decorations) {
            int index = size();
            for (Decoration decoration : decorations) {
                entries.put(positionKey(decoration.pos()),
                        packedEntry(index++, decoration.blockType()));
            }
            return super.addAll(decorations);
        }

        void put(Decoration decoration) {
            long key = positionKey(decoration.pos());
            long existing = entries.get(key, ABSENT);
            if (existing == ABSENT) {
                entries.put(key, packedEntry(size(), decoration.blockType()));
                super.add(decoration);
            } else {
                int index = (int) ((existing >>> 16) - 1);
                entries.put(key, packedEntry(index, decoration.blockType()));
                super.set(index, decoration);
            }
        }

        boolean containsPosition(BlockPos pos) {
            return entries.get(positionKey(pos), ABSENT) != ABSENT;
        }

        Integer blockAt(BlockPos pos) {
            long entry = entries.get(positionKey(pos), ABSENT);
            return entry == ABSENT ? null : (int) (entry & 0xffffL);
        }

        private static long positionKey(BlockPos pos) {
            return blockPositionKey(pos.x(), pos.y(), pos.z());
        }

        private static long packedEntry(int index, int blockType) {
            return ((long) (index + 1) << 16) | (blockType & 0xffffL);
        }
    }

    /** Immutable project-custom natural-water plan (ponds only; vanilla fluid lanes are canonical). */
    public static final class NaturalWaterPlan {
        private final List<Decoration> decorations;
        private final Set<BlockPos> claimed;
        private final LongOpenHashMap blocks;

        private NaturalWaterPlan(List<Decoration> decorations, Set<BlockPos> claimed) {
            this.decorations = List.copyOf(decorations);
            this.claimed = Set.copyOf(claimed);
            LongOpenHashMap indexed = new LongOpenHashMap(decorations.size() * 2 + 1);
            for (Decoration decoration : decorations) {
                BlockPos pos = decoration.pos();
                indexed.put(blockPositionKey(pos.x(), pos.y(), pos.z()), decoration.blockType());
            }
            this.blocks = indexed;
        }

        public List<Decoration> decorations() {
            return decorations;
        }

        public boolean claims(BlockPos pos) {
            return claimed.contains(pos);
        }

        public BlockView overlay(BlockView base) {
            return new BlockView() {
                @Override
                public int getBlock(int x, int y, int z) {
                    long block = blocks.get(blockPositionKey(x, y, z), -1);
                    return block < 0 ? base.getBlock(x, y, z) : (int) block;
                }

                @Override
                public int terrainSurfaceHeight(int x, int z) {
                    return base.terrainSurfaceHeight(x, z);
                }

                @Override
                public boolean isProtectedEdit(int x, int y, int z) {
                    return base.isProtectedEdit(x, y, z);
                }

                @Override
                public int getTreePlanningBlock(int x, int y, int z) {
                    long block = blocks.get(blockPositionKey(x, y, z), -1);
                    return block < 0 ? base.getTreePlanningBlock(x, y, z) : (int) block;
                }

                @Override
                public int getTreeSourceBlock(int x, int y, int z) {
                    return base.getTreeSourceBlock(x, y, z);
                }

                @Override
                public int getTreeValidationBlock(int x, int y, int z) {
                    long block = blocks.get(blockPositionKey(x, y, z), -1);
                    return block < 0 ? base.getTreeValidationBlock(x, y, z) : (int) block;
                }

                @Override
                public int getFeaturePlanningBlock(int x, int y, int z) {
                    long block = blocks.get(blockPositionKey(x, y, z), -1);
                    return block < 0 ? base.getFeaturePlanningBlock(x, y, z) : (int) block;
                }

                @Override
                public int getFeatureSourceBlock(int x, int y, int z) {
                    return base.getFeatureSourceBlock(x, y, z);
                }
            };
        }
    }

    private static final class PlanningColumn {
        private final int surfaceHeight;
        private final int biome;
        private final int waterLevel;

        private PlanningColumn(int surfaceHeight, int biome, int waterLevel) {
            this.surfaceHeight = surfaceHeight;
            this.biome = biome;
            this.waterLevel = waterLevel;
        }

        private static PlanningColumn fromDebug(ChunkGenerator.ColumnDebug debug) {
            return new PlanningColumn(debug.surfaceHeight, debug.biome, debug.waterLevel);
        }
    }

    private final int seed;
    private final ThreadLocal<McClimateSampler> caveBiomeSampler;
    private final ConcurrentHashMap<Long, PlanningColumn> columnCache =
            new ConcurrentHashMap<>();
    private final ConcurrentLinkedQueue<Long> columnCacheOrder = new ConcurrentLinkedQueue<>();

    public SurfaceDecorator(int seed) {
        this.seed = seed;
        this.caveBiomeSampler = ThreadLocal.withInitial(() -> new McClimateSampler(seed));
    }

    /**
     * Plans project ponds plus pinned vanilla 1.21.4 lava lakes and wall-spring sources without changing
     * {@code generateChunk()}. As in SpringFeature, sources are placed here and flow through the authority-owned
     * fluid scheduler after structures have won exact overlaps.
     */
    /**
     * Project-custom natural water. The 26.3 FEATURES product owns every vanilla fluid lane
     * ({@code minecraft:spring_water}/{@code spring_lava}/{@code spring_lava_frozen} at step 8 and
     * {@code minecraft:lake_lava_surface}/{@code lake_lava_underground} at step 1), so this overlay
     * keeps only WebCraft's own inland ponds.
     */
    public NaturalWaterPlan customPondFeatures(int chunkX, int chunkZ, BlockView world,
            Predicate<BlockPos> persistentEdit) {
        Map<BlockPos, Integer> selected = new LinkedHashMap<>();
        Set<BlockPos> claimed = new LinkedHashSet<>();
        Map<Long, PlanningColumn> columns = new HashMap<>(512);
        appendSurfacePonds(chunkX, chunkZ, world, persistentEdit, selected, claimed, columns);

        List<Decoration> decorations = new ArrayList<>(selected.size());
        selected.forEach((pos, blockType) -> decorations.add(new Decoration(pos, blockType)));
        return new NaturalWaterPlan(decorations, claimed);
    }

    private void appendSurfacePonds(int chunkX, int chunkZ, BlockView world,
            Predicate<BlockPos> persistentEdit, Map<BlockPos, Integer> selected,
            Set<BlockPos> claimed, Map<Long, PlanningColumn> columns) {
        int minX = chunkX * Blocks.CHUNK_X;
        int maxX = minX + Blocks.CHUNK_X - 1;
        int minZ = chunkZ * Blocks.CHUNK_Z;
        int maxZ = minZ + Blocks.CHUNK_Z - 1;
        int minCellX = Math.floorDiv(minX - POND_MAX_RADIUS, POND_SITE_SIZE);
        int maxCellX = Math.floorDiv(maxX + POND_MAX_RADIUS, POND_SITE_SIZE);
        int minCellZ = Math.floorDiv(minZ - POND_MAX_RADIUS, POND_SITE_SIZE);
        int maxCellZ = Math.floorDiv(maxZ + POND_MAX_RADIUS, POND_SITE_SIZE);
        for (int cellZ = minCellZ; cellZ <= maxCellZ; cellZ++) {
            for (int cellX = minCellX; cellX <= maxCellX; cellX++) {
                if (bounded(cellX, cellZ, 0, 0x790, POND_RARITY) != 0) continue;
                int centerRange = POND_SITE_SIZE - POND_SITE_MARGIN * 2;
                int centerX = cellX * POND_SITE_SIZE + POND_SITE_MARGIN
                        + bounded(cellX, cellZ, 0, 0x791, centerRange);
                int centerZ = cellZ * POND_SITE_SIZE + POND_SITE_MARGIN
                        + bounded(cellX, cellZ, 0, 0x792, centerRange);
                int radiusX = POND_MIN_RADIUS
                        + bounded(cellX, cellZ, 0, 0x793,
                                POND_MAX_RADIUS - POND_MIN_RADIUS + 1);
                int radiusZ = POND_MIN_RADIUS
                        + bounded(cellX, cellZ, 0, 0x794,
                                POND_MAX_RADIUS - POND_MIN_RADIUS + 1);
                if (centerX + radiusX < minX || centerX - radiusX > maxX
                        || centerZ + radiusZ < minZ || centerZ - radiusZ > maxZ) {
                    continue;
                }
                PlanningColumn center = debugColumn(columns, world, centerX, centerZ);
                int waterY = pondWaterLevel(cellX, cellZ, centerX, centerZ,
                        radiusX, radiusZ, world, columns);
                if (waterY < Blocks.SEA_LEVEL + 2 || waterY > Blocks.MAX_Y - 3
                        || !isPondBiome(center.biome)
                        || !isValidPondSite(cellX, cellZ, centerX, centerZ, radiusX, radiusZ,
                                waterY, world, columns)) {
                    continue;
                }
                appendPondCells(chunkX, chunkZ, cellX, cellZ, centerX, centerZ,
                        radiusX, radiusZ, waterY, world, persistentEdit, selected, claimed, columns);
            }
        }
    }

    private int pondWaterLevel(int cellX, int cellZ, int centerX, int centerZ,
            int radiusX, int radiusZ, BlockView world,
            Map<Long, PlanningColumn> columns) {
        int minimum = Blocks.MAX_Y;
        int maximum = Blocks.MIN_Y;
        for (int z = centerZ - radiusZ - 1; z <= centerZ + radiusZ + 1; z++) {
            for (int x = centerX - radiusX - 1; x <= centerX + radiusX + 1; x++) {
                boolean inside = isPondInterior(cellX, cellZ, centerX, centerZ,
                        radiusX, radiusZ, x, z);
                if (!inside && !touchesPond(cellX, cellZ, centerX, centerZ,
                        radiusX, radiusZ, x, z)) {
                    continue;
                }
                PlanningColumn column = debugColumn(columns, world, x, z);
                if (!isPondBiome(column.biome)) return Integer.MIN_VALUE;
                minimum = Math.min(minimum, column.surfaceHeight);
                maximum = Math.max(maximum, column.surfaceHeight);
            }
        }
        return maximum - minimum <= 3 ? minimum - 1 : Integer.MIN_VALUE;
    }

    private boolean isValidPondSite(int cellX, int cellZ, int centerX, int centerZ,
            int radiusX, int radiusZ, int waterY, BlockView world,
            Map<Long, PlanningColumn> columns) {
        for (int z = centerZ - radiusZ - 1; z <= centerZ + radiusZ + 1; z++) {
            for (int x = centerX - radiusX - 1; x <= centerX + radiusX + 1; x++) {
                boolean inside = isPondInterior(cellX, cellZ, centerX, centerZ,
                        radiusX, radiusZ, x, z);
                boolean bank = !inside && touchesPond(cellX, cellZ, centerX, centerZ,
                        radiusX, radiusZ, x, z);
                if (!inside && !bank) continue;
                PlanningColumn column = debugColumn(columns, world, x, z);
                int surfaceY = column.surfaceHeight;
                if (!isPondBiome(column.biome)
                        || surfaceY < waterY + (bank ? 1 : 0)
                        || surfaceY > waterY + 4
                        || !Fluids.isSolid(world.getBlock(x, surfaceY, z))
                        || !isOpenPondSurface(world.getBlock(x, surfaceY + 1, z))) {
                    return false;
                }
                int protectedMinY = bank ? waterY : waterY - pondDepth(
                        cellX, cellZ, centerX, centerZ, radiusX, radiusZ, x, z) + 1;
                for (int y = protectedMinY; y <= surfaceY + 1; y++) {
                    if (world.isProtectedEdit(x, y, z)) return false;
                }
                if (bank) {
                    if (!Fluids.isSolid(world.getBlock(x, waterY, z))) return false;
                    continue;
                }
                int bottomY = waterY - pondDepth(cellX, cellZ, centerX, centerZ,
                        radiusX, radiusZ, x, z);
                for (int y = bottomY; y <= surfaceY; y++) {
                    if (!Fluids.isSolid(world.getBlock(x, y, z))) return false;
                }
            }
        }
        return true;
    }

    private void appendPondCells(int chunkX, int chunkZ, int cellX, int cellZ,
            int centerX, int centerZ, int radiusX, int radiusZ, int waterY, BlockView world,
            Predicate<BlockPos> persistentEdit, Map<BlockPos, Integer> selected,
            Set<BlockPos> claimed, Map<Long, PlanningColumn> columns) {
        int minX = chunkX * Blocks.CHUNK_X;
        int maxX = minX + Blocks.CHUNK_X - 1;
        int minZ = chunkZ * Blocks.CHUNK_Z;
        int maxZ = minZ + Blocks.CHUNK_Z - 1;
        for (int z = Math.max(minZ, centerZ - radiusZ);
                z <= Math.min(maxZ, centerZ + radiusZ); z++) {
            for (int x = Math.max(minX, centerX - radiusX);
                    x <= Math.min(maxX, centerX + radiusX); x++) {
                if (!isPondInterior(cellX, cellZ, centerX, centerZ, radiusX, radiusZ, x, z)) {
                    continue;
                }
                PlanningColumn column = debugColumn(columns, world, x, z);
                int surfaceY = column.surfaceHeight;
                int bottomY = waterY - pondDepth(cellX, cellZ, centerX, centerZ,
                        radiusX, radiusZ, x, z);
                boolean edited = false;
                for (int y = bottomY + 1; y <= Math.min(Blocks.MAX_Y, surfaceY + 1); y++) {
                    if (persistentEdit.test(new BlockPos(x, y, z))) {
                        edited = true;
                        break;
                    }
                }
                if (edited) continue;

                for (int y = bottomY + 1; y <= waterY; y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    int block = y == waterY && pondSurfaceFreezes(column.biome)
                            ? Blocks.ICE : Blocks.WATER_SOURCE;
                    selected.put(pos, block);
                    claimed.add(pos);
                }
                for (int y = waterY + 1; y <= Math.min(Blocks.MAX_Y, surfaceY + 1); y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    claimed.add(pos);
                    if (world.getBlock(x, y, z) != Blocks.AIR) selected.put(pos, Blocks.AIR);
                }
            }
        }
    }

    private boolean isPondInterior(int cellX, int cellZ, int centerX, int centerZ,
            int radiusX, int radiusZ, int x, int z) {
        return pondRatio(cellX, cellZ, centerX, centerZ, radiusX, radiusZ, x, z) <= 1024;
    }

    private int pondDepth(int cellX, int cellZ, int centerX, int centerZ,
            int radiusX, int radiusZ, int x, int z) {
        return pondRatio(cellX, cellZ, centerX, centerZ, radiusX, radiusZ, x, z) <= 420 ? 2 : 1;
    }

    private int pondRatio(int cellX, int cellZ, int centerX, int centerZ,
            int radiusX, int radiusZ, int x, int z) {
        long dx = x - centerX;
        long dz = z - centerZ;
        long ellipse = dx * dx * 1024L / (radiusX * radiusX)
                + dz * dz * 1024L / (radiusZ * radiusZ);
        int edge = bounded(x ^ cellX, z ^ cellZ, 0, 0x795, 97) - 48;
        return (int) ellipse - edge;
    }

    private boolean touchesPond(int cellX, int cellZ, int centerX, int centerZ,
            int radiusX, int radiusZ, int x, int z) {
        return isPondInterior(cellX, cellZ, centerX, centerZ, radiusX, radiusZ, x + 1, z)
                || isPondInterior(cellX, cellZ, centerX, centerZ, radiusX, radiusZ, x - 1, z)
                || isPondInterior(cellX, cellZ, centerX, centerZ, radiusX, radiusZ, x, z + 1)
                || isPondInterior(cellX, cellZ, centerX, centerZ, radiusX, radiusZ, x, z - 1);
    }

    private static boolean isOpenPondSurface(int block) {
        return block == Blocks.AIR || block == Blocks.SNOW;
    }

    private static boolean isPondBiome(int biome) {
        return biome != DEEP_DARK && biome != RIVER && biome != FROZEN_RIVER
                && biome != BEACH && biome != SNOWY_BEACH && !McBiomeRegistry.isOcean(biome);
    }

    private static boolean pondSurfaceFreezes(int biome) {
        return McFreezeTopLayer.shouldFreezeWater(
                McBiomeRegistry.get(biome).temperature(), Blocks.WATER_SOURCE);
    }

    /**
     * 동결 생성기를 수정하지 않고 한랭 표면과 확인된 건조 바이옴 선인장을 runtime overlay로 만든다.
     * SNOW(64)는 클라이언트의 상태 기반 1~8층 partial 형상에서 state 0/1이 첫 눈 층인 기존 역할을
     * 유지한다. 따라서 full-cube SNOW_BLOCK(233)이나 통과/동결 규칙이 있는 POWDER_SNOW(235)로
     * 치환하지 않는다. 대상 또는 바로 아래/위의 플레이어 diff가 있으면 자연 장식을 재생성하지 않는다.
     * 반환 목록은 WorldRuntime이 청크 단위 overlay를 완성해 첫 권위 스냅샷에 합성하는 초기 장식 묶음이다.
     */
    /**
     * Project-custom surface overlay. Every vanilla surface/cave/ore lane this decorator used to
     * duplicate is now produced by the pinned 26.3 FEATURES stages, so only WebCraft's own
     * Rafflesia lane survives here.
     */
    public List<Decoration> customSurfaceDecorations(int chunkX, int chunkZ, BlockView world,
            Predicate<BlockPos> persistentEdit) {
        List<Decoration> selected = new DecorationList(4);
        Map<Long, PlanningColumn> columns = new HashMap<>(512);
        appendNaturalRafflesia(chunkX, chunkZ, world, persistentEdit, selected, columns);
        return List.copyOf(selected);
    }

    private static long coordinateKey(int x, int z) {
        return ((long) x << 32) ^ (z & 0xffffffffL);
    }

    private static long blockPositionKey(int x, int y, int z) {
        return ((long) x & 0x3ffffffL) << 38
                | ((long) (y + 2048) & 0xfffL) << 26
                | ((long) z & 0x3ffffffL);
    }

    private PlanningColumn debugColumn(Map<Long, PlanningColumn> columns,
            BlockView world, int x, int z) {
        long key = coordinateKey(x, z);
        PlanningColumn column = columns.get(key);
        if (column == null) {
            column = planningColumn(world, x, z);
            columns.put(key, column);
        }
        return column;
    }

    /**
     * Uses the already generated chunk heightmap when available. The fallback retains exact
     * density-column behavior for standalone fixtures that only implement {@link BlockView#getBlock}.
     */
    private PlanningColumn planningColumn(BlockView world, int x, int z) {
        long key = coordinateKey(x, z);
        PlanningColumn cached = columnCache.get(key);
        if (cached != null) return cached;

        int surfaceHeight = world == null
                ? UNKNOWN_SURFACE_HEIGHT : world.terrainSurfaceHeight(x, z);
        PlanningColumn computed;
        if (surfaceHeight == UNKNOWN_SURFACE_HEIGHT) {
            computed = PlanningColumn.fromDebug(ChunkGenerator.debugColumn(seed, x, z));
        } else {
            int biome = caveBiomeSampler.get().biomeAtBlock(x, surfaceHeight, z);
            int waterLevel = surfaceHeight < Blocks.SEA_LEVEL
                    ? Blocks.SEA_LEVEL : McSurfaceRuleEngine.NO_WATER;
            computed = new PlanningColumn(surfaceHeight, biome, waterLevel);
        }
        PlanningColumn existing = columnCache.putIfAbsent(key, computed);
        if (existing != null) return existing;
        columnCacheOrder.add(key);
        while (columnCache.size() > COLUMN_CACHE_LIMIT) {
            Long oldest = columnCacheOrder.poll();
            if (oldest == null) break;
            columnCache.remove(oldest);
        }
        return computed;
    }

    /** Exact climate-table biome lookup when the generated terrain surface is already known. */
    int surfaceBiome(int x, int surfaceY, int z) {
        return caveBiomeSampler.get().biomeAtBlock(x, surfaceY, z);
    }

    /** Original rare swamp colony root: one deterministic candidate per eligible 1/32 chunk. */
    private void appendNaturalRafflesia(int chunkX, int chunkZ, BlockView world,
            Predicate<BlockPos> persistentEdit, List<Decoration> selected,
            Map<Long, PlanningColumn> columns) {
        if (bounded(chunkX, chunkZ, 0, 0x865, 32) != 0) return;
        int baseX = chunkX * Blocks.CHUNK_X;
        int baseZ = chunkZ * Blocks.CHUNK_Z;
        for (int attempt = 0; attempt < Blocks.CHUNK_X * Blocks.CHUNK_Z; attempt++) {
            int x = baseX + bounded(chunkX, chunkZ, attempt, 0x7FA, Blocks.CHUNK_X);
            int z = baseZ + bounded(chunkX, chunkZ, attempt, 0x7FB, Blocks.CHUNK_Z);
            PlanningColumn column = debugColumn(columns, world, x, z);
            if (column.biome != SWAMP && column.biome != MANGROVE_SWAMP) continue;
            BlockPos root = new BlockPos(x, column.surfaceHeight, z);
            BlockPos flower = new BlockPos(x, column.surfaceHeight + 1, z);
            if (persistentEdit.test(root) || persistentEdit.test(flower)
                    || containsDecoration(selected, flower)
                    || selectedOrWorldBlock(selected, world, flower) != Blocks.AIR
                    || !RafflesiaRules.isHostSoil(selectedOrWorldBlock(selected, world, root))
                    || !hasNaturalLivingHost(selected, world, root)
                    || !hasNearbyCanonicalFirefly(world, x, flower.y(), z, 4)) continue;
            selected.add(new Decoration(flower, Blocks.RAFFLESIA));
            return;
        }
    }

    private boolean hasNaturalLivingHost(List<Decoration> selected, BlockView world, BlockPos root) {
        for (int dy = 0; dy <= 3; dy++) for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                int block = selectedOrWorldBlock(selected, world,
                        new BlockPos(root.x() + dx, root.y() + dy, root.z() + dz));
                if (block == Blocks.MANGROVE_ROOTS || block == Blocks.MUDDY_MANGROVE_ROOTS
                        || BlockFamilies.isWoodLog(block) && !Blocks.isStrippedLog(block)
                        && !Blocks.isStrippedPoplarLog(block)
                        && !Blocks.isStrippedPaleOakLog(block)) return true;
            }
        }
        return false;
    }

    /**
     * {@code minecraft:patch_firefly_bush*} is a canonical step-9 feature, so the Rafflesia host rule
     * reads the finished product instead of a sibling overlay list.
     */
    private static boolean hasNearbyCanonicalFirefly(BlockView world,
            int x, int y, int z, int radius) {
        for (int dy = -1; dy <= 1; dy++) {
            for (int dz = -radius; dz <= radius; dz++) {
                for (int dx = -radius; dx <= radius; dx++) {
                    if (world.getBlock(x + dx, y + dy, z + dz) == Blocks.FIREFLY_BUSH) return true;
                }
            }
        }
        return false;
    }

    private boolean containsDecoration(List<Decoration> selected, BlockPos pos) {
        if (selected instanceof DecorationList indexed) {
            return indexed.containsPosition(pos);
        }
        for (Decoration decoration : selected) {
            if (decoration.pos().equals(pos)) return true;
        }
        return false;
    }

    private int selectedOrWorldBlock(
            List<Decoration> selected, BlockView world, BlockPos pos) {
        if (selected instanceof DecorationList indexed) {
            Integer block = indexed.blockAt(pos);
            return block == null ? world.getBlock(pos.x(), pos.y(), pos.z()) : block;
        }
        for (int i = selected.size() - 1; i >= 0; i--) {
            Decoration decoration = selected.get(i);
            if (decoration.pos().equals(pos)) return decoration.blockType();
        }
        return world.getBlock(pos.x(), pos.y(), pos.z());
    }

    static boolean isDeadBushSupport(int blockId) {
        return blockId == Blocks.SAND || blockId == Blocks.RED_SAND
                || blockId == Blocks.DIRT || blockId == Blocks.COARSE_DIRT
                || blockId == Blocks.TERRACOTTA || blockId == Blocks.ORANGE_TERRACOTTA
                || blockId == Blocks.WHITE_TERRACOTTA || blockId == Blocks.YELLOW_TERRACOTTA
                || blockId == Blocks.BROWN_TERRACOTTA || blockId == Blocks.RED_TERRACOTTA
                || blockId == Blocks.LIGHT_GRAY_TERRACOTTA;
    }

    private int score(int chunkX, int chunkZ, int attempt, int salt) {
        int h = seed ^ Integer.rotateLeft(chunkX * 0x9E3779B9, 11)
                ^ Integer.rotateLeft(chunkZ * 0x85EBCA6B, 19);
        h ^= attempt * 0xC2B2AE35;
        h ^= salt * 0x27D4EB2D;
        h ^= h >>> 16;
        h *= 0x7FEB352D;
        h ^= h >>> 15;
        h *= 0x846CA68B;
        return h ^ (h >>> 16);
    }

    private int bounded(int chunkX, int chunkZ, int attempt, int salt, int bound) {
        return Math.floorMod(score(chunkX, chunkZ, attempt, salt), bound);
    }

}
