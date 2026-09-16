package com.gameexpert.engine.persistence.finalcarrier;

import com.gameexpert.api.persistence.WorldAccess;
import com.gameexpert.api.persistence.WorldStore;
import com.gameexpert.engine.WorldRuntime;
import jakarta.persistence.EntityManager;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class FinalCarrierHistoryPaginationTest {
    private final com.gameexpert.terrain.persistence.CanonicalWorldgenStore canonical = mock(com.gameexpert.terrain.persistence.CanonicalWorldgenStore.class);
    private final WorldStore worlds = mock(WorldStore.class);
    private final FinalCarrierLaneMutationRepository lanes = mock(FinalCarrierLaneMutationRepository.class);
    private final WorldRuntime.FinalCarrierGameplayInstaller installer = mock(WorldRuntime.FinalCarrierGameplayInstaller.class);

    @Test void worldHistoryCrossesFormer65536LimitInBoundedPages() throws Exception {
        for (int total : new int[]{65536, 65537}) {
            reset(lanes);
            AtomicInteger count = new AtomicInteger();
            when(lanes.findAllByWorldIdAndIdGreaterThanOrderById(eq(1L), anyLong(), any(Pageable.class)))
                    .thenAnswer(invocation -> {
                        long after = invocation.getArgument(1);
                        Pageable page = invocation.getArgument(2);
                        assertThat(page.getPageSize()).isEqualTo(1024);
                        count.incrementAndGet();
                        return rows(after, total, page.getPageSize(), 0);
                    });
            assertThatCode(() -> service().recoverWorld(1L, installer)).doesNotThrowAnyException();
            assertThat(count.get()).isEqualTo(65);
        }
        verifyNoInteractions(installer);
    }

    @Test void chunkHistoryAlsoPagesAndChecksCoordinates() throws Exception {
        when(lanes.findAllByWorldIdAndChunkXAndChunkZAndIdGreaterThanOrderById(eq(1L), eq(9), eq(0), anyLong(), any(Pageable.class)))
                .thenAnswer(invocation -> rows(invocation.getArgument(3), 2050, 1024, 9));
        assertThatCode(() -> service().recoverChunk(1L, 9, 0, installer)).doesNotThrowAnyException();
        verify(lanes, times(3)).findAllByWorldIdAndChunkXAndChunkZAndIdGreaterThanOrderById(eq(1L), eq(9), eq(0), anyLong(), any(Pageable.class));
        when(lanes.findAllByWorldIdAndChunkXAndChunkZAndIdGreaterThanOrderById(eq(1L), eq(9), eq(0), anyLong(), any(Pageable.class)))
                .thenReturn(List.of(row(1L, 1L, 10)));
        assertThatThrownBy(() -> service().recoverChunk(1L, 9, 0, installer)).hasMessageContaining("foreign coordinate");
    }

    @Test void duplicateOrRegressingIdsAreRejectedInsteadOfLooping() throws Exception {
        when(lanes.findAllByWorldIdAndIdGreaterThanOrderById(eq(1L), anyLong(), any(Pageable.class)))
                .thenReturn(List.of(row(1L, 1L, 0), row(1L, 1L, 0)));
        assertThatThrownBy(() -> service().recoverWorld(1L, installer)).hasMessageContaining("invalid ordering");
    }

    @Test void foreignWorldRowsAreRejected() throws Exception {
        when(lanes.findAllByWorldIdAndIdGreaterThanOrderById(eq(1L), anyLong(), any(Pageable.class)))
                .thenReturn(List.of(row(1L, 2L, 0)));
        assertThatThrownBy(() -> service().recoverWorld(1L, installer)).hasMessageContaining("world identity");
    }

    @Test void processedPagesFlushBeforeDetachingOwnedEntities() throws Exception {
        FinalCarrierPersistenceService service = service();
        EntityManager entityManager = mock(EntityManager.class);
        ReflectionTestUtils.setField(service, "tickRecoveryEntityManager", entityManager);
        List<FinalCarrierLaneMutation> page = rows(0, 2, 1024, 0);
        when(lanes.findAllByWorldIdAndIdGreaterThanOrderById(eq(1L), anyLong(), any(Pageable.class))).thenReturn(page);
        service.recoverWorld(1L, installer);
        org.mockito.InOrder order = inOrder(entityManager);
        order.verify(entityManager).flush();
        order.verify(entityManager).detach(page.get(0));
        order.verify(entityManager).detach(page.get(1));
        verify(entityManager, never()).clear();
    }

    @Test void tickRecoveryPagesOldSourcesWithoutReplayingThemAsCurrent() throws Exception {
        installCanonicalSource();
        when(lanes.findAllByWorldIdAndLaneAndIdGreaterThanOrderById(eq(1L), eq("BLOCK_TICKS"), anyLong(), any(Pageable.class)))
                .thenAnswer(invocation -> rows(invocation.getArgument(2), 65537, 1024, 0));
        assertThat(service().loadWorld(1L)).isEmpty();
        verify(lanes, times(65)).findAllByWorldIdAndLaneAndIdGreaterThanOrderById(eq(1L), eq("BLOCK_TICKS"), anyLong(), any(Pageable.class));
        verify(lanes, never()).findAllByWorldIdAndChunkXAndChunkZAndLaneAndSourceFingerprintOrderById(anyLong(), anyInt(), anyInt(), anyString(), anyString(), any(Pageable.class));
        verify(canonical, times(1)).find(1L, 0, 0);
    }

    @Test void duplicateCurrentSourceMutationsStillFailAfterPaging() throws Exception {
        installCanonicalSource();
        String fingerprint = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(new byte[]{1}));
        FinalCarrierLaneMutation current = row(1L, 1L, 0);
        ReflectionTestUtils.setField(current, "sourceFingerprint", fingerprint);
        when(lanes.findAllByWorldIdAndLaneAndIdGreaterThanOrderById(eq(1L), eq("BLOCK_TICKS"), anyLong(), any(Pageable.class))).thenReturn(List.of(current));
        when(lanes.findAllByWorldIdAndChunkXAndChunkZAndLaneAndSourceFingerprintOrderById(eq(1L), eq(0), eq(0), eq("BLOCK_TICKS"), eq(fingerprint), any(Pageable.class))).thenReturn(List.of(current, current));
        assertThatThrownBy(() -> service().loadWorld(1L)).hasMessageContaining("multiple current-source");
    }

    private void installCanonicalSource() {
        com.gameexpert.authority.versioned.NeutralFinalChunk source = mock(com.gameexpert.authority.versioned.NeutralFinalChunk.class);
        when(source.withSidecars(any())).thenReturn(source);
        when(source.encodedCarrier()).thenReturn(new byte[]{1});
        com.gameexpert.terrain.persistence.CanonicalWorldgenStore.ChunkCommit commit = mock(com.gameexpert.terrain.persistence.CanonicalWorldgenStore.ChunkCommit.class);
        when(commit.semanticFinalChunk()).thenReturn(source);
        com.gameexpert.terrain.persistence.CanonicalWorldgenStore.CanonicalChunkSnapshot snapshot = mock(com.gameexpert.terrain.persistence.CanonicalWorldgenStore.CanonicalChunkSnapshot.class);
        when(snapshot.commit()).thenReturn(commit);
        when(canonical.find(1L, 0, 0)).thenReturn(snapshot);
    }

    private FinalCarrierPersistenceService service() throws Exception {
        when(worlds.findByIdForShare(1L)).thenReturn(Optional.of(mock(WorldAccess.class)));
        Constructor<?> constructor = Arrays.stream(FinalCarrierPersistenceService.class.getConstructors())
                .filter(Constructor::isVarArgs).findFirst().orElseThrow();
        Object[] arguments = Arrays.stream(constructor.getParameterTypes()).map(type -> {
            if (type == com.gameexpert.terrain.persistence.CanonicalWorldgenStore.class) return canonical;
            if (type == WorldStore.class) return worlds;
            if (type == FinalCarrierLaneMutationRepository.class) return lanes;
            return type.isArray() ? Array.newInstance(type.getComponentType(), 0) : mock(type);
        }).toArray();
        return (FinalCarrierPersistenceService) constructor.newInstance(arguments);
    }
    private static List<FinalCarrierLaneMutation> rows(long after, int total, int limit, int chunkX) {
        return LongStream.rangeClosed(after + 1, Math.min(total, after + limit))
                .mapToObj(id -> row(id, 1L, chunkX)).toList();
    }
    private static FinalCarrierLaneMutation row(long id, long world, int chunkX) {
        FinalCarrierLaneMutation row = new FinalCarrierLaneMutation(world, chunkX, 0, "BLOCK_TICKS", "a".repeat(64), "b".repeat(64), new byte[]{1});
        ReflectionTestUtils.setField(row, "id", id);
        return row;
    }
}
