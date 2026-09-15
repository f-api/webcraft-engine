package com.gameexpert.engine;

import com.gameexpert.engine.blocks.P2Rules;
import com.gameexpert.engine.blocks.P3Rules;
import com.gameexpert.engine.blocks.P6Rules;
import com.gameexpert.engine.blocks.P26Rules;
import com.gameexpert.engine.blocks.VoidEndBlockRules;
import com.gameexpert.engine.blocks.CandleRules;
import com.gameexpert.engine.crop.SweetBerryBushRules;
import com.gameexpert.terrain.Blocks;
import com.gameexpert.terrain.mc.surface.McFreezeTopLayer;

/**
 * 블록의 지지 조건과 지지 상실 드랍을 판정하는 순수 규칙.
 */
public final class SupportRules {

    /** 한 번의 지지 상실 연쇄에서 처리할 수 있는 최대 블록 수. */
    public static final int MAX_CASCADE_BLOCKS = 4096;
    private static final StateLookup ZERO_STATES = (x, y, z, blockId) -> 0;

    private SupportRules() {
    }

    /** 좌표의 현재 블록 ID를 조회하는 함수형 포트. */
    @FunctionalInterface
    public interface BlockLookup {
        int getBlock(int x, int y, int z);
    }

    /** 좌표 블록의 저장 state를 조회하는 포트. */
    @FunctionalInterface
    public interface StateLookup {
        int getState(int x, int y, int z, int blockId);
    }

    /** 해당 블록 ID가 인접 블록의 지지를 계속 필요로 하는가. */
    public static boolean requiresSupport(int blockId) {
        if (com.gameexpert.engine.redstone.RedstoneState.isRedstoneComponent(blockId)) return false;
        Blocks.SupportKind supportKind = Blocks.supportMetadata(blockId).supportKind();
        return supportKind != Blocks.SupportKind.NONE
                && supportKind != Blocks.SupportKind.UNSPECIFIED
                || BuildingBlockRules.isLantern(blockId) || isLightningRod(blockId) || isCopperGolemStatue(blockId)
                // [BLOCK-SHAPES] BellBlock#canSurvive: 부착면이 sturdy 여야 한다.
                || blockId == Blocks.BELL || SignSupportRules.isSign(blockId)
                || isP2SupportedPlant(blockId) || P3Rules.requiresSupport(blockId)
                || P6Rules.requiresSupport(blockId)
                // [VOID-END] 후렴 식물·꽃은 SupportKind 로 표현하지 못하는 이웃 조건이라 전용 규칙이다.
                || VoidEndBlockRules.requiresSupport(blockId);
    }

    /**
     * 아래 지지를 잃으면 블록 단위 중력 갱신을 받아야 하는 블록인가.
     *
     * <p>[CONCRETE] 콘크리트 가루 16색은 이름을 나열하지 않고 <b>연속 ID 범위 술어</b>로
     * 넣는다. 중력 블록은 이 파일 말고도 세 곳(StandaloneFallingBlocks.gravityBlock ·
     * FallingBlockFx.fallingBlock · block-traits.json)에 같은 집합이 있어야 하는데, 색 16개를
     * 네 번 적으면 한 곳만 빠지는 사고가 나기 때문이다. 범위 양 끝 두 상수만 참조하므로
     * lint-single-source.mjs 가 네 지점에서 같은 이름 두 개를 보는 것으로 일치를 고정한다.
     */
    public static boolean isGravityBlock(int blockId) {
        return blockId == Blocks.SAND || blockId == Blocks.GRAVEL || blockId == Blocks.RED_SAND
                || blockId >= Blocks.WHITE_CONCRETE_POWDER
                && blockId <= Blocks.BLACK_CONCRETE_POWDER
                || blockId >= Blocks.ANVIL && blockId <= Blocks.DAMAGED_ANVIL
                // 드래곤 알: 바닐라 DragonEggBlock extends FallingBlock(getDelayAfterPlace 5).
                || blockId == Blocks.DRAGON_EGG;
    }

    /** 중력 블록이 이 블록을 부수거나 밀어내고 아래 칸으로 이동할 수 있는가. */
    public static boolean canGravityFallInto(int blockId) {
        return !Fluids.isSolid(blockId);
    }

    /** 위 칸 변경 직후 흙길이 막혔으면 즉시 흙으로 되돌린다. */
    public static int blockAfterAboveChange(int blockId, int aboveBlockId) {
        return blockId == Blocks.DIRT_PATH && Fluids.isSolid(aboveBlockId) ? Blocks.DIRT : blockId;
    }

    /**
     * 현재 좌표의 블록이 주변 월드 상태에서 유효하게 지지되는가.
     * 지지가 필요 없는 블록은 항상 {@code true}다.
     */
    public static boolean isSupported(int blockId, int x, int y, int z, BlockLookup lookup) {
        return isSupported(blockId, 0, x, y, z, lookup, ZERO_STATES);
    }

    /** state가 지지 면을 나타내는 블록까지 포함한 지지 판정. */
    public static boolean isSupported(int blockId, int state, int x, int y, int z, BlockLookup lookup) {
        return isSupported(blockId, state, x, y, z, lookup, ZERO_STATES);
    }

    /** 주변 블록의 state가 지지 면 형상을 바꾸는 경우까지 포함한 지지 판정. */
    public static boolean isSupported(int blockId, int state, int x, int y, int z,
            BlockLookup lookup, StateLookup states) {
        if (com.gameexpert.engine.redstone.RedstoneState.isRedstoneComponent(blockId)) return true;
        if (SignSupportRules.isSign(blockId)) {
            return SignSupportRules.isSupported(blockId, state, x, y, z, lookup);
        }
        if (VoidEndBlockRules.requiresSupport(blockId)) {
            return VoidEndBlockRules.isSupported(blockId, x, y, z, lookup);
        }
        if (blockId == Blocks.SHELF_MUSHROOM) {
            // FACING은 벽 바깥 방향이다. AGE와 무관하게 반대쪽 부착면만 검사한다.
            int facing = state & BuildingBlockRules.FACING_MASK;
            int supportX = x + (facing == 1 ? -1 : facing == 3 ? 1 : 0);
            int supportZ = z + (facing == 0 ? 1 : facing == 2 ? -1 : 0);
            int support = lookup.getBlock(supportX, y, supportZ);
            // Java 이웃 갱신의 비상주 보존 규약을 유지한다.
            if (support == WorldTickLoop.UNAVAILABLE_BLOCK) return true;
            // [BLOCK-SHAPES] 부착면(버섯 쪽을 보는 면)이 FULL sturdy 여야 한다. 이중 반 블록·계단의
            // 꽉 찬 옆면·유리가 되고, 호박 연결 예외는 적용하지 않는다.
            return BlockFaceSturdiness.isFaceSturdy(support,
                    states.getState(supportX, y, supportZ, support), HORIZONTAL_FACE[facing],
                    BlockFaceSturdiness.FULL);
        }
        if (BuildingBlockRules.isLantern(blockId)) {
            // [VANILLA-FLAME] 26.3 LanternBlock.canSurvive: hanging 이면 위 칸의 아랫면, 아니면 아래
            // 칸의 윗면이 중심을 받쳐야 한다(Block.canSupportCenter). 철·구리 랜턴이 같은 규칙이다.
            boolean hanging = (state & BuildingBlockRules.LANTERN_HANGING) != 0;
            // [BLOCK-SHAPES] canSupportCenter 는 면별 CENTER sturdy 다(아래 반 블록 밑면·계단 등).
            return sturdy(lookup, states, x, hanging ? y + 1 : y - 1, z,
                    hanging ? BlockFaceSturdiness.DOWN : BlockFaceSturdiness.UP, BlockFaceSturdiness.CENTER);
        }
        if (isLightningRod(blockId)) {
            return switch (state & 7) {
                case 0 -> isSupportSolid(lookup.getBlock(x, y - 1, z));
                case 1 -> isSupportSolid(lookup.getBlock(x, y + 1, z));
                case 2 -> isSupportSolid(lookup.getBlock(x, y, z + 1));
                case 3 -> isSupportSolid(lookup.getBlock(x - 1, y, z));
                case 4 -> isSupportSolid(lookup.getBlock(x, y, z - 1));
                case 5 -> isSupportSolid(lookup.getBlock(x + 1, y, z));
                default -> false;
            };
        }
        if (isCopperGolemStatue(blockId)) {
            return isSupportSolid(lookup.getBlock(x, y - 1, z));
        }
        if (blockId == Blocks.FROGSPAWN) {
            return lookup.getBlock(x, y - 1, z) == Blocks.WATER_SOURCE
                    && !Fluids.isSolid(lookup.getBlock(x, y + 1, z));
        }
        // [PITCHER] DoublePlantBlock.canSurvive: the upper half needs its own lower half below; the lower
        // pitcher crop sits on farmland (#supports_crops) and the lower pitcher plant on dirt or farmland
        // (VegetationBlock.mayPlaceOn, the torchflower's soil).
        if (PitcherRules.isPitcher(blockId) && PitcherRules.isUpper(state)) {
            return lookup.getBlock(x, y - 1, z) == blockId
                    && !PitcherRules.isUpper(states.getState(x, y - 1, z, blockId));
        }
        if (blockId == Blocks.PITCHER_PLANT) {
            int below = lookup.getBlock(x, y - 1, z);
            return below == Blocks.FARMLAND || P6Rules.isSaplingSoil(below);
        }
        if (blockId == Blocks.TORCHFLOWER_CROP || blockId == Blocks.PITCHER_CROP) {
            return lookup.getBlock(x, y - 1, z) == Blocks.FARMLAND;
        }
        if (blockId == Blocks.TORCHFLOWER) {
            int below = lookup.getBlock(x, y - 1, z);
            return below == Blocks.FARMLAND || P6Rules.isSaplingSoil(below);
        }
        if (P6Rules.isAmethystBud(blockId)) {
            // [BLOCK-SHAPES] AmethystClusterBlock#canSurvive: 붙은 이웃의 그 면이 FULL sturdy 여야 한다.
            int full = BlockFaceSturdiness.FULL;
            return switch (BuildingBlockRules.normalizeState(blockId, state)) {
                case P6Rules.AMETHYST_FACING_UP ->
                    sturdy(lookup, states, x, y - 1, z, BlockFaceSturdiness.UP, full);
                case P6Rules.AMETHYST_FACING_DOWN ->
                    sturdy(lookup, states, x, y + 1, z, BlockFaceSturdiness.DOWN, full);
                case P6Rules.AMETHYST_FACING_NORTH ->
                    sturdy(lookup, states, x, y, z + 1, BlockFaceSturdiness.NORTH, full);
                case P6Rules.AMETHYST_FACING_EAST ->
                    sturdy(lookup, states, x - 1, y, z, BlockFaceSturdiness.EAST, full);
                case P6Rules.AMETHYST_FACING_SOUTH ->
                    sturdy(lookup, states, x, y, z - 1, BlockFaceSturdiness.SOUTH, full);
                case P6Rules.AMETHYST_FACING_WEST ->
                    sturdy(lookup, states, x + 1, y, z, BlockFaceSturdiness.WEST, full);
                default -> false;
            };
        }
        if (blockId == Blocks.DEAD_BUSH) {
            return SurfaceDecorator.isDeadBushSupport(lookup.getBlock(x, y - 1, z));
        }
        if (blockId == Blocks.BAMBOO) {
            return isBambooSoil(lookup.getBlock(x, y - 1, z));
        }
        if (blockId == Blocks.KELP) {
            int below = lookup.getBlock(x, y - 1, z);
            return below == Blocks.KELP || isSupportSolid(below);
        }
        if (blockId == Blocks.SNOW) {
            int below = lookup.getBlock(x, y - 1, z);
            int belowState = states.getState(x, y - 1, z, below);
            return canSupportSnowLayer(below, belowState);
        }
        if (blockId == Blocks.VINE) {
            return isVineSupported(state, x, y, z, lookup, states);
        }
        // [SPRING-TO-LIFE] 마른 풀 두 종은 흙 계열 말고 모래·붉은 모래·테라코타 위에도 놓인다 [B].
        // DIRT_OR_GRASS_BELOW 를 그대로 쓰면 사막·황무지가 이 풀의 정본 생성지인데 그 바닥에
        // 놓이지 못한다 — 그래서 이 갈래만 메타데이터 switch 앞에서 가로챈다.
        if (Blocks.isDryGrass(blockId)) {
            return P26Rules.isDryGrassSoil(lookup.getBlock(x, y - 1, z));
        }
        // 선인장 꽃은 고체 상면 말고 선인장 꼭대기에도 붙는다 [B]. 선인장은 XZ 1픽셀 인셋
        // 박스라 isSupportSolid 에 걸리지 않으므로 그 갈래를 명시적으로 더한다.
        if (blockId == Blocks.CACTUS_FLOWER) {
            int below = lookup.getBlock(x, y - 1, z);
            return P26Rules.isCactusFlowerSupport(below, isSupportSolid(below));
        }
        if (blockId == Blocks.RED_SHRUB) {
            return RedShrubRules.isSoil(lookup.getBlock(x, y - 1, z));
        }
        if (blockId == Blocks.RAFFLESIA) {
            return RafflesiaRules.isSupported(x, y, z, lookup);
        }
        // [CROP-BERRY] 달콤한 열매 덤불은 바닐라 BushBlock.mayPlaceOn 이 #minecraft:dirt 에
        // **경작지**를 더한 목록이라 DIRT_OR_GRASS_BELOW 만으로는 경작지 위를 놓친다 —
        // 위 마른 풀과 같은 꼴로 이 갈래만 메타데이터 switch 앞에서 가로챈다.
        if (blockId == Blocks.SWEET_BERRY_BUSH) {
            return SweetBerryBushRules.isSoil(lookup.getBlock(x, y - 1, z));
        }
        // [ENCHANT-WIDE] 바닐라 #minecraft:supports_lily_pad(얼음·살얼음)도 수련잎을 받친다.
        if (blockId == Blocks.LILY_PAD) {
            int below = lookup.getBlock(x, y - 1, z);
            if (below == Blocks.ICE || below == Blocks.FROSTED_ICE) return true;
        }
        // [BLOCK-SHAPES] 바닐라가 면별 sturdy 로 판정하는 부착(26.3 javap):
        //   TorchBlock · RedstoneTorchBlock · CandleBlock: Block.canSupportCenter(below, UP)
        //   DoorBlock(아래 칸): below.isFaceSturdy(UP), 위 칸은 같은 문의 아래 칸 위에만 선다
        //   BaseRailBlock: canSupportRigidBlock(below)
        //   BasePressurePlateBlock: canSupportRigidBlock(below) || canSupportCenter(below, UP)
        //   BellBlock: 바닥은 below.isFaceSturdy(UP), 천장은 canSupportCenter(above, DOWN), 벽은
        //   facing 이웃의 마주 보는 면 FULL.
        if (blockId == Blocks.TORCH || blockId == Blocks.COPPER_TORCH || blockId == Blocks.REDSTONE_TORCH
                || CandleRules.isCandle(blockId)) {
            return sturdy(lookup, states, x, y - 1, z, BlockFaceSturdiness.UP, BlockFaceSturdiness.CENTER);
        }
        if (Blocks.isDoor(blockId)) {
            if ((state & BuildingBlockRules.DOOR_UPPER) != 0) return lookup.getBlock(x, y - 1, z) == blockId;
            return sturdy(lookup, states, x, y - 1, z, BlockFaceSturdiness.UP, BlockFaceSturdiness.FULL);
        }
        if (blockId == Blocks.RAIL) {
            return sturdy(lookup, states, x, y - 1, z, BlockFaceSturdiness.UP, BlockFaceSturdiness.RIGID);
        }
        if (isPressurePlate(blockId)) {
            return sturdy(lookup, states, x, y - 1, z, BlockFaceSturdiness.UP, BlockFaceSturdiness.RIGID)
                    || sturdy(lookup, states, x, y - 1, z, BlockFaceSturdiness.UP, BlockFaceSturdiness.CENTER);
        }
        if (blockId == Blocks.BELL) return bellCanSurvive(state, x, y, z, lookup, states);
        Blocks.SupportKind supportKind = Blocks.supportMetadata(blockId).supportKind();
        boolean metadataSupported = switch (supportKind) {
            case DIRT_OR_GRASS_BELOW -> {
                int below = lookup.getBlock(x, y - 1, z);
                yield P6Rules.isSaplingSoil(below);
            }
            case SOLID_BELOW -> isSupportSolid(lookup.getBlock(x, y - 1, z));
            case FARMLAND_BELOW -> lookup.getBlock(x, y - 1, z) == Blocks.FARMLAND;
            case SAND_AND_WATER -> {
                int below = lookup.getBlock(x, y - 1, z);
                yield below == Blocks.SUGARCANE
                        || isSugarCaneSoil(below)
                                && hasHorizontalWater(lookup, x, y - 1, z);
            }
            case CACTUS_COLUMN -> {
                int below = lookup.getBlock(x, y - 1, z);
                yield (below == Blocks.SAND || below == Blocks.RED_SAND
                        || below == Blocks.CACTUS)
                        && !hasHorizontalSolidNeighbor(lookup, x, y, z);
            }
            case WATER_BELOW -> isSourceWaterMedium(lookup.getBlock(x, y - 1, z));
            case WALL_ANY -> hasHorizontalSolidNeighbor(lookup, x, y, z);
            // [BLOCK-SHAPES] WallTorchBlock#canSurvive: 붙은 벽의 마주 보는 면이 FULL sturdy.
            case WALL_NORTH -> sturdy(lookup, states, x, y, z - 1, BlockFaceSturdiness.SOUTH, BlockFaceSturdiness.FULL);
            case WALL_EAST -> sturdy(lookup, states, x + 1, y, z, BlockFaceSturdiness.WEST, BlockFaceSturdiness.FULL);
            case WALL_SOUTH -> sturdy(lookup, states, x, y, z + 1, BlockFaceSturdiness.NORTH, BlockFaceSturdiness.FULL);
            case WALL_WEST -> sturdy(lookup, states, x - 1, y, z, BlockFaceSturdiness.EAST, BlockFaceSturdiness.FULL);
            case NONE, UNSPECIFIED -> true;
        };
        if (!metadataSupported) return false;
        // [BED-COLOR] 짝 셀은 같은 색이어야 한다. 바닐라도 두 셀이 같은 블록이며, 색이 다르면
        // 하나의 침대가 아니라 반쪽 둘이므로 서로를 지지하지 않는다.
        if (Blocks.isBed(blockId)) {
            return lookup.getBlock(
                    BuildingBlockRules.bedOtherX(x, state), y,
                    BuildingBlockRules.bedOtherZ(z, state)) == blockId;
        }

        // 발광 이끼 state의 여섯 비트는 각각 부착 면이다. 지지를 잃은 면은 이웃 갱신이
        // state에서 잘라내며, 적어도 한 면이 남아 있는 동안 블록 자체는 유지한다.
        if (blockId == Blocks.GLOW_LICHEN) {
            return supportedGlowLichenFaces(state, x, y, z, lookup) != 0;
        }

        // P2 식물은 공통 메타데이터로 표현하기 어려운 "같은 식물 위/아래" 조건만 평범한 위임으로 보강한다.
        int below = lookup.getBlock(x, y - 1, z);
        boolean supportedBelow = below == blockId || isSupportSolid(below);
        boolean attachedToWall = hasHorizontalSolidNeighbor(lookup, x, y, z);
        boolean samePlantAbove = lookup.getBlock(x, y + 1, z) == blockId;
        boolean p2Supported = P2Rules.hasRequiredSupport(blockId, supportedBelow, attachedToWall, samePlantAbove);
        int above = lookup.getBlock(x, y + 1, z);
        boolean p3Supported = P3Rules.hasRequiredSupport(blockId, supportedBelow,
                isSupportSolid(above), attachedToWall, p2Supported);
        return p3Supported && P6Rules.hasRequiredSupport(blockId, state, below, above,
                isSupportSolid(below), isSupportSolid(above));
    }

    /**
     * 지지 상실로 블록이 제거될 때 생성할 아이템 ID. {@link Blocks#AIR}이면 드랍하지 않는다.
     */
    public static int dropForSupportLoss(int blockId) {
        return Blocks.supportMetadata(blockId).supportLossDrop();
    }

    private static boolean isSupportSolid(int blockId) {
        return Fluids.isSolid(blockId);
    }

    /** 수평 facing(0 N · 1 E · 2 S · 3 W) 쪽을 보는 면(BlockFaceSturdiness 면 번호). */
    private static final int[] HORIZONTAL_FACE = {
        BlockFaceSturdiness.NORTH, BlockFaceSturdiness.EAST, BlockFaceSturdiness.SOUTH, BlockFaceSturdiness.WEST,
    };
    /** 수평 facing 의 반대쪽을 보는 면. */
    private static final int[] OPPOSITE_HORIZONTAL_FACE = {
        BlockFaceSturdiness.SOUTH, BlockFaceSturdiness.WEST, BlockFaceSturdiness.NORTH, BlockFaceSturdiness.EAST,
    };

    /**
     * [BLOCK-SHAPES] 이웃 칸 한 면의 바닐라 {@code isFaceSturdy}. 적재되지 않은 칸은 이전 판정(고체 여부)을
     * 그대로 쓴다 — 이웃 갱신의 비상주 규약을 바꾸지 않는다.
     */
    private static boolean sturdy(BlockLookup lookup, StateLookup states, int x, int y, int z, int face, int type) {
        int id = lookup.getBlock(x, y, z);
        if (id < 0) return isSupportSolid(id);
        return BlockFaceSturdiness.isFaceSturdy(id, states.getState(x, y, z, id), face, type);
    }

    /** [BLOCK-SHAPES] {@code BellBlock#canSurvive}(state 어휘는 BlockModelShapes). */
    private static boolean bellCanSurvive(int state, int x, int y, int z, BlockLookup lookup, StateLookup states) {
        int attachment = BlockModelShapes.bellAttachment(state);
        if (attachment == BlockModelShapes.BELL_FLOOR) {
            return sturdy(lookup, states, x, y - 1, z, BlockFaceSturdiness.UP, BlockFaceSturdiness.FULL);
        }
        if (attachment == BlockModelShapes.BELL_CEILING) {
            return sturdy(lookup, states, x, y + 1, z, BlockFaceSturdiness.DOWN, BlockFaceSturdiness.CENTER);
        }
        int facing = state & BuildingBlockRules.FACING_MASK;
        if (bellWall(facing, x, y, z, lookup, states)) return true;
        // BellBlock#updateShape 는 두 벽 종을 없애지 않는다 — 한쪽 벽을 잃으면 남은 벽을 보는 한 벽
        // 종이 된다(bellStateAfterNeighborChange). 그래서 어느 한쪽이라도 sturdy 면 남는다.
        return attachment == BlockModelShapes.BELL_DOUBLE_WALL
                && bellWall((facing + 2) & 3, x, y, z, lookup, states);
    }

    /** 수평 facing 쪽 이웃이 종을 보는 면이 FULL sturdy 인가. */
    private static boolean bellWall(int facing, int x, int y, int z, BlockLookup lookup, StateLookup states) {
        int dx = facing == 1 ? 1 : facing == 3 ? -1 : 0;
        int dz = facing == 0 ? -1 : facing == 2 ? 1 : 0;
        return sturdy(lookup, states, x + dx, y, z + dz, OPPOSITE_HORIZONTAL_FACE[facing], BlockFaceSturdiness.FULL);
    }

    /**
     * [BLOCK-SHAPES] {@code BellBlock#updateShape} 의 축 방향 전이: 벽 하나를 잃은 두 벽 종은 남은 벽을
     * 보는 한 벽 종이 되고, 반대편 이웃이 sturdy 면을 드러낸 한 벽 종은 두 벽 종이 된다. 나머지는 그대로다.
     * 정적판 {@code BlockModelShapes.bellStateAfterNeighborChange} 와 같은 식이다.
     */
    public static int bellStateAfterNeighborChange(int state, int x, int y, int z,
            BlockLookup lookup, StateLookup states) {
        int attachment = BlockModelShapes.bellAttachment(state);
        int facing = state & BuildingBlockRules.FACING_MASK;
        int opposite = (facing + 2) & 3;
        // 적재되지 않은 벽은 판정하지 않는다(정적판도 비상주 칸이면 state 를 두고 넘어간다).
        for (int side : new int[] {facing, opposite}) {
            int dx = side == 1 ? 1 : side == 3 ? -1 : 0;
            int dz = side == 0 ? -1 : side == 2 ? 1 : 0;
            if (lookup.getBlock(x + dx, y, z + dz) < 0) return state;
        }
        boolean facingWall = bellWall(facing, x, y, z, lookup, states);
        boolean oppositeWall = bellWall(opposite, x, y, z, lookup, states);
        int shift = BlockModelShapes.BELL_ATTACHMENT_SHIFT;
        if (attachment == BlockModelShapes.BELL_DOUBLE_WALL) {
            if (!facingWall && oppositeWall) return BlockModelShapes.BELL_SINGLE_WALL << shift | opposite;
            if (facingWall && !oppositeWall) return BlockModelShapes.BELL_SINGLE_WALL << shift | facing;
            return state;
        }
        if (attachment == BlockModelShapes.BELL_SINGLE_WALL && oppositeWall) {
            return BlockModelShapes.BELL_DOUBLE_WALL << shift | facing;
        }
        return state;
    }

    private static boolean isPressurePlate(int blockId) {
        return blockId == Blocks.STONE_PRESSURE_PLATE || blockId == Blocks.OAK_PRESSURE_PLATE
                || blockId == Blocks.POPLAR_PRESSURE_PLATE;
    }

    private static boolean isLightningRod(int blockId) {
        return blockId == Blocks.LIGHTNING_ROD
                || blockId >= Blocks.EXPOSED_LIGHTNING_ROD
                && blockId <= Blocks.WAXED_OXIDIZED_LIGHTNING_ROD;
    }

    private static boolean isCopperGolemStatue(int blockId) {
        return blockId >= Blocks.COPPER_GOLEM_STATUE
                && blockId <= Blocks.WAXED_OXIDIZED_COPPER_GOLEM_STATUE;
    }

    private static boolean isVineSupported(int state, int x, int y, int z,
            BlockLookup lookup, StateLookup states) {
        int above = lookup.getBlock(x, y + 1, z);
        int inherited = above == Blocks.VINE
                ? states.getState(x, y + 1, z, Blocks.VINE)
                        & (P2Rules.VINE_NORTH | P2Rules.VINE_EAST
                                | P2Rules.VINE_SOUTH | P2Rules.VINE_WEST)
                : 0;
        boolean horizontal = (state & P2Rules.VINE_NORTH) != 0
                        && (isSupportSolid(lookup.getBlock(x, y, z - 1))
                                || (inherited & P2Rules.VINE_NORTH) != 0)
                || (state & P2Rules.VINE_EAST) != 0
                        && (isSupportSolid(lookup.getBlock(x + 1, y, z))
                                || (inherited & P2Rules.VINE_EAST) != 0)
                || (state & P2Rules.VINE_SOUTH) != 0
                        && (isSupportSolid(lookup.getBlock(x, y, z + 1))
                                || (inherited & P2Rules.VINE_SOUTH) != 0)
                || (state & P2Rules.VINE_WEST) != 0
                        && (isSupportSolid(lookup.getBlock(x - 1, y, z))
                                || (inherited & P2Rules.VINE_WEST) != 0);
        boolean ceiling = (state & P2Rules.VINE_HANGING) != 0
                && isSupportSolid(above);
        return P2Rules.hasRequiredSupport(Blocks.VINE, false, horizontal, ceiling);
    }

    /**
     * Java 1.21.4 SnowLayerBlock의 위쪽 전체 충돌면 + 예외 태그를 프로젝트 state로 표현한다.
     * 진흙은 명시 허용 태그이며, 얼음 계열은 명시 금지 태그다.
     */
    static boolean canSupportSnowLayer(int blockId, int state) {
        if (McFreezeTopLayer.cannotSupportSnowLayer(blockId)) return false;
        if (blockId == Blocks.MUD) return true;
        // [FROST-SOUL] 영혼 모래도 #support_override_snow_layer 다(14/16 충돌이어도 눈 층을 받친다).
        if (blockId == Blocks.SOUL_SAND) return true;
        if (blockId == Blocks.SNOW) return state >= 8;
        if (BuildingBlockRules.isSlab(blockId)) {
            return state == BuildingBlockRules.SLAB_TOP || state == BuildingBlockRules.SLAB_DOUBLE;
        }
        if (BuildingBlockRules.isStairs(blockId)) {
            return (state & BuildingBlockRules.STAIR_TOP) != 0;
        }
        if (Blocks.isTrapdoor(blockId)) {
            return (state & BuildingBlockRules.TRAPDOOR_OPEN) == 0
                    && (state & BuildingBlockRules.TRAPDOOR_TOP) != 0;
        }
        if (blockId == Blocks.CACTUS || Blocks.isDoor(blockId) || Blocks.isChestShaped(blockId)
                || blockId == Blocks.FARMLAND || blockId == Blocks.COBWEB
                || blockId == Blocks.BIG_DRIPLEAF || P6Rules.isSpeleothem(blockId)) return false;
        boolean fullFootprint = BuildingBlockRules.hasFullSquareFace(blockId)
                || Blocks.isGlassBlock(blockId) || BlockFamilies.isLeaves(blockId);
        return fullFootprint && BuildingBlockRules.collisionTop(blockId, state) >= 1.0;
    }

    private static boolean isP2SupportedPlant(int blockId) {
        return blockId == Blocks.BAMBOO || blockId == Blocks.VINE
                || blockId == Blocks.FERN || blockId == Blocks.BUSH
                || blockId == Blocks.MOSS_CARPET;
    }

    static boolean isSugarCaneSoil(int blockId) {
        return blockId == Blocks.GRASS || blockId == Blocks.DIRT
                || blockId == Blocks.COARSE_DIRT || blockId == Blocks.PODZOL
                || blockId == Blocks.SAND || blockId == Blocks.RED_SAND;
    }

    static boolean isBambooSoil(int blockId) {
        return blockId == Blocks.BAMBOO || blockId == Blocks.SAND
                || blockId == Blocks.RED_SAND || blockId == Blocks.GRAVEL
                || P6Rules.isSaplingSoil(blockId);
    }

    private static boolean hasHorizontalWater(BlockLookup lookup, int x, int y, int z) {
        return supportsSugarCaneAdjacently(lookup.getBlock(x - 1, y, z))
                || supportsSugarCaneAdjacently(lookup.getBlock(x + 1, y, z))
                || supportsSugarCaneAdjacently(lookup.getBlock(x, y, z - 1))
                || supportsSugarCaneAdjacently(lookup.getBlock(x, y, z + 1));
    }

    /** 바닐라 SugarCaneBlock.canSurvive: 물 유체 또는 [FROST-SOUL] #supports_sugar_cane_adjacently(살얼음). */
    private static boolean supportsSugarCaneAdjacently(int blockId) {
        return Fluids.isWaterMedium(blockId) || blockId == Blocks.FROSTED_ICE;
    }

    /** Waterlogged single-ID plants retain a source-water fluid state. */
    static boolean isSourceWaterMedium(int blockId) {
        return blockId == Blocks.WATER_SOURCE || Fluids.isSubmergedDecoration(blockId);
    }

    /** Returns only glow-lichen face bits whose adjacent support still exists. */
    static int supportedGlowLichenFaces(int state, int x, int y, int z, BlockLookup lookup) {
        int faces = 0;
        if ((state & 0x01) != 0 && isSupportSolid(lookup.getBlock(x, y - 1, z))) faces |= 0x01;
        if ((state & 0x02) != 0 && isSupportSolid(lookup.getBlock(x, y + 1, z))) faces |= 0x02;
        if ((state & 0x04) != 0 && isSupportSolid(lookup.getBlock(x, y, z + 1))) faces |= 0x04;
        if ((state & 0x08) != 0 && isSupportSolid(lookup.getBlock(x - 1, y, z))) faces |= 0x08;
        if ((state & 0x10) != 0 && isSupportSolid(lookup.getBlock(x, y, z - 1))) faces |= 0x10;
        if ((state & 0x20) != 0 && isSupportSolid(lookup.getBlock(x + 1, y, z))) faces |= 0x20;
        return faces;
    }

    private static boolean hasHorizontalSolidNeighbor(BlockLookup lookup, int x, int y, int z) {
        return isSupportSolid(lookup.getBlock(x - 1, y, z))
                || isSupportSolid(lookup.getBlock(x + 1, y, z))
                || isSupportSolid(lookup.getBlock(x, y, z - 1))
                || isSupportSolid(lookup.getBlock(x, y, z + 1));
    }
}
