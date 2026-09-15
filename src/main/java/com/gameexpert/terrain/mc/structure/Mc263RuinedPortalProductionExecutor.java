package com.gameexpert.terrain.mc.structure;

import com.gameexpert.terrain.Blocks;
import com.gameexpert.terrain.mc.feature.Mc263FeatureBlockState;
import com.gameexpert.terrain.mc.feature.Mc263FinalChunkSidecars;
import com.gameexpert.terrain.mc.feature.Mc263FinalChunkSidecars.BlockEntity;
import com.gameexpert.terrain.mc.feature.Mc263FinalChunkSidecars.FluidTick;
import com.gameexpert.terrain.mc.feature.Mc263FinalChunkSidecars.Loot;
import com.gameexpert.terrain.mc.feature.Mc263FinalChunkSidecars.TickPriority;
import com.gameexpert.terrain.mc.structure.Mc263RuinedPortalGrammarData.Command;
import com.gameexpert.terrain.mc.structure.Mc263RuinedPortalGrammarData.Grammar;
import com.gameexpert.terrain.mc.structure.Mc263RuinedPortalGrammarData.Op;
import com.gameexpert.terrain.mc.structure.Mc263RuinedPortalGrammarData.Semantic;
import com.gameexpert.terrain.mc.structure.Mc263RuinedPortalProgram.BlockPos;
import com.gameexpert.terrain.mc.structure.Mc263RuinedPortalProgram.BoundingBox;
import com.gameexpert.terrain.mc.structure.Mc263RuinedPortalProgram.Heightmap;
import com.gameexpert.terrain.mc.structure.Mc263RuinedPortalProgram.Mirror;
import com.gameexpert.terrain.mc.structure.Mc263RuinedPortalProgram.Plan;
import com.gameexpert.terrain.mc.structure.Mc263RuinedPortalProgram.Rotation;
import com.gameexpert.terrain.mc.structure.Mc263RuinedPortalProgram.VerticalPlacement;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/** Processor-ordered, clipped and atomically committed production executor for ruined portals. */
public final class Mc263RuinedPortalProductionExecutor {
    private static final byte[] RECEIPT_MAGIC = "RUP263C2".getBytes(StandardCharsets.US_ASCII);
    private static final String CHEST = "minecraft:chest";
    private static final String LAVA = "minecraft:lava";
    private static final String FLOWING_LAVA = "minecraft:flowing_lava";
    private static final String WATER = "minecraft:water";
    private static final String NETHERRACK = "minecraft:netherrack";
    private static final String MAGMA = "minecraft:magma_block";
    private static final String AIR = "minecraft:air";
    private static final String OBSIDIAN = "minecraft:obsidian";
    private static final int OVERWORLD_SEA_LEVEL = 63;
    private static final List<Float> NETHERRACK_PROBABILITY = List.of(
            1F, 1F, 1F, 1F, 1F, 1F, 1F, .9F, .9F, .8F, .7F, .6F, .4F, .2F);
    private static final Set<String> OFFICIAL_QUERY_OPERATIONS = Set.of(
            "getBlockState", "getFluidState", "getBlockEntity", "isEmptyBlock",
            "getHeight:WORLD_SURFACE_WG", "getHeight:OCEAN_FLOOR_WG");

    private Mc263RuinedPortalProductionExecutor() { }

    public interface WorldAccess {
        boolean supportsAtomicSettlement();
        boolean supportsExactState(String exactState);
        boolean supportsLootTable(String lootTable);
        boolean supportsBlockEntity(String blockIdentity, String entityType);
        String blockState(BlockPos position);
        String fluidState(BlockPos position);
        boolean canFeatureReplace(BlockPos position);
        boolean isFaceFull(BlockPos position, Direction face);
        int height(Heightmap heightmap, int x, int z);
        void settle(AtomicSettlement settlement);
    }

    public enum Direction { NORTH, EAST, SOUTH, WEST }
    private enum ShapeDirection {
        WEST(-1, 0, 0), EAST(1, 0, 0), NORTH(0, 0, -1), SOUTH(0, 0, 1),
        DOWN(0, -1, 0), UP(0, 1, 0);
        private final int dx, dy, dz;
        ShapeDirection(int dx, int dy, int dz) { this.dx = dx; this.dy = dy; this.dz = dz; }
        BlockPos move(BlockPos p) { return p.offset(dx, dy, dz); }
        boolean horizontal() { return dy == 0; }
        ShapeDirection opposite() {
            return switch (this) {
                case WEST -> EAST; case EAST -> WEST; case NORTH -> SOUTH; case SOUTH -> NORTH;
                case DOWN -> UP; case UP -> DOWN;
            };
        }
    }
    public record Clip(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        public Clip {
            if (minX > maxX || minY > maxY || minZ > maxZ)
                throw new IllegalArgumentException("inverted ruined-portal clip");
        }
        public boolean contains(BlockPos p) {
            return p.x() >= minX && p.x() <= maxX && p.y() >= minY && p.y() <= maxY
                    && p.z() >= minZ && p.z() <= maxZ;
        }
        public Clip encompass(BoundingBox box) {
            return new Clip(Math.min(minX, box.minX()), Math.min(minY, box.minY()),
                    Math.min(minZ, box.minZ()), Math.max(maxX, box.maxX()),
                    Math.max(maxY, box.maxY()), Math.max(maxZ, box.maxZ()));
        }
        public static Clip chunk(int chunkX, int chunkZ, int minY, int maxY) {
            int x = Math.multiplyExact(chunkX, 16), z = Math.multiplyExact(chunkZ, 16);
            return new Clip(x, minY, z, x + 15, maxY, z + 15);
        }
    }
    public record BlockWrite(BlockPos position, String exactState, int flags, int encounterOrder) { }
    public record Query(String operation, BlockPos position, String result, int encounterOrder) { }

    /** Ordered actual WorldGenLevel semantic query identity; deliberately separate from diagnostics. */
    public static final class OfficialQuery {
        private final String operation;
        private final BlockPos position;

        public OfficialQuery(String operation, BlockPos position) {
            this.operation = Objects.requireNonNull(operation, "ruined-portal official operation");
            this.position = Objects.requireNonNull(position, "ruined-portal official position");
            if (!OFFICIAL_QUERY_OPERATIONS.contains(operation))
                throw new IllegalArgumentException("unknown ruined-portal official operation: " + operation);
            if (operation.startsWith("getHeight:") && position.y() != 0)
                throw new IllegalArgumentException("malformed ruined-portal official height position");
        }
        public String operation() { return operation; }
        public BlockPos position() { return position; }
        @Override public boolean equals(Object other) {
            return other instanceof OfficialQuery value && operation.equals(value.operation)
                    && position.equals(value.position);
        }
        @Override public int hashCode() { return Objects.hash(operation, position); }
        @Override public String toString() { return operation + "@" + position; }
    }
    public record LootWrite(BlockPos position, String table, long seed, int encounterOrder) { }
    public record BlockEntityWrite(BlockPos position, String blockIdentity, String entityType,
            byte[] canonicalNbt, int encounterOrder) {
        public BlockEntityWrite { canonicalNbt = canonicalNbt.clone(); }
        @Override public byte[] canonicalNbt() { return canonicalNbt.clone(); }
    }
    public record FluidTickWrite(BlockPos position, String key, int delay, int priority,
            long subTickOrder) {
        public FluidTickWrite {
            Objects.requireNonNull(position, "ruined-portal FTIK position");
            Objects.requireNonNull(key, "ruined-portal FTIK key");
            int expectedDelay = fluidTickDelay(key);
            if (delay != expectedDelay)
                throw new IllegalArgumentException("ruined-portal FTIK key/delay mismatch");
            if (priority != 0)
                throw new IllegalArgumentException("ruined-portal FTIK priority must be NORMAL/0");
            if (subTickOrder < 0)
                throw new IllegalArgumentException("negative ruined-portal FTIK subTickOrder");
        }
        int encounterOrder() { return Math.toIntExact(subTickOrder); }
    }

    public static final class AtomicSettlement {
        private final Plan successor;
        private final List<Query> queries;
        private final List<OfficialQuery> officialQueries;
        private final List<BlockWrite> writes;
        private final List<LootWrite> loot;
        private final List<BlockEntityWrite> blockEntities;
        private final List<FluidTickWrite> fluidTicks;
        private final List<FluidTickWrite> officialFluidTicks;
        private final byte[] randomContinuation;
        private final byte[] frozenReceipt;

        private AtomicSettlement(Plan successor, List<Query> queries, List<OfficialQuery> officialQueries,
                List<BlockWrite> writes, List<LootWrite> loot, List<BlockEntityWrite> blockEntities,
                List<FluidTickWrite> fluidTicks, List<FluidTickWrite> officialFluidTicks,
                byte[] randomContinuation) {
            validateFluidTicks(fluidTicks); validateFluidTicks(officialFluidTicks);
            validateOfficialQueries(officialQueries);
            this.successor = successor; this.queries = List.copyOf(queries);
            this.officialQueries = List.copyOf(officialQueries); this.writes = List.copyOf(writes);
            this.loot = List.copyOf(loot); this.blockEntities = List.copyOf(blockEntities);
            this.fluidTicks = List.copyOf(fluidTicks);
            this.officialFluidTicks = List.copyOf(officialFluidTicks);
            this.randomContinuation = randomContinuation.clone(); this.frozenReceipt = freeze();
        }
        public Plan successor() { return successor; }
        public List<Query> queries() { return queries; }
        public List<OfficialQuery> officialQueries() { return officialQueries; }
        public int officialQueryCount() { return officialQueries.size(); }
        public String officialQuerySha256() { return canonicalOfficialQuerySha256(officialQueries); }
        public List<BlockWrite> writes() { return writes; }
        public List<LootWrite> loot() { return loot; }
        public List<BlockEntityWrite> blockEntities() { return blockEntities; }
        public List<FluidTickWrite> fluidTicks() { return fluidTicks; }
        /** Raw official scheduleTick(Fluid) rows in source encounter order. */
        public List<FluidTickWrite> officialFluidTicks() { return officialFluidTicks; }
        public int officialFluidTickCount() { return officialFluidTicks.size(); }
        public String officialFluidTickSha256() {
            return canonicalOfficialFluidTickSha256(officialFluidTicks);
        }
        public byte[] randomContinuation() { return randomContinuation.clone(); }
        public byte[] frozenReceipt() { return frozenReceipt.clone(); }
        public String frozenReceiptSha256() { return sha256(frozenReceipt); }

        public Mc263FinalChunkSidecars schema4Sidecars(int chunkX, int chunkZ) {
            ArrayList<Loot> lootLane = new ArrayList<>();
            ArrayList<BlockEntity> bent = new ArrayList<>();
            ArrayList<FluidTick> ftik = new ArrayList<>();
            for (LootWrite value : loot) if (inChunk(value.position(), chunkX, chunkZ))
                lootLane.add(new Loot(packed(value.position(), chunkX, chunkZ),
                        facing(value.position()), value.table(), value.seed()));
            for (BlockEntityWrite value : blockEntities)
                if (inChunk(value.position(), chunkX, chunkZ))
                    bent.add(new BlockEntity(packed(value.position(), chunkX, chunkZ),
                            value.blockIdentity(), value.entityType(), value.canonicalNbt()));
            ftik.addAll(schema4FluidTicks(fluidTicks, chunkX, chunkZ));
            return new Mc263FinalChunkSidecars(List.of(), ftik, lootLane, List.of(), List.of(),
                    List.of(), List.of(), bent, List.of());
        }
        private String facing(BlockPos position) {
            for (BlockWrite write : writes) if (write.position().equals(position)
                    && blockKey(write.exactState()).equals(CHEST))
                return properties(write.exactState()).getOrDefault("facing", "north");
            throw new IllegalStateException("ruined-portal LOOT lacks chest write");
        }
        private byte[] freeze() {
            try {
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                try (DataOutputStream out = new DataOutputStream(bytes)) {
                    out.write(RECEIPT_MAGIC); out.writeUTF(successor.structureKey());
                    out.writeInt(successor.chunkX()); out.writeInt(successor.chunkZ());
                    out.writeInt(queries.size());
                    for (Query value : queries) {
                        out.writeUTF(value.operation()); position(out, value.position());
                        out.writeUTF(value.result()); out.writeInt(value.encounterOrder());
                    }
                    out.writeInt(writes.size());
                    for (BlockWrite value : writes) {
                        position(out, value.position()); out.writeUTF(value.exactState());
                        out.writeInt(value.flags()); out.writeInt(value.encounterOrder());
                    }
                    out.writeInt(loot.size());
                    for (LootWrite value : loot) {
                        position(out, value.position()); out.writeUTF(value.table());
                        out.writeLong(value.seed()); out.writeInt(value.encounterOrder());
                    }
                    out.writeInt(blockEntities.size());
                    for (BlockEntityWrite value : blockEntities) {
                        position(out, value.position()); out.writeUTF(value.blockIdentity());
                        out.writeUTF(value.entityType()); out.writeInt(value.encounterOrder());
                        out.writeInt(value.canonicalNbt().length); out.write(value.canonicalNbt());
                    }
                    out.writeInt(fluidTicks.size());
                    for (FluidTickWrite value : fluidTicks) {
                        position(out, value.position()); out.writeUTF(value.key());
                        out.writeInt(value.delay()); out.writeInt(value.priority());
                        out.writeLong(value.subTickOrder());
                    }
                    out.writeInt(successor.startNbt().length); out.write(successor.startNbt());
                    out.writeInt(successor.pieceNbt().length); out.write(successor.pieceNbt());
                    out.writeInt(randomContinuation.length); out.write(randomContinuation);
                }
                byte[] body = bytes.toByteArray();
                ByteArrayOutputStream result = new ByteArrayOutputStream(body.length + 32);
                result.write(body); result.write(digest(body)); return result.toByteArray();
            } catch (IOException impossible) { throw new IllegalStateException(impossible); }
        }
        public static void verifyFrozenReceipt(byte[] receipt) {
            Objects.requireNonNull(receipt, "ruined-portal receipt");
            if (receipt.length < RECEIPT_MAGIC.length + 32)
                throw new IllegalArgumentException("truncated ruined-portal receipt");
            for (int i = 0; i < RECEIPT_MAGIC.length; i++)
                if (receipt[i] != RECEIPT_MAGIC[i]) throw new IllegalArgumentException("bad portal receipt magic");
            byte[] body = Arrays.copyOf(receipt, receipt.length - 32);
            if (!Arrays.equals(digest(body), Arrays.copyOfRange(receipt, receipt.length - 32, receipt.length)))
                throw new IllegalArgumentException("ruined-portal receipt digest mismatch");
        }
    }

    public static AtomicSettlement execute(Plan plan, int targetChunkX, int targetChunkZ,
            int stepIndex, Clip chunkClip, WorldAccess world, PlacementRandom random) {
        Objects.requireNonNull(plan, "ruined-portal plan"); Objects.requireNonNull(chunkClip, "portal clip");
        Objects.requireNonNull(world, "ruined-portal world"); Objects.requireNonNull(random, "portal random");
        if (stepIndex < 0) throw new IllegalArgumentException("negative ruined-portal step index");
        if (Math.floorDiv(chunkClip.minX(), 16) != targetChunkX
                || Math.floorDiv(chunkClip.minZ(), 16) != targetChunkZ
                || chunkClip.maxX() != Math.multiplyExact(targetChunkX, 16) + 15
                || chunkClip.maxZ() != Math.multiplyExact(targetChunkZ, 16) + 15)
            throw new IllegalArgumentException("ruined-portal clip/chunk mismatch");
        requireIdentity(); preflight(plan, world);
        PlacementRandom staged = random.copy();
        BoundingBox bounds = plan.boundingBox();
        // Vanilla selects exactly the chunk containing the transformed template center, then
        // expands that mutable placement box to the whole piece. An edge-only intersection does
        // no world access and consumes no placement RNG.
        if (!chunkClip.contains(bounds.center())) {
            AtomicSettlement empty = new AtomicSettlement(plan, List.of(), List.of(), List.of(), List.of(),
                    List.of(), List.of(), List.of(), staged.canonicalContinuation());
            world.settle(empty); random.commit(staged); return empty;
        }
        Staging staging = new Staging(world, chunkClip.encompass(bounds));
        placeTemplate(plan, staging, staged);
        spreadNetherrack(plan, staging, staged);
        addPortalDrips(plan, staging, staged);
        addVegetation(plan, staging, staged);
        AtomicSettlement settlement = staging.finish(plan, staged.canonicalContinuation());
        world.settle(settlement); random.commit(staged); return settlement;
    }

    private static void preflight(Plan plan, WorldAccess world) {
        if (!world.supportsAtomicSettlement())
            throw new UnsupportedOperationException("atomic ruined-portal settlement required");
        Set<String> states = requiredStates(plan);
        for (String state : states) if (!world.supportsExactState(state))
            throw new UnsupportedOperationException("unsupported ruined-portal state: " + state);
        if (!world.supportsLootTable(Mc263RuinedPortalProgram.LOOT_TABLE)
                || !world.supportsBlockEntity(CHEST, CHEST))
            throw new UnsupportedOperationException("ruined-portal LOOT/BENT capability required");
    }
    private static Set<String> requiredStates(Plan plan) {
        LinkedHashSet<String> result = new LinkedHashSet<>(List.of(AIR, NETHERRACK, MAGMA,
                "minecraft:crying_obsidian", "minecraft:cracked_stone_bricks",
                "minecraft:mossy_stone_bricks", "minecraft:stone_brick_stairs[facing=north, half=bottom, shape=straight, waterlogged=false]",
                "minecraft:stone_slab[type=bottom, waterlogged=false]",
                "minecraft:stone_brick_slab[type=bottom, waterlogged=false]",
                "minecraft:mossy_stone_brick_slab[type=bottom, waterlogged=false]",
                "minecraft:mossy_stone_brick_wall[east=none, north=none, south=none, up=true, waterlogged=false, west=none]",
                "minecraft:jungle_leaves[distance=7, persistent=true, waterlogged=false]",
                "minecraft:vine[east=false, north=false, south=false, up=false, west=false]",
                "minecraft:barrier"));
        for (String facing : List.of("north", "east", "south", "west"))
            for (String half : List.of("bottom", "top"))
                for (String key : List.of("minecraft:stone_brick_stairs",
                        "minecraft:mossy_stone_brick_stairs"))
                    result.add(key + "[facing=" + facing + ", half=" + half
                            + ", shape=straight, waterlogged=false]");
        for (String attached : List.of("north", "east", "south", "west")) {
            TreeMap<String, String> vine = new TreeMap<>(Map.of("north", "false", "east", "false",
                    "south", "false", "west", "false", "up", "false"));
            vine.put(attached, "true"); result.add(withProperties("minecraft:vine", vine));
        }
        Grammar grammar = Mc263RuinedPortalGrammarData.require(plan.templateKey());
        for (String state : grammar.states()) {
            String key = blockKey(state);
            if (key.equals("minecraft:jigsaw")) continue;
            result.add(transformState(state, plan.rotation(), plan.mirror()));
        }
        // Close the guard by enumeration rather than by the states this one plan happens to
        // reach: the template palette runs through the same rule/age processors and the same
        // water settlement in every rotation and mirror, so the preflight admits a plan only
        // when the whole pipeline surface is catalogued.
        result.addAll(processorStateClosure());
        return result;
    }

    /**
     * Complete exact-state surface the pinned 26.3 ruined-portal pipeline can emit.
     *
     * <p>Derivation: the 13 pinned template palettes carried by
     * {@link Mc263RuinedPortalGrammarData} run through the rule processor ({@link #rules}), the
     * block-age processor ({@link #age}), the lava/water settlement that
     * {@link #placeTemplate} applies to the processed state, and finally the placement
     * rotation/mirror transform. Enumerating those four stages over the palette is the closure the
     * preflight and the exact-state catalog must both carry.</p>
     */
    static Set<String> processorStateClosure() {
        LinkedHashSet<String> sources = new LinkedHashSet<>();
        for (Grammar grammar : Mc263RuinedPortalGrammarData.all().values())
            sources.addAll(grammar.states());
        LinkedHashSet<String> aged = new LinkedHashSet<>();
        for (String source : sources) {
            if (blockKey(source).equals("minecraft:jigsaw")) continue;
            for (String ruled : ruleOutputs(source)) aged.addAll(ageOutputs(ruled));
        }
        LinkedHashSet<String> settled = new LinkedHashSet<>();
        for (String state : aged) {
            settled.add(state);
            if (!isFullBlock(state)) settled.add("minecraft:lava[level=0]");
            Map<String, String> values = properties(state);
            if (values.containsKey("waterlogged")
                    && !values.getOrDefault("type", "").equals("double")) {
                TreeMap<String, String> waterlogged = new TreeMap<>(values);
                waterlogged.put("waterlogged", "true");
                settled.add(withProperties(blockKey(state), waterlogged));
            }
        }
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (String state : settled) for (Rotation rotation : Rotation.values())
            for (Mirror mirror : Mirror.values())
                result.add(transformState(state, rotation, mirror));
        return result;
    }

    /** Every {@link #rules} outcome for one palette state. */
    private static Set<String> ruleOutputs(String state) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        result.add(state);
        String key = blockKey(state);
        if (key.equals("minecraft:gold_block")) result.add(AIR);
        if (key.equals(LAVA)) { result.add(MAGMA); result.add(NETHERRACK); }
        if (key.equals(NETHERRACK)) result.add(MAGMA);
        return result;
    }

    /** Every {@link #age} outcome for one rule-processor output. */
    private static Set<String> ageOutputs(String state) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        result.add(state);
        String key = blockKey(state);
        if (Set.of("minecraft:stone_bricks", "minecraft:stone",
                "minecraft:chiseled_stone_bricks").contains(key)) {
            result.add("minecraft:mossy_stone_bricks");
            result.add("minecraft:cracked_stone_bricks");
            for (String facing : List.of("north", "east", "south", "west"))
                for (String half : List.of("top", "bottom"))
                    for (String stairs : List.of("minecraft:stone_brick_stairs",
                            "minecraft:mossy_stone_brick_stairs"))
                        result.add(stairs + "[facing=" + facing + ", half=" + half
                                + ", shape=straight, waterlogged=false]");
        } else if (key.endsWith("_stairs")) {
            result.add(replaceKey(state, "minecraft:mossy_stone_brick_stairs"));
            result.add("minecraft:mossy_stone_brick_slab[type=bottom, waterlogged=false]");
            result.add("minecraft:stone_slab[type=bottom, waterlogged=false]");
            result.add("minecraft:stone_brick_slab[type=bottom, waterlogged=false]");
        } else if (key.endsWith("_slab")) {
            result.add(replaceKey(state, "minecraft:mossy_stone_brick_slab"));
        } else if (key.endsWith("_wall")) {
            result.add(replaceKey(state, "minecraft:mossy_stone_brick_wall"));
        } else if (key.equals(OBSIDIAN)) {
            result.add("minecraft:crying_obsidian");
        }
        return result;
    }

    private static void placeTemplate(Plan plan, Staging staging, PlacementRandom random) {
        Grammar grammar = Mc263RuinedPortalGrammarData.require(plan.templateKey());
        for (Command command : grammar.commands()) for (int index = 0; index < command.count(); index++) {
            BlockPos local = new BlockPos(command.x() + command.dx() * index,
                    command.y() + command.dy() * index, command.z() + command.dz() * index);
            BlockPos position = Mc263RuinedPortalProgram.transform(plan.templatePosition(), local,
                    plan.rotation(), plan.mirror(), plan.pivot());
            String source = grammar.states().get(command.state());
            Semantic semantic = command.op() == Op.DATA ? command.semantic() : Semantic.NONE;
            if (semantic == Semantic.JIGSAW) source = command.finalState();
            String key = blockKey(source);
            if (key.equals("minecraft:jigsaw") || (semantic != Semantic.JIGSAW
                    && !plan.properties().airPocket() && key.equals(AIR))) continue;
            String processed = rules(source, position, plan);
            processed = age(processed, position, plan.properties().mossiness());
            staging.recordTemplateProcessor(position);
            boolean replaceable = staging.canFeatureReplace(position);
            if (!replaceable) continue;
            staging.recordTemplateProcessorAccepted(position);
            String fluid = staging.fluidState(position);
            staging.recordTemplateFluid(position);
            if (blockKey(fluid).equals(LAVA) && !isFullBlock(processed)) {
                processed = "minecraft:lava[level=0]";
            } else if (blockKey(fluid).equals(WATER)
                    && properties(processed).containsKey("waterlogged")
                    && !properties(processed).getOrDefault("type", "").equals("double")) {
                TreeMap<String, String> waterlogged = new TreeMap<>(properties(processed));
                waterlogged.put("waterlogged", "true");
                processed = withProperties(blockKey(processed), waterlogged);
            }
            processed = transformState(processed, plan.rotation(), plan.mirror());
            boolean hasNbt = semantic == Semantic.JIGSAW || semantic == Semantic.LOOT_CHEST;
            if (semantic == Semantic.LOOT_CHEST) staging.write(position, "minecraft:barrier", 820);
            boolean placed = staging.write(position, processed, 2);
            if (placed) staging.recordTemplatePlaced(position, processed, hasNbt, fluid);
            if (hasNbt && placed) staging.recordTemplateBlockEntity(position);
            if (semantic == Semantic.LOOT_CHEST) {
                long seed = random.nextLong();
                staging.loot(position, seed, Mc263RuinedPortalProgram.chestNbt(position, seed));
            }
        }
        staging.flushTemplateOfficial(plan);
    }

    private static String rules(String state, BlockPos position, Plan plan) {
        String key = blockKey(state); PositionalRandom random = new PositionalRandom(seed(position));
        if (key.equals("minecraft:gold_block") && random.nextFloat() < .3F) return AIR;
        if (key.equals(LAVA)) {
            if (plan.setup().placement() == VerticalPlacement.ON_OCEAN_FLOOR) return MAGMA;
            if (plan.properties().cold()) return NETHERRACK;
            if (random.nextFloat() < .2F) return MAGMA;
        }
        if (key.equals(NETHERRACK) && !plan.properties().cold() && random.nextFloat() < .07F)
            return MAGMA;
        return state;
    }
    private static String age(String state, BlockPos position, float mossiness) {
        String key = blockKey(state); PositionalRandom random = new PositionalRandom(seed(position));
        if (Set.of("minecraft:stone_bricks", "minecraft:stone", "minecraft:chiseled_stone_bricks").contains(key)) {
            if (random.nextFloat() >= .5F) return state;
            String nonMossyStairs = randomStairs(random, "minecraft:stone_brick_stairs");
            String mossyStairs = randomStairs(random, "minecraft:mossy_stone_brick_stairs");
            boolean mossy = random.nextFloat() < mossiness;
            return random.nextInt(2) == 0
                    ? mossy ? "minecraft:mossy_stone_bricks" : "minecraft:cracked_stone_bricks"
                    : mossy ? mossyStairs : nonMossyStairs;
        }
        if (key.endsWith("_stairs")) {
            if (random.nextFloat() >= .5F) return state;
            boolean mossy = random.nextFloat() < mossiness;
            int selected = random.nextInt(2);
            if (mossy) return selected == 0
                    ? replaceKey(state, "minecraft:mossy_stone_brick_stairs")
                    : "minecraft:mossy_stone_brick_slab[type=bottom, waterlogged=false]";
            return selected == 0 ? "minecraft:stone_slab[type=bottom, waterlogged=false]"
                    : "minecraft:stone_brick_slab[type=bottom, waterlogged=false]";
        }
        if (key.endsWith("_slab") && random.nextFloat() < mossiness)
            return replaceKey(state, "minecraft:mossy_stone_brick_slab");
        if (key.endsWith("_wall") && random.nextFloat() < mossiness)
            return replaceKey(state, "minecraft:mossy_stone_brick_wall");
        if (key.equals(OBSIDIAN) && random.nextFloat() < .15F) return "minecraft:crying_obsidian";
        return state;
    }
    private static String randomStairs(PositionalRandom random, String key) {
        String facing = List.of("north", "east", "south", "west").get(random.nextInt(4));
        String half = random.nextInt(2) == 0 ? "top" : "bottom";
        return key + "[facing=" + facing + ", half=" + half
                + ", shape=straight, waterlogged=false]";
    }

    private static void spreadNetherrack(Plan plan, Staging staging, PlacementRandom random) {
        BoundingBox box = plan.boundingBox(); BlockPos center = box.center();
        int average = ((box.maxX() - box.minX() + 1) + (box.maxZ() - box.minZ() + 1)) / 2;
        int adjustment = random.nextInt(Math.max(1, 8 - average / 2));
        boolean follow = plan.setup().placement() == VerticalPlacement.ON_LAND_SURFACE
                || plan.setup().placement() == VerticalPlacement.ON_OCEAN_FLOOR;
        Heightmap map = plan.setup().placement() == VerticalPlacement.ON_OCEAN_FLOOR
                ? Heightmap.OCEAN_FLOOR_WG : Heightmap.WORLD_SURFACE_WG;
        for (int x = center.x() - 14; x <= center.x() + 14; x++)
            for (int z = center.z() - 14; z <= center.z() + 14; z++) {
                int distance = Math.max(0, Math.abs(x - center.x()) + Math.abs(z - center.z()) + adjustment);
                if (distance >= 14 || random.nextDouble() >= NETHERRACK_PROBABILITY.get(distance)) continue;
                int surface = staging.height(map, x, z) - 1;
                int y = follow ? surface : Math.min(box.minY(), surface);
                BlockPos position = new BlockPos(x, y, z);
                if (Math.abs(y - box.minY()) > 3 || !staging.canNetherrackReplace(position,
                        plan.setup().placement())) continue;
                placeNetherrackOrMagma(plan, staging, random, position);
                if (plan.properties().overgrown()) maybeLeaves(staging, random, position);
                drip(plan, staging, random, position.offset(0, -1, 0));
            }
    }
    private static void addPortalDrips(Plan plan, Staging staging, PlacementRandom random) {
        BoundingBox box = plan.boundingBox();
        for (int x = box.minX() + 1; x < box.maxX(); x++)
            for (int z = box.minZ() + 1; z < box.maxZ(); z++) {
                BlockPos position = new BlockPos(x, box.minY(), z);
                if (blockKey(staging.blockState(position)).equals(NETHERRACK))
                    drip(plan, staging, random, position.offset(0, -1, 0));
            }
    }
    private static void drip(Plan plan, Staging staging, PlacementRandom random, BlockPos position) {
        BlockPos cursor = position; placeNetherrackOrMagma(plan, staging, random, cursor);
        for (int cap = 8; cap > 0 && random.nextFloat() < .5F; cap--) {
            cursor = cursor.offset(0, -1, 0); placeNetherrackOrMagma(plan, staging, random, cursor);
        }
    }
    private static void placeNetherrackOrMagma(Plan plan, Staging staging,
            PlacementRandom random, BlockPos position) {
        staging.write(position, !plan.properties().cold() && random.nextFloat() < .07F ? MAGMA : NETHERRACK, 3);
    }
    private static void addVegetation(Plan plan, Staging staging, PlacementRandom random) {
        if (!plan.properties().vines() && !plan.properties().overgrown()) return;
        BoundingBox box = plan.boundingBox();
        // BlockPos.betweenClosedStream iterates X fastest, then Y, then Z.
        for (int z = box.minZ(); z <= box.maxZ(); z++) for (int y = box.minY(); y <= box.maxY(); y++)
            for (int x = box.minX(); x <= box.maxX(); x++) {
                BlockPos position = new BlockPos(x, y, z);
                if (plan.properties().vines()) maybeVine(staging, random, position);
                if (plan.properties().overgrown()) maybeLeaves(staging, random, position);
            }
    }
    private static void maybeVine(Staging staging, PlacementRandom random, BlockPos position) {
        String state = staging.blockState(position);
        if (blockKey(state).equals(AIR) || blockKey(state).equals("minecraft:vine")) return;
        Direction direction = Direction.values()[random.nextInt(4)];
        BlockPos neighbor = offset(position, direction);
        if (!blockKey(staging.blockState(neighbor)).equals(AIR)
                || !staging.isFaceFull(position, direction)) return;
        String attached = opposite(direction).name().toLowerCase();
        TreeMap<String, String> properties = new TreeMap<>(Map.of("north", "false", "east", "false",
                "south", "false", "west", "false", "up", "false"));
        properties.put(attached, "true"); staging.write(neighbor, withProperties("minecraft:vine", properties), 3);
    }
    private static void maybeLeaves(Staging staging, PlacementRandom random, BlockPos position) {
        if (random.nextFloat() < .5F && blockKey(staging.blockState(position)).equals(NETHERRACK)
                && blockKey(staging.blockState(position.offset(0, 1, 0))).equals(AIR))
            staging.write(position.offset(0, 1, 0),
                    "minecraft:jungle_leaves[distance=7, persistent=true, waterlogged=false]", 3);
    }

    static String transformState(String state, Rotation rotation, Mirror mirror) {
        Map<String, String> original = properties(state);
        if (original.isEmpty()) return state;
        TreeMap<String, String> transformed = new TreeMap<>();
        for (Map.Entry<String, String> entry : original.entrySet()) {
            String key = entry.getKey(), value = entry.getValue();
            if (isDirection(value)) value = transformDirection(value, rotation, mirror);
            if (isDirection(key)) key = transformDirection(key, rotation, mirror);
            if (key.equals("axis") && (rotation == Rotation.CLOCKWISE_90
                    || rotation == Rotation.COUNTERCLOCKWISE_90))
                value = value.equals("x") ? "z" : value.equals("z") ? "x" : value;
            // StairBlock.mirror: FRONT_BACK acts only when the source facing axis is X, and it
            // swaps outer_left<->outer_right while retaining inner_left/inner_right.
            if (key.equals("shape") && mirror == Mirror.FRONT_BACK
                    && blockKey(state).endsWith("_stairs")
                    && isXAxisFacing(original.get("facing"))) value = switch (value) {
                case "outer_left" -> "outer_right"; case "outer_right" -> "outer_left";
                default -> value;
            };
            transformed.put(key, value);
        }
        return withProperties(blockKey(state), transformed);
    }
    private static String transformDirection(String direction, Rotation rotation, Mirror mirror) {
        int index = List.of("north", "east", "south", "west").indexOf(direction);
        if (index < 0) return direction;
        if (mirror == Mirror.FRONT_BACK) index = switch (index) { case 1 -> 3; case 3 -> 1; default -> index; };
        int turns = switch (rotation) { case NONE -> 0; case CLOCKWISE_90 -> 1;
            case CLOCKWISE_180 -> 2; case COUNTERCLOCKWISE_90 -> 3; };
        return List.of("north", "east", "south", "west").get((index + turns) & 3);
    }
    private static boolean isXAxisFacing(String facing) {
        return "east".equals(facing) || "west".equals(facing);
    }
    private static boolean isDirection(String value) { return Set.of("north", "east", "south", "west").contains(value); }
    private static String replaceKey(String state, String key) {
        Map<String, String> values = properties(state); return values.isEmpty() ? key : withProperties(key, values);
    }
    private static String withProperties(String key, Map<String, String> values) {
        return key + "[" + values.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .map(value -> value.getKey() + "=" + value.getValue()).collect(Collectors.joining(", ")) + "]";
    }
    private static Map<String, String> properties(String state) {
        int open = state.indexOf('['); if (open < 0) return Map.of();
        LinkedHashMap<String, String> result = new LinkedHashMap<>();
        for (String item : state.substring(open + 1, state.length() - 1).split(",\\s*")) {
            String[] pair = item.split("=", 2); result.put(pair[0], pair[1]);
        }
        return result;
    }
    private static String blockKey(String state) {
        int property = state.indexOf('['); return property < 0 ? state : state.substring(0, property);
    }
    private static boolean isFullBlock(String state) {
        String key = blockKey(state);
        return !key.endsWith("_slab") && !key.endsWith("_stairs") && !key.endsWith("_wall")
                && !key.equals("minecraft:iron_bars") && !key.equals("minecraft:chest")
                && !key.equals("minecraft:vine") && !key.equals(AIR);
    }
    private static long seed(BlockPos p) {
        long value = (long) (p.x() * 3_129_871) ^ (long) p.z() * 116_129_781L ^ p.y();
        return (value * value * 42_317_861L + value * 11L) >> 16;
    }
    private static BlockPos offset(BlockPos p, Direction d) { return switch (d) {
        case NORTH -> p.offset(0, 0, -1); case EAST -> p.offset(1, 0, 0);
        case SOUTH -> p.offset(0, 0, 1); case WEST -> p.offset(-1, 0, 0); };
    }
    private static Direction opposite(Direction d) { return Direction.values()[(d.ordinal() + 2) & 3]; }

    private static final class TemplatePlaced {
        private final BlockPos position;
        private final String state;
        private final boolean hasNbt;
        private final String originalFluid;
        TemplatePlaced(BlockPos position, String state, boolean hasNbt, String originalFluid) {
            this.position = position; this.state = state; this.hasNbt = hasNbt;
            this.originalFluid = originalFluid;
        }
    }

    private static final class Staging {
        private final WorldAccess world; private final Clip clip;
        private final LinkedHashMap<BlockPos, String> overlay = new LinkedHashMap<>();
        private final ArrayList<Query> queries = new ArrayList<>();
        private final ArrayList<OfficialQuery> officialQueries = new ArrayList<>();
        private final ArrayList<OfficialQuery> templateProcessorOfficial = new ArrayList<>();
        private final ArrayList<OfficialQuery> templatePlacementOfficial = new ArrayList<>();
        private final ArrayList<TemplatePlaced> templatePlaced = new ArrayList<>();
        private final ArrayList<BlockWrite> writes = new ArrayList<>();
        private final ArrayList<LootWrite> loot = new ArrayList<>();
        private final ArrayList<BlockEntityWrite> blockEntities = new ArrayList<>();
        private final ArrayList<FluidTickWrite> fluidTicks = new ArrayList<>();
        private final ArrayList<FluidTickWrite> officialFluidTicks = new ArrayList<>();
        private boolean templateWaterEnvironment;
        Staging(WorldAccess world, Clip clip) { this.world = world; this.clip = clip; }
        void recordTemplateProcessor(BlockPos p) {
            templateProcessorOfficial.add(new OfficialQuery("getBlockState", p));
        }
        void recordTemplateProcessorAccepted(BlockPos p) {
            templateProcessorOfficial.add(new OfficialQuery("getBlockState", p));
        }
        void recordTemplateFluid(BlockPos p) {
            templatePlacementOfficial.add(new OfficialQuery("getFluidState", p));
        }
        void recordTemplateBlockEntity(BlockPos p) {
            templatePlacementOfficial.add(new OfficialQuery("getBlockEntity", p));
        }
        void recordTemplatePlaced(BlockPos p, String state, boolean hasNbt, String originalFluid) {
            templatePlaced.add(new TemplatePlaced(p, state, hasNbt, originalFluid));
            if (blockKey(originalFluid).equals(WATER)) templateWaterEnvironment = true;
            if (blockKey(originalFluid).equals(WATER)
                    && properties(state).getOrDefault("waterlogged", "false").equals("true"))
                scheduleOfficialFluidTick(p, WATER);
        }
        void flushTemplateOfficial(Plan plan) {
            officialQueries.addAll(templateProcessorOfficial);
            officialQueries.addAll(templatePlacementOfficial);
            for (TemplatePlaced value : templatePlaced) {
                if (!properties(value.state).containsKey("waterlogged")
                        || isSourceFluid(value.originalFluid)) continue;
                addOfficial("getFluidState", value.position);
                addOfficial("getFluidState", value.position.offset(0, 1, 0));
                addOfficial("getFluidState", value.position.offset(0, 0, -1));
                addOfficial("getFluidState", value.position.offset(1, 0, 0));
                addOfficial("getFluidState", value.position.offset(0, 0, 1));
                addOfficial("getFluidState", value.position.offset(-1, 0, 0));
            }
            emitTemplateShapeOfficial(plan);
        }
        private static boolean isSourceFluid(String state) {
            String key = blockKey(state);
            return key.equals(WATER) || key.equals(LAVA);
        }
        private void addOfficial(String operation, BlockPos position) {
            officialQueries.add(new OfficialQuery(operation, position));
        }
        private void emitTemplateShapeOfficial(Plan plan) {
            if (templatePlaced.isEmpty()) return;
            LinkedHashSet<BlockPos> occupied = new LinkedHashSet<>();
            int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
            for (TemplatePlaced value : templatePlaced) {
                BlockPos p = value.position; occupied.add(p);
                minX = Math.min(minX, p.x()); minY = Math.min(minY, p.y()); minZ = Math.min(minZ, p.z());
                maxX = Math.max(maxX, p.x()); maxY = Math.max(maxY, p.y()); maxZ = Math.max(maxZ, p.z());
            }
            // DiscreteVoxelShape.forAllFaces order: Z, then Y, then X axis runs.
            for (int x = minX; x <= maxX; x++) for (int y = minY; y <= maxY; y++) {
                boolean previous = false;
                for (int z = minZ; z <= maxZ + 1; z++) {
                    boolean current = z <= maxZ && occupied.contains(new BlockPos(x, y, z));
                    if (!previous && current) emitEdge(plan, new BlockPos(x, y, z), ShapeDirection.NORTH);
                    if (previous && !current) emitEdge(plan, new BlockPos(x, y, z - 1), ShapeDirection.SOUTH);
                    previous = current;
                }
            }
            for (int z = minZ; z <= maxZ; z++) for (int x = minX; x <= maxX; x++) {
                boolean previous = false;
                for (int y = minY; y <= maxY + 1; y++) {
                    boolean current = y <= maxY && occupied.contains(new BlockPos(x, y, z));
                    if (!previous && current) emitEdge(plan, new BlockPos(x, y, z), ShapeDirection.DOWN);
                    if (previous && !current) emitEdge(plan, new BlockPos(x, y - 1, z), ShapeDirection.UP);
                    previous = current;
                }
            }
            for (int y = minY; y <= maxY; y++) for (int z = minZ; z <= maxZ; z++) {
                boolean previous = false;
                for (int x = minX; x <= maxX + 1; x++) {
                    boolean current = x <= maxX && occupied.contains(new BlockPos(x, y, z));
                    if (!previous && current) emitEdge(plan, new BlockPos(x, y, z), ShapeDirection.WEST);
                    if (previous && !current) emitEdge(plan, new BlockPos(x - 1, y, z), ShapeDirection.EAST);
                    previous = current;
                }
            }
            for (TemplatePlaced value : templatePlaced) {
                addOfficial("getBlockState", value.position);
                String state = officialShapeState(plan, value.position);
                for (ShapeDirection direction : ShapeDirection.values()) {
                    BlockPos neighbor = direction.move(value.position);
                    addOfficial("getBlockState", neighbor);
                    recordOfficialShapeFluidTick(value.position, state, officialShapeState(plan, neighbor));
                    emitNestedShapeReads(value.state, value.position, direction);
                }
                if (value.hasNbt) addOfficial("getBlockEntity", value.position);
            }
        }
        private void emitEdge(Plan plan, BlockPos inside, ShapeDirection direction) {
            BlockPos outside = direction.move(inside);
            addOfficial("getBlockState", inside);
            addOfficial("getBlockState", outside);
            String state = officialShapeState(plan, inside);
            recordOfficialShapeFluidTick(inside, state, officialShapeState(plan, outside));
            if (overlay.containsKey(inside)) emitNestedShapeReads(state, inside, direction);
            String outsideState = officialShapeState(plan, outside);
            recordOfficialShapeFluidTick(outside, outsideState, state);
            if (overlay.containsKey(outside)) emitNestedShapeReads(outsideState, outside, direction.opposite());
        }
        private String officialShapeState(Plan plan, BlockPos position) {
            String state = overlay.get(position);
            if (state != null) return state;
            if (templateWaterEnvironment
                    && plan.setup().placement() == VerticalPlacement.ON_OCEAN_FLOOR
                    && position.y() > plan.boundingBox().minY()
                    && position.y() <= OVERWORLD_SEA_LEVEL)
                return "minecraft:water[level=0]";
            return AIR;
        }
        private void recordOfficialShapeFluidTick(BlockPos position, String state,
                String neighborState) {
            Map<String, String> stateProperties = properties(state);
            if (stateProperties.getOrDefault("waterlogged", "false").equals("true")) {
                scheduleOfficialFluidTick(position, WATER);
                return;
            }
            String key = blockKey(state);
            if (key.equals(WATER)) {
                scheduleOfficialFluidTick(position, WATER);
                return;
            }
            if (!key.equals(LAVA)) return;
            boolean source = stateProperties.getOrDefault("level", "0").equals("0");
            if (source || blockKey(neighborState).equals(LAVA)
                    && properties(neighborState).getOrDefault("level", "0").equals("0"))
                scheduleOfficialFluidTick(position, source ? LAVA : FLOWING_LAVA);
        }
        private void scheduleOfficialFluidTick(BlockPos position, String key) {
            officialFluidTicks.add(new FluidTickWrite(position, key, fluidTickDelay(key), 0,
                    officialFluidTicks.size()));
        }
        private void emitNestedShapeReads(String state, BlockPos position, ShapeDirection updateDirection) {
            if (!updateDirection.horizontal()) return;
            String key = blockKey(state);
            if (key.endsWith("_stairs")) { emitStairShapeReads(state, position); return; }
            if (key.endsWith("_wall")) addOfficial("getBlockState", position.offset(0, 1, 0));
        }
        private void emitStairShapeReads(String state, BlockPos position) {
            Map<String, String> stateProperties = properties(state);
            ShapeDirection facing = horizontalDirection(stateProperties.get("facing"));
            String half = stateProperties.get("half");
            BlockPos front = facing.move(position);
            addOfficial("getBlockState", front);
            String frontState = overlay.get(front);
            if (compatiblePerpendicularStair(frontState, half, facing)) {
                ShapeDirection frontFacing = horizontalDirection(properties(frontState).get("facing"));
                BlockPos test = frontFacing.opposite().move(position);
                addOfficial("getBlockState", test);
                if (canTakeStairShape(state, overlay.get(test))) return;
            }
            BlockPos back = facing.opposite().move(position);
            addOfficial("getBlockState", back);
            String backState = overlay.get(back);
            if (compatiblePerpendicularStair(backState, half, facing)) {
                ShapeDirection backFacing = horizontalDirection(properties(backState).get("facing"));
                BlockPos test = backFacing.move(position);
                addOfficial("getBlockState", test);
                canTakeStairShape(state, overlay.get(test));
            }
        }
        private static boolean compatiblePerpendicularStair(String other, String half,
                ShapeDirection facing) {
            if (other == null || !blockKey(other).endsWith("_stairs")) return false;
            Map<String, String> props = properties(other);
            ShapeDirection otherFacing = horizontalDirection(props.get("facing"));
            return Objects.equals(half, props.get("half")) && perpendicular(facing, otherFacing);
        }
        private static boolean canTakeStairShape(String state, String other) {
            if (other == null || !blockKey(other).endsWith("_stairs")) return true;
            Map<String, String> a = properties(state), b = properties(other);
            return !Objects.equals(a.get("facing"), b.get("facing"))
                    || !Objects.equals(a.get("half"), b.get("half"));
        }
        private static boolean perpendicular(ShapeDirection a, ShapeDirection b) {
            return (a == ShapeDirection.NORTH || a == ShapeDirection.SOUTH)
                    != (b == ShapeDirection.NORTH || b == ShapeDirection.SOUTH);
        }
        private static ShapeDirection horizontalDirection(String value) {
            return switch (Objects.requireNonNull(value, "ruined-portal horizontal facing")) {
                case "west" -> ShapeDirection.WEST; case "east" -> ShapeDirection.EAST;
                case "north" -> ShapeDirection.NORTH; case "south" -> ShapeDirection.SOUTH;
                default -> throw new IllegalArgumentException("unknown ruined-portal horizontal facing: " + value);
            };
        }
        String blockState(BlockPos p) {
            String result = overlay.get(p);
            if (result == null) result = world.blockState(p);
            queries.add(new Query("getBlockState", p, result, queries.size()));
            officialQueries.add(new OfficialQuery("getBlockState", p)); return result;
        }
        String fluidState(BlockPos p) {
            String result = world.fluidState(p);
            queries.add(new Query("getFluidState", p, result, queries.size()));
            return result;
        }
        boolean canFeatureReplace(BlockPos p) {
            String staged = overlay.get(p);
            boolean result = staged == null ? world.canFeatureReplace(p)
                    : !blockKey(staged).equals(CHEST);
            queries.add(new Query("canFeatureReplace", p, Boolean.toString(result), queries.size())); return result;
        }
        boolean isFaceFull(BlockPos p, Direction d) {
            boolean result = overlay.containsKey(p) ? isFaceFull(overlay.get(p), d) : world.isFaceFull(p, d);
            queries.add(new Query("isFaceFull:" + d.name(), p, Boolean.toString(result), queries.size())); return result;
        }
        private static boolean isFaceFull(String state, Direction direction) {
            String key = blockKey(state);
            if (key.endsWith("_stairs")) return false;
            return isFullBlock(state);
        }
        int height(Heightmap map, int x, int z) {
            int base = world.height(map, x, z), top = base - 1;
            for (BlockPos position : overlay.keySet())
                if (position.x() == x && position.z() == z) top = Math.max(top, position.y());
            int result = base;
            for (int y = top; y >= base - 64; y--) {
                String state = overlay.get(new BlockPos(x, y, z));
                if (state == null) { result = y < base ? y + 1 : base; break; }
                if (heightMatches(state, map)) { result = y + 1; break; }
            }
            BlockPos queryPosition = new BlockPos(x, 0, z);
            queries.add(new Query("getHeight:" + map.name(), queryPosition,
                    Integer.toString(result), queries.size()));
            officialQueries.add(new OfficialQuery("getHeight:" + map.name(), queryPosition)); return result;
        }
        private static boolean heightMatches(String state, Heightmap map) {
            String key = blockKey(state);
            if (key.equals(AIR) || key.equals("minecraft:vine")) return false;
            if (map == Heightmap.OCEAN_FLOOR_WG && key.equals(LAVA)) return false;
            return true;
        }
        boolean canNetherrackReplace(BlockPos p, VerticalPlacement placement) {
            String key = blockKey(blockState(p));
            return !key.equals(AIR) && !key.equals(OBSIDIAN) && canFeatureReplace(p)
                    && (placement == VerticalPlacement.ON_LAND_SURFACE
                        || placement == VerticalPlacement.PARTLY_BURIED
                        || placement == VerticalPlacement.IN_MOUNTAIN
                        || placement == VerticalPlacement.UNDERGROUND
                            ? !key.equals(LAVA) : true);
        }
        boolean write(BlockPos p, String state, int flags) {
            String canonicalState = Mc263FeatureBlockState.fromExact(state).exactState();
            String current = overlay.get(p);
            if (current == null) current = world.blockState(p);
            if (current.equals(canonicalState)) return false;
            overlay.put(p, canonicalState);
            writes.add(new BlockWrite(p, canonicalState, flags, writes.size()));
            if (blockKey(canonicalState).equals(LAVA))
                fluidTicks.add(new FluidTickWrite(p, LAVA, 30, 0, fluidTicks.size()));
            return true;
        }
        void loot(BlockPos p, long seed, byte[] nbt) {
            loot.add(new LootWrite(p, Mc263RuinedPortalProgram.LOOT_TABLE, seed, loot.size()));
            blockEntities.add(new BlockEntityWrite(p, CHEST, CHEST, nbt, blockEntities.size()));
        }
        AtomicSettlement finish(Plan plan, byte[] continuation) {
            return new AtomicSettlement(plan, queries, officialQueries, writes, loot, blockEntities,
                    fluidTicks, officialFluidTicks, continuation);
        }
    }

    /** Exact decoration Xoroshiro source with fork/commit semantics. */
    public static final class PlacementRandom {
        private Xoroshiro source; private int count;
        private PlacementRandom(Xoroshiro source, int count) { this.source = source; this.count = count; }
        public static PlacementRandom forChunk(long seed, int chunkX, int chunkZ,
                int stepIndex, int step) {
            PlacementRandom result = new PlacementRandom(new Xoroshiro(0), 0);
            result.reseed(seed); long x = result.nextLong() | 1L, z = result.nextLong() | 1L;
            long decoration = (long) Math.multiplyExact(chunkX, 16) * x
                    + (long) Math.multiplyExact(chunkZ, 16) * z ^ seed;
            result.reseed(decoration + stepIndex + step * 10_000L); return result;
        }
        private int next(int bits) { count++; return (int) (source.nextLong() >>> (64 - bits)); }
        public int nextInt(int bound) {
            if (bound <= 0) throw new IllegalArgumentException("nonpositive placement bound");
            if ((bound & -bound) == bound) return (int) (bound * (long) next(31) >> 31);
            int bits, value; do { bits = next(31); value = bits % bound; }
            while (bits - value + bound - 1 < 0); return value;
        }
        public float nextFloat() { return next(24) * 0x1.0p-24F; }
        public double nextDouble() { return (((long) next(26) << 27) + next(27)) * 0x1.0p-53; }
        public long nextLong() { return ((long) next(32) << 32) + next(32); }
        private void reseed(long seed) { source = new Xoroshiro(seed); }
        public int count() { return count; }
        public PlacementRandom copy() { return new PlacementRandom(source.copy(), count); }
        private void commit(PlacementRandom value) { source = value.source.copy(); count = value.count; }
        public byte[] canonicalContinuation() {
            Xoroshiro copy = source.copy(); ByteBuffer bytes = ByteBuffer.allocate(4 + 16 + 64);
            bytes.putInt(count).putLong(source.lo).putLong(source.hi);
            for (int i = 0; i < 8; i++) bytes.putLong(copy.nextLong()); return bytes.array();
        }
    }
    private static final class Xoroshiro {
        private static final long SILVER = 0x6A09E667F3BCC909L, GOLDEN = 0x9E3779B97F4A7C15L;
        private long lo, hi;
        Xoroshiro(long seed) { long first = seed ^ SILVER; set(mix(first), mix(first + GOLDEN)); }
        Xoroshiro(long lo, long hi) { set(lo, hi); }
        void set(long lo, long hi) { if ((lo | hi) == 0) { this.lo = GOLDEN; this.hi = SILVER; } else { this.lo = lo; this.hi = hi; } }
        long nextLong() { long a = lo, b = hi, value = Long.rotateLeft(a + b, 17) + a;
            b ^= a; lo = Long.rotateLeft(a, 49) ^ b ^ b << 21; hi = Long.rotateLeft(b, 28); return value; }
        Xoroshiro copy() { return new Xoroshiro(lo, hi); }
        static long mix(long value) { value = (value ^ value >>> 30) * 0xBF58476D1CE4E5B9L;
            value = (value ^ value >>> 27) * 0x94D049BB133111EBL; return value ^ value >>> 31; }
    }
    private static final class PositionalRandom {
        private long state;
        PositionalRandom(long seed) { state = (seed ^ 0x5deece66dL) & ((1L << 48) - 1); }
        int next(int bits) { state = (state * 0x5deece66dL + 0xbL) & ((1L << 48) - 1); return (int) (state >>> (48 - bits)); }
        int nextInt(int bound) { if ((bound & -bound) == bound) return (int) (bound * (long) next(31) >> 31);
            int bits, value; do { bits = next(31); value = bits % bound; } while (bits - value + bound - 1 < 0); return value; }
        float nextFloat() { return next(24) * 0x1.0p-24F; }
    }
    private static void validateOfficialQueries(List<OfficialQuery> values) {
        Objects.requireNonNull(values, "ruined-portal official query rows");
        for (OfficialQuery value : values) {
            Objects.requireNonNull(value, "ruined-portal official query row");
            if (!OFFICIAL_QUERY_OPERATIONS.contains(value.operation()))
                throw new IllegalArgumentException("unknown ruined-portal official operation: " + value.operation());
            Objects.requireNonNull(value.position(), "ruined-portal official position");
            if (value.operation().startsWith("getHeight:") && value.position().y() != 0)
                throw new IllegalArgumentException("malformed ruined-portal official height position");
        }
    }
    private static String canonicalOfficialQuerySha256(List<OfficialQuery> values) {
        validateOfficialQueries(values);
        StringBuilder json = new StringBuilder(values.size() * 72 + 2).append('[');
        for (int i = 0; i < values.size(); i++) {
            if (i != 0) json.append(',');
            OfficialQuery value = values.get(i);
            BlockPos position = value.position();
            json.append("{\"operation\":\"").append(value.operation())
                    .append("\",\"position\":[").append(position.x()).append(',')
                    .append(position.y()).append(',').append(position.z()).append("]}");
        }
        return sha256(json.append(']').toString().getBytes(StandardCharsets.UTF_8));
    }

    public static String canonicalOfficialFluidTickSha256(List<FluidTickWrite> values) {
        validateFluidTicks(values);
        StringBuilder json = new StringBuilder(values.size() * 112 + 2).append('[');
        for (int i = 0; i < values.size(); i++) {
            if (i != 0) json.append(',');
            FluidTickWrite value = values.get(i);
            BlockPos position = value.position();
            json.append("{\"delay\":").append(value.delay())
                    .append(",\"fluidKey\":\"").append(value.key())
                    .append("\",\"position\":[").append(position.x()).append(',')
                    .append(position.y()).append(',').append(position.z())
                    .append("],\"priority\":").append(value.priority())
                    .append(",\"subTickOrder\":").append(value.subTickOrder()).append('}');
        }
        return sha256(json.append(']').toString().getBytes(StandardCharsets.UTF_8));
    }

    private static int fluidTickDelay(String key) {
        return switch (key) {
            case LAVA, FLOWING_LAVA -> 30;
            case WATER -> 5;
            default -> throw new IllegalArgumentException("unknown ruined-portal FTIK key: " + key);
        };
    }
    private static void validateFluidTicks(List<FluidTickWrite> values) {
        Objects.requireNonNull(values, "ruined-portal FTIK rows");
        long expectedOrder = 0;
        for (FluidTickWrite value : values) {
            Objects.requireNonNull(value, "ruined-portal FTIK row");
            if (value.delay() != fluidTickDelay(value.key()) || value.priority() != 0)
                throw new IllegalArgumentException("invalid ruined-portal FTIK typed mapping");
            if (value.subTickOrder() != expectedOrder++)
                throw new IllegalArgumentException("non-dense ruined-portal FTIK subTickOrder");
        }
    }
    static List<FluidTick> schema4FluidTicks(List<FluidTickWrite> rawTicks, int chunkX, int chunkZ) {
        validateFluidTicks(rawTicks);
        ArrayList<FluidTick> result = new ArrayList<>();
        long destinationOrder = 0;
        for (FluidTickWrite value : rawTicks)
            if (inChunk(value.position(), chunkX, chunkZ))
                result.add(new FluidTick(packed(value.position(), chunkX, chunkZ), value.key(),
                        value.delay(), TickPriority.NORMAL, destinationOrder++));
        return List.copyOf(result);
    }

    private static void requireIdentity() {
        if (!Mc263RuinedPortalGrammarData.PRODUCER.equals("gameexpert-official-26.3-ruined-portal-oracle-v1")
                || Mc263RuinedPortalGrammarData.TEMPLATE_COUNT != 13
                || Mc263RuinedPortalGrammarData.BLOCK_COUNT != 14_104
                || Mc263RuinedPortalGrammarData.COMMAND_COUNT != 2_643)
            throw new IllegalStateException("accepted ruined-portal grammar identity drift");
    }
    private static boolean inChunk(BlockPos p, int x, int z) { return Math.floorDiv(p.x(), 16) == x && Math.floorDiv(p.z(), 16) == z; }
    private static int packed(BlockPos p, int x, int z) {
        if (!inChunk(p, x, z) || p.y() < Blocks.MIN_Y || p.y() > Blocks.MAX_Y)
            throw new IllegalArgumentException("portal sidecar outside target chunk/build height");
        return Blocks.blockIndex(Math.floorMod(p.x(), 16), p.y(), Math.floorMod(p.z(), 16));
    }
    private static void position(DataOutputStream out, BlockPos p) throws IOException { out.writeInt(p.x()); out.writeInt(p.y()); out.writeInt(p.z()); }
    private static byte[] digest(byte[] value) { try { return MessageDigest.getInstance("SHA-256").digest(value); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); } }
    private static String sha256(byte[] value) { return java.util.HexFormat.of().formatHex(digest(value)); }
}
