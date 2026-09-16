package com.gameexpert.ws;

import com.gameexpert.api.SessionRegistry;
import static com.gameexpert.ws.SessionAttributes.ATTR_ERROR_CODE;
import static com.gameexpert.ws.SessionAttributes.ATTR_NICKNAME;
import static com.gameexpert.ws.SessionAttributes.ATTR_PLAYER_ID;
import static com.gameexpert.ws.SessionAttributes.ATTR_WORLD_ID;
import static com.gameexpert.ws.SessionAttributes.ATTR_WORLD_DIFFICULTY;
import static com.gameexpert.ws.SessionAttributes.ATTR_WORLD_SEED;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import com.gameexpert.config.EngineProperties;
import com.gameexpert.engine.Difficulty;
import com.gameexpert.engine.WorldEngineManager;
import com.gameexpert.engine.inventory.PlayerInventory;
import com.gameexpert.engine.inventory.ItemComponentCodec;
import com.gameexpert.engine.inventory.ItemComponentData;
import com.gameexpert.state.entity.InventoryItem;
import com.gameexpert.state.entity.PlayerWorldState;
import com.gameexpert.ws.dto.WsMessages.Error;
import com.gameexpert.ws.dto.WsMessages.EffectDto;
import com.gameexpert.ws.handler.ChunkSnapshotWsHandler;
import com.gameexpert.ws.dto.WsMessages.InventorySlot;
import com.gameexpert.ws.dto.WsMessages.BannerPatternLayer;
import com.gameexpert.ws.dto.WsMessages.BookComponent;
import com.gameexpert.ws.dto.WsMessages.PlayerJoin;
import com.gameexpert.ws.dto.WsMessages.Self;
import com.gameexpert.ws.dto.WsMessages.SpawnPoint;
import com.gameexpert.ws.dto.WsMessages.Welcome;

import lombok.RequiredArgsConstructor;

/** 연결 수명주기와 메시지 라우팅을 담당하는 게임 WebSocket 진입점입니다. */
@Component
@RequiredArgsConstructor
public class GameConnectionRuntime {

    private static final Logger log = LoggerFactory.getLogger(GameConnectionRuntime.class);

    private final SessionRegistry registry;
    private final GameTransport broadcaster;
    private final WorldEngineManager engineManager;
    private final EngineProperties properties;
    private final WorldSessionLifecycle sessionLifecycle;
    private final InboundRateLimiter rateLimiter;
    private volatile DimensionTravelCoordinator dimensions;

    public void attachDimensions(DimensionTravelCoordinator value) {
        if (dimensions != null) throw new IllegalStateException("dimension transport already attached");
        dimensions = java.util.Objects.requireNonNull(value);
    }

    public WebSocketSession open(WebSocketSession session) throws Exception {
        if (dimensions != null && !(session instanceof DimensionSession)) {
            return dimensions.open(session);
        }
        return session;
    }

    public void prepare(WebSocketSession session) {
        HeartbeatMonitor.awaitingWelcome(session);
        broadcaster.registerAwaitingWelcome(session);
    }

    public void reject(WebSocketSession session) {
        broadcaster.forget(session);
    }

    public void joined(WebSocketSession session, SessionRegistry.Entry entry) throws Exception {
        Long worldId = (Long) session.getAttributes().get(ATTR_WORLD_ID);
        Long playerId = (Long) session.getAttributes().get(ATTR_PLAYER_ID);
        Integer seed = (Integer) session.getAttributes().get(ATTR_WORLD_SEED);
        // 난이도는 월드 생성 시 고정되며, 런타임이 이미 있으면 런타임 값이 권위입니다(JoinResult).
        Difficulty difficulty = Difficulty.orDefault(
                (Difficulty) session.getAttributes().get(ATTR_WORLD_DIFFICULTY));
        String nickname = (String) session.getAttributes().get(ATTR_NICKNAME);
        boolean stageTrace = Boolean.getBoolean("webcraft.snapshotStageTrace");
        long joinStartNanos = stageTrace ? System.nanoTime() : 0L;
        long spawnNanos = 0L;
        long joinedNanos = 0L;

        try {
            // 원점이 바다일 수 있으므로(지형 v7 이후 심해 33.5%) 마른 땅을 찾아 스폰한다.
            // 그러지 않으면 시드에 따라 접속하자마자 물속에서 시작해 익사한다.
            int[] spawn = engineManager.worldSpawn(worldId, seed);
            if (stageTrace) spawnNanos = System.nanoTime();

            // 틱 엔진에 입장을 알리고(첫 입장이면 런타임 생성), 현재 월드 시간과 다른 접속자 pose를 받습니다.
            // 이 과정에서 저장 중인 직전 퇴장 스냅샷을 먼저 복원하고 해당 월드 diff 버퍼를 flush합니다.
            WorldEngineManager.JoinResult join =
                    session instanceof DimensionSession dimensionSession
                            ? engineManager.onDimensionJoin(dimensionSession.identity(), seed, difficulty,
                                    playerId, nickname, entry.connectionId(), spawn[0], spawn[1], spawn[2])
                            : engineManager.onPlayerJoin(worldId, seed, difficulty, playerId, nickname,
                                    entry.connectionId(), spawn[0], spawn[1], spawn[2]);
            PlayerWorldState state = join.selfState();
            if (stageTrace) joinedNanos = System.nanoTime();

            Welcome welcome = new Welcome(toSelf(state), join.worldTime(),
                    join.otherPlayers(), join.mobs(), join.items(), join.boats(), join.cushions(),
                    join.burningBlocks(), join.fireRevision(), join.weather(),
                    properties.qaInvulnerable(), join.dayCount(), join.campfires(),
                    join.difficulty(), join.xpOrbs(), join.primedTnt(),
                    join.projectiles(),
                    join.generatedEntities(),
                    // [QA5-3] 두 QA 피해 스위치를 함께 보고한다. godmode 만 켠 서버에서
                    // qaInvulnerable=false 만 보이면 "무적이 아닌데 안 죽는" 오진이 난다.
                    properties.qaGodmode())
                    .withGenerationProfile(engineManager.generationProfileFor(worldId));
            // A received welcome may immediately cause ready/chunk input on another WS thread.
            // Publish its entry coordinates first and serialize the wire boundary with inputReady.
            session.getAttributes().put(ChunkSnapshotWsHandler.ATTR_ENTRY_X, state.getPosX());
            session.getAttributes().put(ChunkSnapshotWsHandler.ATTR_ENTRY_Z, state.getPosZ());
            synchronized (session) {
                if (!broadcaster.sendWelcomeThenRelease(
                        session, dimensions == null ? welcome : dimensions.welcome(session, welcome),
                        join.generatedEntitySequence())) {
                    throw new IllegalStateException("Welcome 송신 경계를 열지 못했습니다.");
                }
                if (session instanceof DimensionSession dimensionSession) dimensionSession.welcomeComplete();
                HeartbeatMonitor.welcomeCompleted(session);
            }
            if (stageTrace) {
                long sentNanos = System.nanoTime();
                log.info("join stage: world={} nickname={} 스폰탐색+{}ms 런타임입장+{}ms welcome송신+{}ms",
                        worldId, nickname, (spawnNanos - joinStartNanos) / 1_000_000L,
                        (joinedNanos - joinStartNanos) / 1_000_000L,
                        (sentNanos - joinStartNanos) / 1_000_000L);
            }
            broadcaster.sendTo(session, new com.gameexpert.ws.dto.WsMessages.PlayerStatistics(
                    state.sleepInStrawBedOrZero()));
            // [MAPNAV] 나침반이 첫 죽음 전에도 스폰 지점을 알 수 있도록 welcome 직후 개인 전송한다.
            broadcaster.sendTo(session, session instanceof DimensionSession dimensionSession
                    && !"overworld".equals(dimensionSession.identity().dimension())
                    ? new SpawnPoint(spawn[0] + .5, spawn[1], spawn[2] + .5, false)
                    : spawnPointOf(state, spawn));
            var selectedMap = engineManager.selectedMapState(worldId, nickname);
            if (selectedMap != null) broadcaster.sendTo(session, selectedMap);
            for (var shelf : join.shelves()) broadcaster.sendTo(session, shelf);
            // 실제 3×3 준비·활성화·압축은 welcome 뒤 전용 워커와 world owner가 수행한다.
            // WS 스레드는 지형을 동기 생성하지 않아 클라이언트가 즉시 진짜 로딩 진행률을 표시할 수 있다.
            // 아직 틱 pose 가 없는 동안의 청크 스트리밍 범위 기준(ChunkSnapshotWsHandler).
            session.getAttributes().put(ChunkSnapshotWsHandler.ATTR_ENTRY_X, state.getPosX());
            session.getAttributes().put(ChunkSnapshotWsHandler.ATTR_ENTRY_Z, state.getPosZ());
            engineManager.enqueueSpawnChunkSnapshots(worldId, session, state.getPosX(), state.getPosZ());
            broadcaster.broadcastExcept(worldId, session,
                    new PlayerJoin(nickname));
        } catch (Exception exception) {
            // 로그를 남기지 않으면 접속만 조용히 끊겨 부하 문제와 구별되지 않습니다.
            log.error("접속 처리 실패: world={}, nickname={}", worldId, nickname, exception);
            sessionLifecycle.release(session);
            session.close(CloseStatus.SERVER_ERROR);
        }
    }

    public void receive(WebSocketSession session, TextMessage message,
            java.util.function.BiConsumer<WsMessageContext, String> dispatch) {
        if (dimensions != null) {
            session = dimensions.current(session);
            if (session == null || dimensions.consumeBarrier(session, message)) return;
        }
        Long worldId = (Long) session.getAttributes().get(ATTR_WORLD_ID);
        String nickname = (String) session.getAttributes().get(ATTR_NICKNAME);
        SessionRegistry.Entry current = worldId == null || nickname == null
                ? null : registry.get(worldId, nickname);
        if (current == null || current.session() != session) return;
        // 파싱·핸들러·액션 큐에 닿기 전에 세션 예산부터 확인합니다. 인증이 닉네임뿐이라
        // 한 세션의 폭주가 곧바로 힙·틱 스레드 고갈로 이어질 수 있습니다.
        InboundRateLimiter.Decision decision = rateLimiter.check(session);
        if (!decision.accepted()) {
            if (decision == InboundRateLimiter.Decision.REJECT_NOTIFY) {
                log.warn("인바운드 레이트리밋 초과: world={}, nickname={}", worldId, nickname);
                broadcaster.sendTo(session, new Error("RATE_LIMITED"));
            }
            return;
        }
        if (session instanceof DimensionSession dimensionSession) {
            synchronized (dimensionSession) {
                if (dimensionSession.inputReady()) {
                    HeartbeatMonitor.received(session, message.getPayload());
                    dispatch.accept(new WsMessageContext(worldId, nickname, session), message.getPayload());
                }
            }
        } else {
            synchronized (session) {
                HeartbeatMonitor.received(session, message.getPayload());
                dispatch.accept(new WsMessageContext(worldId, nickname, session), message.getPayload());
            }
        }
    }

    public void closed(WebSocketSession session) {
        if (dimensions == null) sessionLifecycle.release(session);
        else dimensions.close(session);
    }

    /** 저장된 침대 지점이 있으면 그 지점을, 없으면 월드 스폰을 나침반 목표로 돌려준다. */
    private static SpawnPoint spawnPointOf(PlayerWorldState state, int[] worldSpawn) {
        Integer bedX = state.getSpawnX();
        Integer bedY = state.getSpawnY();
        Integer bedZ = state.getSpawnZ();
        if (bedX != null && bedY != null && bedZ != null) {
            return new SpawnPoint(bedX + 0.5, bedY, bedZ + 0.5, true);
        }
        return new SpawnPoint(worldSpawn[0], worldSpawn[1], worldSpawn[2], false);
    }

    private Self toSelf(PlayerWorldState state) {
        var inventory = new ArrayList<InventorySlot>(36);
        for (int slot = 0; slot < 36; slot++) {
            InventoryItem found = null;
            for (InventoryItem item : state.getInventory()) {
                if (item.getSlot() == slot) {
                    found = item;
                    break;
                }
            }
            if (found == null) {
                inventory.add(inventorySlot(slot, (short) 0, 0, null, 0,
                        null, null, null, null));
            } else {
                inventory.add(inventorySlot(slot, found.getItemType(),
                        found.getItemCount(), durabilityOf(found),
                        found.enchantmentMaskOrZero(),
                        found.mapIdOrZero() == 0 ? null : found.mapIdOrZero(),
                        found.shulkerIdOrZero() == 0 ? null : found.shulkerIdOrZero(),
                        found.getBucketMobData(), found.getItemComponentData()));
            }
        }
        InventoryItem persistedOffhand = null;
        for (InventoryItem item : state.getInventory()) {
            if (item.getSlot() == PlayerInventory.OFFHAND_SLOT) {
                persistedOffhand = item;
                break;
            }
        }
        InventorySlot offhand = persistedOffhand == null
                ? inventorySlot(PlayerInventory.OFFHAND_INVENTORY_SLOT,
                        (short) 0, 0, null, 0, null, null, null, null)
                : inventorySlot(PlayerInventory.OFFHAND_INVENTORY_SLOT,
                        persistedOffhand.getItemType(),
                        persistedOffhand.getItemCount(), durabilityOf(persistedOffhand),
                        persistedOffhand.enchantmentMaskOrZero(),
                        persistedOffhand.mapIdOrZero() == 0 ? null : persistedOffhand.mapIdOrZero(),
                        persistedOffhand.shulkerIdOrZero() == 0
                                ? null : persistedOffhand.shulkerIdOrZero(),
                        persistedOffhand.getBucketMobData(),
                        persistedOffhand.getItemComponentData());
        return new Self(state.getPosX(), state.getPosY(), state.getPosZ(),
                state.getYaw(), state.getPitch(), state.getHealth(), 20, inventory,
                restoredArmor(state), "right", offhand,
                state.getSelectedSlot(), state.getFireTicks() > 0,
                restoredEffects(state), state.hungerOrFull(), state.saturationMilliOrDefault(),
                state.xpTotalOrZero(), restoredArmorPoints(state),
                state.effectClocksSnapshot().absorptionPoints());
    }

    private static List<InventorySlot> restoredArmor(PlayerWorldState state) {
        List<InventorySlot> armor = new ArrayList<>(4);
        for (int index = 0; index < 4; index++) {
            int persistedSlot = PlayerInventory.EQUIPPED_SLOT_BASE + index;
            InventoryItem found = null;
            for (InventoryItem item : state.getInventory()) {
                if (item.getSlot() == persistedSlot) {
                    found = item;
                    break;
                }
            }
            armor.add(found == null
                    ? inventorySlot(persistedSlot, (short) 0, 0, null, 0,
                            null, null, null, null)
                    : inventorySlot(persistedSlot, found.getItemType(), 1, durabilityOf(found),
                            found.enchantmentMaskOrZero(), null, null, null,
                            found.getItemComponentData()));
        }
        return List.copyOf(armor);
    }

    private static InventorySlot inventorySlot(int slot, short itemType, int count,
            Integer durability, long enchantments, Integer mapId, Integer shulkerId,
            String bucketMobData, String encodedComponents) {
        ItemComponentData components = ItemComponentCodec.decode(itemType, encodedComponents);
        List<BannerPatternLayer> patterns = components.bannerPatterns().stream()
                .map(layer -> new BannerPatternLayer(layer.pattern().wireName(), layer.color()))
                .toList();
        ItemComponentData.BookData bookData = components.book();
        BookComponent book = bookData == null ? null
                : new BookComponent(bookData.title(), bookData.author(), bookData.pages());
        return new InventorySlot(slot, itemType, count, durability,
                components.enchantments(enchantments), mapId, shulkerId,
                bucketMobData, components.customName(), patterns, book, components.anvilUseCount(),
                components.leatherColor(), components.suspiciousStewEffect(),
                components.suspiciousStewDurationMcTicks(),
                components.ominousBottleAmplifierComponent(), components.potionContents(),
                com.gameexpert.ws.dto.WsMessages.TrimComponent.of(components.trim()), components.potDecorations());
    }

    /** Persisted MC-tick precision is rounded up only at the 10 TPS wire boundary. */
    static List<EffectDto> restoredEffects(PlayerWorldState state) {
        return state.statusEffectsSnapshot().stream()
                .map(effect -> new EffectDto(
                        effect.effect().protocolName(),
                        effect.amplifier(),
                        (effect.remainingMcTicks() + 1) / 2,
                        effect.effect() == com.gameexpert.engine.effect.StatusEffect.CONDUIT_POWER
                                || effect.effect() == com.gameexpert.engine.effect.StatusEffect.NAUSEA))
                .toList();
    }

    /**
     * [HUD-VANILLA] 저장된 착용 방어구의 합산 방어 점수. 착용품은 인벤토리 목록에
     * {@code EQUIPPED_SLOT_BASE + 부위} 슬롯으로 접혀 있으므로 그 구간만 합산한다.
     * 흡수량은 별도 효과 시계에서 welcome 에 실린다.
     */
    private static int restoredArmorPoints(PlayerWorldState state) {
        int total = 0;
        for (InventoryItem item : state.getInventory()) {
            // [ENDER-SHULKER] 상한을 함께 본다. 엔더 상자 밴드(ENDER_SLOT_BASE 이상)가 같은
            // 목록에 붙으면서, "EQUIPPED_SLOT_BASE 이상이면 착용품"은 더 이상 참이 아니다 —
            // 상한이 없으면 엔더 상자에 넣어 둔 방어구가 welcome 방어 점수에 더해진다.
            if (item.getSlot() < PlayerInventory.EQUIPPED_SLOT_BASE
                    || item.getSlot() >= PlayerInventory.PERSISTED_SLOTS) {
                continue;
            }
            total += PlayerInventory.armorPoints(item.getItemType());
        }
        return total;
    }

    /** welcome에 실을 현재 내구도. 내구 아이템은 필수 정수이고 그 외 아이템은 null이다. */
    private static Integer durabilityOf(InventoryItem item) {
        short type = item.getItemType();
        Integer durability = item.getDurability();
        if (!com.gameexpert.engine.inventory.PlayerInventory.isDurable(type)) {
            if (durability != null) {
                throw new IllegalStateException("내구 없는 아이템에 저장 내구도가 있습니다: type="
                        + Short.toUnsignedInt(type));
            }
            return null;
        }
        if (durability == null || durability <= 0
                || durability > com.gameexpert.engine.inventory.PlayerInventory.initialDurability(type)) {
            throw new IllegalStateException("내구 아이템의 저장 내구도가 올바르지 않습니다: type="
                    + Short.toUnsignedInt(type) + ", durability=" + durability);
        }
        return durability;
    }
}
