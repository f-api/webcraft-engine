package com.gameexpert.mob.repository;

import com.gameexpert.mob.entity.WorldRaidReceipt;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

/** 보상 receipt 저장소. 살아 있는 월드에서는 append-only 이고, 월드 삭제와 함께 정리됩니다. */
public interface WorldRaidReceiptRepository extends JpaRepository<WorldRaidReceipt, Long> {

    List<WorldRaidReceipt> findAllByWorldId(Long worldId);

    Optional<WorldRaidReceipt> findByWorldIdAndRaidIdAndRewardToken(
            Long worldId, long raidId, String rewardToken);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<WorldRaidReceipt> findLockedByWorldIdAndRaidIdAndRewardToken(
            Long worldId, long raidId, String rewardToken);

    boolean existsByWorldIdAndRaidIdAndRewardToken(Long worldId, long raidId, String rewardToken);

    void deleteAllByWorldId(Long worldId);
}
