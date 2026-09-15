package com.gameexpert.falling.entity;

import com.gameexpert.falling.dto.FallingSpeleothemState;
import com.gameexpert.api.persistence.WorldAccess;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

/** Finished rows are receipts: replay must never resurrect an already landed entity. */
@Getter
@Entity
@Table(name = "world_falling_speleothems", uniqueConstraints = @UniqueConstraint(
        name = "uk_world_falling_speleothem", columnNames = {"world_id", "entity_key"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorldFallingSpeleothem {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private WorldAccess world;
    @Column(nullable = false, length = 160)
    private String entityKey;
    private int blockId;
    private int blockState;
    private double x;
    private double y;
    private double z;
    private double velocityY;
    private double fallDistance;
    private int age;
    private int damagePerDistance;
    private boolean finished;

    public WorldFallingSpeleothem(WorldAccess world, FallingSpeleothemState state) {
        this.world = world;
        entityKey = state.id();
        blockId = state.blockId();
        blockState = state.blockState();
        damagePerDistance = state.damagePerDistance();
        assignProgress(state);
    }

    public void apply(FallingSpeleothemState state) {
        if (!entityKey.equals(state.id()) || blockId != state.blockId()
                || blockState != state.blockState() || damagePerDistance != state.damagePerDistance()) {
            throw new IllegalArgumentException("falling entity identity or progress changed");
        }
        FallingSpeleothemState current = snapshot();
        if (current.equals(state)) return;
        if (finished || state.age() <= age || Double.compare(x, state.x()) != 0
                || Double.compare(z, state.z()) != 0 || state.y() > y
                || state.fallDistance() < fallDistance) {
            throw new IllegalArgumentException("falling entity identity or progress changed");
        }
        assignProgress(state);
    }

    private void assignProgress(FallingSpeleothemState state) {
        x = state.x();
        y = state.y();
        z = state.z();
        velocityY = state.velocityY();
        fallDistance = state.fallDistance();
        age = state.age();
        finished = state.finished();
    }

    public FallingSpeleothemState snapshot() {
        return new FallingSpeleothemState(entityKey, blockId, blockState, x, y, z,
                velocityY, fallDistance, age, damagePerDistance, finished);
    }
}
