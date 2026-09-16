package com.gameexpert.verification;

import com.gameexpert.chest.entity.WorldChest;
import com.gameexpert.chest.repository.WorldChestRepository;
import com.gameexpert.chest.service.ChestPersistenceService;
import com.gameexpert.engine.ChestInventory;
import com.gameexpert.engine.ChestStorage;
import com.gameexpert.engine.PersistenceExecutor;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Controlled actual service calls; repository effects model committed rows and the real FIFO. */
class StorageAtomicityRegressionTest {
    @Test
    void sequentialCheckpointsPersistBothChanges() throws Exception {
        Fixture fixture = new Fixture();
        fixture.addAndQueue();
        fixture.runNext();
        fixture.addAndQueue();
        fixture.runNext();
        assertEquals(2, fixture.persistedCount());
        assertEquals(2, fixture.runtimeCount());
        assertFalse(fixture.storage.hasDirty());
    }

    @Test
    void queuedNewChestChangesRemainDirtyUntilNextCommittedCapture() throws Exception {
        Fixture fixture = new Fixture();
        fixture.addAndQueue();
        fixture.addAndQueue();
        assertEquals(1, fixture.queue.size());
        assertTrue(fixture.storage.hasDirty());
        fixture.runNext();
        fixture.service.flushDirty(1L, fixture.storage);
        fixture.runNext();
        assertEquals(2, fixture.persistedCount());
        assertEquals(2, fixture.runtimeCount());
        assertFalse(fixture.storage.hasDirty());
    }

    @Test
    void queuedBoundChestChangesCaptureThePrecedingCommittedBinding() throws Exception {
        Fixture fixture = new Fixture();
        fixture.addAndQueue(); fixture.runNext();
        fixture.addAndQueue(); fixture.addAndQueue();
        assertEquals(1, fixture.queue.size());
        fixture.runNext();
        fixture.service.flushDirty(1L, fixture.storage);
        fixture.runNext();
        assertEquals(3, fixture.persistedCount());
        assertEquals(3, fixture.runtimeCount());
        assertFalse(fixture.storage.hasDirty());
    }

    @Test
    void queuedCheckpointCannotOverwriteAReplacementRow() throws Exception {
        Fixture fixture = new Fixture();
        fixture.addAndQueue(); fixture.runNext();
        fixture.addAndQueue();
        WorldChest replacement = new WorldChest(1L, 0, 80, 0);
        Field id = WorldChest.class.getDeclaredField("id");
        id.setAccessible(true); id.set(replacement, 2L);
        replacement.replaceItemsIfNewer(fixture.rows.getFirst().getItems(), 100);
        fixture.rows.clear(); fixture.rows.add(replacement);
        fixture.runNext();
        assertEquals(2L, fixture.rows.getFirst().getId());
        assertEquals(100, replacement.getPersistenceRevision());
        assertEquals(1, fixture.persistedCount());
    }

    @Test
    void queueRejectionPreservesDirtyAndReleasesCheckpointSlot() throws Exception {
        Fixture fixture = new Fixture();
        doReturn(false).when(fixture.executor).trySubmit(any(Runnable.class));
        fixture.addAndQueue();
        assertTrue(fixture.storage.hasDirty());
        doAnswer(invocation -> {
            fixture.queue.add(invocation.getArgument(0)); return true;
        }).when(fixture.executor).trySubmit(any(Runnable.class));
        fixture.service.flushDirty(1L, fixture.storage);
        assertEquals(1, fixture.queue.size());
        fixture.runNext();
        assertEquals(1, fixture.persistedCount());
        assertFalse(fixture.storage.hasDirty());
    }

    @Test
    void captureFailureRestoresDirtyAndReleasesCheckpointSlot() throws Exception {
        Fixture fixture = new Fixture();
        ChestInventory resident = spy(fixture.inventory);
        fixture.storage.load(0, 80, 0, resident);
        assertEquals(0, resident.add((short) 1, 1));
        fixture.storage.markDirty(0, 80, 0);
        doThrow(new IllegalStateException("snapshot unavailable")).when(resident).persistenceSnapshot();
        assertThrows(IllegalStateException.class, () -> fixture.service.flushDirty(1L, fixture.storage));
        assertTrue(fixture.storage.hasDirty());
        assertTrue(fixture.queue.isEmpty());
        doCallRealMethod().when(resident).persistenceSnapshot();
        fixture.service.flushDirty(1L, fixture.storage);
        fixture.runNext();
        assertEquals(1, fixture.persistedCount());
        assertFalse(fixture.storage.hasDirty());
    }

    static final class Fixture {
        final WorldChestRepository repository = mock(WorldChestRepository.class);
        final PersistenceExecutor executor = mock(PersistenceExecutor.class);
        final TransactionTemplate transactions = mock(TransactionTemplate.class);
        final List<Runnable> queue = new ArrayList<>();
        final List<WorldChest> rows = new ArrayList<>();
        final ChestStorage storage = new ChestStorage();
        final ChestPersistenceService service;
        final ChestInventory inventory;

        Fixture() throws Exception {
            when(executor.trySubmit(any(Runnable.class))).thenAnswer(invocation -> {
                queue.add(invocation.getArgument(0)); return true;
            });
            doAnswer(invocation -> {
                Consumer<TransactionStatus> action = invocation.getArgument(0);
                action.accept(new SimpleTransactionStatus()); return null;
            }).when(transactions).executeWithoutResult(any());
            when(repository.findDirtyByWorldId(anyLong(), any())).thenAnswer(ignored -> List.copyOf(rows));
            when(repository.findLockedById(anyLong())).thenAnswer(invocation -> rows.stream()
                    .filter(row -> row.getId().equals(invocation.getArgument(0))).findFirst());
            when(repository.saveAll(any())).thenAnswer(invocation -> {
                Iterable<WorldChest> changed = invocation.getArgument(0);
                List<WorldChest> result = new ArrayList<>();
                for (WorldChest row : changed) {
                    if (row.getId() == null) {
                        Field id = WorldChest.class.getDeclaredField("id");
                        id.setAccessible(true); id.set(row, 1L);
                        rows.add(row);
                    }
                    result.add(row);
                }
                return result;
            });
            service = new ChestPersistenceService(repository, executor, transactions);
            inventory = storage.openAt(0, 80, 0);
        }
        void addAndQueue() {
            assertEquals(0, inventory.add((short) 1, 1));
            storage.markDirty(0, 80, 0);
            service.flushDirty(1L, storage);
        }
        void runNext() { queue.removeFirst().run(); }
        int persistedCount() { return rows.getFirst().getItems().getFirst().getItemCount(); }
        int runtimeCount() { return inventory.snapshot().counts()[0]; }
    }
}
