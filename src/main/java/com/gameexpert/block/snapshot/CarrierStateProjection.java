package com.gameexpert.block.snapshot;

import com.gameexpert.engine.BlockFamilies;
import com.gameexpert.engine.BlockModelShapes;
import com.gameexpert.engine.BuildingBlockRules;
import com.gameexpert.engine.CakeRules;
import com.gameexpert.engine.EndPortalFrameRules;
import com.gameexpert.engine.FurnaceVariant;
import com.gameexpert.engine.blocks.P2Rules;
import com.gameexpert.engine.blocks.P3Rules;
import com.gameexpert.engine.blocks.P6Rules;
import com.gameexpert.engine.blocks.P29Rules;
import com.gameexpert.engine.blocks.CandleRules;
import com.gameexpert.engine.trial.TrialVaultContract;
import com.gameexpert.terrain.Blocks;
import com.gameexpert.terrain.mc.feature.Mc263ExactStateCodec;
import com.gameexpert.terrain.mc.feature.Mc263FeatureBlockState;

/**
 * carrier exact-state 코드 → <b>엔진 의미 state 바이트</b> 단일 투영.
 *
 * <p><b>왜 필요한가.</b> 청크 스냅샷의 셀당 상태 바이트({@code (blockType<<8)|state})에는
 * 이름공간이 둘 섞여 있었다 — 엔진 규칙이 직접 쓴 의미 비트필드와, carrier 가 심은
 * {@link Mc263ExactStateCodec} 코드북 서수. 클라이언트는 태그가 없어 모든 바이트를 엔진
 * 비트필드로 읽으므로 carrier 계단은 엉뚱한 방향으로, 이끼광원은 엉뚱한 면으로 그려졌고
 * waterlogged 칸은 전부 마른 채로 그려졌다. 이 클래스가 <b>전송 직전에</b> carrier 서수를
 * 엔진 의미 바이트로 바꿔 이름공간을 하나로 만든다.
 *
 * <p><b>계약.</b> {@code docs/CONTRACT.md} §4.1 이 정본이다. 요지는 두 가지다.
 * <ul>
 *   <li>형상 비트는 {@link BuildingBlockRules#normalizeState(int, int)} 가 그 블록에 대해
 *       허용하는 어휘 그대로다. 투영 결과는 언제나 그 함수의 고정점이다(비트 7 제외).</li>
 *   <li>비트 7({@link #WATERLOGGED})은 <b>모든 형상군에서</b> "이 칸은 블록이면서 동시에
 *       레벨 8 수원" 을 뜻한다. 구리({@code Fluids}) 와 선반({@code ShelfRules}) 이 이미 같은
 *       뜻으로 쓰던 비트라 새 이름공간이 아니라 기존 관례의 전면 확장이다.</li>
 * </ul>
 *
 * <p><b>전역성.</b> 6,220 행 카탈로그의 어떤 (블록, 코드) 쌍도 예외 없이 투영된다. 엔진이
 * 그 블록에 대해 의미 비트를 정의하지 않은 형상군은 <b>0</b>(엔진 기본 바이트)으로 투영한다 —
 * 엔진이 한 번도 쓴 적 없는 칸이 갖는 값과 같으므로 클라이언트가 이미 아는 값이다.
 * 서수를 그대로 흘려보내는 선택지는 없다(그것이 바로 이 결함이다).
 */
public final class CarrierStateProjection {

    /** 모든 형상군 공통 waterlogged 비트. 구리·선반이 이미 쓰던 비트 7 을 전면 확장한 것이다. */
    public static final int WATERLOGGED = 0x80;

    private static final int FACING_NORTH = 0;
    private static final int FACING_EAST = 1;
    private static final int FACING_SOUTH = 2;
    private static final int FACING_WEST = 3;

    /** blockId 별 코드 → 투영 바이트 표. 첫 조회에서만 코드북을 훑는다. */
    private static final byte[][] TABLE = new byte[Blocks.BLOCK_ID_HIGH_WATER + 1][];

    private static final java.util.concurrent.ConcurrentMap<Integer,
            java.util.concurrent.ConcurrentMap<String, Integer>> EXACT_PROJECTIONS =
                    new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * blockId 별 투영 요약 비트. 0 이면 미확정이고, 확정되면 {@link #SUMMARY_KNOWN} 이 선다.
     * {@link #SUMMARY_ANY_NON_ZERO} 는 어떤 코드라도 0 이 아닌 바이트로 투영됨,
     * {@link #SUMMARY_DEFAULT_NON_ZERO} 는 code 0 이 0 이 아닌 바이트로 투영됨을 뜻한다.
     */
    private static final byte[] NON_ZERO = new byte[Blocks.BLOCK_ID_HIGH_WATER + 1];
    private static final int SUMMARY_KNOWN = 1;
    private static final int SUMMARY_ANY_NON_ZERO = 2;
    private static final int SUMMARY_DEFAULT_NON_ZERO = 4;

    private CarrierStateProjection() {
    }

    /**
     * carrier 코드를 엔진 의미 바이트로 투영한다. 코드북 밖 코드는 fail-closed 로 던진다.
     *
     * @param blockId 셀의 블록 ID
     * @param carrierCode {@link Mc263ExactStateCodec} 코드(0..255)
     */
    public static int project(int blockId, int carrierCode) {
        byte[] table = TABLE[blockId];
        if (table == null) table = TABLE[blockId] = buildTable(blockId);
        if (carrierCode < 0 || carrierCode >= table.length) {
            throw new IllegalArgumentException("invalid carrier state code " + carrierCode
                    + " for block ID " + blockId);
        }
        return Byte.toUnsignedInt(table[carrierCode]);
    }

    /**
     * 이 블록의 어떤 carrier 코드라도 0 이 아닌 엔진 바이트로 투영되는가. 권위가 생성 칸의 상태를
     * carrier 에서 읽을 필요가 있는지를 셀마다 조회 없이 걸러 내는 표 조회다.
     */
    public static boolean projectsNonZero(int blockId) {
        return (summary(blockId) & SUMMARY_ANY_NON_ZERO) != 0;
    }

    /**
     * 이 블록의 code 0 이 0 이 아닌 엔진 바이트로 투영되는가.
     *
     * <p>carrier 의 희소 상태 평면은 code 0 을 싣지 않는다({@code Mc263FinalChunkCodec} 이 거절한다).
     * 그래서 평면에 항목이 없는 생성 칸의 carrier 상태는 코드북의 code 0 — 그 블록의 카탈로그
     * 기본 exact state — 이다. code 0 은 바닐라 기본 상태가 아닐 수 있다(예:
     * {@code oak_stairs[facing=south,...]}, 물에 잠긴 산호). 이 조회는 스냅샷 조립과 권위 읽기가
     * 셀마다 코드북을 보지 않고 그런 블록만 골라내게 한다.
     */
    public static boolean projectsNonZeroByDefault(int blockId) {
        return (summary(blockId) & SUMMARY_DEFAULT_NON_ZERO) != 0;
    }

    /** 희소 평면에 항목이 없는 생성 칸의 엔진 바이트 — code 0 의 투영이다. */
    public static int projectDefault(int blockId) {
        return project(blockId, 0);
    }

    private static int summary(int blockId) {
        if (blockId < 0 || blockId >= TABLE.length) return 0;
        int cached = NON_ZERO[blockId];
        if (cached == 0) {
            byte[] table = TABLE[blockId];
            if (table == null) table = TABLE[blockId] = buildTable(blockId);
            cached = SUMMARY_KNOWN;
            if (table[0] != 0) cached |= SUMMARY_DEFAULT_NON_ZERO;
            for (byte projected : table) {
                if (projected != 0) {
                    cached |= SUMMARY_ANY_NON_ZERO;
                    break;
                }
            }
            // 순수 함수 결과라 경쟁 기록이 같은 값을 쓴다.
            NON_ZERO[blockId] = (byte) cached;
        }
        return cached;
    }

    /**
     * 블록 하나의 코드북 전체를 한 번만 투영해 둔다. 스냅샷 조립은 셀마다 이 표를 읽으므로
     * 코드북 조회·문자열 파싱이 뜨거운 경로에 들어오지 않는다. 표는 순수 함수 결과라
     * 경쟁 기록이 같은 배열 내용을 쓴다.
     */
    private static byte[] buildTable(int blockId) {
        int count;
        try {
            count = Mc263ExactStateCodec.stateCount(blockId);
        } catch (IllegalArgumentException outsideCodebook) {
            // 코드북이 없는 블록의 carrier 상태는 존재할 수 없다. 0(기본 바이트)만 유효하다.
            return new byte[] {0};
        }
        byte[] table = new byte[count];
        for (int code = 0; code < count; code++) {
            table[code] = (byte) projectState(blockId, Mc263ExactStateCodec.decode(blockId, code));
        }
        return table;
    }

    /** 코드북 조회 없이 exact state 하나를 투영한다. 패리티 테스트와 fixture 내보내기가 쓴다. */
    public static int projectState(int blockId, Mc263FeatureBlockState state) {
        return projectExactState(blockId, state.exactState());
    }

    public static int projectState(int blockId, com.gameexpert.authority.versioned.NeutralFinalChunk.StateOverride state) {
        if (state.blockId() != blockId) throw new IllegalArgumentException("carrier block identity mismatch");
        return EXACT_PROJECTIONS.computeIfAbsent(blockId,
                ignored -> new java.util.concurrent.ConcurrentHashMap<>())
                .computeIfAbsent(state.exactState(), exact -> projectExactState(blockId, exact));
    }

    private static int projectExactState(int blockId, String exact) {
        int shape = BuildingBlockRules.normalizeState(blockId, shapeBits(blockId, exact))
                & ~WATERLOGGED;
        return shape | (isWaterlogged(blockId, exact) ? WATERLOGGED : 0);
    }

    /**
     * 이 칸이 블록이면서 동시에 수원인가. <b>released exact state 가 {@code waterlogged=true} 를
     * 적었는가</b> 하나로 판정한다 — Rust {@code carrier_state_is_waterlogged} 와 정적판
     * {@code carrierExactStateIsWaterlogged} 가 같은 규칙을 쓴다.
     *
     * <p>카탈로그의 {@code fluidKind()} 를 쓰지 않는 이유: 그 사실은 형상군 일부에서
     * {@code waterlogged=false} 행에도 {@code WATER_SOURCE} 로 붙어 있어(예:
     * {@code minecraft:cobblestone_slab[type=top,waterlogged=false]}) 마른 칸을 물로 만든다.
     * 전송 계약은 문자열이 적은 것만 싣는다.
     *
     * <p>진짜 유체 셀(물·용암 블록 자체)은 자기 블록 ID 로 물이므로 비트를 세우지 않는다.
     */
    private static boolean isWaterlogged(int blockId, String exact) {
        if (com.gameexpert.engine.Fluids.isFluid(blockId)) return false;
        return "true".equals(property(exact, "waterlogged"));
    }

    // ── 형상 비트 ────────────────────────────────────────────────────────────
    // 분기 순서와 어휘는 BuildingBlockRules.normalizeState 를 그대로 따라간다. 그 함수가
    // 엔진의 "블록별 허용 비트" 등록부이므로, 여기서 새 어휘를 만들지 않고 그 등록부를 읽는다.

    private static int shapeBits(int blockId, String exact) {
        if (blockId == Blocks.LEAF_LITTER) {
            return Blocks.flowerBedState(number(exact, "segment_amount", 1), horizontalFacing(exact));
        }
        if (CandleRules.isCandle(blockId)) {
            return Math.max(1, Math.min(4, number(exact, "candles", 1))) - 1;
        }
        if (blockId == Blocks.VINE) {
            return connection(exact, "north", P2Rules.VINE_NORTH)
                    | connection(exact, "east", P2Rules.VINE_EAST)
                    | connection(exact, "south", P2Rules.VINE_SOUTH)
                    | connection(exact, "west", P2Rules.VINE_WEST)
                    | connection(exact, "up", P2Rules.VINE_HANGING);
        }
        if (blockId == Blocks.MOSS_CARPET) return 2;
        if (blockId == Blocks.GLOW_LICHEN) return lichenFaces(exact);
        if (blockId == Blocks.BIG_DRIPLEAF) {
            return horizontalFacing(exact) << 2 | dripleafTilt(exact);
        }
        // [WORLD-GEOMETRY] 큰 흘림잎 줄기 · 작은 흘림잎의 facing(비트 0..1): 줄기 box(5,0,9,11,16,15)와 두 모델이
        // facing 으로 돈다. 작은 흘림잎의 half 는 위아래 이웃이 정한다(DoublePlantBlock 짝).
        if (blockId == Blocks.BIG_DRIPLEAF_STEM || blockId == Blocks.SMALL_DRIPLEAF) return horizontalFacing(exact);
        // [WORLD-GEOMETRY] 생성 비명체의 can_summon(비트 2, SculkVibrationRules.SHRIEKER_CAN_SUMMON).
        if (blockId == Blocks.SCULK_SHRIEKER) {
            return "true".equals(property(exact, "can_summon"))
                    ? com.gameexpert.engine.sculk.SculkVibrationRules.SHRIEKER_CAN_SUMMON : 0;
        }
        if (P6Rules.isSpeleothem(blockId)) {
            return "up".equals(property(exact, "vertical_direction")) ? P6Rules.DRIPSTONE_UP : 0;
        }
        if (blockId == Blocks.CAVE_VINES || blockId == Blocks.CAVE_VINES_PLANT) {
            int berries = "true".equals(property(exact, "berries")) ? P6Rules.CAVE_VINES_BERRIES : 0;
            if (blockId == Blocks.CAVE_VINES_PLANT) return berries;
            int age = Math.min(number(exact, "age", 0), P6Rules.CAVE_VINES_MAX_AGE);
            return berries | age << P6Rules.CAVE_VINES_AGE_SHIFT & P6Rules.CAVE_VINES_AGE_MASK;
        }
        if (blockId == Blocks.MANGROVE_PROPAGULE) {
            int hanging = "true".equals(property(exact, "hanging")) ? P6Rules.PROPAGULE_HANGING : 0;
            return hanging | number(exact, "age", 0) << 1 & P6Rules.PROPAGULE_AGE_MASK;
        }
        if (BuildingBlockRules.isLantern(blockId)) {
            // 철 랜턴(영혼 랜턴이 같은 ID 로 접힌다)과 구리 랜턴 계열이 같은 hanging 한 비트다.
            return "true".equals(property(exact, "hanging")) ? BuildingBlockRules.LANTERN_HANGING : 0;
        }
        if (BlockFamilies.isStateOrientedWoodLog(blockId)) return axis(exact);
        if (P6Rules.isAmethystBud(blockId)) return amethystFace(exact);
        if (BuildingBlockRules.isStairs(blockId)) {
            // [BLOCK-SHAPES] 엔진 계단 facing 은 윗단의 반대쪽이다(blocks.ts stairQuadrantMask:
            // 엔진 N = 바닐라 S). 바닐라 StairBlock.FACING 은 윗단 쪽이므로 뒤집어 싣는다.
            return oppositeFacing(horizontalFacing(exact))
                    | ("top".equals(property(exact, "half")) ? BuildingBlockRules.STAIR_TOP : 0)
                    | stairShape(exact) << BuildingBlockRules.STAIR_SHAPE_SHIFT;
        }
        if (BuildingBlockRules.isSlab(blockId)) return slabType(exact);
        if (BuildingBlockRules.isWall(blockId) || Blocks.isFence(blockId)) {
            return connection(exact, "north", BuildingBlockRules.CONNECT_NORTH)
                    | connection(exact, "east", BuildingBlockRules.CONNECT_EAST)
                    | connection(exact, "south", BuildingBlockRules.CONNECT_SOUTH)
                    | connection(exact, "west", BuildingBlockRules.CONNECT_WEST);
        }
        if (Blocks.isFenceGate(blockId)) {
            return horizontalFacing(exact)
                    | ("true".equals(property(exact, "open")) ? BuildingBlockRules.GATE_OPEN : 0)
                    | ("true".equals(property(exact, "in_wall"))
                            ? BuildingBlockRules.GATE_IN_WALL : 0);
        }
        if (Blocks.isTrapdoor(blockId)) {
            // [BLOCK-SHAPES] 엔진 다락문 facing 은 열린 판이 서는 면이고 바닐라 facing=north 의 열린
            // 판은 z 13..16(남쪽)에 선다(template_orientable_trapdoor_open) — 반대 방향이다.
            return oppositeFacing(horizontalFacing(exact))
                    | ("top".equals(property(exact, "half")) ? BuildingBlockRules.TRAPDOOR_TOP : 0)
                    | ("true".equals(property(exact, "open"))
                            ? BuildingBlockRules.TRAPDOOR_OPEN : 0);
        }
        if (Blocks.isDoor(blockId)) {
            // [BLOCK-SHAPES] 엔진 문 facing 은 닫힌 판이 서는 면(바닐라 facing=north 의 판은 z 13..16)
            // 이라 바닐라의 반대이고, 경첩 좌우도 그 반대 방향에서 본 것이라 뒤집힌다: 바닐라
            // facing=north·hinge=left 의 열린 판(DoorBlock EAST_AABB x 0..3)이 엔진 S·hinge-right
            // 의 열린 판과 같다.
            return oppositeFacing(horizontalFacing(exact))
                    | ("true".equals(property(exact, "open")) ? BuildingBlockRules.DOOR_OPEN : 0)
                    | ("upper".equals(property(exact, "half")) ? BuildingBlockRules.DOOR_UPPER : 0)
                    | ("left".equals(property(exact, "hinge"))
                            ? BuildingBlockRules.DOOR_HINGE_RIGHT : 0);
        }
        if (Blocks.isBed(blockId)) {
            // 엔진 facing 은 발치→머리 방향이고 바닐라 BedBlock.FACING 도 같은 방향이다.
            return horizontalFacing(exact)
                    | ("head".equals(property(exact, "part")) ? BuildingBlockRules.BED_HEAD : 0);
        }
        if (Blocks.isChestShaped(blockId)) {
            // 좌우 짝은 exact state 의 type 에서 직접 읽는다. 코드북 서수에서 유추하지 않는다 —
            // 서수 4/6 은 마른 남/서 단일 상자이지 좌우 반쪽이 아니다.
            String type = property(exact, "type");
            int pair = "left".equals(type) ? BuildingBlockRules.CHEST_LEFT
                    : "right".equals(type) ? BuildingBlockRules.CHEST_RIGHT
                    : BuildingBlockRules.CHEST_SINGLE;
            return horizontalFacing(exact) | pair;
        }
        if (Blocks.isAnvil(blockId)) return horizontalFacing(exact);
        if (FurnaceVariant.of(blockId) != null) return horizontalFacing(exact);
        if (Blocks.isShelf(blockId)) {
            // 옆 연쇄 비트(3..4)는 이웃에서 나오는 사실이라 exact state 에 없다.
            return horizontalFacing(exact) | ("true".equals(property(exact, "powered")) ? 0x04 : 0);
        }
        if (Blocks.isShelfMushroom(blockId)) {
            return horizontalFacing(exact) | (number(exact, "age", 0) > 0 ? P29Rules.AGE : 0);
        }
        if (BuildingBlockRules.isPane(blockId)) {
            return connection(exact, "north", BuildingBlockRules.CONNECT_NORTH)
                    | connection(exact, "east", BuildingBlockRules.CONNECT_EAST)
                    | connection(exact, "south", BuildingBlockRules.CONNECT_SOUTH)
                    | connection(exact, "west", BuildingBlockRules.CONNECT_WEST);
        }
        // 벽 군기는 연속 구간이 아니다 — 2264 와 2275 두 ID 뿐이고 사이의 ID 는 무관한 블록이다.
        if (blockId == Blocks.WHITE_WALL_BANNER || blockId == Blocks.BROWN_WALL_BANNER) {
            return horizontalFacing(exact);
        }
        return switch (blockId) {
            case Blocks.PUMPKIN, Blocks.CARVED_PUMPKIN, Blocks.JACK_O_LANTERN,
                    Blocks.TATTERED_BANNER, Blocks.LOOM -> horizontalFacing(exact);
            // [VANILLA-STATION] 생성 벌집은 BeehiveDecorator.WORLDGEN_FACING(south)을 싣는다.
            case Blocks.BEE_NEST, Blocks.BEEHIVE -> horizontalFacing(exact)
                    | Math.max(0, Math.min(BuildingBlockRules.BEEHIVE_MAX_HONEY,
                            number(exact, "honey_level", 0)))
                            << BuildingBlockRules.BEEHIVE_HONEY_SHIFT;
            // [BARREL-STATE] 통은 6방향 facing + open 이다(마을 통의 facing 이 여기서 온다).
            case Blocks.BARREL -> barrelFacing(exact)
                    | ("true".equals(property(exact, "open")) ? BuildingBlockRules.BARREL_OPEN : 0);
            case Blocks.BONE_BLOCK -> axis(exact);
            case Blocks.RAIL -> railShape(exact);
            case Blocks.CAMPFIRE -> horizontalFacing(exact)
                    | ("true".equals(property(exact, "lit")) ? BuildingBlockRules.CAMPFIRE_LIT : 0);
            case Blocks.CAKE -> {
                int bites = number(exact, "bites", 0);
                yield CakeRules.isValidBites(bites) ? bites : 0;
            }
            case Blocks.END_PORTAL_FRAME -> horizontalFacing(exact)
                    | ("true".equals(property(exact, "eye")) ? EndPortalFrameRules.EYE : 0);
            // [TRIAL] 금고의 facing(비트 2..3)·ominous(비트 4). vault_state 는 권위가 소유하므로
            // 싣지 않는다 — 생성 금고는 모두 inactive 이고 엔진 기본(0)과 같다.
            case Blocks.VAULT -> horizontalFacing(exact) << 2
                    | ("true".equals(property(exact, "ominous")) ? TrialVaultContract.OMINOUS : 0);
            // [TRIAL] 트라이얼 스포너의 trial_spawner_state(코드 비트 0..2)·ominous(비트 3).
            // 생성 스포너는 모두 waiting_for_players 이고 그 코드는 0 이다(엔진 기본과 같다).
            case Blocks.TRIAL_SPAWNER -> trialSpawnerState(exact)
                    | ("true".equals(property(exact, "ominous"))
                            ? com.gameexpert.engine.trial.TrialSpawnerContract.OMINOUS : 0);
            // [BLOCK-SHAPES] 모델 블록의 state 어휘(BlockModelShapes): 구조물 종·독서대·석재 절단기·
            // 숫돌·호퍼·양조대의 방향·부착·병과 물 가마솥 수위를 exact state 에서 싣는다.
            case Blocks.BELL -> horizontalFacing(exact) | bellAttachment(exact)
                    << BlockModelShapes.BELL_ATTACHMENT_SHIFT;
            case Blocks.LECTERN -> horizontalFacing(exact)
                    | ("true".equals(property(exact, "has_book")) ? BlockModelShapes.LECTERN_HAS_BOOK : 0);
            case Blocks.STONECUTTER -> horizontalFacing(exact);
            case Blocks.GRINDSTONE -> horizontalFacing(exact) | attachFace(exact)
                    << BlockModelShapes.GRINDSTONE_FACE_SHIFT;
            case Blocks.HOPPER -> com.gameexpert.engine.hopper.HopperRules.state(
                    hopperFacing(exact), !"false".equals(property(exact, "enabled")));
            case Blocks.BREWING_STAND -> ("true".equals(property(exact, "has_bottle_0")) ? 1 : 0)
                    | ("true".equals(property(exact, "has_bottle_1")) ? 2 : 0)
                    | ("true".equals(property(exact, "has_bottle_2")) ? 4 : 0);
            case Blocks.WATER_CAULDRON -> Math.max(1, Math.min(3, number(exact, "level", 3)));
            // 엔진이 이 블록에 대해 의미 비트를 정의하지 않았다. 기본 바이트로 투영한다.
            default -> 0;
        };
    }

    // ── exact-state 속성 읽기 ────────────────────────────────────────────────

    /** {@code trial_spawner_state} → 상태 코드. 속성이 없거나 모르면 대기(0)다. */
    static int trialSpawnerState(String exact) {
        var state = com.gameexpert.engine.trial.TrialSpawnerContract.State.fromSerializedName(
                String.valueOf(property(exact, "trial_spawner_state")));
        return state == null
                ? com.gameexpert.engine.trial.TrialSpawnerContract.State.WAITING_FOR_PLAYERS
                        .stateBits()
                : state.stateBits();
    }

    /** {@code minecraft:key[a=b,c=d]} 에서 속성 하나. 없으면 null. */
    static String property(String exact, String name) {
        int open = exact.indexOf('[');
        if (open < 0) return null;
        int cursor = open + 1;
        int end = exact.length() - 1;
        while (cursor < end) {
            int equals = exact.indexOf('=', cursor);
            if (equals < 0) return null;
            int comma = exact.indexOf(',', equals);
            int valueEnd = comma < 0 || comma > end ? end : comma;
            if (equals - cursor == name.length() && exact.startsWith(name, cursor)) {
                return exact.substring(equals + 1, valueEnd);
            }
            cursor = valueEnd + 1;
        }
        return null;
    }

    private static int number(String exact, String name, int fallback) {
        String value = property(exact, name);
        if (value == null) return fallback;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException notANumber) {
            return fallback;
        }
    }

    /** 연결 속성. 담장의 {@code low}/{@code tall} 과 울타리의 {@code true} 모두 연결이다. */
    private static int connection(String exact, String name, int bit) {
        String value = property(exact, name);
        if (value == null || "false".equals(value) || "none".equals(value)) return 0;
        return bit;
    }

    private static int horizontalFacing(String exact) {
        String facing = property(exact, "facing");
        if (facing == null) return FACING_NORTH;
        return switch (facing) {
            case "east" -> FACING_EAST;
            case "south" -> FACING_SOUTH;
            case "west" -> FACING_WEST;
            default -> FACING_NORTH;
        };
    }

    /** 수평 facing(0 N · 1 E · 2 S · 3 W)의 반대. */
    private static int oppositeFacing(int facing) {
        return (facing + 2) & 3;
    }

    /** 통 6방향 facing 을 번개막대·자수정 싹 어휘(0 up · 1 down · 2 N · 3 E · 4 S · 5 W)로. */
    private static int barrelFacing(String exact) {
        String facing = property(exact, "facing");
        if (facing == null) return BuildingBlockRules.BARREL_FACING_UP;
        return switch (facing) {
            case "down" -> BuildingBlockRules.BARREL_FACING_DOWN;
            case "north" -> BuildingBlockRules.BARREL_FACING_NORTH;
            case "east" -> BuildingBlockRules.BARREL_FACING_EAST;
            case "south" -> BuildingBlockRules.BARREL_FACING_SOUTH;
            case "west" -> BuildingBlockRules.BARREL_FACING_WEST;
            default -> BuildingBlockRules.BARREL_FACING_UP;
        };
    }

    /** [BLOCK-SHAPES] 종 attachment: 0 floor · 1 ceiling · 2 single_wall · 3 double_wall. */
    private static int bellAttachment(String exact) {
        String value = property(exact, "attachment");
        if (value == null) return BlockModelShapes.BELL_FLOOR;
        return switch (value) {
            case "ceiling" -> BlockModelShapes.BELL_CEILING;
            case "single_wall" -> BlockModelShapes.BELL_SINGLE_WALL;
            case "double_wall" -> BlockModelShapes.BELL_DOUBLE_WALL;
            default -> BlockModelShapes.BELL_FLOOR;
        };
    }

    /** [BLOCK-SHAPES] 숫돌 face: 0 floor · 1 wall · 2 ceiling. */
    private static int attachFace(String exact) {
        String value = property(exact, "face");
        if ("wall".equals(value)) return BlockModelShapes.ATTACH_WALL;
        if ("ceiling".equals(value)) return BlockModelShapes.ATTACH_CEILING;
        return BlockModelShapes.ATTACH_FLOOR;
    }

    /** [BLOCK-SHAPES] 호퍼 facing 의 Direction 3D 값(HopperRules): 0 down · 2 N · 3 S · 4 W · 5 E. */
    private static int hopperFacing(String exact) {
        String value = property(exact, "facing");
        if (value == null) return 0;
        return switch (value) {
            case "north" -> 2;
            case "east" -> 5;
            case "south" -> 3;
            case "west" -> 4;
            default -> 0;
        };
    }

    private static int axis(String exact) {
        String value = property(exact, "axis");
        if (value == null) return 0;
        return switch (value) {
            case "x" -> 1;
            case "z" -> 2;
            default -> 0;
        };
    }

    private static int stairShape(String exact) {
        String shape = property(exact, "shape");
        if (shape == null) return BuildingBlockRules.STAIR_STRAIGHT;
        return switch (shape) {
            case "inner_left" -> BuildingBlockRules.STAIR_INNER_LEFT;
            case "inner_right" -> BuildingBlockRules.STAIR_INNER_RIGHT;
            case "outer_left" -> BuildingBlockRules.STAIR_OUTER_LEFT;
            case "outer_right" -> BuildingBlockRules.STAIR_OUTER_RIGHT;
            default -> BuildingBlockRules.STAIR_STRAIGHT;
        };
    }

    private static int slabType(String exact) {
        String type = property(exact, "type");
        if ("top".equals(type)) return BuildingBlockRules.SLAB_TOP;
        if ("double".equals(type)) return BuildingBlockRules.SLAB_DOUBLE;
        return BuildingBlockRules.SLAB_BOTTOM;
    }

    private static int dripleafTilt(String exact) {
        String tilt = property(exact, "tilt");
        if (tilt == null) return P3Rules.DRIPLEAF_UPRIGHT;
        return switch (tilt) {
            case "unstable" -> P3Rules.DRIPLEAF_UNSTABLE;
            case "partial" -> P3Rules.DRIPLEAF_PARTIAL;
            case "full" -> P3Rules.DRIPLEAF_FULL;
            default -> P3Rules.DRIPLEAF_UPRIGHT;
        };
    }

    private static int amethystFace(String exact) {
        String facing = property(exact, "facing");
        if (facing == null) return P6Rules.AMETHYST_FACING_UP;
        return switch (facing) {
            case "down" -> P6Rules.AMETHYST_FACING_DOWN;
            case "north" -> P6Rules.AMETHYST_FACING_NORTH;
            case "east" -> P6Rules.AMETHYST_FACING_EAST;
            case "south" -> P6Rules.AMETHYST_FACING_SOUTH;
            case "west" -> P6Rules.AMETHYST_FACING_WEST;
            default -> P6Rules.AMETHYST_FACING_UP;
        };
    }

    /**
     * 이끼광원 여섯 면 비트. 값은 메셔의 {@code GLOW_LICHEN_FACE_BITS} 와 한 글자도 다르면
     * 안 된다 — FACES 순서 +X,-X,+Y,-Y,+Z,-Z 에 대응하는 0x20,0x08,0x02,0x01,0x04,0x10 이다.
     */
    private static int lichenFaces(String exact) {
        return connection(exact, "east", 0x20)
                | connection(exact, "west", 0x08)
                | connection(exact, "up", 0x02)
                | connection(exact, "down", 0x01)
                | connection(exact, "south", 0x04)
                | connection(exact, "north", 0x10);
    }

    /** 바닐라 RailShape 서수. 엔진 RAIL_* 상수와 같은 값이다. */
    private static int railShape(String exact) {
        String shape = property(exact, "shape");
        if (shape == null) return BuildingBlockRules.RAIL_NORTH_SOUTH;
        return switch (shape) {
            case "east_west" -> BuildingBlockRules.RAIL_EAST_WEST;
            case "ascending_east" -> BuildingBlockRules.RAIL_ASCENDING_EAST;
            case "ascending_west" -> BuildingBlockRules.RAIL_ASCENDING_WEST;
            case "ascending_north" -> BuildingBlockRules.RAIL_ASCENDING_NORTH;
            case "ascending_south" -> BuildingBlockRules.RAIL_ASCENDING_SOUTH;
            case "south_east" -> BuildingBlockRules.RAIL_SOUTH_EAST;
            case "south_west" -> BuildingBlockRules.RAIL_SOUTH_WEST;
            case "north_west" -> BuildingBlockRules.RAIL_NORTH_WEST;
            case "north_east" -> BuildingBlockRules.RAIL_NORTH_EAST;
            default -> BuildingBlockRules.RAIL_NORTH_SOUTH;
        };
    }
}
