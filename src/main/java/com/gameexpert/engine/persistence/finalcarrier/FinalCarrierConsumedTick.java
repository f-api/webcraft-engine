package com.gameexpert.engine.persistence.finalcarrier;

import com.gameexpert.engine.persistence.tick.FinalCarrierTickScheduler;
import com.gameexpert.engine.persistence.tick.FinalCarrierTickPublication;
import com.gameexpert.engine.persistence.tick.FinalCarrierTickPublicationCodec;
import com.gameexpert.terrain.Blocks;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.util.Objects;

/** Append-only settlement receipt for a scheduled tick. */
@Getter
@Entity
@org.hibernate.annotations.DynamicUpdate
@Table(name = "final_carrier_consumed_ticks", uniqueConstraints = {
        @UniqueConstraint(name = "uk_final_carrier_consumed_tick",
                columnNames = {"world_id", "lane", "x", "y", "z", "type_key"}),
        @UniqueConstraint(name = "uk_final_carrier_consumed_tick_publication",
                columnNames = {"publication_key"}),
        @UniqueConstraint(name = "uk_final_carrier_consumed_world_lane_order",
                columnNames = {"world_id", "lane", "durable_order"})
        }, indexes = {
                @Index(name = "idx_final_carrier_consumed_world_lane_state_order",
                        columnList = "world_id, lane, publication_state, durable_order, id"),
                @Index(name = "idx_final_carrier_consumed_world_chunk_lane_state_order",
                        columnList = "world_id, chunk_x, chunk_z, lane, publication_state, durable_order, id")
        })
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FinalCarrierConsumedTick {
    /** State of the durable publication outbox row, independent of tick disposition. */
    public enum PublicationState {
        UNACKNOWLEDGED,
        OUTCOME_UNKNOWN,
        ACKNOWLEDGED,
        REJECTED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false) private long worldId;
    @Column(nullable = false) private Integer chunkX;
    @Column(nullable = false) private Integer chunkZ;
    @Column(nullable = false, length = 8) private String lane;
    @Column(nullable = false) private int x;
    @Column(nullable = false) private int y;
    @Column(nullable = false) private int z;
    @Column(nullable = false, length = 160) private String typeKey;
    @Column(nullable = false) private Integer expectedBlockId;
    @Column(nullable = false) private Long dueTick;
    @Column(nullable = false) private Integer priority;
    @Column(nullable = false) private Long subTickOrder;
    @Column(nullable = false) private Long durableOrder;
    @Column(nullable = false, length = 64) private String sourceFingerprint;
    @Column(nullable = false, length = 64) private String payloadFingerprint;
    @Column(nullable = false, length = 24) private String disposition;
    @Column(nullable = false, length = 64) private String publicationKey;
    @Column(nullable = false, length = 64) private String publicationDigest;
    @Lob @Column(nullable = false, columnDefinition = "MEDIUMBLOB") private byte[] publicationBody;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16) private PublicationState publicationState;

    @Transient
    private PublicationState loadedPublicationState;

    public FinalCarrierConsumedTick(FinalCarrierTickScheduler.ScheduledTick tick,
            FinalCarrierTickScheduler.DueDisposition disposition) {
        if (tick.durableOrder() <= 0L) {
            throw new IllegalArgumentException("consumed tick requires durable admission order");
        }
        this.worldId = tick.receipt().worldId();
        this.chunkX = tick.receipt().chunkX();
        this.chunkZ = tick.receipt().chunkZ();
        this.lane = tick.lane().name();
        this.x = tick.x(); this.y = tick.y(); this.z = tick.z();
        this.typeKey = tick.typeKey();
        this.expectedBlockId = tick.expectedBlockId();
        this.dueTick = tick.dueTick();
        this.priority = tick.priority().value();
        this.subTickOrder = tick.subTickOrder();
        this.durableOrder = tick.durableOrder();
        this.sourceFingerprint = tick.receipt().sourceFingerprint();
        this.payloadFingerprint = tick.receipt().lanePayloadFingerprint();
        this.disposition = Objects.requireNonNull(disposition, "disposition").name();
    }

    /**
     * Creates a complete current-schema row. The limits are part of the authenticated caller
     * contract; this entity never invents a body or collection bound.
     */
    public FinalCarrierConsumedTick(FinalCarrierTickPublication publication,
            FinalCarrierTickPublicationCodec.Limits limits) {
        this(Objects.requireNonNull(publication, "publication").scheduledTick(),
                publication.disposition());
        Objects.requireNonNull(limits, "publication limits");
        byte[] body = FinalCarrierTickPublicationCodec.encode(publication, limits);
        this.publicationKey = FinalCarrierTickPublicationCodec.publicationKey(
                publication.scheduledTick(), publication.disposition(), limits);
        this.publicationDigest = FinalCarrierTickPublicationCodec.publicationDigest(body);
        this.publicationBody = body.clone();
        this.publicationState = publication.publicationState();
    }

    /** Convenience complete-row constructor for a planned tick mutation. */
    public FinalCarrierConsumedTick(FinalCarrierTickScheduler.ScheduledTick tick,
            FinalCarrierTickScheduler.DueDisposition disposition,
            FinalCarrierTickScheduler.TickMutation mutation,
            FinalCarrierTickPublicationCodec.Limits limits) {
        this(new FinalCarrierTickPublication(tick, disposition, mutation), limits);
    }

    public FinalCarrierTickScheduler.ScheduledTick toScheduledTick() {
        requireCurrentIdentity();
        parsedDisposition();
        FinalCarrierTickScheduler.Lane parsedLane;
        try {
            parsedLane = FinalCarrierTickScheduler.Lane.valueOf(lane);
        } catch (RuntimeException invalidLane) {
            throw new IllegalStateException("consumed tick row has invalid current lane",
                    invalidLane);
        }
        var receipt = new FinalCarrierTickScheduler.CarrierReceipt(worldId, chunkX, chunkZ,
                sourceFingerprint, payloadFingerprint, parsedLane);
        var key = new FinalCarrierTickScheduler.TickKey(parsedLane, x, y, z, typeKey);
        return new FinalCarrierTickScheduler.ScheduledTick(key, receipt, expectedBlockId, dueTick,
                com.gameexpert.authority.versioned.NeutralFinalChunk.TickPriority
                        .fromValue(priority),
                subTickOrder, durableOrder);
    }

    public FinalCarrierTickScheduler.DueDisposition parsedDisposition() {
        if (disposition == null) {
            throw new IllegalStateException("consumed tick row has null disposition");
        }
        try {
            return FinalCarrierTickScheduler.DueDisposition.valueOf(disposition);
        } catch (RuntimeException invalidDisposition) {
            throw new IllegalStateException("consumed tick row has invalid disposition",
                    invalidDisposition);
        }
    }

    public boolean matchesExact(FinalCarrierTickScheduler.ScheduledTick tick,
            FinalCarrierTickScheduler.DueDisposition expectedDisposition) {
        Objects.requireNonNull(tick, "scheduled tick");
        Objects.requireNonNull(expectedDisposition, "expected disposition");
        return parsedDisposition() == expectedDisposition && toScheduledTick().equals(tick);
    }

    public boolean matchesCandidate(FinalCarrierTickScheduler.ScheduledTick candidate) {
        Objects.requireNonNull(candidate, "candidate");
        parsedDisposition();
        FinalCarrierTickScheduler.ScheduledTick settled = toScheduledTick();
        return settled.durableOrder() > 0L
                && settled.key().equals(candidate.key())
                && settled.receipt().equals(candidate.receipt())
                && settled.expectedBlockId() == candidate.expectedBlockId()
                && settled.dueTick() == candidate.dueTick()
                && settled.priority() == candidate.priority()
                && settled.subTickOrder() == candidate.subTickOrder();
    }

    /**
     * Decodes and byte-validates the complete publication. A legacy row with any null publication
     * column is intentionally not interpreted as a current row.
     */
    public FinalCarrierTickPublication toPublication(
            FinalCarrierTickPublicationCodec.Limits limits) {
        requireCompletePublication();
        Objects.requireNonNull(limits, "publication limits");
        FinalCarrierTickScheduler.ScheduledTick exactTick = toScheduledTick();
        FinalCarrierTickPublication publication = FinalCarrierTickPublicationCodec.decode(
                publicationBody, exactTick, parsedDisposition(), limits, publicationState);
        if (!publication.scheduledTick().equals(exactTick)
                || publication.disposition() != parsedDisposition()) {
            throw new IllegalStateException(
                    "consumed tick publication body differs from row identity");
        }
        FinalCarrierTickPublicationCodec.validate(publication, publicationKey,
                publicationDigest, publicationBody, limits);
        return publication;
    }

    public boolean hasCompletePublication() {
        return publicationKey != null && publicationDigest != null
                && publicationBody != null && publicationState != null;
    }

    public byte[] getPublicationBody() {
        return publicationBody == null ? null : publicationBody.clone();
    }

    /** Alias used by mutation-oriented callers; both names address the same immutable bytes. */
    public byte[] getMutationBody() {
        return getPublicationBody();
    }

    public String getMutationDigest() {
        return publicationDigest;
    }

    public PublicationState getState() {
        return publicationState;
    }

    /** Idempotently seals the durable row before the live publication is attempted. */
    public void markOutcomeUnknown() {
        if (publicationState == null) {
            throw new FinalCarrierDurableStateException(
                    "consumed tick row has null publication state");
        }
        if (publicationState == PublicationState.UNACKNOWLEDGED) {
            publicationState = PublicationState.OUTCOME_UNKNOWN;
        } else if (publicationState != PublicationState.OUTCOME_UNKNOWN
                && publicationState != PublicationState.ACKNOWLEDGED) {
            throw new FinalCarrierDurableStateException(
                    "consumed tick publication cannot enter outcome-unknown state");
        }
    }

    /** Exact ACK transition; repeating an ACK is harmless, changing a rejection is not. */
    public void acknowledgePublication() {
        requirePublicationState();
        if (publicationState == PublicationState.ACKNOWLEDGED) {
            return;
        }
        if (publicationState != PublicationState.OUTCOME_UNKNOWN) {
            throw new FinalCarrierDurableStateException(
                    "publication must be outcome-unknown before acknowledgement");
        }
        publicationState = PublicationState.ACKNOWLEDGED;
    }

    /** Exact reject transition; repeating a reject is harmless, changing an ACK is not. */
    public void rejectPublication() {
        requirePublicationState();
        if (publicationState == PublicationState.ACKNOWLEDGED) {
            throw new FinalCarrierDurableStateException(
                    "acknowledged consumed tick cannot be rejected");
        }
        if (publicationState != PublicationState.REJECTED) {
            publicationState = PublicationState.REJECTED;
        }
    }

    public static boolean isLegalPublicationTransition(PublicationState current,
            PublicationState next) {
        Objects.requireNonNull(current, "current publication state");
        Objects.requireNonNull(next, "next publication state");
        if (current == next) return true;
        return switch (current) {
            case UNACKNOWLEDGED -> next == PublicationState.OUTCOME_UNKNOWN
                    || next == PublicationState.REJECTED;
            case OUTCOME_UNKNOWN -> next == PublicationState.ACKNOWLEDGED
                    || next == PublicationState.REJECTED;
            case ACKNOWLEDGED, REJECTED -> false;
        };
    }

    @PostLoad
    private void rememberPublicationState() {
        loadedPublicationState = publicationState;
    }

    @PrePersist
    private void validateCurrentRowBeforeInsert() {
        requireCurrentIdentity();
        requireCompletePublication();
        if (publicationState != PublicationState.UNACKNOWLEDGED) {
            throw new FinalCarrierDurableStateException(
                    "new consumed tick publication must start unacknowledged");
        }
        loadedPublicationState = publicationState;
    }

    @PreUpdate
    private void validateCurrentRowBeforeUpdate() {
        requireCurrentIdentity();
        requireCompletePublication();
        if (loadedPublicationState == null) {
            throw new FinalCarrierDurableStateException(
                    "consumed tick publication update has no loaded state baseline");
        }
        if (!isLegalPublicationTransition(loadedPublicationState, publicationState)) {
            throw new FinalCarrierDurableStateException(
                    "illegal consumed tick publication state transition: "
                            + loadedPublicationState + " -> " + publicationState);
        }
        loadedPublicationState = publicationState;
    }

    private void requireCompletePublication() {
        if (!hasCompletePublication()) {
            throw new IllegalStateException(
                    "legacy consumed tick row is missing complete publication fields");
        }
        if (publicationBody.length == 0) {
            throw new IllegalStateException("consumed tick publication body is empty");
        }
        if (publicationBody.length > FinalCarrierTickPublicationCodec.SQL_MEDIUMBLOB_MAX_BYTES) {
            throw new IllegalStateException(
                    "consumed tick publication body exceeds SQL blob capacity");
        }
        if (publicationKey.length() != 64 || !publicationKey.matches("[0-9a-f]{64}")) {
            throw new IllegalStateException("consumed tick publication key is not lowercase SHA-256");
        }
        if (publicationDigest.length() != 64 || !publicationDigest.matches("[0-9a-f]{64}")) {
            throw new IllegalStateException(
                    "consumed tick publication digest is not lowercase SHA-256");
        }
        requirePublicationState();
    }

    private void requirePublicationState() {
        if (publicationState == null) {
            throw new FinalCarrierDurableStateException(
                    "consumed tick row has null publication state");
        }
    }

    private void requireCurrentIdentity() {
        if (lane == null || typeKey == null || chunkX == null || chunkZ == null
                || expectedBlockId == null || dueTick == null
                || priority == null || subTickOrder == null || durableOrder == null
                || sourceFingerprint == null || payloadFingerprint == null) {
            throw new IllegalStateException(
                    "legacy consumed tick row is missing current-hash identity fields");
        }
        if (durableOrder <= 0L) {
            throw new IllegalStateException("consumed tick row has invalid durable admission order");
        }
        if (worldId <= 0L) {
            throw new IllegalStateException("consumed tick row has invalid world ID");
        }
        if (Math.floorDiv(x, Blocks.CHUNK_X) != chunkX
                || Math.floorDiv(z, Blocks.CHUNK_Z) != chunkZ) {
            throw new IllegalStateException("consumed tick row receipt coordinate mismatch");
        }
        if (y < Blocks.MIN_Y || y > Blocks.MAX_Y) {
            throw new IllegalStateException("consumed tick row Y outside world: " + y);
        }
        if (typeKey.length() > 160) {
            throw new IllegalStateException("consumed tick row type key exceeds VARCHAR(160)");
        }
        if (dueTick < 0L) {
            throw new IllegalStateException("consumed tick row has negative due tick");
        }
        if (subTickOrder < 0L) {
            throw new IllegalStateException("consumed tick row has negative sub-tick order");
        }
        if (priority < -3 || priority > 3) {
            throw new IllegalStateException("consumed tick row priority outside -3..3: " + priority);
        }
        FinalCarrierTickScheduler.Lane parsedLane;
        try {
            parsedLane = FinalCarrierTickScheduler.Lane.valueOf(lane);
        } catch (RuntimeException invalidLane) {
            throw new IllegalStateException("consumed tick row has invalid current lane",
                    invalidLane);
        }
        if (!sourceFingerprint.matches("[0-9a-f]{64}")
                || !payloadFingerprint.matches("[0-9a-f]{64}")) {
            throw new IllegalStateException("consumed tick row has invalid current fingerprint");
        }
        if (parsedLane == FinalCarrierTickScheduler.Lane.BLOCK
                && (expectedBlockId < 0 || expectedBlockId > 0xffff)) {
            throw new IllegalStateException("consumed tick row block ID outside u16: "
                    + expectedBlockId);
        }
        if (parsedLane == FinalCarrierTickScheduler.Lane.FLUID && expectedBlockId != -1) {
            throw new IllegalStateException("consumed tick row fluid ID must be -1");
        }
        var receipt = new FinalCarrierTickScheduler.CarrierReceipt(worldId, chunkX, chunkZ,
                sourceFingerprint, payloadFingerprint, parsedLane);
        var key = new FinalCarrierTickScheduler.TickKey(parsedLane, x, y, z, typeKey);
        new FinalCarrierTickScheduler.ScheduledTick(key, receipt, expectedBlockId, dueTick,
                com.gameexpert.authority.versioned.NeutralFinalChunk.TickPriority
                        .fromValue(priority), subTickOrder, durableOrder);
    }
}
