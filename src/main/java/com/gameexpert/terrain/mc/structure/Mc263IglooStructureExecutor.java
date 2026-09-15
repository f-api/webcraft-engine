package com.gameexpert.terrain.mc.structure;

import com.gameexpert.terrain.mc.structure.Mc263HardcodedStructureCarrier.Kind;
import com.gameexpert.terrain.mc.structure.Mc263HardcodedStructureCarrier.PieceFact;
import com.gameexpert.terrain.mc.structure.Mc263HardcodedStructureCarrier.Rotation;
import com.gameexpert.terrain.mc.structure.Mc263HardcodedStructureCarrier.Template;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Dormant atomic executor for the repository-owned pinned igloo block grammar.
 *
 * <p>The three programs below are a code-native procedural transcription, not copied NBT or an
 * external template payload. Geometry predicates create the regular shells and rooms; small sets
 * contain only the material exceptions that give the laboratory its weathered variation. Entries
 * are emitted in the pinned full-block, non-full-block, block-entity order.</p>
 */
public final class Mc263IglooStructureExecutor {
    public static final String VERSION = Mc263HardcodedStructureCarrier.VERSION;
    public static final String SERVER_SHA1 = Mc263HardcodedStructureCarrier.SERVER_SHA1;
    public static final String LOOT_TABLE = "minecraft:chests/igloo_chest";

    /** Empty items/recipes with all furnace cooking and remaining-lit counters at zero. */
    public static final String EMPTY_FURNACE = "minecraft:igloo/empty_furnace";
    /** Empty item list, before the DATA marker assigns the randomized loot table below it. */
    public static final String EMPTY_CHEST = "minecraft:igloo/empty_chest";
    /** Black, non-glowing sign: blank, {@code <----}, {@code ---->}, blank; empty back text. */
    public static final String DIRECTION_SIGN = "minecraft:igloo/direction_sign";
    /** Fuel/BrewTime zero; slot 1 contains one splash potion of weakness. */
    public static final String WEAKNESS_BREWING_STAND =
            "minecraft:igloo/weakness_splash_potion_brewing_stand";

    private static final String AIR = "minecraft:air";
    private static final String LADDER = "minecraft:ladder";
    private static final String SNOW_BLOCK = "minecraft:snow_block";
    private static final String STRUCTURE_BLOCK = "minecraft:structure_block";
    private static final int GENERATION_HEIGHT = 90;
    private static final int MAX_BLOCKS = 4_096;
    private static final int MAX_MARKERS = 64;
    private static final EnumMap<Template, TemplateProgram> PROGRAMS = createPrograms();

    private Mc263IglooStructureExecutor() { }

    public record BlockPos(int x, int y, int z) {
        BlockPos below() {
            return new BlockPos(x, Math.subtractExact(y, 1), z);
        }
    }

    public record Clip(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        public Clip {
            if (minX > maxX || minY > maxY || minZ > maxZ) {
                throw new IllegalArgumentException("inverted igloo clip");
            }
        }

        public boolean contains(BlockPos position) {
            return position.x() >= minX && position.x() <= maxX
                    && position.y() >= minY && position.y() <= maxY
                    && position.z() >= minZ && position.z() <= maxZ;
        }
    }

    /** Pure declarations are called during preflight; all other methods are observations. */
    public interface QuerySource {
        boolean supportsExactState(String exactState);
        boolean supportsBlockEntityData(String semantic);
        boolean supportsWorldSurfaceWg();
        boolean supportsBlockStateQuery();
        boolean supportsRandomizableContainerQuery();
        boolean supportsLootSidecar();

        int worldSurfaceWg(int blockX, int blockZ);
        String blockState(BlockPos position, PendingView pending);
        boolean hasRandomizableContainer(BlockPos position, PendingView pending);
    }

    public interface PendingView {
        String blockState(BlockPos position);
    }

    public interface RandomSource {
        long nextLong();
    }

    /** The implementation must publish the supplied ordered list atomically. */
    public interface AtomicSink {
        void commit(List<Mutation> orderedMutations);
    }

    public enum MutationKind { BLOCK, BLOCK_ENTITY, LOOT }

    public record Mutation(MutationKind kind, BlockPos position, String value, long seed,
            int flags) {
        public Mutation {
            Objects.requireNonNull(kind, "mutation kind");
            Objects.requireNonNull(position, "mutation position");
            requireResource(value, "mutation value");
            if (kind == MutationKind.BLOCK && flags != 2) {
                throw new IllegalArgumentException("igloo block mutations require flags=2");
            }
            if (kind == MutationKind.LOOT && flags != 0) {
                throw new IllegalArgumentException("igloo loot sidecars do not carry flags");
            }
            if (kind == MutationKind.BLOCK_ENTITY && flags != 0 && flags != 1) {
                throw new IllegalArgumentException("igloo block-entity seed flag is invalid");
            }
            if (kind == MutationKind.BLOCK_ENTITY && flags == 0 && seed != 0L) {
                throw new IllegalArgumentException("unflagged igloo block-entity seed");
            }
            if (kind == MutationKind.BLOCK_ENTITY && flags == 1
                    && !EMPTY_CHEST.equals(value)) {
                throw new IllegalArgumentException(
                        "only the igloo chest may carry a template random seed");
            }
        }

        static Mutation block(BlockPos position, String state) {
            return new Mutation(MutationKind.BLOCK, position, state, 0L, 2);
        }

        static Mutation blockEntity(BlockPos position, String semantic, Long randomSeed) {
            return new Mutation(MutationKind.BLOCK_ENTITY, position, semantic,
                    randomSeed == null ? 0L : randomSeed, randomSeed == null ? 0 : 1);
        }

        public boolean hasRandomSeed() { return kind == MutationKind.BLOCK_ENTITY && flags == 1; }

        static Mutation loot(BlockPos position, long seed) {
            return new Mutation(MutationKind.LOOT, position, LOOT_TABLE, seed, 0);
        }
    }

    public enum TraceKind {
        HEIGHT_QUERY,
        BEGIN_PIECE,
        PROCESSOR,
        BLOCK,
        BLOCK_ENTITY,
        ENTITY,
        CLIPPED_ENTITY,
        CLIPPED_BLOCK,
        MARKER_CLEAR,
        CLIPPED_MARKER,
        CONTAINER_QUERY,
        LOOT_DRAW,
        LOOT,
        TRAPDOOR_BELOW_QUERY,
        TRAPDOOR_COVER,
        END_PIECE
    }

    public record TraceEvent(TraceKind kind, Template template, BlockPos position,
            String detail) {
        public TraceEvent {
            Objects.requireNonNull(kind, "trace kind");
            detail = detail == null ? "" : detail;
        }
    }

    public record Result(List<Mutation> orderedMutations, List<TraceEvent> trace, Clip clip) {
        public Result {
            orderedMutations = List.copyOf(orderedMutations);
            trace = List.copyOf(trace);
            Objects.requireNonNull(clip, "igloo execution clip");
        }
    }

    /** Executes the carried piece graph without reseeding or consuming its start RNG continuation. */
    public static Result execute(Mc263HardcodedStructureCarrier carrier, QuerySource queries,
            RandomSource random, Clip clip, AtomicSink sink) {
        Objects.requireNonNull(carrier, "carrier");
        Objects.requireNonNull(queries, "queries");
        Objects.requireNonNull(random, "random");
        Objects.requireNonNull(clip, "clip");
        Objects.requireNonNull(sink, "sink");
        preflight(carrier, queries);

        Context context = new Context(queries, random, clip);
        for (PieceFact piece : carrier.orderedPieces()) {
            TemplateProgram program = PROGRAMS.get(piece.template());
            int yDelta = alignedYDelta(piece, program, carrier.rotation(), context);
            context.trace(TraceKind.BEGIN_PIECE, piece.template(), null, "");
            for (BlockEntry block : program.orderedBlocks()) {
                BlockPos position = worldPosition(piece, program, carrier.rotation(),
                        block.localPosition(), yDelta);
                String state = block.states().forRotation(carrier.rotation());
                context.trace(TraceKind.PROCESSOR, piece.template(), position,
                        "BlockIgnoreProcessor.STRUCTURE_BLOCK");
                if (blockKey(state).equals(STRUCTURE_BLOCK)) continue;
                if (!clip.contains(position)) {
                    context.trace(TraceKind.CLIPPED_BLOCK, piece.template(), position, state);
                    continue;
                }
                context.mutations.add(Mutation.block(position, state));
                context.trace(TraceKind.BLOCK, piece.template(), position, state);
                if (block.blockEntitySemantic() != null) {
                    Long randomSeed = null;
                    if (block.blockEntitySemantic().equals(EMPTY_CHEST)) {
                        context.trace(TraceKind.CONTAINER_QUERY, piece.template(), position,
                                "template");
                        if (queries.hasRandomizableContainer(position, context)) {
                            randomSeed = random.nextLong();
                            context.trace(TraceKind.LOOT_DRAW, piece.template(), position,
                                    Long.toString(randomSeed));
                        }
                    }
                    context.mutations.add(Mutation.blockEntity(position,
                            block.blockEntitySemantic(), randomSeed));
                    context.trace(TraceKind.BLOCK_ENTITY, piece.template(), position,
                            block.blockEntitySemantic());
                }
            }
            traceEntities(piece, program, carrier.rotation(), yDelta, context);
            for (DataMarker marker : program.orderedMarkers()) {
                BlockPos position = worldPosition(piece, program, carrier.rotation(),
                        marker.localPosition(), yDelta);
                if (!clip.contains(position)) {
                    context.trace(TraceKind.CLIPPED_MARKER, piece.template(), position,
                            marker.metadata());
                    continue;
                }
                context.mutations.add(Mutation.block(position, AIR));
                context.trace(TraceKind.MARKER_CLEAR, piece.template(), position,
                        marker.metadata());
                BlockPos chest = position.below();
                context.trace(TraceKind.CONTAINER_QUERY, piece.template(), chest, "");
                if (queries.hasRandomizableContainer(chest, context)) {
                    long seed = random.nextLong();
                    context.trace(TraceKind.LOOT_DRAW, piece.template(), chest,
                            Long.toString(seed));
                    context.mutations.add(Mutation.loot(chest, seed));
                    context.trace(TraceKind.LOOT, piece.template(), chest, LOOT_TABLE);
                }
            }
            if (piece.template() == Template.IGLOO_TOP) {
                applyTrapdoorCorrection(piece, program, carrier.rotation(), yDelta, context);
            }
            context.trace(TraceKind.END_PIECE, piece.template(), null, "");
        }
        List<Mutation> mutations = List.copyOf(context.mutations);
        List<TraceEvent> trace = List.copyOf(context.trace);
        sink.commit(mutations);
        return new Result(mutations, trace, clip);
    }

    static int templateBlockCount(Template template) {
        return PROGRAMS.get(Objects.requireNonNull(template, "template")).orderedBlocks().size();
    }

    static int templateMarkerCount(Template template) {
        return PROGRAMS.get(Objects.requireNonNull(template, "template")).orderedMarkers().size();
    }

    static List<String> templateCanonicalLines(Template template, Rotation rotation) {
        TemplateProgram program = PROGRAMS.get(Objects.requireNonNull(template, "template"));
        Objects.requireNonNull(rotation, "rotation");
        ArrayList<String> lines = new ArrayList<>();
        for (BlockEntry block : program.orderedBlocks()) {
            BlockPos position = block.localPosition();
            lines.add(position.x() + "," + position.y() + "," + position.z() + "="
                    + block.states().forRotation(rotation) + "|"
                    + Objects.toString(block.blockEntitySemantic(), ""));
        }
        for (DataMarker marker : program.orderedMarkers()) {
            BlockPos position = marker.localPosition();
            lines.add("marker:" + position.x() + "," + position.y() + "," + position.z()
                    + "=" + marker.metadata());
        }
        return List.copyOf(lines);
    }

    private static void preflight(Mc263HardcodedStructureCarrier carrier, QuerySource queries) {
        if (carrier.kind() != Kind.IGLOO) {
            throw new IllegalArgumentException("carrier is not an igloo");
        }
        if (!isTemplateRotation(carrier.rotation())) {
            throw new IllegalArgumentException("carrier has non-template rotation");
        }
        carrier.preflight(carrier.requiredCapabilities(), carrier.requiredExactStates());
        Set<String> states = new HashSet<>(Set.of(AIR, SNOW_BLOCK));
        Set<String> blockEntities = new HashSet<>();
        for (TemplateProgram program : PROGRAMS.values()) {
            validateProgramShape(program);
            for (BlockEntry block : program.orderedBlocks()) {
                String state = block.states().forRotation(carrier.rotation());
                // BlockIgnoreProcessor.STRUCTURE_BLOCK, installed by the igloo piece settings,
                // drops every minecraft:structure_block cell before StructureTemplate writes it;
                // the DATA marker below is consumed by IglooPieces.handleDataMarker instead. The
                // execution loop drops the same cells, so the world never has to carry the state
                // and demanding a FEATURES catalog entry for it would be a divergence. Every
                // state that can actually be written stays fail-closed here.
                if (blockKey(state).equals(STRUCTURE_BLOCK)) continue;
                states.add(state);
                if (block.blockEntitySemantic() != null) {
                    blockEntities.add(block.blockEntitySemantic());
                }
            }
        }
        for (PieceFact piece : carrier.orderedPieces()) {
            if (piece.template() == null || PROGRAMS.get(piece.template()) == null) {
                throw new UnsupportedOperationException("unsupported carried igloo template");
            }
        }
        for (String state : states) {
            if (!queries.supportsExactState(state)) {
                throw new UnsupportedOperationException("unsupported igloo exact state: " + state);
            }
        }
        for (String semantic : blockEntities) {
            if (!queries.supportsBlockEntityData(semantic)) {
                throw new UnsupportedOperationException(
                        "unsupported igloo block-entity data: " + semantic);
            }
        }
        require(queries.supportsWorldSurfaceWg(), "WORLD_SURFACE_WG query");
        require(queries.supportsBlockStateQuery(), "block-state query");
        require(queries.supportsRandomizableContainerQuery(), "container query");
        require(queries.supportsLootSidecar(), "igloo loot sidecar");
    }

    private static int alignedYDelta(PieceFact piece, TemplateProgram program, Rotation rotation,
            Context context) {
        Offset offset = offset(piece.template());
        BlockPos entranceLocal = new BlockPos(3 - offset.x, 0, -offset.z);
        BlockPos entrance = worldPosition(piece, program, rotation, entranceLocal, 0);
        context.trace(TraceKind.HEIGHT_QUERY, piece.template(), entrance,
                "WORLD_SURFACE_WG");
        int height = context.queries.worldSurfaceWg(entrance.x(), entrance.z());
        return Math.subtractExact(Math.subtractExact(height, GENERATION_HEIGHT), 1);
    }

    private static void applyTrapdoorCorrection(PieceFact piece, TemplateProgram program,
            Rotation rotation, int yDelta, Context context) {
        BlockPos trapdoor = worldPosition(piece, program, rotation,
                new BlockPos(3, 0, 5), yDelta);
        BlockPos below = trapdoor.below();
        context.trace(TraceKind.TRAPDOOR_BELOW_QUERY, piece.template(), below, "");
        String belowState = requireState(context.blockState(below));
        String key = blockKey(belowState);
        if (!key.equals(AIR) && !key.equals(LADDER)) {
            // IglooPieces.IglooPiece.postProcess writes this cover with a raw setBlock outside the
            // piece placement loop, so it is not bounded by the piece box; it is still bounded by
            // the chunk being produced, exactly like every other block write, and the cover is
            // re-derived when the chunk holding it is the target.
            if (!context.clip.contains(trapdoor)) {
                context.trace(TraceKind.CLIPPED_BLOCK, piece.template(), trapdoor, SNOW_BLOCK);
                return;
            }
            context.mutations.add(Mutation.block(trapdoor, SNOW_BLOCK));
            context.trace(TraceKind.TRAPDOOR_COVER, piece.template(), trapdoor, SNOW_BLOCK);
        }
    }

    /** StructureTemplate clips entities by transformed integer blockPos, before DATA markers. */
    private static void traceEntities(PieceFact piece, TemplateProgram program,
            Rotation rotation, int yDelta, Context context) {
        if (piece.template() != Template.IGLOO_BOTTOM) return;
        for (EntityEntry entity : List.of(
                new EntityEntry(new BlockPos(2, 1, 1), "minecraft:villager"),
                new EntityEntry(new BlockPos(4, 1, 1), "minecraft:zombie_villager"))) {
            BlockPos position = worldPosition(piece, program, rotation,
                    entity.localBlockPosition(), yDelta);
            context.trace(context.clip.contains(position) ? TraceKind.ENTITY
                    : TraceKind.CLIPPED_ENTITY, piece.template(), position, entity.entityKey());
        }
    }

    private static EnumMap<Template, TemplateProgram> createPrograms() {
        EnumMap<Template, TemplateProgram> programs = new EnumMap<>(Template.class);
        programs.put(Template.IGLOO_TOP, createTop());
        programs.put(Template.IGLOO_MIDDLE, createMiddle());
        programs.put(Template.IGLOO_BOTTOM, createBottom());
        if (!programs.keySet().equals(EnumSet.allOf(Template.class))) {
            throw new ExceptionInInitializerError("incomplete repository igloo grammar");
        }
        requireProgramCount(programs.get(Template.IGLOO_TOP), 152, 0);
        requireProgramCount(programs.get(Template.IGLOO_MIDDLE), 15, 0);
        requireProgramCount(programs.get(Template.IGLOO_BOTTOM), 244, 1);
        return programs;
    }

    private static TemplateProgram createTop() {
        ArrayList<BlockEntry> blocks = new ArrayList<>();
        scan(7, 5, 8, blocks, Mc263IglooStructureExecutor::topSolidState);
        scan(7, 5, 8, blocks, Mc263IglooStructureExecutor::topInteriorState);
        blocks.add(entity(1, 1, 3, facing(
                "minecraft:furnace[lit=false,facing=%s]", Direction.EAST), EMPTY_FURNACE));
        return new TemplateProgram(Template.IGLOO_TOP, 7, 5, 8,
                new BlockPos(3, 5, 5), blocks, List.of());
    }

    private static ExactStates topSolidState(int x, int y, int z) {
        if (y == 0 && ((x == 0 || x == 6) && between(z, 3, 5)
                || (x == 1 || x == 5) && between(z, 2, 6)
                || (x == 2 || x == 4) && between(z, 0, 7)
                || x == 3 && between(z, 0, 7) && z != 5)) return fixed(SNOW_BLOCK);
        if (y == 1 && x == 1 && z == 5) return fixed("minecraft:crafting_table");
        if ((y == 1 || y == 2) && topWall(x, z)) {
            if (y == 1 && (x == 0 || x == 6) && z == 4) return fixed("minecraft:ice");
            return fixed(SNOW_BLOCK);
        }
        if (y == 3 && ((x == 1 || x == 5) && between(z, 3, 5)
                || (x == 2 || x == 4) && (z == 2 || z == 6)
                || x == 3 && (between(z, 0, 2) || z == 6))) return fixed(SNOW_BLOCK);
        if (y == 4 && between(x, 2, 4) && between(z, 3, 5)) return fixed(SNOW_BLOCK);
        return null;
    }

    private static boolean topWall(int x, int z) {
        return (x == 0 || x == 6) && between(z, 3, 5)
                || (x == 1 || x == 5) && (z == 2 || z == 6)
                || (x == 2 || x == 4) && (z <= 1 || z == 7)
                || x == 3 && z == 7;
    }

    private static ExactStates topInteriorState(int x, int y, int z) {
        if (y == 0 && x == 3 && z == 5) {
            return facing("minecraft:oak_trapdoor[half=top,waterlogged=false,powered=false,"
                    + "facing=%s,open=false]", Direction.NORTH);
        }
        if (y == 1) {
            if (x == 1 && z == 4) return fixed("minecraft:redstone_torch[lit=true]");
            if (between(x, 2, 4) && between(z, 3, 5)) {
                return fixed("minecraft:white_carpet");
            }
            if (between(x, 2, 4) && z == 6) return fixed("minecraft:light_gray_carpet");
            if (x == 5 && z == 4) return facing(
                    "minecraft:red_bed[part=foot,facing=%s,occupied=false]", Direction.SOUTH);
            if (x == 5 && z == 5) return facing(
                    "minecraft:red_bed[part=head,facing=%s,occupied=false]", Direction.SOUTH);
            if (x == 2 && z == 2 || x == 3 && between(z, 0, 2)
                    || x == 4 && z == 2 || x == 5 && z == 3) return fixed(AIR);
        }
        if (y == 2 && (x == 1 && between(z, 3, 5)
                || (x == 2 || x == 4) && between(z, 2, 6)
                || x == 3 && between(z, 0, 6)
                || x == 5 && between(z, 3, 5))) return fixed(AIR);
        if (y == 3 && between(x, 2, 4) && between(z, 3, 5)) return fixed(AIR);
        return null;
    }

    private static TemplateProgram createMiddle() {
        ArrayList<BlockEntry> blocks = new ArrayList<>();
        scan(3, 3, 3, blocks, (x, y, z) ->
                x == 1 && (z == 0 || z == 2) || z == 1 && (x == 0 || x == 2)
                        ? fixed("minecraft:stone_bricks") : null);
        scan(3, 3, 3, blocks, (x, y, z) -> x == 1 && z == 1
                ? facing("minecraft:ladder[waterlogged=false,facing=%s]", Direction.NORTH)
                : null);
        return new TemplateProgram(Template.IGLOO_MIDDLE, 3, 3, 3,
                new BlockPos(1, 3, 1), blocks, List.of());
    }

    private static TemplateProgram createBottom() {
        ArrayList<BlockEntry> blocks = new ArrayList<>();
        scan(7, 6, 9, blocks, Mc263IglooStructureExecutor::bottomSolidState);
        scan(7, 6, 9, blocks, Mc263IglooStructureExecutor::bottomInteriorState);
        blocks.add(entity(1, 1, 6, facing(
                "minecraft:chest[waterlogged=false,facing=%s,type=single]", Direction.EAST),
                EMPTY_CHEST));
        blocks.add(block(1, 2, 6, fixed("minecraft:structure_block[mode=data]")));
        blocks.add(entity(3, 2, 3, facing(
                "minecraft:oak_wall_sign[waterlogged=false,facing=%s]", Direction.SOUTH),
                DIRECTION_SIGN));
        blocks.add(entity(5, 2, 6, fixed("minecraft:brewing_stand[has_bottle_0=false,"
                + "has_bottle_1=true,has_bottle_2=false]"), WEAKNESS_BREWING_STAND));
        return new TemplateProgram(Template.IGLOO_BOTTOM, 7, 6, 9,
                new BlockPos(3, 6, 7), blocks,
                List.of(new DataMarker(new BlockPos(1, 2, 6), "chest")));
    }

    private static ExactStates bottomSolidState(int x, int y, int z) {
        if (!bottomSolidGeometry(x, y, z)) return null;
        int coordinate = coordinate(x, y, z);
        if (SetHolder.POLISHED.contains(coordinate)) return fixed("minecraft:polished_andesite");
        if (SetHolder.CHISELED.contains(coordinate)) return fixed("minecraft:chiseled_stone_bricks");
        if (SetHolder.CRACKED.contains(coordinate)) return fixed("minecraft:cracked_stone_bricks");
        if (SetHolder.INFESTED.contains(coordinate)) return fixed("minecraft:infested_stone_bricks");
        if (SetHolder.INFESTED_CHISELED.contains(coordinate)) {
            return fixed("minecraft:infested_chiseled_stone_bricks");
        }
        if (SetHolder.MOSSY.contains(coordinate)) return fixed("minecraft:mossy_stone_bricks");
        if (SetHolder.INFESTED_MOSSY.contains(coordinate)) {
            return fixed("minecraft:infested_mossy_stone_bricks");
        }
        boolean foundationStone = y == 0 && z <= 2
                && !(z == 2 && (x == 2 || x == 4));
        return fixed(foundationStone ? "minecraft:stone" : "minecraft:stone_bricks");
    }

    private static boolean bottomSolidGeometry(int x, int y, int z) {
        if (y == 0) return between(x, 1, 5) && between(z, 0, 7);
        if (y == 1 || y == 2) return bottomWall(x, z, false);
        if (y == 3) return bottomWall(x, z, true);
        if (y == 4) return x == 1 && between(z, 3, 7)
                || (x == 2 || x == 4) && between(z, 1, 8)
                || x == 3 && between(z, 1, 8) && z != 7
                || x == 5 && between(z, 3, 7);
        return y == 5 && (x == 2 && z == 7 || x == 3 && (z == 6 || z == 8)
                || x == 4 && z == 7);
    }

    private static boolean bottomWall(int x, int z, boolean doorway) {
        return (x == 0 || x == 6) && between(z, 3, 7)
                || (x == 1 || x == 5) && (between(z, 0, 2) || z == 8)
                || (x == 2 || x == 4) && (z == 0 || z == 8 || doorway && z == 2)
                || x == 3 && (between(z, 0, 2) || z == 8);
    }

    private static ExactStates bottomInteriorState(int x, int y, int z) {
        if (y == 1) {
            if (x == 1 && (z == 3 || z == 4)) return fixed("minecraft:red_carpet");
            if ((x == 2 || x == 4) && z == 2) return ironBarsEastWest();
            if (x == 5 && z == 4) return fixed("minecraft:water_cauldron[level=2]");
            if (x == 5 && z == 5) return facing(
                    "minecraft:spruce_stairs[half=top,waterlogged=false,shape=straight,"
                            + "facing=%s]", Direction.NORTH);
            if (x == 5 && z == 6) return fixed(
                    "minecraft:spruce_slab[waterlogged=false,type=top]");
            if (x == 5 && z == 7) return facing(
                    "minecraft:spruce_stairs[half=top,waterlogged=false,shape=straight,"
                            + "facing=%s]", Direction.SOUTH);
            if (x == 1 && (z == 5 || z == 7)
                    || (x == 2 || x == 4) && (z == 1 || between(z, 3, 7))
                    || x == 3 && between(z, 3, 7) || x == 5 && z == 3) return fixed(AIR);
        }
        if (y == 2) {
            if ((x == 2 || x == 4) && z == 2) return ironBarsEastWest();
            if (x == 3 && z == 7) return facing(
                    "minecraft:ladder[waterlogged=false,facing=%s]", Direction.NORTH);
            if (x == 5 && z == 7) return fixed("minecraft:potted_cactus");
            if (x == 1 && (between(z, 3, 5) || z == 7)
                    || (x == 2 || x == 4) && (z == 1 || between(z, 3, 7))
                    || x == 3 && between(z, 4, 6)
                    || x == 5 && between(z, 3, 5)) return fixed(AIR);
        }
        if (y == 3) {
            if (x == 1 && z == 3) return fixed("minecraft:cobweb");
            if ((x == 2 || x == 4) && z == 7) return facing(
                    "minecraft:wall_torch[facing=%s]", Direction.NORTH);
            if (x == 3 && z == 3) return facing(
                    "minecraft:wall_torch[facing=%s]", Direction.SOUTH);
            if (x == 3 && z == 7) return facing(
                    "minecraft:ladder[waterlogged=false,facing=%s]", Direction.NORTH);
            if (x == 1 && between(z, 4, 7) || (x == 2 || x == 4) && (z == 1
                    || between(z, 3, 6)) || x == 3 && between(z, 4, 6)
                    || x == 5 && between(z, 3, 7)) return fixed(AIR);
        }
        if ((y == 4 || y == 5) && x == 3 && z == 7) return facing(
                "minecraft:ladder[waterlogged=false,facing=%s]", Direction.NORTH);
        return null;
    }

    private static ExactStates ironBarsEastWest() {
        EnumMap<Rotation, String> states = new EnumMap<>(Rotation.class);
        for (Rotation rotation : templateRotations()) {
            boolean eastWest = rotation == Rotation.NONE || rotation == Rotation.CLOCKWISE_180;
            states.put(rotation, "minecraft:iron_bars[east=" + eastWest
                    + ",waterlogged=false,south=" + !eastWest + ",north=" + !eastWest
                    + ",west=" + eastWest + "]");
        }
        return new ExactStates(states);
    }

    private static ExactStates facing(String pattern, Direction direction) {
        EnumMap<Rotation, String> states = new EnumMap<>(Rotation.class);
        for (Rotation rotation : templateRotations()) {
            states.put(rotation, pattern.formatted(direction.rotate(rotation).key));
        }
        return new ExactStates(states);
    }

    private static ExactStates fixed(String state) {
        EnumMap<Rotation, String> states = new EnumMap<>(Rotation.class);
        for (Rotation rotation : templateRotations()) states.put(rotation, state);
        return new ExactStates(states);
    }

    private static void scan(int sizeX, int sizeY, int sizeZ, List<BlockEntry> target,
            StateAt stateAt) {
        for (int y = 0; y < sizeY; y++) {
            for (int x = 0; x < sizeX; x++) {
                for (int z = 0; z < sizeZ; z++) {
                    ExactStates states = stateAt.get(x, y, z);
                    if (states != null) target.add(block(x, y, z, states));
                }
            }
        }
    }

    private static BlockEntry block(int x, int y, int z, ExactStates states) {
        return new BlockEntry(new BlockPos(x, y, z), states, null);
    }

    private static BlockEntry entity(int x, int y, int z, ExactStates states, String semantic) {
        return new BlockEntry(new BlockPos(x, y, z), states, semantic);
    }

    private static boolean between(int value, int minimum, int maximum) {
        return value >= minimum && value <= maximum;
    }

    private static int coordinate(int x, int y, int z) {
        return y * 100 + x * 10 + z;
    }

    private static void requireProgramCount(TemplateProgram program, int blocks, int markers) {
        if (program.orderedBlocks().size() != blocks
                || program.orderedMarkers().size() != markers) {
            throw new ExceptionInInitializerError("repository igloo grammar count drift: "
                    + program.template() + " blocks=" + program.orderedBlocks().size()
                    + " markers=" + program.orderedMarkers().size());
        }
    }

    private static BlockPos worldPosition(PieceFact piece, TemplateProgram program,
            Rotation rotation, BlockPos local, int yDelta) {
        BlockPos relative = transform(local, rotation, program.pivot());
        return new BlockPos(Math.addExact(piece.templateX(), relative.x()),
                Math.addExact(Math.addExact(piece.templateY(), relative.y()), yDelta),
                Math.addExact(piece.templateZ(), relative.z()));
    }

    private static BlockPos transform(BlockPos position, Rotation rotation, BlockPos pivot) {
        return switch (rotation) {
            case NONE -> position;
            case CLOCKWISE_180 -> new BlockPos(
                    Math.subtractExact(Math.multiplyExact(2, pivot.x()), position.x()),
                    position.y(),
                    Math.subtractExact(Math.multiplyExact(2, pivot.z()), position.z()));
            case COUNTERCLOCKWISE_90 -> new BlockPos(
                    Math.addExact(Math.subtractExact(pivot.x(), pivot.z()), position.z()),
                    position.y(),
                    Math.subtractExact(Math.addExact(pivot.x(), pivot.z()), position.x()));
            case CLOCKWISE_90 -> new BlockPos(
                    Math.subtractExact(Math.addExact(pivot.x(), pivot.z()), position.z()),
                    position.y(),
                    Math.addExact(Math.subtractExact(pivot.z(), pivot.x()), position.x()));
            default -> throw new IllegalArgumentException("template requires quarter rotation");
        };
    }

    private static void validateProgramShape(TemplateProgram program) {
        TemplateShape expected = shape(program.template());
        if (program.sizeX() != expected.sizeX || program.sizeY() != expected.sizeY
                || program.sizeZ() != expected.sizeZ || !program.pivot().equals(expected.pivot)) {
            throw new IllegalArgumentException("noncanonical igloo template shape: "
                    + program.template());
        }
        if (program.orderedBlocks().size() > MAX_BLOCKS
                || program.orderedMarkers().size() > MAX_MARKERS) {
            throw new IllegalArgumentException("igloo template program is too large");
        }
        Set<BlockPos> occupied = new HashSet<>();
        for (BlockEntry block : program.orderedBlocks()) {
            Objects.requireNonNull(block, "block");
            requireInside(program, block.localPosition());
            if (!occupied.add(block.localPosition())) {
                throw new IllegalArgumentException("duplicate igloo block entry");
            }
        }
        for (DataMarker marker : program.orderedMarkers()) {
            Objects.requireNonNull(marker, "marker");
            requireInside(program, marker.localPosition());
            if (!occupied.contains(marker.localPosition())) {
                throw new IllegalArgumentException("igloo marker lacks structure block");
            }
            BlockEntry markerBlock = program.orderedBlocks().stream()
                    .filter(block -> block.localPosition().equals(marker.localPosition()))
                    .findFirst().orElseThrow();
            for (Rotation rotation : templateRotations()) {
                if (!STRUCTURE_BLOCK.equals(blockKey(markerBlock.states().forRotation(rotation)))) {
                    throw new IllegalArgumentException("igloo marker entry is not a structure block");
                }
            }
        }
    }

    private static void requireInside(TemplateProgram program, BlockPos position) {
        if (position.x() < 0 || position.x() >= program.sizeX()
                || position.y() < 0 || position.y() >= program.sizeY()
                || position.z() < 0 || position.z() >= program.sizeZ()) {
            throw new IllegalArgumentException("igloo entry outside template bounds");
        }
    }

    private static TemplateShape shape(Template template) {
        return switch (template) {
            case IGLOO_TOP -> new TemplateShape(7, 5, 8, new BlockPos(3, 5, 5));
            case IGLOO_MIDDLE -> new TemplateShape(3, 3, 3, new BlockPos(1, 3, 1));
            case IGLOO_BOTTOM -> new TemplateShape(7, 6, 9, new BlockPos(3, 6, 7));
        };
    }

    private static Offset offset(Template template) {
        return switch (template) {
            case IGLOO_TOP -> new Offset(0, 0, 0);
            case IGLOO_MIDDLE -> new Offset(2, -3, 4);
            case IGLOO_BOTTOM -> new Offset(0, -3, -2);
        };
    }

    private static boolean isTemplateRotation(Rotation rotation) {
        return rotation == Rotation.NONE || rotation == Rotation.CLOCKWISE_90
                || rotation == Rotation.CLOCKWISE_180
                || rotation == Rotation.COUNTERCLOCKWISE_90;
    }

    private static Set<Rotation> templateRotations() {
        return EnumSet.of(Rotation.NONE, Rotation.CLOCKWISE_90, Rotation.CLOCKWISE_180,
                Rotation.COUNTERCLOCKWISE_90);
    }

    private static String requireState(String state) {
        requireResource(state, "exact state");
        return state;
    }

    private static String requireResource(String value, String label) {
        Objects.requireNonNull(value, label);
        int property = value.indexOf('[');
        String key = property < 0 ? value : value.substring(0, property);
        if (!key.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")
                || property >= 0 && !value.endsWith("]")) {
            throw new IllegalArgumentException("invalid " + label + ": " + value);
        }
        return value;
    }

    private static String blockKey(String state) {
        int property = state.indexOf('[');
        return property < 0 ? state : state.substring(0, property);
    }

    private static void require(boolean condition, String capability) {
        if (!condition) {
            throw new UnsupportedOperationException("unsupported igloo " + capability);
        }
    }

    private enum Direction {
        NORTH("north"), EAST("east"), SOUTH("south"), WEST("west");

        private final String key;

        Direction(String key) { this.key = key; }

        private Direction rotate(Rotation rotation) {
            int turns = switch (rotation) {
                case NONE -> 0;
                case CLOCKWISE_90 -> 1;
                case CLOCKWISE_180 -> 2;
                case COUNTERCLOCKWISE_90 -> 3;
                default -> throw new IllegalArgumentException("template requires quarter rotation");
            };
            return values()[(ordinal() + turns) & 3];
        }
    }

    private interface StateAt {
        ExactStates get(int x, int y, int z);
    }

    private static final class ExactStates {
        private final EnumMap<Rotation, String> values;

        private ExactStates(Map<Rotation, String> source) {
            values = new EnumMap<>(Rotation.class);
            for (Map.Entry<Rotation, String> entry : source.entrySet()) {
                values.put(Objects.requireNonNull(entry.getKey(), "rotation"),
                        requireState(entry.getValue()));
            }
            if (!values.keySet().equals(templateRotations())) {
                throw new IllegalArgumentException("incomplete repository igloo state rotation");
            }
        }

        private String forRotation(Rotation rotation) {
            return values.get(rotation);
        }
    }

    private record BlockEntry(BlockPos localPosition, ExactStates states,
            String blockEntitySemantic) {
        private BlockEntry {
            Objects.requireNonNull(localPosition, "local block position");
            Objects.requireNonNull(states, "exact states");
            if (blockEntitySemantic != null) {
                requireResource(blockEntitySemantic, "block-entity semantic");
            }
        }
    }

    private record DataMarker(BlockPos localPosition, String metadata) {
        private DataMarker {
            Objects.requireNonNull(localPosition, "local marker position");
            if (!"chest".equals(metadata)) {
                throw new UnsupportedOperationException("unsupported igloo data marker");
            }
        }
    }

    private record EntityEntry(BlockPos localBlockPosition, String entityKey) {
        private EntityEntry {
            Objects.requireNonNull(localBlockPosition, "local entity block position");
            requireResource(entityKey, "entity key");
        }
    }

    private record TemplateProgram(Template template, int sizeX, int sizeY, int sizeZ,
            BlockPos pivot, List<BlockEntry> orderedBlocks, List<DataMarker> orderedMarkers) {
        private TemplateProgram(Template template, int sizeX, int sizeY, int sizeZ,
                BlockPos pivot, List<BlockEntry> orderedBlocks,
                List<DataMarker> orderedMarkers) {
            this.template = Objects.requireNonNull(template, "template");
            this.sizeX = sizeX;
            this.sizeY = sizeY;
            this.sizeZ = sizeZ;
            this.pivot = Objects.requireNonNull(pivot, "pivot");
            this.orderedBlocks = List.copyOf(Objects.requireNonNull(orderedBlocks, "blocks"));
            this.orderedMarkers = List.copyOf(
                    Objects.requireNonNull(orderedMarkers, "markers"));
            validateProgramShape(this);
        }
    }

    private static final class SetHolder {
        private static final Set<Integer> POLISHED = points(1, 0, 3);
        private static final Set<Integer> CHISELED = points(
                1, 0, 5, 1, 0, 7, 3, 0, 3, 3, 0, 5, 3, 0, 7, 5, 0, 5, 5, 0, 7);
        private static final Set<Integer> CRACKED = points(
                2, 0, 1, 4, 0, 1, 0, 2, 3, 0, 2, 6, 3, 2, 2, 3, 2, 8,
                5, 2, 8, 6, 2, 5);
        private static final Set<Integer> INFESTED = points(
                2, 0, 4, 5, 0, 6, 1, 2, 8, 6, 2, 6, 0, 3, 4, 2, 4, 6);
        private static final Set<Integer> INFESTED_CHISELED = points(5, 0, 3, 3, 4, 5);
        private static final Set<Integer> MOSSY = points(
                0, 1, 3, 0, 1, 4, 0, 1, 5, 0, 1, 6, 0, 1, 7,
                1, 1, 2, 1, 1, 8, 3, 1, 2, 3, 1, 8, 4, 1, 8,
                5, 1, 2, 5, 1, 8, 6, 1, 3, 6, 1, 4, 6, 1, 5, 6, 1, 6, 6, 1, 7);
        private static final Set<Integer> INFESTED_MOSSY = points(2, 1, 8);

        private SetHolder() { }
    }

    private static Set<Integer> points(int... coordinates) {
        if (coordinates.length % 3 != 0) throw new IllegalArgumentException("incomplete point");
        HashSet<Integer> result = new HashSet<>();
        for (int index = 0; index < coordinates.length; index += 3) {
            result.add(coordinate(coordinates[index], coordinates[index + 1],
                    coordinates[index + 2]));
        }
        return Set.copyOf(result);
    }

    private record TemplateShape(int sizeX, int sizeY, int sizeZ, BlockPos pivot) { }
    private record Offset(int x, int y, int z) { }

    private static final class Context implements PendingView {
        private final QuerySource queries;
        private final RandomSource random;
        private final Clip clip;
        private final ArrayList<Mutation> mutations = new ArrayList<>();
        private final ArrayList<TraceEvent> trace = new ArrayList<>();

        private Context(QuerySource queries, RandomSource random, Clip clip) {
            this.queries = queries;
            this.random = random;
            this.clip = clip;
        }

        @Override public String blockState(BlockPos position) {
            for (int index = mutations.size() - 1; index >= 0; index--) {
                Mutation mutation = mutations.get(index);
                if (mutation.kind() == MutationKind.BLOCK
                        && mutation.position().equals(position)) return mutation.value();
            }
            return requireState(queries.blockState(position, this));
        }

        private void trace(TraceKind kind, Template template, BlockPos position, String detail) {
            trace.add(new TraceEvent(kind, template, position, detail));
        }
    }
}
