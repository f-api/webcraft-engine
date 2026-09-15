package com.gameexpert.terrain.mc.structure;

import com.gameexpert.terrain.mc.structure.Mc263TrailRuinsCatalog.Command;
import com.gameexpert.terrain.mc.structure.Mc263TrailRuinsCatalog.CommandKind;
import com.gameexpert.terrain.mc.structure.Mc263TrailRuinsCatalog.Rotation;
import com.gameexpert.terrain.mc.structure.Mc263TrailRuinsCatalog.Template;
import com.gameexpert.terrain.mc.structure.Mc263TrailRuinsCatalog.Vec;
import com.gameexpert.terrain.mc.structure.Mc263TrailRuinsProducer.Piece;
import com.gameexpert.terrain.mc.structure.Mc263TrailRuinsProducer.Start;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Atomic clip settlement for the pinned Trail Ruins arithmetic-run grammar. */
public final class Mc263TrailRuinsSettlement {
    private Mc263TrailRuinsSettlement() {}

    public static Settlement settle(Start start, Mc263TrailRuinsCatalog catalog, Clip clip,
            WorldAccess world) {
        preflight(start, catalog, clip, world);
        Map<Vec, Cell> finalCells = new LinkedHashMap<>();
        Map<Vec, String> stagedStates = new LinkedHashMap<>();
        for (Piece piece : start.pieces()) {
            Template template = catalog.template(piece.template());
            List<Cell> cells = expand(piece, template);
            if (piece.processor().endsWith("houses_archaeology")) {
                processRules(cells);
                cap(cells, start.worldSeed(), piece.origin(), 6,
                        Mc263TrailRuinsCatalog.COMMON_ARCH);
                cap(cells, start.worldSeed(), piece.origin(), 3,
                        Mc263TrailRuinsCatalog.RARE_ARCH);
            } else if (piece.processor().endsWith("roads_archaeology")) {
                processRules(cells);
                cap(cells, start.worldSeed(), piece.origin(), 2,
                        Mc263TrailRuinsCatalog.COMMON_ARCH);
            } else if (piece.processor().endsWith("tower_top_archaeology")) {
                cap(cells, start.worldSeed(), piece.origin(), 2,
                        Mc263TrailRuinsCatalog.COMMON_ARCH);
            } else throw new IllegalArgumentException("unknown Trail processor: " + piece.processor());
            for (Cell original : cells) {
                if (!clip.contains(original.position())) continue;
                Cell cell = waterlog(original, world.hasWater(original.position()));
                String current = stagedStates.computeIfAbsent(cell.position(), world::blockState);
                if (cell.state().equals(current)) continue;
                stagedStates.put(cell.position(), cell.state());
                finalCells.put(cell.position(), cell);
            }
        }

        List<Cell> ordered = new ArrayList<>(finalCells.values());
        ordered.sort(Comparator.comparingInt((Cell cell) -> cell.position().y())
                .thenComparingInt(cell -> cell.position().z())
                .thenComparingInt(cell -> cell.position().x()));
        List<Write> writes = new ArrayList<>(ordered.size());
        List<Bent> bent = new ArrayList<>();
        List<Arch> arch = new ArrayList<>();
        for (Cell cell : ordered) {
            writes.add(new Write(cell.position(), cell.state()));
            if (!cell.blockEntityType().isEmpty() || !cell.lootTable().isEmpty()) {
                String grammarType = cell.blockEntityType();
                String type = !cell.lootTable().isEmpty() ? "minecraft:brushable_block"
                        : blockEntityId(grammarType);
                byte[] nbt = !cell.lootTable().isEmpty()
                        ? brushableNbt(cell.position(), cell.lootTable(), cell.lootSeed())
                        : emptyBlockEntityNbt(cell.position(), grammarType);
                bent.add(new Bent(cell.position(), cell.state(), type, nbt));
                if (!cell.lootTable().isEmpty()) {
                    arch.add(new Arch(cell.position(), cell.lootTable(), cell.lootSeed(),
                            bent.size() - 1));
                }
            }
        }
        Settlement settlement = new Settlement(clip, writes, arch, bent,
                Mc263TrailRuinsCarrier.rawStructureStartNbt(start), fingerprint(writes, arch, bent));
        world.commit(settlement);
        return settlement;
    }

    private static void preflight(Start start, Mc263TrailRuinsCatalog catalog, Clip clip,
            WorldAccess world) {
        if (start == null || start.empty() || catalog == null || clip == null || world == null) {
            throw new IllegalArgumentException("complete Trail settlement input required");
        }
        for (Piece piece : start.pieces()) {
            catalog.template(piece.template());
            Mc263TrailRuinsCatalog.requireProcessor(piece.processor());
        }
        if (!world.supportsAtomicSettlement() || !world.supportsArchBent()) {
            throw new IllegalArgumentException("atomic ARCH/BENT capability required");
        }
        for (Piece piece : start.pieces()) {
            Template template = catalog.template(piece.template());
            for (String state : template.states()) {
                if (state.startsWith("minecraft:jigsaw")) continue;
                String rotated = rotateState(state, piece.rotation());
                if (!world.supportsState(rotated)) {
                    throw new IllegalArgumentException("unsupported Trail block state: " + rotated);
                }
            }
            for (Command command : template.commands()) {
                if (command.kind() == CommandKind.JIGSAW
                        && !world.supportsState(rotateState(command.finalState(),
                                piece.rotation()))) {
                    throw new IllegalArgumentException("unsupported Trail jigsaw final state: "
                            + command.finalState());
                } else if (command.kind() == CommandKind.EMPTY_BLOCK_ENTITY
                        && !world.supportsBlockEntity(blockEntityId(command.blockEntityType()))) {
                    throw new IllegalArgumentException("unsupported Trail block entity: "
                            + command.blockEntityType());
                }
            }
        }
        if (!world.supportsBlockEntity("minecraft:brushable_block")
                || !world.supportsLootTable(Mc263TrailRuinsCatalog.COMMON_ARCH)
                || !world.supportsLootTable(Mc263TrailRuinsCatalog.RARE_ARCH)) {
            throw new IllegalArgumentException("Trail archaeology vocabulary is incomplete");
        }
    }

    private static List<Cell> expand(Piece piece, Template template) {
        List<Cell> result = new ArrayList<>();
        for (Command command : template.commands()) {
            String state = command.kind() == CommandKind.JIGSAW
                    ? rotateState(command.finalState(), piece.rotation())
                    : rotateState(template.states().get(command.state()), piece.rotation());
            for (int index = 0; index < command.count(); index++) {
                Vec local = command.start().add(new Vec(command.delta().x() * index,
                        command.delta().y() * index, command.delta().z() * index));
                Vec position = piece.origin().add(Mc263TrailRuinsProducer.rotate(local,
                        piece.rotation()));
                String type = command.kind() == CommandKind.EMPTY_BLOCK_ENTITY
                        ? command.blockEntityType() : "";
                result.add(new Cell(position, state, type, "", 0L));
            }
        }
        return result;
    }

    private static void processRules(List<Cell> cells) {
        for (int index = 0; index < cells.size(); index++) {
            Cell cell = cells.get(index);
            Legacy random = new Legacy(positionalSeed(cell.position()));
            String state = cell.state();
            String block = block(state);
            if ("minecraft:gravel".equals(block) && random.nextFloat() < 0.2F) {
                state = "minecraft:dirt";
            } else if ("minecraft:gravel".equals(block) && random.nextFloat() < 0.1F) {
                state = "minecraft:coarse_dirt";
            } else if ("minecraft:mud_bricks".equals(block) && random.nextFloat() < 0.1F) {
                state = "minecraft:packed_mud";
            }
            cells.set(index, cell.withState(state));
        }
    }

    private static void cap(List<Cell> cells, long worldSeed, Vec pieceOrigin, int limit,
            String table) {
        Legacy seed = new Legacy(worldSeed);
        long factorySeed = seed.nextLong();
        Legacy random = new Legacy(factorySeed ^ positionalSeed(pieceOrigin));
        List<Integer> indices = new ArrayList<>(cells.size());
        for (int index = 0; index < cells.size(); index++) indices.add(index);
        shuffle(indices, random);
        int replaced = 0;
        for (int index : indices) {
            if (replaced == limit) break;
            Cell cell = cells.get(index);
            if (!"minecraft:gravel".equals(block(cell.state()))) continue;
            Legacy lootRandom = new Legacy(positionalSeed(cell.position()));
            long lootSeed = lootRandom.nextLong();
            cells.set(index, new Cell(cell.position(), "minecraft:suspicious_gravel[dusted=0]",
                    "minecraft:brushable_block", table, lootSeed));
            replaced++;
        }
    }

    private static long positionalSeed(Vec position) {
        long seed = (long) (position.x() * 3_129_871) ^ (long) position.z() * 116_129_781L
                ^ position.y();
        return (seed * seed * 42_317_861L + seed * 11L) >> 16;
    }

    private static String block(String state) {
        int property = state.indexOf('[');
        return property < 0 ? state : state.substring(0, property);
    }

    private static Cell waterlog(Cell cell, boolean water) {
        if (!water || !cell.state().contains("waterlogged=false")) return cell;
        return cell.withState(cell.state().replace("waterlogged=false", "waterlogged=true"));
    }

    static String rotateState(String state, Rotation rotation) {
        int turns = switch (rotation) {
            case NONE -> 0;
            case CLOCKWISE_90 -> 1;
            case CLOCKWISE_180 -> 2;
            case COUNTERCLOCKWISE_90 -> 3;
        };
        String result = state;
        for (int turn = 0; turn < turns; turn++) {
            result = result.replace("facing=north", "facing=#east")
                    .replace("facing=east", "facing=#south")
                    .replace("facing=south", "facing=#west")
                    .replace("facing=west", "facing=#north")
                    .replace("facing=#", "facing=");
            result = result.replace("axis=x", "axis=#z").replace("axis=z", "axis=#x")
                    .replace("axis=#", "axis=");
        }
        return result;
    }

    private static <T> void shuffle(List<T> values, Legacy random) {
        for (int size = values.size(); size > 1; size--) {
            int selected = random.nextInt(size);
            T displaced = values.set(size - 1, values.get(selected));
            values.set(selected, displaced);
        }
    }

    private static byte[] brushableNbt(Vec position, String table, long seed) {
        return blockEntityNbt(position, "minecraft:brushable_block", out -> {
            stringTag(out, "LootTable", table);
            compoundTag(out, "components", ignored -> {});
        }, seed, true);
    }

    private static byte[] emptyBlockEntityNbt(Vec position, String type) {
        String id = blockEntityId(type);
        return rootNbt(out -> {
            compoundTag(out, "components", ignored -> {});
            intTag(out, "x", position.x());
            intTag(out, "y", position.y());
            if (type.endsWith(":four_empty_slots")) {
                emptyListTag(out, "Items");
                intTag(out, "z", position.z());
                intArrayTag(out, "CookingTimes", new int[] {0, 0, 0, 0});
                intArrayTag(out, "CookingTotalTimes", new int[] {0, 0, 0, 0});
            } else if (type.endsWith(":empty_idle")) {
                emptyListTag(out, "Items");
                intTag(out, "z", position.z());
                shortTag(out, "cooking_time_spent", (short) 0);
                shortTag(out, "cooking_total_time", (short) 0);
                shortTag(out, "lit_time_remaining", (short) 0);
                shortTag(out, "lit_total_time", (short) 0);
                compoundTag(out, "RecipesUsed", ignored -> {});
            } else if ("minecraft:chest".equals(type)) {
                emptyListTag(out, "Items");
                intTag(out, "z", position.z());
            } else {
                throw new IllegalArgumentException("unknown Trail block entity grammar: " + type);
            }
            stringTag(out, "id", id);
        });
    }

    private static String blockEntityId(String grammarType) {
        int variant = grammarType.indexOf(':', "minecraft:".length());
        return variant < 0 ? grammarType : grammarType.substring(0, variant);
    }

    private static byte[] blockEntityNbt(Vec position, String type, NbtBody prefix,
            long lootSeed, boolean appendSeed) {
        return rootNbt(out -> {
            prefix.write(out);
            intTag(out, "x", position.x()); intTag(out, "y", position.y());
            intTag(out, "z", position.z()); stringTag(out, "id", type);
            if (appendSeed) {
                out.writeByte(4); out.writeUTF("LootTableSeed"); out.writeLong(lootSeed);
            }
        });
    }

    private static byte[] rootNbt(NbtBody body) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream out = new DataOutputStream(bytes)) {
                out.writeByte(10); out.writeUTF(""); body.write(out);
                out.writeByte(0);
            }
            return bytes.toByteArray();
        } catch (IOException impossible) { throw new IllegalStateException(impossible); }
    }

    private static void intTag(DataOutputStream out, String key, int value) throws IOException {
        out.writeByte(3); out.writeUTF(key); out.writeInt(value);
    }
    private static void shortTag(DataOutputStream out, String key, short value) throws IOException {
        out.writeByte(2); out.writeUTF(key); out.writeShort(value);
    }
    private static void emptyListTag(DataOutputStream out, String key) throws IOException {
        out.writeByte(9); out.writeUTF(key); out.writeByte(0); out.writeInt(0);
    }
    private static void intArrayTag(DataOutputStream out, String key, int[] values)
            throws IOException {
        out.writeByte(11); out.writeUTF(key); out.writeInt(values.length);
        for (int value : values) out.writeInt(value);
    }
    private static void stringTag(DataOutputStream out, String key, String value) throws IOException {
        out.writeByte(8); out.writeUTF(key); out.writeUTF(value);
    }
    private static void compoundTag(DataOutputStream out, String key, NbtBody body)
            throws IOException {
        out.writeByte(10); out.writeUTF(key); body.write(out); out.writeByte(0);
    }

    private static String fingerprint(List<Write> writes, List<Arch> arch, List<Bent> bent) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (Write value : writes) update(digest, value.position() + "|" + value.state());
            for (Arch value : arch) update(digest, value.position() + "|" + value.table()
                    + "|" + value.signedLootSeed());
            for (Bent value : bent) digest.update(value.rawNbt());
            return java.util.HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    private static void update(MessageDigest digest, String value) {
        digest.update(value.getBytes(StandardCharsets.UTF_8)); digest.update((byte) '\n');
    }

    public interface WorldAccess {
        boolean supportsAtomicSettlement();
        boolean supportsArchBent();
        boolean supportsState(String state);
        boolean supportsBlockEntity(String type);
        boolean supportsLootTable(String table);
        String blockState(Vec position);
        boolean hasWater(Vec position);
        void commit(Settlement settlement);
    }

    public static final class Clip {
        private final int minX, minY, minZ, maxX, maxY, maxZ;
        public Clip(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
            if (minX > maxX || minY > maxY || minZ > maxZ) throw new IllegalArgumentException("clip");
            this.minX = minX; this.minY = minY; this.minZ = minZ;
            this.maxX = maxX; this.maxY = maxY; this.maxZ = maxZ;
        }
        public boolean contains(Vec value) { return value.x() >= minX && value.x() <= maxX
                && value.y() >= minY && value.y() <= maxY && value.z() >= minZ && value.z() <= maxZ; }
        public int minX() { return minX; }
        public int minZ() { return minZ; }
    }

    public static final class Settlement {
        private final Clip clip; private final List<Write> writes; private final List<Arch> arch;
        private final List<Bent> bent; private final byte[] mutableStartNbt; private final String fingerprint;
        Settlement(Clip clip, List<Write> writes, List<Arch> arch, List<Bent> bent,
                byte[] mutableStartNbt, String fingerprint) {
            this.clip = clip; this.writes = List.copyOf(writes); this.arch = List.copyOf(arch);
            this.bent = List.copyOf(bent); this.mutableStartNbt = mutableStartNbt.clone();
            this.fingerprint = fingerprint;
        }
        public Clip clip() { return clip; } public List<Write> writes() { return writes; }
        public List<Arch> arch() { return arch; } public List<Bent> bent() { return bent; }
        public byte[] mutableStartNbt() { return mutableStartNbt.clone(); }
        public String fingerprint() { return fingerprint; }
    }
    public static final class Write {
        private final Vec position; private final String state;
        Write(Vec position, String state) { this.position = position; this.state = state; }
        public Vec position() { return position; } public String state() { return state; }
    }
    public static final class Arch {
        private final Vec position; private final String table; private final long signedLootSeed;
        private final int bentOrdinal;
        Arch(Vec position, String table, long seed, int bentOrdinal) {
            this.position = position; this.table = table; this.signedLootSeed = seed;
            this.bentOrdinal = bentOrdinal;
        }
        public Vec position() { return position; } public String table() { return table; }
        public long signedLootSeed() { return signedLootSeed; }
        public int bentOrdinal() { return bentOrdinal; }
    }
    public static final class Bent {
        private final Vec position; private final String state, type; private final byte[] rawNbt;
        Bent(Vec position, String state, String type, byte[] rawNbt) {
            this.position = position; this.state = state; this.type = type; this.rawNbt = rawNbt.clone();
        }
        public Vec position() { return position; } public String state() { return state; }
        public String type() { return type; } public byte[] rawNbt() { return rawNbt.clone(); }
    }
    private static final class Cell {
        private final Vec position; private final String state, blockEntityType, lootTable;
        private final long lootSeed;
        Cell(Vec position, String state, String type, String table, long seed) {
            this.position = position; this.state = state; this.blockEntityType = type;
            this.lootTable = table; this.lootSeed = seed;
        }
        Vec position() { return position; } String state() { return state; }
        String blockEntityType() { return blockEntityType; } String lootTable() { return lootTable; }
        long lootSeed() { return lootSeed; }
        Cell withState(String value) { return new Cell(position, value, blockEntityType, lootTable, lootSeed); }
    }
    private static final class Legacy {
        private static final long MULTIPLIER = 0x5DEECE66DL, MASK = (1L << 48) - 1;
        private long seed;
        Legacy(long seed) { this.seed = (seed ^ MULTIPLIER) & MASK; }
        int next(int bits) { seed = (seed * MULTIPLIER + 11) & MASK; return (int) (seed >>> (48 - bits)); }
        int nextInt(int bound) {
            if ((bound & -bound) == bound) return (int) ((bound * (long) next(31)) >> 31);
            int bits, value; do { bits = next(31); value = bits % bound; }
            while (bits - value + bound - 1 < 0); return value;
        }
        long nextLong() { return ((long) next(32) << 32) + next(32); }
        float nextFloat() { return next(24) * 0x1.0p-24F; }
    }
    @FunctionalInterface private interface NbtBody { void write(DataOutputStream out) throws IOException; }
}
