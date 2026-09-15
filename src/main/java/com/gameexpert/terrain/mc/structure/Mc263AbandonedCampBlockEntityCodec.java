package com.gameexpert.terrain.mc.structure;

import java.util.List;
import java.util.Set;

/**
 * Authenticated Abandoned Camp block-entity vocabulary.
 *
 * <p>The camp keeps only the part that is family evidence — which block-entity type may carry which
 * authenticated loot table — and delegates the NBT rendering itself to
 * {@link Mc263StructureBlockEntityNbtAuthority}, so the camp shares one program per block-entity
 * type with every other generated family.</p>
 */
public final class Mc263AbandonedCampBlockEntityCodec {
    private static final String COPPER_GOLEM_STATUE = "minecraft:copper_golem_statue";
    private static final Set<String> LOOT_TYPES = Set.of("minecraft:barrel", "minecraft:chest");
    private static final Set<String> LOOT_TABLES = Set.of(
            "minecraft:barrels/abandoned_camp_barrel",
            "minecraft:chests/abandoned_camp_common_chest",
            "minecraft:chests/abandoned_camp_secret_chest");

    private Mc263AbandonedCampBlockEntityCodec() { }

    public static byte[] lootContainer(String entityType, int x, int y, int z,
            String lootTable, long lootSeed) {
        if (!LOOT_TYPES.contains(entityType)) {
            throw new IllegalArgumentException("unsupported Camp loot block entity: " + entityType);
        }
        if (!LOOT_TABLES.contains(lootTable)
                || (entityType.equals("minecraft:barrel") != lootTable.startsWith("minecraft:barrels/"))) {
            throw new IllegalArgumentException("unsupported Camp loot table/type pair");
        }
        return Mc263StructureBlockEntityNbtAuthority.render(x, y, z,
                new Mc263StructureBlockEntityNbtAuthority.LootContainer(
                        entityType, lootTable, lootSeed));
    }

    public static byte[] emptyCampfire(int x, int y, int z) {
        return Mc263StructureBlockEntityNbtAuthority.render(x, y, z,
                new Mc263StructureBlockEntityNbtAuthority.Campfire("minecraft:campfire",
                        List.of(), new int[4], new int[4]));
    }

    public static byte[] copperGolemStatue(String exactState, int x, int y, int z) {
        if (exactState == null
                || !exactState.matches("minecraft:oxidized_copper_golem_statue"
                        + "\\[copper_golem_pose=(?:running|sitting|standing|star),"
                        + "facing=(?:north|south|east|west),waterlogged=(?:false|true)]")) {
            throw new IllegalArgumentException(
                    "unsupported Camp copper golem statue state: " + exactState);
        }
        return Mc263StructureBlockEntityNbtAuthority.render(x, y, z,
                new Mc263StructureBlockEntityNbtAuthority.CopperGolemStatue(
                        COPPER_GOLEM_STATUE));
    }
}
