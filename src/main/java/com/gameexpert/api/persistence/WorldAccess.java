package com.gameexpert.api.persistence;

import java.time.LocalDateTime;
import com.gameexpert.engine.Difficulty;
import com.gameexpert.world.WorldGenerationProfile;

public interface WorldAccess {
    Long getId();
    String getName();
    long getSeed();
    String getBaselineId();
    String getBaselineInputFingerprintSha256();
    String getGeneratorSourceSha256();
    Integer getBaselineWorldVersion();
    Integer getBaselineDataPackMajor();
    Integer getBaselineResourcePackMajor();
    Integer getBaselineProtocolVersion();
    String getOwnerNickname();
    long getDayCount();
    long getWorldTime();
    long getGameTimeMcTicks();
    Long getTraderNextAttemptTick();
    Integer getTraderChancePercent();
    Integer getSpawnX();
    Integer getSpawnY();
    Integer getSpawnZ();
    Difficulty getDifficulty();
    LocalDateTime getCreatedAt();
    WorldGenerationProfile generationProfile();
    boolean usesCurrentBaseline();
    boolean isOwnedBy(String nickname);
    boolean hasOwner();
    void updateDayCount(long dayCount);
    void updateClock(long dayCount, long worldTime, long gameTimeMcTicks);
    boolean hasCanonicalSpawn();
    int[] canonicalSpawn();
    void assignCanonicalSpawnIfAbsent(int x, int y, int z);
    void updateTraderWindow(long nextAttemptTick, int chancePercent);
}
