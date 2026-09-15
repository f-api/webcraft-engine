package com.gameexpert.campfire.repository;

import com.gameexpert.campfire.entity.WorldCampfire;
import java.util.Collection;
import java.util.List;

/** 좌표 튜플 목록으로만 모닥불을 읽는 조회입니다. */
public interface WorldCampfireRepositoryCustom {

    /**
     * 요청한 좌표의 모닥불만 읽습니다.
     *
     * @param positions {@code {x, y, z}} 3원소 배열 목록
     */
    List<WorldCampfire> findDirtyByWorldId(Long worldId, Collection<int[]> positions);
}
