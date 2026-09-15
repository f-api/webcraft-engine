package com.gameexpert.terrain.mc.structure;

import com.gameexpert.terrain.mc.feature.Mc263FeatureBlockState;
import com.gameexpert.terrain.mc.structure.Mc263JigsawStructureBoundary.ElementType;
import com.gameexpert.terrain.mc.structure.Mc263JigsawStructureBoundary.Rotation;
import com.gameexpert.terrain.mc.structure.Mc263JigsawStructureExecutor.PiecePlacement;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalLong;

/**
 * Dormant Trial Chambers settlement seam for the two authenticated 26.3 start probes.
 *
 * <p>The class deliberately owns no canonical caller. It regenerates the accepted start through
 * {@link Mc263TrialChambersProducer}, expands only the authenticated procedural grammar, performs
 * processor queries before clipped placement exactly as {@code StructureTemplate.placeInWorld}
 * does, and hands one immutable predecessor/successor plus semantic payload to an atomic committer.
 * No coordinate corpus, template NBT payload, or externally supplied start is accepted.</p>
 */
public final class Mc263TrialChambersSettlement {
    public static final String STRUCTURE_KEY = "minecraft:trial_chambers";
    public static final int CLIP_MIN_Y = -63;
    public static final int CLIP_MAX_Y = 319;

    private static final int CHUNK_SIZE = 16;
    private static final String COPPER_PROCESSOR =
            "minecraft:trial_chambers_copper_bulb_degradation";
    static final int REFERENCE_BYTES = 86_075;
    static final String REFERENCE_SHA256 =
            "f1ffe956c39448e25a070933044e906d21980c1319a18609f8a83b091b0d4036";
    private static final int REFERENCES_CHANGED_OFFSET = 19;
    private static final long RANDOM_MULTIPLIER = 0x5DEECE66DL;
    private static final long RANDOM_ADDEND = 0xBL;
    private static final long RANDOM_MASK = (1L << 48) - 1;

    private static final EvidenceIdentity EVIDENCE_IDENTITY = new EvidenceIdentity(
            4_992_879,
            "6d4114be756f82d219ba10dc735d6fb5d372fbd45332a0e25be8e25216255e89",
            824_547,
            "a4c13582e3817975371b8bdb4ff69820738cd57f782867f729a07372b8c85b4d",
            13_208,
            "e0982cbd3dbd72bc47894cc5221f5512234b7a5f3e689ddc7b94657640045229",
            REFERENCE_BYTES, REFERENCE_SHA256);

    private static final Witness FIRST = new Witness(
            Long.MIN_VALUE, 0, 0, -6, -6,
            new Mc263TrialChambersProducer.Box(-89, -50, -93, 59, 9, 42), 248,
            139_239,
            "9d66e8bb586cbf39cca4dca2c4e89a58ddca9577d5996dcbb73599343efa700a",
            "2246f8a0207d5237e4fa5dc28744fa2954b7b22ce0b6f18f817698a145e44371");
    private static final Witness SECOND = new Witness(
            Long.MIN_VALUE, 3, 1, -2, -5,
            new Mc263TrialChambersProducer.Box(-30, -65, -76, 107, 24, 57), 239,
            134_056,
            "baf22e997f942d87ab40b28a8bca9369da63fa396381755b6f067190b2ec902b",
            "d4a3e2d0d676453fccaa592329f2605a23073fd60b0ab5661d5de21bbcd7c00f");

    private Mc263TrialChambersSettlement() {
        throw new AssertionError("no instances");
    }

    /** Exact resource identities consumed by this dormant seam. */
    public static EvidenceIdentity evidenceIdentity() {
        PinnedHolder.PINNED.requireHealthy();
        return EVIDENCE_IDENTITY;
    }

    /**
     * Prepares and atomically commits one target chunk. Capability checks happen before any world
     * query. A failure before {@link Committer#commitAtomically(Commit)} has no mutation surface.
     */
    public static CommitResult settle(Request request, WorldAccess world, Committer committer) {
        Objects.requireNonNull(request, "Trial settlement request");
        Objects.requireNonNull(world, "Trial settlement world");
        Objects.requireNonNull(committer, "Trial settlement committer");

        if (!world.supportsProtectedBlockQueries()
                || !world.supportsClip(CLIP_MIN_Y, CLIP_MAX_Y)
                || !committer.supportsAtomicReplayableCommit()) {
            throw new IllegalArgumentException("Trial settlement capability preflight failed");
        }

        Pinned pinned = PinnedHolder.PINNED;
        Witness witness = witness(request.worldSeed(), request.startChunkX(), request.startChunkZ());
        Mc263TrialChambersProducer.Start start = regenerateStart(pinned.producer(), request);
        requireStartIdentity(start, witness);
        boolean exactReferenceWitness = request.targetChunkX() == witness.referenceTargetChunkX()
                && request.targetChunkZ() == witness.referenceTargetChunkZ();
        boolean pieceTarget = intersectsAnyPiece(start, request.targetChunkX(), request.targetChunkZ());
        if (!exactReferenceWitness && !pieceTarget) {
            throw new IllegalArgumentException(
                    "unauthenticated Trial target: only G1T reference witnesses or piece-intersecting chunks are admitted");
        }

        validateExecutionPreflight(pinned, start, request);
        Legacy48 caller = Legacy48.fromInternalState(start.generationRng().state48());
        requireContinuation(start.generationRng(), caller.copy());
        long initialCallerState48 = caller.state();
        long owner = owner(STRUCTURE_KEY, startKey(request.startChunkX(), request.startChunkZ()));

        ArrayList<Query> queries = new ArrayList<>();
        ArrayList<Write> writes = new ArrayList<>();
        LinkedHashMap<Position, FinalContribution> finalContributions = new LinkedHashMap<>();
        long[] writeOrdinal = {0L};
        int[] callerDrawCount = {0};

        int pieceOrdinal = 0;
        for (PiecePlacement piece : start.executionPlan().pieces()) {
            if (pieceOrdinal != pieceOrdinal(piece, pieceOrdinal)) {
                throw new IllegalStateException("Trial piece encounter order drift");
            }
            if (!intersects(piece, request.targetChunkX(), request.targetChunkZ())) {
                pieceOrdinal++;
                continue;
            }
            Mc263TrialChambersGrammar.Template template = pinned.template(piece.elementKey());
            processPiece(pinned, request, world, pieceOrdinal, piece, template, owner, caller,
                    queries, writes, finalContributions, writeOrdinal, callerDrawCount);
            pieceOrdinal++;
        }

        List<FinalContribution> surviving = finalContributions.values().stream()
                .sorted(Comparator.comparingLong(FinalContribution::writeOrdinal)).toList();
        ArrayList<Loot> loot = new ArrayList<>();
        ArrayList<Bent> bent = new ArrayList<>();
        ArrayList<Ownership> ownership = new ArrayList<>();
        ArrayList<FinalCell> finalCells = new ArrayList<>();
        for (FinalContribution value : surviving) {
            if (value.loot() != null) loot.add(value.loot());
            if (value.bent() != null) bent.add(value.bent());
            Write write = value.write();
            ownership.add(new Ownership(write.blockX(), write.blockY(), write.blockZ(), write.owner()));
            finalCells.add(new FinalCell(write.blockX(), write.blockY(), write.blockZ(),
                    write.exactState(), write.owner(), write.pieceOrdinal(), write.template(),
                    write.sourceBlockOrdinal()));
        }

        Settlement payload = new Settlement(writes, loot, List.of(), bent, List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), ownership, finalCells,
                List.copyOf(queries));
        Carrier carrier = successor(start, witness);
        Legacy48 continuation = caller.copy();
        List<Long> continuationNextLongI64 = previewNextLongs(continuation, 8);
        String transactionKey = STRUCTURE_KEY + "@" + request.startChunkX() + ","
                + request.startChunkZ() + "->" + request.targetChunkX() + ","
                + request.targetChunkZ();
        String fingerprint = fingerprint(transactionKey, request, carrier, payload,
                initialCallerState48, caller.state(), callerDrawCount[0], continuationNextLongI64);
        Commit commit = new Commit(transactionKey, request, exactReferenceWitness, pieceTarget,
                carrier, payload, initialCallerState48, caller.state(), callerDrawCount[0],
                continuationNextLongI64, fingerprint);

        CommitResult result = Objects.requireNonNull(committer.commitAtomically(commit),
                "Trial atomic commit result");
        if (result != CommitResult.APPLIED && result != CommitResult.REPLAYED) {
            throw new IllegalStateException("unknown Trial atomic commit result: " + result);
        }
        return result;
    }

    static String rotateStateForTest(String state, Rotation rotation) {
        return rotateState(state, rotation);
    }

    /** Canonical DATA-derived block-entity payload shared by the production transaction. */
    static byte[] canonicalBentNbtForProduction(Mc263TrialChambersGrammar.Command command,
            int x, int y, int z, long lootSeed) {
        PinnedHolder.PINNED.requireHealthy();
        return canonicalBentNbt(command, new Position(x, y, z), lootSeed);
    }

    /** Whether official template placement consumes one caller {@code nextLong()} for this DATA row. */
    static boolean usesCallerNextLongForProduction(Mc263TrialChambersGrammar.Command command) {
        PinnedHolder.PINNED.requireHealthy();
        return usesCallerNextLong(command);
    }

    private static int pieceOrdinal(PiecePlacement piece, int expected) {
        if (piece.type() != ElementType.SINGLE || piece.components().size() > 1) {
            throw new IllegalArgumentException("Trial settlement accepts SINGLE pieces only");
        }
        return expected;
    }

    private static void validateExecutionPreflight(Pinned pinned,
            Mc263TrialChambersProducer.Start start, Request request) {
        int pieceOrdinal = 0;
        for (PiecePlacement piece : start.executionPlan().pieces()) {
            pieceOrdinal(piece, pieceOrdinal);
            if (!intersects(piece, request.targetChunkX(), request.targetChunkZ())) {
                pieceOrdinal++;
                continue;
            }
            if (!piece.processor().isEmpty() && !COPPER_PROCESSOR.equals(piece.processor())) {
                throw new IllegalArgumentException("unknown Trial processor: " + piece.processor());
            }
            Mc263TrialChambersGrammar.Template template = pinned.template(piece.elementKey());
            if (template.entityCount() != 0 || !template.entities().isEmpty()) {
                throw new IllegalArgumentException("unauthenticated Trial template ENTS");
            }
            for (SourceBlock source : expand(template)) {
                if (source.command() instanceof Mc263TrialChambersGrammar.IgnoredStructureBlock) {
                    continue;
                }
                String state = baseProcessedState(template, source, piece.rotation());
                if (state == null || blockKey(state).equals("minecraft:structure_block")) continue;
                requireExactState(state);
                if (COPPER_PROCESSOR.equals(piece.processor())
                        && blockKey(state).equals("minecraft:waxed_copper_bulb")) {
                    for (Mc263TrialChambersGrammar.RuleSemanticRow row : pinned.copperRules()) {
                        requireExactState(row.outputState());
                    }
                }
                if (isBent(source.command())) {
                    String expected = blockEntityType(source.command()).id();
                    if (!blockKey(state).equals(expected)) {
                        throw new IllegalArgumentException("Trial BENT state/type mismatch: "
                                + state + " / " + expected);
                    }
                }
            }
            pieceOrdinal++;
        }
    }

    private static void processPiece(Pinned pinned, Request request, WorldAccess world,
            int pieceOrdinal, PiecePlacement piece, Mc263TrialChambersGrammar.Template template,
            long owner, Legacy48 caller, List<Query> queries, List<Write> writes,
            Map<Position, FinalContribution> finalContributions, long[] writeOrdinal,
            int[] callerDrawCount) {
        ArrayList<ProcessedBlock> processed = new ArrayList<>(template.blockCount());
        boolean copper = COPPER_PROCESSOR.equals(piece.processor());
        for (SourceBlock source : expand(template)) {
            if (source.command() instanceof Mc263TrialChambersGrammar.IgnoredStructureBlock) {
                continue;
            }
            Position position = transform(piece, source.localPosition());
            String state = baseProcessedState(template, source, piece.rotation());
            if (state == null || blockKey(state).equals("minecraft:structure_block")) continue;
            if (copper) state = applyCopperRules(state, position, pinned.copperRules());
            boolean dropped = false;
            if (copper) {
                Protection protection = Objects.requireNonNull(
                        world.protectionAt(position.x(), position.y(), position.z(),
                                stagedState(finalContributions.get(position))),
                        "Trial protected-block query result");
                Query query = new Query(pieceOrdinal, template.key(), source.sourceBlockOrdinal(),
                        position.x(), position.y(), position.z(), protection);
                queries.add(query);
                if (protection == Protection.UNKNOWN) {
                    throw new IllegalArgumentException("unknown Trial protected-block query result at "
                            + position.x() + "," + position.y() + "," + position.z());
                }
                dropped = protection == Protection.PROTECTED;
            }
            processed.add(new ProcessedBlock(source, position, state, dropped));
        }

        for (ProcessedBlock block : processed) {
            if (block.dropped() || !insideClip(request, block.position())) continue;
            SourceBlock source = block.source();
            Position position = block.position();
            Write write = new Write(pieceOrdinal, template.key(), source.sourceBlockOrdinal(),
                    position.x(), position.y(), position.z(), block.state(), owner);
            writes.add(write);
            long ordinal = writeOrdinal[0]++;
            Loot loot = null;
            Bent bent = null;
            if (isBent(source.command())) {
                OptionalLong seed = OptionalLong.empty();
                if (usesCallerNextLong(source.command())) {
                    seed = OptionalLong.of(caller.nextLong());
                    callerDrawCount[0]++;
                }
                byte[] nbt = canonicalBentNbt(source.command(), position, seed.orElse(0L));
                String type = blockEntityType(source.command()).id();
                bent = new Bent(pieceOrdinal, template.key(), source.sourceBlockOrdinal(),
                        position.x(), position.y(), position.z(), blockKey(block.state()), type, nbt);
                if (source.command() instanceof Mc263TrialChambersGrammar.LootContainer value) {
                    loot = new Loot(pieceOrdinal, template.key(), source.sourceBlockOrdinal(),
                            position.x(), position.y(), position.z(), value.lootTable().id(), type, seed);
                }
            }
            finalContributions.put(position,
                    new FinalContribution(ordinal, write, loot, bent));
        }
    }

    private static Mc263TrialChambersProducer.Start regenerateStart(
            Mc263TrialChambersProducer producer, Request request) {
        CapturingPublisher publisher = new CapturingPublisher();
        Mc263TrialChambersProducer.Start start = producer.generate(request.worldSeed(),
                request.startChunkX(), request.startChunkZ(), new Mc263TrialChambersProducer.WorldAccess() {
                    @Override public boolean supportsBuildHeightBoundary() { return true; }
                    @Override public int minBuildY() { return -64; }
                    @Override public int maxBuildY() { return 320; }
                }, publisher);
        if (publisher.start != start || publisher.count != 1) {
            throw new IllegalStateException("Trial producer predecessor publication drift");
        }
        return start;
    }

    private static void requireStartIdentity(Mc263TrialChambersProducer.Start start, Witness witness) {
        if (start.worldSeed() != witness.worldSeed()
                || start.chunkX() != witness.startChunkX()
                || start.chunkZ() != witness.startChunkZ()
                || !start.aggregateBoundingBox().equals(witness.boundingBox())
                || start.executionPlan().pieces().size() != witness.pieceCount()) {
            throw new IllegalArgumentException("Trial start does not match accepted G1T witness");
        }
        byte[] predecessor = start.carrier().structureStart().bytes();
        if (predecessor.length != witness.binaryLength()
                || !sha256(predecessor).equals(witness.predecessorSha256())) {
            throw new IllegalArgumentException("Trial predecessor NBT identity drift");
        }
    }

    private static Carrier successor(Mc263TrialChambersProducer.Start start, Witness witness) {
        byte[] predecessor = start.carrier().structureStart().bytes();
        if (predecessor.length <= REFERENCES_CHANGED_OFFSET
                || predecessor[REFERENCES_CHANGED_OFFSET] != 0) {
            throw new IllegalArgumentException("Trial predecessor references byte is not zero");
        }
        byte[] successor = predecessor.clone();
        successor[REFERENCES_CHANGED_OFFSET] = 1;
        if (successor.length != witness.binaryLength()
                || !sha256(successor).equals(witness.successorSha256())) {
            throw new IllegalArgumentException("Trial G1T successor identity drift");
        }
        return new Carrier(Mc263TrialChambersProducer.CARRIER_FORMAT,
                witness.predecessorSha256(), predecessor, witness.successorSha256(), successor,
                REFERENCES_CHANGED_OFFSET, 0, 1);
    }

    private static boolean intersectsAnyPiece(Mc263TrialChambersProducer.Start start,
            int targetChunkX, int targetChunkZ) {
        return start.executionPlan().pieces().stream()
                .anyMatch(piece -> intersects(piece, targetChunkX, targetChunkZ));
    }

    private static boolean intersects(PiecePlacement piece, int targetChunkX, int targetChunkZ) {
        int minX = Math.multiplyExact(targetChunkX, CHUNK_SIZE);
        int minZ = Math.multiplyExact(targetChunkZ, CHUNK_SIZE);
        int maxX = Math.addExact(minX, CHUNK_SIZE - 1);
        int maxZ = Math.addExact(minZ, CHUNK_SIZE - 1);
        return piece.bounds().maxX() >= minX && piece.bounds().minX() <= maxX
                && piece.bounds().maxZ() >= minZ && piece.bounds().minZ() <= maxZ;
    }

    private static boolean insideClip(Request request, Position position) {
        int minX = Math.multiplyExact(request.targetChunkX(), CHUNK_SIZE);
        int minZ = Math.multiplyExact(request.targetChunkZ(), CHUNK_SIZE);
        return position.x() >= minX && position.x() <= minX + 15
                && position.z() >= minZ && position.z() <= minZ + 15
                && position.y() >= CLIP_MIN_Y && position.y() <= CLIP_MAX_Y;
    }

    private static List<SourceBlock> expand(Mc263TrialChambersGrammar.Template template) {
        ArrayList<SourceBlock> result = new ArrayList<>(template.blockCount());
        for (Mc263TrialChambersGrammar.Command command : template.commands()) {
            if (command instanceof Mc263TrialChambersGrammar.Run run) {
                for (int index = 0; index < run.count(); index++) {
                    result.add(new SourceBlock(command, run.ordinal() + index,
                            new Mc263TrialChambersGrammar.Vec3i(
                                    Math.addExact(run.start().x(), Math.multiplyExact(run.delta().x(), index)),
                                    Math.addExact(run.start().y(), Math.multiplyExact(run.delta().y(), index)),
                                    Math.addExact(run.start().z(), Math.multiplyExact(run.delta().z(), index)))));
                }
            } else if (command instanceof Mc263TrialChambersGrammar.Jigsaw jigsaw) {
                Mc263TrialChambersGrammar.Connector connector = template.connectors().stream()
                        .filter(value -> value.ordinal() == jigsaw.connectorOrdinal()).findFirst()
                        .orElseThrow(() -> new IllegalArgumentException(
                                "Trial JIGSAW connector ordinal drift"));
                result.add(new SourceBlock(command, command.ordinal(), connector.position()));
            } else {
                result.add(new SourceBlock(command, command.ordinal(), commandPosition(command)));
            }
        }
        if (result.size() != template.blockCount()) {
            throw new IllegalArgumentException("Trial procedural command expansion cardinality drift: "
                    + template.key());
        }
        return List.copyOf(result);
    }

    private static Mc263TrialChambersGrammar.Vec3i commandPosition(
            Mc263TrialChambersGrammar.Command command) {
        if (command instanceof Mc263TrialChambersGrammar.IgnoredStructureBlock value) return value.position();
        if (command instanceof Mc263TrialChambersGrammar.LootContainer value) return value.position();
        if (command instanceof Mc263TrialChambersGrammar.FixedContainer value) return value.position();
        if (command instanceof Mc263TrialChambersGrammar.DecoratedPot value) return value.position();
        if (command instanceof Mc263TrialChambersGrammar.Vault value) return value.position();
        if (command instanceof Mc263TrialChambersGrammar.TrialSpawner value) return value.position();
        throw new IllegalArgumentException("unknown Trial command position: " + command.op());
    }

    private static String baseProcessedState(Mc263TrialChambersGrammar.Template template,
            SourceBlock source, Rotation rotation) {
        Mc263TrialChambersGrammar.Command command = source.command();
        if (command instanceof Mc263TrialChambersGrammar.Jigsaw value) {
            if ("minecraft:structure_void".equals(value.finalState())) return null;
            return value.finalState();
        }
        int stateIndex = command.state();
        if (stateIndex < 0 || stateIndex >= template.stateTable().size()) {
            throw new IllegalArgumentException("Trial state-table index drift");
        }
        return rotateState(template.stateTable().get(stateIndex), rotation);
    }

    private static String stagedState(FinalContribution contribution) {
        return contribution == null ? null : contribution.write().exactState();
    }

    private static String applyCopperRules(String state, Position position,
            List<Mc263TrialChambersGrammar.RuleSemanticRow> rules) {
        if (!blockKey(state).equals("minecraft:waxed_copper_bulb")) return state;
        Legacy48 random = Legacy48.fromExternalSeed(positionalSeed(position));
        for (Mc263TrialChambersGrammar.RuleSemanticRow rule : rules) {
            if (!rule.inputBlock().equals("minecraft:waxed_copper_bulb")) {
                throw new IllegalArgumentException("unknown Trial RuleProcessor input");
            }
            if (random.nextFloat() < rule.probability()) return rule.outputState();
        }
        return state;
    }

    private static long positionalSeed(Position position) {
        int xProduct = position.x() * 3_129_871;
        long value = (long) xProduct ^ (long) position.z() * 116_129_781L ^ position.y();
        value = value * value * 42_317_861L + value * 11L;
        return value >> 16;
    }

    private static Position transform(PiecePlacement piece,
            Mc263TrialChambersGrammar.Vec3i local) {
        int x;
        int z;
        switch (piece.rotation()) {
            case NONE -> { x = local.x(); z = local.z(); }
            case CLOCKWISE_90 -> { x = -local.z(); z = local.x(); }
            case CLOCKWISE_180 -> { x = -local.x(); z = -local.z(); }
            case COUNTERCLOCKWISE_90 -> { x = local.z(); z = -local.x(); }
            default -> throw new IllegalArgumentException("unknown Trial rotation");
        }
        return new Position(Math.addExact(piece.originX(), x),
                Math.addExact(piece.originY(), local.y()), Math.addExact(piece.originZ(), z));
    }

    private static String rotateState(String state, Rotation rotation) {
        Objects.requireNonNull(state, "Trial exact state");
        Objects.requireNonNull(rotation, "Trial rotation");
        int open = state.indexOf('[');
        if (open < 0 || rotation == Rotation.NONE) return state;
        if (!state.endsWith("]")) throw new IllegalArgumentException("malformed Trial exact state");
        int turns = switch (rotation) {
            case NONE -> 0;
            case CLOCKWISE_90 -> 1;
            case CLOCKWISE_180 -> 2;
            case COUNTERCLOCKWISE_90 -> 3;
        };
        String block = state.substring(0, open);
        String[] entries = state.substring(open + 1, state.length() - 1).split(",", -1);
        LinkedHashMap<String, String> original = new LinkedHashMap<>();
        for (String entry : entries) {
            int equals = entry.indexOf('=');
            if (equals <= 0 || equals == entry.length() - 1) {
                throw new IllegalArgumentException("malformed Trial exact-state property: " + state);
            }
            original.put(entry.substring(0, equals), entry.substring(equals + 1));
        }
        LinkedHashMap<String, String> rotated = new LinkedHashMap<>(original);
        String facing = original.get("facing");
        if (isHorizontal(facing)) rotated.put("facing", rotateDirection(facing, turns));
        String axis = original.get("axis");
        if ((turns & 1) != 0 && ("x".equals(axis) || "z".equals(axis))) {
            rotated.put("axis", "x".equals(axis) ? "z" : "x");
        }
        for (String direction : List.of("east", "north", "south", "west")) {
            if (original.containsKey(direction)) {
                rotated.put(direction, original.get(rotateDirection(direction, (4 - turns) & 3)));
            }
        }
        StringBuilder out = new StringBuilder(block).append('[');
        boolean first = true;
        for (Map.Entry<String, String> entry : rotated.entrySet()) {
            if (!first) out.append(',');
            first = false;
            out.append(entry.getKey()).append('=').append(entry.getValue());
        }
        return out.append(']').toString();
    }

    private static boolean isHorizontal(String value) {
        return "north".equals(value) || "east".equals(value)
                || "south".equals(value) || "west".equals(value);
    }

    private static String rotateDirection(String value, int turns) {
        if (!isHorizontal(value)) return value;
        List<String> directions = List.of("north", "east", "south", "west");
        return directions.get((directions.indexOf(value) + turns) & 3);
    }

    private static void requireExactState(String state) {
        if (!Mc263FeatureBlockState.supportsExactState(state)) {
            throw new IllegalArgumentException("unsupported Trial exact state after processor/rotation: "
                    + state);
        }
    }

    private static String blockKey(String state) {
        int open = state.indexOf('[');
        return open < 0 ? state : state.substring(0, open);
    }

    private static boolean isBent(Mc263TrialChambersGrammar.Command command) {
        return command instanceof Mc263TrialChambersGrammar.LootContainer
                || command instanceof Mc263TrialChambersGrammar.FixedContainer
                || command instanceof Mc263TrialChambersGrammar.DecoratedPot
                || command instanceof Mc263TrialChambersGrammar.Vault
                || command instanceof Mc263TrialChambersGrammar.TrialSpawner;
    }

    private static boolean usesCallerNextLong(Mc263TrialChambersGrammar.Command command) {
        if (command instanceof Mc263TrialChambersGrammar.LootContainer value) {
            return value.rng() instanceof Mc263TrialChambersGrammar.CallerNextLongRng;
        }
        if (command instanceof Mc263TrialChambersGrammar.FixedContainer value) {
            return value.rng() instanceof Mc263TrialChambersGrammar.CallerNextLongRng;
        }
        return false;
    }

    private static Mc263TrialChambersGrammar.BlockEntityType blockEntityType(
            Mc263TrialChambersGrammar.Command command) {
        if (command instanceof Mc263TrialChambersGrammar.LootContainer value) return value.blockEntityType();
        if (command instanceof Mc263TrialChambersGrammar.FixedContainer value) return value.blockEntityType();
        if (command instanceof Mc263TrialChambersGrammar.DecoratedPot value) return value.blockEntityType();
        if (command instanceof Mc263TrialChambersGrammar.Vault value) return value.blockEntityType();
        if (command instanceof Mc263TrialChambersGrammar.TrialSpawner value) return value.blockEntityType();
        throw new IllegalArgumentException("Trial command has no BENT type");
    }

    /**
     * Canonical block-entity payload for one accepted command at its live placement position.
     *
     * <p>Trial Chambers owns no NBT writer: the grammar translates the accepted command into a
     * family-agnostic {@link Mc263StructureBlockEntityNbtAuthority.Facts} and the shared authority
     * renders it, so every family emits one program per block-entity type.</p>
     */
    private static byte[] canonicalBentNbt(Mc263TrialChambersGrammar.Command command,
            Position position, long lootSeed) {
        return Mc263StructureBlockEntityNbtAuthority.render(position.x(), position.y(), position.z(),
                Mc263TrialChambersGrammar.canonicalBentFacts(command, lootSeed));
    }

    private static void validateCanonicalBent(Mc263TrialChambersGrammar.Corpus corpus) {
        List<Mc263TrialChambersGrammar.CanonicalBentEvidence> expected =
                corpus.canonicalBentEvidenceInCommandOrder();
        int index = 0;
        for (Mc263TrialChambersGrammar.Template template
                : corpus.evidence().templatesInEncounterOrder()) {
            for (Mc263TrialChambersGrammar.Command command : template.commands()) {
                if (!isBent(command)) continue;
                if (index >= expected.size()) throw new IllegalArgumentException("Trial BENT evidence underflow");
                Mc263TrialChambersGrammar.CanonicalBentEvidence evidence = expected.get(index++);
                Position position = localPosition(command);
                long seed = evidence.probeNextLongI64().orElse(0L);
                byte[] actual = canonicalBentNbt(command, position, seed);
                if (!evidence.template().equals(template.key())
                        || evidence.commandOrdinal() != command.ordinal()
                        || evidence.blockEntityType() != blockEntityType(command)
                        || evidence.probeNextLongI64().isPresent() != usesCallerNextLong(command)
                        || !Arrays.equals(actual, evidence.canonicalProbeNbt())) {
                    throw new IllegalArgumentException("Trial canonical BENT semantic drift: "
                            + template.key() + "#" + command.ordinal());
                }
            }
        }
        if (index != expected.size()) throw new IllegalArgumentException("Trial BENT evidence overflow");
    }

    private static Position localPosition(Mc263TrialChambersGrammar.Command command) {
        Mc263TrialChambersGrammar.Vec3i value = commandPosition(command);
        return new Position(value.x(), value.y(), value.z());
    }

    private static void requireContinuation(Mc263TrialChambersProducer.GenerationRng receipt,
            Legacy48 random) {
        List<Long> actual = previewNextLongs(random, receipt.continuationNextLongI64().size());
        if (!actual.equals(receipt.continuationNextLongI64())) {
            throw new IllegalArgumentException("Trial producer caller-RNG handoff drift");
        }
    }

    private static List<Long> previewNextLongs(Legacy48 random, int count) {
        ArrayList<Long> values = new ArrayList<>(count);
        for (int index = 0; index < count; index++) values.add(random.nextLong());
        return List.copyOf(values);
    }

    private static long owner(String structureKey, String startKey) {
        MessageDigest digest = sha256Digest();
        digest.update(structureKey.getBytes(StandardCharsets.UTF_8));
        digest.update((byte) 0);
        byte[] hash = digest.digest(startKey.getBytes(StandardCharsets.UTF_8));
        long value = 0;
        for (int index = 0; index < Long.BYTES; index++) {
            value = (value << 8) | Byte.toUnsignedLong(hash[index]);
        }
        return value;
    }

    private static String startKey(int chunkX, int chunkZ) {
        return STRUCTURE_KEY + "@" + chunkX + "," + chunkZ;
    }

    private static Witness witness(long worldSeed, int chunkX, int chunkZ) {
        if (FIRST.matches(worldSeed, chunkX, chunkZ)) return FIRST;
        if (SECOND.matches(worldSeed, chunkX, chunkZ)) return SECOND;
        throw new IllegalArgumentException("Trial start is outside accepted G1T probes");
    }

    /**
     * Authenticates the G1T reference-successor oracle payload.
     *
     * <p>The payload is a verification corpus, not a production fact: the production path derives
     * the identical successor arithmetically, so the bytes are supplied by the verifying test
     * rather than packaged and parsed on the join path.</p>
     */
    static void verifyReferenceResource(byte[] bytes) {
        Objects.requireNonNull(bytes, "Trial G1T reference bytes");
        if (bytes.length != REFERENCE_BYTES || !sha256(bytes).equals(REFERENCE_SHA256)) {
            throw new IllegalArgumentException("Trial G1T reference resource identity drift");
        }
        String json = new String(bytes, StandardCharsets.UTF_8);
        for (String token : List.of(FIRST.predecessorSha256(), FIRST.successorSha256(),
                SECOND.predecessorSha256(), SECOND.successorSha256(),
                "\"changedByteOffset\":19", "\"before\":\"0\"",
                "\"after\":\"1\"")) {
            if (!json.contains(token)) {
                throw new IllegalArgumentException("Trial G1T reference semantic token drift");
            }
        }
    }

    private static String fingerprint(String transactionKey, Request request, Carrier carrier,
            Settlement payload, long initialState48, long finalState48, int callerDrawCount,
            List<Long> continuation) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeUTF("TRL-G1U-JAVA-SETTLEMENT-v1");
            out.writeUTF(transactionKey);
            out.writeLong(request.worldSeed());
            out.writeInt(request.startChunkX()); out.writeInt(request.startChunkZ());
            out.writeInt(request.targetChunkX()); out.writeInt(request.targetChunkZ());
            out.writeUTF(carrier.predecessorSha256()); out.writeUTF(carrier.successorSha256());
            out.writeLong(initialState48); out.writeLong(finalState48); out.writeInt(callerDrawCount);
            out.writeInt(continuation.size()); for (long value : continuation) out.writeLong(value);
            out.writeInt(payload.queries().size());
            for (Query value : payload.queries()) {
                source(out, value.pieceOrdinal(), value.template(), value.sourceBlockOrdinal());
                pos(out, value.blockX(), value.blockY(), value.blockZ()); out.writeByte(value.result().ordinal());
            }
            out.writeInt(payload.writes().size());
            for (Write value : payload.writes()) {
                source(out, value.pieceOrdinal(), value.template(), value.sourceBlockOrdinal());
                pos(out, value.blockX(), value.blockY(), value.blockZ());
                out.writeUTF(value.exactState()); out.writeLong(value.owner());
            }
            out.writeInt(payload.loot().size());
            for (Loot value : payload.loot()) {
                source(out, value.pieceOrdinal(), value.template(), value.sourceBlockOrdinal());
                pos(out, value.blockX(), value.blockY(), value.blockZ());
                out.writeUTF(value.lootTable()); out.writeUTF(value.blockEntityType());
                out.writeBoolean(value.lootSeed().isPresent());
                if (value.lootSeed().isPresent()) out.writeLong(value.lootSeed().getAsLong());
            }
            out.writeInt(payload.blockEntities().size());
            for (Bent value : payload.blockEntities()) {
                source(out, value.pieceOrdinal(), value.template(), value.sourceBlockOrdinal());
                pos(out, value.blockX(), value.blockY(), value.blockZ());
                out.writeUTF(value.blockIdentity()); out.writeUTF(value.entityType());
                byte[] nbt = value.canonicalNbt(); out.writeInt(nbt.length); out.write(nbt);
            }
            out.writeInt(payload.archaeology().size()); out.writeInt(payload.entities().size());
            out.writeInt(payload.fluidTicks().size()); out.writeInt(payload.postprocessMarks().size());
            out.writeInt(payload.spawners().size()); out.writeInt(payload.blockTicks().size());
            out.writeInt(payload.bees().size());
            out.writeInt(payload.ownership().size());
            for (Ownership value : payload.ownership()) {
                pos(out, value.blockX(), value.blockY(), value.blockZ()); out.writeLong(value.owner());
            }
            out.writeInt(payload.finalCells().size());
            for (FinalCell value : payload.finalCells()) {
                source(out, value.pieceOrdinal(), value.template(), value.sourceBlockOrdinal());
                pos(out, value.blockX(), value.blockY(), value.blockZ());
                out.writeUTF(value.exactState()); out.writeLong(value.owner());
            }
            out.flush();
            return sha256(bytes.toByteArray());
        } catch (IOException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static void source(DataOutputStream out, int pieceOrdinal, String template,
            int sourceBlockOrdinal) throws IOException {
        out.writeInt(pieceOrdinal); out.writeUTF(template); out.writeInt(sourceBlockOrdinal);
    }

    private static void pos(DataOutputStream out, int x, int y, int z) throws IOException {
        out.writeInt(x); out.writeInt(y); out.writeInt(z);
    }

    private static String sha256(byte[] bytes) {
        byte[] digest = sha256Digest().digest(bytes);
        StringBuilder out = new StringBuilder(digest.length * 2);
        for (byte value : digest) out.append(String.format("%02x", value));
        return out.toString();
    }

    private static MessageDigest sha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException error) {
            throw new AssertionError("SHA-256 is required by the Java runtime", error);
        }
    }

    private static final class PinnedHolder {
        private static final Pinned PINNED = loadPinned();
    }

    private static Pinned loadPinned() {
        Mc263TrialChambersGrammar.Corpus grammar = Mc263TrialChambersGrammar.pinned();
        Mc263TrialChambersProducer producer = Mc263TrialChambersProducer.pinned();
        validateCanonicalBent(grammar);
        Mc263TrialChambersGrammar.TypedSidecars sidecars = grammar.evidence().typedSidecars();
        if (!sidecars.ents().isEmpty() || sidecars.templateEntityCount() != 0) {
            throw new IllegalArgumentException("Trial execution corpus ENTS identity drift");
        }
        LinkedHashMap<String, Mc263TrialChambersGrammar.Template> templates = new LinkedHashMap<>();
        for (Mc263TrialChambersGrammar.Template template
                : grammar.evidence().templatesInEncounterOrder()) {
            if (templates.put(template.key(), template) != null) {
                throw new IllegalArgumentException("duplicate Trial template identity");
            }
        }
        List<Mc263TrialChambersGrammar.RuleSemanticRow> rules = null;
        for (Mc263TrialChambersGrammar.Processor processor
                : grammar.evidence().processorsInEncounterOrder()) {
            if (processor.semantics() instanceof Mc263TrialChambersGrammar.RuleSemantics value) {
                if (rules != null) throw new IllegalArgumentException("duplicate Trial RuleProcessor");
                rules = value.rulesInOrder();
            }
        }
        if (rules == null || rules.size() != 3) {
            throw new IllegalArgumentException("Trial RuleProcessor identity drift");
        }
        return new Pinned(producer, Map.copyOf(templates), List.copyOf(rules));
    }

    private record Pinned(Mc263TrialChambersProducer producer,
            Map<String, Mc263TrialChambersGrammar.Template> templates,
            List<Mc263TrialChambersGrammar.RuleSemanticRow> copperRules) {
        Mc263TrialChambersGrammar.Template template(String key) {
            Mc263TrialChambersGrammar.Template template = templates.get(key);
            if (template == null) throw new IllegalArgumentException("unknown Trial template: " + key);
            return template;
        }
        void requireHealthy() { }
    }

    private record Witness(long worldSeed, int startChunkX, int startChunkZ,
            int referenceTargetChunkX, int referenceTargetChunkZ,
            Mc263TrialChambersProducer.Box boundingBox, int pieceCount, int binaryLength,
            String predecessorSha256, String successorSha256) {
        boolean matches(long seed, int chunkX, int chunkZ) {
            return worldSeed == seed && startChunkX == chunkX && startChunkZ == chunkZ;
        }
    }

    private record SourceBlock(Mc263TrialChambersGrammar.Command command,
            int sourceBlockOrdinal, Mc263TrialChambersGrammar.Vec3i localPosition) { }
    private record ProcessedBlock(SourceBlock source, Position position, String state,
            boolean dropped) { }
    private record Position(int x, int y, int z) { }
    private record FinalContribution(long writeOrdinal, Write write, Loot loot, Bent bent) { }

    private static final class CapturingPublisher implements Mc263TrialChambersProducer.Publisher {
        private Mc263TrialChambersProducer.Start start;
        private int count;
        @Override public boolean supports(String structureKey, String carrierFormat) {
            return STRUCTURE_KEY.equals(structureKey)
                    && Mc263TrialChambersProducer.CARRIER_FORMAT.equals(carrierFormat);
        }
        @Override public void publishAtomically(Mc263TrialChambersProducer.Start value) {
            if (start != null) throw new IllegalStateException("duplicate Trial predecessor publication");
            start = Objects.requireNonNull(value, "Trial predecessor");
            count++;
        }
    }

    private static final class Legacy48 {
        private long state;
        private Legacy48(long state) { this.state = state & RANDOM_MASK; }
        static Legacy48 fromInternalState(long state) { return new Legacy48(state); }
        static Legacy48 fromExternalSeed(long seed) {
            return new Legacy48((seed ^ RANDOM_MULTIPLIER) & RANDOM_MASK);
        }
        Legacy48 copy() { return new Legacy48(state); }
        long state() { return state; }
        private int next(int bits) {
            state = (state * RANDOM_MULTIPLIER + RANDOM_ADDEND) & RANDOM_MASK;
            return (int) (state >>> (48 - bits));
        }
        float nextFloat() { return next(24) / ((float) (1 << 24)); }
        long nextLong() {
            int high = next(32);
            int low = next(32);
            return ((long) high << 32) + low;
        }
    }

    public enum Protection { REPLACEABLE, PROTECTED, UNKNOWN }
    public enum CommitResult { APPLIED, REPLAYED }

    /** Pure query/capability surface. Query calls may observe but must not mutate the world. */
    public interface WorldAccess {
        boolean supportsProtectedBlockQueries();
        boolean supportsClip(int minYInclusive, int maxYInclusive);
        Protection protectionAt(int blockX, int blockY, int blockZ, String stagedExactState);
    }

    /**
     * Atomic publication boundary. Implementations must apply payload and successor together; exact
     * transaction-key/fingerprint replay is a no-op, a differing fingerprint for an existing key is
     * a conflict, and any thrown failure must roll back every payload/carrier mutation.
     */
    public interface Committer {
        boolean supportsAtomicReplayableCommit();
        CommitResult commitAtomically(Commit commit);
    }

    public record Request(long worldSeed, int startChunkX, int startChunkZ,
            int targetChunkX, int targetChunkZ) { }

    public record EvidenceIdentity(int executionBytes, String executionSha256,
            int startGraphBytes, String startGraphSha256, int bentBytes, String bentSha256,
            int referenceSuccessorBytes, String referenceSuccessorSha256) {
        public EvidenceIdentity {
            Objects.requireNonNull(executionSha256); Objects.requireNonNull(startGraphSha256);
            Objects.requireNonNull(bentSha256); Objects.requireNonNull(referenceSuccessorSha256);
        }
    }

    public record Query(int pieceOrdinal, String template, int sourceBlockOrdinal,
            int blockX, int blockY, int blockZ, Protection result) {
        public Query { Objects.requireNonNull(template); Objects.requireNonNull(result); }
    }

    public record Write(int pieceOrdinal, String template, int sourceBlockOrdinal,
            int blockX, int blockY, int blockZ, String exactState, long owner) {
        public Write { Objects.requireNonNull(template); Objects.requireNonNull(exactState); }
    }

    /** LOOT preserves seed absence for decorated pots instead of inventing a caller RNG draw. */
    public record Loot(int pieceOrdinal, String template, int sourceBlockOrdinal,
            int blockX, int blockY, int blockZ, String lootTable, String blockEntityType,
            OptionalLong lootSeed) {
        public Loot {
            Objects.requireNonNull(template); Objects.requireNonNull(lootTable);
            Objects.requireNonNull(blockEntityType); Objects.requireNonNull(lootSeed);
        }
    }

    public record Archaeology(int blockX, int blockY, int blockZ, String lootTable, long lootSeed) {
        public Archaeology { Objects.requireNonNull(lootTable); }
    }

    public record Bent(int pieceOrdinal, String template, int sourceBlockOrdinal,
            int blockX, int blockY, int blockZ, String blockIdentity, String entityType,
            byte[] canonicalNbt) {
        public Bent {
            Objects.requireNonNull(template); Objects.requireNonNull(blockIdentity);
            Objects.requireNonNull(entityType); canonicalNbt = Objects.requireNonNull(canonicalNbt).clone();
        }
        @Override public byte[] canonicalNbt() { return canonicalNbt.clone(); }
        @Override public boolean equals(Object other) {
            return other instanceof Bent value && pieceOrdinal == value.pieceOrdinal
                    && sourceBlockOrdinal == value.sourceBlockOrdinal && blockX == value.blockX
                    && blockY == value.blockY && blockZ == value.blockZ
                    && template.equals(value.template) && blockIdentity.equals(value.blockIdentity)
                    && entityType.equals(value.entityType) && Arrays.equals(canonicalNbt, value.canonicalNbt);
        }
        @Override public int hashCode() {
            return 31 * Objects.hash(pieceOrdinal, template, sourceBlockOrdinal, blockX, blockY,
                    blockZ, blockIdentity, entityType) + Arrays.hashCode(canonicalNbt);
        }
    }

    public record Entity(int blockX, int blockY, int blockZ, byte[] canonicalNbt) {
        public Entity { canonicalNbt = Objects.requireNonNull(canonicalNbt).clone(); }
        @Override public byte[] canonicalNbt() { return canonicalNbt.clone(); }
    }
    public record Tick(int blockX, int blockY, int blockZ, String typeKey,
            int delay, int priority, long subTickOrder) {
        public Tick { Objects.requireNonNull(typeKey); }
    }
    public record PostprocessMark(int blockX, int blockY, int blockZ) { }
    public record Spawner(int blockX, int blockY, int blockZ, String entityType) {
        public Spawner { Objects.requireNonNull(entityType); }
    }
    public record Bee(int blockX, int blockY, int blockZ, int ticksInHive) { }
    public record Ownership(int blockX, int blockY, int blockZ, long owner) { }
    public record FinalCell(int blockX, int blockY, int blockZ, String exactState, long owner,
            int pieceOrdinal, String template, int sourceBlockOrdinal) {
        public FinalCell { Objects.requireNonNull(exactState); Objects.requireNonNull(template); }
    }

    /** Complete semantic payload; authenticated absent lanes are represented by immutable empties. */
    public record Settlement(List<Write> writes, List<Loot> loot, List<Archaeology> archaeology,
            List<Bent> blockEntities, List<Entity> entities, List<Tick> fluidTicks,
            List<PostprocessMark> postprocessMarks, List<Spawner> spawners,
            List<Tick> blockTicks, List<Bee> bees, List<Ownership> ownership,
            List<FinalCell> finalCells, List<Query> queries) {
        public Settlement {
            writes = List.copyOf(Objects.requireNonNull(writes));
            loot = List.copyOf(Objects.requireNonNull(loot));
            archaeology = List.copyOf(Objects.requireNonNull(archaeology));
            blockEntities = List.copyOf(Objects.requireNonNull(blockEntities));
            entities = List.copyOf(Objects.requireNonNull(entities));
            fluidTicks = List.copyOf(Objects.requireNonNull(fluidTicks));
            postprocessMarks = List.copyOf(Objects.requireNonNull(postprocessMarks));
            spawners = List.copyOf(Objects.requireNonNull(spawners));
            blockTicks = List.copyOf(Objects.requireNonNull(blockTicks));
            bees = List.copyOf(Objects.requireNonNull(bees));
            ownership = List.copyOf(Objects.requireNonNull(ownership));
            finalCells = List.copyOf(Objects.requireNonNull(finalCells));
            queries = List.copyOf(Objects.requireNonNull(queries));
        }
    }

    public record Carrier(String format, String predecessorSha256, byte[] predecessor,
            String successorSha256, byte[] successor, int changedOffset,
            int referencesBefore, int referencesAfter) {
        public Carrier {
            Objects.requireNonNull(format); Objects.requireNonNull(predecessorSha256);
            Objects.requireNonNull(successorSha256);
            predecessor = Objects.requireNonNull(predecessor).clone();
            successor = Objects.requireNonNull(successor).clone();
        }
        @Override public byte[] predecessor() { return predecessor.clone(); }
        @Override public byte[] successor() { return successor.clone(); }
    }

    public record Commit(String transactionKey, Request request, boolean exactReferenceWitness,
            boolean pieceTarget, Carrier carrier, Settlement payload, long initialCallerState48,
            long finalCallerState48, int callerNextLongDrawCount,
            List<Long> continuationNextLongI64, String fingerprint) {
        public Commit {
            Objects.requireNonNull(transactionKey); Objects.requireNonNull(request);
            Objects.requireNonNull(carrier); Objects.requireNonNull(payload);
            continuationNextLongI64 = List.copyOf(Objects.requireNonNull(continuationNextLongI64));
            Objects.requireNonNull(fingerprint);
            if (initialCallerState48 < 0 || initialCallerState48 > RANDOM_MASK
                    || finalCallerState48 < 0 || finalCallerState48 > RANDOM_MASK
                    || callerNextLongDrawCount < 0 || continuationNextLongI64.size() != 8) {
                throw new IllegalArgumentException("invalid Trial caller RNG receipt");
            }
        }
    }
}
