package com.gameexpert.ws;

import com.gameexpert.api.SessionRegistry;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;

import tools.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.gameexpert.engine.TickSafetyTelemetry;
import com.gameexpert.ws.dto.WsMessages.BlockUpdate;
import com.gameexpert.ws.dto.WsMessages.Block;
import com.gameexpert.ws.dto.WsMessages.BannerBlockState;
import com.gameexpert.ws.dto.WsMessages.BannerRemove;
import com.gameexpert.ws.dto.WsMessages.BannerUpdate;
import com.gameexpert.ws.dto.WsMessages.SignRemove;
import com.gameexpert.ws.dto.WsMessages.SignUpdate;
import com.gameexpert.block.snapshot.ChunkSnapshotCodec;
import com.gameexpert.terrain.Blocks;

/** 동시 송신을 직렬화하고 월드 전체에 JSON 메시지를 전송합니다. */
@Component
@RequiredArgsConstructor
@Slf4j
public class GameTransport {

    private static final int SEND_TIME_LIMIT_MS = 2_000;
    private static final int BUFFER_LIMIT_BYTES = 64 * 1024;
    static final int MAX_PENDING_TICK_OUTBOUND_PER_WORLD = 256;
    private static final int MAX_PENDING_TICK_OUTBOUND_BLOCKS_PER_WORLD = 4_096;
    private static final int MAX_BLOCKS_PER_TICK_OUTBOUND_MESSAGE = 512;
    /**
     * Each world still has exactly one queued drain, preserving its FIFO. Two workers prevent a slow or
     * decoration-heavy world from blocking gameplay messages for every other world on the server.
     */
    private static final ThreadPoolExecutor TICK_OUTBOUND_SENDERS = new ThreadPoolExecutor(
            2, 2, 0L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(64), runnable -> {
                Thread thread = new Thread(runnable, "world-tick-outbound");
                thread.setDaemon(true);
                return thread;
            }, new ThreadPoolExecutor.AbortPolicy());
    static final int MAX_BUFFERED_BLOCK_UPDATES_PER_SESSION = 64;
    static final int MAX_BUFFERED_BLOCKS_PER_SESSION = 2_048;
    static final int MAX_BUFFERED_PRE_WELCOME_MESSAGES = 256;
    static final int MAX_BUFFERED_PRE_WELCOME_BYTES = 1024 * 1024;

    private final SessionRegistry registry;
    private final ObjectMapper objectMapper;
    /** Raw-session identity paired with each decorated sender so a late failure cannot poison a replacement. */
    private final Map<String, WebSocketSession> sendSessionOwners = new ConcurrentHashMap<>();
    private final Map<String, WebSocketSession> sendSessions = new ConcurrentHashMap<>();
    /** A failed decorated send is terminal until reconnect; otherwise Spring silently drops later messages. */
    private final Set<String> unreliableSessions = ConcurrentHashMap.newKeySet();
    /** Per-world bounded FIFO queues; each queue has at most one worker drain scheduled at once. */
    private final Map<Long, TickOutboundQueue> tickOutboundByWorld = new ConcurrentHashMap<>();
    /** 세션별 full snapshot barrier. 해당 청크만 잠그므로 다른 세션/청크 방송은 그대로 흐른다. */
    private final Map<String, SnapshotBarrierSession> snapshotBarriers = new ConcurrentHashMap<>();
    /** Welcome이 물리적인 첫 프레임이 될 때까지 JSON·binary를 함께 보관하는 세션별 FIFO. */
    private final Map<String, WelcomeBarrierSession> welcomeBarriers = new ConcurrentHashMap<>();

    public void register(WebSocketSession session) {
        registerTransport(session);
        welcomeBarriers.remove(session.getId());
    }

    public void registerAwaitingWelcome(WebSocketSession session) {
        welcomeBarriers.put(session.getId(), new WelcomeBarrierSession(session));
        registerTransport(session);
    }

    private void registerTransport(WebSocketSession session) {
        unreliableSessions.remove(session.getId());
        sendSessionOwners.put(session.getId(), session);
        sendSessions.put(session.getId(),
                new ConcurrentWebSocketSessionDecorator(session, SEND_TIME_LIMIT_MS, BUFFER_LIMIT_BYTES));
    }

    public void forget(WebSocketSession session) {
        if (!sendSessionOwners.remove(session.getId(), session)) return;
        sendSessions.remove(session.getId());
        unreliableSessions.remove(session.getId());
        snapshotBarriers.remove(session.getId());
        welcomeBarriers.remove(session.getId());
    }

    /** Releases bounded delivery state once its runtime is gone; world IDs may otherwise accumulate forever. */
    public void forgetWorld(Long worldId) {
        TickOutboundQueue queue = tickOutboundByWorld.remove(worldId);
        if (queue != null) queue.dispose();
        Set<String> worldSessionIds = new HashSet<>();
        if (registry != null) {
            for (SessionRegistry.Entry entry : registry.entries(worldId)) {
                worldSessionIds.add(entry.connectionId());
            }
        }
        for (Map.Entry<String, SnapshotBarrierSession> entry : snapshotBarriers.entrySet()) {
            SnapshotBarrierSession state = entry.getValue();
            synchronized (state) {
                boolean removeAll = worldSessionIds.contains(entry.getKey());
                for (ChunkSnapshotBarrier barrier : new ArrayList<>(state.byChunk.values())) {
                    if (removeAll || (barrier.worldId != null && barrier.worldId.equals(worldId))) {
                        removeBarrierLocked(state, barrier);
                    }
                }
                if (state.byChunk.isEmpty() && state.queued.isEmpty()) {
                    snapshotBarriers.remove(entry.getKey(), state);
                }
            }
        }
    }

    public void broadcast(Long worldId, Object message) {
        if (TickSafetyTelemetry.isTickThread()) {
            enqueueBroadcastFromTick(worldId, message);
            return;
        }
        broadcastNow(worldId, message);
    }

    /**
     * Enqueues a world-tick broadcast onto the bounded sender worker. Calls are FIFO per world;
     * a full queue returns {@code false} so the owner can retain/retry without sending on the tick.
     */
    public boolean enqueueBroadcastFromTick(Long worldId, Object message) {
        return tickOutbound(worldId).enqueue(new BroadcastTickOutbound(worldId, message), true);
    }

    /**
     * Handoff for a producer that keeps its own cursor until this method succeeds. Queue pressure therefore
     * returns false without disconnecting recipients; the caller will retry the exact unsent suffix.
     */
    public boolean enqueueRetainedBroadcastFromTick(Long worldId, Object message) {
        return tickOutbound(worldId).enqueue(new BroadcastTickOutbound(worldId, message), false);
    }

    /**
     * Retained generated-state handoff whose recipients are fixed at the first enqueue attempt.
     * Welcome-barrier recipients carry the internal sequence through their pending FIFO so release
     * can discard an already represented delta or deliver a newer one without a race.
     */
    public boolean enqueueRetainedGeneratedBroadcastFromTick(
            Long worldId, Object message, long generatedSequence) {
        if (generatedSequence <= 0L) {
            throw new IllegalArgumentException("positive generated sequence required");
        }
        return tickOutbound(worldId).enqueueRetainedGenerated(
                message, generatedSequence, () -> generatedRecipients(worldId));
    }

    private List<WebSocketSession> generatedRecipients(Long worldId) {
        ArrayList<WebSocketSession> recipients = new ArrayList<>();
        for (SessionRegistry.Entry entry : registry.entries(worldId)) {
            WebSocketSession session = entry.session();
            if (!isActiveSendTarget(session, sendSessions.get(session.getId()))) continue;
            recipients.add(session);
        }
        return List.copyOf(recipients);
    }

    /**
     * Places one recovery boundary after every delta prefix whose history is about to be discarded.
     * The marker bypasses ordinary queue limits and captures only sessions that can have observed that prefix.
     */
    public boolean enqueueLiveJournalRecoveryFromTick(Long worldId) {
        return tickOutbound(worldId).enqueueLiveJournalRecovery();
    }

    private void broadcastNow(Long worldId, Object message) {
        TickSafetyTelemetry.record(TickSafetyTelemetry.Event.SYNCHRONOUS_SEND_CALL);
        if (message instanceof BlockUpdate blockUpdate) {
            for (SessionRegistry.Entry entry : registry.entries(worldId)) {
                sendBlockUpdate(entry.session(), blockUpdate);
            }
            return;
        }
        if (message instanceof BannerUpdate bannerUpdate) {
            for (SessionRegistry.Entry entry : registry.entries(worldId)) {
                sendBannerSemantic(entry.session(), bannerUpdate,
                        bannerUpdate.getBanner().getX(), bannerUpdate.getBanner().getZ());
            }
            return;
        }
        if (message instanceof BannerRemove bannerRemove) {
            for (SessionRegistry.Entry entry : registry.entries(worldId)) {
                sendBannerSemantic(entry.session(), bannerRemove,
                        bannerRemove.getX(), bannerRemove.getZ());
            }
            return;
        }
        if (message instanceof SignUpdate signUpdate) {
            for (SessionRegistry.Entry entry : registry.entries(worldId)) {
                sendBlockEntitySemantic(entry.session(), signUpdate,
                        signUpdate.getSign().getX(), signUpdate.getSign().getY(),
                        signUpdate.getSign().getZ());
            }
            return;
        }
        if (message instanceof SignRemove signRemove) {
            for (SessionRegistry.Entry entry : registry.entries(worldId)) {
                sendBlockEntitySemantic(entry.session(), signRemove,
                        signRemove.getX(), signRemove.getY(), signRemove.getZ());
            }
            return;
        }
        String messageType = message.getClass().getSimpleName();
        String json = serialize(message);
        if (json == null) {
            for (SessionRegistry.Entry entry : registry.entries(worldId)) {
                recoverSerializationFailure(entry.session(), messageType);
            }
            return;
        }
        for (SessionRegistry.Entry entry : registry.entries(worldId)) {
            sendRaw(entry.session(), json, messageType);
        }
    }

    /** WS 스레드 전용 경로입니다. 틱에서 개별 세션에 보내려면 {@link #enqueueSendToFromTick}을 쓰십시오. */
    public void broadcastExcept(Long worldId, WebSocketSession excluded, Object message) {
        broadcastExceptNow(worldId, excluded, message);
    }

    private void broadcastExceptNow(Long worldId, WebSocketSession excluded, Object message) {
        TickSafetyTelemetry.record(TickSafetyTelemetry.Event.SYNCHRONOUS_SEND_CALL);
        if (message instanceof BlockUpdate blockUpdate) {
            for (SessionRegistry.Entry entry : registry.entries(worldId)) {
                if (entry.session() != excluded) sendBlockUpdate(entry.session(), blockUpdate);
            }
            return;
        }
        String messageType = message.getClass().getSimpleName();
        String json = serialize(message);
        if (json == null) {
            for (SessionRegistry.Entry entry : registry.entries(worldId)) {
                if (entry.session() != excluded) {
                    recoverSerializationFailure(entry.session(), messageType);
                }
            }
            return;
        }
        for (SessionRegistry.Entry entry : registry.entries(worldId)) {
            if (entry.session() != excluded) sendRaw(entry.session(), json, messageType);
        }
    }

    /** WS 스레드 전용 경로입니다. 틱 경로는 월드 FIFO를 유지하려면 {@link #enqueueSendToFromTick}을 씁니다. */
    public void sendTo(WebSocketSession session, Object message) {
        sendToNow(session, message);
    }

    /** Tick-only counterpart of {@link #sendTo(WebSocketSession, Object)}. */
    public boolean enqueueSendToFromTick(Long worldId, WebSocketSession session, Object message) {
        return tickOutbound(worldId).enqueue(new DirectTickOutbound(session, message), true);
    }

    /** Retries a retained tick handoff after bounded-executor saturation without doing any transport work. */
    public void retryTickOutbound(Long worldId) {
        TickOutboundQueue queue = tickOutboundByWorld.get(worldId);
        if (queue != null) queue.retry();
    }

    private void sendToNow(WebSocketSession session, Object message) {
        TickSafetyTelemetry.record(TickSafetyTelemetry.Event.SYNCHRONOUS_SEND_CALL);
        if (message instanceof BlockUpdate blockUpdate) {
            sendBlockUpdate(session, blockUpdate);
            return;
        }
        if (!session.isOpen()) return;
        String messageType = message.getClass().getSimpleName();
        String json = serialize(message);
        if (json == null) {
            recoverSerializationFailure(session, messageType);
            return;
        }
        sendRaw(session, json, messageType);
    }

    /** JSON protocol과 분리된 authoritative chunk-snapshot binary frame을 한 세션에 보냅니다. */
    private boolean sendBinaryTo(WebSocketSession session, byte[] frame) {
        TickSafetyTelemetry.record(TickSafetyTelemetry.Event.SYNCHRONOUS_SEND_CALL);
        if (frame == null || frame.length > ChunkSnapshotCodec.MAX_TRANSPORT_FRAME_BYTES) {
            log.warn("청크 snapshot binary frame 크기 거부: bytes={}", frame == null ? -1 : frame.length);
            return false;
        }
        if (!session.isOpen() || unreliableSessions.contains(session.getId())) return false;
        WebSocketSession target = sendSessions.get(session.getId());
        if (!isActiveSendTarget(session, target)) return false;
        return sendFrame(session, target, new BinaryMessage(frame), "ChunkSnapshot");
    }

    /** Welcome을 직접 먼저 보내고, 준비 중 쌓인 모든 프레임을 FIFO로 비운 뒤 일반 송신을 연다. */
    public boolean sendWelcomeThenRelease(WebSocketSession session, Object welcome) {
        return sendWelcomeThenRelease(session, welcome, -1L);
    }

    /** Generated cursor is internal transport metadata and is never serialized into welcome. */
    public boolean sendWelcomeThenRelease(
            WebSocketSession session, Object welcome, long generatedSequence) {
        if (generatedSequence < -1L) {
            throw new IllegalArgumentException("invalid generated welcome sequence");
        }
        String json = serialize(welcome);
        if (json == null) {
            recoverSerializationFailure(session, "Welcome");
            return false;
        }
        String sessionId = session.getId();
        WelcomeBarrierSession state = welcomeBarriers.get(sessionId);
        WebSocketSession target = sendSessions.get(sessionId);
        if (state == null || !isActiveSendTarget(session, target)) return false;
        synchronized (state) {
            if (welcomeBarriers.get(sessionId) != state
                    || state.owner != session
                    || state.phase != WelcomeBarrierPhase.WAITING) {
                return false;
            }
            state.phase = WelcomeBarrierPhase.DRAINING;
        }
        if (!sendFrameDirect(session, target, new TextMessage(json), "Welcome")) {
            failWelcomeBarrier(state);
            return false;
        }
        while (true) {
            PendingWelcomeFrame pending;
            synchronized (state) {
                if (welcomeBarriers.get(sessionId) != state
                        || state.owner != session
                        || state.phase != WelcomeBarrierPhase.DRAINING) {
                    return false;
                }
                pending = state.pending.pollFirst();
                if (pending == null) {
                    state.releasedGeneratedSequence = generatedSequence;
                    state.phase = WelcomeBarrierPhase.RELEASED;
                    return true;
                }
                state.pendingBytes -= pending.bytes;
            }
            if (pending.generatedSequence != null && generatedSequence >= 0L
                    && pending.generatedSequence <= generatedSequence) {
                continue;
            }
            if (!sendFrameDirect(session, target, pending.message, pending.messageType)) {
                failWelcomeBarrier(state);
                return false;
            }
        }
    }

    private static void failWelcomeBarrier(WelcomeBarrierSession state) {
        synchronized (state) {
            state.phase = WelcomeBarrierPhase.FAILED;
            state.pending.clear();
            state.pendingBytes = 0;
        }
    }

    /**
     * resident snapshot V를 복사한 직후 등록하는 세션·청크 전용 barrier입니다.
     *
     * <p>동일 청크의 기존 barrier가 아직 전송 중이면 null을 반환합니다. world tick은 압축을
     * 기다리지 않고 요청을 다시 큐에 넣습니다.</p>
     */
    public ChunkSnapshotBarrier registerChunkSnapshotBarrier(WebSocketSession session,
            int chunkX, int chunkZ, long snapshotVersion) {
        return registerChunkSnapshotBarrier(null, session, chunkX, chunkZ, snapshotVersion);
    }

    /** 소유 월드를 기록한 barrier를 등록해 월드 폐기 때 세션 누수를 남기지 않고 해제합니다. */
    public ChunkSnapshotBarrier registerChunkSnapshotBarrier(Long worldId,
            WebSocketSession session, int chunkX, int chunkZ, long snapshotVersion) {
        if (!isActiveSendTarget(session, sendSessions.get(session.getId()))
                || unreliableSessions.contains(session.getId())) return null;
        String sessionId = session.getId();
        SnapshotBarrierSession state = snapshotBarriers.computeIfAbsent(sessionId,
                ignored -> new SnapshotBarrierSession());
        long key = chunkKey(chunkX, chunkZ);
        synchronized (state) {
            if (state.byChunk.containsKey(key)) return null;
            ChunkSnapshotBarrier barrier = new ChunkSnapshotBarrier(
                    worldId, sessionId, key, snapshotVersion);
            state.byChunk.put(key, barrier);
            return barrier;
        }
    }

    /**
     * binary snapshot frame 전체를 먼저 보내고, capture V 뒤 같은 청크에서 대기한 blockUpdate만
     * 원래 수신 순서대로 보낸 뒤 barrier를 해제합니다.
     *
     * <p>frame 전송/세션/버퍼가 실패하면 buffered update를 폐기하고 false를 반환합니다. 호출자는
     * 새 resident capture를 다시 스케줄할 수 있으며, 실패한 barrier는 남지 않습니다.</p>
     */
    public boolean sendSnapshotThenRelease(WebSocketSession session, ChunkSnapshotBarrier barrier,
            List<byte[]> frames, List<?> semantics) {
        if (barrier == null || frames == null || frames.isEmpty()) {
            cancelChunkSnapshotBarrier(barrier);
            return false;
        }
        for (byte[] frame : frames) {
            if (frame == null || frame.length > ChunkSnapshotCodec.MAX_TRANSPORT_FRAME_BYTES) {
                cancelChunkSnapshotBarrier(barrier);
                return false;
            }
        }
        SnapshotBarrierSession state = snapshotBarriers.get(barrier.sessionId);
        if (state == null) return false;
        for (byte[] frame : frames) {
            // Streaming cancellation may arrive after the first fragment. Recheck between frames so at most
            // the fragment already inside the transport call is wasted for an unloaded client chunk.
            if (!snapshotBarrierOpen(state, barrier, session)) return false;
            if (!sendBinaryTo(session, frame)) {
                cancelChunkSnapshotBarrier(barrier);
                return false;
            }
        }
        if (semantics == null) {
            cancelChunkSnapshotBarrier(barrier);
            return false;
        }
        // The semantic block-entity replay belongs directly behind the containing binary snapshot and
        // ahead of every mutation buffered after capture V.
        for (Object semantic : semantics) {
            if (!snapshotBarrierOpen(state, barrier, session)
                    || !sendJsonDirect(session, semantic, semantic.getClass().getSimpleName())) {
                cancelChunkSnapshotBarrier(barrier);
                return false;
            }
        }
        // 송신 lock을 잡지 않는다. 새 V+n은 open barrier에 append되고, drain 뒤 다시 확인한다.
        while (true) {
            List<BufferedBlockUpdate> queued;
            List<Object> queuedBanners;
            synchronized (state) {
                if (state.byChunk.get(barrier.chunkKey) != barrier || !barrier.open
                        || barrier.failed || !session.isOpen()) {
                    removeBarrierLocked(state, barrier);
                    return false;
                }
                queued = takeQueuedLocked(state, barrier);
                queuedBanners = takeQueuedBannerSemanticsLocked(state, barrier);
                if ((queued == null || queued.isEmpty())
                        && (queuedBanners == null || queuedBanners.isEmpty())) {
                    removeBarrierLocked(state, barrier);
                    return true;
                }
            }
            if (queued != null) {
                for (BufferedBlockUpdate update : queued) {
                    // The queue is detached from the barrier lock so world ticks can keep appending.
                    // Recheck identity before every send: an unload/cancel after one replay must stop
                    // the remaining detached suffix from reaching a coordinate the client no longer owns.
                    if (!snapshotBarrierOpen(state, barrier, session)) return false;
                    if (!sendBlockUpdateDirect(session, update.message)) {
                        cancelChunkSnapshotBarrier(barrier);
                        return false;
                    }
                }
            }
            if (queuedBanners != null) {
                for (Object semantic : queuedBanners) {
                    if (!snapshotBarrierOpen(state, barrier, session)
                            || !sendJsonDirect(session, semantic,
                                    semantic.getClass().getSimpleName())) {
                        cancelChunkSnapshotBarrier(barrier);
                        return false;
                    }
                }
            }
        }
    }

    private void sendBannerSemantic(WebSocketSession session, Object message, int x, int z) {
        int y = message instanceof BannerUpdate update
                ? update.getBanner().getY() : ((BannerRemove) message).getY();
        sendBlockEntitySemantic(session, message, x, y, z);
    }

    private void sendBlockEntitySemantic(WebSocketSession session, Object message,
            int x, int y, int z) {
        if (!session.isOpen()) return;
        SnapshotBarrierSession state = snapshotBarriers.get(session.getId());
        if (state != null) {
            synchronized (state) {
                ChunkSnapshotBarrier barrier = state.byChunk.get(
                        chunkKey(Math.floorDiv(x, Blocks.CHUNK_X), Math.floorDiv(z, Blocks.CHUNK_Z)));
                if (barrier != null && barrier.open) {
                    LinkedHashMap<Long, Object> queued = state.queuedBannerSemantics
                            .computeIfAbsent(barrier, ignored -> new LinkedHashMap<>());
                    long coordinate = ((long) x << 32) ^ (z & 0xffff_ffffL);
                    // One vertical column may contain several block entities, so mix y into the stable key.
                    coordinate = 31L * coordinate + y;
                    if (!queued.containsKey(coordinate)) {
                        if (state.queuedMessages >= MAX_BUFFERED_BLOCK_UPDATES_PER_SESSION
                                || state.queuedBlocks + 1 > MAX_BUFFERED_BLOCKS_PER_SESSION) {
                            barrier.failed = true;
                            state.queuedBannerSemantics.remove(barrier);
                            state.queuedMessages -= queued.size();
                            state.queuedBlocks -= queued.size();
                            return;
                        }
                        state.queuedMessages++;
                        state.queuedBlocks++;
                    }
                    queued.put(coordinate, message);
                    return;
                }
            }
        }
        sendJsonDirect(session, message, message.getClass().getSimpleName());
    }

    private boolean sendJsonDirect(WebSocketSession session, Object message, String messageType) {
        String json = serialize(message);
        if (json == null) {
            recoverSerializationFailure(session, messageType);
            return false;
        }
        return sendRaw(session, json, messageType);
    }

    /** executor 포화/codec 예외/closed session 시 buffered update 없이 barrier를 해제합니다. */
    public void cancelChunkSnapshotBarrier(ChunkSnapshotBarrier barrier) {
        if (barrier == null) return;
        SnapshotBarrierSession state = snapshotBarriers.get(barrier.sessionId);
        if (state == null) return;
        synchronized (state) {
            if (state.byChunk.get(barrier.chunkKey) == barrier) {
                removeBarrierLocked(state, barrier);
            }
        }
    }

    /** Cancels an in-flight snapshot by the same session/chunk identity used by the client request. */
    public void cancelChunkSnapshotBarrier(WebSocketSession session, int chunkX, int chunkZ) {
        if (session == null) return;
        SnapshotBarrierSession state = snapshotBarriers.get(session.getId());
        if (state == null) return;
        synchronized (state) {
            ChunkSnapshotBarrier barrier = state.byChunk.get(chunkKey(chunkX, chunkZ));
            if (barrier != null) removeBarrierLocked(state, barrier);
        }
    }

    private boolean snapshotBarrierOpen(SnapshotBarrierSession state,
            ChunkSnapshotBarrier barrier, WebSocketSession session) {
        synchronized (state) {
            if (state.byChunk.get(barrier.chunkKey) == barrier && barrier.open
                    && !barrier.failed && session.isOpen()) {
                return true;
            }
            removeBarrierLocked(state, barrier);
            return false;
        }
    }

    private void sendBlockUpdate(WebSocketSession session, BlockUpdate message) {
        if (!session.isOpen()) return;
        SnapshotBarrierSession state = snapshotBarriers.get(session.getId());
        if (state == null) {
            sendBlockUpdateDirect(session, message);
            return;
        }
        BlockUpdate immediateUpdate;
        synchronized (state) {
            if (state.byChunk.isEmpty()) {
                immediateUpdate = message;
            } else {
                List<Block> immediate = new ArrayList<>(message.getBlocks().size());
                Map<ChunkSnapshotBarrier, List<Block>> delayed = new LinkedHashMap<>();
                for (Block block : message.getBlocks()) {
                    ChunkSnapshotBarrier barrier = state.byChunk.get(chunkKeyForBlock(block));
                    if (barrier == null || !barrier.open) {
                        immediate.add(block);
                    } else if (block.getRevision() > barrier.snapshotVersion) {
                        delayed.computeIfAbsent(barrier, ignored -> new ArrayList<>()).add(block);
                    }
                    // revision <= V는 binary snapshot에 이미 포함되어 있으므로 replay하지 않는다.
                }
                for (Map.Entry<ChunkSnapshotBarrier, List<Block>> entry : delayed.entrySet()) {
                    queueAfterSnapshot(state, entry.getKey(), entry.getValue());
                }
                immediateUpdate = immediate.isEmpty() ? null : new BlockUpdate(List.copyOf(immediate));
            }
        }
        if (immediateUpdate != null) {
            sendBlockUpdateDirect(session, immediateUpdate);
        }
    }

    private void queueAfterSnapshot(SnapshotBarrierSession state, ChunkSnapshotBarrier barrier,
            List<Block> blocks) {
        if (barrier.failed) return;
        if (state.queuedMessages >= MAX_BUFFERED_BLOCK_UPDATES_PER_SESSION
                || state.queuedBlocks + blocks.size() > MAX_BUFFERED_BLOCKS_PER_SESSION) {
            barrier.failed = true;
            List<BufferedBlockUpdate> dropped = state.queued.remove(barrier);
            if (dropped != null) {
                for (BufferedBlockUpdate update : dropped) {
                    state.queuedMessages--;
                    state.queuedBlocks -= update.message.getBlocks().size();
                }
            }
            return;
        }
        state.queued.computeIfAbsent(barrier, ignored -> new ArrayList<>())
                .add(new BufferedBlockUpdate(new BlockUpdate(List.copyOf(blocks))));
        state.queuedMessages++;
        state.queuedBlocks += blocks.size();
    }

    private boolean sendBlockUpdateDirect(WebSocketSession session, BlockUpdate message) {
        String json = serialize(message);
        if (json == null) {
            recoverSerializationFailure(session, "BlockUpdate");
            return false;
        }
        return sendRaw(session, json, "BlockUpdate");
    }

    private void recoverSerializationFailure(WebSocketSession session, String messageType) {
        WebSocketSession target = sendSessions.get(session.getId());
        if (markSessionUnreliable(session, target)) {
            log.warn("메시지 직렬화 실패로 세션 재동기화: type={} session={}",
                    messageType, session.getId());
        }
    }

    private void removeBarrierLocked(SnapshotBarrierSession state, ChunkSnapshotBarrier barrier) {
        barrier.open = false;
        state.byChunk.remove(barrier.chunkKey, barrier);
        List<BufferedBlockUpdate> dropped = state.queued.remove(barrier);
        if (dropped != null) {
            for (BufferedBlockUpdate update : dropped) {
                state.queuedMessages--;
                state.queuedBlocks -= update.message.getBlocks().size();
            }
        }
        LinkedHashMap<Long, Object> droppedBanners = state.queuedBannerSemantics.remove(barrier);
        if (droppedBanners != null) {
            state.queuedMessages -= droppedBanners.size();
            state.queuedBlocks -= droppedBanners.size();
        }
        // Keep the empty per-session state mapped until forget()/unreliable cleanup. Otherwise a
        // concurrent register can obtain this state just before the last removal, block on its
        // monitor, and then install a new barrier into an orphan that is no longer map-reachable.
    }

    private List<Object> takeQueuedBannerSemanticsLocked(
            SnapshotBarrierSession state, ChunkSnapshotBarrier barrier) {
        LinkedHashMap<Long, Object> queued = state.queuedBannerSemantics.remove(barrier);
        if (queued == null) return null;
        state.queuedMessages -= queued.size();
        state.queuedBlocks -= queued.size();
        return List.copyOf(queued.values());
    }

    private List<BufferedBlockUpdate> takeQueuedLocked(SnapshotBarrierSession state,
            ChunkSnapshotBarrier barrier) {
        List<BufferedBlockUpdate> queued = state.queued.remove(barrier);
        if (queued != null) {
            for (BufferedBlockUpdate update : queued) {
                state.queuedMessages--;
                state.queuedBlocks -= update.message.getBlocks().size();
            }
        }
        return queued;
    }

    private static long chunkKeyForBlock(Block block) {
        return chunkKey(Math.floorDiv(block.getX(), Blocks.CHUNK_X),
                Math.floorDiv(block.getZ(), Blocks.CHUNK_Z));
    }

    private static long chunkKey(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) ^ (chunkZ & 0xffff_ffffL);
    }

    /** 메시지를 JSON 문자열로 1회 직렬화합니다(월드 브로드캐스트는 세션별로 동일 페이로드). */
    private String serialize(Object message) {
        try {
            return objectMapper.writeValueAsString(message);
        } catch (RuntimeException ignored) {
            // 부분/누락 상태를 정상 전송으로 오인하지 않도록 호출 경로가 대상 세션을 닫아
            // 재접속과 권위 snapshot 재동기화를 강제한다.
            return null;
        }
    }

    /** 이미 직렬화된 JSON(불변)을 한 세션으로 전송합니다. 여러 세션에 그대로 재사용 안전. */
    private boolean sendRaw(WebSocketSession session, String json, String messageType) {
        if (!session.isOpen() || unreliableSessions.contains(session.getId())) {
            return false;
        }
        WebSocketSession target = sendSessions.get(session.getId());
        if (!isActiveSendTarget(session, target)) {
            return false;
        }
        return sendFrame(session, target, new TextMessage(json), messageType);
    }

    private boolean sendGeneratedFrame(WebSocketSession session, String json,
            String messageType, long generatedSequence) {
        if (!session.isOpen() || unreliableSessions.contains(session.getId())) return false;
        WebSocketSession target = sendSessions.get(session.getId());
        if (!isActiveSendTarget(session, target)) return false;
        return sendFrame(session, target, new TextMessage(json), messageType, generatedSequence);
    }

    private boolean sendFrame(WebSocketSession session, WebSocketSession target,
            WebSocketMessage<?> message, String messageType) {
        return sendFrame(session, target, message, messageType, null);
    }

    private boolean sendFrame(WebSocketSession session, WebSocketSession target,
            WebSocketMessage<?> message, String messageType, Long generatedSequence) {
        WelcomeBarrierSession state = welcomeBarriers.get(session.getId());
        boolean overflow = false;
        if (state != null) {
            synchronized (state) {
                if (welcomeBarriers.get(session.getId()) != state || state.owner != session) {
                    return false;
                }
                if (state.phase == WelcomeBarrierPhase.FAILED) return false;
                if (state.phase == WelcomeBarrierPhase.RELEASED
                        && generatedSequence != null
                        && state.releasedGeneratedSequence >= 0L
                        && generatedSequence <= state.releasedGeneratedSequence) {
                    return true;
                }
                if (state.phase != WelcomeBarrierPhase.RELEASED) {
                    int bytes = message.getPayloadLength();
                    if (state.pending.size() >= MAX_BUFFERED_PRE_WELCOME_MESSAGES
                            || state.pendingBytes + bytes > MAX_BUFFERED_PRE_WELCOME_BYTES) {
                        state.phase = WelcomeBarrierPhase.FAILED;
                        state.pending.clear();
                        state.pendingBytes = 0;
                        overflow = true;
                    } else {
                        state.pending.addLast(new PendingWelcomeFrame(
                                message, messageType, bytes, generatedSequence));
                        state.pendingBytes += bytes;
                        return true;
                    }
                }
            }
        }
        if (overflow) {
            if (markSessionUnreliable(session, target)) {
                log.warn("Welcome 대기 송신 큐 초과로 세션 재동기화: session={}", session.getId());
            }
            return false;
        }
        return sendFrameDirect(session, target, message, messageType);
    }

    private boolean sendFrameDirect(WebSocketSession session, WebSocketSession target,
            WebSocketMessage<?> message, String messageType) {
        if (!isActiveSendTarget(session, target)
                || unreliableSessions.contains(session.getId())) {
            return false;
        }
        try {
            target.sendMessage(message);
            return true;
        } catch (IOException | RuntimeException failure) {
            if (!isActiveSendTarget(session, target)) return false;
            if (markSessionUnreliable(session, target)) {
                log.warn("메시지 송신 실패로 세션 재동기화: type={} session={} cause={}: {}",
                        messageType, session.getId(),
                        failure.getClass().getSimpleName(), failure.getMessage());
            }
            return false;
        }
    }

    /**
     * ConcurrentWebSocketSessionDecorator becomes permanently non-sending after a time/buffer limit failure.
     * Closing it makes the existing client reconnect and establish fresh authoritative snapshot cursors instead
     * of treating subsequent no-op sends as success and leaving a visually incomplete world.
     */
    private boolean markSessionUnreliable(WebSocketSession session, WebSocketSession target) {
        if (!isActiveSendTarget(session, target)) return false;
        String sessionId = session.getId();
        if (!unreliableSessions.add(sessionId)) return false;
        if (!isActiveSendTarget(session, target)) {
            unreliableSessions.remove(sessionId);
            return false;
        }
        snapshotBarriers.remove(sessionId);
        welcomeBarriers.remove(sessionId);
        try {
            target.close(CloseStatus.SESSION_NOT_RELIABLE);
        } catch (IOException | RuntimeException closeFailure) {
            log.warn("신뢰할 수 없는 세션 종료 실패: session={} cause={}: {}",
                    sessionId, closeFailure.getClass().getSimpleName(), closeFailure.getMessage());
        }
        return true;
    }

    /**
     * Registered transports must still belong to the same raw session. A missing registration is terminal:
     * falling back to the raw session after {@link #forget(WebSocketSession)} would bypass Spring's serialized
     * sender and let an already queued message race another WebSocket write.
     */
    private boolean isActiveSendTarget(WebSocketSession session, WebSocketSession target) {
        if (target == null || !session.isOpen() || !target.isOpen()) return false;
        String sessionId = session.getId();
        WebSocketSession owner = sendSessionOwners.get(sessionId);
        return owner == session && sendSessions.get(sessionId) == target;
    }

    /** caller가 소유하지 않는 세션·청크 barrier token입니다. */
    public static final class ChunkSnapshotBarrier {
        private final Long worldId;
        private final String sessionId;
        private final long chunkKey;
        private final long snapshotVersion;
        private boolean open = true;
        private boolean failed;

        private ChunkSnapshotBarrier(Long worldId, String sessionId,
                long chunkKey, long snapshotVersion) {
            this.worldId = worldId;
            this.sessionId = sessionId;
            this.chunkKey = chunkKey;
            this.snapshotVersion = snapshotVersion;
        }
    }

    /** 하나의 WebSocket session에서 동시에 전송 중인 청크 barrier들의 bounded 상태입니다. */
    private static final class SnapshotBarrierSession {
        private final Map<Long, ChunkSnapshotBarrier> byChunk = new HashMap<>();
        private final Map<ChunkSnapshotBarrier, List<BufferedBlockUpdate>> queued = new HashMap<>();
        private final Map<ChunkSnapshotBarrier, LinkedHashMap<Long, Object>> queuedBannerSemantics =
                new HashMap<>();
        private int queuedMessages;
        private int queuedBlocks;
    }

    private static final class WelcomeBarrierSession {
        private final WebSocketSession owner;
        private final ArrayDeque<PendingWelcomeFrame> pending = new ArrayDeque<>();
        private int pendingBytes;
        private long releasedGeneratedSequence = -1L;
        private WelcomeBarrierPhase phase = WelcomeBarrierPhase.WAITING;

        private WelcomeBarrierSession(WebSocketSession owner) {
            this.owner = owner;
        }
    }

    private enum WelcomeBarrierPhase {
        WAITING,
        DRAINING,
        RELEASED,
        FAILED
    }

    private static final class PendingWelcomeFrame {
        private final WebSocketMessage<?> message;
        private final String messageType;
        private final int bytes;
        private final Long generatedSequence;

        private PendingWelcomeFrame(WebSocketMessage<?> message, String messageType, int bytes,
                Long generatedSequence) {
            this.message = message;
            this.messageType = messageType;
            this.bytes = bytes;
            this.generatedSequence = generatedSequence;
        }
    }

    private static final class BufferedBlockUpdate {
        private final BlockUpdate message;

        private BufferedBlockUpdate(BlockUpdate message) {
            this.message = message;
        }
    }

    private TickOutboundQueue tickOutbound(Long worldId) {
        return tickOutboundByWorld.computeIfAbsent(worldId, TickOutboundQueue::new);
    }

    /**
     * Serializes tick-originated calls for one world. The queue itself is bounded; on saturation a
     * matching block update is coalesced by coordinate, otherwise the caller receives {@code false}
     * and must retain/retry. WorldTickLoop keeps its block-change map until this handoff succeeds.
     */
    private final class TickOutboundQueue {
        private final Long worldId;
        private final ArrayDeque<TickOutboundMessage> pending = new ArrayDeque<>();
        /** Identity retains the first recipient snapshot across a producer's false/retry cycle. */
        private final IdentityHashMap<Object, CapturedRecipientsTickOutbound>
                retainedGeneratedAttempts = new IdentityHashMap<>();
        private final Set<WebSocketSession> directRecoverySessions = new HashSet<>();
        private int pendingBlocks;
        private boolean scheduled;
        private boolean disposed;
        private LiveJournalRecoveryTickOutbound liveJournalRecovery;

        private TickOutboundQueue(Long worldId) {
            this.worldId = worldId;
        }

        private boolean enqueue(TickOutboundMessage message, boolean recoverOnReject) {
            boolean submit;
            synchronized (pending) {
                if (disposed) return false;
                int blocks = message.blockCount();
                if (pendingBlocks + blocks > MAX_PENDING_TICK_OUTBOUND_BLOCKS_PER_WORLD) {
                    if (recoverOnReject) requireRecovery(message);
                    return false;
                }
                if (pending.size() >= MAX_PENDING_TICK_OUTBOUND_PER_WORLD) {
                    boolean merged = mergeBlockUpdate(message);
                    if (merged) pendingBlocks += blocks;
                    else if (recoverOnReject) requireRecovery(message);
                    return merged;
                }
                pending.addLast(message);
                pendingBlocks += blocks;
                submit = !scheduled;
                if (submit) scheduled = true;
            }
            if (submit) submitDrain();
            return true;
        }

        private boolean mergeBlockUpdate(TickOutboundMessage incoming) {
            if (!incoming.isBlockUpdate()) return false;
            // Only the FIFO tail may absorb an update. Merging into an earlier envelope would move
            // blocks across intervening sound/health/entity events and violate the world sequence.
            TickOutboundMessage tail = pending.peekLast();
            return tail != null && tail.mergeBlockUpdate(incoming);
        }

        private boolean enqueueRetainedGenerated(Object message, long generatedSequence,
                java.util.function.Supplier<List<WebSocketSession>> firstAttemptRecipients) {
            CapturedRecipientsTickOutbound retained;
            synchronized (pending) {
                retained = retainedGeneratedAttempts.get(message);
                if (retained != null && retained.generatedSequence != generatedSequence) {
                    throw new IllegalStateException("generated retry sequence changed");
                }
            }
            // Registry lookup is deliberately lazy: an already captured false/retry envelope must
            // neither pay for nor observe a later recipient set.
            List<WebSocketSession> captured = retained == null
                    ? firstAttemptRecipients.get() : null;
            boolean submit;
            synchronized (pending) {
                if (disposed) return false;
                CapturedRecipientsTickOutbound outbound = retainedGeneratedAttempts.get(message);
                if (outbound == null) {
                    outbound = new CapturedRecipientsTickOutbound(
                            message, generatedSequence, captured);
                    retainedGeneratedAttempts.put(message, outbound);
                } else if (outbound.generatedSequence != generatedSequence) {
                    throw new IllegalStateException("generated retry sequence changed");
                }
                if (outbound.recipients.isEmpty()) {
                    retainedGeneratedAttempts.remove(message);
                    return true;
                }
                if (pending.size() >= MAX_PENDING_TICK_OUTBOUND_PER_WORLD) return false;
                retainedGeneratedAttempts.remove(message);
                pending.addLast(outbound);
                submit = !scheduled;
                if (submit) scheduled = true;
            }
            if (submit) submitDrain();
            return true;
        }

        private boolean enqueueLiveJournalRecovery() {
            List<WebSocketSession> recipients = new ArrayList<>();
            for (SessionRegistry.Entry entry : registry.entries(worldId)) {
                WebSocketSession session = entry.session();
                if (isActiveSendTarget(session, sendSessions.get(session.getId()))) {
                    recipients.add(session);
                }
            }
            boolean submit = false;
            synchronized (pending) {
                if (disposed) return false;
                if (!recipients.isEmpty()) {
                    if (liveJournalRecovery == null) {
                        liveJournalRecovery = new LiveJournalRecoveryTickOutbound();
                        pending.addLast(liveJournalRecovery);
                    }
                    liveJournalRecovery.addRecipients(recipients);
                    submit = !scheduled;
                    if (submit) scheduled = true;
                }
            }
            if (submit) submitDrain();
            return true;
        }

        private void retry() {
            boolean submit;
            synchronized (pending) {
                submit = !disposed && (!pending.isEmpty()
                        || !directRecoverySessions.isEmpty()) && !scheduled;
                if (submit) scheduled = true;
            }
            if (submit) submitDrain();
        }

        private void requireRecovery(TickOutboundMessage message) {
            if (message instanceof DirectTickOutbound direct) {
                directRecoverySessions.add(direct.session);
            } else {
                // Capture the recipients that actually missed this message. Looking them up later in
                // drain() would also disconnect clients that joined after the saturated handoff.
                for (SessionRegistry.Entry entry : registry.entries(worldId)) {
                    directRecoverySessions.add(entry.session());
                }
            }
        }

        private void submitDrain() {
            try {
                TICK_OUTBOUND_SENDERS.execute(this::drain);
            } catch (RejectedExecutionException saturated) {
                // Retain the bounded FIFO for WorldRuntime's next-tick retry; never caller-runs.
                synchronized (pending) {
                    scheduled = false;
                }
            }
        }

        private void drain() {
            while (true) {
                TickOutboundMessage next;
                List<WebSocketSession> recoverDirectNow;
                synchronized (pending) {
                    if (disposed) {
                        scheduled = false;
                        return;
                    }
                    next = pending.pollFirst();
                    if (next == null) {
                        scheduled = false;
                        recoverDirectNow = List.copyOf(directRecoverySessions);
                        directRecoverySessions.clear();
                    } else {
                        if (next == liveJournalRecovery) liveJournalRecovery = null;
                        pendingBlocks -= next.blockCount();
                        recoverDirectNow = List.of();
                    }
                }
                if (next == null) {
                    for (WebSocketSession session : recoverDirectNow) {
                        recoverSaturatedSession(session);
                    }
                    return;
                }
                try {
                    next.send();
                } catch (Throwable failure) {
                    // 한 건의 전송 실패로 루프를 이탈하면 scheduled가 true로 고정돼 이 월드의 아웃바운드
                    // 큐가 영구 정지하고, 결국 상한 포화로 월드 전원이 재동기화 종료된다.
                    log.error("틱 아웃바운드 전송 실패: world={}, type={}",
                            worldId, next.getClass().getSimpleName(), failure);
                }
            }
        }

        private void dispose() {
            synchronized (pending) {
                disposed = true;
                pending.clear();
                pendingBlocks = 0;
                retainedGeneratedAttempts.clear();
                directRecoverySessions.clear();
                liveJournalRecovery = null;
            }
        }
    }

    private void recoverSaturatedSession(WebSocketSession session) {
        WebSocketSession target = sendSessions.get(session.getId());
        if (!isActiveSendTarget(session, target)) return;
        if (markSessionUnreliable(session, target)) {
            log.warn("틱 전송 큐 포화로 세션 재동기화: session={}", session.getId());
        }
    }

    /**
     * 청크 snapshot 전송이 상한을 넘겨 멈춘 세션을 재동기화 경로로 되돌립니다. 블로킹 소켓 쓰기는
     * 세션을 닫아야만 풀리므로, 호출자는 틱·송신 스레드가 아닌 곳에서 이 메서드를 실행해야 합니다.
     */
    public void recoverStalledSnapshotSession(WebSocketSession session) {
        if (session == null) return;
        WebSocketSession target = sendSessions.get(session.getId());
        if (!isActiveSendTarget(session, target)) return;
        if (markSessionUnreliable(session, target)) {
            log.warn("청크 snapshot 송신 정체로 세션 재동기화: session={}", session.getId());
        }
    }

    private void recoverLiveJournalSession(WebSocketSession session) {
        WebSocketSession target = sendSessions.get(session.getId());
        if (!isActiveSendTarget(session, target)) return;
        if (markSessionUnreliable(session, target)) {
            log.warn("블록 갱신 이력 초과로 세션 재동기화: session={}", session.getId());
        }
    }

    private abstract class TickOutboundMessage {
        private Object message;

        private TickOutboundMessage(Object message) {
            this.message = message;
        }

        private boolean isBlockUpdate() {
            return message instanceof BlockUpdate;
        }

        private int blockCount() {
            return message instanceof BlockUpdate update ? update.getBlocks().size() : 0;
        }

        private boolean mergeBlockUpdate(TickOutboundMessage incoming) {
            if (!sameDestination(incoming) || !isBlockUpdate() || !incoming.isBlockUpdate()) return false;
            BlockUpdate existing = (BlockUpdate) message;
            BlockUpdate next = (BlockUpdate) incoming.message;
            if (existing.getBlocks().size() + next.getBlocks().size()
                    > MAX_BLOCKS_PER_TICK_OUTBOUND_MESSAGE) {
                return false;
            }
            message = mergeBlockUpdates(existing, next);
            return true;
        }

        final Object message() {
            return message;
        }

        abstract boolean sameDestination(TickOutboundMessage other);

        abstract void send();
    }

    private final class BroadcastTickOutbound extends TickOutboundMessage {
        private final Long worldId;

        private BroadcastTickOutbound(Long worldId, Object message) {
            super(message);
            this.worldId = worldId;
        }

        @Override
        boolean sameDestination(TickOutboundMessage other) {
            return other instanceof BroadcastTickOutbound broadcast && worldId.equals(broadcast.worldId);
        }

        @Override
        void send() {
            broadcastNow(worldId, message());
        }
    }

    private final class DirectTickOutbound extends TickOutboundMessage {
        private final WebSocketSession session;

        private DirectTickOutbound(WebSocketSession session, Object message) {
            super(message);
            this.session = session;
        }

        @Override
        boolean sameDestination(TickOutboundMessage other) {
            return other instanceof DirectTickOutbound direct && session == direct.session;
        }

        @Override
        void send() {
            sendToNow(session, message());
        }
    }

    /** One immutable recipient snapshot; send never re-reads the world session registry. */
    private final class CapturedRecipientsTickOutbound extends TickOutboundMessage {
        private final long generatedSequence;
        private final List<WebSocketSession> recipients;

        private CapturedRecipientsTickOutbound(Object message, long generatedSequence,
                List<WebSocketSession> recipients) {
            super(message);
            this.generatedSequence = generatedSequence;
            this.recipients = List.copyOf(recipients);
        }

        @Override
        boolean sameDestination(TickOutboundMessage other) {
            return false;
        }

        @Override
        void send() {
            String messageType = message().getClass().getSimpleName();
            String json = serialize(message());
            if (json == null) {
                for (WebSocketSession session : recipients) {
                    recoverSerializationFailure(session, messageType);
                }
                return;
            }
            for (WebSocketSession session : recipients) {
                sendGeneratedFrame(session, json, messageType, generatedSequence);
            }
        }
    }

    private final class LiveJournalRecoveryTickOutbound extends TickOutboundMessage {
        private final Set<WebSocketSession> recipients =
                Collections.newSetFromMap(new IdentityHashMap<>());

        private LiveJournalRecoveryTickOutbound() {
            super(null);
        }

        private void addRecipients(List<WebSocketSession> additions) {
            recipients.addAll(additions);
        }

        @Override
        boolean sameDestination(TickOutboundMessage other) {
            return false;
        }

        @Override
        void send() {
            for (WebSocketSession session : recipients) {
                recoverLiveJournalSession(session);
            }
        }
    }

    private static BlockUpdate mergeBlockUpdates(BlockUpdate existing, BlockUpdate incoming) {
        List<Block> ordered = new ArrayList<>(
                existing.getBlocks().size() + incoming.getBlocks().size());
        ordered.addAll(existing.getBlocks());
        ordered.addAll(incoming.getBlocks());
        // Keep every revision in order. LWW coordinate compaction can erase the oldest revision and
        // make the derived fromVersion jump, forcing an otherwise unnecessary full snapshot.
        return new BlockUpdate(List.copyOf(ordered));
    }

}
