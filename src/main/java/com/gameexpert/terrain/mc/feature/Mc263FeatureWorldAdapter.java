package com.gameexpert.terrain.mc.feature;

import com.gameexpert.terrain.mc.ore.Mc263OreFeature;
import com.gameexpert.terrain.mc.ore.Mc263DecorationRandom.WorldgenRandom;
import com.gameexpert.terrain.mc.biome.McBiomeRegistry;
import com.gameexpert.terrain.mc.surface.McFreezeTopLayer;
import com.gameexpert.terrain.mc.LegacyRand;
import com.gameexpert.terrain.mc.loot.Mc263ContainerLootResolver.LootProductionContext;
import com.gameexpert.terrain.mc.OctaveSimplexNoiseSampler;
import java.util.HashMap;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * One dormant typed view shared by the currently executable Java FEATURES leaves.
 *
 * <p>This class deliberately performs no registration. A caller must explicitly wire a leaf into
 * {@link Mc263FeatureDispatcher}, which keeps incomplete FEATURES coverage out of the canonical
 * generator while proving that all landed leaves compose against one live region and RNG stream.</p>
 */
public final class Mc263FeatureWorldAdapter implements
        Mc263LakeFeature.WorldAccess,
        Mc263ForestRockFeature.WorldAccess,
        Mc263FossilFeature.WorldAccess,
        Mc263MonsterRoomFeature.WorldAccess,
        Mc263OreFeature.WorldAccess,
        Mc263IcebergFeature.WorldAccess,
        Mc263BlueIceFeature.WorldAccess,
        Mc263DesertWellFeature.WorldAccess,
        Mc263IceSpikeFeature.WorldAccess,
        Mc263IcePatchFeature.WorldAccess,
        Mc263DiskClayFeature.WorldAccess,
        Mc263DiskSandFeature.WorldAccess,
        Mc263DiskGravelFeature.WorldAccess,
        Mc263LargeDripstoneFeature.WorldAccess,
        Mc263SulfurPoolFeature.WorldAccess {
    public static final int SEA_LEVEL = 63;
    private static final Map<String, McBiomeRegistry.Biome> BIOMES_BY_KEY = biomeCatalog();
    private static final OctaveSimplexNoiseSampler BIOME_INFO_NOISE =
            new OctaveSimplexNoiseSampler(LegacyRand.fromSeed(2345L), new int[]{0});

    private final Mc263FeaturesRegion region;
    private final long worldSeed;
    private final long biomeZoomSeed;
    private final Mc263FeatureDispatcher.ProductionContextLocator productionContexts;

    private static final Set<String> TREE_WRAPPER_KEYS =
            Mc263FeatureCapabilitySets.treeWrapperKeys();
    private static final int[][] TREE_DIRECTIONS = {
            {0, -1, 0}, {0, 1, 0}, {0, 0, -1}, {0, 0, 1}, {-1, 0, 0}, {1, 0, 0}
    };

    public Mc263FeatureWorldAdapter(long worldSeed, Mc263FeaturesRegion region) {
        this(worldSeed, region, Mc263FeatureDispatcher.ProductionContextLocator.unavailable());
    }

    public Mc263FeatureWorldAdapter(long worldSeed, Mc263FeaturesRegion region,
            Mc263FeatureDispatcher.ProductionContextLocator productionContexts) {
        this.worldSeed = worldSeed;
        this.biomeZoomSeed = biomeZoomSeed(worldSeed);
        this.region = Objects.requireNonNull(region, "region");
        this.productionContexts = Objects.requireNonNull(
                productionContexts, "production context locator");
    }

    /** Pure coordinator-facing exact output capability; performs no region lookup or mutation. */
    public boolean supportsExactOutputState(String exactState) {
        return Mc263FeatureBlockState.supportsExactState(exactState);
    }

    public boolean supportsTickSidecars() { return true; }
    public boolean supportsPostprocessingSidecar() { return true; }
    public boolean supportsMonsterRoomPayloads() { return true; }
    public boolean supportsSuspiciousSandPayload() { return true; }
    public boolean supportsPotentSulfurPayload() { return true; }
    public boolean supportsSculkPayloads() { return true; }

    /** Live common-tree view. Tree writes deliberately retain vanilla's distinct flag contracts. */
    public Mc263CommonTreeFeature.WorldAccess commonTreeWorld() {
        return new Mc263CommonTreeFeature.WorldAccess() {
            @Override public int minY() { return region.minGenerationY(); }
            @Override public int maxY() {
                return region.minGenerationY() + region.generationDepth() - 1;
            }
            @Override public Mc263CommonTreeFeature.State state(Mc263CommonTreeFeature.Pos pos) {
                return commonTreeState(region.blockState(pos.x(), pos.y(), pos.z()));
            }
            @Override public boolean cannotReplaceBelowTreeTrunk(
                    Mc263CommonTreeFeature.State state) {
                return featureState(state.canonical()).cannotReplaceBelowTreeTrunk();
            }
            @Override public boolean canSaplingSurvive(Mc263CommonTreeFeature.State sapling,
                    Mc263CommonTreeFeature.Pos pos) {
                return saplingSurvives(sapling.block(), pos.x(), pos.y(), pos.z());
            }
            @Override public boolean supportsFeature(String key) {
                return supportsTreeFeature(key);
            }
            @Override public boolean supportsState(Mc263CommonTreeFeature.State state) {
                return supportsTreeState(state.block(), state.canonical());
            }
            @Override public boolean supportsBeeNestPayload() { return true; }
            @Override public boolean supportsTreeFinalization() { return true; }
            @Override public boolean supportsPostProcessing() { return true; }
            @Override public boolean set(Mc263CommonTreeFeature.Pos pos,
                    Mc263CommonTreeFeature.State state, int flags) {
                return setTreeState(pos.x(), pos.y(), pos.z(), state.canonical(), flags);
            }
            @Override public boolean setAndUpdate(Mc263CommonTreeFeature.Pos pos,
                    Mc263CommonTreeFeature.State state) {
                return setTreeState(pos.x(), pos.y(), pos.z(), state.canonical(), 3);
            }
            @Override public void markAboveForPostProcessing(Mc263CommonTreeFeature.Pos pos) {
                markTreeAboveForPostprocessing(pos.x(), pos.y(), pos.z());
            }
            @Override public boolean sturdyUp(Mc263CommonTreeFeature.Pos below,
                    Mc263CommonTreeFeature.Pos queriedFrom) {
                return region.blockState(below.x(), below.y(), below.z())
                        .isFaceSturdy(Mc263FeatureBlockState.OcclusionFace.UP);
            }
            @Override public int motionBlockingNoLeaves(Mc263CommonTreeFeature.Pos pos) {
                return Mc263FeatureWorldAdapter.this.motionBlockingNoLeaves(pos.x(), pos.z());
            }
            @Override public void storeBee(Mc263CommonTreeFeature.Pos nest, int ticksInHive) {
                if (!region.storeWorldgenBee(nest.x(), nest.y(), nest.z(), ticksInHive)) {
                    throw new IllegalStateException("bee payload rejected at " + nest);
                }
            }
            @Override public void finishTree(Set<Mc263CommonTreeFeature.Pos> logs,
                    Set<Mc263CommonTreeFeature.Pos> leaves,
                    Set<Mc263CommonTreeFeature.Pos> roots,
                    Set<Mc263CommonTreeFeature.Pos> decorations) {
                finishTreePoints(commonPoints(logs), commonPoints(leaves), commonPoints(roots),
                        commonPoints(decorations));
            }
        };
    }

    /** Live placed/configured complex-tree view. */
    public Mc263ComplexTreeFeature.World complexTreeWorld() {
        Mc263CommonTreeFeature.WorldAccess common = commonTreeWorld();
        return new Mc263ComplexTreeFeature.World() {
            @Override public int minY() { return common.minY(); }
            @Override public int maxY() { return common.maxY(); }
            @Override public Mc263CommonTreeFeature.State state(Mc263CommonTreeFeature.Pos pos) {
                return common.state(pos);
            }
            @Override public boolean cannotReplaceBelowTreeTrunk(
                    Mc263CommonTreeFeature.State state) {
                return common.cannotReplaceBelowTreeTrunk(state);
            }
            @Override public boolean canSaplingSurvive(Mc263CommonTreeFeature.State sapling,
                    Mc263CommonTreeFeature.Pos pos) {
                return common.canSaplingSurvive(sapling, pos);
            }
            @Override public boolean supportsFeature(String key) {
                return common.supportsFeature(key);
            }
            @Override public boolean supportsState(Mc263CommonTreeFeature.State state) {
                return common.supportsState(state);
            }
            @Override public boolean supportsBeeNestPayload() { return true; }
            @Override public boolean supportsTreeFinalization() { return true; }
            @Override public boolean supportsPostProcessing() { return true; }
            @Override public boolean set(Mc263CommonTreeFeature.Pos pos,
                    Mc263CommonTreeFeature.State state, int flags) {
                return common.set(pos, state, flags);
            }
            @Override public boolean setAndUpdate(Mc263CommonTreeFeature.Pos pos,
                    Mc263CommonTreeFeature.State state) {
                return common.setAndUpdate(pos, state);
            }
            @Override public void markAboveForPostProcessing(Mc263CommonTreeFeature.Pos pos) {
                common.markAboveForPostProcessing(pos);
            }
            @Override public boolean sturdyUp(Mc263CommonTreeFeature.Pos below,
                    Mc263CommonTreeFeature.Pos queriedFrom) {
                return common.sturdyUp(below, queriedFrom);
            }
            @Override public int motionBlockingNoLeaves(Mc263CommonTreeFeature.Pos pos) {
                return common.motionBlockingNoLeaves(pos);
            }
            @Override public void storeBee(Mc263CommonTreeFeature.Pos nest, int ticks) {
                common.storeBee(nest, ticks);
            }
            @Override public void finishTree(Set<Mc263CommonTreeFeature.Pos> logs,
                    Set<Mc263CommonTreeFeature.Pos> leaves,
                    Set<Mc263CommonTreeFeature.Pos> roots,
                    Set<Mc263CommonTreeFeature.Pos> decorations) {
                common.finishTree(logs, leaves, roots, decorations);
            }
            @Override public int oceanFloor(Mc263CommonTreeFeature.Pos column) {
                return region.oceanFloorWg(column.x(), column.z());
            }
            @Override public int worldSurface(Mc263CommonTreeFeature.Pos column) {
                return region.worldSurfaceWg(column.x(), column.z());
            }
            @Override public boolean biomeAllows(String placedKey,
                    Mc263CommonTreeFeature.Pos pos) {
                return biomeContainsTree(placedKey, pos.x(), pos.y(), pos.z());
            }
            @Override public boolean supportsBeneathTreePodzolTag() { return true; }
            @Override public boolean beneathTreePodzolReplaceable(
                    Mc263CommonTreeFeature.State state) {
                return switch (state.block()) {
                    case "minecraft:dirt", "minecraft:grass_block",
                            "minecraft:coarse_dirt", "minecraft:rooted_dirt",
                            "minecraft:podzol", "minecraft:mycelium",
                            "minecraft:moss_block", "minecraft:pale_moss_block" -> true;
                    default -> false;
                };
            }
        };
    }

    /** Pure routing over already-frozen configured leaves; the supplied RNG is never replaced. */
    public Mc263ComplexTreeFeature.LeafExecutor complexTreeLeaves() {
        Mc263CommonTreeFeature.WorldAccess common = commonTreeWorld();
        Mc263PoplarTreeFeature.WorldAccess poplar = poplarTreeWorld();
        return new Mc263ComplexTreeFeature.LeafExecutor() {
            @Override public boolean supports(String rawKey) {
                String key = normalizeTreeKey(rawKey);
                return Mc263CommonTreeFeature.configuredKeys().contains(key)
                        || Mc263CommonTreeFeature.fallenKeys().contains(key)
                        || Mc263PoplarTreeFeature.configuredKeys().contains(key)
                        || Mc263PoplarTreeFeature.fallenKey().equals(key)
                        || key.equals("minecraft:huge_brown_mushroom")
                        || key.equals("minecraft:huge_red_mushroom")
                        || key.equals("minecraft:pale_moss_patch")
                        || key.equals(Mc263BambooJungleGrassFeature.CONFIGURED_KEY);
            }
            @Override public void preflight(String rawKey, Mc263ComplexTreeFeature.World world) {
                String key = normalizeTreeKey(rawKey);
                if (Mc263CommonTreeFeature.configuredKeys().contains(key)) {
                    Mc263CommonTreeFeature.preflightConfigured(key, common);
                } else if (Mc263CommonTreeFeature.fallenKeys().contains(key)) {
                    Mc263CommonTreeFeature.preflightFallen(key, common);
                } else if (Mc263PoplarTreeFeature.configuredKeys().contains(key)) {
                    Mc263PoplarTreeFeature.preflightConfigured(key, poplar);
                } else if (Mc263PoplarTreeFeature.fallenKey().equals(key)) {
                    Mc263PoplarTreeFeature.preflightFallen(poplar);
                } else if (key.equals("minecraft:huge_brown_mushroom")
                        || key.equals("minecraft:huge_red_mushroom")) {
                    Mc263HugeMushroomFeature.preflightConfigured(key, hugeMushroomWorld());
                } else if (key.equals("minecraft:pale_moss_patch")) {
                    Mc263CaveRootVegetationFeature.preflight(17, caveRootVegetationWorld());
                } else if (key.equals(Mc263BambooJungleGrassFeature.CONFIGURED_KEY)) {
                    Mc263BambooJungleGrassFeature.preflightConfigured(bambooJungleGrassWorld());
                } else throw new UnsupportedOperationException(key);
            }
            @Override public Mc263CommonTreeFeature.Result place(String rawKey,
                    WorldgenRandom random, Mc263CommonTreeFeature.Pos origin,
                    Mc263ComplexTreeFeature.World world, Mc263CommonTreeFeature.TraceSink trace) {
                String key = normalizeTreeKey(rawKey);
                if (Mc263CommonTreeFeature.configuredKeys().contains(key)) {
                    return Mc263CommonTreeFeature.place(key, random, origin, common, trace);
                }
                if (Mc263CommonTreeFeature.fallenKeys().contains(key)) {
                    return Mc263CommonTreeFeature.placeFallen(key, random, origin, common, trace);
                }
                if (Mc263PoplarTreeFeature.configuredKeys().contains(key)) {
                    Mc263PoplarTreeFeature.Result result = Mc263PoplarTreeFeature.place(key, random,
                            new Mc263PoplarTreeFeature.Pos(origin.x(), origin.y(), origin.z()),
                            poplar, (phase, values) -> { });
                    return new Mc263CommonTreeFeature.Result(result.placed(), result.reads(),
                            result.writes(), result.retained(), 0, result.postprocess());
                }
                if (Mc263PoplarTreeFeature.fallenKey().equals(key)) {
                    Mc263PoplarTreeFeature.Result result = Mc263PoplarTreeFeature.placeFallen(random,
                            new Mc263PoplarTreeFeature.Pos(origin.x(), origin.y(), origin.z()),
                            poplar, (phase, values) -> { });
                    return new Mc263CommonTreeFeature.Result(result.placed(), result.reads(),
                            result.writes(), result.retained(), 0, result.postprocess());
                }
                if (key.equals("minecraft:huge_brown_mushroom")
                        || key.equals("minecraft:huge_red_mushroom")) {
                    Mc263HugeMushroomFeature.Result result =
                            Mc263HugeMushroomFeature.placeConfigured(key, random,
                                    origin.x(), origin.y(), origin.z(), hugeMushroomWorld());
                    return new Mc263CommonTreeFeature.Result(result.placed(), result.reads(),
                            result.attemptedWrites(), result.retainedWrites(), 0, 0);
                }
                if (key.equals("minecraft:pale_moss_patch")) {
                    Mc263CaveRootVegetationFeature.Result result =
                            Mc263CaveRootVegetationFeature.placeConfigured(17, random,
                                    origin.x(), origin.y(), origin.z(),
                                    caveRootVegetationWorld(), (phase, values) -> { });
                    return new Mc263CommonTreeFeature.Result(result.placed(), result.candidates(),
                            result.attemptedWrites(), result.retainedWrites(), 0, 0);
                }
                if (key.equals(Mc263BambooJungleGrassFeature.CONFIGURED_KEY)) {
                    Mc263BambooJungleGrassFeature.Result result =
                            Mc263BambooJungleGrassFeature.placeConfigured(random,
                                    origin.x(), origin.y(), origin.z(), bambooJungleGrassWorld());
                    return new Mc263CommonTreeFeature.Result(result.placed(), result.reads(),
                            result.attemptedWrites(), result.retainedWrites(), 0, 0);
                }
                throw new UnsupportedOperationException(key);
            }
            @Override public Mc263CommonTreeFeature.Result place(String rawKey,
                    com.gameexpert.terrain.mc.ore.Mc263WorldgenRandomSource random,
                    Mc263CommonTreeFeature.Pos origin, Mc263ComplexTreeFeature.World world,
                    Mc263CommonTreeFeature.TraceSink trace) {
                String key = normalizeTreeKey(rawKey);
                if (Mc263CommonTreeFeature.configuredKeys().contains(key)) {
                    return Mc263CommonTreeFeature.place(key, random, origin, common, trace);
                }
                if (Mc263CommonTreeFeature.fallenKeys().contains(key)) {
                    return Mc263CommonTreeFeature.placeFallen(
                            key, random, origin, common, trace);
                }
                if (Mc263PoplarTreeFeature.configuredKeys().contains(key)) {
                    Mc263PoplarTreeFeature.Result result = Mc263PoplarTreeFeature.place(key, random,
                            new Mc263PoplarTreeFeature.Pos(origin.x(), origin.y(), origin.z()),
                            poplar, (phase, values) -> { });
                    return new Mc263CommonTreeFeature.Result(result.placed(), result.reads(),
                            result.writes(), result.retained(), 0, result.postprocess());
                }
                if (Mc263PoplarTreeFeature.fallenKey().equals(key)) {
                    Mc263PoplarTreeFeature.Result result = Mc263PoplarTreeFeature.placeFallen(random,
                            new Mc263PoplarTreeFeature.Pos(origin.x(), origin.y(), origin.z()),
                            poplar, (phase, values) -> { });
                    return new Mc263CommonTreeFeature.Result(result.placed(), result.reads(),
                            result.writes(), result.retained(), 0, result.postprocess());
                }
                if (key.equals("minecraft:huge_brown_mushroom")
                        || key.equals("minecraft:huge_red_mushroom")) {
                    Mc263HugeMushroomFeature.Result result =
                            Mc263HugeMushroomFeature.placeConfigured(key, random,
                                    origin.x(), origin.y(), origin.z(), hugeMushroomWorld());
                    return new Mc263CommonTreeFeature.Result(result.placed(), result.reads(),
                            result.attemptedWrites(), result.retainedWrites(), 0, 0);
                }
                if (key.equals("minecraft:pale_moss_patch")
                        && random instanceof WorldgenRandom xoroshiro) {
                    Mc263CaveRootVegetationFeature.Result result =
                            Mc263CaveRootVegetationFeature.placeConfigured(17, xoroshiro,
                                    origin.x(), origin.y(), origin.z(),
                                    caveRootVegetationWorld(), (phase, values) -> { });
                    return new Mc263CommonTreeFeature.Result(result.placed(), result.candidates(),
                            result.attemptedWrites(), result.retainedWrites(), 0, 0);
                }
                if (key.equals(Mc263BambooJungleGrassFeature.CONFIGURED_KEY)) {
                    Mc263BambooJungleGrassFeature.Result result =
                            Mc263BambooJungleGrassFeature.placeConfigured(random,
                                    origin.x(), origin.y(), origin.z(), bambooJungleGrassWorld());
                    return new Mc263CommonTreeFeature.Result(result.placed(), result.reads(),
                            result.attemptedWrites(), result.retainedWrites(), 0, 0);
                }
                throw new UnsupportedOperationException(
                        "caller-RNG tree leaf execution not implemented: " + key);
            }
        };
    }

    /** Coordinator-facing pure tree closure preflight. */
    public void preflightTreeIndex(int index) {
        if (index == 50 || index == 51) {
            Mc263MangroveSwampTreeFeature.preflightIndex(index,
                    mangroveSwampTreeWorld(), mangroveSwampTreeLeaves());
        } else {
            Mc263ComplexTreeFeature.preflightIndex(index, complexTreeWorld(),
                    complexTreeLeaves());
        }
    }

    /** Coordinator-facing exact placed execution using the supplied decoration RNG unchanged. */
    public void placeTreeIndex(int index, int sourceX, int sourceY, int sourceZ,
            WorldgenRandom random) {
        if (index == 50 || index == 51) {
            Mc263MangroveSwampTreeFeature.placeIndex(index, random,
                    new Mc263CommonTreeFeature.Pos(sourceX, sourceY, sourceZ),
                    mangroveSwampTreeWorld(), mangroveSwampTreeLeaves(),
                    (phase, values) -> { });
        } else {
            Mc263ComplexTreeFeature.placeIndex(index, random,
                    new Mc263CommonTreeFeature.Pos(sourceX, sourceY, sourceZ),
                    complexTreeWorld(), complexTreeLeaves(), (phase, values) -> { });
        }
    }

    public Mc263MangroveSwampTreeFeature.LeafExecutor mangroveSwampTreeLeaves() {
        Mc263CommonTreeFeature.WorldAccess common = commonTreeWorld();
        return new Mc263MangroveSwampTreeFeature.LeafExecutor() {
            @Override public boolean supports(String key) {
                return normalizeTreeKey(key).equals("minecraft:swamp_oak");
            }
            @Override public void preflight(String key,
                    Mc263MangroveSwampTreeFeature.WorldAccess world) {
                Mc263CommonTreeFeature.preflightConfigured(key, common);
            }
            @Override public Mc263CommonTreeFeature.Result place(String key,
                    WorldgenRandom random, Mc263CommonTreeFeature.Pos origin,
                    Mc263MangroveSwampTreeFeature.WorldAccess world,
                    Mc263MangroveSwampTreeFeature.TraceSink trace) {
                return Mc263CommonTreeFeature.place(key, random, origin, common,
                        (phase, values) -> { });
            }
        };
    }

    /** Separate delegating view avoids Java's erased Set&lt;Pos&gt; finalizer collision. */
    public Mc263PoplarTreeFeature.WorldAccess poplarTreeWorld() {
        return new Mc263PoplarTreeFeature.WorldAccess() {
            @Override public int minY() { return region.minGenerationY(); }
            @Override public int maxY() {
                return region.minGenerationY() + region.generationDepth() - 1;
            }
            @Override public Mc263PoplarTreeFeature.State state(Mc263PoplarTreeFeature.Pos pos) {
                return poplarTreeState(region.blockState(pos.x(), pos.y(), pos.z()));
            }
            @Override public boolean cannotReplaceBelowTreeTrunk(
                    Mc263PoplarTreeFeature.State state) {
                return featureState(state.canonical()).cannotReplaceBelowTreeTrunk();
            }
            @Override public boolean canSaplingSurvive(Mc263PoplarTreeFeature.State sapling,
                    Mc263PoplarTreeFeature.Pos pos) {
                return saplingSurvives(sapling.block(), pos.x(), pos.y(), pos.z());
            }
            @Override public boolean supportsFeature(String key) {
                return supportsTreeFeature(key);
            }
            @Override public boolean supportsState(Mc263PoplarTreeFeature.State state) {
                return supportsTreeState(state.block(), state.canonical());
            }
            @Override public boolean supportsTreeFinalization() { return true; }
            @Override public boolean supportsPostProcessing() { return true; }
            @Override public boolean set(Mc263PoplarTreeFeature.Pos pos,
                    Mc263PoplarTreeFeature.State state, int flags) {
                return setTreeState(pos.x(), pos.y(), pos.z(), state.canonical(), flags);
            }
            @Override public boolean setAndUpdate(Mc263PoplarTreeFeature.Pos pos,
                    Mc263PoplarTreeFeature.State state) {
                return setTreeState(pos.x(), pos.y(), pos.z(), state.canonical(), 3);
            }
            @Override public void markAboveForPostProcessing(Mc263PoplarTreeFeature.Pos pos) {
                markTreeAboveForPostprocessing(pos.x(), pos.y(), pos.z());
            }
            @Override public boolean sturdyUp(Mc263PoplarTreeFeature.Pos below,
                    Mc263PoplarTreeFeature.Pos queriedFrom) {
                return region.blockState(below.x(), below.y(), below.z())
                        .isFaceSturdy(Mc263FeatureBlockState.OcclusionFace.UP);
            }
            @Override public int motionBlockingNoLeaves(Mc263PoplarTreeFeature.Pos pos) {
                return Mc263FeatureWorldAdapter.this.motionBlockingNoLeaves(pos.x(), pos.z());
            }
            @Override public boolean waterSource(Mc263PoplarTreeFeature.Pos pos) {
                return region.blockState(pos.x(), pos.y(), pos.z()).fluidKind()
                        == Mc263FeatureBlockState.FluidKind.WATER_SOURCE;
            }
            @Override public void finishTree(Set<Mc263PoplarTreeFeature.Pos> logs,
                    Set<Mc263PoplarTreeFeature.Pos> leaves,
                    Set<Mc263PoplarTreeFeature.Pos> roots,
                    Set<Mc263PoplarTreeFeature.Pos> decorations) {
                finishTreePoints(poplarPoints(logs), poplarPoints(leaves), poplarPoints(roots),
                        poplarPoints(decorations));
            }
        };
    }

    /** Live mangrove/swamp-tree view sharing the normalized finalizer with every tree kernel. */
    public Mc263MangroveSwampTreeFeature.WorldAccess mangroveSwampTreeWorld() {
        Mc263CommonTreeFeature.WorldAccess common = commonTreeWorld();
        return new Mc263MangroveSwampTreeFeature.WorldAccess() {
            @Override public int minY() { return common.minY(); }
            @Override public int maxY() { return common.maxY(); }
            @Override public Mc263CommonTreeFeature.State state(Mc263CommonTreeFeature.Pos pos) {
                return common.state(pos);
            }
            @Override public boolean cannotReplaceBelowTreeTrunk(
                    Mc263CommonTreeFeature.State state) {
                return common.cannotReplaceBelowTreeTrunk(state);
            }
            @Override public boolean canSaplingSurvive(Mc263CommonTreeFeature.State sapling,
                    Mc263CommonTreeFeature.Pos pos) {
                return common.canSaplingSurvive(sapling, pos);
            }
            @Override public boolean supportsFeature(String key) {
                return common.supportsFeature(key);
            }
            @Override public boolean supportsState(Mc263CommonTreeFeature.State state) {
                return common.supportsState(state);
            }
            @Override public boolean supportsBeeNestPayload() { return true; }
            @Override public boolean supportsTreeFinalization() { return true; }
            @Override public boolean supportsPostProcessing() { return true; }
            @Override public boolean set(Mc263CommonTreeFeature.Pos pos,
                    Mc263CommonTreeFeature.State state, int flags) {
                return common.set(pos, state, flags);
            }
            @Override public boolean setAndUpdate(Mc263CommonTreeFeature.Pos pos,
                    Mc263CommonTreeFeature.State state) {
                return common.setAndUpdate(pos, state);
            }
            @Override public void markAboveForPostProcessing(Mc263CommonTreeFeature.Pos pos) {
                common.markAboveForPostProcessing(pos);
            }
            @Override public boolean sturdyUp(Mc263CommonTreeFeature.Pos below,
                    Mc263CommonTreeFeature.Pos queriedFrom) {
                return common.sturdyUp(below, queriedFrom);
            }
            @Override public int motionBlockingNoLeaves(Mc263CommonTreeFeature.Pos pos) {
                return common.motionBlockingNoLeaves(pos);
            }
            @Override public void storeBee(Mc263CommonTreeFeature.Pos nest, int ticks) {
                common.storeBee(nest, ticks);
            }
            @Override public void finishTree(Set<Mc263CommonTreeFeature.Pos> logs,
                    Set<Mc263CommonTreeFeature.Pos> leaves,
                    Set<Mc263CommonTreeFeature.Pos> roots,
                    Set<Mc263CommonTreeFeature.Pos> decorations) {
                common.finishTree(logs, leaves, roots, decorations);
            }
            @Override public int oceanFloor(Mc263CommonTreeFeature.Pos column) {
                return region.oceanFloorWg(column.x(), column.z());
            }
            @Override public int worldSurface(Mc263CommonTreeFeature.Pos column) {
                return region.worldSurfaceWg(column.x(), column.z());
            }
            @Override public boolean biomeAllows(String key, Mc263CommonTreeFeature.Pos pos) {
                return biomeContainsTree(key, pos.x(), pos.y(), pos.z());
            }
            @Override public boolean mangroveRootsCanGrowThrough(
                    Mc263CommonTreeFeature.State state) {
                return featureState(state.canonical()).mangroveRootsCanGrowThrough();
            }
            @Override public boolean mangroveLogsCanGrowThrough(
                    Mc263CommonTreeFeature.State state) {
                return featureState(state.canonical()).mangroveLogsCanGrowThrough();
            }
            @Override public boolean muddyRootsIn(Mc263CommonTreeFeature.State state) {
                return featureState(state.canonical()).isMudTag();
            }
        };
    }

    /** Exact nested azalea tree view used by rooted-azalea's same-stream leaf. */
    public Mc263AzaleaTreeFeature.WorldAccess azaleaTreeWorld() {
        return new Mc263AzaleaTreeFeature.WorldAccess() {
            @Override public int minY() { return region.minGenerationY(); }
            @Override public int maxY() {
                return region.minGenerationY() + region.generationDepth() - 1;
            }
            @Override public Mc263AzaleaTreeFeature.State state(Mc263AzaleaTreeFeature.Pos pos) {
                Mc263FeatureBlockState state = region.blockState(pos.x(), pos.y(), pos.z());
                return new Mc263AzaleaTreeFeature.State(state.blockKey(),
                        exactProperties(state.exactState()), state.isAir(),
                        state.replaceableByTrees(), state.isLogsTag(),
                        property(state.exactState(), "persistent").equals("true"),
                        state.fluidKind() == Mc263FeatureBlockState.FluidKind.WATER_SOURCE);
            }
            @Override public boolean waterSource(Mc263AzaleaTreeFeature.Pos pos) {
                return region.blockState(pos.x(), pos.y(), pos.z()).fluidKind()
                        == Mc263FeatureBlockState.FluidKind.WATER_SOURCE;
            }
            @Override public boolean supportsFeature(String key) {
                return normalizeTreeKey(key).equals("minecraft:azalea_tree");
            }
            @Override public boolean supportsState(Mc263AzaleaTreeFeature.State state) {
                return Mc263FeatureBlockState.supportsExactState(state.canonical());
            }
            @Override public boolean supportsTreeFinalization() { return true; }
            @Override public boolean set(Mc263AzaleaTreeFeature.Pos pos,
                    Mc263AzaleaTreeFeature.State state, int flags) {
                return setTreeState(pos.x(), pos.y(), pos.z(), state.canonical(), flags);
            }
            @Override public void finishTree(Set<Mc263AzaleaTreeFeature.Pos> logs,
                    Set<Mc263AzaleaTreeFeature.Pos> leaves,
                    Set<Mc263AzaleaTreeFeature.Pos> roots,
                    Set<Mc263AzaleaTreeFeature.Pos> decorations) {
                finishTreePoints(azaleaPoints(logs), azaleaPoints(leaves), azaleaPoints(roots),
                        azaleaPoints(decorations));
            }
        };
    }

    public long worldSeed() {
        return worldSeed;
    }

    /** Exact seeded {@code BiomeManager#getBiome} block-coordinate zoom. */
    public String populationBiomeKey(int blockX, int blockY, int blockZ) {
        int shiftedX = blockX - 2;
        int shiftedY = blockY - 2;
        int shiftedZ = blockZ - 2;
        int quartX = Math.floorDiv(shiftedX, 4);
        int quartY = Math.floorDiv(shiftedY, 4);
        int quartZ = Math.floorDiv(shiftedZ, 4);
        double offsetX = Math.floorMod(shiftedX, 4) / 4.0;
        double offsetY = Math.floorMod(shiftedY, 4) / 4.0;
        double offsetZ = Math.floorMod(shiftedZ, 4) / 4.0;
        int bestCorner = 0;
        double bestDistance = Double.POSITIVE_INFINITY;
        for (int corner = 0; corner < 8; corner++) {
            boolean lowX = (corner & 4) == 0;
            boolean lowY = (corner & 2) == 0;
            boolean lowZ = (corner & 1) == 0;
            double distance = fiddledDistance(biomeZoomSeed,
                    lowX ? quartX : quartX + 1,
                    lowY ? quartY : quartY + 1,
                    lowZ ? quartZ : quartZ + 1,
                    lowX ? offsetX : offsetX - 1.0,
                    lowY ? offsetY : offsetY - 1.0,
                    lowZ ? offsetZ : offsetZ - 1.0);
            if (bestDistance > distance) {
                bestCorner = corner;
                bestDistance = distance;
            }
        }
        return region.noiseBiomeKey(quartX + ((bestCorner & 4) == 0 ? 0 : 1),
                quartY + ((bestCorner & 2) == 0 ? 0 : 1),
                quartZ + ((bestCorner & 1) == 0 ? 0 : 1));
    }

    /** Explicit raw quart lookup for callers whose official API is {@code getNoiseBiome}. */
    public String noiseBiomeKey(int quartX, int quartY, int quartZ) {
        return region.noiseBiomeKey(quartX, quartY, quartZ);
    }

    private static long biomeZoomSeed(long worldSeed) {
        byte[] input = new byte[Long.BYTES];
        for (int index = 0; index < input.length; index++) {
            input[index] = (byte) (worldSeed >>> (index * 8));
        }
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(input);
            long result = 0;
            for (int index = Long.BYTES - 1; index >= 0; index--) {
                result = result << 8 | hash[index] & 0xffL;
            }
            return result;
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static double fiddledDistance(long seed, int x, int y, int z,
            double offsetX, double offsetY, double offsetZ) {
        long mixed = lcgNext(seed, x);
        mixed = lcgNext(mixed, y);
        mixed = lcgNext(mixed, z);
        mixed = lcgNext(mixed, x);
        mixed = lcgNext(mixed, y);
        mixed = lcgNext(mixed, z);
        double fiddleX = fiddle(mixed);
        mixed = lcgNext(mixed, seed);
        double fiddleY = fiddle(mixed);
        mixed = lcgNext(mixed, seed);
        double fiddleZ = fiddle(mixed);
        double dx = offsetX + fiddleX;
        double dy = offsetY + fiddleY;
        double dz = offsetZ + fiddleZ;
        return dx * dx + dy * dy + dz * dz;
    }

    private static long lcgNext(long state, long salt) {
        return state * (state * 6_364_136_223_846_793_005L
                + 1_442_695_040_888_963_407L) + salt;
    }

    private static double fiddle(long state) {
        return (Math.floorMod(state >> 24, 1024L) / 1024.0 - 0.5) * 0.9;
    }

    /** Live, fail-closed carrier for the pure SimpleBlock vegetation family. */
    public Mc263SimpleVegetationFeature.WorldAccess simpleVegetationWorld() {
        return new Mc263SimpleVegetationFeature.WorldAccess() {
            @Override public int minGenerationY() { return region.minGenerationY(); }
            @Override public int height(Mc263SimpleVegetationFeature.Heightmap kind,
                    int x, int z) {
                return switch (kind) {
                    case WORLD_SURFACE_WG -> region.worldSurfaceWg(x, z);
                    case MOTION_BLOCKING -> region.motionBlocking(x, z);
                    case MOTION_BLOCKING_NO_LEAVES -> motionBlockingNoLeaves(x, z);
                };
            }
            @Override public String biomeKey(int x, int y, int z) {
                return populationBiomeKey(x, y, z);
            }
            @Override public double flowerNoise(int x, int z) {
                return BIOME_INFO_NOISE.sample(x / 200.0, z / 200.0, false);
            }
            @Override public Mc263SimpleVegetationFeature.State blockState(int x, int y, int z) {
                return simpleVegetationState(region.blockState(x, y, z));
            }
            @Override public String fluidState(int x, int y, int z) {
                return region.blockState(x, y, z).fluidTypeKey();
            }
            @Override public boolean supportsFeature(int globalIndex) {
                return switch (globalIndex) {
                    case 7, 13, 14, 15, 25, 38, 41, 54, 57, 60, 61, 62, 63, 64, 65,
                            71, 72, 73, 74, 75, 76, 77, 80, 83, 84, 85, 88, 97, 98,
                            11, 18, 19, 37, 43, 52, 58, 95, 96,
                            6, 23, 29, 39, 56, 66, 67, 69, 70 -> true;
                    default -> false;
                };
            }
            @Override public boolean supportsState(Mc263SimpleVegetationFeature.State state) {
                try {
                    Mc263FeatureBlockState.fromExact(state.canonical());
                    return true;
                } catch (IllegalArgumentException missingProtocolIdentity) {
                    return false;
                }
            }
            @Override public boolean canSurvive(Mc263SimpleVegetationFeature.State state,
                    int x, int y, int z) {
                final Mc263FeatureBlockState candidate;
                try {
                    candidate = Mc263FeatureBlockState.fromExact(state.canonical());
                } catch (IllegalArgumentException missingProtocolIdentity) {
                    return false;
                }
                String key = candidate.blockKey();
                if (key.equals("minecraft:pumpkin") || key.equals("minecraft:melon")) return true;
                if (key.equals("minecraft:leaf_litter")) {
                    return region.blockState(x, y - 1, z).isFaceSturdy(
                            Mc263FeatureBlockState.OcclusionFace.UP);
                }
                if (key.equals("minecraft:brown_mushroom")
                        || key.equals("minecraft:red_mushroom")) {
                    Mc263FeatureBlockState below = region.blockState(x, y - 1, z);
                    if (below.blockKey().equals("minecraft:mycelium")
                            || below.blockKey().equals("minecraft:podzol")
                            || below.blockKey().equals("minecraft:crimson_nylium")
                            || below.blockKey().equals("minecraft:warped_nylium")) {
                        return true;
                    }
                    return region.rawBrightness(x, y, z, 0) < 13 && below.isSolidRender();
                }
                String below = region.blockState(x, y - 1, z).blockKey();
                return isDryVegetation(key) ? supportsDryVegetation(below)
                        : supportsVegetation(below);
            }
            @Override public boolean trySetBlockState(int x, int y, int z,
                    Mc263SimpleVegetationFeature.State state, int flags) {
                if (flags != 2) {
                    throw new IllegalArgumentException(
                            "unsupported simple-vegetation block flags: " + flags);
                }
                try {
                    return region.setBlockState(x, y, z, state.canonical());
                } catch (IllegalArgumentException missingProtocolIdentity) {
                    return false;
                }
            }
            @Override public void scheduleTick(int x, int y, int z, String block, int delay) {
                if (delay != 1) {
                    throw new IllegalArgumentException(
                            "unsupported simple-vegetation tick delay: " + delay);
                }
                region.scheduleBlockTick(x, y, z, block, delay);
            }
        };
    }

    /** Exact live carrier for step-nine mushroom-island huge-mushroom selection. */
    public Mc263HugeMushroomFeature.WorldAccess hugeMushroomWorld() {
        return new Mc263HugeMushroomFeature.WorldAccess() {
            @Override public int minGenerationY() { return region.minGenerationY(); }
            @Override public int maxGenerationYExclusive() {
                return region.minGenerationY() + region.generationDepth();
            }
            @Override public int motionBlockingHeight(int x, int z) {
                return region.motionBlocking(x, z);
            }
            @Override public String biomeKey(int x, int y, int z) {
                return populationBiomeKey(x, y, z);
            }
            @Override public Mc263HugeMushroomFeature.State blockState(int x, int y, int z) {
                Mc263FeatureBlockState state = region.blockState(x, y, z);
                return new Mc263HugeMushroomFeature.State(state.blockKey(),
                        exactProperties(state.exactState()), state.isAir(), state.isLeavesTag(),
                        state.isReplaceableByMushroomsTag(),
                        state.isHugeMushroomSubstrateTag());
            }
            @Override public boolean supportsFeature(int globalIndex) {
                return globalIndex == Mc263HugeMushroomFeature.INDEX;
            }
            @Override public boolean supportsState(Mc263HugeMushroomFeature.State state) {
                return Mc263FeatureBlockState.supportsExactState(state.canonical());
            }
            @Override public boolean trySetBlockState(int x, int y, int z,
                    Mc263HugeMushroomFeature.State state, int flags) {
                if (flags != 3) throw new IllegalArgumentException("huge-mushroom flags must be 3");
                if (!supportsState(state)) return false;
                return region.setBlockState(x, y, z, state.canonical());
            }
        };
    }

    /** Exact live carrier for waterlily, warm-ocean coral, and sea-pickle features. */
    public Mc263AquaticVegetationFeature.WorldAccess aquaticVegetationWorld() {
        return new Mc263AquaticVegetationFeature.WorldAccess() {
            @Override public int minGenerationY() { return region.minGenerationY(); }
            @Override public int height(Mc263AquaticVegetationFeature.Heightmap kind,
                    int x, int z) {
                return switch (kind) {
                    case WORLD_SURFACE_WG -> region.worldSurfaceWg(x, z);
                    case OCEAN_FLOOR, OCEAN_FLOOR_WG -> region.oceanFloorWg(x, z);
                };
            }
            @Override public String biomeKey(int x, int y, int z) {
                return populationBiomeKey(x, y, z);
            }
            @Override public float biomeInfoNoise(int sourceX, int sourceZ) {
                return (float) BIOME_INFO_NOISE.sample(
                        sourceX / 400.0, sourceZ / 400.0, false);
            }
            @Override public Mc263AquaticVegetationFeature.State blockState(
                    int x, int y, int z) {
                Mc263FeatureBlockState state = region.blockState(x, y, z);
                return new Mc263AquaticVegetationFeature.State(state.blockKey(),
                        exactProperties(state.exactState()), state.fluidTypeKey(),
                        state.fluidKind() == Mc263FeatureBlockState.FluidKind.WATER_SOURCE,
                        state.isAir());
            }
            @Override public boolean supportsFeature(int globalIndex) {
                return globalIndex == 68 || globalIndex == 99 || globalIndex == 101;
            }
            @Override public boolean supportsState(Mc263AquaticVegetationFeature.State state) {
                return Mc263FeatureBlockState.supportsExactState(state.canonical());
            }
            @Override public boolean canSurvive(Mc263AquaticVegetationFeature.State state,
                    int x, int y, int z) {
                if (!supportsState(state)) return false;
                String block = state.block();
                if (block.endsWith("_coral_block")) return true;
                if (block.equals("minecraft:lily_pad")) {
                    Mc263FeatureBlockState below = region.blockState(x, y - 1, z);
                    boolean support = below.fluidTypeKey().equals("minecraft:water")
                            || below.fluidTypeKey().equals("minecraft:flowing_water")
                            || below.blockKey().equals("minecraft:ice");
                    return support && region.blockState(x, y, z).fluidKind()
                            == Mc263FeatureBlockState.FluidKind.NONE;
                }
                if (block.equals("minecraft:sea_pickle")) {
                    Mc263FeatureBlockState below = region.blockState(x, y - 1, z);
                    return below.isCollisionFaceNonEmpty(
                            Mc263FeatureBlockState.OcclusionFace.UP)
                            || below.isFaceSturdy(Mc263FeatureBlockState.OcclusionFace.UP);
                }
                if (block.endsWith("_coral_wall_fan")) {
                    String facing = state.properties().get("facing");
                    int dx = facing.equals("east") ? -1 : facing.equals("west") ? 1 : 0;
                    int dz = facing.equals("south") ? -1 : facing.equals("north") ? 1 : 0;
                    Mc263FeatureBlockState support = region.blockState(x + dx, y, z + dz);
                    Mc263FeatureBlockState.OcclusionFace face = switch (facing) {
                        case "north" -> Mc263FeatureBlockState.OcclusionFace.NORTH;
                        case "east" -> Mc263FeatureBlockState.OcclusionFace.EAST;
                        case "south" -> Mc263FeatureBlockState.OcclusionFace.SOUTH;
                        case "west" -> Mc263FeatureBlockState.OcclusionFace.WEST;
                        default -> throw new IllegalArgumentException(
                                "unsupported coral facing: " + facing);
                    };
                    return support.isFaceSturdy(face);
                }
                if (block.endsWith("_coral") || block.endsWith("_coral_fan")) {
                    return region.blockState(x, y - 1, z).isFaceSturdy(
                            Mc263FeatureBlockState.OcclusionFace.UP);
                }
                return false;
            }
            @Override public boolean trySetBlockState(int x, int y, int z,
                    Mc263AquaticVegetationFeature.State state, int flags) {
                if (flags != 2) throw new IllegalArgumentException("aquatic flags must be 2");
                if (!supportsState(state)) return false;
                return region.setBlockState(x, y, z, state.canonical());
            }
        };
    }

    /** Exact live carrier for bamboo and forest-flower placed features. */
    public Mc263BambooForestFlowersFeature.WorldAccess bambooForestFlowersWorld() {
        return new Mc263BambooForestFlowersFeature.WorldAccess() {
            @Override public int minGenerationY() { return region.minGenerationY(); }
            @Override public int height(Mc263BambooForestFlowersFeature.Heightmap kind,
                    int x, int z) {
                return switch (kind) {
                    case WORLD_SURFACE_WG, WORLD_SURFACE -> region.worldSurfaceWg(x, z);
                    case MOTION_BLOCKING -> region.motionBlocking(x, z);
                };
            }
            @Override public String biomeKey(int x, int y, int z) {
                return populationBiomeKey(x, y, z);
            }
            @Override public double bambooCountNoise(int sourceX, int sourceZ) {
                return (double) (float) BIOME_INFO_NOISE.sample(
                        sourceX / 80.0, sourceZ / 80.0, false);
            }
            @Override public Mc263BambooForestFlowersFeature.State blockState(
                    int x, int y, int z) {
                Mc263FeatureBlockState state = region.blockState(x, y, z);
                return new Mc263BambooForestFlowersFeature.State(state.blockKey(),
                        exactProperties(state.exactState()), state.fluidTypeKey(),
                        state.isAir(), state.canBeReplaced());
            }
            @Override public boolean canSurvive(Mc263BambooForestFlowersFeature.State state,
                    int x, int y, int z) {
                if (!supportsState(state)) return false;
                Mc263FeatureBlockState below = region.blockState(x, y - 1, z);
                return state.block().equals("minecraft:bamboo")
                        ? below.supportsBamboo()
                        : supportsVegetation(below.blockKey());
            }
            @Override public boolean beneathBambooPodzolReplaceable(
                    Mc263BambooForestFlowersFeature.State state) {
                try {
                    return Mc263FeatureBlockState.fromExact(state.canonical())
                            .beneathBambooPodzolReplaceable();
                } catch (IllegalArgumentException unsupported) {
                    return false;
                }
            }
            @Override public boolean supportsFeature(int globalIndex) {
                return globalIndex == 2 || globalIndex == 9
                        || globalIndex == 21 || globalIndex == 24;
            }
            @Override public boolean supportsState(Mc263BambooForestFlowersFeature.State state) {
                return Mc263FeatureBlockState.supportsExactState(state.canonical());
            }
            @Override public boolean trySetBlockState(int x, int y, int z,
                    Mc263BambooForestFlowersFeature.State state, int flags) {
                if (flags != 2) {
                    throw new IllegalArgumentException(
                            "unsupported bamboo/forest-flower flags: " + flags);
                }
                if (!supportsState(state)) return false;
                return region.setBlockState(x, y, z, state.canonical());
            }
        };
    }

    public Mc263BambooJungleGrassFeature.WorldAccess bambooJungleGrassWorld() {
        return new Mc263BambooJungleGrassFeature.WorldAccess() {
            @Override public Mc263BambooJungleGrassFeature.State blockState(int x, int y, int z) {
                Mc263FeatureBlockState state = region.blockState(x, y, z);
                return new Mc263BambooJungleGrassFeature.State(state.blockKey(), state.isAir(),
                        state.supportsVegetationTag());
            }
            @Override public boolean supportsState(Mc263BambooJungleGrassFeature.State state) {
                return Mc263FeatureBlockState.supportsExactState(state.block());
            }
            @Override public boolean trySetBlockState(int x, int y, int z,
                    Mc263BambooJungleGrassFeature.State state, int flags) {
                if (flags != 2) throw new IllegalArgumentException(
                        "bamboo-jungle-grass flags must be 2");
                return region.setBlockState(x, y, z, state.block());
            }
        };
    }

    /** Exact live carrier for the step-nine cold/warm kelp pair. */
    public Mc263KelpFeature.WorldAccess kelpWorld() {
        return new Mc263KelpFeature.WorldAccess() {
            @Override public int minGenerationY() { return region.minGenerationY(); }
            @Override public double flowerNoise(int sourceX, int sourceZ) {
                return (float) BIOME_INFO_NOISE.sample(
                        sourceX / 80.0, sourceZ / 80.0, false);
            }
            @Override public int height(Mc263KelpFeature.Heightmap kind, int x, int z) {
                return switch (kind) {
                    case OCEAN_FLOOR -> region.oceanFloorWg(x, z);
                };
            }
            @Override public String biomeKey(int x, int y, int z) {
                return populationBiomeKey(x, y, z);
            }
            @Override public Mc263KelpFeature.State blockState(int x, int y, int z) {
                return kelpState(region.blockState(x, y, z));
            }
            @Override public boolean supportsState(Mc263KelpFeature.State state) {
                try {
                    Mc263FeatureBlockState.fromExact(state.canonical());
                    return true;
                } catch (IllegalArgumentException missingProtocolIdentity) {
                    return false;
                }
            }
            @Override public boolean trySetBlockState(int x, int y, int z,
                    Mc263KelpFeature.State state, int flags) {
                if (flags != 2) {
                    throw new IllegalArgumentException("unsupported kelp block flags: " + flags);
                }
                try {
                    return region.setBlockState(x, y, z, state.canonical());
                } catch (IllegalArgumentException missingProtocolIdentity) {
                    return false;
                }
            }
        };
    }

    /** Exact live carrier for the step-nine vines feature. */
    public Mc263VinesFeature.WorldAccess vinesWorld() {
        return new Mc263VinesFeature.WorldAccess() {
            @Override public String biomeKey(int x, int y, int z) {
                return populationBiomeKey(x, y, z);
            }
            @Override public Mc263VinesFeature.State blockState(int x, int y, int z) {
                Mc263FeatureBlockState state = region.blockState(x, y, z);
                int supportMask = 0;
                for (Mc263VinesFeature.Direction direction
                        : Mc263VinesFeature.Direction.values()) {
                    Mc263FeatureBlockState.OcclusionFace face =
                            Mc263FeatureBlockState.OcclusionFace.valueOf(direction.name());
                    if (state.isSupportOrCollisionFull(face)) {
                        supportMask |= 1 << direction.ordinal();
                    }
                }
                return new Mc263VinesFeature.State(state.blockKey(),
                        exactProperties(state.exactState()), state.isAir(), supportMask);
            }
            @Override public boolean supportsState(Mc263VinesFeature.State state) {
                try {
                    Mc263FeatureBlockState.fromExact(state.canonical());
                    return true;
                } catch (IllegalArgumentException missingIdentity) {
                    return false;
                }
            }
            @Override public boolean trySetBlockState(int x, int y, int z,
                    Mc263VinesFeature.State state, int flags) {
                if (flags != 2) {
                    throw new IllegalArgumentException("unsupported vines block flags: " + flags);
                }
                if (!supportsState(state)) return false;
                return region.setBlockState(x, y, z, state.canonical());
            }
        };
    }

    /** Exact live carrier for the step-nine cave/root vegetation tranche. */
    public Mc263CaveRootVegetationFeature.WorldAccess caveRootVegetationWorld() {
        return new Mc263CaveRootVegetationFeature.WorldAccess() {
            @Override public int minGenerationY() { return region.minGenerationY(); }
            @Override public int height(Mc263CaveRootVegetationFeature.Heightmap heightmap,
                    int x, int z) {
                return switch (heightmap) {
                    case MOTION_BLOCKING_NO_LEAVES -> motionBlockingNoLeaves(x, z);
                };
            }
            @Override public String biomeKey(int x, int y, int z) {
                return populationBiomeKey(x, y, z);
            }
            @Override public Mc263CaveRootVegetationFeature.State blockState(
                    int x, int y, int z) {
                Mc263FeatureBlockState state = region.blockState(x, y, z);
                int sturdyMask = 0;
                for (Mc263CaveRootVegetationFeature.Direction direction
                        : Mc263CaveRootVegetationFeature.Direction.values()) {
                    Mc263FeatureBlockState.OcclusionFace face =
                            Mc263FeatureBlockState.OcclusionFace.valueOf(direction.name());
                    if (state.isFaceSturdy(face)) sturdyMask |= 1 << direction.ordinal();
                }
                String key = state.blockKey();
                boolean mossReplaceable = isMossReplaceable(key);
                return new Mc263CaveRootVegetationFeature.State(key,
                        exactProperties(state.exactState()), state.isAir(), state.isSolid(),
                        state.canBeReplaced(), mossReplaceable,
                        mossReplaceable || isAdditionalLushGroundReplaceable(key),
                        state.fluidKind() == Mc263FeatureBlockState.FluidKind.WATER_SOURCE,
                        sturdyMask);
            }
            @Override public boolean canSurvive(Mc263CaveRootVegetationFeature.State state,
                    int x, int y, int z) {
                if (!supportsState(state)) return false;
                Mc263FeatureBlockState support = region.blockState(x, y - 1, z);
                return switch (state.block()) {
                    case "minecraft:short_grass", "minecraft:tall_grass" ->
                            supportsVegetation(support.blockKey());
                    case "minecraft:azalea", "minecraft:flowering_azalea" ->
                            supportsVegetation(support.blockKey())
                                    || support.blockKey().equals("minecraft:clay");
                    case "minecraft:moss_carpet", "minecraft:pale_moss_carpet" ->
                            !support.isAir();
                    case "minecraft:spore_blossom" ->
                            region.blockState(x, y + 1, z)
                                    .isFaceSturdy(Mc263FeatureBlockState.OcclusionFace.DOWN)
                                    && !isWater(region.blockState(x, y, z));
                    default -> false;
                };
            }
            @Override public boolean supportsFeature(int globalIndex) {
                return switch (globalIndex) {
                    case 17, 30, 31, 32, 33, 35 -> true;
                    default -> false;
                };
            }
            @Override public boolean supportsNestedFeature(
                    Mc263CaveRootVegetationFeature.NestedFeature feature) {
                Objects.requireNonNull(feature, "feature");
                return feature == Mc263CaveRootVegetationFeature.NestedFeature.DRIPLEAF;
            }
            @Override public boolean supportsState(
                    Mc263CaveRootVegetationFeature.State state) {
                return state != null
                        && Mc263FeatureBlockState.supportsExactState(state.canonical());
            }
            @Override public boolean trySetBlockState(int x, int y, int z,
                    Mc263CaveRootVegetationFeature.State state, int flags) {
                if (flags != 2) {
                    throw new IllegalArgumentException(
                            "unsupported cave/root vegetation flags: " + flags);
                }
                if (!supportsState(state)) return false;
                return region.setBlockState(x, y, z, state.canonical());
            }
            @Override public boolean placeNestedFeature(
                    Mc263CaveRootVegetationFeature.NestedFeature feature,
                    com.gameexpert.terrain.mc.ore.Mc263DecorationRandom.WorldgenRandom random,
                    int x, int y, int z) {
                Objects.requireNonNull(feature, "feature");
                Objects.requireNonNull(random, "random");
                if (feature != Mc263CaveRootVegetationFeature.NestedFeature.DRIPLEAF) {
                    return false;
                }
                Mc263DripleafFeature.WorldAccess dripleaf =
                        new Mc263DripleafFeature.WorldAccess() {
                    @Override public Mc263DripleafFeature.State state(
                            Mc263DripleafFeature.Pos pos) {
                        Mc263FeatureBlockState state = region.blockState(
                                pos.x(), pos.y(), pos.z());
                        return new Mc263DripleafFeature.State(state.blockKey(),
                                exactProperties(state.exactState()), state.isAir(),
                                state.fluidKind()
                                        == Mc263FeatureBlockState.FluidKind.WATER_SOURCE,
                                state.canBeReplaced());
                    }
                    @Override public boolean canSurvive(Mc263DripleafFeature.State state,
                            Mc263DripleafFeature.Pos pos) {
                        if (!state.block().equals("minecraft:small_dripleaf")
                                || !"lower".equals(state.properties().get("half"))) {
                            return false;
                        }
                        String support = region.blockState(
                                pos.x(), pos.y() - 1, pos.z()).blockKey();
                        return support.equals("minecraft:clay")
                                || support.equals("minecraft:moss_block");
                    }
                    @Override public boolean supportsFeature(String configuredKey) {
                        return Mc263DripleafFeature.FEATURE.equals(configuredKey);
                    }
                    @Override public boolean supportsState(Mc263DripleafFeature.State state) {
                        return state != null
                                && Mc263FeatureBlockState.supportsExactState(state.canonical());
                    }
                    @Override public boolean set(Mc263DripleafFeature.Pos pos,
                            Mc263DripleafFeature.State state, int flags) {
                        if (flags != 2) {
                            throw new IllegalArgumentException(
                                    "unsupported dripleaf block flags: " + flags);
                        }
                        if (!supportsState(state)) return false;
                        return region.setBlockState(pos.x(), pos.y(), pos.z(),
                                state.canonical());
                    }
                };
                return Mc263DripleafFeature.place(random,
                        new Mc263DripleafFeature.Pos(x, y, z), dripleaf).placed();
            }
        };
    }

    /** Exact live RootSystem facts with the same-random nested azalea executor. */
    public Mc263RootedAzaleaFeature.WorldAccess rootedAzaleaWorld() {
        Mc263AzaleaTreeFeature.WorldAccess azalea = azaleaTreeWorld();
        return new Mc263RootedAzaleaFeature.WorldAccess() {
            @Override public int minGenerationY() { return region.minGenerationY(); }
            @Override public int height(Mc263RootedAzaleaFeature.Heightmap heightmap,
                    int x, int z) {
                return switch (heightmap) {
                    case WORLD_SURFACE -> region.worldSurfaceWg(x, z);
                };
            }
            @Override public String biomeKey(int x, int y, int z) {
                return populationBiomeKey(x, y, z);
            }
            @Override public Mc263RootedAzaleaFeature.State blockState(int x, int y, int z) {
                Mc263FeatureBlockState state = region.blockState(x, y, z);
                Mc263FeatureBlockState.FluidKind fluid = state.fluidKind();
                return new Mc263RootedAzaleaFeature.State(state.blockKey(), state.isAir(),
                        state.isSolid(), fluid == Mc263FeatureBlockState.FluidKind.WATER_SOURCE
                                || fluid == Mc263FeatureBlockState.FluidKind.WATER_FLOWING,
                        fluid == Mc263FeatureBlockState.FluidKind.LAVA_SOURCE
                                || fluid == Mc263FeatureBlockState.FluidKind.LAVA_FLOWING,
                        state.isFaceSturdyDown());
            }
            @Override public boolean allowedTreePosition(int x, int y, int z) {
                Mc263FeatureBlockState state = region.blockState(x, y, z);
                return (state.isAir() || isReplaceableByTrees(state))
                        && supportsAzaleaGrowth(
                                region.blockState(x, y - 1, z).blockKey());
            }
            @Override public boolean rootReplaceable(int x, int y, int z) {
                return region.blockState(x, y, z).azaleaRootReplaceable();
            }
            @Override public boolean canSurviveHangingRoots(int x, int y, int z) {
                return region.blockState(x, y + 1, z)
                        .isFaceSturdy(Mc263FeatureBlockState.OcclusionFace.DOWN);
            }
            @Override public boolean supportsRootedAzaleaTree() { return true; }
            @Override public boolean supportsState(Mc263RootedAzaleaFeature.State state) {
                return state != null && Mc263FeatureBlockState.supportsExactState(state.block());
            }
            @Override public boolean trySetBlockState(int x, int y, int z,
                    Mc263RootedAzaleaFeature.State state, int flags) {
                if (flags != 2) {
                    throw new IllegalArgumentException(
                            "unsupported rooted-azalea flags: " + flags);
                }
                if (!supportsState(state)) return false;
                return region.setBlockState(x, y, z, state.block());
            }
            @Override public boolean placeAzaleaTree(
                    com.gameexpert.terrain.mc.ore.Mc263DecorationRandom.WorldgenRandom random,
                    int x, int y, int z) {
                Objects.requireNonNull(random, "random");
                return Mc263AzaleaTreeFeature.place(random,
                        new Mc263AzaleaTreeFeature.Pos(x, y, z), azalea).placed();
            }
        };
    }

    /** Exact live carrier for index 36's classic cave vines. */
    public Mc263ClassicVinesCaveFeature.WorldAccess classicVinesCaveWorld() {
        return new Mc263ClassicVinesCaveFeature.WorldAccess() {
            @Override public int minGenerationY() { return region.minGenerationY(); }
            @Override public String biomeKey(int x, int y, int z) {
                return populationBiomeKey(x, y, z);
            }
            @Override public Mc263ClassicVinesCaveFeature.State blockState(
                    int x, int y, int z) {
                Mc263FeatureBlockState state = region.blockState(x, y, z);
                int acceptableMask = 0;
                for (Mc263ClassicVinesCaveFeature.Direction direction
                        : Mc263ClassicVinesCaveFeature.Direction.values()) {
                    Mc263FeatureBlockState.OcclusionFace face =
                            Mc263FeatureBlockState.OcclusionFace.valueOf(direction.name());
                    if (state.isSupportOrCollisionFull(face)) {
                        acceptableMask |= 1 << direction.ordinal();
                    }
                }
                return new Mc263ClassicVinesCaveFeature.State(state.blockKey(),
                        exactProperties(state.exactState()), state.isAir(), acceptableMask);
            }
            @Override public boolean supportsClassicVines() { return true; }
            @Override public boolean supportsState(Mc263ClassicVinesCaveFeature.State state) {
                return state != null && Mc263FeatureBlockState.supportsExactState(
                        classicVineCanonical(state));
            }
            @Override public boolean trySetBlockState(int x, int y, int z,
                    Mc263ClassicVinesCaveFeature.State state, int flags) {
                if (flags != 2) {
                    throw new IllegalArgumentException(
                            "unsupported classic-vines flags: " + flags);
                }
                if (!supportsState(state)) return false;
                return region.setBlockState(x, y, z, classicVineCanonical(state));
            }
        };
    }

    /** Exact live carrier for the step-nine near-water firefly-bush pair. */
    public Mc263NearWaterFireflyFeature.WorldAccess nearWaterFireflyWorld() {
        return new Mc263NearWaterFireflyFeature.WorldAccess() {
            @Override public int minGenerationY() { return region.minGenerationY(); }
            @Override public int height(Mc263NearWaterFireflyFeature.Heightmap heightmap,
                    int x, int z) {
                return switch (heightmap) {
                    case MOTION_BLOCKING -> region.motionBlocking(x, z);
                    case MOTION_BLOCKING_NO_LEAVES -> motionBlockingNoLeaves(x, z);
                };
            }
            @Override public String biomeKey(int x, int y, int z) {
                return populationBiomeKey(x, y, z);
            }
            @Override public Mc263NearWaterFireflyFeature.State blockState(int x, int y, int z) {
                Mc263FeatureBlockState state = region.blockState(x, y, z);
                return new Mc263NearWaterFireflyFeature.State(state.blockKey(),
                        exactProperties(state.exactState()), state.isAir(), state.canBeReplaced());
            }
            @Override public String fluidState(int x, int y, int z) {
                return region.blockState(x, y, z).fluidTypeKey();
            }
            @Override public boolean canSurvive(Mc263NearWaterFireflyFeature.State state,
                    int x, int y, int z) {
                if (!state.block().equals("minecraft:firefly_bush") || !supportsState(state)) {
                    return false;
                }
                return supportsVegetation(region.blockState(x, y - 1, z).blockKey());
            }
            @Override public boolean supportsState(Mc263NearWaterFireflyFeature.State state) {
                try {
                    Mc263FeatureBlockState.fromExact(state.canonical());
                    return true;
                } catch (IllegalArgumentException missingIdentity) {
                    return false;
                }
            }
            @Override public boolean trySetBlockState(int x, int y, int z,
                    Mc263NearWaterFireflyFeature.State state, int flags) {
                if (flags != 2) {
                    throw new IllegalArgumentException(
                            "unsupported near-water firefly flags: " + flags);
                }
                if (!supportsState(state)) return false;
                return region.setBlockState(x, y, z, state.canonical());
            }
        };
    }

    /** Exact step-10 snow-and-freeze view over the immutable post-CARVERS light snapshot. */
    public Mc263FreezeTopLayerFeature.WorldAccess freezeTopLayerWorld() {
        return new Mc263FreezeTopLayerFeature.WorldAccess() {
            @Override public int motionBlockingHeight(int x, int z) {
                return region.motionBlocking(x, z);
            }
            @Override public Mc263FreezeTopLayerFeature.BiomeClimate biomeClimate(
                    int x, int y, int z) {
                McBiomeRegistry.Biome biome = biome(populationBiomeKey(x, y, z));
                return new Mc263FreezeTopLayerFeature.BiomeClimate(biome.temperature(),
                        biome.frozenModifier(), biome.precipitation());
            }
            @Override public int minGenerationY() { return region.minGenerationY(); }
            @Override public int generationDepth() { return region.generationDepth(); }
            @Override public int seaLevel() { return SEA_LEVEL; }
            @Override public int blockLight(int x, int y, int z) {
                return region.blockBrightness(x, y, z);
            }
            @Override public Mc263FreezeTopLayerFeature.State blockState(int x, int y, int z) {
                Mc263FeatureBlockState state = region.blockState(x, y, z);
                String block = state.blockKey();
                Map<String, String> properties = exactProperties(state.exactState());
                int layers = block.equals("minecraft:snow")
                        ? Integer.parseInt(properties.getOrDefault("layers", "1")) : 0;
                boolean snowy = block.equals("minecraft:grass_block")
                        || block.equals("minecraft:podzol")
                        || block.equals("minecraft:mycelium");
                boolean collisionUp = !block.equals("minecraft:sculk_sensor")
                        && state.isCollisionShapeFullBlock();
                return new Mc263FreezeTopLayerFeature.State(block, properties, state.isAir(),
                        block.equals("minecraft:water") || block.equals("minecraft:lava"),
                        state.cannotSupportSnowLayer(), state.supportOverrideSnowLayer(),
                        collisionUp, layers, snowy);
            }
            @Override public Mc263FreezeTopLayerFeature.Fluid fluidState(int x, int y, int z) {
                return switch (region.blockState(x, y, z).fluidKind()) {
                    case WATER_SOURCE, WATER_FLOWING -> Mc263FreezeTopLayerFeature.Fluid.WATER;
                    case LAVA_SOURCE, LAVA_FLOWING -> Mc263FreezeTopLayerFeature.Fluid.LAVA;
                    case NONE -> Mc263FreezeTopLayerFeature.Fluid.NONE;
                };
            }
            @Override public boolean supportsState(Mc263FreezeTopLayerFeature.State state) {
                return Mc263FeatureBlockState.supportsExactState(state.canonical());
            }
            @Override public boolean trySetBlockState(int x, int y, int z,
                    Mc263FreezeTopLayerFeature.State state, int flags) {
                if (flags != 2) throw new IllegalArgumentException("freeze flags must be 2");
                return region.setBlockState(x, y, z, state.canonical());
            }
        };
    }

    /** Exact live carrier for the step-nine sugar-cane/cactus BlockColumn tranche. */
    public Mc263BlockColumnVegetationFeature.WorldAccess blockColumnVegetationWorld() {
        return new Mc263BlockColumnVegetationFeature.WorldAccess() {
            @Override public int minGenerationY() { return region.minGenerationY(); }
            @Override public int height(Mc263BlockColumnVegetationFeature.Heightmap heightmap,
                    int x, int z) {
                return region.motionBlocking(x, z);
            }
            @Override public String biomeKey(int x, int y, int z) {
                return populationBiomeKey(x, y, z);
            }
            @Override public Mc263BlockColumnVegetationFeature.State blockState(
                    int x, int y, int z) {
                Mc263FeatureBlockState state = region.blockState(x, y, z);
                return blockColumnState(state);
            }
            @Override public String fluidState(int x, int y, int z) {
                return region.blockState(x, y, z).fluidTypeKey();
            }
            @Override public boolean supportsState(
                    Mc263BlockColumnVegetationFeature.State state) {
                try {
                    Mc263FeatureBlockState.fromExact(state.canonical());
                    return true;
                } catch (IllegalArgumentException missingIdentity) {
                    return false;
                }
            }
            @Override public boolean canSurvive(Mc263BlockColumnVegetationFeature.State state,
                    int x, int y, int z) {
                if (!supportsState(state)) return false;
                return switch (state.block()) {
                    case "minecraft:sugar_cane" -> sugarCaneSurvives(x, y, z);
                    case "minecraft:cactus" -> cactusSurvives(x, y, z);
                    case "minecraft:cactus_flower" -> cactusFlowerSurvives(x, y, z);
                    default -> false;
                };
            }
            @Override public boolean trySetBlockState(int x, int y, int z,
                    Mc263BlockColumnVegetationFeature.State state, int flags) {
                if (flags != 2) {
                    throw new IllegalArgumentException(
                            "unsupported block-column vegetation flags: " + flags);
                }
                if (!supportsState(state)) return false;
                return region.setBlockState(x, y, z, state.canonical());
            }
        };
    }

    /** Exact live carrier for the step-nine weighted seagrass family. */
    public Mc263SeagrassFeature.WorldAccess seagrassWorld() {
        return new Mc263SeagrassFeature.WorldAccess() {
            @Override public int minGenerationY() { return region.minGenerationY(); }
            @Override public int height(Mc263SeagrassFeature.Heightmap heightmap,
                    int x, int z) {
                return region.oceanFloorWg(x, z);
            }
            @Override public String biomeKey(int x, int y, int z) {
                return populationBiomeKey(x, y, z);
            }
            @Override public Mc263SeagrassFeature.State blockState(int x, int y, int z) {
                return seagrassState(region.blockState(x, y, z));
            }
            @Override public boolean supportsState(Mc263SeagrassFeature.State state) {
                try {
                    Mc263FeatureBlockState.fromExact(state.canonical());
                    return true;
                } catch (IllegalArgumentException missingIdentity) {
                    return false;
                }
            }
            @Override public boolean canSurvive(Mc263SeagrassFeature.State state,
                    int x, int y, int z) {
                if (!supportsState(state)) return false;
                if (state.block().equals("minecraft:tall_seagrass")) {
                    Mc263FeatureBlockState current = region.blockState(x, y, z);
                    if (current.fluidKind() != Mc263FeatureBlockState.FluidKind.WATER_SOURCE) {
                        return false;
                    }
                } else if (!state.block().equals("minecraft:seagrass")) {
                    return false;
                }
                Mc263FeatureBlockState below = region.blockState(x, y - 1, z);
                return below.isFaceSturdy(Mc263FeatureBlockState.OcclusionFace.UP)
                        && !below.blockKey().equals("minecraft:magma_block");
            }
            @Override public boolean trySetBlockState(int x, int y, int z,
                    Mc263SeagrassFeature.State state, int flags) {
                if (flags != 2) {
                    throw new IllegalArgumentException(
                            "unsupported seagrass block flags: " + flags);
                }
                if (!supportsState(state)) return false;
                return region.setBlockState(x, y, z, state.canonical());
            }
        };
    }

    private boolean sugarCaneSurvives(int x, int y, int z) {
        String below = region.blockState(x, y - 1, z).blockKey();
        if (below.equals("minecraft:sugar_cane")) return true;
        if (!supportsSugarCane(below)) return false;
        for (int[] offset : new int[][]{{0, -1}, {1, 0}, {0, 1}, {-1, 0}}) {
            int neighbourX = x + offset[0];
            int neighbourZ = z + offset[1];
            // Vanilla reads the block and fluid independently in N/E/S/W order. Keep the
            // second live read even though the current carrier stores both facts atomically.
            Mc263FeatureBlockState neighbour = region.blockState(neighbourX, y - 1,
                    neighbourZ);
            Mc263FeatureBlockState fluid = region.blockState(neighbourX, y - 1, neighbourZ);
            if (fluid.fluidKind() == Mc263FeatureBlockState.FluidKind.WATER_SOURCE
                    || fluid.fluidKind()
                    == Mc263FeatureBlockState.FluidKind.WATER_FLOWING) return true;
            // Frosted ice is the only block-tag member, and has no protocol identity yet.
            if (neighbour.blockKey().equals("minecraft:frosted_ice")) return true;
        }
        return false;
    }

    private boolean cactusSurvives(int x, int y, int z) {
        for (int[] offset : new int[][]{{0, -1}, {1, 0}, {0, 1}, {-1, 0}}) {
            Mc263FeatureBlockState neighbour = region.blockState(x + offset[0], y,
                    z + offset[1]);
            if (neighbour.isSolid()) return false;
            Mc263FeatureBlockState fluid = region.blockState(x + offset[0], y,
                    z + offset[1]);
            if (fluid.fluidKind() == Mc263FeatureBlockState.FluidKind.LAVA_SOURCE
                    || fluid.fluidKind()
                    == Mc263FeatureBlockState.FluidKind.LAVA_FLOWING) return false;
        }
        String below = region.blockState(x, y - 1, z).blockKey();
        if (!below.equals("minecraft:cactus") && !supportsCactus(below)) return false;
        return region.blockState(x, y + 1, z).fluidKind()
                == Mc263FeatureBlockState.FluidKind.NONE;
    }

    private boolean cactusFlowerSurvives(int x, int y, int z) {
        // Exposed as an exact carrier fact for completeness. BlockColumn only preflights and
        // writes this tip provider; it never invokes flower survival during configured placement.
        Mc263FeatureBlockState below = region.blockState(x, y - 1, z);
        if (below.blockKey().equals("minecraft:cactus")
                || below.blockKey().equals("minecraft:farmland")) return true;
        // CactusFlowerBlock delegates to VegetationBlock after its override lookup, causing a
        // second live read of the support position.
        return region.blockState(x, y - 1, z)
                .isFaceSturdy(Mc263FeatureBlockState.OcclusionFace.UP);
    }

    private static boolean supportsSugarCane(String key) {
        return supportsVegetation(key) || supportsCactus(key);
    }

    private static boolean supportsCactus(String key) {
        return key.equals("minecraft:sand") || key.equals("minecraft:red_sand")
                || key.equals("minecraft:suspicious_sand");
    }

    private static Mc263BlockColumnVegetationFeature.State blockColumnState(
            Mc263FeatureBlockState state) {
        return new Mc263BlockColumnVegetationFeature.State(state.blockKey(),
                exactProperties(state.exactState()),
                state.isAir());
    }

    private static Map<String, String> exactProperties(String exact) {
        int open = exact.indexOf('[');
        Map<String, String> properties = new HashMap<>();
        if (open >= 0) {
            for (String property : exact.substring(open + 1, exact.length() - 1).split(",")) {
                int equals = property.indexOf('=');
                properties.put(property.substring(0, equals), property.substring(equals + 1));
            }
        }
        return properties;
    }

    private static Mc263FeatureBlockState featureState(String exact) {
        return Mc263FeatureBlockState.fromExact(exact);
    }

    private static String property(String exact, String key) {
        return exactProperties(exact).getOrDefault(key, "");
    }

    private static String normalizeTreeKey(String key) {
        return key.startsWith("minecraft:") ? key : "minecraft:" + key;
    }

    private boolean supportsTreeFeature(String rawKey) {
        String key = normalizeTreeKey(rawKey);
        if (TREE_WRAPPER_KEYS.contains(key)
                || Mc263CommonTreeFeature.configuredKeys().contains(key)
                || Mc263CommonTreeFeature.fallenKeys().contains(key)
                || Mc263ComplexTreeFeature.treeKeys().contains(key)
                || Mc263ComplexTreeFeature.selectorKeys().contains(key)
                || Mc263PoplarTreeFeature.configuredKeys().contains(key)
                || Mc263PoplarTreeFeature.fallenKey().equals(key)
                || Mc263MangroveSwampTreeFeature.configuredKeys().contains(key)
                || Mc263MangroveSwampTreeFeature.checkedKeys().contains(key)
                || Mc263FeatureCapabilitySets.treeExtraKeys().contains(key)) return true;
        for (int index : Mc263ComplexTreeFeature.placedIndices()) {
            if (Mc263ComplexTreeFeature.placedKey(index).equals(key)) return true;
        }
        return false;
    }

    private boolean biomeContainsTree(String rawKey, int x, int y, int z) {
        String key = normalizeTreeKey(rawKey);
        return Mc263FeatureIndexReceipt.biome(populationBiomeKey(x, y, z))
                .featuresAtStep(9).stream().anyMatch(reference -> reference.featureKey().equals(key));
    }

    private boolean saplingSurvives(String sapling, int x, int y, int z) {
        if (!Mc263FeatureCapabilitySets.saplingSurvives().contains(sapling)) return false;
        return region.blockState(x, y - 1, z).isSubstrateOverworld();
    }

    private static Mc263CommonTreeFeature.State commonTreeState(Mc263FeatureBlockState state) {
        return new Mc263CommonTreeFeature.State(state.blockKey(),
                exactProperties(state.exactState()), state.isAir(), state.replaceableByTrees(),
                state.isLogsTag(), state.isLeavesTag(),
                property(state.exactState(), "persistent").equals("true"),
                state.fluidKind() == Mc263FeatureBlockState.FluidKind.WATER_SOURCE,
                state.isSolidRender());
    }

    private static boolean supportsTreeState(String block, String canonical) {
        if (Mc263FeatureBlockState.supportsExactState(canonical)) return true;
        // Legacy carrier spelling: saplings were registered without the vanilla stage property
        // before the exact 26.3 states landed, so the property-less entry still answers.
        return block.endsWith("_sapling") && canonical.equals(block + "[stage=0]")
                && Mc263FeatureBlockState.supportsExactState(block);
    }

    private static Mc263PoplarTreeFeature.State poplarTreeState(Mc263FeatureBlockState state) {
        return new Mc263PoplarTreeFeature.State(state.blockKey(),
                exactProperties(state.exactState()), state.isAir(), state.replaceableByTrees(),
                state.canBeReplaced(), state.isLogsTag(), state.isLeavesTag(),
                property(state.exactState(), "persistent").equals("true"),
                state.fluidKind() == Mc263FeatureBlockState.FluidKind.WATER_SOURCE,
                state.isSolidRender());
    }

    private boolean setTreeState(int x, int y, int z, String exact, int flags) {
        if (flags != 2 && flags != 3 && flags != 19) {
            throw new IllegalArgumentException("unsupported tree block flags: " + flags);
        }
        return region.setBlockState(x, y, z, exact);
    }

    private void markTreeAboveForPostprocessing(int x, int y, int z) {
        for (int offset = 1; offset <= 2; offset++) {
            if (region.blockState(x, y + offset, z).isAir()) return;
            region.markForPostprocessing(x, y + offset, z);
        }
    }

    private record TreePoint(int x, int y, int z) {
        // BlockPos.hashCode(): preserve vanilla HashSet bucket traversal in updateLeaves.
        @Override public int hashCode() { return (y + z * 31) * 31 + x; }
    }

    private static Set<TreePoint> commonPoints(Set<Mc263CommonTreeFeature.Pos> source) {
        Set<TreePoint> result = new HashSet<>();
        for (Mc263CommonTreeFeature.Pos pos : source) {
            result.add(new TreePoint(pos.x(), pos.y(), pos.z()));
        }
        return result;
    }

    private static Set<TreePoint> poplarPoints(Set<Mc263PoplarTreeFeature.Pos> source) {
        Set<TreePoint> result = new HashSet<>();
        for (Mc263PoplarTreeFeature.Pos pos : source) {
            result.add(new TreePoint(pos.x(), pos.y(), pos.z()));
        }
        return result;
    }

    private static Set<TreePoint> azaleaPoints(Set<Mc263AzaleaTreeFeature.Pos> source) {
        Set<TreePoint> result = new HashSet<>();
        for (Mc263AzaleaTreeFeature.Pos pos : source) {
            result.add(new TreePoint(pos.x(), pos.y(), pos.z()));
        }
        return result;
    }

    /** Normalized TreeFeature.updateLeaves plus StructureTemplate.updateShapeAtEdge. */
    private void finishTreePoints(Set<TreePoint> logs, Set<TreePoint> leaves,
            Set<TreePoint> roots, Set<TreePoint> decorations) {
        Set<TreePoint> all = new HashSet<>();
        all.addAll(logs); all.addAll(leaves); all.addAll(roots); all.addAll(decorations);
        if (all.isEmpty()) return;
        int minX = all.stream().mapToInt(TreePoint::x).min().orElseThrow();
        int maxX = all.stream().mapToInt(TreePoint::x).max().orElseThrow();
        int minY = all.stream().mapToInt(TreePoint::y).min().orElseThrow();
        int maxY = all.stream().mapToInt(TreePoint::y).max().orElseThrow();
        int minZ = all.stream().mapToInt(TreePoint::z).min().orElseThrow();
        int maxZ = all.stream().mapToInt(TreePoint::z).max().orElseThrow();
        Set<TreePoint> shape = new HashSet<>();
        shape.addAll(roots); shape.addAll(decorations);
        List<Set<TreePoint>> queues = new ArrayList<>(7);
        for (int distance = 0; distance < 7; distance++) queues.add(new HashSet<>());
        queues.get(0).addAll(logs);
        int distance = 0;
        while (distance < 7) {
            if (queues.get(distance).isEmpty()) { distance++; continue; }
            TreePoint pos = queues.get(distance).iterator().next();
            queues.get(distance).remove(pos);
            if (!inside(pos, minX, maxX, minY, maxY, minZ, maxZ)) continue;
            if (distance != 0) {
                Mc263FeatureBlockState state = region.blockState(pos.x, pos.y, pos.z);
                String currentDistance = property(state.exactState(), "distance");
                if (!currentDistance.isEmpty()) {
                    Map<String, String> properties = new java.util.TreeMap<>(
                            exactProperties(state.exactState()));
                    properties.put("distance", Integer.toString(distance));
                    setTreeState(pos.x, pos.y, pos.z,
                            canonical(state.blockKey(), properties), 19);
                }
            }
            shape.add(pos);
            for (int[] direction : TREE_DIRECTIONS) {
                TreePoint neighbor = new TreePoint(pos.x + direction[0], pos.y + direction[1],
                        pos.z + direction[2]);
                if (!inside(neighbor, minX, maxX, minY, maxY, minZ, maxZ)
                        || shape.contains(neighbor)) continue;
                Mc263FeatureBlockState state = region.blockState(
                        neighbor.x, neighbor.y, neighbor.z);
                String oldDistance = property(state.exactState(), "distance");
                if (oldDistance.isEmpty()) continue;
                int next = Math.min(Integer.parseInt(oldDistance), distance + 1);
                if (next < 7) {
                    queues.get(next).add(neighbor);
                    distance = Math.min(distance, next);
                }
            }
        }
        updateTreeShapeFaces(shape, minX, maxX, minY, maxY, minZ, maxZ);
    }

    private static boolean inside(TreePoint p, int minX, int maxX, int minY, int maxY,
            int minZ, int maxZ) {
        return p.x >= minX && p.x <= maxX && p.y >= minY && p.y <= maxY
                && p.z >= minZ && p.z <= maxZ;
    }

    private static String canonical(String block, Map<String, String> properties) {
        if (properties.isEmpty()) return block;
        return block + properties.entrySet().stream().map(entry -> entry.getKey() + "="
                + entry.getValue()).reduce("[", (left, right) -> left.equals("[")
                        ? left + right : left + "," + right) + "]";
    }

    private void updateTreeShapeFaces(Set<TreePoint> shape, int minX, int maxX,
            int minY, int maxY, int minZ, int maxZ) {
        // DiscreteVoxelShape.forAllFaces: Z/NORTH-SOUTH, then Y/DOWN-UP, then X/WEST-EAST.
        for (int x = minX; x <= maxX; x++) for (int y = minY; y <= maxY; y++) {
            scanTreeFaceLine(shape, new TreePoint(x, y, minZ), 0, 0, 1, maxZ - minZ + 1,
                    2, 3);
        }
        for (int z = minZ; z <= maxZ; z++) for (int x = minX; x <= maxX; x++) {
            scanTreeFaceLine(shape, new TreePoint(x, minY, z), 0, 1, 0, maxY - minY + 1,
                    0, 1);
        }
        for (int y = minY; y <= maxY; y++) for (int z = minZ; z <= maxZ; z++) {
            scanTreeFaceLine(shape, new TreePoint(minX, y, z), 1, 0, 0, maxX - minX + 1,
                    4, 5);
        }
    }

    private void scanTreeFaceLine(Set<TreePoint> shape, TreePoint start, int dx, int dy, int dz,
            int length, int negativeDirection, int positiveDirection) {
        boolean previous = false;
        TreePoint previousPos = start;
        for (int index = 0; index <= length; index++) {
            TreePoint current = new TreePoint(start.x + dx * index, start.y + dy * index,
                    start.z + dz * index);
            boolean full = index != length && shape.contains(current);
            if (!previous && full) updateTreeShapeFace(current, negativeDirection);
            if (previous && !full) updateTreeShapeFace(previousPos, positiveDirection);
            previous = full;
            previousPos = current;
        }
    }

    private void updateTreeShapeFace(TreePoint pos, int direction) {
        int[] delta = TREE_DIRECTIONS[direction];
        TreePoint neighbor = new TreePoint(pos.x + delta[0], pos.y + delta[1],
                pos.z + delta[2]);
        Mc263FeatureBlockState state = region.blockState(pos.x, pos.y, pos.z);
        Mc263FeatureBlockState neighborState = region.blockState(
                neighbor.x, neighbor.y, neighbor.z);
        String newExact = updateTreeShapeState(pos, state, direction, neighborState);
        if (!state.exactState().equals(newExact)) {
            setTreeState(pos.x, pos.y, pos.z, newExact, 2);
            state = region.blockState(pos.x, pos.y, pos.z);
        }
        int opposite = direction ^ 1;
        String newNeighbor = updateTreeShapeState(neighbor, neighborState, opposite, state);
        if (!neighborState.exactState().equals(newNeighbor)) {
            setTreeState(neighbor.x, neighbor.y, neighbor.z, newNeighbor, 2);
        }
    }

    private String updateTreeShapeState(TreePoint pos, Mc263FeatureBlockState state,
            int direction, Mc263FeatureBlockState neighbor) {
        Map<String, String> properties = exactProperties(state.exactState());
        if (state.isLeavesTag()) {
            if (state.fluidKind() == Mc263FeatureBlockState.FluidKind.WATER_SOURCE) {
                region.scheduleFluidTick(pos.x, pos.y, pos.z, "minecraft:water", 5);
            }
            int neighborDistance = neighbor.isLogsTag() ? 0
                    : parseLeafDistance(neighbor.exactState());
            int candidate = neighborDistance + 1;
            int own = parseLeafDistance(state.exactState());
            if (candidate != 1 || own != candidate) {
                region.scheduleBlockTick(pos.x, pos.y, pos.z, state.blockKey(), 1);
            }
            return state.exactState();
        }
        if (state.blockKey().equals("minecraft:leaf_litter")) {
            Mc263FeatureBlockState below = region.blockState(pos.x, pos.y - 1, pos.z);
            return below.isFaceSturdy(Mc263FeatureBlockState.OcclusionFace.UP)
                    ? state.exactState() : "minecraft:air";
        }
        if (state.blockKey().equals("minecraft:pale_hanging_moss")) {
            Mc263FeatureBlockState above = region.blockState(pos.x, pos.y + 1, pos.z);
            boolean supported = above.blockKey().equals("minecraft:pale_hanging_moss")
                    || above.isFaceSturdy(Mc263FeatureBlockState.OcclusionFace.DOWN);
            if (!supported) {
                region.scheduleBlockTick(pos.x, pos.y, pos.z,
                        "minecraft:pale_hanging_moss", 1);
            }
            boolean tip = !region.blockState(pos.x, pos.y - 1, pos.z).blockKey()
                    .equals("minecraft:pale_hanging_moss");
            properties.put("tip", Boolean.toString(tip));
            return canonical(state.blockKey(), properties);
        }
        if (state.blockKey().equals("minecraft:mangrove_propagule")) {
            if (state.fluidKind() == Mc263FeatureBlockState.FluidKind.WATER_SOURCE) {
                region.scheduleFluidTick(pos.x, pos.y, pos.z, "minecraft:water", 5);
            }
            boolean hanging = properties.getOrDefault("hanging", "false").equals("true");
            boolean supported = hanging
                    ? region.blockState(pos.x, pos.y + 1, pos.z).blockKey()
                            .equals("minecraft:mangrove_leaves")
                    : region.blockState(pos.x, pos.y - 1, pos.z).isSubstrateOverworld();
            if ((hanging && direction == 1 || !hanging) && !supported) return "minecraft:air";
            return state.exactState();
        }
        String directionKey = switch (direction) {
            case 0 -> "down"; case 1 -> "up"; case 2 -> "north";
            case 3 -> "south"; case 4 -> "west"; case 5 -> "east";
            default -> throw new IllegalArgumentException("tree direction " + direction);
        };
        if (state.blockKey().equals("minecraft:cocoa")
                && directionKey.equals(properties.get("facing"))
                && !neighbor.isJungleLogsTag()) {
            return "minecraft:air";
        }
        if (state.blockKey().equals("minecraft:shelf_mushroom")
                && oppositeDirection(directionKey).equals(properties.get("facing"))
                && !neighbor.isFaceSturdy(oppositeFace(direction))) {
            return "minecraft:air";
        }
        if (state.blockKey().equals("minecraft:vine") && direction != 0) {
            if (properties.getOrDefault("up", "false").equals("true")) {
                boolean supported = region.blockState(pos.x, pos.y + 1, pos.z)
                        .isSupportOrCollisionFull(Mc263FeatureBlockState.OcclusionFace.DOWN);
                properties.put("up", Boolean.toString(supported));
            }
            Mc263FeatureBlockState above = region.blockState(pos.x, pos.y + 1, pos.z);
            for (int horizontal = 2; horizontal < TREE_DIRECTIONS.length; horizontal++) {
                String face = switch (horizontal) {
                    case 2 -> "north"; case 3 -> "south";
                    case 4 -> "west"; case 5 -> "east";
                    default -> throw new AssertionError(horizontal);
                };
                if (!properties.getOrDefault(face, "false").equals("true")) continue;
                int[] delta = TREE_DIRECTIONS[horizontal];
                boolean supported = region.blockState(pos.x + delta[0], pos.y + delta[1],
                        pos.z + delta[2]).isSupportOrCollisionFull(oppositeFace(horizontal));
                if (!supported && above.blockKey().equals("minecraft:vine")) {
                    supported = property(above.exactState(), face).equals("true");
                }
                properties.put(face, Boolean.toString(supported));
            }
            boolean any = properties.values().stream().anyMatch("true"::equals);
            return any ? canonical(state.blockKey(), properties) : "minecraft:air";
        }
        return state.exactState();
    }

    private static int parseLeafDistance(String exact) {
        String value = property(exact, "distance");
        return value.isEmpty() ? 7 : Integer.parseInt(value);
    }

    private static String oppositeDirection(String direction) {
        return switch (direction) {
            case "down" -> "up"; case "up" -> "down";
            case "north" -> "south"; case "south" -> "north";
            case "west" -> "east"; case "east" -> "west";
            default -> throw new IllegalArgumentException(direction);
        };
    }

    private static Mc263FeatureBlockState.OcclusionFace oppositeFace(int direction) {
        return switch (direction ^ 1) {
            case 0 -> Mc263FeatureBlockState.OcclusionFace.DOWN;
            case 1 -> Mc263FeatureBlockState.OcclusionFace.UP;
            case 2 -> Mc263FeatureBlockState.OcclusionFace.NORTH;
            case 3 -> Mc263FeatureBlockState.OcclusionFace.SOUTH;
            case 4 -> Mc263FeatureBlockState.OcclusionFace.WEST;
            case 5 -> Mc263FeatureBlockState.OcclusionFace.EAST;
            default -> throw new IllegalArgumentException("tree direction " + direction);
        };
    }

    private static boolean isMossReplaceable(String key) {
        return Mc263FeatureCapabilitySets.mossReplaceable().contains(key);
    }

    private static boolean isAdditionalLushGroundReplaceable(String key) {
        return Mc263FeatureCapabilitySets.lushGroundExtra().contains(key);
    }

    private static boolean isReplaceableByTrees(Mc263FeatureBlockState state) {
        return state.isLeaves() || switch (state.blockKey()) {
            case "minecraft:dandelion", "minecraft:open_eyeblossom", "minecraft:poppy",
                    "minecraft:blue_orchid", "minecraft:allium",
                    "minecraft:azure_bluet", "minecraft:red_tulip",
                    "minecraft:orange_tulip", "minecraft:white_tulip",
                    "minecraft:pink_tulip", "minecraft:oxeye_daisy",
                    "minecraft:cornflower", "minecraft:lily_of_the_valley",
                    "minecraft:wither_rose", "minecraft:torchflower",
                    "minecraft:closed_eyeblossom", "minecraft:golden_dandelion",
                    "minecraft:pale_moss_carpet", "minecraft:short_grass",
                    "minecraft:fern", "minecraft:dead_bush", "minecraft:vine",
                    "minecraft:glow_lichen", "minecraft:sunflower", "minecraft:lilac",
                    "minecraft:rose_bush", "minecraft:peony", "minecraft:tall_grass",
                    "minecraft:large_fern", "minecraft:hanging_roots",
                    "minecraft:pitcher_plant", "minecraft:water",
                    "minecraft:seagrass", "minecraft:tall_seagrass", "minecraft:bush",
                    "minecraft:firefly_bush", "minecraft:warped_roots",
                    "minecraft:nether_sprouts", "minecraft:crimson_roots",
                    "minecraft:leaf_litter", "minecraft:short_dry_grass",
                    "minecraft:tall_dry_grass" -> true;
            default -> false;
        };
    }

    private static boolean supportsAzaleaGrowth(String key) {
        return Mc263FeatureCapabilitySets.azaleaGrowsOn().contains(key);
    }

    private static boolean isWater(Mc263FeatureBlockState state) {
        return state.fluidKind() == Mc263FeatureBlockState.FluidKind.WATER_SOURCE
                || state.fluidKind() == Mc263FeatureBlockState.FluidKind.WATER_FLOWING;
    }

    private static String classicVineCanonical(Mc263ClassicVinesCaveFeature.State state) {
        if (!state.block().equals("minecraft:vine")) return state.block();
        for (Map.Entry<String, String> property : state.properties().entrySet()) {
            if (!Set.of("up", "north", "south", "west", "east")
                    .contains(property.getKey())
                    || !(property.getValue().equals("true")
                    || property.getValue().equals("false"))) {
                return "minecraft:unsupported_classic_vine";
            }
        }
        return "minecraft:vine[east=" + state.properties().getOrDefault("east", "false")
                + ",north=" + state.properties().getOrDefault("north", "false")
                + ",south=" + state.properties().getOrDefault("south", "false")
                + ",up=" + state.properties().getOrDefault("up", "false")
                + ",west=" + state.properties().getOrDefault("west", "false") + "]";
    }

    private static Mc263SeagrassFeature.State seagrassState(Mc263FeatureBlockState state) {
        String exact = state.exactState();
        int open = exact.indexOf('[');
        Map<String, String> properties = new HashMap<>();
        if (open >= 0) {
            for (String property : exact.substring(open + 1, exact.length() - 1).split(",")) {
                int equals = property.indexOf('=');
                properties.put(property.substring(0, equals), property.substring(equals + 1));
            }
        }
        return new Mc263SeagrassFeature.State(state.blockKey(), properties,
                state.fluidTypeKey(),
                state.fluidKind() == Mc263FeatureBlockState.FluidKind.WATER_SOURCE
                        || state.fluidKind() == Mc263FeatureBlockState.FluidKind.LAVA_SOURCE,
                state.isAir(), state.canBeReplaced());
    }

    private static Mc263KelpFeature.State kelpState(Mc263FeatureBlockState state) {
        String exact = state.exactState();
        int open = exact.indexOf('[');
        Map<String, String> properties = new HashMap<>();
        if (open >= 0) {
            for (String property : exact.substring(open + 1, exact.length() - 1).split(",")) {
                int equals = property.indexOf('=');
                properties.put(property.substring(0, equals), property.substring(equals + 1));
            }
        }
        return new Mc263KelpFeature.State(state.blockKey(), properties,
                state.fluidTypeKey(),
                state.fluidKind() == Mc263FeatureBlockState.FluidKind.WATER_SOURCE,
                state.blockKey().equals("minecraft:magma_block"));
    }

    private int motionBlockingNoLeaves(int x, int z) {
        int top = region.minGenerationY() + region.generationDepth() - 1;
        for (int y = top; y >= region.minGenerationY(); y--) {
            Mc263FeatureBlockState state = region.blockState(x, y, z);
            if (state.blocksMotionInHeightmapNoLeaves()
                    || state.fluidKind() != Mc263FeatureBlockState.FluidKind.NONE) return y + 1;
        }
        return region.minGenerationY();
    }

    private static Mc263SimpleVegetationFeature.State simpleVegetationState(
            Mc263FeatureBlockState state) {
        String exact = state.exactState();
        int open = exact.indexOf('[');
        Map<String, String> properties = new HashMap<>();
        if (open >= 0) {
            for (String property : exact.substring(open + 1, exact.length() - 1).split(",")) {
                int equals = property.indexOf('=');
                properties.put(property.substring(0, equals), property.substring(equals + 1));
            }
        }
        return new Mc263SimpleVegetationFeature.State(state.blockKey(), properties,
                state.fluidTypeKey(), state.isAir(), state.canBeReplaced());
    }

    private static boolean isDryVegetation(String key) {
        return Mc263FeatureCapabilitySets.dryVegetation().contains(key);
    }

    private static boolean supportsVegetation(String key) {
        return Mc263FeatureBlockState.supportsVegetationTag(key);
    }

    private static boolean supportsDryVegetation(String key) {
        return supportsVegetation(key)
                || Mc263FeatureCapabilitySets.dryVegetationExtra().contains(key);
    }

    /** Exact atomic state/fluid view for the step-seven speleothem family. */
    public Mc263SpeleothemKernel.WorldAccess speleothemWorld() {
        return new Mc263SpeleothemKernel.WorldAccess() {
            @Override public int minGenerationY() { return region.minGenerationY(); }
            @Override public int generationDepth() { return region.generationDepth(); }
            @Override public String biomeKey(int x, int y, int z) {
                return populationBiomeKey(x, y, z);
            }
            @Override public Mc263SpeleothemKernel.State blockState(int x, int y, int z) {
                Mc263FeatureBlockState state = region.blockState(x, y, z);
                boolean water = state.fluidKind() == Mc263FeatureBlockState.FluidKind.WATER_SOURCE
                        || state.fluidKind()
                        == Mc263FeatureBlockState.FluidKind.WATER_FLOWING;
                return new Mc263SpeleothemKernel.State(state.exactState(), state.isAir(),
                        state.isSolid(), water);
            }
            @Override public boolean trySetBlockState(int x, int y, int z,
                    String exactState, int flags) {
                if (flags != Mc263SpeleothemKernel.SET_BLOCK_FLAGS) {
                    throw new IllegalArgumentException(
                            "unsupported speleothem block flags: " + flags);
                }
                return region.setBlockState(x, y, z, exactState);
            }
        };
    }

    /** Exact typed live view for the dormant step-seven sculk-vein leaf. */
    public Mc263SculkVeinFeature.WorldAccess sculkVeinWorld() {
        return new Mc263SculkVeinFeature.WorldAccess() {
            @Override public int minGenerationY() {
                return region.minGenerationY();
            }

            @Override public int generationDepth() {
                return region.generationDepth();
            }

            @Override public String biomeKey(int blockX, int blockY, int blockZ) {
                return populationBiomeKey(blockX, blockY, blockZ);
            }

            @Override public Mc263SculkVeinFeature.State blockState(
                    int blockX, int blockY, int blockZ) {
                return sculkState(region.blockState(blockX, blockY, blockZ));
            }

            @Override public boolean canAttachTo(int blockX, int blockY, int blockZ,
                    Mc263SculkVeinFeature.Direction face) {
                int neighbourX = blockX + directionX(face);
                int neighbourY = blockY + directionY(face);
                int neighbourZ = blockZ + directionZ(face);
                Mc263FeatureBlockState neighbour =
                        region.blockState(neighbourX, neighbourY, neighbourZ);
                return neighbour.isSupportOrCollisionFull(carrierFace(face.opposite()));
            }

            @Override public boolean isFaceSturdy(int blockX, int blockY, int blockZ,
                    Mc263SculkVeinFeature.Direction face) {
                return region.blockState(blockX, blockY, blockZ)
                        .isFaceSturdy(carrierFace(face));
            }

            @Override public boolean trySetBlockState(int blockX, int blockY, int blockZ,
                    Mc263SculkVeinFeature.State state, int flags) {
                if (flags != Mc263SculkVeinFeature.DIRECT_UPDATE_FLAGS
                        && flags != Mc263SculkVeinFeature.SPREAD_UPDATE_FLAGS) {
                    throw new IllegalArgumentException(
                            "unsupported sculk-vein block flags: " + flags);
                }
                return region.setBlockState(blockX, blockY, blockZ, state.canonical());
            }

            @Override public boolean tryMarkPosForPostProcessing(
                    int blockX, int blockY, int blockZ) {
                return region.markForPostprocessing(blockX, blockY, blockZ);
            }
        };
    }

    /** Exact live carrier view for the dormant step-seven sculk-patch leaf. */
    public Mc263SculkPatchFeature.WorldAccess sculkPatchWorld() {
        return new Mc263SculkPatchFeature.WorldAccess() {
            @Override public int minGenerationY() { return region.minGenerationY(); }
            @Override public int generationDepth() { return region.generationDepth(); }
            @Override public String biomeKey(int x, int y, int z) {
                return populationBiomeKey(x, y, z);
            }
            @Override public Mc263SculkPatchFeature.State blockState(int x, int y, int z) {
                return sculkPatchState(region.blockState(x, y, z));
            }
            @Override public Mc263SculkPatchFeature.FluidFact fluidState(int x, int y, int z) {
                Mc263FeatureBlockState state = region.blockState(x, y, z);
                return new Mc263SculkPatchFeature.FluidFact(state.fluidTypeKey(),
                        state.fluidKind() == Mc263FeatureBlockState.FluidKind.WATER_SOURCE);
            }
            @Override public boolean isCollisionShapeFullBlock(int x, int y, int z) {
                return region.blockState(x, y, z).isCollisionShapeFullBlock();
            }
            @Override public boolean isFaceSturdy(int x, int y, int z,
                    Mc263SculkPatchFeature.Direction face) {
                return region.blockState(x, y, z).isFaceSturdy(patchCarrierFace(face));
            }
            @Override public boolean canAttachTo(int x, int y, int z,
                    Mc263SculkPatchFeature.Direction face) {
                Mc263FeatureBlockState support = region.blockState(
                        x + patchDirectionX(face), y + patchDirectionY(face),
                        z + patchDirectionZ(face));
                return support.isSupportOrCollisionFull(
                        patchCarrierFace(face.opposite()));
            }
            @Override public boolean sectionMayContainGrowthInhibitor(
                    int sectionX, int sectionY, int sectionZ) {
                return region.sectionMayContainSculkGrowthInhibitor(
                        sectionX, sectionY, sectionZ);
            }
            @Override public boolean trySetBlockState(int x, int y, int z,
                    Mc263SculkPatchFeature.State state, int flags) {
                if (flags != Mc263SculkPatchFeature.DIRECT_FLAGS
                        && flags != Mc263SculkPatchFeature.SPREAD_FLAGS) {
                    throw new IllegalArgumentException(
                            "unsupported sculk-patch block flags: " + flags);
                }
                return region.setBlockState(x, y, z, sculkPatchExact(state));
            }
            @Override public boolean tryMarkPosForPostProcessing(int x, int y, int z) {
                return region.markForPostprocessing(x, y, z);
            }
            // WorldGenRegion sound, level-event, and entity-push surfaces are intentionally
            // effectless during FEATURES; the interface defaults preserve exact call order.
        };
    }

    public static Mc263SculkPatchFeature.State sculkPatchState(
            Mc263FeatureBlockState state) {
        int faces = state.blockKey().equals(Mc263SculkPatchFeature.VEIN)
                ? sculkPatchFaces(state.exactState()) : 0;
        boolean waterlogged = state.fluidKind()
                == Mc263FeatureBlockState.FluidKind.WATER_SOURCE
                && (state.blockKey().equals(Mc263SculkPatchFeature.VEIN)
                || state.blockKey().equals(Mc263SculkPatchFeature.SENSOR)
                || state.blockKey().equals(Mc263SculkPatchFeature.SHRIEKER));
        Mc263SculkPatchFeature.Capability capability = switch (state.capability()) {
            case SCULK_CATALYST -> Mc263SculkPatchFeature.Capability.CATALYST_BLOCK_ENTITY;
            case SCULK_SENSOR -> Mc263SculkPatchFeature.Capability.SENSOR_BLOCK_ENTITY;
            case SCULK_SHRIEKER -> Mc263SculkPatchFeature.Capability.SHRIEKER_BLOCK_ENTITY;
            default -> Mc263SculkPatchFeature.Capability.NONE;
        };
        String exact = state.exactState();
        return new Mc263SculkPatchFeature.State(state.blockKey(), faces, waterlogged,
                state.fluidTypeKey(),
                state.fluidKind() == Mc263FeatureBlockState.FluidKind.WATER_SOURCE,
                state.isAir(), state.canBeReplaced(), state.regularSculkReplaceable(),
                state.worldgenSculkReplaceable(), state.sculkGrowthInhibitor(), capability,
                exact.contains("[bloom=true]"),
                state.blockKey().equals(Mc263SculkPatchFeature.SENSOR) ? 0 : 0,
                state.blockKey().equals(Mc263SculkPatchFeature.SENSOR) ? 0 : 0,
                exact.contains("shrieking=true"), exact.contains("can_summon=true"));
    }

    private static int sculkPatchFaces(String exact) {
        int faces = 0;
        if (exact.contains("[down=true,")) faces |= 1;
        if (exact.contains(",up=true,")) faces |= 2;
        if (exact.contains(",north=true,")) faces |= 4;
        if (exact.contains(",south=true,")) faces |= 8;
        if (exact.contains(",west=true]")) faces |= 16;
        if (exact.contains(",east=true,")) faces |= 32;
        return faces;
    }

    public static String sculkPatchExact(Mc263SculkPatchFeature.State state) {
        return switch (state.capability()) {
            case CATALYST_BLOCK_ENTITY -> Mc263SculkPatchFeature.CATALYST
                    + "[bloom=" + state.catalystBloom() + "]";
            case SENSOR_BLOCK_ENTITY -> Mc263SculkPatchFeature.SENSOR
                    + "[power=" + state.sensorPower() + ",sculk_sensor_phase=inactive,"
                    + "waterlogged=" + state.waterlogged() + "]";
            case SHRIEKER_BLOCK_ENTITY -> Mc263SculkPatchFeature.SHRIEKER
                    + "[can_summon=" + state.canSummon() + ",shrieking=" + state.shrieking()
                    + ",waterlogged=" + state.waterlogged() + "]";
            case NONE -> state.block().equals(Mc263SculkPatchFeature.VEIN)
                    ? sculkVeinExact(state.faces(), state.waterlogged()) : state.block();
        };
    }

    private static String sculkVeinExact(int faces, boolean waterlogged) {
        return "minecraft:sculk_vein[down=" + ((faces & 1) != 0)
                + ",east=" + ((faces & 32) != 0) + ",north=" + ((faces & 4) != 0)
                + ",south=" + ((faces & 8) != 0) + ",up=" + ((faces & 2) != 0)
                + ",waterlogged=" + waterlogged + ",west=" + ((faces & 16) != 0) + "]";
    }

    private static Mc263FeatureBlockState.OcclusionFace patchCarrierFace(
            Mc263SculkPatchFeature.Direction face) {
        return Mc263FeatureBlockState.OcclusionFace.valueOf(face.name());
    }

    private static int patchDirectionX(Mc263SculkPatchFeature.Direction direction) {
        return direction == Mc263SculkPatchFeature.Direction.WEST ? -1
                : direction == Mc263SculkPatchFeature.Direction.EAST ? 1 : 0;
    }

    private static int patchDirectionY(Mc263SculkPatchFeature.Direction direction) {
        return direction == Mc263SculkPatchFeature.Direction.DOWN ? -1
                : direction == Mc263SculkPatchFeature.Direction.UP ? 1 : 0;
    }

    private static int patchDirectionZ(Mc263SculkPatchFeature.Direction direction) {
        return direction == Mc263SculkPatchFeature.Direction.NORTH ? -1
                : direction == Mc263SculkPatchFeature.Direction.SOUTH ? 1 : 0;
    }

    private static Mc263SculkVeinFeature.State sculkState(Mc263FeatureBlockState state) {
        int faces = 0;
        if (state.blockKey().equals(Mc263SculkVeinFeature.SCULK_VEIN)) {
            String exact = state.exactState();
            faces |= exact.contains("[down=true,") ? 1 : 0;
            faces |= exact.contains(",up=true,") ? 2 : 0;
            faces |= exact.contains(",north=true,") ? 4 : 0;
            faces |= exact.contains(",south=true,") ? 8 : 0;
            faces |= exact.contains(",west=true]") ? 16 : 0;
            faces |= exact.contains(",east=true,") ? 32 : 0;
            return Mc263SculkVeinFeature.State.vein(faces,
                    state.fluidKind() == Mc263FeatureBlockState.FluidKind.WATER_SOURCE);
        }
        return new Mc263SculkVeinFeature.State(state.blockKey(), 0, false,
                state.fluidTypeKey(),
                state.fluidKind() == Mc263FeatureBlockState.FluidKind.WATER_SOURCE,
                state.canBeReplaced(), state.isFire());
    }

    private static Mc263FeatureBlockState.OcclusionFace carrierFace(
            Mc263SculkVeinFeature.Direction face) {
        return switch (face) {
            case DOWN -> Mc263FeatureBlockState.OcclusionFace.DOWN;
            case UP -> Mc263FeatureBlockState.OcclusionFace.UP;
            case NORTH -> Mc263FeatureBlockState.OcclusionFace.NORTH;
            case SOUTH -> Mc263FeatureBlockState.OcclusionFace.SOUTH;
            case WEST -> Mc263FeatureBlockState.OcclusionFace.WEST;
            case EAST -> Mc263FeatureBlockState.OcclusionFace.EAST;
        };
    }

    private static int directionX(Mc263SculkVeinFeature.Direction direction) {
        return switch (direction) {
            case WEST -> -1;
            case EAST -> 1;
            default -> 0;
        };
    }

    private static int directionY(Mc263SculkVeinFeature.Direction direction) {
        return switch (direction) {
            case DOWN -> -1;
            case UP -> 1;
            default -> 0;
        };
    }

    private static int directionZ(Mc263SculkVeinFeature.Direction direction) {
        return switch (direction) {
            case NORTH -> -1;
            case SOUTH -> 1;
            default -> 0;
        };
    }

    /** Exact flag-2 and full-face view for the underwater-magma leaf. */
    public Mc263UnderwaterMagmaFeature.WorldAccess underwaterMagmaWorld() {
        return new Mc263UnderwaterMagmaFeature.WorldAccess() {
            @Override public int minGenerationY() {
                return Mc263FeatureWorldAdapter.this.minGenerationY();
            }
            @Override public int generationDepth() {
                return Mc263FeatureWorldAdapter.this.generationDepth();
            }
            @Override public int oceanFloorWgHeight(int x, int z) {
                return Mc263FeatureWorldAdapter.this.oceanFloorWgHeight(x, z);
            }
            @Override public String biomeKey(int x, int y, int z) {
                return Mc263FeatureWorldAdapter.this.biomeKey(x, y, z);
            }
            @Override public String blockState(int x, int y, int z) {
                return Mc263FeatureWorldAdapter.this.blockState(x, y, z);
            }
            @Override public Mc263UnderwaterMagmaFeature.FaceFact faceFact(int x, int y, int z,
                    Mc263UnderwaterMagmaFeature.Face coveredFace) {
                Mc263FeatureBlockState state = region.blockState(x, y, z);
                Mc263FeatureBlockState.OcclusionFace face = switch (coveredFace) {
                    case UP -> Mc263FeatureBlockState.OcclusionFace.UP;
                    case SOUTH -> Mc263FeatureBlockState.OcclusionFace.SOUTH;
                    case WEST -> Mc263FeatureBlockState.OcclusionFace.WEST;
                    case NORTH -> Mc263FeatureBlockState.OcclusionFace.NORTH;
                    case EAST -> Mc263FeatureBlockState.OcclusionFace.EAST;
                };
                return new Mc263UnderwaterMagmaFeature.FaceFact(state.exactState(),
                        state.isFaceOcclusionFull(face)
                                ? Mc263UnderwaterMagmaFeature.FaceOcclusion.FULL
                                : Mc263UnderwaterMagmaFeature.FaceOcclusion.NOT_FULL);
            }
            @Override public boolean trySetBlockState(int x, int y, int z, String state,
                    int flags) {
                if (flags != Mc263UnderwaterMagmaFeature.UPDATE_FLAGS) {
                    throw new IllegalArgumentException(
                            "unsupported underwater-magma block flags: " + flags);
                }
                return region.setBlockState(x, y, z, state);
            }
        };
    }

    /** Exact solid-render/fluid view for the disk-grass rule provider. */
    public Mc263DiskGrassFeature.WorldAccess diskGrassWorld() {
        return new Mc263DiskGrassFeature.WorldAccess() {
            @Override public int minGenerationY() {
                return Mc263FeatureWorldAdapter.this.minGenerationY();
            }
            @Override public int generationDepth() {
                return Mc263FeatureWorldAdapter.this.generationDepth();
            }
            @Override public int oceanFloorWgHeight(int x, int z) {
                return Mc263FeatureWorldAdapter.this.oceanFloorWgHeight(x, z);
            }
            @Override public String biomeKey(int x, int y, int z) {
                return Mc263FeatureWorldAdapter.this.biomeKey(x, y, z);
            }
            @Override public String blockState(int x, int y, int z) {
                return Mc263FeatureWorldAdapter.this.blockState(x, y, z);
            }
            @Override public boolean isSolid(int x, int y, int z) {
                return region.blockState(x, y, z).isSolidRender();
            }
            @Override public String fluidState(int x, int y, int z) {
                return region.blockState(x, y, z).fluidTypeKey();
            }
            @Override public boolean trySetBlockState(int x, int y, int z, String state,
                    int flags) {
                if (flags != Mc263DiskGrassFeature.UPDATE_FLAGS) {
                    throw new IllegalArgumentException(
                            "unsupported disk-grass block flags: " + flags);
                }
                return region.setBlockState(x, y, z, state);
            }
            @Override public boolean tryMarkPosForPostProcessing(int x, int y, int z) {
                return region.markForPostprocessing(x, y, z);
            }
        };
    }

    /** Exact ore view: unlike the shared disk/well method this boundary accepts only flag 2. */
    public Mc263OreFeature.WorldAccess oreWorld() {
        return new Mc263OreFeature.WorldAccess() {
            @Override
            public int minGenerationY() {
                return Mc263FeatureWorldAdapter.this.minGenerationY();
            }

            @Override
            public int generationDepth() {
                return Mc263FeatureWorldAdapter.this.generationDepth();
            }

            @Override
            public int oceanFloorWg(int blockX, int blockZ) {
                return Mc263FeatureWorldAdapter.this.oceanFloorWg(blockX, blockZ);
            }

            @Override
            public String biomeKey(int blockX, int blockY, int blockZ) {
                return Mc263FeatureWorldAdapter.this.biomeKey(blockX, blockY, blockZ);
            }

            @Override
            public String blockState(int blockX, int blockY, int blockZ) {
                return Mc263FeatureWorldAdapter.this.blockState(blockX, blockY, blockZ);
            }

            /** The live state already carries its property-free key; do not re-derive it. */
            @Override
            public String blockKey(int blockX, int blockY, int blockZ) {
                return region.blockState(blockX, blockY, blockZ).blockKey();
            }

            @Override
            public boolean ensureCanWrite(int blockX, int blockY, int blockZ) {
                return Mc263FeatureWorldAdapter.this.ensureCanWrite(blockX, blockY, blockZ);
            }

            @Override
            public boolean trySetBlockState(int blockX, int blockY, int blockZ, String state,
                    int flags) {
                if (flags != Mc263OreFeature.SET_BLOCK_FLAGS) {
                    throw new IllegalArgumentException("unsupported ore block flags: " + flags);
                }
                return region.setBlockState(blockX, blockY, blockZ, state);
            }
        };
    }

    /** Separate view because Geode's typed block-state return cannot overload String blockState. */
    public Mc263GeodeFeature.WorldAccess geodeWorld() {
        return new Mc263GeodeFeature.WorldAccess() {
            @Override
            public long worldSeed() {
                return worldSeed;
            }

            @Override
            public int minGenerationY() {
                return Mc263FeatureWorldAdapter.this.minGenerationY();
            }

            @Override
            public int generationDepth() {
                return Mc263FeatureWorldAdapter.this.generationDepth();
            }

            @Override
            public String biomeKey(int blockX, int blockY, int blockZ) {
                return Mc263FeatureWorldAdapter.this.biomeKey(blockX, blockY, blockZ);
            }

            @Override
            public Mc263GeodeFeature.BlockStateFacts blockState(
                    int blockX, int blockY, int blockZ) {
                Mc263FeatureBlockState state = region.blockState(blockX, blockY, blockZ);
                boolean source = state.fluidKind()
                        == Mc263FeatureBlockState.FluidKind.WATER_SOURCE
                        || state.fluidKind() == Mc263FeatureBlockState.FluidKind.LAVA_SOURCE;
                return new Mc263GeodeFeature.BlockStateFacts(state.exactState(), state.isAir(),
                        state.geodeInvalid(), state.featuresCannotReplace(),
                        new Mc263GeodeFeature.FluidFacts(state.fluidTypeKey(),
                                state.fluidKind() == Mc263FeatureBlockState.FluidKind.NONE,
                                source, state.fluidAmount()));
            }

            @Override
            public boolean setBlockState(int blockX, int blockY, int blockZ, String state) {
                return region.setBlockState(blockX, blockY, blockZ, state);
            }

            @Override
            public void scheduleFluidTick(int blockX, int blockY, int blockZ,
                    String fluidType, int delay) {
                region.scheduleFluidTick(blockX, blockY, blockZ, fluidType, delay);
            }
        };
    }

    /** Separate exact view for the rooted-sulfur leaf's pinned state traits and tag predicates. */
    public Mc263RootedSulfurSpringFeature.WorldAccess rootedSulfurWorld() {
        return new Mc263RootedSulfurSpringFeature.WorldAccess() {
            @Override
            public int minGenerationY() {
                return Mc263FeatureWorldAdapter.this.minGenerationY();
            }

            @Override
            public int generationDepth() {
                return Mc263FeatureWorldAdapter.this.generationDepth();
            }

            @Override
            public String biomeKey(int blockX, int blockY, int blockZ) {
                return Mc263FeatureWorldAdapter.this.biomeKey(blockX, blockY, blockZ);
            }

            @Override
            public String blockState(int blockX, int blockY, int blockZ) {
                return Mc263FeatureWorldAdapter.this.blockState(blockX, blockY, blockZ);
            }

            @Override
            public int worldSurface(int blockX, int blockZ) {
                return Mc263FeatureWorldAdapter.this.worldSurfaceWg(blockX, blockZ);
            }

            @Override
            public boolean isSolid(int blockX, int blockY, int blockZ) {
                return state(blockX, blockY, blockZ).isSolid();
            }

            @Override
            public boolean isWater(int blockX, int blockY, int blockZ) {
                Mc263FeatureBlockState.FluidKind fluid =
                        state(blockX, blockY, blockZ).fluidKind();
                return fluid == Mc263FeatureBlockState.FluidKind.WATER_SOURCE
                        || fluid == Mc263FeatureBlockState.FluidKind.WATER_FLOWING;
            }

            @Override
            public boolean hasLavaFluid(int blockX, int blockY, int blockZ) {
                Mc263FeatureBlockState.FluidKind fluid =
                        state(blockX, blockY, blockZ).fluidKind();
                return fluid == Mc263FeatureBlockState.FluidKind.LAVA_SOURCE
                        || fluid == Mc263FeatureBlockState.FluidKind.LAVA_FLOWING;
            }

            @Override
            public boolean isFaceSturdyDown(int blockX, int blockY, int blockZ) {
                return state(blockX, blockY, blockZ).isFaceSturdyDown();
            }

            @Override
            public boolean isInBlockTag(int blockX, int blockY, int blockZ, String tagKey) {
                Mc263FeatureBlockState state = state(blockX, blockY, blockZ);
                return switch (tagKey) {
                    case Mc263LakeFeature.AIR_TAG -> state.isAir();
                    case Mc263RootedSulfurSpringFeature.AZALEA_ROOT_REPLACEABLE ->
                            state.azaleaRootReplaceable();
                    default -> throw new IllegalArgumentException(
                            "unsupported rooted-sulfur block tag: " + tagKey);
                };
            }

            @Override
            public boolean trySetBlockState(int blockX, int blockY, int blockZ, String state) {
                return Mc263FeatureWorldAdapter.this.trySetBlockState(
                        blockX, blockY, blockZ, state);
            }

            private Mc263FeatureBlockState state(int blockX, int blockY, int blockZ) {
                return region.blockState(blockX, blockY, blockZ);
            }
        };
    }

    /** Live exact-state and bounded sidecar view for the step-eight spring family. */
    public Mc263SpringFeature.WorldAccess springWorld() {
        return new Mc263SpringFeature.WorldAccess() {
            @Override
            public String biomeKey(int blockX, int blockY, int blockZ) {
                return Mc263FeatureWorldAdapter.this.biomeKey(blockX, blockY, blockZ);
            }

            @Override
            public String blockState(int blockX, int blockY, int blockZ) {
                return Mc263FeatureWorldAdapter.this.blockState(blockX, blockY, blockZ);
            }

            @Override
            public boolean isEmptyBlock(int blockX, int blockY, int blockZ) {
                return region.blockState(blockX, blockY, blockZ).isAir();
            }

            @Override
            public boolean trySetBlockState(int blockX, int blockY, int blockZ,
                    String state, int flags) {
                if (flags != Mc263SpringFeature.UPDATE_FLAGS) {
                    throw new IllegalArgumentException(
                            "unsupported spring block flags: " + flags);
                }
                return region.setBlockState(blockX, blockY, blockZ, state);
            }

            @Override
            public void scheduleFluidTick(int blockX, int blockY, int blockZ,
                    String fluid, int delay) {
                if (delay != Mc263SpringFeature.FLUID_TICK_DELAY) {
                    throw new IllegalArgumentException(
                            "unsupported spring fluid-tick delay: " + delay);
                }
                region.scheduleFluidTick(blockX, blockY, blockZ, fluid, delay);
            }
        };
    }

    /** Atomic multiface state/support view for the step-nine glow-lichen leaf. */
    public Mc263GlowLichenFeature.WorldAccess glowLichenWorld() {
        return new Mc263GlowLichenFeature.WorldAccess() {
            @Override public String biomeKey(int x, int y, int z) {
                return populationBiomeKey(x, y, z);
            }
            @Override public int oceanFloorWg(int x, int z) {
                return region.oceanFloorWg(x, z);
            }
            @Override public Mc263GlowLichenFeature.State blockState(int x, int y, int z) {
                Mc263FeatureBlockState state = region.blockState(x, y, z);
                int faces = state.blockKey().equals(Mc263GlowLichenFeature.FEATURE)
                        ? multifaceMask(state.exactState()) : 0;
                return new Mc263GlowLichenFeature.State(state.blockKey(), faces,
                        state.fluidTypeKey(),
                        state.fluidKind() == Mc263FeatureBlockState.FluidKind.WATER_SOURCE,
                        state.isAir());
            }
            @Override public boolean canAttachTo(int x, int y, int z,
                    Mc263GlowLichenFeature.Direction face) {
                int neighbourX = x + directionX(face);
                int neighbourY = y + directionY(face);
                int neighbourZ = z + directionZ(face);
                return region.blockState(neighbourX, neighbourY, neighbourZ)
                        .isSupportOrCollisionFull(glowCarrierFace(face.opposite()));
            }
            @Override public boolean supportsState(Mc263GlowLichenFeature.State state) {
                return state != null
                        && Mc263FeatureBlockState.supportsExactState(state.canonical());
            }
            @Override public boolean trySetBlockState(int x, int y, int z,
                    Mc263GlowLichenFeature.State state, int flags) {
                if (flags != Mc263GlowLichenFeature.DIRECT_FLAGS
                        && flags != Mc263GlowLichenFeature.SPREAD_FLAGS) {
                    throw new IllegalArgumentException(
                            "unsupported glow-lichen block flags: " + flags);
                }
                return region.setBlockState(x, y, z, state.canonical());
            }
            @Override public boolean tryMarkPosForPostProcessing(int x, int y, int z) {
                return region.markForPostprocessing(x, y, z);
            }
        };
    }

    private static int multifaceMask(String exact) {
        int faces = 0;
        if (exact.contains("[down=true,")) faces |= 1;
        if (exact.contains(",up=true,")) faces |= 2;
        if (exact.contains(",north=true,")) faces |= 4;
        if (exact.contains(",south=true,")) faces |= 8;
        if (exact.contains(",west=true]")) faces |= 16;
        if (exact.contains(",east=true,")) faces |= 32;
        return faces;
    }

    private static Mc263FeatureBlockState.OcclusionFace glowCarrierFace(
            Mc263GlowLichenFeature.Direction face) {
        return Mc263FeatureBlockState.OcclusionFace.valueOf(face.name());
    }

    private static int directionX(Mc263GlowLichenFeature.Direction direction) {
        return direction == Mc263GlowLichenFeature.Direction.WEST ? -1
                : direction == Mc263GlowLichenFeature.Direction.EAST ? 1 : 0;
    }

    private static int directionY(Mc263GlowLichenFeature.Direction direction) {
        return direction == Mc263GlowLichenFeature.Direction.DOWN ? -1
                : direction == Mc263GlowLichenFeature.Direction.UP ? 1 : 0;
    }

    private static int directionZ(Mc263GlowLichenFeature.Direction direction) {
        return direction == Mc263GlowLichenFeature.Direction.NORTH ? -1
                : direction == Mc263GlowLichenFeature.Direction.SOUTH ? 1 : 0;
    }

    @Override
    public int minGenerationY() {
        return region.minGenerationY();
    }

    @Override
    public int generationDepth() {
        return region.generationDepth();
    }

    @Override
    public int seaLevel() {
        return SEA_LEVEL;
    }

    @Override
    public boolean isOutsideBuildHeight(int blockY) {
        return blockY < minGenerationY()
                || blockY >= minGenerationY() + generationDepth();
    }

    @Override
    public int worldSurfaceWg(int blockX, int blockZ) {
        return region.worldSurfaceWg(blockX, blockZ);
    }

    @Override
    public int oceanFloorWg(int blockX, int blockZ) {
        return region.oceanFloorWg(blockX, blockZ);
    }

    @Override
    public int oceanFloorWgHeight(int blockX, int blockZ) {
        return region.oceanFloorWg(blockX, blockZ);
    }

    @Override
    public int motionBlockingHeight(int blockX, int blockZ) {
        return region.motionBlocking(blockX, blockZ);
    }

    @Override
    public String biomeKey(int blockX, int blockY, int blockZ) {
        return populationBiomeKey(blockX, blockY, blockZ);
    }

    @Override
    public String blockState(int blockX, int blockY, int blockZ) {
        return region.blockState(blockX, blockY, blockZ).exactState();
    }

    @Override
    public String fluidState(int blockX, int blockY, int blockZ) {
        return region.blockState(blockX, blockY, blockZ).fluidTypeKey();
    }

    @Override
    public boolean ensureCanWrite(int blockX, int blockY, int blockZ) {
        return region.ensureCanWrite(blockX, blockY, blockZ);
    }

    @Override
    public void setBlockState(int blockX, int blockY, int blockZ, String state) {
        region.setBlockState(blockX, blockY, blockZ, state);
    }

    @Override
    public boolean trySetBlockState(int blockX, int blockY, int blockZ, String state) {
        return region.setBlockState(blockX, blockY, blockZ, state);
    }

    /** Exact desert-well write view; flags retain the official leaf contract at this boundary. */
    @Override
    public boolean trySetBlockState(int blockX, int blockY, int blockZ, String state, int flags) {
        if (flags != 2 && flags != 3) {
            throw new IllegalArgumentException("unsupported FEATURES block flags: " + flags);
        }
        return region.setBlockState(blockX, blockY, blockZ, state);
    }

    /** Retains the disk feature's ordered, duplicate-preserving post-processing side effect. */
    @Override
    public boolean tryMarkPosForPostProcessing(int blockX, int blockY, int blockZ) {
        return region.markForPostprocessing(blockX, blockY, blockZ);
    }

    @Override
    public boolean trySetOwnedBlockState(int blockX, int blockY, int blockZ, String state,
            long owner) {
        return region.trySetOwnedBlockState(blockX, blockY, blockZ, state, owner);
    }

    @Override
    public boolean isSolid(int blockX, int blockY, int blockZ) {
        return region.blockState(blockX, blockY, blockZ).isSolid();
    }

    @Override
    public boolean isSolidRender(int blockX, int blockY, int blockZ) {
        return region.blockState(blockX, blockY, blockZ).isSolidRender();
    }

    @Override
    public boolean isLiquid(int blockX, int blockY, int blockZ) {
        return region.blockState(blockX, blockY, blockZ).fluidKind()
                != Mc263FeatureBlockState.FluidKind.NONE;
    }

    @Override
    public boolean isExactSourceLava(int blockX, int blockY, int blockZ) {
        return region.blockState(blockX, blockY, blockZ).fluidKind()
                == Mc263FeatureBlockState.FluidKind.LAVA_SOURCE;
    }

    @Override
    public boolean isExactSourceWater(int blockX, int blockY, int blockZ) {
        return region.blockState(blockX, blockY, blockZ).fluidKind()
                == Mc263FeatureBlockState.FluidKind.WATER_SOURCE;
    }

    @Override
    public boolean hasWaterFluid(int blockX, int blockY, int blockZ) {
        Mc263FeatureBlockState.FluidKind fluid =
                region.blockState(blockX, blockY, blockZ).fluidKind();
        return fluid == Mc263FeatureBlockState.FluidKind.WATER_SOURCE
                || fluid == Mc263FeatureBlockState.FluidKind.WATER_FLOWING;
    }

    @Override
    public boolean shouldFreeze(int blockX, int blockY, int blockZ) {
        McBiomeRegistry.Biome biome = biome(biomeKey(blockX, blockY, blockZ));
        return McFreezeTopLayer.shouldFreezeWater(biome.temperature(),
                region.blockState(blockX, blockY, blockZ).blockId());
    }

    @Override
    public boolean hasPotentSulfurBlockEntity(int blockX, int blockY, int blockZ) {
        return region.hasPotentSulfurBlockEntity(blockX, blockY, blockZ);
    }

    @Override
    public boolean hasBrushableBlockEntity(int blockX, int blockY, int blockZ) {
        return region.hasBrushableBlockEntity(blockX, blockY, blockZ);
    }

    @Override
    public void setArchaeologyLoot(int blockX, int blockY, int blockZ, String lootTable,
            long lootSeed) {
        region.setArchaeologyLoot(blockX, blockY, blockZ, lootTable, lootSeed);
    }

    @Override
    public boolean isInBlockTag(int blockX, int blockY, int blockZ, String tagKey) {
        Mc263FeatureBlockState state = region.blockState(blockX, blockY, blockZ);
        return switch (tagKey) {
            case Mc263LakeFeature.AIR_TAG -> state.isAir();
            case Mc263LakeFeature.FEATURES_CANNOT_REPLACE_TAG ->
                    state.featuresCannotReplace();
            case Mc263LakeFeature.LAVA_POOL_STONE_CANNOT_REPLACE_TAG ->
                    state.lavaPoolStoneCannotReplace();
            default -> throw new IllegalArgumentException(
                    "unsupported FEATURES block tag: " + tagKey);
        };
    }

    @Override
    public boolean featuresCannotReplace(int blockX, int blockY, int blockZ) {
        return region.blockState(blockX, blockY, blockZ).featuresCannotReplace();
    }

    @Override
    public void scheduleBlockTick(int blockX, int blockY, int blockZ, String block, int delay) {
        region.scheduleBlockTick(blockX, blockY, blockZ, block, delay);
    }

    @Override
    public void markForPostprocessing(int blockX, int blockY, int blockZ) {
        region.markForPostprocessing(blockX, blockY, blockZ);
    }

    @Override
    public boolean hasRandomizableContainer(int blockX, int blockY, int blockZ) {
        return region.hasRandomizableContainer(blockX, blockY, blockZ);
    }

    @Override
    public void setChestLoot(int blockX, int blockY, int blockZ, String lootTable,
            long lootSeed) {
        LootProductionContext productionContext = productionContexts.locate(
                region, blockX, blockY, blockZ, lootTable);
        region.setChestLoot(blockX, blockY, blockZ, lootTable, lootSeed, productionContext);
    }

    @Override
    public boolean hasSpawnerBlockEntity(int blockX, int blockY, int blockZ) {
        return region.hasSpawnerBlockEntity(blockX, blockY, blockZ);
    }

    @Override
    public void setSpawnerMob(int blockX, int blockY, int blockZ, String entityType) {
        region.setSpawnerMob(blockX, blockY, blockZ, entityType);
    }

    private static McBiomeRegistry.Biome biome(String key) {
        McBiomeRegistry.Biome biome = BIOMES_BY_KEY.get(key);
        if (biome == null) {
            throw new IllegalArgumentException("unknown 26.3 FEATURES biome: " + key);
        }
        return biome;
    }

    private static Map<String, McBiomeRegistry.Biome> biomeCatalog() {
        Map<String, McBiomeRegistry.Biome> result = new HashMap<>();
        for (int biomeId : McBiomeRegistry.ids()) {
            McBiomeRegistry.Biome biome = McBiomeRegistry.get(biomeId);
            result.put(biome.name(), biome);
        }
        return Map.copyOf(result);
    }
}
