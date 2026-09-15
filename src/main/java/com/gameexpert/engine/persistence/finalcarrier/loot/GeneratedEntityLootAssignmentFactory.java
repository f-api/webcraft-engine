package com.gameexpert.engine.persistence.finalcarrier.loot;

import com.gameexpert.terrain.Blocks;
import com.gameexpert.authority.versioned.NeutralFinalChunk;
import com.gameexpert.authority.versioned.NeutralFinalChunk.ContainerLootDeclaration;
import com.gameexpert.authority.versioned.NeutralFinalChunk.ContainerLootSourceSection;
import com.gameexpert.authority.versioned.NeutralFinalChunk.StructureEntity;
import java.util.Objects;

/** Pure ENTS assignment construction using the unchanged canonical LOOT identity grammar. */
public final class GeneratedEntityLootAssignmentFactory {
    private GeneratedEntityLootAssignmentFactory() {
    }

    public static WorldCanonicalLootAssignment create(long worldId, long worldSeed,
            int chunkX, int chunkZ, String installationIdentity, String sourceFingerprint,
            int encounterOrdinal, StructureEntity entity, ContainerLootDeclaration declaration, NeutralFinalChunk source) {
        Objects.requireNonNull(entity, "generated loot source entity");
        Objects.requireNonNull(declaration, "generated loot source declaration");
        if (!"minecraft:chest_minecart".equals(entity.entityKey())
                || declaration.sourceSection() != ContainerLootSourceSection.ENTS
                || declaration.sourceSectionOrdinal() != encounterOrdinal
                || declaration.containerSize() != CanonicalLootContainerKind.CHEST.slots()) {
            throw new IllegalStateException("generated loot declaration differs from its ENTS row");
        }
        if (!source.sidecars().entities().get(encounterOrdinal).equals(entity)
                || !source.sidecars().containerLootDeclarations().contains(declaration)) {
            throw new IllegalStateException("generated loot declaration lacks selected producer source");
        }
        int x = coordinate(entity.x()), y = coordinate(entity.y()), z = coordinate(entity.z());
        if (Math.floorDiv(x, Blocks.CHUNK_X) != chunkX
                || Math.floorDiv(z, Blocks.CHUNK_Z) != chunkZ
                || y < Blocks.MIN_Y || y > Blocks.MAX_Y) {
            throw new IllegalStateException("generated loot source escaped its installed chunk");
        }
        declaration.productionContext().requireMatches(worldSeed, entity.lootTable(), x, y, z);
        byte[] context = CanonicalLootAssignmentPlan.encodeLocatedProductionContextPayload(declaration);
        long[] initial = entity.lootSeed() == 0L
                ? CanonicalLootAssignmentPlan.namedInitialState(worldSeed, entity.lootTable()) : null;
        int packed = Blocks.blockIndex(Math.floorMod(x, Blocks.CHUNK_X), y,
                Math.floorMod(z, Blocks.CHUNK_Z));
        // This discriminator is entity provenance, not a fabricated physical chest facing.
        String facing = "entity";
        String definition = CanonicalLootAssignmentPlan.fingerprint(worldSeed,
                installationIdentity, sourceFingerprint, worldId, chunkX, chunkZ, packed,
                x, y, z, facing, entity.lootTable(), entity.lootSeed(),
                CanonicalLootContainerKind.CHEST, context, initial, ContainerLootSourceSection.ENTS);
        WorldCanonicalLootAssignment assignment = new WorldCanonicalLootAssignment(worldId, worldSeed, chunkX, chunkZ, packed,
                x, y, z, facing, entity.lootTable(), entity.lootSeed(), CanonicalLootContainerKind.CHEST,
                installationIdentity, sourceFingerprint, definition,
                initial == null ? null : initial[0], initial == null ? null : initial[1], context);
        assignment.verifyProducer(source);
        return assignment;
    }

    private static int coordinate(double value) {
        double floor = Math.floor(value);
        if (!Double.isFinite(value) || floor < Integer.MIN_VALUE || floor > Integer.MAX_VALUE) {
            throw new IllegalStateException("generated loot source coordinate is invalid");
        }
        return (int) floor;
    }
}
