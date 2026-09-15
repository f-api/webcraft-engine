package com.gameexpert.terrain.mc.feature;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Authenticated production lane for the pinned 26.3 speleothem POST {@code updateShape} closure.
 *
 * <p>The official {@code SpeleothemBlock#updateShape} reads exactly two cells — the anchor cell
 * {@code pos.relative(tipDirection.getOpposite())} and the growth cell
 * {@code pos.relative(tipDirection)} — and distinguishes them only by two official predicates:
 * {@code isSpeleothemWithDirection}/{@code BlockState#is(Block)} (exact speleothem state
 * identity) and {@code BlockState#isFaceSturdy} (already authenticated per face by the pinned
 * POST neighbour registry). The published transcript therefore enumerates the complete official
 * verdict grid over that bounded class domain, and this lane only looks a verdict up: no
 * thickness rule, support rule, or scheduling rule is reconstructed here.</p>
 *
 * <p>The published closure is bound to an empty official block-tick schedule at the subject
 * position ({@code TICKSTATE hasScheduledTick 0} in the receipt), which is the only state the
 * POST mark loop can present: a mark is visited once, its six direction steps run in the official
 * order, and the sole official schedule a speleothem can emit for its own position happens on the
 * last step of that loop. Anything outside the published domain fails closed.</p>
 */
public final class Mc263SpeleothemShapeAuthority {
    private static final String WATER = "minecraft:water";
    private static final int NORMAL_PRIORITY = 0;
    private static final List<String> SUBJECTS = loadSubjects();
    private static final Map<String, Integer> SUBJECT_INDEX = indexSubjects();
    private static final Verdict[] VERDICTS = loadVerdicts();
    private static final String[] GRIDS = loadGrids();
    private static final Verdict[] HORIZONTAL = loadHorizontal();

    private Mc263SpeleothemShapeAuthority() {}

    /** One official updateShape verdict: the successor state plus its official tick schedule. */
    private record Verdict(int result, int blockTicks, int blockDelay, int fluidTicks,
                           int fluidDelay) {
        static Verdict parse(String[] row, int offset) {
            Verdict verdict = new Verdict(Integer.parseInt(row[offset]),
                    Integer.parseInt(row[offset + 1]), Integer.parseInt(row[offset + 2]),
                    Integer.parseInt(row[offset + 3]), Integer.parseInt(row[offset + 4]));
            if (verdict.result < 0 || verdict.result >= Mc263SpeleothemShapeClosureData
                    .SUBJECT_COUNT || verdict.blockTicks < 0 || verdict.blockTicks > 1
                    || verdict.fluidTicks < 0 || verdict.fluidTicks > 1) {
                throw new ExceptionInInitializerError("malformed speleothem POST verdict");
            }
            return verdict;
        }
    }

    /** True when the exact state is one of the published official speleothem subjects. */
    public static boolean handles(Mc263FeatureBlockState state) {
        return SUBJECT_INDEX.containsKey(
                Objects.requireNonNull(state, "POST speleothem subject").exactState());
    }

    /**
     * Applies the official speleothem POST step, or returns {@code null} when the subject is not
     * a published speleothem state and the caller must keep resolving.
     */
    public static Mc263FeatureBlockState updateShape(
            Mc263PostprocessResolver.MutableWorld world,
            Mc263PostprocessResolver.Position position,
            Mc263FeatureBlockState currentState,
            Mc263PostprocessResolver.Direction direction) {
        Objects.requireNonNull(world, "POST world");
        Objects.requireNonNull(position, "POST shape position");
        Objects.requireNonNull(direction, "POST shape direction");
        Integer subject = SUBJECT_INDEX.get(
                Objects.requireNonNull(currentState, "POST shape state").exactState());
        if (subject == null) return null;
        Mc263PostprocessResolver.Direction tip = tipDirection(currentState);
        Verdict verdict;
        if (direction != Mc263PostprocessResolver.Direction.DOWN
                && direction != Mc263PostprocessResolver.Direction.UP) {
            verdict = HORIZONTAL[subject];
        } else {
            int anchor = classify(world.blockState(position.relative(opposite(tip))), tip);
            int growth = classify(world.blockState(position.relative(tip)), tip);
            String grid = GRIDS[subject * 2
                    + (direction == Mc263PostprocessResolver.Direction.DOWN ? 0 : 1)];
            verdict = VERDICTS[Mc263SpeleothemShapeClosureData.DIGITS.indexOf(
                    grid.charAt(anchor * Mc263SpeleothemShapeClosureData.CLASS_COUNT + growth))];
        }
        // Official order: the waterlogged fluid schedule precedes any unsupported-tip block
        // schedule inside one official updateShape call.
        if (verdict.fluidTicks() == 1) {
            world.scheduleFluidTick(position, WATER, verdict.fluidDelay(), NORMAL_PRIORITY);
        }
        if (verdict.blockTicks() == 1) {
            world.scheduleBlockTick(position, currentState.blockKey(), verdict.blockDelay(),
                    NORMAL_PRIORITY);
        }
        return Mc263FeatureBlockState.fromExact(SUBJECTS.get(verdict.result()));
    }

    /** Official neighbour class of one cell: an exact speleothem state, STURDY, or PLAIN. */
    private static int classify(Mc263FeatureBlockState neighbor,
            Mc263PostprocessResolver.Direction face) {
        Integer speleothem = SUBJECT_INDEX.get(
                Objects.requireNonNull(neighbor, "POST speleothem neighbour").exactState());
        if (speleothem != null) return speleothem;
        return Mc263PostprocessResolver.requireNeighborAuthority(neighbor).faceSturdy(face)
                ? Mc263SpeleothemShapeClosureData.STURDY_CLASS
                : Mc263SpeleothemShapeClosureData.PLAIN_CLASS;
    }

    private static Mc263PostprocessResolver.Direction tipDirection(
            Mc263FeatureBlockState state) {
        String exact = state.exactState();
        if (exact.contains("vertical_direction=down")) {
            return Mc263PostprocessResolver.Direction.DOWN;
        }
        if (exact.contains("vertical_direction=up")) {
            return Mc263PostprocessResolver.Direction.UP;
        }
        throw new IllegalStateException("no authenticated POST speleothem tip direction for "
                + exact);
    }

    private static Mc263PostprocessResolver.Direction opposite(
            Mc263PostprocessResolver.Direction direction) {
        return direction == Mc263PostprocessResolver.Direction.DOWN
                ? Mc263PostprocessResolver.Direction.UP
                : Mc263PostprocessResolver.Direction.DOWN;
    }

    private static List<String> loadSubjects() {
        List<String> subjects = new java.util.ArrayList<>();
        for (String line : Mc263SpeleothemShapeClosureData.rows()) {
            String[] row = line.split("\\t", -1);
            if (!row[0].equals("S")) continue;
            if (row.length != 3 || Integer.parseInt(row[1]) != subjects.size()) {
                throw new ExceptionInInitializerError("malformed speleothem subject row");
            }
            subjects.add(row[2]);
        }
        if (subjects.size() != Mc263SpeleothemShapeClosureData.SUBJECT_COUNT) {
            throw new ExceptionInInitializerError("speleothem subject cardinality drift");
        }
        return List.copyOf(subjects);
    }

    private static Map<String, Integer> indexSubjects() {
        Map<String, Integer> index = new LinkedHashMap<>();
        for (int position = 0; position < SUBJECTS.size(); position++) {
            if (index.put(SUBJECTS.get(position), position) != null) {
                throw new ExceptionInInitializerError("duplicate speleothem subject");
            }
        }
        return Map.copyOf(index);
    }

    private static Verdict[] loadVerdicts() {
        Verdict[] verdicts = new Verdict[Mc263SpeleothemShapeClosureData.VERDICT_COUNT];
        int count = 0;
        for (String line : Mc263SpeleothemShapeClosureData.rows()) {
            String[] row = line.split("\\t", -1);
            if (!row[0].equals("V")) continue;
            if (row.length != 7 || Integer.parseInt(row[1]) != count) {
                throw new ExceptionInInitializerError("malformed speleothem verdict row");
            }
            verdicts[count++] = Verdict.parse(row, 2);
        }
        if (count != verdicts.length) {
            throw new ExceptionInInitializerError("speleothem verdict cardinality drift");
        }
        return verdicts;
    }

    private static String[] loadGrids() {
        String[] grids = new String[Mc263SpeleothemShapeClosureData.SUBJECT_COUNT * 2];
        int cells = Mc263SpeleothemShapeClosureData.CLASS_COUNT
                * Mc263SpeleothemShapeClosureData.CLASS_COUNT;
        int count = 0;
        for (String line : Mc263SpeleothemShapeClosureData.rows()) {
            String[] row = line.split("\\t", -1);
            if (!row[0].equals("M")) continue;
            int slot = Integer.parseInt(row[1]) * 2 + Integer.parseInt(row[2]);
            if (row.length != 4 || row[3].length() != cells || grids[slot] != null) {
                throw new ExceptionInInitializerError("malformed speleothem verdict grid");
            }
            for (int position = 0; position < cells; position++) {
                int digit = Mc263SpeleothemShapeClosureData.DIGITS.indexOf(row[3].charAt(position));
                if (digit < 0 || digit >= Mc263SpeleothemShapeClosureData.VERDICT_COUNT) {
                    throw new ExceptionInInitializerError("speleothem grid digit out of range");
                }
            }
            grids[slot] = row[3];
            count++;
        }
        if (count != grids.length) {
            throw new ExceptionInInitializerError("speleothem verdict grid cardinality drift");
        }
        return grids;
    }

    private static Verdict[] loadHorizontal() {
        Verdict[] horizontal = new Verdict[Mc263SpeleothemShapeClosureData.SUBJECT_COUNT];
        int count = 0;
        for (String line : Mc263SpeleothemShapeClosureData.rows()) {
            String[] row = line.split("\\t", -1);
            if (!row[0].equals("H")) continue;
            int subject = Integer.parseInt(row[1]);
            if (row.length != 7 || horizontal[subject] != null) {
                throw new ExceptionInInitializerError("malformed speleothem horizontal row");
            }
            Verdict verdict = Verdict.parse(row, 2);
            if (verdict.result() != subject || verdict.blockTicks() != 0) {
                throw new ExceptionInInitializerError(
                        "official horizontal speleothem step is not the identity");
            }
            horizontal[subject] = verdict;
            count++;
        }
        if (count != horizontal.length) {
            throw new ExceptionInInitializerError("speleothem horizontal cardinality drift");
        }
        return horizontal;
    }
}
