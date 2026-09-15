package com.gameexpert.terrain.mc.structure;

import com.gameexpert.terrain.mc.ore.Mc263DecorationRandom.WorldgenRandom;
import com.gameexpert.terrain.mc.structure.Mc263HardcodedStructureCarrier.BoundingBox;
import com.gameexpert.terrain.mc.structure.Mc263HardcodedStructureCarrier.Kind;
import com.gameexpert.terrain.mc.structure.Mc263HardcodedStructureCarrier.PieceFact;
import com.gameexpert.terrain.mc.structure.Mc263HardcodedStructureCarrier.PieceKind;
import com.gameexpert.terrain.mc.structure.Mc263HardcodedStructureCarrier.Processor;
import com.gameexpert.terrain.mc.structure.Mc263HardcodedStructureCarrier.ProcessorDecision;
import com.gameexpert.terrain.mc.structure.Mc263HardcodedStructureCarrier.Rotation;
import com.gameexpert.terrain.mc.structure.Mc263HardcodedStructureCarrier.SuccessorPayload;
import com.gameexpert.terrain.mc.structure.Mc263HardcodedStructureCarrier.Template;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Dormant mutation executor for the pinned 26.3 hardcoded Overworld structure carriers.
 *
 * <p>The program is an expanded, ordered transcription of the pinned Java piece source, not an
 * NBT template. Expansion keeps every block query, RNG draw, write, and sidecar action visible.
 * This class deliberately has no canonical-pipeline registration. A caller must supply a complete
 * program whose source receipt names the pinned server SHA-1 and whose pieces exactly match the
 * carrier. That boundary permits procedural source transcriptions and project-owned igloo block
 * programs without embedding or accepting a Mojang template asset.</p>
 *
 * <p>All exact output states and sidecar capabilities are checked before height queries, live
 * block queries, RNG draws, or mutation. Consequently an unrepresentable template block fails the
 * entire execution atomically instead of being discarded or approximated.</p>
 */
public final class Mc263HardcodedStructureExecutor {
    public static final String VERSION = Mc263HardcodedStructureCarrier.VERSION;
    public static final String SERVER_SHA1 = Mc263HardcodedStructureCarrier.SERVER_SHA1;

    private static final int FLOAT_SAMPLE_SPACE = 1 << 24;

    private Mc263HardcodedStructureExecutor() { }

    public static final class BlockPos {
        private final int x;
        private final int y;
        private final int z;

        public BlockPos(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        public int x() { return x; }
        public int y() { return y; }
        public int z() { return z; }

        private BlockPos below() { return new BlockPos(x, Math.subtractExact(y, 1), z); }

        @Override public boolean equals(Object other) {
            return other instanceof BlockPos value
                    && x == value.x && y == value.y && z == value.z;
        }

        @Override public int hashCode() { return Objects.hash(x, y, z); }
    }

    public static final class Clip {
        private final int minX;
        private final int minY;
        private final int minZ;
        private final int maxX;
        private final int maxY;
        private final int maxZ;

        public Clip(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
            if (minX > maxX || minY > maxY || minZ > maxZ) {
                throw new IllegalArgumentException("inverted hardcoded-structure clip");
            }
            this.minX = minX; this.minY = minY; this.minZ = minZ;
            this.maxX = maxX; this.maxY = maxY; this.maxZ = maxZ;
        }

        public boolean contains(BlockPos position) {
            return position.x >= minX && position.x <= maxX
                    && position.y >= minY && position.y <= maxY
                    && position.z >= minZ && position.z <= maxZ;
        }
    }

    /** Exact mutable-region and sidecar operations already used by canonical feature leaves. */
    public interface WorldAccess {
        /** Pure declaration. It must not inspect mutable region state. */
        boolean supportsExactState(String exactState);
        /** Pure declarations. */
        boolean supportsMotionBlockingNoLeavesHeight();
        boolean supportsWorldSurfaceWgHeight();
        boolean supportsSeaLevel();
        boolean supportsReplaceableByStructuresQuery();
        boolean supportsLootSidecar();
        boolean supportsSpawnerSidecar();
        boolean supportsArchaeologySidecar();
        boolean supportsStructureEntitySidecar();

        int minY();
        int maxY();
        int motionBlockingNoLeaves(int blockX, int blockZ);
        int worldSurfaceWg(int blockX, int blockZ);
        int seaLevel();
        String blockState(BlockPos position);
        boolean isReplaceableByStructures(BlockPos position, String exactState);
        boolean setBlock(BlockPos position, String exactState, int flags);
        boolean hasRandomizableContainer(BlockPos position);
        void setLoot(BlockPos position, String lootTable, long seed);
        void setSpawner(BlockPos position, String entityType);
        void setArchaeologyLoot(BlockPos position, String lootTable, long seed);
        void addStructureEntity(String entityType, String spawnReason, double x, double y,
                double z, float yaw, float pitch, byte[] canonicalPayload);
    }

    /** A complete ordered source transcription for one carrier. */
    public static final class Program {
        private final String sourceSha1;
        private final Kind kind;
        private final List<PieceProgram> pieces;

        public Program(String sourceSha1, Kind kind, List<PieceProgram> pieces) {
            this.sourceSha1 = Objects.requireNonNull(sourceSha1, "source SHA-1");
            this.kind = Objects.requireNonNull(kind, "kind");
            this.pieces = List.copyOf(Objects.requireNonNull(pieces, "pieces"));
        }

        public String sourceSha1() { return sourceSha1; }
        public Kind kind() { return kind; }
        public List<PieceProgram> pieces() { return pieces; }
    }

    public static final class PieceProgram {
        private final PieceKind kind;
        private final Template template;
        private final List<Command> commands;

        public PieceProgram(PieceKind kind, Template template, List<Command> commands) {
            this.kind = Objects.requireNonNull(kind, "piece kind");
            this.template = template;
            this.commands = List.copyOf(Objects.requireNonNull(commands, "commands"));
            if ((kind == PieceKind.IGLOO_TEMPLATE) != (template != null)) {
                throw new IllegalArgumentException("template identity must match igloo piece");
            }
        }

        public PieceKind kind() { return kind; }
        public Template template() { return template; }
        public List<Command> commands() { return commands; }
    }

    /** One source-order action. Commands are intentionally not coalesced or reordered. */
    public abstract static class Command {
        private final int x;
        private final int y;
        private final int z;

        private Command(int x, int y, int z) {
            this.x = x; this.y = y; this.z = z;
        }

        final int x() { return x; }
        final int y() { return y; }
        final int z() { return z; }
        abstract void collectRequirements(Requirements requirements, Rotation rotation);
        abstract int apply(Context context, PieceFact piece, Rotation rotation, int yDelta);
    }

    /** One exact setBlock(..., flags=2), optionally passed through a carried processor. */
    public static final class PlaceBlock extends Command {
        private final ExactStates states;
        private final Processor processor;

        public PlaceBlock(int x, int y, int z, ExactStates states) {
            this(x, y, z, states, null);
        }

        public PlaceBlock(int x, int y, int z, ExactStates states, Processor processor) {
            super(x, y, z);
            this.states = Objects.requireNonNull(states, "states");
            this.processor = processor;
        }

        @Override void collectRequirements(Requirements requirements, Rotation rotation) {
            requirements.states.add(states.forRotation(rotation));
            if (processor != null) requirements.processors.add(processor);
        }

        @Override int apply(Context context, PieceFact piece, Rotation rotation, int yDelta) {
            String state = states.forRotation(rotation);
            BlockPos position = worldPos(piece, rotation, x(), y(), z(), yDelta);
            if (processor == Processor.MONUMENT_FILL_KEEP
                    && !context.clip.contains(position)) return 0;
            String processed = context.process(processor, state, position);
            if (processed == null || !context.clip.contains(position)) return 0;
            context.world.setBlock(position, processed, 2);
            return 1;
        }
    }

    /** Exact fillColumnDown loop; its live replaceability query is never synthesized. */
    public static final class FillColumnDown extends Command {
        private final ExactStates states;

        public FillColumnDown(int x, int y, int z, ExactStates states) {
            super(x, y, z);
            this.states = Objects.requireNonNull(states, "states");
        }

        @Override void collectRequirements(Requirements requirements, Rotation rotation) {
            requirements.states.add(states.forRotation(rotation));
            requirements.replaceableQuery = true;
        }

        @Override int apply(Context context, PieceFact piece, Rotation rotation, int yDelta) {
            BlockPos position = worldPos(piece, rotation, x(), y(), z(), yDelta);
            if (!context.clip.contains(position)) return 0;
            String state = states.forRotation(rotation);
            int writes = 0;
            while (position.y() > context.world.minY() + 1) {
                String existing = requireState(context.world.blockState(position));
                if (!context.world.isReplaceableByStructures(position, existing)) break;
                context.world.setBlock(position, state, 2);
                writes++;
                position = position.below();
            }
            return writes;
        }
    }

    public enum SidecarKind { LOOT, SPAWNER, ARCHAEOLOGY, ENTITY }

    /**
     * A source-order sidecar action. LOOT targets an existing randomizable container; SPAWNER and
     * ARCHAEOLOGY target the block at the command position; ENTITY uses its block center.
     */
    public static final class Sidecar extends Command {
        private final SidecarKind kind;
        private final String key;
        private final String successorFlag;
        private final boolean flagOnClip;
        private final byte[] entityPayload;

        public Sidecar(int x, int y, int z, SidecarKind kind, String key,
                String successorFlag, boolean flagOnClip, byte[] entityPayload) {
            super(x, y, z);
            this.kind = Objects.requireNonNull(kind, "sidecar kind");
            this.key = requireResourceKey(key);
            this.successorFlag = successorFlag;
            this.flagOnClip = flagOnClip;
            this.entityPayload = entityPayload == null ? new byte[0] : entityPayload.clone();
        }

        @Override void collectRequirements(Requirements requirements, Rotation rotation) {
            switch (kind) {
                case LOOT -> requirements.loot = true;
                case SPAWNER -> requirements.spawner = true;
                case ARCHAEOLOGY -> requirements.archaeology = true;
                case ENTITY -> requirements.entity = true;
            }
        }

        @Override int apply(Context context, PieceFact piece, Rotation rotation, int yDelta) {
            if (successorFlag != null && context.successor.flag(successorFlag)) return 0;
            BlockPos position = worldPos(piece, rotation, x(), y(), z(), yDelta);
            if (!context.clip.contains(position)) return 0;
            boolean completed = switch (kind) {
                case LOOT -> {
                    if (!context.world.hasRandomizableContainer(position)) yield false;
                    context.world.setLoot(position, key, context.random.nextLong());
                    yield true;
                }
                case SPAWNER -> {
                    context.world.setSpawner(position, key);
                    yield true;
                }
                case ARCHAEOLOGY -> {
                    context.world.setArchaeologyLoot(position, key, context.random.nextLong());
                    yield true;
                }
                case ENTITY -> {
                    context.world.addStructureEntity(key, "minecraft:structure",
                            position.x() + 0.5D, position.y(), position.z() + 0.5D,
                            0.0F, 0.0F, entityPayload.clone());
                    yield true;
                }
            };
            if (successorFlag != null && (flagOnClip || completed)) {
                context.successor = context.successor.withFlag(successorFlag, true);
            }
            return completed ? 1 : 0;
        }
    }

    /** Explicit exact-state variants; the executor never guesses how properties rotate. */
    public static final class ExactStates {
        private final EnumMap<Rotation, String> states;

        private ExactStates(Map<Rotation, String> values) {
            states = new EnumMap<>(Rotation.class);
            for (Map.Entry<Rotation, String> entry : values.entrySet()) {
                states.put(Objects.requireNonNull(entry.getKey(), "rotation"),
                        requireState(entry.getValue()));
            }
        }

        public static ExactStates fixed(String state) {
            EnumMap<Rotation, String> values = new EnumMap<>(Rotation.class);
            for (Rotation rotation : Rotation.values()) values.put(rotation, state);
            return new ExactStates(values);
        }

        public static ExactStates of(Map<Rotation, String> values) {
            return new ExactStates(values);
        }

        String forRotation(Rotation rotation) {
            String result = states.get(rotation);
            if (result == null) {
                throw new UnsupportedOperationException(
                        "missing exact state for rotation " + rotation);
            }
            return result;
        }
    }

    public static final class ExecutionResult {
        private final Mc263HardcodedStructureCarrier successor;
        private final int writes;
        private final int sidecars;

        private ExecutionResult(Mc263HardcodedStructureCarrier successor,
                int writes, int sidecars) {
            this.successor = successor;
            this.writes = writes;
            this.sidecars = sidecars;
        }

        public Mc263HardcodedStructureCarrier successor() { return successor; }
        public int writes() { return writes; }
        public int sidecars() { return sidecars; }
    }

    /** Executes without creating or reseeding the supplied structure RNG. */
    public static ExecutionResult execute(Mc263HardcodedStructureCarrier carrier, Clip clip,
            Program program, WorldAccess world, WorldgenRandom random) {
        Objects.requireNonNull(carrier, "carrier");
        Objects.requireNonNull(clip, "clip");
        Objects.requireNonNull(program, "program");
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(random, "random");
        preflight(carrier, program, world);

        Context context = new Context(carrier, clip, world, random);
        int yDelta = alignHeight(carrier, clip, world, random, context);
        if (yDelta == Integer.MIN_VALUE) {
            return new ExecutionResult(carrier.withSuccessor(context.successor), 0, 0);
        }
        int writes = 0;
        int sidecars = 0;
        for (int index = 0; index < program.pieces().size(); index++) {
            PieceFact fact = carrier.orderedPieces().get(index);
            PieceProgram piece = program.pieces().get(index);
            int pieceYDelta = carrier.kind() == Kind.IGLOO
                    ? iglooHeightDelta(fact, carrier.rotation(), world) : yDelta;
            for (Command command : piece.commands()) {
                int applied = command.apply(context, fact, carrier.rotation(), pieceYDelta);
                if (command instanceof Sidecar) sidecars += applied; else writes += applied;
            }
        }
        return new ExecutionResult(carrier.withSuccessor(context.successor), writes, sidecars);
    }

    private static Requirements preflight(Mc263HardcodedStructureCarrier carrier,
            Program program, WorldAccess world) {
        if (!SERVER_SHA1.equals(program.sourceSha1())) {
            throw new IllegalArgumentException("hardcoded program is not pinned to server SHA-1");
        }
        if (program.kind() != carrier.kind()) {
            throw new IllegalArgumentException("hardcoded program kind disagrees with carrier");
        }
        if (program.pieces().size() != carrier.orderedPieces().size()) {
            throw new IllegalArgumentException("hardcoded program piece count disagrees with carrier");
        }
        carrier.preflight(carrier.requiredCapabilities(), carrier.requiredExactStates());
        Requirements requirements = new Requirements();
        requirements.height = isScattered(carrier.kind())
                && carrier.successor().heightPosition() < 0;
        requirements.worldSurface = carrier.kind() == Kind.IGLOO;
        for (int index = 0; index < program.pieces().size(); index++) {
            PieceProgram programPiece = program.pieces().get(index);
            PieceFact fact = carrier.orderedPieces().get(index);
            if (programPiece.kind() != fact.kind() || programPiece.template() != fact.template()) {
                throw new IllegalArgumentException(
                        "hardcoded program piece order disagrees with carrier");
            }
            for (Command command : programPiece.commands()) {
                Objects.requireNonNull(command, "command").collectRequirements(
                        requirements, carrier.rotation());
            }
        }
        for (ProcessorDecision decision : carrier.processorDecisions()) {
            if (requirements.processors.contains(decision.processor())
                    && !decision.discard()) requirements.states.add(decision.outputState());
        }
        if (requirements.processors.contains(Processor.MONUMENT_FILL_KEEP)) {
            requirements.states.add("minecraft:air");
            requirements.seaLevel = true;
        }
        for (String state : requirements.states) {
            if (!world.supportsExactState(state)) {
                throw new UnsupportedOperationException(
                        "unsupported hardcoded-structure exact state: " + state);
            }
        }
        require(!requirements.height || world.supportsMotionBlockingNoLeavesHeight(),
                "MOTION_BLOCKING_NO_LEAVES height");
        require(!requirements.worldSurface || world.supportsWorldSurfaceWgHeight(),
                "WORLD_SURFACE_WG height");
        require(!requirements.seaLevel || world.supportsSeaLevel(), "sea level");
        require(!requirements.replaceableQuery || world.supportsReplaceableByStructuresQuery(),
                "replaceable-by-structures query");
        require(!requirements.loot || world.supportsLootSidecar(), "loot sidecar");
        require(!requirements.spawner || world.supportsSpawnerSidecar(), "spawner sidecar");
        require(!requirements.archaeology || world.supportsArchaeologySidecar(),
                "archaeology sidecar");
        require(!requirements.entity || world.supportsStructureEntitySidecar(),
                "structure-entity sidecar");
        return requirements;
    }

    private static int alignHeight(Mc263HardcodedStructureCarrier carrier, Clip clip,
            WorldAccess world, WorldgenRandom random, Context context) {
        if (!isScattered(carrier.kind())) return 0;
        BoundingBox box = carrier.boundingBox();
        int height = carrier.successor().heightPosition();
        int offset = 0;
        if (height < 0) {
            if (carrier.kind() == Kind.DESERT_PYRAMID) {
                offset = -random.nextInt(3);
                int lowest = Math.addExact(world.maxY(), 1);
                for (int z = box.minZ(); z <= box.maxZ(); z++) {
                    for (int x = box.minX(); x <= box.maxX(); x++) {
                        lowest = Math.min(lowest, world.motionBlockingNoLeaves(x, z));
                    }
                }
                height = lowest;
            } else {
                long total = 0;
                int count = 0;
                for (int z = box.minZ(); z <= box.maxZ(); z++) {
                    for (int x = box.minX(); x <= box.maxX(); x++) {
                        if (!clip.contains(new BlockPos(x, 64, z))) continue;
                        total += world.motionBlockingNoLeaves(x, z);
                        count++;
                    }
                }
                if (count == 0) return Integer.MIN_VALUE;
                height = Math.toIntExact(total / count);
            }
            context.successor = context.successor.withHeightPosition(height);
        }
        return Math.addExact(Math.subtractExact(height, box.minY()), offset);
    }

    private static int iglooHeightDelta(PieceFact piece, Rotation rotation, WorldAccess world) {
        int entranceX;
        int entranceZ;
        switch (piece.template()) {
            case IGLOO_TOP -> { entranceX = 3; entranceZ = 0; }
            case IGLOO_MIDDLE -> { entranceX = 1; entranceZ = -4; }
            case IGLOO_BOTTOM -> { entranceX = 3; entranceZ = 2; }
            default -> throw new IllegalStateException("unknown igloo template");
        }
        BlockPos entrance = worldPos(piece, rotation, entranceX, 0, entranceZ, 0);
        return Math.subtractExact(world.worldSurfaceWg(entrance.x(), entrance.z()), 91);
    }

    private static BlockPos worldPos(PieceFact piece, Rotation rotation,
            int x, int y, int z, int yDelta) {
        if (piece.kind() == PieceKind.IGLOO_TEMPLATE) {
            int pivotX = switch (piece.template()) {
                case IGLOO_TOP, IGLOO_BOTTOM -> 3;
                case IGLOO_MIDDLE -> 1;
            };
            int pivotZ = switch (piece.template()) {
                case IGLOO_TOP -> 5;
                case IGLOO_MIDDLE -> 1;
                case IGLOO_BOTTOM -> 7;
            };
            int transformedX;
            int transformedZ;
            switch (rotation) {
                case NONE -> { transformedX = x; transformedZ = z; }
                case CLOCKWISE_180 -> {
                    transformedX = 2 * pivotX - x;
                    transformedZ = 2 * pivotZ - z;
                }
                case COUNTERCLOCKWISE_90 -> {
                    transformedX = pivotX - pivotZ + z;
                    transformedZ = pivotX + pivotZ - x;
                }
                case CLOCKWISE_90 -> {
                    transformedX = pivotX + pivotZ - z;
                    transformedZ = pivotZ - pivotX + x;
                }
                default -> throw new IllegalArgumentException("igloo requires quarter rotation");
            }
            return new BlockPos(Math.addExact(piece.templateX(), transformedX),
                    Math.addExact(Math.addExact(piece.templateY(), y), yDelta),
                    Math.addExact(piece.templateZ(), transformedZ));
        }
        BoundingBox box = piece.boundingBox();
        int worldX;
        int worldZ;
        switch (rotation) {
            case NORTH -> { worldX = box.minX() + x; worldZ = box.maxZ() - z; }
            case SOUTH -> { worldX = box.minX() + x; worldZ = box.minZ() + z; }
            case WEST -> { worldX = box.maxX() - z; worldZ = box.minZ() + x; }
            case EAST -> { worldX = box.minX() + z; worldZ = box.minZ() + x; }
            default -> throw new IllegalArgumentException("hardcoded piece requires cardinal rotation");
        }
        return new BlockPos(worldX, Math.addExact(box.minY() + y, yDelta), worldZ);
    }

    private static boolean isScattered(Kind kind) {
        return kind == Kind.DESERT_PYRAMID || kind == Kind.JUNGLE_PYRAMID
                || kind == Kind.SWAMP_HUT;
    }

    private static void require(boolean supported, String capability) {
        if (!supported) throw new UnsupportedOperationException(capability);
    }

    private static String requireState(String state) {
        Objects.requireNonNull(state, "exact state");
        if (state.isEmpty() || !state.startsWith("minecraft:")) {
            throw new IllegalArgumentException("noncanonical exact state: " + state);
        }
        return state;
    }

    private static String requireResourceKey(String key) {
        Objects.requireNonNull(key, "resource key");
        if (!key.startsWith("minecraft:") || key.indexOf('[') >= 0) {
            throw new IllegalArgumentException("noncanonical resource key: " + key);
        }
        return key;
    }

    private static String blockKey(String state) {
        int property = state.indexOf('[');
        return property < 0 ? state : state.substring(0, property);
    }

    private static final class Requirements {
        private final Set<String> states = new HashSet<>();
        private final Set<Processor> processors = new HashSet<>();
        private boolean height;
        private boolean worldSurface;
        private boolean seaLevel;
        private boolean replaceableQuery;
        private boolean loot;
        private boolean spawner;
        private boolean archaeology;
        private boolean entity;
    }

    private static final class Context {
        private final Mc263HardcodedStructureCarrier carrier;
        private final Clip clip;
        private final WorldAccess world;
        private final WorldgenRandom random;
        private SuccessorPayload successor;

        private Context(Mc263HardcodedStructureCarrier carrier, Clip clip, WorldAccess world,
                WorldgenRandom random) {
            this.carrier = carrier;
            this.clip = clip;
            this.world = world;
            this.random = random;
            this.successor = carrier.successor();
        }

        private String process(Processor processor, String state, BlockPos position) {
            if (processor == null) return state;
            if (processor == Processor.IGNORE_STRUCTURE_BLOCK) {
                return blockKey(state).equals("minecraft:structure_block") ? null : state;
            }
            if (processor == Processor.MONUMENT_FILL_KEEP) {
                String existing = requireState(world.blockState(position));
                String key = blockKey(existing);
                for (ProcessorDecision decision : carrier.processorDecisions()) {
                    if (decision.processor() == processor
                            && blockKey(decision.inputState()).equals(key)) return null;
                }
                return position.y() >= world.seaLevel()
                        && !key.equals("minecraft:water") ? "minecraft:air" : state;
            }
            int sample = random.next(24);
            for (ProcessorDecision decision : carrier.processorDecisions()) {
                if (decision.processor() != processor
                        || !blockKey(decision.inputState()).equals(blockKey(state))) continue;
                if (sample < decision.randomLowerInclusive()
                        || sample >= decision.randomUpperExclusive()) continue;
                return decision.discard() ? null : decision.outputState();
            }
            throw new IllegalStateException("processor sample had no exact carried decision: "
                    + processor + " sample=" + sample + "/" + FLOAT_SAMPLE_SPACE);
        }
    }
}
