package com.gameexpert.terrain.persistence;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CanonicalSnapshotProjectionTest {
    @Test void unmanagedReadKeepsAbiValidationAndLaneMasks() {
        CanonicalWorldgenChunkRepository.SnapshotRow source = mock(CanonicalWorldgenChunkRepository.SnapshotRow.class);
        when(source.getAbiVersion()).thenReturn(-1);
        when(source.getLaneClaimMask()).thenReturn(7);
        when(source.getLaneAckMask()).thenReturn(3);
        when(source.getLaneRejectedMask()).thenReturn(4);
        CanonicalWorldgenChunk detached = CanonicalWorldgenChunk.fromSnapshotRow(source);
        assertThat(detached.laneClaimMask()).isEqualTo(7);
        assertThat(detached.laneAckMask()).isEqualTo(3);
        assertThat(detached.laneRejectedMask()).isEqualTo(4);
        assertThatThrownBy(detached::toCommit).hasMessageContaining("ABI/schema");
    }

    @Test void unmanagedReadDoesNotBypassMalformedGroupValidation() {
        CanonicalWorldgenChunkRepository.SnapshotRow source = mock(CanonicalWorldgenChunkRepository.SnapshotRow.class);
        when(source.getAbiVersion()).thenReturn(CanonicalWorldgenStore.ABI_VERSION);
        when(source.getCarrierSchema()).thenReturn(CanonicalWorldgenStore.FINAL_CARRIER_SCHEMA);
        when(source.getGroupFingerprint()).thenReturn(new byte[32]);
        CanonicalWorldgenChunk detached = CanonicalWorldgenChunk.fromSnapshotRow(source);
        assertThatThrownBy(detached::toCommit).hasMessageContaining("invalid group metadata");
    }
}
