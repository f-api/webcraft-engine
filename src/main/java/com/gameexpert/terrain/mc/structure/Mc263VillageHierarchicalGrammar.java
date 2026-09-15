package com.gameexpert.terrain.mc.structure;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Dormant, pinned Village interior construction grammar for Minecraft 26.3-snapshot-7.
 *
 * <p>The production resource is deliberately not the official template command corpus. It contains
 * homogeneous geometric primitives, shared typed multi-cell motifs, typed marker/entity semantics and
 * exact pool processor/projection bindings. Expansion reconstructs local template cells in the three
 * authenticated StructureTemplate ordering passes (pass, Y, X, Z). No production caller is wired by
 * this tranche.</p>
 */
final class Mc263VillageHierarchicalGrammar {
    static final String RESOURCE = "/mc263/village-hierarchical-grammar-v1.txt";
    static final int RESOURCE_BYTES = 1_135_548;
    static final String RESOURCE_SHA256 =
            "9f817ccf206a0ed25ce37f0d50cf1d37e59737d77205b667b4127f735e691d5b";
    static final String SOURCE_EXECUTION_SHA256 =
            "12700d1e623b9a1352a2f0d7ce9215285f3c0ae5b505b53bcea062ddb464ff34";
    static final int TEMPLATE_COUNT = 478;
    static final int EXPANDED_BLOCK_COUNT = 159_925;
    static final int SOURCE_COMMAND_COUNT = 53_247;
    static final int SOURCE_COORDINATE_SCALARS = 313_341;
    static final int SOURCE_ENTITY_COUNT = 54;
    static final int MAX_ORDER_PASS = 2;

    private static volatile Corpus pinned;

    private Mc263VillageHierarchicalGrammar() { }

    static Corpus pinned() {
        Corpus value = pinned;
        if (value != null) return value;
        synchronized (Mc263VillageHierarchicalGrammar.class) {
            value = pinned;
            if (value == null) {
                try (InputStream input = Mc263VillageHierarchicalGrammar.class.getResourceAsStream(RESOURCE)) {
                    require(input != null, "missing accepted Village hierarchical grammar " + RESOURCE);
                    value = decodeAuthenticated(readAll(input));
                } catch (IOException error) {
                    throw new IllegalStateException("cannot read Village hierarchical grammar", error);
                }
                pinned = value;
            }
        }
        return value;
    }

    private static byte[] readAll(InputStream input) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        input.transferTo(out);
        return out.toByteArray();
    }

    private static Corpus decodeAuthenticated(byte[] bytes) {
        require(bytes.length == RESOURCE_BYTES, "Village hierarchical grammar byte-count drift");
        require(RESOURCE_SHA256.equals(sha256(bytes)), "Village hierarchical grammar identity drift");
        return parseUnchecked(bytes);
    }

    private static Corpus parseUnchecked(byte[] bytes) {
        String raw = strictUtf8(bytes);
        require(raw.endsWith("\n"), "Village hierarchical grammar must end with LF");
        require(raw.indexOf('\r') < 0, "Village hierarchical grammar CR is forbidden");
        require(!raw.contains("\nRUN|") && !raw.contains("\nPOINT|"),
                "expanded coordinate-row opcode is forbidden");

        String[] lines = raw.substring(0, raw.length() - 1).split("\n", -1);
        require(lines.length > 1, "empty Village hierarchical grammar");
        String[] header = fields(lines[0], 12, "header");
        require("H".equals(header[0]) && "VHG2631".equals(header[1]), "Village grammar format drift");
        require(SOURCE_EXECUTION_SHA256.equals(header[2]), "Village execution authority drift");
        Metrics metrics = new Metrics(
                decimal(header[3], "templates"), decimal(header[4], "expandedBlocks"),
                decimal(header[5], "sourceCommands"), decimal(header[6], "semanticPrimitives"),
                decimal(header[7], "coordinateScalars"), decimal(header[8], "sourceCoordinateScalars"),
                decimal(header[9], "motifDefinitions"), decimal(header[10], "motifReferences"),
                decimal(header[11], "bodyReuse"));
        validateMetrics(metrics);

        ArrayList<String> states = new ArrayList<>();
        ArrayList<Binding> bindings = new ArrayList<>();
        ArrayList<MarkerSemantic> markers = new ArrayList<>();
        ArrayList<EntitySemantic> entitySemantics = new ArrayList<>();
        ArrayList<ObjectMotif> motifs = new ArrayList<>();
        ArrayList<Body> bodies = new ArrayList<>();
        ArrayList<Template> templates = new ArrayList<>();
        LinkedHashMap<String, Template> byKey = new LinkedHashMap<>();
        int phase = 0;

        for (int lineNumber = 1; lineNumber < lines.length; lineNumber++) {
            String line = lines[lineNumber];
            require(!line.isEmpty(), "blank Village grammar line " + (lineNumber + 1));
            char type = line.charAt(0);
            switch (type) {
                case 'S' -> {
                    phase = advance(phase, 1, "state");
                    String[] f = fields(line, 2, "state");
                    require(!f[1].isEmpty() && !states.contains(f[1]), "duplicate/empty Village state");
                    states.add(f[1]);
                }
                case 'B' -> {
                    phase = advance(phase, 2, "binding");
                    String[] f = fields(line, 4, "binding");
                    List<String> processors = csvStrings(f[3]);
                    Binding binding = new Binding(f[1], f[2], processors);
                    require(!bindings.contains(binding), "duplicate Village binding");
                    bindings.add(binding);
                }
                case 'K' -> {
                    phase = advance(phase, 3, "marker semantic");
                    String[] f = fields(line, 3, "marker semantic");
                    require("DATA".equals(f[1]) || "CONNECTOR".equals(f[1]), "unknown marker op");
                    markers.add(new MarkerSemantic(f[1], decodeB64(f[2])));
                }
                case 'E' -> {
                    phase = advance(phase, 4, "entity semantic");
                    String[] f = fields(line, 2, "entity semantic");
                    entitySemantics.add(new EntitySemantic(decodeB64(f[1])));
                }
                case 'O' -> {
                    phase = advance(phase, 5, "object motif");
                    String[] f = fields(line, 3, "object motif");
                    require(Set.of("DOOR", "BED", "TALL_PLANT", "FARM_PLOT", "DECOR_CLUSTER")
                            .contains(f[1]), "unknown Village object motif kind");
                    ArrayList<MotifCell> cells = new ArrayList<>();
                    for (String token : semicolon(f[2])) {
                        String[] c = token.split(",", -1);
                        require(c.length == 5, "bad Village motif cell");
                        int dx = base36(c[0]), dy = base36(c[1]), dz = base36(c[2]);
                        int passOffset = base36(c[3]), stateId = base36(c[4]);
                        require(dx >= 0 && dy >= 0 && dz >= 0 && passOffset >= 0,
                                "negative Village motif coordinate");
                        requireIndex(stateId, states.size(), "motif state");
                        cells.add(new MotifCell(dx, dy, dz, passOffset, stateId));
                    }
                    require(cells.size() >= 2, "Village motif must contain at least two cells");
                    motifs.add(new ObjectMotif(f[1], cells));
                }
                case 'D' -> {
                    phase = advance(phase, 6, "body");
                    String[] f = fields(line, 3, "body");
                    Vec3i size = vec3(f[1], "body size");
                    require(size.x > 0 && size.y > 0 && size.z > 0, "non-positive Village body size");
                    ArrayList<Primitive> primitives = new ArrayList<>();
                    ArrayList<MotifPlacement> motifPlacements = new ArrayList<>();
                    ArrayList<MarkerPlacement> markerPlacements = new ArrayList<>();
                    for (String token : semicolon(f[2])) {
                        require(token.length() >= 2, "empty Village body token");
                        if (token.charAt(0) == 'p') {
                            String[] p = token.split(",", -1);
                            require(p.length == 9 && p[0].length() == 2, "bad Village primitive");
                            PrimitiveKind kind = PrimitiveKind.fromCode(p[0].charAt(1));
                            int pass = base36(p[1]);
                            int stateId = base36(p[2]);
                            requirePass(pass); requireIndex(stateId, states.size(), "primitive state");
                            Vec3i origin = new Vec3i(base36(p[3]), base36(p[4]), base36(p[5]));
                            Vec3i extent = new Vec3i(base36(p[6]), base36(p[7]), base36(p[8]));
                            require(origin.nonNegative() && extent.positive(), "bad Village primitive geometry");
                            require(within(origin, extent, size), "Village primitive outside body");
                            primitives.add(new Primitive(kind, pass, stateId, origin, extent));
                        } else if (token.charAt(0) == 'm') {
                            String[] p = token.substring(1).split(",", -1);
                            require(p.length == 5, "bad Village motif placement");
                            int motifId = base36(p[0]), basePass = base36(p[1]);
                            requireIndex(motifId, motifs.size(), "motif"); requirePass(basePass);
                            Vec3i origin = new Vec3i(base36(p[2]), base36(p[3]), base36(p[4]));
                            require(origin.nonNegative(), "negative Village motif placement");
                            motifPlacements.add(new MotifPlacement(motifId, basePass, origin));
                        } else if (token.charAt(0) == 'k') {
                            String[] p = token.substring(1).split(",", -1);
                            require(p.length == 6, "bad Village marker placement");
                            int pass = base36(p[0]), stateId = base36(p[1]), markerId = base36(p[5]);
                            requirePass(pass); requireIndex(stateId, states.size(), "marker state");
                            requireIndex(markerId, markers.size(), "marker semantic");
                            Vec3i position = new Vec3i(base36(p[2]), base36(p[3]), base36(p[4]));
                            require(position.nonNegative() && inside(position, size), "Village marker outside body");
                            markerPlacements.add(new MarkerPlacement(pass, stateId, position, markerId));
                        } else {
                            throw invalid("unknown Village body token " + token.charAt(0));
                        }
                    }
                    bodies.add(new Body(size, primitives, motifPlacements, markerPlacements));
                }
                case 'T' -> {
                    phase = advance(phase, 7, "template");
                    String[] f = fields(line, 7, "template");
                    require(!f[1].isEmpty() && !byKey.containsKey(f[1]), "duplicate/empty Village template");
                    int bodyId = base36(f[2]), blockCount = base36(f[3]);
                    requireIndex(bodyId, bodies.size(), "template body");
                    List<Integer> palette = csvBase36(f[4]);
                    require(!palette.isEmpty(), "empty Village template palette");
                    HashSet<Integer> paletteUnique = new HashSet<>();
                    for (int stateId : palette) {
                        requireIndex(stateId, states.size(), "template palette state");
                        require(paletteUnique.add(stateId), "duplicate Village template palette state");
                    }
                    List<Integer> bindingIds = csvBase36(f[5]);
                    require(!bindingIds.isEmpty(), "unbound Village template " + f[1]);
                    ArrayList<Binding> templateBindings = new ArrayList<>();
                    HashSet<Integer> bindingUnique = new HashSet<>();
                    for (int bindingId : bindingIds) {
                        requireIndex(bindingId, bindings.size(), "template binding");
                        require(bindingUnique.add(bindingId), "duplicate Village template binding");
                        templateBindings.add(bindings.get(bindingId));
                    }
                    ArrayList<EntityPlacement> entities = new ArrayList<>();
                    for (String token : semicolon(f[6])) {
                        String[] p = token.split(",", -1);
                        require(p.length == 7, "bad Village entity placement");
                        int semanticId = base36(p[0]);
                        requireIndex(semanticId, entitySemantics.size(), "entity semantic");
                        Vec3d position = new Vec3d(bitsDouble(p[1]), bitsDouble(p[2]), bitsDouble(p[3]));
                        Vec3i blockPosition = new Vec3i(base36(p[4]), base36(p[5]), base36(p[6]));
                        entities.add(new EntityPlacement(entities.size(), entitySemantics.get(semanticId),
                                position, blockPosition));
                    }
                    Template template = new Template(f[1], bodies.get(bodyId), blockCount, palette,
                            templateBindings, entities, states, motifs, markers);
                    templates.add(template); byKey.put(f[1], template);
                }
                default -> throw invalid("unknown Village grammar line type " + type);
            }
        }
        require(phase == 7, "incomplete Village hierarchical grammar");
        require(templates.size() == TEMPLATE_COUNT, "Village template cardinality drift");
        require(byKey.size() == templates.size(), "Village template key drift");
        validateMaterialization(metrics, bodies, motifs, templates);
        return new Corpus(states, bindings, markers, entitySemantics, motifs, bodies, templates, byKey, metrics);
    }

    private static void validateMaterialization(Metrics metrics, List<Body> bodies,
            List<ObjectMotif> motifs, List<Template> templates) {
        int shapePrimitives = 0, motifReferences = 0, markerReferences = 0;
        for (Body body : bodies) {
            shapePrimitives = Math.addExact(shapePrimitives, body.primitives.size());
            motifReferences = Math.addExact(motifReferences, body.motifPlacements.size());
            markerReferences = Math.addExact(markerReferences, body.markers.size());
        }
        int entityReferences = 0, expanded = 0;
        Set<PrimitiveKind> kinds = new HashSet<>();
        Set<String> motifKinds = new HashSet<>();
        for (Body body : bodies) for (Primitive primitive : body.primitives) kinds.add(primitive.kind);
        for (ObjectMotif motif : motifs) motifKinds.add(motif.kind);
        for (Template template : templates) {
            entityReferences = Math.addExact(entityReferences, template.entities.size());
            List<Cell> cells = template.expandUncached();
            expanded = Math.addExact(expanded, cells.size());
            require(cells.size() == template.blockCount, "Village template expansion count drift: " + template.key);
        }
        int computed = Math.addExact(Math.addExact(shapePrimitives, motifReferences),
                Math.addExact(markerReferences, Math.addExact(entityReferences, motifs.size())));
        require(computed == metrics.semanticPrimitives, "Village primitive metric drift");
        require(expanded == EXPANDED_BLOCK_COUNT, "Village expanded block aggregate drift");
        require(entityReferences == SOURCE_ENTITY_COUNT, "Village entity aggregate drift");
        require(kinds.containsAll(Set.of(PrimitiveKind.CUBOID, PrimitiveKind.PLANE, PrimitiveKind.WALL,
                        PrimitiveKind.FLOOR, PrimitiveKind.ROOF, PrimitiveKind.COLUMN, PrimitiveKind.BEAM,
                        PrimitiveKind.STAIRS, PrimitiveKind.OPENING, PrimitiveKind.DECOR)),
                "Village semantic primitive closure drift");
        require(motifKinds.containsAll(Set.of("DOOR", "BED", "TALL_PLANT", "FARM_PLOT", "DECOR_CLUSTER")),
                "Village reusable motif closure drift");
    }

    private static void validateMetrics(Metrics metrics) {
        require(metrics.templates == TEMPLATE_COUNT, "Village header template drift");
        require(metrics.expandedBlocks == EXPANDED_BLOCK_COUNT, "Village header block drift");
        require(metrics.sourceCommands == SOURCE_COMMAND_COUNT, "Village source command drift");
        require(metrics.sourceCoordinateScalars == SOURCE_COORDINATE_SCALARS,
                "Village source coordinate metric drift");
        require((long) metrics.semanticPrimitives * 10L < (long) SOURCE_COMMAND_COUNT * 7L,
                "Village hierarchy is not materially smaller than command corpus");
        require((long) metrics.coordinateScalars * 4L < (long) SOURCE_COORDINATE_SCALARS * 3L,
                "Village coordinate literal compression is insufficient");
        require(metrics.motifDefinitions > 0 && metrics.motifReferences >= metrics.motifDefinitions * 2,
                "Village repeated motif reuse is insufficient");
        require(metrics.bodyReuse > 0, "Village body motif reuse missing");
    }

    enum PrimitiveKind {
        CUBOID('v'), PLANE('p'), WALL('w'), FLOOR('f'), ROOF('r'), COLUMN('c'), BEAM('b'),
        STAIRS('s'), OPENING('o'), DECOR('d');
        final char code;
        PrimitiveKind(char code) { this.code = code; }
        static PrimitiveKind fromCode(char code) {
            for (PrimitiveKind kind : values()) if (kind.code == code) return kind;
            throw invalid("unknown Village primitive kind " + code);
        }
    }

    static final class Vec3i {
        private final int x, y, z;
        Vec3i(int x, int y, int z) { this.x = x; this.y = y; this.z = z; }
        int x() { return x; } int y() { return y; } int z() { return z; }
        boolean nonNegative() { return x >= 0 && y >= 0 && z >= 0; }
        boolean positive() { return x > 0 && y > 0 && z > 0; }
        @Override public boolean equals(Object other) {
            return other instanceof Vec3i value && x == value.x && y == value.y && z == value.z;
        }
        @Override public int hashCode() { return Objects.hash(x, y, z); }
        @Override public String toString() { return "[" + x + "," + y + "," + z + "]"; }
    }

    static final class Vec3d {
        private final double x, y, z;
        Vec3d(double x, double y, double z) { this.x = x; this.y = y; this.z = z; }
        double x() { return x; } double y() { return y; } double z() { return z; }
        @Override public boolean equals(Object other) {
            return other instanceof Vec3d value
                    && Double.doubleToRawLongBits(x) == Double.doubleToRawLongBits(value.x)
                    && Double.doubleToRawLongBits(y) == Double.doubleToRawLongBits(value.y)
                    && Double.doubleToRawLongBits(z) == Double.doubleToRawLongBits(value.z);
        }
        @Override public int hashCode() {
            return Objects.hash(Double.doubleToRawLongBits(x), Double.doubleToRawLongBits(y),
                    Double.doubleToRawLongBits(z));
        }
    }

    static final class Binding {
        private final String projection, processorList;
        private final List<String> processors;
        Binding(String projection, String processorList, List<String> processors) {
            require(!projection.isEmpty() && !processorList.isEmpty(), "empty Village binding identity");
            this.projection = projection; this.processorList = processorList; this.processors = List.copyOf(processors);
        }
        String projection() { return projection; }
        String processorList() { return processorList; }
        List<String> processors() { return processors; }
        @Override public boolean equals(Object other) {
            return other instanceof Binding value && projection.equals(value.projection)
                    && processorList.equals(value.processorList) && processors.equals(value.processors);
        }
        @Override public int hashCode() { return Objects.hash(projection, processorList, processors); }
    }

    static final class MarkerSemantic {
        private final String op, json;
        MarkerSemantic(String op, String json) { this.op = op; this.json = json; }
        String op() { return op; } String json() { return json; }
    }

    static final class EntitySemantic {
        private final String json;
        EntitySemantic(String json) { this.json = json; }
        String json() { return json; }
    }

    static final class MotifCell {
        private final int dx, dy, dz, passOffset, stateId;
        MotifCell(int dx, int dy, int dz, int passOffset, int stateId) {
            this.dx = dx; this.dy = dy; this.dz = dz; this.passOffset = passOffset; this.stateId = stateId;
        }
    }

    static final class ObjectMotif {
        private final String kind;
        private final List<MotifCell> cells;
        ObjectMotif(String kind, List<MotifCell> cells) { this.kind = kind; this.cells = List.copyOf(cells); }
        String kind() { return kind; } int cellCount() { return cells.size(); }
    }

    static final class Primitive {
        private final PrimitiveKind kind;
        private final int pass, stateId;
        private final Vec3i origin, extent;
        Primitive(PrimitiveKind kind, int pass, int stateId, Vec3i origin, Vec3i extent) {
            this.kind = kind; this.pass = pass; this.stateId = stateId; this.origin = origin; this.extent = extent;
        }
        PrimitiveKind kind() { return kind; } int pass() { return pass; } int stateId() { return stateId; }
        Vec3i origin() { return origin; } Vec3i extent() { return extent; }
    }

    static final class MotifPlacement {
        private final int motifId, basePass;
        private final Vec3i origin;
        MotifPlacement(int motifId, int basePass, Vec3i origin) {
            this.motifId = motifId; this.basePass = basePass; this.origin = origin;
        }
    }

    static final class MarkerPlacement {
        private final int pass, stateId, markerId;
        private final Vec3i position;
        MarkerPlacement(int pass, int stateId, Vec3i position, int markerId) {
            this.pass = pass; this.stateId = stateId; this.position = position; this.markerId = markerId;
        }
    }

    static final class Body {
        private final Vec3i size;
        private final List<Primitive> primitives;
        private final List<MotifPlacement> motifPlacements;
        private final List<MarkerPlacement> markers;
        Body(Vec3i size, List<Primitive> primitives, List<MotifPlacement> motifPlacements,
                List<MarkerPlacement> markers) {
            this.size = size; this.primitives = List.copyOf(primitives);
            this.motifPlacements = List.copyOf(motifPlacements); this.markers = List.copyOf(markers);
        }
        Vec3i size() { return size; }
        List<Primitive> primitives() { return primitives; }
        int motifReferenceCount() { return motifPlacements.size(); }
        int markerCount() { return markers.size(); }
    }

    static final class EntityPlacement {
        private final int ordinal;
        private final EntitySemantic semantic;
        private final Vec3d position;
        private final Vec3i blockPosition;
        EntityPlacement(int ordinal, EntitySemantic semantic, Vec3d position, Vec3i blockPosition) {
            this.ordinal = ordinal; this.semantic = semantic; this.position = position; this.blockPosition = blockPosition;
        }
        int ordinal() { return ordinal; } EntitySemantic semantic() { return semantic; }
        Vec3d position() { return position; } Vec3i blockPosition() { return blockPosition; }
    }

    static final class Cell {
        private final int ordinal, pass, stateIndex;
        private final Vec3i position;
        private final String state;
        private final MarkerSemantic marker;
        Cell(int ordinal, int pass, Vec3i position, int stateIndex, String state, MarkerSemantic marker) {
            this.ordinal = ordinal; this.pass = pass; this.position = position;
            this.stateIndex = stateIndex; this.state = state; this.marker = marker;
        }
        int ordinal() { return ordinal; } int pass() { return pass; } Vec3i position() { return position; }
        int stateIndex() { return stateIndex; } String state() { return state; }
        MarkerSemantic marker() { return marker; } boolean isMarker() { return marker != null; }
    }

    static final class Template {
        private final String key;
        private final Body body;
        private final int blockCount;
        private final List<Integer> paletteStateIds;
        private final List<Binding> bindings;
        private final List<EntityPlacement> entities;
        private final List<String> states;
        private final List<ObjectMotif> motifs;
        private final List<MarkerSemantic> markerSemantics;
        private volatile List<Cell> expanded;
        Template(String key, Body body, int blockCount, List<Integer> paletteStateIds,
                List<Binding> bindings, List<EntityPlacement> entities, List<String> states,
                List<ObjectMotif> motifs, List<MarkerSemantic> markerSemantics) {
            this.key = key; this.body = body; this.blockCount = blockCount;
            this.paletteStateIds = List.copyOf(paletteStateIds); this.bindings = List.copyOf(bindings);
            this.entities = List.copyOf(entities); this.states = states; this.motifs = motifs;
            this.markerSemantics = markerSemantics;
        }
        String key() { return key; } Vec3i size() { return body.size; } int blockCount() { return blockCount; }
        List<String> stateTable() {
            ArrayList<String> result = new ArrayList<>(paletteStateIds.size());
            for (int stateId : paletteStateIds) result.add(states.get(stateId));
            return List.copyOf(result);
        }
        List<Binding> bindings() { return bindings; }
        List<EntityPlacement> entities() { return entities; }
        boolean accepts(String projection, String processorList, List<String> processors) {
            return bindings.contains(new Binding(projection, processorList, processors));
        }
        List<Cell> expand() {
            List<Cell> value = expanded;
            if (value != null) return value;
            value = expandUncached();
            expanded = value;
            return value;
        }
        private List<Cell> expandUncached() {
            HashMap<Vec3i, PendingCell> cells = new HashMap<>();
            for (Primitive primitive : body.primitives) {
                for (int y = primitive.origin.y; y < primitive.origin.y + primitive.extent.y; y++) {
                    for (int x = primitive.origin.x; x < primitive.origin.x + primitive.extent.x; x++) {
                        for (int z = primitive.origin.z; z < primitive.origin.z + primitive.extent.z; z++) {
                            put(cells, new Vec3i(x, y, z), primitive.pass, primitive.stateId, null);
                        }
                    }
                }
            }
            for (MotifPlacement placement : body.motifPlacements) {
                ObjectMotif motif = motifs.get(placement.motifId);
                for (MotifCell cell : motif.cells) {
                    int pass = Math.addExact(placement.basePass, cell.passOffset);
                    requirePass(pass);
                    Vec3i position = new Vec3i(placement.origin.x + cell.dx,
                            placement.origin.y + cell.dy, placement.origin.z + cell.dz);
                    require(inside(position, body.size), "Village motif outside body " + key);
                    put(cells, position, pass, cell.stateId, null);
                }
            }
            for (MarkerPlacement marker : body.markers) {
                put(cells, marker.position, marker.pass, marker.stateId, markerSemantics.get(marker.markerId));
            }
            require(cells.size() == blockCount, "Village expanded template block drift " + key);
            HashMap<Integer, Integer> localState = new HashMap<>();
            for (int i = 0; i < paletteStateIds.size(); i++) localState.put(paletteStateIds.get(i), i);
            ArrayList<PendingCell> ordered = new ArrayList<>(cells.values());
            ordered.sort(Comparator.comparingInt((PendingCell cell) -> cell.pass)
                    .thenComparingInt(cell -> cell.position.y)
                    .thenComparingInt(cell -> cell.position.x)
                    .thenComparingInt(cell -> cell.position.z));
            ArrayList<Cell> result = new ArrayList<>(ordered.size());
            for (int ordinal = 0; ordinal < ordered.size(); ordinal++) {
                PendingCell cell = ordered.get(ordinal);
                Integer stateIndex = localState.get(cell.stateId);
                require(stateIndex != null, "Village body state absent from palette " + key);
                result.add(new Cell(ordinal, cell.pass, cell.position, stateIndex,
                        states.get(cell.stateId), cell.marker));
            }
            return List.copyOf(result);
        }
        private void put(Map<Vec3i, PendingCell> cells, Vec3i position, int pass,
                int stateId, MarkerSemantic marker) {
            requireIndex(stateId, states.size(), "expanded state");
            require(inside(position, body.size), "Village cell outside body " + key);
            require(cells.putIfAbsent(position, new PendingCell(pass, stateId, position, marker)) == null,
                    "Village primitive/motif overlap " + key + " " + position);
        }
    }

    private static final class PendingCell {
        private final int pass, stateId;
        private final Vec3i position;
        private final MarkerSemantic marker;
        PendingCell(int pass, int stateId, Vec3i position, MarkerSemantic marker) {
            this.pass = pass; this.stateId = stateId; this.position = position; this.marker = marker;
        }
    }

    static final class Metrics {
        private final int templates, expandedBlocks, sourceCommands, semanticPrimitives,
                coordinateScalars, sourceCoordinateScalars, motifDefinitions, motifReferences, bodyReuse;
        Metrics(int templates, int expandedBlocks, int sourceCommands, int semanticPrimitives,
                int coordinateScalars, int sourceCoordinateScalars, int motifDefinitions,
                int motifReferences, int bodyReuse) {
            this.templates = templates; this.expandedBlocks = expandedBlocks; this.sourceCommands = sourceCommands;
            this.semanticPrimitives = semanticPrimitives; this.coordinateScalars = coordinateScalars;
            this.sourceCoordinateScalars = sourceCoordinateScalars; this.motifDefinitions = motifDefinitions;
            this.motifReferences = motifReferences; this.bodyReuse = bodyReuse;
        }
        int templateCount() { return templates; } int expandedBlockCount() { return expandedBlocks; }
        int sourceCommandCount() { return sourceCommands; } int semanticPrimitiveCount() { return semanticPrimitives; }
        int coordinateScalarCount() { return coordinateScalars; }
        int sourceCoordinateScalarCount() { return sourceCoordinateScalars; }
        int motifDefinitionCount() { return motifDefinitions; } int motifReferenceCount() { return motifReferences; }
        int bodyReuseCount() { return bodyReuse; }
        double compressionRatio() { return (double) sourceCommands / semanticPrimitives; }
        double coordinateCompressionRatio() { return (double) sourceCoordinateScalars / coordinateScalars; }
    }

    static final class Corpus {
        private final List<String> states;
        private final List<Binding> bindings;
        private final List<MarkerSemantic> markerSemantics;
        private final List<EntitySemantic> entitySemantics;
        private final List<ObjectMotif> motifs;
        private final List<Body> bodies;
        private final List<Template> templates;
        private final Map<String, Template> byKey;
        private final Metrics metrics;
        Corpus(List<String> states, List<Binding> bindings, List<MarkerSemantic> markerSemantics,
                List<EntitySemantic> entitySemantics, List<ObjectMotif> motifs, List<Body> bodies,
                List<Template> templates, Map<String, Template> byKey, Metrics metrics) {
            this.states = List.copyOf(states); this.bindings = List.copyOf(bindings);
            this.markerSemantics = List.copyOf(markerSemantics); this.entitySemantics = List.copyOf(entitySemantics);
            this.motifs = List.copyOf(motifs); this.bodies = List.copyOf(bodies); this.templates = List.copyOf(templates);
            this.byKey = Collections.unmodifiableMap(new LinkedHashMap<>(byKey)); this.metrics = metrics;
        }
        List<Template> templates() { return templates; } Metrics metrics() { return metrics; }
        int bodyCount() { return bodies.size(); } int stateCount() { return states.size(); }
        int bindingCount() { return bindings.size(); } int markerSemanticCount() { return markerSemantics.size(); }
        int entitySemanticCount() { return entitySemantics.size(); } List<ObjectMotif> motifs() { return motifs; }
        Template requireTemplate(String key) {
            Template template = byKey.get(key);
            if (template == null) throw invalid("unknown Village hierarchical template " + key);
            return template;
        }
    }

    private static int advance(int phase, int target, String kind) {
        require(target >= phase && target <= phase + 1, "Village grammar section order drift at " + kind);
        return Math.max(phase, target);
    }
    private static String[] fields(String line, int expected, String kind) {
        String[] fields = line.split("\\|", -1);
        require(fields.length == expected, "bad Village " + kind + " field count");
        return fields;
    }
    private static List<String> csvStrings(String raw) {
        if (raw.isEmpty()) return List.of();
        return List.of(raw.split(",", -1));
    }
    private static List<Integer> csvBase36(String raw) {
        if (raw.isEmpty()) return List.of();
        String[] parts = raw.split(",", -1);
        ArrayList<Integer> values = new ArrayList<>(parts.length);
        for (String part : parts) values.add(base36(part));
        return List.copyOf(values);
    }
    private static List<String> semicolon(String raw) {
        if (raw.isEmpty()) return List.of();
        return List.of(raw.split(";", -1));
    }
    private static Vec3i vec3(String raw, String kind) {
        String[] values = raw.split(",", -1);
        require(values.length == 3, "bad Village " + kind);
        return new Vec3i(base36(values[0]), base36(values[1]), base36(values[2]));
    }
    private static int decimal(String raw, String kind) {
        try {
            int value = Integer.parseInt(raw);
            require(value >= 0 && Integer.toString(value).equals(raw), "non-canonical Village " + kind);
            return value;
        } catch (NumberFormatException error) {
            throw invalid("bad Village decimal " + kind);
        }
    }
    private static int base36(String raw) {
        require(!raw.isEmpty(), "empty Village base36 integer");
        try {
            int value = Integer.parseInt(raw, 36);
            require(Integer.toString(value, 36).equals(raw), "non-canonical Village base36 integer");
            return value;
        } catch (NumberFormatException error) {
            throw invalid("bad Village base36 integer");
        }
    }
    private static double bitsDouble(String raw) {
        require(raw.length() == 16, "bad Village double bits");
        try { return Double.longBitsToDouble(Long.parseUnsignedLong(raw, 16)); }
        catch (NumberFormatException error) { throw invalid("bad Village double bits"); }
    }
    private static String decodeB64(String raw) {
        try {
            int padding = (4 - raw.length() % 4) % 4;
            String padded = raw + "=".repeat(padding);
            return strictUtf8(Base64.getUrlDecoder().decode(padded));
        } catch (IllegalArgumentException error) {
            throw invalid("bad Village base64 payload");
        }
    }
    private static String strictUtf8(byte[] bytes) {
        try {
            CharBuffer chars = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes));
            return chars.toString();
        } catch (CharacterCodingException error) {
            throw invalid("malformed Village UTF-8");
        }
    }
    private static boolean within(Vec3i origin, Vec3i extent, Vec3i size) {
        return origin.x + extent.x <= size.x && origin.y + extent.y <= size.y
                && origin.z + extent.z <= size.z;
    }
    private static boolean inside(Vec3i position, Vec3i size) {
        return position.x >= 0 && position.y >= 0 && position.z >= 0
                && position.x < size.x && position.y < size.y && position.z < size.z;
    }
    private static void requirePass(int pass) {
        require(pass >= 0 && pass <= MAX_ORDER_PASS, "Village order pass out of range");
    }
    private static void requireIndex(int index, int size, String kind) {
        require(index >= 0 && index < size, "Village " + kind + " index out of range");
    }
    private static String sha256(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException error) { throw new IllegalStateException(error); }
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw invalid(message);
    }
    private static IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException(message);
    }
}
