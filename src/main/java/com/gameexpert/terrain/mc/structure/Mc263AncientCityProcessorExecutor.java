package com.gameexpert.terrain.mc.structure;

import com.gameexpert.terrain.mc.LegacyRand;
import com.gameexpert.terrain.mc.structure.Mc263JigsawStructureBoundary.Rotation;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Atomic, mutation-free execution leaf for the authenticated Ancient City block processors.
 *
 * <p>The returned receipt is the commit/rollback boundary. The executor validates the complete
 * processor sequence, state surface and environment capabilities before it creates positional
 * random streams or queries the destination world.</p>
 */
public final class Mc263AncientCityProcessorExecutor {
    private static final Mc263AncientCityGrammar AUTHORITY =
            Mc263AncientCityProductionAuthority.load();
    private static final Map<String, Mc263AncientCityGrammar.ProcessorSpec> PROCESSORS =
            indexProcessors(AUTHORITY.processorSemanticsInEncounterOrder());

    private Mc263AncientCityProcessorExecutor() { }

    public static Receipt execute(Request request, Environment environment) {
        Objects.requireNonNull(request, "Ancient City processor request");
        Objects.requireNonNull(environment, "Ancient City processor environment");

        List<Mc263AncientCityGrammar.ProcessorSpec> processors = resolve(request.processorKeys);
        Mc263VillageSettlement.Position worldPosition = Mc263VillageSettlement.transformBlock(
                request.localX, request.localY, request.localZ, request.rotation,
                request.originX, request.originY, request.originZ);
        String transformedInput = Mc263VillageSettlement.rotateState(
                requireExactState(request.inputExactState), request.rotation);
        String finalState = request.jigsawFinalExactState == null ? null
                : Mc263VillageSettlement.rotateState(
                        requireExactState(request.jigsawFinalExactState), Rotation.NONE);

        preflight(processors, transformedInput, finalState, worldPosition, environment);

        String state = transformedInput;
        byte[] nbt = cloneBytes(request.inputNbt);
        ArrayList<Decision> decisions = new ArrayList<>();
        for (Mc263AncientCityGrammar.ProcessorSpec processor : processors) {
            if (state == null) break;
            String before = state;
            byte[] nbtBefore = cloneBytes(nbt);
            Decision decision;
            switch (processor.kind()) {
                case RULE -> {
                    RuleResult result = applyRules(processor, state, worldPosition);
                    state = result.outputState;
                    decision = new Decision(processor.identity(), Kind.RULE, before, state,
                            result.positionSeed, result.draw, result.randomStateBefore,
                            result.randomStateAfter, result.ruleOrdinal, false, null, false);
                }
                case PROTECTED_BLOCKS -> {
                    Mc263AncientCityGrammar.ProtectedBlocksCodec codec =
                            (Mc263AncientCityGrammar.ProtectedBlocksCodec) processor.codec();
                    String queried = requireExactState(environment.blockStateAt(worldPosition));
                    boolean protectedBlock = environment.stateInTag(queried, codec.valueTag());
                    if (protectedBlock) {
                        state = null;
                        nbt = null;
                    }
                    decision = new Decision(processor.identity(), Kind.PROTECTED_BLOCKS, before,
                            state, null, null, null, null, -1, true, queried, protectedBlock);
                }
                case BLOCK_IGNORE -> {
                    Mc263AncientCityGrammar.BlockIgnoreCodec codec =
                            (Mc263AncientCityGrammar.BlockIgnoreCodec) processor.codec();
                    boolean ignored = codec.blocks().contains(blockIdentity(state));
                    if (ignored) {
                        state = null;
                        nbt = null;
                    }
                    decision = new Decision(processor.identity(), Kind.BLOCK_IGNORE, before, state,
                            null, null, null, null, -1, false, null, ignored);
                }
                case JIGSAW_REPLACEMENT -> {
                    boolean replaced = "minecraft:jigsaw".equals(blockIdentity(state));
                    if (replaced) {
                        state = "minecraft:structure_void".equals(blockIdentity(finalState))
                                ? null : finalState;
                        nbt = null;
                    }
                    decision = new Decision(processor.identity(), Kind.JIGSAW_REPLACEMENT, before,
                            state, null, null, null, null, -1, false, null, replaced);
                }
                case BLOCK_ROT -> {
                    Mc263AncientCityGrammar.BlockRotCodec codec =
                            (Mc263AncientCityGrammar.BlockRotCodec) processor.codec();
                    if (!codec.isRottableExactState(state)) {
                        decision = new Decision(processor.identity(), Kind.BLOCK_ROT, before, state,
                                null, null, null, null, -1, false, null, false);
                    } else {
                        Mc263AncientCityGrammar.BlockRotRng contract =
                                (Mc263AncientCityGrammar.BlockRotRng) processor.rng();
                        long seed = positionSeed(contract.positionSeed(), worldPosition);
                        LegacyRand random = LegacyRand.fromSeed(seed);
                        long randomBefore = random.state48();
                        float draw = random.nextFloat();
                        long randomAfter = random.state48();
                        boolean keep = draw <= (float) codec.integrity();
                        if (!keep) {
                            state = null;
                            nbt = null;
                        }
                        decision = new Decision(processor.identity(), Kind.BLOCK_ROT, before, state,
                                seed, draw, randomBefore, randomAfter, -1, false, null, !keep);
                    }
                }
                default -> throw new IllegalStateException(
                        "unsupported Ancient City processor kind: " + processor.kind());
            }
            decisions.add(decision.withNbtChanged(!Arrays.equals(nbtBefore, nbt)));
        }
        return new Receipt(worldPosition, request.inputExactState, transformedInput,
                request.inputNbt, state, nbt, decisions);
    }

    private static void preflight(List<Mc263AncientCityGrammar.ProcessorSpec> processors,
            String inputState, String finalState, Mc263VillageSettlement.Position position,
            Environment environment) {
        boolean requiresWorldQuery = false;
        boolean mayReachJigsaw = "minecraft:jigsaw".equals(blockIdentity(inputState));
        require(environment.supportsExactState(inputState),
                "Ancient City input state capability absent: " + inputState);
        for (Mc263AncientCityGrammar.ProcessorSpec processor : processors) {
            require(environment.supportsProcessor(processor.identity(), processor.runtimeClass()),
                    "Ancient City processor capability absent: " + processor.identity());
            switch (processor.kind()) {
                case RULE -> {
                    Mc263AncientCityGrammar.RuleCodec codec =
                            (Mc263AncientCityGrammar.RuleCodec) processor.codec();
                    for (Mc263AncientCityGrammar.Rule rule : codec.rules()) {
                        require(environment.supportsExactState(rule.block()),
                                "Ancient City rule input capability absent: " + rule.block());
                        require(environment.supportsExactState(rule.outputState()),
                                "Ancient City rule output capability absent: " + rule.outputState());
                    }
                }
                case PROTECTED_BLOCKS -> {
                    String tag = ((Mc263AncientCityGrammar.ProtectedBlocksCodec)
                            processor.codec()).valueTag();
                    require(environment.supportsTag(tag),
                            "Ancient City protected tag capability absent: " + tag);
                    requiresWorldQuery = true;
                }
                case BLOCK_IGNORE -> {
                    for (String ignored : ((Mc263AncientCityGrammar.BlockIgnoreCodec)
                            processor.codec()).blocks()) {
                        require(environment.supportsExactState(ignored),
                                "Ancient City ignored state capability absent: " + ignored);
                    }
                }
                case JIGSAW_REPLACEMENT -> {
                    if (mayReachJigsaw) {
                        require(finalState != null,
                                "Ancient City jigsaw final state is absent");
                        require(environment.supportsExactState(finalState),
                                "Ancient City jigsaw final-state capability absent: " + finalState);
                        mayReachJigsaw = "minecraft:jigsaw".equals(blockIdentity(finalState));
                    }
                }
                case BLOCK_ROT -> {
                    String tag = ((Mc263AncientCityGrammar.BlockRotCodec)
                            processor.codec()).rottableBlocks();
                    require(environment.supportsTag(tag),
                            "Ancient City block-rot tag capability absent: " + tag);
                }
                default -> throw new IllegalStateException(
                        "unsupported Ancient City processor kind: " + processor.kind());
            }
        }
        if (requiresWorldQuery) {
            require(environment.supportsWorldStateQuery(position),
                    "Ancient City world-state query capability absent at " + position);
        }
    }

    private static RuleResult applyRules(Mc263AncientCityGrammar.ProcessorSpec processor,
            String state, Mc263VillageSettlement.Position position) {
        Mc263AncientCityGrammar.RuleCodec codec =
                (Mc263AncientCityGrammar.RuleCodec) processor.codec();
        Mc263AncientCityGrammar.RuleRng contract =
                (Mc263AncientCityGrammar.RuleRng) processor.rng();
        long seed = positionSeed(contract.positionSeed(), position);
        LegacyRand random = null;
        Long stateBefore = null;
        Float draw = null;
        int ordinal = -1;
        for (int index = 0; index < codec.rules().size(); index++) {
            Mc263AncientCityGrammar.Rule rule = codec.rules().get(index);
            if (!blockIdentity(state).equals(rule.block())) continue;
            random = LegacyRand.fromSeed(seed);
            stateBefore = random.state48();
            draw = random.nextFloat();
            if (draw < rule.probability()) {
                state = rule.outputState();
                ordinal = index;
            }
            break;
        }
        return new RuleResult(state, random == null ? null : seed, draw, stateBefore,
                random == null ? null : random.state48(), ordinal);
    }

    private static long positionSeed(Mc263AncientCityGrammar.PositionSeed contract,
            Mc263VillageSettlement.Position position) {
        int xProduct = position.x() * contract.xMultiplierI32();
        long mixed = (long) xProduct ^ (long) position.z() * contract.zMultiplierI64()
                ^ (long) position.y();
        mixed = mixed * mixed * contract.squareMultiplierI64()
                + mixed * contract.linearMultiplierI64();
        return mixed >> contract.arithmeticRightShift();
    }

    private static List<Mc263AncientCityGrammar.ProcessorSpec> resolve(List<String> keys) {
        require(keys != null, "Ancient City processor sequence is absent");
        ArrayList<Mc263AncientCityGrammar.ProcessorSpec> result = new ArrayList<>(keys.size());
        for (String key : keys) {
            Mc263AncientCityGrammar.ProcessorSpec processor = PROCESSORS.get(key);
            require(processor != null, "unknown Ancient City processor semantic: " + key);
            result.add(processor);
        }
        return List.copyOf(result);
    }

    private static Map<String, Mc263AncientCityGrammar.ProcessorSpec> indexProcessors(
            List<Mc263AncientCityGrammar.ProcessorSpec> processors) {
        HashMap<String, Mc263AncientCityGrammar.ProcessorSpec> result = new HashMap<>();
        for (Mc263AncientCityGrammar.ProcessorSpec processor : processors) {
            require(result.put(processor.identity(), processor) == null,
                    "duplicate Ancient City processor semantic");
        }
        return Map.copyOf(result);
    }

    private static String requireExactState(String state) {
        blockIdentity(state);
        return state;
    }

    private static String blockIdentity(String exactState) {
        require(exactState != null && !exactState.isEmpty(),
                "Ancient City exact state is absent");
        int open = exactState.indexOf('[');
        String block = open < 0 ? exactState : exactState.substring(0, open);
        require(block.indexOf(':') > 0 && block.indexOf(':') == block.lastIndexOf(':'),
                "malformed Ancient City block identity: " + exactState);
        if (open >= 0) {
            require(exactState.endsWith("]") && open < exactState.length() - 2,
                    "malformed Ancient City exact state: " + exactState);
        }
        return block;
    }

    private static byte[] cloneBytes(byte[] value) {
        return value == null ? null : value.clone();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }

    public interface Environment {
        boolean supportsProcessor(String semanticIdentity, String runtimeClass);
        boolean supportsExactState(String exactState);
        boolean supportsTag(String tagKey);
        boolean supportsWorldStateQuery(Mc263VillageSettlement.Position position);
        String blockStateAt(Mc263VillageSettlement.Position position);
        boolean stateInTag(String exactState, String tagKey);
    }

    public enum Kind { RULE, PROTECTED_BLOCKS, BLOCK_IGNORE, JIGSAW_REPLACEMENT, BLOCK_ROT }

    public static final class Request {
        private final List<String> processorKeys;
        private final String inputExactState;
        private final byte[] inputNbt;
        private final String jigsawFinalExactState;
        private final int localX, localY, localZ, originX, originY, originZ;
        private final Rotation rotation;

        public Request(List<String> processorKeys, String inputExactState, byte[] inputNbt,
                String jigsawFinalExactState, int localX, int localY, int localZ,
                int originX, int originY, int originZ, Rotation rotation) {
            this.processorKeys = processorKeys == null ? null : List.copyOf(processorKeys);
            this.inputExactState = inputExactState;
            this.inputNbt = cloneBytes(inputNbt);
            this.jigsawFinalExactState = jigsawFinalExactState;
            this.localX = localX; this.localY = localY; this.localZ = localZ;
            this.originX = originX; this.originY = originY; this.originZ = originZ;
            this.rotation = Objects.requireNonNull(rotation, "Ancient City processor rotation");
        }
    }

    public static final class Receipt {
        private final Mc263VillageSettlement.Position worldPosition;
        private final String sourceExactState, rollbackExactState, outputExactState;
        private final byte[] rollbackNbt, outputNbt;
        private final List<Decision> decisions;

        private Receipt(Mc263VillageSettlement.Position worldPosition, String sourceExactState,
                String rollbackExactState, byte[] rollbackNbt, String outputExactState,
                byte[] outputNbt, List<Decision> decisions) {
            this.worldPosition = worldPosition;
            this.sourceExactState = sourceExactState;
            this.rollbackExactState = rollbackExactState;
            this.rollbackNbt = cloneBytes(rollbackNbt);
            this.outputExactState = outputExactState;
            this.outputNbt = cloneBytes(outputNbt);
            this.decisions = List.copyOf(decisions);
        }

        public Mc263VillageSettlement.Position worldPosition() { return worldPosition; }
        public String sourceExactState() { return sourceExactState; }
        public String rollbackExactState() { return rollbackExactState; }
        public byte[] rollbackNbt() { return cloneBytes(rollbackNbt); }
        public String outputExactState() { return outputExactState; }
        public byte[] outputNbt() { return cloneBytes(outputNbt); }
        public boolean removed() { return outputExactState == null; }
        public boolean changed() {
            return !Objects.equals(rollbackExactState, outputExactState)
                    || !Arrays.equals(rollbackNbt, outputNbt);
        }
        public List<Decision> decisions() { return decisions; }
    }

    public static final class Decision {
        private final String processorKey, inputExactState, outputExactState, queriedExactState;
        private final Kind kind;
        private final Long positionSeed, randomState48Before, randomState48After;
        private final Float randomDraw;
        private final int matchedRuleOrdinal;
        private final boolean worldQueried, predicateMatched, nbtChanged;

        private Decision(String processorKey, Kind kind, String inputExactState,
                String outputExactState, Long positionSeed, Float randomDraw,
                Long randomState48Before, Long randomState48After, int matchedRuleOrdinal,
                boolean worldQueried, String queriedExactState, boolean predicateMatched) {
            this(processorKey, kind, inputExactState, outputExactState, positionSeed, randomDraw,
                    randomState48Before, randomState48After, matchedRuleOrdinal, worldQueried,
                    queriedExactState, predicateMatched, false);
        }

        private Decision(String processorKey, Kind kind, String inputExactState,
                String outputExactState, Long positionSeed, Float randomDraw,
                Long randomState48Before, Long randomState48After, int matchedRuleOrdinal,
                boolean worldQueried, String queriedExactState, boolean predicateMatched,
                boolean nbtChanged) {
            this.processorKey = processorKey; this.kind = kind;
            this.inputExactState = inputExactState; this.outputExactState = outputExactState;
            this.positionSeed = positionSeed; this.randomDraw = randomDraw;
            this.randomState48Before = randomState48Before;
            this.randomState48After = randomState48After;
            this.matchedRuleOrdinal = matchedRuleOrdinal; this.worldQueried = worldQueried;
            this.queriedExactState = queriedExactState;
            this.predicateMatched = predicateMatched; this.nbtChanged = nbtChanged;
        }

        private Decision withNbtChanged(boolean changed) {
            return new Decision(processorKey, kind, inputExactState, outputExactState,
                    positionSeed, randomDraw, randomState48Before, randomState48After,
                    matchedRuleOrdinal, worldQueried, queriedExactState, predicateMatched, changed);
        }

        public String processorKey() { return processorKey; }
        public Kind kind() { return kind; }
        public String inputExactState() { return inputExactState; }
        public String outputExactState() { return outputExactState; }
        public Long positionSeed() { return positionSeed; }
        public Float randomDraw() { return randomDraw; }
        public Long randomState48Before() { return randomState48Before; }
        public Long randomState48After() { return randomState48After; }
        public int matchedRuleOrdinal() { return matchedRuleOrdinal; }
        public boolean worldQueried() { return worldQueried; }
        public String queriedExactState() { return queriedExactState; }
        public boolean predicateMatched() { return predicateMatched; }
        public boolean nbtChanged() { return nbtChanged; }
    }

    private static final class RuleResult {
        private final String outputState;
        private final Long positionSeed;
        private final Float draw;
        private final Long randomStateBefore, randomStateAfter;
        private final int ruleOrdinal;
        private RuleResult(String outputState, Long positionSeed, Float draw,
                Long randomStateBefore, Long randomStateAfter, int ruleOrdinal) {
            this.outputState = outputState; this.positionSeed = positionSeed; this.draw = draw;
            this.randomStateBefore = randomStateBefore; this.randomStateAfter = randomStateAfter;
            this.ruleOrdinal = ruleOrdinal;
        }
    }
}
