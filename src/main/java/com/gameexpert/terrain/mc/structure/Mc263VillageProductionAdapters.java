package com.gameexpert.terrain.mc.structure;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import com.gameexpert.terrain.mc.feature.Mc263WorldGenRegionRandom;
import com.gameexpert.terrain.mc.structure.Mc263VillagePileFeatureAuthority.Query;
import com.gameexpert.terrain.mc.structure.Mc263VillageProductionAuthority.BlockPos;
import com.gameexpert.terrain.mc.structure.Mc263VillageProductionAuthority.Corpus;
import com.gameexpert.terrain.mc.structure.Mc263VillageProductionAuthority.InputPredicateKind;
import com.gameexpert.terrain.mc.structure.Mc263VillageProductionAuthority.RuleDefinition;
import com.gameexpert.terrain.mc.structure.Mc263VillageProductionAuthority.RuleProcessorBody;
import com.gameexpert.terrain.mc.structure.Mc263VillageSettlement.CellPlacement;
import com.gameexpert.terrain.mc.structure.Mc263VillageSettlement.ExecutionResult;
import com.gameexpert.terrain.mc.structure.Mc263VillageSettlement.FeaturePlacement;
import com.gameexpert.terrain.mc.structure.Mc263VillageSettlement.PersistedRequest;
import com.gameexpert.terrain.mc.structure.Mc263VillageSettlement.PersistedSettlement;
import com.gameexpert.terrain.mc.structure.Mc263VillageSettlement.PlacementRandom;
import com.gameexpert.terrain.mc.structure.Mc263VillageSettlement.Position;
import com.gameexpert.terrain.mc.structure.Mc263VillageSettlement.Request;
import com.gameexpert.terrain.mc.structure.Mc263VillageSettlement.Settlement;
import com.gameexpert.terrain.mc.structure.Mc263VillageSettlement.WorldTransaction;

/**
 * Dormant five-key production boundary for the accepted Village grammar and rule authority.
 * Registry publication and canonical-caller activation deliberately live outside this class.
 */
public final class Mc263VillageProductionAdapters {
    private static final Set<String> PILE_FEATURE_KEYS =
            Set.copyOf(Mc263VillagePileFeatureAuthority.featureKeys());
    private static final List<Adapter> ADAPTERS = List.of(
            new Adapter("minecraft:village_desert"),
            new Adapter("minecraft:village_plains"),
            new Adapter("minecraft:village_savanna"),
            new Adapter("minecraft:village_snowy"),
            new Adapter("minecraft:village_taiga"));

    private Mc263VillageProductionAdapters() { }

    public static List<Adapter> adaptersInAuthorityOrder() {
        return ADAPTERS;
    }

    /**
     * Village structure-entity seam. The pinned per-type finalize program consumes the supplied
     * transaction-owned {@link Mc263WorldGenRegionRandom} directly and emits the UUID-excluded full
     * canonical entity NBT; no summary or caller hash may stand in for that payload.
     */
    public static Mc263VillageEntityAuthority.GeneratedEntity spawnStructureEntity(
            String structureKey, Mc263VillageEntityAuthority.EntityRequest request,
            Mc263WorldGenRegionRandom callerRandom) {
        require(structureKey).structureKey();
        Objects.requireNonNull(request, "Village structure entity request");
        Objects.requireNonNull(callerRandom, "Village structure entity caller WGR");
        require(request.templateKey().startsWith("minecraft:village/"),
                "Village structure entity adapter/template key mismatch: " + request.templateKey());
        return Mc263VillageEntityAuthority.generate(request, callerRandom);
    }

    public static Adapter require(String structureKey) {
        Objects.requireNonNull(structureKey, "Village structure key");
        for (Adapter adapter : ADAPTERS) {
            if (adapter.structureKey.equals(structureKey)) return adapter;
        }
        throw new IllegalArgumentException("unsupported Village production adapter: " + structureKey);
    }

    public static final class Adapter {
        private final String structureKey;

        private Adapter(String structureKey) {
            this.structureKey = structureKey;
        }

        public String structureKey() {
            return structureKey;
        }

        /**
         * Executes the existing byte-exact Village producer/settlement path while replacing only the
         * previously opaque minecraft:rule semantics with the promoted G3J21R2 executable authority.
         */
        public Settlement execute(Request request, ProductionWorldTransaction transaction,
                PlacementRandom callerRandom) {
            Objects.requireNonNull(request, "Village production request");
            Objects.requireNonNull(transaction, "Village production transaction");
            Objects.requireNonNull(callerRandom, "Village production caller RNG");
            require(structureKey.equals(request.structureKey()),
                    "Village production adapter/request key mismatch");

            Corpus authority = Mc263VillageProductionAuthority.pinned();
            preflightRuleAuthority(authority, transaction);
            return Mc263VillageSettlement.executeWithTemplateAuthority(request, transaction, callerRandom,
                    (isolated, placement, random) -> executeTemplateCell(
                            authority, isolated, placement, random));
        }

        /**
         * Executes only the authenticated persisted Village plan. All production rule/configured-
         * feature capabilities are proven before the settlement may fork, draw WGR, query or mutate.
         */
        public PersistedSettlement executePersisted(PersistedRequest persisted,
                ProductionWorldTransaction transaction,
                Mc263WorldGenRegionRandom.State randomPredecessor,
                Mc263WorldGenRegionRandom randomFork) {
            Objects.requireNonNull(persisted, "Village persisted production request");
            Objects.requireNonNull(transaction, "Village persisted production transaction");
            Objects.requireNonNull(randomPredecessor, "Village persisted production WGR predecessor");
            Objects.requireNonNull(randomFork, "Village persisted production WGR fork");
            require(structureKey.equals(persisted.request().structureKey()),
                    "Village persisted production adapter/request key mismatch");

            Corpus authority = Mc263VillageProductionAuthority.pinned();
            preflightRuleAuthority(authority, transaction);
            preflightFeatureAuthority(authority, transaction);
            return Mc263VillageSettlement.executePersistedWithAuthority(
                    persisted, transaction, randomPredecessor, randomFork,
                    (isolated, placement, random) -> executePersistedTemplateCell(
                            authority, isolated, placement, random),
                    (isolated, placement, random) -> executePersistedConfiguredFeature(
                            authority, isolated, placement, random));
        }
    }

    /**
     * Production primitive boundary. Non-rule processor semantics remain runtime-owned; every promoted
     * rule semantic must be invoked through the supplied executor in the order present in CellPlacement.
     */
    public interface ProductionWorldTransaction extends WorldTransaction {
        @Override ProductionWorldTransaction fork();

        boolean supportsRuleTag(String tagKey);
        boolean supportsAuthenticatedConfiguredFeature(String featureKey, String configuredTarget);
        boolean supportsPileFeatureQuery(Query query);
        boolean supportsWritableChunkRadius(int radius);
        boolean blockInRuleTag(String blockKey, String tagKey);
        String ruleBlockStateAt(Position processedWorldPosition);
        boolean pileIsEmptyBlock(Position position);
        String pileSupportBlockState(Position supportPosition);
        boolean pileIsFaceSturdyUp(Position supportPosition, String exactState);
        boolean pileSetBlock(Position position, String exactState, int flags);

        ExecutionResult executeTemplateCellWithRuleAuthority(CellPlacement placement,
                PlacementRandom random, RuleProcessorExecutor ruleProcessorExecutor);
        ExecutionResult executePersistedTemplateCellWithRuleAuthority(CellPlacement placement,
                Mc263WorldGenRegionRandom random, RuleProcessorExecutor ruleProcessorExecutor);
        ExecutionResult executeAuthenticatedConfiguredFeature(FeaturePlacement placement,
                Mc263WorldGenRegionRandom random);

        @Override
        default ExecutionResult executeTemplateCell(CellPlacement placement, PlacementRandom random) {
            throw new IllegalStateException("Village production adapter rule authority was bypassed");
        }

        @Override
        default ExecutionResult executeTemplateCell(CellPlacement placement,
                Mc263WorldGenRegionRandom random) {
            throw new IllegalStateException("Village persisted production rule authority was bypassed");
        }

        @Override
        default ExecutionResult executeConfiguredFeature(FeaturePlacement placement,
                Mc263WorldGenRegionRandom random) {
            throw new IllegalStateException("Village persisted production feature authority was bypassed");
        }
    }

    @FunctionalInterface
    public interface RuleProcessorExecutor {
        RuleResult execute(String semanticId, String inputState, byte[] inputNbt,
                Position templateRelativePosition, Position processedWorldPosition,
                Position referencePosition);
    }

    public record RuleResult(String outputState, byte[] outputNbt, int matchedRuleOrdinal,
            long localRandomState48, int randomDraws, int worldBlockQueries) {
        public RuleResult {
            outputNbt = outputNbt == null ? null : outputNbt.clone();
        }
        @Override public byte[] outputNbt() {
            return outputNbt == null ? null : outputNbt.clone();
        }
    }

    private static ExecutionResult executeTemplateCell(Corpus authority, WorldTransaction isolated,
            CellPlacement placement, PlacementRandom random) {
        require(isolated instanceof ProductionWorldTransaction,
                "Village production fork lost rule-authority capability");
        ProductionWorldTransaction production = (ProductionWorldTransaction) isolated;
        OrderedRuleExecutor executor = new OrderedRuleExecutor(authority, production, placement.processors());
        ExecutionResult result = Objects.requireNonNull(
                production.executeTemplateCellWithRuleAuthority(placement, random, executor),
                "Village production template execution result");
        // A cell the pre-chain JigsawReplacementProcessor/BlockIgnoreProcessor drop never reaches
        // the element's rule processors, so completeness is only demanded of surviving cells.
        if (Mc263VillageProductionTransaction.reachesProcessorChain(placement)) {
            executor.requireComplete();
        }
        return result;
    }

    private static ExecutionResult executePersistedTemplateCell(Corpus authority,
            WorldTransaction isolated, CellPlacement placement, Mc263WorldGenRegionRandom random) {
        require(isolated instanceof ProductionWorldTransaction,
                "Village persisted production fork lost rule-authority capability");
        ProductionWorldTransaction production = (ProductionWorldTransaction) isolated;
        OrderedRuleExecutor executor = new OrderedRuleExecutor(authority, production, placement.processors());
        ExecutionResult result = Objects.requireNonNull(
                production.executePersistedTemplateCellWithRuleAuthority(placement, random, executor),
                "Village persisted production template execution result");
        if (Mc263VillageProductionTransaction.reachesProcessorChain(placement)) {
            executor.requireComplete();
        }
        return result;
    }

    private static ExecutionResult executePersistedConfiguredFeature(Corpus authority,
            WorldTransaction isolated, FeaturePlacement placement, Mc263WorldGenRegionRandom random) {
        require(isolated instanceof ProductionWorldTransaction,
                "Village persisted production fork lost feature-authority capability");
        ProductionWorldTransaction production = (ProductionWorldTransaction) isolated;
        Mc263VillageProductionAuthority.Feature authenticated =
                authority.requireFeature(placement.featureKey());
        require(authenticated.registryKey().equals(placement.featureKey())
                        && authenticated.configuredTarget().equals(placement.configuredTarget()),
                "Village persisted configured-feature authentication drift: "
                        + placement.featureKey());
        if (PILE_FEATURE_KEYS.contains(placement.featureKey())) {
            return Mc263VillagePileFeatureAuthority.execute(placement, random,
                    new Mc263VillagePileFeatureAuthority.Environment() {
                        @Override public boolean supportsExactState(String exactState) { return true; }
                        @Override public boolean supportsQuery(Query query) { return true; }
                        @Override public boolean supportsWriteFlags(int flags) {
                            return flags == Mc263VillagePileFeatureAuthority.WRITE_FLAGS;
                        }
                        @Override public boolean supportsWritableChunkRadius(int radius) {
                            return radius == Mc263VillagePileFeatureAuthority.WRITABLE_CHUNK_RADIUS;
                        }
                        @Override public int minBuildY() {
                            return Mc263VillagePileFeatureAuthority.MIN_BUILD_Y;
                        }
                        @Override public int maxBuildY() {
                            return Mc263VillagePileFeatureAuthority.MAX_BUILD_Y;
                        }
                        @Override public boolean isEmptyBlock(Position position) {
                            return production.pileIsEmptyBlock(position);
                        }
                        @Override public String supportBlockState(Position supportPosition) {
                            return production.pileSupportBlockState(supportPosition);
                        }
                        @Override public boolean isFaceSturdyUp(Position supportPosition,
                                String exactState) {
                            return production.pileIsFaceSturdyUp(supportPosition, exactState);
                        }
                        @Override public boolean setBlock(Position position, String exactState, int flags) {
                            return production.pileSetBlock(position, exactState, flags);
                        }
                    });
        }
        return Objects.requireNonNull(
                production.executeAuthenticatedConfiguredFeature(placement, random),
                "Village persisted authenticated configured-feature execution result");
    }

    private static void preflightRuleAuthority(Corpus authority, ProductionWorldTransaction transaction) {
        List<RuleProcessorBody> bodies = authority.ruleProcessorsInOrder();
        require(bodies.size() == Mc263VillageProductionAuthority.RULE_PROCESSOR_COUNT,
                "Village production rule-processor count drift");
        int ruleCount = 0;
        for (RuleProcessorBody body : bodies) {
            require(transaction.supportsProcessorSemantic(body.semanticId()),
                    "Village rule semantic capability absent: " + body.semanticId());
            for (RuleDefinition rule : body.rulesInOrder()) {
                ruleCount++;
                require(transaction.supportsExactState(rule.outputState()),
                        "Village rule output state capability absent: " + rule.outputState());
                if (rule.inputKind() == InputPredicateKind.EXACT_STATE) {
                    require(transaction.supportsExactState(rule.inputValue()),
                            "Village rule input state capability absent: " + rule.inputValue());
                } else if (rule.inputKind() == InputPredicateKind.TAG) {
                    require(transaction.supportsRuleTag(rule.inputValue()),
                            "Village rule tag capability absent: " + rule.inputValue());
                }
            }
        }
        require(ruleCount == Mc263VillageProductionAuthority.RULE_COUNT,
                "Village production rule count drift");
    }

    private static void preflightFeatureAuthority(Corpus authority,
            ProductionWorldTransaction transaction) {
        List<Mc263VillageProductionAuthority.Feature> features = authority.featuresInOrder();
        require(features.size() == Mc263VillageProductionAuthority.CONFIGURED_FEATURE_COUNT,
                "Village production configured-feature count drift");
        HashSet<String> authenticatedPiles = new HashSet<>();
        for (Mc263VillageProductionAuthority.Feature feature : features) {
            require(transaction.supportsConfiguredFeature(feature.registryKey()),
                    "Village configured-feature capability absent: " + feature.registryKey());
            if (PILE_FEATURE_KEYS.contains(feature.registryKey())) {
                require(feature.registryKey().equals(feature.configuredTarget()),
                        "Village pile authenticated configured-target drift: "
                                + feature.registryKey());
                require(authenticatedPiles.add(feature.registryKey()),
                        "duplicate Village pile configured feature: " + feature.registryKey());
                preflightPileFeature(feature, transaction);
            } else {
                require(transaction.supportsAuthenticatedConfiguredFeature(
                                feature.registryKey(), feature.configuredTarget()),
                        "Village authenticated configured-feature authority absent: "
                                + feature.registryKey());
            }
        }
        require(authenticatedPiles.equals(PILE_FEATURE_KEYS),
                "Village pile configured-feature authority set drift");
    }

    private static void preflightPileFeature(Mc263VillageProductionAuthority.Feature feature,
            ProductionWorldTransaction transaction) {
        Mc263WorldGenRegionRandom random = Mc263WorldGenRegionRandom.fromState(
                1L, 2L, 0, false, 0L);
        Mc263WorldGenRegionRandom.State before = random.snapshot();
        ExecutionResult result = Mc263VillagePileFeatureAuthority.execute(
                feature.registryKey(), feature.configuredTarget(),
                new Position(0, Mc263VillagePileFeatureAuthority.MIN_BUILD_Y, 0),
                "NONE", "rigid", random, new Mc263VillagePileFeatureAuthority.Environment() {
                    @Override public boolean supportsExactState(String exactState) {
                        return transaction.supportsExactState(exactState);
                    }
                    @Override public boolean supportsQuery(Query query) {
                        return transaction.supportsPileFeatureQuery(query);
                    }
                    @Override public boolean supportsWriteFlags(int flags) {
                        return transaction.supportsWriteFlags(flags);
                    }
                    @Override public boolean supportsWritableChunkRadius(int radius) {
                        return transaction.supportsWritableChunkRadius(radius);
                    }
                    @Override public int minBuildY() { return transaction.minBuildY(); }
                    @Override public int maxBuildY() { return transaction.maxBuildY(); }
                    @Override public boolean isEmptyBlock(Position position) {
                        throw new IllegalStateException("Village pile preflight queried the world");
                    }
                    @Override public String supportBlockState(Position supportPosition) {
                        throw new IllegalStateException("Village pile preflight queried support state");
                    }
                    @Override public boolean isFaceSturdyUp(Position supportPosition,
                            String exactState) {
                        throw new IllegalStateException("Village pile preflight queried support");
                    }
                    @Override public boolean setBlock(Position position, String exactState, int flags) {
                        throw new IllegalStateException("Village pile preflight mutated the world");
                    }
                });
        require(result.operations().isEmpty() && result.writes().isEmpty()
                        && result.bent().isEmpty() && result.loot().isEmpty()
                        && result.entities().isEmpty() && result.rawBlockTicks().isEmpty(),
                "Village pile preflight emitted runtime effects: " + feature.registryKey());
        require(random.snapshot().equals(before),
                "Village pile preflight consumed WorldGenRegion random: " + feature.registryKey());
    }

    private static final class OrderedRuleExecutor implements RuleProcessorExecutor {
        private final Corpus authority;
        private final ProductionWorldTransaction transaction;
        private final List<String> expected;
        private int next;

        private OrderedRuleExecutor(Corpus authority, ProductionWorldTransaction transaction,
                List<String> processors) {
            this.authority = authority;
            this.transaction = transaction;
            Set<String> promoted = new HashSet<>();
            for (RuleProcessorBody body : authority.ruleProcessorsInOrder()) promoted.add(body.semanticId());
            ArrayList<String> ordered = new ArrayList<>();
            for (String semantic : processors) if (promoted.contains(semantic)) ordered.add(semantic);
            this.expected = List.copyOf(ordered);
        }

        @Override
        public RuleResult execute(String semanticId, String inputState, byte[] inputNbt,
                Position templateRelativePosition, Position processedWorldPosition,
                Position referencePosition) {
            require(next < expected.size() && expected.get(next).equals(semanticId),
                    "Village production rule semantic missing/reordered: " + semanticId);
            Mc263VillageProductionAuthority.RuleExecution result = authority.executeRuleProcessor(
                    semanticId, inputState, inputNbt, blockPos(templateRelativePosition),
                    blockPos(processedWorldPosition), blockPos(referencePosition),
                    new Mc263VillageProductionAuthority.RuleEnvironment() {
                        @Override public boolean supportsExactState(String exactState) { return true; }
                        @Override public boolean supportsTag(String tagKey) { return true; }
                        @Override public boolean blockInTag(String blockKey, String tagKey) {
                            return transaction.blockInRuleTag(blockKey, tagKey);
                        }
                        @Override public String blockStateAt(BlockPos processedWorldPos) {
                            return transaction.ruleBlockStateAt(position(processedWorldPos));
                        }
                    });
            next++;
            return new RuleResult(result.outputState(), result.outputNbt(), result.matchedRuleOrdinal(),
                    result.localRandomState48(), result.randomDraws(), result.worldBlockQueries());
        }

        private void requireComplete() {
            require(next == expected.size(), "Village production rule semantic was not executed");
        }
    }

    private static BlockPos blockPos(Position position) {
        Objects.requireNonNull(position, "Village rule position");
        return new BlockPos(position.x(), position.y(), position.z());
    }

    private static Position position(BlockPos position) {
        return new Position(position.x(), position.y(), position.z());
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }
}
