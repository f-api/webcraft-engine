package com.gameexpert.engine;

import com.gameexpert.engine.crop.CropRules;
import com.gameexpert.engine.blocks.P6Rules;
import com.gameexpert.terrain.Blocks;

/**
 * [제공코드] 유체 ID 규칙(CONTRACT §2)과 레벨 계산 헬퍼.
 *
 * 물: 40=소스, 41~47=흐름 레벨 1~7(소스 인접이 7, 멀수록 낮음).
 * 용암: 48=소스, 49~51=흐름 레벨 1~3.
 */
public final class Fluids {

    private Fluids() {
    }

    public static final int AIR = 0;
    public static final int STONE = 1;
    public static final int COBBLE = 8;
    public static final int BEDROCK = 9;
    public static final int OBSIDIAN = 56;
    public static final int WATER_SOURCE = 40;
    public static final int LAVA_SOURCE = 48;

    public static final int NONE = 0;
    public static final int WATER = 1;
    public static final int LAVA = 2;

    public static final int WATER_MAX = 7;
    public static final int LAVA_MAX = 3;

    /** 바닐라 물 0.014/20TPS tick 을 WebCraft 10TPS 실시간에 맞춘 틱당 속도 가산값. */
    public static final double WATER_PUSH_PER_TICK = 0.028;
    /** 바닐라 오버월드 용암 0.002333…/20TPS tick 의 WebCraft 10TPS 환산값. */
    public static final double LAVA_PUSH_PER_TICK = 0.004666666666666667;

    /** 유체 흐름 계산용 블록 조회 포트. 호출부가 기존 월드 조회를 메서드 참조로 전달한다. */
    @FunctionalInterface
    public interface BlockLookup {
        int get(int x, int y, int z);
    }

    public static boolean isWater(int id) {
        return id == WATER_SOURCE || (id >= 41 && id <= 47);
    }

    public static boolean isLava(int id) {
        return id == LAVA_SOURCE || (id >= 49 && id <= 51);
    }

    public static boolean isFluid(int id) {
        return isWater(id) || isLava(id);
    }

    public static boolean isSource(int id) {
        return id == WATER_SOURCE || id == LAVA_SOURCE;
    }

    // 지형 v2 비고체 장식(§2 11~13): 몹/물리/유체가 통과. 14/16 은 고체, 15(자작 잎)는 잎과 동일 고체 취급.
    // 17 TORCH: 비고체(설치 전용 장식).
    // v3(§2): 18 SUGARCANE·24 MUSHROOM_BROWN·25 MUSHROOM_RED 는 비고체(통과). 19 CACTUS 는 고체.
    public static final int TALL_GRASS = 11;
    public static final int FLOWER_RED = 12;
    public static final int FLOWER_YELLOW = 13;
    public static final int TORCH = 17;
    public static final int SUGARCANE = 18;
    public static final int MUSHROOM_BROWN = 24;
    public static final int MUSHROOM_RED = 25;
    public static final int LILY_PAD = 39;
    public static final int KELP = 57;
    public static final int SEAGRASS = 58;
    public static final int CORAL = 59;
    public static final int SEA_PICKLE = 63;
    // 묘목(133/134)은 블록 ID 와 아이템 ID 를 공유한다. 설치 후 랜덤틱으로 나무가 되며,
    // 그 전까지는 풀·꽃과 같은 비고체 cross 장식이다(몹/물리/유체가 통과).
    public static final int OAK_SAPLING = 133;
    public static final int BIRCH_SAPLING = 134;
    // 벽토치(§2 개정 52~55): 바닥 토치(17)와 동일한 비고체 장식(부착 벽 방향만 id 로 구분). 통과 대상.
    public static final int WALL_TORCH_MIN = 52;
    public static final int WALL_TORCH_MAX = 55;

    // 비고체 구조물: 31 LADDER·39 LILY_PAD·95 RAIL 은 몹/물리/유체가 통과(장식과 달리
    // 즉시파괴·cross 렌더 성질은 아니므로 isDecoration 과 구분). solid 판정에서만 공기처럼 취급.
    public static final int LADDER = 31;
    public static final int NETHER_PORTAL = Blocks.NETHER_PORTAL;

    /** 비고체 장식 블록(식물·토치·벽토치): 공기처럼 통과. */
    public static boolean isDecoration(int id) {
        return id == TALL_GRASS || id == FLOWER_RED || id == FLOWER_YELLOW || id == TORCH
                || id == SUGARCANE || id == MUSHROOM_BROWN || id == MUSHROOM_RED
                || id == KELP || id == SEAGRASS || id == CORAL || id == SEA_PICKLE
                || id == OAK_SAPLING || id == BIRCH_SAPLING
                || id == Blocks.DEAD_BUSH
                || id == Blocks.BAMBOO || id == Blocks.VINE
                || id == Blocks.FERN || id == Blocks.BUSH
                || id == Blocks.GLOW_LICHEN || id == Blocks.HANGING_ROOTS
                || id == Blocks.SPORE_BLOSSOM || id == Blocks.SMALL_DRIPLEAF
                || id == Blocks.AZALEA || id == Blocks.FLOWERING_AZALEA
                || P6Rules.isSpeleothem(id)
                || id == Blocks.CAVE_VINES || id == Blocks.CAVE_VINES_PLANT
                || id == Blocks.MANGROVE_PROPAGULE || id == Blocks.FIRE
                || (id >= Blocks.SMALL_AMETHYST_BUD && id <= Blocks.AMETHYST_CLUSTER)
                || (id >= Blocks.SPRUCE_SAPLING && id <= Blocks.CHERRY_SAPLING)
                // 밀 작물(79)은 묘목·풀과 같은 cross 장식이다. 등록하지 않으면 서버가 고체로 판정해
                // 플레이어가 밭 위를 걸어다닐 수 없다(farming 트랙이 소유 범위 밖이라 남긴 항목).
                || id == Blocks.WHEAT_CROP
                || (id >= WALL_TORCH_MIN && id <= WALL_TORCH_MAX)
                // [CORAL-REEF] 산호 식물형·부채 20종(1890~1909)도 통과 가능한 cross 장식이다.
                // 산호 **블록** 10종(1880~1889)은 풀 큐브 고체라 여기 들어가지 않는다.
                || Blocks.isCoralPlantOrFan(id);
    }

    /** 물 셀을 대신 표현하는 수중 장식. 충돌은 통과하지만 유체가 다시 치환하지는 않는다. */
    public static boolean isSubmergedDecoration(int id) {
        // [CORAL-REEF] 산호 식물형·부채 20종도 같은 단일-ID 수중 식생이다(산호 블록은 아니다).
        return id == KELP || id == SEAGRASS || id == Blocks.KELP_PLANT || id == Blocks.TALL_SEAGRASS
                || id == CORAL || id == SEA_PICKLE
                || Blocks.isCoralPlantOrFan(id);
    }

    public static boolean isWaterMedium(int id) {
        return isWater(id) || isSubmergedDecoration(id);
    }

    /** Stateful copper blocks carry their contained source water in bit 7. */
    public static boolean isWaterMedium(int id, int state) {
        return isWaterMedium(id)
                || BuildingBlockRules.canAcceptWater(id, state) && (state & 0x80) != 0;
    }

    /** 단일 ID로 수원을 겸하는 수중 장식은 제거되면 원래 수원을 드러낸다. */
    public static int blockAfterRemoval(int id) {
        return isSubmergedDecoration(id) ? WATER_SOURCE : AIR;
    }

    /**
     * 바닐라 {@code SimpleWaterloggedBlock}(waterlogged 속성을 가진 블록)들. 물이 이 칸으로 퍼지면
     * {@code FlowingFluid#spreadTo} 가 {@code LiquidBlockContainer#placeLiquid} 로 물을 블록 **안에**
     * 넣기 때문에 블록이 파괴되지도, 드랍되지도 않는다. 이 엔진에는 이 단일-ID 장식들의 waterlogged
     * 상태가 없으므로 물은 칸을 그대로 두고 지나간다(파괴·드랍 금지). 용암은 waterlog 할 수 없어
     * 기존대로 치환한다.
     *
     * <p>포함: 이끼광원·매달린뿌리·작은드립리프·뾰족한종유석·자수정 싹/군집·맹그로브 씨앗·사다리.
     * 제외(이유): 다시마·해초·산호부채·바다피클은 이미 {@link #isSubmergedDecoration} 로 치환 대상에서
     * 빠져 있고, 레일·수련잎·덩굴·동굴덩굴·대나무·횃불·묘목·풀·불은 바닐라에서 waterlogged 속성이
     * 없으며, 유황가시는 이 저장소의 창작 블록이라 바닐라 waterlog 규칙이 없다.
     */
    public static boolean isWaterloggable(int id) {
        return id == Blocks.GLOW_LICHEN || id == Blocks.HANGING_ROOTS
                || id == Blocks.SMALL_DRIPLEAF || id == Blocks.POINTED_DRIPSTONE
                || id == Blocks.MANGROVE_PROPAGULE || id == LADDER
                || (id >= Blocks.SMALL_AMETHYST_BUD && id <= Blocks.AMETHYST_CLUSTER);
    }

    /** 통과 가능한 구조물(사다리·수련잎·레일·네더 포털): solid 판정에서 공기처럼 통과. */
    public static boolean isPassableStructure(int id) {
        return id == LADDER || id == LILY_PAD || id == Blocks.RAIL || id == NETHER_PORTAL;
    }

    /** 공기·유체·비고체 장식·통과 구조물이 아니면 solid(유체가 통과 못 함). */
    public static boolean isSolid(int id) {
        // Light, collision and surface scans ask this per cell; the rule below walks several shape tables.
        return id >= 0 && id < SolidTable.IS_SOLID.length ? SolidTable.IS_SOLID[id] : isSolidById(id);
    }

    private static final class SolidTable {
        private static final boolean[] IS_SOLID = new boolean[com.gameexpert.terrain.Blocks.BLOCK_ID_TABLE_CAPACITY];
        static {
            for (int id = 0; id < IS_SOLID.length; id++) IS_SOLID[id] = isSolidById(id);
        }
    }

    private static boolean isSolidById(int id) {
        if (com.gameexpert.engine.redstone.RedstoneShapes.has(id) && !com.gameexpert.engine.redstone.RedstoneShapes.nonCollision(id)) return true;
        if (com.gameexpert.engine.redstone.RedstoneShapes.nonCollision(id)) return false;
        return id != AIR && !isFluid(id) && !CropRules.isCrop(id)
                && !isDecoration(id) && !isPassableStructure(id);
    }

    /** 공기처럼 유체가 차지할 수 있는 칸. 물을 겸하는 수중 장식은 통과 가능해도 치환 대상에서는 뺀다. */
    public static boolean isReplaceable(int id) {
        return id == AIR || CropRules.isCrop(id)
                || (isDecoration(id) && !isSubmergedDecoration(id))
                || (isPassableStructure(id) && id != NETHER_PORTAL);
    }

    public static int typeOf(int id) {
        if (isWater(id)) {
            return WATER;
        }
        if (isLava(id)) {
            return LAVA;
        }
        return NONE;
    }

    public static int maxLevel(int type) {
        if (type == WATER) {
            return WATER_MAX;
        }
        return type == LAVA ? LAVA_MAX : 0;
    }

    /** 흐름 레벨: 소스는 최대치, 흐름은 그 레벨(1~max). 유체 아니면 0. */
    public static int levelOf(int id) {
        if (id == WATER_SOURCE) {
            return WATER_MAX;
        }
        if (id >= 41 && id <= 47) {
            return id - 40;
        }
        if (id == LAVA_SOURCE) {
            return LAVA_MAX;
        }
        if (id >= 49 && id <= 51) {
            return id - 48;
        }
        return 0;
    }

    /** 수평으로 밀어내는 힘: 소스는 max+1(인접 흐름=max), 흐름 L은 L(인접 흐름=L−1). */
    public static int feedLevel(int id) {
        if (id == WATER_SOURCE) {
            return WATER_MAX + 1;
        }
        if (id >= 41 && id <= 47) {
            return id - 40;
        }
        if (id == LAVA_SOURCE) {
            return LAVA_MAX + 1;
        }
        if (id >= 49 && id <= 51) {
            return id - 48;
        }
        return 0;
    }

    /** 흐름 블록 ID: 레벨 1~max → 물 41~47 / 용암 49~51. */
    public static int flowId(int type, int level) {
        return type == WATER ? 40 + level : 48 + level;
    }

    /**
     * 점유 셀의 같은 타입 유체 레벨 기울기를 정규화해 {@code out[0..2]}에 기록한다.
     * 유체가 아니거나 정지/균형 상태면 false를 반환한다. 호출부 버퍼를 재사용하므로 할당이 없다.
     *
     * <p>수평 이웃은 낮은 레벨 쪽으로 향하고, 다른 타입 유체와 고체는 경계로 무시한다. 비어 있는
     * 이웃과 아래로 떨어지는 같은 유체 이웃은 열린 유출 경로(level 0)로 본다. 현재 셀 아래가
     * 비었거나 같은 타입 비소스 흐름이면 FluidSimulator의 낙하 열 표현과 동일하게 아래 성분을 더한다.
     */
    public static boolean flowVector(BlockLookup blocks, int x, int y, int z, double[] out) {
        int currentId = blocks.get(x, y, z);
        int type = typeOf(currentId);
        if (type == NONE) {
            return false;
        }

        int current = feedLevel(currentId);
        double dx = flowDelta(blocks, x + 1, y, z, type, current)
                - flowDelta(blocks, x - 1, y, z, type, current);
        double dz = flowDelta(blocks, x, y, z + 1, type, current)
                - flowDelta(blocks, x, y, z - 1, type, current);
        double dy = canFallThrough(blocks.get(x, y - 1, z), type) ? -1.0 : 0.0;
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length <= 1e-12) {
            return false;
        }
        out[0] = dx / length;
        out[1] = dy / length;
        out[2] = dz / length;
        return true;
    }

    /** 현재 레벨에서 해당 이웃으로 향하는 양의 기울기. 반대편 차감과 합쳐 signed 성분이 된다. */
    private static int flowDelta(BlockLookup blocks, int x, int y, int z, int type, int current) {
        int neighbor = blocks.get(x, y, z);
        int neighborType = typeOf(neighbor);
        if (neighborType == type) {
            return current - (canFallThrough(blocks.get(x, y - 1, z), type) ? 0 : feedLevel(neighbor));
        }
        if (neighborType == NONE && isReplaceable(neighbor)) {
            return current;
        }
        return 0;
    }

    private static boolean canFallThrough(int below, int type) {
        return isReplaceable(below) || (typeOf(below) == type && !isSource(below));
    }

    public static double pushPerTick(int fluidType) {
        return fluidType == WATER ? WATER_PUSH_PER_TICK
                : fluidType == LAVA ? LAVA_PUSH_PER_TICK : 0.0;
    }
}
