package com.gameexpert.jukebox.entity;

import com.gameexpert.engine.inventory.PlayerInventory;
import com.gameexpert.jukebox.dto.JukeboxBlockData;
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

/**
 * [JUKEBOX] One {@code JukeboxBlockEntity}: the held disc stack with every component and the
 * persisted song clock ({@code ticks_since_song_started}, null when silent).
 */
@Getter
@Entity
@org.hibernate.annotations.DynamicUpdate
@Table(name = "world_jukeboxes",
        uniqueConstraints = @UniqueConstraint(name = "uq_world_jukebox_xyz",
                columnNames = {"world_id", "x", "y", "z"}),
        indexes = @Index(name = "idx_world_jukebox_chunk",
                columnList = "world_id, chunk_x, chunk_z"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorldJukebox {
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

    @Column(name = "disc_item_type", nullable = false)
    private short discItemType;
    @Column(name = "disc_count", nullable = false)
    private int discCount;
    @Column(name = "disc_durability", nullable = false)
    private int discDurability;
    @Column(name = "disc_enchantments", nullable = false)
    private long discEnchantments;
    @Column(name = "disc_map_id", nullable = false)
    private int discMapId;
    @Column(name = "disc_shulker_id", nullable = false)
    private int discShulkerId;
    @Column(name = "disc_bucket_mob_data", length = 512)
    private String discBucketMobData;
    @Column(name = "disc_item_component_data", columnDefinition = "LONGTEXT")
    private String discItemComponentData;
    @Column(name = "ticks_since_song_started")
    private Long ticksSinceSongStarted;

    public WorldJukebox(WorldAccess world, JukeboxBlockData state) {
        if (world == null || state == null) throw new IllegalArgumentException("jukebox state required");
        this.world = world;
        x = state.x();
        y = state.y();
        z = state.z();
        replace(state);
    }

    public void replace(JukeboxBlockData state) {
        if (state.x() != x || state.y() != y || state.z() != z) {
            throw new IllegalArgumentException("jukebox row position is immutable");
        }
        PlayerInventory.StackSnapshot disc = state.disc();
        discItemType = disc.itemType();
        discCount = disc.count();
        discDurability = disc.durability();
        discEnchantments = disc.enchantments();
        discMapId = disc.mapId();
        discShulkerId = disc.shulkerId();
        discBucketMobData = disc.bucketMobData();
        discItemComponentData = disc.itemComponentData();
        ticksSinceSongStarted = state.ticksSinceSongStarted();
    }

    public JukeboxBlockData snapshot() {
        return new JukeboxBlockData(x, y, z, new PlayerInventory.StackSnapshot(discItemType, discCount,
                discDurability, discEnchantments, discMapId, discShulkerId, discBucketMobData,
                discItemComponentData), ticksSinceSongStarted);
    }

    @PrePersist
    @PreUpdate
    private void computeChunkCoordinates() {
        chunkX = Math.floorDiv(x, 16);
        chunkZ = Math.floorDiv(z, 16);
    }
}
