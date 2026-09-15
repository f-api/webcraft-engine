package com.gameexpert.mob.repository;

import com.gameexpert.mob.entity.WorldRewardDelivery;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface WorldRewardDeliveryRepository extends JpaRepository<WorldRewardDelivery, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<WorldRewardDelivery> findLockedByWorldIdAndRaidIdAndRewardToken(
            Long worldId, long raidId, String rewardToken);
    List<WorldRewardDelivery> findAllByWorldIdAndRemainingCountGreaterThan(Long worldId, int count);
    void deleteAllByWorldId(Long worldId);
}
