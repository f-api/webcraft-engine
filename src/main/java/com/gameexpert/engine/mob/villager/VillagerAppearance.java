package com.gameexpert.engine.mob.villager;

import com.gameexpert.engine.mob.Mob;

import java.util.List;
import java.util.Locale;

/**
 * Wire encoding of a villager's / zombie villager's VillagerData (biome type, profession, level)
 * into the species-scoped {@code visualFlags} bits the client composites with vanilla
 * VillagerProfessionLayer. Client {@code encodeVillagerVisualFlags} (net/Protocol.ts) is the same
 * encoding; the orders below are append-only wire vocabularies.
 */
public final class VillagerAppearance {
    /** Wire order of the biome type (0 = plains, the vanilla default). */
    public static final List<String> TYPES = List.of(
            "plains", "desert", "jungle", "savanna", "snow", "swamp", "taiga");
    /** Wire order of the profession (0 = none). */
    public static final List<String> PROFESSIONS = List.of(
            "none", "armorer", "butcher", "cartographer", "cleric", "farmer", "fisherman", "fletcher",
            "leatherworker", "librarian", "mason", "nitwit", "shepherd", "toolsmith", "weaponsmith");

    private VillagerAppearance() {
    }

    /**
     * Accepts authority spellings ({@code minecraft:snow}, {@code CLERIC}, ...); unknown values
     * encode as plains / none and the level is clamped to 1..5.
     */
    public static int visualFlags(String type, String profession, int level) {
        int typeIndex = Math.max(0, TYPES.indexOf(normalize(type)));
        int professionIndex = Math.max(0, PROFESSIONS.indexOf(normalize(profession)));
        int levelIndex = Math.min(4, Math.max(0, level - 1));
        return (typeIndex << Mob.VISUAL_VILLAGER_TYPE_SHIFT & Mob.VISUAL_VILLAGER_TYPE_MASK)
                | (professionIndex << Mob.VISUAL_VILLAGER_PROFESSION_SHIFT & Mob.VISUAL_VILLAGER_PROFESSION_MASK)
                | (levelIndex << Mob.VISUAL_VILLAGER_LEVEL_SHIFT & Mob.VISUAL_VILLAGER_LEVEL_MASK);
    }

    private static String normalize(String value) {
        if (value == null) return "";
        String stripped = value.startsWith("minecraft:") ? value.substring("minecraft:".length()) : value;
        return stripped.toLowerCase(Locale.ROOT);
    }
}
