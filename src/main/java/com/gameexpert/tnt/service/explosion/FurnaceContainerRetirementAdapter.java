package com.gameexpert.tnt.service.explosion;

import com.gameexpert.furnace.service.FurnacePersistenceService;
import com.gameexpert.tnt.dto.ExplosionSettlementCommand;
import com.gameexpert.tnt.service.PrimedTntPersistenceService;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/** Production exact-retirement bridge for every furnace variant. */
@Component
public final class FurnaceContainerRetirementAdapter extends
        PrimedTntPersistenceService.TransactionalContainerRetirementAdapter {
    private final FurnacePersistenceService furnaces;

    public FurnaceContainerRetirementAdapter(FurnacePersistenceService furnaces) {
        super(ExplosionSettlementCommand.ContainerKind.FURNACE);
        this.furnaces = furnaces;
    }

    @Override
    protected void performExactRetirement(Long worldId,
            List<ExplosionSettlementCommand.ContainerRetirement> retirements) {
        List<FurnacePersistenceService.ExactRetirementTarget> targets = new ArrayList<>();
        for (var retirement : retirements) {
            if (!(retirement.state() instanceof ExplosionSettlementCommand.FurnaceState state)) {
                throw new IllegalArgumentException("furnace retirement requires FurnaceState");
            }
            List<FurnacePersistenceService.ExactSlot> slots = retirement.drops().stream()
                    .map(drop -> new FurnacePersistenceService.ExactSlot(
                            drop.source().itemType(), drop.source().count(),
                            drop.source().durability(), drop.source().enchantments(),
                            drop.source().mapId(), drop.source().shulkerId(),
                            drop.source().bucketMobData(), drop.source().itemComponentData()))
                    .toList();
            targets.add(new FurnacePersistenceService.ExactRetirementTarget(
                    retirement.rowId(), retirement.x(), retirement.y(), retirement.z(),
                    retirement.expectedRevision(), state.variantCode(), state.burnTicks(),
                    state.burnTotalTicks(), state.cookTicks(), state.xpMilli(), slots));
        }
        furnaces.retireExactJoiningTransaction(worldId, List.copyOf(targets));
    }
}
