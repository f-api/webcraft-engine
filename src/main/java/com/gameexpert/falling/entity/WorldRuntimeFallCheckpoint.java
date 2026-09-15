package com.gameexpert.falling.entity;

import com.gameexpert.api.persistence.WorldAccess;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

/** A committed admission checkpoint must never replay its old source diffs. */
@Getter
@Entity
@Table(name = "world_runtime_fall_checkpoints")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorldRuntimeFallCheckpoint {
    @Id @Column(length = 64) private String checkpointKey;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @OnDelete(action = OnDeleteAction.CASCADE) private WorldAccess world;
    public WorldRuntimeFallCheckpoint(String key, WorldAccess world) { checkpointKey = key; this.world = world; }
}
