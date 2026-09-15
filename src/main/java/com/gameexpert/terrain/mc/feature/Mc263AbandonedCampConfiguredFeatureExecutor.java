package com.gameexpert.terrain.mc.feature;

import com.gameexpert.terrain.mc.feature.Mc263CommonTreeFeature.Pos;
import com.gameexpert.terrain.mc.feature.Mc263CommonTreeFeature.Result;
import com.gameexpert.terrain.mc.feature.Mc263CommonTreeFeature.State;
import com.gameexpert.terrain.mc.ore.Mc263WorldgenRandomSource;
import com.gameexpert.terrain.mc.structure.Mc263AbandonedCampStartGenerator.TraceRandom;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/**
 * Dormant exact caller-RNG execution boundary for the 21 Abandoned Camp FEATURE pool elements.
 *
 * <p>The outer keys and semantic digests are frozen Java constants authenticated by the checked
 * 26.3-snapshot-7 sidecar. Production never opens that sidecar. Every execution uses the caller's
 * live Legacy48 {@link TraceRandom} directly, stages all observable effects, and exposes a separate
 * retryable atomic publication step. A preflight rejection therefore has no RNG/query/mutation
 * side effect, while a publication rejection cannot partially expose an already prepared batch.</p>
 */
public final class Mc263AbandonedCampConfiguredFeatureExecutor {
    public static final int RECEIPT_MAGIC = 0x41434633; // ACF3
    public static final int RECEIPT_VERSION = 1;
    public static final String RECEIPT_SCHEMA =
            "mc263-abandoned-camp-configured-feature-execution-v1";
    public static final String CONFIG_ORDER_SHA256 =
            "50b602152972d71019732d13e95802bf3ca385ab971071f1089f890c657bfba9";
    public static final String DOMAIN_SHA256 =
            "d8ac54a68cb44b7d4d01ad83d252f7a7b507d07b77176ecf9b0aaa1c2d240fc0";
    public static final String ORACLE_SHA256 =
            "4d693cbd6773e59f8656a040979808f03c8ab0fbce2578acf0fb260cb08174f7";
    public static final String CAPABILITY_SHA256 =
            "7ca0fe82f0412f8f036df8087d75501491a453e7383e797ea7e78d3a435a0e83";

    private static final int[][] DIRECTIONS = {
            {0, -1, 0}, {0, 1, 0}, {0, 0, -1}, {0, 0, 1}, {-1, 0, 0}, {1, 0, 0}
    };
    private static final List<Mapping> MAPPINGS = buildMappings();
    private static final Map<String, Mapping> BY_KEY = indexMappings(MAPPINGS);
    private static final Set<String> BEE_KEYS = Set.of(
            "minecraft:super_birch_bees_0002",
            "minecraft:fancy_oak_bees_002",
            "minecraft:birch_bees_002",
            "minecraft:cherry_bees_005");
    /** Query-only would-survive states: never emitted into the block mutation lane. */
    private static final Set<String> QUERY_ONLY_SAPLING_STATES = Set.of(
            "minecraft:acacia_sapling[stage=0]",
            "minecraft:birch_sapling[stage=0]",
            "minecraft:oak_sapling[stage=0]",
            "minecraft:spruce_sapling[stage=0]",
            "minecraft:pale_oak_sapling[stage=0]",
            "minecraft:jungle_sapling[stage=0]",
            "minecraft:cherry_sapling[stage=0]");
    private static final String PALE_MOSS_PATCH = "minecraft:pale_moss_patch";
    private static final String PALE_MOSS_BLOCK = "minecraft:pale_moss_block";
    private static final String PALE_CARPET =
            "minecraft:pale_moss_carpet[bottom=true,east=none,north=none,south=none,west=none]";
    private static final String SHORT_GRASS = "minecraft:short_grass";
    private static final String TALL_GRASS_LOWER = "minecraft:tall_grass[half=lower]";
    private static final String TALL_GRASS_UPPER = "minecraft:tall_grass[half=upper]";
    private static final String[] PALE_VEGETATION = paleVegetation();

    private Mc263AbandonedCampConfiguredFeatureExecutor() { }

    public enum Family { COMPLEX_TREE, COMMON_TREE, POPLAR_TREE, BAMBOO }
    public enum PublishStatus { COMMITTED, REPLAYED }

    /** Pure configured-feature capability carrier shared by staged and direct execution. */
    public interface CapabilityAccess {
        int minY();
        boolean supportsFeature(String configuredKey);
        boolean supportsExactState(String exactState);
        boolean supportsTreeFinalization();
        boolean supportsWriteFlags(int flags);
        boolean supportsBentPayloads();
        boolean supportsBeePayloads();
        boolean supportsBlockTicks();
        boolean supportsFluidTicks();
        boolean supportsPostprocessing();
        boolean supportsBeneathTreePodzolTag();
    }

    /** Pure read/capability carrier for the released Legacy48 staged API. */
    public interface WorldAccess extends CapabilityAccess {
        int maxY();
        String exactState(int blockX, int blockY, int blockZ);
        boolean hasScheduledBlockTick(int blockX, int blockY, int blockZ, String blockKey);
        boolean hasScheduledFluidTick(int blockX, int blockY, int blockZ, String fluidKey);
    }

    /**
     * Caller-owned already-isolated live world for current-only direct placement. Capability
     * methods are pure. The executor never forks, stages/replays, commits, or publishes this
     * adapter; every retained effect remains owned by the caller transaction.
     */
    public interface DirectWorldAccess extends CapabilityAccess {
        boolean supportsWorldHeightQueries();
        boolean supportsBlockStateQueries();
        /**
         * Authenticated LevelSimulatedReader predicate lane. These reads are internal vanilla
         * predicates and must not be reported through the externally observable exactState lane.
         */
        default boolean supportsInternalTreePredicates() { return false; }
        default String internalTreePredicateState(int blockX, int blockY, int blockZ) {
            throw new UnsupportedOperationException("internal tree state predicates");
        }
        default String internalTreePredicateFluidState(int blockX, int blockY, int blockZ) {
            throw new UnsupportedOperationException("internal tree fluid predicates");
        }
        boolean supportsOwnership(long owner);
        int worldHeight();
        String exactState(int blockX, int blockY, int blockZ);
        boolean setBlock(int blockX, int blockY, int blockZ, String exactState,
                int flags, long owner);
        void storeBent(int blockX, int blockY, int blockZ,
                String blockIdentity, String entityType);
        void storeBee(int blockX, int blockY, int blockZ, int ticksInHive);
        void scheduleBlockTick(int blockX, int blockY, int blockZ, String blockKey, int delay);
        void scheduleFluidTick(int blockX, int blockY, int blockZ, String fluidKey, int delay);
        void markPostprocess(int blockX, int blockY, int blockZ);
    }

    /**
     * Atomic commit seam. Implementations must validate the complete immutable batch before exposing
     * any lane. The receipt SHA is the idempotency identity: equal SHA+batch may return REPLAYED;
     * equal identity with different bytes is a conflict and must throw without side effects.
     */
    public interface AtomicSink {
        PublishStatus publish(String receiptSha256, AtomicBatch batch);
    }

    public static final class Mapping {
        private final int ordinal;
        private final String poolKey;
        private final String configuredKey;
        private final String targetKey;
        private final String placedCodecSha256;
        private final String configuredCodecSha256;
        private final Family family;

        private Mapping(int ordinal, String poolKey, String configuredKey, String targetKey,
                String placedCodecSha256, String configuredCodecSha256, Family family) {
            this.ordinal = ordinal;
            this.poolKey = poolKey;
            this.configuredKey = configuredKey;
            this.targetKey = targetKey;
            this.placedCodecSha256 = placedCodecSha256;
            this.configuredCodecSha256 = configuredCodecSha256;
            this.family = family;
        }

        public int ordinal() { return ordinal; }
        public String poolKey() { return poolKey; }
        public String configuredKey() { return configuredKey; }
        public String targetKey() { return targetKey; }
        public String placedCodecSha256() { return placedCodecSha256; }
        public String configuredCodecSha256() { return configuredCodecSha256; }
        public Family family() { return family; }
    }

    public static final class Origin {
        private final int x;
        private final int y;
        private final int z;

        public Origin(int x, int y, int z) { this.x = x; this.y = y; this.z = z; }
        public int x() { return x; }
        public int y() { return y; }
        public int z() { return z; }
    }

    /** Inclusive absolute geometry clip. */
    public static final class Box {
        private final int minX;
        private final int minY;
        private final int minZ;
        private final int maxX;
        private final int maxY;
        private final int maxZ;

        public Box(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
            if (minX > maxX || minY > maxY || minZ > maxZ) {
                throw new IllegalArgumentException("invalid write box");
            }
            this.minX = minX; this.minY = minY; this.minZ = minZ;
            this.maxX = maxX; this.maxY = maxY; this.maxZ = maxZ;
        }
        public int minX() { return minX; }
        public int minY() { return minY; }
        public int minZ() { return minZ; }
        public int maxX() { return maxX; }
        public int maxY() { return maxY; }
        public int maxZ() { return maxZ; }
        boolean contains(int x, int y, int z) {
            return x >= minX && x <= maxX && y >= minY && y <= maxY
                    && z >= minZ && z <= maxZ;
        }
    }

    /** Ordered disjoint-union geometry clip; boxes are retained verbatim in the receipt. */
    public static final class WriteDomain {
        private final List<Box> boxes;

        public WriteDomain(List<Box> boxes) {
            Objects.requireNonNull(boxes, "write boxes");
            if (boxes.isEmpty()) throw new IllegalArgumentException("write domain is empty");
            this.boxes = List.copyOf(boxes);
        }
        public static WriteDomain unbounded(int minY, int maxY) {
            return new WriteDomain(List.of(new Box(Integer.MIN_VALUE, minY, Integer.MIN_VALUE,
                    Integer.MAX_VALUE, maxY, Integer.MAX_VALUE)));
        }
        public List<Box> boxes() { return boxes; }
        boolean contains(int x, int y, int z) {
            for (Box box : boxes) if (box.contains(x, y, z)) return true;
            return false;
        }
    }

    public static final class BlockWrite {
        private final int x, y, z, flags;
        private final String exactState;
        private final long owner;
        BlockWrite(int x, int y, int z, String exactState, int flags, long owner) {
            this.x=x; this.y=y; this.z=z; this.exactState=exactState; this.flags=flags;
            this.owner=owner;
        }
        public int x(){return x;} public int y(){return y;} public int z(){return z;}
        public String exactState(){return exactState;} public int flags(){return flags;}
        public long owner(){return owner;}
    }

    /** Semantic BENT reservation for a beehive whose occupant rows are in the BEES lane. */
    public static final class BentPayload {
        private final int x, y, z;
        private final String blockIdentity, entityType;
        BentPayload(int x, int y, int z, String blockIdentity, String entityType) {
            this.x=x; this.y=y; this.z=z; this.blockIdentity=blockIdentity;
            this.entityType=entityType;
        }
        public int x(){return x;} public int y(){return y;} public int z(){return z;}
        public String blockIdentity(){return blockIdentity;} public String entityType(){return entityType;}
    }

    public static final class BeePayload {
        private final int x, y, z, ticksInHive;
        BeePayload(int x, int y, int z, int ticksInHive) {
            this.x=x; this.y=y; this.z=z; this.ticksInHive=ticksInHive;
        }
        public int x(){return x;} public int y(){return y;} public int z(){return z;}
        public int ticksInHive(){return ticksInHive;}
    }

    public static final class BlockTick {
        private final int x, y, z, delay;
        private final String blockKey;
        BlockTick(int x,int y,int z,String blockKey,int delay){
            this.x=x;this.y=y;this.z=z;this.blockKey=blockKey;this.delay=delay;
        }
        public int x(){return x;} public int y(){return y;} public int z(){return z;}
        public String blockKey(){return blockKey;} public int delay(){return delay;}
    }

    public static final class FluidTick {
        private final int x, y, z, delay;
        private final String fluidKey;
        FluidTick(int x,int y,int z,String fluidKey,int delay){
            this.x=x;this.y=y;this.z=z;this.fluidKey=fluidKey;this.delay=delay;
        }
        public int x(){return x;} public int y(){return y;} public int z(){return z;}
        public String fluidKey(){return fluidKey;} public int delay(){return delay;}
    }

    public static final class PostprocessMark {
        private final int x, y, z;
        PostprocessMark(int x,int y,int z){this.x=x;this.y=y;this.z=z;}
        public int x(){return x;} public int y(){return y;} public int z(){return z;}
    }

    public static final class Encounter {
        private final String lane, name, text;
        private final long[] values;
        Encounter(String lane, String name, String text, long... values) {
            this.lane=lane;this.name=name;this.text=text == null ? "" : text;
            this.values=values.clone();
        }
        public String lane(){return lane;} public String name(){return name;}
        public String text(){return text;} public long[] values(){return values.clone();}
    }

    public static final class AtomicBatch {
        private final List<BlockWrite> blocks;
        private final List<BentPayload> bent;
        private final List<BeePayload> bees;
        private final List<BlockTick> blockTicks;
        private final List<FluidTick> fluidTicks;
        private final List<PostprocessMark> postprocess;

        AtomicBatch(List<BlockWrite> blocks, List<BentPayload> bent, List<BeePayload> bees,
                List<BlockTick> blockTicks, List<FluidTick> fluidTicks,
                List<PostprocessMark> postprocess) {
            this.blocks=List.copyOf(blocks);this.bent=List.copyOf(bent);this.bees=List.copyOf(bees);
            this.blockTicks=List.copyOf(blockTicks);this.fluidTicks=List.copyOf(fluidTicks);
            this.postprocess=List.copyOf(postprocess);
        }
        public List<BlockWrite> blocks(){return blocks;} public List<BentPayload> bent(){return bent;}
        public List<BeePayload> bees(){return bees;} public List<BlockTick> blockTicks(){return blockTicks;}
        public List<FluidTick> fluidTicks(){return fluidTicks;}
        public List<PostprocessMark> postprocess(){return postprocess;}
    }

    public static final class PreparedExecution {
        private final Mapping mapping;
        private final Origin origin;
        private final long owner;
        private final boolean placed;
        private final int attemptedWrites;
        private final AtomicBatch batch;
        private final List<Encounter> encounters;
        private final byte[] canonicalReceipt;
        private final String receiptSha256;

        PreparedExecution(Mapping mapping, Origin origin, long owner, boolean placed,
                int attemptedWrites, AtomicBatch batch, List<Encounter> encounters,
                byte[] canonicalReceipt, String receiptSha256) {
            this.mapping=mapping;this.origin=origin;this.owner=owner;this.placed=placed;
            this.attemptedWrites=attemptedWrites;this.batch=batch;this.encounters=List.copyOf(encounters);
            this.canonicalReceipt=canonicalReceipt.clone();this.receiptSha256=receiptSha256;
        }
        public Mapping mapping(){return mapping;} public Origin origin(){return origin;}
        public long owner(){return owner;} public boolean placed(){return placed;}
        public int attemptedWrites(){return attemptedWrites;} public AtomicBatch batch(){return batch;}
        public List<Encounter> encounters(){return encounters;}
        public byte[] canonicalReceipt(){return canonicalReceipt.clone();}
        public String receiptSha256(){return receiptSha256;}
        public Publication publish(AtomicSink sink) {
            Objects.requireNonNull(sink, "atomic sink");
            PublishStatus status = Objects.requireNonNull(
                    sink.publish(receiptSha256, batch), "publish status");
            return new Publication(status, receiptSha256);
        }
    }

    public static final class Publication {
        private final PublishStatus status;
        private final String receiptSha256;
        Publication(PublishStatus status, String receiptSha256) {
            this.status=status;this.receiptSha256=receiptSha256;
        }
        public PublishStatus status(){return status;} public String receiptSha256(){return receiptSha256;}
    }

    /** Immutable result of capability-only direct preflight. */
    public static final class DirectPreflight {
        private final Mapping mapping;
        private final long owner;
        DirectPreflight(Mapping mapping, long owner) { this.mapping=mapping;this.owner=owner; }
        public Mapping mapping(){return mapping;} public long owner(){return owner;}
    }

    /** One retained final cell from the direct live-world overlay. */
    public static final class DirectFinalCell {
        private final int x, y, z;
        private final String exactState;
        DirectFinalCell(int x, int y, int z, String exactState) {
            this.x=x;this.y=y;this.z=z;this.exactState=exactState;
        }
        public int x(){return x;} public int y(){return y;} public int z(){return z;}
        public String exactState(){return exactState;}
    }

    /** Immutable observation of one live direct placement; it intentionally has no publish API. */
    public static final class DirectPlacementResult {
        private final Mapping mapping;
        private final Origin origin;
        private final long owner;
        private final boolean placed;
        private final int attemptedWrites;
        private final List<Encounter> encounters;
        private final List<DirectFinalCell> finalCells;
        DirectPlacementResult(Mapping mapping, Origin origin, long owner, boolean placed,
                int attemptedWrites, List<Encounter> encounters, List<DirectFinalCell> finalCells) {
            this.mapping=mapping;this.origin=origin;this.owner=owner;this.placed=placed;
            this.attemptedWrites=attemptedWrites;this.encounters=List.copyOf(encounters);
            this.finalCells=List.copyOf(finalCells);
        }
        public Mapping mapping(){return mapping;} public Origin origin(){return origin;}
        public long owner(){return owner;} public boolean placed(){return placed;}
        public int attemptedWrites(){return attemptedWrites;}
        public List<Encounter> encounters(){return encounters;}
        public List<DirectFinalCell> finalCells(){return finalCells;}
    }

    public static List<Mapping> mappings() { return MAPPINGS; }
    public static List<String> configuredKeys() {
        return MAPPINGS.stream().map(Mapping::configuredKey).toList();
    }
    public static Mapping requireMapping(String configuredKey) {
        Mapping mapping = BY_KEY.get(normalize(configuredKey));
        if (mapping == null) throw new IllegalArgumentException(
                "unknown Abandoned Camp configured feature: " + configuredKey);
        return mapping;
    }

    public static Publication executeAndPublish(String configuredKey, TraceRandom random,
            Origin origin, long owner, WriteDomain writeDomain, WorldAccess world, AtomicSink sink) {
        return prepare(configuredKey, random, origin, owner, writeDomain, world).publish(sink);
    }

    /** Executes against a staged overlay only. No sink method is called by this phase. */
    public static PreparedExecution prepare(String configuredKey, TraceRandom random, Origin origin,
            long owner, WriteDomain writeDomain, WorldAccess world) {
        Mapping mapping = requireMapping(configuredKey);
        Objects.requireNonNull(random, "Camp TraceRandom");
        Objects.requireNonNull(origin, "feature origin");
        Objects.requireNonNull(writeDomain, "write domain");
        Objects.requireNonNull(world, "world");
        preflightBatchCapabilities(mapping, world);

        Stage stage = new Stage(mapping, origin, owner, writeDomain, world);
        boolean placed = placeMapped(mapping, random, origin, stage);
        AtomicBatch batch = stage.batch();
        byte[] receipt = encodeReceipt(mapping, origin, owner, writeDomain, placed,
                stage.attemptedWrites, batch, stage.encounters);
        return new PreparedExecution(mapping, origin, owner, placed, stage.attemptedWrites, batch,
                stage.encounters, receipt, sha256(receipt));
    }

    /**
     * Pure direct capability/state-closure validation. It performs no RNG draw, height/state query,
     * mutation, tick/sidecar encounter, transaction fork, or publication.
     */
    public static DirectPreflight preflightDirect(String configuredKey, long owner,
            DirectWorldAccess world) {
        Mapping mapping = requireMapping(configuredKey);
        Objects.requireNonNull(world, "direct world");
        preflightDirectCapabilities(mapping, owner, world);
        Stage stage = new Stage(mapping, new Origin(0, 0, 0), owner, world);
        preflightKernel(mapping, stage);
        return new DirectPreflight(mapping, owner);
    }

    /**
     * Places directly into a caller-owned isolated live world using the caller's random stream.
     * The first live query captures world height in encounter order; no internal batch or publish
     * exists on this path.
     */
    public static DirectPlacementResult placeDirect(String configuredKey,
            Mc263WorldgenRandomSource random, Origin origin, long owner, DirectWorldAccess world) {
        Mapping mapping = requireMapping(configuredKey);
        Objects.requireNonNull(random, "caller worldgen random");
        Objects.requireNonNull(origin, "feature origin");
        Objects.requireNonNull(world, "direct world");
        preflightDirectCapabilities(mapping, owner, world);
        Stage stage = new Stage(mapping, origin, owner, world);
        preflightKernel(mapping, stage);
        stage.beginDirectPlacement();
        boolean placed = placeMapped(mapping, random, origin, stage);
        return new DirectPlacementResult(mapping, origin, owner, placed,
                stage.attemptedWrites, stage.encounters, stage.directFinalCells());
    }

    private static boolean placeMapped(Mapping mapping, Mc263WorldgenRandomSource random,
            Origin origin, Stage stage) {
        if (mapping.family == Family.BAMBOO) {
            return Mc263BambooForestFlowersFeature.placeConfigured(9, random,
                    origin.x, origin.y, origin.z, stage.bambooWorld(),
                    (phase, values) -> stage.trace("bamboo", phase, values)).placed();
        }
        if (mapping.family == Family.COMMON_TREE) {
            return Mc263CommonTreeFeature.place(mapping.targetKey, random,
                    new Pos(origin.x, origin.y, origin.z), stage.commonWorld(),
                    (phase, values) -> stage.trace("common", phase, values)).placed();
        }
        if (mapping.family == Family.POPLAR_TREE) {
            return Mc263PoplarTreeFeature.place(mapping.targetKey, random,
                    new Mc263PoplarTreeFeature.Pos(origin.x, origin.y, origin.z),
                    stage.poplarWorld(),
                    (phase, values) -> stage.trace("poplar", phase, values)).placed();
        }
        return Mc263ComplexTreeFeature.placeConfigured(mapping.configuredKey, random,
                new Pos(origin.x, origin.y, origin.z), stage.complexWorld(), stage.complexLeaves(),
                (phase, values) -> stage.trace("complex", phase, values)).placed();
    }

    private static void preflightKernel(Mapping mapping, Stage stage) {
        if (mapping.family == Family.BAMBOO) {
            Mc263BambooForestFlowersFeature.preflightPlaced(9, stage.bambooWorld());
        } else if (mapping.family == Family.COMMON_TREE) {
            Mc263CommonTreeFeature.preflightConfigured(mapping.targetKey, stage.commonWorld());
        } else if (mapping.family == Family.POPLAR_TREE) {
            Mc263PoplarTreeFeature.preflightConfigured(mapping.targetKey, stage.poplarWorld());
        } else {
            Mc263ComplexTreeFeature.preflightConfigured(mapping.configuredKey,
                    stage.complexWorld(), stage.complexLeaves());
        }
    }

    private static void preflightDirectCapabilities(Mapping mapping, long owner,
            DirectWorldAccess world) {
        if (!world.supportsWorldHeightQueries()) {
            throw new UnsupportedOperationException("world height queries");
        }
        if (!world.supportsBlockStateQueries()) {
            throw new UnsupportedOperationException("block-state queries");
        }
        if (Mc263CommonTreeFeature.configuredKeys().contains(mapping.targetKey)
                && !world.supportsInternalTreePredicates()) {
            throw new UnsupportedOperationException("internal tree predicates");
        }
        if (!world.supportsOwnership(owner)) {
            throw new UnsupportedOperationException("caller ownership " + owner);
        }
        preflightBatchCapabilities(mapping, world);
    }

    private static void preflightBatchCapabilities(Mapping mapping, CapabilityAccess world) {
        if (!world.supportsFeature(mapping.configuredKey)) {
            throw new UnsupportedOperationException(mapping.configuredKey);
        }
        if (mapping.family == Family.BAMBOO) {
            if (!world.supportsWriteFlags(2)) throw new UnsupportedOperationException("write flags 2");
            return;
        }
        if (!world.supportsTreeFinalization()) {
            throw new UnsupportedOperationException("tree finalization");
        }
        for (int flags : new int[]{2, 3, 19}) {
            if (!world.supportsWriteFlags(flags)) {
                throw new UnsupportedOperationException("write flags " + flags);
            }
        }
        if (!world.supportsBlockTicks()) throw new UnsupportedOperationException("block ticks");
        if (!world.supportsFluidTicks()) throw new UnsupportedOperationException("fluid ticks");
        if (BEE_KEYS.contains(mapping.configuredKey)) {
            if (!world.supportsBentPayloads()) throw new UnsupportedOperationException("BENT payloads");
            if (!world.supportsBeePayloads()) throw new UnsupportedOperationException("BEES payloads");
        }
    }

    private static final class Stage {
        private final Mapping mapping;
        private final Origin origin;
        private final long owner;
        private final WriteDomain domain;
        private final CapabilityAccess capabilities;
        private final WorldAccess source;
        private final DirectWorldAccess direct;
        private final List<BlockWrite> writes = new ArrayList<>();
        private final List<BentPayload> bent = new ArrayList<>();
        private final List<BeePayload> bees = new ArrayList<>();
        private final List<BlockTick> blockTicks = new ArrayList<>();
        private final List<FluidTick> fluidTicks = new ArrayList<>();
        private final List<PostprocessMark> postprocess = new ArrayList<>();
        private final List<Encounter> encounters = new ArrayList<>();
        private final Map<Point, String> overlay = new HashMap<>();
        private final Set<TickIdentity> blockTickKeys = new LinkedHashSet<>();
        private final Set<TickIdentity> fluidTickKeys = new LinkedHashSet<>();
        private final Set<Point> bentPositions = new LinkedHashSet<>();
        private int attemptedWrites;
        private Integer directWorldHeight;

        Stage(Mapping mapping, Origin origin, long owner, WriteDomain domain, WorldAccess source) {
            this.mapping=mapping;this.origin=origin;this.owner=owner;this.domain=domain;
            this.capabilities=source;this.source=source;this.direct=null;
        }

        Stage(Mapping mapping, Origin origin, long owner, DirectWorldAccess direct) {
            this.mapping=mapping;this.origin=origin;this.owner=owner;this.domain=null;
            this.capabilities=direct;this.source=null;this.direct=direct;
        }

        void beginDirectPlacement() {
            if (direct == null) throw new IllegalStateException("not direct placement");
            if (directWorldHeight != null) return;
            int height = direct.worldHeight();
            if (height <= 0) throw new IllegalStateException("invalid world height " + height);
            directWorldHeight = height;
            encounters.add(new Encounter("QUERY", "world_height", "", height));
        }

        List<DirectFinalCell> directFinalCells() {
            if (direct == null) throw new IllegalStateException("not direct placement");
            return overlay.entrySet().stream()
                    .sorted(Comparator.comparingInt((Map.Entry<Point, String> value) -> value.getKey().x)
                            .thenComparingInt(value -> value.getKey().y)
                            .thenComparingInt(value -> value.getKey().z))
                    .map(value -> new DirectFinalCell(value.getKey().x, value.getKey().y,
                            value.getKey().z, value.getValue()))
                    .toList();
        }

        private int minY(){return capabilities.minY();}
        private int maxY(){
            if (source != null) return source.maxY();
            if (directWorldHeight == null) throw new IllegalStateException("world height not queried");
            return Math.addExact(minY(), directWorldHeight - 1);
        }

        void trace(String family, String phase, long... values) {
            encounters.add(new Encounter("TRACE", family + ":" + phase, "", values));
        }

        String exactState(int x, int y, int z) {
            Point point = new Point(x,y,z);
            String exact;
            if (direct != null) {
                exact = Objects.requireNonNull(direct.exactState(x,y,z), "world exact state");
            } else {
                exact = overlay.get(point);
                if (exact == null) exact = Objects.requireNonNull(source.exactState(x,y,z),
                        "world exact state");
            }
            Mc263FeatureBlockState.fromExact(exact);
            encounters.add(new Encounter("QUERY", "state", exact, x,y,z));
            return exact;
        }

        Mc263FeatureBlockState featureState(int x,int y,int z) {
            return Mc263FeatureBlockState.fromExact(exactState(x,y,z));
        }

        boolean write(int x,int y,int z,String exact,int flags) {
            attemptedWrites++;
            if (!capabilities.supportsExactState(exact)) {
                throw new UnsupportedOperationException("exact state " + exact);
            }
            if (direct != null) {
                boolean kept = direct.setBlock(x,y,z,exact,flags,owner);
                encounters.add(new Encounter("MUTATION", "block", exact,
                        x,y,z,flags,kept?1:0,owner));
                if (kept) overlay.put(new Point(x,y,z), exact);
                return kept;
            }
            boolean kept = y >= source.minY() && y <= source.maxY() && domain.contains(x,y,z);
            encounters.add(new Encounter("MUTATION", "block", exact, x,y,z,flags,kept?1:0,owner));
            if (kept) {
                overlay.put(new Point(x,y,z), exact);
                writes.add(new BlockWrite(x,y,z,exact,flags,owner));
            }
            return kept;
        }

        void storeBee(int x,int y,int z,int ticks) {
            if (ticks < 0 || ticks > 598) throw new IllegalArgumentException("bee ticks " + ticks);
            Point point = new Point(x,y,z);
            String exact = overlay.get(point);
            if (exact == null || !Mc263FeatureBlockState.fromExact(exact).blockKey()
                    .equals("minecraft:bee_nest")) {
                throw new IllegalStateException("bee payload lacks retained bee nest at " + point);
            }
            if (bentPositions.add(point)) {
                if (direct != null) {
                    direct.storeBent(x,y,z,"minecraft:bee_nest","minecraft:beehive");
                    encounters.add(new Encounter("SIDECAR", "bent", "minecraft:beehive", x,y,z));
                } else {
                    bent.add(new BentPayload(x,y,z,"minecraft:bee_nest","minecraft:beehive"));
                }
            }
            if (direct != null) direct.storeBee(x,y,z,ticks);
            else bees.add(new BeePayload(x,y,z,ticks));
            encounters.add(new Encounter("SIDECAR", "bee", "minecraft:bee", x,y,z,ticks));
        }

        void scheduleBlockTick(int x,int y,int z,String blockKey,int delay) {
            if (delay < 0) throw new IllegalArgumentException("negative block tick delay");
            if (direct != null) {
                direct.scheduleBlockTick(x,y,z,blockKey,delay);
                encounters.add(new Encounter("SIDECAR", "block_tick", blockKey,
                        x,y,z,delay,1));
                return;
            }
            if (!domain.contains(x,y,z) || y < source.minY() || y > source.maxY()) return;
            TickIdentity key = new TickIdentity(x,y,z,blockKey);
            if (!blockTickKeys.contains(key) && source.hasScheduledBlockTick(x,y,z,blockKey)) {
                blockTickKeys.add(key);
            }
            boolean kept = blockTickKeys.add(key);
            encounters.add(new Encounter("SIDECAR", "block_tick", blockKey,
                    x,y,z,delay,kept?1:0));
            if (kept) blockTicks.add(new BlockTick(x,y,z,blockKey,delay));
        }

        void scheduleFluidTick(int x,int y,int z,String fluidKey,int delay) {
            if (delay < 0) throw new IllegalArgumentException("negative fluid tick delay");
            if (direct != null) {
                direct.scheduleFluidTick(x,y,z,fluidKey,delay);
                encounters.add(new Encounter("SIDECAR", "fluid_tick", fluidKey,
                        x,y,z,delay,1));
                return;
            }
            if (!domain.contains(x,y,z) || y < source.minY() || y > source.maxY()) return;
            TickIdentity key = new TickIdentity(x,y,z,fluidKey);
            if (!fluidTickKeys.contains(key) && source.hasScheduledFluidTick(x,y,z,fluidKey)) {
                fluidTickKeys.add(key);
            }
            boolean kept = fluidTickKeys.add(key);
            encounters.add(new Encounter("SIDECAR", "fluid_tick", fluidKey,
                    x,y,z,delay,kept?1:0));
            if (kept) fluidTicks.add(new FluidTick(x,y,z,fluidKey,delay));
        }

        void markPostprocess(int x,int y,int z) {
            if (!capabilities.supportsPostprocessing()) {
                throw new UnsupportedOperationException("postprocessing");
            }
            if (direct != null) {
                direct.markPostprocess(x,y,z);
                encounters.add(new Encounter("SIDECAR", "postprocess", "", x,y,z));
                return;
            }
            if (!domain.contains(x,y,z) || y < source.minY() || y > source.maxY()) return;
            postprocess.add(new PostprocessMark(x,y,z));
            encounters.add(new Encounter("SIDECAR", "postprocess", "", x,y,z));
        }

        AtomicBatch batch() {
            if (direct != null) throw new IllegalStateException("direct placement has no batch");
            return new AtomicBatch(writes,bent,bees,blockTicks,fluidTicks,postprocess);
        }

        Mc263CommonTreeFeature.WorldAccess commonWorld() {
            return new Mc263CommonTreeFeature.WorldAccess() {
                @Override public int minY(){return Stage.this.minY();}
                @Override public int maxY(){return Stage.this.maxY();}
                @Override public State state(Pos pos){return commonState(featureState(pos.x(),pos.y(),pos.z()));}
                @Override public boolean supportsInternalPredicates(){
                    return direct != null && direct.supportsInternalTreePredicates();
                }
                @Override public State internalPredicateState(Pos pos){
                    if (direct == null) throw new UnsupportedOperationException("internal tree state predicates");
                    String exact = Objects.requireNonNull(
                            direct.internalTreePredicateState(pos.x(),pos.y(),pos.z()),
                            "internal tree predicate state");
                    return commonState(Mc263FeatureBlockState.fromExact(exact));
                }
                @Override public boolean internalFluidIsWater(Pos pos){
                    if (direct == null) throw new UnsupportedOperationException("internal tree fluid predicates");
                    String fluid = Objects.requireNonNull(
                            direct.internalTreePredicateFluidState(pos.x(),pos.y(),pos.z()),
                            "internal tree predicate fluid state");
                    return switch (fluid) {
                        case "minecraft:empty" -> false;
                        case "minecraft:water[level=0]" -> true;
                        default -> throw new UnsupportedOperationException(
                                "internal tree fluid state " + fluid);
                    };
                }
                @Override public boolean cannotReplaceBelowTreeTrunk(State state){
                    return Mc263FeatureBlockState.fromExact(state.canonical()).cannotReplaceBelowTreeTrunk();
                }
                @Override public boolean canSaplingSurvive(State sapling,Pos pos){
                    boolean survives = saplingSurvives(sapling.block(), pos.x(),pos.y(),pos.z());
                    encounters.add(new Encounter("QUERY","sapling_survive",sapling.block(),
                            pos.x(),pos.y(),pos.z(),survives?1:0));
                    return survives;
                }
                @Override public boolean supportsFeature(String key){return capabilities.supportsFeature(normalize(key));}
                @Override public boolean supportsState(State state){return supportsTreeState(state.canonical());}
                @Override public boolean supportsBeeNestPayload(){return capabilities.supportsBeePayloads()&&capabilities.supportsBentPayloads();}
                @Override public boolean supportsTreeFinalization(){return capabilities.supportsTreeFinalization();}
                @Override public boolean supportsPostProcessing(){return capabilities.supportsPostprocessing();}
                @Override public boolean set(Pos pos,State state,int flags){return write(pos.x(),pos.y(),pos.z(),state.canonical(),flags);}
                @Override public boolean setAndUpdate(Pos pos,State state){return write(pos.x(),pos.y(),pos.z(),state.canonical(),3);}
                @Override public void markAboveForPostProcessing(Pos pos){
                    for(int dy=1;dy<=2;dy++){
                        if(featureState(pos.x(),pos.y()+dy,pos.z()).isAir()) return;
                        markPostprocess(pos.x(),pos.y()+dy,pos.z());
                    }
                }
                @Override public boolean sturdyUp(Pos below,Pos queriedFrom){
                    return featureState(below.x(),below.y(),below.z())
                            .isFaceSturdy(Mc263FeatureBlockState.OcclusionFace.UP);
                }
                @Override public int motionBlockingNoLeaves(Pos pos){
                    int y=Stage.this.motionBlockingNoLeaves(pos.x(),pos.z());
                    encounters.add(new Encounter("QUERY","motion_blocking_no_leaves","",pos.x(),pos.z(),y));
                    return y;
                }
                @Override public void storeBee(Pos nest,int ticks){Stage.this.storeBee(nest.x(),nest.y(),nest.z(),ticks);}
                @Override public void finishTree(Set<Pos> logs,Set<Pos> leaves,Set<Pos> roots,Set<Pos> decorations){
                    Stage.this.finishTree(commonPoints(logs),commonPoints(leaves),commonPoints(roots),commonPoints(decorations));
                }
            };
        }

        Mc263ComplexTreeFeature.World complexWorld() {
            Mc263CommonTreeFeature.WorldAccess common = commonWorld();
            return new Mc263ComplexTreeFeature.World() {
                @Override public int minY(){return common.minY();}
                @Override public int maxY(){return common.maxY();}
                @Override public State state(Pos pos){return common.state(pos);}
                @Override public boolean cannotReplaceBelowTreeTrunk(State state){return common.cannotReplaceBelowTreeTrunk(state);}
                @Override public boolean canSaplingSurvive(State sapling,Pos pos){return common.canSaplingSurvive(sapling,pos);}
                @Override public boolean supportsFeature(String key){return common.supportsFeature(key);}
                @Override public boolean supportsState(State state){return common.supportsState(state);}
                @Override public boolean supportsBeeNestPayload(){return common.supportsBeeNestPayload();}
                @Override public boolean supportsTreeFinalization(){return common.supportsTreeFinalization();}
                @Override public boolean supportsPostProcessing(){return common.supportsPostProcessing();}
                @Override public boolean set(Pos pos,State state,int flags){return common.set(pos,state,flags);}
                @Override public boolean setAndUpdate(Pos pos,State state){return common.setAndUpdate(pos,state);}
                @Override public void markAboveForPostProcessing(Pos pos){common.markAboveForPostProcessing(pos);}
                @Override public boolean sturdyUp(Pos below,Pos from){return common.sturdyUp(below,from);}
                @Override public int motionBlockingNoLeaves(Pos pos){return common.motionBlockingNoLeaves(pos);}
                @Override public void storeBee(Pos nest,int ticks){common.storeBee(nest,ticks);}
                @Override public void finishTree(Set<Pos> logs,Set<Pos> leaves,Set<Pos> roots,Set<Pos> decorations){common.finishTree(logs,leaves,roots,decorations);}
                @Override public int oceanFloor(Pos column){return Stage.this.motionBlockingNoLeaves(column.x(),column.z());}
                @Override public int worldSurface(Pos column){return Stage.this.motionBlockingNoLeaves(column.x(),column.z());}
                @Override public boolean biomeAllows(String placedKey,Pos pos){return true;}
                @Override public boolean supportsBeneathTreePodzolTag(){return capabilities.supportsBeneathTreePodzolTag();}
                @Override public boolean beneathTreePodzolReplaceable(State state){
                    return switch(Mc263FeatureBlockState.fromExact(state.canonical()).blockKey()){
                        case "minecraft:dirt","minecraft:grass_block","minecraft:coarse_dirt",
                                "minecraft:rooted_dirt","minecraft:podzol","minecraft:mycelium",
                                "minecraft:moss_block","minecraft:pale_moss_block" -> true;
                        default -> false;
                    };
                }
            };
        }

        Mc263ComplexTreeFeature.LeafExecutor complexLeaves() {
            Mc263CommonTreeFeature.WorldAccess common=commonWorld();
            Mc263PoplarTreeFeature.WorldAccess poplar=poplarWorld();
            return new Mc263ComplexTreeFeature.LeafExecutor(){
                @Override public boolean supports(String raw){
                    String key=normalize(raw);
                    return Mc263CommonTreeFeature.configuredKeys().contains(key)
                            || Mc263PoplarTreeFeature.configuredKeys().contains(key)
                            || key.equals(PALE_MOSS_PATCH);
                }
                @Override public void preflight(String raw,Mc263ComplexTreeFeature.World world){
                    String key=normalize(raw);
                    if(Mc263CommonTreeFeature.configuredKeys().contains(key)){
                        Mc263CommonTreeFeature.preflightConfigured(key,common);return;
                    }
                    if(Mc263PoplarTreeFeature.configuredKeys().contains(key)){
                        Mc263PoplarTreeFeature.preflightConfigured(key,poplar);return;
                    }
                    if(key.equals(PALE_MOSS_PATCH)){
                        preflightPaleMoss();return;
                    }
                    throw new UnsupportedOperationException(key);
                }
                @Override public Result place(String raw,Mc263WorldgenRandomSource random,Pos at,
                        Mc263ComplexTreeFeature.World world,Mc263CommonTreeFeature.TraceSink trace){
                    String key=normalize(raw);
                    if(Mc263CommonTreeFeature.configuredKeys().contains(key)){
                        return Mc263CommonTreeFeature.place(key,random,at,common,
                                (phase,values)->Stage.this.trace("common",phase,values));
                    }
                    if(Mc263PoplarTreeFeature.configuredKeys().contains(key)){
                        Mc263PoplarTreeFeature.Result r=Mc263PoplarTreeFeature.place(key,random,
                                new Mc263PoplarTreeFeature.Pos(at.x(),at.y(),at.z()),poplar,
                                (phase,values)->Stage.this.trace("poplar",phase,values));
                        return new Result(r.placed(),r.reads(),r.writes(),r.retained(),0,r.postprocess());
                    }
                    if(key.equals(PALE_MOSS_PATCH)) return placePaleMoss(random,at);
                    throw new UnsupportedOperationException(key);
                }
            };
        }

        Mc263PoplarTreeFeature.WorldAccess poplarWorld() {
            return new Mc263PoplarTreeFeature.WorldAccess(){
                @Override public int minY(){return Stage.this.minY();}
                @Override public int maxY(){return Stage.this.maxY();}
                @Override public Mc263PoplarTreeFeature.State state(Mc263PoplarTreeFeature.Pos pos){
                    return poplarState(featureState(pos.x(),pos.y(),pos.z()));
                }
                @Override public boolean cannotReplaceBelowTreeTrunk(Mc263PoplarTreeFeature.State state){
                    return Mc263FeatureBlockState.fromExact(state.canonical()).cannotReplaceBelowTreeTrunk();
                }
                @Override public boolean canSaplingSurvive(Mc263PoplarTreeFeature.State sapling,
                        Mc263PoplarTreeFeature.Pos pos){
                    boolean survives=saplingSurvives(sapling.block(),pos.x(),pos.y(),pos.z());
                    encounters.add(new Encounter("QUERY","sapling_survive",sapling.block(),pos.x(),pos.y(),pos.z(),survives?1:0));
                    return survives;
                }
                @Override public boolean supportsFeature(String key){return capabilities.supportsFeature(normalize(key));}
                @Override public boolean supportsState(Mc263PoplarTreeFeature.State state){return supportsTreeState(state.canonical());}
                @Override public boolean supportsTreeFinalization(){return capabilities.supportsTreeFinalization();}
                @Override public boolean supportsPostProcessing(){return capabilities.supportsPostprocessing();}
                @Override public boolean set(Mc263PoplarTreeFeature.Pos pos,Mc263PoplarTreeFeature.State state,int flags){return write(pos.x(),pos.y(),pos.z(),state.canonical(),flags);}
                @Override public boolean setAndUpdate(Mc263PoplarTreeFeature.Pos pos,Mc263PoplarTreeFeature.State state){return write(pos.x(),pos.y(),pos.z(),state.canonical(),3);}
                @Override public void markAboveForPostProcessing(Mc263PoplarTreeFeature.Pos pos){
                    for(int dy=1;dy<=2;dy++){
                        if(featureState(pos.x(),pos.y()+dy,pos.z()).isAir()) return;
                        markPostprocess(pos.x(),pos.y()+dy,pos.z());
                    }
                }
                @Override public boolean sturdyUp(Mc263PoplarTreeFeature.Pos below,Mc263PoplarTreeFeature.Pos from){
                    return featureState(below.x(),below.y(),below.z()).isFaceSturdy(Mc263FeatureBlockState.OcclusionFace.UP);
                }
                @Override public int motionBlockingNoLeaves(Mc263PoplarTreeFeature.Pos pos){return Stage.this.motionBlockingNoLeaves(pos.x(),pos.z());}
                @Override public boolean waterSource(Mc263PoplarTreeFeature.Pos pos){return featureState(pos.x(),pos.y(),pos.z()).fluidKind()==Mc263FeatureBlockState.FluidKind.WATER_SOURCE;}
                @Override public void finishTree(Set<Mc263PoplarTreeFeature.Pos> logs,Set<Mc263PoplarTreeFeature.Pos> leaves,
                        Set<Mc263PoplarTreeFeature.Pos> roots,Set<Mc263PoplarTreeFeature.Pos> decorations){
                    Stage.this.finishTree(poplarPoints(logs),poplarPoints(leaves),poplarPoints(roots),poplarPoints(decorations));
                }
            };
        }

        Mc263BambooForestFlowersFeature.WorldAccess bambooWorld() {
            return new Mc263BambooForestFlowersFeature.WorldAccess(){
                @Override public int minGenerationY(){return Stage.this.minY();}
                @Override public int height(Mc263BambooForestFlowersFeature.Heightmap h,int x,int z){return motionBlockingNoLeaves(x,z);}
                @Override public String biomeKey(int x,int y,int z){return "minecraft:jungle";}
                @Override public double bambooCountNoise(int x,int z){return 0.0;}
                @Override public Mc263BambooForestFlowersFeature.State blockState(int x,int y,int z){
                    Mc263FeatureBlockState state=featureState(x,y,z);
                    return new Mc263BambooForestFlowersFeature.State(state.blockKey(),exactProperties(state.exactState()),
                            state.fluidTypeKey(),state.isAir(),state.canBeReplaced());
                }
                @Override public boolean canSurvive(Mc263BambooForestFlowersFeature.State state,int x,int y,int z){
                    Mc263FeatureBlockState below=featureState(x,y-1,z);
                    boolean result=state.block().equals("minecraft:bamboo")?below.supportsBamboo():below.supportsVegetationTag();
                    encounters.add(new Encounter("QUERY","can_survive",state.block(),x,y,z,result?1:0));
                    return result;
                }
                @Override public boolean beneathBambooPodzolReplaceable(Mc263BambooForestFlowersFeature.State state){
                    return Mc263FeatureBlockState.fromExact(state.canonical()).beneathBambooPodzolReplaceable();
                }
                @Override public boolean supportsFeature(int index){
                    return index==9&&capabilities.supportsFeature(mapping.configuredKey)&&capabilities.supportsFeature(mapping.targetKey);
                }
                @Override public boolean supportsState(Mc263BambooForestFlowersFeature.State state){return capabilities.supportsExactState(state.canonical());}
                @Override public boolean trySetBlockState(int x,int y,int z,Mc263BambooForestFlowersFeature.State state,int flags){
                    return write(x,y,z,state.canonical(),flags);
                }
            };
        }

        private int motionBlockingNoLeaves(int x,int z){
            for(int y=maxY();y>=minY();y--){
                Mc263FeatureBlockState state=featureState(x,y,z);
                if(!state.isAir()&&!state.isLeavesTag()) return y+1;
            }
            return minY();
        }

        private boolean saplingSurvives(String sapling,int x,int y,int z){
            if(!Set.of("minecraft:oak_sapling","minecraft:birch_sapling","minecraft:spruce_sapling",
                    "minecraft:jungle_sapling","minecraft:acacia_sapling","minecraft:dark_oak_sapling",
                    "minecraft:cherry_sapling","minecraft:pale_oak_sapling","minecraft:mangrove_propagule")
                    .contains(sapling)) return false;
            return featureState(x,y-1,z).isSubstrateOverworld();
        }

        private boolean supportsTreeState(String exact){
            if (QUERY_ONLY_SAPLING_STATES.contains(exact)) return true;
            return capabilities.supportsExactState(exact);
        }

        private void preflightPaleMoss(){
            if(!capabilities.supportsFeature(PALE_MOSS_PATCH)) throw new UnsupportedOperationException(PALE_MOSS_PATCH);
            for(String state:List.of(PALE_MOSS_BLOCK,PALE_CARPET,SHORT_GRASS,TALL_GRASS_LOWER,TALL_GRASS_UPPER)){
                if(!capabilities.supportsExactState(state)) throw new UnsupportedOperationException(state);
            }
        }

        /** Exact index-17 configured pale_moss_patch using the caller RNG contract. */
        private Result placePaleMoss(Mc263WorldgenRandomSource random,Pos at){
            preflightPaleMoss();
            int rx=2+nextPaleInt(random,3)+1;
            int rz=2+nextPaleInt(random,3)+1;
            Set<PalePoint> surface=new HashSet<>();
            int readsBefore=queryCount();
            int writesBefore=attemptedWrites;
            for(int dx=-rx;dx<=rx;dx++){
                boolean xEdge=dx==-rx||dx==rx;
                for(int dz=-rz;dz<=rz;dz++){
                    boolean zEdge=dz==-rz||dz==rz;
                    boolean corner=xEdge&&zEdge;
                    boolean edge=(xEdge||zEdge)&&!corner;
                    if(corner||edge&&nextPaleFloat(random)>.75f) continue;
                    int py=findPaleSurface(at.x()+dx,at.y(),at.z()+dz);
                    Mc263FeatureBlockState air=featureState(at.x()+dx,py,at.z()+dz);
                    int gy=py-1;
                    Mc263FeatureBlockState ground=featureState(at.x()+dx,gy,at.z()+dz);
                    if(!air.isAir()||!ground.isFaceSturdy(Mc263FeatureBlockState.OcclusionFace.UP)) continue;
                    boolean groundPlaced=true;
                    Mc263FeatureBlockState old=featureState(at.x()+dx,gy,at.z()+dz);
                    if(!old.blockKey().equals(PALE_MOSS_BLOCK)){
                        if(!mossReplaceable(old.blockKey())) groundPlaced=false;
                        else write(at.x()+dx,gy,at.z()+dz,PALE_MOSS_BLOCK,2);
                    }
                    if(groundPlaced) surface.add(new PalePoint(at.x()+dx,gy,at.z()+dz));
                }
            }
            for(PalePoint point:surface){
                if(!(nextPaleFloat(random)<.3f)) continue;
                String selected=PALE_VEGETATION[nextPaleInt(random,PALE_VEGETATION.length)];
                int y=point.y+1;
                if(!paleCanSurvive(selected,point.x,y,point.z)) continue;
                if(selected.equals(TALL_GRASS_LOWER)){
                    Mc263FeatureBlockState above=featureState(point.x,y+1,point.z);
                    if(!above.isAir()&&!above.canBeReplaced()) continue;
                    write(point.x,y,point.z,TALL_GRASS_LOWER,2);
                    write(point.x,y+1,point.z,TALL_GRASS_UPPER,2);
                }else write(point.x,y,point.z,selected,2);
            }
            int writes=attemptedWrites-writesBefore;
            int reads=queryCount()-readsBefore;
            int retained=countRetainedSince(writesBefore);
            return new Result(!surface.isEmpty(),reads,writes,retained,0,0);
        }

        private int countRetainedSince(int attemptedBefore){
            int retained=0,seen=0;
            for(Encounter event:encounters){
                if(!event.lane.equals("MUTATION")||!event.name.equals("block")) continue;
                if(seen++<attemptedBefore) continue;
                long[] values=event.values;
                if(values.length>=5&&values[4]==1) retained++;
            }
            return retained;
        }
        private int queryCount(){
            int count=0;for(Encounter event:encounters)if(event.lane.equals("QUERY"))count++;return count;
        }
        private int findPaleSurface(int x,int y,int z){
            int py=y,moved=0;
            while(featureState(x,py,z).isAir()&&moved++<5) py--;
            moved=0;
            while(!featureState(x,py,z).isAir()&&moved++<5) py++;
            return py;
        }
        private boolean paleCanSurvive(String exact,int x,int y,int z){
            Mc263FeatureBlockState support=featureState(x,y-1,z);
            String block=blockKey(exact);
            boolean result=switch(block){
                case "minecraft:short_grass","minecraft:tall_grass" -> support.supportsVegetationTag();
                case "minecraft:pale_moss_carpet" -> !support.isAir();
                default -> false;
            };
            encounters.add(new Encounter("QUERY","pale_survive",block,x,y,z,result?1:0));
            return result;
        }
        private int nextPaleInt(Mc263WorldgenRandomSource random,int bound){
            int value=random.nextInt(bound);trace("pale","rng_int",bound,value);return value;
        }
        private float nextPaleFloat(Mc263WorldgenRandomSource random){
            float value=random.nextFloat();trace("pale","rng_float",Float.floatToRawIntBits(value));return value;
        }

        private void finishTree(Set<TreePoint> logs,Set<TreePoint> leaves,Set<TreePoint> roots,Set<TreePoint> decorations){
            Set<TreePoint> all=new HashSet<>();all.addAll(logs);all.addAll(leaves);all.addAll(roots);all.addAll(decorations);
            if(all.isEmpty()) return;
            int minX=all.stream().mapToInt(TreePoint::x).min().orElseThrow();
            int maxX=all.stream().mapToInt(TreePoint::x).max().orElseThrow();
            int minY=all.stream().mapToInt(TreePoint::y).min().orElseThrow();
            int maxY=all.stream().mapToInt(TreePoint::y).max().orElseThrow();
            int minZ=all.stream().mapToInt(TreePoint::z).min().orElseThrow();
            int maxZ=all.stream().mapToInt(TreePoint::z).max().orElseThrow();
            Set<TreePoint> shape=new HashSet<>();shape.addAll(roots);shape.addAll(decorations);
            List<Set<TreePoint>> queues=new ArrayList<>(7);for(int i=0;i<7;i++)queues.add(new HashSet<>());
            queues.get(0).addAll(logs);int distance=0;
            while(distance<7){
                if(queues.get(distance).isEmpty()){distance++;continue;}
                TreePoint pos=queues.get(distance).iterator().next();queues.get(distance).remove(pos);
                if(!inside(pos,minX,maxX,minY,maxY,minZ,maxZ))continue;
                if(distance!=0){
                    Mc263FeatureBlockState state=featureState(pos.x,pos.y,pos.z);
                    String current=property(state.exactState(),"distance");
                    if(!current.isEmpty()){
                        Map<String,String> props=new TreeMap<>(exactProperties(state.exactState()));props.put("distance",Integer.toString(distance));
                        write(pos.x,pos.y,pos.z,canonical(state.blockKey(),props),19);
                    }
                }
                shape.add(pos);
                for(int[] d:DIRECTIONS){
                    TreePoint n=new TreePoint(pos.x+d[0],pos.y+d[1],pos.z+d[2]);
                    if(!inside(n,minX,maxX,minY,maxY,minZ,maxZ)||shape.contains(n))continue;
                    String old=property(featureState(n.x,n.y,n.z).exactState(),"distance");
                    if(old.isEmpty())continue;int next=Math.min(Integer.parseInt(old),distance+1);
                    if(next<7){queues.get(next).add(n);distance=Math.min(distance,next);}
                }
            }
            updateTreeShapeFaces(shape,minX,maxX,minY,maxY,minZ,maxZ);
        }

        private void updateTreeShapeFaces(Set<TreePoint> shape,int minX,int maxX,int minY,int maxY,int minZ,int maxZ){
            for(int x=minX;x<=maxX;x++)for(int y=minY;y<=maxY;y++)scanTreeFaceLine(shape,new TreePoint(x,y,minZ),0,0,1,maxZ-minZ+1,2,3);
            for(int z=minZ;z<=maxZ;z++)for(int x=minX;x<=maxX;x++)scanTreeFaceLine(shape,new TreePoint(x,minY,z),0,1,0,maxY-minY+1,0,1);
            for(int y=minY;y<=maxY;y++)for(int z=minZ;z<=maxZ;z++)scanTreeFaceLine(shape,new TreePoint(minX,y,z),1,0,0,maxX-minX+1,4,5);
        }
        private void scanTreeFaceLine(Set<TreePoint> shape,TreePoint start,int dx,int dy,int dz,int length,int neg,int posDir){
            boolean previous=false;TreePoint previousPos=start;
            for(int i=0;i<=length;i++){
                TreePoint current=new TreePoint(start.x+dx*i,start.y+dy*i,start.z+dz*i);
                boolean full=i!=length&&shape.contains(current);
                if(!previous&&full)updateTreeShapeFace(current,neg);
                if(previous&&!full)updateTreeShapeFace(previousPos,posDir);
                previous=full;previousPos=current;
            }
        }
        private void updateTreeShapeFace(TreePoint pos,int direction){
            int[] d=DIRECTIONS[direction];TreePoint n=new TreePoint(pos.x+d[0],pos.y+d[1],pos.z+d[2]);
            Mc263FeatureBlockState state=featureState(pos.x,pos.y,pos.z);Mc263FeatureBlockState neighbor=featureState(n.x,n.y,n.z);
            String next=updateTreeShapeState(pos,state,direction,neighbor);
            if(!state.exactState().equals(next)){write(pos.x,pos.y,pos.z,next,2);state=featureState(pos.x,pos.y,pos.z);}
            int opposite=direction^1;String neighborNext=updateTreeShapeState(n,neighbor,opposite,state);
            if(!neighbor.exactState().equals(neighborNext))write(n.x,n.y,n.z,neighborNext,2);
        }
        private String updateTreeShapeState(TreePoint pos,Mc263FeatureBlockState state,int direction,Mc263FeatureBlockState neighbor){
            Map<String,String> props=exactProperties(state.exactState());
            if(state.isLeavesTag()){
                if(state.fluidKind()==Mc263FeatureBlockState.FluidKind.WATER_SOURCE)scheduleFluidTick(pos.x,pos.y,pos.z,"minecraft:water",5);
                int neighborDistance=neighbor.isLogsTag()?0:parseLeafDistance(neighbor.exactState());int candidate=neighborDistance+1;int own=parseLeafDistance(state.exactState());
                if(candidate!=1||own!=candidate)scheduleBlockTick(pos.x,pos.y,pos.z,state.blockKey(),1);
                return state.exactState();
            }
            if(state.blockKey().equals("minecraft:leaf_litter")){
                return featureState(pos.x,pos.y-1,pos.z).isFaceSturdy(Mc263FeatureBlockState.OcclusionFace.UP)?state.exactState():"minecraft:air";
            }
            if(state.blockKey().equals("minecraft:pale_hanging_moss")){
                Mc263FeatureBlockState above=featureState(pos.x,pos.y+1,pos.z);
                boolean supported=above.blockKey().equals("minecraft:pale_hanging_moss")||above.isFaceSturdy(Mc263FeatureBlockState.OcclusionFace.DOWN);
                if(!supported)scheduleBlockTick(pos.x,pos.y,pos.z,"minecraft:pale_hanging_moss",1);
                boolean tip=!featureState(pos.x,pos.y-1,pos.z).blockKey().equals("minecraft:pale_hanging_moss");props.put("tip",Boolean.toString(tip));
                return canonical(state.blockKey(),props);
            }
            String directionKey=switch(direction){case 0->"down";case 1->"up";case 2->"north";case 3->"south";case 4->"west";case 5->"east";default->throw new IllegalArgumentException("tree direction "+direction);};
            if(state.blockKey().equals("minecraft:cocoa")&&directionKey.equals(props.get("facing"))&&!neighbor.isJungleLogsTag())return "minecraft:air";
            if(state.blockKey().equals("minecraft:shelf_mushroom")&&oppositeDirection(directionKey).equals(props.get("facing"))&&!neighbor.isFaceSturdy(oppositeFace(direction)))return "minecraft:air";
            if(state.blockKey().equals("minecraft:vine")&&direction!=0){
                if(props.getOrDefault("up","false").equals("true")){boolean supported=featureState(pos.x,pos.y+1,pos.z).isSupportOrCollisionFull(Mc263FeatureBlockState.OcclusionFace.DOWN);props.put("up",Boolean.toString(supported));}
                Mc263FeatureBlockState above=featureState(pos.x,pos.y+1,pos.z);
                for(int horizontal=2;horizontal<DIRECTIONS.length;horizontal++){
                    String face=switch(horizontal){case 2->"north";case 3->"south";case 4->"west";case 5->"east";default->throw new AssertionError(horizontal);};
                    if(!props.getOrDefault(face,"false").equals("true"))continue;int[] delta=DIRECTIONS[horizontal];
                    boolean supported=featureState(pos.x+delta[0],pos.y+delta[1],pos.z+delta[2]).isSupportOrCollisionFull(oppositeFace(horizontal));
                    if(!supported&&above.blockKey().equals("minecraft:vine"))supported=property(above.exactState(),face).equals("true");props.put(face,Boolean.toString(supported));
                }
                boolean any=props.values().stream().anyMatch("true"::equals);return any?canonical(state.blockKey(),props):"minecraft:air";
            }
            return state.exactState();
        }
    }

    private static State commonState(Mc263FeatureBlockState state){
        return new State(state.blockKey(),exactProperties(state.exactState()),state.isAir(),state.replaceableByTrees(),state.isLogsTag(),state.isLeavesTag(),property(state.exactState(),"persistent").equals("true"),state.fluidKind()==Mc263FeatureBlockState.FluidKind.WATER_SOURCE,state.isSolidRender());
    }
    private static Mc263PoplarTreeFeature.State poplarState(Mc263FeatureBlockState state){
        return new Mc263PoplarTreeFeature.State(state.blockKey(),exactProperties(state.exactState()),state.isAir(),state.replaceableByTrees(),state.canBeReplaced(),state.isLogsTag(),state.isLeavesTag(),property(state.exactState(),"persistent").equals("true"),state.fluidKind()==Mc263FeatureBlockState.FluidKind.WATER_SOURCE,state.isSolidRender());
    }
    private static Set<TreePoint> commonPoints(Set<Pos> source){Set<TreePoint> out=new HashSet<>();for(Pos p:source)out.add(new TreePoint(p.x(),p.y(),p.z()));return out;}
    private static Set<TreePoint> poplarPoints(Set<Mc263PoplarTreeFeature.Pos> source){Set<TreePoint> out=new HashSet<>();for(Mc263PoplarTreeFeature.Pos p:source)out.add(new TreePoint(p.x(),p.y(),p.z()));return out;}
    private static boolean inside(TreePoint p,int minX,int maxX,int minY,int maxY,int minZ,int maxZ){return p.x>=minX&&p.x<=maxX&&p.y>=minY&&p.y<=maxY&&p.z>=minZ&&p.z<=maxZ;}
    private static int parseLeafDistance(String exact){String value=property(exact,"distance");return value.isEmpty()?7:Integer.parseInt(value);}
    private static String oppositeDirection(String d){return switch(d){case"down"->"up";case"up"->"down";case"north"->"south";case"south"->"north";case"west"->"east";case"east"->"west";default->throw new IllegalArgumentException(d);};}
    private static Mc263FeatureBlockState.OcclusionFace oppositeFace(int direction){return switch(direction^1){case 0->Mc263FeatureBlockState.OcclusionFace.DOWN;case 1->Mc263FeatureBlockState.OcclusionFace.UP;case 2->Mc263FeatureBlockState.OcclusionFace.NORTH;case 3->Mc263FeatureBlockState.OcclusionFace.SOUTH;case 4->Mc263FeatureBlockState.OcclusionFace.WEST;case 5->Mc263FeatureBlockState.OcclusionFace.EAST;default->throw new IllegalArgumentException("tree direction "+direction);};}
    private static boolean mossReplaceable(String key){return switch(key){case "minecraft:stone","minecraft:granite","minecraft:diorite","minecraft:andesite","minecraft:tuff","minecraft:deepslate","minecraft:cave_vines","minecraft:cave_vines_plant","minecraft:dirt","minecraft:grass_block","minecraft:podzol","minecraft:coarse_dirt","minecraft:mycelium","minecraft:rooted_dirt","minecraft:moss_block","minecraft:pale_moss_block","minecraft:mud","minecraft:muddy_mangrove_roots"->true;default->false;};}
    private static Map<String,String> exactProperties(String exact){int open=exact.indexOf('[');Map<String,String> out=new HashMap<>();if(open>=0){for(String p:exact.substring(open+1,exact.length()-1).split(",")){int eq=p.indexOf('=');out.put(p.substring(0,eq),p.substring(eq+1));}}return out;}
    private static String property(String exact,String key){return exactProperties(exact).getOrDefault(key,"");}
    private static String blockKey(String exact){int open=exact.indexOf('[');return open<0?exact:exact.substring(0,open);}
    private static String canonical(String block,Map<String,String> properties){if(properties.isEmpty())return block;return block+new TreeMap<>(properties).entrySet().stream().map(e->e.getKey()+"="+e.getValue()).reduce("[",(a,b)->a.equals("[")?a+b:a+","+b)+"]";}
    private static String normalize(String key){Objects.requireNonNull(key,"configured key");return key.startsWith("minecraft:")?key:"minecraft:"+key;}
    private static String[] paleVegetation(){String[] values=new String[60];int at=0;for(int i=0;i<25;i++)values[at++]=PALE_CARPET;for(int i=0;i<25;i++)values[at++]=SHORT_GRASS;for(int i=0;i<10;i++)values[at++]=TALL_GRASS_LOWER;return values;}

    private static final class Point {
        final int x,y,z;Point(int x,int y,int z){this.x=x;this.y=y;this.z=z;}
        @Override public boolean equals(Object o){return o instanceof Point p&&x==p.x&&y==p.y&&z==p.z;}
        @Override public int hashCode(){return (y+z*31)*31+x;}
        @Override public String toString(){return x+","+y+","+z;}
    }
    private static final class TreePoint {
        final int x,y,z;TreePoint(int x,int y,int z){this.x=x;this.y=y;this.z=z;}
        int x(){return x;}int y(){return y;}int z(){return z;}
        @Override public boolean equals(Object o){return o instanceof TreePoint p&&x==p.x&&y==p.y&&z==p.z;}
        @Override public int hashCode(){return (y+z*31)*31+x;}
    }
    private static final class PalePoint {
        final int x,y,z;PalePoint(int x,int y,int z){this.x=x;this.y=y;this.z=z;}
        @Override public boolean equals(Object o){return o instanceof PalePoint p&&x==p.x&&y==p.y&&z==p.z;}
        @Override public int hashCode(){return (y+z*31)*31+x;}
    }
    private static final class TickIdentity {
        final int x,y,z;final String key;TickIdentity(int x,int y,int z,String key){this.x=x;this.y=y;this.z=z;this.key=key;}
        @Override public boolean equals(Object o){return o instanceof TickIdentity t&&x==t.x&&y==t.y&&z==t.z&&key.equals(t.key);}
        @Override public int hashCode(){return Objects.hash(x,y,z,key);}
    }

    private static byte[] encodeReceipt(Mapping mapping,Origin origin,long owner,WriteDomain domain,
            boolean placed,int attemptedWrites,AtomicBatch batch,List<Encounter> encounters){
        try{
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();DataOutputStream out=new DataOutputStream(bytes);
            out.writeInt(RECEIPT_MAGIC);out.writeShort(RECEIPT_VERSION);writeString(out,RECEIPT_SCHEMA);
            out.writeInt(mapping.ordinal);writeString(out,mapping.poolKey);writeString(out,mapping.configuredKey);
            writeString(out,mapping.targetKey);writeHex(out,mapping.placedCodecSha256);writeHex(out,mapping.configuredCodecSha256);
            writeHex(out,CONFIG_ORDER_SHA256);writeHex(out,DOMAIN_SHA256);writeHex(out,CAPABILITY_SHA256);writeHex(out,ORACLE_SHA256);
            out.writeInt(origin.x);out.writeInt(origin.y);out.writeInt(origin.z);out.writeLong(owner);out.writeBoolean(placed);out.writeInt(attemptedWrites);
            out.writeInt(domain.boxes.size());for(Box box:domain.boxes){out.writeInt(box.minX);out.writeInt(box.minY);out.writeInt(box.minZ);out.writeInt(box.maxX);out.writeInt(box.maxY);out.writeInt(box.maxZ);}
            out.writeInt(encounters.size());for(Encounter event:encounters){writeString(out,event.lane);writeString(out,event.name);writeString(out,event.text);out.writeInt(event.values.length);for(long value:event.values)out.writeLong(value);}
            out.writeInt(batch.blocks.size());for(BlockWrite w:batch.blocks){out.writeInt(w.x);out.writeInt(w.y);out.writeInt(w.z);writeString(out,w.exactState);out.writeInt(w.flags);out.writeLong(w.owner);}
            out.writeInt(batch.bent.size());for(BentPayload b:batch.bent){out.writeInt(b.x);out.writeInt(b.y);out.writeInt(b.z);writeString(out,b.blockIdentity);writeString(out,b.entityType);}
            out.writeInt(batch.bees.size());for(BeePayload b:batch.bees){out.writeInt(b.x);out.writeInt(b.y);out.writeInt(b.z);out.writeInt(b.ticksInHive);}
            out.writeInt(batch.blockTicks.size());for(BlockTick t:batch.blockTicks){out.writeInt(t.x);out.writeInt(t.y);out.writeInt(t.z);writeString(out,t.blockKey);out.writeInt(t.delay);}
            out.writeInt(batch.fluidTicks.size());for(FluidTick t:batch.fluidTicks){out.writeInt(t.x);out.writeInt(t.y);out.writeInt(t.z);writeString(out,t.fluidKey);out.writeInt(t.delay);}
            out.writeInt(batch.postprocess.size());for(PostprocessMark p:batch.postprocess){out.writeInt(p.x);out.writeInt(p.y);out.writeInt(p.z);}
            out.flush();return bytes.toByteArray();
        }catch(IOException impossible){throw new AssertionError(impossible);}
    }
    private static void writeString(DataOutputStream out,String value)throws IOException{byte[] bytes=value.getBytes(StandardCharsets.UTF_8);if(bytes.length>0xffff)throw new IllegalArgumentException("receipt string too long");out.writeShort(bytes.length);out.write(bytes);}
    private static void writeHex(DataOutputStream out,String hex)throws IOException{if(hex.length()!=64)throw new IllegalArgumentException("SHA-256 hex length");for(int i=0;i<64;i+=2)out.writeByte(Integer.parseInt(hex.substring(i,i+2),16));}
    private static String sha256(byte[] bytes){try{return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}catch(NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}}

    private static List<Mapping> buildMappings(){
        List<Mapping> m=new ArrayList<>();
        add(m,0,"acacia","minecraft:acacia_checked","minecraft:acacia","27a7ded7b0c2367f2924896351aaa89f240428ed843bd1a796b1645c66ca485f","d621f731b5802ba9c81bdd6ce53ab76385c1c29c1175efc85c9870bb24021a7e",Family.COMPLEX_TREE);
        add(m,1,"birch","minecraft:birch_checked","minecraft:birch","4c680e964ec7580bce6df993867c347d8c4caeddd7281014bcdcb94b8b7a6889","84b5758c4446dada7f131a4916fe55f077a1ab4c3f7c3900c34ce9b9927f1b3e",Family.COMPLEX_TREE);
        add(m,2,"fancy_oak","minecraft:fancy_oak_checked","minecraft:fancy_oak","4889fc521432030e40015017ff080bb0468fef5a15c28ef461c35ae17e90778a","eb507602e89d05ae3c631fbf484e56f4e930e74297d196ff14e3307fa99d4700",Family.COMPLEX_TREE);
        add(m,3,"oak","minecraft:oak_checked","minecraft:oak","865e84a9a9c0797102fe1d8ca0a0215b4b5a157af92389e544496b615160850e","51e2db4592fe4697e14e7956bbb078c16fe22446b1290093abbf33e9b8071bf1",Family.COMPLEX_TREE);
        add(m,4,"spruce","minecraft:spruce_checked","minecraft:spruce","d8b5aadcc133973dc88e5856d68f61d17a4d05e128f1733821f257d8136e9583","a46d8d6a421e5278ad8d833a6c9a42a796936188a42ad73b940b23a35e090493",Family.COMPLEX_TREE);
        add(m,5,"thick_spruce","minecraft:mega_spruce_checked","minecraft:mega_spruce","eb8d5ec44a16474587e85e99588c8d6a42fec689a9757d76cf1ef65d294b0d9b","bd761ee9c8c502ea90ac38ce193e4d783da59434d2c286f400a6186cf23356ee",Family.COMPLEX_TREE);
        add(m,6,"yellow_poplar","minecraft:yellow_poplar","minecraft:yellow_poplar","1bb42c48451d1945bac7114f07cfdbfba10896920a331a67d2c56564bd77f3f9","8259c5d7603c984b2b60062f97c90988fe61b354e9988123a58d9c943f9c9dd5",Family.POPLAR_TREE);
        add(m,7,"orange_poplar","minecraft:orange_poplar","minecraft:orange_poplar","83ca916afffc358121c4d27139b71c302813bf8068dfe1fdc3a66f2a61767253","67137233923e729bc706721d889ff0e9871b6a45e47b1145ed68ffead28c695a",Family.POPLAR_TREE);
        add(m,8,"red_poplar","minecraft:red_poplar","minecraft:red_poplar","5ee83ea4ebb5a555d3d103fafc95471d833c1c88cf857fb16848c255875ad68e","bc1871697995bf9d84ce52d02df22b6debf891dd845f2192520d2ad7c8b8fa17",Family.POPLAR_TREE);
        add(m,9,"super_birch_bees","minecraft:super_birch_bees_0002","minecraft:super_birch_bees_0002","c841df020ac2c08e9cdb6717c389f6479e74e43602564ecd71d051964c8382f9","ae0f1d01959933df8c7669e788bbc2c60a4ab0a425568bd36c95e4d8c5b7d4fd",Family.COMMON_TREE);
        add(m,10,"spruce_on_snow","minecraft:spruce_on_snow","minecraft:spruce","6465b7e18b4729a21916a2c17c9c75d14ce658542cddec8facae1e5d584f96f9","a46d8d6a421e5278ad8d833a6c9a42a796936188a42ad73b940b23a35e090493",Family.COMPLEX_TREE);
        add(m,11,"fancy_oak_bees","minecraft:fancy_oak_bees_002","minecraft:fancy_oak_bees_002","110637a9794fcd4eaf838409d494c48aa492d0ec1bb1c93ee1267b9d163833d5","4d63c6a871db8b34196dca17952a902e01c210e6816b9b1c0893988073ee7657",Family.COMPLEX_TREE);
        add(m,12,"birch_bees","minecraft:birch_bees_002","minecraft:birch_bees_002","07f0312fb273c78120776a0b7485a9cfc3f85f6154d81cfb14000e6f48751d7a","c22719c2fabdc9e3a74335aa1857e2eeb52e5753f8a784bb1d052795f735b9a1",Family.COMMON_TREE);
        add(m,13,"pale_oak","minecraft:pale_oak_checked","minecraft:pale_oak","eeffcd3af2760902798063217d408a531d7d6229b1dbc4a178b1c2714fc0b588","3f5c7ea1f99dbf891049ee0be1b7a8d1c6174af0ee7809a6034d291794c84ea5",Family.COMPLEX_TREE);
        add(m,14,"bamboo","minecraft:bamboo_in_structure","minecraft:bamboo_no_podzol","1ff871f29b6266180ebb3b9a32bb0ff9172620a4c2d891512b95c2748332a33e","707a3163df2302f56db7268b6ae4960679aea732b64c36a5bdcc0b8f84f94199",Family.BAMBOO);
        add(m,15,"jungle","minecraft:jungle_tree","minecraft:jungle_tree","13ad4328c235476daa3669df76b87fedf14246651952d17aa6fefbd7f5fa0453","951c09c5b17a9d02f92dc32b8a646832f1a6a741b1fc66608bd151a2d5ef5132",Family.COMPLEX_TREE);
        add(m,16,"pine","minecraft:pine_checked","minecraft:pine","6b1450d7098a36fb64aa029e9842432826003a1cb7b2703fe2fd363db12c001e","76ef3f85e5359267c5ca004655888f05188e84e72f37b97769c1452273a44f7c",Family.COMPLEX_TREE);
        add(m,17,"mega_pine","minecraft:mega_pine_checked","minecraft:mega_pine","1b452b8a0e34e8c7b8495ba8b19badc45b72faee5afc4663af2c5ac493e7267d","9529b1be455e652643aa3f782a3db0bd509f14d149846c7f268866ecb202b5a3",Family.COMPLEX_TREE);
        add(m,18,"mega_jungle","minecraft:mega_jungle_tree_checked","minecraft:mega_jungle_tree","02a55699788ee113dbce38bc547ab4d71d079d5920f21721640aced19a705987","514060d7cdd88dd8f3d96ddca43bdf31cfe96e32798a6ea2e4cca21ca94b06d7",Family.COMPLEX_TREE);
        add(m,19,"cherry","minecraft:cherry_checked","minecraft:cherry","374e564355745dd287f6ec969db0938d8638cb1b36b9ebc1c25f8f7f65d654ff","1236e3c3e1064d67046f1be6ca3b384ec7ddc193ba4cd7f2ed15f0634b61bd34",Family.COMPLEX_TREE);
        add(m,20,"cherry_bees","minecraft:cherry_bees_005","minecraft:cherry_bees_005","9b48688e2bc86dc77f00b24a1a8447d2111f05fc1e9a645e2d148e91946aeb69","45787953a5d23181fd76d4f1327c289a16b43bf223d1b1c22d5078c559ae0894",Family.COMPLEX_TREE);
        return List.copyOf(m);
    }
    private static void add(List<Mapping> list,int ordinal,String pool,String key,String target,String placed,String configured,Family family){list.add(new Mapping(ordinal,"minecraft:abandoned_camp/trees/"+pool,key,target,placed,configured,family));}
    private static Map<String,Mapping> indexMappings(List<Mapping> mappings){Map<String,Mapping> out=new LinkedHashMap<>();for(Mapping mapping:mappings){if(mapping.ordinal!=out.size()||out.put(mapping.configuredKey,mapping)!=null)throw new ExceptionInInitializerError("Camp configured mapping drift");}if(out.size()!=21)throw new ExceptionInInitializerError("Camp configured mapping count");return Collections.unmodifiableMap(out);}
}
