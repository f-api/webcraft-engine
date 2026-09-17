package com.gameexpert.engine;

import com.gameexpert.ground.service.GroundMutationOutcome;
import com.gameexpert.projectile.service.ProjectilePersistenceService;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ProjectileRecoveryPersistenceRegressionTest {
    @Test
    void earlierWriterCompletionCanAcquireMonitorBeforeRecoveryTransaction() throws Exception {
        PersistenceExecutor writer = new PersistenceExecutor();
        Fixture fixture = new Fixture(writer);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch callbackAllowed = new CountDownLatch(1);
        Future<?> earlier = writer.submitFuture(() -> {
            started.countDown();
            awaitLatch(callbackAllowed);
            fixture.runtime.enqueuePersistenceCompletion(() -> { });
        });
        Thread owner = fixture.owner();
        try {
            assertTrue(started.await(2, TimeUnit.SECONDS));
            owner.start();
            await(() -> writer.queueDepth() == 1);
            assertTrue((boolean) get(fixture.runtime, "projectileSaveInFlight"));
            callbackAllowed.countDown();
            earlier.get(2, TimeUnit.SECONDS);
            owner.join(2000);
            assertFalse(owner.isAlive(), "recovery must not hold the callback admission monitor");
            assertNull(fixture.failure.get());
            fixture.assertCommitted();
        } finally {
            callbackAllowed.countDown();
            owner.interrupt();
            owner.join(2000);
            writer.shutdown();
        }
    }

    @Test
    void recoveryPersistsExactGroundSnapshotWithProjectileRemovalInOneCall() throws Exception {
        Fixture fixture = new Fixture(null);
        com.gameexpert.ground.dto.GroundItemSnapshot item =
                new com.gameexpert.ground.dto.GroundItemSnapshot(1L,
                        (short) com.gameexpert.terrain.Blocks.STONE, 1, 0, 0L,
                        0, 0, null, null, 1.5, 64, 2.5, 0, 0, 0, false, 0, 0, 0L);
        com.gameexpert.ground.dto.GroundXpOrbSnapshot orb =
                new com.gameexpert.ground.dto.GroundXpOrbSnapshot(2L, 1, 1.5, 64, 2.5, 0, 0, 0, 0);
        List<com.gameexpert.ground.dto.GroundItemSnapshot> ground = List.of(item);
        List<com.gameexpert.ground.dto.GroundXpOrbSnapshot> xp = List.of(orb);
        when(fixture.items.persistenceSnapshot()).thenReturn(ground);
        when(fixture.xp.persistenceSnapshot()).thenReturn(xp);
        fixture.runtime.persistProjectiles();
        verify(fixture.persistence).replaceWorldWithGround(eq(42L), eq(0L), eq(1L),
                eq(List.of()), same(ground), same(xp));
        fixture.assertCommitted();
    }

    @Test
    void failurePreservesRecoveryAndAllowsNextTickToRetry() throws Exception {
        PersistenceExecutor writer = new PersistenceExecutor();
        Fixture fixture = new Fixture(writer);
        when(fixture.persistence.replaceWorldWithGround(anyLong(), anyLong(), anyLong(), anyList(), anyList(), anyList()))
                .thenThrow(new IllegalStateException("DB unavailable"))
                .thenReturn(GroundMutationOutcome.COMMITTED);
        try {
            assertThrows(IllegalStateException.class, fixture.runtime::persistProjectiles);
            fixture.assertRetryable();
            fixture.runtime.persistProjectiles();
            fixture.assertCommitted();
            verify(fixture.persistence, times(2)).replaceWorldWithGround(eq(42L), eq(0L), eq(1L), anyList(), anyList(), anyList());
        } finally { writer.shutdown(); }
    }

    @Test
    void staleOutcomeDoesNotAcknowledgeAndRetriesSameRevision() throws Exception {
        Fixture fixture = new Fixture(null);
        when(fixture.persistence.replaceWorldWithGround(anyLong(), anyLong(), anyLong(), anyList(), anyList(), anyList()))
                .thenReturn(GroundMutationOutcome.STALE, GroundMutationOutcome.COMMITTED);
        fixture.runtime.persistProjectiles();
        fixture.assertRetryable();
        fixture.runtime.persistProjectiles();
        fixture.assertCommitted();
        verify(fixture.persistence, times(2)).replaceWorldWithGround(eq(42L), eq(0L), eq(1L), anyList(), anyList(), anyList());
    }

    @Test
    void rejectedSubmissionReleasesLaneWithoutAcknowledgingRecovery() throws Exception {
        PersistenceExecutor writer = mock(PersistenceExecutor.class);
        when(writer.submitFuture(any(Runnable.class))).thenThrow(new RejectedExecutionException("closed"));
        Fixture fixture = new Fixture(writer);
        assertThrows(RejectedExecutionException.class, fixture.runtime::persistProjectiles);
        fixture.assertRetryable();
        verifyNoInteractions(fixture.persistence);
        when(fixture.context.persistenceExecutor()).thenReturn(null);
        fixture.runtime.persistProjectiles();
        fixture.assertCommitted();
    }

    @Test
    void cancelledUnstartedTaskReleasesLaneAndPreservesRecoveryForRetry() throws Exception {
        PersistenceExecutor writer = mock(PersistenceExecutor.class);
        java.util.concurrent.FutureTask<Void> cancelled =
                new java.util.concurrent.FutureTask<>(() -> null);
        assertTrue(cancelled.cancel(false));
        when(writer.submitFuture(any(Runnable.class))).thenAnswer(invocation -> cancelled);
        Fixture fixture = new Fixture(writer);
        assertThrows(java.util.concurrent.CancellationException.class, fixture.runtime::persistProjectiles);
        fixture.assertRetryable();
        verifyNoInteractions(fixture.persistence);
        when(fixture.context.persistenceExecutor()).thenReturn(null);
        fixture.runtime.persistProjectiles();
        fixture.assertCommitted();
    }

    @Test
    void interruptionDoesNotReleaseLaneBeforeRunningTransactionFinishes() throws Exception {
        PersistenceExecutor writer = new PersistenceExecutor();
        Fixture fixture = new Fixture(writer);
        CountDownLatch transactionStarted = new CountDownLatch(1);
        CountDownLatch commitAllowed = new CountDownLatch(1);
        when(fixture.persistence.replaceWorldWithGround(anyLong(), anyLong(), anyLong(), anyList(), anyList(), anyList()))
                .thenAnswer(invocation -> {
                    transactionStarted.countDown();
                    awaitLatch(commitAllowed);
                    return GroundMutationOutcome.COMMITTED;
                });
        Thread owner = fixture.owner();
        try {
            owner.start();
            assertTrue(transactionStarted.await(2, TimeUnit.SECONDS));
            owner.interrupt();
            await(() -> owner.getState() == Thread.State.WAITING);
            assertTrue((boolean) get(fixture.runtime, "projectileSaveInFlight"));
            assertEquals(0L, get(fixture.runtime, "groundRevision"));
            fixture.runtime.persistProjectiles();
            verify(fixture.persistence, times(1)).replaceWorldWithGround(anyLong(), anyLong(), anyLong(), anyList(), anyList(), anyList());
            commitAllowed.countDown();
            owner.join(2000);
            assertFalse(owner.isAlive());
            assertNull(fixture.failure.get());
            assertTrue(fixture.interruptedOnExit.get());
            fixture.assertCommitted();
        } finally {
            commitAllowed.countDown();
            owner.interrupt();
            owner.join(2000);
            writer.shutdown();
        }
    }

    @Test
    void terminalTransitionPreservesDurableRevisionWithoutRevivingAbortedRuntime() throws Exception {
        for (String phase : List.of("DRAINING", "ABORTED", "DISPOSED")) {
            PersistenceExecutor writer = new PersistenceExecutor();
            Fixture fixture = new Fixture(writer);
            CountDownLatch started = new CountDownLatch(1);
            CountDownLatch commitAllowed = new CountDownLatch(1);
            when(fixture.persistence.replaceWorldWithGround(anyLong(), anyLong(), anyLong(), anyList(), anyList(), anyList()))
                    .thenAnswer(invocation -> {
                        started.countDown();
                        awaitLatch(commitAllowed);
                        return GroundMutationOutcome.COMMITTED;
                    });
            Thread owner = fixture.owner();
            try {
                owner.start();
                assertTrue(started.await(2, TimeUnit.SECONDS));
                synchronized (fixture.runtime) { fixture.phase(phase); }
                commitAllowed.countDown();
                owner.join(2000);
                assertFalse(owner.isAlive());
                assertNull(fixture.failure.get());
                assertEquals(1L, get(fixture.runtime, "groundRevision"));
                assertFalse((boolean) get(fixture.runtime, "projectileSaveInFlight"));
                verify(fixture.mobs, times(phase.equals("DRAINING") ? 1 : 0))
                        .acknowledgeProjectileRecoveryPersistence();
                assertEquals(phase, get(fixture.runtime, "terminalPhase").toString());
            } finally {
                commitAllowed.countDown();
                owner.interrupt();
                owner.join(2000);
                writer.shutdown();
            }
        }
    }

    private static final class Fixture {
        final WorldRuntime runtime = mock(WorldRuntime.class, CALLS_REAL_METHODS);
        final EngineContext context = mock(EngineContext.class);
        final ItemEntitySystem items = mock(ItemEntitySystem.class);
        final XpOrbSystem xp = mock(XpOrbSystem.class);
        final MobSystem mobs = mock(MobSystem.class);
        final ProjectilePersistenceService persistence = mock(ProjectilePersistenceService.class);
        final AtomicReference<Throwable> failure = new AtomicReference<>();
        final AtomicBoolean interruptedOnExit = new AtomicBoolean();

        Fixture(PersistenceExecutor writer) throws Exception {
            when(context.persistenceExecutor()).thenReturn(writer);
            when(mobs.projectileRecoveryPersistencePending()).thenReturn(true);
            when(mobs.projectilePersistenceSnapshot()).thenReturn(List.of());
            when(items.persistenceSnapshot()).thenReturn(List.of());
            when(xp.persistenceSnapshot()).thenReturn(List.of());
            when(persistence.replaceWorldWithGround(anyLong(), anyLong(), anyLong(), anyList(), anyList(), anyList()))
                    .thenReturn(GroundMutationOutcome.COMMITTED);
            set(runtime, "ctx", context);
            set(runtime, "worldId", 42L);
            set(runtime, "mobSystem", mobs);
            set(runtime, "itemSystem", items);
            set(runtime, "xpOrbSystem", xp);
            set(runtime, "projectilePersistence", persistence);
            set(runtime, "persistenceCompletions", new ConcurrentLinkedQueue<>());
            phase("RUNNING");
        }

        void phase(String name) throws Exception {
            Class<?> type = Class.forName(WorldRuntime.class.getName() + "$TerminalPhase");
            set(runtime, "terminalPhase", Arrays.stream(type.getEnumConstants())
                    .filter(value -> value.toString().equals(name)).findFirst().orElseThrow());
        }

        Thread owner() {
            Thread thread = new Thread(() -> {
                try { runtime.persistProjectiles(); }
                catch (Throwable thrown) { failure.set(thrown); }
                finally { interruptedOnExit.set(Thread.currentThread().isInterrupted()); }
            }, "projectile-recovery-test-owner");
            thread.setDaemon(true);
            return thread;
        }

        void assertRetryable() throws Exception {
            assertFalse((boolean) get(runtime, "projectileSaveInFlight"));
            assertEquals(0L, get(runtime, "groundRevision"));
            assertTrue(mobs.projectileRecoveryPersistencePending());
            verify(mobs, never()).acknowledgeProjectileRecoveryPersistence();
        }

        void assertCommitted() throws Exception {
            assertFalse((boolean) get(runtime, "projectileSaveInFlight"));
            assertEquals(1L, get(runtime, "groundRevision"));
            verify(mobs).acknowledgeProjectileRecoveryPersistence();
        }
    }

    private static void awaitLatch(CountDownLatch latch) {
        try { assertTrue(latch.await(5, TimeUnit.SECONDS)); }
        catch (InterruptedException failure) { throw new AssertionError(failure); }
    }

    private static void await(BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) Thread.sleep(5);
        assertTrue(condition.getAsBoolean());
    }

    private static Object get(Object target, String name) throws Exception {
        Field field = WorldRuntime.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static void set(Object target, String name, Object value) throws Exception {
        Field field = WorldRuntime.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
