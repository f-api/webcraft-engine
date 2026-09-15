package com.gameexpert.terrain;

import com.gameexpert.terrain.mc.loot.Mc263ProductionContextCatalog;
import com.gameexpert.terrain.persistence.InMemoryCanonicalWorldgenStore;
import java.util.Objects;

/**
 * Produces one complete generated chunk for every TerrainAccessor generation entry point.
 * The only production implementation is the canonical 26.3 product seam; the legacy final raster
 * was removed, so every source answers with a canonical final carrier.
 */
@FunctionalInterface
public interface ChunkProductSource {
    ChunkGenerator.GeneratedChunk generate(int worldSeed, int chunkX, int chunkZ);

    /** Every production source is bound to the stored world's complete producer identity. */
    default com.gameexpert.world.WorldGenerationProfile generationProfile() {
        throw new IllegalStateException("chunk source has no bound generation profile");
    }

    /** Positive in-memory admission hint only; false includes unknown. Never reads storage. */
    default boolean hasKnownCommittedChunk(int chunkX, int chunkZ) {
        return false;
    }

    /**
     * The production canonical seam without durable persistence: a private in-memory canonical
     * store plus the authenticated POST activation context. One instance serves one world seed,
     * exactly like the persisted production source, and never reaches a legacy raster.
     */
    static ChunkProductSource detachedCanonical(long worldId,
            Mc263ProductionContextCatalog.Provider productionContextProvider) {
        productionContextProvider = Objects.requireNonNull(productionContextProvider,
                "production context catalog provider");
        return new CanonicalOriginChunkProductSource(new InMemoryCanonicalWorldgenStore(), worldId,
                new CanonicalPostprocessActivationContext(0L), productionContextProvider);
    }

    /**
     * The seeded canonical seam using a caller-owned input-builder context. The caller owns the
     * context lifecycle and may reuse it across sources that belong to the same exporter worker.
     */
    static ChunkProductSource detachedCanonical(long worldId, int worldSeed,
            Mc263FeaturesRegionBridge.InputBuilderContext inputBuilderContext,
            Mc263ProductionContextCatalog.Provider productionContextProvider) {
        productionContextProvider = Objects.requireNonNull(productionContextProvider,
                "production context catalog provider");
        return new CanonicalOriginChunkProductSource(new InMemoryCanonicalWorldgenStore(), worldId,
                worldSeed, new CanonicalPostprocessActivationContext(0L), inputBuilderContext,
                productionContextProvider);
    }

    /** Seed-bound detached canonical seam with a source-created input context per pending build. */
    static ChunkProductSource detachedCanonical(long worldId, int worldSeed,
            Mc263ProductionContextCatalog.Provider productionContextProvider) {
        productionContextProvider = Objects.requireNonNull(productionContextProvider,
                "production context catalog provider");
        return new CanonicalOriginChunkProductSource(new InMemoryCanonicalWorldgenStore(), worldId,
                worldSeed, new CanonicalPostprocessActivationContext(0L), null,
                productionContextProvider);
    }
}
