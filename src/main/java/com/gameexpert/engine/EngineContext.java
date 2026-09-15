package com.gameexpert.engine;

import com.gameexpert.block.persistence.BlockDiffBuffer;
import com.gameexpert.block.persistence.BlockDiffFlusher;
import com.gameexpert.block.repository.WorldBlockDiffRepository;
import com.gameexpert.config.EngineProperties;
import com.gameexpert.state.service.PlayerWorldStateService;
import com.gameexpert.ws.GameTransport;
import com.gameexpert.api.SessionRegistry;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.Accessors;

/** [제공코드] 월드 런타임이 공유하는 Spring 빈 묶음(월드마다 동일). */
@Getter
@Accessors(fluent = true)
@AllArgsConstructor
public class EngineContext {

    private final GameTransport broadcaster;
    private final SessionRegistry registry;
    private final WorldBlockDiffRepository diffRepository;
    private final BlockDiffBuffer blockDiffBuffer;
    private final BlockDiffFlusher blockDiffFlusher;
    private final PersistenceExecutor persistenceExecutor;
    private final PlayerWorldStateService stateService;
    private final EngineProperties properties;
}
