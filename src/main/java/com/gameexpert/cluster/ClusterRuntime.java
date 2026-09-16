package com.gameexpert.cluster;

import static com.gameexpert.ws.SessionAttributes.*;
import com.gameexpert.api.PresenceOperations;
import com.gameexpert.api.SessionRegistry;
import com.gameexpert.engine.Difficulty;
import com.gameexpert.engine.WorldEngineManager;
import com.gameexpert.ws.*;
import com.gameexpert.ws.handler.EngineMessageHandler;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Ordered, bounded engine transport. These Redis mailboxes are NOT student chat Pub/Sub.
 * Every physical connection, including a connection on the owner, gets one separate engine
 * endpoint. Student handlers run once on the edge; game handlers run once on the authority.
 * There is no automatic command retry across a session or authority generation.
 */
@Component
public final class ClusterRuntime implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(ClusterRuntime.class);
    private static final int MAX_PACKET_CHARS = 24 * 1024 * 1024;
    private static final String WIRE = "webcraft.cluster.wire";
    private static final DefaultRedisScript<Long> PUSH = new DefaultRedisScript<>("""
        local n=string.len(ARGV[1])
        if redis.call('LLEN',KEYS[1])>=2048 or tonumber(redis.call('GET',KEYS[2]) or '0')+n>67108864 then return 0 end
        redis.call('RPUSH',KEYS[1],ARGV[1]); redis.call('INCRBY',KEYS[2],n)
        redis.call('EXPIRE',KEYS[1],30); redis.call('EXPIRE',KEYS[2],30)
        return 1
        """, Long.class);
    private static final DefaultRedisScript<String> POP = new DefaultRedisScript<>("""
        local v=redis.call('LPOP',KEYS[1])
        if v then redis.call('DECRBY',KEYS[2],string.len(v)) end
        return v
        """, String.class);
    @com.fasterxml.jackson.annotation.JsonAutoDetect(fieldVisibility = com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.ANY)
    @lombok.Value
    @lombok.experimental.Accessors(fluent = true)
    static class Packet {
        String kind;
        String from;
        String id;
        long root;
        long epoch;
        long sequence;
        long world;
        String payload;
        boolean binary;
        int code;
        @com.fasterxml.jackson.annotation.JsonCreator
        Packet(@com.fasterxml.jackson.annotation.JsonProperty("kind") String kind, @com.fasterxml.jackson.annotation.JsonProperty("from") String from, @com.fasterxml.jackson.annotation.JsonProperty("id") String id, @com.fasterxml.jackson.annotation.JsonProperty("root") long root, @com.fasterxml.jackson.annotation.JsonProperty("epoch") long epoch, @com.fasterxml.jackson.annotation.JsonProperty("sequence") long sequence, @com.fasterxml.jackson.annotation.JsonProperty("world") long world, @com.fasterxml.jackson.annotation.JsonProperty("payload") String payload, @com.fasterxml.jackson.annotation.JsonProperty("binary") boolean binary, @com.fasterxml.jackson.annotation.JsonProperty("code") int code) {
            this.kind = kind;
            this.from = from;
            this.id = id;
            this.root = root;
            this.epoch = epoch;
            this.sequence = sequence;
            this.world = world;
            this.payload = payload;
            this.binary = binary;
            this.code = code;
        }
    }
    @com.fasterxml.jackson.annotation.JsonAutoDetect(fieldVisibility = com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.ANY)
    @lombok.Value
    @lombok.experimental.Accessors(fluent = true)
    static class Identity {
        String nickname;
        long player;
        int seed;
        String difficulty;
        long world;
        @com.fasterxml.jackson.annotation.JsonCreator
        Identity(@com.fasterxml.jackson.annotation.JsonProperty("nickname") String nickname, @com.fasterxml.jackson.annotation.JsonProperty("player") long player, @com.fasterxml.jackson.annotation.JsonProperty("seed") int seed, @com.fasterxml.jackson.annotation.JsonProperty("difficulty") String difficulty, @com.fasterxml.jackson.annotation.JsonProperty("world") long world) {
            this.nickname = nickname;
            this.player = player;
            this.seed = seed;
            this.difficulty = difficulty;
            this.world = world;
        }
    }
    private static volatile ClusterRuntime active;
    private static final ThreadLocal<Boolean> AUTHORITY_DISPATCH = new ThreadLocal<>();
    private final org.springframework.context.ApplicationContext context;
    private final WorldAuthority authority;
    private final AuthoritySessions sessions;
    private final StringRedisTemplate redis;
    private final ObjectMapper json;
    private final ObjectProvider<GameConnectionRuntime> game;
    private final ObjectProvider<GameTransport> transport;
    private final ObjectProvider<WorldEngineManager> engines;
    private final ObjectProvider<ChatCommands> commands;
    private final ObjectProvider<com.gameexpert.world.service.WorldOperations> worldOperations;
    private final ObjectProvider<com.gameexpert.api.persistence.WorldStore> worldStore;
    private final ObjectProvider<org.springframework.transaction.PlatformTransactionManager> transactions;
    private final ConcurrentMap<String, PendingDelete> deletions = new ConcurrentHashMap<>();
    private static final class PendingDelete {
        final WorldAuthority.Owner owner;
        final CompletableFuture<Packet> result = new CompletableFuture<>();
        PendingDelete(WorldAuthority.Owner owner) { this.owner = owner; }
    }
    private final SessionCleanup localCleanup;
    private final PresenceOperations localPresence;
    private final Map<String, EngineMessageHandler> handlers;
    private final ConcurrentMap<String, Edge> physicalEdges = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Edge> wireEdges = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Actor> actors = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Long> tombstones = new ConcurrentHashMap<>();
    private final Set<Long> abandoned = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean stopped = new AtomicBoolean();
    private final ScheduledExecutorService maintenance = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "cluster-session-health"); t.setDaemon(true); return t;
    });
    private volatile Thread reader;
    private volatile boolean started;
    private volatile boolean transportFailed;

    public ClusterRuntime(WorldAuthority authority, AuthoritySessions sessions, StringRedisTemplate redis,
            ObjectMapper json, ObjectProvider<GameConnectionRuntime> game, ObjectProvider<GameTransport> transport,
            ObjectProvider<WorldEngineManager> engines, ObjectProvider<ChatCommands> commands,
            @Qualifier("connectionCleanup") SessionCleanup localCleanup,
            @Qualifier("presenceService") PresenceOperations localPresence, List<EngineMessageHandler> handlers,
            ObjectProvider<com.gameexpert.world.service.WorldOperations> worldOperations,
            ObjectProvider<com.gameexpert.api.persistence.WorldStore> worldStore,
            ObjectProvider<org.springframework.transaction.PlatformTransactionManager> transactions,
            org.springframework.context.ApplicationContext context) {
        this.context=context;
        this.authority=authority; this.sessions=sessions; this.redis=redis; this.json=json;
        this.game=game; this.transport=transport; this.engines=engines; this.commands=commands;
        this.localCleanup=localCleanup; this.localPresence=localPresence;
        this.worldOperations=worldOperations; this.worldStore=worldStore; this.transactions=transactions;
        Map<String, EngineMessageHandler> index = new HashMap<>();
        for (EngineMessageHandler handler : handlers) for (String type : handler.supportedTypes()) {
            if (index.putIfAbsent(type, handler) != null) throw new IllegalStateException("Duplicate engine handler: " + type);
        }
        this.handlers=Map.copyOf(index);
    }
    public static ClusterRuntime current() {
        ClusterRuntime value=active;
        return value != null && value.started && !value.stopped.get() ? value : null;
    }
    @EventListener(ApplicationReadyEvent.class)
    public synchronized void start() {
        if (!authority.enabled() || started || stopped.get()) return;
        redis.opsForValue().set(alive(ClusterIdentity.NODE), "1", Duration.ofSeconds(8));
        started=true; active=this;
        reader=Thread.ofVirtual().name("cluster-engine-inbox").start(this::readLoop);
        maintenance.scheduleWithFixedDelay(this::maintain, 1, 1, TimeUnit.SECONDS);
    }
    private String inbox(String node) { return authority.namespace() + ":inbox:" + node; }
    private String alive(String node) { return authority.namespace() + ":alive:" + node; }
    private void push(String node, Packet packet) {
        String encoded=json.writeValueAsString(packet);
        if (encoded.length() > MAX_PACKET_CHARS) throw new IllegalStateException("Engine packet too large");
        String queue=inbox(node);
        if (!Long.valueOf(1).equals(redis.execute(PUSH, List.of(queue, queue + ":bytes"), encoded))) {
            throw new IllegalStateException("Engine mailbox capacity exceeded");
        }
    }
    private void readLoop() {
        String queue=inbox(ClusterIdentity.NODE);
        while (!stopped.get()) {
            try {
                String raw=redis.execute(POP, List.of(queue, queue + ":bytes"));
                if (raw == null) { Thread.sleep(10); continue; }
                if (raw.length() > MAX_PACKET_CHARS) throw new IllegalArgumentException("Oversized engine envelope");
                Packet packet=json.readValue(raw, Packet.class);
                receivePacket(packet);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt(); return;
            } catch (Exception failure) {
                fault(failure);
                try { Thread.sleep(250); } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); return; }
            }
        }
    }
    private void receivePacket(Packet packet) {
        if (packet.id()==null || packet.from()==null || packet.root()<=0 || packet.epoch()<=0) return;
        if (packet.kind().equals("deleteResult")) {
            PendingDelete pending = deletions.get(packet.id());
            if (pending != null && pending.owner.node().equals(packet.from())
                    && pending.owner.root() == packet.root() && pending.owner.epoch() == packet.epoch()) {
                pending.result.complete(packet);
            }
            return;
        }
        if (packet.kind().equals("deleteWorld")) {
            Thread.ofVirtual().name("cluster-world-delete").start(() -> deleteOnAuthority(packet));
            return;
        }
        if (Set.of("opened", "frame", "closed", "result").contains(packet.kind())) {
            Edge edge=wireEdges.get(packet.id());
            if (edge == null || !edge.owner.node().equals(packet.from()) || edge.owner.root()!=packet.root()
                    || edge.owner.epoch()!=packet.epoch()) return;
            if (!edge.mailbox.offer(() -> edge.accept(packet))) edge.terminate(new CloseStatus(1013, "ENGINE_EDGE_OVERFLOW"));
            return;
        }
        Actor actor=actors.get(packet.id());
        if (actor == null && packet.kind().equals("open")) {
            if (tombstones.containsKey(packet.id()) || actors.size() >= 256) { reject(packet, 1013); return; }
            WorldAuthority.Owner expected=new WorldAuthority.Owner(packet.root(), ClusterIdentity.NODE, packet.epoch());
            if (!authority.accepts(expected)) { reject(packet, 1012); return; }
            Actor candidate=new Actor(packet, expected);
            Actor prior=actors.putIfAbsent(packet.id(), candidate);
            if (prior != null) { candidate.mailbox.close(); actor=prior; } else actor=candidate;
        }
        if (actor == null || !actor.edgeNode.equals(packet.from()) || actor.owner.root()!=packet.root()
                || actor.owner.epoch()!=packet.epoch()) return;
        Actor target=actor;
        target.lastSeen=System.nanoTime();
        if (packet.kind().equals("keepalive")) return;
        if (!target.mailbox.offer(() -> target.accept(packet))) target.terminate(new CloseStatus(1013, "ENGINE_INPUT_OVERFLOW"));
    }
    private void reject(Packet packet, int code) {
        try { push(packet.from(), new Packet("closed", ClusterIdentity.NODE, packet.id(), packet.root(), packet.epoch(),
                0, packet.world(), "WORLD_AUTHORITY_UNAVAILABLE", false, code)); }
        catch (RuntimeException failure) { log.debug("Could not reject unavailable authority connection", failure); }
    }

    /** The student service already checked ownership/authorization; execute cleanup on its game owner. */
    public boolean forwardWorldService(String method, Object[] arguments) {
        long world = ((Number) arguments[0]).longValue();
        return deleteWorldOnOwner(world, json.writeValueAsString(Map.of("method", method, "arguments", arguments)));
    }
    public boolean deleteWorldOnOwner(long world) { return deleteWorldOnOwner(world, ""); }
    private boolean deleteWorldOnOwner(long world, String request) {
        WorldAuthority.Owner owner = authority.resolve(world);
        if (ClusterIdentity.NODE.equals(owner.node())) return false;
        String id = UUID.randomUUID().toString();
        PendingDelete pending = new PendingDelete(owner);
        deletions.put(id, pending);
        try {
            push(owner.node(), new Packet("deleteWorld", ClusterIdentity.NODE, id, owner.root(), owner.epoch(),
                    0, world, request, false, 0));
            Packet response = pending.result.get(30, TimeUnit.SECONDS);
            if (response.code() == 403) throw new com.gameexpert.common.ForbiddenException(response.payload());
            if (response.code() == 404) throw new com.gameexpert.common.NotFoundException(response.payload());
            if (response.code() == 400) throw new com.gameexpert.common.InvalidRequestException(response.payload());
            if (response.code() == 409) throw new com.gameexpert.common.ConflictException(response.payload());
            if (response.code() != 204) throw new IllegalStateException(response.payload());
            return true;
        } catch (com.gameexpert.common.ConflictException | com.gameexpert.common.ForbiddenException
                | com.gameexpert.common.NotFoundException | com.gameexpert.common.InvalidRequestException rejected) { throw rejected;
        } catch (Exception failure) {
            throw new IllegalStateException("Remote world deletion did not confirm completion", failure);
        } finally { deletions.remove(id, pending); }
    }

    private void deleteOnAuthority(Packet request) {
        int code = 204;
        String error = "";
        try {
            WorldAuthority.Owner expected = new WorldAuthority.Owner(request.root(), ClusterIdentity.NODE, request.epoch());
            if (request.world() != request.root() || !authority.accepts(expected)) {
                throw new com.gameexpert.common.ConflictException("WORLD_AUTHORITY_CHANGED");
            }
            if (!request.payload().isEmpty()) {
                JsonNode payload = json.readTree(request.payload());
                String name = payload.path("method").asString();
                if (!name.equals("deleteWorld") && !name.equals("deleteWorldIfMatches")) throw new IllegalArgumentException("Unsupported world operation");
                Object service = context.getBean("worldService");
                int count = name.equals("deleteWorld") ? 2 : 3;
                java.lang.reflect.Method method = Arrays.stream(service.getClass().getMethods())
                        .filter(candidate -> candidate.getName().equals(name) && candidate.getParameterCount() == count)
                        .findFirst().orElseThrow();
                Object[] arguments = new Object[count];
                for (int index = 0; index < count; index++) arguments[index] = json.treeToValue(
                        payload.path("arguments").get(index), method.getParameterTypes()[index]);
                try { method.invoke(service, arguments); }
                catch (java.lang.reflect.InvocationTargetException failure) {
                    if (failure.getCause() instanceof RuntimeException cause) throw cause;
                    throw new IllegalStateException(failure.getCause());
                }
            } else {
            new org.springframework.transaction.support.TransactionTemplate(transactions.getObject()).executeWithoutResult(status -> {
                com.gameexpert.api.persistence.WorldStore store = worldStore.getObject();
                store.findById(request.world()).ifPresent(world ->
                        worldOperations.getObject().deleteWorld(request.world(), () -> store.delete(world)));
            });
            }
        } catch (com.gameexpert.common.ForbiddenException rejected) {
            code = 403; error = rejected.getError();
        } catch (com.gameexpert.common.NotFoundException rejected) {
            code = 404; error = rejected.getError();
        } catch (com.gameexpert.common.InvalidRequestException rejected) {
            code = 400; error = rejected.getError();
        } catch (com.gameexpert.common.ConflictException conflict) {
            code = 409; error = conflict.getError();
        } catch (Exception failure) {
            code = 500; error = "WORLD_DELETE_FAILED";
            log.error("Authority world deletion failed: world={}", request.world(), failure);
        }
        try { push(request.from(), new Packet("deleteResult", ClusterIdentity.NODE, request.id(), request.root(),
                request.epoch(), 0, request.world(), error, false, code));
        } catch (RuntimeException failure) { log.warn("World deletion response unavailable: world={}", request.world(), failure); }
    }

    /** Called before the student's registry registration; the authority resolves the saved dimension first. */
    public WebSocketSession openPhysical(WebSocketSession physical) throws Exception {
        long root=(Long) physical.getAttributes().get(ATTR_WORLD_ID);
        WorldAuthority.Owner owner=authority.resolve(root);
        Edge edge=new Edge(physical, owner);
        if (physicalEdges.putIfAbsent(physical.getId(), edge) != null) throw new IllegalStateException("Duplicate physical connection");
        wireEdges.put(edge.wire, edge);
        try {
            Identity identity=new Identity((String) physical.getAttributes().get(ATTR_NICKNAME),
                    (Long) physical.getAttributes().get(ATTR_PLAYER_ID), (Integer) physical.getAttributes().get(ATTR_WORLD_SEED),
                    Difficulty.orDefault((Difficulty) physical.getAttributes().get(ATTR_WORLD_DIFFICULTY)).name(), root);
            edge.send("open", json.writeValueAsString(identity));
            edge.opened.get(30, TimeUnit.SECONDS);
            return edge.current;
        } catch (Exception failure) { edge.terminate(new CloseStatus(1012, "ENGINE_OPEN_FAILED")); throw failure; }
    }
    public boolean isPhysical(WebSocketSession session) {
        return session instanceof EdgeSession || physicalEdges.containsKey(session.getId());
    }
    private Edge edgeOf(WebSocketSession session) {
        return session instanceof EdgeSession edge ? edge.edge : physicalEdges.get(session.getId());
    }
    public WebSocketSession currentPhysical(WebSocketSession session) {
        Edge edge=edgeOf(session); return edge==null ? null : edge.current;
    }
    public Object physicalLock(WebSocketSession session) {
        Edge edge=edgeOf(session);
        return edge==null ? session : edge.transportLock;
    }
    public boolean physicalReady(WebSocketSession session) {
        Edge edge=edgeOf(session); return edge!=null && edge.ready && !edge.closed.get();
    }
    public void joinedPhysical(WebSocketSession session) throws Exception {
        Edge edge=edgeOf(session);
        if (edge==null) throw new IllegalStateException("Missing engine edge");
        edge.send("join", "");
        edge.welcome.get(180, TimeUnit.SECONDS);
    }
    public boolean consumePhysicalBarrier(WebSocketSession session, TextMessage message) {
        Edge edge=edgeOf(session);
        if (edge==null || edge.ready) return false;
        try {
            JsonNode value=json.readTree(message.getPayload());
            if (value!=null && "dimensionReady".equals(value.path("type").asString())) edge.send("input", message.getPayload());
        } catch (RuntimeException invalid) { /* same withholding policy as DimensionSession */ }
        return true;
    }
    public void releasePhysical(WebSocketSession session, boolean graceful) {
        Edge edge=edgeOf(session);
        if (edge==null) return;
        if (graceful) edge.send("input", "{\"type\":\"leaveWorld\"}");
        else edge.terminate(CloseStatus.NORMAL);
    }
    public void closedPhysical(WebSocketSession session) {
        Edge edge=edgeOf(session); if (edge!=null) edge.terminate(CloseStatus.NORMAL);
    }
    public static boolean forwardFromEdge(WsMessageContext context, JsonNode message) {
        ClusterRuntime cluster=current();
        if (cluster==null || !(context.session() instanceof EdgeSession edge)) return false;
        if (edge.edge.current!=edge || !edge.isOpen() || !edge.edge.ready) return true;
        edge.edge.send("input", cluster.json.writeValueAsString(message));
        return true;
    }
    /** /pos remains a single local chat save; only its game-state query goes to the owner. */
    public static String resolveEdgeCommand(Long world, String nickname, String content) {
        ClusterRuntime cluster=current();
        if (cluster==null || Boolean.TRUE.equals(AUTHORITY_DISPATCH.get()) || !content.strip().equals("/pos")) return null;
        SessionRegistry.Entry local=cluster.sessions.local().get(world, nickname);
        return local != null && local.session() instanceof EdgeSession edge ? edge.edge.query(content) : null;
    }
    public static boolean establishAuthorityDimension(WebSocketSession session) throws Exception {
        ClusterRuntime cluster=current();
        if (cluster==null || !ClusterIdentity.isAuthority(session)) return false;
        Actor actor=cluster.actors.get((String) session.getAttributes().get(WIRE));
        if (actor==null || !actor.live.get() || !cluster.authority.accepts(actor.owner)) throw new IllegalStateException("Stale dimension authority");
        actor.session=session;
        cluster.joinAuthority(actor, session);
        return true;
    }
    private void joinAuthority(Actor actor, WebSocketSession session) throws Exception {
        long world=(Long) session.getAttributes().get(ATTR_WORLD_ID);
        String nickname=(String) session.getAttributes().get(ATTR_NICKNAME);
        game.getObject().prepare(session);
        SessionRegistry.Entry entry=sessions.register(world, nickname, session);
        if (entry==null) { game.getObject().reject(session); actor.terminate(new CloseStatus(4002)); return; }
        game.getObject().joined(session, entry);
    }
    private void dispatchAuthority(WsMessageContext context, String payload) {
        JsonNode message=json.readTree(payload);
        String type=message==null ? "" : message.path("type").asString("");
        if (ClusterHandlerPostProcessor.LOCAL_TYPES.contains(type)) throw new IllegalArgumentException("Local student handler on game wire");
        EngineMessageHandler handler=handlers.get(type);
        if (handler==null) throw new IllegalArgumentException("Unknown game message type: " + type);
        try {
            handler.handle(context, message);
        } catch (com.gameexpert.engine.ActionQueueOverflowException overflow) {
            transport.getObject().sendTo(context.session(), new com.gameexpert.ws.dto.WsMessages.Error("QUEUE_FULL"));
        } catch (IllegalArgumentException invalid) {
            transport.getObject().sendTo(context.session(), new com.gameexpert.ws.dto.WsMessages.Error("INVALID_MESSAGE"));
        } catch (Exception failure) {
            log.error("Game handler failed on authority: type={} world={}", type, context.worldId(), failure);
            transport.getObject().sendTo(context.session(), new com.gameexpert.ws.dto.WsMessages.Error("INTERNAL_ERROR"));
        }
    }

    private void maintain() {
        if (stopped.get()) return;
        try {
            redis.opsForValue().set(alive(ClusterIdentity.NODE), "1", Duration.ofSeconds(8));
            transportFailed=false;
            Map<String,Boolean> health=new HashMap<>();
            for (Edge edge : physicalEdges.values()) {
                boolean healthy=health.computeIfAbsent(edge.owner.node(), node -> Boolean.TRUE.equals(redis.hasKey(alive(node))));
                if (!healthy) edge.terminate(new CloseStatus(1012, "ENGINE_OWNER_LOST"));
                else if (!edge.closed.get()) edge.heartbeat();
            }
            for (Actor actor : actors.values()) {
                boolean healthy=health.computeIfAbsent(actor.edgeNode, node -> Boolean.TRUE.equals(redis.hasKey(alive(node))));
                if (!authority.accepts(actor.owner) || !healthy || System.nanoTime()-actor.lastSeen > Duration.ofSeconds(12).toNanos()) {
                    actor.terminate(new CloseStatus(1012, "ENGINE_SESSION_LOST"));
                }
            }
            for (long root : authority.retiredRoots()) {
                if (abandoned.add(root)) engines.getObject().abandonClusterAuthority(root);
            }
            long now=System.nanoTime(); tombstones.entrySet().removeIf(e -> e.getValue()<now);
        } catch (Exception failure) { fault(failure); }
    }
    private void fault(Throwable failure) {
        if (!transportFailed) { transportFailed=true; log.warn("Engine transport unavailable; closing sessions rather than replaying ambiguous input", failure); }
        for (Edge edge : physicalEdges.values()) edge.terminate(new CloseStatus(1012, "ENGINE_TRANSPORT_LOST"));
        for (Actor actor : actors.values()) actor.terminate(new CloseStatus(1012, "ENGINE_TRANSPORT_LOST"));
    }
    @Override @jakarta.annotation.PreDestroy public void close() {
        if (!stopped.compareAndSet(false,true)) return;
        for (Edge edge : physicalEdges.values()) edge.terminate(new CloseStatus(1001, "SERVER_SHUTDOWN"));
        for (Actor actor : actors.values()) actor.terminate(new CloseStatus(1001, "SERVER_SHUTDOWN"));
        deletions.values().forEach(pending -> pending.result.completeExceptionally(new IllegalStateException("Server shutting down")));
        deletions.clear();
        maintenance.shutdownNow();
        if (reader!=null) reader.interrupt();
        try { redis.delete(List.of(alive(ClusterIdentity.NODE), inbox(ClusterIdentity.NODE), inbox(ClusterIdentity.NODE)+":bytes")); }
        catch (RuntimeException failure) { log.debug("Owned engine mailbox cleanup deferred to TTL", failure); }
        if (active==this) active=null;
    }

    final class Edge {
        final WebSocketSession physical;
        final WorldAuthority.Owner owner;
        final String wire=UUID.randomUUID().toString();
        final Object transportLock=new Object();
        final Object sendLock=new Object();
        final AtomicBoolean closed=new AtomicBoolean();
        final CompletableFuture<Void> opened=new CompletableFuture<>();
        final CompletableFuture<Void> welcome=new CompletableFuture<>();
        final ConcurrentMap<Long,CompletableFuture<String>> queries=new ConcurrentHashMap<>();
        final SerialMailbox mailbox;
        volatile EdgeSession current;
        volatile boolean ready;
        private long requestSequence=-1;
        private long lastFrame;
        private long generation;
        private boolean transitioning;
        Edge(WebSocketSession physical, WorldAuthority.Owner owner) {
            this.physical=physical; this.owner=owner;
            mailbox=new SerialMailbox("cluster-edge-"+wire, 128,
                    failure -> { log.warn("Engine edge failed: {}",wire,failure); terminate(new CloseStatus(1012,"ENGINE_EDGE_FAILED")); });
        }
        long world() {
            EdgeSession value=current;
            return value==null ? owner.root() : (Long)value.getAttributes().get(ATTR_WORLD_ID);
        }
        void send(String kind, String payload) {
            synchronized (sendLock) {
                if (closed.get()) throw new IllegalStateException("Engine edge is closed");
                push(owner.node(), new Packet(kind,ClusterIdentity.NODE,wire,owner.root(),owner.epoch(),
                        ++requestSequence,world(),payload,false,0));
            }
        }
        void heartbeat() {
            if (!closed.get()) push(owner.node(),new Packet("keepalive",ClusterIdentity.NODE,wire,owner.root(),
                    owner.epoch(),-1,world(),"",false,0));
        }
        String query(String content) {
            CompletableFuture<String> reply=new CompletableFuture<>();
            long sequence;
            synchronized(sendLock) {
                if(closed.get()) throw new IllegalStateException("Engine edge is closed");
                sequence=++requestSequence; queries.put(sequence,reply);
                try { push(owner.node(),new Packet("command",ClusterIdentity.NODE,wire,owner.root(),owner.epoch(),
                        sequence,world(),content,false,0)); }
                catch(RuntimeException failure) { queries.remove(sequence); throw failure; }
            }
            try { return reply.get(5,TimeUnit.SECONDS); }
            catch(Exception failure) { throw new IllegalStateException("Authoritative command query failed",failure); }
            finally { queries.remove(sequence); }
        }
        void accept(Packet packet) {
            if(closed.get()) return;
            switch(packet.kind()) {
                case "opened" -> {
                    if(current!=null) return;
                    Identity identity=json.readValue(packet.payload(),Identity.class);
                    Map<String,Object> attrs=new HashMap<>(physical.getAttributes());
                    attrs.put(ATTR_WORLD_ID,identity.world());
                    attrs.put(ATTR_NICKNAME,identity.nickname());
                    attrs.put(ATTR_PLAYER_ID,identity.player());
                    attrs.put(ATTR_WORLD_SEED,identity.seed());
                    attrs.put(ATTR_WORLD_DIFFICULTY,Difficulty.valueOf(identity.difficulty()));
                    current=new EdgeSession(this,0,attrs);
                    opened.complete(null);
                }
                case "frame" -> frame(packet);
                case "result" -> {
                    CompletableFuture<String> reply=queries.get(packet.sequence());
                    if(reply!=null) reply.complete(packet.payload());
                }
                case "closed" -> terminate(new CloseStatus(packet.code(),"ENGINE_REMOTE_CLOSED"));
                default -> throw new IllegalArgumentException("Unexpected edge envelope");
            }
        }
        private void frame(Packet packet) {
            if(packet.sequence()<=lastFrame) return; // duplicate transport envelope, not another gameplay action
            if(packet.sequence()!=lastFrame+1 || current==null) {
                terminate(new CloseStatus(1012,"ENGINE_FRAME_SEQUENCE_GAP")); return;
            }
            lastFrame=packet.sequence();
            synchronized(transportLock) {
                if(closed.get()) return;
                boolean changed=packet.world()!=world();
                if(changed) {
                    EdgeSession old=current;
                    String nickname=(String)old.getAttributes().get(ATTR_NICKNAME);
                    localCleanup.remove(world(),nickname,old);
                    transport.getObject().forget(old);
                    Map<String,Object> attrs=new HashMap<>(old.getAttributes());
                    attrs.put(ATTR_WORLD_ID,packet.world());
                    current=new EdgeSession(this,++generation,attrs);
                    ready=false; transitioning=true;
                    game.getObject().prepare(current);
                }
                EdgeSession target=current;
                if(packet.binary()) {
                    if(!ready || !transport.getObject().sendBinaryTo(target,Base64.getDecoder().decode(packet.payload()))) {
                        terminate(new CloseStatus(1012,"ENGINE_BINARY_SEND_FAILED"));
                    }
                    return;
                }
                JsonNode message=json.readTree(packet.payload());
                String type=message.path("type").asString("");
                if(type.equals("welcome")) {
                    if(transitioning) {
                        SessionRegistry.Entry registered=sessions.local().register(world(),
                                (String)target.getAttributes().get(ATTR_NICKNAME),target);
                        if(registered==null) { terminate(new CloseStatus(4002)); return; }
                    }
                    synchronized(target) {
                        ready=true;
                        HeartbeatMonitor.welcomeCompleted(target);
                        if(!transport.getObject().sendWelcomeThenRelease(target,message)) {
                            terminate(new CloseStatus(1012,"ENGINE_WELCOME_FAILED")); return;
                        }
                        if(transitioning) {
                            SessionRegistry.Entry entry=sessions.local().get(world(),(String)target.getAttributes().get(ATTR_NICKNAME));
                            if(target.isOpen() && entry!=null && entry.session()==target) localPresence.join(world(),entry.connectionId());
                            transitioning=false;
                        }
                    }
                    welcome.complete(null);
                } else if(changed) {
                    // The dimension transition must reach the client before its ACK/welcome barrier.
                    if(!transport.getObject().sendAuthorityControl(target,message)) terminate(new CloseStatus(1012,"ENGINE_CONTROL_FAILED"));
                } else {
                    transport.getObject().sendTo(target,message);
                }
            }
        }
        void terminate(CloseStatus status) {
            if(!closed.compareAndSet(false,true)) return;
            physicalEdges.remove(physical.getId(),this); wireEdges.remove(wire,this);
            synchronized(sendLock) {
                try { push(owner.node(),new Packet("close",ClusterIdentity.NODE,wire,owner.root(),owner.epoch(),
                        ++requestSequence,world(),"",false,status.getCode())); }
                catch(RuntimeException failure) { log.debug("Owner close deferred to engine connection health timeout",failure); }
            }
            // Never acquire the session monitor while holding transportLock: a local
            // chat handler may hold that monitor while sending its physical frame.
            EdgeSession target=current;
            if(target!=null) {
                localCleanup.remove((Long)target.getAttributes().get(ATTR_WORLD_ID),
                        (String)target.getAttributes().get(ATTR_NICKNAME),target);
                transport.getObject().forget(target);
            }
            IllegalStateException failure=new IllegalStateException("Engine connection closed: "+status.getCode());
            opened.completeExceptionally(failure); welcome.completeExceptionally(failure);
            queries.values().forEach(reply->reply.completeExceptionally(failure)); queries.clear();
            try { if(physical.isOpen()) physical.close(status); }
            catch(IOException|RuntimeException closeFailure) { log.debug("Physical edge close failed",closeFailure); }
            mailbox.close();
        }
    }

    private final class Actor {
        final String wire;
        final String edgeNode;
        final WorldAuthority.Owner owner;
        final AtomicBoolean live=new AtomicBoolean(true);
        final SerialMailbox mailbox;
        final TunnelSession raw;
        volatile WebSocketSession session;
        volatile long lastSeen=System.nanoTime();
        private long lastInput=-1;
        private long frameSequence;
        private boolean joined;
        Actor(Packet open,WorldAuthority.Owner owner) {
            this.wire=open.id();this.edgeNode=open.from();this.owner=owner;
            Identity identity=json.readValue(open.payload(),Identity.class);
            Map<String,Object> attrs=new ConcurrentHashMap<>();
            attrs.put(ATTR_WORLD_ID,owner.root()); attrs.put(ATTR_NICKNAME,identity.nickname());
            attrs.put(ATTR_PLAYER_ID,identity.player()); attrs.put(ATTR_WORLD_SEED,identity.seed());
            attrs.put(ATTR_WORLD_DIFFICULTY,Difficulty.valueOf(identity.difficulty()));
            attrs.put(ClusterIdentity.ROLE,ClusterIdentity.AUTHORITY); attrs.put(WIRE,wire);
            raw=new TunnelSession(wire,attrs,()->live.get()&&authority.accepts(owner),this::frame,this::terminate);
            mailbox=new SerialMailbox("cluster-authority-"+wire,128,
                    failure->{log.warn("Authority connection failed: {}",wire,failure);terminate(new CloseStatus(1012,"ENGINE_AUTHORITY_FAILED"));});
        }
        WebSocketSession currentSession() {
            WebSocketSession value=session;
            return value instanceof DimensionSession dimension ? dimension.current() : value;
        }
        long world() {
            WebSocketSession value=currentSession();
            return value==null ? owner.root() : (Long)value.getAttributes().get(ATTR_WORLD_ID);
        }
        void accept(Packet packet) {
            if(!live.get()) return;
            if(!authority.accepts(owner)) { terminate(new CloseStatus(1012,"WORLD_AUTHORITY_LOST"));return; }
            if(packet.sequence()<=lastInput) return;
            if(packet.sequence()!=lastInput+1) { terminate(new CloseStatus(1012,"ENGINE_INPUT_SEQUENCE_GAP"));return; }
            lastInput=packet.sequence();
            try {
                switch(packet.kind()) {
                    case "open" -> {
                        if(session!=null) throw new IllegalStateException("Second authority open");
                        session=game.getObject().open(raw);
                        Identity identity=new Identity((String)session.getAttributes().get(ATTR_NICKNAME),
                                (Long)session.getAttributes().get(ATTR_PLAYER_ID),(Integer)session.getAttributes().get(ATTR_WORLD_SEED),
                                ((Difficulty)session.getAttributes().get(ATTR_WORLD_DIFFICULTY)).name(),world());
                        emit("opened",0,json.writeValueAsString(identity),false,0);
                    }
                    case "join" -> {
                        if(joined || session==null) throw new IllegalStateException("Invalid authority join");
                        joined=true; joinAuthority(this,session);
                    }
                    case "input" -> {
                        if(!joined || packet.world()!=world()) return;
                        AUTHORITY_DISPATCH.set(true);
                        try { game.getObject().receive(raw,new TextMessage(packet.payload()),ClusterRuntime.this::dispatchAuthority); }
                        finally { AUTHORITY_DISPATCH.remove(); }
                    }
                    case "command" -> {
                        if(!joined || packet.world()!=world()) return;
                        AUTHORITY_DISPATCH.set(true);
                        try {
                            String result=commands.getObject().resolve(world(),
                                    (String)currentSession().getAttributes().get(ATTR_NICKNAME),packet.payload());
                            emit("result",packet.sequence(),result,false,0);
                        } finally { AUTHORITY_DISPATCH.remove(); }
                    }
                    case "close" -> terminate(new CloseStatus(packet.code()==0?1000:packet.code()));
                    default -> throw new IllegalArgumentException("Unexpected authority envelope");
                }
            } catch(Exception failure) {
                log.warn("Authority request failed: kind={} root={} connection={}",packet.kind(),owner.root(),wire,failure);
                terminate(new CloseStatus(1012,"ENGINE_REQUEST_FAILED"));
            }
        }
        void frame(WebSocketMessage<?> message) throws IOException {
            if(!live.get() || !authority.accepts(owner)) throw new IOException("World ownership lost");
            String payload; boolean binary;
            if(message instanceof TextMessage text) { payload=text.getPayload();binary=false; }
            else if(message instanceof BinaryMessage data) {
                ByteBuffer buffer=data.getPayload().duplicate();byte[] bytes=new byte[buffer.remaining()];buffer.get(bytes);
                payload=Base64.getEncoder().encodeToString(bytes);binary=true;
            } else throw new IOException("Unsupported engine frame");
            try { emit("frame",++frameSequence,payload,binary,0); }
            catch(RuntimeException failure) { throw new IOException("Engine frame delivery failed",failure); }
        }
        void emit(String kind,long sequence,String payload,boolean binary,int code) {
            push(edgeNode,new Packet(kind,ClusterIdentity.NODE,wire,owner.root(),owner.epoch(),sequence,world(),payload,binary,code));
        }
        void terminate(CloseStatus status) {
            if(!live.compareAndSet(true,false)) return;
            actors.remove(wire,this);
            tombstones.put(wire,System.nanoTime()+Duration.ofSeconds(60).toNanos());
            try { emit("closed",0,"",false,status.getCode()); }
            catch(RuntimeException failure) { log.debug("Remote edge close deferred to owner health timeout",failure); }
            // Existing dimension/leave ordering drains required saves on this authority only.
            // Fenced/expired writes are rejected by the database, never retried on a successor.
            if(session!=null) game.getObject().closed(raw);
            mailbox.close();
        }
    }
}
