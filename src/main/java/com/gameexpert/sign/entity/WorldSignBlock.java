package com.gameexpert.sign.entity;

import com.gameexpert.sign.dto.SignBlockData;
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
@org.hibernate.annotations.DynamicUpdate
@Table(name = "world_sign_blocks",
        uniqueConstraints = @UniqueConstraint(name = "uq_world_sign_xyz",
                columnNames = {"world_id", "x", "y", "z"}),
        indexes = @Index(name = "idx_world_sign_chunk",
                columnList = "world_id, chunk_x, chunk_z"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorldSignBlock {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "world_id", nullable = false)
    private WorldAccess world;

    private int x;
    private int y;
    private int z;
    @Column(name = "chunk_x", nullable = false) private int chunkX;
    @Column(name = "chunk_z", nullable = false) private int chunkZ;
    @Column(name = "line_0", nullable = false, length = 360) private String line0;
    @Column(name = "line_1", nullable = false, length = 360) private String line1;
    @Column(name = "line_2", nullable = false, length = 360) private String line2;
    @Column(name = "line_3", nullable = false, length = 360) private String line3;
    @Column(length = 255) private String generatedInstallationId;
    @Column(length = 64) private String generatedInstallationFingerprint;

    public WorldSignBlock(WorldAccess world, SignBlockData state) {
        if (world == null) throw new IllegalArgumentException("world required");
        this.world = world;
        x = state.x(); y = state.y(); z = state.z();
        replace(state.lines());
    }

    public void replace(java.util.List<String> lines) {
        SignBlockData checked = new SignBlockData(x, y, z, lines);
        line0 = checked.lines().get(0); line1 = checked.lines().get(1);
        line2 = checked.lines().get(2); line3 = checked.lines().get(3);
    }

    public SignBlockData snapshot() {
        return new SignBlockData(x, y, z, java.util.List.of(line0, line1, line2, line3));
    }

    public boolean claimGeneratedInstallation(String installationId, String fingerprint) {
        if (installationId == null || installationId.isBlank() || installationId.length() > 255) {
            throw new IllegalArgumentException("invalid generated installation id");
        }
        if (fingerprint == null || !fingerprint.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("invalid generated installation fingerprint");
        }
        if (generatedInstallationId == null && generatedInstallationFingerprint == null) {
            generatedInstallationId = installationId;
            generatedInstallationFingerprint = fingerprint;
            return true;
        }
        if (installationId.equals(generatedInstallationId)
                && fingerprint.equals(generatedInstallationFingerprint)) return false;
        throw new IllegalStateException("conflicting generated sign installation");
    }

    public void requireSameGeneratedInstallation(String installationId, String fingerprint) {
        if (installationId == null || installationId.isBlank() || installationId.length() > 255
                || fingerprint == null || !fingerprint.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("invalid generated sign installation identity");
        }
        if (!installationId.equals(generatedInstallationId)
                || !fingerprint.equals(generatedInstallationFingerprint)) {
            throw new IllegalStateException("conflicting generated sign installation");
        }
    }

    @PrePersist @PreUpdate
    private void coordinates() { chunkX = Math.floorDiv(x, 16); chunkZ = Math.floorDiv(z, 16); }
}
