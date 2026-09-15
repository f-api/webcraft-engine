package com.gameexpert.terrain.mc.structure;

import com.gameexpert.terrain.mc.feature.Mc263FeatureBlockState;
import com.gameexpert.terrain.mc.feature.Mc263FinalChunkCodec;
import com.gameexpert.terrain.mc.structure.Mc263OceanRuinGrammarData.Command;
import com.gameexpert.terrain.mc.structure.Mc263OceanRuinGrammarData.Grammar;
import com.gameexpert.terrain.mc.structure.Mc263OceanRuinGrammarData.Op;
import com.gameexpert.terrain.mc.structure.Mc263OceanRuinProgram.BlockPos;
import com.gameexpert.terrain.mc.structure.Mc263OceanRuinProgram.LegacyRandom;
import com.gameexpert.terrain.mc.structure.Mc263OceanRuinProgram.PiecePlan;
import com.gameexpert.terrain.mc.structure.Mc263OceanRuinProgram.Plan;
import com.gameexpert.terrain.mc.structure.Mc263OceanRuinProgram.Rotation;
import com.gameexpert.terrain.mc.structure.Mc263OceanRuinProgram.Type;
import com.gameexpert.terrain.mc.structure.Mc263OceanRuinSettlement.Cell;
import com.gameexpert.terrain.mc.structure.Mc263OceanRuinSettlement.Clip;
import com.gameexpert.terrain.mc.structure.Mc263OceanRuinSettlement.Execution;
import com.gameexpert.terrain.mc.structure.Mc263OceanRuinSettlement.Settlement;
import com.gameexpert.terrain.mc.structure.Mc263OceanRuinSettlement.TemplateProgram;
import com.gameexpert.terrain.mc.structure.Mc263OceanRuinSettlement.Terrain;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Complete cold/warm direct-piece executor for the accepted v3 ocean-ruin grammar.
 *
 * <p>The shared registry decides placement and supplies the persisted, seabed-projected plan. This
 * executor owns the exact template expansion, processor order, marker RNG, schema-4 sidecars and
 * atomic STR/final-carrier successor. It performs all grammar and capability checks before the
 * caller's post RNG can advance.</p>
 */
public final class Mc263OceanRuinProductionExecutor {
    private static final int ARCHAEOLOGY_CAP = 5;
    private static final long MULTIPLIER = 0x5deece66dL;
    private static final long ADDEND = 0xbL;
    private static final long MASK = (1L << 48) - 1L;

    private Mc263OceanRuinProductionExecutor() { }

    public static Settlement executeCold(long worldSeed, Plan projected, Terrain terrain,
            LegacyRandom postRandom, Clip clip, int references,
            Mc263FinalChunkCodec.FinalChunk source) {
        return execute(Type.COLD, worldSeed, projected, terrain, postRandom, clip,
                references, source);
    }

    public static Settlement executeWarm(long worldSeed, Plan projected, Terrain terrain,
            LegacyRandom postRandom, Clip clip, int references,
            Mc263FinalChunkCodec.FinalChunk source) {
        return execute(Type.WARM, worldSeed, projected, terrain, postRandom, clip,
                references, source);
    }

    public static Settlement execute(Type expectedType, long worldSeed, Plan projected,
            Terrain terrain, LegacyRandom postRandom, Clip clip, int references,
            Mc263FinalChunkCodec.FinalChunk source) {
        Objects.requireNonNull(source, "ocean-ruin final carrier");
        Clip sourceClip = Clip.chunk(source.chunkX(), source.chunkZ());
        if (!sourceClip.equals(clip)) {
            throw new IllegalArgumentException("ocean-ruin settlement requires target chunk clip");
        }
        if (references < 0) throw new IllegalArgumentException("negative ocean-ruin references");
        LegacyRandom settlementRandom = postRandom.fork();
        Execution execution = prepare(expectedType, worldSeed, projected, terrain,
                settlementRandom, clip);
        Settlement settlement = Mc263OceanRuinSettlement.settle(execution, references, source);
        postRandom.commitFrom(settlementRandom);
        return settlement;
    }

    /** Executes cold production semantics without requiring a final authenticated carrier. */
    public static Execution prepareCold(long worldSeed, Plan projected, Terrain terrain,
            LegacyRandom postRandom, Clip clip) {
        return prepare(Type.COLD, worldSeed, projected, terrain, postRandom, clip);
    }

    /** Executes warm production semantics without requiring a final authenticated carrier. */
    public static Execution prepareWarm(long worldSeed, Plan projected, Terrain terrain,
            LegacyRandom postRandom, Clip clip) {
        return prepare(Type.WARM, worldSeed, projected, terrain, postRandom, clip);
    }

    /**
     * Produces the pre-assembly semantic execution. Callers that own FEATURES dispatch must use
     * {@code Mc263OceanRuinSettlement.settleFeatures}; direct callers retain {@link #execute}.
     */
    public static Execution prepare(Type expectedType, long worldSeed, Plan projected,
            Terrain terrain, LegacyRandom postRandom, Clip clip) {
        Objects.requireNonNull(expectedType, "ocean-ruin executor type");
        Objects.requireNonNull(projected, "projected ocean-ruin plan");
        Objects.requireNonNull(terrain, "ocean-ruin terrain");
        Objects.requireNonNull(postRandom, "ocean-ruin post RNG");
        Objects.requireNonNull(clip, "ocean-ruin clip");
        if (projected.type() != expectedType) {
            throw new IllegalArgumentException("ocean-ruin executor/type mismatch");
        }
        requireAcceptedIdentity();

        HashMap<Integer, TemplateProgram> programs = new HashMap<>();
        for (PiecePlan piece : projected.pieces()) {
            TemplateProgram program = program(worldSeed, piece);
            preflightRepresentable(piece, program, clip);
            programs.put(piece.ordinal(), program);
        }
        LegacyRandom settlementRandom = postRandom.fork();
        Execution execution = Mc263OceanRuinSettlement.executePieces(projected,
                piece -> programs.get(piece.ordinal()), terrain, settlementRandom, clip);
        postRandom.commitFrom(settlementRandom);
        return execution;
    }

    static TemplateProgram program(long worldSeed, PiecePlan piece) {
        Objects.requireNonNull(piece, "ocean-ruin piece");
        Grammar grammar = Mc263OceanRuinGrammarData.require(piece.template().key());
        if (grammar.sizeX() != piece.template().sizeX()
                || grammar.sizeY() != piece.template().sizeY()
                || grammar.sizeZ() != piece.template().sizeZ()
                || grammar.binaryLength() != piece.template().binaryLength()
                || !grammar.binarySha256().equals(piece.template().binarySha256())) {
            throw new IllegalArgumentException("ocean-ruin v3 grammar/descriptor mismatch");
        }

        ArrayList<Expanded> expanded = new ArrayList<>(grammar.blockCount());
        for (Command command : grammar.commands()) {
            if (command.op() == Op.EMPTY_CHEST) {
                throw new IllegalArgumentException("unexpected ocean-ruin EMPTY_CHEST command");
            }
            for (int index = 0; index < command.count(); index++) {
                BlockPos local = new BlockPos(
                        Math.addExact(command.x(), Math.multiplyExact(command.dx(), index)),
                        Math.addExact(command.y(), Math.multiplyExact(command.dy(), index)),
                        Math.addExact(command.z(), Math.multiplyExact(command.dz(), index)));
                String state = grammar.states().get(command.state());
                expanded.add(new Expanded(command.ordinal() + index, local, state,
                        command.op() == Op.DATA));
            }
        }
        if (expanded.size() != grammar.blockCount()) {
            throw new IllegalArgumentException("ocean-ruin expansion count drift");
        }

        ArrayList<Processed> survivors = new ArrayList<>();
        for (Expanded value : expanded) {
            BlockPos world = transform(piece.templatePosition(), value.local(), piece.rotation());
            float sample = positional(world).nextFloat();
            boolean ignored = value.data() || blockKey(value.state()).equals("minecraft:air")
                    || blockKey(value.state()).equals("minecraft:structure_block");
            if (!ignored && sample <= piece.integrity()) {
                survivors.add(new Processed(value.ordinal(), value.local(), world,
                        value.state(), sample));
            }
        }

        int[] shuffled = shuffledIndices(worldSeed, piece.templatePosition(), survivors.size());
        HashMap<Integer, Long> archaeology = new HashMap<>();
        String input = piece.type() == Type.COLD ? "minecraft:gravel" : "minecraft:sand";
        for (int index : shuffled) {
            Processed candidate = survivors.get(index);
            if (!blockKey(candidate.state()).equals(input)) continue;
            archaeology.put(candidate.ordinal(), positional(candidate.world()).nextLong());
            if (archaeology.size() == ARCHAEOLOGY_CAP) break;
        }

        ArrayList<Cell> cells = new ArrayList<>(survivors.size());
        for (Processed survivor : survivors) {
            Long seed = archaeology.get(survivor.ordinal());
            cells.add(new Cell(survivor.local(), survivor.state(), survivor.rotSample(),
                    seed != null, seed == null ? 0L : seed));
        }
        return new TemplateProgram(grammar.key(), grammar.binaryLength(),
                grammar.binarySha256(), cells);
    }

    private static void preflightRepresentable(PiecePlan piece, TemplateProgram program,
            Clip clip) {
        Set<String> states = new HashSet<>();
        for (Cell cell : program.orderedCells()) {
            BlockPos world = transform(piece.templatePosition(), cell.localPosition(),
                    piece.rotation());
            if (!clip.contains(world)) continue;
            String state = cell.archaeologySelected()
                    ? piece.type() == Type.COLD
                            ? "minecraft:suspicious_gravel[dusted=0]"
                            : "minecraft:suspicious_sand[dusted=0]"
                    : rotateState(cell.sourceExactState(), piece.rotation());
            states.add(state);
            if (state.contains("waterlogged=false")) {
                states.add(state.replace("waterlogged=false", "waterlogged=true"));
            }
        }
        for (String state : states) Mc263FeatureBlockState.fromExact(state);
    }

    private static int[] shuffledIndices(long worldSeed, BlockPos origin, int size) {
        int[] values = new int[size];
        for (int index = 0; index < size; index++) values[index] = index;
        Legacy48 root = new Legacy48(worldSeed);
        Legacy48 random = new Legacy48(root.nextLong() ^ seed(origin));
        for (int bound = size; bound > 1; bound--) {
            int selected = random.nextInt(bound), end = bound - 1;
            int swap = values[end]; values[end] = values[selected]; values[selected] = swap;
        }
        return values;
    }

    private static LegacyRandom positional(BlockPos position) {
        return new LegacyRandom(seed(position));
    }

    static long seed(BlockPos position) {
        long value = (long) (position.x() * 3_129_871)
                ^ (long) position.z() * 116_129_781L ^ position.y();
        value = value * value * 42_317_861L + value * 11L;
        return value >> 16;
    }

    static String rotateState(String exact, Rotation rotation) {
        int facing = exact.indexOf("facing=");
        if (facing < 0 || rotation == Rotation.NONE) return exact.replace(", ", ",");
        int start = facing + "facing=".length();
        int end = exact.indexOf(',', start);
        if (end < 0) end = exact.indexOf(']', start);
        if (end < 0) throw new IllegalArgumentException("malformed directional ocean-ruin state");
        String direction = exact.substring(start, end);
        String rotated = rotateDirection(direction, rotation);
        return (exact.substring(0, start) + rotated + exact.substring(end)).replace(", ", ",");
    }

    private static String rotateDirection(String direction, Rotation rotation) {
        List<String> horizontal = List.of("north", "east", "south", "west");
        int index = horizontal.indexOf(direction);
        if (index < 0) return direction;
        int turns = switch (rotation) {
            case NONE -> 0;
            case CLOCKWISE_90 -> 1;
            case CLOCKWISE_180 -> 2;
            case COUNTERCLOCKWISE_90 -> 3;
        };
        return horizontal.get((index + turns) & 3);
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
        return new BlockPos(Math.addExact(origin.x(), x),
                Math.addExact(origin.y(), local.y()), Math.addExact(origin.z(), z));
    }

    private static String blockKey(String exact) {
        int bracket = exact.indexOf('['); return bracket < 0 ? exact : exact.substring(0, bracket);
    }

    private static void requireAcceptedIdentity() {
        if (!Mc263OceanRuinGrammarData.ORACLE_SHA256.equals(Mc263OceanRuinProgram.ORACLE_SHA256)
                || !Mc263OceanRuinGrammarData.CONTRACT_SHA256.equals(
                        Mc263OceanRuinProgram.ORACLE_CONTRACT_SHA256)
                || !Mc263OceanRuinGrammarData.SOURCE_SHA256.equals(
                        Mc263OceanRuinProgram.ORACLE_SOURCE_SHA256)) {
            throw new IllegalStateException("ocean-ruin accepted oracle identity drift");
        }
    }

    private record Expanded(int ordinal, BlockPos local, String state, boolean data) { }
    private record Processed(int ordinal, BlockPos local, BlockPos world, String state,
            float rotSample) { }

    private static final class Legacy48 {
        private long state;
        Legacy48(long seed) { state = (seed ^ MULTIPLIER) & MASK; }
        int next(int bits) {
            state = (state * MULTIPLIER + ADDEND) & MASK;
            return (int) (state >>> (48 - bits));
        }
        int nextInt(int bound) {
            if (bound <= 0) throw new IllegalArgumentException("nonpositive random bound");
            if ((bound & -bound) == bound) return (int) ((bound * (long) next(31)) >> 31);
            int bits, value;
            do { bits = next(31); value = bits % bound; }
            while (bits - value + bound - 1 < 0);
            return value;
        }
        long nextLong() { return ((long) next(32) << 32) + next(32); }
    }
}
