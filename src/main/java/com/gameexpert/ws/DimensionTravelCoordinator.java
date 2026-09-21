package com.gameexpert.ws;

import com.gameexpert.api.SessionRegistry;
import static com.gameexpert.ws.SessionAttributes.*;
import com.gameexpert.engine.Difficulty;
import com.gameexpert.engine.WorldEngineManager;
import com.gameexpert.api.PresenceOperations;
import com.gameexpert.state.service.DimensionTravelPersistence;
import com.gameexpert.world.dimension.*;
import com.gameexpert.ws.dto.DimensionWelcome;
import com.gameexpert.ws.dto.WsMessages;
import java.util.Map;
import java.util.concurrent.*;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import tools.jackson.databind.ObjectMapper;

/** 준비→source 정산→travel CAS→송신 lease 교체→ACK→welcome의 단일 제어 lane. */
@Component
public class DimensionTravelCoordinator {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(DimensionTravelCoordinator.class);
    private final DimensionTravelPersistence travel;
    private final DimensionRegistry dimensions;
    private final DimensionProviders providers;
    private final WorldEngineManager engine;
    private final SessionRegistry sessions;
    private final GameTransport broadcaster;
    private final PresenceOperations presence;
    private final WorldSessionLifecycle lifecycle;
    private final ObjectMapper mapper;
    private final Map<String, DimensionSession> connections = new ConcurrentHashMap<>();
    /** How long a returning player waits for the previous connection of the same player to leave. */
    private static final long REJOIN_GRACE_NANOS = TimeUnit.SECONDS.toNanos(5);
    private final Map<String, String> rootOwners = new ConcurrentHashMap<>();
    private final Map<String, Long> preparedTargets = new ConcurrentHashMap<>();
    private final java.util.Queue<WebSocketSession> closing = new ConcurrentLinkedQueue<>();
    private final java.util.Set<String> closingIds = ConcurrentHashMap.newKeySet();
    private final ThreadPoolExecutor worker = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(64), task -> {
                Thread thread = new Thread(task, "dimension-transfers");
                thread.setDaemon(true);
                return thread;
            }) {
        @Override protected void afterExecute(Runnable task, Throwable failure) {
            super.afterExecute(task, failure);
            drainClosedConnections();
        }
    };
    private volatile ConnectionEndpoint handler;

    public DimensionTravelCoordinator(DimensionTravelPersistence travel, DimensionRegistry dimensions,
            DimensionProviders providers, WorldEngineManager engine, SessionRegistry sessions,
            GameTransport broadcaster, PresenceOperations presence, WorldSessionLifecycle lifecycle,
            ObjectMapper mapper) {
        this.travel = travel; this.dimensions = dimensions; this.providers = providers;
        this.engine = engine; this.sessions = sessions; this.broadcaster = broadcaster;
        this.presence = presence; this.lifecycle = lifecycle; this.mapper = mapper;
    }

    public void attachHandler(ConnectionEndpoint handler) { this.handler = handler; }

    public DimensionSession open(WebSocketSession physical) throws Exception {
        long root = (Long) physical.getAttributes().get(ATTR_WORLD_ID);
        long player = (Long) physical.getAttributes().get(ATTR_PLAYER_ID);
        if (travel.isChild(root)) throw new IllegalArgumentException("direct child entry forbidden");
        String claim = root + ":" + player;
        if (!claimRoot(claim, physical.getId())) {
            physical.close(new CloseStatus(4002));
            throw new IllegalStateException("root player already connected");
        }
        try {
            var identity = travel.current(root, player);
            dimensions.requireEnabled(identity.dimension());
            if (!DimensionRegistry.OVERWORLD.equals(identity.dimension())) providers.require(identity.dimension());
            DimensionSession active = new DimensionSession(physical, identity);
            connections.put(physical.getId(), active);
            return active;
        } catch (Exception failure) {
            rootOwners.remove(claim, physical.getId());
            throw failure;
        }
    }

    /**
     * A closed tab is cleaned up on the transfer worker, which saves the leaving player and frees
     * this claim last. A reconnect that arrives first waits for that departure instead of being
     * refused; a second live connection still holds the claim when the grace runs out.
     */
    private boolean claimRoot(String claim, String physicalId) throws InterruptedException {
        long deadline = System.nanoTime() + REJOIN_GRACE_NANOS;
        while (rootOwners.putIfAbsent(claim, physicalId) != null) {
            if (System.nanoTime() - deadline >= 0) return false;
            Thread.sleep(50);
        }
        return true;
    }

    public WebSocketSession current(WebSocketSession session) {
        if (session instanceof DimensionSession dimension) return dimension.current();
        DimensionSession initial = connections.get(session.getId());
        return initial == null ? null : initial.current();
    }

    public Object welcome(WebSocketSession session, WsMessages.Welcome welcome) {
        if (!(session instanceof DimensionSession dimension)) return welcome;
        return new DimensionWelcome(welcome, dimension.identity(),
                dimensions.requireEnabled(dimension.identity().dimension()).environment());
    }

    /** true means consumed/withheld. No normal handler observes a pending target generation. */
    public boolean consumeBarrier(WebSocketSession session, TextMessage message) {
        if (!(session instanceof DimensionSession dimension)) return false;
        // The welcome writer holds this same monitor through the readiness publication.
        synchronized (dimension) {
            if (dimension.inputReady()) return false;
        }
        try {
            var node = mapper.readTree(message.getPayload());
            var revision = node == null ? null : node.get("travelRevision");
            if (node != null && "dimensionReady".equals(node.path("type").asString())
                    && revision != null && revision.isIntegralNumber() && revision.canConvertToLong()
                    && dimension.acknowledge(revision.asLong())) {
                submit(() -> {
                    try {
                        if (!com.gameexpert.cluster.ClusterRuntime.establishAuthorityDimension(dimension)) {
                            handler.afterConnectionEstablished(dimension);
                        }
                    }
                    catch (Exception failure) {
                        log.warn("dimension target welcome failed: world={}", dimension.identity().runtimeWorldId(), failure);
                        closeActive(dimension);
                    } finally { releasePreparedTarget(dimension); }
                }, () -> closeActive(dimension));
            }
        } catch (Exception invalid) {
            // Invalid/stale ACK cannot release the authority barrier.
            log.debug("invalid dimension acknowledgment", invalid);
        }
        return true;
    }

    public void request(PortalTravelRequest request) {
        SessionRegistry.Entry entry = sessions.get(request.worldId(), request.nickname());
        if (entry == null || !entry.connectionId().equals(request.connectionId())
                || !(entry.session() instanceof DimensionSession source)
                || !source.beginTransfer()) return;
        submit(() -> transfer(source, request), () -> {
            source.abortTransfer();
            broadcaster.sendTo(source, new WsMessages.Error("DIMENSION_BUSY"));
        });
    }

    private void transfer(DimensionSession source, PortalTravelRequest request) {
        long target = 0;
        boolean prepared = false, departed = false, committed = false, commitAttempted = false;
        String nickname = request.nickname();
        long player = (Long) source.getAttributes().get(ATTR_PLAYER_ID);
        var before = source.identity();
        try {
            var destination = dimensions.destination(before.dimension(), request.portalBlock())
                    .orElseThrow(() -> new IllegalStateException("portal destination unavailable"));
            if (request.respawn() && !"void_end".equals(before.dimension()))
                throw new IllegalStateException("respawn source must be End");
            double[] origin = request.respawn() ? null : engine.dimensionReturnOrigin(before.runtimeWorldId(), nickname,
                    source.getId(), request.portalBlock());
            double[] arrival;
            if (DimensionRegistry.OVERWORLD.equals(destination.key())) {
                target = before.rootWorldId();
                arrival = travel.returnPose(before.rootWorldId(), player);
            } else {
                providers.require(destination.key());
                target = travel.ensureChild(before.rootWorldId(), destination.key()).getId();
                arrival = destination.arrival().pose();
            }
            int seed = (Integer) source.getAttributes().get(ATTR_WORLD_SEED);
            Difficulty difficulty = Difficulty.orDefault((Difficulty) source.getAttributes().get(ATTR_WORLD_DIFFICULTY));
            com.gameexpert.engine.PlayerTickState outgoing;
            com.gameexpert.engine.RespawnRules.Spawn respawnSpawn = null;
            if (request.respawn()) {
                outgoing = engine.departForRespawn(before.runtimeWorldId(), nickname, source.getId());
                departed = true;
                respawnSpawn = engine.prepareDimensionRespawnTarget(target, seed, difficulty, outgoing);
                arrival = new double[] {respawnSpawn.x(), respawnSpawn.y(), respawnSpawn.z(), outgoing.yaw(), outgoing.pitch()};
                prepared = true;
            } else {
                engine.prepareDimensionTarget(target, seed, difficulty, arrival);
                prepared = true;
                if (!source.isOpen()) throw new IllegalStateException("source connection closed before commit");
                outgoing = engine.departForDimension(before.runtimeWorldId(), nickname, source.getId(), request.portalBlock());
                departed = true;
            }
            if (!source.isOpen()) throw new IllegalStateException("source connection closed before commit");
            commitAttempted = true;
            var after = request.respawn()
                    ? travel.commitRespawn(player, before, outgoing.inventory().revision(), arrival, respawnSpawn.bed())
                    : travel.commit(player, before, outgoing.inventory().revision(), target, arrival, origin);
            committed = true;
            TextMessage envelope = new TextMessage(mapper.writeValueAsString(Map.of(
                    "type", "dimensionTransition", "rootWorldId", after.rootWorldId(),
                    "dimension", after.dimension(), "travelRevision", after.travelRevision())));
            DimensionSession next = source.transition(after, envelope);
            preparedTargets.put(next.getId(), target);
            prepared = false; // matching ACK/close now owns the retained target pin.
            retireSource(source, nickname);
            engine.completeDimensionDeparture(before.runtimeWorldId(), nickname);
        } catch (Exception failure) {
            if (commitAttempted && !committed) {
                try {
                    var recovered = travel.recoverAfterCommitFailure(before.rootWorldId(), player);
                    if (recovered == null) throw new IllegalStateException("travel recovery missing");
                    if (recovered.travelRevision() == before.travelRevision() + 1
                            && recovered.runtimeWorldId() == target) committed = true;
                    else if (recovered.travelRevision() != before.travelRevision()
                            || recovered.runtimeWorldId() != before.runtimeWorldId()) {
                        throw new IllegalStateException("unexpected durable travel generation");
                    }
                } catch (Exception unknown) {
                    failure.addSuppressed(unknown);
                    // Both possible DB outcomes already contain the same durable inventory.
                    // Close; a fresh connection must read the resolved row. Never write source over it.
                    source.retire();
                    retireSource(source, nickname);
                    engine.completeDimensionDeparture(before.runtimeWorldId(), nickname);
                    closeActive(source.current());
                    log.error("dimension commit outcome requires reconnect recovery", failure);
                    return;
                }
            }
            log.warn("dimension transition failed: source={} target={} committed={}",
                    before.runtimeWorldId(), target, committed, failure);
            if (committed) {
                source.retire();
                // Durable target wins even if envelope delivery failed. Never restore source.
                retireSource(source, nickname);
                engine.completeDimensionDeparture(before.runtimeWorldId(), nickname);
                closeActive(source.current());
            } else {
                if (departed) engine.restoreDimensionDeparture(before.runtimeWorldId(), nickname, source.getId());
                source.abortTransfer();
                broadcaster.sendTo(source, new WsMessages.Error("DIMENSION_PREPARATION_FAILED"));
            }
        } finally {
            if (prepared) engine.releaseDimensionTarget(target);
        }
    }

    private void retireSource(DimensionSession source, String nickname) {
        synchronized (source) {
            SessionRegistry.Entry removed = sessions.remove(source.identity().runtimeWorldId(), nickname, source);
            broadcaster.forget(source);
            if (removed != null) {
                try {
                    if (!com.gameexpert.cluster.ClusterIdentity.isAuthority(source)) {
                        presence.leave(source.identity().runtimeWorldId(), source.getId());
                    }
                }
                catch (RuntimeException failure) { log.warn("dimension source presence release failed", failure); }
                broadcaster.broadcast(source.identity().runtimeWorldId(), new WsMessages.PlayerLeave(nickname));
            }
        }
    }

    public void close(WebSocketSession physical) {
        String id = physical instanceof DimensionSession lease ? lease.getDelegate().getId() : physical.getId();
        if (!closingIds.add(id)) return;
        closing.add(physical);
        // A full work queue already has a drain scheduled by afterExecute. Never discard a close.
        submit(() -> { }, () -> { });
    }

    private void drainClosedConnections() {
        WebSocketSession physical;
        while ((physical = closing.poll()) != null) {
            String physicalId = physical instanceof DimensionSession lease ? lease.getDelegate().getId() : physical.getId();
            try {
                DimensionSession initial = physical instanceof DimensionSession value ? value
                        : connections.get(physicalId);
                if (initial == null) continue;
                DimensionSession active = initial.current();
                lifecycle.release(active);
                releasePreparedTarget(active);
                connections.remove(physicalId);
                long player = (Long) active.getAttributes().get(ATTR_PLAYER_ID);
                rootOwners.remove(active.identity().rootWorldId() + ":" + player, physicalId);
            } catch (RuntimeException failure) {
                log.error("dimension connection cleanup failed: session={}", physicalId, failure);
            } finally { closingIds.remove(physicalId); }
        }
    }

    private void releasePreparedTarget(DimensionSession session) {
        Long world = preparedTargets.remove(session.getId());
        if (world != null) engine.releaseDimensionTarget(world);
    }

    private void closeActive(DimensionSession session) {
        try { session.close(CloseStatus.SERVER_ERROR); }
        catch (Exception failure) { log.debug("dimension socket close failed", failure); }
    }

    private void submit(Runnable task, Runnable rejected) {
        try { worker.execute(task); }
        catch (RejectedExecutionException unavailable) { rejected.run(); }
    }

    @jakarta.annotation.PreDestroy
    void shutdown() { worker.shutdown(); }
}
