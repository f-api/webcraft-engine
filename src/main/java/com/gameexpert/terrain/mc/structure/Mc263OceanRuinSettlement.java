package com.gameexpert.terrain.mc.structure;

import com.gameexpert.terrain.Blocks;
import com.gameexpert.terrain.mc.feature.Mc263ExactStateCodec;
import com.gameexpert.terrain.mc.feature.Mc263FeatureBlockState;
import com.gameexpert.terrain.mc.feature.Mc263FinalChunkCodec;
import com.gameexpert.terrain.mc.feature.Mc263FinalChunkSidecars;
import com.gameexpert.terrain.mc.structure.Mc263OceanRuinProgram.BlockPos;
import com.gameexpert.terrain.mc.structure.Mc263OceanRuinProgram.LegacyRandom;
import com.gameexpert.terrain.mc.structure.Mc263OceanRuinProgram.Marker;
import com.gameexpert.terrain.mc.structure.Mc263OceanRuinProgram.MarkerKind;
import com.gameexpert.terrain.mc.structure.Mc263OceanRuinProgram.PiecePlan;
import com.gameexpert.terrain.mc.structure.Mc263OceanRuinProgram.Plan;
import com.gameexpert.terrain.mc.structure.Mc263OceanRuinProgram.Rotation;
import com.gameexpert.terrain.mc.structure.Mc263OceanRuinProgram.TemplateDescriptor;
import com.gameexpert.terrain.mc.structure.Mc263OceanRuinProgram.Type;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Dormant schema-4 settlement for validated procedural ocean-ruin template programs.
 *
 * <p>The caller supplies independent procedural cells, not a Mojang template. The complete
 * descriptor identity and processor outcomes are preflighted before terrain queries or post RNG.
 * Geometry and LOOT/ARCH/BENT/ENTS are staged and then assembled into one immutable MCF263LC
 * successor. This direct-piece boundary intentionally makes no full-stage orchestration claim.</p>
 */
public final class Mc263OceanRuinSettlement {
    private static final String CHEST = "minecraft:chest";
    private static final String BRUSHABLE = "minecraft:brushable_block";
    private static final String DROWNED = "minecraft:drowned";
    private static final String STRUCTURE_REASON = "minecraft:structure";
    private static final String SMALL_LOOT = "minecraft:chests/underwater_ruin_small";
    private static final String BIG_LOOT = "minecraft:chests/underwater_ruin_big";

    private Mc263OceanRuinSettlement() { }

    /** One template block after procedural decoding, before the official processors. */
    public record Cell(BlockPos localPosition, String sourceExactState, float rotSample,
            boolean archaeologySelected, long archaeologyLootSeed) {
        public Cell {
            Objects.requireNonNull(localPosition, "ocean-ruin cell position");
            Objects.requireNonNull(sourceExactState, "ocean-ruin source state");
            if (!(rotSample >= 0.0F && rotSample < 1.0F) || !Float.isFinite(rotSample)) {
                throw new IllegalArgumentException("ocean-ruin rot sample outside [0,1)");
            }
        }
    }

    /** Independently encoded block grammar, bound to the official descriptor identity. */
    public record TemplateProgram(String templateKey, int binaryLength, String binarySha256,
            List<Cell> orderedCells) {
        public TemplateProgram {
            Objects.requireNonNull(templateKey); Objects.requireNonNull(binarySha256);
            orderedCells = List.copyOf(orderedCells);
            if (orderedCells.isEmpty()) throw new IllegalArgumentException("empty template grammar");
            HashSet<BlockPos> positions = new HashSet<>();
            for (Cell cell : orderedCells) if (!positions.add(cell.localPosition())) {
                throw new IllegalArgumentException("duplicate procedural template cell");
            }
        }
    }

    @FunctionalInterface
    public interface ProceduralGrammar {
        TemplateProgram require(String templateKey);
    }

    @FunctionalInterface
    public interface PieceProceduralGrammar {
        TemplateProgram require(PiecePlan piece);
    }

    public interface Terrain {
        int seaLevel();
        boolean waterFluid(BlockPos position);
    }

    public record Clip(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        public Clip {
            if (minX > maxX || minY > maxY || minZ > maxZ) {
                throw new IllegalArgumentException("inverted ocean-ruin clip");
            }
        }
        boolean contains(BlockPos p) {
            return p.x() >= minX && p.x() <= maxX && p.y() >= minY && p.y() <= maxY
                    && p.z() >= minZ && p.z() <= maxZ;
        }
        public static Clip chunk(int chunkX, int chunkZ) {
            int minX = Math.multiplyExact(chunkX, 16), minZ = Math.multiplyExact(chunkZ, 16);
            return new Clip(minX, Blocks.MIN_Y, minZ, minX + 15, Blocks.MAX_Y, minZ + 15);
        }
    }

    public record BlockMutation(BlockPos position, String exactState) { }
    public record LootMutation(BlockPos position, String facing, String table, long seed,
            byte[] canonicalNbt) {
        public LootMutation { canonicalNbt = canonicalNbt.clone(); }
        @Override public byte[] canonicalNbt() { return canonicalNbt.clone(); }
    }
    public record ArchaeologyMutation(BlockPos position, String table, long seed,
            String exactState, byte[] canonicalNbt) {
        public ArchaeologyMutation { canonicalNbt = canonicalNbt.clone(); }
        @Override public byte[] canonicalNbt() { return canonicalNbt.clone(); }
    }
    public record EntityMutation(int encounterOrdinal, BlockPos position, byte[] payload) {
        public EntityMutation { payload = payload.clone(); }
        @Override public byte[] payload() { return payload.clone(); }
    }

    public record Execution(Plan successor, List<BlockMutation> blocks,
            List<LootMutation> loot, List<ArchaeologyMutation> archaeology,
            List<EntityMutation> entities, byte[] rngContinuation) {
        public Execution {
            blocks = List.copyOf(blocks); loot = List.copyOf(loot);
            archaeology = List.copyOf(archaeology); entities = List.copyOf(entities);
            rngContinuation = rngContinuation.clone();
        }
        @Override public byte[] rngContinuation() { return rngContinuation.clone(); }
    }

    public record Settlement(Mc263StructureCarrier.ValidStart successorStart,
            Mc263FinalChunkCodec.FinalChunk finalChunk, byte[] fingerprint) {
        public Settlement { fingerprint = fingerprint.clone(); }
        @Override public byte[] fingerprint() { return fingerprint.clone(); }
    }

    /**
     * FEATURES-only semantic settlement. This deliberately does not validate or encode an MCF
     * carrier: LOOT declarations become authenticated only after the resulting batch is committed
     * to the region and the shared final assembler runs.
     */
    public record FeaturesSettlement(Mc263StructureCarrier.ValidStart successorStart,
            Mc263FinalChunkCodec.FinalChunk semanticChunk) { }

    /** Executes all pieces in official encounter order, publishing neither world state nor RNG. */
    public static Execution execute(Plan projected, ProceduralGrammar grammar, Terrain terrain,
            LegacyRandom postRandom, Clip clip) {
        Objects.requireNonNull(grammar, "ocean-ruin procedural grammar");
        return executePieces(projected, piece -> grammar.require(piece.template().key()),
                terrain, postRandom, clip);
    }

    /** Piece-aware production form; repeated template keys retain independent processor results. */
    public static Execution executePieces(Plan projected, PieceProceduralGrammar grammar,
            Terrain terrain, LegacyRandom postRandom, Clip clip) {
        Objects.requireNonNull(projected, "projected ocean-ruin plan");
        Objects.requireNonNull(grammar, "ocean-ruin procedural grammar");
        Objects.requireNonNull(terrain, "ocean-ruin marker terrain");
        Objects.requireNonNull(postRandom, "ocean-ruin post RNG");
        Objects.requireNonNull(clip, "ocean-ruin clip");

        ArrayList<TemplateProgram> programs = new ArrayList<>();
        for (PiecePlan piece : projected.pieces()) {
            TemplateProgram program = Objects.requireNonNull(
                    grammar.require(piece), "ocean-ruin template program");
            preflight(piece, program);
            programs.add(program);
        }

        LegacyRandom stagedRandom = postRandom.fork();
        ArrayList<BlockMutation> blocks = new ArrayList<>();
        LinkedHashMap<BlockPos, LootMutation> loot = new LinkedHashMap<>();
        LinkedHashMap<BlockPos, ArchaeologyMutation> archaeology = new LinkedHashMap<>();
        ArrayList<EntityMutation> entities = new ArrayList<>();
        for (int pieceIndex = 0; pieceIndex < projected.pieces().size(); pieceIndex++) {
            PiecePlan piece = projected.pieces().get(pieceIndex);
            TemplateProgram program = programs.get(pieceIndex);
            int selectedArchaeology = 0;
            for (Cell cell : program.orderedCells()) {
                BlockPos world = transform(piece.templatePosition(), cell.localPosition(),
                        piece.rotation());
                boolean ignored = cell.sourceExactState().equals("minecraft:air")
                        || cell.sourceExactState().startsWith("minecraft:structure_block");
                boolean retained = cell.rotSample() <= piece.integrity();
                if (cell.archaeologySelected()) selectedArchaeology++;
                if (cell.archaeologySelected() && (ignored || !retained
                        || !archaeologyInput(piece.type(), cell.sourceExactState())
                        || selectedArchaeology > 5)) {
                    throw new IllegalArgumentException("noncanonical archaeology selection");
                }
                if (ignored || !retained || !clip.contains(world)) continue;
                String state = cell.archaeologySelected() ? suspiciousState(piece.type())
                        : Mc263OceanRuinProductionExecutor.rotateState(
                                cell.sourceExactState(), piece.rotation());
                if (state.contains("waterlogged=false") && terrain.waterFluid(world)) {
                    state = state.replace("waterlogged=false", "waterlogged=true");
                }
                if (cell.archaeologySelected()) {
                    blocks.add(new BlockMutation(world,
                            "minecraft:barrier[waterlogged=false]"));
                }
                blocks.add(new BlockMutation(world, state));
                // Later official pieces overwrite both the block and any earlier block entity at
                // the same position. Linked maps retain encounter order for surviving sidecars.
                archaeology.remove(world);
                loot.remove(world);
                if (cell.archaeologySelected()) {
                    String table = archaeologyTable(piece.type());
                    archaeology.put(world, new ArchaeologyMutation(world, table,
                            cell.archaeologyLootSeed(), state,
                            blockEntityNbt(world, BRUSHABLE, table,
                                    cell.archaeologyLootSeed())));
                }
            }
            for (Marker marker : piece.template().markers()) {
                BlockPos world = transform(piece.templatePosition(), marker.localPosition(),
                        piece.rotation());
                if (!clip.contains(world)) continue;
                if (marker.kind() == MarkerKind.CHEST) {
                    boolean waterlogged = terrain.waterFluid(world);
                    String state = "minecraft:chest[facing=north,type=single,waterlogged="
                            + waterlogged + "]";
                    long seed = stagedRandom.nextLong();
                    String table = piece.large() ? BIG_LOOT : SMALL_LOOT;
                    blocks.add(new BlockMutation(world, state));
                    archaeology.remove(world);
                    loot.put(world, new LootMutation(world, "north", table, seed,
                            blockEntityNbt(world, CHEST, table, seed)));
                } else {
                    entities.add(new EntityMutation(entities.size(), world,
                            entityPayload(world)));
                    blocks.add(new BlockMutation(world, "minecraft:air"));
                    archaeology.remove(world);
                    loot.remove(world);
                }
            }
        }
        byte[] continuation = ByteBufferBuilder.continuation(stagedRandom);
        ArrayList<LootMutation> orderedLoot = new ArrayList<>(loot.values());
        ArrayList<ArchaeologyMutation> orderedArchaeology =
                new ArrayList<>(archaeology.values());
        orderedLoot.sort(Comparator.comparingInt(value ->
                touchOrdinal(blocks, value.position())));
        orderedArchaeology.sort(Comparator.comparingInt(value ->
                touchOrdinal(blocks, value.position())));
        Execution result = new Execution(projected, blocks, orderedLoot,
                orderedArchaeology, entities, continuation);
        postRandom.commitFrom(stagedRandom);
        return result;
    }

    /** Atomically constructs canonical STR successor and schema-4 MCF successor. */
    public static Settlement settle(Execution execution, int references,
            Mc263FinalChunkCodec.FinalChunk source) {
        Objects.requireNonNull(execution, "ocean-ruin execution");
        Objects.requireNonNull(source, "ocean-ruin final carrier");
        Mc263FinalChunkCodec.validate(source);
        requireChunkClip(execution, source);
        requireAuthenticatedLootRows(source, execution);
        Mc263FinalChunkCodec.FinalChunk successor = applySemantic(source, execution);
        Mc263StructureCarrier.ValidStart start = Mc263OceanRuinProgram.validStart(
                execution.successor(), references);
        byte[] mcf = Mc263FinalChunkCodec.encode(successor);
        byte[] str = startReceipt(start);
        byte[] fingerprint = sha256(concat(str, mcf, execution.rngContinuation()));
        return new Settlement(start, successor, fingerprint);
    }

    /**
     * Forms semantic block and sidecar rows for one FEATURES structure batch. No LDEC is available
     * before dispatch, so this path must never be treated as a final MCF carrier.
     */
    public static FeaturesSettlement settleFeatures(Execution execution, int references,
            Mc263FinalChunkCodec.FinalChunk source) {
        Objects.requireNonNull(execution, "ocean-ruin execution");
        Objects.requireNonNull(source, "ocean-ruin semantic source");
        if (references < 0) throw new IllegalArgumentException("negative ocean-ruin references");
        requireChunkClip(execution, source);
        return new FeaturesSettlement(Mc263OceanRuinProgram.validStart(
                execution.successor(), references), applySemantic(source, execution));
    }

    private static void requireChunkClip(Execution execution,
            Mc263FinalChunkCodec.FinalChunk source) {
        Clip chunk = Clip.chunk(source.chunkX(), source.chunkZ());
        for (BlockMutation mutation : execution.blocks()) if (!chunk.contains(mutation.position())) {
            throw new IllegalArgumentException("ocean-ruin execution outside final chunk clip");
        }
    }

    private static Mc263FinalChunkCodec.FinalChunk applySemantic(
            Mc263FinalChunkCodec.FinalChunk source, Execution execution) {
        short[] ids = source.blockIds();
        HashMap<Integer, Mc263FeatureBlockState> overrides =
                new HashMap<>(source.stateOverrides());
        boolean[] touched = new boolean[256];
        LinkedHashMap<BlockPos, String> finalStates = new LinkedHashMap<>();
        for (BlockMutation mutation : execution.blocks()) {
            finalStates.put(mutation.position(), mutation.exactState());
        }
        for (Map.Entry<BlockPos, String> mutation : finalStates.entrySet()) {
            int packed = packed(source, mutation.getKey());
            Mc263FeatureBlockState state = Mc263FeatureBlockState.fromExact(mutation.getValue());
            ids[packed] = (short) state.blockId();
            if (Mc263ExactStateCodec.stateCode(state) == 0) overrides.remove(packed);
            else overrides.put(packed, state);
            touched[Math.floorMod(mutation.getKey().x(), 16)
                    + Math.floorMod(mutation.getKey().z(), 16) * 16] = true;
        }

        Mc263FinalChunkSidecars old = source.sidecars();
        ArrayList<Mc263FinalChunkSidecars.Loot> loot = new ArrayList<>(old.loot());
        ArrayList<Mc263FinalChunkSidecars.Archaeology> archaeology =
                new ArrayList<>(old.archaeology());
        ArrayList<Mc263FinalChunkSidecars.BlockEntity> blockEntities =
                new ArrayList<>(old.blockEntities());
        ArrayList<Mc263FinalChunkSidecars.StructureEntity> entities =
                new ArrayList<>(old.entities());
        HashSet<Integer> occupiedLoot = packedSet(loot.stream()
                .map(Mc263FinalChunkSidecars.Loot::packed).toList());
        HashSet<Integer> occupiedArch = packedSet(archaeology.stream()
                .map(Mc263FinalChunkSidecars.Archaeology::packed).toList());
        HashSet<Integer> occupiedBent = packedSet(blockEntities.stream()
                .map(Mc263FinalChunkSidecars.BlockEntity::packed).toList());
        HashMap<BlockPos, LootMutation> lootByPosition = new HashMap<>();
        for (LootMutation mutation : execution.loot()) {
            int packed = packed(source, mutation.position());
            Mc263FinalChunkSidecars.Loot value = new Mc263FinalChunkSidecars.Loot(packed,
                    mutation.facing(), mutation.table(), mutation.seed());
            if (!occupiedLoot.add(packed)) {
                Mc263FinalChunkSidecars.Loot existing = loot.stream()
                        .filter(row -> row.packed() == packed).findFirst().orElseThrow();
                if (!existing.equals(value)) {
                    throw new IllegalArgumentException("ocean-ruin LOOT/BENT conflict");
                }
            } else {
                loot.add(value);
            }
            lootByPosition.put(mutation.position(), mutation);
        }
        HashMap<BlockPos, ArchaeologyMutation> archaeologyByPosition = new HashMap<>();
        for (ArchaeologyMutation mutation : execution.archaeology()) {
            int packed = packed(source, mutation.position());
            if (!occupiedArch.add(packed)) {
                throw new IllegalArgumentException("ocean-ruin ARCH/BENT conflict");
            }
            archaeology.add(new Mc263FinalChunkSidecars.Archaeology(packed, mutation.table(),
                    mutation.seed()));
            archaeologyByPosition.put(mutation.position(), mutation);
        }
        HashSet<BlockPos> blockEntityPositions = new HashSet<>();
        for (BlockMutation write : execution.blocks()) {
            BlockPos position = write.position();
            if (!blockEntityPositions.add(position)) continue;
            LootMutation lootMutation = lootByPosition.get(position);
            ArchaeologyMutation archaeologyMutation = archaeologyByPosition.get(position);
            if (lootMutation != null && archaeologyMutation != null) {
                throw new IllegalArgumentException("ocean-ruin LOOT/ARCH/BENT conflict");
            }
            if (lootMutation != null) {
                int packed = packed(source, position);
                if (!occupiedBent.add(packed)) {
                    throw new IllegalArgumentException("ocean-ruin LOOT/BENT conflict");
                }
                blockEntities.add(new Mc263FinalChunkSidecars.BlockEntity(packed,
                        CHEST, CHEST, lootMutation.canonicalNbt()));
            } else if (archaeologyMutation != null) {
                int packed = packed(source, position);
                if (!occupiedBent.add(packed)) {
                    throw new IllegalArgumentException("ocean-ruin ARCH/BENT conflict");
                }
                blockEntities.add(new Mc263FinalChunkSidecars.BlockEntity(packed,
                        blockKey(archaeologyMutation.exactState()), BRUSHABLE,
                        archaeologyMutation.canonicalNbt()));
            }
        }
        for (EntityMutation mutation : execution.entities()) {
            BlockPos p = mutation.position();
            entities.add(new Mc263FinalChunkSidecars.StructureEntity(DROWNED, STRUCTURE_REASON,
                    p.x(), p.y(), p.z(), 0.0F, 0.0F, 0.0, 0.0, 0.0,
                    mutation.payload()));
        }
        Mc263FinalChunkSidecars sidecars = new Mc263FinalChunkSidecars(old.blockTicks(),
                old.fluidTicks(), loot, old.spawners(), old.owners(), archaeology, old.bees(),
                blockEntities, entities, old.containerLootDeclarations());
        int[] ws = source.worldSurfaceWg(), of = source.oceanFloorWg(),
                mb = source.motionBlocking();
        for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
            int column = x + z * 16; if (!touched[column]) continue;
            ws[column] = scan(ids, overrides, x, z, HeightKind.WORLD_SURFACE);
            of[column] = scan(ids, overrides, x, z, HeightKind.OCEAN_FLOOR);
            mb[column] = scan(ids, overrides, x, z, HeightKind.MOTION_BLOCKING);
        }
        Mc263FinalChunkCodec.FinalChunk result = new Mc263FinalChunkCodec.FinalChunk(
                source.chunkX(), source.chunkZ(), ids, overrides, ws, of, mb, sidecars);
        return result;
    }

    private static void requireAuthenticatedLootRows(
            Mc263FinalChunkCodec.FinalChunk source, Execution execution) {
        for (LootMutation mutation : execution.loot()) {
            Mc263FinalChunkSidecars.Loot row = new Mc263FinalChunkSidecars.Loot(
                    packed(source, mutation.position()), mutation.facing(), mutation.table(),
                    mutation.seed());
            if (!source.sidecars().loot().contains(row)) {
                throw new IllegalStateException(
                        "ocean-ruin LDEC upstream handoff missing: Mc263FinalChunkAssembler "
                                + "must provide the authenticated LOOT row and declaration "
                                + "before settlement");
            }
        }
    }

    private enum HeightKind { WORLD_SURFACE, OCEAN_FLOOR, MOTION_BLOCKING }
    private static int scan(short[] ids, Map<Integer, Mc263FeatureBlockState> overrides,
            int x, int z, HeightKind kind) {
        for (int y = Blocks.MAX_Y; y >= Blocks.MIN_Y; y--) {
            int packed = Blocks.blockIndex(x, y, z);
            Mc263FeatureBlockState state = overrides.get(packed);
            if (state == null) state = Mc263FeatureBlockState.defaultForId(
                    Short.toUnsignedInt(ids[packed]));
            boolean motion = state.blocksMotionInHeightmapNoLeaves() || state.isLeaves();
            boolean match = switch (kind) {
                case WORLD_SURFACE -> !state.isAir();
                case OCEAN_FLOOR -> motion;
                case MOTION_BLOCKING -> motion
                        || state.fluidKind() != Mc263FeatureBlockState.FluidKind.NONE;
            };
            if (match) return y + 1;
        }
        return Blocks.MIN_Y;
    }

    private static void preflight(PiecePlan piece, TemplateProgram program) {
        TemplateDescriptor descriptor = piece.template();
        if (!program.templateKey().equals(descriptor.key())
                || program.binaryLength() != descriptor.binaryLength()
                || !program.binarySha256().equals(descriptor.binarySha256())) {
            throw new IllegalArgumentException("ocean-ruin procedural grammar identity drift");
        }
        for (Cell cell : program.orderedCells()) {
            BlockPos p = cell.localPosition();
            if (p.x() < 0 || p.y() < 0 || p.z() < 0 || p.x() >= descriptor.sizeX()
                    || p.y() >= descriptor.sizeY() || p.z() >= descriptor.sizeZ()) {
                throw new IllegalArgumentException("ocean-ruin grammar cell outside descriptor");
            }
            if (!Mc263OceanRuinGrammarData.acceptsState(cell.sourceExactState())) {
                Mc263FeatureBlockState.fromExact(cell.sourceExactState());
            }
        }
    }

    private static BlockPos transform(BlockPos origin, BlockPos local, Rotation rotation) {
        int x, z;
        switch (rotation) {
            case NONE -> { x = local.x(); z = local.z(); }
            case CLOCKWISE_90 -> { x = -local.z(); z = local.x(); }
            case CLOCKWISE_180 -> { x = -local.x(); z = -local.z(); }
            case COUNTERCLOCKWISE_90 -> { x = local.z(); z = -local.x(); }
            default -> throw new AssertionError(rotation);
        }
        return new BlockPos(Math.addExact(origin.x(), x), Math.addExact(origin.y(), local.y()),
                Math.addExact(origin.z(), z));
    }

    private static boolean archaeologyInput(Type type, String state) {
        return state.equals(type == Type.COLD ? "minecraft:gravel" : "minecraft:sand");
    }
    private static String suspiciousState(Type type) {
        return type == Type.COLD ? "minecraft:suspicious_gravel[dusted=0]"
                : "minecraft:suspicious_sand[dusted=0]";
    }
    private static String archaeologyTable(Type type) {
        return "minecraft:archaeology/ocean_ruin_" + type.name().toLowerCase();
    }
    private static String blockKey(String exact) {
        int bracket = exact.indexOf('['); return bracket < 0 ? exact : exact.substring(0, bracket);
    }

    private static int touchOrdinal(List<BlockMutation> blocks, BlockPos position) {
        for (int index = 0; index < blocks.size(); index++) {
            if (blocks.get(index).position().equals(position)) return index;
        }
        return Integer.MAX_VALUE;
    }

    private static int packed(Mc263FinalChunkCodec.FinalChunk chunk, BlockPos p) {
        if (Math.floorDiv(p.x(), 16) != chunk.chunkX()
                || Math.floorDiv(p.z(), 16) != chunk.chunkZ()
                || p.y() < Blocks.MIN_Y || p.y() > Blocks.MAX_Y) {
            throw new IllegalArgumentException("ocean-ruin mutation outside final chunk");
        }
        return Blocks.blockIndex(Math.floorMod(p.x(), 16), p.y(), Math.floorMod(p.z(), 16));
    }

    private static HashSet<Integer> packedSet(List<Integer> values) {
        return new HashSet<>(values);
    }

    private static byte[] blockEntityNbt(BlockPos p, String entityType, String table, long seed) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream out = new DataOutputStream(bytes)) {
                out.writeByte(10); out.writeShort(0);
                tagString(out, "LootTable", table);
                out.writeByte(10); utf(out, "components"); out.writeByte(0);
                tagInt(out, "x", p.x()); tagInt(out, "y", p.y()); tagInt(out, "z", p.z());
                tagString(out, "id", entityType);
                out.writeByte(4); utf(out, "LootTableSeed"); out.writeLong(seed);
                out.writeByte(0);
            }
            return bytes.toByteArray();
        } catch (IOException impossible) { throw new AssertionError(impossible); }
    }

    private static byte[] entityPayload(BlockPos p) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream out = new DataOutputStream(bytes)) {
                out.write("ORP263E1".getBytes(StandardCharsets.US_ASCII));
                out.writeInt(p.x()); out.writeInt(p.y()); out.writeInt(p.z());
                out.writeBoolean(true);
            }
            return bytes.toByteArray();
        } catch (IOException impossible) { throw new AssertionError(impossible); }
    }

    private static byte[] startReceipt(Mc263StructureCarrier.ValidStart start) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream out = new DataOutputStream(bytes)) {
                utf(out, start.startKey()); out.writeInt(start.originChunkX());
                out.writeInt(start.originChunkZ()); out.writeInt(start.references());
                out.writeInt(start.orderedPieces().size());
                for (Mc263StructureCarrier.Piece piece : start.orderedPieces()) {
                    byte[] nbt = piece.persistedPayload().binaryNbtCompound();
                    out.writeInt(nbt.length); out.write(nbt);
                }
            }
            return bytes.toByteArray();
        } catch (IOException impossible) { throw new AssertionError(impossible); }
    }

    private static byte[] concat(byte[]... values) {
        int length = 0; for (byte[] value : values) length = Math.addExact(length, value.length);
        byte[] result = new byte[length]; int offset = 0;
        for (byte[] value : values) {
            System.arraycopy(value, 0, result, offset, value.length); offset += value.length;
        }
        return result;
    }
    private static byte[] sha256(byte[] value) {
        try { return java.security.MessageDigest.getInstance("SHA-256").digest(value); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }
    private static void tagInt(DataOutputStream out, String name, int value) throws IOException {
        out.writeByte(3); utf(out, name); out.writeInt(value);
    }
    private static void tagString(DataOutputStream out, String name, String value)
            throws IOException {
        out.writeByte(8); utf(out, name); utf(out, value);
    }
    private static void utf(DataOutputStream out, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        out.writeShort(bytes.length); out.write(bytes);
    }

    private static final class ByteBufferBuilder {
        private ByteBufferBuilder() { }
        static byte[] continuation(LegacyRandom random) {
            var value = random.continuation();
            java.nio.ByteBuffer buffer = java.nio.ByteBuffer.allocate(8 + 8 * 8);
            buffer.putLong(value.state48());
            for (long word : value.nextLongs()) buffer.putLong(word);
            return buffer.array();
        }
    }
}
