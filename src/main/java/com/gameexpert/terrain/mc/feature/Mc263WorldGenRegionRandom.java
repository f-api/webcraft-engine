package com.gameexpert.terrain.mc.feature;

import com.gameexpert.terrain.Blocks;
import com.gameexpert.terrain.mc.ore.Mc263WorldgenRandomSource;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/**
 * Current-only mutable {@code WorldGenRegion.getRandom()} state for 26.3-snapshot-7.
 *
 * <p>This is the independent {@code XoroshiroRandomSource} owned by {@code WorldGenRegion}; it is
 * neither the decoration dispatcher's {@code WorldgenRandom} wrapper nor a Legacy48 source. The
 * draw count records raw 64-bit Xoroshiro128++ transitions. A cached Gaussian replay therefore
 * consumes zero draws, while one accepted Marsaglia polar pair consumes two draws.</p>
 */
public final class Mc263WorldGenRegionRandom implements Mc263WorldgenRandomSource {
    private static final byte[] RECEIPT_MAGIC = "WGR263R1".getBytes(StandardCharsets.US_ASCII);
    private static final int RECEIPT_VERSION = 1;
    public static final int RECEIPT_BYTES = 39;
    public static final int CONTINUATION_WIDTH = 8;

    private static final long SILVER_RATIO_64 = 0x6A09E667F3BCC909L;
    private static final long GOLDEN_RATIO_64 = 0x9E3779B97F4A7C15L;
    private static final long WORLDGEN_REGION_RANDOM_HASH_LO = 0x801BC97BF768DAB0L;
    private static final long WORLDGEN_REGION_RANDOM_HASH_HI = 0x48C569AA46FB5065L;

    private long lo;
    private long hi;
    private int drawCount;
    private boolean gaussianPresent;
    private long gaussianBits;

    private Mc263WorldGenRegionRandom(State state) {
        replaceWith(state);
    }

    /** Constructs a canonical current state, including Java's all-zero source normalization. */
    public static Mc263WorldGenRegionRandom fromState(long lo, long hi, int drawCount,
            boolean gaussianPresent, long gaussianBits) {
        return new Mc263WorldGenRegionRandom(
                State.canonical(lo, hi, drawCount, gaussianPresent, gaussianBits));
    }

    /**
     * Exact pinned {@code RandomState#getOrCreateRandomFactory("worldgen_region_random")}
     * derivation at the center chunk origin.
     */
    public static Mc263WorldGenRegionRandom atCenterChunk(
            long worldSeed, int centerChunkX, int centerChunkZ) {
        final int blockX;
        final int blockZ;
        try {
            blockX = Math.multiplyExact(centerChunkX, Blocks.CHUNK_X);
            blockZ = Math.multiplyExact(centerChunkZ, Blocks.CHUNK_Z);
        } catch (ArithmeticException overflow) {
            throw new IllegalArgumentException(
                    "WorldGenRegion center chunk origin exceeds signed block coordinates",
                    overflow);
        }
        Core root = seeded(worldSeed);
        long rootFactoryLo = root.nextLong();
        long rootFactoryHi = root.nextLong();
        Core named = new Core(rootFactoryLo ^ WORLDGEN_REGION_RANDOM_HASH_LO,
                rootFactoryHi ^ WORLDGEN_REGION_RANDOM_HASH_HI);
        long namedFactoryLo = named.nextLong();
        long namedFactoryHi = named.nextLong();
        return fromState(namedFactoryLo ^ coordinateSeed(blockX, 0, blockZ),
                namedFactoryHi, 0, false, 0L);
    }

    public State snapshot() {
        return State.canonical(lo, hi, drawCount, gaussianPresent, gaussianBits);
    }

    public Mc263WorldGenRegionRandom copy() {
        return new Mc263WorldGenRegionRandom(snapshot());
    }

    /** Creates an isolated mutable candidate without changing the caller. */
    public Mc263WorldGenRegionRandom forkForTransaction() {
        return copy();
    }

    /**
     * Installs a transaction candidate only while this object is still the exact predecessor.
     * A stale predecessor returns {@code false} and leaves every source/cache bit unchanged.
     */
    public boolean commitIfExactPredecessor(
            State exactPredecessor, Mc263WorldGenRegionRandom accepted) {
        Objects.requireNonNull(exactPredecessor, "exactPredecessor");
        Objects.requireNonNull(accepted, "accepted");
        if (!snapshot().equals(exactPredecessor)) return false;
        replaceWith(accepted.snapshot());
        return true;
    }

    /**
     * Restores a failed transaction only while the exact candidate successor is still installed.
     * This cannot overwrite a newer continuation.
     */
    public boolean rollbackIfExactSuccessor(State exactSuccessor, State predecessor) {
        Objects.requireNonNull(exactSuccessor, "exactSuccessor");
        Objects.requireNonNull(predecessor, "predecessor");
        if (!snapshot().equals(exactSuccessor)) return false;
        replaceWith(predecessor);
        return true;
    }

    /** Direct {@code RandomSource.nextInt(bound)} using the low 32 bits and Lemire rejection. */
    public int nextInt(int bound) {
        if (bound <= 0) throw new IllegalArgumentException("bound must be positive");
        long random = Integer.toUnsignedLong((int) nextRawLong());
        long product = random * bound;
        long low = product & 0xFFFF_FFFFL;
        if (low < Integer.toUnsignedLong(bound)) {
            long threshold = Integer.toUnsignedLong(Integer.remainderUnsigned(-bound, bound));
            while (low < threshold) {
                random = Integer.toUnsignedLong((int) nextRawLong());
                product = random * bound;
                low = product & 0xFFFF_FFFFL;
            }
        }
        return (int) (product >>> 32);
    }

    /** Direct {@code XoroshiroRandomSource.nextBoolean()}: low bit of one raw transition. */
    @Override
    public boolean nextBoolean() {
        return (nextRawLong() & 1L) != 0L;
    }

    /** Direct {@code XoroshiroRandomSource.nextLong()}: one raw source transition. */
    public long nextLong() {
        return nextRawLong();
    }

    /** Direct {@code RandomSource.nextFloat()}: high 24 bits of one raw source transition. */
    public float nextFloat() {
        return (float) (nextRawLong() >>> 40) * 0x1.0p-24F;
    }

    /** Direct {@code RandomSource.nextDouble()}: high 53 bits of one raw source transition. */
    public double nextDouble() {
        return (nextRawLong() >>> 11) * 0x1.0p-53;
    }

    /** Exact wrapper-owned Marsaglia polar Gaussian cache. */
    public double nextGaussian() {
        if (gaussianPresent) {
            long cached = gaussianBits;
            gaussianPresent = false;
            gaussianBits = 0L;
            return Double.longBitsToDouble(cached);
        }
        while (true) {
            double x = 2.0 * nextDouble() - 1.0;
            double y = 2.0 * nextDouble() - 1.0;
            double radiusSquared = x * x + y * y;
            if (radiusSquared >= 1.0 || radiusSquared == 0.0) continue;
            double multiplier = Math.sqrt(-2.0 * Math.log(radiusSquared) / radiusSquared);
            double cached = y * multiplier;
            if (!Double.isFinite(cached)) {
                throw new IllegalStateException("non-finite WorldGenRegion Gaussian cache");
            }
            gaussianPresent = true;
            gaussianBits = Double.doubleToRawLongBits(cached);
            return x * multiplier;
        }
    }

    /** Eight raw next-long previews from the exact current source state, without mutation. */
    public List<Long> continuationNextLongI64() {
        Mc263WorldGenRegionRandom copy = copy();
        ArrayList<Long> continuation = new ArrayList<>(CONTINUATION_WIDTH);
        for (int index = 0; index < CONTINUATION_WIDTH; index++) {
            continuation.add(copy.nextLong());
        }
        return List.copyOf(continuation);
    }

    public byte[] receipt() {
        return snapshot().receipt();
    }

    public String receiptSha256() {
        return snapshot().receiptSha256();
    }

    public static State decodeReceipt(byte[] receipt) {
        Objects.requireNonNull(receipt, "receipt");
        if (receipt.length != RECEIPT_BYTES) {
            throw new IllegalArgumentException("WorldGenRegion random receipt length must be "
                    + RECEIPT_BYTES);
        }
        ByteBuffer input = ByteBuffer.wrap(receipt).order(ByteOrder.BIG_ENDIAN);
        byte[] magic = new byte[RECEIPT_MAGIC.length];
        input.get(magic);
        if (!Arrays.equals(magic, RECEIPT_MAGIC)) {
            throw new IllegalArgumentException("WorldGenRegion random receipt magic mismatch");
        }
        int version = Short.toUnsignedInt(input.getShort());
        if (version != RECEIPT_VERSION) {
            throw new IllegalArgumentException("WorldGenRegion random receipt version mismatch");
        }
        long decodedLo = input.getLong();
        long decodedHi = input.getLong();
        if ((decodedLo | decodedHi) == 0L) {
            throw new IllegalArgumentException("noncanonical all-zero WorldGenRegion source state");
        }
        int count = input.getInt();
        int rawBoolean = Byte.toUnsignedInt(input.get());
        if (rawBoolean > 1) {
            throw new IllegalArgumentException("noncanonical WorldGenRegion Gaussian boolean");
        }
        long decodedGaussianBits = input.getLong();
        State decoded = State.canonical(decodedLo, decodedHi, count,
                rawBoolean == 1, decodedGaussianBits);
        if (!Arrays.equals(receipt, decoded.receipt())) {
            throw new IllegalArgumentException("noncanonical WorldGenRegion random receipt");
        }
        return decoded;
    }

    private long nextRawLong() {
        int nextCount;
        try {
            nextCount = Math.incrementExact(drawCount);
        } catch (ArithmeticException error) {
            throw new IllegalStateException("WorldGenRegion random draw count overflow", error);
        }
        long first = lo;
        long second = hi;
        long result = Long.rotateLeft(first + second, 17) + first;
        second ^= first;
        lo = Long.rotateLeft(first, 49) ^ second ^ (second << 21);
        hi = Long.rotateLeft(second, 28);
        drawCount = nextCount;
        return result;
    }

    private void replaceWith(State state) {
        lo = state.lo;
        hi = state.hi;
        drawCount = state.drawCount;
        gaussianPresent = state.gaussianPresent;
        gaussianBits = state.gaussianBits;
    }

    private static Core seeded(long seed) {
        long seedLo = seed ^ SILVER_RATIO_64;
        long seedHi = seedLo + GOLDEN_RATIO_64;
        return new Core(mixStafford13(seedLo), mixStafford13(seedHi));
    }

    private static long mixStafford13(long value) {
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }

    private static long coordinateSeed(int x, int y, int z) {
        // Vanilla Mth#getSeed multiplies x as a signed i32 (imul) and only then widens (i2l);
        // a 64-bit x term diverges for |x| > 686.
        long value = (long) (x * 3_129_871) ^ (long) z * 116_129_781L ^ y;
        value = value * value * 42_317_861L + value * 11L;
        return value >> 16;
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    /** Immutable, canonical current-state snapshot. */
    public static final class State {
        private final long lo;
        private final long hi;
        private final int drawCount;
        private final boolean gaussianPresent;
        private final long gaussianBits;

        private State(long lo, long hi, int drawCount, boolean gaussianPresent, long gaussianBits) {
            this.lo = lo;
            this.hi = hi;
            this.drawCount = drawCount;
            this.gaussianPresent = gaussianPresent;
            this.gaussianBits = gaussianBits;
        }

        private static State canonical(long lo, long hi, int drawCount,
                boolean gaussianPresent, long gaussianBits) {
            if (drawCount < 0) {
                throw new IllegalArgumentException("negative WorldGenRegion random draw count");
            }
            if ((lo | hi) == 0L) {
                lo = GOLDEN_RATIO_64;
                hi = SILVER_RATIO_64;
            }
            if (!gaussianPresent && gaussianBits != 0L) {
                throw new IllegalArgumentException("absent WorldGenRegion Gaussian cache has bits");
            }
            if (gaussianPresent && !Double.isFinite(Double.longBitsToDouble(gaussianBits))) {
                throw new IllegalArgumentException("non-finite WorldGenRegion Gaussian cache");
            }
            return new State(lo, hi, drawCount, gaussianPresent, gaussianBits);
        }

        public long lo() { return lo; }
        public long hi() { return hi; }
        public int drawCount() { return drawCount; }
        public boolean gaussianPresent() { return gaussianPresent; }
        public long gaussianBits() { return gaussianBits; }

        public byte[] receipt() {
            ByteBuffer output = ByteBuffer.allocate(RECEIPT_BYTES).order(ByteOrder.BIG_ENDIAN);
            output.put(RECEIPT_MAGIC);
            output.putShort((short) RECEIPT_VERSION);
            output.putLong(lo);
            output.putLong(hi);
            output.putInt(drawCount);
            output.put((byte) (gaussianPresent ? 1 : 0));
            output.putLong(gaussianBits);
            return output.array();
        }

        public String receiptSha256() {
            return sha256(receipt());
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof State state)) return false;
            return lo == state.lo && hi == state.hi && drawCount == state.drawCount
                    && gaussianPresent == state.gaussianPresent && gaussianBits == state.gaussianBits;
        }

        @Override
        public int hashCode() {
            return Objects.hash(lo, hi, drawCount, gaussianPresent, gaussianBits);
        }

        @Override
        public String toString() {
            return "State[lo=" + Long.toUnsignedString(lo)
                    + ", hi=" + Long.toUnsignedString(hi)
                    + ", drawCount=" + drawCount
                    + ", gaussianPresent=" + gaussianPresent
                    + ", gaussianBits=" + Long.toUnsignedString(gaussianBits) + "]";
        }
    }

    /** Small derivation-only core; mutable placement state always lives in the outer class. */
    private static final class Core {
        private long lo;
        private long hi;

        private Core(long lo, long hi) {
            if ((lo | hi) == 0L) {
                this.lo = GOLDEN_RATIO_64;
                this.hi = SILVER_RATIO_64;
            } else {
                this.lo = lo;
                this.hi = hi;
            }
        }

        private long nextLong() {
            long first = lo;
            long second = hi;
            long result = Long.rotateLeft(first + second, 17) + first;
            second ^= first;
            lo = Long.rotateLeft(first, 49) ^ second ^ (second << 21);
            hi = Long.rotateLeft(second, 28);
            return result;
        }
    }
}
