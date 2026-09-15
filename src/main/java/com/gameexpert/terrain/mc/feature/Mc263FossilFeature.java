package com.gameexpert.terrain.mc.feature;

import com.gameexpert.terrain.mc.McRandom;
import com.gameexpert.terrain.mc.ore.Mc263DecorationRandom;
import com.gameexpert.terrain.mc.ore.Mc263DecorationRandom.WorldgenRandom;
import java.util.function.Predicate;

/**
 * Inactive Minecraft Java 26.3-snapshot-7 fossil leaf with a strict originality boundary.
 *
 * <p>The placed modifiers, feature selection, envelope, burial, empty-corner gate and accepted
 * official random suffix are exact. Official structure identifiers, cells and NBT are deliberately
 * absent. A domain-separated coordinate grammar supplies an original connected skeleton after the
 * official processor draw budget has been consumed. The two traces keep those claims separate.</p>
 */
public final class Mc263FossilFeature {
    public static final int UNDERGROUND_STRUCTURES_STEP = 3;
    public static final int UPPER_GLOBAL_INDEX = 0;
    public static final int LOWER_GLOBAL_INDEX = 1;
    public static final String FOSSIL_UPPER = "minecraft:fossil_upper";
    public static final String FOSSIL_LOWER = "minecraft:fossil_lower";

    public static final String INNER_SERVER_SHA1 =
            "2f1ef79f3cad10138ad18da45b265fe656624026";
    public static final String FOSSIL_FEATURE_CLASS_SHA256 =
            "6109e27b82773bda8243575df5a9b15f17fff24a99a58b62d20c80e60d46171f";
    public static final String FEATURE_PLACER_CLASS_SHA256 =
            "1d3e9c087bf63463503ba2fb55893053a3e0da6cd38fd3d6302afb02d9be437f";
    public static final String STRUCTURE_TEMPLATE_CLASS_SHA256 =
            "6730a621dd6b800a769e2c2a5bec797b83c9fd90cf8448245940b7b546234e67";
    public static final String BLOCK_ROT_PROCESSOR_CLASS_SHA256 =
            "d4809afba3dd3208cfab6d5b040b46668b668c1033612cd248aa272cffd0dfd2";
    public static final String UPPER_PLACED_JSON_SHA256 =
            "3d0017c7f3d60495331bf1a41c2cdb809343591b2732b7105c23f238aef0018f";
    public static final String LOWER_PLACED_JSON_SHA256 =
            "bd0b190a07e40e97c8071609bb946f3af70d92af012d4bddafd081b9ed7d5bff";
    public static final String UPPER_CONFIGURED_JSON_SHA256 =
            "e88c4c52b25847be5a80b7251e0a28e098559ddd82c68eb9dfd2e866c0ef0f6f";
    public static final String LOWER_CONFIGURED_JSON_SHA256 =
            "c711529888500044c3a2a1f69f55d8eda6118898e8c795533618f42592f42478";
    public static final String BASE_PROCESSOR_JSON_SHA256 =
            "d615b6de54331f2dd4441acc9de18b75471a91cfb0e048b308e0ab25fc27dcf4";
    public static final String UPPER_OVERLAY_PROCESSOR_JSON_SHA256 =
            "92660081d1bb4ead2eb05343d26669db148fc84a37c26583c2fe296fca1233b7";
    public static final String LOWER_OVERLAY_PROCESSOR_JSON_SHA256 =
            "752afb4de563371b503a933e7b95f0793fe85c9378b104230526297c098bf4b3";
    public static final String PROTECTED_TAG_JSON_SHA256 =
            "a117e03f857eacf14ff5862ba6f885bb02b476a844b683c2be61211202a9a10d";
    public static final String SOURCE_AGGREGATE_SHA256 =
            "8dafd25679f880d8beae640595e7d59de6b7c04860ff3f0445641fbd2f6637af";
    /** SHA-256 of the documented metadata-only receipt encoded by {@link #metadataReceiptLine}. */
    public static final String TEMPLATE_METADATA_AGGREGATE_SHA256 =
            "2bb26fce1074d5a8af65ef95d6a2f3a3cd8f533ab0c2bd31dfb12b721c420817";

    private static final int RARITY_CHANCE = 64;
    private static final int MAX_EMPTY_CORNERS = 4;
    private static final int BOUNDING_MARGIN = 16;
    private static final int BURIAL_BASE = 15;
    private static final int BURIAL_VARIANCE = 10;
    private static final int MIN_BURIAL_ABOVE_BOTTOM = 10;
    private static final long ORIGINALITY_DOMAIN = 0x666f7373696c2633L;
    private static final long FNV_OFFSET = 0xcbf29ce484222325L;
    private static final long FNV_PRIME = 0x100000001b3L;
    private static final String BONE_X = "minecraft:bone_block[axis=x]";
    private static final String BONE_Y = "minecraft:bone_block[axis=y]";
    private static final String BONE_Z = "minecraft:bone_block[axis=z]";
    private static final String UPPER_OVERLAY = "minecraft:coal_ore";
    private static final String LOWER_OVERLAY = "minecraft:deepslate_diamond_ore";

    /** Pure exact owned-state closure for both fossil grammar variants. */
    public static void preflight(Predicate<String> supportsState) {
        Mc263LakeFeature.requireOutputs(supportsState, BONE_X, BONE_Y, BONE_Z,
                UPPER_OVERLAY, LOWER_OVERLAY);
    }

    private static final OfficialTraceSink NO_OFFICIAL_TRACE = new OfficialTraceSink() {
        @Override
        public void record(String phase, long... values) {
        }

        @Override
        public boolean enabled() {
            return false;
        }
    };
    private static final OriginalityTraceSink NO_ORIGINALITY_TRACE =
            new OriginalityTraceSink() {
                @Override
                public void record(String phase, long... values) {
                }

                @Override
                public boolean enabled() {
                    return false;
                }
            };

    private Mc263FossilFeature() {
    }

    /** Complete mutable FEATURES-region view. Biomes are queried at the candidate's exact Y. */
    public interface WorldAccess {
        int minGenerationY();

        int generationDepth();

        String biomeKey(int blockX, int blockY, int blockZ);

        int oceanFloorWg(int blockX, int blockZ);

        String blockState(int blockX, int blockY, int blockZ);

        boolean featuresCannotReplace(int blockX, int blockY, int blockZ);

        /** Atomically writes a bounded original cell and associates it with the stable owner. */
        boolean trySetOwnedBlockState(int blockX, int blockY, int blockZ, String state,
                long owner);
    }

    @FunctionalInterface
    public interface OfficialTraceSink {
        void record(String phase, long... values);

        default boolean enabled() {
            return true;
        }
    }

    @FunctionalInterface
    public interface OriginalityTraceSink {
        void record(String phase, long... values);

        default boolean enabled() {
            return true;
        }
    }

    public record BlockPos(int x, int y, int z) {
    }

    public record BoundingBox(int minX, int minY, int minZ,
                              int maxX, int maxY, int maxZ) {
        public BoundingBox {
            if (minX > maxX || minY > maxY || minZ > maxZ) {
                throw new IllegalArgumentException("invalid fossil bounding box");
            }
        }

        public boolean contains(int x, int y, int z) {
            return x >= minX && x <= maxX && y >= minY && y <= maxY
                    && z >= minZ && z <= maxZ;
        }
    }

    /** Metadata-only dimensions. No official structure cell can cross this API boundary. */
    public record TemplateEnvelope(int sizeX, int sizeY, int sizeZ) {
        public TemplateEnvelope {
            if (sizeX < 1 || sizeY < 1 || sizeZ < 1) {
                throw new IllegalArgumentException("invalid fossil envelope");
            }
        }
    }

    public record PlacementResult(long decorationSeed, long featureSeed, int globalIndex,
                                  int candidates, int configuredSuccesses,
                                  int attemptedWrites, int acceptedWrites,
                                  int ownedCells, long ownershipDigest) {
        public PlacementResult {
            validateCounts(candidates, configuredSuccesses, attemptedWrites,
                    acceptedWrites, ownedCells);
        }
    }

    public record PlacementCounts(int candidates, int configuredSuccesses,
                                  int attemptedWrites, int acceptedWrites,
                                  int ownedCells, long ownershipDigest) {
        public PlacementCounts {
            validateCounts(candidates, configuredSuccesses, attemptedWrites,
                    acceptedWrites, ownedCells);
        }
    }

    /**
     * {@code returned} reports completion of the original connected backbone. The official
     * boundary remains true once the pinned corner gate and template draw budget have completed,
     * even when a protected cell or rejected owned write stops that original backbone prefix.
     */
    public record ConfiguredResult(boolean returned,
                                   boolean officialTemplateBoundaryReached,
                                   boolean officialTemplateVoxelParity,
                                   int selector, int rotation,
                                   BlockPos target, BoundingBox bounds,
                                   int emptyCorners, int attemptedWrites,
                                   int acceptedWrites, int ownedCells,
                                   long ownershipDigest) {
        public ConfiguredResult {
            if (officialTemplateVoxelParity || selector < 0 || selector > 7
                    || rotation < 0 || rotation > 3 || emptyCorners < 0 || emptyCorners > 8
                    || attemptedWrites < 0 || acceptedWrites < 0
                    || acceptedWrites > attemptedWrites || ownedCells < 0
                    || ownedCells > acceptedWrites
                    || (returned && !officialTemplateBoundaryReached)
                    || (!officialTemplateBoundaryReached
                    && (attemptedWrites != 0 || acceptedWrites != 0
                    || ownedCells != 0 || ownershipDigest != FNV_OFFSET))) {
                throw new IllegalArgumentException("invalid fossil result");
            }
            if (target == null || bounds == null) {
                throw new IllegalArgumentException("fossil target and bounds are required");
            }
        }
    }

    /** Official template voxels are intentionally unavailable to the product. */
    public static boolean officialTemplateVoxelParity() {
        return false;
    }

    public static TemplateEnvelope templateEnvelope(int selector) {
        return metadata(selector).envelope();
    }

    public static PlacementResult place(long worldSeed, int sourceBlockX, int sourceBlockZ,
            String placedFeatureKey, WorldAccess world) {
        return place(worldSeed, sourceBlockX, sourceBlockZ, placedFeatureKey, world,
                NO_OFFICIAL_TRACE, NO_ORIGINALITY_TRACE);
    }

    public static PlacementResult place(long worldSeed, int sourceBlockX, int sourceBlockZ,
            String placedFeatureKey, WorldAccess world, OfficialTraceSink officialTrace,
            OriginalityTraceSink originalityTrace) {
        requireWorld(world);
        PlacedSpec spec = placedSpec(placedFeatureKey);
        Mc263DecorationRandom.FeatureRandom seeded = Mc263DecorationRandom.forFeature(worldSeed,
                sourceBlockX, sourceBlockZ, UNDERGROUND_STRUCTURES_STEP, spec.key());
        PlacementCounts counts = placeWithFeatureRandom(worldSeed, sourceBlockX, sourceBlockZ,
                spec.key(), world, seeded.random(), officialTrace, originalityTrace);
        return new PlacementResult(seeded.decorationSeed(), seeded.featureSeed(),
                seeded.globalIndex(), counts.candidates(), counts.configuredSuccesses(),
                counts.attemptedWrites(), counts.acceptedWrites(), counts.ownedCells(),
                counts.ownershipDigest());
    }

    /** Executes rarity, in-square, uniform height and exact 3-D biome filtering on one stream. */
    public static PlacementCounts placeWithFeatureRandom(long worldSeed,
            int sourceBlockX, int sourceBlockZ, String placedFeatureKey, WorldAccess world,
            WorldgenRandom random, OfficialTraceSink officialTrace,
            OriginalityTraceSink originalityTrace) {
        requireWorld(world);
        if (random == null) throw new IllegalArgumentException("random is required");
        OfficialTraceSink official = requireOfficialTrace(officialTrace);
        OriginalityTraceSink original = requireOriginalityTrace(originalityTrace);
        PlacedSpec spec = placedSpec(placedFeatureKey);
        verifyIndex(spec);

        float rarity = random.nextFloat();
        boolean selected = rarity < 1.0f / RARITY_CHANCE;
        if (official.enabled()) {
            official.record("rarity", Float.floatToRawIntBits(rarity), selected ? 1L : 0L);
        }
        if (!selected) return emptyCounts();

        int x = sourceBlockX + random.nextInt(16);
        int z = sourceBlockZ + random.nextInt(16);
        int y = spec.minY() + random.nextInt(spec.maxY() - spec.minY() + 1);
        if (official.enabled()) official.record("candidate", x, y, z);
        boolean biomeAccepted = biomeContains(world.biomeKey(x, y, z), spec.key());
        if (official.enabled()) {
            official.record("biome", x, y, z, biomeAccepted ? 1L : 0L);
        }
        if (!biomeAccepted) return new PlacementCounts(1, 0, 0, 0, 0, FNV_OFFSET);

        ConfiguredResult configured = placeConfigured(worldSeed, spec.key(),
                new BlockPos(x, y, z), world, random, official, original);
        if (official.enabled()) {
            official.record("placed_result", spec.globalIndex(),
                    configured.officialTemplateBoundaryReached() ? 1L : 0L);
        }
        return new PlacementCounts(1, configured.returned() ? 1 : 0,
                configured.attemptedWrites(), configured.acceptedWrites(),
                configured.ownedCells(), configured.ownershipDigest());
    }

    public static ConfiguredResult placeConfigured(long worldSeed, String placedFeatureKey,
            BlockPos origin, WorldAccess world, WorldgenRandom random) {
        return placeConfigured(worldSeed, placedFeatureKey, origin, world, random,
                NO_OFFICIAL_TRACE, NO_ORIGINALITY_TRACE);
    }

    /** Executes the configured feature through the metadata boundary and original grammar. */
    public static ConfiguredResult placeConfigured(long worldSeed, String placedFeatureKey,
            BlockPos origin, WorldAccess world, WorldgenRandom random,
            OfficialTraceSink officialTrace, OriginalityTraceSink originalityTrace) {
        requireWorld(world);
        if (origin == null || random == null) {
            throw new IllegalArgumentException("origin and random are required");
        }
        OfficialTraceSink official = requireOfficialTrace(officialTrace);
        OriginalityTraceSink original = requireOriginalityTrace(originalityTrace);
        PlacedSpec spec = placedSpec(placedFeatureKey);

        int rotation = random.nextInt(4);
        int selector = random.nextInt(8);
        OfficialTemplateMetadata sealed = metadata(selector);
        TemplateEnvelope envelope = sealed.envelope();
        int rotatedX = swapsHorizontalAxes(rotation) ? envelope.sizeZ() : envelope.sizeX();
        int rotatedZ = swapsHorizontalAxes(rotation) ? envelope.sizeX() : envelope.sizeZ();
        int lowX = origin.x() - rotatedX / 2;
        int lowZ = origin.z() - rotatedZ / 2;
        if (official.enabled()) {
            official.record("selection", rotation, selector);
            official.record("envelope", envelope.sizeX(), envelope.sizeY(), envelope.sizeZ(),
                    rotatedX, rotatedZ, lowX, lowZ);
        }

        int lowestSurface = origin.y();
        for (int dx = 0; dx < rotatedX; dx++) {
            for (int dz = 0; dz < rotatedZ; dz++) {
                int surface = world.oceanFloorWg(lowX + dx, lowZ + dz);
                if (surface < lowestSurface) lowestSurface = surface;
                if (official.enabled()) {
                    official.record("surface", dx, dz, surface, lowestSurface);
                }
            }
        }
        int depthDraw = random.nextInt(BURIAL_VARIANCE);
        int unclampedY = lowestSurface - BURIAL_BASE - depthDraw;
        int targetY = Math.max(unclampedY,
                world.minGenerationY() + MIN_BURIAL_ABOVE_BOTTOM);
        BlockPos target = transformedZero(lowX, targetY, lowZ, envelope, rotation);
        BoundingBox bounds = transformedBounds(target, envelope, rotation);
        int chunkMinX = Math.floorDiv(origin.x(), 16) * 16;
        int chunkMinZ = Math.floorDiv(origin.z(), 16) * 16;
        BoundingBox writeBounds = new BoundingBox(chunkMinX - BOUNDING_MARGIN,
                world.minGenerationY(), chunkMinZ - BOUNDING_MARGIN,
                chunkMinX + 15 + BOUNDING_MARGIN,
                world.minGenerationY() + world.generationDepth() - 1,
                chunkMinZ + 15 + BOUNDING_MARGIN);
        if (official.enabled()) {
            official.record("burial", lowestSurface, depthDraw, unclampedY, targetY);
            official.record("target", target.x(), target.y(), target.z());
            official.record("bounds", bounds.minX(), bounds.minY(), bounds.minZ(),
                    bounds.maxX(), bounds.maxY(), bounds.maxZ());
        }

        int emptyCorners = countEmptyCorners(world, bounds, official);
        boolean accepted = emptyCorners <= MAX_EMPTY_CORNERS;
        if (official.enabled()) {
            official.record("corner_gate", emptyCorners, accepted ? 1L : 0L);
        }
        if (!accepted) {
            return new ConfiguredResult(false, false, false, selector, rotation,
                    target, bounds, emptyCorners, 0, 0, 0, FNV_OFFSET);
        }

        consumeOfficialTemplateBudget(random, sealed, official);
        if (official.enabled()) {
            official.record("template_boundary", selector, rotation, target.x(),
                    target.y(), target.z());
            official.record("template_voxel_parity", 0L);
        }
        GrammarResult grammar = placeOriginalGrammar(worldSeed, spec.lower(), selector,
                rotation, target, bounds, writeBounds, envelope, world, original);
        return new ConfiguredResult(grammar.completed, true, false, selector, rotation,
                target, bounds, emptyCorners, grammar.attemptedWrites,
                grammar.acceptedWrites, grammar.ownedCells, grammar.ownershipDigest);
    }

    static BlockPos transformedZero(int lowX, int y, int lowZ,
            TemplateEnvelope envelope, int rotation) {
        return switch (requireRotation(rotation)) {
            case 0 -> new BlockPos(lowX, y, lowZ);
            case 1 -> new BlockPos(lowX + envelope.sizeZ(), y, lowZ);
            case 2 -> new BlockPos(lowX + envelope.sizeX(), y,
                    lowZ + envelope.sizeZ());
            case 3 -> new BlockPos(lowX, y, lowZ + envelope.sizeX());
            default -> throw new AssertionError("unreachable rotation");
        };
    }

    static BoundingBox transformedBounds(BlockPos target, TemplateEnvelope envelope,
            int rotation) {
        BlockPos first = transformLocal(target, 0, 0, 0, rotation);
        BlockPos last = transformLocal(target, envelope.sizeX() - 1,
                envelope.sizeY() - 1, envelope.sizeZ() - 1, rotation);
        return new BoundingBox(Math.min(first.x(), last.x()), Math.min(first.y(), last.y()),
                Math.min(first.z(), last.z()), Math.max(first.x(), last.x()),
                Math.max(first.y(), last.y()), Math.max(first.z(), last.z()));
    }

    private static GrammarResult placeOriginalGrammar(long worldSeed, boolean lower,
            int selector, int rotation, BlockPos target, BoundingBox bounds,
            BoundingBox writeBounds, TemplateEnvelope envelope, WorldAccess world,
            OriginalityTraceSink trace) {
        long owner = originalitySeed(worldSeed, target, selector, rotation);
        int volume = envelope.sizeX() * envelope.sizeY() * envelope.sizeZ();
        boolean[] generated = new boolean[volume];
        boolean[] owned = new boolean[volume];
        WriteCounts counts = new WriteCounts();
        int centerX = envelope.sizeX() / 2;
        int centerZ = envelope.sizeZ() / 2;
        int baseY = Math.min(1, envelope.sizeY() - 1);
        boolean alongZ = envelope.sizeZ() >= envelope.sizeX();
        int spineLength = alongZ ? envelope.sizeZ() : envelope.sizeX();
        int lateralLimit = Math.max(1,
                (alongZ ? envelope.sizeX() : envelope.sizeZ()) / 2);
        if (trace.enabled()) {
            trace.record("grammar_seed", owner);
            trace.record("grammar_shape", selector, rotation, alongZ ? 1L : 0L,
                    spineLength, lateralLimit, baseY);
        }

        // Accepted cells form a prefix rooted at the first backbone cell. Never resume the
        // backbone beyond a rejected write: later ribs would otherwise become disconnected.
        boolean completed = true;
        grammar:
        for (int along = 0; along < spineLength; along++) {
            int x = alongZ ? centerX : along;
            int z = alongZ ? along : centerZ;
            byte axis = alongZ ? axisAfterRotation((byte) 2, rotation)
                    : axisAfterRotation((byte) 0, rotation);
            if (!placeBaseCell(owner, selector, target, bounds, envelope, world, trace,
                    writeBounds, rotation, x, baseY, z, axis, true,
                    generated, owned, counts)) {
                completed = false;
                break grammar;
            }

            if ((along & 1) == 0) {
                int below = baseY - 1;
                if (below >= 0) {
                    placeBaseCell(owner, selector, target, bounds, envelope, world, trace,
                            writeBounds, rotation, x, below, z, (byte) 1, true,
                            generated, owned, counts);
                }
                int rise = 1 + (int) Long.remainderUnsigned(coordinateHash(owner,
                        x, baseY, z, 0x737570706f7274L), envelope.sizeY() - baseY);
                for (int dy = 1; dy <= rise; dy++) {
                    if (!placeBaseCell(owner, selector, target, bounds, envelope, world, trace,
                            writeBounds, rotation, x, baseY + dy, z, (byte) 1, true,
                            generated, owned, counts)) {
                        break;
                    }
                }
            }

            if (along == 0 || along == spineLength - 1 || (along & 1) != 0) continue;
            int variation = 1 + (int) Long.remainderUnsigned(coordinateHash(owner,
                    x, baseY, z, 0x7269626c656eL), lateralLimit);
            for (int side = -1; side <= 1; side += 2) {
                for (int distance = 1; distance <= variation; distance++) {
                    int ribX = alongZ ? centerX + side * distance : x;
                    int ribZ = alongZ ? z : centerZ + side * distance;
                    byte ribAxis = alongZ ? axisAfterRotation((byte) 0, rotation)
                            : axisAfterRotation((byte) 2, rotation);
                    boolean placed = placeBaseCell(owner, selector, target, bounds,
                            envelope, world, trace, writeBounds, rotation,
                            ribX, baseY, ribZ, ribAxis, false,
                            generated, owned, counts);
                    if (!placed) break;
                    int archY = baseY + Math.min(envelope.sizeY() - baseY - 1,
                            Math.max(0, variation - distance));
                    for (int y = baseY + 1; y <= archY; y++) {
                        if (!placeBaseCell(owner, selector, target, bounds,
                                envelope, world, trace, writeBounds, rotation,
                                ribX, y, ribZ, (byte) 1, false,
                                generated, owned, counts)) {
                            break;
                        }
                    }
                }
            }
        }

        String overlay = lower ? LOWER_OVERLAY : UPPER_OVERLAY;
        byte overlayCode = lower ? (byte) 4 : (byte) 3;
        for (int index = 0; index < volume; index++) {
            if (!generated[index]) continue;
            int x = index % envelope.sizeX();
            int yz = index / envelope.sizeX();
            int z = yz % envelope.sizeZ();
            int y = yz / envelope.sizeZ();
            long gate = coordinateHash(owner, x, y, z, 0x6f7665726c6179L);
            boolean selected = Long.remainderUnsigned(gate, 10L) == 0L;
            if (trace.enabled()) {
                trace.record("overlay_gate", index, selected ? 1L : 0L);
            }
            if (!selected || !owned[index]) continue;
            BlockPos position = transformLocal(target, x, y, z, rotation);
            counts.attempted++;
            boolean protectedCell = world.featuresCannotReplace(
                    position.x(), position.y(), position.z());
            boolean written = !protectedCell && bounds.contains(
                    position.x(), position.y(), position.z())
                    && writeBounds.contains(position.x(), position.y(), position.z())
                    && world.trySetOwnedBlockState(position.x(), position.y(), position.z(),
                    overlay, owner);
            if (written) {
                counts.accepted++;
            }
            if (trace.enabled()) {
                trace.record("overlay_write", index, position.x(), position.y(), position.z(),
                        overlayCode, protectedCell ? 1L : 0L, written ? 1L : 0L);
            }
        }

        long digest = FNV_OFFSET;
        int ownedCells = 0;
        for (int index = 0; index < volume; index++) {
            if (!owned[index]) continue;
            int x = index % envelope.sizeX();
            int yz = index / envelope.sizeX();
            int z = yz % envelope.sizeZ();
            int y = yz / envelope.sizeZ();
            BlockPos position = transformLocal(target, x, y, z, rotation);
            digest = fnvInt(digest, position.x());
            digest = fnvInt(digest, position.y());
            digest = fnvInt(digest, position.z());
            ownedCells++;
        }
        if (trace.enabled()) {
            trace.record("originality_result", counts.attempted, counts.accepted,
                    ownedCells, digest);
        }
        return new GrammarResult(completed, counts.attempted, counts.accepted,
                ownedCells, digest);
    }

    /** Returns false only when the prefix-preserving base integrity gate rejects this cell. */
    private static boolean placeBaseCell(long owner, int selector, BlockPos target,
            BoundingBox bounds, TemplateEnvelope envelope, WorldAccess world,
            OriginalityTraceSink trace, BoundingBox writeBounds, int rotation,
            int localX, int localY, int localZ, byte stateCode, boolean mandatory,
            boolean[] generated, boolean[] owned, WriteCounts counts) {
        if (localX < 0 || localX >= envelope.sizeX() || localY < 0
                || localY >= envelope.sizeY() || localZ < 0 || localZ >= envelope.sizeZ()) {
            return false;
        }
        int index = localIndex(envelope, localX, localY, localZ);
        if (generated[index]) return true;
        long gate = coordinateHash(owner, localX, localY, localZ,
                0x6261736567617465L ^ selector);
        boolean selected = mandatory || Long.remainderUnsigned(gate, 10L) != 0L;
        if (trace.enabled()) {
            trace.record("base_gate", index, mandatory ? 1L : 0L, selected ? 1L : 0L);
        }
        if (!selected) return false;
        BlockPos position = transformLocal(target, localX, localY, localZ, rotation);
        counts.attempted++;
        boolean protectedCell = world.featuresCannotReplace(
                position.x(), position.y(), position.z());
        boolean written = !protectedCell && bounds.contains(
                position.x(), position.y(), position.z())
                && writeBounds.contains(position.x(), position.y(), position.z())
                && world.trySetOwnedBlockState(position.x(), position.y(), position.z(),
                boneState(stateCode), owner);
        if (written) {
            counts.accepted++;
            generated[index] = true;
            owned[index] = true;
        }
        if (trace.enabled()) {
            trace.record("base_write", index, position.x(), position.y(), position.z(),
                    stateCode, protectedCell ? 1L : 0L, written ? 1L : 0L);
        }
        return written;
    }

    private static int localIndex(TemplateEnvelope envelope, int x, int y, int z) {
        return (y * envelope.sizeZ() + z) * envelope.sizeX() + x;
    }

    private static int countEmptyCorners(WorldAccess world, BoundingBox bounds,
            OfficialTraceSink trace) {
        int empty = 0;
        int corner = 0;
        for (int zSide = 1; zSide >= 0; zSide--) {
            int z = zSide == 1 ? bounds.maxZ() : bounds.minZ();
            for (int ySide = 1; ySide >= 0; ySide--) {
                int y = ySide == 1 ? bounds.maxY() : bounds.minY();
                for (int xSide = 1; xSide >= 0; xSide--) {
                    int x = xSide == 1 ? bounds.maxX() : bounds.minX();
                    boolean emptyCorner = isEmptyCorner(world.blockState(x, y, z));
                    if (emptyCorner) empty++;
                    if (trace.enabled()) {
                        trace.record("corner", corner++, x, y, z,
                                emptyCorner ? 1L : 0L, empty);
                    }
                }
            }
        }
        return empty;
    }

    private static void consumeOfficialTemplateBudget(WorldgenRandom random,
            OfficialTemplateMetadata metadata, OfficialTraceSink trace) {
        int basePalette = random.nextInt(metadata.paletteCount());
        long baseDigest = FNV_OFFSET;
        for (int draw = 0; draw < metadata.baseProcessorDraws(); draw++) {
            int bits = Float.floatToRawIntBits(random.nextFloat());
            baseDigest = fnvInt(baseDigest, bits);
        }
        int overlayPalette = random.nextInt(metadata.paletteCount());
        long overlayDigest = FNV_OFFSET;
        for (int draw = 0; draw < metadata.overlayProcessorDraws(); draw++) {
            int bits = Float.floatToRawIntBits(random.nextFloat());
            overlayDigest = fnvInt(overlayDigest, bits);
        }
        if (trace.enabled()) {
            trace.record("base_budget", basePalette,
                    metadata.baseProcessorDraws(), baseDigest);
            trace.record("overlay_budget", overlayPalette,
                    metadata.overlayProcessorDraws(), overlayDigest);
        }
    }

    private static BlockPos transformLocal(BlockPos target, int x, int y, int z,
            int rotation) {
        return switch (requireRotation(rotation)) {
            case 0 -> new BlockPos(target.x() + x, target.y() + y, target.z() + z);
            case 1 -> new BlockPos(target.x() - z, target.y() + y, target.z() + x);
            case 2 -> new BlockPos(target.x() - x, target.y() + y, target.z() - z);
            case 3 -> new BlockPos(target.x() + z, target.y() + y, target.z() - x);
            default -> throw new AssertionError("unreachable rotation");
        };
    }

    private static byte axisAfterRotation(byte localAxis, int rotation) {
        if (localAxis == 1 || !swapsHorizontalAxes(rotation)) return localAxis;
        return localAxis == 0 ? (byte) 2 : (byte) 0;
    }

    private static String boneState(byte stateCode) {
        return switch (stateCode) {
            case 0 -> BONE_X;
            case 1 -> BONE_Y;
            case 2 -> BONE_Z;
            default -> throw new IllegalArgumentException("invalid bone axis code");
        };
    }

    private static boolean isEmptyCorner(String state) {
        String block = blockKey(state);
        return block.equals("minecraft:air") || block.equals("minecraft:cave_air")
                || block.equals("minecraft:void_air") || block.equals("minecraft:water")
                || block.equals("minecraft:lava");
    }

    private static String blockKey(String state) {
        if (state == null || state.isBlank()) {
            throw new IllegalArgumentException("world returned an empty block state");
        }
        int properties = state.indexOf('[');
        return properties < 0 ? state : state.substring(0, properties);
    }

    private static boolean biomeContains(String biomeKey, String featureKey) {
        if (biomeKey == null || !biomeKey.startsWith("minecraft:")
                || biomeKey.length() == "minecraft:".length()) {
            throw new IllegalArgumentException(
                    "exact Minecraft namespaced biome key is required: " + biomeKey);
        }
        return Mc263FeatureIndexReceipt.biome(biomeKey)
                .featuresAtStep(UNDERGROUND_STRUCTURES_STEP).stream()
                .anyMatch(reference -> reference.featureKey().equals(featureKey));
    }

    private static long originalitySeed(long worldSeed, BlockPos target,
            int selector, int rotation) {
        long value = McRandom.mixStafford13(worldSeed ^ ORIGINALITY_DOMAIN);
        value ^= (long) target.x() * 0x9E3779B97F4A7C15L;
        value ^= (long) target.y() * 0xC2B2AE3D27D4EB4FL;
        value ^= (long) target.z() * 0x165667B19E3779F9L;
        value ^= (long) selector * 0xD6E8FEB86659FD93L;
        value ^= (long) rotation * 0xA5A3564E27F8862FL;
        return McRandom.mixStafford13(value);
    }

    private static long coordinateHash(long seed, int x, int y, int z, long salt) {
        long value = seed ^ (long) x * 0x9E3779B97F4A7C15L;
        value ^= (long) y * 0xC2B2AE3D27D4EB4FL;
        value ^= (long) z * 0x165667B19E3779F9L;
        return McRandom.mixStafford13(value ^ salt);
    }

    private static long fnvInt(long hash, int value) {
        hash = fnvByte(hash, (byte) (value >>> 24));
        hash = fnvByte(hash, (byte) (value >>> 16));
        hash = fnvByte(hash, (byte) (value >>> 8));
        return fnvByte(hash, (byte) value);
    }

    private static long fnvByte(long hash, byte value) {
        return (hash ^ Byte.toUnsignedLong(value)) * FNV_PRIME;
    }

    private static PlacedSpec placedSpec(String featureKey) {
        if (featureKey == null || featureKey.isBlank()) {
            throw new IllegalArgumentException("placed feature key is required");
        }
        return switch (featureKey) {
            case FOSSIL_UPPER -> new PlacedSpec(FOSSIL_UPPER, UPPER_GLOBAL_INDEX,
                    false, 0, 319);
            case FOSSIL_LOWER -> new PlacedSpec(FOSSIL_LOWER, LOWER_GLOBAL_INDEX,
                    true, -64, -8);
            default -> throw new IllegalArgumentException("unsupported fossil feature: "
                    + featureKey);
        };
    }

    private static void verifyIndex(PlacedSpec spec) {
        int index = Mc263DecorationRandom.globalIndex(
                UNDERGROUND_STRUCTURES_STEP, spec.key());
        if (index != spec.globalIndex()
                || !Mc263FeatureIndexReceipt.step(UNDERGROUND_STRUCTURES_STEP)
                .featureAt(index).equals(spec.key())) {
            throw new IllegalStateException("pinned fossil feature index changed");
        }
    }

    private static int requireRotation(int rotation) {
        if (rotation < 0 || rotation > 3) {
            throw new IllegalArgumentException("rotation must be in 0..3");
        }
        return rotation;
    }

    private static boolean swapsHorizontalAxes(int rotation) {
        return (requireRotation(rotation) & 1) != 0;
    }

    private static void requireWorld(WorldAccess world) {
        if (world == null || world.minGenerationY() != -64 || world.generationDepth() != 384) {
            throw new IllegalArgumentException(
                    "pinned Overworld access with min Y -64 and depth 384 is required");
        }
    }

    private static OfficialTraceSink requireOfficialTrace(OfficialTraceSink trace) {
        if (trace == null) throw new IllegalArgumentException("official trace sink is required");
        return trace;
    }

    private static OriginalityTraceSink requireOriginalityTrace(OriginalityTraceSink trace) {
        if (trace == null) throw new IllegalArgumentException("originality trace sink is required");
        return trace;
    }

    private static PlacementCounts emptyCounts() {
        return new PlacementCounts(0, 0, 0, 0, 0, FNV_OFFSET);
    }

    private static void validateCounts(int candidates, int successes,
            int attemptedWrites, int acceptedWrites, int ownedCells) {
        if (candidates < 0 || candidates > 1 || successes < 0 || successes > candidates
                || attemptedWrites < 0 || acceptedWrites < 0
                || acceptedWrites > attemptedWrites || ownedCells < 0
                || ownedCells > acceptedWrites) {
            throw new IllegalArgumentException("invalid fossil placement counts");
        }
    }

    /** Receipt encoding: schema line, then one selector line, all UTF-8 and LF terminated. */
    private static String metadataReceiptLine(int selector) {
        OfficialTemplateMetadata item = metadata(selector);
        TemplateEnvelope size = item.envelope();
        return selector + ":" + size.sizeX() + "," + size.sizeY() + "," + size.sizeZ()
                + ";palette=" + item.paletteCount() + ";base=" + item.baseProcessorDraws()
                + ";overlay=" + item.overlayProcessorDraws();
    }

    private static OfficialTemplateMetadata metadata(int selector) {
        return switch (selector) {
            case 0 -> Metadata.M0;
            case 1 -> Metadata.M1;
            case 2 -> Metadata.M2;
            case 3 -> Metadata.M3;
            case 4 -> Metadata.M4;
            case 5 -> Metadata.M5;
            case 6 -> Metadata.M6;
            case 7 -> Metadata.M7;
            default -> throw new IllegalArgumentException("selector must be in 0..7");
        };
    }

    private sealed interface OfficialTemplateMetadata permits Metadata {
        TemplateEnvelope envelope();

        int paletteCount();

        int baseProcessorDraws();

        int overlayProcessorDraws();
    }

    private record Metadata(TemplateEnvelope envelope, int paletteCount,
                            int baseProcessorDraws, int overlayProcessorDraws)
            implements OfficialTemplateMetadata {
        private static final Metadata M0 = item(3, 3, 13, 37, 37);
        private static final Metadata M1 = item(5, 4, 13, 61, 61);
        private static final Metadata M2 = item(7, 4, 13, 97, 104);
        private static final Metadata M3 = item(9, 5, 13, 121, 121);
        private static final Metadata M4 = item(6, 5, 7, 86, 86);
        private static final Metadata M5 = item(7, 5, 5, 75, 75);
        private static final Metadata M6 = item(5, 4, 5, 58, 58);
        private static final Metadata M7 = item(4, 4, 4, 32, 32);

        private Metadata {
            if (envelope == null || paletteCount != 1 || baseProcessorDraws < 1
                    || overlayProcessorDraws < 1) {
                throw new IllegalArgumentException("invalid sealed fossil metadata");
            }
        }

        private static Metadata item(int x, int y, int z, int base, int overlay) {
            return new Metadata(new TemplateEnvelope(x, y, z), 1, base, overlay);
        }
    }

    private record PlacedSpec(String key, int globalIndex, boolean lower,
                              int minY, int maxY) {
    }

    private record GrammarResult(boolean completed,
                                 int attemptedWrites, int acceptedWrites,
                                 int ownedCells, long ownershipDigest) {
    }

    private static final class WriteCounts {
        private int attempted;
        private int accepted;
    }
}
