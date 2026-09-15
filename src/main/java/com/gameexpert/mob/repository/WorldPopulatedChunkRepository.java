package com.gameexpert.mob.repository;

import com.gameexpert.mob.entity.WorldPopulatedChunk;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 청크별 동물 무리 배치 판정 완료 표식 저장소입니다. */
public interface WorldPopulatedChunkRepository extends JpaRepository<WorldPopulatedChunk, Long> {

    /** Spring Data의 컬렉션 조회 계약에 따라 결과가 없으면 null이 아니라 빈 목록을 반환합니다. */
    List<WorldPopulatedChunk> findAllByWorldId(Long worldId);

    boolean existsByWorldIdAndChunkXAndChunkZ(Long worldId, int chunkX, int chunkZ);

    @Modifying
    @Query("delete from WorldPopulatedChunk populated where populated.worldId = :worldId")
    void deleteAllByWorldId(@Param("worldId") Long worldId);
}
