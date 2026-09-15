package com.gameexpert.terrain.mc.structure;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Strict decoder for the pinned Trail Ruins semantic grammar. The grammar contains arithmetic
 * runs and typed connector markers only; it never contains Mojang template NBT or coordinate
 * replay receipts.
 */
public final class Mc263TrailRuinsCatalog {
    public static final String CATALOG_SHA256 =
            "650cb33622255e0e7fa71b6732c93255c1e82ee021c4a690c779f9d32c843042";
    public static final String STRUCTURE_KEY = "minecraft:trail_ruins";
    public static final String START_POOL = "minecraft:trail_ruins/tower";
    public static final String EMPTY_POOL = "minecraft:empty";
    public static final String COMMON_ARCH = "minecraft:archaeology/trail_ruins_common";
    public static final String RARE_ARCH = "minecraft:archaeology/trail_ruins_rare";
    public static final int MAX_DEPTH = 7;
    public static final int START_HEIGHT = -15;
    public static final int MAX_DISTANCE = 80;

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final List<String> POOL_KEYS = List.of(
            "minecraft:trail_ruins/tower",
            "minecraft:trail_ruins/tower/tower_top",
            "minecraft:trail_ruins/tower/additions",
            "minecraft:trail_ruins/roads",
            "minecraft:trail_ruins/buildings",
            "minecraft:trail_ruins/buildings/grouped",
            "minecraft:trail_ruins/decor");
    private static final Map<String, Integer> POOL_SIZES = Map.of(
            "minecraft:trail_ruins/tower", 5,
            "minecraft:trail_ruins/tower/tower_top", 5,
            "minecraft:trail_ruins/tower/additions", 25,
            "minecraft:trail_ruins/roads", 7,
            "minecraft:trail_ruins/buildings", 15,
            "minecraft:trail_ruins/buildings/grouped", 20,
            "minecraft:trail_ruins/decor", 7);
    private static final Set<String> PROCESSORS = Set.of(
            "minecraft:trail_ruins_houses_archaeology",
            "minecraft:trail_ruins_roads_archaeology",
            "minecraft:trail_ruins_tower_top_archaeology");

    private final List<Pool> pools;
    private final Map<String, Pool> poolsByKey;
    private final Map<String, Template> templatesByKey;

    private Mc263TrailRuinsCatalog(List<Pool> pools, Map<String, Pool> poolsByKey,
            Map<String, Template> templatesByKey) {
        this.pools = List.copyOf(pools);
        this.poolsByKey = Map.copyOf(poolsByKey);
        this.templatesByKey = Map.copyOf(templatesByKey);
    }

    /**
     * The pinned catalog, decoded once per class loader.
     *
     * <p>AGENTS rule 10l: decoding it costs a base64 pass, a gunzip of the whole pinned grammar
     * and a full JSON parse with its closure validation, and the FEATURES seam asks for it once
     * per source chunk of every target. The catalog is a pure function of the pinned bytes and is
     * deeply immutable ({@link #Mc263TrailRuinsCatalog} copies every collection and every record
     * it holds is a value), so one decode serves every caller byte-for-byte. {@link #decode} stays
     * public for callers that hold their own bytes.</p>
     */
    private static final class PinnedHolder {
        private static final Mc263TrailRuinsCatalog CATALOG =
                decode(Mc263TrailRuinsGrammarData.json());

        private PinnedHolder() { }
    }

    public static Mc263TrailRuinsCatalog pinned() {
        return PinnedHolder.CATALOG;
    }

    /** Shared dormant jigsaw descriptor consumed by the generic exact graph executor. */
    public static Mc263JigsawStructureBoundary.Grammar grammar() {
        Mc263TrailRuinsCatalog catalog = pinned();
        List<Mc263JigsawStructureBoundary.PoolSpec> pools = new ArrayList<>();
        for (Pool pool : catalog.pools) {
            List<Mc263JigsawStructureBoundary.ElementSpec> elements = new ArrayList<>();
            for (Element element : pool.elements()) {
                Template template = catalog.template(element.template());
                List<Mc263JigsawStructureBoundary.ConnectorSpec> connectors = new ArrayList<>();
                for (Connector connector : template.connectors()) {
                    try {
                        Orientation orientation = orientation(connector.state());
                        connectors.add(new Mc263JigsawStructureBoundary.ConnectorSpec(
                                connector.position().x(), connector.position().y(),
                                connector.position().z(),
                                Mc263JigsawStructureBoundary.Direction.valueOf(
                                        orientation.front().name()),
                                Mc263JigsawStructureBoundary.Direction.valueOf(
                                        orientation.top().name()),
                                Mc263JigsawStructureBoundary.Joint.valueOf(connector.joint()),
                                connector.name(), connector.target(), connector.pool(),
                                connector.placementPriority(), connector.selectionPriority()));
                    } catch (IOException error) {
                        throw new IllegalStateException("preflighted Trail connector drift", error);
                    }
                }
                Vec size = template.size();
                elements.add(Mc263JigsawStructureBoundary.ElementSpec.single(template.key(),
                        element.weight(), Mc263JigsawStructureBoundary.Projection.RIGID,
                        element.processor(), new Mc263JigsawStructureBoundary.Bounds(0, 0, 0,
                                size.x() - 1, size.y() - 1, size.z() - 1), connectors));
            }
            pools.add(new Mc263JigsawStructureBoundary.PoolSpec(pool.key(), pool.fallback(),
                    elements));
        }
        return new Mc263JigsawStructureBoundary.Grammar(STRUCTURE_KEY, pools);
    }

    public static List<Template> templates() {
        return List.copyOf(pinned().templatesByKey.values());
    }

    public static Template requireTemplate(String key) { return pinned().template(key); }

    public static String requireProcessor(String key) {
        if (!PROCESSORS.contains(key)) {
            throw new IllegalArgumentException("unknown Trail Ruins processor: " + key);
        }
        return key;
    }

    public static Mc263TrailRuinsCatalog decode(byte[] catalogJson) {
        if (catalogJson == null || catalogJson.length == 0) {
            throw new IllegalArgumentException("Trail Ruins grammar bytes are required");
        }
        try {
            JsonNode root = MAPPER.readTree(catalogJson);
            requireObjectKeys(root, Set.of("structureSet", "pools", "templates",
                    "processorLists", "archaeologyLootTables", "processorPlacementOrder",
                    "catalogSha256"), "catalog");
            validateStructureSet(required(root, "structureSet", "catalog"));
            validateProcessors(required(root, "processorLists", "catalog"));
            validateArchaeology(required(root, "archaeologyLootTables", "catalog"));
            validatePlacementOrder(required(root, "processorPlacementOrder", "catalog"));

            Map<String, Template> templates = parseTemplates(
                    required(root, "templates", "catalog"));
            List<Pool> pools = parsePools(required(root, "pools", "catalog"), templates);
            Map<String, Pool> byPool = new LinkedHashMap<>();
            for (Pool pool : pools) byPool.put(pool.key(), pool);
            validateConnectorPools(templates, byPool);
            return new Mc263TrailRuinsCatalog(pools, byPool, templates);
        } catch (IOException error) {
            throw new IllegalArgumentException("invalid Trail Ruins semantic grammar", error);
        }
    }

    public List<Pool> pools() { return pools; }

    public Pool pool(String key) {
        Pool value = poolsByKey.get(key);
        if (value == null) throw new IllegalArgumentException("unknown Trail Ruins pool: " + key);
        return value;
    }

    public Template template(String key) {
        Template value = templatesByKey.get(key);
        if (value == null) {
            throw new IllegalArgumentException("unknown Trail Ruins template: " + key);
        }
        return value;
    }

    public int templateCount() { return templatesByKey.size(); }

    private static void validateStructureSet(JsonNode node) throws IOException {
        if (!"minecraft:trail_ruins".equals(text(node, "key"))
                || integer(node, "spacing") != 34 || integer(node, "separation") != 8
                || integer(node, "salt") != 83469867
                || !"LINEAR".equals(text(node, "spreadType"))
                || !"minecraft:random_spread".equals(text(node, "placementType"))) {
            throw malformed("Trail Ruins structure-set identity drift");
        }
        JsonNode entries = required(node, "entries", "structureSet");
        if (!entries.isArray() || entries.size() != 1
                || !STRUCTURE_KEY.equals(text(entries.get(0), "structureKey"))
                || integer(entries.get(0), "weight") != 1) {
            throw malformed("Trail Ruins structure-set membership drift");
        }
    }

    private static void validateProcessors(JsonNode node) throws IOException {
        if (!node.isArray() || node.size() != 3) throw malformed("processor-list drift");
        Set<String> keys = new LinkedHashSet<>();
        for (JsonNode row : node) {
            String key = text(row, "key");
            if (!PROCESSORS.contains(key) || !keys.add(key)) {
                throw malformed("unknown or duplicate Trail Ruins processor: " + key);
            }
            JsonNode order = required(row, "order", key);
            int expected = key.endsWith("houses_archaeology") ? 3
                    : key.endsWith("roads_archaeology") ? 2 : 1;
            if (!order.isArray() || order.size() != expected) {
                throw malformed("processor order drift: " + key);
            }
        }
    }

    private static void validateArchaeology(JsonNode node) throws IOException {
        if (!node.isArray() || node.size() != 2) throw malformed("archaeology table drift");
        Set<String> keys = new LinkedHashSet<>();
        for (JsonNode row : node) keys.add(text(row, "key"));
        if (!keys.equals(Set.of(COMMON_ARCH, RARE_ARCH))) {
            throw malformed("archaeology table identity drift");
        }
    }

    private static void validatePlacementOrder(JsonNode node) throws IOException {
        List<String> expected = List.of("BlockIgnoreProcessor.STRUCTURE_BLOCK",
                "JigsawReplacementProcessor.INSTANCE",
                "element processor list in codec order",
                "projection processor list in projection order");
        if (!node.isArray() || node.size() != expected.size()) {
            throw malformed("processor placement-order drift");
        }
        for (int index = 0; index < expected.size(); index++) {
            if (!expected.get(index).equals(node.get(index).asString())) {
                throw malformed("processor placement-order drift");
            }
        }
    }

    private static Map<String, Template> parseTemplates(JsonNode node) throws IOException {
        if (!node.isArray() || node.size() != 84) throw malformed("template count drift");
        Map<String, List<String>> jigsawFinalStates = parseJigsawFinalStates();
        Map<String, Template> result = new LinkedHashMap<>();
        for (JsonNode row : node) {
            String key = text(row, "template");
            requireResourceKey(key, "template");
            Vec size = vector(required(row, "size", key), key + ".size");
            if (size.x() <= 0 || size.y() <= 0 || size.z() <= 0) {
                throw malformed("non-positive template size: " + key);
            }
            List<String> states = strings(required(row, "stateTable", key), key + ".states");
            JsonNode palettes = required(row, "palettePrograms", key);
            if (!palettes.isArray() || palettes.size() != 1) {
                throw malformed("Trail Ruins template must have one palette: " + key);
            }
            List<String> finalStates = jigsawFinalStates.remove(key);
            if (finalStates == null) throw malformed("missing jigsaw final-state grammar: " + key);
            List<Command> commands = parseCommands(palettes.get(0), states.size(), key,
                    finalStates);
            int expectedBlocks = integer(row, "blocksPerPalette");
            int expanded = commands.stream().mapToInt(Command::blockCount).sum();
            if (expanded != expectedBlocks) {
                throw malformed("template block grammar cardinality drift: " + key);
            }
            List<Connector> connectors = parseConnectors(
                    required(row, "connectorsInTemplateOrder", key), key);
            long semanticConnectors = commands.stream()
                    .filter(command -> command.kind() == CommandKind.JIGSAW).count();
            if (semanticConnectors != connectors.size()) {
                throw malformed("template connector grammar drift: " + key);
            }
            Template template = new Template(key, size, states, commands, connectors,
                    text(row, "boundedGrammarSha256"));
            if (result.put(key, template) != null) {
                throw malformed("duplicate Trail Ruins template: " + key);
            }
        }
        if (!jigsawFinalStates.isEmpty()) {
            throw malformed("orphaned jigsaw final-state grammar: " + jigsawFinalStates.keySet());
        }
        return result;
    }

    private static List<Command> parseCommands(JsonNode palette, int stateCount, String owner,
            List<String> jigsawFinalStates) throws IOException {
        JsonNode rows = required(palette, "commands", owner);
        if (!rows.isArray()) throw malformed("command grammar is not an array: " + owner);
        List<Command> result = new ArrayList<>(rows.size());
        int ordinal = 0;
        int jigsawOrdinal = 0;
        for (JsonNode row : rows) {
            CommandKind kind;
            try { kind = CommandKind.valueOf(text(row, "op")); }
            catch (IllegalArgumentException error) {
                throw malformed("unknown Trail Ruins command: " + row.path("op"));
            }
            int commandOrdinal = integer(row, "ordinal");
            if (commandOrdinal != ordinal) throw malformed("command ordinal drift: " + owner);
            int state = integer(row, "state");
            if (state < 0 || state >= stateCount) throw malformed("state index drift: " + owner);
            Vec start = kind == CommandKind.RUN
                    ? vector(required(row, "start", owner), owner + ".start")
                    : vector(required(row, "position", owner), owner + ".position");
            Vec delta = kind == CommandKind.RUN
                    ? vector(required(row, "delta", owner), owner + ".delta") : new Vec(0, 0, 0);
            int count = kind == CommandKind.RUN ? integer(row, "count") : 1;
            if (count <= 0 || count > 65_536) throw malformed("command count drift: " + owner);
            Connector connector = kind == CommandKind.JIGSAW
                    ? connector(row, null) : null;
            String blockEntityType = kind == CommandKind.EMPTY_BLOCK_ENTITY
                    ? text(row, "blockEntityType") : "";
            String finalState = kind == CommandKind.JIGSAW
                    ? jigsawFinalStates.get(jigsawOrdinal++) : "";
            result.add(new Command(kind, state, start, delta, count, connector,
                    blockEntityType, finalState));
            ordinal += count;
        }
        if (jigsawOrdinal != jigsawFinalStates.size()) {
            throw malformed("jigsaw final-state cardinality drift: " + owner);
        }
        if (ordinal != integer(palette, "blockCount")) {
            throw malformed("palette block count drift: " + owner);
        }
        return List.copyOf(result);
    }

    private static Map<String, List<String>> parseJigsawFinalStates() throws IOException {
        Set<String> allowed = Set.of("minecraft:air", "minecraft:cobblestone",
                "minecraft:dirt", "minecraft:gravel", "minecraft:mud_bricks",
                "minecraft:red_terracotta");
        String grammar = new String(Mc263TrailRuinsGrammarData.jigsawFinalStates(),
                StandardCharsets.UTF_8);
        Map<String, List<String>> result = new LinkedHashMap<>();
        for (String row : grammar.split("\\n")) {
            if (row.isEmpty()) continue;
            int separator = row.indexOf('|');
            if (separator <= 0 || separator == row.length() - 1) {
                throw malformed("malformed jigsaw final-state row");
            }
            String key = row.substring(0, separator);
            List<String> states = List.of(row.substring(separator + 1).split(",", -1));
            if (!states.stream().allMatch(allowed::contains) || result.put(key, states) != null) {
                throw malformed("unknown or duplicate jigsaw final-state grammar: " + key);
            }
        }
        if (result.size() != 84) throw malformed("jigsaw final-state template count drift");
        return result;
    }

    private static List<Connector> parseConnectors(JsonNode node, String owner)
            throws IOException {
        if (!node.isArray()) throw malformed("connector list drift: " + owner);
        List<Connector> result = new ArrayList<>();
        for (int index = 0; index < node.size(); index++) {
            JsonNode row = node.get(index);
            if (integer(row, "ordinal") != index) {
                throw malformed("connector ordinal drift: " + owner);
            }
            result.add(connector(row, null));
        }
        return List.copyOf(result);
    }

    private static Connector connector(JsonNode row, String stateOverride) throws IOException {
        JsonNode stateNode = required(row, "state", "connector");
        String state = stateOverride;
        if (state == null) {
            if (!stateNode.isString()) {
                // Semantic JIGSAW commands carry the state-table index. Their connector state is
                // not used by graph planning; the separately validated connector receipt is.
                state = "minecraft:jigsaw[orientation=north_up]";
            } else state = stateNode.asString();
        }
        return new Connector(vector(required(row, "position", "connector"), "connector"),
                state, text(row, "joint"), text(row, "name"),
                text(row, "target"), text(row, "pool"), integer(row, "placementPriority"),
                integer(row, "selectionPriority"));
    }

    private static List<Pool> parsePools(JsonNode node, Map<String, Template> templates)
            throws IOException {
        if (!node.isArray() || node.size() != POOL_KEYS.size()) {
            throw malformed("Trail Ruins pool count drift");
        }
        List<Pool> pools = new ArrayList<>();
        Set<String> selectedTemplates = new LinkedHashSet<>();
        for (int poolIndex = 0; poolIndex < node.size(); poolIndex++) {
            JsonNode row = node.get(poolIndex);
            String key = text(row, "key");
            if (!POOL_KEYS.get(poolIndex).equals(key)
                    || !EMPTY_POOL.equals(text(row, "fallback"))) {
                throw malformed("Trail Ruins pool order/fallback drift: " + key);
            }
            JsonNode elements = required(row, "elements", key);
            int expected = POOL_SIZES.get(key);
            if (!elements.isArray() || elements.size() != expected
                    || integer(row, "expandedWeight") != expected) {
                throw malformed("Trail Ruins pool cardinality drift: " + key);
            }
            List<Element> parsed = new ArrayList<>();
            for (int ordinal = 0; ordinal < elements.size(); ordinal++) {
                JsonNode element = elements.get(ordinal);
                String template = text(element, "template");
                String processor = text(element, "processorList");
                if (integer(element, "ordinal") != ordinal
                        || integer(element, "weight") != 1
                        || !"rigid".equals(text(element, "projection"))
                        || !"minecraft:single_pool_element".equals(text(element, "type"))
                        || !PROCESSORS.contains(processor) || !templates.containsKey(template)) {
                    throw malformed("unknown Trail Ruins pool element: " + key + "#" + ordinal);
                }
                if (!selectedTemplates.add(template)) {
                    throw malformed("template appears in multiple pools: " + template);
                }
                parsed.add(new Element(template, processor, 1));
            }
            pools.add(new Pool(key, EMPTY_POOL, parsed));
        }
        if (!selectedTemplates.equals(templates.keySet())) {
            throw malformed("pool/template closure drift");
        }
        return List.copyOf(pools);
    }

    private static void validateConnectorPools(Map<String, Template> templates,
            Map<String, Pool> pools) throws IOException {
        for (Template template : templates.values()) {
            for (Connector connector : template.connectors()) {
                requireResourceKey(connector.name(), "connector name");
                requireResourceKey(connector.target(), "connector target");
                requireResourceKey(connector.pool(), "connector pool");
                if (!EMPTY_POOL.equals(connector.pool()) && !pools.containsKey(connector.pool())) {
                    throw malformed("connector references unknown pool: " + connector.pool());
                }
                orientation(connector.state());
                if (!Set.of("ROLLABLE", "ALIGNED").contains(connector.joint())) {
                    throw malformed("unknown connector joint: " + connector.joint());
                }
            }
        }
    }

    static Orientation orientation(String state) throws IOException {
        int start = state.indexOf("orientation=");
        int end = state.indexOf(']', start);
        if (!state.startsWith("minecraft:jigsaw[") || start < 0 || end < 0) {
            throw malformed("unknown jigsaw state: " + state);
        }
        String[] parts = state.substring(start + 12, end).split("_");
        if (parts.length != 2) throw malformed("unknown jigsaw orientation: " + state);
        try { return new Orientation(Direction.valueOf(parts[0].toUpperCase()),
                Direction.valueOf(parts[1].toUpperCase())); }
        catch (IllegalArgumentException error) { throw malformed("unknown direction: " + state); }
    }

    private static void requireResourceKey(String value, String label) throws IOException {
        if (value == null || !value.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) {
            throw malformed(label + " is not a resource key: " + value);
        }
    }

    private static JsonNode required(JsonNode node, String field, String owner)
            throws IOException {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) throw malformed(owner + " lacks " + field);
        return value;
    }

    private static String text(JsonNode node, String field) throws IOException {
        JsonNode value = required(node, field, "node");
        if (!value.isString()) throw malformed(field + " is not text");
        return value.asString();
    }

    private static int integer(JsonNode node, String field) throws IOException {
        JsonNode value = required(node, field, "node");
        if (!value.isInt()) throw malformed(field + " is not int");
        return value.asInt();
    }

    private static Vec vector(JsonNode node, String owner) throws IOException {
        if (!node.isArray() || node.size() != 3) throw malformed(owner + " is not vec3");
        return new Vec(node.get(0).asInt(), node.get(1).asInt(), node.get(2).asInt());
    }

    private static List<String> strings(JsonNode node, String owner) throws IOException {
        if (!node.isArray() || node.isEmpty()) throw malformed(owner + " is not string array");
        List<String> result = new ArrayList<>();
        for (JsonNode value : node) {
            if (!value.isString()) throw malformed(owner + " contains non-string");
            result.add(value.asString());
        }
        return List.copyOf(result);
    }

    private static void requireObjectKeys(JsonNode node, Set<String> keys, String owner)
            throws IOException {
        if (!node.isObject()) throw malformed(owner + " is not an object");
        Set<String> actual = new LinkedHashSet<>();
        actual.addAll(node.propertyNames());
        if (!actual.equals(keys)) throw malformed(owner + " schema drift: " + actual);
    }

    private static IOException malformed(String message) { return new IOException(message); }

    public enum CommandKind { RUN, JIGSAW, EMPTY_BLOCK_ENTITY }
    public enum Rotation { NONE, CLOCKWISE_90, CLOCKWISE_180, COUNTERCLOCKWISE_90 }
    public enum Direction { DOWN, UP, NORTH, SOUTH, WEST, EAST }

    public static final class Orientation {
        private final Direction front;
        private final Direction top;
        public Orientation(Direction front, Direction top) { this.front = front; this.top = top; }
        public Direction front() { return front; }
        public Direction top() { return top; }
    }

    public static final class Vec {
        private final int x, y, z;
        public Vec(int x, int y, int z) { this.x = x; this.y = y; this.z = z; }
        public int x() { return x; }
        public int y() { return y; }
        public int z() { return z; }
        public Vec add(Vec other) { return new Vec(x + other.x, y + other.y, z + other.z); }
        public Vec subtract(Vec other) { return new Vec(x - other.x, y - other.y, z - other.z); }
        @Override public boolean equals(Object value) {
            return value instanceof Vec other && x == other.x && y == other.y && z == other.z;
        }
        @Override public int hashCode() { return ((x * 31) + y) * 31 + z; }
        @Override public String toString() { return "[" + x + "," + y + "," + z + "]"; }
    }

    public static final class Connector {
        private final Vec position;
        private final String state, joint, name, target, pool;
        private final int placementPriority, selectionPriority;
        public Connector(Vec position, String state, String joint, String name, String target,
                String pool, int placementPriority, int selectionPriority) {
            this.position = position; this.state = state; this.joint = joint; this.name = name;
            this.target = target; this.pool = pool; this.placementPriority = placementPriority;
            this.selectionPriority = selectionPriority;
        }
        public Vec position() { return position; }
        public String state() { return state; }
        public String joint() { return joint; }
        public String name() { return name; }
        public String target() { return target; }
        public String pool() { return pool; }
        public int placementPriority() { return placementPriority; }
        public int selectionPriority() { return selectionPriority; }
    }

    public static final class Command {
        private final CommandKind kind;
        private final int state, count;
        private final Vec start, delta;
        private final Connector connector;
        private final String blockEntityType, finalState;
        public Command(CommandKind kind, int state, Vec start, Vec delta, int count,
                Connector connector, String blockEntityType, String finalState) {
            this.kind = kind; this.state = state; this.start = start; this.delta = delta;
            this.count = count; this.connector = connector; this.blockEntityType = blockEntityType;
            this.finalState = finalState;
        }
        public CommandKind kind() { return kind; }
        public int state() { return state; }
        public Vec start() { return start; }
        public Vec delta() { return delta; }
        public int count() { return count; }
        public Connector connector() { return connector; }
        public String blockEntityType() { return blockEntityType; }
        public String finalState() { return finalState; }
        public int blockCount() { return count; }
    }

    public static final class Template {
        private final String key, grammarSha256;
        private final Vec size;
        private final List<String> states;
        private final List<Command> commands;
        private final List<Connector> connectors;
        public Template(String key, Vec size, List<String> states, List<Command> commands,
                List<Connector> connectors, String grammarSha256) {
            this.key = key; this.size = size; this.states = List.copyOf(states);
            this.commands = List.copyOf(commands); this.connectors = List.copyOf(connectors);
            this.grammarSha256 = grammarSha256;
        }
        public String key() { return key; }
        public Vec size() { return size; }
        public List<String> states() { return states; }
        public List<Command> commands() { return commands; }
        public List<Connector> connectors() { return connectors; }
        public String grammarSha256() { return grammarSha256; }
    }

    public static final class Element {
        private final String template, processor;
        private final int weight;
        public Element(String template, String processor, int weight) {
            this.template = template; this.processor = processor; this.weight = weight;
        }
        public String template() { return template; }
        public String processor() { return processor; }
        public int weight() { return weight; }
    }

    public static final class Pool {
        private final String key, fallback;
        private final List<Element> elements;
        public Pool(String key, String fallback, List<Element> elements) {
            this.key = key; this.fallback = fallback; this.elements = List.copyOf(elements);
        }
        public String key() { return key; }
        public String fallback() { return fallback; }
        public List<Element> elements() { return elements; }
    }
}
