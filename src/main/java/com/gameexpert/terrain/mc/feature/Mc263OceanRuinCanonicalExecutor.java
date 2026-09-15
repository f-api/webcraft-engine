package com.gameexpert.terrain.mc.feature;

import com.gameexpert.terrain.Blocks;
import com.gameexpert.terrain.mc.ore.Mc263DecorationRandom;
import com.gameexpert.terrain.mc.structure.Mc263OceanRuinProgram;
import com.gameexpert.terrain.mc.structure.Mc263OceanRuinProgram.BlockPos;
import com.gameexpert.terrain.mc.structure.Mc263OceanRuinProgram.LegacyRandom;
import com.gameexpert.terrain.mc.structure.Mc263OceanRuinProgram.PiecePlan;
import com.gameexpert.terrain.mc.structure.Mc263OceanRuinProgram.Plan;
import com.gameexpert.terrain.mc.structure.Mc263OceanRuinProgram.RngContinuation;
import com.gameexpert.terrain.mc.structure.Mc263OceanRuinProgram.Rotation;
import com.gameexpert.terrain.mc.structure.Mc263OceanRuinProgram.Type;
import com.gameexpert.terrain.mc.structure.Mc263OceanRuinProductionExecutor;
import com.gameexpert.terrain.mc.structure.Mc263OceanRuinSettlement;
import com.gameexpert.terrain.mc.structure.Mc263OceanRuinTemplateCatalog;
import com.gameexpert.terrain.mc.structure.Mc263StructureCarrier;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Dormant canonical adapters for the accepted cold and warm Ocean Ruin producers. */
final class Mc263OceanRuinCanonicalExecutor implements
        Mc263CanonicalFeaturesProducerSkeleton.CanonicalStructureExecutor {
    private static final int STEP = 4;
    private static final int COLD_INDEX = 25;
    private static final int WARM_INDEX = 26;
    private static final int COLD_ORDINAL = 30;
    private static final int WARM_ORDINAL = 31;
    private static final long COLD_MASK = 0x0000000000007eL;
    private static final long WARM_MASK = 0x00000000000380L;
    private static final List<String> REQUIRED_STATES = List.of(
            "minecraft:air", "minecraft:water", "minecraft:barrier[waterlogged=false]",
            "minecraft:suspicious_gravel[dusted=0]",
            "minecraft:suspicious_sand[dusted=0]",
            "minecraft:chest[facing=north,type=single,waterlogged=false]",
            "minecraft:chest[facing=north,type=single,waterlogged=true]");

    private final Type type;
    private final String key;
    private final int index;
    private final int ordinal;
    private final long biomeMask;
    private final Capabilities capabilities;

    private Mc263OceanRuinCanonicalExecutor(Type type, int index, int ordinal,
            long biomeMask, Capabilities capabilities) {
        this.type = type;
        this.key = type == Type.COLD ? Mc263OceanRuinProgram.COLD_STRUCTURE
                : Mc263OceanRuinProgram.WARM_STRUCTURE;
        this.index = index;
        this.ordinal = ordinal;
        this.biomeMask = biomeMask;
        this.capabilities = Objects.requireNonNull(capabilities, "ocean-ruin capabilities");
    }

    static void register(
            Mc263CanonicalFeaturesProducerSkeleton.CanonicalStructureExecutorRegistry registry) {
        Objects.requireNonNull(registry, "canonical structure executor registry")
                .register(Mc263OceanRuinProgram.COLD_STRUCTURE, cold())
                .register(Mc263OceanRuinProgram.WARM_STRUCTURE, warm());
    }

    static Mc263OceanRuinCanonicalExecutor cold() {
        return new Mc263OceanRuinCanonicalExecutor(Type.COLD, COLD_INDEX, COLD_ORDINAL,
                COLD_MASK, Capabilities.COMPLETE);
    }

    static Mc263OceanRuinCanonicalExecutor warm() {
        return new Mc263OceanRuinCanonicalExecutor(Type.WARM, WARM_INDEX, WARM_ORDINAL,
                WARM_MASK, Capabilities.COMPLETE);
    }

    static Mc263OceanRuinCanonicalExecutor fixture(Type type, boolean exactStates,
            boolean atomicStr, boolean schema4) {
        return new Mc263OceanRuinCanonicalExecutor(type,
                type == Type.COLD ? COLD_INDEX : WARM_INDEX,
                type == Type.COLD ? COLD_ORDINAL : WARM_ORDINAL,
                type == Type.COLD ? COLD_MASK : WARM_MASK,
                new Capabilities(exactStates, atomicStr, schema4));
    }

    @Override
    public void preflight(
            Mc263CanonicalFeaturesProducerSkeleton.StructurePreflightContext context) {
        Objects.requireNonNull(context, "ocean-ruin preflight context");
        requireCapabilities();
        requireSchedule(context.entry(), context.carrier().registry());
        requireSourceBinding(context.references(), context.clip());
        validateAllStarts(context.carrier());
        for (Mc263StructureCarrier.ValidStart start : context.carrier().resolveStarts(
                context.references(), key)) replay(start);
    }

    @Override
    public void place(Mc263CanonicalFeaturesProducerSkeleton.StructurePlacementInput context) {
        Objects.requireNonNull(context, "ocean-ruin placement context");
        requireCapabilities();
        Mc263FeatureDispatcher.StructurePlacementContext dispatcher = context.dispatcher();
        requireDispatcher(dispatcher, context.references(), context.clip());
        requireSchedule(Mc263StructureIndexReceipt.step(STEP).get(index),
                context.carrier().registry());

        List<String> starts = context.carrier().resolveStarts(context.references(), key).stream()
                .map(Mc263StructureCarrier.ValidStart::startKey).toList();
        for (String startKey : starts) {
            Prepared prepared = prepare(context, startKey);
            context.commitStructureBatch(prepared.predecessor(), prepared.successor(),
                    prepared.batch());
            advanceDispatcher(dispatcher.random(), prepared.draws());
        }
    }

    /** Complete immutable handoff used by the orchestrator's later shared-registry wiring. */
    Prepared prepare(Mc263CanonicalFeaturesProducerSkeleton.StructurePlacementInput context,
            String startKey) {
        requireCapabilities();
        Mc263FeatureDispatcher.StructurePlacementContext dispatcher = context.dispatcher();
        requireDispatcher(dispatcher, context.references(), context.clip());
        Mc263StructureCarrier predecessor = context.carrier();
        Mc263StructureCarrier.ChunkReferences references = context.references();
        Mc263StructureCarrier.ValidStart persisted = predecessor.resolveStarts(references, key)
                .stream().filter(value -> value.startKey().equals(startKey)).findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "evolved ocean-ruin carrier lost referenced start"));
        Plan projected = replay(persisted);
        Mc263FinalChunkCodec.FinalChunk source = sourceChunk(dispatcher.region(),
                references.chunkX(), references.chunkZ());
        LegacyRandom candidate = new LegacyRandom(dispatcher.featureSeed());
        Mc263OceanRuinSettlement.Execution execution = type == Type.COLD
                ? Mc263OceanRuinProductionExecutor.prepareCold(dispatcher.worldSeed(), projected,
                        terrain(dispatcher.region()), candidate, clip(context.clip()))
                : Mc263OceanRuinProductionExecutor.prepareWarm(dispatcher.worldSeed(), projected,
                        terrain(dispatcher.region()), candidate, clip(context.clip()));
        Mc263OceanRuinSettlement.FeaturesSettlement settlement =
                Mc263OceanRuinSettlement.settleFeatures(execution, persisted.references(), source);
        Mc263StructureCarrier successor = replaceStart(predecessor, settlement.successorStart());
        return new Prepared(predecessor, successor,
                batch(source, settlement.semanticChunk(), key, startKey, context),
                candidate.drawCount());
    }

    private void requireCapabilities() {
        if (!capabilities.exactStates || !capabilities.atomicStr || !capabilities.schema4) {
            throw new UnsupportedOperationException(
                    key + " STR/schema4 LOOT/ARCH/BENT/ENTS capability is absent");
        }
        for (String state : REQUIRED_STATES) {
            if (!Mc263FeatureBlockState.supportsExactState(state)) {
                throw new UnsupportedOperationException(
                        key + " exact-state capability is absent: " + state);
            }
        }
    }

    private void requireSchedule(Mc263StructureIndexReceipt.Entry entry,
            Mc263StructureCarrier.Registry registry) {
        Mc263StructureCarrier.StructureDefinition definition = registry.require(key);
        if (entry.step() != STEP || entry.index() != index || !entry.key().equals(key)
                || entry.biomeMask() != biomeMask || definition.registryOrdinal() != ordinal
                || definition.decorationStep() != STEP
                || definition.terrainAdjustment() != Mc263StructureCarrier.TerrainAdjustment.NONE
                || !Mc263StructureIndexReceipt.entries().get(ordinal).equals(entry)) {
            throw new IllegalArgumentException("ocean-ruin schedule mismatch: " + key);
        }
    }

    private void requireDispatcher(Mc263FeatureDispatcher.StructurePlacementContext dispatcher,
            Mc263StructureCarrier.ChunkReferences references,
            Mc263CanonicalFeaturesProducerSkeleton.SourceClip clip) {
        long expected = Mc263DecorationRandom.featureSeed(dispatcher.decorationSeed(), index, STEP);
        if (dispatcher.step() != STEP || dispatcher.globalIndex() != index
                || !dispatcher.structureKey().equals(key)
                || dispatcher.sourceChunkX() != references.chunkX()
                || dispatcher.sourceChunkZ() != references.chunkZ()
                || dispatcher.featureSeed() != expected) {
            throw new IllegalArgumentException("ocean-ruin dispatcher/source/RNG mismatch: " + key);
        }
        requireSourceBinding(references, clip);
    }

    private static void requireSourceBinding(Mc263StructureCarrier.ChunkReferences references,
            Mc263CanonicalFeaturesProducerSkeleton.SourceClip clip) {
        if (!Mc263CanonicalFeaturesProducerSkeleton.SourceClip.chunk(
                references.chunkX(), references.chunkZ()).equals(clip)) {
            throw new IllegalArgumentException("ocean-ruin source clip mismatch");
        }
    }

    private void validateAllStarts(Mc263StructureCarrier carrier) {
        for (Mc263StructureCarrier.ChunkStarts chunk : carrier.startChunks()) {
            for (Mc263StructureCarrier.StartEntry entry : chunk.orderedStarts()) {
                if (!entry.structureId().equals(key)) continue;
                if (entry.body() instanceof Mc263StructureCarrier.ValidStart valid) replay(valid);
            }
        }
    }

    private Plan replay(Mc263StructureCarrier.ValidStart start) {
        if (!start.startKey().equals(key + "@" + start.originChunkX() + ","
                + start.originChunkZ()) || start.references() < 0 || start.orderedPieces().isEmpty()) {
            throw new IllegalArgumentException("noncanonical persisted ocean-ruin start: " + key);
        }
        ArrayList<PiecePlan> pieces = new ArrayList<>();
        for (int ordinal = 0; ordinal < start.orderedPieces().size(); ordinal++) {
            Mc263StructureCarrier.Piece persisted = start.orderedPieces().get(ordinal);
            if (!persisted.pieceType().equals(Mc263OceanRuinProgram.PIECE_TYPE)
                    || persisted.poolElement()
                    || persisted.projection() != Mc263StructureCarrier.Projection.NOT_APPLICABLE
                    || persisted.groundLevelDelta() != 0 || !persisted.junctions().isEmpty()) {
                throw new IllegalArgumentException("noncanonical persisted ocean-ruin piece");
            }
            PieceNbt nbt = PieceNbt.decode(persisted.persistedPayload().binaryNbtCompound());
            if (nbt.type != type || !nbt.box.equals(persisted.boundingBox())) {
                throw new IllegalArgumentException("ocean-ruin persisted piece binding mismatch");
            }
            var descriptor = Mc263OceanRuinTemplateCatalog.official().require(nbt.template);
            pieces.add(new PiecePlan(ordinal, descriptor, nbt.position, nbt.rotation,
                    nbt.integrity, nbt.large, nbt.type, nbt.box,
                    processors(nbt.integrity, nbt.type),
                    persisted.persistedPayload().binaryNbtCompound()));
        }
        PiecePlan first = pieces.getFirst();
        Plan result = new Plan(type, first.templatePosition().x(), first.templatePosition().z(),
                first.rotation(), first.large(), pieces.size() > (type == Type.COLD ? 3 : 1),
                pieces, List.of(), start.adjustedBoundingBox(),
                Mc263OceanRuinProgram.persistedStartNbt(type, start.originChunkX(),
                        start.originChunkZ(), pieces, start.references()),
                new RngContinuation(0L, new long[8]));
        if (!Mc263OceanRuinProgram.validStart(result, start.references()).equals(start)) {
            throw new IllegalArgumentException("ocean-ruin persisted start replay mismatch");
        }
        return result;
    }

    private static List<Mc263OceanRuinProgram.Processor> processors(float integrity, Type type) {
        String material = type == Type.COLD ? "gravel" : "sand";
        return List.of(new Mc263OceanRuinProgram.Processor("minecraft:block_rot", integrity,
                        "", "", "", 0),
                new Mc263OceanRuinProgram.Processor("minecraft:ignore_structure_and_air",
                        Float.NaN, "minecraft:air,minecraft:structure_block", "", "", 0),
                new Mc263OceanRuinProgram.Processor("minecraft:capped_archaeology", Float.NaN,
                        "minecraft:" + material, "minecraft:suspicious_" + material,
                        "minecraft:archaeology/ocean_ruin_" + type.name().toLowerCase(), 5));
    }

    private static Mc263OceanRuinSettlement.Terrain terrain(Mc263FeaturesRegion region) {
        return new Mc263OceanRuinSettlement.Terrain() {
            @Override public int seaLevel() { return 63; }
            @Override public boolean waterFluid(BlockPos position) {
                return region.blockState(position.x(), position.y(), position.z()).fluidKind()
                        != Mc263FeatureBlockState.FluidKind.NONE;
            }
        };
    }

    private static Mc263FinalChunkCodec.FinalChunk sourceChunk(Mc263FeaturesRegion region,
            int chunkX, int chunkZ) {
        short[] ids = new short[Blocks.CHUNK_BLOCKS];
        Map<Integer, Mc263FeatureBlockState> overrides = new LinkedHashMap<>();
        for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
            for (int y = Blocks.MIN_Y; y <= Blocks.MAX_Y; y++) {
                int packed = Blocks.blockIndex(x, y, z);
                Mc263FeatureBlockState state = region.blockState(chunkX * 16 + x, y,
                        chunkZ * 16 + z);
                ids[packed] = (short) state.blockId();
                if (Mc263ExactStateCodec.stateCode(state) != 0) overrides.put(packed, state);
            }
        }
        int[] heights = new int[256];
        return new Mc263FinalChunkCodec.FinalChunk(chunkX, chunkZ, ids, overrides,
                heights, heights, heights, Mc263FinalChunkSidecars.EMPTY);
    }

    private static Mc263FeaturesRegion.StructureBatch batch(
            Mc263FinalChunkCodec.FinalChunk before, Mc263FinalChunkCodec.FinalChunk after,
            String structure, String startKey,
            Mc263CanonicalFeaturesProducerSkeleton.StructurePlacementInput placement) {
        long owner = Mc263StructureOwner.owner(structure, startKey);
        ArrayList<Mc263FeaturesRegion.StructureBlockWrite> blocks = new ArrayList<>();
        Map<Integer, Mc263FinalChunkSidecars.Archaeology> archByPacked = new LinkedHashMap<>();
        for (var value : after.sidecars().archaeology()) archByPacked.put(value.packed(), value);
        for (int packed = 0; packed < Blocks.CHUNK_BLOCKS; packed++) {
            Mc263FeatureBlockState oldState = state(before, packed);
            Mc263FeatureBlockState newState = state(after, packed);
            if (oldState.equals(newState) && !archByPacked.containsKey(packed)) continue;
            int[] p = unpack(after, packed);
            if (archByPacked.containsKey(packed)) blocks.add(new Mc263FeaturesRegion.StructureBlockWrite(
                    p[0], p[1], p[2], "minecraft:barrier[waterlogged=false]", owner));
            blocks.add(new Mc263FeaturesRegion.StructureBlockWrite(
                    p[0], p[1], p[2], newState.exactState(), owner));
        }
        List<Mc263FeaturesRegion.StructureLoot> loot = after.sidecars().loot().stream()
                .map(value -> Map.entry(value, unpack(after, value.packed())))
                .filter(value -> placement.clip().contains(value.getValue()[0],
                        value.getValue()[1], value.getValue()[2]))
                .map(value -> { var sidecar = value.getKey(); int[] p = value.getValue();
                    return new Mc263FeaturesRegion.StructureLoot(p[0], p[1], p[2],
                            sidecar.table(), sidecar.seed(), placement.productionContext(
                                    p[0], p[1], p[2], sidecar.table())); }).toList();
        List<Mc263FeaturesRegion.StructureArchaeology> archaeology =
                after.sidecars().archaeology().stream().map(value -> {
                    int[] p = unpack(after, value.packed());
                    return new Mc263FeaturesRegion.StructureArchaeology(p[0], p[1], p[2],
                            value.table(), value.seed()); }).toList();
        List<Mc263FeaturesRegion.StructureBentEvidence> bent =
                after.sidecars().blockEntities().stream().map(value -> {
                    int[] p = unpack(after, value.packed());
                    return new Mc263FeaturesRegion.StructureBentEvidence(p[0], p[1], p[2],
                            value.blockIdentity(), value.entityType(), value.canonicalNbt());
                }).toList();
        return new Mc263FeaturesRegion.StructureBatch(blocks, loot, archaeology, bent,
                after.sidecars().entities());
    }

    private static Mc263FeatureBlockState state(Mc263FinalChunkCodec.FinalChunk chunk, int packed) {
        Mc263FeatureBlockState override = chunk.stateOverrides().get(packed);
        return override != null ? override : Mc263FeatureBlockState.defaultForId(
                Short.toUnsignedInt(chunk.blockIds()[packed]));
    }

    private static int[] unpack(Mc263FinalChunkCodec.FinalChunk chunk, int packed) {
        int yIndex = packed / 256, rem = packed % 256;
        return new int[] {chunk.chunkX() * 16 + rem % 16, Blocks.MIN_Y + yIndex,
                chunk.chunkZ() * 16 + rem / 16};
    }

    private static Mc263OceanRuinSettlement.Clip clip(
            Mc263CanonicalFeaturesProducerSkeleton.SourceClip value) {
        return new Mc263OceanRuinSettlement.Clip(value.minX(), value.minY(), value.minZ(),
                value.maxX(), value.maxY(), value.maxZ());
    }

    private static Mc263StructureCarrier replaceStart(Mc263StructureCarrier carrier,
            Mc263StructureCarrier.ValidStart replacement) {
        ArrayList<Mc263StructureCarrier.ChunkStarts> chunks = new ArrayList<>();
        boolean replaced = false;
        for (Mc263StructureCarrier.ChunkStarts chunk : carrier.startChunks()) {
            ArrayList<Mc263StructureCarrier.StartEntry> starts = new ArrayList<>();
            for (Mc263StructureCarrier.StartEntry entry : chunk.orderedStarts()) {
                if (entry.structureId().equals(replacement.startKey().substring(
                        0, replacement.startKey().lastIndexOf('@')))
                        && entry.body() instanceof Mc263StructureCarrier.ValidStart valid
                        && valid.startKey().equals(replacement.startKey())) {
                    starts.add(new Mc263StructureCarrier.StartEntry(entry.structureId(), replacement));
                    replaced = true;
                } else starts.add(entry);
            }
            chunks.add(chunk.withStarts(starts));
        }
        if (!replaced) throw new IllegalArgumentException("ocean-ruin STR start is absent");
        return new Mc263StructureCarrier(carrier.registry(), chunks, carrier.referenceChunks(),
                carrier.rawStartPayloads(), carrier.producerGraphPayloads());
    }

    private static void advanceDispatcher(Mc263DecorationRandom.WorldgenRandom random, int draws) {
        for (int draw = 0; draw < draws; draw++) random.next(32);
    }

    private record Capabilities(boolean exactStates, boolean atomicStr, boolean schema4) {
        private static final Capabilities COMPLETE = new Capabilities(true, true, true);
    }

    record Prepared(Mc263StructureCarrier predecessor, Mc263StructureCarrier successor,
                    Mc263FeaturesRegion.StructureBatch batch, int draws) { }

    private record PieceNbt(Mc263StructureCarrier.BoundingBox box, BlockPos position,
                            Rotation rotation, String template, float integrity,
                            Type type, boolean large) {
        private static PieceNbt decode(byte[] bytes) {
            Nbt input = new Nbt(bytes);
            input.root();
            Map<String, Object> tags = input.compound();
            input.end();
            int[] box = (int[]) tags.get("BB");
            String id = (String) tags.get("id");
            if (box == null || box.length != 6 || !Mc263OceanRuinProgram.PIECE_TYPE.equals(id)
                    || !Integer.valueOf(0).equals(tags.get("GD"))
                    || !Integer.valueOf(2).equals(tags.get("O"))) {
                throw new IllegalArgumentException("noncanonical ocean-ruin piece NBT");
            }
            return new PieceNbt(new Mc263StructureCarrier.BoundingBox(box[0], box[1], box[2],
                    box[3], box[4], box[5]), new BlockPos((int) tags.get("TPX"),
                    (int) tags.get("TPY"), (int) tags.get("TPZ")),
                    Rotation.valueOf((String) tags.get("Rot")), (String) tags.get("Template"),
                    (float) tags.get("Integrity"), Type.valueOf((String) tags.get("BiomeType")),
                    ((byte) tags.get("IsLarge")) != 0);
        }
    }

    private static final class Nbt {
        private final ByteBuffer in;
        private Nbt(byte[] bytes) { in = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN); }
        private void root() { if (in.get() != 10 || string().length() != 0) fail(); }
        private Map<String, Object> compound() {
            Map<String, Object> result = new LinkedHashMap<>();
            while (true) {
                byte type = in.get(); if (type == 0) return result;
                String name = string(); result.put(name, value(type));
            }
        }
        private Object value(byte type) {
            return switch (type) {
                case 1 -> in.get(); case 3 -> in.getInt(); case 5 -> in.getFloat();
                case 8 -> string(); case 11 -> { int size = in.getInt();
                    if (size < 0 || size > 64) fail(); int[] values = new int[size];
                    for (int i = 0; i < size; i++) values[i] = in.getInt(); yield values; }
                default -> throw new IllegalArgumentException("unsupported ocean-ruin NBT tag");
            };
        }
        private String string() { int size = Short.toUnsignedInt(in.getShort());
            byte[] value = new byte[size]; in.get(value); return new String(value, StandardCharsets.UTF_8); }
        private void end() { if (in.hasRemaining()) fail(); }
        private static void fail() { throw new IllegalArgumentException("malformed ocean-ruin NBT"); }
    }
}
