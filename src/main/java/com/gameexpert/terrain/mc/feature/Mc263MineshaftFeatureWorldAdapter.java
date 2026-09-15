package com.gameexpert.terrain.mc.feature;

import com.gameexpert.terrain.Blocks;
import com.gameexpert.terrain.mc.feature.Mc263FeatureBlockState.OcclusionFace;
import com.gameexpert.terrain.mc.structure.Mc263MineshaftCorridorPieceExecutor;
import com.gameexpert.terrain.mc.structure.Mc263MineshaftCrossingPieceExecutor;
import com.gameexpert.terrain.mc.structure.Mc263MineshaftOrderedAggregate;
import com.gameexpert.terrain.mc.structure.Mc263MineshaftRoomPieceExecutor;
import com.gameexpert.terrain.mc.structure.Mc263MineshaftStairsPieceExecutor;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Live FEATURES-region projection shared by all four pinned mineshaft piece executors. */
public final class Mc263MineshaftFeatureWorldAdapter implements
        Mc263MineshaftStairsPieceExecutor.WorldAccess,
        Mc263MineshaftRoomPieceExecutor.WorldAccess,
        Mc263MineshaftCrossingPieceExecutor.WorldAccess,
        Mc263MineshaftCorridorPieceExecutor.WorldAccess {
    private static final Set<String> FALLING_BLOCKS = Set.of(
            "minecraft:sand", "minecraft:red_sand", "minecraft:gravel",
            "minecraft:suspicious_sand", "minecraft:suspicious_gravel",
            "minecraft:dragon_egg", "minecraft:anvil", "minecraft:chipped_anvil",
            "minecraft:damaged_anvil",
            "minecraft:white_concrete_powder", "minecraft:orange_concrete_powder",
            "minecraft:magenta_concrete_powder", "minecraft:light_blue_concrete_powder",
            "minecraft:yellow_concrete_powder", "minecraft:lime_concrete_powder",
            "minecraft:pink_concrete_powder", "minecraft:gray_concrete_powder",
            "minecraft:light_gray_concrete_powder", "minecraft:cyan_concrete_powder",
            "minecraft:purple_concrete_powder", "minecraft:blue_concrete_powder",
            "minecraft:brown_concrete_powder", "minecraft:green_concrete_powder",
            "minecraft:red_concrete_powder", "minecraft:black_concrete_powder");

    private final Mc263FeaturesRegion region;
    private final boolean buffered;
    private final long owner;
    private final Map<WorldPosition, Mc263FeatureBlockState> stagedStates =
            new LinkedHashMap<>();
    private final List<Mc263FeaturesRegion.StructureBlockWrite> stagedBlocks =
            new ArrayList<>();
    private final List<Mc263FeaturesRegion.StructureFluidTick> stagedFluidTicks =
            new ArrayList<>();
    private final List<Mc263FeaturesRegion.StructurePostprocessMark> stagedPostprocessMarks =
            new ArrayList<>();

    public Mc263MineshaftFeatureWorldAdapter(Mc263FeaturesRegion region,
            String structureKey, String startKey) {
        this(region, false, Mc263StructureOwner.owner(structureKey, startKey));
    }

    private Mc263MineshaftFeatureWorldAdapter(Mc263FeaturesRegion region, boolean buffered,
            long owner) {
        this.region = Objects.requireNonNull(region, "FEATURES region");
        this.buffered = buffered;
        this.owner = owner;
    }

    static Mc263MineshaftFeatureWorldAdapter buffered(Mc263FeaturesRegion region) {
        return new Mc263MineshaftFeatureWorldAdapter(region, true, 0L);
    }

    public Mc263MineshaftOrderedAggregate.Worlds worlds() {
        return new Mc263MineshaftOrderedAggregate.Worlds(this, this, this, this);
    }

    @Override public boolean supportsExactState(String state) {
        return Mc263FeatureBlockState.supportsExactState(state);
    }
    @Override public boolean supportsMineshaftBlockingBiomeTag() { return true; }
    @Override public boolean supportsLiquidStateQueries() { return true; }
    @Override public boolean supportsMineshaftProtectedStateQueries() { return true; }
    @Override public boolean supportsPostWriteFluidStateQueries() { return true; }
    @Override public boolean supportsScheduledFluidTicks() { return true; }
    @Override public boolean supportsAirStateQueries() { return true; }
    @Override public boolean supportsOceanFloorWgHeight() { return true; }
    @Override public boolean supportsSturdyUpQueries() { return true; }
    @Override public boolean supportsSturdyFaceQueries() { return true; }
    @Override public boolean supportsSolidRenderQueries() { return true; }
    @Override public boolean supportsReplaceableByStructuresQueries() { return true; }
    @Override public boolean supportsCenterSupportQueries() { return true; }
    @Override public boolean supportsFallingBlockQueries() { return true; }
    @Override public boolean supportsPostProcessingMarks() { return true; }
    @Override public boolean supportsSpawnerBlockEntityQueries() { return true; }
    @Override public boolean supportsChestMinecartCreation() { return true; }
    @Override public int minY() { return Blocks.MIN_Y; }
    @Override public int maxY() { return Blocks.MAX_Y; }

    @Override public boolean isMineshaftBlockingBiome(
            Mc263MineshaftStairsPieceExecutor.BlockPos position) {
        return isMineshaftBlockingBiome(position.x(), position.y(), position.z());
    }
    @Override public boolean isMineshaftBlockingBiome(
            Mc263MineshaftRoomPieceExecutor.BlockPos position) {
        return isMineshaftBlockingBiome(position.x(), position.y(), position.z());
    }
    @Override public boolean isMineshaftBlockingBiome(
            Mc263MineshaftCrossingPieceExecutor.BlockPos position) {
        return isMineshaftBlockingBiome(position.x(), position.y(), position.z());
    }
    @Override public boolean isMineshaftBlockingBiome(
            Mc263MineshaftCorridorPieceExecutor.BlockPos position) {
        return isMineshaftBlockingBiome(position.x(), position.y(), position.z());
    }

    @Override public boolean isLiquid(Mc263MineshaftStairsPieceExecutor.BlockPos position) {
        return state(position.x(), position.y(), position.z()).fluidAmount() != 0;
    }
    @Override public boolean isLiquid(Mc263MineshaftRoomPieceExecutor.BlockPos position) {
        return state(position.x(), position.y(), position.z()).fluidAmount() != 0;
    }
    @Override public boolean isLiquid(Mc263MineshaftCrossingPieceExecutor.BlockPos position) {
        return state(position.x(), position.y(), position.z()).fluidAmount() != 0;
    }
    @Override public boolean isLiquid(Mc263MineshaftCorridorPieceExecutor.BlockPos position) {
        return state(position.x(), position.y(), position.z()).fluidAmount() != 0;
    }

    @Override public String blockState(Mc263MineshaftStairsPieceExecutor.BlockPos position) {
        return exact(position.x(), position.y(), position.z());
    }
    @Override public String blockState(Mc263MineshaftRoomPieceExecutor.BlockPos position) {
        return exact(position.x(), position.y(), position.z());
    }
    @Override public String blockState(Mc263MineshaftCrossingPieceExecutor.BlockPos position) {
        return exact(position.x(), position.y(), position.z());
    }
    @Override public String blockState(Mc263MineshaftCorridorPieceExecutor.BlockPos position) {
        return exact(position.x(), position.y(), position.z());
    }

    @Override public boolean setBlock(Mc263MineshaftStairsPieceExecutor.BlockPos position,
            String state, int flags) {
        return set(position.x(), position.y(), position.z(), state, flags);
    }
    @Override public boolean setBlock(Mc263MineshaftRoomPieceExecutor.BlockPos position,
            String state, int flags) {
        return set(position.x(), position.y(), position.z(), state, flags);
    }
    @Override public boolean setBlock(Mc263MineshaftCrossingPieceExecutor.BlockPos position,
            String state, int flags) {
        return set(position.x(), position.y(), position.z(), state, flags);
    }
    @Override public boolean setBlock(Mc263MineshaftCorridorPieceExecutor.BlockPos position,
            String state, int flags) {
        return set(position.x(), position.y(), position.z(), state, flags);
    }

    @Override public String postWriteFluidType(
            Mc263MineshaftRoomPieceExecutor.BlockPos position) {
        return fluid(position.x(), position.y(), position.z());
    }
    @Override public String postWriteFluidType(
            Mc263MineshaftCrossingPieceExecutor.BlockPos position) {
        return fluid(position.x(), position.y(), position.z());
    }
    @Override public String postWriteFluidType(
            Mc263MineshaftCorridorPieceExecutor.BlockPos position) {
        return fluid(position.x(), position.y(), position.z());
    }

    @Override public void scheduleFluidTick(Mc263MineshaftRoomPieceExecutor.BlockPos position,
            String fluid, int delay) {
        schedule(position.x(), position.y(), position.z(), fluid, delay);
    }
    @Override public void scheduleFluidTick(Mc263MineshaftCrossingPieceExecutor.BlockPos position,
            String fluid, int delay) {
        schedule(position.x(), position.y(), position.z(), fluid, delay);
    }
    @Override public void scheduleFluidTick(Mc263MineshaftCorridorPieceExecutor.BlockPos position,
            String fluid, int delay) {
        schedule(position.x(), position.y(), position.z(), fluid, delay);
    }

    @Override public int oceanFloorWgHeight(int x, int z) { return region.oceanFloorWg(x, z); }

    @Override public boolean isFaceSturdyUp(
            Mc263MineshaftCrossingPieceExecutor.BlockPos position, String exactState) {
        return parsed(exactState).isFaceSturdy(OcclusionFace.UP);
    }

    @Override public boolean isFaceSturdy(Mc263MineshaftCorridorPieceExecutor.BlockPos position,
            String exactState, Mc263MineshaftCorridorPieceExecutor.Face face) {
        return parsed(exactState).isFaceSturdy(face(face));
    }

    @Override public boolean isSolidRender(Mc263MineshaftCorridorPieceExecutor.BlockPos position,
            String exactState) {
        return parsed(exactState).isSolidRender();
    }

    @Override public boolean isReplaceableByStructures(
            Mc263MineshaftCorridorPieceExecutor.BlockPos position, String exactState) {
        Mc263FeatureBlockState state = parsed(exactState);
        return state.isAir() || state.fluidAmount() != 0
                || state.blockKey().equals("minecraft:glow_lichen")
                || state.blockKey().equals("minecraft:seagrass")
                || state.blockKey().equals("minecraft:tall_seagrass");
    }

    @Override public boolean canSupportCenter(
            Mc263MineshaftCorridorPieceExecutor.BlockPos position, String exactState,
            Mc263MineshaftCorridorPieceExecutor.Face face) {
        return parsed(exactState).isFaceSturdy(face(face));
    }

    @Override public boolean isFallingBlock(String exactState) {
        return FALLING_BLOCKS.contains(parsed(exactState).blockKey());
    }

    @Override public void markForPostProcessing(
            Mc263MineshaftCorridorPieceExecutor.BlockPos position) {
        if (buffered) {
            stagedPostprocessMarks.add(new Mc263FeaturesRegion.StructurePostprocessMark(
                    position.x(), position.y(), position.z()));
            return;
        }
        if (!region.markForPostprocessing(position.x(), position.y(), position.z())) {
            throw new IllegalStateException("mineshaft postprocessing mark escaped source view");
        }
    }

    @Override public boolean hasSpawnerBlockEntity(
            Mc263MineshaftCorridorPieceExecutor.BlockPos position) {
        if (buffered) {
            return state(position.x(), position.y(), position.z()).capability()
                    == Mc263FeatureBlockState.Capability.SPAWNER;
        }
        return region.hasSpawnerBlockEntity(position.x(), position.y(), position.z());
    }

    private boolean isMineshaftBlockingBiome(int x, int y, int z) {
        return region.biomeKey(x, y, z).equals("minecraft:deep_dark");
    }

    private Mc263FeatureBlockState state(int x, int y, int z) {
        Mc263FeatureBlockState staged = stagedStates.get(new WorldPosition(x, y, z));
        if (staged != null) return staged;
        return region.blockState(x, y, z);
    }

    private String exact(int x, int y, int z) { return state(x, y, z).exactState(); }

    private boolean set(int x, int y, int z, String exactState, int flags) {
        if (flags != 2) {
            throw new IllegalArgumentException("unsupported mineshaft block flags: " + flags);
        }
        if (buffered) {
            Mc263FeatureBlockState parsed = Mc263FeatureBlockState.fromExact(exactState);
            stagedBlocks.add(new Mc263FeaturesRegion.StructureBlockWrite(
                    x, y, z, parsed.exactState(), 0L));
            stagedStates.put(new WorldPosition(x, y, z), parsed);
            return true;
        }
        if (!region.trySetOwnedBlockState(x, y, z, exactState, owner)) {
            throw new IllegalStateException("mineshaft write escaped active source view");
        }
        return true;
    }

    private String fluid(int x, int y, int z) {
        Mc263FeatureBlockState state = state(x, y, z);
        return state.fluidAmount() == 0 ? "" : state.fluidTypeKey();
    }

    private void schedule(int x, int y, int z, String fluid, int delay) {
        if (buffered) {
            stagedFluidTicks.add(new Mc263FeaturesRegion.StructureFluidTick(
                    x, y, z, fluid, delay, 0, stagedFluidTicks.size()));
            return;
        }
        // ProtoChunkTicks keeps the first insertion for an exact position/type duplicate.
        region.scheduleFluidTick(x, y, z, fluid, delay);
    }

    List<Mc263FeaturesRegion.StructureBlockWrite> stagedBlocks() {
        return List.copyOf(stagedBlocks);
    }

    List<Mc263FeaturesRegion.StructureFluidTick> stagedFluidTicks() {
        return List.copyOf(stagedFluidTicks);
    }

    List<Mc263FeaturesRegion.StructurePostprocessMark> stagedPostprocessMarks() {
        return List.copyOf(stagedPostprocessMarks);
    }

    private static Mc263FeatureBlockState parsed(String exactState) {
        return Mc263FeatureBlockState.fromExact(exactState);
    }

    private static OcclusionFace face(Mc263MineshaftCorridorPieceExecutor.Face face) {
        return switch (face) {
            case DOWN -> OcclusionFace.DOWN;
            case UP -> OcclusionFace.UP;
            case NORTH -> OcclusionFace.NORTH;
            case SOUTH -> OcclusionFace.SOUTH;
            case WEST -> OcclusionFace.WEST;
            case EAST -> OcclusionFace.EAST;
        };
    }

    private record WorldPosition(int x, int y, int z) { }
}
