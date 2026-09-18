package com.gameexpert.engine.persistence.finalcarrier.archaeology;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Durable projection of one installed final-carrier archaeology aggregate. */
@Getter
@Entity
@org.hibernate.annotations.DynamicUpdate
@Table(name = "world_archaeology_brushables", uniqueConstraints = {
        @UniqueConstraint(name = "uk_archaeology_installation",
                columnNames = "installation_identity"),
        @UniqueConstraint(name = "uk_archaeology_world_position",
                columnNames = {"world_id", "target_x", "target_y", "target_z"})
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorldArchaeologyBrushable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false) private long worldId;
    @Column(nullable = false) private int chunkX;
    @Column(nullable = false) private int chunkZ;
    @Column(nullable = false) private int packed;
    @Column(nullable = false, length = 255) private String laneInstallationIdentity;
    @Column(nullable = false, length = 255) private String installationIdentity;
    @Column(nullable = false, length = 255) private String exactTableKey;
    @Column(nullable = false) private long rawSeed;
    @Column(nullable = false) private int targetX;
    @Column(nullable = false) private int targetY;
    @Column(nullable = false) private int targetZ;
    @Column(nullable = false, length = 255) private String exactBlockState;
    @Column(nullable = false, length = 255) private String currentExactBlockState;
    @Column(nullable = false) private long targetRevision;
    @Column(nullable = false) private boolean revoked;
    @Lob @Column(nullable = false, columnDefinition = "longblob")
    private byte[] canonicalReceiptBytes;
    @Lob private String resultIdentity;
    private Long resultEntityId;

    public WorldArchaeologyBrushable(long worldId, int chunkX, int chunkZ, int packed,
            String laneInstallationIdentity, ArchaeologyBrushableAggregate aggregate) {
        this.worldId = worldId;
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.packed = packed;
        this.laneInstallationIdentity = java.util.Objects.requireNonNull(
                laneInstallationIdentity, "lane installation identity");
        this.installationIdentity = aggregate.installationIdentity();
        this.exactTableKey = aggregate.exactTableKey();
        this.rawSeed = aggregate.rawSeed();
        this.targetX = aggregate.target().x();
        this.targetY = aggregate.target().y();
        this.targetZ = aggregate.target().z();
        this.exactBlockState = aggregate.target().exactBlockState();
        this.currentExactBlockState = aggregate.currentExactBlockState();
        this.targetRevision = aggregate.targetRevision();
        this.canonicalReceiptBytes = aggregate.canonicalReceiptBytes();
    }

    public ArchaeologyBrushableAggregate toAggregate() {
        if ((resultIdentity == null) != (resultEntityId == null)) {
            throw new IllegalStateException("incomplete persisted ARCH result delivery");
        }
        return ArchaeologyBrushableAggregate.restore(
                new ArchaeologyBrushableAggregate.Installation(
                        installationIdentity, exactTableKey, rawSeed,
                        new ArchaeologyBrushableAggregate.TargetBlock(
                                targetX, targetY, targetZ, exactBlockState),
                        canonicalReceiptBytes), currentExactBlockState, targetRevision,
                revoked, resultIdentity);
    }

    public ArchaeologyBrushableAggregate.ConsumeOutcome consume(
            String candidateResultIdentity, String candidateExactBlockState,
            long candidateTargetRevision, long proposedEntityId) {
        if (proposedEntityId <= 0 || proposedEntityId == Long.MAX_VALUE) {
            throw new IllegalArgumentException("stable archaeology ground entity ID is required");
        }
        ArchaeologyBrushableAggregate aggregate = toAggregate();
        ArchaeologyBrushableAggregate.ConsumeOutcome outcome =
                aggregate.consume(candidateResultIdentity, candidateExactBlockState,
                        candidateTargetRevision);
        resultIdentity = aggregate.resultIdentity().orElseThrow();
        if (resultEntityId == null) resultEntityId = proposedEntityId;
        return outcome;
    }

    public void advanceTarget(String expectedExactBlockState, long expectedRevision,
            String nextExactBlockState) {
        ArchaeologyBrushableAggregate aggregate = toAggregate();
        aggregate.advanceTarget(expectedExactBlockState, expectedRevision, nextExactBlockState);
        currentExactBlockState = aggregate.currentExactBlockState();
        targetRevision = aggregate.targetRevision();
    }

    public void revoke(String expectedExactBlockState, long expectedRevision) {
        ArchaeologyBrushableAggregate aggregate = toAggregate();
        aggregate.revoke(expectedExactBlockState, expectedRevision);
        revoked = aggregate.revoked();
        targetRevision = aggregate.targetRevision();
    }

    public byte[] getCanonicalReceiptBytes() {
        return canonicalReceiptBytes.clone();
    }
}
