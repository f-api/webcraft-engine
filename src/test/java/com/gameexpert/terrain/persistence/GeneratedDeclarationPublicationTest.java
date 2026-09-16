package com.gameexpert.terrain.persistence;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.gameexpert.api.persistence.WorldAccess;
import com.gameexpert.api.persistence.WorldStore;
import com.gameexpert.world.WorldGenerationProfiles;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class GeneratedDeclarationPublicationTest {
    private final CanonicalWorldgenChunkRepository chunks = mock(CanonicalWorldgenChunkRepository.class);
    private final WorldStore worlds = mock(WorldStore.class);
    private final CanonicalWorldgenPersistenceService store =
            new CanonicalWorldgenPersistenceService(chunks, worlds);
    private final CanonicalWorldgenStore.ChunkCommit declaration =
            mock(CanonicalWorldgenStore.ChunkCommit.class);

    private void prepareWorld() {
        when(declaration.worldId()).thenReturn(7L);
        when(declaration.worldIdentity()).thenReturn(WorldGenerationProfiles.CURRENT.getBaselineId());
        WorldAccess world = mock(WorldAccess.class);
        when(world.generationProfile()).thenReturn(WorldGenerationProfiles.CURRENT);
        when(worlds.findByIdForUpdate(7L)).thenReturn(Optional.of(world));
    }

    private CanonicalWorldgenChunk prepareRow(boolean matching) {
        when(declaration.chunkX()).thenReturn(10000);
        when(declaration.chunkZ()).thenReturn(-10000);
        when(declaration.mutablePieceSuccessor()).thenReturn(new byte[]{1});
        CanonicalWorldgenChunk row = mock(CanonicalWorldgenChunk.class);
        when(row.isUngrouped()).thenReturn(true);
        when(row.samePayload(declaration)).thenReturn(matching);
        when(row.toCommit()).thenReturn(declaration);
        when(chunks.findForUpdateByWorldIdAndChunkXAndChunkZ(7L, 10000, -10000))
                .thenReturn(Optional.of(row));
        return row;
    }

    @Test
    void publicationLocksWorldAndNeverLoadsAccumulatedStructureRows() {
        prepareWorld();
        prepareRow(true);
        // A full-world enumeration is deliberately unusable, as in an oversized existing world.
        when(chunks.findStructureRows(anyLong()))
                .thenThrow(new IllegalArgumentException("structure snapshot exceeds bounds"));
        assertSame(declaration, store.commitGeneratedDeclaration(declaration).commit());
        InOrder order = inOrder(worlds, chunks);
        order.verify(worlds).findByIdForUpdate(7L);
        order.verify(chunks).insertIfAbsent(eq(7L), anyString(), eq(10000), eq(-10000),
                anyInt(), anyInt(), isNull(), isNull(), any(byte[].class), isNull());
        verify(chunks, never()).findStructureRows(anyLong());
        verify(chunks, never()).findAllByWorldIdOrderByChunkXAscChunkZAsc(anyLong());
    }

    @Test
    void competingProposalReplaysAlreadyCommittedWinnerUnchanged() {
        prepareWorld();
        CanonicalWorldgenStore.ChunkCommit winner = mock(CanonicalWorldgenStore.ChunkCommit.class);
        byte[] originalFingerprint = {42};
        when(winner.worldIdentity()).thenReturn(WorldGenerationProfiles.CURRENT.getBaselineId());
        when(winner.fingerprint()).thenReturn(originalFingerprint);
        CanonicalWorldgenChunk row = mock(CanonicalWorldgenChunk.class);
        when(row.toCommit()).thenReturn(winner);
        when(chunks.findByWorldIdAndChunkXAndChunkZ(7L, 0, 0)).thenReturn(Optional.of(row));
        CanonicalWorldgenStore.CanonicalChunkSnapshot result = store.commitGeneratedDeclaration(declaration);
        assertSame(winner, result.commit());
        assertArrayEquals(originalFingerprint, result.commit().fingerprint());
        verify(chunks, never()).insertIfAbsent(anyLong(), anyString(), anyInt(), anyInt(),
                anyInt(), anyInt(), any(), any(), any(), any());
        verify(chunks, never()).findStructureRows(anyLong());
    }

    @Test
    void persistedWinnerWithForeignProfileIsRejected() {
        prepareWorld();
        CanonicalWorldgenStore.ChunkCommit winner = mock(CanonicalWorldgenStore.ChunkCommit.class);
        when(winner.worldIdentity()).thenReturn("foreign-profile");
        CanonicalWorldgenChunk row = mock(CanonicalWorldgenChunk.class);
        when(row.toCommit()).thenReturn(winner);
        when(chunks.findByWorldIdAndChunkXAndChunkZ(7L, 0, 0)).thenReturn(Optional.of(row));
        assertThrows(IllegalStateException.class, () -> store.commitGeneratedDeclaration(declaration));
    }

    @Test
    void mismatchedWorldProfileCannotPublish() {
        prepareWorld();
        when(declaration.worldIdentity()).thenReturn("foreign-profile");
        assertThrows(IllegalStateException.class, () -> store.commitGeneratedDeclaration(declaration));
        verifyNoInteractions(chunks);
    }

    @Test
    void missingWorldCannotPublish() {
        when(declaration.worldId()).thenReturn(7L);
        when(worlds.findByIdForUpdate(7L)).thenReturn(Optional.empty());
        assertThrows(IllegalStateException.class, () -> store.commitGeneratedDeclaration(declaration));
        verifyNoInteractions(chunks);
    }

    @Test
    void inMemoryPublicationReplaysFirstWriterAndOrdinaryCommitStillRejectsConflicts() {
        prepareWorld();
        when(declaration.fingerprint()).thenReturn(new byte[]{1});
        InMemoryCanonicalWorldgenStore memory = new InMemoryCanonicalWorldgenStore();
        assertSame(declaration, memory.commitGeneratedDeclaration(declaration).commit());
        assertSame(declaration, memory.commitGeneratedDeclaration(declaration).commit());
        CanonicalWorldgenStore.ChunkCommit conflict = mock(CanonicalWorldgenStore.ChunkCommit.class);
        when(conflict.worldId()).thenReturn(7L);
        when(conflict.worldIdentity()).thenReturn(WorldGenerationProfiles.CURRENT.getBaselineId());
        when(conflict.fingerprint()).thenReturn(new byte[]{2});
        assertSame(declaration, memory.commitGeneratedDeclaration(conflict).commit());
        assertArrayEquals(new byte[]{1}, memory.find(7L, 0, 0).commit().fingerprint());
        assertThrows(IllegalStateException.class, () -> memory.commit(conflict));
        assertSame(declaration, memory.find(7L, 0, 0).commit());
    }
}
