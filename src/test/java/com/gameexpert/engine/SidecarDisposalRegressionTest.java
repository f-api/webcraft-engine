package com.gameexpert.engine;

import com.gameexpert.boat.service.BoatPersistenceService;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.FutureTask;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SidecarDisposalRegressionTest {
    @Test
    void failedFinalBoatWriteBlocksDisposalAndRetries() {
        DisposalDirtyRegressionTest.Fixture fixture = new DisposalDirtyRegressionTest.Fixture();
        List<FutureTask<Void>> futures = new ArrayList<>();
        when(fixture.writer.submitFuture(any(Runnable.class))).thenAnswer(invocation -> {
            FutureTask<Void> future = new FutureTask<>(invocation.getArgument(0), null);
            futures.add(future);
            fixture.queue.add(future);
            return future;
        });
        BoatPersistenceService boats = mock(BoatPersistenceService.class);
        doThrow(new IllegalStateException("audit DB unavailable")).when(boats).replaceWorld(anyLong(), any());
        WorldRuntime runtime = fixture.runtime(null);
        runtime.installBoatPersistence(boats);
        fixture.disposeAndDrain(runtime);
        verify(boats).replaceWorld(anyLong(), any());
        assertEquals(1, futures.size());
        assertFalse(fixture.released.get());
        verify(fixture.scheduler, never()).shutdown();
        doNothing().when(boats).replaceWorld(anyLong(), any());
        fixture.retryAndDrain();
        assertTrue(fixture.released.get());
        verify(boats, times(2)).replaceWorld(anyLong(), any());
        verify(fixture.scheduler).shutdown();
    }

    @Test
    void successfulFinalBoatFutureCompletesDisposalNormally() throws Exception {
        DisposalDirtyRegressionTest.Fixture fixture = new DisposalDirtyRegressionTest.Fixture();
        List<FutureTask<Void>> futures = new ArrayList<>();
        when(fixture.writer.submitFuture(any(Runnable.class))).thenAnswer(invocation -> {
            FutureTask<Void> future = new FutureTask<>(invocation.getArgument(0), null);
            futures.add(future); fixture.queue.add(future); return future;
        });
        BoatPersistenceService boats = mock(BoatPersistenceService.class);
        WorldRuntime runtime = fixture.runtime(null);
        runtime.installBoatPersistence(boats);
        fixture.disposeAndDrain(runtime);
        verify(boats).replaceWorld(anyLong(), any());
        assertEquals(1, futures.size());
        assertNull(futures.getFirst().get());
        assertTrue(fixture.released.get());
    }
}
