package com.gameexpert.config;

import com.gameexpert.engine.WorldEngineManager;
import com.gameexpert.state.service.DimensionTravelPersistence;
import com.gameexpert.state.service.PlayerWorldStateService;
import com.gameexpert.world.dimension.DimensionProviders;
import com.gameexpert.world.dimension.DimensionRegistry;
import com.gameexpert.ws.DimensionTravelCoordinator;
import com.gameexpert.ws.ConnectionEndpoint;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;

/** 기존 fixture 생성자와 별개로 실제 application composition에서만 차원 capability를 결박한다. */
@Configuration
@RequiredArgsConstructor
public class DimensionConfiguration {
    private final WorldEngineManager engine;
    private final DimensionTravelPersistence travel;
    private final PlayerWorldStateService playerStates;
    private final DimensionRegistry registry;
    private final DimensionProviders providers;
    private final DimensionTravelCoordinator transport;
    private final ConnectionEndpoint handler;
    private final com.gameexpert.world.service.WorldOperations worldService;
    private final com.gameexpert.world.repository.WorldDimensionRepository mappings;
    private final com.gameexpert.state.repository.PlayerDimensionTravelRepository travelRows;
    private final com.gameexpert.engine.persistence.animal.FleshNetherPersistenceService fleshNetherPersistence;
    private final com.gameexpert.engine.persistence.animal.FleshColonySettlementService fleshColonySettlements;

    @PostConstruct
    void bind() {
        // 콘텐츠 플러그인은 연결 결박 전에 이 조립점에서 설치한다(CONTRACT §11A).
        com.gameexpert.world.dimension.voidend.VoidEndDimension.install(providers);
        providers.install(com.gameexpert.world.dimension.flesh.FleshNetherProvider.definition(),
                new com.gameexpert.world.dimension.flesh.FleshNetherProvider());
        engine.attachFleshNetherPersistence(fleshNetherPersistence);
        engine.attachFleshColonySettlements(fleshColonySettlements);
        playerStates.attachDimensionTravel(travel);
        engine.attachDimensions(travel, registry, providers);
        engine.attachDimensionGateway(transport::request);
        transport.attachHandler(handler);
        handler.attachDimensions(transport);
        worldService.attachDimensions(mappings, travelRows);
    }
}
