package com.gameexpert.placed.dto;

import com.gameexpert.chest.entity.ChestItem;
import java.util.List;

/**
 * [CONTAINER-MENUS] The durable state of one player-placed armor stand or minecart. Riders and hit
 * wobbles are session state and are not stored, like a boat's driver.
 *
 * <p>{@code items} carries the armor stand's six equipment slots (0 main hand, 1 off hand, 2 feet,
 * 3 legs, 4 chest, 5 head, vanilla {@code EquipmentSlot} order) or a container minecart's cargo
 * slots (27 chest, 5 hopper) as {@link ChestItem} rows keyed by slot.
 *
 * @param kind      protocol kind ({@code armor_stand}, {@code minecart}, {@code chest_minecart},
 *                  {@code hopper_minecart}, {@code furnace_minecart}, {@code tnt_minecart})
 * @param yaw       vanilla {@code yRot} in degrees
 * @param flags     armor stand client flags (1 small, 4 show arms, 8 no base plate); minecart
 *                  bit 0 = flipped, bit 1 = hopper disabled
 * @param fuel      furnace minecart fuel ticks
 * @param fuse      TNT minecart fuse ticks, -1 when not primed
 */
@lombok.Value
@lombok.experimental.Accessors(fluent = true)
public class PlacedEntitySnapshot {
    long entityId;
    String kind;
    double x;
    double y;
    double z;
    float yaw;
    float pitch;
    double velocityX;
    double velocityY;
    double velocityZ;
    int flags;
    int fuel;
    int fuse;
    double pushX;
    double pushZ;
    List<ChestItem> items;
    float health;
    int fireTicks;
    long mobPassengerId;

    public PlacedEntitySnapshot(long entityId, String kind, double x, double y, double z, float yaw, float pitch, double velocityX, double velocityY, double velocityZ, int flags, int fuel, int fuse, double pushX, double pushZ, List<ChestItem> items) {
        this(entityId, kind, x, y, z, yaw, pitch, velocityX, velocityY, velocityZ, flags, fuel, fuse,
                pushX, pushZ, items, 20.0F, 0);
    }

    public PlacedEntitySnapshot(long entityId, String kind, double x, double y, double z, float yaw,
            float pitch, double velocityX, double velocityY, double velocityZ, int flags, int fuel,
            int fuse, double pushX, double pushZ, List<ChestItem> items, float health, int fireTicks) {
        this(entityId, kind, x, y, z, yaw, pitch, velocityX, velocityY, velocityZ, flags, fuel,
                fuse, pushX, pushZ, items, health, fireTicks, 0);
    }

    public PlacedEntitySnapshot(long entityId, String kind, double x, double y, double z, float yaw,
            float pitch, double velocityX, double velocityY, double velocityZ, int flags, int fuel,
            int fuse, double pushX, double pushZ, List<ChestItem> items, float health, int fireTicks,
            long mobPassengerId) {
        if (mobPassengerId < 0 || mobPassengerId != 0 && !"minecart".equals(kind)) {
            throw new IllegalArgumentException("invalid minecart passenger");
        }
        if (entityId <= 0) throw new IllegalArgumentException("placed entity id must be positive");
        if (kind == null || kind.isEmpty()) throw new IllegalArgumentException("kind is required");
        items = items == null ? List.of() : List.copyOf(items);

        this.entityId = entityId;
        this.kind = kind;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
        this.velocityX = velocityX;
        this.velocityY = velocityY;
        this.velocityZ = velocityZ;
        this.flags = flags;
        this.fuel = fuel;
        this.fuse = fuse;
        this.pushX = pushX;
        this.pushZ = pushZ;
        this.items = items;
        this.health = health;
        this.fireTicks = fireTicks;
        this.mobPassengerId = mobPassengerId;
    }

}
