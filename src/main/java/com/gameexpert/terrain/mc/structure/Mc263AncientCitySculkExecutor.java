package com.gameexpert.terrain.mc.structure;

import com.gameexpert.terrain.mc.feature.Mc263SculkPatchFeature;
import com.gameexpert.terrain.mc.ore.Mc263WorldgenRandomSource;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

/**
 * Atomic execution leaf for the authenticated Ancient City configured sculk feature.
 *
 * <p>The leaf executes {@code minecraft:sculk_patch_ancient_city} exactly as the pinned
 * 26.3-snapshot-7 server does during Ancient City piece placement: the
 * {@code minecraft:sculk_patch} feature with its {@code SculkSpreader} charge/decay/growth
 * semantics, followed by the {@code minecraft:overlay} children — the {@code random_chance}
 * gated sculk-catalyst simple block and the {@code count}/{@code offset} gated sculk-shrieker
 * simple block. Charge, spread and catalyst semantics are delegated to the pinned
 * {@link Mc263SculkPatchFeature} leaf; only the authenticated overlay shrieker layer, the
 * capability preflight and the receipt boundary belong to this executor.</p>
 *
 * <p>The overlay draw order implemented here — catalyst chance, then shrieker count, then three
 * offsets per counted position, with both block predicates consuming no randomness — is the order
 * the authenticated E3I9 natural-feature transcript records for the ten Ancient City
 * {@code minecraft:sculk_patch_ancient_city} feature pieces.</p>
 *
 * <p>Every constant is bound to {@link Mc263AncientCityProductionAuthority}'s authenticated
 * configured-feature definition rather than transcribed here, and the returned receipt is the
 * commit/rollback boundary: it records the ordered world queries, the ordered mutations and the
 * successful write positions in encounter order so a caller can compare them against the
 * authenticated natural-feature transcript.</p>
 */
public final class Mc263AncientCitySculkExecutor {
    public static final String FEATURE_KEY = "minecraft:sculk_patch_ancient_city";
    /** {@code SimpleBlockFeature} writes through {@code setBlock(state, 2)}. */
    public static final int SIMPLE_BLOCK_FLAGS = Mc263SculkPatchFeature.SPREAD_FLAGS;

    private static final Mc263AncientCityGrammar AUTHORITY =
            Mc263AncientCityProductionAuthority.load();
    private static final Mc263AncientCityGrammar.ConfiguredFeature CONFIGURED_FEATURE =
            requireAuthenticatedFeature(AUTHORITY.configuredFeature());

    private Mc263AncientCitySculkExecutor() { }

    /** The authenticated configured feature this leaf executes. */
    static Mc263AncientCityGrammar.ConfiguredFeature configuredFeature() {
        return CONFIGURED_FEATURE;
    }

    public static Receipt execute(Request request, Environment environment) {
        Objects.requireNonNull(request, "Ancient City sculk request");
        Objects.requireNonNull(environment, "Ancient City sculk environment");
        preflight(environment);

        Mc263SculkPatchFeature.BlockPos origin =
                new Mc263SculkPatchFeature.BlockPos(request.originX, request.originY,
                        request.originZ);
        RecordingWorld world = new RecordingWorld(environment, request.sourceChunkX,
                request.sourceChunkZ);
        Mc263SculkPatchFeature.ConfiguredResult patch =
                Mc263SculkPatchFeature.placeConfigured(request.random, origin, world);

        ArrayList<ShriekerAttempt> shriekers = new ArrayList<>();
        int shriekerCount = -1;
        if (patch.patchPlaced()) {
            Mc263AncientCityGrammar.ShriekerFeature spec =
                    CONFIGURED_FEATURE.sequence().overlay().shrieker();
            shriekerCount = sample(request.random, spec.count());
            for (int index = 0; index < shriekerCount; index++) {
                shriekers.add(placeShrieker(spec, request.random, origin, world));
            }
        }
        return new Receipt(origin, request.sourceChunkX, request.sourceChunkZ, patch,
                shriekerCount, shriekers, world.queries, world.mutations,
                world.successfulWritePositions);
    }

    /**
     * One {@code minecraft:offset} placement followed by the {@code all_of} block-predicate
     * filter: the target must match the {@code #minecraft:air} tag and the block one below it
     * must present a sturdy upward face. The lazy placed-feature stream evaluates offset,
     * predicate and placement for one position before the next position is offset.
     */
    private static ShriekerAttempt placeShrieker(Mc263AncientCityGrammar.ShriekerFeature spec,
            Mc263WorldgenRandomSource random, Mc263SculkPatchFeature.BlockPos origin,
            RecordingWorld world) {
        int offsetX = sample(random, spec.offset().x());
        int offsetY = sample(random, spec.offset().y());
        int offsetZ = sample(random, spec.offset().z());
        Mc263SculkPatchFeature.BlockPos target =
                new Mc263SculkPatchFeature.BlockPos(origin.x() + offsetX, origin.y() + offsetY,
                        origin.z() + offsetZ);
        boolean air = world.blockState(target.x(), target.y(), target.z()).air();
        boolean sturdy = false;
        boolean placed = false;
        if (air) {
            sturdy = world.isFaceSturdy(target.x() + spec.supportOffset().x(),
                    target.y() + spec.supportOffset().y(),
                    target.z() + spec.supportOffset().z(), Mc263SculkPatchFeature.Direction.UP);
            if (sturdy) {
                world.trySetBlockState(target.x(), target.y(), target.z(),
                        shriekerState(spec), SIMPLE_BLOCK_FLAGS);
                placed = true;
            }
        }
        return new ShriekerAttempt(offsetX, offsetY, offsetZ, target, air, sturdy, placed);
    }

    private static Mc263SculkPatchFeature.State shriekerState(
            Mc263AncientCityGrammar.ShriekerFeature spec) {
        return Mc263SculkPatchFeature.State.shrieker(
                Boolean.parseBoolean(spec.state().waterlogged()));
    }

    /** Exact {@code UniformInt.sample}: {@code nextInt(max - min + 1) + min}. */
    private static int sample(Mc263WorldgenRandomSource random,
            Mc263AncientCityGrammar.UniformInt uniform) {
        return random.nextInt(uniform.maxInclusive() - uniform.minInclusive() + 1)
                + uniform.minInclusive();
    }

    private static void preflight(Environment environment) {
        Mc263SculkPatchFeature.preflight(environment::supportsExactState,
                environment.supportsPostprocessing(), environment.supportsSculkPayloads());
        Mc263AncientCityGrammar.ShriekerFeature shrieker =
                CONFIGURED_FEATURE.sequence().overlay().shrieker();
        String exact = shrieker.state().id() + "[can_summon=" + shrieker.state().canSummon()
                + ",shrieking=" + shrieker.state().shrieking()
                + ",waterlogged=" + shrieker.state().waterlogged() + "]";
        require(environment.supportsExactState(exact),
                "Ancient City sculk shrieker capability absent: " + exact);
        require(environment.generationDepth() > 0 && environment.minGenerationY()
                        <= Mc263SculkPatchFeature.WORLD_MAX_Y,
                "Ancient City sculk generation bounds are invalid");
    }

    /**
     * Binds every executed constant to the authenticated configured feature and refuses to run
     * when the pinned sculk leaf and the authority disagree.
     */
    static Mc263AncientCityGrammar.ConfiguredFeature requireAuthenticatedFeature(
            Mc263AncientCityGrammar.ConfiguredFeature feature) {
        require(feature != null && FEATURE_KEY.equals(feature.registryKey()),
                "Ancient City configured sculk feature identity drift");
        Mc263AncientCityGrammar.SculkPatch patch = feature.sequence().patch();
        require(patch.chargeCount() == Mc263SculkPatchFeature.CHARGE_COUNT
                        && patch.amountPerCharge() == Mc263SculkPatchFeature.AMOUNT_PER_CHARGE
                        && patch.spreadAttempts() == Mc263SculkPatchFeature.SPREAD_ATTEMPTS
                        && patch.growthRounds() == Mc263SculkPatchFeature.GROWTH_ROUNDS
                        && patch.spreadRounds() == Mc263SculkPatchFeature.SPREAD_ROUNDS,
                "Ancient City sculk patch configuration drift");
        Mc263AncientCityGrammar.CatalystFeature catalyst = feature.sequence().overlay().catalyst();
        require(Mc263SculkPatchFeature.CATALYST.equals(catalyst.state())
                        && Double.compare(catalyst.chance(), 0.5D) == 0
                        && catalyst.supportOffset().y() == -1
                        && catalyst.supportOffset().x() == 0
                        && catalyst.supportOffset().z() == 0,
                "Ancient City sculk catalyst overlay drift");
        Mc263AncientCityGrammar.ShriekerFeature shrieker = feature.sequence().overlay().shrieker();
        require(Mc263SculkPatchFeature.SHRIEKER.equals(shrieker.state().id())
                        && "minecraft:air".equals(shrieker.requiredTag())
                        && shrieker.supportOffset().x() == 0
                        && shrieker.supportOffset().y() == -1
                        && shrieker.supportOffset().z() == 0,
                "Ancient City sculk shrieker overlay drift");
        require(feature.transitiveFeaturesInOrder().size() == 4,
                "Ancient City sculk transitive feature cardinality drift");
        return feature;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }

    /**
     * Live carrier surface. The executor deliberately consumes the same exact sculk-patch world
     * access the FEATURES adapter already publishes so no lookalike world contract is introduced.
     */
    public interface Environment extends Mc263SculkPatchFeature.WorldAccess {
        boolean supportsExactState(String exactState);
        boolean supportsPostprocessing();
        boolean supportsSculkPayloads();
    }

    public enum QueryKind { BLOCK_STATE, FLUID_STATE, COLLISION_SHAPE, FACE_STURDY, ATTACH,
        SECTION_INHIBITOR }

    /** {@code setBlock(state, 3)} is the official {@code setBlockAndUpdate} shorthand. */
    public enum MutationKind { SET_BLOCK, SET_BLOCK_AND_UPDATE, MARK_FOR_POSTPROCESSING }

    public enum DestinationRelation { SOURCE, FOREIGN }

    private static final class RecordingWorld implements Mc263SculkPatchFeature.WorldAccess {
        private final Environment delegate;
        private final int sourceChunkX;
        private final int sourceChunkZ;
        private final ArrayList<Query> queries = new ArrayList<>();
        private final ArrayList<Mutation> mutations = new ArrayList<>();
        private final LinkedHashSet<Position> successfulWritePositions = new LinkedHashSet<>();

        private RecordingWorld(Environment delegate, int sourceChunkX, int sourceChunkZ) {
            this.delegate = delegate;
            this.sourceChunkX = sourceChunkX;
            this.sourceChunkZ = sourceChunkZ;
        }

        private void query(QueryKind kind, int x, int y, int z) {
            queries.add(new Query(queries.size(), kind, new Position(x, y, z)));
        }

        private DestinationRelation relation(int x, int y, int z) {
            return (x >> 4) == sourceChunkX && (z >> 4) == sourceChunkZ
                    ? DestinationRelation.SOURCE : DestinationRelation.FOREIGN;
        }

        @Override public int minGenerationY() { return delegate.minGenerationY(); }

        @Override public int generationDepth() { return delegate.generationDepth(); }

        @Override public String biomeKey(int x, int y, int z) {
            return delegate.biomeKey(x, y, z);
        }

        @Override public Mc263SculkPatchFeature.State blockState(int x, int y, int z) {
            query(QueryKind.BLOCK_STATE, x, y, z);
            return delegate.blockState(x, y, z);
        }

        @Override public Mc263SculkPatchFeature.FluidFact fluidState(int x, int y, int z) {
            query(QueryKind.FLUID_STATE, x, y, z);
            return delegate.fluidState(x, y, z);
        }

        @Override public boolean isCollisionShapeFullBlock(int x, int y, int z) {
            query(QueryKind.COLLISION_SHAPE, x, y, z);
            return delegate.isCollisionShapeFullBlock(x, y, z);
        }

        @Override public boolean isFaceSturdy(int x, int y, int z,
                Mc263SculkPatchFeature.Direction face) {
            query(QueryKind.FACE_STURDY, x, y, z);
            return delegate.isFaceSturdy(x, y, z, face);
        }

        @Override public boolean canAttachTo(int x, int y, int z,
                Mc263SculkPatchFeature.Direction face) {
            query(QueryKind.ATTACH, x, y, z);
            return delegate.canAttachTo(x, y, z, face);
        }

        @Override public boolean sectionMayContainGrowthInhibitor(int sectionX, int sectionY,
                int sectionZ) {
            query(QueryKind.SECTION_INHIBITOR, sectionX, sectionY, sectionZ);
            return delegate.sectionMayContainGrowthInhibitor(sectionX, sectionY, sectionZ);
        }

        @Override public boolean trySetBlockState(int x, int y, int z,
                Mc263SculkPatchFeature.State state, int flags) {
            boolean written = delegate.trySetBlockState(x, y, z, state, flags);
            MutationKind kind = flags == Mc263SculkPatchFeature.DIRECT_FLAGS
                    ? MutationKind.SET_BLOCK_AND_UPDATE : MutationKind.SET_BLOCK;
            Position position = new Position(x, y, z);
            mutations.add(new Mutation(mutations.size(), kind, position, state, flags, written,
                    relation(x, y, z)));
            if (written) successfulWritePositions.add(position);
            return written;
        }

        @Override public boolean tryMarkPosForPostProcessing(int x, int y, int z) {
            boolean marked = delegate.tryMarkPosForPostProcessing(x, y, z);
            mutations.add(new Mutation(mutations.size(), MutationKind.MARK_FOR_POSTPROCESSING,
                    new Position(x, y, z), null, 0, marked, relation(x, y, z)));
            return marked;
        }

        @Override public void pushEntitiesUp(int x, int y, int z,
                Mc263SculkPatchFeature.State oldState, Mc263SculkPatchFeature.State newState) {
            delegate.pushEntitiesUp(x, y, z, oldState, newState);
        }

        @Override public void playSculkSpreadSound(int x, int y, int z) {
            delegate.playSculkSpreadSound(x, y, z);
        }

        @Override public void playGrowthPlaceSound(int x, int y, int z,
                Mc263SculkPatchFeature.State state) {
            delegate.playGrowthPlaceSound(x, y, z, state);
        }

        @Override public void emitSculkChargeEvent(int x, int y, int z, int data) {
            delegate.emitSculkChargeEvent(x, y, z, data);
        }
    }

    public static final class Request {
        private final int originX;
        private final int originY;
        private final int originZ;
        private final int sourceChunkX;
        private final int sourceChunkZ;
        private final Mc263WorldgenRandomSource random;

        public Request(int originX, int originY, int originZ, int sourceChunkX, int sourceChunkZ,
                Mc263WorldgenRandomSource random) {
            this.originX = originX;
            this.originY = originY;
            this.originZ = originZ;
            this.sourceChunkX = sourceChunkX;
            this.sourceChunkZ = sourceChunkZ;
            this.random = Objects.requireNonNull(random, "Ancient City sculk random");
        }
    }

    public static final class Position {
        private final int x;
        private final int y;
        private final int z;

        public Position(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        public int x() { return x; }
        public int y() { return y; }
        public int z() { return z; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Position position)) return false;
            return x == position.x && y == position.y && z == position.z;
        }

        @Override public int hashCode() { return (y + z * 31) * 31 + x; }

        @Override public String toString() { return "[" + x + ", " + y + ", " + z + "]"; }
    }

    public static final class Query {
        private final int ordinal;
        private final QueryKind kind;
        private final Position position;

        private Query(int ordinal, QueryKind kind, Position position) {
            this.ordinal = ordinal;
            this.kind = kind;
            this.position = position;
        }

        public int ordinal() { return ordinal; }
        public QueryKind kind() { return kind; }
        public Position position() { return position; }
    }

    public static final class Mutation {
        private final int ordinal;
        private final MutationKind kind;
        private final Position position;
        private final Mc263SculkPatchFeature.State state;
        private final int flags;
        private final boolean applied;
        private final DestinationRelation destinationRelation;

        private Mutation(int ordinal, MutationKind kind, Position position,
                Mc263SculkPatchFeature.State state, int flags, boolean applied,
                DestinationRelation destinationRelation) {
            this.ordinal = ordinal;
            this.kind = kind;
            this.position = position;
            this.state = state;
            this.flags = flags;
            this.applied = applied;
            this.destinationRelation = destinationRelation;
        }

        public int ordinal() { return ordinal; }
        public MutationKind kind() { return kind; }
        public Position position() { return position; }
        public Mc263SculkPatchFeature.State state() { return state; }
        public int flags() { return flags; }
        public boolean applied() { return applied; }
        public DestinationRelation destinationRelation() { return destinationRelation; }
    }

    public static final class ShriekerAttempt {
        private final int offsetX;
        private final int offsetY;
        private final int offsetZ;
        private final Mc263SculkPatchFeature.BlockPos target;
        private final boolean airPredicatePassed;
        private final boolean supportPredicatePassed;
        private final boolean placed;

        private ShriekerAttempt(int offsetX, int offsetY, int offsetZ,
                Mc263SculkPatchFeature.BlockPos target, boolean airPredicatePassed,
                boolean supportPredicatePassed, boolean placed) {
            this.offsetX = offsetX;
            this.offsetY = offsetY;
            this.offsetZ = offsetZ;
            this.target = target;
            this.airPredicatePassed = airPredicatePassed;
            this.supportPredicatePassed = supportPredicatePassed;
            this.placed = placed;
        }

        public int offsetX() { return offsetX; }
        public int offsetY() { return offsetY; }
        public int offsetZ() { return offsetZ; }
        public Mc263SculkPatchFeature.BlockPos target() { return target; }
        public boolean airPredicatePassed() { return airPredicatePassed; }
        public boolean supportPredicatePassed() { return supportPredicatePassed; }
        public boolean placed() { return placed; }
    }

    public static final class Receipt {
        private final Mc263SculkPatchFeature.BlockPos origin;
        private final int sourceChunkX;
        private final int sourceChunkZ;
        private final Mc263SculkPatchFeature.ConfiguredResult patch;
        private final int shriekerCountDraw;
        private final List<ShriekerAttempt> shriekerAttempts;
        private final List<Query> queriesInOrder;
        private final List<Mutation> mutationsInOrder;
        private final List<Position> successfulWritePositionsInEncounterOrder;

        private Receipt(Mc263SculkPatchFeature.BlockPos origin, int sourceChunkX,
                int sourceChunkZ, Mc263SculkPatchFeature.ConfiguredResult patch,
                int shriekerCountDraw, List<ShriekerAttempt> shriekerAttempts,
                List<Query> queriesInOrder, List<Mutation> mutationsInOrder,
                Iterable<Position> successfulWritePositions) {
            this.origin = origin;
            this.sourceChunkX = sourceChunkX;
            this.sourceChunkZ = sourceChunkZ;
            this.patch = patch;
            this.shriekerCountDraw = shriekerCountDraw;
            this.shriekerAttempts = List.copyOf(shriekerAttempts);
            this.queriesInOrder = List.copyOf(queriesInOrder);
            this.mutationsInOrder = List.copyOf(mutationsInOrder);
            ArrayList<Position> ordered = new ArrayList<>();
            for (Position position : successfulWritePositions) ordered.add(position);
            this.successfulWritePositionsInEncounterOrder = List.copyOf(ordered);
        }

        public Mc263SculkPatchFeature.BlockPos origin() { return origin; }
        public int sourceChunkX() { return sourceChunkX; }
        public int sourceChunkZ() { return sourceChunkZ; }
        public boolean patchPlaced() { return patch.patchPlaced(); }
        public boolean sequencePlaced() { return patch.sequencePlaced(); }
        public boolean catalystChancePassed() { return patch.catalystChancePassed(); }
        public boolean catalystPredicatePassed() { return patch.catalystPredicatePassed(); }
        /** {@code -1} when the aborted patch prevented the overlay from drawing a count. */
        public int shriekerCountDraw() { return shriekerCountDraw; }
        public List<ShriekerAttempt> shriekerAttempts() { return shriekerAttempts; }
        public List<Query> queriesInOrder() { return queriesInOrder; }
        public List<Mutation> mutationsInOrder() { return mutationsInOrder; }
        public List<Position> successfulWritePositionsInEncounterOrder() {
            return successfulWritePositionsInEncounterOrder;
        }
    }
}
