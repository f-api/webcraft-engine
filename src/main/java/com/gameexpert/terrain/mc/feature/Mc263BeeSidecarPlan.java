package com.gameexpert.terrain.mc.feature;

import com.gameexpert.terrain.Blocks;
import com.gameexpert.authority.versioned.NeutralFinalChunk;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Pure projection of an already-visible schema-4 {@code BEES} lane.
 *
 * <p>This class deliberately has no authority hooks. It does not allocate entity identities, advance
 * hive time, persist receipts, or expose the seeds to a live mob runtime.</p>
 */
public final class Mc263BeeSidecarPlan {
    private static final byte[] RECEIPT_MAGIC = "MC263BEE".getBytes(StandardCharsets.US_ASCII);
    private static final int RECEIPT_VERSION = 1;

    private Mc263BeeSidecarPlan() { }

    public record OccupantSeed(int ordinal, int ticksInHive) {
        public OccupantSeed {
            if (ordinal < 0 || ordinal > 0xffff) {
                throw new IllegalArgumentException("bee occupant ordinal outside u16: " + ordinal);
            }
            if (ticksInHive < 0 || ticksInHive > 598) {
                throw new IllegalArgumentException(
                        "bee occupant ticks in hive outside 0..598: " + ticksInHive);
            }
        }
    }

    public record NestSeed(int packed, int worldX, int worldY, int worldZ,
                           List<OccupantSeed> occupants, long durableFingerprint) {
        public NestSeed {
            requirePacked(packed);
            occupants = List.copyOf(Objects.requireNonNull(occupants, "occupants"));
            if (occupants.isEmpty()) {
                throw new IllegalArgumentException("bee nest seed must contain an occupant");
            }
            if (occupants.size() > 3) {
                throw new IllegalArgumentException("bee nest occupant count exceeds capacity 3");
            }
            for (int index = 0; index < occupants.size(); index++) {
                OccupantSeed occupant = Objects.requireNonNull(occupants.get(index), "occupant");
                if (occupant.ordinal() != index) {
                    throw new IllegalArgumentException("bee occupant ordinals are not contiguous");
                }
            }
            long expectedFingerprint = Mc263BeeSidecarPlan.durableFingerprint(
                    packed, worldX, worldY, worldZ, occupants);
            if (durableFingerprint != expectedFingerprint) {
                throw new IllegalArgumentException("bee nest durable fingerprint mismatch");
            }
        }
    }

    public record Plan(int chunkX, int chunkZ, List<NestSeed> nests) {
        public Plan {
            nests = List.copyOf(Objects.requireNonNull(nests, "nests"));
            Set<Integer> packedPositions = new HashSet<>();
            for (NestSeed nest : nests) {
                Objects.requireNonNull(nest, "nest");
                if (!packedPositions.add(nest.packed())) {
                    throw new IllegalArgumentException(
                            "duplicate bee nest sidecar at packed position " + nest.packed());
                }
                int[] expected = worldPosition(chunkX, chunkZ, nest.packed());
                if (nest.worldX() != expected[0] || nest.worldY() != expected[1]
                        || nest.worldZ() != expected[2]) {
                    throw new IllegalArgumentException("bee nest seed world position mismatch");
                }
            }
        }

        /**
         * Canonical non-persistent plan receipt shared with the standalone adapter.
         *
         * <p>Encoding is big-endian: magic[8], version u16, chunk X/Z i32, nest count u32, then
         * insertion-ordered {@code packed u32, world X/Y/Z i32, occupant count u16}, followed by
         * insertion-ordered {@code ordinal u16, ticksInHive u16} occupants.</p>
         */
        public byte[] receiptBytes() {
            long length = 22L;
            for (NestSeed nest : nests) {
                length += 18L + 4L * nest.occupants().size();
                if (length > Integer.MAX_VALUE) {
                    throw new IllegalStateException("bee sidecar plan receipt exceeds byte-array limit");
                }
            }
            try {
                ByteArrayOutputStream bytes = new ByteArrayOutputStream((int) length);
                DataOutputStream out = new DataOutputStream(bytes);
                out.write(RECEIPT_MAGIC);
                out.writeShort(RECEIPT_VERSION);
                out.writeInt(chunkX);
                out.writeInt(chunkZ);
                out.writeInt(nests.size());
                for (NestSeed nest : nests) {
                    out.writeInt(nest.packed());
                    out.writeInt(nest.worldX());
                    out.writeInt(nest.worldY());
                    out.writeInt(nest.worldZ());
                    out.writeShort(nest.occupants().size());
                    for (OccupantSeed occupant : nest.occupants()) {
                        out.writeShort(occupant.ordinal());
                        out.writeShort(occupant.ticksInHive());
                    }
                }
                out.flush();
                return bytes.toByteArray();
            } catch (IOException impossible) {
                throw new IllegalStateException(impossible);
            }
        }
    }

    public static Plan prepare(int chunkX, int chunkZ,
            List<NeutralFinalChunk.BeeNest> visibleBees) {
        Objects.requireNonNull(visibleBees, "visibleBees");
        Set<Integer> packedPositions = new HashSet<>();
        java.util.ArrayList<NestSeed> nests = new java.util.ArrayList<>(visibleBees.size());
        for (NeutralFinalChunk.BeeNest source : visibleBees) {
            Objects.requireNonNull(source, "bee nest sidecar");
            int packed = source.packed();
            requirePacked(packed);
            if (!packedPositions.add(packed)) {
                throw new IllegalArgumentException(
                        "duplicate bee nest sidecar at packed position " + packed);
            }
            List<Integer> ticks = Objects.requireNonNull(source.ticksInHive(), "ticksInHive");
            if (ticks.isEmpty()) {
                throw new IllegalArgumentException("bee nest sidecar must contain an occupant");
            }
            if (ticks.size() > 3) {
                throw new IllegalArgumentException("bee nest occupant count exceeds capacity 3");
            }
            java.util.ArrayList<OccupantSeed> occupants = new java.util.ArrayList<>(ticks.size());
            for (int ordinal = 0; ordinal < ticks.size(); ordinal++) {
                occupants.add(new OccupantSeed(ordinal,
                        Objects.requireNonNull(ticks.get(ordinal), "ticksInHive value")));
            }
            int[] world = worldPosition(chunkX, chunkZ, packed);
            nests.add(new NestSeed(packed, world[0], world[1], world[2], occupants,
                    Mc263BeeSidecarPlan.durableFingerprint(
                            packed, world[0], world[1], world[2], occupants)));
        }
        return new Plan(chunkX, chunkZ, nests);
    }

    /** Stable unsigned FNV-1a receipt for one exact durable nest claim. */
    private static long durableFingerprint(int packed, int worldX, int worldY, int worldZ,
            List<OccupantSeed> occupants) {
        int hash = 0x811c9dc5;
        hash = fnvInt(hash, packed);
        hash = fnvInt(hash, worldX);
        hash = fnvInt(hash, worldY);
        hash = fnvInt(hash, worldZ);
        hash = fnvShort(hash, occupants.size());
        for (OccupantSeed occupant : occupants) {
            hash = fnvShort(hash, occupant.ordinal());
            hash = fnvShort(hash, occupant.ticksInHive());
        }
        return Integer.toUnsignedLong(hash);
    }

    private static int fnvInt(int hash, int value) {
        hash = fnvByte(hash, value >>> 24);
        hash = fnvByte(hash, value >>> 16);
        hash = fnvByte(hash, value >>> 8);
        return fnvByte(hash, value);
    }

    private static int fnvShort(int hash, int value) {
        hash = fnvByte(hash, value >>> 8);
        return fnvByte(hash, value);
    }

    private static int fnvByte(int hash, int value) {
        return (hash ^ value & 0xff) * 0x01000193;
    }

    private static int[] worldPosition(int chunkX, int chunkZ, int packed) {
        requirePacked(packed);
        int horizontal = packed % (Blocks.CHUNK_X * Blocks.CHUNK_X);
        int localX = horizontal % Blocks.CHUNK_X;
        int localZ = horizontal / Blocks.CHUNK_X;
        int worldY = packed / (Blocks.CHUNK_X * Blocks.CHUNK_X) + Blocks.MIN_Y;
        try {
            int worldX = Math.addExact(Math.multiplyExact(chunkX, Blocks.CHUNK_X), localX);
            int worldZ = Math.addExact(Math.multiplyExact(chunkZ, Blocks.CHUNK_X), localZ);
            return new int[] {worldX, worldY, worldZ};
        } catch (ArithmeticException overflow) {
            throw new IllegalArgumentException("bee nest world position exceeds signed-32", overflow);
        }
    }

    private static void requirePacked(int packed) {
        if (packed < 0 || packed >= Blocks.CHUNK_BLOCKS) {
            throw new IllegalArgumentException(
                    "bee nest packed position outside chunk: " + packed);
        }
    }
}
