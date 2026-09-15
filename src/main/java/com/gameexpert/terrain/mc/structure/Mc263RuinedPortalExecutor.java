package com.gameexpert.terrain.mc.structure;

import com.gameexpert.terrain.mc.feature.Mc263FinalChunkCodec;
import com.gameexpert.terrain.mc.feature.Mc263FinalChunkSidecars;
import com.gameexpert.terrain.mc.structure.Mc263RuinedPortalGrammarData.Command;
import com.gameexpert.terrain.mc.structure.Mc263RuinedPortalGrammarData.Op;
import com.gameexpert.terrain.mc.structure.Mc263RuinedPortalGrammarData.Semantic;
import com.gameexpert.terrain.mc.structure.Mc263RuinedPortalProducer.Box;
import com.gameexpert.terrain.mc.structure.Mc263RuinedPortalProducer.Mirror;
import com.gameexpert.terrain.mc.structure.Mc263RuinedPortalProducer.Plan;
import com.gameexpert.terrain.mc.structure.Mc263RuinedPortalProducer.Pos;
import com.gameexpert.terrain.mc.structure.Mc263RuinedPortalProducer.Rotation;
import com.gameexpert.terrain.mc.structure.Mc263RuinedPortalProducer.VerticalPlacement;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Atomic template and post-placement executor for a persisted 26.3 ruined-portal piece. */
public final class Mc263RuinedPortalExecutor {
    private static final String CHEST = "minecraft:chest";
    private static final String LAVA = "minecraft:lava";
    private static final byte[] RECEIPT_MAGIC = "RUP263C1".getBytes(StandardCharsets.US_ASCII);

    private Mc263RuinedPortalExecutor() { }

    public record Clip(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        public Clip {
            if (minX > maxX || minY > maxY || minZ > maxZ) {
                throw new IllegalArgumentException("inverted ruined-portal clip");
            }
        }
        public boolean contains(Pos p) { return contains(p.x(), p.y(), p.z()); }
        public boolean contains(int x, int y, int z) {
            return x >= minX && x <= maxX && y >= minY && y <= maxY
                    && z >= minZ && z <= maxZ;
        }
        public Clip encapsulate(Box box) {
            return new Clip(Math.min(minX, box.minX()), Math.min(minY, box.minY()),
                    Math.min(minZ, box.minZ()), Math.max(maxX, box.maxX()),
                    Math.max(maxY, box.maxY()), Math.max(maxZ, box.maxZ()));
        }
        public static Clip chunk(int chunkX, int chunkZ, int minY, int maxY) {
            int x = Math.multiplyExact(chunkX, 16), z = Math.multiplyExact(chunkZ, 16);
            return new Clip(x, minY, z, Math.addExact(x, 15), maxY, Math.addExact(z, 15));
        }
    }

    public enum OperationKind {
        GET_BLOCK_STATE, GET_FLUID_STATE, GET_HEIGHT, GET_BLOCK_ENTITY,
        SET_BLOCK, SET_BLOCK_AND_UPDATE, SCHEDULE_FLUID_TICK
    }
    public record Operation(int ordinal, OperationKind kind, Pos position, String before,
            String after, boolean result, int delay) { }
    public record BlockWrite(Pos position, String exactState, int flags, int encounterOrder) { }
    public record LootWrite(Pos position, String facing, String table, long seed,
            int encounterOrder) { }
    public record FluidTick(Pos position, String key, int delay, int encounterOrder) { }
    public record BlockEntityWrite(Pos position, String blockIdentity, String entityType,
            byte[] canonicalNbt, int encounterOrder) {
        public BlockEntityWrite { canonicalNbt = canonicalNbt.clone(); }
        @Override public byte[] canonicalNbt() { return canonicalNbt.clone(); }
    }

    /**
     * Capability boundary. {@code materializeFinalCarrier} must stage geometry and all sidecars
     * without mutating storage; {@code settle} is the sole commit point.
     */
    public interface WorldAccess {
        boolean supportsAtomicSettlement();
        boolean supportsExactState(String exactState);
        boolean supportsLootTable(String lootTable);
        boolean supportsBlockEntity(String blockIdentity, String entityType);
        boolean supportsFluidTick(String fluidKey);
        int minY();
        int maxY();
        String blockState(int x, int y, int z);
        int height(VerticalPlacement placement, int x, int z);
        boolean featureCannotReplace(String exactState);
        boolean collisionFaceFull(String exactState, Direction face);
        Mc263FinalChunkCodec.FinalChunk materializeFinalCarrier(
                Mc263FinalChunkCodec.FinalChunk source, List<BlockWrite> writes,
                Mc263FinalChunkSidecars sidecars);
        void settle(Settlement settlement);
    }
    public enum Direction { DOWN, UP, NORTH, SOUTH, WEST, EAST }

    public static final class Settlement {
        private final Plan plan;
        private final Clip requestedClip;
        private final Clip effectiveClip;
        private final List<Operation> operations;
        private final List<BlockWrite> writes;
        private final List<LootWrite> loot;
        private final List<FluidTick> fluidTicks;
        private final List<BlockEntityWrite> blockEntities;
        private final byte[] successorStr;
        private final byte[] finalMcf;
        private final byte[] randomContinuation;
        private final byte[] receipt;

        private Settlement(Plan plan, Clip requestedClip, Clip effectiveClip,
                List<Operation> operations, List<BlockWrite> writes, List<LootWrite> loot,
                List<FluidTick> fluidTicks, List<BlockEntityWrite> blockEntities,
                byte[] successorStr, byte[] finalMcf, byte[] randomContinuation) {
            this.plan = plan; this.requestedClip = requestedClip; this.effectiveClip = effectiveClip;
            this.operations = List.copyOf(operations); this.writes = List.copyOf(writes);
            this.loot = List.copyOf(loot); this.fluidTicks = List.copyOf(fluidTicks);
            this.blockEntities = List.copyOf(blockEntities);
            this.successorStr = successorStr.clone(); this.finalMcf = finalMcf.clone();
            this.randomContinuation = randomContinuation.clone(); this.receipt = freeze();
        }
        public Plan plan() { return plan; }
        public Clip requestedClip() { return requestedClip; }
        public Clip effectiveClip() { return effectiveClip; }
        public List<Operation> operations() { return operations; }
        public List<BlockWrite> writes() { return writes; }
        public List<LootWrite> loot() { return loot; }
        public List<FluidTick> fluidTicks() { return fluidTicks; }
        public List<BlockEntityWrite> blockEntities() { return blockEntities; }
        public byte[] successorStr() { return successorStr.clone(); }
        public byte[] finalMcf() { return finalMcf.clone(); }
        public byte[] randomContinuation() { return randomContinuation.clone(); }
        public byte[] frozenReceipt() { return receipt.clone(); }
        public String frozenReceiptSha256() { return sha256(receipt); }

        public Mc263FinalChunkSidecars schema4Sidecars(int chunkX, int chunkZ) {
            throw new IllegalStateException(
                    "ruined-portal schema4Sidecars handoff missing: execute must receive "
                            + "an authenticated MCF263LC source carrier");
        }

        private byte[] freeze() {
            try {
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                DataOutputStream out = new DataOutputStream(bytes); out.write(RECEIPT_MAGIC);
                out.writeUTF(plan.structureKey()); out.writeUTF(plan.template());
                out.writeInt(plan.chunkX()); out.writeInt(plan.chunkZ());
                out.writeUTF(plan.rotation().name()); out.writeUTF(plan.mirror().name());
                out.writeInt(operations.size());
                for (Operation value : operations) {
                    out.writeInt(value.ordinal); out.writeByte(value.kind.ordinal());
                    position(out, value.position); out.writeUTF(value.before == null ? "" : value.before);
                    out.writeUTF(value.after == null ? "" : value.after); out.writeBoolean(value.result);
                    out.writeInt(value.delay);
                }
                out.writeInt(writes.size());
                for (BlockWrite value : writes) { position(out, value.position); out.writeUTF(value.exactState); out.writeInt(value.flags); out.writeInt(value.encounterOrder); }
                out.writeInt(loot.size());
                for (LootWrite value : loot) { position(out, value.position); out.writeUTF(value.facing); out.writeUTF(value.table); out.writeLong(value.seed); out.writeInt(value.encounterOrder); }
                out.writeInt(fluidTicks.size());
                for (FluidTick value : fluidTicks) { position(out, value.position); out.writeUTF(value.key); out.writeInt(value.delay); out.writeInt(value.encounterOrder); }
                out.writeInt(blockEntities.size());
                for (BlockEntityWrite value : blockEntities) { position(out, value.position); out.writeUTF(value.blockIdentity); out.writeUTF(value.entityType); out.writeInt(value.encounterOrder); out.writeInt(value.canonicalNbt.length); out.write(value.canonicalNbt); }
                blob(out, successorStr); blob(out, finalMcf); blob(out, randomContinuation);
                out.flush(); byte[] body = bytes.toByteArray();
                ByteArrayOutputStream result = new ByteArrayOutputStream(); result.write(body);
                result.write(digest(body)); return result.toByteArray();
            } catch (IOException impossible) { throw new IllegalStateException(impossible); }
        }
    }

    public static Settlement execute(Plan plan, Clip requestedClip, WorldAccess world,
            PlacementRandom random, Mc263StructureCarrier structureCarrier,
            Mc263FinalChunkCodec.FinalChunk source) {
        Objects.requireNonNull(plan, "ruined-portal plan");
        Objects.requireNonNull(requestedClip, "ruined-portal clip");
        Objects.requireNonNull(world, "ruined-portal world");
        Objects.requireNonNull(random, "ruined-portal placement RNG");
        Objects.requireNonNull(structureCarrier, "ruined-portal STR carrier");
        Objects.requireNonNull(source, "ruined-portal source MCF");
        if (source.chunkX() * 16 != requestedClip.minX()
                || source.chunkZ() * 16 != requestedClip.minZ()
                || requestedClip.maxX() != requestedClip.minX() + 15
                || requestedClip.maxZ() != requestedClip.minZ() + 15) {
            throw new IllegalArgumentException("ruined-portal source/clip mismatch");
        }
        Mc263FinalChunkCodec.validate(source);
        preflight(plan, world);
        PlacementRandom candidate = random.copy();
        Pos center = plan.boundingBox().center();
        Clip effective = requestedClip;
        Stage stage = new Stage(world);
        if (requestedClip.contains(center)) {
            effective = requestedClip.encapsulate(plan.boundingBox());
            placeTemplate(plan, effective, stage, candidate);
            spreadNetherrack(plan, stage, candidate);
            dripBelowPortal(plan, stage, candidate);
            if (plan.properties().vines() || plan.properties().overgrown()) {
                decorateBox(plan, stage, candidate);
            }
        }
        validateSidecars(stage);
        Mc263FinalChunkSidecars sidecars = sidecars(stage, source);
        Mc263FinalChunkCodec.FinalChunk finalChunk = world.materializeFinalCarrier(
                source, stage.writes, sidecars);
        byte[] mcf = Mc263FinalChunkCodec.encode(finalChunk);
        if (!Arrays.equals(mcf, Mc263FinalChunkCodec.encode(Mc263FinalChunkCodec.decode(mcf)))) {
            throw new IllegalStateException("noncanonical ruined-portal schema-4 carrier");
        }
        Settlement settlement = new Settlement(plan, requestedClip, effective, stage.operations,
                stage.writes, stage.loot, stage.fluidTicks, stage.blockEntities,
                structureCarrier.receiptBytes(), mcf, candidate.canonicalContinuation());
        world.settle(settlement);
        random.commit(candidate);
        return settlement;
    }

    private static void preflight(Plan plan, WorldAccess world) {
        if (!world.supportsAtomicSettlement()) {
            throw new UnsupportedOperationException("atomic ruined-portal settlement required");
        }
        if (!world.supportsLootTable(Mc263RuinedPortalProducer.LOOT_TABLE)
                || !world.supportsBlockEntity(CHEST, CHEST)
                || !world.supportsFluidTick(LAVA)) {
            throw new UnsupportedOperationException("ruined-portal semantic lanes are incomplete");
        }
        LinkedHashSet<String> states = new LinkedHashSet<>(plan.grammar().states());
        states.addAll(List.of("minecraft:air", "minecraft:netherrack", "minecraft:magma_block",
                "minecraft:crying_obsidian", "minecraft:cracked_stone_bricks",
                "minecraft:mossy_stone_bricks", "minecraft:stone_slab[type=bottom,waterlogged=false]",
                "minecraft:stone_brick_slab[type=bottom,waterlogged=false]",
                "minecraft:mossy_stone_brick_slab[type=bottom,waterlogged=false]",
                "minecraft:jungle_leaves[distance=7,persistent=true,waterlogged=false]",
                "minecraft:vine[east=false,north=false,south=false,up=false,west=true]"));
        for (String state : states) if (!world.supportsExactState(normalize(state))) {
            throw new UnsupportedOperationException("unsupported ruined-portal exact state: " + state);
        }
    }

    private static void placeTemplate(Plan plan, Clip clip, Stage stage,
            PlacementRandom random) {
        for (Command command : plan.grammar().commands()) {
            for (int offset = 0; offset < command.count(); offset++) {
                Pos local = new Pos(command.x() + command.dx() * offset,
                        command.y() + command.dy() * offset, command.z() + command.dz() * offset);
                Pos transformed = Mc263RuinedPortalProducer.transform(local, plan.pivot(),
                        plan.rotation(), plan.mirror());
                Pos worldPos = add(plan.origin(), transformed);
                if (!clip.contains(worldPos)) continue;
                String state = command.semantic() == Semantic.JIGSAW
                        ? command.finalState() : plan.grammar().states().get(command.state());
                state = process(plan, worldPos, state, stage);
                if (state == null) continue;
                String previousFluid = stage.fluid(worldPos);
                if (command.semantic() == Semantic.LOOT_CHEST) {
                    stage.set(worldPos, "minecraft:barrier", 820, false);
                }
                String placed = rotateState(state, plan.rotation(), plan.mirror());
                stage.set(worldPos, placed, 2, false);
                if (blockKey(placed).equals(LAVA)) stage.schedule(worldPos, LAVA, 30);
                if (command.semantic() == Semantic.LOOT_CHEST) {
                    long seed = random.nextLong();
                    int order = stage.loot.size();
                    String facing = property(placed, "facing", "north");
                    stage.loot.add(new LootWrite(worldPos, facing,
                            Mc263RuinedPortalProducer.LOOT_TABLE, seed, order));
                    stage.blockEntities.add(new BlockEntityWrite(worldPos, CHEST, CHEST,
                            chestNbt(worldPos, seed), order));
                    stage.blockEntity(worldPos);
                }
                if (previousFluid.startsWith("minecraft:water") && placed.contains("waterlogged=false")) {
                    String waterlogged = placed.replace("waterlogged=false", "waterlogged=true");
                    stage.set(worldPos, waterlogged, 2, false);
                }
            }
        }
    }

    private static String process(Plan plan, Pos pos, String source, Stage stage) {
        String key = blockKey(source);
        if (key.equals("minecraft:structure_block")) return null;
        if (!plan.properties().airPocket() && key.equals("minecraft:air")) return null;
        Legacy48 rule = positional(pos);
        if (key.equals("minecraft:gold_block") && rule.nextFloat() < .3F) source = "minecraft:air";
        else if (key.equals(LAVA)) {
            if (plan.setup().placement() == VerticalPlacement.ON_OCEAN_FLOOR) source = "minecraft:magma_block";
            else if (plan.properties().cold()) source = "minecraft:netherrack";
            else if (rule.nextFloat() < .2F) source = "minecraft:magma_block";
        } else if (key.equals("minecraft:netherrack") && !plan.properties().cold()
                && rule.nextFloat() < .07F) source = "minecraft:magma_block";
        source = age(source, plan.properties().mossiness(), positional(pos));
        String existing = stage.state(pos);
        if (stage.world.featureCannotReplace(existing)) return null;
        if (blockKey(existing).equals(LAVA) && !fullBlock(source)) return "minecraft:lava[level=0]";
        return source;
    }

    private static String age(String state, float mossiness, Legacy48 random) {
        String key = blockKey(state);
        if (Set.of("minecraft:stone_bricks", "minecraft:stone",
                "minecraft:chiseled_stone_bricks").contains(key)) {
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
                    ? state.replace(key, "minecraft:mossy_stone_brick_stairs")
                    : "minecraft:mossy_stone_brick_slab[type=bottom,waterlogged=false]";
            return selected == 0 ? "minecraft:stone_slab[type=bottom,waterlogged=false]"
                    : "minecraft:stone_brick_slab[type=bottom,waterlogged=false]";
        }
        if (key.endsWith("_slab") && random.nextFloat() < mossiness) {
            return state.replace(key, "minecraft:mossy_stone_brick_slab");
        }
        if (key.endsWith("_wall") && random.nextFloat() < mossiness) {
            return state.replace(key, "minecraft:mossy_stone_brick_wall");
        }
        if (key.equals("minecraft:obsidian") && random.nextFloat() < .15F) {
            return "minecraft:crying_obsidian";
        }
        return state;
    }

    private static String randomStairs(Legacy48 random, String key) {
        String facing = List.of("north", "east", "south", "west").get(random.nextInt(4));
        String half = random.nextInt(2) == 0 ? "bottom" : "top";
        return key + "[facing=" + facing + ",half=" + half
                + ",shape=straight,waterlogged=false]";
    }

    private static void spreadNetherrack(Plan plan, Stage stage, PlacementRandom random) {
        boolean follow = plan.setup().placement() == VerticalPlacement.ON_LAND_SURFACE
                || plan.setup().placement() == VerticalPlacement.ON_OCEAN_FLOOR;
        Pos center = plan.boundingBox().center();
        float[] chance = {1, 1, 1, 1, 1, 1, 1, .9F, .9F, .8F, .7F, .6F, .4F, .2F};
        int average = ((plan.boundingBox().maxX() - plan.boundingBox().minX() + 1)
                + (plan.boundingBox().maxZ() - plan.boundingBox().minZ() + 1)) / 2;
        int adjustment = random.nextInt(Math.max(1, 8 - average / 2));
        for (int x = center.x() - chance.length; x <= center.x() + chance.length; x++) {
            for (int z = center.z() - chance.length; z <= center.z() + chance.length; z++) {
                int distance = Math.max(0, Math.abs(x - center.x()) + Math.abs(z - center.z())
                        + adjustment);
                if (distance >= chance.length || random.nextDouble() >= chance[distance]) continue;
                int surface = stage.height(plan.setup().placement(), x, z) - 1;
                int y = follow ? surface : Math.min(plan.boundingBox().minY(), surface);
                Pos pos = new Pos(x, y, z);
                if (Math.abs(y - plan.boundingBox().minY()) > 3 || !replaceable(plan, stage, pos)) continue;
                netherrackOrMagma(plan, stage, random, pos);
                if (plan.properties().overgrown()) leaves(plan, stage, random, pos);
                drip(plan, stage, random, below(pos));
            }
        }
    }

    private static void dripBelowPortal(Plan plan, Stage stage, PlacementRandom random) {
        Box box = plan.boundingBox();
        for (int x = box.minX() + 1; x < box.maxX(); x++) for (int z = box.minZ() + 1; z < box.maxZ(); z++) {
            Pos pos = new Pos(x, box.minY(), z);
            if (blockKey(stage.state(pos)).equals("minecraft:netherrack")) drip(plan, stage, random, below(pos));
        }
    }

    private static void drip(Plan plan, Stage stage, PlacementRandom random, Pos start) {
        Pos pos = start; netherrackOrMagma(plan, stage, random, pos);
        for (int remaining = 8; remaining > 0 && random.nextFloat() < .5F; remaining--) {
            pos = below(pos); netherrackOrMagma(plan, stage, random, pos);
        }
    }

    private static void netherrackOrMagma(Plan plan, Stage stage, PlacementRandom random, Pos pos) {
        if (pos.y() < stage.world.minY() || pos.y() > stage.world.maxY()) return;
        String state = !plan.properties().cold() && random.nextFloat() < .07F
                ? "minecraft:magma_block" : "minecraft:netherrack";
        stage.set(pos, state, 3, true);
    }

    private static void decorateBox(Plan plan, Stage stage, PlacementRandom random) {
        Box box = plan.boundingBox();
        for (int x = box.minX(); x <= box.maxX(); x++) for (int y = box.minY(); y <= box.maxY(); y++)
            for (int z = box.minZ(); z <= box.maxZ(); z++) {
                Pos pos = new Pos(x, y, z);
                if (plan.properties().vines()) vine(stage, random, pos);
                if (plan.properties().overgrown()) leaves(plan, stage, random, pos);
            }
    }

    private static void vine(Stage stage, PlacementRandom random, Pos pos) {
        String state = stage.state(pos);
        if (isAir(state) || blockKey(state).equals("minecraft:vine")) return;
        Direction direction = List.of(Direction.NORTH, Direction.EAST, Direction.SOUTH,
                Direction.WEST).get(random.nextInt(4));
        Pos neighbor = relative(pos, direction);
        if (!isAir(stage.state(neighbor)) || !stage.world.collisionFaceFull(state, direction)) return;
        Direction attached = opposite(direction);
        String vine = "minecraft:vine[east=" + (attached == Direction.EAST)
                + ",north=" + (attached == Direction.NORTH) + ",south="
                + (attached == Direction.SOUTH) + ",up=false,west="
                + (attached == Direction.WEST) + "]";
        stage.set(neighbor, vine, 3, true);
    }

    private static void leaves(Plan plan, Stage stage, PlacementRandom random, Pos pos) {
        if (random.nextFloat() < .5F && blockKey(stage.state(pos)).equals("minecraft:netherrack")
                && isAir(stage.state(above(pos)))) {
            stage.set(above(pos), "minecraft:jungle_leaves[distance=7,persistent=true,waterlogged=false]", 3, true);
        }
    }

    private static boolean replaceable(Plan plan, Stage stage, Pos pos) {
        String state = stage.state(pos), key = blockKey(state);
        return !isAir(state) && !key.equals("minecraft:obsidian")
                && !stage.world.featureCannotReplace(state) && !key.equals(LAVA);
    }

    private static void validateSidecars(Stage stage) {
        Set<Pos> lootPositions = new LinkedHashSet<>();
        for (LootWrite loot : stage.loot) {
            if (!lootPositions.add(loot.position)) throw new IllegalStateException("duplicate ruined-portal LOOT");
            if (!blockKey(stage.overlay.get(loot.position)).equals(CHEST)) {
                throw new IllegalStateException("ruined-portal LOOT lacks chest state");
            }
        }
        if (stage.loot.size() != stage.blockEntities.size()) {
            throw new IllegalStateException("ruined-portal LOOT/BENT mismatch");
        }
    }

    private static Mc263FinalChunkSidecars sidecars(Stage stage,
            Mc263FinalChunkCodec.FinalChunk source) {
        Mc263FinalChunkSidecars input = source.sidecars();
        requireAuthenticatedLootRows(source, stage);
        ArrayList<Mc263FinalChunkSidecars.FluidTick> ftik =
                new ArrayList<>(input.fluidTicks());
        LinkedHashSet<String> scheduled = new LinkedHashSet<>();
        for (Mc263FinalChunkSidecars.FluidTick value : ftik) {
            scheduled.add(value.packed() + "\0" + value.key() + "\0" + value.subTickOrder());
        }
        for (FluidTick value : stage.fluidTicks)
                if (inChunk(value.position, source.chunkX(), source.chunkZ())) {
            int packed = packed(value.position, source.chunkX(), source.chunkZ());
            if (scheduled.add(packed + "\0" + value.key + "\0" + value.encounterOrder)) {
                ftik.add(new Mc263FinalChunkSidecars.FluidTick(packed, value.key, value.delay,
                        Mc263FinalChunkSidecars.TickPriority.NORMAL, value.encounterOrder));
            }
        }
        ArrayList<Mc263FinalChunkSidecars.BlockEntity> bent =
                new ArrayList<>(input.blockEntities());
        for (BlockEntityWrite value : stage.blockEntities)
                if (inChunk(value.position, source.chunkX(), source.chunkZ())) {
            mergeBlockEntity(bent, new Mc263FinalChunkSidecars.BlockEntity(
                    packed(value.position, source.chunkX(), source.chunkZ()),
                    value.blockIdentity, value.entityType, value.canonicalNbt));
        }
        return new Mc263FinalChunkSidecars(input.blockTicks(), ftik, input.loot(),
                input.spawners(), input.owners(), input.archaeology(), input.bees(), bent,
                input.entities(), input.containerLootDeclarations());
    }

    private static void requireAuthenticatedLootRows(
            Mc263FinalChunkCodec.FinalChunk source, Stage stage) {
        for (LootWrite value : stage.loot)
                if (inChunk(value.position, source.chunkX(), source.chunkZ())) {
            Mc263FinalChunkSidecars.Loot row = new Mc263FinalChunkSidecars.Loot(
                    packed(value.position, source.chunkX(), source.chunkZ()), value.facing,
                    value.table, value.seed);
            if (!source.sidecars().loot().contains(row)) {
                throw new IllegalStateException(
                        "ruined-portal LDEC upstream handoff missing: "
                                + "Mc263FinalChunkAssembler must provide the authenticated "
                                + "LOOT row and declaration before materialization");
            }
        }
    }

    private static void mergeBlockEntity(
            List<Mc263FinalChunkSidecars.BlockEntity> target,
            Mc263FinalChunkSidecars.BlockEntity addition) {
        Mc263FinalChunkSidecars.BlockEntity existing = target.stream()
                .filter(value -> value.packed() == addition.packed()).findFirst().orElse(null);
        if (existing == null) {
            target.add(addition);
        } else if (!sameBlockEntity(existing, addition)) {
            throw new IllegalArgumentException(
                    "ruined-portal BENT conflicts with existing sidecar");
        }
    }

    private static boolean sameBlockEntity(Mc263FinalChunkSidecars.BlockEntity left,
            Mc263FinalChunkSidecars.BlockEntity right) {
        return left.packed() == right.packed()
                && left.blockIdentity().equals(right.blockIdentity())
                && left.entityType().equals(right.entityType())
                && Arrays.equals(left.canonicalNbt(), right.canonicalNbt());
    }

    private static final class Stage {
        private final WorldAccess world;
        private final Map<Pos, String> overlay = new LinkedHashMap<>();
        private final List<Operation> operations = new ArrayList<>();
        private final List<BlockWrite> writes = new ArrayList<>();
        private final List<LootWrite> loot = new ArrayList<>();
        private final List<FluidTick> fluidTicks = new ArrayList<>();
        private final List<BlockEntityWrite> blockEntities = new ArrayList<>();
        Stage(WorldAccess world) { this.world = world; }
        String state(Pos pos) {
            String state = overlay.get(pos);
            if (state == null) state = world.blockState(pos.x(), pos.y(), pos.z());
            operations.add(new Operation(operations.size(), OperationKind.GET_BLOCK_STATE,
                    pos, state, null, true, 0)); return state;
        }
        String fluid(Pos pos) {
            String state = state(pos); String fluid = blockKey(state).equals("minecraft:water")
                    ? "minecraft:water" : blockKey(state).equals(LAVA) ? LAVA : "minecraft:empty";
            operations.add(new Operation(operations.size(), OperationKind.GET_FLUID_STATE,
                    pos, fluid, null, true, 0)); return fluid;
        }
        int height(VerticalPlacement placement, int x, int z) {
            int value = world.height(placement, x, z);
            operations.add(new Operation(operations.size(), OperationKind.GET_HEIGHT,
                    new Pos(x, value, z), null, null, true, value)); return value;
        }
        void blockEntity(Pos pos) {
            operations.add(new Operation(operations.size(), OperationKind.GET_BLOCK_ENTITY,
                    pos, CHEST, CHEST, true, 0));
        }
        void set(Pos pos, String state, int flags, boolean update) {
            String before = overlay.containsKey(pos) ? overlay.get(pos) : world.blockState(pos.x(), pos.y(), pos.z());
            overlay.put(pos, normalize(state));
            writes.add(new BlockWrite(pos, normalize(state), flags, writes.size()));
            operations.add(new Operation(operations.size(), update
                    ? OperationKind.SET_BLOCK_AND_UPDATE : OperationKind.SET_BLOCK,
                    pos, before, normalize(state), true, 0));
        }
        void schedule(Pos pos, String key, int delay) {
            int order = fluidTicks.size(); fluidTicks.add(new FluidTick(pos, key, delay, order));
            operations.add(new Operation(operations.size(), OperationKind.SCHEDULE_FLUID_TICK,
                    pos, key, null, true, delay));
        }
    }

    /** Exact Xoroshiro128++ decoration stream with copy/commit atomicity. */
    public static final class PlacementRandom {
        private Xoroshiro source; private int count;
        private PlacementRandom(Xoroshiro source, int count) { this.source = source; this.count = count; }
        public static PlacementRandom forChunk(long worldSeed, int chunkX, int chunkZ,
                int stepLocalIndex) {
            PlacementRandom result = new PlacementRandom(new Xoroshiro(0), 0);
            result.reseed(worldSeed); long x = result.nextLong() | 1L, z = result.nextLong() | 1L;
            long decoration = (long) Math.multiplyExact(chunkX, 16) * x
                    + (long) Math.multiplyExact(chunkZ, 16) * z ^ worldSeed;
            result.reseed(decoration + stepLocalIndex + 40_000L); return result;
        }
        public int nextInt(int bound) {
            if (bound <= 0) throw new IllegalArgumentException("nonpositive ruined-portal RNG bound");
            if ((bound & -bound) == bound) return (int) (bound * (long) next(31) >> 31);
            int bits, value; do { bits = next(31); value = bits % bound; }
            while (bits - value + bound - 1 < 0); return value;
        }
        public float nextFloat() { return next(24) * 0x1.0p-24F; }
        public double nextDouble() { return (((long) next(26) << 27) + next(27)) * 0x1.0p-53; }
        public long nextLong() { return ((long) next(32) << 32) + next(32); }
        private int next(int bits) { count++; return (int) (source.nextLong() >>> (64 - bits)); }
        private void reseed(long seed) { source = new Xoroshiro(seed); }
        public PlacementRandom copy() { return new PlacementRandom(source.copy(), count); }
        private void commit(PlacementRandom value) { source = value.source.copy(); count = value.count; }
        public int count() { return count; }
        public byte[] canonicalContinuation() {
            Xoroshiro copy = source.copy(); ByteBuffer out = ByteBuffer.allocate(84);
            out.putInt(count).putLong(source.lo).putLong(source.hi);
            for (int i = 0; i < 8; i++) out.putLong(copy.nextLong()); return out.array();
        }
    }

    private static final class Xoroshiro {
        private static final long SILVER = 0x6A09E667F3BCC909L, GOLDEN = 0x9E3779B97F4A7C15L;
        private long lo, hi;
        Xoroshiro(long seed) { long first = seed ^ SILVER; set(mix(first), mix(first + GOLDEN)); }
        Xoroshiro(long lo, long hi) { set(lo, hi); }
        void set(long lo, long hi) { if ((lo | hi) == 0) { this.lo = GOLDEN; this.hi = SILVER; } else { this.lo = lo; this.hi = hi; } }
        long nextLong() { long a = lo, b = hi, value = Long.rotateLeft(a + b, 17) + a; b ^= a; lo = Long.rotateLeft(a, 49) ^ b ^ b << 21; hi = Long.rotateLeft(b, 28); return value; }
        Xoroshiro copy() { return new Xoroshiro(lo, hi); }
        static long mix(long value) { value = (value ^ value >>> 30) * 0xBF58476D1CE4E5B9L; value = (value ^ value >>> 27) * 0x94D049BB133111EBL; return value ^ value >>> 31; }
    }

    /**
     * Vanilla {@code Mth#getSeed}: the x term is a signed i32 multiply (`imul`) that is only then
     * widened (`i2l`), so a 64-bit x term diverges for {@code |x| > 686}.
     */
    static long coordinateSeed(int x, int y, int z) {
        long value = (long) (x * 3_129_871) ^ (long) z * 116_129_781L ^ y;
        return (value * value * 42_317_861L + value * 11L) >> 16;
    }

    private static Legacy48 positional(Pos pos) {
        return new Legacy48(coordinateSeed(pos.x(), pos.y(), pos.z()));
    }
    private static final class Legacy48 {
        private static final long MULTIPLIER = 0x5deece66dL, ADDEND = 0xbL, MASK = (1L << 48) - 1;
        private long state; Legacy48(long seed) { state = (seed ^ MULTIPLIER) & MASK; }
        int next(int bits) { state = (state * MULTIPLIER + ADDEND) & MASK; return (int) (state >>> (48 - bits)); }
        int nextInt(int bound) { if ((bound & -bound) == bound) return (int) (bound * (long) next(31) >> 31); int bits, value; do { bits = next(31); value = bits % bound; } while (bits - value + bound - 1 < 0); return value; }
        float nextFloat() { return next(24) * 0x1.0p-24F; }
    }

    static String rotateState(String exact, Rotation rotation, Mirror mirror) {
        String facing = property(exact, "facing", null);
        if (facing == null) return normalize(exact);
        Direction direction = direction(facing);
        if (mirror == Mirror.FRONT_BACK) {
            if (direction == Direction.EAST) direction = Direction.WEST;
            else if (direction == Direction.WEST) direction = Direction.EAST;
        }
        int turns = switch (rotation) { case NONE -> 0; case CLOCKWISE_90 -> 1; case CLOCKWISE_180 -> 2; case COUNTERCLOCKWISE_90 -> 3; };
        for (int i = 0; i < turns; i++) direction = rotateClockwise(direction);
        return normalize(replaceProperty(exact, "facing", direction.name().toLowerCase()));
    }

    private static byte[] chestNbt(Pos pos, long seed) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(); DataOutputStream out = new DataOutputStream(bytes);
            out.writeByte(10); out.writeUTF(""); tagString(out, "LootTable", Mc263RuinedPortalProducer.LOOT_TABLE);
            out.writeByte(10); out.writeUTF("components"); out.writeByte(0);
            tagInt(out, "x", pos.x()); tagInt(out, "y", pos.y()); tagInt(out, "z", pos.z());
            tagString(out, "id", CHEST); out.writeByte(4); out.writeUTF("LootTableSeed"); out.writeLong(seed);
            out.writeByte(0); out.flush(); return bytes.toByteArray();
        } catch (IOException impossible) { throw new IllegalStateException(impossible); }
    }
    private static void tagInt(DataOutputStream out, String name, int value) throws IOException { out.writeByte(3); out.writeUTF(name); out.writeInt(value); }
    private static void tagString(DataOutputStream out, String name, String value) throws IOException { out.writeByte(8); out.writeUTF(name); out.writeUTF(value); }
    private static void position(DataOutputStream out, Pos value) throws IOException { out.writeInt(value.x()); out.writeInt(value.y()); out.writeInt(value.z()); }
    private static void blob(DataOutputStream out, byte[] value) throws IOException { out.writeInt(value.length); out.write(value); }
    private static byte[] digest(byte[] value) { try { return MessageDigest.getInstance("SHA-256").digest(value); } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); } }
    private static String sha256(byte[] value) { return java.util.HexFormat.of().formatHex(digest(value)); }
    private static Pos add(Pos a, Pos b) { return new Pos(Math.addExact(a.x(), b.x()), Math.addExact(a.y(), b.y()), Math.addExact(a.z(), b.z())); }
    private static Pos below(Pos p) { return new Pos(p.x(), p.y() - 1, p.z()); }
    private static Pos above(Pos p) { return new Pos(p.x(), p.y() + 1, p.z()); }
    private static Pos relative(Pos p, Direction d) { return switch (d) { case DOWN -> below(p); case UP -> above(p); case NORTH -> new Pos(p.x(), p.y(), p.z() - 1); case SOUTH -> new Pos(p.x(), p.y(), p.z() + 1); case WEST -> new Pos(p.x() - 1, p.y(), p.z()); case EAST -> new Pos(p.x() + 1, p.y(), p.z()); }; }
    private static Direction opposite(Direction d) { return switch (d) { case DOWN -> Direction.UP; case UP -> Direction.DOWN; case NORTH -> Direction.SOUTH; case SOUTH -> Direction.NORTH; case WEST -> Direction.EAST; case EAST -> Direction.WEST; }; }
    private static Direction direction(String name) { return Direction.valueOf(name.toUpperCase(java.util.Locale.ROOT)); }
    private static Direction rotateClockwise(Direction d) { return switch (d) { case NORTH -> Direction.EAST; case EAST -> Direction.SOUTH; case SOUTH -> Direction.WEST; case WEST -> Direction.NORTH; default -> d; }; }
    private static String blockKey(String exact) { if (exact == null) return ""; int bracket = exact.indexOf('['); return bracket < 0 ? exact : exact.substring(0, bracket); }
    private static boolean isAir(String state) { return Set.of("minecraft:air", "minecraft:cave_air", "minecraft:void_air").contains(blockKey(state)); }
    private static boolean fullBlock(String state) { String key = blockKey(state); return !isAir(state) && !key.endsWith("_stairs") && !key.endsWith("_slab") && !key.equals("minecraft:vine") && !key.equals(CHEST); }
    private static String normalize(String state) { return state.replace(", ", ","); }
    private static String property(String state, String name, String fallback) { int start = state.indexOf(name + "="); if (start < 0) return fallback; start += name.length() + 1; int end = state.indexOf(',', start); if (end < 0) end = state.indexOf(']', start); return end < 0 ? fallback : state.substring(start, end); }
    private static String replaceProperty(String state, String name, String value) { String old = property(state, name, null); return old == null ? state : state.replace(name + "=" + old, name + "=" + value); }
    private static boolean inChunk(Pos pos, int chunkX, int chunkZ) { return Math.floorDiv(pos.x(), 16) == chunkX && Math.floorDiv(pos.z(), 16) == chunkZ; }
    private static int packed(Pos pos, int chunkX, int chunkZ) { int x = Math.floorMod(pos.x(), 16), z = Math.floorMod(pos.z(), 16), y = pos.y() - com.gameexpert.terrain.Blocks.MIN_Y; if (y < 0 || y >= com.gameexpert.terrain.Blocks.CHUNK_Y) throw new IllegalArgumentException("ruined-portal sidecar outside world"); return (y << 8) | (z << 4) | x; }
}
