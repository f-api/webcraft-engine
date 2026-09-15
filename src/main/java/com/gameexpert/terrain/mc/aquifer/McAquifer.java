package com.gameexpert.terrain.mc.aquifer;

import com.gameexpert.terrain.Blocks;
import com.gameexpert.terrain.mc.Mc263NoiseRegistry;
import com.gameexpert.terrain.mc.McNormalNoise;
import com.gameexpert.terrain.mc.McRandom;
import com.gameexpert.terrain.mc.df.McDensityFunction;
import java.util.function.DoubleSupplier;
import java.util.function.Supplier;

/**
 * Minecraft Java 26.3-snapshot-7 NoiseBasedAquifer material-decision port.
 *
 * <p>The immutable noise/function inputs are shared per seed while site and fluid-status caches
 * live for one target chunk. Structure and configured-carver blocks are applied after this base
 * material decision and are therefore never refilled by the aquifer.</p>
 */
public final class McAquifer {
    private static final int X_SPACING = 16;
    private static final int Y_SPACING = 12;
    private static final int Z_SPACING = 16;
    private static final int X_RANGE = 10;
    private static final int Y_RANGE = 9;
    private static final int Z_RANGE = 10;
    private static final int LAVA_LEVEL = -54;
    /** `overworld/aquifers.fluid_level_spread` y_scale. */
    private static final double SPREAD_Y_SCALE = 0.7142857142857143D;
    private static final int DRY_LEVEL = -32512;
    private static final double FLOWING_UPDATE_SIMILARITY = similarity(100, 144);
    private static final int[][] SURFACE_SAMPLING_OFFSETS = {
        {0, 0}, {-2, -1}, {-1, -1}, {0, -1}, {1, -1},
        {-3, 0}, {-2, 0}, {-1, 0}, {1, 0},
        {-2, 1}, {-1, 1}, {0, 1}, {1, 1}
    };

    private final Inputs inputs;
    private final PreliminarySurface preliminarySurface;
    private final int gridMinX;
    private final int gridMinY;
    private final int gridMinZ;
    private final int gridCountX;
    private final int gridCountY;
    private final int gridCountZ;
    private final Site[] sites;
    private final FluidStatus[] statuses;

    public McAquifer(Inputs inputs, int chunkMinX, int chunkMinZ,
            PreliminarySurface preliminarySurface) {
        this.inputs = inputs;
        this.preliminarySurface = preliminarySurface;
        gridMinX = Math.floorDiv(chunkMinX, X_SPACING) - 1;
        gridMinY = Math.floorDiv(Blocks.MIN_Y, Y_SPACING) - 1;
        gridMinZ = Math.floorDiv(chunkMinZ, Z_SPACING) - 1;
        int gridMaxX = Math.floorDiv(chunkMinX + Blocks.CHUNK_X - 1, X_SPACING) + 1;
        int gridMaxY = Math.floorDiv(Blocks.MAX_Y + 1, Y_SPACING) + 1;
        int gridMaxZ = Math.floorDiv(chunkMinZ + Blocks.CHUNK_Z - 1, Z_SPACING) + 1;
        gridCountX = gridMaxX - gridMinX + 1;
        gridCountY = gridMaxY - gridMinY + 1;
        gridCountZ = gridMaxZ - gridMinZ + 1;
        int cacheSize = gridCountX * gridCountY * gridCountZ;
        sites = new Site[cacheSize];
        statuses = new FluidStatus[cacheSize];
    }

    public static Inputs loadInputs(long seed, McDensityFunction erosion,
            McDensityFunction depth) {
        McRandom.PositionalFactory rootRandom = new McRandom(seed).forkPositional();
        NoiseSet noises = new NoiseSet(
                noise(rootRandom, "minecraft:aquifer_fluid_level_floodedness"),
                noise(rootRandom, "minecraft:aquifer_fluid_level_spread"),
                noise(rootRandom, "minecraft:aquifer_lava"),
                noise(rootRandom, "minecraft:aquifer_barrier"));
        McRandom.PositionalFactory aquiferRandom =
                rootRandom.fromHashOf("minecraft:aquifer").forkPositional();
        return new Inputs(seed, noises, erosion, depth, aquiferRandom);
    }

    /**
     * Returns the base material selected by the exact aquifer leaf. Positive density always
     * yields the default stone; non-positive density may still yield stone when an aquifer
     * pressure barrier separates incompatible local fluid levels.
     */
    public int blockForDensity(int x, int y, int z, double density) {
        SubstanceResult result = substance(x, y, z, density);
        return switch (result.material()) {
            case SOLID -> Blocks.STONE;
            case AIR -> Blocks.AIR;
            case WATER -> Blocks.WATER_SOURCE;
            case LAVA -> Blocks.LAVA_SOURCE;
        };
    }

    /**
     * Returns the immutable official aquifer result, including whether the selected fluid must be
     * added to the chunk's post-processing list. The scheduling bit is evidence only until the
     * canonical chunk caller explicitly consumes it.
     */
    public SubstanceResult substance(int x, int y, int z, double density) {
        return computeSubstance(x, y, z, density);
    }

    /** Raw proof output for Java/TypeScript/Rust parity vectors. */
    public Sample sample(int x, int y, int z, double density) {
        SubstanceResult result = substance(x, y, z, density);
        return new Sample(result.material(), result.fluidLevel(), density,
                inputs.noises.floodedness.getValue(x, y * 0.67D, z),
                inputs.noises.spread.getValue(x, y * SPREAD_Y_SCALE, z),
                inputs.noises.lava.getValue(x, y, z),
                inputs.noises.barrier.getValue(x, y * 0.5D, z));
    }

    public long seed() {
        return inputs.seed;
    }

    private SubstanceResult computeSubstance(int x, int y, int z, double density) {
        if (density > 0.0D) return result(Material.SOLID, Integer.MAX_VALUE, false);

        FluidStatus global = globalFluid(y);
        if (global.fluidAt(y) == Material.LAVA) {
            return result(Material.LAVA, global.level, false);
        }

        int gridX = Math.floorDiv(x - 5, X_SPACING);
        int gridY = Math.floorDiv(y + 1, Y_SPACING);
        int gridZ = Math.floorDiv(z - 5, Z_SPACING);
        int distance0 = Integer.MAX_VALUE;
        int distance1 = Integer.MAX_VALUE;
        int distance2 = Integer.MAX_VALUE;
        int distance3 = Integer.MAX_VALUE;
        Site nearest0 = null;
        Site nearest1 = null;
        Site nearest2 = null;
        Site nearest3 = null;

        for (int dx = 0; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = 0; dz <= 1; dz++) {
                    Site site = site(gridX + dx, gridY + dy, gridZ + dz);
                    int sx = site.x - x;
                    int sy = site.y - y;
                    int sz = site.z - z;
                    int distance = sx * sx + sy * sy + sz * sz;
                    if (distance0 >= distance) {
                        nearest3 = nearest2;
                        nearest2 = nearest1;
                        nearest1 = nearest0;
                        nearest0 = site;
                        distance3 = distance2;
                        distance2 = distance1;
                        distance1 = distance0;
                        distance0 = distance;
                    } else if (distance1 >= distance) {
                        nearest3 = nearest2;
                        nearest2 = nearest1;
                        nearest1 = site;
                        distance3 = distance2;
                        distance2 = distance1;
                        distance1 = distance;
                    } else if (distance2 >= distance) {
                        nearest3 = nearest2;
                        nearest2 = site;
                        distance3 = distance2;
                        distance2 = distance;
                    } else if (distance3 >= distance) {
                        nearest3 = site;
                        distance3 = distance;
                    }
                }
            }
        }

        FluidStatus status0 = status(nearest0);
        double similarity01 = similarity(distance0, distance1);
        Material nearestMaterial = status0.fluidAt(y);
        if (similarity01 <= 0.0D) {
            boolean shouldSchedule = similarity01 >= FLOWING_UPDATE_SIMILARITY
                    && !status0.equals(status(nearest1));
            return result(nearestMaterial, status0.level, shouldSchedule);
        }

        if (nearestMaterial == Material.WATER
                && globalFluid(y - 1).fluidAt(y - 1) == Material.LAVA) {
            return result(Material.WATER, status0.level, true);
        }

        FluidStatus status1 = status(nearest1);
        LazyBarrier barrier = new LazyBarrier(
                () -> inputs.noises.barrier.getValue(x, y * 0.5D, z));
        double pressure01 = similarity01 * calculatePressure(y, barrier, status0, status1);
        if (density + pressure01 > 0.0D) {
            return result(Material.SOLID, status0.level, false);
        }

        FluidStatus status2 = status(nearest2);
        double similarity02 = similarity(distance0, distance2);
        if (similarity02 > 0.0D) {
            double pressure02 = similarity01 * similarity02
                    * calculatePressure(y, barrier, status0, status2);
            if (density + pressure02 > 0.0D) {
                return result(Material.SOLID, status0.level, false);
            }
        }

        double similarity12 = similarity(distance1, distance2);
        if (similarity12 > 0.0D) {
            double pressure12 = similarity01 * similarity12
                    * calculatePressure(y, barrier, status1, status2);
            if (density + pressure12 > 0.0D) {
                return result(Material.SOLID, status0.level, false);
            }
        }
        Site fourth = nearest3;
        boolean shouldSchedule = flowingUpdateNeeded(
                distance0, distance1, distance2, distance3,
                status0, status1, status2, () -> status(fourth));
        return result(nearestMaterial, status0.level, shouldSchedule);
    }

    private static boolean flowingUpdateNeeded(int distance0, int distance1,
            int distance2, int distance3, FluidStatus status0, FluidStatus status1,
            FluidStatus status2, FluidStatus status3) {
        return flowingUpdateNeeded(distance0, distance1, distance2, distance3,
                status0, status1, status2, () -> status3);
    }

    private static boolean flowingUpdateNeeded(int distance0, int distance1,
            int distance2, int distance3, FluidStatus status0, FluidStatus status1,
            FluidStatus status2, Supplier<FluidStatus> status3) {
        double similarity02 = similarity(distance0, distance2);
        boolean mayFlow01 = !status0.equals(status1);
        boolean mayFlow12 = similarity(distance1, distance2) >= FLOWING_UPDATE_SIMILARITY
                && !status1.equals(status2);
        boolean mayFlow02 = similarity02 >= FLOWING_UPDATE_SIMILARITY
                && !status0.equals(status2);
        if (mayFlow01 || mayFlow12 || mayFlow02) return true;
        return similarity02 >= FLOWING_UPDATE_SIMILARITY
                && similarity(distance0, distance3) >= FLOWING_UPDATE_SIMILARITY
                && !status0.equals(status3.get());
    }

    private static double similarity(int firstDistance, int secondDistance) {
        if (secondDistance < firstDistance) {
            throw new IllegalArgumentException("aquifer sites are not distance-ordered");
        }
        return 1.0D - (secondDistance - firstDistance) / 25.0D;
    }

    private static double calculatePressure(int y, double barrier,
            FluidStatus first, FluidStatus second) {
        return calculatePressure(y, new LazyBarrier(() -> barrier), first, second);
    }

    private static double calculatePressure(int y, LazyBarrier barrier,
            FluidStatus first, FluidStatus second) {
        Material firstMaterial = first.fluidAt(y);
        Material secondMaterial = second.fluidAt(y);
        if ((firstMaterial == Material.LAVA && secondMaterial == Material.WATER)
                || (firstMaterial == Material.WATER && secondMaterial == Material.LAVA)) {
            return 2.0D;
        }
        int levelDifference = Math.abs(first.level - second.level);
        if (levelDifference == 0) return 0.0D;

        double midpoint = 0.5D * (first.level + second.level);
        double offset = y + 0.5D - midpoint;
        double halfDifference = levelDifference / 2.0D;
        double distanceInside = halfDifference - Math.abs(offset);
        double pressure;
        if (offset > 0.0D) {
            pressure = distanceInside > 0.0D
                    ? distanceInside / 1.5D : distanceInside / 2.5D;
        } else {
            double below = 3.0D + distanceInside;
            pressure = below > 0.0D ? below / 3.0D : below / 10.0D;
        }
        double barrierContribution = pressure < -2.0D || pressure > 2.0D
                ? 0.0D : barrier.value();
        return 2.0D * (barrierContribution + pressure);
    }

    private static final class LazyBarrier {
        private final DoubleSupplier supplier;
        private double value = Double.NaN;

        private LazyBarrier(DoubleSupplier supplier) {
            this.supplier = supplier;
        }

        private double value() {
            if (Double.isNaN(value)) value = supplier.getAsDouble();
            return value;
        }
    }

    private Site site(int gridX, int gridY, int gridZ) {
        int index = gridIndex(gridX, gridY, gridZ);
        Site cached = sites[index];
        if (cached != null) return cached;
        McRandom random = inputs.aquiferRandom.at(gridX, gridY, gridZ);
        Site created = new Site(
                gridX * X_SPACING + random.nextInt(X_RANGE),
                gridY * Y_SPACING + random.nextInt(Y_RANGE),
                gridZ * Z_SPACING + random.nextInt(Z_RANGE),
                gridX, gridY, gridZ);
        sites[index] = created;
        return created;
    }

    private FluidStatus status(Site site) {
        int index = gridIndex(site.gridX, site.gridY, site.gridZ);
        FluidStatus cached = statuses[index];
        if (cached != null) return cached;
        FluidStatus created = computeStatus(site.x, site.y, site.z);
        statuses[index] = created;
        return created;
    }

    private int gridIndex(int gridX, int gridY, int gridZ) {
        int localX = gridX - gridMinX;
        int localY = gridY - gridMinY;
        int localZ = gridZ - gridMinZ;
        if (localX < 0 || localX >= gridCountX || localY < 0 || localY >= gridCountY
                || localZ < 0 || localZ >= gridCountZ) {
            throw new IllegalStateException("aquifer site escaped target chunk cache: "
                    + gridX + "," + gridY + "," + gridZ);
        }
        return (localY * gridCountZ + localZ) * gridCountX + localX;
    }

    private FluidStatus computeStatus(int x, int y, int z) {
        FluidStatus global = globalFluid(y);
        int minimumSurface = Integer.MAX_VALUE;
        int upperY = y + 12;
        int lowerY = y - 12;
        boolean surfaceFluid = false;

        for (int[] offset : SURFACE_SAMPLING_OFFSETS) {
            int sampleX = x + offset[0] * 16;
            int sampleZ = z + offset[1] * 16;
            int surface = preliminarySurface.levelAt(sampleX, sampleZ);
            int surfacePlusEight = surface + 8;
            boolean center = offset[0] == 0 && offset[1] == 0;
            if (center && lowerY > surfacePlusEight) return global;

            boolean aboveSurface = upperY > surfacePlusEight;
            if (aboveSurface || center) {
                FluidStatus surfaceStatus = globalFluid(surfacePlusEight);
                if (surfaceStatus.fluidAt(surfacePlusEight) != Material.AIR) {
                    if (center) surfaceFluid = true;
                    if (aboveSurface) return surfaceStatus;
                }
            }
            minimumSurface = Math.min(minimumSurface, surface);
        }

        int level = computeSurfaceLevel(x, y, z, global, minimumSurface, surfaceFluid);
        return new FluidStatus(level, computeFluidType(x, y, z, global, level));
    }

    private int computeSurfaceLevel(int x, int y, int z, FluidStatus global,
            int minimumSurface, boolean surfaceFluid) {
        McDensityFunction.Context context = new McDensityFunction.Context(x, y, z);
        double lower;
        double upper;
        if (inputs.erosion.compute(context) < -0.225D
                && inputs.depth.compute(context) > 0.9D) {
            lower = -1.0D;
            upper = -1.0D;
        } else {
            int distanceToSurface = minimumSurface + 8 - y;
            double surfaceInfluence = surfaceFluid
                    ? clampedMap(distanceToSurface, 0.0D, 64.0D, 1.0D, 0.0D)
                    : 0.0D;
            double floodedness = clamp(inputs.noises.floodedness.getValue(
                    x, y * 0.67D, z), -1.0D, 1.0D);
            double upperThreshold = clampedMap(
                    surfaceInfluence, 1.0D, 0.0D, -0.3D, 0.8D);
            double lowerThreshold = clampedMap(
                    surfaceInfluence, 1.0D, 0.0D, -0.8D, 0.4D);
            lower = floodedness - lowerThreshold;
            upper = floodedness - upperThreshold;
        }

        if (upper > 0.0D) return global.level;
        if (lower > 0.0D) return randomizedFluidLevel(x, y, z, minimumSurface);
        return DRY_LEVEL;
    }

    private int randomizedFluidLevel(int x, int y, int z, int maximumLevel) {
        int gridX = Math.floorDiv(x, 16);
        int gridY = Math.floorDiv(y, 40);
        int gridZ = Math.floorDiv(z, 16);
        int centerY = gridY * 40 + 20;
        // `overworld/aquifers.fluid_level_spread` is noise(aquifer_fluid_level_spread,
        // xz_scale = 1.0, y_scale = 0.7142857142857143), and vanilla's
        // computeRandomizedFluidSurfaceLevel feeds it the GRID coordinates through that same
        // density function — the y scale applies to the grid index, not to the block y.
        double spread = inputs.noises.spread.getValue(
                gridX, gridY * SPREAD_Y_SCALE, gridZ) * 10.0D;
        int quantized = (int) Math.floor(spread / 3.0D) * 3;
        return Math.min(maximumLevel, centerY + quantized);
    }

    private Material computeFluidType(int x, int y, int z, FluidStatus global, int level) {
        Material type = global.type;
        if (level <= -10 && level != DRY_LEVEL && global.type != Material.LAVA) {
            int gridX = Math.floorDiv(x, 64);
            int gridY = Math.floorDiv(y, 40);
            int gridZ = Math.floorDiv(z, 64);
            if (Math.abs(inputs.noises.lava.getValue(gridX, gridY, gridZ)) > 0.3D) {
                type = Material.LAVA;
            }
        }
        return type;
    }

    private static FluidStatus globalFluid(int y) {
        return y < Math.min(LAVA_LEVEL, Blocks.SEA_LEVEL)
                ? Inputs.LAVA_STATUS : Inputs.WATER_STATUS;
    }

    private static McNormalNoise noise(McRandom.PositionalFactory random, String id) {
        return Mc263NoiseRegistry.create(random, id);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double clampedMap(double value, double fromLow, double fromHigh,
            double toLow, double toHigh) {
        double delta = clamp((value - fromLow) / (fromHigh - fromLow), 0.0D, 1.0D);
        return toLow + delta * (toHigh - toLow);
    }

    private static SubstanceResult result(Material material, int level,
            boolean shouldScheduleFluidUpdate) {
        return new SubstanceResult(material, level, shouldScheduleFluidUpdate);
    }

    @FunctionalInterface
    public interface PreliminarySurface {
        int levelAt(int x, int z);
    }

    public enum Material {
        SOLID,
        AIR,
        WATER,
        LAVA
    }

    public record SubstanceResult(
            Material material,
            int fluidLevel,
            boolean shouldScheduleFluidUpdate) {
    }

    public static final class Inputs {
        private static final FluidStatus LAVA_STATUS =
                new FluidStatus(LAVA_LEVEL, Material.LAVA);
        private static final FluidStatus WATER_STATUS =
                new FluidStatus(Blocks.SEA_LEVEL, Material.WATER);

        private final long seed;
        private final NoiseSet noises;
        private final McDensityFunction erosion;
        private final McDensityFunction depth;
        private final McRandom.PositionalFactory aquiferRandom;

        private Inputs(long seed, NoiseSet noises, McDensityFunction erosion,
                McDensityFunction depth, McRandom.PositionalFactory aquiferRandom) {
            this.seed = seed;
            this.noises = noises;
            this.erosion = erosion;
            this.depth = depth;
            this.aquiferRandom = aquiferRandom;
        }
    }

    private static final class NoiseSet {
        private final McNormalNoise floodedness;
        private final McNormalNoise spread;
        private final McNormalNoise lava;
        private final McNormalNoise barrier;

        private NoiseSet(McNormalNoise floodedness, McNormalNoise spread,
                McNormalNoise lava, McNormalNoise barrier) {
            this.floodedness = floodedness;
            this.spread = spread;
            this.lava = lava;
            this.barrier = barrier;
        }
    }

    private static final class Site {
        private final int x;
        private final int y;
        private final int z;
        private final int gridX;
        private final int gridY;
        private final int gridZ;

        private Site(int x, int y, int z, int gridX, int gridY, int gridZ) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.gridX = gridX;
            this.gridY = gridY;
            this.gridZ = gridZ;
        }
    }

    private static final class FluidStatus {
        private final int level;
        private final Material type;

        private FluidStatus(int level, Material type) {
            this.level = level;
            this.type = type;
        }

        private Material fluidAt(int y) {
            return y < level ? type : Material.AIR;
        }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof FluidStatus)) return false;
            FluidStatus that = (FluidStatus) other;
            return level == that.level && type == that.type;
        }

        @Override public int hashCode() {
            return 31 * level + type.ordinal();
        }
    }

    public static final class Sample {
        private final Material material;
        private final int fluidLevel;
        private final double density;
        private final double floodedness;
        private final double spread;
        private final double lava;
        private final double barrier;

        private Sample(Material material, int fluidLevel, double density,
                double floodedness, double spread, double lava, double barrier) {
            this.material = material;
            this.fluidLevel = fluidLevel;
            this.density = density;
            this.floodedness = floodedness;
            this.spread = spread;
            this.lava = lava;
            this.barrier = barrier;
        }

        public Material material() { return material; }
        public int fluidLevel() { return fluidLevel; }
        public double density() { return density; }
        public double floodedness() { return floodedness; }
        public double spread() { return spread; }
        public double lava() { return lava; }
        public double barrier() { return barrier; }
    }
}
