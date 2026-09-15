package com.gameexpert.terrain.mc.structure;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Pure, dormant planning boundary for the pinned hardcoded Overworld structures.
 *
 * <p>This is deliberately not a placement executor. It carries only facts proven by the pinned
 * {@code 26.3-snapshot-7} server classes: large-feature legacy RNG, start orientation and bounds,
 * igloo template identities/offsets, monument room fitting order, the small processor decision
 * vocabulary below, and piece fields that the official codecs mutate. Template block lists and
 * unsupported placement grammar are outside this boundary and fail closed.</p>
 */
public final class Mc263HardcodedStructureCarrier {
    public static final String VERSION = "26.3-snapshot-7";
    public static final String SERVER_SHA1 = "06157fedd67ff4dd6e0e6fa4a9dd0af296f0dd61";

    private static final byte[] MAGIC = "HCS263C1".getBytes(StandardCharsets.US_ASCII);
    private static final long LEGACY_MASK = (1L << 48) - 1L;
    private static final long LEGACY_MULTIPLIER = 25214903917L;
    private static final long LEGACY_INCREMENT = 11L;
    private static final int FLOAT_SAMPLE_SPACE = 1 << 24;
    private static final int MOSSY_COBBLESTONE_CUTOFF = 6_710_887;
    private static final int NO_HEIGHT_POSITION = -1;
    private static final int NO_DESIGN = -1;

    private final Kind kind;
    private final long worldSeed;
    private final int chunkX;
    private final int chunkZ;
    private final Rotation rotation;
    private final BoundingBox boundingBox;
    private final List<PieceFact> orderedPieces;
    private final List<RoomFact> orderedRooms;
    private final List<ProcessorDecision> processorDecisions;
    private final SuccessorPayload successor;
    private final RngContinuation rngContinuation;
    private final boolean productionIdentity;

    private Mc263HardcodedStructureCarrier(Kind kind, long worldSeed, int chunkX, int chunkZ,
            Rotation rotation, BoundingBox boundingBox, List<PieceFact> orderedPieces,
            List<RoomFact> orderedRooms, List<ProcessorDecision> processorDecisions,
            SuccessorPayload successor, RngContinuation rngContinuation,
            boolean productionIdentity) {
        this.kind = Objects.requireNonNull(kind, "kind");
        this.worldSeed = worldSeed;
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.rotation = Objects.requireNonNull(rotation, "rotation");
        this.boundingBox = Objects.requireNonNull(boundingBox, "boundingBox");
        this.orderedPieces = List.copyOf(orderedPieces);
        this.orderedRooms = List.copyOf(orderedRooms);
        this.processorDecisions = List.copyOf(processorDecisions);
        this.successor = Objects.requireNonNull(successor, "successor");
        this.rngContinuation = Objects.requireNonNull(rngContinuation, "rngContinuation");
        this.productionIdentity = productionIdentity;
        validateShape();
    }

    /** Plans one start from the exact legacy large-feature seed and preserves its continuation. */
    public static Mc263HardcodedStructureCarrier plan(
            Kind kind, long worldSeed, int chunkX, int chunkZ) {
        Objects.requireNonNull(kind, "kind");
        LegacyRandom random = LegacyRandom.largeFeature(worldSeed, chunkX, chunkZ);
        return switch (kind) {
            case DESERT_PYRAMID -> scattered(kind, worldSeed, chunkX, chunkZ,
                    direction(random.nextInt(4)), 21, 15, 21, random);
            case JUNGLE_PYRAMID -> scattered(kind, worldSeed, chunkX, chunkZ,
                    direction(random.nextInt(4)), 12, 10, 15, random);
            case SWAMP_HUT -> scattered(kind, worldSeed, chunkX, chunkZ,
                    direction(random.nextInt(4)), 7, 7, 9, random);
            case IGLOO -> igloo(worldSeed, chunkX, chunkZ, random);
            case OCEAN_MONUMENT -> monument(worldSeed, chunkX, chunkZ, random);
        };
    }

    public Kind kind() { return kind; }
    public long worldSeed() { return worldSeed; }
    public int chunkX() { return chunkX; }
    public int chunkZ() { return chunkZ; }
    public Rotation rotation() { return rotation; }
    public BoundingBox boundingBox() { return boundingBox; }
    public List<PieceFact> orderedPieces() { return orderedPieces; }
    public List<RoomFact> orderedRooms() { return orderedRooms; }
    public List<ProcessorDecision> processorDecisions() { return processorDecisions; }
    public SuccessorPayload successor() { return successor; }
    public RngContinuation rngContinuation() { return rngContinuation; }
    public Set<String> requiredCapabilities() { return requiredCapabilities(kind); }
    public Set<String> requiredExactStates() { return requiredStates(kind); }

    /** Exact persisted {@code minecraft:omb} binary-NBT compound proven by the pinned oracle. */
    public byte[] canonicalMonumentBuildingNbt() {
        if (kind != Kind.OCEAN_MONUMENT) {
            throw new IllegalStateException("non-monument carrier has no omb payload");
        }
        return canonicalMonumentBuildingNbt(boundingBox, rotation);
    }

    static byte[] canonicalMonumentBuildingNbt(BoundingBox boundingBox, Rotation rotation) {
        Objects.requireNonNull(boundingBox, "monument building box");
        Objects.requireNonNull(rotation, "monument building orientation");
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(74);
            try (DataOutputStream out = new DataOutputStream(bytes)) {
                out.writeByte(10); out.writeUTF("");
                out.writeByte(11); out.writeUTF("BB"); out.writeInt(6);
                out.writeInt(boundingBox.minX); out.writeInt(boundingBox.minY);
                out.writeInt(boundingBox.minZ); out.writeInt(boundingBox.maxX);
                out.writeInt(boundingBox.maxY); out.writeInt(boundingBox.maxZ);
                out.writeByte(8); out.writeUTF("id"); out.writeUTF("minecraft:omb");
                out.writeByte(3); out.writeUTF("GD"); out.writeInt(0);
                out.writeByte(3); out.writeUTF("O"); out.writeInt(switch (rotation) {
                    case SOUTH -> 0;
                    case WEST -> 1;
                    case NORTH -> 2;
                    case EAST -> 3;
                    default -> throw new IllegalStateException(
                            "monument orientation is not cardinal");
                });
                out.writeByte(0);
            }
            byte[] result = bytes.toByteArray();
            if (result.length != 74) throw new AssertionError("monument omb NBT length drift");
            return result;
        } catch (IOException impossible) {
            throw new IllegalStateException("in-memory monument NBT failed", impossible);
        }
    }

    /**
     * Validates the complete dormant consumer surface before it can use any carried fact.
     * Unknown capability or state names are rejected, as are missing required entries.
     */
    public void preflight(Set<String> capabilities, Set<String> exactStates) {
        Objects.requireNonNull(capabilities, "capabilities");
        Objects.requireNonNull(exactStates, "exactStates");
        Set<String> knownCapabilities = Set.of(
                "rotation_aware_boxes", "ordered_piece_graph", "canonical_codec",
                "mutable_successor", "legacy_rng_continuation", "template_identity",
                "processor_decisions", "room_opening_graph");
        if (!knownCapabilities.containsAll(capabilities)) {
            throw new UnsupportedOperationException("unknown hardcoded-structure capability");
        }
        Set<String> requiredCapabilities = requiredCapabilities(kind);
        if (!capabilities.containsAll(requiredCapabilities)) {
            throw new UnsupportedOperationException("missing hardcoded-structure capability");
        }
        Set<String> knownStates = Set.of("minecraft:structure_block", "minecraft:cobblestone",
                "minecraft:mossy_cobblestone", "minecraft:sand",
                "minecraft:suspicious_sand", "minecraft:water", "minecraft:ice",
                "minecraft:packed_ice", "minecraft:blue_ice");
        if (!knownStates.containsAll(exactStates)) {
            throw new UnsupportedOperationException("unknown hardcoded-structure exact state");
        }
        Set<String> requiredStates = requiredStates(kind);
        if (!exactStates.containsAll(requiredStates)) {
            throw new UnsupportedOperationException("missing hardcoded-structure exact state");
        }
    }

    /** Returns an immutable successor after validating every mutable official field atomically. */
    public Mc263HardcodedStructureCarrier withSuccessor(SuccessorPayload replacement) {
        validateSuccessor(kind, replacement);
        return new Mc263HardcodedStructureCarrier(kind, worldSeed, chunkX, chunkZ, rotation,
                boundingBox, orderedPieces, orderedRooms, processorDecisions, replacement,
                rngContinuation, productionIdentity);
    }

    public byte[] encodeCanonical() {
        if (!productionIdentity) {
            throw new UnsupportedOperationException(
                    "direct-constructor monument uses its dedicated canonical carrier");
        }
        return encodeFactsCanonical(MAGIC);
    }

    byte[] directFactsCanonical() {
        if (productionIdentity || kind != Kind.OCEAN_MONUMENT) {
            throw new IllegalStateException("not a direct-constructor monument fact graph");
        }
        return encodeFactsCanonical("HCS263DF".getBytes(StandardCharsets.US_ASCII));
    }

    private byte[] encodeFactsCanonical(byte[] magic) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(bytes)) {
                output.write(magic);
                output.writeByte(kind.ordinal());
                output.writeLong(worldSeed);
                output.writeInt(chunkX);
                output.writeInt(chunkZ);
                output.writeByte(rotation.ordinal());
                boundingBox.write(output);
                output.writeInt(orderedPieces.size());
                for (PieceFact piece : orderedPieces) piece.write(output);
                output.writeInt(orderedRooms.size());
                for (RoomFact room : orderedRooms) room.write(output);
                output.writeInt(processorDecisions.size());
                for (ProcessorDecision decision : processorDecisions) decision.write(output);
                successor.write(output);
                rngContinuation.write(output);
            }
            return bytes.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("in-memory hardcoded structure codec failed", exception);
        }
    }

    /** Strictly decodes, replans, and byte-compares the immutable facts before accepting state. */
    public static Mc263HardcodedStructureCarrier decodeCanonical(byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes");
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(bytes))) {
            byte[] magic = input.readNBytes(MAGIC.length);
            if (!Arrays.equals(magic, MAGIC)) {
                throw new IllegalArgumentException("unknown hardcoded structure codec");
            }
            Kind kind = enumValue(Kind.values(), input.readUnsignedByte(), "structure kind");
            long worldSeed = input.readLong();
            int chunkX = input.readInt();
            int chunkZ = input.readInt();
            Rotation rotation = enumValue(
                    Rotation.values(), input.readUnsignedByte(), "rotation");
            BoundingBox box = BoundingBox.read(input);
            List<PieceFact> pieces = readList(input, 64, PieceFact::read, "pieces");
            List<RoomFact> rooms = readList(input, 64, RoomFact::read, "rooms");
            List<ProcessorDecision> decisions = readList(
                    input, 16, ProcessorDecision::read, "processor decisions");
            SuccessorPayload successor = SuccessorPayload.read(input);
            RngContinuation continuation = RngContinuation.read(input);
            if (input.read() != -1) {
                throw new IllegalArgumentException("trailing hardcoded structure bytes");
            }
            Mc263HardcodedStructureCarrier decoded = new Mc263HardcodedStructureCarrier(kind,
                    worldSeed, chunkX, chunkZ, rotation, box, pieces, rooms, decisions, successor,
                    continuation, true);
            Mc263HardcodedStructureCarrier replanned = plan(kind, worldSeed, chunkX, chunkZ)
                    .withSuccessor(successor);
            if (!Arrays.equals(decoded.encodeCanonical(), replanned.encodeCanonical())) {
                throw new IllegalArgumentException("noncanonical hardcoded structure facts");
            }
            if (!Arrays.equals(bytes, decoded.encodeCanonical())) {
                throw new IllegalArgumentException("noncanonical hardcoded structure encoding");
            }
            return decoded;
        } catch (EOFException exception) {
            throw new IllegalArgumentException("truncated hardcoded structure encoding", exception);
        } catch (IOException exception) {
            throw new IllegalArgumentException("invalid hardcoded structure encoding", exception);
        }
    }

    private static Mc263HardcodedStructureCarrier scattered(Kind kind, long worldSeed, int chunkX,
            int chunkZ, Rotation direction, int width, int height, int depth, LegacyRandom random) {
        int west = Math.multiplyExact(chunkX, 16);
        int north = Math.multiplyExact(chunkZ, 16);
        BoundingBox box = axisBox(west, 64, north, direction, width, height, depth);
        PieceKind pieceKind = switch (kind) {
            case DESERT_PYRAMID -> PieceKind.DESERT_PYRAMID;
            case JUNGLE_PYRAMID -> PieceKind.JUNGLE_PYRAMID;
            case SWAMP_HUT -> PieceKind.SWAMP_HUT;
            default -> throw new IllegalArgumentException("not a scattered hardcoded structure");
        };
        List<ProcessorDecision> decisions = switch (kind) {
            case DESERT_PYRAMID -> List.of(
                    new ProcessorDecision(Processor.ARCHAEOLOGY_SAND,
                            "minecraft:sand", "minecraft:suspicious_sand",
                            false, 0, FLOAT_SAMPLE_SPACE));
            case JUNGLE_PYRAMID -> List.of(
                    new ProcessorDecision(Processor.MOSS_STONE_SELECTOR,
                            "minecraft:cobblestone", "minecraft:mossy_cobblestone",
                            false, 0, MOSSY_COBBLESTONE_CUTOFF),
                    new ProcessorDecision(Processor.MOSS_STONE_SELECTOR,
                            "minecraft:cobblestone", "minecraft:cobblestone",
                            false, MOSSY_COBBLESTONE_CUTOFF, FLOAT_SAMPLE_SPACE));
            case SWAMP_HUT -> List.of();
            default -> throw new IllegalArgumentException("not a scattered hardcoded structure");
        };
        PieceFact piece = new PieceFact(pieceKind, box, List.of(), NO_DESIGN, null, null);
        return new Mc263HardcodedStructureCarrier(kind, worldSeed, chunkX, chunkZ, direction, box,
                List.of(piece), List.of(), decisions, SuccessorPayload.initial(kind),
                random.continuation(), true);
    }

    private static Mc263HardcodedStructureCarrier igloo(
            long worldSeed, int chunkX, int chunkZ, LegacyRandom random) {
        int baseX = Math.multiplyExact(chunkX, 16);
        int baseZ = Math.multiplyExact(chunkZ, 16);
        Rotation rotation = Rotation.values()[random.nextInt(4)];
        ArrayList<PieceFact> pieces = new ArrayList<>();
        if (random.nextDouble() < 0.5D) {
            int depth = random.nextInt(8) + 4;
            pieces.add(iglooPiece(Template.IGLOO_BOTTOM, baseX, baseZ, rotation, depth * 3));
            for (int index = 0; index < depth - 1; index++) {
                pieces.add(iglooPiece(Template.IGLOO_MIDDLE, baseX, baseZ, rotation, index * 3));
            }
        }
        pieces.add(iglooPiece(Template.IGLOO_TOP, baseX, baseZ, rotation, 0));
        BoundingBox aggregate = aggregate(pieces);
        List<ProcessorDecision> decisions = List.of(
                new ProcessorDecision(Processor.IGNORE_STRUCTURE_BLOCK,
                        "minecraft:structure_block", "", true, 0, FLOAT_SAMPLE_SPACE));
        return new Mc263HardcodedStructureCarrier(Kind.IGLOO, worldSeed, chunkX, chunkZ,
                rotation, aggregate, pieces, List.of(), decisions,
                SuccessorPayload.initial(Kind.IGLOO), random.continuation(), true);
    }

    private static PieceFact iglooPiece(
            Template template, int baseX, int baseZ, Rotation rotation, int depth) {
        TemplateFacts facts = templateFacts(template);
        int x = Math.addExact(baseX, facts.offsetX);
        int y = Math.subtractExact(Math.addExact(90, facts.offsetY), depth);
        int z = Math.addExact(baseZ, facts.offsetZ);
        BoundingBox box = templateBox(x, y, z, rotation, facts);
        return new PieceFact(PieceKind.IGLOO_TEMPLATE, box, List.of(), NO_DESIGN,
                template, new BlockPos(x, y, z));
    }

    private static Mc263HardcodedStructureCarrier monument(
            long worldSeed, int chunkX, int chunkZ, LegacyRandom random) {
        Rotation direction = direction(random.nextInt(4));
        int west = Math.subtractExact(Math.multiplyExact(chunkX, 16), 29);
        int north = Math.subtractExact(Math.multiplyExact(chunkZ, 16), 29);
        return monument(worldSeed, chunkX, chunkZ, west, north, direction, random, true);
    }

    static Mc263HardcodedStructureCarrier directMonument(long constructorSeed,
            int originX, int originZ, Rotation direction) {
        Objects.requireNonNull(direction, "direct monument direction");
        if (!EnumSet.of(Rotation.NORTH, Rotation.EAST, Rotation.SOUTH, Rotation.WEST)
                .contains(direction)) {
            throw new IllegalArgumentException("direct monument direction is not cardinal");
        }
        return monument(constructorSeed, Math.floorDiv(originX, 16), Math.floorDiv(originZ, 16),
                originX, originZ,
                direction, new LegacyRandom(constructorSeed), false);
    }

    private static Mc263HardcodedStructureCarrier monument(long identitySeed, int identityX,
            int identityZ, int west, int north, Rotation direction, LegacyRandom random,
            boolean productionIdentity) {
        BoundingBox building = axisBox(west, 39, north, direction, 58, 23, 58);
        MonumentGraph graph = generateMonumentGraph(random, direction, building);
        ArrayList<PieceFact> pieces = new ArrayList<>();
        pieces.add(new PieceFact(PieceKind.MONUMENT_BUILDING, building,
                List.of(), NO_DESIGN, null, null));
        pieces.addAll(graph.pieces);
        List<ProcessorDecision> decisions = List.of(
                fillKeep("minecraft:water"), fillKeep("minecraft:ice"),
                fillKeep("minecraft:packed_ice"), fillKeep("minecraft:blue_ice"));
        return new Mc263HardcodedStructureCarrier(Kind.OCEAN_MONUMENT, identitySeed, identityX,
                identityZ, direction, building, pieces, graph.rooms, decisions,
                SuccessorPayload.initial(Kind.OCEAN_MONUMENT), random.continuation(),
                productionIdentity);
    }

    private static ProcessorDecision fillKeep(String state) {
        return new ProcessorDecision(Processor.MONUMENT_FILL_KEEP, state, state,
                false, 0, FLOAT_SAMPLE_SPACE);
    }

    private static MonumentGraph generateMonumentGraph(
            LegacyRandom random, Rotation direction, BoundingBox building) {
        RoomNode[] grid = new RoomNode[75];
        for (int y = 0; y < 2; y++) {
            for (int x = 0; x < 5; x++) {
                for (int z = 0; z < 4; z++) grid[roomIndex(x, y, z)] = new RoomNode(roomIndex(x, y, z));
            }
        }
        for (int x = 1; x < 4; x++) {
            for (int z = 0; z < 2; z++) grid[roomIndex(x, 2, z)] = new RoomNode(roomIndex(x, 2, z));
        }
        for (int x = 0; x < 5; x++) {
            for (int z = 0; z < 5; z++) {
                for (int y = 0; y < 3; y++) {
                    RoomNode node = grid[roomIndex(x, y, z)];
                    if (node == null) continue;
                    for (int directionIndex = 0; directionIndex < 6; directionIndex++) {
                        int nx = x + stepX(directionIndex);
                        int ny = y + stepY(directionIndex);
                        int nz = z + stepZ(directionIndex);
                        if (nx < 0 || nx >= 5 || ny < 0 || ny >= 3 || nz < 0 || nz >= 5) continue;
                        RoomNode neighbor = grid[roomIndex(nx, ny, nz)];
                        if (neighbor == null) continue;
                        int edge = nz == z ? directionIndex : opposite(directionIndex);
                        connect(node, edge, neighbor);
                    }
                }
            }
        }
        RoomNode roof = new RoomNode(1003);
        RoomNode leftWing = new RoomNode(1001);
        RoomNode rightWing = new RoomNode(1002);
        connect(grid[roomIndex(2, 2, 0)], 1, roof);
        connect(grid[roomIndex(0, 1, 0)], 3, leftWing);
        connect(grid[roomIndex(4, 1, 0)], 3, rightWing);
        roof.claimed = true;
        leftWing.claimed = true;
        rightWing.claimed = true;
        RoomNode source = grid[roomIndex(2, 0, 0)];
        source.source = true;
        RoomNode core = grid[roomIndex(random.nextInt(4), 0, 2)];
        claimCore(core);
        ArrayList<RoomNode> ordered = new ArrayList<>();
        for (RoomNode node : grid) if (node != null) ordered.add(node);
        for (RoomNode node : ordered) node.updateOpenings();
        roof.updateOpenings();
        shuffle(ordered, random);
        int scanIndex = 1;
        for (RoomNode node : ordered) {
            int closed = 0;
            for (int attempt = 0; closed < 2 && attempt < 5; attempt++) {
                int edge = random.nextInt(6);
                if (!node.open(edge)) continue;
                RoomNode neighbor = node.connections[edge];
                int reverse = opposite(edge);
                node.setOpen(edge, false);
                neighbor.setOpen(reverse, false);
                if (findSource(node, scanIndex++) && findSource(neighbor, scanIndex++)) {
                    closed++;
                } else {
                    node.setOpen(edge, true);
                    neighbor.setOpen(reverse, true);
                }
            }
        }
        ordered.add(roof);
        ordered.add(leftWing);
        ordered.add(rightWing);

        ArrayList<PieceFact> pieces = new ArrayList<>();
        source.claimed = true;
        pieces.add(roomPiece(PieceKind.MONUMENT_ENTRY, direction, building, source,
                1, 1, 1, List.of(source.index), NO_DESIGN));
        pieces.add(roomPiece(PieceKind.MONUMENT_CORE, direction, building, core,
                2, 2, 2, coreIndexes(core), NO_DESIGN));
        for (RoomNode node : ordered) {
            if (node.claimed || node.index >= 75) continue;
            PieceFact fitted = fitRoom(node, direction, building, random);
            if (fitted == null) throw new IllegalStateException("unfitted monument room");
            pieces.add(fitted);
        }
        BoundingBox leftBox = corners(worldPos(building, direction, 1, 1, 1),
                worldPos(building, direction, 23, 8, 21));
        BoundingBox rightBox = corners(worldPos(building, direction, 34, 1, 1),
                worldPos(building, direction, 56, 8, 21));
        BoundingBox penthouse = corners(worldPos(building, direction, 22, 13, 22),
                worldPos(building, direction, 35, 17, 35));
        int wingRandom = random.nextInt();
        pieces.add(new PieceFact(PieceKind.MONUMENT_WING, leftBox,
                List.of(1001), wingRandom & 1, null, null));
        pieces.add(new PieceFact(PieceKind.MONUMENT_WING, rightBox,
                List.of(1002), (wingRandom + 1) & 1, null, null));
        pieces.add(new PieceFact(PieceKind.MONUMENT_PENTHOUSE, penthouse,
                List.of(1003), NO_DESIGN, null, null));

        ArrayList<RoomFact> roomFacts = new ArrayList<>();
        for (RoomNode node : ordered) roomFacts.add(node.fact());
        return new MonumentGraph(List.copyOf(pieces), List.copyOf(roomFacts));
    }

    private static PieceFact fitRoom(RoomNode node, Rotation direction, BoundingBox building,
            LegacyRandom random) {
        if (canFitDoubleXy(node)) {
            RoomNode east = node.connections[5];
            RoomNode up = node.connections[1];
            RoomNode eastUp = east.connections[1];
            claim(node, east, up, eastUp);
            return roomPiece(PieceKind.MONUMENT_DOUBLE_XY, direction, building, node,
                    2, 2, 1, List.of(node.index, east.index, up.index, eastUp.index), NO_DESIGN);
        }
        if (canFitDoubleYz(node)) {
            RoomNode north = node.connections[2];
            RoomNode up = node.connections[1];
            RoomNode northUp = north.connections[1];
            claim(node, north, up, northUp);
            return roomPiece(PieceKind.MONUMENT_DOUBLE_YZ, direction, building, node,
                    1, 2, 2, List.of(node.index, north.index, up.index, northUp.index), NO_DESIGN);
        }
        if (node.open(2) && !node.connections[2].claimed) {
            RoomNode source = node;
            claim(source, source.connections[2]);
            return roomPiece(PieceKind.MONUMENT_DOUBLE_Z, direction, building, source,
                    1, 1, 2, List.of(source.index, source.connections[2].index), NO_DESIGN);
        }
        if (node.open(5) && !node.connections[5].claimed) {
            claim(node, node.connections[5]);
            return roomPiece(PieceKind.MONUMENT_DOUBLE_X, direction, building, node,
                    2, 1, 1, List.of(node.index, node.connections[5].index), NO_DESIGN);
        }
        if (node.open(1) && !node.connections[1].claimed) {
            claim(node, node.connections[1]);
            return roomPiece(PieceKind.MONUMENT_DOUBLE_Y, direction, building, node,
                    1, 2, 1, List.of(node.index, node.connections[1].index), NO_DESIGN);
        }
        if (!node.open(4) && !node.open(5) && !node.open(2) && !node.open(3) && !node.open(1)) {
            claim(node);
            return roomPiece(PieceKind.MONUMENT_SIMPLE_TOP, direction, building, node,
                    1, 1, 1, List.of(node.index), NO_DESIGN);
        }
        claim(node);
        int design = random.nextInt(3);
        return roomPiece(PieceKind.MONUMENT_SIMPLE, direction, building, node,
                1, 1, 1, List.of(node.index), design);
    }

    private static boolean canFitDoubleXy(RoomNode node) {
        return node.open(5) && !node.connections[5].claimed
                && node.open(1) && !node.connections[1].claimed
                && node.connections[5].open(1)
                && !node.connections[5].connections[1].claimed;
    }

    private static boolean canFitDoubleYz(RoomNode node) {
        return node.open(2) && !node.connections[2].claimed
                && node.open(1) && !node.connections[1].claimed
                && node.connections[2].open(1)
                && !node.connections[2].connections[1].claimed;
    }

    private static PieceFact roomPiece(PieceKind kind, Rotation direction, BoundingBox building,
            RoomNode source, int width, int height, int depth, List<Integer> roomIndexes,
            int design) {
        BoundingBox local = roomBox(direction, source.index, width, height, depth);
        BlockPos offset = worldPos(building, direction, 9, 0, 22);
        BoundingBox world = local.move(offset.x, offset.y, offset.z);
        return new PieceFact(kind, world, roomIndexes, design, null, null);
    }

    private static BoundingBox roomBox(
            Rotation direction, int index, int width, int height, int depth) {
        int roomX = index % 5;
        int roomZ = index / 5 % 5;
        int roomY = index / 25;
        BoundingBox box = axisBox(0, 0, 0, direction, width * 8, height * 4, depth * 8);
        return switch (direction) {
            case NORTH -> box.move(roomX * 8, roomY * 4, -(roomZ + depth) * 8 + 1);
            case SOUTH -> box.move(roomX * 8, roomY * 4, roomZ * 8);
            case WEST -> box.move(-(roomZ + depth) * 8 + 1, roomY * 4, roomX * 8);
            case EAST -> box.move(roomZ * 8, roomY * 4, roomX * 8);
            default -> throw new IllegalArgumentException("monument requires a direction");
        };
    }

    private static List<Integer> coreIndexes(RoomNode core) {
        int x = core.index % 5;
        int z = core.index / 5 % 5;
        int y = core.index / 25;
        ArrayList<Integer> result = new ArrayList<>(8);
        for (int dy = 0; dy < 2; dy++) {
            for (int dz = 0; dz < 2; dz++) {
                for (int dx = 0; dx < 2; dx++) result.add(roomIndex(x + dx, y + dy, z + dz));
            }
        }
        return List.copyOf(result);
    }

    private static void claimCore(RoomNode core) {
        RoomNode east = core.connections[5];
        RoomNode north = core.connections[2];
        RoomNode eastNorth = east.connections[2];
        claim(core, east, north, eastNorth, core.connections[1], east.connections[1],
                north.connections[1], eastNorth.connections[1]);
    }

    private static void claim(RoomNode... nodes) {
        for (RoomNode node : nodes) node.claimed = true;
    }

    private static void connect(RoomNode source, int edge, RoomNode target) {
        source.connections[edge] = target;
        target.connections[opposite(edge)] = source;
    }

    private static boolean findSource(RoomNode node, int scanIndex) {
        if (node.source) return true;
        node.scanIndex = scanIndex;
        for (int edge = 0; edge < 6; edge++) {
            RoomNode neighbor = node.connections[edge];
            if (neighbor == null || !node.open(edge) || neighbor.scanIndex == scanIndex) continue;
            if (findSource(neighbor, scanIndex)) return true;
        }
        return false;
    }

    private static void shuffle(List<RoomNode> list, LegacyRandom random) {
        for (int size = list.size(); size > 1; size--) {
            Collections.swap(list, size - 1, random.nextInt(size));
        }
    }

    private void validateShape() {
        if (orderedPieces.isEmpty()) throw new IllegalArgumentException("empty hardcoded structure");
        for (PieceFact piece : orderedPieces) Objects.requireNonNull(piece, "piece");
        for (RoomFact room : orderedRooms) Objects.requireNonNull(room, "room");
        for (ProcessorDecision decision : processorDecisions) Objects.requireNonNull(decision, "decision");
        validateSuccessor(kind, successor);
        if (kind == Kind.OCEAN_MONUMENT) {
            if (orderedRooms.size() != 49 || orderedPieces.get(0).kind != PieceKind.MONUMENT_BUILDING) {
                throw new IllegalArgumentException("incomplete monument room graph");
            }
        } else if (!orderedRooms.isEmpty()) {
            throw new IllegalArgumentException("unexpected room graph");
        }
    }

    private static void validateSuccessor(Kind kind, SuccessorPayload payload) {
        Objects.requireNonNull(payload, "successor");
        List<String> expected = successorKeys(kind);
        if (!payload.flags.keySet().stream().toList().equals(expected)) {
            throw new IllegalArgumentException("unknown or missing successor field");
        }
        boolean hasHeight = kind == Kind.DESERT_PYRAMID || kind == Kind.JUNGLE_PYRAMID
                || kind == Kind.SWAMP_HUT;
        if (hasHeight && payload.heightPosition < -1) {
            throw new IllegalArgumentException("invalid scattered-piece height position");
        }
        if (!hasHeight && payload.heightPosition != Integer.MIN_VALUE) {
            throw new IllegalArgumentException("unexpected successor height position");
        }
    }

    private static List<String> successorKeys(Kind kind) {
        return switch (kind) {
            case DESERT_PYRAMID -> List.of("hasPlacedChest0", "hasPlacedChest1",
                    "hasPlacedChest2", "hasPlacedChest3");
            case JUNGLE_PYRAMID -> List.of("placedMainChest", "placedHiddenChest",
                    "placedTrap1", "placedTrap2");
            case SWAMP_HUT -> List.of("Witch", "Cat");
            case IGLOO, OCEAN_MONUMENT -> List.of();
        };
    }

    private static Set<String> requiredCapabilities(Kind kind) {
        EnumSet<Capability> required = EnumSet.of(Capability.ROTATION_AWARE_BOXES,
                Capability.ORDERED_PIECE_GRAPH, Capability.CANONICAL_CODEC,
                Capability.MUTABLE_SUCCESSOR, Capability.LEGACY_RNG_CONTINUATION);
        if (kind == Kind.IGLOO) {
            required.add(Capability.TEMPLATE_IDENTITY);
            required.add(Capability.PROCESSOR_DECISIONS);
        }
        if (kind == Kind.DESERT_PYRAMID || kind == Kind.JUNGLE_PYRAMID) {
            required.add(Capability.PROCESSOR_DECISIONS);
        }
        if (kind == Kind.OCEAN_MONUMENT) {
            required.add(Capability.ROOM_OPENING_GRAPH);
            required.add(Capability.PROCESSOR_DECISIONS);
        }
        return required.stream().map(Capability::id).collect(java.util.stream.Collectors.toSet());
    }

    private static Set<String> requiredStates(Kind kind) {
        return switch (kind) {
            case DESERT_PYRAMID -> Set.of("minecraft:sand", "minecraft:suspicious_sand");
            case JUNGLE_PYRAMID -> Set.of("minecraft:cobblestone", "minecraft:mossy_cobblestone");
            case IGLOO -> Set.of("minecraft:structure_block");
            case OCEAN_MONUMENT -> Set.of("minecraft:water", "minecraft:ice",
                    "minecraft:packed_ice", "minecraft:blue_ice");
            case SWAMP_HUT -> Set.of();
        };
    }

    private static BoundingBox axisBox(int x, int y, int z, Rotation direction,
            int width, int height, int depth) {
        if (direction == Rotation.NORTH || direction == Rotation.SOUTH) {
            return new BoundingBox(x, y, z, Math.addExact(x, width - 1),
                    Math.addExact(y, height - 1), Math.addExact(z, depth - 1));
        }
        if (direction == Rotation.WEST || direction == Rotation.EAST) {
            return new BoundingBox(x, y, z, Math.addExact(x, depth - 1),
                    Math.addExact(y, height - 1), Math.addExact(z, width - 1));
        }
        throw new IllegalArgumentException("directional piece requires cardinal rotation");
    }

    private static BoundingBox templateBox(
            int x, int y, int z, Rotation rotation, TemplateFacts facts) {
        BlockPos first = transform(0, 0, 0, rotation, facts.pivotX, facts.pivotZ);
        BlockPos second = transform(facts.sizeX - 1, facts.sizeY - 1, facts.sizeZ - 1,
                rotation, facts.pivotX, facts.pivotZ);
        return corners(new BlockPos(Math.addExact(x, first.x), Math.addExact(y, first.y),
                Math.addExact(z, first.z)), new BlockPos(Math.addExact(x, second.x),
                Math.addExact(y, second.y), Math.addExact(z, second.z)));
    }

    private static BlockPos transform(
            int x, int y, int z, Rotation rotation, int pivotX, int pivotZ) {
        return switch (rotation) {
            case NONE -> new BlockPos(x, y, z);
            case CLOCKWISE_180 -> new BlockPos(2 * pivotX - x, y, 2 * pivotZ - z);
            case COUNTERCLOCKWISE_90 ->
                    new BlockPos(pivotX - pivotZ + z, y, pivotX + pivotZ - x);
            case CLOCKWISE_90 ->
                    new BlockPos(pivotX + pivotZ - z, y, pivotZ - pivotX + x);
            default -> throw new IllegalArgumentException("template requires quarter rotation");
        };
    }

    private static TemplateFacts templateFacts(Template template) {
        return switch (template) {
            case IGLOO_TOP -> new TemplateFacts(7, 5, 8, 3, 5, 5, 0, 0, 0);
            case IGLOO_MIDDLE -> new TemplateFacts(3, 3, 3, 1, 3, 1, 2, -3, 4);
            case IGLOO_BOTTOM -> new TemplateFacts(7, 6, 9, 3, 6, 7, 0, -3, -2);
        };
    }

    private static BoundingBox aggregate(List<PieceFact> pieces) {
        BoundingBox result = pieces.get(0).box;
        for (int index = 1; index < pieces.size(); index++) result = result.encapsulate(pieces.get(index).box);
        return result;
    }

    private static BoundingBox corners(BlockPos first, BlockPos second) {
        return new BoundingBox(Math.min(first.x, second.x), Math.min(first.y, second.y),
                Math.min(first.z, second.z), Math.max(first.x, second.x),
                Math.max(first.y, second.y), Math.max(first.z, second.z));
    }

    private static BlockPos worldPos(
            BoundingBox box, Rotation direction, int x, int y, int z) {
        int worldX = switch (direction) {
            case NORTH, SOUTH -> box.minX + x;
            case WEST -> box.maxX - z;
            case EAST -> box.minX + z;
            default -> throw new IllegalArgumentException("cardinal direction required");
        };
        int worldZ = switch (direction) {
            case NORTH -> box.maxZ - z;
            case SOUTH -> box.minZ + z;
            case WEST, EAST -> box.minZ + x;
            default -> throw new IllegalArgumentException("cardinal direction required");
        };
        return new BlockPos(worldX, box.minY + y, worldZ);
    }

    private static Rotation direction(int value) {
        return switch (value) {
            case 0 -> Rotation.NORTH;
            case 1 -> Rotation.EAST;
            case 2 -> Rotation.SOUTH;
            case 3 -> Rotation.WEST;
            default -> throw new IllegalArgumentException("direction draw outside 0..3");
        };
    }

    private static int roomIndex(int x, int y, int z) { return y * 25 + z * 5 + x; }
    private static int opposite(int edge) { return switch (edge) {
        case 0 -> 1; case 1 -> 0; case 2 -> 3; case 3 -> 2; case 4 -> 5; case 5 -> 4;
        default -> throw new IllegalArgumentException("invalid room edge");
    }; }
    private static int stepX(int edge) { return edge == 4 ? -1 : edge == 5 ? 1 : 0; }
    private static int stepY(int edge) { return edge == 0 ? -1 : edge == 1 ? 1 : 0; }
    private static int stepZ(int edge) { return edge == 2 ? -1 : edge == 3 ? 1 : 0; }

    private static <T> T enumValue(T[] values, int ordinal, String name) {
        if (ordinal < 0 || ordinal >= values.length) {
            throw new IllegalArgumentException("unknown " + name);
        }
        return values[ordinal];
    }

    private interface Reader<T> { T read(DataInputStream input) throws IOException; }

    private static <T> List<T> readList(DataInputStream input, int maximum,
            Reader<T> reader, String name) throws IOException {
        int count = input.readInt();
        if (count < 0 || count > maximum) throw new IllegalArgumentException("invalid " + name + " count");
        ArrayList<T> result = new ArrayList<>(count);
        for (int index = 0; index < count; index++) result.add(reader.read(input));
        return List.copyOf(result);
    }

    private static void writeString(DataOutputStream output, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > 256) throw new IllegalArgumentException("hardcoded structure string too long");
        output.writeShort(bytes.length);
        output.write(bytes);
    }

    private static String readString(DataInputStream input) throws IOException {
        int size = input.readUnsignedShort();
        if (size > 256) throw new IllegalArgumentException("hardcoded structure string too long");
        return new String(input.readNBytes(size), StandardCharsets.UTF_8);
    }

    public enum Kind { DESERT_PYRAMID, IGLOO, JUNGLE_PYRAMID, SWAMP_HUT, OCEAN_MONUMENT }

    public enum Rotation {
        NONE, CLOCKWISE_90, CLOCKWISE_180, COUNTERCLOCKWISE_90,
        NORTH, EAST, SOUTH, WEST
    }

    public enum PieceKind {
        DESERT_PYRAMID, IGLOO_TEMPLATE, JUNGLE_PYRAMID, SWAMP_HUT,
        MONUMENT_BUILDING, MONUMENT_ENTRY, MONUMENT_CORE, MONUMENT_DOUBLE_X,
        MONUMENT_DOUBLE_XY, MONUMENT_DOUBLE_Y, MONUMENT_DOUBLE_YZ, MONUMENT_DOUBLE_Z,
        MONUMENT_SIMPLE, MONUMENT_SIMPLE_TOP, MONUMENT_WING, MONUMENT_PENTHOUSE
    }

    public enum Template { IGLOO_TOP, IGLOO_MIDDLE, IGLOO_BOTTOM }

    public enum Processor {
        ARCHAEOLOGY_SAND, IGNORE_STRUCTURE_BLOCK, MOSS_STONE_SELECTOR, MONUMENT_FILL_KEEP
    }

    private enum Capability {
        ROTATION_AWARE_BOXES("rotation_aware_boxes"),
        ORDERED_PIECE_GRAPH("ordered_piece_graph"),
        CANONICAL_CODEC("canonical_codec"),
        MUTABLE_SUCCESSOR("mutable_successor"),
        LEGACY_RNG_CONTINUATION("legacy_rng_continuation"),
        TEMPLATE_IDENTITY("template_identity"),
        PROCESSOR_DECISIONS("processor_decisions"),
        ROOM_OPENING_GRAPH("room_opening_graph");
        private final String id;
        Capability(String id) { this.id = id; }
        String id() { return id; }
    }

    public static final class BoundingBox {
        private final int minX;
        private final int minY;
        private final int minZ;
        private final int maxX;
        private final int maxY;
        private final int maxZ;

        public BoundingBox(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
            if (minX > maxX || minY > maxY || minZ > maxZ) {
                throw new IllegalArgumentException("inverted hardcoded structure box");
            }
            this.minX = minX; this.minY = minY; this.minZ = minZ;
            this.maxX = maxX; this.maxY = maxY; this.maxZ = maxZ;
        }
        public int minX() { return minX; } public int minY() { return minY; }
        public int minZ() { return minZ; } public int maxX() { return maxX; }
        public int maxY() { return maxY; } public int maxZ() { return maxZ; }
        private BoundingBox move(int x, int y, int z) {
            return new BoundingBox(Math.addExact(minX, x), Math.addExact(minY, y),
                    Math.addExact(minZ, z), Math.addExact(maxX, x),
                    Math.addExact(maxY, y), Math.addExact(maxZ, z));
        }
        private BoundingBox encapsulate(BoundingBox other) {
            return new BoundingBox(Math.min(minX, other.minX), Math.min(minY, other.minY),
                    Math.min(minZ, other.minZ), Math.max(maxX, other.maxX),
                    Math.max(maxY, other.maxY), Math.max(maxZ, other.maxZ));
        }
        private void write(DataOutputStream output) throws IOException {
            output.writeInt(minX); output.writeInt(minY); output.writeInt(minZ);
            output.writeInt(maxX); output.writeInt(maxY); output.writeInt(maxZ);
        }
        private static BoundingBox read(DataInputStream input) throws IOException {
            return new BoundingBox(input.readInt(), input.readInt(), input.readInt(),
                    input.readInt(), input.readInt(), input.readInt());
        }
        @Override public boolean equals(Object value) {
            if (!(value instanceof BoundingBox other)) return false;
            return minX == other.minX && minY == other.minY && minZ == other.minZ
                    && maxX == other.maxX && maxY == other.maxY && maxZ == other.maxZ;
        }
        @Override public int hashCode() { return Objects.hash(minX, minY, minZ, maxX, maxY, maxZ); }
    }

    public static final class PieceFact {
        private final PieceKind kind;
        private final BoundingBox box;
        private final List<Integer> roomIndexes;
        private final int design;
        private final Template template;
        private final BlockPos templatePosition;
        private PieceFact(PieceKind kind, BoundingBox box, List<Integer> roomIndexes, int design,
                Template template, BlockPos templatePosition) {
            this.kind = Objects.requireNonNull(kind, "piece kind");
            this.box = Objects.requireNonNull(box, "piece box");
            this.roomIndexes = List.copyOf(roomIndexes);
            this.design = design;
            this.template = template;
            this.templatePosition = templatePosition;
            if ((template == null) != (templatePosition == null)) {
                throw new IllegalArgumentException("partial template fact");
            }
            if (kind == PieceKind.IGLOO_TEMPLATE && template == null) {
                throw new IllegalArgumentException("missing igloo template fact");
            }
            if (kind != PieceKind.IGLOO_TEMPLATE && template != null) {
                throw new IllegalArgumentException("unexpected template fact");
            }
        }
        public PieceKind kind() { return kind; }
        public BoundingBox boundingBox() { return box; }
        public List<Integer> roomIndexes() { return roomIndexes; }
        public int design() { return design; }
        public Template template() { return template; }
        public int templateX() { requireTemplate(); return templatePosition.x; }
        public int templateY() { requireTemplate(); return templatePosition.y; }
        public int templateZ() { requireTemplate(); return templatePosition.z; }
        private void requireTemplate() {
            if (templatePosition == null) throw new IllegalStateException("piece has no template position");
        }
        private void write(DataOutputStream output) throws IOException {
            output.writeByte(kind.ordinal()); box.write(output); output.writeInt(roomIndexes.size());
            for (int room : roomIndexes) output.writeInt(room);
            output.writeInt(design); output.writeByte(template == null ? 255 : template.ordinal());
            if (templatePosition != null) templatePosition.write(output);
        }
        private static PieceFact read(DataInputStream input) throws IOException {
            PieceKind kind = enumValue(PieceKind.values(), input.readUnsignedByte(), "piece kind");
            BoundingBox box = BoundingBox.read(input);
            int count = input.readInt();
            if (count < 0 || count > 8) throw new IllegalArgumentException("invalid piece room count");
            ArrayList<Integer> rooms = new ArrayList<>(count);
            for (int index = 0; index < count; index++) rooms.add(input.readInt());
            int design = input.readInt();
            int templateOrdinal = input.readUnsignedByte();
            Template template = templateOrdinal == 255 ? null
                    : enumValue(Template.values(), templateOrdinal, "template");
            BlockPos position = template == null ? null : BlockPos.read(input);
            return new PieceFact(kind, box, rooms, design, template, position);
        }
    }

    public static final class RoomFact {
        private final int index;
        private final int openingMask;
        private final boolean source;
        private RoomFact(int index, int openingMask, boolean source) {
            if (!((index >= 0 && index < 75) || index == 1001 || index == 1002 || index == 1003)) {
                throw new IllegalArgumentException("unknown monument room index");
            }
            if ((openingMask & ~63) != 0) throw new IllegalArgumentException("unknown room opening");
            this.index = index; this.openingMask = openingMask; this.source = source;
        }
        public int index() { return index; }
        public int openingMask() { return openingMask; }
        public boolean source() { return source; }
        private void write(DataOutputStream output) throws IOException {
            output.writeInt(index); output.writeByte(openingMask); output.writeBoolean(source);
        }
        private static RoomFact read(DataInputStream input) throws IOException {
            return new RoomFact(input.readInt(), input.readUnsignedByte(), input.readBoolean());
        }
    }

    public static final class ProcessorDecision {
        private final Processor processor;
        private final String inputState;
        private final String outputState;
        private final boolean discard;
        private final int randomLowerInclusive;
        private final int randomUpperExclusive;
        private ProcessorDecision(Processor processor, String inputState, String outputState,
                boolean discard, int randomLowerInclusive, int randomUpperExclusive) {
            this.processor = Objects.requireNonNull(processor, "processor");
            this.inputState = Objects.requireNonNull(inputState, "inputState");
            this.outputState = Objects.requireNonNull(outputState, "outputState");
            this.discard = discard;
            this.randomLowerInclusive = randomLowerInclusive;
            this.randomUpperExclusive = randomUpperExclusive;
            if (randomLowerInclusive < 0 || randomUpperExclusive > FLOAT_SAMPLE_SPACE
                    || randomLowerInclusive >= randomUpperExclusive) {
                throw new IllegalArgumentException("invalid processor decision interval");
            }
            if (discard != outputState.isEmpty()) {
                throw new IllegalArgumentException("invalid processor discard decision");
            }
        }
        public Processor processor() { return processor; }
        public String inputState() { return inputState; }
        public String outputState() { return outputState; }
        public boolean discard() { return discard; }
        public int randomLowerInclusive() { return randomLowerInclusive; }
        public int randomUpperExclusive() { return randomUpperExclusive; }
        private void write(DataOutputStream output) throws IOException {
            output.writeByte(processor.ordinal()); writeString(output, inputState);
            writeString(output, outputState); output.writeBoolean(discard);
            output.writeInt(randomLowerInclusive); output.writeInt(randomUpperExclusive);
        }
        private static ProcessorDecision read(DataInputStream input) throws IOException {
            return new ProcessorDecision(enumValue(Processor.values(), input.readUnsignedByte(),
                    "processor"), readString(input), readString(input), input.readBoolean(),
                    input.readInt(), input.readInt());
        }
    }

    public static final class SuccessorPayload {
        private final int heightPosition;
        private final LinkedHashMap<String, Boolean> flags;
        private SuccessorPayload(int heightPosition, Map<String, Boolean> flags) {
            this.heightPosition = heightPosition;
            this.flags = new LinkedHashMap<>();
            for (Map.Entry<String, Boolean> entry : flags.entrySet()) {
                this.flags.put(Objects.requireNonNull(entry.getKey(), "successor key"),
                        Objects.requireNonNull(entry.getValue(), "successor value"));
            }
        }
        private static SuccessorPayload initial(Kind kind) {
            LinkedHashMap<String, Boolean> flags = new LinkedHashMap<>();
            for (String key : successorKeys(kind)) flags.put(key, false);
            boolean hasHeight = kind == Kind.DESERT_PYRAMID || kind == Kind.JUNGLE_PYRAMID
                    || kind == Kind.SWAMP_HUT;
            return new SuccessorPayload(hasHeight ? NO_HEIGHT_POSITION : Integer.MIN_VALUE, flags);
        }
        public SuccessorPayload withFlag(String key, boolean value) {
            if (!flags.containsKey(key)) throw new IllegalArgumentException("unknown successor field");
            LinkedHashMap<String, Boolean> replacement = new LinkedHashMap<>(flags);
            replacement.put(key, value);
            return new SuccessorPayload(heightPosition, replacement);
        }
        public SuccessorPayload withHeightPosition(int value) {
            return new SuccessorPayload(value, flags);
        }
        public int heightPosition() { return heightPosition; }
        public boolean flag(String key) {
            Boolean value = flags.get(key);
            if (value == null) throw new IllegalArgumentException("unknown successor field");
            return value;
        }
        public List<String> orderedKeys() { return List.copyOf(flags.keySet()); }
        private void write(DataOutputStream output) throws IOException {
            output.writeInt(heightPosition); output.writeInt(flags.size());
            for (Map.Entry<String, Boolean> entry : flags.entrySet()) {
                writeString(output, entry.getKey()); output.writeBoolean(entry.getValue());
            }
        }
        private static SuccessorPayload read(DataInputStream input) throws IOException {
            int height = input.readInt();
            int count = input.readInt();
            if (count < 0 || count > 4) throw new IllegalArgumentException("invalid successor count");
            LinkedHashMap<String, Boolean> flags = new LinkedHashMap<>();
            for (int index = 0; index < count; index++) {
                String key = readString(input);
                if (flags.putIfAbsent(key, input.readBoolean()) != null) {
                    throw new IllegalArgumentException("duplicate successor field");
                }
            }
            return new SuccessorPayload(height, flags);
        }
    }

    public static final class RngContinuation {
        private final long rawLegacySeed;
        private final long bitDraws;
        private RngContinuation(long rawLegacySeed, long bitDraws) {
            if ((rawLegacySeed & ~LEGACY_MASK) != 0 || bitDraws < 0) {
                throw new IllegalArgumentException("invalid legacy RNG continuation");
            }
            this.rawLegacySeed = rawLegacySeed;
            this.bitDraws = bitDraws;
        }
        public long rawLegacySeed() { return rawLegacySeed; }
        public long bitDraws() { return bitDraws; }
        private void write(DataOutputStream output) throws IOException {
            output.writeLong(rawLegacySeed); output.writeLong(bitDraws);
        }
        private static RngContinuation read(DataInputStream input) throws IOException {
            return new RngContinuation(input.readLong(), input.readLong());
        }
    }

    private static final class LegacyRandom {
        private long rawSeed;
        private long bitDraws;
        private LegacyRandom(long seed) { setSeed(seed); }
        private static LegacyRandom largeFeature(long worldSeed, int chunkX, int chunkZ) {
            LegacyRandom random = new LegacyRandom(worldSeed);
            long xScale = random.nextLong();
            long zScale = random.nextLong();
            random.setSeed((long) chunkX * xScale ^ (long) chunkZ * zScale ^ worldSeed);
            return random;
        }
        private void setSeed(long seed) { rawSeed = (seed ^ LEGACY_MULTIPLIER) & LEGACY_MASK; }
        private int next(int bits) {
            rawSeed = (rawSeed * LEGACY_MULTIPLIER + LEGACY_INCREMENT) & LEGACY_MASK;
            bitDraws++;
            return (int) (rawSeed >>> (48 - bits));
        }
        private int nextInt() { return next(32); }
        private int nextInt(int bound) {
            if (bound <= 0) throw new IllegalArgumentException("bound must be positive");
            if ((bound & -bound) == bound) return (int) ((bound * (long) next(31)) >> 31);
            int bits;
            int value;
            do { bits = next(31); value = bits % bound; }
            while (bits - value + (bound - 1) < 0);
            return value;
        }
        private long nextLong() { return ((long) next(32) << 32) + next(32); }
        private double nextDouble() {
            return (((long) next(26) << 27) + next(27)) * 0x1.0p-53;
        }
        private RngContinuation continuation() { return new RngContinuation(rawSeed, bitDraws); }
    }

    private static final class RoomNode {
        private final int index;
        private final RoomNode[] connections = new RoomNode[6];
        private int openingMask;
        private boolean claimed;
        private boolean source;
        private int scanIndex;
        private RoomNode(int index) { this.index = index; }
        private void updateOpenings() {
            openingMask = 0;
            for (int edge = 0; edge < 6; edge++) if (connections[edge] != null) openingMask |= 1 << edge;
        }
        private boolean open(int edge) { return (openingMask & (1 << edge)) != 0; }
        private void setOpen(int edge, boolean open) {
            if (open) openingMask |= 1 << edge; else openingMask &= ~(1 << edge);
        }
        private RoomFact fact() { return new RoomFact(index, openingMask, source); }
    }

    private static final class MonumentGraph {
        private final List<PieceFact> pieces;
        private final List<RoomFact> rooms;
        private MonumentGraph(List<PieceFact> pieces, List<RoomFact> rooms) {
            this.pieces = pieces; this.rooms = rooms;
        }
    }

    private static final class TemplateFacts {
        private final int sizeX, sizeY, sizeZ, pivotX, pivotY, pivotZ, offsetX, offsetY, offsetZ;
        private TemplateFacts(int sizeX, int sizeY, int sizeZ, int pivotX, int pivotY,
                int pivotZ, int offsetX, int offsetY, int offsetZ) {
            this.sizeX = sizeX; this.sizeY = sizeY; this.sizeZ = sizeZ;
            this.pivotX = pivotX; this.pivotY = pivotY; this.pivotZ = pivotZ;
            this.offsetX = offsetX; this.offsetY = offsetY; this.offsetZ = offsetZ;
        }
    }

    private static final class BlockPos {
        private final int x, y, z;
        private BlockPos(int x, int y, int z) { this.x = x; this.y = y; this.z = z; }
        private void write(DataOutputStream output) throws IOException {
            output.writeInt(x); output.writeInt(y); output.writeInt(z);
        }
        private static BlockPos read(DataInputStream input) throws IOException {
            return new BlockPos(input.readInt(), input.readInt(), input.readInt());
        }
    }
}
