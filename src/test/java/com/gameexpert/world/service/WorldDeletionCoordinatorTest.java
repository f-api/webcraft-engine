package com.gameexpert.world.service;

import com.gameexpert.common.ConflictException;
import com.gameexpert.engine.WorldEngineManager;
import com.gameexpert.world.repository.WorldDimensionRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WorldDeletionCoordinatorTest {
    @Test
    void validationPrecedesDrainAndSecondValidationPrecedesMutation() throws Throwable {
        Fixture fixture = new Fixture();
        List<String> events = new ArrayList<>();
        when(fixture.engines.beginWorldDeletion(1L)).thenAnswer(ignored -> {
            events.add("drain");
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            return true;
        });
        fixture.coordinator.delete(1, () -> {
            events.add("validate");
            WorldDeletionCoordinator.beforeDelete(1);
            events.add("delete");
            return null;
        });
        assertEquals(List.of("validate", "drain", "validate", "delete"), events);
        verify(fixture.engines).endWorldDeletion(1L);
        assertFalse(WorldDeletionCoordinator.hasReservation(1));
    }

    @Test
    void failedAuthorizationNeverDisposesRuntime() {
        Fixture fixture = new Fixture();
        IllegalArgumentException denied = new IllegalArgumentException("not owner");
        assertSame(denied, assertThrows(IllegalArgumentException.class,
                () -> fixture.coordinator.delete(1, () -> { throw denied; })));
        verifyNoInteractions(fixture.engines);
    }

    @Test
    void childReservationFailureReleasesOnlyPreviouslyReservedWorlds() {
        Fixture fixture = new Fixture();
        when(fixture.dimensions.findChildIdsByRootId(1)).thenReturn(List.of(2L));
        when(fixture.engines.beginWorldDeletion(2L)).thenReturn(false);
        assertThrows(ConflictException.class, () -> fixture.coordinator.delete(1, () -> {
            WorldDeletionCoordinator.beforeDelete(1);
            return null;
        }));
        verify(fixture.engines).endWorldDeletion(1L);
        verify(fixture.engines, never()).endWorldDeletion(2L);
    }

    @Test
    void changedIdentityOnSecondValidationDoesNotDeleteAndReleasesReservation() {
        Fixture fixture = new Fixture();
        AtomicInteger calls = new AtomicInteger();
        assertThrows(IllegalArgumentException.class, () -> fixture.coordinator.delete(1, () -> {
            if (calls.incrementAndGet() == 2) throw new IllegalArgumentException("identity changed");
            WorldDeletionCoordinator.beforeDelete(1);
            fail("mutation must not be reached");
            return null;
        }));
        assertEquals(2, calls.get());
        verify(fixture.engines).endWorldDeletion(1L);
    }

    @Test
    void unreservedNewDimensionIsRejectedInsteadOfDrainedUnderSqlLock() {
        Fixture fixture = new Fixture();
        assertThrows(ConflictException.class, () -> fixture.coordinator.delete(1, () -> {
            WorldDeletionCoordinator.beforeDelete(1);
            WorldDeletionCoordinator.requireChildrenReserved(1, List.of(2L));
            return null;
        }));
        verify(fixture.engines, never()).beginWorldDeletion(2L);
        verify(fixture.engines).endWorldDeletion(1L);
    }

    @Test
    void existingTransactionIsRejectedBeforeAnyServiceInvocation() {
        Fixture fixture = new Fixture();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            assertThrows(IllegalStateException.class, () -> fixture.coordinator.delete(1, () -> {
                fail("validation must not join a transaction that cannot release its lock");
                return null;
            }));
            verifyNoInteractions(fixture.engines);
        } finally {
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
    }

    @Test
    void childPresenceRejectsBeforeAnyRuntimeIsDrained() {
        Fixture fixture = new Fixture();
        when(fixture.dimensions.findChildIdsByRootId(1)).thenReturn(List.of(2L));
        when(fixture.presence.onlineCount(2L)).thenReturn(1L);
        assertThrows(ConflictException.class, () -> fixture.coordinator.delete(1, () -> {
            WorldDeletionCoordinator.beforeDelete(1);
            return null;
        }));
        verify(fixture.engines, never()).beginWorldDeletion(1L);
        verify(fixture.engines, never()).beginWorldDeletion(2L);
    }

    private static final class Fixture {
        final WorldEngineManager engines = mock(WorldEngineManager.class);
        final WorldDimensionRepository dimensions = mock(WorldDimensionRepository.class);
        final com.gameexpert.api.PresenceOperations presence = mock(com.gameexpert.api.PresenceOperations.class);
        final WorldDeletionCoordinator coordinator = new WorldDeletionCoordinator(engines, dimensions, presence);
        Fixture() { when(engines.beginWorldDeletion(1L)).thenReturn(true); }
    }
}
