package com.gameexpert.terrain.mc.structure;

import com.gameexpert.terrain.mc.feature.Mc263FeatureBlockState;
import com.gameexpert.terrain.mc.feature.Mc263FeaturesRegion;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Staged FEATURES-region projection shared by all thirteen stronghold piece executors. */
public final class Mc263StrongholdFeatureWorldAdapter implements
        Mc263StrongholdStraightPieceExecutor.WorldAccess,
        Mc263StrongholdLeftTurnPieceExecutor.WorldAccess,
        Mc263StrongholdRightTurnPieceExecutor.WorldAccess,
        Mc263StrongholdFillerCorridorPieceExecutor.WorldAccess {
    private static final String CHEST_PREFIX = "minecraft:chest[facing=";
    private final Mc263FeaturesRegion region;
    private final boolean buffered;
    private final Map<WorldPosition, Mc263FeatureBlockState> stagedStates = new LinkedHashMap<>();
    private final List<BlockWrite> blocks = new ArrayList<>();
    private final List<Loot> loot = new ArrayList<>();
    private final List<FluidTick> fluidTicks = new ArrayList<>();
    private final List<PostprocessMark> postprocess = new ArrayList<>();
    private final List<Spawner> spawners = new ArrayList<>();

    public record BlockWrite(int blockX, int blockY, int blockZ, String exactState) { }
    public record Loot(int blockX, int blockY, int blockZ, String lootTable, long lootSeed) { }
    public record FluidTick(int blockX, int blockY, int blockZ, String fluidKey,
                            int delay, int priority) { }
    public record PostprocessMark(int blockX, int blockY, int blockZ) { }
    public record Spawner(int blockX, int blockY, int blockZ, String entityType) { }

    public Mc263StrongholdFeatureWorldAdapter(Mc263FeaturesRegion region) {
        this(region, false);
    }

    private Mc263StrongholdFeatureWorldAdapter(Mc263FeaturesRegion region, boolean buffered) {
        this.region = Objects.requireNonNull(region, "FEATURES region");
        this.buffered = buffered;
    }

    public static Mc263StrongholdFeatureWorldAdapter buffered(Mc263FeaturesRegion region) {
        return new Mc263StrongholdFeatureWorldAdapter(region, true);
    }

    public Mc263StrongholdOrderedAggregate.Worlds worlds() {
        return new Mc263StrongholdOrderedAggregate.Worlds(this, this, this, this);
    }

    @Override public boolean supportsExactState(String state) {
        return Mc263FeatureBlockState.supportsExactState(state);
    }
    @Override public boolean supportsLiveAirQueries() { return true; }
    @Override public boolean supportsPostWriteFluidStateQueries() { return true; }
    @Override public boolean supportsScheduledFluidTicks() { return true; }
    @Override public boolean supportsPostprocessingMarks() { return true; }
    @Override public boolean supportsLootChests() {
        return Mc263FeatureBlockState.supportsExactState(chestState("north"));
    }
    @Override public boolean supportsSpawnerBlockEntities() { return true; }

    @Override public boolean isAir(Mc263StrongholdStraightPieceExecutor.BlockPos position) {
        return state(position.x(), position.y(), position.z()).isAir();
    }
    @Override public boolean isAir(Mc263StrongholdLeftTurnPieceExecutor.BlockPos position) {
        return state(position.x(), position.y(), position.z()).isAir();
    }
    @Override public boolean isAir(Mc263StrongholdRightTurnPieceExecutor.BlockPos position) {
        return state(position.x(), position.y(), position.z()).isAir();
    }

    @Override public boolean setBlock(Mc263StrongholdStraightPieceExecutor.BlockPos position,
                                      String state, int flags) {
        return set(position.x(), position.y(), position.z(), state, flags);
    }
    @Override public boolean setBlock(Mc263StrongholdLeftTurnPieceExecutor.BlockPos position,
                                      String state, int flags) {
        return set(position.x(), position.y(), position.z(), state, flags);
    }
    @Override public boolean setBlock(Mc263StrongholdRightTurnPieceExecutor.BlockPos position,
                                      String state, int flags) {
        return set(position.x(), position.y(), position.z(), state, flags);
    }
    @Override public boolean setBlock(Mc263StrongholdFillerCorridorPieceExecutor.BlockPos position,
                                      String state, int flags) {
        return set(position.x(), position.y(), position.z(), state, flags);
    }

    @Override public String postWriteFluidType(
            Mc263StrongholdStraightPieceExecutor.BlockPos position) {
        return fluid(position.x(), position.y(), position.z());
    }
    @Override public String postWriteFluidType(
            Mc263StrongholdLeftTurnPieceExecutor.BlockPos position) {
        return fluid(position.x(), position.y(), position.z());
    }
    @Override public String postWriteFluidType(
            Mc263StrongholdRightTurnPieceExecutor.BlockPos position) {
        return fluid(position.x(), position.y(), position.z());
    }
    @Override public String postWriteFluidType(
            Mc263StrongholdFillerCorridorPieceExecutor.BlockPos position) {
        return fluid(position.x(), position.y(), position.z());
    }

    @Override public void scheduleFluidTick(
            Mc263StrongholdStraightPieceExecutor.BlockPos position, String fluid, int delay) {
        schedule(position.x(), position.y(), position.z(), fluid, delay);
    }
    @Override public void scheduleFluidTick(
            Mc263StrongholdLeftTurnPieceExecutor.BlockPos position, String fluid, int delay) {
        schedule(position.x(), position.y(), position.z(), fluid, delay);
    }
    @Override public void scheduleFluidTick(
            Mc263StrongholdRightTurnPieceExecutor.BlockPos position, String fluid, int delay) {
        schedule(position.x(), position.y(), position.z(), fluid, delay);
    }
    @Override public void scheduleFluidTick(
            Mc263StrongholdFillerCorridorPieceExecutor.BlockPos position,
            String fluid, int delay) {
        schedule(position.x(), position.y(), position.z(), fluid, delay);
    }

    @Override public void markForPostprocessing(
            Mc263StrongholdStraightPieceExecutor.BlockPos position) {
        mark(position.x(), position.y(), position.z());
    }
    @Override public void markForPostprocessing(
            Mc263StrongholdLeftTurnPieceExecutor.BlockPos position) {
        mark(position.x(), position.y(), position.z());
    }
    @Override public void markForPostprocessing(
            Mc263StrongholdRightTurnPieceExecutor.BlockPos position) {
        mark(position.x(), position.y(), position.z());
    }

    @Override public void createLootChest(Mc263StrongholdStraightPieceExecutor.BlockPos position,
            String lootTable, Mc263StrongholdStraightPieceExecutor.RandomSource random) {
        Objects.requireNonNull(lootTable, "stronghold loot table");
        Objects.requireNonNull(random, "stronghold loot random");
        String facing = reorientedChestFacing(position.x(), position.y(), position.z());
        set(position.x(), position.y(), position.z(), chestState(facing), 2);
        long seed = random.nextLong();
        if (buffered) {
            loot.add(new Loot(position.x(), position.y(), position.z(), lootTable, seed));
        } else {
            throw new IllegalStateException(
                    "stronghold direct loot requires an authenticated placement context");
        }
    }

    @Override public void configureSpawner(Mc263StrongholdStraightPieceExecutor.BlockPos position,
            String entityType, Mc263StrongholdStraightPieceExecutor.RandomSource random) {
        Objects.requireNonNull(random, "stronghold spawner random");
        if (buffered) {
            spawners.add(new Spawner(position.x(), position.y(), position.z(), entityType));
        } else if (!region.setSpawnerMob(position.x(), position.y(), position.z(), entityType)) {
            throw new IllegalStateException("stronghold spawner escaped active source view");
        }
    }

    public List<BlockWrite> stagedBlocks() {
        return List.copyOf(blocks);
    }
    public List<Loot> stagedLoot() { return List.copyOf(loot); }
    public List<FluidTick> stagedFluidTicks() {
        return List.copyOf(fluidTicks);
    }
    public List<PostprocessMark> stagedPostprocessMarks() {
        return List.copyOf(postprocess);
    }
    public List<Spawner> stagedSpawners() {
        return List.copyOf(spawners);
    }

    private Mc263FeatureBlockState state(int x, int y, int z) {
        Mc263FeatureBlockState staged = stagedStates.get(new WorldPosition(x, y, z));
        return staged == null ? region.blockState(x, y, z) : staged;
    }

    private boolean set(int x, int y, int z, String exactState, int flags) {
        if (flags != 2) {
            throw new IllegalArgumentException("unsupported stronghold block flags: " + flags);
        }
        Mc263FeatureBlockState parsed = Mc263FeatureBlockState.fromExact(exactState);
        if (buffered) {
            blocks.add(new BlockWrite(x, y, z, parsed.exactState()));
            stagedStates.put(new WorldPosition(x, y, z), parsed);
            return true;
        }
        if (!region.setBlockState(x, y, z, parsed)) {
            throw new IllegalStateException("stronghold write escaped active source view");
        }
        return true;
    }

    private String fluid(int x, int y, int z) {
        Mc263FeatureBlockState value = state(x, y, z);
        return value.fluidAmount() == 0 ? "" : value.fluidTypeKey();
    }

    private void schedule(int x, int y, int z, String fluid, int delay) {
        if (delay != 0) throw new IllegalArgumentException("stronghold fluid tick delay is not zero");
        if (buffered) {
            fluidTicks.add(new FluidTick(x, y, z, fluid, delay, 0));
        } else {
            region.scheduleFluidTick(x, y, z, fluid, delay);
        }
    }

    private void mark(int x, int y, int z) {
        if (buffered) {
            postprocess.add(new PostprocessMark(x, y, z));
        } else if (!region.markForPostprocessing(x, y, z)) {
            throw new IllegalStateException("stronghold POST mark escaped active source view");
        }
    }

    private String reorientedChestFacing(int x, int y, int z) {
        String solid = null;
        int count = 0;
        for (String direction : List.of("north", "south", "west", "east")) {
            int nx = x + (direction.equals("east") ? 1 : direction.equals("west") ? -1 : 0);
            int nz = z + (direction.equals("south") ? 1 : direction.equals("north") ? -1 : 0);
            if (state(nx, y, nz).isSolidRender()) {
                solid = direction;
                count++;
            }
        }
        if (count == 1) return opposite(solid);
        String facing = "north";
        if (solid(x, y, z - 1) && !solid(x, y, z + 1)) facing = "south";
        else if (solid(x - 1, y, z) && !solid(x + 1, y, z)) facing = "east";
        else if (solid(x + 1, y, z) && !solid(x - 1, y, z)) facing = "west";
        return facing;
    }

    private boolean solid(int x, int y, int z) { return state(x, y, z).isSolidRender(); }

    private static String opposite(String direction) {
        return switch (direction) {
            case "north" -> "south";
            case "south" -> "north";
            case "west" -> "east";
            case "east" -> "west";
            default -> throw new IllegalArgumentException("non-horizontal chest direction");
        };
    }

    private static String chestState(String facing) {
        return CHEST_PREFIX + facing + ",type=single,waterlogged=false]";
    }

    private record WorldPosition(int x, int y, int z) { }
}
