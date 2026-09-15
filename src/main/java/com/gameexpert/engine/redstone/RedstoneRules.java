package com.gameexpert.engine.redstone;

import static com.gameexpert.terrain.Blocks.*;
import static com.gameexpert.engine.redstone.RedstoneHost.*;
import static com.gameexpert.engine.redstone.RedstoneState.CRAFTER_ID;
import static com.gameexpert.engine.redstone.RedstoneState.DROPPER_ID;

import com.gameexpert.engine.BlockFaceSturdiness;
import com.gameexpert.engine.BlockFamilies;
import com.gameexpert.engine.BuildingBlockRules;
import com.gameexpert.engine.Fluids;
import com.gameexpert.engine.FurnaceRules;
import com.gameexpert.engine.blocks.CandleRules;

/** [REDSTONE] 정적판 world/redstoneRules.ts와 같은 도체·지지면·피스톤 물성. */
public final class RedstoneRules {
    private RedstoneRules() {}

    /** `isRedstoneConductor(Blocks::never)` 인 블록. */
    private static boolean neverConducts(int id) {
      return id == REDSTONE_BLOCK || id == OBSERVER || id == PISTON || id == STICKY_PISTON
        || id == MOVING_PISTON || id == TNT || id == GLOWSTONE || id == SEA_LANTERN || id == BEACON
        || id == ICE || id == FROSTED_ICE || id == CHORUS_FLOWER || id == BAMBOO || id == POWDER_SNOW
        || id == POINTED_DRIPSTONE || isGlassBlock(id) || BlockFamilies.isLeaves(id) || isCopperBulb(id);
    }

    /** `BlockState#isRedstoneConductor`. */
    public static boolean isConductor(int id, int state) {
      if (id == SOUL_SAND || id == MUD) return true;
      if (neverConducts(id)) return false;
      return BuildingBlockRules.isFullCollisionShape(id, state);
    }

    /** `isFaceSturdy(level, pos, face, SupportType.FULL)`. face 는 바닐라 3D 값. */
    public static boolean isFaceSturdy(int id, int state, int face) {
      return BlockFaceSturdiness.isFaceSturdy(id, state, face, BlockFaceSturdiness.FULL);
    }

    /** `Block.canSupportRigidBlock`. */
    public static boolean isRigidTop(int id, int state) {
      return BlockFaceSturdiness.canSupportRigidBlock(id, state);
    }

    /** `Block.canSupportCenter`. */
    public static boolean canSupportCenter(int id, int state, int face) {
      return BlockFaceSturdiness.canSupportCenter(id, state, face);
    }

    /** 블록 엔티티 보유(피스톤이 밀지 못함). */
    public static boolean hasBlockEntity(int id) {
      return isChestShaped(id) || id == BARREL || FurnaceRules.isFurnace(id)
        || id == BREWING_STAND
        || id == HOPPER || id == DISPENSER || id == DROPPER_ID || id == CRAFTER_ID || id == ENCHANTING_TABLE
        || id == BEACON || id == JUKEBOX || id == LECTERN || isShulkerBox(id) || id == BEE_NEST
        || id == BEEHIVE || id == CAMPFIRE || id >= SPAWNER_BASE && id <= SPAWNER_BASE + 2
        || id == TRIAL_SPAWNER || id == VAULT || id == END_PORTAL || id == END_GATEWAY || id == CONDUIT
        || isShelf(id) || id == SCULK_SENSOR || id == SCULK_SHRIEKER || id == SCULK_CATALYST
        || id == CREAKING_HEART || id == DAYLIGHT_DETECTOR || id == COMPARATOR || isBanner(id)
        || isDecoratedPot(id) || isBed(id) || id == BELL || id == TRAPPED_CHEST;
    }

    /**
     * `hasAnalogOutputSignal` — 권위 `analogOutputAt`(Java WorldRuntime · 정적판 StandaloneWorldRuntime)이
     * 값을 내는 블록과 감지 레일. 두 권위의 analogOutputAt 가지와 같은 집합이다.
     */
    public static boolean hasAnalogOutput(int id) {
      return isShelf(id) || id != ENDER_CHEST && isChestShaped(id) || id == BARREL || isShulkerBox(id)
        || isDecoratedPot(id) || id == HOPPER || id == DISPENSER || id == DROPPER_ID || id == CRAFTER_ID
        || FurnaceRules.isFurnace(id) || id == BREWING_STAND || id == COMPOSTER || id == JUKEBOX || id == CAKE
        || id == END_PORTAL_FRAME || id == BEE_NEST || id == BEEHIVE || id == CAULDRON || isCopperBulb(id)
        || id >= COPPER_GOLEM_STATUE && id <= WAXED_OXIDIZED_COPPER_GOLEM_STATUE || id == LECTERN
        || id == DETECTOR_RAIL;
    }

    /** `getDestroySpeed == -1`. */
    public static boolean isUnbreakable(int id) {
      return id == BEDROCK || id == BARRIER || id == END_PORTAL || id == END_PORTAL_FRAME || id == END_GATEWAY;
    }

    /** `BlockBehaviour.getPistonPushReaction`(엔진이 소유한 레드스톤 부품은 엔진이 먼저 답한다). */
    public static int pushReaction(int id, int state) {
      if (id == OBSIDIAN || id == CRYING_OBSIDIAN || isUnbreakable(id)) return PUSH_BLOCK;
      if (isGlazedTerracotta(id)) return PUSH_ONLY;
      if (!Fluids.isSolid(id) || isDoor(id) || isBed(id) || id == CAKE || BuildingBlockRules.isLantern(id) || CandleRules.isCandle(id)
        || id == FLOWER_POT || id == POINTED_DRIPSTONE || id == TURTLE_EGG || id == SEA_PICKLE
        || id == COCOA || id == CHORUS_PLANT || id == CHORUS_FLOWER || id == BAMBOO || id == COBWEB
        || id == SNOW || id == DRAGON_EGG || id == BIG_DRIPLEAF || id == SMALL_DRIPLEAF || id == FROGSPAWN
        || id == LILY_PAD || id == LADDER || isBanner(id) || isDecoratedPot(id) || isShulkerBox(id)
        || id == BELL || id == CONDUIT) {
        return PUSH_DESTROY;
      }
      return PUSH_NORMAL;
    }
}
