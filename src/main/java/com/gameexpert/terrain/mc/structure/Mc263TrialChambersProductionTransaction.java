package com.gameexpert.terrain.mc.structure;

import com.gameexpert.terrain.mc.feature.Mc263FeatureBlockState;
import com.gameexpert.terrain.mc.structure.Mc263JigsawStructureExecutor.PiecePlacement;
import com.gameexpert.terrain.mc.structure.Mc263TemplatePlacementExecutor.BlockEntitySynthesizer;
import com.gameexpert.terrain.mc.structure.Mc263TemplatePlacementExecutor.Cell;
import com.gameexpert.terrain.mc.structure.Mc263TemplatePlacementExecutor.CellProcessor;
import com.gameexpert.terrain.mc.structure.Mc263TemplatePlacementExecutor.CellProgram;
import com.gameexpert.terrain.mc.structure.Mc263TemplatePlacementExecutor.IgnorePolicy;
import com.gameexpert.terrain.mc.structure.Mc263TemplatePlacementExecutor.Mirror;
import com.gameexpert.terrain.mc.structure.Mc263TemplatePlacementExecutor.Piece;
import com.gameexpert.terrain.mc.structure.Mc263TemplatePlacementExecutor.PlacementRandom;
import com.gameexpert.terrain.mc.structure.Mc263TemplatePlacementExecutor.PlacementWorld;
import com.gameexpert.terrain.mc.structure.Mc263TemplatePlacementExecutor.ProcessedCell;
import com.gameexpert.terrain.mc.structure.Mc263TemplatePlacementExecutor.Result;
import com.gameexpert.terrain.mc.structure.Mc263TemplatePlacementExecutor.Rotation;
import com.gameexpert.terrain.mc.structure.Mc263TemplatePlacementExecutor.Vec;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Production Trial Chambers template transaction.
 *
 * <p>The persisted start has already been regenerated and authenticated by
 * {@link Mc263TrialChambersProducer}. This adapter executes every intersecting jigsaw piece through
 * the family-neutral {@link Mc263TemplatePlacementExecutor}; its only family-specific work is
 * adapting the authenticated Trial template commands, copper/protected-block processor chain and
 * canonical DATA block-entity facts. The two G1T settlement witnesses remain independent exact
 * verification oracles and are not an admission list for production starts.</p>
 */
public final class Mc263TrialChambersProductionTransaction {
    private static final String COPPER_PROCESSOR =
            "minecraft:trial_chambers_copper_bulb_degradation";
    private static final String FIXED_CONTAINER_DRAW =
            "webcraft:internal/trial_chambers/fixed_container_next_long";
    private static final String NON_CHEST_CONTAINER_DRAW =
            "webcraft:internal/trial_chambers/non_chest_container_next_long";
    private static final String TRIAL_CHAMBERS_LOOT_TUPLES_SHA256 =
            "1b35e17b0ba0f826db9ec1906282eefdf49a5c65a680719bc5469ca7d9af3ff9";
    private static final String DISPENSER_CHAMBER_LOOT =
            "minecraft:dispensers/trial_chambers/chamber";
    private static final String DISPENSER_CORRIDOR_LOOT =
            "minecraft:dispensers/trial_chambers/corridor";
    private static final String DECORATED_POT_CORRIDOR_LOOT =
            "minecraft:pots/trial_chambers/corridor";
    private static final long RANDOM_MULTIPLIER = 0x5DEECE66DL;
    private static final long RANDOM_ADDEND = 0xBL;
    private static final long RANDOM_MASK = (1L << 48) - 1;

    private Mc263TrialChambersProductionTransaction() { }

    /** Result plus the exact placement-RNG handoff consumed by DATA rows in piece encounter order. */
    public static final class Execution {
        private final Result result;
        private final int callerDrawCount;
        private final List<Long> continuation;

        private Execution(Result result, int callerDrawCount, List<Long> continuation) {
            this.result = Objects.requireNonNull(result, "Trial production result");
            this.callerDrawCount = callerDrawCount;
            this.continuation = List.copyOf(continuation);
        }

        public Result result() { return result; }
        public int callerDrawCount() { return callerDrawCount; }
        public List<Long> continuation() { return continuation; }
    }

    /** Executes one authenticated persisted start against an isolated transaction world. */
    public static Execution execute(Mc263TrialChambersProducer.Start start,
            Mc263TemplatePlacementExecutor.Clip clip, PlacementWorld world) {
        Objects.requireNonNull(start, "Trial production start");
        Objects.requireNonNull(clip, "Trial production clip");
        Objects.requireNonNull(world, "Trial production world");
        Mc263TrialChambersSettlement.evidenceIdentity();

        Mc263TrialChambersGrammar.Corpus corpus = Mc263TrialChambersGrammar.pinned();
        if (!corpus.evidence().typedSidecars().ents().isEmpty()
                || corpus.evidence().typedSidecars().templateEntityCount() != 0) {
            throw new IllegalArgumentException("Trial production ENTS evidence drift");
        }
        List<Mc263TrialChambersGrammar.RuleSemanticRow> copperRules = copperRules(corpus);
        ArrayList<Piece> pieces = new ArrayList<>();
        int ordinal = 0;
        for (PiecePlacement placement : start.executionPlan().pieces()) {
            if (!placement.processor().isEmpty()
                    && !COPPER_PROCESSOR.equals(placement.processor())) {
                throw new IllegalArgumentException(
                        "unknown Trial production processor: " + placement.processor());
            }
            Mc263TrialChambersGrammar.Template template =
                    corpus.evidence().requireTemplate(placement.elementKey());
            if (template.entityCount() != 0 || !template.entities().isEmpty()) {
                throw new IllegalArgumentException("Trial production template ENTS drift");
            }
            pieces.add(new Piece(ordinal, new Program(template),
                    new Vec(placement.originX(), placement.originY(), placement.originZ()),
                    Rotation.valueOf(placement.rotation().name()), Mirror.NONE,
                    placement.projection().name().toLowerCase(),
                    placement.processor().isEmpty() ? List.of() : List.of(placement.processor()),
                    IgnorePolicy.STRUCTURE_BLOCK, true));
            ordinal++;
        }

        Legacy48 random = Legacy48.fromInternalState(start.generationRng().state48());
        requireContinuation(start.generationRng(), random.copy());
        Result result = Mc263TemplatePlacementExecutor.execute(pieces, clip, world,
                new TrialProcessor(copperRules), BENT, random);
        return new Execution(result, random.drawCount(), preview(random.copy(), 8));
    }

    /** Internal marker predicate for DATA draws that must stay out of the chest LOOT sidecar. */
    public static boolean isFixedContainerDraw(String table) {
        return FIXED_CONTAINER_DRAW.equals(table) || NON_CHEST_CONTAINER_DRAW.equals(table);
    }

    /** Only authenticated Trial loot facts may cross the BENT-to-LDEC boundary. */
    public static boolean isAuthenticatedLdecTable(String table) {
        return table.startsWith("minecraft:chests/trial_chambers/")
                || DISPENSER_CHAMBER_LOOT.equals(table)
                || DISPENSER_CORRIDOR_LOOT.equals(table)
                || DECORATED_POT_CORRIDOR_LOOT.equals(table);
    }

    /** Returns the pinned receipt for the Trial Chambers loot-tuple corpus. */
    public static String trialChambersLootTuplesSha256() {
        return TRIAL_CHAMBERS_LOOT_TUPLES_SHA256;
    }

    /**
     * Fail-closed admission for the only non-chest Trial BENT/LDEC pairs.
     *
     * <p>This shared authority is used by production emission and final-carrier acceptance.
     * Callers must not reconstruct this table membership.</p>
     */
    public static boolean isAuthenticatedNonChestBentLdec(String blockEntityType,
            String lootTable) {
        return ("minecraft:dispenser".equals(blockEntityType)
                && (DISPENSER_CHAMBER_LOOT.equals(lootTable)
                        || DISPENSER_CORRIDOR_LOOT.equals(lootTable)))
                || ("minecraft:decorated_pot".equals(blockEntityType)
                        && DECORATED_POT_CORRIDOR_LOOT.equals(lootTable));
    }

    private static final BlockEntitySynthesizer BENT = (piece, rawCell, position, exactState,
            blockEntityType, lootTable, lootSeed) -> {
        TrialCell cell = (TrialCell) rawCell;
        Mc263TrialChambersGrammar.Command command = cell.command();
        String expected = blockEntityType(command);
        if (!expected.equals(blockEntityType) || !blockKey(exactState).equals(expected)) {
            throw new IllegalArgumentException("Trial production BENT state/type drift at "
                    + position);
        }
        if ("minecraft:chest".equals(expected) && !hasHorizontalFacing(exactState)) {
            throw new IllegalArgumentException("Trial production chest state is outside the "
                    + "authenticated horizontal catalog: template=" + piece.program().templateKey()
                    + " piece=" + piece.ordinal() + " command=" + command.ordinal()
                    + " position=" + position + " state=" + exactState);
        }
        boolean expectedDraw = Mc263TrialChambersSettlement
                .usesCallerNextLongForProduction(command);
        if (expectedDraw != (lootTable != null)) {
            throw new IllegalArgumentException("Trial production DATA RNG binding drift at "
                    + position);
        }
        return Mc263TrialChambersSettlement.canonicalBentNbtForProduction(command,
                position.x(), position.y(), position.z(), lootSeed);
    };

    private static final class Program implements CellProgram {
        private final Mc263TrialChambersGrammar.Template template;
        private final List<TrialCell> cells;

        private Program(Mc263TrialChambersGrammar.Template template) {
            this.template = template;
            this.cells = cells(template);
        }

        @Override public String templateKey() { return template.key(); }
        @Override public List<TrialCell> cellsInPlacementOrder() { return cells; }
    }

    private static final class TrialCell implements Cell {
        private final Mc263TrialChambersGrammar.Command command;
        private final int x, y, z;
        private final String state;

        private TrialCell(Mc263TrialChambersGrammar.Command command, int x, int y, int z,
                String state) {
            this.command = command;
            this.x = x; this.y = y; this.z = z;
            this.state = state;
        }

        Mc263TrialChambersGrammar.Command command() { return command; }
        @Override public int localX() { return x; }
        @Override public int localY() { return y; }
        @Override public int localZ() { return z; }
        @Override public String exactState() { return state; }
        @Override public String blockEntityType() {
            return isBent(command)
                    ? Mc263TrialChambersProductionTransaction.blockEntityType(command) : null;
        }
        @Override public String lootTable() {
            if (command instanceof Mc263TrialChambersGrammar.LootContainer loot
                    && loot.rng() instanceof Mc263TrialChambersGrammar.CallerNextLongRng) {
                return ldecTableForProduction(loot.blockEntityType().id(), loot.lootTable().id());
            }
            if (command instanceof Mc263TrialChambersGrammar.FixedContainer) {
                return FIXED_CONTAINER_DRAW;
            }
            return null;
        }
        @Override public String markerOp() { return command.op().name(); }
        @Override public String jigsawFinalState() {
            return command instanceof Mc263TrialChambersGrammar.Jigsaw jigsaw
                    ? jigsaw.finalState() : null;
        }
    }

    /** Official rule processor followed by the protected-block processor. */
    private static final class TrialProcessor implements CellProcessor {
        private final List<Mc263TrialChambersGrammar.RuleSemanticRow> rules;

        private TrialProcessor(List<Mc263TrialChambersGrammar.RuleSemanticRow> rules) {
            this.rules = rules;
        }

        @Override
        public ProcessedCell process(Piece piece, Cell source, ProcessedCell input,
                PlacementWorld world) {
            if (!piece.processors().contains(COPPER_PROCESSOR)) return input;
            String state = applyCopperRules(input.exactState(), input.position(), rules);
            String existing = world.getBlockState(input.position());
            if (Mc263FeatureBlockState.fromExact(existing).featuresCannotReplace()) return null;
            return new ProcessedCell(input.position(), state, input.inputNbt());
        }
    }

    private static List<TrialCell> cells(Mc263TrialChambersGrammar.Template template) {
        ArrayList<TrialCell> result = new ArrayList<>(template.blockCount());
        for (Mc263TrialChambersGrammar.Command command : template.commands()) {
            String state = template.stateTable().get(command.state());
            if (command instanceof Mc263TrialChambersGrammar.Run run) {
                for (int index = 0; index < run.count(); index++) {
                    result.add(new TrialCell(command,
                            Math.addExact(run.start().x(), Math.multiplyExact(run.delta().x(), index)),
                            Math.addExact(run.start().y(), Math.multiplyExact(run.delta().y(), index)),
                            Math.addExact(run.start().z(), Math.multiplyExact(run.delta().z(), index)),
                            state));
                }
            } else {
                Mc263TrialChambersGrammar.Vec3i position = position(template, command);
                result.add(new TrialCell(command, position.x(), position.y(), position.z(), state));
            }
        }
        if (result.size() != template.blockCount()) {
            throw new IllegalArgumentException("Trial production command expansion drift: "
                    + template.key());
        }
        return List.copyOf(result);
    }

    private static Mc263TrialChambersGrammar.Vec3i position(
            Mc263TrialChambersGrammar.Template template,
            Mc263TrialChambersGrammar.Command command) {
        if (command instanceof Mc263TrialChambersGrammar.Jigsaw jigsaw) {
            return template.connectors().stream()
                    .filter(value -> value.ordinal() == jigsaw.connectorOrdinal())
                    .findFirst().orElseThrow(() -> new IllegalArgumentException(
                            "Trial production connector ordinal drift")).position();
        }
        if (command instanceof Mc263TrialChambersGrammar.IgnoredStructureBlock value) return value.position();
        if (command instanceof Mc263TrialChambersGrammar.LootContainer value) return value.position();
        if (command instanceof Mc263TrialChambersGrammar.FixedContainer value) return value.position();
        if (command instanceof Mc263TrialChambersGrammar.DecoratedPot value) return value.position();
        if (command instanceof Mc263TrialChambersGrammar.Vault value) return value.position();
        if (command instanceof Mc263TrialChambersGrammar.TrialSpawner value) return value.position();
        throw new IllegalArgumentException("unknown Trial production command position");
    }

    private static List<Mc263TrialChambersGrammar.RuleSemanticRow> copperRules(
            Mc263TrialChambersGrammar.Corpus corpus) {
        List<Mc263TrialChambersGrammar.RuleSemanticRow> found = null;
        for (Mc263TrialChambersGrammar.Processor processor
                : corpus.evidence().processorsInEncounterOrder()) {
            if (processor.semantics() instanceof Mc263TrialChambersGrammar.RuleSemantics value) {
                if (found != null) throw new IllegalArgumentException(
                        "duplicate Trial production rule authority");
                found = value.rulesInOrder();
            }
        }
        if (found == null || found.size() != 3) {
            throw new IllegalArgumentException("Trial production rule authority drift");
        }
        return List.copyOf(found);
    }

    private static String applyCopperRules(String state, Vec position,
            List<Mc263TrialChambersGrammar.RuleSemanticRow> rules) {
        if (!"minecraft:waxed_copper_bulb".equals(blockKey(state))) return state;
        Legacy48 random = Legacy48.fromExternalSeed(positionalSeed(position));
        for (Mc263TrialChambersGrammar.RuleSemanticRow rule : rules) {
            if (!"minecraft:waxed_copper_bulb".equals(rule.inputBlock())) {
                throw new IllegalArgumentException("Trial production copper rule input drift");
            }
            if (random.nextFloat() < rule.probability()) return rule.outputState();
        }
        return state;
    }

    private static long positionalSeed(Vec position) {
        int xProduct = position.x() * 3_129_871;
        long value = (long) xProduct ^ (long) position.z() * 116_129_781L ^ position.y();
        value = value * value * 42_317_861L + value * 11L;
        return value >> 16;
    }

    private static boolean isBent(Mc263TrialChambersGrammar.Command command) {
        return command instanceof Mc263TrialChambersGrammar.LootContainer
                || command instanceof Mc263TrialChambersGrammar.FixedContainer
                || command instanceof Mc263TrialChambersGrammar.DecoratedPot
                || command instanceof Mc263TrialChambersGrammar.Vault
                || command instanceof Mc263TrialChambersGrammar.TrialSpawner;
    }

    static String ldecTableForProduction(String blockEntityType, String lootTable) {
        if ("minecraft:chest".equals(blockEntityType)
                || isAuthenticatedNonChestBentLdec(blockEntityType, lootTable)) {
            return lootTable;
        }
        return NON_CHEST_CONTAINER_DRAW;
    }

    private static String blockEntityType(Mc263TrialChambersGrammar.Command command) {
        if (command instanceof Mc263TrialChambersGrammar.LootContainer value) return value.blockEntityType().id();
        if (command instanceof Mc263TrialChambersGrammar.FixedContainer value) return value.blockEntityType().id();
        if (command instanceof Mc263TrialChambersGrammar.DecoratedPot value) return value.blockEntityType().id();
        if (command instanceof Mc263TrialChambersGrammar.Vault value) return value.blockEntityType().id();
        if (command instanceof Mc263TrialChambersGrammar.TrialSpawner value) return value.blockEntityType().id();
        throw new IllegalArgumentException("Trial production command has no BENT type");
    }

    private static String blockKey(String state) {
        int property = state.indexOf('[');
        return property < 0 ? state : state.substring(0, property);
    }

    private static boolean hasHorizontalFacing(String state) {
        return state.contains("facing=north") || state.contains("facing=east")
                || state.contains("facing=south") || state.contains("facing=west");
    }

    private static void requireContinuation(Mc263TrialChambersProducer.GenerationRng receipt,
            Legacy48 random) {
        if (!preview(random, receipt.continuationNextLongI64().size())
                .equals(receipt.continuationNextLongI64())) {
            throw new IllegalArgumentException("Trial production caller-RNG handoff drift");
        }
    }

    private static List<Long> preview(Legacy48 random, int count) {
        ArrayList<Long> values = new ArrayList<>(count);
        for (int index = 0; index < count; index++) values.add(random.nextLong());
        return List.copyOf(values);
    }

    private static final class Legacy48 implements PlacementRandom {
        private long state;
        private int drawCount;

        private Legacy48(long state) { this.state = state & RANDOM_MASK; }
        static Legacy48 fromInternalState(long state) { return new Legacy48(state); }
        static Legacy48 fromExternalSeed(long seed) {
            return new Legacy48((seed ^ RANDOM_MULTIPLIER) & RANDOM_MASK);
        }
        Legacy48 copy() { return new Legacy48(state); }
        int drawCount() { return drawCount; }
        private int next(int bits) {
            state = (state * RANDOM_MULTIPLIER + RANDOM_ADDEND) & RANDOM_MASK;
            return (int) (state >>> (48 - bits));
        }
        @Override public long nextLong() {
            drawCount++;
            return ((long) next(32) << 32) + next(32);
        }
        float nextFloat() { return next(24) / ((float) (1 << 24)); }
    }
}
