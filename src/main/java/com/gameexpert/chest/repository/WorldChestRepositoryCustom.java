package com.gameexpert.chest.repository;

import com.gameexpert.chest.entity.WorldChest;
import java.util.Collection;
import java.util.List;

/** 좌표 튜플 목록으로만 상자를 읽는 조회입니다. */
public interface WorldChestRepositoryCustom {

    /**
     * 요청한 좌표의 상자만 items 와 함께 읽습니다.
     *
     * @param positions {@code {x, y, z}} 3원소 배열 목록
     */
    List<WorldChest> findDirtyByWorldId(Long worldId, Collection<int[]> positions);
}
