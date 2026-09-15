package com.gameexpert.state.service.inventory;

import com.gameexpert.container.persistence.ContainerSettlementPersistenceService;
import com.gameexpert.state.service.PlayerWorldStateService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** 플레이어와 상자/화로/모닥불의 exact snapshot을 한 외부 트랜잭션으로 확정한다. */
@Service
public class PlayerContainerSettlementPersistenceService {
    private static final Logger log = LoggerFactory.getLogger(
            PlayerContainerSettlementPersistenceService.class);
    public enum Outcome { COMMITTED, IDEMPOTENT, STALE }

    private final PlayerWorldStateService playerStates;
    private final ContainerSettlementPersistenceService containers;
    private final TransactionTemplate transactions;

    public PlayerContainerSettlementPersistenceService(PlayerWorldStateService playerStates,
            ContainerSettlementPersistenceService containers, TransactionTemplate transactions) {
        this.playerStates = playerStates;
        this.containers = containers;
        this.transactions = transactions;
    }

    public Outcome settle(PlayerContainerSettlementCommand command) {
        try {
            return transactions.execute(ignored -> settleJoiningTransaction(command));
        } catch (StaleInventoryMutationException stale) {
            return Outcome.STALE;
        }
    }

    private Outcome settleJoiningTransaction(PlayerContainerSettlementCommand command) {
        var player = command.player();
        var containerOutcome = containers.settleJoiningTransaction(
                player.worldId(), command.container());
        if (containerOutcome == ContainerSettlementPersistenceService.Outcome.IDEMPOTENT) {
            return Outcome.IDEMPOTENT;
        }
        if (containerOutcome == ContainerSettlementPersistenceService.Outcome.STALE) {
            log.warn("컨테이너 정산 {} 거부: 컨테이너 revision 불일치 (world={}, expected={})",
                    command.container().getSettlementId(), player.worldId(),
                    command.container().getExpectedRevisions());
            return Outcome.STALE;
        }
        long persistedPlayerRevision = playerStates
                .lockInventoryPersistenceRevisionJoiningTransaction(
                        player.playerId(), player.worldId());
        if (persistedPlayerRevision != command.expectedPlayerRevision()) {
            log.warn("컨테이너 정산 {} 거부: 플레이어 revision 불일치 (world={}, player={}, expected={}, persisted={})",
                    command.container().getSettlementId(), player.worldId(), player.playerId(),
                    command.expectedPlayerRevision(), persistedPlayerRevision);
            throw new StaleInventoryMutationException("player");
        }
        playerStates.replaceExactSnapshotJoiningTransaction(player);
        return Outcome.COMMITTED;
    }
}
