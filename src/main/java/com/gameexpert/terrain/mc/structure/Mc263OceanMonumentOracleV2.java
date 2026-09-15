package com.gameexpert.terrain.mc.structure;

import com.gameexpert.terrain.mc.structure.Mc263HardcodedStructureCarrier.Rotation;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Bounded identity contract for the accepted full-shell snapshot-7 monument oracle.
 *
 * <p>This contains no Mojang template payload. It binds the procedural implementation to the
 * complete source-order operation stream, its 64-operation segment digest, final observed volume,
 * placement-RNG tail, raw official NBT digests, clip schedule, and elder encounter order.</p>
 */
public final class Mc263OceanMonumentOracleV2 {
    public static final String PRODUCER =
            "gameexpert-official-26.3-ocean-monument-oracle-v2";
    public static final String SOURCE_SHA256 =
            "b30ccc773be1fbc7cd8e2dcbd1011373fac0888c807b67085c6289a7f2da47a8";
    public static final String CONTRACT_SHA256 =
            "86feb545a7d1108c427ba9e677dbc69bc60443cbd8e415007c138b6aa2fd57be";
    public static final String ORACLE_SHA256 =
            "b1759513f6d65027968f3dd02baef7b2544be6c777aa2ef2e593cd51d6edfb5c";

    public record ClipReceipt(int chunkX, int chunkZ, int operationStart, int operationCount,
            int randomDrawStart, int randomDrawCount) {
        public ClipReceipt {
            if (operationStart < 0 || operationCount <= 0 || randomDrawStart < 0
                    || randomDrawCount < 0) throw new IllegalArgumentException("invalid clip receipt");
        }
    }

    public record ElderReceipt(double x, double y, double z, int operationOrdinal) {
        public ElderReceipt {
            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                    || operationOrdinal < 0) throw new IllegalArgumentException("invalid elder receipt");
        }
    }

    public record Probe(Rotation rotation, long constructorSeed, long spongeSeed,
            int originX, int originZ, int childPieceCount, int operationCount,
            int blockQueryCount, int fluidQueryCount, int setBlockCount, int fluidTickCount,
            String orderedOperationSha256, String segmentDigestSha256,
            String finalVolumeSha256, int placementDrawCount, String placementState48Hex,
            String structureStartNbtSha256, String buildingNbtSha256,
            String orderedChildNbtSha256, List<ClipReceipt> clips,
            List<ElderReceipt> elders) {
        public Probe {
            Objects.requireNonNull(rotation); clips = List.copyOf(clips); elders = List.copyOf(elders);
            if (childPieceCount <= 0 || operationCount <= 0 || blockQueryCount <= 0
                    || fluidQueryCount <= 0 || setBlockCount <= 0 || fluidTickCount < 0
                    || placementDrawCount < 0 || clips.size() != 16 || elders.size() != 3) {
                throw new IllegalArgumentException("invalid monument oracle probe");
            }
            for (String digest : List.of(orderedOperationSha256, segmentDigestSha256,
                    finalVolumeSha256, structureStartNbtSha256, buildingNbtSha256,
                    orderedChildNbtSha256)) requireSha256(digest);
            if (!placementState48Hex.matches("[0-9a-f]{12}")) {
                throw new IllegalArgumentException("invalid 48-bit RNG state");
            }
            int expectedOperationStart = 0;
            int expectedDrawStart = 0;
            for (ClipReceipt clip : clips) {
                if (clip.operationStart() != expectedOperationStart
                        || clip.randomDrawStart() != expectedDrawStart) {
                    throw new IllegalArgumentException("non-contiguous monument clip receipt");
                }
                expectedOperationStart += clip.operationCount();
                expectedDrawStart += clip.randomDrawCount();
            }
            if (expectedOperationStart != operationCount || expectedDrawStart != placementDrawCount) {
                throw new IllegalArgumentException("monument clip totals drift");
            }
            int previous = -1;
            for (ElderReceipt elder : elders) {
                if (elder.operationOrdinal() <= previous || elder.operationOrdinal() >= operationCount) {
                    throw new IllegalArgumentException("noncanonical elder encounter order");
                }
                previous = elder.operationOrdinal();
            }
        }
    }

    private static final Map<Rotation, Probe> PROBES = Map.of(
            Rotation.NORTH, probe(Rotation.NORTH, 0L, 11L, 0, 0, 27, 179205, 144432,
                    14572, 20092, 106,
                    "8ea08bb05e081eca7753734640f03eb690e6ea6d0d5274f7c6d9c935815bbb99",
                    "9ccd9b8663214030ed161583f9309c11e79c74dda5b9a97bef3667227c02ea6d",
                    "16a74d12ab0d28319d67b4671030dc5cb5b42d62b2d5cf9f36f7f49d424a56fa",
                    69, "77f8977eec4d",
                    "3b3536f473867df0295e53bb4c28ed95a6004a6eab750db4fea667789181d3fb",
                    "cd8751a1f3c7eb63df74ae61d1ae6fbca19fd5c6f0d62dbb0f920aa8b2d08325",
                    "0259b5585393a70bd781d7e2a056e3b2a99c5bfae603a952e1d860e3dd2c858b",
                    "0,0,0,12366,0,0;1,0,12366,13224,0,2;2,0,25590,12682,2,1;3,0,38272,9500,3,1;0,1,47772,12234,4,1;1,1,60006,15809,5,60;2,1,75815,14447,65,2;3,1,90262,9384,67,0;0,2,99646,11767,67,0;1,2,111413,11936,67,1;2,2,123349,12227,68,1;3,2,135576,9527,69,0;0,3,145103,9246,69,0;1,3,154349,8244,69,0;2,3,162593,9032,69,0;3,3,171625,7580,69,0",
                    "28.5,53,29.5,75814;12.5,45,43.5,111412;45.5,42,40.5,135513"),
            Rotation.EAST, probe(Rotation.EAST, 1L, 12L, 160, -96, 33, 181691, 145930,
                    15072, 20592, 94,
                    "f153be1ad8a00d38296ab58ed70c036b60a52aa4bcb4e00186d813c40770e650",
                    "e3f5c697404d59807671fb0fc9da455f23ed34dddbb505bf44436da9f74e81df",
                    "08a4ed79014ca3c6bf186d99aee53999a3502f1585e0cf25f1b5e4ad5f08a3e8",
                    385, "553673b162d8",
                    "e068488cff98adcfee1a51e5138f47d3d01c3816ea65a250dd57f28dd8dfb9c6",
                    "614677f18a176bb279a0bd184af3d876c11d81f0b5c158d0bb7d1ddcb69b7d96",
                    "337e7df96a9327a2afd5c615a478fbff47e33da3e5ebcce6840c805db2f9ff90",
                    "10,-6,0,12175,0,0;11,-6,12175,11870,0,0;12,-6,24045,12170,0,62;13,-6,36215,9922,62,59;10,-5,46137,10318,121,0;11,-5,56455,14986,121,61;12,-5,71441,14431,182,124;13,-5,85872,10372,306,67;10,-4,96244,11508,373,0;11,-4,107752,14090,373,3;12,-4,121842,13470,376,3;13,-4,135312,10084,379,2;10,-3,145396,9770,381,0;11,-3,155166,9529,381,0;12,-3,164695,9256,381,2;13,-3,173951,7740,383,2",
                    "174.5,45,-83.5,12174;188.5,53,-67.5,71440;177.5,42,-50.5,121671"),
            Rotation.SOUTH, probe(Rotation.SOUTH, -7046029254386353131L, 13L, -224, 288,
                    32, 181419, 145908, 14943, 20463, 102,
                    "11abb4fe5fc932670238298e92b98f54f6cc55a2ca233d23574e047181ccd7fc",
                    "f7752afd7b3e617af3f6d0bffb6cd5a297b9b8e8b50bc2b7fd6b6ddae6602c71",
                    "95e61d48359593c239be5ff79d0807fe58a12ada3ae14831d103fcea99f0be10",
                    398, "a7da561db246",
                    "979957c42a7e7fe7af7490d1c812fbe9a25b5e6714385c780d51869665b7dc36",
                    "cb451c23ac15f00851d1a0dc054669c14ce2f1489e9c4b5e89b3bcb35d62d66d",
                    "05e09736771c657dad8ff69c8222352af18bfb65d865bfaff2a6da13aaae9680",
                    "-14,18,0,12175,0,0;-13,18,12175,10318,0,0;-12,18,22493,11508,0,0;-11,18,34001,9770,0,0;-14,19,43771,11870,0,1;-13,19,55641,14944,1,3;-12,19,70585,13991,4,5;-11,19,84576,9573,9,0;-14,20,94149,12118,9,128;-13,20,106267,14579,137,136;-12,20,120846,13243,273,3;-11,20,134089,9194,276,0;-14,21,143283,9932,276,56;-13,21,153215,10520,332,65;-12,21,163735,10008,397,1;-11,21,173743,7676,398,0",
                    "-211.5,45,302.5,12174;-195.5,53,316.5,70584;-178.5,42,305.5,84405"),
            Rotation.WEST, probe(Rotation.WEST, Long.MAX_VALUE, 14L, 496, 512, 31, 179735,
                    144308, 14937, 20457, 30,
                    "07e21bc044c6a079324a343134edf5ff104a74af841d390d70993353d23ff642",
                    "d953686557f4687b615093e505ddba3bdc8ae6b9da56f5d26b9bbbf95ea1aa86",
                    "2d57d0e46328b4201c25a2cb6692212e50cb5427e86051cec28a45ff5fc7f6d5",
                    202, "045e80a918fd",
                    "cd6b233169d072c215f1df6dd1a2da398cd8aca185c22d3ca3eb6001bc5c8311",
                    "b0c13f867b0b7461fb9426e20ef17be32b1dc772f38f8fccd7c28858435310be",
                    "5d12cb5b3df1157d6d3c47f792682bbc1f84955b71214e28b13efef529f538f7",
                    "31,32,0,12630,0,1;32,32,12630,12114,1,1;33,32,24744,11685,2,0;34,32,36429,9246,2,0;31,33,45675,12598,2,1;32,33,58273,16165,3,126;33,33,74438,12076,129,62;34,33,86514,8244,191,0;31,34,94758,13132,191,3;32,34,107890,14630,194,3;33,34,122520,12130,197,1;34,34,134650,9032,198,0;31,35,143682,9560,198,1;32,35,153242,9384,199,2;33,35,162626,9529,201,1;34,35,172155,7580,202,0",
                    "539.5,45,524.5,36428;525.5,53,540.5,74437;536.5,42,557.5,134587"));

    private Mc263OceanMonumentOracleV2() { }

    public static Probe requireProbe(Rotation rotation) {
        Probe probe = PROBES.get(Objects.requireNonNull(rotation, "monument rotation"));
        if (probe == null) throw new UnsupportedOperationException("missing monument v2 oracle probe");
        return probe;
    }

    public static List<Probe> probes() {
        return List.of(requireProbe(Rotation.NORTH), requireProbe(Rotation.EAST),
                requireProbe(Rotation.SOUTH), requireProbe(Rotation.WEST));
    }

    /** Fail-closed comparison used by a generated full-shell receipt before promotion. */
    public static void requireExactProbe(Probe observed) {
        Objects.requireNonNull(observed, "observed monument receipt");
        Probe expected = requireProbe(observed.rotation());
        if (!expected.equals(observed)) {
            throw new IllegalArgumentException("ocean-monument v2 operation/NBT receipt drift");
        }
    }

    /** Validates every emitted value through the ordered event and 64-operation segment hashes. */
    public static void requireOperations(Rotation rotation,
            Mc263OceanMonumentPieceProgram.OperationReceipt observed) {
        Objects.requireNonNull(observed, "observed monument operations");
        Probe expected = requireProbe(rotation);
        Map<String, Integer> counts = observed.methodCounts();
        if (observed.operationCount() != expected.operationCount()
                || counts.getOrDefault("getBlockState", 0) != expected.blockQueryCount()
                || counts.getOrDefault("getFluidState", 0) != expected.fluidQueryCount()
                || counts.getOrDefault("setBlock", 0) != expected.setBlockCount()
                || counts.getOrDefault("scheduleTick", 0) != expected.fluidTickCount()
                || counts.getOrDefault("addFreshEntityWithPassengers", 0) != 3
                || !observed.orderedSha256().equals(expected.orderedOperationSha256())
                || !observed.segmentDigestSha256().equals(expected.segmentDigestSha256())) {
            throw new IllegalArgumentException("ocean-monument v2 operation/segment value drift");
        }
    }

    /** Complete generated predecessor/successor raw NBT, excluding all template block payloads. */
    public record GeneratedNbt(byte[] predecessorStructureStart,
            byte[] predecessorMonumentBuilding, List<byte[]> predecessorChildren,
            byte[] successorStructureStart, byte[] successorMonumentBuilding,
            List<byte[]> successorChildren, byte[] frozenBytes) {
        public GeneratedNbt {
            predecessorStructureStart = predecessorStructureStart.clone();
            predecessorMonumentBuilding = predecessorMonumentBuilding.clone();
            predecessorChildren = cloneBytes(predecessorChildren);
            successorStructureStart = successorStructureStart.clone();
            successorMonumentBuilding = successorMonumentBuilding.clone();
            successorChildren = cloneBytes(successorChildren);
            frozenBytes = frozenBytes.clone();
        }
        @Override public byte[] predecessorStructureStart() { return predecessorStructureStart.clone(); }
        @Override public byte[] predecessorMonumentBuilding() { return predecessorMonumentBuilding.clone(); }
        @Override public List<byte[]> predecessorChildren() { return cloneBytes(predecessorChildren); }
        @Override public byte[] successorStructureStart() { return successorStructureStart.clone(); }
        @Override public byte[] successorMonumentBuilding() { return successorMonumentBuilding.clone(); }
        @Override public List<byte[]> successorChildren() { return cloneBytes(successorChildren); }
        @Override public byte[] frozenBytes() { return frozenBytes.clone(); }
        public String frozenSha256() { return hex(sha256(frozenBytes)); }
    }

    /** Validates and freezes all raw official generated NBT for one rotation. */
    public static GeneratedNbt requireGeneratedNbt(Rotation rotation, byte[] predecessorStart,
            byte[] predecessorBuilding, List<byte[]> predecessorChildren,
            byte[] successorStart, byte[] successorBuilding, List<byte[]> successorChildren) {
        Probe expected = requireProbe(rotation);
        requireDigest(predecessorStart, expected.structureStartNbtSha256(), "structure start");
        requireDigest(predecessorBuilding, expected.buildingNbtSha256(), "building piece");
        requireChildren(predecessorChildren, expected);
        requireDigest(successorStart, expected.structureStartNbtSha256(), "successor start");
        requireDigest(successorBuilding, expected.buildingNbtSha256(), "successor building");
        requireChildren(successorChildren, expected);
        if (!java.util.Arrays.equals(predecessorStart, successorStart)
                || !java.util.Arrays.equals(predecessorBuilding, successorBuilding)
                || !equalBytes(predecessorChildren, successorChildren)) {
            throw new IllegalArgumentException("official monument NBT successor drift");
        }
        byte[] frozen = encodeGeneratedNbt(rotation, predecessorStart, predecessorBuilding,
                predecessorChildren, successorStart, successorBuilding, successorChildren);
        return new GeneratedNbt(predecessorStart, predecessorBuilding, predecessorChildren,
                successorStart, successorBuilding, successorChildren, frozen);
    }

    private static void requireChildren(List<byte[]> children, Probe expected) {
        Objects.requireNonNull(children, "monument child NBT");
        if (children.size() != expected.childPieceCount()) {
            throw new IllegalArgumentException("monument child NBT count drift");
        }
        ByteArrayOutputStream joined = new ByteArrayOutputStream();
        for (byte[] child : children) {
            if (child == null || child.length < 3 || Byte.toUnsignedInt(child[0]) != 10) {
                throw new IllegalArgumentException("invalid monument child NBT");
            }
            try { joined.write(child); }
            catch (IOException impossible) { throw new AssertionError(impossible); }
        }
        requireDigest(joined.toByteArray(), expected.orderedChildNbtSha256(), "ordered children");
    }

    private static byte[] encodeGeneratedNbt(Rotation rotation, byte[] predecessorStart,
            byte[] predecessorBuilding, List<byte[]> predecessorChildren, byte[] successorStart,
            byte[] successorBuilding, List<byte[]> successorChildren) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeBytes("OMN263F2"); out.writeByte(rotation.ordinal());
            writeNbt(out, predecessorStart); writeNbt(out, predecessorBuilding);
            writeNbtList(out, predecessorChildren); writeNbt(out, successorStart);
            writeNbt(out, successorBuilding); writeNbtList(out, successorChildren); out.flush();
            byte[] body = bytes.toByteArray(); out.write(sha256(body)); out.flush();
            return bytes.toByteArray();
        } catch (IOException impossible) {
            throw new IllegalStateException("in-memory monument NBT freeze failed", impossible);
        }
    }

    private static void writeNbt(DataOutputStream out, byte[] value) throws IOException {
        out.writeInt(value.length); out.write(value);
    }
    private static void writeNbtList(DataOutputStream out, List<byte[]> values) throws IOException {
        out.writeInt(values.size()); for (byte[] value : values) writeNbt(out, value);
    }
    private static List<byte[]> cloneBytes(List<byte[]> values) {
        return values.stream().map(byte[]::clone).toList();
    }
    private static boolean equalBytes(List<byte[]> left, List<byte[]> right) {
        if (left.size() != right.size()) return false;
        for (int index = 0; index < left.size(); index++) {
            if (!java.util.Arrays.equals(left.get(index), right.get(index))) return false;
        }
        return true;
    }
    private static void requireDigest(byte[] value, String expected, String field) {
        if (value == null || !hex(sha256(value)).equals(expected)) {
            throw new IllegalArgumentException("monument " + field + " NBT digest drift");
        }
    }
    private static byte[] sha256(byte[] value) {
        try { return MessageDigest.getInstance("SHA-256").digest(value); }
        catch (NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }
    private static String hex(byte[] value) { return java.util.HexFormat.of().formatHex(value); }

    private static Probe probe(Rotation rotation, long constructorSeed, long spongeSeed,
            int x, int z, int pieces, int operations, int blockQueries, int fluidQueries,
            int setBlocks, int fluidTicks, String operationSha, String segmentSha,
            String volumeSha, int draws, String state48, String startNbt, String buildingNbt,
            String childrenNbt, String clips, String elders) {
        return new Probe(rotation, constructorSeed, spongeSeed, x, z, pieces, operations,
                blockQueries, fluidQueries, setBlocks, fluidTicks, operationSha, segmentSha,
                volumeSha, draws, state48, startNbt, buildingNbt, childrenNbt,
                parseClips(clips), parseElders(elders));
    }

    private static List<ClipReceipt> parseClips(String encoded) {
        return java.util.Arrays.stream(encoded.split(";")).map(value -> {
            int[] f = java.util.Arrays.stream(value.split(",")).mapToInt(Integer::parseInt).toArray();
            if (f.length != 6) throw new IllegalArgumentException("invalid clip evidence");
            return new ClipReceipt(f[0], f[1], f[2], f[3], f[4], f[5]);
        }).toList();
    }

    private static List<ElderReceipt> parseElders(String encoded) {
        return java.util.Arrays.stream(encoded.split(";")).map(value -> {
            String[] f = value.split(",");
            if (f.length != 4) throw new IllegalArgumentException("invalid elder evidence");
            return new ElderReceipt(Double.parseDouble(f[0]), Double.parseDouble(f[1]),
                    Double.parseDouble(f[2]), Integer.parseInt(f[3]));
        }).toList();
    }

    private static void requireSha256(String value) {
        if (value == null || !value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("invalid SHA-256 evidence");
        }
    }
}
