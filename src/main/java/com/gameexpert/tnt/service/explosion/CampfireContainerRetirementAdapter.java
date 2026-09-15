package com.gameexpert.tnt.service.explosion;

import com.gameexpert.campfire.service.CampfirePersistenceService;
import com.gameexpert.tnt.dto.ExplosionSettlementCommand;
import com.gameexpert.tnt.service.PrimedTntPersistenceService;
import java.util.List;
import org.springframework.stereotype.Component;

/** Production exact-retirement bridge for campfire slot/progress state. */
@Component
public final class CampfireContainerRetirementAdapter extends
        PrimedTntPersistenceService.TransactionalContainerRetirementAdapter {
    private final CampfirePersistenceService campfires;

    public CampfireContainerRetirementAdapter(CampfirePersistenceService campfires) {
        super(ExplosionSettlementCommand.ContainerKind.CAMPFIRE);
        this.campfires = campfires;
    }

    @Override
    protected void performExactRetirement(Long worldId,
            List<ExplosionSettlementCommand.ContainerRetirement> retirements) {
        for (var retirement : retirements) {
            if (!(retirement.state() instanceof ExplosionSettlementCommand.CampfireState)) {
                throw new IllegalArgumentException("campfire retirement requires CampfireState");
            }
            short[] itemTypes = new short[retirement.drops().size()];
            int[] cookTicks = new int[retirement.drops().size()];
            for (var drop : retirement.drops()) {
                int slot = drop.source().slot();
                itemTypes[slot] = drop.source().itemType();
                cookTicks[slot] = drop.source().progress();
            }
            campfires.retireExactJoiningTransaction(worldId,
                    new CampfirePersistenceService.ExactRetirementTarget(
                            retirement.rowId(), retirement.x(), retirement.y(), retirement.z(),
                            retirement.expectedRevision(), itemTypes, cookTicks));
        }
    }
}
