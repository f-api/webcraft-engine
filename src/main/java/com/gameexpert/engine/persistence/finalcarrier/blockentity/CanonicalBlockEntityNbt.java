package com.gameexpert.engine.persistence.finalcarrier.blockentity;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

/** Bounded, strict reader for one raw binary-NBT block-entity compound. */
public final class CanonicalBlockEntityNbt {
    public static final int MAX_BYTES = 1_048_576;
    public static final int MAX_DEPTH = 64;
    public static final int MAX_VALUES = 65_536;

    private CanonicalBlockEntityNbt() {}

    /**
     * Validates the complete document without normalizing or reserializing it. The returned
     * document retains a defensive copy of the exact carrier bytes.
     */
    public static Document parse(byte[] raw) {
        Objects.requireNonNull(raw, "canonical block-entity NBT");
        if (raw.length == 0 || raw.length > MAX_BYTES) {
            throw invalid("NBT byte length outside 1.." + MAX_BYTES);
        }
        Reader reader = new Reader(raw);
        int rootType = reader.u8("root tag");
        if (rootType != TagType.COMPOUND.id) {
            throw invalid("NBT root must be TAG_Compound");
        }
        String rootName = reader.string("root name");
        if (!rootName.isEmpty()) {
            throw invalid("block-entity NBT root name must be empty");
        }
        Node root = reader.payload(TagType.COMPOUND, 0);
        if (!reader.exhausted()) {
            throw invalid("trailing bytes after NBT root");
        }
        return new Document(raw, root.compound());
    }

    public enum TagType {
        END(0), BYTE(1), SHORT(2), INT(3), LONG(4), FLOAT(5), DOUBLE(6), BYTE_ARRAY(7),
        STRING(8), LIST(9), COMPOUND(10), INT_ARRAY(11), LONG_ARRAY(12);

        private final int id;

        TagType(int id) {
            this.id = id;
        }

        public int id() {
            return id;
        }

        private static TagType fromId(int id) {
            for (TagType type : values()) if (type.id == id) return type;
            throw invalid("unknown NBT tag type " + id);
        }
    }

    /** Immutable root view used by the semantic installation planner. */
    public static final class Document {
        private final byte[] raw;
        private final Map<String, Node> root;

        private Document(byte[] raw, Map<String, Node> root) {
            this.raw = raw.clone();
            this.root = root;
        }

        public byte[] raw() {
            return raw.clone();
        }

        public Map<String, TagType> rootTypes() {
            LinkedHashMap<String, TagType> result = new LinkedHashMap<>();
            root.forEach((name, value) -> result.put(name, value.type));
            return Collections.unmodifiableMap(result);
        }

        public boolean contains(String name) {
            return root.containsKey(name);
        }

        public Optional<String> string(String name) {
            Node node = root.get(name);
            if (node == null) return Optional.empty();
            if (node.type != TagType.STRING) {
                throw invalid("NBT field " + name + " must be TAG_String");
            }
            return Optional.of((String) node.value);
        }

        public OptionalLong longValue(String name) {
            Node node = root.get(name);
            if (node == null) return OptionalLong.empty();
            if (node.type != TagType.LONG) {
                throw invalid("NBT field " + name + " must be TAG_Long");
            }
            return OptionalLong.of((long) node.value);
        }

        public Optional<Integer> intValue(String name) {
            Node node = root.get(name);
            if (node == null) return Optional.empty();
            if (node.type != TagType.INT) {
                throw invalid("NBT field " + name + " must be TAG_Int");
            }
            return Optional.of((int) node.value);
        }

        /**
         * Returns the exact named string fields of a nested compound.  The compound may contain
         * neither extra fields nor differently typed values, so callers never silently ignore a
         * canonical projection change.
         */
        public Map<String, String> requireCompoundStrings(String name, String... names) {
            Node node = root.get(name);
            if (node == null || node.type != TagType.COMPOUND) {
                throw invalid("NBT field " + name + " must be TAG_Compound");
            }
            Map<String, Node> compound = node.compound();
            java.util.Set<String> required = java.util.Set.of(names);
            if (compound.size() != required.size() || !compound.keySet().equals(required)) {
                throw invalid("NBT compound " + name + " has non-canonical fields");
            }
            LinkedHashMap<String, String> result = new LinkedHashMap<>();
            for (String field : names) {
                Node value = compound.get(field);
                if (value.type != TagType.STRING) {
                    throw invalid("NBT field " + name + "." + field + " must be TAG_String");
                }
                result.put(field, (String) value.value);
            }
            return Collections.unmodifiableMap(result);
        }

        /** Validates that the named nested value remains a compound without reserializing it. */
        public void requireCompound(String name) {
            Node node = root.get(name);
            if (node == null || node.type != TagType.COMPOUND) {
                throw invalid("NBT field " + name + " must be TAG_Compound");
            }
        }

        public void requireOnly(String... names) {
            java.util.Set<String> allowed = java.util.Set.of(names);
            for (String actual : root.keySet()) {
                if (!allowed.contains(actual)) {
                    throw invalid("unsupported block-entity NBT field " + actual);
                }
            }
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Document value && Arrays.equals(raw, value.raw);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(raw);
        }
    }

    private record Node(TagType type, Object value) {
        private Map<String, Node> compound() {
            @SuppressWarnings("unchecked")
            Map<String, Node> result = (Map<String, Node>) value;
            return result;
        }
    }

    private static final class Reader {
        private final byte[] input;
        private int offset;
        private int values;

        private Reader(byte[] input) {
            this.input = input;
        }

        private Node payload(TagType type, int depth) {
            if (type == TagType.END) throw invalid("TAG_End cannot have a payload");
            if (depth >= MAX_DEPTH) throw invalid("NBT nesting exceeds " + MAX_DEPTH);
            account(1);
            return switch (type) {
                case BYTE -> new Node(type, (byte) u8("TAG_Byte"));
                case SHORT -> new Node(type, (short) u16("TAG_Short"));
                case INT -> new Node(type, i32("TAG_Int"));
                case LONG -> new Node(type, i64("TAG_Long"));
                case FLOAT -> new Node(type, Float.intBitsToFloat(i32("TAG_Float")));
                case DOUBLE -> new Node(type, Double.longBitsToDouble(i64("TAG_Double")));
                case BYTE_ARRAY -> {
                    int count = count("TAG_Byte_Array");
                    account(count);
                    require(count, "TAG_Byte_Array payload");
                    byte[] bytes = Arrays.copyOfRange(input, offset, offset + count);
                    offset += count;
                    yield new Node(type, bytes);
                }
                case STRING -> new Node(type, string("TAG_String"));
                case LIST -> list(depth + 1);
                case COMPOUND -> compound(depth + 1);
                case INT_ARRAY -> {
                    int count = count("TAG_Int_Array");
                    account(count);
                    requireProduct(count, Integer.BYTES, "TAG_Int_Array payload");
                    int[] values = new int[count];
                    for (int i = 0; i < count; i++) values[i] = i32("TAG_Int_Array value");
                    yield new Node(type, values);
                }
                case LONG_ARRAY -> {
                    int count = count("TAG_Long_Array");
                    account(count);
                    requireProduct(count, Long.BYTES, "TAG_Long_Array payload");
                    long[] values = new long[count];
                    for (int i = 0; i < count; i++) values[i] = i64("TAG_Long_Array value");
                    yield new Node(type, values);
                }
                case END -> throw new AssertionError();
            };
        }

        private Node list(int depth) {
            TagType element = TagType.fromId(u8("TAG_List element type"));
            int count = count("TAG_List");
            if (element == TagType.END && count != 0) {
                throw invalid("non-empty TAG_List cannot use TAG_End elements");
            }
            account(count);
            java.util.ArrayList<Node> entries = new java.util.ArrayList<>(count);
            for (int i = 0; i < count; i++) entries.add(payload(element, depth));
            return new Node(TagType.LIST, ListValue.of(element, entries));
        }

        private Node compound(int depth) {
            LinkedHashMap<String, Node> entries = new LinkedHashMap<>();
            while (true) {
                TagType type = TagType.fromId(u8("TAG_Compound entry type"));
                if (type == TagType.END) break;
                String name = string("TAG_Compound entry name");
                if (entries.containsKey(name)) {
                    throw invalid("duplicate TAG_Compound name " + name);
                }
                entries.put(name, payload(type, depth));
            }
            return new Node(TagType.COMPOUND,
                    Collections.unmodifiableMap(new LinkedHashMap<>(entries)));
        }

        private String string(String label) {
            int length = u16(label + " byte length");
            require(length, label);
            int end = offset + length;
            StringBuilder result = new StringBuilder(length);
            while (offset < end) {
                int first = input[offset++] & 0xff;
                if (first >= 0x01 && first <= 0x7f) {
                    result.append((char) first);
                } else if ((first & 0xe0) == 0xc0) {
                    if (offset >= end) throw invalid("truncated modified UTF-8 in " + label);
                    int second = input[offset++] & 0xff;
                    if ((second & 0xc0) != 0x80) throw invalid("malformed modified UTF-8 in " + label);
                    int value = (first & 0x1f) << 6 | second & 0x3f;
                    if (value == 0) {
                        if (first != 0xc0 || second != 0x80) {
                            throw invalid("overlong modified UTF-8 in " + label);
                        }
                    } else if (value < 0x80) {
                        throw invalid("overlong modified UTF-8 in " + label);
                    }
                    result.append((char) value);
                } else if ((first & 0xf0) == 0xe0) {
                    if (offset + 1 >= end) throw invalid("truncated modified UTF-8 in " + label);
                    int second = input[offset++] & 0xff;
                    int third = input[offset++] & 0xff;
                    if ((second & 0xc0) != 0x80 || (third & 0xc0) != 0x80) {
                        throw invalid("malformed modified UTF-8 in " + label);
                    }
                    int value = (first & 0x0f) << 12 | (second & 0x3f) << 6 | third & 0x3f;
                    if (value < 0x800) throw invalid("overlong modified UTF-8 in " + label);
                    result.append((char) value);
                } else {
                    throw invalid("malformed modified UTF-8 in " + label);
                }
            }
            return result.toString();
        }

        private int count(String label) {
            int count = i32(label + " count");
            if (count < 0) throw invalid(label + " has negative count");
            if (count > MAX_VALUES) throw invalid(label + " count exceeds " + MAX_VALUES);
            return count;
        }

        private void account(int amount) {
            if (amount < 0 || values > MAX_VALUES - amount) {
                throw invalid("NBT value count exceeds " + MAX_VALUES);
            }
            values += amount;
        }

        private int u8(String label) {
            require(1, label);
            return input[offset++] & 0xff;
        }

        private int u16(String label) {
            require(2, label);
            int value = (input[offset] & 0xff) << 8 | input[offset + 1] & 0xff;
            offset += 2;
            return value;
        }

        private int i32(String label) {
            require(4, label);
            int value = (input[offset] & 0xff) << 24 | (input[offset + 1] & 0xff) << 16
                    | (input[offset + 2] & 0xff) << 8 | input[offset + 3] & 0xff;
            offset += 4;
            return value;
        }

        private long i64(String label) {
            require(8, label);
            long value = 0;
            for (int i = 0; i < 8; i++) value = value << 8 | input[offset++] & 0xffL;
            return value;
        }

        private void requireProduct(int count, int width, String label) {
            if (count > (input.length - offset) / width) throw invalid("truncated " + label);
        }

        private void require(int count, String label) {
            if (count < 0 || count > input.length - offset) throw invalid("truncated " + label);
        }

        private boolean exhausted() {
            return offset == input.length;
        }
    }

    private record ListValue(TagType elementType, java.util.List<Node> entries) {
        private static ListValue of(TagType elementType, java.util.List<Node> entries) {
            return new ListValue(elementType, java.util.List.copyOf(entries));
        }
    }

    private static IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException(message);
    }
}
