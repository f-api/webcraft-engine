package com.gameexpert.terrain;

import com.gameexpert.terrain.mc.feature.Mc263FeatureBlockState;
import com.gameexpert.terrain.mc.feature.Mc263FeaturesRegion;
import com.gameexpert.terrain.mc.feature.Mc263PostprocessResolver;
import com.gameexpert.terrain.mc.loot.Mc263ProductionContextCatalog;
import com.gameexpert.terrain.persistence.CanonicalWorldgenStore;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Production authority for the authenticated pinned-26.3 POST closure.
 *
 * <p>The resolver binds this context to the live canonical FEATURES region immediately before
 * POST. All center and seam-neighbour decisions therefore read the product carrier after FEATURES
 * and structure placement, rather than a caller-authored transcript. The only scheduling grammar
 * currently authenticated by the landed family receipts is source water at delay 5/NORMAL and
 * leaves at delay 1/NORMAL. Anything outside that closure fails closed.</p>
 *
 * <p>One unbound instance may be retained by a production chunk source. {@link #bind} creates the
 * run-scoped view, so consecutive chunk generation cannot leak carrier state.</p>
 */
public final class CanonicalPostprocessActivationContext
        implements Mc263PostprocessResolver.ActivationContext {
    static final String WATER = "minecraft:water";
    static final String LAVA = "minecraft:lava";
    static final String FARMLAND = "minecraft:farmland";
    static final String COCOA = "minecraft:cocoa";
    static final int WATER_DELAY = 5;
    static final int FARMLAND_DELAY = 1;
    /**
     * Official POST-EV-6 support class of {@code minecraft:seagrass}: the transcript row
     * {@code POST_SUPPORT_CLASS_EV6 21 DERIVED_16 minecraft:seagrass}.
     */
    private static final int SEAGRASS_SUPPORT_CLASS =
            Mc263PostprocessResolver.supportClassOrdinal("DERIVED_16");
    /**
     * Official POST-EV-6 support class of the kelp family: the transcript row
     * {@code POST_SUPPORT_CLASS_EV6 14 DERIVED_9 minecraft:kelp[age=0]}.
     *
     * <p>That column is the whole official {@code GrowingPlantBlock.canSurvive} predicate of the
     * kelp head and body — the up-face sturdiness of the block below together with the official
     * {@code canAttachTo} exclusion (the published {@code minecraft:magma_block} bit is {@code 0}
     * while {@code minecraft:stone} is {@code 1}) and the growth columns
     * ({@code minecraft:kelp_plant} and {@code minecraft:kelp[age=0]} are {@code 1}). The body
     * block shares that predicate with the head, so both kelp lanes read the same authenticated
     * ordinal instead of the five witness keys the {@code KELP_POST}/{@code KELP_HEAD_POST} rows
     * happened to publish.</p>
     */
    private static final int KELP_SUPPORT_CLASS =
            Mc263PostprocessResolver.supportClassOrdinal("DERIVED_9");
    /**
     * Exact transitive contents of the pinned {@code #minecraft:maintains_farmland} block tag:
     * the twelve direct members of {@code data/minecraft/tags/block/maintains_farmland.json}
     * plus the thirteen members of the {@code #minecraft:fence_gates} tag it includes.
     */
    private static final Set<String> MAINTAINS_FARMLAND = Set.of(
            "minecraft:pumpkin_stem", "minecraft:attached_pumpkin_stem",
            "minecraft:melon_stem", "minecraft:attached_melon_stem",
            "minecraft:beetroots", "minecraft:carrots", "minecraft:potatoes",
            "minecraft:torchflower_crop", "minecraft:torchflower",
            "minecraft:pitcher_crop", "minecraft:wheat", "minecraft:moving_piston",
            "minecraft:acacia_fence_gate", "minecraft:birch_fence_gate",
            "minecraft:dark_oak_fence_gate", "minecraft:pale_oak_fence_gate",
            "minecraft:jungle_fence_gate", "minecraft:oak_fence_gate",
            "minecraft:spruce_fence_gate", "minecraft:crimson_fence_gate",
            "minecraft:warped_fence_gate", "minecraft:mangrove_fence_gate",
            "minecraft:bamboo_fence_gate", "minecraft:cherry_fence_gate",
            "minecraft:poplar_fence_gate");
    static final int LAVA_DELAY = 30;
    static final int LEAVES_DELAY = 1;
    static final int NORMAL_PRIORITY = 0;

    private final long gameTimeMcTicks;
    private final Mc263FeaturesRegion carrier;

    /** Creates a reusable POST context; source attachment separately requires its catalog provider. */
    public CanonicalPostprocessActivationContext(long gameTimeMcTicks) {
        this(gameTimeMcTicks, null);
    }

    private CanonicalPostprocessActivationContext(long gameTimeMcTicks,
            Mc263FeaturesRegion carrier) {
        if (gameTimeMcTicks < 0) {
            throw new IllegalArgumentException(
                    "negative absolute Minecraft time: " + gameTimeMcTicks);
        }
        this.gameTimeMcTicks = gameTimeMcTicks;
        this.carrier = carrier;
    }

    /** Builds the production chunk seam with its mandatory authenticated catalog provider. */
    public CanonicalOriginChunkProductSource attachTo(CanonicalWorldgenStore store, long worldId,
            Mc263ProductionContextCatalog.Provider productionContextProvider) {
        return new CanonicalOriginChunkProductSource(store, worldId, this,
                Objects.requireNonNull(productionContextProvider,
                        "production context catalog provider"));
    }

    @Override
    public Mc263PostprocessResolver.ActivationContext bind(Mc263FeaturesRegion region) {
        return new CanonicalPostprocessActivationContext(gameTimeMcTicks,
                Objects.requireNonNull(region, "canonical FEATURES carrier"));
    }

    @Override public long gameTime() { return gameTimeMcTicks; }

    @Override public Mc263PostprocessResolver.MutableRandom random() {
        return bound -> {
            throw new IllegalStateException(
                    "authenticated POST closure attempted an unsupported random draw");
        };
    }

    @Override
    public Mc263FeatureBlockState readFull(Mc263PostprocessResolver.Position position) {
        Objects.requireNonNull(position, "FULL read position");
        Mc263FeaturesRegion live = requireBoundCarrier();
        Mc263FeatureBlockState state = live.blockState(
                position.x(), position.y(), position.z());
        return Mc263FeatureBlockState.fromExact(state.exactState());
    }

    @Override public Mc263PostprocessResolver.ForeignMutationSink foreignSink() {
        return mutations -> {
            List<Mc263PostprocessResolver.ForeignMutation> batch =
                    List.copyOf(Objects.requireNonNull(mutations, "foreign POST batch"));
            if (!batch.isEmpty()) {
                throw new IllegalStateException(
                        "authenticated POST closure produced a foreign mutation");
            }
        };
    }

    @Override public void tickContainedFluid(Mc263PostprocessResolver.MutableWorld world,
            Mc263PostprocessResolver.Position position, Mc263FeatureBlockState originalState) {
        Objects.requireNonNull(world, "POST world");
        Objects.requireNonNull(position, "POST fluid position");
        originalState = exact(originalState, "contained-fluid state");
        if (originalState.fluidKind() == Mc263FeatureBlockState.FluidKind.WATER_SOURCE) {
            world.scheduleFluidTick(position, WATER, WATER_DELAY, NORMAL_PRIORITY);
            return;
        }
        if (originalState.fluidKind() == Mc263FeatureBlockState.FluidKind.LAVA_SOURCE) {
            world.scheduleFluidTick(position, LAVA, LAVA_DELAY, NORMAL_PRIORITY);
            return;
        }
        // Authenticated POST-EV-16 contained-fluid lane: a flowing state carries a different
        // official fluid than its source, with its own published key and tick delay.
        Mc263PostprocessResolver.ContainedFluidAuthority contained =
                Mc263PostprocessResolver.containedFluidAuthority(originalState);
        if (contained != null) {
            world.scheduleFluidTick(position, contained.fluidKey(), contained.tickDelay(),
                    NORMAL_PRIORITY);
            return;
        }
        throw unsupported("contained fluid", originalState);
    }

    @Override public void tickLiquidBlock(Mc263PostprocessResolver.MutableWorld world,
            Mc263PostprocessResolver.Position position, Mc263FeatureBlockState originalState,
            Mc263PostprocessResolver.MutableRandom random) {
        Objects.requireNonNull(world, "POST world");
        Objects.requireNonNull(position, "POST liquid position");
        Objects.requireNonNull(random, "POST random");
        originalState = exact(originalState, "liquid-block state");
        boolean authenticatedWater = originalState.blockKey().equals(WATER)
                && originalState.fluidKind() == Mc263FeatureBlockState.FluidKind.WATER_SOURCE;
        boolean authenticatedLava = originalState.blockKey().equals(LAVA)
                && originalState.fluidKind() == Mc263FeatureBlockState.FluidKind.LAVA_SOURCE;
        if (!authenticatedWater && !authenticatedLava
                && Mc263PostprocessResolver.containedFluidAuthority(originalState) == null) {
            throw unsupported("liquid block", originalState);
        }
        // The contained-fluid call already emitted the authenticated first-winner schedule.
    }

    @Override public Mc263FeatureBlockState updateShape(
            Mc263PostprocessResolver.MutableWorld world,
            Mc263PostprocessResolver.Position position,
            Mc263FeatureBlockState currentState,
            Mc263PostprocessResolver.Direction direction,
            Mc263PostprocessResolver.Position neighborPosition,
            Mc263FeatureBlockState neighborState,
            Mc263PostprocessResolver.MutableRandom random) {
        Objects.requireNonNull(world, "POST world");
        Objects.requireNonNull(position, "POST shape position");
        Objects.requireNonNull(direction, "POST shape direction");
        Objects.requireNonNull(neighborPosition, "POST neighbor position");
        Objects.requireNonNull(random, "POST random");
        currentState = exact(currentState, "current shape state");
        neighborState = exact(neighborState, "neighbor shape state");

        Mc263PostprocessResolver.Ev10Authority waterlogged =
                Mc263PostprocessResolver.ev10Authority(currentState);
        if (waterlogged != null && waterlogged.isWaterlogged(currentState)
                && waterlogged.schedulesWaterTick(direction)) {
            // POST-EV-10 authenticates whether this exact pair/direction requests the water
            // tick. The resolver journal keeps the same first-winner tick identity already
            // requested by postProcessGeneration's contained-fluid pass.
            world.scheduleFluidTick(position, WATER, WATER_DELAY, NORMAL_PRIORITY);
        }

        if (currentState.isLeavesTag()) {
            if (currentState.fluidKind() == Mc263FeatureBlockState.FluidKind.WATER_SOURCE) {
                world.scheduleFluidTick(position, WATER, WATER_DELAY, NORMAL_PRIORITY);
            } else if (currentState.fluidKind() != Mc263FeatureBlockState.FluidKind.NONE) {
                throw unsupported("leaves fluid", currentState);
            }
            int ownDistance = leafDistance(currentState);
            int neighborDistance = neighborState.isLogsTag() ? 0
                    : neighborState.isLeavesTag() ? leafDistance(neighborState) : 7;
            int candidate = Math.min(7, neighborDistance + 1);
            if (candidate != 1 || ownDistance != candidate) {
                world.scheduleBlockTick(position, currentState.blockKey(),
                        LEAVES_DELAY, NORMAL_PRIORITY);
            }
            return currentState;
        }

        String key = currentState.blockKey();
        if (key.endsWith("_fence")) {
            return updateFence(currentState, direction, neighborState);
        }
        if (key.endsWith("_wall")) {
            return updateWall(world, position, currentState, direction, neighborState);
        }
        if (key.endsWith("_stairs")) {
            return updateStairs(world, position, currentState);
        }
        if (key.equals("minecraft:iron_bars") || key.endsWith("_glass_pane")) {
            return updatePane(currentState, direction, neighborState);
        }
        if (key.equals("minecraft:rail")) {
            requireAuthenticatedRail(currentState);
            if (direction == Mc263PostprocessResolver.Direction.DOWN) {
                neighborAuthority(neighborState).rigidRailSupport();
            }
            return currentState;
        }
        Mc263PostprocessResolver.Ev9Authority ev9 =
                Mc263PostprocessResolver.ev9Authority(currentState);
        if (ev9 != null) {
            return updateEv9(world, position, currentState, direction, neighborState, ev9);
        }
        Mc263PostprocessResolver.AttachmentAuthority attached =
                Mc263PostprocessResolver.attachmentAuthority(currentState);
        if (attached != null) {
            return updateAttachment(world, position, currentState, direction, neighborState,
                    attached);
        }
        Mc263PostprocessResolver.PairedHalfAuthority pairedHalf =
                Mc263PostprocessResolver.pairedHalfAuthority(currentState);
        if (pairedHalf != null) {
            return updatePairedHalf(world, position, currentState, direction, neighborState,
                    pairedHalf);
        }
        if (key.equals("minecraft:kelp_plant")) {
            return updateKelpPlant(world, position, currentState, direction, neighborState);
        }
        if (key.equals("minecraft:seagrass")) {
            return updateSeagrass(world, position, currentState);
        }
        if (key.equals(FARMLAND)) {
            return updateFarmland(world, position, currentState, direction);
        }
        Mc263PostprocessResolver.DoublePlantAuthority doublePlant =
                Mc263PostprocessResolver.doublePlantAuthority(currentState);
        if (doublePlant != null) {
            return updateDoublePlant(world, position, currentState, direction, neighborState,
                    doublePlant);
        }
        if (key.equals("minecraft:kelp")) {
            return updateKelpHead(world, position, currentState, direction, neighborState);
        }
        if (key.equals("minecraft:glow_lichen") || key.equals("minecraft:sculk_vein")) {
            return updateMultiface(currentState, direction, neighborState);
        }
        // Official CocoaBlock#updateShape: the pod survives only while the block it faces carries
        // the JUNGLE_LOGS tag, and the seam is otherwise the inherited identity with no scheduled
        // tick. The FEATURES lane already ports this closure
        // (Mc263FeatureWorldAdapter#treeUpdateShape); POST reached the same subject with no
        // published cocoa authority and answered a bare fail-closed error for every jungle
        // chunk that grew a cocoa pod.
        if (key.equals(COCOA)) {
            return updateCocoa(currentState, direction, neighborState);
        }

        // Authenticated POST-EV-7 lanes: the official world-reading vegetation closures. Each
        // one is a published table lookup, so an unpublished neighbourhood still fails closed.
        if (key.equals("minecraft:vine")) {
            return updateVine(world, position, currentState, direction);
        }
        if (key.equals("minecraft:pale_moss_carpet")) {
            return updateMossCarpet(world, position, currentState);
        }
        Mc263PostprocessResolver.Ev7Family family =
                Mc263PostprocessResolver.ev7Family(key);
        if (family != null) {
            return updateEv7Family(world, position, currentState, direction, family);
        }

        Mc263PostprocessResolver.Ev8Family ev8 =
                Mc263PostprocessResolver.ev8Family(currentState);
        if (ev8 != null) {
            return updateEv8Family(world, position, currentState, direction, random, ev8);
        }

        // Authenticated speleothem lane (POST-SPELEO-1): the official SpeleothemBlock closure
        // is a published verdict grid, so it resolves before the generic support lane.
        Mc263FeatureBlockState speleothem = com.gameexpert.terrain.mc.feature
                .Mc263SpeleothemShapeAuthority.updateShape(world, position, currentState,
                        direction);
        if (speleothem != null) return speleothem;

        Mc263PostprocessResolver.SupportAuthority support =
                Mc263PostprocessResolver.supportAuthority(currentState);
        if (support != null) {
            return updateSupported(world, position, currentState, direction, neighborState,
                    support);
        }
        // Authenticated POST-EV-15 seam identity lane: a registry-derived family proven to be
        // the identity on every seam over the whole candidate universe answers from its own
        // published row, before the shape-independent rule guesses on its behalf.
        Mc263PostprocessResolver.SeamIdentityAuthority identity =
                Mc263PostprocessResolver.seamIdentityAuthority(currentState);
        if (identity != null) {
            scheduleAttachment(world, position, identity.verdict().tickSpec(), currentState);
            return Mc263FeatureBlockState.fromExact(identity.verdict().resultState());
        }
        // Authenticated POST-EV-16 horizontal paired-half lane: the pairing axis is the
        // subject's own facing, so the seam that carries the transfer is published per state and
        // the partner it holds selects the published verdict.
        Mc263PostprocessResolver.PairedHorizontalAuthority paired =
                Mc263PostprocessResolver.pairedHorizontalAuthority(currentState);
        if (paired != null) {
            Mc263PostprocessResolver.Ev9Verdict verdict = direction == paired.pairedSeam()
                    ? paired.pairedVerdict(neighborState.exactState())
                    : paired.identity();
            scheduleAttachment(world, position, verdict.tickSpec(), currentState);
            return Mc263FeatureBlockState.fromExact(verdict.resultState());
        }
        // Authenticated POST-EV-16 cross-connection lane: a vertical seam is the identity and a
        // horizontal seam reads the published neighbour predicate, so nothing recomputes an
        // attachment rule here.
        // Authenticated POST-EV-18 seam-property lane: some seams answer one published state and
        // the rest rewrite one of the subject's own properties from the published neighbour
        // predicate, so production reads two tables instead of recomputing a wall rule.
        Mc263PostprocessResolver.SeamPropertyAuthority seamProperty =
                Mc263PostprocessResolver.seamPropertyAuthority(currentState);
        if (seamProperty != null) {
            boolean predicate = false;
            if (seamProperty.rewrites(direction)) {
                Mc263PostprocessResolver.SeamPropertyNeighbor published =
                        Mc263PostprocessResolver.seamPropertyNeighbor(neighborState);
                if (published == null) throw unsupported("seam-property neighbour", neighborState);
                predicate = published.predicate();
            }
            Mc263PostprocessResolver.Ev9Verdict verdict =
                    seamProperty.verdict(direction, predicate);
            scheduleAttachment(world, position, verdict.tickSpec(), currentState);
            return Mc263FeatureBlockState.fromExact(verdict.resultState());
        }
        Mc263PostprocessResolver.CrossConnectionAuthority cross =
                Mc263PostprocessResolver.crossConnectionAuthority(currentState);
        if (cross != null) {
            Mc263PostprocessResolver.Ev9Verdict verdict;
            if (direction == Mc263PostprocessResolver.Direction.UP
                    || direction == Mc263PostprocessResolver.Direction.DOWN) {
                verdict = cross.vertical();
            } else {
                Mc263PostprocessResolver.CrossConnectionNeighbor predicate =
                        Mc263PostprocessResolver.crossConnectionNeighbor(neighborState);
                if (predicate == null) throw unsupported("cross-connection neighbour", neighborState);
                verdict = cross.horizontalVerdict(direction, predicate.connects(direction));
            }
            scheduleAttachment(world, position, verdict.tickSpec(), currentState);
            return Mc263FeatureBlockState.fromExact(verdict.resultState());
        }
        if (shapeIndependent(currentState, waterlogged != null)
                || waterlogged != null && waterlogged.isIdentity(direction)
                || authenticatedFullCube(currentState)) return currentState;
        throw unsupported("updateShape", currentState);
    }

    private Mc263FeatureBlockState updateEv8Family(
            Mc263PostprocessResolver.MutableWorld world,
            Mc263PostprocessResolver.Position position, Mc263FeatureBlockState current,
            Mc263PostprocessResolver.Direction direction,
            Mc263PostprocessResolver.MutableRandom random,
            Mc263PostprocessResolver.Ev8Family family) {
        String scenario;
        int randomValue = 0;
        switch (family.label()) {
            case "CAVE_VINES", "CAVE_VINES_PLANT" -> {
                Mc263FeatureBlockState above = world.blockState(
                        relative(position, Mc263PostprocessResolver.Direction.UP));
                Mc263FeatureBlockState below = world.blockState(
                        relative(position, Mc263PostprocessResolver.Direction.DOWN));
                int support = caveVinesSupport(above) ? 1 : 0;
                int growth = below.blockKey().equals("minecraft:cave_vines") ? 1
                        : below.blockKey().equals("minecraft:cave_vines_plant") ? 2 : 0;
                scenario = "S" + support + "G" + growth;
                if (family.label().equals("CAVE_VINES_PLANT")) {
                    if (direction == Mc263PostprocessResolver.Direction.DOWN && growth == 0) {
                        randomValue = random.nextInt(25);
                    }
                    scenario += "R" + randomValue;
                }
            }
            case "MANGROVE_PROPAGULE" -> {
                Mc263FeatureBlockState above = world.blockState(
                        relative(position, Mc263PostprocessResolver.Direction.UP));
                scenario = "S" + bit(Mc263PostprocessResolver.ev7Fact(above,
                        "TAG_SUPPORTS_HANGING_MANGROVE_PROPAGULE"));
            }
            case "WHEAT", "CARROTS", "POTATOES", "BEETROOTS" -> {
                Mc263FeatureBlockState below = world.blockState(
                        relative(position, Mc263PostprocessResolver.Direction.DOWN));
                int support = bit(Mc263PostprocessResolver.ev7Fact(below,
                        "TAG_SUPPORTS_CROPS"));
                int light = bit(requireBoundCarrier().rawBrightness(
                        position.x(), position.y(), position.z(), 0) >= 8);
                scenario = "S" + support + "L" + light;
            }
            case "ATTACHED_MELON_STEM", "ATTACHED_PUMPKIN_STEM" -> {
                Mc263FeatureBlockState below = world.blockState(
                        relative(position, Mc263PostprocessResolver.Direction.DOWN));
                int support = bit(Mc263PostprocessResolver.ev7Fact(below,
                        "TAG_SUPPORTS_STEM_CROPS"));
                Mc263PostprocessResolver.Direction facing = parseHorizontal(
                        requireProperty(properties(current.exactState()), "facing", current));
                String fruit = family.label().equals("ATTACHED_MELON_STEM")
                        ? "minecraft:melon" : "minecraft:pumpkin";
                int present = bit(world.blockState(relative(position, facing))
                        .blockKey().equals(fruit));
                scenario = "S" + support + "F" + present;
            }
            case "BIG_DRIPLEAF" -> {
                Mc263FeatureBlockState below = world.blockState(
                        relative(position, Mc263PostprocessResolver.Direction.DOWN));
                boolean supported = below.blockKey().equals("minecraft:big_dripleaf")
                        || below.blockKey().equals("minecraft:big_dripleaf_stem")
                        || Mc263PostprocessResolver.ev7Fact(below,
                                "TAG_SUPPORTS_BIG_DRIPLEAF");
                boolean above = world.blockState(
                        relative(position, Mc263PostprocessResolver.Direction.UP))
                        .blockKey().equals("minecraft:big_dripleaf");
                scenario = "S" + bit(supported) + "A" + bit(above);
            }
            case "BIG_DRIPLEAF_STEM" -> {
                Mc263FeatureBlockState below = world.blockState(
                        relative(position, Mc263PostprocessResolver.Direction.DOWN));
                Mc263FeatureBlockState above = world.blockState(
                        relative(position, Mc263PostprocessResolver.Direction.UP));
                boolean supported = below.blockKey().equals("minecraft:big_dripleaf_stem")
                        || Mc263PostprocessResolver.ev7Fact(below,
                                "TAG_SUPPORTS_BIG_DRIPLEAF");
                boolean capped = above.blockKey().equals("minecraft:big_dripleaf_stem")
                        || above.blockKey().equals("minecraft:big_dripleaf");
                scenario = "S" + bit(supported) + "A" + bit(capped);
            }
            case "CACTUS" -> scenario = cactusScenario(world, position);
            default -> throw unsupported("POST-EV-8 family", current);
        }
        Mc263PostprocessResolver.Ev8Verdict verdict = Mc263PostprocessResolver.ev8Verdict(
                family.label(), current.exactState(), scenario, direction);
        requireRandomTranscript(verdict.randomSpec(), randomValue);
        scheduleEv8(world, position, verdict.tickSpec());
        return Mc263FeatureBlockState.fromExact(verdict.resultState());
    }

    private static boolean caveVinesSupport(Mc263FeatureBlockState above) {
        return above.blockKey().equals("minecraft:cave_vines")
                || above.blockKey().equals("minecraft:cave_vines_plant")
                || neighborAuthority(above).multifaceSupports(
                        Mc263PostprocessResolver.Direction.DOWN);
    }

    private static String cactusScenario(Mc263PostprocessResolver.MutableWorld world,
            Mc263PostprocessResolver.Position position) {
        for (Mc263PostprocessResolver.Direction direction : new Mc263PostprocessResolver.Direction[]{
                Mc263PostprocessResolver.Direction.WEST, Mc263PostprocessResolver.Direction.EAST,
                Mc263PostprocessResolver.Direction.NORTH, Mc263PostprocessResolver.Direction.SOUTH}) {
            Mc263FeatureBlockState neighbor = world.blockState(relative(position, direction));
            if (Mc263PostprocessResolver.ev7Fact(neighbor, "SOLID")) return "H_SOLID";
            if (Mc263PostprocessResolver.ev7Fact(neighbor, "LAVA_FLUID")) return "H_LAVA";
        }
        Mc263FeatureBlockState below = world.blockState(
                relative(position, Mc263PostprocessResolver.Direction.DOWN));
        if (!below.blockKey().equals("minecraft:cactus")
                && !Mc263PostprocessResolver.ev7Fact(below, "TAG_SUPPORTS_CACTUS")) {
            return "BELOW_BAD";
        }
        Mc263FeatureBlockState above = world.blockState(
                relative(position, Mc263PostprocessResolver.Direction.UP));
        if (Mc263PostprocessResolver.ev7Fact(above, "LIQUID")) return "ABOVE_LIQUID";
        return "VALID";
    }

    private static void requireRandomTranscript(String spec, int value) {
        String[] fields = spec.split("/", -1);
        if (fields.length != 3 || Integer.parseInt(fields[2]) != value) {
            throw new IllegalStateException("malformed POST-EV-8 RNG transcript: " + spec);
        }
        boolean draw = fields[0].equals("1") && fields[1].equals("25");
        boolean none = fields[0].equals("0") && fields[1].equals("-1");
        if (!draw && !none) {
            throw new IllegalStateException("unsupported POST-EV-8 RNG transcript: " + spec);
        }
    }

    private static void scheduleEv8(Mc263PostprocessResolver.MutableWorld world,
            Mc263PostprocessResolver.Position position, String spec) {
        String[] fields = spec.split("/", -1);
        if (fields.length != 4) {
            throw new IllegalStateException("malformed POST-EV-8 tick transcript: " + spec);
        }
        if (fields[0].equals("0") && fields[1].equals("-1")
                && fields[2].equals("-") && fields[3].equals("-")) return;
        if (!fields[0].equals("1") || !fields[2].equals("NORMAL")) {
            throw new IllegalStateException("unsupported POST-EV-8 tick transcript: " + spec);
        }
        int delay = Integer.parseInt(fields[1]);
        if (fields[3].equals(WATER)) {
            world.scheduleFluidTick(position, WATER, delay, NORMAL_PRIORITY);
        } else {
            world.scheduleBlockTick(position, fields[3], delay, NORMAL_PRIORITY);
        }
    }

    private static int bit(boolean value) { return value ? 1 : 0; }

    /**
     * Authenticated support lane for the plant/snow POST families. The official
     * {@code canSurvive} closure decides the DOWN seam and every other direction is the official
     * identity; both verdicts are transcribed from the published POST transcript, so an
     * unauthenticated combination fails closed instead of guessing a vanilla rule.
     */
    private static Mc263FeatureBlockState updateSupported(
            Mc263PostprocessResolver.MutableWorld world,
            Mc263PostprocessResolver.Position position, Mc263FeatureBlockState current,
            Mc263PostprocessResolver.Direction direction, Mc263FeatureBlockState neighbor,
            Mc263PostprocessResolver.SupportAuthority support) {
        Mc263FeatureBlockState below = direction == Mc263PostprocessResolver.Direction.DOWN
                ? neighbor
                : world.blockState(relative(position, Mc263PostprocessResolver.Direction.DOWN));
        boolean survives = support.survivesOn(below);
        if (direction == Mc263PostprocessResolver.Direction.DOWN) {
            schedule(world, position, support.downTick(survives));
            return Mc263FeatureBlockState.fromExact(survives
                    ? support.supportedResult() : support.removedResult());
        }
        // POST-EV-6 double plants: without a support every seam collapses to the removed
        // verdict; with one, the paired-half seam is its own authenticated verdict and every
        // remaining seam is the official identity.
        if (support.pairedState() != null) {
            schedule(world, position, support.seamTick(survives));
            if (!survives) return Mc263FeatureBlockState.fromExact(support.removedResult());
            if (direction == Mc263PostprocessResolver.Direction.UP) {
                return Mc263FeatureBlockState.fromExact(
                        neighbor.exactState().equals(support.pairedState())
                                ? support.pairMatchResult() : support.pairMismatchResult());
            }
            return current;
        }
        if (support.seamMirrorsSupport()) {
            schedule(world, position, support.seamTick(survives));
            return Mc263FeatureBlockState.fromExact(survives
                    ? support.supportedResult() : support.removedResult());
        }
        if (!support.nonDownIdentity()) throw unsupported("support-lane seam", current);
        return current;
    }

    /** Emits one authenticated POST block tick, or nothing when the receipt schedules none. */
    private static void schedule(Mc263PostprocessResolver.MutableWorld world,
            Mc263PostprocessResolver.Position position,
            Mc263PostprocessResolver.SupportAuthority.Tick tick) {
        if (tick == null) return;
        if (!tick.priority().equals("NORMAL")) {
            throw new IllegalStateException(
                    "no authenticated POST tick priority: " + tick.priority());
        }
        world.scheduleBlockTick(position, tick.key(), tick.delay(), NORMAL_PRIORITY);
    }

    private static Mc263FeatureBlockState updateFence(Mc263FeatureBlockState current,
            Mc263PostprocessResolver.Direction direction, Mc263FeatureBlockState neighbor) {
        String side = horizontal(direction);
        if (side == null) {
            return current;
        }
        return withProperty(current, side, Boolean.toString(
                neighborAuthority(neighbor).fenceConnects(opposite(direction))));
    }

    private static void requireAuthenticatedRail(Mc263FeatureBlockState state) {
        if (!authenticatedRail(state)) throw unsupported("rail updateShape", state);
    }

    private static boolean authenticatedRail(Mc263FeatureBlockState state) {
        if (!state.blockKey().equals("minecraft:rail")) return false;
        Map<String, String> values = properties(state.exactState());
        if (values.size() != 2 || !"false".equals(values.get("waterlogged"))) return false;
        String shape = values.get("shape");
        return shape != null && switch (shape) {
            case "north_south", "east_west", "ascending_east", "ascending_west",
                    "ascending_north", "ascending_south", "south_east", "south_west",
                    "north_west", "north_east" -> true;
            default -> false;
        };
    }

    private static Mc263FeatureBlockState updateEv9(
            Mc263PostprocessResolver.MutableWorld world,
            Mc263PostprocessResolver.Position position, Mc263FeatureBlockState current,
            Mc263PostprocessResolver.Direction direction, Mc263FeatureBlockState neighbor,
            Mc263PostprocessResolver.Ev9Authority authority) {
        Mc263FeatureBlockState support = direction == authority.supportDirection()
                ? neighbor : world.blockState(relative(position, authority.supportDirection()));
        boolean survives = Mc263PostprocessResolver.ev9Supports(support, authority.ordinal());
        Mc263PostprocessResolver.Ev9Verdict verdict = authority.verdict(direction, survives);
        scheduleEv7(world, position, verdict.tickSpec(), current);
        return Mc263FeatureBlockState.fromExact(verdict.resultState());
    }

    /**
     * Attached-family lane: the coral wall fans (POST-EV-12) and the amethyst buds and clusters
     * (POST-EV-13).
     *
     * <p>The official {@code updateShape} of an attached family clears the block on its
     * attachment seam alone and only when the official {@code canSurvive} fails; every other seam
     * is the official identity. The published subject row carries the probed attachment direction
     * and, per seam, the result state and scheduled tick for both verdicts, so no branch is
     * reconstructed here.</p>
     *
     * <p>The predicate is the published {@code POST_NEIGHBOR} sturdy-face bit of the attached
     * neighbour at the subject's own facing: each generation executes the official
     * {@code canSurvive} over the whole registry-plus-catalog candidate universe and fails closed
     * unless it agrees with that column for every candidate, so production reads one pinned table
     * instead of a second copy of the same bits.</p>
     */
    private static Mc263FeatureBlockState updateAttachment(
            Mc263PostprocessResolver.MutableWorld world,
            Mc263PostprocessResolver.Position position, Mc263FeatureBlockState current,
            Mc263PostprocessResolver.Direction direction, Mc263FeatureBlockState neighbor,
            Mc263PostprocessResolver.AttachmentAuthority authority) {
        Mc263FeatureBlockState support = direction == authority.supportDirection()
                ? neighbor : world.blockState(relative(position, authority.supportDirection()));
        boolean survives = neighborAuthority(support)
                .faceSturdy(opposite(authority.supportDirection()));
        Mc263PostprocessResolver.Ev9Verdict verdict = authority.verdict(direction, survives);
        scheduleAttachment(world, position, verdict.tickSpec(), current);
        return Mc263FeatureBlockState.fromExact(verdict.resultState());
    }

    /**
     * Official POST-EV-14 paired-half step.
     *
     * <p>The paired seam is answered first: a neighbour that is a published family state of the
     * opposite half transfers its own state onto this half -- the published twin ordinal of that
     * neighbour -- and anything else collapses to the published mismatch verdict. For a lower
     * half the DOWN seam is the official {@code canSurvive} verdict, read from the same pinned
     * sturdy-face column the attachment lane uses. Every remaining seam is the published
     * identity.</p>
     */
    private static Mc263FeatureBlockState updatePairedHalf(
            Mc263PostprocessResolver.MutableWorld world,
            Mc263PostprocessResolver.Position position, Mc263FeatureBlockState current,
            Mc263PostprocessResolver.Direction direction, Mc263FeatureBlockState neighbor,
            Mc263PostprocessResolver.PairedHalfAuthority authority) {
        if (direction == authority.pairedSeam()) {
            Mc263PostprocessResolver.PairedHalfAuthority paired =
                    Mc263PostprocessResolver.pairedHalfNeighbour(neighbor);
            if (paired != null && !paired.half().equals(authority.half())) {
                scheduleAttachment(world, position, authority.transferTick(), current);
                return Mc263FeatureBlockState.fromExact(
                        Mc263PostprocessResolver.pairedHalfState(paired.twinOrdinal()));
            }
            scheduleAttachment(world, position, authority.pairMismatch().tickSpec(), current);
            return Mc263FeatureBlockState.fromExact(authority.pairMismatch().resultState());
        }
        if (direction == Mc263PostprocessResolver.Direction.DOWN) {
            boolean survives = neighborAuthority(neighbor)
                    .faceSturdy(Mc263PostprocessResolver.Direction.UP);
            Mc263PostprocessResolver.Ev9Verdict verdict = authority.downVerdict(survives);
            scheduleAttachment(world, position, verdict.tickSpec(), current);
            return Mc263FeatureBlockState.fromExact(verdict.resultState());
        }
        scheduleAttachment(world, position, authority.identityTick(), current);
        return current;
    }

    /**
     * Emits the published attachment seam tick. The official lane may request one identical tick
     * more than once on a seam -- the waterlogged decorator and the block rule both ask for the
     * source-water tick -- and the resolver journal keeps the first winner, so a repeated
     * identical schedule is emitted once. Only the authenticated grammars are representable:
     * source water at delay {@value #WATER_DELAY} and a block tick on the subject's own block.
     */
    private static void scheduleAttachment(Mc263PostprocessResolver.MutableWorld world,
            Mc263PostprocessResolver.Position position, String spec,
            Mc263FeatureBlockState current) {
        if (spec.equals("0/-1/-/-")) return;
        String[] fields = spec.split("/", -1);
        if (fields.length != 4 || !fields[2].equals("NORMAL")
                || Integer.parseInt(fields[0]) < 1) {
            throw unsupported("attachment seam tick grammar", current);
        }
        int delay = Integer.parseInt(fields[1]);
        if (fields[3].equals(WATER)) {
            if (delay != WATER_DELAY) {
                throw unsupported("attachment seam water tick delay", current);
            }
            world.scheduleFluidTick(position, WATER, WATER_DELAY, NORMAL_PRIORITY);
            return;
        }
        if (!fields[3].equals(current.blockKey())) {
            throw unsupported("attachment seam tick target", current);
        }
        world.scheduleBlockTick(position, fields[3], delay, NORMAL_PRIORITY);
    }

    private static Mc263FeatureBlockState updateKelpPlant(
            Mc263PostprocessResolver.MutableWorld world,
            Mc263PostprocessResolver.Position position, Mc263FeatureBlockState current,
            Mc263PostprocessResolver.Direction direction, Mc263FeatureBlockState neighbor) {
        world.scheduleFluidTick(position, WATER, WATER_DELAY, NORMAL_PRIORITY);
        if (direction == Mc263PostprocessResolver.Direction.DOWN
                && !Mc263PostprocessResolver.supportClassSurvives(neighbor, KELP_SUPPORT_CLASS)) {
            // Published KELP_POST ocean_kelp_body_support_removed branch: the subject state is
            // the identity and the official updateShape schedules one delay-1/NORMAL block tick.
            world.scheduleBlockTick(position, current.blockKey(), 1, NORMAL_PRIORITY);
        }
        if (direction == Mc263PostprocessResolver.Direction.UP
                && !neighbor.blockKey().equals("minecraft:kelp_plant")
                && !neighbor.blockKey().equals("minecraft:kelp")) {
            throw unsupported("kelp head conversion", neighbor);
        }
        return current;
    }

    /**
     * Seagrass lane (POST-EV-6 class {@code DERIVED_16}).
     *
     * <p>The official {@code SeagrassBlock} survives wherever its published {@code canSurvive}
     * column says so — the pinned transcript authenticates that column for every support state
     * it published, and the connection-shape closure answers the rest of the exact catalog. The
     * result states and the delay-5 water tick are the published
     * {@code POST_PLANT_EV6 minecraft:seagrass} row, whose seam grammar is uniform, so every
     * seam reproduces the DOWN verdict.</p>
     */
    private static Mc263FeatureBlockState updateSeagrass(
            Mc263PostprocessResolver.MutableWorld world,
            Mc263PostprocessResolver.Position position, Mc263FeatureBlockState current) {
        Mc263FeatureBlockState support = world.blockState(
                relative(position, Mc263PostprocessResolver.Direction.DOWN));
        if (Mc263PostprocessResolver.supportClassSurvives(support, SEAGRASS_SUPPORT_CLASS)) {
            world.scheduleFluidTick(position, WATER, WATER_DELAY, NORMAL_PRIORITY);
            return current;
        }
        return Mc263FeatureBlockState.fromExact("minecraft:air");
    }

    /**
     * Farmland lane.
     *
     * <p>The pinned 26.3 {@code net/minecraft/world/level/block/FarmlandBlock.updateShape}
     * returns the official {@code BlockBehaviour.updateShape} identity for every seam and, on the
     * UP seam alone, schedules a {@code minecraft:farmland} block tick at delay 1/NORMAL when
     * {@code canSurvive} is false. That predicate is
     * {@code !above.isSolid() || above.is(#minecraft:maintains_farmland)} and reads the block
     * above through the level, so the whole lane is a function of the above-state and closes over
     * all eight authenticated {@code moisture} states and every catalog neighbour.</p>
     */
    private static Mc263FeatureBlockState updateFarmland(
            Mc263PostprocessResolver.MutableWorld world,
            Mc263PostprocessResolver.Position position, Mc263FeatureBlockState current,
            Mc263PostprocessResolver.Direction direction) {
        String moisture = requireProperty(properties(current.exactState()), "moisture", current);
        if (properties(current.exactState()).size() != 1 || moisture.length() != 1
                || moisture.charAt(0) < '0' || moisture.charAt(0) > '7') {
            throw unsupported("farmland moisture", current);
        }
        if (direction == Mc263PostprocessResolver.Direction.UP) {
            Mc263FeatureBlockState above = world.blockState(
                    relative(position, Mc263PostprocessResolver.Direction.UP));
            if (above.isSolid() && !MAINTAINS_FARMLAND.contains(above.blockKey())) {
                world.scheduleBlockTick(position, FARMLAND, FARMLAND_DELAY, NORMAL_PRIORITY);
            }
        }
        return current;
    }

    /**
     * Official POST-EV-15 double-plant step.
     *
     * <p>The seam that points at this subject's own other half clears to the published mismatch
     * verdict unless it holds the published paired state. Every other seam -- and that seam when
     * the paired state is there -- is the published supported/removed verdict of the official
     * {@code canSurvive} predicate, read from the pinned POST-EV-6 support class the subject
     * shares. The DOWN neighbour is the block below, so that seam reads it directly instead of
     * asking the world for a state the caller already handed over.</p>
     */
    private static Mc263FeatureBlockState updateDoublePlant(
            Mc263PostprocessResolver.MutableWorld world,
            Mc263PostprocessResolver.Position position, Mc263FeatureBlockState current,
            Mc263PostprocessResolver.Direction direction, Mc263FeatureBlockState neighbor,
            Mc263PostprocessResolver.DoublePlantAuthority authority) {
        if (direction == authority.pairedSeam()
                && !neighbor.exactState().equals(authority.pairedState())) {
            scheduleAttachment(world, position, authority.pairMismatch().tickSpec(), current);
            return Mc263FeatureBlockState.fromExact(authority.pairMismatch().resultState());
        }
        Mc263FeatureBlockState below = direction == Mc263PostprocessResolver.Direction.DOWN
                ? neighbor
                : world.blockState(relative(position, Mc263PostprocessResolver.Direction.DOWN));
        Mc263PostprocessResolver.Ev9Verdict verdict = authority.supportVerdict(
                Mc263PostprocessResolver.supportClassSurvives(below, authority.supportClass()));
        scheduleAttachment(world, position, verdict.tickSpec(), current);
        return Mc263FeatureBlockState.fromExact(verdict.resultState());
    }

    private static Mc263FeatureBlockState updateKelpHead(
            Mc263PostprocessResolver.MutableWorld world,
            Mc263PostprocessResolver.Position position, Mc263FeatureBlockState current,
            Mc263PostprocessResolver.Direction direction, Mc263FeatureBlockState neighbor) {
        world.scheduleFluidTick(position, WATER, WATER_DELAY, NORMAL_PRIORITY);
        if (direction == Mc263PostprocessResolver.Direction.DOWN
                && !Mc263PostprocessResolver.supportClassSurvives(neighbor, KELP_SUPPORT_CLASS)) {
            // Published KELP_HEAD_POST ocean_kelp_head_age_*_support_removed branch.
            world.scheduleBlockTick(position, current.blockKey(), 1, NORMAL_PRIORITY);
        }
        if (direction == Mc263PostprocessResolver.Direction.UP) {
            if (neighbor.blockKey().equals(WATER)
                    && neighbor.fluidKind() == Mc263FeatureBlockState.FluidKind.WATER_SOURCE) {
                return current;
            }
            throw unsupported("kelp head growth neighbor", neighbor);
        }
        return current;
    }

    private static Mc263FeatureBlockState updateWall(
            Mc263PostprocessResolver.MutableWorld world,
            Mc263PostprocessResolver.Position position, Mc263FeatureBlockState current,
            Mc263PostprocessResolver.Direction direction, Mc263FeatureBlockState neighbor) {
        if (direction == Mc263PostprocessResolver.Direction.DOWN) return current;
        Map<String, String> values = new java.util.TreeMap<>(properties(current.exactState()));
        String changedSide = horizontal(direction);
        if (changedSide != null) {
            values.put(changedSide, neighborAuthority(neighbor)
                    .wallConnects(opposite(direction)) ? "low" : "none");
        }
        Mc263FeatureBlockState above = direction == Mc263PostprocessResolver.Direction.UP
                ? neighbor : world.blockState(relative(position,
                        Mc263PostprocessResolver.Direction.UP));
        Mc263PostprocessResolver.NeighborAuthority aboveAuthority = neighborAuthority(above);
        for (Mc263PostprocessResolver.Direction side : new Mc263PostprocessResolver.Direction[]{
                Mc263PostprocessResolver.Direction.WEST,
                Mc263PostprocessResolver.Direction.EAST,
                Mc263PostprocessResolver.Direction.NORTH,
                Mc263PostprocessResolver.Direction.SOUTH}) {
            String property = horizontal(side);
            if (!"none".equals(values.get(property))) {
                values.put(property, aboveAuthority.wallTall(side) ? "tall" : "low");
            }
        }
        boolean up = wallRaisesPost(values, above, aboveAuthority);
        values.put("up", Boolean.toString(up));
        return withProperties(current, values);
    }

    private static boolean wallRaisesPost(Map<String, String> sides,
            Mc263FeatureBlockState above,
            Mc263PostprocessResolver.NeighborAuthority aboveAuthority) {
        Map<String, String> aboveValues = properties(above.exactState());
        if (above.blockKey().endsWith("_wall")
                && "true".equals(aboveValues.get("up"))) return true;
        boolean northNone = "none".equals(sides.get("north"));
        boolean southNone = "none".equals(sides.get("south"));
        boolean eastNone = "none".equals(sides.get("east"));
        boolean westNone = "none".equals(sides.get("west"));
        if (northNone && southNone && eastNone && westNone
                || northNone != southNone || eastNone != westNone) return true;
        if ("tall".equals(sides.get("north")) && "tall".equals(sides.get("south"))
                || "tall".equals(sides.get("east"))
                        && "tall".equals(sides.get("west"))) return false;
        return aboveAuthority.wallPostOverride() || aboveAuthority.wallPostCovered();
    }

    private static Mc263FeatureBlockState updatePane(Mc263FeatureBlockState current,
            Mc263PostprocessResolver.Direction direction, Mc263FeatureBlockState neighbor) {
        String side = horizontal(direction);
        if (side == null) return current;
        return withProperty(current, side, Boolean.toString(
                neighborAuthority(neighbor).paneConnects(opposite(direction))));
    }

    private static Mc263FeatureBlockState updateStairs(
            Mc263PostprocessResolver.MutableWorld world,
            Mc263PostprocessResolver.Position position, Mc263FeatureBlockState current) {
        Map<String, String> own = properties(current.exactState());
        Mc263PostprocessResolver.NeighborAuthority ownAuthority = neighborAuthority(current);
        if (!ownAuthority.isStairs()) throw unsupported("stairs input", current);
        Mc263PostprocessResolver.Direction facing = parseHorizontal(
                ownAuthority.stairFacing());
        String half = ownAuthority.stairHalf();
        Mc263FeatureBlockState front = world.blockState(relative(position, facing));
        String shape = "straight";
        if (sameHalfStairs(front, half)) {
            Mc263PostprocessResolver.Direction frontFacing = parseHorizontal(
                    properties(front.exactState()).get("facing"));
            if (axis(frontFacing) != axis(facing)
                    && canTakeStairShape(world, position, current, frontFacing)) {
                shape = frontFacing == counterClockwise(facing) ? "outer_left" : "outer_right";
            }
        }
        if (shape.equals("straight")) {
            Mc263FeatureBlockState back = world.blockState(relative(position, opposite(facing)));
            if (sameHalfStairs(back, half)) {
                Mc263PostprocessResolver.Direction backFacing = parseHorizontal(
                        properties(back.exactState()).get("facing"));
                if (axis(backFacing) != axis(facing)
                        && canTakeStairShape(world, position, current, backFacing)) {
                    shape = backFacing == counterClockwise(facing)
                            ? "inner_left" : "inner_right";
                }
            }
        }
        return withProperty(current, "shape", shape);
    }

    private static boolean canTakeStairShape(Mc263PostprocessResolver.MutableWorld world,
            Mc263PostprocessResolver.Position position, Mc263FeatureBlockState current,
            Mc263PostprocessResolver.Direction side) {
        Mc263FeatureBlockState neighbor = world.blockState(relative(position, side));
        Mc263PostprocessResolver.NeighborAuthority other = neighborAuthority(neighbor);
        if (!other.isStairs()) return true;
        Mc263PostprocessResolver.NeighborAuthority own = neighborAuthority(current);
        return !Objects.equals(own.stairFacing(), other.stairFacing())
                || !Objects.equals(own.stairHalf(), other.stairHalf());
    }

    private static boolean sameHalfStairs(Mc263FeatureBlockState state, String half) {
        Mc263PostprocessResolver.NeighborAuthority authority = neighborAuthority(state);
        return authority.isStairs() && Objects.equals(authority.stairHalf(), half);
    }

    private static Mc263FeatureBlockState updateMultiface(Mc263FeatureBlockState current,
            Mc263PostprocessResolver.Direction direction, Mc263FeatureBlockState neighbor) {
        Map<String, String> values = properties(current.exactState());
        String face = direction.name().toLowerCase(java.util.Locale.ROOT);
        if (!"true".equals(values.get(face))) return current;
        if (neighborAuthority(neighbor).multifaceSupports(opposite(direction))) return current;
        Mc263FeatureBlockState without = withProperty(current, face, "false");
        Map<String, String> updated = properties(without.exactState());
        for (Mc263PostprocessResolver.Direction candidate
                : Mc263PostprocessResolver.Direction.values()) {
            if ("true".equals(updated.get(candidate.name().toLowerCase(java.util.Locale.ROOT)))) {
                return without;
            }
        }
        return Mc263FeatureBlockState.fromExact("minecraft:air");
    }

    /**
     * {@code CocoaBlock#updateShape}. {@code canSurvive} reads exactly one block -- the one the
     * pod faces -- so the closure needs no world access: the seam that carries the update is the
     * facing seam, and its neighbour is that block.
     */
    private static Mc263FeatureBlockState updateCocoa(Mc263FeatureBlockState current,
            Mc263PostprocessResolver.Direction direction, Mc263FeatureBlockState neighbor) {
        // Reject a missing or noncanonical facing instead of treating it as a non-matching seam.
        Mc263PostprocessResolver.Direction facing = parseHorizontal(
                requireProperty(properties(current.exactState()), "facing", current));
        if (facing != direction || neighbor.isJungleLogsTag()) return current;
        return Mc263FeatureBlockState.fromExact("minecraft:air");
    }

    /** Official vine face order of the published POST-EV-7 table. */
    private static final String[] VINE_FACES = {"up", "north", "east", "south", "west"};
    /** Official moss-carpet side order of the published POST-EV-7 state key. */
    private static final String[] MOSS_SIDES = {"west", "east", "north", "south"};

    /**
     * Vine lane (POST-EV-7). The official {@code VineBlock.updateShape} returns the identity on
     * the DOWN seam and otherwise rewrites every face independently from the neighbour on that
     * face and the vine above; both facts are proved exhaustively by the pinned oracle before
     * the compact table is published, and every face verdict here is one published row.
     */
    private static Mc263FeatureBlockState updateVine(
            Mc263PostprocessResolver.MutableWorld world,
            Mc263PostprocessResolver.Position position, Mc263FeatureBlockState current,
            Mc263PostprocessResolver.Direction direction) {
        if (direction == Mc263PostprocessResolver.Direction.DOWN) return current;
        Mc263FeatureBlockState above = world.blockState(
                relative(position, Mc263PostprocessResolver.Direction.UP));
        boolean aboveIsVine = above.blockKey().equals("minecraft:vine");
        Map<String, String> values = properties(current.exactState());
        Map<String, String> aboveValues = aboveIsVine
                ? properties(above.exactState()) : Map.of();
        StringBuilder faces = new StringBuilder();
        for (String face : VINE_FACES) {
            boolean own = requireProperty(values, face, current).equals("true");
            Mc263PostprocessResolver.Direction seam = face.equals("up")
                    ? Mc263PostprocessResolver.Direction.UP : parseHorizontal(face);
            Mc263PostprocessResolver.Direction attach = face.equals("up")
                    ? Mc263PostprocessResolver.Direction.DOWN : seam;
            boolean acceptable = neighborAuthority(world.blockState(relative(position, seam)))
                    .multifaceSupports(attach);
            boolean aboveFace = aboveIsVine
                    && "true".equals(aboveValues.get(face));
            String verdict = Mc263PostprocessResolver.ev7Table("V", face, own ? "1" : "0",
                    acceptable ? "1" : "0", aboveFace ? "1" : "0");
            if (verdict == null) throw unsupported("vine face", current);
            faces.append(verdict);
        }
        String result = Mc263PostprocessResolver.ev7Table("VS", faces.toString());
        if (result == null) throw unsupported("vine collapse", current);
        return Mc263FeatureBlockState.fromExact(result);
    }

    /**
     * Pale-moss-carpet lane (POST-EV-7). The official {@code MossyCarpetBlock.updateShape}
     * ignores the changed direction: it recomputes {@code canSurvive} from the block below and
     * then one side per horizontal face from that face's neighbour, the carpet above and the
     * carpet below. Every verdict is a published row and no rule is reconstructed here.
     */
    private static Mc263FeatureBlockState updateMossCarpet(
            Mc263PostprocessResolver.MutableWorld world,
            Mc263PostprocessResolver.Position position, Mc263FeatureBlockState current) {
        Mc263FeatureBlockState below = world.blockState(
                relative(position, Mc263PostprocessResolver.Direction.DOWN));
        Mc263FeatureBlockState above = world.blockState(
                relative(position, Mc263PostprocessResolver.Direction.UP));
        Map<String, String> values = properties(current.exactState());
        String base = requireProperty(values, "bottom", current).equals("true") ? "1" : "0";
        String survives = Mc263PostprocessResolver.ev7Table("MS", base,
                mossSurviveClass(below));
        if (survives == null) throw unsupported("moss carpet survival", current);
        StringBuilder key = new StringBuilder(base);
        if (survives.equals("1")) {
            for (String side : MOSS_SIDES) {
                Mc263PostprocessResolver.Direction seam = parseHorizontal(side);
                boolean support = neighborAuthority(world.blockState(relative(position, seam)))
                        .multifaceSupports(seam);
                String verdict = Mc263PostprocessResolver.ev7Table("MD", side, base,
                        mossSideCode(requireProperty(values, side, current)),
                        support ? "1" : "0", mossAboveClass(above, side),
                        mossBelowClass(below, side));
                if (verdict == null) throw unsupported("moss carpet side", current);
                key.append(verdict);
            }
        } else {
            key.setLength(0);
            key.append("0nnnn");
        }
        String result = Mc263PostprocessResolver.ev7Table("MT", key.toString());
        if (result == null) throw unsupported("moss carpet collapse", current);
        return Mc263FeatureBlockState.fromExact(result);
    }

    private static String mossSideCode(String value) {
        return switch (value) {
            case "none" -> "n"; case "low" -> "l"; case "tall" -> "t";
            default -> throw new IllegalStateException(
                    "no authenticated POST moss carpet side: " + value);
        };
    }

    private static boolean isMossCarpet(Mc263FeatureBlockState state) {
        return state.blockKey().equals("minecraft:pale_moss_carpet");
    }

    private static boolean mossBase(Mc263FeatureBlockState state) {
        return "true".equals(properties(state.exactState()).get("bottom"));
    }

    private static String mossSurviveClass(Mc263FeatureBlockState below) {
        if (Mc263PostprocessResolver.ev7Fact(below, "AIR")) return "AIR";
        if (!isMossCarpet(below)) return "OTHER";
        return mossBase(below) ? "CARPET_BASE" : "CARPET_NOBASE";
    }

    private static String mossAboveClass(Mc263FeatureBlockState above, String side) {
        if (!isMossCarpet(above)) return "NON_CARPET";
        boolean set = !"none".equals(properties(above.exactState()).get(side));
        return mossBase(above) ? (set ? "BASE_SET" : "BASE_NONE")
                : (set ? "NOBASE_SET" : "NOBASE_NONE");
    }

    private static String mossBelowClass(Mc263FeatureBlockState below, String side) {
        if (!isMossCarpet(below)) return "NON_CARPET";
        return "none".equals(properties(below.exactState()).get(side))
                ? "SIDE_NONE" : "SIDE_SET";
    }

    /**
     * POST-EV-7 support families: one official {@code canSurvive} predicate over one neighbour,
     * proved against the official runtime over every catalog candidate, plus the published seam
     * verdict and its scheduled tick.
     */
    private static Mc263FeatureBlockState updateEv7Family(
            Mc263PostprocessResolver.MutableWorld world,
            Mc263PostprocessResolver.Position position, Mc263FeatureBlockState current,
            Mc263PostprocessResolver.Direction direction,
            Mc263PostprocessResolver.Ev7Family family) {
        Map<String, String> values = properties(current.exactState());
        Mc263PostprocessResolver.Direction support = switch (family.supportSpec()) {
            case "up" -> Mc263PostprocessResolver.Direction.UP;
            case "down" -> Mc263PostprocessResolver.Direction.DOWN;
            case "facing_opposite" -> opposite(
                    parseHorizontal(requireProperty(values, "facing", current)));
            default -> throw unsupported("family support seam", current);
        };
        Mc263FeatureBlockState neighbour = world.blockState(relative(position, support));
        boolean supported = switch (family.predicate()) {
            case "STURDY_FACE" -> neighborAuthority(neighbour).faceSturdy(opposite(support));
            case "SUPPORT_CENTER_DOWN" ->
                    Mc263PostprocessResolver.ev7Fact(neighbour, "SUPPORT_CENTER_DOWN");
            case "SEA_PICKLE_PLACE" ->
                    Mc263PostprocessResolver.ev7Fact(neighbour, "SEA_PICKLE_PLACE");
            default -> throw unsupported("family predicate", current);
        };
        String seam = Mc263PostprocessResolver.ev7Table("FS", family.label(),
                current.exactState(), directionName(direction), supported ? "1" : "0");
        if (seam == null) throw unsupported("family seam", current);
        String[] fields = seam.split("\t", -1);
        if (fields.length != 2) throw unsupported("family seam schema", current);
        scheduleEv7(world, position, fields[1], current);
        return Mc263FeatureBlockState.fromExact(fields[0]);
    }

    /**
     * Emits the single published POST-EV-7 seam tick. Only the two grammars the landed receipts
     * authenticate are representable: source water at delay {@value #WATER_DELAY} and a block
     * tick on the subject's own block; anything else fails closed.
     */
    private static void scheduleEv7(Mc263PostprocessResolver.MutableWorld world,
            Mc263PostprocessResolver.Position position, String spec,
            Mc263FeatureBlockState current) {
        if (spec.equals("0/-1/-/-")) return;
        String[] fields = spec.split("/", -1);
        if (fields.length != 4 || !fields[0].equals("1") || !fields[2].equals("NORMAL")) {
            throw unsupported("seam tick grammar", current);
        }
        int delay = Integer.parseInt(fields[1]);
        if (fields[3].equals(WATER)) {
            if (delay != WATER_DELAY) throw unsupported("seam water tick delay", current);
            world.scheduleFluidTick(position, WATER, WATER_DELAY, NORMAL_PRIORITY);
            return;
        }
        if (!fields[3].equals(current.blockKey())) throw unsupported("seam tick target", current);
        world.scheduleBlockTick(position, fields[3], delay, NORMAL_PRIORITY);
    }

    private static String directionName(Mc263PostprocessResolver.Direction direction) {
        return switch (direction) {
            case WEST -> "west"; case EAST -> "east"; case NORTH -> "north";
            case SOUTH -> "south"; case DOWN -> "down"; case UP -> "up";
        };
    }

    private static Mc263FeatureBlockState withProperty(Mc263FeatureBlockState state,
            String property, String value) {
        Map<String, String> values = new java.util.TreeMap<>(properties(state.exactState()));
        if (!values.containsKey(property)) throw unsupported("missing property " + property, state);
        values.put(property, value);
        return withProperties(state, values);
    }

    private static Mc263FeatureBlockState withProperties(Mc263FeatureBlockState state,
            Map<String, String> values) {
        StringBuilder exact = new StringBuilder(state.blockKey()).append('[');
        boolean first = true;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            if (!first) exact.append(',');
            first = false;
            exact.append(entry.getKey()).append('=').append(entry.getValue());
        }
        return Mc263FeatureBlockState.fromExact(exact.append(']').toString());
    }

    private static Mc263PostprocessResolver.NeighborAuthority neighborAuthority(
            Mc263FeatureBlockState state) {
        return Mc263PostprocessResolver.requireNeighborAuthority(state);
    }

    private static String requireProperty(Map<String, String> values, String property,
            Mc263FeatureBlockState state) {
        String value = values.get(property);
        if (value == null) throw unsupported("missing property " + property, state);
        return value;
    }

    private static String horizontal(Mc263PostprocessResolver.Direction direction) {
        return switch (direction) {
            case WEST -> "west"; case EAST -> "east"; case NORTH -> "north";
            case SOUTH -> "south"; default -> null;
        };
    }

    private static Mc263PostprocessResolver.Direction parseHorizontal(String value) {
        if (value == null) throw new IllegalStateException("missing authenticated POST facing");
        return switch (value) {
            case "west" -> Mc263PostprocessResolver.Direction.WEST;
            case "east" -> Mc263PostprocessResolver.Direction.EAST;
            case "north" -> Mc263PostprocessResolver.Direction.NORTH;
            case "south" -> Mc263PostprocessResolver.Direction.SOUTH;
            default -> throw new IllegalStateException("unsupported POST facing: " + value);
        };
    }

    private static int axis(Mc263PostprocessResolver.Direction direction) {
        return direction == Mc263PostprocessResolver.Direction.WEST
                || direction == Mc263PostprocessResolver.Direction.EAST ? 0 : 1;
    }

    private static Mc263PostprocessResolver.Direction counterClockwise(
            Mc263PostprocessResolver.Direction direction) {
        return switch (direction) {
            case NORTH -> Mc263PostprocessResolver.Direction.WEST;
            case WEST -> Mc263PostprocessResolver.Direction.SOUTH;
            case SOUTH -> Mc263PostprocessResolver.Direction.EAST;
            case EAST -> Mc263PostprocessResolver.Direction.NORTH;
            default -> throw new IllegalArgumentException("non-horizontal direction " + direction);
        };
    }

    private static Mc263PostprocessResolver.Direction opposite(
            Mc263PostprocessResolver.Direction direction) {
        return switch (direction) {
            case WEST -> Mc263PostprocessResolver.Direction.EAST;
            case EAST -> Mc263PostprocessResolver.Direction.WEST;
            case NORTH -> Mc263PostprocessResolver.Direction.SOUTH;
            case SOUTH -> Mc263PostprocessResolver.Direction.NORTH;
            case DOWN -> Mc263PostprocessResolver.Direction.UP;
            case UP -> Mc263PostprocessResolver.Direction.DOWN;
        };
    }

    private static Mc263PostprocessResolver.Position relative(
            Mc263PostprocessResolver.Position position,
            Mc263PostprocessResolver.Direction direction) {
        return switch (direction) {
            case WEST -> new Mc263PostprocessResolver.Position(position.x() - 1, position.y(), position.z());
            case EAST -> new Mc263PostprocessResolver.Position(position.x() + 1, position.y(), position.z());
            case NORTH -> new Mc263PostprocessResolver.Position(position.x(), position.y(), position.z() - 1);
            case SOUTH -> new Mc263PostprocessResolver.Position(position.x(), position.y(), position.z() + 1);
            case DOWN -> new Mc263PostprocessResolver.Position(position.x(), position.y() - 1, position.z());
            case UP -> new Mc263PostprocessResolver.Position(position.x(), position.y() + 1, position.z());
        };
    }

    private Mc263FeaturesRegion requireBoundCarrier() {
        if (carrier == null) {
            throw new IllegalStateException(
                    "production POST context is not bound to a canonical FEATURES carrier");
        }
        return carrier;
    }

    private static Mc263FeatureBlockState exact(Mc263FeatureBlockState state, String name) {
        state = Objects.requireNonNull(state, name);
        return Mc263FeatureBlockState.fromExact(state.exactState());
    }

    private static int leafDistance(Mc263FeatureBlockState state) {
        String value = properties(state.exactState()).get("distance");
        try {
            int distance = Integer.parseInt(value);
            if (distance < 1 || distance > 7 || !Integer.toString(distance).equals(value)) {
                throw new IllegalArgumentException("invalid leaf distance");
            }
            return distance;
        } catch (NullPointerException | NumberFormatException invalid) {
            throw new IllegalArgumentException(
                    "authenticated leaf state has no canonical distance: "
                            + state.exactState(), invalid);
        }
    }

    private static Map<String, String> properties(String exactState) {
        int open = exactState.indexOf('[');
        if (open < 0 || !exactState.endsWith("]")) return Map.of();
        java.util.LinkedHashMap<String, String> result = new java.util.LinkedHashMap<>();
        for (String property : exactState.substring(open + 1, exactState.length() - 1).split(",")) {
            int equals = property.indexOf('=');
            if (equals <= 0 || equals == property.length() - 1
                    || result.put(property.substring(0, equals),
                            property.substring(equals + 1)) != null) {
                throw new IllegalArgumentException(
                        "noncanonical POST block-state properties: " + exactState);
            }
        }
        return result;
    }

    private static boolean shapeIndependent(Mc263FeatureBlockState state,
            boolean authenticatedWaterlogged) {
        if (state.fluidKind() != Mc263FeatureBlockState.FluidKind.NONE
                && !authenticatedWaterlogged) return false;
        String key = state.blockKey();
        // The pinned 26.3 {@code PowderSnowBlock} never overrides {@code BlockBehaviour.updateShape}
        // (javap of the pinned inner jar lists no such member), but the catalog codes the block
        // {@code solidRender=false} and it is not a collision full cube, so neither generic gate
        // claims it; a frozen-peaks column marked for post-processing then failed closed.
        if (key.equals("minecraft:powder_snow")) return true;
        return state.isAir() || state.isSolidRender()
                && !key.endsWith("_stairs")
                && !key.endsWith("_fence")
                && !key.endsWith("_wall")
                && !key.endsWith("_slab")
                && !key.equals("minecraft:iron_bars")
                && !key.equals("minecraft:chest");
    }

    /**
     * The pinned POST_NEIGHBOR reference full cube.
     *
     * <p>{@code BlockBehaviour.updateShape} is the identity for every block that does not
     * override it, and the official full cube is exactly the carrier state that occludes,
     * supports and connects on all six faces. The pinned transcript already publishes that vector
     * once per exact state, so the class is read out of the authenticated table instead of
     * restated beside it: any state whose published POST_NEIGHBOR facts equal this reference
     * row's is the official full cube (stained glass, trial spawner and vault are coded
     * {@code solidRender=false}, so {@link #shapeIndependent} never claimed them). The shaped
     * families keep their own authenticated lanes and are excluded before the comparison,
     * exactly as {@link #shapeIndependent} excludes them. Mirrors the Rust POST lane's
     * {@code authenticated_full_cube}.</p>
     */
    private static final String FULL_CUBE_NEIGHBOR_REFERENCE = "minecraft:stone";

    /** True when {@code state} carries the pinned reference full cube's published facts. */
    private static boolean authenticatedFullCube(Mc263FeatureBlockState state) {
        if (state.fluidKind() != Mc263FeatureBlockState.FluidKind.NONE) return false;
        String key = state.blockKey();
        if (key.endsWith("_stairs") || key.endsWith("_fence") || key.endsWith("_wall")
                || key.endsWith("_slab") || key.equals("minecraft:iron_bars")
                || key.equals("minecraft:chest")) return false;
        Mc263PostprocessResolver.NeighborAuthority reference = neighborAuthority(
                Mc263FeatureBlockState.fromExact(FULL_CUBE_NEIGHBOR_REFERENCE));
        Mc263PostprocessResolver.NeighborAuthority facts;
        try {
            facts = neighborAuthority(state);
        } catch (IllegalStateException unpublished) {
            // An unpublished state has no authenticated facts to compare, so it keeps failing
            // closed through the caller's unsupported verdict rather than borrowing this one.
            return false;
        }
        return sameNeighborFacts(facts, reference);
    }

    private static boolean sameNeighborFacts(Mc263PostprocessResolver.NeighborAuthority left,
            Mc263PostprocessResolver.NeighborAuthority right) {
        if (left.sameWoodFence() != right.sameWoodFence()
                || left.connectionException() != right.connectionException()
                || left.wallPostCovered() != right.wallPostCovered()
                || left.wallPostOverride() != right.wallPostOverride()
                || left.rigidRailSupport() != right.rigidRailSupport()
                || !Objects.equals(left.stairFacing(), right.stairFacing())
                || !Objects.equals(left.stairHalf(), right.stairHalf())) return false;
        for (Mc263PostprocessResolver.Direction face
                : Mc263PostprocessResolver.Direction.values()) {
            if (left.faceSturdy(face) != right.faceSturdy(face)
                    || left.gateConnects(face) != right.gateConnects(face)
                    || left.fenceConnects(face) != right.fenceConnects(face)
                    || left.wallConnects(face) != right.wallConnects(face)
                    || left.wallTall(face) != right.wallTall(face)
                    || left.paneConnects(face) != right.paneConnects(face)
                    || left.multifaceSupports(face) != right.multifaceSupports(face)) return false;
        }
        return true;
    }

    private static IllegalStateException unsupported(String operation,
            Mc263FeatureBlockState state) {
        return new IllegalStateException("no authenticated POST " + operation
                + " authority for " + state.exactState());
    }
}
