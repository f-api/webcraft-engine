package com.gameexpert.terrain.mc.feature;

import com.gameexpert.terrain.Blocks;
import com.gameexpert.terrain.mc.structure.Mc263JigsawStructureBoundary.Bounds;
import com.gameexpert.terrain.mc.structure.Mc263JigsawStructureExecutor.PiecePlacement;
import com.gameexpert.terrain.mc.structure.Mc263StructureCarrier;
import com.gameexpert.terrain.mc.structure.Mc263TemplatePlacementExecutor;
import com.gameexpert.terrain.mc.structure.Mc263TrialChambersProducer;
import com.gameexpert.terrain.mc.structure.Mc263TrialChambersProductionTransaction;
import com.gameexpert.terrain.mc.structure.Mc263TrialChambersSettlement;
import com.gameexpert.terrain.persistence.CanonicalWorldgenStore;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Dormant production adapter for the accepted exact Trial Chambers producer/settlement seam. */
final class Mc263TrialChambersCanonicalExecutor implements
        Mc263CanonicalFeaturesProducerSkeleton.CanonicalStructureExecutor {
    private static final String STRUCTURE = Mc263TrialChambersProducer.STRUCTURE_KEY;
    private static final String PIECE_TYPE = "minecraft:jigsaw";
    private static final int STEP = 3;
    private static final int INDEX = 4;
    private static final int REGISTRY_ORDINAL = 4;
    private static final long BIOME_MASK = 0x7fffffffffffffL;

    /**
     * Lazily bound producer. Registry construction happens on the first world join, so binding the
     * pinned producer (and through it the distilled Trial Chambers production authority) in the
     * constructor would parse the whole grammar before any Trial start is known to intersect the
     * generated region. The holder defers that work to the first Trial placement/preflight.
     */
    private static final class ProducerHolder {
        private static final Mc263TrialChambersProducer PINNED = bind();

        private static Mc263TrialChambersProducer bind() {
            Mc263TrialChambersSettlement.evidenceIdentity();
            return Mc263TrialChambersProducer.pinned();
        }
    }

    /**
     * Bounded memo of the pinned producer's start for one origin. The FEATURES seam validates the
     * persisted starts of a carrier once per source chunk (25 per target) and again on every
     * placement dispatch, and {@link Mc263TrialChambersProducer#generate} is a pure function of
     * the world seed and the origin chunk under this executor's fixed boundary world and
     * accepting publisher, so every repeat after the first is byte-neutral work. The bound covers
     * the origins one activation wall can reach: Trial starts are 34 chunks apart, so a 24-chunk
     * course plus its 10-chunk halo touches a handful, and 64 leaves the whole wall resident.
     */
    private static final int START_MEMO_BOUND = 64;

    private final Mc263CanonicalStartMemo<StartKey, Mc263TrialChambersProducer.Start> starts =
            new Mc263CanonicalStartMemo<>(START_MEMO_BOUND);

    /** Every input {@link Mc263TrialChambersProducer#generate} reads for this executor's calls. */
    private record StartKey(long worldSeed, int originChunkX, int originChunkZ) { }

    private Mc263TrialChambersCanonicalExecutor() { }

    private static Mc263TrialChambersProducer producer() {
        return ProducerHolder.PINNED;
    }

    /** The pinned producer's start for one origin, generated once per world seed and origin. */
    private Mc263TrialChambersProducer.Start generatedStart(long worldSeed, int originChunkX,
            int originChunkZ) {
        return starts.resolve(new StartKey(worldSeed, originChunkX, originChunkZ),
                () -> producer().generate(worldSeed, originChunkX, originChunkZ,
                        new BoundaryWorld(), new AcceptingPublisher()));
    }

    static void register(
            Mc263CanonicalFeaturesProducerSkeleton.CanonicalStructureExecutorRegistry registry) {
        Objects.requireNonNull(registry, "canonical structure executor registry")
                .register(STRUCTURE, new Mc263TrialChambersCanonicalExecutor());
    }

    static Mc263StructureCarrier.ValidStart createValidStart(long worldSeed, int chunkX,
            int chunkZ, int references) {
        Mc263TrialChambersProducer.Start start = Mc263TrialChambersProducer.pinned().generate(
                worldSeed, chunkX, chunkZ, new BoundaryWorld(), new AcceptingPublisher());
        return validStart(start, references);
    }

    @Override
    public void preflight(
            Mc263CanonicalFeaturesProducerSkeleton.StructurePreflightContext context) {
        Objects.requireNonNull(context, "Trial preflight context");
        requireSchedule(context.entry(), context.carrier().registry());
        requireSourceBinding(context.references(), context.clip());
        validatePersistedStarts(context.worldSeed(), context.carrier());
        for (Mc263StructureCarrier.ValidStart start
                : referencedStarts(context.carrier(), context.references())) {
            Mc263TrialChambersProducer.Start generated = requirePersistedIdentity(
                    context.worldSeed(), start);
            if (intersectsAnyPiece(generated, context.clip())) {
                productionPreflight(generated, context.references());
            }
        }
    }

    @Override
    public void place(Mc263CanonicalFeaturesProducerSkeleton.StructurePlacementInput context) {
        Objects.requireNonNull(context, "Trial placement context");
        Mc263FeatureDispatcher.StructurePlacementContext dispatcher = context.dispatcher();
        requireDispatcher(dispatcher, context.references());
        requireSourceBinding(context.references(), context.clip());
        requireSchedule(Mc263StructureIndexReceipt.step(STEP).get(INDEX),
                context.carrier().registry());
        validatePersistedStarts(dispatcher.worldSeed(), context.carrier());

        List<String> startKeys = referencedStarts(context.carrier(), context.references()).stream()
                .map(Mc263StructureCarrier.ValidStart::startKey).toList();
        for (String startKey : startKeys) {
            Mc263StructureCarrier predecessor = context.carrier();
            Mc263StructureCarrier.ValidStart persisted = referencedStarts(
                    predecessor, context.references()).stream()
                    .filter(value -> value.startKey().equals(startKey)).findFirst()
                    .orElseThrow(() -> new IllegalStateException(
                            "evolved Trial carrier lost referenced start"));
            Mc263TrialChambersProducer.Start generated = requirePersistedIdentity(
                    dispatcher.worldSeed(), persisted);
            if (persisted.references() == 1) {
                continue;
            }
            Mc263StructureCarrier successor = successor(predecessor, persisted.startKey());
            if (!intersectsAnyPiece(generated, context.clip())) {
                context.commitStructureBatch(predecessor, successor, emptyBatch());
                continue;
            }

            TransactionWorld world = TransactionWorld.live(dispatcher.region());
            Mc263TrialChambersProductionTransaction.Execution execution =
                    Mc263TrialChambersProductionTransaction.execute(generated,
                            placementClip(context.references()), world);
            long owner = Mc263StructureOwner.owner(STRUCTURE, persisted.startKey());
            context.commitStructureBatch(predecessor, successor,
                    batch(execution.result(), owner, context));
        }
    }

    /**
     * Dormant durable boundary used once the global 52/52 producer is activated. The complete
     * canonical product already owns schema-4 lanes/fingerprint; Trial contributes the distinct
     * raw mutable start successor instead of aliasing STR bytes into that persistence column.
     */
    static CanonicalWorldgenStore.CanonicalChunkSnapshot commitCanonicalProduct(
            CanonicalWorldgenStore store, long worldId, String worldIdentity, long worldSeed,
            Mc263CanonicalGenerationProduct product) {
        Objects.requireNonNull(store, "canonical worldgen store");
        Objects.requireNonNull(product, "canonical generation product");
        Mc263StructureCarrier carrier = product.structureCarrier();
        int chunkX = product.finalChunk().chunkX();
        int chunkZ = product.finalChunk().chunkZ();
        Mc263StructureCarrier.ChunkReferences references = carrier.referenceChunk(chunkX, chunkZ)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Trial canonical product is missing target STR references"));
        List<Mc263StructureCarrier.ValidStart> starts = referencedStarts(carrier, references);
        if (starts.isEmpty()) {
            throw new IllegalArgumentException(
                    "Trial canonical product must resolve a target start");
        }
        // Every referenced start is validated, because a chunk between two Trial boxes references
        // both. The durable mutable-successor column names one start; it is redundant evidence
        // beside the STR receipt column, which already carries every referenced start's successor,
        // so it is bound to the closure's first member in reference order.
        byte[] mutableSuccessor = null;
        for (Mc263StructureCarrier.ValidStart start : starts) {
            Mc263TrialChambersProducer.Start generated = Mc263TrialChambersProducer.pinned()
                    .generate(worldSeed, start.originChunkX(), start.originChunkZ(),
                            new BoundaryWorld(), new AcceptingPublisher());
            requireSameStart(start, validStart(generated, start.references()));
            if (mutableSuccessor == null) {
                mutableSuccessor = referenceSuccessor(generated.carrier().structureStart().bytes());
            }
        }
        return store.commit(new CanonicalWorldgenStore.ChunkCommit(worldId, worldIdentity,
                chunkX, chunkZ, product.finalCarrier(), product.structureCarrier().receiptBytes(),
                mutableSuccessor, product.commitFingerprint()));
    }

    /**
     * The Trial Chambers starts this source chunk references, in reference-closure order.
     *
     * <p>{@code ChunkGenerator#applyBiomeDecoration} binds one feature seed per (chunk, structure,
     * decoration step) and then places every element of {@code
     * StructureManager#startsForStructure(SectionPos, Structure)} -- a {@code List} drained from
     * the chunk's whole {@code LongSet} of references -- through one shared {@code WorldgenRandom}.
     * Two chambers whose boxes both reach one chunk are therefore authentic, not a carrier defect.
     * Only a repeated start is rejected, because {@code ChunkStarts} holds at most one start per
     * (origin chunk, structure) and {@code ReferenceSet} forbids a duplicate origin, so a repeat
     * proves a corrupt closure rather than two chambers.</p>
     */
    private static List<Mc263StructureCarrier.ValidStart> referencedStarts(
            Mc263StructureCarrier carrier,
            Mc263StructureCarrier.ChunkReferences references) {
        List<Mc263StructureCarrier.ValidStart> starts = carrier.resolveStarts(references, STRUCTURE);
        if (starts.stream().map(Mc263StructureCarrier.ValidStart::startKey)
                .distinct().count() != starts.size()) {
            throw new IllegalArgumentException("Trial reference closure repeats a start");
        }
        return starts;
    }

    private Mc263TrialChambersProducer.Start requirePersistedIdentity(long worldSeed,
            Mc263StructureCarrier.ValidStart persisted) {
        if (persisted.references() < 0 || persisted.references() > 1
                || !persisted.startKey().equals(STRUCTURE + "@" + persisted.originChunkX()
                        + "," + persisted.originChunkZ())) {
            throw new IllegalArgumentException("noncanonical persisted Trial start");
        }
        Mc263TrialChambersProducer.Start expected = generatedStart(worldSeed,
                persisted.originChunkX(), persisted.originChunkZ());
        requireSameStart(persisted, validStart(expected, persisted.references()));
        return expected;
    }

    private void validatePersistedStarts(long worldSeed, Mc263StructureCarrier carrier) {
        for (Mc263StructureCarrier.ChunkStarts chunk : carrier.startChunks()) {
            for (Mc263StructureCarrier.StartEntry entry : chunk.orderedStarts()) {
                if (entry.structureId().equals(STRUCTURE)
                        && entry.body() instanceof Mc263StructureCarrier.ValidStart valid) {
                    requirePersistedIdentity(worldSeed, valid);
                }
            }
        }
    }

    private static void productionPreflight(Mc263TrialChambersProducer.Start start,
            Mc263StructureCarrier.ChunkReferences references) {
        Mc263TrialChambersProductionTransaction.execute(start, placementClip(references),
                TransactionWorld.preflight());
    }

    private static Mc263TemplatePlacementExecutor.Clip placementClip(
            Mc263StructureCarrier.ChunkReferences references) {
        return Mc263TemplatePlacementExecutor.Clip.chunk(references.chunkX(), references.chunkZ(),
                Blocks.MIN_Y + 1, Blocks.MAX_Y);
    }

    private static Mc263FeaturesRegion.StructureBatch emptyBatch() {
        return new Mc263FeaturesRegion.StructureBatch(List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
    }

    private static Mc263FeaturesRegion.StructureBatch batch(
            Mc263TemplatePlacementExecutor.Result result, long owner,
            Mc263CanonicalFeaturesProducerSkeleton.StructurePlacementInput placement) {
        List<Mc263FeaturesRegion.StructureBlockWrite> blocks = result.finalCells().stream()
                .map(value -> new Mc263FeaturesRegion.StructureBlockWrite(value.position().x(),
                        value.position().y(), value.position().z(),
                        Mc263FeatureBlockState.canonicalExactState(value.exactState()), owner))
                .toList();
        List<Mc263FeaturesRegion.StructureLoot> loot = result.loot().stream()
                .filter(value -> !Mc263TrialChambersProductionTransaction
                        .isFixedContainerDraw(value.table()))
                .filter(value -> placement.clip().contains(value.position().x(),
                        value.position().y(), value.position().z()))
                .map(value -> {
                    if (!Mc263TrialChambersProductionTransaction
                            .isAuthenticatedLdecTable(value.table())) {
                        throw new IllegalArgumentException("Trial unauthenticated LDEC table: "
                                + value.table());
                    }
                    Mc263TemplatePlacementExecutor.BentRow bent = result.bent()
                            .get(value.bentOrdinal());
                    if (!isLdecBentPair(bent.blockEntityType(), value.table())) {
                        throw new IllegalArgumentException("Trial BENT/LDEC binding drift: "
                                + bent.blockEntityType() + " / " + value.table());
                    }
                    return new Mc263FeaturesRegion.StructureLoot(value.position().x(),
                            value.position().y(), value.position().z(), value.table(),
                            value.signedSeed(), placement.productionContext(value.position().x(),
                                    value.position().y(), value.position().z(), value.table()));
                }).toList();
        if (loot.size() != loot.stream().map(value -> value.blockX() + "," + value.blockY()
                + "," + value.blockZ() + "|" + value.lootTable()).distinct().count()) {
            throw new IllegalArgumentException("duplicate Trial BENT/LDEC handoff");
        }
        List<Mc263FeaturesRegion.StructureBentEvidence> bent = result.bent().stream()
                .map(value -> new Mc263FeaturesRegion.StructureBentEvidence(value.position().x(),
                        value.position().y(), value.position().z(), blockKey(value.exactState()),
                        value.blockEntityType(),
                        value.canonicalNbt()))
                .toList();
        List<Mc263FeaturesRegion.StructureFluidTick> fluidTicks = result.blockTicks().stream()
                .map(value -> new Mc263FeaturesRegion.StructureFluidTick(value.position().x(),
                        value.position().y(), value.position().z(), value.key(), value.delay(),
                        value.priority(), value.subTickOrder()))
                .toList();
        return new Mc263FeaturesRegion.StructureBatch(blocks, loot, List.of(), bent, List.of(),
                fluidTicks, List.of(), List.of(), List.of(), List.of());
    }

    private static String blockKey(String exactState) {
        int property = exactState.indexOf('[');
        return property < 0 ? exactState : exactState.substring(0, property);
    }

    private static boolean isLdecBentPair(String blockEntityType, String lootTable) {
        return ("minecraft:chest".equals(blockEntityType)
                && lootTable.startsWith("minecraft:chests/trial_chambers/"))
                || ("minecraft:dispenser".equals(blockEntityType)
                        && ("minecraft:dispensers/trial_chambers/chamber".equals(lootTable)
                                || "minecraft:dispensers/trial_chambers/corridor".equals(lootTable)))
                || ("minecraft:decorated_pot".equals(blockEntityType)
                        && "minecraft:pots/trial_chambers/corridor".equals(lootTable));
    }

    private static Mc263StructureCarrier.ValidStart validStart(
            Mc263TrialChambersProducer.Start start, int references) {
        List<PiecePlacement> placements = start.executionPlan().pieces();
        List<Mc263TrialChambersProducer.BinaryNbt> payloads = start.carrier().pieces();
        if (placements.size() != payloads.size()) {
            throw new IllegalArgumentException("Trial placement/payload cardinality mismatch");
        }
        ArrayList<Mc263StructureCarrier.Piece> pieces = new ArrayList<>(placements.size());
        for (int index = 0; index < placements.size(); index++) {
            PiecePlacement placement = placements.get(index);
            Bounds bounds = placement.bounds();
            List<Mc263StructureCarrier.Junction> junctions = placement.junctions().stream()
                    .map(value -> new Mc263StructureCarrier.Junction(value.sourceX(),
                            value.sourceGroundY(), value.sourceZ(), value.deltaY(),
                            Mc263StructureCarrier.Projection.valueOf(
                                    value.destinationProjection().name())))
                    .toList();
            pieces.add(new Mc263StructureCarrier.Piece(PIECE_TYPE,
                    new Mc263StructureCarrier.BoundingBox(bounds.minX(), bounds.minY(),
                            bounds.minZ(), bounds.maxX(), bounds.maxY(), bounds.maxZ()), true,
                    Mc263StructureCarrier.Projection.valueOf(placement.projection().name()),
                    placement.groundLevelDelta(), junctions,
                    new Mc263StructureCarrier.PiecePayload(payloads.get(index).bytes())));
        }
        Mc263TrialChambersProducer.Box aggregate = start.aggregateBoundingBox();
        return new Mc263StructureCarrier.ValidStart(STRUCTURE + "@" + start.chunkX() + ","
                + start.chunkZ(), start.chunkX(), start.chunkZ(), references,
                new Mc263StructureCarrier.BoundingBox(aggregate.minX(), aggregate.minY(),
                        aggregate.minZ(), aggregate.maxX(), aggregate.maxY(), aggregate.maxZ()),
                pieces);
    }

    private static void requireSameStart(Mc263StructureCarrier.ValidStart actual,
            Mc263StructureCarrier.ValidStart expected) {
        if (!actual.startKey().equals(expected.startKey())
                || actual.originChunkX() != expected.originChunkX()
                || actual.originChunkZ() != expected.originChunkZ()
                || actual.references() != expected.references()
                || !actual.adjustedBoundingBox().equals(expected.adjustedBoundingBox())
                || actual.orderedPieces().size() != expected.orderedPieces().size()) {
            throw new IllegalArgumentException("persisted Trial start identity mismatch");
        }
        for (int index = 0; index < actual.orderedPieces().size(); index++) {
            Mc263StructureCarrier.Piece left = actual.orderedPieces().get(index);
            Mc263StructureCarrier.Piece right = expected.orderedPieces().get(index);
            if (!left.pieceType().equals(PIECE_TYPE) || !left.equals(right)
                    || !Arrays.equals(left.persistedPayload().binaryNbtCompound(),
                            right.persistedPayload().binaryNbtCompound())) {
                throw new IllegalArgumentException(
                        "persisted Trial piece/template/state identity mismatch at " + index);
            }
        }
    }

    private static Mc263StructureCarrier successor(Mc263StructureCarrier predecessor,
            String startKey) {
        ArrayList<Mc263StructureCarrier.ChunkStarts> chunks = new ArrayList<>();
        for (Mc263StructureCarrier.ChunkStarts chunk : predecessor.startChunks()) {
            ArrayList<Mc263StructureCarrier.StartEntry> starts = new ArrayList<>();
            for (Mc263StructureCarrier.StartEntry entry : chunk.orderedStarts()) {
                if (entry.structureId().equals(STRUCTURE)
                        && entry.body() instanceof Mc263StructureCarrier.ValidStart valid
                        && valid.startKey().equals(startKey)) {
                    starts.add(new Mc263StructureCarrier.StartEntry(STRUCTURE,
                            new Mc263StructureCarrier.ValidStart(valid.startKey(),
                                    valid.originChunkX(), valid.originChunkZ(), 1,
                                    valid.adjustedBoundingBox(), valid.orderedPieces())));
                } else {
                    starts.add(entry);
                }
            }
            chunks.add(chunk.withStarts(starts));
        }
        return new Mc263StructureCarrier(predecessor.registry(), chunks,
                predecessor.referenceChunks(), predecessor.rawStartPayloads(),
                predecessor.producerGraphPayloads());
    }

    private static boolean intersectsAnyPiece(Mc263TrialChambersProducer.Start start,
            Mc263CanonicalFeaturesProducerSkeleton.SourceClip clip) {
        for (PiecePlacement piece : start.executionPlan().pieces()) {
            Bounds box = piece.bounds();
            if (box.maxX() >= clip.minX() && box.minX() <= clip.maxX()
                    && box.maxY() >= clip.minY() && box.minY() <= clip.maxY()
                    && box.maxZ() >= clip.minZ() && box.minZ() <= clip.maxZ()) return true;
        }
        return false;
    }

    private static void requireSchedule(Mc263StructureIndexReceipt.Entry entry,
            Mc263StructureCarrier.Registry registry) {
        Mc263StructureCarrier.StructureDefinition definition = registry.require(STRUCTURE);
        if (entry.step() != STEP || entry.index() != INDEX || !entry.key().equals(STRUCTURE)
                || entry.biomeMask() != BIOME_MASK
                || !Mc263StructureIndexReceipt.entries().get(REGISTRY_ORDINAL).equals(entry)
                || definition.registryOrdinal() != REGISTRY_ORDINAL
                || !definition.structureId().equals(STRUCTURE)
                || definition.decorationStep() != STEP
                || definition.terrainAdjustment()
                        != Mc263StructureCarrier.TerrainAdjustment.ENCAPSULATE) {
            throw new IllegalArgumentException("Trial schedule/membership/ordinal mismatch");
        }
    }

    private static void requireDispatcher(
            Mc263FeatureDispatcher.StructurePlacementContext dispatcher,
            Mc263StructureCarrier.ChunkReferences references) {
        if (dispatcher.step() != STEP || dispatcher.globalIndex() != INDEX
                || !dispatcher.structureKey().equals(STRUCTURE)
                || dispatcher.sourceChunkX() != references.chunkX()
                || dispatcher.sourceChunkZ() != references.chunkZ()) {
            throw new IllegalArgumentException("Trial dispatcher/source mismatch");
        }
    }

    private static void requireSourceBinding(Mc263StructureCarrier.ChunkReferences references,
            Mc263CanonicalFeaturesProducerSkeleton.SourceClip clip) {
        if (!Mc263CanonicalFeaturesProducerSkeleton.SourceClip.chunk(
                references.chunkX(), references.chunkZ()).equals(clip)) {
            throw new IllegalArgumentException("Trial source clip mismatch");
        }
    }

    private static byte[] referenceSuccessor(byte[] predecessor) {
        byte[] successor = predecessor.clone();
        if (successor.length <= 19 || successor[19] != 0) {
            throw new IllegalArgumentException("Trial mutable predecessor references drift");
        }
        successor[19] = 1;
        return successor;
    }

    private static final class BoundaryWorld implements Mc263TrialChambersProducer.WorldAccess {
        @Override public boolean supportsBuildHeightBoundary() { return true; }
        @Override public int minBuildY() { return Blocks.MIN_Y; }
        @Override public int maxBuildY() { return Blocks.MAX_Y + 1; }
    }

    private static final class AcceptingPublisher implements Mc263TrialChambersProducer.Publisher {
        @Override public boolean supports(String structureKey, String carrierFormat) {
            return STRUCTURE.equals(structureKey)
                    && Mc263TrialChambersProducer.CARRIER_FORMAT.equals(carrierFormat);
        }
        @Override public void publishAtomically(Mc263TrialChambersProducer.Start start) { }
    }

    /** Isolated overlay: no live region mutation occurs before the canonical batch commit. */
    private static final class TransactionWorld implements
            Mc263TemplatePlacementExecutor.PlacementWorld {
        private final Mc263FeaturesRegion region;
        private final Map<Mc263TemplatePlacementExecutor.Vec, String> overlay =
                new LinkedHashMap<>();
        private final Map<Mc263TemplatePlacementExecutor.Vec, MutableBlockEntity> blockEntities =
                new LinkedHashMap<>();

        private TransactionWorld(Mc263FeaturesRegion region) { this.region = region; }
        static TransactionWorld preflight() { return new TransactionWorld(null); }
        static TransactionWorld live(Mc263FeaturesRegion region) {
            return new TransactionWorld(Objects.requireNonNull(region, "Trial live region"));
        }

        private Mc263FeatureBlockState state(Mc263TemplatePlacementExecutor.Vec position) {
            String staged = overlay.get(position);
            if (staged != null) return Mc263FeatureBlockState.fromExact(staged);
            return region == null ? Mc263FeatureBlockState.fromExact("minecraft:air")
                    : region.blockState(position.x(), position.y(), position.z());
        }

        @Override public int getHeight(String heightmap, int x, int z) {
            if (region == null) return Blocks.SEA_LEVEL + 1;
            return region.worldSurfaceWg(x, z);
        }
        @Override public String getBlockState(Mc263TemplatePlacementExecutor.Vec position) {
            return state(position).exactState();
        }
        @Override public String getFluidKey(Mc263TemplatePlacementExecutor.Vec position) {
            return switch (state(position).fluidKind()) {
                case WATER_SOURCE, WATER_FLOWING -> "minecraft:water";
                case LAVA_SOURCE, LAVA_FLOWING -> "minecraft:lava";
                case NONE -> Mc263TemplatePlacementExecutor.EMPTY_FLUID;
            };
        }
        @Override public boolean isFluidSource(Mc263TemplatePlacementExecutor.Vec position) {
            return switch (state(position).fluidKind()) {
                case WATER_SOURCE, LAVA_SOURCE -> true;
                default -> false;
            };
        }
        @Override public double fluidHeight(Mc263TemplatePlacementExecutor.Vec position) {
            return state(position).fluidKind() == Mc263FeatureBlockState.FluidKind.NONE
                    ? 0.0D : 1.0D;
        }
        @Override public boolean setBlock(Mc263TemplatePlacementExecutor.Vec position,
                String exactState, int flags) {
            String previous = state(position).exactState();
            overlay.put(position, exactState);
            String type = blockEntityType(exactState);
            if (type == null) {
                blockEntities.remove(position);
                return true;
            }
            // LevelChunk#setBlockState drops the block entity only through the outgoing state's
            // onRemove, which calls removeBlockEntity solely when the block itself changed
            // (BlockState#shouldChangedStateKeepBlockEntity / !state.is(newState.getBlock()));
            // when the block is unchanged the existing block entity survives and only receives
            // setBlockState(state). A property-only rewrite -- the keepLiquids pass turning an
            // already placed decorated_pot's waterlogged from false to true -- therefore keeps the
            // canonical NBT placeInWorld already loaded into it, so the piece's trailing
            // setChanged() still sees a loaded payload.
            MutableBlockEntity existing = blockEntities.get(position);
            if (existing != null && existing.blockEntityType().equals(type)
                    && blockKey(previous).equals(blockKey(exactState))) {
                return true;
            }
            blockEntities.put(position, new MutableBlockEntity(type));
            return true;
        }
        @Override public Mc263TemplatePlacementExecutor.BlockEntityHandle getBlockEntity(
                Mc263TemplatePlacementExecutor.Vec position) {
            return blockEntities.get(position);
        }
        @Override public void scheduleFluidTick(Mc263TemplatePlacementExecutor.Vec position,
                String fluidKey, int delay) { }

        private static String blockEntityType(String exactState) {
            return switch (blockKey(exactState)) {
                case "minecraft:barrel" -> "minecraft:barrel";
                case "minecraft:chest" -> "minecraft:chest";
                case "minecraft:decorated_pot" -> "minecraft:decorated_pot";
                case "minecraft:dispenser" -> "minecraft:dispenser";
                case "minecraft:hopper" -> "minecraft:hopper";
                case "minecraft:vault" -> "minecraft:vault";
                case "minecraft:trial_spawner" -> "minecraft:trial_spawner";
                default -> null;
            };
        }
    }

    private static final class MutableBlockEntity implements
            Mc263TemplatePlacementExecutor.BlockEntityHandle {
        private final String type;
        private byte[] canonicalNbt;
        private boolean changed;

        private MutableBlockEntity(String type) { this.type = type; }
        @Override public String blockEntityType() { return type; }
        @Override public void loadCanonicalNbt(byte[] value) { canonicalNbt = value.clone(); }
        @Override public void setChanged() {
            if (canonicalNbt == null) throw new IllegalStateException(
                    "Trial block entity changed before canonical NBT load");
            changed = true;
        }
    }
}
