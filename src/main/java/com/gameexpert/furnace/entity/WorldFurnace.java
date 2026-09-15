package com.gameexpert.furnace.entity;

import java.util.Arrays;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import com.gameexpert.engine.FurnaceInventory;

/** 월드 좌표에 귀속된 화로 세 칸과 남은 연료/조리 진행의 영속 스냅샷입니다. */
@Getter
@Entity
@Table(
        name = "world_furnaces",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_world_furnace_pos",
                columnNames = { "world_id", "pos_x", "pos_y", "pos_z" }))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorldFurnace {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long worldId;

    @Column(nullable = false)
    private int posX;

    @Column(nullable = false)
    private int posY;

    @Column(nullable = false)
    private int posZ;

    @Column(nullable = false, columnDefinition = "bigint not null default 0")
    private long persistenceRevision;

    /** [SURV-X] 정산하지 않은 제련 경험치의 1/1000 단위. 구형 행은 0으로 채워진다. */
    @Column(nullable = false, columnDefinition = "int not null default 0")
    private int xpMilli;

    @Column(nullable = false)
    private short inputType;

    @Column(nullable = false)
    private int inputCount;

    @Column(nullable = false)
    private short fuelType;

    @Column(nullable = false)
    private int fuelCount;

    @Column(nullable = false)
    private short outputType;

    @Column(nullable = false)
    private int outputCount;

    @Column(nullable = false)
    private int burnTicks;

    @Column(nullable = false)
    private int burnTotalTicks;

    @Column(nullable = false)
    private int cookTicks;

    /**
     * [FURNACE-VARIANT] 화로 변형의 안정 코드({@link com.gameexpert.engine.FurnaceVariant#code}).
     * 0=화로 · 1=용광로 · 2=훈연기.
     *
     * <h4>스키마 마이그레이션</h4>
     * <p>이 열은 이번 트랙에서 새로 생겼다. 배포는 {@code spring.jpa.hibernate.ddl-auto=update}
     * 이라 Hibernate 가 {@code ALTER TABLE world_furnaces ADD COLUMN variant_code INT NOT NULL
     * DEFAULT 0} 을 낸다. 별도 마이그레이션 스크립트가 필요 없는 이유는 <b>기본값 0 이 곧
     * 화로</b>이기 때문이다 — 이 열이 없던 시절에 저장된 모든 행은 화로였고, 채워지는 값도
     * 화로다. {@link com.gameexpert.engine.FurnaceVariant#fromCode} 가 알 수 없는 코드까지
     * 화로로 읽으므로 되돌리기(롤백 후 재기동)에도 데이터가 깨지지 않는다.
     */
    @Column(nullable = false, columnDefinition = "int not null default 0")
    private int variantCode;

    @Column(length = 255)
    private String generatedInstallationId;

    @Column(length = 64)
    private String generatedInstallationFingerprint;

    public WorldFurnace(Long worldId, int x, int y, int z) {
        this.worldId = worldId;
        this.posX = x;
        this.posY = y;
        this.posZ = z;
    }

    public void replace(short[] itemTypes, int[] counts,
            int burnTicks, int burnTotalTicks, int cookTicks, int variantCode,
            int xpMilli) {
        validateSnapshot(itemTypes, counts, xpMilli);
        inputType = itemTypes[0];
        inputCount = counts[0];
        fuelType = itemTypes[1];
        fuelCount = counts[1];
        outputType = itemTypes[2];
        outputCount = counts[2];
        this.burnTicks = burnTicks;
        this.burnTotalTicks = burnTotalTicks;
        this.cookTicks = cookTicks;
        this.variantCode = variantCode;
        this.xpMilli = xpMilli;
    }

    public boolean replaceIfNewer(short[] itemTypes, int[] counts,
            int burnTicks, int burnTotalTicks, int cookTicks, int variantCode,
            int xpMilli, long revision) {
        validateSnapshot(itemTypes, counts, xpMilli);
        validateRevision(revision);
        validateXpMilli(this.xpMilli);
        if (revision == Long.MAX_VALUE) throw new IllegalArgumentException("furnace revision overflow");
        if (revision <= persistenceRevision) return false;
        replace(itemTypes, counts, burnTicks, burnTotalTicks, cookTicks, variantCode, xpMilli);
        persistenceRevision = revision;
        return true;
    }

    /** 폭발 정산이 행 전체를 한 번에 검증할 때 사용하는 current-schema 완전 일치 비교입니다. */
    public boolean matchesExact(short[] itemTypes, int[] counts,
            int expectedBurnTicks, int expectedBurnTotalTicks, int expectedCookTicks,
            int expectedVariantCode, int expectedXpMilli, long expectedRevision) {
        if (itemTypes == null || counts == null
                || itemTypes.length != FurnaceInventory.SLOTS
                || counts.length != FurnaceInventory.SLOTS) return false;
        return persistenceRevision == expectedRevision
                && variantCode == expectedVariantCode
                && burnTicks == expectedBurnTicks
                && burnTotalTicks == expectedBurnTotalTicks
                && cookTicks == expectedCookTicks
                && xpMilli == expectedXpMilli
                && Arrays.equals(itemTypes,
                        new short[] { inputType, fuelType, outputType })
                && Arrays.equals(counts,
                        new int[] { inputCount, fuelCount, outputCount });
    }

    public boolean claimGeneratedInstallation(String installationId, String fingerprint,
            int expectedVariantCode) {
        requireInstallationIdentity(installationId, fingerprint);
        validateXpMilli(xpMilli);
        if (generatedInstallationId == null && generatedInstallationFingerprint == null) {
            if (persistenceRevision != 0 || inputCount != 0 || fuelCount != 0 || outputCount != 0
                    || burnTicks != 0 || burnTotalTicks != 0 || cookTicks != 0 || xpMilli != 0) {
                throw new IllegalStateException("occupied furnace cannot become generated");
            }
            variantCode = expectedVariantCode;
            generatedInstallationId = installationId;
            generatedInstallationFingerprint = fingerprint;
            return true;
        }
        if (installationId.equals(generatedInstallationId)
                && fingerprint.equals(generatedInstallationFingerprint)
                && variantCode == expectedVariantCode) return false;
        throw new IllegalStateException("conflicting generated furnace installation");
    }

    public void requireSameGeneratedInstallation(String installationId, String fingerprint,
            int expectedVariantCode) {
        requireInstallationIdentity(installationId, fingerprint);
        validateXpMilli(xpMilli);
        if (!installationId.equals(generatedInstallationId)
                || !fingerprint.equals(generatedInstallationFingerprint)
                || variantCode != expectedVariantCode) {
            throw new IllegalStateException("conflicting generated furnace installation");
        }
    }

    private static void requireInstallationIdentity(String installationId, String fingerprint) {
        if (installationId == null || installationId.isBlank() || installationId.length() > 255) {
            throw new IllegalArgumentException("invalid generated installation id");
        }
        if (fingerprint == null || !fingerprint.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("invalid generated installation fingerprint");
        }
    }

    private static void validateSnapshot(short[] itemTypes, int[] counts, int xpMilli) {
        if (itemTypes == null || itemTypes.length != FurnaceInventory.SLOTS
                || counts == null || counts.length != FurnaceInventory.SLOTS) {
            throw new IllegalArgumentException("furnace entity snapshot must have three slots");
        }
        validateXpMilli(xpMilli);
    }

    private static void validateRevision(long revision) {
        if (revision < 0) {
            throw new IllegalArgumentException("furnace persistence revision must be non-negative");
        }
    }

    private static void validateXpMilli(int xpMilli) {
        if (xpMilli < 0 || xpMilli > FurnaceInventory.MAX_XP_MILLI) {
            throw new IllegalArgumentException("furnace xpMilli is outside its non-negative bound");
        }
    }
}
