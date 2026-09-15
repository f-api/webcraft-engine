package com.gameexpert.engine.persistence.finalcarrier;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import com.gameexpert.terrain.persistence.CanonicalWorldgenChunk;

/**
 * Deletion-only view of {@code canonical_worldgen_chunks}.
 *
 * <p>The canonical worldgen source tree is frozen for source-identity reasons, so world deletion
 * gets its own bulk-delete declaration here rather than growing the terrain repository. Bulk
 * delete keeps the statement count at one even for the thousands of rows a played world commits.
 * </p>
 */
public interface CanonicalWorldgenChunkPurgeRepository
        extends Repository<CanonicalWorldgenChunk, Long> {

    /** Removes every canonical worldgen chunk this world published; returns the row count. */
    @Modifying
    @Query("delete from CanonicalWorldgenChunk row where row.worldId = :worldId")
    int deleteByWorldId(@Param("worldId") long worldId);
}
