package com.gameexpert.tnt.service.explosion;

import com.gameexpert.dispenser.entity.WorldDispenser;
import com.gameexpert.dispenser.repository.WorldDispenserRepository;
import com.gameexpert.dispenser.service.DispenserPersistenceService;
import com.gameexpert.state.service.inventory.StaleInventoryMutationException;
import com.gameexpert.tnt.dto.ExplosionSettlementCommand;
import com.gameexpert.tnt.service.PrimedTntPersistenceService;
import java.util.List;
import org.springframework.stereotype.Component;

/** Locks every exact dispenser row incarnation, validates all of them, then retires the batch. */
@Component
public final class DispenserContainerRetirementAdapter extends
        PrimedTntPersistenceService.TransactionalContainerRetirementAdapter {
    private final WorldDispenserRepository dispensers;
    private final DispenserPersistenceService persistence;

    public DispenserContainerRetirementAdapter(WorldDispenserRepository dispensers,
            DispenserPersistenceService persistence) {
        super(ExplosionSettlementCommand.ContainerKind.DISPENSER);
        this.dispensers = dispensers;
        this.persistence = persistence;
    }

    @Override
    protected void performExactRetirement(Long worldId,
            List<ExplosionSettlementCommand.ContainerRetirement> retirements) {
        for (var retirement : retirements) {
            if (retirement.pairedChest()
                    || !(retirement.state() instanceof ExplosionSettlementCommand.ChestState state)
                    || state.containerSize() != WorldDispenser.CONTAINER_SIZE) {
                throw new IllegalArgumentException("dispenser retirement requires nine-slot state");
            }
            WorldDispenser entity = dispensers.findLockedById(retirement.rowId()).orElse(null);
            if (entity == null || !entity.matchesExact(worldId,
                    retirement.x(), retirement.y(), retirement.z(), retirement.rowId(),
                    retirement.expectedRevision(), retirement.aggregateId(),
                    ChestContainerRetirementAdapter.exactItems(retirement))) {
                throw new StaleInventoryMutationException("stale exact dispenser retirement at "
                        + retirement.x() + ":" + retirement.y() + ":" + retirement.z());
            }
        }
        PrimedTntPersistenceService.ContainerRetirementWitness witness =
                persistence.preflightExact(worldId, retirements);
        if (persistence.retireExact(worldId, retirements, witness) != witness) {
            throw new IllegalStateException("dispenser retirement capability changed");
        }
    }
}
