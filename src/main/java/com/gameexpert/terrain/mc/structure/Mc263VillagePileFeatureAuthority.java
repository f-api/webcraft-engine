package com.gameexpert.terrain.mc.structure;

import com.gameexpert.terrain.mc.ore.Mc263WorldgenRandomSource;
import com.gameexpert.terrain.mc.structure.Mc263VillageSettlement.EffectKind;
import com.gameexpert.terrain.mc.structure.Mc263VillageSettlement.ExecutionResult;
import com.gameexpert.terrain.mc.structure.Mc263VillageSettlement.FeaturePlacement;
import com.gameexpert.terrain.mc.structure.Mc263VillageSettlement.Operation;
import com.gameexpert.terrain.mc.structure.Mc263VillageSettlement.Position;
import com.gameexpert.terrain.mc.structure.Mc263VillageSettlement.Write;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Pinned Minecraft 26.3-snapshot-7 configured-feature authority for the five Village block piles.
 */
public final class Mc263VillagePileFeatureAuthority {
    public static final String SOURCE_VERSION = "26.3-snapshot-7";
    public static final int MIN_BUILD_Y = -64,
            MAX_BUILD_Y = 320,
            WRITE_FLAGS = 260,
            WRITABLE_CHUNK_RADIUS = 1;
    private static final int MAX_HORIZONTAL_RADIUS = 3;
    private static final List<String> FEATURE_KEYS =
            List.of(
                    "minecraft:pile_hay",
                    "minecraft:pile_melon",
                    "minecraft:pile_snow",
                    "minecraft:pile_ice",
                    "minecraft:pile_pumpkin");
    private static final Set<String>
            ROTATIONS = Set.of("NONE", "CLOCKWISE_90", "CLOCKWISE_180", "COUNTERCLOCKWISE_90"),
            PROJECTIONS = Set.of("rigid", "terrain_matching");

    private Mc263VillagePileFeatureAuthority() {
        throw new AssertionError("no instances");
    }

    public enum Query {
        EMPTY_BLOCK,
        SUPPORT_BLOCK_STATE,
        STURDY_UP
    }

    /** Capability methods are pure; runtime methods run only after complete preflight. */
    public interface Environment {
        boolean supportsExactState(String exactState);

        boolean supportsQuery(Query query);

        boolean supportsWriteFlags(int flags);

        boolean supportsWritableChunkRadius(int radius);

        int minBuildY();

        int maxBuildY();

        boolean isEmptyBlock(Position position);

        String supportBlockState(Position supportPosition);

        boolean isFaceSturdyUp(Position supportPosition, String exactState);

        boolean setBlock(Position position, String exactState, int flags);
    }

    public static List<String> featureKeys() {
        return FEATURE_KEYS;
    }

    /**
     * Exhaustive authenticated output exact-states of the five Village pile configured features,
     * in feature order and, within a feature, in the provider receipt's draw order.
     *
     * <p>These are the states {@link #execute} may write, so they are exactly the states the
     * runtime carrier must be able to represent. The list is read from the pinned village
     * production authority ({@code village-production-authority-v1.txt}, provider receipts
     * {@code V|…}) — never from a hand list — so the representability surface is derived from the
     * same grammar the executor draws from.</p>
     */
    public static List<String> authenticatedOutputExactStates() {
        ArrayList<String> states = new ArrayList<>();
        for (String featureKey : FEATURE_KEYS) {
            for (String exactState : requireSpec(featureKey).outputStates) {
                if (!states.contains(exactState)) states.add(exactState);
            }
        }
        return List.copyOf(states);
    }

    public static ExecutionResult execute(
            FeaturePlacement placement, Mc263WorldgenRandomSource random, Environment environment) {
        Objects.requireNonNull(placement, "Village pile placement");
        return execute(
                placement.featureKey(),
                placement.configuredTarget(),
                placement.origin(),
                placement.rotation(),
                placement.projection(),
                random,
                environment);
    }

    public static ExecutionResult execute(
            String featureKey,
            String configuredTarget,
            Position origin,
            String rotation,
            String projection,
            Mc263WorldgenRandomSource random,
            Environment environment) {
        Objects.requireNonNull(featureKey, "Village pile feature key");
        Objects.requireNonNull(configuredTarget, "Village pile configured target");
        Objects.requireNonNull(origin, "Village pile origin");
        Objects.requireNonNull(rotation, "Village pile rotation");
        Objects.requireNonNull(projection, "Village pile projection");
        Objects.requireNonNull(random, "Village pile random");
        Objects.requireNonNull(environment, "Village pile environment");
        Spec spec = requireSpec(featureKey);
        require(ROTATIONS.contains(rotation), "unknown Village pile rotation: " + rotation);
        require(PROJECTIONS.contains(projection), "unknown Village pile projection: " + projection);
        Mc263VillageProductionAuthority.Feature authenticated =
                Mc263VillageProductionAuthority.pinned().requireFeature(featureKey);
        require(
                featureKey.equals(authenticated.registryKey()),
                "Village pile authenticated registry-key drift: " + featureKey);
        require(
                spec.configuredTarget.equals(authenticated.configuredTarget()),
                "Village pile authenticated configured-target drift: " + featureKey);
        require(
                spec.configuredTarget.equals(configuredTarget),
                "Village pile configured-target mismatch: " + featureKey);
        for (String exactState : spec.outputStates)
            require(
                    environment.supportsExactState(exactState),
                    "Village pile exact-state capability absent: " + exactState);
        for (Query query : Query.values())
            require(
                    environment.supportsQuery(query),
                    "Village pile query capability absent: " + query);
        require(
                environment.supportsWriteFlags(WRITE_FLAGS),
                "Village pile write-flags capability absent");
        require(
                environment.supportsWritableChunkRadius(WRITABLE_CHUNK_RADIUS),
                "Village pile writable-radius capability absent");
        require(environment.minBuildY() == MIN_BUILD_Y, "Village pile minimum build height drift");
        require(environment.maxBuildY() == MAX_BUILD_Y, "Village pile maximum build height drift");
        int sourceChunkX = Math.floorDiv(origin.x(), 16),
                sourceChunkZ = Math.floorDiv(origin.z(), 16);
        validateHorizontalEnvelope(origin, sourceChunkX, sourceChunkZ);
        if (origin.y() < MIN_BUILD_Y + 5) return ExecutionResult.empty();
        require(
                origin.y() < MAX_BUILD_Y - 1,
                "Village pile origin exceeds writable build-height clip");
        int radiusX = 2 + random.nextInt(2), radiusZ = 2 + random.nextInt(2);
        int minX = checkedAdd(origin.x(), -radiusX, "x minimum"),
                maxX = checkedAdd(origin.x(), radiusX, "x maximum"),
                minZ = checkedAdd(origin.z(), -radiusZ, "z minimum"),
                maxZ = checkedAdd(origin.z(), radiusZ, "z maximum"),
                maxY = checkedAdd(origin.y(), 1, "y maximum");
        ArrayList<Operation> operations = new ArrayList<>();
        ArrayList<Write> writes = new ArrayList<>();
        for (int z = minZ; z <= maxZ; z++) {
            for (int y = origin.y(); y <= maxY; y++) {
                for (int x = minX; x <= maxX; x++) {
                    int dx = origin.x() - x, dz = origin.z() - z;
                    float threshold = random.nextFloat() * 10.0F - random.nextFloat() * 6.0F;
                    boolean selected = (float) (dx * dx + dz * dz) <= threshold;
                    if (!selected) selected = (double) random.nextFloat() < 0.031D;
                    if (!selected) continue;
                    Position position = new Position(x, y, z);
                    validateRuntimePosition(position, sourceChunkX, sourceChunkZ);
                    boolean empty = environment.isEmptyBlock(position);
                    operations.add(
                            new Operation(
                                    operations.size(), EffectKind.QUERY, position, "isEmptyBlock"));
                    if (!empty) continue;
                    Position support = new Position(x, y - 1, z);
                    validateRuntimePosition(support, sourceChunkX, sourceChunkZ);
                    String supportState = Objects.requireNonNull(
                            environment.supportBlockState(support),
                            "Village pile support block state");
                    operations.add(
                            new Operation(
                                    operations.size(),
                                    EffectKind.QUERY,
                                    support,
                                    "getBlockState|isFaceSturdy|UP"));
                    boolean mayPlace = blockKey(supportState).equals("minecraft:dirt_path")
                            ? random.nextBoolean()
                            : environment.isFaceSturdyUp(support, supportState);
                    if (!mayPlace) continue;
                    String exactState = spec.stateForPlacement(random);
                    boolean successful = environment.setBlock(position, exactState, WRITE_FLAGS);
                    operations.add(
                            new Operation(
                                    operations.size(),
                                    EffectKind.WRITE,
                                    position,
                                    "setBlock|" + exactState + '|' + WRITE_FLAGS));
                    writes.add(new Write(position, exactState, WRITE_FLAGS, successful));
                }
            }
        }
        return new ExecutionResult(operations, writes, List.of(), List.of(), List.of(), List.of());
    }

    private static void validateHorizontalEnvelope(
            Position origin, int sourceChunkX, int sourceChunkZ) {
        int minX = checkedAdd(origin.x(), -MAX_HORIZONTAL_RADIUS, "preflight x minimum"),
                maxX = checkedAdd(origin.x(), MAX_HORIZONTAL_RADIUS, "preflight x maximum"),
                minZ = checkedAdd(origin.z(), -MAX_HORIZONTAL_RADIUS, "preflight z minimum"),
                maxZ = checkedAdd(origin.z(), MAX_HORIZONTAL_RADIUS, "preflight z maximum");
        validateChunkRadius(minX, minZ, sourceChunkX, sourceChunkZ);
        validateChunkRadius(minX, maxZ, sourceChunkX, sourceChunkZ);
        validateChunkRadius(maxX, minZ, sourceChunkX, sourceChunkZ);
        validateChunkRadius(maxX, maxZ, sourceChunkX, sourceChunkZ);
    }

    private static void validateRuntimePosition(
            Position position, int sourceChunkX, int sourceChunkZ) {
        require(
                position.y() >= MIN_BUILD_Y && position.y() < MAX_BUILD_Y,
                "Village pile runtime position outside build-height clip");
        validateChunkRadius(position.x(), position.z(), sourceChunkX, sourceChunkZ);
    }

    private static void validateChunkRadius(int x, int z, int sourceChunkX, int sourceChunkZ) {
        long dx = (long) Math.floorDiv(x, 16) - sourceChunkX,
                dz = (long) Math.floorDiv(z, 16) - sourceChunkZ;
        require(
                Math.abs(dx) <= WRITABLE_CHUNK_RADIUS && Math.abs(dz) <= WRITABLE_CHUNK_RADIUS,
                "Village pile position exceeds writable chunk radius");
    }

    private static int checkedAdd(int value, int delta, String label) {
        try {
            return Math.addExact(value, delta);
        } catch (ArithmeticException overflow) {
            throw new IllegalArgumentException(
                    "Village pile coordinate overflow: " + label, overflow);
        }
    }

    private static String blockKey(String state) {
        int property = state.indexOf('[');
        return property < 0 ? state : state.substring(0, property);
    }

    private static Spec requireSpec(String featureKey) {
        require(FEATURE_KEYS.contains(featureKey),
                "unknown Village pile configured feature: " + featureKey);
        Mc263VillageProductionAuthority.Corpus authority =
                Mc263VillageProductionAuthority.pinned();
        Mc263VillageProductionAuthority.Feature feature = authority.requireFeature(featureKey);
        Mc263VillageProductionAuthority.ProviderReceipt receipt =
                authority.requireProviderReceipt(featureKey);
        require(receipt.featureOrdinal() == feature.ordinal(),
                "Village pile provider feature ordinal drift: " + featureKey);
        return new Spec(feature.configuredTarget(), receipt.orderedExhaustiveStates(),
                receipt.drawBound(), receipt.drawCount());
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }

    private static final class Spec {
        private final String configuredTarget;
        private final List<String> outputStates;
        private final int drawBound;
        private final int drawCount;

        private Spec(String configuredTarget, List<String> outputStates, int drawBound,
                int drawCount) {
            this.configuredTarget = configuredTarget;
            this.outputStates = List.copyOf(outputStates);
            this.drawBound = drawBound;
            this.drawCount = drawCount;
        }

        private String stateForPlacement(Mc263WorldgenRandomSource random) {
            if (drawCount == 0) return outputStates.getFirst();
            require(drawCount == 1 && drawBound == outputStates.size(),
                    "Village pile provider receipt is not executable");
            return outputStates.get(random.nextInt(drawBound));
        }
    }
}
