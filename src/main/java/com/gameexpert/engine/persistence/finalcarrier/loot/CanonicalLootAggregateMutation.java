package com.gameexpert.engine.persistence.finalcarrier.loot;

import java.util.Objects;

/** Immutable exact container mutation supplied to the first-open persistence boundary. */
public record CanonicalLootAggregateMutation(
        long worldId, int x, int y, int z,
        CanonicalLootContainerKind containerKind,
        String laneInstallationIdentity,
        String installationFingerprint,
        String definitionFingerprint,
        String resultFingerprint,
        CanonicalLootStoredResolution resolution,
        boolean firstInstallation) {

    /** Existing callers/replays never acquire missing-aggregate creation authority implicitly. */
    public CanonicalLootAggregateMutation(long worldId, int x, int y, int z,
            CanonicalLootContainerKind containerKind, String laneInstallationIdentity,
            String installationFingerprint, String definitionFingerprint, String resultFingerprint,
            CanonicalLootStoredResolution resolution) {
        this(worldId, x, y, z, containerKind, laneInstallationIdentity, installationFingerprint,
                definitionFingerprint, resultFingerprint, resolution, false);
    }

    public CanonicalLootAggregateMutation {
        if (worldId <= 0L) throw new IllegalArgumentException("positive world ID required");
        Objects.requireNonNull(containerKind, "container kind");
        laneInstallationIdentity = requireText(
                laneInstallationIdentity, "lane installation identity");
        installationFingerprint = requireFingerprint(
                installationFingerprint, "installation fingerprint");
        definitionFingerprint = requireFingerprint(
                definitionFingerprint, "definition fingerprint");
        resultFingerprint = requireFingerprint(resultFingerprint, "result fingerprint");
        Objects.requireNonNull(resolution, "canonical LOOT resolution");
        if (resolution.slots().size() != containerKind.slots()) {
            throw new IllegalArgumentException("LOOT resolution does not match container shape");
        }
        if (!resultFingerprint.equals(resolution.fingerprint(definitionFingerprint))) {
            throw new IllegalArgumentException("LOOT result fingerprint mismatch");
        }
    }

    public static CanonicalLootAggregateMutation from(
            WorldCanonicalLootAssignment assignment,
            CanonicalLootStoredResolution resolution) {
        Objects.requireNonNull(assignment, "canonical LOOT assignment");
        Objects.requireNonNull(resolution, "canonical LOOT resolution");
        return new CanonicalLootAggregateMutation(
                assignment.getWorldId(), assignment.getPosX(), assignment.getPosY(),
                assignment.getPosZ(), assignment.getContainerKind(),
                assignment.getLaneInstallationIdentity(),
                assignment.getInstallationFingerprint(),
                assignment.getDefinitionFingerprint(),
                resolution.fingerprint(assignment.getDefinitionFingerprint()), resolution);
    }

    /**
     * Transient writer authority, not a persisted field. The caller holds the assignment write lock
     * and has checked live kind/ownership and resolved its authenticated canonical witness.
     * RESOLVED replay must continue to use from(), even when its aggregate is absent.
     */
    public static CanonicalLootAggregateMutation forFirstInstallation(
            WorldCanonicalLootAssignment assignment, CanonicalLootStoredResolution resolution) {
        Objects.requireNonNull(assignment, "canonical LOOT assignment");
        if (assignment.getStatus() != WorldCanonicalLootAssignment.Status.UNOPENED) {
            throw new IllegalStateException("missing aggregate creation requires an UNOPENED assignment");
        }
        CanonicalLootAggregateMutation exact = from(assignment, resolution);
        return new CanonicalLootAggregateMutation(exact.worldId(), exact.x(), exact.y(), exact.z(),
                exact.containerKind(), exact.laneInstallationIdentity(), exact.installationFingerprint(),
                exact.definitionFingerprint(), exact.resultFingerprint(), exact.resolution(), true);
    }

    private static String requireText(String value, String label) {
        if (value == null || value.isBlank() || value.length() > 255) {
            throw new IllegalArgumentException(label + " required");
        }
        return value;
    }

    private static String requireFingerprint(String value, String label) {
        if (value == null || !value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(label + " must be SHA-256");
        }
        return value;
    }
}
