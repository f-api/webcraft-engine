package com.gameexpert.placed.entity;

import com.gameexpert.chest.entity.ChestItem;
import com.gameexpert.placed.dto.PlacedEntitySnapshot;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** [CONTAINER-MENUS] One stored armor stand or minecart; see {@link PlacedEntitySnapshot}. */
@Getter
@Entity
@org.hibernate.annotations.DynamicUpdate
@Table(name = "world_placed_entities", uniqueConstraints = @UniqueConstraint(
        name = "uk_world_placed_entity_id", columnNames = { "world_id", "entity_id" }))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorldPlacedEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "world_id", nullable = false)
    private Long worldId;
    @Column(name = "entity_id", nullable = false)
    private long entityId;
    @Column(nullable = false, length = 32)
    private String kind;
    @Column(nullable = false)
    private double posX;
    @Column(nullable = false)
    private double posY;
    @Column(nullable = false)
    private double posZ;
    @Column(nullable = false)
    private float yaw;
    @Column(nullable = false)
    private float pitch;
    @Column(nullable = false)
    private double velocityX;
    @Column(nullable = false)
    private double velocityY;
    @Column(nullable = false)
    private double velocityZ;
    @Column(nullable = false)
    private int flags;
    @Column(nullable = false)
    private int fuel;
    @Column(nullable = false)
    private int fuse;
    @Column(nullable = false)
    private double pushX;
    @Column(nullable = false)
    private double pushZ;
    private Float health;
    private Integer fireTicks;
    private Long mobPassengerId;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "world_placed_entity_items",
            joinColumns = @JoinColumn(name = "placed_entity_row_id"))
    private List<ChestItem> items = new ArrayList<>();

    public WorldPlacedEntity(Long worldId, PlacedEntitySnapshot snapshot) {
        this.worldId = worldId;
        this.entityId = snapshot.entityId();
        this.kind = snapshot.kind();
        apply(snapshot);
    }

    /** The entity identity and kind are the row identity and cannot change. */
    public void apply(PlacedEntitySnapshot snapshot) {
        if (snapshot.entityId() != entityId || !snapshot.kind().equals(kind)) {
            throw new IllegalArgumentException("placed entity identity cannot change");
        }
        posX = snapshot.x();
        posY = snapshot.y();
        posZ = snapshot.z();
        yaw = snapshot.yaw();
        pitch = snapshot.pitch();
        velocityX = snapshot.velocityX();
        velocityY = snapshot.velocityY();
        velocityZ = snapshot.velocityZ();
        flags = snapshot.flags();
        fuel = snapshot.fuel();
        fuse = snapshot.fuse();
        pushX = snapshot.pushX();
        pushZ = snapshot.pushZ();
        health = snapshot.health();
        fireTicks = snapshot.fireTicks();
        mobPassengerId = snapshot.mobPassengerId() == 0 ? null : snapshot.mobPassengerId();
        items.clear();
        items.addAll(snapshot.items());
    }

    public boolean matches(PlacedEntitySnapshot snapshot) {
        return toSnapshot().equals(snapshot);
    }

    public PlacedEntitySnapshot toSnapshot() {
        List<ChestItem> ordered = new ArrayList<>(items);
        ordered.sort(Comparator.comparingInt(ChestItem::getSlot));
        return new PlacedEntitySnapshot(entityId, kind, posX, posY, posZ, yaw, pitch,
                velocityX, velocityY, velocityZ, flags, fuel, fuse, pushX, pushZ, ordered,
                health == null ? 20.0F : health, fireTicks == null ? 0 : fireTicks, mobPassengerId == null ? 0 : mobPassengerId);
    }
}
