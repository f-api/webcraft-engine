package com.gameexpert.terrain.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.util.Arrays;

/** MySQL row for one current canonical final-carrier commit. */
@Entity
@org.hibernate.annotations.DynamicUpdate
@Table(name = "canonical_worldgen_chunks", uniqueConstraints = @UniqueConstraint(
        name = "uk_canonical_worldgen_chunk", columnNames = {"world_id", "chunk_x", "chunk_z"}))
public class CanonicalWorldgenChunk {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false) private long worldId;
    @Column(nullable = false, length = 160) private String worldIdentity;
    @Column(nullable = false) private int chunkX;
    @Column(nullable = false) private int chunkZ;
    @Column(nullable = false) private int abiVersion;
    @Column(nullable = false) private int carrierSchema;
    @Lob @Column(nullable = false, columnDefinition = "longblob") private byte[] finalCarrier;
    @Lob @Column(nullable = false, columnDefinition = "longblob") private byte[] structureCarrier;
    @Lob @Column(nullable = false, columnDefinition = "longblob")
    private byte[] mutablePieceSuccessor;
    @Column(nullable = false, length = 32) private byte[] commitFingerprint;
    @Column(length = 32) private byte[] groupFingerprint;
    private Integer groupMinChunkX;
    private Integer groupMinChunkZ;
    private Integer groupMaxChunkX;
    private Integer groupMaxChunkZ;
    private Integer groupOrdinal;
    private Integer groupSize;
    @Column(nullable = false) private int laneClaimMask;
    @Column(nullable = false) private int laneAckMask;
    @Column(nullable = false) private int laneRejectedMask;
    @Version @Column(nullable = false) private long rowRevision;

    protected CanonicalWorldgenChunk() {}

    /** Unmanaged view reuses the same ABI, group and carrier validation as entity reads. */
    static CanonicalWorldgenChunk fromSnapshotRow(CanonicalWorldgenChunkRepository.SnapshotRow source) {
        CanonicalWorldgenChunk row = new CanonicalWorldgenChunk();
        row.worldId = source.getWorldId();
        row.worldIdentity = source.getWorldIdentity();
        row.chunkX = source.getChunkX();
        row.chunkZ = source.getChunkZ();
        row.abiVersion = source.getAbiVersion();
        row.carrierSchema = source.getCarrierSchema();
        row.finalCarrier = source.getFinalCarrier();
        row.structureCarrier = source.getStructureCarrier();
        row.mutablePieceSuccessor = source.getMutablePieceSuccessor();
        row.commitFingerprint = source.getCommitFingerprint();
        row.groupFingerprint = source.getGroupFingerprint();
        row.groupMinChunkX = source.getGroupMinChunkX();
        row.groupMinChunkZ = source.getGroupMinChunkZ();
        row.groupMaxChunkX = source.getGroupMaxChunkX();
        row.groupMaxChunkZ = source.getGroupMaxChunkZ();
        row.groupOrdinal = source.getGroupOrdinal();
        row.groupSize = source.getGroupSize();
        row.laneClaimMask = source.getLaneClaimMask();
        row.laneAckMask = source.getLaneAckMask();
        row.laneRejectedMask = source.getLaneRejectedMask();
        return row;
    }

    public CanonicalWorldgenChunk(CanonicalWorldgenStore.ChunkCommit commit) {
        initialize(commit);
    }

    public CanonicalWorldgenChunk(CanonicalWorldgenStore.ChunkCommit commit,
            CanonicalWorldgenStore.WholeBboxCommit group, int ordinal) {
        initialize(commit);
        requireGroupMember(commit, group, ordinal);
        this.groupFingerprint = group.fingerprint();
        this.groupMinChunkX = group.minChunkX();
        this.groupMinChunkZ = group.minChunkZ();
        this.groupMaxChunkX = group.maxChunkX();
        this.groupMaxChunkZ = group.maxChunkZ();
        this.groupOrdinal = ordinal;
        this.groupSize = group.commits().size();
    }

    private void initialize(CanonicalWorldgenStore.ChunkCommit commit) {
        this.worldId = commit.worldId(); this.worldIdentity = commit.worldIdentity();
        this.chunkX = commit.chunkX(); this.chunkZ = commit.chunkZ();
        this.abiVersion = CanonicalWorldgenStore.ABI_VERSION;
        this.carrierSchema = CanonicalWorldgenStore.FINAL_CARRIER_SCHEMA;
        this.finalCarrier = commit.finalCarrier(); this.structureCarrier = commit.structureCarrier();
        this.mutablePieceSuccessor = requireSuccessor(commit.mutablePieceSuccessor());
        this.commitFingerprint = commit.fingerprint();
    }

    public CanonicalWorldgenStore.ChunkCommit toCommit() {
        if (abiVersion != CanonicalWorldgenStore.ABI_VERSION
                || carrierSchema != CanonicalWorldgenStore.FINAL_CARRIER_SCHEMA) {
            throw new IllegalStateException("canonical worldgen row ABI/schema is not current");
        }
        validateGroupMetadata();
        return new CanonicalWorldgenStore.ChunkCommit(worldId, worldIdentity, chunkX, chunkZ,
                finalCarrier, structureCarrier, persistedSuccessor(), commitFingerprint);
    }
    public int laneClaimMask() { return laneClaimMask; }
    public int laneAckMask() { return laneAckMask; }
    public int laneRejectedMask() { return laneRejectedMask; }
    public boolean isUngrouped() {
        validateGroupMetadata();
        return groupFingerprint == null;
    }
    public byte[] groupFingerprint() {
        validateGroupMetadata();
        return groupFingerprint == null ? null : groupFingerprint.clone();
    }
    public Integer groupMinChunkX() { validateGroupMetadata(); return groupMinChunkX; }
    public Integer groupMinChunkZ() { validateGroupMetadata(); return groupMinChunkZ; }
    public Integer groupMaxChunkX() { validateGroupMetadata(); return groupMaxChunkX; }
    public Integer groupMaxChunkZ() { validateGroupMetadata(); return groupMaxChunkZ; }
    public Integer groupOrdinal() { validateGroupMetadata(); return groupOrdinal; }
    public Integer groupSize() { validateGroupMetadata(); return groupSize; }
    public boolean sameGroup(CanonicalWorldgenStore.WholeBboxCommit group, int ordinal) {
        validateGroupMetadata();
        requireGroupMember(toCommit(), group, ordinal);
        return Arrays.equals(groupFingerprint, group.fingerprint())
                && groupMinChunkX == group.minChunkX() && groupMinChunkZ == group.minChunkZ()
                && groupMaxChunkX == group.maxChunkX() && groupMaxChunkZ == group.maxChunkZ()
                && groupOrdinal == ordinal && groupSize == group.commits().size();
    }
    public boolean isTerminal(int mask) { return ((laneAckMask | laneRejectedMask) & mask) != 0; }
    public void claim(int mask) { laneClaimMask |= mask; }
    public void acknowledge(int mask) {
        if ((laneRejectedMask & mask) != 0) throw new IllegalStateException("lane is rejected");
        laneClaimMask |= mask;
        laneAckMask |= mask;
    }
    public void reject(int mask) {
        if ((laneAckMask & mask) != 0) throw new IllegalStateException("lane is acknowledged");
        laneClaimMask |= mask;
        laneRejectedMask |= mask;
    }
    private static byte[] requireSuccessor(byte[] successor) {
        if (successor == null || successor.length == 0) {
            throw new IllegalArgumentException(
                    "canonical commit requires mutable piece/start successor");
        }
        return successor;
    }

    private byte[] persistedSuccessor() {
        if (mutablePieceSuccessor == null || mutablePieceSuccessor.length == 0) {
            throw new IllegalStateException(
                    "canonical worldgen row is missing mutable piece/start successor");
        }
        return mutablePieceSuccessor;
    }

    public boolean samePayload(CanonicalWorldgenStore.ChunkCommit commit) {
        return Arrays.equals(commit.fingerprint(), commitFingerprint)
                && Arrays.equals(commit.finalCarrier(), finalCarrier)
                && Arrays.equals(commit.structureCarrier(), structureCarrier)
                && Arrays.equals(commit.mutablePieceSuccessor(), mutablePieceSuccessor)
                && commit.worldIdentity().equals(worldIdentity);
    }

    private void validateGroupMetadata() {
        boolean fingerprintPresent = groupFingerprint != null;
        boolean anyValuePresent = groupMinChunkX != null || groupMinChunkZ != null
                || groupMaxChunkX != null || groupMaxChunkZ != null
                || groupOrdinal != null || groupSize != null;
        boolean everyValuePresent = groupMinChunkX != null && groupMinChunkZ != null
                && groupMaxChunkX != null && groupMaxChunkZ != null
                && groupOrdinal != null && groupSize != null;
        if (!fingerprintPresent && !anyValuePresent) return;
        if (!fingerprintPresent || !everyValuePresent || groupFingerprint.length != 32) {
            throw new IllegalStateException("canonical row has incomplete group metadata");
        }
        if (groupMinChunkX > groupMaxChunkX || groupMinChunkZ > groupMaxChunkZ
                || groupSize <= 0 || groupOrdinal < 0 || groupOrdinal >= groupSize) {
            throw new IllegalStateException("canonical row has invalid group metadata");
        }
        long xCount = (long) groupMaxChunkX - groupMinChunkX + 1;
        long zCount = (long) groupMaxChunkZ - groupMinChunkZ + 1;
        long expectedSize;
        try {
            expectedSize = Math.multiplyExact(xCount, zCount);
        } catch (ArithmeticException overflow) {
            throw new IllegalStateException("canonical row group bounds overflow", overflow);
        }
        long xOffset = groupOrdinal / zCount;
        long zOffset = groupOrdinal % zCount;
        if (expectedSize != groupSize || groupMinChunkX + xOffset != chunkX
                || groupMinChunkZ + zOffset != chunkZ) {
            throw new IllegalStateException("canonical row group ordinal/coordinate mismatch");
        }
    }

    private static void requireGroupMember(CanonicalWorldgenStore.ChunkCommit commit,
            CanonicalWorldgenStore.WholeBboxCommit group, int ordinal) {
        if (ordinal < 0 || ordinal >= group.commits().size()
                || group.commits().get(ordinal) != commit
                        && !sameCommit(group.commits().get(ordinal), commit)) {
            throw new IllegalArgumentException("commit is not the requested whole-bbox member");
        }
    }

    private static boolean sameCommit(CanonicalWorldgenStore.ChunkCommit left,
            CanonicalWorldgenStore.ChunkCommit right) {
        return left.worldId() == right.worldId()
                && left.worldIdentity().equals(right.worldIdentity())
                && left.chunkX() == right.chunkX() && left.chunkZ() == right.chunkZ()
                && Arrays.equals(left.fingerprint(), right.fingerprint())
                && Arrays.equals(left.finalCarrier(), right.finalCarrier())
                && Arrays.equals(left.structureCarrier(), right.structureCarrier())
                && Arrays.equals(left.mutablePieceSuccessor(), right.mutablePieceSuccessor());
    }
}
