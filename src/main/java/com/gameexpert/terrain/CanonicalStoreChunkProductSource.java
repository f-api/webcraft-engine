package com.gameexpert.terrain;

import com.gameexpert.authority.versioned.NeutralFinalChunk;
import com.gameexpert.terrain.persistence.CanonicalWorldgenStore;
import java.util.Objects;

/** Load-only replay of current canonical chunk products already committed for one world. */
public final class CanonicalStoreChunkProductSource implements ChunkProductSource {
    private final CanonicalWorldgenStore store;
    private final long worldId;
    private final com.gameexpert.world.WorldGenerationProfile generationProfile;

    @Override public com.gameexpert.world.WorldGenerationProfile generationProfile() {
        return generationProfile;
    }

    public CanonicalStoreChunkProductSource(CanonicalWorldgenStore store, long worldId) {
        this(store, worldId, com.gameexpert.world.WorldGenerationProfiles.newWorldProfile());
    }

    public CanonicalStoreChunkProductSource(CanonicalWorldgenStore store, long worldId,
            com.gameexpert.world.WorldGenerationProfile profile) {
        generationProfile = com.gameexpert.world.WorldGenerationProfiles.requireSupported(profile);
        this.store = Objects.requireNonNull(store, "canonical worldgen store");
        if (worldId <= 0) {
            throw new IllegalArgumentException("world ID must be positive");
        }
        this.worldId = worldId;
    }

    @Override
    public ChunkGenerator.GeneratedChunk generate(int worldSeed, int chunkX, int chunkZ) {
        ChunkGenerator.GeneratedChunk replayed = replayIfCommitted(chunkX, chunkZ);
        if (replayed == null) {
            throw new IllegalStateException("canonical chunk is absent from the current store");
        }
        return replayed;
    }

    /**
     * Replays the committed canonical chunk, or returns {@code null} when this world has never
     * committed it. Every other inconsistency — foreign world ID, stale world identity, wrong
     * coordinates, malformed carrier — still fails closed rather than degrading to a miss.
     */
    public ChunkGenerator.GeneratedChunk replayIfCommitted(int chunkX, int chunkZ) {
        CanonicalWorldgenStore.CanonicalChunkSnapshot snapshot =
                store.find(worldId, chunkX, chunkZ);
        if (snapshot == null) {
            return null;
        }
        CanonicalWorldgenStore.ChunkCommit commit = Objects.requireNonNull(
                snapshot.commit(), "canonical chunk snapshot commit");
        if (commit.worldId() != worldId) {
            throw new IllegalStateException("canonical chunk world ID does not match request");
        }
        if (!generationProfile.getBaselineId().equals(commit.worldIdentity())) {
            throw new IllegalStateException("canonical chunk world identity is not current");
        }
        if (commit.chunkX() != chunkX || commit.chunkZ() != chunkZ) {
            throw new IllegalStateException("canonical chunk coordinates do not match request");
        }

        // The commit decoded its own carrier once when it was built; replay reuses that proven
        // view rather than paying a second schema-4 decode of the identical bytes (AGENTS 10l).
        NeutralFinalChunk carrier = commit.semanticFinalChunk();
        if (carrier.chunkX() != chunkX || carrier.chunkZ() != chunkZ) {
            throw new IllegalStateException("canonical carrier coordinates do not match request");
        }
        if (carrier.blockIds().length != Blocks.CHUNK_BLOCKS
                || carrier.worldSurfaceWg().length != Blocks.CHUNK_X * Blocks.CHUNK_Z) {
            throw new IllegalStateException("canonical carrier arrays are malformed");
        }
        try {
            return ChunkGenerator.generatedChunkFromCanonicalCarrier(chunkX, chunkZ, carrier);
        } catch (IllegalArgumentException | ArithmeticException malformed) {
            throw new IllegalStateException("canonical carrier arrays are malformed", malformed);
        }
    }
}
