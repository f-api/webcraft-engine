package com.gameexpert.falling.service;

import com.gameexpert.block.entity.WorldBlockDiff;
import com.gameexpert.block.persistence.BlockDiffBuffer;
import com.gameexpert.block.persistence.BlockDiffFlusher;
import com.gameexpert.engine.BlockPos;
import com.gameexpert.engine.persistence.tick.FinalCarrierTickScheduler.BlockMutation;
import com.gameexpert.falling.dto.RuntimeSpeleothemFall;
import com.gameexpert.falling.entity.*;
import com.gameexpert.api.persistence.WorldAccess;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@RequiredArgsConstructor
public class RuntimeFallingPersistence {
    public static final class StaleBeforeState extends IllegalStateException {
        public StaleBeforeState() { super("runtime fall before-state is stale"); }
    }

    private final EntityManager entityManager;
    private final TransactionTemplate transactions;
    private final BlockDiffFlusher blockDiffFlusher;

    public List<RuntimeSpeleothemFall> load(Long worldId) {
        return transactions.execute(status -> entityManager.createQuery(
                "select f from WorldRuntimeSpeleothemFall f where f.world.id = :world and f.consumed = false order by f.dueTick, f.id",
                WorldRuntimeSpeleothemFall.class).setParameter("world", worldId).getResultList().stream()
                .map(WorldRuntimeSpeleothemFall::snapshot).toList());
    }

    public void checkpoint(Long worldId, String identity, List<RuntimeSpeleothemFall> admissions,
            Runnable sourceCheckpoint) {
        transactions.executeWithoutResult(status -> {
            if (entityManager.find(WorldRuntimeFallCheckpoint.class, identity) != null) return;
            WorldAccess world = entityManager.getReference(WorldAccess.class, worldId);
            sourceCheckpoint.run();
            for (var admission : admissions) {
                var pending = entityManager.createQuery(
                        "select f from WorldRuntimeSpeleothemFall f where f.world.id = :world and f.x = :x and f.y = :y and f.z = :z and f.blockId = :block and f.consumed = false",
                        WorldRuntimeSpeleothemFall.class).setParameter("world", worldId)
                        .setParameter("x", admission.x()).setParameter("y", admission.y())
                        .setParameter("z", admission.z()).setParameter("block", admission.blockId()).getResultList();
                if (pending.isEmpty()) entityManager.persist(new WorldRuntimeSpeleothemFall(world, admission));
                else if (!pending.getFirst().snapshot().equals(admission)) {
                    throw new IllegalStateException("runtime fall admission lost first-winner ownership");
                }
            }
            entityManager.persist(new WorldRuntimeFallCheckpoint(identity, world));
            entityManager.flush();
        });
    }

    /** Returning a stored plan is replay, never a new live-world computation. */
    public List<BlockMutation> settle(Long worldId, RuntimeSpeleothemFall fall, List<BlockMutation> plan) {
        return transactions.execute(status -> {
            var row = entityManager.createQuery(
                    "select f from WorldRuntimeSpeleothemFall f where f.world.id = :world and f.eventKey = :key",
                    WorldRuntimeSpeleothemFall.class).setParameter("world", worldId)
                    .setParameter("key", fall.identity()).setLockMode(LockModeType.PESSIMISTIC_WRITE).getSingleResult();
            if (!row.snapshot().equals(fall)) throw new IllegalStateException("runtime fall identity changed");
            if (row.isConsumed()) return RuntimeSpeleothemFall.decode(row.getSettlementPlan());
            Map<BlockPos, BlockDiffBuffer.Change> changes = new LinkedHashMap<>();
            Map<Integer, WorldBlockDiff> storedByY = new HashMap<>();
            if (!plan.isEmpty()) {
                int minY = plan.stream().mapToInt(BlockMutation::y).min().orElseThrow();
                for (var stored : entityManager.createQuery(
                        "select d from WorldBlockDiff d where d.world.id = :world and d.x = :x and d.y >= :min and d.y <= :max and d.z = :z",
                        WorldBlockDiff.class).setParameter("world", worldId).setParameter("x", fall.x())
                        .setParameter("min", minY).setParameter("max", fall.y()).setParameter("z", fall.z())
                        .setLockMode(LockModeType.PESSIMISTIC_WRITE).getResultList()) {
                    storedByY.put(stored.getY(), stored);
                }
            }
            for (var cell : plan) {
                var before = storedByY.get(cell.y());
                if (before != null && (Short.toUnsignedInt(before.getBlockType()) != cell.beforeBlockId()
                        || Short.toUnsignedInt(before.getBlockState()) != RuntimeSpeleothemFall.compactState(
                                cell.beforeBlockId(), cell.beforeBlockState()))) {
                    throw new StaleBeforeState();
                }
                changes.put(new BlockPos(cell.x(), cell.y(), cell.z()),
                        new BlockDiffBuffer.Change((short) cell.blockId(), (short) cell.blockState()));
            }
            blockDiffFlusher.writeDetached(worldId, changes);
            WorldAccess world = entityManager.getReference(WorldAccess.class, worldId);
            for (var birth : fall.births(plan)) entityManager.persist(new WorldFallingSpeleothem(world, birth));
            row.consume(RuntimeSpeleothemFall.encode(plan));
            entityManager.flush();
            return List.copyOf(plan);
        });
    }
}
