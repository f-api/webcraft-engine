package com.gameexpert.terrain.mc.df;

import com.gameexpert.terrain.mc.Mc263NoiseRegistry;
import com.gameexpert.terrain.mc.McNormalNoise;
import com.gameexpert.terrain.mc.McRandom;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** 26.3-snapshot-7에서 컴파일한 strict typed density graph loader다. */
public final class McDensityFunctionLoader {
    private final McRandom.PositionalFactory random;
    private final Map<String, Node> definitions = new LinkedHashMap<>();
    private final Map<String, McDensityFunction> functions = new HashMap<>();
    private final Map<String, McNormalNoise> noises = new HashMap<>();
    private final Set<String> resolving = new HashSet<>();
    private int wrapperCacheSlots;

    /** 경로는 기존 호출 ABI만 유지하며, 런타임 입력은 오직 내장 26.3 typed payload다. */
    public McDensityFunctionLoader(Path ignoredDatapackRoot, long seed) throws IOException {
        random = new McRandom(seed).forkPositional();
        try (DataInputStream input = Mc263DensityData.openVerified()) {
            int magic = input.readInt();
            int schema = input.readUnsignedShort();
            int count = input.readUnsignedShort();
            if (magic != Mc263DensityData.MAGIC || schema != Mc263DensityData.SCHEMA_VERSION
                    || count != Mc263DensityData.DEFINITION_COUNT) {
                throw new IOException("unsupported 26.3 density payload header");
            }
            for (int i = 0; i < count; i++) {
                String id = input.readUTF();
                if (definitions.put(id, readNode(input)) != null) {
                    throw new IOException("duplicate 26.3 density definition: " + id);
                }
            }
            if (input.read() != -1) throw new IOException("trailing 26.3 density payload bytes");
        }
    }

    public McDensityFunction continents() throws IOException { return resolve("minecraft:overworld/continents"); }
    public McDensityFunction erosion() throws IOException { return resolve("minecraft:overworld/erosion"); }
    public McDensityFunction depth() throws IOException { return resolve("minecraft:overworld/depth"); }
    public McDensityFunction ridges() throws IOException { return resolve("minecraft:overworld/ridges"); }
    public McDensityFunction ridgesFolded() throws IOException { return resolve("minecraft:overworld/ridges_folded"); }
    public McDensityFunction factor() throws IOException { return resolve("minecraft:overworld/factor"); }
    public McDensityFunction offset() throws IOException { return resolve("minecraft:overworld/offset"); }
    public McDensityFunction jaggedness() throws IOException { return resolve("minecraft:overworld/jaggedness"); }
    public McDensityFunction slopedCheese() throws IOException { return resolve("minecraft:overworld/sloped_cheese"); }
    public McDensityFunction finalDensity() throws IOException { return resolve("minecraft:overworld/final_density"); }
    public McDensityFunction preliminarySurfaceLevel() throws IOException {
        return resolve("minecraft:overworld/preliminary_surface_level");
    }

    public FullDensityGraph fullDensityGraph() throws IOException {
        Node root = requireDefinition("minecraft:overworld/final_density");
        requireOp(root, 6, "final_density root add");
        Node min = node(root, 0); requireOp(min, 10, "final_density left min");
        Node squeeze = node(min, 0); requireOp(squeeze, 17, "final_density squeeze");
        Node interpolated = node(squeeze, 0); requireOp(interpolated, 22,
                "final_density interpolated");
        Node latticeInput = node(interpolated, 0); requireOp(latticeInput, 8,
                "final_density interpolated mul");
        Node scale = node(latticeInput, 1);
        requireOp(scale, 0, "final_density 0.64 scale");
        if (Float.floatToRawIntBits((Float) scale.args[0])
                != Float.floatToRawIntBits(0.64F)) {
            throw new IOException("final_density scale is not pinned 0.64f");
        }
        Node beardifier = node(root, 1); requireOp(beardifier, 30,
                "final_density beardifier");
        return new FullDensityGraph(compile(latticeInput), compile(node(min, 1)),
                wrapperCacheSlots);
    }

    /** 26.3에는 이 1.21.4 router field가 없으므로 근사하지 않고 거부한다. */
    public McDensityFunction initialDensityWithoutJaggedness() throws IOException {
        throw new IOException("26.3 schema has no initial_density_without_jaggedness");
    }
    public McDensityFunction initialDensity() throws IOException {
        throw new IOException("26.3 schema has no initial_density router field");
    }
    public McDensityFunction finalDensityNoCaves() throws IOException {
        throw new IOException("26.3 graph has no evidence-backed no-caves transform");
    }
    public McDensityFunction finalDensityNoCavesBeforeSqueeze() throws IOException {
        throw new IOException("26.3 graph has no evidence-backed no-caves transform");
    }
    public McDensityFunction finalDensityNoCavesLatticeInput() throws IOException {
        throw new IOException("26.3 graph has no evidence-backed no-caves transform");
    }

    public McDensityFunction resolve(String identifier) throws IOException {
        String id = normalize(identifier);
        McDensityFunction cached = functions.get(id);
        if (cached != null) return cached;
        Node definition = requireDefinition(id);
        if (!resolving.add(id)) throw new IOException("cyclic density reference: " + id);
        try {
            McDensityFunction value = compile(definition);
            functions.put(id, value);
            return value;
        } finally {
            resolving.remove(id);
        }
    }

    private McDensityFunction compile(Node node) throws IOException {
        return switch (node.op) {
            case 0 -> new McDensityFunctions.Constant((Float) node.args[0]);
            case 1 -> resolve((String) node.args[0]);
            case 2 -> new McDensityFunctions.Noise(noise((String) node.args[0]),
                    (Double) node.args[1], (Double) node.args[2]);
            case 3 -> new McDensityFunctions.ShiftedNoise(compile(node(node, 0)),
                    compile(node(node, 1)), compile(node(node, 2)),
                    noise((String) node.args[3]), (Double) node.args[4],
                    (Double) node.args[5]);
            case 4 -> gradient(node);
            case 5 -> new McDensityFunctions.RangeChoice(compile(node(node, 0)),
                    (Float) node.args[1], (Float) node.args[2], compile(node(node, 3)),
                    compile(node(node, 4)));
            case 6 -> binary(node, McDensityFunctions.Binary.Kind.ADD);
            case 7 -> binary(node, McDensityFunctions.Binary.Kind.SUB);
            case 8 -> binary(node, McDensityFunctions.Binary.Kind.MUL);
            case 9 -> binary(node, McDensityFunctions.Binary.Kind.DIV);
            case 10 -> binary(node, McDensityFunctions.Binary.Kind.MIN);
            case 11 -> binary(node, McDensityFunctions.Binary.Kind.MAX);
            case 12 -> mapped(node, McDensityFunctions.Mapped.Kind.ABS);
            case 13 -> mapped(node, McDensityFunctions.Mapped.Kind.SQUARE);
            case 14 -> mapped(node, McDensityFunctions.Mapped.Kind.CUBE);
            case 15 -> mapped(node, McDensityFunctions.Mapped.Kind.HALF_NEGATIVE);
            case 16 -> mapped(node, McDensityFunctions.Mapped.Kind.QUARTER_NEGATIVE);
            case 17 -> mapped(node, McDensityFunctions.Mapped.Kind.SQUEEZE);
            case 18 -> new McDensityFunctions.Clamp(compile(node(node, 0)),
                    (Float) node.args[1], (Float) node.args[2]);
            case 19 -> wrapper(node, McDensityFunctions.Wrapper.Kind.CACHE_ONCE);
            case 20 -> wrapper(node, McDensityFunctions.Wrapper.Kind.CACHE_2D);
            case 21 -> wrapper(node, McDensityFunctions.Wrapper.Kind.FLAT_CACHE);
            case 22 -> wrapper(node, McDensityFunctions.Wrapper.Kind.INTERPOLATED);
            case 23 -> wrapper(node, McDensityFunctions.Wrapper.Kind.BLEND_DENSITY);
            case 24 -> spline(node);
            case 25 -> intervalSelect(node);
            case 26 -> new McDensityFunctions.Lerp(compile(node(node, 0)),
                    compile(node(node, 1)), compile(node(node, 2)));
            case 27 -> new McDensityFunctions.OldBlendedNoise(
                    random.fromHashOf("minecraft:terrain"), (Double) node.args[0],
                    (Double) node.args[1], (Double) node.args[2],
                    (Double) node.args[3], (Double) node.args[4]);
            case 28 -> new McDensityFunctions.Constant(1.0F);
            case 29, 30 -> new McDensityFunctions.Constant(0.0F);
            case 31 -> new McDensityFunctions.FindTopSurface(compile(node(node, 0)),
                    compile(node(node, 1)), (Integer) node.args[2], (Integer) node.args[3]);
            case 32 -> new McDensityFunctions.Shift(noise((String) node.args[0]),
                    McDensityFunctions.Shift.Kind.A);
            case 33 -> new McDensityFunctions.Shift(noise((String) node.args[0]),
                    McDensityFunctions.Shift.Kind.B);
            default -> throw new IOException("unsupported 26.3 density opcode: " + node.op);
        };
    }

    private McDensityFunction gradient(Node node) throws IOException {
        String axis = (String) node.args[0];
        String tiling = (String) node.args[1];
        if (!axis.equals("y") || !tiling.equals("clamp_to_edge")) {
            throw new IOException("unsupported 26.3 gradient axis/tiling: " + axis + "/" + tiling);
        }
        return new McDensityFunctions.YClampedGradient((Integer) node.args[2],
                (Integer) node.args[3], (Float) node.args[4], (Float) node.args[5]);
    }

    private McDensityFunction binary(Node node, McDensityFunctions.Binary.Kind kind)
            throws IOException {
        return new McDensityFunctions.Binary(kind, compile(node(node, 0)),
                compile(node(node, 1)));
    }

    private McDensityFunction mapped(Node node, McDensityFunctions.Mapped.Kind kind)
            throws IOException {
        return new McDensityFunctions.Mapped(kind, compile(node(node, 0)));
    }

    private McDensityFunction wrapper(Node node, McDensityFunctions.Wrapper.Kind kind)
            throws IOException {
        int slot = kind == McDensityFunctions.Wrapper.Kind.BLEND_DENSITY
                ? -1 : wrapperCacheSlots++;
        return new McDensityFunctions.Wrapper(kind, compile(node(node, 0)), slot);
    }

    private McDensityFunction spline(Node node) throws IOException {
        McDensityFunction coordinate = compile(node(node, 0));
        float[] locations = (float[]) node.args[1];
        float[] derivatives = (float[]) node.args[2];
        Node[] valueNodes = (Node[]) node.args[3];
        McDensityFunction[] values = new McDensityFunction[valueNodes.length];
        for (int i = 0; i < values.length; i++) values[i] = compile(valueNodes[i]);
        return new McDensityFunctions.Spline(coordinate, locations, derivatives, values);
    }

    private McDensityFunction intervalSelect(Node node) throws IOException {
        Node[] valueNodes = (Node[]) node.args[2];
        McDensityFunction[] values = new McDensityFunction[valueNodes.length];
        for (int i = 0; i < values.length; i++) values[i] = compile(valueNodes[i]);
        return new McDensityFunctions.IntervalSelect(compile(node(node, 0)),
                (float[]) node.args[1], values);
    }

    private McNormalNoise noise(String identifier) throws IOException {
        String id = normalize(identifier);
        McNormalNoise cached = noises.get(id);
        if (cached != null) return cached;
        try {
            McNormalNoise value = Mc263NoiseRegistry.create(random, id);
            noises.put(id, value);
            return value;
        } catch (IllegalArgumentException unsupported) {
            throw new IOException("unsupported 26.3 named noise: " + id, unsupported);
        }
    }

    private Node requireDefinition(String id) throws IOException {
        Node node = definitions.get(id);
        if (node == null) throw new IOException("unsupported 26.3 density reference: " + id);
        return node;
    }

    private static void requireOp(Node node, int op, String owner) throws IOException {
        if (node.op != op) throw new IOException(owner + " opcode mismatch: " + node.op);
    }

    private static Node node(Node owner, int index) throws IOException {
        Object value = owner.args[index];
        if (value instanceof Node result) return result;
        throw new IOException("density child is not a node at opcode " + owner.op + "/" + index);
    }

    private static Node readNode(DataInputStream input) throws IOException {
        int op = input.readUnsignedByte();
        return switch (op) {
            case 0 -> n(op, input.readFloat());
            case 1 -> n(op, input.readUTF());
            case 2 -> n(op, input.readUTF(), input.readDouble(), input.readDouble());
            case 3 -> n(op, readNode(input), readNode(input), readNode(input), input.readUTF(),
                    input.readDouble(), input.readDouble());
            case 4 -> n(op, input.readUTF(), input.readUTF(), input.readInt(), input.readInt(),
                    input.readFloat(), input.readFloat());
            case 5 -> n(op, readNode(input), input.readFloat(), input.readFloat(),
                    readNode(input), readNode(input));
            case 6, 7, 8, 9, 10, 11 -> n(op, readNode(input), readNode(input));
            case 12, 13, 14, 15, 16, 17, 19, 20, 21, 22, 23 -> n(op, readNode(input));
            case 18 -> n(op, readNode(input), input.readFloat(), input.readFloat());
            case 24 -> readSpline(input);
            case 25 -> readIntervalSelect(input);
            case 26 -> n(op, readNode(input), readNode(input), readNode(input));
            case 27 -> n(op, input.readDouble(), input.readDouble(), input.readDouble(),
                    input.readDouble(), input.readDouble());
            case 28, 29, 30 -> n(op);
            case 31 -> n(op, readNode(input), readNode(input), input.readInt(), input.readInt());
            case 32, 33 -> n(op, input.readUTF());
            default -> throw new IOException("unsupported 26.3 density opcode in payload: " + op);
        };
    }

    private static Node readSpline(DataInputStream input) throws IOException {
        Node coordinate = readNode(input);
        int count = boundedCount(input.readInt(), 2, 10_000, "spline points");
        float[] locations = new float[count];
        float[] derivatives = new float[count];
        Node[] values = new Node[count];
        for (int i = 0; i < count; i++) {
            locations[i] = input.readFloat();
            derivatives[i] = input.readFloat();
            values[i] = input.readUnsignedByte() == 1 ? readSpline(input) : readNode(input);
            if (i > 0 && locations[i] <= locations[i - 1]) {
                throw new IOException("26.3 spline points are not strictly ordered");
            }
        }
        return n(24, coordinate, locations, derivatives, values);
    }

    private static Node readIntervalSelect(DataInputStream input) throws IOException {
        Node selector = readNode(input);
        int thresholdCount = boundedCount(input.readInt(), 1, 1_000, "interval thresholds");
        float[] thresholds = new float[thresholdCount];
        for (int i = 0; i < thresholdCount; i++) thresholds[i] = input.readFloat();
        int functionCount = boundedCount(input.readInt(), 2, 1_001, "interval functions");
        if (functionCount != thresholdCount + 1) {
            throw new IOException("26.3 interval arity mismatch");
        }
        Node[] values = new Node[functionCount];
        for (int i = 0; i < functionCount; i++) values[i] = readNode(input);
        return n(25, selector, thresholds, values);
    }

    private static int boundedCount(int count, int min, int max, String owner) throws IOException {
        if (count < min || count > max) throw new IOException(owner + " out of bounds: " + count);
        return count;
    }

    private static Node n(int op, Object... args) { return new Node(op, args); }
    private static String normalize(String id) { return id.indexOf(':') < 0 ? "minecraft:" + id : id; }
    private record Node(int op, Object[] args) { }

    public static final class FullDensityGraph {
        private final McDensityFunction mainLatticeInput;
        private final McDensityFunction noodle;
        private final int cacheSlotCount;
        private FullDensityGraph(McDensityFunction mainLatticeInput, McDensityFunction noodle,
                int cacheSlotCount) {
            this.mainLatticeInput = mainLatticeInput;
            this.noodle = noodle;
            this.cacheSlotCount = cacheSlotCount;
        }
        public McDensityFunction mainLatticeInput() { return mainLatticeInput; }
        public McDensityFunction noodle() { return noodle; }
        public int cacheSlotCount() { return cacheSlotCount; }
    }
}
