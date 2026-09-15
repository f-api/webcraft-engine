package com.gameexpert.terrain.mc.feature;

import com.gameexpert.terrain.Blocks;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Canonical, fail-closed block-state bridge for the dormant 26.3 FEATURES runtime.
 *
 * <p>Instances can only come from the catalog below. The unsigned-16 ID, fluid facts,
 * block-entity capability, tags, and heightmap traits therefore cannot contradict the exact
 * canonical ASCII state. Default states are indexed directly by protocol ID and non-default
 * states are retained only as sparse chunk overrides.</p>
 */
public final class Mc263FeatureBlockState {
    static final int HEIGHTMAP_WORLD_SURFACE = 1;
    static final int HEIGHTMAP_OCEAN_FLOOR = 2;
    static final int HEIGHTMAP_MOTION_BLOCKING = 4;

    private static final String AIR = "minecraft:air";
    private static final String CAVE_AIR = "minecraft:cave_air";
    private static final String VOID_AIR = "minecraft:void_air";
    private static final int CATALOG_SIZE = Blocks.BLOCK_ID_TABLE_CAPACITY;
    /** Sentinel for the unresolved official-fact word; {@code 0} is a legal sturdy mask. */
    private static final int UNRESOLVED_OFFICIAL_FACTS = -1;
    /** Bit of {@link #officialFacts} carrying the pinned {@code POST_FACT_EV7 SOLID} column. */
    private static final int OFFICIAL_SOLID_BIT = 6;
    /** Bit of {@link #officialFacts} carrying the pinned {@code POST_FACT_EV7 AIR} column. */
    private static final int OFFICIAL_AIR_BIT = 7;
    /**
     * {@link OcclusionFace} to {@link Mc263PostprocessResolver.Direction}, in this enum's own
     * order, so the mask bit of a face is its ordinal and no caller can transpose the two.
     */
    private static final Mc263PostprocessResolver.Direction[] OFFICIAL_FACE_DIRECTIONS = {
            Mc263PostprocessResolver.Direction.DOWN, Mc263PostprocessResolver.Direction.UP,
            Mc263PostprocessResolver.Direction.NORTH, Mc263PostprocessResolver.Direction.SOUTH,
            Mc263PostprocessResolver.Direction.WEST, Mc263PostprocessResolver.Direction.EAST};
    private static final Mc263FeatureBlockState[] DEFAULT_BY_ID =
            new Mc263FeatureBlockState[CATALOG_SIZE];
    private static final Map<String, Mc263FeatureBlockState> BY_EXACT;
    private static final Map<String, Mc263FeatureBlockState> BY_SORTED_IDENTITY;
    private static final Map<String, Mc263FeatureBlockState> BY_BLOCK_KEY;
    /**
     * Spelling-keyed memos over the two pure catalog resolutions, and the bound derived from the
     * authenticated tables they index rather than a constant written beside them (AGENTS 10m).
     *
     * <p>AGENTS rule 10l: the FEATURES hot path resolves one exact-state spelling per placed
     * cell, and {@link #canonicalize(String)} is a pure function of that spelling — a syntax
     * scan, one or two catalog lookups, and, for a spelling the catalog does not index verbatim,
     * a property split, sort and re-join whose result never changes. The memo hands back exactly
     * the value the first computation produced, so the answer, and therefore every published
     * byte, is identical; only rejections are never stored, so an unsupported spelling is
     * rejected identically every time. The bound is the count of spellings the two authenticated
     * indexes already name, and at the bound the memo stops growing and every caller simply pays
     * the original computation.</p>
     */
    private static final Map<String, String> CANONICAL_SPELLINGS = new ConcurrentHashMap<>();
    private static final Map<String, Mc263FeatureBlockState> RESOLVED_SPELLINGS =
            new ConcurrentHashMap<>();
    private static final int SPELLING_MEMO_BOUND;
    private static final Set<String> VILLAGE_G3J15_ADDITIONS = new HashSet<>();
    private static final Set<String> STATE_CLOSE_ADDITIONS = new HashSet<>();
    private static final Set<String> POST_STATE_CLOSE_ADDITIONS = new HashSet<>();
    private static final Set<String> SAPLING_STAGE_ADDITIONS = new HashSet<>();
    private static final Set<String> SCULK_PATCH_CLOSURE_ADDITIONS = new HashSet<>();
    private static final Set<String> RUINED_PORTAL_CLOSURE_ADDITIONS = new HashSet<>();
    private static final Set<String> AUTHENTICATED_PRODUCTION_CLOSURE = new HashSet<>();
    private static final Set<String> VILLAGE_PILE_ADDITIONS = new HashSet<>();
    /**
     * Exhaustive authenticated Village pile provider output states, in provider-receipt order:
     * {@code pile_hay}, {@code pile_melon}, {@code pile_snow}, {@code pile_ice},
     * {@code pile_pumpkin} of {@code village-production-authority-v1.txt}.
     */
    private static final List<String> VILLAGE_PILE_PROVIDER_STATES = List.of(
            "minecraft:hay_block[axis=y]",
            "minecraft:hay_block[axis=z]",
            "minecraft:hay_block[axis=x]",
            "minecraft:melon",
            "minecraft:snow[layers=1]",
            "minecraft:blue_ice",
            "minecraft:packed_ice",
            "minecraft:pumpkin",
            "minecraft:jack_o_lantern[facing=north]");
    /**
     * Released protocol IDs for the block keys the authenticated Village pile providers write.
     * Block IDs are an append-only contract, so the binding is explicit and every value is an
     * existing {@link Blocks} code — the batch fails closed on any key absent here.
     */
    private static final Map<String, Integer> VILLAGE_PILE_BLOCK_IDS = Map.of(
            "minecraft:hay_block", Blocks.HAY_BLOCK,
            "minecraft:melon", Blocks.MELON,
            "minecraft:snow", Blocks.SNOW,
            "minecraft:blue_ice", Blocks.BLUE_ICE,
            "minecraft:packed_ice", Blocks.PACKED_ICE,
            "minecraft:pumpkin", Blocks.PUMPKIN,
            "minecraft:jack_o_lantern", Blocks.JACK_O_LANTERN);

    private static final Set<String> FEATURES_CANNOT_REPLACE = Set.of(
            "minecraft:bedrock", "minecraft:spawner", "minecraft:chest",
            "minecraft:end_portal_frame", "minecraft:reinforced_deepslate",
            "minecraft:trial_spawner", "minecraft:vault");
    private static final Set<String> LOGS = Set.of(
            "minecraft:oak_log", "minecraft:birch_log", "minecraft:spruce_log",
            "minecraft:jungle_log", "minecraft:acacia_log", "minecraft:dark_oak_log",
            "minecraft:mangrove_log", "minecraft:cherry_log", "minecraft:pale_oak_log",
            "minecraft:poplar_log",
            "minecraft:stripped_oak_log", "minecraft:stripped_birch_log",
            "minecraft:stripped_spruce_log", "minecraft:stripped_jungle_log",
            "minecraft:stripped_acacia_log", "minecraft:stripped_dark_oak_log",
            "minecraft:stripped_mangrove_log", "minecraft:stripped_cherry_log",
            "minecraft:stripped_pale_oak_log", "minecraft:stripped_poplar_log",
            "minecraft:poplar_wood", "minecraft:stripped_poplar_wood");
    private static final Set<String> LEAVES = Set.of(
            "minecraft:oak_leaves", "minecraft:birch_leaves", "minecraft:spruce_leaves",
            "minecraft:jungle_leaves", "minecraft:acacia_leaves", "minecraft:dark_oak_leaves",
            "minecraft:mangrove_leaves", "minecraft:cherry_leaves",
            "minecraft:pale_oak_leaves", "minecraft:red_poplar_leaves",
            "minecraft:orange_poplar_leaves", "minecraft:yellow_poplar_leaves",
            "minecraft:azalea_leaves", "minecraft:flowering_azalea_leaves");
    /** Exact representable intersection of the pinned {@code replaceable_by_trees} tag. */
    private static final Set<String> REPLACEABLE_BY_TREES = Set.of(
            "minecraft:dandelion", "minecraft:poppy", "minecraft:blue_orchid",
            "minecraft:allium", "minecraft:azure_bluet", "minecraft:red_tulip",
            "minecraft:orange_tulip", "minecraft:white_tulip", "minecraft:pink_tulip",
            "minecraft:oxeye_daisy", "minecraft:cornflower",
            "minecraft:lily_of_the_valley", "minecraft:closed_eyeblossom",
            "minecraft:pale_moss_carpet", "minecraft:short_grass", "minecraft:fern",
            "minecraft:dead_bush", "minecraft:vine", "minecraft:glow_lichen",
            "minecraft:sunflower", "minecraft:lilac", "minecraft:rose_bush",
            "minecraft:peony", "minecraft:tall_grass", "minecraft:large_fern",
            "minecraft:hanging_roots", "minecraft:water", "minecraft:seagrass",
            "minecraft:tall_seagrass", "minecraft:bush", "minecraft:firefly_bush",
            "minecraft:leaf_litter", "minecraft:short_dry_grass",
            "minecraft:tall_dry_grass");
    private static final Set<String> CANNOT_REPLACE_BELOW_TREE_TRUNK = Set.of(
            "minecraft:dirt", "minecraft:coarse_dirt", "minecraft:rooted_dirt",
            "minecraft:mud", "minecraft:muddy_mangrove_roots", "minecraft:moss_block",
            "minecraft:pale_moss_block", "minecraft:podzol");
    private static final Set<String> SUBSTRATE_OVERWORLD = Set.of(
            "minecraft:dirt", "minecraft:coarse_dirt", "minecraft:rooted_dirt",
            "minecraft:mud", "minecraft:muddy_mangrove_roots", "minecraft:moss_block",
            "minecraft:pale_moss_block", "minecraft:grass_block", "minecraft:podzol",
            "minecraft:mycelium");
    private static final Set<String> MANGROVE_ROOTS_GROW_THROUGH = Set.of(
            "minecraft:mud", "minecraft:muddy_mangrove_roots", "minecraft:mangrove_roots",
            "minecraft:moss_carpet", "minecraft:vine", "minecraft:mangrove_propagule",
            "minecraft:snow");
    private static final Set<String> MANGROVE_LOGS_GROW_THROUGH = Set.of(
            "minecraft:mud", "minecraft:muddy_mangrove_roots", "minecraft:mangrove_roots",
            "minecraft:mangrove_leaves", "minecraft:mangrove_log",
            "minecraft:mangrove_propagule", "minecraft:moss_carpet", "minecraft:vine");
    private static final Set<String> REPLACEABLE_BY_MUSHROOMS = Set.of(
            "minecraft:poppy", "minecraft:dandelion", "minecraft:blue_orchid",
            "minecraft:allium", "minecraft:azure_bluet", "minecraft:red_tulip",
            "minecraft:orange_tulip", "minecraft:white_tulip", "minecraft:pink_tulip",
            "minecraft:oxeye_daisy", "minecraft:cornflower",
            "minecraft:closed_eyeblossom", "minecraft:short_grass", "minecraft:fern",
            "minecraft:dead_bush", "minecraft:vine", "minecraft:glow_lichen",
            "minecraft:sunflower", "minecraft:tall_grass", "minecraft:large_fern",
            "minecraft:water", "minecraft:seagrass", "minecraft:tall_seagrass",
            "minecraft:brown_mushroom", "minecraft:red_mushroom",
            "minecraft:brown_mushroom_block", "minecraft:red_mushroom_block",
            "minecraft:leaf_litter", "minecraft:short_dry_grass",
            "minecraft:tall_dry_grass", "minecraft:bush", "minecraft:red_shrub",
            "minecraft:firefly_bush", "minecraft:lilac", "minecraft:rose_bush",
            "minecraft:peony", "minecraft:lily_of_the_valley");
    private static final Set<String> HUGE_MUSHROOM_SUBSTRATES = Set.of(
            "minecraft:dirt", "minecraft:coarse_dirt", "minecraft:rooted_dirt",
            "minecraft:mud", "minecraft:muddy_mangrove_roots", "minecraft:moss_block",
            "minecraft:pale_moss_block", "minecraft:grass_block", "minecraft:mycelium",
            "minecraft:podzol");
    private static final Set<String> GEODE_INVALID = Set.of(
            "minecraft:bedrock", "minecraft:water", "minecraft:lava", "minecraft:ice",
            "minecraft:packed_ice", "minecraft:blue_ice");
    /** Current-catalog intersection of the exact transitive pinned tag closure. */
    private static final Set<String> AZALEA_ROOT_REPLACEABLE = Set.of(
            "minecraft:andesite", "minecraft:brown_terracotta", "minecraft:clay",
            "minecraft:coarse_dirt", "minecraft:deepslate", "minecraft:diorite",
            "minecraft:dirt", "minecraft:granite", "minecraft:grass_block",
            "minecraft:gravel", "minecraft:light_gray_terracotta", "minecraft:moss_block",
            "minecraft:mud", "minecraft:muddy_mangrove_roots", "minecraft:mycelium",
            "minecraft:orange_terracotta", "minecraft:pale_moss_block", "minecraft:podzol",
            "minecraft:powder_snow", "minecraft:red_sand", "minecraft:red_terracotta",
            "minecraft:rooted_dirt", "minecraft:sand", "minecraft:snow_block",
            "minecraft:stone", "minecraft:terracotta", "minecraft:tuff",
            "minecraft:white_terracotta", "minecraft:yellow_terracotta");
    private static final Set<String> DIRECTIONS = Set.of(
            "down", "up", "north", "south", "west", "east");
    /** Exact current-catalog intersection of the pinned sculk-replaceable tag closure. */
    private static final Set<String> SCULK_REPLACEABLE = Set.of(
            "minecraft:stone", "minecraft:granite", "minecraft:diorite",
            "minecraft:andesite", "minecraft:tuff", "minecraft:deepslate",
            "minecraft:dirt", "minecraft:coarse_dirt", "minecraft:rooted_dirt",
            "minecraft:mud", "minecraft:muddy_mangrove_roots", "minecraft:moss_block",
            "minecraft:pale_moss_block", "minecraft:grass_block", "minecraft:podzol",
            "minecraft:mycelium", "minecraft:terracotta", "minecraft:white_terracotta",
            "minecraft:orange_terracotta", "minecraft:yellow_terracotta",
            "minecraft:brown_terracotta", "minecraft:red_terracotta",
            "minecraft:light_gray_terracotta", "minecraft:sand", "minecraft:red_sand",
            "minecraft:gravel", "minecraft:calcite", "minecraft:smooth_basalt",
            "minecraft:clay", "minecraft:dripstone_block", "minecraft:red_sandstone",
            "minecraft:sandstone", "minecraft:sulfur", "minecraft:cinnabar");
    /** Current-catalog intersection of the exact transitive pinned heightmap tag closure. */
    private static final Set<String> BLOCKS_MOTION_NO_LEAVES = Set.of(
            "minecraft:amethyst_block", "minecraft:amethyst_cluster", "minecraft:andesite",
            "minecraft:barrier",
            "minecraft:bedrock", "minecraft:bee_nest", "minecraft:birch_log", "minecraft:blue_ice",
            "minecraft:blue_terracotta", "minecraft:bone_block", "minecraft:brown_terracotta",
            "minecraft:budding_amethyst", "minecraft:cactus", "minecraft:calcite",
            "minecraft:chest", "minecraft:chiseled_sandstone",
            "minecraft:chiseled_stone_bricks", "minecraft:cinnabar",
            "minecraft:clay",
            "minecraft:coal_ore", "minecraft:coarse_dirt", "minecraft:cobbled_deepslate",
            "minecraft:cobblestone", "minecraft:cobblestone_stairs",
            "minecraft:mossy_cobblestone_stairs", "minecraft:copper_ore",
            "minecraft:cut_sandstone", "minecraft:deepslate",
            "minecraft:deepslate_coal_ore", "minecraft:deepslate_copper_ore", "minecraft:dispenser",
            "minecraft:deepslate_diamond_ore", "minecraft:deepslate_emerald_ore",
            "minecraft:deepslate_gold_ore", "minecraft:deepslate_iron_ore",
            "minecraft:deepslate_lapis_ore", "minecraft:deepslate_redstone_ore",
            "minecraft:creaking_heart", "minecraft:dark_oak_log", "minecraft:diamond_ore",
            "minecraft:diorite", "minecraft:dirt",
            "minecraft:dripstone_block", "minecraft:emerald_ore", "minecraft:farmland",
            "minecraft:gold_block", "minecraft:gold_ore", "minecraft:granite", "minecraft:grass_block",
            "minecraft:gravel", "minecraft:ice", "minecraft:infested_deepslate",
            "minecraft:infested_stone", "minecraft:iron_ore", "minecraft:jungle_log",
            "minecraft:lapis_ore",
            "minecraft:large_amethyst_bud", "minecraft:light_gray_terracotta",
            "minecraft:magma_block", "minecraft:medium_amethyst_bud", "minecraft:melon",
            "minecraft:brown_mushroom_block", "minecraft:red_mushroom_block",
            "minecraft:mushroom_stem",
            "minecraft:moss_block", "minecraft:mossy_cobblestone", "minecraft:mud",
            "minecraft:mangrove_log", "minecraft:mangrove_roots",
            "minecraft:muddy_mangrove_roots", "minecraft:mycelium", "minecraft:oak_log",
            "minecraft:oak_planks", "minecraft:spruce_planks", "minecraft:orange_terracotta",
            "minecraft:packed_ice",
            "minecraft:pale_moss_block", "minecraft:pale_oak_log", "minecraft:podzol",
            "minecraft:pointed_dripstone", "minecraft:poplar_log",
            "minecraft:potent_sulfur", "minecraft:prismarine", "minecraft:prismarine_bricks",
            "minecraft:dark_prismarine", "minecraft:sea_lantern",
            "minecraft:pumpkin",
            "minecraft:raw_copper_block", "minecraft:raw_iron_block",
            "minecraft:red_sand",
            "minecraft:red_sandstone", "minecraft:red_terracotta",
            "minecraft:redstone_ore", "minecraft:rooted_dirt", "minecraft:sand",
            "minecraft:sandstone", "minecraft:sandstone_slab", "minecraft:sandstone_stairs",
            "minecraft:sculk",
            "minecraft:sculk_catalyst", "minecraft:sculk_sensor",
            "minecraft:sculk_shrieker", "minecraft:sculk_vein",
            "minecraft:small_amethyst_bud", "minecraft:smooth_basalt",
            "minecraft:snow_block", "minecraft:spawner", "minecraft:spruce_log", "minecraft:sticky_piston",
            "minecraft:stone", "minecraft:stone_pressure_plate", "minecraft:acacia_log",
            "minecraft:cherry_log",
            "minecraft:stripped_oak_log", "minecraft:stripped_birch_log",
            "minecraft:stripped_spruce_log", "minecraft:stripped_jungle_log",
            "minecraft:stripped_acacia_log", "minecraft:stripped_dark_oak_log",
            "minecraft:stripped_cherry_log", "minecraft:stripped_mangrove_log",
            "minecraft:stripped_pale_oak_log", "minecraft:stripped_poplar_log",
            "minecraft:poplar_wood", "minecraft:stripped_poplar_wood",
            "minecraft:sulfur", "minecraft:sulfur_spike", "minecraft:suspicious_sand",
            "minecraft:tnt", "minecraft:wet_sponge",
            "minecraft:terracotta", "minecraft:tuff", "minecraft:white_terracotta",
            "minecraft:yellow_terracotta", "minecraft:bookshelf",
            "minecraft:end_portal_frame");

    /** Exact unsupported P2N successful-write/final-cell union recovered by MAN-P2P. */
    private static final List<CatalogEntry> MANSION_P2P_EXACT_STATES = List.of(
            entry(Blocks.BIRCH_PLANK, "minecraft:birch_planks"),
            entry(Blocks.BIRCH_STAIRS, "minecraft:birch_stairs[facing=east,half=bottom,shape=inner_left,waterlogged=false]"),
            entry(Blocks.BIRCH_STAIRS, "minecraft:birch_stairs[facing=east,half=bottom,shape=inner_right,waterlogged=false]"),
            entry(Blocks.BIRCH_STAIRS, "minecraft:birch_stairs[facing=east,half=bottom,shape=straight,waterlogged=false]"),
            entry(Blocks.BIRCH_STAIRS, "minecraft:birch_stairs[facing=north,half=bottom,shape=inner_right,waterlogged=false]"),
            entry(Blocks.BIRCH_STAIRS, "minecraft:birch_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]"),
            entry(Blocks.BLACK_CARPET, "minecraft:black_carpet"),
            entry(Blocks.BLACK_WOOL, "minecraft:black_wool"),
            entry(Blocks.BROWN_CARPET, "minecraft:brown_carpet"),
            entry(Blocks.CHEST, "minecraft:chest[facing=south,type=left,waterlogged=false]"),
            entry(Blocks.CHEST, "minecraft:chest[facing=south,type=right,waterlogged=false]"),
            entry(Blocks.COBBLE_SLAB, "minecraft:cobblestone_slab[type=bottom,waterlogged=false]"),
            entry(Blocks.COBBLE_WALL, "minecraft:cobblestone_wall[east=low,north=low,south=low,up=true,waterlogged=false,west=none]"),
            entry(Blocks.COBBLE_WALL, "minecraft:cobblestone_wall[east=low,north=low,south=none,up=true,waterlogged=false,west=low]"),
            entry(Blocks.COBBLE_WALL, "minecraft:cobblestone_wall[east=low,north=none,south=none,up=true,waterlogged=false,west=low]"),
            entry(Blocks.COBBLE_WALL, "minecraft:cobblestone_wall[east=none,north=low,south=low,up=true,waterlogged=false,west=low]"),
            entry(Blocks.COBBLE_WALL, "minecraft:cobblestone_wall[east=none,north=low,south=low,up=true,waterlogged=false,west=none]"),
            entry(Blocks.COBBLE_WALL, "minecraft:cobblestone_wall[east=none,north=none,south=none,up=true,waterlogged=false,west=none]"),
            entry(Blocks.COBBLE_WALL, "minecraft:cobblestone_wall[east=none,north=none,south=none,up=true,waterlogged=false,west=tall]"),
            entry(Blocks.COBBLE_WALL, "minecraft:cobblestone_wall[east=none,north=none,south=tall,up=true,waterlogged=false,west=tall]"),
            entry(Blocks.CYAN_CARPET, "minecraft:cyan_carpet"),
            entry(Blocks.DARK_OAK_STAIRS, "minecraft:dark_oak_stairs[facing=east,half=bottom,shape=outer_left,waterlogged=false]"),
            entry(Blocks.DARK_OAK_STAIRS, "minecraft:dark_oak_stairs[facing=east,half=bottom,shape=straight,waterlogged=false]"),
            entry(Blocks.DARK_OAK_STAIRS, "minecraft:dark_oak_stairs[facing=east,half=top,shape=inner_right,waterlogged=false]"),
            entry(Blocks.DARK_OAK_STAIRS, "minecraft:dark_oak_stairs[facing=east,half=top,shape=straight,waterlogged=false]"),
            entry(Blocks.DARK_OAK_STAIRS, "minecraft:dark_oak_stairs[facing=north,half=bottom,shape=inner_right,waterlogged=false]"),
            entry(Blocks.DARK_OAK_STAIRS, "minecraft:dark_oak_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]"),
            entry(Blocks.DARK_OAK_STAIRS, "minecraft:dark_oak_stairs[facing=north,half=top,shape=straight,waterlogged=false]"),
            entry(Blocks.DARK_OAK_STAIRS, "minecraft:dark_oak_stairs[facing=south,half=bottom,shape=straight,waterlogged=false]"),
            entry(Blocks.DARK_OAK_STAIRS, "minecraft:dark_oak_stairs[facing=south,half=top,shape=inner_left,waterlogged=false]"),
            entry(Blocks.DARK_OAK_STAIRS, "minecraft:dark_oak_stairs[facing=south,half=top,shape=straight,waterlogged=false]"),
            entry(Blocks.DARK_OAK_STAIRS, "minecraft:dark_oak_stairs[facing=west,half=bottom,shape=straight,waterlogged=false]"),
            entry(Blocks.DARK_OAK_STAIRS, "minecraft:dark_oak_stairs[facing=west,half=top,shape=inner_left,waterlogged=false]"),
            entry(Blocks.DARK_OAK_STAIRS, "minecraft:dark_oak_stairs[facing=west,half=top,shape=straight,waterlogged=false]"),
            entry(Blocks.GLASS_PANE, "minecraft:glass_pane[east=false,north=true,south=true,waterlogged=false,west=false]"),
            entry(Blocks.GLASS_PANE, "minecraft:glass_pane[east=true,north=false,south=false,waterlogged=false,west=true]"),
            entry(Blocks.GRAY_CARPET, "minecraft:gray_carpet"),
            entry(Blocks.GRAY_WOOL, "minecraft:gray_wool"),
            entry(Blocks.GREEN_WOOL, "minecraft:green_wool"),
            entry(Blocks.LIGHT_BLUE_WOOL, "minecraft:light_blue_wool"),
            entry(Blocks.LIGHT_GRAY_WOOL, "minecraft:light_gray_wool"),
            entry(Blocks.OAK_SLAB, "minecraft:oak_slab[type=top,waterlogged=false]"),
            entry(Blocks.WOOD_STAIRS, "minecraft:oak_stairs[facing=east,half=bottom,shape=straight,waterlogged=false]"),
            entry(Blocks.WOOD_STAIRS, "minecraft:oak_stairs[facing=east,half=top,shape=straight,waterlogged=false]"),
            entry(Blocks.WOOD_STAIRS, "minecraft:oak_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]"),
            entry(Blocks.WOOD_STAIRS, "minecraft:oak_stairs[facing=north,half=top,shape=outer_right,waterlogged=false]"),
            entry(Blocks.WOOD_STAIRS, "minecraft:oak_stairs[facing=north,half=top,shape=straight,waterlogged=false]"),
            entry(Blocks.WOOD_STAIRS, "minecraft:oak_stairs[facing=south,half=top,shape=outer_left,waterlogged=false]"),
            entry(Blocks.WOOD_STAIRS, "minecraft:oak_stairs[facing=south,half=top,shape=outer_right,waterlogged=false]"),
            entry(Blocks.WOOD_STAIRS, "minecraft:oak_stairs[facing=south,half=top,shape=straight,waterlogged=false]"),
            entry(Blocks.WOOD_STAIRS, "minecraft:oak_stairs[facing=west,half=top,shape=outer_right,waterlogged=false]"),
            entry(Blocks.WOOD_STAIRS, "minecraft:oak_stairs[facing=west,half=top,shape=straight,waterlogged=false]"),
            entry(Blocks.POTTED_RED_TULIP, "minecraft:potted_red_tulip"),
            entry(Blocks.REDSTONE_WIRE, "minecraft:redstone_wire[east=side,north=up,power=0,south=none,west=none]"),
            entry(Blocks.WHITE_WOOL, "minecraft:white_wool"));

    static {
        Map<String, Mc263FeatureBlockState> states = new HashMap<>();
        registerDefault(states, Blocks.AIR, AIR, false, false,
                FluidKind.NONE, 0, Capability.NONE);
        registerAlias(states, Blocks.AIR, CAVE_AIR, false, false,
                FluidKind.NONE, 0, Capability.NONE);
        registerAlias(states, Blocks.AIR, VOID_AIR, false, false,
                FluidKind.NONE, 0, Capability.NONE);

        registerSolidDefaults(states,
                entry(Blocks.STONE, "minecraft:stone"),
                entry(Blocks.DIRT, "minecraft:dirt"),
                entry(Blocks.SAND, "minecraft:sand"),
                entry(Blocks.PLANK, "minecraft:oak_planks"),
                entry(Blocks.SPRUCE_PLANK, "minecraft:spruce_planks"),
                entry(Blocks.COBBLE, "minecraft:cobblestone"),
                entry(Blocks.STONE_BRICK, "minecraft:stone_bricks"),
                entry(Blocks.BEDROCK, "minecraft:bedrock"),
                entry(Blocks.GRAVEL, "minecraft:gravel"),
                entry(Blocks.COAL_ORE, "minecraft:coal_ore"),
                entry(Blocks.IRON_ORE, "minecraft:iron_ore"),
                entry(Blocks.GOLD_ORE, "minecraft:gold_ore"),
                entry(Blocks.GOLD_BLOCK, "minecraft:gold_block"),
                entry(Blocks.DIAMOND_ORE, "minecraft:diamond_ore"),
                entry(Blocks.ICE, "minecraft:ice"),
                entry(Blocks.MOSS_BLOCK, "minecraft:moss_block"),
                entry(Blocks.MOSSY_COBBLE, "minecraft:mossy_cobblestone"),
                entry(Blocks.CLAY, "minecraft:clay"),
                entry(Blocks.CALCITE, "minecraft:calcite"),
                entry(Blocks.MUD, "minecraft:mud"),
                entry(Blocks.ROOTED_DIRT, "minecraft:rooted_dirt"),
                entry(Blocks.PACKED_ICE, "minecraft:packed_ice"),
                entry(Blocks.BLUE_ICE, "minecraft:blue_ice"),
                entry(Blocks.EMERALD_ORE, "minecraft:emerald_ore"),
                entry(Blocks.LAPIS_ORE, "minecraft:lapis_ore"),
                entry(Blocks.REDSTONE_ORE, "minecraft:redstone_ore"),
                entry(Blocks.SNOW_BLOCK, "minecraft:snow_block"),
                entry(Blocks.COARSE_DIRT, "minecraft:coarse_dirt"),
                entry(Blocks.DEEPSLATE, "minecraft:deepslate"),
                entry(Blocks.GRANITE, "minecraft:granite"),
                entry(Blocks.DIORITE, "minecraft:diorite"),
                entry(Blocks.ANDESITE, "minecraft:andesite"),
                entry(Blocks.TUFF, "minecraft:tuff"),
                entry(Blocks.COPPER_ORE, "minecraft:copper_ore"),
                // OreVeinifier raw-ore lane: overworld noise settings `ore_veins` names
                // raw_copper_block/raw_iron_block, so both reach the post-CARVERS cut.
                entry(Blocks.RAW_COPPER_BLOCK, "minecraft:raw_copper_block"),
                entry(Blocks.RAW_IRON_BLOCK, "minecraft:raw_iron_block"),
                entry(Blocks.DEEPSLATE_COAL_ORE, "minecraft:deepslate_coal_ore"),
                entry(Blocks.DEEPSLATE_IRON_ORE, "minecraft:deepslate_iron_ore"),
                entry(Blocks.DEEPSLATE_COPPER_ORE, "minecraft:deepslate_copper_ore"),
                entry(Blocks.DEEPSLATE_GOLD_ORE, "minecraft:deepslate_gold_ore"),
                entry(Blocks.DEEPSLATE_REDSTONE_ORE, "minecraft:deepslate_redstone_ore"),
                entry(Blocks.DEEPSLATE_EMERALD_ORE, "minecraft:deepslate_emerald_ore"),
                entry(Blocks.DEEPSLATE_LAPIS_ORE, "minecraft:deepslate_lapis_ore"),
                entry(Blocks.DEEPSLATE_DIAMOND_ORE, "minecraft:deepslate_diamond_ore"),
                entry(Blocks.DRIPSTONE_BLOCK, "minecraft:dripstone_block"),
                entry(Blocks.PALE_MOSS_BLOCK, "minecraft:pale_moss_block"),
                entry(Blocks.INFESTED_STONE, "minecraft:infested_stone"),
                entry(Blocks.INFESTED_DEEPSLATE, "minecraft:infested_deepslate"),
                entry(Blocks.CHISELED_STONE_BRICKS, "minecraft:chiseled_stone_bricks"),
                entry(Blocks.MAGMA, "minecraft:magma_block"),
                entry(Blocks.NETHERRACK, "minecraft:netherrack"),
                entry(Blocks.WET_SPONGE, "minecraft:wet_sponge"),
                entry(Blocks.PRISMARINE, "minecraft:prismarine"),
                entry(Blocks.PRISMARINE_BRICKS, "minecraft:prismarine_bricks"),
                entry(Blocks.DARK_PRISMARINE, "minecraft:dark_prismarine"),
                entry(Blocks.SEA_LANTERN, "minecraft:sea_lantern"),

                // Every currently reachable CARVERS default.
                entry(Blocks.SANDSTONE, "minecraft:sandstone"),
                entry(Blocks.ORANGE_TERRACOTTA, "minecraft:orange_terracotta"),
                entry(Blocks.RED_SAND, "minecraft:red_sand"),
                entry(Blocks.TERRACOTTA, "minecraft:terracotta"),
                entry(Blocks.WHITE_TERRACOTTA, "minecraft:white_terracotta"),
                entry(Blocks.RED_SANDSTONE, "minecraft:red_sandstone"),
                entry(Blocks.YELLOW_TERRACOTTA, "minecraft:yellow_terracotta"),
                entry(Blocks.BROWN_TERRACOTTA, "minecraft:brown_terracotta"),
                entry(Blocks.RED_TERRACOTTA, "minecraft:red_terracotta"),
                entry(Blocks.LIGHT_GRAY_TERRACOTTA, "minecraft:light_gray_terracotta"),
                entry(Blocks.COBBLED_DEEPSLATE, "minecraft:cobbled_deepslate"),
                entry(Blocks.SULFUR_BLOCK, "minecraft:sulfur"),
                entry(Blocks.CINNABAR, "minecraft:cinnabar"),

                // Exact geode carrier defaults.
                entry(Blocks.SMOOTH_BASALT, "minecraft:smooth_basalt"),
                entry(Blocks.AMETHYST_BLOCK, "minecraft:amethyst_block"),
                entry(Blocks.BUDDING_AMETHYST, "minecraft:budding_amethyst"),
                entry(Blocks.DARK_OAK_PLANK, "minecraft:dark_oak_planks"));

        registerDefault(states, Blocks.COBWEB, "minecraft:cobweb", false, false,
                FluidKind.NONE, 0, Capability.NONE);
        registerFenceStates(states, Blocks.WOOD_FENCE, "minecraft:oak_fence");
        registerFenceStates(states, Blocks.DARK_OAK_FENCE, "minecraft:dark_oak_fence");
        registerStairStates(states, Blocks.COBBLE_STAIRS, "minecraft:cobblestone_stairs");
        registerStairStates(states, Blocks.MOSSY_COBBLE_STAIRS,
                "minecraft:mossy_cobblestone_stairs");
        String[] wallTorchFacings = {"north", "east", "south", "west"};
        // Vanilla facing points away from the supporting wall; engine IDs name that wall.
        int[] wallTorchIds = {Blocks.WALL_TORCH_S, Blocks.WALL_TORCH_W,
                Blocks.WALL_TORCH_N, Blocks.WALL_TORCH_E};
        for (int index = 0; index < wallTorchFacings.length; index++) {
            registerDefault(states, wallTorchIds[index],
                    "minecraft:wall_torch[facing=" + wallTorchFacings[index] + "]",
                    false, false, FluidKind.NONE, 0, Capability.NONE);
        }
        registerDefault(states, Blocks.RAIL,
                "minecraft:rail[shape=north_south,waterlogged=false]", false, false,
                FluidKind.NONE, 0, Capability.NONE);
        registerAlias(states, Blocks.RAIL,
                "minecraft:rail[shape=east_west,waterlogged=false]", false, false,
                FluidKind.NONE, 0, Capability.NONE);
        for (String shape : List.of("ascending_east", "ascending_west", "ascending_north",
                "ascending_south", "south_east", "south_west", "north_west",
                "north_east")) {
            registerAlias(states, Blocks.RAIL,
                    "minecraft:rail[shape=" + shape + ",waterlogged=false]", false, false,
                    FluidKind.NONE, 0, Capability.NONE);
        }
        registerDefault(states, Blocks.CHAIN, "minecraft:chain[axis=y]", false, false,
                FluidKind.NONE, 0, Capability.NONE);
        registerDefault(states, Blocks.CHAIN_X, "minecraft:chain[axis=x]", false, false,
                FluidKind.NONE, 0, Capability.NONE);
        registerDefault(states, Blocks.CHAIN_Z, "minecraft:chain[axis=z]", false, false,
                FluidKind.NONE, 0, Capability.NONE);

        for (CatalogEntry snowy : List.of(
                entry(Blocks.GRASS, "minecraft:grass_block"),
                entry(Blocks.PODZOL, "minecraft:podzol"),
                entry(Blocks.MYCELIUM, "minecraft:mycelium"))) {
            for (boolean value : new boolean[]{false, true}) {
                String exact = snowy.exact() + "[snowy=" + value + "]";
                Mc263FeatureBlockState state = state(snowy.id(), exact, true, true,
                        FluidKind.NONE, 0, Capability.NONE);
                states.put(exact, state);
                if (!value) registerDefaultOnly(snowy.id(), state);
            }
        }

        registerDefault(states, Blocks.SANDSTONE_SLAB,
                "minecraft:sandstone_slab[type=bottom,waterlogged=false]", true, false,
                FluidKind.NONE, 0, Capability.NONE);
        registerDefault(states, Blocks.SUSPICIOUS_SAND,
                "minecraft:suspicious_sand[dusted=0]", true, true,
                FluidKind.NONE, 0, Capability.BRUSHABLE);
        registerDefault(states, Blocks.SUSPICIOUS_GRAVEL,
                "minecraft:suspicious_gravel[dusted=0]", true, true,
                FluidKind.NONE, 0, Capability.BRUSHABLE);

        for (String axis : new String[]{"x", "y", "z"}) {
            String exact = "minecraft:bone_block[axis=" + axis + "]";
            Mc263FeatureBlockState bone = state(Blocks.BONE_BLOCK, exact, true, true,
                    FluidKind.NONE, 0, Capability.NONE);
            states.put(exact, bone);
            if (axis.equals("y")) registerDefaultOnly(Blocks.BONE_BLOCK, bone);
        }

        registerTreeLog(states, "minecraft:oak_log", Blocks.LOG, Blocks.LOG_X, Blocks.LOG_Z);
        registerTreeLog(states, "minecraft:birch_log", Blocks.BIRCH_LOG,
                Blocks.BIRCH_LOG_X, Blocks.BIRCH_LOG_Z);
        registerTreeLog(states, "minecraft:spruce_log", Blocks.SPRUCE_LOG);
        registerTreeLog(states, "minecraft:jungle_log", Blocks.JUNGLE_LOG);
        registerTreeLog(states, "minecraft:acacia_log", Blocks.ACACIA_LOG);
        registerTreeLog(states, "minecraft:dark_oak_log", Blocks.DARK_OAK_LOG);
        registerTreeLog(states, "minecraft:cherry_log", Blocks.CHERRY_LOG);
        registerTreeLog(states, "minecraft:mangrove_log", Blocks.MANGROVE_LOG);
        registerTreeLog(states, "minecraft:pale_oak_log", Blocks.PALE_OAK_LOG,
                Blocks.PALE_OAK_LOG_X, Blocks.PALE_OAK_LOG_Z);
        registerTreeLog(states, "minecraft:poplar_log", Blocks.POPLAR_LOG,
                Blocks.POPLAR_LOG_X, Blocks.POPLAR_LOG_Z);
        registerTreeLog(states, "minecraft:stripped_oak_log", Blocks.STRIPPED_OAK_LOG,
                Blocks.STRIPPED_OAK_LOG_X, Blocks.STRIPPED_OAK_LOG_Z);
        registerTreeLog(states, "minecraft:stripped_birch_log", Blocks.STRIPPED_BIRCH_LOG,
                Blocks.STRIPPED_BIRCH_LOG_X, Blocks.STRIPPED_BIRCH_LOG_Z);
        registerTreeLog(states, "minecraft:stripped_spruce_log", Blocks.STRIPPED_SPRUCE_LOG,
                Blocks.STRIPPED_SPRUCE_LOG_X, Blocks.STRIPPED_SPRUCE_LOG_Z);
        registerTreeLog(states, "minecraft:stripped_jungle_log", Blocks.STRIPPED_JUNGLE_LOG,
                Blocks.STRIPPED_JUNGLE_LOG_X, Blocks.STRIPPED_JUNGLE_LOG_Z);
        registerTreeLog(states, "minecraft:stripped_acacia_log", Blocks.STRIPPED_ACACIA_LOG,
                Blocks.STRIPPED_ACACIA_LOG_X, Blocks.STRIPPED_ACACIA_LOG_Z);
        registerTreeLog(states, "minecraft:stripped_dark_oak_log",
                Blocks.STRIPPED_DARK_OAK_LOG,
                Blocks.STRIPPED_DARK_OAK_LOG_X, Blocks.STRIPPED_DARK_OAK_LOG_Z);
        registerTreeLog(states, "minecraft:stripped_cherry_log", Blocks.STRIPPED_CHERRY_LOG,
                Blocks.STRIPPED_CHERRY_LOG_X, Blocks.STRIPPED_CHERRY_LOG_Z);
        registerTreeLog(states, "minecraft:stripped_mangrove_log", Blocks.STRIPPED_MANGROVE_LOG,
                Blocks.STRIPPED_MANGROVE_LOG_X, Blocks.STRIPPED_MANGROVE_LOG_Z);
        registerTreeLog(states, "minecraft:stripped_pale_oak_log",
                Blocks.STRIPPED_PALE_OAK_LOG,
                Blocks.STRIPPED_PALE_OAK_LOG_X, Blocks.STRIPPED_PALE_OAK_LOG_Z);
        registerTreeLog(states, "minecraft:stripped_poplar_log", Blocks.STRIPPED_POPLAR_LOG,
                Blocks.STRIPPED_POPLAR_LOG_X, Blocks.STRIPPED_POPLAR_LOG_Z);
        registerTreeLog(states, "minecraft:poplar_wood", Blocks.POPLAR_WOOD);
        registerTreeLog(states, "minecraft:stripped_poplar_wood", Blocks.STRIPPED_POPLAR_WOOD);

        for (CatalogEntry leaf : List.of(
                entry(Blocks.LEAVES, "minecraft:oak_leaves"),
                entry(Blocks.BIRCH_LEAVES, "minecraft:birch_leaves"),
                entry(Blocks.SPRUCE_LEAVES, "minecraft:spruce_leaves"),
                entry(Blocks.JUNGLE_LEAVES, "minecraft:jungle_leaves"),
                entry(Blocks.ACACIA_LEAVES, "minecraft:acacia_leaves"),
                entry(Blocks.DARK_OAK_LEAVES, "minecraft:dark_oak_leaves"),
                entry(Blocks.CHERRY_LEAVES, "minecraft:cherry_leaves"),
                entry(Blocks.MANGROVE_LEAVES, "minecraft:mangrove_leaves"),
                entry(Blocks.PALE_OAK_LEAVES, "minecraft:pale_oak_leaves"),
                entry(Blocks.POPLAR_LEAVES_YELLOW, "minecraft:yellow_poplar_leaves"),
                entry(Blocks.POPLAR_LEAVES_ORANGE, "minecraft:orange_poplar_leaves"),
                entry(Blocks.POPLAR_LEAVES_RED, "minecraft:red_poplar_leaves"))) {
            registerTreeLeaves(states, leaf);
        }
        registerDefault(states, Blocks.SNOW, "minecraft:snow[layers=1]", false, false,
                FluidKind.NONE, 0, Capability.NONE);
        registerDefault(states, Blocks.LILY_PAD, "minecraft:lily_pad", false, false,
                FluidKind.NONE, 0, Capability.NONE);
        for (int pickles = 1; pickles <= 4; pickles++) {
            String exact = "minecraft:sea_pickle[pickles=" + pickles + ",waterlogged=true]";
            Mc263FeatureBlockState pickle = state(Blocks.SEA_PICKLE, exact, false, false,
                    FluidKind.WATER_SOURCE, 8, Capability.NONE);
            states.put(exact, pickle);
            if (pickles == 1) registerDefaultOnly(Blocks.SEA_PICKLE, pickle);
        }
        String[] coralSpecies = {"tube", "brain", "bubble", "fire", "horn"};
        for (int species = 0; species < coralSpecies.length; species++) {
            String key = coralSpecies[species];
            registerDefault(states, Blocks.TUBE_CORAL_BLOCK + species,
                    "minecraft:" + key + "_coral_block", true, true,
                    FluidKind.NONE, 0, Capability.NONE);
            for (int shape = 0; shape < 2; shape++) {
                int id = (shape == 0 ? Blocks.TUBE_CORAL : Blocks.TUBE_CORAL_FAN) + species;
                String suffix = shape == 0 ? "_coral" : "_coral_fan";
                registerDefault(states, id,
                        "minecraft:" + key + suffix + "[waterlogged=true]", false, false,
                        FluidKind.WATER_SOURCE, 8, Capability.NONE);
            }
            for (String facing : new String[]{"north", "east", "south", "west"}) {
                String exact = "minecraft:" + key + "_coral_wall_fan[facing=" + facing
                        + ",waterlogged=true]";
                Mc263FeatureBlockState wall = state(Blocks.TUBE_CORAL_WALL_FAN + species,
                        exact, false, false, FluidKind.WATER_SOURCE, 8, Capability.NONE);
                states.put(exact, wall);
                if (facing.equals("north")) {
                    registerDefaultOnly(Blocks.TUBE_CORAL_WALL_FAN + species, wall);
                }
            }
        }
        registerDefault(states, Blocks.POWDER_SNOW, "minecraft:powder_snow", false, false,
                FluidKind.NONE, 0, Capability.NONE);
        for (int moisture = 0; moisture <= 7; moisture++) {
            String exact = "minecraft:farmland[moisture=" + moisture + "]";
            Mc263FeatureBlockState farmland = state(Blocks.FARMLAND, exact, true, false,
                    FluidKind.NONE, 0, Capability.NONE);
            states.put(exact, farmland);
            if (moisture == 0) registerDefaultOnly(Blocks.FARMLAND, farmland);
        }

        // Exact, already-registered protocol carriers reached by the pure SimpleBlock family.
        // Distinct vanilla identities without an append-only protocol ID deliberately remain
        // absent: the FEATURES bridge must never alias one block identity onto another ID.
        registerDefault(states, Blocks.TALL_GRASS, "minecraft:short_grass", false, false,
                FluidKind.NONE, 0, Capability.NONE);
        registerDefault(states, Blocks.SUGARCANE, "minecraft:sugar_cane[age=0]", false, false,
                FluidKind.NONE, 0, Capability.NONE);
        registerDefault(states, Blocks.CACTUS, "minecraft:cactus[age=0]", true, false,
                FluidKind.NONE, 0, Capability.NONE);
        registerDefault(states, Blocks.SEAGRASS, "minecraft:seagrass", false, false,
                FluidKind.WATER_SOURCE, 8, Capability.NONE);
        registerDefault(states, Blocks.TALL_SEAGRASS,
                "minecraft:tall_seagrass[half=lower]", false, false,
                FluidKind.WATER_SOURCE, 8, Capability.NONE);
        registerAlias(states, Blocks.TALL_SEAGRASS,
                "minecraft:tall_seagrass[half=upper]", false, false,
                FluidKind.WATER_SOURCE, 8, Capability.NONE);
        for (int age = 0; age <= 25; age++) {
            String kelp = "minecraft:kelp[age=" + age + "]";
            if (age == 0) {
                registerDefault(states, Blocks.KELP, kelp, false, false,
                        FluidKind.WATER_SOURCE, 8, Capability.NONE);
            } else {
                registerAlias(states, Blocks.KELP, kelp, false, false,
                        FluidKind.WATER_SOURCE, 8, Capability.NONE);
            }
        }
        registerDefault(states, Blocks.KELP_PLANT, "minecraft:kelp_plant", false, false,
                FluidKind.WATER_SOURCE, 8, Capability.NONE);
        registerDefault(states, Blocks.BAMBOO,
                "minecraft:bamboo[age=0,leaves=none,stage=0]", false, false,
                FluidKind.NONE, 0, Capability.NONE);
        for (String exact : List.of(
                "minecraft:bamboo[age=1,leaves=none,stage=0]",
                "minecraft:bamboo[age=1,leaves=large,stage=1]",
                "minecraft:bamboo[age=1,leaves=large,stage=0]",
                "minecraft:bamboo[age=1,leaves=small,stage=0]")) {
            registerAlias(states, Blocks.BAMBOO, exact, false, false,
                    FluidKind.NONE, 0, Capability.NONE);
        }
        registerPaleMossCarpet(states);
        registerDefault(states, Blocks.MOSS_CARPET, "minecraft:moss_carpet", false, false,
                FluidKind.NONE, 0, Capability.NONE);
        registerDefault(states, Blocks.FLOWERING_AZALEA, "minecraft:flowering_azalea",
                false, false, FluidKind.NONE, 0, Capability.NONE);
        registerDefault(states, Blocks.AZALEA, "minecraft:azalea", false, false,
                FluidKind.NONE, 0, Capability.NONE);
        registerDripleafStates(states);
        registerNestedAzaleaLeaves(states, Blocks.AZALEA_LEAVES,
                "minecraft:azalea_leaves");
        registerNestedAzaleaLeaves(states, Blocks.FLOWERING_AZALEA_LEAVES,
                "minecraft:flowering_azalea_leaves");
        registerDefault(states, Blocks.SPORE_BLOSSOM, "minecraft:spore_blossom", false, false,
                FluidKind.NONE, 0, Capability.NONE);
        registerCaveVines(states);
        registerWaterloggedPlant(states, Blocks.HANGING_ROOTS, "minecraft:hanging_roots");
        registerDoublePlant(states, Blocks.LILAC, "minecraft:lilac");
        registerDoublePlant(states, Blocks.ROSE_BUSH, "minecraft:rose_bush");
        registerDoublePlant(states, Blocks.PEONY, "minecraft:peony");
        registerDefault(states, Blocks.LILY_OF_THE_VALLEY, "minecraft:lily_of_the_valley",
                false, false, FluidKind.NONE, 0, Capability.NONE);
        for (CatalogEntry flower : List.of(
                entry(Blocks.ALLIUM, "minecraft:allium"),
                entry(Blocks.AZURE_BLUET, "minecraft:azure_bluet"),
                entry(Blocks.RED_TULIP, "minecraft:red_tulip"),
                entry(Blocks.ORANGE_TULIP, "minecraft:orange_tulip"),
                entry(Blocks.WHITE_TULIP, "minecraft:white_tulip"),
                entry(Blocks.PINK_TULIP, "minecraft:pink_tulip"),
                entry(Blocks.OXEYE_DAISY, "minecraft:oxeye_daisy"),
                entry(Blocks.CORNFLOWER, "minecraft:cornflower"))) {
            registerDefault(states, flower.id(), flower.exact(), false, false,
                    FluidKind.NONE, 0, Capability.NONE);
        }
        registerDefault(states, Blocks.BLUE_ORCHID, "minecraft:blue_orchid", false, false,
                FluidKind.NONE, 0, Capability.NONE);
        registerDefault(states, Blocks.CLOSED_EYEBLOSSOM, "minecraft:closed_eyeblossom",
                false, false, FluidKind.NONE, 0, Capability.NONE);
        registerDoublePlant(states, Blocks.LARGE_FERN, "minecraft:large_fern");
        registerDefault(states, Blocks.MELON, "minecraft:melon", true, true,
                FluidKind.NONE, 0, Capability.NONE);
        registerFlowerBed(states, Blocks.PINK_PETALS, "minecraft:pink_petals",
                "flower_amount", 4);
        registerDoublePlant(states, Blocks.SUNFLOWER, "minecraft:sunflower");
        registerDoublePlant(states, Blocks.TALL_GRASS_263, "minecraft:tall_grass");
        registerDefault(states, Blocks.FLOWER_RED, "minecraft:poppy", false, false,
                FluidKind.NONE, 0, Capability.NONE);
        registerDefault(states, Blocks.FLOWER_YELLOW, "minecraft:dandelion", false, false,
                FluidKind.NONE, 0, Capability.NONE);
        registerDefault(states, Blocks.MUSHROOM_BROWN, "minecraft:brown_mushroom", false, false,
                FluidKind.NONE, 0, Capability.NONE);
        registerDefault(states, Blocks.MUSHROOM_RED, "minecraft:red_mushroom", false, false,
                FluidKind.NONE, 0, Capability.NONE);
        registerHugeMushroom(states, Blocks.BROWN_MUSHROOM_BLOCK,
                "minecraft:brown_mushroom_block");
        registerHugeMushroom(states, Blocks.RED_MUSHROOM_BLOCK,
                "minecraft:red_mushroom_block");
        registerHugeMushroom(states, Blocks.MUSHROOM_STEM, "minecraft:mushroom_stem");
        registerDefault(states, Blocks.FERN, "minecraft:fern", false, false,
                FluidKind.NONE, 0, Capability.NONE);
        registerDefault(states, Blocks.BUSH, "minecraft:bush", false, false,
                FluidKind.NONE, 0, Capability.NONE);
        registerDefault(states, Blocks.PUMPKIN, "minecraft:pumpkin", true, true,
                FluidKind.NONE, 0, Capability.NONE);
        registerDefault(states, Blocks.DEAD_BUSH, "minecraft:dead_bush", false, false,
                FluidKind.NONE, 0, Capability.NONE);
        registerDefault(states, Blocks.RED_SHRUB, "minecraft:red_shrub", false, false,
                FluidKind.NONE, 0, Capability.NONE);
        registerDefault(states, Blocks.FIREFLY_BUSH, "minecraft:firefly_bush", false, false,
                FluidKind.NONE, 0, Capability.NONE);
        registerDefault(states, Blocks.CACTUS_FLOWER, "minecraft:cactus_flower", false, false,
                FluidKind.NONE, 0, Capability.NONE);
        registerDefault(states, Blocks.SHORT_DRY_GRASS, "minecraft:short_dry_grass", false,
                false, FluidKind.NONE, 0, Capability.NONE);
        registerDefault(states, Blocks.TALL_DRY_GRASS, "minecraft:tall_dry_grass", false,
                false, FluidKind.NONE, 0, Capability.NONE);
        registerDefault(states, Blocks.SWEET_BERRY_BUSH,
                "minecraft:sweet_berry_bush[age=3]", false, false,
                FluidKind.NONE, 0, Capability.NONE);
        registerFlowerBed(states, Blocks.WILDFLOWERS, "minecraft:wildflowers",
                "flower_amount", 4);
        registerFlowerBed(states, Blocks.LEAF_LITTER, "minecraft:leaf_litter",
                "segment_amount", 4);

        for (CatalogEntry sapling : List.of(
                entry(Blocks.OAK_SAPLING, "minecraft:oak_sapling"),
                entry(Blocks.BIRCH_SAPLING, "minecraft:birch_sapling"),
                entry(Blocks.SPRUCE_SAPLING, "minecraft:spruce_sapling"),
                entry(Blocks.CHERRY_SAPLING, "minecraft:cherry_sapling"),
                entry(Blocks.POPLAR_SAPLING, "minecraft:poplar_sapling"))) {
            registerDefault(states, sapling.id(), sapling.exact(), false, false,
                    FluidKind.NONE, 0, Capability.NONE);
        }
        for (int age = 0; age <= 2; age++) {
            for (String facing : new String[]{"north", "east", "south", "west"}) {
                String exact = "minecraft:cocoa[age=" + age + ",facing=" + facing + "]";
                registerTreeState(states, Blocks.COCOA, exact,
                        age == 0 && facing.equals("north"), false, false,
                        FluidKind.NONE, 0);
            }
        }
        for (String facing : new String[]{"north", "east", "south", "west"}) {
            String exact = "minecraft:bee_nest[facing=" + facing + ",honey_level=0]";
            registerTreeState(states, Blocks.BEE_NEST, exact, facing.equals("north"),
                    true, true, FluidKind.NONE, 0, Capability.BEEHIVE);
        }
        for (boolean tip : new boolean[]{true, false}) {
            String exact = "minecraft:pale_hanging_moss[tip=" + tip + "]";
            registerTreeState(states, Blocks.PALE_HANGING_MOSS, exact, tip,
                    false, false, FluidKind.NONE, 0);
        }
        registerDefault(states, Blocks.CREAKING_HEART,
                "minecraft:creaking_heart[axis=y,creaking_heart_state=uprooted,natural=false]",
                true, true, FluidKind.NONE, 0, Capability.NONE);
        registerAlias(states, Blocks.CREAKING_HEART,
                "minecraft:creaking_heart[axis=y,creaking_heart_state=dormant,natural=true]",
                true, true, FluidKind.NONE, 0, Capability.NONE);
        for (int age = 0; age <= 1; age++) {
            for (String facing : new String[]{"north", "east", "south", "west"}) {
                String exact = "minecraft:shelf_mushroom[age=" + age + ",facing=" + facing + "]";
                registerTreeState(states, Blocks.SHELF_MUSHROOM, exact,
                        age == 0 && facing.equals("north"), false, false,
                        FluidKind.NONE, 0);
            }
        }
        for (boolean waterlogged : new boolean[]{false, true}) {
            String exact = "minecraft:mangrove_roots[waterlogged=" + waterlogged + "]";
            registerTreeState(states, Blocks.MANGROVE_ROOTS, exact, !waterlogged,
                    true, false, waterlogged ? FluidKind.WATER_SOURCE : FluidKind.NONE,
                    waterlogged ? 8 : 0);
        }
        registerDefault(states, Blocks.MUDDY_MANGROVE_ROOTS,
                "minecraft:muddy_mangrove_roots[axis=y]", true, true,
                FluidKind.NONE, 0, Capability.NONE);
        registerDefault(states, Blocks.MANGROVE_PROPAGULE,
                "minecraft:mangrove_propagule[age=0,hanging=false,stage=0,waterlogged=false]",
                false, false, FluidKind.NONE, 0, Capability.NONE);
        for (int age = 0; age <= 4; age++) {
            for (boolean waterlogged : new boolean[]{false, true}) {
                registerAlias(states, Blocks.MANGROVE_PROPAGULE,
                        "minecraft:mangrove_propagule[age=" + age
                                + ",hanging=true,stage=0,waterlogged=" + waterlogged + "]",
                        false, false,
                        waterlogged ? FluidKind.WATER_SOURCE : FluidKind.NONE,
                        waterlogged ? 8 : 0, Capability.NONE);
            }
        }

        for (int faceMask = 0; faceMask < 32; faceMask++) {
            String exact = vineState(faceMask);
            Mc263FeatureBlockState vine = state(Blocks.VINE, exact, false, false,
                    FluidKind.NONE, 0, Capability.NONE);
            if (faceMask == 0) registerDefaultOnly(Blocks.VINE, vine);
            if (states.putIfAbsent(exact, vine) != null) {
                throw new ExceptionInInitializerError(
                        "duplicate exact FEATURES state: " + exact);
            }
        }

        for (int faceMask = 0; faceMask < 64; faceMask++) {
            for (boolean waterlogged : new boolean[]{false, true}) {
                String exact = glowLichenState(faceMask, waterlogged);
                Mc263FeatureBlockState lichen = state(Blocks.GLOW_LICHEN, exact, false, false,
                        waterlogged ? FluidKind.WATER_SOURCE : FluidKind.NONE,
                        waterlogged ? 8 : 0, Capability.NONE);
                if (faceMask == 0 && !waterlogged) {
                    registerDefaultOnly(Blocks.GLOW_LICHEN, lichen);
                }
                if (states.putIfAbsent(exact, lichen) != null) {
                    throw new ExceptionInInitializerError(
                            "duplicate exact FEATURES state: " + exact);
                }
            }
        }

        for (int faceMask = 0; faceMask < 64; faceMask++) {
            for (boolean waterlogged : new boolean[]{false, true}) {
                String exact = sculkVeinState(faceMask, waterlogged);
                Mc263FeatureBlockState vein = state(Blocks.SCULK_VEIN, exact, true, false,
                        waterlogged ? FluidKind.WATER_SOURCE : FluidKind.NONE,
                        waterlogged ? 8 : 0, Capability.NONE);
                if (faceMask == 0 && !waterlogged) {
                    registerDefaultOnly(Blocks.SCULK_VEIN, vein);
                }
                if (states.putIfAbsent(exact, vein) != null) {
                    throw new ExceptionInInitializerError(
                            "duplicate exact FEATURES state: " + exact);
                }
            }
        }

        registerDefault(states, Blocks.SCULK, "minecraft:sculk", true, true,
                FluidKind.NONE, 0, Capability.NONE);
        registerDefault(states, Blocks.SCULK_CATALYST,
                "minecraft:sculk_catalyst[bloom=false]", true, true,
                FluidKind.NONE, 0, Capability.SCULK_CATALYST);
        for (boolean waterlogged : new boolean[]{false, true}) {
            String sensorExact = "minecraft:sculk_sensor[power=0,sculk_sensor_phase=inactive,"
                    + "waterlogged=" + waterlogged + "]";
            Mc263FeatureBlockState sensor = state(Blocks.SCULK_SENSOR, sensorExact, true, false,
                    waterlogged ? FluidKind.WATER_SOURCE : FluidKind.NONE,
                    waterlogged ? 8 : 0, Capability.SCULK_SENSOR);
            states.put(sensorExact, sensor);
            if (!waterlogged) registerDefaultOnly(Blocks.SCULK_SENSOR, sensor);

            for (boolean canSummon : new boolean[]{false, true}) {
                String shriekerExact = "minecraft:sculk_shrieker[can_summon=" + canSummon
                        + ",shrieking=false,waterlogged=" + waterlogged + "]";
                Mc263FeatureBlockState shrieker = state(Blocks.SCULK_SHRIEKER, shriekerExact,
                        false, false, waterlogged ? FluidKind.WATER_SOURCE : FluidKind.NONE,
                        waterlogged ? 8 : 0, Capability.SCULK_SHRIEKER);
                states.put(shriekerExact, shrieker);
                if (!canSummon && !waterlogged) {
                    registerDefaultOnly(Blocks.SCULK_SHRIEKER, shrieker);
                }
            }
        }

        for (int level = 0; level <= 7; level++) {
            String exact = level == 0 ? "minecraft:water"
                    : "minecraft:water[level=" + level + "]";
            registerDefault(states, Blocks.WATER_SOURCE + level, exact, false, false,
                    level == 0 ? FluidKind.WATER_SOURCE : FluidKind.WATER_FLOWING,
                    level == 0 ? 8 : 8 - level, Capability.NONE);
        }
        for (int level = 8; level <= 15; level++) {
            registerAlias(states, Blocks.WATER_SOURCE + level - 8,
                    "minecraft:water[level=" + level + "]", false, false,
                    FluidKind.WATER_FLOWING, 16 - level, Capability.NONE);
        }
        for (int level = 0; level <= 3; level++) {
            String exact = level == 0 ? "minecraft:lava"
                    : "minecraft:lava[level=" + level + "]";
            registerDefault(states, Blocks.LAVA_SOURCE + level, exact, false, false,
                    level == 0 ? FluidKind.LAVA_SOURCE : FluidKind.LAVA_FLOWING,
                    level == 0 ? 8 : 8 - level, Capability.NONE);
        }

        registerDefault(states, Blocks.SPAWNER_BASE, "minecraft:spawner", true, false,
                FluidKind.NONE, 0, Capability.SPAWNER);
        registerDefaultOnly(Blocks.SPAWNER_BASE + 1,
                state(Blocks.SPAWNER_BASE + 1, "minecraft:spawner", true, false,
                        FluidKind.NONE, 0, Capability.SPAWNER));
        registerDefaultOnly(Blocks.SPAWNER_BASE + 2,
                state(Blocks.SPAWNER_BASE + 2, "minecraft:spawner", true, false,
                        FluidKind.NONE, 0, Capability.SPAWNER));

        registerDefault(states, Blocks.CHEST,
                "minecraft:chest[facing=north,type=single,waterlogged=false]", true, false,
                FluidKind.NONE, 0, Capability.RANDOMIZABLE_CONTAINER);
        for (String facing : new String[]{"north", "east", "south", "west"}) {
            for (boolean waterlogged : new boolean[]{false, true}) {
                if (facing.equals("north") && !waterlogged) continue;
                String exact = "minecraft:chest[facing=" + facing
                        + ",type=single,waterlogged=" + waterlogged + "]";
                Mc263FeatureBlockState chest = state(Blocks.CHEST, exact, true, false,
                        waterlogged ? FluidKind.WATER_SOURCE : FluidKind.NONE,
                        waterlogged ? 8 : 0, Capability.RANDOMIZABLE_CONTAINER);
                states.put(exact, chest);
            }
        }
        registerIglooStates(states);
        registerSwampHutStates(states);
        registerJungleMechanismStates(states);
        registerDesertPyramidStates(states);
        registerStrongholdStates(states);
        registerTrailOceanRuinStates(states);
        registerRuinedPortalStates(states);
        registerAbandonedCampStates(states);
        registerWhiteWallBannerStates(states);
        registerTrialChambersG1QStates(states);
        registerMansionP2PStates(states);
        registerVillageG3J15States(states);
        registerDefault(states, Blocks.BARRIER, "minecraft:barrier[waterlogged=false]",
                true, false, FluidKind.NONE, 0, Capability.NONE);

        String[] budBlocks = {
                "minecraft:small_amethyst_bud", "minecraft:medium_amethyst_bud",
                "minecraft:large_amethyst_bud", "minecraft:amethyst_cluster"
        };
        int[] budIds = {
                Blocks.SMALL_AMETHYST_BUD, Blocks.MEDIUM_AMETHYST_BUD,
                Blocks.LARGE_AMETHYST_BUD, Blocks.AMETHYST_CLUSTER
        };
        for (int index = 0; index < budBlocks.length; index++) {
            for (String facing : new String[]{"down", "up", "north", "south", "west", "east"}) {
                for (boolean waterlogged : new boolean[]{false, true}) {
                    String exact = budBlocks[index] + "[facing=" + facing
                            + ",waterlogged=" + waterlogged + "]";
                    Mc263FeatureBlockState bud = state(budIds[index], exact, false, false,
                            waterlogged ? FluidKind.WATER_SOURCE : FluidKind.NONE,
                            waterlogged ? 8 : 0, Capability.NONE);
                    states.put(exact, bud);
                    if (facing.equals("up") && !waterlogged) {
                        registerDefaultOnly(budIds[index], bud);
                    }
                }
            }
        }

        for (String direction : new String[]{"down", "up"}) {
            for (String thickness : new String[]{"tip", "tip_merge", "frustum", "middle", "base"}) {
                for (boolean waterlogged : new boolean[]{false, true}) {
                    String exact = "minecraft:sulfur_spike[thickness=" + thickness
                            + ",vertical_direction=" + direction + ",waterlogged="
                            + waterlogged + "]";
                    Mc263FeatureBlockState spike = state(Blocks.SULFUR_SPIKE, exact, true, false,
                            waterlogged ? FluidKind.WATER_SOURCE : FluidKind.NONE,
                            waterlogged ? 8 : 0, Capability.NONE);
                    states.put(exact, spike);
                    if (direction.equals("up") && thickness.equals("tip") && !waterlogged) {
                        registerDefaultOnly(Blocks.SULFUR_SPIKE, spike);
                    }
                }
            }
        }

        for (String direction : new String[]{"down", "up"}) {
            for (String thickness : new String[]{"tip", "tip_merge", "frustum", "middle", "base"}) {
                for (boolean waterlogged : new boolean[]{false, true}) {
                    String exact = "minecraft:pointed_dripstone[thickness=" + thickness
                            + ",vertical_direction=" + direction + ",waterlogged="
                            + waterlogged + "]";
                    Mc263FeatureBlockState pointed = state(Blocks.POINTED_DRIPSTONE, exact,
                            true, false,
                            waterlogged ? FluidKind.WATER_SOURCE : FluidKind.NONE,
                            waterlogged ? 8 : 0, Capability.NONE);
                    states.put(exact, pointed);
                    if (direction.equals("up") && thickness.equals("tip") && !waterlogged) {
                        registerDefaultOnly(Blocks.POINTED_DRIPSTONE, pointed);
                    }
                }
            }
        }

        for (String potentState : new String[]{"dry", "wet", "dormant", "erupting", "continuous"}) {
            String exact = "minecraft:potent_sulfur[potent_sulfur_state="
                    + potentState + "]";
            Mc263FeatureBlockState potent = state(Blocks.POTENT_SULFUR, exact, true, true,
                    FluidKind.NONE, 0, Capability.POTENT_SULFUR);
            states.put(exact, potent);
            if (potentState.equals("dry")) registerDefaultOnly(Blocks.POTENT_SULFUR, potent);
        }
        registerAuthenticatedProductionClosure(states);
        registerSaplingStageStates(states);
        registerAuthenticatedVillagePileStates(states);
        registerAuthenticatedPostProductionClosure(states);
        registerAuthenticatedSculkPatchClosure(states);
        registerAuthenticatedRuinedPortalClosure(states);
        BY_EXACT = Collections.unmodifiableMap(states);
        // 26.3 state strings carry properties in no semantically meaningful order, so an
        // order-only spelling of a released state resolves through the released entry instead
        // of failing closed or claiming a second code.
        Map<String, Mc263FeatureBlockState> sortedIdentities = new HashMap<>();
        for (Map.Entry<String, Mc263FeatureBlockState> entry : states.entrySet()) {
            sortedIdentities.putIfAbsent(propertySortedIdentity(entry.getKey()), entry.getValue());
        }
        BY_SORTED_IDENTITY = Collections.unmodifiableMap(sortedIdentities);
        SPELLING_MEMO_BOUND = BY_EXACT.size() + BY_SORTED_IDENTITY.size();
        Map<String, Mc263FeatureBlockState> blockKeys = new HashMap<>();
        for (Mc263FeatureBlockState state : states.values()) {
            if (state.exactState().equals(state.blockKey())) {
                blockKeys.put(state.blockKey(), state);
            } else {
                blockKeys.putIfAbsent(state.blockKey(), state);
            }
        }
        BY_BLOCK_KEY = Collections.unmodifiableMap(blockKeys);
    }

    private final int blockId;
    private final String exactState;
    private final String blockKey;
    private final FluidKind fluidKind;
    private final int fluidAmount;
    private final Capability capability;
    /**
     * Catalog-registration render prerequisite only — {@link #isSolid()} is the official
     * {@code POST_FACT_EV7} column and never reads this field. Kept because
     * {@link #solidRender}, the {@code canOcclude} fact the transcript does not publish, is
     * registered as a refinement of it.
     */
    private final boolean solid;
    private final boolean solidRender;
    /**
     * Official per-state facts this carrier no longer restates: bits 0..5 are the
     * {@code sturdyFaces} column of the pinned {@code POST_NEIGHBOR} row, indexed by
     * {@link OcclusionFace#ordinal()}, and bit {@link #OFFICIAL_SOLID_BIT} is the pinned
     * {@code POST_FACT_EV7} {@code SOLID} column. The value is a pure function of
     * {@link #exactState}, so the lazy fill is idempotent and races are benign; a negative value
     * means "not resolved yet" because {@code 0} is a legal mask (every face non-sturdy).
     *
     * <p>AGENTS rule 10m: the predicate now exists exactly once, in the pinned transcript both
     * languages parse, and there is no hand-written list left that could contradict it. AGENTS
     * rule 10l: the FEATURES hot path queries these facts per placed cell, so the authenticated
     * lookup is amortized into the interned catalog instance instead of being repeated.</p>
     */
    private int officialFacts = UNRESOLVED_OFFICIAL_FACTS;

    private Mc263FeatureBlockState(int blockId, String exactState, boolean solid,
            boolean solidRender, FluidKind fluidKind, int fluidAmount, Capability capability) {
        this.blockId = blockId;
        this.exactState = exactState;
        this.blockKey = blockKey(exactState);
        this.solid = solid;
        this.solidRender = solidRender;
        this.fluidKind = fluidKind;
        this.fluidAmount = fluidAmount;
        this.capability = capability;
    }

    public static Mc263FeatureBlockState defaultForId(int blockId) {
        if (blockId < 0 || blockId >= DEFAULT_BY_ID.length || DEFAULT_BY_ID[blockId] == null) {
            throw new IllegalStateException("no exact 26.3 FEATURES state for block ID " + blockId);
        }
        return DEFAULT_BY_ID[blockId];
    }

    static String defaultExactStateForId(int blockId) {
        return defaultForId(blockId).exactState;
    }

    static Mc263FeatureBlockState defaultOrNullForId(int blockId) {
        return blockId >= 0 && blockId < DEFAULT_BY_ID.length ? DEFAULT_BY_ID[blockId] : null;
    }

    /** Immutable released exact-state catalog used by versioned chunk carriers. */
    static List<Mc263FeatureBlockState> exactCatalog() {
        return BY_EXACT.values().stream()
                .sorted(java.util.Comparator.comparingInt(Mc263FeatureBlockState::blockId)
                        .thenComparing(Mc263FeatureBlockState::exactState))
                .toList();
    }

    static boolean isStateCloseExactState(String exactState) {
        return STATE_CLOSE_ADDITIONS.contains(exactState);
    }

    /** True only for a state appended by the official connection-shaped POST closure. */
    static boolean isPostStateCloseExactState(String exactState) {
        return POST_STATE_CLOSE_ADDITIONS.contains(exactState);
    }

    /** True only for an exact sapling {@code stage} state appended after the STATE-CLOSE batch. */
    static boolean isSaplingStageExactState(String exactState) {
        return SAPLING_STAGE_ADDITIONS.contains(exactState);
    }

    /** True only for an exact state added from the pinned production-closure oracle receipt. */
    public static boolean isAuthenticatedProductionClosureExactState(String exactState) {
        return AUTHENTICATED_PRODUCTION_CLOSURE.contains(exactState);
    }

    private static void registerAuthenticatedProductionClosure(
            Map<String, Mc263FeatureBlockState> states) {
        List<String> authenticated = java.util.stream.Stream.concat(
                Mc263ExactStateProductionClosureData.states().stream(),
                Mc263ExactStateProductionClosureJ4Data.states().stream()).toList();
        registerAuthenticatedProductionClosure(states, authenticated, STATE_CLOSE_ADDITIONS,
                false);
    }

    private static void registerAuthenticatedPostProductionClosure(
            Map<String, Mc263FeatureBlockState> states) {
        registerAuthenticatedProductionClosure(states,
                Mc263ExactStateProductionClosurePostData.states(),
                POST_STATE_CLOSE_ADDITIONS, true);
    }

    /**
     * Ancient City places sculk through the structure's post-placement
     * {@code minecraft:sculk_patch_ancient_city} configured feature; its enumerated world-write
     * surface ({@link Mc263SculkPatchFeature#preflight}) is the appended closure tranche carried by
     * {@link Mc263ExactStateProductionClosureSculkData}. Every row of that tranche is already a
     * released catalog state, so the tranche authenticates the states without claiming a code.
     */
    private static void registerAuthenticatedSculkPatchClosure(
            Map<String, Mc263FeatureBlockState> states) {
        registerAuthenticatedProductionClosure(states,
                Mc263ExactStateProductionClosureSculkData.states(),
                SCULK_PATCH_CLOSURE_ADDITIONS, true);
    }

    /** True only for a state the sculk-patch closure tranche had to append to the catalog. */
    static boolean isSculkPatchClosureExactState(String exactState) {
        return SCULK_PATCH_CLOSURE_ADDITIONS.contains(exactState);
    }

    /**
     * A ruined portal is built by running the 13 pinned templates through the
     * {@code minecraft:ruined_portal} processor list and then settling the placed state against
     * the fluid already at the position, so a mossy slab, stair or wall keeps the source
     * {@code type}, {@code waterlogged} and wall-connection properties and gains
     * {@code waterlogged=true} wherever the position holds water. That pipeline surface is the
     * appended closure tranche carried by
     * {@link Mc263ExactStateProductionClosureRuinedPortalData}; every row is a released state of
     * a block the catalog already codes, so the tranche claims no new block id.
     */
    private static void registerAuthenticatedRuinedPortalClosure(
            Map<String, Mc263FeatureBlockState> states) {
        registerAuthenticatedProductionClosure(states,
                Mc263ExactStateProductionClosureRuinedPortalData.states(),
                RUINED_PORTAL_CLOSURE_ADDITIONS, true);
    }

    /** True only for a state the ruined-portal closure tranche had to append to the catalog. */
    static boolean isRuinedPortalClosureExactState(String exactState) {
        return RUINED_PORTAL_CLOSURE_ADDITIONS.contains(exactState);
    }

    private static void registerAuthenticatedProductionClosure(
            Map<String, Mc263FeatureBlockState> states, List<String> authenticated,
            Set<String> additions, boolean postClosure) {
        AUTHENTICATED_PRODUCTION_CLOSURE.addAll(authenticated);
        // The frozen catalog registers each state under one property order; the pinned receipt
        // spells the same state with the official order. Index by property-sorted identity so an
        // order-only spelling resolves through the released entry instead of appending a second
        // code for one 26.3 block state.
        Set<String> sortedIdentities = new HashSet<>();
        for (String released : states.keySet()) sortedIdentities.add(propertySortedIdentity(released));
        for (String exact : authenticated) {
            if (states.containsKey(exact)) continue;
            if (!sortedIdentities.add(propertySortedIdentity(exact))) continue;
            String bare = Mc263VanillaCanonicalState.internalBareForVanillaCompletion(exact);
            if (bare != null && states.containsKey(bare)) continue;
            String key = blockKey(exact);
            Mc263FeatureBlockState prototype = states.values().stream()
                    .filter(value -> value.blockKey.equals(key))
                    .findFirst().orElse(null);
            int blockId = prototype == null
                    ? postClosure ? Mc263ExactStateProductionClosurePostData.blockId(key)
                            : stateCloseJ4BlockId(key)
                    : prototype.blockId;
            if (prototype == null && blockId < 0) continue;
            Mc263FeatureBlockState shape = prototype == null
                    ? stateCloseShapePrototype(states, key) : prototype;
            if (shape == null) shape = defaultOrNullForId(blockId);
            boolean waterlogged = exact.contains("waterlogged=true");
            boolean doubleSlab = exact.contains("_slab[type=double,");
            boolean solid = shape == null || shape.solid;
            boolean shaped = key.endsWith("_stairs") || key.endsWith("_slab")
                    || key.endsWith("_fence") || key.endsWith("_fence_gate")
                    || key.endsWith("_trapdoor") || key.endsWith("_door")
                    || key.endsWith("_wall");
            boolean solidRender = doubleSlab
                    || (shape == null ? !shaped : shape.solidRender);
            // The shape prototype is only the first sibling state of the same block, so its
            // fluid may belong to a waterlogged=true sibling. A state that spells the property
            // carries its own fluid: waterlogged=true is a level-8 water source and
            // waterlogged=false is dry. Only a block without the property (kelp, seagrass and the
            // other intrinsically water-filled families) may inherit the prototype's fluid.
            boolean spellsWaterlogged = exact.contains("waterlogged=");
            FluidKind fluid = waterlogged ? FluidKind.WATER_SOURCE
                    : spellsWaterlogged || shape == null ? FluidKind.NONE : shape.fluidKind;
            int fluidAmount = waterlogged ? 8
                    : spellsWaterlogged || shape == null ? 0 : shape.fluidAmount;
            Capability capability = shape == null ? Capability.NONE : shape.capability;
            if (defaultOrNullForId(blockId) == null) {
                registerDefault(states, blockId, exact, solid, solidRender, fluid, fluidAmount,
                        capability);
            } else {
                registerAlias(states, blockId, exact, solid, solidRender, fluid, fluidAmount,
                        capability);
            }
            additions.add(exact);
        }
    }

    /** Property-order-independent identity of one exact state, for duplicate-spelling checks. */
    private static String propertySortedIdentity(String exactState) {
        int bracket = exactState.indexOf('[');
        if (bracket < 0 || !exactState.endsWith("]")) return exactState;
        String[] properties =
                exactState.substring(bracket + 1, exactState.length() - 1).split(",", -1);
        java.util.Arrays.sort(properties);
        return exactState.substring(0, bracket) + "[" + String.join(",", properties) + "]";
    }

    private static Mc263FeatureBlockState stateCloseShapePrototype(
            Map<String, Mc263FeatureBlockState> states, String key) {
        String suffix = key.substring(key.lastIndexOf('_') + 1);
        String prototypeKey = switch (suffix) {
            case "stairs" -> "minecraft:oak_stairs";
            case "slab" -> "minecraft:oak_slab";
            case "fence" -> "minecraft:oak_fence";
            case "gate" -> "minecraft:oak_fence_gate";
            case "trapdoor" -> "minecraft:oak_trapdoor";
            case "door" -> "minecraft:oak_door";
            case "wall" -> "minecraft:cobblestone_wall";
            default -> null;
        };
        if (prototypeKey == null) return null;
        return states.values().stream().filter(value -> value.blockKey.equals(prototypeKey))
                .findFirst().orElse(null);
    }

    private static int stateCloseJ4BlockId(String key) {
        return Mc263ExactStateProductionClosureJ4Data.blockId(key);
    }

    static Mc263FeatureBlockState forSemanticBlockKey(String blockKey) {
        String canonical = requireCanonicalResourceKey(blockKey, "block key");
        Mc263FeatureBlockState state = BY_BLOCK_KEY.get(canonical);
        if (state == null) {
            throw new IllegalArgumentException(
                    "unsupported semantic 26.3 FEATURES block key: " + blockKey);
        }
        return state;
    }

    public static Mc263FeatureBlockState fromExact(String exactState) {
        Mc263FeatureBlockState resolved = RESOLVED_SPELLINGS.get(exactState);
        if (resolved != null) return resolved;
        String canonical = canonicalize(exactState);
        Mc263FeatureBlockState state = BY_EXACT.get(canonical);
        if (state == null) {
            throw new IllegalArgumentException(
                    "unsupported exact 26.3 FEATURES state: " + exactState);
        }
        if (RESOLVED_SPELLINGS.size() < SPELLING_MEMO_BOUND) {
            RESOLVED_SPELLINGS.putIfAbsent(exactState, state);
        }
        return state;
    }

    /** Canonical compact spelling for an exact state, including processor intermediates. */
    public static String canonicalExactState(String exactState) {
        return canonicalize(exactState);
    }

    /** Whether the canonical exact state is present in the frozen FEATURES catalog. */
    public static boolean supportsExactState(String exactState) {
        try {
            fromExact(exactState);
            return true;
        } catch (IllegalArgumentException unsupported) {
            return false;
        }
    }

    static String requireCanonicalResourceKey(String key, String description) {
        if (!isAsciiResourceKey(key)) {
            throw new IllegalArgumentException("canonical ASCII " + description
                    + " is required: " + key);
        }
        return key;
    }

    static String requireFluidTickKey(String key) {
        String canonical = requireCanonicalResourceKey(key, "fluid tick type");
        return switch (canonical) {
            case "minecraft:water", "minecraft:flowing_water",
                    "minecraft:lava", "minecraft:flowing_lava" -> canonical;
            default -> throw new IllegalArgumentException(
                    "unsupported 26.3 fluid tick type: " + key);
        };
    }

    static boolean legacyTickKeyMatches(int blockId, String key) {
        return ("webcraft:block_" + blockId).equals(key);
    }

    public int blockId() {
        return blockId;
    }

    public String exactState() {
        return exactState;
    }

    public String blockKey() {
        return blockKey;
    }

    public FluidKind fluidKind() {
        return fluidKind;
    }

    public int fluidAmount() {
        return fluidAmount;
    }

    public String fluidTypeKey() {
        return switch (fluidKind) {
            case NONE -> "minecraft:empty";
            case WATER_SOURCE -> "minecraft:water";
            case WATER_FLOWING -> "minecraft:flowing_water";
            case LAVA_SOURCE -> "minecraft:lava";
            case LAVA_FLOWING -> "minecraft:flowing_lava";
        };
    }

    public Capability capability() {
        return capability;
    }

    /**
     * Official {@code BlockStateBase#isAir()} for this exact state, read from the pinned
     * {@code POST_FACT_EV7} {@code AIR} column rather than restated as a key list here.
     */
    public boolean isAir() {
        return (officialFacts() & 1 << OFFICIAL_AIR_BIT) != 0;
    }

    /** Exact pinned {@code #leaves} membership used by huge-mushroom validation. */
    public boolean isLeavesTag() {
        return LEAVES.contains(blockKey);
    }

    /** Exact current-catalog intersection of {@code #replaceable_by_mushrooms}. */
    public boolean isReplaceableByMushroomsTag() {
        return isLeavesTag() || REPLACEABLE_BY_MUSHROOMS.contains(blockKey);
    }

    /** Exact current-catalog intersection of both huge-mushroom can-place-on tags. */
    public boolean isHugeMushroomSubstrateTag() {
        return HUGE_MUSHROOM_SUBSTRATES.contains(blockKey);
    }

    /** Exact current-catalog expansion of pinned {@code #supports_bamboo}. */
    public boolean supportsBamboo() {
        return Set.of("minecraft:sand", "minecraft:red_sand", "minecraft:suspicious_sand",
                "minecraft:dirt", "minecraft:coarse_dirt", "minecraft:rooted_dirt",
                "minecraft:mud", "minecraft:muddy_mangrove_roots",
                "minecraft:moss_block", "minecraft:pale_moss_block",
                "minecraft:grass_block", "minecraft:podzol", "minecraft:mycelium",
                "minecraft:bamboo", "minecraft:gravel").contains(blockKey);
    }

    /** Exact pinned {@code #beneath_bamboo_podzol_replaceable} substrate closure. */
    public boolean beneathBambooPodzolReplaceable() {
        return Set.of("minecraft:dirt", "minecraft:coarse_dirt", "minecraft:rooted_dirt",
                "minecraft:mud", "minecraft:muddy_mangrove_roots",
                "minecraft:moss_block", "minecraft:pale_moss_block",
                "minecraft:grass_block", "minecraft:podzol", "minecraft:mycelium")
                .contains(blockKey);
    }

    /** Exact representable intersection of pinned {@code replaceable_by_trees}. */
    public boolean replaceableByTrees() {
        return LEAVES.contains(blockKey) || REPLACEABLE_BY_TREES.contains(blockKey);
    }

    /** Exact pinned {@code cannot_replace_below_tree_trunk} closure. */
    public boolean cannotReplaceBelowTreeTrunk() {
        return CANNOT_REPLACE_BELOW_TREE_TRUNK.contains(blockKey);
    }

    /** Exact pinned {@code substrate_overworld} closure. */
    public boolean isSubstrateOverworld() {
        return SUBSTRATE_OVERWORLD.contains(blockKey);
    }

    /** Exact representable intersection of pinned {@code #logs}. */
    public boolean isLogsTag() {
        return LOGS.contains(blockKey);
    }

    /** Exact representable intersection of pinned {@code #jungle_logs}. */
    public boolean isJungleLogsTag() {
        return blockKey.equals("minecraft:jungle_log")
                || blockKey.equals("minecraft:stripped_jungle_log");
    }

    /** Exact representable intersection of pinned {@code #supports_vegetation}. */
    public boolean supportsVegetationTag() {
        return supportsVegetationTag(blockKey);
    }

    /** Key-level form for callers that already projected an exact carrier to its block key. */
    public static boolean supportsVegetationTag(String blockKey) {
        return SUBSTRATE_OVERWORLD.contains(blockKey) || blockKey.equals("minecraft:farmland");
    }

    /** Exact pinned {@code mangrove_roots_can_grow_through} closure. */
    public boolean mangroveRootsCanGrowThrough() {
        return MANGROVE_ROOTS_GROW_THROUGH.contains(blockKey);
    }

    /** Exact pinned {@code mangrove_logs_can_grow_through} closure. */
    public boolean mangroveLogsCanGrowThrough() {
        return MANGROVE_LOGS_GROW_THROUGH.contains(blockKey);
    }

    /** Exact pinned {@code mud} tag closure. */
    public boolean isMudTag() {
        return blockKey.equals("minecraft:mud")
                || blockKey.equals("minecraft:muddy_mangrove_roots");
    }

    /** Exact pinned {@code snow} tag closure. */
    public boolean isSnowTag() {
        return blockKey.equals("minecraft:snow") || blockKey.equals("minecraft:snow_block")
                || blockKey.equals("minecraft:powder_snow");
    }

    /**
     * Official {@code BlockStateBase#isSolid()} for this exact state, read from the pinned
     * {@code POST_FACT_EV7} {@code SOLID} column rather than restated here.
     *
     * <p>The transcript publishes the column for all every exact
     * catalog state, so the predicate is total; a state the transcript never published fails
     * closed inside the resolver instead of being guessed.</p>
     */
    public boolean isSolid() {
        return (officialFacts() & 1 << OFFICIAL_SOLID_BIT) != 0;
    }

    /**
     * Official facts of this exact state, resolved once from the pinned POST transcript.
     *
     * <p>{@link Mc263PostprocessResolver#requireNeighborAuthority} answers the
     * {@code sturdyFaces} column — from the published row, or from the connection-shape closure
     * over the published rows of the same family — and
     * {@link Mc263PostprocessResolver#ev7Fact} answers the {@code SOLID} column. Both fail closed
     * on a state the transcript never published, which is why nothing here has a fallback.</p>
     */
    private int officialFacts() {
        int facts = officialFacts;
        if (facts != UNRESOLVED_OFFICIAL_FACTS) return facts;
        Mc263PostprocessResolver.NeighborAuthority authority =
                Mc263PostprocessResolver.requireNeighborAuthority(this);
        facts = 0;
        for (int face = 0; face < OFFICIAL_FACE_DIRECTIONS.length; face++) {
            if (authority.faceSturdy(OFFICIAL_FACE_DIRECTIONS[face])) facts |= 1 << face;
        }
        if (Mc263PostprocessResolver.ev7Fact(this, "SOLID")) {
            facts |= 1 << OFFICIAL_SOLID_BIT;
        }
        if (Mc263PostprocessResolver.ev7Fact(this, "AIR")) {
            facts |= 1 << OFFICIAL_AIR_BIT;
        }
        officialFacts = facts;
        return facts;
    }

    /** Official {@code sturdyFaces} bit of one face, from the pinned POST neighbour row. */
    private boolean officialFaceSturdy(OcclusionFace face) {
        return (officialFacts() & 1 << Objects.requireNonNull(face, "face").ordinal()) != 0;
    }

    public boolean isSolidRender() {
        return solidRender;
    }

    /**
     * Official {@code BlockState#isFaceSturdy(EmptyBlockGetter.INSTANCE, ZERO, DOWN)} for this
     * exact state, read from the pinned {@code POST_NEIGHBOR} {@code sturdyFaces} column.
     */
    public boolean isFaceSturdyDown() {
        return officialFaceSturdy(OcclusionFace.DOWN);
    }

    /**
     * Official empty-world face-sturdy query for any direction, from the same pinned column.
     *
     * <p>Vanilla {@code isFaceSturdy(level, pos, face)} is
     * {@code isFaceSturdy(level, pos, face, SupportType.FULL)}, so it and
     * {@link #isSupportShapeFull} are one predicate; the transcript publishes it once and both
     * accessors read that one column.</p>
     */
    public boolean isFaceSturdy(OcclusionFace face) {
        return officialFaceSturdy(face);
    }

    /** Official empty-world full support-shape predicate; the {@code SupportType.FULL} column. */
    public boolean isSupportShapeFull(OcclusionFace face) {
        return officialFaceSturdy(face);
    }

    /** Exact full support-shape-or-full-collision-shape predicate used by multiface attachment. */
    public boolean isSupportOrCollisionFull(OcclusionFace face) {
        Objects.requireNonNull(face, "face");
        if (blockKey.equals("minecraft:sculk_vein")) return false;
        if (blockKey.equals("minecraft:sculk_sensor")) return face == OcclusionFace.DOWN;
        return isSupportShapeFull(face) || isCollisionShapeFullBlock();
    }

    /** Exact empty-world full collision-shape predicate used by sculk patch placement. */
    public boolean isCollisionShapeFullBlock() {
        return !blockKey.equals("minecraft:mud") && !blockKey.equals("minecraft:farmland")
                && (solidRender
                || blockKey.equals("minecraft:barrier")
                || blockKey.equals("minecraft:spawner")
                || blockKey.equals("minecraft:sculk_sensor")
                || blockKey.equals("minecraft:mangrove_roots")
                || isLeavesTag());
    }

    /** Exact {@code BlockState#canBeReplaced()} fact needed by sculk-vein spread. */
    public boolean canBeReplaced() {
        return isAir() || blockKey.equals("minecraft:water")
                || blockKey.equals("minecraft:lava") || blockKey.equals("minecraft:snow")
                || blockKey.equals("minecraft:powder_snow")
                || Set.of("minecraft:short_grass", "minecraft:poppy",
                        "minecraft:dandelion", "minecraft:brown_mushroom",
                        "minecraft:red_mushroom", "minecraft:fern", "minecraft:bush",
                        "minecraft:dead_bush", "minecraft:red_shrub",
                        "minecraft:firefly_bush", "minecraft:short_dry_grass",
                        "minecraft:tall_dry_grass", "minecraft:sweet_berry_bush",
                        "minecraft:wildflowers", "minecraft:leaf_litter",
                        "minecraft:sugar_cane", "minecraft:cactus_flower",
                        "minecraft:seagrass", "minecraft:tall_seagrass")
                        .contains(blockKey)
                || Set.of("minecraft:blue_orchid", "minecraft:closed_eyeblossom",
                        "minecraft:large_fern", "minecraft:pink_petals",
                        "minecraft:sunflower", "minecraft:tall_grass", "minecraft:lilac",
                        "minecraft:rose_bush", "minecraft:peony",
                        "minecraft:lily_of_the_valley", "minecraft:allium",
                        "minecraft:azure_bluet", "minecraft:red_tulip",
                        "minecraft:orange_tulip", "minecraft:white_tulip",
                        "minecraft:pink_tulip", "minecraft:oxeye_daisy",
                        "minecraft:cornflower", "minecraft:hanging_roots")
                        .contains(blockKey) || blockKey.equals("minecraft:vine");
    }

    /**
     * Exact catalog intersection of the pinned {@code #minecraft:fire} tag, which vanilla defines
     * as {@code minecraft:fire} and {@code minecraft:soul_fire}. Only {@code soul_fire} is
     * representable here; {@code minecraft:fire} has no carrier state.
     */
    public boolean isFire() {
        return blockKey.equals("minecraft:soul_fire");
    }

    public boolean regularSculkReplaceable() {
        return SCULK_REPLACEABLE.contains(blockKey);
    }

    public boolean worldgenSculkReplaceable() {
        return regularSculkReplaceable() || blockKey.equals("minecraft:cobbled_deepslate");
    }

    public boolean sculkGrowthInhibitor() {
        return blockKey.equals("minecraft:sculk_sensor")
                || blockKey.equals("minecraft:sculk_shrieker");
    }

    /** Canonical full-face occlusion shape for the requested face; unknown shapes fail closed. */
    public boolean isFaceOcclusionFull(OcclusionFace face) {
        Objects.requireNonNull(face, "face");
        if (solidRender) return true;
        return face == OcclusionFace.DOWN
                && (blockKey.equals("minecraft:snow")
                || blockKey.equals("minecraft:sandstone_slab"));
    }

    public enum OcclusionFace {
        DOWN, UP, NORTH, SOUTH, WEST, EAST
    }

    /** Exact transitive contents of the pinned {@code azalea_root_replaceable} block tag. */
    public boolean azaleaRootReplaceable() {
        return AZALEA_ROOT_REPLACEABLE.contains(blockKey);
    }

    public boolean featuresCannotReplace() {
        return FEATURES_CANNOT_REPLACE.contains(blockKey);
    }

    public boolean lavaPoolStoneCannotReplace() {
        return FEATURES_CANNOT_REPLACE.contains(blockKey) || LOGS.contains(blockKey)
                || LEAVES.contains(blockKey);
    }

    /** Exact pinned leaves-tag membership used by MOTION_BLOCKING_NO_LEAVES. */
    public boolean isLeaves() {
        return LEAVES.contains(blockKey);
    }

    /** Exact pinned tag used before the snow layer's sturdy-face fallback. */
    public boolean cannotSupportSnowLayer() {
        return blockKey.equals("minecraft:ice") || blockKey.equals("minecraft:packed_ice");
    }

    /** Exact current-catalog intersection of {@code support_override_snow_layer}. */
    public boolean supportOverrideSnowLayer() {
        return blockKey.equals("minecraft:mud");
    }

    /** Exact non-empty collision face fact required by sea-pickle survival. */
    public boolean isCollisionFaceNonEmpty(OcclusionFace face) {
        Objects.requireNonNull(face, "face");
        if (isCollisionShapeFullBlock()) return true;
        return face == OcclusionFace.UP && (blockKey.equals("minecraft:farmland")
                || blockKey.equals("minecraft:mud") || blockKey.equals("minecraft:cactus"));
    }

    /** Exact pinned {@code blocks_motion_in_heightmap_no_leaves} membership. */
    public boolean blocksMotionInHeightmapNoLeaves() {
        return BLOCKS_MOTION_NO_LEAVES.contains(blockKey);
    }

    public boolean geodeInvalid() {
        return GEODE_INVALID.contains(blockKey);
    }

    public String chestFacing() {
        if (capability != Capability.RANDOMIZABLE_CONTAINER) {
            throw new IllegalStateException("state is not a randomizable container: " + exactState);
        }
        return parseProperties(exactState).get("facing");
    }

    int heightmapMask(boolean worldSurface, boolean oceanFloor, boolean motionBlocking) {
        int mask = worldSurface ? HEIGHTMAP_WORLD_SURFACE : 0;
        if (oceanFloor) mask |= HEIGHTMAP_OCEAN_FLOOR;
        if (motionBlocking || fluidKind != FluidKind.NONE) {
            mask |= HEIGHTMAP_MOTION_BLOCKING;
        }
        return mask;
    }

    private static String canonicalize(String input) {
        String memoized = input == null ? null : CANONICAL_SPELLINGS.get(input);
        if (memoized != null) return memoized;
        String canonical = canonicalizeUncached(input);
        if (CANONICAL_SPELLINGS.size() < SPELLING_MEMO_BOUND) {
            CANONICAL_SPELLINGS.putIfAbsent(input, canonical);
        }
        return canonical;
    }

    private static String canonicalizeUncached(String input) {
        requireAsciiStateSyntax(input);
        Mc263FeatureBlockState registered = BY_EXACT.get(input);
        if (registered != null) return registered.exactState;
        Mc263FeatureBlockState reordered = BY_SORTED_IDENTITY.get(propertySortedIdentity(input));
        if (reordered != null) return reordered.exactState;
        try {
            return canonicalizeInternalForm(input);
        } catch (IllegalArgumentException unsupported) {
            // The pinned closure spells states in vanilla's complete form. Where the internal
            // catalog keeps the bare state instead, fold onto it rather than claiming a second
            // exact code for one 26.3 block state.
            String bare = Mc263VanillaCanonicalState.internalBareForVanillaCompletion(input);
            if (bare != null && BY_EXACT.containsKey(bare)) return bare;
            throw unsupported;
        }
    }

    private static String canonicalizeInternalForm(String input) {
        String block = blockKey(input);
        Map<String, String> properties = parseProperties(input);
        return switch (block) {
            case "minecraft:barrier" -> properties.isEmpty()
                    ? "minecraft:barrier[waterlogged=false]"
                    : canonicalFixedProperties(block, properties, Map.of("waterlogged", "false"));
            case "minecraft:water" -> canonicalFluid(block, properties, 15);
            case "minecraft:lava" -> canonicalFluid(block, properties, 8);
            case "minecraft:chest", "minecraft:oxidized_copper_chest" ->
                    canonicalChest(block, properties);
            case "minecraft:barrel" -> canonicalCampBarrel(block, properties, input);
            case "minecraft:small_amethyst_bud", "minecraft:medium_amethyst_bud",
                    "minecraft:large_amethyst_bud", "minecraft:amethyst_cluster" ->
                    canonicalBud(block, properties);
            case "minecraft:sulfur_spike", "minecraft:pointed_dripstone" ->
                    canonicalSulfurSpike(block, properties);
            case "minecraft:potent_sulfur" -> canonicalPotentSulfur(block, properties);
            case "minecraft:sculk_vein" -> canonicalSculkVein(block, properties);
            case "minecraft:glow_lichen" -> canonicalGlowLichen(block, properties);
            case "minecraft:vine" -> canonicalVine(block, properties);
            case "minecraft:brown_mushroom_block", "minecraft:red_mushroom_block",
                    "minecraft:mushroom_stem" ->
                    canonicalHugeMushroom(block, properties, input);
            case "minecraft:oak_leaves", "minecraft:birch_leaves",
                    "minecraft:spruce_leaves", "minecraft:jungle_leaves",
                    "minecraft:acacia_leaves", "minecraft:dark_oak_leaves",
                    "minecraft:cherry_leaves", "minecraft:mangrove_leaves",
                    "minecraft:pale_oak_leaves", "minecraft:yellow_poplar_leaves",
                    "minecraft:orange_poplar_leaves", "minecraft:red_poplar_leaves",
                    "minecraft:azalea_leaves", "minecraft:flowering_azalea_leaves" ->
                    canonicalLeaves(block, properties, input);
            case "minecraft:sculk_catalyst" -> canonicalFixedProperties(block, properties,
                    Map.of("bloom", "false"));
            case "minecraft:sculk_sensor" -> canonicalSculkSensor(block, properties);
            case "minecraft:sculk_shrieker" -> canonicalSculkShrieker(block, properties);
            case "minecraft:bone_block", "minecraft:oak_log", "minecraft:birch_log",
                    "minecraft:spruce_log", "minecraft:jungle_log", "minecraft:acacia_log",
                    "minecraft:dark_oak_log", "minecraft:cherry_log", "minecraft:mangrove_log",
                    "minecraft:pale_oak_log", "minecraft:poplar_log",
                    "minecraft:stripped_oak_log", "minecraft:stripped_birch_log",
                    "minecraft:stripped_spruce_log", "minecraft:stripped_jungle_log",
                    "minecraft:stripped_acacia_log", "minecraft:stripped_dark_oak_log",
                    "minecraft:stripped_cherry_log", "minecraft:stripped_mangrove_log",
                    "minecraft:stripped_pale_oak_log", "minecraft:stripped_poplar_log",
                    "minecraft:poplar_wood", "minecraft:stripped_poplar_wood",
                    "minecraft:acacia_wood", "minecraft:stripped_oak_wood",
                    "minecraft:stripped_spruce_wood",
                    "minecraft:muddy_mangrove_roots", "minecraft:chain",
                    "minecraft:hay_block" ->
                    canonicalAxis(block, properties);
            case "minecraft:oak_fence", "minecraft:birch_fence",
                    "minecraft:spruce_fence", "minecraft:jungle_fence",
                    "minecraft:acacia_fence", "minecraft:cherry_fence",
                    "minecraft:dark_oak_fence", "minecraft:pale_oak_fence",
                    "minecraft:poplar_fence", "minecraft:bamboo_fence" ->
                    canonicalFence(block, properties, input);
            case "minecraft:cobblestone_stairs", "minecraft:mossy_cobblestone_stairs",
                    "minecraft:stone_brick_stairs", "minecraft:brick_stairs",
                    "minecraft:mud_brick_stairs", "minecraft:mossy_stone_brick_stairs",
                    "minecraft:oak_stairs", "minecraft:birch_stairs",
                    "minecraft:acacia_stairs",
                    "minecraft:diorite_stairs", "minecraft:smooth_sandstone_stairs",
                    "minecraft:white_wool_stairs" ->
                    canonicalStairs(block, properties, input);
            case "minecraft:spruce_stairs" ->
                    canonicalSpruceStairs(block, properties, input);
            case "minecraft:oak_door", "minecraft:spruce_door",
                    "minecraft:jungle_door", "minecraft:acacia_door",
                    "minecraft:iron_door", "minecraft:stone_button",
                    "minecraft:stone_brick_slab", "minecraft:smooth_stone_slab",
                    "minecraft:end_portal_frame" ->
                    canonicalStrongholdState(block, properties, input);
            case "minecraft:stone_slab", "minecraft:sandstone_slab",
                    "minecraft:spruce_slab", "minecraft:acacia_slab",
                    "minecraft:smooth_sandstone_slab" ->
                    canonicalTrailSlab(block, properties, input);
            case "minecraft:brick_slab", "minecraft:mud_brick_slab",
                    "minecraft:mossy_stone_brick_slab" ->
                    canonicalTrailSlab(block, properties, input);
            case "minecraft:brick_wall", "minecraft:mud_brick_wall",
                    "minecraft:stone_brick_wall", "minecraft:mossy_stone_brick_wall",
                    "minecraft:cobblestone_wall", "minecraft:mossy_cobblestone_wall",
                    "minecraft:diorite_wall", "minecraft:sandstone_wall" ->
                    canonicalTrailWall(block, properties, input);
            case "minecraft:iron_bars" -> canonicalTrailIronBars(block, properties, input);
            case "minecraft:ladder" -> canonicalTrailLadder(block, properties, input);
            case "minecraft:furnace", "minecraft:blast_furnace" ->
                    canonicalTrailFurnace(block, properties, input);
            case "minecraft:campfire" -> canonicalTrailCampfire(block, properties, input);
            case "minecraft:grindstone" -> canonicalTrailGrindstone(block, properties, input);
            case "minecraft:white_wall_banner", "minecraft:brown_wall_banner" ->
                    canonicalRequiredHorizontalFacing(block, properties, input);
            case "minecraft:loom", "minecraft:damaged_anvil",
                    "minecraft:orange_glazed_terracotta",
                    "minecraft:light_blue_glazed_terracotta",
                    "minecraft:yellow_glazed_terracotta",
                    "minecraft:light_gray_glazed_terracotta",
                    "minecraft:cyan_glazed_terracotta",
                    "minecraft:purple_glazed_terracotta",
                    "minecraft:black_glazed_terracotta",
                    "minecraft:white_glazed_terracotta", "minecraft:stonecutter" ->
                    canonicalHorizontalFacing(block, properties, input);
            case "minecraft:wall_torch" -> canonicalHorizontalFacing(block, properties, input);
            case "minecraft:rail" -> canonicalRail(block, properties, input);
            case "minecraft:tripwire_hook" -> canonicalTripwireHook(block, properties, input);
            case "minecraft:tripwire" -> canonicalTripwire(block, properties, input);
            case "minecraft:dispenser" -> canonicalDispenser(block, properties, input);
            case "minecraft:lever" -> canonicalLever(block, properties, input);
            case "minecraft:redstone_wire" -> canonicalRedstoneWire(block, properties, input);
            case "minecraft:repeater" -> canonicalRepeater(block, properties, input);
            case "minecraft:sticky_piston" -> canonicalStickyPiston(block, properties, input);
            case "minecraft:suspicious_sand" -> canonicalFixedProperties(block, properties,
                    Map.of("dusted", "0"));
            case "minecraft:suspicious_gravel" -> canonicalFixedProperties(block, properties,
                    Map.of("dusted", "0"));
            case "minecraft:farmland" -> canonicalBoundedInteger(block, properties,
                    "moisture", 0, 7, input);
            case "minecraft:grass_block", "minecraft:podzol", "minecraft:mycelium" ->
                    properties.isEmpty() ? block + "[snowy=false]"
                            : canonicalSnowy(block, properties);
            case "minecraft:snow" -> properties.isEmpty() ? block + "[layers=1]"
                    : canonicalBoundedInteger(block, properties, "layers", 1, 8, input);
            case "minecraft:wheat", "minecraft:carrots", "minecraft:potatoes",
                    "minecraft:pumpkin_stem", "minecraft:melon_stem" ->
                    canonicalBoundedInteger(block, properties, "age", 0, 7, input);
            case "minecraft:beetroots" ->
                    canonicalBoundedInteger(block, properties, "age", 0, 3, input);
            case "minecraft:cherry_sapling", "minecraft:jungle_sapling",
                    "minecraft:acacia_sapling" ->
                    canonicalBoundedInteger(block, properties, "stage", 0, 1, input);
            case "minecraft:oak_sapling", "minecraft:birch_sapling",
                    "minecraft:spruce_sapling", "minecraft:dark_oak_sapling",
                    "minecraft:pale_oak_sapling", "minecraft:poplar_sapling" ->
                    properties.isEmpty() ? block
                            : canonicalBoundedInteger(block, properties, "stage", 0, 1, input);
            case "minecraft:water_cauldron" ->
                    canonicalBoundedInteger(block, properties, "level", 1, 3, input);
            case "minecraft:oak_trapdoor", "minecraft:spruce_trapdoor" ->
                    canonicalVillageTrapdoor(block, properties, input);
            case "minecraft:oak_fence_gate" ->
                    canonicalVillageFenceGate(block, properties, input);
            case "minecraft:bell" -> canonicalVillageBell(block, properties, input);
            case "minecraft:lantern" -> canonicalVillageLantern(block, properties, input);
            case "minecraft:lectern" -> canonicalVillageLectern(block, properties, input);
            case "minecraft:smoker" -> canonicalVillageSmoker(block, properties, input);
            case "minecraft:white_stained_glass_pane",
                    "minecraft:yellow_stained_glass_pane" ->
                    canonicalVillagePane(block, properties, input);
            case "minecraft:oxidized_copper_lantern" ->
                    canonicalCampCopperLantern(block, properties, input);
            case "minecraft:red_bed" -> canonicalTrialRedBed(block, properties, input);
            case "minecraft:straw_bed" -> canonicalCampBed(block, properties, input);
            case "minecraft:oxidized_copper_golem_statue" ->
                    canonicalCampCopperStatue(block, properties, input);
            case "minecraft:sea_pickle" -> canonicalSeaPickle(block, properties, input);
            case "minecraft:tube_coral", "minecraft:brain_coral",
                    "minecraft:bubble_coral", "minecraft:fire_coral",
                    "minecraft:horn_coral", "minecraft:tube_coral_fan",
                    "minecraft:brain_coral_fan", "minecraft:bubble_coral_fan",
                    "minecraft:fire_coral_fan", "minecraft:horn_coral_fan" ->
                    canonicalFixedProperties(block, properties, Map.of("waterlogged", "true"));
            case "minecraft:tube_coral_wall_fan", "minecraft:brain_coral_wall_fan",
                    "minecraft:bubble_coral_wall_fan", "minecraft:fire_coral_wall_fan",
                    "minecraft:horn_coral_wall_fan" -> canonicalCoralWall(block, properties);
            case "minecraft:sweet_berry_bush" -> canonicalFixedProperties(block, properties,
                    Map.of("age", "3"));
            case "minecraft:sugar_cane", "minecraft:cactus" ->
                    properties.isEmpty() ? block + "[age=0]"
                            : canonicalFixedProperties(block, properties, Map.of("age", "0"));
            case "minecraft:kelp" -> {
                if (properties.isEmpty()) yield block + "[age=0]";
                if (properties.size() != 1 || !properties.containsKey("age")) {
                    throw new IllegalArgumentException("unsupported kelp state: " + input);
                }
                int age;
                try {
                    age = Integer.parseInt(properties.get("age"));
                } catch (NumberFormatException invalidAge) {
                    throw new IllegalArgumentException("unsupported kelp state: " + input,
                            invalidAge);
                }
                if (age < 0 || age > 25 || !Integer.toString(age).equals(properties.get("age"))) {
                    throw new IllegalArgumentException("unsupported kelp state: " + input);
                }
                yield block + "[age=" + age + "]";
            }
            case "minecraft:tall_seagrass" -> {
                if (properties.isEmpty()) yield block + "[half=lower]";
                String half = properties.get("half");
                if (properties.size() != 1
                        || !("lower".equals(half) || "upper".equals(half))) {
                    throw new IllegalArgumentException(
                            "unsupported tall-seagrass state: " + input);
                }
                yield block + "[half=" + half + "]";
            }
            case "minecraft:bamboo" -> canonicalBamboo(block, properties, input);
            case "minecraft:pale_moss_carpet" ->
                    canonicalPaleMossCarpet(block, properties, input);
            case "minecraft:cave_vines" -> canonicalCaveVines(block, properties, input);
            case "minecraft:cave_vines_plant" ->
                    canonicalBooleanProperty(block, properties, "berries", false, input);
            case "minecraft:hanging_roots" ->
                    canonicalBooleanProperty(block, properties, "waterlogged", false, input);
            case "minecraft:small_dripleaf" ->
                    canonicalSmallDripleaf(block, properties, input);
            case "minecraft:big_dripleaf_stem" ->
                    canonicalBigDripleafStem(block, properties, input);
            case "minecraft:big_dripleaf" ->
                    canonicalBigDripleaf(block, properties, input);
            case "minecraft:mangrove_roots" ->
                    canonicalBooleanProperty(block, properties, "waterlogged", false, input);
            case "minecraft:pale_hanging_moss" ->
                    canonicalBooleanProperty(block, properties, "tip", true, input);
            case "minecraft:cocoa" -> canonicalCocoa(block, properties, input);
            case "minecraft:bee_nest" -> canonicalBeeNest(block, properties, input);
            case "minecraft:shelf_mushroom" -> canonicalShelfMushroom(block, properties, input);
            case "minecraft:creaking_heart" -> canonicalCreakingHeart(block, properties, input);
            case "minecraft:mangrove_propagule" ->
                    canonicalMangrovePropagule(block, properties, input);
            case "minecraft:large_fern", "minecraft:sunflower", "minecraft:tall_grass",
                    "minecraft:lilac", "minecraft:rose_bush", "minecraft:peony" -> {
                if (properties.isEmpty()) yield block + "[half=lower]";
                String half = properties.get("half");
                if (properties.size() != 1
                        || !("lower".equals(half) || "upper".equals(half))) {
                    throw new IllegalArgumentException("unsupported double-plant state: "
                            + input);
                }
                yield block + "[half=" + half + "]";
            }
            case "minecraft:wildflowers" -> canonicalFlowerBed(block, properties,
                    "flower_amount", 4);
            case "minecraft:pink_petals" -> canonicalFlowerBed(block, properties,
                    "flower_amount", 4);
            case "minecraft:leaf_litter" -> canonicalFlowerBed(block, properties,
                    "segment_amount", 4);
            default -> {
                if (!properties.isEmpty()) {
                    throw new IllegalArgumentException(
                            "unsupported properties in FEATURES state: " + input);
                }
                yield block;
            }
        };
    }

    private static String canonicalBamboo(String block, Map<String, String> properties,
            String input) {
        if (properties.isEmpty()) return block + "[age=0,leaves=none,stage=0]";
        String age = properties.get("age");
        String leaves = properties.get("leaves");
        String stage = properties.get("stage");
        if (properties.size() != 3 || !("0".equals(age) || "1".equals(age))
                || !("none".equals(leaves) || "small".equals(leaves)
                || "large".equals(leaves))
                || !("0".equals(stage) || "1".equals(stage))) {
            throw new IllegalArgumentException("unsupported bamboo state: " + input);
        }
        return block + "[age=" + age + ",leaves=" + leaves + ",stage=" + stage + "]";
    }

    private static String canonicalPaleMossCarpet(String block,
            Map<String, String> properties, String input) {
        if (properties.isEmpty()) {
            return block + "[bottom=true,east=none,north=none,south=none,west=none]";
        }
        if (properties.size() != 5
                || !Set.of("false", "true").contains(properties.get("bottom"))) {
            throw new IllegalArgumentException("unsupported pale-moss-carpet state: " + input);
        }
        for (String side : List.of("east", "north", "south", "west")) {
            if (!Set.of("none", "low", "tall").contains(properties.get(side))) {
                throw new IllegalArgumentException(
                        "unsupported pale-moss-carpet state: " + input);
            }
        }
        return block + "[bottom=" + properties.get("bottom")
                + ",east=" + properties.get("east") + ",north=" + properties.get("north")
                + ",south=" + properties.get("south") + ",west=" + properties.get("west")
                + "]";
    }

    private static String canonicalCaveVines(String block, Map<String, String> properties,
            String input) {
        if (properties.isEmpty()) return block + "[age=0,berries=false]";
        if (properties.size() != 2 || !properties.containsKey("age")
                || !Set.of("false", "true").contains(properties.get("berries"))) {
            throw new IllegalArgumentException("unsupported cave-vines state: " + input);
        }
        int age = parseRange(properties.get("age"), 0, 25, "cave-vines age");
        if (!Integer.toString(age).equals(properties.get("age"))) {
            throw new IllegalArgumentException("unsupported cave-vines state: " + input);
        }
        return block + "[age=" + age + ",berries=" + properties.get("berries") + "]";
    }

    private static String canonicalBooleanProperty(String block,
            Map<String, String> properties, String property, boolean defaultValue, String input) {
        if (properties.isEmpty()) return block + "[" + property + "=" + defaultValue + "]";
        String value = properties.get(property);
        if (properties.size() != 1 || !("false".equals(value) || "true".equals(value))) {
            throw new IllegalArgumentException("unsupported " + block + " state: " + input);
        }
        return block + "[" + property + "=" + value + "]";
    }

    private static String canonicalSmallDripleaf(String block,
            Map<String, String> properties, String input) {
        if (properties.isEmpty()) {
            return block + "[facing=north,half=lower,waterlogged=false]";
        }
        String facing = properties.get("facing");
        String half = properties.get("half");
        if (properties.size() != 3
                || !Set.of("north", "east", "south", "west").contains(facing)
                || !Set.of("lower", "upper").contains(half)
                || !"false".equals(properties.get("waterlogged"))) {
            throw new IllegalArgumentException("unsupported small-dripleaf state: " + input);
        }
        return block + "[facing=" + facing + ",half=" + half + ",waterlogged=false]";
    }

    private static String canonicalBigDripleafStem(String block,
            Map<String, String> properties, String input) {
        if (properties.isEmpty()) return block + "[facing=north,waterlogged=false]";
        String facing = properties.get("facing");
        if (properties.size() != 2
                || !Set.of("north", "east", "south", "west").contains(facing)
                || !"false".equals(properties.get("waterlogged"))) {
            throw new IllegalArgumentException("unsupported big-dripleaf-stem state: " + input);
        }
        return block + "[facing=" + facing + ",waterlogged=false]";
    }

    private static String canonicalBigDripleaf(String block,
            Map<String, String> properties, String input) {
        if (properties.isEmpty()) {
            return block + "[facing=north,tilt=none,waterlogged=false]";
        }
        String facing = properties.get("facing");
        if (properties.size() != 3
                || !Set.of("north", "east", "south", "west").contains(facing)
                || !"none".equals(properties.get("tilt"))
                || !"false".equals(properties.get("waterlogged"))) {
            throw new IllegalArgumentException("unsupported big-dripleaf state: " + input);
        }
        return block + "[facing=" + facing + ",tilt=none,waterlogged=false]";
    }

    private static String canonicalFluid(String block, Map<String, String> properties,
            int maxLevel) {
        if (properties.isEmpty()) return block;
        if (properties.size() != 1 || !properties.containsKey("level")) {
            throw new IllegalArgumentException("unsupported fluid state: " + block + properties);
        }
        int level = parseRange(properties.get("level"), 0, maxLevel, "fluid level");
        return level == 0 ? block : block + "[level=" + level + "]";
    }

    private static String canonicalAxis(String block, Map<String, String> properties) {
        if (properties.isEmpty()) return block + "[axis=y]";
        String axis = properties.get("axis");
        if (properties.size() != 1
                || !("x".equals(axis) || "y".equals(axis) || "z".equals(axis))) {
            throw new IllegalArgumentException("unsupported axis state: " + block + properties);
        }
        return block + "[axis=" + axis + "]";
    }

    private static String canonicalCocoa(String block, Map<String, String> properties,
            String input) {
        if (properties.isEmpty()) return block + "[age=0,facing=north]";
        String facing = properties.get("facing");
        if (properties.size() != 2
                || !Set.of("north", "east", "south", "west").contains(facing)) {
            throw new IllegalArgumentException("unsupported cocoa state: " + input);
        }
        int age = parseRange(properties.get("age"), 0, 2, "cocoa age");
        return block + "[age=" + age + ",facing=" + facing + "]";
    }

    private static String canonicalBeeNest(String block, Map<String, String> properties,
            String input) {
        if (properties.isEmpty()) return block + "[facing=north,honey_level=0]";
        String facing = properties.get("facing");
        if (properties.size() != 2
                || !Set.of("north", "east", "south", "west").contains(facing)) {
            throw new IllegalArgumentException("unsupported bee-nest state: " + input);
        }
        int honey = parseRange(properties.get("honey_level"), 0, 5, "bee-nest honey level");
        return block + "[facing=" + facing + ",honey_level=" + honey + "]";
    }

    private static String canonicalShelfMushroom(String block,
            Map<String, String> properties, String input) {
        if (properties.isEmpty()) return block + "[age=0,facing=north]";
        String facing = properties.get("facing");
        if (properties.size() != 2
                || !Set.of("north", "east", "south", "west").contains(facing)) {
            throw new IllegalArgumentException("unsupported shelf-mushroom state: " + input);
        }
        int age = parseRange(properties.get("age"), 0, 3, "shelf-mushroom age");
        return block + "[age=" + age + ",facing=" + facing + "]";
    }

    private static String canonicalCreakingHeart(String block,
            Map<String, String> properties, String input) {
        if (properties.isEmpty()) {
            return block + "[axis=y,creaking_heart_state=uprooted,natural=false]";
        }
        String axis = properties.get("axis");
        String state = properties.get("creaking_heart_state");
        String natural = properties.get("natural");
        if (properties.size() != 3 || !Set.of("x", "y", "z").contains(axis)
                || !Set.of("uprooted", "dormant", "awake").contains(state)
                || !Set.of("false", "true").contains(natural)) {
            throw new IllegalArgumentException("unsupported creaking-heart state: " + input);
        }
        return block + "[axis=" + axis + ",creaking_heart_state=" + state
                + ",natural=" + natural + "]";
    }

    private static String canonicalMangrovePropagule(String block,
            Map<String, String> properties, String input) {
        if (properties.isEmpty()) {
            return block + "[age=0,hanging=false,stage=0,waterlogged=false]";
        }
        if (properties.size() != 4
                || !Set.of("false", "true").contains(properties.get("hanging"))
                || !Set.of("0", "1").contains(properties.get("stage"))
                || !Set.of("false", "true").contains(properties.get("waterlogged"))) {
            throw new IllegalArgumentException("unsupported mangrove-propagule state: " + input);
        }
        int age = parseRange(properties.get("age"), 0, 4, "mangrove-propagule age");
        return block + "[age=" + age + ",hanging=" + properties.get("hanging")
                + ",stage=" + properties.get("stage") + ",waterlogged="
                + properties.get("waterlogged") + "]";
    }

    private static String canonicalChest(String block, Map<String, String> properties) {
        String facing = properties.get("facing");
        String waterlogged = properties.get("waterlogged");
        if (properties.size() != 3 || !Set.of("north", "east", "south", "west").contains(facing)
                || !Set.of("single", "left", "right").contains(properties.get("type"))
                || !Set.of("false", "true").contains(waterlogged)) {
            throw new IllegalArgumentException("unsupported chest state: " + block + properties);
        }
        return block + "[facing=" + facing + ",type=" + properties.get("type")
                + ",waterlogged="
                + waterlogged + "]";
    }

    private static String canonicalBud(String block, Map<String, String> properties) {
        String facing = properties.get("facing");
        String waterlogged = properties.get("waterlogged");
        if (properties.size() != 2 || !DIRECTIONS.contains(facing)
                || !("true".equals(waterlogged) || "false".equals(waterlogged))) {
            throw new IllegalArgumentException("unsupported amethyst bud state: "
                    + block + properties);
        }
        return block + "[facing=" + facing + ",waterlogged=" + waterlogged + "]";
    }

    private static String canonicalSulfurSpike(String block, Map<String, String> properties) {
        if (properties.isEmpty()) {
            return block
                    + "[thickness=tip,vertical_direction=up,waterlogged=false]";
        }
        String thickness = properties.get("thickness");
        String direction = properties.get("vertical_direction");
        String waterlogged = properties.get("waterlogged");
        if (properties.size() != 3
                || !Set.of("tip", "tip_merge", "frustum", "middle", "base")
                        .contains(thickness)
                || !Set.of("down", "up").contains(direction)
                || !("true".equals(waterlogged) || "false".equals(waterlogged))) {
            throw new IllegalArgumentException("unsupported sulfur-spike state: "
                    + block + properties);
        }
        return block + "[thickness=" + thickness + ",vertical_direction=" + direction
                + ",waterlogged=" + waterlogged + "]";
    }

    private static String canonicalPotentSulfur(String block,
            Map<String, String> properties) {
        String value = properties.get("potent_sulfur_state");
        if (properties.size() != 1
                || !Set.of("dry", "wet", "dormant", "erupting", "continuous")
                        .contains(value)) {
            throw new IllegalArgumentException("unsupported potent-sulfur state: "
                    + block + properties);
        }
        return block + "[potent_sulfur_state=" + value + "]";
    }

    private static String canonicalSculkVein(String block,
            Map<String, String> properties) {
        if (properties.size() != 7
                || !properties.keySet().equals(Set.of(
                        "down", "east", "north", "south", "up", "waterlogged", "west"))) {
            throw new IllegalArgumentException("unsupported sculk-vein state: "
                    + block + properties);
        }
        int faceMask = booleanBit(properties, "down", 0)
                | booleanBit(properties, "up", 1)
                | booleanBit(properties, "north", 2)
                | booleanBit(properties, "south", 3)
                | booleanBit(properties, "west", 4)
                | booleanBit(properties, "east", 5);
        boolean waterlogged = booleanValue(properties, "waterlogged");
        return sculkVeinState(faceMask, waterlogged);
    }

    private static String canonicalGlowLichen(String block,
            Map<String, String> properties) {
        if (properties.size() != 7
                || !properties.keySet().equals(Set.of(
                        "down", "east", "north", "south", "up", "waterlogged", "west"))) {
            throw new IllegalArgumentException("unsupported glow-lichen state: "
                    + block + properties);
        }
        int faceMask = booleanBit(properties, "down", 0)
                | booleanBit(properties, "up", 1)
                | booleanBit(properties, "north", 2)
                | booleanBit(properties, "south", 3)
                | booleanBit(properties, "west", 4)
                | booleanBit(properties, "east", 5);
        return glowLichenState(faceMask, booleanValue(properties, "waterlogged"));
    }

    private static String canonicalVine(String block, Map<String, String> properties) {
        if (properties.size() != 5
                || !properties.keySet().equals(Set.of(
                        "east", "north", "south", "up", "west"))) {
            throw new IllegalArgumentException("unsupported vine state: " + block + properties);
        }
        int faceMask = booleanBit(properties, "up", 0)
                | booleanBit(properties, "north", 1)
                | booleanBit(properties, "south", 2)
                | booleanBit(properties, "west", 3)
                | booleanBit(properties, "east", 4);
        return vineState(faceMask);
    }

    private static String canonicalHugeMushroom(String block,
            Map<String, String> properties, String input) {
        if (properties.isEmpty()) {
            return block
                    + "[down=true,east=true,north=true,south=true,up=true,west=true]";
        }
        List<String> names = List.of("down", "east", "north", "south", "up", "west");
        if (properties.size() != names.size() || !properties.keySet().containsAll(names)) {
            throw new IllegalArgumentException("unsupported huge-mushroom state: " + input);
        }
        for (String name : names) {
            if (!Set.of("false", "true").contains(properties.get(name))) {
                throw new IllegalArgumentException("unsupported huge-mushroom state: " + input);
            }
        }
        return block + "[down=" + properties.get("down") + ",east="
                + properties.get("east") + ",north=" + properties.get("north")
                + ",south=" + properties.get("south") + ",up=" + properties.get("up")
                + ",west=" + properties.get("west") + "]";
    }

    private static int booleanBit(Map<String, String> properties, String name, int bit) {
        return booleanValue(properties, name) ? 1 << bit : 0;
    }

    private static boolean booleanValue(Map<String, String> properties, String name) {
        return switch (properties.get(name)) {
            case "true" -> true;
            case "false" -> false;
            default -> throw new IllegalArgumentException(
                    "non-boolean block-state property " + name + ": " + properties.get(name));
        };
    }

    private static String sculkVeinState(int faceMask, boolean waterlogged) {
        return "minecraft:sculk_vein[down=" + ((faceMask & 1) != 0)
                + ",east=" + ((faceMask & 32) != 0)
                + ",north=" + ((faceMask & 4) != 0)
                + ",south=" + ((faceMask & 8) != 0)
                + ",up=" + ((faceMask & 2) != 0)
                + ",waterlogged=" + waterlogged
                + ",west=" + ((faceMask & 16) != 0) + "]";
    }

    private static String glowLichenState(int faceMask, boolean waterlogged) {
        return "minecraft:glow_lichen[down=" + ((faceMask & 1) != 0)
                + ",east=" + ((faceMask & 32) != 0)
                + ",north=" + ((faceMask & 4) != 0)
                + ",south=" + ((faceMask & 8) != 0)
                + ",up=" + ((faceMask & 2) != 0)
                + ",waterlogged=" + waterlogged
                + ",west=" + ((faceMask & 16) != 0) + "]";
    }

    private static String vineState(int faceMask) {
        return "minecraft:vine[east=" + ((faceMask & 16) != 0)
                + ",north=" + ((faceMask & 2) != 0)
                + ",south=" + ((faceMask & 4) != 0)
                + ",up=" + ((faceMask & 1) != 0)
                + ",west=" + ((faceMask & 8) != 0) + "]";
    }

    private static String canonicalSculkSensor(String block, Map<String, String> properties) {
        if (properties.size() != 3 || !"0".equals(properties.get("power"))
                || !"inactive".equals(properties.get("sculk_sensor_phase"))
                || !("false".equals(properties.get("waterlogged"))
                || "true".equals(properties.get("waterlogged")))) {
            throw new IllegalArgumentException("unsupported sculk-sensor state: "
                    + block + properties);
        }
        return block + "[power=0,sculk_sensor_phase=inactive,waterlogged="
                + properties.get("waterlogged") + "]";
    }

    private static String canonicalSculkShrieker(String block, Map<String, String> properties) {
        if (properties.size() != 3
                || !("false".equals(properties.get("can_summon"))
                || "true".equals(properties.get("can_summon")))
                || !"false".equals(properties.get("shrieking"))
                || !("false".equals(properties.get("waterlogged"))
                || "true".equals(properties.get("waterlogged")))) {
            throw new IllegalArgumentException("unsupported sculk-shrieker state: "
                    + block + properties);
        }
        return block + "[can_summon=" + properties.get("can_summon")
                + ",shrieking=false,waterlogged="
                + properties.get("waterlogged") + "]";
    }

    private static String canonicalFixedProperties(String block,
            Map<String, String> properties, Map<String, String> expected) {
        if (!properties.equals(expected)) {
            throw new IllegalArgumentException("unsupported fixed state: "
                    + block + properties);
        }
        return block + "[" + expected.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(java.util.stream.Collectors.joining(",")) + "]";
    }

    private static String canonicalSnowy(String block, Map<String, String> properties) {
        String snowy = properties.get("snowy");
        if (properties.size() != 1 || !Set.of("false", "true").contains(snowy)) {
            throw new IllegalArgumentException("unsupported snowy state: " + block + properties);
        }
        return block + "[snowy=" + snowy + "]";
    }

    private static String canonicalSeaPickle(String block, Map<String, String> properties,
            String input) {
        if (properties.size() != 2
                || !Set.of("false", "true").contains(properties.get("waterlogged"))) {
            throw new IllegalArgumentException("unsupported sea-pickle state: " + input);
        }
        int pickles = parseRange(properties.get("pickles"), 1, 4, "sea-pickle count");
        return block + "[pickles=" + pickles + ",waterlogged="
                + properties.get("waterlogged") + "]";
    }

    private static String canonicalCoralWall(String block, Map<String, String> properties) {
        String facing = properties.get("facing");
        if (properties.size() != 2
                || !Set.of("north", "east", "south", "west").contains(facing)
                || !"true".equals(properties.get("waterlogged"))) {
            throw new IllegalArgumentException("unsupported coral-wall state: "
                    + block + properties);
        }
        return block + "[facing=" + facing + ",waterlogged=true]";
    }

    private static String canonicalLeaves(String block, Map<String, String> properties,
            String input) {
        if (properties.size() != 3
                || !Set.of("false", "true").contains(properties.get("persistent"))
                || !Set.of("false", "true").contains(properties.get("waterlogged"))) {
            throw new IllegalArgumentException("unsupported leaves state: " + input);
        }
        String distance = properties.get("distance");
        int parsed;
        try {
            parsed = Integer.parseInt(distance);
        } catch (RuntimeException invalid) {
            throw new IllegalArgumentException("unsupported leaves state: " + input, invalid);
        }
        if (parsed < 1 || parsed > 7 || !Integer.toString(parsed).equals(distance)) {
            throw new IllegalArgumentException("unsupported leaves state: " + input);
        }
        return block + "[distance=" + parsed + ",persistent=" + properties.get("persistent")
                + ",waterlogged=" + properties.get("waterlogged") + "]";
    }

    private static String canonicalBoundedInteger(String block,
            Map<String, String> properties, String property, int minimum, int maximum,
            String input) {
        if (properties.size() != 1 || !properties.containsKey(property)) {
            throw new IllegalArgumentException("unsupported bounded state: " + input);
        }
        String value = properties.get(property);
        int parsed;
        try {
            parsed = Integer.parseInt(value);
        } catch (NumberFormatException invalid) {
            throw new IllegalArgumentException("unsupported bounded state: " + input, invalid);
        }
        if (parsed < minimum || parsed > maximum || !Integer.toString(parsed).equals(value)) {
            throw new IllegalArgumentException("unsupported bounded state: " + input);
        }
        return block + "[" + property + "=" + parsed + "]";
    }

    private static String canonicalFlowerBed(String block, Map<String, String> properties,
            String amountProperty, int maximum) {
        if (properties.isEmpty()) {
            return block + "[facing=north," + amountProperty + "=1]";
        }
        String facing = properties.get("facing");
        if (properties.size() != 2
                || !Set.of("north", "east", "south", "west").contains(facing)) {
            throw new IllegalArgumentException("unsupported flower-bed state: "
                    + block + properties);
        }
        int amount = parseRange(properties.get(amountProperty), 1, maximum,
                amountProperty);
        return block + "[facing=" + facing + "," + amountProperty + "=" + amount + "]";
    }

    private static Map<String, String> parseProperties(String state) {
        int open = state.indexOf('[');
        if (open < 0) return Map.of();
        String body = state.substring(open + 1, state.length() - 1);
        Map<String, String> result = new TreeMap<>();
        for (String entry : body.split(",", -1)) {
            int equals = entry.indexOf('=');
            if (equals <= 0 || equals == entry.length() - 1
                    || entry.indexOf('=', equals + 1) >= 0) {
                throw new IllegalArgumentException("malformed block-state property: " + state);
            }
            String name = entry.substring(0, equals).strip();
            String value = entry.substring(equals + 1).strip();
            if (!isAsciiToken(name) || !isAsciiToken(value)
                    || result.putIfAbsent(name, value) != null) {
                throw new IllegalArgumentException("malformed block-state property: " + state);
            }
        }
        return result;
    }

    private static void requireAsciiStateSyntax(String state) {
        if (state == null || state.isEmpty()) {
            throw new IllegalArgumentException("exact Minecraft state is required: " + state);
        }
        int open = state.indexOf('[');
        String block = open < 0 ? state : state.substring(0, open);
        if (!isAsciiResourceKey(block) || (open >= 0
                && (!state.endsWith("]") || open == state.length() - 2
                || state.indexOf('[', open + 1) >= 0))) {
            throw new IllegalArgumentException("canonical ASCII Minecraft state is required: "
                    + state);
        }
    }

    private static boolean isAsciiResourceKey(String key) {
        if (key == null) return false;
        int colon = key.indexOf(':');
        if (colon <= 0 || colon == key.length() - 1 || key.indexOf(':', colon + 1) >= 0) {
            return false;
        }
        for (int index = 0; index < key.length(); index++) {
            char value = key.charAt(index);
            if (value > 0x7f) return false;
            if (index == colon) continue;
            boolean namespace = index < colon;
            if (!isLowerAlphaNumeric(value) && value != '_' && value != '-' && value != '.'
                    && (!namespace && value != '/')) return false;
        }
        return true;
    }

    private static boolean isAsciiToken(String token) {
        if (token.isEmpty()) return false;
        for (int index = 0; index < token.length(); index++) {
            char value = token.charAt(index);
            if (!isLowerAlphaNumeric(value) && value != '_' && value != '-') return false;
        }
        return true;
    }

    private static boolean isLowerAlphaNumeric(char value) {
        return value >= 'a' && value <= 'z' || value >= '0' && value <= '9';
    }

    private static int parseRange(String value, int minimum, int maximum, String description) {
        final int parsed;
        try {
            parsed = Integer.parseInt(value);
        } catch (NumberFormatException failure) {
            throw new IllegalArgumentException("invalid " + description + ": " + value, failure);
        }
        if (parsed < minimum || parsed > maximum) {
            throw new IllegalArgumentException("unrepresentable " + description + ": " + value);
        }
        return parsed;
    }

    private static String blockKey(String state) {
        int properties = state.indexOf('[');
        return properties < 0 ? state : state.substring(0, properties);
    }

    private static String canonicalRequiredHorizontalFacing(String block,
            Map<String, String> properties, String input) {
        String facing = properties.get("facing");
        if (properties.size() != 1
                || facing == null
                || !Set.of("north", "west", "east", "south").contains(facing)
                || !input.equals(block + "[facing=" + facing + "]")) {
            throw new IllegalArgumentException("unsupported required-facing state: " + input);
        }
        return input;
    }

    private static String canonicalHorizontalFacing(String block,
            Map<String, String> properties, String input) {
        String facing = properties.isEmpty() ? "north" : properties.get("facing");
        if (facing == null || properties.size() > 1
                || !Set.of("north", "east", "south", "west").contains(facing)) {
            throw new IllegalArgumentException("unsupported horizontal-facing state: " + input);
        }
        return block + "[facing=" + facing + "]";
    }

    private static String canonicalRail(String block, Map<String, String> properties,
            String input) {
        if (properties.isEmpty()) {
            return block + "[shape=north_south,waterlogged=false]";
        }
        String shape = properties.get("shape");
        if (shape == null || properties.size() != 2
                || !Set.of("north_south", "east_west", "ascending_east", "ascending_west",
                        "ascending_north", "ascending_south", "south_east", "south_west",
                        "north_west", "north_east").contains(shape)
                || !"false".equals(properties.get("waterlogged"))) {
            throw new IllegalArgumentException("unsupported rail state: " + input);
        }
        return block + "[shape=" + shape + ",waterlogged=false]";
    }

    private static String canonicalTripwireHook(String block,
            Map<String, String> properties, String input) {
        if (properties.isEmpty()) {
            return block + "[attached=false,facing=north,powered=false]";
        }
        requireBoolean(properties, "attached", input);
        String facing = requireFacing(properties, "facing", false, input);
        requireBoolean(properties, "powered", input);
        if (properties.size() != 3) throw unsupportedMechanism(input);
        return block + "[attached=" + properties.get("attached") + ",facing=" + facing
                + ",powered=" + properties.get("powered") + "]";
    }

    private static String canonicalTripwire(String block,
            Map<String, String> properties, String input) {
        if (properties.isEmpty()) {
            return block + "[attached=false,disarmed=false,east=false,north=false,"
                    + "powered=false,south=false,west=false]";
        }
        for (String property : List.of("attached", "disarmed", "east", "north",
                "powered", "south", "west")) requireBoolean(properties, property, input);
        if (properties.size() != 7) throw unsupportedMechanism(input);
        return block + "[attached=" + properties.get("attached") + ",disarmed="
                + properties.get("disarmed") + ",east=" + properties.get("east")
                + ",north=" + properties.get("north") + ",powered="
                + properties.get("powered") + ",south=" + properties.get("south")
                + ",west=" + properties.get("west") + "]";
    }

    private static String canonicalDispenser(String block,
            Map<String, String> properties, String input) {
        if (properties.isEmpty()) return block + "[facing=north,triggered=false]";
        String facing = requireFacing(properties, "facing", true, input);
        requireBoolean(properties, "triggered", input);
        if (properties.size() != 2) throw unsupportedMechanism(input);
        return block + "[facing=" + facing + ",triggered="
                + properties.get("triggered") + "]";
    }

    private static String canonicalLever(String block,
            Map<String, String> properties, String input) {
        if (properties.isEmpty()) {
            return block + "[face=wall,facing=north,powered=false]";
        }
        String face = properties.get("face");
        if (!Set.of("floor", "wall", "ceiling").contains(face)) {
            throw unsupportedMechanism(input);
        }
        String facing = requireFacing(properties, "facing", false, input);
        requireBoolean(properties, "powered", input);
        if (properties.size() != 3) throw unsupportedMechanism(input);
        return block + "[face=" + face + ",facing=" + facing + ",powered="
                + properties.get("powered") + "]";
    }

    private static String canonicalRedstoneWire(String block,
            Map<String, String> properties, String input) {
        if (properties.isEmpty()) {
            return block + "[east=none,north=none,power=0,south=none,west=none]";
        }
        for (String side : List.of("east", "north", "south", "west")) {
            if (!Set.of("none", "side", "up").contains(properties.get(side))) {
                throw unsupportedMechanism(input);
            }
        }
        int power = parseRange(properties.get("power"), 0, 15, "redstone power");
        if (properties.size() != 5) throw unsupportedMechanism(input);
        return block + "[east=" + properties.get("east") + ",north="
                + properties.get("north") + ",power=" + power + ",south="
                + properties.get("south") + ",west=" + properties.get("west") + "]";
    }

    private static String canonicalRepeater(String block,
            Map<String, String> properties, String input) {
        if (properties.isEmpty()) {
            return block + "[delay=1,facing=north,locked=false,powered=false]";
        }
        int delay = parseRange(properties.get("delay"), 1, 4, "repeater delay");
        String facing = requireFacing(properties, "facing", false, input);
        requireBoolean(properties, "locked", input);
        requireBoolean(properties, "powered", input);
        if (properties.size() != 4) throw unsupportedMechanism(input);
        return block + "[delay=" + delay + ",facing=" + facing + ",locked="
                + properties.get("locked") + ",powered=" + properties.get("powered") + "]";
    }

    private static String canonicalStickyPiston(String block,
            Map<String, String> properties, String input) {
        if (properties.isEmpty()) return block + "[extended=false,facing=north]";
        requireBoolean(properties, "extended", input);
        String facing = requireFacing(properties, "facing", true, input);
        if (properties.size() != 2) throw unsupportedMechanism(input);
        return block + "[extended=" + properties.get("extended") + ",facing="
                + facing + "]";
    }

    private static void requireBoolean(Map<String, String> properties, String property,
            String input) {
        if (!Set.of("false", "true").contains(properties.get(property))) {
            throw unsupportedMechanism(input);
        }
    }

    private static String requireFacing(Map<String, String> properties, String property,
            boolean vertical, String input) {
        String facing = properties.get(property);
        Set<String> domain = vertical
                ? Set.of("down", "up", "north", "south", "west", "east")
                : Set.of("north", "east", "south", "west");
        if (!domain.contains(facing)) throw unsupportedMechanism(input);
        return facing;
    }

    private static IllegalArgumentException unsupportedMechanism(String input) {
        return new IllegalArgumentException("unsupported jungle mechanism state: " + input);
    }

    private static String canonicalFence(String block, Map<String, String> properties,
            String input) {
        if (properties.isEmpty()) {
            return block + "[east=false,north=false,south=false,waterlogged=false,west=false]";
        }
        String waterlogged = properties.get("waterlogged");
        if (properties.size() != 5 || !Set.of("false", "true").contains(waterlogged)) {
            throw new IllegalArgumentException("unsupported fence state: " + input);
        }
        for (String side : List.of("east", "north", "south", "west")) {
            String value = properties.get(side);
            if (value == null || !Set.of("false", "true").contains(value)) {
                throw new IllegalArgumentException("unsupported fence state: " + input);
            }
        }
        return block + "[east=" + properties.get("east") + ",north="
                + properties.get("north") + ",south=" + properties.get("south")
                + ",waterlogged=" + waterlogged + ",west=" + properties.get("west") + "]";
    }

    private static String canonicalStairs(String block, Map<String, String> properties,
            String input) {
        if (properties.isEmpty()) {
            return block + "[facing=north,half=bottom,shape=straight,waterlogged=false]";
        }
        String facing = properties.get("facing");
        String half = properties.get("half");
        String shape = properties.get("shape");
        String waterlogged = properties.get("waterlogged");
        if (properties.size() != 4
                || !Set.of("north", "east", "south", "west").contains(facing)
                || !Set.of("bottom", "top").contains(half)
                || !Set.of("straight", "inner_left", "inner_right", "outer_left",
                        "outer_right").contains(shape)
                || !Set.of("false", "true").contains(waterlogged)) {
            throw new IllegalArgumentException("unsupported stair state: " + input);
        }
        return block + "[facing=" + facing + ",half=" + half + ",shape=" + shape
                + ",waterlogged=" + waterlogged + "]";
    }

    private static String canonicalSpruceStairs(String block,
            Map<String, String> properties, String input) {
        String canonical = canonicalStairs(block, properties, input);
        if ("top".equals(properties.get("half"))
                && "straight".equals(properties.get("shape"))
                && "false".equals(properties.get("waterlogged"))) {
            return block + "[half=top,waterlogged=false,shape=straight,facing="
                    + properties.get("facing") + "]";
        }
        return canonical;
    }

    private static void registerFenceStates(Map<String, Mc263FeatureBlockState> states, int id,
            String block) {
        boolean registeredDefault = false;
        for (boolean east : new boolean[]{false, true}) {
            for (boolean north : new boolean[]{false, true}) {
                for (boolean south : new boolean[]{false, true}) {
                    for (boolean west : new boolean[]{false, true}) {
                        String exact = block + "[east=" + east + ",north=" + north
                                + ",south=" + south + ",waterlogged=false,west=" + west + "]";
                        if (!registeredDefault) {
                            registerDefault(states, id, exact, true, false,
                                    FluidKind.NONE, 0, Capability.NONE);
                            registeredDefault = true;
                        } else {
                            registerAlias(states, id, exact, true, false,
                                    FluidKind.NONE, 0, Capability.NONE);
                        }
                    }
                }
            }
        }
    }

    private static void registerStairStates(Map<String, Mc263FeatureBlockState> states, int id,
            String block) {
        boolean registeredDefault = false;
        for (String facing : List.of("north", "east", "south", "west")) {
            for (String half : List.of("bottom", "top")) {
                for (String shape : List.of("straight", "inner_left", "inner_right",
                        "outer_left", "outer_right")) {
                    for (boolean waterlogged : new boolean[]{false, true}) {
                        String exact = block + "[facing=" + facing + ",half=" + half
                                + ",shape=" + shape + ",waterlogged=" + waterlogged + "]";
                        FluidKind fluid = waterlogged
                                ? FluidKind.WATER_SOURCE : FluidKind.NONE;
                        if (!registeredDefault) {
                            registerDefault(states, id, exact, true, false, fluid,
                                    waterlogged ? 8 : 0, Capability.NONE);
                            registeredDefault = true;
                        } else {
                            registerAlias(states, id, exact, true, false, fluid,
                                    waterlogged ? 8 : 0, Capability.NONE);
                        }
                    }
                }
            }
        }
    }

    private static void registerSolidDefaults(Map<String, Mc263FeatureBlockState> states,
            CatalogEntry... entries) {
        for (CatalogEntry entry : entries) {
            registerDefault(states, entry.id, entry.exact, true, true,
                    FluidKind.NONE, 0, Capability.NONE);
        }
    }

    private static void registerTreeLog(Map<String, Mc263FeatureBlockState> states,
            String block, int yId, int... horizontalIds) {
        registerDefault(states, yId, block + "[axis=y]", true, true,
                FluidKind.NONE, 0, Capability.NONE);
        if (horizontalIds.length == 0) {
            registerAlias(states, yId, block + "[axis=x]", true, true,
                    FluidKind.NONE, 0, Capability.NONE);
            registerAlias(states, yId, block + "[axis=z]", true, true,
                    FluidKind.NONE, 0, Capability.NONE);
            return;
        }
        if (horizontalIds.length != 2) {
            throw new ExceptionInInitializerError("tree-log axis carrier requires x/z IDs: "
                    + block);
        }
        registerDefault(states, horizontalIds[0], block + "[axis=x]", true, true,
                FluidKind.NONE, 0, Capability.NONE);
        registerDefault(states, horizontalIds[1], block + "[axis=z]", true, true,
                FluidKind.NONE, 0, Capability.NONE);
    }

    private static void registerTreeLeaves(Map<String, Mc263FeatureBlockState> states,
            CatalogEntry leaf) {
        for (int distance = 1; distance <= 7; distance++) {
            boolean fullExistingDomain = leaf.exact().equals("minecraft:oak_leaves")
                    || leaf.exact().equals("minecraft:birch_leaves")
                    || leaf.exact().equals("minecraft:jungle_leaves");
            for (boolean persistent : fullExistingDomain
                    ? new boolean[]{false, true} : new boolean[]{false}) {
                for (boolean waterlogged : new boolean[]{false, true}) {
                    String exact = leaf.exact() + "[distance=" + distance
                            + ",persistent=" + persistent + ",waterlogged=" + waterlogged + "]";
                    registerTreeState(states, leaf.id(), exact,
                            distance == 7 && !persistent && !waterlogged, true, false,
                            waterlogged ? FluidKind.WATER_SOURCE : FluidKind.NONE,
                            waterlogged ? 8 : 0);
                }
            }
        }
    }

    private static void registerDripleafStates(Map<String, Mc263FeatureBlockState> states) {
        for (String facing : new String[]{"north", "east", "south", "west"}) {
            for (String half : new String[]{"lower", "upper"}) {
                String exact = "minecraft:small_dripleaf[facing=" + facing + ",half=" + half
                        + ",waterlogged=false]";
                registerTreeState(states, Blocks.SMALL_DRIPLEAF, exact,
                        facing.equals("north") && half.equals("lower"), false, false,
                        FluidKind.NONE, 0);
            }
            registerTreeState(states, Blocks.BIG_DRIPLEAF_STEM,
                    "minecraft:big_dripleaf_stem[facing=" + facing + ",waterlogged=false]",
                    facing.equals("north"), false, false, FluidKind.NONE, 0);
            registerTreeState(states, Blocks.BIG_DRIPLEAF,
                    "minecraft:big_dripleaf[facing=" + facing
                            + ",tilt=none,waterlogged=false]",
                    facing.equals("north"), false, false, FluidKind.NONE, 0);
        }
    }

    private static void registerNestedAzaleaLeaves(
            Map<String, Mc263FeatureBlockState> states, int id, String block) {
        for (int distance = 1; distance <= 7; distance++) {
            for (boolean waterlogged : new boolean[]{false, true}) {
                String exact = block + "[distance=" + distance
                        + ",persistent=false,waterlogged=" + waterlogged + "]";
                registerTreeState(states, id, exact, distance == 7 && !waterlogged,
                        true, false, waterlogged ? FluidKind.WATER_SOURCE : FluidKind.NONE,
                        waterlogged ? 8 : 0);
            }
        }
    }

    private static void registerTreeState(Map<String, Mc263FeatureBlockState> states, int id,
            String exact, boolean defaultState, boolean solid, boolean solidRender,
            FluidKind fluidKind, int fluidAmount) {
        registerTreeState(states, id, exact, defaultState, solid, solidRender,
                fluidKind, fluidAmount, Capability.NONE);
    }

    private static void registerTreeState(Map<String, Mc263FeatureBlockState> states, int id,
            String exact, boolean defaultState, boolean solid, boolean solidRender,
            FluidKind fluidKind, int fluidAmount, Capability capability) {
        if (defaultState) {
            registerDefault(states, id, exact, solid, solidRender,
                    fluidKind, fluidAmount, capability);
        } else {
            registerAlias(states, id, exact, solid, solidRender,
                    fluidKind, fluidAmount, capability);
        }
    }

    private static void registerFlowerBed(Map<String, Mc263FeatureBlockState> states, int id,
            String block, String amountProperty, int maximum) {
        for (int amount = 1; amount <= maximum; amount++) {
            for (String facing : new String[]{"north", "east", "south", "west"}) {
                String exact = block + "[facing=" + facing + "," + amountProperty
                        + "=" + amount + "]";
                Mc263FeatureBlockState value = state(id, exact, false, false,
                        FluidKind.NONE, 0, Capability.NONE);
                if (amount == 1 && facing.equals("north")) registerDefaultOnly(id, value);
                if (states.putIfAbsent(exact, value) != null) {
                    throw new ExceptionInInitializerError(
                            "duplicate exact FEATURES state: " + exact);
                }
            }
        }
    }

    private static void registerPaleMossCarpet(Map<String, Mc263FeatureBlockState> states) {
        boolean first = true;
        for (boolean bottom : new boolean[]{true, false})
            for (String east : new String[]{"none", "low", "tall"})
                for (String north : new String[]{"none", "low", "tall"})
                    for (String south : new String[]{"none", "low", "tall"})
                        for (String west : new String[]{"none", "low", "tall"}) {
                            String exact = "minecraft:pale_moss_carpet[bottom=" + bottom
                                    + ",east=" + east + ",north=" + north + ",south=" + south
                                    + ",west=" + west + "]";
                            if (first) {
                                registerDefault(states, Blocks.PALE_MOSS_CARPET, exact,
                                        false, false, FluidKind.NONE, 0, Capability.NONE);
                                first = false;
                            } else {
                                registerAlias(states, Blocks.PALE_MOSS_CARPET, exact,
                                        false, false, FluidKind.NONE, 0, Capability.NONE);
                            }
                        }
    }

    private static void registerCaveVines(Map<String, Mc263FeatureBlockState> states) {
        for (int age = 0; age <= 25; age++) {
            for (boolean berries : new boolean[]{false, true}) {
                String exact = "minecraft:cave_vines[age=" + age + ",berries=" + berries + "]";
                if (age == 0 && !berries) {
                    registerDefault(states, Blocks.CAVE_VINES, exact, false, false,
                            FluidKind.NONE, 0, Capability.NONE);
                } else {
                    registerAlias(states, Blocks.CAVE_VINES, exact, false, false,
                            FluidKind.NONE, 0, Capability.NONE);
                }
            }
        }
        registerDefault(states, Blocks.CAVE_VINES_PLANT,
                "minecraft:cave_vines_plant[berries=false]", false, false,
                FluidKind.NONE, 0, Capability.NONE);
        registerAlias(states, Blocks.CAVE_VINES_PLANT,
                "minecraft:cave_vines_plant[berries=true]", false, false,
                FluidKind.NONE, 0, Capability.NONE);
    }

    private static void registerWaterloggedPlant(Map<String, Mc263FeatureBlockState> states,
            int id, String block) {
        registerDefault(states, id, block + "[waterlogged=false]", false, false,
                FluidKind.NONE, 0, Capability.NONE);
        registerAlias(states, id, block + "[waterlogged=true]", false, false,
                FluidKind.WATER_SOURCE, 8, Capability.NONE);
    }

    private static void registerDoublePlant(Map<String, Mc263FeatureBlockState> states, int id,
            String block) {
        registerDefault(states, id, block + "[half=lower]", false, false,
                FluidKind.NONE, 0, Capability.NONE);
        registerAlias(states, id, block + "[half=upper]", false, false,
                FluidKind.NONE, 0, Capability.NONE);
    }

    private static void registerHugeMushroom(Map<String, Mc263FeatureBlockState> states, int id,
            String block) {
        boolean first = true;
        for (boolean down : new boolean[]{true, false})
            for (boolean east : new boolean[]{true, false})
                for (boolean north : new boolean[]{true, false})
                    for (boolean south : new boolean[]{true, false})
                        for (boolean up : new boolean[]{true, false})
                            for (boolean west : new boolean[]{true, false}) {
                                String exact = block + "[down=" + down + ",east=" + east
                                        + ",north=" + north + ",south=" + south + ",up=" + up
                                        + ",west=" + west + "]";
                                if (first) {
                                    registerDefault(states, id, exact, true, true,
                                            FluidKind.NONE, 0, Capability.NONE);
                                    first = false;
                                } else {
                                    registerAlias(states, id, exact, true, true,
                                            FluidKind.NONE, 0, Capability.NONE);
                                }
                            }
    }

    /** Exact block-state closure emitted by the pinned 26.3 igloo templates. */
    private static void registerIglooStates(Map<String, Mc263FeatureBlockState> states) {
        registerDefault(states, Blocks.CRAFTING_TABLE, "minecraft:crafting_table", true, true,
                FluidKind.NONE, 0, Capability.NONE);
        registerDefault(states, Blocks.REDSTONE_TORCH,
                "minecraft:redstone_torch[lit=true]", false, false,
                FluidKind.NONE, 0, Capability.NONE);
        registerDefault(states, Blocks.WHITE_CARPET, "minecraft:white_carpet", false, false,
                FluidKind.NONE, 0, Capability.NONE);
        registerDefault(states, Blocks.LIGHT_GRAY_CARPET, "minecraft:light_gray_carpet",
                false, false, FluidKind.NONE, 0, Capability.NONE);
        registerDefault(states, Blocks.RED_CARPET, "minecraft:red_carpet", false, false,
                FluidKind.NONE, 0, Capability.NONE);
        registerDefault(states, Blocks.POLISHED_ANDESITE, "minecraft:polished_andesite",
                true, true, FluidKind.NONE, 0, Capability.NONE);
        registerDefault(states, Blocks.CRACKED_STONE_BRICKS,
                "minecraft:cracked_stone_bricks", true, true,
                FluidKind.NONE, 0, Capability.NONE);
        registerDefault(states, Blocks.MOSSY_STONE_BRICK, "minecraft:mossy_stone_bricks",
                true, true, FluidKind.NONE, 0, Capability.NONE);
        registerDefault(states, Blocks.INFESTED_STONE_BRICKS,
                "minecraft:infested_stone_bricks", true, true,
                FluidKind.NONE, 0, Capability.NONE);
        registerDefault(states, Blocks.INFESTED_CHISELED_STONE_BRICKS,
                "minecraft:infested_chiseled_stone_bricks", true, true,
                FluidKind.NONE, 0, Capability.NONE);
        registerDefault(states, Blocks.INFESTED_MOSSY_STONE_BRICKS,
                "minecraft:infested_mossy_stone_bricks", true, true,
                FluidKind.NONE, 0, Capability.NONE);
        registerDefault(states, Blocks.WATER_CAULDRON, "minecraft:water_cauldron[level=2]",
                true, false, FluidKind.NONE, 0, Capability.NONE);
        registerDefault(states, Blocks.POTTED_CACTUS, "minecraft:potted_cactus", false, false,
                FluidKind.NONE, 0, Capability.NONE);
        registerDefault(states, Blocks.BREWING_STAND,
                "minecraft:brewing_stand[has_bottle_0=false,has_bottle_1=true,"
                        + "has_bottle_2=false]",
                false, false, FluidKind.NONE, 0, Capability.NONE);
        registerDefault(states, Blocks.SPRUCE_SLAB,
                "minecraft:spruce_slab[waterlogged=false,type=top]", true, false,
                FluidKind.NONE, 0, Capability.NONE);

        String[] facings = {"north", "east", "south", "west"};
        for (int index = 0; index < facings.length; index++) {
            String facing = facings[index];
            registerIglooFacingState(states, Blocks.WOOD_TRAPDOOR,
                    "minecraft:oak_trapdoor[half=top,waterlogged=false,powered=false,facing="
                            + facing + ",open=false]", index == 0, false, false,
                    Capability.NONE);
            registerIglooFacingState(states, Blocks.RED_BED,
                    "minecraft:red_bed[part=foot,facing=" + facing + ",occupied=false]",
                    index == 0, false, false, Capability.NONE);
            registerAlias(states, Blocks.RED_BED,
                    "minecraft:red_bed[part=head,facing=" + facing + ",occupied=false]",
                    false, false, FluidKind.NONE, 0, Capability.NONE);
            registerIglooFacingState(states, Blocks.FURNACE,
                    "minecraft:furnace[lit=false,facing=" + facing + "]", index == 0,
                    true, true, Capability.NONE);
            registerIglooFacingState(states, Blocks.LADDER,
                    "minecraft:ladder[waterlogged=false,facing=" + facing + "]", index == 0,
                    false, false, Capability.NONE);
            registerIglooFacingState(states, Blocks.SPRUCE_STAIRS,
                    "minecraft:spruce_stairs[half=top,waterlogged=false,shape=straight,facing="
                            + facing + "]", index == 0, true, false, Capability.NONE);
            registerIglooFacingState(states, Blocks.OAK_WALL_SIGN,
                    "minecraft:oak_wall_sign[waterlogged=false,facing=" + facing + "]",
                    index == 0, false, false, Capability.NONE);
        }
        registerDefault(states, Blocks.IRON_BARS,
                "minecraft:iron_bars[east=true,waterlogged=false,south=false,north=false,"
                        + "west=true]",
                true, false, FluidKind.NONE, 0, Capability.NONE);
        registerAlias(states, Blocks.IRON_BARS,
                "minecraft:iron_bars[east=false,waterlogged=false,south=true,north=true,"
                        + "west=false]",
                true, false, FluidKind.NONE, 0, Capability.NONE);
    }

    private static void registerIglooFacingState(Map<String, Mc263FeatureBlockState> states,
            int id, String exact, boolean defaultState, boolean solid, boolean solidRender,
            Capability capability) {
        if (defaultState) {
            registerDefault(states, id, exact, solid, solidRender,
                    FluidKind.NONE, 0, capability);
        } else {
            registerAlias(states, id, exact, solid, solidRender,
                    FluidKind.NONE, 0, capability);
        }
    }

    /** Exact additional block-state closure emitted by the pinned swamp-hut program. */
    private static void registerSwampHutStates(Map<String, Mc263FeatureBlockState> states) {
        registerDefault(states, Blocks.CAULDRON, "minecraft:cauldron", true, false,
                FluidKind.NONE, 0, Capability.NONE);
        registerDefault(states, Blocks.POTTED_RED_MUSHROOM,
                "minecraft:potted_red_mushroom", false, false,
                FluidKind.NONE, 0, Capability.NONE);
        for (String facing : new String[]{"north", "east", "south", "west"}) {
            for (String shape : new String[]{"straight", "outer_left", "outer_right"}) {
                registerAlias(states, Blocks.SPRUCE_STAIRS,
                        "minecraft:spruce_stairs[facing=" + facing
                                + ",half=bottom,shape=" + shape
                                + ",waterlogged=false]",
                        true, false, FluidKind.NONE, 0, Capability.NONE);
            }
        }
    }

    private static void registerJungleMechanismStates(
            Map<String, Mc263FeatureBlockState> states) {
        registerReleasedStates(states, Blocks.TRIPWIRE_HOOK_BLOCK,
                "minecraft:tripwire_hook[attached=false,facing=north,powered=false]",
                List.of(
                        "minecraft:tripwire_hook[attached=true,facing=east,powered=false]",
                        "minecraft:tripwire_hook[attached=true,facing=north,powered=false]",
                        "minecraft:tripwire_hook[attached=true,facing=south,powered=false]",
                        "minecraft:tripwire_hook[attached=true,facing=west,powered=false]"),
                false, false, Capability.NONE);
        registerReleasedStates(states, Blocks.TRIPWIRE,
                "minecraft:tripwire[attached=false,disarmed=false,east=false,north=false,"
                        + "powered=false,south=false,west=false]",
                List.of(
                        "minecraft:tripwire[attached=true,disarmed=false,east=false,north=true,"
                                + "powered=false,south=true,west=false]",
                        "minecraft:tripwire[attached=true,disarmed=false,east=true,north=false,"
                                + "powered=false,south=false,west=true]"),
                false, false, Capability.NONE);
        registerReleasedStates(states, Blocks.DISPENSER,
                "minecraft:dispenser[facing=north,triggered=false]",
                List.of("minecraft:dispenser[facing=east,triggered=false]",
                        "minecraft:dispenser[facing=south,triggered=false]",
                        "minecraft:dispenser[facing=west,triggered=false]"),
                true, true, Capability.RANDOMIZABLE_CONTAINER);
        registerReleasedStates(states, Blocks.LEVER,
                "minecraft:lever[face=wall,facing=north,powered=false]",
                List.of("minecraft:lever[face=wall,facing=east,powered=false]",
                        "minecraft:lever[face=wall,facing=south,powered=false]",
                        "minecraft:lever[face=wall,facing=west,powered=false]"),
                false, false, Capability.NONE);
        registerReleasedStates(states, Blocks.REDSTONE_WIRE,
                "minecraft:redstone_wire[east=none,north=none,power=0,south=none,west=none]",
                List.of(
                        "minecraft:redstone_wire[east=none,north=none,power=0,south=side,west=side]",
                        "minecraft:redstone_wire[east=none,north=side,power=0,south=none,west=side]",
                        "minecraft:redstone_wire[east=none,north=side,power=0,south=side,west=none]",
                        "minecraft:redstone_wire[east=none,north=side,power=0,south=up,west=none]",
                        "minecraft:redstone_wire[east=none,north=up,power=0,south=side,west=none]",
                        "minecraft:redstone_wire[east=side,north=none,power=0,south=none,west=side]",
                        "minecraft:redstone_wire[east=side,north=none,power=0,south=none,west=up]",
                        "minecraft:redstone_wire[east=side,north=side,power=0,south=none,west=none]",
                        "minecraft:redstone_wire[east=side,north=side,power=0,south=side,west=side]",
                        "minecraft:redstone_wire[east=up,north=none,power=0,south=none,west=side]"),
                false, false, Capability.NONE);
        registerReleasedStates(states, Blocks.REPEATER,
                "minecraft:repeater[delay=1,facing=north,locked=false,powered=false]",
                List.of(
                        "minecraft:repeater[delay=1,facing=east,locked=false,powered=false]",
                        "minecraft:repeater[delay=1,facing=south,locked=false,powered=false]",
                        "minecraft:repeater[delay=1,facing=west,locked=false,powered=false]"),
                false, false, Capability.NONE);
        registerReleasedStates(states, Blocks.STICKY_PISTON,
                "minecraft:sticky_piston[extended=false,facing=north]",
                List.of("minecraft:sticky_piston[extended=false,facing=up]",
                        "minecraft:sticky_piston[extended=false,facing=west]"),
                true, true, Capability.NONE);
    }

    /** Exact block-state closure emitted by the pinned 26.3 desert-pyramid program. */
    private static void registerDesertPyramidStates(
            Map<String, Mc263FeatureBlockState> states) {
        registerSolidDefaults(states,
                entry(Blocks.BLUE_TERRACOTTA, "minecraft:blue_terracotta"),
                entry(Blocks.CUT_SANDSTONE, "minecraft:cut_sandstone"),
                entry(Blocks.CHISELED_SANDSTONE, "minecraft:chiseled_sandstone"));
        registerStairStates(states, Blocks.SANDSTONE_STAIRS, "minecraft:sandstone_stairs");
        registerDefault(states, Blocks.TNT, "minecraft:tnt[unstable=false]", true, true,
                FluidKind.NONE, 0, Capability.NONE);
        registerDefault(states, Blocks.STONE_PRESSURE_PLATE,
                "minecraft:stone_pressure_plate[powered=false]", true, false,
                FluidKind.NONE, 0, Capability.NONE);
        registerAlias(states, Blocks.STONE_PRESSURE_PLATE,
                "minecraft:stone_pressure_plate[powered=true]", true, false,
                FluidKind.NONE, 0, Capability.NONE);
    }

    /** Exact canonical state closure emitted by accepted Trail Ruins and ocean-ruin v3. */
    private static void registerTrailOceanRuinStates(
            Map<String, Mc263FeatureBlockState> states) {
        registerTrailOceanSolidDefaults(states,
                entry(Blocks.BRICKS, "minecraft:bricks"),
                entry(Blocks.PACKED_MUD, "minecraft:packed_mud"),
                entry(Blocks.MUD_BRICKS, "minecraft:mud_bricks"),
                entry(Blocks.COAL_BLOCK, "minecraft:coal_block"),
                entry(Blocks.OBSIDIAN, "minecraft:obsidian"),
                entry(Blocks.POLISHED_GRANITE, "minecraft:polished_granite"),
                entry(Blocks.POLISHED_DIORITE, "minecraft:polished_diorite"),
                entry(Blocks.LIGHT_BLUE_TERRACOTTA, "minecraft:light_blue_terracotta"),
                entry(Blocks.GRAY_TERRACOTTA, "minecraft:gray_terracotta"),
                entry(Blocks.CYAN_TERRACOTTA, "minecraft:cyan_terracotta"));
        registerTrailOceanState(states, Blocks.BROWN_STAINED_GLASS,
                "minecraft:brown_stained_glass", true, false, false);
        registerTrailOceanState(states, Blocks.CARTOGRAPHY_TABLE,
                "minecraft:cartography_table", true, true, false);
        registerTrailOceanState(states, Blocks.SMITHING_TABLE,
                "minecraft:smithing_table", true, true, false);
        registerTrailOceanState(states, Blocks.COMPOSTER,
                "minecraft:composter[level=0]", true, false, false);

        registerTrailStairs(states, Blocks.BRICK_STAIRS, "minecraft:brick_stairs",
                List.of("bottom:straight", "top:straight"));
        registerTrailStairs(states, Blocks.MUD_BRICK_STAIRS, "minecraft:mud_brick_stairs",
                List.of("bottom:straight", "top:straight", "bottom:inner_left",
                        "bottom:outer_left", "bottom:outer_right", "top:inner_right"));
        registerTrailStairs(states, Blocks.STONE_BRICK_STAIRS,
                "minecraft:stone_brick_stairs",
                List.of("bottom:straight", "top:straight"));
        registerTrailSlabs(states, Blocks.BRICK_SLAB, "minecraft:brick_slab",
                List.of("bottom", "top"));
        registerTrailSlabs(states, Blocks.MUD_BRICK_SLAB, "minecraft:mud_brick_slab",
                List.of("bottom", "top"));
        registerTrailSlabs(states, Blocks.SMOOTH_STONE_SLAB,
                "minecraft:smooth_stone_slab", List.of("double"));

        List<String> brickWalls = List.of(
                "east=tall,north=tall,south=tall,up=false,west=tall",
                "east=none,north=none,south=tall,up=true,west=tall",
                "east=none,north=none,south=low,up=true,west=low",
                "east=tall,north=tall,south=none,up=true,west=none",
                "east=none,north=tall,south=none,up=true,west=tall",
                "east=tall,north=none,south=tall,up=true,west=tall",
                "east=low,north=none,south=none,up=true,west=none");
        registerTrailWalls(states, Blocks.BRICK_WALL, "minecraft:brick_wall", brickWalls);
        registerTrailWalls(states, Blocks.MUD_BRICK_WALL, "minecraft:mud_brick_wall",
                List.of(
                        "east=none,north=none,south=tall,up=true,west=tall",
                        "east=tall,north=tall,south=none,up=true,west=none",
                        "east=tall,north=tall,south=tall,up=false,west=tall",
                        "east=low,north=none,south=none,up=true,west=none",
                        "east=none,north=none,south=tall,up=true,west=none",
                        "east=none,north=none,south=low,up=true,west=low",
                        "east=none,north=tall,south=none,up=true,west=tall"));

        for (String facing : List.of("north", "east", "south", "west")) {
            registerTrailOceanState(states, Blocks.LADDER,
                    "minecraft:ladder[facing=" + facing + ",waterlogged=true]",
                    false, false, true);
            registerTrailOceanState(states, Blocks.FURNACE,
                    "minecraft:furnace[facing=" + facing + ",lit=false]",
                    true, true, false);
            registerTrailOceanState(states, Blocks.BLAST_FURNACE,
                    "minecraft:blast_furnace[facing=" + facing + ",lit=false]",
                    true, true, false);
            registerTrailOceanState(states, Blocks.GRINDSTONE,
                    "minecraft:grindstone[face=floor,facing=" + facing + "]",
                    true, false, false);
            registerTrailOceanState(states, Blocks.LOOM,
                    "minecraft:loom[facing=" + facing + "]", true, true, false);
            registerTrailOceanState(states, Blocks.DAMAGED_ANVIL,
                    "minecraft:damaged_anvil[facing=" + facing + "]", true, false, false);
            for (boolean waterlogged : new boolean[]{false, true}) {
                registerTrailOceanState(states, Blocks.CAMPFIRE,
                        "minecraft:campfire[facing=" + facing
                                + ",lit=false,signal_fire=false,waterlogged="
                                + waterlogged + "]",
                        false, false, waterlogged);
            }
        }

        registerFacingStates(states, Blocks.ORANGE_GLAZED_TERRACOTTA,
                "minecraft:orange_glazed_terracotta");
        registerFacingStates(states, Blocks.LIGHT_BLUE_GLAZED_TERRACOTTA,
                "minecraft:light_blue_glazed_terracotta");
        registerFacingStates(states, Blocks.YELLOW_GLAZED_TERRACOTTA,
                "minecraft:yellow_glazed_terracotta");
        registerFacingStates(states, Blocks.LIGHT_GRAY_GLAZED_TERRACOTTA,
                "minecraft:light_gray_glazed_terracotta");
        registerFacingStates(states, Blocks.CYAN_GLAZED_TERRACOTTA,
                "minecraft:cyan_glazed_terracotta");
        registerFacingStates(states, Blocks.PURPLE_GLAZED_TERRACOTTA,
                "minecraft:purple_glazed_terracotta");
        registerFacingStates(states, Blocks.BLACK_GLAZED_TERRACOTTA,
                "minecraft:black_glazed_terracotta");

        for (String connections : List.of(
                "east=true,north=true,south=false,west=true",
                "east=true,north=true,south=true,west=true")) {
            registerTrailOceanState(states, Blocks.IRON_BARS,
                    "minecraft:iron_bars[" + connections.replace(",west=",
                            ",waterlogged=true,west=") + "]", true, false, true);
        }
    }

    private static void registerTrailOceanSolidDefaults(
            Map<String, Mc263FeatureBlockState> states, CatalogEntry... entries) {
        for (CatalogEntry value : entries) {
            registerTrailOceanState(states, value.id(), value.exact(), true, true, false);
        }
    }

    private static void registerTrailStairs(Map<String, Mc263FeatureBlockState> states, int id,
            String block, List<String> halfShapes) {
        for (String halfShape : halfShapes) {
            String[] parts = halfShape.split(":", -1);
            for (String facing : List.of("north", "east", "south", "west")) {
                for (boolean waterlogged : new boolean[]{false, true}) {
                    registerTrailOceanState(states, id, block + "[facing=" + facing
                            + ",half=" + parts[0] + ",shape=" + parts[1]
                            + ",waterlogged=" + waterlogged + "]",
                            true, false, waterlogged);
                }
            }
        }
    }

    private static void registerTrailSlabs(Map<String, Mc263FeatureBlockState> states, int id,
            String block, List<String> types) {
        for (String type : types) for (boolean waterlogged : new boolean[]{false, true}) {
            registerTrailOceanState(states, id, block + "[type=" + type + ",waterlogged="
                    + waterlogged + "]", true, type.equals("double"), waterlogged);
        }
    }

    private static void registerTrailWalls(Map<String, Mc263FeatureBlockState> states, int id,
            String block, List<String> topologies) {
        for (String topology : topologies) for (boolean waterlogged
                : new boolean[]{false, true}) {
            registerTrailOceanState(states, id, block + "[" + topology.replace(",west=",
                    ",waterlogged=" + waterlogged + ",west=") + "]",
                    true, false, waterlogged);
        }
    }

    private static void registerFacingStates(Map<String, Mc263FeatureBlockState> states, int id,
            String block) {
        for (String facing : List.of("north", "east", "south", "west")) {
            registerTrailOceanState(states, id, block + "[facing=" + facing + "]",
                    true, true, false);
        }
    }

    private static void registerTrailOceanState(Map<String, Mc263FeatureBlockState> states,
            int id, String exact, boolean solid, boolean solidRender, boolean waterlogged) {
        if (states.containsKey(exact)) return;
        FluidKind fluid = waterlogged ? FluidKind.WATER_SOURCE : FluidKind.NONE;
        int amount = waterlogged ? 8 : 0;
        if (DEFAULT_BY_ID[id] == null) {
            registerDefault(states, id, exact, solid, solidRender, fluid, amount,
                    Capability.NONE);
        } else {
            registerAlias(states, id, exact, solid, solidRender, fluid, amount,
                    Capability.NONE);
        }
    }

    /** Exact block-state closure emitted by the pinned 26.3 stronghold executors. */
    private static void registerStrongholdStates(Map<String, Mc263FeatureBlockState> states) {
        registerDefault(states, Blocks.TORCH, "minecraft:torch", false, false,
                FluidKind.NONE, 0, Capability.NONE);
        registerDefault(states, Blocks.BOOKSHELF, "minecraft:bookshelf", true, true,
                FluidKind.NONE, 0, Capability.NONE);
        registerDefault(states, Blocks.END_PORTAL, "minecraft:end_portal", false, false,
                FluidKind.NONE, 0, Capability.NONE);

        for (String facing : List.of("north", "east", "south", "west")) {
            String stair = "minecraft:stone_brick_stairs[facing=" + facing
                    + ",half=bottom,shape=straight,waterlogged=false]";
            if (facing.equals("north")) {
                registerDefault(states, Blocks.STONE_BRICK_STAIRS, stair, true, false,
                        FluidKind.NONE, 0, Capability.NONE);
            } else {
                registerAlias(states, Blocks.STONE_BRICK_STAIRS, stair, true, false,
                        FluidKind.NONE, 0, Capability.NONE);
            }

            String button = "minecraft:stone_button[face=wall,facing=" + facing
                    + ",powered=false]";
            if (facing.equals("north")) {
                registerDefault(states, Blocks.STONE_BUTTON, button, false, false,
                        FluidKind.NONE, 0, Capability.NONE);
            } else {
                registerAlias(states, Blocks.STONE_BUTTON, button, false, false,
                        FluidKind.NONE, 0, Capability.NONE);
            }
            for (boolean eye : new boolean[]{false, true}) {
                String frame = "minecraft:end_portal_frame[eye=" + eye + ",facing="
                        + facing + "]";
                if (facing.equals("north") && !eye) {
                    registerDefault(states, Blocks.END_PORTAL_FRAME, frame, true, true,
                            FluidKind.NONE, 0, Capability.NONE);
                } else {
                    registerAlias(states, Blocks.END_PORTAL_FRAME, frame, true, true,
                            FluidKind.NONE, 0, Capability.NONE);
                }
            }
            registerAlias(states, Blocks.LADDER,
                    "minecraft:ladder[facing=" + facing + ",waterlogged=false]",
                    false, false, FluidKind.NONE, 0, Capability.NONE);

            for (String block : List.of("oak_door", "iron_door")) {
                int id = block.equals("oak_door") ? Blocks.DOOR_CLOSED : Blocks.IRON_DOOR;
                for (String half : List.of("lower", "upper")) {
                    for (String hinge : List.of("left", "right")) {
                        String exact = "minecraft:" + block + "[facing=" + facing + ",half="
                                + half + ",hinge=" + hinge + ",open=false,powered=false]";
                        if (DEFAULT_BY_ID[id] == null) {
                            registerDefault(states, id, exact, true, false,
                                    FluidKind.NONE, 0, Capability.NONE);
                        } else {
                            registerAlias(states, id, exact, true, false,
                                    FluidKind.NONE, 0, Capability.NONE);
                        }
                    }
                }
            }
        }

        registerDefault(states, Blocks.STONE_BRICK_SLAB,
                "minecraft:stone_brick_slab[type=bottom,waterlogged=false]", true, false,
                FluidKind.NONE, 0, Capability.NONE);
        registerAlias(states, Blocks.STONE_BRICK_SLAB,
                "minecraft:stone_brick_slab[type=double,waterlogged=false]", true, true,
                FluidKind.NONE, 0, Capability.NONE);
        registerDefault(states, Blocks.SMOOTH_STONE_SLAB,
                "minecraft:smooth_stone_slab[type=bottom,waterlogged=false]", true, false,
                FluidKind.NONE, 0, Capability.NONE);
        registerAlias(states, Blocks.SMOOTH_STONE_SLAB,
                "minecraft:smooth_stone_slab[type=double,waterlogged=false]", true, true,
                FluidKind.NONE, 0, Capability.NONE);

        for (boolean east : new boolean[]{false, true})
            for (boolean north : new boolean[]{false, true})
                for (boolean south : new boolean[]{false, true})
                    for (boolean west : new boolean[]{false, true}) {
                        String exact = "minecraft:iron_bars[east=" + east + ",north=" + north
                                + ",south=" + south + ",waterlogged=false,west=" + west + "]";
                        if (!states.containsKey(exact)) {
                            registerAlias(states, Blocks.IRON_BARS, exact, true, false,
                                    FluidKind.NONE, 0, Capability.NONE);
                        }
                    }
    }

    private static String canonicalStrongholdState(String block,
            Map<String, String> properties, String input) {
        String facing = properties.get("facing");
        boolean horizontal = facing != null
                && Set.of("north", "east", "south", "west").contains(facing);
        if (block.equals("minecraft:stone_button") && properties.size() == 3 && horizontal
                && "wall".equals(properties.get("face"))
                && "false".equals(properties.get("powered"))) {
            return block + "[face=wall,facing=" + facing + ",powered=false]";
        }
        if (block.equals("minecraft:end_portal_frame") && properties.size() == 2 && horizontal
                && Set.of("false", "true").contains(properties.get("eye"))) {
            return block + "[eye=" + properties.get("eye") + ",facing=" + facing + "]";
        }
        if (block.endsWith("_door") && properties.size() == 5 && horizontal
                && Set.of("lower", "upper").contains(properties.get("half"))
                && Set.of("left", "right").contains(properties.get("hinge"))
                && Set.of("false", "true").contains(properties.get("open"))
                && "false".equals(properties.get("powered"))) {
            return block + "[facing=" + facing + ",half=" + properties.get("half")
                    + ",hinge=" + properties.get("hinge") + ",open="
                    + properties.get("open") + ",powered=false]";
        }
        if ((block.equals("minecraft:stone_brick_slab")
                || block.equals("minecraft:smooth_stone_slab"))
                && properties.size() == 2
                && Set.of("bottom", "top", "double").contains(properties.get("type"))
                && Set.of("false", "true").contains(properties.get("waterlogged"))) {
            return block + "[type=" + properties.get("type") + ",waterlogged="
                    + properties.get("waterlogged") + "]";
        }
        throw new IllegalArgumentException("unsupported stronghold state: " + input);
    }

    /** Accepted G1N Trial Chambers exact-state closure; no vanilla cross-product widening. */
    private static void registerTrialChambersG1QStates(
            Map<String, Mc263FeatureBlockState> states) {
        for (CatalogEntry entry : List.of(
                entry(Blocks.COPPER_BLOCK, "minecraft:copper_block"),
                entry(Blocks.MANGROVE_LEAVES, "minecraft:mangrove_leaves[distance=1,persistent=true,waterlogged=false]"),
                entry(Blocks.MANGROVE_LEAVES, "minecraft:mangrove_leaves[distance=2,persistent=true,waterlogged=false]"),
                entry(Blocks.MANGROVE_LEAVES, "minecraft:mangrove_leaves[distance=3,persistent=true,waterlogged=false]"),
                entry(Blocks.MANGROVE_LEAVES, "minecraft:mangrove_leaves[distance=4,persistent=true,waterlogged=false]"),
                entry(Blocks.MANGROVE_LEAVES, "minecraft:mangrove_leaves[distance=7,persistent=true,waterlogged=false]"),
                entry(Blocks.MUDDY_MANGROVE_ROOTS, "minecraft:muddy_mangrove_roots[axis=x]"),
                entry(Blocks.MUDDY_MANGROVE_ROOTS, "minecraft:muddy_mangrove_roots[axis=z]"),
                entry(Blocks.CHISELED_TUFF, "minecraft:chiseled_tuff"),
                entry(Blocks.POLISHED_TUFF, "minecraft:polished_tuff"),
                entry(Blocks.TUFF_BRICKS, "minecraft:tuff_bricks"),
                entry(Blocks.CHISELED_TUFF_BRICKS, "minecraft:chiseled_tuff_bricks"),
                entry(Blocks.POLISHED_TUFF_SLAB, "minecraft:polished_tuff_slab[type=top,waterlogged=false]"),
                entry(Blocks.WHITE_BED, "minecraft:white_bed[facing=north,occupied=false,part=foot]"),
                entry(Blocks.WHITE_BED, "minecraft:white_bed[facing=east,occupied=false,part=foot]"),
                entry(Blocks.WHITE_BED, "minecraft:white_bed[facing=east,occupied=false,part=head]"),
                entry(Blocks.WHITE_BED, "minecraft:white_bed[facing=north,occupied=false,part=head]"),
                entry(Blocks.WHITE_BED, "minecraft:white_bed[facing=south,occupied=false,part=foot]"),
                entry(Blocks.WHITE_BED, "minecraft:white_bed[facing=south,occupied=false,part=head]"),
                entry(Blocks.WHITE_BED, "minecraft:white_bed[facing=west,occupied=false,part=foot]"),
                entry(Blocks.WHITE_BED, "minecraft:white_bed[facing=west,occupied=false,part=head]"),
                entry(Blocks.ORANGE_BED, "minecraft:orange_bed[facing=north,occupied=false,part=foot]"),
                entry(Blocks.ORANGE_BED, "minecraft:orange_bed[facing=east,occupied=false,part=foot]"),
                entry(Blocks.ORANGE_BED, "minecraft:orange_bed[facing=east,occupied=false,part=head]"),
                entry(Blocks.ORANGE_BED, "minecraft:orange_bed[facing=north,occupied=false,part=head]"),
                entry(Blocks.ORANGE_BED, "minecraft:orange_bed[facing=south,occupied=false,part=foot]"),
                entry(Blocks.ORANGE_BED, "minecraft:orange_bed[facing=south,occupied=false,part=head]"),
                entry(Blocks.ORANGE_BED, "minecraft:orange_bed[facing=west,occupied=false,part=foot]"),
                entry(Blocks.ORANGE_BED, "minecraft:orange_bed[facing=west,occupied=false,part=head]"),
                entry(Blocks.MAGENTA_BED, "minecraft:magenta_bed[facing=north,occupied=false,part=foot]"),
                entry(Blocks.MAGENTA_BED, "minecraft:magenta_bed[facing=east,occupied=false,part=foot]"),
                entry(Blocks.MAGENTA_BED, "minecraft:magenta_bed[facing=east,occupied=false,part=head]"),
                entry(Blocks.MAGENTA_BED, "minecraft:magenta_bed[facing=north,occupied=false,part=head]"),
                entry(Blocks.MAGENTA_BED, "minecraft:magenta_bed[facing=south,occupied=false,part=foot]"),
                entry(Blocks.MAGENTA_BED, "minecraft:magenta_bed[facing=south,occupied=false,part=head]"),
                entry(Blocks.MAGENTA_BED, "minecraft:magenta_bed[facing=west,occupied=false,part=foot]"),
                entry(Blocks.MAGENTA_BED, "minecraft:magenta_bed[facing=west,occupied=false,part=head]"),
                entry(Blocks.LIGHT_BLUE_BED, "minecraft:light_blue_bed[facing=north,occupied=false,part=foot]"),
                entry(Blocks.LIGHT_BLUE_BED, "minecraft:light_blue_bed[facing=east,occupied=false,part=foot]"),
                entry(Blocks.LIGHT_BLUE_BED, "minecraft:light_blue_bed[facing=east,occupied=false,part=head]"),
                entry(Blocks.LIGHT_BLUE_BED, "minecraft:light_blue_bed[facing=north,occupied=false,part=head]"),
                entry(Blocks.LIGHT_BLUE_BED, "minecraft:light_blue_bed[facing=south,occupied=false,part=foot]"),
                entry(Blocks.LIGHT_BLUE_BED, "minecraft:light_blue_bed[facing=south,occupied=false,part=head]"),
                entry(Blocks.LIGHT_BLUE_BED, "minecraft:light_blue_bed[facing=west,occupied=false,part=foot]"),
                entry(Blocks.LIGHT_BLUE_BED, "minecraft:light_blue_bed[facing=west,occupied=false,part=head]"),
                entry(Blocks.YELLOW_BED, "minecraft:yellow_bed[facing=north,occupied=false,part=foot]"),
                entry(Blocks.YELLOW_BED, "minecraft:yellow_bed[facing=east,occupied=false,part=foot]"),
                entry(Blocks.YELLOW_BED, "minecraft:yellow_bed[facing=east,occupied=false,part=head]"),
                entry(Blocks.YELLOW_BED, "minecraft:yellow_bed[facing=north,occupied=false,part=head]"),
                entry(Blocks.YELLOW_BED, "minecraft:yellow_bed[facing=south,occupied=false,part=foot]"),
                entry(Blocks.YELLOW_BED, "minecraft:yellow_bed[facing=south,occupied=false,part=head]"),
                entry(Blocks.YELLOW_BED, "minecraft:yellow_bed[facing=west,occupied=false,part=foot]"),
                entry(Blocks.YELLOW_BED, "minecraft:yellow_bed[facing=west,occupied=false,part=head]"),
                entry(Blocks.LIME_BED, "minecraft:lime_bed[facing=north,occupied=false,part=foot]"),
                entry(Blocks.LIME_BED, "minecraft:lime_bed[facing=east,occupied=false,part=foot]"),
                entry(Blocks.LIME_BED, "minecraft:lime_bed[facing=east,occupied=false,part=head]"),
                entry(Blocks.LIME_BED, "minecraft:lime_bed[facing=north,occupied=false,part=head]"),
                entry(Blocks.LIME_BED, "minecraft:lime_bed[facing=south,occupied=false,part=foot]"),
                entry(Blocks.LIME_BED, "minecraft:lime_bed[facing=south,occupied=false,part=head]"),
                entry(Blocks.LIME_BED, "minecraft:lime_bed[facing=west,occupied=false,part=foot]"),
                entry(Blocks.LIME_BED, "minecraft:lime_bed[facing=west,occupied=false,part=head]"),
                entry(Blocks.PINK_BED, "minecraft:pink_bed[facing=north,occupied=false,part=foot]"),
                entry(Blocks.PINK_BED, "minecraft:pink_bed[facing=east,occupied=false,part=foot]"),
                entry(Blocks.PINK_BED, "minecraft:pink_bed[facing=east,occupied=false,part=head]"),
                entry(Blocks.PINK_BED, "minecraft:pink_bed[facing=north,occupied=false,part=head]"),
                entry(Blocks.PINK_BED, "minecraft:pink_bed[facing=south,occupied=false,part=foot]"),
                entry(Blocks.PINK_BED, "minecraft:pink_bed[facing=south,occupied=false,part=head]"),
                entry(Blocks.PINK_BED, "minecraft:pink_bed[facing=west,occupied=false,part=foot]"),
                entry(Blocks.PINK_BED, "minecraft:pink_bed[facing=west,occupied=false,part=head]"),
                entry(Blocks.GRAY_BED, "minecraft:gray_bed[facing=north,occupied=false,part=foot]"),
                entry(Blocks.GRAY_BED, "minecraft:gray_bed[facing=east,occupied=false,part=foot]"),
                entry(Blocks.GRAY_BED, "minecraft:gray_bed[facing=east,occupied=false,part=head]"),
                entry(Blocks.GRAY_BED, "minecraft:gray_bed[facing=north,occupied=false,part=head]"),
                entry(Blocks.GRAY_BED, "minecraft:gray_bed[facing=south,occupied=false,part=foot]"),
                entry(Blocks.GRAY_BED, "minecraft:gray_bed[facing=south,occupied=false,part=head]"),
                entry(Blocks.GRAY_BED, "minecraft:gray_bed[facing=west,occupied=false,part=foot]"),
                entry(Blocks.GRAY_BED, "minecraft:gray_bed[facing=west,occupied=false,part=head]"),
                entry(Blocks.LIGHT_GRAY_BED, "minecraft:light_gray_bed[facing=north,occupied=false,part=foot]"),
                entry(Blocks.LIGHT_GRAY_BED, "minecraft:light_gray_bed[facing=east,occupied=false,part=foot]"),
                entry(Blocks.LIGHT_GRAY_BED, "minecraft:light_gray_bed[facing=east,occupied=false,part=head]"),
                entry(Blocks.LIGHT_GRAY_BED, "minecraft:light_gray_bed[facing=north,occupied=false,part=head]"),
                entry(Blocks.LIGHT_GRAY_BED, "minecraft:light_gray_bed[facing=south,occupied=false,part=foot]"),
                entry(Blocks.LIGHT_GRAY_BED, "minecraft:light_gray_bed[facing=south,occupied=false,part=head]"),
                entry(Blocks.LIGHT_GRAY_BED, "minecraft:light_gray_bed[facing=west,occupied=false,part=foot]"),
                entry(Blocks.LIGHT_GRAY_BED, "minecraft:light_gray_bed[facing=west,occupied=false,part=head]"),
                entry(Blocks.CYAN_BED, "minecraft:cyan_bed[facing=north,occupied=false,part=foot]"),
                entry(Blocks.CYAN_BED, "minecraft:cyan_bed[facing=east,occupied=false,part=foot]"),
                entry(Blocks.CYAN_BED, "minecraft:cyan_bed[facing=east,occupied=false,part=head]"),
                entry(Blocks.CYAN_BED, "minecraft:cyan_bed[facing=north,occupied=false,part=head]"),
                entry(Blocks.CYAN_BED, "minecraft:cyan_bed[facing=south,occupied=false,part=foot]"),
                entry(Blocks.CYAN_BED, "minecraft:cyan_bed[facing=south,occupied=false,part=head]"),
                entry(Blocks.CYAN_BED, "minecraft:cyan_bed[facing=west,occupied=false,part=foot]"),
                entry(Blocks.CYAN_BED, "minecraft:cyan_bed[facing=west,occupied=false,part=head]"),
                entry(Blocks.PURPLE_BED, "minecraft:purple_bed[facing=north,occupied=false,part=foot]"),
                entry(Blocks.PURPLE_BED, "minecraft:purple_bed[facing=east,occupied=false,part=foot]"),
                entry(Blocks.PURPLE_BED, "minecraft:purple_bed[facing=east,occupied=false,part=head]"),
                entry(Blocks.PURPLE_BED, "minecraft:purple_bed[facing=north,occupied=false,part=head]"),
                entry(Blocks.PURPLE_BED, "minecraft:purple_bed[facing=south,occupied=false,part=foot]"),
                entry(Blocks.PURPLE_BED, "minecraft:purple_bed[facing=south,occupied=false,part=head]"),
                entry(Blocks.PURPLE_BED, "minecraft:purple_bed[facing=west,occupied=false,part=foot]"),
                entry(Blocks.PURPLE_BED, "minecraft:purple_bed[facing=west,occupied=false,part=head]"),
                entry(Blocks.BLUE_BED, "minecraft:blue_bed[facing=north,occupied=false,part=foot]"),
                entry(Blocks.BLUE_BED, "minecraft:blue_bed[facing=east,occupied=false,part=foot]"),
                entry(Blocks.BLUE_BED, "minecraft:blue_bed[facing=east,occupied=false,part=head]"),
                entry(Blocks.BLUE_BED, "minecraft:blue_bed[facing=north,occupied=false,part=head]"),
                entry(Blocks.BLUE_BED, "minecraft:blue_bed[facing=south,occupied=false,part=foot]"),
                entry(Blocks.BLUE_BED, "minecraft:blue_bed[facing=south,occupied=false,part=head]"),
                entry(Blocks.BLUE_BED, "minecraft:blue_bed[facing=west,occupied=false,part=foot]"),
                entry(Blocks.BLUE_BED, "minecraft:blue_bed[facing=west,occupied=false,part=head]"),
                entry(Blocks.BROWN_BED, "minecraft:brown_bed[facing=north,occupied=false,part=foot]"),
                entry(Blocks.BROWN_BED, "minecraft:brown_bed[facing=east,occupied=false,part=foot]"),
                entry(Blocks.BROWN_BED, "minecraft:brown_bed[facing=east,occupied=false,part=head]"),
                entry(Blocks.BROWN_BED, "minecraft:brown_bed[facing=north,occupied=false,part=head]"),
                entry(Blocks.BROWN_BED, "minecraft:brown_bed[facing=south,occupied=false,part=foot]"),
                entry(Blocks.BROWN_BED, "minecraft:brown_bed[facing=south,occupied=false,part=head]"),
                entry(Blocks.BROWN_BED, "minecraft:brown_bed[facing=west,occupied=false,part=foot]"),
                entry(Blocks.BROWN_BED, "minecraft:brown_bed[facing=west,occupied=false,part=head]"),
                entry(Blocks.GREEN_BED, "minecraft:green_bed[facing=north,occupied=false,part=foot]"),
                entry(Blocks.GREEN_BED, "minecraft:green_bed[facing=east,occupied=false,part=foot]"),
                entry(Blocks.GREEN_BED, "minecraft:green_bed[facing=east,occupied=false,part=head]"),
                entry(Blocks.GREEN_BED, "minecraft:green_bed[facing=north,occupied=false,part=head]"),
                entry(Blocks.GREEN_BED, "minecraft:green_bed[facing=south,occupied=false,part=foot]"),
                entry(Blocks.GREEN_BED, "minecraft:green_bed[facing=south,occupied=false,part=head]"),
                entry(Blocks.GREEN_BED, "minecraft:green_bed[facing=west,occupied=false,part=foot]"),
                entry(Blocks.GREEN_BED, "minecraft:green_bed[facing=west,occupied=false,part=head]"),
                entry(Blocks.BLACK_BED, "minecraft:black_bed[facing=north,occupied=false,part=foot]"),
                entry(Blocks.BLACK_BED, "minecraft:black_bed[facing=east,occupied=false,part=foot]"),
                entry(Blocks.BLACK_BED, "minecraft:black_bed[facing=east,occupied=false,part=head]"),
                entry(Blocks.BLACK_BED, "minecraft:black_bed[facing=north,occupied=false,part=head]"),
                entry(Blocks.BLACK_BED, "minecraft:black_bed[facing=south,occupied=false,part=foot]"),
                entry(Blocks.BLACK_BED, "minecraft:black_bed[facing=south,occupied=false,part=head]"),
                entry(Blocks.BLACK_BED, "minecraft:black_bed[facing=west,occupied=false,part=foot]"),
                entry(Blocks.BLACK_BED, "minecraft:black_bed[facing=west,occupied=false,part=head]"),
                entry(Blocks.OXIDIZED_CUT_COPPER, "minecraft:oxidized_cut_copper"),
                entry(Blocks.OXIDIZED_COPPER_TRAPDOOR, "minecraft:oxidized_copper_trapdoor[facing=north,half=top,open=false,powered=false,waterlogged=false]"),
                entry(Blocks.OXIDIZED_COPPER_TRAPDOOR, "minecraft:oxidized_copper_trapdoor[facing=east,half=top,open=false,powered=false,waterlogged=false]"),
                entry(Blocks.OXIDIZED_COPPER_TRAPDOOR, "minecraft:oxidized_copper_trapdoor[facing=south,half=top,open=false,powered=false,waterlogged=false]"),
                entry(Blocks.OXIDIZED_COPPER_TRAPDOOR, "minecraft:oxidized_copper_trapdoor[facing=west,half=top,open=false,powered=false,waterlogged=false]"),
                entry(Blocks.WAXED_COPPER_BLOCK, "minecraft:waxed_copper_block"),
                entry(Blocks.WAXED_OXIDIZED_COPPER, "minecraft:waxed_oxidized_copper"),
                entry(Blocks.WAXED_CUT_COPPER, "minecraft:waxed_cut_copper"),
                entry(Blocks.WAXED_OXIDIZED_CUT_COPPER, "minecraft:waxed_oxidized_cut_copper"),
                entry(Blocks.WAXED_CUT_COPPER_STAIRS, "minecraft:waxed_cut_copper_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]"),
                entry(Blocks.WAXED_CUT_COPPER_STAIRS, "minecraft:waxed_cut_copper_stairs[facing=east,half=bottom,shape=straight,waterlogged=false]"),
                entry(Blocks.WAXED_CUT_COPPER_STAIRS, "minecraft:waxed_cut_copper_stairs[facing=south,half=bottom,shape=straight,waterlogged=false]"),
                entry(Blocks.WAXED_CUT_COPPER_STAIRS, "minecraft:waxed_cut_copper_stairs[facing=west,half=bottom,shape=straight,waterlogged=false]"),
                entry(Blocks.WAXED_OXIDIZED_CUT_COPPER_STAIRS, "minecraft:waxed_oxidized_cut_copper_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]"),
                entry(Blocks.WAXED_OXIDIZED_CUT_COPPER_STAIRS, "minecraft:waxed_oxidized_cut_copper_stairs[facing=east,half=bottom,shape=inner_left,waterlogged=true]"),
                entry(Blocks.WAXED_OXIDIZED_CUT_COPPER_STAIRS, "minecraft:waxed_oxidized_cut_copper_stairs[facing=east,half=bottom,shape=inner_right,waterlogged=true]"),
                entry(Blocks.WAXED_OXIDIZED_CUT_COPPER_STAIRS, "minecraft:waxed_oxidized_cut_copper_stairs[facing=east,half=bottom,shape=outer_left,waterlogged=false]"),
                entry(Blocks.WAXED_OXIDIZED_CUT_COPPER_STAIRS, "minecraft:waxed_oxidized_cut_copper_stairs[facing=east,half=bottom,shape=outer_left,waterlogged=true]"),
                entry(Blocks.WAXED_OXIDIZED_CUT_COPPER_STAIRS, "minecraft:waxed_oxidized_cut_copper_stairs[facing=east,half=bottom,shape=outer_right,waterlogged=false]"),
                entry(Blocks.WAXED_OXIDIZED_CUT_COPPER_STAIRS, "minecraft:waxed_oxidized_cut_copper_stairs[facing=east,half=bottom,shape=outer_right,waterlogged=true]"),
                entry(Blocks.WAXED_OXIDIZED_CUT_COPPER_STAIRS, "minecraft:waxed_oxidized_cut_copper_stairs[facing=east,half=bottom,shape=straight,waterlogged=false]"),
                entry(Blocks.WAXED_OXIDIZED_CUT_COPPER_STAIRS, "minecraft:waxed_oxidized_cut_copper_stairs[facing=east,half=bottom,shape=straight,waterlogged=true]"),
                entry(Blocks.WAXED_OXIDIZED_CUT_COPPER_STAIRS, "minecraft:waxed_oxidized_cut_copper_stairs[facing=north,half=bottom,shape=inner_left,waterlogged=true]"),
                entry(Blocks.WAXED_OXIDIZED_CUT_COPPER_STAIRS, "minecraft:waxed_oxidized_cut_copper_stairs[facing=north,half=bottom,shape=inner_right,waterlogged=true]"),
                entry(Blocks.WAXED_OXIDIZED_CUT_COPPER_STAIRS, "minecraft:waxed_oxidized_cut_copper_stairs[facing=north,half=bottom,shape=outer_left,waterlogged=false]"),
                entry(Blocks.WAXED_OXIDIZED_CUT_COPPER_STAIRS, "minecraft:waxed_oxidized_cut_copper_stairs[facing=north,half=bottom,shape=outer_left,waterlogged=true]"),
                entry(Blocks.WAXED_OXIDIZED_CUT_COPPER_STAIRS, "minecraft:waxed_oxidized_cut_copper_stairs[facing=north,half=bottom,shape=outer_right,waterlogged=false]"),
                entry(Blocks.WAXED_OXIDIZED_CUT_COPPER_STAIRS, "minecraft:waxed_oxidized_cut_copper_stairs[facing=north,half=bottom,shape=outer_right,waterlogged=true]"),
                entry(Blocks.WAXED_OXIDIZED_CUT_COPPER_STAIRS, "minecraft:waxed_oxidized_cut_copper_stairs[facing=north,half=bottom,shape=straight,waterlogged=true]"),
                entry(Blocks.WAXED_OXIDIZED_CUT_COPPER_STAIRS, "minecraft:waxed_oxidized_cut_copper_stairs[facing=south,half=bottom,shape=inner_left,waterlogged=true]"),
                entry(Blocks.WAXED_OXIDIZED_CUT_COPPER_STAIRS, "minecraft:waxed_oxidized_cut_copper_stairs[facing=south,half=bottom,shape=inner_right,waterlogged=true]"),
                entry(Blocks.WAXED_OXIDIZED_CUT_COPPER_STAIRS, "minecraft:waxed_oxidized_cut_copper_stairs[facing=south,half=bottom,shape=outer_left,waterlogged=false]"),
                entry(Blocks.WAXED_OXIDIZED_CUT_COPPER_STAIRS, "minecraft:waxed_oxidized_cut_copper_stairs[facing=south,half=bottom,shape=outer_left,waterlogged=true]"),
                entry(Blocks.WAXED_OXIDIZED_CUT_COPPER_STAIRS, "minecraft:waxed_oxidized_cut_copper_stairs[facing=south,half=bottom,shape=outer_right,waterlogged=false]"),
                entry(Blocks.WAXED_OXIDIZED_CUT_COPPER_STAIRS, "minecraft:waxed_oxidized_cut_copper_stairs[facing=south,half=bottom,shape=outer_right,waterlogged=true]"),
                entry(Blocks.WAXED_OXIDIZED_CUT_COPPER_STAIRS, "minecraft:waxed_oxidized_cut_copper_stairs[facing=south,half=bottom,shape=straight,waterlogged=false]"),
                entry(Blocks.WAXED_OXIDIZED_CUT_COPPER_STAIRS, "minecraft:waxed_oxidized_cut_copper_stairs[facing=south,half=bottom,shape=straight,waterlogged=true]"),
                entry(Blocks.WAXED_OXIDIZED_CUT_COPPER_STAIRS, "minecraft:waxed_oxidized_cut_copper_stairs[facing=west,half=bottom,shape=inner_left,waterlogged=true]"),
                entry(Blocks.WAXED_OXIDIZED_CUT_COPPER_STAIRS, "minecraft:waxed_oxidized_cut_copper_stairs[facing=west,half=bottom,shape=inner_right,waterlogged=true]"),
                entry(Blocks.WAXED_OXIDIZED_CUT_COPPER_STAIRS, "minecraft:waxed_oxidized_cut_copper_stairs[facing=west,half=bottom,shape=outer_left,waterlogged=false]"),
                entry(Blocks.WAXED_OXIDIZED_CUT_COPPER_STAIRS, "minecraft:waxed_oxidized_cut_copper_stairs[facing=west,half=bottom,shape=outer_left,waterlogged=true]"),
                entry(Blocks.WAXED_OXIDIZED_CUT_COPPER_STAIRS, "minecraft:waxed_oxidized_cut_copper_stairs[facing=west,half=bottom,shape=outer_right,waterlogged=false]"),
                entry(Blocks.WAXED_OXIDIZED_CUT_COPPER_STAIRS, "minecraft:waxed_oxidized_cut_copper_stairs[facing=west,half=bottom,shape=outer_right,waterlogged=true]"),
                entry(Blocks.WAXED_OXIDIZED_CUT_COPPER_STAIRS, "minecraft:waxed_oxidized_cut_copper_stairs[facing=west,half=bottom,shape=straight,waterlogged=false]"),
                entry(Blocks.WAXED_OXIDIZED_CUT_COPPER_STAIRS, "minecraft:waxed_oxidized_cut_copper_stairs[facing=west,half=bottom,shape=straight,waterlogged=true]"),
                entry(Blocks.WAXED_CUT_COPPER_SLAB, "minecraft:waxed_cut_copper_slab[type=bottom,waterlogged=false]"),
                entry(Blocks.WAXED_CUT_COPPER_SLAB, "minecraft:waxed_cut_copper_slab[type=top,waterlogged=false]"),
                entry(Blocks.WAXED_OXIDIZED_CUT_COPPER_SLAB, "minecraft:waxed_oxidized_cut_copper_slab[type=bottom,waterlogged=false]"),
                entry(Blocks.WAXED_OXIDIZED_CUT_COPPER_SLAB, "minecraft:waxed_oxidized_cut_copper_slab[type=top,waterlogged=false]"),
                entry(Blocks.WAXED_CHISELED_COPPER, "minecraft:waxed_chiseled_copper"),
                entry(Blocks.WAXED_OXIDIZED_CHISELED_COPPER, "minecraft:waxed_oxidized_chiseled_copper"),
                entry(Blocks.WAXED_COPPER_GRATE, "minecraft:waxed_copper_grate[waterlogged=false]"),
                entry(Blocks.WAXED_OXIDIZED_COPPER_GRATE, "minecraft:waxed_oxidized_copper_grate[waterlogged=false]"),
                entry(Blocks.WAXED_COPPER_BULB, "minecraft:waxed_copper_bulb[lit=false,powered=false]"),
                entry(Blocks.WAXED_COPPER_BULB, "minecraft:waxed_copper_bulb[lit=true,powered=false]"),
                entry(Blocks.WAXED_EXPOSED_COPPER_BULB, "minecraft:waxed_exposed_copper_bulb[lit=true,powered=false]"),
                entry(Blocks.WAXED_WEATHERED_COPPER_BULB, "minecraft:waxed_weathered_copper_bulb[lit=true,powered=false]"),
                entry(Blocks.WAXED_OXIDIZED_COPPER_BULB, "minecraft:waxed_oxidized_copper_bulb[lit=true,powered=false]"),
                entry(Blocks.WAXED_COPPER_DOOR, "minecraft:waxed_copper_door[facing=north,half=lower,hinge=left,open=false,powered=false]"),
                entry(Blocks.WAXED_COPPER_DOOR, "minecraft:waxed_copper_door[facing=east,half=lower,hinge=left,open=false,powered=false]"),
                entry(Blocks.WAXED_COPPER_DOOR, "minecraft:waxed_copper_door[facing=east,half=lower,hinge=right,open=false,powered=false]"),
                entry(Blocks.WAXED_COPPER_DOOR, "minecraft:waxed_copper_door[facing=east,half=upper,hinge=left,open=false,powered=false]"),
                entry(Blocks.WAXED_COPPER_DOOR, "minecraft:waxed_copper_door[facing=east,half=upper,hinge=right,open=false,powered=false]"),
                entry(Blocks.WAXED_COPPER_DOOR, "minecraft:waxed_copper_door[facing=north,half=lower,hinge=right,open=false,powered=false]"),
                entry(Blocks.WAXED_COPPER_DOOR, "minecraft:waxed_copper_door[facing=north,half=upper,hinge=left,open=false,powered=false]"),
                entry(Blocks.WAXED_COPPER_DOOR, "minecraft:waxed_copper_door[facing=north,half=upper,hinge=right,open=false,powered=false]"),
                entry(Blocks.WAXED_COPPER_DOOR, "minecraft:waxed_copper_door[facing=south,half=lower,hinge=left,open=false,powered=false]"),
                entry(Blocks.WAXED_COPPER_DOOR, "minecraft:waxed_copper_door[facing=south,half=lower,hinge=right,open=false,powered=false]"),
                entry(Blocks.WAXED_COPPER_DOOR, "minecraft:waxed_copper_door[facing=south,half=upper,hinge=left,open=false,powered=false]"),
                entry(Blocks.WAXED_COPPER_DOOR, "minecraft:waxed_copper_door[facing=south,half=upper,hinge=right,open=false,powered=false]"),
                entry(Blocks.WAXED_COPPER_DOOR, "minecraft:waxed_copper_door[facing=west,half=lower,hinge=left,open=false,powered=false]"),
                entry(Blocks.WAXED_COPPER_DOOR, "minecraft:waxed_copper_door[facing=west,half=lower,hinge=right,open=false,powered=false]"),
                entry(Blocks.WAXED_COPPER_DOOR, "minecraft:waxed_copper_door[facing=west,half=upper,hinge=left,open=false,powered=false]"),
                entry(Blocks.WAXED_COPPER_DOOR, "minecraft:waxed_copper_door[facing=west,half=upper,hinge=right,open=false,powered=false]"),
                entry(Blocks.WAXED_OXIDIZED_COPPER_DOOR, "minecraft:waxed_oxidized_copper_door[facing=north,half=lower,hinge=left,open=false,powered=false]"),
                entry(Blocks.WAXED_OXIDIZED_COPPER_DOOR, "minecraft:waxed_oxidized_copper_door[facing=east,half=lower,hinge=left,open=false,powered=false]"),
                entry(Blocks.WAXED_OXIDIZED_COPPER_DOOR, "minecraft:waxed_oxidized_copper_door[facing=east,half=lower,hinge=right,open=false,powered=false]"),
                entry(Blocks.WAXED_OXIDIZED_COPPER_DOOR, "minecraft:waxed_oxidized_copper_door[facing=east,half=upper,hinge=left,open=false,powered=false]"),
                entry(Blocks.WAXED_OXIDIZED_COPPER_DOOR, "minecraft:waxed_oxidized_copper_door[facing=east,half=upper,hinge=right,open=false,powered=false]"),
                entry(Blocks.WAXED_OXIDIZED_COPPER_DOOR, "minecraft:waxed_oxidized_copper_door[facing=north,half=lower,hinge=right,open=false,powered=false]"),
                entry(Blocks.WAXED_OXIDIZED_COPPER_DOOR, "minecraft:waxed_oxidized_copper_door[facing=north,half=upper,hinge=left,open=false,powered=false]"),
                entry(Blocks.WAXED_OXIDIZED_COPPER_DOOR, "minecraft:waxed_oxidized_copper_door[facing=north,half=upper,hinge=right,open=false,powered=false]"),
                entry(Blocks.WAXED_OXIDIZED_COPPER_DOOR, "minecraft:waxed_oxidized_copper_door[facing=south,half=lower,hinge=left,open=false,powered=false]"),
                entry(Blocks.WAXED_OXIDIZED_COPPER_DOOR, "minecraft:waxed_oxidized_copper_door[facing=south,half=lower,hinge=right,open=false,powered=false]"),
                entry(Blocks.WAXED_OXIDIZED_COPPER_DOOR, "minecraft:waxed_oxidized_copper_door[facing=south,half=upper,hinge=left,open=false,powered=false]"),
                entry(Blocks.WAXED_OXIDIZED_COPPER_DOOR, "minecraft:waxed_oxidized_copper_door[facing=south,half=upper,hinge=right,open=false,powered=false]"),
                entry(Blocks.WAXED_OXIDIZED_COPPER_DOOR, "minecraft:waxed_oxidized_copper_door[facing=west,half=lower,hinge=left,open=false,powered=false]"),
                entry(Blocks.WAXED_OXIDIZED_COPPER_DOOR, "minecraft:waxed_oxidized_copper_door[facing=west,half=lower,hinge=right,open=false,powered=false]"),
                entry(Blocks.WAXED_OXIDIZED_COPPER_DOOR, "minecraft:waxed_oxidized_copper_door[facing=west,half=upper,hinge=left,open=false,powered=false]"),
                entry(Blocks.WAXED_OXIDIZED_COPPER_DOOR, "minecraft:waxed_oxidized_copper_door[facing=west,half=upper,hinge=right,open=false,powered=false]"),
                entry(Blocks.WAXED_OXIDIZED_COPPER_TRAPDOOR, "minecraft:waxed_oxidized_copper_trapdoor[facing=north,half=top,open=false,powered=false,waterlogged=false]"),
                entry(Blocks.WAXED_OXIDIZED_COPPER_TRAPDOOR, "minecraft:waxed_oxidized_copper_trapdoor[facing=east,half=top,open=false,powered=false,waterlogged=false]"),
                entry(Blocks.WAXED_OXIDIZED_COPPER_TRAPDOOR, "minecraft:waxed_oxidized_copper_trapdoor[facing=east,half=top,open=true,powered=false,waterlogged=false]"),
                entry(Blocks.WAXED_OXIDIZED_COPPER_TRAPDOOR, "minecraft:waxed_oxidized_copper_trapdoor[facing=north,half=top,open=true,powered=false,waterlogged=false]"),
                entry(Blocks.WAXED_OXIDIZED_COPPER_TRAPDOOR, "minecraft:waxed_oxidized_copper_trapdoor[facing=south,half=top,open=false,powered=false,waterlogged=false]"),
                entry(Blocks.WAXED_OXIDIZED_COPPER_TRAPDOOR, "minecraft:waxed_oxidized_copper_trapdoor[facing=south,half=top,open=true,powered=false,waterlogged=false]"),
                entry(Blocks.WAXED_OXIDIZED_COPPER_TRAPDOOR, "minecraft:waxed_oxidized_copper_trapdoor[facing=west,half=top,open=false,powered=false,waterlogged=false]"),
                entry(Blocks.WAXED_OXIDIZED_COPPER_TRAPDOOR, "minecraft:waxed_oxidized_copper_trapdoor[facing=west,half=top,open=true,powered=false,waterlogged=false]"),
                entry(Blocks.WHITE_CONCRETE, "minecraft:white_concrete"),
                entry(Blocks.RED_CONCRETE, "minecraft:red_concrete"),
                entry(Blocks.RED_GLAZED_TERRACOTTA, "minecraft:red_glazed_terracotta[facing=north]"),
                entry(Blocks.RED_GLAZED_TERRACOTTA, "minecraft:red_glazed_terracotta[facing=east]"),
                entry(Blocks.RED_GLAZED_TERRACOTTA, "minecraft:red_glazed_terracotta[facing=south]"),
                entry(Blocks.RED_GLAZED_TERRACOTTA, "minecraft:red_glazed_terracotta[facing=west]"),
                entry(Blocks.WHITE_STAINED_GLASS, "minecraft:white_stained_glass"),
                entry(Blocks.LIGHT_GRAY_STAINED_GLASS, "minecraft:light_gray_stained_glass"),
                entry(Blocks.BLACK_STAINED_GLASS, "minecraft:black_stained_glass"),
                entry(Blocks.TRIAL_SPAWNER, "minecraft:trial_spawner[ominous=false,trial_spawner_state=waiting_for_players]"),
                entry(Blocks.VAULT, "minecraft:vault[facing=north,ominous=false,vault_state=inactive]"),
                entry(Blocks.VAULT, "minecraft:vault[facing=east,ominous=false,vault_state=inactive]"),
                entry(Blocks.VAULT, "minecraft:vault[facing=east,ominous=true,vault_state=inactive]"),
                entry(Blocks.VAULT, "minecraft:vault[facing=north,ominous=true,vault_state=inactive]"),
                entry(Blocks.VAULT, "minecraft:vault[facing=south,ominous=false,vault_state=inactive]"),
                entry(Blocks.VAULT, "minecraft:vault[facing=south,ominous=true,vault_state=inactive]"),
                entry(Blocks.VAULT, "minecraft:vault[facing=west,ominous=false,vault_state=inactive]"),
                entry(Blocks.VAULT, "minecraft:vault[facing=west,ominous=true,vault_state=inactive]"),
                entry(Blocks.CHAIN, "minecraft:iron_chain[axis=y,waterlogged=false]"),
                entry(Blocks.DECORATED_POT, "minecraft:decorated_pot[cracked=false,facing=north,waterlogged=false]"),
                entry(Blocks.DECORATED_POT, "minecraft:decorated_pot[cracked=false,facing=east,waterlogged=false]"),
                entry(Blocks.DECORATED_POT, "minecraft:decorated_pot[cracked=false,facing=south,waterlogged=false]"),
                entry(Blocks.DECORATED_POT, "minecraft:decorated_pot[cracked=false,facing=west,waterlogged=false]"),
                entry(Blocks.TRIPWIRE_HOOK_BLOCK, "minecraft:tripwire_hook[attached=false,facing=east,powered=false]"),
                entry(Blocks.TRIPWIRE_HOOK_BLOCK, "minecraft:tripwire_hook[attached=false,facing=south,powered=false]"),
                entry(Blocks.TRIPWIRE_HOOK_BLOCK, "minecraft:tripwire_hook[attached=false,facing=west,powered=false]"),
                entry(Blocks.TRIPWIRE, "minecraft:tripwire[attached=false,disarmed=false,east=false,north=false,powered=false,south=false,west=true]"),
                entry(Blocks.TRIPWIRE, "minecraft:tripwire[attached=false,disarmed=false,east=false,north=false,powered=false,south=true,west=false]"),
                entry(Blocks.TRIPWIRE, "minecraft:tripwire[attached=false,disarmed=false,east=false,north=true,powered=false,south=false,west=false]"),
                entry(Blocks.TRIPWIRE, "minecraft:tripwire[attached=false,disarmed=false,east=false,north=true,powered=false,south=true,west=false]"),
                entry(Blocks.TRIPWIRE, "minecraft:tripwire[attached=false,disarmed=false,east=true,north=false,powered=false,south=false,west=false]"),
                entry(Blocks.TRIPWIRE, "minecraft:tripwire[attached=false,disarmed=false,east=true,north=false,powered=false,south=false,west=true]"),
                entry(Blocks.DISPENSER, "minecraft:dispenser[facing=up,triggered=false]"),
                entry(Blocks.RED_CANDLE, "minecraft:red_candle[candles=3,lit=true,waterlogged=false]"),
                entry(Blocks.RED_CANDLE, "minecraft:red_candle[candles=4,lit=true,waterlogged=false]"),
                entry(Blocks.FLOWER_POT, "minecraft:flower_pot"),
                entry(Blocks.CANDLE, "minecraft:candle[candles=1,lit=false,waterlogged=false]"),
                entry(Blocks.CANDLE, "minecraft:candle[candles=2,lit=false,waterlogged=false]"),
                entry(Blocks.CANDLE, "minecraft:candle[candles=3,lit=false,waterlogged=false]"),
                entry(Blocks.CANDLE, "minecraft:candle[candles=4,lit=false,waterlogged=false]"),
                entry(Blocks.MANGROVE_WOOD, "minecraft:mangrove_wood[axis=y]"),
                entry(Blocks.MANGROVE_WOOD, "minecraft:mangrove_wood[axis=x]"),
                entry(Blocks.MANGROVE_WOOD, "minecraft:mangrove_wood[axis=z]"),
                entry(Blocks.POTTED_DEAD_BUSH, "minecraft:potted_dead_bush"),
                entry(Blocks.HOPPER, "minecraft:hopper[enabled=true,facing=down]"),
                entry(Blocks.OAK_BUTTON, "minecraft:oak_button[face=wall,facing=north,powered=false]"),
                entry(Blocks.OAK_BUTTON, "minecraft:oak_button[face=floor,facing=east,powered=false]"),
                entry(Blocks.OAK_BUTTON, "minecraft:oak_button[face=floor,facing=north,powered=false]"),
                entry(Blocks.OAK_BUTTON, "minecraft:oak_button[face=floor,facing=south,powered=false]"),
                entry(Blocks.OAK_BUTTON, "minecraft:oak_button[face=floor,facing=west,powered=false]"),
                entry(Blocks.OAK_BUTTON, "minecraft:oak_button[face=wall,facing=east,powered=false]"),
                entry(Blocks.OAK_BUTTON, "minecraft:oak_button[face=wall,facing=south,powered=false]"),
                entry(Blocks.OAK_BUTTON, "minecraft:oak_button[face=wall,facing=west,powered=false]"),
                entry(Blocks.OAK_SLAB, "minecraft:oak_slab[type=bottom,waterlogged=false]"),
                entry(Blocks.OAK_PRESSURE_PLATE, "minecraft:oak_pressure_plate[powered=false]"))) {
            registerTrialChambersG1QState(states, entry);
        }
    }

    private static void registerTrialChambersG1QState(
            Map<String, Mc263FeatureBlockState> states, CatalogEntry entry) {
        String key = blockKey(entry.exact());
        Mc263FeatureBlockState prior = defaultOrNullForId(entry.id());
        boolean waterlogged = entry.exact().contains("waterlogged=true");
        boolean solid;
        boolean solidRender;
        Capability capability;
        if (prior != null) {
            solid = prior.solid;
            solidRender = prior.solidRender;
            capability = prior.capability;
        } else {
            solid = switch (key) {
                case "minecraft:white_bed", "minecraft:orange_bed",
                        "minecraft:magenta_bed", "minecraft:light_blue_bed",
                        "minecraft:yellow_bed", "minecraft:lime_bed",
                        "minecraft:pink_bed", "minecraft:gray_bed",
                        "minecraft:light_gray_bed", "minecraft:cyan_bed",
                        "minecraft:purple_bed", "minecraft:blue_bed",
                        "minecraft:brown_bed", "minecraft:green_bed",
                        "minecraft:black_bed", "minecraft:candle",
                        "minecraft:red_candle", "minecraft:flower_pot",
                        "minecraft:potted_dead_bush", "minecraft:oak_button",
                        "minecraft:oxidized_copper_trapdoor",
                        "minecraft:waxed_oxidized_copper_trapdoor" -> false;
                default -> true;
            };
            solidRender = solid && switch (key) {
                case "minecraft:polished_tuff_slab",
                        "minecraft:oxidized_copper_trapdoor",
                        "minecraft:waxed_cut_copper_stairs",
                        "minecraft:waxed_oxidized_cut_copper_stairs",
                        "minecraft:waxed_cut_copper_slab",
                        "minecraft:waxed_oxidized_cut_copper_slab",
                        "minecraft:waxed_copper_grate",
                        "minecraft:waxed_oxidized_copper_grate",
                        "minecraft:waxed_copper_door",
                        "minecraft:waxed_oxidized_copper_door",
                        "minecraft:waxed_oxidized_copper_trapdoor",
                        "minecraft:white_stained_glass",
                        "minecraft:light_gray_stained_glass",
                        "minecraft:black_stained_glass",
                        "minecraft:trial_spawner", "minecraft:vault",
                        "minecraft:decorated_pot", "minecraft:hopper",
                        "minecraft:oak_slab", "minecraft:oak_pressure_plate" -> false;
                default -> true;
            };
            capability = key.equals("minecraft:hopper")
                    ? Capability.RANDOMIZABLE_CONTAINER : Capability.NONE;
        }
        if (prior == null) {
            registerDefault(states, entry.id(), entry.exact(), solid, solidRender,
                    waterlogged ? FluidKind.WATER_SOURCE : FluidKind.NONE,
                    waterlogged ? 8 : 0, capability);
        } else {
            registerAlias(states, entry.id(), entry.exact(), solid, solidRender,
                    waterlogged ? FluidKind.WATER_SOURCE : FluidKind.NONE,
                    waterlogged ? 8 : 0, capability);
        }
    }

    private static void registerVillageG3J15States(Map<String, Mc263FeatureBlockState> states) {
        for (CatalogEntry entry : List.of(
                entry(Blocks.ACACIA_PLANK, "minecraft:acacia_planks"),
                entry(Blocks.LIME_TERRACOTTA, "minecraft:lime_terracotta"),
                entry(Blocks.SMOOTH_SANDSTONE, "minecraft:smooth_sandstone"),
                entry(Blocks.SMOOTH_STONE, "minecraft:smooth_stone"))) {
            registerVillageState(states, entry.id(), entry.exact(), true, true, Capability.NONE);
        }
        for (CatalogEntry carpet : List.of(
                entry(Blocks.BLUE_CARPET, "minecraft:blue_carpet"),
                entry(Blocks.GREEN_CARPET, "minecraft:green_carpet"),
                entry(Blocks.ORANGE_CARPET, "minecraft:orange_carpet"))) {
            registerVillageState(states, carpet.id(), carpet.exact(), false, false, Capability.NONE);
        }
        for (CatalogEntry entry : List.of(
                entry(Blocks.ACACIA_WOOD, "minecraft:acacia_wood"),
                entry(Blocks.STRIPPED_OAK_WOOD, "minecraft:stripped_oak_wood"),
                entry(Blocks.STRIPPED_SPRUCE_WOOD, "minecraft:stripped_spruce_wood"),
                entry(Blocks.HAY_BLOCK, "minecraft:hay_block"))) {
            for (String axis : List.of("y", "x", "z"))
                registerVillageState(states, entry.id(), entry.exact() + "[axis=" + axis + "]",
                        true, true, Capability.NONE);
        }
        registerVillageBounded(states, Blocks.ACACIA_SAPLING, "minecraft:acacia_sapling", "stage", 0, 1);
        registerVillageBounded(states, Blocks.BEETROOT_CROP, "minecraft:beetroots", "age", 0, 3);
        registerVillageBounded(states, Blocks.WHEAT_CROP, "minecraft:wheat", "age", 0, 7);
        registerVillageBounded(states, Blocks.CARROT_CROP, "minecraft:carrots", "age", 0, 7);
        registerVillageBounded(states, Blocks.POTATO_CROP, "minecraft:potatoes", "age", 0, 7);
        registerVillageBounded(states, Blocks.PUMPKIN_STEM, "minecraft:pumpkin_stem", "age", 0, 7);
        registerVillageBounded(states, Blocks.MELON_STEM, "minecraft:melon_stem", "age", 0, 7);

        for (CatalogEntry door : List.of(
                entry(Blocks.DOOR_CLOSED, "minecraft:oak_door"),
                entry(Blocks.SPRUCE_DOOR, "minecraft:spruce_door"),
                entry(Blocks.JUNGLE_DOOR, "minecraft:jungle_door"),
                entry(Blocks.ACACIA_DOOR, "minecraft:acacia_door"))) {
            for (String facing : List.of("north", "east", "south", "west"))
                for (String half : List.of("lower", "upper"))
                    for (String hinge : List.of("left", "right"))
                        for (boolean open : new boolean[]{false, true})
                            registerVillageState(states, door.id(), door.exact()
                                    + "[facing=" + facing + ",half=" + half + ",hinge=" + hinge
                                    + ",open=" + open + ",powered=false]", true, false, Capability.NONE);
        }
        for (CatalogEntry fence : List.of(
                entry(Blocks.SPRUCE_FENCE, "minecraft:spruce_fence"),
                entry(Blocks.ACACIA_FENCE, "minecraft:acacia_fence")))
            for (boolean east : new boolean[]{false, true})
                for (boolean north : new boolean[]{false, true})
                    for (boolean south : new boolean[]{false, true})
                        for (boolean west : new boolean[]{false, true})
                            registerVillageState(states, fence.id(), fence.exact()
                                    + "[east=" + east + ",north=" + north + ",south=" + south
                                    + ",waterlogged=false,west=" + west + "]", true, false, Capability.NONE);

        for (CatalogEntry slab : List.of(
                entry(Blocks.SPRUCE_SLAB, "minecraft:spruce_slab"),
                entry(Blocks.ACACIA_SLAB, "minecraft:acacia_slab"),
                entry(Blocks.SANDSTONE_SLAB, "minecraft:sandstone_slab"),
                entry(Blocks.SMOOTH_SANDSTONE_SLAB, "minecraft:smooth_sandstone_slab")))
            for (String type : List.of("bottom", "top", "double"))
                registerVillageState(states, slab.id(), slab.exact() + "[type=" + type
                        + ",waterlogged=false]", true, type.equals("double"), Capability.NONE);

        for (CatalogEntry stair : List.of(
                entry(Blocks.WOOD_STAIRS, "minecraft:oak_stairs"),
                entry(Blocks.SPRUCE_STAIRS, "minecraft:spruce_stairs"),
                entry(Blocks.ACACIA_STAIRS, "minecraft:acacia_stairs"),
                entry(Blocks.DIORITE_STAIRS, "minecraft:diorite_stairs"),
                entry(Blocks.SMOOTH_SANDSTONE_STAIRS, "minecraft:smooth_sandstone_stairs")))
            for (String facing : List.of("north", "east", "south", "west"))
                for (String shape : List.of("straight", "inner_left", "inner_right", "outer_left", "outer_right"))
                    registerVillageState(states, stair.id(), stair.exact() + "[facing=" + facing
                            + ",half=bottom,shape=" + shape + ",waterlogged=false]",
                            true, false, Capability.NONE);

        for (CatalogEntry trapdoor : List.of(
                entry(Blocks.WOOD_TRAPDOOR, "minecraft:oak_trapdoor"),
                entry(Blocks.SPRUCE_TRAPDOOR, "minecraft:spruce_trapdoor")))
            for (String facing : List.of("north", "east", "south", "west"))
                for (String half : List.of("bottom", "top"))
                    for (boolean open : new boolean[]{false, true})
                        registerVillageState(states, trapdoor.id(), trapdoor.exact()
                                + "[facing=" + facing + ",half=" + half + ",open=" + open
                                + ",powered=false,waterlogged=false]", true, false, Capability.NONE);

        for (String facing : List.of("north", "east", "south", "west"))
            for (boolean inWall : new boolean[]{false, true})
                for (boolean open : new boolean[]{false, true})
                    registerVillageState(states, Blocks.WOOD_FENCE_GATE,
                            "minecraft:oak_fence_gate[facing=" + facing + ",in_wall=" + inWall
                                    + ",open=" + open + ",powered=false]",
                            true, false, Capability.NONE);
        for (boolean b0 : new boolean[]{false, true})
            for (boolean b1 : new boolean[]{false, true})
                for (boolean b2 : new boolean[]{false, true})
                    registerVillageState(states, Blocks.BREWING_STAND,
                            "minecraft:brewing_stand[has_bottle_0=" + b0 + ",has_bottle_1=" + b1
                                    + ",has_bottle_2=" + b2 + "]", false, false, Capability.NONE);
        for (String facing : List.of("north", "east", "south", "west"))
            for (boolean lit : new boolean[]{false, true})
                for (boolean signal : new boolean[]{false, true})
                    registerVillageState(states, Blocks.CAMPFIRE,
                            "minecraft:campfire[facing=" + facing + ",lit=" + lit
                                    + ",signal_fire=" + signal + ",waterlogged=false]",
                            false, false, Capability.NONE);

        registerVillageWalls(states, Blocks.COBBLE_WALL, "minecraft:cobblestone_wall", List.of(
                "east=low,north=low,south=low,up=true,waterlogged=false,west=low",
                "east=none,north=tall,south=none,up=true,waterlogged=false,west=tall",
                "east=tall,north=none,south=none,up=true,waterlogged=false,west=none",
                "east=tall,north=tall,south=none,up=true,waterlogged=false,west=none"));
        registerVillageWalls(states, Blocks.DIORITE_WALL, "minecraft:diorite_wall", List.of(
                "east=none,north=low,south=low,up=false,waterlogged=false,west=none",
                "east=none,north=low,south=none,up=true,waterlogged=false,west=none",
                "east=none,north=tall,south=none,up=true,waterlogged=false,west=none"));
        registerVillageWalls(states, Blocks.SANDSTONE_WALL, "minecraft:sandstone_wall", List.of(
                "east=none,north=none,south=none,up=true,waterlogged=false,west=none"));

        for (String attachment : List.of("floor", "ceiling"))
            for (String facing : List.of("north", "east", "south", "west"))
                registerVillageState(states, Blocks.BELL,
                        "minecraft:bell[attachment=" + attachment + ",facing=" + facing
                                + ",powered=false]", false, false, Capability.NONE);
        for (String facing : List.of("north", "east", "south", "west")) {
            registerVillageState(states, Blocks.BROWN_WALL_BANNER,
                    "minecraft:brown_wall_banner[facing=" + facing + "]", false, false, Capability.NONE);
            registerVillageState(states, Blocks.LECTERN,
                    "minecraft:lectern[facing=" + facing + ",has_book=false,powered=false]",
                    true, false, Capability.NONE);
            registerVillageState(states, Blocks.SMOKER,
                    "minecraft:smoker[facing=" + facing + ",lit=false]", true, true, Capability.NONE);
            registerVillageState(states, Blocks.STONECUTTER,
                    "minecraft:stonecutter[facing=" + facing + "]", true, false, Capability.NONE);
            registerVillageState(states, Blocks.WHITE_GLAZED_TERRACOTTA,
                    "minecraft:white_glazed_terracotta[facing=" + facing + "]",
                    true, true, Capability.NONE);
        }
        for (boolean hanging : new boolean[]{false, true})
            registerVillageState(states, Blocks.LANTERN,
                    "minecraft:lantern[hanging=" + hanging + ",waterlogged=false]",
                    false, false, Capability.NONE);
        for (CatalogEntry pane : List.of(
                entry(Blocks.WHITE_STAINED_GLASS_PANE, "minecraft:white_stained_glass_pane"),
                entry(Blocks.YELLOW_STAINED_GLASS_PANE, "minecraft:yellow_stained_glass_pane")))
            for (boolean east : new boolean[]{false, true})
                for (boolean north : new boolean[]{false, true})
                    for (boolean south : new boolean[]{false, true})
                        for (boolean west : new boolean[]{false, true})
                            registerVillageState(states, pane.id(), pane.exact()
                                    + "[east=" + east + ",north=" + north + ",south=" + south
                                    + ",waterlogged=false,west=" + west + "]", true, false, Capability.NONE);
        for (int pickles = 1; pickles <= 4; pickles++)
            registerVillageState(states, Blocks.SEA_PICKLE,
                    "minecraft:sea_pickle[pickles=" + pickles + ",waterlogged=false]",
                    false, false, Capability.NONE);
    }

    private static void registerVillageBounded(Map<String, Mc263FeatureBlockState> states,
            int id, String block, String property, int minimum, int maximum) {
        for (int value = minimum; value <= maximum; value++)
            registerVillageState(states, id, block + "[" + property + "=" + value + "]",
                    false, false, Capability.NONE);
    }

    private static void registerVillageWalls(Map<String, Mc263FeatureBlockState> states,
            int id, String block, List<String> topologies) {
        for (String topology : topologies)
            registerVillageState(states, id, block + "[" + topology + "]",
                    true, false, Capability.NONE);
    }

    private static void registerVillageState(Map<String, Mc263FeatureBlockState> states,
            int id, String exact, boolean solid, boolean solidRender, Capability capability) {
        if (states.containsKey(exact)) return;
        Mc263FeatureBlockState prior = defaultOrNullForId(id);
        boolean waterlogged = exact.contains("waterlogged=true");
        if (prior == null) {
            registerDefault(states, id, exact, solid, solidRender,
                    waterlogged ? FluidKind.WATER_SOURCE : FluidKind.NONE,
                    waterlogged ? 8 : 0, capability);
        } else {
            registerAlias(states, id, exact, prior.solid, prior.solidRender,
                    waterlogged ? FluidKind.WATER_SOURCE : FluidKind.NONE,
                    waterlogged ? 8 : 0, prior.capability);
        }
        VILLAGE_G3J15_ADDITIONS.add(exact);
    }

    static boolean isVillageG3J15ExactState(String exact) {
        return VILLAGE_G3J15_ADDITIONS.contains(exact);
    }

    private static String canonicalVillageTrapdoor(String block,
            Map<String, String> properties, String input) {
        String facing = properties.get("facing");
        String half = properties.get("half");
        if (properties.size() != 5
                || !Set.of("north", "east", "south", "west").contains(facing)
                || !Set.of("bottom", "top").contains(half)
                || !Set.of("false", "true").contains(properties.get("open"))
                || !"false".equals(properties.get("powered"))
                || !"false".equals(properties.get("waterlogged"))) {
            throw new IllegalArgumentException("unsupported Village trapdoor state: " + input);
        }
        return block + "[facing=" + facing + ",half=" + half + ",open="
                + properties.get("open") + ",powered=false,waterlogged=false]";
    }

    private static String canonicalVillageFenceGate(String block,
            Map<String, String> properties, String input) {
        String facing = properties.get("facing");
        if (properties.size() != 4
                || !Set.of("north", "east", "south", "west").contains(facing)
                || !Set.of("false", "true").contains(properties.get("in_wall"))
                || !Set.of("false", "true").contains(properties.get("open"))
                || !"false".equals(properties.get("powered"))) {
            throw new IllegalArgumentException("unsupported Village fence-gate state: " + input);
        }
        return block + "[facing=" + facing + ",in_wall=" + properties.get("in_wall")
                + ",open=" + properties.get("open") + ",powered=false]";
    }

    private static String canonicalVillageBell(String block,
            Map<String, String> properties, String input) {
        String attachment = properties.get("attachment");
        String facing = properties.get("facing");
        if (properties.size() != 3
                || !Set.of("floor", "ceiling").contains(attachment)
                || !Set.of("north", "east", "south", "west").contains(facing)
                || !"false".equals(properties.get("powered"))) {
            throw new IllegalArgumentException("unsupported Village bell state: " + input);
        }
        return block + "[attachment=" + attachment + ",facing=" + facing + ",powered=false]";
    }

    private static String canonicalVillageLantern(String block,
            Map<String, String> properties, String input) {
        if (properties.size() != 2
                || !Set.of("false", "true").contains(properties.get("hanging"))
                || !"false".equals(properties.get("waterlogged"))) {
            throw new IllegalArgumentException("unsupported Village lantern state: " + input);
        }
        return block + "[hanging=" + properties.get("hanging") + ",waterlogged=false]";
    }

    private static String canonicalVillageLectern(String block,
            Map<String, String> properties, String input) {
        String facing = properties.get("facing");
        if (properties.size() != 3
                || !Set.of("north", "east", "south", "west").contains(facing)
                || !"false".equals(properties.get("has_book"))
                || !"false".equals(properties.get("powered"))) {
            throw new IllegalArgumentException("unsupported Village lectern state: " + input);
        }
        return block + "[facing=" + facing + ",has_book=false,powered=false]";
    }

    private static String canonicalVillageSmoker(String block,
            Map<String, String> properties, String input) {
        String facing = properties.get("facing");
        if (properties.size() != 2
                || !Set.of("north", "east", "south", "west").contains(facing)
                || !"false".equals(properties.get("lit"))) {
            throw new IllegalArgumentException("unsupported Village smoker state: " + input);
        }
        return block + "[facing=" + facing + ",lit=false]";
    }

    private static String canonicalVillagePane(String block,
            Map<String, String> properties, String input) {
        if (properties.size() != 5 || !"false".equals(properties.get("waterlogged"))) {
            throw new IllegalArgumentException("unsupported Village pane state: " + input);
        }
        for (String side : List.of("east", "north", "south", "west")) {
            if (!Set.of("false", "true").contains(properties.get(side))) {
                throw new IllegalArgumentException("unsupported Village pane state: " + input);
            }
        }
        return block + "[east=" + properties.get("east") + ",north="
                + properties.get("north") + ",south=" + properties.get("south")
                + ",waterlogged=false,west=" + properties.get("west") + "]";
    }

    private static String canonicalTrialRedBed(String block, Map<String, String> properties,
            String input) {
        String facing = properties.get("facing");
        String occupied = properties.get("occupied");
        String part = properties.get("part");
        if (properties.size() != 3
                || !Set.of("north", "east", "south", "west").contains(facing)
                || !"false".equals(occupied)
                || !Set.of("foot", "head").contains(part)) {
            throw new IllegalArgumentException("unsupported Trial red-bed state: " + input);
        }
        return block + "[part=" + part + ",facing=" + facing + ",occupied=false]";
    }

    static boolean isMansionP2PExactState(String exact) {
        return MANSION_P2P_EXACT_STATES.stream().anyMatch(entry -> entry.exact().equals(exact));
    }

    private static void registerMansionP2PStates(Map<String, Mc263FeatureBlockState> states) {
        if (MANSION_P2P_EXACT_STATES.size() != 55
                || MANSION_P2P_EXACT_STATES.stream().map(CatalogEntry::exact).distinct().count() != 55
                || MANSION_P2P_EXACT_STATES.stream()
                        .map(entry -> blockKey(entry.exact())).distinct().count() != 21) {
            throw new ExceptionInInitializerError("invalid Mansion P2P exact-state authority");
        }
        for (CatalogEntry entry : MANSION_P2P_EXACT_STATES) {
            registerMansionP2PState(states, entry);
        }
        for (CatalogEntry entry : MANSION_P2P_EXACT_STATES) {
            if (!states.containsKey(entry.exact()) || defaultOrNullForId(entry.id()) == null) {
                throw new ExceptionInInitializerError(
                        "incomplete Mansion P2P exact-state admission: " + entry.exact());
            }
        }
    }

    private static void registerMansionP2PState(
            Map<String, Mc263FeatureBlockState> states, CatalogEntry entry) {
        Mc263FeatureBlockState prior = defaultOrNullForId(entry.id());
        boolean solid;
        boolean solidRender;
        Capability capability;
        if (prior != null) {
            solid = prior.solid;
            solidRender = prior.solidRender;
            capability = prior.capability;
        } else {
            solid = switch (entry.id()) {
                case Blocks.BLACK_CARPET, Blocks.BROWN_CARPET, Blocks.CYAN_CARPET,
                        Blocks.GRAY_CARPET, Blocks.POTTED_RED_TULIP -> false;
                default -> true;
            };
            solidRender = switch (entry.id()) {
                case Blocks.BIRCH_PLANK, Blocks.BLACK_WOOL, Blocks.GRAY_WOOL,
                        Blocks.GREEN_WOOL, Blocks.LIGHT_BLUE_WOOL, Blocks.LIGHT_GRAY_WOOL,
                        Blocks.WHITE_WOOL -> true;
                default -> false;
            };
            capability = Capability.NONE;
        }
        String defaultExact = mansionP2PDefaultExact(entry.id());
        if (prior == null && entry.exact().equals(defaultExact)) {
            registerDefault(states, entry.id(), entry.exact(), solid, solidRender,
                    FluidKind.NONE, 0, capability);
        } else {
            registerAlias(states, entry.id(), entry.exact(), solid, solidRender,
                    FluidKind.NONE, 0, capability);
        }
    }

    private static String mansionP2PDefaultExact(int id) {
        if (id == Blocks.DARK_OAK_STAIRS) {
            return "minecraft:dark_oak_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]";
        }
        return MANSION_P2P_EXACT_STATES.stream()
                .filter(entry -> entry.id() == id)
                .map(CatalogEntry::exact)
                .findFirst()
                .orElseThrow(() -> new ExceptionInInitializerError(
                        "missing Mansion P2P default for block " + id));
    }

    private static void registerWhiteWallBannerStates(
            Map<String, Mc263FeatureBlockState> states) {
        registerReleasedStates(states, Blocks.WHITE_WALL_BANNER,
                "minecraft:white_wall_banner[facing=north]",
                List.of(
                        "minecraft:white_wall_banner[facing=west]",
                        "minecraft:white_wall_banner[facing=east]",
                        "minecraft:white_wall_banner[facing=south]"),
                false, false, Capability.NONE);
    }

    /** Exact template vocabulary plus lawful Camp rotation/liquid-restoration successors. */
    private static void registerAbandonedCampStates(
            Map<String, Mc263FeatureBlockState> states) {
        registerCampFences(states, Blocks.ACACIA_FENCE, "minecraft:acacia_fence",
                List.of("", "west", "south", "north"));
        registerCampFences(states, Blocks.BAMBOO_FENCE, "minecraft:bamboo_fence",
                List.of("", "west", "south", "north", "east"));
        registerCampFences(states, Blocks.BIRCH_FENCE, "minecraft:birch_fence",
                List.of("", "south", "north", "east"));
        registerCampFences(states, Blocks.CHERRY_FENCE, "minecraft:cherry_fence",
                List.of("", "west", "south", "north", "east"));
        registerCampFences(states, Blocks.JUNGLE_FENCE, "minecraft:jungle_fence",
                List.of("", "west", "south", "north"));
        registerCampFences(states, Blocks.PALE_OAK_FENCE, "minecraft:pale_oak_fence",
                List.of("", "west", "south", "north"));
        registerCampFences(states, Blocks.POPLAR_FENCE, "minecraft:poplar_fence",
                List.of("", "west", "south", "north"));
        registerCampRotatedWaterloggedSpruceFenceStates(states);
        registerCampState(states, Blocks.BIRCH_FENCE,
                "minecraft:birch_fence[east=false,north=false,south=false,waterlogged=false,west=true]",
                true, false, Capability.NONE);
        registerCampState(states, Blocks.BIRCH_FENCE,
                "minecraft:birch_fence[east=false,north=false,south=false,waterlogged=true,west=true]",
                true, false, Capability.NONE);
        registerCampState(states, Blocks.POPLAR_FENCE,
                "minecraft:poplar_fence[east=true,north=false,south=false,waterlogged=false,west=false]",
                true, false, Capability.NONE);
        registerCampState(states, Blocks.POPLAR_FENCE,
                "minecraft:poplar_fence[east=true,north=false,south=false,waterlogged=true,west=false]",
                true, false, Capability.NONE);
        registerCampState(states, Blocks.PALE_OAK_FENCE,
                "minecraft:pale_oak_fence[east=true,north=false,south=false,waterlogged=false,west=false]",
                true, false, Capability.NONE);
        registerCampState(states, Blocks.PALE_OAK_FENCE,
                "minecraft:pale_oak_fence[east=true,north=false,south=false,waterlogged=true,west=false]",
                true, false, Capability.NONE);

        for (String facing : List.of("north", "east", "south", "west", "up")) {
            registerCampState(states, Blocks.BARREL,
                    "minecraft:barrel[facing=" + facing + ",open=false]",
                    true, true, Capability.RANDOMIZABLE_CONTAINER);
        }
        registerCampState(states, Blocks.BIRCH_STAIRS,
                "minecraft:birch_stairs[facing=west,half=bottom,shape=straight,waterlogged=false]",
                true, false, Capability.NONE);
        for (String facing : List.of("south", "west")) {
            registerCampState(states, Blocks.WOOD_STAIRS,
                    "minecraft:oak_stairs[facing=" + facing
                            + ",half=bottom,shape=straight,waterlogged=false]",
                    true, false, Capability.NONE);
        }
        for (String facing : List.of("north", "east", "south", "west")) {
            for (boolean waterlogged : new boolean[]{false, true}) {
                registerCampState(states, Blocks.WHITE_WOOL_STAIRS,
                        "minecraft:white_wool_stairs[facing=" + facing
                                + ",half=bottom,shape=straight,waterlogged=" + waterlogged + "]",
                        true, false, Capability.NONE);
            }
        }

        for (String exact : List.of(
                "minecraft:acacia_leaves[distance=1,persistent=true,waterlogged=false]",
                "minecraft:acacia_leaves[distance=2,persistent=true,waterlogged=false]")) {
            registerCampState(states, Blocks.ACACIA_LEAVES, exact,
                    true, false, Capability.NONE);
        }
        registerCampState(states, Blocks.CHERRY_LEAVES,
                "minecraft:cherry_leaves[distance=1,persistent=true,waterlogged=false]",
                true, false, Capability.NONE);
        registerCampState(states, Blocks.CREAKING_HEART,
                "minecraft:creaking_heart[axis=y,creaking_heart_state=dormant,natural=false]",
                true, true, Capability.NONE);

        for (String facing : List.of("east", "north")) {
            registerCampState(states, Blocks.CAMPFIRE,
                    "minecraft:campfire[facing=" + facing
                            + ",lit=false,signal_fire=true,waterlogged=false]",
                    false, false, Capability.NONE);
        }
        registerCampState(states, Blocks.CARROT_CROP, "minecraft:carrots[age=7]",
                false, false, Capability.NONE);
        registerCampState(states, Blocks.POTATO_CROP, "minecraft:potatoes[age=7]",
                false, false, Capability.NONE);
        registerCampState(states, Blocks.WHEAT_CROP, "minecraft:wheat[age=7]",
                false, false, Capability.NONE);
        registerCampState(states, Blocks.CHERRY_SAPLING, "minecraft:cherry_sapling[stage=1]",
                false, false, Capability.NONE);
        registerCampState(states, Blocks.JUNGLE_SAPLING, "minecraft:jungle_sapling[stage=1]",
                false, false, Capability.NONE);

        for (String facing : List.of("north", "west")) {
            for (String type : List.of("left", "right")) {
                registerCampState(states, Blocks.CHEST,
                        "minecraft:chest[facing=" + facing + ",type=" + type
                                + ",waterlogged=false]",
                        true, false, Capability.RANDOMIZABLE_CONTAINER);
            }
        }
        registerCampWalls(states, Blocks.COBBLE_WALL, "minecraft:cobblestone_wall",
                List.of(
                        "east=low,north=low,south=none,up=true,waterlogged=false,west=none",
                        "east=low,north=none,south=low,up=true,waterlogged=false,west=none",
                        "east=low,north=none,south=none,up=false,waterlogged=false,west=low",
                        "east=low,north=none,south=none,up=true,waterlogged=false,west=none",
                        "east=none,north=low,south=low,up=false,waterlogged=false,west=none",
                        "east=none,north=low,south=none,up=true,waterlogged=false,west=low",
                        "east=none,north=none,south=low,up=true,waterlogged=false,west=low",
                        "east=none,north=none,south=low,up=true,waterlogged=false,west=none",
                        "east=none,north=none,south=none,up=true,waterlogged=false,west=low",
                        "east=none,north=low,south=none,up=true,waterlogged=false,west=none"));
        registerCampWalls(states, Blocks.MOSSY_COBBLE_WALL,
                "minecraft:mossy_cobblestone_wall", List.of(
                        "east=low,north=none,south=low,up=true,waterlogged=false,west=none",
                        "east=low,north=none,south=none,up=false,waterlogged=false,west=low",
                        "east=low,north=none,south=none,up=true,waterlogged=false,west=none",
                        "east=none,north=low,south=low,up=false,waterlogged=false,west=none",
                        "east=none,north=low,south=none,up=true,waterlogged=false,west=none",
                        "east=none,north=none,south=low,up=true,waterlogged=false,west=none",
                        "east=none,north=none,south=none,up=true,waterlogged=false,west=low"));
        registerCampState(states, Blocks.MOSSY_COBBLE_WALL,
                "minecraft:mossy_cobblestone_wall[east=none,north=none,south=low,up=true,waterlogged=false,west=low]",
                true, false, Capability.NONE);
        registerCampState(states, Blocks.MOSSY_COBBLE_WALL,
                "minecraft:mossy_cobblestone_wall[east=none,north=none,south=low,up=true,waterlogged=true,west=low]",
                true, false, Capability.NONE);

        registerCampState(states, Blocks.DIRT_PATH, "minecraft:dirt_path",
                true, true, Capability.NONE);
        registerCampState(states, Blocks.HAY_BLOCK, "minecraft:hay_block[axis=y]",
                true, true, Capability.NONE);
        registerCampState(states, Blocks.RESIN_BLOCK, "minecraft:resin_block",
                true, true, Capability.NONE);
        for (int layers = 2; layers <= 8; layers++) {
            registerCampState(states, Blocks.SNOW, "minecraft:snow[layers=" + layers + "]",
                    layers == 8, false, Capability.NONE);
        }

        for (String facing : List.of("north", "east", "south", "west")) {
            for (boolean waterlogged : new boolean[]{false, true}) {
                registerCampState(states, Blocks.OXIDIZED_COPPER_CHEST,
                        "minecraft:oxidized_copper_chest[facing=" + facing
                                + ",type=single,waterlogged=" + waterlogged + "]",
                        true, false, Capability.RANDOMIZABLE_CONTAINER);
            }
        }
        for (String exact : List.of(
                "minecraft:oxidized_copper_golem_statue[copper_golem_pose=running,facing=north,waterlogged=false]",
                "minecraft:oxidized_copper_golem_statue[copper_golem_pose=sitting,facing=east,waterlogged=false]",
                "minecraft:oxidized_copper_golem_statue[copper_golem_pose=sitting,facing=north,waterlogged=false]",
                "minecraft:oxidized_copper_golem_statue[copper_golem_pose=sitting,facing=west,waterlogged=false]",
                "minecraft:oxidized_copper_golem_statue[copper_golem_pose=standing,facing=south,waterlogged=false]",
                "minecraft:oxidized_copper_golem_statue[copper_golem_pose=star,facing=west,waterlogged=false]")) {
            registerCampState(states, Blocks.OXIDIZED_COPPER_GOLEM_STATUE, exact,
                    false, false, Capability.NONE);
        }
        for (boolean hanging : new boolean[]{false, true}) {
            registerCampState(states, Blocks.OXIDIZED_COPPER_LANTERN,
                    "minecraft:oxidized_copper_lantern[hanging=" + hanging
                            + ",waterlogged=false]",
                    false, false, Capability.NONE);
        }
        for (String facing : List.of("north", "east", "south", "west")) {
            for (String part : List.of("foot", "head")) {
                registerCampState(states, Blocks.STRAW_BED,
                        "minecraft:straw_bed[facing=" + facing
                                + ",occupied=false,part=" + part + "]",
                        false, false, Capability.NONE);
            }
        }
        registerCampState(states, Blocks.WATER_CAULDRON,
                "minecraft:water_cauldron[level=3]", true, false, Capability.NONE);
        registerCampState(states, Blocks.JUNGLE_FENCE,
                "minecraft:jungle_fence[east=true,north=false,south=false,waterlogged=false,west=false]",
                true, false, Capability.NONE);
    }

    private static void registerCampRotatedWaterloggedSpruceFenceStates(
            Map<String, Mc263FeatureBlockState> states) {
        for (String side : List.of("", "east", "north", "south", "west")) {
            for (boolean waterlogged : new boolean[]{false, true}) {
                registerCampState(states, Blocks.SPRUCE_FENCE,
                        "minecraft:spruce_fence[east=" + side.equals("east")
                                + ",north=" + side.equals("north")
                                + ",south=" + side.equals("south")
                                + ",waterlogged=" + waterlogged
                                + ",west=" + side.equals("west") + "]",
                        true, false, Capability.NONE);
            }
        }
    }

    private static void registerCampFences(Map<String, Mc263FeatureBlockState> states,
            int id, String block, List<String> connectedSides) {
        for (String side : connectedSides) {
            registerCampState(states, id, block + "[east=" + side.equals("east")
                            + ",north=" + side.equals("north")
                            + ",south=" + side.equals("south")
                            + ",waterlogged=false,west=" + side.equals("west") + "]",
                    true, false, Capability.NONE);
        }
    }

    private static void registerCampWalls(Map<String, Mc263FeatureBlockState> states,
            int id, String block, List<String> topologies) {
        for (String topology : topologies) {
            registerCampState(states, id, block + "[" + topology + "]",
                    true, false, Capability.NONE);
        }
    }

    private static void registerCampState(Map<String, Mc263FeatureBlockState> states,
            int id, String exact, boolean solid, boolean solidRender, Capability capability) {
        boolean waterlogged = exact.contains("waterlogged=true");
        if (defaultOrNullForId(id) == null) {
            registerDefault(states, id, exact, solid, solidRender,
                    waterlogged ? FluidKind.WATER_SOURCE : FluidKind.NONE,
                    waterlogged ? 8 : 0, capability);
        } else {
            registerAlias(states, id, exact, solid, solidRender,
                    waterlogged ? FluidKind.WATER_SOURCE : FluidKind.NONE,
                    waterlogged ? 8 : 0, capability);
        }
    }

    private static String canonicalTrailSlab(String block,
            Map<String, String> properties, String input) {
        if (properties.size() != 2
                || !Set.of("bottom", "top", "double").contains(properties.get("type"))
                || !Set.of("false", "true").contains(properties.get("waterlogged"))) {
            throw new IllegalArgumentException("unsupported Trail slab state: " + input);
        }
        return block + "[type=" + properties.get("type") + ",waterlogged="
                + properties.get("waterlogged") + "]";
    }

    /** Exact placeable state closure used by the 13 accepted Ruined Portal grammars. */
    private static void registerRuinedPortalStates(
            Map<String, Mc263FeatureBlockState> states) {
        registerDefault(states, Blocks.CRYING_OBSIDIAN, "minecraft:crying_obsidian", true, true,
                FluidKind.NONE, 0, Capability.NONE);
        registerAlias(states, Blocks.LAVA_SOURCE, "minecraft:lava[level=8]", false, false,
                FluidKind.LAVA_FLOWING, 8, Capability.NONE);
        registerDefault(states, Blocks.STONE_SLAB,
                "minecraft:stone_slab[type=bottom,waterlogged=false]", true, false,
                FluidKind.NONE, 0, Capability.NONE);
        registerAlias(states, Blocks.STONE_SLAB,
                "minecraft:stone_slab[type=double,waterlogged=false]", true, true,
                FluidKind.NONE, 0, Capability.NONE);
        registerAlias(states, Blocks.STONE_SLAB,
                "minecraft:stone_slab[type=bottom,waterlogged=true]", true, false,
                FluidKind.WATER_SOURCE, 8, Capability.NONE);
        registerAlias(states, Blocks.STONE_BRICK_SLAB,
                "minecraft:stone_brick_slab[type=top,waterlogged=false]", true, false,
                FluidKind.NONE, 0, Capability.NONE);
        registerAlias(states, Blocks.STONE_BRICK_SLAB,
                "minecraft:stone_brick_slab[type=top,waterlogged=true]", true, false,
                FluidKind.WATER_SOURCE, 8, Capability.NONE);
        registerAlias(states, Blocks.SMOOTH_STONE_SLAB,
                "minecraft:smooth_stone_slab[type=top,waterlogged=false]", true, false,
                FluidKind.NONE, 0, Capability.NONE);
        for (String type : List.of("bottom", "top", "double")) {
            String exact = "minecraft:mossy_stone_brick_slab[type=" + type
                    + ",waterlogged=false]";
            if (type.equals("bottom")) {
                registerDefault(states, Blocks.MOSSY_STONE_BRICK_SLAB, exact, true,
                        type.equals("double"), FluidKind.NONE, 0, Capability.NONE);
            } else {
                registerAlias(states, Blocks.MOSSY_STONE_BRICK_SLAB, exact, true,
                        type.equals("double"), FluidKind.NONE, 0, Capability.NONE);
            }
        }

        registerAlias(states, Blocks.MOSSY_STONE_BRICK_SLAB,
                "minecraft:mossy_stone_brick_slab[type=bottom,waterlogged=true]", true, false,
                FluidKind.WATER_SOURCE, 8, Capability.NONE);

        for (String facing : List.of("north", "east", "south", "west")) {
            for (String half : List.of("bottom", "top")) {
                String exact = "minecraft:mossy_stone_brick_stairs[facing=" + facing
                        + ",half=" + half + ",shape=straight,waterlogged=false]";
                if (facing.equals("north") && half.equals("bottom")) {
                    registerDefault(states, Blocks.MOSSY_STONE_BRICK_STAIRS, exact, true, false,
                            FluidKind.NONE, 0, Capability.NONE);
                } else {
                    registerAlias(states, Blocks.MOSSY_STONE_BRICK_STAIRS, exact, true, false,
                            FluidKind.NONE, 0, Capability.NONE);
                }
            }
        }

        registerAlias(states, Blocks.MOSSY_STONE_BRICK_STAIRS,
                "minecraft:mossy_stone_brick_stairs[facing=north,half=bottom,shape=straight,waterlogged=true]",
                true, false, FluidKind.WATER_SOURCE, 8, Capability.NONE);
        registerAlias(states, Blocks.MOSSY_STONE_BRICK_STAIRS,
                "minecraft:mossy_stone_brick_stairs[facing=east,half=bottom,shape=straight,waterlogged=true]",
                true, false, FluidKind.WATER_SOURCE, 8, Capability.NONE);

        registerAlias(states, Blocks.STONE_BRICK_STAIRS,
                "minecraft:stone_brick_stairs[facing=east,half=bottom,shape=outer_right,waterlogged=true]",
                true, false, FluidKind.WATER_SOURCE, 8, Capability.NONE);
        registerAlias(states, Blocks.STONE_BRICK_STAIRS,
                "minecraft:stone_brick_stairs[facing=west,half=bottom,shape=outer_left,waterlogged=true]",
                true, false, FluidKind.WATER_SOURCE, 8, Capability.NONE);
        registerAlias(states, Blocks.STONE_BRICK_STAIRS,
                "minecraft:stone_brick_stairs[facing=east,half=bottom,shape=outer_left,waterlogged=true]",
                true, false, FluidKind.WATER_SOURCE, 8, Capability.NONE);
        registerAlias(states, Blocks.STONE_BRICK_STAIRS,
                "minecraft:stone_brick_stairs[facing=west,half=bottom,shape=outer_right,waterlogged=true]",
                true, false, FluidKind.WATER_SOURCE, 8, Capability.NONE);

        for (String state : List.of(
                "facing=south,half=bottom,shape=outer_left,waterlogged=false",
                "facing=north,half=bottom,shape=outer_right,waterlogged=false",
                "facing=west,half=bottom,shape=outer_right,waterlogged=false",
                "facing=west,half=bottom,shape=outer_left,waterlogged=false",
                "facing=west,half=bottom,shape=inner_left,waterlogged=false",
                "facing=south,half=bottom,shape=outer_right,waterlogged=false",
                "facing=north,half=bottom,shape=outer_left,waterlogged=false",
                "facing=east,half=top,shape=straight,waterlogged=false")) {
            String exact = "minecraft:stone_brick_stairs[" + state + "]";
            if (!states.containsKey(exact)) {
                registerAlias(states, Blocks.STONE_BRICK_STAIRS, exact, true, false,
                        FluidKind.NONE, 0, Capability.NONE);
            }
        }

        List<String> walls = List.of(
                "east=none,north=none,south=none,up=true,waterlogged=false,west=none",
                "east=none,north=none,south=low,up=true,waterlogged=false,west=none",
                "east=none,north=tall,south=none,up=true,waterlogged=false,west=none");
        for (int index = 0; index < walls.size(); index++) {
            String exact = "minecraft:stone_brick_wall[" + walls.get(index) + "]";
            if (index == 0) {
                registerDefault(states, Blocks.STONE_BRICK_WALL, exact, true, false,
                        FluidKind.NONE, 0, Capability.NONE);
            } else {
                registerAlias(states, Blocks.STONE_BRICK_WALL, exact, true, false,
                        FluidKind.NONE, 0, Capability.NONE);
            }
            String mossy = "minecraft:mossy_stone_brick_wall[" + walls.get(index) + "]";
            if (index == 0) {
                registerDefault(states, Blocks.MOSSY_STONE_BRICK_WALL, mossy, true, false,
                        FluidKind.NONE, 0, Capability.NONE);
            } else {
                registerAlias(states, Blocks.MOSSY_STONE_BRICK_WALL, mossy, true, false,
                        FluidKind.NONE, 0, Capability.NONE);
            }
        }
        registerAlias(states, Blocks.STONE_BRICK_WALL,
                "minecraft:stone_brick_wall[east=tall,north=none,south=none,up=true,waterlogged=false,west=none]",
                true, false, FluidKind.NONE, 0, Capability.NONE);
        registerAlias(states, Blocks.MOSSY_STONE_BRICK_WALL,
                "minecraft:mossy_stone_brick_wall[east=none,north=none,south=none,up=true,waterlogged=false,west=low]",
                true, false, FluidKind.NONE, 0, Capability.NONE);
        registerAlias(states, Blocks.MOSSY_STONE_BRICK_WALL,
                "minecraft:mossy_stone_brick_wall[east=tall,north=none,south=none,up=true,waterlogged=false,west=none]",
                true, false, FluidKind.NONE, 0, Capability.NONE);
        registerAlias(states, Blocks.MOSSY_STONE_BRICK_WALL,
                "minecraft:mossy_stone_brick_wall[east=none,north=none,south=none,up=true,waterlogged=true,west=none]",
                true, false, FluidKind.WATER_SOURCE, 8, Capability.NONE);
        registerAlias(states, Blocks.MOSSY_STONE_BRICK_WALL,
                "minecraft:mossy_stone_brick_wall[east=none,north=none,south=low,up=true,waterlogged=true,west=none]",
                true, false, FluidKind.WATER_SOURCE, 8, Capability.NONE);
        registerAlias(states, Blocks.MOSSY_STONE_BRICK_WALL,
                "minecraft:mossy_stone_brick_wall[east=none,north=tall,south=none,up=true,waterlogged=true,west=none]",
                true, false, FluidKind.WATER_SOURCE, 8, Capability.NONE);
    }

    private static String canonicalTrailWall(String block,
            Map<String, String> properties, String input) {
        if (properties.size() != 6
                || !Set.of("false", "true").contains(properties.get("up"))
                || !Set.of("false", "true").contains(properties.get("waterlogged"))) {
            throw new IllegalArgumentException("unsupported Trail wall state: " + input);
        }
        for (String side : List.of("east", "north", "south", "west")) {
            if (!Set.of("none", "low", "tall").contains(properties.get(side))) {
                throw new IllegalArgumentException("unsupported Trail wall state: " + input);
            }
        }
        return block + "[east=" + properties.get("east") + ",north="
                + properties.get("north") + ",south=" + properties.get("south")
                + ",up=" + properties.get("up") + ",waterlogged="
                + properties.get("waterlogged") + ",west=" + properties.get("west") + "]";
    }

    private static String canonicalTrailIronBars(String block,
            Map<String, String> properties, String input) {
        if (properties.size() != 5
                || !Set.of("false", "true").contains(properties.get("waterlogged"))) {
            throw new IllegalArgumentException("unsupported Trail iron-bars state: " + input);
        }
        for (String side : List.of("east", "north", "south", "west")) {
            if (!Set.of("false", "true").contains(properties.get(side))) {
                throw new IllegalArgumentException(
                        "unsupported Trail iron-bars state: " + input);
            }
        }
        return block + "[east=" + properties.get("east") + ",north="
                + properties.get("north") + ",south=" + properties.get("south")
                + ",waterlogged=" + properties.get("waterlogged") + ",west="
                + properties.get("west") + "]";
    }

    private static String canonicalTrailLadder(String block,
            Map<String, String> properties, String input) {
        String facing = properties.get("facing");
        if (properties.size() != 2
                || !Set.of("north", "east", "south", "west").contains(facing)
                || !Set.of("false", "true").contains(properties.get("waterlogged"))) {
            throw new IllegalArgumentException("unsupported Trail ladder state: " + input);
        }
        return block + "[facing=" + facing + ",waterlogged="
                + properties.get("waterlogged") + "]";
    }

    private static String canonicalTrailFurnace(String block,
            Map<String, String> properties, String input) {
        String facing = properties.get("facing");
        if (properties.size() != 2
                || !Set.of("north", "east", "south", "west").contains(facing)
                || !"false".equals(properties.get("lit"))) {
            throw new IllegalArgumentException("unsupported Trail furnace state: " + input);
        }
        return block + "[facing=" + facing + ",lit=false]";
    }

    private static String canonicalTrailCampfire(String block,
            Map<String, String> properties, String input) {
        String facing = properties.get("facing");
        if (properties.size() != 4
                || !Set.of("north", "east", "south", "west").contains(facing)
                || !Set.of("false", "true").contains(properties.get("lit"))
                || !Set.of("false", "true").contains(properties.get("signal_fire"))
                || !Set.of("false", "true").contains(properties.get("waterlogged"))) {
            throw new IllegalArgumentException("unsupported Trail campfire state: " + input);
        }
        return block + "[facing=" + facing + ",lit=" + properties.get("lit")
                + ",signal_fire=" + properties.get("signal_fire") + ",waterlogged="
                + properties.get("waterlogged") + "]";
    }

    private static String canonicalCampBarrel(String block,
            Map<String, String> properties, String input) {
        String facing = properties.get("facing");
        if (properties.size() != 2
                || !Set.of("down", "up", "north", "east", "south", "west").contains(facing)
                || !"false".equals(properties.get("open"))) {
            throw new IllegalArgumentException("unsupported Abandoned Camp barrel state: " + input);
        }
        return block + "[facing=" + facing + ",open=false]";
    }

    private static String canonicalCampBed(String block,
            Map<String, String> properties, String input) {
        String facing = properties.get("facing");
        String part = properties.get("part");
        if (properties.size() != 3
                || !Set.of("north", "east", "south", "west").contains(facing)
                || !Set.of("foot", "head").contains(part)
                || !"false".equals(properties.get("occupied"))) {
            throw new IllegalArgumentException("unsupported Abandoned Camp bed state: " + input);
        }
        return block + "[facing=" + facing + ",occupied=false,part=" + part + "]";
    }

    private static String canonicalCampCopperStatue(String block,
            Map<String, String> properties, String input) {
        String pose = properties.get("copper_golem_pose");
        String facing = properties.get("facing");
        if (properties.size() != 3
                || !Set.of("standing", "sitting", "running", "star").contains(pose)
                || !Set.of("north", "east", "south", "west").contains(facing)
                || !"false".equals(properties.get("waterlogged"))) {
            throw new IllegalArgumentException(
                    "unsupported Abandoned Camp copper statue state: " + input);
        }
        return block + "[copper_golem_pose=" + pose + ",facing=" + facing
                + ",waterlogged=false]";
    }

    private static String canonicalCampCopperLantern(String block,
            Map<String, String> properties, String input) {
        String hanging = properties.get("hanging");
        if (properties.size() != 2
                || !Set.of("false", "true").contains(hanging)
                || !"false".equals(properties.get("waterlogged"))) {
            throw new IllegalArgumentException(
                    "unsupported Abandoned Camp copper lantern state: " + input);
        }
        return block + "[hanging=" + hanging + ",waterlogged=false]";
    }

    private static String canonicalTrailGrindstone(String block,
            Map<String, String> properties, String input) {
        String facing = properties.get("facing");
        if (properties.size() != 2 || !"floor".equals(properties.get("face"))
                || !Set.of("north", "east", "south", "west").contains(facing)) {
            throw new IllegalArgumentException("unsupported Trail grindstone state: " + input);
        }
        return block + "[face=floor,facing=" + facing + "]";
    }

    private static void registerReleasedStates(Map<String, Mc263FeatureBlockState> states,
            int id, String defaultExact, List<String> aliases, boolean solid,
            boolean solidRender, Capability capability) {
        registerDefault(states, id, defaultExact, solid, solidRender,
                FluidKind.NONE, 0, capability);
        for (String exact : aliases) {
            registerAlias(states, id, exact, solid, solidRender,
                    FluidKind.NONE, 0, capability);
        }
    }

    /**
     * Append-only exact vanilla sapling states. Pinned 26.3 saplings always carry {@code stage};
     * the earlier property-less spellings stay registered so no carrier loses its code, and the
     * exact states resolve through the same append-only block IDs. This batch runs after the
     * authenticated production closure so a closure-owned sapling state keeps its STATE-CLOSE
     * code, and every state added here is coded as a strict suffix after that batch.
     */
    private static void registerSaplingStageStates(Map<String, Mc263FeatureBlockState> states) {
        for (CatalogEntry sapling : List.of(
                entry(Blocks.OAK_SAPLING, "minecraft:oak_sapling"),
                entry(Blocks.BIRCH_SAPLING, "minecraft:birch_sapling"),
                entry(Blocks.SPRUCE_SAPLING, "minecraft:spruce_sapling"),
                entry(Blocks.JUNGLE_SAPLING, "minecraft:jungle_sapling"),
                entry(Blocks.ACACIA_SAPLING, "minecraft:acacia_sapling"),
                entry(Blocks.DARK_OAK_SAPLING, "minecraft:dark_oak_sapling"),
                entry(Blocks.CHERRY_SAPLING, "minecraft:cherry_sapling"),
                entry(Blocks.PALE_OAK_SAPLING, "minecraft:pale_oak_sapling"),
                entry(Blocks.POPLAR_SAPLING, "minecraft:poplar_sapling"))) {
            for (int stage = 0; stage <= 1; stage++) {
                String exact = sapling.exact() + "[stage=" + stage + "]";
                if (states.containsKey(exact)) continue;
                if (defaultOrNullForId(sapling.id()) == null) {
                    registerDefault(states, sapling.id(), exact, false, false,
                            FluidKind.NONE, 0, Capability.NONE);
                } else {
                    registerAlias(states, sapling.id(), exact, false, false,
                            FluidKind.NONE, 0, Capability.NONE);
                }
                SAPLING_STAGE_ADDITIONS.add(exact);
            }
        }
    }

    /**
     * Append-only exact states the authenticated Village pile providers may write.
     *
     * <p>The state set is the exhaustive provider-receipt output of the five pile configured
     * features in the pinned {@code village-production-authority-v1.txt} ({@code V|…} rows), i.e.
     * exactly {@code Mc263VillagePileFeatureAuthority.authenticatedOutputExactStates()}. The rows
     * are transcribed here rather than read at class-initialisation time so the frozen catalog
     * stays free of resource/authority load order, and
     * {@code Mc263VillagePileFeatureAuthorityTest} binds the transcription back to the authority
     * so it can never drift from the receipt. Only the protocol-ID binding is explicit, because a
     * block ID is an append-only contract (AGENTS 38c) and must never be inferred; every ID below
     * is an already-released {@link Blocks} code, so this batch claims no new ID and no released
     * state changes its per-block code.</p>
     *
     * <p>This batch runs last, so a state a previous batch already registered keeps its code and
     * only genuinely missing states are appended.</p>
     */
    private static void registerAuthenticatedVillagePileStates(
            Map<String, Mc263FeatureBlockState> states) {
        Set<String> sortedIdentities = new HashSet<>();
        for (String released : states.keySet()) {
            sortedIdentities.add(propertySortedIdentity(released));
        }
        for (String exact : VILLAGE_PILE_PROVIDER_STATES) {
            if (states.containsKey(exact)) continue;
            if (!sortedIdentities.add(propertySortedIdentity(exact))) continue;
            String key = blockKey(exact);
            Integer blockId = VILLAGE_PILE_BLOCK_IDS.get(key);
            if (blockId == null) {
                throw new ExceptionInInitializerError(
                        "no released protocol ID for authenticated Village pile state: " + exact);
            }
            Mc263FeatureBlockState prototype = states.values().stream()
                    .filter(value -> value.blockId == blockId).findFirst().orElse(null);
            boolean solid = prototype == null || prototype.solid;
            boolean solidRender = prototype == null || prototype.solidRender;
            if (defaultOrNullForId(blockId) == null) {
                registerDefault(states, blockId, exact, solid, solidRender,
                        FluidKind.NONE, 0, Capability.NONE);
            } else {
                registerAlias(states, blockId, exact, solid, solidRender,
                        FluidKind.NONE, 0, Capability.NONE);
            }
            VILLAGE_PILE_ADDITIONS.add(exact);
        }
    }

    /** True only for an exact state appended from the authenticated Village pile providers. */
    public static boolean isVillagePileProviderExactState(String exactState) {
        return VILLAGE_PILE_ADDITIONS.contains(exactState);
    }

    private static void registerDefault(Map<String, Mc263FeatureBlockState> states, int id,
            String exact, boolean solid, boolean solidRender, FluidKind fluidKind,
            int fluidAmount, Capability capability) {
        Mc263FeatureBlockState value = state(id, exact, solid, solidRender,
                fluidKind, fluidAmount, capability);
        registerDefaultOnly(id, value);
        if (states.putIfAbsent(exact, value) != null) {
            throw new ExceptionInInitializerError("duplicate exact FEATURES state: " + exact);
        }
    }

    private static void registerAlias(Map<String, Mc263FeatureBlockState> states, int id,
            String exact, boolean solid, boolean solidRender, FluidKind fluidKind,
            int fluidAmount, Capability capability) {
        Mc263FeatureBlockState value = state(id, exact, solid, solidRender,
                fluidKind, fluidAmount, capability);
        if (states.putIfAbsent(exact, value) != null) {
            throw new ExceptionInInitializerError("duplicate exact FEATURES state: " + exact);
        }
    }

    private static void registerDefaultOnly(int id, Mc263FeatureBlockState state) {
        if (id < 0 || id >= DEFAULT_BY_ID.length || DEFAULT_BY_ID[id] != null) {
            throw new ExceptionInInitializerError("duplicate/invalid FEATURES block ID: " + id);
        }
        DEFAULT_BY_ID[id] = state;
    }

    private static Mc263FeatureBlockState state(int id, String exact, boolean solid,
            boolean solidRender, FluidKind fluidKind, int fluidAmount, Capability capability) {
        if (id < 0 || id > 0xffff || !isAsciiResourceKey(blockKey(exact))
                || fluidAmount < 0 || fluidAmount > 8) {
            throw new ExceptionInInitializerError("invalid FEATURES catalog state: " + exact);
        }
        return new Mc263FeatureBlockState(id, exact, solid, solidRender,
                Objects.requireNonNull(fluidKind), fluidAmount,
                Objects.requireNonNull(capability));
    }

    private static CatalogEntry entry(int id, String exact) {
        return new CatalogEntry(id, exact);
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof Mc263FeatureBlockState state
                && blockId == state.blockId && exactState.equals(state.exactState);
    }

    @Override
    public int hashCode() {
        return 31 * blockId + exactState.hashCode();
    }

    @Override
    public String toString() {
        return "Mc263FeatureBlockState[blockId=" + blockId + ", exactState=" + exactState
                + ", fluidKind=" + fluidKind + ", capability=" + capability + "]";
    }

    private record CatalogEntry(int id, String exact) {
    }

    public enum FluidKind {
        NONE,
        WATER_SOURCE,
        WATER_FLOWING,
        LAVA_SOURCE,
        LAVA_FLOWING
    }

    public enum Capability {
        NONE,
        RANDOMIZABLE_CONTAINER,
        SPAWNER,
        POTENT_SULFUR,
        BRUSHABLE,
        BEEHIVE,
        SCULK_CATALYST,
        SCULK_SENSOR,
        SCULK_SHRIEKER
    }
}
