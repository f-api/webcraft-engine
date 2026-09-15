package com.gameexpert.terrain.mc.structure;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Dormant, data-only catalog for the pinned 26.3 Overworld jigsaw structures. */
public final class Mc263JigsawStructureCatalog {
    public static final String VERSION = "26.3-snapshot-7";
    public static final String SERVER_SHA1 = "06157fedd67ff4dd6e0e6fa4a9dd0af296f0dd61";
    public static final int STRUCTURE_COUNT = 27;
    public static final int ABANDONED_CAMP_COUNT = 18;

    private static final List<StructureSpec> SPECS = buildSpecs();
    private static final Map<String, StructureSpec> BY_KEY = index(SPECS);

    private Mc263JigsawStructureCatalog() {}

    public static List<StructureSpec> structures() {
        return SPECS;
    }

    public static StructureSpec require(String key) {
        StructureSpec spec = BY_KEY.get(key);
        if (spec == null) {
            throw new IllegalArgumentException("unsupported pinned jigsaw structure: " + key);
        }
        return spec;
    }

    private static List<StructureSpec> buildSpecs() {
        List<StructureSpec> specs = new ArrayList<>();
        String[] camps = {
                "bamboo_jungle", "birch_forest", "cherry_grove", "dappled_forest",
                "flower_forest", "forest", "meadow", "old_growth_birch_forest",
                "old_growth_pine_taiga", "old_growth_spruce_taiga", "pale_garden",
                "savanna", "snowy_taiga", "sparse_jungle", "swamp", "taiga",
                "windswept_forest", "wooded_badlands"
        };
        for (String biome : camps) {
            specs.add(new StructureSpec("minecraft:abandoned_camp_" + biome,
                    "minecraft:abandoned_camp/tent/" + biome, 2,
                    HeightMode.CONSTANT, 0, 0, true, "WORLD_SURFACE_WG", 80, 80,
                    TerrainAdaptation.BEARD_THIN, "surface_structures", "", 0,
                    LiquidSettings.APPLY_WATERLOGGING, AliasMode.NONE));
        }
        String[] villages = {"desert", "plains", "savanna", "snowy", "taiga"};
        for (String biome : villages) {
            specs.add(new StructureSpec("minecraft:village_" + biome,
                    "minecraft:village/" + biome + "/town_centers", 6,
                    HeightMode.CONSTANT, 0, 0, true, "WORLD_SURFACE_WG", 80, 80,
                    TerrainAdaptation.BEARD_THIN, "surface_structures", "", 0,
                    LiquidSettings.APPLY_WATERLOGGING, AliasMode.NONE));
        }
        specs.add(new StructureSpec("minecraft:pillager_outpost",
                "minecraft:pillager_outpost/base_plates", 7, HeightMode.CONSTANT,
                0, 0, true, "WORLD_SURFACE_WG", 80, 80,
                TerrainAdaptation.BEARD_THIN, "surface_structures", "", 0,
                LiquidSettings.APPLY_WATERLOGGING, AliasMode.NONE));
        specs.add(new StructureSpec("minecraft:trail_ruins", "minecraft:trail_ruins/tower",
                7, HeightMode.CONSTANT, -15, -15, false, "WORLD_SURFACE_WG", 80, 80,
                TerrainAdaptation.BURY, "underground_structures", "", 0,
                LiquidSettings.APPLY_WATERLOGGING, AliasMode.NONE));
        specs.add(new StructureSpec("minecraft:trial_chambers",
                "minecraft:trial_chambers/chamber/end", 20, HeightMode.UNIFORM,
                -40, -20, false, "", 116, 116, TerrainAdaptation.ENCAPSULATE,
                "underground_structures", "", 10, LiquidSettings.IGNORE_WATERLOGGING,
                AliasMode.TRIAL_CHAMBERS_PINNED));
        specs.add(new StructureSpec("minecraft:ancient_city",
                "minecraft:ancient_city/city_center", 7, HeightMode.CONSTANT,
                -27, -27, false, "", 116, 116, TerrainAdaptation.BEARD_BOX,
                "underground_decoration", "minecraft:city_anchor", 0,
                LiquidSettings.APPLY_WATERLOGGING, AliasMode.NONE));
        specs.sort(Comparator.comparing(StructureSpec::key));
        if (specs.size() != STRUCTURE_COUNT) {
            throw new ExceptionInInitializerError("pinned jigsaw structure count");
        }
        return List.copyOf(specs);
    }

    private static Map<String, StructureSpec> index(List<StructureSpec> specs) {
        Map<String, StructureSpec> result = new HashMap<>();
        for (StructureSpec spec : specs) {
            if (result.put(spec.key(), spec) != null) {
                throw new ExceptionInInitializerError("duplicate jigsaw structure: " + spec.key());
            }
        }
        return Map.copyOf(result);
    }

    public enum HeightMode { CONSTANT, UNIFORM }
    public enum TerrainAdaptation { BURY, BEARD_THIN, BEARD_BOX, ENCAPSULATE }
    public enum LiquidSettings { APPLY_WATERLOGGING, IGNORE_WATERLOGGING }
    public enum AliasMode { NONE, TRIAL_CHAMBERS_PINNED }

    public static final class StructureSpec {
        private final String key;
        private final String startPool;
        private final int depth;
        private final HeightMode heightMode;
        private final int minStartHeight;
        private final int maxStartHeight;
        private final boolean expansionHack;
        private final String projectStartToHeightmap;
        private final int maxHorizontalDistance;
        private final int maxVerticalDistance;
        private final TerrainAdaptation terrainAdaptation;
        private final String generationStep;
        private final String startJigsawName;
        private final int dimensionPadding;
        private final LiquidSettings liquidSettings;
        private final AliasMode aliasMode;

        private StructureSpec(String key, String startPool, int depth, HeightMode heightMode,
                int minStartHeight, int maxStartHeight, boolean expansionHack,
                String projectStartToHeightmap, int maxHorizontalDistance,
                int maxVerticalDistance, TerrainAdaptation terrainAdaptation,
                String generationStep, String startJigsawName, int dimensionPadding,
                LiquidSettings liquidSettings, AliasMode aliasMode) {
            this.key = key;
            this.startPool = startPool;
            this.depth = depth;
            this.heightMode = heightMode;
            this.minStartHeight = minStartHeight;
            this.maxStartHeight = maxStartHeight;
            this.expansionHack = expansionHack;
            this.projectStartToHeightmap = projectStartToHeightmap;
            this.maxHorizontalDistance = maxHorizontalDistance;
            this.maxVerticalDistance = maxVerticalDistance;
            this.terrainAdaptation = terrainAdaptation;
            this.generationStep = generationStep;
            this.startJigsawName = startJigsawName;
            this.dimensionPadding = dimensionPadding;
            this.liquidSettings = liquidSettings;
            this.aliasMode = aliasMode;
        }

        public String key() { return key; }
        public String startPool() { return startPool; }
        public int depth() { return depth; }
        public HeightMode heightMode() { return heightMode; }
        public int minStartHeight() { return minStartHeight; }
        public int maxStartHeight() { return maxStartHeight; }
        public boolean expansionHack() { return expansionHack; }
        public String projectStartToHeightmap() { return projectStartToHeightmap; }
        public int maxHorizontalDistance() { return maxHorizontalDistance; }
        public int maxVerticalDistance() { return maxVerticalDistance; }
        public TerrainAdaptation terrainAdaptation() { return terrainAdaptation; }
        public String generationStep() { return generationStep; }
        public String startJigsawName() { return startJigsawName; }
        public int dimensionPadding() { return dimensionPadding; }
        public LiquidSettings liquidSettings() { return liquidSettings; }
        public AliasMode aliasMode() { return aliasMode; }
    }
}
