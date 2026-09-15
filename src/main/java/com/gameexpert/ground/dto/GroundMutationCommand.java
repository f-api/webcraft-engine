package com.gameexpert.ground.dto;

import com.gameexpert.state.service.inventory.PlayerInventoryMutationSnapshot;
import com.gameexpert.ground.entity.WorldGroundRevision;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;

/** Complete optimistic command for one exactly-once change to the ground aggregate. */
public final class GroundMutationCommand {

    private static final int MUTATION_NAMESPACE_LIMIT = 8;
    /** Largest shared item/XP identity encodable by every stable mutation namespace. */
    public static final long MAX_GROUND_ENTITY_ID = maximumEntityIdForMutationNamespace(
            MUTATION_NAMESPACE_LIMIT - 1);

    public enum Kind { PLAYER_PICKUP, PLAYER_DEATH, PLAYER_DROP, BLOCK_DROP, XP_PICKUP }

    private final long mutationId;
    private final Kind kind;
    private final Long worldId;
    private final long expectedGroundRevision;
    private final long committedGroundRevision;
    private final Long expectedPlayerRevision;
    private final PlayerInventoryMutationSnapshot committedPlayer;
    private final List<GroundItemSnapshot> insertedItems;
    private final List<Long> removedItemIds;
    private final List<GroundXpOrbSnapshot> insertedXpOrbs;
    private final List<Long> removedXpOrbIds;

    public GroundMutationCommand(long mutationId, Kind kind, Long worldId,
            long expectedGroundRevision, long committedGroundRevision,
            Long expectedPlayerRevision, PlayerInventoryMutationSnapshot committedPlayer,
            List<GroundItemSnapshot> insertedItems, List<Long> removedItemIds,
            List<GroundXpOrbSnapshot> insertedXpOrbs, List<Long> removedXpOrbIds) {
        if (mutationId <= 0 || mutationId == Long.MAX_VALUE || kind == null
                || !isValidWorldId(worldId)) {
            throw new IllegalArgumentException("stable mutation identity is required");
        }
        if (!WorldGroundRevision.isValidSuccessor(
                expectedGroundRevision, committedGroundRevision)) {
            throw new IllegalArgumentException("ground revision must advance by one");
        }
        List<GroundItemSnapshot> itemCopies = immutableUnique(
                insertedItems, GroundItemSnapshot::entityId, "item");
        List<Long> removedItemCopies = immutableIds(removedItemIds, "removed item");
        List<GroundXpOrbSnapshot> xpCopies = immutableUnique(
                insertedXpOrbs, GroundXpOrbSnapshot::entityId, "XP orb");
        List<Long> removedXpCopies = immutableIds(removedXpOrbIds, "removed XP orb");
        if (intersects(itemCopies.stream().map(GroundItemSnapshot::entityId).toList(),
                removedItemCopies)
                || intersects(xpCopies.stream().map(GroundXpOrbSnapshot::entityId).toList(),
                        removedXpCopies)) {
            throw new IllegalArgumentException("one entity cannot be inserted and removed together");
        }
        if (committedPlayer == null) {
            if (expectedPlayerRevision != null) {
                throw new IllegalArgumentException("player revision requires a player snapshot");
            }
        } else if (expectedPlayerRevision == null || expectedPlayerRevision < 0
                || expectedPlayerRevision == Long.MAX_VALUE
                || committedPlayer.revision() != Math.addExact(expectedPlayerRevision, 1)
                || !worldId.equals(committedPlayer.worldId())) {
            throw new IllegalArgumentException("player CAS contract is invalid");
        }
        if (itemCopies.isEmpty() && removedItemCopies.isEmpty() && xpCopies.isEmpty()
                && removedXpCopies.isEmpty()
                && (kind != Kind.PLAYER_DEATH || committedPlayer == null)) {
            throw new IllegalArgumentException("ground mutation cannot be empty");
        }
        this.mutationId = mutationId;
        this.kind = kind;
        this.worldId = worldId;
        this.expectedGroundRevision = expectedGroundRevision;
        this.committedGroundRevision = committedGroundRevision;
        this.expectedPlayerRevision = expectedPlayerRevision;
        this.committedPlayer = committedPlayer;
        this.insertedItems = itemCopies;
        this.removedItemIds = removedItemCopies;
        this.insertedXpOrbs = xpCopies;
        this.removedXpOrbIds = removedXpCopies;
    }

    public long mutationId() { return mutationId; }
    public Kind kind() { return kind; }
    public Long worldId() { return worldId; }
    public long expectedGroundRevision() { return expectedGroundRevision; }
    public long committedGroundRevision() { return committedGroundRevision; }
    public Long expectedPlayerRevision() { return expectedPlayerRevision; }
    public PlayerInventoryMutationSnapshot committedPlayer() { return committedPlayer; }
    public List<GroundItemSnapshot> insertedItems() { return insertedItems; }
    public List<Long> removedItemIds() { return removedItemIds; }
    public List<GroundXpOrbSnapshot> insertedXpOrbs() { return insertedXpOrbs; }
    public List<Long> removedXpOrbIds() { return removedXpOrbIds; }

    private static <T> List<T> immutableUnique(
            List<T> values, java.util.function.ToLongFunction<T> identity, String label) {
        if (values == null) throw new IllegalArgumentException(label + " snapshots are required");
        for (T value : values) {
            if (value == null) throw new IllegalArgumentException(label + " identity is invalid");
        }
        List<T> copy = List.copyOf(values);
        HashSet<Long> ids = new HashSet<>();
        for (T value : copy) {
            long id = identity.applyAsLong(value);
            if (id <= 0 || id > MAX_GROUND_ENTITY_ID || !ids.add(id)) {
                throw new IllegalArgumentException(label + " identity is invalid");
            }
        }
        return copy;
    }

    private static List<Long> immutableIds(List<Long> values, String label) {
        if (values == null) throw new IllegalArgumentException(label + " identities are required");
        for (Long id : values) {
            if (id == null) throw new IllegalArgumentException(label + " identity is invalid");
        }
        List<Long> copy = List.copyOf(values);
        HashSet<Long> ids = new HashSet<>();
        for (Long id : copy) {
            if (id == null || id <= 0 || id > MAX_GROUND_ENTITY_ID || !ids.add(id)) {
                throw new IllegalArgumentException(label + " identity is invalid");
            }
        }
        return copy;
    }

    private static boolean isValidWorldId(Long worldId) {
        return worldId != null && worldId > 0 && worldId < Long.MAX_VALUE;
    }

    private static long maximumEntityIdForMutationNamespace(int namespace) {
        return (Long.MAX_VALUE - 1L - namespace) / MUTATION_NAMESPACE_LIMIT + 1L;
    }

    private static boolean intersects(List<Long> left, List<Long> right) {
        HashSet<Long> ids = new HashSet<>(left);
        return right.stream().anyMatch(ids::contains);
    }

    /** Fingerprints immutable command identities and exact ground payloads for collision rejection. */
    public String fingerprint() {
        String player = committedPlayer == null ? "-"
                : expectedPlayerRevision + ":" + committedPlayer.fingerprint();
        String canonical = kind + "|" + worldId + "|" + mutationId + "|"
                + expectedGroundRevision + "|" + committedGroundRevision + "|" + player + "|"
                + insertedItems + "|" + removedItemIds + "|" + insertedXpOrbs + "|"
                + removedXpOrbIds;
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

}
