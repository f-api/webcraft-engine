package com.gameexpert.terrain.mc.structure;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Dormant, evidence-bound parser/model for the authenticated 26.3 Pillager Outpost grammar.
 *
 * <p>The only published instance is decoded from {@link Mc263PillagerOutpostGrammarData}. That
 * source authenticates both the compressed and inflated byte identities and bounds inflation;
 * this class rechecks the inflated identity before parsing. The evidence intentionally contains
 * no raw template NBT and no loot seed field, so neither is invented here. DATA, LOOT and entity
 * evidence is exposed only through typed records.</p>
 */
final class Mc263PillagerOutpostGrammar {
    static final int SCHEMA = 1;
    static final String RECEIPT_ID = "OUT-E2A";
    static final int JAVA_VERSION = 25;
    static final String SERVER_VERSION = "26.3 Snapshot 7";
    static final int TEMPLATE_COUNT = 11;
    static final int BLOCK_COUNT = 20_741;
    static final int COMMAND_COUNT = 3_764;
    static final int CONNECTOR_COUNT = 55;
    static final int ENTITY_COUNT = 3;
    static final int STATE_COUNT = 152;
    static final int RUN_COUNT = 3_691;
    static final int JIGSAW_COUNT = 55;
    static final int DATA_COUNT = 16;
    static final int LOOT_COUNT = 2;

    private static final String EMPTY_POOL = "minecraft:empty";
    private static final String INLINE_PROCESSOR = "inline";
    private static final String OUTPOST_ROT = "minecraft:outpost_rot";
    private static final List<String> POOL_KEYS = List.of(
            "minecraft:pillager_outpost/base_plates",
            "minecraft:pillager_outpost/towers",
            "minecraft:pillager_outpost/feature_plates",
            "minecraft:pillager_outpost/features");
    private static final List<String> TEMPLATE_KEYS = List.of(
            "minecraft:pillager_outpost/base_plate",
            "minecraft:pillager_outpost/watchtower",
            "minecraft:pillager_outpost/watchtower_overgrown",
            "minecraft:pillager_outpost/feature_plate",
            "minecraft:pillager_outpost/feature_cage1",
            "minecraft:pillager_outpost/feature_cage2",
            "minecraft:pillager_outpost/feature_cage_with_allays",
            "minecraft:pillager_outpost/feature_logs",
            "minecraft:pillager_outpost/feature_tent1",
            "minecraft:pillager_outpost/feature_tent2",
            "minecraft:pillager_outpost/feature_targets");
    private static final int[] EXPECTED_STATE_COUNTS = {5, 40, 48, 3, 10, 10, 13, 3, 6, 7, 7};
    private static final int[] EXPECTED_BLOCK_COUNTS = {
            7_680, 4_725, 5_175, 2_048, 196, 196, 196, 126, 168, 168, 63};
    private static final int[] EXPECTED_COMMAND_COUNTS = {
            489, 1_244, 1_401, 96, 99, 99, 100, 28, 87, 92, 29};
    private static final int[] EXPECTED_CONNECTOR_COUNTS = {5, 1, 1, 16, 5, 5, 5, 4, 4, 4, 5};
    private static final int[] EXPECTED_ENTITY_COUNTS = {0, 0, 0, 0, 1, 0, 2, 0, 0, 0, 0};
    private static final List<Vec3i> EXPECTED_SIZES = List.of(
            new Vec3i(16, 30, 16), new Vec3i(15, 21, 15), new Vec3i(15, 23, 15),
            new Vec3i(16, 4, 32), new Vec3i(7, 4, 7), new Vec3i(7, 4, 7),
            new Vec3i(7, 4, 7), new Vec3i(6, 3, 7), new Vec3i(6, 4, 7),
            new Vec3i(6, 4, 7), new Vec3i(3, 3, 7));
    private static final Set<Vec3i> RUN_DELTAS = Set.of(
            new Vec3i(0, 0, 0), new Vec3i(0, 0, 1), new Vec3i(1, 0, -1),
            new Vec3i(1, 0, 0), new Vec3i(1, 0, 1));
    private static final Set<String> CONNECTOR_NAMES = Set.of(
            "minecraft:entrance", "minecraft:feature", "minecraft:plate_entry");
    private static final Set<String> CONNECTOR_POOLS = Set.of(
            EMPTY_POOL, "minecraft:pillager_outpost/towers",
            "minecraft:pillager_outpost/feature_plates",
            "minecraft:pillager_outpost/features");
    private static final List<String> ASSET_BOUNDARY = List.of(
            "no raw Mojang structure NBT or template payload copy",
            "no finite seed/chunk generation probes or production coordinate lookup",
            "block payload is a lossless state-table plus arithmetic RUN/special-command grammar",
            "entity sidecars retain only authenticated type and template-relative position; payload semantics are not claimed");
    private static final TypedSidecars EXPECTED_SIDECARS = new TypedSidecars(
            List.of(new SidecarIdentity("LOOT_CONTAINER|minecraft:chest", 2),
                    new SidecarIdentity("OMINOUS_BANNER|minecraft:banner", 16)),
            List.of(new SidecarIdentity(
                    "minecraft:chests/pillager_outpost|minecraft:chest", 2)),
            List.of(new SidecarIdentity("minecraft:allay", 2),
                    new SidecarIdentity("minecraft:iron_golem", 1)));
    private static final Corpus PINNED = loadPinned();

    private Mc263PillagerOutpostGrammar() {
        throw new AssertionError("no instances");
    }

    static Corpus pinned() {
        return PINNED;
    }

    private static Corpus loadPinned() {
        try {
            return decodeAuthenticated(Mc263PillagerOutpostGrammarData.decode());
        } catch (IOException | RuntimeException error) {
            throw new ExceptionInInitializerError(error);
        }
    }

    private static Corpus decodeAuthenticated(byte[] bytes) {
        Objects.requireNonNull(bytes, "Pillager Outpost grammar bytes");
        require(bytes.length == Mc263PillagerOutpostGrammarData.PAYLOAD_BYTES,
                "Pillager Outpost grammar payload byte-count drift");
        require(Mc263PillagerOutpostGrammarData.PAYLOAD_SHA256.equals(sha256(bytes)),
                "Pillager Outpost grammar payload identity drift");
        return parseEvidence(bytes);
    }

    private static Corpus parseEvidence(byte[] bytes) {
        Objects.requireNonNull(bytes, "Pillager Outpost grammar bytes");
        require(bytes.length <= Mc263PillagerOutpostGrammarData.PAYLOAD_BYTES + 4_096,
                "Pillager Outpost grammar parser bound exceeded");
        JsonReader in = new JsonReader(bytes);
        in.beginObject();
        in.field("schema", 0); int schema = in.readInt();
        in.field("receiptId", 1); String receiptId = in.readString();
        in.field("javaVersion", 2); int javaVersion = in.readInt();
        in.field("serverVersion", 3); String serverVersion = in.readString();
        in.field("evidence", 4); Evidence evidence = readEvidence(in);
        in.endObject();
        in.finish();
        require(schema == SCHEMA, "Pillager Outpost schema drift");
        require(RECEIPT_ID.equals(receiptId), "Pillager Outpost receipt drift");
        require(javaVersion == JAVA_VERSION, "Pillager Outpost Java version drift");
        require(SERVER_VERSION.equals(serverVersion), "Pillager Outpost server version drift");
        validateEvidence(evidence);
        return new Corpus(schema, receiptId, javaVersion, serverVersion, evidence);
    }

    private static Evidence readEvidence(JsonReader in) {
        in.beginObject();
        in.field("structureSet", 0); StructureSet structureSet = readStructureSet(in);
        in.field("structure", 1); Structure structure = readStructure(in);
        in.field("registryPoolKeysInExecutionOrder", 2);
        List<String> registryPools = readStrings(in, 16, "registry pool keys");
        in.field("poolsInExecutionOrder", 3); List<Pool> pools = readPools(in);
        in.field("processorListsInEncounterOrder", 4);
        List<ProcessorList> processorLists = readProcessorLists(in);
        in.field("processorSemanticsInEncounterOrder", 5);
        List<ProcessorSemantic> processorSemantics = readProcessorSemantics(in);
        in.field("templatesInEncounterOrder", 6); List<Template> templates = readTemplates(in);
        in.field("typedSidecars", 7); TypedSidecars sidecars = readTypedSidecars(in);
        in.field("assetBoundary", 8); List<String> assetBoundary = readStrings(in, 8, "asset boundary");
        in.endObject();
        return new Evidence(structureSet, structure, registryPools, pools, processorLists,
                processorSemantics, templates, sidecars, assetBoundary);
    }

    private static StructureSet readStructureSet(JsonReader in) {
        in.beginObject();
        in.field("key", 0); String key = in.readString();
        in.field("entries", 1);
        in.beginArray();
        ArrayList<StructureEntry> entries = new ArrayList<>();
        for (int index = 0; in.nextArrayValue(index); index++) {
            require(index < 4, "too many Pillager Outpost structure entries");
            in.beginObject();
            in.field("structureKey", 0); String structureKey = in.readString();
            in.field("weight", 1); int weight = in.readInt();
            in.endObject();
            entries.add(new StructureEntry(structureKey, weight));
        }
        in.endArray();
        in.field("placementType", 2); String placementType = in.readString();
        in.field("spacing", 3); int spacing = in.readInt();
        in.field("separation", 4); int separation = in.readInt();
        in.field("spreadType", 5); String spreadType = in.readString();
        in.field("salt", 6); int salt = in.readInt();
        in.field("frequency", 7); double frequency = in.readDouble();
        in.field("frequencyReductionMethod", 8); String reduction = in.readString();
        in.field("locateOffset", 9); Vec3i locateOffset = readVec3i(in);
        in.field("exclusionZone", 10);
        in.beginObject();
        in.field("otherSet", 0); String otherSet = in.readString();
        in.field("chunkCount", 1); int chunkCount = in.readInt();
        in.endObject();
        in.endObject();
        return new StructureSet(key, entries, placementType, spacing, separation, spreadType,
                salt, frequency, reduction, locateOffset, new ExclusionZone(otherSet, chunkCount));
    }

    private static Structure readStructure(JsonReader in) {
        in.beginObject();
        in.field("key", 0); String key = in.readString();
        in.field("orderedCodec", 1); JigsawCodec codec = readJigsawCodec(in);
        in.field("defaultLiquidSettings", 2); String liquid = in.readString();
        in.endObject();
        return new Structure(key, codec, liquid);
    }

    private static JigsawCodec readJigsawCodec(JsonReader in) {
        in.beginObject();
        in.field("biomes", 0); String biomes = in.readString();
        in.field("spawn_overrides", 1);
        in.beginObject();
        in.field("monster", 0);
        in.beginObject();
        in.field("bounding_box", 0); String boundingBox = in.readString();
        in.field("spawns", 1);
        in.beginArray();
        ArrayList<Spawn> spawns = new ArrayList<>();
        for (int index = 0; in.nextArrayValue(index); index++) {
            require(index < 8, "too many Pillager Outpost spawn rows");
            in.beginObject();
            in.field("type", 0); String type = in.readString();
            in.field("count", 1); int count = in.readInt();
            in.field("weight", 2); int weight = in.readInt();
            in.endObject();
            spawns.add(new Spawn(type, count, weight));
        }
        in.endArray(); in.endObject(); in.endObject();
        in.field("step", 2); String step = in.readString();
        in.field("terrain_adaptation", 3); String terrain = in.readString();
        in.field("start_pool", 4); String startPool = in.readString();
        in.field("size", 5); int size = in.readInt();
        in.field("start_height", 6);
        in.beginObject(); in.field("absolute", 0); int absolute = in.readInt(); in.endObject();
        in.field("use_expansion_hack", 7); boolean expansion = in.readBoolean();
        in.field("project_start_to_heightmap", 8); String heightmap = in.readString();
        in.field("max_distance_from_center", 9); int maxDistance = in.readInt();
        in.field("type", 10); String type = in.readString();
        in.endObject();
        return new JigsawCodec(biomes, new SpawnOverride(boundingBox, spawns), step, terrain,
                startPool, size, absolute, expansion, heightmap, maxDistance, type);
    }

    private static List<Pool> readPools(JsonReader in) {
        in.beginArray();
        ArrayList<Pool> pools = new ArrayList<>();
        for (int index = 0; in.nextArrayValue(index); index++) {
            require(index < 8, "too many Pillager Outpost pools");
            in.beginObject();
            in.field("key", 0); String key = in.readString();
            in.field("fallback", 1); String fallback = in.readString();
            in.field("rawElementCount", 2); int rawCount = in.readInt();
            in.field("expandedWeight", 3); int expandedWeight = in.readInt();
            in.field("elementsInDeclaredOrder", 4);
            List<PoolElement> elements = readPoolElements(in);
            in.endObject();
            pools.add(new Pool(key, fallback, rawCount, expandedWeight, elements));
        }
        in.endArray();
        return List.copyOf(pools);
    }

    private static List<PoolElement> readPoolElements(JsonReader in) {
        in.beginArray();
        ArrayList<PoolElement> elements = new ArrayList<>();
        for (int index = 0; in.nextArrayValue(index); index++) {
            require(index < 32, "too many Pillager Outpost pool elements");
            in.beginObject();
            in.field("ordinal", 0); int ordinal = in.readInt();
            in.field("weight", 1); int weight = in.readInt();
            in.field("projection", 2); Projection projection = Projection.from(in.readString());
            in.field("kind", 3); String kind = in.readString();
            PoolElement element;
            switch (kind) {
                case "single" -> {
                    in.field("template", 4); String template = in.readString();
                    in.field("processorList", 5); String processor = in.readString();
                    in.field("placementProcessorsInOrder", 6);
                    List<String> placement = readStrings(in, 8, "placement processors");
                    element = new SingleElement(ordinal, weight, projection, template, processor,
                            placement);
                }
                case "list" -> {
                    in.field("childrenInDeclaredOrder", 4);
                    element = new ListElement(ordinal, weight, projection, readChildren(in));
                }
                case "empty" -> element = new EmptyElement(ordinal, weight, projection);
                default -> throw invalid("unknown Pillager Outpost pool element kind: " + kind);
            }
            in.endObject();
            elements.add(element);
        }
        in.endArray();
        return List.copyOf(elements);
    }

    private static List<SingleChild> readChildren(JsonReader in) {
        in.beginArray();
        ArrayList<SingleChild> children = new ArrayList<>();
        for (int index = 0; in.nextArrayValue(index); index++) {
            require(index < 16, "too many Pillager Outpost list children");
            in.beginObject();
            in.field("ordinal", 0); int ordinal = in.readInt();
            in.field("projection", 1); Projection projection = Projection.from(in.readString());
            in.field("kind", 2); String kind = in.readString();
            require("single".equals(kind), "unknown Pillager Outpost list child kind: " + kind);
            in.field("template", 3); String template = in.readString();
            in.field("processorList", 4); String processor = in.readString();
            in.field("placementProcessorsInOrder", 5);
            List<String> placement = readStrings(in, 8, "child placement processors");
            in.endObject();
            children.add(new SingleChild(ordinal, projection, template, processor, placement));
        }
        in.endArray();
        return List.copyOf(children);
    }

    private static List<ProcessorList> readProcessorLists(JsonReader in) {
        in.beginArray();
        ArrayList<ProcessorList> lists = new ArrayList<>();
        for (int index = 0; in.nextArrayValue(index); index++) {
            require(index < 8, "too many Pillager Outpost processor lists");
            in.beginObject();
            in.field("identity", 0); String identity = in.readString();
            in.field("registered", 1); boolean registered = in.readBoolean();
            in.field("orderedCodec", 2);
            in.beginObject(); in.field("processors", 0);
            in.beginArray();
            ArrayList<ProcessorConfig> configs = new ArrayList<>();
            for (int processorIndex = 0; in.nextArrayValue(processorIndex); processorIndex++) {
                require(processorIndex < 8, "too many Pillager Outpost processor configs");
                in.beginObject();
                in.field("integrity", 0); double integrity = in.readDouble();
                in.field("processor_type", 1); String type = in.readString();
                in.endObject();
                require("minecraft:block_rot".equals(type),
                        "unknown Pillager Outpost processor config: " + type);
                configs.add(new BlockRotConfig(integrity));
            }
            in.endArray(); in.endObject();
            in.field("processorsInOrder", 3);
            List<String> order = readStrings(in, 8, "processor order");
            in.endObject();
            lists.add(new ProcessorList(identity, registered, configs, order));
        }
        in.endArray();
        return List.copyOf(lists);
    }

    private static List<ProcessorSemantic> readProcessorSemantics(JsonReader in) {
        in.beginArray();
        ArrayList<ProcessorSemantic> semantics = new ArrayList<>();
        for (int index = 0; in.nextArrayValue(index); index++) {
            require(index < 8, "too many Pillager Outpost processor semantics");
            in.beginObject();
            in.field("type", 0); String type = in.readString();
            in.field("runtimeClass", 1); String runtimeClass = in.readString();
            ProcessorSemantic semantic;
            switch (type) {
                case "minecraft:jigsaw_replacement" -> {
                    in.field("orderedCodec", 2);
                    in.beginObject(); in.field("processor_type", 0);
                    String codecType = in.readString(); in.endObject();
                    in.field("rng", 3); readNoRng(in);
                    semantic = new JigsawReplacementSemantic(runtimeClass, codecType);
                }
                case "minecraft:block_ignore" -> {
                    in.field("orderedCodec", 2);
                    in.beginObject(); in.field("blocks", 0);
                    List<String> blocks = readStrings(in, 8, "ignored blocks");
                    in.field("processor_type", 1); String codecType = in.readString(); in.endObject();
                    in.field("rng", 3); readNoRng(in);
                    semantic = new BlockIgnoreSemantic(runtimeClass, blocks, codecType);
                }
                case "minecraft:block_rot" -> {
                    in.field("orderedCodec", 2);
                    in.beginObject(); in.field("integrity", 0); double integrity = in.readDouble();
                    in.field("processor_type", 1); String codecType = in.readString(); in.endObject();
                    in.field("rng", 3); BlockRotRng rng = readBlockRotRng(in);
                    semantic = new BlockRotSemantic(runtimeClass, integrity, codecType, rng);
                }
                case "minecraft:gravity" -> {
                    in.field("orderedCodec", 2);
                    in.beginObject(); in.field("offset", 0); int offset = in.readInt();
                    in.field("processor_type", 1); String codecType = in.readString(); in.endObject();
                    in.field("rng", 3); readNoRng(in);
                    semantic = new GravitySemantic(runtimeClass, offset, codecType);
                }
                default -> throw invalid("unknown Pillager Outpost processor semantic: " + type);
            }
            in.endObject();
            semantics.add(semantic);
        }
        in.endArray();
        return List.copyOf(semantics);
    }

    private static void readNoRng(JsonReader in) {
        in.beginObject(); in.field("kind", 0);
        require("none".equals(in.readString()), "unexpected Pillager Outpost RNG semantic");
        in.endObject();
    }

    private static BlockRotRng readBlockRotRng(JsonReader in) {
        in.beginObject();
        in.field("kind", 0); String kind = in.readString();
        in.field("rottableBlocks", 1); String blocks = in.readString();
        in.field("integrity", 2); double integrity = in.readDouble();
        in.field("xMultiplierI32", 3); int xMultiplier = in.readInt();
        in.field("zMultiplierI64", 4); long zMultiplier = in.readLong();
        in.field("squareMultiplierI64", 5); long squareMultiplier = in.readLong();
        in.field("linearMultiplierI64", 6); long linearMultiplier = in.readLong();
        in.field("arithmeticRightShift", 7); int shift = in.readInt();
        in.field("randomSource", 8); String randomSource = in.readString();
        in.field("draw", 9); String draw = in.readString();
        in.field("keepWhen", 10); String keepWhen = in.readString();
        in.endObject();
        return new BlockRotRng(kind, blocks, integrity, xMultiplier, zMultiplier,
                squareMultiplier, linearMultiplier, shift, randomSource, draw, keepWhen);
    }

    private static List<Template> readTemplates(JsonReader in) {
        in.beginArray();
        ArrayList<Template> templates = new ArrayList<>();
        for (int index = 0; in.nextArrayValue(index); index++) {
            require(index < TEMPLATE_COUNT + 1, "too many Pillager Outpost templates");
            in.beginObject();
            in.field("template", 0); String key = in.readString();
            in.field("size", 1); Vec3i size = readVec3i(in);
            in.field("stateTable", 2); List<String> states = readStrings(in, 256, "state table");
            in.field("blockCount", 3); int blockCount = in.readInt();
            in.field("commandCount", 4); int commandCount = in.readInt();
            in.field("commands", 5); List<Command> commands = readCommands(in, commandCount);
            in.field("connectorCount", 6); int connectorCount = in.readInt();
            in.field("connectorsInTemplateOrder", 7);
            List<Connector> connectors = readConnectors(in, connectorCount);
            in.field("entityCount", 8); int entityCount = in.readInt();
            in.field("entitySidecarsInTemplateOrder", 9);
            List<EntitySidecar> entities = readEntities(in, entityCount);
            in.endObject();
            templates.add(new Template(key, size, states, blockCount, commandCount, commands,
                    connectorCount, connectors, entityCount, entities));
        }
        in.endArray();
        return List.copyOf(templates);
    }

    private static List<Command> readCommands(JsonReader in, int declaredCount) {
        require(declaredCount >= 0 && declaredCount <= COMMAND_COUNT,
                "Pillager Outpost command count outside bound");
        in.beginArray();
        ArrayList<Command> commands = new ArrayList<>(declaredCount);
        for (int index = 0; in.nextArrayValue(index); index++) {
            require(index < COMMAND_COUNT + 1, "too many Pillager Outpost commands");
            in.beginObject();
            in.field("op", 0); String op = in.readString();
            in.field("ordinal", 1); int ordinal = in.readInt();
            Command command;
            switch (op) {
                case "RUN" -> {
                    in.field("start", 2); Vec3i start = readVec3i(in);
                    in.field("delta", 3); Vec3i delta = readVec3i(in);
                    in.field("count", 4); int count = in.readInt();
                    in.field("state", 5); int state = in.readInt();
                    command = new Run(ordinal, start, delta, count, state);
                }
                case "JIGSAW" -> {
                    in.field("state", 2); int state = in.readInt();
                    in.field("connectorOrdinal", 3); int connectorOrdinal = in.readInt();
                    in.field("finalState", 4); String finalState = in.readString();
                    command = new Jigsaw(ordinal, state, connectorOrdinal, finalState);
                }
                case "DATA" -> {
                    in.field("position", 2); Vec3i position = readVec3i(in);
                    in.field("state", 3); int state = in.readInt();
                    in.field("semantic", 4); String semantic = in.readString();
                    in.field("blockEntityType", 5); String blockEntityType = in.readString();
                    require("OMINOUS_BANNER".equals(semantic),
                            "unknown Pillager Outpost DATA semantic: " + semantic);
                    command = new Data(ordinal, position, state, DataSemantic.OMINOUS_BANNER,
                            blockEntityType);
                }
                case "LOOT_CONTAINER" -> {
                    in.field("position", 2); Vec3i position = readVec3i(in);
                    in.field("state", 3); int state = in.readInt();
                    in.field("blockEntityType", 4); String blockEntityType = in.readString();
                    in.field("lootTable", 5); String lootTable = in.readString();
                    command = new LootContainer(ordinal, position, state, blockEntityType, lootTable);
                }
                default -> throw invalid("unknown Pillager Outpost command op: " + op);
            }
            in.endObject();
            commands.add(command);
        }
        in.endArray();
        require(commands.size() == declaredCount,
                "Pillager Outpost declared command count drift");
        return List.copyOf(commands);
    }

    private static List<Connector> readConnectors(JsonReader in, int declaredCount) {
        require(declaredCount >= 0 && declaredCount <= CONNECTOR_COUNT,
                "Pillager Outpost connector count outside bound");
        in.beginArray();
        ArrayList<Connector> connectors = new ArrayList<>(declaredCount);
        for (int index = 0; in.nextArrayValue(index); index++) {
            require(index < CONNECTOR_COUNT + 1, "too many Pillager Outpost connectors");
            in.beginObject();
            in.field("ordinal", 0); int ordinal = in.readInt();
            in.field("position", 1); Vec3i position = readVec3i(in);
            in.field("front", 2); Direction front = Direction.from(in.readString());
            in.field("top", 3); Direction top = Direction.from(in.readString());
            in.field("joint", 4); Joint joint = Joint.from(in.readString());
            in.field("name", 5); String name = in.readString();
            in.field("target", 6); String target = in.readString();
            in.field("pool", 7); String pool = in.readString();
            in.field("placementPriority", 8); int placementPriority = in.readInt();
            in.field("selectionPriority", 9); int selectionPriority = in.readInt();
            in.endObject();
            connectors.add(new Connector(ordinal, position, front, top, joint, name, target,
                    pool, placementPriority, selectionPriority));
        }
        in.endArray();
        require(connectors.size() == declaredCount,
                "Pillager Outpost declared connector count drift");
        return List.copyOf(connectors);
    }

    private static List<EntitySidecar> readEntities(JsonReader in, int declaredCount) {
        require(declaredCount >= 0 && declaredCount <= ENTITY_COUNT,
                "Pillager Outpost entity count outside bound");
        in.beginArray();
        ArrayList<EntitySidecar> entities = new ArrayList<>(declaredCount);
        for (int index = 0; in.nextArrayValue(index); index++) {
            require(index < ENTITY_COUNT + 1, "too many Pillager Outpost entity sidecars");
            in.beginObject();
            in.field("ordinal", 0); int ordinal = in.readInt();
            in.field("entityType", 1); EntityType type = EntityType.from(in.readString());
            in.field("position", 2); Vec3d position = readVec3d(in);
            in.field("blockPosition", 3); Vec3i blockPosition = readVec3i(in);
            in.endObject();
            entities.add(new EntitySidecar(ordinal, type, position, blockPosition));
        }
        in.endArray();
        require(entities.size() == declaredCount,
                "Pillager Outpost declared entity count drift");
        return List.copyOf(entities);
    }

    private static TypedSidecars readTypedSidecars(JsonReader in) {
        in.beginObject();
        in.field("BENT", 0); List<SidecarIdentity> bent = readSidecars(in, "BENT");
        in.field("LOOT", 1); List<SidecarIdentity> loot = readSidecars(in, "LOOT");
        in.field("ENTS", 2); List<SidecarIdentity> ents = readSidecars(in, "ENTS");
        in.endObject();
        return new TypedSidecars(bent, loot, ents);
    }

    private static List<SidecarIdentity> readSidecars(JsonReader in, String lane) {
        in.beginArray();
        ArrayList<SidecarIdentity> values = new ArrayList<>();
        for (int index = 0; in.nextArrayValue(index); index++) {
            require(index < 8, "too many Pillager Outpost " + lane + " sidecars");
            in.beginObject();
            in.field("identity", 0); String identity = in.readString();
            in.field("occurrences", 1); int occurrences = in.readInt();
            in.endObject();
            values.add(new SidecarIdentity(identity, occurrences));
        }
        in.endArray();
        return List.copyOf(values);
    }

    private static List<String> readStrings(JsonReader in, int maximum, String label) {
        in.beginArray();
        ArrayList<String> values = new ArrayList<>();
        for (int index = 0; in.nextArrayValue(index); index++) {
            require(index < maximum, "too many Pillager Outpost " + label);
            values.add(in.readString());
        }
        in.endArray();
        return List.copyOf(values);
    }

    private static Vec3i readVec3i(JsonReader in) {
        in.beginArray();
        require(in.nextArrayValue(0), "missing vector x"); int x = in.readInt();
        require(in.nextArrayValue(1), "missing vector y"); int y = in.readInt();
        require(in.nextArrayValue(2), "missing vector z"); int z = in.readInt();
        require(!in.nextArrayValue(3), "extra integer vector coordinate");
        in.endArray();
        return new Vec3i(x, y, z);
    }

    private static Vec3d readVec3d(JsonReader in) {
        in.beginArray();
        require(in.nextArrayValue(0), "missing vector x"); double x = in.readDouble();
        require(in.nextArrayValue(1), "missing vector y"); double y = in.readDouble();
        require(in.nextArrayValue(2), "missing vector z"); double z = in.readDouble();
        require(!in.nextArrayValue(3), "extra floating vector coordinate");
        in.endArray();
        return new Vec3d(x, y, z);
    }

    private static void validateEvidence(Evidence evidence) {
        require(evidence.structureSet().equals(new StructureSet(
                        "minecraft:pillager_outposts",
                        List.of(new StructureEntry("minecraft:pillager_outpost", 1)),
                        "minecraft:random_spread", 32, 8, "LINEAR", 165_745_296, 0.2,
                        "LEGACY_TYPE_1", new Vec3i(0, 0, 0),
                        new ExclusionZone("minecraft:villages", 10))),
                "Pillager Outpost structure-set semantic drift");
        JigsawCodec expectedCodec = new JigsawCodec(
                "#minecraft:has_structure/pillager_outpost",
                new SpawnOverride("full", List.of(new Spawn("minecraft:pillager", 1, 1))),
                "surface_structures", "beard_thin",
                "minecraft:pillager_outpost/base_plates", 7, 0, true,
                "WORLD_SURFACE_WG", 80, "minecraft:jigsaw");
        require(evidence.structure().equals(new Structure(
                        "minecraft:pillager_outpost", expectedCodec, "apply_waterlogging")),
                "Pillager Outpost structure semantic drift");
        require(evidence.registryPoolKeysInExecutionOrder().equals(POOL_KEYS),
                "Pillager Outpost registry pool order drift");
        validatePools(evidence.poolsInExecutionOrder());
        validateProcessorLists(evidence.processorListsInEncounterOrder());
        validateProcessorSemantics(evidence.processorSemanticsInEncounterOrder());
        validateTemplates(evidence.templatesInEncounterOrder());
        require(evidence.typedSidecars().equals(EXPECTED_SIDECARS),
                "Pillager Outpost typed sidecar drift");
        require(evidence.assetBoundary().equals(ASSET_BOUNDARY),
                "Pillager Outpost asset-boundary drift");
    }

    private static void validatePools(List<Pool> pools) {
        require(pools.size() == 4, "Pillager Outpost pool cardinality drift");
        List<String> basicPlacement = List.of(
                "minecraft:jigsaw_replacement", "minecraft:block_ignore");
        List<Pool> expected = List.of(
                new Pool(POOL_KEYS.get(0), EMPTY_POOL, 1, 1, List.of(
                        new SingleElement(0, 1, Projection.RIGID, TEMPLATE_KEYS.get(0),
                                INLINE_PROCESSOR, basicPlacement))),
                new Pool(POOL_KEYS.get(1), EMPTY_POOL, 1, 1, List.of(
                        new ListElement(0, 1, Projection.RIGID, List.of(
                                new SingleChild(0, Projection.RIGID, TEMPLATE_KEYS.get(1),
                                        INLINE_PROCESSOR, basicPlacement),
                                new SingleChild(1, Projection.RIGID, TEMPLATE_KEYS.get(2),
                                        OUTPOST_ROT, List.of("minecraft:jigsaw_replacement",
                                                "minecraft:block_rot", "minecraft:block_ignore")))))),
                new Pool(POOL_KEYS.get(2), EMPTY_POOL, 1, 1, List.of(
                        new SingleElement(0, 1, Projection.TERRAIN_MATCHING,
                                TEMPLATE_KEYS.get(3), INLINE_PROCESSOR,
                                List.of("minecraft:jigsaw_replacement", "minecraft:gravity",
                                        "minecraft:block_ignore")))),
                new Pool(POOL_KEYS.get(3), EMPTY_POOL, 8, 13, List.of(
                        new SingleElement(0, 1, Projection.RIGID, TEMPLATE_KEYS.get(4), INLINE_PROCESSOR, basicPlacement),
                        new SingleElement(1, 1, Projection.RIGID, TEMPLATE_KEYS.get(5), INLINE_PROCESSOR, basicPlacement),
                        new SingleElement(2, 1, Projection.RIGID, TEMPLATE_KEYS.get(6), INLINE_PROCESSOR, basicPlacement),
                        new SingleElement(3, 1, Projection.RIGID, TEMPLATE_KEYS.get(7), INLINE_PROCESSOR, basicPlacement),
                        new SingleElement(4, 1, Projection.RIGID, TEMPLATE_KEYS.get(8), INLINE_PROCESSOR, basicPlacement),
                        new SingleElement(5, 1, Projection.RIGID, TEMPLATE_KEYS.get(9), INLINE_PROCESSOR, basicPlacement),
                        new SingleElement(6, 1, Projection.RIGID, TEMPLATE_KEYS.get(10), INLINE_PROCESSOR, basicPlacement),
                        new EmptyElement(7, 6, Projection.TERRAIN_MATCHING))));
        require(pools.equals(expected), "Pillager Outpost pool/feature semantic drift");
    }

    private static void validateProcessorLists(List<ProcessorList> lists) {
        List<ProcessorList> expected = List.of(
                new ProcessorList(INLINE_PROCESSOR, false, List.of(), List.of()),
                new ProcessorList(OUTPOST_ROT, true,
                        List.of(new BlockRotConfig(0.05)), List.of("minecraft:block_rot")));
        require(lists.equals(expected), "Pillager Outpost processor-list semantic drift");
    }

    private static void validateProcessorSemantics(List<ProcessorSemantic> semantics) {
        List<ProcessorSemantic> expected = List.of(
                new JigsawReplacementSemantic(
                        "net.minecraft.world.level.levelgen.structure.templatesystem.JigsawReplacementProcessor",
                        "minecraft:jigsaw_replacement"),
                new BlockIgnoreSemantic(
                        "net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor",
                        List.of("minecraft:air", "minecraft:structure_block"),
                        "minecraft:block_ignore"),
                new BlockRotSemantic(
                        "net.minecraft.world.level.levelgen.structure.templatesystem.BlockRotProcessor",
                        0.05, "minecraft:block_rot",
                        new BlockRotRng("position_seeded_next_float", "all", 0.05, 3_129_871,
                                116_129_781L, 42_317_861L, 11L, 16,
                                "RandomSource.create(positionSeed)", "nextFloat",
                                "draw <= integrity")),
                new GravitySemantic(
                        "net.minecraft.world.level.levelgen.structure.templatesystem.GravityProcessor",
                        -1, "minecraft:gravity"));
        require(semantics.equals(expected), "Pillager Outpost processor semantic drift");
    }

    private static void validateTemplates(List<Template> templates) {
        require(templates.size() == TEMPLATE_COUNT, "Pillager Outpost template cardinality drift");
        int states = 0, blocks = 0, commands = 0, connectors = 0, entities = 0;
        int runs = 0, jigsaws = 0, data = 0, loot = 0;
        for (int templateIndex = 0; templateIndex < templates.size(); templateIndex++) {
            Template template = templates.get(templateIndex);
            require(TEMPLATE_KEYS.get(templateIndex).equals(template.key()),
                    "unknown or misordered Pillager Outpost template: " + template.key());
            require(EXPECTED_SIZES.get(templateIndex).equals(template.size()),
                    "Pillager Outpost template size drift: " + template.key());
            require(template.stateTable().size() == EXPECTED_STATE_COUNTS[templateIndex]
                            && new HashSet<>(template.stateTable()).size() == template.stateTable().size(),
                    "Pillager Outpost state-table drift: " + template.key());
            for (String state : template.stateTable()) {
                require(state.startsWith("minecraft:") && !state.isBlank(),
                        "invalid Pillager Outpost exact state: " + state);
            }
            require(template.blockCount() == EXPECTED_BLOCK_COUNTS[templateIndex]
                            && template.commandCount() == EXPECTED_COMMAND_COUNTS[templateIndex]
                            && template.connectorCount() == EXPECTED_CONNECTOR_COUNTS[templateIndex]
                            && template.entityCount() == EXPECTED_ENTITY_COUNTS[templateIndex],
                    "Pillager Outpost per-template aggregate drift: " + template.key());
            require(template.commands().size() == template.commandCount()
                            && template.connectors().size() == template.connectorCount()
                            && template.entities().size() == template.entityCount(),
                    "Pillager Outpost declared list aggregate drift: " + template.key());

            int ordinal = 0;
            int jigsawOrdinal = 0;
            for (Command command : template.commands()) {
                require(command.ordinal() == ordinal,
                        "Pillager Outpost command continuity drift: " + template.key());
                require(command.state() >= 0 && command.state() < template.stateTable().size(),
                        "Pillager Outpost command state index drift: " + template.key());
                switch (command) {
                    case Run run -> {
                        require(run.count() > 0 && RUN_DELTAS.contains(run.delta()),
                                "Pillager Outpost RUN grammar drift: " + template.key());
                        require(inside(run.start(), template.size())
                                        && inside(run.lastPosition(), template.size()),
                                "Pillager Outpost RUN position drift: " + template.key());
                        runs++;
                    }
                    case Jigsaw jigsaw -> {
                        require(jigsaw.connectorOrdinal() == jigsawOrdinal
                                        && "minecraft:air".equals(jigsaw.finalState()),
                                "Pillager Outpost JIGSAW binding drift: " + template.key());
                        jigsawOrdinal++; jigsaws++;
                    }
                    case Data dataCommand -> {
                        require(inside(dataCommand.position(), template.size())
                                        && dataCommand.semantic() == DataSemantic.OMINOUS_BANNER
                                        && "minecraft:banner".equals(dataCommand.blockEntityType()),
                                "Pillager Outpost DATA semantic drift: " + template.key());
                        data++;
                    }
                    case LootContainer lootCommand -> {
                        require(inside(lootCommand.position(), template.size())
                                        && "minecraft:chest".equals(lootCommand.blockEntityType())
                                        && "minecraft:chests/pillager_outpost".equals(lootCommand.lootTable()),
                                "Pillager Outpost LOOT semantic drift: " + template.key());
                        loot++;
                    }
                }
                ordinal = Math.addExact(ordinal, command.expandedCount());
            }
            require(ordinal == template.blockCount() && jigsawOrdinal == template.connectorCount(),
                    "Pillager Outpost command/block aggregate drift: " + template.key());
            for (int connectorIndex = 0; connectorIndex < template.connectors().size(); connectorIndex++) {
                Connector connector = template.connectors().get(connectorIndex);
                require(connector.ordinal() == connectorIndex && inside(connector.position(), template.size()),
                        "Pillager Outpost connector order/position drift: " + template.key());
                require(connector.front().axis() != connector.top().axis(),
                        "Pillager Outpost connector orientation drift: " + template.key());
                require(CONNECTOR_NAMES.contains(connector.name())
                                && CONNECTOR_NAMES.contains(connector.target())
                                && CONNECTOR_POOLS.contains(connector.pool()),
                        "unknown Pillager Outpost connector vocabulary: " + template.key());
            }
            for (int entityIndex = 0; entityIndex < template.entities().size(); entityIndex++) {
                EntitySidecar entity = template.entities().get(entityIndex);
                require(entity.ordinal() == entityIndex && inside(entity.blockPosition(), template.size()),
                        "Pillager Outpost entity sidecar order/position drift: " + template.key());
            }
            states += template.stateTable().size(); blocks += template.blockCount();
            commands += template.commandCount(); connectors += template.connectorCount();
            entities += template.entityCount();
        }
        require(states == STATE_COUNT && blocks == BLOCK_COUNT && commands == COMMAND_COUNT
                        && connectors == CONNECTOR_COUNT && entities == ENTITY_COUNT,
                "Pillager Outpost aggregate drift");
        require(runs == RUN_COUNT && jigsaws == JIGSAW_COUNT && data == DATA_COUNT
                        && loot == LOOT_COUNT,
                "Pillager Outpost command-op aggregate drift");
    }

    private static boolean inside(Vec3i position, Vec3i size) {
        return position.x() >= 0 && position.x() < size.x()
                && position.y() >= 0 && position.y() < size.y()
                && position.z() >= 0 && position.z() < size.z();
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw invalid(message);
    }

    private static IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException(message);
    }

    enum Projection {
        RIGID("rigid"), TERRAIN_MATCHING("terrain_matching");
        private final String id;
        Projection(String id) { this.id = id; }
        String id() { return id; }
        static Projection from(String id) {
            for (Projection value : values()) if (value.id.equals(id)) return value;
            throw invalid("unknown Pillager Outpost projection: " + id);
        }
    }

    enum Axis { X, Y, Z }
    enum Direction {
        DOWN(Axis.Y), UP(Axis.Y), NORTH(Axis.Z), SOUTH(Axis.Z), WEST(Axis.X), EAST(Axis.X);
        private final Axis axis;
        Direction(Axis axis) { this.axis = axis; }
        Axis axis() { return axis; }
        static Direction from(String id) {
            try { return valueOf(id); }
            catch (IllegalArgumentException error) {
                throw invalid("unknown Pillager Outpost direction: " + id);
            }
        }
    }
    enum Joint {
        ALIGNED, ROLLABLE;
        static Joint from(String id) {
            try { return valueOf(id); }
            catch (IllegalArgumentException error) {
                throw invalid("unknown Pillager Outpost joint: " + id);
            }
        }
    }
    enum DataSemantic { OMINOUS_BANNER }
    enum EntityType {
        ALLAY("minecraft:allay"), IRON_GOLEM("minecraft:iron_golem");
        private final String id;
        EntityType(String id) { this.id = id; }
        String id() { return id; }
        static EntityType from(String id) {
            for (EntityType value : values()) if (value.id.equals(id)) return value;
            throw invalid("unknown Pillager Outpost entity: " + id);
        }
    }
    enum CommandOp { RUN, JIGSAW, DATA, LOOT_CONTAINER }

    record Corpus(int schema, String receiptId, int javaVersion, String serverVersion,
            Evidence evidence) { }

    record Evidence(StructureSet structureSet, Structure structure,
            List<String> registryPoolKeysInExecutionOrder, List<Pool> poolsInExecutionOrder,
            List<ProcessorList> processorListsInEncounterOrder,
            List<ProcessorSemantic> processorSemanticsInEncounterOrder,
            List<Template> templatesInEncounterOrder, TypedSidecars typedSidecars,
            List<String> assetBoundary) {
        Evidence {
            registryPoolKeysInExecutionOrder = List.copyOf(registryPoolKeysInExecutionOrder);
            poolsInExecutionOrder = List.copyOf(poolsInExecutionOrder);
            processorListsInEncounterOrder = List.copyOf(processorListsInEncounterOrder);
            processorSemanticsInEncounterOrder = List.copyOf(processorSemanticsInEncounterOrder);
            templatesInEncounterOrder = List.copyOf(templatesInEncounterOrder);
            assetBoundary = List.copyOf(assetBoundary);
        }
        Template requireTemplate(String key) {
            for (Template template : templatesInEncounterOrder) if (template.key().equals(key)) return template;
            throw invalid("unknown Pillager Outpost template: " + key);
        }
        Pool requirePool(String key) {
            for (Pool pool : poolsInExecutionOrder) if (pool.key().equals(key)) return pool;
            throw invalid("unknown Pillager Outpost pool: " + key);
        }
    }

    record StructureSet(String key, List<StructureEntry> entries, String placementType,
            int spacing, int separation, String spreadType, int salt, double frequency,
            String frequencyReductionMethod, Vec3i locateOffset, ExclusionZone exclusionZone) {
        StructureSet { entries = List.copyOf(entries); }
    }
    record StructureEntry(String structureKey, int weight) { }
    record ExclusionZone(String otherSet, int chunkCount) { }
    record Structure(String key, JigsawCodec orderedCodec, String defaultLiquidSettings) { }
    record JigsawCodec(String biomes, SpawnOverride monsterSpawnOverride, String step,
            String terrainAdaptation, String startPool, int size, int startHeightAbsolute,
            boolean useExpansionHack, String projectStartToHeightmap, int maxDistanceFromCenter,
            String type) { }
    record SpawnOverride(String boundingBox, List<Spawn> spawns) {
        SpawnOverride { spawns = List.copyOf(spawns); }
    }
    record Spawn(String type, int count, int weight) { }
    record Vec3i(int x, int y, int z) { }
    record Vec3d(double x, double y, double z) { }

    record Pool(String key, String fallback, int rawElementCount, int expandedWeight,
            List<PoolElement> elementsInDeclaredOrder) {
        Pool { elementsInDeclaredOrder = List.copyOf(elementsInDeclaredOrder); }
    }
    sealed interface PoolElement permits SingleElement, ListElement, EmptyElement {
        int ordinal(); int weight(); Projection projection();
    }
    record SingleElement(int ordinal, int weight, Projection projection, String template,
            String processorList, List<String> placementProcessorsInOrder) implements PoolElement {
        SingleElement { placementProcessorsInOrder = List.copyOf(placementProcessorsInOrder); }
    }
    record ListElement(int ordinal, int weight, Projection projection,
            List<SingleChild> childrenInDeclaredOrder) implements PoolElement {
        ListElement { childrenInDeclaredOrder = List.copyOf(childrenInDeclaredOrder); }
    }
    record EmptyElement(int ordinal, int weight, Projection projection) implements PoolElement { }
    record SingleChild(int ordinal, Projection projection, String template, String processorList,
            List<String> placementProcessorsInOrder) {
        SingleChild { placementProcessorsInOrder = List.copyOf(placementProcessorsInOrder); }
    }

    record ProcessorList(String identity, boolean registered, List<ProcessorConfig> orderedCodec,
            List<String> processorsInOrder) {
        ProcessorList {
            orderedCodec = List.copyOf(orderedCodec);
            processorsInOrder = List.copyOf(processorsInOrder);
        }
    }
    sealed interface ProcessorConfig permits BlockRotConfig { }
    record BlockRotConfig(double integrity) implements ProcessorConfig { }

    sealed interface ProcessorSemantic permits JigsawReplacementSemantic, BlockIgnoreSemantic,
            BlockRotSemantic, GravitySemantic {
        String type(); String runtimeClass();
    }
    record JigsawReplacementSemantic(String runtimeClass, String codecType)
            implements ProcessorSemantic {
        @Override public String type() { return "minecraft:jigsaw_replacement"; }
    }
    record BlockIgnoreSemantic(String runtimeClass, List<String> blocks, String codecType)
            implements ProcessorSemantic {
        BlockIgnoreSemantic { blocks = List.copyOf(blocks); }
        @Override public String type() { return "minecraft:block_ignore"; }
    }
    record BlockRotSemantic(String runtimeClass, double integrity, String codecType, BlockRotRng rng)
            implements ProcessorSemantic {
        @Override public String type() { return "minecraft:block_rot"; }
    }
    record GravitySemantic(String runtimeClass, int offset, String codecType)
            implements ProcessorSemantic {
        @Override public String type() { return "minecraft:gravity"; }
    }
    record BlockRotRng(String kind, String rottableBlocks, double integrity, int xMultiplierI32,
            long zMultiplierI64, long squareMultiplierI64, long linearMultiplierI64,
            int arithmeticRightShift, String randomSource, String draw, String keepWhen) { }

    record Template(String key, Vec3i size, List<String> stateTable, int blockCount,
            int commandCount, List<Command> commands, int connectorCount,
            List<Connector> connectors, int entityCount, List<EntitySidecar> entities) {
        Template {
            stateTable = List.copyOf(stateTable); commands = List.copyOf(commands);
            connectors = List.copyOf(connectors); entities = List.copyOf(entities);
        }
    }
    sealed interface Command permits Run, Jigsaw, Data, LootContainer {
        int ordinal(); int state();
        default int expandedCount() { return 1; }
        CommandOp op();
    }
    record Run(int ordinal, Vec3i start, Vec3i delta, int count, int state) implements Command {
        @Override public int expandedCount() { return count; }
        @Override public CommandOp op() { return CommandOp.RUN; }
        Vec3i lastPosition() {
            return new Vec3i(Math.addExact(start.x(), Math.multiplyExact(delta.x(), count - 1)),
                    Math.addExact(start.y(), Math.multiplyExact(delta.y(), count - 1)),
                    Math.addExact(start.z(), Math.multiplyExact(delta.z(), count - 1)));
        }
    }
    record Jigsaw(int ordinal, int state, int connectorOrdinal, String finalState)
            implements Command {
        @Override public CommandOp op() { return CommandOp.JIGSAW; }
    }
    record Data(int ordinal, Vec3i position, int state, DataSemantic semantic,
            String blockEntityType) implements Command {
        @Override public CommandOp op() { return CommandOp.DATA; }
    }
    record LootContainer(int ordinal, Vec3i position, int state, String blockEntityType,
            String lootTable) implements Command {
        @Override public CommandOp op() { return CommandOp.LOOT_CONTAINER; }
    }
    record Connector(int ordinal, Vec3i position, Direction front, Direction top, Joint joint,
            String name, String target, String pool, int placementPriority,
            int selectionPriority) { }
    record EntitySidecar(int ordinal, EntityType entityType, Vec3d position,
            Vec3i blockPosition) { }
    record SidecarIdentity(String identity, int occurrences) { }
    record TypedSidecars(List<SidecarIdentity> bent, List<SidecarIdentity> loot,
            List<SidecarIdentity> ents) {
        TypedSidecars {
            bent = List.copyOf(bent); loot = List.copyOf(loot); ents = List.copyOf(ents);
        }
    }

    private static final class JsonReader {
        private static final int MAX_STRING_CHARS = 32_768;
        private final String text;
        private int offset;

        private JsonReader(byte[] bytes) {
            try {
                text = StandardCharsets.UTF_8.newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT)
                        .decode(ByteBuffer.wrap(bytes)).toString();
            } catch (CharacterCodingException error) {
                throw invalid("Pillager Outpost grammar is not UTF-8");
            }
        }

        private void beginObject() { expect('{'); }
        private void endObject() { expect('}'); }
        private void beginArray() { expect('['); }
        private void endArray() { expect(']'); }

        private void field(String expected, int index) {
            skipWhitespace();
            if (index > 0) expectRaw(',');
            String actual = readString();
            if (!expected.equals(actual)) {
                throw invalid("Pillager Outpost JSON field order/schema drift: expected "
                        + expected + ", got " + actual);
            }
            expect(':');
        }

        private boolean nextArrayValue(int index) {
            skipWhitespace();
            if (index == 0) return peek() != ']';
            if (peek() == ']') return false;
            expectRaw(',');
            skipWhitespace();
            if (peek() == ']') throw invalid("Pillager Outpost JSON trailing array comma");
            return true;
        }

        private String readString() {
            skipWhitespace();
            expectRaw('"');
            StringBuilder value = new StringBuilder();
            while (offset < text.length()) {
                char current = text.charAt(offset++);
                if (current == '"') return value.toString();
                if (current < 0x20) throw invalid("Pillager Outpost JSON control character");
                if (current != '\\') {
                    value.append(current);
                } else {
                    if (offset >= text.length()) throw invalid("truncated Pillager Outpost JSON escape");
                    char escaped = text.charAt(offset++);
                    switch (escaped) {
                        case '"', '\\', '/' -> value.append(escaped);
                        case 'b' -> value.append('\b');
                        case 'f' -> value.append('\f');
                        case 'n' -> value.append('\n');
                        case 'r' -> value.append('\r');
                        case 't' -> value.append('\t');
                        case 'u' -> value.append(readUnicodeEscape());
                        default -> throw invalid("unknown Pillager Outpost JSON escape");
                    }
                }
                if (value.length() > MAX_STRING_CHARS) {
                    throw invalid("Pillager Outpost JSON string exceeds bound");
                }
            }
            throw invalid("truncated Pillager Outpost JSON string");
        }

        private char readUnicodeEscape() {
            if (offset + 4 > text.length()) throw invalid("truncated Pillager Outpost Unicode escape");
            int value = 0;
            for (int index = 0; index < 4; index++) {
                int digit = Character.digit(text.charAt(offset++), 16);
                if (digit < 0) throw invalid("malformed Pillager Outpost Unicode escape");
                value = value * 16 + digit;
            }
            return (char) value;
        }

        private int readInt() {
            String value = readNumber();
            require(value.indexOf('.') < 0 && value.indexOf('e') < 0 && value.indexOf('E') < 0,
                    "Pillager Outpost JSON integer type drift");
            try { return Integer.parseInt(value); }
            catch (NumberFormatException error) { throw invalid("Pillager Outpost integer overflow"); }
        }

        private long readLong() {
            String value = readNumber();
            require(value.indexOf('.') < 0 && value.indexOf('e') < 0 && value.indexOf('E') < 0,
                    "Pillager Outpost JSON long type drift");
            try { return Long.parseLong(value); }
            catch (NumberFormatException error) { throw invalid("Pillager Outpost long overflow"); }
        }

        private double readDouble() {
            String value = readNumber();
            try {
                double result = Double.parseDouble(value);
                require(Double.isFinite(result), "non-finite Pillager Outpost JSON number");
                return result;
            } catch (NumberFormatException error) {
                throw invalid("malformed Pillager Outpost JSON number");
            }
        }

        private String readNumber() {
            skipWhitespace();
            int start = offset;
            if (peek() == '-') offset++;
            if (offset >= text.length()) throw invalid("truncated Pillager Outpost JSON number");
            char first = text.charAt(offset);
            if (first == '0') {
                offset++;
                if (offset < text.length() && Character.isDigit(text.charAt(offset))) {
                    throw invalid("Pillager Outpost JSON leading zero");
                }
            } else if (first >= '1' && first <= '9') {
                do { offset++; } while (offset < text.length() && Character.isDigit(text.charAt(offset)));
            } else {
                throw invalid("malformed Pillager Outpost JSON number");
            }
            if (offset < text.length() && text.charAt(offset) == '.') {
                offset++;
                int fraction = offset;
                while (offset < text.length() && Character.isDigit(text.charAt(offset))) offset++;
                if (offset == fraction) throw invalid("malformed Pillager Outpost JSON fraction");
            }
            if (offset < text.length() && (text.charAt(offset) == 'e' || text.charAt(offset) == 'E')) {
                offset++;
                if (offset < text.length() && (text.charAt(offset) == '+' || text.charAt(offset) == '-')) offset++;
                int exponent = offset;
                while (offset < text.length() && Character.isDigit(text.charAt(offset))) offset++;
                if (offset == exponent) throw invalid("malformed Pillager Outpost JSON exponent");
            }
            return text.substring(start, offset);
        }

        private boolean readBoolean() {
            skipWhitespace();
            if (text.startsWith("true", offset)) { offset += 4; return true; }
            if (text.startsWith("false", offset)) { offset += 5; return false; }
            throw invalid("Pillager Outpost JSON boolean type drift");
        }

        private void expect(char expected) {
            skipWhitespace();
            expectRaw(expected);
        }

        private void expectRaw(char expected) {
            if (offset >= text.length() || text.charAt(offset) != expected) {
                throw invalid("Pillager Outpost JSON syntax drift near offset " + offset
                        + ": expected " + expected);
            }
            offset++;
        }

        private char peek() {
            return offset >= text.length() ? '\0' : text.charAt(offset);
        }

        private void skipWhitespace() {
            while (offset < text.length()) {
                char value = text.charAt(offset);
                if (value != ' ' && value != '\n' && value != '\r' && value != '\t') break;
                offset++;
            }
        }

        private void finish() {
            skipWhitespace();
            require(offset == text.length(), "trailing Pillager Outpost JSON data");
        }
    }
}
