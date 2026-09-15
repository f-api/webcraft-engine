package com.gameexpert.engine;

import com.gameexpert.engine.redstone.RedstoneShapes;

import com.gameexpert.engine.blocks.P2Rules;
import com.gameexpert.engine.blocks.P6Rules;
import com.gameexpert.engine.blocks.P29Rules;
import com.gameexpert.engine.blocks.CandleRules;
import com.gameexpert.engine.sculk.DriedGhastHydration;
import com.gameexpert.engine.trial.TrialVaultContract;
import com.gameexpert.terrain.Blocks;

/**
 * 기본 건축 블록의 상태 규약. ID는 재질만 나타내고, 방향·상하·연결·개폐는
 * 이 클래스의 1 byte state로 표현한다. 방향은 항상 0=N, 1=E, 2=S, 3=W이다.
 */
public final class BuildingBlockRules {

    public static final int FACING_MASK = 0x03;

    // stair: facing[0..1], top[2], shape[3..5]
    public static final int STAIR_TOP = 0x04;
    public static final int STAIR_SHAPE_SHIFT = 3;
    public static final int STAIR_STRAIGHT = 0;
    public static final int STAIR_INNER_LEFT = 1;
    public static final int STAIR_INNER_RIGHT = 2;
    public static final int STAIR_OUTER_LEFT = 3;
    public static final int STAIR_OUTER_RIGHT = 4;

    // slab: 전체 state 값. 같은 재질의 반 블록만 합쳐진다.
    public static final int SLAB_BOTTOM = 0;
    public static final int SLAB_TOP = 1;
    public static final int SLAB_DOUBLE = 2;

    // fence/wall/pane/bars: 연결 mask NESW bits 0..3.
    public static final int CONNECT_NORTH = 1;
    public static final int CONNECT_EAST = 2;
    public static final int CONNECT_SOUTH = 4;
    public static final int CONNECT_WEST = 8;

    // gate: facing[0..1], open[2], in-wall[3]
    public static final int GATE_OPEN = 0x04;
    public static final int GATE_IN_WALL = 0x08;
    // trapdoor: facing[0..1], top[2], open[3]
    public static final int TRAPDOOR_TOP = 0x04;
    public static final int TRAPDOOR_OPEN = 0x08;
    // door: facing[0..1], open[2], upper[3], right-hinge[4]
    public static final int DOOR_OPEN = 0x04;
    public static final int DOOR_UPPER = 0x08;
    public static final int DOOR_HINGE_RIGHT = 0x10;
    // campfire: facing[0..1], lit[2]
    public static final int CAMPFIRE_LIT = 0x04;
    // bed: facing[0..1] is foot→head, head[2].
    public static final int BED_HEAD = 0x04;
    // chest: facing[0..1], type[2..3]. LEFT/RIGHT are named while looking at the front.
    public static final int CHEST_SINGLE = 0;
    public static final int CHEST_LEFT = 0x04;
    public static final int CHEST_RIGHT = 0x08;
    public static final int CHEST_TYPE_MASK = 0x0c;
    // lantern (iron + copper family): hanging[0], waterlogged[7]. 26.3 LanternBlock.HANGING.
    public static final int LANTERN_HANGING = 0x01;
    // [VANILLA-STATION] loom: facing[0..1]. bee nest / beehive: facing[0..1], honey_level[2..4]
    // (0..5, BeehiveBlock.MAX_HONEY_LEVELS).
    public static final int BEEHIVE_HONEY_SHIFT = 2;
    public static final int BEEHIVE_HONEY_MASK = 0x1c;
    public static final int BEEHIVE_MAX_HONEY = 5;
    // [BARREL-STATE] barrel: facing[0..2] in the lightning-rod / amethyst six-direction vocabulary
    // (0 up, 1 down, 2 north, 3 east, 4 south, 5 west), open[3]. Zero is facing=up, open=false so a
    // barrel saved before the state existed (state 0) keeps the look it always had.
    public static final int BARREL_FACING_MASK = 0x07;
    public static final int BARREL_OPEN = 0x08;
    public static final int BARREL_FACING_UP = 0;
    public static final int BARREL_FACING_DOWN = 1;
    public static final int BARREL_FACING_NORTH = 2;
    public static final int BARREL_FACING_EAST = 3;
    public static final int BARREL_FACING_SOUTH = 4;
    public static final int BARREL_FACING_WEST = 5;

    // rail: vanilla RailShape ordinal/state values.
    public static final int RAIL_NORTH_SOUTH = 0;
    public static final int RAIL_EAST_WEST = 1;
    public static final int RAIL_ASCENDING_EAST = 2;
    public static final int RAIL_ASCENDING_WEST = 3;
    public static final int RAIL_ASCENDING_NORTH = 4;
    public static final int RAIL_ASCENDING_SOUTH = 5;
    public static final int RAIL_SOUTH_EAST = 6;
    public static final int RAIL_SOUTH_WEST = 7;
    public static final int RAIL_NORTH_WEST = 8;
    public static final int RAIL_NORTH_EAST = 9;

    private BuildingBlockRules() {
    }

    /**
     * 클라이언트가 보낸 state의 예약 비트를 제거한다.
     *
     * <p>비트 7({@code 0x80})은 물담김을 지원하는 형상군의 waterlogged 비트다(`docs/CONTRACT.md` §4.1).
     * 물을 품을 수 없는 블록에서는 예약 비트이므로 여기서 잘라낸다 — 그렇지 않으면 조작 클라가
     * 아무 블록에나 비트 7 을 실어 가짜 수원을 그리게 만들 수 있다.
     */
    public static int normalizeState(int blockId, int state) {
        int redstone = com.gameexpert.engine.redstone.RedstoneState.normalizeBlockState(blockId, state);
        if (redstone >= 0) return redstone;
        int normalized = normalizeShapeState(blockId, state);
        return canCarryWaterloggedBit(blockId) ? normalized : normalized & ~WATERLOGGED;
    }

    /** 모든 형상군 공통 waterlogged 비트. */
    public static final int WATERLOGGED = 0x80;

    /**
     * 게임플레이가 이 블록의 state 에 물을 담을 수 있는가. carrier 투영은 이 술어를 거치지 않고
     * exact state 에서 직접 비트를 세우므로, 여기서는 <b>플레이어가 보낸</b> state 만 판정한다.
     * 정적판 {@code normalizeStandaloneBlockState} 와 같은 집합이어야 한다.
     */
    public static boolean isPost7Waterloggable(int blockId) {
        return blockId >= Blocks.ORANGE_WOOL_STAIRS && blockId <= Blocks.BLACK_CONCRETE_SLAB;
    }

    /** Double slabs are full cubes and cannot accept bucket water. */
    public static boolean canAcceptWater(int blockId, int state) {
        return canCarryWaterloggedBit(blockId)
                && (!Blocks.isConcreteSlab(blockId) || (state & 3) != SLAB_DOUBLE);
    }

    private static boolean canCarryWaterloggedBit(int blockId) {
        return CopperAgeRules.canWaterlog(blockId) || Blocks.isShelf(blockId) || isPost7Waterloggable(blockId);
    }

    private static int normalizeShapeState(int blockId, int state) {
        if (CandleRules.isCandle(blockId)) return state & 3;
        if (blockId >= Blocks.CLAY && blockId <= Blocks.ROOTED_DIRT) return 0;
        if (blockId == Blocks.BAMBOO) {
            return state & (P2Rules.BAMBOO_BELOW | P2Rules.BAMBOO_ABOVE);
        }
        if (blockId == Blocks.VINE) {
            return state & (P2Rules.VINE_NORTH | P2Rules.VINE_EAST | P2Rules.VINE_SOUTH
                    | P2Rules.VINE_WEST | P2Rules.VINE_HANGING);
        }
        // [PALE-GARDEN] 창백한 이끼 바닥도 기존 이끼 바닥과 같은 부분 높이 계약이라 상태 폭이 같다.
        if (blockId == Blocks.MOSS_CARPET || blockId == Blocks.PALE_MOSS_CARPET) return 2;
        if (blockId == Blocks.GLOW_LICHEN) return state & 0x3f;
        if (blockId == Blocks.BIG_DRIPLEAF) return state & 0x0f;
        // [WORLD-GEOMETRY] 비명체: 경고 단계(비트 0..1) + can_summon(비트 2).
        if (blockId == Blocks.SCULK_SHRIEKER) return state & 0x07;
        // [WORLD-GEOMETRY] 큰 흘림잎 줄기 · 작은 흘림잎은 수평 facing 2비트뿐이다.
        if (blockId == Blocks.BIG_DRIPLEAF_STEM || blockId == Blocks.SMALL_DRIPLEAF) return state & FACING_MASK;
        if (P6Rules.isSpeleothem(blockId)) {
            return state & P6Rules.DRIPSTONE_UP;
        }
        if (blockId == Blocks.CAVE_VINES || blockId == Blocks.CAVE_VINES_PLANT) {
            return blockId == Blocks.CAVE_VINES
                    ? state & (P6Rules.CAVE_VINES_BERRIES | P6Rules.CAVE_VINES_AGE_MASK)
                    : state & P6Rules.CAVE_VINES_BERRIES;
        }
        if (blockId == Blocks.MANGROVE_PROPAGULE) {
            return state & (P6Rules.PROPAGULE_HANGING | P6Rules.PROPAGULE_AGE_MASK);
        }
        if (BlockFamilies.isStateOrientedWoodLog(blockId)) {
            int axis = state & P6Rules.LOG_AXIS_MASK;
            return axis <= 2 ? axis : 0;
        }
        if (P6Rules.isAmethystBud(blockId)) {
            return state >= P6Rules.AMETHYST_FACING_UP && state <= P6Rules.AMETHYST_FACING_WEST
                    ? state : P6Rules.AMETHYST_FACING_UP;
        }
        // 종별 목재 세트가 들어오면서 형상군은 단일 ID case 로 열거할 수 없다.
        // 판정 정본은 Blocks 의 형상군 헬퍼 한 곳이고, 마스크는 참나무 총칭 세트와 같다.
        // [BLOCK-SHAPES] shape(비트 3..5)도 어휘다: 생성 구조물의 모서리 계단은 carrier 의 shape 를
        // 그대로 싣고, 설치는 refreshStairs 가 이웃으로 다시 정한다. 어휘 밖 shape(5..7)는 straight.
        if (isStairs(blockId)) {
            int shape = (state >> STAIR_SHAPE_SHIFT) & 7;
            return state & 0x07 | (shape <= STAIR_OUTER_RIGHT ? shape << STAIR_SHAPE_SHIFT : 0)
                    | (isPost7Waterloggable(blockId) ? state & WATERLOGGED : 0);
        }
        if (isSlab(blockId)) {
            int type = Math.min(state & 0x03, SLAB_DOUBLE);
            return type | (isPost7Waterloggable(blockId) && type != SLAB_DOUBLE ? state & WATERLOGGED : 0);
        }
        if (isWall(blockId) || Blocks.isFence(blockId) || Blocks.isFenceGate(blockId)) {
            return state & 0x0f;
        }
        if (Blocks.isTrapdoor(blockId)) {
            return state & (FACING_MASK | TRAPDOOR_TOP | TRAPDOOR_OPEN);
        }
        if (Blocks.isDoor(blockId)) return state & 0x1f;
        // [BED-COLOR] 색 침대도 총칭 침대와 같은 state 어휘다(facing 2비트 + 머리 비트).
        if (Blocks.isBed(blockId)) return state & (FACING_MASK | BED_HEAD);
        // [CHEST-FAMILY] 상자 형상군 전체가 같은 state 어휘다(정면 2비트 + 좌우 짝 2비트).
        if (Blocks.isChestShaped(blockId)) return state & (FACING_MASK | CHEST_TYPE_MASK);
        if (Blocks.isAnvil(blockId)) return state & FACING_MASK;
        if (Blocks.isDecoratedPot(blockId)) return state & FACING_MASK;
        // [FURNACE-VARIANT] 제련로 세 변형(점화 쌍둥이 포함)의 state 어휘는 정면 방위 2비트뿐이다
        // (점화는 별도 블록 ID 라 state 비트가 아니다). 여섯 ID 를 손으로 열거하지 않고 변형 표
        // 한 곳만 물어, 클라가 보낸 방위를 그대로 확정하되 나머지 비트는 영속에 남기지 않는다.
        if (FurnaceVariant.of(blockId) != null) return state & FACING_MASK;
        // [VANILLA-FLAME] 랜턴 계열의 형상 어휘는 hanging 한 비트다. waterlogged 는 요청 바이트가
        // 아니라 설치 칸의 유체에서 다시 정한다(바닐라 getStateForPlacement 가 FluidState 로
        // WATERLOGGED 를 세운다). 그래서 여기서 비트 7 까지 잘라 조작 클라가 마른 칸에 물을 심지
        // 못하게 한다 — 설치 경로가 교체한 블록이 물이면 비트를 다시 붙인다.
        if (isLantern(blockId)) return state & LANTERN_HANGING;
        // Shelf: FACING(0..1), POWERED(2), SIDE_CHAIN_PART(3..4), WATERLOGGED(7).
        if (Blocks.isShelf(blockId)) return state & 0x9f;
        // Shelf mushroom: horizontal FACING(0..1) and AGE(2).
        if (Blocks.isShelfMushroom(blockId)) {
            return state & (FACING_MASK | P29Rules.AGE);
        }
        // [STAINED-GLASS] 색 유리판 16색도 유리판·철창과 같은 4비트 연결 어휘다.
        if (isPane(blockId)) return state & 0x0f;
        // [VANILLA-STATION] 직조기는 수평 facing 뿐이고, 벌집·벌통은 facing 에 honey_level 0..5 가
        // 붙는다. 범위 밖 꿀 단계(6·7)는 빈 벌집으로 되돌린다.
        if (blockId == Blocks.LOOM) return state & FACING_MASK;
        if (blockId == Blocks.BEE_NEST || blockId == Blocks.BEEHIVE) {
            int honey = (state & BEEHIVE_HONEY_MASK) >>> BEEHIVE_HONEY_SHIFT;
            return honey <= BEEHIVE_MAX_HONEY
                    ? state & (FACING_MASK | BEEHIVE_HONEY_MASK) : state & FACING_MASK;
        }
        // [BARREL-STATE] 통: 6방향 facing(범위 밖은 up) + open.
        if (blockId == Blocks.BARREL) return barrelFacing(state) | state & BARREL_OPEN;
        // [HOPPER] FACING(0..2, Direction 3D value; up folds to down) + !ENABLED(3).
        if (blockId == Blocks.HOPPER) {
            return com.gameexpert.engine.hopper.HopperRules.normalizeState(state);
        }
        // [CONTAINER-MENUS] DispenserBlock (dispenser, dropper): FACING 0..5 + TRIGGERED(3).
        if (com.gameexpert.engine.dispenser.DispenserRules.isDispenserFamily(blockId)) {
            return com.gameexpert.engine.dispenser.DispenserRules.normalizeState(state);
        }
        // [CONTAINER-MENUS] CrafterBlock: ORIENTATION code 0..11 + TRIGGERED(4) + CRAFTING(5).
        if (blockId == Blocks.CRAFTER) {
            return com.gameexpert.engine.dispenser.CrafterRules.normalizeState(state);
        }
        // [VOID-END] 보라 기둥 축 0..2(뼈 블록과 같은 어휘), 엔드 막대 6방향 facing(범위 밖은 up),
        // 후렴 식물 6연결 비트, 후렴 꽃 AGE 0..5(범위 밖은 0).
        if (blockId == Blocks.PURPUR_PILLAR) return Math.min(state & FACING_MASK, 2);
        if (blockId == Blocks.END_ROD) return (state & 0x07) <= 5 ? state & 0x07 : 0;
        if (blockId == Blocks.CHORUS_PLANT) return state & 0x3f;
        if (blockId == Blocks.CHORUS_FLOWER) return (state & 0x07) <= 5 ? state & 0x07 : 0;
        // [VOID-END] 엔드 관문 state 는 목적지 종류 0..2(VoidEndBlockRules.GATEWAY_*, 범위 밖은 0).
        if (blockId == Blocks.END_GATEWAY) return (state & 0x03) <= 2 ? state & 0x03 : 0;
        // [END-CITY] 드래곤 머리 0..15 바닥 방위 · 16..19 벽 facing(범위 밖은 0), 자홍색 벽 현수막 facing.
        if (blockId == Blocks.DRAGON_HEAD) return (state & 0x1f) <= 19 ? state & 0x1f : 0;
        if (blockId == Blocks.MAGENTA_WALL_BANNER) return state & FACING_MASK;
        return switch (blockId) {
            case Blocks.PUMPKIN, Blocks.CARVED_PUMPKIN, Blocks.JACK_O_LANTERN -> state & FACING_MASK;
            // 찢어진 군기는 방향만 보존한다. 분재는 방향 없는 장식이라 상태를 쓰지 않는다.
            case Blocks.TATTERED_BANNER -> state & FACING_MASK;
            case Blocks.CHERRY_BONSAI -> 0;
            case Blocks.BONE_BLOCK -> Math.min(state & FACING_MASK, 2);
            case Blocks.RAIL -> Math.min(state & 0x0f, 9);
            case Blocks.CAMPFIRE -> state & 0x07;
            // [COOKING] 케이크의 state 어휘는 바닐라 bites 0~6 뿐이다. 3비트로는 7 도 담기므로
            // 범위 밖은 온전한 케이크(0)로 되돌린다 — 조작 클라가 존재하지 않는 조각 수를
            // 영속에 심지 못하게 하는 자리다.
            case Blocks.CAKE -> CakeRules.isValidBites(state & 0x07) ? state & 0x07 : 0;
            // 엔드 차원문 틀: FACING[0..1] + EYE[2] (EndPortalFrameBlock.FACING / HAS_EYE).
            case Blocks.END_PORTAL_FRAME -> state & (FACING_MASK | EndPortalFrameRules.EYE);
            // [DEEP-DARK] 말린 가스트: HYDRATION[0..1] + FACING[2..3] (DriedGhastBlock).
            case Blocks.DRIED_GHAST -> state & (DriedGhastHydration.HYDRATION_MASK
                    | DriedGhastHydration.FACING_MASK);
            // [TRIAL] 금고: VAULT_STATE[0..1] + FACING[2..3] + OMINOUS[4] (VaultBlock).
            case Blocks.VAULT -> state & (TrialVaultContract.STATE_MASK
                    | TrialVaultContract.FACING_MASK | TrialVaultContract.OMINOUS);
            // [TRIAL] 트라이얼 스포너: 상태 코드[0..2](6·7 은 대기로 접는다) + OMINOUS[3].
            case Blocks.TRIAL_SPAWNER -> com.gameexpert.engine.trial.TrialSpawnerContract
                    .normalizeState(state);
            // [FROST-SOUL] 살얼음 AGE 0..3(FrostedIceBlock.AGE). 영혼 모래·영혼 흙은 속성이 없다.
            case Blocks.FROSTED_ICE -> state & 0x03;
            case Blocks.SOUL_SAND, Blocks.SOUL_SOIL -> 0;
            // [BLOCK-SHAPES] 모델 블록의 state 어휘(BlockModelShapes). 어휘 밖 facing/face 는 기본값으로.
            case Blocks.LECTERN -> state & (FACING_MASK | BlockModelShapes.LECTERN_HAS_BOOK);
            case Blocks.GRINDSTONE -> state & FACING_MASK
                    | BlockModelShapes.grindstoneFace(state) << BlockModelShapes.GRINDSTONE_FACE_SHIFT;
            case Blocks.BELL -> state & (FACING_MASK | 3 << BlockModelShapes.BELL_ATTACHMENT_SHIFT);
            case Blocks.BREWING_STAND -> state & BlockModelShapes.BREWING_STAND_BOTTLE_MASK;
            case Blocks.WATER_CAULDRON -> state & BlockModelShapes.WATER_CAULDRON_LEVEL_MASK;
            case Blocks.STONECUTTER -> state & FACING_MASK;
            // [UTILITY] 주크박스 HAS_RECORD[0](JukeboxBlock.HAS_RECORD). 슬라임 블록은 속성이 없다.
            // [BEACON] 신호기 state 는 주/보조 효과 코드(BeaconRules, 비트 0..5)다.
            case Blocks.JUKEBOX -> com.gameexpert.engine.jukebox.JukeboxRules.normalizeState(state);
            case Blocks.BEACON -> BeaconRules.normalizeState(state);
            case Blocks.SLIME_BLOCK -> 0;
            // [BLOCK-ENTITY] 포플러 표지판: 서기 ROTATION[0..3] 또는 벽(bit 4) + FACING[0..1]. 매달린 표지판은
            // 천장이면 ROTATION[0..3] + ATTACHED(bit 5), 벽이면 bit 4 + FACING (클라 signPlacement 어휘).
            case Blocks.POPLAR_SIGN -> (state & 0x10) != 0 ? state & 0x13 : state & 0x0f;
            case Blocks.POPLAR_HANGING_SIGN -> (state & 0x10) != 0 ? state & 0x13 : state & 0x2f;
            default -> Blocks.isShulkerBox(blockId)
                    // [BLOCK-ENTITY] 셜커 상자 FACING 은 6방향(0 up · 1 down · 2 N · 3 E · 4 S · 5 W).
                    ? (state & 0x07) <= 5 ? state & 0x07 : 0
                    : state & 0xff;
        };
    }

    /**
     * 플레이어가 아이템으로 놓는 블록의 state. {@link #normalizeState} 는 블록의 <b>어휘</b>를 지키고,
     * 이 함수는 그중 설치가 정할 수 있는 부분만 남긴다 — 바닐라 {@code getStateForPlacement} 가
     * 정하는 facing 만 클라이언트 값을 받아들이고, 나머지는 기본값이다. 이 저장소의 아이템은
     * {@code BLOCK_STATE} 컴포넌트를 싣지 않으므로(바닐라 벌집·벌통 아이템의 기본값도
     * {@code BlockItemStateProperties.EMPTY}) 꿀 단계는 0, 통은 닫힌 채로 놓인다. 통의 open 은
     * 열람자 수로만 서고, 꿀은 벌이 채운다.
     *
     * @param state {@link #normalizeState} 를 거친 요청 state
     */
    public static int normalizePlacementState(int blockId, int state) {
        if (blockId == Blocks.BEE_NEST || blockId == Blocks.BEEHIVE || blockId == Blocks.LOOM) {
            return state & FACING_MASK;
        }
        if (blockId == Blocks.BARREL) return barrelFacing(state);
        // [HOPPER] HopperBlock#getStateForPlacement: facing from the clicked face, ENABLED=true
        // (the placement caller re-evaluates it from the neighbour signal like onPlace).
        if (blockId == Blocks.HOPPER) {
            return com.gameexpert.engine.hopper.HopperRules.state(
                    com.gameexpert.engine.hopper.HopperRules.facing(state), true);
        }
        // [CONTAINER-MENUS] DispenserBlock#getStateForPlacement: FACING only, TRIGGERED=false.
        if (com.gameexpert.engine.dispenser.DispenserRules.isDispenserFamily(blockId)) {
            return com.gameexpert.engine.dispenser.DispenserRules.placementState(state);
        }
        // [CONTAINER-MENUS] CrafterBlock#getStateForPlacement: ORIENTATION from the look vector,
        // TRIGGERED and CRAFTING false; CrafterSystem's evaluation of the placed cell then applies
        // the hasNeighborSignal read (TRIGGERED + the setPlacedBy schedule) at the end of the tick.
        if (blockId == Blocks.CRAFTER) {
            return com.gameexpert.engine.dispenser.CrafterRules.orientation(state);
        }
        // [VOID-END] 후렴 꽃 아이템은 BLOCK_STATE 컴포넌트가 없어 AGE 0 으로 놓인다. 후렴 식물의
        // 연결은 이웃이 정하는 파생 상태라 요청 값을 받지 않는다(설치 후 이웃 갱신이 다시 계산한다).
        if (blockId == Blocks.CHORUS_FLOWER || blockId == Blocks.CHORUS_PLANT) return 0;
        // [DEEP-DARK] DriedGhastBlock#getStateForPlacement 는 facing 만 정하고 hydration 은 0 이다.
        if (blockId == Blocks.DRIED_GHAST) return state & DriedGhastHydration.FACING_MASK;
        // [WORLD-GEOMETRY] 설치한 비명체는 can_summon=false · 경고 단계 0 이다(SculkShriekerBlock 기본 상태).
        if (blockId == Blocks.SCULK_SHRIEKER) return 0;
        // [BLOCK-SHAPES] 설치가 정하는 것은 독서대·석재 절단기 facing(책 없음), 숫돌 face+facing, 종
        // attachment+facing 뿐이다(호퍼는 위 [HOPPER] 가 정한다). 양조대는 병 없이 놓인다.
        if (blockId == Blocks.LECTERN || blockId == Blocks.STONECUTTER) return state & FACING_MASK;
        if (blockId == Blocks.BREWING_STAND) return 0;
        // [BEACON] 새 신호기 블록 엔티티는 효과가 비어 있다(primary/secondary = null). 효과는 신호기
        // 화면의 확정(BeaconMenu.updateEffects)으로만 선다 — 조작 클라가 효과 켜진 신호기를 놓지 못한다.
        if (blockId == Blocks.BEACON) return 0;
        return state;
    }

    /** 통 state 의 facing(0..5). 어휘 밖 값은 up 이다. */
    public static int barrelFacing(int state) {
        int facing = state & BARREL_FACING_MASK;
        return facing <= BARREL_FACING_WEST ? facing : BARREL_FACING_UP;
    }

    /** 통 state 에서 open 비트만 바꾼다. */
    public static int withBarrelOpen(int state, boolean open) {
        return open ? state | BARREL_OPEN : state & ~BARREL_OPEN;
    }

    /**
     * [BARREL-SOUND] 바닐라 {@code BarrelBlockEntity#playSound} 의 음원 위치: 블록 중심에
     * {@code facing.getUnitVec3i() / 2.0} 을 더한 정면 가운데다(26.3-snapshot-7 javap).
     * 통 facing 어휘는 0 up · 1 down · 2 north(−Z) · 3 east(+X) · 4 south(+Z) · 5 west(−X).
     *
     * @return {x, y, z}
     */
    public static double[] barrelSoundPosition(int x, int y, int z, int state) {
        int facing = barrelFacing(state);
        int dx = facing == BARREL_FACING_EAST ? 1 : facing == BARREL_FACING_WEST ? -1 : 0;
        int dy = facing == BARREL_FACING_UP ? 1 : facing == BARREL_FACING_DOWN ? -1 : 0;
        int dz = facing == BARREL_FACING_SOUTH ? 1 : facing == BARREL_FACING_NORTH ? -1 : 0;
        return new double[] {x + 0.5 + dx / 2.0, y + 0.5 + dy / 2.0, z + 0.5 + dz / 2.0};
    }

    // 심층암 가공 계열은 형상 계약을 새로 만들지 않고 기존 계단·반 블록·담장 계열에 합류한다.
    // ID 배정을 형상군별 연속 구간으로 잡아 두어 판정이 범위 하나만 더 보면 된다.
    public static boolean isStairs(int id) {
        return Blocks.isWoodStairs(id) || Blocks.isWoolStairs(id) || Blocks.isConcreteStairs(id)
                || id >= Blocks.WOOD_STAIRS && id <= Blocks.SANDSTONE_STAIRS
                || id >= Blocks.POLISHED_DEEPSLATE_STAIRS && id <= Blocks.DEEPSLATE_TILE_STAIRS
                || id >= Blocks.BIRCH_STAIRS && id <= Blocks.MANGROVE_STAIRS
                || id >= Blocks.GRANITE_STAIRS && id <= Blocks.MOSSY_STONE_BRICK_STAIRS
                || id >= Blocks.POLISHED_GRANITE_STAIRS && id <= Blocks.TUFF_BRICK_STAIRS
                // [PRISMARINE] 프리즈머린 계단 3종(939~941)도 같은 계단 계약이다.
                || id >= Blocks.PRISMARINE_STAIRS && id <= Blocks.DARK_PRISMARINE_STAIRS
                // [QUARTZ] 석영 계단 2종(969~970)도 같은 계단 계약이다.
                || id >= Blocks.QUARTZ_STAIRS && id <= Blocks.SMOOTH_QUARTZ_STAIRS
                // [COPPER] 잘린 구리 계단 산화 4단계(991~994)와 그 밀랍 대응(1031~1034).
                || id >= Blocks.CUT_COPPER_STAIRS && id <= Blocks.OXIDIZED_CUT_COPPER_STAIRS
                || id >= Blocks.WAXED_CUT_COPPER_STAIRS
                        && id <= Blocks.WAXED_OXIDIZED_CUT_COPPER_STAIRS
                || id == Blocks.POLISHED_SULFUR_STAIRS
                || id == Blocks.SULFUR_BRICK_STAIRS
                || id == Blocks.SULFUR_STAIRS
                || id == Blocks.CINNABAR_BRICK_STAIRS || id == Blocks.CINNABAR_STAIRS
                || id == Blocks.POLISHED_CINNABAR_STAIRS
                // [VOID-END] 엔드 돌 벽돌·보라 계단(2293~2294).
                || id >= Blocks.END_STONE_BRICK_STAIRS && id <= Blocks.PURPUR_STAIRS;
    }

    public static boolean isSlab(int id) {
        // 목재 반 블록은 여덟 수종 표 밖(창백한 참나무 1498 · 포플러 1551)이 있어 연속 구간
        // 열거로는 새지만, 정본 집합은 Blocks.isWoodSlab 하나뿐이다 — 그 술어에서 파생한다.
        return Blocks.isWoodSlab(id) || id == Blocks.COBBLE_SLAB
                || id == Blocks.STONE_BRICK_SLAB || id == Blocks.SANDSTONE_SLAB
                || id >= Blocks.POLISHED_DEEPSLATE_SLAB && id <= Blocks.DEEPSLATE_TILE_SLAB
                || id >= Blocks.GRANITE_SLAB && id <= Blocks.MOSSY_STONE_BRICK_SLAB
                || id >= Blocks.POLISHED_GRANITE_SLAB && id <= Blocks.TUFF_BRICK_SLAB
                // [PRISMARINE] 프리즈머린 반 블록 3종(942~944).
                || id >= Blocks.PRISMARINE_SLAB && id <= Blocks.DARK_PRISMARINE_SLAB
                // [QUARTZ] 석영 반 블록 2종(971~972).
                || id >= Blocks.QUARTZ_SLAB && id <= Blocks.SMOOTH_QUARTZ_SLAB
                // [COPPER] 잘린 구리 반 블록 산화 4단계(995~998)와 그 밀랍 대응(1035~1038).
                || id >= Blocks.CUT_COPPER_SLAB && id <= Blocks.OXIDIZED_CUT_COPPER_SLAB
                || id >= Blocks.WAXED_CUT_COPPER_SLAB
                        && id <= Blocks.WAXED_OXIDIZED_CUT_COPPER_SLAB
                // [WOOL-SLAB] 양털 반 블록 16색(1560~1575)도 같은 반 블록 상태 어휘를 쓴다.
                // 재질만 양털이고 bottom/top/double 세 상태는 돌·목재 반 블록과 한 글자도 다르지 않다.
                || Blocks.isWoolSlab(id) || Blocks.isConcreteSlab(id)
                || id == Blocks.POLISHED_SULFUR_SLAB
                || id == Blocks.SULFUR_BRICK_SLAB
                || id == Blocks.SULFUR_SLAB
                || id == Blocks.CINNABAR_BRICK_SLAB || id == Blocks.CINNABAR_SLAB
                || id == Blocks.POLISHED_CINNABAR_SLAB
                // [VOID-END] 엔드 돌 벽돌·보라 반 블록(2295~2296).
                || id >= Blocks.END_STONE_BRICK_SLAB && id <= Blocks.PURPUR_SLAB;
    }

    public static boolean isWall(int id) {
        return id >= Blocks.COBBLE_WALL && id <= Blocks.STONE_BRICK_WALL
                || id >= Blocks.POLISHED_DEEPSLATE_WALL && id <= Blocks.DEEPSLATE_TILE_WALL
                || id >= Blocks.GRANITE_WALL && id <= Blocks.MOSSY_STONE_BRICK_WALL
                || id >= Blocks.BRICK_WALL && id <= Blocks.TUFF_BRICK_WALL
                // [PRISMARINE] 바닐라에 담장이 있는 프리즈머린 재질은 하나뿐이다(945).
                || id == Blocks.PRISMARINE_WALL
                // [QUARTZ] 석영 담장 3종(973~975). 바닐라에 없는 WebCraft 고유 추가라
                // 형상·제작·물성은 기존 담장 계약과 모재값을 그대로 쓴다(MC-REFERENCE).
                || id >= Blocks.QUARTZ_WALL && id <= Blocks.QUARTZ_BRICK_WALL
                || id == Blocks.POLISHED_SULFUR_WALL
                || id == Blocks.SULFUR_BRICK_WALL
                || id == Blocks.SULFUR_WALL
                || id == Blocks.CINNABAR_BRICK_WALL || id == Blocks.CINNABAR_WALL
                || id == Blocks.POLISHED_CINNABAR_WALL
                // [VOID-END] 바닐라 엔드 돌 계열의 담장은 벽돌 담장 하나뿐이다.
                || id == Blocks.END_STONE_BRICK_WALL;
    }

    public static boolean isPane(int id) {
        // [STAINED-GLASS] 색 유리판 16색도 같은 GlassPaneBlock 계약이라 여기 들어온다.
        // [OPENABLE-METAL] 구리 창살 8종도 바닐라에서 철창과 **같은 IronBarsBlock** 이라
        // 연결·형상·충돌이 한 계약이다(새 형상을 만들지 않는 근거).
        return Blocks.isGlassPane(id) || id == Blocks.IRON_BARS || Blocks.isCopperBars(id);
    }

    public static boolean canMergeSlab(int currentId, int currentState, int placedId, int placedState) {
        return currentId == placedId && isSlab(currentId)
                && (currentState == SLAB_BOTTOM || currentState == SLAB_TOP)
                && (placedState == SLAB_DOUBLE
                        || placedState == SLAB_BOTTOM || placedState == SLAB_TOP)
                && (placedState == SLAB_DOUBLE || currentState != placedState);
    }

    /**
     * MC stair shape 유도(StairBlock#getStairsShape): 윗단이 있는 쪽의 다른 축 계단을 외각으로
     * 먼저 판정하고, 없으면 그 반대쪽을 내각으로 판정한다. 재질은 달라도 연결하지만
     * top/bottom half는 같아야 한다.
     *
     * <p>바닐라 FACING은 윗단이 있는 쪽을 가리키지만 이 프로젝트의 state facing은 그 반대쪽이다
     * (straight mask=[12,9,3,6] — facing 반대편 절반이 윗단). 그래서 바닐라가 FACING 쪽 이웃을
     * 보는 자리에서 우리는 opposite(facing) 쪽 이웃을 봐야 한다. 좌/우 명칭은 뒤집히지 않는다:
     * 바닐라 조건 opp(nb)==left(opp(me))는 nb==left(me)와 같다.</p>
     */
    public static int stairShape(int x, int y, int z, int state, StateLookup world) {
        int facing = state & FACING_MASK;
        int half = state & STAIR_TOP;
        int upper = opposite(facing);
        int fx = x + dx(upper), fz = z + dz(upper);
        int frontId = world.block(fx, y, fz);
        int frontState = world.state(fx, y, fz, frontId);
        if (isStairs(frontId) && (frontState & STAIR_TOP) == half
                && axis(frontState) != axis(state)
                && differentStairOnSide(x, y, z, state, frontState & FACING_MASK, world)) {
            return (frontState & FACING_MASK) == left(facing) ? STAIR_OUTER_LEFT : STAIR_OUTER_RIGHT;
        }

        int bx = x + dx(facing), bz = z + dz(facing);
        int backId = world.block(bx, y, bz);
        int backState = world.state(bx, y, bz, backId);
        if (isStairs(backId) && (backState & STAIR_TOP) == half
                && axis(backState) != axis(state)
                && differentStairOnSide(x, y, z, state, opposite(backState & FACING_MASK), world)) {
            return (backState & FACING_MASK) == left(facing) ? STAIR_INNER_LEFT : STAIR_INNER_RIGHT;
        }
        return STAIR_STRAIGHT;
    }

    private static boolean differentStairOnSide(int x, int y, int z, int state,
            int side, StateLookup world) {
        int id = world.block(x + dx(side), y, z + dz(side));
        if (!isStairs(id)) return true;
        int other = world.state(x + dx(side), y, z + dz(side), id);
        return (other & (FACING_MASK | STAIR_TOP)) != (state & (FACING_MASK | STAIR_TOP));
    }

    /**
     * 인접 조합에서 연결 mask(NESW bits 0..3)를 유도한다. 울타리 문은 facing 에 따라 연결 방향이
     * 갈리고, [BLOCK-SHAPES] 이웃 면의 sturdy 여부(계단 등받이·반 블록)는 state 로 정해지므로 이웃
     * state 까지 읽는다.
     */
    public static int connectionMask(int blockId, int x, int y, int z, StateLookup world) {
        int mask = 0;
        for (int direction = 0; direction < 4; direction++) {
            int nx = x + dx(direction), nz = z + dz(direction);
            int neighbor = world.block(nx, y, nz);
            int neighborState = neighbor >= 0 ? world.state(nx, y, nz, neighbor) : 0;
            if (connects(blockId, neighbor, neighborState, direction)) {
                mask |= connectionBit(direction);
            }
        }
        return mask;
    }

    /** 덩굴 부착면은 현재 권위 월드에서 유도하며, 아래로 자란 셀은 위 셀의 면을 이어받는다. */
    public static int vineState(int x, int y, int z, StateLookup world) {
        int above = world.block(x, y + 1, z);
        int inherited = above == Blocks.VINE
                ? world.state(x, y + 1, z, Blocks.VINE)
                        & (P2Rules.VINE_NORTH | P2Rules.VINE_EAST
                                | P2Rules.VINE_SOUTH | P2Rules.VINE_WEST)
                : 0;
        int state = inherited;
        if (Fluids.isSolid(world.block(x, y, z - 1))) state |= P2Rules.VINE_NORTH;
        if (Fluids.isSolid(world.block(x + 1, y, z))) state |= P2Rules.VINE_EAST;
        if (Fluids.isSolid(world.block(x, y, z + 1))) state |= P2Rules.VINE_SOUTH;
        if (Fluids.isSolid(world.block(x - 1, y, z))) state |= P2Rules.VINE_WEST;
        if (Fluids.isSolid(above)) {
            state |= P2Rules.VINE_HANGING;
        }
        return state & (P2Rules.VINE_NORTH | P2Rules.VINE_EAST | P2Rules.VINE_SOUTH
                | P2Rules.VINE_WEST | P2Rules.VINE_HANGING);
    }

    /** Result of placing one chest. partnerDirection=-1 means a legal single chest. */
    public record ChestPlacement(int state, int partnerDirection, int partnerState) {
        public boolean paired() {
            return partnerDirection >= 0;
        }
    }

    /**
     * Derives the vanilla double-chest topology before inventory consumption. A normal placement
     * joins exactly one same-facing single chest on its left/right axis. It never creates a triple
     * chest or attaches to an already paired half. Unknown neighbor cells fail closed.
     *
     * <p>[CHEST-FAMILY] {@code blockId} 는 <b>놓이는 상자 자신의 ID</b> 다. 짝 판정은
     * {@link Blocks#chestPairs(int, int)} 하나만 보므로 덫 상자는 덫 상자끼리, 구리 상자는
     * 같은 산화·밀랍 단계끼리만 합쳐지고 일반 상자와는 섞이지 않는다. 이 인자가 생기기 전에는
     * {@code == Blocks.CHEST} 가 박혀 있어 형상군을 늘릴 수 없었다.
     */
    public static ChestPlacement chestPlacement(
            int blockId, int x, int y, int z, int requestedState, StateLookup world) {
        int facing = requestedState & FACING_MASK;
        int leftDirection = left(facing);
        int rightDirection = (facing + 1) & 3;
        int matchingDirection = -1;
        for (int direction : new int[] {leftDirection, rightDirection}) {
            int nx = x + dx(direction);
            int nz = z + dz(direction);
            int neighbor = world.block(nx, y, nz);
            if (neighbor < 0) return null;
            if (!Blocks.chestPairs(blockId, neighbor)) continue;
            int neighborState = world.state(nx, y, nz, neighbor);
            if ((neighborState & FACING_MASK) != facing) continue;
            if ((neighborState & CHEST_TYPE_MASK) != CHEST_SINGLE || matchingDirection >= 0) {
                return null;
            }
            matchingDirection = direction;
        }
        if (matchingDirection < 0) {
            return new ChestPlacement(facing | CHEST_SINGLE, -1, CHEST_SINGLE);
        }
        if (matchingDirection == leftDirection) {
            return new ChestPlacement(facing | CHEST_RIGHT, matchingDirection,
                    facing | CHEST_LEFT);
        }
        return new ChestPlacement(facing | CHEST_LEFT, matchingDirection,
                facing | CHEST_RIGHT);
    }

    public static int chestPartnerDirection(int state) {
        return switch (state & CHEST_TYPE_MASK) {
            case CHEST_LEFT -> (state + 1) & 3;
            case CHEST_RIGHT -> (state + 3) & 3;
            default -> -1;
        };
    }

    public static int chestPartnerX(int x, int state) {
        int direction = chestPartnerDirection(state);
        return direction < 0 ? x : x + dx(direction);
    }

    public static int chestPartnerZ(int z, int state) {
        int direction = chestPartnerDirection(state);
        return direction < 0 ? z : z + dz(direction);
    }

    public static boolean matchingChestStates(int state, int partnerState) {
        int type = state & CHEST_TYPE_MASK;
        int partnerType = partnerState & CHEST_TYPE_MASK;
        return (state & FACING_MASK) == (partnerState & FACING_MASK)
                && (type == CHEST_LEFT && partnerType == CHEST_RIGHT
                        || type == CHEST_RIGHT && partnerType == CHEST_LEFT);
    }

    /**
     * Invalid or missing paired halves deterministically return to a single chest.
     *
     * <p>[CHEST-FAMILY] {@code blockId} 는 상태를 다시 세우는 상자 자신의 ID 다. 짝이
     * {@link Blocks#chestPairs(int, int)} 를 만족하지 않으면(다른 갈래거나 다른 산화 단계면)
     * 이웃이 상자여도 단일 상자로 돌아간다.
     */
    public static int chestStateAfterNeighborChange(
            int blockId, int x, int y, int z, int state, StateLookup world) {
        int facing = state & FACING_MASK;
        int direction = chestPartnerDirection(state);
        if (direction < 0) return facing;
        int px = x + dx(direction);
        int pz = z + dz(direction);
        int partner = world.block(px, y, pz);
        if (partner < 0) return state;
        if (!Blocks.chestPairs(blockId, partner)) return facing;
        int partnerState = world.state(px, y, pz, partner);
        return matchingChestStates(state, partnerState) ? state : facing;
    }

    /**
     * Recomputes normal-rail topology from resident rails at the same, one-up, or one-down Y.
     * Shape ordering and ascending preference match RailState#place for an unpowered normal rail.
     */
    public static int railState(int x, int y, int z, int previousState, StateLookup world) {
        int northPresence = railPresence(x, y, z - 1, world);
        int southPresence = railPresence(x, y, z + 1, world);
        int westPresence = railPresence(x - 1, y, z, world);
        int eastPresence = railPresence(x + 1, y, z, world);
        if (northPresence < 0 || southPresence < 0 || westPresence < 0 || eastPresence < 0) {
            return previousState;
        }
        boolean north = northPresence != 0;
        boolean south = southPresence != 0;
        boolean west = westPresence != 0;
        boolean east = eastPresence != 0;
        boolean northSouth = north || south;
        boolean eastWest = west || east;
        int shape = -1;
        if (northSouth && !eastWest) shape = RAIL_NORTH_SOUTH;
        if (eastWest && !northSouth) shape = RAIL_EAST_WEST;
        // RailState#place first accepts only an unambiguous two-arm corner.
        if (south && east && !north && !west) shape = RAIL_SOUTH_EAST;
        if (south && west && !north && !east) shape = RAIL_SOUTH_WEST;
        if (north && west && !south && !east) shape = RAIL_NORTH_WEST;
        if (north && east && !south && !west) shape = RAIL_NORTH_EAST;
        if (shape < 0) {
            if (northSouth && eastWest) shape = previousState;
            else if (northSouth) shape = RAIL_NORTH_SOUTH;
            else if (eastWest) shape = RAIL_EAST_WEST;
            else shape = previousState;
            // Ordinary rails are not straight-only. The unpowered RailState fallback has this
            // exact overwrite order, which matters at three- and four-way junctions.
            if (north && west) shape = RAIL_NORTH_WEST;
            if (north && east) shape = RAIL_NORTH_EAST;
            if (south && west) shape = RAIL_SOUTH_WEST;
            if (south && east) shape = RAIL_SOUTH_EAST;
        }
        if (shape == RAIL_NORTH_SOUTH) {
            int raisedNorth = world.block(x, y + 1, z - 1);
            int raisedSouth = world.block(x, y + 1, z + 1);
            if (raisedNorth < 0 || raisedSouth < 0) return previousState;
            if (raisedNorth == Blocks.RAIL) shape = RAIL_ASCENDING_NORTH;
            if (raisedSouth == Blocks.RAIL) shape = RAIL_ASCENDING_SOUTH;
        } else if (shape == RAIL_EAST_WEST) {
            int raisedEast = world.block(x + 1, y + 1, z);
            int raisedWest = world.block(x - 1, y + 1, z);
            if (raisedEast < 0 || raisedWest < 0) return previousState;
            if (raisedEast == Blocks.RAIL) shape = RAIL_ASCENDING_EAST;
            if (raisedWest == Blocks.RAIL) shape = RAIL_ASCENDING_WEST;
        }
        return shape;
    }

    /** -1 unknown, 0 absent, 1 present. */
    private static int railPresence(int x, int y, int z, StateLookup world) {
        int same = world.block(x, y, z);
        int above = world.block(x, y + 1, z);
        int below = world.block(x, y - 1, z);
        if (same < 0 || above < 0 || below < 0) return -1;
        return same == Blocks.RAIL || above == Blocks.RAIL || below == Blocks.RAIL ? 1 : 0;
    }

    /**
     * MC 문 경첩 우선순위. 방향 기준 좌/우의 하단+상단 막힌 면 수를 먼저 비교하고,
     * 같으면 인접한 다른 문과 반대 경첩을 선택한다. 모두 같은 완전 동률은 left hinge다.
     */
    public static int doorHinge(int x, int y, int z, int facing, BlockLookup world) {
        int left = left(facing);
        int right = (facing + 1) & 3;
        int leftScore = obstruction(x + dx(left), y, z + dz(left), world);
        int rightScore = obstruction(x + dx(right), y, z + dz(right), world);
        boolean leftDoor = Blocks.isDoor(world.block(x + dx(left), y, z + dz(left)));
        boolean rightDoor = Blocks.isDoor(world.block(x + dx(right), y, z + dz(right)));
        boolean rightHinge = leftDoor && !rightDoor || leftScore > rightScore;
        return rightHinge ? DOOR_HINGE_RIGHT : 0;
    }

    private static int obstruction(int x, int y, int z, BlockLookup world) {
        int score = hasFullSquareFace(world.block(x, y, z)) ? 1 : 0;
        return score + (hasFullSquareFace(world.block(x, y + 1, z)) ? 1 : 0);
    }

    /**
     * MC {@code FenceGateBlock.connectsToDirection}: 울타리 문은 자기 facing 축과 직교하는
     * 방향으로만 울타리·담장에 연결된다. direction 은 0=N,1=E,2=S,3=W, 축은 N/S=z, E/W=x다.
     */
    public static boolean gateConnectsToDirection(int gateState, int direction) {
        return ((gateState & FACING_MASK) & 1) != (direction & 1);
    }

    public static boolean connects(int blockId, int neighborId, int neighborState, int direction) {
        boolean gate = Blocks.isFenceGate(neighborId)
                && gateConnectsToDirection(neighborState, direction);
        if (Blocks.isFence(blockId)) {
            return Blocks.isFence(neighborId) || gate
                    || attachableSideFace(neighborId, neighborState, direction);
        }
        if (isWall(blockId)) {
            return isWall(neighborId) || gate
                    || isPane(neighborId) || attachableSideFace(neighborId, neighborState, direction);
        }
        if (isPane(blockId)) {
            // MC의 GlassPaneBlock은 IronBarsBlock이므로 유리판/철창은 서로 같은 규칙을 쓰고,
            // BlockTags.WALLS 에도 붙는다(IronBarsBlock.attachsTo). 담장 쪽만 유리판을 향해
            // 팔을 뻗던 비대칭을 없앤다.
            return isPane(neighborId) || isWall(neighborId)
                    || attachableSideFace(neighborId, neighborState, direction);
        }
        return false;
    }

    /**
     * 울타리/담장/유리판이 붙는 옆면. [BLOCK-SHAPES] 바닐라 {@code FenceBlock/WallBlock/IronBarsBlock}
     * 은 이웃의 마주 보는 면이 {@code isFaceSturdy(FULL)} 인지 본다(support shape 만 보므로 유리처럼
     * 렌더만 투명한 풀 큐브와 계단 등받이·이중 반 블록의 옆면에도 붙는다). 반대로
     * {@code Block.isExceptionForConnection}(잎·방벽·호박·조각한 호박·잭오랜턴·수박·셜커 상자)에는
     * 붙지 않는다 — 잎은 지지 형상이 비어 있어 면 판정에서 이미 빠진다.
     *
     * @param direction 연결하는 블록에서 이웃 쪽 수평 방향(0 N · 1 E · 2 S · 3 W)
     */
    public static boolean attachableSideFace(int id, int state, int direction) {
        if (id == Blocks.PUMPKIN || id == Blocks.CARVED_PUMPKIN || id == Blocks.JACK_O_LANTERN
                || id == Blocks.MELON || id == Blocks.BARRIER || Blocks.isShulkerBox(id)) {
            return false;
        }
        if (id < 0) return false;
        // 이웃이 우리를 보는 면은 방향의 반대쪽 면이다(N 이웃의 south 면).
        int face = switch (direction & 3) {
            case 0 -> BlockFaceSturdiness.SOUTH;
            case 1 -> BlockFaceSturdiness.WEST;
            case 2 -> BlockFaceSturdiness.NORTH;
            default -> BlockFaceSturdiness.EAST;
        };
        return BlockFaceSturdiness.isFaceSturdy(id, state, face, BlockFaceSturdiness.FULL);
    }

    /** 연결선이 붙을 수 있는 보통의 full-cube 면. */
    public static boolean hasFullSquareFace(int id) {
        if (DecorativeCollisionShapes.box(id, 0) != null) return false;
        if (!Fluids.isSolid(id) || collisionHeight(id, 0) < 1.0) return false;
        // 상자·선인장·인챈트 테이블은 셀을 다 채우지 않는 형상이라 sturdy face가 아니다.
        return !isStairs(id) && !isSlab(id) && !isWall(id) && !Blocks.isFence(id)
                && !Blocks.isFenceGate(id) && !isPane(id) && !Blocks.isTrapdoor(id)
                && id != Blocks.RAIL && id != Blocks.CAMPFIRE && !Blocks.isGlassBlock(id)
                && !Blocks.isBed(id) && !Blocks.isChestShaped(id) && id != Blocks.CACTUS
                && !Blocks.isAnvil(id)
                // [COOKING] 케이크도 높이 8/16 에 XZ 인셋이 있는 부분 형상이라 sturdy face 가 아니다.
                && id != Blocks.CAKE
                && id != Blocks.SHELF_MUSHROOM
                && !Blocks.isShelf(id) && !Blocks.isDecoratedPot(id)
                // [TURTLE] 거북 알도 높이 7/16 에 XZ 인셋이 있는 부분 형상이다 — 클라
                // hasSolidTopFace 가 (불투명 큐브도 아니고 상면이 16 도 아니라) 거짓이므로
                // 스폰 지지면·레드스톤 도체 판정이 두 권위에서 같아진다.
                && id != Blocks.TURTLE_EGG
                // [DEEP-DARK] 말린 가스트도 box(3,0,3,13,10,13) 부분 형상이다.
                && id != Blocks.DRIED_GHAST
                // [TRIAL-GAP] 무거운 핵도 Block.column(8, 0, 8) = box(4,0,4,12,8,12) 부분 형상이다.
                && id != Blocks.HEAVY_CORE
                && id != Blocks.ENCHANTING_TABLE && !BlockFamilies.isLeaves(id)
                // [BLOCK-SHAPES] 모델 블록 열 종은 셀을 다 채우지 않는다(면별 판정은 BlockFaceSturdiness).
                && !BlockModelShapes.has(id);
    }

    /**
     * 자연 ON_GROUND 스폰이 요구하는 완전한 윗면. 클라이언트
     * {@code BlockRegistry.canSpawnOn}과 같은 상태 규칙을 사용한다.
     */
    public static boolean canSpawnOn(int id, int state) {
        if (isSlab(id)) return (state & ~WATERLOGGED) == SLAB_TOP
                || (state & ~WATERLOGGED) == SLAB_DOUBLE;
        if (!hasFullSquareFace(id)) return false;
        // [FROST-SOUL] 살얼음의 isValidSpawn 은 북극곰만 받는다(자연 ON_GROUND 스폰 지지면이 아니다).
        return !Blocks.isDoor(id) && id != Blocks.SNOW && id != Blocks.ICE && id != Blocks.FROSTED_ICE
                && id != Blocks.POWDER_SNOW && id != Blocks.FARMLAND
                && id != Blocks.MOSS_CARPET && !Blocks.isChestShaped(id) && id != Blocks.CACTUS
                && id != Blocks.TINTED_GLASS
                && (id < Blocks.SPAWNER_BASE || id > Blocks.SPAWNER_BASE + 2)
                // [TRIAL] 트라이얼 스포너·금고는 몬스터 스포너와 같은 noOcclusion 컷아웃 풀 셀이다
                // (클라 hasSolidTopFace 는 불투명 큐브만 인정한다).
                && !Blocks.isTrialChamberFixture(id);
    }

    /** IN_WATER 스폰 배치가 위 칸에서 검사하는 full-cube redstone conductor 근사. */
    public static boolean isRedstoneConductor(int id, int state) {
        if (isSlab(id)) return (state & ~WATERLOGGED) == SLAB_DOUBLE;
        if (!hasFullSquareFace(id) || collisionHeight(id, state) < 1.0) return false;
        return !Blocks.isDoor(id) && !Blocks.isChestShaped(id) && id != Blocks.CACTUS
                && id != Blocks.CAKE
                && id != Blocks.TINTED_GLASS
                && id != Blocks.ICE && id != Blocks.POWDER_SNOW && id != Blocks.BIG_DRIPLEAF
                // [FROST-SOUL] 살얼음은 isRedstoneConductor(Blocks::never) 다.
                && id != Blocks.FROSTED_ICE
                && (id < Blocks.SPAWNER_BASE || id > Blocks.SPAWNER_BASE + 2)
                && !Blocks.isTrialChamberFixture(id);
    }

    /** Bat ceiling checks BlockState.isCollisionShapeFullBlock, not redstone conduction. */
    public static boolean isFullCollisionShape(int id, int state) {
        if (RedstoneShapes.has(id)) return RedstoneShapes.full(id, state);
        if (DecorativeCollisionShapes.box(id, state) != null) return false;
        if (!Fluids.isSolid(id) || collisionBottom(id, state) != 0.0
                || collisionHeight(id, state) != 1.0) return false;
        return !isStairs(id) && !isWall(id) && !isPane(id)
                && !Blocks.isFence(id) && !Blocks.isFenceGate(id)
                && !Blocks.isTrapdoor(id) && !Blocks.isDoor(id)
                && id != Blocks.CAMPFIRE && !Blocks.isBed(id) && !Blocks.isChestShaped(id)
                && id != Blocks.CACTUS && !P6Rules.isSpeleothem(id)
                // [COOKING] 케이크는 높이 8/16 이라 collisionHeight 게이트에서 이미 걸리지만,
                // 상자·선인장과 같이 목록에도 적어 "부분 형상" 의도를 명시한다.
                && id != Blocks.CAKE
                && !Blocks.isAnvil(id)
                && !Blocks.isShelf(id) && !Blocks.isDecoratedPot(id)
                // [END-PORTAL] 눈이 박힌 틀도 윗면은 셀 꼭대기에 닿지만 13/16 몸체 + 눈 기둥이라
                // 바닐라 isCollisionShapeFullBlock 이 거짓이다.
                && id != Blocks.END_PORTAL_FRAME
                && !BlockModelShapes.has(id)
                && id != Blocks.BIG_DRIPLEAF;
    }

    /** MOTION_BLOCKING 높이맵이 쓰는 블록 상태 기반 이동 차단 판정. */
    public static boolean blocksMotion(int id, int state) {
        if (Blocks.isFence(id) || isWall(id) || Blocks.isFenceGate(id)
                || P6Rules.isSpeleothem(id) || id == Blocks.MANGROVE_ROOTS
                || id >= Blocks.SMALL_AMETHYST_BUD && id <= Blocks.AMETHYST_CLUSTER) return true;
        double[] decorative = DecorativeCollisionShapes.box(id, state);
        if (decorative != null) {
            // 높이맵의 calculateSolid 판정은 셀 폭이 아니라 충돌 AABB의 세 축 크기를 쓴다.
            double width = (decorative[3] - decorative[0]) / 16;
            double height = (decorative[4] - decorative[1]) / 16;
            double depth = (decorative[5] - decorative[2]) / 16;
            return height >= 1.0 || (width + height + depth) / 3.0 >= 35.0 / 48.0;
        }
        if (!Fluids.isSolid(id) || id == Blocks.MOSS_CARPET || id == Blocks.SNOW
                || id == Blocks.COBWEB || id == Blocks.BIG_DRIPLEAF) return false;
        if (id == Blocks.SHELF_MUSHROOM) return false;
        if (BlockModelShapes.has(id)) {
            // [WORLD-GEOMETRY] 바닐라 calculateSolid 는 충돌 형상 bounding AABB 의 세 축 크기를 본다(클라
            // collisionShapeBlocksMotion 과 같다): 떠 있는 콘딧 6×6×6 은 막지 않고 비명체 반 칸은 막는다.
            double[][] boxes = BlockModelShapes.collisionBoxes16(id, state);
            if (boxes.length == 0) return false;
            double[] b = {16, 16, 16, 0, 0, 0};
            for (double[] box : boxes) {
                for (int a = 0; a < 3; a++) {
                    b[a] = Math.min(b[a], box[a]);
                    b[a + 3] = Math.max(b[a + 3], box[a + 3]);
                }
            }
            double sizeX = (b[3] - b[0]) / 16, sizeY = (b[4] - b[1]) / 16, sizeZ = (b[5] - b[2]) / 16;
            return sizeY >= 1.0 || (sizeX + sizeY + sizeZ) / 3.0 >= 35.0 / 48.0;
        }
        double bottom = collisionBottom(id, state);
        double top = supportTop(id, state, 0.0, 1.0, 0.0, 1.0);
        double height = Math.max(0.0, top - bottom);
        if (height >= 1.0) return true;
        double width = 1.0;
        double depth = 1.0;
        if (id == Blocks.CACTUS) {
            width = depth = 14.0 / 16.0;
        } else if (P6Rules.isSpeleothem(id)) {
            width = depth = 1.0 - pointedDripstoneInset(state) * 2.0;
        }
        return (width + height + depth) / 3.0 >= 35.0 / 48.0;
    }

    /** 공통 부분 높이 물리. 이 값은 풀 셀 고체 근사를 대체한다. */
    public static double collisionHeight(int blockId, int state) {
        if (RedstoneShapes.has(blockId)) return RedstoneShapes.top(blockId, state) - Math.min(RedstoneShapes.top(blockId, state), RedstoneShapes.bottom(blockId, state));
        double[] decorative = DecorativeCollisionShapes.box(blockId, state);
        if (decorative != null) return (decorative[4] - decorative[1]) / 16;
        // 유체를 막는 장식도 엔티티 충돌은 비어 있을 수 있다. 유체/지형 규칙과 분리한다.
        if (isCollisionPassThrough(blockId)) return 0.0;
        if (Blocks.isDoor(blockId)) return (state & DOOR_OPEN) != 0 ? 0.0 : 1.0;
        if (isSlab(blockId)) return (state & ~WATERLOGGED) == SLAB_DOUBLE ? 1.0 : 0.5;
        if (isStairs(blockId)) return 1.0; // 아래/위 half + 방향별 quarter box는 클라이언와 동일하게 분할한다.
        if (isWall(blockId) || Blocks.isFence(blockId) || Blocks.isFenceGate(blockId)) {
            return (state & GATE_OPEN) != 0 && Blocks.isFenceGate(blockId) ? 0.0 : 1.5;
        }
        if (Blocks.isTrapdoor(blockId)) return (state & TRAPDOOR_OPEN) != 0 ? 1.0 : 3.0 / 16.0;
        if (blockId == Blocks.RAIL) return 0.0; // 레일의 2/16은 outline 모양이고 엔티티 충돌은 없다.
        // [END-PORTAL] 바닐라 EndPortalFrameBlock: 빈 틀 13/16, 눈이 박힌 틀은 눈 기둥 윗면 1.
        if (blockId == Blocks.END_PORTAL_FRAME) return EndPortalFrameRules.shapeTop(state);
        // [BLOCK-SHAPES] 모델 블록 열 종: 바닐라 getCollisionShape 박스 표의 윗면.
        if (BlockModelShapes.has(blockId)) return BlockModelShapes.collisionTop(blockId, state);
        // [TURTLE] 바닐라 TurtleEggBlock ONE_EGG_AABB = box(3,0,3,12,7,12) ·
        // MULTIPLE_EGGS_AABB = box(1,0,1,15,7,15) — 알 수와 무관하게 높이는 7/16 이다.
        // XZ 인셋은 supportTop 이 본다. 클라 BlockRegistry.collisionShapeCode 의 turtle_egg
        // 갈래(0x20000 | inset | 7)와 같은 값이어야 한다 — 이 갈래가 없으면 서버만 알을
        // 풀 셀로 보고 클라보다 9/16 높은 곳에 플레이어를 세운다.
        if (blockId == Blocks.TURTLE_EGG) return 7.0 / 16.0;
        // [DEEP-DARK] 바닐라 DriedGhastBlock SHAPE = Block.column(10, 10, 0, 10) =
        // box(3,0,3,13,10,13). 클라 collisionShapeCode 의 dried_ghast 갈래(0x20000 | 3 | 10)와 같다.
        if (blockId == Blocks.DRIED_GHAST) return 10.0 / 16.0;
        // [TRIAL-GAP] 바닐라 HeavyCoreBlock SHAPE = Block.column(8, 0, 8) = box(4,0,4,12,8,12).
        // 클라 collisionShapeCode 의 heavy_core 갈래(0x20000 | 4 | 8)와 같다.
        if (blockId == Blocks.HEAVY_CORE) return 8.0 / 16.0;
        if (blockId == Blocks.SHELF_MUSHROOM) {
            return (state & com.gameexpert.engine.blocks.P29Rules.AGE) == 0
                    ? 7.5 / 16.0 : 8.0 / 16.0;
        }
        if (Blocks.isBed(blockId)) return Blocks.isStrawBed(blockId) ? ((state & BED_HEAD) != 0 ? 5.0 / 16 : 4.0 / 16) : 9.0 / 16;
        // 쿠션 ID는 아이템 전용이며 CushionSystem 엔티티가 별도의 히트박스를 소유한다.
        if (Blocks.isCushion(blockId)) return 0.0;
        if (blockId == Blocks.FARMLAND) return 15.0 / 16.0;
        if (blockId >= Blocks.WHITE_CARPET && blockId <= Blocks.BLACK_CARPET
                || blockId == Blocks.PALE_MOSS_CARPET) return 1.0 / 16.0;
        if (blockId == Blocks.CHERRY_BONSAI) return 12.0 / 16.0;
        if (blockId == Blocks.BAMBOO || blockId == Blocks.AZALEA
                || blockId == Blocks.FLOWERING_AZALEA) return 1.0;
        if (isFlowerPotCollision(blockId)) return 6.0 / 16.0;
        if (blockId == Blocks.COCOA) return 12.0 / 16.0;
        if (blockId == Blocks.CONDUIT) return 11.0 / 16.0;
        if (blockId == Blocks.HONEY_BLOCK) return 15.0 / 16.0;
        if (blockId == Blocks.RAFFLESIA) return 6.0 / 16.0;
        // 바닐라 ChestBlock = box(1,0,1,15,14,15), EnchantingTableBlock = box(0,0,0,16,12,16),
        // CactusBlock COLLISION_SHAPE = box(1,0,1,15,15,15). XZ 인셋은 supportTop 이 본다.
        if (Blocks.isChestShaped(blockId)) return 14.0 / 16.0;
        // [COOKING] 바닐라 CakeBlock = box(1 + bites*2, 0, 1, 15, 8, 15). 높이는 조각 수와
        // 무관하게 8/16 이고 서쪽 면만 한 입마다 2/16 물러난다(그건 supportTop 이 본다).
        if (blockId == Blocks.CAKE) return 8.0 / 16.0;
        if (blockId == Blocks.ENCHANTING_TABLE) return 12.0 / 16.0;
        if (blockId == Blocks.CACTUS) return 15.0 / 16.0;
        if (blockId == Blocks.SNOW) {
            int layers = Math.max(1, Math.min(8, state));
            return (layers - 1) / 8.0;
        }
        if (P6Rules.isSpeleothem(blockId)) {
            return pointedDripstoneTop(state);
        }
        double height = Fluids.isSolid(blockId) ? 1.0 : 0.0;
        height = com.gameexpert.engine.blocks.P1Rules.collisionHeight(blockId, state, height);
        height = com.gameexpert.engine.blocks.P2Rules.collisionHeight(blockId, state, height);
        height = com.gameexpert.engine.blocks.P3Rules.collisionHeight(blockId, state, height);
        return com.gameexpert.engine.blocks.P26Rules.collisionHeight(blockId, state, height);
    }

    /** 클라이언트에 등록된 비충돌 장식. Fluids.isSolid는 유체 치환 규약이라 바꾸지 않는다. */
    private static boolean isCollisionPassThrough(int id) {
        if (Blocks.isBanner(id)) return true;
        return switch (id) {
            case Blocks.PRIMED_TNT, Blocks.COBWEB, Blocks.TATTERED_BANNER, Blocks.SCULK_VEIN,
                    Blocks.WILDFLOWERS, Blocks.FIREFLY_BUSH, Blocks.CACTUS_FLOWER,
                    Blocks.SHORT_DRY_GRASS, Blocks.TALL_DRY_GRASS,
                    Blocks.PALE_OAK_SAPLING, Blocks.PALE_HANGING_MOSS,
                    Blocks.POPLAR_SAPLING, Blocks.RED_SHRUB, Blocks.POPLAR_BUTTON,
                    Blocks.POPLAR_PRESSURE_PLATE, Blocks.POPLAR_SIGN, Blocks.POPLAR_HANGING_SIGN,
                    Blocks.SWEET_BERRY_BUSH, Blocks.GOLDEN_DANDELION,
                    Blocks.COPPER_TORCH, Blocks.COPPER_WALL_TORCH_N, Blocks.COPPER_WALL_TORCH_E,
                    Blocks.COPPER_WALL_TORCH_S, Blocks.COPPER_WALL_TORCH_W,
                    Blocks.FROGSPAWN, Blocks.TORCHFLOWER, Blocks.PITCHER_PLANT,
                    Blocks.TALL_SEAGRASS, Blocks.BLUE_ORCHID, Blocks.CLOSED_EYEBLOSSOM, Blocks.OPEN_EYEBLOSSOM,
                    Blocks.LARGE_FERN, Blocks.PINK_PETALS, Blocks.SUNFLOWER, Blocks.TALL_GRASS_263,
                    Blocks.KELP_PLANT, Blocks.LILAC, Blocks.ROSE_BUSH, Blocks.PEONY,
                    Blocks.LILY_OF_THE_VALLEY, Blocks.BIG_DRIPLEAF_STEM,
                    Blocks.ALLIUM, Blocks.AZURE_BLUET, Blocks.RED_TULIP, Blocks.ORANGE_TULIP,
                    Blocks.WHITE_TULIP, Blocks.PINK_TULIP, Blocks.OXEYE_DAISY, Blocks.CORNFLOWER,
                    Blocks.TRIPWIRE_HOOK_BLOCK, Blocks.TRIPWIRE, Blocks.LEVER,
                    Blocks.REDSTONE_WIRE, Blocks.REPEATER, Blocks.REDSTONE_TORCH,
                    Blocks.OAK_WALL_SIGN, Blocks.STONE_PRESSURE_PLATE,
                    Blocks.STONE_BUTTON, Blocks.END_PORTAL, Blocks.END_GATEWAY,
                    Blocks.SPRUCE_HANGING_SIGN, Blocks.OAK_HANGING_SIGN,
                    Blocks.WHITE_WALL_BANNER, Blocks.OAK_BUTTON, Blocks.OAK_PRESSURE_PLATE,
                    Blocks.BROWN_WALL_BANNER, Blocks.MELON_STEM, Blocks.MAGENTA_WALL_BANNER -> true;
            default -> false;
        };
    }

    /**
     * [VANILLA-FLAME] 바닐라 {@code LanternBlock} 계열(철 랜턴 + 구리 랜턴 산화 4단계 × 밀랍 2)인가.
     * 모두 같은 state 어휘 — {@link #LANTERN_HANGING} 과 {@link #WATERLOGGED} — 를 쓰므로
     * 설치 정규화·지지·물담김·carrier 투영이 이 하나만 본다. client {@code blocks.ts} 의
     * {@code isLantern} 과 같은 판정이어야 한다.
     */
    public static boolean isLantern(int id) {
        return id == Blocks.LANTERN || id == Blocks.COPPER_LANTERN
                || id >= Blocks.EXPOSED_COPPER_LANTERN && id <= Blocks.WAXED_OXIDIZED_COPPER_LANTERN;
    }


    private static boolean isFlowerPotCollision(int id) {
        return id == Blocks.FLOWER_POT || id == Blocks.POTTED_POPLAR_SAPLING
                || id == Blocks.POTTED_CACTUS || id == Blocks.POTTED_RED_MUSHROOM
                || id == Blocks.POTTED_DEAD_BUSH || id == Blocks.POTTED_RED_TULIP;
    }

    @FunctionalInterface
    public interface CollisionBoxVisitor {
        void visit(double minX, double minY, double minZ, double maxX, double maxY, double maxZ);
    }

    /** 위치 오프셋은 블록 상태/저장 바이트를 바꾸지 않고 모든 실제 충돌 소비자에 적용한다. */
    public static void forCollisionBoxes(int id, int state, int blockX, int blockZ,
            CollisionBoxVisitor visitor) {
        if (id == Blocks.BAMBOO) {
            double x = DecorativeCollisionShapes.bambooOffsetX(blockX, blockZ);
            double z = DecorativeCollisionShapes.bambooOffsetZ(blockX, blockZ);
            visitor.visit(6.5 / 16 + x, 0, 6.5 / 16 + z, 9.5 / 16 + x, 1, 9.5 / 16 + z);
        } else forCollisionBoxes(id, state, visitor);
    }

    /** 엔티티 스윕이 소비하는 실제 로컬 AABB들. 비충돌 셀은 visitor를 호출하지 않는다. */
    public static void forCollisionBoxes(int id, int state, CollisionBoxVisitor visitor) {
        if (RedstoneShapes.has(id)) { visitRotatedBoxes(RedstoneShapes.collision(id, state), 0, visitor); return; }
        double[] decorative = DecorativeCollisionShapes.box(id, state);
        if (decorative != null) {
            visitor.visit(decorative[0] / 16, decorative[1] / 16, decorative[2] / 16,
                    decorative[3] / 16, decorative[4] / 16, decorative[5] / 16);
            return;
        }
        if (id == Blocks.SHELF_MUSHROOM) {
            visitRotatedBoxes(SHELF_MUSHROOM_COLLISION_BOXES[(state & P29Rules.AGE) == 0 ? 0 : 1],
                    state, visitor);
            return;
        }
        if (Blocks.isShelf(id)) {
            visitRotatedBoxes(SHELF_COLLISION_BOXES, state, visitor);
            return;
        }
        if (Blocks.isAnvil(id)) {
            visitRotatedBoxes(ANVIL_COLLISION_BOXES, state & 1, visitor);
            return;
        }
        if (id == Blocks.RAFFLESIA) {
            visitRotatedBoxes(RAFFLESIA_COLLISION_BOXES, 0, visitor);
            return;
        }
        if (id == Blocks.END_PORTAL_FRAME) {
            // 수평 대칭 형상이라 facing 회전 없이(0) 그대로 방문한다.
            visitRotatedBoxes(EndPortalFrameRules.shapeBoxes16(state), 0, visitor);
            return;
        }
        if (BlockModelShapes.has(id)) {
            // [BLOCK-SHAPES] 표가 이미 state 별 회전을 끝낸 박스라 facing 0 으로 그대로 방문한다.
            visitRotatedBoxes(BlockModelShapes.collisionBoxes16(id, state), 0, visitor);
            return;
        }
        if (id == Blocks.COCOA) {
            visitRotatedBoxes(COCOA_COLLISION_BOXES[Math.min(2, state & 3)], state >> 2, visitor);
            return;
        }
        if (id == Blocks.AZALEA || id == Blocks.FLOWERING_AZALEA) {
            visitor.visit(0, .5, 0, 1, 1, 1);
            visitor.visit(6.0 / 16, 0, 6.0 / 16, 10.0 / 16, .5, 10.0 / 16);
            return;
        }
        if (Blocks.isDoor(id) || Blocks.isTrapdoor(id) && (state & TRAPDOOR_OPEN) != 0) {
            int direction = state & FACING_MASK;
            if (Blocks.isDoor(id) && (state & DOOR_OPEN) != 0) {
                direction = (direction + ((state & DOOR_HINGE_RIGHT) != 0 ? 1 : 3)) & 3;
            }
            if ((direction & 1) == 0) {
                visitor.visit(0, 0, direction == 0 ? 0 : 13.0 / 16,
                        1, 1, direction == 0 ? 3.0 / 16 : 1);
            } else {
                visitor.visit(direction == 3 ? 0 : 13.0 / 16, 0, 0,
                        direction == 3 ? 3.0 / 16 : 1, 1, 1);
            }
            return;
        }
        if (isStairs(id)) {
            boolean top = (state & STAIR_TOP) != 0;
            visitor.visit(0, top ? .5 : 0, 0, 1, top ? 1 : .5, 1);
            int mask = stairQuarterMask(state);
            for (int quarter = 0; quarter < 4; quarter++) {
                if ((mask & (1 << quarter)) == 0) continue;
                double x = quarter == 1 || quarter == 2 ? .5 : 0;
                double z = quarter >= 2 ? .5 : 0;
                visitor.visit(x, top ? 0 : .5, z, x + .5, top ? .5 : 1, z + .5);
            }
            return;
        }
        if (Blocks.isFenceGate(id)) {
            if ((state & GATE_OPEN) != 0) return;
            if ((state & 1) == 0) visitor.visit(0, 0, 6.0 / 16, 1, 1.5, 10.0 / 16);
            else visitor.visit(6.0 / 16, 0, 0, 10.0 / 16, 1.5, 1);
            return;
        }
        if (isWall(id) || Blocks.isFence(id) || isPane(id)) {
            boolean pane = isPane(id);
            double center = pane ? 7.0 / 16 : isWall(id) ? 4.0 / 16 : 6.0 / 16;
            double arm = pane ? 7.0 / 16 : isWall(id) ? 5.0 / 16 : 7.0 / 16;
            double height = pane ? 1 : 1.5;
            visitor.visit(center, 0, center, 1 - center, height, 1 - center);
            double far = pane ? 9.0 / 16 : .5;
            double near = pane ? 7.0 / 16 : .5;
            if ((state & CONNECT_NORTH) != 0) visitor.visit(arm, 0, 0, 1 - arm, height, far);
            if ((state & CONNECT_EAST) != 0) visitor.visit(near, 0, arm, 1, height, 1 - arm);
            if ((state & CONNECT_SOUTH) != 0) visitor.visit(arm, 0, near, 1 - arm, height, 1);
            if ((state & CONNECT_WEST) != 0) visitor.visit(0, 0, arm, far, height, 1 - arm);
            return;
        }
        double bottom = collisionBottom(id, state), top = collisionTop(id, state);
        if (top <= bottom) return;
        double inset = 0;
        if (id == Blocks.BAMBOO) inset = 6.5 / 16;
        else if (isFlowerPotCollision(id) || id == Blocks.CONDUIT) inset = 5.0 / 16;
        else if (id == Blocks.CACTUS || id == Blocks.HONEY_BLOCK || Blocks.isChestShaped(id)
                || Blocks.isDecoratedPot(id)) inset = 1.0 / 16;
        else if (id == Blocks.TURTLE_EGG) inset = (TurtleEggRules.eggs(state) == 1 ? 3.0 : 1.0) / 16;
        else if (id == Blocks.DRIED_GHAST) inset = 3.0 / 16;
        else if (id == Blocks.HEAVY_CORE) inset = 4.0 / 16;
        else if (P6Rules.isSpeleothem(id)) inset = pointedDripstoneInset(state);
        if (id == Blocks.CAKE) {
            int bites = CakeRules.isValidBites(state) ? state : 0;
            visitor.visit((1.0 + bites * 2.0) / 16, bottom, 1.0 / 16, 15.0 / 16, top, 15.0 / 16);
        } else visitor.visit(inset, bottom, inset, 1 - inset, top, 1 - inset);
    }

    private static void visitRotatedBoxes(double[][] boxes, int state, CollisionBoxVisitor visitor) {
        int facing = state & FACING_MASK;
        for (double[] box : boxes) {
            double x0 = box[0] / 16, y0 = box[1] / 16, z0 = box[2] / 16;
            double x1 = box[3] / 16, y1 = box[4] / 16, z1 = box[5] / 16;
            if (facing == 1) visitor.visit(1 - z1, y0, x0, 1 - z0, y1, x1);
            else if (facing == 2) visitor.visit(1 - x1, y0, 1 - z1, 1 - x0, y1, 1 - z0);
            else if (facing == 3) visitor.visit(z0, y0, 1 - x1, z1, y1, 1 - x0);
            else visitor.visit(x0, y0, z0, x1, y1, z1);
        }
    }

    /** 셀 바닥을 0으로 한 충돌체의 위쪽 면. */
    public static double collisionTop(int blockId, int state) {
        if (RedstoneShapes.has(blockId)) return RedstoneShapes.top(blockId, state);
        double[] decorative = DecorativeCollisionShapes.box(blockId, state);
        if (decorative != null) return decorative[4] / 16;
        if (isSlab(blockId) && (state & ~WATERLOGGED) == SLAB_TOP) return 1.0;
        if (Blocks.isTrapdoor(blockId)
                && (state & TRAPDOOR_OPEN) == 0 && (state & TRAPDOOR_TOP) != 0) return 1.0;
        return collisionHeight(blockId, state);
    }

    /**
     * 플레이어 발 면적과 실제로 겹치는 충돌체 중 가장 높은 상면.
     * 계단의 낮은 1/2면을 full-height로 오인하면 클라이언트는 착지했는데 서버만 계속
     * 공중으로 추적하므로, 클라이언트 Physics의 무할당 계단 box 분해와 같은 mask를 사용한다.
     */
    public static double supportTop(int blockId, int state, int blockX, int blockZ,
            double localMinX, double localMaxX, double localMinZ, double localMaxZ) {
        if (blockId == Blocks.BAMBOO) {
            double x = DecorativeCollisionShapes.bambooOffsetX(blockX, blockZ);
            double z = DecorativeCollisionShapes.bambooOffsetZ(blockX, blockZ);
            return supportTop(blockId, state, localMinX - x, localMaxX - x,
                    localMinZ - z, localMaxZ - z);
        }
        return supportTop(blockId, state, localMinX, localMaxX, localMinZ, localMaxZ);
    }

    public static double supportTop(int blockId, int state,
            double localMinX, double localMaxX, double localMinZ, double localMaxZ) {
        if (RedstoneShapes.has(blockId)) return rotatedSupportTop(RedstoneShapes.collision(blockId, state), 0, localMinX, localMaxX, localMinZ, localMaxZ);
        double[] decorative = DecorativeCollisionShapes.box(blockId, state);
        if (decorative != null) {
            return intersects(localMinX, localMaxX, localMinZ, localMaxZ,
                    decorative[0] / 16, decorative[3] / 16, decorative[2] / 16, decorative[5] / 16)
                    ? decorative[4] / 16 : 0;
        }
        // 선반버섯만 셀 밖으로 돌출한다. 확장 broadphase가 이웃의 풀 큐브까지 발판으로 만들면 안 된다.
        if (blockId != Blocks.SHELF_MUSHROOM && !intersects(
                localMinX, localMaxX, localMinZ, localMaxZ, 0, 1, 0, 1)) return 0.0;
        if (blockId == Blocks.BAMBOO) {
            return intersects(localMinX, localMaxX, localMinZ, localMaxZ,
                    6.5 / 16.0, 9.5 / 16.0, 6.5 / 16.0, 9.5 / 16.0) ? 1.0 : 0.0;
        }
        if (isFlowerPotCollision(blockId)) {
            double inset = 5.0 / 16.0;
            return intersects(localMinX, localMaxX, localMinZ, localMaxZ,
                    inset, 1 - inset, inset, 1 - inset) ? 6.0 / 16.0 : 0.0;
        }
        if (blockId == Blocks.COCOA) {
            int age = Math.min(2, state & 3);
            return rotatedSupportTop(COCOA_COLLISION_BOXES[age], state >> 2,
                    localMinX, localMaxX, localMinZ, localMaxZ);
        }
        if (blockId == Blocks.CONDUIT) {
            return intersects(localMinX, localMaxX, localMinZ, localMaxZ,
                    5.0 / 16.0, 11.0 / 16.0, 5.0 / 16.0, 11.0 / 16.0)
                    ? collisionHeight(blockId, state) : 0.0;
        }
        if (Blocks.isDecoratedPot(blockId)) {
            return intersects(localMinX, localMaxX, localMinZ, localMaxZ,
                    1.0 / 16.0, 15.0 / 16.0, 1.0 / 16.0, 15.0 / 16.0) ? 1.0 : 0.0;
        }
        if (Blocks.isShelf(blockId)) {
            return rotatedSupportTop(SHELF_COLLISION_BOXES, state,
                    localMinX, localMaxX, localMinZ, localMaxZ);
        }
        if (blockId == Blocks.RAFFLESIA) {
            return rotatedSupportTop(RAFFLESIA_COLLISION_BOXES, 0,
                    localMinX, localMaxX, localMinZ, localMaxZ);
        }
        // [END-PORTAL] 발 면적이 눈 기둥(4..12)에 걸리면 1, 몸체에만 걸리면 13/16.
        if (blockId == Blocks.END_PORTAL_FRAME) {
            return rotatedSupportTop(EndPortalFrameRules.shapeBoxes16(state), 0,
                    localMinX, localMaxX, localMinZ, localMaxZ);
        }
        // [BLOCK-SHAPES] 발 면적과 겹치는 박스 중 가장 높은 윗면(가마솥·퇴비통 안쪽 바닥, 호퍼 테 등).
        if (BlockModelShapes.has(blockId)) {
            return rotatedSupportTop(BlockModelShapes.collisionBoxes16(blockId, state), 0,
                    localMinX, localMaxX, localMinZ, localMaxZ);
        }
        if (P6Rules.isSpeleothem(blockId)) {
            double inset = pointedDripstoneInset(state);
            return intersects(localMinX, localMaxX, localMinZ, localMaxZ,
                    inset, 1.0 - inset, inset, 1.0 - inset)
                    ? pointedDripstoneTop(state) : 0.0;
        }
        if (blockId == Blocks.CACTUS || blockId == Blocks.HONEY_BLOCK || Blocks.isChestShaped(blockId)) {
            return intersects(localMinX, localMaxX, localMinZ, localMaxZ,
                    1.0 / 16.0, 15.0 / 16.0, 1.0 / 16.0, 15.0 / 16.0)
                    ? collisionHeight(blockId, state) : 0.0;
        }
        // [TURTLE] 거북 알은 알 수로 인셋이 갈린다 — 여럿은 [A] 1px, 한 개는 [C] 대칭 3px
        // (바닐라 3..12 비대칭을 대칭 인셋 한 값으로 옮긴 클라 turtle_egg 갈래와 같은 자리다).
        if (blockId == Blocks.TURTLE_EGG) {
            double inset = (TurtleEggRules.eggs(state) == 1 ? 3.0 : 1.0) / 16.0;
            return intersects(localMinX, localMaxX, localMinZ, localMaxZ,
                    inset, 1.0 - inset, inset, 1.0 - inset)
                    ? collisionHeight(blockId, state) : 0.0;
        }
        if (blockId == Blocks.DRIED_GHAST) {
            return intersects(localMinX, localMaxX, localMinZ, localMaxZ,
                    3.0 / 16.0, 13.0 / 16.0, 3.0 / 16.0, 13.0 / 16.0)
                    ? collisionHeight(blockId, state) : 0.0;
        }
        if (blockId == Blocks.HEAVY_CORE) {
            return intersects(localMinX, localMaxX, localMinZ, localMaxZ,
                    4.0 / 16.0, 12.0 / 16.0, 4.0 / 16.0, 12.0 / 16.0)
                    ? collisionHeight(blockId, state) : 0.0;
        }
        if (blockId == Blocks.SHELF_MUSHROOM) {
            return shelfMushroomSupportTop(state,
                    localMinX, localMaxX, localMinZ, localMaxZ);
        }
        // [COOKING] 케이크는 상자와 같은 1px 인셋이지만 **서쪽 면만** 한 입마다 2/16 물러난다
        // (바닐라 box(1 + bites*2, 0, 1, 15, 8, 15)). 먹힌 쪽에 발판이 남지 않는 것이 바닐라다.
        if (blockId == Blocks.CAKE) {
            int bites = CakeRules.isValidBites(state) ? state : 0;
            double west = (1.0 + bites * 2.0) / 16.0;
            return intersects(localMinX, localMaxX, localMinZ, localMaxZ,
                    west, 15.0 / 16.0, 1.0 / 16.0, 15.0 / 16.0)
                    ? collisionHeight(blockId, state) : 0.0;
        }
        if (Blocks.isAnvil(blockId)) {
            return rotatedSupportTop(ANVIL_COLLISION_BOXES, state & 1,
                    localMinX, localMaxX, localMinZ, localMaxZ);
        }
        if (Blocks.isDoor(blockId)) {
            int facing = state & FACING_MASK;
            boolean opened = (state & DOOR_OPEN) != 0;
            boolean hingeRight = (state & DOOR_HINGE_RIGHT) != 0;
            int direction = opened ? (facing + (hingeRight ? 1 : 3)) & 3 : facing;
            // 바닐라 DoorBlock 두께는 3/16 이다(block/door_bottom_left = [0,0,0]~[3,16,16]).
            boolean hit = (direction & 1) == 0
                    ? intersects(localMinX, localMaxX, localMinZ, localMaxZ,
                            0, 1, direction == 0 ? 0 : 13.0 / 16.0,
                            direction == 0 ? 3.0 / 16.0 : 1)
                    : intersects(localMinX, localMaxX, localMinZ, localMaxZ,
                            direction == 3 ? 0 : 13.0 / 16.0,
                            direction == 3 ? 3.0 / 16.0 : 1, 0, 1);
            return hit ? 1.0 : 0.0;
        }
        if (isWall(blockId) || Blocks.isFence(blockId)) {
            boolean wall = isWall(blockId);
            double centerInset = wall ? 4.0 / 16.0 : 6.0 / 16.0;
            if (intersects(localMinX, localMaxX, localMinZ, localMaxZ,
                    centerInset, 1 - centerInset, centerInset, 1 - centerInset)) {
                return 1.5;
            }
            int mask = state & 0x0f;
            double armInset = wall ? 5.0 / 16.0 : 7.0 / 16.0;
            if ((mask & CONNECT_NORTH) != 0 && intersects(
                    localMinX, localMaxX, localMinZ, localMaxZ,
                    armInset, 1 - armInset, 0, 0.5)) return 1.5;
            if ((mask & CONNECT_SOUTH) != 0 && intersects(
                    localMinX, localMaxX, localMinZ, localMaxZ,
                    armInset, 1 - armInset, 0.5, 1)) return 1.5;
            if ((mask & CONNECT_WEST) != 0 && intersects(
                    localMinX, localMaxX, localMinZ, localMaxZ,
                    0, 0.5, armInset, 1 - armInset)) return 1.5;
            if ((mask & CONNECT_EAST) != 0 && intersects(
                    localMinX, localMaxX, localMinZ, localMaxZ,
                    0.5, 1, armInset, 1 - armInset)) return 1.5;
            return 0.0;
        }
        if (Blocks.isFenceGate(blockId)) {
            if ((state & GATE_OPEN) != 0) return 0.0;
            boolean hit = (state & FACING_MASK & 1) == 0
                    ? intersects(localMinX, localMaxX, localMinZ, localMaxZ,
                            0, 1, 6.0 / 16.0, 10.0 / 16.0)
                    : intersects(localMinX, localMaxX, localMinZ, localMaxZ,
                            6.0 / 16.0, 10.0 / 16.0, 0, 1);
            return hit ? 1.5 : 0.0;
        }
        if (isPane(blockId)) {
            // 바닐라 CrossCollisionBlock: 기둥 [7,0,7]~[9,16,9] + 연결된 방향의 팔만.
            // 연결 없는 방향으로 셀을 가로지르던 판은 보이지 않는 벽이었다.
            int mask = state & 0x0f;
            if (intersects(localMinX, localMaxX, localMinZ, localMaxZ,
                    7.0 / 16.0, 9.0 / 16.0, 7.0 / 16.0, 9.0 / 16.0)) return 1.0;
            if ((mask & CONNECT_NORTH) != 0 && intersects(localMinX, localMaxX, localMinZ, localMaxZ,
                    7.0 / 16.0, 9.0 / 16.0, 0, 9.0 / 16.0)) return 1.0;
            if ((mask & CONNECT_SOUTH) != 0 && intersects(localMinX, localMaxX, localMinZ, localMaxZ,
                    7.0 / 16.0, 9.0 / 16.0, 7.0 / 16.0, 1)) return 1.0;
            if ((mask & CONNECT_WEST) != 0 && intersects(localMinX, localMaxX, localMinZ, localMaxZ,
                    0, 9.0 / 16.0, 7.0 / 16.0, 9.0 / 16.0)) return 1.0;
            if ((mask & CONNECT_EAST) != 0 && intersects(localMinX, localMaxX, localMinZ, localMaxZ,
                    7.0 / 16.0, 1, 7.0 / 16.0, 9.0 / 16.0)) return 1.0;
            return 0.0;
        }
        if (Blocks.isTrapdoor(blockId) && (state & TRAPDOOR_OPEN) != 0) {
            int facing = state & FACING_MASK;
            boolean hit = (facing & 1) == 0
                    ? intersects(localMinX, localMaxX, localMinZ, localMaxZ,
                            0, 1, facing == 0 ? 0 : 13.0 / 16.0,
                            facing == 0 ? 3.0 / 16.0 : 1)
                    : intersects(localMinX, localMaxX, localMinZ, localMaxZ,
                            facing == 3 ? 0 : 13.0 / 16.0,
                            facing == 3 ? 3.0 / 16.0 : 1, 0, 1);
            return hit ? 1.0 : 0.0;
        }
        if (!isStairs(blockId)) return collisionTop(blockId, state);
        if ((state & STAIR_TOP) != 0) return 1.0;

        if (stairQuarterIntersects(state,
                localMinX, localMaxX, localMinZ, localMaxZ)) return 1.0;
        return 0.5;
    }

    /** 계단의 방향·내외각 state가 차지하는 반높이 quarter들과 XZ 면적이 겹치는지 판정한다. */
    public static boolean stairQuarterIntersects(int state,
            double localMinX, double localMaxX, double localMinZ, double localMaxZ) {
        int mask = stairQuarterMask(state);
        if ((mask & 1) != 0 && intersects(localMinX, localMaxX, localMinZ, localMaxZ,
                0, 0.5, 0, 0.5)) return true;
        if ((mask & 2) != 0 && intersects(localMinX, localMaxX, localMinZ, localMaxZ,
                0.5, 1, 0, 0.5)) return true;
        if ((mask & 4) != 0 && intersects(localMinX, localMaxX, localMinZ, localMaxZ,
                0.5, 1, 0.5, 1)) return true;
        return (mask & 8) != 0 && intersects(localMinX, localMaxX, localMinZ, localMaxZ,
                0, 0.5, 0.5, 1);
    }

    /**
     * MC 1.21.4 {@code StairBlock.SHAPE_BY_STATE} 를 그대로 옮긴 표.
     * {@code index = shape.ordinal() * 4 + facing.get2DDataValue()}, shape 순서는
     * STRAIGHT, INNER_LEFT, INNER_RIGHT, OUTER_LEFT, OUTER_RIGHT, facing 순서는 S,W,N,E,
     * 바닐라 비트는 1=(x-,z-) 2=(x+,z-) 4=(x-,z+) 8=(x+,z+) 이다.
     *
     * <p>40행을 손으로 적으면 표가 자기모순이 될 수 있어(예전 결함: facing=1 outer_right 와
     * facing=3 outer_left 의 윗단이 통째로 사라졌다) 바닐라 표를 그대로 두고 규약 변환만 한다.</p>
     */
    private static final int[] VANILLA_STAIR_SHAPE_BY_STATE = {
        12, 5, 3, 10,
        14, 13, 7, 11,
        13, 7, 11, 14,
        8, 4, 1, 2,
        4, 1, 2, 8,
    };

    /**
     * 계단 단(step)이 차지하는 반높이 사분면 비트
     * (1=(x-,z-), 2=(x+,z-), 4=(x+,z+), 8=(x-,z+)).
     *
     * <p>{@link #VANILLA_STAIR_SHAPE_BY_STATE} 에서 두 가지 규약 변환만 한다. (1) 이 프로젝트의
     * facing 0..3=N,E,S,W 는 윗단의 반대쪽이라 바닐라 S,W,N,E(0..3) 칸에 그대로 대응한다.
     * (2) 비트 4와 8을 맞바꾼다. half({@link #STAIR_TOP})는 사분면을 바꾸지 않는다 — 바닐라도
     * TOP_SHAPES/BOTTOM_SHAPES 가 같은 인덱스를 y 로만 뒤집어 쓴다.</p>
     */
    public static int stairQuarterMask(int state) {
        int facing = state & FACING_MASK;
        int shape = (state >> STAIR_SHAPE_SHIFT) & 7;
        if (shape > STAIR_OUTER_RIGHT) shape = STAIR_STRAIGHT;
        int raw = VANILLA_STAIR_SHAPE_BY_STATE[shape * 4 + facing];
        return (raw & 3) | ((raw & 4) << 1) | ((raw & 8) >> 1);
    }

    private static boolean intersects(double minX, double maxX, double minZ, double maxZ,
            double boxMinX, double boxMaxX, double boxMinZ, double boxMaxZ) {
        final double epsilon = 1e-3;
        return maxX > boxMinX + epsilon && minX < boxMaxX - epsilon
                && maxZ > boxMinZ + epsilon && minZ < boxMaxZ - epsilon;
    }

    // 각 행은 minX,minY,minZ,maxX,maxY,maxZ, 단위는 1/16. 공용 클라이언트 충돌 박스와 같다.
    private static final double[][] SHELF_COLLISION_BOXES = {
        {0, 12, 11, 16, 16, 13}, {0, 0, 13, 16, 16, 16}, {0, 0, 11, 16, 4, 13}
    };
    private static final double[][] ANVIL_COLLISION_BOXES = {
        {2, 0, 2, 14, 4, 14}, {4, 4, 3, 12, 5, 13},
        {6, 5, 4, 10, 10, 12}, {3, 10, 0, 13, 16, 16}
    };
    private static final double[][] RAFFLESIA_COLLISION_BOXES = {
        {4, 0, 4, 12, 2, 12}, {5, 0, 0, 11, 2, 4}, {12, 0, 3, 16, 2, 9},
        {9, 0, 12, 15, 2, 16}, {1, 0, 12, 7, 2, 16}, {0, 0, 3, 4, 2, 9},
        {5, 2, 5, 11, 6, 7}, {5, 2, 9, 11, 6, 11},
        {5, 2, 7, 7, 6, 9}, {9, 2, 7, 11, 6, 9}
    };
    private static final double[][][] COCOA_COLLISION_BOXES = {
        {{6, 7, 1, 10, 12, 5}}, {{5, 5, 1, 11, 12, 7}}, {{4, 3, 1, 12, 12, 9}}
    };
    private static final double[][][] SHELF_MUSHROOM_COLLISION_BOXES = {
        {{3, 4.5, 12.5, 13, 7.5, 19.5}, {5, 6, 14, 11, 7, 18}},
        {{1, 5, 9, 15, 8, 19}, {4, 5, 11, 12, 7, 17}}
    };

    private static double rotatedSupportTop(double[][] boxes, int state,
            double minX, double maxX, double minZ, double maxZ) {
        double top = 0.0;
        int facing = state & FACING_MASK;
        for (double[] box : boxes) {
            double x0 = box[0] / 16.0, z0 = box[2] / 16.0;
            double x1 = box[3] / 16.0, z1 = box[5] / 16.0;
            double rx0, rz0, rx1, rz1;
            if (facing == 1) {
                rx0 = 1 - z1; rz0 = x0; rx1 = 1 - z0; rz1 = x1;
            } else if (facing == 2) {
                rx0 = 1 - x1; rz0 = 1 - z1; rx1 = 1 - x0; rz1 = 1 - z0;
            } else if (facing == 3) {
                rx0 = z0; rz0 = 1 - x1; rx1 = z1; rz1 = 1 - x0;
            } else {
                rx0 = x0; rz0 = z0; rx1 = x1; rz1 = z1;
            }
            if (intersects(minX, maxX, minZ, maxZ, rx0, rx1, rz0, rz1)) {
                top = Math.max(top, box[4] / 16.0);
            }
        }
        return top;
    }

    private static double shelfMushroomSupportTop(int state,
            double localMinX, double localMaxX, double localMinZ, double localMaxZ) {
        int age = (state & com.gameexpert.engine.blocks.P29Rules.AGE) == 0 ? 0 : 1;
        int facing = state & 3;
        double[][] boxes = age == 0
                ? new double[][] {{3, 4.5, 12.5, 13, 7.5, 19.5}, {5, 6, 14, 11, 7, 18}}
                : new double[][] {{1, 5, 9, 15, 8, 19}, {4, 5, 11, 12, 7, 17}};
        double top = 0.0;
        for (double[] box : boxes) {
            double x0 = box[0] / 16.0, z0 = box[2] / 16.0;
            double x1 = box[3] / 16.0, z1 = box[5] / 16.0;
            double rx0, rz0, rx1, rz1;
            if (facing == 1) {
                rx0 = 1 - z1; rz0 = x0; rx1 = 1 - z0; rz1 = x1;
            } else if (facing == 2) {
                rx0 = 1 - x1; rz0 = 1 - z1; rx1 = 1 - x0; rz1 = 1 - z0;
            } else if (facing == 3) {
                rx0 = z0; rz0 = 1 - x1; rx1 = z1; rz1 = 1 - x0;
            } else {
                rx0 = x0; rz0 = z0; rx1 = x1; rz1 = z1;
            }
            if (intersects(localMinX, localMaxX, localMinZ, localMaxZ,
                    rx0, rx1, rz0, rz1)) top = Math.max(top, box[4] / 16.0);
        }
        return top;
    }

    public static double collisionBottom(int blockId, int state) {
        if (RedstoneShapes.has(blockId)) return Math.min(RedstoneShapes.top(blockId, state), RedstoneShapes.bottom(blockId, state));
        double[] decorative = DecorativeCollisionShapes.box(blockId, state);
        if (decorative != null) return decorative[1] / 16;
        if (blockId == Blocks.COCOA) return (7.0 - 2 * Math.min(2, state & 3)) / 16.0;
        // [BLOCK-SHAPES] 천장 종은 몸체가 4/16 에서 시작한다(나머지는 0).
        if (BlockModelShapes.has(blockId)) return BlockModelShapes.collisionBottom(blockId, state);
        if (blockId == Blocks.SHELF_MUSHROOM) {
            return (state & P29Rules.AGE) == 0 ? 4.5 / 16.0 : 5.0 / 16.0;
        }
        if (isSlab(blockId) && (state & ~WATERLOGGED) == SLAB_TOP) return 0.5;
        if (Blocks.isTrapdoor(blockId)
                && (state & TRAPDOOR_OPEN) == 0 && (state & TRAPDOOR_TOP) != 0) return 13.0 / 16.0;
        if (P6Rules.isSpeleothem(blockId)
                && (state & P6Rules.DRIPSTONE_UP) == 0
                && (state & P6Rules.DRIPSTONE_THICKNESS_MASK) == P6Rules.DRIPSTONE_TIP) {
            return 5.0 / 16.0;
        }
        return 0.0;
    }

    private static double pointedDripstoneTop(int state) {
        return (state & P6Rules.DRIPSTONE_UP) != 0
                && (state & P6Rules.DRIPSTONE_THICKNESS_MASK) == P6Rules.DRIPSTONE_TIP
                ? 11.0 / 16.0 : 1.0;
    }

    private static double pointedDripstoneInset(int state) {
        return switch (state & P6Rules.DRIPSTONE_THICKNESS_MASK) {
            case P6Rules.DRIPSTONE_TIP, P6Rules.DRIPSTONE_TIP_MERGE -> 5.0 / 16.0;
            case P6Rules.DRIPSTONE_FRUSTUM -> 4.0 / 16.0;
            case P6Rules.DRIPSTONE_MIDDLE -> 3.0 / 16.0;
            default -> 2.0 / 16.0;
        };
    }

    /**
     * 플레이어 yaw 가 바라보는 수평 방향(0 N · 1 E · 2 S · 3 W). 클라 {@code blocks.ts lookFacing} 과
     * 같은 산술이다(look 벡터 (-sin yaw, -cos yaw)).
     */
    public static int lookFacing(double yaw) {
        return (4 - ((int) Math.floor(yaw * 2 / Math.PI + 0.5) & 3)) & 3;
    }

    /**
     * [BLOCK-SHAPES] 바닐라 {@code FenceGateBlock#useWithoutItem}: 열린 문은 닫고, 닫힌 문을 열 때
     * facing 이 플레이어 방향의 반대면 플레이어 방향으로 돌려 늘 플레이어에게서 멀어지게 연다
     * (열린 판은 facing 쪽으로 선다 — template_fence_gate_open). 닫힌 문의 모양은 facing 과 그 반대가
     * 같아서 저장된 문의 겉모습은 바뀌지 않는다.
     */
    public static int fenceGateStateAfterUse(int state, double playerYaw) {
        if ((state & GATE_OPEN) != 0) return state & ~GATE_OPEN;
        int player = lookFacing(playerYaw);
        int next = (state & FACING_MASK) == opposite(player) ? state & ~FACING_MASK | player : state;
        return next | GATE_OPEN;
    }

    public static int toggleOpen(int blockId, int state) {
        if (Blocks.isDoor(blockId) || Blocks.isFenceGate(blockId)) return state ^ 0x04;
        if (Blocks.isTrapdoor(blockId)) return state ^ TRAPDOOR_OPEN;
        return state;
    }

    public static int bedOtherX(int x, int state) {
        int facing = state & FACING_MASK;
        int direction = (state & BED_HEAD) != 0 ? opposite(facing) : facing;
        return x + dx(direction);
    }

    public static int bedOtherZ(int z, int state) {
        int facing = state & FACING_MASK;
        int direction = (state & BED_HEAD) != 0 ? opposite(facing) : facing;
        return z + dz(direction);
    }

    public static int bedHeadX(int x, int state) {
        return (state & BED_HEAD) != 0 ? x : x + dx(state & FACING_MASK);
    }

    public static int bedHeadZ(int z, int state) {
        return (state & BED_HEAD) != 0 ? z : z + dz(state & FACING_MASK);
    }

    public static boolean matchingBedStates(int state, int otherState) {
        return (state & FACING_MASK) == (otherState & FACING_MASK)
                && ((state ^ otherState) & BED_HEAD) != 0;
    }

    private static int axis(int state) { return (state & 1); }
    private static int connectionBit(int direction) {
        return switch (direction) {
            case 0 -> CONNECT_NORTH;
            case 1 -> CONNECT_EAST;
            case 2 -> CONNECT_SOUTH;
            default -> CONNECT_WEST;
        };
    }
    private static int left(int direction) { return (direction + 3) & 3; }
    private static int opposite(int direction) { return (direction + 2) & 3; }
    private static int dx(int direction) { return direction == 1 ? 1 : direction == 3 ? -1 : 0; }
    private static int dz(int direction) { return direction == 2 ? 1 : direction == 0 ? -1 : 0; }

    @FunctionalInterface
    public interface BlockLookup {
        int block(int x, int y, int z);
    }

    public interface StateLookup extends BlockLookup {
        int state(int x, int y, int z, int blockId);
    }
}
