package com.gameexpert.boat.repository;

import com.gameexpert.boat.entity.WorldBoat;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 런타임 교체 뒤 복원할 보트를 담는 저장소입니다. */
public interface WorldBoatRepository extends JpaRepository<WorldBoat, Long> {

    /** Spring Data 컬렉션 조회 계약에 따라 결과가 없으면 null 이 아니라 빈 목록을 반환합니다. */
    List<WorldBoat> findAllByWorldId(Long worldId);

    @Modifying
    @Query("delete from WorldBoat boat where boat.worldId = :worldId")
    void deleteAllByWorldId(@Param("worldId") Long worldId);
}
