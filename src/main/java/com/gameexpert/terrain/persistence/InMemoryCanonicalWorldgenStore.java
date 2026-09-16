package com.gameexpert.terrain.persistence;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Deterministic store used by focused authority tests and crash-boundary probes. */
public final class InMemoryCanonicalWorldgenStore
        implements CanonicalWorldgenStore.WholeBboxCommitter {
    private final Map<Key, Entry> entries = new HashMap<>();
    private final GroupCommitProbe groupCommitProbe;

    public InMemoryCanonicalWorldgenStore() {
        this((index, commit) -> {});
    }

    InMemoryCanonicalWorldgenStore(GroupCommitProbe groupCommitProbe) {
        this.groupCommitProbe = Objects.requireNonNull(groupCommitProbe, "group commit probe");
    }

    @Override public synchronized com.gameexpert.authority.versioned.CanonicalStructureSnapshot structureSnapshot(
            long worldId, com.gameexpert.world.WorldGenerationProfile profile) {
        var rows = new ArrayList<com.gameexpert.authority.versioned.CanonicalStructureSnapshot.Row>();
        for (Entry entry : entries.values()) if (entry.commit.worldId() == worldId) {
            if (!profile.getBaselineId().equals(entry.commit.worldIdentity())) throw new IllegalStateException("mixed world producer identities");
            rows.add(new com.gameexpert.authority.versioned.CanonicalStructureSnapshot.Row(
                    entry.commit.chunkX(), entry.commit.chunkZ(), entry.commit.structureCarrier()));
        }
        return new com.gameexpert.authority.versioned.CanonicalStructureSnapshot(worldId, profile, rows);
    }

    @Override public synchronized boolean commitFromSnapshot(ChunkCommit commit, String expectedReferenceReceipt) {
        if (isCommitted(commit.worldId(), commit.chunkX(), commit.chunkZ())) return true;
        var profile = com.gameexpert.world.WorldGenerationProfiles.requireSupportedBaselineId(commit.worldIdentity());
        if (!structureSnapshot(commit.worldId(), profile).receipt().equals(expectedReferenceReceipt)) return false;
        commit(commit);
        return true;
    }

    @Override
    public synchronized CanonicalChunkSnapshot commitGeneratedDeclaration(ChunkCommit commit) {
        Objects.requireNonNull(commit, "canonical generation declaration");
        com.gameexpert.world.WorldGenerationProfiles.requireSupportedBaselineId(commit.worldIdentity());
        CanonicalChunkSnapshot existing = find(commit.worldId(), commit.chunkX(), commit.chunkZ());
        if (existing != null) {
            if (!commit.worldIdentity().equals(existing.commit().worldIdentity())) {
                throw new IllegalStateException("canonical persisted profile mismatch");
            }
            return existing;
        }
        return commit(commit);
    }

    @Override
    public synchronized CanonicalChunkSnapshot commit(ChunkCommit commit) {
        Key key = new Key(commit.worldId(), commit.chunkX(), commit.chunkZ());
        Entry previous = entries.get(key);
        if (previous != null) {
            if (!Arrays.equals(previous.commit.fingerprint(), commit.fingerprint())) {
                throw new IllegalStateException("canonical chunk fingerprint conflict at " + key);
            }
            if (!samePayload(previous.commit, commit)) {
                throw new IllegalStateException("canonical chunk payload conflict at " + key);
            }
            return snapshot(previous);
        }
        Entry created = new Entry(commit, null);
        entries.put(key, created);
        return snapshot(created);
    }

    @Override
    public synchronized List<CanonicalChunkSnapshot> commitWholeBbox(WholeBboxCommit group) {
        Objects.requireNonNull(group, "whole-bbox canonical commit");
        List<ChunkCommit> commits = group.commits();
        int existing = 0;
        for (ChunkCommit commit : commits) {
            if (entries.containsKey(new Key(commit.worldId(), commit.chunkX(), commit.chunkZ()))) {
                existing++;
            }
        }
        if (existing != 0 && existing != commits.size()) {
            throw new IllegalStateException("whole-bbox publication is partially preexisting");
        }
        if (existing == commits.size()) {
            ArrayList<CanonicalChunkSnapshot> snapshots = new ArrayList<>(commits.size());
            for (int ordinal = 0; ordinal < commits.size(); ordinal++) {
                ChunkCommit commit = commits.get(ordinal);
                Key key = new Key(commit.worldId(), commit.chunkX(), commit.chunkZ());
                Entry entry = entries.get(key);
                if (entry.group == null || !entry.group.matches(group, ordinal)) {
                    throw new IllegalStateException(
                            "whole-bbox canonical group membership conflict at " + key);
                }
                requireSamePayload(entry.commit, commit, key);
                snapshots.add(snapshot(entry));
            }
            return List.copyOf(snapshots);
        }

        LinkedHashMap<Key, Entry> staged = new LinkedHashMap<>();
        for (int index = 0; index < commits.size(); index++) {
            ChunkCommit commit = commits.get(index);
            groupCommitProbe.beforeStage(index, commit);
            staged.put(new Key(commit.worldId(), commit.chunkX(), commit.chunkZ()),
                    new Entry(commit, new GroupMembership(group, index)));
        }
        entries.putAll(staged);
        return staged.values().stream().map(InMemoryCanonicalWorldgenStore::snapshot).toList();
    }

    @Override
    public synchronized CanonicalChunkSnapshot find(long worldId, int chunkX, int chunkZ) {
        Entry entry = entries.get(new Key(worldId, chunkX, chunkZ));
        return entry == null ? null : snapshot(entry);
    }

    @Override
    public synchronized com.gameexpert.terrain.mc.loot.Mc263LocatedMapAuthority
            .StructureReferenceSnapshot structureReferenceSnapshot(long worldId) {
        List<CanonicalChunkSnapshot> frozen = entries.entrySet().stream()
                .filter(entry -> entry.getKey().worldId == worldId)
                .map(Map.Entry::getValue)
                .map(InMemoryCanonicalWorldgenStore::snapshot)
                .toList();
        return CanonicalWorldgenStore.freezeStructureReferences(worldId, frozen);
    }

    @Override
    public synchronized LaneReceipt claim(long worldId, int chunkX, int chunkZ, Lane lane) {
        Entry entry = require(worldId, chunkX, chunkZ);
        if ((entry.terminalMask() & lane.mask()) != 0) return null;
        entry.claimMask |= lane.mask();
        return LaneReceipt.fromCommit(lane, entry.commit, lanePayload(entry.commit, lane));
    }

    @Override
    public synchronized void acknowledge(long worldId, int chunkX, int chunkZ, Lane lane,
            byte[] receipt) {
        Entry entry = require(worldId, chunkX, chunkZ);
        byte[] expected = lanePayload(entry.commit, lane);
        if (!MessageDigest.isEqual(expected, receipt)) {
            throw new IllegalArgumentException("lane receipt payload mismatch: " + lane);
        }
        if ((entry.rejectedMask & lane.mask()) != 0) {
            throw new IllegalStateException("lane is terminally rejected: " + lane);
        }
        entry.claimMask |= lane.mask();
        entry.ackMask |= lane.mask();
    }

    @Override
    public synchronized void reject(long worldId, int chunkX, int chunkZ, Lane lane,
            byte[] receipt) {
        Entry entry = require(worldId, chunkX, chunkZ);
        byte[] expected = lanePayload(entry.commit, lane);
        if (!MessageDigest.isEqual(expected, receipt)) {
            throw new IllegalArgumentException("lane receipt payload mismatch: " + lane);
        }
        if ((entry.ackMask & lane.mask()) != 0) {
            throw new IllegalStateException("lane is already acknowledged: " + lane);
        }
        entry.claimMask |= lane.mask();
        entry.rejectedMask |= lane.mask();
    }

    private Entry require(long worldId, int chunkX, int chunkZ) {
        Entry entry = entries.get(new Key(worldId, chunkX, chunkZ));
        if (entry == null) throw new IllegalStateException("canonical chunk is not committed");
        return entry;
    }

    private static boolean samePayload(ChunkCommit left, ChunkCommit right) {
        return left.worldId() == right.worldId()
                && left.worldIdentity().equals(right.worldIdentity())
                && left.chunkX() == right.chunkX() && left.chunkZ() == right.chunkZ()
                && Arrays.equals(left.finalCarrier(), right.finalCarrier())
                && Arrays.equals(left.structureCarrier(), right.structureCarrier())
                && Arrays.equals(left.mutablePieceSuccessor(), right.mutablePieceSuccessor());
    }

    private static void requireSamePayload(ChunkCommit previous, ChunkCommit commit, Key key) {
        if (!Arrays.equals(previous.fingerprint(), commit.fingerprint())
                || !samePayload(previous, commit)) {
            throw new IllegalStateException(
                    "whole-bbox canonical payload conflict at " + key);
        }
    }

    private static byte[] lanePayload(ChunkCommit commit, Lane lane) {
        byte[] source = switch (lane) {
            case BLOCK_TICKS, FLUID_TICKS, LOOT, SPAWNERS, OWNERS, ARCHAEOLOGY, BEES,
                    BLOCK_ENTITIES, ENTITIES -> commit.finalCarrier();
        };
        return digest(commit.fingerprint(), lane.name(), source);
    }

    private static byte[] digest(byte[] fingerprint, String lane, byte[] payload) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(fingerprint); digest.update((byte) 0);
            digest.update(lane.getBytes(StandardCharsets.US_ASCII));
            digest.update((byte) 0); digest.update(payload);
            return digest.digest();
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }

    private static CanonicalChunkSnapshot snapshot(Entry entry) {
        return new CanonicalChunkSnapshot(entry.commit, entry.claimMask, entry.ackMask,
                entry.rejectedMask);
    }

    private static final class Key {
        private final long worldId;
        private final int chunkX;
        private final int chunkZ;

        private Key(long worldId, int chunkX, int chunkZ) {
            this.worldId = worldId;
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
        }

        @Override public boolean equals(Object other) {
            return other instanceof Key value && worldId == value.worldId
                    && chunkX == value.chunkX && chunkZ == value.chunkZ;
        }

        @Override public int hashCode() {
            int result = Long.hashCode(worldId);
            result = 31 * result + chunkX;
            return 31 * result + chunkZ;
        }

        @Override public String toString() {
            return worldId + ":" + chunkX + ":" + chunkZ;
        }
    }
    private static final class Entry {
        private final ChunkCommit commit;
        private final GroupMembership group;
        private int claimMask;
        private int ackMask;
        private int rejectedMask;
        private Entry(ChunkCommit commit, GroupMembership group) {
            this.commit = commit;
            this.group = group;
        }
        private int terminalMask() { return ackMask | rejectedMask; }
    }

    private static final class GroupMembership {
        private final byte[] fingerprint;
        private final int minChunkX, minChunkZ, maxChunkX, maxChunkZ, ordinal, size;

        private GroupMembership(WholeBboxCommit group, int ordinal) {
            this.fingerprint = group.fingerprint();
            this.minChunkX = group.minChunkX(); this.minChunkZ = group.minChunkZ();
            this.maxChunkX = group.maxChunkX(); this.maxChunkZ = group.maxChunkZ();
            this.ordinal = ordinal; this.size = group.commits().size();
        }

        private boolean matches(WholeBboxCommit group, int expectedOrdinal) {
            return MessageDigest.isEqual(fingerprint, group.fingerprint())
                    && minChunkX == group.minChunkX() && minChunkZ == group.minChunkZ()
                    && maxChunkX == group.maxChunkX() && maxChunkZ == group.maxChunkZ()
                    && ordinal == expectedOrdinal && size == group.commits().size();
        }
    }


    @FunctionalInterface
    interface GroupCommitProbe {
        void beforeStage(int index, ChunkCommit commit);
    }
}
