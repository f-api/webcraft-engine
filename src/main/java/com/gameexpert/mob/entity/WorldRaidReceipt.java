package com.gameexpert.mob.entity;

import com.gameexpert.engine.raid.RaidRewardReceipt;
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

/**
 * 승리 보상 receipt 한 건. `(world_id, raid_id, reward_token)` 유일 제약이 중복 청구를 막고,
 * 보상 롤 시드도 같은 행에 있어 재시도가 다른 보상을 뽑을 수 없습니다.
 */
@Getter
@Entity
@Table(
        name = "world_raid_receipts",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_world_raid_receipt",
                columnNames = { "world_id", "raid_id", "reward_token" }))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorldRaidReceipt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long worldId;

    @Column(nullable = false)
    private long raidId;

    @Column(nullable = false)
    private String rewardToken;

    @Column(nullable = false)
    private long rewardSeed;

    private String recipientNickname;

    @Column(nullable = false)
    private long recordedTick;

    @Column(nullable = false)
    private String state;

    public WorldRaidReceipt(Long worldId, RaidRewardReceipt receipt) {
        this.worldId = worldId;
        this.raidId = receipt.raidId();
        this.rewardToken = receipt.rewardToken();
        this.rewardSeed = receipt.rewardSeed();
        this.recipientNickname = receipt.recipientNickname();
        this.recordedTick = receipt.recordedTick();
        this.state = receipt.state();
    }

    public RaidRewardReceipt toReceipt() {
        return new RaidRewardReceipt(raidId, rewardToken, rewardSeed, recipientNickname,
                recordedTick, state);
    }

    /** Flips PENDING → GRANTED once. A second call is a no-op and reports it. */
    public boolean markGranted() {
        if (RaidRewardReceipt.STATE_GRANTED.equals(state)) return false;
        state = RaidRewardReceipt.STATE_GRANTED;
        return true;
    }
}
