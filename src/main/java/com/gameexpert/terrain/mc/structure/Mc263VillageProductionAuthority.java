package com.gameexpert.terrain.mc.structure;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Compact production authority for the five Minecraft 26.3-snapshot-7 Village families.
 *
 * <p>The large G3J1 execution corpus remains test evidence only. Production consumes the accepted
 * G3J7 hierarchical template grammar plus this source-bound topology/semantic index. This resource
 * contains no template NBT, no generated start coordinates, and no seed/chunk lookup table.</p>
 */
final class Mc263VillageProductionAuthority {
    static final String RESOURCE = "/mc263/village-production-authority-v1.txt";
    static final int RESOURCE_BYTES = 501_475;
    static final String RESOURCE_SHA256 =
            "6d808f97caf793abd589e86c06cc17c6d191a7dd3813d00842cf843a0077427b";
    static final String FORMAT = "VPA2631";
    static final int SCHEMA = 1;
    static final String SOURCE_VERSION = "26.3-snapshot-7";
    static final String RECEIPT_ID = "VIL-G1";
    static final String SOURCE_EXECUTION_SHA256 =
            "12700d1e623b9a1352a2f0d7ce9215285f3c0ae5b505b53bcea062ddb464ff34";
    static final String HIERARCHICAL_GRAMMAR_SHA256 =
            "9f817ccf206a0ed25ce37f0d50cf1d37e59737d77205b667b4127f735e691d5b";
    static final String WEIGHTED_POOL_SHA256 =
            "3a268597f7b8eeb6d3a386f808c210cf666c1f2414829d5a2dba7f5fa9b60b4b";
    static final String PROCESSOR_DEFINITION_SHA256 =
            "0aff3922f006c063018964fe45ac9ef3397929ebec084051321458d836c8472e";
    static final String TEMPLATE_KEY_SHA256 =
            "ffb77716cbc5cd175902ec9ff36525f6a4ac6303e1386337a4e33548bbfdb609";
    static final String STATE_COMMAND_ENTITY_SHA256 =
            "4479f3a726972b0b5311b472e6c29707f7a0fdf7f1a904cbc3a645423f8cd207";

    static final int FAMILY_COUNT = 5;
    static final int POOL_COUNT = 62;
    static final int RAW_POOL_ROWS = 649;
    static final int EXPANDED_POOL_WEIGHT = 2_950;
    static final int TEMPLATE_COUNT = 478;
    static final int CONNECTOR_COUNT = 1_891;
    static final int PROCESSOR_LIST_COUNT = 16;
    static final int PROCESSOR_SEMANTIC_COUNT = 19;
    static final int RULE_PROCESSOR_COUNT = 16;
    static final int RULE_COUNT = 92;
    static final int CONFIGURED_FEATURE_COUNT = 13;
    static final int PROVIDER_RECEIPT_COUNT = 5;
    static final String PROVIDER_RECEIPTS_SHA256 =
            "a0518a78c7b6ac1e260fc78e58cff6dc3fc52ca46c651930a16f31d4137bf70a";
    static final int BENT_TYPE_COUNT = 11;
    static final int LOOT_TABLE_COUNT = 16;
    static final int ENTITY_TYPE_COUNT = 10;
    static final int EXACT_STATE_COUNT = 422;
    static final String EXACT_STATE_EVIDENCE_SHA256 =
            "82b71ec5d18b07760cd8f770506dc039f7c461c1497872b9fdee008d8866786a";

    private static final List<String> FAMILY_ORDER = List.of(
            "minecraft:village_desert", "minecraft:village_plains", "minecraft:village_savanna",
            "minecraft:village_snowy", "minecraft:village_taiga");
    private static final List<String> STRUCTURE_SET_ORDER = List.of(
            "minecraft:village_plains", "minecraft:village_desert", "minecraft:village_savanna",
            "minecraft:village_snowy", "minecraft:village_taiga");

    private Mc263VillageProductionAuthority() { throw new AssertionError("no instances"); }

    static Corpus pinned() { return Holder.CORPUS; }

    static Corpus decodeAuthenticated(byte[] bytes) {
        Objects.requireNonNull(bytes, "Village production-authority bytes");
        require(bytes.length == RESOURCE_BYTES, "Village production-authority byte-count drift");
        require(RESOURCE_SHA256.equals(sha256(bytes)),
                "Village production-authority file identity drift");
        return parse(bytes);
    }

    private static final class Holder {
        private static final Corpus CORPUS = decodeAuthenticated(readResource());
    }

    private static byte[] readResource() {
        try (InputStream input = Mc263VillageProductionAuthority.class.getResourceAsStream(RESOURCE)) {
            if (input == null) throw new IllegalStateException("missing Village production authority: " + RESOURCE);
            return input.readAllBytes();
        } catch (IOException error) {
            throw new IllegalStateException("failed to read Village production authority", error);
        }
    }

    private static Corpus parse(byte[] bytes) {
        String text = new String(bytes, StandardCharsets.UTF_8);
        require(Arrays.equals(bytes, text.getBytes(StandardCharsets.UTF_8)),
                "Village production-authority UTF-8 drift");
        require(!text.contains("\r") && text.endsWith("\n"),
                "Village production-authority newline drift");
        String[] lines = text.split("\n", -1);
        int cursor = 0;

        String[] header = row(lines[cursor++], "H", 27);
        expect(header[1], FORMAT, "format");
        expect(header[2], Integer.toString(SCHEMA), "schema");
        expect(header[3], SOURCE_VERSION, "source version");
        expect(header[4], RECEIPT_ID, "receipt id");
        expect(header[5], SOURCE_EXECUTION_SHA256, "source execution sha256");
        expect(header[6], HIERARCHICAL_GRAMMAR_SHA256, "hierarchical grammar sha256");
        expectInt(header[7], FAMILY_COUNT, "family count");
        expectInt(header[8], POOL_COUNT, "pool count");
        expectInt(header[9], RAW_POOL_ROWS, "raw pool rows");
        expectInt(header[10], EXPANDED_POOL_WEIGHT, "expanded pool weight");
        expectInt(header[11], TEMPLATE_COUNT, "template count");
        expectInt(header[12], CONNECTOR_COUNT, "connector count");
        expectInt(header[13], PROCESSOR_LIST_COUNT, "processor-list count");
        expectInt(header[14], PROCESSOR_SEMANTIC_COUNT, "processor-semantic count");
        expectInt(header[15], CONFIGURED_FEATURE_COUNT, "configured-feature count");
        expectInt(header[16], PROVIDER_RECEIPT_COUNT, "provider-receipt count");
        expect(header[17], PROVIDER_RECEIPTS_SHA256, "provider-receipt identity");
        expectInt(header[18], BENT_TYPE_COUNT, "BENT type count");
        expectInt(header[19], LOOT_TABLE_COUNT, "loot-table count");
        expectInt(header[20], ENTITY_TYPE_COUNT, "entity type count");
        expect(header[21], WEIGHTED_POOL_SHA256, "weighted-pool identity");
        expect(header[22], PROCESSOR_DEFINITION_SHA256, "processor identity");
        expect(header[23], TEMPLATE_KEY_SHA256, "template-key identity");
        expect(header[24], STATE_COMMAND_ENTITY_SHA256, "template semantic identity");
        expectInt(header[25], EXACT_STATE_COUNT, "exact-state count");
        expect(header[26], EXACT_STATE_EVIDENCE_SHA256, "exact-state evidence identity");

        String[] source = row(lines[cursor++], "A", 10);
        expect(source[1], "26.3 Snapshot 7", "display version");
        expect(source[2], "06157fedd67ff4dd6e0e6fa4a9dd0af296f0dd61", "outer server sha1");
        expect(source[3], "2f1ef79f3cad10138ad18da45b265fe656624026", "inner server sha1");
        expect(source[4], "e5efad859e05767b507f43cf5adb28b6c8944ef7c0b612527f3e6ebdd2c4ace1",
                "runtime inner sha256");
        expect(source[5], "25.0.1", "java version");
        expect(source[6], "5009", "world version");
        expect(source[7], "115", "data-pack version");
        expect(source[8], "95", "resource-pack version");
        expect(source[9], "vanilla", "pack");

        String[] set = row(lines[cursor++], "U", 10);
        expect(set[1], "minecraft:villages", "structure-set key");
        expect(set[2], "minecraft:random_spread", "structure-set placement");
        expect(set[3], "34", "structure-set spacing");
        expect(set[4], "8", "structure-set separation");
        expect(set[5], "LINEAR", "structure-set spread");
        expect(set[6], "10387312", "structure-set salt");
        expect(set[7], "1.0", "structure-set frequency");
        expect(set[8], "DEFAULT", "structure-set frequency reduction");
        expect(set[9], "0,0,0", "structure-set locate offset");

        ArrayList<StructureSetEntry> structureSet = new ArrayList<>(FAMILY_COUNT);
        for (int ordinal = 0; ordinal < FAMILY_COUNT; ordinal++) {
            String[] fields = row(lines[cursor++], "Q", 4);
            expectInt(fields[1], ordinal, "structure-set ordinal");
            expect(fields[2], STRUCTURE_SET_ORDER.get(ordinal), "structure-set family order");
            expect(fields[3], "1", "structure-set weight");
            structureSet.add(new StructureSetEntry(ordinal, fields[2], 1));
        }

        ArrayList<Family> families = new ArrayList<>(FAMILY_COUNT);
        for (int ordinal = 0; ordinal < FAMILY_COUNT; ordinal++) {
            String[] fields = row(lines[cursor++], "F", 11);
            expectInt(fields[1], ordinal, "family ordinal");
            expect(fields[2], FAMILY_ORDER.get(ordinal), "family order");
            families.add(new Family(ordinal, fields[2], fields[3], fields[4],
                    parseInt(fields[5], "family size"), parseInt(fields[6], "family start height"),
                    parseBoolean(fields[7], "family expansion hack"), fields[8],
                    parseInt(fields[9], "family max distance"), fields[10]));
        }

        ArrayList<Pool> pools = new ArrayList<>(POOL_COUNT);
        int rawRows = 0;
        int expanded = 0;
        for (int ordinal = 0; ordinal < POOL_COUNT; ordinal++) {
            String[] fields = row(lines[cursor++], "P", 6);
            expectInt(fields[1], ordinal, "pool ordinal");
            int rawCount = parseInt(fields[4], "pool raw count");
            int expandedWeight = parseInt(fields[5], "pool expanded weight");
            require(rawCount > 0 && expandedWeight >= rawCount, "invalid Village pool cardinality");
            ArrayList<PoolElement> elements = new ArrayList<>(rawCount);
            int weightSum = 0;
            for (int elementOrdinal = 0; elementOrdinal < rawCount; elementOrdinal++) {
                String[] element = row(lines[cursor++], "L", 9);
                expectInt(element[1], ordinal, "pool-element pool ordinal");
                expectInt(element[2], elementOrdinal, "pool-element ordinal");
                ElementKind kind = ElementKind.from(element[3]);
                int weight = parseInt(element[4], "pool-element weight");
                require(weight > 0, "nonpositive Village pool-element weight");
                require("RIGID".equals(element[5]) || "TERRAIN_MATCHING".equals(element[5]),
                        "unknown Village projection");
                List<String> placementProcessors;
                if (kind == ElementKind.EMPTY) {
                    require("-".equals(element[6]) && "-".equals(element[7])
                                    && "-".equals(element[8]),
                            "Village empty pool element carries payload");
                    placementProcessors = List.of();
                } else if (kind == ElementKind.FEATURE) {
                    require(!"-".equals(element[6]) && "-".equals(element[7])
                                    && "-".equals(element[8]),
                            "Village feature pool element shape drift");
                    placementProcessors = List.of();
                } else {
                    require(!"-".equals(element[6]) && !"-".equals(element[7])
                                    && !"-".equals(element[8]),
                            "Village template pool element shape drift");
                    placementProcessors = splitList(element[8]);
                }
                elements.add(new PoolElement(elementOrdinal, kind, weight, element[5],
                        element[6], element[7], placementProcessors));
                weightSum = Math.addExact(weightSum, weight);
            }
            require(weightSum == expandedWeight, "Village pool expanded-weight drift");
            rawRows = Math.addExact(rawRows, rawCount);
            expanded = Math.addExact(expanded, expandedWeight);
            pools.add(new Pool(ordinal, fields[2], fields[3], List.copyOf(elements), expandedWeight));
        }
        require(rawRows == RAW_POOL_ROWS && expanded == EXPANDED_POOL_WEIGHT,
                "Village aggregate pool cardinality drift");

        ArrayList<TemplateAuthority> templates = new ArrayList<>(TEMPLATE_COUNT);
        int connectors = 0;
        for (int ordinal = 0; ordinal < TEMPLATE_COUNT; ordinal++) {
            String[] fields = row(lines[cursor++], "T", 8);
            expectInt(fields[1], ordinal, "template ordinal");
            int connectorCount = parseInt(fields[7], "template connector count");
            ArrayList<Connector> rows = new ArrayList<>(connectorCount);
            for (int connectorOrdinal = 0; connectorOrdinal < connectorCount; connectorOrdinal++) {
                String[] connector = row(lines[cursor++], "C", 14);
                expectInt(connector[1], ordinal, "connector template ordinal");
                expectInt(connector[2], connectorOrdinal, "connector ordinal");
                rows.add(new Connector(connectorOrdinal,
                        parseInt(connector[3], "connector x"), parseInt(connector[4], "connector y"),
                        parseInt(connector[5], "connector z"), connector[6], connector[7], connector[8],
                        connector[9], connector[10], connector[11],
                        parseInt(connector[12], "connector selection priority"),
                        parseInt(connector[13], "connector placement priority")));
            }
            connectors = Math.addExact(connectors, connectorCount);
            templates.add(new TemplateAuthority(ordinal, fields[2],
                    parseInt(fields[3], "template size x"), parseInt(fields[4], "template size y"),
                    parseInt(fields[5], "template size z"), parseInt(fields[6], "template block count"),
                    List.copyOf(rows)));
        }
        require(connectors == CONNECTOR_COUNT, "Village connector cardinality drift");

        String[] inline = row(lines[cursor++], "I", 3);
        expect(inline[1], "inline", "inline processor-list identity");
        ProcessorList inlineList = new ProcessorList("inline",
                inline[2].isEmpty() ? List.of() : splitList(inline[2]));

        ArrayList<ProcessorList> processorLists = new ArrayList<>(PROCESSOR_LIST_COUNT);
        for (int ordinal = 0; ordinal < PROCESSOR_LIST_COUNT; ordinal++) {
            String[] fields = row(lines[cursor++], "R", 4);
            expectInt(fields[1], ordinal, "processor-list ordinal");
            processorLists.add(new ProcessorList(fields[2], splitList(fields[3])));
        }

        ArrayList<String> processorSemantics = new ArrayList<>(PROCESSOR_SEMANTIC_COUNT);
        for (int ordinal = 0; ordinal < PROCESSOR_SEMANTIC_COUNT; ordinal++) {
            String[] fields = row(lines[cursor++], "S", 3);
            expectInt(fields[1], ordinal, "processor-semantic ordinal");
            processorSemantics.add(fields[2]);
        }

        ArrayList<RuleProcessorBody> ruleProcessors = new ArrayList<>(RULE_PROCESSOR_COUNT);
        int ruleCount = 0;
        for (int processorOrdinal = 0; processorOrdinal < RULE_PROCESSOR_COUNT; processorOrdinal++) {
            String[] definition = row(lines[cursor++], "D", 8);
            expectInt(definition[1], processorOrdinal, "rule-processor ordinal");
            int semanticOrdinal = parseInt(definition[2], "rule-processor semantic ordinal");
            require(semanticOrdinal >= 0 && semanticOrdinal < processorSemantics.size(),
                    "Village rule-processor semantic ordinal out of range");
            expect(definition[3], processorSemantics.get(semanticOrdinal),
                    "rule-processor semantic identity");
            require(processorLists.get(processorOrdinal).processorsInOrder()
                            .equals(List.of(definition[3])),
                    "Village rule-processor list/semantic binding drift");
            int count = parseInt(definition[4], "rule count");
            require(count > 0, "empty Village rule processor");
            expect(definition[5], "FIRST", "rule selection");
            expect(definition[6], "POS_LEGACY48", "rule random source");
            require(validSha256(definition[7]), "invalid Village rule source-row sha256");
            ArrayList<RuleDefinition> rules = new ArrayList<>(count);
            for (int ruleOrdinal = 0; ruleOrdinal < count; ruleOrdinal++) {
                String[] rule = row(lines[cursor++], "K", 11);
                expectInt(rule[1], processorOrdinal, "rule processor ordinal");
                expectInt(rule[2], ruleOrdinal, "rule ordinal");
                InputPredicateKind inputKind = InputPredicateKind.from(rule[3]);
                String inputValue = rule[4];
                String probability = rule[5];
                LocationPredicateKind locationKind = LocationPredicateKind.from(rule[6]);
                String locationValue = rule[7];
                expect(rule[8], "A", "position predicate");
                require(rule[9].startsWith("minecraft:") && !rule[9].contains("|"),
                        "invalid Village rule output state");
                expect(rule[10], "P", "rule block-entity modifier");
                validateRulePredicate(inputKind, inputValue, probability,
                        locationKind, locationValue);
                rules.add(new RuleDefinition(ruleOrdinal, inputKind, inputValue, probability,
                        locationKind, locationValue, rule[9]));
            }
            ruleCount = Math.addExact(ruleCount, count);
            ruleProcessors.add(new RuleProcessorBody(processorOrdinal, semanticOrdinal,
                    definition[3], definition[7], List.copyOf(rules)));
        }
        require(ruleCount == RULE_COUNT, "Village rule cardinality drift");

        ArrayList<Feature> features = new ArrayList<>(CONFIGURED_FEATURE_COUNT);
        for (int ordinal = 0; ordinal < CONFIGURED_FEATURE_COUNT; ordinal++) {
            String[] fields = row(lines[cursor++], "G", 4);
            expectInt(fields[1], ordinal, "configured-feature ordinal");
            features.add(new Feature(ordinal, fields[2], fields[3]));
        }

        ArrayList<ProviderReceipt> providerReceipts = new ArrayList<>(PROVIDER_RECEIPT_COUNT);
        for (int ordinal = 0; ordinal < PROVIDER_RECEIPT_COUNT; ordinal++) {
            String[] fields = row(lines[cursor++], "V", 9);
            expectInt(fields[1], ordinal, "provider-receipt ordinal");
            int featureOrdinal = parseInt(fields[2], "provider feature ordinal");
            int drawBound = parseInt(fields[6], "provider draw bound");
            int drawCount = parseInt(fields[7], "provider draw count");
            List<String> states = splitList(fields[8]);
            require(featureOrdinal >= 0 && featureOrdinal < features.size()
                            && features.get(featureOrdinal).registryKey().equals(fields[3]),
                    "Village provider feature binding drift");
            require((drawCount == 0 && "none".equals(fields[5]) && drawBound == 0
                            && states.size() == 1)
                            || (drawCount == 1 && "nextInt".equals(fields[5])
                                    && drawBound > 0 && states.size() == drawBound),
                    "Village provider draw/state cardinality drift");
            require(states.stream().allMatch(state -> state.startsWith("minecraft:")),
                    "Village provider state identity drift");
            providerReceipts.add(new ProviderReceipt(ordinal, featureOrdinal, fields[3], fields[4],
                    fields[5], drawBound, drawCount, states));
        }

        ArrayList<String> exactStates = new ArrayList<>(EXACT_STATE_COUNT);
        String priorState = null;
        for (int ordinal = 0; ordinal < EXACT_STATE_COUNT; ordinal++) {
            String[] fields = row(lines[cursor++], "X", 3);
            expectInt(fields[1], ordinal, "exact-state ordinal");
            require(fields[2].startsWith("minecraft:") && !fields[2].contains("|"),
                    "invalid Village exact-state authority row");
            if (priorState != null) {
                require(priorState.compareTo(fields[2]) < 0,
                        "Village exact-state authority order drift");
            }
            exactStates.add(fields[2]);
            priorState = fields[2];
        }

        ArrayList<BentIdentity> bent = new ArrayList<>(BENT_TYPE_COUNT);
        for (int ordinal = 0; ordinal < BENT_TYPE_COUNT; ordinal++) {
            String[] fields = row(lines[cursor++], "B", 5);
            expectInt(fields[1], ordinal, "BENT identity ordinal");
            bent.add(new BentIdentity(ordinal, fields[2], fields[3],
                    parseInt(fields[4], "BENT occurrences")));
        }
        ArrayList<LootIdentity> loot = new ArrayList<>(LOOT_TABLE_COUNT);
        for (int ordinal = 0; ordinal < LOOT_TABLE_COUNT; ordinal++) {
            String[] fields = row(lines[cursor++], "O", 4);
            expectInt(fields[1], ordinal, "LOOT identity ordinal");
            loot.add(new LootIdentity(ordinal, fields[2], parseInt(fields[3], "LOOT occurrences")));
        }
        ArrayList<EntityIdentity> entities = new ArrayList<>(ENTITY_TYPE_COUNT);
        for (int ordinal = 0; ordinal < ENTITY_TYPE_COUNT; ordinal++) {
            String[] fields = row(lines[cursor++], "E", 4);
            expectInt(fields[1], ordinal, "ENTS identity ordinal");
            entities.add(new EntityIdentity(ordinal, fields[2], parseInt(fields[3], "ENTS occurrences")));
        }
        require(cursor == lines.length - 1 && lines[cursor].isEmpty(),
                "Village production-authority trailing data");

        return new Corpus(List.copyOf(structureSet), List.copyOf(families), List.copyOf(pools),
                List.copyOf(templates), inlineList, List.copyOf(processorLists),
                List.copyOf(processorSemantics), List.copyOf(ruleProcessors), List.copyOf(features),
                List.copyOf(providerReceipts), List.copyOf(exactStates),
                new Sidecars(List.copyOf(bent), List.copyOf(loot), List.copyOf(entities)));
    }

    static final class Corpus {
        private final List<StructureSetEntry> structureSetEntries;
        private final List<Family> families;
        private final List<Pool> pools;
        private final List<TemplateAuthority> templates;
        private final ProcessorList inlineProcessorList;
        private final List<ProcessorList> processorLists;
        private final List<String> processorSemantics;
        private final List<RuleProcessorBody> ruleProcessors;
        private final List<Feature> features;
        private final List<ProviderReceipt> providerReceipts;
        private final List<String> exactStates;
        private final Sidecars sidecars;
        private final Map<String, Family> familyByKey;
        private final Map<String, Pool> poolByKey;
        private final Map<String, TemplateAuthority> templateByKey;
        private final Map<String, ProcessorList> processorListByKey;
        private final Map<String, RuleProcessorBody> ruleProcessorBySemantic;
        private final Map<String, Feature> featureByKey;
        private final Mc263VillageHierarchicalGrammar.Corpus hierarchy;

        private Corpus(List<StructureSetEntry> structureSetEntries, List<Family> families,
                List<Pool> pools, List<TemplateAuthority> templates,
                ProcessorList inlineProcessorList, List<ProcessorList> processorLists,
                List<String> processorSemantics, List<RuleProcessorBody> ruleProcessors,
                List<Feature> features, List<ProviderReceipt> providerReceipts,
                List<String> exactStates, Sidecars sidecars) {
            this.structureSetEntries = structureSetEntries;
            this.families = families;
            this.pools = pools;
            this.templates = templates;
            this.inlineProcessorList = inlineProcessorList;
            this.processorLists = processorLists;
            this.processorSemantics = processorSemantics;
            this.ruleProcessors = ruleProcessors;
            this.features = features;
            this.providerReceipts = providerReceipts;
            this.exactStates = exactStates;
            this.sidecars = sidecars;
            familyByKey = index(families, Family::structureKey, "family");
            poolByKey = index(pools, Pool::key, "pool");
            templateByKey = index(templates, TemplateAuthority::key, "template");
            LinkedHashMap<String, ProcessorList> processors = new LinkedHashMap<>();
            putUnique(processors, inlineProcessorList.identity(), inlineProcessorList, "processor list");
            for (ProcessorList value : processorLists) {
                putUnique(processors, value.identity(), value, "processor list");
            }
            processorListByKey = Map.copyOf(processors);
            ruleProcessorBySemantic = index(ruleProcessors, RuleProcessorBody::semanticId, "rule processor");
            featureByKey = index(features, Feature::registryKey, "configured feature");
            hierarchy = Mc263VillageHierarchicalGrammar.pinned();
            validate();
        }

        List<StructureSetEntry> structureSetEntries() { return structureSetEntries; }
        List<Family> families() { return families; }
        List<Pool> poolsInOrder() { return pools; }
        List<TemplateAuthority> templatesInOrder() { return templates; }
        ProcessorList inlineProcessorList() { return inlineProcessorList; }
        List<ProcessorList> processorListsInOrder() { return processorLists; }
        List<String> processorSemanticsInOrder() { return processorSemantics; }
        List<RuleProcessorBody> ruleProcessorsInOrder() { return ruleProcessors; }
        List<String> ruleExactStatesInOrder() {
            LinkedHashSet<String> states = new LinkedHashSet<>();
            for (RuleProcessorBody body : ruleProcessors) {
                for (RuleDefinition rule : body.rulesInOrder()) {
                    if (rule.inputKind() == InputPredicateKind.EXACT_STATE) states.add(rule.inputValue());
                    states.add(rule.outputState());
                }
            }
            return List.copyOf(states);
        }
        List<Feature> featuresInOrder() { return features; }
        List<ProviderReceipt> providerReceiptsInOrder() { return providerReceipts; }
        List<String> exactStatesInOrder() { return exactStates; }
        Sidecars sidecars() { return sidecars; }
        Mc263VillageHierarchicalGrammar.Corpus hierarchy() { return hierarchy; }

        Family requireFamily(String key) { return requireMapped(familyByKey, key, "Village family"); }
        Pool requirePool(String key) { return requireMapped(poolByKey, key, "Village pool"); }
        TemplateAuthority requireTemplate(String key) {
            return requireMapped(templateByKey, key, "Village template authority");
        }
        ProcessorList requireProcessorList(String key) {
            return requireMapped(processorListByKey, key, "Village processor list");
        }
        Feature requireFeature(String key) {
            return requireMapped(featureByKey, key, "Village configured feature");
        }
        ProviderReceipt requireProviderReceipt(String key) {
            for (ProviderReceipt receipt : providerReceipts) {
                if (receipt.featureKey().equals(key)) return receipt;
            }
            throw invalid("Village provider receipt unavailable: " + key);
        }
        RuleProcessorBody requireRuleProcessor(String semanticId) {
            return requireMapped(ruleProcessorBySemantic, semanticId, "Village rule processor");
        }

        RuleExecution executeRuleProcessor(String semanticId, String inputState, byte[] inputNbt,
                BlockPos templateRelativePos, BlockPos processedWorldPos, BlockPos referencePos,
                RuleEnvironment environment) {
            RuleProcessorBody body = requireRuleProcessor(semanticId);
            Objects.requireNonNull(inputState, "Village rule input state");
            Objects.requireNonNull(templateRelativePos, "Village rule template-relative position");
            Objects.requireNonNull(processedWorldPos, "Village rule processed position");
            Objects.requireNonNull(referencePos, "Village rule reference position");
            Objects.requireNonNull(environment, "Village rule environment");
            String inputBlock = blockKey(inputState);
            for (RuleDefinition rule : body.rulesInOrder()) {
                require(environment.supportsExactState(rule.outputState()),
                        "Village rule output-state capability absent: " + rule.outputState());
                if (rule.inputKind() == InputPredicateKind.EXACT_STATE) {
                    require(environment.supportsExactState(rule.inputValue()),
                            "Village rule input-state capability absent: " + rule.inputValue());
                } else if (rule.inputKind() == InputPredicateKind.TAG) {
                    require(environment.supportsTag(rule.inputValue()),
                            "Village rule tag capability absent: " + rule.inputValue());
                }
            }
            LocalLegacy48 random = LocalLegacy48.forPosition(processedWorldPos);
            int worldQueries = 0;
            for (RuleDefinition rule : body.rulesInOrder()) {
                if (!matchesInput(rule, inputState, inputBlock, environment, random)) continue;
                if (rule.locationKind() == LocationPredicateKind.BLOCK) {
                    String worldState = Objects.requireNonNull(environment.blockStateAt(processedWorldPos),
                            "Village rule location state");
                    worldQueries++;
                    if (!blockKey(worldState).equals(rule.locationValue())) continue;
                }
                return new RuleExecution(rule.outputState(), inputNbt, rule.ordinal(),
                        random.state48(), random.draws(), worldQueries);
            }
            return new RuleExecution(inputState, inputNbt, -1, random.state48(),
                    random.draws(), worldQueries);
        }

        private static boolean matchesInput(RuleDefinition rule, String inputState,
                String inputBlock, RuleEnvironment environment, LocalLegacy48 random) {
            return switch (rule.inputKind()) {
                case BLOCK -> inputBlock.equals(rule.inputValue());
                case EXACT_STATE -> inputState.equals(rule.inputValue());
                case TAG -> environment.blockInTag(inputBlock, rule.inputValue());
                case RANDOM_BLOCK -> inputBlock.equals(rule.inputValue())
                        && random.nextFloat() < rule.probabilityFloat();
            };
        }

        List<String> placementProcessors(String templateKey, String projection, String processorList) {
            requireProcessorList(processorList);
            List<String> found = null;
            for (Pool pool : pools) {
                for (PoolElement element : pool.elements()) {
                    if (element.kind() == ElementKind.TEMPLATE
                            && element.key().equals(templateKey)
                            && projectionId(element.projection()).equals(projection)
                            && element.processorList().equals(processorList)) {
                        if (found != null && !found.equals(element.placementProcessorsInOrder())) {
                            throw invalid("ambiguous Village placement processor binding: " + templateKey);
                        }
                        found = element.placementProcessorsInOrder();
                    }
                }
            }
            if (found == null) {
                throw invalid("Village template binding absent from pools: " + templateKey);
            }
            Mc263VillageHierarchicalGrammar.Template template = hierarchy.requireTemplate(templateKey);
            require(template.accepts(projection, processorList, found),
                    "Village hierarchical processor/projection binding absent: " + templateKey);
            return found;
        }

        private void validate() {
            require(families.size() == FAMILY_COUNT && pools.size() == POOL_COUNT
                            && templates.size() == TEMPLATE_COUNT
                            && processorLists.size() == PROCESSOR_LIST_COUNT
                            && processorSemantics.size() == PROCESSOR_SEMANTIC_COUNT
                            && ruleProcessors.size() == RULE_PROCESSOR_COUNT
                            && features.size() == CONFIGURED_FEATURE_COUNT
                            && exactStates.size() == EXACT_STATE_COUNT,
                    "Village production-authority cardinality drift");
            require(Mc263VillageHierarchicalGrammar.RESOURCE_SHA256.equals(HIERARCHICAL_GRAMMAR_SHA256),
                    "Village hierarchy identity constant drift");
            require(hierarchy.templates().size() == templates.size(),
                    "Village hierarchy/authority template cardinality drift");
            for (int ordinal = 0; ordinal < templates.size(); ordinal++) {
                TemplateAuthority authority = templates.get(ordinal);
                Mc263VillageHierarchicalGrammar.Template template = hierarchy.templates().get(ordinal);
                require(authority.ordinal() == ordinal && authority.key().equals(template.key())
                                && authority.sizeX() == template.size().x()
                                && authority.sizeY() == template.size().y()
                                && authority.sizeZ() == template.size().z()
                                && authority.blockCount() == template.blockCount(),
                        "Village hierarchy/authority template order drift at " + ordinal);
                int markerConnectors = 0;
                for (Mc263VillageHierarchicalGrammar.Cell cell : template.expand()) {
                    if (cell.marker() != null && "CONNECTOR".equals(cell.marker().op())) markerConnectors++;
                }
                require(markerConnectors == authority.connectors().size(),
                        "Village hierarchy/authority connector cardinality drift: " + authority.key());
            }

            HashSet<String> semanticSet = new HashSet<>(processorSemantics);
            require(semanticSet.size() == processorSemantics.size(),
                    "duplicate Village processor semantic");
            HashSet<String> promotedRuleSemantics = new HashSet<>();
            HashSet<String> promotedRuleRowHashes = new HashSet<>();
            int promotedRules = 0;
            for (int ordinal = 0; ordinal < ruleProcessors.size(); ordinal++) {
                RuleProcessorBody body = ruleProcessors.get(ordinal);
                require(body.ordinal() == ordinal,
                        "Village rule-processor order drift at " + ordinal);
                require(body.semanticOrdinal() >= 0 && body.semanticOrdinal() < processorSemantics.size()
                                && processorSemantics.get(body.semanticOrdinal()).equals(body.semanticId()),
                        "Village rule-processor semantic binding drift at " + ordinal);
                require(processorLists.get(ordinal).processorsInOrder().equals(List.of(body.semanticId())),
                        "Village rule-processor list binding drift at " + ordinal);
                require(promotedRuleSemantics.add(body.semanticId()),
                        "duplicate Village promoted rule semantic: " + body.semanticId());
                require(promotedRuleRowHashes.add(body.sourceRowSha256()),
                        "duplicate Village rule source-row hash: " + body.sourceRowSha256());
                for (int ruleOrdinal = 0; ruleOrdinal < body.rulesInOrder().size(); ruleOrdinal++) {
                    RuleDefinition rule = body.rulesInOrder().get(ruleOrdinal);
                    require(rule.ordinal() == ruleOrdinal,
                            "Village rule order drift at " + ordinal + ':' + ruleOrdinal);
                    blockKey(rule.outputState());
                    if (rule.inputKind() == InputPredicateKind.EXACT_STATE) blockKey(rule.inputValue());
                    promotedRules++;
                }
            }
            require(promotedRuleSemantics.size() == RULE_PROCESSOR_COUNT && promotedRules == RULE_COUNT,
                    "Village promoted rule aggregate drift");
            for (ProcessorList list : processorListByKey.values()) {
                if ("inline".equals(list.identity())) {
                    require(list.processorsInOrder().isEmpty(),
                            "Village inline processor list unexpectedly carries registered processors");
                } else {
                    require(!list.processorsInOrder().isEmpty(),
                            "empty Village processor list: " + list.identity());
                }
                for (String semantic : list.processorsInOrder()) {
                    require(semanticSet.contains(semantic),
                            "Village processor list references unknown semantic: " + semantic);
                }
            }

            for (Pool pool : pools) {
                require("minecraft:empty".equals(pool.fallback()) || poolByKey.containsKey(pool.fallback()),
                        "Village pool fallback unavailable: " + pool.fallback());
                for (PoolElement element : pool.elements()) {
                    if (element.kind() == ElementKind.TEMPLATE) {
                        TemplateAuthority template = requireTemplate(element.key());
                        placementProcessors(template.key(), projectionId(element.projection()),
                                element.processorList());
                    } else if (element.kind() == ElementKind.FEATURE) {
                        requireFeature(element.key());
                    }
                }
            }
            for (TemplateAuthority template : templates) {
                for (Connector connector : template.connectors()) {
                    require("minecraft:empty".equals(connector.pool()) || poolByKey.containsKey(connector.pool()),
                            "Village connector pool unavailable: " + connector.pool());
                }
            }
            for (Family family : families) {
                require(poolByKey.containsKey(family.startPool()),
                        "Village family start pool unavailable: " + family.startPool());
                require(family.size() == 6 && family.startHeightAbsolute() == 0
                                && family.useExpansionHack()
                                && "WORLD_SURFACE_WG".equals(family.projectStartToHeightmap())
                                && family.maxDistanceFromCenter() == 80
                                && "beard_thin".equals(family.terrainAdaptation()),
                        "Village family codec drift: " + family.structureKey());
            }
            require(unique(sidecars.bent(), BentIdentity::blockEntityType)
                            && unique(sidecars.loot(), LootIdentity::lootTable)
                            && unique(sidecars.entities(), EntityIdentity::entityType),
                    "Village sidecar identity duplication");
        }
    }

    enum ElementKind {
        TEMPLATE("T"), FEATURE("F"), EMPTY("E");
        private final String token;
        ElementKind(String token) { this.token = token; }
        static ElementKind from(String token) {
            for (ElementKind value : values()) if (value.token.equals(token)) return value;
            throw invalid("unknown Village production pool element kind: " + token);
        }
    }

    enum InputPredicateKind {
        BLOCK("B"), RANDOM_BLOCK("R"), TAG("T"), EXACT_STATE("S");
        private final String token;
        InputPredicateKind(String token) { this.token = token; }
        static InputPredicateKind from(String token) {
            for (InputPredicateKind value : values()) if (value.token.equals(token)) return value;
            throw invalid("unknown Village rule input predicate: " + token);
        }
    }

    enum LocationPredicateKind {
        ALWAYS("A"), BLOCK("B");
        private final String token;
        LocationPredicateKind(String token) { this.token = token; }
        static LocationPredicateKind from(String token) {
            for (LocationPredicateKind value : values()) if (value.token.equals(token)) return value;
            throw invalid("unknown Village rule location predicate: " + token);
        }
    }

    record RuleProcessorBody(int ordinal, int semanticOrdinal, String semanticId,
            String sourceRowSha256, List<RuleDefinition> rulesInOrder) {
        RuleProcessorBody { rulesInOrder = List.copyOf(rulesInOrder); }
    }
    record RuleDefinition(int ordinal, InputPredicateKind inputKind, String inputValue,
            String probability, LocationPredicateKind locationKind, String locationValue,
            String outputState) {
        float probabilityFloat() {
            return "-".equals(probability) ? Float.NaN : Float.parseFloat(probability);
        }
    }
    record BlockPos(int x, int y, int z) { }
    interface RuleEnvironment {
        boolean supportsExactState(String exactState);
        boolean supportsTag(String tagKey);
        boolean blockInTag(String blockKey, String tagKey);
        String blockStateAt(BlockPos processedWorldPos);
    }
    record RuleExecution(String outputState, byte[] outputNbt, int matchedRuleOrdinal,
            long localRandomState48, int randomDraws, int worldBlockQueries) {
        RuleExecution {
            outputNbt = outputNbt == null ? null : outputNbt.clone();
        }
        @Override public byte[] outputNbt() {
            return outputNbt == null ? null : outputNbt.clone();
        }
    }

    private static final class LocalLegacy48 {
        private static final long MULTIPLIER = 25_214_903_917L;
        private static final long INCREMENT = 11L;
        private static final long MASK = (1L << 48) - 1L;
        private long state;
        private int draws;
        private LocalLegacy48(long state) { this.state = state; }
        static LocalLegacy48 forPosition(BlockPos position) {
            long mixed = (long) (position.x() * 3_129_871)
                    ^ (long) position.z() * 116_129_781L ^ (long) position.y();
            mixed = mixed * mixed * 42_317_861L + mixed * 11L;
            long positionSeed = mixed >> 16;
            return new LocalLegacy48((positionSeed ^ MULTIPLIER) & MASK);
        }
        float nextFloat() {
            state = (state * MULTIPLIER + INCREMENT) & MASK;
            draws++;
            return (float) (state >>> 24) * 0x1.0p-24F;
        }
        long state48() { return state; }
        int draws() { return draws; }
    }

    record StructureSetEntry(int ordinal, String structureKey, int weight) { }
    record Family(int ordinal, String structureKey, String biomeTag, String startPool, int size,
            int startHeightAbsolute, boolean useExpansionHack, String projectStartToHeightmap,
            int maxDistanceFromCenter, String terrainAdaptation) { }
    record Pool(int ordinal, String key, String fallback, List<PoolElement> elements,
            int expandedWeight) { }
    record PoolElement(int ordinal, ElementKind kind, int weight, String projection, String key,
            String processorList, List<String> placementProcessorsInOrder) {
        PoolElement { placementProcessorsInOrder = List.copyOf(placementProcessorsInOrder); }
    }
    record TemplateAuthority(int ordinal, String key, int sizeX, int sizeY, int sizeZ,
            int blockCount, List<Connector> connectors) { }
    record Connector(int ordinal, int x, int y, int z, String front, String top, String joint,
            String name, String target, String pool, int selectionPriority, int placementPriority) { }
    record ProcessorList(String identity, List<String> processorsInOrder) {
        ProcessorList { processorsInOrder = List.copyOf(processorsInOrder); }
    }
    record Feature(int ordinal, String registryKey, String configuredTarget) { }
    record ProviderReceipt(int ordinal, int featureOrdinal, String featureKey,
            String runtimeProviderType, String drawMethod, int drawBound, int drawCount,
            List<String> orderedExhaustiveStates) {
        ProviderReceipt { orderedExhaustiveStates = List.copyOf(orderedExhaustiveStates); }
    }
    record BentIdentity(int ordinal, String policy, String blockEntityType, int occurrences) { }
    record LootIdentity(int ordinal, String lootTable, int occurrences) { }
    record EntityIdentity(int ordinal, String entityType, int occurrences) { }
    record Sidecars(List<BentIdentity> bent, List<LootIdentity> loot, List<EntityIdentity> entities) { }

    private static void validateRulePredicate(InputPredicateKind inputKind, String inputValue,
            String probability, LocationPredicateKind locationKind, String locationValue) {
        require(inputValue != null && !inputValue.isEmpty() && !inputValue.contains("|"),
                "invalid Village rule input value");
        if (inputKind == InputPredicateKind.EXACT_STATE) {
            blockKey(inputValue);
        } else {
            require(validBlockKey(inputValue), "invalid Village rule input block/tag: " + inputValue);
        }
        if (inputKind == InputPredicateKind.RANDOM_BLOCK) {
            float parsed = parseProbability(probability);
            require(parsed > 0.0F && parsed <= 1.0F,
                    "invalid Village random-block probability: " + probability);
        } else {
            expect(probability, "-", "non-random rule probability");
        }
        if (locationKind == LocationPredicateKind.ALWAYS) {
            expect(locationValue, "-", "always-true location payload");
        } else {
            require(validBlockKey(locationValue),
                    "invalid Village rule location block: " + locationValue);
        }
    }

    private static float parseProbability(String value) {
        try {
            float parsed = Float.parseFloat(value);
            require(Float.isFinite(parsed) && Float.toString(parsed).equals(value),
                    "non-canonical Village rule probability: " + value);
            return parsed;
        } catch (NumberFormatException error) {
            throw invalid("invalid Village rule probability: " + value);
        }
    }

    private static String blockKey(String exactState) {
        Objects.requireNonNull(exactState, "Village exact state");
        int open = exactState.indexOf('[');
        String block = open < 0 ? exactState : exactState.substring(0, open);
        require(validBlockKey(block), "invalid Village rule block state: " + exactState);
        if (open >= 0) {
            require(exactState.endsWith("]") && open < exactState.length() - 2
                            && exactState.indexOf('[', open + 1) < 0,
                    "malformed Village rule block state: " + exactState);
            String properties = exactState.substring(open + 1, exactState.length() - 1);
            HashSet<String> names = new HashSet<>();
            for (String property : properties.split(",", -1)) {
                int equals = property.indexOf('=');
                require(equals > 0 && equals < property.length() - 1
                                && property.indexOf('=', equals + 1) < 0,
                        "malformed Village rule state property: " + exactState);
                require(names.add(property.substring(0, equals)),
                        "duplicate Village rule state property: " + exactState);
            }
        }
        return block;
    }

    private static boolean validBlockKey(String value) {
        if (value == null || !value.startsWith("minecraft:") || value.length() == 10) return false;
        for (int index = 10; index < value.length(); index++) {
            char ch = value.charAt(index);
            if (!((ch >= 'a' && ch <= 'z') || (ch >= '0' && ch <= '9')
                    || ch == '_' || ch == '.' || ch == '/' || ch == '-')) return false;
        }
        return true;
    }

    private static String projectionId(String value) {
        return switch (value) {
            case "RIGID" -> "rigid";
            case "TERRAIN_MATCHING" -> "terrain_matching";
            default -> throw invalid("unknown Village production projection: " + value);
        };
    }

    private static List<String> splitList(String value) {
        require(!value.isEmpty(), "empty Village production list");
        List<String> result = List.of(value.split(",", -1));
        for (String entry : result) require(!entry.isEmpty(), "empty Village production list entry");
        return result;
    }

    private static String[] row(String line, String tag, int fields) {
        String[] values = line.split("\\|", -1);
        require(values.length == fields && tag.equals(values[0]),
                "Village production-authority row shape drift: expected " + tag);
        return values;
    }

    private static int parseInt(String value, String label) {
        try { return Integer.parseInt(value); }
        catch (NumberFormatException error) { throw invalid("invalid Village " + label + ": " + value); }
    }

    private static boolean parseBoolean(String value, String label) {
        if ("true".equals(value)) return true;
        if ("false".equals(value)) return false;
        throw invalid("invalid Village " + label + ": " + value);
    }

    private static void expectInt(String actual, int expected, String label) {
        require(parseInt(actual, label) == expected, "Village production-authority " + label + " drift");
    }

    private static void expect(String actual, String expected, String label) {
        require(expected.equals(actual), "Village production-authority " + label + " drift");
    }

    private static <T> Map<String, T> index(List<T> values,
            java.util.function.Function<T, String> key, String label) {
        LinkedHashMap<String, T> result = new LinkedHashMap<>();
        for (T value : values) putUnique(result, key.apply(value), value, label);
        return Map.copyOf(result);
    }

    private static <T> void putUnique(Map<String, T> values, String key, T value, String label) {
        require(values.put(key, value) == null, "duplicate Village " + label + ": " + key);
    }

    private static <T> boolean unique(List<T> values,
            java.util.function.Function<T, String> key) {
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        for (T value : values) if (!seen.add(key.apply(value))) return false;
        return true;
    }

    private static <T> T requireMapped(Map<String, T> values, String key, String label) {
        Objects.requireNonNull(key, label + " key");
        T value = values.get(key);
        if (value == null) throw invalid("unknown " + label + ": " + key);
        return value;
    }

    private static boolean validSha256(String value) {
        if (value == null || value.length() != 64) return false;
        for (int index = 0; index < value.length(); index++) {
            char ch = value.charAt(index);
            if (!((ch >= '0' && ch <= '9') || (ch >= 'a' && ch <= 'f'))) return false;
        }
        return true;
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw invalid(message);
    }

    private static IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException(message);
    }
}
