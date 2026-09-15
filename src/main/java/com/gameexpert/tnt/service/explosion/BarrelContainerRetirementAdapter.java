package com.gameexpert.tnt.service.explosion;

import com.gameexpert.chest.service.ChestPersistenceService;
import com.gameexpert.tnt.dto.ExplosionSettlementCommand;
import com.gameexpert.tnt.service.PrimedTntPersistenceService;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/** Barrels use the existing 27-slot chest-schema persistence authority. */
@Component
public final class BarrelContainerRetirementAdapter extends
        PrimedTntPersistenceService.TransactionalContainerRetirementAdapter {
    private final ChestPersistenceService chests;

    public BarrelContainerRetirementAdapter(ChestPersistenceService chests) {
        super(ExplosionSettlementCommand.ContainerKind.BARREL);
        this.chests = chests;
    }

    @Override
    protected void performExactRetirement(Long worldId,
            List<ExplosionSettlementCommand.ContainerRetirement> retirements) {
        List<ChestPersistenceService.ExactRetirementTarget> targets = new ArrayList<>();
        for (var retirement : retirements) {
            if (retirement.pairedChest()
                    || !(retirement.state() instanceof ExplosionSettlementCommand.ChestState state)) {
                throw new IllegalArgumentException("barrel retirement requires single ChestState");
            }
            targets.add(new ChestPersistenceService.ExactRetirementTarget(
                    retirement.rowId(), retirement.x(), retirement.y(), retirement.z(),
                    retirement.expectedRevision(), state.containerSize(),
                    ChestContainerRetirementAdapter.exactItems(retirement)));
        }
        chests.retireExactJoiningTransaction(worldId, List.copyOf(targets));
    }
}
