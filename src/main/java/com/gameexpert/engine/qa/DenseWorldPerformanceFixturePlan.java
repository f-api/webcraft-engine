package com.gameexpert.engine.qa;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.gameexpert.engine.mob.MobType;
import com.gameexpert.terrain.Blocks;

/**
 * Pure, deterministic plan for the version-bound dense-world performance fixture.
 *
 * <p>This is deliberately a synthetic representative farm crowd. It is not a reconstruction of
 * the historical 406-mob world: that world's exact roster was never persisted as fixture evidence.
 * The explicit provenance string and checksum prevent a later report from silently presenting this
 * workload as the historical scene.</p>
 *
 * <p>The fixture occupies one chunk-aligned 48x48 area. A player at its centre has exactly the
 * ordinary one-player 3x3 random-tick neighborhood: 9 chunks and, with the current 384-block build
 * height and six samples per 16-block section, 1,296 samples per authority turn.</p>
 */
public final class DenseWorldPerformanceFixturePlan {

    public static final String WORLD_NAME = "webcraft-perf-263-v1-qa";
    public static final long WORLD_SEED = 0x5743_5031L; // "WCP1", signed-int32 safe
    public static final String ROSTER_PROVENANCE = "synthetic-representative-not-historical";

    public static final int WIDTH = 48;
    public static final int DEPTH = 48;
    public static final int CLEAR_HEIGHT = 4;
    public static final int MOB_COUNT = 406;
    public static final int EXPECTED_RANDOM_TICK_CHUNKS = 9;
    public static final int EXPECTED_RANDOM_TICK_SAMPLES = 1_296;
    public static final int EXPECTED_MAX_RANDOM_TICK_TRANSITIONS = 128;
    public static final int MIN_ANCHOR_XZ = -(1 << 25);
    public static final int MAX_ANCHOR_XZ = (1 << 25) - WIDTH;

    private static final int MOB_COLUMNS = 20;
    private static final double MOB_FIRST_OFFSET = 3.5;
    private static final double MOB_SPACING = 2.0;
    private static final long FNV_OFFSET = 0xcbf29ce484222325L;
    private static final long FNV_PRIME = 0x100000001b3L;
    private static final MobType[] REPRESENTATIVE_ROSTER = {
            MobType.COW, MobType.PIG, MobType.SHEEP, MobType.CHICKEN,
            MobType.RABBIT, MobType.GOAT, MobType.ARMADILLO
    };

    public enum Variant {
        BASE("base-v1", false, false),
        DENSE_406("dense-406-v1", true, false),
        DENSE_406_RANDOM("dense-406-random-v1", true, true);

        private final String id;
        private final boolean denseMobs;
        private final boolean randomTickProbe;

        Variant(String id, boolean denseMobs, boolean randomTickProbe) {
            this.id = id;
            this.denseMobs = denseMobs;
            this.randomTickProbe = randomTickProbe;
        }

        public String id() { return id; }
        public boolean hasDenseMobs() { return denseMobs; }
        public boolean hasRandomTickProbe() { return randomTickProbe; }

        public static Variant fromId(String id) {
            for (Variant candidate : values()) {
                if (candidate.id.equals(id)) return candidate;
            }
            throw new IllegalArgumentException("unknown dense performance fixture variant: " + id);
        }
    }

    public record Point(int x, int y, int z) { }
    public record Cell(Point point, int blockId, int blockState) { }
    public record Bounds(int anchorX, int floorY, int anchorZ,
                         int width, int clearHeight, int depth) { }
    public record MobRequest(int ordinal, MobType type, double x, double y, double z) { }

    private final Variant variant;
    private final Bounds bounds;
    private final List<Cell> cells;
    private final List<MobRequest> mobRequests;
    private final int randomTickProbeCells;
    private final long checksum;

    private DenseWorldPerformanceFixturePlan(Variant variant, Bounds bounds, List<Cell> cells,
            List<MobRequest> mobRequests, int randomTickProbeCells) {
        this.variant = variant;
        this.bounds = bounds;
        this.cells = List.copyOf(cells);
        this.mobRequests = List.copyOf(mobRequests);
        this.randomTickProbeCells = randomTickProbeCells;
        validateMobPlacement(this.mobRequests);
        this.checksum = checksum(variant, bounds, this.cells, this.mobRequests,
                randomTickProbeCells);
    }

    public static DenseWorldPerformanceFixturePlan create(
            String variantId, int anchorX, int floorY, int anchorZ) {
        return create(Variant.fromId(variantId), anchorX, floorY, anchorZ);
    }

    public static DenseWorldPerformanceFixturePlan create(
            Variant variant, int anchorX, int floorY, int anchorZ) {
        if (variant == null) throw new IllegalArgumentException("variant is required");
        validateAnchor(anchorX, floorY, anchorZ);
        Bounds bounds = new Bounds(anchorX, floorY, anchorZ, WIDTH, CLEAR_HEIGHT, DEPTH);

        Map<Point, Cell> finalCells = new HashMap<>(WIDTH * DEPTH * (CLEAR_HEIGHT + 1));
        for (int x = 0; x < WIDTH; x++) {
            for (int z = 0; z < DEPTH; z++) {
                put(finalCells, anchorX + x, floorY, anchorZ + z, Blocks.STONE, 0);
                for (int dy = 1; dy <= CLEAR_HEIGHT; dy++) {
                    put(finalCells, anchorX + x, floorY + dy, anchorZ + z, Blocks.AIR, 0);
                }
            }
        }

        // Three-block perimeter keeps the moving crowd inside the measured arena. It is shared by
        // every variant so A/B/C differ only by their declared workload additions.
        for (int y = floorY + 1; y <= floorY + 3; y++) {
            for (int offset = 0; offset < WIDTH; offset++) {
                put(finalCells, anchorX + offset, y, anchorZ, Blocks.STONE, 0);
                put(finalCells, anchorX + offset, y, anchorZ + DEPTH - 1, Blocks.STONE, 0);
                put(finalCells, anchorX, y, anchorZ + offset, Blocks.STONE, 0);
                put(finalCells, anchorX + WIDTH - 1, y, anchorZ + offset, Blocks.STONE, 0);
            }
        }

        int probeCells = 0;
        if (variant.hasRandomTickProbe()) {
            // State zero is intentionally dry farmland. With the immature crop above it, farmland
            // remains stable, while the crop's real low-light check exercises randomTickLight
            // without growing away during the 60-second measurement.
            for (int x = 1; x < WIDTH - 1; x++) {
                for (int z = 1; z < DEPTH - 1; z++) {
                    put(finalCells, anchorX + x, floorY, anchorZ + z, Blocks.FARMLAND, 0);
                    put(finalCells, anchorX + x, floorY + 1, anchorZ + z,
                            Blocks.WHEAT_CROP, 0);
                    probeCells++;
                }
            }
        }

        List<Cell> cells = finalCells.values().stream()
                .sorted(Comparator.comparingInt((Cell cell) -> cell.point().y())
                        .thenComparingInt(cell -> cell.point().z())
                        .thenComparingInt(cell -> cell.point().x()))
                .toList();
        List<MobRequest> mobs = variant.hasDenseMobs()
                ? representativeMobs(anchorX, floorY, anchorZ) : List.of();
        return new DenseWorldPerformanceFixturePlan(variant, bounds, cells, mobs, probeCells);
    }

    private static List<MobRequest> representativeMobs(int anchorX, int floorY, int anchorZ) {
        List<MobRequest> result = new ArrayList<>(MOB_COUNT);
        for (int ordinal = 0; ordinal < MOB_COUNT; ordinal++) {
            int column = ordinal % MOB_COLUMNS;
            int row = ordinal / MOB_COLUMNS;
            MobType type = REPRESENTATIVE_ROSTER[ordinal % REPRESENTATIVE_ROSTER.length];
            result.add(new MobRequest(ordinal, type,
                    anchorX + MOB_FIRST_OFFSET + column * MOB_SPACING,
                    floorY + 1.0,
                    anchorZ + MOB_FIRST_OFFSET + row * MOB_SPACING));
        }
        return result;
    }

    private static void validateMobPlacement(List<MobRequest> mobs) {
        for (int left = 0; left < mobs.size(); left++) {
            MobRequest a = mobs.get(left);
            if (a.ordinal() != left) {
                throw new IllegalArgumentException("mob ordinals must be dense and ordered");
            }
            for (int right = left + 1; right < mobs.size(); right++) {
                MobRequest b = mobs.get(right);
                if (a.x() == b.x() && a.y() == b.y() && a.z() == b.z()) {
                    throw new IllegalArgumentException("duplicate mob position");
                }
                if (a.y() >= b.y() + b.type().height()
                        || b.y() >= a.y() + a.type().height()) continue;
                double dx = a.x() - b.x();
                double dz = a.z() - b.z();
                double minimum = (a.type().width() + b.type().width()) * 0.5;
                if (dx * dx + dz * dz < minimum * minimum) {
                    throw new IllegalArgumentException("overlapping mob positions");
                }
            }
        }
    }

    private static void validateAnchor(int x, int y, int z) {
        if (x < MIN_ANCHOR_XZ || x > MAX_ANCHOR_XZ
                || z < MIN_ANCHOR_XZ || z > MAX_ANCHOR_XZ) {
            throw new IllegalArgumentException("performance fixture X/Z anchor is outside packed range");
        }
        if (Math.floorMod(x, Blocks.CHUNK_X) != 0
                || Math.floorMod(z, Blocks.CHUNK_Z) != 0) {
            throw new IllegalArgumentException("performance fixture X/Z anchor must be chunk aligned");
        }
        if (y < 0 || y > Blocks.MAX_Y - CLEAR_HEIGHT) {
            throw new IllegalArgumentException("performance fixture floor leaves build height");
        }
    }

    private static void put(Map<Point, Cell> cells, int x, int y, int z,
            int blockId, int blockState) {
        Point point = new Point(x, y, z);
        cells.put(point, new Cell(point, blockId, blockState));
    }

    private static long checksum(Variant variant, Bounds bounds, List<Cell> cells,
            List<MobRequest> mobs, int randomTickProbeCells) {
        long hash = hashString(FNV_OFFSET, variant.id());
        hash = hashString(hash, ROSTER_PROVENANCE);
        hash = hashInt(hash, bounds.anchorX());
        hash = hashInt(hash, bounds.floorY());
        hash = hashInt(hash, bounds.anchorZ());
        hash = hashInt(hash, bounds.width());
        hash = hashInt(hash, bounds.clearHeight());
        hash = hashInt(hash, bounds.depth());
        hash = hashInt(hash, randomTickProbeCells);
        for (Cell cell : cells) {
            hash = hashInt(hash, cell.point().x());
            hash = hashInt(hash, cell.point().y());
            hash = hashInt(hash, cell.point().z());
            hash = hashInt(hash, cell.blockId());
            hash = hashInt(hash, cell.blockState());
        }
        for (MobRequest mob : mobs) {
            hash = hashInt(hash, mob.ordinal());
            hash = hashInt(hash, mob.type().stableId());
            hash = hashLong(hash, Double.doubleToRawLongBits(mob.x()));
            hash = hashLong(hash, Double.doubleToRawLongBits(mob.y()));
            hash = hashLong(hash, Double.doubleToRawLongBits(mob.z()));
        }
        return hash;
    }

    private static long hashString(long hash, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        hash = hashInt(hash, bytes.length);
        for (byte valueByte : bytes) hash = hashByte(hash, valueByte);
        return hash;
    }

    private static long hashInt(long hash, int value) {
        hash = hashByte(hash, value >>> 24);
        hash = hashByte(hash, value >>> 16);
        hash = hashByte(hash, value >>> 8);
        return hashByte(hash, value);
    }

    private static long hashLong(long hash, long value) {
        for (int shift = 56; shift >= 0; shift -= 8) {
            hash = hashByte(hash, (int) (value >>> shift));
        }
        return hash;
    }

    private static long hashByte(long hash, int value) {
        return (hash ^ (value & 0xffL)) * FNV_PRIME;
    }

    public String id() { return variant.id(); }
    public Variant variant() { return variant; }
    public Bounds bounds() { return bounds; }
    public List<Cell> cells() { return cells; }
    public List<MobRequest> mobRequests() { return mobRequests; }
    public int randomTickProbeCells() { return randomTickProbeCells; }
    public int expectedFixtureMobCount() { return mobRequests.size(); }
    public int expectedRandomTickChunks() { return EXPECTED_RANDOM_TICK_CHUNKS; }
    public int expectedRandomTickSamples() { return EXPECTED_RANDOM_TICK_SAMPLES; }
    public long checksum() { return checksum; }
}
