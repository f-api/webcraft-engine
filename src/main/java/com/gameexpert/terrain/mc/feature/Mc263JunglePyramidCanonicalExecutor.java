package com.gameexpert.terrain.mc.feature;

import com.gameexpert.terrain.Blocks;
import com.gameexpert.terrain.mc.ore.Mc263DecorationRandom.WorldgenRandom;
import com.gameexpert.terrain.mc.structure.Mc263HardcodedStructureCarrier;
import com.gameexpert.terrain.mc.structure.Mc263HardcodedStructureCarrier.Kind;
import com.gameexpert.terrain.mc.structure.Mc263HardcodedStructureCarrier.PieceKind;
import com.gameexpert.terrain.mc.structure.Mc263JunglePyramidProgram;
import com.gameexpert.terrain.mc.structure.Mc263JunglePyramidStartGenerator;
import com.gameexpert.terrain.mc.structure.Mc263StructureCarrier;
import com.gameexpert.terrain.persistence.CanonicalWorldgenStore;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** Dormant exact TeJP adapter; production remains closed until all 52 executors are exact. */
final class Mc263JunglePyramidCanonicalExecutor implements
        Mc263CanonicalFeaturesProducerSkeleton.CanonicalStructureExecutor {
    private static final int STEP = 4;
    private static final int INDEX = 22;
    private static final long BIOME_MASK = 0x00800200000000L;

    private Mc263JunglePyramidCanonicalExecutor() { }

    static void register(
            Mc263CanonicalFeaturesProducerSkeleton.CanonicalStructureExecutorRegistry registry) {
        Objects.requireNonNull(registry, "canonical structure executor registry")
                .register(Mc263JunglePyramidProgram.STRUCTURE_ID,
                        new Mc263JunglePyramidCanonicalExecutor());
    }

    @Override
    public void preflight(
            Mc263CanonicalFeaturesProducerSkeleton.StructurePreflightContext context) {
        Objects.requireNonNull(context, "jungle-pyramid preflight context");
        requireSchedule(context.entry());
        requireSourceBinding(context.references(), context.clip());
        requireRegistry(context.carrier().registry());
        for (Mc263StructureCarrier.ValidStart start : context.carrier().resolveStarts(
                context.references(), Mc263JunglePyramidProgram.STRUCTURE_ID)) {
            Mc263JunglePyramidStartGenerator.loadPersisted(context.worldSeed(), start);
        }
    }

    @Override
    public void place(Mc263CanonicalFeaturesProducerSkeleton.StructurePlacementInput context) {
        Objects.requireNonNull(context, "jungle-pyramid placement context");
        Mc263FeatureDispatcher.StructurePlacementContext dispatcher = context.dispatcher();
        requireDispatcher(dispatcher, context.references());
        requireSourceBinding(context.references(), context.clip());

        List<String> startKeys = context.carrier().resolveStarts(context.references(),
                Mc263JunglePyramidProgram.STRUCTURE_ID).stream()
                .map(Mc263StructureCarrier.ValidStart::startKey).toList();
        for (String startKey : startKeys) {
            Mc263StructureCarrier predecessorCarrier = context.carrier();
            Mc263StructureCarrier.ValidStart start = predecessorCarrier.resolveStarts(
                    context.references(), Mc263JunglePyramidProgram.STRUCTURE_ID).stream()
                    .filter(candidate -> candidate.startKey().equals(startKey))
                    .findFirst().orElseThrow(() -> new IllegalStateException(
                            "evolved jungle-pyramid carrier lost referenced start"));
            var loaded = Mc263JunglePyramidStartGenerator.loadPersisted(
                    dispatcher.worldSeed(), start);
            CapturingRegionWorld world = new CapturingRegionWorld(dispatcher.region());
            Mc263JunglePyramidProgram.ExecutionResult execution =
                    Mc263JunglePyramidProgram.execute(loaded.hardcodedCarrier(),
                            programClip(context.clip()), world, dispatcher.random());
            Mc263JunglePyramidProgram.AtomicSettlement settlement = world.requireSettlement();
            requireSuccessorOf(loaded.hardcodedCarrier(), execution.successor());
            requireSettlementIdentity(loaded.hardcodedCarrier(), execution, settlement);
            Mc263StructureCarrier successorCarrier = rebuild(
                    predecessorCarrier, startKey, execution.successor());
            context.commitStructureBatch(predecessorCarrier, successorCarrier,
                    structureBatch(settlement, startKey, context));
        }
    }

    private static void requireSchedule(Mc263StructureIndexReceipt.Entry entry) {
        if (entry.step() != STEP || entry.index() != INDEX
                || !entry.key().equals(Mc263JunglePyramidProgram.STRUCTURE_ID)
                || entry.biomeMask() != BIOME_MASK) {
            throw new IllegalArgumentException("jungle-pyramid schedule mismatch");
        }
    }

    private static void requireRegistry(Mc263StructureCarrier.Registry registry) {
        Mc263StructureCarrier.StructureDefinition definition = registry.require(
                Mc263JunglePyramidProgram.STRUCTURE_ID);
        int ordinal = Mc263StructureIndexReceipt.entries().stream()
                .map(Mc263StructureIndexReceipt.Entry::key).toList()
                .indexOf(Mc263JunglePyramidProgram.STRUCTURE_ID);
        if (definition.registryOrdinal() != ordinal || definition.decorationStep() != STEP
                || definition.terrainAdjustment()
                        != Mc263StructureCarrier.TerrainAdjustment.NONE) {
            throw new IllegalArgumentException("jungle-pyramid carrier registry mismatch");
        }
    }

    private static void requireSourceBinding(Mc263StructureCarrier.ChunkReferences references,
            Mc263CanonicalFeaturesProducerSkeleton.SourceClip clip) {
        if (!Mc263CanonicalFeaturesProducerSkeleton.SourceClip.chunk(
                references.chunkX(), references.chunkZ()).equals(clip)) {
            throw new IllegalArgumentException("jungle-pyramid source clip mismatch");
        }
    }

    private static void requireDispatcher(
            Mc263FeatureDispatcher.StructurePlacementContext dispatcher,
            Mc263StructureCarrier.ChunkReferences references) {
        if (dispatcher.step() != STEP || dispatcher.globalIndex() != INDEX
                || !dispatcher.structureKey().equals(Mc263JunglePyramidProgram.STRUCTURE_ID)
                || dispatcher.sourceChunkX() != references.chunkX()
                || dispatcher.sourceChunkZ() != references.chunkZ()) {
            throw new IllegalArgumentException("jungle-pyramid dispatcher/source mismatch");
        }
    }

    private static Mc263JunglePyramidProgram.Clip programClip(
            Mc263CanonicalFeaturesProducerSkeleton.SourceClip clip) {
        return new Mc263JunglePyramidProgram.Clip(clip.minX(), clip.minY(), clip.minZ(),
                clip.maxX(), clip.maxY(), clip.maxZ());
    }

    static Mc263FeaturesRegion.StructureBatch structureBatch(
            Mc263JunglePyramidProgram.AtomicSettlement settlement, String startKey,
            Mc263CanonicalFeaturesProducerSkeleton.StructurePlacementInput placement) {
        long owner = Mc263StructureOwner.owner(
                Mc263JunglePyramidProgram.STRUCTURE_ID, startKey);
        List<Mc263FeaturesRegion.StructureBlockWrite> blocks = settlement.blocks().stream()
                .map(value -> new Mc263FeaturesRegion.StructureBlockWrite(value.position().x(),
                        value.position().y(), value.position().z(), value.exactState(), owner))
                .toList();
        List<Mc263FeaturesRegion.StructureLoot> loot = settlement.loot().stream()
                .filter(value -> placement.clip().contains(value.position().x(),
                        value.position().y(), value.position().z()))
                .map(value -> new Mc263FeaturesRegion.StructureLoot(value.position().x(),
                        value.position().y(), value.position().z(), value.table(), value.seed(),
                        placement.productionContext(value.position().x(), value.position().y(),
                                value.position().z(), value.table())))
                .toList();
        List<Mc263FeaturesRegion.StructureBentEvidence> blockEntities = settlement.loot().stream()
                .filter(value -> value.table().equals(
                        Mc263JunglePyramidProgram.DISPENSER_LOOT))
                .map(value -> new Mc263FeaturesRegion.StructureBentEvidence(
                        value.position().x(), value.position().y(), value.position().z(),
                        "minecraft:dispenser", "minecraft:dispenser",
                        canonicalDispenserNbt(value.table(), value.seed())))
                .toList();
        return new Mc263FeaturesRegion.StructureBatch(
                blocks, loot, List.of(), blockEntities);
    }

    record Settlement(CanonicalWorldgenStore.CanonicalChunkSnapshot snapshot,
            byte[] tejpSuccessor, int writes,
            List<Mc263JunglePyramidProgram.LootMutation> orderedLoot) {
        Settlement {
            Objects.requireNonNull(snapshot, "jungle-pyramid snapshot");
            tejpSuccessor = Objects.requireNonNull(
                    tejpSuccessor, "minecraft:tejp successor").clone();
            if (writes < 0) throw new IllegalArgumentException("negative jungle-pyramid writes");
            orderedLoot = List.copyOf(orderedLoot);
        }
        @Override public byte[] tejpSuccessor() { return tejpSuccessor.clone(); }
    }

    static Settlement executeAndCommit(CanonicalWorldgenStore store, long worldId,
            String worldIdentity, byte[] fingerprint,
            Mc263StructureCarrier structureCarrier,
            Mc263StructureCarrier.ChunkReferences references,
            Mc263HardcodedStructureCarrier predecessor,
            Mc263FinalChunkCodec.FinalChunk targetChunk,
            WorldgenRandom structureRandom) {
        Objects.requireNonNull(store, "canonical worldgen store");
        Objects.requireNonNull(fingerprint, "jungle-pyramid fingerprint");
        Objects.requireNonNull(structureCarrier, "STR263C1 carrier");
        Objects.requireNonNull(references, "jungle-pyramid references");
        Objects.requireNonNull(predecessor, "jungle-pyramid predecessor");
        Objects.requireNonNull(targetChunk, "jungle-pyramid target chunk");
        Objects.requireNonNull(structureRandom, "jungle-pyramid structure RNG");
        if (fingerprint.length != 32) {
            throw new IllegalArgumentException("jungle-pyramid fingerprint must be SHA-256");
        }
        Binding binding = requireBinding(
                structureCarrier, references, predecessor, targetChunk);

        CanonicalWorldgenStore.CanonicalChunkSnapshot existing = store.find(
                worldId, targetChunk.chunkX(), targetChunk.chunkZ());
        if (existing != null) {
            if (!Arrays.equals(existing.commit().fingerprint(), fingerprint)) {
                throw new IllegalStateException("jungle-pyramid canonical replay conflict");
            }
            byte[] successor = existing.commit().mutablePieceSuccessor();
            if (successor == null || successor.length == 0) {
                throw new IllegalStateException("missing canonical minecraft:tejp successor");
            }
            Mc263StructureCarrier persisted = Mc263StructureCarrier.decode(
                    existing.commit().structureCarrier());
            List<Mc263StructureCarrier.ValidStart> persistedStarts = persisted.resolveStarts(
                    persisted.referenceChunk(targetChunk.chunkX(), targetChunk.chunkZ())
                            .orElseThrow(() -> new IllegalStateException(
                                    "missing persisted jungle-pyramid target references")),
                    Mc263JunglePyramidProgram.STRUCTURE_ID);
            List<Mc263StructureCarrier.ValidStart> persistedBound = persistedStarts.stream()
                    .filter(value -> value.originChunkX() == predecessor.chunkX()
                            && value.originChunkZ() == predecessor.chunkZ()).toList();
            if (persistedStarts.stream().map(Mc263StructureCarrier.ValidStart::startKey)
                    .distinct().count() != persistedStarts.size()
                    || persistedBound.size() != 1
                    || persistedBound.getFirst().orderedPieces().size() != 1
                    || !Arrays.equals(successor, persistedBound.getFirst().orderedPieces()
                            .getFirst().persistedPayload().binaryNbtCompound())) {
                throw new IllegalStateException(
                        "canonical minecraft:tejp successor/STR conflict");
            }
            return new Settlement(existing, successor, 0, List.of());
        }

        CapturingWorld world = new CapturingWorld(targetChunk);
        Mc263JunglePyramidProgram.ExecutionResult execution =
                Mc263JunglePyramidProgram.execute(predecessor,
                        clip(targetChunk.chunkX(), targetChunk.chunkZ()), world,
                        structureRandom);
        Mc263JunglePyramidProgram.AtomicSettlement mutations = world.requireSettlement();
        requireSuccessorOf(predecessor, execution.successor());
        requireSettlementIdentity(predecessor, execution, mutations);

        Mc263FinalChunkCodec.FinalChunk successorChunk = apply(targetChunk, mutations);
        Mc263StructureCarrier successorCarrier = rebuild(
                structureCarrier, binding.startKey(), execution.successor());
        byte[] mutableSuccessor = Mc263JunglePyramidProgram
                .canonicalPieceNbt(execution.successor());
        CanonicalWorldgenStore.CanonicalChunkSnapshot committed = store.commit(
                new CanonicalWorldgenStore.ChunkCommit(worldId, worldIdentity,
                        targetChunk.chunkX(), targetChunk.chunkZ(),
                        Mc263FinalChunkCodec.encode(successorChunk),
                        successorCarrier.receiptBytes(), mutableSuccessor, fingerprint));
        return new Settlement(committed, mutableSuccessor, execution.writes(), mutations.loot());
    }

    /**
     * The referenced start this settlement's predecessor was loaded from.
     *
     * <p>{@code ChunkGenerator#applyBiomeDecoration} binds one feature seed per (chunk, structure,
     * decoration step) and then places every element of {@code
     * StructureManager#startsForStructure(SectionPos, Structure)} -- a {@code List} drained from
     * the chunk's whole {@code LongSet} of references -- through one shared {@code WorldgenRandom}.
     * Two jungle pyramids whose adjusted boxes both reach one chunk are therefore authentic, so the
     * closure is not capped at one member; the binding is taken from the start the execution ran
     * on. Only a repeated start is rejected, because {@code ChunkStarts} holds at most one start per
     * (origin chunk, structure) and {@code ReferenceSet} forbids a duplicate origin, so a repeat
     * proves a corrupt closure rather than two pyramids.</p>
     */
    private static Mc263StructureCarrier.ValidStart boundStart(Mc263StructureCarrier carrier,
            Mc263StructureCarrier.ChunkReferences references,
            Mc263HardcodedStructureCarrier predecessor) {
        List<Mc263StructureCarrier.ValidStart> starts = carrier.resolveStarts(
                references, Mc263JunglePyramidProgram.STRUCTURE_ID);
        if (starts.stream().map(Mc263StructureCarrier.ValidStart::startKey)
                .distinct().count() != starts.size()) {
            throw new IllegalArgumentException(
                    "jungle-pyramid reference closure repeats a start");
        }
        List<Mc263StructureCarrier.ValidStart> bound = starts.stream()
                .filter(value -> value.originChunkX() == predecessor.chunkX()
                        && value.originChunkZ() == predecessor.chunkZ()).toList();
        if (bound.size() != 1) {
            throw new IllegalArgumentException("jungle-pyramid start evidence set mismatch");
        }
        return bound.getFirst();
    }

    private static Binding requireBinding(Mc263StructureCarrier carrier,
            Mc263StructureCarrier.ChunkReferences references,
            Mc263HardcodedStructureCarrier predecessor,
            Mc263FinalChunkCodec.FinalChunk targetChunk) {
        requireShape(predecessor);
        if (references.chunkX() != targetChunk.chunkX()
                || references.chunkZ() != targetChunk.chunkZ()) {
            throw new IllegalArgumentException("jungle-pyramid target chunk mismatch");
        }
        Mc263StructureCarrier.ValidStart start = boundStart(carrier, references, predecessor);
        if (start.originChunkX() != predecessor.chunkX()
                || start.originChunkZ() != predecessor.chunkZ()
                || start.orderedPieces().size() != 1) {
            throw new IllegalArgumentException("jungle-pyramid start/predecessor mismatch");
        }
        Mc263StructureCarrier.Piece piece = start.orderedPieces().getFirst();
        if (!piece.pieceType().equals(Mc263JunglePyramidProgram.PIECE_TYPE)
                || piece.poolElement()
                || piece.projection() != Mc263StructureCarrier.Projection.NOT_APPLICABLE
                || piece.groundLevelDelta() != 0 || !piece.junctions().isEmpty()
                || !Arrays.equals(piece.persistedPayload().binaryNbtCompound(),
                        Mc263JunglePyramidProgram.canonicalPieceNbt(predecessor))) {
            throw new IllegalArgumentException("noncanonical minecraft:tejp start piece");
        }
        int minX = Math.multiplyExact(references.chunkX(), 16);
        int minZ = Math.multiplyExact(references.chunkZ(), 16);
        var box = predecessor.boundingBox();
        if (box.maxX() < minX || box.minX() > minX + 15
                || box.maxZ() < minZ || box.minZ() > minZ + 15) {
            throw new IllegalArgumentException("jungle-pyramid reference misses target chunk");
        }
        return new Binding(start.startKey());
    }

    private static void requireShape(Mc263HardcodedStructureCarrier carrier) {
        if (carrier.kind() != Kind.JUNGLE_PYRAMID
                || carrier.orderedPieces().size() != 1
                || carrier.orderedPieces().getFirst().kind() != PieceKind.JUNGLE_PYRAMID
                || carrier.orderedPieces().getFirst().template() != null) {
            throw new IllegalArgumentException("noncanonical jungle-pyramid carrier shape");
        }
    }

    private static void requireSuccessorOf(Mc263HardcodedStructureCarrier predecessor,
            Mc263HardcodedStructureCarrier successor) {
        requireShape(successor);
        if (!Arrays.equals(successor.encodeCanonical(), predecessor
                .withSuccessor(successor.successor()).encodeCanonical())) {
            throw new IllegalArgumentException("jungle-pyramid successor/predecessor mismatch");
        }
    }

    private static void requireSettlementIdentity(Mc263HardcodedStructureCarrier predecessor,
            Mc263JunglePyramidProgram.ExecutionResult execution,
            Mc263JunglePyramidProgram.AtomicSettlement settlement) {
        String expected = Mc263JunglePyramidProgram.STRUCTURE_ID + ":"
                + predecessor.worldSeed() + ":" + predecessor.chunkX() + ":"
                + predecessor.chunkZ() + ":"
                + execution.successor().successor().heightPosition();
        if (!settlement.identity().equals(expected)
                || settlement.blocks().size() != execution.writes()
                || settlement.loot().size() != execution.lootSidecars()) {
            throw new IllegalArgumentException("jungle-pyramid settlement evidence mismatch");
        }
        List<String> tables = settlement.loot().stream()
                .map(Mc263JunglePyramidProgram.LootMutation::table).toList();
        List<String> pinned = List.of(Mc263JunglePyramidProgram.DISPENSER_LOOT,
                Mc263JunglePyramidProgram.DISPENSER_LOOT,
                Mc263JunglePyramidProgram.CHEST_LOOT,
                Mc263JunglePyramidProgram.CHEST_LOOT);
        int cursor = 0;
        for (String table : tables) {
            while (cursor < pinned.size() && !pinned.get(cursor).equals(table)) cursor++;
            if (cursor == pinned.size()) {
                throw new IllegalArgumentException("jungle-pyramid LOOT order mismatch");
            }
            cursor++;
        }
    }

    private static Mc263FinalChunkCodec.FinalChunk apply(
            Mc263FinalChunkCodec.FinalChunk source,
            Mc263JunglePyramidProgram.AtomicSettlement settlement) {
        Mc263FinalChunkCodec.validate(source);
        requireAuthenticatedLootRows(source, settlement);
        short[] ids = source.blockIds();
        Map<Integer, Mc263FeatureBlockState> states =
                new LinkedHashMap<>(source.stateOverrides());
        for (var mutation : settlement.blocks()) {
            int packed = packed(source, mutation.position());
            Mc263FeatureBlockState state = Mc263FeatureBlockState
                    .fromExact(mutation.exactState());
            ids[packed] = (short) state.blockId();
            Mc263FeatureBlockState defaultState =
                    Mc263FeatureBlockState.defaultForId(state.blockId());
            if (defaultState.exactState().equals(state.exactState())) states.remove(packed);
            else states.put(packed, state);
        }

        Mc263FinalChunkSidecars old = source.sidecars();
        ArrayList<Mc263FinalChunkSidecars.Loot> loot = new ArrayList<>(old.loot());
        TreeMap<Integer, Mc263FinalChunkSidecars.BlockEntity> blockEntities = new TreeMap<>();
        for (var existing : old.blockEntities()) {
            if (blockEntities.put(existing.packed(), existing) != null) {
                throw new IllegalArgumentException(
                        "duplicate existing jungle-pyramid BENT position");
            }
        }
        for (var mutation : settlement.loot()) {
            int packed = packed(source, mutation.position());
            Mc263FeatureBlockState state = states.getOrDefault(packed,
                    Mc263FeatureBlockState.defaultForId(Short.toUnsignedInt(ids[packed])));
            Mc263FinalChunkSidecars.Loot addition = new Mc263FinalChunkSidecars.Loot(
                    packed, state.chestFacing(), mutation.table(), mutation.seed());
            Mc263FinalChunkSidecars.Loot existing = loot.stream()
                    .filter(value -> value.packed() == packed).findFirst().orElse(null);
            if (existing == null) loot.add(addition);
            else if (!existing.equals(addition)) {
                throw new IllegalArgumentException(
                        "jungle-pyramid LOOT conflicts with existing sidecar");
            }
            if (mutation.table().equals(Mc263JunglePyramidProgram.DISPENSER_LOOT)) {
                if (!state.blockKey().equals("minecraft:dispenser")) {
                    throw new IllegalArgumentException(
                            "jungle-pyramid dispenser LOOT block evidence mismatch");
                }
                Mc263FinalChunkSidecars.BlockEntity bent =
                        new Mc263FinalChunkSidecars.BlockEntity(packed,
                                "minecraft:dispenser", "minecraft:dispenser",
                                canonicalDispenserNbt(mutation.table(), mutation.seed()));
                Mc263FinalChunkSidecars.BlockEntity prior = blockEntities.putIfAbsent(
                        packed, bent);
                if (prior != null && !prior.equals(bent)) {
                    throw new IllegalArgumentException(
                            "jungle-pyramid BENT conflicts with existing sidecar");
                }
            }
        }
        Mc263FinalChunkSidecars sidecars = new Mc263FinalChunkSidecars(
                old.blockTicks(), old.fluidTicks(), loot, old.spawners(), old.owners(),
                old.archaeology(), old.bees(), List.copyOf(blockEntities.values()),
                old.entities(), old.containerLootDeclarations());
        int[] worldSurface = new int[256];
        int[] oceanFloor = new int[256];
        int[] motionBlocking = new int[256];
        rebuildHeightmaps(ids, states, worldSurface, oceanFloor, motionBlocking);
        Mc263FinalChunkCodec.FinalChunk result = new Mc263FinalChunkCodec.FinalChunk(
                source.chunkX(), source.chunkZ(), ids, states, worldSurface,
                oceanFloor, motionBlocking, sidecars);
        Mc263FinalChunkCodec.encode(result);
        return result;
    }

    private static void requireAuthenticatedLootRows(
            Mc263FinalChunkCodec.FinalChunk source,
            Mc263JunglePyramidProgram.AtomicSettlement settlement) {
        for (var mutation : settlement.loot()) {
            int packed = packed(source, mutation.position());
            String finalExactState = null;
            for (var block : settlement.blocks()) {
                if (block.position().equals(mutation.position())) {
                    finalExactState = block.exactState();
                }
            }
            Mc263FeatureBlockState state = finalExactState == null
                    ? source.stateAt(packed)
                    : Mc263FeatureBlockState.fromExact(finalExactState);
            Mc263FinalChunkSidecars.Loot row = new Mc263FinalChunkSidecars.Loot(
                    packed, state.chestFacing(), mutation.table(), mutation.seed());
            if (!source.sidecars().loot().contains(row)) {
                throw new IllegalStateException(
                        "jungle-pyramid LDEC upstream handoff missing: "
                                + "Mc263FinalChunkAssembler must provide the authenticated "
                                + "LOOT row and declaration before settlement");
            }
        }
    }

    /** Exact custom payload written by RandomizableContainer.trySaveLootTable. */
    private static byte[] canonicalDispenserNbt(String table, long seed) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(96);
            try (DataOutputStream output = new DataOutputStream(bytes)) {
                output.writeByte(10); output.writeShort(0);
                output.writeByte(8); output.writeUTF("LootTable"); output.writeUTF(table);
                if (seed != 0L) {
                    output.writeByte(4); output.writeUTF("LootTableSeed");
                    output.writeLong(seed);
                }
                output.writeByte(0);
            }
            return bytes.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "in-memory jungle-pyramid dispenser NBT failed", exception);
        }
    }

    private static void rebuildHeightmaps(short[] ids,
            Map<Integer, Mc263FeatureBlockState> states, int[] worldSurface,
            int[] oceanFloor, int[] motionBlocking) {
        Arrays.fill(worldSurface, Blocks.MIN_Y);
        Arrays.fill(oceanFloor, Blocks.MIN_Y);
        Arrays.fill(motionBlocking, Blocks.MIN_Y);
        for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
            int column = x + z * 16;
            for (int y = Blocks.MAX_Y; y >= Blocks.MIN_Y; y--) {
                int packed = Blocks.blockIndex(x, y, z);
                Mc263FeatureBlockState state = states.getOrDefault(packed,
                        Mc263FeatureBlockState.defaultForId(
                                Short.toUnsignedInt(ids[packed])));
                if (worldSurface[column] == Blocks.MIN_Y && !state.isAir()) {
                    worldSurface[column] = y + 1;
                }
                if (oceanFloor[column] == Blocks.MIN_Y && state.isSolid()) {
                    oceanFloor[column] = y + 1;
                }
                if (motionBlocking[column] == Blocks.MIN_Y
                        && (state.blocksMotionInHeightmapNoLeaves()
                                || state.fluidAmount() > 0)) {
                    motionBlocking[column] = y + 1;
                }
                if (worldSurface[column] != Blocks.MIN_Y
                        && oceanFloor[column] != Blocks.MIN_Y
                        && motionBlocking[column] != Blocks.MIN_Y) break;
            }
        }
    }

    private static int packed(Mc263FinalChunkCodec.FinalChunk chunk,
            Mc263JunglePyramidProgram.BlockPos position) {
        if (Math.floorDiv(position.x(), 16) != chunk.chunkX()
                || Math.floorDiv(position.z(), 16) != chunk.chunkZ()
                || position.y() < Blocks.MIN_Y || position.y() > Blocks.MAX_Y) {
            throw new IllegalArgumentException(
                    "jungle-pyramid mutation outside target chunk");
        }
        return Blocks.blockIndex(Math.floorMod(position.x(), 16), position.y(),
                Math.floorMod(position.z(), 16));
    }

    private static Mc263StructureCarrier rebuild(Mc263StructureCarrier carrier,
            String selectedStart, Mc263HardcodedStructureCarrier successor) {
        byte[] payload = Mc263JunglePyramidProgram.canonicalPieceNbt(successor);
        var sourceBox = successor.boundingBox();
        int delta = Math.subtractExact(successor.successor().heightPosition(), sourceBox.minY());
        var box = new Mc263StructureCarrier.BoundingBox(sourceBox.minX(),
                Math.addExact(sourceBox.minY(), delta), sourceBox.minZ(), sourceBox.maxX(),
                Math.addExact(sourceBox.maxY(), delta), sourceBox.maxZ());
        ArrayList<Mc263StructureCarrier.ChunkStarts> chunks = new ArrayList<>();
        for (var chunk : carrier.startChunks()) {
            ArrayList<Mc263StructureCarrier.StartEntry> starts = new ArrayList<>();
            for (var entry : chunk.orderedStarts()) {
                if (!(entry.body() instanceof Mc263StructureCarrier.ValidStart start)
                        || !start.startKey().equals(selectedStart)) {
                    starts.add(entry);
                    continue;
                }
                var piece = new Mc263StructureCarrier.Piece(
                        Mc263JunglePyramidProgram.PIECE_TYPE, box, false,
                        Mc263StructureCarrier.Projection.NOT_APPLICABLE, 0, List.of(),
                        new Mc263StructureCarrier.PiecePayload(payload));
                starts.add(new Mc263StructureCarrier.StartEntry(entry.structureId(),
                        new Mc263StructureCarrier.ValidStart(start.startKey(),
                                start.originChunkX(), start.originChunkZ(), start.references(),
                                box, List.of(piece))));
            }
            chunks.add(chunk.withStarts(starts));
        }
        return new Mc263StructureCarrier(carrier.registry(), chunks,
                carrier.referenceChunks(), carrier.rawStartPayloads(),
                carrier.producerGraphPayloads()).strictlyDecoded();
    }

    private static Mc263JunglePyramidProgram.Clip clip(int chunkX, int chunkZ) {
        int minX = Math.multiplyExact(chunkX, 16);
        int minZ = Math.multiplyExact(chunkZ, 16);
        return new Mc263JunglePyramidProgram.Clip(minX, Blocks.MIN_Y, minZ,
                Math.addExact(minX, 15), Blocks.MAX_Y, Math.addExact(minZ, 15));
    }

    private record Binding(String startKey) { }

    private static final class CapturingRegionWorld implements
            Mc263JunglePyramidProgram.WorldAccess {
        private final Mc263FeaturesRegion region;
        private Mc263JunglePyramidProgram.AtomicSettlement settlement;

        private CapturingRegionWorld(Mc263FeaturesRegion region) {
            this.region = Objects.requireNonNull(region, "jungle-pyramid region");
        }

        @Override public boolean supportsExactState(String state) {
            return Mc263FeatureBlockState.supportsExactState(state);
        }
        @Override public boolean supportsMotionBlockingNoLeavesHeight() { return true; }
        @Override public boolean supportsSolidRenderQuery() { return true; }
        @Override public boolean supportsLootSidecar(String table) {
            return table.equals(Mc263JunglePyramidProgram.CHEST_LOOT)
                    || table.equals(Mc263JunglePyramidProgram.DISPENSER_LOOT);
        }
        @Override public boolean supportsAtomicSettlement() { return true; }
        @Override public int motionBlockingNoLeaves(int x, int z) {
            return region.motionBlocking(x, z);
        }
        @Override public String blockState(Mc263JunglePyramidProgram.BlockPos position) {
            return region.blockState(position.x(), position.y(), position.z()).exactState();
        }
        @Override public boolean isSolidRender(Mc263JunglePyramidProgram.BlockPos position,
                String exactState) {
            return Mc263FeatureBlockState.fromExact(exactState).isSolidRender();
        }
        @Override public void settle(Mc263JunglePyramidProgram.AtomicSettlement value) {
            if (settlement != null) {
                throw new IllegalStateException("duplicate jungle-pyramid atomic settlement");
            }
            settlement = Objects.requireNonNull(value, "jungle-pyramid atomic settlement");
        }

        private Mc263JunglePyramidProgram.AtomicSettlement requireSettlement() {
            if (settlement == null) {
                throw new IllegalStateException("missing jungle-pyramid atomic settlement");
            }
            return settlement;
        }
    }

    private static final class CapturingWorld implements Mc263JunglePyramidProgram.WorldAccess {
        private final Mc263FinalChunkCodec.FinalChunk chunk;
        private final short[] blocks;
        private final Map<Integer, Mc263FeatureBlockState> states;
        private final int[] motionBlocking;
        private Mc263JunglePyramidProgram.AtomicSettlement settlement;

        private CapturingWorld(Mc263FinalChunkCodec.FinalChunk chunk) {
            this.chunk = chunk;
            blocks = chunk.blockIds();
            states = chunk.stateOverrides();
            motionBlocking = chunk.motionBlocking();
        }

        @Override public boolean supportsExactState(String state) {
            return Mc263FeatureBlockState.supportsExactState(state);
        }
        @Override public boolean supportsMotionBlockingNoLeavesHeight() { return true; }
        @Override public boolean supportsSolidRenderQuery() { return true; }
        @Override public boolean supportsLootSidecar(String table) {
            return table.equals(Mc263JunglePyramidProgram.CHEST_LOOT)
                    || table.equals(Mc263JunglePyramidProgram.DISPENSER_LOOT);
        }
        @Override public boolean supportsAtomicSettlement() { return true; }
        @Override public int motionBlockingNoLeaves(int x, int z) {
            requireTargetColumn(x, z);
            return motionBlocking[Math.floorMod(x, 16)
                    + Math.floorMod(z, 16) * 16];
        }
        @Override public String blockState(Mc263JunglePyramidProgram.BlockPos position) {
            int packed = packed(chunk, position);
            Mc263FeatureBlockState override = states.get(packed);
            return (override != null ? override : Mc263FeatureBlockState.defaultForId(
                    Short.toUnsignedInt(blocks[packed]))).exactState();
        }
        @Override public boolean isSolidRender(Mc263JunglePyramidProgram.BlockPos position,
                String exactState) {
            packed(chunk, position);
            return Mc263FeatureBlockState.fromExact(exactState).isSolidRender();
        }
        @Override public void settle(Mc263JunglePyramidProgram.AtomicSettlement value) {
            if (settlement != null) {
                throw new IllegalStateException("duplicate jungle-pyramid atomic settlement");
            }
            settlement = Objects.requireNonNull(value, "jungle-pyramid atomic settlement");
        }

        private void requireTargetColumn(int x, int z) {
            if (Math.floorDiv(x, 16) != chunk.chunkX()
                    || Math.floorDiv(z, 16) != chunk.chunkZ()) {
                throw new IllegalArgumentException(
                        "jungle-pyramid observation outside target chunk");
            }
        }

        private Mc263JunglePyramidProgram.AtomicSettlement requireSettlement() {
            if (settlement == null) {
                throw new IllegalStateException("missing jungle-pyramid atomic settlement");
            }
            return settlement;
        }
    }
}
