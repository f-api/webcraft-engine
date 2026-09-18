package com.gameexpert.mob.entity;

import com.gameexpert.mob.dto.RewardDeliverySnapshot;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** receipt 소비와 실제 플레이어 저장 사이의 유실 구간을 없애는 durable reward outbox. */
@Getter
@Entity
@org.hibernate.annotations.DynamicUpdate
@Table(name = "world_reward_deliveries", uniqueConstraints = @UniqueConstraint(
        name = "uk_world_reward_delivery",
        columnNames = {"world_id", "raid_id", "reward_token"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorldRewardDelivery {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false) private Long worldId;
    @Column(nullable = false) private long raidId;
    @Column(nullable = false) private String rewardToken;
    @Column(nullable = false) private String recipientNickname;
    @Column(nullable = false) private short itemType;
    @Column(nullable = false) private int remainingCount;
    @Column(nullable = false) private int durability;
    @Getter(AccessLevel.NONE)
    @Column(nullable = false, columnDefinition = "BIGINT") private long enchantments;
    /**
     * [ENCHANT-WIDE] 트라이얼 금고 배출 행의 성분 문자열(ID 16 이상 인챈트, WCIC4). 없으면 null —
     * 옛 행과 습격 보상 행은 그대로 읽힌다.
     */
    @Column(length = 8192) private String itemComponentData;

    public WorldRewardDelivery(Long worldId, long raidId, String rewardToken,
            String recipientNickname, short itemType, int count, int durability,
            long enchantments) {
        this(worldId, raidId, rewardToken, recipientNickname, itemType, count, durability,
                enchantments, null);
    }

    public WorldRewardDelivery(Long worldId, long raidId, String rewardToken,
            String recipientNickname, short itemType, int count, int durability,
            long enchantments, String itemComponentData) {
        this.itemComponentData = itemComponentData;
        this.worldId = worldId;
        this.raidId = raidId;
        this.rewardToken = rewardToken;
        this.recipientNickname = recipientNickname;
        this.itemType = itemType;
        this.remainingCount = Math.max(0, count);
        this.durability = durability;
        this.enchantments = enchantments;
    }

    public void delivered(int count) {
        if (count < 0 || count > remainingCount) throw new IllegalArgumentException("delivery count");
        remainingCount -= count;
    }

    public RewardDeliverySnapshot snapshot() {
        return new RewardDeliverySnapshot(raidId, rewardToken, recipientNickname, itemType,
                remainingCount, durability, enchantments);
    }

    public long getEnchantments() { return enchantments; }
}
