package com.gameexpert.falling.entity;

import com.gameexpert.falling.dto.RuntimeSpeleothemFall;
import com.gameexpert.api.persistence.WorldAccess;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

@Getter
@Entity
@org.hibernate.annotations.DynamicUpdate
@Table(name = "world_runtime_speleothem_falls", uniqueConstraints = @UniqueConstraint(
        name = "uk_runtime_speleothem_identity", columnNames = {"world_id", "event_key"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorldRuntimeSpeleothemFall {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @OnDelete(action = OnDeleteAction.CASCADE) private WorldAccess world;
    @Column(nullable = false, length = 64) private String eventKey;
    private int x;
    private int y;
    private int z;
    private int blockId;
    private long dueTick;
    private boolean consumed;
    @Lob @Column(columnDefinition = "LONGBLOB") private byte[] settlementPlan;

    public WorldRuntimeSpeleothemFall(WorldAccess world, RuntimeSpeleothemFall fall) {
        this.world = world; eventKey = fall.identity();
        x = fall.x(); y = fall.y(); z = fall.z(); blockId = fall.blockId(); dueTick = fall.dueTick();
    }
    public RuntimeSpeleothemFall snapshot() {
        return new RuntimeSpeleothemFall(eventKey, x, y, z, blockId, dueTick);
    }
    public void consume(byte[] exactPlan) { settlementPlan = exactPlan.clone(); consumed = true; }
}
