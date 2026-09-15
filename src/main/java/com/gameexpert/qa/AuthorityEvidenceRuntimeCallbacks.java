package com.gameexpert.qa;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Component;

/** Routes the single armed schema-2 probe through real Spring authority boundaries. */
@Component
public final class AuthorityEvidenceRuntimeCallbacks {
    private static final int SNOWY_X = -54;
    private static final int SNOWY_Z = 222;
    private static final int JUNGLE_X = 1;
    private static final int JUNGLE_Z = 186;

    private final AuthorityEvidenceCoordinator coordinator;
    private final Map<Key, Route> routes = new HashMap<>();

    private static boolean probeChunk(int chunkX, int chunkZ) {
        return (chunkX == SNOWY_X && chunkZ == SNOWY_Z) || (chunkX == JUNGLE_X && chunkZ == JUNGLE_Z);
    }

    public AuthorityEvidenceRuntimeCallbacks(AuthorityEvidenceCoordinator coordinator) {
        this.coordinator = Objects.requireNonNull(coordinator, "authority evidence coordinator");
    }

    public synchronized void sessionJoined(long worldId, String nickname,
            String connectionIdentity) {
        Key key = new Key(worldId, nickname);
        Route existing = routes.get(key);
        if (existing != null) {
            if (existing.phase == Phase.REJOIN && existing.firstConnectionIdentity == null
                    && coordinator.firstConnectionMatches(worldId, nickname, connectionIdentity)) return;
            if (existing.phase != Phase.REJOIN
                    || (existing.firstConnectionIdentity != null
                            && existing.firstConnectionIdentity.equals(connectionIdentity))) return;
            coordinator.sessionJoinedIfArmed(worldId, nickname, connectionIdentity)
                    .ifPresent(session -> {
                        existing.session = session;
                        AuthorityEvidenceCoordinator.CallbackPhase callbackPhase =
                                coordinator.callbackPhase(session);
                        existing.firstConnectionIdentity =
                                firstConnectionIdentity(callbackPhase, connectionIdentity);
                        existing.phase = phase(callbackPhase);
                    });
            return;
        }
        coordinator.sessionJoinedIfArmed(worldId, nickname, connectionIdentity)
                .ifPresent(session -> {
                    AuthorityEvidenceCoordinator.CallbackPhase callbackPhase =
                            coordinator.callbackPhase(session);
                    routes.put(key, new Route(key,
                            firstConnectionIdentity(callbackPhase, connectionIdentity), session,
                            phase(callbackPhase)));
                });
    }

    public synchronized void chunkActivated(long worldId, int chunkX, int chunkZ) {
        Route route = uniqueRoute(worldId,
                (chunkX == SNOWY_X && chunkZ == SNOWY_Z)
                        ? Phase.FIRST_CHUNK : (chunkX == JUNGLE_X && chunkZ == JUNGLE_Z)
                                ? Phase.SECOND_CHUNK : null);
        if (route == null) {
            Phase expected = (chunkX == SNOWY_X && chunkZ == SNOWY_Z)
                    ? Phase.FIRST_CHUNK : (chunkX == JUNGLE_X && chunkZ == JUNGLE_Z)
                            ? Phase.SECOND_CHUNK : null;
            route = recoverRoute(worldId, expected);
        }
        if (route != null) {
            coordinator.chunkActivated(route.session, chunkX, chunkZ);
            route.phase = route.phase == Phase.FIRST_CHUNK
                    ? Phase.FIRST_MOVE : Phase.SECOND_MOVE;
            return;
        }
        Route reconnect = uniqueRoute(worldId,
                chunkX == SNOWY_X && chunkZ == SNOWY_Z ? Phase.RECONNECTED_CHUNK : null);
        if (reconnect == null && chunkX == SNOWY_X && chunkZ == SNOWY_Z) {
            reconnect = recoverRoute(worldId, Phase.RECONNECTED_CHUNK);
        }
        if (reconnect != null) {
            coordinator.chunkActivated(reconnect.session, chunkX, chunkZ);
            routes.remove(reconnect.key);
        }
    }

    /** Identity-bound adapter for runtimes that can carry the connection on chunk callbacks. */
    public synchronized void chunkActivated(long worldId, String nickname,
            String connectionIdentity, int chunkX, int chunkZ) {
        Route route = identityRoute(worldId, nickname, connectionIdentity,
                (chunkX == SNOWY_X && chunkZ == SNOWY_Z)
                        ? Phase.FIRST_CHUNK : (chunkX == JUNGLE_X && chunkZ == JUNGLE_Z)
                                ? Phase.SECOND_CHUNK : null);
        if (route == null) {
            if (chunkX != SNOWY_X || chunkZ != SNOWY_Z) return;
            route = identityRoute(worldId, nickname, connectionIdentity,
                    Phase.RECONNECTED_CHUNK);
        }
        if (route == null) return;
        coordinator.chunkActivated(route.session, connectionIdentity, chunkX, chunkZ);
        if (route.phase == Phase.RECONNECTED_CHUNK) routes.remove(route.key);
        else route.phase = route.phase == Phase.FIRST_CHUNK
                ? Phase.FIRST_MOVE : Phase.SECOND_MOVE;
    }

    /** Same identity-bound adapter with the identity in the final argument position. */
    public synchronized void chunkActivated(long worldId, String nickname,
            int chunkX, int chunkZ, String connectionIdentity) {
        chunkActivated(worldId, nickname, connectionIdentity, chunkX, chunkZ);
    }

    public synchronized void playerMoved(long worldId, String nickname,
            int chunkX, int chunkZ) {
        // Every applied move reaches here on the world tick thread. Only the two probe chunks can
        // advance a route, so any other chunk returns before route recovery reads the journal.
        if (!probeChunk(chunkX, chunkZ)) return;
        Route route = routes.get(new Key(worldId, nickname));
        if (route == null) route = recoverNamedRoute(worldId, nickname);
        if (route == null) return;
        if (route.phase == Phase.FIRST_MOVE
                && chunkX == SNOWY_X && chunkZ == SNOWY_Z) {
            coordinator.playerMoved(route.session, chunkX, chunkZ);
            route.phase = Phase.SECOND_CHUNK;
        } else if (route.phase == Phase.SECOND_MOVE
                && chunkX == JUNGLE_X && chunkZ == JUNGLE_Z) {
            coordinator.playerMoved(route.session, chunkX, chunkZ);
            route.phase = Phase.EVICTION;
        }
    }

    /** Identity-bound adapter for movement callbacks. */
    public synchronized void playerMoved(long worldId, String nickname,
            String connectionIdentity, int chunkX, int chunkZ) {
        if (!probeChunk(chunkX, chunkZ)) return;
        Route route = identityRoute(worldId, nickname, connectionIdentity, null);
        if (route == null) return;
        if (route.phase == Phase.FIRST_MOVE
                && chunkX == SNOWY_X && chunkZ == SNOWY_Z) {
            coordinator.playerMoved(route.session, connectionIdentity, chunkX, chunkZ);
            route.phase = Phase.SECOND_CHUNK;
        } else if (route.phase == Phase.SECOND_MOVE
                && chunkX == JUNGLE_X && chunkZ == JUNGLE_Z) {
            coordinator.playerMoved(route.session, connectionIdentity, chunkX, chunkZ);
            route.phase = Phase.EVICTION;
        }
    }

    /** Same identity-bound movement adapter with the identity in the final argument position. */
    public synchronized void playerMoved(long worldId, String nickname,
            int chunkX, int chunkZ, String connectionIdentity) {
        playerMoved(worldId, nickname, connectionIdentity, chunkX, chunkZ);
    }

    public synchronized void capacityChunkEvicted(long worldId, int chunkX, int chunkZ) {
        if (chunkX != SNOWY_X || chunkZ != SNOWY_Z) return;
        Route route = uniqueRoute(worldId, Phase.EVICTION);
        if (route == null) route = recoverRoute(worldId, Phase.EVICTION);
        if (route == null) return;
        coordinator.capacityChunkEvicted(route.session, chunkX, chunkZ);
        route.phase = Phase.LEAVE;
    }

    /** Identity-bound adapter for capacity eviction callbacks. */
    public synchronized void capacityChunkEvicted(long worldId, String nickname,
            String connectionIdentity, int chunkX, int chunkZ) {
        if (chunkX != SNOWY_X || chunkZ != SNOWY_Z) return;
        Route route = identityRoute(worldId, nickname, connectionIdentity, Phase.EVICTION);
        if (route == null) return;
        coordinator.capacityChunkEvicted(route.session, connectionIdentity, chunkX, chunkZ);
        route.phase = Phase.LEAVE;
    }

    /** Same identity-bound eviction adapter with the identity in the final argument position. */
    public synchronized void capacityChunkEvicted(long worldId, String nickname,
            int chunkX, int chunkZ, String connectionIdentity) {
        capacityChunkEvicted(worldId, nickname, connectionIdentity, chunkX, chunkZ);
    }

    public synchronized void sessionLeft(long worldId, String nickname,
            String connectionIdentity) {
        Route route = routes.get(new Key(worldId, nickname));
        if (route == null) route = recoverNamedRoute(worldId, nickname);
        if (route == null || route.phase != Phase.LEAVE
                || (route.firstConnectionIdentity != null
                        && !route.firstConnectionIdentity.equals(connectionIdentity))) return;
        if (route.firstConnectionIdentity == null
                && !coordinator.activeConnectionMatches(route.session, connectionIdentity)) return;
        coordinator.sessionLeft(route.session, connectionIdentity);
        route.session = null;
        route.phase = Phase.REJOIN;
    }

    private Route recoverNamedRoute(long worldId, String nickname) {
        return coordinator.sessionForCallbacks(worldId, nickname)
                .map(session -> {
                    AuthorityEvidenceCoordinator.CallbackPhase callbackPhase =
                            coordinator.callbackPhase(session);
                    Phase recoveredPhase = phase(callbackPhase);
                    if (recoveredPhase == null) return null;
                    Key key = new Key(worldId, nickname);
                    Route recovered = new Route(key, null, session, recoveredPhase);
                    routes.put(key, recovered);
                    return recovered;
                }).orElse(null);
    }

    private Route identityRoute(long worldId, String nickname, String connectionIdentity,
            Phase expected) {
        if (connectionIdentity == null || nickname == null) return null;
        Route route = routes.get(new Key(worldId, nickname));
        if (route == null) route = recoverNamedRoute(worldId, nickname);
        if (route == null || route.session == null
                || (expected != null && route.phase != expected)) return null;
        try {
            return coordinator.activeConnectionMatches(route.session, connectionIdentity)
                    ? route : null;
        } catch (IllegalStateException stale) {
            return null;
        }
    }

    private Route recoverRoute(long worldId, Phase expected) {
        if (expected == null) return null;
        int expectedEvent = switch (expected) {
            case FIRST_CHUNK -> 2;
            case SECOND_CHUNK -> 4;
            case EVICTION -> 6;
            case RECONNECTED_CHUNK -> 9;
            default -> -1;
        };
        if (expectedEvent < 0) return null;
        return coordinator.sessionForCallback(worldId, expectedEvent)
                .map(session -> {
                    Key key = new Key(worldId, coordinator.callbackNickname(session));
                    Route recovered = new Route(key, null, session, expected);
                    routes.put(key, recovered);
                    return recovered;
                }).orElse(null);
    }

    private static String firstConnectionIdentity(
            AuthorityEvidenceCoordinator.CallbackPhase callbackPhase, String currentIdentity) {
        return callbackPhase == AuthorityEvidenceCoordinator.CallbackPhase.RECONNECTED_CHUNK
                ? null : currentIdentity;
    }

    private static Phase phase(AuthorityEvidenceCoordinator.CallbackPhase value) {
        if (value == null) return null;
        return switch (value) {
            case FIRST_CHUNK -> Phase.FIRST_CHUNK;
            case FIRST_MOVE -> Phase.FIRST_MOVE;
            case SECOND_CHUNK -> Phase.SECOND_CHUNK;
            case SECOND_MOVE -> Phase.SECOND_MOVE;
            case EVICTION -> Phase.EVICTION;
            case LEAVE -> Phase.LEAVE;
            case RECONNECTED_CHUNK -> Phase.RECONNECTED_CHUNK;
        };
    }

    private Route uniqueRoute(long worldId, Phase phase) {
        if (phase == null) return null;
        Route found = null;
        for (Route route : routes.values()) {
            if (route.key.worldId != worldId || route.phase != phase) continue;
            if (found != null) return null;
            found = route;
        }
        return found;
    }

    private enum Phase {
        FIRST_CHUNK,
        FIRST_MOVE,
        SECOND_CHUNK,
        SECOND_MOVE,
        EVICTION,
        LEAVE,
        REJOIN,
        RECONNECTED_CHUNK
    }

    private static final class Key {
        private final long worldId;
        private final String nickname;

        private Key(long worldId, String nickname) {
            this.worldId = worldId;
            this.nickname = nickname;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Key value)) return false;
            return worldId == value.worldId && Objects.equals(nickname, value.nickname);
        }

        @Override
        public int hashCode() {
            return Objects.hash(worldId, nickname);
        }
    }

    private static final class Route {
        private final Key key;
        private String firstConnectionIdentity;
        private AuthorityEvidenceCoordinator.SessionHandle session;
        private Phase phase;

        private Route(Key key, String firstConnectionIdentity,
                AuthorityEvidenceCoordinator.SessionHandle session, Phase phase) {
            this.key = key;
            this.firstConnectionIdentity = firstConnectionIdentity;
            this.session = session;
            this.phase = phase;
        }
    }
}
