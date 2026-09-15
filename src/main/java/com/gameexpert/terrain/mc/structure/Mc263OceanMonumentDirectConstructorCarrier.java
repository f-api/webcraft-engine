package com.gameexpert.terrain.mc.structure;

import com.gameexpert.terrain.mc.structure.Mc263HardcodedStructureCarrier.PieceFact;
import com.gameexpert.terrain.mc.structure.Mc263HardcodedStructureCarrier.RoomFact;
import com.gameexpert.terrain.mc.structure.Mc263HardcodedStructureCarrier.Rotation;
import com.gameexpert.terrain.mc.structure.Mc263OceanMonumentOracleV2.GeneratedNbt;
import com.gameexpert.terrain.mc.structure.Mc263OceanMonumentOracleV2.Probe;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/** Strict carrier for the official direct {@code MonumentBuilding} constructor boundary. */
public final class Mc263OceanMonumentDirectConstructorCarrier {
    public static final String PROVENANCE = "official-direct-constructor-v1";

    private static final byte[] MAGIC = "OMD263C1".getBytes(StandardCharsets.US_ASCII);
    private static final int MAX_NBT_BYTES = 1 << 20;
    private static final long LEGACY_MASK = (1L << 48) - 1L;

    private final long constructorSeed;
    private final int originX;
    private final int originZ;
    private final Rotation direction;
    private final RngFact constructorStart;
    private final RngFact constructorContinuation;
    private final Probe officialReceipt;
    private final Mc263HardcodedStructureCarrier graph;
    private final GeneratedNbt generatedNbt;
    private final byte[] graphSha256;

    private Mc263OceanMonumentDirectConstructorCarrier(long constructorSeed, int originX,
            int originZ, Rotation direction, RngFact constructorStart,
            RngFact constructorContinuation, Probe officialReceipt,
            Mc263HardcodedStructureCarrier graph,
            GeneratedNbt generatedNbt) {
        this.constructorSeed = constructorSeed;
        this.originX = originX;
        this.originZ = originZ;
        this.direction = Objects.requireNonNull(direction, "direct monument direction");
        this.constructorStart = Objects.requireNonNull(constructorStart, "constructor RNG start");
        this.constructorContinuation = Objects.requireNonNull(
                constructorContinuation, "constructor RNG continuation");
        this.officialReceipt = Objects.requireNonNull(officialReceipt,
                "direct monument official receipt");
        this.graph = Objects.requireNonNull(graph, "direct monument graph");
        this.generatedNbt = Objects.requireNonNull(generatedNbt, "direct monument raw NBT");
        this.graphSha256 = sha256(graph.directFactsCanonical());
    }

    /** Accepts only one of the four pinned official probes and its complete validated raw NBT. */
    public static Mc263OceanMonumentDirectConstructorCarrier officialProbe(Probe probe,
            GeneratedNbt generatedNbt) {
        Objects.requireNonNull(probe, "official monument probe");
        Objects.requireNonNull(generatedNbt, "official monument raw NBT");
        Mc263OceanMonumentOracleV2.requireExactProbe(probe);
        Probe expected = Mc263OceanMonumentOracleV2.requireProbe(probe.rotation());
        var binding = Mc263OceanMonumentConstructorSeedAdapter.directBinding(
                expected.constructorSeed(), expected.rotation());
        if (binding.provenance()
                != Mc263OceanMonumentConstructorSeedAdapter.Provenance.OFFICIAL_DIRECT_CONSTRUCTOR
                || binding.constructorStartRawState48() != binding.graphStartRawState48()) {
            throw new IllegalArgumentException("direct monument constructor provenance drift");
        }
        Mc263HardcodedStructureCarrier graph = Mc263HardcodedStructureCarrier.directMonument(
                expected.constructorSeed(), expected.originX(), expected.originZ(),
                expected.rotation());
        if (!Arrays.equals(graph.canonicalMonumentBuildingNbt(),
                generatedNbt.predecessorMonumentBuilding())) {
            throw new IllegalArgumentException("direct monument building NBT/graph drift");
        }
        GeneratedNbt validated = Mc263OceanMonumentOracleV2.requireGeneratedNbt(
                expected.rotation(), generatedNbt.predecessorStructureStart(),
                generatedNbt.predecessorMonumentBuilding(), generatedNbt.predecessorChildren(),
                generatedNbt.successorStructureStart(), generatedNbt.successorMonumentBuilding(),
                generatedNbt.successorChildren());
        if (!Arrays.equals(validated.frozenBytes(), generatedNbt.frozenBytes())) {
            throw new IllegalArgumentException("direct monument frozen NBT provenance drift");
        }
        requireChildGraph(graph, validated.predecessorChildren());
        RngFact start = new RngFact(binding.constructorStartRawState48(), 0);
        RngFact continuation = new RngFact(graph.rngContinuation().rawLegacySeed(),
                graph.rngContinuation().bitDraws());
        return new Mc263OceanMonumentDirectConstructorCarrier(expected.constructorSeed(),
                expected.originX(), expected.originZ(), expected.rotation(), start,
                continuation, expected, graph, validated);
    }

    public String provenance() { return PROVENANCE; }
    public long constructorSeed() { return constructorSeed; }
    public int originX() { return originX; }
    public int originZ() { return originZ; }
    public Rotation direction() { return direction; }
    public RngFact constructorStart() { return constructorStart; }
    public RngFact constructorContinuation() { return constructorContinuation; }
    public Probe officialReceipt() { return officialReceipt; }
    public List<PieceFact> orderedPieces() { return graph.orderedPieces(); }
    public List<RoomFact> orderedRooms() { return graph.orderedRooms(); }
    public GeneratedNbt generatedNbt() { return generatedNbt; }
    public byte[] graphSha256() { return graphSha256.clone(); }

    Mc263HardcodedStructureCarrier programCarrier() { return graph; }

    public byte[] encodeCanonical() {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream out = new DataOutputStream(bytes)) {
                out.write(MAGIC);
                out.writeUTF(PROVENANCE);
                out.writeLong(constructorSeed);
                out.writeInt(originX);
                out.writeInt(originZ);
                out.writeByte(direction.ordinal());
                constructorStart.write(out);
                constructorContinuation.write(out);
                out.write(graphSha256);
                writeNbt(out, generatedNbt.predecessorStructureStart());
                writeNbt(out, generatedNbt.predecessorMonumentBuilding());
                writeNbtList(out, generatedNbt.predecessorChildren());
                writeNbt(out, generatedNbt.successorStructureStart());
                writeNbt(out, generatedNbt.successorMonumentBuilding());
                writeNbtList(out, generatedNbt.successorChildren());
            }
            return bytes.toByteArray();
        } catch (IOException impossible) {
            throw new IllegalStateException("in-memory direct monument codec failed", impossible);
        }
    }

    public static Mc263OceanMonumentDirectConstructorCarrier decodeCanonical(byte[] bytes) {
        Objects.requireNonNull(bytes, "direct monument bytes");
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes))) {
            if (!Arrays.equals(in.readNBytes(MAGIC.length), MAGIC)) {
                throw new IllegalArgumentException("unknown direct monument codec");
            }
            if (!PROVENANCE.equals(in.readUTF())) {
                throw new IllegalArgumentException("unknown direct monument provenance");
            }
            long seed = in.readLong();
            int x = in.readInt();
            int z = in.readInt();
            int directionOrdinal = in.readUnsignedByte();
            if (directionOrdinal >= Rotation.values().length) {
                throw new IllegalArgumentException("unknown direct monument direction");
            }
            Rotation direction = Rotation.values()[directionOrdinal];
            RngFact start = RngFact.read(in);
            RngFact continuation = RngFact.read(in);
            byte[] graphDigest = in.readNBytes(32);
            byte[] predecessorStart = readNbt(in);
            byte[] predecessorBuilding = readNbt(in);
            List<byte[]> predecessorChildren = readNbtList(in);
            byte[] successorStart = readNbt(in);
            byte[] successorBuilding = readNbt(in);
            List<byte[]> successorChildren = readNbtList(in);
            if (in.read() != -1) {
                throw new IllegalArgumentException("trailing direct monument bytes");
            }
            Probe probe = Mc263OceanMonumentOracleV2.requireProbe(direction);
            if (probe.constructorSeed() != seed || probe.originX() != x || probe.originZ() != z) {
                throw new IllegalArgumentException("direct monument probe identity drift");
            }
            GeneratedNbt nbt = Mc263OceanMonumentOracleV2.requireGeneratedNbt(direction,
                    predecessorStart, predecessorBuilding, predecessorChildren,
                    successorStart, successorBuilding, successorChildren);
            Mc263OceanMonumentDirectConstructorCarrier decoded = officialProbe(probe, nbt);
            if (!start.equals(decoded.constructorStart)
                    || !continuation.equals(decoded.constructorContinuation)
                    || !Arrays.equals(graphDigest, decoded.graphSha256)
                    || !Arrays.equals(bytes, decoded.encodeCanonical())) {
                throw new IllegalArgumentException("noncanonical direct monument facts");
            }
            return decoded;
        } catch (EOFException exception) {
            throw new IllegalArgumentException("truncated direct monument encoding", exception);
        } catch (IOException exception) {
            throw new IllegalArgumentException("invalid direct monument encoding", exception);
        }
    }

    private static void writeNbt(DataOutputStream out, byte[] value) throws IOException {
        if (value.length == 0 || value.length > MAX_NBT_BYTES) {
            throw new IllegalArgumentException("invalid direct monument NBT length");
        }
        out.writeInt(value.length);
        out.write(value);
    }

    private static byte[] readNbt(DataInputStream in) throws IOException {
        int length = in.readInt();
        if (length <= 0 || length > MAX_NBT_BYTES) {
            throw new IllegalArgumentException("invalid direct monument NBT length");
        }
        byte[] value = in.readNBytes(length);
        if (value.length != length) throw new EOFException("truncated direct monument NBT");
        return value;
    }

    private static void writeNbtList(DataOutputStream out, List<byte[]> values) throws IOException {
        if (values.isEmpty() || values.size() > 64) {
            throw new IllegalArgumentException("invalid direct monument NBT list");
        }
        out.writeInt(values.size());
        for (byte[] value : values) writeNbt(out, value);
    }

    private static List<byte[]> readNbtList(DataInputStream in) throws IOException {
        int count = in.readInt();
        if (count <= 0 || count > 64) {
            throw new IllegalArgumentException("invalid direct monument NBT list");
        }
        ArrayList<byte[]> values = new ArrayList<>(count);
        for (int index = 0; index < count; index++) values.add(readNbt(in));
        return List.copyOf(values);
    }

    private static byte[] sha256(byte[] value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value);
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }

    private static void requireChildGraph(Mc263HardcodedStructureCarrier graph,
            List<byte[]> childNbt) {
        List<PieceFact> pieces = graph.orderedPieces().subList(1, graph.orderedPieces().size());
        if (pieces.size() != childNbt.size()) {
            throw new IllegalArgumentException("direct monument child graph/NBT count drift");
        }
        for (int index = 0; index < pieces.size(); index++) {
            ChildNbtFact official = ChildNbtFact.read(childNbt.get(index));
            PieceFact piece = pieces.get(index);
            if (!official.id.equals(pieceId(piece))
                    || !official.box.equals(piece.boundingBox())) {
                throw new IllegalArgumentException(
                        "direct monument child graph/NBT fact drift at " + index);
            }
        }
    }

    private static String pieceId(PieceFact piece) {
        return switch (piece.kind()) {
            case MONUMENT_ENTRY -> "minecraft:omentry";
            case MONUMENT_CORE -> "minecraft:omcr";
            case MONUMENT_DOUBLE_X -> "minecraft:omdxr";
            case MONUMENT_DOUBLE_XY -> "minecraft:omdxyr";
            case MONUMENT_DOUBLE_Y -> "minecraft:omdyr";
            case MONUMENT_DOUBLE_YZ -> "minecraft:omdyzr";
            case MONUMENT_DOUBLE_Z -> "minecraft:omdzr";
            case MONUMENT_SIMPLE -> "minecraft:omsimple";
            case MONUMENT_SIMPLE_TOP -> "minecraft:omsimplet";
            case MONUMENT_WING -> "minecraft:omwr";
            case MONUMENT_PENTHOUSE -> "minecraft:ompenthouse";
            default -> throw new IllegalArgumentException("non-child direct monument piece");
        };
    }

    private static final class ChildNbtFact {
        private final String id;
        private final Mc263HardcodedStructureCarrier.BoundingBox box;

        private ChildNbtFact(String id, Mc263HardcodedStructureCarrier.BoundingBox box) {
            this.id = id;
            this.box = box;
        }

        private static ChildNbtFact read(byte[] bytes) {
            try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes))) {
                if (in.readUnsignedByte() != 10 || !in.readUTF().isEmpty()) {
                    throw new IllegalArgumentException("invalid direct monument child NBT root");
                }
                String id = null;
                int[] box = null;
                for (int type = in.readUnsignedByte(); type != 0; type = in.readUnsignedByte()) {
                    String name = in.readUTF();
                    if (type == 11 && "BB".equals(name)) {
                        int count = in.readInt();
                        if (count != 6) {
                            throw new IllegalArgumentException("invalid monument child NBT box");
                        }
                        box = new int[count];
                        for (int index = 0; index < count; index++) box[index] = in.readInt();
                    } else if (type == 8 && "id".equals(name)) {
                        id = in.readUTF();
                    } else if (type == 3 && ("GD".equals(name) || "O".equals(name))) {
                        in.readInt();
                    } else {
                        throw new IllegalArgumentException(
                                "unknown direct monument child NBT field");
                    }
                }
                if (in.read() != -1 || id == null || box == null) {
                    throw new IllegalArgumentException("incomplete direct monument child NBT");
                }
                return new ChildNbtFact(id, new Mc263HardcodedStructureCarrier.BoundingBox(
                        box[0], box[1], box[2], box[3], box[4], box[5]));
            } catch (EOFException exception) {
                throw new IllegalArgumentException("truncated direct monument child NBT", exception);
            } catch (IOException exception) {
                throw new IllegalArgumentException("invalid direct monument child NBT", exception);
            }
        }
    }

    public static final class RngFact {
        private final long rawState48;
        private final long bitDraws;

        private RngFact(long rawState48, long bitDraws) {
            if ((rawState48 & ~LEGACY_MASK) != 0 || bitDraws < 0) {
                throw new IllegalArgumentException("invalid direct monument RNG fact");
            }
            this.rawState48 = rawState48;
            this.bitDraws = bitDraws;
        }

        public long rawState48() { return rawState48; }
        public long bitDraws() { return bitDraws; }

        private void write(DataOutputStream out) throws IOException {
            out.writeLong(rawState48);
            out.writeLong(bitDraws);
        }

        private static RngFact read(DataInputStream in) throws IOException {
            return new RngFact(in.readLong(), in.readLong());
        }

        @Override public boolean equals(Object value) {
            return value instanceof RngFact other && rawState48 == other.rawState48
                    && bitDraws == other.bitDraws;
        }

        @Override public int hashCode() { return Objects.hash(rawState48, bitDraws); }
    }
}
