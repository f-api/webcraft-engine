package com.gameexpert.engine.persistence.finalcarrier.structureentity;

import com.gameexpert.engine.persistence.finalcarrier.structureentity.StructureEntityAggregate.InstallReceipt;
import com.gameexpert.engine.persistence.finalcarrier.structureentity.StructureEntityAggregate.PlannedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.security.MessageDigest;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Complete durable receipt and exact source facts for one encounter-ordered ENTS row. */
@Getter
@Entity
@org.hibernate.annotations.DynamicUpdate
@Table(name = "world_structure_entities", uniqueConstraints = {
        @UniqueConstraint(name = "uk_structure_entity_install_ordinal",
                columnNames = {"lane_installation_identity", "encounter_ordinal"}),
        @UniqueConstraint(name = "uk_structure_entity_authority_id",
                columnNames = {"world_id", "authoritative_entity_id"})
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorldStructureEntity {
    public enum BindingStatus { LIVE, DEAD }
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false) private long worldId;
    @Column(nullable = false) private int chunkX;
    @Column(nullable = false) private int chunkZ;
    @Column(nullable = false, length = 255) private String laneInstallationIdentity;
    @Column(nullable = false, length = 64) private String installationSourceFingerprint;
    @Column(nullable = false) private int encounterOrdinal;
    @Column(nullable = false) private long authoritativeEntityId;
    @Column(nullable = false, length = 8) private String bindingStatus = BindingStatus.LIVE.name();
    @Column(nullable = false, length = 64) private String rowFingerprint;
    @Column(nullable = false, length = 128) private String entityKey;
    @Column(nullable = false, length = 128) private String spawnReason;
    @Column(nullable = false) private long xBits;
    @Column(nullable = false) private long yBits;
    @Column(nullable = false) private long zBits;
    @Column(nullable = false) private int yawBits;
    @Column(nullable = false) private int pitchBits;
    @Column(nullable = false) private long velocityXBits;
    @Column(nullable = false) private long velocityYBits;
    @Column(nullable = false) private long velocityZBits;
    @Column(nullable = false, length = 255) private String lootTable;
    @Column(nullable = false) private long lootSeed;
    @Lob @Column(nullable = false, columnDefinition = "longblob") private byte[] canonicalPayload;

    public WorldStructureEntity(long worldId, int chunkX, int chunkZ,
            String laneInstallationIdentity, String installationSourceFingerprint,
            PlannedEntity entity) {
        this.worldId = worldId;
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.laneInstallationIdentity = laneInstallationIdentity;
        this.installationSourceFingerprint = installationSourceFingerprint;
        this.encounterOrdinal = entity.encounterOrdinal();
        this.authoritativeEntityId = entity.authoritativeEntityId();
        this.rowFingerprint = entity.rowFingerprint();
        this.entityKey = entity.entityKey();
        this.spawnReason = entity.spawnReason();
        this.xBits = Double.doubleToRawLongBits(entity.x());
        this.yBits = Double.doubleToRawLongBits(entity.y());
        this.zBits = Double.doubleToRawLongBits(entity.z());
        this.yawBits = Float.floatToRawIntBits(entity.yaw());
        this.pitchBits = Float.floatToRawIntBits(entity.pitch());
        this.velocityXBits = Double.doubleToRawLongBits(entity.velocityX());
        this.velocityYBits = Double.doubleToRawLongBits(entity.velocityY());
        this.velocityZBits = Double.doubleToRawLongBits(entity.velocityZ());
        this.lootTable = entity.lootTable();
        this.lootSeed = entity.lootSeed();
        this.canonicalPayload = entity.canonicalPayload();
    }

    public byte[] getCanonicalPayload() {
        return canonicalPayload.clone();
    }

    public InstallReceipt toReceipt() {
        return new InstallReceipt(encounterOrdinal, authoritativeEntityId, rowFingerprint);
    }

    public BindingStatus bindingStatus() { return BindingStatus.valueOf(bindingStatus); }

    public boolean markDead() {
        if (bindingStatus().equals(BindingStatus.DEAD)) return false;
        bindingStatus = BindingStatus.DEAD.name();
        return true;
    }

    public boolean matches(long expectedWorldId, int expectedChunkX, int expectedChunkZ,
            String expectedLaneIdentity, String expectedSourceFingerprint, PlannedEntity entity) {
        return worldId == expectedWorldId && chunkX == expectedChunkX && chunkZ == expectedChunkZ
                && laneInstallationIdentity.equals(expectedLaneIdentity)
                && installationSourceFingerprint.equals(expectedSourceFingerprint)
                && encounterOrdinal == entity.encounterOrdinal()
                && authoritativeEntityId == entity.authoritativeEntityId()
                && rowFingerprint.equals(entity.rowFingerprint())
                && entityKey.equals(entity.entityKey()) && spawnReason.equals(entity.spawnReason())
                && xBits == Double.doubleToRawLongBits(entity.x())
                && yBits == Double.doubleToRawLongBits(entity.y())
                && zBits == Double.doubleToRawLongBits(entity.z())
                && yawBits == Float.floatToRawIntBits(entity.yaw())
                && pitchBits == Float.floatToRawIntBits(entity.pitch())
                && velocityXBits == Double.doubleToRawLongBits(entity.velocityX())
                && velocityYBits == Double.doubleToRawLongBits(entity.velocityY())
                && velocityZBits == Double.doubleToRawLongBits(entity.velocityZ())
                && lootTable.equals(entity.lootTable()) && lootSeed == entity.lootSeed()
                && MessageDigest.isEqual(canonicalPayload, entity.canonicalPayload());
    }
}
