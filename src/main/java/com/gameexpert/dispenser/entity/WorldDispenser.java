package com.gameexpert.dispenser.entity;

import com.gameexpert.chest.entity.ChestItem;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Current-schema authoritative nine-slot dispenser aggregate. */
@Getter
@Entity
@Table(name = "world_dispensers", uniqueConstraints = @UniqueConstraint(
        name = "uk_world_dispenser_pos",
        columnNames = {"world_id", "pos_x", "pos_y", "pos_z"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorldDispenser {
    public static final int CONTAINER_SIZE = 9;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false) private Long worldId;
    @Column(nullable = false) private int posX;
    @Column(nullable = false) private int posY;
    @Column(nullable = false) private int posZ;
    @Column(nullable = false) private long persistenceRevision;
    @Column(nullable = false, length = 255) private String generatedInstallationId;
    @Column(nullable = false, length = 64) private String generatedInstallationFingerprint;
    @Column(length = 255) private String canonicalLootInstallationId;
    @Column(length = 64) private String canonicalLootInstallationFingerprint;
    @Column(length = 64) private String canonicalLootDefinitionFingerprint;
    @Column(length = 64) private String canonicalLootResultFingerprint;
    @Lob private byte[] canonicalLootResolution;

    @ElementCollection(fetch = FetchType.EAGER)
    @OrderBy("slot ASC")
    @CollectionTable(name = "world_dispenser_items",
            joinColumns = @JoinColumn(name = "dispenser_id"),
            uniqueConstraints = @UniqueConstraint(name = "uk_world_dispenser_item_slot",
                    columnNames = {"dispenser_id", "slot"}))
    private List<ChestItem> items = new ArrayList<>();

    public WorldDispenser(Long worldId, int x, int y, int z, String installationId,
            String installationFingerprint) {
        if (worldId == null) throw new IllegalArgumentException("world id is required");
        requireInstallationIdentity(installationId, installationFingerprint);
        this.worldId = worldId;
        posX = x;
        posY = y;
        posZ = z;
        generatedInstallationId = installationId;
        generatedInstallationFingerprint = installationFingerprint;
    }

    public void requireSameGeneratedInstallation(String installationId, String fingerprint) {
        requireInstallationIdentity(installationId, fingerprint);
        if (!installationId.equals(generatedInstallationId)
                || !fingerprint.equals(generatedInstallationFingerprint)) {
            throw new IllegalStateException("conflicting generated dispenser installation");
        }
    }

    /** Installs or byte-exactly replays one canonical first-use result under the row lock. */
    public boolean installCanonicalLoot(String installationId, String installationFingerprint,
            String definitionFingerprint, String resultFingerprint, byte[] resolution,
            List<ChestItem> fixedItems) {
        requireInstallationIdentity(installationId, installationFingerprint);
        requireFingerprint(definitionFingerprint, "definition fingerprint");
        requireFingerprint(resultFingerprint, "result fingerprint");
        if (resolution == null || resolution.length == 0 || fixedItems == null) {
            throw new IllegalArgumentException("canonical loot payload is required");
        }
        List<ChestItem> canonicalItems = canonicalizeItems(fixedItems);
        if (hasCanonicalMetadata()) {
            requireCompleteCanonicalMetadata();
            if (!installationId.equals(canonicalLootInstallationId)
                    || !installationFingerprint.equals(canonicalLootInstallationFingerprint)
                    || !definitionFingerprint.equals(canonicalLootDefinitionFingerprint)
                    || !resultFingerprint.equals(canonicalLootResultFingerprint)
                    || !java.security.MessageDigest.isEqual(resolution, canonicalLootResolution)
                    || persistenceRevision < 1L) {
                throw new IllegalStateException("conflicting canonical dispenser loot replay");
            }
            // Contents/revision are mutable after first use.  Exact receipt replay is idempotent
            // and must preserve that newer durable state rather than demanding the initial slots.
            return false;
        }
        if (persistenceRevision != 0L || !items.isEmpty()) {
            throw new IllegalStateException("canonical loot cannot overwrite dispenser contents");
        }
        canonicalLootInstallationId = installationId;
        canonicalLootInstallationFingerprint = installationFingerprint;
        canonicalLootDefinitionFingerprint = definitionFingerprint;
        canonicalLootResultFingerprint = resultFingerprint;
        canonicalLootResolution = resolution.clone();
        items.clear();
        items.addAll(canonicalItems);
        persistenceRevision = 1L;
        return true;
    }

    /** Compares one locked row with the exact identity and sparse form of a nine-slot snapshot. */
    public boolean matchesExact(Long expectedWorldId, int expectedX, int expectedY, int expectedZ,
            long expectedRowId, long expectedRevision, String expectedAuthorityId,
            List<ChestItem> expectedItems) {
        if (!Objects.equals(id, expectedRowId)
                || !Objects.equals(worldId, expectedWorldId)
                || posX != expectedX || posY != expectedY || posZ != expectedZ
                || expectedRevision < 0L || persistenceRevision != expectedRevision
                || expectedAuthorityId == null
                || !expectedAuthorityId.equals(generatedInstallationId)) {
            return false;
        }
        try {
            requireInstallationIdentity(generatedInstallationId, generatedInstallationFingerprint);
            if (!canonicalLootIdentityMatches(expectedAuthorityId)) {
                return false;
            }
            return sameCanonicalItems(items, expectedItems);
        } catch (RuntimeException malformed) {
            return false;
        }
    }

    private boolean canonicalLootIdentityMatches(String expectedAuthorityId) {
        boolean present = canonicalLootInstallationId != null
                || canonicalLootInstallationFingerprint != null
                || canonicalLootDefinitionFingerprint != null
                || canonicalLootResultFingerprint != null || canonicalLootResolution != null;
        if (!present) return true;
        if (canonicalLootResolution == null || canonicalLootResolution.length == 0) return false;
        try {
            requireInstallationIdentity(canonicalLootInstallationId,
                    canonicalLootInstallationFingerprint);
            requireFingerprint(canonicalLootDefinitionFingerprint, "definition fingerprint");
            requireFingerprint(canonicalLootResultFingerprint, "result fingerprint");
        } catch (RuntimeException malformed) {
            return false;
        }
        return expectedAuthorityId.equals(canonicalLootInstallationId)
                && generatedInstallationFingerprint.equals(canonicalLootInstallationFingerprint);
    }

    /** Returns a detached, canonical slot-ordered snapshot for lock-time comparisons. */
    public List<ChestItem> getItems() {
        return List.copyOf(canonicalizeItems(items));
    }

    /** Compares sparse item lists by slot and every current-schema identity column. */
    public static boolean sameCanonicalItems(List<ChestItem> left, List<ChestItem> right) {
        return sameItems(canonicalizeItems(left), canonicalizeItems(right));
    }

    private static List<ChestItem> canonicalizeItems(List<ChestItem> source) {
        if (source == null) {
            throw new IllegalArgumentException("dispenser items are required");
        }
        if (source.size() > CONTAINER_SIZE) {
            throw new IllegalArgumentException("canonical loot exceeds the dispenser container");
        }
        boolean[] used = new boolean[CONTAINER_SIZE];
        List<ChestItem> canonical = new ArrayList<>(source.size());
        for (ChestItem item : source) {
            if (item == null) throw new IllegalArgumentException("dispenser item is required");
            int slot = item.getSlot();
            if (slot < 0 || slot >= CONTAINER_SIZE) {
                throw new IllegalArgumentException("canonical loot slot outside the dispenser");
            }
            if (used[slot]) {
                throw new IllegalArgumentException("canonical loot repeats a dispenser slot");
            }
            used[slot] = true;
            canonical.add(new ChestItem(slot, item.getItemType(), item.getItemCount(),
                    item.getDurability(), item.getEnchantments(), item.getMapId(),
                    item.getShulkerId(), item.getBucketMobData(), item.getItemComponentData()));
        }
        canonical.sort(Comparator.comparingInt(ChestItem::getSlot));
        return canonical;
    }

    private static void requireInstallationIdentity(String installationId, String fingerprint) {
        if (installationId == null || installationId.isBlank() || installationId.length() > 255) {
            throw new IllegalArgumentException("invalid generated installation id");
        }
        if (fingerprint == null || !fingerprint.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("invalid generated installation fingerprint");
        }
    }

    private static void requireFingerprint(String value, String label) {
        if (value == null || !value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("invalid canonical loot " + label);
        }
    }

    private static boolean sameItems(List<ChestItem> left, List<ChestItem> right) {
        if (left.size() != right.size()) return false;
        for (int index = 0; index < left.size(); index++) {
            ChestItem a = left.get(index);
            ChestItem b = right.get(index);
            if (a.getSlot() != b.getSlot() || a.getItemType() != b.getItemType()
                    || a.getItemCount() != b.getItemCount()
                    || !java.util.Objects.equals(a.getDurability(), b.getDurability())
                    || !java.util.Objects.equals(a.getEnchantments(), b.getEnchantments())
                    || !java.util.Objects.equals(a.getMapId(), b.getMapId())
                    || !java.util.Objects.equals(a.getShulkerId(), b.getShulkerId())
                    || !java.util.Objects.equals(a.getBucketMobData(), b.getBucketMobData())
                    || !java.util.Objects.equals(
                            a.getItemComponentData(), b.getItemComponentData())) return false;
        }
        return true;
    }

    public byte[] getCanonicalLootResolution() {
        return canonicalLootResolution == null ? null : canonicalLootResolution.clone();
    }

    private boolean hasCanonicalMetadata() {
        return canonicalLootInstallationId != null || canonicalLootInstallationFingerprint != null
                || canonicalLootDefinitionFingerprint != null
                || canonicalLootResultFingerprint != null || canonicalLootResolution != null;
    }

    private void requireCompleteCanonicalMetadata() {
        if (canonicalLootInstallationId == null || canonicalLootInstallationFingerprint == null
                || canonicalLootDefinitionFingerprint == null
                || canonicalLootResultFingerprint == null || canonicalLootResolution == null
                || canonicalLootResolution.length == 0) {
            throw new IllegalStateException("incomplete canonical dispenser loot identity");
        }
        requireInstallationIdentity(canonicalLootInstallationId,
                canonicalLootInstallationFingerprint);
        requireFingerprint(canonicalLootDefinitionFingerprint, "definition fingerprint");
        requireFingerprint(canonicalLootResultFingerprint, "result fingerprint");
    }
}
