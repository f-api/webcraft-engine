package com.gameexpert.world.service;

import com.gameexpert.cluster.WorldAuthority;
import com.gameexpert.api.PresenceOperations;
import com.gameexpert.common.ConflictException;
import com.gameexpert.engine.WorldEngineManager;
import com.gameexpert.world.repository.WorldDimensionRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Validate in the existing service, release its SQL locks, then drain before deleting. */
@Component
@RequiredArgsConstructor
public final class WorldDeletionCoordinator {
    private static final ThreadLocal<Scope> CURRENT = new ThreadLocal<>();
    private final WorldEngineManager engines;
    private final WorldDimensionRepository dimensions;
    private final PresenceOperations presence;

    @FunctionalInterface
    public interface Operation {
        Object proceed() throws Throwable;
    }

    public Object delete(long root, Operation operation) throws Throwable {
        if (TransactionSynchronizationManager.isActualTransactionActive() || CURRENT.get() != null) {
            throw new IllegalStateException("World deletion must start outside an existing transaction");
        }
        Scope scope = new Scope(root);
        CURRENT.set(scope);
        try {
            try {
                return operation.proceed();
            } catch (Validated probe) {
                if (probe != scope.validated) throw probe;
            }
            if (TransactionSynchronizationManager.isActualTransactionActive()) {
                throw new IllegalStateException("World deletion validation did not release its transaction");
            }
            WorldAuthority.requireRuntime(root);
            Set<Long> worlds = new LinkedHashSet<>();
            collectWorlds(root, worlds);
            if (worlds.stream().anyMatch(world -> presence.onlineCount(world) > 0)) {
                throw new ConflictException("WORLD_IN_USE");
            }
            for (Long world : worlds) reserve(world, scope);
            scope.probing = false;
            WorldAuthority.requireRuntime(root);
            return operation.proceed();
        } finally {
            CURRENT.remove();
            for (int index = scope.reserved.size() - 1; index >= 0; index--) {
                engines.endWorldDeletion(scope.reserved.get(index));
            }
        }
    }

    private void collectWorlds(long world, Set<Long> worlds) {
        if (!worlds.add(world)) return;
        dimensions.findChildIdsByRootId(world).forEach(child -> collectWorlds(child, worlds));
    }

    private void reserve(long world, Scope scope) {
        if (presence.onlineCount(world) > 0 || !engines.beginWorldDeletion(world)) {
            throw new ConflictException("WORLD_IN_USE");
        }
        scope.reserved.add(world);
    }

    /** Called only after the service has checked authorization and conditional identity. */
    public static boolean beforeDelete(long world) {
        Scope scope = CURRENT.get();
        if (scope == null) return false;
        if (world != scope.root) throw new IllegalStateException("Unexpected coordinated deletion root");
        if (scope.probing) throw scope.validated;
        requireReserved(world);
        WorldAuthority.requireRuntime(world);
        return true;
    }

    public static boolean hasReservation(long world) {
        Scope scope = CURRENT.get();
        return scope != null && !scope.probing && scope.reserved.contains(world);
    }

    /** Reject a dimension added after draining instead of waiting under the deletion's SQL lock. */
    public static void requireChildrenReserved(long world, List<Long> children) {
        Scope scope = CURRENT.get();
        if (scope == null || scope.probing) return;
        requireReserved(world);
        children.forEach(WorldDeletionCoordinator::requireReserved);
    }

    private static void requireReserved(long world) {
        Scope scope = CURRENT.get();
        if (scope == null || !scope.reserved.contains(world)) {
            throw new ConflictException("WORLD_IN_USE");
        }
    }

    private static final class Scope {
        final long root;
        final Validated validated = new Validated();
        final List<Long> reserved = new ArrayList<>();
        boolean probing = true;
        Scope(long root) { this.root = root; }
    }

    private static final class Validated extends RuntimeException {
        private static final long serialVersionUID = 1L;
        Validated() { super(null, null, false, false); }
    }
}
