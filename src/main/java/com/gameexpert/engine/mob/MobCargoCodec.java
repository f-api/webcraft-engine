package com.gameexpert.engine.mob;

import java.util.ArrayList;
import java.util.List;

import com.gameexpert.engine.ChestInventory;
import com.gameexpert.engine.enchant.EnchantmentRules;
import com.gameexpert.engine.inventory.PlayerInventory;
import com.gameexpert.terrain.Blocks;

/**
 * 상자 장착 말 계열(Donkey·Mule·Llama) 화물 한 칸씩을 <b>한 줄 문자열</b>로 싣는 코덱.
 * JPA 행에는 화물용 자식 테이블을 두지 않고 {@code world_mob.horse_cargo} 한 열만 쓰므로,
 * 그 열의 포맷을 이 한 곳이 소유한다.
 *
 * <p><b>포맷</b> — 비어 있지 않은 칸만 <b>슬롯 오름차순</b>으로 늘어놓는다:
 *
 * <pre>{@code
 *   slot ':' itemType ':' count ':' durability ':' enchantments ':' mapId ':' shulkerId
 * }</pre>
 *
 * 빈 화물은 {@code null} 이다(열 자체가 비고, 옛 행의 null 과 같은 뜻이다).
 *
 * 일곱 필드의 값은 일반 컨테이너와 같은 정체성으로 보존하고, 이 포맷에 없는 bucket/component
 * 데이터는 투영하지 않고 거절한다.
 *
 * <p>정적판은 같은 네 값을 구조화 배열({@code StandaloneMobCargoSlot})로 들고 있고, 그 배열과
 * 이 문자열이 <b>같은 벡터</b>로 왕복한다는 사실은
 * {@code src/test/resources/mob/cargo-serialization-vectors.json} 을 두 권위 테스트가 함께
 * 읽어 강제한다(Java {@code MobCargoCodecParityTest}, 정적판 {@code StandaloneChestedHorseRules.test.ts}).
 */
public final class MobCargoCodec {

    private MobCargoCodec() {
    }

    /** 칸과 칸 사이. */
    public static final char SLOT_SEPARATOR = ';';
    /** 한 칸 안의 일곱 값 사이. */
    public static final char FIELD_SEPARATOR = ':';
    /** 한 칸이 싣는 값의 수(슬롯·종류·개수·내구도·인챈트·지도·셜커). */
    public static final int FIELDS_PER_SLOT = 7;
    /** 어떤 상자 장착 말에도 존재할 수 있는 최대 화물 행 수. */
    public static final int MAX_ENTRIES = ChestedHorseRules.CHESTED_HORSE_CARGO_SLOTS;
    /** Java long 과 TypeScript number 양쪽에서 안전하게 보존하는 인챈트 마스크 상한. */
    public static final long ENCHANTMENT_MASK_LIMIT = EnchantmentRules.ENCHANT_MASK_LIMIT;
    /** 등록 프로토콜 표가 현재 표현할 수 있는 itemType의 마지막 값. */
    public static final int ITEM_TYPE_MAX = Blocks.PROTOCOL_ID_TABLE_CAPACITY - 1;
    private static final int MAX_INT_FIELD_DIGITS = Integer.toString(Integer.MAX_VALUE).length();
    private static final int MAX_ITEM_TYPE_FIELD_DIGITS = Integer.toString(ITEM_TYPE_MAX).length();
    private static final int MAX_ENCHANTMENT_FIELD_DIGITS =
            Long.toString(ENCHANTMENT_MASK_LIMIT).length();
    private static final List<String> FIELD_NAMES = List.of(
            "slot", "itemType", "count", "durability", "enchantments", "mapId", "shulkerId");
    /** 필드·행 상한으로부터 계산한 현재 7-field 텍스트의 최대 문자 수. */
    public static final int MAX_ENCODED_CHARS = maxEncodedChars();

    /**
     * 저장된 화물 문자열을 읽을 때의 안전한 진단이다. 입력 문자열이나 입력 필드 값은
     * 포함하지 않고, 고정된 행·필드·문자 위치만 노출한다.
     */
    public static final class CargoFormatException extends IllegalArgumentException {
        private final int row;
        private final String field;
        private final int characterOffset;

        private CargoFormatException(String message, int row, String field, int characterOffset) {
            super(message);
            this.row = row;
            this.field = field;
            this.characterOffset = characterOffset;
        }

        public int row() {
            return row;
        }

        public String field() {
            return field;
        }

        public int characterOffset() {
            return characterOffset;
        }

        public int offset() {
            return characterOffset;
        }
    }

    /** 화물 한 칸. 블록 컨테이너 슬롯과 같은 전체 구성요소다. */
    public record Stack(int slot, int itemType, int count, int durability,
            long enchantments, int mapId, int shulkerId) {
        public Stack {
            if (slot < 0 || slot >= MAX_ENTRIES || !isRegisteredItemType(itemType)) {
                throw new IllegalArgumentException("invalid cargo stack");
            }
            try {
                new PlayerInventory.StackSnapshot((short) itemType, count, durability,
                        enchantments, mapId, shulkerId, null, null);
            } catch (IllegalArgumentException malformed) {
                throw new IllegalArgumentException("invalid cargo stack", malformed);
            }
        }
    }

    /** 살아 있는 화물 컨테이너를 한 줄로 싣는다. 비었거나 없으면 null 이다. */
    public static String encode(ChestInventory cargo) {
        if (cargo == null) return null;
        PlayerInventory.StackSnapshot[] captured = stackSnapshots(cargo.snapshot());
        List<Stack> stacks = new ArrayList<>();
        for (int slot = 0; slot < captured.length; slot++) {
            PlayerInventory.StackSnapshot stack = captured[slot];
            if (stack.bucketMobData() != null || stack.itemComponentData() != null) {
                throw new IllegalArgumentException(
                        "7-field cargo codec cannot project bucket or component data");
            }
            if (stack.isEmpty()) continue;
            stacks.add(new Stack(slot, Short.toUnsignedInt(stack.itemType()), stack.count(),
                    stack.durability(), stack.enchantments(), stack.mapId(),
                    stack.shulkerId()));
        }
        return encodeStacks(stacks);
    }

    /** 이미 뽑아 둔 칸 목록을 한 줄로 싣는다(파리티 벡터가 쓰는 경로). */
    public static String encodeStacks(List<Stack> stacks) {
        if (stacks == null || stacks.isEmpty()) return null;
        if (stacks.size() > MAX_ENTRIES) {
            throw new IllegalArgumentException("cargo entry count exceeds " + MAX_ENTRIES);
        }
        StringBuilder out = new StringBuilder();
        int previousSlot = -1;
        for (Stack stack : stacks) {
            if (stack == null) throw new IllegalArgumentException("cargo stack is required");
            if (stack.slot() <= previousSlot) {
                throw new IllegalArgumentException("cargo stacks must ascend by slot");
            }
            previousSlot = stack.slot();
            if (out.length() > 0) out.append(SLOT_SEPARATOR);
            out.append(stack.slot()).append(FIELD_SEPARATOR)
                    .append(stack.itemType()).append(FIELD_SEPARATOR)
                    .append(stack.count()).append(FIELD_SEPARATOR)
                    .append(stack.durability()).append(FIELD_SEPARATOR)
                    .append(stack.enchantments()).append(FIELD_SEPARATOR)
                    .append(stack.mapId()).append(FIELD_SEPARATOR)
                    .append(stack.shulkerId());
        }
        if (out.length() > MAX_ENCODED_CHARS) {
            throw new IllegalArgumentException("cargo text exceeds " + MAX_ENCODED_CHARS);
        }
        return out.toString();
    }

    /**
     * 저장된 한 줄을 칸 목록으로 되돌린다. null·빈 문자열은 빈 화물이고, 그 밖의 어긋난 입력은
     * 손상으로 보고 거절한다(슬롯 역순·중복, 값 개수 불일치, 음수, 개수 0).
     */
    public static List<Stack> decode(String encoded) {
        if (encoded == null || encoded.isEmpty()) return List.of();
        if (encoded.length() > MAX_ENCODED_CHARS) {
            throw formatError(0, "payload", MAX_ENCODED_CHARS,
                    "cargo text exceeds maximum length");
        }
        List<Stack> stacks = new ArrayList<>();
        int previousSlot = -1;
        int row = 0;
        int entryStart = 0;
        while (true) {
            int separator = encoded.indexOf(SLOT_SEPARATOR, entryStart);
            int entryEnd = separator < 0 ? encoded.length() : separator;
            if (row >= MAX_ENTRIES) {
                throw formatError(row, "row", entryStart, "cargo entry count exceeds maximum");
            }
            String entry = encoded.substring(entryStart, entryEnd);
            FieldParts parts = splitFields(entry, entryStart);
            if (parts.values().length != FIELDS_PER_SLOT) {
                throw formatError(row, "entry", entryStart, "malformed cargo entry");
            }
            String[] fields = parts.values();
            int[] offsets = parts.offsets();
            int slot = parseIntField(fields[0], row, FIELD_NAMES.get(0), offsets[0],
                    MAX_ENTRIES - 1, Integer.toString(MAX_ENTRIES - 1).length());
            int itemType = parseIntField(fields[1], row, FIELD_NAMES.get(1), offsets[1],
                    ITEM_TYPE_MAX, MAX_ITEM_TYPE_FIELD_DIGITS);
            int count = parseIntField(fields[2], row, FIELD_NAMES.get(2), offsets[2],
                    Integer.MAX_VALUE, MAX_INT_FIELD_DIGITS);
            int durability = parseIntField(fields[3], row, FIELD_NAMES.get(3), offsets[3],
                    Integer.MAX_VALUE, MAX_INT_FIELD_DIGITS);
            long enchantments = parseEnchantmentField(fields[4], row, offsets[4]);
            int mapId = parseIntField(fields[5], row, FIELD_NAMES.get(5), offsets[5],
                    Integer.MAX_VALUE, MAX_INT_FIELD_DIGITS);
            int shulkerId = parseIntField(fields[6], row, FIELD_NAMES.get(6), offsets[6],
                    Integer.MAX_VALUE, MAX_INT_FIELD_DIGITS);
            validateDecodedStack(row, offsets, slot, itemType, count, durability,
                    enchantments, mapId, shulkerId);
            Stack stack;
            try {
                stack = new Stack(slot, itemType, count, durability, enchantments, mapId, shulkerId);
            } catch (IllegalArgumentException ignored) {
                throw formatError(row, "itemType", offsets[1], "malformed cargo stack");
            }
            if (stack.slot() <= previousSlot) {
                throw formatError(row, "slot", offsets[0],
                        "cargo slots must ascend and not repeat");
            }
            previousSlot = stack.slot();
            stacks.add(stack);
            if (separator < 0) break;
            entryStart = separator + 1;
            row++;
        }
        return List.copyOf(stacks);
    }

    /**
     * 저장된 한 줄을 살아 있는 컨테이너에 되돌려 넣는다. 슬롯이 이 개체의 칸 수를 넘으면
     * 손상이다 — 힘이 줄어든 라마 행이 예전 칸을 들고 오는 경우가 여기서 걸린다.
     */
    public static void decodeInto(ChestInventory cargo, String encoded) {
        if (cargo == null) {
            throw new IllegalArgumentException("cargo row without a container");
        }
        List<Stack> stacks = decode(encoded);
        PreparedCargo prepared = prepare(cargo.slots(), stacks);
        installPrepared(cargo, prepared);
    }

    /**
     * Incoming cargo is a complete replacement payload. Build every slot in detached storage
     * first so a late-invalid row can never clear or partially rewrite the live container.
     */
    private static PreparedCargo prepare(int slotCount, List<Stack> stacks) {
        short[] itemTypes = new short[slotCount];
        int[] counts = new int[slotCount];
        int[] durabilities = new int[slotCount];
        long[] enchantments = new long[slotCount];
        int[] mapIds = new int[slotCount];
        int[] shulkerIds = new int[slotCount];
        boolean[] seen = new boolean[slotCount];

        for (Stack stack : stacks) {
            if (stack == null) {
                throw new IllegalArgumentException("cargo stack is required");
            }
            int slot = stack.slot();
            if (slot < 0 || slot >= slotCount) {
                throw new IllegalArgumentException("cargo slot " + stack.slot()
                        + " is beyond the container's " + slotCount + " slots");
            }
            if (seen[slot]) {
                throw new IllegalArgumentException("cargo slot is repeated: " + slot);
            }
            seen[slot] = true;
            if (!isRegisteredItemType(stack.itemType())) {
                throw new IllegalArgumentException("cargo item type is not registered");
            }

            PlayerInventory.StackSnapshot validated;
            try {
                validated = new PlayerInventory.StackSnapshot((short) stack.itemType(),
                        stack.count(), stack.durability(), stack.enchantments(), stack.mapId(),
                        stack.shulkerId(), null, null);
            } catch (IllegalArgumentException malformed) {
                throw new IllegalArgumentException("invalid cargo stack at slot " + slot,
                        malformed);
            }
            itemTypes[slot] = validated.itemType();
            counts[slot] = validated.count();
            durabilities[slot] = validated.durability();
            enchantments[slot] = validated.enchantments();
            mapIds[slot] = validated.mapId();
            shulkerIds[slot] = validated.shulkerId();
        }

        ChestInventory.Snapshot validatedCargo;
        try {
            validatedCargo = new ChestInventory.Snapshot(itemTypes, counts, durabilities,
                    enchantments, mapIds, shulkerIds, new String[slotCount], new String[slotCount]);
        } catch (IllegalArgumentException malformed) {
            throw new IllegalArgumentException("invalid prepared cargo payload", malformed);
        }
        return new PreparedCargo(stackSnapshots(ownedSnapshot(validatedCargo)));
    }

    /** Installs only the detached, fully validated state. */
    private static void installPrepared(ChestInventory cargo, PreparedCargo prepared) {
        synchronized (cargo) {
            if (cargo.slots() != prepared.slotCount()) {
                throw new IllegalStateException("cargo container shape changed during restore");
            }
            cargo.clearForRestore();
            for (int slot = 0; slot < prepared.slotCount(); slot++) {
                PlayerInventory.StackSnapshot stack = prepared.slot(slot);
                if (stack.isEmpty()) continue;
                cargo.restoreSlot(slot, stack.itemType(), stack.count(),
                        stack.durability() == 0 ? null : stack.durability(),
                        stack.enchantments() == 0 ? null : stack.enchantments(),
                        stack.mapId() == 0 ? null : stack.mapId(),
                        stack.shulkerId() == 0 ? null : stack.shulkerId());
            }
        }
    }

    private static ChestInventory.Snapshot ownedSnapshot(ChestInventory.Snapshot snapshot) {
        return new ChestInventory.Snapshot(snapshot.itemTypes(), snapshot.counts(),
                snapshot.durabilities(), snapshot.enchantments(), snapshot.mapIds(),
                snapshot.shulkerIds(), snapshot.bucketMobData(), snapshot.itemComponentData());
    }

    private static PlayerInventory.StackSnapshot[] stackSnapshots(
            ChestInventory.Snapshot snapshot) {
        short[] itemTypes = snapshot.itemTypes();
        int[] counts = snapshot.counts();
        int[] durabilities = snapshot.durabilities();
        long[] enchantments = snapshot.enchantments();
        int[] mapIds = snapshot.mapIds();
        int[] shulkerIds = snapshot.shulkerIds();
        String[] bucketMobData = snapshot.bucketMobData();
        String[] itemComponents = snapshot.itemComponentData();
        PlayerInventory.StackSnapshot[] slots = new PlayerInventory.StackSnapshot[itemTypes.length];
        for (int slot = 0; slot < itemTypes.length; slot++) {
            slots[slot] = new PlayerInventory.StackSnapshot(itemTypes[slot], counts[slot],
                    durabilities[slot], enchantments[slot], mapIds[slot], shulkerIds[slot],
                    bucketMobData[slot], itemComponents[slot]);
        }
        return slots;
    }

    private record PreparedCargo(PlayerInventory.StackSnapshot[] slots) {
        private PreparedCargo {
            if (slots == null || slots.length == 0) {
                throw new IllegalArgumentException("prepared cargo slots are required");
            }
            slots = slots.clone();
            for (PlayerInventory.StackSnapshot slot : slots) {
                if (slot == null) throw new IllegalArgumentException("prepared cargo slot required");
            }
        }

        private int slotCount() {
            return slots.length;
        }

        private PlayerInventory.StackSnapshot slot(int index) {
            if (index < 0 || index >= slots.length) {
                throw new IllegalArgumentException("prepared cargo slot index out of range");
            }
            return slots[index];
        }
    }

    private static int parseIntField(String field, int row, String fieldName, int offset,
            long maximum, int maximumDigits) {
        return Math.toIntExact(parseUnsignedDecimal(field, maximum, maximumDigits,
                row, fieldName, offset));
    }

    private static long parseEnchantmentField(String field, int row, int offset) {
        return parseUnsignedDecimal(field, ENCHANTMENT_MASK_LIMIT, MAX_ENCHANTMENT_FIELD_DIGITS,
                row, FIELD_NAMES.get(4), offset);
    }

    /** Parses canonical unsigned decimal without allowing arithmetic overflow. */
    private static long parseUnsignedDecimal(String field, long maximum, int maximumDigits,
            int row, String fieldName, int fieldOffset) {
        if (field == null || field.isEmpty() || field.length() > maximumDigits
                || field.length() > 1 && field.charAt(0) == '0') {
            throw formatError(row, fieldName, fieldOffset, "malformed cargo field");
        }
        long value = 0L;
        for (int index = 0; index < field.length(); index++) {
            char c = field.charAt(index);
            if (c < '0' || c > '9') {
                throw formatError(row, fieldName, fieldOffset + index, "malformed cargo field");
            }
            int digit = c - '0';
            if (value > (maximum - digit) / 10L) {
                throw formatError(row, fieldName, fieldOffset + index, "cargo field overflows");
            }
            value = value * 10L + digit;
        }
        return value;
    }

    private static void validateDecodedStack(int row, int[] offsets, int slot, int itemType,
            int count, int durability, long enchantments, int mapId, int shulkerId) {
        if (slot < 0 || slot >= MAX_ENTRIES) {
            throw formatError(row, FIELD_NAMES.get(0), offsets[0], "malformed cargo stack");
        }
        if (!isRegisteredItemType(itemType)) {
            throw formatError(row, FIELD_NAMES.get(1), offsets[1], "malformed cargo stack");
        }
        short rawItemType = (short) itemType;
        if (count <= 0 || count > PlayerInventory.stackMax(rawItemType)) {
            throw formatError(row, FIELD_NAMES.get(2), offsets[2], "malformed cargo stack");
        }
        if (PlayerInventory.isDurable(rawItemType)) {
            if (durability <= 0 || durability > PlayerInventory.initialDurability(rawItemType)) {
                throw formatError(row, FIELD_NAMES.get(3), offsets[3], "malformed cargo stack");
            }
        } else if (durability != 0) {
            throw formatError(row, FIELD_NAMES.get(3), offsets[3], "malformed cargo stack");
        }
        if (!EnchantmentRules.isValidEnchantmentMaskForItem(rawItemType, enchantments)) {
            throw formatError(row, FIELD_NAMES.get(4), offsets[4], "malformed cargo stack");
        }
        if (!PlayerInventory.isValidMapIdentity(rawItemType, mapId)) {
            throw formatError(row, FIELD_NAMES.get(5), offsets[5], "malformed cargo stack");
        }
        if (!PlayerInventory.isValidShulkerIdentity(rawItemType, shulkerId)
                || shulkerId != 0 && count != 1) {
            throw formatError(row, FIELD_NAMES.get(6), offsets[6], "malformed cargo stack");
        }
    }

    private static boolean isRegisteredItemType(int itemType) {
        return itemType > 0 && itemType < Blocks.PROTOCOL_ID_TABLE_CAPACITY
                && PlayerInventory.isRegisteredItemType((short) itemType);
    }

    private static FieldParts splitFields(String entry, int entryStart) {
        List<String> values = new ArrayList<>();
        List<Integer> offsets = new ArrayList<>();
        int fieldStart = 0;
        offsets.add(entryStart);
        for (int index = 0; index < entry.length(); index++) {
            if (entry.charAt(index) != FIELD_SEPARATOR) continue;
            values.add(entry.substring(fieldStart, index));
            fieldStart = index + 1;
            offsets.add(entryStart + fieldStart);
        }
        values.add(entry.substring(fieldStart));
        return new FieldParts(values.toArray(String[]::new), offsets.stream().mapToInt(Integer::intValue).toArray());
    }

    private static CargoFormatException formatError(int row, String field, int offset, String reason) {
        int boundedRow = Math.max(0, Math.min(MAX_ENTRIES, row));
        int boundedOffset = Math.max(0, Math.min(MAX_ENCODED_CHARS, offset));
        return new CargoFormatException(reason + " at row " + boundedRow + ", field " + field
                + ", character offset " + boundedOffset, boundedRow, field, boundedOffset);
    }

    private record FieldParts(String[] values, int[] offsets) {
        private FieldParts {
            values = values.clone();
            offsets = offsets.clone();
        }
    }

    private static int maxEncodedChars() {
        long slotDigits = 0L;
        for (int slot = 0; slot < MAX_ENTRIES; slot++) {
            slotDigits += Long.toString(slot).length();
        }
        long charsPerRowWithoutSlot = 5L * MAX_INT_FIELD_DIGITS
                + MAX_ENCHANTMENT_FIELD_DIGITS + FIELDS_PER_SLOT - 1L;
        return Math.toIntExact(slotDigits + MAX_ENTRIES * charsPerRowWithoutSlot
                + MAX_ENTRIES - 1L);
    }
}
