package com.gameexpert.engine.mob;

import com.gameexpert.engine.ChestInventory;
import com.gameexpert.engine.inventory.HorseMenuContainerAccess;
import com.gameexpert.engine.inventory.PlayerInventory;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashSet;
import java.util.Set;

/** Strict current-schema persistence for every exact stack in a horse inventory menu. */
public final class HorseMenuCodec {
    private static final int MAGIC = 0x484d4931; // HMI1
    private static final int MAX_TEXT_BYTES = 65_535;
    /** Exact durable ceiling of WorldMob.horse_inventory_data. */
    private static final int MAX_ENCODED_CHARS = 16_384;
    /** MAX_VALUE - 1 is unpublishable because it has no representable live successor. */
    private static final long TERMINAL_REVISION = Long.MAX_VALUE - 1L;
    private static final int HEADER_BYTES = Integer.BYTES * 2;
    private static final int ROW_FIXED_BYTES = Short.BYTES + Integer.BYTES * 6 + Long.BYTES;

    private HorseMenuCodec() { }

    public static String encode(Mob mob) {
        return capture(mob).payload();
    }

    public record EncodedMenu(String payload, long sharedRevision, long equipmentRevision,
            long cargoRevision) {
        public EncodedMenu {
            validateRevision(sharedRevision, "horse menu capture");
            validateRevision(equipmentRevision, "horse equipment capture");
            validateRevision(cargoRevision, "horse cargo capture");
        }

        /** Compatibility constructor for callers that only carried one legacy generation. */
        public EncodedMenu(String payload, long revision) {
            this(payload, revision, revision, revision);
        }

        /** The shared logical menu generation; local generations are never max-collapsed. */
        public long revision() {
            return sharedRevision;
        }

        public RevisionSnapshot revisions() {
            return new RevisionSnapshot(sharedRevision, equipmentRevision, cargoRevision);
        }
    }

    /** The exact shared/equipment-local/cargo-local tuple crossing a persistence boundary. */
    public record RevisionSnapshot(long sharedRevision, long equipmentRevision,
            long cargoRevision) {
        public RevisionSnapshot {
            validateRevision(sharedRevision, "horse menu expected");
            validateRevision(equipmentRevision, "horse equipment expected");
            validateRevision(cargoRevision, "horse cargo expected");
        }

        public long revision() {
            return sharedRevision;
        }
    }

    /** Captures the complete menu and exact revision tuple under the owner reservation. */
    public static EncodedMenu capture(Mob mob) {
        if (mob == null || mob.horseEquipment() == null) return new EncodedMenu(null, 0L);
        HorseMenuContainerAccess.MenuSnapshot snapshot =
                new HorseMenuContainerAccess(mob).snapshot();
        validatePublishable(snapshot.revisions());
        validateUniqueShulkerIds(snapshot.stacks());
        return new EncodedMenu(encodeSlots(snapshot.stacks()),
                snapshot.revisions().sharedRevision(), snapshot.revisions().equipmentRevision(),
                snapshot.revisions().cargoRevision());
    }

    /** Encodes a complete, generation-safe detached or live menu snapshot. */
    public static String encode(HorseMenuContainerAccess menu) {
        if (menu == null) throw new IllegalArgumentException("horse menu required");
        HorseMenuContainerAccess.MenuSnapshot snapshot = menu.snapshot();
        validatePublishable(snapshot.revisions());
        validateUniqueShulkerIds(snapshot.stacks());
        return encodeSlots(snapshot.stacks());
    }

    private static String encodeSlots(PlayerInventory.StackSnapshot[] slots) {
        int rawLength = encodedRawLength(slots);
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(rawLength);
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeInt(MAGIC);
            out.writeInt(slots.length);
            for (PlayerInventory.StackSnapshot stack : slots) {
                out.writeShort(stack.itemType());
                out.writeInt(stack.count());
                out.writeInt(stack.durability());
                out.writeLong(stack.enchantments());
                out.writeInt(stack.mapId());
                out.writeInt(stack.shulkerId());
                writeText(out, stack.bucketMobData());
                writeText(out, stack.itemComponentData());
            }
            out.flush();
            String encoded = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(bytes.toByteArray());
            if (encoded.length() != unpaddedBase64Length(rawLength)) {
                throw new IllegalStateException("horse inventory encoded length drift");
            }
            return encoded;
        } catch (IOException impossible) {
            throw new IllegalStateException("in-memory horse inventory encoding failed", impossible);
        }
    }

    /** Restores a capture only when its exact tuple still belongs to this restore boundary. */
    public static void restore(Mob mob, String encoded) {
        requireRestoreArguments(mob, encoded);
        HorseMenuContainerAccess.MenuSnapshot current =
                new HorseMenuContainerAccess(mob).snapshot();
        restorePrepared(mob, encoded, revisionSnapshot(current), current, false);
    }

    public static void restore(Mob mob, EncodedMenu captured) {
        if (captured == null) throw new IllegalArgumentException("horse menu capture required");
        restore(mob, captured.payload(), captured.revisions());
    }

    public static void restore(Mob mob, String encoded, long expectedSharedRevision) {
        requireRestoreArguments(mob, encoded);
        HorseMenuContainerAccess.MenuSnapshot current =
                new HorseMenuContainerAccess(mob).snapshot();
        RevisionSnapshot expected = revisionSnapshot(current);
        if (expected.sharedRevision() != expectedSharedRevision) throw staleRestoreRevision();
        restorePrepared(mob, encoded, expected, current, false);
    }

    public static void restore(Mob mob, String encoded, long expectedSharedRevision,
            long expectedEquipmentRevision, long expectedCargoRevision) {
        restore(mob, encoded, new RevisionSnapshot(expectedSharedRevision,
                expectedEquipmentRevision, expectedCargoRevision));
    }

    /**
     * Restore-only exact replay. It rejects an already bound inventory before clearing either
     * local container; live gameplay must use HorseMenuContainerAccess transactions instead.
     */
    public static void restore(Mob mob, String encoded, RevisionSnapshot expected) {
        requireRestoreArguments(mob, encoded);
        if (expected == null) throw new IllegalArgumentException("horse menu expected revision required");
        HorseMenuContainerAccess.MenuSnapshot current =
                new HorseMenuContainerAccess(mob).snapshot();
        restorePrepared(mob, encoded, expected, current, false);
    }

    /**
     * Fresh-load-only restore used by MobRuntime. It carries the authenticated tuple into the
     * newly materialized Mob and binds both local containers to their own expected revisions.
     */
    public static void restoreForLoad(Mob mob, String encoded, RevisionSnapshot expected) {
        requireRestoreArguments(mob, encoded);
        if (expected == null) throw new IllegalArgumentException("horse menu load revision required");
        HorseMenuContainerAccess.MenuSnapshot current =
                new HorseMenuContainerAccess(mob).snapshot();
        restorePrepared(mob, encoded, expected, current, true);
    }

    private static void restorePrepared(Mob mob, String encoded, RevisionSnapshot expected,
            HorseMenuContainerAccess.MenuSnapshot current, boolean freshLoad) {
        PreparedMenu prepared = decodePrepared(mob, encoded);
        installPrepared(mob, expected, current, prepared, freshLoad);
    }

    /** Validates HMI1 framing, shape and all stack identities without changing the live menu. */
    public static void validatePayload(Mob mob, String encoded) {
        requireRestoreArguments(mob, encoded);
        decodePrepared(mob, encoded);
    }

    private static void requireRestoreArguments(Mob mob, String encoded) {
        if (mob == null || mob.horseEquipment() == null || encoded == null || encoded.isBlank()) {
            throw new IllegalArgumentException("horse inventory current-schema payload required");
        }
    }

    private static PreparedMenu decodePrepared(Mob mob, String encoded) {
        if (encoded.length() > MAX_ENCODED_CHARS) {
            throw new IllegalArgumentException("horse inventory payload exceeds durable ceiling");
        }
        final byte[] bytes;
        try {
            bytes = Base64.getUrlDecoder().decode(encoded);
        } catch (IllegalArgumentException malformed) {
            throw new IllegalArgumentException("malformed horse inventory payload", malformed);
        }
        if (!Base64.getUrlEncoder().withoutPadding().encodeToString(bytes).equals(encoded)) {
            throw new IllegalArgumentException("noncanonical horse inventory payload");
        }

        HorseMenuContainerAccess liveMenu = new HorseMenuContainerAccess(mob);
        ChestInventory preparedEquipment = new ChestInventory(HorseMenuContainerAccess.CARGO_START);
        ChestInventory liveCargo = mob.horseCargo();
        ChestInventory preparedCargo = liveCargo == null ? null : new ChestInventory(liveCargo.slots());
        HorseMenuContainerAccess preparedMenu = new HorseMenuContainerAccess(mob.type,
                preparedEquipment, preparedCargo);

        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes))) {
            int slotCount = liveMenu.slotCount();
            if (in.readInt() != MAGIC || in.readInt() != slotCount) {
                throw new IllegalArgumentException("horse inventory shape mismatch");
            }
            boolean[] seen = new boolean[slotCount];
            Set<Integer> positiveShulkerIds = new HashSet<>();
            for (int row = 0; row < slotCount; row++) {
                int slot = row;
                if (seen[slot]) throw new IllegalArgumentException("duplicate horse inventory slot");
                seen[slot] = true;
                short type = in.readShort();
                int count = in.readInt();
                int durability = in.readInt();
                long enchantments = in.readLong();
                int mapId = in.readInt();
                int shulkerId = in.readInt();
                String bucket = readText(in);
                String components = readText(in);
                PlayerInventory.StackSnapshot stack = new PlayerInventory.StackSnapshot(type, count,
                        durability, enchantments, mapId, shulkerId, bucket, components);
                requireUniquePositiveShulkerId(positiveShulkerIds, stack.shulkerId());
                if (!stack.isEmpty()) {
                    preparedMenu.restoreSlot(slot, stack.itemType(), stack.count(),
                            stack.durability(), stack.enchantments(), stack.mapId(),
                            stack.shulkerId(), stack.bucketMobData(), stack.itemComponentData());
                }
            }
            for (boolean slotSeen : seen) {
                if (!slotSeen) throw new IllegalArgumentException("missing horse inventory slot");
            }
            if (in.available() != 0) throw new IllegalArgumentException("horse inventory trailing data");
        } catch (IOException malformed) {
            throw new IllegalArgumentException("malformed horse inventory payload", malformed);
        } catch (IllegalStateException malformed) {
            throw new IllegalArgumentException("malformed horse inventory payload", malformed);
        }

        ChestInventory.Snapshot equipmentSnapshot = ownedSnapshot(preparedEquipment.snapshot());
        ChestInventory.Snapshot cargoSnapshot = preparedCargo == null
                ? null : ownedSnapshot(preparedCargo.snapshot());
        PreparedMenu prepared = new PreparedMenu(
                stackSnapshots(equipmentSnapshot, cargoSnapshot, liveMenu.slotCount()));
        if (!encodeSlots(prepared.slots()).equals(encoded)) {
            throw new IllegalArgumentException("noncanonical horse inventory semantics");
        }
        return prepared;
    }

    private static void installPrepared(Mob mob, RevisionSnapshot expected,
            HorseMenuContainerAccess.MenuSnapshot expectedCurrent, PreparedMenu prepared,
            boolean freshLoad) {
        HorseMenuContainerAccess menu = new HorseMenuContainerAccess(mob);
        menu.withReservation(() -> {
            ChestInventory equipment = mob.horseEquipment();
            ChestInventory cargo = mob.horseCargo();
            if (equipment == null) throw new IllegalStateException("horse menu disappeared during restore");
            if (!sameMenuSnapshot(expectedCurrent, menu.snapshot())) {
                throw staleRestoreRevision();
            }
            if (cargo == null && expected.cargoRevision() != 0L) {
                throw new IllegalArgumentException("unattached horse has cargo revision");
            }
            if (freshLoad) {
                if (mob.horseMenuPersistenceRevision() != 0L) {
                    throw new IllegalStateException("horse menu load lifecycle is already active");
                }
                requireRestoreOpen(equipment);
                if (cargo != null) requireRestoreOpen(cargo);
            } else if (mob.horseEquipment() != equipment
                    || mob.horseCargo() != cargo
                    || mob.horseMenuPersistenceRevision() != expected.sharedRevision()
                    || equipment.persistenceRevision() != expected.equipmentRevision()
                    || revisionOf(cargo) != expected.cargoRevision()) {
                throw staleRestoreRevision();
            } else {
                // Probe both lifecycles before the first clear. This is intentionally separate
                // from clearForRestore so an equipment/cargo mixed lifecycle cannot half-clear.
                requireRestoreOpen(equipment);
                if (cargo != null) requireRestoreOpen(cargo);
            }
            if (menu.slotCount() != prepared.slotCount()) {
                throw new IllegalStateException("horse inventory shape changed during restore");
            }
            if (cargo != null) cargo.clearForRestore();
            equipment.clearForRestore();
            installSlots(equipment, cargo, prepared);
            equipment.restorePersistenceRevision(expected.equipmentRevision());
            if (cargo != null) cargo.restorePersistenceRevision(expected.cargoRevision());
            if (freshLoad) mob.restoreHorseMenuPersistenceRevision(expected.sharedRevision());
            return null;
        });
    }

    private static RevisionSnapshot revisionSnapshot(HorseMenuContainerAccess.MenuSnapshot snapshot) {
        HorseMenuContainerAccess.RevisionToken revisions = snapshot.revisions();
        return new RevisionSnapshot(revisions.sharedRevision(), revisions.equipmentRevision(),
                revisions.cargoRevision());
    }

    private static boolean sameMenuSnapshot(HorseMenuContainerAccess.MenuSnapshot expected,
            HorseMenuContainerAccess.MenuSnapshot actual) {
        return expected != null && expected.revisions().equals(actual.revisions())
                && java.util.Arrays.equals(expected.stacks(), actual.stacks());
    }

    /** Checks lifecycle without clearing or writing a slot. */
    private static void requireRestoreOpen(ChestInventory inventory) {
        try {
            inventory.restoreSlot(-1, PlayerInventory.EMPTY, 0, null);
        } catch (IllegalStateException expected) {
            String message = expected.getMessage();
            if (message != null && message.contains("슬롯 범위")) return;
            throw new IllegalStateException("horse menu restore lifecycle is mixed", expected);
        }
        throw new IllegalStateException("horse menu restore lifecycle probe unexpectedly succeeded");
    }

    private static void installSlots(ChestInventory equipment, ChestInventory cargo,
            PreparedMenu prepared) {
        for (int slot = 0; slot < prepared.slotCount(); slot++) {
            PlayerInventory.StackSnapshot stack = prepared.slot(slot);
            if (stack.isEmpty()) continue;
            ChestInventory target = slot < HorseMenuContainerAccess.CARGO_START
                    ? equipment : cargo;
            if (target == null) throw new IllegalStateException("horse cargo disappeared during restore");
            int local = slot < HorseMenuContainerAccess.CARGO_START
                    ? slot : slot - HorseMenuContainerAccess.CARGO_START;
            target.restoreSlot(local, stack.itemType(), stack.count(),
                    stack.durability() == 0 ? null : stack.durability(),
                    stack.enchantments() == 0 ? null : stack.enchantments(),
                    stack.mapId() == 0 ? null : stack.mapId(),
                    stack.shulkerId() == 0 ? null : stack.shulkerId(),
                    stack.bucketMobData(), stack.itemComponentData());
        }
    }

    private static long revisionOf(ChestInventory inventory) {
        return inventory == null ? 0L : inventory.persistenceRevision();
    }

    private static IllegalStateException staleRestoreRevision() {
        return new IllegalStateException("stale horse menu persistence revision");
    }

    private static void validateRevision(long revision, String name) {
        if (revision < 0L) throw new IllegalArgumentException("invalid " + name + " persistence revision");
        if (revision >= TERMINAL_REVISION) {
            throw new IllegalArgumentException(name + " persistence revision is exhausted");
        }
    }

    private static PlayerInventory.StackSnapshot[] stackSnapshots(
            ChestInventory.Snapshot equipment, ChestInventory.Snapshot cargo, int slotCount) {
        PlayerInventory.StackSnapshot[] slots = new PlayerInventory.StackSnapshot[slotCount];
        fillStackSnapshots(slots, 0, equipment);
        if (cargo != null) fillStackSnapshots(slots, HorseMenuContainerAccess.CARGO_START, cargo);
        for (PlayerInventory.StackSnapshot slot : slots) {
            if (slot == null) throw new IllegalArgumentException("missing prepared horse slot");
        }
        return slots;
    }

    private static void fillStackSnapshots(PlayerInventory.StackSnapshot[] destination, int offset,
            ChestInventory.Snapshot source) {
        short[] types = source.itemTypes();
        int[] counts = source.counts();
        int[] durabilities = source.durabilities();
        long[] enchantments = source.enchantments();
        int[] mapIds = source.mapIds();
        int[] shulkerIds = source.shulkerIds();
        String[] bucketMobData = source.bucketMobData();
        String[] itemComponents = source.itemComponentData();
        if (offset < 0 || offset + types.length > destination.length) {
            throw new IllegalArgumentException("prepared horse slot index out of range");
        }
        for (int local = 0; local < types.length; local++) {
            destination[offset + local] = new PlayerInventory.StackSnapshot(types[local], counts[local],
                    durabilities[local], enchantments[local], mapIds[local], shulkerIds[local],
                    bucketMobData[local], itemComponents[local]);
        }
    }

    private static ChestInventory.Snapshot ownedSnapshot(ChestInventory.Snapshot snapshot) {
        return new ChestInventory.Snapshot(snapshot.itemTypes().clone(), snapshot.counts().clone(),
                snapshot.durabilities().clone(), snapshot.enchantments().clone(),
                snapshot.mapIds().clone(), snapshot.shulkerIds().clone(),
                snapshot.bucketMobData().clone(), snapshot.itemComponentData().clone());
    }

    private static void requireUniquePositiveShulkerId(Set<Integer> seen, int shulkerId) {
        if (shulkerId > 0 && !seen.add(shulkerId)) {
            throw new IllegalArgumentException("duplicate horse inventory shulker identity");
        }
    }

    private static void validateUniqueShulkerIds(PlayerInventory.StackSnapshot[] slots) {
        Set<Integer> seen = new HashSet<>();
        for (PlayerInventory.StackSnapshot slot : slots) {
            requireUniquePositiveShulkerId(seen, slot.shulkerId());
        }
    }

    private static void validatePublishable(HorseMenuContainerAccess.RevisionToken revisions) {
        requirePublishable(revisions.sharedRevision(), "horse menu");
        requirePublishable(revisions.equipmentRevision(), "horse equipment");
        requirePublishable(revisions.cargoRevision(), "horse cargo");
    }

    private static void requirePublishable(long revision, String label) {
        if (revision < 0L || revision >= TERMINAL_REVISION) {
            throw new IllegalStateException(label + " persistence revision is exhausted");
        }
    }

    private static int encodedRawLength(PlayerInventory.StackSnapshot[] slots) {
        long rawLength = HEADER_BYTES;
        for (PlayerInventory.StackSnapshot stack : slots) {
            rawLength += ROW_FIXED_BYTES;
            rawLength += textByteLength(stack.bucketMobData());
            rawLength += textByteLength(stack.itemComponentData());
            if (unpaddedBase64Length(rawLength) > MAX_ENCODED_CHARS) {
                throw new IllegalArgumentException("horse inventory payload exceeds durable ceiling");
            }
        }
        return Math.toIntExact(rawLength);
    }

    private static int textByteLength(String value) {
        if (value == null) return 0;
        int length = value.getBytes(StandardCharsets.UTF_8).length;
        if (length > MAX_TEXT_BYTES) throw new IllegalArgumentException("horse stack text too long");
        return length;
    }

    private static long unpaddedBase64Length(long rawLength) {
        long completeGroups = rawLength / 3;
        int remainder = (int) (rawLength % 3);
        return completeGroups * 4 + (remainder == 0 ? 0 : remainder + 1);
    }

    private static void writeText(DataOutputStream out, String value) throws IOException {
        if (value == null) {
            out.writeInt(-1);
            return;
        }
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_TEXT_BYTES) throw new IllegalArgumentException("horse stack text too long");
        out.writeInt(bytes.length);
        out.write(bytes);
    }

    private static String readText(DataInputStream in) throws IOException {
        int length = in.readInt();
        if (length == -1) return null;
        if (length < 0 || length > MAX_TEXT_BYTES || length > in.available()) {
            throw new IllegalArgumentException("malformed horse stack text length");
        }
        return new String(in.readNBytes(length), StandardCharsets.UTF_8);
    }

    private record PreparedMenu(PlayerInventory.StackSnapshot[] slots) {
        private PreparedMenu {
            if (slots == null || slots.length < HorseMenuContainerAccess.CARGO_START) {
                throw new IllegalArgumentException("prepared horse menu slots required");
            }
            slots = slots.clone();
            for (PlayerInventory.StackSnapshot slot : slots) {
                if (slot == null) throw new IllegalArgumentException("prepared horse slot required");
            }
        }

        private int slotCount() { return slots.length; }
        private PlayerInventory.StackSnapshot slot(int index) {
            if (index < 0 || index >= slots.length) {
                throw new IllegalArgumentException("prepared horse slot index out of range");
            }
            return slots[index];
        }
        @Override public PlayerInventory.StackSnapshot[] slots() { return slots.clone(); }
    }

    private static boolean horseMenuType(MobType type) {
        return switch (type) {
            case HORSE, DONKEY, MULE, LLAMA, ZOMBIE_HORSE, CAMEL, SKELETON_HORSE,
                    TRADER_LLAMA -> true;
            default -> false;
        };
    }
}
