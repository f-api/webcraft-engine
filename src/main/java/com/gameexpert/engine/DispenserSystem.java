package com.gameexpert.engine;

import com.gameexpert.engine.dispenser.DispenserRules;
import com.gameexpert.engine.hopper.HopperStack;
import com.gameexpert.engine.inventory.PlayerInventory;
import com.gameexpert.engine.persistence.animal.AnimalBlockTickPersistenceService;
import com.gameexpert.terrain.Blocks;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * [CONTAINER-MENUS] Owner of the pinned 26.3-snapshot-7 {@code DispenserBlock} / {@code DropperBlock}
 * redstone behaviour (javap): the neighbour-update edge on {@code TRIGGERED}, the four-game-tick
 * scheduled dispense and {@code dispenseFrom}. The standalone twin lives in
 * {@code StandaloneWorldRuntime} over {@code StandaloneDispenserRules.ts}.
 *
 * <p>There is no redstone dust network. {@code Level.hasNeighborSignal} is the repository's
 * signal model (a powered poplar button or pressure plate next to the cell), the same one the
 * shelf and hopper lanes read. {@link WorldTickLoop#processSignalNeighbourUpdates} delivers
 * {@code neighborChanged} exactly to the cells vanilla notifies: the six neighbours of every
 * changed cell ({@code Level.updateNeighborsAt}) and, for a pressure plate that is or was there,
 * also the neighbours of the cell below it ({@code BasePressurePlateBlock#updateNeighbours}); a
 * dispenser then reads {@code hasNeighborSignal(pos) || hasNeighborSignal(pos.above())}.
 *
 * <p>Scheduled dispenses persist like vanilla's chunk-saved block ticks: every schedule is an
 * absolute-game-time row of the durable block-tick table
 * ({@link AnimalBlockTickPersistenceService.Kind#DISPENSER_TICK}) and a restart reloads it.
 */
final class DispenserSystem {

    private final WorldRuntime rt;
    /** Due game time of each scheduled dispense, in scheduling order. */
    private final LinkedHashMap<BlockPos, Long> scheduled = new LinkedHashMap<>();
    private final Random random = new Random();
    private DispenseBehaviours behaviours;

    DispenserSystem(WorldRuntime rt) {
        this.rt = rt;
    }

    DispenseBehaviours behaviours() {
        if (behaviours == null) behaviours = new DispenseBehaviours(rt, random);
        return behaviours;
    }

    /** Test/diagnostic view of the pending dispenses. */
    Map<BlockPos, Long> scheduledDispenses() {
        return java.util.Collections.unmodifiableMap(scheduled);
    }

    /** Restores the durable schedule rows of this system's kind (world activation). */
    void hydrate(List<AnimalBlockTickPersistenceService.ScheduledTick> persisted) {
        scheduled.clear();
        for (AnimalBlockTickPersistenceService.ScheduledTick tick : persisted) {
            if (tick.kind() != AnimalBlockTickPersistenceService.Kind.DISPENSER_TICK) continue;
            scheduled.put(new BlockPos(tick.x(), tick.y(), tick.z()), tick.dueTick());
        }
    }

    /** {@code LevelTicks.schedule}: an already scheduled cell keeps its first due time. */
    private void schedule(BlockPos pos, long due) {
        if (scheduled.putIfAbsent(pos, due) != null) return;
        persist(pos, due);
    }

    private void persist(BlockPos pos, long due) {
        AnimalBlockTickPersistenceService.WorldStore store = rt.animalBlockTickStore();
        if (store != null) {
            store.upsert(pos.x(), pos.y(), pos.z(),
                    AnimalBlockTickPersistenceService.Kind.DISPENSER_TICK, due);
        }
    }

    private void forgetRow(BlockPos pos) {
        AnimalBlockTickPersistenceService.WorldStore store = rt.animalBlockTickStore();
        if (store != null) {
            store.deleteAfterCommittedHatch(pos.x(), pos.y(), pos.z(),
                    AnimalBlockTickPersistenceService.Kind.DISPENSER_TICK);
        }
    }

    /** {@code DispenserBlock#neighborChanged} at a notified cell. */
    void evaluate(int x, int y, int z) {
        if (y < Blocks.MIN_Y || y > Blocks.MAX_Y) return;
        int block = WorldTickLoop.residentBlockType(rt.accessor(), x, y, z);
        if (!DispenserRules.isDispenserFamily(block)) return;
        int state = rt.blockStates().get(x, y, z, block);
        boolean powered = rt.tickLoop().hopperHasNeighborSignal(x, y, z)
                || y + 1 <= Blocks.MAX_Y && rt.tickLoop().hopperHasNeighborSignal(x, y + 1, z);
        switch (DispenserRules.edge(state, powered)) {
            case TRIGGER -> {
                BlockPos pos = new BlockPos(x, y, z);
                schedule(pos, rt.clock().gameTimeMcTicks() + DispenserRules.TRIGGER_DURATION);
                setState(pos, block, DispenserRules.withTriggered(state, true));
            }
            case RELEASE -> setState(new BlockPos(x, y, z), block,
                    DispenserRules.withTriggered(state, false));
            case NONE -> { }
        }
    }

    private void setState(BlockPos pos, int block, int next) {
        rt.setBlockState(pos.x(), pos.y(), pos.z(), block, next);
        rt.tickBlockChanges().put(pos, (short) block);
    }

    /** Runs every dispense whose scheduled game time has come, in scheduling order. */
    void tick() {
        if (scheduled.isEmpty()) return;
        long gameTime = rt.clock().gameTimeMcTicks();
        List<BlockPos> due = new ArrayList<>();
        Iterator<Map.Entry<BlockPos, Long>> iterator = scheduled.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<BlockPos, Long> entry = iterator.next();
            if (entry.getValue() > gameTime) continue;
            due.add(entry.getKey());
            iterator.remove();
        }
        for (BlockPos pos : due) {
            forgetRow(pos);
            int block = WorldTickLoop.residentBlockType(rt.accessor(), pos.x(), pos.y(), pos.z());
            // LevelTicks only runs a scheduled tick while the cell still holds that block.
            if (!DispenserRules.isDispenserFamily(block)) continue;
            dispenseFrom(pos, block, gameTime);
        }
    }

    /** Waits for the owner (a player settlement or a LOOT first open) and runs next tick. */
    private void retry(BlockPos pos, long gameTime) {
        scheduled.putIfAbsent(pos, gameTime + 1);
        persist(pos, scheduled.get(pos));
    }

    /** {@code DispenserBlock#dispenseFrom} / {@code DropperBlock#dispenseFrom}. */
    void dispenseFrom(BlockPos pos, int block, long gameTime) {
        int x = pos.x(), y = pos.y(), z = pos.z();
        // A player's container settlement owns this inventory until it lands; retry next tick.
        if (rt.containerSettlementTargets(x, y, z)) {
            retry(pos, gameTime);
            return;
        }
        ChestInventory inventory = rt.openContainer(x, y, z);
        if (inventory == null) {
            // getRandomSlot unpacks the LOOT table first; here that is the durable first-open
            // writer, which completes on a later owner turn.
            rt.hopperSystem().requestLootMaterialization(pos, block, List.of(pos));
            retry(pos, gameTime);
            return;
        }
        if (inventory.slots() != DispenserRules.SLOTS) return;
        int state = rt.blockStates().get(x, y, z, block);
        int facing = DispenserRules.facing(state);
        boolean[] occupied = new boolean[DispenserRules.SLOTS];
        for (int slot = 0; slot < occupied.length; slot++) {
            occupied[slot] = inventory.itemType(slot) != PlayerInventory.EMPTY
                    && inventory.count(slot) > 0;
        }
        int slot = DispenserRules.randomSlot(occupied, random::nextInt);
        if (slot < 0) {
            // levelEvent 1001 (DISPENSER_FAIL click); GameEvent.BLOCK_ACTIVATE has no lane.
            rt.mobSystem().levelEvent(DispenseBehaviours.EVENT_FAIL, x, y, z, 0);
            return;
        }
        // DropperBlock#dispenseFrom: HopperBlockEntity.getContainerAt unpacks a LOOT container
        // in front synchronously. Its durable first open lands on a later owner turn here, so
        // the dropper waits for it instead of spitting the item out.
        if (block == Blocks.DROPPER && rt.hopperSystem().frontContainerPending(pos, facing)) {
            retry(pos, gameTime);
            return;
        }
        HopperStack stack = HopperSystem.stackAt(inventory, slot);
        if (block == Blocks.DROPPER) {
            dropperDispense(pos, inventory, slot, stack, facing);
        } else {
            behaviours().dispense(pos, inventory, slot, stack, facing);
        }
        rt.chestStorage().markDirty(x, y, z);
        rt.tickLoop().publishHopperChestChange(pos);
    }

    /**
     * {@code DropperBlock#dispenseFrom}: push one item into the container in front through
     * {@code HopperBlockEntity.addItem} (no sound); without a container,
     * {@code DISPENSE_BEHAVIOUR} ({@code DefaultDispenseItemBehavior}) drops it with its 1000
     * click and 2000 smoke.
     */
    private void dropperDispense(BlockPos pos, ChestInventory inventory, int slot,
            HopperStack stack, int facing) {
        HopperStack remainder = rt.hopperSystem().dropperInsert(pos, inventory,
                stack.withCount(1), facing);
        if (remainder == null) {
            behaviours().defaultDispense(pos, inventory, slot, stack, facing);
            behaviours().events(pos, facing, DispenseBehaviours.EVENT_DISPENSE);
            return;
        }
        if (remainder.isEmpty()) inventory.take(slot, 1);
    }
}
