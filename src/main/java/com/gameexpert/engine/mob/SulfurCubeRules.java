package com.gameexpert.engine.mob;

import java.util.Set;

import com.gameexpert.terrain.Blocks;

/** Exact data-driven gameplay constants for Java 26.3-snapshot-7 {@code sulfur_cube}. */
public final class SulfurCubeRules {
    private SulfurCubeRules() {}

    /** EntityType base dimensions are 0.49; adult spawn scale/size is two. */
    public static final double BASE_WIDTH = 0.49;
    public static final double BASE_HEIGHT = 0.49;
    public static final double WIDTH = 0.98;
    public static final double HEIGHT = 0.98;
    public static final double EYE_HEIGHT = 0.35;
    /**
     * {@code SulfurCube} 는 {@code AbstractCubeMob} 의 크기 계단을 1·2 로만 쓴다
     * ({@code SulfurCube.MIN_SIZE=1}, {@code MAX_SIZE=2}, {@code SPLIT_COUNT=2}).
     * {@code setSpawnSize} 는 새끼를 1, 성체를 2 로 놓고 {@code setSize(1,…)} 는 곧바로
     * {@code setBaby(true)} 를 부른다 — 즉 <b>크기 1 ⟺ 새끼</b> 다. 개구리의
     * {@code Frog#canEat} 는 이 크기 1 만 먹는다(성체 큐브는 먹지 않는다).
     */
    public static final int MIN_SIZE = 1;
    public static final int ADULT_SIZE = 2;
    public static final int MAX_HEALTH = 8;
    public static final double ARMOR = 0.0;
    public static final double TOUGHNESS = 0.0;
    /** AbstractCubeMob: 0.2 + 0.1 * size. */
    public static final double MOVEMENT_SPEED = 0.4;
    public static final boolean FIRE_IMMUNE = true;
    public static final int PICKUP_COOLDOWN_TICKS = 50; // 100 MC ticks
    public static final int EXPLOSIVE_FUSE_TICKS = 60; // 120 MC ticks
    public static final int EXPLOSIVE_POWER = 3;
    public static final double FLUID_JUMP_THRESHOLD = HEIGHT * 0.2;

    /**
     * 아키타입 표. [SULFUR-KB] 넉백 저항 가산치는 {@code data/minecraft/sulfur_cube_archetype/*.json} 의
     * {@code minecraft:knockback_resistance} {@code add_value} 원문(double; 0.7·0.4·0.8 은 float 출신
     * {@code 0.699999988079071} 등)이고, 넉백 배율 {@code knockback_modifiers.horizontal_power /
     * vertical_power} 는 {@code SulfurCubeArchetype$KnockbackModifiers} 의 float 이다.
     */
    public enum Archetype {
        BOUNCY(-2, .9, -.7, -.99, true, .4125, .105, 0, 0),
        EXPLOSIVE(-1, .5, -.7, -.7, true, .4125, .09, 0, EXPLOSIVE_POWER),
        FAST_FLAT(-1, .5, -.8, -.99, false, .9125, .09, 0, 0),
        FAST_SLIDING(.5, .1, -.95, -.99, false, .6625, .09, 0, 0),
        HIGH_RESISTANCE(0.699999988079071, .2, 0, -.99, false, .4125, .09, 0, 0),
        HOT(-1, .5, -.7, -.9, true, .4125, .09, 1, 0),
        LIGHT(-1, 1, -.7, .8, true, .4125, .18, 0, 0),
        REGULAR(-1, .5, -.7, -.9, true, .4125, .09, 0, 0),
        SLOW_BOUNCY(0.4000000059604645, .6, -.7, -.95, false, .4125, .24, 0, 0),
        SLOW_FLAT(.5, .4, -.6, -.9, false, .4125, .105, 0, 0),
        SLOW_SLIDING(0.800000011920929, .1, -.95, -.99, false, .4125, .09, 0, 0),
        STICKY(-2, 0, 1, -.99, false, .4125, .09, 0, 0);

        public final double knockbackResistance, bounciness, frictionModifier, airDragModifier;
        public final boolean buoyant;
        public final double horizontalPush, verticalPush;

        /** [SULFUR-KB] {@code knockback_modifiers.horizontal_power}(float). */
        public float horizontalPower() { return (float) horizontalPush; }

        /** [SULFUR-KB] {@code knockback_modifiers.vertical_power}(float). */
        public float verticalPower() { return (float) verticalPush; }
        public final int contactDamage, explosionPower;

        Archetype(double knockbackResistance, double bounciness, double frictionModifier,
                double airDragModifier, boolean buoyant, double horizontalPush,
                double verticalPush, int contactDamage, int explosionPower) {
            this.knockbackResistance = knockbackResistance;
            this.bounciness = bounciness;
            this.frictionModifier = frictionModifier;
            this.airDragModifier = airDragModifier;
            this.buoyant = buoyant;
            this.horizontalPush = horizontalPush;
            this.verticalPush = verticalPush;
            this.contactDamage = contactDamage;
            this.explosionPower = explosionPower;
        }
    }

    private static final Set<Integer> REGULAR = Set.of(
            Blocks.DIRT, Blocks.COARSE_DIRT, Blocks.ROOTED_DIRT, Blocks.PODZOL, Blocks.GRASS,
            Blocks.MUD, Blocks.MUDDY_MANGROVE_ROOTS, Blocks.PACKED_MUD, Blocks.COAL_BLOCK,
            Blocks.CLAY, Blocks.BONE_BLOCK);
    private static final Set<Integer> FAST_FLAT = Set.of(
            Blocks.MOSS_BLOCK, Blocks.RESIN_BLOCK, Blocks.HAY_BLOCK, Blocks.PUMPKIN,
            Blocks.SPONGE, Blocks.WET_SPONGE);
    private static final Set<Integer> SLOW_SLIDING = Set.of(
            Blocks.BROWN_MUSHROOM_BLOCK, Blocks.RED_MUSHROOM_BLOCK, Blocks.MUSHROOM_STEM);
    private static final Set<Integer> SLOW_BOUNCY = Set.of(
            Blocks.STONE, Blocks.COBBLE, Blocks.ANDESITE, Blocks.DIORITE, Blocks.GRANITE,
            Blocks.TUFF, Blocks.AMETHYST_BLOCK, Blocks.SULFUR_BLOCK, Blocks.POLISHED_SULFUR,
            Blocks.SULFUR_BRICKS, Blocks.CINNABAR, Blocks.POLISHED_CINNABAR, Blocks.CINNABAR_BRICKS);

    /** Exact tag order is disjoint in the pinned data pack; zero means not swallowable. */
    public static Archetype archetypeFor(int itemId) {
        if (itemId == Blocks.TNT) return Archetype.EXPLOSIVE;
        if (itemId == Blocks.HONEYCOMB_BLOCK) return Archetype.STICKY;
        if (itemId == Blocks.PACKED_ICE || itemId == Blocks.BLUE_ICE
                || itemId == Blocks.SNOW_BLOCK) return Archetype.FAST_SLIDING;
        if (itemId == Blocks.IRON_BLOCK || itemId == Blocks.GOLD_BLOCK
                || itemId == Blocks.RAW_COPPER_BLOCK || itemId == Blocks.RAW_GOLD_BLOCK
                || itemId == Blocks.RAW_IRON_BLOCK || itemId == Blocks.COPPER_BLOCK
                || itemId == Blocks.EXPOSED_COPPER || itemId == Blocks.WEATHERED_COPPER
                || itemId == Blocks.OXIDIZED_COPPER || itemId == Blocks.NETHERITE_BLOCK
                || itemId == Blocks.ANCIENT_DEBRIS) {
            return Archetype.SLOW_FLAT;
        }
        for (int wool : Blocks.WOOL_BY_DYE_COLOR) if (itemId == wool) return Archetype.LIGHT;
        if (REGULAR.contains(itemId)) return Archetype.REGULAR;
        if (FAST_FLAT.contains(itemId)) return Archetype.FAST_FLAT;
        if (SLOW_SLIDING.contains(itemId)) return Archetype.SLOW_SLIDING;
        if (SLOW_BOUNCY.contains(itemId)) return Archetype.SLOW_BOUNCY;
        if (com.gameexpert.engine.BlockFamilies.isWoodLog(itemId)
                || Blocks.isPlankBlock(itemId)) return Archetype.BOUNCY;
        return null;
    }

    public static boolean isFood(int itemId) { return itemId == Blocks.SLIME_BALL; }
}
