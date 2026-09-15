package com.gameexpert.terrain.persistence;

import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Transactional Spring implementation of the canonical worldgen store. */
@Service
public class CanonicalWorldgenPersistenceService
        implements CanonicalWorldgenStore.WholeBboxCommitter {
    private final CanonicalWorldgenChunkRepository chunks;
    private final com.gameexpert.api.persistence.WorldStore worlds;

    public CanonicalWorldgenPersistenceService(CanonicalWorldgenChunkRepository chunks) {
        this(chunks, null);
    }
    @org.springframework.beans.factory.annotation.Autowired
    public CanonicalWorldgenPersistenceService(CanonicalWorldgenChunkRepository chunks,
            com.gameexpert.api.persistence.WorldStore worlds) {
        this.chunks = Objects.requireNonNull(chunks, "canonical worldgen repository");
        this.worlds = worlds;
    }

    @Override @Transactional(readOnly = true)
    public com.gameexpert.authority.versioned.CanonicalStructureSnapshot structureSnapshot(
            long worldId, com.gameexpert.world.WorldGenerationProfile profile) {
        var rows = new ArrayList<com.gameexpert.authority.versioned.CanonicalStructureSnapshot.Row>();
        for (var row : chunks.findStructureRows(worldId)) {
            if (!profile.getBaselineId().equals(row.getWorldIdentity())) throw new IllegalStateException("mixed world producer identities");
            rows.add(new com.gameexpert.authority.versioned.CanonicalStructureSnapshot.Row(
                    row.getChunkX(), row.getChunkZ(), row.getStructureCarrier()));
        }
        return new com.gameexpert.authority.versioned.CanonicalStructureSnapshot(worldId, profile, rows);
    }

    @Override @Transactional
    public boolean commitFromSnapshot(ChunkCommit commit, String expectedReferenceReceipt) {
        if (worlds == null) throw new IllegalStateException("world-bound canonical publication unavailable");
        // Generation runs outside this short publication lock. It serializes only reference
        // snapshot comparison and insertion, including independent producer slots in this world.
        var world = worlds.findByIdForUpdate(commit.worldId())
                .orElseThrow(() -> new IllegalStateException("canonical publication world is absent"));
        var profile = world.generationProfile();
        if (!profile.getBaselineId().equals(commit.worldIdentity())) throw new IllegalStateException("canonical publication profile mismatch");
        if (isCommitted(commit.worldId(), commit.chunkX(), commit.chunkZ())) return true;
        if (!structureSnapshot(commit.worldId(), profile).receipt().equals(expectedReferenceReceipt)) return false;
        commitMember(commit);
        return true;
    }

    @Override @Transactional
    public CanonicalChunkSnapshot commit(ChunkCommit commit) {
        return commitMember(commit);
    }

    /** One short transaction after CPU production, with no additional world-row lock. */
    @Override @Transactional
    public void commitBatch(List<ChunkCommit> commits) {
        List<ChunkCommit> batch = List.copyOf(commits);
        if (batch.isEmpty() || batch.size() > 8) {
            throw new IllegalArgumentException("canonical publication batch must contain 1..8 chunks");
        }
        long worldId = batch.getFirst().worldId();
        for (ChunkCommit member : batch) {
            requireSuccessor(member.mutablePieceSuccessor());
            if (member.worldId() != worldId) {
                throw new IllegalArgumentException("canonical publication batch mixes worlds");
            }
        }
        for (ChunkCommit member : batch) commitMember(member);
    }

    private CanonicalChunkSnapshot commitMember(ChunkCommit commit) {
        Objects.requireNonNull(commit, "canonical chunk commit");
        byte[] successor = requireSuccessor(commit.mutablePieceSuccessor());
        chunks.insertIfAbsent(commit.worldId(), commit.worldIdentity(), commit.chunkX(),
                commit.chunkZ(), ABI_VERSION, FINAL_CARRIER_SCHEMA, commit.finalCarrier(),
                commit.structureCarrier(), successor, commit.fingerprint());
        CanonicalWorldgenChunk row = chunks.findForUpdateByWorldIdAndChunkXAndChunkZ(
                commit.worldId(), commit.chunkX(), commit.chunkZ())
                .orElseThrow(() -> new IllegalStateException("canonical chunk insert disappeared"));
        row.toCommit();
        if (!row.isUngrouped() || !row.samePayload(commit)) {
            throw new IllegalStateException("canonical chunk fingerprint/payload conflict");
        }
        return snapshot(row);
    }

    @Override @Transactional
    public List<CanonicalChunkSnapshot> commitWholeBbox(WholeBboxCommit group) {
        Objects.requireNonNull(group, "whole-bbox canonical commit");
        List<ChunkCommit> commits = group.commits();
        ArrayList<CanonicalWorldgenChunk> existing = new ArrayList<>(commits.size());
        int existingCount = 0;
        for (ChunkCommit commit : commits) {
            requireSuccessor(commit.mutablePieceSuccessor());
            CanonicalWorldgenChunk row = chunks.findForUpdateByWorldIdAndChunkXAndChunkZ(
                    commit.worldId(), commit.chunkX(), commit.chunkZ()).orElse(null);
            existing.add(row);
            if (row != null) existingCount++;
        }
        if (existingCount != 0 && existingCount != commits.size()) {
            throw new IllegalStateException("whole-bbox publication is partially preexisting");
        }
        if (existingCount == commits.size()) {
            return verifiedSnapshots(group, existing);
        }

        for (int ordinal = 0; ordinal < commits.size(); ordinal++) {
            ChunkCommit commit = commits.get(ordinal);
            chunks.insertGroupIfAbsent(commit.worldId(), commit.worldIdentity(), commit.chunkX(),
                    commit.chunkZ(), ABI_VERSION, FINAL_CARRIER_SCHEMA, commit.finalCarrier(),
                    commit.structureCarrier(), commit.mutablePieceSuccessor(),
                    commit.fingerprint(), group.fingerprint(), group.minChunkX(),
                    group.minChunkZ(), group.maxChunkX(), group.maxChunkZ(), ordinal,
                    commits.size());
        }
        ArrayList<CanonicalWorldgenChunk> inserted = new ArrayList<>(commits.size());
        for (ChunkCommit commit : commits) {
            inserted.add(chunks.findForUpdateByWorldIdAndChunkXAndChunkZ(
                    commit.worldId(), commit.chunkX(), commit.chunkZ()).orElseThrow(
                            () -> new IllegalStateException(
                                    "whole-bbox canonical member insert disappeared")));
        }
        return verifiedSnapshots(group, inserted);
    }

    @Override @Transactional(readOnly = true)
    public CanonicalChunkSnapshot find(long worldId, int chunkX, int chunkZ) {
        return chunks.findByWorldIdAndChunkXAndChunkZ(worldId, chunkX, chunkZ)
                .map(CanonicalWorldgenPersistenceService::snapshot)
                .orElse(null);
    }

    /** Existence only: no carrier blob is read, so a committed-or-not test costs one index probe. */
    @Override @Transactional(readOnly = true)
    public boolean isCommitted(long worldId, int chunkX, int chunkZ) {
        return chunks.existsByWorldIdAndChunkXAndChunkZ(worldId, chunkX, chunkZ);
    }

    @Override @Transactional(readOnly = true)
    public com.gameexpert.terrain.mc.loot.Mc263LocatedMapAuthority.StructureReferenceSnapshot
            structureReferenceSnapshot(long worldId) {
        List<CanonicalChunkSnapshot> frozen = chunks
                .findAllByWorldIdOrderByChunkXAscChunkZAsc(worldId).stream()
                .map(CanonicalWorldgenPersistenceService::snapshot).toList();
        return CanonicalWorldgenStore.freezeStructureReferences(worldId, frozen);
    }

    @Override @Transactional
    public LaneReceipt claim(long worldId, int chunkX, int chunkZ, Lane lane) {
        CanonicalWorldgenChunk row = requireForUpdate(worldId, chunkX, chunkZ);
        ChunkCommit commit = row.toCommit();
        if (row.isTerminal(lane.mask())) return null;
        row.claim(lane.mask());
        chunks.save(row);
        return LaneReceipt.fromCommit(lane, commit, laneReceipt(commit, lane));
    }

    @Override @Transactional
    public void acknowledge(long worldId, int chunkX, int chunkZ, Lane lane, byte[] receipt) {
        CanonicalWorldgenChunk row = requireForUpdate(worldId, chunkX, chunkZ);
        if (!MessageDigest.isEqual(laneReceipt(row.toCommit(), lane), receipt)) {
            throw new IllegalArgumentException("lane receipt payload mismatch: " + lane);
        }
        row.acknowledge(lane.mask());
        chunks.save(row);
    }

    @Override @Transactional
    public void reject(long worldId, int chunkX, int chunkZ, Lane lane, byte[] receipt) {
        CanonicalWorldgenChunk row = requireForUpdate(worldId, chunkX, chunkZ);
        if (!MessageDigest.isEqual(laneReceipt(row.toCommit(), lane), receipt)) {
            throw new IllegalArgumentException("lane receipt payload mismatch: " + lane);
        }
        row.reject(lane.mask());
        chunks.save(row);
    }

    private static byte[] requireSuccessor(byte[] successor) {
        if (successor == null || successor.length == 0) {
            throw new IllegalArgumentException(
                    "canonical commit requires mutable piece/start successor");
        }
        return successor;
    }

    private CanonicalWorldgenChunk requireForUpdate(long worldId, int chunkX, int chunkZ) {
        return chunks.findForUpdateByWorldIdAndChunkXAndChunkZ(worldId, chunkX, chunkZ)
                .orElseThrow(() -> new IllegalStateException("canonical chunk is not committed"));
    }

    private static CanonicalChunkSnapshot snapshot(CanonicalWorldgenChunk row) {
        return new CanonicalChunkSnapshot(row.toCommit(), row.laneClaimMask(), row.laneAckMask(),
                row.laneRejectedMask());
    }

    private static List<CanonicalChunkSnapshot> verifiedSnapshots(WholeBboxCommit group,
            List<CanonicalWorldgenChunk> rows) {
        List<ChunkCommit> commits = group.commits();
        ArrayList<CanonicalChunkSnapshot> snapshots = new ArrayList<>(commits.size());
        for (int index = 0; index < commits.size(); index++) {
            CanonicalWorldgenChunk row = rows.get(index);
            row.toCommit();
            if (!row.sameGroup(group, index) || !row.samePayload(commits.get(index))) {
                throw new IllegalStateException("whole-bbox canonical member payload conflict");
            }
            snapshots.add(snapshot(row));
        }
        return List.copyOf(snapshots);
    }

    private static byte[] laneReceipt(ChunkCommit commit, Lane lane) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(commit.fingerprint()); digest.update((byte) 0);
            digest.update(lane.name().getBytes(java.nio.charset.StandardCharsets.US_ASCII));
            digest.update((byte) 0); digest.update(commit.finalCarrier());
            return digest.digest();
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }
}
