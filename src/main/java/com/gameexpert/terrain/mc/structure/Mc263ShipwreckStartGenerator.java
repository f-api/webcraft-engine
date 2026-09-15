package com.gameexpert.terrain.mc.structure;

import com.gameexpert.terrain.mc.feature.Mc263StructureIndexReceipt;
import com.gameexpert.terrain.mc.structure.Mc263ShipwreckCarrier.BoundingBox;
import com.gameexpert.terrain.mc.structure.Mc263ShipwreckCarrier.Rotation;
import com.gameexpert.terrain.mc.structure.Mc263StructureCarrier.ChunkReferences;
import com.gameexpert.terrain.mc.structure.Mc263StructureCarrier.ChunkStarts;
import com.gameexpert.terrain.mc.structure.Mc263StructureCarrier.Piece;
import com.gameexpert.terrain.mc.structure.Mc263StructureCarrier.PiecePayload;
import com.gameexpert.terrain.mc.structure.Mc263StructureCarrier.Projection;
import com.gameexpert.terrain.mc.structure.Mc263StructureCarrier.ReferenceSet;
import com.gameexpert.terrain.mc.structure.Mc263StructureCarrier.Registry;
import com.gameexpert.terrain.mc.structure.Mc263StructureCarrier.StartEntry;
import com.gameexpert.terrain.mc.structure.Mc263StructureCarrier.StructureDefinition;
import com.gameexpert.terrain.mc.structure.Mc263StructureCarrier.TerrainAdjustment;
import com.gameexpert.terrain.mc.structure.Mc263StructureCarrier.ValidStart;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Exact dormant start selection and one-piece carrier construction for both Shipwreck keys. */
public final class Mc263ShipwreckStartGenerator {
    public static final String STRUCTURE_SET = "minecraft:shipwrecks";
    public static final int DECORATION_STEP = 4;
    public static final int OCEAN_STEP_INDEX = 35;
    public static final int BEACHED_STEP_INDEX = 36;
    public static final int OCEAN_REGISTRY_ORDINAL = 40;
    public static final int BEACHED_REGISTRY_ORDINAL = 41;

    private Mc263ShipwreckStartGenerator() { }

    public interface WorldAccess {
        boolean supportsOceanFloorWg();
        boolean supportsWorldSurfaceWg();
        boolean supportsValidBiomeTest();
        int baseHeight(Heightmap heightmap, int blockX, int blockZ);
        boolean isValidBiome(int blockX, int blockY, int blockZ);
    }
    public enum Heightmap { OCEAN_FLOOR_WG, WORLD_SURFACE_WG }

    public record Result(Mc263ShipwreckCarrier carrier, ChunkStarts startChunk,
            ChunkReferences references, int generationPointY) {
        public Result {
            Objects.requireNonNull(carrier, "shipwreck carrier");
            Objects.requireNonNull(startChunk, "shipwreck start chunk");
            Objects.requireNonNull(references, "shipwreck references");
        }
    }

    public static Result generate(String structureKey, long worldSeed, int chunkX, int chunkZ,
            int priorReferences, Registry registry, WorldAccess world) {
        Objects.requireNonNull(structureKey, "shipwreck structure key");
        Objects.requireNonNull(registry, "shipwreck registry");
        Objects.requireNonNull(world, "shipwreck start world");
        boolean beached = switch (structureKey) {
            case Mc263ShipwreckCarrier.OCEAN -> false;
            case Mc263ShipwreckCarrier.BEACHED -> true;
            default -> throw new IllegalArgumentException("unsupported shipwreck key");
        };
        if (priorReferences < 0) throw new IllegalArgumentException("negative prior references");
        validateRegistry(registry);
        if (!world.supportsValidBiomeTest() || (beached && !world.supportsWorldSurfaceWg())
                || (!beached && !world.supportsOceanFloorWg())) {
            throw new UnsupportedOperationException("complete shipwreck start capabilities required");
        }

        List<String> order = beached ? Mc263ShipwreckCatalog.beachedOrder()
                : Mc263ShipwreckCatalog.oceanOrder();
        Legacy48 random = Legacy48.largeFeature(worldSeed, chunkX, chunkZ);
        Rotation rotation = Rotation.values()[random.nextInt(4)];
        String selected = order.get(random.nextInt(order.size()));
        var template = Mc263ShipwreckCatalog.require(selected);
        int originX = Math.multiplyExact(chunkX, 16);
        int originZ = Math.multiplyExact(chunkZ, 16);
        int pointX = Math.addExact(originX, 8);
        int pointZ = Math.addExact(originZ, 8);
        Heightmap heightmap = beached ? Heightmap.WORLD_SURFACE_WG : Heightmap.OCEAN_FLOOR_WG;
        int generationPointY = Math.subtractExact(world.baseHeight(heightmap, pointX, pointZ), 1);
        if (!world.isValidBiome(pointX, generationPointY, pointZ)) return null;

        Mc263ShipwreckCarrier carrier = new Mc263ShipwreckCarrier(structureKey, worldSeed,
                chunkX, chunkZ, beached, template, rotation, originX, 90, originZ,
                Mc263ShipwreckCarrier.bounds(template, rotation, originX, 90, originZ), false,
                random.state(), random.continuation(8));
        BoundingBox box = carrier.boundingBox();
        Piece piece = new Piece(Mc263ShipwreckCarrier.PIECE_TYPE, box(box), false,
                Projection.NOT_APPLICABLE, 0, List.of(),
                new PiecePayload(carrier.canonicalPieceNbt()));
        ValidStart start = new ValidStart(startKey(structureKey, chunkX, chunkZ), chunkX, chunkZ,
                priorReferences, box(box), List.of(piece));
        ChunkStarts starts = new ChunkStarts(chunkX, chunkZ,
                List.of(new StartEntry(structureKey, start)));
        ChunkReferences references = new ChunkReferences(chunkX, chunkZ,
                List.of(new ReferenceSet(structureKey,
                        List.of(Mc263StructureCarrier.packChunk(chunkX, chunkZ)))));
        return new Result(carrier, starts, references, generationPointY);
    }

    public static void validateRegistry(Registry registry) {
        List<Mc263StructureIndexReceipt.Entry> pinned = Mc263StructureIndexReceipt.entries();
        List<StructureDefinition> definitions = registry.definitions();
        if (pinned.size() != 52 || definitions.size() != 52) {
            throw new IllegalArgumentException("STR263C1 registry is not pinned 52 entries");
        }
        for (int i = 0; i < 52; i++) {
            var receipt = pinned.get(i); var definition = definitions.get(i);
            if (definition.registryOrdinal() != i || !definition.structureId().equals(receipt.key())
                    || definition.decorationStep() != receipt.step()) {
                throw new IllegalArgumentException("shipwreck registry mismatch at " + i);
            }
        }
        requireSlot(definitions.get(OCEAN_REGISTRY_ORDINAL), Mc263ShipwreckCarrier.OCEAN,
                OCEAN_STEP_INDEX);
        requireSlot(definitions.get(BEACHED_REGISTRY_ORDINAL), Mc263ShipwreckCarrier.BEACHED,
                BEACHED_STEP_INDEX);
    }

    private static void requireSlot(StructureDefinition definition, String key, int stepIndex) {
        if (!definition.structureId().equals(key) || definition.decorationStep() != DECORATION_STEP
                || definition.terrainAdjustment() != TerrainAdjustment.NONE
                || !Mc263StructureIndexReceipt.step(DECORATION_STEP).get(stepIndex).key().equals(key)) {
            throw new IllegalArgumentException("shipwreck registry slot is not pinned");
        }
    }

    private static String startKey(String structure, int x, int z) {
        return structure + "@" + x + "," + z;
    }
    private static Mc263StructureCarrier.BoundingBox box(BoundingBox b) {
        return new Mc263StructureCarrier.BoundingBox(
                b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ());
    }

    /** java.util.Random-compatible 48-bit source including exact large-feature reseeding. */
    static final class Legacy48 {
        private static final long MULTIPLIER = 0x5deece66dL;
        private static final long ADDEND = 0xbL;
        private static final long MASK = (1L << 48) - 1;
        private long state;
        private Legacy48(long seed) { setSeed(seed); }
        static Legacy48 largeFeature(long seed, int chunkX, int chunkZ) {
            Legacy48 result = new Legacy48(seed);
            long first = result.nextLong();
            long second = result.nextLong();
            result.setSeed((long) chunkX * first ^ (long) chunkZ * second ^ seed);
            return result;
        }
        private void setSeed(long seed) { state = (seed ^ MULTIPLIER) & MASK; }
        private int next(int bits) {
            state = (state * MULTIPLIER + ADDEND) & MASK;
            return (int) (state >>> (48 - bits));
        }
        int nextInt(int bound) {
            if (bound <= 0) throw new IllegalArgumentException("nonpositive Legacy48 bound");
            if ((bound & -bound) == bound) return (int) ((bound * (long) next(31)) >> 31);
            int bits, value;
            do { bits = next(31); value = bits % bound; }
            while (bits - value + (bound - 1) < 0);
            return value;
        }
        long nextLong() { return ((long) next(32) << 32) + next(32); }
        long state() { return state; }
        List<Long> continuation(int count) {
            Legacy48 copy = new Legacy48(0); copy.state = state;
            ArrayList<Long> result = new ArrayList<>(count);
            for (int i = 0; i < count; i++) result.add(copy.nextLong());
            return List.copyOf(result);
        }
    }
}
