package com.gameexpert.engine;

import com.gameexpert.engine.persistence.finalcarrier.structureentity.GeneratedStructureEntityFacts;
import com.gameexpert.engine.persistence.finalcarrier.structureentity.WorldGeneratedStructureEntityState;
import com.gameexpert.engine.persistence.finalcarrier.structureentity.WorldGeneratedStructureEntityState.ArmorStandRuntimeSnapshot;
import com.gameexpert.engine.persistence.finalcarrier.structureentity.WorldGeneratedStructureEntityState.CushionRuntimeSnapshot;
import com.gameexpert.engine.persistence.finalcarrier.structureentity.WorldGeneratedStructureEntityState.Lifecycle;
import com.gameexpert.engine.persistence.finalcarrier.structureentity.WorldGeneratedStructureEntityState.MinecartRuntimeSnapshot;
import com.gameexpert.engine.persistence.finalcarrier.structureentity.WorldGeneratedStructureEntityState.RuntimeBinding;
import com.gameexpert.engine.persistence.finalcarrier.structureentity.WorldGeneratedStructureEntityState.RuntimeSnapshot;
import com.gameexpert.terrain.Blocks;
import com.gameexpert.ws.dto.WsMessages.ArmorStandSnapshot;
import com.gameexpert.ws.dto.WsMessages.ChestMinecartSnapshot;
import com.gameexpert.ws.dto.WsMessages.GeneratedCushionSnapshot;
import com.gameexpert.ws.dto.WsMessages.GeneratedEntitySnapshot;
import com.gameexpert.ws.dto.WsMessages.GeneratedEntityTarget;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Owner-thread live registry for authenticated generated non-mob ENTS state. */
public final class GeneratedStructureEntitySystem {
    private final long worldId;
    private final Map<Long, RuntimeSnapshot> byEntityId = new HashMap<>();
    private final Map<AuthorityKey, RuntimeSnapshot> byAuthority = new HashMap<>();
    private final EnumMap<GeneratedStructureEntityFacts.Kind, Map<Long, RuntimeSnapshot>> byKind =
            new EnumMap<>(GeneratedStructureEntityFacts.Kind.class);

    public GeneratedStructureEntitySystem(long worldId) {
        if (worldId <= 0L) throw new IllegalArgumentException("positive world ID required");
        this.worldId = worldId;
        for (GeneratedStructureEntityFacts.Kind kind : GeneratedStructureEntityFacts.Kind.values()) {
            byKind.put(kind, new HashMap<>());
        }
    }

    /**
     * Validates the complete callback before changing any index, then atomically installs its
     * current revisions. Older callbacks are harmless and DEAD rows remain indexed tombstones.
     */
    public InstallDelta install(List<RuntimeSnapshot> values) {
        Objects.requireNonNull(values, "generated entity snapshots");
        LinkedHashMap<Long, RuntimeSnapshot> incomingByEntity = new LinkedHashMap<>();
        HashMap<AuthorityKey, RuntimeSnapshot> incomingByAuthority = new HashMap<>();
        for (RuntimeSnapshot snapshot : values) {
            requireEnvelope(snapshot);
            RuntimeSnapshot duplicateEntity = incomingByEntity.putIfAbsent(
                    snapshot.binding().entityId(), snapshot);
            if (duplicateEntity != null) {
                throw new IllegalStateException("generated entity callback contains duplicate ID");
            }
            AuthorityKey authority = AuthorityKey.of(snapshot.binding());
            RuntimeSnapshot duplicateAuthority = incomingByAuthority.putIfAbsent(authority, snapshot);
            if (duplicateAuthority != null) {
                throw new IllegalStateException("generated entity callback contains duplicate authority");
            }
        }

        LinkedHashMap<Long, RuntimeSnapshot> next = new LinkedHashMap<>(byEntityId);
        ArrayList<GeneratedEntitySnapshot> spawned = new ArrayList<>();
        ArrayList<GeneratedEntitySnapshot> updated = new ArrayList<>();
        ArrayList<GeneratedEntityTarget> removed = new ArrayList<>();
        for (RuntimeSnapshot incoming : incomingByEntity.values()) {
            RuntimeBinding binding = incoming.binding();
            AuthorityKey authority = AuthorityKey.of(binding);
            RuntimeSnapshot entityCurrent = next.get(binding.entityId());
            RuntimeSnapshot authorityCurrent = byAuthority.get(authority);
            if (entityCurrent != null && !AuthorityKey.of(entityCurrent.binding()).equals(authority)) {
                throw new IllegalStateException("generated entity ID changed authority");
            }
            if (authorityCurrent != null
                    && authorityCurrent.binding().entityId() != binding.entityId()) {
                throw new IllegalStateException("generated entity authority changed ID");
            }
            RuntimeSnapshot current = entityCurrent == null ? authorityCurrent : entityCurrent;
            if (current != null) {
                requireSameBinding(current.binding(), binding);
                if (incoming.revision() < current.revision()) continue;
                if (incoming.revision() == current.revision()) {
                    if (!sameSnapshot(current, incoming)) {
                        throw new IllegalStateException(
                                "generated entity callback conflicts at current revision");
                    }
                    continue;
                }
                if (current.lifecycle() == Lifecycle.DEAD
                        && incoming.lifecycle() != Lifecycle.DEAD) {
                    throw new IllegalStateException("generated entity tombstone cannot resurrect");
                }
                next.put(binding.entityId(), incoming);
                if (current.lifecycle() == Lifecycle.LIVE
                        && incoming.lifecycle() == Lifecycle.DEAD) {
                    removed.add(target(incoming));
                } else if (incoming.lifecycle() == Lifecycle.LIVE) {
                    updated.add(toWire(incoming));
                }
            } else {
                next.put(binding.entityId(), incoming);
                if (incoming.lifecycle() == Lifecycle.LIVE) spawned.add(toWire(incoming));
            }
        }

        byEntityId.clear();
        byAuthority.clear();
        for (Map<Long, RuntimeSnapshot> index : byKind.values()) index.clear();
        for (RuntimeSnapshot snapshot : next.values()) {
            RuntimeBinding binding = snapshot.binding();
            byEntityId.put(binding.entityId(), snapshot);
            byAuthority.put(AuthorityKey.of(binding), snapshot);
            byKind.get(binding.kind()).put(binding.entityId(), snapshot);
        }
        return new InstallDelta(spawned, updated, removed);
    }

    public RuntimeSnapshot snapshotByEntityId(long entityId) {
        return byEntityId.get(entityId);
    }

    /** Resolves a staged action target without erasing unknown, kind-drift, or tombstone states. */
    public LiveResolution resolveLive(GeneratedStructureEntityFacts.Kind expectedKind, long entityId) {
        Objects.requireNonNull(expectedKind, "expected generated entity kind");
        RuntimeSnapshot snapshot = byEntityId.get(entityId);
        if (snapshot == null) return LiveResolution.of(LiveResolution.Status.UNKNOWN, null);
        if (snapshot.binding().kind() != expectedKind) {
            return LiveResolution.of(LiveResolution.Status.KIND_MISMATCH, snapshot);
        }
        if (snapshot.lifecycle() != Lifecycle.LIVE) {
            return LiveResolution.of(LiveResolution.Status.DEAD, snapshot);
        }
        return LiveResolution.of(LiveResolution.Status.LIVE, snapshot);
    }

    public RuntimeSnapshot snapshotByAuthority(String installationIdentity, int encounterOrdinal) {
        return byAuthority.get(new AuthorityKey(installationIdentity, encounterOrdinal));
    }

    public List<RuntimeSnapshot> snapshotsByKind(GeneratedStructureEntityFacts.Kind kind) {
        Map<Long, RuntimeSnapshot> index = byKind.get(Objects.requireNonNull(kind, "kind"));
        return index.values().stream().sorted((left, right) -> Long.compare(
                left.binding().entityId(), right.binding().entityId())).toList();
    }

    /** Current live wire view in stable entity-ID order; DEAD tombstones never leak. */
    public List<GeneratedEntitySnapshot> activeSnapshots() {
        return byEntityId.values().stream()
                .filter(snapshot -> snapshot.lifecycle() == Lifecycle.LIVE)
                .sorted((left, right) -> Long.compare(
                        left.binding().entityId(), right.binding().entityId()))
                .map(GeneratedStructureEntitySystem::toWire).toList();
    }

    public List<RuntimeSnapshot> activeRuntimeSnapshotsInChunks(Set<Long> chunkKeys) {
        Objects.requireNonNull(chunkKeys, "visible chunk keys");
        return byEntityId.values().stream()
                .filter(snapshot -> snapshot.lifecycle() == Lifecycle.LIVE)
                .filter(snapshot -> chunkKeys.contains(chunkKey(
                        Math.floorDiv((int) Math.floor(snapshot.transform().x()), Blocks.CHUNK_X),
                        Math.floorDiv((int) Math.floor(snapshot.transform().z()), Blocks.CHUNK_Z))))
                .sorted((left, right) -> Long.compare(
                        left.binding().entityId(), right.binding().entityId())).toList();
    }

    public GeneratedEntityTarget targetByEntityId(long entityId) {
        RuntimeSnapshot snapshot = byEntityId.get(entityId);
        if (snapshot == null) throw new IllegalStateException("unknown generated entity ID");
        return target(snapshot);
    }

    private static long chunkKey(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) ^ (chunkZ & 0xffff_ffffL);
    }

    private void requireEnvelope(RuntimeSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "generated entity snapshot");
        RuntimeBinding binding = Objects.requireNonNull(snapshot.binding(), "runtime binding");
        if (binding.worldId() != worldId || binding.entityId() <= 0L
                || binding.kind() == null || binding.encounterOrdinal() < 0
                || snapshot.revision() < 0L || snapshot.lifecycle() == null) {
            throw new IllegalStateException("generated entity snapshot has an invalid envelope");
        }
        if ((snapshot instanceof ArmorStandRuntimeSnapshot)
                != (binding.kind() == GeneratedStructureEntityFacts.Kind.ARMOR_STAND)
                || (snapshot instanceof CushionRuntimeSnapshot)
                != (binding.kind() == GeneratedStructureEntityFacts.Kind.CUSHION)
                || (snapshot instanceof MinecartRuntimeSnapshot)
                != (binding.kind() == GeneratedStructureEntityFacts.Kind.CHEST_MINECART)) {
            throw new IllegalStateException("generated entity snapshot kind differs from its type");
        }
        AuthorityKey.of(binding);
        toWire(snapshot);
    }

    private static void requireSameBinding(RuntimeBinding current, RuntimeBinding incoming) {
        if (!current.equals(incoming)) {
            throw new IllegalStateException("generated entity durable binding changed");
        }
    }

    private static boolean sameSnapshot(RuntimeSnapshot left, RuntimeSnapshot right) {
        if (!left.binding().equals(right.binding()) || left.revision() != right.revision()
                || left.lifecycle() != right.lifecycle()
                || !left.transform().equals(right.transform()) || left.getClass() != right.getClass()) {
            return false;
        }
        if (left instanceof ArmorStandRuntimeSnapshot a
                && right instanceof ArmorStandRuntimeSnapshot b) {
            return Arrays.equals(a.poseHead(), b.poseHead())
                    && Arrays.equals(a.poseBody(), b.poseBody())
                    && a.equipmentSlot().equals(b.equipmentSlot())
                    && a.equipmentItem().equals(b.equipmentItem())
                    && a.showArms() == b.showArms() && a.small() == b.small()
                    && a.noBasePlate() == b.noBasePlate() && a.invisible() == b.invisible()
                    && a.invulnerable() == b.invulnerable()
                    && a.disabledSlots() == b.disabledSlots()
                    && Float.floatToRawIntBits(a.health()) == Float.floatToRawIntBits(b.health());
        }
        if (left instanceof CushionRuntimeSnapshot a && right instanceof CushionRuntimeSnapshot b) {
            return a.color().equals(b.color()) && a.blockX() == b.blockX()
                    && a.blockY() == b.blockY() && a.blockZ() == b.blockZ()
                    && a.invulnerable() == b.invulnerable()
                    && Objects.equals(a.rider(), b.rider())
                    && Objects.equals(a.customName(), b.customName());
        }
        if (left instanceof MinecartRuntimeSnapshot a && right instanceof MinecartRuntimeSnapshot b) {
            return a.lootTable().equals(b.lootTable()) && a.lootSeed() == b.lootSeed()
                    && a.provenance().equals(b.provenance())
                    && a.lootStatus() == b.lootStatus()
                    && Objects.equals(a.lootDefinitionFingerprint(), b.lootDefinitionFingerprint())
                    && Objects.equals(a.lootResultFingerprint(), b.lootResultFingerprint())
                    && Arrays.equals(a.lootResolution(), b.lootResolution())
                    && sameCargo(a.cargo(), b.cargo());
        }
        return false;
    }

    private static boolean sameCargo(List<com.gameexpert.chest.entity.ChestItem> left,
            List<com.gameexpert.chest.entity.ChestItem> right) {
        if (left.size() != right.size()) return false;
        for (int index = 0; index < left.size(); index++) {
            var a = left.get(index);
            var b = right.get(index);
            if (a.getSlot() != b.getSlot() || a.getItemType() != b.getItemType()
                    || a.getItemCount() != b.getItemCount()
                    || !Objects.equals(a.getDurability(), b.getDurability())
                    || !Objects.equals(a.getEnchantments(), b.getEnchantments())
                    || !Objects.equals(a.getMapId(), b.getMapId())
                    || !Objects.equals(a.getShulkerId(), b.getShulkerId())
                    || !Objects.equals(a.getBucketMobData(), b.getBucketMobData())
                    || !Objects.equals(a.getItemComponentData(), b.getItemComponentData())) return false;
        }
        return true;
    }

    public static GeneratedEntitySnapshot toWire(RuntimeSnapshot snapshot) {
        RuntimeBinding binding = snapshot.binding();
        var transform = snapshot.transform();
        if (snapshot instanceof ArmorStandRuntimeSnapshot armor) {
            return new ArmorStandSnapshot(1, binding.kind().name(), binding.entityId(),
                    transform.x(), transform.y(), transform.z(), transform.yaw(), transform.pitch(),
                    transform.velocityX(), transform.velocityY(), transform.velocityZ(),
                    armor.poseHead(), armor.poseBody(), armor.equipmentSlot(), armor.equipmentItem(),
                    armor.showArms(), armor.small(), armor.noBasePlate(), armor.invisible(),
                    armor.invulnerable(), armor.disabledSlots(), armor.health());
        }
        if (snapshot instanceof CushionRuntimeSnapshot cushion) {
            return new GeneratedCushionSnapshot(1, binding.kind().name(), binding.entityId(),
                    binding.entityId(), cushionItemType(cushion.color()), transform.x(), transform.y(),
                    transform.z(), transform.yaw(), cushion.rider(), cushion.customName(),
                    cushion.invulnerable());
        }
        if (snapshot instanceof MinecartRuntimeSnapshot minecart) {
            return new ChestMinecartSnapshot(1, binding.kind().name(), binding.entityId(),
                    transform.x(), transform.y(), transform.z(), transform.yaw(), transform.pitch(),
                    transform.velocityX(), transform.velocityY(), transform.velocityZ(),
                    minecart.lootStatus().name());
        }
        throw new IllegalStateException("unsupported generated entity snapshot");
    }

    private static GeneratedEntityTarget target(RuntimeSnapshot snapshot) {
        return new GeneratedEntityTarget(1, snapshot.binding().kind().name(),
                snapshot.binding().entityId());
    }

    private static short cushionItemType(String color) {
        int value = switch (color) {
            case "white" -> Blocks.WHITE_CUSHION;
            case "orange" -> Blocks.ORANGE_CUSHION;
            case "magenta" -> Blocks.MAGENTA_CUSHION;
            case "light_blue" -> Blocks.LIGHT_BLUE_CUSHION;
            case "yellow" -> Blocks.YELLOW_CUSHION;
            case "lime" -> Blocks.LIME_CUSHION;
            case "pink" -> Blocks.PINK_CUSHION;
            case "gray" -> Blocks.GRAY_CUSHION;
            case "light_gray" -> Blocks.LIGHT_GRAY_CUSHION;
            case "cyan" -> Blocks.CYAN_CUSHION;
            case "purple" -> Blocks.PURPLE_CUSHION;
            case "blue" -> Blocks.BLUE_CUSHION;
            case "brown" -> Blocks.BROWN_CUSHION;
            case "green" -> Blocks.GREEN_CUSHION;
            case "red" -> Blocks.RED_CUSHION;
            case "black" -> Blocks.BLACK_CUSHION;
            default -> throw new IllegalStateException("unsupported generated cushion color");
        };
        return (short) value;
    }

    public static final class InstallDelta {
        private final List<GeneratedEntitySnapshot> spawned;
        private final List<GeneratedEntitySnapshot> updated;
        private final List<GeneratedEntityTarget> removed;

        private InstallDelta(List<GeneratedEntitySnapshot> spawned,
                List<GeneratedEntitySnapshot> updated, List<GeneratedEntityTarget> removed) {
            this.spawned = List.copyOf(spawned);
            this.updated = List.copyOf(updated);
            this.removed = List.copyOf(removed);
        }

        public List<GeneratedEntitySnapshot> spawned() { return spawned; }
        public List<GeneratedEntitySnapshot> updated() { return updated; }
        public List<GeneratedEntityTarget> removed() { return removed; }
        public boolean isEmpty() {
            return spawned.isEmpty() && updated.isEmpty() && removed.isEmpty();
        }
    }

    /** Immutable target resolution; only LIVE carries an action-eligible snapshot. */
    public static final class LiveResolution {
        public enum Status { LIVE, UNKNOWN, KIND_MISMATCH, DEAD }

        private final Status status;
        private final RuntimeSnapshot snapshot;

        private LiveResolution(Status status, RuntimeSnapshot snapshot) {
            this.status = Objects.requireNonNull(status, "generated entity resolution status");
            this.snapshot = snapshot;
        }

        private static LiveResolution of(Status status, RuntimeSnapshot observed) {
            return new LiveResolution(status, status == Status.LIVE ? observed : null);
        }

        public Status status() { return status; }
        public RuntimeSnapshot snapshot() { return snapshot; }
    }

    private static final class AuthorityKey {
        private final String installationIdentity;
        private final int encounterOrdinal;

        private AuthorityKey(String installationIdentity, int encounterOrdinal) {
            if (installationIdentity == null || installationIdentity.isBlank()
                    || encounterOrdinal < 0) {
                throw new IllegalStateException("invalid generated entity authority key");
            }
            this.installationIdentity = installationIdentity;
            this.encounterOrdinal = encounterOrdinal;
        }

        private static AuthorityKey of(RuntimeBinding binding) {
            return new AuthorityKey(binding.installationIdentity(), binding.encounterOrdinal());
        }

        @Override public boolean equals(Object other) {
            return other instanceof AuthorityKey key
                    && encounterOrdinal == key.encounterOrdinal
                    && installationIdentity.equals(key.installationIdentity);
        }

        @Override public int hashCode() {
            return 31 * installationIdentity.hashCode() + encounterOrdinal;
        }
    }
}
