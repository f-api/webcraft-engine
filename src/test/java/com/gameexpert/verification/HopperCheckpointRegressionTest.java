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

import com.gameexpert.engine.FurnaceStorage;
import com.gameexpert.engine.FurnaceInventory;
import com.gameexpert.furnace.service.FurnacePersistenceService;
import com.gameexpert.furnace.repository.WorldFurnaceRepository;
import com.gameexpert.furnace.entity.WorldFurnace;

class HopperCheckpointRegressionTest {
    @Test
    void reverseTransferRollsBackBothEndpointsAndRetriesAsOneTransaction() throws Exception {
        JointFixture fixture = new JointFixture();
        fixture.moveFromFurnace();
        fixture.capture();
        assertEquals(1, fixture.chest.queue.size());
        when(fixture.repository.findDirtyByWorldId(anyLong(), any()))
                .thenThrow(new IllegalStateException("furnace database write failed"));
        fixture.chest.runNext();
        assertTrue(fixture.chest.rows.isEmpty());
        assertEquals(1, fixture.durable.getFirst().getOutputCount());
        assertTrue(fixture.chest.storage.hasDirty());
        assertTrue(fixture.furnaces.hasDirty());
        doAnswer(ignored -> List.copyOf(fixture.durable)).when(fixture.repository)
                .findDirtyByWorldId(anyLong(), any());
        fixture.capture(); fixture.chest.runNext();
        assertEquals(1, fixture.chest.persistedCount());
        assertEquals(0, fixture.durable.getFirst().getOutputCount());
        assertFalse(fixture.chest.storage.hasDirty());
        assertFalse(fixture.furnaces.hasDirty());
        verify(fixture.chest.transactions, times(2)).executeWithoutResult(any());
    }

    @Test
    void pendingCheckpointDoesNotDrainEitherEndpointsLaterChanges() throws Exception {
        JointFixture fixture = new JointFixture();
        fixture.moveFromFurnace(); fixture.capture();
        fixture.chest.storage.markDirty(0, 80, 0);
        fixture.furnaces.markDirty(0, 81, 0);
        fixture.capture();
        assertEquals(1, fixture.chest.queue.size());
        assertTrue(fixture.chest.storage.hasDirty());
        assertTrue(fixture.furnaces.hasDirty());
        fixture.chest.runNext();
        fixture.capture(); fixture.chest.runNext();
        assertFalse(fixture.chest.storage.hasDirty());
        assertFalse(fixture.furnaces.hasDirty());
        assertEquals(1, fixture.chest.persistedCount());
        assertEquals(0, fixture.durable.getFirst().getOutputCount());
    }

    @Test
    void rejectedQueueRestoresBothEndpointsAndReleasesPendingGate() throws Exception {
        JointFixture fixture = new JointFixture(); fixture.moveFromFurnace();
        doReturn(false).when(fixture.chest.executor).trySubmit(any(Runnable.class));
        fixture.capture();
        assertTrue(fixture.chest.storage.hasDirty());
        assertTrue(fixture.furnaces.hasDirty());
        doAnswer(invocation -> {
            fixture.chest.queue.add(invocation.getArgument(0)); return true;
        }).when(fixture.chest.executor).trySubmit(any(Runnable.class));
        fixture.capture(); fixture.chest.runNext();
        assertEquals(1, fixture.chest.persistedCount());
        assertEquals(0, fixture.durable.getFirst().getOutputCount());
    }

    @Test
    void captureExceptionRestoresPreviouslyDrainedChestAndFurnace() throws Exception {
        JointFixture fixture = new JointFixture(); fixture.moveFromFurnace();
        FurnaceInventory broken = spy(fixture.furnace);
        fixture.furnaces.load(0, 81, 0, broken);
        doThrow(new IllegalStateException("snapshot failure")).when(broken).snapshot();
        fixture.furnaces.markDirty(0, 81, 0);
        assertThrows(IllegalStateException.class, fixture::capture);
        assertTrue(fixture.chest.storage.hasDirty());
        assertTrue(fixture.furnaces.hasDirty());
        assertTrue(fixture.chest.queue.isEmpty());
        doCallRealMethod().when(broken).snapshot();
        fixture.capture(); fixture.chest.runNext();
        assertEquals(1, fixture.chest.persistedCount());
    }

    private static final class JointFixture {
        final Fixture chest = new Fixture();
        final FurnaceStorage furnaces = new FurnaceStorage();
        final FurnaceInventory furnace = furnaces.openAt(0, 81, 0);
        final WorldFurnaceRepository repository = mock(WorldFurnaceRepository.class);
        final List<WorldFurnace> durable = new ArrayList<>();
        final FurnacePersistenceService service;

        JointFixture() throws Exception {
            furnace.restore(new short[]{0, 0, (short) com.gameexpert.terrain.Blocks.STONE},
                    new int[]{0, 0, 1}, 0, 0, 0,
                    com.gameexpert.engine.FurnaceVariant.FURNACE, 1L, 0);
            WorldFurnace initial = new WorldFurnace(1L, 0, 81, 0);
            initial.replaceIfNewer(furnace.snapshot()); durable.add(initial);
            when(repository.findDirtyByWorldId(anyLong(), any()))
                    .thenAnswer(ignored -> List.copyOf(durable));
            when(repository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
            service = new FurnacePersistenceService(repository, chest.executor,
                    mock(TransactionTemplate.class));
            // Controlled transaction: a downstream failure removes any newly inserted chest row.
            // Actual MySQL rollback is independently covered by the integration audit.
            doAnswer(invocation -> {
                List<WorldChest> before = List.copyOf(chest.rows);
                Consumer<TransactionStatus> action = invocation.getArgument(0);
                try { action.accept(new SimpleTransactionStatus()); }
                catch (RuntimeException failure) {
                    chest.rows.clear(); chest.rows.addAll(before); throw failure;
                }
                return null;
            }).when(chest.transactions).executeWithoutResult(any());
        }
        void moveFromFurnace() {
            assertEquals(1, furnace.take(2, 1));
            assertEquals(0, chest.inventory.add((short) com.gameexpert.terrain.Blocks.STONE, 1));
            furnaces.markDirty(0, 81, 0); chest.storage.markDirty(0, 80, 0);
        }
        void capture() {
            chest.service.flushDirty(1L, chest.storage, null,
                    () -> service.captureDirty(1L, furnaces, 0, 0L, false, ignored -> {}));
        }
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
            assertEquals(0, inventory.add((short) com.gameexpert.terrain.Blocks.COBBLE, 1));
            storage.markDirty(0, 80, 0);
            service.flushDirty(1L, storage);
        }
        void runNext() { queue.removeFirst().run(); }
        int persistedCount() { return rows.getFirst().getItems().getFirst().getItemCount(); }
        int runtimeCount() { return inventory.snapshot().counts()[0]; }
    }
}
