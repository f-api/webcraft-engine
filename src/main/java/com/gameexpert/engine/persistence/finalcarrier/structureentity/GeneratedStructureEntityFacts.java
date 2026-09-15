package com.gameexpert.engine.persistence.finalcarrier.structureentity;

import com.gameexpert.terrain.Blocks;
import com.gameexpert.terrain.mc.structure.Mc263VillageTemplateEntityFacts;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/** Strict typed projection of generated non-mob structure-entity carrier rows. */
public final class GeneratedStructureEntityFacts {
    private static final String ARMOR_STAND_KEY = "minecraft:armor_stand";
    private static final String CUSHION_KEY = "minecraft:cushion";
    private static final String CHEST_MINECART_KEY = "minecraft:chest_minecart";
    private static final String STRUCTURE_REASON = "minecraft:structure";
    private static final String CHUNK_GENERATION_REASON = "minecraft:chunk_generation";
    private static final String MINESHAFT_LOOT = "minecraft:chests/abandoned_mineshaft";
    private static final byte[] MINECART_PROVENANCE =
            "CME263E1".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
    private static final Pattern RESOURCE_KEY =
            Pattern.compile("[a-z0-9_.-]+:[a-z0-9/._-]+");
    private static final double HORIZONTAL_LIMIT = 30_000_000.0d;
    private static final double MIN_ENTITY_Y = Blocks.MIN_Y - 64.0d;
    private static final double MAX_ENTITY_Y = Blocks.MAX_Y + 64.0d;
    private static final int MAX_NBT_BYTES = 1_048_576;

    private GeneratedStructureEntityFacts() {}

    public enum Kind { ARMOR_STAND, CUSHION, CHEST_MINECART }
    public enum SourceKind { VILLAGE, ABANDONED_CAMP, MINESHAFT }

    public record Transform(double x, double y, double z, float yaw, float pitch,
            double velocityX, double velocityY, double velocityZ) {}

    public sealed interface EntityFacts permits ArmorStand, Cushion, ChestMinecart {
        Kind kind();
        long authoritativeEntityId();
        WorldStructureEntity.BindingStatus lifecycle();
        SourceKind sourceKind();
        Transform transform();
    }

    public record ArmorStand(long authoritativeEntityId,
            WorldStructureEntity.BindingStatus lifecycle, SourceKind sourceKind,
            Transform transform, float[] poseHead, float[] poseBody, String equipmentSlot,
            String equipmentItem, boolean showArms, boolean small, boolean noBasePlate,
            boolean invisible, boolean invulnerable, int disabledSlots, float health)
            implements EntityFacts {
        public ArmorStand {
            poseHead = pose(poseHead, "armor stand head pose");
            poseBody = pose(poseBody, "armor stand body pose");
        }
        @Override public Kind kind() { return Kind.ARMOR_STAND; }
        @Override public float[] poseHead() { return poseHead.clone(); }
        @Override public float[] poseBody() { return poseBody.clone(); }
    }

    public record Cushion(long authoritativeEntityId,
            WorldStructureEntity.BindingStatus lifecycle, SourceKind sourceKind,
            Transform transform, String color, int blockX, int blockY, int blockZ,
            boolean invulnerable) implements EntityFacts {
        @Override public Kind kind() { return Kind.CUSHION; }
    }

    public record ChestMinecart(long authoritativeEntityId,
            WorldStructureEntity.BindingStatus lifecycle, SourceKind sourceKind,
            Transform transform, String lootTable, long lootSeed, String provenance)
            implements EntityFacts {
        @Override public Kind kind() { return Kind.CHEST_MINECART; }
    }

    public sealed interface DecodeResult permits Decoded, NotOwned {}
    public record Decoded(EntityFacts facts) implements DecodeResult {
        public Decoded { Objects.requireNonNull(facts, "generated entity facts"); }
    }
    public record NotOwned(String entityKey) implements DecodeResult {
        public NotOwned { Objects.requireNonNull(entityKey, "entity key"); }
    }

    /** Decodes the three generated non-mob kinds; ordinary mob rows remain explicitly unowned. */
    public static DecodeResult decode(WorldStructureEntity row) {
        Objects.requireNonNull(row, "world structure entity");
        String key = Objects.requireNonNull(row.getEntityKey(), "entity key");
        return switch (key) {
            case ARMOR_STAND_KEY -> new Decoded(decodeArmorStand(row));
            case CUSHION_KEY -> new Decoded(decodeCushion(row));
            case CHEST_MINECART_KEY -> new Decoded(decodeMinecart(row));
            default -> new NotOwned(key);
        };
    }

    private static ArmorStand decodeArmorStand(WorldStructureEntity row) {
        Envelope envelope = envelope(row, STRUCTURE_REASON, false);
        ParsedNbt nbt = ParsedNbt.parse(row.getCanonicalPayload());
        Compound root = nbt.root();
        Compound pose = root.compound("Pose");
        int[] head = pose.floatBits("Head", 3);
        int[] body = pose.floatBits("Body", 3);
        finitePose(head, "armor stand head pose");
        finitePose(body, "armor stand body pose");
        Compound equipment = root.compound("equipment");
        if (equipment.values.size() != 1) fail("armor stand equipment cardinality");
        var entry = equipment.values.entrySet().iterator().next();
        if (!entry.getKey().equals("head") && !entry.getKey().equals("chest")) {
            fail("armor stand equipment slot");
        }
        Compound item = entry.getValue().compound("armor stand equipment");
        if (!item.names().equals(List.of("count", "id")) || item.integer("count") != 1) {
            fail("armor stand equipment payload");
        }
        String equipmentItem = item.string("id");
        resourceKey(equipmentItem, "armor stand equipment item");
        authenticateArmorTuple(head, body, entry.getKey(), equipmentItem);
        byte[] expected = encodeArmorStand(envelope.transform, head, body,
                entry.getKey(), equipmentItem);
        exactPayload(row, expected, "armor stand");
        return new ArmorStand(envelope.id, envelope.lifecycle, SourceKind.VILLAGE,
                envelope.transform, floats(head), floats(body), entry.getKey(), equipmentItem,
                false, false, false, false, false, 0, 20.0f);
    }

    private static Cushion decodeCushion(WorldStructureEntity row) {
        Envelope envelope = envelope(row, STRUCTURE_REASON, false);
        Transform t = envelope.transform;
        int blockX = floorInt(t.x, "cushion X");
        int blockY = floorInt(t.y, "cushion Y");
        int blockZ = floorInt(t.z, "cushion Z");
        if (t.x - blockX != 0.5d || t.y - blockY != 0.9375d || t.z - blockZ != 0.5d) {
            fail("cushion fractional position");
        }
        int yaw = Float.floatToRawIntBits(t.yaw);
        if (yaw != 0x00000000 && yaw != 0x42b40000
                && yaw != 0x43340000 && yaw != 0x43870000) {
            fail("cushion cardinal yaw");
        }
        if (Float.floatToRawIntBits(t.pitch) != 0 || !zeroMotion(t)) {
            fail("cushion transform");
        }
        byte[] expected = encodeCushion(t, blockX, blockY, blockZ);
        exactPayload(row, expected, "cushion");
        return new Cushion(envelope.id, envelope.lifecycle, SourceKind.ABANDONED_CAMP,
                t, "lime", blockX, blockY, blockZ, false);
    }

    private static ChestMinecart decodeMinecart(WorldStructureEntity row) {
        Envelope envelope = envelope(row, CHUNK_GENERATION_REASON, true);
        Transform t = envelope.transform;
        if (!halfCell(t.x) || !halfCell(t.y) || !halfCell(t.z)
                || Math.floor(t.y) < Blocks.MIN_Y || Math.floor(t.y) > Blocks.MAX_Y) {
            fail("chest minecart position");
        }
        if (Float.floatToRawIntBits(t.yaw) != 0 || Float.floatToRawIntBits(t.pitch) != 0
                || !zeroMotion(t)) {
            fail("chest minecart transform");
        }
        if (!MINESHAFT_LOOT.equals(row.getLootTable())) fail("chest minecart loot table");
        exactPayload(row, MINECART_PROVENANCE, "chest minecart provenance");
        return new ChestMinecart(envelope.id, envelope.lifecycle, SourceKind.MINESHAFT,
                t, row.getLootTable(), row.getLootSeed(), "CME263E1");
    }

    private static Envelope envelope(WorldStructureEntity row, String reason, boolean requiresLoot) {
        if (row.getAuthoritativeEntityId() <= 0) fail("authoritative entity ID");
        if (!reason.equals(row.getSpawnReason())) fail("generated entity spawn reason");
        if (requiresLoot == row.getLootTable().isEmpty()) fail("generated entity loot envelope");
        if (!requiresLoot && row.getLootSeed() != 0L) fail("generated entity no-loot seed");
        Transform transform = new Transform(
                Double.longBitsToDouble(row.getXBits()), Double.longBitsToDouble(row.getYBits()),
                Double.longBitsToDouble(row.getZBits()), Float.intBitsToFloat(row.getYawBits()),
                Float.intBitsToFloat(row.getPitchBits()),
                Double.longBitsToDouble(row.getVelocityXBits()),
                Double.longBitsToDouble(row.getVelocityYBits()),
                Double.longBitsToDouble(row.getVelocityZBits()));
        finiteBounded(transform);
        return new Envelope(row.getAuthoritativeEntityId(), row.bindingStatus(), transform);
    }

    private static void finiteBounded(Transform t) {
        if (!Double.isFinite(t.x) || !Double.isFinite(t.y) || !Double.isFinite(t.z)
                || !Float.isFinite(t.yaw) || !Float.isFinite(t.pitch)
                || !Double.isFinite(t.velocityX) || !Double.isFinite(t.velocityY)
                || !Double.isFinite(t.velocityZ)) fail("non-finite generated entity transform");
        if (Math.abs(t.x) > HORIZONTAL_LIMIT || Math.abs(t.z) > HORIZONTAL_LIMIT
                || t.y < MIN_ENTITY_Y || t.y > MAX_ENTITY_Y
                || t.pitch < -90.0f || t.pitch > 90.0f) {
            fail("generated entity transform bounds");
        }
    }

    private static boolean zeroMotion(Transform t) {
        return Double.doubleToRawLongBits(t.velocityX) == 0L
                && Double.doubleToRawLongBits(t.velocityY) == 0L
                && Double.doubleToRawLongBits(t.velocityZ) == 0L;
    }

    private static boolean halfCell(double value) {
        return value - Math.floor(value) == 0.5d;
    }

    private static int floorInt(double value, String label) {
        double floor = Math.floor(value);
        if (floor < Integer.MIN_VALUE || floor > Integer.MAX_VALUE) fail(label + " block position");
        return (int) floor;
    }

    private static float[] pose(float[] value, String label) {
        Objects.requireNonNull(value, label);
        if (value.length != 3) throw new IllegalArgumentException(label + " cardinality");
        return value.clone();
    }

    private static float[] floats(int[] bits) {
        return new float[] {Float.intBitsToFloat(bits[0]), Float.intBitsToFloat(bits[1]),
                Float.intBitsToFloat(bits[2])};
    }

    private static void finitePose(int[] bits, String label) {
        for (int value : bits) if (!Float.isFinite(Float.intBitsToFloat(value))) fail(label);
    }

    private static void authenticateArmorTuple(int[] head, int[] body, String slot, String item) {
        boolean authenticated = Mc263VillageTemplateEntityFacts.pinned()
                .entriesInOfficialOrder().stream()
                .filter(entry -> ARMOR_STAND_KEY.equals(entry.entityKey()))
                .map(Mc263VillageTemplateEntityFacts.Entry::facts)
                .anyMatch(facts -> Arrays.equals(head, facts.poseHeadBits())
                        && Arrays.equals(body, facts.poseBodyBits())
                        && slot.equals(facts.armorStandEquipmentSlot())
                        && item.equals(facts.armorStandEquipmentItem()));
        if (!authenticated) fail("unauthenticated armor stand template facts");
    }

    private static void exactPayload(WorldStructureEntity row, byte[] expected, String label) {
        if (!Arrays.equals(row.getCanonicalPayload(), expected)) fail(label + " payload drift");
    }

    private static void resourceKey(String value, String label) {
        if (!RESOURCE_KEY.matcher(value).matches()) fail(label);
    }

    private static byte[] encodeArmorStand(Transform t, int[] head, int[] body,
            String slot, String item) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            root(out); compound(out, "Brain"); compound(out, "memories"); end(out); end(out);
            tagByte(out, "Invulnerable", 0); tagByte(out, "FallFlying", 0);
            tagByte(out, "ShowArms", 0); tagInt(out, "PortalCooldown", 0);
            tagFloat(out, "AbsorptionAmount", 0); tagInt(out, "DisabledSlots", 0);
            tagShort(out, "DeathTime", 0); compound(out, "Pose");
            floatList(out, "Head", head); floatList(out, "Body", body); end(out);
            tagString(out, "id", ARMOR_STAND_KEY); tagByte(out, "Invisible", 0);
            doubleList(out, "Motion", t.velocityX, t.velocityY, t.velocityZ);
            tagByte(out, "Small", 0); tagFloat(out, "Health", 0x41a00000);
            compound(out, "equipment"); compound(out, slot); tagInt(out, "count", 1);
            tagString(out, "id", item); end(out); end(out);
            tagDouble(out, "fall_distance", 0); tagShort(out, "Air", 300);
            tagByte(out, "OnGround", 1);
            floatList(out, "Rotation", new int[] {Float.floatToRawIntBits(t.yaw),
                    Float.floatToRawIntBits(t.pitch)});
            tagInt(out, "current_impulse_context_reset_grace_time", 0);
            doubleList(out, "Pos", t.x, t.y, t.z); tagShort(out, "Fire", -1);
            tagByte(out, "NoBasePlate", 0); list(out, "attributes", 10, 5);
            attribute(out, "minecraft:movement_speed", 0x3fe6666660000000L);
            attribute(out, "minecraft:armor_toughness", 0L);
            attribute(out, "minecraft:max_health", 0x4034000000000000L);
            attribute(out, "minecraft:armor", 0L);
            attribute(out, "minecraft:knockback_resistance", 0L);
            tagShort(out, "HurtTime", 0); end(out);
            return bytes.toByteArray();
        } catch (IOException impossible) {
            throw new AssertionError(impossible);
        }
    }

    private static byte[] encodeCushion(Transform t, int x, int y, int z) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            root(out); doubleList(out, "Motion", 0d, 0d, 0d);
            tagString(out, "color", "lime"); intArray(out, "block_pos", x, y, z);
            tagByte(out, "Invulnerable", 0); tagDouble(out, "fall_distance", 0);
            tagShort(out, "Air", 300); tagByte(out, "OnGround", 0);
            tagInt(out, "PortalCooldown", 0);
            floatList(out, "Rotation", new int[] {Float.floatToRawIntBits(t.yaw), 0});
            doubleList(out, "Pos", t.x, t.y, t.z); tagShort(out, "Fire", 0);
            tagString(out, "id", CUSHION_KEY); end(out);
            return bytes.toByteArray();
        } catch (IOException impossible) {
            throw new AssertionError(impossible);
        }
    }

    private static void root(DataOutputStream out) throws IOException { out.writeByte(10); out.writeUTF(""); }
    private static void end(DataOutputStream out) throws IOException { out.writeByte(0); }
    private static void header(DataOutputStream out, int type, String name) throws IOException { out.writeByte(type); out.writeUTF(name); }
    private static void compound(DataOutputStream out, String name) throws IOException { header(out, 10, name); }
    private static void list(DataOutputStream out, String name, int type, int count) throws IOException { header(out, 9, name); out.writeByte(type); out.writeInt(count); }
    private static void tagByte(DataOutputStream out, String name, int value) throws IOException { header(out, 1, name); out.writeByte(value); }
    private static void tagShort(DataOutputStream out, String name, int value) throws IOException { header(out, 2, name); out.writeShort(value); }
    private static void tagInt(DataOutputStream out, String name, int value) throws IOException { header(out, 3, name); out.writeInt(value); }
    private static void tagFloat(DataOutputStream out, String name, int bits) throws IOException { header(out, 5, name); out.writeInt(bits); }
    private static void tagDouble(DataOutputStream out, String name, long bits) throws IOException { header(out, 6, name); out.writeLong(bits); }
    private static void tagString(DataOutputStream out, String name, String value) throws IOException { header(out, 8, name); out.writeUTF(value); }
    private static void floatList(DataOutputStream out, String name, int[] bits) throws IOException { list(out, name, 5, bits.length); for (int value : bits) out.writeInt(value); }
    private static void doubleList(DataOutputStream out, String name, double... values) throws IOException { list(out, name, 6, values.length); for (double value : values) out.writeLong(Double.doubleToRawLongBits(value)); }
    private static void intArray(DataOutputStream out, String name, int... values) throws IOException { header(out, 11, name); out.writeInt(values.length); for (int value : values) out.writeInt(value); }
    private static void attribute(DataOutputStream out, String id, long base) throws IOException { tagString(out, "id", id); tagDouble(out, "base", base); end(out); }

    private record Envelope(long id, WorldStructureEntity.BindingStatus lifecycle,
            Transform transform) {}

    private record Node(int type, Object value) {
        Compound compound(String label) {
            if (type != 10) fail(label + " must be compound");
            return (Compound) value;
        }
    }

    private static final class Compound {
        private final LinkedHashMap<String, Node> values;
        private Compound(LinkedHashMap<String, Node> values) { this.values = values; }
        private List<String> names() { return List.copyOf(values.keySet()); }
        private Node require(String name, int type) {
            Node node = values.get(name);
            if (node == null || node.type != type) fail("NBT field " + name);
            return node;
        }
        private Compound compound(String name) { return require(name, 10).compound(name); }
        private int integer(String name) { return (int) require(name, 3).value; }
        private String string(String name) { return (String) require(name, 8).value; }
        private int[] floatBits(String name, int count) {
            ListValue list = (ListValue) require(name, 9).value;
            if (list.elementType != 5 || list.values.size() != count) fail(name + " float list");
            int[] result = new int[count];
            for (int i = 0; i < count; i++) result[i] = (int) list.values.get(i).value;
            return result;
        }
    }

    private record ListValue(int elementType, List<Node> values) {}

    private record ParsedNbt(Compound root) {
        private static ParsedNbt parse(byte[] raw) {
            Objects.requireNonNull(raw, "generated entity NBT");
            if (raw.length == 0 || raw.length > MAX_NBT_BYTES) fail("generated entity NBT length");
            try {
                DataInputStream in = new DataInputStream(new ByteArrayInputStream(raw));
                if (in.readUnsignedByte() != 10 || !in.readUTF().isEmpty()) fail("NBT root");
                Reader reader = new Reader(in);
                Node root = reader.payload(10, 0);
                if (in.read() != -1) fail("NBT trailing bytes");
                return new ParsedNbt(root.compound("root"));
            } catch (IOException error) {
                throw new IllegalArgumentException("invalid generated entity NBT", error);
            }
        }
    }

    private static final class Reader {
        private final DataInputStream in;
        private int values;
        private Reader(DataInputStream in) { this.in = in; }
        private Node payload(int type, int depth) throws IOException {
            if (depth > 64 || ++values > 65_536) fail("NBT budget");
            return switch (type) {
                case 1 -> new Node(type, in.readByte());
                case 2 -> new Node(type, in.readShort());
                case 3 -> new Node(type, in.readInt());
                case 4 -> new Node(type, in.readLong());
                case 5 -> new Node(type, in.readInt());
                case 6 -> new Node(type, in.readLong());
                case 7 -> { int n = count(); yield new Node(type, in.readNBytes(n)); }
                case 8 -> new Node(type, in.readUTF());
                case 9 -> {
                    int element = in.readUnsignedByte(); int n = count();
                    if (element == 0 && n != 0) fail("NBT list type");
                    ArrayList<Node> list = new ArrayList<>(n);
                    for (int i = 0; i < n; i++) list.add(payload(element, depth + 1));
                    yield new Node(type, new ListValue(element, List.copyOf(list)));
                }
                case 10 -> {
                    LinkedHashMap<String, Node> map = new LinkedHashMap<>();
                    while (true) {
                        int child = in.readUnsignedByte(); if (child == 0) break;
                        String name = in.readUTF();
                        if (map.putIfAbsent(name, payload(child, depth + 1)) != null) {
                            fail("duplicate NBT field " + name);
                        }
                    }
                    yield new Node(type, new Compound(map));
                }
                case 11 -> { int n = count(); int[] a = new int[n]; for (int i = 0; i < n; i++) a[i] = in.readInt(); yield new Node(type, a); }
                case 12 -> { int n = count(); long[] a = new long[n]; for (int i = 0; i < n; i++) a[i] = in.readLong(); yield new Node(type, a); }
                default -> throw new IllegalArgumentException("unsupported NBT type " + type);
            };
        }
        private int count() throws IOException {
            int count = in.readInt();
            if (count < 0 || count > 65_536 || values > 65_536 - count) fail("NBT count");
            return count;
        }
    }

    private static void fail(String reason) {
        throw new IllegalArgumentException("invalid generated structure entity: " + reason);
    }
}
