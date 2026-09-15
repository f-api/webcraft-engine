package com.gameexpert.terrain.mc.feature;

import com.gameexpert.terrain.mc.ore.Mc263DecorationRandom.WorldgenRandom;
import com.gameexpert.terrain.mc.structure.Mc263HardcodedStructureCarrier;
import com.gameexpert.terrain.mc.structure.Mc263HardcodedStructureCarrier.Kind;
import com.gameexpert.terrain.mc.structure.Mc263HardcodedStructureCarrier.PieceKind;
import com.gameexpert.terrain.mc.structure.Mc263HardcodedStructureExecutor;
import com.gameexpert.terrain.mc.structure.Mc263SwampHutProgram;
import com.gameexpert.terrain.mc.structure.Mc263SwampHutStartGenerator;
import com.gameexpert.terrain.mc.structure.Mc263StructureCarrier;
import com.gameexpert.terrain.mc.structure.Mc263StructureCarrier.ChunkReferences;
import com.gameexpert.terrain.mc.structure.Mc263StructureCarrier.ChunkStarts;
import com.gameexpert.terrain.mc.structure.Mc263StructureCarrier.Piece;
import com.gameexpert.terrain.mc.structure.Mc263StructureCarrier.PiecePayload;
import com.gameexpert.terrain.mc.structure.Mc263StructureCarrier.StartEntry;
import com.gameexpert.terrain.mc.structure.Mc263StructureCarrier.ValidStart;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Exact candidate adapter for the pinned swamp-hut carrier and procedural program.
 * Registration remains closed while the production exact-state registry cannot encode every
 * ordered swamp-hut block state and until all 52 executors are exact.
 */
final class Mc263SwampHutCanonicalExecutor implements
        Mc263CanonicalFeaturesProducerSkeleton.CanonicalStructureExecutor {
    private static final int STEP = 4;
    private static final int INDEX = 38;
    private static final int REGISTRY_ORDINAL = 43;
    private static final long BIOME_MASK = 0x00000000000800L;

    private Mc263SwampHutCanonicalExecutor() { }

    static void register(
            Mc263CanonicalFeaturesProducerSkeleton.CanonicalStructureExecutorRegistry registry) {
        Objects.requireNonNull(registry, "canonical structure executor registry")
                .register(Mc263SwampHutProgram.STRUCTURE_ID,
                        new Mc263SwampHutCanonicalExecutor());
    }

    @Override
    public void preflight(
            Mc263CanonicalFeaturesProducerSkeleton.StructurePreflightContext context) {
        Objects.requireNonNull(context, "swamp-hut preflight context");
        requireSchedule(context.entry());
        requireSourceBinding(context.references(), context.clip());
        requireRegistry(context.carrier().registry());
        for (ValidStart start : context.carrier().resolveStarts(
                context.references(), Mc263SwampHutProgram.STRUCTURE_ID)) {
            loadPersisted(context.worldSeed(), start);
        }
    }

    @Override
    public void place(Mc263CanonicalFeaturesProducerSkeleton.StructurePlacementInput context) {
        Objects.requireNonNull(context, "swamp-hut placement context");
        Mc263FeatureDispatcher.StructurePlacementContext dispatcher = context.dispatcher();
        requireDispatcher(dispatcher, context.references());
        requireSourceBinding(context.references(), context.clip());
        requireRegistry(context.carrier().registry());

        List<String> startKeys = context.carrier().resolveStarts(context.references(),
                Mc263SwampHutProgram.STRUCTURE_ID).stream()
                .map(ValidStart::startKey).toList();
        for (String startKey : startKeys) {
            Mc263StructureCarrier predecessorCarrier = context.carrier();
            ChunkReferences latestReferences = context.references();
            ValidStart start = predecessorCarrier.resolveStarts(latestReferences,
                    Mc263SwampHutProgram.STRUCTURE_ID).stream()
                    .filter(candidate -> candidate.startKey().equals(startKey))
                    .findFirst().orElseThrow(() -> new IllegalStateException(
                            "evolved swamp-hut carrier lost referenced start"));
            Mc263HardcodedStructureCarrier predecessor = loadPersisted(
                    dispatcher.worldSeed(), start);
            CapturingRegionWorld world = new CapturingRegionWorld(dispatcher.region(), startKey);
            Mc263HardcodedStructureExecutor.ExecutionResult execution = execute(predecessor,
                    programClip(context.clip()), world, dispatcher.random());
            List<Mc263FinalChunkSidecars.StructureEntity> entities = validateExecution(
                    predecessor, execution, context.clip(), world.blocks, world.entities);
            Mc263StructureCarrier successorCarrier = rebuild(
                    predecessorCarrier, startKey, execution.successor());
            context.commitStructureBatch(predecessorCarrier, successorCarrier,
                    new Mc263FeaturesRegion.StructureBatch(
                            world.blocks, List.of(), List.of(), List.of(), entities));
        }
    }

    private static void requireSchedule(Mc263StructureIndexReceipt.Entry entry) {
        if (entry.step() != STEP || entry.index() != INDEX
                || !entry.key().equals(Mc263SwampHutProgram.STRUCTURE_ID)
                || entry.biomeMask() != BIOME_MASK) {
            throw new IllegalArgumentException("swamp-hut schedule mismatch");
        }
    }

    private static void requireRegistry(Mc263StructureCarrier.Registry registry) {
        Mc263SwampHutStartGenerator.validateRegistry(registry);
        Mc263StructureCarrier.StructureDefinition definition =
                registry.definitions().get(REGISTRY_ORDINAL);
        if (!definition.structureId().equals(Mc263SwampHutProgram.STRUCTURE_ID)
                || definition.registryOrdinal() != REGISTRY_ORDINAL
                || definition.decorationStep() != STEP
                || definition.terrainAdjustment()
                        != Mc263StructureCarrier.TerrainAdjustment.NONE) {
            throw new IllegalArgumentException("swamp-hut carrier registry mismatch");
        }
    }

    private static void requireSourceBinding(ChunkReferences references,
            Mc263CanonicalFeaturesProducerSkeleton.SourceClip clip) {
        if (!Mc263CanonicalFeaturesProducerSkeleton.SourceClip.chunk(
                references.chunkX(), references.chunkZ()).equals(clip)) {
            throw new IllegalArgumentException("swamp-hut source clip mismatch");
        }
    }

    private static void requireDispatcher(
            Mc263FeatureDispatcher.StructurePlacementContext dispatcher,
            ChunkReferences references) {
        if (dispatcher.step() != STEP || dispatcher.globalIndex() != INDEX
                || !dispatcher.structureKey().equals(Mc263SwampHutProgram.STRUCTURE_ID)
                || dispatcher.sourceChunkX() != references.chunkX()
                || dispatcher.sourceChunkZ() != references.chunkZ()) {
            throw new IllegalArgumentException("swamp-hut dispatcher/source mismatch");
        }
    }

    private static Mc263HardcodedStructureExecutor.Clip programClip(
            Mc263CanonicalFeaturesProducerSkeleton.SourceClip clip) {
        return new Mc263HardcodedStructureExecutor.Clip(clip.minX(), clip.minY(), clip.minZ(),
                clip.maxX(), clip.maxY(), clip.maxZ());
    }

    static Mc263HardcodedStructureExecutor.ExecutionResult execute(
            Mc263HardcodedStructureCarrier carrier,
            Mc263HardcodedStructureExecutor.Clip clip,
            Mc263HardcodedStructureExecutor.WorldAccess world,
            WorldgenRandom random) {
        Objects.requireNonNull(carrier, "swamp-hut carrier");
        if (carrier.kind() != Kind.SWAMP_HUT
                || carrier.orderedPieces().size() != 1
                || carrier.orderedPieces().getFirst().kind() != PieceKind.SWAMP_HUT
                || carrier.orderedPieces().getFirst().template() != null) {
            throw new IllegalArgumentException("noncanonical swamp-hut carrier shape");
        }
        return Mc263HardcodedStructureExecutor.execute(carrier,
                Objects.requireNonNull(clip, "swamp-hut clip"),
                Mc263SwampHutProgram.program(),
                Objects.requireNonNull(world, "swamp-hut world"),
                Objects.requireNonNull(random, "swamp-hut random"));
    }

    record Settlement(Mc263StructureCarrier structureCarrier,
            Mc263FinalChunkCodec.FinalChunk finalChunk) { }

    /**
     * Validates execution evidence first, then constructs both durable successors. Replaying the
     * same evidence against those successors is byte-idempotent and cannot duplicate occupants.
     */
    static Settlement settle(Mc263StructureCarrier structureCarrier,
            ChunkReferences references, Mc263HardcodedStructureCarrier predecessor,
            Mc263HardcodedStructureExecutor.ExecutionResult execution,
            List<Mc263FinalChunkSidecars.StructureEntity> entities,
            Mc263FinalChunkCodec.FinalChunk finalChunk) {
        Objects.requireNonNull(structureCarrier, "STR263C1 carrier");
        Objects.requireNonNull(references, "swamp-hut references");
        Objects.requireNonNull(predecessor, "swamp-hut predecessor");
        Objects.requireNonNull(execution, "swamp-hut execution");
        Objects.requireNonNull(entities, "swamp-hut entity evidence");
        Objects.requireNonNull(finalChunk, "MCF263LC final chunk");
        Mc263FinalChunkCodec.validate(finalChunk);
        requireCarrierShape(predecessor);
        Mc263HardcodedStructureCarrier successor = execution.successor();
        requireCarrierShape(successor);
        if (!Arrays.equals(successor.encodeCanonical(), predecessor
                .withSuccessor(successor.successor()).encodeCanonical())) {
            throw new IllegalArgumentException("swamp-hut execution/predecessor mismatch");
        }
        if (references.chunkX() != finalChunk.chunkX()
                || references.chunkZ() != finalChunk.chunkZ()
                || predecessor.chunkX() != finalChunk.chunkX()
                || predecessor.chunkZ() != finalChunk.chunkZ()) {
            throw new IllegalArgumentException("swamp-hut settlement chunk mismatch");
        }
        if (successor.successor().heightPosition() < 0
                || !successor.successor().flag("Witch")
                || !successor.successor().flag("Cat")
                || execution.sidecars() != 2) {
            throw new IllegalArgumentException("incomplete swamp-hut successor evidence");
        }

        List<Mc263FinalChunkSidecars.StructureEntity> exactEntities =
                validateEntities(successor, entities);
        SelectedStart selected = selectStart(structureCarrier, references, predecessor, successor);
        Mc263FinalChunkSidecars merged = mergeEntities(finalChunk.sidecars(), exactEntities);
        Mc263FinalChunkCodec.FinalChunk successorChunk = new Mc263FinalChunkCodec.FinalChunk(
                finalChunk.chunkX(), finalChunk.chunkZ(), finalChunk.blockIds(),
                finalChunk.stateOverrides(), finalChunk.worldSurfaceWg(),
                finalChunk.oceanFloorWg(), finalChunk.motionBlocking(), merged);
        Mc263FinalChunkCodec.encode(successorChunk);

        Mc263StructureCarrier successorCarrier = rebuild(
                structureCarrier, selected.startKey(), successor);
        successorCarrier = successorCarrier.strictlyDecoded();
        return new Settlement(successorCarrier, successorChunk);
    }

    private static void requireCarrierShape(Mc263HardcodedStructureCarrier carrier) {
        if (carrier.kind() != Kind.SWAMP_HUT
                || carrier.orderedPieces().size() != 1
                || carrier.orderedPieces().getFirst().kind() != PieceKind.SWAMP_HUT
                || carrier.orderedPieces().getFirst().template() != null) {
            throw new IllegalArgumentException("noncanonical swamp-hut carrier shape");
        }
    }

    private static List<Mc263FinalChunkSidecars.StructureEntity> validateEntities(
            Mc263HardcodedStructureCarrier successor,
            List<Mc263FinalChunkSidecars.StructureEntity> evidence) {
        List<Mc263FinalChunkSidecars.StructureEntity> values = List.copyOf(evidence);
        if (values.size() != 2) {
            throw new IllegalArgumentException("swamp-hut entity evidence set mismatch");
        }
        var box = successor.boundingBox();
        int yDelta = Math.subtractExact(successor.successor().heightPosition(), box.minY());
        int x;
        int z;
        switch (successor.rotation()) {
            case NORTH -> { x = box.minX() + 2; z = box.maxZ() - 5; }
            case SOUTH -> { x = box.minX() + 2; z = box.minZ() + 5; }
            case WEST -> { x = box.maxX() - 5; z = box.minZ() + 2; }
            case EAST -> { x = box.minX() + 5; z = box.minZ() + 2; }
            default -> throw new IllegalArgumentException(
                    "swamp hut requires cardinal orientation");
        }
        double entityX = x + .5D;
        double entityY = box.minY() + 2 + yDelta;
        double entityZ = z + .5D;
        for (int index = 0; index < values.size(); index++) {
            var value = values.get(index);
            String expectedKey = index == 0 ? "minecraft:witch" : "minecraft:cat";
            if (!value.entityKey().equals(expectedKey)
                    || !value.spawnReason().equals("minecraft:structure")
                    || Double.doubleToRawLongBits(value.x())
                            != Double.doubleToRawLongBits(entityX)
                    || Double.doubleToRawLongBits(value.y())
                            != Double.doubleToRawLongBits(entityY)
                    || Double.doubleToRawLongBits(value.z())
                            != Double.doubleToRawLongBits(entityZ)
                    || Float.floatToRawIntBits(value.yaw()) != 0
                    || Float.floatToRawIntBits(value.pitch()) != 0
                    || Double.doubleToRawLongBits(value.velocityX()) != 0
                    || Double.doubleToRawLongBits(value.velocityY()) != 0
                    || Double.doubleToRawLongBits(value.velocityZ()) != 0
                    || !value.lootTable().isEmpty() || value.lootSeed() != 0L
                    || !Arrays.equals(value.canonicalPayload(),
                            Mc263SwampHutProgram.canonicalEntityPayload())) {
                throw new IllegalArgumentException(
                        "noncanonical swamp-hut entity evidence at " + index);
            }
        }
        return values;
    }

    /**
     * The referenced start this settlement's predecessor was loaded from.
     *
     * <p>{@code ChunkGenerator#applyBiomeDecoration} binds one feature seed per (chunk, structure,
     * decoration step) and then places every element of {@code
     * StructureManager#startsForStructure(SectionPos, Structure)} -- a {@code List} drained from
     * the chunk's whole {@code LongSet} of references -- through one shared {@code WorldgenRandom}.
     * Two swamp huts whose adjusted boxes both reach one chunk are therefore authentic, so the
     * closure is not capped at one member; the selection is bound to the start the execution ran
     * on. Only a repeated start is rejected, because {@code ChunkStarts} holds at most one start per
     * (origin chunk, structure) and {@code ReferenceSet} forbids a duplicate origin, so a repeat
     * proves a corrupt closure rather than two huts.</p>
     */
    private static ValidStart boundStart(Mc263StructureCarrier carrier,
            ChunkReferences references, Mc263HardcodedStructureCarrier predecessor) {
        List<ValidStart> starts = carrier.resolveStarts(
                references, Mc263SwampHutProgram.STRUCTURE_ID);
        if (starts.stream().map(ValidStart::startKey).distinct().count() != starts.size()) {
            throw new IllegalArgumentException("swamp-hut reference closure repeats a start");
        }
        List<ValidStart> bound = starts.stream()
                .filter(value -> value.originChunkX() == predecessor.chunkX()
                        && value.originChunkZ() == predecessor.chunkZ()).toList();
        if (bound.size() != 1) {
            throw new IllegalArgumentException("swamp-hut start evidence set mismatch");
        }
        return bound.getFirst();
    }

    private static SelectedStart selectStart(Mc263StructureCarrier carrier,
            ChunkReferences references, Mc263HardcodedStructureCarrier predecessor,
            Mc263HardcodedStructureCarrier successor) {
        ValidStart start = boundStart(carrier, references, predecessor);
        if (start.originChunkX() != predecessor.chunkX()
                || start.originChunkZ() != predecessor.chunkZ()
                || start.orderedPieces().size() != 1) {
            throw new IllegalArgumentException("swamp-hut start/carrier mismatch");
        }
        Piece piece = start.orderedPieces().getFirst();
        requirePieceShape(piece);
        byte[] current = piece.persistedPayload().binaryNbtCompound();
        boolean predecessorPayload = Arrays.equals(
                current, Mc263SwampHutProgram.canonicalPieceNbt(predecessor));
        boolean successorPayload = Arrays.equals(
                current, Mc263SwampHutProgram.canonicalPieceNbt(successor));
        if (!predecessorPayload && !successorPayload) {
            throw new IllegalArgumentException("noncanonical swamp-hut persisted NBT");
        }
        return new SelectedStart(start.startKey());
    }

    private static void requirePieceShape(Piece piece) {
        if (!piece.pieceType().equals(Mc263SwampHutProgram.PIECE_TYPE)
                || piece.poolElement()
                || piece.projection() != Mc263StructureCarrier.Projection.NOT_APPLICABLE
                || piece.groundLevelDelta() != 0 || !piece.junctions().isEmpty()) {
            throw new IllegalArgumentException("expected exact minecraft:tesh piece");
        }
    }

    private static Mc263FinalChunkSidecars mergeEntities(Mc263FinalChunkSidecars source,
            List<Mc263FinalChunkSidecars.StructureEntity> additions) {
        ArrayList<Mc263FinalChunkSidecars.StructureEntity> merged =
                new ArrayList<>(source.entities());
        for (var addition : additions) {
            Mc263FinalChunkSidecars.StructureEntity existing = source.entities().stream()
                    .filter(value -> sameIdentity(value, addition)).findFirst().orElse(null);
            if (existing == null) merged.add(addition);
            else if (!existing.equals(addition)) {
                throw new IllegalArgumentException(
                        "swamp-hut entity conflicts with existing sidecar");
            }
        }
        return new Mc263FinalChunkSidecars(source.blockTicks(), source.fluidTicks(), source.loot(),
                source.spawners(), source.owners(), source.archaeology(), source.bees(),
                source.blockEntities(), merged, source.containerLootDeclarations());
    }

    private static boolean sameIdentity(Mc263FinalChunkSidecars.StructureEntity left,
            Mc263FinalChunkSidecars.StructureEntity right) {
        return left.entityKey().equals(right.entityKey())
                && left.spawnReason().equals(right.spawnReason())
                && Double.doubleToRawLongBits(left.x()) == Double.doubleToRawLongBits(right.x())
                && Double.doubleToRawLongBits(left.y()) == Double.doubleToRawLongBits(right.y())
                && Double.doubleToRawLongBits(left.z()) == Double.doubleToRawLongBits(right.z());
    }

    private static Mc263StructureCarrier rebuild(Mc263StructureCarrier carrier,
            String selectedStart, Mc263HardcodedStructureCarrier successor) {
        byte[] payload = Mc263SwampHutProgram.canonicalPieceNbt(successor);
        var hardcodedBox = successor.boundingBox();
        int yDelta = Math.subtractExact(
                successor.successor().heightPosition(), hardcodedBox.minY());
        var box = new Mc263StructureCarrier.BoundingBox(hardcodedBox.minX(),
                Math.addExact(hardcodedBox.minY(), yDelta), hardcodedBox.minZ(),
                hardcodedBox.maxX(), Math.addExact(hardcodedBox.maxY(), yDelta),
                hardcodedBox.maxZ());
        ArrayList<ChunkStarts> chunks = new ArrayList<>();
        for (ChunkStarts chunk : carrier.startChunks()) {
            ArrayList<StartEntry> starts = new ArrayList<>();
            for (StartEntry entry : chunk.orderedStarts()) {
                if (!(entry.body() instanceof ValidStart start)
                        || !start.startKey().equals(selectedStart)) {
                    starts.add(entry);
                    continue;
                }
                Piece current = start.orderedPieces().getFirst();
                Piece replacement = new Piece(current.pieceType(), box, false,
                        Mc263StructureCarrier.Projection.NOT_APPLICABLE, 0, List.of(),
                        new PiecePayload(payload));
                starts.add(new StartEntry(entry.structureId(), new ValidStart(start.startKey(),
                        start.originChunkX(), start.originChunkZ(), start.references(), box,
                        List.of(replacement))));
            }
            chunks.add(chunk.withStarts(starts));
        }
        return new Mc263StructureCarrier(carrier.registry(), chunks, carrier.referenceChunks(),
                carrier.rawStartPayloads(), carrier.producerGraphPayloads());
    }

    /** Loads either the initial TeSH payload or any exact mutable successor persisted by us. */
    private static Mc263HardcodedStructureCarrier loadPersisted(
            long worldSeed, ValidStart start) {
        Objects.requireNonNull(start, "swamp-hut persisted start");
        Mc263HardcodedStructureCarrier initial = Mc263HardcodedStructureCarrier.plan(
                Kind.SWAMP_HUT, worldSeed, start.originChunkX(), start.originChunkZ());
        requireCarrierShape(initial);
        if (!start.startKey().equals(Mc263SwampHutProgram.STRUCTURE_ID + "@"
                + start.originChunkX() + "," + start.originChunkZ())
                || start.orderedPieces().size() != 1) {
            throw new IllegalArgumentException("noncanonical swamp-hut persisted start");
        }
        Piece piece = start.orderedPieces().getFirst();
        requirePieceShape(piece);
        var initialBox = initial.boundingBox();
        var persistedBox = start.adjustedBoundingBox();
        if (!piece.boundingBox().equals(persistedBox)
                || persistedBox.minX() != initialBox.minX()
                || persistedBox.maxX() != initialBox.maxX()
                || persistedBox.minZ() != initialBox.minZ()
                || persistedBox.maxZ() != initialBox.maxZ()
                || persistedBox.maxY() - persistedBox.minY()
                        != initialBox.maxY() - initialBox.minY()) {
            throw new IllegalArgumentException("noncanonical swamp-hut persisted bounds");
        }

        byte[] payload = piece.persistedPayload().binaryNbtCompound();
        if (persistedBox.minY() == initialBox.minY()
                && Arrays.equals(payload, Mc263SwampHutProgram.canonicalPieceNbt(initial))) {
            return initial;
        }
        int height = Math.addExact(initialBox.minY(),
                Math.subtractExact(persistedBox.minY(), initialBox.minY()));
        for (boolean witch : List.of(false, true)) {
            for (boolean cat : List.of(false, true)) {
                var successor = initial.successor().withHeightPosition(height)
                        .withFlag("Witch", witch).withFlag("Cat", cat);
                Mc263HardcodedStructureCarrier candidate = initial.withSuccessor(successor);
                if (Arrays.equals(payload, Mc263SwampHutProgram.canonicalPieceNbt(candidate))) {
                    return candidate;
                }
            }
        }
        throw new IllegalArgumentException("noncanonical swamp-hut persisted NBT");
    }

    private static List<Mc263FinalChunkSidecars.StructureEntity> validateExecution(
            Mc263HardcodedStructureCarrier predecessor,
            Mc263HardcodedStructureExecutor.ExecutionResult execution,
            Mc263CanonicalFeaturesProducerSkeleton.SourceClip clip,
            List<Mc263FeaturesRegion.StructureBlockWrite> blocks,
            List<Mc263FinalChunkSidecars.StructureEntity> entities) {
        requireCarrierShape(execution.successor());
        if (!Arrays.equals(execution.successor().encodeCanonical(), predecessor
                .withSuccessor(execution.successor().successor()).encodeCanonical())
                || execution.writes() != blocks.size()
                || execution.sidecars() != entities.size()) {
            throw new IllegalArgumentException("swamp-hut execution evidence mismatch");
        }
        var box = execution.successor().boundingBox();
        int yDelta = execution.successor().successor().heightPosition() < 0 ? 0
                : Math.subtractExact(execution.successor().successor().heightPosition(),
                        box.minY());
        int x;
        int z;
        switch (execution.successor().rotation()) {
            case NORTH -> { x = box.minX() + 2; z = box.maxZ() - 5; }
            case SOUTH -> { x = box.minX() + 2; z = box.minZ() + 5; }
            case WEST -> { x = box.maxX() - 5; z = box.minZ() + 2; }
            case EAST -> { x = box.minX() + 5; z = box.minZ() + 2; }
            default -> throw new IllegalArgumentException(
                    "swamp hut requires cardinal orientation");
        }
        int y = Math.addExact(box.minY() + 2, yDelta);
        boolean inClip = x >= clip.minX() && x <= clip.maxX()
                && y >= clip.minY() && y <= clip.maxY()
                && z >= clip.minZ() && z <= clip.maxZ();
        int expected = inClip
                ? (predecessor.successor().flag("Witch") ? 0 : 1)
                        + (predecessor.successor().flag("Cat") ? 0 : 1)
                : 0;
        if (entities.size() != expected
                || execution.successor().successor().flag("Witch")
                        != (predecessor.successor().flag("Witch") || inClip)
                || execution.successor().successor().flag("Cat")
                        != (predecessor.successor().flag("Cat") || inClip)) {
            throw new IllegalArgumentException("incomplete swamp-hut ENTS successor evidence");
        }
        int entityIndex = 0;
        for (String key : List.of("minecraft:witch", "minecraft:cat")) {
            boolean already = predecessor.successor().flag(
                    key.equals("minecraft:witch") ? "Witch" : "Cat");
            if (!inClip || already) continue;
            requireExactEntity(entities.get(entityIndex++), key, x, y, z);
        }
        return List.copyOf(entities);
    }

    private static void requireExactEntity(Mc263FinalChunkSidecars.StructureEntity value,
            String entityKey, int x, int y, int z) {
        if (!value.entityKey().equals(entityKey)
                || !value.spawnReason().equals("minecraft:structure")
                || Double.doubleToRawLongBits(value.x())
                        != Double.doubleToRawLongBits(x + .5D)
                || Double.doubleToRawLongBits(value.y()) != Double.doubleToRawLongBits(y)
                || Double.doubleToRawLongBits(value.z())
                        != Double.doubleToRawLongBits(z + .5D)
                || Float.floatToRawIntBits(value.yaw()) != 0
                || Float.floatToRawIntBits(value.pitch()) != 0
                || Double.doubleToRawLongBits(value.velocityX()) != 0
                || Double.doubleToRawLongBits(value.velocityY()) != 0
                || Double.doubleToRawLongBits(value.velocityZ()) != 0
                || !value.lootTable().isEmpty() || value.lootSeed() != 0L
                || !Arrays.equals(value.canonicalPayload(),
                        Mc263SwampHutProgram.canonicalEntityPayload())) {
            throw new IllegalArgumentException("noncanonical swamp-hut ENTS evidence");
        }
    }

    private static final class CapturingRegionWorld implements
            Mc263HardcodedStructureExecutor.WorldAccess {
        private final Mc263FeaturesRegion region;
        private final List<Mc263FeaturesRegion.StructureBlockWrite> blocks = new ArrayList<>();
        private final List<Mc263FinalChunkSidecars.StructureEntity> entities = new ArrayList<>();
        private final Map<BlockPosition, String> staged = new LinkedHashMap<>();

        private final long owner;

        private CapturingRegionWorld(Mc263FeaturesRegion region, String startKey) {
            this.region = Objects.requireNonNull(region, "swamp-hut region");
            this.owner = Mc263StructureOwner.owner(
                    Mc263SwampHutProgram.STRUCTURE_ID, startKey);
        }

        @Override public boolean supportsExactState(String state) {
            return Mc263FeatureBlockState.supportsExactState(state);
        }
        @Override public boolean supportsMotionBlockingNoLeavesHeight() { return true; }
        @Override public boolean supportsWorldSurfaceWgHeight() { return false; }
        @Override public boolean supportsSeaLevel() { return false; }
        @Override public boolean supportsReplaceableByStructuresQuery() { return true; }
        @Override public boolean supportsLootSidecar() { return false; }
        @Override public boolean supportsSpawnerSidecar() { return false; }
        @Override public boolean supportsArchaeologySidecar() { return false; }
        @Override public boolean supportsStructureEntitySidecar() { return true; }
        @Override public int minY() { return com.gameexpert.terrain.Blocks.MIN_Y; }
        @Override public int maxY() { return com.gameexpert.terrain.Blocks.MAX_Y; }
        @Override public int motionBlockingNoLeaves(int x, int z) {
            return region.motionBlocking(x, z);
        }
        @Override public int worldSurfaceWg(int x, int z) { throw unsupported(); }
        @Override public int seaLevel() { throw unsupported(); }
        @Override public String blockState(Mc263HardcodedStructureExecutor.BlockPos position) {
            String value = staged.get(new BlockPosition(position.x(), position.y(), position.z()));
            return value != null ? value
                    : region.blockState(position.x(), position.y(), position.z()).exactState();
        }
        @Override public boolean isReplaceableByStructures(
                Mc263HardcodedStructureExecutor.BlockPos position, String exactState) {
            Mc263FeatureBlockState state = Mc263FeatureBlockState.fromExact(exactState);
            return state.isAir() || state.fluidAmount() != 0
                    || state.blockKey().equals("minecraft:glow_lichen")
                    || state.blockKey().equals("minecraft:seagrass")
                    || state.blockKey().equals("minecraft:tall_seagrass");
        }
        @Override public boolean setBlock(Mc263HardcodedStructureExecutor.BlockPos position,
                String exactState, int flags) {
            if (flags != 2) throw new IllegalArgumentException("swamp-hut block flags mismatch");
            blocks.add(new Mc263FeaturesRegion.StructureBlockWrite(
                    position.x(), position.y(), position.z(), exactState, owner));
            staged.put(new BlockPosition(position.x(), position.y(), position.z()), exactState);
            return true;
        }
        @Override public boolean hasRandomizableContainer(
                Mc263HardcodedStructureExecutor.BlockPos position) { return false; }
        @Override public void setLoot(Mc263HardcodedStructureExecutor.BlockPos position,
                String lootTable, long seed) { throw unsupported(); }
        @Override public void setSpawner(Mc263HardcodedStructureExecutor.BlockPos position,
                String entityType) { throw unsupported(); }
        @Override public void setArchaeologyLoot(
                Mc263HardcodedStructureExecutor.BlockPos position,
                String lootTable, long seed) { throw unsupported(); }
        @Override public void addStructureEntity(String entityType, String spawnReason,
                double x, double y, double z, float yaw, float pitch, byte[] payload) {
            entities.add(new Mc263FinalChunkSidecars.StructureEntity(entityType, spawnReason,
                    x, y, z, yaw, pitch, 0.0D, 0.0D, 0.0D, payload));
        }

        private static UnsupportedOperationException unsupported() {
            return new UnsupportedOperationException("unsupported swamp-hut world operation");
        }
    }

    private record BlockPosition(int x, int y, int z) { }

    private record SelectedStart(String startKey) { }
}
