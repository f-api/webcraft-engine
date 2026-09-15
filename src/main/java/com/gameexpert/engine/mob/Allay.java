package com.gameexpert.engine.mob;

import java.util.List;

import com.gameexpert.engine.inventory.PlayerInventory;

/**
 * Java 1.21.4 Allay core: a passive flying item helper bound to the player who gives it one item.
 * Dropped-item discovery/consumption stays behind explicit methods so the engine package can wire its
 * existing item-entity owner without making mob AI depend on that implementation.
 */
public final class Allay extends Mob {
    public static final double ITEM_SEARCH_RANGE = 64.0;
    public static final double LIKED_PLAYER_SEARCH_RANGE = 64.0;
    public static final double ITEM_PICKUP_RANGE = 1.75;
    public static final double RETURN_RANGE = 3.0;
    public static final int REGEN_INTERVAL_TICKS = 10;
    public static final double REGEN_POINTS = 1.0;

    private static final double FOLLOW_START_RANGE = 4.0;
    private static final double FOLLOW_STOP_RANGE = 2.0;
    private static final int ITEM_OFFER_FRESH_TICKS = 2;

    /** [MOB-LOOK] {@code GameEvent.JUKEBOX_PLAY.notificationRadius()} = Allay JukeboxListener radius. */
    public static final int JUKEBOX_LISTENER_RADIUS = 10;
    /** [MOB-LOOK] {@code EntityType.ALLAY.eyeHeight(0.36F)}: the listener's EntityPositionSource height. */
    public static final double EYE_HEIGHT = 0.36;
    /** [MOB-LOOK] {@code tickCount % 20 == 0} dance-stop check = every 10 authority ticks. */
    public static final int DANCE_STOP_CHECK_AUTHORITY_TICKS = 10;
    /** [MOB-LOOK] visual bit: vanilla {@code Allay.DATA_DANCING} ({@link Mob#VISUAL_ALLAY_DANCING}). */
    public static final int VISUAL_DANCING = Mob.VISUAL_ALLAY_DANCING;

    private int regenTicks;
    // [MOB-LOOK] Vanilla Allay#jukeboxPos / DATA_DANCING. Transient like vanilla (neither is saved).
    private boolean dancing;
    private boolean hasJukebox;
    private int jukeboxX;
    private int jukeboxY;
    private int jukeboxZ;
    private int danceTicks;
    private double wanderDy;
    private long wantedItemEntityId;
    private short wantedItemType;
    private int wantedItemDurability;
    private int wantedItemCount;
    private double wantedItemX;
    private double wantedItemY;
    private double wantedItemZ;
    private int wantedItemFreshTicks;
    private boolean pickupRequestOutstanding;
    private int deliveryCount;
    private int deliveryDurability;
    private boolean returnRequestOutstanding;

    public Allay(long id, double x, double y, double z) {
        super(id, MobType.ALLAY, x, y, z);
    }

    @Override
    public String movementMedium() {
        return "fly";
    }

    /** The liked player is stored in the existing durable owner-name column. */
    public String likedPlayerNickname() {
        return ownerNickname();
    }

    /** Player interaction: consume exactly one item only when the Allay's hand is empty. */
    public boolean giveItem(String nickname, short itemType, int durability) {
        if (nickname == null || nickname.isBlank() || deliveryCount != 0
                || !installAllayHeldItem(itemType, durability)) return false;
        setOwnerNickname(nickname);
        return true;
    }

    /** Empty-hand interaction by the liked player; the caller returns this one item to inventory. */
    public EquipmentDrop takeGivenItem(String nickname) {
        if (nickname == null || !nickname.equals(likedPlayerNickname()) || deliveryCount != 0) {
            return null;
        }
        EquipmentDrop item = removeAllayHeldItem();
        if (item == null) return null;
        setOwnerNickname(null);
        clearWantedItem();
        return item;
    }

    /**
     * Offers one live dropped stack to this tick's search. Calls may arrive in any order; the nearest
     * matching stack wins, with entity ID as a stable tie-break. The original given item remains in
     * the visible hand and acts as the match template.
     */
    public boolean offerMatchingDroppedItem(long itemEntityId, short itemType, int durability,
            int count, double itemX, double itemY, double itemZ) {
        if (itemEntityId <= 0 || count <= 0 || deliveryCount != 0
                || heldItem() == PlayerInventory.EMPTY || likedPlayerNickname() == null
                || itemType != heldItem() || durability != heldItemDurability()) return false;
        double distanceSquared = distanceSquared(itemX, itemY, itemZ);
        if (distanceSquared > ITEM_SEARCH_RANGE * ITEM_SEARCH_RANGE) return false;
        if (wantedItemFreshTicks > 0) {
            double currentDistanceSquared = distanceSquared(
                    wantedItemX, wantedItemY, wantedItemZ);
            if (distanceSquared > currentDistanceSquared
                    || distanceSquared == currentDistanceSquared
                    && itemEntityId >= wantedItemEntityId) return false;
        }
        wantedItemEntityId = itemEntityId;
        wantedItemType = itemType;
        wantedItemDurability = durability;
        wantedItemCount = Math.min(count, PlayerInventory.stackMax(itemType));
        wantedItemX = itemX;
        wantedItemY = itemY;
        wantedItemZ = itemZ;
        wantedItemFreshTicks = ITEM_OFFER_FRESH_TICKS;
        pickupRequestOutstanding = false;
        return true;
    }

    /** Acknowledges the amount atomically removed from the requested dropped entity. */
    public boolean confirmPickup(long itemEntityId, int acceptedCount) {
        if (!pickupRequestOutstanding || itemEntityId != wantedItemEntityId
                || acceptedCount <= 0 || acceptedCount > wantedItemCount) return false;
        deliveryCount = acceptedCount;
        deliveryDurability = wantedItemDurability;
        clearWantedItem();
        returnRequestOutstanding = false;
        return true;
    }

    /** Acknowledges only the amount the liked player's inventory actually accepted. */
    public boolean confirmReturnedItems(int acceptedCount) {
        if (!returnRequestOutstanding || acceptedCount <= 0
                || acceptedCount > deliveryCount) return false;
        deliveryCount -= acceptedCount;
        returnRequestOutstanding = false;
        if (deliveryCount == 0) deliveryDurability = 0;
        return true;
    }

    public int deliveryCount() {
        return deliveryCount;
    }

    public int deliveryDurability() {
        return deliveryDurability;
    }

    /** Restores only the durable carried stack; transient pickup/return requests restart clear. */
    void restoreDelivery(int count, int durability) {
        boolean durable = heldItem() != PlayerInventory.EMPTY
                && PlayerInventory.isDurable(heldItem());
        if (count < 0 || count > (heldItem() == PlayerInventory.EMPTY
                        ? 0 : PlayerInventory.stackMax(heldItem()))
                || count > 0 && (likedPlayerNickname() == null
                        || durability != heldItemDurability())
                || count == 0 && durability != 0
                || durable && count > 1) {
            throw new IllegalArgumentException("invalid persisted Allay delivery");
        }
        deliveryCount = count;
        deliveryDurability = durability;
        clearWantedItem();
        returnRequestOutstanding = false;
    }

    @Override
    public boolean shouldPersist() {
        return super.shouldPersist() || heldItem() != PlayerInventory.EMPTY
                || likedPlayerNickname() != null || deliveryCount > 0;
    }

    @Override
    public EquipmentDrop[] pickedEquipmentDrops() {
        int total = (heldItem() == PlayerInventory.EMPTY ? 0 : 1) + deliveryCount;
        if (total == 0) return new EquipmentDrop[0];
        EquipmentDrop[] drops = new EquipmentDrop[total];
        int index = 0;
        if (heldItem() != PlayerInventory.EMPTY) {
            drops[index++] = new EquipmentDrop(heldItem(), heldItemDurability());
        }
        while (index < total) drops[index++] = new EquipmentDrop(heldItem(), deliveryDurability);
        return drops;
    }

    @Override
    public int visualFlags() {
        return dancing ? VISUAL_DANCING : 0;
    }

    public boolean dancing() {
        return dancing;
    }

    /**
     * [MOB-LOOK] GameEventDispatcher range test of the JukeboxListener: the listener's block (its eye
     * position) within {@link #JUKEBOX_LISTENER_RADIUS} blocks (squared block distance) of the jukebox.
     */
    public boolean hearsJukebox(int x, int y, int z) {
        long dx = (long) Math.floor(this.x) - x;
        long dy = (long) Math.floor(this.y + EYE_HEIGHT) - y;
        long dz = (long) Math.floor(this.z) - z;
        return dx * dx + dy * dy + dz * dz <= (long) JUKEBOX_LISTENER_RADIUS * JUKEBOX_LISTENER_RADIUS;
    }

    /** [MOB-LOOK] {@code Allay#setJukeboxPlaying}: JUKEBOX_PLAY (true) / JUKEBOX_STOP_PLAY (false). */
    public void setJukeboxPlaying(int x, int y, int z, boolean playing) {
        if (playing) {
            if (!dancing) {
                hasJukebox = true;
                jukeboxX = x;
                jukeboxY = y;
                jukeboxZ = z;
                dancing = true;
            }
        } else if (!hasJukebox || jukeboxX == x && jukeboxY == y && jukeboxZ == z) {
            hasJukebox = false;
            dancing = false;
        }
    }

    /**
     * [MOB-LOOK] {@code Allay#aiStep}: every 20 MC ticks a dancing allay stops when its jukebox is gone,
     * no longer a jukebox, or not closer than 10 blocks to its centre ({@code closerToCenterThan}).
     */
    void tickDance(MobWorldView world) {
        if (++danceTicks < DANCE_STOP_CHECK_AUTHORITY_TICKS) return;
        danceTicks = 0;
        if (!dancing) return;
        boolean stop = !hasJukebox
                || world.getBlock(jukeboxX, jukeboxY, jukeboxZ) != com.gameexpert.terrain.Blocks.JUKEBOX;
        if (!stop) {
            double dx = jukeboxX + 0.5 - x;
            double dy = jukeboxY + 0.5 - y;
            double dz = jukeboxZ + 0.5 - z;
            stop = dx * dx + dy * dy + dz * dz >= (double) JUKEBOX_LISTENER_RADIUS * JUKEBOX_LISTENER_RADIUS;
        }
        if (stop) {
            dancing = false;
            hasJukebox = false;
        }
    }

    @Override
    public EquipmentDrop[] naturalEquipmentDrops(MobRandom rng, int lootingLevel) {
        return new EquipmentDrop[0];
    }

    @Override
    public List<MobEvent> tick(MobWorldView world, MobRandom rng) {
        if (isDead()) return List.of();
        if (++regenTicks >= REGEN_INTERVAL_TICKS) {
            regenTicks = 0;
            heal(REGEN_POINTS);
        }
        tickDance(world);

        PlayerSnapshot liked = likedPlayer(world);
        if (deliveryCount > 0) {
            if (liked == null) {
                flyWander(world, rng);
                return List.of();
            }
            double distance = distanceTo(liked.x(), liked.y() + 1.0, liked.z());
            if (distance <= RETURN_RANGE) {
                state = MobState.IDLE;
                if (returnRequestOutstanding) return List.of();
                returnRequestOutstanding = true;
                return List.of(new MobEvent.AllayReturnItem(
                        liked.nickname(), heldItem(), deliveryDurability, deliveryCount));
            }
            flyToward(world, liked.x(), liked.y() + 1.0, liked.z(), MobState.CHASE);
            return List.of();
        }

        if (wantedItemFreshTicks > 0) {
            wantedItemFreshTicks--;
            double distance = distanceTo(wantedItemX, wantedItemY, wantedItemZ);
            if (distance <= ITEM_PICKUP_RANGE) {
                state = MobState.IDLE;
                if (pickupRequestOutstanding) return List.of();
                pickupRequestOutstanding = true;
                return List.of(new MobEvent.AllayPickupItem(wantedItemEntityId,
                        wantedItemType, wantedItemDurability, wantedItemCount));
            }
            flyToward(world, wantedItemX, wantedItemY, wantedItemZ, MobState.CHASE);
            return List.of();
        }
        clearWantedItem();

        if (liked != null) {
            double distance = distanceTo(liked.x(), liked.y() + 1.0, liked.z());
            if (distance > FOLLOW_START_RANGE) {
                flyToward(world, liked.x(), liked.y() + 1.0, liked.z(), MobState.CHASE);
                return List.of();
            }
            if (distance <= FOLLOW_STOP_RANGE) {
                state = MobState.IDLE;
                vy = 0.0;
                return List.of();
            }
        }
        flyWander(world, rng);
        return List.of();
    }

    private PlayerSnapshot likedPlayer(MobWorldView world) {
        String nickname = likedPlayerNickname();
        if (nickname == null) return null;
        double maximumSquared = LIKED_PLAYER_SEARCH_RANGE * LIKED_PLAYER_SEARCH_RANGE;
        for (PlayerSnapshot player : world.players()) {
            if (!player.alive() || !nickname.equals(player.nickname())) continue;
            return distanceSquared(player.x(), player.y(), player.z()) <= maximumSquared
                    ? player : null;
        }
        return null;
    }

    private void flyToward(MobWorldView world, double targetX, double targetY, double targetZ,
            MobState nextState) {
        double dx = targetX - x;
        double dy = targetY - y;
        double dz = targetZ - z;
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (distance <= 1e-9) {
            state = MobState.IDLE;
            return;
        }
        double speed = type.baseSpeed() * effectSpeedMultiplier();
        MobPhysics.tickMove(this, world, dx / distance * speed, dy / distance * speed,
                dz / distance * speed, MoveMode.FLY);
        faceToward(targetX, targetZ);
        state = nextState;
    }

    private void flyWander(MobWorldView world, MobRandom rng) {
        boolean choosingDirection = wanderTimer <= 0;
        double[] movement = wander(rng, type.baseSpeed());
        if (choosingDirection) {
            wanderDy = MobPhysics.GRAVITY * MobPhysics.FLY_GRAVITY_SCALE
                    + (rng.nextDouble() - 0.5) * type.baseSpeed();
        }
        MobPhysics.tickMove(this, world, movement[0], wanderDy, movement[1], MoveMode.FLY);
        state = movement[0] == 0.0 && movement[1] == 0.0 ? MobState.IDLE : MobState.WANDER;
    }

    private double distanceTo(double targetX, double targetY, double targetZ) {
        return Math.sqrt(distanceSquared(targetX, targetY, targetZ));
    }

    private double distanceSquared(double targetX, double targetY, double targetZ) {
        double dx = targetX - x;
        double dy = targetY - y;
        double dz = targetZ - z;
        return dx * dx + dy * dy + dz * dz;
    }

    private void clearWantedItem() {
        wantedItemEntityId = 0;
        wantedItemType = PlayerInventory.EMPTY;
        wantedItemDurability = 0;
        wantedItemCount = 0;
        wantedItemFreshTicks = 0;
        pickupRequestOutstanding = false;
    }
}
