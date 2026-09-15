package com.gameexpert.engine;

import java.util.ArrayList;
import java.util.List;

import com.gameexpert.terrain.Blocks;

/**
 * [BLOCK-SHAPES] 바닐라 26.3 모델 블록 열 종의 형상과 state 어휘: 양조대·호퍼·가마솥(물 가마솥)·
 * 독서대·숫돌·퇴비통·석재 절단기·스니퍼 알·종. 클라 정본 {@code client/src/world/BlockModelShapes.ts}
 * 와 같은 표이며, 두 권위의 기대값은 고정 서버가 모든 state 에 대해 낸
 * {@code BlockState#getShape/#getCollisionShape/#getInteractionShape/#getBlockSupportShape}
 * ({@code original/block-shape-golden.json}) 하나로 못박힌다.
 *
 * <p>javap 근거({@code net.minecraft.world.level.block}, 26.3-snapshot-7):
 * <ul>
 *   <li>{@code Block.column(sx, sz, y0, y1)} = box(8 - sx/2, y0, 8 - sz/2, 8 + sx/2, y1, 8 + sz/2),
 *       {@code Block.boxZ(sx, y0, y1, z0, z1)} = box(8 - sx/2, y0, z0, 8 + sx/2, y1, z1),
 *       {@code Shapes.rotateHorizontal} 은 북향 기준이고 동향 = (x, z) → (16 - z, x).</li>
 *   <li>{@code BrewingStandBlock.SHAPE = or(column(14, 0, 2), column(2, 0, 14))}.</li>
 *   <li>{@code HopperBlock}: 몸통 = join(or(column(16, 10, 16), column(8, 4, 10)),
 *       column(12, 11, 16), ONLY_FIRST), 주둥이 = boxZ(4, 4, 8, 0, 8) 을 (8, 6, 8) 중심
 *       {@code Shapes.rotateAll} 한 것(셀 밖은 잘린다: 아래 = box(6, 0, 6, 10, 4, 10)).
 *       상호작용 = column(12, 11, 16) (+ 옆 주둥이면 boxZ(4, 8, 10, 0, 4) 회전).</li>
 *   <li>{@code AbstractCauldronBlock.SHAPE = join(block(), or(column(16, 8, 0, 3),
 *       column(8, 16, 0, 3), column(12, 0, 3), SHAPE_INSIDE = column(12, 4, 16)), ONLY_FIRST)},
 *       상호작용 = SHAPE_INSIDE.</li>
 *   <li>{@code LecternBlock.SHAPE_COLLISION = or(column(16, 0, 2), column(8, 2, 14))}, 윤곽은
 *       boxZ(16, 10, 14, 1, 5.333333) · boxZ(16, 12, 16, 5.333333, 9.666667) ·
 *       boxZ(16, 14, 18, 9.666667, 14) 를 더해 수평 회전한다.</li>
 *   <li>{@code GrindstoneBlock}: 벽/북 = or(box(2, 6, 7, 4, 10, 16), box(2, 5, 3, 4, 11, 9)) 을
 *       INVERT_X 로 복제 + boxZ(8, 2, 14, 0, 12); {@code Shapes.rotateAttachFace} 가 바닥·천장을 만든다.</li>
 *   <li>{@code ComposterBlock.SHAPES[i] = join(block(), column(12, max(2, 1 + 2i), 16), ONLY_FIRST)}
 *       (i = 0..7, SHAPES[8] = SHAPES[7]), getCollisionShape = SHAPES[0], getInteractionShape = block().</li>
 *   <li>{@code StonecutterBlock.SHAPE = column(16, 0, 9)}, {@code SnifferEggBlock.SHAPE =
 *       box(1, 0, 2, 15, 16, 14)}.</li>
 *   <li>{@code BellBlock}: BELL_SHAPE = or(column(6, 6, 13), column(8, 4, 6)); 천장은
 *       column(2, 13, 16), 바닥은 축별 cube(16, 16, 8), 두 벽은 축별 column(2, 16, 13, 15),
 *       한 벽은 북향 boxZ(2, 13, 15, 0, 13) 을 더한다.</li>
 * </ul>
 *
 * <h2>state 어휘</h2>
 * 모든 블록에서 state 0 은 이 어휘 이전 저장분이 뜻하던 바닐라 기본 상태다.
 * 호퍼 facing[0..2](0 down · 1 N · 2 E · 3 S · 4 W) + disabled[3], 독서대 facing[0..1] + has_book[2],
 * 숫돌 facing[0..1] + face[2..3](0 floor · 1 wall · 2 ceiling), 종 facing[0..1] +
 * attachment[2..3](0 floor · 1 ceiling · 2 single_wall · 3 double_wall), 양조대 has_bottle_0..2[0..2],
 * 물 가마솥 level[0..1](1..3, 0 은 옛 저장분의 3), 석재 절단기 facing[0..1], 퇴비통 level 0..8,
 * 스니퍼 알 hatch 0..2.
 */
public final class BlockModelShapes {

    public static final int LECTERN_HAS_BOOK = 0x04;
    public static final int GRINDSTONE_FACE_SHIFT = 2;
    public static final int BELL_ATTACHMENT_SHIFT = 2;
    public static final int BREWING_STAND_BOTTLE_MASK = 0x07;
    public static final int WATER_CAULDRON_LEVEL_MASK = 0x03;

    public static final int ATTACH_FLOOR = 0;
    public static final int ATTACH_WALL = 1;
    public static final int ATTACH_CEILING = 2;
    public static final int BELL_FLOOR = 0;
    public static final int BELL_CEILING = 1;
    public static final int BELL_SINGLE_WALL = 2;
    public static final int BELL_DOUBLE_WALL = 3;

    private static final double[][] NONE = {};
    private static final double[][] FULL = {{0, 0, 0, 16, 16, 16}};
    /**
     * [PITCHER] {@code PitcherCropBlock.makeShapes}: height = (age 0 ? 4 : 6) + {0, 9, 11, 22, 26}[age],
     * width 6 at age 0 else 10; the lower half is {@code column(width, -1, min(16, height - 1))}, the upper
     * {@code column(width, 0, max(0, height - 17))}. Collision is the lower half's {@code SHAPE_BULB =
     * column(6, -1, 3)} at age 0 and {@code SHAPE_CROP = column(10, -1, 5)} otherwise; the upper half has
     * none. The pitcher plant ({@code TallFlowerBlock}) keeps the full-cube outline and no collision.
     * State bits: {@link PitcherRules}. Pinned by the oracle rows in block-shape-golden.json.
     */
    private static final double[][][] PITCHER_CROP_OUTLINES = new double[16][][];
    private static final double[][][] PITCHER_CROP_COLLISIONS = new double[16][][];

    static {
        int[] plantHeights = {0, 9, 11, 22, 26};
        for (int state = 0; state < 16; state++) {
            int age = Math.min(PitcherRules.MAX_AGE, PitcherRules.age(state));
            int height = (age == 0 ? 4 : 6) + plantHeights[age];
            int width = age == 0 ? 6 : 10;
            boolean upper = PitcherRules.isUpper(state);
            int top = upper ? Math.max(0, height - 17) : Math.min(16, height - 1);
            PITCHER_CROP_OUTLINES[state] = top <= (upper ? 0 : -1)
                    ? NONE : new double[][] {column(width, width, upper ? 0 : -1, top)};
            PITCHER_CROP_COLLISIONS[state] = upper ? NONE
                    : new double[][] {age == 0 ? column(6, 6, -1, 3) : column(10, 10, -1, 5)};
        }
    }

    private static final double[][] BREWING_STAND_SHAPE = {column(14, 14, 0, 2), column(2, 2, 0, 14)};

    private static final double[][] HOPPER_BASE = {
        {0, 10, 0, 16, 11, 16},
        {0, 11, 0, 2, 16, 16}, {14, 11, 0, 16, 16, 16},
        {2, 11, 0, 14, 16, 2}, {2, 11, 14, 14, 16, 16},
        column(8, 8, 4, 10),
    };
    private static final double[] HOPPER_SPOUT_NORTH = {6, 4, 0, 10, 8, 4};
    private static final double[][][] HOPPER_SHAPES = new double[5][][];
    private static final double[][][] HOPPER_INTERACTION = new double[5][][];

    private static final double[][] CAULDRON_SHAPE = {
        {0, 3, 0, 2, 16, 16}, {14, 3, 0, 16, 16, 16}, {2, 3, 0, 14, 16, 2}, {2, 3, 14, 14, 16, 16},
        {2, 3, 2, 14, 4, 14},
        {0, 0, 0, 4, 3, 2}, {0, 0, 2, 2, 3, 4}, {12, 0, 0, 16, 3, 2}, {14, 0, 2, 16, 3, 4},
        {0, 0, 14, 4, 3, 16}, {0, 0, 12, 2, 3, 14}, {12, 0, 14, 16, 3, 16}, {14, 0, 12, 16, 3, 14},
    };
    private static final double[][] CAULDRON_INTERACTION = {column(12, 12, 4, 16)};

    private static final double[][] LECTERN_COLLISION = {column(16, 16, 0, 2), column(8, 8, 2, 14)};
    private static final double[][][] LECTERN_OUTLINES = new double[4][][];

    private static final double[][] GRINDSTONE_WALL_NORTH = {
        {2, 6, 7, 4, 10, 16}, {2, 5, 3, 4, 11, 9},
        {12, 6, 7, 14, 10, 16}, {12, 5, 3, 14, 11, 9},
        boxZ(8, 2, 14, 0, 12),
    };
    private static final double[][][] GRINDSTONE_SHAPES = new double[12][][];

    private static final double[][][] COMPOSTER_SHAPES = new double[9][][];

    private static final double[][] STONECUTTER_SHAPE = {column(16, 16, 0, 9)};
    private static final double[][] SNIFFER_EGG_SHAPE = {{1, 0, 2, 15, 16, 14}};
    /**
     * LanternBlock SHAPE_STANDING = or(column(4, 7, 9), column(6, 0, 7)), SHAPE_HANGING = 같은 형상을
     * 1px 올린 것(구리 랜턴도 LanternBlock 이다). hanging 비트로 고른다.
     */
    private static final double[][][] LANTERN_SHAPES = {
        {{5, 0, 5, 11, 7, 11}, {6, 7, 6, 10, 9, 10}},
        {{5, 1, 5, 11, 8, 11}, {6, 8, 6, 10, 10, 10}},
    };
    /** CampfireBlock.SHAPE = column(16, 0, 7) — 모든 state. */
    private static final double[][] CAMPFIRE_SHAPE = {{0, 0, 0, 16, 7, 16}};

    private static final double[][][] BELL_SHAPES = new double[16][][];

    // [WORLD-GEOMETRY] 셀을 다 채우지 않는 월드 블록(javap · 고정 26.3 서버 골든 행). 클라 BlockModelShapes.ts 와
    // 같은 표다: 선인장 SHAPE column(14,0,16) · COLLISION column(14,0,15), 작은 흘림잎 column(12,0,13)(충돌 없음),
    // 큰 흘림잎 잎 column(16,11,15/13/없음) + 줄기 box(5,0,9,11,15|13,15)(facing 회전, 충돌은 잎만), 큰 흘림잎
    // 줄기 box(5,0,9,11,16,15)(충돌 없음), 비명체 윤곽 풀 셀 · 충돌 column(16,0,8), 감지체 column(16,0,8), 콘딧
    // column(6,5,11), 감압판 column(14,0,1)/(14,0,0.5)(충돌 없음), 침대 box(0,3,0,16,9,16) + 끝쪽 다리 둘,
    // 건초 침대 column(16,0,4) + 머리의 box(0,4,0,16,5,8).
    private static final double[][] CACTUS_OUTLINE = {column(14, 14, 0, 16)};
    private static final double[][] CACTUS_COLLISION = {column(14, 14, 0, 15)};
    private static final double[][] SMALL_DRIPLEAF_SHAPE = {column(12, 12, 0, 13)};
    private static final double[][][] BIG_DRIPLEAF_OUTLINES = new double[16][][];
    private static final double[][][] BIG_DRIPLEAF_COLLISIONS = new double[16][][];
    private static final double[][][] BIG_DRIPLEAF_STEM_SHAPES = new double[4][][];
    private static final double[][] SCULK_SLAB = {column(16, 16, 0, 8)};
    private static final double[][] CONDUIT_SHAPE = {column(6, 6, 5, 11)};
    private static final double[][] PRESSURE_PLATE_UP = {column(14, 14, 0, 1)};
    private static final double[][] PRESSURE_PLATE_DOWN = {column(14, 14, 0, 0.5)};
    /** BED_SHAPES / STRAW_BED_SHAPES[head * 4 + facing]. */
    private static final double[][][] BED_SHAPES = new double[8][][];
    private static final double[][][] STRAW_BED_SHAPES = new double[8][][];

    static {
        HOPPER_SHAPES[0] = concat(HOPPER_BASE, column(4, 4, 0, 4));
        HOPPER_INTERACTION[0] = new double[][] {column(12, 12, 11, 16)};
        for (int facing = 0; facing < 4; facing++) {
            HOPPER_SHAPES[facing + 1] = concat(HOPPER_BASE, rotate(HOPPER_SPOUT_NORTH, facing));
            HOPPER_INTERACTION[facing + 1] = new double[][] {
                column(12, 12, 11, 16), rotate(boxZ(4, 8, 10, 0, 4), facing)};
        }
        double[][] lecternNorth = concat(LECTERN_COLLISION,
                boxZ(16, 10, 14, 1, 5.333333),
                boxZ(16, 12, 16, 5.333333, 9.666667),
                boxZ(16, 14, 18, 9.666667, 14));
        for (int facing = 0; facing < 4; facing++) LECTERN_OUTLINES[facing] = rotateAll(lecternNorth, facing);
        for (int face = ATTACH_FLOOR; face <= ATTACH_CEILING; face++) {
            double[][] base = new double[GRINDSTONE_WALL_NORTH.length][];
            for (int i = 0; i < base.length; i++) {
                double[] b = GRINDSTONE_WALL_NORTH[i];
                base[i] = face == ATTACH_FLOOR
                        ? new double[] {b[0], 16 - b[5], b[1], b[3], 16 - b[2], b[4]}
                        : face == ATTACH_CEILING
                                ? new double[] {b[0], b[2], 16 - b[4], b[3], b[5], 16 - b[1]}
                                : b;
            }
            for (int facing = 0; facing < 4; facing++) GRINDSTONE_SHAPES[face * 4 + facing] = rotateAll(base, facing);
        }
        for (int level = 0; level <= 8; level++) {
            double floor = Math.max(2, 1 + 2 * Math.min(level, 7));
            COMPOSTER_SHAPES[level] = new double[][] {
                {0, 0, 0, 16, floor, 16},
                {0, floor, 0, 2, 16, 16}, {14, floor, 0, 16, 16, 16},
                {2, floor, 0, 14, 16, 2}, {2, floor, 14, 14, 16, 16},
            };
        }
        double[][] bellBody = {column(6, 6, 6, 13), column(8, 8, 4, 6)};
        for (int attachment = BELL_FLOOR; attachment <= BELL_DOUBLE_WALL; attachment++) {
            for (int facing = 0; facing < 4; facing++) {
                boolean alongZ = (facing & 1) == 0;
                BELL_SHAPES[attachment * 4 + facing] = switch (attachment) {
                    case BELL_FLOOR -> new double[][] {alongZ
                            ? new double[] {0, 0, 4, 16, 16, 12} : new double[] {4, 0, 0, 12, 16, 16}};
                    case BELL_CEILING -> concat(bellBody, column(2, 2, 13, 16));
                    case BELL_DOUBLE_WALL -> concat(bellBody,
                            alongZ ? column(2, 16, 13, 15) : column(16, 2, 13, 15));
                    default -> concat(bellBody, rotate(boxZ(2, 13, 15, 0, 13), facing));
                };
            }
        }
    }

    static {
        for (int facing = 0; facing < 4; facing++) {
            for (int tilt = 0; tilt < 4; tilt++) {
                double[][] leaf = tilt <= 1 ? new double[][] {column(16, 16, 11, 15)}
                        : tilt == 2 ? new double[][] {column(16, 16, 11, 13)} : NONE;
                double[] stem = rotate(new double[] {5, 0, 9, 11, tilt <= 1 ? 15 : 13, 15}, facing);
                BIG_DRIPLEAF_OUTLINES[facing * 4 + tilt] = concat(new double[][] {stem}, leaf);
                BIG_DRIPLEAF_COLLISIONS[facing * 4 + tilt] = leaf;
            }
            BIG_DRIPLEAF_STEM_SHAPES[facing] = new double[][] {rotate(new double[] {5, 0, 9, 11, 16, 15}, facing)};
            BED_SHAPES[facing] = rotateAll(new double[][] {
                {0, 3, 0, 16, 9, 16}, {0, 0, 13, 3, 3, 16}, {13, 0, 13, 16, 3, 16}}, facing);
            BED_SHAPES[4 + facing] = rotateAll(new double[][] {
                {0, 3, 0, 16, 9, 16}, {0, 0, 0, 3, 3, 3}, {13, 0, 0, 16, 3, 3}}, facing);
            STRAW_BED_SHAPES[facing] = new double[][] {column(16, 16, 0, 4)};
            STRAW_BED_SHAPES[4 + facing] = new double[][] {
                column(16, 16, 0, 4), rotate(new double[] {0, 4, 0, 16, 5, 8}, facing)};
        }
    }

    private static boolean isPressurePlate(int id) {
        return id == Blocks.STONE_PRESSURE_PLATE || id == Blocks.OAK_PRESSURE_PLATE
                || id == Blocks.POPLAR_PRESSURE_PLATE;
    }

    private BlockModelShapes() {
    }

    private static double[] column(double sx, double sz, double y0, double y1) {
        return new double[] {8 - sx / 2, y0, 8 - sz / 2, 8 + sx / 2, y1, 8 + sz / 2};
    }

    private static double[] boxZ(double sx, double y0, double y1, double z0, double z1) {
        return new double[] {8 - sx / 2, y0, z0, 8 + sx / 2, y1, z1};
    }

    /** 북향 기준 박스(px)를 수평 facing(0 N · 1 E · 2 S · 3 W)으로 돌린다. */
    static double[] rotate(double[] b, int facing) {
        return switch (facing & 3) {
            case 1 -> new double[] {16 - b[5], b[1], b[0], 16 - b[2], b[4], b[3]};
            case 2 -> new double[] {16 - b[3], b[1], 16 - b[5], 16 - b[0], b[4], 16 - b[2]};
            case 3 -> new double[] {b[2], b[1], 16 - b[3], b[5], b[4], 16 - b[0]};
            default -> b;
        };
    }

    private static double[][] rotateAll(double[][] boxes, int facing) {
        double[][] out = new double[boxes.length][];
        for (int i = 0; i < boxes.length; i++) out[i] = rotate(boxes[i], facing);
        return out;
    }

    private static double[][] concat(double[][] boxes, double[]... extra) {
        List<double[]> out = new ArrayList<>(List.of(boxes));
        out.addAll(List.of(extra));
        return out.toArray(new double[0][]);
    }

    /** 이 표가 형상을 소유하는 블록인가. */
    public static boolean has(int id) {
        return id == Blocks.BREWING_STAND || id == Blocks.HOPPER || id == Blocks.CAULDRON
                || id == Blocks.WATER_CAULDRON || id == Blocks.LECTERN || id == Blocks.GRINDSTONE
                || id == Blocks.COMPOSTER || id == Blocks.STONECUTTER || id == Blocks.SNIFFER_EGG
                || id == Blocks.BELL || id == Blocks.CAMPFIRE || BuildingBlockRules.isLantern(id)
                // [WORLD-GEOMETRY]
                || id == Blocks.CACTUS || id == Blocks.SMALL_DRIPLEAF || id == Blocks.BIG_DRIPLEAF
                || id == Blocks.BIG_DRIPLEAF_STEM || id == Blocks.SCULK_SHRIEKER || id == Blocks.SCULK_SENSOR
                || id == Blocks.CONDUIT || isPressurePlate(id) || Blocks.isBed(id)
                || PitcherRules.isPitcher(id);
    }

    /**
     * 호퍼 모델 번호 0 down · 1 N · 2 E · 3 S · 4 W. state 어휘의 정본은 {@code HopperRules}(FACING 은
     * Direction 3D 값 0 down · 2 N · 3 S · 4 W · 5 E, up·범위 밖은 down, bit 3 = !ENABLED)이고, 이 함수는
     * 그 facing 을 형상·모델 표의 순서로 옮길 뿐이다.
     */
    public static int hopperModelFacing(int state) {
        return switch (com.gameexpert.engine.hopper.HopperRules.facing(state)) {
            case 2 -> 1;
            case 5 -> 2;
            case 3 -> 3;
            case 4 -> 4;
            default -> 0;
        };
    }

    /** 숫돌 face 0..2(어휘 밖 3 은 floor). */
    public static int grindstoneFace(int state) {
        int face = (state >> GRINDSTONE_FACE_SHIFT) & 3;
        return face <= ATTACH_CEILING ? face : ATTACH_FLOOR;
    }

    public static int bellAttachment(int state) {
        return (state >> BELL_ATTACHMENT_SHIFT) & 3;
    }

    /**
     * BellBlock#isProperHit: 클릭한 면이 수평이고, 맞은 점이 칸 높이 0.8124F 를 넘지 않으며, 그 면이
     * 종이 흔들리는 축에 있다 — 바닥 종은 facing 축, 벽 종은 그 직교 축, 천장 종은 모든 수평 면.
     * {@code face} 는 바닐라 Direction 번호(0 down · 1 up · 2 north · 3 south · 4 west · 5 east),
     * {@code hitY} 는 칸 안의 맞은 높이다. 클라 {@code bellIsProperHit} 와 같은 규칙이다.
     */
    public static boolean bellIsProperHit(int state, int face, double hitY) {
        if (face < 2 || face > 5 || !(hitY <= 0.8124F)) return false;
        boolean faceAlongZ = face == 2 || face == 3;
        boolean facingAlongZ = (state & 1) == 0;
        return switch (bellAttachment(state)) {
            case BELL_FLOOR -> faceAlongZ == facingAlongZ;
            case BELL_CEILING -> true;
            default -> faceAlongZ != facingAlongZ;
        };
    }

    /** 퇴비통 level 0..8(손상된 바이트는 수거 가능 단계로 수렴한다). */
    public static int composterLevel(int state) {
        return Math.max(0, Math.min(8, state));
    }

    /** 물 가마솥 level 1..3. */
    public static int waterCauldronLevel(int state) {
        int level = state & WATER_CAULDRON_LEVEL_MASK;
        return level == 0 ? 3 : level;
    }

    /** 바닐라 getShape(선택 윤곽), px/16. 호출자는 배열을 바꾸지 않는다. */
    public static double[][] outlineBoxes16(int id, int state) {
        return switch (id) {
            case Blocks.BREWING_STAND -> BREWING_STAND_SHAPE;
            case Blocks.HOPPER -> HOPPER_SHAPES[hopperModelFacing(state)];
            case Blocks.CAULDRON, Blocks.WATER_CAULDRON -> CAULDRON_SHAPE;
            case Blocks.LECTERN -> LECTERN_OUTLINES[state & 3];
            case Blocks.GRINDSTONE -> GRINDSTONE_SHAPES[grindstoneFace(state) * 4 + (state & 3)];
            case Blocks.COMPOSTER -> COMPOSTER_SHAPES[composterLevel(state)];
            case Blocks.STONECUTTER -> STONECUTTER_SHAPE;
            case Blocks.SNIFFER_EGG -> SNIFFER_EGG_SHAPE;
            case Blocks.BELL -> BELL_SHAPES[bellAttachment(state) * 4 + (state & 3)];
            case Blocks.CAMPFIRE -> CAMPFIRE_SHAPE;
            case Blocks.PITCHER_CROP -> PITCHER_CROP_OUTLINES[state & 15];
            case Blocks.PITCHER_PLANT -> FULL;
                    case Blocks.CACTUS -> CACTUS_OUTLINE;
            case Blocks.SMALL_DRIPLEAF -> SMALL_DRIPLEAF_SHAPE;
            case Blocks.BIG_DRIPLEAF -> BIG_DRIPLEAF_OUTLINES[((state >> 2) & 3) * 4 + (state & 3)];
            case Blocks.BIG_DRIPLEAF_STEM -> BIG_DRIPLEAF_STEM_SHAPES[state & 3];
            case Blocks.SCULK_SHRIEKER -> FULL;
            case Blocks.SCULK_SENSOR -> SCULK_SLAB;
            case Blocks.CONDUIT -> CONDUIT_SHAPE;
            case Blocks.POPLAR_PRESSURE_PLATE -> (state & 1) != 0 ? PRESSURE_PLATE_DOWN : PRESSURE_PLATE_UP;
            case Blocks.STONE_PRESSURE_PLATE, Blocks.OAK_PRESSURE_PLATE -> PRESSURE_PLATE_UP;
            default -> {
                if (Blocks.isBed(id)) {
                    int index = ((state & BuildingBlockRules.BED_HEAD) != 0 ? 4 : 0) + (state & 3);
                    yield id == Blocks.STRAW_BED ? STRAW_BED_SHAPES[index] : BED_SHAPES[index];
                }
                yield BuildingBlockRules.isLantern(id) ? LANTERN_SHAPES[state & 1] : NONE;
            }
        };
    }

    /** 바닐라 getCollisionShape(= 지지 형상: 열 종 모두 getBlockSupportShape 재정의가 없다), px/16. */
    public static double[][] collisionBoxes16(int id, int state) {
        if (id == Blocks.LECTERN) return LECTERN_COLLISION;
        if (id == Blocks.COMPOSTER) return COMPOSTER_SHAPES[0];
        // [WORLD-GEOMETRY] noCollision() 블록과 충돌이 윤곽과 다른 블록.
        if (id == Blocks.SMALL_DRIPLEAF || id == Blocks.BIG_DRIPLEAF_STEM || isPressurePlate(id)) return NONE;
        if (id == Blocks.CACTUS) return CACTUS_COLLISION;
        if (id == Blocks.BIG_DRIPLEAF) return BIG_DRIPLEAF_COLLISIONS[((state >> 2) & 3) * 4 + (state & 3)];
        if (id == Blocks.SCULK_SHRIEKER) return SCULK_SLAB;
        if (id == Blocks.PITCHER_CROP) return PITCHER_CROP_COLLISIONS[state & 15];
        if (id == Blocks.PITCHER_PLANT) return NONE;
        return outlineBoxes16(id, state);
    }

    /** 바닐라 getInteractionShape(재정의가 없으면 빈 형상), px/16. */
    public static double[][] interactionBoxes16(int id, int state) {
        if (id == Blocks.HOPPER) return HOPPER_INTERACTION[hopperModelFacing(state)];
        if (id == Blocks.CAULDRON || id == Blocks.WATER_CAULDRON) return CAULDRON_INTERACTION;
        if (id == Blocks.COMPOSTER) return FULL;
        return NONE;
    }

    /** 충돌 형상의 가장 높은 윗면(블록 단위). */
    public static double collisionTop(int id, int state) {
        double top = 0;
        for (double[] box : collisionBoxes16(id, state)) top = Math.max(top, box[4] / 16.0);
        return top;
    }

    /** 충돌 형상의 가장 낮은 바닥(블록 단위). */
    public static double collisionBottom(int id, int state) {
        double bottom = 1;
        for (double[] box : collisionBoxes16(id, state)) bottom = Math.min(bottom, box[1] / 16.0);
        return bottom;
    }
}
