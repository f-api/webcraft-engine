package com.gameexpert.dragonfight.service;

import com.gameexpert.dragonfight.entity.WorldDragonFight;
import com.gameexpert.dragonfight.repository.WorldDragonFightRepository;
import com.gameexpert.api.persistence.WorldStore;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * [DRAGON] 드래곤전 상태 행의 트랜잭션 경계. 틱 소유자는 상태가 바뀐 틱에만 인코딩한 문자열을 단일 영속 실행기로
 * 넘긴다. 드래곤·수정 개체는 몹 원장이, 포털·관문·알 블록은 일반 블록 diff 가 따로 영속한다.
 */
@Service
public class DragonFightPersistenceService {
    private final WorldDragonFightRepository fights;
    private final WorldStore worlds;

    public DragonFightPersistenceService(WorldDragonFightRepository fights, WorldStore worlds) {
        this.fights = fights;
        this.worlds = worlds;
    }

    @Transactional(readOnly = true)
    public Optional<String> load(Long worldId) {
        return fights.findByWorldId(worldId).map(WorldDragonFight::getState);
    }

    @Transactional
    public void save(Long worldId, String state) {
        WorldDragonFight row = fights.findByWorldId(worldId).orElse(null);
        if (row == null) row = new WorldDragonFight(worlds.getReferenceById(worldId), state);
        else row.replace(state);
        fights.save(row);
    }

    @Transactional
    public void deleteWorld(Long worldId) {
        fights.deleteAllByWorldId(worldId);
    }
}
