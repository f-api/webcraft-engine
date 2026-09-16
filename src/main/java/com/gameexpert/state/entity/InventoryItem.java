package com.gameexpert.state.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 핫바(인벤토리) 한 칸의 내용물입니다.
 *
 * {@code @Embeddable} 이라서 자체 테이블이 아니라 PlayerWorldState 에 딸린
 * {@code player_inventory_items} 테이블의 한 행으로 저장됩니다(아래 @ElementCollection 참고).
 *   - slot       : 인벤토리 0~35 또는 착용 방어구 36~39(helmet/chest/legs/feet)
 *   - itemType   : 공유 프로토콜의 현재 블록 또는 순수 아이템 ID
 *   - itemCount  : 개수(스택)
 *   - durability : 내구 아이템의 남은 내구도(필수). 그 외 아이템은 null
 */
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InventoryItem {

    private int slot;

    private short itemType;

    private int itemCount;

    // 내구 아이템만 값을 가지며, 블록·일반 아이템은 null.
    private Integer durability;

    /** [SURV-X] 인챈트 압축 마스크. 인챈트가 없는 칸은 null 로 두어 기존 행과 호환된다. */
    @Column(columnDefinition = "BIGINT")
    private Long enchantments;

    /** 채워진 지도의 월드 소유 ID. 다른 아이템과 빈 지도는 null 이다. */
    private Integer mapId;

    /**
     * [SHULKER-CONTENTS] 셜커 상자가 물고 있는 27칸 참조 ID({@code shulker_contents} 의 키).
     *
     * <p>{@link #mapId} 가 낸 선례를 문자 그대로 따른다 — 27칸을 이 행에 실으면 인벤토리
     * 행이 가변 크기가 된다. 내용이 없는 상자와 그 밖의 모든 아이템은 null 이라, 이 컬럼이
     * 없던 옛 세이브가 그대로 "내용 없음"으로 복원된다({@code DATABASE_VERSION} 무관).
     */
    private Integer shulkerId;

    /** Strict WCMB1 identity component for Axolotl, Tropical Fish and Tadpole buckets. */
    @Column(columnDefinition = "LONGTEXT")
    private String bucketMobData;

    /** Strict current WCIC2 item component identity, including suspicious-stew effects. */
    @Column(columnDefinition = "LONGTEXT")
    private String itemComponentData;

    public InventoryItem(int slot, short itemType, int itemCount, Integer durability) {
        this(slot, itemType, itemCount, durability, null);
    }

    public InventoryItem(int slot, short itemType, int itemCount, Integer durability,
            Long enchantments) {
        this(slot, itemType, itemCount, durability, enchantments, null);
    }

    public InventoryItem(int slot, short itemType, int itemCount, Integer durability,
            Long enchantments, Integer mapId) {
        this(slot, itemType, itemCount, durability, enchantments, mapId, null);
    }

    public InventoryItem(int slot, short itemType, int itemCount, Integer durability,
            Long enchantments, Integer mapId, Integer shulkerId) {
        this(slot, itemType, itemCount, durability, enchantments, mapId, shulkerId, null);
    }

    public InventoryItem(int slot, short itemType, int itemCount, Integer durability,
            Long enchantments, Integer mapId, Integer shulkerId, String bucketMobData) {
        this(slot, itemType, itemCount, durability, enchantments, mapId, shulkerId,
                bucketMobData, null);
    }

    public InventoryItem(int slot, short itemType, int itemCount, Integer durability,
            Long enchantments, Integer mapId, Integer shulkerId, String bucketMobData,
            String itemComponentData) {
        this.slot = slot;
        this.itemType = itemType;
        this.itemCount = itemCount;
        this.durability = durability;
        this.enchantments = enchantments;
        this.mapId = mapId;
        this.shulkerId = shulkerId;
        this.bucketMobData = bucketMobData;
        this.itemComponentData = itemComponentData;
    }

    /** 인챈트 컬럼이 없던 행은 0(마스크 없음)으로 복원한다. */
    public long enchantmentMaskOrZero() {
        return enchantments == null ? 0L : enchantments;
    }

    public int mapIdOrZero() {
        return mapId == null ? 0 : mapId;
    }

    /** [SHULKER-CONTENTS] 참조 컬럼이 없던 행은 0(내용 없음)으로 복원한다. */
    public int shulkerIdOrZero() {
        return shulkerId == null ? 0 : shulkerId;
    }
}
