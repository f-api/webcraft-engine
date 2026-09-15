package com.gameexpert.engine;

import com.gameexpert.engine.enchant.EnchantmentRules;
import com.gameexpert.engine.inventory.PlayerInventory;
import com.gameexpert.terrain.Blocks;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/** 사용자 정의 차원 규칙. Minecraft 생성기·RNG·전리품 정체성과 독립적이다. */
public final class FleshNetherRules {
    public static final int FLOOR_Y = 63;
    public static final int CLUSTER_SPACING = 128;
    public static final int RADIUS = 12;
    public static final int GROWTH_INTERVAL = FleshColonyProgress.GROWTH_INTERVAL_TICKS;
    public static final int CLUSTERS_PER_STEP = 4;
    public static final int COCOON_INTERVAL = 200;
    public static final int HATCH_COOLDOWN = 100;
    public static final int MOB_CAP = FleshColonyProgress.RESIDENT_GLOBAL_CAP;
    public static final int GLOBAL_HATCH_INTERVAL = 20;
    public static final int HEIGHT_SALT = 0x336c0000;
    public static final int FRONTIER_SALT = 0x447d0000;
    public static final int COCOON_SALT = 0x558e0000;
    public static final int STAGE_SALT = 0x669f0000;
    private static final int[] DX = {0, 1, 0, -1};
    private static final int[] DZ = {-1, 0, 1, 0};

    private FleshNetherRules() { }

    public static boolean isFleshMob(String type) {
        return "FLESH_STALKER".equals(type) || "GHOUL".equals(type) || "BABY_GHOUL".equals(type) || "BONE_PROCESSION".equals(type) || "HANGING_MAW".equals(type);
    }

    public static int hatchRoll(int seed, int x, int z) {
        return (int) (hash(seed, x, z, 0x71660000) % 100);
    }

    /** Count the current resident/locked population without loading terrain or reserving an entity. */
    public static final class Population {
        private final BlockPos core;
        private int global, total, family, babies, guardians;
        public Population(BlockPos core) { this.core = core; }
        public void include(String type, double x, double z) {
            if (!isFleshMob(type)) return;
            global++;
            if (Math.floorDiv((int) Math.floor(x), 256) != Math.floorDiv(core.x(), 256)
                    || Math.floorDiv((int) Math.floor(z), 256) != Math.floorDiv(core.z(), 256)) return;
            total++;
            if ("GHOUL".equals(type) || "BABY_GHOUL".equals(type)) family++;
            if ("BONE_PROCESSION".equals(type)) guardians++;
            if ("BABY_GHOUL".equals(type)) babies++;
        }
        public FleshColonyProgress.PopulationCounts counts() {
            return new FleshColonyProgress.PopulationCounts(total, family, babies, guardians, global);
        }
    }

    public static boolean isFlesh(int block) {
        return block == Blocks.FLESH_HEART || block == Blocks.HEART_CORE
                || block == Blocks.FLESH_BLOCK || block == Blocks.FLESH_COCOON || Blocks.isFleshTissue(block);
    }

    /** 실제 도구·무기만 허용한다. 내구도가 있다는 이유로 방어구를 도구로 보지 않는다. */
    public static boolean isMiningTool(short item) {
        return EnchantmentRules.isPickaxeItem(item) || EnchantmentRules.isAxeItem(item)
                || EnchantmentRules.isShovelItem(item) || EnchantmentRules.isHoeItem(item)
                || EnchantmentRules.isSwordItem(item) || EnchantmentRules.isSpearItem(item)
                || EnchantmentRules.isShearsItem(item) || EnchantmentRules.isFishingRodItem(item)
                || EnchantmentRules.isBowItem(item) || item == PlayerInventory.TRIDENT
                || item == PlayerInventory.CROSSBOW || item == PlayerInventory.CARROT_ON_A_STICK
                || item == PlayerInventory.BRUSH || item == PlayerInventory.FLINT_AND_STEEL;
    }

    public static boolean canBreak(int block, short held) {
        return block != Blocks.ABYSS_STONE && (!isFlesh(block) || isMiningTool(held));
    }

    /** 정확히 [0,.05)만 성공. 경계값과 부동소수점 NaN도 성공으로 취급하지 않는다. */
    public static boolean mysteryDrop(double roll) { return roll >= 0 && roll < 0.05; }

    public static long hash(int seed, int x, int z, int salt) {
        int v = seed ^ x * 0x9e3779b9 ^ z * 0x85ebca6b ^ salt;
        v = (v ^ (v >>> 16)) * 0x7feb352d;
        v = (v ^ (v >>> 15)) * 0x846ca68b;
        return Integer.toUnsignedLong(v ^ (v >>> 16));
    }

    public static BlockPos coreFor(int x, int z) {
        return new BlockPos(Math.floorDiv(x, CLUSTER_SPACING) * CLUSTER_SPACING + 64,
                65, Math.floorDiv(z, CLUSTER_SPACING) * CLUSTER_SPACING + 64);
    }

    public static int height(int seed, int x, int z) {
        long roll = hash(seed, x, z, HEIGHT_SALT) % 100;
        return roll < 70 ? 1 : roll < 90 ? 2 : roll < 98 ? 3 : 4;
    }

    public static Map<BlockPos, Integer> initialColumns(int seed, BlockPos core) {
        Map<BlockPos, Integer> columns = new LinkedHashMap<>();
        for (int branch = 0; branch < 8; branch++) {
            int x = core.x(), z = core.z(), direction = branch & 3;
            int length = 8 + (int) (hash(seed, core.x(), core.z(), 0x115a0000 + branch) % 9);
            columns.put(new BlockPos(x, 64, z), height(seed, x, z));
            for (int step = 0; step < length; step++) {
                int turn = (int) (hash(seed, core.x(), core.z(),
                        0x225b0000 + branch * 32 + step) % 10);
                if (turn < 2) direction = (direction + (turn == 0 ? 3 : 1)) & 3;
                int nx = x + DX[direction], nz = z + DZ[direction];
                if (distance(core, nx, nz) > RADIUS) {
                    direction = (direction + 1) & 3;
                    nx = x + DX[direction]; nz = z + DZ[direction];
                    if (distance(core, nx, nz) > RADIUS) continue;
                }
                x = nx; z = nz;
                columns.put(new BlockPos(x, 64, z), height(seed, x, z));
            }
        }
        return java.util.Collections.unmodifiableMap(columns);
    }

    public static boolean naturalCocoon(int seed, int x, int z) {
        return hash(seed, x, z, COCOON_SALT) % 64 == 0;
    }

    public static int naturalCocoonStage(int seed, int x, int z) {
        return (int) (hash(seed, x, z, STAGE_SALT) % 3);
    }

    /** 상주 확인과 명시적 편집 검사는 호출자의 같은 불변 뷰에서 수행한다. */
    public static List<BlockPos> nextColumn(int seed, BlockPos core,
            Fluids.BlockLookup resident, Predicate<BlockPos> edited) {
        if (edited.test(core) || resident.get(core.x(), core.y(), core.z()) != Blocks.HEART_CORE) {
            return List.of();
        }
        List<BlockPos> candidates = new ArrayList<>();
        for (int dz = -RADIUS; dz <= RADIUS; dz++) {
            for (int dx = -RADIUS; dx <= RADIUS; dx++) {
                if (Math.abs(dx) + Math.abs(dz) > RADIUS) continue;
                int x = core.x() + dx, z = core.z() + dz;
                if (resident.get(x, FLOOR_Y, z) != Blocks.ABYSS_STONE) continue;
                boolean adjacent = false;
                for (int side = 0; side < 4; side++) {
                    int block = resident.get(x + DX[side], 64, z + DZ[side]);
                    adjacent |= block == Blocks.FLESH_BLOCK || block == Blocks.FLESH_HEART
                            || block == Blocks.HEART_CORE;
                }
                if (!adjacent) continue;
                boolean clear = true;
                for (int y = 64; y < 64 + height(seed, x, z); y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (edited.test(pos) || resident.get(x, y, z) != Blocks.AIR) { clear = false; break; }
                }
                if (clear) candidates.add(new BlockPos(x, 64, z));
            }
        }
        candidates.sort(Comparator.<BlockPos>comparingLong(p -> hash(seed, p.x(), p.z(), FRONTIER_SALT))
                .thenComparingInt(BlockPos::z).thenComparingInt(BlockPos::x));
        if (candidates.isEmpty()) return List.of();
        BlockPos first = candidates.getFirst();
        List<BlockPos> result = new ArrayList<>();
        for (int y = 64; y < 64 + height(seed, first.x(), first.z()); y++) {
            result.add(new BlockPos(first.x(), y, first.z()));
        }
        return List.copyOf(result);
    }


    public static boolean requiresLootRoll(int block) { return block == Blocks.FLESH_BLOCK; }

    public static int dropItem(int block, double roll) {
        var tissue = FleshTissueLoot.drops(block, 0, () -> roll);
        if (tissue != null) return tissue.isEmpty() ? Blocks.AIR : tissue.getFirst()[0];
        if (block == Blocks.HEART_CORE) return Blocks.HEART_CORE;
        return block == Blocks.FLESH_BLOCK && mysteryDrop(roll) ? Blocks.MYSTERY_FLESH : Blocks.AIR;
    }

    /** 초기값만 제공한다. 실제 저장된 AIR/상태 편집은 차원 공급자가 반드시 우선 적용한다. */
    public static int initialBlock(int seed, int x, int y, int z) {
        return initialBlock(seed, x, y, z, null);
    }

    private static int initialBlock(int seed, int x, int y, int z, Map<BlockPos, Integer> columns) {
        if (y < Blocks.MIN_Y || y > Blocks.MAX_Y) return Blocks.AIR;
        if (y <= FLOOR_Y) return Blocks.ABYSS_STONE;
        if (y > 68) return Blocks.AIR;
        if (reservedPortal(x, y, z)) {
            if (z != 8 || x < 7 || x > 10) return Blocks.AIR;
            return x == 7 || x == 10 || y == 64 || y == 68
                    ? Blocks.OBSIDIAN : Blocks.NETHER_PORTAL;
        }
        BlockPos core = coreFor(x, z);
        if (heartFootprint(core, x, z)) {
            if (y > 66) return Blocks.AIR;
            return x == core.x() && z == core.z() && y == 65
                    ? Blocks.HEART_CORE : Blocks.FLESH_HEART;
        }
        int h = (columns == null ? initialColumns(seed, core) : columns)
                .getOrDefault(new BlockPos(x, 64, z), 0);
        if (h == 0) return Blocks.AIR;
        if (y < 64 + h) return Blocks.FLESH_BLOCK;
        return y == 64 + h && naturalCocoon(seed, x, z) ? Blocks.FLESH_COCOON : Blocks.AIR;
    }

    public static int initialState(int seed, int x, int y, int z) {
        return initialState(seed, x, y, z, initialBlock(seed, x, y, z));
    }

    /** Only initial provider cells use this overload; persisted raw states always take priority. */
    public static int initialState(int seed, int x, int y, int z, int blockType) {
        return blockType == Blocks.FLESH_COCOON
                ? naturalCocoonStage(seed, x, z) : 0;
    }

    /** One aligned chunk belongs to one 128-block cluster; calculate its walks just once. */
    public static short[] generateBlocks(int seed, int chunkX, int chunkZ) {
        int minX = Math.multiplyExact(chunkX, Blocks.CHUNK_X);
        int minZ = Math.multiplyExact(chunkZ, Blocks.CHUNK_Z);
        Math.addExact(minX, Blocks.CHUNK_X - 1);
        Math.addExact(minZ, Blocks.CHUNK_Z - 1);
        short[] blocks = new short[Blocks.CHUNK_BLOCKS];
        java.util.Arrays.fill(blocks, 0, (FLOOR_Y - Blocks.MIN_Y + 1) * 256,
                (short) Blocks.ABYSS_STONE);
        Map<BlockPos, Integer> columns = initialColumns(seed, coreFor(minX, minZ));
        for (int z = 0; z < Blocks.CHUNK_Z; z++) for (int x = 0; x < Blocks.CHUNK_X; x++) {
            for (int y = 64; y <= 68; y++) {
                blocks[Blocks.blockIndex(x, y, z)] =
                        (short) initialBlock(seed, minX + x, y, minZ + z, columns);
            }
        }
        return blocks;
    }

    public static GrowthPlan planGrowth(long tick, int seed, List<BlockPos> residentCores,
            int cursor, Fluids.BlockLookup resident, Predicate<BlockPos> edited) {
        if (tick % GROWTH_INTERVAL != 0 || residentCores.isEmpty()) {
            return new GrowthPlan(List.of(), cursor);
        }
        List<BlockPos> sorted = residentCores.stream().distinct()
                .sorted(Comparator.comparingInt(BlockPos::z).thenComparingInt(BlockPos::x)).toList();
        int first = Math.floorMod(cursor, sorted.size());
        int count = Math.min(CLUSTERS_PER_STEP, sorted.size());
        List<Cell> edits = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            for (BlockPos pos : nextColumn(seed, sorted.get((first + i) % sorted.size()), resident, edited)) {
                edits.add(new Cell(pos.x(), pos.y(), pos.z(), Blocks.FLESH_BLOCK, 0));
            }
        }
        return new GrowthPlan(edits, (first + count) % sorted.size());
    }

    /** 200틱/클러스터 제한과 원자 설치는 호출자 소유. 여기서는 다음 stage0 위치만 제안한다. */
    public static Cell nextCocoon(int seed, BlockPos core,
            Fluids.BlockLookup resident, Predicate<BlockPos> edited) {
        if (edited.test(core) || resident.get(core.x(), 65, core.z()) != Blocks.HEART_CORE) return null;
        List<BlockPos> candidates = new ArrayList<>();
        for (int dz = -RADIUS; dz <= RADIUS; dz++) {
            for (int dx = -RADIUS; dx <= RADIUS; dx++) {
                int x = core.x() + dx, z = core.z() + dz;
                if (Math.abs(dx) + Math.abs(dz) > RADIUS || heartFootprint(core, x, z)
                        || !naturalCocoon(seed, x, z)
                        || resident.get(x, FLOOR_Y, z) != Blocks.ABYSS_STONE) continue;
                int top = 63;
                while (top < 67 && resident.get(x, top + 1, z) == Blocks.FLESH_BLOCK) top++;
                if (top < 64) continue;
                BlockPos pos = new BlockPos(x, top + 1, z);
                if (!reservedPortal(x, pos.y(), z) && !edited.test(pos)
                        && resident.get(x, pos.y(), z) == Blocks.AIR) candidates.add(pos);
            }
        }
        candidates.sort(Comparator.<BlockPos>comparingLong(p -> hash(seed, p.x(), p.z(), FRONTIER_SALT))
                .thenComparingInt(BlockPos::z).thenComparingInt(BlockPos::x));
        if (candidates.isEmpty()) return null;
        BlockPos p = candidates.getFirst();
        return new Cell(p.x(), p.y(), p.z(), Blocks.FLESH_COCOON, 0);
    }

    public static int nextCocoonStage(int stage) {
        if (stage < 0 || stage > 2) throw new IllegalArgumentException("cocoon stage outside 0..2");
        return Math.min(2, stage + 1);
    }

    /** 참이면 부화 트랜잭션을 예약할 수 있다. 몹 생성/ID 발급/편집은 이 함수가 수행하지 않는다. */
    public static boolean canHatch(BlockPos core, int x, int y, int z, int stage,
            Fluids.BlockLookup resident, Predicate<BlockPos> edited, long tick,
            long lastGlobalHatch, long lastClusterHatch, int liveCount) {
        BlockPos cocoon = new BlockPos(x, y, z);
        return tick >= 0 && tick % GLOBAL_HATCH_INTERVAL == 0
                && stage == 2 && liveCount >= 0 && liveCount < MOB_CAP
                && (lastGlobalHatch < 0 || tick - lastGlobalHatch >= GLOBAL_HATCH_INTERVAL)
                && (lastClusterHatch < 0 || tick - lastClusterHatch >= HATCH_COOLDOWN)
                && distance(core, x, z) <= RADIUS && !heartFootprint(core, x, z)
                && y >= 65 && y <= 68 && !reservedPortal(x, y, z)
                && !edited.test(core) && !edited.test(cocoon)
                && resident.get(core.x(), 65, core.z()) == Blocks.HEART_CORE
                && resident.get(x, y, z) == Blocks.FLESH_COCOON
                && resident.get(x, y - 1, z) == Blocks.FLESH_BLOCK
                // The cocoon cell itself becomes AIR in the same atomic hatch proposal.
                && resident.get(x, y + 1, z) == Blocks.AIR;
    }

    private static boolean heartFootprint(BlockPos core, int x, int z) {
        return Math.abs(x - core.x()) <= 1 && Math.abs(z - core.z()) <= 1;
    }

    private static boolean reservedPortal(int x, int y, int z) {
        return x >= 6 && x <= 10 && z >= 6 && z <= 10 && y >= 64 && y <= 69;
    }

    public static final class Cell {
        private final int x, y, z, blockType, state;
        public Cell(int x, int y, int z, int blockType, int state) {
            this.x = x; this.y = y; this.z = z; this.blockType = blockType; this.state = state;
        }
        public int x() { return x; }
        public int y() { return y; }
        public int z() { return z; }
        public int blockType() { return blockType; }
        public int state() { return state; }
    }

    public static final class GrowthPlan {
        private final List<Cell> edits;
        private final int nextCursor;
        private GrowthPlan(List<Cell> edits, int nextCursor) {
            this.edits = List.copyOf(edits); this.nextCursor = nextCursor;
        }
        public List<Cell> edits() { return edits; }
        public int nextCursor() { return nextCursor; }
    }

    private static int distance(BlockPos core, int x, int z) {
        return Math.abs(x - core.x()) + Math.abs(z - core.z());
    }
}
