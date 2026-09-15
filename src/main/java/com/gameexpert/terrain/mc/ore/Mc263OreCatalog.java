package com.gameexpert.terrain.mc.ore;

import com.gameexpert.terrain.mc.McRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Typed projection of the ore and fossil feature data shipped by Minecraft Java
 * {@code 26.3-snapshot-7}. This class contains data and distribution mechanics only; callers own
 * the pinned decoration seed stream and the actual block replacement algorithm.
 */
public final class Mc263OreCatalog {
    public static final int UNDERGROUND_STRUCTURES_STEP = 3;
    public static final int UNDERGROUND_ORES_STEP = 6;
    public static final int UNDERGROUND_DECORATION_STEP = 7;

    public enum TargetKind {
        BASE_STONE_OVERWORLD,
        HEIGHT_SPLIT_STONE_AND_DEEPSLATE
    }

    public enum FrequencyKind {
        COUNT,
        COUNT_UNIFORM,
        RARITY_FILTER
    }

    public enum HeightKind {
        UNIFORM,
        TRAPEZOID
    }

    public enum AnchorKind {
        ABSOLUTE,
        ABOVE_BOTTOM,
        BELOW_TOP
    }

    /** One configured-ore target in the exact order stored in the official JSON. */
    public record OreTarget(String replacementState, TargetKind targetKind, boolean deepslate) {
        public OreTarget {
            if (replacementState == null || !replacementState.startsWith("minecraft:")) {
                throw new IllegalArgumentException("namespaced replacement state is required");
            }
        }

        /**
         * Implements the pinned tag/height rule without collapsing the Y=0..8 tuff overlap.
         * A caller must visit the returned targets in order because both split targets can match.
         */
        public boolean matches(String blockState, int y) {
            return matchesBlockKey(blockKey(blockState), y);
        }

        /**
         * The same rule keyed by the property-free block key a caller already holds.
         *
         * <p>{@code blockKey(state)} is a prefix of the exact state, so requiring the namespace
         * here rejects exactly the states {@link #matches} rejected before extracting it. A
         * caller reading a live region already knows this key without scanning for {@code '['},
         * and the ore inner loop tests one cell against every ordered target.</p>
         */
        public boolean matchesBlockKey(String block, int y) {
            requireBlockKey(block);
            return matchesCheckedBlockKey(block, y);
        }

        /**
         * The rule itself, for a key the caller has already put through
         * {@link Mc263OreCatalog#requireBlockKey}. The namespace is a property of the key and not
         * of the pair, so a caller that tests one cell against every ordered target checks it
         * once instead of once per target.
         */
        boolean matchesCheckedBlockKey(String block, int y) {
            if (targetKind == TargetKind.BASE_STONE_OVERWORLD) {
                return BASE_STONE_OVERWORLD.contains(block);
            }
            if (block.equals("minecraft:tuff")) {
                return deepslate ? y >= -2032 && y <= 8 : y >= 0 && y <= 2031;
            }
            return deepslate
                    ? DEEPSLATE_ORE_REPLACEABLES.contains(block)
                    : STONE_ORE_REPLACEABLES.contains(block);
        }
    }

    @FunctionalInterface
    public interface InclusiveIntRandom {
        int nextIntInclusive(int minInclusive, int maxInclusive);
    }

    public static final class Anchor {
        private final AnchorKind kind;
        private final int value;

        private Anchor(AnchorKind kind, int value) {
            this.kind = kind;
            this.value = value;
        }

        public AnchorKind kind() {
            return kind;
        }

        public int value() {
            return value;
        }

        /** Matches {@code VerticalAnchor.resolveY}; generation depth is a height, not max Y. */
        public int resolve(int minGenerationY, int generationDepth) {
            return switch (kind) {
                case ABSOLUTE -> value;
                case ABOVE_BOTTOM -> minGenerationY + value;
                case BELOW_TOP -> minGenerationY + generationDepth - 1 - value;
            };
        }
    }

    public static final class ConfiguredOre {
        private final String key;
        private final int size;
        private final float discardChanceOnAirExposure;
        private final TargetKind targetKind;
        private final String stoneState;
        private final String deepslateState;

        private ConfiguredOre(String key, int size, float discardChanceOnAirExposure,
                TargetKind targetKind, String stoneState, String deepslateState) {
            this.key = key;
            this.size = size;
            this.discardChanceOnAirExposure = discardChanceOnAirExposure;
            this.targetKind = targetKind;
            this.stoneState = stoneState;
            this.deepslateState = deepslateState;
        }

        public String key() {
            return key;
        }

        public int size() {
            return size;
        }

        public float discardChanceOnAirExposure() {
            return discardChanceOnAirExposure;
        }

        public TargetKind targetKind() {
            return targetKind;
        }

        public String stoneState() {
            return stoneState;
        }

        public String deepslateState() {
            return deepslateState;
        }

        /** The official height-specific target predicates overlap from Y=0 through Y=8. */
        public int stoneHeightSpecificMinimum() {
            return 0;
        }

        public int deepslateHeightSpecificMaximum() {
            return 8;
        }

        public String baseStoneTargetTag() {
            return "minecraft:base_stone_overworld";
        }

        public String heightSpecificTargetTag() {
            return "minecraft:height_specific_ore_replaceables";
        }

        public String stoneTargetTag() {
            return "minecraft:stone_ore_replaceables";
        }

        public String deepslateTargetTag() {
            return "minecraft:deepslate_ore_replaceables";
        }

        /** Returns the executable replacement rules in official JSON order. */
        public List<OreTarget> orderedTargets() {
            if (targetKind == TargetKind.BASE_STONE_OVERWORLD) {
                return List.of(new OreTarget(stoneState, targetKind, false));
            }
            return List.of(
                    new OreTarget(stoneState, targetKind, false),
                    new OreTarget(deepslateState, targetKind, true));
        }
    }

    public static final class PlacedFeature {
        private final String key;
        private final String configuredKey;
        private final FrequencyKind frequencyKind;
        private final int frequencyMin;
        private final int frequencyMax;
        private final HeightKind heightKind;
        private final Anchor minHeight;
        private final Anchor maxHeight;

        private PlacedFeature(String key, String configuredKey, FrequencyKind frequencyKind,
                int frequencyMin, int frequencyMax, HeightKind heightKind,
                Anchor minHeight, Anchor maxHeight) {
            this.key = key;
            this.configuredKey = configuredKey;
            this.frequencyKind = frequencyKind;
            this.frequencyMin = frequencyMin;
            this.frequencyMax = frequencyMax;
            this.heightKind = heightKind;
            this.minHeight = minHeight;
            this.maxHeight = maxHeight;
        }

        public String key() {
            return key;
        }

        public String configuredKey() {
            return configuredKey;
        }

        public FrequencyKind frequencyKind() {
            return frequencyKind;
        }

        public int frequencyMin() {
            return frequencyMin;
        }

        public int frequencyMax() {
            return frequencyMax;
        }

        public HeightKind heightKind() {
            return heightKind;
        }

        public Anchor minHeight() {
            return minHeight;
        }

        public Anchor maxHeight() {
            return maxHeight;
        }

        /** Implements CountPlacement's fixed or UniformInt count. Rarity uses {@link #passesRarity}. */
        public int sampleCount(InclusiveIntRandom random) {
            return switch (frequencyKind) {
                case COUNT -> frequencyMin;
                case COUNT_UNIFORM -> checkedRandom(random, frequencyMin, frequencyMax);
                case RARITY_FILTER -> throw new IllegalStateException("rarity filter has no count");
            };
        }

        /** Implements the pinned {@code RarityFilter.shouldPlace} float draw exactly. */
        public boolean passesRarity(McRandom random) {
            if (frequencyKind != FrequencyKind.RARITY_FILTER) {
                throw new IllegalStateException("feature does not use a rarity filter");
            }
            return random.nextFloat() < 1.0f / frequencyMin;
        }

        /** Implements the pinned UniformHeight/TrapezoidHeight sample bytecode exactly. */
        public int sampleHeight(InclusiveIntRandom random, int minGenerationY, int generationDepth) {
            int min = minHeight.resolve(minGenerationY, generationDepth);
            int max = maxHeight.resolve(minGenerationY, generationDepth);
            if (min > max) {
                return min;
            }
            if (heightKind == HeightKind.UNIFORM) {
                return checkedRandom(random, min, max);
            }
            int span = max - min;
            int lowerHalf = span / 2;
            int upperHalf = span - lowerHalf;
            return min + checkedRandom(random, 0, upperHalf)
                    + checkedRandom(random, 0, lowerHalf);
        }
    }

    public static final class FossilFeature {
        private final String placedKey;
        private final String configuredKey;
        private final Anchor minHeight;
        private final Anchor maxHeight;
        private final String overlayProcessors;

        private FossilFeature(String placedKey, String configuredKey, Anchor minHeight,
                Anchor maxHeight, String overlayProcessors) {
            this.placedKey = placedKey;
            this.configuredKey = configuredKey;
            this.minHeight = minHeight;
            this.maxHeight = maxHeight;
            this.overlayProcessors = overlayProcessors;
        }

        public String placedKey() {
            return placedKey;
        }

        public String configuredKey() {
            return configuredKey;
        }

        public int rarityChance() {
            return 64;
        }

        public Anchor minHeight() {
            return minHeight;
        }

        public Anchor maxHeight() {
            return maxHeight;
        }

        public String fossilProcessors() {
            return "fossil_rot";
        }

        public String overlayProcessors() {
            return overlayProcessors;
        }

        public int maxEmptyCornersAllowed() {
            return 4;
        }

        public float fossilIntegrity() {
            return 0.9f;
        }

        public float overlayIntegrity() {
            return 0.1f;
        }

        public String overlayRuleInputState() {
            return configuredKey.equals("fossil_diamonds") ? "minecraft:coal_ore" : null;
        }

        public String overlayRuleOutputState() {
            return configuredKey.equals("fossil_diamonds")
                    ? "minecraft:deepslate_diamond_ore" : null;
        }

        public List<String> fossilStructures() {
            return FOSSIL_STRUCTURES;
        }

        public List<String> overlayStructures() {
            return FOSSIL_OVERLAY_STRUCTURES;
        }
    }

    private static final Anchor ABS_NEG_64 = absolute(-64);
    private static final Anchor BOTTOM_0 = aboveBottom(0);
    private static final Set<String> BASE_STONE_OVERWORLD = Set.of(
            "minecraft:stone", "minecraft:granite", "minecraft:diorite",
            "minecraft:andesite", "minecraft:tuff", "minecraft:deepslate");
    private static final Set<String> STONE_ORE_REPLACEABLES = Set.of(
            "minecraft:stone", "minecraft:granite", "minecraft:diorite",
            "minecraft:andesite");
    private static final Set<String> DEEPSLATE_ORE_REPLACEABLES = Set.of(
            "minecraft:deepslate");
    private static final List<ConfiguredOre> CONFIGURED_ORES = List.of(
            base("ore_andesite", 64, "andesite"),
            base("ore_clay", 33, "clay"),
            split("ore_coal", 17, 0.0f, "coal_ore", "deepslate_coal_ore"),
            split("ore_coal_buried", 17, 0.5f, "coal_ore", "deepslate_coal_ore"),
            split("ore_copper_large", 20, 0.0f, "copper_ore", "deepslate_copper_ore"),
            split("ore_copper_small", 10, 0.0f, "copper_ore", "deepslate_copper_ore"),
            split("ore_diamond_buried", 8, 1.0f, "diamond_ore", "deepslate_diamond_ore"),
            split("ore_diamond_large", 12, 0.7f, "diamond_ore", "deepslate_diamond_ore"),
            split("ore_diamond_medium", 8, 0.5f, "diamond_ore", "deepslate_diamond_ore"),
            split("ore_diamond_small", 4, 0.5f, "diamond_ore", "deepslate_diamond_ore"),
            base("ore_diorite", 64, "diorite"),
            base("ore_dirt", 33, "dirt"),
            split("ore_emerald", 3, 0.0f, "emerald_ore", "deepslate_emerald_ore"),
            split("ore_gold", 9, 0.0f, "gold_ore", "deepslate_gold_ore"),
            split("ore_gold_buried", 9, 0.5f, "gold_ore", "deepslate_gold_ore"),
            base("ore_granite", 64, "granite"),
            base("ore_gravel", 33, "gravel"),
            split("ore_infested", 9, 0.0f, "infested_stone", "infested_deepslate"),
            split("ore_iron", 9, 0.0f, "iron_ore", "deepslate_iron_ore"),
            split("ore_iron_small", 4, 0.0f, "iron_ore", "deepslate_iron_ore"),
            split("ore_lapis", 7, 0.0f, "lapis_ore", "deepslate_lapis_ore"),
            split("ore_lapis_buried", 7, 1.0f, "lapis_ore", "deepslate_lapis_ore"),
            split("ore_redstone", 8, 0.0f, "redstone_ore", "deepslate_redstone_ore"),
            base("ore_tuff", 64, "tuff")
    );

    private static final List<PlacedFeature> PLACED_FEATURES = List.of(
            count("ore_andesite_lower", "ore_andesite", 2, HeightKind.UNIFORM, absolute(0), absolute(60)),
            rarity("ore_andesite_upper", "ore_andesite", 6, HeightKind.UNIFORM, absolute(64), absolute(128)),
            count("ore_clay", "ore_clay", 46, HeightKind.UNIFORM, BOTTOM_0, absolute(256)),
            count("ore_coal_lower", "ore_coal_buried", 20, HeightKind.TRAPEZOID, absolute(0), absolute(192)),
            count("ore_coal_upper", "ore_coal", 30, HeightKind.UNIFORM, absolute(136), belowTop(0)),
            count("ore_copper", "ore_copper_small", 16, HeightKind.TRAPEZOID, absolute(-16), absolute(112)),
            count("ore_copper_large", "ore_copper_large", 16, HeightKind.TRAPEZOID, absolute(-16), absolute(112)),
            count("ore_diamond", "ore_diamond_small", 7, HeightKind.TRAPEZOID, aboveBottom(-80), aboveBottom(80)),
            count("ore_diamond_buried", "ore_diamond_buried", 4, HeightKind.TRAPEZOID, aboveBottom(-80), aboveBottom(80)),
            rarity("ore_diamond_large", "ore_diamond_large", 9, HeightKind.TRAPEZOID, aboveBottom(-80), aboveBottom(80)),
            count("ore_diamond_medium", "ore_diamond_medium", 2, HeightKind.UNIFORM, ABS_NEG_64, absolute(-4)),
            count("ore_diorite_lower", "ore_diorite", 2, HeightKind.UNIFORM, absolute(0), absolute(60)),
            rarity("ore_diorite_upper", "ore_diorite", 6, HeightKind.UNIFORM, absolute(64), absolute(128)),
            count("ore_dirt", "ore_dirt", 7, HeightKind.UNIFORM, absolute(0), absolute(160)),
            count("ore_emerald", "ore_emerald", 100, HeightKind.TRAPEZOID, absolute(-16), absolute(480)),
            count("ore_gold", "ore_gold_buried", 4, HeightKind.TRAPEZOID, ABS_NEG_64, absolute(32)),
            count("ore_gold_extra", "ore_gold", 50, HeightKind.UNIFORM, absolute(32), absolute(256)),
            countUniform("ore_gold_lower", "ore_gold_buried", 0, 1, HeightKind.UNIFORM, ABS_NEG_64, absolute(-48)),
            count("ore_granite_lower", "ore_granite", 2, HeightKind.UNIFORM, absolute(0), absolute(60)),
            rarity("ore_granite_upper", "ore_granite", 6, HeightKind.UNIFORM, absolute(64), absolute(128)),
            count("ore_gravel", "ore_gravel", 14, HeightKind.UNIFORM, BOTTOM_0, belowTop(0)),
            count("ore_infested", "ore_infested", 14, HeightKind.UNIFORM, BOTTOM_0, absolute(63)),
            count("ore_iron_middle", "ore_iron", 10, HeightKind.TRAPEZOID, absolute(-24), absolute(56)),
            count("ore_iron_small", "ore_iron_small", 10, HeightKind.UNIFORM, BOTTOM_0, absolute(72)),
            count("ore_iron_upper", "ore_iron", 90, HeightKind.TRAPEZOID, absolute(80), absolute(384)),
            count("ore_lapis", "ore_lapis", 2, HeightKind.TRAPEZOID, absolute(-32), absolute(32)),
            count("ore_lapis_buried", "ore_lapis_buried", 4, HeightKind.UNIFORM, BOTTOM_0, absolute(64)),
            count("ore_redstone", "ore_redstone", 4, HeightKind.UNIFORM, BOTTOM_0, absolute(15)),
            count("ore_redstone_lower", "ore_redstone", 8, HeightKind.TRAPEZOID, aboveBottom(-32), aboveBottom(32)),
            count("ore_tuff", "ore_tuff", 2, HeightKind.UNIFORM, BOTTOM_0, absolute(0))
    );

    /** Official feature order inside generation step 6; this order is intentionally not sorted. */
    private static final List<String> COMMON_ORE_ORDER = List.of(
            "ore_dirt", "ore_gravel", "ore_granite_upper", "ore_granite_lower",
            "ore_diorite_upper", "ore_diorite_lower", "ore_andesite_upper", "ore_andesite_lower",
            "ore_tuff", "ore_coal_upper", "ore_coal_lower", "ore_iron_upper",
            "ore_iron_middle", "ore_iron_small", "ore_gold", "ore_gold_lower",
            "ore_redstone", "ore_redstone_lower", "ore_diamond", "ore_diamond_medium",
            "ore_diamond_large", "ore_diamond_buried", "ore_lapis", "ore_lapis_buried",
            "ore_copper"
    );

    private static final Set<String> FOSSIL_BIOMES = Set.of("desert", "mangrove_swamp", "swamp");
    private static final Set<String> MOUNTAIN_BIOMES = Set.of(
            "cherry_grove", "frozen_peaks", "grove", "jagged_peaks", "meadow",
            "snowy_slopes", "stony_peaks", "windswept_forest", "windswept_gravelly_hills",
            "windswept_hills"
    );
    private static final Set<String> BADLANDS_BIOMES = Set.of(
            "badlands", "eroded_badlands", "wooded_badlands"
    );

    private static final List<String> OVERWORLD_BIOMES = List.of(
            "mushroom_fields", "deep_frozen_ocean", "frozen_ocean", "deep_cold_ocean",
            "cold_ocean", "deep_ocean", "ocean", "deep_lukewarm_ocean", "lukewarm_ocean",
            "warm_ocean", "stony_shore", "swamp", "mangrove_swamp", "snowy_slopes",
            "snowy_plains", "snowy_beach", "windswept_gravelly_hills", "grove",
            "windswept_hills", "snowy_taiga", "windswept_forest", "taiga", "plains",
            "meadow", "beach", "forest", "old_growth_spruce_taiga", "flower_forest",
            "birch_forest", "dark_forest", "pale_garden", "savanna_plateau", "savanna",
            "jungle", "badlands", "desert", "wooded_badlands", "jagged_peaks",
            "stony_peaks", "frozen_river", "river", "ice_spikes", "dappled_forest",
            "old_growth_pine_taiga", "sunflower_plains", "old_growth_birch_forest",
            "sparse_jungle", "bamboo_jungle", "eroded_badlands", "windswept_savanna",
            "cherry_grove", "frozen_peaks", "dripstone_caves", "lush_caves",
            "sulfur_caves", "deep_dark"
    );

    private static final List<String> FOSSIL_STRUCTURES = List.of(
            "fossil/spine_1", "fossil/spine_2", "fossil/spine_3", "fossil/spine_4",
            "fossil/skull_1", "fossil/skull_2", "fossil/skull_3", "fossil/skull_4"
    );
    private static final List<String> FOSSIL_OVERLAY_STRUCTURES = List.of(
            "fossil/spine_1_coal", "fossil/spine_2_coal", "fossil/spine_3_coal",
            "fossil/spine_4_coal", "fossil/skull_1_coal", "fossil/skull_2_coal",
            "fossil/skull_3_coal", "fossil/skull_4_coal"
    );
    private static final List<FossilFeature> FOSSILS = List.of(
            new FossilFeature("fossil_lower", "fossil_diamonds", BOTTOM_0, absolute(-8),
                    "fossil_diamonds"),
            new FossilFeature("fossil_upper", "fossil_coal", absolute(0), belowTop(0),
                    "fossil_coal")
    );

    static {
        requireSortedUnique(CONFIGURED_ORES.stream().map(ConfiguredOre::key).toList(), 24,
                "configured ores");
        requireSortedUnique(PLACED_FEATURES.stream().map(PlacedFeature::key).toList(), 30,
                "placed ores");
        requireSortedUnique(FOSSILS.stream().map(FossilFeature::placedKey).toList(), 2,
                "fossils");
        Set<String> configured = new LinkedHashSet<>(
                CONFIGURED_ORES.stream().map(ConfiguredOre::key).toList());
        for (PlacedFeature placed : PLACED_FEATURES) {
            if (!configured.contains(placed.configuredKey())) {
                throw new ExceptionInInitializerError("unknown configured ore: " + placed.configuredKey());
            }
        }
        if (new LinkedHashSet<>(OVERWORLD_BIOMES).size() != 56) {
            throw new ExceptionInInitializerError("official Overworld biome set is incomplete");
        }
    }

    private Mc263OreCatalog() {
    }

    public static List<ConfiguredOre> configuredOres() {
        return CONFIGURED_ORES;
    }

    public static ConfiguredOre configuredOre(String key) {
        String bareKey = stripMinecraftNamespace(key);
        return CONFIGURED_ORES.stream().filter(value -> value.key().equals(bareKey))
                .findFirst().orElseThrow(() -> new IllegalArgumentException(
                        "unknown pinned configured ore: " + key));
    }

    public static List<PlacedFeature> placedFeatures() {
        return PLACED_FEATURES;
    }

    public static PlacedFeature placedFeature(String key) {
        String bareKey = stripMinecraftNamespace(key);
        return PLACED_FEATURES.stream().filter(value -> value.key().equals(bareKey))
                .findFirst().orElseThrow(() -> new IllegalArgumentException(
                        "unknown pinned placed ore: " + key));
    }

    public static List<FossilFeature> fossils() {
        return FOSSILS;
    }

    /** Official tag order is preserved because it is source evidence, not lookup order. */
    public static List<String> overworldBiomes() {
        return OVERWORLD_BIOMES;
    }

    /** Returns only ore/fossil entries at the requested official generation step. */
    public static List<String> featureKeysForBiomeStep(String biome, int step) {
        String key = stripMinecraftNamespace(biome);
        if (!OVERWORLD_BIOMES.contains(key)) {
            throw new IllegalArgumentException("unknown pinned Overworld biome: " + biome);
        }
        if (step == UNDERGROUND_STRUCTURES_STEP) {
            return FOSSIL_BIOMES.contains(key)
                    ? List.of("fossil_upper", "fossil_lower") : List.of();
        }
        if (step == UNDERGROUND_ORES_STEP) {
            List<String> result = new ArrayList<>(COMMON_ORE_ORDER);
            if (key.equals("dripstone_caves")) {
                result.set(result.size() - 1, "ore_copper_large");
            }
            if (MOUNTAIN_BIOMES.contains(key)) {
                result.add("ore_emerald");
            }
            if (BADLANDS_BIOMES.contains(key)) {
                result.add("ore_gold_extra");
            }
            if (key.equals("lush_caves")) {
                result.add("ore_clay");
            }
            return Collections.unmodifiableList(result);
        }
        if (step == UNDERGROUND_DECORATION_STEP && MOUNTAIN_BIOMES.contains(key)) {
            return List.of("ore_infested");
        }
        return List.of();
    }

    private static String stripMinecraftNamespace(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("biome is required");
        }
        return value.startsWith("minecraft:") ? value.substring("minecraft:".length()) : value;
    }

    /** The namespace law every ore-target test applies to the key it is given. */
    static void requireBlockKey(String block) {
        if (block == null || !block.startsWith("minecraft:")) {
            throw new IllegalArgumentException(
                    "exact minecraft block state is required: " + block);
        }
    }

    /** Property-free block key of an exact state; shared with the ore leaf's read fast path. */
    static String blockKey(String state) {
        if (state == null || !state.startsWith("minecraft:")) {
            throw new IllegalArgumentException("exact minecraft block state is required: " + state);
        }
        return blockKeyUnchecked(state);
    }

    /** Namespace-agnostic extraction; the namespace is checked where the key is consumed. */
    static String blockKeyUnchecked(String state) {
        int properties = state.indexOf('[');
        return properties < 0 ? state : state.substring(0, properties);
    }

    private static ConfiguredOre base(String key, int size, String state) {
        return new ConfiguredOre(key, size, 0.0f, TargetKind.BASE_STONE_OVERWORLD,
                "minecraft:" + state, null);
    }

    private static ConfiguredOre split(String key, int size, float discardChance,
            String stoneState, String deepslateState) {
        return new ConfiguredOre(key, size, discardChance,
                TargetKind.HEIGHT_SPLIT_STONE_AND_DEEPSLATE,
                "minecraft:" + stoneState, "minecraft:" + deepslateState);
    }

    private static PlacedFeature count(String key, String configured, int count,
            HeightKind heightKind, Anchor min, Anchor max) {
        return new PlacedFeature(key, configured, FrequencyKind.COUNT, count, count,
                heightKind, min, max);
    }

    private static PlacedFeature countUniform(String key, String configured, int minCount,
            int maxCount, HeightKind heightKind, Anchor min, Anchor max) {
        return new PlacedFeature(key, configured, FrequencyKind.COUNT_UNIFORM,
                minCount, maxCount, heightKind, min, max);
    }

    private static PlacedFeature rarity(String key, String configured, int chance,
            HeightKind heightKind, Anchor min, Anchor max) {
        return new PlacedFeature(key, configured, FrequencyKind.RARITY_FILTER,
                chance, chance, heightKind, min, max);
    }

    private static Anchor absolute(int value) {
        return new Anchor(AnchorKind.ABSOLUTE, value);
    }

    private static Anchor aboveBottom(int value) {
        return new Anchor(AnchorKind.ABOVE_BOTTOM, value);
    }

    private static Anchor belowTop(int value) {
        return new Anchor(AnchorKind.BELOW_TOP, value);
    }

    private static int checkedRandom(InclusiveIntRandom random, int min, int max) {
        int value = random.nextIntInclusive(min, max);
        if (value < min || value > max) {
            throw new IllegalArgumentException("random value outside requested inclusive bounds");
        }
        return value;
    }

    private static void requireSortedUnique(List<String> values, int expectedSize, String label) {
        if (values.size() != expectedSize) {
            throw new ExceptionInInitializerError(label + " expected " + expectedSize
                    + " entries, got " + values.size());
        }
        String[] actual = values.toArray(String[]::new);
        String[] sorted = actual.clone();
        Arrays.sort(sorted);
        if (!Arrays.equals(actual, sorted)
                || new LinkedHashSet<>(values).size() != values.size()) {
            throw new ExceptionInInitializerError(label + " must be sorted and unique");
        }
    }
}
