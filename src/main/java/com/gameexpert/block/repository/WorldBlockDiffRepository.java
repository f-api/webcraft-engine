package com.gameexpert.block.repository;

import com.gameexpert.block.entity.WorldBlockDiff;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 블록 변경 기록 저장소.
 */
public interface WorldBlockDiffRepository extends JpaRepository<WorldBlockDiff, Long> {

    /** 월드 런타임 시작 때 희소 상태만 복원하기 위한 diff 전체 조회. */
    List<WorldBlockDiff> findByWorldId(Long worldId);

    Optional<WorldBlockDiff> findByWorldIdAndXAndYAndZ(Long worldId, int x, int y, int z);

    /** Locks one overlay cell before an exact multi-authority settlement validates it. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM WorldBlockDiff d WHERE d.world.id = :worldId "
            + "AND d.x = :x AND d.y = :y AND d.z = :z")
    Optional<WorldBlockDiff> findLockedAt(@Param("worldId") Long worldId,
            @Param("x") int x, @Param("y") int y, @Param("z") int z);

    /** One-shot runtime-entity migrations must not hydrate every historical world edit. */
    @Query("SELECT d FROM WorldBlockDiff d WHERE d.world.id = :worldId AND d.blockType = :blockType")
    List<WorldBlockDiff> findByWorldIdAndBlockType(
            @Param("worldId") Long worldId, @Param("blockType") short blockType);

    /**
     * 한 좌표 상자 안의 durable overlay 행을 한 번에 읽습니다. 같은 좌표를 다시 쓰는 정산이
     * 좌표마다 왕복 조회를 하지 않고 "있으면 갱신, 없으면 생성"을 판단하기 위한 조회입니다.
     */
    @Query("SELECT d FROM WorldBlockDiff d WHERE d.world.id = :worldId "
            + "AND d.x BETWEEN :minX AND :maxX AND d.z BETWEEN :minZ AND :maxZ")
    List<WorldBlockDiff> findWithinColumnBox(@Param("worldId") Long worldId,
            @Param("minX") int minX, @Param("maxX") int maxX,
            @Param("minZ") int minZ, @Param("maxZ") int maxZ);

    /** 특정 월드의 특정 청크(cx, cz)에 속한 변경 블록들을 모두 가져옵니다. */
    List<WorldBlockDiff> findByWorldIdAndChunkXAndChunkZ(Long worldId, int chunkX, int chunkZ);

    /**
     * 월드가 삭제될 때, 그 월드의 블록 변경 기록을 한 번의 DELETE 문으로 모두 지웁니다.
     * (@Modifying = 조회가 아니라 변경/삭제 쿼리라는 표시. 대량 삭제라 엔티티를 하나씩 불러오지 않아 빠릅니다.)
     */
    @Modifying
    @Query("DELETE FROM WorldBlockDiff d WHERE d.world.id = :worldId")
    void deleteByWorldId(@Param("worldId") Long worldId);

    /** One-shot schema migration hook for retired block-form runtime entities. */
    @Modifying
    @Query("UPDATE WorldBlockDiff d SET d.blockType = :replacement, d.blockState = 0 "
            + "WHERE d.world.id = :worldId AND d.blockType = :legacy")
    int replaceBlockType(@Param("worldId") Long worldId, @Param("legacy") short legacy,
            @Param("replacement") short replacement);

    /** Atomically replaces one exact world-coordinate block diff when its stored value matches. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE WorldBlockDiff d SET d.blockType = :nextBlockType, "
            + "d.blockState = :nextBlockState, d.mobMutationKey = NULL, "
            + "d.updatedAt = CURRENT_TIMESTAMP "
            + "WHERE d.world.id = :worldId AND d.x = :x AND d.y = :y AND d.z = :z "
            + "AND d.blockType = :expectedBlockType AND d.blockState = :expectedBlockState")
    int compareAndSetExact(
            @Param("worldId") Long worldId,
            @Param("x") int x,
            @Param("y") int y,
            @Param("z") int z,
            @Param("expectedBlockType") short expectedBlockType,
            @Param("expectedBlockState") short expectedBlockState,
            @Param("nextBlockType") short nextBlockType,
            @Param("nextBlockState") short nextBlockState);
}
