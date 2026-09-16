package com.gameexpert.engine;

import com.gameexpert.block.repository.WorldBlockDiffRepository;
import com.gameexpert.brewing.repository.WorldBrewingStandRepository;
import com.gameexpert.brewing.service.BrewingPersistenceService;
import com.gameexpert.campfire.repository.WorldCampfireRepository;
import com.gameexpert.campfire.service.CampfirePersistenceService;
import com.gameexpert.config.EngineProperties;
import com.gameexpert.crafter.service.CrafterPersistenceService;
import com.gameexpert.engine.inventory.PlayerInventory;
import com.gameexpert.api.SessionRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DisposalDirtyRegressionTest {
    @Test
    void failedBrewingCheckpointKeepsRuntimeUntilSuccessfulRetry() {
        Fixture fixture = new Fixture();
        WorldBrewingStandRepository repository = mock(WorldBrewingStandRepository.class);
        when(repository.findDirtyByWorldId(anyLong(), any())).thenThrow(new IllegalStateException("audit DB unavailable"));
        BrewingPersistenceService brewing = new BrewingPersistenceService(repository, fixture.writer, fixture.transactions);
        WorldRuntime runtime = fixture.runtime(null);
        runtime.installBrewingPersistence(brewing);
        runtime.brewingStorage().openAt(2, 70, 4);
        runtime.brewingStorage().markDirty(2, 70, 4);
        fixture.disposeAndDrain(runtime);
        verify(repository).findDirtyByWorldId(anyLong(), any());
        assertTrue(runtime.brewingStorage().hasDirty(), "DB failure restores dirty brewing coordinates");
        assertFalse(fixture.released.get());
        verify(fixture.scheduler, never()).shutdown();
        doReturn(List.of()).when(repository).findDirtyByWorldId(anyLong(), any());
        fixture.retryAndDrain();
        assertTrue(fixture.released.get());
        assertFalse(runtime.brewingStorage().hasDirty());
        verify(fixture.scheduler).shutdown();
    }

    @Test
    void failedCampfireCheckpointCorrectlyKeepsRuntimeForRetry() {
        Fixture fixture = new Fixture();
        WorldCampfireRepository repository = mock(WorldCampfireRepository.class);
        when(repository.findDirtyByWorldId(anyLong(), any())).thenThrow(new IllegalStateException("audit DB unavailable"));
        CampfirePersistenceService campfires = new CampfirePersistenceService(repository, fixture.writer, fixture.transactions);
        WorldRuntime runtime = fixture.runtime(campfires);
        runtime.campfireStorage().insertAt(2, 70, 4, PlayerInventory.COD_RAW);
        runtime.campfireStorage().markDirty(2, 70, 4);
        fixture.disposeAndDrain(runtime);
        assertTrue(runtime.campfireStorage().hasDirty());
        assertFalse(fixture.released.get(), "Control: existing dirty campfire gate preserves retry");
        verify(fixture.scheduler, never()).shutdown();
    }

    @Test
    void failedCrafterCheckpointKeepsRuntimeUntilSuccessfulRetry() {
        Fixture fixture = new Fixture();
        CrafterPersistenceService crafters = mock(CrafterPersistenceService.class);
        doThrow(new IllegalStateException("audit DB unavailable")).when(crafters).replaceAll(anyLong(), any());
        WorldRuntime runtime = fixture.runtime(null);
        runtime.installCrafterPersistence(crafters);
        runtime.setCrafterDisabledSlots(2, 70, 4, 1);
        fixture.disposeAndDrain(runtime);
        verify(crafters).replaceAll(anyLong(), any());
        assertFalse(fixture.released.get());
        verify(fixture.scheduler, never()).shutdown();
        doNothing().when(crafters).replaceAll(anyLong(), any());
        fixture.retryAndDrain();
        assertTrue(fixture.released.get());
        verify(crafters, times(2)).replaceAll(anyLong(), any());
        verify(fixture.scheduler).shutdown();
    }

    @Test
    void earlySettlementRetryFailureIsNotClearedBeforeDisposalBarrier() throws Exception {
        Fixture fixture = new Fixture();
        WorldRuntime runtime = fixture.runtime(null);
        WorldTickLoop tickLoop = spy(runtime.tickLoop());
        java.lang.reflect.Field field = WorldRuntime.class.getDeclaredField("tickLoop");
        field.setAccessible(true);
        field.set(runtime, tickLoop);
        doThrow(new IllegalStateException("animal settlement unavailable")).doNothing()
                .when(tickLoop).retryAnimalSettlementPersistenceForDisposal();
        fixture.disposeAndDrain(runtime);
        assertFalse(fixture.released.get());
        verify(fixture.scheduler, never()).shutdown();
        fixture.retryAndDrain();
        assertTrue(fixture.released.get());
        verify(tickLoop, times(2)).retryAnimalSettlementPersistenceForDisposal();
    }

    static final class Fixture {
        final List<Runnable> queue = new ArrayList<>();
        final List<Runnable> retries = new ArrayList<>();
        final PersistenceExecutor writer = mock(PersistenceExecutor.class);
        final TransactionTemplate transactions = mock(TransactionTemplate.class);
        final ScheduledExecutorService scheduler = mock(ScheduledExecutorService.class);
        final AtomicBoolean released = new AtomicBoolean();
        Fixture() {
            when(scheduler.schedule(any(Runnable.class), anyLong(), any(java.util.concurrent.TimeUnit.class)))
                    .thenAnswer(invocation -> { retries.add(invocation.getArgument(0)); return null; });
            when(writer.trySubmit(any(Runnable.class))).thenAnswer(invocation -> {
                queue.add(invocation.getArgument(0)); return true;
            });
            doAnswer(invocation -> {
                Consumer<TransactionStatus> action = invocation.getArgument(0);
                action.accept(new SimpleTransactionStatus()); return null;
            }).when(transactions).executeWithoutResult(any());
        }
        WorldRuntime runtime(CampfirePersistenceService campfires) {
            EngineContext context = new EngineContext(null, mock(SessionRegistry.class),
                    mock(WorldBlockDiffRepository.class), null, null, writer, null,
                    new EngineProperties(1, 5000, false, -1));
            WorldRuntime runtime = new WorldRuntime(994L, 12345, context, null, null, campfires, null);
            runtime.attach(scheduler, () -> released.set(true));
            return runtime;
        }
        void disposeAndDrain(WorldRuntime runtime) {
            runtime.disposeNow();
            drainQueue();
        }
        void retryAndDrain() {
            assertFalse(retries.isEmpty());
            retries.removeFirst().run();
            drainQueue();
        }
        void drainQueue() {
            int runs = 0;
            while (!queue.isEmpty()) {
                assertTrue(runs++ < 20, "unexpected unbounded persistence queue");
                queue.removeFirst().run();
            }
        }
    }
}
