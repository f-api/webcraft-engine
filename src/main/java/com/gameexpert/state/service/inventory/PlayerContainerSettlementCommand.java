package com.gameexpert.state.service.inventory;

import com.gameexpert.container.persistence.ContainerSettlementCommand;
import java.util.List;

/** 플레이어와 블록 컨테이너를 한 영수증/커밋으로 교체하는 명령. */
public final class PlayerContainerSettlementCommand {
    private final long expectedPlayerRevision;
    private final PlayerInventoryMutationSnapshot player;
    private final ContainerSettlementCommand container;

    public PlayerContainerSettlementCommand(long settlementId, long expectedPlayerRevision,
            PlayerInventoryMutationSnapshot player, InventoryMutationTarget target,
            List<Long> expectedContainerRevisions) {
        if (expectedPlayerRevision < 0 || expectedPlayerRevision == Long.MAX_VALUE
                || player == null
                || player.revision() != Math.addExact(expectedPlayerRevision, 1)) {
            throw new IllegalArgumentException("player revision must advance exactly once");
        }
        this.expectedPlayerRevision = expectedPlayerRevision;
        this.player = player;
        this.container = new ContainerSettlementCommand(settlementId, target,
                expectedContainerRevisions,
                expectedPlayerRevision + ":" + player.fingerprint());
    }

    public long expectedPlayerRevision() { return expectedPlayerRevision; }
    public PlayerInventoryMutationSnapshot player() { return player; }
    public ContainerSettlementCommand container() { return container; }
}
