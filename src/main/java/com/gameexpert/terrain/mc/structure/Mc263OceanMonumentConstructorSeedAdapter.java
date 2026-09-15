package com.gameexpert.terrain.mc.structure;

import com.gameexpert.terrain.mc.structure.Mc263HardcodedStructureCarrier.Kind;
import com.gameexpert.terrain.mc.structure.Mc263HardcodedStructureCarrier.Rotation;
import java.util.Arrays;
import java.util.Objects;

/** Exact bridge between production large-feature seeding and the direct oracle constructor seed. */
public final class Mc263OceanMonumentConstructorSeedAdapter {
    private static final long MASK = (1L << 48) - 1;
    private static final long MULTIPLIER = 25214903917L;
    private static final long INCREMENT = 11L;

    public enum Provenance { PRODUCTION_DERIVED, OFFICIAL_DIRECT_CONSTRUCTOR }

    public record SeedBinding(Provenance provenance, long worldSeed, int chunkX, int chunkZ,
            long xScale, long zScale, long constructorSeed,
            long constructorStartRawState48, long graphStartRawState48,
            Rotation direction) {
        public SeedBinding {
            Objects.requireNonNull(provenance, "monument constructor provenance");
            Objects.requireNonNull(direction, "monument constructor direction");
            if ((constructorStartRawState48 & ~MASK) != 0
                    || (graphStartRawState48 & ~MASK) != 0) {
                throw new IllegalArgumentException("invalid monument constructor RNG state");
            }
        }
    }

    private Mc263OceanMonumentConstructorSeedAdapter() { }

    public static SeedBinding productionBinding(long worldSeed, int chunkX, int chunkZ) {
        Legacy48 seedRandom = new Legacy48(worldSeed);
        long xScale = seedRandom.nextLong();
        long zScale = seedRandom.nextLong();
        long constructorSeed = (long) chunkX * xScale ^ (long) chunkZ * zScale ^ worldSeed;
        Legacy48 placement = new Legacy48(constructorSeed);
        long constructorStartRaw = placement.raw;
        Rotation direction = direction(placement.nextInt(4));
        long graphRaw = placement.raw;
        return new SeedBinding(Provenance.PRODUCTION_DERIVED, worldSeed, chunkX, chunkZ,
                xScale, zScale, constructorSeed, constructorStartRaw, graphRaw, direction);
    }

    /** Replans and byte-compares the production carrier before exposing its direct-seed binding. */
    public static SeedBinding requireProductionCarrier(Mc263HardcodedStructureCarrier carrier) {
        Objects.requireNonNull(carrier, "monument production carrier");
        if (carrier.kind() != Kind.OCEAN_MONUMENT) {
            throw new IllegalArgumentException("constructor seed adapter requires monument");
        }
        Mc263HardcodedStructureCarrier replanned = Mc263HardcodedStructureCarrier.plan(
                Kind.OCEAN_MONUMENT, carrier.worldSeed(), carrier.chunkX(), carrier.chunkZ());
        if (!Arrays.equals(carrier.encodeCanonical(), replanned.encodeCanonical())) {
            throw new IllegalArgumentException("monument production carrier seed drift");
        }
        SeedBinding binding = productionBinding(
                carrier.worldSeed(), carrier.chunkX(), carrier.chunkZ());
        if (binding.direction() != carrier.rotation()) {
            throw new IllegalArgumentException("monument direct constructor direction drift");
        }
        return binding;
    }

    /** Exact official direct-constructor boundary: direction is explicit and consumes no draw. */
    public static SeedBinding directBinding(long constructorSeed, Rotation direction) {
        Objects.requireNonNull(direction, "direct monument direction");
        if (direction != Rotation.NORTH && direction != Rotation.EAST
                && direction != Rotation.SOUTH && direction != Rotation.WEST) {
            throw new IllegalArgumentException("direct monument direction is not cardinal");
        }
        Legacy48 constructor = new Legacy48(constructorSeed);
        long raw = constructor.raw;
        return new SeedBinding(Provenance.OFFICIAL_DIRECT_CONSTRUCTOR, 0, 0, 0, 0, 0,
                constructorSeed, raw, raw, direction);
    }

    private static Rotation direction(int ordinal) {
        return switch (ordinal) {
            case 0 -> Rotation.NORTH;
            case 1 -> Rotation.EAST;
            case 2 -> Rotation.SOUTH;
            case 3 -> Rotation.WEST;
            default -> throw new IllegalArgumentException("unknown monument direction");
        };
    }

    private static final class Legacy48 {
        private long raw;
        private Legacy48(long seed) { raw = (seed ^ MULTIPLIER) & MASK; }
        private int next(int bits) {
            raw = (raw * MULTIPLIER + INCREMENT) & MASK;
            return (int) (raw >>> (48 - bits));
        }
        private int nextInt(int bound) {
            if ((bound & -bound) == bound) return (int) ((bound * (long) next(31)) >> 31);
            int bits, value;
            do { bits = next(31); value = bits % bound; }
            while (bits - value + bound - 1 < 0);
            return value;
        }
        private long nextLong() { return ((long) next(32) << 32) + next(32); }
    }
}
