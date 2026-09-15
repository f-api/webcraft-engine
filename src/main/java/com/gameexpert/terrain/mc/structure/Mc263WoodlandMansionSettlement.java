package com.gameexpert.terrain.mc.structure;

import com.gameexpert.terrain.mc.McRandom;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Dormant Java settlement seam for the pinned Minecraft 26.3 Woodland Mansion.
 *
 * <p>The production path consumes only the procedural Mansion producer graph plus the coordinate-free
 * RUN/DATA grammar. P2N witness coordinates, operation traces and settlement sidecar fixtures are not
 * execution inputs. The caller supplies a runtime implementation of the exact P2R/P2T placement
 * authority; every static capability is authenticated before the producer is allowed to query terrain.
 * Template cells are then expanded procedurally and executed on an unpublished fork. Only a final
 * replay-aware atomic publication may install world writes, foreign correction writes, FTIK/BTIK,
 * BENT/LOOT/OWNR, mutable start successor and the caller-owned Xoroshiro continuation.</p>
 */
public final class Mc263WoodlandMansionSettlement {
    public static final String STRUCTURE_KEY = "minecraft:mansion";
    public static final String P2R_P2T_AUTHORITY_SHA256 =
            "cd9757f9c593a31f558ed57f855ac638fb9f00652273083837e9e1c2ee3abd93";
    public static final String P2T_LIQUID_BLOCK_SHA256 =
            "590a0296b90ff7164ac521581385298f3e5e06308a60444b3a4dbade2d72bb0d";
    public static final int P2T_LIQUID_BLOCK_BYTES = 17_406;
    public static final int AUTHORITY_TEMPLATE_COUNT = 73;
    public static final int AUTHORITY_EXACT_STATE_COUNT = 417;
    public static final int SOURCE_STATE_COUNT = 277;
    public static final int CLIP_MIN_Y = -64;
    public static final int CLIP_MAX_Y = 319;
    public static final int MIN_BUILD_Y = -64;
    public static final int MAX_BUILD_Y = 320;
    public static final int PRIMARY_WRITE_FLAGS = 2;
    public static final int NBT_BARRIER_FLAGS = 820;
    public static final int NEIGHBOR_CORRECTION_FLAGS = 16;
    public static final int EDGE_UPDATE_FLAGS = 0;
    public static final String WATER_SOURCE_STATE = "minecraft:water[level=0]";
    public static final String WATER = "minecraft:water";
    public static final int WATER_DELAY = 5;
    public static final int NORMAL_PRIORITY = 0;
    public static final String LEAVES = "minecraft:dark_oak_leaves";
    public static final int LEAVES_DELAY = 1;
    public static final String LOOT_TABLE = "minecraft:chests/woodland_mansion";
    public static final String SERVER_LEVEL_RANDOM_IMPLEMENTATION =
            "net.minecraft.world.level.levelgen.XoroshiroRandomSource";
    public static final String SERVER_LEVEL_RANDOM_IMPLEMENTATION_SHA256 =
            "43c6e33b55c91bd4576d2cf04ba7e5e7d2561c5918e1b3549485639d17906447";
    public static final int SERVER_LEVEL_RANDOM_STATE_WIDTH_BITS = 128;
    public static final String SERVER_LEVEL_RANDOM_SOURCE =
            "net.minecraft.server.level.WorldGenRegion#getRandom";
    public static final String SERVER_LEVEL_RANDOM_SOURCE_SHA256 =
            "9b3490302f809f99d196b93a738fc461cb703fc15bfc4743f420f34b6765854b";
    public static final String SERVER_LEVEL_RANDOM_CALLER =
            "net.minecraft.world.level.chunk.ChunkGenerator#applyBiomeDecoration";
    public static final String SERVER_LEVEL_RANDOM_CALLER_SHA256 =
            "71bcd9ce3b9de7f5132fad52a481be6b804f1af6f83c02fd1183af4adc583b12";
    public static final String SERVER_LEVEL_RANDOM_FACTORY =
            "net.minecraft.world.level.levelgen.RandomState#getOrCreateRandomFactory";
    public static final String SERVER_LEVEL_RANDOM_FACTORY_SHA256 =
            "3edf6d813903ecd0d92689b05d8176d98429b6f328b34d4ca1e191ee8c78b0f7";
    public static final String SERVER_LEVEL_RANDOM_FACTORY_KEY = "minecraft:worldgen_region_random";
    public static final String SERVER_LEVEL_RANDOM_CORE =
            "net.minecraft.world.level.levelgen.Xoroshiro128PlusPlus";
    public static final String SERVER_LEVEL_RANDOM_CORE_SHA256 =
            "70d31ed88a59ea6a529e60d63a7c32db23ee1e94ae259b6a8ecf9bce78ba57a3";
    public static final String SERVER_LEVEL_RANDOM_GAUSSIAN =
            "net.minecraft.world.level.levelgen.MarsagliaPolarGaussian";
    public static final String SERVER_LEVEL_RANDOM_GAUSSIAN_SHA256 =
            "e038018b121d0ebe3188eb540cf6ee2e7c8824b00e12c0aa424fa5399cc42ead";
    public static final ServerRandomAuthority SERVER_LEVEL_RANDOM_AUTHORITY = new ServerRandomAuthority(
            SERVER_LEVEL_RANDOM_IMPLEMENTATION,
            SERVER_LEVEL_RANDOM_IMPLEMENTATION_SHA256,
            SERVER_LEVEL_RANDOM_STATE_WIDTH_BITS,
            SERVER_LEVEL_RANDOM_SOURCE,
            SERVER_LEVEL_RANDOM_SOURCE_SHA256,
            SERVER_LEVEL_RANDOM_CALLER,
            SERVER_LEVEL_RANDOM_CALLER_SHA256,
            SERVER_LEVEL_RANDOM_FACTORY,
            SERVER_LEVEL_RANDOM_FACTORY_SHA256,
            SERVER_LEVEL_RANDOM_FACTORY_KEY,
            SERVER_LEVEL_RANDOM_CORE,
            SERVER_LEVEL_RANDOM_CORE_SHA256,
            SERVER_LEVEL_RANDOM_GAUSSIAN,
            SERVER_LEVEL_RANDOM_GAUSSIAN_SHA256);

    private static final String PROCESSOR_CLASS =
            "net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor";
    private static final String STRUCTURE_BLOCK = "minecraft:structure_block";
    private static final String PROCESSOR_AUTHORITY = PROCESSOR_CLASS + ":STRUCTURE_BLOCK";
    private static final String BARRIER = "minecraft:barrier[waterlogged=false]";
    private static final String AIR = "minecraft:air";
    private static final String COBBLESTONE = "minecraft:cobblestone";
    private static final Set<String> MARKERS = Set.of(
            "Chest", "ChestWest", "ChestEast", "ChestSouth", "ChestNorth",
            "Mage", "Warrior", "Group of Allays");
    private static final List<String> AUTHORITY_OPERATIONS = List.of(
            "EXPAND_GRAMMAR", "TRANSFORM_POSITION", "CLIP_BEFORE_PROCESSORS",
            "PROCESSOR_IGNORE_STRUCTURE_BLOCK", "QUERY_FLUID_PREIMAGE", "NBT_BARRIER_WRITE",
            "PRIMARY_WRITE", "RESTORE_SOURCE_FLUID", "EDGE_INSIDE_UPDATE", "EDGE_OUTSIDE_UPDATE",
            "UPDATE_FROM_NEIGHBOR_SHAPES", "NEIGHBOR_CORRECTION_WRITE", "UPDATE_NEIGHBORS_AT",
            "BLOCK_ENTITY_LOAD", "BLOCK_ENTITY_DIRTY", "LOOT_SEED_DRAW", "MARKER_CHEST",
            "MARKER_ILLAGER", "MARKER_ALLAY", "MARKER_CLEAR", "TEMPLATE_ENTITY_SUPPRESSED",
            "SCHEDULE_BLOCK_TICK", "SCHEDULE_FLUID_TICK", "AFTER_PLACE_CELL_TEST",
            "AFTER_PLACE_DESCEND_QUERY", "AFTER_PLACE_FOUNDATION_WRITE");
    private static final List<String> EXPLICIT_AUTHORITY_STATES = List.of(
            AIR, COBBLESTONE, WATER_SOURCE_STATE,
            "minecraft:chest[facing=north,type=single,waterlogged=false]",
            "minecraft:chest[facing=east,type=single,waterlogged=false]",
            "minecraft:chest[facing=south,type=single,waterlogged=false]",
            "minecraft:chest[facing=west,type=single,waterlogged=false]");

    private Mc263WoodlandMansionSettlement() { }

    /**
     * Projects raw Mansion BTIK encounters onto one chunk's {@code LevelChunkTicks} publication.
     * The pinned scheduler's uniqueness strategy compares only block identity and position, so the
     * first schedule wins even when a later encounter carries a different trigger, priority or
     * sub-tick order. Raw encounters remain in {@link Settlement#blockTicks()} for the transaction
     * receipt; only the live carrier uses this projection.
     */
    public static List<BlockTick> settleBlockTicksForPublication(List<BlockTick> rawTicks) {
        Objects.requireNonNull(rawTicks, "Mansion raw BTIK transcript");
        LinkedHashMap<BlockTickIdentity, BlockTick> first = new LinkedHashMap<>();
        for (int index = 0; index < rawTicks.size(); index++) {
            BlockTick tick = Objects.requireNonNull(rawTicks.get(index),
                    "Mansion raw BTIK row " + index);
            require(!tick.blockKey.isBlank() && tick.delay >= 0
                            && tick.subTickOrder >= -1,
                    "unknown Mansion raw block-tick authority: " + tick.blockKey + " at "
                            + tick.position + " delay=" + tick.delay + " priority="
                            + tick.priority + " subTickOrder=" + tick.subTickOrder);
            BlockTickIdentity identity = new BlockTickIdentity(tick.position, tick.blockKey);
            BlockTick accepted = first.putIfAbsent(identity, tick);
            require(accepted != null || tick.subTickOrder >= 0,
                    "Mansion first block-tick schedule lacks sub-tick order");
        }
        return List.copyOf(first.values());
    }

    /**
     * Settles one destination chunk. Capability and authority methods on {@code transaction} are
     * required to be pure. The first terrain query is the producer's WORLD_SURFACE_WG read, which
     * occurs only after the complete P2R/P2T capability closure has passed.
     */
    public static Settlement execute(Request request, WorldTransaction transaction,
            PlacementRandom callerRandom, ServerLevelRandom callerServerRandom) {
        Objects.requireNonNull(request, "Mansion settlement request");
        Objects.requireNonNull(transaction, "Mansion settlement transaction");
        Objects.requireNonNull(callerRandom, "Mansion settlement caller random");
        Objects.requireNonNull(callerServerRandom, "Mansion settlement ServerLevel random");

        long incomingLo = callerRandom.lo();
        long incomingHi = callerRandom.hi();
        int incomingCount = callerRandom.count();
        ServerRandomState incomingServerState = callerServerRandom.state();
        Preflight preflight = preflight(request, transaction, incomingLo, incomingHi, incomingCount,
                incomingServerState);

        Mc263WoodlandMansionProducer.Result generated = Mc263WoodlandMansionProducer.generate(
                request.worldSeed(), request.startChunkX(), request.startChunkZ(), transaction);
        if (!(generated instanceof Mc263WoodlandMansionProducer.Start start)) {
            throw new IllegalArgumentException("Mansion settlement start is rejected");
        }
        validateStart(request, start, preflight);
        return executePrepared(request, start.pieces(), start.aggregateBoundingBox(),
                start.successor().carrier(), transaction, callerRandom, callerServerRandom, preflight,
                incomingLo, incomingHi, incomingCount, incomingServerState);
    }

    static Settlement executePersisted(Request request,
            List<Mc263WoodlandMansionProducer.Piece> pieces,
            Mc263WoodlandMansionGrammar.Box aggregateBoundingBox,
            Mc263WoodlandMansionProducer.PersistedCarrier carrier,
            WorldTransaction transaction, PlacementRandom callerRandom,
            ServerLevelRandom callerServerRandom) {
        Objects.requireNonNull(request, "Mansion settlement request");
        List<Mc263WoodlandMansionProducer.Piece> preparedPieces = List.copyOf(
                Objects.requireNonNull(pieces, "Mansion persisted pieces"));
        Objects.requireNonNull(aggregateBoundingBox, "Mansion persisted aggregate bounds");
        Objects.requireNonNull(carrier, "Mansion persisted carrier");
        Objects.requireNonNull(transaction, "Mansion settlement transaction");
        Objects.requireNonNull(callerRandom, "Mansion settlement caller random");
        Objects.requireNonNull(callerServerRandom, "Mansion settlement ServerLevel random");

        long incomingLo = callerRandom.lo();
        long incomingHi = callerRandom.hi();
        int incomingCount = callerRandom.count();
        ServerRandomState incomingServerState = callerServerRandom.state();
        Preflight preflight = preflight(request, transaction, incomingLo, incomingHi, incomingCount,
                incomingServerState);
        validatePersistedGraph(request, preparedPieces, aggregateBoundingBox, carrier, preflight);
        return executePrepared(request, preparedPieces, aggregateBoundingBox, carrier, transaction,
                callerRandom, callerServerRandom, preflight, incomingLo, incomingHi, incomingCount,
                incomingServerState);
    }

    private static Settlement executePrepared(Request request,
            List<Mc263WoodlandMansionProducer.Piece> pieces,
            Mc263WoodlandMansionGrammar.Box aggregateBoundingBox,
            Mc263WoodlandMansionProducer.PersistedCarrier carrier,
            WorldTransaction transaction, PlacementRandom callerRandom,
            ServerLevelRandom callerServerRandom, Preflight preflight, long incomingLo,
            long incomingHi, int incomingCount, ServerRandomState incomingServerState) {
        Clip clip = clip(request.targetChunkX(), request.targetChunkZ());
        validateClip(aggregateBoundingBox, clip);

        PlacementRandom candidate = callerRandom.copy();
        ServerLevelRandom serverCandidate = callerServerRandom.copy();
        WorldTransaction isolated = Objects.requireNonNull(transaction.fork(),
                "isolated Mansion transaction");
        require(isolated != transaction, "Mansion transaction fork is not isolated");

        long owner = ownerId(request);
        Accumulator accumulator = new Accumulator(preflight, clip, owner);
        for (Mc263WoodlandMansionProducer.Piece piece : pieces) {
            if (!intersects(piece.boundingBox(), clip)) continue;
            executePiece(piece, preflight, isolated, candidate, accumulator);
            executeMarkers(piece, preflight, isolated, candidate, serverCandidate, accumulator);
        }
        executeAfterPlace(pieces, aggregateBoundingBox, preflight, isolated, accumulator);

        SuccessorCarrier successor = successor(carrier);
        RandomContinuation continuation = new RandomContinuation(candidate.lo(), candidate.hi(),
                candidate.count(), candidate.continuationNextLongI64());
        ServerRandomContinuation serverContinuation = serverCandidate.continuation();
        Settlement settlement = accumulator.freeze(request, successor, incomingLo, incomingHi,
                incomingCount, continuation, incomingServerState, serverContinuation);

        require(callerRandom.lo() == incomingLo && callerRandom.hi() == incomingHi
                        && callerRandom.count() == incomingCount,
                "Mansion caller placement RNG changed before atomic publication");
        require(callerServerRandom.state().equals(incomingServerState),
                "Mansion caller ServerLevel RNG changed before atomic publication");
        PlacementRandom placementBeforePublish = callerRandom.copy();
        ServerLevelRandom serverBeforePublish = callerServerRandom.copy();
        try {
            PublishStatus status = Objects.requireNonNull(transaction.publishAtomically(
                    isolated, settlement, callerRandom, candidate, callerServerRandom, serverCandidate),
                    "Mansion publish status");
            require(status == PublishStatus.COMMITTED || status == PublishStatus.REPLAYED,
                    "unknown Mansion publish status");
            require(callerRandom.sameState(candidate),
                    "Mansion atomic publisher did not install accepted placement RNG continuation");
            require(callerServerRandom.sameState(serverCandidate),
                    "Mansion atomic publisher did not install accepted ServerLevel RNG continuation");
            return settlement;
        } catch (RuntimeException | Error failure) {
            callerRandom.replaceWith(placementBeforePublish);
            callerServerRandom.replaceWith(serverBeforePublish);
            throw failure;
        }
    }

    private static Preflight preflight(Request request, WorldTransaction transaction, long incomingLo,
            long incomingHi, int incomingCount, ServerRandomState incomingServerState) {
        require(STRUCTURE_KEY.equals(request.structureKey()),
                "unsupported Mansion structure key: " + request.structureKey());
        Math.multiplyExact(request.startChunkX(), 16);
        Math.multiplyExact(request.startChunkZ(), 16);
        Math.multiplyExact(request.targetChunkX(), 16);
        Math.multiplyExact(request.targetChunkZ(), 16);

        require(transaction.supportsPlacementRandom(incomingLo, incomingHi, incomingCount),
                "Mansion caller placement RNG authority unavailable");
        require(transaction.supportsServerLevelRandom(SERVER_LEVEL_RANDOM_AUTHORITY,
                        incomingServerState),
                "Mansion caller ServerLevel RNG authority unavailable");
        require(transaction.supportsP2rP2tAuthority(P2R_P2T_AUTHORITY_SHA256,
                        P2T_LIQUID_BLOCK_SHA256, P2T_LIQUID_BLOCK_BYTES,
                        AUTHORITY_TEMPLATE_COUNT, AUTHORITY_EXACT_STATE_COUNT),
                "accepted Mansion P2R/P2T authority is unavailable");
        require(transaction.authorityTemplateCount() == AUTHORITY_TEMPLATE_COUNT
                        && transaction.authorityExactStateCount() == AUTHORITY_EXACT_STATE_COUNT,
                "Mansion authority cardinality drift");
        require(transaction.supportsAtomicForkPublishWithRandom(),
                "atomic Mansion world/sidecar/start/RNG publish required");
        require(transaction.supportsHeightmap(Mc263WoodlandMansionProducer.Heightmap.WORLD_SURFACE_WG),
                "WORLD_SURFACE_WG capability unavailable");
        require(transaction.supportsBuildHeightBoundary()
                        && transaction.minBuildY() == MIN_BUILD_Y
                        && transaction.maxBuildY() == MAX_BUILD_Y,
                "Mansion build-height capability drift");
        require(transaction.supportsTemplateCellExecution()
                        && transaction.supportsMarkerExecution()
                        && transaction.supportsStateTransform()
                        && transaction.supportsKeepLiquids()
                        && transaction.supportsNeighborResolution()
                        && transaction.supportsAfterPlaceExecution()
                        && transaction.supportsCanonicalBlockEntityNbt()
                        && transaction.supportsTypedBentLane()
                        && transaction.supportsLootLane()
                        && transaction.supportsStructureEntityLane()
                        && transaction.supportsOwnerLane()
                        && transaction.supportsSuccessorLane()
                        && transaction.supportsBlockTickLane()
                        && transaction.supportsFluidTickLane()
                        && transaction.supportsEmptyBlockQueries()
                        && transaction.supportsFluidStateQueries()
                        && transaction.supportsSetBlock(),
                "Mansion settlement capability closure unavailable");
        require(transaction.supportsWriteFlags(EDGE_UPDATE_FLAGS)
                        && transaction.supportsWriteFlags(PRIMARY_WRITE_FLAGS)
                        && transaction.supportsWriteFlags(NEIGHBOR_CORRECTION_FLAGS)
                        && transaction.supportsWriteFlags(NBT_BARRIER_FLAGS),
                "Mansion write-flag closure unavailable");
        require(transaction.supportsTransientState(BARRIER),
                "Mansion transient NBT barrier state unavailable");
        require(transaction.authorityAdmitsProcessor(PROCESSOR_AUTHORITY),
                "Mansion processor authority unavailable");
        require(transaction.supportsBlockTick(LEAVES, LEAVES_DELAY, NORMAL_PRIORITY),
                "Mansion dark-oak leaf scheduler authority unavailable");
        require(transaction.supportsFluidTick(WATER, WATER_SOURCE_STATE,
                        WATER_DELAY, NORMAL_PRIORITY),
                "Mansion LiquidBlock scheduler authority unavailable");
        require(transaction.supportsLootTable(LOOT_TABLE),
                "Mansion loot-table authority unavailable");
        for (String marker : MARKERS) {
            require(transaction.supportsMarker(marker), "unknown Mansion marker capability: " + marker);
        }
        require(transaction.supportsStructureEntity("minecraft:evoker")
                        && transaction.supportsStructureEntity("minecraft:vindicator")
                        && transaction.supportsStructureEntity("minecraft:allay"),
                "Mansion structure-entity capability closure unavailable");
        require(transaction.supportsNbtSemantic("MARKER_CHEST", "minecraft:chest"),
                "Mansion marker-chest NBT authority unavailable");
        for (String operation : AUTHORITY_OPERATIONS) {
            require(transaction.authorityAdmitsOperation(operation),
                    "Mansion operation outside P2R/P2T authority: " + operation);
        }

        Mc263WoodlandMansionGrammar grammar = Mc263WoodlandMansionGrammar.loadAccepted();
        require(grammar.templates().size() == AUTHORITY_TEMPLATE_COUNT
                        && grammar.stateCatalog().size() == SOURCE_STATE_COUNT,
                "Mansion procedural grammar cardinality drift");
        LinkedHashMap<String, Mc263WoodlandMansionGrammar.Template> templates = new LinkedHashMap<>();
        LinkedHashMap<TransformKey, String> transforms = new LinkedHashMap<>();
        LinkedHashSet<String> preflightedStates = new LinkedHashSet<>();

        for (Mc263WoodlandMansionGrammar.Template template : grammar.templates().values()) {
            require(transaction.authorityAdmitsTemplate(template.id())
                            && transaction.supportsTemplate(template.id()),
                    "Mansion template authority/capability unavailable: " + template.id());
            require(templates.put(template.id(), template) == null,
                    "duplicate Mansion procedural template: " + template.id());
            for (String source : template.stateTable()) {
                if (STRUCTURE_BLOCK.equals(blockKey(source))) continue;
                require(transaction.authorityAdmitsExactState(source)
                                && transaction.supportsExactState(source),
                        "Mansion source exact state unavailable: " + source);
                preflightedStates.add(canonicalState(source));
                for (Mc263WoodlandMansionGrammar.Mirror mirror
                        : Mc263WoodlandMansionGrammar.Mirror.values()) {
                    for (Mc263WoodlandMansionGrammar.Rotation rotation
                            : Mc263WoodlandMansionGrammar.Rotation.values()) {
                        TransformKey key = new TransformKey(source, mirror, rotation);
                        if (transforms.containsKey(key)) continue;
                        String transformed = canonicalState(Objects.requireNonNull(
                                transaction.transformExactState(source, mirror, rotation),
                                "Mansion transformed state"));
                        require(transaction.authorityAdmitsExactState(transformed)
                                        && transaction.supportsExactState(transformed),
                                "Mansion transformed exact state unavailable: " + transformed);
                        transforms.put(key, transformed);
                        preflightedStates.add(transformed);
                    }
                }
            }
            for (Mc263WoodlandMansionGrammar.Command command : template.commands()) {
                if (command instanceof Mc263WoodlandMansionGrammar.Data data
                        && !(data.semantic() instanceof Mc263WoodlandMansionGrammar.StructureMarker)) {
                    String kind = semanticKind(data.semantic());
                    String type = blockEntityType(data.semantic());
                    require(transaction.supportsNbtSemantic(kind, type),
                            "Mansion NBT semantic authority unavailable: " + kind + "/" + type);
                }
            }
        }
        require(templates.size() == AUTHORITY_TEMPLATE_COUNT,
                "Mansion template preflight cardinality drift");
        for (String state : EXPLICIT_AUTHORITY_STATES) {
            require(transaction.authorityAdmitsExactState(state)
                            && transaction.supportsExactState(state),
                    "Mansion explicit exact state unavailable: " + state);
            preflightedStates.add(state);
        }
        require(transaction.supportsFingerprintLane(),
                "Mansion fingerprint/replay capability unavailable");
        return new Preflight(transaction, Map.copyOf(templates), Map.copyOf(transforms),
                Set.copyOf(preflightedStates), transaction.minBuildY());
    }

    private static void validateStart(Request request, Mc263WoodlandMansionProducer.Start start,
            Preflight preflight) {
        require(STRUCTURE_KEY.equals(start.request().structureKey())
                        && start.request().worldSeed() == request.worldSeed()
                        && start.request().chunkX() == request.startChunkX()
                        && start.request().chunkZ() == request.startChunkZ(),
                "Mansion procedural start identity drift");
        Mc263WoodlandMansionProducer.ProcessorSpec processor = start.successor().processor();
        require(PROCESSOR_CLASS.equals(processor.className())
                        && processor.ignoredBlocks().equals(List.of(STRUCTURE_BLOCK))
                        && preflight.transaction.authorityAdmitsProcessor(PROCESSOR_AUTHORITY),
                "Mansion procedural processor drift");
        require(start.successor().templates().size() == AUTHORITY_TEMPLATE_COUNT,
                "Mansion successor template closure drift");
        for (Mc263WoodlandMansionGrammar.Template template : start.successor().templates()) {
            require(preflight.templates.containsKey(template.id()),
                    "Mansion successor references unpreflighted template: " + template.id());
        }
        for (Mc263WoodlandMansionProducer.Piece piece : start.pieces()) {
            require(preflight.templates.containsKey(piece.templateKey()),
                    "Mansion piece references unpreflighted template: " + piece.templateKey());
        }
    }

    private static void validatePersistedGraph(Request request,
            List<Mc263WoodlandMansionProducer.Piece> pieces,
            Mc263WoodlandMansionGrammar.Box aggregateBoundingBox,
            Mc263WoodlandMansionProducer.PersistedCarrier carrier, Preflight preflight) {
        require(!pieces.isEmpty(), "empty Mansion persisted piece graph");
        Mc263WoodlandMansionGrammar.Box union = null;
        for (int index = 0; index < pieces.size(); index++) {
            Mc263WoodlandMansionProducer.Piece piece = pieces.get(index);
            require(piece.ordinal() == index, "Mansion persisted piece encounter order drift");
            require(preflight.templates.containsKey(piece.templateKey()),
                    "Mansion persisted piece references unauthenticated template: " + piece.templateKey());
            require(piece.markers().equals(canonicalMarkers(piece, preflight)),
                    "Mansion persisted piece marker graph drift at " + index);
            union = union == null ? piece.boundingBox() : encapsulate(union, piece.boundingBox());
        }
        require(aggregateBoundingBox.equals(union),
                "Mansion persisted aggregate bounds do not equal exact piece union");
        validateClip(aggregateBoundingBox, clip(request.targetChunkX(), request.targetChunkZ()));

        Mc263WoodlandMansionProducer.PersistedCarrier expected =
                Mc263WoodlandMansionProducer.encodeCarrier(
                        pieces, request.startChunkX(), request.startChunkZ());
        require(expected.format().equals(carrier.format()), "Mansion persisted carrier format drift");
        require(expected.pieces().size() == carrier.pieces().size(),
                "Mansion persisted carrier piece count drift");
        for (int index = 0; index < expected.pieces().size(); index++) {
            require(expected.pieces().get(index).equals(carrier.pieces().get(index)),
                    "Mansion persisted carrier piece order/payload drift at " + index);
        }
        validateReferenceDelta(carrier);
        require(expected.structureStart().equals(carrier.structureStart()),
                "Mansion persisted start payload identity drift");
        require(expected.mutableSuccessorAfterOneReference().equals(
                        carrier.mutableSuccessorAfterOneReference()),
                "Mansion persisted successor payload identity drift");
    }

    private static List<Mc263WoodlandMansionGrammar.Marker> canonicalMarkers(
            Mc263WoodlandMansionProducer.Piece piece, Preflight preflight) {
        Mc263WoodlandMansionGrammar.Template template = preflight.requireTemplate(piece.templateKey());
        ArrayList<Mc263WoodlandMansionGrammar.Marker> result = new ArrayList<>();
        for (Mc263WoodlandMansionGrammar.Command command : template.commands()) {
            if (!(command instanceof Mc263WoodlandMansionGrammar.Data data)
                    || !(data.semantic() instanceof Mc263WoodlandMansionGrammar.StructureMarker marker)) {
                continue;
            }
            Position local = transformLocal(data.position(), piece.mirror(), piece.rotation());
            Mc263WoodlandMansionGrammar.Pos position = new Mc263WoodlandMansionGrammar.Pos(
                    Math.addExact(piece.origin().x(), local.x()),
                    Math.addExact(piece.origin().y(), local.y()),
                    Math.addExact(piece.origin().z(), local.z()));
            result.add(new Mc263WoodlandMansionGrammar.Marker(
                    result.size(), marker.metadata(), position));
        }
        return List.copyOf(result);
    }

    private static void validateClip(Mc263WoodlandMansionGrammar.Box aggregateBoundingBox, Clip clip) {
        require(intersects(aggregateBoundingBox, clip),
                "Mansion destination clip misses aggregate bounds");
    }

    private static void executePiece(Mc263WoodlandMansionProducer.Piece piece, Preflight preflight,
            WorldTransaction world, PlacementRandom random, Accumulator accumulator) {
        Mc263WoodlandMansionGrammar.Template template = preflight.requireTemplate(piece.templateKey());
        for (Mc263WoodlandMansionGrammar.Command command : template.commands()) {
            String source = template.stateTable().get(command.stateIndex());
            if (command instanceof Mc263WoodlandMansionGrammar.Run run) {
                for (int index = 0; index < run.count(); index++) {
                    Mc263WoodlandMansionGrammar.Pos local = new Mc263WoodlandMansionGrammar.Pos(
                            Math.addExact(run.start().x(), Math.multiplyExact(run.delta().x(), index)),
                            Math.addExact(run.start().y(), Math.multiplyExact(run.delta().y(), index)),
                            Math.addExact(run.start().z(), Math.multiplyExact(run.delta().z(), index)));
                    executeCell(piece, template.id(), Math.addExact(run.ordinal(), index), source,
                            local, null, preflight, world, random, accumulator);
                }
            } else if (command instanceof Mc263WoodlandMansionGrammar.Data data) {
                if (data.semantic() instanceof Mc263WoodlandMansionGrammar.StructureMarker) {
                    require(STRUCTURE_BLOCK.equals(blockKey(source)),
                            "Mansion structure marker escaped ignore processor");
                    continue;
                }
                executeCell(piece, template.id(), data.ordinal(), source, data.position(),
                        data.semantic(), preflight, world, random, accumulator);
            } else {
                throw new IllegalArgumentException("unknown Mansion grammar command implementation");
            }
        }
    }

    private static void executeCell(Mc263WoodlandMansionProducer.Piece piece, String templateId,
            int sourceOrdinal, String sourceState, Mc263WoodlandMansionGrammar.Pos local,
            Mc263WoodlandMansionGrammar.Semantic semantic, Preflight preflight,
            WorldTransaction world, PlacementRandom random, Accumulator accumulator) {
        if (STRUCTURE_BLOCK.equals(blockKey(sourceState))) return;
        Position transformedLocal = transformLocal(local, piece.mirror(), piece.rotation());
        Position position = new Position(
                Math.addExact(piece.origin().x(), transformedLocal.x()),
                Math.addExact(piece.origin().y(), transformedLocal.y()),
                Math.addExact(piece.origin().z(), transformedLocal.z()));
        if (!accumulator.clip.contains(position)) return; // clip-before-processors

        String transformedState = preflight.transformedState(sourceState, piece.mirror(), piece.rotation());
        Long placementSeed = null;
        if (isRandomizableContainer(semantic)) placementSeed = random.nextLong();
        CellPlacement placement = new CellPlacement(piece.ordinal(), templateId, sourceOrdinal,
                new Position(local.x(), local.y(), local.z()), position, sourceState, transformedState,
                piece.mirror(), piece.rotation(), semantic, placementSeed, accumulator.clip);
        CellEffects effects = Objects.requireNonNull(world.executeTemplateCell(placement),
                "Mansion template-cell effects");
        accumulator.acceptCell(placement, effects);
    }

    private static void executeMarkers(Mc263WoodlandMansionProducer.Piece piece, Preflight preflight,
            WorldTransaction world, PlacementRandom random, ServerLevelRandom serverRandom,
            Accumulator accumulator) {
        for (Mc263WoodlandMansionGrammar.Marker marker : piece.markers()) {
            Position position = new Position(marker.position().x(), marker.position().y(), marker.position().z());
            if (!accumulator.clip.contains(position)) continue;
            require(MARKERS.contains(marker.metadata()), "unknown Mansion marker: " + marker.metadata());
            Long lootSeed = isChestMarker(marker.metadata()) ? random.nextLong() : null;
            MarkerPlacement placement = new MarkerPlacement(piece.ordinal(), piece.templateKey(),
                    marker.ordinal(), marker.metadata(), position, piece.mirror(), piece.rotation(),
                    lootSeed, accumulator.clip);
            MarkerEffects effects = Objects.requireNonNull(world.executeMarker(placement, serverRandom),
                    "Mansion marker effects");
            accumulator.acceptMarker(placement, effects);
        }
    }

    private static void executeAfterPlace(List<Mc263WoodlandMansionProducer.Piece> pieces,
            Mc263WoodlandMansionGrammar.Box aggregateBoundingBox, Preflight preflight,
            WorldTransaction world, Accumulator accumulator) {
        int anchorY = aggregateBoundingBox.minY();
        for (int x = accumulator.clip.minX(); x <= accumulator.clip.maxX(); x++) {
            for (int z = accumulator.clip.minZ(); z <= accumulator.clip.maxZ(); z++) {
                Position anchor = new Position(x, anchorY, z);
                if (!inside(aggregateBoundingBox, anchor) || !insideAnyPiece(pieces, anchor)) continue;
                if (world.isEmptyBlock(anchor)) continue;
                for (int y = Math.subtractExact(anchorY, 1); y > preflight.minBuildY; y--) {
                    Position position = new Position(x, y, z);
                    boolean empty = world.isEmptyBlock(position);
                    FluidState fluid = Objects.requireNonNull(world.getFluidState(position),
                            "Mansion afterPlace fluid state");
                    if (!empty && fluid.empty()) break;
                    if (world.setBlock(position, COBBLESTONE, PRIMARY_WRITE_FLAGS)) {
                        accumulator.acceptDirectWrite(new WorldWrite(
                                WriteCause.FOUNDATION, position, COBBLESTONE, PRIMARY_WRITE_FLAGS));
                    }
                }
            }
        }
    }

    static Position transformLocalForTest(Mc263WoodlandMansionGrammar.Pos local,
            Mc263WoodlandMansionGrammar.Mirror mirror,
            Mc263WoodlandMansionGrammar.Rotation rotation) {
        return transformLocal(local, mirror, rotation);
    }

    private static Position transformLocal(Mc263WoodlandMansionGrammar.Pos local,
            Mc263WoodlandMansionGrammar.Mirror mirror,
            Mc263WoodlandMansionGrammar.Rotation rotation) {
        Objects.requireNonNull(local, "Mansion local position");
        Objects.requireNonNull(mirror, "Mansion mirror");
        Objects.requireNonNull(rotation, "Mansion rotation");
        int x = local.x();
        int z = local.z();
        if (mirror == Mc263WoodlandMansionGrammar.Mirror.LEFT_RIGHT) z = Math.negateExact(z);
        else if (mirror == Mc263WoodlandMansionGrammar.Mirror.FRONT_BACK) x = Math.negateExact(x);
        int rx;
        int rz;
        switch (rotation) {
            case NONE -> { rx = x; rz = z; }
            case CLOCKWISE_90 -> { rx = Math.negateExact(z); rz = x; }
            case CLOCKWISE_180 -> { rx = Math.negateExact(x); rz = Math.negateExact(z); }
            case COUNTERCLOCKWISE_90 -> { rx = z; rz = Math.negateExact(x); }
            default -> throw new IllegalArgumentException("unknown Mansion rotation");
        }
        return new Position(rx, local.y(), rz);
    }

    private static Clip clip(int chunkX, int chunkZ) {
        int minX = Math.multiplyExact(chunkX, 16);
        int minZ = Math.multiplyExact(chunkZ, 16);
        return new Clip(chunkX, chunkZ, minX, CLIP_MIN_Y, minZ,
                Math.addExact(minX, 15), CLIP_MAX_Y, Math.addExact(minZ, 15));
    }

    private static boolean intersects(Mc263WoodlandMansionGrammar.Box box, Clip clip) {
        return box.maxX() >= clip.minX && box.minX() <= clip.maxX
                && box.maxY() >= clip.minY && box.minY() <= clip.maxY
                && box.maxZ() >= clip.minZ && box.minZ() <= clip.maxZ;
    }

    private static boolean inside(Mc263WoodlandMansionGrammar.Box box, Position position) {
        return position.x >= box.minX() && position.x <= box.maxX()
                && position.y >= box.minY() && position.y <= box.maxY()
                && position.z >= box.minZ() && position.z <= box.maxZ();
    }

    private static Mc263WoodlandMansionGrammar.Box encapsulate(
            Mc263WoodlandMansionGrammar.Box a, Mc263WoodlandMansionGrammar.Box b) {
        return new Mc263WoodlandMansionGrammar.Box(
                Math.min(a.minX(), b.minX()), Math.min(a.minY(), b.minY()),
                Math.min(a.minZ(), b.minZ()), Math.max(a.maxX(), b.maxX()),
                Math.max(a.maxY(), b.maxY()), Math.max(a.maxZ(), b.maxZ()));
    }

    private static boolean insideAnyPiece(List<Mc263WoodlandMansionProducer.Piece> pieces,
            Position position) {
        for (Mc263WoodlandMansionProducer.Piece piece : pieces) {
            if (inside(piece.boundingBox(), position)) return true;
        }
        return false;
    }

    private static boolean isChestMarker(String marker) { return marker.startsWith("Chest"); }

    private static boolean isRandomizableContainer(Mc263WoodlandMansionGrammar.Semantic semantic) {
        if (semantic instanceof Mc263WoodlandMansionGrammar.EmptyContainer empty) {
            return "minecraft:chest".equals(empty.blockEntityType())
                    || "minecraft:trapped_chest".equals(empty.blockEntityType());
        }
        if (semantic instanceof Mc263WoodlandMansionGrammar.ContainerItems items) {
            return "minecraft:chest".equals(items.blockEntityType())
                    || "minecraft:trapped_chest".equals(items.blockEntityType());
        }
        return false;
    }

    private static String semanticKind(Mc263WoodlandMansionGrammar.Semantic semantic) {
        if (semantic instanceof Mc263WoodlandMansionGrammar.EmptyContainer) return "EMPTY_CONTAINER";
        if (semantic instanceof Mc263WoodlandMansionGrammar.ContainerItems) return "CONTAINER_ITEMS";
        if (semantic instanceof Mc263WoodlandMansionGrammar.PatternedBanner) return "PATTERNED_BANNER";
        if (semantic instanceof Mc263WoodlandMansionGrammar.MobSpawner) return "MOB_SPAWNER";
        if (semantic instanceof Mc263WoodlandMansionGrammar.StructureMarker) return "STRUCTURE_MARKER";
        throw new IllegalArgumentException("unknown Mansion NBT semantic implementation");
    }

    private static String blockEntityType(Mc263WoodlandMansionGrammar.Semantic semantic) {
        if (semantic instanceof Mc263WoodlandMansionGrammar.EmptyContainer value) return value.blockEntityType();
        if (semantic instanceof Mc263WoodlandMansionGrammar.ContainerItems value) return value.blockEntityType();
        if (semantic instanceof Mc263WoodlandMansionGrammar.PatternedBanner value) return value.blockEntityType();
        if (semantic instanceof Mc263WoodlandMansionGrammar.MobSpawner value) return value.blockEntityType();
        throw new IllegalArgumentException("Mansion semantic has no block entity type");
    }

    private static String canonicalState(String state) {
        String value = Objects.requireNonNull(state, "Mansion exact state").replace(", ", ",").trim();
        require(value.startsWith("minecraft:") && value.indexOf(' ') < 0,
                "malformed Mansion exact state: " + state);
        return value;
    }

    private static String blockKey(String state) {
        String canonical = canonicalState(state);
        int bracket = canonical.indexOf('[');
        return bracket < 0 ? canonical : canonical.substring(0, bracket);
    }

    private static long ownerId(Request request) {
        String startKey = request.structureKey() + "@" + request.startChunkX() + "," + request.startChunkZ();
        MessageDigest digest = sha256Digest();
        digest.update(request.structureKey().getBytes(StandardCharsets.UTF_8));
        digest.update((byte) 0);
        byte[] hash = digest.digest(startKey.getBytes(StandardCharsets.UTF_8));
        long owner = 0L;
        for (int index = 0; index < 8; index++) owner = owner << 8 | (hash[index] & 0xffL);
        return owner;
    }

    private static void validateReferenceDelta(
            Mc263WoodlandMansionProducer.PersistedCarrier carrier) {
        byte[] predecessor = carrier.structureStart().bytes();
        byte[] mutable = carrier.mutableSuccessorAfterOneReference().bytes();
        require(predecessor.length == mutable.length, "Mansion successor length drift");
        int changed = -1;
        int differences = 0;
        for (int index = 0; index < predecessor.length; index++) {
            if (predecessor[index] != mutable[index]) {
                differences++;
                changed = index;
            }
        }
        require(differences == 1 && predecessor[changed] == 0 && mutable[changed] == 1,
                "Mansion references=0->1 successor is not one-byte exact");
    }

    private static SuccessorCarrier successor(Mc263WoodlandMansionProducer.PersistedCarrier carrier) {
        validateReferenceDelta(carrier);
        byte[] predecessor = carrier.structureStart().bytes();
        byte[] mutable = carrier.mutableSuccessorAfterOneReference().bytes();
        int changed = -1;
        for (int index = 0; index < predecessor.length; index++) {
            if (predecessor[index] != mutable[index]) {
                changed = index;
                break;
            }
        }
        return new SuccessorCarrier(predecessor, mutable, changed);
    }

    private static String transactionKey(Request request) {
        return STRUCTURE_KEY + "@" + request.startChunkX() + "," + request.startChunkZ()
                + "/destination@" + request.targetChunkX() + "," + request.targetChunkZ();
    }

    private static byte[] canonicalPayload(Settlement settlement) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            string(out, "MANP2AC1");
            string(out, P2R_P2T_AUTHORITY_SHA256);
            string(out, P2T_LIQUID_BLOCK_SHA256);
            string(out, settlement.transactionKey);
            string(out, settlement.request.structureKey);
            out.writeLong(settlement.request.worldSeed);
            out.writeInt(settlement.request.startChunkX);
            out.writeInt(settlement.request.startChunkZ);
            out.writeInt(settlement.request.targetChunkX);
            out.writeInt(settlement.request.targetChunkZ);
            out.writeLong(settlement.ownerId);
            out.writeLong(settlement.incomingLo);
            out.writeLong(settlement.incomingHi);
            out.writeInt(settlement.incomingCount);
            blob(out, settlement.successor.predecessor);
            blob(out, settlement.successor.mutableSuccessor);
            out.writeInt(settlement.successor.changedByteOffset);

            out.writeInt(settlement.writes.size());
            for (PublishedWrite write : settlement.writes) {
                out.writeInt(write.ordinal); string(out, write.cause.name()); position(out, write.position);
                string(out, write.exactState); out.writeInt(write.flags); string(out, write.relation.name());
            }
            out.writeInt(settlement.finalStates.size());
            for (FinalState state : settlement.finalStates) {
                position(out, state.position); string(out, state.exactState); string(out, state.relation.name());
            }
            out.writeInt(settlement.bent.size());
            for (BentPayload bent : settlement.bent) {
                position(out, bent.position); string(out, bent.exactState); string(out, bent.blockEntityType);
                blob(out, bent.canonicalNbt.binary);
            }
            out.writeInt(settlement.loot.size());
            for (LootPayload loot : settlement.loot) {
                position(out, loot.position); string(out, loot.table); out.writeLong(loot.seed);
            }
            out.writeInt(settlement.entities.size());
            for (EntityPayload entity : settlement.entities) {
                string(out, entity.marker); position(out, entity.markerPosition); string(out, entity.entityKey);
                string(out, entity.spawnReason); string(out, entity.runtimeSpawnReason);
                out.writeBoolean(entity.runtimeUuidExcluded); blob(out, entity.canonicalNbt.binary);
            }
            out.writeInt(settlement.blockTicks.size());
            for (BlockTick tick : settlement.blockTicks) {
                position(out, tick.position); string(out, tick.blockKey); out.writeInt(tick.delay);
                out.writeInt(tick.priority); out.writeLong(tick.subTickOrder);
            }
            out.writeInt(settlement.fluidTicks.size());
            for (FluidTick tick : settlement.fluidTicks) {
                position(out, tick.position); string(out, tick.fluidKey); string(out, tick.sourceState);
                out.writeInt(tick.delay); out.writeInt(tick.priority); out.writeBoolean(tick.inserted);
                out.writeLong(tick.subTickOrder);
            }
            out.writeInt(settlement.ownership.size());
            for (Ownership owner : settlement.ownership) {
                position(out, owner.position); out.writeLong(owner.ownerId);
            }
            out.writeLong(settlement.random.lo); out.writeLong(settlement.random.hi);
            out.writeInt(settlement.random.count); out.writeInt(settlement.random.continuation.size());
            for (long value : settlement.random.continuation) out.writeLong(value);
            serverRandomAuthority(out, SERVER_LEVEL_RANDOM_AUTHORITY);
            serverRandomState(out, settlement.incomingServerState);
            serverRandomState(out, settlement.serverRandom.state);
            out.writeInt(settlement.serverRandom.drawCount);
            out.writeInt(settlement.serverRandom.continuation.size());
            for (long value : settlement.serverRandom.continuation) out.writeLong(value);
            out.flush();
            return bytes.toByteArray();
        } catch (IOException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static void string(DataOutputStream out, String value) throws IOException {
        byte[] bytes = Objects.requireNonNull(value).getBytes(StandardCharsets.UTF_8);
        out.writeInt(bytes.length); out.write(bytes);
    }

    private static void blob(DataOutputStream out, byte[] value) throws IOException {
        out.writeInt(value.length); out.write(value);
    }

    private static void serverRandomAuthority(DataOutputStream out, ServerRandomAuthority value)
            throws IOException {
        string(out, value.implementationClass);
        string(out, value.implementationSha256);
        out.writeInt(value.stateWidthBits);
        string(out, value.source);
        string(out, value.sourceSha256);
        string(out, value.caller);
        string(out, value.callerSha256);
        string(out, value.factory);
        string(out, value.factorySha256);
        string(out, value.factoryKey);
        string(out, value.coreClass);
        string(out, value.coreSha256);
        string(out, value.gaussianClass);
        string(out, value.gaussianSha256);
    }

    private static void serverRandomState(DataOutputStream out, ServerRandomState value) throws IOException {
        out.writeLong(value.lo); out.writeLong(value.hi);
        out.writeBoolean(value.gaussianCached); out.writeLong(value.gaussianValueRawBits);
    }

    private static void position(DataOutputStream out, Position value) throws IOException {
        out.writeInt(value.x); out.writeInt(value.y); out.writeInt(value.z);
    }

    private static MessageDigest sha256Digest() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }

    private static String sha256(byte[] value) {
        return HexFormat.of().formatHex(sha256Digest().digest(value));
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }

    private static final class TransformKey {
        private final String state;
        private final Mc263WoodlandMansionGrammar.Mirror mirror;
        private final Mc263WoodlandMansionGrammar.Rotation rotation;
        TransformKey(String state, Mc263WoodlandMansionGrammar.Mirror mirror,
                Mc263WoodlandMansionGrammar.Rotation rotation) {
            this.state = canonicalState(state); this.mirror = mirror; this.rotation = rotation;
        }
        @Override public boolean equals(Object other) {
            return other instanceof TransformKey value && state.equals(value.state)
                    && mirror == value.mirror && rotation == value.rotation;
        }
        @Override public int hashCode() { return Objects.hash(state, mirror, rotation); }
    }

    private static final class Preflight {
        private final WorldTransaction transaction;
        private final Map<String, Mc263WoodlandMansionGrammar.Template> templates;
        private final Map<TransformKey, String> transforms;
        private final Set<String> preflightedStates;
        private final int minBuildY;
        Preflight(WorldTransaction transaction,
                Map<String, Mc263WoodlandMansionGrammar.Template> templates,
                Map<TransformKey, String> transforms, Set<String> preflightedStates, int minBuildY) {
            this.transaction = transaction; this.templates = templates; this.transforms = transforms;
            this.preflightedStates = preflightedStates; this.minBuildY = minBuildY;
        }
        Mc263WoodlandMansionGrammar.Template requireTemplate(String id) {
            Mc263WoodlandMansionGrammar.Template value = templates.get(id);
            if (value == null) throw new IllegalArgumentException("unpreflighted Mansion template: " + id);
            return value;
        }
        String transformedState(String source, Mc263WoodlandMansionGrammar.Mirror mirror,
                Mc263WoodlandMansionGrammar.Rotation rotation) {
            String value = transforms.get(new TransformKey(source, mirror, rotation));
            if (value == null) throw new IllegalArgumentException("unpreflighted Mansion state transform");
            return value;
        }
        String admitEffectState(String state) {
            String canonical = canonicalState(state);
            if (BARRIER.equals(canonical)) return canonical;
            require(preflightedStates.contains(canonical)
                            || transaction.authorityAdmitsExactState(canonical),
                    "Mansion runtime emitted state outside P2R authority: " + canonical);
            return canonical;
        }
    }

    private static final class Accumulator {
        private final Preflight preflight;
        private final Clip clip;
        private final long owner;
        private final ArrayList<PublishedWrite> writes = new ArrayList<>();
        private final LinkedHashMap<Position, FinalState> finalStates = new LinkedHashMap<>();
        private final LinkedHashMap<Position, BentPayload> bent = new LinkedHashMap<>();
        private final LinkedHashMap<Position, LootPayload> loot = new LinkedHashMap<>();
        private final ArrayList<EntityPayload> entities = new ArrayList<>();
        private final ArrayList<BlockTick> blockTicks = new ArrayList<>();
        private final ArrayList<FluidTick> fluidTicks = new ArrayList<>();
        private long lastInsertedFluidSubTickOrder = Long.MIN_VALUE;

        Accumulator(Preflight preflight, Clip clip, long owner) {
            this.preflight = preflight; this.clip = clip; this.owner = owner;
        }

        void acceptCell(CellPlacement placement, CellEffects effects) {
            Objects.requireNonNull(effects, "Mansion cell effects");
            boolean sawPrimary = false;
            for (WorldWrite write : effects.writes) {
                if (write.cause == WriteCause.PRIMARY && write.position.equals(placement.worldPosition)
                        && canonicalState(write.exactState).equals(placement.transformedState)
                        && write.flags == PRIMARY_WRITE_FLAGS) sawPrimary = true;
                acceptDirectWrite(write);
            }
            require(!effects.primarySucceeded || sawPrimary,
                    "successful Mansion template cell lacks exact primary write receipt");
            if (placement.semantic == null) {
                require(effects.bent.isEmpty(), "plain Mansion cell emitted BENT");
            } else {
                String expectedType = blockEntityType(placement.semantic);
                require(effects.bent.size() <= 1, "Mansion DATA cell emitted multiple BENT payloads");
                for (BentPayload payload : effects.bent) {
                    require(expectedType.equals(payload.blockEntityType)
                                    && payload.position.equals(placement.worldPosition),
                            "Mansion DATA BENT semantic/type drift");
                    acceptBent(payload);
                }
            }
            for (BlockTick tick : effects.blockTicks) acceptBlockTick(tick);
            for (FluidTick tick : effects.fluidTicks) acceptFluidTick(tick);
        }

        void acceptMarker(MarkerPlacement placement, MarkerEffects effects) {
            for (WorldWrite write : effects.writes) acceptDirectWrite(write);
            if (isChestMarker(placement.metadata)) {
                require(placement.lootSeed != null && effects.bent.size() == 1
                                && effects.loot.size() == 1 && effects.entities.isEmpty(),
                        "Mansion chest marker effect cardinality drift");
                BentPayload payload = effects.bent.get(0);
                require("minecraft:chest".equals(payload.blockEntityType)
                                && payload.position.equals(placement.position),
                        "Mansion chest marker BENT drift");
                acceptBent(payload);
                LootPayload lootPayload = effects.loot.get(0);
                require(lootPayload.position.equals(placement.position)
                                && LOOT_TABLE.equals(lootPayload.table)
                                && lootPayload.seed == placement.lootSeed.longValue(),
                        "Mansion chest marker LOOT drift");
                acceptLoot(lootPayload);
                require(hasMarkerChestWrite(effects.writes, placement.position),
                        "Mansion chest marker lacks chest write");
                return;
            }
            require(effects.bent.isEmpty() && effects.loot.isEmpty(),
                    "Mansion entity marker emitted BENT/LOOT");
            String expectedEntity;
            int minCount;
            int maxCount;
            if ("Mage".equals(placement.metadata)) {
                expectedEntity = "minecraft:evoker"; minCount = 1; maxCount = 1;
            } else if ("Warrior".equals(placement.metadata)) {
                expectedEntity = "minecraft:vindicator"; minCount = 1; maxCount = 1;
            } else if ("Group of Allays".equals(placement.metadata)) {
                expectedEntity = "minecraft:allay"; minCount = 1; maxCount = 3;
            } else {
                throw new IllegalArgumentException("unknown Mansion entity marker: " + placement.metadata);
            }
            require(effects.entities.size() >= minCount && effects.entities.size() <= maxCount,
                    "Mansion marker entity count drift");
            for (EntityPayload entity : effects.entities) {
                require(placement.metadata.equals(entity.marker)
                                && placement.position.equals(entity.markerPosition)
                                && expectedEntity.equals(entity.entityKey)
                                && "minecraft:structure".equals(entity.spawnReason)
                                && "STRUCTURE".equals(entity.runtimeSpawnReason)
                                && entity.runtimeUuidExcluded,
                        "Mansion marker entity authority drift");
                entities.add(entity);
            }
            require(hasMarkerClear(effects.writes, placement.position),
                    "Mansion entity marker lacks AIR clear");
        }

        private boolean hasMarkerChestWrite(List<WorldWrite> values, Position position) {
            for (WorldWrite value : values) {
                if (value.cause == WriteCause.MARKER_CHEST && value.position.equals(position)
                        && "minecraft:chest".equals(blockKey(value.exactState))) return true;
            }
            return false;
        }

        private boolean hasMarkerClear(List<WorldWrite> values, Position position) {
            for (WorldWrite value : values) {
                if (value.cause == WriteCause.MARKER_CLEAR && value.position.equals(position)
                        && AIR.equals(canonicalState(value.exactState))
                        && value.flags == PRIMARY_WRITE_FLAGS) return true;
            }
            return false;
        }

        void acceptDirectWrite(WorldWrite write) {
            Objects.requireNonNull(write, "Mansion world write");
            String state = preflight.admitEffectState(write.exactState);
            require(write.flags == EDGE_UPDATE_FLAGS || write.flags == PRIMARY_WRITE_FLAGS
                            || write.flags == NEIGHBOR_CORRECTION_FLAGS || write.flags == NBT_BARRIER_FLAGS,
                    "Mansion runtime emitted unpreflighted write flags: " + write.flags);
            if (write.cause == WriteCause.NBT_BARRIER) {
                require(BARRIER.equals(state) && write.flags == NBT_BARRIER_FLAGS,
                        "Mansion NBT barrier write drift");
            } else if (write.cause == WriteCause.PRIMARY) {
                require(write.flags == PRIMARY_WRITE_FLAGS, "Mansion primary write flags drift");
            } else if (write.cause == WriteCause.EDGE_INSIDE || write.cause == WriteCause.EDGE_OUTSIDE) {
                require(write.flags == EDGE_UPDATE_FLAGS, "Mansion edge update flags drift");
            } else if (write.cause == WriteCause.NEIGHBOR_CORRECTION) {
                require(write.flags == NEIGHBOR_CORRECTION_FLAGS,
                        "Mansion neighbor correction flags drift");
            } else if (write.cause == WriteCause.MARKER_CLEAR) {
                require(AIR.equals(state) && write.flags == PRIMARY_WRITE_FLAGS,
                        "Mansion marker-clear write drift");
            } else if (write.cause == WriteCause.FOUNDATION) {
                require(COBBLESTONE.equals(state) && write.flags == PRIMARY_WRITE_FLAGS,
                        "Mansion foundation write drift");
            }
            Relation relation = clip.relation(write.position);
            PublishedWrite published = new PublishedWrite(writes.size(), write.cause, write.position,
                    state, write.flags, relation);
            writes.add(published);
            finalStates.remove(write.position);
            finalStates.put(write.position, new FinalState(write.position, state, relation));
            bent.remove(write.position);
            loot.remove(write.position);
        }

        private void acceptBent(BentPayload payload) {
            Objects.requireNonNull(payload, "Mansion BENT payload");
            String state = preflight.admitEffectState(payload.exactState);
            FinalState finalState = finalStates.get(payload.position);
            require(finalState != null && finalState.exactState.equals(state)
                            && blockKey(state).equals(blockKeyForBlockEntity(
                                    payload.blockEntityType, state)),
                    "Mansion BENT does not match surviving block state");
            require(payload.canonicalNbt.binary.length >= 4
                            && payload.canonicalNbt.binary[0] == 10,
                    "Mansion canonical BENT NBT root drift");
            bent.put(payload.position, new BentPayload(payload.position, state,
                    payload.blockEntityType, payload.canonicalNbt));
        }

        private void acceptLoot(LootPayload payload) {
            require(LOOT_TABLE.equals(payload.table), "unknown Mansion loot table");
            require(finalStates.containsKey(payload.position), "Mansion LOOT lacks surviving cell");
            loot.put(payload.position, payload);
        }

        private void acceptBlockTick(BlockTick tick) {
            require(LEAVES.equals(tick.blockKey) && tick.delay == LEAVES_DELAY
                            && tick.priority == NORMAL_PRIORITY && tick.subTickOrder >= 0,
                    "unknown Mansion block-tick authority");
            blockTicks.add(tick);
        }

        private void acceptFluidTick(FluidTick tick) {
            require(WATER.equals(tick.fluidKey) && WATER_SOURCE_STATE.equals(tick.sourceState)
                            && tick.delay == WATER_DELAY && tick.priority == NORMAL_PRIORITY,
                    "unknown Mansion LiquidBlock fluid-tick authority");
            if (tick.inserted) {
                require(tick.subTickOrder >= 0 && tick.subTickOrder > lastInsertedFluidSubTickOrder,
                        "Mansion inserted FTIK subTickOrder is not strictly increasing");
                lastInsertedFluidSubTickOrder = tick.subTickOrder;
            } else {
                require(tick.subTickOrder == -1L,
                        "Mansion duplicate-unchanged FTIK must not invent subTickOrder");
            }
            fluidTicks.add(tick);
        }

        Settlement freeze(Request request, SuccessorCarrier successor, long incomingLo,
                long incomingHi, int incomingCount, RandomContinuation random,
                ServerRandomState incomingServerState, ServerRandomContinuation serverRandom) {
            ArrayList<Ownership> owners = new ArrayList<>(finalStates.size());
            for (FinalState state : finalStates.values()) owners.add(new Ownership(state.position, owner));
            return new Settlement(request, clip, owner, List.copyOf(writes),
                    List.copyOf(finalStates.values()), List.copyOf(bent.values()),
                    List.copyOf(loot.values()), List.copyOf(entities), List.copyOf(blockTicks),
                    List.copyOf(fluidTicks), List.copyOf(owners), successor, incomingLo, incomingHi,
                    incomingCount, random, incomingServerState, serverRandom);
        }
    }

    static String blockKeyForBlockEntity(String type, String exactState) {
        if ("minecraft:chest".equals(type)) return "minecraft:chest";
        if ("minecraft:trapped_chest".equals(type)) return "minecraft:trapped_chest";
        if ("minecraft:banner".equals(type)) {
            String key = blockKey(canonicalState(exactState));
            require(key.endsWith("_banner"),
                    "Mansion banner block entity does not belong to a banner state");
            return key;
        }
        if ("minecraft:mob_spawner".equals(type)) return "minecraft:spawner";
        throw new IllegalArgumentException("unknown Mansion block-entity authority: " + type);
    }

    public record ServerRandomAuthority(String implementationClass, String implementationSha256,
            int stateWidthBits, String source, String sourceSha256, String caller,
            String callerSha256, String factory, String factorySha256, String factoryKey,
            String coreClass, String coreSha256, String gaussianClass, String gaussianSha256) {
        public ServerRandomAuthority {
            Objects.requireNonNull(implementationClass); Objects.requireNonNull(implementationSha256);
            Objects.requireNonNull(source); Objects.requireNonNull(sourceSha256);
            Objects.requireNonNull(caller); Objects.requireNonNull(callerSha256);
            Objects.requireNonNull(factory); Objects.requireNonNull(factorySha256);
            Objects.requireNonNull(factoryKey); Objects.requireNonNull(coreClass);
            Objects.requireNonNull(coreSha256); Objects.requireNonNull(gaussianClass);
            Objects.requireNonNull(gaussianSha256);
            require(stateWidthBits > 0, "invalid Mansion ServerLevel RNG state width");
            require(List.of(implementationSha256, sourceSha256, callerSha256, factorySha256,
                    coreSha256, gaussianSha256).stream().allMatch(value -> value.matches("[0-9a-f]{64}")),
                    "invalid Mansion ServerLevel RNG source identity");
        }
    }

    public record ServerRandomState(long lo, long hi, boolean gaussianCached,
            long gaussianValueRawBits) {
        public ServerRandomState {
            require((lo | hi) != 0L, "invalid all-zero Mansion ServerLevel Xoroshiro state");
            require(gaussianCached || gaussianValueRawBits == 0L,
                    "noncanonical absent Mansion ServerLevel Gaussian cache");
            require(Double.isFinite(Double.longBitsToDouble(gaussianValueRawBits)),
                    "invalid Mansion ServerLevel Gaussian cache value");
        }
        public double gaussianValue() { return Double.longBitsToDouble(gaussianValueRawBits); }
    }

    public enum PublishStatus { COMMITTED, REPLAYED }
    public enum Relation { LOCAL, FOREIGN }
    public enum WriteCause {
        NBT_BARRIER, PRIMARY, FLUID_RESTORE, EDGE_INSIDE, EDGE_OUTSIDE,
        NEIGHBOR_CORRECTION, MARKER_CHEST, MARKER_CLEAR, FOUNDATION
    }

    /** All capability/authority methods and transformExactState must be pure. */
    public interface WorldTransaction extends Mc263WoodlandMansionProducer.WorldAccess {
        boolean supportsP2rP2tAuthority(String authoritySha256, String liquidBlockSha256,
                int liquidBlockBytes, int templateCount, int exactStateCount);
        int authorityTemplateCount();
        int authorityExactStateCount();
        boolean authorityAdmitsTemplate(String templateId);
        boolean authorityAdmitsExactState(String exactState);
        boolean authorityAdmitsProcessor(String processorAuthority);
        boolean authorityAdmitsOperation(String operation);
        boolean supportsPlacementRandom(long lo, long hi, int count);
        boolean supportsServerLevelRandom(ServerRandomAuthority authority, ServerRandomState state);
        boolean supportsAtomicForkPublishWithRandom();
        boolean supportsBuildHeightBoundary();
        int minBuildY();
        int maxBuildY();
        boolean supportsTemplateCellExecution();
        boolean supportsMarkerExecution();
        boolean supportsStateTransform();
        boolean supportsKeepLiquids();
        boolean supportsNeighborResolution();
        boolean supportsAfterPlaceExecution();
        boolean supportsCanonicalBlockEntityNbt();
        boolean supportsTypedBentLane();
        boolean supportsLootLane();
        boolean supportsStructureEntityLane();
        boolean supportsOwnerLane();
        boolean supportsSuccessorLane();
        boolean supportsFingerprintLane();
        boolean supportsBlockTickLane();
        boolean supportsFluidTickLane();
        boolean supportsEmptyBlockQueries();
        boolean supportsFluidStateQueries();
        boolean supportsSetBlock();
        boolean supportsWriteFlags(int flags);
        boolean supportsTransientState(String exactState);
        boolean supportsTemplate(String templateId);
        boolean supportsExactState(String exactState);
        boolean supportsNbtSemantic(String semanticKind, String blockEntityType);
        boolean supportsMarker(String metadata);
        boolean supportsStructureEntity(String entityKey);
        boolean supportsLootTable(String table);
        boolean supportsBlockTick(String blockKey, int delay, int priority);
        boolean supportsFluidTick(String fluidKey, String sourceState, int delay, int priority);
        String transformExactState(String sourceState, Mc263WoodlandMansionGrammar.Mirror mirror,
                Mc263WoodlandMansionGrammar.Rotation rotation);
        WorldTransaction fork();
        CellEffects executeTemplateCell(CellPlacement placement);
        MarkerEffects executeMarker(MarkerPlacement placement, ServerLevelRandom serverRandom);
        boolean isEmptyBlock(Position position);
        FluidState getFluidState(Position position);
        boolean setBlock(Position position, String exactState, int flags);
        /**
         * Atomically commits fork mutations plus all typed lanes, successor, fingerprint and caller
         * RNG replacement. Exact fingerprint+payload replay returns REPLAYED without duplicate lane
         * publication. Any conflicting payload for the same transaction key must throw before world
         * state or caller RNG changes. COMMITTED and REPLAYED both install acceptedRandom.
         */
        PublishStatus publishAtomically(WorldTransaction isolated, Settlement settlement,
                PlacementRandom callerRandom, PlacementRandom acceptedRandom,
                ServerLevelRandom callerServerRandom, ServerLevelRandom acceptedServerRandom);
    }

    public static final class Request {
        private final String structureKey;
        private final long worldSeed;
        private final int startChunkX, startChunkZ, targetChunkX, targetChunkZ;
        public Request(String structureKey, long worldSeed, int startChunkX, int startChunkZ,
                int targetChunkX, int targetChunkZ) {
            this.structureKey = Objects.requireNonNull(structureKey, "Mansion structure key");
            this.worldSeed = worldSeed; this.startChunkX = startChunkX; this.startChunkZ = startChunkZ;
            this.targetChunkX = targetChunkX; this.targetChunkZ = targetChunkZ;
        }
        public String structureKey() { return structureKey; }
        public long worldSeed() { return worldSeed; }
        public int startChunkX() { return startChunkX; }
        public int startChunkZ() { return startChunkZ; }
        public int targetChunkX() { return targetChunkX; }
        public int targetChunkZ() { return targetChunkZ; }
    }

    public static final class Position {
        private final int x, y, z;
        public Position(int x, int y, int z) { this.x = x; this.y = y; this.z = z; }
        public int x() { return x; } public int y() { return y; } public int z() { return z; }
        @Override public boolean equals(Object other) {
            return other instanceof Position value && x == value.x && y == value.y && z == value.z;
        }
        @Override public int hashCode() { return Objects.hash(x, y, z); }
        @Override public String toString() { return "[" + x + "," + y + "," + z + "]"; }
    }

    public static final class Clip {
        private final int chunkX, chunkZ, minX, minY, minZ, maxX, maxY, maxZ;
        private Clip(int chunkX, int chunkZ, int minX, int minY, int minZ,
                int maxX, int maxY, int maxZ) {
            this.chunkX = chunkX; this.chunkZ = chunkZ; this.minX = minX; this.minY = minY;
            this.minZ = minZ; this.maxX = maxX; this.maxY = maxY; this.maxZ = maxZ;
        }
        public int chunkX() { return chunkX; } public int chunkZ() { return chunkZ; }
        public int minX() { return minX; } public int minY() { return minY; }
        public int minZ() { return minZ; } public int maxX() { return maxX; }
        public int maxY() { return maxY; } public int maxZ() { return maxZ; }
        public boolean contains(Position position) {
            return position.x >= minX && position.x <= maxX && position.y >= minY
                    && position.y <= maxY && position.z >= minZ && position.z <= maxZ;
        }
        public Relation relation(Position position) {
            return Math.floorDiv(position.x, 16) == chunkX && Math.floorDiv(position.z, 16) == chunkZ
                    ? Relation.LOCAL : Relation.FOREIGN;
        }
    }

    public static final class FluidState {
        private final String fluidKey;
        private final boolean source, empty;
        public FluidState(String fluidKey, boolean source, boolean empty) {
            this.fluidKey = Objects.requireNonNull(fluidKey); this.source = source; this.empty = empty;
        }
        public String fluidKey() { return fluidKey; } public boolean source() { return source; }
        public boolean empty() { return empty; }
    }

    public static final class WorldWrite {
        private final WriteCause cause; private final Position position;
        private final String exactState; private final int flags;
        public WorldWrite(WriteCause cause, Position position, String exactState, int flags) {
            this.cause = Objects.requireNonNull(cause); this.position = Objects.requireNonNull(position);
            this.exactState = canonicalState(exactState); this.flags = flags;
        }
        public WriteCause cause() { return cause; } public Position position() { return position; }
        public String exactState() { return exactState; } public int flags() { return flags; }
    }

    public static final class CellPlacement {
        private final int pieceOrdinal, sourceOrdinal;
        private final String templateId, sourceState, transformedState;
        private final Position localPosition, worldPosition;
        private final Mc263WoodlandMansionGrammar.Mirror mirror;
        private final Mc263WoodlandMansionGrammar.Rotation rotation;
        private final Mc263WoodlandMansionGrammar.Semantic semantic;
        private final Long placementSeed;
        private final Clip clip;
        private CellPlacement(int pieceOrdinal, String templateId, int sourceOrdinal,
                Position localPosition, Position worldPosition, String sourceState, String transformedState,
                Mc263WoodlandMansionGrammar.Mirror mirror,
                Mc263WoodlandMansionGrammar.Rotation rotation,
                Mc263WoodlandMansionGrammar.Semantic semantic, Long placementSeed, Clip clip) {
            this.pieceOrdinal = pieceOrdinal; this.templateId = templateId; this.sourceOrdinal = sourceOrdinal;
            this.localPosition = localPosition; this.worldPosition = worldPosition;
            this.sourceState = canonicalState(sourceState); this.transformedState = canonicalState(transformedState);
            this.mirror = mirror; this.rotation = rotation; this.semantic = semantic;
            this.placementSeed = placementSeed; this.clip = clip;
        }
        public int pieceOrdinal() { return pieceOrdinal; } public String templateId() { return templateId; }
        public int sourceOrdinal() { return sourceOrdinal; } public Position localPosition() { return localPosition; }
        public Position worldPosition() { return worldPosition; } public String sourceState() { return sourceState; }
        public String transformedState() { return transformedState; }
        public Mc263WoodlandMansionGrammar.Mirror mirror() { return mirror; }
        public Mc263WoodlandMansionGrammar.Rotation rotation() { return rotation; }
        public Mc263WoodlandMansionGrammar.Semantic semantic() { return semantic; }
        public Long placementSeed() { return placementSeed; } public Clip clip() { return clip; }
    }

    public static final class CellEffects {
        private final boolean primarySucceeded;
        private final List<WorldWrite> writes;
        private final List<BentPayload> bent;
        private final List<BlockTick> blockTicks;
        private final List<FluidTick> fluidTicks;
        public CellEffects(boolean primarySucceeded, List<WorldWrite> writes, List<BentPayload> bent,
                List<BlockTick> blockTicks, List<FluidTick> fluidTicks) {
            this.primarySucceeded = primarySucceeded; this.writes = List.copyOf(writes);
            this.bent = List.copyOf(bent); this.blockTicks = List.copyOf(blockTicks);
            this.fluidTicks = List.copyOf(fluidTicks);
        }
        public boolean primarySucceeded() { return primarySucceeded; }
        public List<WorldWrite> writes() { return writes; } public List<BentPayload> bent() { return bent; }
        public List<BlockTick> blockTicks() { return blockTicks; } public List<FluidTick> fluidTicks() { return fluidTicks; }
    }

    public static final class MarkerPlacement {
        private final int pieceOrdinal, markerOrdinal;
        private final String templateId, metadata;
        private final Position position;
        private final Mc263WoodlandMansionGrammar.Mirror mirror;
        private final Mc263WoodlandMansionGrammar.Rotation rotation;
        private final Long lootSeed;
        private final Clip clip;
        private MarkerPlacement(int pieceOrdinal, String templateId, int markerOrdinal, String metadata,
                Position position, Mc263WoodlandMansionGrammar.Mirror mirror,
                Mc263WoodlandMansionGrammar.Rotation rotation, Long lootSeed, Clip clip) {
            this.pieceOrdinal = pieceOrdinal; this.templateId = templateId; this.markerOrdinal = markerOrdinal;
            this.metadata = metadata; this.position = position; this.mirror = mirror; this.rotation = rotation;
            this.lootSeed = lootSeed; this.clip = clip;
        }
        public int pieceOrdinal() { return pieceOrdinal; } public String templateId() { return templateId; }
        public int markerOrdinal() { return markerOrdinal; } public String metadata() { return metadata; }
        public Position position() { return position; }
        public Mc263WoodlandMansionGrammar.Mirror mirror() { return mirror; }
        public Mc263WoodlandMansionGrammar.Rotation rotation() { return rotation; }
        public Long lootSeed() { return lootSeed; } public Clip clip() { return clip; }
    }

    public static final class MarkerEffects {
        private final List<WorldWrite> writes;
        private final List<BentPayload> bent;
        private final List<LootPayload> loot;
        private final List<EntityPayload> entities;
        public MarkerEffects(List<WorldWrite> writes, List<BentPayload> bent,
                List<LootPayload> loot, List<EntityPayload> entities) {
            this.writes = List.copyOf(writes); this.bent = List.copyOf(bent);
            this.loot = List.copyOf(loot); this.entities = List.copyOf(entities);
        }
        public List<WorldWrite> writes() { return writes; } public List<BentPayload> bent() { return bent; }
        public List<LootPayload> loot() { return loot; } public List<EntityPayload> entities() { return entities; }
    }

    public static final class CanonicalNbt {
        private final byte[] binary;
        private final String sha256;
        public CanonicalNbt(byte[] binary) {
            this.binary = Objects.requireNonNull(binary).clone();
            require(this.binary.length >= 4 && this.binary[0] == 10,
                    "invalid Mansion canonical NBT root");
            this.sha256 = Mc263WoodlandMansionSettlement.sha256(this.binary);
        }
        public byte[] binary() { return binary.clone(); }
        public String sha256() { return sha256; }
    }

    public static final class BentPayload {
        private final Position position;
        private final String exactState, blockEntityType;
        private final CanonicalNbt canonicalNbt;
        public BentPayload(Position position, String exactState, String blockEntityType, CanonicalNbt canonicalNbt) {
            this.position = Objects.requireNonNull(position); this.exactState = canonicalState(exactState);
            this.blockEntityType = Objects.requireNonNull(blockEntityType); this.canonicalNbt = Objects.requireNonNull(canonicalNbt);
        }
        public Position position() { return position; } public String exactState() { return exactState; }
        public String blockEntityType() { return blockEntityType; } public CanonicalNbt canonicalNbt() { return canonicalNbt; }
    }

    public static final class LootPayload {
        private final Position position; private final String table; private final long seed;
        public LootPayload(Position position, String table, long seed) {
            this.position = Objects.requireNonNull(position); this.table = Objects.requireNonNull(table); this.seed = seed;
        }
        public Position position() { return position; } public String table() { return table; } public long seed() { return seed; }
    }

    public static final class EntityPayload {
        private final String marker; private final Position markerPosition;
        private final String entityKey, spawnReason, runtimeSpawnReason;
        private final CanonicalNbt canonicalNbt; private final boolean runtimeUuidExcluded;
        public EntityPayload(String marker, Position markerPosition, String entityKey, String spawnReason,
                String runtimeSpawnReason, CanonicalNbt canonicalNbt, boolean runtimeUuidExcluded) {
            this.marker = Objects.requireNonNull(marker); this.markerPosition = Objects.requireNonNull(markerPosition);
            this.entityKey = Objects.requireNonNull(entityKey); this.spawnReason = Objects.requireNonNull(spawnReason);
            this.runtimeSpawnReason = Objects.requireNonNull(runtimeSpawnReason);
            this.canonicalNbt = Objects.requireNonNull(canonicalNbt); this.runtimeUuidExcluded = runtimeUuidExcluded;
        }
        public String marker() { return marker; } public Position markerPosition() { return markerPosition; }
        public String entityKey() { return entityKey; } public String spawnReason() { return spawnReason; }
        public String runtimeSpawnReason() { return runtimeSpawnReason; }
        public CanonicalNbt canonicalNbt() { return canonicalNbt; }
        public boolean runtimeUuidExcluded() { return runtimeUuidExcluded; }
    }

    public static final class BlockTick {
        private final Position position; private final String blockKey;
        private final int delay, priority; private final long subTickOrder;
        public BlockTick(Position position, String blockKey, int delay, int priority, long subTickOrder) {
            this.position = Objects.requireNonNull(position); this.blockKey = Objects.requireNonNull(blockKey);
            this.delay = delay; this.priority = priority; this.subTickOrder = subTickOrder;
        }
        public Position position() { return position; } public String blockKey() { return blockKey; }
        public int delay() { return delay; } public int priority() { return priority; }
        public long subTickOrder() { return subTickOrder; }
    }

    private static final class BlockTickIdentity {
        private final Position position;
        private final String blockKey;

        private BlockTickIdentity(Position position, String blockKey) {
            this.position = position;
            this.blockKey = blockKey;
        }

        @Override public boolean equals(Object other) {
            return other instanceof BlockTickIdentity value
                    && position.equals(value.position) && blockKey.equals(value.blockKey);
        }

        @Override public int hashCode() { return Objects.hash(position, blockKey); }
    }

    public static final class FluidTick {
        private final Position position; private final String fluidKey, sourceState;
        private final int delay, priority; private final boolean inserted; private final long subTickOrder;
        public FluidTick(Position position, String fluidKey, String sourceState, int delay, int priority,
                boolean inserted, long subTickOrder) {
            this.position = Objects.requireNonNull(position); this.fluidKey = Objects.requireNonNull(fluidKey);
            this.sourceState = canonicalState(sourceState); this.delay = delay; this.priority = priority;
            this.inserted = inserted; this.subTickOrder = subTickOrder;
        }
        public Position position() { return position; } public String fluidKey() { return fluidKey; }
        public String sourceState() { return sourceState; } public int delay() { return delay; }
        public int priority() { return priority; } public boolean inserted() { return inserted; }
        public long subTickOrder() { return subTickOrder; }
    }

    public static final class PublishedWrite {
        private final int ordinal; private final WriteCause cause; private final Position position;
        private final String exactState; private final int flags; private final Relation relation;
        private PublishedWrite(int ordinal, WriteCause cause, Position position, String exactState,
                int flags, Relation relation) {
            this.ordinal = ordinal; this.cause = cause; this.position = position;
            this.exactState = exactState; this.flags = flags; this.relation = relation;
        }
        public int ordinal() { return ordinal; } public WriteCause cause() { return cause; }
        public Position position() { return position; } public String exactState() { return exactState; }
        public int flags() { return flags; } public Relation relation() { return relation; }
    }

    public static final class FinalState {
        private final Position position; private final String exactState; private final Relation relation;
        private FinalState(Position position, String exactState, Relation relation) {
            this.position = position; this.exactState = exactState; this.relation = relation;
        }
        public Position position() { return position; } public String exactState() { return exactState; }
        public Relation relation() { return relation; }
    }

    public static final class Ownership {
        private final Position position; private final long ownerId;
        private Ownership(Position position, long ownerId) { this.position = position; this.ownerId = ownerId; }
        public Position position() { return position; } public long ownerId() { return ownerId; }
    }

    public static final class SuccessorCarrier {
        private final byte[] predecessor, mutableSuccessor;
        private final String predecessorSha256, mutableSuccessorSha256;
        private final int changedByteOffset;
        private SuccessorCarrier(byte[] predecessor, byte[] mutableSuccessor, int changedByteOffset) {
            this.predecessor = predecessor.clone(); this.mutableSuccessor = mutableSuccessor.clone();
            this.predecessorSha256 = sha256(this.predecessor);
            this.mutableSuccessorSha256 = sha256(this.mutableSuccessor);
            this.changedByteOffset = changedByteOffset;
        }
        public byte[] predecessor() { return predecessor.clone(); }
        public byte[] mutableSuccessor() { return mutableSuccessor.clone(); }
        public String predecessorSha256() { return predecessorSha256; }
        public String mutableSuccessorSha256() { return mutableSuccessorSha256; }
        public int changedByteOffset() { return changedByteOffset; }
    }

    public static final class RandomContinuation {
        private final long lo, hi; private final int count; private final List<Long> continuation;
        private RandomContinuation(long lo, long hi, int count, List<Long> continuation) {
            this.lo = lo; this.hi = hi; this.count = count; this.continuation = List.copyOf(continuation);
            require(count >= 0 && this.continuation.size() == 8, "invalid Mansion caller RNG continuation");
        }
        public long lo() { return lo; } public long hi() { return hi; } public int count() { return count; }
        public List<Long> continuationNextLongI64() { return continuation; }
    }

    public static final class ServerRandomContinuation {
        private final ServerRandomState state; private final int drawCount;
        private final List<Long> continuation;
        private ServerRandomContinuation(ServerRandomState state, int drawCount,
                List<Long> continuation) {
            this.state = Objects.requireNonNull(state); this.drawCount = drawCount;
            this.continuation = List.copyOf(continuation);
            require(drawCount >= 0, "invalid Mansion ServerLevel RNG draw count");
            require(this.continuation.size() == 8, "invalid Mansion ServerLevel RNG continuation");
        }
        public ServerRandomAuthority authority() { return SERVER_LEVEL_RANDOM_AUTHORITY; }
        public String implementationClass() { return SERVER_LEVEL_RANDOM_IMPLEMENTATION; }
        public int stateWidthBits() { return SERVER_LEVEL_RANDOM_STATE_WIDTH_BITS; }
        public String source() { return SERVER_LEVEL_RANDOM_SOURCE; }
        public ServerRandomState state() { return state; }
        public long lo() { return state.lo(); } public long hi() { return state.hi(); }
        public boolean gaussianCached() { return state.gaussianCached(); }
        public long gaussianValueRawBits() { return state.gaussianValueRawBits(); }
        /** Raw Xoroshiro transitions consumed since this transaction candidate was opened. */
        public int drawCount() { return drawCount; }
        public List<Long> continuationNextLongI64() { return continuation; }
    }

    public static final class Settlement {
        private final Request request; private final Clip clip; private final long ownerId;
        private final List<PublishedWrite> writes; private final List<FinalState> finalStates;
        private final List<BentPayload> bent; private final List<LootPayload> loot;
        private final List<EntityPayload> entities; private final List<BlockTick> blockTicks;
        private final List<FluidTick> fluidTicks; private final List<Ownership> ownership;
        private final SuccessorCarrier successor;
        private final long incomingLo, incomingHi; private final int incomingCount;
        private final RandomContinuation random; private final ServerRandomState incomingServerState;
        private final ServerRandomContinuation serverRandom; private final String transactionKey;
        private final byte[] canonicalPayload; private final String fingerprint;
        private Settlement(Request request, Clip clip, long ownerId, List<PublishedWrite> writes,
                List<FinalState> finalStates, List<BentPayload> bent, List<LootPayload> loot,
                List<EntityPayload> entities, List<BlockTick> blockTicks, List<FluidTick> fluidTicks,
                List<Ownership> ownership, SuccessorCarrier successor, long incomingLo,
                long incomingHi, int incomingCount, RandomContinuation random,
                ServerRandomState incomingServerState, ServerRandomContinuation serverRandom) {
            this.request = request; this.clip = clip; this.ownerId = ownerId; this.writes = writes;
            this.finalStates = finalStates; this.bent = bent; this.loot = loot; this.entities = entities;
            this.blockTicks = blockTicks; this.fluidTicks = fluidTicks; this.ownership = ownership;
            this.successor = successor; this.incomingLo = incomingLo; this.incomingHi = incomingHi;
            this.incomingCount = incomingCount; this.random = random;
            this.incomingServerState = Objects.requireNonNull(incomingServerState);
            this.serverRandom = Objects.requireNonNull(serverRandom);
            this.transactionKey = Mc263WoodlandMansionSettlement.transactionKey(request);
            this.canonicalPayload = Mc263WoodlandMansionSettlement.canonicalPayload(this);
            this.fingerprint = sha256(this.canonicalPayload);
        }
        public Request request() { return request; } public Clip clip() { return clip; }
        public long ownerId() { return ownerId; } public List<PublishedWrite> writes() { return writes; }
        public List<FinalState> finalStates() { return finalStates; } public List<BentPayload> bent() { return bent; }
        public List<LootPayload> loot() { return loot; } public List<EntityPayload> entities() { return entities; }
        public List<BlockTick> blockTicks() { return blockTicks; } public List<FluidTick> fluidTicks() { return fluidTicks; }
        public List<Ownership> ownership() { return ownership; } public SuccessorCarrier successor() { return successor; }
        public RandomContinuation random() { return random; }
        public ServerRandomState incomingServerState() { return incomingServerState; }
        public ServerRandomContinuation serverRandom() { return serverRandom; }
        public String transactionKey() { return transactionKey; }
        public String fingerprint() { return fingerprint; } public byte[] canonicalPayload() { return canonicalPayload.clone(); }
        public String canonicalPayloadSha256() { return fingerprint; }
    }

    /** Caller-owned exact Xoroshiro128++ state. No seeding/reset factory is exposed. */
    public static final class PlacementRandom {
        private Xoroshiro source;
        private int count;
        private PlacementRandom(Xoroshiro source, int count) { this.source = source; this.count = count; }
        public static PlacementRandom fromState(long lo, long hi, int count) {
            if (count < 0) throw new IllegalArgumentException("negative Mansion RNG count");
            return new PlacementRandom(new Xoroshiro(lo, hi), count);
        }
        /** Exact {@code WorldgenRandom#setFeatureSeed} Xoroshiro body with retained wrapper count. */
        public static PlacementRandom fromSeed(long seed, int count) {
            long lo = seed ^ 0x6A09E667F3BCC909L;
            long hi = lo + 0x9E3779B97F4A7C15L;
            return fromState(McRandom.mixStafford13(lo), McRandom.mixStafford13(hi), count);
        }
        public long nextLong() { return ((long) next(32) << 32) + next(32); }
        private int next(int bits) { count++; return (int) (source.nextLong() >>> (64 - bits)); }
        public PlacementRandom copy() { return new PlacementRandom(source.copy(), count); }
        public void replaceWith(PlacementRandom value) {
            Objects.requireNonNull(value); source = value.source.copy(); count = value.count;
        }
        public long lo() { return source.lo; } public long hi() { return source.hi; } public int count() { return count; }
        public boolean sameState(PlacementRandom value) {
            return value != null && lo() == value.lo() && hi() == value.hi() && count == value.count;
        }
        public List<Long> continuationNextLongI64() {
            Xoroshiro copy = source.copy(); ArrayList<Long> result = new ArrayList<>(8);
            for (int index = 0; index < 8; index++) result.add(copy.nextLong());
            return List.copyOf(result);
        }
    }


    /** Caller-owned exact WorldGenRegion Xoroshiro128++ state plus Marsaglia Gaussian cache. */
    public static final class ServerLevelRandom {
        private Xoroshiro source;
        private boolean gaussianCached;
        private long gaussianValueRawBits;
        private int drawCount;

        private ServerLevelRandom(ServerRandomState state, int drawCount) {
            this.source = new Xoroshiro(state.lo(), state.hi());
            this.gaussianCached = state.gaussianCached();
            this.gaussianValueRawBits = state.gaussianValueRawBits();
            require(drawCount >= 0, "negative Mansion ServerLevel RNG draw count");
            this.drawCount = drawCount;
        }

        public static ServerLevelRandom fromAuthenticatedState(ServerRandomAuthority authority,
                ServerRandomState state, List<Long> continuationNextLongI64) {
            require(SERVER_LEVEL_RANDOM_AUTHORITY.equals(Objects.requireNonNull(authority)),
                    "unrecognized Mansion ServerLevel RNG source authority");
            ServerLevelRandom result = new ServerLevelRandom(Objects.requireNonNull(state), 0);
            List<Long> expected = List.copyOf(Objects.requireNonNull(
                    continuationNextLongI64, "Mansion ServerLevel RNG continuation"));
            require(expected.size() == 8, "invalid Mansion ServerLevel RNG continuation width");
            require(result.continuation().continuationNextLongI64().equals(expected),
                    "Mansion ServerLevel RNG continuation mismatch");
            return result;
        }

        public static ServerLevelRandom fromAuthenticatedState(ServerRandomState state,
                List<Long> continuationNextLongI64) {
            return fromAuthenticatedState(SERVER_LEVEL_RANDOM_AUTHORITY, state, continuationNextLongI64);
        }

        public ServerRandomState state() {
            return new ServerRandomState(source.lo, source.hi, gaussianCached, gaussianValueRawBits);
        }

        private long nextRawLong() {
            try {
                drawCount = Math.incrementExact(drawCount);
            } catch (ArithmeticException error) {
                throw new IllegalStateException("Mansion ServerLevel RNG draw count overflow", error);
            }
            return source.nextLong();
        }

        private int nextInt() { return (int) nextRawLong(); }

        public int nextInt(int bound) {
            if (bound <= 0) throw new IllegalArgumentException("non-positive Mansion ServerLevel RNG bound");
            long random = Integer.toUnsignedLong(nextInt());
            long product = random * bound;
            long low = product & 0xffff_ffffL;
            if (low < bound) {
                int threshold = Integer.remainderUnsigned(-bound, bound);
                while (low < threshold) {
                    random = Integer.toUnsignedLong(nextInt());
                    product = random * bound;
                    low = product & 0xffff_ffffL;
                }
            }
            return (int) (product >>> 32);
        }

        public float nextFloat() { return (float) (nextRawLong() >>> 40) * 0x1.0p-24F; }
        public double nextDouble() { return (nextRawLong() >>> 11) * 0x1.0p-53; }
        public double triangle(double mean, double deviation) {
            return mean + deviation * (nextDouble() - nextDouble());
        }

        public double nextGaussian() {
            if (gaussianCached) {
                long cachedValueRawBits = gaussianValueRawBits;
                gaussianCached = false;
                gaussianValueRawBits = 0L;
                return Double.longBitsToDouble(cachedValueRawBits);
            }
            while (true) {
                double x = 2.0D * nextDouble() - 1.0D;
                double y = 2.0D * nextDouble() - 1.0D;
                double radiusSquared = x * x + y * y;
                if (radiusSquared >= 1.0D || radiusSquared == 0.0D) continue;
                double multiplier = Math.sqrt(-2.0D * Math.log(radiusSquared) / radiusSquared);
                gaussianValueRawBits = Double.doubleToRawLongBits(y * multiplier);
                gaussianCached = true;
                return x * multiplier;
            }
        }

        public long nextLong() { return nextRawLong(); }

        public ServerLevelRandom copy() { return new ServerLevelRandom(state(), drawCount); }
        public void replaceWith(ServerLevelRandom value) {
            ServerLevelRandom accepted = Objects.requireNonNull(value, "Mansion ServerLevel random");
            source = accepted.source.copy();
            gaussianCached = accepted.gaussianCached;
            gaussianValueRawBits = accepted.gaussianValueRawBits;
            drawCount = accepted.drawCount;
        }
        public boolean sameState(ServerLevelRandom value) {
            return value != null && state().equals(value.state()) && drawCount == value.drawCount;
        }
        public ServerRandomContinuation continuation() {
            ServerRandomState state = state();
            ServerLevelRandom copy = copy();
            ArrayList<Long> result = new ArrayList<>(8);
            for (int index = 0; index < 8; index++) result.add(copy.nextLong());
            return new ServerRandomContinuation(state, drawCount, result);
        }
    }

    private static final class Xoroshiro {
        private static final long SILVER = 0x6A09E667F3BCC909L;
        private static final long GOLDEN = 0x9E3779B97F4A7C15L;
        private long lo, hi;
        Xoroshiro(long lo, long hi) {
            if ((lo | hi) == 0L) { this.lo = GOLDEN; this.hi = SILVER; }
            else { this.lo = lo; this.hi = hi; }
        }
        long nextLong() {
            long first = lo, second = hi, value = Long.rotateLeft(first + second, 17) + first;
            second ^= first; lo = Long.rotateLeft(first, 49) ^ second ^ second << 21;
            hi = Long.rotateLeft(second, 28); return value;
        }
        Xoroshiro copy() { return new Xoroshiro(lo, hi); }
    }
}
