package com.gameexpert.engine;

import com.gameexpert.engine.dispenser.DispenserRules;
import com.gameexpert.engine.effect.PotionRules;
import com.gameexpert.engine.enchant.EnchantmentRules;
import com.gameexpert.engine.hopper.HopperStack;
import com.gameexpert.engine.inventory.PlayerInventory;
import com.gameexpert.terrain.Blocks;
import java.util.Random;

/**
 * [CONTAINER-MENUS] {@code DispenseItemBehavior.bootStrap} registrations of the pinned
 * 26.3-snapshot-7 jar (javap) over the repository's existing systems. The standalone twin is
 * {@code StandaloneDispenseBehaviours.ts}; both classify an item identically
 * ({@link DispenseBehaviours#kindOf}).
 *
 * <ul>
 *   <li>Projectiles ({@code ProjectileDispenseBehavior}): arrow, snowball, the three eggs,
 *       splash potions, wind charge and firework rocket. {@code DispenseConfig.DEFAULT} launches
 *       from {@code getDispensePosition(source, 0.7, (0, 0.1, 0))} with power 1.1 and
 *       uncertainty 6; thrown potions use power 1.375 and uncertainty 3; the wind charge
 *       launches 1.0 out with power 1.0 and uncertainty 6.6666665; the firework rocket launches
 *       just outside the face (0.5000099999997474) with power 0.5 and uncertainty 1.
 *       {@code Projectile.shoot} normalises the facing, adds {@code triangle(0, 0.0172275 *
 *       uncertainty)} per axis and scales by the power; the repository simulation then applies
 *       each kind's own speed scale, the same one its player throw uses.</li>
 *   <li>{@code DispenseItemBehavior$3} (filled buckets, mob buckets) and {@code $4} (empty
 *       bucket), {@code $5} bone meal, {@code $6} TNT, {@code $9} glass bottle,
 *       {@code FlintAndSteelDispenseItemBehavior}, {@code ShulkerBoxDispenseBehavior},
 *       {@code ShearsDispenseItemBehavior} (beehives) and {@code EquipmentDispenseItemBehavior}
 *       (players' armor, elytra, turtle shell, carved pumpkin).</li>
 * </ul>
 * {@code OptionalDispenseItemBehavior}s that fail keep their item and play the 1001 fail click.
 * Every behaviour ends like {@code DefaultDispenseItemBehavior#dispense}: its sound level event
 * ({@code playSound}: 1000 dispense, 1001 fail, 1002 projectile launch, or the projectile's
 * {@code overrideDispenseEvent} — 1004 firework, 1018 fire charge, 1051 wind charge) and the
 * {@code playAnimation} smoke burst 2000 with the facing's 3D data value.
 */
final class DispenseBehaviours {

    enum Kind {
        DEFAULT, PROJECTILE, FILLED_BUCKET, EMPTY_BUCKET, BONE_MEAL, TNT, FLINT_AND_STEEL,
        SHULKER_BOX, GLASS_BOTTLE, SHEARS, CARVED_PUMPKIN, EQUIPMENT, BOAT, HONEYCOMB, BRUSH,
        MOB_EQUIPMENT, HORSE_CHEST, WATER_BOTTLE, ARMOR_STAND, MINECART
    }

    /**
     * {@code DispenseItemBehavior$11} (brush) is one shared {@code OptionalDispenseItemBehavior}
     * whose {@code execute} only ever calls {@code setSuccess(false)}: after the first brush that
     * finds no armadillo, every later brush dispense of the process clicks 1001 (javap).
     */
    private static volatile boolean brushSuccess = true;

    private final WorldRuntime rt;
    private final Random random;

    DispenseBehaviours(WorldRuntime rt, Random random) {
        this.rt = rt;
        this.random = random;
    }

    /** {@code LevelEvent} ids the dispenser family sends (26.3-snapshot-7 {@code LevelEvent}). */
    static final int EVENT_DISPENSE = 1000;
    static final int EVENT_FAIL = 1001;
    static final int EVENT_LAUNCH = 1002;
    static final int EVENT_FIREWORK_SHOOT = 1004;
    static final int EVENT_FIRE_CHARGE_SHOOT = 1018;
    static final int EVENT_WIND_CHARGE_SHOOT = 1051;
    static final int EVENT_SHOOT_SMOKE = 2000;

    /** The registered behaviour of an item type (the fallback is {@link Kind#DEFAULT}). */
    static Kind kindOf(short type) {
        if (type == PlayerInventory.ARROW || PlayerInventory.isTippedArrow(type)
                || type == PlayerInventory.SPECTRAL_ARROW
                || type == PlayerInventory.EXPERIENCE_BOTTLE
                || type == PlayerInventory.FIRE_CHARGE
                || PlayerInventory.isLingeringPotion(type)
                || type == PlayerInventory.SNOWBALL
                || type == PlayerInventory.EGG || type == PlayerInventory.BROWN_EGG
                || type == PlayerInventory.BLUE_EGG || type == PlayerInventory.WIND_CHARGE
                || PotionRules.splashEffect(type) != null
                || PlayerInventory.fireworkFlightDuration(type) > 0) {
            return Kind.PROJECTILE;
        }
        if (type == PlayerInventory.WATER_BUCKET || type == PlayerInventory.LAVA_BUCKET
                || type == PlayerInventory.POWDER_SNOW_BUCKET
                || type == PlayerInventory.COD_BUCKET || type == PlayerInventory.SALMON_BUCKET
                || type == PlayerInventory.PUFFERFISH_BUCKET
                || type == PlayerInventory.TROPICAL_FISH_BUCKET
                || type == PlayerInventory.AXOLOTL_BUCKET
                || type == PlayerInventory.SULFUR_CUBE_BUCKET
                || type == PlayerInventory.TADPOLE_BUCKET) {
            return Kind.FILLED_BUCKET;
        }
        if (type == PlayerInventory.BUCKET) return Kind.EMPTY_BUCKET;
        if (type == PlayerInventory.BONE_MEAL) return Kind.BONE_MEAL;
        if (type == (short) Blocks.TNT) return Kind.TNT;
        if (type == PlayerInventory.FLINT_AND_STEEL) return Kind.FLINT_AND_STEEL;
        if (PlayerInventory.isShulkerBox(type)) return Kind.SHULKER_BOX;
        if (type == PlayerInventory.GLASS_BOTTLE) return Kind.GLASS_BOTTLE;
        if (type == PlayerInventory.SHEARS) return Kind.SHEARS;
        if (type == (short) Blocks.CARVED_PUMPKIN) return Kind.CARVED_PUMPKIN;
        if (PlayerInventory.armorSlot(type) != null) return Kind.EQUIPMENT;
        if (type == PlayerInventory.BOAT) return Kind.BOAT;
        if (type == PlayerInventory.HONEYCOMB) return Kind.HONEYCOMB;
        if (type == PlayerInventory.BRUSH) return Kind.BRUSH;
        if (type == (short) Blocks.CHEST) return Kind.HORSE_CHEST;
        if (type == PlayerInventory.WATER_BOTTLE) return Kind.WATER_BOTTLE;
        if (type == PlayerInventory.ARMOR_STAND) return Kind.ARMOR_STAND;
        if (PlayerInventory.isMinecartItem(type)) return Kind.MINECART;
        if (type == PlayerInventory.SADDLE || type == PlayerInventory.WOLF_ARMOR
                || com.gameexpert.engine.mob.HorseRules.isArmorItem(type)
                || com.gameexpert.engine.mob.LlamaRules.isCarpet(type)) {
            return Kind.MOB_EQUIPMENT;
        }
        return Kind.DEFAULT;
    }

    /** {@code DispenserBlock#dispenseFrom} after the slot was chosen: run the item's behaviour. */
    void dispense(BlockPos pos, ChestInventory inventory, int slot, HopperStack stack, int facing) {
        int tx = pos.x() + DispenserRules.stepX(facing);
        int ty = pos.y() + DispenserRules.stepY(facing);
        int tz = pos.z() + DispenserRules.stepZ(facing);
        boolean inWorld = ty >= Blocks.MIN_Y && ty <= Blocks.MAX_Y;
        WorldTickLoop loop = rt.tickLoop();
        int sound = EVENT_DISPENSE;
        switch (kindOf(stack.itemType())) {
            case PROJECTILE -> sound = projectile(pos, inventory, slot, stack, facing);
            case FILLED_BUCKET -> {
                if (inWorld && loop.dispenserEmptyBucket(tx, ty, tz, stack.itemType(),
                        stack.bucketMobData())) {
                    consumeWithRemainder(pos, inventory, slot, facing, PlayerInventory.BUCKET);
                } else {
                    defaultDispense(pos, inventory, slot, stack, facing);
                }
            }
            case EMPTY_BUCKET -> {
                short filled = inWorld ? loop.dispenserFillBucket(tx, ty, tz)
                        : PlayerInventory.EMPTY;
                if (filled == PlayerInventory.EMPTY) {
                    defaultDispense(pos, inventory, slot, stack, facing);
                } else {
                    consumeWithRemainder(pos, inventory, slot, facing, filled);
                }
            }
            case BONE_MEAL -> {
                if (inWorld && loop.dispenserBoneMeal(tx, ty, tz)) inventory.take(slot, 1);
                else sound = EVENT_FAIL;
            }
            case TNT -> {
                if (inWorld) {
                    loop.dispenserPrimeTnt(tx, ty, tz);
                    inventory.take(slot, 1);
                }
            }
            case FLINT_AND_STEEL -> {
                if (inWorld && loop.dispenserIgnite(tx, ty, tz)) hurt(inventory, slot);
                else sound = EVENT_FAIL;
            }
            case SHULKER_BOX -> {
                if (inWorld && loop.dispenserPlaceShulker(tx, ty, tz, stack.itemType(),
                        stack.shulkerId())) {
                    inventory.take(slot, 1);
                } else {
                    sound = EVENT_FAIL;
                }
            }
            case GLASS_BOTTLE -> {
                short filled = inWorld ? loop.dispenserFillBottle(tx, ty, tz)
                        : PlayerInventory.EMPTY;
                if (filled == PlayerInventory.EMPTY) {
                    defaultDispense(pos, inventory, slot, stack, facing);
                } else {
                    consumeWithRemainder(pos, inventory, slot, facing, filled);
                }
            }
            case SHEARS -> {
                if (inWorld && loop.dispenserShear(tx, ty, tz)) hurt(inventory, slot);
                else sound = EVENT_FAIL;
            }
            case CARVED_PUMPKIN -> {
                // $8: a golem pattern at the target takes the pumpkin as a block; otherwise the
                // equipment dispense, and on failure nothing leaves (fail click).
                if (inWorld && loop.dispenserBuildGolem(tx, ty, tz)) {
                    inventory.take(slot, 1);
                } else if (inWorld && equip(tx, ty, tz, stack)) {
                    inventory.take(slot, 1);
                } else {
                    sound = EVENT_FAIL;
                }
            }
            case EQUIPMENT -> {
                if (inWorld && equip(tx, ty, tz, stack)) {
                    inventory.take(slot, 1);
                } else {
                    defaultDispense(pos, inventory, slot, stack, facing);
                }
            }
            case BOAT -> {
                if (!loop.dispenserPlaceBoat(pos.x(), pos.y(), pos.z(), facing, stack)) {
                    defaultDispense(pos, inventory, slot, stack, facing);
                } else {
                    inventory.take(slot, 1);
                }
            }
            case HONEYCOMB -> {
                // $12 only ever sets success; a block that cannot be waxed falls to the default
                // drop (OptionalDispenseItemBehavior.execute) with the 1000 click.
                if (inWorld && loop.dispenserWax(tx, ty, tz)) inventory.take(slot, 1);
                else defaultDispense(pos, inventory, slot, stack, facing);
            }
            case BRUSH -> {
                if (inWorld && loop.dispenserBrush(tx, ty, tz)) {
                    hurtBy(inventory, slot, 16);
                } else {
                    brushSuccess = false;
                }
                if (!brushSuccess) sound = EVENT_FAIL;
            }
            case WATER_BOTTLE -> {
                // DispenseItemBehavior$13: a water potion onto BlockTags.CONVERTIBLE_TO_MUD turns it
                // to mud with bottle_empty at the dispenser and the glass bottle remainder. Anything
                // else runs a nested DefaultDispenseItemBehavior#dispense, so its click and smoke
                // play before the outer ones (vanilla plays both pairs).
                if (inWorld && loop.dispenserMakeMud(pos.x(), pos.y(), pos.z(), tx, ty, tz)) {
                    consumeWithRemainder(pos, inventory, slot, facing, PlayerInventory.GLASS_BOTTLE);
                } else {
                    defaultDispense(pos, inventory, slot, stack, facing);
                    events(pos, facing, EVENT_DISPENSE);
                }
            }
            case HORSE_CHEST -> {
                // $2: a tamed chested horse (donkey, mule, llama) without a chest takes it;
                // otherwise the default drop.
                if (inWorld && loop.dispenserChestHorse(tx, ty, tz)) inventory.take(slot, 1);
                else defaultDispense(pos, inventory, slot, stack, facing);
            }
            case MOB_EQUIPMENT -> {
                if (inWorld && loop.dispenserEquipMob(tx, ty, tz, snapshot(stack))) {
                    inventory.take(slot, 1);
                } else {
                    defaultDispense(pos, inventory, slot, stack, facing);
                }
            }
            case ARMOR_STAND -> {
                // DispenseItemBehavior (Items.ARMOR_STAND): EntityType.ARMOR_STAND.spawn at the
                // front cell, yRot = direction.toYRot(), no overlap check; the stack shrinks.
                if (inWorld && rt.placedEntities().dispenseArmorStand(tx, ty, tz,
                        HopperSystem.direction3d(facing))) {
                    inventory.take(slot, 1);
                }
            }
            case MINECART -> {
                // MinecartDispenseItemBehavior: onto a rail in front (or below it), else default.
                if (rt.placedEntities().dispenseMinecart(stack.itemType(), pos.x(), pos.y(),
                        pos.z(), DispenserRules.stepX(facing), DispenserRules.stepY(facing),
                        DispenserRules.stepZ(facing))) {
                    inventory.take(slot, 1);
                } else {
                    defaultDispense(pos, inventory, slot, stack, facing);
                }
            }
            case DEFAULT -> defaultDispense(pos, inventory, slot, stack, facing);
        }
        events(pos, facing, sound);
    }

    /** {@code playSound} then {@code playAnimation} (levelEvent 2000 with the facing). */
    void events(BlockPos pos, int facing, int sound) {
        MobSystem mobs = rt.mobSystem();
        mobs.levelEvent(sound, pos.x(), pos.y(), pos.z(), 0);
        mobs.levelEvent(EVENT_SHOOT_SMOKE, pos.x(), pos.y(), pos.z(),
                HopperSystem.direction3d(facing));
    }

    private static PlayerInventory.StackSnapshot snapshot(HopperStack stack) {
        return new PlayerInventory.StackSnapshot(stack.itemType(), 1, stack.durability(),
                stack.enchantments(), stack.mapId(), stack.shulkerId(), stack.bucketMobData(),
                stack.itemComponentData());
    }

    private boolean equip(int x, int y, int z, HopperStack stack) {
        PlayerInventory.StackSnapshot one = new PlayerInventory.StackSnapshot(
                stack.itemType(), 1, stack.durability(), stack.enchantments(), stack.mapId(),
                stack.shulkerId(), stack.bucketMobData(), stack.itemComponentData());
        // [CONTAINER-MENUS] EquipmentDispenseItemBehavior also equips armor stands in front.
        return rt.tickLoop().dispenserEquip(x, y, z, one)
                || rt.placedEntities().dispenseEquipment(x, y, z, one);
    }

    /**
     * {@code DefaultDispenseItemBehavior#dispense}: {@code execute} splits one item off and
     * {@code spawnItem}s it with accuracy 6.
     */
    void defaultDispense(BlockPos pos, ChestInventory inventory, int slot, HopperStack stack,
            int facing) {
        if (inventory.take(slot, 1) != 1) return;
        spawnItem(pos, stack.withCount(1), facing, DispenserRules.DEFAULT_ACCURACY);
    }

    /** {@code DefaultDispenseItemBehavior.spawnItem} at {@code getDispensePosition(source)}. */
    void spawnItem(BlockPos pos, HopperStack one, int facing, int accuracy) {
        double[] position = DispenserRules.dispensePosition(pos.x(), pos.y(), pos.z(), facing,
                DispenserRules.DISPENSE_DISTANCE);
        double[] motion = DispenserRules.spawnItem(position, facing, accuracy, random::nextDouble);
        rt.itemSystem().spawnDispensed(one.itemType(), one.count(), one.durability(),
                one.enchantments(), one.mapId(), one.shulkerId(), one.bucketMobData(),
                one.itemComponentData(), motion[0], motion[1], motion[2],
                motion[3], motion[4], motion[5]);
    }

    /**
     * {@code DefaultDispenseItemBehavior#consumeWithRemainder}: shrink the stack by one; an
     * emptied slot takes the remainder, otherwise {@code DispenserBlockEntity.insertItem}
     * (first empty or mergeable slot in order) and, when that is full, {@code spawnItem}.
     */
    private void consumeWithRemainder(BlockPos pos, ChestInventory inventory, int slot,
            int facing, short remainder) {
        inventory.take(slot, 1);
        int durability = PlayerInventory.isDurable(remainder)
                ? PlayerInventory.initialDurability(remainder) : 0;
        if (inventory.itemType(slot) == PlayerInventory.EMPTY) {
            inventory.putInSlot(slot, remainder, 1, durability, 0L, 0);
            return;
        }
        for (int candidate = 0; candidate < inventory.slots(); candidate++) {
            if (inventory.putInSlot(candidate, remainder, 1, durability, 0L, 0) == 1) return;
        }
        spawnItem(pos, HopperStack.of(remainder, 1), facing, DispenserRules.DEFAULT_ACCURACY);
    }

    /** {@code ItemStack.hurtAndBreak(1, level, null, ...)}: Unbreaking may skip the damage. */
    private void hurt(ChestInventory inventory, int slot) {
        hurtBy(inventory, slot, 1);
    }

    /** {@code ItemStack.hurtAndBreak(amount, ...)}: each point may be skipped by Unbreaking. */
    private void hurtBy(ChestInventory inventory, int slot, int amount) {
        HopperStack stack = HopperSystem.stackAt(inventory, slot);
        if (stack.isEmpty() || !PlayerInventory.isDurable(stack.itemType())) return;
        int unbreaking = EnchantmentRules.enchantLevel(stack.enchantments(),
                EnchantmentRules.UNBREAKING);
        int damage = 0;
        for (int point = 0; point < amount; point++) {
            if (!EnchantmentRules.unbreakingSkipsDurability(unbreaking, false,
                    random.nextInt(EnchantmentRules.MILLI), random.nextInt(Integer.MAX_VALUE))) {
                damage++;
            }
        }
        if (damage == 0) return;
        inventory.take(slot, 1);
        if (stack.durability() > damage) {
            inventory.putInSlot(slot, stack.itemType(), 1, stack.durability() - damage,
                    stack.enchantments(), stack.mapId(), stack.shulkerId(),
                    stack.bucketMobData(), stack.itemComponentData());
        }
    }

    /**
     * {@code ProjectileDispenseBehavior#execute}; returns its {@code playSound} event (1002 or the
     * config's {@code overrideDispenseEvent}).
     */
    private int projectile(BlockPos pos, ChestInventory inventory, int slot, HopperStack stack,
            int facing) {
        short type = stack.itemType();
        double[] position;
        double power;
        double uncertainty;
        // The repository simulation's speed scale of each kind (its player throw's ratio).
        double simulationScale;
        if (type == PlayerInventory.FIRE_CHARGE) {
            // FireChargeItem: getDispensePosition(source, 1.0, ZERO); asProjectile builds a
            // SmallFireball along triangle(step, 0.11485) normalised at acceleration 0.1 and its
            // shoot is empty. overrideDispenseEvent 1018.
            position = DispenserRules.dispensePosition(pos.x(), pos.y(), pos.z(), facing, 1.0);
            double dx = DispenserRules.triangle(DispenserRules.stepX(facing), 0.11485, random::nextDouble);
            double dy = DispenserRules.triangle(DispenserRules.stepY(facing), 0.11485, random::nextDouble);
            double dz = DispenserRules.triangle(DispenserRules.stepZ(facing), 0.11485, random::nextDouble);
            if (rt.mobSystem().launchDispensedFireball(position[0], position[1], position[2],
                    dx, dy, dz) != null) {
                inventory.take(slot, 1);
            }
            return EVENT_FIRE_CHARGE_SHOOT;
        }
        int event = EVENT_LAUNCH;
        if (type == PlayerInventory.WIND_CHARGE) {
            event = EVENT_WIND_CHARGE_SHOOT;
            position = DispenserRules.dispensePosition(pos.x(), pos.y(), pos.z(), facing, 1.0);
            power = 1.0;
            uncertainty = 6.6666665f;
            simulationScale = 2.0;
        } else if (PlayerInventory.fireworkFlightDuration(type) > 0) {
            event = EVENT_FIREWORK_SHOOT;
            position = DispenserRules.dispensePosition(pos.x(), pos.y(), pos.z(), facing,
                    0.5000099999997474);
            power = 0.5;
            uncertainty = 1.0;
            simulationScale = 1.0;
        } else {
            position = DispenserRules.dispensePosition(pos.x(), pos.y(), pos.z(), facing,
                    DispenserRules.DISPENSE_DISTANCE);
            position[1] += 0.1;
            // [CONTAINER-MENUS] ExperienceBottleItem.createDispenseConfig: uncertainty x 0.5,
            // power x 1.25, like the throwable potions.
            boolean potion = PotionRules.splashEffect(type) != null
                    || PlayerInventory.isLingeringPotion(type)
                    || type == PlayerInventory.EXPERIENCE_BOTTLE;
            power = potion ? 1.1f * 1.25f : 1.1f;
            uncertainty = potion ? 6.0f * 0.5f : 6.0f;
            simulationScale = PlayerInventory.isArrowAmmo(type)
                    ? com.gameexpert.engine.mob.ProjectileSim.ARROW_SIMULATION_SCALE : 1.0;
        }
        double[] velocity = shoot(facing, power * simulationScale, uncertainty);
        if (rt.mobSystem().launchDispensedProjectile(type, position[0], position[1], position[2],
                velocity[0], velocity[1], velocity[2]) != null) {
            inventory.take(slot, 1);
        }
        return event;
    }

    /** {@code Projectile.shoot}: normalised facing plus the triangle noise, times the speed. */
    double[] shoot(int facing, double speed, double uncertainty) {
        double deviation = 0.0172275 * uncertainty;
        double x = DispenserRules.triangle(DispenserRules.stepX(facing), deviation, random::nextDouble);
        double y = DispenserRules.triangle(DispenserRules.stepY(facing), deviation, random::nextDouble);
        double z = DispenserRules.triangle(DispenserRules.stepZ(facing), deviation, random::nextDouble);
        return new double[] { x * speed, y * speed, z * speed };
    }
}
