package com.gameexpert.mob.dto;

/** 재접속·재기동 뒤에도 남은 보상을 계속 지급하기 위한 불변 전달 행. */
public record RewardDeliverySnapshot(
        long raidId, String rewardToken, String recipientNickname,
        short itemType, int remainingCount, int durability, long enchantments) {}
