package com.gameexpert.enchanting.entity;

import com.gameexpert.chest.entity.ChestItem;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Durable, coordinate-owned snapshot of the two enchanting-table inventory slots. */
@Getter
@Entity
@Table(
        name = "world_enchanting_tables",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_world_enchanting_table_pos",
                columnNames = { "world_id", "pos_x", "pos_y", "pos_z" }))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorldEnchantingTable {

    private static final int SLOT_COUNT = 2;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long worldId;

    @Column(nullable = false)
    private int posX;

    @Column(nullable = false)
    private int posY;

    @Column(nullable = false)
    private int posZ;

    @Column(nullable = false, columnDefinition = "bigint not null default 0")
    private long persistenceRevision;

    @Getter(AccessLevel.NONE)
    @ElementCollection(fetch = FetchType.EAGER)
    @OrderBy("slot ASC")
    @CollectionTable(
            name = "world_enchanting_table_items",
            joinColumns = @JoinColumn(name = "table_id"),
            uniqueConstraints = @UniqueConstraint(
                    name = "uk_world_enchanting_table_item_slot",
                    columnNames = { "table_id", "slot" }))
    private List<ChestItem> items = new ArrayList<>();

    public WorldEnchantingTable(Long worldId, int x, int y, int z) {
        requireWorldId(worldId);
        this.worldId = worldId;
        this.posX = x;
        this.posY = y;
        this.posZ = z;
    }

    public boolean replaceIfNewer(List<ChestItem> snapshot, long revision) {
        if (revision < 0L) {
            throw new IllegalArgumentException("enchanting-table revision must be non-negative");
        }
        List<ChestItem> canonical = canonicalize(snapshot);
        if (revision <= persistenceRevision) return false;
        items.clear();
        items.addAll(canonical);
        persistenceRevision = revision;
        return true;
    }

    public boolean matches(List<ChestItem> snapshot, long revision) {
        List<ChestItem> canonical = canonicalize(snapshot);
        return revision >= 0L && persistenceRevision == revision
                && canonicalize(items).equals(canonical);
    }

    /** Returns a canonical, deeply detached snapshot of every occupied slot. */
    public List<ChestItem> snapshotItems() {
        return List.copyOf(canonicalize(items));
    }

    @PostLoad
    private void validateAfterLoad() {
        try {
            validateState();
        } catch (RuntimeException malformed) {
            throw new IllegalStateException("invalid world enchanting-table state", malformed);
        }
    }

    @PrePersist
    @PreUpdate
    private void validateBeforeWrite() {
        validateState();
        List<ChestItem> canonical = canonicalize(items);
        items.clear();
        items.addAll(canonical);
    }

    private void validateState() {
        requireWorldId(worldId);
        if (persistenceRevision < 0L) {
            throw new IllegalArgumentException("enchanting-table revision must be non-negative");
        }
        canonicalize(items);
    }

    private static List<ChestItem> canonicalize(List<ChestItem> source) {
        if (source == null) {
            throw new IllegalArgumentException("enchanting-table items are required");
        }
        boolean[] occupied = new boolean[SLOT_COUNT];
        List<ChestItem> canonical = new ArrayList<>(source.size());
        for (ChestItem item : source) {
            if (item == null) {
                throw new IllegalArgumentException("enchanting-table item is required");
            }
            int slot = item.getSlot();
            if (slot < 0 || slot >= SLOT_COUNT) {
                throw new IllegalArgumentException("enchanting-table item slot is outside 0..1");
            }
            if (occupied[slot]) {
                throw new IllegalArgumentException("enchanting-table item slot is duplicated");
            }
            occupied[slot] = true;
            canonical.add(copy(item));
        }
        canonical.sort(Comparator.comparingInt(ChestItem::getSlot));
        return canonical;
    }

    private static ChestItem copy(ChestItem item) {
        return new ChestItem(item.getSlot(), item.getItemType(), item.getItemCount(),
                item.getDurability(), item.getEnchantments(), item.getMapId(),
                item.getShulkerId(), item.getBucketMobData(), item.getItemComponentData());
    }

    private static void requireWorldId(Long worldId) {
        if (worldId == null || worldId <= 0L) {
            throw new IllegalArgumentException("positive world ID required");
        }
    }
}
