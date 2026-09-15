package com.gameexpert.terrain.mc.structure;

import com.gameexpert.terrain.Blocks;
import com.gameexpert.terrain.mc.feature.Mc263ExactStateCodec;
import com.gameexpert.terrain.mc.feature.Mc263FeatureBlockState;
import com.gameexpert.terrain.mc.feature.Mc263FinalChunkCodec;
import com.gameexpert.terrain.mc.feature.Mc263FinalChunkSidecars;
import com.gameexpert.terrain.mc.structure.Mc263HardcodedStructureCarrier.Kind;
import com.gameexpert.terrain.mc.structure.Mc263StructureCarrier.ChunkReferences;
import com.gameexpert.terrain.mc.structure.Mc263StructureCarrier.Piece;
import com.gameexpert.terrain.mc.structure.Mc263StructureCarrier.PiecePayload;
import com.gameexpert.terrain.mc.structure.Mc263StructureCarrier.Projection;
import com.gameexpert.terrain.mc.structure.Mc263StructureCarrier.ValidStart;
import java.util.Arrays;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Dormant exact persisted-start and successor-STR boundary for the pinned ocean monument. */
public final class Mc263OceanMonumentSettlement {
    public static final String STRUCTURE = "minecraft:monument";
    public static final String BUILDING_PIECE = "minecraft:omb";

    private Mc263OceanMonumentSettlement() { }

    /**
     * One schema-4 chunk result plus the product-owned exactly-once elder mask. The mask is not an
     * official piece field and is intentionally outside raw predecessor/successor NBT.
     */
    public record ChunkSettlement(Mc263FinalChunkCodec.FinalChunk finalChunk,
            int carrierOwnedElderInstallMask, byte[] fingerprint) {
        public ChunkSettlement {
            Objects.requireNonNull(finalChunk, "monument final chunk");
            if ((carrierOwnedElderInstallMask & ~0b111) != 0) {
                throw new IllegalArgumentException("invalid monument elder install mask");
            }
            fingerprint = fingerprint.clone();
        }
        @Override public byte[] fingerprint() { return fingerprint.clone(); }
    }

    /** Builds the one persisted official MonumentBuilding piece; children regenerate from it. */
    public static ValidStart persistedStart(
            Mc263HardcodedStructureCarrier hardcoded, int references) {
        requireHardcoded(hardcoded);
        if (references < 0) throw new IllegalArgumentException("negative monument references");
        Mc263StructureCarrier.BoundingBox box = box(hardcoded.boundingBox());
        Piece building = new Piece(BUILDING_PIECE, box, false,
                Projection.NOT_APPLICABLE, 0, List.of(),
                new PiecePayload(hardcoded.canonicalMonumentBuildingNbt()));
        return new ValidStart(STRUCTURE + "@" + hardcoded.chunkX() + ","
                + hardcoded.chunkZ(), hardcoded.chunkX(), hardcoded.chunkZ(), references,
                box, List.of(building));
    }

    /**
     * Validates the referenced predecessor start and returns its canonical byte-identical
     * successor. The official monument building has no mutable post-process fields, but this STR
     * publication must still commit atomically with geometry, FTIK and ENTS.
     */
    public static Mc263StructureCarrier successorStr(Mc263StructureCarrier carrier,
            ChunkReferences references, Mc263HardcodedStructureCarrier hardcoded) {
        Objects.requireNonNull(carrier, "monument STR carrier");
        Objects.requireNonNull(references, "monument references");
        requireHardcoded(hardcoded);
        List<ValidStart> starts = carrier.resolveStarts(references, STRUCTURE);
        if (starts.size() != 1) {
            throw new IllegalArgumentException("monument settlement requires one referenced start");
        }
        ValidStart actual = starts.getFirst();
        ValidStart expected = persistedStart(hardcoded, actual.references());
        if (!actual.startKey().equals(expected.startKey())
                || actual.originChunkX() != expected.originChunkX()
                || actual.originChunkZ() != expected.originChunkZ()
                || !actual.adjustedBoundingBox().equals(expected.adjustedBoundingBox())
                || actual.orderedPieces().size() != 1) {
            throw new IllegalArgumentException("noncanonical persisted monument start");
        }
        Piece actualPiece = actual.orderedPieces().getFirst();
        Piece expectedPiece = expected.orderedPieces().getFirst();
        if (!actualPiece.pieceType().equals(BUILDING_PIECE)
                || !actualPiece.boundingBox().equals(expectedPiece.boundingBox())
                || actualPiece.poolElement()
                || actualPiece.projection() != Projection.NOT_APPLICABLE
                || actualPiece.groundLevelDelta() != 0
                || !actualPiece.junctions().isEmpty()
                || !Arrays.equals(actualPiece.persistedPayload().binaryNbtCompound(),
                        expectedPiece.persistedPayload().binaryNbtCompound())) {
            throw new IllegalArgumentException("noncanonical persisted monument building piece");
        }
        return carrier.strictlyDecoded();
    }

    /**
     * Applies the chunk-local portion of a frozen aggregate to a current-only schema-4 MCF carrier.
     * Caller processes chunks in canonical clip order and durably carries the returned elder mask.
     */
    public static ChunkSettlement settleChunk(
            Mc263OceanMonumentPieceProgram.AtomicSettlement aggregate,
            int predecessorElderMask, Mc263FinalChunkCodec.FinalChunk source) {
        Objects.requireNonNull(aggregate, "monument aggregate settlement");
        Objects.requireNonNull(source, "monument source final chunk");
        if ((predecessorElderMask & ~0b111) != 0) {
            throw new IllegalArgumentException("invalid predecessor elder install mask");
        }
        Mc263FinalChunkCodec.validate(source);
        short[] ids = source.blockIds();
        HashMap<Integer, Mc263FeatureBlockState> overrides =
                new HashMap<>(source.stateOverrides());
        boolean[] touched = new boolean[256];
        for (var write : aggregate.writes()) {
            if (!inChunk(source, write.position())) continue;
            int packed = packed(source, write.position());
            Mc263FeatureBlockState state = Mc263FeatureBlockState.fromExact(write.exactState());
            ids[packed] = (short) state.blockId();
            if (Mc263ExactStateCodec.stateCode(state) == 0) overrides.remove(packed);
            else overrides.put(packed, state);
            touched[Math.floorMod(write.position().x(), 16)
                    + Math.floorMod(write.position().z(), 16) * 16] = true;
        }

        Mc263FinalChunkSidecars old = source.sidecars();
        ArrayList<Mc263FinalChunkSidecars.FluidTick> fluidTicks =
                new ArrayList<>(old.fluidTicks());
        HashSet<String> occupiedTicks = new HashSet<>();
        for (var tick : fluidTicks) occupiedTicks.add(tick.packed() + "\0" + tick.key());
        for (var tick : aggregate.fluidTicks()) {
            if (!inChunk(source, tick.position())) continue;
            int packed = packed(source, tick.position());
            String identity = packed + "\0" + tick.fluidKey();
            if (!occupiedTicks.add(identity)) {
                throw new IllegalArgumentException("monument FTIK conflict");
            }
            fluidTicks.add(new Mc263FinalChunkSidecars.FluidTick(packed, tick.fluidKey(),
                    tick.delay(), Mc263FinalChunkSidecars.TickPriority.NORMAL,
                    tick.subTickOrder()));
        }

        ArrayList<Mc263FinalChunkSidecars.StructureEntity> entities =
                new ArrayList<>(old.entities());
        int successorMask = predecessorElderMask;
        for (int ordinal = 0; ordinal < aggregate.entities().size(); ordinal++) {
            var entity = aggregate.entities().get(ordinal);
            int blockX = (int) Math.floor(entity.x());
            int blockZ = (int) Math.floor(entity.z());
            if (Math.floorDiv(blockX, 16) != source.chunkX()
                    || Math.floorDiv(blockZ, 16) != source.chunkZ()) continue;
            int bit = 1 << ordinal;
            if ((successorMask & bit) != 0) continue;
            entities.add(new Mc263FinalChunkSidecars.StructureEntity(entity.entityType(),
                    entity.spawnReason(), entity.x(), entity.y(), entity.z(), entity.yaw(),
                    entity.pitch(), 0.0, 0.0, 0.0, entity.canonicalPayload()));
            successorMask |= bit;
        }

        Mc263FinalChunkSidecars sidecars = new Mc263FinalChunkSidecars(old.blockTicks(),
                fluidTicks, old.loot(), old.spawners(), old.owners(), old.archaeology(),
                old.bees(), old.blockEntities(), entities, old.containerLootDeclarations());
        int[] worldSurface = source.worldSurfaceWg();
        int[] oceanFloor = source.oceanFloorWg();
        int[] motionBlocking = source.motionBlocking();
        for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
            int column = x + z * 16;
            if (!touched[column]) continue;
            worldSurface[column] = scan(ids, overrides, x, z, HeightKind.WORLD_SURFACE);
            oceanFloor[column] = scan(ids, overrides, x, z, HeightKind.OCEAN_FLOOR);
            motionBlocking[column] = scan(ids, overrides, x, z, HeightKind.MOTION_BLOCKING);
        }
        var result = new Mc263FinalChunkCodec.FinalChunk(source.chunkX(), source.chunkZ(), ids,
                overrides, worldSurface, oceanFloor, motionBlocking, sidecars);
        byte[] mcf = Mc263FinalChunkCodec.encode(result);
        byte[] fingerprint = sha256(concat(aggregate.successorStr(), mcf,
                aggregate.randomContinuation(), new byte[]{(byte) successorMask}));
        return new ChunkSettlement(result, successorMask, fingerprint);
    }

    /** Reconstructs the oracle's pinned stone-floor/water substrate final-volume receipt. */
    public static String directFinalVolumeSha256(
            Mc263OceanMonumentPieceProgram.AtomicSettlement settlement,
            Mc263HardcodedStructureCarrier hardcoded) {
        Objects.requireNonNull(settlement, "monument direct settlement");
        requireHardcoded(hardcoded);
        Map<Mc263OceanMonumentPieceProgram.BlockPos, String> states = new HashMap<>();
        for (var write : settlement.writes()) {
            states.put(write.position(), write.exactState());
        }
        Mc263HardcodedStructureCarrier.BoundingBox building = hardcoded.boundingBox();
        int minX = Math.multiplyExact(Math.floorDiv(building.minX(), 16), 16);
        int minZ = Math.multiplyExact(Math.floorDiv(building.minZ(), 16), 16);
        int maxX = Math.addExact(Math.multiplyExact(Math.floorDiv(building.maxX(), 16), 16), 15);
        int maxZ = Math.addExact(Math.multiplyExact(Math.floorDiv(building.maxZ(), 16), 16), 15);
        int minY = Math.subtractExact(building.minY(), 16);
        int maxY = Math.max(building.maxY(), 64);
        MessageDigest digest;
        try { digest = MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
        for (int y = minY; y <= maxY; y++) for (int z = minZ; z <= maxZ; z++) {
            for (int x = minX; x <= maxX; x++) {
                var position = new Mc263OceanMonumentPieceProgram.BlockPos(x, y, z);
                String state = states.getOrDefault(position,
                        y == minY ? "minecraft:stone" : Mc263OceanMonumentPieceProgram.WATER);
                String stateText = state.equals(Mc263OceanMonumentPieceProgram.WATER)
                        ? "minecraft:water[level=0]" : state;
                String fluidText = state.equals(Mc263OceanMonumentPieceProgram.WATER)
                        ? "minecraft:water[falling=false]" : "minecraft:empty";
                digest.update((x + "\0" + y + "\0" + z + "\0" + stateText + "\0"
                        + fluidText + "\n").getBytes(StandardCharsets.UTF_8));
            }
        }
        return java.util.HexFormat.of().formatHex(digest.digest());
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

    private static boolean inChunk(Mc263FinalChunkCodec.FinalChunk chunk,
            Mc263OceanMonumentPieceProgram.BlockPos position) {
        return Math.floorDiv(position.x(), 16) == chunk.chunkX()
                && Math.floorDiv(position.z(), 16) == chunk.chunkZ()
                && position.y() >= Blocks.MIN_Y && position.y() <= Blocks.MAX_Y;
    }

    private static int packed(Mc263FinalChunkCodec.FinalChunk chunk,
            Mc263OceanMonumentPieceProgram.BlockPos position) {
        if (!inChunk(chunk, position)) throw new IllegalArgumentException("monument position outside chunk");
        return Blocks.blockIndex(Math.floorMod(position.x(), 16), position.y(),
                Math.floorMod(position.z(), 16));
    }

    private static byte[] concat(byte[]... values) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            for (byte[] value : values) out.write(value);
            return out.toByteArray();
        } catch (IOException impossible) {
            throw new IllegalStateException("in-memory monument fingerprint failed", impossible);
        }
    }

    private static byte[] sha256(byte[] value) {
        try { return MessageDigest.getInstance("SHA-256").digest(value); }
        catch (NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }

    private static void requireHardcoded(Mc263HardcodedStructureCarrier hardcoded) {
        Objects.requireNonNull(hardcoded, "monument hardcoded carrier");
        if (hardcoded.kind() != Kind.OCEAN_MONUMENT
                || hardcoded.orderedPieces().isEmpty()
                || hardcoded.orderedPieces().getFirst().kind()
                        != Mc263HardcodedStructureCarrier.PieceKind.MONUMENT_BUILDING) {
            throw new IllegalArgumentException("noncanonical monument hardcoded carrier");
        }
    }

    private static Mc263StructureCarrier.BoundingBox box(
            Mc263HardcodedStructureCarrier.BoundingBox value) {
        return new Mc263StructureCarrier.BoundingBox(value.minX(), value.minY(), value.minZ(),
                value.maxX(), value.maxY(), value.maxZ());
    }
}
