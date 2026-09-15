package com.gameexpert.terrain.mc.structure;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Closed semantic catalog for Minecraft Java 26.3-snapshot-7 Abandoned Camp jigsaw data.
 *
 * <p>The backing payload is a generated arithmetic-run grammar. It deliberately contains no raw
 * asset tree, binary template payload, or expanded block-coordinate payload. Loading and all
 * cross-reference validation complete before the singleton is published.</p>
 */
public final class Mc263AbandonedCampCatalog {
    public static final String CATALOG_SHA256 =
            "66a9f8fa9a955f91b27bd8a7120946b0ca30b5ab69e694bae1e2cb38dc0e0bbd";
    public static final String ORACLE_SHA256 =
            "61657141fdc6dac4261620e835fbcafffe44dddc7d14f0b886acdda608573f35";
    public static final String JIGSAW_FINAL_STATES_SIDECAR_SHA256 =
            "2fe2d5ddae8aafa69f7a700102a0ddeb5dd4c0e316b957118fafe1b054a14e94";
    public static final String STATE_TOKEN_ORDER_SHA256 =
            "226374943617da10821ab9d13b86e467bc6aa442aaf040b059ffc3814e28d96d";
    public static final String COMMAND_IDENTITY_ORDER_SHA256 =
            "9ae010c98393629eb52725d982021322a0eea8ae645869890c0f9a3947507ecd";
    public static final String SIDECAR_CONTRACT_SHA256 =
            "248bba2896997393f05127a9b97dfc9a29d006c1a2ea9a8f7184ce04817e4d1a";
    public static final String SIDECAR_PIN_SHA256 =
            "e57a958737ae3007e574d494264fcdf367df619e0afc347ce903ddaf7a340201";
    public static final String SIDECAR_PRODUCER_ID =
            "gameexpert-official-26.3-abandoned-camp-oracle-v3";
    public static final String SIDECAR_SOURCE_SHA256 =
            "ec9aff294de1c5bfde884fdfcf161c38ce0821eae33b14c65ca11683fccd5669";
    public static final String TEMPLATE_IDENTITY_ORDER_SHA256 =
            "94de5603bbd319d239318299d033670052166c780d7cdf582f7e6468be7e9bfb";

    public static final int STRUCTURE_COUNT = 18;
    public static final int POOL_COUNT = 57;
    public static final int WEIGHTED_ELEMENT_COUNT = 1_083;
    public static final int TEMPLATE_ELEMENT_COUNT = 1_062;
    public static final int CONFIGURED_FEATURE_ELEMENT_COUNT = 21;
    public static final int TEMPLATE_COUNT = 297;
    public static final int COMMAND_COUNT = 42_906;
    public static final int JIGSAW_COMMAND_COUNT = 514;

    public static final String INLINE_PROCESSOR_KEY = "inline";
    public static final String INLINE_PROCESSOR_SHA256 =
            "80732fe8d264c25bcb13bdb27cec96f0080600d6ac1c92b90d5fefea8332e151";
    public static final String EMPTY_POOL_KEY = "minecraft:empty";
    public static final String RIGID_PROJECTION = "rigid";
    public static final String TEMPLATE_ELEMENT_TYPE = "minecraft:legacy_single_pool_element";
    public static final String FEATURE_ELEMENT_TYPE = "minecraft:feature_pool_element";
    public static final String ASSET_BOUNDARY =
            "lossless semantic arithmetic-run grammar; no raw template NBT or raw coordinate list";

    private static final byte[] MAGIC = "ACAT2631".getBytes(StandardCharsets.US_ASCII);
    private static final int MAX_STRING_BYTES = 32_768;
    private static final List<String> EXPECTED_REPLACEMENT_STATES = List.of(
            "minecraft:podzol[snowy=false]",
            "minecraft:dirt_path",
            "minecraft:dirt",
            "minecraft:air");
    private static final List<String> EXPECTED_PROCESSOR_ORDER = List.of(
            "BlockIgnoreProcessor.STRUCTURE_BLOCK",
            "JigsawReplacementProcessor.INSTANCE",
            "element processor list in codec order",
            "projection processor list in projection order");

    private static final Mc263AbandonedCampCatalog PINNED = loadPinned();

    private final List<String> replacementStates;
    private final List<ProcessorList> processorLists;
    private final List<String> processorPlacementOrder;
    private final StructureSet structureSet;
    private final List<Pool> pools;
    private final List<Template> templates;
    private final List<Element> configuredFeatureElements;
    private final Map<String, ProcessorList> processorByKey;
    private final Map<String, Pool> poolByKey;
    private final Map<String, Template> templateByKey;
    private final Set<String> configuredFeatureKeys;

    private Mc263AbandonedCampCatalog(
            List<String> replacementStates,
            List<ProcessorList> processorLists,
            List<String> processorPlacementOrder,
            StructureSet structureSet,
            List<Pool> pools,
            List<Template> templates,
            List<Element> configuredFeatureElements,
            Map<String, ProcessorList> processorByKey,
            Map<String, Pool> poolByKey,
            Map<String, Template> templateByKey,
            Set<String> configuredFeatureKeys) {
        this.replacementStates = List.copyOf(replacementStates);
        this.processorLists = List.copyOf(processorLists);
        this.processorPlacementOrder = List.copyOf(processorPlacementOrder);
        this.structureSet = structureSet;
        this.pools = List.copyOf(pools);
        this.templates = List.copyOf(templates);
        this.configuredFeatureElements = List.copyOf(configuredFeatureElements);
        this.processorByKey = immutableOrderedMap(processorByKey);
        this.poolByKey = immutableOrderedMap(poolByKey);
        this.templateByKey = immutableOrderedMap(templateByKey);
        this.configuredFeatureKeys = Collections.unmodifiableSet(
                new LinkedHashSet<>(configuredFeatureKeys));
    }

    public static Mc263AbandonedCampCatalog pinned() {
        return PINNED;
    }

    public List<String> replacementStates() {
        return replacementStates;
    }

    public List<ProcessorList> processorLists() {
        return processorLists;
    }

    public List<String> processorPlacementOrder() {
        return processorPlacementOrder;
    }

    public StructureSet structureSet() {
        return structureSet;
    }

    public List<String> structureKeys() {
        return structureSet.structureKeys();
    }

    public List<Pool> pools() {
        return pools;
    }

    public List<Template> templates() {
        return templates;
    }

    /** The exact 21 authenticated feature-pool elements; no feature execution behavior is attached. */
    public List<Element> configuredFeatureElements() {
        return configuredFeatureElements;
    }

    public ProcessorList requireProcessor(String key) {
        return requireKnown(processorByKey, key, "processor");
    }

    public Pool requirePool(String key) {
        return requireKnown(poolByKey, key, "pool");
    }

    public Template requireTemplate(String key) {
        return requireKnown(templateByKey, key, "template");
    }

    public String requireConfiguredFeature(String key) {
        requireNonBlank(key, "feature");
        if (!configuredFeatureKeys.contains(key)) {
            throw new IllegalArgumentException("unknown Abandoned Camp feature: " + key);
        }
        return key;
    }

    public String requireReplacementState(String state) {
        requireNonBlank(state, "replacement state");
        if (!replacementStates.contains(state)) {
            throw new IllegalArgumentException("unknown Abandoned Camp replacement state: " + state);
        }
        return state;
    }

    public String requireReplacementStateToken(int token) {
        if (token < 0 || token >= replacementStates.size()) {
            throw new IllegalArgumentException("unknown Abandoned Camp replacement-state token: " + token);
        }
        return replacementStates.get(token);
    }

    public CommandOp requireCommandOp(String op) {
        requireNonBlank(op, "command op");
        try {
            return CommandOp.valueOf(op);
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException("unknown Abandoned Camp command op: " + op, error);
        }
    }

    public Rotation requireRotation(String rotation) {
        requireNonBlank(rotation, "rotation");
        try {
            return Rotation.valueOf(rotation);
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException("unknown Abandoned Camp rotation: " + rotation, error);
        }
    }

    /**
     * Validates all externally selected semantic vocabulary as one atomic preflight. The method has
     * no RNG, world-query, or mutation hooks; callers can only proceed after every selector closes.
     */
    public void preflight(PreflightSelection selection) {
        Objects.requireNonNull(selection, "selection");
        requirePool(selection.poolKey());
        requireTemplate(selection.templateKey());
        requireConfiguredFeature(selection.featureKey());
        requireProcessor(selection.processorKey());
        requireReplacementState(selection.replacementState());
        requireCommandOp(selection.commandOp());
        requireReplacementStateToken(selection.replacementStateToken());
    }

    private static Mc263AbandonedCampCatalog loadPinned() {
        try {
            return decodeAndValidate(Mc263AbandonedCampGrammarData.decode());
        } catch (IOException | RuntimeException error) {
            throw new ExceptionInInitializerError(error);
        }
    }

    private static Mc263AbandonedCampCatalog decodeAndValidate(byte[] bytes) throws IOException {
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes))) {
            byte[] magic = new byte[MAGIC.length];
            in.readFully(magic);
            for (int n = 0; n < MAGIC.length; n++) {
                if (magic[n] != MAGIC[n]) throw invalid("semantic grammar magic drift");
            }
            expect(readString(in), CATALOG_SHA256, "catalog SHA-256");
            expect(readString(in), ORACLE_SHA256, "oracle SHA-256");
            expect(readString(in), JIGSAW_FINAL_STATES_SIDECAR_SHA256, "sidecar SHA-256");
            expect(readString(in), STATE_TOKEN_ORDER_SHA256, "state-token-order SHA-256");
            expect(readString(in), COMMAND_IDENTITY_ORDER_SHA256, "command-identity-order SHA-256");
            expect(readString(in), SIDECAR_CONTRACT_SHA256, "sidecar contract SHA-256");
            expect(readString(in), SIDECAR_PIN_SHA256, "sidecar pin SHA-256");
            expect(readString(in), SIDECAR_PRODUCER_ID, "sidecar producer id");
            expect(readString(in), SIDECAR_SOURCE_SHA256, "sidecar source SHA-256");
            expect(readString(in), TEMPLATE_IDENTITY_ORDER_SHA256, "template-identity-order SHA-256");

            int replacementStateCount = readCount(in, 16, "replacement state count");
            ArrayList<String> replacementStates = new ArrayList<>(replacementStateCount);
            for (int n = 0; n < replacementStateCount; n++) replacementStates.add(readString(in));
            if (!replacementStates.equals(EXPECTED_REPLACEMENT_STATES)) {
                throw invalid("replacement-state token table drift");
            }

            int processorCount = readCount(in, 16, "processor-list count");
            ArrayList<ProcessorList> processorLists = new ArrayList<>(processorCount);
            LinkedHashMap<String, ProcessorList> processorByKey = new LinkedHashMap<>();
            for (int n = 0; n < processorCount; n++) {
                String key = readString(in);
                String sha = readString(in);
                int orderCount = readCount(in, 256, "processor order count");
                ArrayList<String> order = new ArrayList<>(orderCount);
                for (int q = 0; q < orderCount; q++) order.add(readString(in));
                ProcessorList processor = new ProcessorList(key, sha, order);
                putUnique(processorByKey, key, processor, "processor");
                processorLists.add(processor);
            }
            if (processorLists.size() != 1
                    || !processorLists.get(0).registryKey().equals(INLINE_PROCESSOR_KEY)
                    || !processorLists.get(0).codecSha256().equals(INLINE_PROCESSOR_SHA256)
                    || !processorLists.get(0).order().isEmpty()) {
                throw invalid("inline-empty processor-list contract drift");
            }
            int placementCount = readCount(in, 16, "processor placement count");
            ArrayList<String> processorPlacementOrder = new ArrayList<>(placementCount);
            for (int n = 0; n < placementCount; n++) processorPlacementOrder.add(readString(in));
            if (!processorPlacementOrder.equals(EXPECTED_PROCESSOR_ORDER)) {
                throw invalid("Jigsaw processor placement order drift");
            }

            StructureSet structureSet = readStructureSet(in);
            if (structureSet.entries().size() != STRUCTURE_COUNT) {
                throw invalid("expected " + STRUCTURE_COUNT + " structures");
            }

            int poolCount = readCount(in, POOL_COUNT, "pool count");
            if (poolCount != POOL_COUNT) throw invalid("expected " + POOL_COUNT + " pools");
            ArrayList<Pool> pools = new ArrayList<>(poolCount);
            LinkedHashMap<String, Pool> poolByKey = new LinkedHashMap<>();
            ArrayList<Element> configuredFeatureElements = new ArrayList<>();
            LinkedHashSet<String> configuredFeatureKeys = new LinkedHashSet<>();
            int templateElementCount = 0;
            int featureElementCount = 0;
            int weightedElementCount = 0;
            for (int n = 0; n < poolCount; n++) {
                Pool pool = readPool(in);
                putUnique(poolByKey, pool.key(), pool, "pool");
                pools.add(pool);
                weightedElementCount += pool.expandedElements().size();
                for (Element element : pool.elements()) {
                    if (element.kind() == ElementKind.TEMPLATE) {
                        templateElementCount++;
                    } else {
                        featureElementCount++;
                        configuredFeatureElements.add(element);
                        if (!configuredFeatureKeys.add(element.featureKey())) {
                            throw invalid("duplicate configured feature key: " + element.featureKey());
                        }
                    }
                }
            }
            if (weightedElementCount != WEIGHTED_ELEMENT_COUNT
                    || templateElementCount != TEMPLATE_ELEMENT_COUNT
                    || featureElementCount != CONFIGURED_FEATURE_ELEMENT_COUNT) {
                throw invalid("pool element cardinality drift");
            }

            int templateCount = readCount(in, TEMPLATE_COUNT, "template count");
            if (templateCount != TEMPLATE_COUNT) throw invalid("expected " + TEMPLATE_COUNT + " templates");
            ArrayList<Template> templates = new ArrayList<>(templateCount);
            LinkedHashMap<String, Template> templateByKey = new LinkedHashMap<>();
            int commandCount = 0;
            int jigsawCount = 0;
            for (int n = 0; n < templateCount; n++) {
                Template template = readTemplate(in, replacementStates);
                putUnique(templateByKey, template.key(), template, "template");
                templates.add(template);
                commandCount += template.commands().size();
                for (Command command : template.commands()) {
                    if (command.op() == CommandOp.JIGSAW) jigsawCount++;
                }
            }
            if (commandCount != COMMAND_COUNT || jigsawCount != JIGSAW_COMMAND_COUNT) {
                throw invalid("semantic command cardinality drift");
            }
            if (in.read() != -1) throw invalid("trailing semantic grammar bytes");

            validateReferences(pools, poolByKey, templateByKey, processorByKey, templates);
            return new Mc263AbandonedCampCatalog(
                    replacementStates,
                    processorLists,
                    processorPlacementOrder,
                    structureSet,
                    pools,
                    templates,
                    configuredFeatureElements,
                    processorByKey,
                    poolByKey,
                    templateByKey,
                    configuredFeatureKeys);
        } catch (EOFException error) {
            throw invalid("truncated semantic grammar", error);
        }
    }

    private static StructureSet readStructureSet(DataInputStream in) throws IOException {
        String key = readString(in);
        String placementType = readString(in);
        Vec locateOffset = readVec(in);
        double frequency = in.readDouble();
        String frequencyReductionMethod = readString(in);
        int salt = in.readInt();
        int separation = in.readInt();
        int spacing = in.readInt();
        String spreadType = readString(in);
        String exclusionZone = readString(in);
        int entryCount = readCount(in, STRUCTURE_COUNT, "structure entry count");
        ArrayList<StructureEntry> entries = new ArrayList<>(entryCount);
        LinkedHashSet<String> keys = new LinkedHashSet<>();
        for (int n = 0; n < entryCount; n++) {
            String structureKey = readString(in);
            int weight = positive(in.readInt(), "structure weight");
            if (!keys.add(structureKey)) throw invalid("duplicate structure key: " + structureKey);
            entries.add(new StructureEntry(structureKey, weight));
        }
        return new StructureSet(
                key, placementType, locateOffset, frequency, frequencyReductionMethod,
                salt, separation, spacing, spreadType, exclusionZone, entries);
    }

    private static Pool readPool(DataInputStream in) throws IOException {
        String key = readString(in);
        String fallback = readString(in);
        int expandedWeight = positive(in.readInt(), "pool expanded weight");
        int elementCount = readCount(in, WEIGHTED_ELEMENT_COUNT, "pool element count");
        ArrayList<Element> elements = new ArrayList<>(elementCount);
        ArrayList<Element> expanded = new ArrayList<>(expandedWeight);
        int weightSum = 0;
        for (int n = 0; n < elementCount; n++) {
            int ordinal = in.readInt();
            if (ordinal != n) throw invalid("non-dense pool element ordinal in " + key);
            ElementKind kind = ElementKind.fromCode(in.readUnsignedByte());
            String projection = readString(in);
            if (!RIGID_PROJECTION.equals(projection)) throw invalid("unknown projection: " + projection);
            int weight = positive(in.readInt(), "pool element weight");
            String codecSha256 = readString(in);
            Element element;
            if (kind == ElementKind.TEMPLATE) {
                String templateKey = readString(in);
                String processorSha256 = readString(in);
                if (!INLINE_PROCESSOR_SHA256.equals(processorSha256)) {
                    throw invalid("template element processor identity drift");
                }
                element = Element.template(
                        ordinal, projection, weight, codecSha256, templateKey, processorSha256);
            } else {
                String featureKey = readString(in);
                element = Element.feature(ordinal, projection, weight, codecSha256, featureKey);
            }
            elements.add(element);
            weightSum += weight;
            for (int q = 0; q < weight; q++) expanded.add(element);
        }
        if (weightSum != expandedWeight) throw invalid("pool expanded-weight drift for " + key);
        return new Pool(key, fallback, expandedWeight, elements, expanded);
    }

    private static Template readTemplate(DataInputStream in, List<String> replacementStates)
            throws IOException {
        String key = readString(in);
        Vec size = readVec(in);
        if (size.x() <= 0 || size.y() <= 0 || size.z() <= 0) throw invalid("invalid template size: " + key);
        int paletteCount = positive(in.readInt(), "palette count");
        int blocksPerPalette = nonNegative(in.readInt(), "blocks per palette");
        int entityCount = nonNegative(in.readInt(), "entity count");
        String assetBoundary = readString(in);
        if (!ASSET_BOUNDARY.equals(assetBoundary)) throw invalid("template asset boundary drift: " + key);
        String boundedGrammarSha256 = readString(in);
        int stateCount = readCount(in, 4_096, "template state count");
        ArrayList<String> stateTable = new ArrayList<>(stateCount);
        for (int n = 0; n < stateCount; n++) stateTable.add(readString(in));
        if (new LinkedHashSet<>(stateTable).size() != stateTable.size()) {
            throw invalid("duplicate template state in " + key);
        }
        int programCount = readCount(in, 16, "palette program count");
        if (programCount != paletteCount || paletteCount != 1) {
            throw invalid("unexpected palette program cardinality in " + key);
        }
        int blockCount = -1;
        ArrayList<Command> commands = new ArrayList<>();
        int expectedOrdinal = 0;
        for (int p = 0; p < programCount; p++) {
            int programBlockCount = nonNegative(in.readInt(), "palette block count");
            int commandCount = readCount(in, COMMAND_COUNT, "palette command count");
            int encodedCommandCount = readCount(in, COMMAND_COUNT, "encoded command count");
            if (commandCount != encodedCommandCount) throw invalid("command-count drift in " + key);
            if (p == 0) blockCount = programBlockCount;
            for (int n = 0; n < encodedCommandCount; n++) {
                CommandOp op = CommandOp.fromCode(in.readUnsignedByte());
                int ordinal = nonNegative(in.readInt(), "command ordinal");
                int state = nonNegative(in.readInt(), "state index");
                if (state >= stateTable.size()) throw invalid("state index outside table in " + key);
                if (ordinal != expectedOrdinal) throw invalid("non-dense command grammar ordinal in " + key);
                Command command;
                if (op == CommandOp.RUN) {
                    Vec start = readVec(in);
                    Vec delta = readVec(in);
                    int count = positive(in.readInt(), "RUN count");
                    if (count > 1 && delta.equals(Vec.ZERO)) throw invalid("non-arithmetic RUN in " + key);
                    command = Command.run(ordinal, state, start, delta, count);
                    expectedOrdinal += count;
                } else if (op == CommandOp.JIGSAW) {
                    Vec position = readVec(in);
                    String joint = readString(in);
                    String name = readString(in);
                    String target = readString(in);
                    String pool = readString(in);
                    int placementPriority = in.readInt();
                    int selectionPriority = in.readInt();
                    int token = in.readUnsignedByte();
                    if (token >= replacementStates.size()) throw invalid("unknown JIGSAW state token in " + key);
                    command = Command.jigsaw(
                            ordinal, state, position, joint, name, target, pool,
                            placementPriority, selectionPriority, token, replacementStates.get(token));
                    expectedOrdinal++;
                } else if (op == CommandOp.LOOT_CONTAINER) {
                    Vec position = readVec(in);
                    String blockEntityType = readString(in);
                    String lootTable = readString(in);
                    command = Command.lootContainer(
                            ordinal, state, position, blockEntityType, lootTable);
                    expectedOrdinal++;
                } else {
                    Vec position = readVec(in);
                    String blockEntityType = readString(in);
                    command = Command.emptyEntity(op, ordinal, state, position, blockEntityType);
                    expectedOrdinal++;
                }
                commands.add(command);
            }
            if (expectedOrdinal != programBlockCount) {
                throw invalid("command grammar does not cover palette blocks in " + key);
            }
        }
        if (blockCount != blocksPerPalette) throw invalid("blocks-per-palette drift in " + key);

        int connectorCount = readCount(in, JIGSAW_COMMAND_COUNT, "connector count");
        ArrayList<Connector> connectors = new ArrayList<>(connectorCount);
        for (int n = 0; n < connectorCount; n++) {
            int ordinal = in.readInt();
            if (ordinal != n) throw invalid("non-dense connector ordinal in " + key);
            connectors.add(new Connector(
                    ordinal,
                    readVec(in),
                    readString(in),
                    readString(in),
                    readString(in),
                    readString(in),
                    readString(in),
                    in.readInt(),
                    in.readInt()));
        }
        validateTemplateConnectorParity(key, stateTable, commands, connectors);
        return new Template(
                key, size, paletteCount, blocksPerPalette, entityCount, assetBoundary,
                boundedGrammarSha256, stateTable, blockCount, commands, connectors);
    }

    private static void validateTemplateConnectorParity(
            String templateKey,
            List<String> stateTable,
            List<Command> commands,
            List<Connector> connectors) throws IOException {
        ArrayList<Command> jigsaws = new ArrayList<>();
        for (Command command : commands) if (command.op() == CommandOp.JIGSAW) jigsaws.add(command);
        if (jigsaws.size() != connectors.size()) throw invalid("JIGSAW connector count drift in " + templateKey);
        for (int n = 0; n < jigsaws.size(); n++) {
            Command command = jigsaws.get(n);
            Connector connector = connectors.get(n);
            if (!command.position().equals(connector.position())
                    || !stateTable.get(command.state()).equals(connector.state())
                    || !command.joint().equals(connector.joint())
                    || !command.name().equals(connector.name())
                    || !command.target().equals(connector.target())
                    || !command.poolKey().equals(connector.poolKey())
                    || command.placementPriority() != connector.placementPriority()
                    || command.selectionPriority() != connector.selectionPriority()) {
                throw invalid("JIGSAW connector semantic drift in " + templateKey + " at " + n);
            }
        }
    }

    private static void validateReferences(
            List<Pool> pools,
            Map<String, Pool> poolByKey,
            Map<String, Template> templateByKey,
            Map<String, ProcessorList> processorByKey,
            List<Template> templates) throws IOException {
        if (!processorByKey.containsKey(INLINE_PROCESSOR_KEY)) throw invalid("missing inline processor list");
        for (Pool pool : pools) {
            if (!EMPTY_POOL_KEY.equals(pool.fallback()) && !poolByKey.containsKey(pool.fallback())) {
                throw invalid("unknown pool fallback: " + pool.fallback());
            }
            for (Element element : pool.elements()) {
                if (element.kind() == ElementKind.TEMPLATE
                        && !templateByKey.containsKey(element.templateKey())) {
                    throw invalid("unknown template element: " + element.templateKey());
                }
            }
        }
        for (Template template : templates) {
            for (Command command : template.commands()) {
                if (command.op() == CommandOp.JIGSAW
                        && !EMPTY_POOL_KEY.equals(command.poolKey())
                        && !poolByKey.containsKey(command.poolKey())) {
                    throw invalid("unknown JIGSAW pool: " + command.poolKey());
                }
            }
        }
    }

    private static Vec readVec(DataInputStream in) throws IOException {
        return new Vec(in.readInt(), in.readInt(), in.readInt());
    }

    private static String readString(DataInputStream in) throws IOException {
        int length = in.readInt();
        if (length < 0 || length > MAX_STRING_BYTES) throw invalid("invalid semantic string length: " + length);
        byte[] data = new byte[length];
        in.readFully(data);
        String value = new String(data, StandardCharsets.UTF_8);
        if (!StandardCharsets.UTF_8.newEncoder().canEncode(value)) throw invalid("invalid UTF-8 semantic string");
        return value;
    }

    private static int readCount(DataInputStream in, int maximum, String label) throws IOException {
        int count = in.readInt();
        if (count < 0 || count > maximum) throw invalid("invalid " + label + ": " + count);
        return count;
    }

    private static int positive(int value, String label) throws IOException {
        if (value <= 0) throw invalid("invalid " + label + ": " + value);
        return value;
    }

    private static int nonNegative(int value, String label) throws IOException {
        if (value < 0) throw invalid("invalid " + label + ": " + value);
        return value;
    }

    private static void expect(String actual, String expected, String label) throws IOException {
        if (!expected.equals(actual)) throw invalid(label + " drift");
    }

    private static IOException invalid(String message) {
        return new IOException("invalid Abandoned Camp semantic catalog: " + message);
    }

    private static IOException invalid(String message, Throwable cause) {
        return new IOException("invalid Abandoned Camp semantic catalog: " + message, cause);
    }

    private static void requireNonBlank(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("malformed Abandoned Camp " + label);
        }
    }

    private static <T> T requireKnown(Map<String, T> map, String key, String kind) {
        requireNonBlank(key, kind);
        T value = map.get(key);
        if (value == null) throw new IllegalArgumentException("unknown Abandoned Camp " + kind + ": " + key);
        return value;
    }

    private static <T> void putUnique(Map<String, T> map, String key, T value, String kind)
            throws IOException {
        requireNonBlank(key, kind);
        if (map.putIfAbsent(key, value) != null) throw invalid("duplicate " + kind + ": " + key);
    }

    private static <K, V> Map<K, V> immutableOrderedMap(Map<K, V> source) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }

    public enum CommandOp {
        RUN,
        JIGSAW,
        LOOT_CONTAINER,
        EMPTY_BLOCK_ENTITY,
        EMPTY_COMPONENT_ENTITY;

        private static CommandOp fromCode(int code) throws IOException {
            if (code < 0 || code >= values().length) throw invalid("unknown command op token: " + code);
            return values()[code];
        }
    }

    public enum ElementKind {
        TEMPLATE,
        FEATURE;

        private static ElementKind fromCode(int code) throws IOException {
            if (code < 0 || code >= values().length) throw invalid("unknown pool element kind token: " + code);
            return values()[code];
        }
    }

    public enum Rotation {
        NONE,
        CLOCKWISE_90,
        CLOCKWISE_180,
        COUNTERCLOCKWISE_90
    }

    public static final class Vec {
        public static final Vec ZERO = new Vec(0, 0, 0);

        private final int x;
        private final int y;
        private final int z;

        public Vec(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        public int x() { return x; }
        public int y() { return y; }
        public int z() { return z; }

        public Vec add(Vec other) {
            Objects.requireNonNull(other, "other");
            return new Vec(Math.addExact(x, other.x), Math.addExact(y, other.y), Math.addExact(z, other.z));
        }

        public Vec scale(int factor) {
            return new Vec(Math.multiplyExact(x, factor), Math.multiplyExact(y, factor), Math.multiplyExact(z, factor));
        }

        /** Structure-template rotation around its local origin; negative coordinates are intentional. */
        public Vec rotateAroundOrigin(Rotation rotation) {
            Objects.requireNonNull(rotation, "rotation");
            return switch (rotation) {
                case NONE -> this;
                case CLOCKWISE_90 -> new Vec(Math.negateExact(z), y, x);
                case CLOCKWISE_180 -> new Vec(Math.negateExact(x), y, Math.negateExact(z));
                case COUNTERCLOCKWISE_90 -> new Vec(z, y, Math.negateExact(x));
            };
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Vec value)) return false;
            return x == value.x && y == value.y && z == value.z;
        }

        @Override
        public int hashCode() {
            return Objects.hash(x, y, z);
        }

        @Override
        public String toString() {
            return "Vec[x=" + x + ", y=" + y + ", z=" + z + "]";
        }
    }

    public static final class ProcessorList {
        private final String registryKey;
        private final String codecSha256;
        private final List<String> order;

        private ProcessorList(String registryKey, String codecSha256, List<String> order) {
            requireNonBlank(registryKey, "processor key");
            requireNonBlank(codecSha256, "processor SHA-256");
            this.registryKey = registryKey;
            this.codecSha256 = codecSha256;
            this.order = List.copyOf(order);
        }

        public String registryKey() { return registryKey; }
        public String codecSha256() { return codecSha256; }
        public List<String> order() { return order; }
    }

    public static final class StructureEntry {
        private final String structureKey;
        private final int weight;

        private StructureEntry(String structureKey, int weight) {
            this.structureKey = structureKey;
            this.weight = weight;
        }

        public String structureKey() { return structureKey; }
        public int weight() { return weight; }
    }

    public static final class StructureSet {
        private final String key;
        private final String placementType;
        private final Vec locateOffset;
        private final double frequency;
        private final String frequencyReductionMethod;
        private final int salt;
        private final int separation;
        private final int spacing;
        private final String spreadType;
        private final String exclusionZone;
        private final List<StructureEntry> entries;
        private final List<String> structureKeys;

        private StructureSet(
                String key,
                String placementType,
                Vec locateOffset,
                double frequency,
                String frequencyReductionMethod,
                int salt,
                int separation,
                int spacing,
                String spreadType,
                String exclusionZone,
                List<StructureEntry> entries) {
            this.key = key;
            this.placementType = placementType;
            this.locateOffset = locateOffset;
            this.frequency = frequency;
            this.frequencyReductionMethod = frequencyReductionMethod;
            this.salt = salt;
            this.separation = separation;
            this.spacing = spacing;
            this.spreadType = spreadType;
            this.exclusionZone = exclusionZone;
            this.entries = List.copyOf(entries);
            ArrayList<String> keys = new ArrayList<>(entries.size());
            for (StructureEntry entry : entries) keys.add(entry.structureKey());
            this.structureKeys = List.copyOf(keys);
        }

        public String key() { return key; }
        public String placementType() { return placementType; }
        public Vec locateOffset() { return locateOffset; }
        public double frequency() { return frequency; }
        public String frequencyReductionMethod() { return frequencyReductionMethod; }
        public int salt() { return salt; }
        public int separation() { return separation; }
        public int spacing() { return spacing; }
        public String spreadType() { return spreadType; }
        public String exclusionZone() { return exclusionZone; }
        public List<StructureEntry> entries() { return entries; }
        public List<String> structureKeys() { return structureKeys; }
    }

    public static final class Element {
        private final int ordinal;
        private final ElementKind kind;
        private final String projection;
        private final int weight;
        private final String codecSha256;
        private final String templateKey;
        private final String processorSha256;
        private final String featureKey;

        private Element(
                int ordinal,
                ElementKind kind,
                String projection,
                int weight,
                String codecSha256,
                String templateKey,
                String processorSha256,
                String featureKey) {
            this.ordinal = ordinal;
            this.kind = kind;
            this.projection = projection;
            this.weight = weight;
            this.codecSha256 = codecSha256;
            this.templateKey = templateKey;
            this.processorSha256 = processorSha256;
            this.featureKey = featureKey;
        }

        private static Element template(
                int ordinal, String projection, int weight, String codecSha256,
                String templateKey, String processorSha256) {
            return new Element(
                    ordinal, ElementKind.TEMPLATE, projection, weight, codecSha256,
                    templateKey, processorSha256, null);
        }

        private static Element feature(
                int ordinal, String projection, int weight, String codecSha256, String featureKey) {
            return new Element(
                    ordinal, ElementKind.FEATURE, projection, weight, codecSha256,
                    null, null, featureKey);
        }

        public int ordinal() { return ordinal; }
        public ElementKind kind() { return kind; }
        public String projection() { return projection; }
        public int weight() { return weight; }
        public String codecSha256() { return codecSha256; }
        public String templateKey() { return templateKey; }
        public String processorSha256() { return processorSha256; }
        public String featureKey() { return featureKey; }
        public String elementType() {
            return kind == ElementKind.TEMPLATE ? TEMPLATE_ELEMENT_TYPE : FEATURE_ELEMENT_TYPE;
        }
        public String processorListKey() {
            return kind == ElementKind.TEMPLATE ? INLINE_PROCESSOR_KEY : null;
        }
        public List<String> processorOrder() {
            return kind == ElementKind.TEMPLATE ? List.of() : List.of();
        }
    }

    public static final class Pool {
        private final String key;
        private final String fallback;
        private final int expandedWeight;
        private final List<Element> elements;
        private final List<Element> expandedElements;

        private Pool(
                String key,
                String fallback,
                int expandedWeight,
                List<Element> elements,
                List<Element> expandedElements) {
            this.key = key;
            this.fallback = fallback;
            this.expandedWeight = expandedWeight;
            this.elements = List.copyOf(elements);
            this.expandedElements = List.copyOf(expandedElements);
        }

        public String key() { return key; }
        public String fallback() { return fallback; }
        public int expandedWeight() { return expandedWeight; }
        public List<Element> elements() { return elements; }
        public List<Element> expandedElements() { return expandedElements; }
    }

    public static final class Command {
        private final CommandOp op;
        private final int ordinal;
        private final int state;
        private final Vec start;
        private final Vec delta;
        private final int count;
        private final Vec position;
        private final String joint;
        private final String name;
        private final String target;
        private final String poolKey;
        private final int placementPriority;
        private final int selectionPriority;
        private final String blockEntityType;
        private final String lootTable;
        private final int replacementStateToken;
        private final String replacementState;

        private Command(
                CommandOp op,
                int ordinal,
                int state,
                Vec start,
                Vec delta,
                int count,
                Vec position,
                String joint,
                String name,
                String target,
                String poolKey,
                int placementPriority,
                int selectionPriority,
                String blockEntityType,
                String lootTable,
                int replacementStateToken,
                String replacementState) {
            this.op = op;
            this.ordinal = ordinal;
            this.state = state;
            this.start = start;
            this.delta = delta;
            this.count = count;
            this.position = position;
            this.joint = joint;
            this.name = name;
            this.target = target;
            this.poolKey = poolKey;
            this.placementPriority = placementPriority;
            this.selectionPriority = selectionPriority;
            this.blockEntityType = blockEntityType;
            this.lootTable = lootTable;
            this.replacementStateToken = replacementStateToken;
            this.replacementState = replacementState;
        }

        private static Command run(int ordinal, int state, Vec start, Vec delta, int count) {
            return new Command(
                    CommandOp.RUN, ordinal, state, start, delta, count, null,
                    null, null, null, null, 0, 0, null, null, -1, null);
        }

        private static Command jigsaw(
                int ordinal, int state, Vec position, String joint, String name, String target,
                String poolKey, int placementPriority, int selectionPriority,
                int replacementStateToken, String replacementState) {
            return new Command(
                    CommandOp.JIGSAW, ordinal, state, null, null, 1, position,
                    joint, name, target, poolKey, placementPriority, selectionPriority,
                    null, null, replacementStateToken, replacementState);
        }

        private static Command lootContainer(
                int ordinal, int state, Vec position, String blockEntityType, String lootTable) {
            return new Command(
                    CommandOp.LOOT_CONTAINER, ordinal, state, null, null, 1, position,
                    null, null, null, null, 0, 0, blockEntityType, lootTable, -1, null);
        }

        private static Command emptyEntity(
                CommandOp op, int ordinal, int state, Vec position, String blockEntityType)
                throws IOException {
            if (op != CommandOp.EMPTY_BLOCK_ENTITY && op != CommandOp.EMPTY_COMPONENT_ENTITY) {
                throw invalid("invalid empty-entity op: " + op);
            }
            return new Command(
                    op, ordinal, state, null, null, 1, position,
                    null, null, null, null, 0, 0, blockEntityType, null, -1, null);
        }

        public CommandOp op() { return op; }
        public int ordinal() { return ordinal; }
        public int state() { return state; }
        public Vec start() { return start; }
        public Vec delta() { return delta; }
        public int count() { return count; }
        public Vec position() { return position; }
        public String joint() { return joint; }
        public String name() { return name; }
        public String target() { return target; }
        public String poolKey() { return poolKey; }
        public int placementPriority() { return placementPriority; }
        public int selectionPriority() { return selectionPriority; }
        public String blockEntityType() { return blockEntityType; }
        public String lootTable() { return lootTable; }
        public int replacementStateToken() { return replacementStateToken; }
        public String replacementState() { return replacementState; }

        /** Computes a command occurrence without materializing an expanded coordinate array. */
        public Vec localPositionAt(int occurrence) {
            if (op == CommandOp.RUN) {
                if (occurrence < 0 || occurrence >= count) {
                    throw new IllegalArgumentException("RUN occurrence outside [0," + count + "): " + occurrence);
                }
                return start.add(delta.scale(occurrence));
            }
            if (occurrence != 0) throw new IllegalArgumentException("semantic singleton occurrence must be zero");
            return position;
        }

        public Vec worldPositionAt(int occurrence, Rotation rotation, Vec origin) {
            Objects.requireNonNull(origin, "origin");
            return origin.add(localPositionAt(occurrence).rotateAroundOrigin(rotation));
        }
    }

    public static final class Connector {
        private final int ordinal;
        private final Vec position;
        private final String state;
        private final String joint;
        private final String name;
        private final String target;
        private final String poolKey;
        private final int placementPriority;
        private final int selectionPriority;

        private Connector(
                int ordinal,
                Vec position,
                String state,
                String joint,
                String name,
                String target,
                String poolKey,
                int placementPriority,
                int selectionPriority) {
            this.ordinal = ordinal;
            this.position = position;
            this.state = state;
            this.joint = joint;
            this.name = name;
            this.target = target;
            this.poolKey = poolKey;
            this.placementPriority = placementPriority;
            this.selectionPriority = selectionPriority;
        }

        public int ordinal() { return ordinal; }
        public Vec position() { return position; }
        public String state() { return state; }
        public String joint() { return joint; }
        public String name() { return name; }
        public String target() { return target; }
        public String poolKey() { return poolKey; }
        public int placementPriority() { return placementPriority; }
        public int selectionPriority() { return selectionPriority; }
    }

    public static final class Template {
        private final String key;
        private final Vec size;
        private final int paletteCount;
        private final int blocksPerPalette;
        private final int entityCount;
        private final String assetBoundary;
        private final String boundedGrammarSha256;
        private final List<String> stateTable;
        private final int blockCount;
        private final List<Command> commands;
        private final List<Connector> connectors;

        private Template(
                String key,
                Vec size,
                int paletteCount,
                int blocksPerPalette,
                int entityCount,
                String assetBoundary,
                String boundedGrammarSha256,
                List<String> stateTable,
                int blockCount,
                List<Command> commands,
                List<Connector> connectors) {
            this.key = key;
            this.size = size;
            this.paletteCount = paletteCount;
            this.blocksPerPalette = blocksPerPalette;
            this.entityCount = entityCount;
            this.assetBoundary = assetBoundary;
            this.boundedGrammarSha256 = boundedGrammarSha256;
            this.stateTable = List.copyOf(stateTable);
            this.blockCount = blockCount;
            this.commands = List.copyOf(commands);
            this.connectors = List.copyOf(connectors);
        }

        public String key() { return key; }
        public Vec size() { return size; }
        public int paletteCount() { return paletteCount; }
        public int blocksPerPalette() { return blocksPerPalette; }
        public int entityCount() { return entityCount; }
        public String assetBoundary() { return assetBoundary; }
        public String boundedGrammarSha256() { return boundedGrammarSha256; }
        public List<String> stateTable() { return stateTable; }
        public int blockCount() { return blockCount; }
        public List<Command> commands() { return commands; }
        public List<Connector> connectors() { return connectors; }

        public String requireState(int index) {
            if (index < 0 || index >= stateTable.size()) {
                throw new IllegalArgumentException("state index outside template " + key + ": " + index);
            }
            return stateTable.get(index);
        }

        public int requireState(String state) {
            requireNonBlank(state, "template state");
            int index = stateTable.indexOf(state);
            if (index < 0) throw new IllegalArgumentException("unknown state for template " + key + ": " + state);
            return index;
        }
    }

    public static final class PreflightSelection {
        private final String poolKey;
        private final String templateKey;
        private final String featureKey;
        private final String processorKey;
        private final String replacementState;
        private final String commandOp;
        private final int replacementStateToken;

        public PreflightSelection(
                String poolKey,
                String templateKey,
                String featureKey,
                String processorKey,
                String replacementState,
                String commandOp,
                int replacementStateToken) {
            this.poolKey = poolKey;
            this.templateKey = templateKey;
            this.featureKey = featureKey;
            this.processorKey = processorKey;
            this.replacementState = replacementState;
            this.commandOp = commandOp;
            this.replacementStateToken = replacementStateToken;
        }

        public String poolKey() { return poolKey; }
        public String templateKey() { return templateKey; }
        public String featureKey() { return featureKey; }
        public String processorKey() { return processorKey; }
        public String replacementState() { return replacementState; }
        public String commandOp() { return commandOp; }
        public int replacementStateToken() { return replacementStateToken; }
    }
}
