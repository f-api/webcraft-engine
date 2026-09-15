package com.gameexpert.furnace.repository;

import com.gameexpert.furnace.entity.WorldFurnace;
import java.util.Collection;
import java.util.List;

/** 좌표 튜플 목록으로만 화로를 읽는 조회입니다. */
public interface WorldFurnaceRepositoryCustom {

    /**
     * 요청한 좌표의 화로만 읽습니다.
     *
     * @param positions {@code {x, y, z}} 3원소 배열 목록
     */
    List<WorldFurnace> findDirtyByWorldId(Long worldId, Collection<int[]> positions);
}
