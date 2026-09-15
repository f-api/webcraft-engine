package com.gameexpert.lectern.entity;

import com.gameexpert.engine.inventory.PlayerInventory;
import com.gameexpert.lectern.dto.LecternBlockData;
import com.gameexpert.api.persistence.WorldAccess;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "world_lecterns",
        uniqueConstraints = @UniqueConstraint(name = "uq_world_lectern_xyz",
                columnNames = {"world_id", "x", "y", "z"}),
        indexes = @Index(name = "idx_world_lectern_chunk",
                columnList = "world_id, chunk_x, chunk_z"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorldLectern {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "world_id", nullable = false)
    private WorldAccess world;

    private int x;
    private int y;
    private int z;
    @Column(name = "chunk_x", nullable = false)
    private int chunkX;
    @Column(name = "chunk_z", nullable = false)
    private int chunkZ;

    @Column(name = "book_item_type", nullable = false)
    private short bookItemType;
    @Column(name = "book_count", nullable = false)
    private int bookCount;
    @Column(name = "book_durability", nullable = false)
    private int bookDurability;
    @Column(name = "book_enchantments", nullable = false)
    private long bookEnchantments;
    @Column(name = "book_map_id", nullable = false)
    private int bookMapId;
    @Column(name = "book_shulker_id", nullable = false)
    private int bookShulkerId;
    @Column(name = "book_bucket_mob_data", length = 512)
    private String bookBucketMobData;
    @Column(name = "book_item_component_data", nullable = false, columnDefinition = "LONGTEXT")
    private String bookItemComponentData;
    @Column(nullable = false)
    private int page;

    public WorldLectern(WorldAccess world, LecternBlockData state) {
        if (world == null || state == null) throw new IllegalArgumentException("lectern state required");
        this.world = world;
        x = state.x();
        y = state.y();
        z = state.z();
        replace(state.book(), state.page());
    }

    public void replace(PlayerInventory.StackSnapshot book, int page) {
        LecternBlockData validated = new LecternBlockData(x, y, z, book, page);
        bookItemType = validated.book().itemType();
        bookCount = validated.book().count();
        bookDurability = validated.book().durability();
        bookEnchantments = validated.book().enchantments();
        bookMapId = validated.book().mapId();
        bookShulkerId = validated.book().shulkerId();
        bookBucketMobData = validated.book().bucketMobData();
        bookItemComponentData = validated.book().itemComponentData();
        this.page = validated.page();
    }

    public void turnPage(int page) {
        replace(book(), page);
    }

    public PlayerInventory.StackSnapshot book() {
        return new PlayerInventory.StackSnapshot(bookItemType, bookCount, bookDurability,
                bookEnchantments, bookMapId, bookShulkerId,
                bookBucketMobData, bookItemComponentData);
    }

    public LecternBlockData snapshot() {
        return new LecternBlockData(x, y, z, book(), page);
    }

    @PrePersist
    @PreUpdate
    private void computeChunkCoordinates() {
        chunkX = Math.floorDiv(x, 16);
        chunkZ = Math.floorDiv(z, 16);
    }
}
