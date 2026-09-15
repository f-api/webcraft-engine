package com.gameexpert.terrain.mc.structure;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.Set;

/**
 * Compact production authority for the pinned Minecraft 26.3-snapshot-7 Trial Chambers grammar.
 *
 * <p>The source artifacts are the authenticated verification corpora
 * {@code trial-chambers-execution-corpus-v1.json} (4,992,879 bytes),
 * {@code trial-chambers-bent-evidence-v2.json} and {@code trial-chambers-start-graph-v1.json},
 * which are test-only oracles. This authority is their distilled projection: it carries only the
 * class closure, pools, aliases, template programs, jigsaw connectors, canonical BENT verification
 * rows and the start-graph closure pins that production consumes, at template-local coordinates
 * and with no probe coordinate, bounding box, persisted NBT or RNG transcript. Receipt prose,
 * per-section digests and every fact {@link Mc263TrialChambersGrammar} already pins in Java are
 * dropped and reconstructed by {@link Mc263TrialChambersGrammar#assemble}, which then re-runs the
 * full accepted evidence closure over the reconstruction.</p>
 *
 * <p>The projection is produced by {@code scripts/mc-263/project_trial_chambers_production_authority.py}
 * under a dual-run identity check; the four source receipts live in the header so the runtime fails
 * closed on any source drift. Runtime code never consults the multi-megabyte verification corpora.</p>
 */
final class Mc263TrialChambersProductionAuthority {
    static final String RESOURCE = "/mc263/trial-chambers-production-authority-v1.txt";
    static final int RESOURCE_BYTES = 1_614_132;
    static final String RESOURCE_SHA256 =
            "ed4c25614f65c80fff192d589b52761d37f36f36825c6d19f1b45520d104ae78";
    static final int LINE_COUNT = 60_008;

    private static final String FORMAT = "TRL263P1";
    private static final int SCHEMA = 1;
    private static final int CLASS_PIN_COUNT = 21;
    private static final int POOL_COUNT = 47;
    private static final int ALIAS_COUNT = 3;
    private static final int TEMPLATE_COUNT = 191;
    private static final int COMMAND_COUNT = 56_475;
    private static final int BLOCK_COUNT = 264_296;
    private static final int CONNECTOR_COUNT = 834;
    private static final int BENT_ROW_COUNT = 45;
    private static final int BENT_RNG_DRAW_COUNT = 24;
    private static final int START_PROBE_COUNT = 2;
    private static final int START_PIECE_PIN_COUNT = 214;
    private static final int START_ALIAS_PIN_COUNT = 32;

    private static final String IGNORED_STRUCTURE_BLOCK_REASON =
            "SinglePoolElement prepends BlockIgnoreProcessor.STRUCTURE_BLOCK";
    private static final Mc263TrialChambersGrammar.CallerNextLongRng CALLER_NEXT_LONG =
            new Mc263TrialChambersGrammar.CallerNextLongRng(
                    "resulting block entity instanceof net.minecraft.world.RandomizableContainer",
                    "processed block survives clipping/processors and setBlock succeeds",
                    "LootTableSeed", "RandomSource.nextLong");
    private static final String BENT_RECEIPT_ID = "TRL-G1L-BENT-V2";
    private static final String BENT_RNG_KIND = "LegacyRandomSource";
    private static final String BENT_RNG_DRAW = "RandomSource.nextLong";
    private static final String BENT_ENCOUNTER_RULE =
            "one draw after processed block survives clipping/processors, setBlock succeeds, and resulting block entity is RandomizableContainer; value is written to LootTableSeed before loadWithComponents";
    private static final String BENT_BOUNDARY =
            "canonical COMPOUND preimages are runtime saveWithFullMetadata outputs rebuilt from the accepted semantic command payload at template-local coordinates; x/y/z and caller LootTableSeed are the only placement substitutions, and no raw template NBT is emitted";
    private static final Set<Mc263TrialChambersGrammar.BlockEntityType> RANDOMIZABLE = Set.of(
            Mc263TrialChambersGrammar.BlockEntityType.BARREL,
            Mc263TrialChambersGrammar.BlockEntityType.CHEST,
            Mc263TrialChambersGrammar.BlockEntityType.DISPENSER,
            Mc263TrialChambersGrammar.BlockEntityType.HOPPER);

    private Mc263TrialChambersProductionAuthority() { }

    /** The distilled start-graph closure pins consumed by the producer's evidence check. */
    record StartGraphPins(int probeCount, List<PiecePin> pieces, List<AliasPin> aliases) {
        StartGraphPins {
            pieces = List.copyOf(pieces);
            aliases = List.copyOf(aliases);
        }
    }

    /** One distinct accepted (template, processor list, processor order, projection, rotation). */
    record PiecePin(String template, String processorRegistryKey, List<String> processorOrder,
            String projection, String rotation) {
        PiecePin { processorOrder = List.copyOf(processorOrder); }
    }

    /** One distinct accepted (selected pool, resolved alias) connector edge. */
    record AliasPin(String selectedPool, String resolvedAlias) { }

    private static final class Holder {
        private static final Loaded LOADED = decode(read());
    }

    /** Loads the assembled, fully validated corpus. */
    static Mc263TrialChambersGrammar.Corpus load() {
        return Holder.LOADED.corpus();
    }

    /** Loads the distilled start-graph closure pins. */
    static StartGraphPins startGraphPins() {
        return Holder.LOADED.startGraphPins();
    }

    /** Decodes an externally supplied authority payload; verification path only. */
    static Loaded decodeForTest(byte[] bytes) {
        return decode(bytes);
    }

    private record Loaded(Mc263TrialChambersGrammar.Corpus corpus, StartGraphPins startGraphPins) { }

    private static byte[] read() {
        try (InputStream input =
                Mc263TrialChambersProductionAuthority.class.getResourceAsStream(RESOURCE)) {
            if (input == null) {
                throw invalid("Trial Chambers production authority is absent: " + RESOURCE);
            }
            return input.readAllBytes();
        } catch (IOException error) {
            throw invalid("failed to read the Trial Chambers production authority", error);
        }
    }

    private static Loaded decode(byte[] bytes) {
        Objects.requireNonNull(bytes, "Trial Chambers production authority bytes");
        require(bytes.length == RESOURCE_BYTES,
                "Trial Chambers production authority byte-count drift");
        require(RESOURCE_SHA256.equals(sha256(bytes)),
                "Trial Chambers production authority SHA-256 drift");
        Rows rows = new Rows(bytes);

        String[] header = rows.take("H", 11);
        require(FORMAT.equals(header[1]) && parseInt(header[2], "schema") == SCHEMA,
                "Trial Chambers production authority format drift");
        require(parseInt(header[3], "source corpus bytes") == Mc263TrialChambersGrammar.PAYLOAD_BYTES
                        && Mc263TrialChambersGrammar.PAYLOAD_SHA256.equals(header[4])
                        && parseInt(header[5], "source BENT bytes")
                                == Mc263TrialChambersGrammar.BENT_PAYLOAD_BYTES
                        && Mc263TrialChambersGrammar.BENT_PAYLOAD_SHA256.equals(header[6])
                        && parseInt(header[7], "source start-graph bytes")
                                == Mc263TrialChambersStartGraph.RESOURCE_BYTES
                        && Mc263TrialChambersStartGraph.RESOURCE_SHA256.equals(header[8])
                        && parseInt(header[9], "source reference-successor bytes")
                                == Mc263TrialChambersSettlement.REFERENCE_BYTES
                        && Mc263TrialChambersSettlement.REFERENCE_SHA256.equals(header[10]),
                "Trial Chambers production authority source receipt drift");

        List<Mc263TrialChambersGrammar.ClassPin> classPins = readClassPins(rows);
        List<Mc263TrialChambersGrammar.Pool> pools = readPools(rows);
        List<Mc263TrialChambersGrammar.AliasBinding> aliases = readAliases(rows);
        List<Mc263TrialChambersGrammar.Template> templates = readTemplates(rows);
        Mc263TrialChambersGrammar.BentCorpus bent = readBent(rows);
        StartGraphPins pins = readStartGraphPins(rows, templates);
        rows.finish();
        return new Loaded(
                Mc263TrialChambersGrammar.assemble(classPins, pools, aliases, templates, bent),
                pins);
    }

    private static List<Mc263TrialChambersGrammar.ClassPin> readClassPins(Rows rows) {
        ArrayList<Mc263TrialChambersGrammar.ClassPin> pins = new ArrayList<>(CLASS_PIN_COUNT);
        for (int index = 0; index < CLASS_PIN_COUNT; index++) {
            String[] row = rows.take("P", 3);
            pins.add(new Mc263TrialChambersGrammar.ClassPin(row[1], row[2]));
        }
        return List.copyOf(pins);
    }

    private static List<Mc263TrialChambersGrammar.Pool> readPools(Rows rows) {
        ArrayList<Mc263TrialChambersGrammar.Pool> pools = new ArrayList<>(POOL_COUNT);
        for (int poolIndex = 0; poolIndex < POOL_COUNT; poolIndex++) {
            String[] head = rows.take("K", 6);
            int rawElementCount = parseInt(head[3], "raw pool element count");
            int expandedWeight = parseInt(head[4], "expanded pool weight");
            int elementCount = parseInt(head[5], "pool element count");
            require(rawElementCount == elementCount && elementCount >= 0,
                    "Trial Chambers production pool cardinality drift");
            ArrayList<Mc263TrialChambersGrammar.PoolElement> elements =
                    new ArrayList<>(elementCount);
            int total = 0;
            for (int ordinal = 0; ordinal < elementCount; ordinal++) {
                String[] element = rows.take("E");
                require(element.length >= 5, "malformed Trial Chambers production pool element");
                require(parseInt(element[2], "pool element ordinal") == ordinal,
                        "Trial Chambers production pool element order drift");
                int weight = parseInt(element[3], "pool element weight");
                total = Math.addExact(total, weight);
                Mc263TrialChambersGrammar.Projection projection =
                        Mc263TrialChambersGrammar.Projection.from(element[4]);
                switch (element[1]) {
                    case "S" -> {
                        require(element.length == 8,
                                "malformed Trial Chambers production single pool element");
                        ArrayList<Mc263TrialChambersGrammar.ProcessorType> placement =
                                new ArrayList<>();
                        for (String value : csv(element[7])) {
                            placement.add(Mc263TrialChambersGrammar.ProcessorType.from(value));
                        }
                        elements.add(new Mc263TrialChambersGrammar.SingleElement(ordinal, weight,
                                projection, element[5],
                                Mc263TrialChambersGrammar.ProcessorListIdentity.from(element[6]),
                                placement));
                    }
                    case "X" -> {
                        require(element.length == 5,
                                "malformed Trial Chambers production empty pool element");
                        elements.add(new Mc263TrialChambersGrammar.EmptyElement(ordinal, weight,
                                projection));
                    }
                    default -> throw invalid(
                            "unknown Trial Chambers production pool element kind: " + element[1]);
                }
            }
            require(total == expandedWeight,
                    "Trial Chambers production expanded pool weight drift");
            pools.add(new Mc263TrialChambersGrammar.Pool(head[1], head[2], rawElementCount,
                    expandedWeight, elements));
        }
        return List.copyOf(pools);
    }

    private static List<Mc263TrialChambersGrammar.AliasBinding> readAliases(Rows rows) {
        ArrayList<Mc263TrialChambersGrammar.AliasBinding> aliases = new ArrayList<>(ALIAS_COUNT);
        for (int index = 0; index < ALIAS_COUNT; index++) {
            String[] head = rows.take("B");
            require(head.length >= 6, "malformed Trial Chambers production alias binding");
            int ordinal = parseInt(head[2], "alias ordinal");
            require(ordinal == index, "Trial Chambers production alias declared-order drift");
            List<String> targets = csv(head[4]);
            switch (head[1]) {
                case "G" -> {
                    require(head.length == 6,
                            "malformed Trial Chambers production random-group alias");
                    int groupCount = parseInt(head[5], "alias group count");
                    ArrayList<Mc263TrialChambersGrammar.AliasGroup> groups =
                            new ArrayList<>(groupCount);
                    for (int group = 0; group < groupCount; group++) {
                        String[] row = rows.take("Y", 3);
                        int weight = parseInt(row[1], "alias group weight");
                        ArrayList<Mc263TrialChambersGrammar.DirectAlias> data = new ArrayList<>();
                        for (String pair : row[2].split(";", -1)) {
                            int split = pair.indexOf('=');
                            require(split > 0 && split < pair.length() - 1,
                                    "malformed Trial Chambers production direct alias: " + pair);
                            data.add(new Mc263TrialChambersGrammar.DirectAlias(
                                    pair.substring(0, split), pair.substring(split + 1)));
                        }
                        groups.add(new Mc263TrialChambersGrammar.AliasGroup(data, weight));
                    }
                    aliases.add(new Mc263TrialChambersGrammar.RandomGroupAlias(ordinal, head[3],
                            targets, groups));
                }
                case "R" -> {
                    require(head.length == 7, "malformed Trial Chambers production random alias");
                    int targetCount = parseInt(head[6], "alias target count");
                    ArrayList<Mc263TrialChambersGrammar.WeightedTarget> weighted =
                            new ArrayList<>(targetCount);
                    for (int target = 0; target < targetCount; target++) {
                        String[] row = rows.take("Y", 3);
                        weighted.add(new Mc263TrialChambersGrammar.WeightedTarget(row[2],
                                parseInt(row[1], "alias target weight")));
                    }
                    aliases.add(new Mc263TrialChambersGrammar.RandomAlias(ordinal, head[3], targets,
                            head[5], weighted));
                }
                default -> throw invalid(
                        "unknown Trial Chambers production alias kind: " + head[1]);
            }
        }
        return List.copyOf(aliases);
    }

    private static List<Mc263TrialChambersGrammar.Template> readTemplates(Rows rows) {
        ArrayList<Mc263TrialChambersGrammar.Template> templates = new ArrayList<>(TEMPLATE_COUNT);
        int totalCommands = 0;
        int totalBlocks = 0;
        int totalConnectors = 0;
        for (int index = 0; index < TEMPLATE_COUNT; index++) {
            String[] head = rows.take("T", 9);
            String key = head[1];
            Mc263TrialChambersGrammar.Vec3i size = new Mc263TrialChambersGrammar.Vec3i(
                    parseInt(head[2], "template size x"), parseInt(head[3], "template size y"),
                    parseInt(head[4], "template size z"));
            int blockCount = parseInt(head[5], "template block count");
            int stateCount = parseInt(head[6], "template state count");
            int commandCount = parseInt(head[7], "template command count");
            int connectorCount = parseInt(head[8], "template connector count");
            require(size.x() > 0 && size.y() > 0 && size.z() > 0 && blockCount > 0
                            && stateCount > 0 && commandCount > 0 && connectorCount >= 0,
                    "Trial Chambers production template shape drift: " + key);

            ArrayList<String> states = new ArrayList<>(stateCount);
            for (int state = 0; state < stateCount; state++) states.add(rows.take("S", 2)[1]);
            ArrayList<Mc263TrialChambersGrammar.Command> commands = new ArrayList<>(commandCount);
            int expanded = 0;
            for (int command = 0; command < commandCount; command++) {
                Mc263TrialChambersGrammar.Command parsed = readCommand(rows, stateCount);
                expanded = Math.addExact(expanded, parsed.expandedCount());
                commands.add(parsed);
            }
            require(expanded == blockCount,
                    "Trial Chambers production arithmetic command grammar is lossy: " + key);
            ArrayList<Mc263TrialChambersGrammar.Connector> connectors =
                    new ArrayList<>(connectorCount);
            for (int connector = 0; connector < connectorCount; connector++) {
                String[] row = rows.take("N", 13);
                require(parseInt(row[1], "connector ordinal") == connector,
                        "Trial Chambers production connector order drift");
                connectors.add(new Mc263TrialChambersGrammar.Connector(connector,
                        new Mc263TrialChambersGrammar.Vec3i(parseInt(row[2], "connector x"),
                                parseInt(row[3], "connector y"), parseInt(row[4], "connector z")),
                        Mc263TrialChambersGrammar.Direction.from(row[5]),
                        Mc263TrialChambersGrammar.Direction.from(row[6]),
                        Mc263TrialChambersGrammar.Joint.from(row[7]), row[8], row[9], row[10],
                        parseInt(row[11], "connector placement priority"),
                        parseInt(row[12], "connector selection priority")));
            }
            totalCommands = Math.addExact(totalCommands, commandCount);
            totalBlocks = Math.addExact(totalBlocks, blockCount);
            totalConnectors = Math.addExact(totalConnectors, connectorCount);
            templates.add(new Mc263TrialChambersGrammar.Template(key, size, states, blockCount,
                    commandCount, commands, connectorCount, connectors, 0, List.of()));
        }
        require(totalCommands == COMMAND_COUNT && totalBlocks == BLOCK_COUNT
                        && totalConnectors == CONNECTOR_COUNT,
                "Trial Chambers production template aggregate drift");
        return List.copyOf(templates);
    }

    private static Mc263TrialChambersGrammar.Command readCommand(Rows rows, int stateCount) {
        String[] row = rows.takeAny();
        require(row.length > 0, "empty Trial Chambers production command row");
        int ordinal = row.length > 1 ? parseInt(row[1], "command ordinal") : -1;
        require(ordinal >= 0, "Trial Chambers production command ordinal drift");
        switch (row[0]) {
            case "R" -> {
                require(row.length == 10, "malformed Trial Chambers production RUN");
                int count = parseInt(row[8], "RUN count");
                require(count > 0, "Trial Chambers production RUN count drift");
                return new Mc263TrialChambersGrammar.Run(ordinal, vec(row, 2), vec(row, 5), count,
                        state(row[9], stateCount));
            }
            case "J" -> {
                require(row.length == 5, "malformed Trial Chambers production JIGSAW");
                return new Mc263TrialChambersGrammar.Jigsaw(ordinal, state(row[2], stateCount),
                        parseInt(row[3], "JIGSAW connector ordinal"), row[4]);
            }
            case "I" -> {
                require(row.length == 6,
                        "malformed Trial Chambers production IGNORED_STRUCTURE_BLOCK");
                return new Mc263TrialChambersGrammar.IgnoredStructureBlock(ordinal, vec(row, 2),
                        state(row[5], stateCount), IGNORED_STRUCTURE_BLOCK_REASON);
            }
            case "O" -> {
                require(row.length == 9 || row.length == 13,
                        "malformed Trial Chambers production LOOT_CONTAINER");
                Mc263TrialChambersGrammar.BlockEntityType type =
                        Mc263TrialChambersGrammar.BlockEntityType.from(row[6]);
                Optional<Mc263TrialChambersGrammar.PotSherds> sherds =
                        type == Mc263TrialChambersGrammar.BlockEntityType.DECORATED_POT
                                ? Optional.of(sherds(row, 9)) : Optional.empty();
                require(sherds.isPresent() == (row.length == 13),
                        "Trial Chambers production LOOT_CONTAINER sherd binding drift");
                return new Mc263TrialChambersGrammar.LootContainer(ordinal, vec(row, 2),
                        state(row[5], stateCount), type,
                        Mc263TrialChambersGrammar.LootTable.from(row[7]), rng(row[8], type), sherds);
            }
            case "F" -> {
                require(row.length >= 9, "malformed Trial Chambers production FIXED_CONTAINER");
                Mc263TrialChambersGrammar.BlockEntityType type =
                        Mc263TrialChambersGrammar.BlockEntityType.from(row[6]);
                int itemCount = parseInt(row[8], "fixed-container item count");
                boolean hopper = type == Mc263TrialChambersGrammar.BlockEntityType.HOPPER;
                require(row.length == 9 + itemCount + (hopper ? 1 : 0),
                        "Trial Chambers production FIXED_CONTAINER field-count drift");
                ArrayList<Mc263TrialChambersGrammar.SlotItem> items = new ArrayList<>(itemCount);
                for (int index = 0; index < itemCount; index++) {
                    String[] parts = row[9 + index].split(":", -1);
                    require(parts.length == 4,
                            "malformed Trial Chambers production fixed-container item");
                    items.add(new Mc263TrialChambersGrammar.SlotItem(
                            parseInt(parts[0], "fixed-container slot"),
                            Mc263TrialChambersGrammar.ItemIdentity.from(parts[1] + ":" + parts[2]),
                            parseInt(parts[3], "fixed-container item count")));
                }
                OptionalInt cooldown = hopper
                        ? OptionalInt.of(parseInt(row[row.length - 1], "hopper transfer cooldown"))
                        : OptionalInt.empty();
                return new Mc263TrialChambersGrammar.FixedContainer(ordinal, vec(row, 2),
                        state(row[5], stateCount), type, items, rng(row[7], type), cooldown);
            }
            case "C" -> {
                require(row.length == 13, "malformed Trial Chambers production DECORATED_POT");
                return new Mc263TrialChambersGrammar.DecoratedPot(ordinal, vec(row, 2),
                        state(row[5], stateCount),
                        Mc263TrialChambersGrammar.BlockEntityType.from(row[6]),
                        new Mc263TrialChambersGrammar.ItemStack(
                                Mc263TrialChambersGrammar.ItemIdentity.from(row[7]),
                                parseInt(row[8], "decorated-pot item count")),
                        sherds(row, 9));
            }
            case "V" -> {
                require(row.length == 10, "malformed Trial Chambers production VAULT");
                return new Mc263TrialChambersGrammar.Vault(ordinal, vec(row, 2),
                        state(row[5], stateCount),
                        Mc263TrialChambersGrammar.BlockEntityType.from(row[6]),
                        new Mc263TrialChambersGrammar.ItemStack(
                                Mc263TrialChambersGrammar.ItemIdentity.from(row[7]),
                                parseInt(row[8], "vault key count")),
                        Mc263TrialChambersGrammar.LootTable.from(row[9]));
            }
            case "Z" -> {
                require(row.length == 8, "malformed Trial Chambers production TRIAL_SPAWNER");
                return new Mc263TrialChambersGrammar.TrialSpawner(ordinal, vec(row, 2),
                        state(row[5], stateCount),
                        Mc263TrialChambersGrammar.BlockEntityType.from(row[6]),
                        new Mc263TrialChambersGrammar.TrialSpawnerConfig(row[7]));
            }
            default -> throw invalid(
                    "unknown Trial Chambers production command opcode: " + row[0]);
        }
    }

    private static Mc263TrialChambersGrammar.BentCorpus readBent(Rows rows) {
        String[] head = rows.take("b", 3);
        long probeSeed = parseLong(head[1], "BENT probe seed");
        int drawCount = parseInt(head[2], "BENT caller draw count");
        require(drawCount == BENT_RNG_DRAW_COUNT, "Trial Chambers production BENT draw drift");
        ArrayList<Mc263TrialChambersGrammar.BentRow> bentRows = new ArrayList<>(BENT_ROW_COUNT);
        int draws = 0;
        for (int index = 0; index < BENT_ROW_COUNT; index++) {
            String[] row = rows.take("n", 8);
            Mc263TrialChambersGrammar.BlockEntityType type =
                    Mc263TrialChambersGrammar.BlockEntityType.from(row[3]);
            OptionalInt encounter = OptionalInt.empty();
            OptionalLong preimage = OptionalLong.empty();
            if (RANDOMIZABLE.contains(type)) {
                encounter = OptionalInt.of(parseInt(row[4], "BENT caller encounter ordinal"));
                preimage = OptionalLong.of(parseLong(row[5], "BENT caller nextLong preimage"));
                require(encounter.getAsInt() == draws++,
                        "Trial Chambers production BENT caller RNG encounter-order drift");
            } else {
                require("-".equals(row[4]) && "-".equals(row[5]),
                        "Trial Chambers production non-randomizable BENT claimed a caller draw");
            }
            bentRows.add(new Mc263TrialChambersGrammar.BentRow(row[1],
                    parseInt(row[2], "BENT command ordinal"), type, encounter, preimage,
                    parseInt(row[6], "BENT canonical NBT length"), row[7]));
        }
        require(draws == BENT_RNG_DRAW_COUNT,
                "Trial Chambers production BENT caller RNG cardinality drift");
        return new Mc263TrialChambersGrammar.BentCorpus(2, BENT_RECEIPT_ID, 25, "26.3 Snapshot 7",
                Mc263TrialChambersGrammar.RECEIPT_ID, Mc263TrialChambersGrammar.PAYLOAD_SHA256,
                new Mc263TrialChambersGrammar.BentCallerRng(BENT_RNG_KIND, probeSeed, BENT_RNG_DRAW,
                        drawCount, BENT_ENCOUNTER_RULE),
                bentRows, BENT_BOUNDARY);
    }

    private static StartGraphPins readStartGraphPins(Rows rows,
            List<Mc263TrialChambersGrammar.Template> templates) {
        String[] head = rows.take("g", 4);
        int probeCount = parseInt(head[1], "start-graph probe count");
        int pieceCount = parseInt(head[2], "start-graph piece pin count");
        int aliasCount = parseInt(head[3], "start-graph alias pin count");
        require(probeCount == START_PROBE_COUNT && pieceCount == START_PIECE_PIN_COUNT
                        && aliasCount == START_ALIAS_PIN_COUNT,
                "Trial Chambers production start-graph pin cardinality drift");
        LinkedHashSet<String> templateKeys = new LinkedHashSet<>();
        for (Mc263TrialChambersGrammar.Template template : templates) templateKeys.add(template.key());
        ArrayList<PiecePin> pieces = new ArrayList<>(pieceCount);
        LinkedHashSet<PiecePin> unique = new LinkedHashSet<>();
        for (int index = 0; index < pieceCount; index++) {
            String[] row = rows.take("p", 6);
            require(templateKeys.contains(row[1]),
                    "Trial Chambers production start-graph pin names an unknown template: " + row[1]);
            PiecePin pin = new PiecePin(row[1], row[2], csv(row[3]), row[4], row[5]);
            require(unique.add(pin), "duplicate Trial Chambers production start-graph piece pin");
            pieces.add(pin);
        }
        ArrayList<AliasPin> aliases = new ArrayList<>(aliasCount);
        LinkedHashSet<AliasPin> uniqueAliases = new LinkedHashSet<>();
        for (int index = 0; index < aliasCount; index++) {
            String[] row = rows.take("a", 3);
            AliasPin pin = new AliasPin(row[1], row[2]);
            require(uniqueAliases.add(pin),
                    "duplicate Trial Chambers production start-graph alias pin");
            aliases.add(pin);
        }
        return new StartGraphPins(probeCount, pieces, aliases);
    }

    private static Mc263TrialChambersGrammar.PlacementLootSeedRng rng(String flag,
            Mc263TrialChambersGrammar.BlockEntityType type) {
        if ("C".equals(flag)) {
            require(RANDOMIZABLE.contains(type),
                    "Trial Chambers production non-randomizable block entity claimed a caller draw");
            return CALLER_NEXT_LONG;
        }
        require("N".equals(flag),
                "unknown Trial Chambers production placement loot-seed RNG flag: " + flag);
        require(!RANDOMIZABLE.contains(type),
                "Trial Chambers production randomizable block entity declined its caller draw");
        return Mc263TrialChambersGrammar.NoPlacementLootSeedRng.INSTANCE;
    }

    private static Mc263TrialChambersGrammar.PotSherds sherds(String[] row, int offset) {
        return new Mc263TrialChambersGrammar.PotSherds(
                Mc263TrialChambersGrammar.PotSherd.from(row[offset]),
                Mc263TrialChambersGrammar.PotSherd.from(row[offset + 1]),
                Mc263TrialChambersGrammar.PotSherd.from(row[offset + 2]),
                Mc263TrialChambersGrammar.PotSherd.from(row[offset + 3]));
    }

    private static Mc263TrialChambersGrammar.Vec3i vec(String[] row, int offset) {
        return new Mc263TrialChambersGrammar.Vec3i(parseInt(row[offset], "vector x"),
                parseInt(row[offset + 1], "vector y"), parseInt(row[offset + 2], "vector z"));
    }

    private static int state(String value, int stateCount) {
        int index = parseInt(value, "state index");
        require(index >= 0 && index < stateCount,
                "Trial Chambers production state index outside the template state table");
        return index;
    }

    private static List<String> csv(String value) {
        if (value.isEmpty()) return List.of();
        return List.of(value.split(",", -1));
    }

    private static int parseInt(String value, String label) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException error) {
            throw invalid("invalid Trial Chambers production " + label, error);
        }
    }

    private static long parseLong(String value, String label) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException error) {
            throw invalid("invalid Trial Chambers production " + label, error);
        }
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw invalid(message);
    }

    private static IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException(message);
    }

    private static IllegalArgumentException invalid(String message, Throwable cause) {
        return new IllegalArgumentException(message, cause);
    }

    private static final class Rows {
        private final String[] lines;
        private int index;

        Rows(byte[] bytes) {
            String text = new String(bytes, StandardCharsets.UTF_8);
            require(text.endsWith("\n") && text.indexOf('\r') < 0,
                    "Trial Chambers production authority newline drift");
            lines = text.substring(0, text.length() - 1).split("\n", -1);
            require(lines.length == LINE_COUNT,
                    "Trial Chambers production authority line-count drift");
        }

        String[] take(String tag) {
            String[] row = takeAny();
            require(row.length > 0 && tag.equals(row[0]),
                    "Trial Chambers production authority row-order drift; expected " + tag);
            return row;
        }

        String[] take(String tag, int fields) {
            String[] row = take(tag);
            require(row.length == fields,
                    "Trial Chambers production authority field-count drift for " + tag);
            return row;
        }

        String[] takeAny() {
            require(index < lines.length, "Trial Chambers production authority ended early");
            return lines[index++].split("\t", -1);
        }

        void finish() {
            require(index == lines.length,
                    "Trial Chambers production authority has trailing rows");
        }
    }
}
