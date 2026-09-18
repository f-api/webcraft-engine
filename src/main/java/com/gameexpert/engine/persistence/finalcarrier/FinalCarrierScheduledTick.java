package com.gameexpert.engine.persistence.finalcarrier;

import com.gameexpert.engine.persistence.tick.FinalCarrierTickScheduler;
import com.gameexpert.terrain.Blocks;
import com.gameexpert.authority.versioned.NeutralFinalChunk;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.Objects;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** First-winner durable BLOCK/FLUID scheduled tick. */
@Getter
@Entity
@org.hibernate.annotations.DynamicUpdate
@Table(name = "final_carrier_scheduled_ticks", uniqueConstraints = {
        @UniqueConstraint(name = "uk_final_carrier_scheduled_tick",
                columnNames = {"world_id", "lane", "x", "y", "z", "type_key"}),
        @UniqueConstraint(name = "uk_final_carrier_scheduled_world_lane_order",
                columnNames = {"world_id", "lane", "durable_order"})
        },
        indexes = {
                @Index(name = "idx_final_carrier_scheduled_world_lane_order",
                        columnList = "world_id, lane, durable_order, id"),
                @Index(name = "idx_final_carrier_scheduled_world_chunk_lane_order",
                        columnList = "world_id, chunk_x, chunk_z, lane, durable_order, id"),
                @Index(name = "idx_final_carrier_scheduled_settlement_key",
                        columnList = "world_id, chunk_x, chunk_z, lane, x, y, z, type_key")
        })
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FinalCarrierScheduledTick {
    /** 행마다 두 번 도는 검사다. 호출마다 패턴을 새로 컴파일하지 않도록 미리 만들어 둔다. */
    private static final Pattern SHA256_LOWERCASE = Pattern.compile("[0-9a-f]{64}");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false) private long worldId;
    @Column(nullable = false) private int chunkX;
    @Column(nullable = false) private int chunkZ;
    @Column(nullable = false, length = 8) private String lane;
    @Column(nullable = false) private int x;
    @Column(nullable = false) private int y;
    @Column(nullable = false) private int z;
    @Column(nullable = false, length = 160) private String typeKey;
    @Column(nullable = false) private int expectedBlockId;
    @Column(nullable = false) private long dueTick;
    @Column(nullable = false) private int priority;
    @Column(nullable = false) private long subTickOrder;
    @Column(nullable = false) private long durableOrder;
    @Column(nullable = false, length = 64) private String sourceFingerprint;
    @Column(nullable = false, length = 64) private String payloadFingerprint;

    /**
     * Compatibility constructor for already durable callers. Prepared candidates use -1 and
    * must be assigned a positive order through the two-argument constructor before persistence.
     */
    public FinalCarrierScheduledTick(FinalCarrierTickScheduler.ScheduledTick tick) {
        this(Objects.requireNonNull(tick, "scheduled tick"), tick.durableOrder());
    }

    /** Creates one persisted row with the caller-assigned, positive durable order. */
    public FinalCarrierScheduledTick(FinalCarrierTickScheduler.ScheduledTick tick,
            long durableOrder) {
        Objects.requireNonNull(tick, "scheduled tick");
        if (durableOrder <= 0L) {
            throw new IllegalArgumentException("scheduled tick requires positive durable order");
        }
        if (tick.durableOrder() != -1L && tick.durableOrder() != durableOrder) {
            throw new IllegalArgumentException("scheduled tick durable order mismatch");
        }
        FinalCarrierTickScheduler.CarrierReceipt receipt = tick.receipt();
        if (Math.floorDiv(tick.x(), Blocks.CHUNK_X) != receipt.chunkX()
                || Math.floorDiv(tick.z(), Blocks.CHUNK_Z)
                        != receipt.chunkZ()) {
            throw new IllegalArgumentException("scheduled tick receipt coordinate mismatch");
        }
        this.worldId = receipt.worldId();
        this.chunkX = receipt.chunkX();
        this.chunkZ = receipt.chunkZ();
        this.lane = tick.lane().name();
        this.x = tick.x(); this.y = tick.y(); this.z = tick.z();
        this.typeKey = tick.typeKey();
        this.expectedBlockId = tick.expectedBlockId();
        this.dueTick = tick.dueTick();
        this.priority = tick.priority().value();
        this.subTickOrder = tick.subTickOrder();
        this.durableOrder = durableOrder;
        this.sourceFingerprint = receipt.sourceFingerprint();
        this.payloadFingerprint = receipt.lanePayloadFingerprint();
    }

    public FinalCarrierTickScheduler.ScheduledTick toScheduledTick() {
        validateDurableRow();
        FinalCarrierTickScheduler.Lane parsedLane = FinalCarrierTickScheduler.Lane.valueOf(lane);
        var receipt = new FinalCarrierTickScheduler.CarrierReceipt(worldId, chunkX, chunkZ,
                sourceFingerprint, payloadFingerprint, parsedLane);
        var key = new FinalCarrierTickScheduler.TickKey(parsedLane, x, y, z, typeKey);
        return new FinalCarrierTickScheduler.ScheduledTick(key, receipt, expectedBlockId, dueTick,
                NeutralFinalChunk.TickPriority.fromValue(priority), subTickOrder,
                durableOrder);
    }

    @PrePersist
    @PreUpdate
    private void validateDurableRow() {
        if (worldId <= 0L) {
            throw new IllegalStateException("scheduled tick row has invalid world ID");
        }
        if (durableOrder <= 0L) {
            throw new IllegalStateException("scheduled tick row has invalid durable order");
        }
        if (Math.floorDiv(x, Blocks.CHUNK_X) != chunkX
                || Math.floorDiv(z, Blocks.CHUNK_Z) != chunkZ) {
            throw new IllegalStateException("scheduled tick row receipt coordinate mismatch");
        }
        if (y < Blocks.MIN_Y || y > Blocks.MAX_Y) {
            throw new IllegalStateException("scheduled tick row Y outside world: " + y);
        }
        if (typeKey == null || typeKey.length() > 160) {
            throw new IllegalStateException("scheduled tick row type key exceeds VARCHAR(160)");
        }
        if (dueTick < 0L) {
            throw new IllegalStateException("scheduled tick row has negative due tick");
        }
        if (subTickOrder < 0L) {
            throw new IllegalStateException("scheduled tick row has negative sub-tick order");
        }
        if (priority < -3 || priority > 3) {
            throw new IllegalStateException("scheduled tick row priority outside -3..3: "
                    + priority);
        }
        FinalCarrierTickScheduler.Lane parsedLane;
        try {
            parsedLane = FinalCarrierTickScheduler.Lane.valueOf(lane);
        } catch (RuntimeException exception) {
            throw new IllegalStateException("scheduled tick row has invalid lane", exception);
        }
        if (sourceFingerprint == null || !SHA256_LOWERCASE.matcher(sourceFingerprint).matches()) {
            throw new IllegalStateException("scheduled tick row has invalid source fingerprint");
        }
        if (payloadFingerprint == null
                || !SHA256_LOWERCASE.matcher(payloadFingerprint).matches()) {
            throw new IllegalStateException("scheduled tick row has invalid payload fingerprint");
        }
        if (parsedLane == FinalCarrierTickScheduler.Lane.BLOCK
                && (expectedBlockId < 0 || expectedBlockId > 0xffff)) {
            throw new IllegalStateException("scheduled tick row block ID outside u16: "
                    + expectedBlockId);
        }
        if (parsedLane == FinalCarrierTickScheduler.Lane.FLUID && expectedBlockId != -1) {
            throw new IllegalStateException("scheduled tick row fluid ID must be -1");
        }
        var receipt = new FinalCarrierTickScheduler.CarrierReceipt(worldId, chunkX, chunkZ,
                sourceFingerprint, payloadFingerprint, parsedLane);
        var key = new FinalCarrierTickScheduler.TickKey(parsedLane, x, y, z, typeKey);
        new FinalCarrierTickScheduler.ScheduledTick(key, receipt, expectedBlockId, dueTick,
                NeutralFinalChunk.TickPriority.fromValue(priority), subTickOrder,
                durableOrder);
    }
}
