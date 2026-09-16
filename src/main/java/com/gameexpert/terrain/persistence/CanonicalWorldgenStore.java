package com.gameexpert.terrain.persistence;

import com.gameexpert.terrain.Blocks;
import com.gameexpert.terrain.mc.feature.Mc263FinalChunkCodec;
import com.gameexpert.terrain.mc.feature.Mc263FinalChunkSidecars;
import com.gameexpert.terrain.mc.loot.Mc263LocatedMapAuthority;
import com.gameexpert.terrain.mc.structure.Mc263StructureCarrier;
import com.gameexpert.world.WorldBaseline;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.IntPredicate;

/** Current-only persistence contract for canonical structure and final-carrier state. */
public interface CanonicalWorldgenStore {
    int ABI_VERSION = 5;
    int FINAL_CARRIER_SCHEMA = Mc263FinalChunkCodec.SCHEMA;
    int LANE_COUNT = 9;

    CanonicalChunkSnapshot commit(ChunkCommit commit);

    /** A bounded publication batch; every member retains its individual exact replay contract. */
    default void commitBatch(List<ChunkCommit> commits) {
        for (ChunkCommit commit : List.copyOf(commits)) commit(commit);
    }

    CanonicalChunkSnapshot find(long worldId, int chunkX, int chunkZ);

    default com.gameexpert.authority.versioned.CanonicalStructureSnapshot structureSnapshot(
            long worldId, com.gameexpert.world.WorldGenerationProfile profile) {
        throw new IllegalStateException("atomic structure snapshot is unavailable");
    }

    default com.gameexpert.authority.versioned.CanonicalStructureSnapshot.Row structureRow(
            long worldId, com.gameexpert.world.WorldGenerationProfile profile, int x, int z) {
        return structureSnapshot(worldId, profile).rows().stream()
                .filter(row -> row.chunkX() == x && row.chunkZ() == z).findFirst().orElse(null);
    }

    /**
     * Publishes CURRENT generation declarations without reading mutable world references.
     * Implementations retain world/profile serialization and replay an existing immutable winner.
     * Located-map references are resolved later, when the declared loot is opened.
     */
    default CanonicalChunkSnapshot commitGeneratedDeclaration(ChunkCommit commit) {
        throw new IllegalStateException("declaration publication is unavailable");
    }

    /** False discards a stale proposal; callers reread committed state and regenerate as needed. */
    default boolean commitFromSnapshot(ChunkCommit commit, String expectedReferenceReceipt) {
        throw new IllegalStateException("atomic profile publication is unavailable");
    }


    /**
     * True when this world has a committed canonical product for the chunk.
     *
     * <p>Separate from {@link #find} because the answer is one bit and the row is not: each row
     * carries three longblob carriers, and reading them to test for existence was the single
     * hottest frame on the production workers. Implementations that hold rows in memory can keep
     * the default; a database-backed one must answer without loading the carriers.</p>
     */
    default boolean isCommitted(long worldId, int chunkX, int chunkZ) {
        return find(worldId, chunkX, chunkZ) != null;
    }

    /**
     * Atomically freezes all committed structure carriers for one world. Implementations must
     * enumerate under their existing store transaction/monitor and pass that complete immutable
     * list to {@link #freezeStructureReferences}; a per-candidate lazy lookup is not a snapshot.
     */
    default Mc263LocatedMapAuthority.StructureReferenceSnapshot structureReferenceSnapshot(
            long worldId) {
        throw new IllegalStateException(
                "canonical worldgen store has no atomic structure-reference snapshot adapter");
    }

    /** Builds the exact immutable located-map reference view from a complete store enumeration. */
    static Mc263LocatedMapAuthority.StructureReferenceSnapshot freezeStructureReferences(
            long worldId, List<CanonicalChunkSnapshot> snapshots) {
        var profile = snapshots.isEmpty()
                ? com.gameexpert.world.WorldGenerationProfiles.CURRENT
                : com.gameexpert.world.WorldGenerationProfiles.requireSupportedBaselineId(
                        snapshots.get(0).commit().worldIdentity());
        return freezeStructureReferences(worldId, profile, snapshots);
    }

    static Mc263LocatedMapAuthority.StructureReferenceSnapshot freezeStructureReferences(
            long worldId, com.gameexpert.world.WorldGenerationProfile profile,
            List<CanonicalChunkSnapshot> snapshots) {
        if (worldId <= 0L) throw new IllegalArgumentException("positive world ID required");
        Objects.requireNonNull(snapshots, "canonical structure snapshots");
        int[] lengths = snapshots.stream().mapToInt(row -> row.commit().structureCarrierLength()).toArray();
        structureReferenceSnapshotBinaryLength(snapshots.size(), lengths);
        List<com.gameexpert.authority.versioned.CanonicalStructureSnapshot.Row> rows = snapshots.stream()
                .map(snapshot -> {
                    ChunkCommit commit = snapshot.commit();
                    if (commit.worldId() != worldId || !profile.getBaselineId().equals(commit.worldIdentity())) {
                        throw new IllegalArgumentException("mixed structure snapshot profile/world");
                    }
                    return com.gameexpert.authority.versioned.CanonicalStructureSnapshot.Row.lazy(
                            commit.chunkX(), commit.chunkZ(), commit.structureCarrierLength(), commit::structureCarrier);
                }).toList();
        return new com.gameexpert.authority.versioned.CanonicalStructureSnapshot(worldId, profile, rows).references();
    }

    /** Exact SRS26301 wrapper size, used to reject inadmissible snapshots before hashing. */
    private static long structureReferenceSnapshotBinaryLength(int rowCount, int[] carrierLengths) {
        validateStructureReferenceSnapshotRowCount(rowCount);
        Objects.requireNonNull(carrierLengths, "structure carrier lengths");
        if (carrierLengths.length != rowCount) {
            throw new IllegalArgumentException("structure-reference snapshot row count mismatch");
        }
        long length = 8L + 1L + Long.BYTES + Integer.BYTES + 32L;
        for (int carrierLength : carrierLengths) {
            if (carrierLength <= 0 || carrierLength > 16 * 1024 * 1024) {
                throw new IllegalArgumentException(
                        "structure carrier length is outside snapshot bounds");
            }
            try {
                length = Math.addExact(length, Math.addExact(12L, carrierLength));
            } catch (ArithmeticException overflow) {
                throw new IllegalArgumentException("structure-reference snapshot size overflow",
                        overflow);
            }
        }
        return length;
    }

    private static void validateStructureReferenceSnapshotRowCount(int rowCount) {
        if (rowCount < 0) {
            throw new IllegalArgumentException("structure carrier count exceeds snapshot bounds");
        }
    }

    record StructureStartKey(String structureKey, int chunkX, int chunkZ) {
        public StructureStartKey {
            if (structureKey == null
                    || !structureKey.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) {
                throw new IllegalArgumentException("invalid canonical structure key");
            }
        }
    }

    LaneReceipt claim(long worldId, int chunkX, int chunkZ, Lane lane);

    void acknowledge(long worldId, int chunkX, int chunkZ, Lane lane, byte[] receipt);

    void reject(long worldId, int chunkX, int chunkZ, Lane lane, byte[] receipt);

    interface WholeBboxCommitter extends CanonicalWorldgenStore {
        List<CanonicalChunkSnapshot> commitWholeBbox(WholeBboxCommit group);
    }

    enum Lane {
        BLOCK_TICKS(0), FLUID_TICKS(1), LOOT(2), SPAWNERS(3), OWNERS(4),
        ARCHAEOLOGY(5), BEES(6), BLOCK_ENTITIES(7), ENTITIES(8);

        private final int bit;
        Lane(int bit) { this.bit = bit; }
        public int bit() { return bit; }
        public int mask() { return 1 << bit; }
    }

    final class ChunkCommit {
        private final long worldId;
        private final String worldIdentity;
        private final int chunkX, chunkZ;
        private final byte[] finalCarrier;
        private final byte[] structureCarrier;
        private final byte[] mutablePieceSuccessor;
        private final byte[] fingerprint;
        private final Mc263StructureCarrier semanticStructureCarrier;
        private final com.gameexpert.authority.versioned.NeutralFinalChunk semanticFinalChunk;
        private final List<Integer> referenceCounts;

        public ChunkCommit(long worldId, String worldIdentity, int chunkX, int chunkZ,
                byte[] finalCarrier, byte[] structureCarrier, byte[] mutablePieceSuccessor,
                byte[] fingerprint) {
            this(worldId, worldIdentity, chunkX, chunkZ, finalCarrier, structureCarrier,
                    mutablePieceSuccessor, fingerprint, new int[0]);
        }

        /**
         * Builds the one atomic commit after removing every generated sidecar whose cell is
         * already owned by a player override. The block payload itself remains the canonical
         * generated baseline; player edits are layered by the authority after loading it.
         */
        public ChunkCommit(long worldId, String worldIdentity, int chunkX, int chunkZ,
                byte[] finalCarrier, byte[] structureCarrier, byte[] mutablePieceSuccessor,
                byte[] fingerprint, int[] playerOverriddenCells) {
            if (worldId <= 0) throw new IllegalArgumentException("world ID must be positive");
            this.worldId = worldId;
            this.worldIdentity = requireText(worldIdentity, "world identity");
            var profile = com.gameexpert.world.WorldGenerationProfiles.requireSupportedBaselineId(this.worldIdentity);
            this.chunkX = chunkX; this.chunkZ = chunkZ;
            var validated = com.gameexpert.authority.versioned.ProducerStoreWire.validate(
                    com.gameexpert.authority.versioned.ProducerAuthorities.forProfile(profile),
                    profile, worldId, chunkX, chunkZ, finalCarrier, structureCarrier,
                    mutablePieceSuccessor, fingerprint, playerOverriddenCells);
            this.finalCarrier = validated.finalCarrier();
            this.semanticFinalChunk = validated.semantic();
            this.structureCarrier = validated.structureCarrier();
            this.semanticStructureCarrier = null;
            this.referenceCounts = validated.referenceCounts();
            this.mutablePieceSuccessor = validated.successor();
            this.fingerprint = validated.fingerprint();
            if (this.fingerprint.length != 32) {
                throw new IllegalArgumentException("commit fingerprint must be SHA-256");
            }
        }
        public long worldId() { return worldId; }
        public String worldIdentity() { return worldIdentity; }
        public int chunkX() { return chunkX; }
        public int chunkZ() { return chunkZ; }
        public byte[] finalCarrier() { return finalCarrier.clone(); }
        /**
         * The committed carrier's proven decoded view, decoded once when this commit was built.
         *
         * <p>AGENTS rule 10l, and the same amortization {@link #semanticStructureCarrier} already
         * holds: the stored bytes are immutable, so replay reuses this view instead of paying a
         * second schema-4 decode of the identical payload.</p>
         */
        public com.gameexpert.authority.versioned.NeutralFinalChunk semanticFinalChunk() { return semanticFinalChunk; }
        public byte[] structureCarrier() { return structureCarrier.clone(); }
        int structureCarrierLength() { return structureCarrier.length; }
        public byte[] mutablePieceSuccessor() { return copyNullable(mutablePieceSuccessor); }
        public byte[] fingerprint() { return fingerprint.clone(); }
        public List<Mc263StructureCarrier.ChunkStarts> orderedStarts() {
            if (semanticStructureCarrier == null) throw new IllegalStateException("profile-specific structure diagnostics required");
            return semanticStructureCarrier.startChunks();
        }
        public List<Mc263StructureCarrier.ChunkReferences> orderedReferences() {
            if (semanticStructureCarrier == null) throw new IllegalStateException("profile-specific structure diagnostics required");
            return semanticStructureCarrier.referenceChunks();
        }
        /** Reference counts in persisted start-chunk/start-map encounter order. */
        public List<Integer> orderedReferenceCounts() {
            return referenceCounts;
        }
    }

    /** One ordered, complete and fingerprinted whole-bounding-box publication. */
    final class WholeBboxCommit {
        private static final byte[] FINGERPRINT_DOMAIN =
                "canonical-worldgen-whole-bbox-v1".getBytes(StandardCharsets.US_ASCII);

        private final int minChunkX;
        private final int minChunkZ;
        private final int maxChunkX;
        private final int maxChunkZ;
        private final List<ChunkCommit> commits;
        private final byte[] fingerprint;

        public WholeBboxCommit(int minChunkX, int minChunkZ, int maxChunkX, int maxChunkZ,
                List<ChunkCommit> commits, byte[] fingerprint) {
            if (minChunkX > maxChunkX || minChunkZ > maxChunkZ) {
                throw new IllegalArgumentException("whole-bbox chunk bounds are inverted");
            }
            Objects.requireNonNull(commits, "whole-bbox commits");
            if (commits.isEmpty()) {
                throw new IllegalArgumentException("whole-bbox commit group is empty");
            }
            long chunkXCount = (long) maxChunkX - minChunkX + 1;
            long chunkZCount = (long) maxChunkZ - minChunkZ + 1;
            long expected;
            try {
                expected = Math.multiplyExact(chunkXCount, chunkZCount);
            } catch (ArithmeticException overflow) {
                throw new IllegalArgumentException("whole-bbox commit group is too large",
                        overflow);
            }
            if (expected != commits.size()) {
                throw new IllegalArgumentException("whole-bbox commit group is incomplete");
            }
            ArrayList<ChunkCommit> ordered = new ArrayList<>(commits.size());
            long worldId = -1;
            String worldIdentity = null;
            int index = 0;
            for (long xOffset = 0; xOffset < chunkXCount; xOffset++) {
                int chunkX = (int) (minChunkX + xOffset);
                for (long zOffset = 0; zOffset < chunkZCount; zOffset++) {
                    int chunkZ = (int) (minChunkZ + zOffset);
                    ChunkCommit commit = Objects.requireNonNull(commits.get(index),
                            "whole-bbox commit member");
                    if (commit.chunkX() != chunkX || commit.chunkZ() != chunkZ) {
                        throw new IllegalArgumentException(
                                "whole-bbox targets are missing, duplicate, or out of order");
                    }
                    if (index == 0) {
                        worldId = commit.worldId();
                        worldIdentity = commit.worldIdentity();
                    } else if (worldId != commit.worldId()
                            || !worldIdentity.equals(commit.worldIdentity())) {
                        throw new IllegalArgumentException(
                                "whole-bbox commits do not share one world identity");
                    }
                    if (commit.mutablePieceSuccessor() == null
                            || commit.mutablePieceSuccessor().length == 0) {
                        throw new IllegalArgumentException(
                                "whole-bbox commit requires every mutable successor");
                    }
                    ordered.add(commit);
                    index++;
                }
            }
            this.minChunkX = minChunkX;
            this.minChunkZ = minChunkZ;
            this.maxChunkX = maxChunkX;
            this.maxChunkZ = maxChunkZ;
            this.commits = List.copyOf(ordered);
            this.fingerprint = copy(fingerprint, "whole-bbox group fingerprint");
            if (this.fingerprint.length != 32) {
                throw new IllegalArgumentException(
                        "whole-bbox group fingerprint must be SHA-256");
            }
            byte[] expectedFingerprint = fingerprintFor(
                    minChunkX, minChunkZ, maxChunkX, maxChunkZ, this.commits);
            if (!MessageDigest.isEqual(this.fingerprint, expectedFingerprint)) {
                throw new IllegalArgumentException("whole-bbox group fingerprint mismatch");
            }
        }

        public int minChunkX() { return minChunkX; }
        public int minChunkZ() { return minChunkZ; }
        public int maxChunkX() { return maxChunkX; }
        public int maxChunkZ() { return maxChunkZ; }
        public List<ChunkCommit> commits() { return commits; }
        public byte[] fingerprint() { return fingerprint.clone(); }

        public static byte[] fingerprintFor(int minChunkX, int minChunkZ, int maxChunkX,
                int maxChunkZ, List<ChunkCommit> commits) {
            Objects.requireNonNull(commits, "whole-bbox commits");
            try {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                digest.update(FINGERPRINT_DOMAIN);
                updateInt(digest, minChunkX); updateInt(digest, minChunkZ);
                updateInt(digest, maxChunkX); updateInt(digest, maxChunkZ);
                updateInt(digest, commits.size());
                for (ChunkCommit commit : commits) {
                    Objects.requireNonNull(commit, "whole-bbox commit member");
                    updateLong(digest, commit.worldId());
                    updateBytes(digest, commit.worldIdentity().getBytes(StandardCharsets.UTF_8));
                    updateInt(digest, commit.chunkX()); updateInt(digest, commit.chunkZ());
                    updateBytes(digest, commit.finalCarrier());
                    updateBytes(digest, commit.structureCarrier());
                    byte[] successor = commit.mutablePieceSuccessor();
                    if (successor == null) {
                        updateInt(digest, -1);
                    } else {
                        updateBytes(digest, successor);
                    }
                    updateBytes(digest, commit.fingerprint());
                }
                return digest.digest();
            } catch (NoSuchAlgorithmException impossible) {
                throw new AssertionError(impossible);
            }
        }

        private static void updateBytes(MessageDigest digest, byte[] value) {
            updateInt(digest, value.length);
            digest.update(value);
        }

        private static void updateInt(MessageDigest digest, int value) {
            digest.update((byte) (value >>> 24));
            digest.update((byte) (value >>> 16));
            digest.update((byte) (value >>> 8));
            digest.update((byte) value);
        }

        private static void updateLong(MessageDigest digest, long value) {
            updateInt(digest, (int) (value >>> 32));
            updateInt(digest, (int) value);
        }
    }

    final class CanonicalChunkSnapshot {
        private final ChunkCommit commit;
        private final int laneClaimMask;
        private final int laneAckMask;
        private final int laneRejectedMask;

        public CanonicalChunkSnapshot(ChunkCommit commit, int laneClaimMask, int laneAckMask,
                int laneRejectedMask) {
            this.commit = Objects.requireNonNull(commit, "chunk commit");
            int known = (1 << LANE_COUNT) - 1;
            if (((laneClaimMask | laneAckMask | laneRejectedMask) & ~known) != 0) {
                throw new IllegalArgumentException("lane state contains an unknown lane");
            }
            if ((laneAckMask & laneRejectedMask) != 0) {
                throw new IllegalArgumentException("lane cannot be acknowledged and rejected");
            }
            if (((laneAckMask | laneRejectedMask) & ~laneClaimMask) != 0) {
                throw new IllegalArgumentException("terminal lane was never claimed");
            }
            this.laneClaimMask = laneClaimMask;
            this.laneAckMask = laneAckMask;
            this.laneRejectedMask = laneRejectedMask;
        }
        public ChunkCommit commit() { return commit; }
        public int laneClaimMask() { return laneClaimMask; }
        public int laneAckMask() { return laneAckMask; }
        public int laneRejectedMask() { return laneRejectedMask; }
        public int laneTerminalMask() { return laneAckMask | laneRejectedMask; }
    }

    final class LaneReceipt {
        private final Lane lane;
        private final byte[] activation;
        private final byte[] receipt;
        private final com.gameexpert.authority.versioned.NeutralFinalChunk semanticActivation;

        public LaneReceipt(Lane lane, byte[] activation, byte[] receipt) {
            this.lane = Objects.requireNonNull(lane, "lane");
            this.activation = copy(activation, "lane activation");
            this.receipt = copy(receipt, "lane receipt");
            this.semanticActivation = null;
        }
        private LaneReceipt(Lane lane, ChunkCommit commit, byte[] receipt) {
            this.lane = Objects.requireNonNull(lane, "lane");
            ChunkCommit source = Objects.requireNonNull(commit, "chunk commit");
            this.activation = source.finalCarrier();
            this.receipt = copy(receipt, "lane receipt");
            this.semanticActivation = source.semanticFinalChunk();
        }
        /** Reuses only the proven decoded view of this immutable commit's exact bytes. */
        public static LaneReceipt fromCommit(Lane lane, ChunkCommit commit, byte[] receipt) {
            return new LaneReceipt(lane, commit, receipt);
        }
        public Lane lane() { return lane; }
        /** Exact schema-4 carrier from which the named semantic lane is activated. */
        public byte[] activation() { return activation.clone(); }
        /** Raw-byte claims still undergo strict decoding at the consumer's validation boundary. */
        public com.gameexpert.authority.versioned.NeutralFinalChunk semanticActivation() {
            if (semanticActivation != null) return semanticActivation;
            // The unbound constructor is the legacy raw-lane ABI. Production claims use fromCommit.
            if (activation.length < 50) throw new IllegalArgumentException("truncated legacy lane carrier");
            var target = java.nio.ByteBuffer.wrap(activation).position(42);
            int x = target.getInt(), z = target.getInt();
            return com.gameexpert.authority.versioned.ProducerAuthorities.verify(
                    com.gameexpert.world.WorldGenerationProfiles.CURRENT, x, z, activation);
        }
        public byte[] receipt() { return receipt.clone(); }
        public byte[] payload() { return receipt(); }
    }

    /** One filtered carrier and the proven decoded view of exactly those bytes. */
    record FilteredFinalCarrier(byte[] carrier, Mc263FinalChunkCodec.FinalChunk chunk) {}

    private static FilteredFinalCarrier filteredFinalCarrier(byte[] value, int chunkX, int chunkZ,
            int[] playerOverriddenCells) {
        byte[] carrier = copy(value, "final carrier");
        Mc263FinalChunkCodec.FinalChunk chunk = Mc263FinalChunkCodec.decode(carrier);
        if (chunk.chunkX() != chunkX || chunk.chunkZ() != chunkZ) {
            throw new IllegalArgumentException("final carrier coordinate mismatch");
        }
        Objects.requireNonNull(playerOverriddenCells, "player overridden cells");
        if (playerOverriddenCells.length == 0) return new FilteredFinalCarrier(carrier, chunk);
        boolean[] overridden = new boolean[Blocks.CHUNK_BLOCKS];
        for (int packed : playerOverriddenCells) {
            if (packed < 0 || packed >= overridden.length) {
                throw new IllegalArgumentException("player override outside chunk: " + packed);
            }
            overridden[packed] = true;
        }
        IntPredicate retained = packed -> !overridden[packed];
        Mc263FinalChunkSidecars source = chunk.sidecars();
        List<Mc263FinalChunkSidecars.Loot> loot = source.loot().stream()
                .filter(valueAt -> retained.test(valueAt.packed())).toList();
        List<Mc263FinalChunkSidecars.StructureEntity> entities = source.entities().stream()
                .filter(valueAt -> retained.test(entityPacked(chunk, valueAt))).toList();
        List<Mc263FinalChunkSidecars.ContainerLootDeclaration> declarations =
                filteredContainerLootDeclarations(chunk, source, overridden, loot, entities);
        Mc263FinalChunkSidecars filtered = new Mc263FinalChunkSidecars(
                source.blockTicks().stream().filter(valueAt -> retained.test(valueAt.packed())).toList(),
                source.fluidTicks().stream().filter(valueAt -> retained.test(valueAt.packed())).toList(),
                loot,
                source.spawners().stream().filter(valueAt -> retained.test(valueAt.packed())).toList(),
                source.owners().stream().filter(valueAt -> retained.test(valueAt.packed())).toList(),
                source.archaeology().stream().filter(valueAt -> retained.test(valueAt.packed())).toList(),
                source.bees().stream().filter(valueAt -> retained.test(valueAt.packed())).toList(),
                source.blockEntities().stream().filter(valueAt -> retained.test(valueAt.packed())).toList(),
                entities, declarations);
        byte[] filteredCarrier = Mc263FinalChunkCodec.encode(new Mc263FinalChunkCodec.FinalChunk(
                chunk.chunkX(), chunk.chunkZ(), chunk.blockIds(), chunk.stateOverrides(),
                chunk.worldSurfaceWg(), chunk.oceanFloorWg(), chunk.motionBlocking(), filtered));
        // The override path republishes bytes, so its view is decoded from what is actually stored.
        return new FilteredFinalCarrier(filteredCarrier,
                Mc263FinalChunkCodec.decode(filteredCarrier));
    }

    private static List<Mc263FinalChunkSidecars.ContainerLootDeclaration>
            filteredContainerLootDeclarations(Mc263FinalChunkCodec.FinalChunk chunk,
                    Mc263FinalChunkSidecars source, boolean[] overridden,
                    List<Mc263FinalChunkSidecars.Loot> filteredLoot,
                    List<Mc263FinalChunkSidecars.StructureEntity> filteredEntities) {
        List<Mc263FinalChunkSidecars.Loot> sourceLoot = source.loot();
        List<Mc263FinalChunkSidecars.StructureEntity> sourceEntities = source.entities();
        List<Mc263FinalChunkSidecars.ContainerLootDeclaration> sourceDeclarations =
                source.containerLootDeclarations();
        int expectedCount = Math.addExact(sourceLoot.size(), (int) sourceEntities.stream()
                .filter(value -> !value.lootTable().isEmpty()).count());
        if (sourceDeclarations.size() != expectedCount) {
            throw new IllegalArgumentException(
                    "ambiguous LOOT/ENTS declaration count before override encode");
        }

        ArrayList<Mc263FinalChunkSidecars.ContainerLootDeclaration> filteredDeclarations =
                new ArrayList<>(expectedCount);
        int sourceDeclarationOrdinal = 0;
        int filteredLootOrdinal = 0;
        for (int sourceSectionOrdinal = 0; sourceSectionOrdinal < sourceLoot.size();
                sourceSectionOrdinal++) {
            Mc263FinalChunkSidecars.Loot row = sourceLoot.get(sourceSectionOrdinal);
            Mc263FinalChunkSidecars.ContainerLootDeclaration declaration =
                    sourceDeclarations.get(sourceDeclarationOrdinal);
            Mc263FinalChunkSidecars.ContainerLootDeclaration validated =
                    Mc263FinalChunkSidecars.validateAndRebindContainerLootDeclaration(
                            declaration, sourceDeclarationOrdinal, sourceSectionOrdinal,
                            chunk.chunkX(), chunk.chunkZ(), row);
            if (validated != declaration) {
                throw new IllegalArgumentException(
                        "ambiguous LOOT declaration/source-row mismatch before override encode");
            }
            if (!overridden[row.packed()]) {
                filteredDeclarations.add(
                        Mc263FinalChunkSidecars.validateAndRebindContainerLootDeclaration(
                                validated, filteredDeclarations.size(), filteredLootOrdinal,
                                chunk.chunkX(), chunk.chunkZ(), row));
                filteredLootOrdinal++;
            }
            sourceDeclarationOrdinal++;
        }

        int filteredEntityOrdinal = 0;
        for (int sourceSectionOrdinal = 0; sourceSectionOrdinal < sourceEntities.size();
                sourceSectionOrdinal++) {
            Mc263FinalChunkSidecars.StructureEntity row = sourceEntities.get(sourceSectionOrdinal);
            boolean retained = !overridden[entityPacked(chunk, row)];
            if (!row.lootTable().isEmpty()) {
                Mc263FinalChunkSidecars.ContainerLootDeclaration declaration =
                        sourceDeclarations.get(sourceDeclarationOrdinal);
                Mc263FinalChunkSidecars.ContainerLootDeclaration validated =
                        Mc263FinalChunkSidecars.validateAndRebindContainerLootDeclaration(
                                declaration, sourceDeclarationOrdinal, sourceSectionOrdinal,
                                chunk.chunkX(), chunk.chunkZ(), row);
                if (validated != declaration) {
                    throw new IllegalArgumentException(
                            "ambiguous ENTS declaration/source-row mismatch before override encode");
                }
                if (retained) {
                    filteredDeclarations.add(
                            Mc263FinalChunkSidecars.validateAndRebindContainerLootDeclaration(
                                    validated, filteredDeclarations.size(), filteredEntityOrdinal,
                                    chunk.chunkX(), chunk.chunkZ(), row));
                }
                sourceDeclarationOrdinal++;
            }
            if (retained) filteredEntityOrdinal++;
        }
        if (sourceDeclarationOrdinal != sourceDeclarations.size()
                || filteredLootOrdinal != filteredLoot.size()
                || filteredEntityOrdinal != filteredEntities.size()
                || filteredDeclarations.size() != filteredLoot.size()
                        + (int) filteredEntities.stream()
                                .filter(value -> !value.lootTable().isEmpty()).count()) {
            throw new IllegalArgumentException(
                    "ambiguous LOOT/ENTS source-row mapping before override encode");
        }
        return List.copyOf(filteredDeclarations);
    }

    private static int entityPacked(Mc263FinalChunkCodec.FinalChunk chunk,
            Mc263FinalChunkSidecars.StructureEntity entity) {
        int x = (int) Math.floorMod((long) Math.floor(entity.x()), 16L);
        int y = (int) Math.floor(entity.y());
        int z = (int) Math.floorMod((long) Math.floor(entity.z()), 16L);
        return Blocks.blockIndex(x, y, z);
    }

    static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank() || value.length() > 160) {
            throw new IllegalArgumentException(name + " is blank or too long");
        }
        return value;
    }

    static byte[] copy(byte[] value, String name) {
        Objects.requireNonNull(value, name);
        if (value.length == 0) throw new IllegalArgumentException(name + " is empty");
        return Arrays.copyOf(value, value.length);
    }

    static byte[] copyNullable(byte[] value) {
        return value == null ? null : Arrays.copyOf(value, value.length);
    }

    private static void updateInt(MessageDigest digest, int value) {
        digest.update((byte) (value >>> 24));
        digest.update((byte) (value >>> 16));
        digest.update((byte) (value >>> 8));
        digest.update((byte) value);
    }

    private static void updateLong(MessageDigest digest, long value) {
        updateInt(digest, (int) (value >>> 32));
        updateInt(digest, (int) value);
    }
}
