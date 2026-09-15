package com.gameexpert.engine.persistence.finalcarrier.structureentity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Durable, never-decreasing mob identity cursor owned independently by each world. */
@Getter
@Entity
@Table(name = "world_structure_entity_id_sequences")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorldStructureEntityIdSequence {
    @Id
    private Long worldId;
    private long highWater;
    @Version
    private long version;

    public WorldStructureEntityIdSequence(long worldId, long highWater) {
        if (worldId <= 0L || highWater < 0L) {
            throw new IllegalArgumentException("invalid ENTS world identity cursor");
        }
        this.worldId = worldId;
        this.highWater = highWater;
    }

    public long reserve(int count) {
        if (count <= 0) throw new IllegalArgumentException("positive ENTS ID count required");
        if (highWater >= Long.MAX_VALUE - count) {
            throw new IllegalStateException("ENTS identity space is exhausted");
        }
        long first = Math.addExact(highWater, 1L);
        highWater = Math.addExact(highWater, count);
        return first;
    }

    /** Monotonically excludes every identity at or below the authenticated external high-water. */
    public void advanceHighWater(long minimumHighWater) {
        if (minimumHighWater < 0L) {
            throw new IllegalArgumentException("non-negative ENTS identity floor required");
        }
        highWater = Math.max(highWater, minimumHighWater);
    }
}
