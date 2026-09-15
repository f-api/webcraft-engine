package com.gameexpert.terrain.mc.structure;

import com.gameexpert.terrain.mc.structure.Mc263StructureBlockEntityNbtAuthority.Facts;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;

/**
 * Village adapter over {@link Mc263StructureBlockEntityNbtAuthority}.
 *
 * <p>This class no longer renders NBT: it only maps the village template DATA row onto the
 * family-agnostic canonical facts. The rendering itself is shared with every other generated
 * family, so a village block entity and an ancient-city block entity of the same type produce the
 * same bytes by construction rather than by two hand-kept writers agreeing.
 */
public final class Mc263VillageBlockEntityAuthority {
    private Mc263VillageBlockEntityAuthority() {}

    /**
     * Renders the exact save-with-full-metadata payload created after template placement.
     * The template DATA row owns the type/table facts; position and loot seed are live placement
     * facts. No settlement-corpus payload is accepted by this boundary.
     */
    public static byte[] synthesize(int x, int y, int z, String blockEntityType,
            String lootTable, long lootSeed) {
        Objects.requireNonNull(blockEntityType, "Village block-entity type");
        if ((lootTable == null) != !"minecraft:chest".equals(blockEntityType)) {
            throw new IllegalArgumentException(
                    "Village loot DATA must be a chest and every chest DATA must name its table");
        }
        return Mc263StructureBlockEntityNbtAuthority.render(x, y, z,
                facts(blockEntityType, lootTable, lootSeed));
    }

    private static Facts facts(String blockEntityType, String lootTable, long lootSeed) {
        return switch (blockEntityType) {
            case "minecraft:bell", "minecraft:lectern", "minecraft:banner" ->
                    new Mc263StructureBlockEntityNbtAuthority.Positioned(blockEntityType);
            case "minecraft:barrel" ->
                    new Mc263StructureBlockEntityNbtAuthority.ItemContainer(
                            blockEntityType, List.of());
            case "minecraft:chest" ->
                    new Mc263StructureBlockEntityNbtAuthority.LootContainer(
                            blockEntityType, lootTable, lootSeed);
            case "minecraft:brewing_stand" ->
                    new Mc263StructureBlockEntityNbtAuthority.BrewingStand(
                            blockEntityType, List.of(), 0, 0, 400, 20);
            case "minecraft:furnace", "minecraft:smoker", "minecraft:blast_furnace" ->
                    new Mc263StructureBlockEntityNbtAuthority.Furnace(
                            blockEntityType, List.of(), 0, 0, 0, 0, new LinkedHashMap<>());
            case "minecraft:campfire" ->
                    new Mc263StructureBlockEntityNbtAuthority.Campfire(
                            blockEntityType, List.of(), new int[4], new int[4]);
            case "minecraft:sign" ->
                    new Mc263StructureBlockEntityNbtAuthority.Sign(blockEntityType);
            default -> throw new IllegalArgumentException(
                    "unsupported Village block-entity type: " + blockEntityType);
        };
    }
}
