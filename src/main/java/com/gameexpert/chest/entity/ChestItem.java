package com.gameexpert.chest.entity;

import com.gameexpert.engine.inventory.PlayerInventory;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Lob;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 상자 한 칸의 내용물입니다.
 *
 * {@code @Embeddable} 이라서 자체 테이블이 아니라 {@link WorldChest} 에 딸린
 * {@code world_chest_items} 테이블의 한 행으로 저장됩니다.
 *   - slot      : 상자 위치 0~26 (27칸)
 *   - itemType  : 아이템 종류 ID (계약 §2)
 *   - itemCount : 개수(스택)
 *   - durability: 내구 아이템의 남은 내구도(필수), 일반 아이템은 null
 */
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChestItem {

    private int slot;

    private short itemType;

    private int itemCount;

    private Integer durability;

    /** [SURV-X] 인챈트 압축 마스크. 마스크가 없는 칸은 null 로 두어 기존 행과 호환된다. */
    @Getter(AccessLevel.NONE)
    @Column(columnDefinition = "BIGINT")
    private Long enchantments;

    /** 채워진 지도의 월드 지도 ID. 다른 아이템은 null 이다. */
    private Integer mapId;

    /**
     * [SHULKER-CONTENTS] 이 칸에 든 셜커 상자가 물고 있는 27칸의 참조 ID
     * ({@code shulker_contents} 의 키). 그 밖의 아이템은 null 이다.
     *
     * <p>이 {@code @Embeddable} 은 좌표 상자({@code world_chest_items})와 셜커 27칸
     * ({@code shulker_contents_items}) 두 수집 테이블이 함께 쓴다. 셜커 안에는 셜커를 넣을 수
     * 없으므로([A] 바닐라 {@code ShulkerBoxBlockEntity#canPlaceItem}) 후자에서 이 열은 언제나
     * null 이고, 그래서 참조가 참조를 낳는 순환이 구조적으로 생기지 않는다.
     */
    private Integer shulkerId;

    @Column(columnDefinition = "LONGTEXT")
    private String bucketMobData;

    /** Current WCIC2 item components. Books can exceed VARCHAR limits. */
    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String itemComponentData;

    public ChestItem(int slot, short itemType, int itemCount, Integer durability,
            Long enchantments, Integer mapId, Integer shulkerId, String bucketMobData,
            String itemComponentData) {
        if (slot < 0 || itemType == PlayerInventory.EMPTY || itemCount <= 0) {
            throw new IllegalArgumentException("invalid chest item slot, type, or count");
        }
        if (PlayerInventory.isDurable(itemType) != (durability != null)) {
            throw new IllegalArgumentException("invalid chest item durability representation");
        }
        try {
            new PlayerInventory.StackSnapshot(itemType, itemCount,
                    durability == null ? 0 : durability,
                    enchantments == null ? 0L : enchantments,
                    mapId == null ? 0 : mapId,
                    shulkerId == null ? 0 : shulkerId,
                    bucketMobData, itemComponentData);
        } catch (IllegalArgumentException malformed) {
            throw new IllegalArgumentException("invalid chest item identity", malformed);
        }
        this.enchantments = enchantments;
        this.mapId = mapId;
        this.shulkerId = shulkerId;
        this.bucketMobData = bucketMobData;
        this.itemComponentData = itemComponentData;
        this.slot = slot;
        this.itemType = itemType;
        this.itemCount = itemCount;
        this.durability = durability;
    }

    public int mapIdOrZero() {
        return mapId == null ? 0 : mapId;
    }

    public Long getEnchantments() { return enchantments; }

    public long enchantmentsOrZero() {
        return enchantments == null ? 0L : enchantments;
    }

    /** [SHULKER-CONTENTS] 참조 컬럼이 없던 행은 0(내용 없음)으로 복원한다. */
    public int shulkerIdOrZero() {
        return shulkerId == null ? 0 : shulkerId;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ChestItem that)) return false;
        return slot == that.slot && itemType == that.itemType && itemCount == that.itemCount
                && Objects.equals(durability, that.durability)
                && Objects.equals(enchantments, that.enchantments)
                && Objects.equals(mapId, that.mapId)
                && Objects.equals(shulkerId, that.shulkerId)
                && Objects.equals(bucketMobData, that.bucketMobData)
                && Objects.equals(itemComponentData, that.itemComponentData);
    }

    @Override
    public int hashCode() {
        return Objects.hash(slot, itemType, itemCount, durability, enchantments, mapId,
                shulkerId, bucketMobData, itemComponentData);
    }
}
