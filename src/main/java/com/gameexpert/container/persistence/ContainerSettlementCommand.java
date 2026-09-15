package com.gameexpert.container.persistence;

import com.gameexpert.state.service.inventory.InventoryMutationTarget;
import java.util.List;
import lombok.Getter;

/** 재시도 가능한 컨테이너 정산의 안정 ID, 예상 DB 세대와 새 정확 스냅샷. */
@Getter
public final class ContainerSettlementCommand {

    private final long settlementId;
    private final InventoryMutationTarget target;
    private final List<Long> expectedRevisions;
    private final String participantFingerprint;

    public ContainerSettlementCommand(long settlementId, InventoryMutationTarget target,
            List<Long> expectedRevisions, String participantFingerprint) {
        if (settlementId <= 0 || settlementId == Long.MAX_VALUE
                || target == null || expectedRevisions == null
                || expectedRevisions.isEmpty() || participantFingerprint == null
                || participantFingerprint.isBlank()) {
            throw new IllegalArgumentException("settlement id, target and expected revisions are required");
        }
        List<Long> copiedRevisions = List.copyOf(expectedRevisions);
        if (copiedRevisions.stream().anyMatch(revision -> revision == null || revision < 0
                || revision == Long.MAX_VALUE)) {
            throw new IllegalArgumentException("expected revisions must be non-negative");
        }
        int expectedCount = target instanceof InventoryMutationTarget.Chests chests
                ? chests.halves().size() : 1;
        if (copiedRevisions.size() != expectedCount) {
            throw new IllegalArgumentException("expected revision count does not match target");
        }
        this.settlementId = settlementId;
        this.target = target;
        this.expectedRevisions = copiedRevisions;
        this.participantFingerprint = participantFingerprint;
    }
}
