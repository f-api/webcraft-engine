package com.gameexpert.state.service;

import com.gameexpert.state.entity.PlayerWorldState;
import com.gameexpert.state.entity.InventoryItem;
import com.gameexpert.state.repository.PlayerWorldStateRepository;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.gameexpert.common.NotFoundException;
import com.gameexpert.engine.inventory.ArmorSlot;
import com.gameexpert.engine.inventory.PlayerInventory;
import com.gameexpert.engine.ChestInventory;
import com.gameexpert.engine.effect.StatusEffects;
import com.gameexpert.api.persistence.PlayerAccess;
import com.gameexpert.api.persistence.PlayerStore;
import com.gameexpert.api.persistence.WorldAccess;
import com.gameexpert.api.persistence.WorldStore;

import lombok.RequiredArgsConstructor;
import com.gameexpert.state.service.inventory.PlayerGroundSettlementBaseline;
import com.gameexpert.state.service.inventory.PlayerInventoryMutationSnapshot;
import com.gameexpert.state.service.inventory.StaleInventoryMutationException;

/**
 * 플레이어-월드 상태를 다루는 서비스입니다.
 *
 * P4에서는 최초 접속 시 스폰 높이 계산을 담당합니다.
 * 위치·체력·인벤토리의 지속적인 권위 저장은 P5/P6 틱 엔진의 saveRuntime 이 담당합니다.
 */
@Service
@RequiredArgsConstructor
public class PlayerWorldStateService {
    private volatile DimensionTravelPersistence dimensionTravel;

    public synchronized void attachDimensionTravel(DimensionTravelPersistence travel) {
        if (travel == null || dimensionTravel != null && dimensionTravel != travel) throw new IllegalStateException("dimension persistence already attached");
        dimensionTravel = travel;
    }

    @Transactional
    public int recordStrawBedSleep(Long playerId, PlayerDimensionIdentity identity) {
        return requireDimension(playerId, identity).recordStrawBedSleep();
    }

    private PlayerWorldState stateFor(PlayerInventoryMutationSnapshot snapshot) {
        return requireDimension(snapshot.playerId(), snapshot.dimensionIdentity());
    }

    private PlayerWorldState requireDimension(Long playerId, PlayerDimensionIdentity identity) {
        if (dimensionTravel != null) return dimensionTravel.requireCurrent(playerId, identity);
        if (identity.rootWorldId() != identity.runtimeWorldId() || identity.travelRevision() != 0) {
            throw new IllegalStateException("dimension persistence unavailable");
        }
        return stateRepository.findLockedByPlayerIdAndWorldId(playerId, identity.rootWorldId())
                .orElseThrow(() -> new NotFoundException("PLAYER_WORLD_STATE_NOT_FOUND"));
    }

    private Long playerRoot(Long spatialWorldId) {
        return dimensionTravel == null ? spatialWorldId : dimensionTravel.rootFor(spatialWorldId);
    }

    @Transactional
    public PlayerWorldState findOrCreateDimension(Long playerId, PlayerDimensionIdentity identity,
            double spawnX, double spawnY, double spawnZ) {
        findOrCreate(playerId, identity.rootWorldId(), spawnX, spawnY, spawnZ);
        PlayerWorldState state = requireDimension(playerId, identity);
        remember(playerId, identity.rootWorldId(), InventorySnapshot.from(state));
        state.statusEffectsSnapshot();
        return state;
    }


    /** 런타임 체크포인트가 DB에 반영됐는지, 더 최신 원자 저장에 의해 대체됐는지 나타낸다. */
    public enum RuntimeSaveOutcome { COMMITTED, STALE }

    private final PlayerWorldStateRepository stateRepository;
    private final PlayerStore playerRepository;
    private final WorldStore worldRepository;
    /**
     * 월드 → (플레이어 → 마지막 저장 인벤토리) 런타임 캐시입니다. 저장 생략 판정에만 쓰는 메모리
     * 사본이라, 퇴장 저장이 끝나거나 월드가 삭제되면 반드시 비워야 합니다(비우지 않으면 접속했던
     * (플레이어, 월드) 조합마다 배열 7개가 프로세스 수명 내내 남습니다).
     */
    private final Map<Long, Map<Long, InventorySnapshot>> runtimeInventories = new ConcurrentHashMap<>();

    /** 외부 조정 서비스가 연 트랜잭션에 플레이어의 완전한 스냅샷을 합류시킨다. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void replaceExactSnapshotJoiningTransaction(PlayerInventoryMutationSnapshot snapshot) {
        PlayerWorldState state = stateFor(snapshot);
        if (!state.beginInventoryPersistenceRevision(snapshot.revision())) {
            throw new StaleInventoryMutationException("player");
        }
        state.updatePosition(snapshot.x(), snapshot.y(), snapshot.z(), snapshot.yaw(), snapshot.pitch());
        state.updateHealth(snapshot.health());
        state.updateHunger(snapshot.hunger(), snapshot.saturationMilli());
        state.updateXpTotal(snapshot.xpTotal());
        state.updateEnchantSeed(snapshot.enchantSeed());
        state.updateTimeSinceRest(snapshot.timeSinceRestMcTicks());
        state.updateInventory(snapshot.itemTypes(), snapshot.counts(), snapshot.durabilities(),
                snapshot.enchantments(), snapshot.mapIds(), snapshot.shulkerIds(),
                snapshot.bucketMobData(), snapshot.itemComponentData(),
                snapshot.equippedTypes(), snapshot.equippedDurabilities(),
                snapshot.equippedEnchantments(), snapshot.equippedItemComponentData());
        state.updateOffhand(snapshot.offhand());
        ChestInventory.Snapshot ender = snapshot.enderChest();
        state.updateEnderChest(ender.itemTypes(), ender.counts(), ender.durabilities(),
                ender.enchantments(), ender.mapIds(), ender.shulkerIds(),
                ender.bucketMobData(), ender.itemComponentData());
        state.updateSpawn(snapshot.spawnX(), snapshot.spawnY(), snapshot.spawnZ());
        state.updateStatusEffects(snapshot.statusEffects());
        state.updateEffectClocks(snapshot.effectClocks());
        state.updateSelectedSlot(snapshot.selectedSlot());
        state.updateFireState(snapshot.fireTicks(), snapshot.fireDamageAccum());
        InventorySnapshot inventory = InventorySnapshot.from(snapshot.itemTypes(), snapshot.counts(),
                snapshot.durabilities(), snapshot.enchantments(), snapshot.mapIds(),
                snapshot.shulkerIds(), snapshot.bucketMobData(), snapshot.itemComponentData(),
                snapshot.equippedTypes(),
                snapshot.equippedDurabilities(), snapshot.equippedEnchantments(),
                snapshot.equippedItemComponentData(),
                snapshot.revision(), snapshot.offhand());
        rememberAfterCommit(snapshot.playerId(), snapshot.dimensionIdentity().rootWorldId(), inventory);
    }

    /**
     * Persists a player-only inventory mutation with the same exact revision CAS used by
     * multi-aggregate settlements. Runtime state must be installed only after COMMITTED.
     */
    @Transactional
    public RuntimeSaveOutcome replaceExactSnapshot(
            long expectedRevision, PlayerInventoryMutationSnapshot snapshot) {
        if (snapshot == null || expectedRevision < 0 || snapshot.revision() <= expectedRevision) {
            throw new IllegalArgumentException("invalid player inventory settlement");
        }
        PlayerWorldState state = stateFor(snapshot);
        if (state.getInventoryPersistenceRevision() != expectedRevision) {
            return RuntimeSaveOutcome.STALE;
        }
        replaceExactSnapshotJoiningTransaction(snapshot);
        return RuntimeSaveOutcome.COMMITTED;
    }

    /**
     * Commits a player-only mutation from the live runtime while preserving the durable CAS
     * boundary. The runtime can legitimately be ahead of the last asynchronous checkpoint (for
     * example, a selected-hotbar-slot change); a newer durable branch is still stale, but an older
     * durable baseline may advance directly to this complete current snapshot.
     */
    @Transactional
    public RuntimeSaveOutcome replaceRuntimeSnapshot(
            long expectedRevision, PlayerInventoryMutationSnapshot snapshot) {
        if (snapshot == null || expectedRevision < 0 || snapshot.revision() <= expectedRevision) {
            throw new IllegalArgumentException("invalid player runtime settlement");
        }
        PlayerWorldState state = stateFor(snapshot);
        if (state.getInventoryPersistenceRevision() > expectedRevision) {
            return RuntimeSaveOutcome.STALE;
        }
        replaceExactSnapshotJoiningTransaction(snapshot);
        return RuntimeSaveOutcome.COMMITTED;
    }

    /** 외부 정산이 같은 트랜잭션 안에서 CAS 기준 revision을 읽고 플레이어 행을 잠근다. */
    @Transactional(propagation = Propagation.MANDATORY)
    public long lockInventoryPersistenceRevisionJoiningTransaction(Long playerId, Long worldId) {
        worldId = playerRoot(worldId);
        return stateRepository.findLockedByPlayerIdAndWorldId(playerId, worldId)
                .orElseThrow(() -> new NotFoundException("PLAYER_WORLD_STATE_NOT_FOUND"))
                .getInventoryPersistenceRevision();
    }

    /**
     * 외부 지상 정산이 같은 트랜잭션에서 플레이어 행을 잠그고 정확한 비교 기준을 읽는다.
     * 반환값은 JPA 컬렉션이나 내부 배열을 노출하지 않는 불변 슬롯별 스냅샷이다.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public PlayerGroundSettlementBaseline lockGroundSettlementBaselineJoiningTransaction(
            Long playerId, Long worldId) {
        worldId = playerRoot(worldId);
        PlayerWorldState state = stateRepository.findLockedByPlayerIdAndWorldId(playerId, worldId)
                .orElseThrow(() -> new NotFoundException("PLAYER_WORLD_STATE_NOT_FOUND"));
        InventorySnapshot inventory = InventorySnapshot.from(state);
        return new PlayerGroundSettlementBaseline(
                state.getInventoryPersistenceRevision(), inventory.mainSlots(),
                inventory.equippedSlots(), inventory.offhand, state.xpTotalOrZero());
    }

    /**
     * (플레이어, 월드) 상태를 찾아 돌려주고, 없으면 스폰 기본값으로 새로 만들어 저장합니다.
     * 플레이어나 월드 자체가 없으면 404 로 알려 줍니다.
     */
    @Transactional
    public PlayerWorldState findOrCreate(Long playerId, Long worldId) {
        if (!playerRoot(worldId).equals(worldId)) throw new IllegalArgumentException("child state requires an explicit dimension identity");
        PlayerWorldState state = stateRepository.findByPlayerIdAndWorldId(playerId, worldId)
                .orElseGet(() -> createDefaultState(playerId, worldId));
        state.statusEffectsSnapshot();
        return state;
    }

    /** P4 WebSocket 최초 접속용: 상태가 없으면 계산된 월드 스폰 좌표로 생성합니다. */
    @Transactional
    public PlayerWorldState findOrCreate(Long playerId, Long worldId,
            double spawnX, double spawnY, double spawnZ) {
        if (!playerRoot(worldId).equals(worldId)) throw new IllegalArgumentException("child state requires an explicit dimension identity");
        PlayerWorldState state = stateRepository.findByPlayerIdAndWorldId(playerId, worldId)
                .orElseGet(() -> createDefaultState(playerId, worldId, spawnX, spawnY, spawnZ));
        // 트랜잭션 안에서 스냅샷을 만들며 지연 로딩하고, 이후 welcome 인벤토리에도 재사용합니다.
        remember(playerId, worldId, InventorySnapshot.from(state));
        state.statusEffectsSnapshot();
        return state;
    }

    /**
     * P5/P6 틱 엔진용: 위치·시선과 함께 서버 권위 체력·인벤토리까지 저장합니다.
     * 300틱 주기 저장과 퇴장 저장에서 persistence executor 스레드가 호출합니다.
     *
     * <p>[SURV-X] 인챈트 마스크와 누적 경험치도 여기서 함께 씁니다. 이 메서드가 런타임의
     * <b>유일한</b> DB 쓰기 경로라, 인자에서 빠진 필드는 재접속 때 조용히 사라집니다.
     * 마스크는 {@link InventorySnapshot} 동등성에도 들어갑니다 — 제자리 인챈트는 개수·내구가
     * 그대로라, 마스크를 비교에서 빼면 "인벤토리 변화 없음"으로 판정되어 영영 저장되지 않습니다.
     */
    /**
     * [PHANTOM] 불면 시간을 싣지 않는 기존 호출부 호환. sentinel {@link #TIME_SINCE_REST_UNCHANGED}
     * 를 넘겨 컬럼을 건드리지 않습니다 — 0 을 넘기면 저장할 때마다 "방금 잔 것"으로 되감깁니다.
     */
    /** [PHANTOM] 이 값을 넘기면 불면 컬럼을 그대로 둡니다(구형 호출부 전용). */
    public static final long TIME_SINCE_REST_UNCHANGED = -1L;

    /** [ENDER-SHULKER] 엔더 상자 밴드를 넘기지 않는 구형 호출부 전용(그 밴드를 그대로 둔다). */
    /**
     * [ENDER-SHULKER] 엔더 상자 27칸까지 함께 저장한다. {@code enderChest} 가 null 이면 그
     * 밴드를 건드리지 않는다 — 인벤토리 밴드와 엔더 밴드는 같은 테이블을 쓰되 서로의 행을
     * 소유하지 않으므로, 한쪽만 저장해도 다른 쪽이 조용히 비워지지 않는다.
     */
    /**
     * [SHULKER-CONTENTS] 셜커 27칸 참조 ID 까지 함께 저장한다. 27칸 자체는 {@code shulker_contents}
     * 가 소유하므로 여기서는 참조 열 하나만 더 흐른다 — 지도({@code mapIds})가 낸 선례와 같다.
     */
    @Transactional
    public RuntimeSaveOutcome saveRuntime(Long playerId, Long worldId, long inventoryRevision,
            double x, double y, double z, float yaw, float pitch, int health,
            short[] itemTypes, int[] counts, int[] durabilities, long[] enchantments, int[] mapIds,
            int[] shulkerIds, String[] bucketMobData,
            String[] itemComponentData,
            short[] equippedTypes, int[] equippedDurabilities, long[] equippedEnchantments,
            PlayerInventory.StackSnapshot offhand,
            Integer spawnX, Integer spawnY, Integer spawnZ,
            int hunger, int saturationMilli, int xpTotal, int enchantSeed,
            long timeSinceRestMcTicks,
            com.gameexpert.engine.ChestInventory.Snapshot enderChest,
            List<StatusEffects.PersistentEffect> statusEffects,
            StatusEffects.PersistentPlayerEffectClocks effectClocks,
            int selectedSlot, int fireTicks, int fireDamageAccum) {
        return saveRuntime(playerId, worldId, inventoryRevision, x, y, z, yaw, pitch, health,
                itemTypes, counts, durabilities, enchantments, mapIds, shulkerIds,
                bucketMobData, itemComponentData, equippedTypes, equippedDurabilities,
                equippedEnchantments, new String[ArmorSlot.values().length], offhand,
                spawnX, spawnY, spawnZ, hunger, saturationMilli, xpTotal, enchantSeed,
                timeSinceRestMcTicks, enderChest, statusEffects, effectClocks, selectedSlot,
                fireTicks, fireDamageAccum);
    }

    @Transactional
public RuntimeSaveOutcome saveRuntime(Long playerId, Long worldId, long inventoryRevision,
            double x, double y, double z, float yaw, float pitch, int health,
            short[] itemTypes, int[] counts, int[] durabilities, long[] enchantments, int[] mapIds,
            int[] shulkerIds, String[] bucketMobData, String[] itemComponentData,
            short[] equippedTypes, int[] equippedDurabilities, long[] equippedEnchantments,
            String[] equippedItemComponentData,
            PlayerInventory.StackSnapshot offhand,
            Integer spawnX, Integer spawnY, Integer spawnZ,
            int hunger, int saturationMilli, int xpTotal, int enchantSeed,
            long timeSinceRestMcTicks,
            com.gameexpert.engine.ChestInventory.Snapshot enderChest,
            List<StatusEffects.PersistentEffect> statusEffects,
            StatusEffects.PersistentPlayerEffectClocks effectClocks,
            int selectedSlot, int fireTicks, int fireDamageAccum) {
        return saveDimensionRuntime(playerId, worldId, inventoryRevision, x, y, z, yaw, pitch, health, itemTypes, counts, durabilities, enchantments, mapIds, shulkerIds, bucketMobData, itemComponentData, equippedTypes, equippedDurabilities, equippedEnchantments, equippedItemComponentData, offhand, spawnX, spawnY, spawnZ, hunger, saturationMilli, xpTotal, enchantSeed, timeSinceRestMcTicks, enderChest, statusEffects, effectClocks, selectedSlot, fireTicks, fireDamageAccum,
                new PlayerDimensionIdentity(worldId, worldId, 0));
    }

    @Transactional
    public RuntimeSaveOutcome saveDimensionRuntime(Long playerId, Long worldId, long inventoryRevision,
            double x, double y, double z, float yaw, float pitch, int health,
            short[] itemTypes, int[] counts, int[] durabilities, long[] enchantments, int[] mapIds,
            int[] shulkerIds, String[] bucketMobData, String[] itemComponentData,
            short[] equippedTypes, int[] equippedDurabilities, long[] equippedEnchantments,
            String[] equippedItemComponentData,
            PlayerInventory.StackSnapshot offhand,
            Integer spawnX, Integer spawnY, Integer spawnZ,
            int hunger, int saturationMilli, int xpTotal, int enchantSeed,
            long timeSinceRestMcTicks,
            com.gameexpert.engine.ChestInventory.Snapshot enderChest,
            List<StatusEffects.PersistentEffect> statusEffects,
            StatusEffects.PersistentPlayerEffectClocks effectClocks,
            int selectedSlot, int fireTicks, int fireDamageAccum,
            PlayerDimensionIdentity identity) {
        if (identity.runtimeWorldId() != worldId) throw new IllegalArgumentException("spatial identity mismatch");

        if (inventoryRevision < 0 || inventoryRevision == Long.MAX_VALUE) {
            throw new IllegalArgumentException("runtime inventory revision is invalid");
        }
        PlayerWorldState state;
        try {
            state = requireDimension(playerId, identity);
        } catch (com.gameexpert.state.service.inventory.StaleInventoryMutationException stale) {
            return RuntimeSaveOutcome.STALE;
        }
        long persistedRevision = state.getInventoryPersistenceRevision();
        if (inventoryRevision < persistedRevision) {
            return RuntimeSaveOutcome.STALE;
        }
        InventorySnapshot next = InventorySnapshot.from(
                itemTypes, counts, durabilities, enchantments, mapIds, shulkerIds, bucketMobData,
                itemComponentData, equippedTypes, equippedDurabilities, equippedEnchantments,
                equippedItemComponentData,
                inventoryRevision, offhand);
        InventorySnapshot persistedInventory = InventorySnapshot.from(state);
        if (inventoryRevision == persistedRevision && !persistedInventory.equals(next)) {
            return RuntimeSaveOutcome.STALE;
        }
        if (inventoryRevision > persistedRevision
                && !state.beginInventoryPersistenceRevision(inventoryRevision)) {
            return RuntimeSaveOutcome.STALE;
        }

        state.updatePosition(x, y, z, yaw, pitch);
        state.updateHealth(health);
        state.updateHunger(hunger, saturationMilli);
        state.updateXpTotal(xpTotal);
        state.updateEnchantSeed(enchantSeed);
        if (timeSinceRestMcTicks != TIME_SINCE_REST_UNCHANGED) {
            state.updateTimeSinceRest(timeSinceRestMcTicks);
        }
        if (!persistedInventory.equals(next)) {
            state.updateInventory(itemTypes, counts, durabilities, enchantments, mapIds,
                    shulkerIds, bucketMobData, itemComponentData, equippedTypes,
                    equippedDurabilities, equippedEnchantments, equippedItemComponentData);
            state.updateOffhand(offhand);
        }
        rememberAfterCommit(playerId, identity.rootWorldId(), next);
        if (enderChest != null) {
            state.updateEnderChest(enderChest.itemTypes(), enderChest.counts(),
                    enderChest.durabilities(), enderChest.enchantments(), enderChest.mapIds(),
                    enderChest.shulkerIds(), enderChest.bucketMobData(),
                    enderChest.itemComponentData());
        }
        state.updateSpawn(spawnX, spawnY, spawnZ);
        state.updateStatusEffects(statusEffects);
        state.updateEffectClocks(effectClocks);
        if (selectedSlot >= 0) state.updateSelectedSlot(selectedSlot);
        if (fireTicks >= 0 && fireDamageAccum >= 0) {
            state.updateFireState(fireTicks, fireDamageAccum);
        }
        return RuntimeSaveOutcome.COMMITTED;
    }


    /**
     * 퇴장 저장이 커밋된 뒤 그 (플레이어, 월드) 런타임 캐시를 비웁니다.
     * 다시 들어오면 {@link #findOrCreate(Long, Long, double, double, double)} 가 새로 채웁니다.
     */
    public void evictRuntimeInventory(Long playerId, Long worldId) {
        worldId = playerRoot(worldId);
        runtimeInventories.computeIfPresent(worldId, (ignored, byPlayer) -> {
            byPlayer.remove(playerId);
            return byPlayer.isEmpty() ? null : byPlayer;
        });
    }

    /** 월드 삭제 경로에서 그 월드의 런타임 캐시를 통째로 비웁니다. */
    public void evictWorldRuntimeInventories(Long worldId) {
        runtimeInventories.remove(playerRoot(worldId));
    }

    private void rememberAfterCommit(Long playerId, Long worldId, InventorySnapshot snapshot) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            remember(playerId, worldId, snapshot);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                remember(playerId, worldId, snapshot);
            }
        });
    }

    private void remember(Long playerId, Long worldId, InventorySnapshot snapshot) {
        runtimeInventories
                .computeIfAbsent(worldId, ignored -> new ConcurrentHashMap<>())
                .put(playerId, snapshot);
    }

    private InventorySnapshot remembered(Long playerId, Long worldId) {
        Map<Long, InventorySnapshot> byPlayer = runtimeInventories.get(worldId);
        return byPlayer == null ? null : byPlayer.get(playerId);
    }

    /** persistence 계층 안에서만 보관하는 정확한 인벤토리 스냅샷입니다. */
    private static final class InventorySnapshot {
        private final short[] itemTypes;
        private final int[] counts;
        private final int[] durabilities;
        private final long[] enchantments;
        private final int[] mapIds;
        private final int[] shulkerIds;
        private final String[] bucketMobData;
        private final String[] itemComponentData;
        private final short[] equippedTypes;
        private final int[] equippedDurabilities;
        private final long[] equippedEnchantments;
        private final String[] equippedItemComponentData;
        private final PlayerInventory.StackSnapshot offhand;

        private InventorySnapshot(short[] itemTypes, int[] counts, int[] durabilities,
                long[] enchantments, int[] mapIds, int[] shulkerIds, String[] bucketMobData,
                String[] itemComponentData,
                short[] equippedTypes, int[] equippedDurabilities,
                long[] equippedEnchantments, String[] equippedItemComponentData,
                PlayerInventory.StackSnapshot offhand) {
            this.itemTypes = itemTypes;
            this.counts = counts;
            this.durabilities = durabilities;
            this.enchantments = enchantments;
            this.mapIds = mapIds;
            this.shulkerIds = shulkerIds;
            this.bucketMobData = bucketMobData;
            this.itemComponentData = itemComponentData;
            this.equippedTypes = equippedTypes;
            this.equippedDurabilities = equippedDurabilities;
            this.equippedEnchantments = equippedEnchantments;
            this.equippedItemComponentData = equippedItemComponentData;
            this.offhand = offhand;
        }

        private static InventorySnapshot from(short[] itemTypes, int[] counts, int[] durabilities,
                long[] enchantments, int[] mapIds, int[] shulkerIds, String[] bucketMobData,
                String[] itemComponentData,
                short[] equippedTypes, int[] equippedDurabilities,
                long[] equippedEnchantments, String[] equippedItemComponentData,
                long persistedRevision, PlayerInventory.StackSnapshot offhand) {
            new PlayerInventory(itemTypes, counts, durabilities, enchantments, mapIds, shulkerIds,
                    bucketMobData, itemComponentData,
                    equippedTypes, equippedDurabilities, equippedEnchantments,
                    equippedItemComponentData,
                    offhand, 0, persistedRevision);
            return new InventorySnapshot(
                    itemTypes.clone(), counts.clone(), durabilities.clone(), enchantments.clone(),
                    mapIds.clone(), shulkerIds.clone(), bucketMobData.clone(),
                    itemComponentData.clone(),
                    equippedTypes.clone(), equippedDurabilities.clone(),
                    equippedEnchantments.clone(), equippedItemComponentData.clone(), offhand);
        }

        private static InventorySnapshot from(PlayerWorldState state) {
            short[] itemTypes = new short[PlayerInventory.SLOTS];
            int[] counts = new int[PlayerInventory.SLOTS];
            int[] durabilities = new int[PlayerInventory.SLOTS];
            long[] enchantments = new long[PlayerInventory.SLOTS];
            int[] mapIds = new int[PlayerInventory.SLOTS];
            int[] shulkerIds = new int[PlayerInventory.SLOTS];
            String[] bucketMobData = new String[PlayerInventory.SLOTS];
            String[] itemComponentData = new String[PlayerInventory.SLOTS];
            short[] equippedTypes = new short[ArmorSlot.values().length];
            int[] equippedDurabilities = new int[ArmorSlot.values().length];
            long[] equippedEnchantments = new long[ArmorSlot.values().length];
            String[] equippedItemComponentData = new String[ArmorSlot.values().length];
            boolean[] occupied = new boolean[PlayerInventory.PERSISTED_SLOTS];
            for (InventoryItem item : state.getInventory()) {
                int slot = item.getSlot();
                // [ENDER-SHULKER] 엔더 상자 밴드는 이 스냅샷의 관심 밖이다. 같은 테이블을
                // 쓰지만 다른 밴드이고, 이 스냅샷은 "인벤토리가 바뀌었는가"만 판정한다 —
                // 거르지 않으면 엔더 상자에 아이템을 넣은 순간 여기서 예외로 터진다.
                if (slot >= PlayerInventory.ENDER_SLOT_BASE
                        && slot < PlayerInventory.PERSISTED_SLOTS_WITH_ENDER) {
                    continue;
                }
                if (slot == PlayerInventory.OFFHAND_SLOT) continue;
                if (slot < 0 || slot >= PlayerInventory.PERSISTED_SLOTS || occupied[slot]) {
                    throw new IllegalStateException("invalid or duplicate persisted inventory slot " + slot);
                }
                occupied[slot] = true;
                if (slot < PlayerInventory.SLOTS) {
                    itemTypes[slot] = item.getItemType();
                    counts[slot] = item.getItemCount();
                    durabilities[slot] = item.getDurability() == null ? 0 : item.getDurability();
                    enchantments[slot] = item.enchantmentMaskOrZero();
                    mapIds[slot] = item.mapIdOrZero();
                    shulkerIds[slot] = item.shulkerIdOrZero();
                    bucketMobData[slot] = item.getBucketMobData();
                    itemComponentData[slot] = item.getItemComponentData();
                } else {
                    int equipmentSlot = slot - PlayerInventory.EQUIPPED_SLOT_BASE;
                    if (item.getItemCount() != 1) {
                        throw new IllegalStateException("persisted equipped item count must be one");
                    }
                    equippedTypes[equipmentSlot] = item.getItemType();
                    equippedDurabilities[equipmentSlot] =
                            item.getDurability() == null ? 0 : item.getDurability();
                    equippedEnchantments[equipmentSlot] = item.enchantmentMaskOrZero();
                    equippedItemComponentData[equipmentSlot] = item.getItemComponentData();
                }
            }
            return from(itemTypes, counts, durabilities, enchantments, mapIds, shulkerIds,
                    bucketMobData, itemComponentData,
                    equippedTypes, equippedDurabilities, equippedEnchantments,
                    equippedItemComponentData,
                    state.getInventoryPersistenceRevision(), state.offhandSnapshot());
        }

        private List<PlayerInventory.StackSnapshot> mainSlots() {
            java.util.ArrayList<PlayerInventory.StackSnapshot> slots =
                    new java.util.ArrayList<>(PlayerInventory.SLOTS);
            for (int slot = 0; slot < PlayerInventory.SLOTS; slot++) {
                if (itemTypes[slot] == PlayerInventory.EMPTY) {
                    slots.add(PlayerInventory.StackSnapshot.EMPTY);
                } else {
                    slots.add(new PlayerInventory.StackSnapshot(
                            itemTypes[slot], counts[slot], durabilities[slot], enchantments[slot],
                            mapIds[slot], shulkerIds[slot], bucketMobData[slot],
                            itemComponentData[slot]));
                }
            }
            return List.copyOf(slots);
        }

        private List<PlayerInventory.StackSnapshot> equippedSlots() {
            java.util.ArrayList<PlayerInventory.StackSnapshot> slots =
                    new java.util.ArrayList<>(ArmorSlot.values().length);
            for (int slot = 0; slot < ArmorSlot.values().length; slot++) {
                if (equippedTypes[slot] == PlayerInventory.EMPTY) {
                    slots.add(PlayerInventory.StackSnapshot.EMPTY);
                } else {
                    slots.add(new PlayerInventory.StackSnapshot(
                            equippedTypes[slot], 1, equippedDurabilities[slot],
                            equippedEnchantments[slot], 0, 0, null,
                            equippedItemComponentData[slot]));
                }
            }
            return List.copyOf(slots);
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof InventorySnapshot snapshot)) return false;
            return Arrays.equals(itemTypes, snapshot.itemTypes)
                    && Arrays.equals(counts, snapshot.counts)
                    && Arrays.equals(durabilities, snapshot.durabilities)
                    && Arrays.equals(enchantments, snapshot.enchantments)
                    && Arrays.equals(mapIds, snapshot.mapIds)
                    && Arrays.equals(shulkerIds, snapshot.shulkerIds)
                    && Arrays.equals(bucketMobData, snapshot.bucketMobData)
                    && Arrays.equals(itemComponentData, snapshot.itemComponentData)
                    && Arrays.equals(equippedTypes, snapshot.equippedTypes)
                    && Arrays.equals(equippedDurabilities, snapshot.equippedDurabilities)
                    && Arrays.equals(equippedEnchantments, snapshot.equippedEnchantments)
                    && Arrays.equals(equippedItemComponentData,
                            snapshot.equippedItemComponentData)
                    && offhand.equals(snapshot.offhand);
        }

        @Override
        public int hashCode() {
            int result = Arrays.hashCode(itemTypes);
            result = 31 * result + Arrays.hashCode(counts);
            result = 31 * result + Arrays.hashCode(durabilities);
            result = 31 * result + Arrays.hashCode(enchantments);
            result = 31 * result + Arrays.hashCode(mapIds);
            result = 31 * result + Arrays.hashCode(shulkerIds);
            result = 31 * result + Arrays.hashCode(bucketMobData);
            result = 31 * result + Arrays.hashCode(itemComponentData);
            result = 31 * result + Arrays.hashCode(equippedTypes);
            result = 31 * result + Arrays.hashCode(equippedDurabilities);
            result = 31 * result + Arrays.hashCode(equippedEnchantments);
            result = 31 * result + Arrays.hashCode(equippedItemComponentData);
            return 31 * result + offhand.hashCode();
        }
    }

    private PlayerWorldState createDefaultState(Long playerId, Long worldId) {
        return createDefaultState(playerId, worldId, 0, 0, 0);
    }

    private PlayerWorldState createDefaultState(Long playerId, Long worldId,
            double spawnX, double spawnY, double spawnZ) {
        PlayerAccess player = playerRepository.findById(playerId)
                .orElseThrow(() -> new NotFoundException("PLAYER_NOT_FOUND"));
        WorldAccess world = worldRepository.findById(worldId)
                .orElseThrow(() -> new NotFoundException("WORLD_NOT_FOUND"));

        // 기본 상태: 지정된 월드 스폰 좌표 / 체력 20 / 빈 인벤토리
        PlayerWorldState state = new PlayerWorldState(player, world, spawnX, spawnY, spawnZ);
        return stateRepository.save(state);
    }
}
