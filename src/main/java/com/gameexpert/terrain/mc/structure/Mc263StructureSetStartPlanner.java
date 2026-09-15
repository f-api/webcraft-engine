package com.gameexpert.terrain.mc.structure;

import com.gameexpert.terrain.mc.LegacyRand;
import com.gameexpert.terrain.mc.feature.Mc263StructureIndexReceipt;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Dormant exact per-center 26.3 structure-set placement planner. */
public final class Mc263StructureSetStartPlanner {
    private static final long LARGE_FEATURE_X = 341_873_128_712L;
    private static final long LARGE_FEATURE_Z = 132_897_987_541L;
    private static final int ARBITRARY_FREQUENCY_SALT = 10_387_320;
    private static final double TWO_PI = Math.PI * 2.0D;
    private static final byte[] RECEIPT_MAGIC = "SSP263P2".getBytes(StandardCharsets.US_ASCII);

    private final List<SetSpec> sets;
    private final Map<String, SetSpec> byKey;

    private Mc263StructureSetStartPlanner(List<SetSpec> sets) {
        this.sets = sets;
        Map<String, SetSpec> indexed = new HashMap<>();
        for (SetSpec set : sets) indexed.put(set.key, set);
        byKey = Map.copyOf(indexed);
    }

    public static Mc263StructureSetStartPlanner pinned() {
        return PinnedHolder.INSTANCE;
    }

    /** Parsed once because the authenticated placement receipt is process-wide pinned identity. */
    private static final class PinnedHolder {
        private static final Mc263StructureSetStartPlanner INSTANCE =
                fromReceipt(Mc263StructureIndexReceipt.placements());

        private PinnedHolder() { }
    }

    public static Mc263StructureSetStartPlanner fromReceipt(
            List<Mc263StructureIndexReceipt.PlacementRecord> records) {
        if (records == null || !records.equals(Mc263StructureIndexReceipt.placements())) {
            throw new IllegalArgumentException("structure-set placement receipt is not pinned 26.3");
        }
        List<SetSpec> parsed = new ArrayList<>();
        Set<String> unique = new HashSet<>();
        for (Mc263StructureIndexReceipt.PlacementRecord record : records) {
            if (!unique.add(record.key())) throw new IllegalArgumentException("duplicate set key");
            parsed.add(parse(record));
        }
        if (parsed.size() != Mc263StructureIndexReceipt.STRUCTURE_SET_COUNT) {
            throw new IllegalArgumentException("structure-set count is not pinned 26.3");
        }
        return new Mc263StructureSetStartPlanner(List.copyOf(parsed));
    }

    /**
     * Builds immutable dimension structure state. Possible-set filtering and stronghold rings happen
     * once here, mirroring {@code ChunkGeneratorStructureState}; per-center decisions reuse it.
     */
    public PlanningState buildState(long worldSeed, WorldAccess world) {
        preflight(world);
        List<String> possible = new ArrayList<>();
        List<BlockPos> rings = List.of();
        for (SetSpec set : sets) {
            if (!world.isStructureSetPossible(set.key, memberKeys(set))) continue;
            possible.add(set.key);
            if (set.kind == Kind.CONCENTRIC_RINGS) rings = buildRings(set, worldSeed, world);
        }
        return new PlanningState(worldSeed, List.copyOf(possible), rings);
    }

    /** Capability-only validation; safe before existing-start reads. */
    public void preflightDecision(WorldAccess world, PlanningState state, long worldSeed) {
        preflight(world);
        if (state == null || state.worldSeed() != worldSeed) {
            throw new IllegalArgumentException("matching immutable structure planning state required");
        }
        int lastIndex = -1;
        Set<String> unique = new HashSet<>();
        for (String key : state.possibleSetKeys()) {
            SetSpec set = byKey.get(key);
            if (set == null || !unique.add(key)) {
                throw new IllegalArgumentException("unknown or duplicate set in state");
            }
            int index = sets.indexOf(set);
            if (index <= lastIndex) throw new IllegalArgumentException("set state order is not pinned");
            lastIndex = index;
        }
        boolean strongholdsPossible = unique.contains("minecraft:strongholds");
        if (state.strongholdRingPositions().size() != (strongholdsPossible ? 128 : 0)) {
            throw new IllegalArgumentException("stronghold ring cache does not match possible sets");
        }
    }

    public List<String> memberKeys(String setKey) {
        SetSpec set = requireSet(setKey);
        return memberKeys(set);
    }

    /** Exact authenticated random-spread spacing used by the official locate perimeter. */
    public int gridSpacing(String setKey) {
        SetSpec set = requireSet(setKey);
        if (set.kind != Kind.RANDOM_SPREAD) {
            throw new IllegalArgumentException("grid probes require random-spread placement");
        }
        return set.spacing;
    }

    /** Resets the exact production sampler before one independent locate candidate. */
    public void beginGridProbe(Mc263StructureWorldAccess world) {
        if (world == null || world.planner() != this) {
            throw new IllegalArgumentException("matching production structure world required");
        }
        world.beginStructureChunkDecision();
    }

    /** Validates one server-thread locate after worker-local FULL biome generation. */
    public void beginLocatePerimeter(Mc263StructureWorldAccess world) {
        if (world == null || world.planner() != this) {
            throw new IllegalArgumentException("matching production structure world required");
        }
    }

    /**
     * Maps one official locate grid probe onto the set's exact potential candidate chunk and
     * evaluates that candidate through the ordinary authenticated placement path.
     */
    public Candidate candidateForGridProbe(long worldSeed, int probeChunkX, int probeChunkZ,
            String setKey, PlanningState state, WorldAccess world) {
        preflightDecision(world, state, worldSeed);
        if (!state.possibleSetKeys().contains(setKey)) {
            throw new IllegalArgumentException("set is not possible in supplied state");
        }
        SetSpec set = requireSet(setKey);
        if (set.kind != Kind.RANDOM_SPREAD) {
            throw new IllegalArgumentException("grid probes require random-spread placement");
        }
        int gridX = Math.floorDiv(probeChunkX, set.spacing);
        int gridZ = Math.floorDiv(probeChunkZ, set.spacing);
        LegacyRand placementRandom = new LegacyRand(largeFeatureWithSalt(
                worldSeed, gridX, gridZ, set.salt));
        int limit = Math.subtractExact(set.spacing, set.separation);
        int potentialX = Math.addExact(Math.multiplyExact(gridX, set.spacing),
                spread(placementRandom, limit, set.spreadType));
        int potentialZ = Math.addExact(Math.multiplyExact(gridZ, set.spacing),
                spread(placementRandom, limit, set.spreadType));
        return candidateAt(worldSeed, potentialX, potentialZ, setKey, state, world);
    }

    /** Evaluates one possible set for exactly the current center chunk. */
    public Candidate candidateAt(long worldSeed, int centerChunkX, int centerChunkZ,
            String setKey, PlanningState state, WorldAccess world) {
        preflightDecision(world, state, worldSeed);
        if (state.worldSeed() != worldSeed || !state.possibleSetKeys().contains(setKey)) {
            throw new IllegalArgumentException("set is not possible in supplied state");
        }
        SetSpec set = requireSet(setKey);
        if (set.kind == Kind.CONCENTRIC_RINGS) {
            boolean placement = state.strongholdRingPositions().stream()
                    .anyMatch(pos -> pos.x() == centerChunkX && pos.z() == centerChunkZ);
            return candidate(set, centerChunkX, centerChunkZ, placement, placement, false,
                    placement ? weightedAttempts(set, worldSeed, centerChunkX, centerChunkZ)
                            : List.of());
        }
        int gridX = Math.floorDiv(centerChunkX, set.spacing);
        int gridZ = Math.floorDiv(centerChunkZ, set.spacing);
        LegacyRand placementRandom = new LegacyRand(largeFeatureWithSalt(
                worldSeed, gridX, gridZ, set.salt));
        int limit = Math.subtractExact(set.spacing, set.separation);
        int potentialX = Math.addExact(Math.multiplyExact(gridX, set.spacing),
                spread(placementRandom, limit, set.spreadType));
        int potentialZ = Math.addExact(Math.multiplyExact(gridZ, set.spacing),
                spread(placementRandom, limit, set.spreadType));
        boolean placement = potentialX == centerChunkX && potentialZ == centerChunkZ;
        if (!placement) return candidate(set, centerChunkX, centerChunkZ,
                false, false, false, List.of());
        boolean frequency = frequencyAccepted(set, worldSeed, centerChunkX, centerChunkZ);
        boolean excluded = frequency && set.exclusionSet != null
                && world.hasStartCandidateInRange(set.exclusionSet, centerChunkX, centerChunkZ,
                        set.exclusionChunks);
        return candidate(set, centerChunkX, centerChunkZ, true, frequency, excluded,
                frequency && !excluded
                        ? weightedAttempts(set, worldSeed, centerChunkX, centerChunkZ) : List.of());
    }

    private static void preflight(WorldAccess world) {
        if (world == null || !world.hasPossibleSetFacts() || !world.hasExclusionStartFacts()
                || !world.hasPreferredBiomeSearch()) {
            throw new IllegalStateException("complete structure planning capabilities are required");
        }
    }

    private static List<BlockPos> buildRings(SetSpec set, long worldSeed, WorldAccess world) {
        LegacyRand random = new LegacyRand(worldSeed);
        double angle = random.nextDouble() * TWO_PI;
        int positionInCircle = 0;
        int circle = 0;
        int spread = set.ringSpread;
        List<BlockPos> result = new ArrayList<>(set.ringCount);
        for (int index = 0; index < set.ringCount; index++) {
            double distance = (double) (4 * set.ringDistance + set.ringDistance * circle * 6)
                    + (random.nextDouble() - 0.5D) * ((double) set.ringDistance * 2.5D);
            int initialX = Math.toIntExact(Math.round(Math.cos(angle) * distance));
            int initialZ = Math.toIntExact(Math.round(Math.sin(angle) * distance));
            long searchSeed = random.nextLong();
            int searchX = Math.addExact(Math.multiplyExact(initialX, 16), 8);
            int searchZ = Math.addExact(Math.multiplyExact(initialZ, 16), 8);
            Optional<BlockPos> preferred = world.findPreferredBiome(set.preferredBiomes,
                    searchX, 0, searchZ, 112, searchSeed);
            result.add(preferred.map(pos -> new BlockPos(Math.floorDiv(pos.x(), 16), 0,
                    Math.floorDiv(pos.z(), 16))).orElse(new BlockPos(initialX, 0, initialZ)));
            angle += TWO_PI / (double) spread;
            if (++positionInCircle == spread) {
                positionInCircle = 0;
                spread += 2 * spread / (++circle + 1);
                spread = Math.min(spread, set.ringCount - index);
                angle += random.nextDouble() * TWO_PI;
            }
        }
        return List.copyOf(result);
    }

    private static Candidate candidate(SetSpec set, int chunkX, int chunkZ,
            boolean placementChunk, boolean frequencyAccepted, boolean excluded,
            List<Attempt> attempts) {
        int locateX = Math.addExact(Math.multiplyExact(chunkX, 16), set.locateOffset.x());
        int locateZ = Math.addExact(Math.multiplyExact(chunkZ, 16), set.locateOffset.z());
        return new Candidate(set.key, chunkX, chunkZ,
                new BlockPos(locateX, set.locateOffset.y(), locateZ), placementChunk,
                frequencyAccepted, excluded, attempts);
    }

    private static List<Attempt> weightedAttempts(SetSpec set, long seed, int x, int z) {
        if (set.members.size() == 1) {
            WeightedMember member = set.members.getFirst();
            return List.of(new Attempt(member.key, member.weight));
        }
        List<WeightedMember> remaining = new ArrayList<>(set.members);
        LegacyRand random = new LegacyRand(largeFeatureSeed(seed, x, z));
        int total = remaining.stream().mapToInt(WeightedMember::weight).sum();
        List<Attempt> attempts = new ArrayList<>(remaining.size());
        while (!remaining.isEmpty()) {
            int choice = random.nextInt(total);
            int selected = 0;
            while ((choice -= remaining.get(selected).weight) >= 0) selected++;
            WeightedMember member = remaining.remove(selected);
            attempts.add(new Attempt(member.key, member.weight));
            total -= member.weight;
        }
        return List.copyOf(attempts);
    }

    private static boolean frequencyAccepted(SetSpec set, long seed, int x, int z) {
        if (!(set.frequency < 1.0F)) return true;
        return switch (set.reduction) {
            case DEFAULT -> new LegacyRand(largeFeatureWithSalt(seed, set.salt, x, z))
                    .nextFloat() < set.frequency;
            case LEGACY_TYPE_1 -> {
                int cx = x >> 4, cz = z >> 4;
                LegacyRand random = new LegacyRand((long) (cx ^ (cz << 4)) ^ seed);
                random.nextInt();
                yield random.nextInt((int) (1.0F / set.frequency)) == 0;
            }
            case LEGACY_TYPE_2 -> new LegacyRand(largeFeatureWithSalt(
                    seed, x, z, ARBITRARY_FREQUENCY_SALT)).nextFloat() < set.frequency;
            case LEGACY_TYPE_3 -> new LegacyRand(largeFeatureSeed(seed, x, z)).nextDouble()
                    < (double) set.frequency;
        };
    }

    private static int spread(LegacyRand random, int limit, SpreadType type) {
        return type == SpreadType.LINEAR ? random.nextInt(limit)
                : (random.nextInt(limit) + random.nextInt(limit)) / 2;
    }

    private static long largeFeatureWithSalt(long seed, int x, int z, int salt) {
        return (long) x * LARGE_FEATURE_X + (long) z * LARGE_FEATURE_Z + seed + salt;
    }

    private static long largeFeatureSeed(long seed, int x, int z) {
        LegacyRand random = new LegacyRand(seed);
        return (long) x * random.nextLong() ^ (long) z * random.nextLong() ^ seed;
    }

    private SetSpec requireSet(String key) {
        SetSpec set = byKey.get(key);
        if (set == null) throw new IllegalArgumentException("unknown pinned set: " + key);
        return set;
    }

    private static List<String> memberKeys(SetSpec set) {
        return set.members.stream().map(WeightedMember::key).toList();
    }

    private static SetSpec parse(Mc263StructureIndexReceipt.PlacementRecord record) {
        List<WeightedMember> members = parseMembers(record.weightedStructures());
        BlockPos offset = parseOffset(record.locateOffset());
        if (record.type().equals("minecraft:random_spread")) {
            if (record.spacing() <= record.separation() || record.salt() < 0) {
                throw new IllegalArgumentException("invalid random-spread receipt");
            }
            return new SetSpec(record.key(), Kind.RANDOM_SPREAD, record.spacing(),
                    record.separation(), record.salt(), SpreadType.parse(record.spreadType()),
                    (float) record.frequency(), Reduction.parse(record.frequencyReductionMethod()),
                    offset, record.exclusionSet().isBlank() ? null : record.exclusionSet(),
                    record.exclusionChunks(), -1, -1, -1, null, members);
        }
        if (!record.type().equals("minecraft:concentric_rings")) {
            throw new IllegalArgumentException("unknown placement type");
        }
        return new SetSpec(record.key(), Kind.CONCENTRIC_RINGS, -1, -1, 0,
                SpreadType.LINEAR, 1.0F, Reduction.DEFAULT, offset, null, -1,
                record.distance(), record.spread(), record.count(), record.preferredBiomes(), members);
    }

    private static List<WeightedMember> parseMembers(String text) {
        List<WeightedMember> result = new ArrayList<>();
        Set<String> unique = new HashSet<>();
        for (String token : text.split(",", -1)) {
            String[] pair = token.split("=", -1);
            if (pair.length != 2 || !unique.add(pair[0])) throw new IllegalArgumentException("members");
            int weight = Integer.parseInt(pair[1]);
            if (weight <= 0) throw new IllegalArgumentException("weight");
            result.add(new WeightedMember(pair[0], weight));
        }
        if (result.isEmpty()) throw new IllegalArgumentException("members");
        return List.copyOf(result);
    }

    private static BlockPos parseOffset(String text) {
        if (text.isBlank()) return new BlockPos(0, 0, 0);
        String[] p = text.split(",", -1);
        if (p.length != 3) throw new IllegalArgumentException("offset");
        return new BlockPos(Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2]));
    }

    public static byte[] receiptBytes(Plan plan) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            out.write(RECEIPT_MAGIC); out.writeLong(plan.worldSeed());
            out.writeInt(plan.centerChunkX()); out.writeInt(plan.centerChunkZ());
            out.writeInt(plan.candidates().size());
            for (Candidate c : plan.candidates()) {
                writeString(out, c.setKey()); out.writeInt(c.chunkX()); out.writeInt(c.chunkZ());
                out.writeInt(c.locatePos().x()); out.writeInt(c.locatePos().y());
                out.writeInt(c.locatePos().z()); out.writeBoolean(c.placementChunk());
                out.writeBoolean(c.frequencyAccepted()); out.writeBoolean(c.excluded());
                out.writeInt(c.attempts().size());
                for (Attempt a : c.attempts()) { writeString(out, a.structureKey()); out.writeInt(a.weight()); }
            }
            out.flush(); return bytes.toByteArray();
        } catch (IOException impossible) { throw new IllegalStateException(impossible); }
    }

    private static void writeString(DataOutputStream out, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        out.writeShort(bytes.length); out.write(bytes);
    }

    public interface WorldAccess {
        boolean hasPossibleSetFacts();
        boolean hasExclusionStartFacts();
        boolean hasPreferredBiomeSearch();
        boolean isStructureSetPossible(String setKey, List<String> memberKeys);
        boolean hasStartCandidateInRange(String setKey, int chunkX, int chunkZ, int range);
        Optional<BlockPos> findPreferredBiome(String tag, int x, int y, int z, int radius,
                long legacyForkSeed);
    }

    public record PlanningState(long worldSeed, List<String> possibleSetKeys,
            List<BlockPos> strongholdRingPositions) {
        public PlanningState {
            if (possibleSetKeys == null || strongholdRingPositions == null
                    || possibleSetKeys.stream().anyMatch(java.util.Objects::isNull)
                    || strongholdRingPositions.stream().anyMatch(java.util.Objects::isNull)) {
                throw new IllegalArgumentException("null structure planning state");
            }
            possibleSetKeys = List.copyOf(possibleSetKeys);
            strongholdRingPositions = List.copyOf(strongholdRingPositions);
        }
    }
    public record Plan(long worldSeed, int centerChunkX, int centerChunkZ,
            List<Candidate> candidates) { public Plan { candidates = List.copyOf(candidates); } }
    public record Candidate(String setKey, int chunkX, int chunkZ, BlockPos locatePos,
            boolean placementChunk, boolean frequencyAccepted, boolean excluded,
            List<Attempt> attempts) { public Candidate { attempts = List.copyOf(attempts); } }
    public record Attempt(String structureKey, int weight) { }
    public record BlockPos(int x, int y, int z) { }

    private record WeightedMember(String key, int weight) { }
    private record SetSpec(String key, Kind kind, int spacing, int separation, int salt,
            SpreadType spreadType, float frequency, Reduction reduction, BlockPos locateOffset,
            String exclusionSet, int exclusionChunks, int ringDistance, int ringSpread,
            int ringCount, String preferredBiomes, List<WeightedMember> members) { }
    private enum Kind { RANDOM_SPREAD, CONCENTRIC_RINGS }
    private enum SpreadType { LINEAR, TRIANGULAR;
        static SpreadType parse(String value) { return switch (value) {
            case "linear" -> LINEAR; case "triangular" -> TRIANGULAR;
            default -> throw new IllegalArgumentException("spread"); }; } }
    private enum Reduction { DEFAULT, LEGACY_TYPE_1, LEGACY_TYPE_2, LEGACY_TYPE_3;
        static Reduction parse(String value) { return switch (value) {
            case "default" -> DEFAULT; case "legacy_type_1" -> LEGACY_TYPE_1;
            case "legacy_type_2" -> LEGACY_TYPE_2; case "legacy_type_3" -> LEGACY_TYPE_3;
            default -> throw new IllegalArgumentException("reduction"); }; } }
}
