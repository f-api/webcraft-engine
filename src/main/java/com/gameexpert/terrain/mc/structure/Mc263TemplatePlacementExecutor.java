package com.gameexpert.terrain.mc.structure;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Family-neutral Minecraft Java 26.3-snapshot-7 {@code StructureTemplate.placeInWorld} executor.
 *
 * <p>This is the generic template-placement engine the Village, Woodland Mansion and Ancient City
 * emission transactions share. It owns exactly the vanilla placement loop and nothing family
 * specific: the cell program, the per-cell processors, the block-entity NBT synthesiser and the
 * loot-seed stream are all caller supplied, so the three family grammars adapt to
 * {@link CellProgram} without this class learning any family coordinate, pool or processor
 * identity.</p>
 *
 * <p>Semantics are taken from the pinned server jar and the accepted Abandoned Camp executor, which
 * is the landed family-specific reference for the same loop:</p>
 * <ul>
 *   <li>Cell order is the caller's program order; the placement position is
 *       {@code origin + transform(local, mirror, rotation)} with the vanilla
 *       mirror-before-rotation pivot-zero arithmetic of
 *       {@code StructureTemplate.transform(BlockPos, Mirror, Rotation, BlockPos)}.</li>
 *   <li>{@link IgnorePolicy} models {@code BlockIgnoreProcessor}: {@code STRUCTURE_BLOCK} drops only
 *       {@code minecraft:structure_block}, {@code STRUCTURE_AND_AIR} additionally drops
 *       {@code minecraft:air} — the policy legacy single pool elements install.</li>
 *   <li>Placement is the vanilla two phases of {@code placeInWorld}: first
 *       {@code StructureTemplate.processBlockInfos} runs the caller's {@link CellProcessor} chain
 *       (the family rule authorities, and {@link Mc263GravityProcessorAuthority} for
 *       {@code terrain_matching} projections) over every reached cell of the piece, then the write
 *       loop walks the processed list. So every processor world query of a piece precedes every
 *       write of that piece, exactly as the authenticated corpus transcripts record. Every world
 *       access a processor performs goes through the recording world, so processor queries appear
 *       in the transcript in their real position.</li>
 *   <li>{@code minecraft:structure_block} and {@code minecraft:jigsaw} cells are dropped before the
 *       chain, matching {@code BlockIgnoreProcessor.STRUCTURE_BLOCK} and
 *       {@code JigsawReplacementProcessor} which {@code SinglePoolElement.getSettings} installs
 *       ahead of the element and projection processors; a {@code STRUCTURE_AND_AIR} air cell still
 *       reaches the chain and is dropped only at its write, which is why the corpus carries its
 *       {@code getHeight} row but no air write.</li>
 *   <li>{@code keepLiquids} reads the pre-existing fluid immediately before the write; a retained
 *       water source over a {@code waterlogged=false} successor is re-flooded at once, and every
 *       other retained fluid is restored by the vanilla post-pass that re-reads the position and its
 *       {@code UP, NORTH, EAST, SOUTH, WEST} neighbours until no position changes.</li>
 *   <li>Writes use flags {@value #TEMPLATE_WRITE_FLAGS}; a cell carrying a block entity first clears
 *       the old one with a {@code minecraft:barrier} write at flags
 *       {@value #BLOCK_ENTITY_CLEAR_FLAGS}, exactly as {@code placeInWorld} does before
 *       {@code Clearable.tryClear}.</li>
 *   <li>A loot container derives its seed from one {@code nextLong()} of the caller placement RNG,
 *       which is {@code RandomizableContainer.setLootTable(ResourceKey, long)} being fed
 *       {@code random.nextLong()} at the placement site.</li>
 * </ul>
 *
 * <p>The ordered operation transcript uses the authenticated corpus row grammar
 * {@code <ordinal>|<kind>|<detail>}; see {@link Transcript} for the per-kind detail forms.</p>
 */
public final class Mc263TemplatePlacementExecutor {
    public static final int TEMPLATE_WRITE_FLAGS = 18;
    public static final int BLOCK_ENTITY_CLEAR_FLAGS = 820;
    public static final int WATER_TICK_DELAY = 5;
    public static final String WATER_FLUID = "minecraft:water";
    /** Corpus-authenticated {@code scheduleTick} identity for a restored water source. */
    public static final String WATER_TICK_IDENTITY =
            "net.minecraft.world.level.material.WaterFluid$Source";
    public static final String EMPTY_FLUID = "minecraft:empty";
    public static final String BARRIER_STATE = "minecraft:barrier[waterlogged=false]";
    public static final String AIR_BLOCK = "minecraft:air";
    public static final String STRUCTURE_BLOCK = "minecraft:structure_block";
    public static final String JIGSAW_BLOCK = "minecraft:jigsaw";
    public static final String STRUCTURE_VOID = "minecraft:structure_void";

    private static final List<String> HORIZONTAL = List.of("north", "east", "south", "west");
    private static final List<Direction> LIQUID_NEIGHBORS = List.of(
            Direction.UP, Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST);
    private static final Comparator<Vec> POSITION_ORDER = Comparator
            .comparingInt(Vec::y).thenComparingInt(Vec::z).thenComparingInt(Vec::x);

    private Mc263TemplatePlacementExecutor() { }

    /** Official {@code net.minecraft.world.level.block.Rotation}. */
    public enum Rotation { NONE, CLOCKWISE_90, CLOCKWISE_180, COUNTERCLOCKWISE_90 }

    /** Official {@code net.minecraft.world.level.block.Mirror}. */
    public enum Mirror { NONE, LEFT_RIGHT, FRONT_BACK }

    /** {@code BlockIgnoreProcessor} closure actually installed by the pool element. */
    public enum IgnorePolicy { STRUCTURE_BLOCK, STRUCTURE_AND_AIR }

    private enum Direction {
        UP(0, 1, 0), NORTH(0, 0, -1), EAST(1, 0, 0), SOUTH(0, 0, 1), WEST(-1, 0, 0);
        private final int dx, dy, dz;
        Direction(int dx, int dy, int dz) { this.dx = dx; this.dy = dy; this.dz = dz; }
    }

    /** One template-local cell of a neutral cell program. */
    public interface Cell {
        int localX();
        int localY();
        int localZ();
        /** Template-local exact state, before mirror/rotation. */
        String exactState();
        /** Block-entity type this cell creates, or {@code null}. */
        default String blockEntityType() { return null; }
        /** Loot table of a randomizable container cell, or {@code null}. */
        default String lootTable() { return null; }
        /** Typed marker semantics (jigsaw/data) the caller's processors consume, or {@code null}. */
        default String markerOp() { return null; }
        default String markerSemanticJson() { return null; }
        /**
         * {@code JigsawReplacementProcessor}'s {@code final_state} for a {@code minecraft:jigsaw}
         * cell — the block-entity NBT string this executor cannot read itself. {@code null} means
         * the cell carries no NBT, which vanilla logs and leaves as the jigsaw block.
         */
        default String jigsawFinalState() { return null; }
    }

    /** Neutral cell program: the shape the family grammars adapt to. */
    public interface CellProgram {
        String templateKey();
        List<? extends Cell> cellsInPlacementOrder();
    }

    /** One placed cell program with its landed transform and processor identity. */
    public static final class Piece {
        private final int ordinal;
        private final CellProgram program;
        private final Vec origin;
        private final Rotation rotation;
        private final Mirror mirror;
        private final String projection;
        private final List<String> processors;
        private final IgnorePolicy ignorePolicy;
        private final boolean keepLiquids;
        private final int writeFlags;

        /** Piece placed at the default {@value #TEMPLATE_WRITE_FLAGS} template write flags. */
        public Piece(int ordinal, CellProgram program, Vec origin, Rotation rotation, Mirror mirror,
                String projection, List<String> processors, IgnorePolicy ignorePolicy,
                boolean keepLiquids) {
            this(ordinal, program, origin, rotation, mirror, projection, processors, ignorePolicy,
                    keepLiquids, TEMPLATE_WRITE_FLAGS);
        }

        /**
         * Piece placed at the caller's authenticated {@code placeInWorld} write flags. The jigsaw
         * families pass {@value #TEMPLATE_WRITE_FLAGS}; a family whose pinned settings row records a
         * different {@code setBlock} flag word — the Woodland Mansion's {@code WMN263P1} {@code W}
         * row records {@code 2} — passes that value instead. The flags travel with the piece because
         * they are a property of the placement settings, not of this loop.
         */
        public Piece(int ordinal, CellProgram program, Vec origin, Rotation rotation, Mirror mirror,
                String projection, List<String> processors, IgnorePolicy ignorePolicy,
                boolean keepLiquids, int writeFlags) {
            if (ordinal < 0) throw new IllegalArgumentException("negative template piece ordinal");
            if (writeFlags < 0) throw new IllegalArgumentException("negative template write flags");
            this.ordinal = ordinal;
            this.program = Objects.requireNonNull(program, "template cell program");
            this.origin = Objects.requireNonNull(origin, "template piece origin");
            this.rotation = Objects.requireNonNull(rotation, "template piece rotation");
            this.mirror = Objects.requireNonNull(mirror, "template piece mirror");
            this.projection = Objects.requireNonNull(projection, "template piece projection");
            this.processors = List.copyOf(processors);
            this.ignorePolicy = Objects.requireNonNull(ignorePolicy, "template ignore policy");
            this.keepLiquids = keepLiquids;
            this.writeFlags = writeFlags;
        }

        public int ordinal() { return ordinal; }
        public CellProgram program() { return program; }
        public Vec origin() { return origin; }
        public Rotation rotation() { return rotation; }
        public Mirror mirror() { return mirror; }
        public String projection() { return projection; }
        public List<String> processors() { return processors; }
        public IgnorePolicy ignorePolicy() { return ignorePolicy; }
        public boolean keepLiquids() { return keepLiquids; }
        /** {@code setBlock} flag word of this piece's placement settings. */
        public int writeFlags() { return writeFlags; }
    }

    /** Processor output for one cell; {@code null} from a processor drops the cell. */
    public static final class ProcessedCell {
        private final Vec position;
        private final String exactState;
        private final byte[] inputNbt;

        public ProcessedCell(Vec position, String exactState, byte[] inputNbt) {
            this.position = Objects.requireNonNull(position, "processed cell position");
            this.exactState = Objects.requireNonNull(exactState, "processed cell state");
            this.inputNbt = inputNbt == null ? null : inputNbt.clone();
        }

        public Vec position() { return position; }
        public String exactState() { return exactState; }
        public byte[] inputNbt() { return inputNbt == null ? null : inputNbt.clone(); }
    }

    /**
     * Caller-supplied per-cell processor chain. Implementations receive the recording world, so any
     * height or block-state probe they make is transcribed in place.
     */
    public interface CellProcessor {
        ProcessedCell process(Piece piece, Cell cell, ProcessedCell input, PlacementWorld world);
    }

    /** Caller-supplied canonical block-entity NBT authority. */
    public interface BlockEntitySynthesizer {
        byte[] canonicalNbt(Piece piece, Cell cell, Vec position, String exactState,
                String blockEntityType, String lootTable, long lootSeed);
    }

    /** The placement random stream; only {@code nextLong()} is consumed by this loop. */
    public interface PlacementRandom {
        long nextLong();
    }

    /** Mutable block entity handle exposed by an isolated transaction. */
    public interface BlockEntityHandle {
        String blockEntityType();
        void loadCanonicalNbt(byte[] canonicalNbt);
        void setChanged();
    }

    /** World seam. Every method is transcribed by the executor before the caller sees it. */
    public interface PlacementWorld {
        int getHeight(String heightmap, int x, int z);
        String getBlockState(Vec position);
        /** Fluid registry key at the position, {@link #EMPTY_FLUID} when there is none. */
        String getFluidKey(Vec position);
        boolean isFluidSource(Vec position);
        double fluidHeight(Vec position);
        boolean setBlock(Vec position, String exactState, int flags);
        BlockEntityHandle getBlockEntity(Vec position);
        void scheduleFluidTick(Vec position, String fluidKey, int delay);
    }

    /** Inclusive world-space clip; cells outside it are never queried or written. */
    public static final class Clip {
        private final int minX, minY, minZ, maxX, maxY, maxZ;

        public Clip(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
            if (minX > maxX || minY > maxY || minZ > maxZ) {
                throw new IllegalArgumentException("inverted template placement clip");
            }
            this.minX = minX; this.minY = minY; this.minZ = minZ;
            this.maxX = maxX; this.maxY = maxY; this.maxZ = maxZ;
        }

        public static Clip chunk(int chunkX, int chunkZ, int minY, int maxY) {
            int x = Math.multiplyExact(chunkX, 16);
            int z = Math.multiplyExact(chunkZ, 16);
            return new Clip(x, minY, z, Math.addExact(x, 15), maxY, Math.addExact(z, 15));
        }

        public int minX() { return minX; } public int minY() { return minY; }
        public int minZ() { return minZ; } public int maxX() { return maxX; }
        public int maxY() { return maxY; } public int maxZ() { return maxZ; }

        public boolean contains(Vec position) {
            return position.x() >= minX && position.x() <= maxX
                    && position.y() >= minY && position.y() <= maxY
                    && position.z() >= minZ && position.z() <= maxZ;
        }
    }

    /** Immutable integer position. */
    public static final class Vec {
        private final int x, y, z;
        public Vec(int x, int y, int z) { this.x = x; this.y = y; this.z = z; }
        public int x() { return x; } public int y() { return y; } public int z() { return z; }
        @Override public boolean equals(Object other) {
            return other instanceof Vec value && x == value.x && y == value.y && z == value.z;
        }
        @Override public int hashCode() { return Objects.hash(x, y, z); }
        @Override public String toString() { return "[" + x + ", " + y + ", " + z + "]"; }
    }

    public static final class FinalCell {
        private final Vec position;
        private final String exactState;
        private FinalCell(Vec position, String exactState) {
            this.position = position; this.exactState = exactState;
        }
        public Vec position() { return position; }
        public String exactState() { return exactState; }
    }

    public static final class BentRow {
        private final Vec position;
        private final String exactState, blockEntityType;
        private final byte[] canonicalNbt;
        private BentRow(Vec position, String exactState, String blockEntityType, byte[] nbt) {
            this.position = position; this.exactState = exactState;
            this.blockEntityType = blockEntityType; this.canonicalNbt = nbt.clone();
        }
        public Vec position() { return position; }
        public String exactState() { return exactState; }
        public String blockEntityType() { return blockEntityType; }
        public byte[] canonicalNbt() { return canonicalNbt.clone(); }
    }

    public static final class LootRow {
        private final Vec position;
        private final String table;
        private final long signedSeed;
        private final int bentOrdinal;
        private LootRow(Vec position, String table, long signedSeed, int bentOrdinal) {
            this.position = position; this.table = table;
            this.signedSeed = signedSeed; this.bentOrdinal = bentOrdinal;
        }
        public Vec position() { return position; }
        public String table() { return table; }
        public long signedSeed() { return signedSeed; }
        public int bentOrdinal() { return bentOrdinal; }
    }

    public static final class TickRow {
        private final Vec position;
        private final String key;
        private final int delay, priority;
        private final long subTickOrder;
        private TickRow(Vec position, String key, int delay, int priority, long subTickOrder) {
            this.position = position; this.key = key; this.delay = delay;
            this.priority = priority; this.subTickOrder = subTickOrder;
        }
        public Vec position() { return position; }
        public String key() { return key; }
        public int delay() { return delay; }
        public int priority() { return priority; }
        public long subTickOrder() { return subTickOrder; }
    }

    /** Complete result of one clip execution. */
    public static final class Result {
        private final List<String> operations;
        private final List<FinalCell> finalCells;
        private final List<BentRow> bent;
        private final List<LootRow> loot;
        private final List<TickRow> blockTicks;

        private Result(List<String> operations, List<FinalCell> finalCells, List<BentRow> bent,
                List<LootRow> loot, List<TickRow> blockTicks) {
            this.operations = List.copyOf(operations);
            this.finalCells = List.copyOf(finalCells);
            this.bent = List.copyOf(bent);
            this.loot = List.copyOf(loot);
            this.blockTicks = List.copyOf(blockTicks);
        }

        public List<String> operations() { return operations; }
        public List<FinalCell> finalCells() { return finalCells; }
        public List<BentRow> bent() { return bent; }
        public List<LootRow> loot() { return loot; }
        public List<TickRow> blockTicks() { return blockTicks; }
    }

    /**
     * Ordered operation transcript in the authenticated corpus row grammar. Every row is
     * {@code <ordinal>|<kind>|<detail>} with the per-kind detail forms proven by the Village
     * settlement corpus, for example
     * {@code 297|getFluidState|pos:[-920, 64, 3529]->fluid:minecraft:empty},
     * {@code 298|setBlock|pos:[-920, 64, 3529]|state:minecraft:dirt_path|18->true},
     * {@code 48|getBlockState|pos:[-920, 63, 3529]->state:minecraft:dirt},
     * {@code 0|getHeight|WORLD_SURFACE_WG|-920|3535->65} and
     * {@code 1441|scheduleTick|pos:[-919, 67, 3527]|net.minecraft.world.level.block.LeavesBlock|1->null}.
     */
    public static final class Transcript {
        private final ArrayList<String> rows = new ArrayList<>();

        private void add(String detail) {
            rows.add(rows.size() + "|" + detail);
        }

        void getHeight(String heightmap, int x, int z, int result) {
            add("getHeight|" + heightmap + "|" + x + "|" + z + "->" + result);
        }

        void getBlockState(Vec position, String state) {
            add("getBlockState|pos:" + position + "->state:" + state);
        }

        void getFluidState(Vec position, String fluid) {
            add("getFluidState|pos:" + position + "->fluid:" + fluid);
        }

        void setBlock(Vec position, String state, int flags, boolean result) {
            add("setBlock|pos:" + position + "|state:" + state + "|" + flags + "->" + result);
        }

        void getBlockEntity(Vec position, String blockEntityType) {
            add("getBlockEntity|pos:" + position + "->blockEntity:"
                    + (blockEntityType == null ? "null" : blockEntityType + "@" + position));
        }

        void scheduleTick(Vec position, String key, int delay, String result) {
            add("scheduleTick|pos:" + position + "|" + key + "|" + delay + "->" + result);
        }

        public List<String> rows() { return List.copyOf(rows); }
    }

    /**
     * Executes the intersecting cells of one already-transformed piece list against a caller world.
     * Pieces run in list order; the caller owns piece selection, ordering and isolation.
     */
    public static Result execute(List<Piece> pieces, Clip clip, PlacementWorld world,
            CellProcessor processor, BlockEntitySynthesizer synthesizer, PlacementRandom random) {
        Objects.requireNonNull(pieces, "template pieces");
        Objects.requireNonNull(clip, "template clip");
        Objects.requireNonNull(world, "template placement world");

        Transcript transcript = new Transcript();
        RecordingWorld recording = new RecordingWorld(world, transcript);
        LinkedHashMap<Vec, String> finalStates = new LinkedHashMap<>();
        LinkedHashMap<Vec, StagedEntity> entities = new LinkedHashMap<>();
        ArrayList<TickRow> ticks = new ArrayList<>();

        for (Piece piece : pieces) {
            placePiece(piece, clip, recording, processor, synthesizer, random,
                    finalStates, entities, ticks);
        }

        ArrayList<Map.Entry<Vec, String>> ordered = new ArrayList<>(finalStates.entrySet());
        ordered.sort((left, right) -> POSITION_ORDER.compare(left.getKey(), right.getKey()));
        ArrayList<FinalCell> cells = new ArrayList<>(ordered.size());
        for (Map.Entry<Vec, String> entry : ordered) {
            cells.add(new FinalCell(entry.getKey(), entry.getValue()));
        }

        ArrayList<StagedEntity> staged = new ArrayList<>(entities.values());
        staged.sort((left, right) -> POSITION_ORDER.compare(left.position, right.position));
        ArrayList<BentRow> bent = new ArrayList<>(staged.size());
        ArrayList<LootRow> orderedLoot = new ArrayList<>();
        for (StagedEntity entity : staged) {
            String state = finalStates.get(entity.position);
            if (state == null) {
                throw new IllegalStateException("template BENT lost its final state at "
                        + entity.position);
            }
            int bentOrdinal = bent.size();
            bent.add(new BentRow(entity.position, state, entity.blockEntityType, entity.nbt));
            if (entity.lootTable != null) {
                orderedLoot.add(new LootRow(entity.position, entity.lootTable, entity.lootSeed,
                        bentOrdinal));
            }
        }
        return new Result(transcript.rows(), cells, bent, orderedLoot, ticks);
    }

    private static void placePiece(Piece piece, Clip clip, RecordingWorld world,
            CellProcessor processor, BlockEntitySynthesizer synthesizer, PlacementRandom random,
            Map<Vec, String> finalStates, Map<Vec, StagedEntity> entities, List<TickRow> ticks) {
        ArrayList<Vec> pendingLiquids = new ArrayList<>();
        // placeInWorld's second keepLiquids list: positions where the piece itself wrote a fluid
        // source. It is never relaxed; it only excludes those positions from being borrowed as a
        // neighbouring source by the post-pass.
        ArrayList<Vec> placedSources = new ArrayList<>();
        ArrayList<StagedEntity> pieceEntities = new ArrayList<>();

        // Vanilla runs the whole piece through StructureTemplate.processBlockInfos first and only
        // then walks the processed list writing blocks, so every processor world query of a piece
        // precedes every write of that piece. The pre-chain drops are the two processors
        // SinglePoolElement.getSettings installs ahead of the element and projection processors:
        // BlockIgnoreProcessor.STRUCTURE_BLOCK and JigsawReplacementProcessor.
        ArrayList<Cell> sources = new ArrayList<>();
        ArrayList<ProcessedCell> processed = new ArrayList<>();
        for (Cell cell : piece.program().cellsInPlacementOrder()) {
            String sourceState = Objects.requireNonNull(cell.exactState(), "template cell state");
            String sourceBlock = blockKey(sourceState);
            // JigsawReplacementProcessor, installed by SinglePoolElement.getSettings ahead of the
            // element and projection processors: a jigsaw cell becomes its block entity's
            // final_state (default minecraft:air), keeps its position, loses its NBT, and is dropped
            // outright when that state is minecraft:structure_void. It is never simply skipped.
            if (JIGSAW_BLOCK.equals(sourceBlock)) {
                String finalState = cell.jigsawFinalState();
                if (finalState == null) finalState = AIR_BLOCK;
                if (STRUCTURE_VOID.equals(blockKey(finalState))) continue;
                sourceState = finalState;
                sourceBlock = blockKey(finalState);
            }
            if (ignored(piece.ignorePolicy(), sourceState)) continue;

            Vec local = new Vec(cell.localX(), cell.localY(), cell.localZ());
            Vec position = add(piece.origin(), transform(local, piece.mirror(), piece.rotation()));
            if (!clip.contains(position)) continue;

            ProcessedCell candidate = new ProcessedCell(position,
                    transformState(sourceState, piece.mirror(), piece.rotation()), null);
            if (processor != null) {
                candidate = processor.process(piece, cell, candidate, world);
                if (candidate == null) continue;
            }
            sources.add(cell);
            processed.add(candidate);
        }

        for (int index = 0; index < processed.size(); index++) {
            Cell cell = sources.get(index);
            ProcessedCell candidate = processed.get(index);
            if (!clip.contains(candidate.position())) continue;
            Vec target = candidate.position();
            String state = candidate.exactState();
            String block = blockKey(state);
            // A STRUCTURE_BLOCK-policy element keeps its air cells through the chain (only the
            // element's own BlockIgnoreProcessor list drops blocks), and the authenticated corpus
            // carries no air write anywhere, so the air drop lands on the write instead.
            if (STRUCTURE_BLOCK.equals(block) || AIR_BLOCK.equals(block)
                    || JIGSAW_BLOCK.equals(block)) {
                continue;
            }

            String retainedFluid = EMPTY_FLUID;
            boolean retainedSource = false;
            if (piece.keepLiquids()) {
                retainedFluid = world.getFluidKey(target);
                retainedSource = !EMPTY_FLUID.equals(retainedFluid) && world.rawIsFluidSource(target);
            }

            String blockEntityType = cell.blockEntityType();
            if (blockEntityType != null && world.setBlock(target, BARRIER_STATE,
                    BLOCK_ENTITY_CLEAR_FLAGS)) {
                finalStates.put(target, BARRIER_STATE);
                entities.remove(target);
            }

            if (!world.setBlock(target, state, piece.writeFlags())) continue;
            finalStates.put(target, state);
            if (blockEntityType == null) entities.remove(target);

            if (blockEntityType != null) {
                if (synthesizer == null) {
                    throw new IllegalStateException(
                            "template block entity without a caller BENT synthesizer at " + target);
                }
                String lootTable = cell.lootTable();
                long lootSeed = 0L;
                if (lootTable != null) {
                    if (random == null) {
                        throw new IllegalStateException(
                                "loot container without a caller placement random at " + target);
                    }
                    // RandomizableContainer.setLootTable consumes exactly one placement nextLong().
                    lootSeed = random.nextLong();
                }
                byte[] nbt = Objects.requireNonNull(synthesizer.canonicalNbt(piece, cell, target,
                        state, blockEntityType, lootTable, lootSeed), "canonical BENT payload");
                BlockEntityHandle handle = world.getBlockEntity(target);
                if (handle == null || !blockEntityType.equals(handle.blockEntityType())) {
                    throw new IllegalStateException(
                            "template block entity lifecycle mismatch at " + target);
                }
                handle.loadCanonicalNbt(nbt.clone());
                StagedEntity staged = new StagedEntity(target, blockEntityType, lootTable,
                        lootSeed, nbt);
                pieceEntities.add(staged);
                entities.put(target, staged);
            }

            // placeInWorld's keepLiquids bookkeeping, on the state it just wrote:
            //   blockstate1.getFluidState().isSource()          -> post-pass candidate
            //   else blockstate1.getBlock() instanceof LiquidBlockContainer
            //        -> placeLiquid(level, pos, blockstate1, fluidstate), and the position becomes a
            //           post-pass candidate whenever the pre-existing fluidstate was not a source.
            // The candidate list therefore holds every written waterloggable position, including the
            // ones whose pre-existing fluid was empty; the corpus records their neighbour scan.
            if (piece.keepLiquids()) {
                if (fluidSourceState(state)) {
                    placedSources.add(target);
                } else if (waterloggable(state)) {
                    if (WATER_FLUID.equals(retainedFluid) && dryWaterlogged(state)) {
                        placeWater(target, wetSuccessor(state), piece.writeFlags(), world,
                                finalStates, entities, ticks);
                    }
                    if (!retainedSource) pendingLiquids.add(target);
                }
            }
        }

        restorePendingLiquids(pendingLiquids, placedSources, piece.writeFlags(), world, finalStates,
                entities, ticks);
        for (StagedEntity staged : pieceEntities) {
            BlockEntityHandle handle = world.getBlockEntity(staged.position);
            if (handle == null || !staged.blockEntityType.equals(handle.blockEntityType())) {
                throw new IllegalStateException(
                        "template block entity vanished before setChanged at " + staged.position);
            }
            handle.setChanged();
        }
    }

    /**
     * The vanilla {@code placeInWorld} keepLiquids relaxation, byte for byte: while any candidate
     * changed, each candidate re-reads its own fluid and then its {@code UP, NORTH, EAST, SOUTH,
     * WEST} neighbours until one of them is a source the piece did not itself write; a candidate
     * that ends on a source and still holds a {@code LiquidBlockContainer} is fed
     * {@code placeLiquid} and leaves the list whether or not the call actually flooded it.
     */
    private static void restorePendingLiquids(List<Vec> pending, List<Vec> placedSources,
            int writeFlags, RecordingWorld world, Map<Vec, String> finalStates,
            Map<Vec, StagedEntity> entities, List<TickRow> ticks) {
        boolean changed = true;
        while (changed && !pending.isEmpty()) {
            changed = false;
            Iterator<Vec> iterator = pending.iterator();
            while (iterator.hasNext()) {
                Vec position = iterator.next();
                String bestFluid = world.getFluidKey(position);
                boolean bestSource = !EMPTY_FLUID.equals(bestFluid)
                        && world.rawIsFluidSource(position);
                for (Direction direction : LIQUID_NEIGHBORS) {
                    if (bestSource) break;
                    Vec neighbor = offset(position, direction);
                    String fluid = world.getFluidKey(neighbor);
                    if (EMPTY_FLUID.equals(fluid) || !world.rawIsFluidSource(neighbor)) continue;
                    if (placedSources.contains(neighbor)) continue;
                    bestFluid = fluid;
                    bestSource = true;
                }
                if (!bestSource) continue;
                String current = world.getBlockState(position);
                if (!waterloggable(current)) continue;
                if (WATER_FLUID.equals(bestFluid) && dryWaterlogged(current)) {
                    placeWater(position, wetSuccessor(current), writeFlags, world, finalStates,
                            entities, ticks);
                }
                iterator.remove();
                changed = true;
            }
        }
    }

    private static void placeWater(Vec position, String wetState, int writeFlags,
            RecordingWorld world, Map<Vec, String> finalStates, Map<Vec, StagedEntity> entities,
            List<TickRow> ticks) {
        if (world.setBlock(position, wetState, writeFlags)) {
            finalStates.put(position, wetState);
            StagedEntity staged = entities.get(position);
            if (staged != null && !staged.blockEntityType.equals(blockKey(wetState))) {
                entities.remove(position);
            }
        }
        world.scheduleFluidTick(position, WATER_FLUID, WATER_TICK_DELAY);
        // ProtoChunk keeps one fluid-tick list per chunk in encounter order, so the raw encounter
        // index is the running size of this placement's tick list, never a per-row constant.
        ticks.add(new TickRow(position, WATER_FLUID, WATER_TICK_DELAY, 0, ticks.size()));
    }

    private static boolean ignored(IgnorePolicy policy, String state) {
        String block = blockKey(state);
        if (STRUCTURE_BLOCK.equals(block)) return true;
        return policy == IgnorePolicy.STRUCTURE_AND_AIR && AIR_BLOCK.equals(block);
    }

    /**
     * Vanilla {@code StructureTemplate.transform(BlockPos, Mirror, Rotation, BlockPos)} with a zero
     * pivot: mirror first, rotation second.
     */
    public static Vec transform(Vec local, Mirror mirror, Rotation rotation) {
        Objects.requireNonNull(local, "template local position");
        Objects.requireNonNull(mirror, "template mirror");
        Objects.requireNonNull(rotation, "template rotation");
        int x = local.x(), y = local.y(), z = local.z();
        switch (mirror) {
            case LEFT_RIGHT -> z = Math.negateExact(z);
            case FRONT_BACK -> x = Math.negateExact(x);
            case NONE -> { }
        }
        return switch (rotation) {
            case NONE -> new Vec(x, y, z);
            case CLOCKWISE_90 -> new Vec(Math.negateExact(z), y, x);
            case CLOCKWISE_180 -> new Vec(Math.negateExact(x), y, Math.negateExact(z));
            case COUNTERCLOCKWISE_90 -> new Vec(z, y, Math.negateExact(x));
        };
    }

    /**
     * Applies the same mirror-before-rotation transform to an exact block state. Only the
     * authenticated directional property families are transformed; an unknown directional property
     * fails closed rather than being guessed.
     */
    public static String transformState(String exactState, Mirror mirror, Rotation rotation) {
        Objects.requireNonNull(exactState, "template exact state");
        if (mirror == Mirror.NONE && rotation == Rotation.NONE) return exactState;
        int open = exactState.indexOf('[');
        if (open < 0) return exactState;
        if (!exactState.endsWith("]") || open == 0) {
            throw new IllegalArgumentException("malformed template exact state: " + exactState);
        }
        String block = exactState.substring(0, open).trim();
        String body = exactState.substring(open + 1, exactState.length() - 1);
        TreeMap<String, String> properties = new TreeMap<>();
        if (!body.isBlank()) {
            for (String raw : body.split(",")) {
                String item = raw.trim();
                int equals = item.indexOf('=');
                if (equals <= 0 || equals == item.length() - 1) {
                    throw new IllegalArgumentException("malformed template state property: " + item);
                }
                String key = transformKey(item.substring(0, equals).trim(), mirror, rotation);
                String value = transformValue(key, item.substring(equals + 1).trim(),
                        mirror, rotation);
                if (properties.put(key, value) != null) {
                    throw new IllegalArgumentException(
                            "duplicate template state property after transform: " + key);
                }
            }
        }
        StringBuilder result = new StringBuilder(block).append('[');
        boolean first = true;
        for (Map.Entry<String, String> property : properties.entrySet()) {
            if (!first) result.append(',');
            result.append(property.getKey()).append('=').append(property.getValue());
            first = false;
        }
        return result.append(']').toString();
    }

    private static String transformKey(String key, Mirror mirror, Rotation rotation) {
        return HORIZONTAL.contains(key) ? horizontal(key, mirror, rotation) : key;
    }

    private static String transformValue(String key, String value, Mirror mirror,
            Rotation rotation) {
        if (HORIZONTAL.contains(value)) return horizontal(value, mirror, rotation);
        if ("axis".equals(key) && quarterTurn(rotation)) {
            if ("x".equals(value)) return "z";
            if ("z".equals(value)) return "x";
            return value;
        }
        if ("rotation".equals(key)) {
            int stored = Integer.parseInt(value);
            if (stored < 0 || stored > 15) {
                throw new IllegalArgumentException("template rotation property out of range: " + value);
            }
            int mirrored = switch (mirror) {
                case NONE -> stored;
                case LEFT_RIGHT -> (16 - stored) & 15;
                case FRONT_BACK -> (24 - stored) & 15;
            };
            return Integer.toString((mirrored + quarterTurns(rotation) * 4) & 15);
        }
        if ("hinge".equals(key) && mirror != Mirror.NONE) {
            return "left".equals(value) ? "right" : "left";
        }
        // StairBlock.rotate is FACING-only: it never touches SHAPE, so a rotated stair keeps its
        // stored corner shape. Only StairBlock.mirror rewrites SHAPE (the axis-dependent
        // inner_left/inner_right and outer_left/outer_right swap), and no authenticated corpus row
        // exercises a mirrored non-straight stair here, so that case still fails closed.
        if ("shape".equals(key) && !"straight".equals(value) && mirror != Mirror.NONE) {
            throw new IllegalArgumentException(
                    "unauthenticated transformed template shape property: " + value);
        }
        return value;
    }

    private static String horizontal(String value, Mirror mirror, Rotation rotation) {
        String mirrored = switch (mirror) {
            case NONE -> value;
            case LEFT_RIGHT -> "north".equals(value) ? "south" : "south".equals(value)
                    ? "north" : value;
            case FRONT_BACK -> "east".equals(value) ? "west" : "west".equals(value)
                    ? "east" : value;
        };
        return HORIZONTAL.get((HORIZONTAL.indexOf(mirrored) + quarterTurns(rotation)) & 3);
    }

    private static int quarterTurns(Rotation rotation) {
        return switch (rotation) {
            case NONE -> 0;
            case CLOCKWISE_90 -> 1;
            case CLOCKWISE_180 -> 2;
            case COUNTERCLOCKWISE_90 -> 3;
        };
    }

    private static boolean quarterTurn(Rotation rotation) {
        return rotation == Rotation.CLOCKWISE_90 || rotation == Rotation.COUNTERCLOCKWISE_90;
    }

    /**
     * {@code SimpleWaterloggedBlock}, the {@code LiquidBlockContainer} family a template can write:
     * the block carries the {@code waterlogged} property.
     */
    private static boolean waterloggable(String state) {
        return state.contains("waterlogged=");
    }

    /** {@code blockstate.getFluidState().isSource()} for a written fluid block. */
    private static boolean fluidSourceState(String state) {
        return WATER_FLUID.equals(blockKey(state)) && state.contains("level=0");
    }

    private static boolean dryWaterlogged(String state) {
        return state.contains("waterlogged=false");
    }

    private static String wetSuccessor(String state) {
        return state.replace("waterlogged=false", "waterlogged=true");
    }

    private static String blockKey(String state) {
        int property = state.indexOf('[');
        return property < 0 ? state.trim() : state.substring(0, property).trim();
    }

    private static Vec add(Vec base, Vec delta) {
        return new Vec(Math.addExact(base.x(), delta.x()), Math.addExact(base.y(), delta.y()),
                Math.addExact(base.z(), delta.z()));
    }

    private static Vec offset(Vec position, Direction direction) {
        return new Vec(Math.addExact(position.x(), direction.dx),
                Math.addExact(position.y(), direction.dy),
                Math.addExact(position.z(), direction.dz));
    }

    private static final class StagedEntity {
        private final Vec position;
        private final String blockEntityType, lootTable;
        private final long lootSeed;
        private final byte[] nbt;
        private StagedEntity(Vec position, String blockEntityType, String lootTable, long lootSeed,
                byte[] nbt) {
            this.position = position; this.blockEntityType = blockEntityType;
            this.lootTable = lootTable; this.lootSeed = lootSeed; this.nbt = nbt.clone();
        }
    }

    /** Transcribing wrapper; processors and the loop share it so every access is in order. */
    private static final class RecordingWorld implements PlacementWorld {
        private final PlacementWorld inner;
        private final Transcript transcript;

        private RecordingWorld(PlacementWorld inner, Transcript transcript) {
            this.inner = inner;
            this.transcript = transcript;
        }

        @Override public int getHeight(String heightmap, int x, int z) {
            int result = inner.getHeight(heightmap, x, z);
            transcript.getHeight(heightmap, x, z, result);
            return result;
        }

        @Override public String getBlockState(Vec position) {
            String state = inner.getBlockState(position);
            transcript.getBlockState(position, state);
            return state;
        }

        @Override public String getFluidKey(Vec position) {
            String fluid = inner.getFluidKey(position);
            transcript.getFluidState(position, fluid);
            return fluid;
        }

        /** Part of the same transcribed {@code getFluidState}; never a second world access. */
        private boolean rawIsFluidSource(Vec position) { return inner.isFluidSource(position); }

        private double rawFluidHeight(Vec position) { return inner.fluidHeight(position); }

        @Override public boolean isFluidSource(Vec position) { return rawIsFluidSource(position); }

        @Override public double fluidHeight(Vec position) { return rawFluidHeight(position); }

        @Override public boolean setBlock(Vec position, String exactState, int flags) {
            boolean result = inner.setBlock(position, exactState, flags);
            transcript.setBlock(position, exactState, flags, result);
            return result;
        }

        @Override public BlockEntityHandle getBlockEntity(Vec position) {
            BlockEntityHandle handle = inner.getBlockEntity(position);
            transcript.getBlockEntity(position, handle == null ? null : handle.blockEntityType());
            return handle;
        }

        @Override public void scheduleFluidTick(Vec position, String fluidKey, int delay) {
            inner.scheduleFluidTick(position, fluidKey, delay);
            transcript.scheduleTick(position, WATER_TICK_IDENTITY, delay, "null");
        }
    }
}
