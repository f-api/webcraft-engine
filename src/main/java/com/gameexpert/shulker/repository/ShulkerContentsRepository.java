package com.gameexpert.shulker.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.gameexpert.shulker.entity.ShulkerContents;

/** [SHULKER-CONTENTS] 아이템으로 이동 중인 셜커 27칸의 저장소. */
public interface ShulkerContentsRepository extends JpaRepository<ShulkerContents, Long> {

    /** 월드 입장 시 그 월드의 참조 행 전량을 한 번에 읽어 런타임 저장소를 채웁니다. */
    @Query("select distinct row from ShulkerContents row left join fetch row.items "
            + "where row.world.id = :worldId order by row.shulkerId asc")
    List<ShulkerContents> findAllByWorldId(@Param("worldId") Long worldId);

    /** 플러시 배치가 건드리는 참조 ID 만 읽어 upsert 대상을 가려냅니다. */
    @Query("select distinct row from ShulkerContents row left join fetch row.items "
            + "where row.world.id = :worldId and row.shulkerId in :shulkerIds")
    List<ShulkerContents> findDirtyByWorldId(
            @Param("worldId") Long worldId, @Param("shulkerIds") Collection<Integer> shulkerIds);

    /** 설치가 확정된 참조 행을 지웁니다(27칸의 소유가 좌표 블록 엔티티로 넘어갔다). */
    @Modifying
    @Query("delete from ShulkerContents row "
            + "where row.world.id = :worldId and row.shulkerId in :shulkerIds")
    void deleteByWorldIdAndShulkerIdIn(
            @Param("worldId") Long worldId, @Param("shulkerIds") Collection<Integer> shulkerIds);

    /** 월드 삭제 시 참조 행과 딸린 칸 행을 함께 정리합니다. */
    void deleteAllByWorld_Id(Long worldId);
}
