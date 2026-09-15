package com.gameexpert.terrain.mc.feature;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Public construction boundary for the current canonical structure settlement batch.
 *
 * <p>The region's executable {@link Mc263FeaturesRegion.StructureBatch} stays package-private.
 * Structure-package executors can instead retain this bridge's typed immutable {@link Batch};
 * feature-package callers may convert it into the producer-owned transaction, or callers with a
 * live region may delegate directly through {@link #settle}. No lane is defaulted, reordered,
 * deduplicated, or normalized here, so the existing preflight, atomic mutation, and replay inputs
 * remain owned by {@link Mc263FeaturesRegion#settleStructureBatch}.</p>
 */
public final class Mc263StructureBatchBridge {
    private static final int REQUIRED_FINAL_CHUNK_SCHEMA = Mc263FinalChunkCodec.SCHEMA;

    private Mc263StructureBatchBridge() { }

    /**
     * Delegates one complete current batch to the existing atomic region settlement.
     * All rejecting validation remains inside the region before its first mutation.
     */
    public static Settlement settle(Mc263FeaturesRegion region, Batch batch,
            Mc263CanonicalFeaturesProducerSkeleton.StructurePlacementInput placement) {
        requireCurrentSchema();
        Objects.requireNonNull(region, "FEATURES region");
        Mc263FeaturesRegion.StructureBatchSettlement settled =
                region.settleStructureBatch(toRegionBatch(batch, placement));
        return project(settled);
    }

    /** Package-private handoff for producer-owned carrier transactions in this feature package. */
    static Mc263FeaturesRegion.StructureBatch toRegionBatch(Batch batch,
            Mc263CanonicalFeaturesProducerSkeleton.StructurePlacementInput placement) {
        requireCurrentSchema();
        batch = Objects.requireNonNull(batch, "structure batch");
        Mc263CanonicalFeaturesProducerSkeleton.StructurePlacementInput activePlacement =
                Objects.requireNonNull(placement, "structure placement context");
        List<Mc263FeaturesRegion.StructureBlockWrite> blocks = batch.blocks().stream()
                .map(value -> new Mc263FeaturesRegion.StructureBlockWrite(
                        value.blockX(), value.blockY(), value.blockZ(), value.exactState(),
                        value.owner()))
                .toList();
        List<Mc263FeaturesRegion.StructureLoot> loot = batch.loot().stream()
                .filter(value -> activePlacement.clip().contains(
                        value.blockX(), value.blockY(), value.blockZ()))
                .map(value -> new Mc263FeaturesRegion.StructureLoot(
                        value.blockX(), value.blockY(), value.blockZ(), value.lootTable(),
                        value.lootSeed(), activePlacement.productionContext(value.blockX(),
                                value.blockY(), value.blockZ(), value.lootTable())))
                .toList();
        List<Mc263FeaturesRegion.StructureArchaeology> archaeology = batch.archaeology().stream()
                .map(value -> new Mc263FeaturesRegion.StructureArchaeology(
                        value.blockX(), value.blockY(), value.blockZ(), value.lootTable(),
                        value.lootSeed()))
                .toList();
        List<Mc263FeaturesRegion.StructureBentEvidence> blockEntities =
                batch.blockEntities().stream()
                        .map(value -> new Mc263FeaturesRegion.StructureBentEvidence(
                                value.blockX(), value.blockY(), value.blockZ(),
                                value.blockIdentity(), value.entityType(), value.canonicalNbt()))
                        .toList();
        List<Mc263FeaturesRegion.StructureFluidTick> fluidTicks = batch.fluidTicks().stream()
                .map(value -> new Mc263FeaturesRegion.StructureFluidTick(
                        value.blockX(), value.blockY(), value.blockZ(), value.fluidKey(),
                        value.delay(), value.priority(), value.subTickOrder()))
                .toList();
        List<Mc263FeaturesRegion.StructurePostprocessMark> postprocessMarks =
                batch.postprocessMarks().stream()
                        .map(value -> new Mc263FeaturesRegion.StructurePostprocessMark(
                                value.blockX(), value.blockY(), value.blockZ()))
                        .toList();
        List<Mc263FeaturesRegion.StructureSpawner> spawners = batch.spawners().stream()
                .map(value -> new Mc263FeaturesRegion.StructureSpawner(
                        value.blockX(), value.blockY(), value.blockZ(), value.entityType()))
                .toList();
        List<Mc263FeaturesRegion.StructureBlockTick> blockTicks = batch.blockTicks().stream()
                .map(value -> new Mc263FeaturesRegion.StructureBlockTick(
                        value.blockX(), value.blockY(), value.blockZ(), value.blockKey(),
                        value.delay(), value.priority(), value.subTickOrder()))
                .toList();
        List<Mc263FeaturesRegion.StructureBee> bees = batch.bees().stream()
                .map(value -> new Mc263FeaturesRegion.StructureBee(
                        value.blockX(), value.blockY(), value.blockZ(), value.ticksInHive()))
                .toList();
        return new Mc263FeaturesRegion.StructureBatch(blocks, loot, archaeology, blockEntities,
                batch.entities(), fluidTicks, postprocessMarks, spawners, blockTicks, bees);
    }

    private static Settlement project(Mc263FeaturesRegion.StructureBatchSettlement settled) {
        LinkedHashMap<Destination, Mc263FinalChunkSidecars.BlockEntity> blockEntities =
                new LinkedHashMap<>();
        settled.blockEntities().forEach((destination, value) -> blockEntities.put(
                new Destination(destination.chunkX(), destination.chunkZ(),
                        destination.packedPosition()), value));
        LinkedHashMap<Destination, List<Mc263FinalChunkSidecars.StructureEntity>> entities =
                new LinkedHashMap<>();
        settled.entities().forEach((destination, values) -> entities.put(
                new Destination(destination.chunkX(), destination.chunkZ(),
                        destination.packedPosition()), List.copyOf(values)));
        return new Settlement(blockEntities, entities);
    }

    private static void requireCurrentSchema() {
        if (Mc263FinalChunkCodec.SCHEMA != REQUIRED_FINAL_CHUNK_SCHEMA) {
            throw new IllegalStateException("structure batch bridge supports only final schema "
                    + REQUIRED_FINAL_CHUNK_SCHEMA + ": " + Mc263FinalChunkCodec.SCHEMA);
        }
    }

    /** Immutable complete request; every current structure contribution lane is mandatory. */
    public record Batch(List<BlockWrite> blocks, List<Loot> loot,
                        List<Archaeology> archaeology, List<BentEvidence> blockEntities,
                        List<Mc263FinalChunkSidecars.StructureEntity> entities,
                        List<FluidTick> fluidTicks, List<PostprocessMark> postprocessMarks,
                        List<Spawner> spawners, List<BlockTick> blockTicks, List<Bee> bees) {
        public Batch {
            requireCurrentSchema();
            blocks = List.copyOf(Objects.requireNonNull(blocks, "structure blocks"));
            loot = List.copyOf(Objects.requireNonNull(loot, "structure loot"));
            archaeology = List.copyOf(Objects.requireNonNull(
                    archaeology, "structure archaeology"));
            blockEntities = List.copyOf(Objects.requireNonNull(
                    blockEntities, "structure BENT"));
            entities = List.copyOf(Objects.requireNonNull(entities, "structure ENTS"));
            fluidTicks = List.copyOf(Objects.requireNonNull(
                    fluidTicks, "structure fluid ticks"));
            postprocessMarks = List.copyOf(Objects.requireNonNull(
                    postprocessMarks, "structure postprocess marks"));
            spawners = List.copyOf(Objects.requireNonNull(spawners, "structure spawners"));
            blockTicks = List.copyOf(Objects.requireNonNull(
                    blockTicks, "structure block ticks"));
            bees = List.copyOf(Objects.requireNonNull(bees, "structure bees"));
        }
    }

    /** One exact absolute block write in caller encounter order. */
    public record BlockWrite(int blockX, int blockY, int blockZ, String exactState, long owner) {
        public BlockWrite {
            Objects.requireNonNull(exactState, "structure exact state");
        }
    }

    /** One absolute randomizable-container loot contribution. */
    public record Loot(int blockX, int blockY, int blockZ, String lootTable, long lootSeed) {
        public Loot {
            Objects.requireNonNull(lootTable, "structure loot table");
        }
    }

    /** One absolute brushable-block archaeology contribution. */
    public record Archaeology(int blockX, int blockY, int blockZ, String lootTable,
                              long lootSeed) {
        public Archaeology {
            Objects.requireNonNull(lootTable, "structure archaeology loot table");
        }
    }

    /** Canonical block-entity evidence at one absolute destination coordinate. */
    public record BentEvidence(int blockX, int blockY, int blockZ, String blockIdentity,
                               String entityType, byte[] canonicalNbt) {
        public BentEvidence {
            Objects.requireNonNull(blockIdentity, "structure BENT block identity");
            Objects.requireNonNull(entityType, "structure BENT entity type");
            canonicalNbt = Objects.requireNonNull(
                    canonicalNbt, "structure BENT canonical NBT").clone();
        }

        @Override public byte[] canonicalNbt() {
            return canonicalNbt.clone();
        }

        @Override public boolean equals(Object other) {
            return other instanceof BentEvidence value
                    && blockX == value.blockX && blockY == value.blockY
                    && blockZ == value.blockZ && blockIdentity.equals(value.blockIdentity)
                    && entityType.equals(value.entityType)
                    && Arrays.equals(canonicalNbt, value.canonicalNbt);
        }

        @Override public int hashCode() {
            int result = Objects.hash(blockX, blockY, blockZ, blockIdentity, entityType);
            return 31 * result + Arrays.hashCode(canonicalNbt);
        }
    }

    /** One absolute scheduled fluid tick in caller encounter order. */
    public record FluidTick(int blockX, int blockY, int blockZ, String fluidKey,
                            int delay, int priority, long subTickOrder) {
        public FluidTick {
            Objects.requireNonNull(fluidKey, "structure fluid tick type");
        }
    }

    /** One absolute ProtoChunk postprocessing mark; duplicates remain ordered. */
    public record PostprocessMark(int blockX, int blockY, int blockZ) { }

    /** One absolute generated spawner assignment. */
    public record Spawner(int blockX, int blockY, int blockZ, String entityType) {
        public Spawner {
            Objects.requireNonNull(entityType, "structure spawner entity type");
        }
    }

    /** One absolute scheduled block tick in caller encounter order. */
    public record BlockTick(int blockX, int blockY, int blockZ, String blockKey,
                            int delay, int priority, long subTickOrder) {
        public BlockTick {
            Objects.requireNonNull(blockKey, "structure block tick type");
        }
    }

    /** One pinned vanilla bee occupant attached to the final staged beehive. */
    public record Bee(int blockX, int blockY, int blockZ, int ticksInHive) { }

    /** Public destination identity for the BENT/ENTS result of an accepted batch. */
    public record Destination(int chunkX, int chunkZ, int packedPosition) { }

    /** Immutable accepted BENT/ENTS projection; map and entity encounter order are retained. */
    public record Settlement(
            Map<Destination, Mc263FinalChunkSidecars.BlockEntity> blockEntities,
            Map<Destination, List<Mc263FinalChunkSidecars.StructureEntity>> entities) {
        public Settlement {
            blockEntities = Collections.unmodifiableMap(new LinkedHashMap<>(
                    Objects.requireNonNull(blockEntities, "settled structure BENT")));
            LinkedHashMap<Destination, List<Mc263FinalChunkSidecars.StructureEntity>> copy =
                    new LinkedHashMap<>();
            Objects.requireNonNull(entities, "settled structure ENTS").forEach(
                    (destination, values) -> copy.put(destination, List.copyOf(values)));
            entities = Collections.unmodifiableMap(copy);
        }
    }
}
