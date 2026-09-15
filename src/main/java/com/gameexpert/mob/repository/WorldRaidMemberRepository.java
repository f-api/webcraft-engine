package com.gameexpert.mob.repository;

import com.gameexpert.mob.entity.WorldRaidMember;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 레이드 원장 멤버 저장소입니다. */
public interface WorldRaidMemberRepository extends JpaRepository<WorldRaidMember, Long> {

    List<WorldRaidMember> findAllByWorldId(Long worldId);

    @Modifying
    @Query("delete from WorldRaidMember member where member.worldId = :worldId")
    void deleteAllByWorldId(@Param("worldId") Long worldId);
}
