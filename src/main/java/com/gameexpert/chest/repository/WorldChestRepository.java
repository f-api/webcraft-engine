package com.gameexpert.chest.repository;

import com.gameexpert.chest.entity.WorldChest;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.repository.query.Param;

/** 월드에 설치된 상자의 내용물 저장소. */
public interface WorldChestRepository
        extends JpaRepository<WorldChest, Long>, WorldChestRepositoryCustom {

    /** 월드 입장 시 그 월드의 모든 상자를 한 번에 읽어 런타임 맵을 채웁니다. */
    @Query("select distinct chest from WorldChest chest left join fetch chest.items "
            + "where chest.worldId = :worldId")
    List<WorldChest> findAllByWorldId(@Param("worldId") Long worldId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select distinct chest from WorldChest chest left join fetch chest.items "
            + "where chest.worldId=:worldId and chest.posX=:x and chest.posY=:y and chest.posZ=:z")
    Optional<WorldChest> findLockedAt(@Param("worldId") Long worldId,
            @Param("x") int x, @Param("y") int y, @Param("z") int z);

    /** Locks one exact row incarnation before a dirty snapshot can update or delete it. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select distinct chest from WorldChest chest left join fetch chest.items "
            + "where chest.id=:id")
    Optional<WorldChest> findLockedById(@Param("id") Long id);

    /** 월드 삭제 시 상자와 딸린 아이템 행을 함께 정리합니다. */
    void deleteAllByWorldId(Long worldId);
}
