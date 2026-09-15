package com.gameexpert.engine;

import com.gameexpert.engine.inventory.InventoryRules;
import com.gameexpert.engine.inventory.PlayerInventory;
import com.gameexpert.terrain.mc.structure.Mc263StructureSetStartPlanner;
import com.gameexpert.ws.dto.WsMessages;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.random.RandomGenerator;

import lombok.extern.slf4j.Slf4j;

/**
 * 던진 엔더의 눈({@code EnderEyeItem#use} + {@code EyeOfEnder}) 한 월드분.
 *
 * <p>눈은 충돌·명중이 없는 순수 비행체라 {@code ProjectileSim} 의 충돌·영속 경로에 태우지 않고,
 * 투사체 프로토콜({@code projectileSpawn/Update/Remove}, 종류 {@code "eye_of_ender"})과 투사체 id
 * 공간만 공유한다. 비행 중인 눈은 영속하지 않는다 — 바닐라도 {@code target}·{@code life}·
 * {@code surviveAfterDeath} 를 저장하지 않아 재적재된 눈은 결국 깨지므로, 재시작으로 사라진
 * 눈은 "깨진 눈"과 같은 결과다(소비된 눈 한 개가 돌아오지 않는다).
 *
 * <p>권위 1 틱(10 TPS)에 바닐라 서버 틱 두 번을 돈다({@link EnderEyeFlight}).
 *
 * <p>링 128 개는 선호 바이옴 탐색을 포함해 약 1 초가 걸린다. 바닐라도
 * {@code ChunkGeneratorStructureState#generateRingPositions} 를 배경 실행기에서 돌리고
 * {@code getRingPositionsFor} 가 그 결과를 기다린다. 여기서는 틱 스레드를 멈추지 않도록 첫
 * 투척이 배경 계산을 시작하고, 계산이 끝난 틱에 보류된 투척을 같은 순서로 처리한다(첫 눈만
 * 그만큼 늦게 뜬다). 링은 월드 시드의 순수 함수라 한 번 계산하면 이 월드 수명 동안 쓴다.
 */
@Slf4j
final class EnderEyeSystem {
    static final String PROJECTILE_KIND =
            com.gameexpert.engine.mob.ProjectileSim.Kind.EYE_OF_ENDER.protocolName();
    static final String SOUND_LAUNCH = "ender_eye_launch";
    static final String SOUND_DEATH = "ender_eye_death";
    /** 바닐라 {@code level event 2003} 을 싣는 종결 사유(아이템으로 남으면 "expired"). */
    static final String REMOVE_SHATTERED = "shattered";
    static final String REMOVE_SURVIVED = "expired";
    /** 권위 1 틱이 재생하는 바닐라 서버 틱 수. */
    static final int MC_TICKS_PER_AUTHORITY_TICK = 2;
    private static final double PLAYER_HEIGHT = 1.8;
    private static final double CROUCHING_HEIGHT = 1.5;

    private final WorldRuntime rt;
    private final RandomGenerator random;
    private final Supplier<List<Mc263StructureSetStartPlanner.BlockPos>> ringSource;
    private final Executor ringExecutor;
    private final Consumer<PlayerTickState> inventoryChanged;
    private CompletableFuture<List<Mc263StructureSetStartPlanner.BlockPos>> rings;
    private final ArrayDeque<PendingThrow> pendingThrows = new ArrayDeque<>();
    private final Map<Long, EnderEyeFlight> eyes = new LinkedHashMap<>();

    private record PendingThrow(String nickname, PlayerInventory.Hand hand) { }

    EnderEyeSystem(WorldRuntime rt, RandomGenerator random,
            Consumer<PlayerTickState> inventoryChanged) {
        this(rt, random, () -> StrongholdLocator.ringChunks(rt.seed()),
                task -> Thread.ofPlatform().daemon().name("ender-eye-rings-" + rt.worldId())
                        .start(task),
                inventoryChanged);
    }

    EnderEyeSystem(WorldRuntime rt, RandomGenerator random,
            Supplier<List<Mc263StructureSetStartPlanner.BlockPos>> ringSource,
            Executor ringExecutor, Consumer<PlayerTickState> inventoryChanged) {
        this.rt = rt;
        this.random = random;
        this.ringSource = ringSource;
        this.ringExecutor = ringExecutor;
        this.inventoryChanged = inventoryChanged;
    }

    /** 링 계산이 끝났으면 그 결과, 아니면 null(계산을 시작해 둔다). 실패하면 빈 목록이다. */
    private List<Mc263StructureSetStartPlanner.BlockPos> readyRings() {
        if (rings == null) {
            rings = CompletableFuture.supplyAsync(() -> List.copyOf(ringSource.get()), ringExecutor)
                    .exceptionally(failure -> {
                        log.error("월드 {} 요새 링 계산이 실패해 엔더의 눈이 요새를 찾지 못합니다.",
                                rt.worldId(), failure);
                        return List.of();
                    });
        }
        return rings.isDone() ? rings.join() : null;
    }

    int liveCount() {
        return eyes.size();
    }

    int pendingThrowCount() {
        return pendingThrows.size();
    }

    /**
     * {@code EnderEyeItem#use} 의 서버 갈래. 링 계산이 아직이면 투척을 보류하고 false 를 돌려준다
     * (계산이 끝나는 틱에 {@link #tick()} 이 처리하고 인벤토리 갱신을 보낸다).
     *
     * @return 이번 호출에서 눈을 던졌으면 true
     */
    boolean throwEye(PlayerTickState player, PlayerInventory.HandRef hand) {
        if (player.inventory().stack(hand).itemType() != PlayerInventory.EYE_OF_ENDER) return false;
        // 오버월드 밖에는 EYE_OF_ENDER_LOCATED 구조물이 없다: findNearestMapStructure 가 null 이라
        // 바닐라는 눈을 쓰지 않고 CONSUME 만 돌려준다. 오버월드 시드의 링을 자식 차원에서 계산하지 않는다.
        if (rt.customDimension()) return false;
        List<Mc263StructureSetStartPlanner.BlockPos> ready = readyRings();
        if (ready == null || !pendingThrows.isEmpty()) {
            pendingThrows.add(new PendingThrow(player.nickname(), hand.hand()));
            return false;
        }
        return launch(player, hand, ready);
    }

    /**
     * 가까운 요새가 없으면 눈을 쓰지 않는다(바닐라 {@code InteractionResult.CONSUME} 이지만
     * {@code consume} 호출 전 반환).
     */
    private boolean launch(PlayerTickState player, PlayerInventory.HandRef hand,
            List<Mc263StructureSetStartPlanner.BlockPos> ready) {
        if (player.inventory().stack(hand).itemType() != PlayerInventory.EYE_OF_ENDER) return false;
        Mc263StructureSetStartPlanner.BlockPos located = StrongholdLocator.nearestLocatePos(
                ready, (int) Math.floor(player.x()), (int) Math.floor(player.y()),
                (int) Math.floor(player.z()));
        if (located == null) return false;
        if (!InventoryRules.consumeOne(player.inventory(), hand, PlayerInventory.EYE_OF_ENDER)) {
            return false;
        }
        double height = player.crouching() ? CROUCHING_HEIGHT : PLAYER_HEIGHT;
        EnderEyeFlight eye = EnderEyeFlight.signalTo(player.x(), player.y() + height * 0.5,
                player.z(), located.x(), located.y(), located.z(),
                random.nextInt(EnderEyeFlight.SURVIVE_BOUND));
        long id = rt.mobSystem().reserveProjectileId();
        eyes.put(id, eye);
        broadcast(new WsMessages.ProjectileSpawn(PROJECTILE_KIND, id, eye.x(), eye.y(), eye.z(),
                0.0, 0.0, 0.0, player.nickname(), null));
        broadcastSound(SOUND_LAUNCH, player.x(), player.y(), player.z());
        return true;
    }

    /** 살아 있는 눈을 바닐라 두 틱만큼 옮기고, 수명이 다한 눈을 떨어뜨리거나 깨뜨린다. */
    void tick() {
        drainPendingThrows();
        if (eyes.isEmpty()) return;
        List<WsMessages.ProjectilePos> positions = new ArrayList<>(eyes.size());
        List<WsMessages.ProjectileRemoval> removals = null;
        Iterator<Map.Entry<Long, EnderEyeFlight>> iterator = eyes.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Long, EnderEyeFlight> entry = iterator.next();
            EnderEyeFlight eye = entry.getValue();
            boolean died = false;
            for (int step = 0; step < MC_TICKS_PER_AUTHORITY_TICK && !died; step++) {
                died = eye.mcTick();
            }
            if (!died) {
                positions.add(new WsMessages.ProjectilePos(entry.getKey(), eye.x(), eye.y(), eye.z()));
                continue;
            }
            iterator.remove();
            broadcastSound(SOUND_DEATH, eye.x(), eye.y(), eye.z());
            if (eye.surviveAfterDeath()) {
                rt.itemSystem().spawnMobDrop(PlayerInventory.EYE_OF_ENDER, 1,
                        eye.x(), eye.y(), eye.z());
            }
            if (removals == null) removals = new ArrayList<>();
            removals.add(new WsMessages.ProjectileRemoval(entry.getKey(),
                    eye.surviveAfterDeath() ? REMOVE_SURVIVED : REMOVE_SHATTERED,
                    eye.x(), eye.y(), eye.z(), null));
        }
        if (!positions.isEmpty()) broadcast(new WsMessages.ProjectileUpdate(positions));
        if (removals != null) broadcast(new WsMessages.ProjectileRemove(removals));
    }

    private void drainPendingThrows() {
        if (pendingThrows.isEmpty()) return;
        List<Mc263StructureSetStartPlanner.BlockPos> ready = readyRings();
        if (ready == null) return;
        while (!pendingThrows.isEmpty()) {
            PendingThrow pending = pendingThrows.poll();
            PlayerTickState player = rt.players().get(pending.nickname());
            if (player == null || player.isDead()) continue;
            if (launch(player, player.inventory().capture(pending.hand()), ready)) {
                inventoryChanged.accept(player);
            }
        }
    }

    private void broadcast(Object message) {
        rt.ctx().broadcaster().enqueueBroadcastFromTick(rt.worldId(), message);
    }

    /** {@code Entity#playSound}/{@code Level#playSound}: 반경 안 플레이어에게만 보낸다. */
    private void broadcastSound(String kind, double x, double y, double z) {
        WsMessages.WorldSound message = new WsMessages.WorldSound(rt.nextEventId(), kind, x, y, z,
                PlayerInventory.EYE_OF_ENDER);
        double range = SoundRules.worldSoundRange(kind);
        for (PlayerTickState player : rt.players().values()) {
            if (!SoundRules.audible(x, y, z, player.x(), player.y(), player.z(), range)) continue;
            var session = rt.session(player.nickname());
            if (session != null) {
                rt.ctx().broadcaster().enqueueSendToFromTick(rt.worldId(), session, message);
            }
        }
    }
}
