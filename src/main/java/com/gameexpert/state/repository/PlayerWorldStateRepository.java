package com.gameexpert.state.repository;

import com.gameexpert.state.entity.PlayerWorldState;


import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;

/**
 * 플레이어-월드 상태 저장소.
 */
public interface PlayerWorldStateRepository extends JpaRepository<PlayerWorldState, Long> {

    /** 특정 플레이어가 특정 월드에서 가진 상태를 찾습니다(없을 수 있으므로 Optional). */
    Optional<PlayerWorldState> findByPlayerIdAndWorldId(Long playerId, Long worldId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PlayerWorldState> findLockedByPlayerIdAndWorldId(Long playerId, Long worldId);

    /** 월드 삭제 시 element collection까지 엔티티 생명주기로 지울 대상입니다. */
    List<PlayerWorldState> findAllByWorldId(Long worldId);
}
