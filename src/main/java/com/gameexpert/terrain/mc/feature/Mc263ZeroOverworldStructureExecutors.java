package com.gameexpert.terrain.mc.feature;

import com.gameexpert.terrain.mc.structure.Mc263StructureCarrier;
import java.util.List;
import java.util.Objects;

/**
 * Complete dormant capabilities for pinned structures whose Overworld biome membership is empty.
 *
 * <p>The entries remain in their registry/scheduler slots, but an Overworld carrier is invalid if
 * it persists a start or reference for any of them. Placement is therefore an intentional no-op
 * after pure carrier preflight; it never reads the feature world, consumes RNG, or writes.</p>
 */
final class Mc263ZeroOverworldStructureExecutors {
    static final List<String> STRUCTURE_KEYS = List.of(
            "minecraft:bastion_remnant",
            "minecraft:end_city",
            "minecraft:ruined_portal_nether",
            "minecraft:fortress",
            "minecraft:nether_fossil");

    private Mc263ZeroOverworldStructureExecutors() {}

    static void registerAll(
            Mc263CanonicalFeaturesProducerSkeleton.CanonicalStructureExecutorRegistry registry) {
        Objects.requireNonNull(registry, "canonical structure executor registry");
        for (String structureKey : STRUCTURE_KEYS) {
            registry.register(structureKey, executor(structureKey));
        }
    }

    static Mc263CanonicalFeaturesProducerSkeleton.CanonicalStructureExecutor executor(
            String structureKey) {
        if (!STRUCTURE_KEYS.contains(structureKey)) {
            throw new IllegalArgumentException(
                    "structure is not zero-Overworld: " + structureKey);
        }
        Mc263StructureIndexReceipt.Entry pinned = Mc263StructureIndexReceipt.entries().stream()
                .filter(entry -> entry.key().equals(structureKey))
                .findFirst()
                .orElseThrow();
        if (pinned.biomeMask() != 0L) {
            throw new IllegalStateException(
                    "zero-Overworld structure has nonzero pinned biome mask: " + structureKey);
        }
        return new ZeroOverworldExecutor(pinned);
    }

    private record ZeroOverworldExecutor(Mc263StructureIndexReceipt.Entry pinned)
            implements Mc263CanonicalFeaturesProducerSkeleton.CanonicalStructureExecutor {
        @Override
        public void preflight(
                Mc263CanonicalFeaturesProducerSkeleton.StructurePreflightContext context) {
            Objects.requireNonNull(context, "zero-Overworld structure preflight context");
            if (!pinned.equals(context.entry()) || context.entry().biomeMask() != 0L) {
                throw new IllegalArgumentException(
                        "zero-Overworld structure schedule mismatch: " + pinned.key());
            }
            rejectPersistedStart(context.carrier());
            rejectPersistedReference(context.carrier().referenceChunks());
            rejectPersistedReference(List.of(context.references()));
        }

        private void rejectPersistedStart(Mc263StructureCarrier carrier) {
            for (Mc263StructureCarrier.ChunkStarts chunk : carrier.startChunks()) {
                for (Mc263StructureCarrier.StartEntry start : chunk.orderedStarts()) {
                    if (pinned.key().equals(start.structureId())) {
                        throw new IllegalArgumentException(
                                "zero-Overworld structure start is persisted: " + pinned.key());
                    }
                }
            }
        }

        private void rejectPersistedReference(
                List<Mc263StructureCarrier.ChunkReferences> referenceChunks) {
            for (Mc263StructureCarrier.ChunkReferences chunk : referenceChunks) {
                for (Mc263StructureCarrier.ReferenceSet references : chunk.orderedSets()) {
                    if (pinned.key().equals(references.structureId())) {
                        throw new IllegalArgumentException(
                                "zero-Overworld structure reference is persisted: " + pinned.key());
                    }
                }
            }
        }

        @Override
        public void place(
                Mc263CanonicalFeaturesProducerSkeleton.StructurePlacementInput context) {
            Objects.requireNonNull(context, "zero-Overworld structure placement context");
            if (!pinned.key().equals(context.dispatcher().structureKey())) {
                throw new IllegalArgumentException(
                        "zero-Overworld structure dispatcher mismatch: " + pinned.key());
            }
        }
    }
}
