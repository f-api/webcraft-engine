package com.gameexpert.terrain.mc.structure;

import com.gameexpert.terrain.Blocks;
import com.gameexpert.terrain.mc.feature.Mc263FinalChunkSidecars;
import com.gameexpert.terrain.mc.structure.Mc263ShipwreckCarrier.BlockPos;
import com.gameexpert.terrain.mc.structure.Mc263ShipwreckCarrier.BoundingBox;
import com.gameexpert.terrain.mc.structure.Mc263ShipwreckCarrier.Rotation;
import com.gameexpert.terrain.mc.structure.Mc263ShipwreckCatalog.Marker;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Dormant procedural Shipwreck grammar, projection and schema-4 sidecar settlement.
 *
 * <p>The hull is expressed as readable sections and profiles, never as copied template NBT.
 * Catalog receipts bind all 20 distinct official variants (20 ocean choices and the official
 * 11-choice beached subset).  Global cross-chunk scheduling remains outside this class; callers
 * execute one persisted piece with one explicit clip.</p>
 */
public final class Mc263ShipwreckPieceProgram {
    private static final String CHEST_BLOCK = "minecraft:chest";
    private static final String CHEST_ENTITY = "minecraft:chest";
    private static final byte[] RECEIPT_MAGIC = "SWP263C1".getBytes(StandardCharsets.US_ASCII);

    private Mc263ShipwreckPieceProgram() { }

    public interface WorldAccess {
        boolean supportsAtomicSettlement();
        boolean supportsExactState(String exactState);
        boolean supportsLootTable(String lootTable);
        boolean supportsBlockEntity(String blockIdentity, String entityType);
        int height(Mc263ShipwreckStartGenerator.Heightmap heightmap, int blockX, int blockZ);
        void settle(AtomicSettlement settlement);
    }

    public record Clip(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        public Clip {
            if (minX > maxX || minY > maxY || minZ > maxZ) {
                throw new IllegalArgumentException("inverted shipwreck clip");
            }
        }
        public boolean contains(BlockPos p) {
            return p.x() >= minX && p.x() <= maxX && p.y() >= minY && p.y() <= maxY
                    && p.z() >= minZ && p.z() <= maxZ;
        }
        public static Clip chunk(int chunkX, int chunkZ, int minY, int maxY) {
            int minX = Math.multiplyExact(chunkX, 16), minZ = Math.multiplyExact(chunkZ, 16);
            return new Clip(minX, minY, minZ, Math.addExact(minX, 15), maxY,
                    Math.addExact(minZ, 15));
        }
    }

    public record BlockWrite(BlockPos position, String exactState, int flags) { }
    public record LootWrite(BlockPos position, String table, long seed, int encounterOrder) { }
    public record BlockEntityWrite(BlockPos position, String blockIdentity, String entityType,
            byte[] canonicalNbt, int encounterOrder) {
        public BlockEntityWrite { canonicalNbt = canonicalNbt.clone(); }
        @Override public byte[] canonicalNbt() { return canonicalNbt.clone(); }
    }

    public static final class AtomicSettlement {
        private final String identity;
        private final Mc263ShipwreckCarrier successor;
        private final List<BlockWrite> writes;
        private final List<LootWrite> loot;
        private final List<BlockEntityWrite> blockEntities;
        private final byte[] successorStr;
        private final byte[] randomContinuation;
        private final byte[] frozenReceipt;

        private AtomicSettlement(String identity, Mc263ShipwreckCarrier successor,
                List<BlockWrite> writes, List<LootWrite> loot,
                List<BlockEntityWrite> blockEntities, byte[] successorStr,
                byte[] randomContinuation) {
            this.identity = identity; this.successor = successor;
            this.writes = List.copyOf(writes); this.loot = List.copyOf(loot);
            this.blockEntities = List.copyOf(blockEntities);
            this.successorStr = successorStr.clone();
            this.randomContinuation = randomContinuation.clone();
            this.frozenReceipt = freeze();
        }
        public String identity() { return identity; }
        public Mc263ShipwreckCarrier successor() { return successor; }
        public List<BlockWrite> writes() { return writes; }
        public List<LootWrite> loot() { return loot; }
        public List<BlockEntityWrite> blockEntities() { return blockEntities; }
        public byte[] successorStr() { return successorStr.clone(); }
        public byte[] randomContinuation() { return randomContinuation.clone(); }
        public byte[] frozenReceipt() { return frozenReceipt.clone(); }
        public String frozenReceiptSha256() { return sha256(frozenReceipt); }

        /**
         * Replays the flags-2 placement writes on an explicit canonical base and returns the
         * post-placement stair/fence states used by the final-chunk identity fixture.
         */
        public Map<BlockPos, String> postSetBlockStates(Map<BlockPos, String> canonicalBase) {
            LinkedHashMap<BlockPos, String> states = new LinkedHashMap<>(canonicalBase);
            for (BlockWrite write : writes) states.put(write.position, write.exactState);
            LinkedHashMap<BlockPos, String> resolved = new LinkedHashMap<>(states);
            for (BlockWrite write : writes) {
                String state = states.get(write.position);
                if (isStair(state)) {
                    resolved.put(write.position, resolveStair(write.position, state, states));
                } else if (isFence(state)) {
                    resolved.put(write.position, resolveFence(write.position, state, states));
                }
            }
            return Map.copyOf(resolved);
        }

        /** Materializes the exact schema-4 LOOT and BENT lanes for one target chunk. */
        public Mc263FinalChunkSidecars schema4Sidecars(int chunkX, int chunkZ) {
            ArrayList<Mc263FinalChunkSidecars.Loot> lootLane = new ArrayList<>();
            ArrayList<Mc263FinalChunkSidecars.BlockEntity> bentLane = new ArrayList<>();
            String facing = rotateDirection("north", successor.rotation());
            for (LootWrite value : loot) {
                lootLane.add(new Mc263FinalChunkSidecars.Loot(
                        packed(chunkX, chunkZ, value.position), facing, value.table, value.seed));
            }
            for (BlockEntityWrite value : blockEntities) {
                bentLane.add(new Mc263FinalChunkSidecars.BlockEntity(
                        packed(chunkX, chunkZ, value.position), value.blockIdentity,
                        value.entityType, value.canonicalNbt));
            }
            return new Mc263FinalChunkSidecars(List.of(), List.of(), lootLane, List.of(),
                    List.of(), List.of(), List.of(), bentLane, List.of());
        }

        private byte[] freeze() {
            try {
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                try (DataOutputStream out = new DataOutputStream(bytes)) {
                    out.write(RECEIPT_MAGIC); out.writeUTF(identity);
                    out.writeUTF(successor.structureKey()); out.writeUTF(successor.template().id());
                    out.writeUTF(successor.rotation().name()); out.writeInt(successor.templateY());
                    out.writeInt(writes.size());
                    for (BlockWrite write : writes) {
                        position(out, write.position); out.writeUTF(write.exactState); out.writeInt(write.flags);
                    }
                    out.writeInt(loot.size());
                    for (LootWrite value : loot) {
                        position(out, value.position); out.writeUTF(value.table);
                        out.writeLong(value.seed); out.writeInt(value.encounterOrder);
                    }
                    out.writeInt(blockEntities.size());
                    for (BlockEntityWrite value : blockEntities) {
                        position(out, value.position); out.writeUTF(value.blockIdentity);
                        out.writeUTF(value.entityType); out.writeInt(value.encounterOrder);
                        out.writeInt(value.canonicalNbt.length); out.write(value.canonicalNbt);
                    }
                    out.writeInt(successorStr.length); out.write(successorStr);
                    out.writeInt(randomContinuation.length); out.write(randomContinuation);
                }
                byte[] body = bytes.toByteArray();
                ByteArrayOutputStream result = new ByteArrayOutputStream(body.length + 32);
                result.write(body); result.write(digest(body));
                return result.toByteArray();
            } catch (IOException impossible) {
                throw new IllegalStateException("in-memory shipwreck receipt failed", impossible);
            }
        }
        public static void verifyFrozenReceipt(byte[] value) {
            if (value == null || value.length < RECEIPT_MAGIC.length + 32) {
                throw new IllegalArgumentException("truncated shipwreck receipt");
            }
            for (int i = 0; i < RECEIPT_MAGIC.length; i++) {
                if (value[i] != RECEIPT_MAGIC[i]) throw new IllegalArgumentException("bad shipwreck receipt magic");
            }
            byte[] body = Arrays.copyOf(value, value.length - 32);
            if (!Arrays.equals(digest(body), Arrays.copyOfRange(value, value.length - 32, value.length))) {
                throw new IllegalArgumentException("shipwreck receipt digest mismatch");
            }
        }
    }

    public static AtomicSettlement settle(Mc263ShipwreckCarrier initial,
            Mc263StructureCarrier structureCarrier,
            Mc263StructureCarrier.ChunkReferences references, String processor,
            Clip clip, WorldAccess world, PlacementRandom random) {
        Objects.requireNonNull(initial, "shipwreck initial carrier");
        Objects.requireNonNull(structureCarrier, "shipwreck STR carrier");
        Objects.requireNonNull(references, "shipwreck references");
        Objects.requireNonNull(clip, "shipwreck clip");
        Objects.requireNonNull(world, "shipwreck world");
        Objects.requireNonNull(random, "shipwreck placement random");
        Mc263ShipwreckCatalog.requireProcessor(processor);
        if (initial.heightAdjusted()) throw new IllegalArgumentException("shipwreck is already adjusted");
        preflight(initial, world);
        validateInitialStr(initial, structureCarrier, references);

        PlacementRandom candidate = random.copy();
        int projectedY = project(initial, world, candidate);
        Mc263ShipwreckCarrier successor = initial.projectTo(projectedY);
        List<LocalBlock> grammar = grammar(successor);
        ArrayList<BlockWrite> writes = new ArrayList<>();
        LinkedHashMap<BlockPos, String> finalStates = new LinkedHashMap<>();
        for (LocalBlock block : grammar) {
            BlockPos position = successor.worldPosition(block.x, block.y, block.z);
            if (!clip.contains(position)) continue;
            String state = rotateState(block.state, successor.rotation());
            writes.add(new BlockWrite(position, state, 2)); finalStates.put(position, state);
        }

        ArrayList<LootWrite> loot = new ArrayList<>();
        ArrayList<BlockEntityWrite> bent = new ArrayList<>();
        ArrayList<InstalledMarker> installed = new ArrayList<>();
        for (Marker marker : successor.template().markers()) {
            BlockPos container = successor.worldPosition(marker.x(), marker.y(), marker.z()).below();
            if (!clip.contains(container)) continue;
            String chest = chestState(successor.rotation());
            if (!CHEST_BLOCK.equals(blockKey(finalStates.get(container)))) {
                writes.add(new BlockWrite(container, chest, 2)); finalStates.put(container, chest);
            }
            installed.add(new InstalledMarker(container, marker));
        }
        // Official placement consumes one wrapper nextLong per installed DATA marker before
        // marker loot seeds are drawn in transformed marker encounter order.
        for (int index = 0; index < installed.size(); index++) candidate.nextLong();
        for (InstalledMarker value : installed) {
            Marker marker = value.marker;
            BlockPos container = value.position;
            long seed = candidate.nextLong();
            int order = loot.size();
            loot.add(new LootWrite(container, marker.kind().lootTable(), seed, order));
            bent.add(new BlockEntityWrite(container, CHEST_BLOCK, CHEST_ENTITY,
                    canonicalChestNbt(container, marker.kind().lootTable(), seed), order));
        }

        Mc263StructureCarrier successorCarrier = successorCarrier(
                structureCarrier, initial, successor);
        AtomicSettlement settlement = new AtomicSettlement(initial.structureKey() + "@"
                + initial.chunkX() + "," + initial.chunkZ(), successor, writes, loot, bent,
                successorCarrier.receiptBytes(), candidate.canonicalContinuation());
        world.settle(settlement);
        random.commit(candidate);
        return settlement;
    }

    /** Replays one already height-adjusted persisted piece into another referencing chunk. */
    public static AtomicSettlement replayAdjusted(Mc263ShipwreckCarrier adjusted,
            Mc263StructureCarrier structureCarrier,
            Mc263StructureCarrier.ChunkReferences references, String processor,
            Clip clip, WorldAccess world, PlacementRandom random) {
        Objects.requireNonNull(adjusted, "adjusted shipwreck carrier");
        Objects.requireNonNull(structureCarrier, "shipwreck STR carrier");
        Objects.requireNonNull(references, "shipwreck references");
        Objects.requireNonNull(clip, "shipwreck clip");
        Objects.requireNonNull(world, "shipwreck world");
        Objects.requireNonNull(random, "shipwreck placement random");
        Mc263ShipwreckCatalog.requireProcessor(processor);
        if (!adjusted.heightAdjusted()) {
            throw new IllegalArgumentException("shipwreck replay requires adjusted start");
        }
        preflight(adjusted, world);
        validateInitialStr(adjusted, structureCarrier, references);

        PlacementRandom candidate = random.copy();
        List<LocalBlock> grammar = grammar(adjusted);
        ArrayList<BlockWrite> writes = new ArrayList<>();
        LinkedHashMap<BlockPos, String> finalStates = new LinkedHashMap<>();
        for (LocalBlock block : grammar) {
            BlockPos position = adjusted.worldPosition(block.x, block.y, block.z);
            if (!clip.contains(position)) continue;
            String state = rotateState(block.state, adjusted.rotation());
            writes.add(new BlockWrite(position, state, 2));
            finalStates.put(position, state);
        }

        ArrayList<LootWrite> loot = new ArrayList<>();
        ArrayList<BlockEntityWrite> bent = new ArrayList<>();
        ArrayList<InstalledMarker> installed = new ArrayList<>();
        for (Marker marker : adjusted.template().markers()) {
            BlockPos container = adjusted.worldPosition(marker.x(), marker.y(), marker.z()).below();
            if (!clip.contains(container)) continue;
            String chest = chestState(adjusted.rotation());
            if (!CHEST_BLOCK.equals(blockKey(finalStates.get(container)))) {
                writes.add(new BlockWrite(container, chest, 2));
                finalStates.put(container, chest);
            }
            installed.add(new InstalledMarker(container, marker));
        }
        for (int index = 0; index < installed.size(); index++) candidate.nextLong();
        for (InstalledMarker value : installed) {
            long seed = candidate.nextLong();
            int order = loot.size();
            loot.add(new LootWrite(value.position, value.marker.kind().lootTable(), seed, order));
            bent.add(new BlockEntityWrite(value.position, CHEST_BLOCK, CHEST_ENTITY,
                    canonicalChestNbt(value.position, value.marker.kind().lootTable(), seed),
                    order));
        }

        AtomicSettlement settlement = new AtomicSettlement(adjusted.structureKey() + "@"
                + adjusted.chunkX() + "," + adjusted.chunkZ(), adjusted, writes, loot, bent,
                structureCarrier.receiptBytes(), candidate.canonicalContinuation());
        world.settle(settlement);
        random.commit(candidate);
        return settlement;
    }

    private static void preflight(Mc263ShipwreckCarrier carrier, WorldAccess world) {
        if (!world.supportsAtomicSettlement()) {
            throw new UnsupportedOperationException("atomic shipwreck settlement required");
        }
        LinkedHashSet<String> states = requiredStates(carrier);
        states.add(chestState(carrier.rotation()));
        for (String state : states) {
            if (!world.supportsExactState(state)) {
                throw new UnsupportedOperationException("unsupported shipwreck exact state: " + state);
            }
        }
        for (Marker marker : carrier.template().markers()) {
            if (!world.supportsLootTable(marker.kind().lootTable())) {
                throw new UnsupportedOperationException("unsupported shipwreck loot table");
            }
        }
        if (!world.supportsBlockEntity(CHEST_BLOCK, CHEST_ENTITY)) {
            throw new UnsupportedOperationException("shipwreck chest BENT required");
        }
    }

    private static LinkedHashSet<String> requiredStates(Mc263ShipwreckCarrier carrier) {
        var grammar = Mc263ShipwreckGrammarData.require(carrier.template().id());
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (var palette : grammar.palettes()) for (var command : palette.commands()) {
            if (command.op() == Mc263ShipwreckGrammarData.Op.DATA) continue;
            String state = grammar.states().get(command.state());
            if (blockKey(state).equals("minecraft:air")
                    || blockKey(state).equals("minecraft:structure_block")) continue;
            result.add(rotateState(state, carrier.rotation()));
        }
        return result;
    }

    private static int project(Mc263ShipwreckCarrier carrier, WorldAccess world,
            PlacementRandom random) {
        Mc263ShipwreckStartGenerator.Heightmap map = carrier.beached()
                ? Mc263ShipwreckStartGenerator.Heightmap.WORLD_SURFACE_WG
                : Mc263ShipwreckStartGenerator.Heightmap.OCEAN_FLOOR_WG;
        long sum = 0; int minimum = Integer.MAX_VALUE; int count = 0;
        int maxX = Math.addExact(carrier.templateX(), carrier.template().sizeX() - 1);
        int maxZ = Math.addExact(carrier.templateZ(), carrier.template().sizeZ() - 1);
        for (int z = carrier.templateZ(); z <= maxZ; z++) {
            for (int x = carrier.templateX(); x <= maxX; x++) {
                int height = world.height(map, x, z);
                sum += height; minimum = Math.min(minimum, height); count++;
            }
        }
        if (carrier.beached()) {
            return Math.subtractExact(Math.subtractExact(minimum, carrier.template().sizeY() / 2),
                    random.nextInt(3));
        }
        return Math.toIntExact(sum / count);
    }

    /** Expands the v2 lossless arithmetic-run grammar; no raw template NBT is embedded. */
    static List<LocalBlock> grammar(Mc263ShipwreckCarrier carrier) {
        var template = carrier.template();
        var grammar = Mc263ShipwreckGrammarData.require(template.id());
        int paletteIndex = paletteIndex(carrier.templateX(), carrier.templateY(),
                carrier.templateZ(), grammar.palettes().size());
        var palette = grammar.palettes().get(paletteIndex);
        LinkedHashMap<LocalPos, String> blocks = new LinkedHashMap<>();
        for (var command : palette.commands()) {
            if (command.op() == Mc263ShipwreckGrammarData.Op.DATA) continue;
            String state = grammar.states().get(command.state());
            if (blockKey(state).equals("minecraft:air")
                    || blockKey(state).equals("minecraft:structure_block")) continue;
            for (int index = 0; index < command.count(); index++) {
                put(blocks, template, command.x() + command.dx() * index,
                        command.y() + command.dy() * index,
                        command.z() + command.dz() * index, state);
            }
        }
        ArrayList<LocalBlock> result = new ArrayList<>(blocks.size());
        blocks.forEach((position, state) -> result.add(
                new LocalBlock(position.x, position.y, position.z, state)));
        return List.copyOf(result);
    }

    /**
     * Vanilla {@code Mth#getSeed}: the x term is a signed i32 multiply (`imul`) that is only then
     * widened (`i2l`), so a 64-bit x term diverges for {@code |x| > 686}.
     */
    static long coordinateSeed(int x, int y, int z) {
        long value = (long) (x * 3_129_871) ^ (long) z * 116_129_781L ^ y;
        return (value * value * 42_317_861L + value * 11L) >> 16;
    }

    private static int paletteIndex(int x, int y, int z, int bound) {
        long state = (coordinateSeed(x, y, z) ^ 0x5deece66dL) & ((1L << 48) - 1);
        state = (state * 0x5deece66dL + 0xbL) & ((1L << 48) - 1);
        int bits = (int) (state >>> 17);
        return (int) ((bound * (long) bits) >> 31); // official palette count is power-of-two 8
    }
    private static void put(Map<LocalPos, String> blocks, Mc263ShipwreckCatalog.Template template,
            int x, int y, int z, String state) {
        if (x >= 0 && x < template.sizeX() && y >= 0 && y < template.sizeY()
                && z >= 0 && z < template.sizeZ()) blocks.put(new LocalPos(x, y, z), state);
    }
    record LocalPos(int x, int y, int z) { }
    record LocalBlock(int x, int y, int z, String state) { }
    private record InstalledMarker(BlockPos position, Marker marker) { }

    private static Mc263StructureCarrier successorCarrier(Mc263StructureCarrier source,
            Mc263ShipwreckCarrier initial, Mc263ShipwreckCarrier successor) {
        ArrayList<Mc263StructureCarrier.ChunkStarts> chunks = new ArrayList<>();
        boolean replaced = false;
        for (var chunk : source.startChunks()) {
            if (chunk.chunkX() != initial.chunkX() || chunk.chunkZ() != initial.chunkZ()) {
                chunks.add(chunk); continue;
            }
            ArrayList<Mc263StructureCarrier.StartEntry> entries = new ArrayList<>();
            for (var entry : chunk.orderedStarts()) {
                if (!entry.structureId().equals(initial.structureKey())) { entries.add(entry); continue; }
                if (!(entry.body() instanceof Mc263StructureCarrier.ValidStart valid)
                        || valid.orderedPieces().size() != 1
                        || !Arrays.equals(valid.orderedPieces().getFirst().persistedPayload()
                                .binaryNbtCompound(), initial.canonicalPieceNbt())) {
                    throw new IllegalArgumentException("shipwreck STR initial piece mismatch");
                }
                var box = successor.boundingBox();
                var strBox = new Mc263StructureCarrier.BoundingBox(box.minX(), box.minY(), box.minZ(),
                        box.maxX(), box.maxY(), box.maxZ());
                var piece = new Mc263StructureCarrier.Piece(Mc263ShipwreckCarrier.PIECE_TYPE,
                        strBox, false, Mc263StructureCarrier.Projection.NOT_APPLICABLE, 0, List.of(),
                        new Mc263StructureCarrier.PiecePayload(successor.canonicalPieceNbt()));
                entries.add(new Mc263StructureCarrier.StartEntry(entry.structureId(),
                        new Mc263StructureCarrier.ValidStart(valid.startKey(), valid.originChunkX(),
                                valid.originChunkZ(), valid.references(), strBox, List.of(piece))));
                replaced = true;
            }
            chunks.add(chunk.withStarts(entries));
        }
        if (!replaced) throw new IllegalArgumentException("shipwreck STR start is absent");
        return new Mc263StructureCarrier(source.registry(), chunks, source.referenceChunks(),
                source.rawStartPayloads(), source.producerGraphPayloads());
    }

    private static void validateInitialStr(Mc263ShipwreckCarrier initial,
            Mc263StructureCarrier source, Mc263StructureCarrier.ChunkReferences references) {
        List<Mc263StructureCarrier.ValidStart> starts = source.resolveStarts(
                references, initial.structureKey());
        if (starts.size() != 1) throw new IllegalArgumentException("shipwreck reference closure mismatch");
        var valid = starts.getFirst();
        if (valid.originChunkX() != initial.chunkX() || valid.originChunkZ() != initial.chunkZ()
                || valid.orderedPieces().size() != 1
                || !Arrays.equals(valid.orderedPieces().getFirst().persistedPayload()
                        .binaryNbtCompound(), initial.canonicalPieceNbt())) {
            throw new IllegalArgumentException("shipwreck persisted start mismatch");
        }
    }

    private static String chestState(Rotation rotation) {
        return "minecraft:chest[facing=" + rotateDirection("north", rotation)
                + ",type=single,waterlogged=false]";
    }
    private static String stair(String facing, String half) {
        return "minecraft:spruce_stairs[facing=" + facing + ",half=" + half
                + ",shape=straight,waterlogged=false]";
    }
    private static String rotateState(String state, Rotation rotation) {
        int open = state.indexOf('[');
        if (open < 0 || rotation == Rotation.NONE) return state;
        String body = state.substring(open + 1, state.length() - 1);
        LinkedHashMap<String, String> properties = new LinkedHashMap<>();
        for (String item : body.split(", ")) {
            String[] pair = item.split("=", 2); properties.put(pair[0], pair[1]);
        }
        LinkedHashMap<String, String> rotated = new LinkedHashMap<>();
        for (var entry : properties.entrySet()) {
            String key = entry.getKey(), value = entry.getValue();
            if (key.equals("facing")) value = rotateDirection(value, rotation);
            if (key.equals("axis") && (rotation == Rotation.CLOCKWISE_90
                    || rotation == Rotation.COUNTERCLOCKWISE_90)) {
                if (value.equals("x")) value = "z"; else if (value.equals("z")) value = "x";
            }
            if (List.of("north", "east", "south", "west").contains(key)) {
                key = rotateDirection(key, rotation);
            }
            rotated.put(key, value);
        }
        return state.substring(0, open + 1) + rotated.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(value -> value.getKey() + "=" + value.getValue())
                .collect(java.util.stream.Collectors.joining(", ")) + "]";
    }

    private static String rotateDirection(String direction, Rotation rotation) {
        List<String> order = List.of("north", "east", "south", "west");
        int index = order.indexOf(direction);
        if (index < 0) throw new IllegalArgumentException("unknown horizontal facing: " + direction);
        int turns = switch (rotation) {
            case NONE -> 0; case CLOCKWISE_90 -> 1; case CLOCKWISE_180 -> 2;
            case COUNTERCLOCKWISE_90 -> 3;
        };
        return order.get((index + turns) & 3);
    }
    private static String blockKey(String state) {
        if (state == null) return "";
        int property = state.indexOf('['); return property < 0 ? state : state.substring(0, property);
    }

    private static boolean isStair(String state) {
        return state != null && blockKey(state).endsWith("_stairs");
    }
    private static boolean isFence(String state) {
        return state != null && blockKey(state).endsWith("_fence");
    }
    private static String resolveStair(BlockPos position, String state,
            Map<BlockPos, String> states) {
        LinkedHashMap<String, String> properties = properties(state);
        String facing = properties.get("facing"), half = properties.get("half");
        String shape = "straight";
        String front = states.get(offset(position, facing));
        if (sameHalfStair(front, half)) {
            String otherFacing = properties(front).get("facing");
            if (!sameAxis(facing, otherFacing)
                    && differentStair(states.get(offset(position, opposite(otherFacing))),
                            facing, half)) {
                shape = otherFacing.equals(counterClockwise(facing))
                        ? "outer_left" : "outer_right";
            }
        }
        if (shape.equals("straight")) {
            String back = states.get(offset(position, opposite(facing)));
            if (sameHalfStair(back, half)) {
                String otherFacing = properties(back).get("facing");
                if (!sameAxis(facing, otherFacing)
                        && differentStair(states.get(offset(position, otherFacing)),
                                facing, half)) {
                    shape = otherFacing.equals(counterClockwise(facing))
                            ? "inner_left" : "inner_right";
                }
            }
        }
        properties.put("shape", shape);
        return withProperties(blockKey(state), properties);
    }
    private static String resolveFence(BlockPos position, String state,
            Map<BlockPos, String> states) {
        LinkedHashMap<String, String> properties = properties(state);
        for (String direction : List.of("north", "east", "south", "west")) {
            String neighbor = states.get(offset(position, direction));
            properties.put(direction, Boolean.toString(connectsFence(neighbor)));
        }
        return withProperties(blockKey(state), properties);
    }
    private static boolean connectsFence(String state) {
        if (state == null || blockKey(state).equals("minecraft:air")) return false;
        String key = blockKey(state);
        if (key.endsWith("_fence")) return true;
        return key.endsWith("_planks") || key.endsWith("_log") || key.endsWith("_wood")
                || key.equals("minecraft:chest");
    }
    private static boolean sameHalfStair(String state, String half) {
        return isStair(state) && properties(state).get("half").equals(half);
    }
    private static boolean differentStair(String state, String facing, String half) {
        return !sameHalfStair(state, half) || !properties(state).get("facing").equals(facing);
    }
    private static boolean sameAxis(String first, String second) {
        return (first.equals("north") || first.equals("south"))
                == (second.equals("north") || second.equals("south"));
    }
    private static String opposite(String direction) {
        return switch (direction) {
            case "north" -> "south"; case "east" -> "west";
            case "south" -> "north"; case "west" -> "east";
            default -> throw new IllegalArgumentException("unknown direction: " + direction);
        };
    }
    private static String counterClockwise(String direction) {
        return switch (direction) {
            case "north" -> "west"; case "west" -> "south";
            case "south" -> "east"; case "east" -> "north";
            default -> throw new IllegalArgumentException("unknown direction: " + direction);
        };
    }
    private static BlockPos offset(BlockPos position, String direction) {
        return switch (direction) {
            case "north" -> new BlockPos(position.x(), position.y(), position.z() - 1);
            case "east" -> new BlockPos(position.x() + 1, position.y(), position.z());
            case "south" -> new BlockPos(position.x(), position.y(), position.z() + 1);
            case "west" -> new BlockPos(position.x() - 1, position.y(), position.z());
            default -> throw new IllegalArgumentException("unknown direction: " + direction);
        };
    }
    private static LinkedHashMap<String, String> properties(String state) {
        int open = state.indexOf('[');
        LinkedHashMap<String, String> result = new LinkedHashMap<>();
        if (open < 0) return result;
        for (String item : state.substring(open + 1, state.length() - 1).split(", ")) {
            String[] pair = item.split("=", 2); result.put(pair[0], pair[1]);
        }
        return result;
    }
    private static String withProperties(String key, Map<String, String> properties) {
        return key + "[" + properties.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .map(value -> value.getKey() + "=" + value.getValue())
                .collect(java.util.stream.Collectors.joining(", ")) + "]";
    }
    private static int packed(int chunkX, int chunkZ, BlockPos position) {
        if (Math.floorDiv(position.x(), 16) != chunkX
                || Math.floorDiv(position.z(), 16) != chunkZ
                || position.y() < Blocks.MIN_Y || position.y() > Blocks.MAX_Y) {
            throw new IllegalArgumentException("shipwreck sidecar outside target chunk");
        }
        return Blocks.blockIndex(Math.floorMod(position.x(), 16), position.y(),
                Math.floorMod(position.z(), 16));
    }

    public static byte[] canonicalChestNbt(BlockPos p, String table, long seed) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(160);
            try (DataOutputStream out = new DataOutputStream(bytes)) {
                out.writeByte(10); out.writeShort(0);
                out.writeByte(8); out.writeUTF("LootTable"); out.writeUTF(table);
                out.writeByte(10); out.writeUTF("components"); out.writeByte(0);
                integer(out, "x", p.x()); integer(out, "y", p.y()); integer(out, "z", p.z());
                out.writeByte(8); out.writeUTF("id"); out.writeUTF("minecraft:chest");
                out.writeByte(4); out.writeUTF("LootTableSeed"); out.writeLong(seed);
                out.writeByte(0);
            }
            return bytes.toByteArray();
        } catch (IOException impossible) {
            throw new IllegalStateException("in-memory shipwreck chest NBT failed", impossible);
        }
    }
    private static void integer(DataOutputStream out, String name, int value) throws IOException {
        out.writeByte(3); out.writeUTF(name); out.writeInt(value);
    }
    private static void position(DataOutputStream out, BlockPos p) throws IOException {
        out.writeInt(p.x()); out.writeInt(p.y()); out.writeInt(p.z());
    }
    private static byte[] digest(byte[] value) {
        try { return MessageDigest.getInstance("SHA-256").digest(value); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    private static String sha256(byte[] value) {
        return java.util.HexFormat.of().formatHex(digest(value));
    }

    /** Exact WorldgenRandom(Xoroshiro128++) state with fork/commit transaction semantics. */
    public static final class PlacementRandom {
        private Xoroshiro source;
        private int count;
        private PlacementRandom(Xoroshiro source, int count) { this.source = source; this.count = count; }
        public static PlacementRandom forChunk(long worldSeed, int chunkX, int chunkZ,
                int stepIndex) {
            PlacementRandom result = new PlacementRandom(new Xoroshiro(0), 0);
            result.reseed(worldSeed);
            long xScale = result.nextLong() | 1L;
            long zScale = result.nextLong() | 1L;
            long decoration = (long) Math.multiplyExact(chunkX, 16) * xScale
                    + (long) Math.multiplyExact(chunkZ, 16) * zScale ^ worldSeed;
            result.reseed(decoration + stepIndex + 40_000L);
            return result;
        }
        public int nextInt(int bound) {
            if (bound <= 0) throw new IllegalArgumentException("nonpositive placement bound");
            if ((bound & -bound) == bound) return (int) (bound * (long) next(31) >> 31);
            int bits, value;
            do { bits = next(31); value = bits % bound; }
            while (bits - value + bound - 1 < 0);
            return value;
        }
        public long nextLong() { return ((long) next(32) << 32) + next(32); }
        private int next(int bits) { count++; return (int) (source.nextLong() >>> (64 - bits)); }
        private void reseed(long seed) { source = new Xoroshiro(seed); }
        public PlacementRandom copy() { return new PlacementRandom(source.copy(), count); }
        private void commit(PlacementRandom value) { source = value.source.copy(); count = value.count; }
        public int count() { return count; }
        public byte[] canonicalContinuation() {
            Xoroshiro copy = source.copy();
            ByteBuffer bytes = ByteBuffer.allocate(4 + 16 + 8 * 8);
            bytes.putInt(count).putLong(source.lo).putLong(source.hi);
            for (int i = 0; i < 8; i++) bytes.putLong(copy.nextLong());
            return bytes.array();
        }
    }

    private static final class Xoroshiro {
        private static final long SILVER = 0x6A09E667F3BCC909L;
        private static final long GOLDEN = 0x9E3779B97F4A7C15L;
        private long lo, hi;
        private Xoroshiro(long seed) {
            long first = seed ^ SILVER;
            set(mix(first), mix(first + GOLDEN));
        }
        private Xoroshiro(long lo, long hi) { set(lo, hi); }
        private void set(long lo, long hi) {
            if ((lo | hi) == 0) { this.lo = GOLDEN; this.hi = SILVER; }
            else { this.lo = lo; this.hi = hi; }
        }
        private long nextLong() {
            long a = lo, b = hi;
            long value = Long.rotateLeft(a + b, 17) + a;
            b ^= a; lo = Long.rotateLeft(a, 49) ^ b ^ b << 21; hi = Long.rotateLeft(b, 28);
            return value;
        }
        private Xoroshiro copy() { return new Xoroshiro(lo, hi); }
        private static long mix(long value) {
            value = (value ^ value >>> 30) * 0xBF58476D1CE4E5B9L;
            value = (value ^ value >>> 27) * 0x94D049BB133111EBL;
            return value ^ value >>> 31;
        }
    }
}
