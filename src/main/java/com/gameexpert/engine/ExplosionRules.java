package com.gameexpert.engine;

import java.util.ArrayList;
import java.util.List;

import com.gameexpert.common.LongOpenHashMap;
import com.gameexpert.terrain.Blocks;

import static com.gameexpert.engine.Fluids.isFluid;
import static com.gameexpert.engine.Fluids.isSolid;

/** Minecraft Java 크리퍼 폭발의 순수 규칙(피해·노출도·블록 광선). */
public final class ExplosionRules {

    private static final int RAY_GRID = 16;
    private static final int RAY_COUNT = RAY_GRID * RAY_GRID * RAY_GRID
            - (RAY_GRID - 2) * (RAY_GRID - 2) * (RAY_GRID - 2);
    private static final double RAY_STEP = 0.3;
    private static final double STEP_ENERGY_LOSS = 0.225;
    /** 고정 16³ 표면 방향. 산술 순서와 저장 정밀도는 기존 매 폭발 계산과 같다. */
    private static final double[] RAY_DIRECTIONS = createRayDirections();

    private ExplosionRules() {
    }

    /** 블록 조회 포트. */
    public interface BlockLookup {
        int getBlock(int x, int y, int z);
    }

    /** 폭발 광선의 초기 에너지를 재현 가능하게 만드는 [0,1) 난수 포트. */
    @FunctionalInterface
    public interface RandomSource {
        double nextDouble();
    }

    /**
     * 16³ 큐브 표면에서 방출한 광선이 에너지를 잃으며 통과한 파괴 가능 블록 목록.
     * {@code power}는 파괴 구의 반경이 아니라 폭발력이며, 크리퍼 기본값은 3이다.
     */
    public static List<BlockPos> destroyedBlocks(BlockLookup world, double cx, double cy, double cz,
                                                  double power, RandomSource rng) {
        if (power <= 0) return List.of();
        List<BlockPos> destroyed = new ArrayList<>();
        LongOpenHashMap seen = new LongOpenHashMap(256);
        for (int ray = 0; ray < RAY_DIRECTIONS.length; ray += 3) {
            double dx = RAY_DIRECTIONS[ray];
            double dy = RAY_DIRECTIONS[ray + 1];
            double dz = RAY_DIRECTIONS[ray + 2];
            double energy = power * (0.7 + rng.nextDouble() * 0.6);
            double x = cx, y = cy, z = cz;
            int lastX = 0, lastY = 0, lastZ = 0, lastId = Blocks.AIR;
            double lastResistanceLoss = 0.0;
            boolean lastDestructible = false;
            boolean hasLastBlock = false;
            while (energy > 0) {
                int bx = (int) Math.floor(x);
                int by = (int) Math.floor(y);
                int bz = (int) Math.floor(z);
                if (by >= Blocks.MIN_Y && by <= Blocks.MAX_Y) {
                    int id;
                    if (hasLastBlock && bx == lastX && by == lastY && bz == lastZ) {
                        id = lastId;
                    } else {
                        id = world.getBlock(bx, by, bz);
                        lastX = bx;
                        lastY = by;
                        lastZ = bz;
                        lastId = id;
                        lastResistanceLoss = id == Blocks.AIR
                                ? 0.0 : (blastResistance(id) + 0.3) * 0.3;
                        lastDestructible = id != Blocks.AIR && isDestructible(id);
                        hasLastBlock = true;
                    }
                    if (id != Blocks.AIR) {
                        // 같은 복셀 안의 0.3 간격 표본마다 저항을 다시 빼는 바닐라 의미를 보존한다.
                        energy -= lastResistanceLoss;
                        if (energy > 0 && lastDestructible) {
                            long key = blockPositionKey(bx, by, bz);
                            if (seen.get(key, 0L) == 0L) {
                                seen.put(key, 1L);
                                destroyed.add(new BlockPos(bx, by, bz));
                            }
                        }
                    }
                }
                x += dx * RAY_STEP;
                y += dy * RAY_STEP;
                z += dz * RAY_STEP;
                energy -= STEP_ENERGY_LOSS;
            }
        }
        return destroyed;
    }

    private static double[] createRayDirections() {
        double[] directions = new double[RAY_COUNT * 3];
        int ray = 0;
        for (int ix = 0; ix < RAY_GRID; ix++) {
            for (int iy = 0; iy < RAY_GRID; iy++) {
                for (int iz = 0; iz < RAY_GRID; iz++) {
                    if (ix != 0 && ix != RAY_GRID - 1
                            && iy != 0 && iy != RAY_GRID - 1
                            && iz != 0 && iz != RAY_GRID - 1) continue;

                    double dx = ix / (RAY_GRID - 1.0) * 2.0 - 1.0;
                    double dy = iy / (RAY_GRID - 1.0) * 2.0 - 1.0;
                    double dz = iz / (RAY_GRID - 1.0) * 2.0 - 1.0;
                    double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
                    dx /= length;
                    dy /= length;
                    dz /= length;
                    directions[ray++] = dx;
                    directions[ray++] = dy;
                    directions[ray++] = dz;
                }
            }
        }
        if (ray != directions.length) throw new IllegalStateException("invalid explosion ray count");
        return directions;
    }

    /** Minecraft BlockPos의 26/12/26 배치. 프로젝트 월드 경계와 전체 빌드 높이에서 충돌이 없다. */
    private static long blockPositionKey(int x, int y, int z) {
        return ((long) x & 0x3ffffffL) << 38
                | ((long) (y + 2048) & 0xfffL) << 26
                | ((long) z & 0x3ffffffL);
    }

    /**
     * Normal 난이도 폭발 피해. 영향도는 {@code (1-distance/(2*power))*exposure}이고,
     * 피해는 {@code floor((impact²+impact)*7*power+1)}이다.
     */
    public static int damageAt(double distance, double power, double exposure) {
        double impact = impactAt(distance, power, exposure);
        if (impact <= 0) return 0;
        return (int) Math.floor((impact * impact + impact) * 7.0 * power + 1.0);
    }

    /** 피해와 넉백이 공유하는 거리·노출 영향도. */
    public static double impactAt(double distance, double power, double exposure) {
        if (power <= 0 || distance < 0 || distance >= 2.0 * power || exposure <= 0) return 0;
        return (1.0 - distance / (2.0 * power)) * Math.min(1.0, exposure);
    }

    /**
     * 엔티티 AABB의 MC 표본 격자 중 폭심까지 솔리드 블록에 막히지 않은 광선 비율.
     * 유체와 통과 가능한 장식은 시야를 막지 않는다.
     */
    public static double exposure(BlockLookup world, double explosionX, double explosionY, double explosionZ,
                                  double minX, double minY, double minZ,
                                  double maxX, double maxY, double maxZ) {
        if (maxX < minX || maxY < minY || maxZ < minZ) return 0;
        double stepX = 1.0 / ((maxX - minX) * 2.0 + 1.0);
        double stepY = 1.0 / ((maxY - minY) * 2.0 + 1.0);
        double stepZ = 1.0 / ((maxZ - minZ) * 2.0 + 1.0);
        double offsetX = (1.0 - Math.floor(1.0 / stepX) * stepX) / 2.0;
        double offsetZ = (1.0 - Math.floor(1.0 / stepZ) * stepZ) / 2.0;
        int clear = 0;
        int total = 0;
        for (double fx = 0; fx <= 1.0 + 1e-9; fx += stepX) {
            for (double fy = 0; fy <= 1.0 + 1e-9; fy += stepY) {
                for (double fz = 0; fz <= 1.0 + 1e-9; fz += stepZ) {
                    double x = minX + (maxX - minX) * fx + offsetX;
                    double y = minY + (maxY - minY) * fy;
                    double z = minZ + (maxZ - minZ) * fz + offsetZ;
                    if (hasClearRay(world, x, y, z, explosionX, explosionY, explosionZ)) clear++;
                    total++;
                }
            }
        }
        return total == 0 ? 0 : (double) clear / total;
    }

    private static boolean hasClearRay(BlockLookup world, double sx, double sy, double sz,
                                       double ex, double ey, double ez) {
        int x = (int) Math.floor(sx), y = (int) Math.floor(sy), z = (int) Math.floor(sz);
        int endX = (int) Math.floor(ex), endY = (int) Math.floor(ey), endZ = (int) Math.floor(ez);
        double dx = ex - sx, dy = ey - sy, dz = ez - sz;
        int stepX = Integer.compare(endX, x), stepY = Integer.compare(endY, y), stepZ = Integer.compare(endZ, z);
        double deltaX = stepX == 0 ? Double.POSITIVE_INFINITY : Math.abs(1.0 / dx);
        double deltaY = stepY == 0 ? Double.POSITIVE_INFINITY : Math.abs(1.0 / dy);
        double deltaZ = stepZ == 0 ? Double.POSITIVE_INFINITY : Math.abs(1.0 / dz);
        double nextX = firstBoundary(sx, x, stepX, dx);
        double nextY = firstBoundary(sy, y, stepY, dy);
        double nextZ = firstBoundary(sz, z, stepZ, dz);

        for (int traversed = 0; traversed < 512; traversed++) {
            if (y >= Blocks.MIN_Y && y <= Blocks.MAX_Y && isSolid(world.getBlock(x, y, z))) return false;
            if (x == endX && y == endY && z == endZ) return true;
            double next = Math.min(nextX, Math.min(nextY, nextZ));
            if (nextX <= next + 1e-12) { x += stepX; nextX += deltaX; }
            if (nextY <= next + 1e-12) { y += stepY; nextY += deltaY; }
            if (nextZ <= next + 1e-12) { z += stepZ; nextZ += deltaZ; }
        }
        return true;
    }

    private static double firstBoundary(double start, int block, int step, double direction) {
        if (step == 0) return Double.POSITIVE_INFINITY;
        double boundary = step > 0 ? block + 1.0 : block;
        return (boundary - start) / direction;
    }

    private static boolean isDestructible(int id) {
        return id != Blocks.BEDROCK && id != Blocks.ABYSS_STONE && id != Blocks.HEART_CORE && id != Blocks.FLESH_ANCHOR
                && !isFluid(id) && Blocks.isWorldBlockId(id);
    }

    /**
     * WebCraft가 가진 모든 월드 블록 ID의 Java blast resistance 대응표.
     *
     * <p><b>표에 없는 블록은 {@code default -> 0.0} 으로 조용히 떨어진다</b> — 저항이 진짜 0 인
     * 즉시파괴 장식과, 아직 등록되지 않아 0 이 된 블록을 값만 봐서는 구분할 수 없다는 뜻이다.
     * 그래서 {@link com.gameexpert.engine.ExplosionResistanceAuditTest} 가 등록 월드 블록 전수를
     * 돌며 저항 0 집합을 <b>명시 허용 목록과 정확히 일치</b>시킨다. 새 블록이 표에 빠지면 그
     * 테스트가 ID 를 들고 실패하므로 0.0 침묵이 회귀로 남지 않는다.
     */
    static double blastResistance(int id) {
        if (com.gameexpert.engine.redstone.RedstoneState.isRedstoneRail(id)) return .7;
        if (id == Blocks.LEVER || com.gameexpert.engine.redstone.RedstoneState.isButton(id) || com.gameexpert.engine.redstone.RedstoneState.isPressurePlate(id)) return .5;
        if (id == Blocks.REDSTONE_BLOCK) return 6;
        if (id == Blocks.OBSERVER) return 3;
        if (id == Blocks.PISTON || id == Blocks.STICKY_PISTON || id == Blocks.PISTON_HEAD) return 1.5;
        if (id == Blocks.MOVING_PISTON) return Double.POSITIVE_INFINITY;
        if (id == Blocks.REDSTONE_LAMP || id == Blocks.REDSTONE_LAMP_LIT) return .3;
        if (id == Blocks.TARGET) return .5;
        if (id == Blocks.DAYLIGHT_DETECTOR) return .2;
        if (id == Blocks.NOTE_BLOCK) return .8;
        if (id == Blocks.ABYSS_STONE || id == Blocks.HEART_CORE || id == Blocks.FLESH_ANCHOR) return Double.POSITIVE_INFINITY;
        if (FleshNetherRules.isFlesh(id)) return blastResistance(Blocks.SANDSTONE);
        if (Blocks.isSulfurBlock(id)) {
            return id == Blocks.SULFUR_SPIKE ? 3.0 : 6.0;
        }
        if (Blocks.isCinnabarBlock(id)) return 6.0;
        if (BlockFamilies.isWoodLog(id)) return 2.0;
        if (BlockFamilies.isLeaves(id)) return 0.2;
        // [SHELF-FUNGUS-WOOL-SLAB] 양털 반 블록은 양털과 같은 0.8, 선반은 바닐라
        // explosion_resistance 3, 선반버섯은 0 이다 [B].
        if (Blocks.isWoolSlab(id) || Blocks.isWoolStairs(id)) return 0.8;
        if (Blocks.isConcreteStairs(id) || Blocks.isConcreteSlab(id)) return 1.8;
        // [FURNITURE-26.3] 쿠션 16색은 바닐라에서 엔티티라 블록 explosion_resistance 자체가
        // 없다. 이 저장소는 divergence [C] 로 <b>양털 계열 부분 높이 월드 블록</b>으로 냈으므로
        // 값도 양털·양털 반 블록과 같은 0.8 을 쓴다 — 표에서 빠져 조용히 0.0 이 되는 것만은
        // 막는다(계열 판정이라 색이 늘어도 새지 않는다).
        if (Blocks.isCushion(id)) return 0.8;
        if (Blocks.isShelf(id)) return 3.0;
        if (Blocks.isShelfMushroom(id)) return 0.0;
        // 종별 목재 가공 계열은 일곱 형상 전부 바닐라 explosion_resistance 3.0 이다
        // (참나무 총칭 판자·반 블록·문과 같은 값, MC Java 1.21.4).
        if (Blocks.isSpeciesWoodBuildingBlock(id)) return 3.0;
        // [BLAST-AUDIT] 침대는 색과 무관하게 바닐라 0.2/0.2 다. 색 침대가 늘어도 표에서
        // 빠지지 않도록 개별 ID 가 아니라 계열 판정으로 받는다.
        if (Blocks.isBed(id)) return 0.2;
        // [FURNITURE-26.3] 건초 침대도 바닐라 explosion_resistance 0.2 라 위 침대 갈래가
        // 그대로 맞다 [B]. 쿠션은 위 {@code isCushion} 갈래가 이미 받았다.
        // [STAINED-GLASS] 색 유리·색 유리판은 무색 유리와 같은 바닐라 0.3/0.3 이다.
        // 색 32개를 case 로 나열하지 않고 계열 술어 한 줄로 받는다(침대 선례).
        if (Blocks.isStainedGlass(id) || Blocks.isStainedGlassPane(id)) return 0.3;
        // [COPPER] 구리 계열 987~1063 은 전부 바닐라 explosion_resistance 6.0 이다
        // (기존 구리 4블록 375~378 과 같은 값). 구리 랜턴만 바닐라 lantern 의 3.5 다.
        if (Blocks.isCopperBuildingBlock(id)) return 6.0;
        // [CHEST-FAMILY] 구리 상자(1811~1818)는 ID 가 isCopperBuildingBlock 의 987~1063 구간
        // 밖이라 그 술어에 걸리지 않는다. 값은 구리 계열 공통 6.0 그대로다([B]).
        if (Blocks.chestKind(id) == Blocks.CHEST_KIND_COPPER) return 6.0;
        // [ENDER-SHULKER] 엔더 상자 explosion_resistance 600 · 셜커 상자 17종 2.0 [B].
        // 엔더 상자는 흑요석(1200)의 절반이라 크리퍼·TNT 로는 사실상 부술 수 없다 —
        // 그 값이 곧 "내용이 절대 안전하다"는 바닐라 계약의 뼈대다.
        if (id == Blocks.ENDER_CHEST) return 600.0;
        if (Blocks.isShulkerBox(id)) return 2.0;
        if (id == Blocks.COPPER_LANTERN
                || id >= Blocks.EXPOSED_COPPER_LANTERN
                        && id <= Blocks.WAXED_OXIDIZED_COPPER_LANTERN) return 3.5;
        if (id >= Blocks.EXPOSED_LIGHTNING_ROD && id <= Blocks.WAXED_OXIDIZED_LIGHTNING_ROD
                || id >= Blocks.COPPER_GOLEM_STATUE
                        && id <= Blocks.WAXED_OXIDIZED_COPPER_GOLEM_STATUE) return 6.0;
        if (id >= Blocks.OCHRE_FROGLIGHT && id <= Blocks.PEARLESCENT_FROGLIGHT) return 0.3;
        if (id == Blocks.COPPER_TORCH
                || id >= Blocks.COPPER_WALL_TORCH_N && id <= Blocks.COPPER_WALL_TORCH_W) return 0.0;
        if (Blocks.isDecoratedPot(id)) return 0.0;
        // [PROP-MATERIAL] 철 랜턴은 바닐라 lantern 의 3.5 이고 사슬은 chain 의 6.0 이다
        // (사슬만 경도 5.0 과 폭발 저항 6.0 이 다르다 — 바닐라 값 그대로다).
        if (id == Blocks.LANTERN) return 3.5;
        // [COPPER-CHAIN] 구리 사슬도 ChainBlock 이라 같은 6.0 이다. 구리 계열 6.0 과 값이
        // 우연히 같지만 근거가 다르므로 위 isCopperBuildingBlock 갈래에 묶지 않는다.
        if (Blocks.isAnyChain(id)) return 6.0;
        if (Blocks.isAnvil(id)) return 1200.0;
        if (id == Blocks.TINTED_GLASS) return 0.3;
        if (id == Blocks.GLOWSTONE) return 0.3;
        // InfestedBlock constructor overrides every host's explosion resistance to 0.75.
        if (Blocks.isInfestedStone(id)) return 0.75;
        // [OPENABLE-METAL] 구리 창살은 IronBarsBlock 이라 철창과 같은 5.0/6.0 이다 —
        // 저항만은 구리 계열과 우연히 같지만, 근거가 다른 값을 위 갈래에 묶지 않는다.
        if (Blocks.isCopperBars(id)) return 6.0;
        // 철 문·철 다락문은 바닐라 iron_door/iron_trapdoor 가 5.0/5.0 이다.
        if (id == Blocks.IRON_DOOR || id == Blocks.IRON_TRAPDOOR) return 5.0;
        return switch (id) {
            case Blocks.BEDROCK -> Double.POSITIVE_INFINITY;
            case Blocks.OBSIDIAN, Blocks.ENCHANTING_TABLE, Blocks.ANCIENT_DEBRIS,
                    Blocks.NETHERITE_BLOCK, 60, 61, 62 -> 1200.0;
            case Blocks.NETHERRACK -> 0.4;
            case Blocks.TRIAL_SPAWNER, Blocks.VAULT -> 50.0;
            case Blocks.SCULK_CATALYST, Blocks.SCULK_SHRIEKER,
                    Blocks.CREAKING_HEART, Blocks.CREAKING_HEART_ACTIVE -> 3.0;
            case Blocks.SCULK_SENSOR -> 1.5;
            case Blocks.SCULK, Blocks.SCULK_VEIN -> 0.2;
            case Blocks.DRIED_GHAST, Blocks.MAGMA,
                    Blocks.BRIMSTONE_CARAPACE_TROPHY, Blocks.RESIN_BLOCK,
                    Blocks.CAKE, Blocks.TURTLE_EGG, Blocks.SUSPICIOUS_SAND,
                    Blocks.SUSPICIOUS_GRAVEL, Blocks.SNIFFER_EGG -> 0.5;
            case Blocks.BEE_NEST -> 0.3;
            case Blocks.BEEHIVE, Blocks.HONEYCOMB_BLOCK -> 0.6;
            case Blocks.WATER_SOURCE, 41, 42, 43, 44, 45, 46, 47,
                    Blocks.LAVA_SOURCE, 49, 50, 51 -> 100.0;
            case Blocks.STONE, Blocks.COBBLE, Blocks.STONE_BRICK, Blocks.COBBLE_SLAB,
                    Blocks.COAL_BLOCK, Blocks.IRON_BLOCK, Blocks.GOLD_BLOCK, Blocks.DIAMOND_BLOCK,
                    Blocks.GRANITE, Blocks.DIORITE, Blocks.ANDESITE, Blocks.TUFF,
                    Blocks.DRIPSTONE_BLOCK, Blocks.RAW_IRON_BLOCK, Blocks.RAW_COPPER_BLOCK,
                    Blocks.RAW_GOLD_BLOCK, Blocks.COPPER_BLOCK, Blocks.EXPOSED_COPPER,
                    Blocks.WEATHERED_COPPER, Blocks.OXIDIZED_COPPER,
                    // 심층암 가공 계열은 전 변형이 바닐라 explosion_resistance 6.0 이다.
                    // 선재(deepslate 3.0/6.0 · cobbled_deepslate 3.5/6.0)도 같은 6.0 이며,
                    // 가공 계열만 등록돼 있어 원석 둘이 표에서 빠져 있었다.
                    Blocks.DEEPSLATE, Blocks.COBBLED_DEEPSLATE,
                    // [BLAST-AUDIT] 이끼/돌벽돌 계열과 그 계단·반 블록·담장, 철창도 6.0 이다
                    // (mossy_cobblestone 2.0/6.0 · mossy_stone_bricks 1.5/6.0 ·
                    //  cobblestone_stairs·stone_brick_stairs 2.0·1.5/6.0 ·
                    //  stone_brick_slab·sandstone_slab 6.0 · iron_bars 5.0/6.0).
                    Blocks.MOSSY_COBBLE, Blocks.MOSSY_STONE_BRICK,
                    Blocks.COBBLE_STAIRS, Blocks.STONE_BRICK_STAIRS,
                    Blocks.STONE_BRICK_SLAB, Blocks.SANDSTONE_SLAB,
                    Blocks.COBBLE_WALL, Blocks.MOSSY_COBBLE_WALL, Blocks.STONE_BRICK_WALL,
                    Blocks.IRON_BARS,
                    Blocks.POLISHED_DEEPSLATE, Blocks.DEEPSLATE_BRICKS,
                    Blocks.CRACKED_DEEPSLATE_BRICKS, Blocks.DEEPSLATE_TILES,
                    Blocks.CRACKED_DEEPSLATE_TILES, Blocks.CHISELED_DEEPSLATE,
                    Blocks.POLISHED_DEEPSLATE_STAIRS, Blocks.DEEPSLATE_BRICK_STAIRS,
                    Blocks.DEEPSLATE_TILE_STAIRS, Blocks.POLISHED_DEEPSLATE_SLAB,
                    Blocks.DEEPSLATE_BRICK_SLAB, Blocks.DEEPSLATE_TILE_SLAB,
                    Blocks.POLISHED_DEEPSLATE_WALL, Blocks.DEEPSLATE_BRICK_WALL,
                    Blocks.DEEPSLATE_TILE_WALL,
                    // [VILLAGER-STATION] 숫돌과 매끄러운 돌만 explosion_resistance 6.0 이다.
                    Blocks.GRINDSTONE, Blocks.SMOOTH_STONE,
                    // [STONE-PROC] 석재 가공 계열은 붉은 사암/사암 변형 셋을 뺀 전부가 6.0 이다.
                    Blocks.GRANITE_STAIRS, Blocks.DIORITE_STAIRS, Blocks.ANDESITE_STAIRS,
                    Blocks.STONE_STAIRS, Blocks.MOSSY_COBBLE_STAIRS,
                    Blocks.MOSSY_STONE_BRICK_STAIRS,
                    Blocks.GRANITE_SLAB, Blocks.DIORITE_SLAB, Blocks.ANDESITE_SLAB,
                    Blocks.CUT_SANDSTONE_SLAB, Blocks.RED_SANDSTONE_SLAB, Blocks.STONE_SLAB,
                    Blocks.MOSSY_COBBLE_SLAB, Blocks.MOSSY_STONE_BRICK_SLAB,
                    Blocks.GRANITE_WALL, Blocks.DIORITE_WALL, Blocks.ANDESITE_WALL,
                    Blocks.MOSSY_STONE_BRICK_WALL,
                    // [STONE-RESIDUAL] 석재 잔여 계열은 진흙 둘과 붉은 사암 둘을 뺀 전부가 6.0 이다.
                    // 매끄러운(제련) 사암 계열은 원석 0.8 이 아니라 6.0 이고, 잘린 붉은 사암도
                    // 블록은 0.8 인데 반 블록만 6.0 이다.
                    Blocks.POLISHED_GRANITE, Blocks.POLISHED_DIORITE, Blocks.POLISHED_ANDESITE,
                    Blocks.SMOOTH_SANDSTONE, Blocks.SMOOTH_RED_SANDSTONE,
                    Blocks.CRACKED_STONE_BRICKS, Blocks.CHISELED_STONE_BRICKS, Blocks.BRICKS,
                    Blocks.CHISELED_TUFF, Blocks.POLISHED_TUFF, Blocks.TUFF_BRICKS,
                    Blocks.CHISELED_TUFF_BRICKS, Blocks.POLISHED_GRANITE_STAIRS,
                    Blocks.POLISHED_DIORITE_STAIRS, Blocks.POLISHED_ANDESITE_STAIRS,
                    Blocks.SMOOTH_SANDSTONE_STAIRS, Blocks.SMOOTH_RED_SANDSTONE_STAIRS,
                    Blocks.BRICK_STAIRS, Blocks.TUFF_STAIRS, Blocks.POLISHED_TUFF_STAIRS,
                    Blocks.TUFF_BRICK_STAIRS, Blocks.POLISHED_GRANITE_SLAB,
                    Blocks.POLISHED_DIORITE_SLAB, Blocks.POLISHED_ANDESITE_SLAB,
                    Blocks.SMOOTH_SANDSTONE_SLAB, Blocks.CUT_RED_SANDSTONE_SLAB,
                    Blocks.SMOOTH_RED_SANDSTONE_SLAB, Blocks.SMOOTH_STONE_SLAB,
                    Blocks.BRICK_SLAB, Blocks.TUFF_SLAB, Blocks.POLISHED_TUFF_SLAB,
                    Blocks.TUFF_BRICK_SLAB, Blocks.BRICK_WALL, Blocks.TUFF_WALL,
                    Blocks.POLISHED_TUFF_WALL, Blocks.TUFF_BRICK_WALL,
                    // [PRISMARINE] 프리즈머린 3재질과 그 형상 변형도 전부 1.5 / 6.0 이다
                    // (바닐라 prismarine·prismarine_bricks·dark_prismarine 전 변형 공통).
                    Blocks.PRISMARINE, Blocks.PRISMARINE_BRICKS, Blocks.DARK_PRISMARINE,
                    Blocks.PRISMARINE_STAIRS, Blocks.PRISMARINE_BRICK_STAIRS,
                    Blocks.DARK_PRISMARINE_STAIRS, Blocks.PRISMARINE_SLAB,
                    Blocks.PRISMARINE_BRICK_SLAB, Blocks.DARK_PRISMARINE_SLAB,
                    Blocks.PRISMARINE_WALL -> 6.0;
            // [STONE-PROC] 사암·붉은 사암 원본 속성을 복사하는 계단·담장만 0.8 이다
            // (같은 재질이라도 1.13 에 개별 등록된 반 블록은 위 6.0 이다).
            // [BLAST-AUDIT] 사암 원석·다듬은/조각 사암·붉은 사암도 같은 0.8 이다.
            case Blocks.RED_SANDSTONE_STAIRS, Blocks.SANDSTONE_WALL,
                    Blocks.RED_SANDSTONE_WALL,
                    Blocks.SANDSTONE, Blocks.SANDSTONE_STAIRS, Blocks.CUT_SANDSTONE,
                    Blocks.CHISELED_SANDSTONE, Blocks.RED_SANDSTONE,
                    // [STONE-RESIDUAL] 잘린·조각된 붉은 사암도 원석과 같은 0.8 이다.
                    Blocks.CHISELED_RED_SANDSTONE, Blocks.CUT_RED_SANDSTONE,
                    // [QUARTZ] 석영 계열 962~975 는 explosion_resistance 가 destroy_time 과
                    // 같은 0.8 이다(바닐라 quartz_block 계열 전부 0.8 / 0.8).
                    Blocks.QUARTZ_BLOCK, Blocks.CHISELED_QUARTZ_BLOCK, Blocks.QUARTZ_PILLAR,
                    Blocks.QUARTZ_PILLAR_X, Blocks.QUARTZ_PILLAR_Z, Blocks.SMOOTH_QUARTZ,
                    Blocks.QUARTZ_BRICKS, Blocks.QUARTZ_STAIRS, Blocks.SMOOTH_QUARTZ_STAIRS,
                    Blocks.QUARTZ_SLAB, Blocks.SMOOTH_QUARTZ_SLAB, Blocks.QUARTZ_WALL,
                    Blocks.SMOOTH_QUARTZ_WALL, Blocks.QUARTZ_BRICK_WALL -> 0.8;
            // [VILLAGER-STATION] 나머지 스테이션은 explosion_resistance == destroy_time 이다.
            // [BLAST-AUDIT] 점화 변형은 바닐라에서 같은 블록의 상태라 값이 같다(FURNACE_LIT 선례).
            case Blocks.BLAST_FURNACE, Blocks.SMOKER, Blocks.STONECUTTER,
                    Blocks.BLAST_FURNACE_LIT, Blocks.SMOKER_LIT -> 3.5;
            case Blocks.BARREL, Blocks.CARTOGRAPHY_TABLE, Blocks.FLETCHING_TABLE,
                    Blocks.LECTERN, Blocks.LOOM, Blocks.SMITHING_TABLE -> 2.5;
            case Blocks.CAULDRON -> 2.0;
            case Blocks.COMPOSTER -> 0.6;
            case Blocks.BREWING_STAND -> 0.5;
            case Blocks.FURNACE, Blocks.FURNACE_LIT -> 3.5;
            // [BLAST-AUDIT] 참나무 총칭 가공 계열(계단·울타리·울타리문·다락문)도 종별 세트와
            // 같은 3.0 이며, 뾰족한 종유석은 1.5/3.0 이다.
            case Blocks.PLANK, Blocks.PLANK_SLAB, Blocks.DOOR_CLOSED,
                    Blocks.WOOD_STAIRS, Blocks.WOOD_FENCE, Blocks.WOOD_FENCE_GATE,
                    Blocks.WOOD_TRAPDOOR, Blocks.POINTED_DRIPSTONE,
                    // [STONE-RESIDUAL] 진흙 계열만 폭발 저항이 3.0 이다(다진 진흙 1.0/3.0 ·
                    // 진흙 벽돌 1.5/3.0). 나머지 석재 잔여 계열은 위 6.0 이다.
                    Blocks.PACKED_MUD, Blocks.MUD_BRICKS, Blocks.MUD_BRICK_STAIRS,
                    Blocks.MUD_BRICK_SLAB, Blocks.MUD_BRICK_WALL -> 3.0;
            // [BLAST-AUDIT] 테라코타는 전 색이 1.25/4.2 다.
            case Blocks.TERRACOTTA, Blocks.WHITE_TERRACOTTA, Blocks.ORANGE_TERRACOTTA,
                    Blocks.YELLOW_TERRACOTTA, Blocks.BROWN_TERRACOTTA, Blocks.RED_TERRACOTTA,
                    Blocks.LIGHT_GRAY_TERRACOTTA -> 4.2;
            // [BLAST-AUDIT] 거미줄 4.0, 상자·작업대 2.5, 모닥불·뼈 블록 2.0.
            case Blocks.COBWEB -> 4.0;
            // [CHEST-FAMILY] 덫 상자는 바닐라에서 일반 상자와 물성이 같다(2.5/2.5).
            case Blocks.CHEST, Blocks.TRAPPED_CHEST, Blocks.CRAFTING_TABLE -> 2.5;
            case Blocks.CAMPFIRE, Blocks.BONE_BLOCK -> 2.0;
            case Blocks.LODESTONE -> 3.5;
            // [TRIAL-GAP] emerald_block strength(5, 6) · heavy_core explosionResistance(1200).
            case Blocks.EMERALD_BLOCK -> 6.0;
            case Blocks.HEAVY_CORE -> 1200.0;
            // [DRAGON] dragon_egg strength(3.0, 9.0).
            case Blocks.DRAGON_EGG -> 9.0;
            // [END-CITY] dragon_head strength(1.0) · magenta_wall_banner strength(1.0).
            case Blocks.DRAGON_HEAD, Blocks.MAGENTA_WALL_BANNER -> 1.0;
            // [UTILITY] beacon strength(3.0) · jukebox strength(2.0, 6.0) · slime_block 0.
            case Blocks.BEACON -> 3.0;
            case Blocks.JUKEBOX -> 6.0;
            case Blocks.SLIME_BLOCK -> 0.0;
            // [UTILITY] 흑암석 계열 explosionResistance 6.0.
            case Blocks.BLACKSTONE, Blocks.POLISHED_BLACKSTONE, Blocks.POLISHED_BLACKSTONE_BRICKS -> 6.0;
            // [VOID-END] 엔드 돌 계열 strength(3.0, 9.0), 보라 계열 6.0(반 블록은 destroyTime 만 2.0 이라
            // 폭발 저항은 복사한 6.0 그대로), 후렴 식물·꽃 strength(0.4). 엔드 막대는 instabreak 0.
            case Blocks.END_STONE, Blocks.END_STONE_BRICKS, Blocks.END_STONE_BRICK_STAIRS,
                    Blocks.END_STONE_BRICK_SLAB, Blocks.END_STONE_BRICK_WALL -> 9.0;
            case Blocks.PURPUR_BLOCK, Blocks.PURPUR_PILLAR, Blocks.PURPUR_STAIRS,
                    Blocks.PURPUR_SLAB -> 6.0;
            case Blocks.CHORUS_PLANT, Blocks.CHORUS_FLOWER -> 0.4;
            // [BLAST-AUDIT] 대나무·호박 계열은 1.0, 찢어진 군기는 바닐라 대응 블록 banner 의 1.0 이다.
            case Blocks.BAMBOO, Blocks.PUMPKIN, Blocks.CARVED_PUMPKIN, Blocks.JACK_O_LANTERN,
                    Blocks.TATTERED_BANNER,
                    Blocks.WHITE_BANNER, Blocks.ORANGE_BANNER, Blocks.MAGENTA_BANNER,
                    Blocks.LIGHT_BLUE_BANNER, Blocks.YELLOW_BANNER, Blocks.LIME_BANNER,
                    Blocks.PINK_BANNER, Blocks.GRAY_BANNER, Blocks.LIGHT_GRAY_BANNER,
                    Blocks.CYAN_BANNER, Blocks.PURPLE_BANNER, Blocks.BLUE_BANNER,
                    Blocks.BROWN_BANNER, Blocks.GREEN_BANNER, Blocks.RED_BANNER,
                    Blocks.BLACK_BANNER -> 1.0;
            // [BLAST-AUDIT] 레일·맹그로브 뿌리 계열 0.7, 가루눈 0.25.
            case Blocks.RAIL, Blocks.MANGROVE_ROOTS, Blocks.MUDDY_MANGROVE_ROOTS -> 0.7;
            case Blocks.POWDER_SNOW -> 0.25;
            case Blocks.COAL_ORE, Blocks.IRON_ORE, Blocks.GOLD_ORE, Blocks.DIAMOND_ORE,
                    Blocks.EMERALD_ORE, Blocks.LAPIS_ORE, Blocks.REDSTONE_ORE,
                    Blocks.NETHER_GOLD_ORE, Blocks.COPPER_ORE, Blocks.DEEPSLATE_COAL_ORE,
                    Blocks.DEEPSLATE_IRON_ORE, Blocks.DEEPSLATE_COPPER_ORE,
                    Blocks.DEEPSLATE_GOLD_ORE, Blocks.DEEPSLATE_REDSTONE_ORE,
                    Blocks.DEEPSLATE_EMERALD_ORE, Blocks.DEEPSLATE_LAPIS_ORE,
                    Blocks.DEEPSLATE_DIAMOND_ORE,
                    // [CONDUIT] 콘딧도 3.0 이다 — [A] ConduitBlock 은 strength(3.0F) 라
                    // destroy_time 과 explosion_resistance 가 같은 값이다.
                    Blocks.CONDUIT -> 3.0;
            case Blocks.AMETHYST_BLOCK, Blocks.BUDDING_AMETHYST,
                    Blocks.SMALL_AMETHYST_BUD, Blocks.MEDIUM_AMETHYST_BUD,
                    Blocks.LARGE_AMETHYST_BUD, Blocks.AMETHYST_CLUSTER,
                    Blocks.BOOKSHELF -> 1.5;
            case Blocks.CALCITE -> 0.75;
            // [BLAST-AUDIT] 경작지도 0.6 이다.
            // [PRISMARINE] 스펀지 둘은 바닐라 explosion_resistance 가 destroy_time 과 같은 0.6 이다.
            case Blocks.GRASS, Blocks.CLAY, Blocks.MYCELIUM, Blocks.FARMLAND,
                    Blocks.SPONGE, Blocks.WET_SPONGE -> 0.6;
            case Blocks.DIRT_PATH -> 0.65;
            // [BLAST-AUDIT] 얼음·건초 더미·거친 흙도 0.5 다.
            case Blocks.DIRT, Blocks.SAND, Blocks.GRAVEL, Blocks.MUD, Blocks.PODZOL,
                    Blocks.RED_SAND, Blocks.ROOTED_DIRT, Blocks.PACKED_ICE,
                    Blocks.ICE, Blocks.HAY_BLOCK, Blocks.COARSE_DIRT,
                    // [FROST-SOUL] strength(0.5) 한 값이라 폭발 저항도 0.5 다.
                    Blocks.FROSTED_ICE, Blocks.SOUL_SAND, Blocks.SOUL_SOIL -> 0.5;
            case Blocks.SMOOTH_BASALT -> 4.2;
            case Blocks.CACTUS, Blocks.LADDER -> 0.4;
            // [PRISMARINE] 바다 랜턴도 유리와 같은 0.3 / 0.3 이다.
            case Blocks.GLASS, Blocks.GLASS_PANE, Blocks.SEA_LANTERN -> 0.3;
            // [BLAST-AUDIT] 덩굴·발광 이끼·눈 블록도 0.2 다.
            case Blocks.BROWN_MUSHROOM_BLOCK, Blocks.RED_MUSHROOM_BLOCK,
                    Blocks.MUSHROOM_STEM,
                    Blocks.VINE, Blocks.GLOW_LICHEN, Blocks.SNOW_BLOCK -> 0.2;
            // 양털·카펫은 바닐라 explosion_resistance 가 destroy_time 과 같다(0.8 / 0.1).
            case Blocks.WHITE_WOOL, Blocks.ORANGE_WOOL, Blocks.MAGENTA_WOOL,
                    Blocks.LIGHT_BLUE_WOOL, Blocks.YELLOW_WOOL, Blocks.LIME_WOOL,
                    Blocks.PINK_WOOL, Blocks.GRAY_WOOL, Blocks.LIGHT_GRAY_WOOL,
                    Blocks.CYAN_WOOL, Blocks.PURPLE_WOOL, Blocks.BLUE_WOOL,
                    Blocks.BROWN_WOOL, Blocks.GREEN_WOOL, Blocks.RED_WOOL,
                    Blocks.BLACK_WOOL -> 0.8;
            case Blocks.WHITE_CARPET, Blocks.ORANGE_CARPET, Blocks.MAGENTA_CARPET,
                    Blocks.LIGHT_BLUE_CARPET, Blocks.YELLOW_CARPET, Blocks.LIME_CARPET,
                    Blocks.PINK_CARPET, Blocks.GRAY_CARPET, Blocks.LIGHT_GRAY_CARPET,
                    Blocks.CYAN_CARPET, Blocks.PURPLE_CARPET, Blocks.BLUE_CARPET,
                    Blocks.BROWN_CARPET, Blocks.GREEN_CARPET, Blocks.RED_CARPET,
                    Blocks.BLACK_CARPET -> 0.1;
            // [BLAST-AUDIT] 눈 층·이끼 블록/카펫·큰 드립리프도 0.1 이다.
            case Blocks.LILY_PAD, Blocks.SNOW, Blocks.MOSS_BLOCK, Blocks.MOSS_CARPET,
                    Blocks.BIG_DRIPLEAF,
                    // [PALE-GARDEN] 창백한 이끼 셋도 바닐라 explosion_resistance 0.1 로
                    // 기존 이끼 계열과 같은 값이다(핀 §5).
                    Blocks.PALE_MOSS_BLOCK, Blocks.PALE_MOSS_CARPET,
                    Blocks.PALE_HANGING_MOSS -> 0.1;
            case Blocks.BLUE_ICE -> 2.8;
            // ── [MC-263] 26.3 어휘 확장으로 append 된 블록의 바닐라 explosion_resistance.
            // 값은 전부 고정 inner jar(net.minecraft.world.level.block.Blocks 정적 초기화)의
            // Properties.strength(...) / explosionResistance(...) 인자를 그대로 옮긴 것이다.
            case Blocks.BARRIER -> 3600000.8;
            case Blocks.END_PORTAL, Blocks.END_PORTAL_FRAME, Blocks.END_GATEWAY -> 3600000.0;
            case Blocks.CRYING_OBSIDIAN -> 1200.0;
            case Blocks.BELL -> 5.0;
            case Blocks.HOPPER -> 4.8; // strength(3.0F, 4.8F)
            // DropperBlock keeps DispenserBlock's strength(3.5F); CrafterBlock strength(1.5F, 3.5F).
            case Blocks.DISPENSER, Blocks.DROPPER, Blocks.CRAFTER -> 3.5;
            // 참나무 총칭 판자 계열(3.0)과 대나무 울타리(strength(2.0F, 3.0F))는 같은 값이다.
            case Blocks.OAK_SLAB, Blocks.BAMBOO_FENCE, Blocks.COCOA -> 3.0;
            // 통나무 계열 물성 그대로인 wood/stripped_wood 는 2.0 이다.
            case Blocks.MANGROVE_WOOD, Blocks.ACACIA_WOOD,
                    Blocks.STRIPPED_OAK_WOOD, Blocks.STRIPPED_SPRUCE_WOOD -> 2.0;
            case Blocks.WATER_CAULDRON -> 2.0; // ofLegacyCopy(cauldron) — 2.0
            case Blocks.STICKY_PISTON -> 1.5; // pistonProperties(): strength(1.5F)
            // 팻말·매달린 팟말·벽 군기는 전부 strength(1.0F) 다.
            case Blocks.OAK_WALL_SIGN, Blocks.OAK_HANGING_SIGN, Blocks.SPRUCE_HANGING_SIGN, Blocks.BAMBOO_HANGING_SIGN,
                    Blocks.WHITE_WALL_BANNER, Blocks.BROWN_WALL_BANNER, Blocks.MELON -> 1.0;
            // [WEBCRAFT] 흰 양털 계단은 바닐라 대응이 없는 원본 형상이라 양털의 0.8 을 쓴다.
            case Blocks.WHITE_WOOL_STAIRS -> 0.8;
            // 레버·압력판·버튼은 전부 strength(0.5F) 다(buttonProperties() 포함).
            case Blocks.LEVER, Blocks.STONE_PRESSURE_PLATE, Blocks.OAK_PRESSURE_PLATE,
                    Blocks.STONE_BUTTON, Blocks.OAK_BUTTON -> 0.5;
            // 양초는 candleProperties(): strength(0.1F), 큰 드립리프 줄기도 strength(0.1F) 다.
            case Blocks.CANDLE, Blocks.RED_CANDLE, Blocks.GREEN_CANDLE, Blocks.PURPLE_CANDLE,
                    Blocks.BROWN_CANDLE, Blocks.BIG_DRIPLEAF_STEM -> 0.1;
            // 바닐라 explosion_resistance 가 실제로 0 인 즉시파괴 장식·작물·묘목. 표에서 빠져
            // default 로 떨어진 것과 구분되도록 여기에 <b>명시</b>한다([BLAST-AUDIT]).
            case Blocks.AIR, Blocks.TALL_GRASS, Blocks.FLOWER_RED, Blocks.FLOWER_YELLOW,
                    Blocks.TORCH, Blocks.SUGARCANE, Blocks.MUSHROOM_BROWN, Blocks.MUSHROOM_RED,
                    Blocks.WALL_TORCH_N, Blocks.WALL_TORCH_E, Blocks.WALL_TORCH_S, Blocks.WALL_TORCH_W,
                    // 수중 식생·산호(부채형)·바다 피클
                    Blocks.KELP, Blocks.SEAGRASS, Blocks.CORAL, Blocks.SEA_PICKLE,
                    // TNT 는 0/0, 네더 포탈은 파괴 불가지만 explosion_resistance 자체는 0 이다
                    Blocks.TNT, Blocks.NETHER_PORTAL,
                    // 작물·줄기
                    Blocks.WHEAT_CROP, Blocks.CARROT_CROP, Blocks.POTATO_CROP,
                    Blocks.BEETROOT_CROP, Blocks.PUMPKIN_STEM,
                    // 묘목·번식체
                    Blocks.OAK_SAPLING, Blocks.BIRCH_SAPLING, Blocks.SPRUCE_SAPLING,
                    Blocks.JUNGLE_SAPLING, Blocks.ACACIA_SAPLING, Blocks.DARK_OAK_SAPLING,
                    Blocks.CHERRY_SAPLING, Blocks.MANGROVE_PROPAGULE,
                    Blocks.PALE_OAK_SAPLING, Blocks.POPLAR_SAPLING,
                    // [POPLAR] 붉은 관목도 바닐라 비고체 식물이라 폭발 저항 0 이다.
                    Blocks.RED_SHRUB,
                    // 동굴·습지 장식 식생
                    Blocks.FERN, Blocks.BUSH, Blocks.DEAD_BUSH, Blocks.HANGING_ROOTS,
                    Blocks.SPORE_BLOSSOM, Blocks.SMALL_DRIPLEAF, Blocks.AZALEA,
                    Blocks.FLOWERING_AZALEA, Blocks.CAVE_VINES, Blocks.CAVE_VINES_PLANT,
                    Blocks.FIRE, Blocks.WILDFLOWERS, Blocks.LEAF_LITTER,
                    Blocks.FIREFLY_BUSH, Blocks.CACTUS_FLOWER, Blocks.SHORT_DRY_GRASS,
                    Blocks.TALL_DRY_GRASS, Blocks.SHELF_MUSHROOM, Blocks.SWEET_BERRY_BUSH,
                    Blocks.GOLDEN_DANDELION, Blocks.FROGSPAWN,
                    Blocks.TORCHFLOWER_CROP, Blocks.TORCHFLOWER, Blocks.PITCHER_CROP,
                    // [PITCHER] 벌레잡이풀도 instabreak 식물이라 폭발 저항 0 이다.
                    Blocks.PITCHER_PLANT,
                    Blocks.HONEY_BLOCK,
                    // 팟말·매달린 팟말·화분은 바닐라 등록 물성이 0/0 이다.
                    Blocks.POPLAR_SIGN, Blocks.POPLAR_HANGING_SIGN,
                    Blocks.POTTED_POPLAR_SAPLING,
                    // 벚꽃 분재는 WebCraft 원본이며 바닐라 대응 블록인 화분(flower_pot)이 0 이다
                    Blocks.CHERRY_BONSAI -> 0.0;
            default -> coralFamilyResistance(id);
        };
    }

    /**
     * [CONCRETE] 콘크리트·가루·색 테라코타·유광 테라코타의 바닐라 explosion_resistance.
     *
     * <p>색 16개씩 58개를 case 로 나열하면 하나가 빠져도 조용히 0.0(= 폭발에 무저항)이 되므로
     * 계열 술어로 묻는다. 값은 전부 MC Java 1.21.4 다.
     * <ul>
     *   <li>concrete 1.8 — destroy_time 과 같다</li>
     *   <li>concrete_powder 0.5 — destroy_time 과 같다</li>
     *   <li>terracotta(색) 4.2 — destroy_time 1.25 보다 훨씬 높다(기존 여섯 색과 같은 값)</li>
     *   <li>glazed_terracotta 1.4 — 구우면 단단해지지만 폭발 저항은 4.2 → 1.4 로 <b>낮아진다</b></li>
     * </ul>
     * 기존 여섯 색 테라코타는 위 4.2 case 에 이미 있어 여기 도달하지 않는다.
     */
    private static double concreteFamilyResistance(int id) {
        if (Blocks.isConcrete(id)) return 1.8;
        if (Blocks.isConcretePowder(id)) return 0.5;
        if (Blocks.isGlazedTerracotta(id)) return 1.4;
        if (Blocks.isColoredTerracotta(id)) return 4.2;
        return 0.0;
    }

    /**
     * [CORAL-REEF] 산호 30종의 바닐라 explosion_resistance. 서른을 case 로 나열하면 하나가
     * 빠져도 조용히 0.0 이 되므로 {@link #concreteFamilyResistance} 와 같은 계열 술어로 묻는다.
     *
     * <ul>
     *   <li>coral_block · dead_coral_block 10종 — 6.0 ([A] destroy_time 1.5 / resistance 6.0,
     *       프리즈머린과 같은 값이다)</li>
     *   <li>coral · coral_fan 과 죽은 변형 20종 — 0.0 (즉시파괴 수중 식생)</li>
     * </ul>
     *
     * <p>산호가 아니면 콘크리트 계열 술어로 넘긴다 — 두 계열이 하나의 default 갈래를 순서대로
     * 나눠 쓰므로 어느 쪽도 상대의 값을 가리지 않는다.
     */
    private static double coralFamilyResistance(int id) {
        if (Blocks.isCoralBlock(id)) return 6.0;
        if (Blocks.isCoralPlantOrFan(id)) return 0.0;
        return concreteFamilyResistance(id);
    }
}
