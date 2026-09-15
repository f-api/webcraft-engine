package com.gameexpert.tnt.service.explosion;

import com.gameexpert.chest.entity.ChestItem;
import com.gameexpert.chest.service.ChestPersistenceService;
import com.gameexpert.engine.inventory.PlayerInventory;
import com.gameexpert.tnt.dto.ExplosionSettlementCommand;
import com.gameexpert.tnt.service.PrimedTntPersistenceService;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/** Production exact-retirement bridge for coordinate chest aggregates. */
@Component
public final class ChestContainerRetirementAdapter extends
        PrimedTntPersistenceService.TransactionalContainerRetirementAdapter {
    private final ChestPersistenceService chests;

    public ChestContainerRetirementAdapter(ChestPersistenceService chests) {
        super(ExplosionSettlementCommand.ContainerKind.CHEST);
        this.chests = chests;
    }

    @Override
    protected void performExactRetirement(Long worldId,
            List<ExplosionSettlementCommand.ContainerRetirement> retirements) {
        List<ChestPersistenceService.ExactRetirementTarget> targets = new ArrayList<>();
        for (var retirement : retirements) {
            if (!(retirement.state() instanceof ExplosionSettlementCommand.ChestState state)) {
                throw new IllegalArgumentException("chest retirement requires ChestState");
            }
            targets.add(new ChestPersistenceService.ExactRetirementTarget(
                    retirement.rowId(), retirement.x(), retirement.y(), retirement.z(),
                    retirement.expectedRevision(), state.containerSize(),
                    exactItems(retirement)));
        }
        chests.retireExactJoiningTransaction(worldId, List.copyOf(targets));
    }

    static List<ChestItem> exactItems(
            ExplosionSettlementCommand.ContainerRetirement retirement) {
        List<ChestItem> items = new ArrayList<>();
        for (var drop : retirement.drops()) {
            var source = drop.source();
            if (source.isEmpty()) continue;
            items.add(new ChestItem(source.slot(), source.itemType(), source.count(),
                    PlayerInventory.isDurable(source.itemType()) ? source.durability() : null,
                    source.enchantments() == 0L ? null : source.enchantments(),
                    source.mapId() == 0 ? null : source.mapId(),
                    source.shulkerId() == 0 ? null : source.shulkerId(),
                    source.bucketMobData(), source.itemComponentData()));
        }
        return List.copyOf(items);
    }
}
