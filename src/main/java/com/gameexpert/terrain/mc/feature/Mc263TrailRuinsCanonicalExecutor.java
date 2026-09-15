package com.gameexpert.terrain.mc.feature;

import com.gameexpert.terrain.mc.ore.Mc263DecorationRandom;
import com.gameexpert.terrain.mc.structure.Mc263StructureCarrier;
import com.gameexpert.terrain.mc.structure.Mc263TrailRuinsCarrier;
import com.gameexpert.terrain.mc.structure.Mc263TrailRuinsCatalog;
import com.gameexpert.terrain.mc.structure.Mc263TrailRuinsProducer;
import com.gameexpert.terrain.mc.structure.Mc263TrailRuinsSettlement;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/** Production adapter for the accepted exact pinned Trail Ruins producer and settlement. */
final class Mc263TrailRuinsCanonicalExecutor implements
        Mc263CanonicalFeaturesProducerSkeleton.CanonicalStructureExecutor {
    private static final String STRUCTURE = Mc263TrailRuinsCatalog.STRUCTURE_KEY;
    private static final String PIECE_TYPE = "minecraft:jigsaw";
    private static final int STEP = 3;
    private static final int INDEX = 3;
    private static final int REGISTRY_ORDINAL = 3;
    private static final long BIOME_MASK = 0x00280204280000L;
    private static final int BURY_INFLATION = 12;
    private static final byte TAG_BYTE = 1;
    private static final byte[] PLACED_NAME = "placed".getBytes(StandardCharsets.UTF_8);
    private static final String ACCEPTED_CATALOG =
            "650cb33622255e0e7fa71b6732c93255c1e82ee021c4a690c779f9d32c843042";

    private final Mc263TrailRuinsCatalog catalog;
    private final Mc263TrailRuinsProducer producer;

    private Mc263TrailRuinsCanonicalExecutor() {
        catalog = Mc263TrailRuinsCatalog.pinned();
        producer = new Mc263TrailRuinsProducer(catalog);
    }

    static void register(
            Mc263CanonicalFeaturesProducerSkeleton.CanonicalStructureExecutorRegistry registry) {
        Objects.requireNonNull(registry, "canonical structure executor registry")
                .register(STRUCTURE, new Mc263TrailRuinsCanonicalExecutor());
    }

    /** Exact initial STR start helper for later structure-start wiring. */
    static Mc263StructureCarrier.ValidStart createValidStart(long worldSeed, int chunkX,
            int chunkZ, int references, int worldSurfaceHeight) {
        Mc263TrailRuinsProducer.Start start = Mc263TrailRuinsProducer.pinned().generate(
                worldSeed, chunkX, chunkZ, constantHeight(worldSurfaceHeight));
        if (start.empty()) {
            throw new IllegalArgumentException("Trail Ruins start is outside build bounds");
        }
        return validStart(start, references, false);
    }

    @Override
    public void preflight(
            Mc263CanonicalFeaturesProducerSkeleton.StructurePreflightContext context) {
        requireProductionCapabilitiesOnce();
        Objects.requireNonNull(context, "Trail Ruins preflight context");
        requireAcceptedIdentity();
        requireSchedule(context.entry(), context.carrier().registry());
        requireSourceBinding(context.references(), context.clip());
        validatePersistedStarts(context.worldSeed(), context.carrier());
        for (Mc263StructureCarrier.ValidStart start : context.carrier().resolveStarts(
                context.references(), STRUCTURE)) {
            loadPersisted(context.worldSeed(), start);
        }
    }

    @Override
    public void place(Mc263CanonicalFeaturesProducerSkeleton.StructurePlacementInput context) {
        requireProductionCapabilitiesOnce();
        Objects.requireNonNull(context, "Trail Ruins placement context");
        Mc263FeatureDispatcher.StructurePlacementContext dispatcher = context.dispatcher();
        requireDispatcher(dispatcher, context.references());
        requireSourceBinding(context.references(), context.clip());
        requireSchedule(Mc263StructureIndexReceipt.step(STEP).get(INDEX),
                context.carrier().registry());
        long expectedFeatureSeed = Mc263DecorationRandom.featureSeed(
                dispatcher.decorationSeed(), INDEX, STEP);
        if (dispatcher.featureSeed() != expectedFeatureSeed) {
            throw new IllegalArgumentException("Trail Ruins featureSeed mismatch");
        }
        validatePersistedStarts(dispatcher.worldSeed(), context.carrier());

        List<String> startKeys = context.carrier().resolveStarts(
                context.references(), STRUCTURE).stream()
                .map(Mc263StructureCarrier.ValidStart::startKey).toList();
        for (String startKey : startKeys) {
            Mc263StructureCarrier predecessor = context.carrier();
            Mc263StructureCarrier.ChunkReferences references = context.references();
            Mc263StructureCarrier.ValidStart persisted = predecessor.resolveStarts(
                    references, STRUCTURE).stream()
                    .filter(value -> value.startKey().equals(startKey)).findFirst()
                    .orElseThrow(() -> new IllegalStateException(
                            "evolved Trail Ruins carrier lost referenced start"));
            LoadedStart loaded = loadPersisted(dispatcher.worldSeed(), persisted);
            if (loaded.replay()) continue;

            CapturingWorld world = new CapturingWorld(dispatcher.region());
            Mc263TrailRuinsSettlement.Settlement settlement =
                    Mc263TrailRuinsSettlement.settle(loaded.start(), catalog,
                            settlementClip(context.clip()), world);
            if (world.settlement != settlement) {
                throw new IllegalStateException(
                        "Trail Ruins settlement was not captured atomically");
            }
            Mc263StructureCarrier successor = successor(
                    predecessor, persisted.startKey(), loaded.start(), persisted.references());
            context.commitStructureBatch(predecessor, successor,
                    batch(settlement, persisted.startKey()));
        }
    }

    /**
     * The capability closure of the pinned catalog, checked once per class loader.
     *
     * <p>AGENTS rule 10l: {@link #requireProductionCapabilities()} walks every template of the
     * pinned catalog in all four rotations and asks the exact-state registry about every state
     * and command it names. That is a constant of the pinned catalog, not of the chunk, yet the
     * FEATURES seam reaches preflight once per source chunk (25 per target) and place once per
     * dispatch. The holder runs the identical check exactly once and every later demand is a
     * field read; the raw method stays available so a capability regression still fails closed
     * for callers that want it re-run.</p>
     */
    private static final class CapabilityClosureHolder {
        private static final boolean CLOSED = close();

        private static boolean close() {
            requireProductionCapabilities();
            return true;
        }

        private CapabilityClosureHolder() { }
    }

    private static void requireProductionCapabilitiesOnce() {
        if (!CapabilityClosureHolder.CLOSED) {
            throw new IllegalStateException("Trail Ruins capability closure did not close");
        }
    }

    static void requireProductionCapabilities() {
        Mc263TrailRuinsCatalog catalog = Mc263TrailRuinsCatalog.pinned();
        for (Mc263TrailRuinsCatalog.Template template : catalog.pools().stream()
                .flatMap(pool -> pool.elements().stream())
                .map(element -> catalog.template(element.template())).distinct().toList()) {
            for (Mc263TrailRuinsCatalog.Rotation rotation
                    : Mc263TrailRuinsCatalog.Rotation.values()) {
                for (String state : template.states()) {
                    if (!state.startsWith("minecraft:jigsaw")) {
                        requireStateAndWaterlogged(rotateState(state, rotation));
                    }
                }
                for (Mc263TrailRuinsCatalog.Command command : template.commands()) {
                    if (command.kind() == Mc263TrailRuinsCatalog.CommandKind.JIGSAW) {
                        requireStateAndWaterlogged(rotateState(command.finalState(), rotation));
                    } else if (command.kind()
                            == Mc263TrailRuinsCatalog.CommandKind.EMPTY_BLOCK_ENTITY) {
                        requireBlockEntity(blockEntityId(command.blockEntityType()));
                    }
                }
            }
        }
        for (String processorState : List.of("minecraft:dirt", "minecraft:coarse_dirt",
                "minecraft:packed_mud", "minecraft:suspicious_gravel[dusted=0]")) {
            requireStateAndWaterlogged(processorState);
        }
        requireBlockEntity("minecraft:brushable_block");
        requireLootTable(Mc263TrailRuinsCatalog.COMMON_ARCH);
        requireLootTable(Mc263TrailRuinsCatalog.RARE_ARCH);
    }

    private static void requireStateAndWaterlogged(String state) {
        if (!Mc263FeatureBlockState.supportsExactState(state)) {
            throw new IllegalStateException("missing Trail Ruins exact-state capability: "
                    + state);
        }
        if (state.contains("waterlogged=false")) {
            String waterlogged = state.replace("waterlogged=false", "waterlogged=true");
            if (!Mc263FeatureBlockState.supportsExactState(waterlogged)) {
                throw new IllegalStateException(
                        "missing Trail Ruins waterlogged-state capability: " + waterlogged);
            }
        }
    }

    private static void requireBlockEntity(String type) {
        if (!List.of("minecraft:brushable_block", "minecraft:campfire", "minecraft:furnace",
                "minecraft:blast_furnace", "minecraft:chest").contains(type)) {
            throw new IllegalStateException("missing Trail Ruins BENT capability: " + type);
        }
    }

    private static void requireLootTable(String table) {
        if (!(Mc263TrailRuinsCatalog.COMMON_ARCH.equals(table)
                || Mc263TrailRuinsCatalog.RARE_ARCH.equals(table))) {
            throw new IllegalStateException("missing Trail Ruins ARCH capability: " + table);
        }
    }

    private static void requireAcceptedIdentity() {
        if (!ACCEPTED_CATALOG.equals(Mc263TrailRuinsCatalog.CATALOG_SHA256)) {
            throw new IllegalStateException("Trail Ruins accepted catalog identity drift");
        }
    }

    private static void requireSchedule(Mc263StructureIndexReceipt.Entry entry,
            Mc263StructureCarrier.Registry registry) {
        Mc263StructureCarrier.StructureDefinition definition = registry.require(STRUCTURE);
        if (entry.step() != STEP || entry.index() != INDEX || !entry.key().equals(STRUCTURE)
                || entry.biomeMask() != BIOME_MASK
                || definition.registryOrdinal() != REGISTRY_ORDINAL
                || !definition.structureId().equals(STRUCTURE)
                || definition.decorationStep() != STEP
                || definition.terrainAdjustment()
                        != Mc263StructureCarrier.TerrainAdjustment.BURY
                || !Mc263StructureIndexReceipt.entries().get(REGISTRY_ORDINAL).equals(entry)) {
            throw new IllegalArgumentException(
                    "Trail Ruins schedule/membership/ordinal mismatch");
        }
    }

    private static void requireDispatcher(
            Mc263FeatureDispatcher.StructurePlacementContext dispatcher,
            Mc263StructureCarrier.ChunkReferences references) {
        if (dispatcher.step() != STEP || dispatcher.globalIndex() != INDEX
                || !dispatcher.structureKey().equals(STRUCTURE)
                || dispatcher.sourceChunkX() != references.chunkX()
                || dispatcher.sourceChunkZ() != references.chunkZ()) {
            throw new IllegalArgumentException("Trail Ruins dispatcher/source mismatch");
        }
    }

    private static void requireSourceBinding(Mc263StructureCarrier.ChunkReferences references,
            Mc263CanonicalFeaturesProducerSkeleton.SourceClip clip) {
        if (!Mc263CanonicalFeaturesProducerSkeleton.SourceClip.chunk(
                references.chunkX(), references.chunkZ()).equals(clip)) {
            throw new IllegalArgumentException("Trail Ruins source clip mismatch");
        }
    }

    private void validatePersistedStarts(long worldSeed, Mc263StructureCarrier carrier) {
        for (Mc263StructureCarrier.ChunkStarts chunk : carrier.startChunks()) {
            for (Mc263StructureCarrier.StartEntry entry : chunk.orderedStarts()) {
                if (entry.structureId().equals(STRUCTURE)
                        && entry.body() instanceof Mc263StructureCarrier.ValidStart valid) {
                    loadPersisted(worldSeed, valid);
                }
            }
        }
    }

    private LoadedStart loadPersisted(long worldSeed,
            Mc263StructureCarrier.ValidStart persisted) {
        if (persisted.references() < 0 || persisted.orderedPieces().isEmpty()
                || !persisted.startKey().equals(STRUCTURE + "@"
                        + persisted.originChunkX() + "," + persisted.originChunkZ())) {
            throw new IllegalArgumentException("noncanonical persisted Trail Ruins start");
        }
        Mc263StructureCarrier.Piece first = persisted.orderedPieces().getFirst();
        int height = Math.addExact(first.boundingBox().minY(),
                -Mc263TrailRuinsCatalog.START_HEIGHT + 1);
        Mc263TrailRuinsProducer.Start expected = producer.generate(worldSeed,
                persisted.originChunkX(), persisted.originChunkZ(), constantHeight(height));
        if (expected.empty() || expected.pieces().size() != persisted.orderedPieces().size()) {
            throw new IllegalArgumentException("persisted Trail Ruins graph cardinality mismatch");
        }
        Mc263StructureCarrier.ValidStart initial = validStart(
                expected, persisted.references(), false);
        Mc263StructureCarrier.ValidStart replay = validStart(
                expected, persisted.references(), true);
        boolean initialMatch = sameStartFacts(persisted, initial);
        boolean replayMatch = sameStartFacts(persisted, replay);
        if (!initialMatch && !replayMatch) {
            throw new IllegalArgumentException("persisted Trail Ruins graph/payload mismatch");
        }
        return new LoadedStart(expected, replayMatch);
    }

    private static boolean sameStartFacts(Mc263StructureCarrier.ValidStart actual,
            Mc263StructureCarrier.ValidStart expected) {
        if (!actual.startKey().equals(expected.startKey())
                || actual.originChunkX() != expected.originChunkX()
                || actual.originChunkZ() != expected.originChunkZ()
                || actual.references() != expected.references()
                || !actual.adjustedBoundingBox().equals(expected.adjustedBoundingBox())
                || actual.orderedPieces().size() != expected.orderedPieces().size()) {
            return false;
        }
        for (int index = 0; index < actual.orderedPieces().size(); index++) {
            Mc263StructureCarrier.Piece left = actual.orderedPieces().get(index);
            Mc263StructureCarrier.Piece right = expected.orderedPieces().get(index);
            if (!left.pieceType().equals(right.pieceType())
                    || !left.boundingBox().equals(right.boundingBox())
                    || left.poolElement() != right.poolElement()
                    || left.projection() != right.projection()
                    || left.groundLevelDelta() != right.groundLevelDelta()
                    || !left.junctions().equals(right.junctions())
                    || !Arrays.equals(left.persistedPayload().binaryNbtCompound(),
                            right.persistedPayload().binaryNbtCompound())) {
                return false;
            }
        }
        return true;
    }

    private static Mc263StructureCarrier.ValidStart validStart(
            Mc263TrailRuinsProducer.Start start, int references, boolean placed) {
        ArrayList<Mc263StructureCarrier.Piece> pieces = new ArrayList<>();
        for (Mc263TrailRuinsProducer.Piece piece : start.pieces()) {
            byte[] payload = Mc263TrailRuinsCarrier.rawPieceNbt(piece);
            if (placed) payload = markPlaced(payload);
            List<Mc263StructureCarrier.Junction> junctions = piece.junctions().stream()
                    .map(value -> new Mc263StructureCarrier.Junction(value.sourceX(),
                            value.sourceGroundY(), value.sourceZ(), value.deltaY(),
                            Mc263StructureCarrier.Projection.RIGID))
                    .toList();
            pieces.add(new Mc263StructureCarrier.Piece(PIECE_TYPE, box(piece.box()), true,
                    Mc263StructureCarrier.Projection.RIGID, piece.groundLevelDelta(),
                    junctions, new Mc263StructureCarrier.PiecePayload(payload)));
        }
        Mc263StructureCarrier.BoundingBox raw = box(start.aggregateBox());
        Mc263StructureCarrier.BoundingBox adjusted = new Mc263StructureCarrier.BoundingBox(
                Math.subtractExact(raw.minX(), BURY_INFLATION),
                Math.subtractExact(raw.minY(), BURY_INFLATION),
                Math.subtractExact(raw.minZ(), BURY_INFLATION),
                Math.addExact(raw.maxX(), BURY_INFLATION),
                Math.addExact(raw.maxY(), BURY_INFLATION),
                Math.addExact(raw.maxZ(), BURY_INFLATION));
        return new Mc263StructureCarrier.ValidStart(STRUCTURE + "@" + start.chunkX() + ","
                + start.chunkZ(), start.chunkX(), start.chunkZ(), references, adjusted, pieces);
    }

    private static Mc263StructureCarrier successor(Mc263StructureCarrier predecessor,
            String startKey, Mc263TrailRuinsProducer.Start start, int references) {
        Mc263StructureCarrier.ValidStart replacement = validStart(start, references, true);
        ArrayList<Mc263StructureCarrier.ChunkStarts> chunks = new ArrayList<>();
        boolean replaced = false;
        for (Mc263StructureCarrier.ChunkStarts chunk : predecessor.startChunks()) {
            ArrayList<Mc263StructureCarrier.StartEntry> entries = new ArrayList<>();
            for (Mc263StructureCarrier.StartEntry entry : chunk.orderedStarts()) {
                if (entry.body() instanceof Mc263StructureCarrier.ValidStart valid
                        && valid.startKey().equals(startKey)) {
                    entries.add(new Mc263StructureCarrier.StartEntry(STRUCTURE, replacement));
                    replaced = true;
                } else {
                    entries.add(entry);
                }
            }
            chunks.add(chunk.withStarts(entries));
        }
        if (!replaced) throw new IllegalArgumentException("missing Trail Ruins STR predecessor");
        Mc263StructureCarrier successor = new Mc263StructureCarrier(predecessor.registry(), chunks,
                predecessor.referenceChunks(), predecessor.rawStartPayloads(),
                predecessor.producerGraphPayloads());
        return successor.strictlyDecoded();
    }

    private static Mc263FeaturesRegion.StructureBatch batch(
            Mc263TrailRuinsSettlement.Settlement settlement, String startKey) {
        long owner = Mc263StructureOwner.owner(STRUCTURE, startKey);
        List<Mc263FeaturesRegion.StructureBlockWrite> blocks = settlement.writes().stream()
                .map(value -> new Mc263FeaturesRegion.StructureBlockWrite(
                        value.position().x(), value.position().y(), value.position().z(),
                        Mc263FeatureBlockState.canonicalExactState(value.state()), owner)).toList();
        List<Mc263FeaturesRegion.StructureArchaeology> archaeology = settlement.arch().stream()
                .map(value -> new Mc263FeaturesRegion.StructureArchaeology(
                        value.position().x(), value.position().y(), value.position().z(),
                        value.table(), value.signedLootSeed())).toList();
        List<Mc263FeaturesRegion.StructureBentEvidence> bent = settlement.bent().stream()
                .map(value -> new Mc263FeaturesRegion.StructureBentEvidence(
                        value.position().x(), value.position().y(), value.position().z(),
                        blockKey(value.state()), value.type(), value.rawNbt())).toList();
        return new Mc263FeaturesRegion.StructureBatch(blocks, List.of(), archaeology, bent);
    }

    private static Mc263TrailRuinsSettlement.Clip settlementClip(
            Mc263CanonicalFeaturesProducerSkeleton.SourceClip clip) {
        return new Mc263TrailRuinsSettlement.Clip(clip.minX(), clip.minY(), clip.minZ(),
                clip.maxX(), clip.maxY(), clip.maxZ());
    }

    private static Mc263StructureCarrier.BoundingBox box(Mc263TrailRuinsProducer.Box value) {
        return new Mc263StructureCarrier.BoundingBox(value.minX(), value.minY(), value.minZ(),
                value.maxX(), value.maxY(), value.maxZ());
    }

    private static byte[] markPlaced(byte[] rawNbt) {
        if (rawNbt.length < 4 || rawNbt[0] != 10 || rawNbt[rawNbt.length - 1] != 0) {
            throw new IllegalArgumentException("noncanonical Trail Ruins piece NBT");
        }
        byte[] result = Arrays.copyOf(rawNbt, rawNbt.length + PLACED_NAME.length + 4);
        int offset = rawNbt.length - 1;
        result[offset++] = TAG_BYTE;
        result[offset++] = 0;
        result[offset++] = (byte) PLACED_NAME.length;
        System.arraycopy(PLACED_NAME, 0, result, offset, PLACED_NAME.length);
        offset += PLACED_NAME.length;
        result[offset++] = 1;
        result[offset] = 0;
        return result;
    }

    private static String blockKey(String state) {
        int properties = state.indexOf('[');
        return properties < 0 ? state : state.substring(0, properties);
    }

    private static String blockEntityId(String grammarType) {
        int variant = grammarType.indexOf(':', "minecraft:".length());
        return variant < 0 ? grammarType : grammarType.substring(0, variant);
    }

    private static String rotateState(String state,
            Mc263TrailRuinsCatalog.Rotation rotation) {
        int turns = switch (rotation) {
            case NONE -> 0;
            case CLOCKWISE_90 -> 1;
            case CLOCKWISE_180 -> 2;
            case COUNTERCLOCKWISE_90 -> 3;
        };
        String result = state;
        for (int turn = 0; turn < turns; turn++) {
            result = result.replace("facing=north", "facing=#east")
                    .replace("facing=east", "facing=#south")
                    .replace("facing=south", "facing=#west")
                    .replace("facing=west", "facing=#north")
                    .replace("facing=#", "facing=");
            result = result.replace("axis=x", "axis=#z").replace("axis=z", "axis=#x")
                    .replace("axis=#", "axis=");
        }
        return result;
    }

    private static Mc263TrailRuinsProducer.HeightAccess constantHeight(int height) {
        return new Mc263TrailRuinsProducer.HeightAccess() {
            @Override public boolean supportsWorldSurfaceHeight() { return true; }
            @Override public int worldSurfaceHeight(int blockX, int blockZ) { return height; }
        };
    }

    private record LoadedStart(Mc263TrailRuinsProducer.Start start, boolean replay) { }

    private static final class CapturingWorld
            implements Mc263TrailRuinsSettlement.WorldAccess {
        private final Mc263FeaturesRegion region;
        private Mc263TrailRuinsSettlement.Settlement settlement;

        private CapturingWorld(Mc263FeaturesRegion region) {
            this.region = Objects.requireNonNull(region, "Trail Ruins FEATURES region");
        }

        @Override public boolean supportsAtomicSettlement() { return true; }
        @Override public boolean supportsArchBent() { return true; }
        @Override public boolean supportsState(String state) {
            return Mc263FeatureBlockState.supportsExactState(state);
        }
        @Override public boolean supportsBlockEntity(String type) {
            return List.of("minecraft:brushable_block", "minecraft:campfire",
                    "minecraft:furnace", "minecraft:blast_furnace", "minecraft:chest")
                    .contains(type);
        }
        @Override public boolean supportsLootTable(String table) {
            return Mc263TrailRuinsCatalog.COMMON_ARCH.equals(table)
                    || Mc263TrailRuinsCatalog.RARE_ARCH.equals(table);
        }
        @Override public String blockState(Mc263TrailRuinsCatalog.Vec position) {
            return region.blockState(position.x(), position.y(), position.z()).exactState();
        }
        @Override public boolean hasWater(Mc263TrailRuinsCatalog.Vec position) {
            return region.blockState(position.x(), position.y(), position.z())
                    .fluidTypeKey().contains("water");
        }
        @Override public void commit(Mc263TrailRuinsSettlement.Settlement value) {
            if (settlement != null) {
                throw new IllegalStateException("duplicate Trail Ruins settlement");
            }
            settlement = Objects.requireNonNull(value, "Trail Ruins settlement");
        }
    }
}
