package com.gameexpert.terrain.mc.feature;

import com.gameexpert.terrain.Blocks;
import com.gameexpert.terrain.mc.ore.Mc263DecorationRandom;
import com.gameexpert.terrain.mc.structure.Mc263HardcodedStructureCarrier;
import com.gameexpert.terrain.mc.structure.Mc263OceanMonumentPieceProgram;
import com.gameexpert.terrain.mc.structure.Mc263OceanMonumentSettlement;
import com.gameexpert.terrain.mc.structure.Mc263StructureCarrier;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Production adapter for the accepted procedural Ocean Monument program and settlement. */
final class Mc263OceanMonumentCanonicalExecutor implements
        Mc263CanonicalFeaturesProducerSkeleton.CanonicalStructureExecutor {
    private static final String STRUCTURE = Mc263OceanMonumentSettlement.STRUCTURE;
    private static final int STEP = 4;
    private static final int INDEX = 24;
    private static final int REGISTRY_ORDINAL = 29;
    private static final long BIOME_MASK = 0x000000000000aaL;
    private static final long LEGACY_MASK = (1L << 48) - 1L;
    private static final long LEGACY_MULTIPLIER = 25214903917L;
    private static final long LEGACY_INCREMENT = 11L;
    private static final byte[] ELDER_PAYLOAD =
            "OME263E1".getBytes(StandardCharsets.US_ASCII);
    private static final Set<String> CAPABILITIES = Set.of(
            "block_query", "sea_level", "block_write", "replaceable_query",
            "minimum_y", "elder_guardian_emission");
    private static final List<String> EXACT_STATES = List.of(
            "minecraft:air", "minecraft:water", "minecraft:stone",
            "minecraft:prismarine", "minecraft:prismarine_bricks",
            "minecraft:dark_prismarine", "minecraft:sea_lantern",
            "minecraft:wet_sponge", "minecraft:gold_block");

    private final Capabilities capabilities;

    private Mc263OceanMonumentCanonicalExecutor(Capabilities capabilities) {
        this.capabilities = Objects.requireNonNull(capabilities, "monument capabilities");
    }

    static void register(
            Mc263CanonicalFeaturesProducerSkeleton.CanonicalStructureExecutorRegistry registry) {
        Objects.requireNonNull(registry, "canonical structure executor registry")
                .register(STRUCTURE, production());
    }

    static Mc263OceanMonumentCanonicalExecutor production() {
        return new Mc263OceanMonumentCanonicalExecutor(new Capabilities(true, true, true));
    }

    static Mc263OceanMonumentCanonicalExecutor fixture(
            boolean exactStates, boolean fluidTicks, boolean entities) {
        return new Mc263OceanMonumentCanonicalExecutor(
                new Capabilities(exactStates, fluidTicks, entities));
    }

    @Override
    public void preflight(
            Mc263CanonicalFeaturesProducerSkeleton.StructurePreflightContext context) {
        Objects.requireNonNull(context, "monument preflight context");
        // Capability rejection is first: no carrier resolution, query, RNG, or write may precede it.
        requireCapabilities();
        requireSchedule(context.entry(), context.carrier().registry());
        requireSourceBinding(context.references(), context.clip());
        validateReferencedStarts(context.worldSeed(), context.carrier(), context.references());
    }

    @Override
    public void place(Mc263CanonicalFeaturesProducerSkeleton.StructurePlacementInput context) {
        Objects.requireNonNull(context, "monument placement context");
        // Preserve the same fail-closed order as preflight before touching live region state.
        requireCapabilities();
        Mc263FeatureDispatcher.StructurePlacementContext dispatcher = context.dispatcher();
        requireDispatcher(dispatcher, context.references(), context.clip());
        requireSchedule(Mc263StructureIndexReceipt.step(STEP).get(INDEX),
                context.carrier().registry());

        List<String> startKeys = referencedStarts(context.carrier(), context.references()).stream()
                .map(Mc263StructureCarrier.ValidStart::startKey).toList();
        for (Mc263StructureCarrier.ValidStart start
                : referencedStarts(context.carrier(), context.references())) {
            requireCanonicalStart(dispatcher.worldSeed(), context.carrier(),
                    context.references(), start);
        }
        if (startKeys.isEmpty()) return;

        // One WorldgenRandom is bound per (chunk, structure, decoration step) and every start of
        // that structure is placed through it in closure order, so the candidate stream is opened
        // once and continues across starts instead of restarting from the feature seed.
        LegacyPlacementRandom candidate = new LegacyPlacementRandom(dispatcher.featureSeed());
        Mc263OceanMonumentPieceProgram.Clip clip = clip(context.clip());
        for (String startKey : startKeys) {
            Mc263StructureCarrier predecessor = context.carrier();
            Mc263StructureCarrier.ValidStart persisted = referencedStarts(
                    predecessor, context.references()).stream()
                    .filter(value -> value.startKey().equals(startKey)).findFirst()
                    .orElseThrow(() -> new IllegalStateException(
                            "evolved minecraft:monument carrier lost referenced start"));
            Mc263HardcodedStructureCarrier monument = Mc263HardcodedStructureCarrier.plan(
                    Mc263HardcodedStructureCarrier.Kind.OCEAN_MONUMENT,
                    dispatcher.worldSeed(), persisted.originChunkX(), persisted.originChunkZ());
            Mc263StructureCarrier successor = Mc263OceanMonumentSettlement.successorStr(
                    predecessor, context.references(), monument);
            SourceWorld world = new SourceWorld(dispatcher.region(), context.clip());

            for (Mc263HardcodedStructureCarrier.PieceFact piece : monument.orderedPieces()) {
                if (piece.kind() != Mc263HardcodedStructureCarrier.PieceKind.MONUMENT_BUILDING
                        && !intersects(piece.boundingBox(), context.clip())) {
                    continue;
                }
                Mc263OceanMonumentPieceProgram.postProcess(piece, monument.orderedRooms(),
                        monument.rotation(), clip, world, candidate);
            }

            long owner = Mc263StructureOwner.owner(STRUCTURE, persisted.startKey());
            Mc263FeaturesRegion.StructureBatch batch = world.batch(owner,
                    context.references().chunkX() == dispatcher.region().targetChunkX()
                            && context.references().chunkZ() == dispatcher.region().targetChunkZ());
            context.commitStructureBatch(predecessor, successor, batch);
        }
        advanceDispatcherRandom(dispatcher.random(), candidate.draws());
    }

    /**
     * The Ocean Monument starts this source chunk references, in reference-closure order.
     *
     * <p>{@code ChunkGenerator#applyBiomeDecoration} binds one feature seed per (chunk, structure,
     * decoration step) and then places every element of {@code
     * StructureManager#startsForStructure(SectionPos, Structure)} -- a {@code List} drained from
     * the chunk's whole {@code LongSet} of references -- through one shared {@code WorldgenRandom}.
     * Two monuments whose boxes both reach one chunk are therefore authentic, not a carrier defect.
     * Only a repeated start is rejected, because {@code ChunkStarts} holds at most one start per
     * (origin chunk, structure) and {@code ReferenceSet} forbids a duplicate origin, so a repeat
     * proves a corrupt closure rather than two monuments.</p>
     */
    private static List<Mc263StructureCarrier.ValidStart> referencedStarts(
            Mc263StructureCarrier carrier,
            Mc263StructureCarrier.ChunkReferences references) {
        List<Mc263StructureCarrier.ValidStart> starts = carrier.resolveStarts(
                references, STRUCTURE);
        if (starts.stream().map(Mc263StructureCarrier.ValidStart::startKey)
                .distinct().count() != starts.size()) {
            throw new IllegalArgumentException(
                    "minecraft:monument reference closure repeats a start");
        }
        return starts;
    }

    private void requireCapabilities() {
        if (!capabilities.exactStates || !capabilities.fluidTicks || !capabilities.entities) {
            throw new UnsupportedOperationException(
                    "minecraft:monument schema-4 block/FTIK/ENTS capability is absent");
        }
        for (String state : EXACT_STATES) {
            if (!Mc263FeatureBlockState.supportsExactState(state)) {
                throw new UnsupportedOperationException(
                        "minecraft:monument exact-state capability is absent: " + state);
            }
        }
    }

    private static void requireSchedule(Mc263StructureIndexReceipt.Entry entry,
            Mc263StructureCarrier.Registry registry) {
        Mc263StructureCarrier.StructureDefinition definition = registry.require(STRUCTURE);
        Mc263StructureIndexReceipt.Entry expected =
                Mc263StructureIndexReceipt.entries().get(REGISTRY_ORDINAL);
        if (!expected.equals(entry) || entry.step() != STEP || entry.index() != INDEX
                || !entry.key().equals(STRUCTURE) || entry.biomeMask() != BIOME_MASK
                || definition.registryOrdinal() != REGISTRY_ORDINAL
                || !definition.structureId().equals(STRUCTURE)
                || definition.decorationStep() != STEP
                || definition.terrainAdjustment()
                        != Mc263StructureCarrier.TerrainAdjustment.NONE) {
            throw new IllegalArgumentException(
                    "minecraft:monument schedule/membership/ordinal mismatch");
        }
    }

    private static void requireDispatcher(
            Mc263FeatureDispatcher.StructurePlacementContext dispatcher,
            Mc263StructureCarrier.ChunkReferences references,
            Mc263CanonicalFeaturesProducerSkeleton.SourceClip clip) {
        long expectedSeed = Mc263DecorationRandom.featureSeed(
                dispatcher.decorationSeed(), INDEX, STEP);
        if (dispatcher.step() != STEP || dispatcher.globalIndex() != INDEX
                || !dispatcher.structureKey().equals(STRUCTURE)
                || dispatcher.sourceChunkX() != references.chunkX()
                || dispatcher.sourceChunkZ() != references.chunkZ()
                || dispatcher.featureSeed() != expectedSeed) {
            throw new IllegalArgumentException(
                    "minecraft:monument dispatcher/source/RNG mismatch");
        }
        requireSourceBinding(references, clip);
    }

    private static void requireSourceBinding(
            Mc263StructureCarrier.ChunkReferences references,
            Mc263CanonicalFeaturesProducerSkeleton.SourceClip clip) {
        if (!Mc263CanonicalFeaturesProducerSkeleton.SourceClip.chunk(
                references.chunkX(), references.chunkZ()).equals(clip)) {
            throw new IllegalArgumentException("minecraft:monument source clip mismatch");
        }
    }

    private static void validateReferencedStarts(long worldSeed,
            Mc263StructureCarrier carrier,
            Mc263StructureCarrier.ChunkReferences references) {
        for (Mc263StructureCarrier.ValidStart start : referencedStarts(carrier, references)) {
            requireCanonicalStart(worldSeed, carrier, references, start);
        }
    }

    private static void requireCanonicalStart(long worldSeed,
            Mc263StructureCarrier carrier,
            Mc263StructureCarrier.ChunkReferences references,
            Mc263StructureCarrier.ValidStart start) {
        if (!start.startKey().equals(STRUCTURE + "@" + start.originChunkX()
                + "," + start.originChunkZ()) || start.references() < 0) {
            throw new IllegalArgumentException("noncanonical persisted minecraft:monument start");
        }
        Mc263HardcodedStructureCarrier expected = Mc263HardcodedStructureCarrier.plan(
                Mc263HardcodedStructureCarrier.Kind.OCEAN_MONUMENT,
                worldSeed, start.originChunkX(), start.originChunkZ());
        Mc263OceanMonumentSettlement.successorStr(carrier, references, expected);
    }

    private static Mc263OceanMonumentPieceProgram.Clip clip(
            Mc263CanonicalFeaturesProducerSkeleton.SourceClip value) {
        return new Mc263OceanMonumentPieceProgram.Clip(value.minX(), value.minY(), value.minZ(),
                value.maxX(), value.maxY(), value.maxZ());
    }

    private static boolean intersects(Mc263HardcodedStructureCarrier.BoundingBox piece,
            Mc263CanonicalFeaturesProducerSkeleton.SourceClip clip) {
        return clip.maxX() >= piece.minX() && clip.minX() <= piece.maxX()
                && clip.maxZ() >= piece.minZ() && clip.minZ() <= piece.maxZ();
    }

    private static void advanceDispatcherRandom(
            Mc263DecorationRandom.WorldgenRandom random, long draws) {
        for (long draw = 0; draw < draws; draw++) random.next(32);
    }

    private static final class Capabilities {
        private final boolean exactStates;
        private final boolean fluidTicks;
        private final boolean entities;

        private Capabilities(boolean exactStates, boolean fluidTicks, boolean entities) {
            this.exactStates = exactStates;
            this.fluidTicks = fluidTicks;
            this.entities = entities;
        }
    }

    /** Java Random-compatible 48-bit stream whose input is the accepted raw placement state. */
    private static final class LegacyPlacementRandom
            implements Mc263OceanMonumentPieceProgram.PostProcessRandom {
        private long raw;
        private long draws;

        private LegacyPlacementRandom(long rawSeed) {
            raw = rawSeed & LEGACY_MASK;
        }

        private int next(int bits) {
            raw = (raw * LEGACY_MULTIPLIER + LEGACY_INCREMENT) & LEGACY_MASK;
            draws++;
            return (int) (raw >>> (48 - bits));
        }

        @Override
        public int nextInt(int bound) {
            if (bound <= 0) throw new IllegalArgumentException("bound must be positive");
            if ((bound & -bound) == bound) {
                return (int) ((bound * (long) next(31)) >> 31);
            }
            int bits;
            int value;
            do {
                bits = next(31);
                value = bits % bound;
            } while (bits - value + bound - 1 < 0);
            return value;
        }

        @Override
        public boolean nextBoolean() {
            return next(1) != 0;
        }

        private long draws() { return draws; }

    }

    /** Private source-clip overlay; it cannot publish any lane before the producer commit. */
    private static final class SourceWorld
            implements Mc263OceanMonumentPieceProgram.WorldAccess {
        private final Mc263FeaturesRegion region;
        private final Mc263CanonicalFeaturesProducerSkeleton.SourceClip clip;
        private final Map<Mc263OceanMonumentPieceProgram.BlockPos, String> overlay =
                new LinkedHashMap<>();
        private final List<Mc263OceanMonumentPieceProgram.BlockWrite> writes = new ArrayList<>();
        private final List<Mc263OceanMonumentPieceProgram.FluidTick> fluidTicks =
                new ArrayList<>();
        private final List<Mc263OceanMonumentPieceProgram.EntityEmission> entities =
                new ArrayList<>();

        private SourceWorld(Mc263FeaturesRegion region,
                Mc263CanonicalFeaturesProducerSkeleton.SourceClip clip) {
            this.region = Objects.requireNonNull(region, "monument FEATURES region");
            this.clip = Objects.requireNonNull(clip, "monument source clip");
        }

        @Override public Set<String> capabilities() { return CAPABILITIES; }
        @Override public boolean supportsExactState(String state) {
            return Mc263FeatureBlockState.supportsExactState(state);
        }
        @Override public int seaLevel() { return 63; }
        @Override public int minY() { return Blocks.MIN_Y; }

        @Override
        public String blockState(Mc263OceanMonumentPieceProgram.BlockPos position) {
            String staged = overlay.get(position);
            return staged == null ? region.blockState(
                    position.x(), position.y(), position.z()).exactState() : staged;
        }

        @Override
        public String fluidState(Mc263OceanMonumentPieceProgram.BlockPos position) {
            String state = overlay.get(position);
            Mc263FeatureBlockState value = state == null
                    ? region.blockState(position.x(), position.y(), position.z())
                    : Mc263FeatureBlockState.fromExact(state);
            return (value.fluidKind() == Mc263FeatureBlockState.FluidKind.WATER_SOURCE
                    || value.fluidKind() == Mc263FeatureBlockState.FluidKind.WATER_FLOWING)
                    ? "minecraft:water[falling=false]" : "minecraft:empty";
        }

        @Override
        public boolean isReplaceableByStructures(
                Mc263OceanMonumentPieceProgram.BlockPos position, String state) {
            String key = blockKey(state);
            return key.equals("minecraft:air") || key.equals("minecraft:cave_air")
                    || key.equals("minecraft:void_air") || key.equals("minecraft:water");
        }

        @Override
        public boolean predictsSetBlockResult(
                Mc263OceanMonumentPieceProgram.BlockPos position, String state, int flags) {
            return inside(position, clip) && flags == 2
                    && Mc263FeatureBlockState.supportsExactState(state);
        }

        @Override
        public boolean setBlock(Mc263OceanMonumentPieceProgram.BlockPos position,
                String state, int flags) {
            if (state.equals(overlay.get(position))
                    || !predictsSetBlockResult(position, state, flags)) return false;
            writes.add(new Mc263OceanMonumentPieceProgram.BlockWrite(position, state, flags));
            overlay.put(position, state);
            return true;
        }

        @Override
        public void scheduleFluidTick(Mc263OceanMonumentPieceProgram.BlockPos position,
                String fluidKey, int delay) {
            fluidTicks.add(new Mc263OceanMonumentPieceProgram.FluidTick(
                    position, fluidKey, delay, "NORMAL", fluidTicks.size()));
        }

        @Override
        public void emitStructureMob(String entityType, String spawnReason,
                double x, double y, double z, float yaw, float pitch) {
            entities.add(new Mc263OceanMonumentPieceProgram.EntityEmission(entityType,
                    spawnReason, x, y, z, yaw, pitch, ELDER_PAYLOAD));
        }

        private Mc263FeaturesRegion.StructureBatch batch(long owner, boolean includeEntities) {
            List<Mc263FeaturesRegion.StructureBlockWrite> blockBatch = writes.stream()
                    .map(value -> new Mc263FeaturesRegion.StructureBlockWrite(
                            value.position().x(), value.position().y(), value.position().z(),
                            value.exactState(), owner)).toList();
            List<Mc263FeaturesRegion.StructureFluidTick> tickBatch = fluidTicks.stream()
                    .map(value -> new Mc263FeaturesRegion.StructureFluidTick(
                            value.position().x(), value.position().y(), value.position().z(),
                            value.fluidKey(), value.delay(), 0, value.subTickOrder())).toList();
            List<Mc263FinalChunkSidecars.StructureEntity> entityBatch = includeEntities
                    ? entities.stream().map(SourceWorld::entity).toList() : List.of();
            return new Mc263FeaturesRegion.StructureBatch(blockBatch, List.of(), List.of(),
                    List.of(), entityBatch, tickBatch, List.of(), List.of());
        }

        private static Mc263FinalChunkSidecars.StructureEntity entity(
                Mc263OceanMonumentPieceProgram.EntityEmission value) {
            return new Mc263FinalChunkSidecars.StructureEntity(value.entityType(),
                    value.spawnReason(), value.x(), value.y(), value.z(), value.yaw(),
                    value.pitch(), 0.0, 0.0, 0.0, value.canonicalPayload());
        }

        private static boolean inside(Mc263OceanMonumentPieceProgram.BlockPos position,
                Mc263CanonicalFeaturesProducerSkeleton.SourceClip clip) {
            return position.x() >= clip.minX() && position.x() <= clip.maxX()
                    && position.y() >= clip.minY() && position.y() <= clip.maxY()
                    && position.z() >= clip.minZ() && position.z() <= clip.maxZ();
        }

        private static String blockKey(String state) {
            int property = state.indexOf('[');
            return property < 0 ? state : state.substring(0, property);
        }
    }
}
