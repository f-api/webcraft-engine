package com.gameexpert.brewing.entity;

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

/** Strict current-schema snapshot of one coordinate-owned brewing stand. */
@Getter
@Entity
@Table(name = "world_brewing_stands", uniqueConstraints = @UniqueConstraint(
        name = "uk_world_brewing_stand_pos",
        columnNames = { "world_id", "pos_x", "pos_y", "pos_z" }))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorldBrewingStand {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false) private Long worldId;
    @Column(nullable = false) private int posX;
    @Column(nullable = false) private int posY;
    @Column(nullable = false) private int posZ;
    @Column(nullable = false) private long persistenceRevision;

    @Column(nullable = false) private short fuelSlotType;
    @Column(nullable = false) private int fuelSlotCount;
    @Column(nullable = false) private short ingredientType;
    @Column(nullable = false) private int ingredientCount;
    @Column(nullable = false) private short bottle0Type;
    @Column(nullable = false) private int bottle0Count;
    @Column(nullable = false) private short bottle1Type;
    @Column(nullable = false) private int bottle1Count;
    @Column(nullable = false) private short bottle2Type;
    @Column(nullable = false) private int bottle2Count;
    @Column(nullable = false) private int fuel;
    @Column(nullable = false) private int brewTicks;
    @Column(nullable = false) private short brewingIngredient;
    /**
     * [BREWING-26.3] 병 칸 셋의 스택 성분(WCIC). 범용 물약 CONTENTS_* 의 potionContents 가 여기
     * 실린다. 옛 행·성분 없는 칸은 null 이다(연료·재료 칸은 성분을 싣지 않는다).
     */
    @Column(length = 1024) private String bottle0Components;
    @Column(length = 1024) private String bottle1Components;
    @Column(length = 1024) private String bottle2Components;
    @Column(length = 255) private String generatedInstallationId;
    @Column(length = 64) private String generatedInstallationFingerprint;

    public WorldBrewingStand(Long worldId, int x, int y, int z) {
        if (worldId == null) throw new IllegalArgumentException("world id is required");
        this.worldId = worldId;
        posX = x;
        posY = y;
        posZ = z;
    }

    public boolean replaceIfNewer(short[] types, int[] counts, int nextFuel,
            int nextBrewTicks, short nextBrewingIngredient, long revision) {
        return replaceIfNewer(types, counts, null, nextFuel, nextBrewTicks,
                nextBrewingIngredient, revision);
    }

    /** [BREWING-26.3] 칸별 성분({@code components} 는 null 이면 전부 없음)까지 함께 쓴다. */
    public boolean replaceIfNewer(short[] types, int[] counts, String[] components, int nextFuel,
            int nextBrewTicks, short nextBrewingIngredient, long revision) {
        validateArrays(types, counts);
        if (revision == Long.MAX_VALUE) throw new IllegalArgumentException("brewing revision overflow");
        if (revision <= persistenceRevision) return false;
        replace(types, counts, nextFuel, nextBrewTicks, nextBrewingIngredient);
        replaceComponents(components);
        persistenceRevision = revision;
        return true;
    }

    public boolean matches(short[] types, int[] counts, int expectedFuel,
            int expectedBrewTicks, short expectedBrewingIngredient, long revision) {
        validateArrays(types, counts);
        return persistenceRevision == revision
                && Arrays.equals(types, itemTypes()) && Arrays.equals(counts, counts())
                && fuel == expectedFuel && brewTicks == expectedBrewTicks
                && brewingIngredient == expectedBrewingIngredient;
    }

    /** [BREWING-26.3] 성분까지 같은가. */
    public boolean matches(short[] types, int[] counts, String[] components, int expectedFuel,
            int expectedBrewTicks, short expectedBrewingIngredient, long revision) {
        return matches(types, counts, expectedFuel, expectedBrewTicks, expectedBrewingIngredient,
                revision) && Arrays.equals(normalizedComponents(components), components());
    }

    /** 칸별 성분(연료·재료 칸은 언제나 null). */
    public String[] components() {
        return new String[] { null, null, bottle0Components, bottle1Components, bottle2Components };
    }

    private void replaceComponents(String[] components) {
        String[] normalized = normalizedComponents(components);
        bottle0Components = normalized[2];
        bottle1Components = normalized[3];
        bottle2Components = normalized[4];
    }

    private static String[] normalizedComponents(String[] components) {
        if (components == null) return new String[5];
        if (components.length != 5 || components[0] != null || components[1] != null) {
            throw new IllegalArgumentException("brewing components are carried by bottle slots only");
        }
        return components.clone();
    }

    public boolean claimGeneratedInstallation(String installationId, String fingerprint,
            short initialBottleType) {
        requireInstallationIdentity(installationId, fingerprint);
        if (initialBottleType <= 0) {
            throw new IllegalArgumentException("generated brewing stand initial bottle required");
        }
        if (generatedInstallationId == null && generatedInstallationFingerprint == null) {
            if (persistenceRevision != 0 || fuel != 0 || brewTicks != 0
                    || brewingIngredient != 0 || Arrays.stream(counts()).anyMatch(count -> count != 0)) {
                throw new IllegalStateException("occupied brewing stand cannot become generated");
            }
            generatedInstallationId = installationId;
            generatedInstallationFingerprint = fingerprint;
            replace(new short[] {0, 0, initialBottleType, 0, 0},
                    new int[] {0, 0, 1, 0, 0}, 0, 0, (short) 0);
            replaceComponents(null);
            return true;
        }
        if (installationId.equals(generatedInstallationId)
                && fingerprint.equals(generatedInstallationFingerprint)) return false;
        throw new IllegalStateException("conflicting generated brewing stand installation");
    }

    public void requireSameGeneratedInstallation(String installationId, String fingerprint) {
        requireInstallationIdentity(installationId, fingerprint);
        if (!installationId.equals(generatedInstallationId)
                || !fingerprint.equals(generatedInstallationFingerprint)) {
            throw new IllegalStateException("conflicting generated brewing stand installation");
        }
    }

    public short[] itemTypes() {
        return new short[] { fuelSlotType, ingredientType, bottle0Type, bottle1Type, bottle2Type };
    }

    public int[] counts() {
        return new int[] { fuelSlotCount, ingredientCount, bottle0Count, bottle1Count, bottle2Count };
    }

    private void replace(short[] types, int[] counts, int nextFuel,
            int nextBrewTicks, short nextBrewingIngredient) {
        fuelSlotType = types[0]; fuelSlotCount = counts[0];
        ingredientType = types[1]; ingredientCount = counts[1];
        bottle0Type = types[2]; bottle0Count = counts[2];
        bottle1Type = types[3]; bottle1Count = counts[3];
        bottle2Type = types[4]; bottle2Count = counts[4];
        fuel = nextFuel;
        brewTicks = nextBrewTicks;
        brewingIngredient = nextBrewingIngredient;
    }

    private static void validateArrays(short[] types, int[] counts) {
        if (types == null || types.length != 5 || counts == null || counts.length != 5) {
            throw new IllegalArgumentException("brewing entity snapshot must have five slots");
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
}
