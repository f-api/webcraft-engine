package com.gameexpert.state.entity;


import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.UpdateTimestamp;

import com.gameexpert.engine.HungerRules;
import com.gameexpert.engine.effect.StatusEffects;
import com.gameexpert.engine.inventory.ArmorSlot;
import com.gameexpert.engine.inventory.PlayerInventory;
import com.gameexpert.api.persistence.PlayerAccess;
import com.gameexpert.api.persistence.WorldAccess;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * "특정 플레이어가 특정 월드에서 어떤 상태인가"를 저장하는 엔티티입니다.
 * 테이블 {@code player_world_states} 에 매핑됩니다.
 *
 * 같은 (플레이어, 월드) 조합은 하나만 존재합니다(UNIQUE). 여기에 위치/시선/체력과
 * 핫바 인벤토리(@ElementCollection)를 담아, 다시 접속했을 때 이어서 플레이하게 합니다.
 */
@Getter
@Entity
@Table(
        name = "player_world_states",
        uniqueConstraints = @UniqueConstraint(name = "uq_player_world", columnNames = {"player_id", "world_id"})
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlayerWorldState {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "player_id", nullable = false)
    private PlayerAccess player;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "world_id", nullable = false)
    private WorldAccess world;

    // 위치(월드 절대 좌표)와 시선 방향
    private double posX;
    private double posY;
    private double posZ;
    private float yaw;
    private float pitch;

    // 체력 (최대 20, 계약 §4)
    @Column(nullable = false)
    private int health;

    // 침대 리스폰 지점(S2a). 침대 상호작용으로 설정되며, 미설정이면 null → 월드 스폰으로 리스폰.
    private Integer spawnX;
    private Integer spawnY;
    private Integer spawnZ;

    // 허기(SURV-H). 허기 도입 전 행에는 컬럼이 없으므로 nullable 로 두고, null 이면 만복 20 / saturation 5.0 으로 복원한다.
    private Integer hunger;
    /** saturation 을 1/1000 단위 정수로 저장한다(HungerRules.MILLI). 소수 저장으로 양판이 갈리지 않게 한다. */
    private Integer saturationMilli;

    // 누적 경험치(SURV-X). 허기와 같은 이유로 nullable 이며, null 이면 0(레벨 0)으로 복원한다.
    private Integer xpTotal;

    /**
     * 인챈트 제안 시드(SURV-X). 바닐라도 플레이어별로 보존하므로 접속마다 새로 뽑지 않는다 —
     * 재추첨하면 마음에 안 드는 제안이 뜰 때마다 나갔다 들어와 무한 리롤할 수 있다.
     * 도입 전 행에는 컬럼이 없으므로 nullable 이고, null 이면 접속 시 한 번 뽑는다.
     */
    private Integer enchantSeed;

    /**
     * [PHANTOM] 마지막 휴식 이후 경과 MC 틱(바닐라 {@code TIME_SINCE_REST}). 팬텀 불면 스폰이
     * 유일한 소비자다. 도입 전 행에는 컬럼이 없으므로 nullable 이고, null 이면 0(방금 잔 것과 같음)
     * 으로 복원한다 — 구형 세이브가 접속하자마자 3일치 불면으로 판정되지 않게 하는 안전한 방향이다.
     */
    private Long timeSinceRestMcTicks;

    /** Added in 26.3; absent legacy rows represent zero recorded uses. */
    private Integer sleepInStrawBed;

    public int sleepInStrawBedOrZero() {
        return sleepInStrawBed == null ? 0 : sleepInStrawBed;
    }

    public int recordStrawBedSleep() {
        int previous = sleepInStrawBedOrZero();
        sleepInStrawBed = previous == Integer.MAX_VALUE ? previous : previous + 1;
        return sleepInStrawBed;
    }

    /**
     * 지속 상태이상. 행이 없으면 효과가 없는 현재 상태를 뜻하며, 남은 시간과 주기 누적을 MC 틱
     * 그대로 보관해 재접속이 독 피해 시점이나 홀수 틱 지속을 되감지 않는다.
     */
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
            name = "player_status_effects",
            joinColumns = @JoinColumn(name = "state_id"),
            uniqueConstraints = @UniqueConstraint(
                    name = "uq_player_state_effect", columnNames = {"state_id", "effect"})
    )
    private List<PlayerStatusEffect> activeStatusEffects = new ArrayList<>();

    // 황금사과·불사의 토템이 상태이상 목록 밖의 물리 축에서 사용하는 정확한 남은 시계.
    private Integer regenerationMcTicksRemaining;
    private Integer regenerationMcTickAccum;
    private Integer absorptionTicksRemaining;
    private Integer absorptionPoints;
    private Integer fireResistanceTicksRemaining;
    private Integer bandageHealingTicks;

    /** 플레이어↔컨테이너 원자 스냅샷의 단조 증가 세대. */
    @Column(nullable = false, columnDefinition = "bigint not null default 0")
    private long inventoryPersistenceRevision;

    /** 재접속 뒤에도 유지되는 서버 권위 핫바 선택 슬롯(0~8). */
    @Column(nullable = false, columnDefinition = "int not null default 0")
    private int selectedSlot;

    /** 남은 연소 MC 틱과 다음 화상 피해까지의 정확한 누적 틱. */
    @Column(nullable = false, columnDefinition = "int not null default 0")
    private int fireTicks;

    @Column(nullable = false, columnDefinition = "int not null default 0")
    private int fireDamageAccum;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    /**
     * sparse 인벤토리(일반 0~35, 착용 방어구 36~39). 상태 엔티티에 딸린 값 목록이라
     * @ElementCollection 으로 저장합니다.
     * player_inventory_items(state_id, slot, item_type, item_count, durability) 테이블에 슬롯당 한 행씩 들어갑니다.
     */
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
            name = "player_inventory_items",
            joinColumns = @JoinColumn(name = "state_id"),
            uniqueConstraints = @UniqueConstraint(name = "uq_state_slot", columnNames = {"state_id", "slot"})
    )
    private List<InventoryItem> inventory = new ArrayList<>();

    /** 최초 접속 시 계산된 마른 땅 스폰 위치를 저장합니다. */
    public PlayerWorldState(PlayerAccess player, WorldAccess world,
            double spawnX, double spawnY, double spawnZ) {
        this.player = player;
        this.world = world;
        this.posX = spawnX;
        this.posY = spawnY;
        this.posZ = spawnZ;
        this.yaw = 0;
        this.pitch = 0;
        this.health = 20;
        // Minecraft와 같이 처음 들어온 플레이어는 빈손으로 시작합니다.
    }

    /** WebSocket 연결이 끝날 때 마지막 위치와 시선을 저장합니다. */
    public void updatePosition(double x, double y, double z, float yaw, float pitch) {
        this.posX = x;
        this.posY = y;
        this.posZ = z;
        this.yaw = yaw;
        this.pitch = pitch;
    }

    /** P5 틱 엔진의 서버 권위 체력을 저장합니다(0~20으로 제한). */
    public void updateHealth(int health) {
        this.health = Math.max(0, Math.min(20, health));
    }

    /** 서버 권위 허기·saturation 을 저장합니다(SURV-H). saturation 은 허기 값을 넘지 못합니다. */
    public void updateHunger(int hunger, int saturationMilli) {
        int clampedHunger = Math.max(0, Math.min(HungerRules.MAX_FOOD, hunger));
        this.hunger = clampedHunger;
        this.saturationMilli = Math.max(0, Math.min(clampedHunger * HungerRules.MILLI, saturationMilli));
    }

    /** 허기 컬럼이 없던 세이브는 만복으로 복원합니다. */
    public int hungerOrFull() {
        return hunger == null ? HungerRules.INITIAL_FOOD : hunger;
    }

    /** 허기 컬럼이 없던 세이브는 바닐라 초기 saturation 5.0 으로 복원합니다. */
    public int saturationMilliOrDefault() {
        return saturationMilli == null ? HungerRules.INITIAL_SATURATION_MILLI : saturationMilli;
    }

    /** [SURV-X] 서버 권위 누적 경험치를 저장합니다(음수 불가). */
    public void updateXpTotal(int xpTotal) {
        this.xpTotal = Math.max(0, xpTotal);
    }

    /** 경험치 컬럼이 없던 세이브는 0(레벨 0)으로 복원합니다. */
    public int xpTotalOrZero() {
        return xpTotal == null ? 0 : xpTotal;
    }

    /** [SURV-X] 인챈트 제안 시드를 저장합니다. int32 전 구간이 유효한 시드입니다. */
    public void updateEnchantSeed(int enchantSeed) {
        this.enchantSeed = enchantSeed;
    }

    /** 저장된 인챈트 시드. 컬럼이 없던 세이브는 null 이며 접속 시 새로 뽑습니다. */
    public Integer enchantSeedOrNull() {
        return enchantSeed;
    }

    /**
     * [PHANTOM] 불면 시간(MC 틱)을 저장합니다. 음수는 0 으로 자릅니다.
     * 저장 경로가 {@code -1} 같은 sentinel 을 보내면 호출부가 이 메서드를 아예 건너뜁니다.
     */
    public void updateTimeSinceRest(long timeSinceRestMcTicks) {
        this.timeSinceRestMcTicks = Math.max(0L, timeSinceRestMcTicks);
    }

    /** [PHANTOM] 컬럼이 없던 세이브는 0(방금 잔 것과 같음)으로 복원합니다. */
    public long timeSinceRestOrZero() {
        return timeSinceRestMcTicks == null ? 0L : timeSinceRestMcTicks;
    }

    /** 서버 권위 상태이상 목록을 현재 스냅샷으로 교체합니다. */
    public void updateStatusEffects(List<StatusEffects.PersistentEffect> effects) {
        if (effects == null) {
            throw new IllegalArgumentException("상태이상 스냅샷은 null일 수 없습니다");
        }
        this.activeStatusEffects.clear();
        for (StatusEffects.PersistentEffect effect : effects) {
            this.activeStatusEffects.add(new PlayerStatusEffect(effect));
        }
    }

    /** 접속 복원용 방어적 상태이상 스냅샷. */
    public List<StatusEffects.PersistentEffect> statusEffectsSnapshot() {
        return activeStatusEffects.stream().map(PlayerStatusEffect::toSnapshot).toList();
    }

    /** 목록 밖 플레이어 효과 시계를 같은 상태 행에 저장합니다. */
    public void updateEffectClocks(StatusEffects.PersistentPlayerEffectClocks clocks) {
        if (clocks == null) {
            throw new IllegalArgumentException("플레이어 상태이상 시계는 null일 수 없습니다");
        }
        regenerationMcTicksRemaining = clocks.regenerationMcTicksRemaining();
        regenerationMcTickAccum = clocks.regenerationMcTickAccum();
        absorptionTicksRemaining = clocks.absorptionTicksRemaining();
        absorptionPoints = clocks.absorptionPoints();
        fireResistanceTicksRemaining = clocks.fireResistanceTicksRemaining();
        bandageHealingTicks = clocks.bandageHealingTicks();
    }

    /** 접속 복원용 목록 밖 효과 시계. 저장 행이 없으면 효과가 없는 상태입니다. */
    public StatusEffects.PersistentPlayerEffectClocks effectClocksSnapshot() {
        return new StatusEffects.PersistentPlayerEffectClocks(
                regenerationMcTicksRemaining == null ? 0 : regenerationMcTicksRemaining,
                regenerationMcTickAccum == null ? 0 : regenerationMcTickAccum,
                absorptionTicksRemaining == null ? 0 : absorptionTicksRemaining,
                absorptionPoints == null ? 0 : absorptionPoints,
                fireResistanceTicksRemaining == null ? 0 : fireResistanceTicksRemaining,
                bandageHealingTicks == null ? 0 : bandageHealingTicks);
    }

    /** 핫바 선택과 연소 위상을 같은 플레이어 상태 행에 저장한다. */
    public void updateSelectedSlot(int selectedSlot) {
        if (selectedSlot < 0 || selectedSlot >= PlayerInventory.HOTBAR_SLOTS) {
            throw new IllegalArgumentException("선택 슬롯은 핫바 범위여야 합니다: " + selectedSlot);
        }
        this.selectedSlot = selectedSlot;
    }

    public void updateFireState(int fireTicks, int fireDamageAccum) {
        if (fireTicks < 0 || fireDamageAccum < 0) {
            throw new IllegalArgumentException("연소 시계는 음수일 수 없습니다");
        }
        this.fireTicks = fireTicks;
        this.fireDamageAccum = fireDamageAccum;
    }

    /** 침대 리스폰 지점을 저장합니다(S2a). null 3개면 기존 지점을 해제합니다. */
    public void updateSpawn(Integer spawnX, Integer spawnY, Integer spawnZ) {
        this.spawnX = spawnX;
        this.spawnY = spawnY;
        this.spawnZ = spawnZ;
    }

    /**
     * 서버 권위 인벤토리를 저장합니다(일반 36칸과 별도 착용 4칸).
     * 비어 있는 칸(count 0 / itemType 0)은 저장하지 않아 재접속 welcome 에서 빈 칸으로 복원됩니다.
     * durability는 내구 아이템에만 저장하고 그 외에는 null로 둡니다.
     */
    public void updateInventory(short[] itemTypes, int[] counts, int[] durabilities,
            short[] equippedTypes, int[] equippedDurabilities) {
        updateInventory(itemTypes, counts, durabilities,
                new long[itemTypes.length], equippedTypes, equippedDurabilities,
                new long[equippedTypes.length]);
    }

    /** 더 최신인 원자 인벤토리 명령만 적용한다. 행 잠금 안에서 호출해야 한다. */
    public boolean beginInventoryPersistenceRevision(long revision) {
        if (revision == Long.MAX_VALUE) {
            throw new IllegalArgumentException("inventory persistence revision overflow");
        }
        if (revision <= inventoryPersistenceRevision) return false;
        inventoryPersistenceRevision = revision;
        return true;
    }

    /** [SURV-X] 인챈트 마스크까지 함께 저장합니다. 마스크 0 은 컬럼을 null 로 남겨 기존 행과 같게 둡니다. */
    public void updateInventory(short[] itemTypes, int[] counts, int[] durabilities,
            long[] enchantments, short[] equippedTypes, int[] equippedDurabilities,
            long[] equippedEnchantments) {
        updateInventory(itemTypes, counts, durabilities, enchantments, new int[itemTypes.length],
                equippedTypes, equippedDurabilities, equippedEnchantments);
    }

    /** 채워진 지도의 월드 지도 ID까지 함께 저장합니다. */
    public void updateInventory(short[] itemTypes, int[] counts, int[] durabilities,
            long[] enchantments, int[] mapIds, short[] equippedTypes, int[] equippedDurabilities,
            long[] equippedEnchantments) {
        updateInventory(itemTypes, counts, durabilities, enchantments, mapIds,
                new int[itemTypes.length], equippedTypes, equippedDurabilities,
                equippedEnchantments);
    }

    /** [SHULKER-CONTENTS] 셜커 27칸 참조 ID까지 함께 저장합니다. */
    public void updateInventory(short[] itemTypes, int[] counts, int[] durabilities,
            long[] enchantments, int[] mapIds, int[] shulkerIds, short[] equippedTypes,
            int[] equippedDurabilities, long[] equippedEnchantments) {
        updateInventory(itemTypes, counts, durabilities, enchantments, mapIds, shulkerIds,
                new String[itemTypes.length], equippedTypes, equippedDurabilities,
                equippedEnchantments);
    }

    public void updateInventory(short[] itemTypes, int[] counts, int[] durabilities,
            long[] enchantments, int[] mapIds, int[] shulkerIds, String[] bucketMobData,
            short[] equippedTypes, int[] equippedDurabilities, long[] equippedEnchantments) {
        updateInventory(itemTypes, counts, durabilities, enchantments, mapIds, shulkerIds,
                bucketMobData, new String[itemTypes.length], equippedTypes,
                equippedDurabilities, equippedEnchantments);
    }

    public void updateInventory(short[] itemTypes, int[] counts, int[] durabilities,
            long[] enchantments, int[] mapIds, int[] shulkerIds, String[] bucketMobData,
            String[] itemComponentData, short[] equippedTypes, int[] equippedDurabilities,
            long[] equippedEnchantments) {
        updateInventory(itemTypes, counts, durabilities, enchantments, mapIds, shulkerIds,
                bucketMobData, itemComponentData, equippedTypes, equippedDurabilities,
                equippedEnchantments, new String[ArmorSlot.values().length]);
    }

    public void updateInventory(short[] itemTypes, int[] counts, int[] durabilities,
            long[] enchantments, int[] mapIds, int[] shulkerIds, String[] bucketMobData,
            String[] itemComponentData, short[] equippedTypes, int[] equippedDurabilities,
            long[] equippedEnchantments, String[] equippedItemComponentData) {
        new PlayerInventory(itemTypes, counts, durabilities, enchantments, mapIds, shulkerIds,
                bucketMobData, itemComponentData,
                equippedTypes, equippedDurabilities, equippedEnchantments,
                equippedItemComponentData,
                PlayerInventory.StackSnapshot.EMPTY, 0, inventoryPersistenceRevision);
        // [ENDER-SHULKER] 엔더 상자 밴드(ENDER_SLOT_BASE 이상)는 이 메서드가 소유하지 않는다.
        // 통째로 clear() 하면 인벤토리를 저장할 때마다 엔더 상자가 조용히 비워진다 —
        // 두 밴드가 같은 테이블을 쓰되 서로의 행을 건드리지 않는 것이 append 스키마의 계약이다.
        this.inventory.removeIf(item -> item.getSlot() < PlayerInventory.ENDER_SLOT_BASE);
        for (int slot = 0; slot < itemTypes.length; slot++) {
            if (counts[slot] > 0 && itemTypes[slot] != 0) {
                Integer durability = PlayerInventory.isDurable(itemTypes[slot])
                        ? durabilities[slot] : null;
                this.inventory.add(new InventoryItem(slot, itemTypes[slot], counts[slot], durability,
                        enchantments[slot] == 0 ? null : enchantments[slot],
                        mapIds[slot] == 0 ? null : mapIds[slot],
                        shulkerIds[slot] == 0 ? null : shulkerIds[slot], bucketMobData[slot],
                        itemComponentData[slot]));
            }
        }
        for (ArmorSlot armorSlot : ArmorSlot.values()) {
            int slot = armorSlot.ordinal();
            short itemType = equippedTypes[slot];
            if (itemType == 0) continue;
            Integer durability = PlayerInventory.isDurable(itemType)
                    ? equippedDurabilities[slot] : null;
            this.inventory.add(new InventoryItem(
                    PlayerInventory.EQUIPPED_SLOT_BASE + slot, itemType, 1, durability,
                    equippedEnchantments[slot] == 0 ? null : equippedEnchantments[slot],
                    null, null, null, equippedItemComponentData[slot]));
        }
    }

    /**
     * [ENDER-SHULKER] 서버 권위 엔더 상자 27칸을 저장합니다.
     *
     * <p>{@link #updateInventory} 와 <b>같은 테이블</b>({@code player_inventory_items})의
     * 다른 슬롯 밴드({@code ENDER_SLOT_BASE..+27})를 쓰므로 새 테이블도, {@code DATABASE_VERSION}
     * 상승도 필요 없다 — 이 밴드의 행이 없던 옛 세이브는 빈 27칸으로 복원된다(화로 {@code variant} ·
     * {@code xpMilli} 가 낸 선례와 같은 계약). 두 메서드는 서로의 밴드를 건드리지 않으므로
     * 호출 순서와 무관하게 결과가 같다.
     *
     * <p>비어 있는 칸은 저장하지 않는다 — 다른 밴드와 같은 sparse 규약이다.
     */
    public void updateEnderChest(short[] itemTypes, int[] counts, int[] durabilities,
            long[] enchantments, int[] mapIds) {
        updateEnderChest(itemTypes, counts, durabilities, enchantments, mapIds,
                new int[itemTypes.length]);
    }

    /** [SHULKER-CONTENTS] 엔더 상자 27칸이 든 셜커 상자의 참조 ID 까지 함께 저장합니다. */
    public void updateEnderChest(short[] itemTypes, int[] counts, int[] durabilities,
            long[] enchantments, int[] mapIds, int[] shulkerIds) {
        updateEnderChest(itemTypes, counts, durabilities, enchantments, mapIds, shulkerIds,
                new String[itemTypes.length]);
    }

    public void updateEnderChest(short[] itemTypes, int[] counts, int[] durabilities,
            long[] enchantments, int[] mapIds, int[] shulkerIds, String[] bucketMobData) {
        updateEnderChest(itemTypes, counts, durabilities, enchantments, mapIds, shulkerIds,
                bucketMobData, new String[itemTypes.length]);
    }

    public void updateEnderChest(short[] itemTypes, int[] counts, int[] durabilities,
            long[] enchantments, int[] mapIds, int[] shulkerIds, String[] bucketMobData,
            String[] itemComponentData) {
        if (itemTypes.length != PlayerInventory.ENDER_CHEST_SLOTS) {
            throw new IllegalArgumentException(
                    "엔더 상자 칸 수가 올바르지 않습니다: " + itemTypes.length);
        }
        List<InventoryItem> rows = new ArrayList<>();
        for (int slot = 0; slot < itemTypes.length; slot++) {
            if (counts[slot] <= 0 || itemTypes[slot] == 0) continue;
            Integer durability = PlayerInventory.isDurable(itemTypes[slot])
                    ? durabilities[slot] : null;
            rows.add(new InventoryItem(
                    PlayerInventory.ENDER_SLOT_BASE + slot, itemTypes[slot], counts[slot],
                    durability,
                    enchantments[slot] == 0 ? null : enchantments[slot],
                    mapIds[slot] == 0 ? null : mapIds[slot],
                    shulkerIds[slot] == 0 ? null : shulkerIds[slot], bucketMobData[slot],
                    itemComponentData[slot]));
        }
        // 내용이 그대로면 행을 건드리지 않는다. 주기 저장은 대부분 "엔더 상자는 안 열었다"
        // 이므로, 매번 27칸을 지웠다 다시 넣으면 열지도 않은 상자가 매 저장마다 delete+insert
        // 를 부른다(인벤토리 쪽 InventorySnapshot 스킵과 같은 이유의 절약이다).
        if (sameEnderRows(rows)) return;
        this.inventory.removeIf(item -> item.getSlot() >= PlayerInventory.ENDER_SLOT_BASE
                && item.getSlot() < PlayerInventory.PERSISTED_SLOTS_WITH_ENDER);
        this.inventory.addAll(rows);
    }

    /** 지금 저장돼 있는 엔더 밴드가 {@code rows} 와 슬롯·종류·개수·내구·마스크·지도까지 같은가. */
    private boolean sameEnderRows(List<InventoryItem> rows) {
        List<InventoryItem> current = new ArrayList<>();
        for (InventoryItem item : this.inventory) {
            if (item.getSlot() >= PlayerInventory.ENDER_SLOT_BASE
                    && item.getSlot() < PlayerInventory.PERSISTED_SLOTS_WITH_ENDER) {
                current.add(item);
            }
        }
        if (current.size() != rows.size()) return false;
        current.sort(java.util.Comparator.comparingInt(InventoryItem::getSlot));
        rows.sort(java.util.Comparator.comparingInt(InventoryItem::getSlot));
        for (int index = 0; index < rows.size(); index++) {
            InventoryItem a = current.get(index);
            InventoryItem b = rows.get(index);
            if (a.getSlot() != b.getSlot() || a.getItemType() != b.getItemType()
                    || a.getItemCount() != b.getItemCount()
                    || !java.util.Objects.equals(a.getDurability(), b.getDurability())
                    || a.enchantmentMaskOrZero() != b.enchantmentMaskOrZero()
                    || a.mapIdOrZero() != b.mapIdOrZero()
                    || a.shulkerIdOrZero() != b.shulkerIdOrZero()
                    || !java.util.Objects.equals(a.getBucketMobData(), b.getBucketMobData())
                    || !java.util.Objects.equals(
                            a.getItemComponentData(), b.getItemComponentData())) {
                return false;
            }
        }
        return true;
    }

    /** 보조손의 단일 append-only 행을 저장하며 주 인벤토리와 엔더 상자 밴드는 보존합니다. */
    public void updateOffhand(PlayerInventory.StackSnapshot stack) {
        if (stack == null) throw new IllegalArgumentException("offhand stack is required");
        this.inventory.removeIf(item -> item.getSlot() == PlayerInventory.OFFHAND_SLOT);
        if (stack.isEmpty()) return;
        this.inventory.add(new InventoryItem(PlayerInventory.OFFHAND_SLOT, stack.itemType(),
                stack.count(), PlayerInventory.isDurable(stack.itemType())
                        ? stack.durability() : null,
                stack.enchantments() == 0 ? null : stack.enchantments(),
                stack.mapId() == 0 ? null : stack.mapId(),
                stack.shulkerId() == 0 ? null : stack.shulkerId(), stack.bucketMobData(),
                stack.itemComponentData()));
    }

    /** 저장된 보조손 행을 손실 없는 런타임 값으로 읽습니다. */
    public PlayerInventory.StackSnapshot offhandSnapshot() {
        PlayerInventory.StackSnapshot result = PlayerInventory.StackSnapshot.EMPTY;
        for (InventoryItem item : inventory) {
            if (item.getSlot() != PlayerInventory.OFFHAND_SLOT) continue;
            if (!result.isEmpty()) {
                throw new IllegalStateException("duplicate persisted offhand slot");
            }
            result = new PlayerInventory.StackSnapshot(item.getItemType(), item.getItemCount(),
                    item.getDurability() == null ? 0 : item.getDurability(),
                    item.enchantmentMaskOrZero(), item.mapIdOrZero(), item.shulkerIdOrZero(),
                    item.getBucketMobData(), item.getItemComponentData());
        }
        return result;
    }
}
