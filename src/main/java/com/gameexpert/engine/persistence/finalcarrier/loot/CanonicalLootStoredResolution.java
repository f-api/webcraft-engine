package com.gameexpert.engine.persistence.finalcarrier.loot;

import com.gameexpert.terrain.mc.loot.Mc263ContainerLootResolver;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Immutable current-schema fixed-slot result with a canonical component-complete binary codec. */
public final class CanonicalLootStoredResolution {
    private static final byte[] MAGIC = "MCF263LOOTR1".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] MAGIC_V2 = "MCF263LOOTR2".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] FINGERPRINT_DOMAIN_V2 = "MCF263/LOOT/RESULT/v2".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] FINGERPRINT_DOMAIN =
            "MCF263/LOOT/RESULT/v1".getBytes(StandardCharsets.US_ASCII);

    private final int schemaVersion;
    private final List<Slot> slots;
    private final Mc263ContainerLootResolver.Continuation.Kind continuationKind;
    private final long continuationFirst;
    private final long continuationSecond;

    private CanonicalLootStoredResolution(List<Slot> slots,
            Mc263ContainerLootResolver.Continuation.Kind continuationKind,
            long continuationFirst, long continuationSecond) {
        this(slots, continuationKind, continuationFirst, continuationSecond, 1);
    }

    private CanonicalLootStoredResolution(List<Slot> slots,
            Mc263ContainerLootResolver.Continuation.Kind continuationKind,
            long continuationFirst, long continuationSecond, int schemaVersion) {
        if (schemaVersion != 1 && schemaVersion != 2) throw invalid();
        this.schemaVersion = schemaVersion;
        if (!CanonicalLootContainerKind.supportsSlots(slots.size())) throw invalid();
        this.slots = Collections.unmodifiableList(new ArrayList<>(slots));
        this.continuationKind = Objects.requireNonNull(continuationKind, "continuation kind");
        this.continuationFirst = continuationFirst;
        this.continuationSecond = continuationSecond;
    }

    public static CanonicalLootStoredResolution from(
            Mc263ContainerLootResolver.Resolution resolution) {
        return from(resolution, 1);
    }

    /** New producer results use their own marker; retained V1 bytes and bounds remain fixed. */
    public static CanonicalLootStoredResolution fromV2(Mc263ContainerLootResolver.Resolution resolution) {
        return from(resolution, 2);
    }

    private static CanonicalLootStoredResolution from(Mc263ContainerLootResolver.Resolution resolution, int schemaVersion) {
        Objects.requireNonNull(resolution, "loot resolution");
        ArrayList<Slot> slots = new ArrayList<>(resolution.slots().size());
        for (Mc263ContainerLootResolver.LootStack stack : resolution.slots()) {
            slots.add(stack == null ? null : Slot.from(stack, schemaVersion));
        }
        Mc263ContainerLootResolver.Continuation continuation = resolution.continuation();
        return continuation.kind() == Mc263ContainerLootResolver.Continuation.Kind.LEGACY_48
                ? new CanonicalLootStoredResolution(slots, continuation.kind(),
                        continuation.legacy48State(), 0L, schemaVersion)
                : new CanonicalLootStoredResolution(slots, continuation.kind(),
                        continuation.xoroshiroSeedLo(), continuation.xoroshiroSeedHi(), schemaVersion);
    }

    public int schemaVersion() { return schemaVersion; }

    public List<Slot> slots() {
        return slots;
    }

    public Mc263ContainerLootResolver.Continuation.Kind continuationKind() {
        return continuationKind;
    }

    public long continuationFirst() {
        return continuationFirst;
    }

    public long continuationSecond() {
        return continuationSecond;
    }

    public byte[] encode() {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(bytes);
            output.write(schemaVersion == 1 ? MAGIC : MAGIC_V2);
            output.writeInt(slots.size());
            for (Slot slot : slots) {
                output.writeByte(slot == null ? 0 : 1);
                if (slot == null) continue;
                writeText(output, slot.itemKey);
                output.writeInt(slot.count);
                output.writeInt(slot.maximumStackSize);
                output.writeInt(slot.components.size());
                for (Map.Entry<String, Component> entry : slot.components.entrySet()) {
                    writeText(output, entry.getKey());
                    Component component = entry.getValue();
                    output.writeByte(component.kind.wireOrdinal);
                    switch (component.kind) {
                        case STORED_ENCHANTMENTS, ENCHANTMENTS -> {
                            output.writeInt(component.enchantments.size());
                            for (Map.Entry<String, Integer> enchantment
                                    : component.enchantments.entrySet()) {
                                writeText(output, enchantment.getKey());
                                output.writeInt(enchantment.getValue());
                            }
                        }
                        case POTION_CONTENTS -> writeText(output, component.potionKey);
                        case ITEM_NAME -> writeText(output, component.translationKey);
                        case MAP_DECORATIONS -> {
                            output.writeInt(component.decorations.size());
                            for (Map.Entry<String, Decoration> decoration
                                    : component.decorations.entrySet()) {
                                writeText(output, decoration.getKey());
                                writeText(output, decoration.getValue().type);
                                output.writeDouble(decoration.getValue().x);
                                output.writeDouble(decoration.getValue().z);
                                output.writeDouble(decoration.getValue().rotation);
                            }
                        }
                        case MAP_ID -> output.writeInt(component.mapId);
                        case PENDING_MAP_ID -> {
                            writeText(output, component.destination);
                            writeText(output, component.targetReceipt);
                        }
                        case DAMAGE -> output.writeInt(component.damage);
                        case SUSPICIOUS_STEW_EFFECTS -> {
                            output.writeInt(component.stewEffects.size());
                            for (StewEffect effect : component.stewEffects) {
                                writeText(output, effect.effectKey());
                                output.writeInt(effect.duration());
                            }
                        }
                        case INSTRUMENT -> writeText(output, component.instrumentKey);
                        case OMINOUS_BOTTLE_AMPLIFIER -> output.writeInt(component.amplifier);
                    }
                }
            }
            output.writeByte(continuationKind.ordinal());
            output.writeLong(continuationFirst);
            output.writeLong(continuationSecond);
            output.flush();
            return bytes.toByteArray();
        } catch (IOException impossible) {
            throw new AssertionError(impossible);
        }
    }

    public static CanonicalLootStoredResolution decode(byte[] payload) {
        Objects.requireNonNull(payload, "resolved loot payload");
        try {
            DataInputStream input = new DataInputStream(new ByteArrayInputStream(payload));
            byte[] magic = input.readNBytes(MAGIC.length);
            int schemaVersion = MessageDigest.isEqual(magic, MAGIC) ? 1
                    : MessageDigest.isEqual(magic, MAGIC_V2) ? 2 : 0;
            if (schemaVersion == 0) throw invalid();
            int size = input.readInt();
            if (!CanonicalLootContainerKind.supportsSlots(size)) throw invalid();
            ArrayList<Slot> slots = new ArrayList<>(size);
            for (int index = 0; index < size; index++) {
                if (!readBoolean(input)) {
                    slots.add(null);
                    continue;
                }
                String itemKey = readText(input);
                int count = input.readInt();
                int maximumStackSize = input.readInt();
                int componentCount = bounded(input.readInt(), 0, 16);
                LinkedHashMap<String, Component> components = new LinkedHashMap<>();
                for (int componentIndex = 0; componentIndex < componentCount; componentIndex++) {
                    String key = readText(input);
                    int ordinal = input.readUnsignedByte();
                    ComponentKind kind = ComponentKind.fromWireOrdinal(ordinal);
                    Component component;
                    if (kind == ComponentKind.STORED_ENCHANTMENTS
                            || kind == ComponentKind.ENCHANTMENTS) {
                        int countEntries = bounded(input.readInt(), 1, 128);
                        LinkedHashMap<String, Integer> enchantments = new LinkedHashMap<>();
                        for (int entry = 0; entry < countEntries; entry++) {
                            String enchantment = readText(input);
                            int level = input.readInt();
                            if (level <= 0 || enchantments.put(enchantment, level) != null) {
                                throw invalid();
                            }
                        }
                        component = Component.enchantments(kind, enchantments);
                    } else if (kind == ComponentKind.POTION_CONTENTS) {
                        component = Component.potion(readText(input));
                    } else if (kind == ComponentKind.ITEM_NAME) {
                        component = Component.itemName(readText(input));
                    } else if (kind == ComponentKind.MAP_DECORATIONS) {
                        int decorationCount = bounded(input.readInt(), 1, 128);
                        LinkedHashMap<String, Decoration> decorations = new LinkedHashMap<>();
                        for (int entry = 0; entry < decorationCount; entry++) {
                            String decorationKey = readText(input);
                            Decoration decoration = new Decoration(readText(input),
                                    input.readDouble(), input.readDouble(), input.readDouble());
                            if (decorations.put(decorationKey, decoration) != null) throw invalid();
                        }
                        component = Component.decorations(decorations);
                    } else if (kind == ComponentKind.MAP_ID) {
                        component = Component.mapId(input.readInt());
                    } else if (kind == ComponentKind.PENDING_MAP_ID) {
                        component = Component.pendingMapId(readText(input), readText(input));
                    } else if (kind == ComponentKind.DAMAGE) {
                        component = Component.damage(input.readInt());
                    } else if (kind == ComponentKind.SUSPICIOUS_STEW_EFFECTS) {
                        int effectCount = bounded(input.readInt(), 1, 128);
                        ArrayList<StewEffect> effects = new ArrayList<>(effectCount);
                        for (int entry = 0; entry < effectCount; entry++) {
                            effects.add(new StewEffect(readText(input), input.readInt()));
                        }
                        component = Component.stewEffects(effects);
                    } else if (kind == ComponentKind.INSTRUMENT) {
                        component = Component.instrument(readText(input));
                    } else if (kind == ComponentKind.OMINOUS_BOTTLE_AMPLIFIER) {
                        component = Component.ominousBottleAmplifier(input.readInt());
                    } else {
                        throw invalid();
                    }
                    if (components.put(key, component) != null) throw invalid();
                }
                slots.add(new Slot(itemKey, count, maximumStackSize, components, schemaVersion));
            }
            int continuationOrdinal = input.readUnsignedByte();
            if (continuationOrdinal
                    >= Mc263ContainerLootResolver.Continuation.Kind.values().length) {
                throw invalid();
            }
            var continuationKind = Mc263ContainerLootResolver.Continuation.Kind.values()[
                    continuationOrdinal];
            long first = input.readLong();
            long second = input.readLong();
            if (continuationKind == Mc263ContainerLootResolver.Continuation.Kind.LEGACY_48
                    && (first < 0L || first >= (1L << 48) || second != 0L)) throw invalid();
            if (input.read() != -1) throw invalid();
            return new CanonicalLootStoredResolution(slots, continuationKind, first, second, schemaVersion);
        } catch (IOException | IllegalArgumentException invalid) {
            throw new IllegalStateException("malformed persisted canonical LOOT result", invalid);
        }
    }

    public String fingerprint(String definitionFingerprint) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(schemaVersion == 1 ? FINGERPRINT_DOMAIN : FINGERPRINT_DOMAIN_V2);
            writeDigestText(digest, definitionFingerprint);
            digest.update(encode());
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }

    /** Declaration-ordered authenticated placeholders selected by the loot resolver. */
    public List<PendingMapReference> pendingMapReferences() {
        List<PendingMapReference> pending = new ArrayList<>();
        for (int slotIndex = 0; slotIndex < slots.size(); slotIndex++) {
            Slot slot = slots.get(slotIndex);
            if (slot == null) continue;
            for (Map.Entry<String, Component> entry : slot.components.entrySet()) {
                Component component = entry.getValue();
                if (component.kind == ComponentKind.PENDING_MAP_ID) {
                    pending.add(new PendingMapReference(slotIndex, entry.getKey(),
                            component.destination, component.targetReceipt));
                }
            }
        }
        return List.copyOf(pending);
    }

    public boolean hasNumericMapId() {
        for (Slot slot : slots) {
            if (slot == null) continue;
            for (Component component : slot.components.values()) {
                if (component.kind == ComponentKind.MAP_ID) return true;
            }
        }
        return false;
    }

    /** Substitutes positive durable IDs without changing RNG continuation or any other component. */
    public CanonicalLootStoredResolution materializePendingMapIds(List<Integer> mapIds) {
        Objects.requireNonNull(mapIds, "materialized map IDs");
        List<PendingMapReference> pending = pendingMapReferences();
        if (pending.size() != mapIds.size()) {
            throw new IllegalArgumentException("materialized map ID count differs from placeholders");
        }
        ArrayList<Slot> materialized = new ArrayList<>(slots.size());
        int nextId = 0;
        for (Slot slot : slots) {
            if (slot == null) {
                materialized.add(null);
                continue;
            }
            LinkedHashMap<String, Component> components = new LinkedHashMap<>();
            for (Map.Entry<String, Component> entry : slot.components.entrySet()) {
                Component component = entry.getValue();
                if (component.kind == ComponentKind.PENDING_MAP_ID) {
                    Integer mapId = mapIds.get(nextId++);
                    if (mapId == null || mapId <= 0) {
                        throw new IllegalArgumentException("materialized map ID must be positive");
                    }
                    component = Component.mapId(mapId);
                }
                components.put(entry.getKey(), component);
            }
            materialized.add(new Slot(slot.itemKey, slot.count, slot.maximumStackSize, components, schemaVersion));
        }
        return new CanonicalLootStoredResolution(materialized, continuationKind,
                continuationFirst, continuationSecond, schemaVersion);
    }

    /** Reconstructs the authenticated pre-allocation bytes solely for receipt replay checking. */
    CanonicalLootStoredResolution restorePendingMapIds(List<PendingMapReference> references) {
        Objects.requireNonNull(references, "pending map references");
        ArrayList<Slot> restored = new ArrayList<>(slots.size());
        int referenceIndex = 0;
        for (int slotIndex = 0; slotIndex < slots.size(); slotIndex++) {
            Slot slot = slots.get(slotIndex);
            if (slot == null) {
                restored.add(null);
                continue;
            }
            LinkedHashMap<String, Component> components = new LinkedHashMap<>(slot.components);
            while (referenceIndex < references.size()
                    && references.get(referenceIndex).slotIndex() == slotIndex) {
                PendingMapReference reference = references.get(referenceIndex++);
                Component existing = components.get(reference.componentKey());
                if (existing == null || existing.kind != ComponentKind.MAP_ID
                        || existing.mapId == null || existing.mapId <= 0) {
                    throw new IllegalStateException(
                            "materialization receipt does not identify a stored map component");
                }
                components.put(reference.componentKey(), Component.pendingMapId(
                        reference.destination(), reference.targetReceipt()));
            }
            restored.add(new Slot(slot.itemKey, slot.count, slot.maximumStackSize, components, schemaVersion));
        }
        if (referenceIndex != references.size()) {
            throw new IllegalStateException("materialization receipt has an invalid slot order");
        }
        return new CanonicalLootStoredResolution(restored, continuationKind,
                continuationFirst, continuationSecond, schemaVersion);
    }

    public record PendingMapReference(
            int slotIndex, String componentKey, String destination, String targetReceipt) {
        public PendingMapReference {
            if (slotIndex < 0 || componentKey == null || componentKey.isBlank()
                    || destination == null || destination.isBlank()
                    || !isSha256(targetReceipt)) {
                throw invalid();
            }
        }
    }

    private static void writeText(DataOutputStream output, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length == 0 || bytes.length > 65_535) throw invalid();
        output.writeShort(bytes.length);
        output.write(bytes);
    }

    private static String readText(DataInputStream input) throws IOException {
        int length = input.readUnsignedShort();
        if (length == 0) throw invalid();
        byte[] bytes = input.readNBytes(length);
        if (bytes.length != length) throw invalid();
        try {
            CharBuffer decoded = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes));
            return decoded.toString();
        } catch (CharacterCodingException malformed) {
            throw invalid();
        }
    }

    private static boolean readBoolean(DataInputStream input) throws IOException {
        int value = input.readUnsignedByte();
        if (value != 0 && value != 1) throw invalid();
        return value == 1;
    }

    private static int bounded(int value, int minimum, int maximum) {
        if (value < minimum || value > maximum) throw invalid();
        return value;
    }

    private static void writeDigestText(MessageDigest digest, String value) {
        byte[] bytes = Objects.requireNonNull(value, "definition fingerprint")
                .getBytes(StandardCharsets.US_ASCII);
        digest.update((byte) (bytes.length >>> 8));
        digest.update((byte) bytes.length);
        digest.update(bytes);
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("invalid canonical LOOT result payload");
    }

    private static boolean isSha256(String value) {
        if (value == null || value.length() != 64 || value.equals("0".repeat(64))) return false;
        for (int index = 0; index < value.length(); index++) {
            char c = value.charAt(index);
            if (!(c >= '0' && c <= '9') && !(c >= 'a' && c <= 'f')) return false;
        }
        return true;
    }

    private static boolean isMapItem(String itemKey) {
        return "minecraft:map".equals(itemKey) || switch (itemKey) {
            case "minecraft:abandoned_campsite_map", "minecraft:ancient_city_map",
                    "minecraft:trial_explorer_map", "minecraft:mineshaft_map",
                    "minecraft:desert_pyramid_map", "minecraft:jungle_explorer_map",
                    "minecraft:warm_ocean_ruins_map", "minecraft:woodland_explorer_map",
                    "minecraft:buried_treasure_map" -> true;
            default -> false;
        };
    }

    private static void requireMinecraftKey(String value) {
        if (value == null || !value.matches("minecraft:[a-z0-9_./-]+")) throw invalid();
    }

    public static final class Slot {
        private final String itemKey;
        private final int count;
        private final int maximumStackSize;
        private final Map<String, Component> components;

        private Slot(String itemKey, int count, int maximumStackSize,
                Map<String, Component> components) {
            this(itemKey, count, maximumStackSize, components, 1);
        }

        private Slot(String itemKey, int count, int maximumStackSize,
                Map<String, Component> components, int schemaVersion) {
            if (itemKey == null || !itemKey.startsWith("minecraft:")
                    || maximumStackSize != (schemaVersion == 1 ? resolverMaximumStackSize(itemKey)
                            : CanonicalLootResultV2Items.maximumStackSize(itemKey))
                    || count <= 0 || count > maximumStackSize) {
                throw invalid();
            }
            this.itemKey = itemKey;
            this.count = count;
            this.maximumStackSize = maximumStackSize;
            this.components = Collections.unmodifiableMap(new LinkedHashMap<>(components));
            for (Map.Entry<String, Component> entry : this.components.entrySet()) {
                requireMinecraftKey(entry.getKey());
                Component component = Objects.requireNonNull(entry.getValue(),
                        "canonical LOOT component");
                if (!component.kind.componentKey.equals(entry.getKey())) throw invalid();
                if (!component.isCompatibleWith(itemKey, schemaVersion)) throw invalid();
                if ((component.kind == ComponentKind.MAP_ID
                                || component.kind == ComponentKind.PENDING_MAP_ID)
                        && (!"minecraft:map_id".equals(entry.getKey())
                                || !(schemaVersion == 1 ? isMapItem(itemKey) : CanonicalLootResultV2Items.isMapItem(itemKey)) || count != 1)) {
                    throw invalid();
                }
            }
        }

        private static Slot from(Mc263ContainerLootResolver.LootStack stack, int schemaVersion) {
            LinkedHashMap<String, Component> components = new LinkedHashMap<>();
            for (Map.Entry<String, Mc263ContainerLootResolver.ComponentValue> entry
                    : stack.components().entrySet()) {
                Mc263ContainerLootResolver.ComponentValue value = entry.getValue();
                Component component;
                if (value instanceof Mc263ContainerLootResolver.StoredEnchantments enchanted) {
                    component = Component.enchantments(
                            ComponentKind.STORED_ENCHANTMENTS, enchanted.levels());
                } else if (value instanceof Mc263ContainerLootResolver.Enchantments enchanted) {
                    component = Component.enchantments(
                            ComponentKind.ENCHANTMENTS, enchanted.levels());
                } else if (value instanceof Mc263ContainerLootResolver.PotionContents potion) {
                    component = Component.potion(potion.potionKey());
                } else if (value instanceof Mc263ContainerLootResolver.TranslatableItemName name) {
                    component = Component.itemName(name.translationKey());
                } else if (value instanceof Mc263ContainerLootResolver.ItemName name) {
                    component = Component.itemName(name.translationKey());
                } else if (value instanceof Mc263ContainerLootResolver.MapDecorations map) {
                    LinkedHashMap<String, Decoration> decorations = new LinkedHashMap<>();
                    for (Map.Entry<String, Mc263ContainerLootResolver.MapDecoration> decoration
                            : map.decorations().entrySet()) {
                        var valueDecoration = decoration.getValue();
                        decorations.put(decoration.getKey(), new Decoration(
                                valueDecoration.type(), valueDecoration.x(), valueDecoration.z(),
                                valueDecoration.rotation()));
                    }
                    component = Component.decorations(decorations);
                } else if (value instanceof Mc263ContainerLootResolver.MapId mapId) {
                    component = Component.mapId(mapId.id());
                } else if (value instanceof Mc263ContainerLootResolver.PendingMapId pending) {
                    component = Component.pendingMapId(
                            pending.destination(), pending.targetReceipt());
                } else if (value instanceof Mc263ContainerLootResolver.Damage damage) {
                    component = Component.damage(damage.value());
                } else if (value instanceof Mc263ContainerLootResolver.SuspiciousStewEffects stew) {
                    component = Component.stewEffects(stew.effects().stream()
                            .map(effect -> new StewEffect(effect.effectKey(), effect.duration()))
                            .toList());
                } else if (value instanceof Mc263ContainerLootResolver.Instrument instrument) {
                    component = Component.instrument(instrument.instrumentKey());
                } else if (value instanceof Mc263ContainerLootResolver.OminousBottleAmplifier omen) {
                    component = Component.ominousBottleAmplifier(omen.value());
                } else {
                    throw new IllegalStateException("unsupported canonical LOOT component");
                }
                components.put(entry.getKey(), component);
            }
            return new Slot(stack.itemKey(), stack.count(), stack.maximumStackSize(), components, schemaVersion);
        }

        public String itemKey() { return itemKey; }
        public int count() { return count; }
        public int maximumStackSize() { return maximumStackSize; }
        public Map<String, Component> components() { return components; }
    }

    public static final class Component {
        private final ComponentKind kind;
        private final Map<String, Integer> enchantments;
        private final String potionKey;
        private final String translationKey;
        private final Map<String, Decoration> decorations;
        private final Integer mapId;
        private final String destination;
        private final String targetReceipt;
        private final Integer damage;
        private final List<StewEffect> stewEffects;
        private final String instrumentKey;
        private final Integer amplifier;

        private Component(ComponentKind kind, Map<String, Integer> enchantments,
                String potionKey, String translationKey, Map<String, Decoration> decorations,
                Integer mapId, String destination, String targetReceipt, Integer damage,
                List<StewEffect> stewEffects, String instrumentKey, Integer amplifier) {
            this.kind = kind;
            this.enchantments = enchantments;
            this.potionKey = potionKey;
            this.translationKey = translationKey;
            this.decorations = decorations;
            this.mapId = mapId;
            this.destination = destination;
            this.targetReceipt = targetReceipt;
            this.damage = damage;
            this.stewEffects = stewEffects;
            this.instrumentKey = instrumentKey;
            this.amplifier = amplifier;
        }

        private static Component enchantments(
                ComponentKind kind, Map<String, Integer> values) {
            if (kind != ComponentKind.STORED_ENCHANTMENTS
                    && kind != ComponentKind.ENCHANTMENTS) throw invalid();
            if (values == null || values.isEmpty() || values.size() > 128) throw invalid();
            LinkedHashMap<String, Integer> checked = new LinkedHashMap<>();
            for (Map.Entry<String, Integer> entry : values.entrySet()) {
                requireMinecraftKey(entry.getKey());
                Integer level = entry.getValue();
                if (level == null || level <= 0 || checked.put(entry.getKey(), level) != null) {
                    throw invalid();
                }
            }
            return new Component(kind, Collections.unmodifiableMap(checked), null, null,
                    Map.of(), null, null, null, null, List.of(), null, null);
        }

        private static Component potion(String potionKey) {
            requireMinecraftKey(potionKey);
            return new Component(ComponentKind.POTION_CONTENTS, Map.of(),
                    potionKey, null, Map.of(), null,
                    null, null, null, List.of(), null, null);
        }

        private static Component itemName(String translationKey) {
            if (!"filled_map.buried_treasure".equals(translationKey)
                    && (translationKey == null || !translationKey.startsWith("filled_map.")
                    || !translationKey.endsWith("_abandoned_camp"))) throw invalid();
            return new Component(ComponentKind.ITEM_NAME, Map.of(), null,
                    translationKey, Map.of(), null,
                    null, null, null, List.of(), null, null);
        }

        private static Component decorations(Map<String, Decoration> values) {
            if (values == null || values.isEmpty() || values.size() > 128
                    || values.entrySet().stream().anyMatch(entry -> entry.getKey() == null
                            || entry.getKey().isEmpty() || entry.getValue() == null)) {
                throw invalid();
            }
            return new Component(ComponentKind.MAP_DECORATIONS, Map.of(), null, null,
                    Collections.unmodifiableMap(new LinkedHashMap<>(values)), null, null, null,
                    null, List.of(), null, null);
        }

        private static Component mapId(int id) {
            if (id < 0) throw invalid();
            return new Component(ComponentKind.MAP_ID, Map.of(), null, null, Map.of(), id,
                    null, null, null, List.of(), null, null);
        }

        private static Component pendingMapId(String destination, String targetReceipt) {
            if (destination == null || destination.isBlank() || !isSha256(targetReceipt)) {
                throw invalid();
            }
            return new Component(ComponentKind.PENDING_MAP_ID, Map.of(), null, null, Map.of(),
                    null, destination, targetReceipt, null, List.of(), null, null);
        }

        private static Component damage(int value) {
            if (value < 0) throw invalid();
            return new Component(ComponentKind.DAMAGE, Map.of(), null, null, Map.of(), null,
                    null, null, value, List.of(), null, null);
        }

        private static Component stewEffects(List<StewEffect> values) {
            if (values == null || values.isEmpty() || values.size() > 128) throw invalid();
            List<StewEffect> checked = List.copyOf(values);
            if (checked.stream().anyMatch(Objects::isNull)) throw invalid();
            return new Component(ComponentKind.SUSPICIOUS_STEW_EFFECTS, Map.of(), null, null,
                    Map.of(), null, null, null, null, checked, null, null);
        }

        private static Component instrument(String value) {
            requireMinecraftKey(value);
            return new Component(ComponentKind.INSTRUMENT, Map.of(), null, null, Map.of(), null,
                    null, null, null, List.of(), value, null);
        }

        private static Component ominousBottleAmplifier(int value) {
            if (value < 0 || value > 4) throw invalid();
            return new Component(ComponentKind.OMINOUS_BOTTLE_AMPLIFIER, Map.of(), null, null,
                    Map.of(), null, null, null, null, List.of(), null, value);
        }

        private boolean isCompatibleWith(String itemKey, int schemaVersion) {
            return switch (kind) {
                case ITEM_NAME -> schemaVersion == 1 ? isMapItem(itemKey) : CanonicalLootResultV2Items.isMapItem(itemKey);
                case DAMAGE -> resolverMaximumDamage(itemKey) >= 0
                        && damage <= resolverMaximumDamage(itemKey);
                case SUSPICIOUS_STEW_EFFECTS -> "minecraft:suspicious_stew".equals(itemKey);
                case INSTRUMENT -> "minecraft:goat_horn".equals(itemKey);
                case OMINOUS_BOTTLE_AMPLIFIER -> "minecraft:ominous_bottle".equals(itemKey);
                case ENCHANTMENTS -> !"minecraft:enchanted_book".equals(itemKey);
                default -> true;
            };
        }

        public String kind() { return kind.name(); }
        public Map<String, Integer> enchantments() { return enchantments; }
        public String potionKey() { return potionKey; }
        public String translationKey() { return translationKey; }
        public Map<String, Decoration> decorations() { return decorations; }
        public Integer mapId() { return mapId; }
        public String destination() { return destination; }
        public String targetReceipt() { return targetReceipt; }
        public Integer damage() { return damage; }
        public List<StewEffect> stewEffects() { return stewEffects; }
        public String instrumentKey() { return instrumentKey; }
        public Integer amplifier() { return amplifier; }
    }

    public record StewEffect(String effectKey, int duration) {
        public StewEffect {
            requireMinecraftKey(effectKey);
            if (duration < 0) throw invalid();
        }
    }

    public static final class Decoration {
        private final String type;
        private final double x;
        private final double z;
        private final double rotation;

        private Decoration(String type, double x, double z, double rotation) {
            if (type == null || !type.startsWith("minecraft:") || !Double.isFinite(x)
                    || !Double.isFinite(z) || !Double.isFinite(rotation)) throw invalid();
            this.type = type;
            this.x = x;
            this.z = z;
            this.rotation = rotation;
        }

        public String type() { return type; }
        public double x() { return x; }
        public double z() { return z; }
        public double rotation() { return rotation; }
    }

    private static int resolverMaximumStackSize(String itemKey) {
        return CanonicalLootResultV1Items.maximumStackSize(itemKey);
    }

    private static int resolverMaximumDamage(String itemKey) {
        return CanonicalLootResultV1Items.maximumDamage(itemKey);
    }

    private enum ComponentKind {
        STORED_ENCHANTMENTS(0, "minecraft:stored_enchantments"),
        POTION_CONTENTS(1, "minecraft:potion_contents"),
        ITEM_NAME(2, "minecraft:item_name"),
        MAP_DECORATIONS(3, "minecraft:map_decorations"),
        MAP_ID(4, "minecraft:map_id"),
        PENDING_MAP_ID(5, "minecraft:map_id"),
        ENCHANTMENTS(6, "minecraft:enchantments"),
        DAMAGE(7, "minecraft:damage"),
        SUSPICIOUS_STEW_EFFECTS(8, "minecraft:suspicious_stew_effects"),
        INSTRUMENT(9, "minecraft:instrument"),
        OMINOUS_BOTTLE_AMPLIFIER(10, "minecraft:ominous_bottle_amplifier");

        private final int wireOrdinal;
        private final String componentKey;

        ComponentKind(int wireOrdinal, String componentKey) {
            this.wireOrdinal = wireOrdinal;
            this.componentKey = componentKey;
        }

        private static ComponentKind fromWireOrdinal(int wireOrdinal) {
            for (ComponentKind kind : values()) {
                if (kind.wireOrdinal == wireOrdinal) return kind;
            }
            throw invalid();
        }
    }
}
