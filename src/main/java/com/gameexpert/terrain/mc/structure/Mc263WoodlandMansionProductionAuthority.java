package com.gameexpert.terrain.mc.structure;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/**
 * Java reader for the shared coordinate-free Woodland Mansion execution authority
 * ({@code mc263/woodland-mansion-production-authority-v1.txt}, format {@code WMN263P1}).
 *
 * <p>The resource is projected by
 * {@code original/scripts/mc-263/project_woodland_mansion_production_authority.py} out of the
 * authenticated Mansion corpora and is already consumed by the Rust runtime
 * ({@code original/client/wasm/src/mc_woodland_mansion_production_authority_263.rs}). This class is
 * the byte-identical Java mirror of that loader: the same pinned receipts, the same row grammar, the
 * same fail-closed gates and the same derived closures, so Spring and standalone read one authority
 * rather than two transcriptions.</p>
 *
 * <p><b>Row grammar</b>, in projection order: {@code H} header receipts, {@code M} metrics,
 * {@code E} BENT provenance, {@code V} directional provenance, {@code F} transform algebra,
 * {@code W} placement settings, {@code P} sole processor, {@code I} ignored source state, {@code X}
 * exact-state closure, {@code Z} door/rail mirror-before-rotation closure, {@code Q} keepLiquids
 * waterlogged-restore closure, {@code N} marker-chest
 * facing table, {@code Y} stair mirror closure, {@code K} directional rules, then per template a
 * {@code T} head, its {@code S} palette and its {@code R} /
 * {@code D} command program, then the {@code B} canonical post-placement block-entity preimages, the
 * {@code C} marker-chest cells and the {@code A} aggregate tail.</p>
 *
 * <p><b>Forward compatibility.</b> Parsing is by row tag and fails closed on any tag this reader does
 * not know: the projector is the single writer of the resource, and a new row tag always carries new
 * authenticated semantics that production must not silently ignore. Extending the projection
 * therefore requires an explicit reader change here and in the Rust loader, and the pinned
 * {@link #AUTHORITY_BYTES} / {@link #AUTHORITY_SHA256} receipts make that drift impossible to miss.
 * The already-landed marker-chest lane ({@code C}/{@code N}/{@code Y}/{@code K} rows) is parsed
 * here.</p>
 *
 * <p>Every coordinate in the resource is template local; no bounded witness coordinate, chunk or
 * world seed exists in the payload, so this reader stays coordinate-free.</p>
 */
public final class Mc263WoodlandMansionProductionAuthority {
    public static final String RESOURCE = "mc263/woodland-mansion-production-authority-v1.txt";
    public static final int AUTHORITY_BYTES = 480_685;
    public static final String AUTHORITY_SHA256 =
            "24dda95d3196d4ac1b4d0158d2fcfa435ca0387888292eb7ee310ebf48bdcc46";

    /** {@code woodland-mansion-code-grammar-v1.json}, the authenticated command-program corpus. */
    public static final long SOURCE_GRAMMAR_BYTES = 1_424_424L;
    public static final String SOURCE_GRAMMAR_FILE_SHA256 =
            "cdc45615029aad2bf39674e4761a44fbf2a1bc25f19eb0abd1e47a073439382e";
    public static final String SOURCE_GRAMMAR_SHA256 =
            "b532d2b62e063d83bd7dbf8886c3bc1f2ef3810cfd74414d424cc0469b720cf9";
    /** {@code woodland-mansion-procedural-settlement-authority-v1.json}. */
    public static final long SOURCE_SETTLEMENT_BYTES = 45_010L;
    public static final String SOURCE_SETTLEMENT_SHA256 =
            "cd9757f9c593a31f558ed57f855ac638fb9f00652273083837e9e1c2ee3abd93";
    /** {@code woodland-mansion-settlement-evidence-v2.json}, the post-placement BENT closure. */
    public static final long SOURCE_EVIDENCE_BYTES = 274_492L;
    public static final String SOURCE_EVIDENCE_SHA256 =
            "98f2884e19fbe95679347c7cd1e55e70a8ee3dbc12f8d894be058164e646e03f";
    /** {@code mc263-directional-mirror-closure-v1.json}, the official mirror/marker closure. */
    public static final long SOURCE_DIRECTIONAL_BYTES = 311_718L;
    public static final String SOURCE_DIRECTIONAL_SHA256 =
            "8b4b28c1b9378a9aaf5e516ed9bbb14a0a21b2bed50e4f650046225651aa37db";

    public static final String FORMAT = "WMN263P1";
    public static final long SCHEMA = 1L;
    public static final int TEMPLATE_COUNT = 73;
    public static final long COMMAND_COUNT = 11_480L;
    public static final long RUN_COUNT = 11_362L;
    public static final long DATA_COUNT = 118L;
    public static final long BLOCK_COUNT = 66_365L;
    public static final long MARKER_COUNT = 38L;
    public static final int SOURCE_STATE_COUNT = 277;
    public static final int EXACT_STATE_COUNT = 417;
    /** One canonical post-placement preimage per non-marker DATA cell of the pinned catalog. */
    public static final int BENT_COUNT = 80;
    public static final int BENT_TEMPLATE_COUNT = 11;
    public static final int MARKER_CHEST_CELL_COUNT = 10;
    public static final int MARKER_CHEST_FACING_COUNT = 16;
    public static final int STAIR_MIRROR_COUNT = 152;
    public static final int DOOR_RAIL_TRANSFORM_COUNT = 48;
    /**
     * Official {@code shouldApplyWaterlogging} (keepLiquids) restore closure: one row per dry
     * waterloggable state this Mansion can write, carrying the exact successor
     * {@code SimpleWaterloggedBlock#placeLiquid} produces when the cell lands on retained water.
     * The one {@code smooth_stone_slab[type=double]} cell of the catalog is deliberately absent —
     * {@code SlabBlock#canPlaceLiquid} rejects it.
     */
    public static final int WATERLOGGED_RESTORE_COUNT = 255;
    public static final String SOLE_PROCESSOR = "net.minecraft.world.level.levelgen.structure"
            + ".templatesystem.BlockIgnoreProcessor:STRUCTURE_BLOCK";
    public static final String IGNORED_SOURCE_BLOCK = "minecraft:structure_block";
    public static final String MARKER_CHEST_LOOT_TABLE = "minecraft:chests/woodland_mansion";
    /**
     * A marker chest is written by {@code StructurePiece.createChest} with {@code setBlock} flags 2
     * after the whole template has been placed, so no {@code updateShape} runs and it is never paired.
     */
    public static final String MARKER_CHEST_TYPE = "single";
    public static final int MARKER_CHEST_WRITE_FLAGS = 2;
    public static final String TEMPLATE_PREFIX = "minecraft:woodland_mansion/";

    private static final List<String> TRANSFORM_KEYS = List.of("NONE", "CLOCKWISE_90",
            "CLOCKWISE_180", "COUNTERCLOCKWISE_90", "LEFT_RIGHT", "FRONT_BACK", "mirrorOrder");
    private static final List<String> SETTING_KEYS = List.of("ignoreEntities", "keepLiquids",
            "finalizeEntities", "knownShape", "nbtBarrierFlags", "writeFlags");
    private static final List<String> ROTATIONS = List.of("NONE", "CLOCKWISE_90", "CLOCKWISE_180",
            "COUNTERCLOCKWISE_90");
    /** The four marker names the pinned Mansion piece accepts, with their base (unrotated) facing. */
    private static final List<String> CHEST_MARKERS =
            List.of("ChestEast", "ChestNorth", "ChestSouth", "ChestWest");
    private static final List<String> CHEST_MARKER_FACING =
            List.of("east", "north", "south", "west");
    private static final List<String> RULE_KEYS = List.of("markerChestOrder",
            "markerChestFacingRule", "markerChestWriteFlags", "markerChestNeighbourResolution",
            "markerChestType", "markerChestTypeRule", "markerChestSkipRule", "markerChestLootTable",
            "markerChestLootSeed", "stairMirrorNONE", "stairMirrorLEFT_RIGHT",
            "stairMirrorFRONT_BACK");

    private final List<Entry> transform;
    private final List<Entry> settings;
    private final List<String> processors;
    private final List<String> ignoredStates;
    private final List<String> exactStates;
    private final List<DirectionalTransform> doorRailTransform;
    private final List<WaterloggedRestore> waterloggedRestore;
    private final List<MarkerChestFacing> markerChestFacing;
    private final List<StairMirror> stairMirror;
    private final List<Entry> rules;
    private final List<Template> templates;
    private final List<MarkerChestCell> markerChestCells;
    private final Aggregate aggregate;

    private Mc263WoodlandMansionProductionAuthority(List<Entry> transform, List<Entry> settings,
            List<String> processors, List<String> ignoredStates, List<String> exactStates,
            List<DirectionalTransform> doorRailTransform,
            List<WaterloggedRestore> waterloggedRestore,
            List<MarkerChestFacing> markerChestFacing, List<StairMirror> stairMirror,
            List<Entry> rules, List<Template> templates, List<MarkerChestCell> markerChestCells,
            Aggregate aggregate) {
        this.transform = List.copyOf(transform);
        this.settings = List.copyOf(settings);
        this.processors = List.copyOf(processors);
        this.ignoredStates = List.copyOf(ignoredStates);
        this.exactStates = List.copyOf(exactStates);
        this.doorRailTransform = List.copyOf(doorRailTransform);
        this.waterloggedRestore = List.copyOf(waterloggedRestore);
        this.markerChestFacing = List.copyOf(markerChestFacing);
        this.stairMirror = List.copyOf(stairMirror);
        this.rules = List.copyOf(rules);
        this.templates = List.copyOf(templates);
        this.markerChestCells = List.copyOf(markerChestCells);
        this.aggregate = aggregate;
    }

    /** Parses and validates the shared main resource once; the payload is read-only. */
    public static Mc263WoodlandMansionProductionAuthority pinned() { return Holder.VALUE; }

    private static final class Holder {
        private static final Mc263WoodlandMansionProductionAuthority VALUE = load();
    }

    private static Mc263WoodlandMansionProductionAuthority load() {
        byte[] payload;
        try (InputStream input = Mc263WoodlandMansionProductionAuthority.class.getClassLoader()
                .getResourceAsStream(RESOURCE)) {
            if (input == null) {
                throw new IllegalStateException("missing Mansion production authority: " + RESOURCE);
            }
            payload = input.readAllBytes();
        } catch (IOException error) {
            throw new IllegalStateException("cannot read Mansion production authority", error);
        }
        require(payload.length == AUTHORITY_BYTES,
                "Mansion production authority byte-count drift: " + payload.length);
        require(sha256(payload).equals(AUTHORITY_SHA256),
                "Mansion production authority SHA-256 drift");
        return parse(new String(payload, StandardCharsets.UTF_8));
    }

    public List<Entry> transform() { return transform; }
    public List<Entry> settings() { return settings; }
    public List<String> processors() { return processors; }
    public List<String> ignoredStates() { return ignoredStates; }
    /** Sorted-unique exact-state closure of the pinned Mansion template path. */
    public List<String> exactStates() { return exactStates; }
    public List<DirectionalTransform> doorRailTransform() { return doorRailTransform; }
    /** Official keepLiquids waterlogged-restore closure, sorted-unique by dry source state. */
    public List<WaterloggedRestore> waterloggedRestore() { return waterloggedRestore; }
    public List<MarkerChestFacing> markerChestFacing() { return markerChestFacing; }
    public List<StairMirror> stairMirror() { return stairMirror; }
    public List<Entry> rules() { return rules; }
    public List<Template> templates() { return templates; }
    public List<MarkerChestCell> markerChestCells() { return markerChestCells; }
    public Aggregate aggregate() { return aggregate; }

    public Template template(String id) {
        for (Template template : templates) {
            if (template.id().equals(id)) return template;
        }
        return null;
    }

    public Template requireTemplate(String id) {
        Template template = template(Objects.requireNonNull(id, "Mansion template identity"));
        if (template == null) {
            throw new IllegalArgumentException("unknown Mansion production template: " + id);
        }
        return template;
    }

    public boolean admitsExactState(String state) {
        if (Collections.binarySearch(exactStates, state) >= 0) return true;
        for (DirectionalTransform row : doorRailTransform) {
            if (row.result().equals(state)) return true;
        }
        for (WaterloggedRestore row : waterloggedRestore) {
            if (row.result().equals(state)) return true;
        }
        return false;
    }

    /**
     * Official {@code placeLiquid} successor a written Mansion cell takes when {@code keepLiquids}
     * re-floods it, or {@code null} when the official predicate admits no water for that state.
     */
    public String waterloggedRestoredState(String state) {
        for (WaterloggedRestore row : waterloggedRestore) {
            if (row.source().equals(state)) return row.result();
        }
        return null;
    }

    public String setting(String key) { return value(settings, key); }

    public String transformRule(String key) { return value(transform, key); }

    public String rule(String key) { return value(rules, key); }

    private static String value(List<Entry> entries, String key) {
        for (Entry entry : entries) {
            if (entry.key().equals(key)) return entry.value();
        }
        return null;
    }

    /**
     * Exact chest state the pinned marker branch writes for {@code marker} under {@code rotation}.
     * The placement mirror is deliberately absent: {@code handleDataMarker} reads only the rotation.
     */
    public String markerChestState(String marker, String rotation) {
        for (MarkerChestFacing row : markerChestFacing) {
            if (row.marker().equals(marker) && row.rotation().equals(rotation)) return row.state();
        }
        return null;
    }

    /**
     * Official {@code StairBlock#mirror} successor of an exact stair state; the state itself under
     * {@code NONE}. Returns {@code null} for a state outside the Mansion stair closure.
     */
    public String mirroredStairState(String state, String mirror) {
        int index = stairIndex(state);
        if (index < 0) return null;
        StairMirror row = stairMirror.get(index);
        return switch (mirror) {
            case "NONE" -> row.source();
            case "LEFT_RIGHT" -> row.leftRight();
            case "FRONT_BACK" -> row.frontBack();
            default -> null;
        };
    }

    /** Official mirror-before-rotation successor for a selected door or rail source state. */
    public String doorRailTransformedState(String state, String mirror, String rotation) {
        for (DirectionalTransform row : doorRailTransform) {
            if (row.source().equals(state) && row.mirror().equals(mirror)
                    && row.rotation().equals(rotation)) return row.result();
        }
        return null;
    }

    private int stairIndex(String state) {
        int low = 0, high = stairMirror.size() - 1;
        while (low <= high) {
            int middle = (low + high) >>> 1;
            int comparison = stairMirror.get(middle).source().compareTo(state);
            if (comparison == 0) return middle;
            if (comparison < 0) low = middle + 1; else high = middle - 1;
        }
        return -1;
    }

    /** Every {@code Chest*} structure-marker cell of one template, in command order. */
    public List<MarkerChestCell> markerChestCellsOf(String template) {
        ArrayList<MarkerChestCell> cells = new ArrayList<>();
        for (MarkerChestCell cell : markerChestCells) {
            if (cell.template().equals(template)) cells.add(cell);
        }
        return List.copyOf(cells);
    }

    // ------------------------------------------------------------------ parser

    public static Mc263WoodlandMansionProductionAuthority parse(String text) {
        Rows rows = new Rows(Objects.requireNonNull(text, "Mansion production authority text"));

        String[] header = rows.take("H", 8);
        require(header[1].equals(FORMAT) && number(header[2], "schema") == SCHEMA,
                "Mansion production authority format drift");
        require(number(header[3], "source grammar bytes") == SOURCE_GRAMMAR_BYTES
                        && header[4].equals(SOURCE_GRAMMAR_FILE_SHA256)
                        && header[5].equals(SOURCE_GRAMMAR_SHA256)
                        && number(header[6], "source settlement bytes") == SOURCE_SETTLEMENT_BYTES
                        && header[7].equals(SOURCE_SETTLEMENT_SHA256),
                "Mansion production authority source receipt drift");

        String[] metrics = rows.take("M", 10);
        require(number(metrics[1], "template count") == TEMPLATE_COUNT
                        && number(metrics[2], "command count") == COMMAND_COUNT
                        && number(metrics[3], "run count") == RUN_COUNT
                        && number(metrics[4], "data count") == DATA_COUNT
                        && number(metrics[5], "block count") == BLOCK_COUNT
                        && number(metrics[6], "marker count") == MARKER_COUNT
                        && number(metrics[7], "entity count") == 0L
                        && number(metrics[8], "source state count") == SOURCE_STATE_COUNT
                        && number(metrics[9], "exact state count") == EXACT_STATE_COUNT,
                "Mansion production authority metric drift");

        String[] evidence = rows.take("E", 5);
        require(number(evidence[1], "source evidence bytes") == SOURCE_EVIDENCE_BYTES
                        && evidence[2].equals(SOURCE_EVIDENCE_SHA256)
                        && number(evidence[3], "BENT preimage count") == BENT_COUNT
                        && number(evidence[4], "BENT template count") == BENT_TEMPLATE_COUNT,
                "Mansion production BENT provenance drift");

        String[] directional = rows.take("V", 8);
        require(number(directional[1], "source directional bytes") == SOURCE_DIRECTIONAL_BYTES
                        && directional[2].equals(SOURCE_DIRECTIONAL_SHA256)
                        && number(directional[3], "marker chest cell count") == MARKER_CHEST_CELL_COUNT
                        && number(directional[4], "marker chest facing count")
                                == MARKER_CHEST_FACING_COUNT
                        && number(directional[5], "stair mirror count") == STAIR_MIRROR_COUNT
                        && number(directional[6], "rule count") == RULE_KEYS.size()
                        && number(directional[7], "waterlogged restore count")
                                == WATERLOGGED_RESTORE_COUNT,
                "Mansion production directional provenance drift");

        ArrayList<Entry> transform = new ArrayList<>(TRANSFORM_KEYS.size());
        for (String key : TRANSFORM_KEYS) {
            String[] row = rows.take("F", 3);
            require(row[1].equals(key), "Mansion production transform order drift");
            transform.add(new Entry(row[1], row[2]));
        }
        ArrayList<Entry> settings = new ArrayList<>(SETTING_KEYS.size());
        for (String key : SETTING_KEYS) {
            String[] row = rows.take("W", 3);
            require(row[1].equals(key), "Mansion production settings order drift");
            settings.add(new Entry(row[1], row[2]));
        }
        String[] processor = rows.take("P", 2);
        require(processor[1].equals(SOLE_PROCESSOR), "Mansion production processor drift");
        String[] ignored = rows.take("I", 2);
        require(ignored[1].equals(IGNORED_SOURCE_BLOCK), "Mansion production ignored-state drift");

        ArrayList<String> exactStates = new ArrayList<>(EXACT_STATE_COUNT);
        for (int index = 0; index < EXACT_STATE_COUNT; index++) {
            String[] row = rows.take("X", 2);
            require(exactStates.isEmpty() || row[1].compareTo(exactStates.getLast()) > 0,
                    "Mansion production exact-state closure is not sorted-unique");
            exactStates.add(row[1]);
        }

        // Official mirror-before-rotation closure for the two dark-oak-door source states and four
        // rail source states that escape the accepted rotation-only X catalog.
        ArrayList<DirectionalTransform> doorRailTransform =
                new ArrayList<>(DOOR_RAIL_TRANSFORM_COUNT);
        String previousTransformSource = null;
        int sourceTransformIndex = 0, transformSources = 0;
        int doorTransforms = 0, railTransforms = 0;
        for (int index = 0; index < DOOR_RAIL_TRANSFORM_COUNT; index++) {
            String[] row = rows.take("Z", 5);
            require(Collections.binarySearch(exactStates, row[1]) >= 0,
                    "Mansion door/rail transform source escapes the X closure");
            if (!row[1].equals(previousTransformSource)) {
                require(previousTransformSource == null || sourceTransformIndex == 8
                                && row[1].compareTo(previousTransformSource) > 0,
                        "Mansion door/rail transform source order drift");
                previousTransformSource = row[1];
                sourceTransformIndex = 0;
                transformSources++;
            }
            String expectedMirror = sourceTransformIndex < ROTATIONS.size()
                    ? "LEFT_RIGHT" : "FRONT_BACK";
            String expectedRotation = ROTATIONS.get(sourceTransformIndex % ROTATIONS.size());
            require(row[2].equals(expectedMirror) && row[3].equals(expectedRotation),
                    "Mansion door/rail transform key drift");
            String sourceBlock = blockKey(row[1]);
            require(blockKey(row[4]).equals(sourceBlock),
                    "Mansion door/rail transform changed block identity");
            if (sourceBlock.equals("minecraft:dark_oak_door")) {
                require(row[4].contains("hinge=left"),
                        "Mansion mirrored door did not flip its hinge");
                doorTransforms++;
            } else {
                require(sourceBlock.equals("minecraft:rail") && row[4].contains("shape="),
                        "Mansion directional transform row is not a selected door or rail");
                railTransforms++;
            }
            doorRailTransform.add(new DirectionalTransform(row[1], row[2], row[3], row[4]));
            sourceTransformIndex++;
        }
        require(sourceTransformIndex == 8 && transformSources == 6
                        && doorTransforms == 16 && railTransforms == 32,
                "Mansion door/rail transform family cardinality drift");

        // Official keepLiquids restore closure. A Mansion is placed with shouldApplyWaterlogging
        // enabled, so any dry waterloggable cell that lands on retained water is re-flooded in
        // place by LiquidBlockContainer#placeLiquid and emits a waterlogged=true state that no
        // palette, rotation or mirror closure can produce. Each source is either an X state or a
        // Z result — the two lanes are exactly the states the runtime can write.
        ArrayList<WaterloggedRestore> waterloggedRestore =
                new ArrayList<>(WATERLOGGED_RESTORE_COUNT);
        for (int index = 0; index < WATERLOGGED_RESTORE_COUNT; index++) {
            String[] row = rows.take("Q", 3);
            require(waterloggedRestore.isEmpty()
                            || row[1].compareTo(waterloggedRestore.getLast().source()) > 0,
                    "Mansion production waterlogged restore closure is not sorted-unique");
            require(row[1].contains(DRY_WATERLOGGED) && row[2].contains(WET_WATERLOGGED),
                    "Mansion production waterlogged restore row is not a dry/wet pair");
            require(row[2].equals(row[1].replace(DRY_WATERLOGGED, WET_WATERLOGGED)),
                    "Mansion production waterlogged restore successor drift");
            require(Collections.binarySearch(exactStates, row[1]) >= 0
                            || isDoorRailResult(doorRailTransform, row[1]),
                    "Mansion waterlogged restore source escapes the writable-state closure");
            require(Collections.binarySearch(exactStates, row[2]) < 0,
                    "Mansion waterlogged restore successor is already an exact state");
            waterloggedRestore.add(new WaterloggedRestore(row[1], row[2]));
        }

        // Marker-chest facing table: four accepted marker names x four rotations, marker-major.
        ArrayList<MarkerChestFacing> markerChestFacing = new ArrayList<>(MARKER_CHEST_FACING_COUNT);
        for (int marker = 0; marker < CHEST_MARKERS.size(); marker++) {
            for (String rotation : ROTATIONS) {
                String[] row = rows.take("N", 5);
                require(row[1].equals(CHEST_MARKERS.get(marker))
                                && row[2].equals(CHEST_MARKER_FACING.get(marker))
                                && row[3].equals(rotation),
                        "Mansion production marker-chest facing order drift");
                require(row[4].startsWith("minecraft:chest[facing=")
                                && row[4].contains("type=" + MARKER_CHEST_TYPE + ",")
                                && Collections.binarySearch(exactStates, row[4]) >= 0,
                        "Mansion production marker-chest state is not an admitted single chest");
                markerChestFacing.add(new MarkerChestFacing(row[1], row[2], row[3], row[4]));
            }
        }

        // Official StairBlock#mirror closure over every stair state of the exact-state closure.
        ArrayList<StairMirror> stairMirror = new ArrayList<>(STAIR_MIRROR_COUNT);
        for (int index = 0; index < STAIR_MIRROR_COUNT; index++) {
            String[] row = rows.take("Y", 4);
            require(stairMirror.isEmpty() || row[1].compareTo(stairMirror.getLast().source()) > 0,
                    "Mansion production stair mirror closure is not sorted-unique");
            for (int field = 1; field <= 3; field++) {
                require(row[field].contains("_stairs[") && row[field].contains("shape="),
                        "Mansion production stair mirror row is not a stair state");
                require(Collections.binarySearch(exactStates, row[field]) >= 0,
                        "Mansion production stair mirror state escapes the exact-state closure");
            }
            stairMirror.add(new StairMirror(row[1], row[2], row[3]));
        }

        ArrayList<Entry> rules = new ArrayList<>(RULE_KEYS.size());
        for (String key : RULE_KEYS) {
            String[] row = rows.take("K", 3);
            require(row[1].equals(key), "Mansion production rule order drift");
            require(!row[2].isEmpty(), "Mansion production rule carries no value");
            rules.add(new Entry(row[1], row[2]));
        }
        require(rules.get(4).value().equals(MARKER_CHEST_TYPE)
                        && rules.get(2).value().equals(Integer.toString(MARKER_CHEST_WRITE_FLAGS))
                        && rules.get(3).value().equals("none")
                        && rules.get(7).value().equals(MARKER_CHEST_LOOT_TABLE),
                "Mansion production marker-chest pairing outcome drift");

        ArrayList<Template> templates = new ArrayList<>(TEMPLATE_COUNT);
        long totalCommands = 0L, totalRuns = 0L, totalData = 0L, totalBlocks = 0L, totalMarkers = 0L;
        for (int index = 0; index < TEMPLATE_COUNT; index++) {
            String[] head = rows.take("T", 14);
            String id = head[1];
            require(id.startsWith(TEMPLATE_PREFIX), "Mansion production template identity drift");
            Local size = new Local(integer(head[2], "template size x"),
                    integer(head[3], "template size y"), integer(head[4], "template size z"));
            require(size.x() > 0 && size.y() > 0 && size.z() > 0,
                    "Mansion production template size drift");
            int stateCount = integer(head[5], "template state count");
            int commandCount = integer(head[6], "template command count");
            int runCount = integer(head[7], "template run count");
            int dataCount = integer(head[8], "template data count");
            int blockCount = integer(head[9], "template block count");
            int markerCount = integer(head[10], "template marker count");
            require(stateCount > 0 && commandCount == runCount + dataCount && markerCount >= 0,
                    "Mansion production template accounting drift");
            String grammarSha256 = shaField(head[11], "template grammar digest");
            int nbtBinaryLength = integer(head[12], "template NBT length");
            String nbtBinarySha256 = shaField(head[13], "template NBT digest");
            require(nbtBinaryLength > 0, "Mansion production template NBT receipt drift");

            ArrayList<String> states = new ArrayList<>(stateCount);
            for (int state = 0; state < stateCount; state++) {
                String[] row = rows.take("S", 2);
                require(!states.contains(row[1]),
                        "Mansion production template palette repeats a state");
                states.add(row[1]);
            }

            ArrayList<Command> commands = new ArrayList<>(commandCount);
            long expanded = 0L;
            int runs = 0, data = 0, previousOrdinal = -1;
            for (int command = 0; command < commandCount; command++) {
                String[] row = rows.takeAny();
                require(row.length >= 2,
                        "Mansion production authority carries a truncated command row");
                int ordinal = integer(row[1], "command ordinal");
                require(ordinal > previousOrdinal,
                        "Mansion production command ordinals are not strictly increasing");
                previousOrdinal = ordinal;
                switch (row[0]) {
                    case "R" -> {
                        require(row.length == 10, "malformed Mansion production RUN");
                        int count = integer(row[8], "RUN count");
                        int state = index(row[9], states.size(), "RUN state");
                        require(count > 0, "Mansion production RUN count must be positive");
                        commands.add(Command.run(ordinal,
                                new Local(integer(row[2], "RUN start x"),
                                        integer(row[3], "RUN start y"),
                                        integer(row[4], "RUN start z")),
                                new Local(integer(row[5], "RUN delta x"),
                                        integer(row[6], "RUN delta y"),
                                        integer(row[7], "RUN delta z")),
                                count, state));
                        expanded += count;
                        runs++;
                    }
                    case "D" -> {
                        require(row.length == 9, "malformed Mansion production DATA");
                        DataKind kind = DataKind.parse(row[6]);
                        require(!row[7].isEmpty(),
                                "Mansion production DATA carries no semantic identity");
                        String payload;
                        if (row[8].equals("-")) {
                            require(kind == DataKind.STRUCTURE_MARKER
                                            || kind == DataKind.EMPTY_CONTAINER,
                                    "Mansion production DATA payload is absent for a payload semantic");
                            payload = null;
                        } else {
                            require(kind != DataKind.STRUCTURE_MARKER
                                            && kind != DataKind.EMPTY_CONTAINER,
                                    "Mansion production DATA payload is present for a payload-free "
                                            + "semantic");
                            payload = row[8];
                        }
                        if (kind.isBlockEntity()) {
                            require(row[7].startsWith("minecraft:"),
                                    "Mansion production DATA block-entity type drift");
                        }
                        commands.add(Command.data(ordinal,
                                new Local(integer(row[2], "DATA x"), integer(row[3], "DATA y"),
                                        integer(row[4], "DATA z")),
                                index(row[5], states.size(), "DATA state"), kind, row[7], payload));
                        expanded++;
                        data++;
                    }
                    default -> throw new IllegalArgumentException(
                            "unknown Mansion production command opcode: " + row[0]);
                }
            }
            require(expanded == blockCount,
                    "Mansion production arithmetic command grammar is lossy");
            require(runs == runCount && data == dataCount,
                    "Mansion production command-kind accounting drift");

            totalCommands += commandCount;
            totalRuns += runCount;
            totalData += dataCount;
            totalBlocks += blockCount;
            totalMarkers += markerCount;
            templates.add(new Template(id, size, states, commands, blockCount, runCount, dataCount,
                    markerCount, grammarSha256, nbtBinaryLength, nbtBinarySha256));
        }

        // Canonical post-placement BENT preimages: one row per non-marker DATA cell, in template
        // order, each bound back to its own DATA command.
        int bentTemplates = 0, cursor = 0;
        for (int index = 0; index < BENT_COUNT; index++) {
            String[] bent = rows.take("B", 11);
            String identity = bent[1];
            while (cursor < templates.size() && !templates.get(cursor).id().equals(identity)) {
                cursor++;
            }
            require(cursor < templates.size(),
                    "Mansion production BENT row names an unknown or out-of-order template");
            int dataOrdinal = integer(bent[2], "BENT data ordinal");
            int bentOrdinal = integer(bent[3], "BENT closure ordinal");
            Local position = new Local(integer(bent[4], "BENT x"), integer(bent[5], "BENT y"),
                    integer(bent[6], "BENT z"));
            String blockEntityType = bent[7];
            int binaryLength = integer(bent[8], "BENT binary length");
            String binarySha256 = shaField(bent[9], "BENT preimage");
            byte[] binary = hexBytes(bent[10], "BENT preimage");
            require(binary.length == binaryLength && binary.length > 0,
                    "Mansion production BENT preimage length drift");
            require(sha256(binary).equals(binarySha256),
                    "Mansion production BENT preimage digest drift");
            require(binary[0] == 10,
                    "Mansion production BENT preimage root is not a COMPOUND tag");

            Template template = templates.get(cursor);
            Command command = template.command(dataOrdinal);
            require(command != null,
                    "Mansion production BENT row has no DATA command in " + identity);
            require(command.isData(),
                    "Mansion production BENT row binds a RUN command in " + identity);
            require(command.kind().isBlockEntity() && command.position().equals(position)
                            && command.primary().equals(blockEntityType),
                    "Mansion production BENT row does not bind its DATA cell");
            List<BlockEntityPreimage> landed = template.mutablePreimages();
            if (landed.isEmpty()) {
                bentTemplates++;
            } else {
                BlockEntityPreimage previous = landed.getLast();
                require(previous.dataOrdinal() < dataOrdinal
                                && previous.bentOrdinal() + 1 == bentOrdinal,
                        "Mansion production BENT rows are not in closure order");
            }
            require(bentOrdinal == landed.size(),
                    "Mansion production BENT closure ordinal drift");
            landed.add(new BlockEntityPreimage(dataOrdinal, bentOrdinal, position, blockEntityType,
                    binary, binarySha256));
        }
        require(bentTemplates == BENT_TEMPLATE_COUNT,
                "Mansion production BENT template coverage drift");
        for (Template template : templates) {
            int cells = 0;
            for (Command command : template.commands()) {
                if (command.isData() && command.kind().isBlockEntity()) cells++;
            }
            require(cells == template.blockEntityPreimages().size(),
                    "Mansion production block-entity DATA cell lacks its canonical preimage");
        }

        // Marker-chest cells: every row binds a real STRUCTURE_MARKER DATA command whose metadata is
        // one of the accepted `Chest*` names.
        ArrayList<MarkerChestCell> markerChestCells = new ArrayList<>(MARKER_CHEST_CELL_COUNT);
        cursor = 0;
        for (int index = 0; index < MARKER_CHEST_CELL_COUNT; index++) {
            String[] cell = rows.take("C", 11);
            String identity = cell[1];
            while (cursor < templates.size() && !templates.get(cursor).id().equals(identity)) {
                cursor++;
            }
            require(cursor < templates.size(),
                    "Mansion production marker-chest row names an unknown or out-of-order template");
            int dataOrdinal = integer(cell[2], "marker-chest data ordinal");
            Local position = new Local(integer(cell[3], "marker-chest x"),
                    integer(cell[4], "marker-chest y"), integer(cell[5], "marker-chest z"));
            int marker = CHEST_MARKERS.indexOf(cell[6]);
            require(marker >= 0, "unknown Mansion chest marker: " + cell[6]);
            require(cell[7].equals(CHEST_MARKER_FACING.get(marker)),
                    "Mansion production marker-chest base facing drift");
            require(cell[8].equals(MARKER_CHEST_TYPE)
                            && integer(cell[9], "marker-chest write flags") == MARKER_CHEST_WRITE_FLAGS
                            && cell[10].equals(MARKER_CHEST_LOOT_TABLE),
                    "Mansion production marker-chest pairing outcome drift");
            Command command = templates.get(cursor).command(dataOrdinal);
            require(command != null,
                    "Mansion production marker-chest row has no DATA command in " + identity);
            require(command.isData(),
                    "Mansion production marker-chest row binds a RUN command in " + identity);
            require(command.kind() == DataKind.STRUCTURE_MARKER
                            && command.position().equals(position)
                            && command.primary().equals(cell[6]),
                    "Mansion production marker-chest row does not bind its DATA cell");
            if (!markerChestCells.isEmpty()) {
                MarkerChestCell previous = markerChestCells.getLast();
                require(!previous.template().equals(identity)
                                || previous.dataOrdinal() < dataOrdinal,
                        "Mansion production marker-chest rows are not in command order");
            }
            markerChestCells.add(new MarkerChestCell(identity, dataOrdinal, position, cell[6],
                    CHEST_MARKER_FACING.get(marker), cell[8], MARKER_CHEST_WRITE_FLAGS, cell[10]));
        }
        int projectedMarkers = 0;
        for (Template template : templates) {
            for (Command command : template.commands()) {
                if (command.isData() && command.kind() == DataKind.STRUCTURE_MARKER
                        && command.primary().startsWith("Chest")) {
                    projectedMarkers++;
                }
            }
        }
        require(projectedMarkers == MARKER_CHEST_CELL_COUNT,
                "Mansion production chest marker coverage drift");

        String[] tail = rows.take("A", 9);
        Aggregate aggregate = new Aggregate(number(tail[1], "aggregate command count"),
                number(tail[2], "aggregate run count"), number(tail[3], "aggregate data count"),
                number(tail[4], "aggregate block count"), number(tail[5], "aggregate marker count"),
                shaField(tail[6], "template-ordered receipt"), shaField(tail[7], "oracle receipt"),
                shaField(tail[8], "compiler source receipt"));
        rows.finish();

        require(aggregate.commandCount() == totalCommands && aggregate.runCount() == totalRuns
                        && aggregate.dataCount() == totalData
                        && aggregate.blockCount() == totalBlocks
                        && aggregate.markerCount() == totalMarkers,
                "Mansion production aggregate drift");
        require(aggregate.commandCount() == COMMAND_COUNT && aggregate.runCount() == RUN_COUNT
                        && aggregate.dataCount() == DATA_COUNT
                        && aggregate.blockCount() == BLOCK_COUNT
                        && aggregate.markerCount() == MARKER_COUNT,
                "Mansion production aggregate identity drift");
        ArrayList<String> identities = new ArrayList<>(TEMPLATE_COUNT);
        ArrayList<String> sourceStates = new ArrayList<>(SOURCE_STATE_COUNT);
        for (Template template : templates) {
            if (!identities.contains(template.id())) identities.add(template.id());
            for (String state : template.states()) {
                if (!sourceStates.contains(state)) sourceStates.add(state);
            }
        }
        require(identities.size() == TEMPLATE_COUNT,
                "Mansion production template identities are not unique");
        require(sourceStates.size() == SOURCE_STATE_COUNT,
                "Mansion production source-state closure drift");

        return new Mc263WoodlandMansionProductionAuthority(transform, settings,
                List.of(processor[1]), List.of(ignored[1]), exactStates, doorRailTransform,
                waterloggedRestore, markerChestFacing,
                stairMirror, rules, templates, markerChestCells, aggregate);
    }

    /** Tab-separated row cursor with the projector's exact opcode/width grammar. */
    private static final class Rows {
        private final String[] lines;
        private int consumed;

        private Rows(String text) {
            String[] split = text.split("\n", -1);
            int length = split.length;
            if (length > 0 && split[length - 1].isEmpty()) length--;
            this.lines = java.util.Arrays.copyOf(split, length);
        }

        private String[] takeAny() {
            require(consumed < lines.length, "Mansion production authority ended early");
            String[] fields = lines[consumed++].split("\t", -1);
            require(fields.length > 0 && !fields[0].isEmpty(),
                    "Mansion production authority carries an empty row");
            return fields;
        }

        private String[] take(String opcode, int width) {
            String[] fields = takeAny();
            require(fields[0].equals(opcode) && fields.length == width,
                    "Mansion production authority expected " + opcode + " row of width " + width
                            + " at line " + consumed);
            return fields;
        }

        private void finish() {
            require(consumed == lines.length, "Mansion production authority carries trailing rows");
        }
    }

    private static String blockKey(String state) {
        int bracket = state.indexOf('[');
        return bracket < 0 ? state : state.substring(0, bracket);
    }

    // ------------------------------------------------------------------ value types

    /** Template-local integer position. */
    public static final class Local {
        private final int x, y, z;
        public Local(int x, int y, int z) { this.x = x; this.y = y; this.z = z; }
        public int x() { return x; }
        public int y() { return y; }
        public int z() { return z; }
        @Override public boolean equals(Object other) {
            return other instanceof Local value && x == value.x && y == value.y && z == value.z;
        }
        @Override public int hashCode() { return Objects.hash(x, y, z); }
        @Override public String toString() { return "[" + x + ", " + y + ", " + z + "]"; }
    }

    /** One authenticated key/value row of the transform, settings or rule tables. */
    public static final class Entry {
        private final String key, value;
        private Entry(String key, String value) { this.key = key; this.value = value; }
        public String key() { return key; }
        public String value() { return value; }
    }

    private static final String DRY_WATERLOGGED = "waterlogged=false";
    private static final String WET_WATERLOGGED = "waterlogged=true";

    private static boolean isDoorRailResult(List<DirectionalTransform> rows, String state) {
        for (DirectionalTransform row : rows) {
            if (row.result().equals(state)) return true;
        }
        return false;
    }

    /**
     * One official keepLiquids restore pair: the dry state a Mansion cell writes and the exact
     * successor {@code SimpleWaterloggedBlock#placeLiquid} writes over retained water.
     */
    public static final class WaterloggedRestore {
        private final String source, result;
        private WaterloggedRestore(String source, String result) {
            this.source = source; this.result = result;
        }
        public String source() { return source; }
        public String result() { return result; }
    }

    /** One official mirror-before-rotation door-hinge or rail-shape successor. */
    public static final class DirectionalTransform {
        private final String source, mirror, rotation, result;
        private DirectionalTransform(String source, String mirror, String rotation, String result) {
            this.source = source; this.mirror = mirror; this.rotation = rotation;
            this.result = result;
        }
        public String source() { return source; }
        public String mirror() { return mirror; }
        public String rotation() { return rotation; }
        public String result() { return result; }
    }

    /** Typed semantic of a {@code DATA} cell. */
    public enum DataKind {
        STRUCTURE_MARKER, EMPTY_CONTAINER, CONTAINER_ITEMS, PATTERNED_BANNER, MOB_SPAWNER;

        static DataKind parse(String value) {
            for (DataKind kind : values()) {
                if (kind.name().equals(value)) return kind;
            }
            throw new IllegalArgumentException("unknown Mansion production DATA kind: " + value);
        }

        public boolean isBlockEntity() { return this != STRUCTURE_MARKER; }
    }

    /** One {@code RUN} or {@code DATA} command of a template program, in authority order. */
    public static final class Command {
        private final boolean data;
        private final int ordinal;
        private final Local position, delta;
        private final int count, state;
        private final DataKind kind;
        private final String primary, payload;

        private Command(boolean data, int ordinal, Local position, Local delta, int count, int state,
                DataKind kind, String primary, String payload) {
            this.data = data; this.ordinal = ordinal; this.position = position; this.delta = delta;
            this.count = count; this.state = state; this.kind = kind; this.primary = primary;
            this.payload = payload;
        }

        private static Command run(int ordinal, Local start, Local delta, int count, int state) {
            return new Command(false, ordinal, start, delta, count, state, null, null, null);
        }

        private static Command data(int ordinal, Local position, int state, DataKind kind,
                String primary, String payload) {
            return new Command(true, ordinal, position, null, 1, state, kind, primary, payload);
        }

        public boolean isData() { return data; }
        public boolean isRun() { return !data; }
        public int ordinal() { return ordinal; }
        /** {@code RUN} start, or the {@code DATA} cell. */
        public Local position() { return position; }
        /** {@code RUN} step; {@code null} for a {@code DATA} cell. */
        public Local delta() { return delta; }
        /** {@code RUN} cell count; {@code 1} for a {@code DATA} cell. */
        public int count() { return count; }
        public int state() { return state; }
        /** {@code null} for a {@code RUN}. */
        public DataKind kind() { return kind; }
        /** Marker metadata for {@code STRUCTURE_MARKER}, otherwise the block-entity type. */
        public String primary() { return primary; }
        /** Canonical JSON of the typed payload, or {@code null}. */
        public String payload() { return payload; }
    }

    /** Canonical post-placement block-entity NBT of one template-local {@code DATA} cell. */
    public static final class BlockEntityPreimage {
        private final int dataOrdinal, bentOrdinal;
        private final Local position;
        private final String blockEntityType, binarySha256;
        private final byte[] binary;

        private BlockEntityPreimage(int dataOrdinal, int bentOrdinal, Local position,
                String blockEntityType, byte[] binary, String binarySha256) {
            this.dataOrdinal = dataOrdinal; this.bentOrdinal = bentOrdinal; this.position = position;
            this.blockEntityType = blockEntityType; this.binary = binary.clone();
            this.binarySha256 = binarySha256;
        }

        /** Ordinal of the owning {@code DATA} command inside its template program. */
        public int dataOrdinal() { return dataOrdinal; }
        /** Ordinal inside the evidence closure's per-template BENT sidecar. */
        public int bentOrdinal() { return bentOrdinal; }
        public Local position() { return position; }
        public String blockEntityType() { return blockEntityType; }
        public byte[] binary() { return binary.clone(); }
        public String binarySha256() { return binarySha256; }
    }

    /** One pinned Mansion template program. */
    public static final class Template {
        private final String id;
        private final Local size;
        private final List<String> states;
        private final List<Command> commands;
        private final int blockCount, runCount, dataCount, markerCount, nbtBinaryLength;
        private final String grammarSha256, nbtBinarySha256;
        private final ArrayList<BlockEntityPreimage> preimages = new ArrayList<>();

        private Template(String id, Local size, List<String> states, List<Command> commands,
                int blockCount, int runCount, int dataCount, int markerCount, String grammarSha256,
                int nbtBinaryLength, String nbtBinarySha256) {
            this.id = id; this.size = size; this.states = List.copyOf(states);
            this.commands = List.copyOf(commands); this.blockCount = blockCount;
            this.runCount = runCount; this.dataCount = dataCount; this.markerCount = markerCount;
            this.grammarSha256 = grammarSha256; this.nbtBinaryLength = nbtBinaryLength;
            this.nbtBinarySha256 = nbtBinarySha256;
        }

        public String id() { return id; }
        public Local size() { return size; }
        public List<String> states() { return states; }
        public List<Command> commands() { return commands; }
        public int blockCount() { return blockCount; }
        public int runCount() { return runCount; }
        public int dataCount() { return dataCount; }
        public int markerCount() { return markerCount; }
        public String grammarSha256() { return grammarSha256; }
        public int nbtBinaryLength() { return nbtBinaryLength; }
        public String nbtBinarySha256() { return nbtBinarySha256; }
        /** One entry per non-marker {@code DATA} cell, in template command order. */
        public List<BlockEntityPreimage> blockEntityPreimages() {
            return Collections.unmodifiableList(preimages);
        }

        private List<BlockEntityPreimage> mutablePreimages() { return preimages; }

        public String state(int index) {
            if (index < 0 || index >= states.size()) {
                throw new IllegalArgumentException(
                        "Mansion production state index out of range in " + id);
            }
            return states.get(index);
        }

        public Command command(int ordinal) {
            for (Command command : commands) {
                if (command.ordinal() == ordinal) return command;
            }
            return null;
        }

        /** Canonical post-placement NBT written by the {@code DATA} cell at {@code ordinal}. */
        public BlockEntityPreimage blockEntityPreimage(int ordinal) {
            for (BlockEntityPreimage preimage : preimages) {
                if (preimage.dataOrdinal() == ordinal) return preimage;
            }
            return null;
        }
    }

    /** Projected aggregate tail with the three source receipts of the grammar corpus. */
    public static final class Aggregate {
        private final long commandCount, runCount, dataCount, blockCount, markerCount;
        private final String templateOrderedReceiptSha256, oracleSha256, compilerSourceSha256;

        private Aggregate(long commandCount, long runCount, long dataCount, long blockCount,
                long markerCount, String templateOrderedReceiptSha256, String oracleSha256,
                String compilerSourceSha256) {
            this.commandCount = commandCount; this.runCount = runCount; this.dataCount = dataCount;
            this.blockCount = blockCount; this.markerCount = markerCount;
            this.templateOrderedReceiptSha256 = templateOrderedReceiptSha256;
            this.oracleSha256 = oracleSha256; this.compilerSourceSha256 = compilerSourceSha256;
        }

        public long commandCount() { return commandCount; }
        public long runCount() { return runCount; }
        public long dataCount() { return dataCount; }
        public long blockCount() { return blockCount; }
        public long markerCount() { return markerCount; }
        public String templateOrderedReceiptSha256() { return templateOrderedReceiptSha256; }
        public String oracleSha256() { return oracleSha256; }
        public String compilerSourceSha256() { return compilerSourceSha256; }
    }

    /**
     * One {@code Chest*} structure-marker cell of a template program.
     *
     * <p>The marker is not a block-entity {@code DATA} cell: the template leaves the cell untouched
     * (the sole processor ignores {@code minecraft:structure_block}) and the piece writes the chest
     * afterwards, so this lane — not the BENT lane — carries the cell's production outcome.</p>
     */
    public static final class MarkerChestCell {
        private final String template;
        private final int dataOrdinal;
        private final Local position;
        private final String marker, baseFacing, chestType, lootTable;
        private final int writeFlags;

        private MarkerChestCell(String template, int dataOrdinal, Local position, String marker,
                String baseFacing, String chestType, int writeFlags, String lootTable) {
            this.template = template; this.dataOrdinal = dataOrdinal; this.position = position;
            this.marker = marker; this.baseFacing = baseFacing; this.chestType = chestType;
            this.writeFlags = writeFlags; this.lootTable = lootTable;
        }

        public String template() { return template; }
        public int dataOrdinal() { return dataOrdinal; }
        public Local position() { return position; }
        public String marker() { return marker; }
        public String baseFacing() { return baseFacing; }
        public String chestType() { return chestType; }
        public int writeFlags() { return writeFlags; }
        public String lootTable() { return lootTable; }
    }

    /** The official transformed chest state of one {@code (marker, rotation)} pair. */
    public static final class MarkerChestFacing {
        private final String marker, baseFacing, rotation, state;
        private MarkerChestFacing(String marker, String baseFacing, String rotation, String state) {
            this.marker = marker; this.baseFacing = baseFacing; this.rotation = rotation;
            this.state = state;
        }
        public String marker() { return marker; }
        public String baseFacing() { return baseFacing; }
        public String rotation() { return rotation; }
        public String state() { return state; }
    }

    /** The official {@code StairBlock#mirror} successor of one exact stair state. */
    public static final class StairMirror {
        private final String source, leftRight, frontBack;
        private StairMirror(String source, String leftRight, String frontBack) {
            this.source = source; this.leftRight = leftRight; this.frontBack = frontBack;
        }
        public String source() { return source; }
        public String leftRight() { return leftRight; }
        public String frontBack() { return frontBack; }
    }

    // ------------------------------------------------------------------ small helpers

    private static long number(String value, String label) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException(
                    "Mansion production authority " + label + " is not an integer", error);
        }
    }

    private static int integer(String value, String label) {
        long parsed = number(value, label);
        if (parsed < Integer.MIN_VALUE || parsed > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(
                    "Mansion production authority " + label + " is out of int range");
        }
        return (int) parsed;
    }

    private static int index(String value, int bound, String label) {
        int parsed = integer(value, label);
        require(parsed >= 0 && parsed < bound,
                "Mansion production authority " + label + " index out of range");
        return parsed;
    }

    private static byte[] hexBytes(String value, String label) {
        require(!value.isEmpty() && value.length() % 2 == 0,
                "Mansion production authority " + label + " hex length drift");
        for (int index = 0; index < value.length(); index++) {
            char digit = value.charAt(index);
            require((digit >= '0' && digit <= '9') || (digit >= 'a' && digit <= 'f'),
                    "Mansion production authority " + label + " is not lowercase hex");
        }
        return HexFormat.of().parseHex(value);
    }

    private static String shaField(String value, String label) {
        require(value.length() == 64 && value.chars().allMatch(
                        digit -> (digit >= '0' && digit <= '9') || (digit >= 'a' && digit <= 'f')
                                || (digit >= 'A' && digit <= 'F')),
                "Mansion production authority " + label + " digest drift");
        return value;
    }

    private static String sha256(byte[] payload) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(payload));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }
}
