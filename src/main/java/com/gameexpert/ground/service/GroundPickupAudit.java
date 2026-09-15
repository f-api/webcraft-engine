package com.gameexpert.ground.service;

import com.gameexpert.ground.dto.GroundMutationCommand;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Temporary, opt-in observation only; never participates in a settlement or persisted identity. */
public final class GroundPickupAudit {
    public static final String LOGGER_NAME = "gameexpert.audit.pickup";
    public static final String SCOPE_PROPERTY = "gameexpert.audit.pickup.scope";
    public static final int MAX_EVENTS_PER_ITEM = 64;
    private static final Logger LOG = LoggerFactory.getLogger(LOGGER_NAME);
    private static volatile Scope current = new Scope("", -1L, -1L, -1L);

    private GroundPickupAudit() { }

    /** Both DEBUG and an explicit world:item[,item] numeric scope are required. */
    public static boolean enabled(long worldId, long itemId) {
        return LOG.isDebugEnabled() && scope().matches(worldId, itemId);
    }

    public static boolean enabled(GroundMutationCommand command) {
        if (!LOG.isDebugEnabled() || command == null
                || command.kind() != GroundMutationCommand.Kind.PLAYER_PICKUP) return false;
        Scope scope = scope();
        for (long id : command.removedItemIds()) if (scope.matches(command.worldId(), id)) return true;
        for (var item : command.insertedItems()) if (scope.matches(command.worldId(), item.entityId())) return true;
        return false;
    }

    /** Callers supply constant phase/reason/field labels and numeric facts only, never payloads. */
    public static void event(long worldId, long itemId, String phase, String reason,
            String fields, Number... values) {
        if (!LOG.isDebugEnabled()) return;
        Scope scope = scope();
        if (!scope.accept(worldId, itemId, phase, reason)) return;
        LOG.debug("phase={} reason={} world={} item={} fields={} values={}",
                phase, reason, worldId, itemId, fields, Arrays.toString(values));
    }

    public static void event(GroundMutationCommand command, String phase, String reason,
            String fields, Number... values) {
        if (!enabled(command)) return;
        for (long id : command.removedItemIds()) event(command.worldId(), id, phase, reason, fields, values);
        for (var item : command.insertedItems()) event(command.worldId(), item.entityId(), phase, reason, fields, values);
    }

    private static Scope scope() {
        String configured = System.getProperty(SCOPE_PROPERTY, "");
        Scope snapshot = current;
        if (snapshot.specification.equals(configured)) return snapshot;
        synchronized (GroundPickupAudit.class) {
            if (!current.specification.equals(configured)) current = parse(configured);
            return current;
        }
    }

    private static Scope parse(String specification) {
        try {
            if (!specification.matches("[1-9][0-9]*:[1-9][0-9]*(,[1-9][0-9]*)?"))
                return new Scope(specification, -1L, -1L, -1L);
            String[] parts = specification.split(":", -1), ids = parts[1].split(",", -1);
            long world = Long.parseLong(parts[0]), first = Long.parseLong(ids[0]);
            long second = ids.length == 2 ? Long.parseLong(ids[1]) : -1L;
            if (world == Long.MAX_VALUE || first == Long.MAX_VALUE || second == Long.MAX_VALUE
                    || first == second) return new Scope(specification, -1L, -1L, -1L);
            return new Scope(specification, world, first, second);
        } catch (NumberFormatException invalid) {
            return new Scope(specification, -1L, -1L, -1L);
        }
    }

    private static final class Scope {
        private final String specification;
        private final long worldId;
        private final long firstId;
        private final long secondId;
        private final Map<Long, Map<String, String>> reasons = new HashMap<>();
        private final Map<Long, Integer> counts = new HashMap<>();

        private Scope(String specification, long worldId, long firstId, long secondId) {
            this.specification = specification;
            this.worldId = worldId;
            this.firstId = firstId;
            this.secondId = secondId;
        }

        private boolean matches(long worldId, long itemId) {
            return this.worldId > 0 && this.worldId == worldId && itemId > 0
                    && (itemId == firstId || itemId == secondId);
        }

        private synchronized boolean accept(long worldId, long itemId, String phase, String reason) {
            if (!matches(worldId, itemId) || counts.getOrDefault(itemId, 0) >= MAX_EVENTS_PER_ITEM) return false;
            Map<String, String> byPhase = reasons.computeIfAbsent(itemId, ignored -> new HashMap<>());
            if (reason.equals(byPhase.get(phase))) return false;
            byPhase.put(phase, reason);
            counts.merge(itemId, 1, Integer::sum);
            return true;
        }
    }
}
