package com.gameexpert.engine.blocks;

import com.gameexpert.engine.SupportRules;
import com.gameexpert.terrain.Blocks;
import java.util.function.IntUnaryOperator;

/**
 * [VOID-END] 후렴 식물·꽃의 생존·연결 규칙(핀 26.3 {@code ChorusPlantBlock}/{@code ChorusFlowerBlock}).
 * 정적판 {@code client/src/world/dimensions/voidEnd/VoidEndBlockRules.ts} 와 같은 판정이다.
 *
 * <p>비상주 이웃(음수 ID)은 지지로 본다 — Java 이웃 갱신의 비상주 보존 규약과 같다.
 */
public final class VoidEndBlockRules {
    /** 후렴 식물 연결 비트. 수평은 담장/철창의 NESW(1,2,4,8), 그 위 up=16, down=32. */
    public static final int CHORUS_PLANT_NORTH = 1;
    public static final int CHORUS_PLANT_EAST = 2;
    public static final int CHORUS_PLANT_SOUTH = 4;
    public static final int CHORUS_PLANT_WEST = 8;
    public static final int CHORUS_PLANT_UP = 16;
    public static final int CHORUS_PLANT_DOWN = 32;
    /** 후렴 꽃 AGE 0..5 (5 = 시든 꽃). */
    public static final int CHORUS_FLOWER_MAX_AGE = 5;
    /** 엔드 막대 6방향 facing: 0 up · 1 down · 2 N · 3 E · 4 S · 5 W(번개막대·통 어휘). */
    public static final int END_ROD_FACING_UP = 0;
    public static final int END_ROD_FACING_DOWN = 1;
    /** 엔드 관문 state: 0 생성 귀환 관문(정확 출구 100,50,0) · 1 고리 관문 · 2 출구 관문. 출구는 권위의 관문 블록 엔티티({@code EndGatewaySystem})가 소유한다. */
    public static final int GATEWAY_RETURN_TO_CENTER = 0;
    public static final int GATEWAY_OUTBOUND = 1;
    public static final int GATEWAY_EXIT_RETURN = 2;

    /** 바닐라 {@code Direction.Plane.HORIZONTAL} 순서 N, E, S, W. */
    private static final int[][] HORIZONTAL = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};
    /** 6방향 facing(0 +Y · 1 −Y · 2 −Z · 3 +X · 4 +Z · 5 −X)의 반대 방향과 단위 벡터. */
    private static final int[] OPPOSITE_FACING = {1, 0, 4, 5, 2, 3};
    private static final int[][] FACING_VECTOR = {{0, 1, 0}, {0, -1, 0}, {0, 0, -1}, {1, 0, 0}, {0, 0, 1}, {-1, 0, 0}};
    private static final int SALT_CHORUS_DROP = 0x5001;

    private VoidEndBlockRules() {
    }

    public static boolean requiresSupport(int blockId) {
        return blockId == Blocks.CHORUS_PLANT || blockId == Blocks.CHORUS_FLOWER;
    }

    private static boolean isChorus(int blockId) {
        return blockId == Blocks.CHORUS_PLANT || blockId == Blocks.CHORUS_FLOWER;
    }

    /** {@code #supports_chorus_plant} = {@code #supports_chorus_flower} = 엔드 돌. */
    private static boolean supportsChorus(int blockId) {
        return blockId == Blocks.END_STONE;
    }

    /**
     * {@code ChorusPlantBlock#canSurvive}: 위·아래가 모두 차 있으면 수평 식물 이웃에 기대지 못한다.
     * 수평 식물 이웃의 아래가 식물이거나 엔드 돌이면 산다. 그 밖에는 아래가 식물·엔드 돌이어야 한다.
     */
    public static boolean chorusPlantSurvives(int x, int y, int z, SupportRules.BlockLookup lookup) {
        int below = lookup.getBlock(x, y - 1, z);
        int above = lookup.getBlock(x, y + 1, z);
        if (below < 0 || above < 0) return true;
        boolean verticalFilled = above != Blocks.AIR && below != Blocks.AIR;
        for (int[] direction : HORIZONTAL) {
            int neighbor = lookup.getBlock(x + direction[0], y, z + direction[1]);
            if (neighbor < 0) return true;
            if (neighbor != Blocks.CHORUS_PLANT) continue;
            if (verticalFilled) return false;
            int neighborBelow = lookup.getBlock(x + direction[0], y - 1, z + direction[1]);
            if (neighborBelow < 0 || neighborBelow == Blocks.CHORUS_PLANT || supportsChorus(neighborBelow)) {
                return true;
            }
        }
        return below == Blocks.CHORUS_PLANT || supportsChorus(below);
    }

    /**
     * {@code ChorusFlowerBlock#canSurvive}: 아래가 식물·엔드 돌이면 산다. 아래가 공기이면 수평 이웃
     * 중 정확히 하나가 식물이고 나머지가 공기일 때만 산다.
     */
    public static boolean chorusFlowerSurvives(int x, int y, int z, SupportRules.BlockLookup lookup) {
        int below = lookup.getBlock(x, y - 1, z);
        if (below < 0 || below == Blocks.CHORUS_PLANT || supportsChorus(below)) return true;
        if (below != Blocks.AIR) return false;
        boolean plant = false;
        for (int[] direction : HORIZONTAL) {
            int neighbor = lookup.getBlock(x + direction[0], y, z + direction[1]);
            if (neighbor < 0) return true;
            if (neighbor == Blocks.CHORUS_PLANT) {
                if (plant) return false;
                plant = true;
            } else if (neighbor != Blocks.AIR) {
                return false;
            }
        }
        return plant;
    }

    public static boolean isSupported(int blockId, int x, int y, int z, SupportRules.BlockLookup lookup) {
        if (blockId == Blocks.CHORUS_PLANT) return chorusPlantSurvives(x, y, z, lookup);
        if (blockId == Blocks.CHORUS_FLOWER) return chorusFlowerSurvives(x, y, z, lookup);
        return true;
    }

    /** {@code ChorusPlantBlock.getStateWithConnections}: 수평·위는 식물/꽃, 아래는 식물/꽃/엔드 돌. */
    public static int chorusPlantConnections(int x, int y, int z, SupportRules.BlockLookup lookup) {
        int state = 0;
        if (isChorus(lookup.getBlock(x, y, z - 1))) state |= CHORUS_PLANT_NORTH;
        if (isChorus(lookup.getBlock(x + 1, y, z))) state |= CHORUS_PLANT_EAST;
        if (isChorus(lookup.getBlock(x, y, z + 1))) state |= CHORUS_PLANT_SOUTH;
        if (isChorus(lookup.getBlock(x - 1, y, z))) state |= CHORUS_PLANT_WEST;
        if (isChorus(lookup.getBlock(x, y + 1, z))) state |= CHORUS_PLANT_UP;
        int below = lookup.getBlock(x, y - 1, z);
        if (isChorus(below) || supportsChorus(below)) state |= CHORUS_PLANT_DOWN;
        return state;
    }

    /**
     * {@code ChorusPlantBlock.updateShape}: 살 수 없는 식물은 연결을 고치지 않고(다음 틱에 부서진다)
     * 그대로 둔다. 살 수 있으면 {@link #chorusPlantConnections} 가 새 state 다.
     */
    public static int refreshedChorusPlantState(int old, int x, int y, int z, SupportRules.BlockLookup lookup) {
        if (!chorusPlantSurvives(x, y, z, lookup)) return old;
        return chorusPlantConnections(x, y, z, lookup);
    }

    /**
     * {@code EndRodBlock.getStateForPlacement}: FACING 은 클릭한 면이지만, 붙는 블록(클릭한 칸 =
     * FACING 반대쪽 이웃)이 같은 FACING 의 엔드 막대이면 반대로 뒤집는다.
     *
     * @param facing 클릭한 면으로 정한 요청 facing(0..5, 범위 밖은 up)
     */
    public static int endRodPlacementFacing(int facing, int x, int y, int z,
            SupportRules.BlockLookup blocks, SupportRules.StateLookup states) {
        int requested = facing >= 0 && facing <= 5 ? facing : END_ROD_FACING_UP;
        int[] toward = FACING_VECTOR[OPPOSITE_FACING[requested]];
        int ax = x + toward[0];
        int ay = y + toward[1];
        int az = z + toward[2];
        if (blocks.getBlock(ax, ay, az) == Blocks.END_ROD
                && (states.getState(ax, ay, az, Blocks.END_ROD) & 0x07) == requested) {
            return OPPOSITE_FACING[requested];
        }
        return requested;
    }

    /**
     * 후렴 식물이 지지를 잃어 부서질 때의 드랍. {@code loot_table/blocks/chorus_plant} 는 후렴과
     * set_count uniform(0,1)(= {@code Mth.nextInt(random, 0, 1)}, 50%)이다. 연쇄에는 채굴 난수가
     * 없으므로 월드 시드·좌표에서 파생한 결정적 난수를 쓴다(정적판 {@code chorusPlantSupportLossDrop}
     * 과 같은 해시 — 아래 {@link #hash}).
     */
    public static int chorusPlantSupportLossDrop(int worldSeed, int x, int y, int z) {
        int h = hash(worldSeed ^ y * 0x632be5ab, x, z, SALT_CHORUS_DROP);
        return (h >>> 31) != 0 ? Blocks.CHORUS_FRUIT : Blocks.AIR;
    }

    /**
     * 엔드 차원 결정적 난수의 단일 정본: 시드·두 정수 좌표·용도 salt 의 32비트 해시(murmur3 fmix 세 번).
     * 생성기 {@code VoidEndGenerator.hash} 가 이것을 쓰고, 정적판 {@code voidEndHash} 가 같은 값을 낸다.
     */
    public static int hash(int seed, int a, int b, int salt) {
        int h = fmix(seed ^ salt * 0x27d4eb2d);
        h = fmix(h ^ a * 0x9e3779b1);
        h = fmix(h ^ b * 0x85ebca77);
        return h;
    }

    private static int fmix(int value) {
        int h = value;
        h ^= h >>> 16;
        h *= 0x85ebca6b;
        h ^= h >>> 13;
        h *= 0xc2b2ae35;
        h ^= h >>> 16;
        return h;
    }

    /**
     * {@code EndGatewayBlock.entityInside} 의 접촉 판정: 플레이어 상자(발 x±0.3, y..y+1.8, z±0.3)가 덮는 칸 중
     * y → z → x 순서로 처음 만난 엔드 관문 칸. 없으면 null. 정적판 {@code endGatewayContact} 와 같은 순서다.
     */
    public static int[] endGatewayContact(double x, double y, double z, SupportRules.BlockLookup lookup) {
        int minX = (int) Math.floor(x - 0.3);
        int maxX = (int) Math.floor(x + 0.3);
        int minY = (int) Math.floor(y);
        int maxY = (int) Math.floor(y + 1.8);
        int minZ = (int) Math.floor(z - 0.3);
        int maxZ = (int) Math.floor(z + 0.3);
        for (int py = minY; py <= maxY; py++) {
            for (int pz = minZ; pz <= maxZ; pz++) {
                for (int px = minX; px <= maxX; px++) {
                    if (lookup.getBlock(px, py, pz) == Blocks.END_GATEWAY) return new int[] {px, py, pz};
                }
            }
        }
        return null;
    }

    /** 성장 규칙이 쓰는 월드. setBlock 은 블록과 state 를 함께 쓴다. */
    public interface GrowthWorld {
        int getBlock(int x, int y, int z);

        void setBlock(int x, int y, int z, int blockId, int state);
    }

    private static boolean isEmpty(GrowthWorld world, int x, int y, int z, int maxY) {
        if (y > maxY || y < Blocks.MIN_Y) return true;
        return world.getBlock(x, y, z) == Blocks.AIR;
    }

    private static boolean allNeighborsEmpty(GrowthWorld world, int x, int y, int z, int except, int maxY) {
        for (int direction = 0; direction < 4; direction++) {
            if (direction == except) continue;
            if (!isEmpty(world, x + HORIZONTAL[direction][0], y, z + HORIZONTAL[direction][1], maxY)) return false;
        }
        return true;
    }

    /**
     * 핀 26.3 {@code ChorusFlowerBlock.randomTick} 의 줄 단위 이식. 정적판 {@code chorusFlowerRandomTick}
     * 과 같은 난수 소비 순서다: 줄기 높이 판정의 {@code nextInt(4|5)}(높이 ≥ 2 일 때만), 옆 가지
     * 개수 {@code nextInt(4)}, 가지마다 방향 {@code nextInt(4)}(N, E, S, W).
     *
     * <ul>
     * <li>위 칸이 비어 있고 {@code maxY} 이하일 때만, age &lt; 5 일 때만 자란다.</li>
     * <li>아래가 엔드 돌이거나 공기이면, 또는 아래 식물 줄기 높이 j 가 j &lt; 2 이거나
     * j ≤ nextInt(엔드 돌에 닿으면 5, 아니면 4) 이면 위로 자랄 후보다. 위 칸의 수평 이웃과 두 칸 위가
     * 비어 있으면 이 칸은 식물이 되고 위 칸에 같은 age 의 꽃이 선다.</li>
     * <li>아니면 age &lt; 4 에서 nextInt(4)(+1 엔드 돌 줄기) 번 옆 가지를 시도한다. 빈 칸·그 아래가
     * 비고 반대쪽 외 수평 이웃이 빈 방향에 age+1 꽃을 놓는다. 하나라도 놓았으면 이 칸은 식물,
     * 아니면 시든 꽃(age 5)이다. age ≥ 4 는 곧바로 시든 꽃이다.</li>
     * </ul>
     *
     * @return 바꾼 칸 수(0 이면 아무것도 바꾸지 않았다)
     */
    public static int chorusFlowerRandomTick(int x, int y, int z, int age, GrowthWorld world,
            IntUnaryOperator nextInt, int maxY) {
        if (!isEmpty(world, x, y + 1, z, maxY) || y + 1 > maxY) return 0;
        if (age >= CHORUS_FLOWER_MAX_AGE) return 0;
        boolean grow = false;
        boolean endStoneStem = false;
        int below = world.getBlock(x, y - 1, z);
        if (supportsChorus(below)) {
            grow = true;
        } else if (below == Blocks.CHORUS_PLANT) {
            int height = 1;
            for (int i = 0; i < 4; i++) {
                int block = world.getBlock(x, y - (height + 1), z);
                if (block == Blocks.CHORUS_PLANT) {
                    height++;
                } else {
                    if (supportsChorus(block)) endStoneStem = true;
                    break;
                }
            }
            if (height < 2 || height <= nextInt.applyAsInt(endStoneStem ? 5 : 4)) grow = true;
        } else if (below == Blocks.AIR) {
            grow = true;
        }
        if (grow && allNeighborsEmpty(world, x, y + 1, z, -1, maxY) && isEmpty(world, x, y + 2, z, maxY)) {
            world.setBlock(x, y, z, Blocks.CHORUS_PLANT, chorusPlantConnections(x, y, z, world::getBlock));
            world.setBlock(x, y + 1, z, Blocks.CHORUS_FLOWER, age);
            return 2;
        }
        if (age < 4) {
            int attempts = nextInt.applyAsInt(4);
            if (endStoneStem) attempts++;
            int changed = 0;
            for (int i = 0; i < attempts; i++) {
                int direction = nextInt.applyAsInt(4);
                int nx = x + HORIZONTAL[direction][0];
                int nz = z + HORIZONTAL[direction][1];
                if (isEmpty(world, nx, y, nz, maxY) && isEmpty(world, nx, y - 1, nz, maxY)
                        && allNeighborsEmpty(world, nx, y, nz, direction + 2 & 3, maxY)) {
                    world.setBlock(nx, y, nz, Blocks.CHORUS_FLOWER, age + 1);
                    changed++;
                }
            }
            if (changed > 0) {
                world.setBlock(x, y, z, Blocks.CHORUS_PLANT, chorusPlantConnections(x, y, z, world::getBlock));
                return changed + 1;
            }
        }
        world.setBlock(x, y, z, Blocks.CHORUS_FLOWER, CHORUS_FLOWER_MAX_AGE);
        return 1;
    }
}
