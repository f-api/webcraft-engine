package com.gameexpert.endgateway.service;

import com.gameexpert.endgateway.dto.EndGatewayData;
import com.gameexpert.endgateway.entity.WorldEndGateway;
import com.gameexpert.endgateway.repository.WorldEndGatewayRepository;
import com.gameexpert.api.persistence.WorldStore;
import java.util.Collection;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * [END-GATEWAY] 엔드 관문 출구 행의 트랜잭션 경계. 틱 소유자는 관문을 만들거나 출구를 정할 때 바뀐 행만
 * 단일 영속 실행기로 넘긴다. 관문 블록 자체는 일반 블록 diff 로 영속한다(바닐라도 관문 블록 엔티티와
 * 출구 쪽 청크는 따로 저장된다).
 */
@Service
public class EndGatewayPersistenceService {
    private final WorldEndGatewayRepository gateways;
    private final WorldStore worlds;

    public EndGatewayPersistenceService(WorldEndGatewayRepository gateways, WorldStore worlds) {
        this.gateways = gateways;
        this.worlds = worlds;
    }

    @Transactional(readOnly = true)
    public List<EndGatewayData> loadWorld(Long worldId) {
        return gateways.findAllByWorldId(worldId).stream().map(WorldEndGateway::snapshot).toList();
    }

    @Transactional
    public void upsert(Long worldId, Collection<EndGatewayData> rows) {
        for (EndGatewayData data : rows) {
            WorldEndGateway row = gateways.findAt(worldId, data.x(), data.y(), data.z()).orElse(null);
            if (row == null) row = new WorldEndGateway(worlds.getReferenceById(worldId), data);
            else row.replace(data);
            gateways.save(row);
        }
    }

    @Transactional
    public void deleteWorld(Long worldId) {
        gateways.deleteAllByWorldId(worldId);
    }
}
