package com.gameexpert.terrain.mc.structure;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Authenticated production projection of the pinned official Village template entity NBT. */
public final class Mc263VillageTemplateEntityFacts {
    public static final String RESOURCE = "/mc263/village-template-entity-facts-v1.txt";
    public static final int RESOURCE_LENGTH = 17_253;
    public static final String RESOURCE_SHA256 =
            "34fdef9d294ca346b3193b7bcbb6ec98a1da08a1fba9a8d7448fb4b2e18621bd";
    public static final int ROW_BYTES = 17_006;
    public static final String ROW_SHA256 =
            "1980bc2782b2b0911e51a80a77a24344bcdbc7a6d5a048cf6f17e73b8f8ea277";
    public static final String ORACLE_SOURCE_SHA256 =
            "1fa2d82cb119e107ca1aafb7e9ca361a08a3a2ea653496a400ab3876b6a02584";
    public static final int ENTITY_COUNT = 54;
    public static final int CAT_TEMPLATE_VARIANT_COUNT = 10;
    public static final int CAT_VARIANT_COUNT = 11;
    public static final int PIG_SOUND_VARIANT_COUNT = 3;

    private static final String OUTER_SHA1 = "06157fedd67ff4dd6e0e6fa4a9dd0af296f0dd61";
    private static final String INNER_SHA1 = "2f1ef79f3cad10138ad18da45b265fe656624026";
    private static final Set<String> TYPES = Set.copyOf(
            Mc263VillageEntityAuthority.AUTHENTICATED_ENTITY_TYPES);
    private static final Corpus PINNED = load();

    private Mc263VillageTemplateEntityFacts() {}

    public static Corpus pinned() { return PINNED; }

    private static Corpus load() {
        byte[] resource;
        try (InputStream input = Mc263VillageTemplateEntityFacts.class.getResourceAsStream(RESOURCE)) {
            if (input == null) throw new IllegalStateException("missing Village template facts resource");
            resource = input.readAllBytes();
        } catch (IOException error) {
            throw new IllegalStateException("failed to read Village template facts resource", error);
        }
        require(resource.length == RESOURCE_LENGTH && sha256(resource).equals(RESOURCE_SHA256),
                "Village template facts resource identity drift");
        String text = new String(resource, StandardCharsets.US_ASCII);
        require(text.endsWith("\n") && text.indexOf('\r') < 0,
                "Village template facts newline drift");
        String[] lines = text.split("\n", -1);
        int factRows = ENTITY_COUNT + CAT_TEMPLATE_VARIANT_COUNT + CAT_VARIANT_COUNT + 1
                + PIG_SOUND_VARIANT_COUNT + 1;
        require(lines.length == factRows + 2 && lines[lines.length - 1].isEmpty(),
                "Village template facts line count drift");
        String[] header = fields(lines[0], 9, "header");
        require(header[0].equals("H") && header[1].equals("VEF2632")
                        && header[2].equals("26.3-snapshot-7")
                        && header[3].equals(OUTER_SHA1) && header[4].equals(INNER_SHA1)
                        && header[5].equals(ORACLE_SOURCE_SHA256)
                        && parseInt(header[6], "entity count") == ENTITY_COUNT
                        && parseInt(header[7], "row bytes") == ROW_BYTES
                        && header[8].equals(ROW_SHA256),
                "Village template facts header drift");
        int rowOffset = lines[0].getBytes(StandardCharsets.US_ASCII).length + 1;
        byte[] rows = java.util.Arrays.copyOfRange(resource, rowOffset, resource.length);
        require(rows.length == ROW_BYTES && sha256(rows).equals(ROW_SHA256),
                "Village template facts row identity drift");

        LinkedHashMap<Identity, Entry> entries = new LinkedHashMap<>();
        HashSet<String> types = new HashSet<>();
        for (int index = 1; index <= ENTITY_COUNT; index++) {
            String[] row = fields(lines[index], 17, "entity row " + (index - 1));
            require(row[0].equals("E") && row[16].equals("EASY"),
                    "Village template facts opcode/difficulty drift");
            String template = decode(row[1], "template key");
            int ordinal = parseInt(row[2], "entity ordinal");
            String entity = decode(row[3], "entity key");
            require(template.startsWith("minecraft:village/") && ordinal >= 0
                            && TYPES.contains(entity),
                    "Village template entity identity drift");
            Entry value = new Entry(template, ordinal, entity,
                    parseLongs(row[4], 3, "template position bits"),
                    parseInts(row[5], 2, "template rotation bits"),
                    parseLongs(row[6], 3, "template motion bits"),
                    optionalLong(row[7], "follow-range bits"),
                    optionalInt(row[8], "age"), optionalString(row[9], "profession"),
                    optionalString(row[10], "villager type"), optionalInt(row[11], "sheep color"),
                    optionalInts(row[12], 3, "armor-stand head pose"),
                    optionalInts(row[13], 3, "armor-stand body pose"),
                    optionalString(row[14], "armor-stand equipment slot"),
                    optionalString(row[15], "armor-stand equipment item"));
            require(entries.put(new Identity(template, ordinal), value) == null,
                    "duplicate Village template entity identity");
            types.add(entity);
        }
        require(entries.size() == ENTITY_COUNT && types.equals(TYPES),
                "Village template entity closure drift");

        LinkedHashMap<Identity, String> templateCatVariants = new LinkedHashMap<>();
        int cursor = ENTITY_COUNT + 1;
        for (int index = 0; index < CAT_TEMPLATE_VARIANT_COUNT; index++, cursor++) {
            String[] row = fields(lines[cursor], 4, "cat template variant row " + index);
            Identity identity = new Identity(decode(row[1], "cat template key"),
                    parseInt(row[2], "cat entity ordinal"));
            Entry entry = entries.get(identity);
            require(row[0].equals("T") && entry != null
                            && entry.entityKey().equals(Mc263VillageEntityAuthority.CAT),
                    "Village cat template variant identity drift");
            require(templateCatVariants.put(identity, decode(row[3], "cat template variant")) == null,
                    "duplicate Village cat template variant identity");
        }
        require(templateCatVariants.size() == CAT_TEMPLATE_VARIANT_COUNT,
                "Village cat template variant closure drift");

        ArrayList<CatVariantFact> catVariants = new ArrayList<>();
        for (int index = 0; index < CAT_VARIANT_COUNT; index++, cursor++) {
            String[] row = fields(lines[cursor], 5, "cat registry row " + index);
            require(row[0].equals("V") && parseInt(row[1], "cat registry index") == index
                            && row[3].matches("[0-9a-f]{64}"),
                    "Village cat registry identity drift");
            String conditions = decode(row[4], "cat spawn conditions");
            require(index == 0
                            ? conditions.equals("1:structure:#minecraft:cats_spawn_as_black;"
                                    + "0:moon_brightness:min=0.9")
                            : conditions.equals("0:always"),
                    "Village cat spawn-condition drift");
            catVariants.add(new CatVariantFact(index, decode(row[2], "cat variant key"),
                    row[3], conditions));
        }
        String[] grammar = fields(lines[cursor], 9, "cat draw grammar");
        require(grammar[0].equals("G")
                        && decode(grammar[1], "Cat class").equals(
                                "net/minecraft/world/entity/animal/feline/Cat.class")
                        && grammar[2].equals(
                                "03640f57a16103e025b55f519875dd96e0ba9f9f92600244a99de88c1bc525e8")
                        && decode(grammar[3], "VariantUtils class").equals(
                                "net/minecraft/world/entity/variant/VariantUtils.class")
                        && grammar[4].equals(
                                "70e65119c13ce8ce163792757b41ffe887ed35e8db5a421b86296db2a52d8bcb")
                        && decode(grammar[5], "PriorityProvider class").equals(
                                "net/minecraft/world/entity/variant/PriorityProvider.class")
                        && grammar[6].equals(
                                "46dd83a5977a6e0d6ff8b61c583841fcda4865fd46a59df5e1549d6912538b98")
                        && parseInt(grammar[7], "cat candidate count") == CAT_VARIANT_COUNT,
                "Village cat draw-grammar identity drift");
        String drawGrammar = decode(grammar[8], "cat draw grammar");
        require(drawGrammar.equals("Cat.finalizeSpawn:super;SpawnContext.create;"
                        + "VariantUtils.selectVariantToSpawn;PriorityProvider.pick:"
                        + "highest-matching-priority,registry-order-candidates,Util.getRandomSafe,"
                        + "nextInt(candidateCount);CatSoundVariants.pickRandomSoundVariant:nextInt(2)"),
                "Village cat draw-grammar semantic drift");
        cursor++;

        // `minecraft:pig_sound_variant` is a data-driven registry with no spawn conditions;
        // `PigSoundVariants.pickRandomSoundVariant` is `registry.getRandom(random).orElseThrow()`,
        // a uniform `nextInt(size)` over the whole registry in registration order, so every index
        // the draw can return is authenticated here.
        ArrayList<PigSoundVariantFact> pigSoundVariants = new ArrayList<>();
        for (int index = 0; index < PIG_SOUND_VARIANT_COUNT; index++, cursor++) {
            String[] row = fields(lines[cursor], 5, "pig sound registry row " + index);
            require(row[0].equals("S") && parseInt(row[1], "pig sound registry index") == index
                            && row[3].matches("[0-9a-f]{64}"),
                    "Village pig sound registry identity drift");
            String conditions = decode(row[4], "pig sound spawn conditions");
            require(conditions.equals("registry-uniform"),
                    "Village pig sound spawn-condition drift");
            pigSoundVariants.add(new PigSoundVariantFact(index,
                    decode(row[2], "pig sound variant key"), row[3], conditions));
        }
        String[] pigGrammar = fields(lines[cursor], 9, "pig draw grammar");
        require(pigGrammar[0].equals("P")
                        && decode(pigGrammar[1], "Pig class").equals(
                                "net/minecraft/world/entity/animal/pig/Pig.class")
                        && pigGrammar[2].equals(
                                "082701bfa8073e6508658d57f9792bb1e47d862007f324be4fb4dfee3066870e")
                        && decode(pigGrammar[3], "PigSoundVariants class").equals(
                                "net/minecraft/world/entity/animal/pig/PigSoundVariants.class")
                        && pigGrammar[4].equals(
                                "9d0802f7d3a37e3ea7b4d737cafa8578f7efbba09addc487b907dde684a672a0")
                        && decode(pigGrammar[5], "MappedRegistry class").equals(
                                "net/minecraft/core/MappedRegistry.class")
                        && pigGrammar[6].equals(
                                "4f3279ff6a2da3650551bf5744640986a936b14f883e807f2d23079d88ecda40")
                        && parseInt(pigGrammar[7], "pig sound variant count")
                                == PIG_SOUND_VARIANT_COUNT,
                "Village pig draw-grammar identity drift");
        String pigDrawGrammar = decode(pigGrammar[8], "pig draw grammar");
        require(pigDrawGrammar.equals("Pig.finalizeSpawn:SpawnContext.create;"
                        + "VariantUtils.selectVariantToSpawn;"
                        + "PigSoundVariants.pickRandomSoundVariant:Registry.getRandom,"
                        + "MappedRegistry.byId-registration-order,Util.getRandomSafe,"
                        + "nextInt(soundVariantCount);Animal.finalizeSpawn:super"),
                "Village pig draw-grammar semantic drift");
        return new Corpus(entries, templateCatVariants, catVariants, drawGrammar,
                pigSoundVariants, pigDrawGrammar);
    }

    public static final class Corpus {
        private final Map<Identity, Entry> entries;
        private final Map<Identity, String> templateCatVariants;
        private final List<CatVariantFact> catVariants;
        private final List<String> catVariantKeys;
        private final String catDrawGrammar;
        private final List<PigSoundVariantFact> pigSoundVariants;
        private final List<String> pigSoundVariantKeys;
        private final String pigDrawGrammar;

        private Corpus(Map<Identity, Entry> entries, Map<Identity, String> templateCatVariants,
                List<CatVariantFact> catVariants, String catDrawGrammar,
                List<PigSoundVariantFact> pigSoundVariants, String pigDrawGrammar) {
            this.entries = Map.copyOf(entries);
            this.templateCatVariants = Map.copyOf(templateCatVariants);
            this.catVariants = List.copyOf(catVariants);
            this.catVariantKeys = registryOrderKeys(this.catVariants.size(),
                    index -> this.catVariants.get(index).index(),
                    index -> this.catVariants.get(index).key(), "cat");
            this.catDrawGrammar = catDrawGrammar;
            this.pigSoundVariants = List.copyOf(pigSoundVariants);
            this.pigSoundVariantKeys = registryOrderKeys(this.pigSoundVariants.size(),
                    index -> this.pigSoundVariants.get(index).index(),
                    index -> this.pigSoundVariants.get(index).key(), "pig sound");
            this.pigDrawGrammar = pigDrawGrammar;
        }

        public int size() { return entries.size(); }

        public Entry require(String templateKey, int entityOrdinal, String semanticJson) {
            Objects.requireNonNull(templateKey, "Village entity template key");
            Objects.requireNonNull(semanticJson, "Village entity semantic JSON");
            Entry entry = entries.get(new Identity(templateKey, entityOrdinal));
            if (entry == null) {
                throw new IllegalArgumentException("unknown Village template entity: "
                        + templateKey + '#' + entityOrdinal);
            }
            String semanticType = jsonString(semanticJson, "entityType");
            Mc263VillageTemplateEntityFacts.require(entry.entityKey.equals(semanticType),
                    "Village template entity semantic/resource drift");
            return entry;
        }

        public List<Entry> entriesInOfficialOrder() {
            return List.copyOf(entries.values());
        }

        public String requireTemplateCatVariant(String templateKey, int entityOrdinal) {
            String value = templateCatVariants.get(new Identity(templateKey, entityOrdinal));
            if (value == null) {
                throw new IllegalArgumentException("unknown Village template cat variant: "
                        + templateKey + '#' + entityOrdinal);
            }
            return value;
        }

        public List<CatVariantFact> catVariantsInRegistryOrder() { return catVariants; }

        /**
         * The whole authenticated {@code minecraft:cat_variant} registry as draw keys, in
         * registration order. This list is the draw table: {@code Mc263RegistryDraw.uniform}
         * derives the {@code nextInt} bound from its size, so no caller restates
         * {@link #CAT_VARIANT_COUNT} beside a draw.
         */
        public List<String> catVariantKeysInRegistryOrder() { return catVariantKeys; }

        public String catDrawGrammar() { return catDrawGrammar; }

        public List<PigSoundVariantFact> pigSoundVariantsInRegistryOrder() {
            return pigSoundVariants;
        }

        /**
         * The whole authenticated {@code minecraft:pig_sound_variant} registry as draw keys, in
         * registration order; the draw table for {@code Mc263RegistryDraw.uniform}.
         */
        public List<String> pigSoundVariantKeysInRegistryOrder() { return pigSoundVariantKeys; }

        public String pigDrawGrammar() { return pigDrawGrammar; }
    }

    /**
     * Projects a registry fact list to its draw keys after proving the rows are the contiguous
     * registration order {@code 0..size}. The draw table therefore cannot be indexed by anything
     * but that registration order.
     */
    private static List<String> registryOrderKeys(int size,
            java.util.function.IntUnaryOperator declaredIndex,
            java.util.function.IntFunction<String> key, String what) {
        ArrayList<String> keys = new ArrayList<>(size);
        for (int index = 0; index < size; index++) {
            require(declaredIndex.applyAsInt(index) == index,
                    "Village " + what + " registry order drift");
            keys.add(key.apply(index));
        }
        require(!keys.isEmpty(), "Village " + what + " registry is empty");
        return List.copyOf(keys);
    }

    public record CatVariantFact(int index, String key, String jsonSha256, String conditions) {}

    public record PigSoundVariantFact(int index, String key, String jsonSha256, String conditions) {}

    public static final class Entry {
        private final String templateKey;
        private final int entityOrdinal;
        private final String entityKey;
        private final long[] templatePositionBits;
        private final int[] templateRotationBits;
        private final long[] templateMotionBits;
        private final Mc263VillageEntityAuthority.TemplateFacts facts;

        private Entry(String templateKey, int entityOrdinal, String entityKey,
                long[] templatePositionBits, int[] templateRotationBits, long[] templateMotionBits,
                Long followRangeBits, Integer age, String profession, String villagerType,
                Integer sheepColor, int[] poseHead, int[] poseBody, String equipmentSlot,
                String equipmentItem) {
            this.templateKey = templateKey;
            this.entityOrdinal = entityOrdinal;
            this.entityKey = entityKey;
            this.templatePositionBits = templatePositionBits.clone();
            this.templateRotationBits = templateRotationBits.clone();
            this.templateMotionBits = templateMotionBits.clone();
            this.facts = new Mc263VillageEntityAuthority.TemplateFacts(followRangeBits, age,
                    profession, villagerType, sheepColor, poseHead, poseBody, equipmentSlot,
                    equipmentItem, Mc263VillageEntityAuthority.Difficulty.EASY);
        }

        public String templateKey() { return templateKey; }
        public int entityOrdinal() { return entityOrdinal; }
        public String entityKey() { return entityKey; }
        public long[] templatePositionBits() { return templatePositionBits.clone(); }
        public int[] templateRotationBits() { return templateRotationBits.clone(); }
        public long[] templateMotionBits() { return templateMotionBits.clone(); }
        public Mc263VillageEntityAuthority.TemplateFacts facts() { return facts; }
    }

    private static final class Identity {
        private final String templateKey;
        private final int ordinal;

        private Identity(String templateKey, int ordinal) {
            this.templateKey = templateKey;
            this.ordinal = ordinal;
        }

        @Override public boolean equals(Object other) {
            return other instanceof Identity value && ordinal == value.ordinal
                    && templateKey.equals(value.templateKey);
        }

        @Override public int hashCode() { return Objects.hash(templateKey, ordinal); }
    }

    private static String jsonString(String json, String name) {
        String prefix = "\"" + name + "\":\"";
        int start = json.indexOf(prefix);
        require(start >= 0, "Village entity semantic lacks " + name);
        start += prefix.length();
        int end = json.indexOf('"', start);
        require(end >= start && json.indexOf('\\', start) < 0,
                "Village entity semantic string is not canonical");
        return json.substring(start, end);
    }

    private static String[] fields(String line, int count, String label) {
        String[] fields = line.split("\\|", -1);
        require(fields.length == count, "Village template facts " + label + " field drift");
        return fields;
    }

    private static String decode(String value, String label) {
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
            require(!decoded.isEmpty(), "empty Village template facts " + label);
            return decoded;
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException("invalid Village template facts " + label, error);
        }
    }

    private static String optionalString(String value, String label) {
        return value.equals("-") ? null : decode(value, label);
    }

    private static Integer optionalInt(String value, String label) {
        return value.equals("-") ? null : parseInt(value, label);
    }

    private static Long optionalLong(String value, String label) {
        if (value.equals("-")) return null;
        try { return Long.parseLong(value); }
        catch (NumberFormatException error) {
            throw new IllegalArgumentException("invalid Village template facts " + label, error);
        }
    }

    private static int[] optionalInts(String value, int count, String label) {
        return value.equals("-") ? null : parseInts(value, count, label);
    }

    private static int[] parseInts(String value, int count, String label) {
        String[] fields = value.split(",", -1);
        require(fields.length == count, "Village template facts " + label + " cardinality drift");
        int[] result = new int[count];
        for (int index = 0; index < count; index++) result[index] = parseInt(fields[index], label);
        return result;
    }

    private static long[] parseLongs(String value, int count, String label) {
        String[] fields = value.split(",", -1);
        require(fields.length == count, "Village template facts " + label + " cardinality drift");
        long[] result = new long[count];
        for (int index = 0; index < count; index++) {
            try { result[index] = Long.parseLong(fields[index]); }
            catch (NumberFormatException error) {
                throw new IllegalArgumentException("invalid Village template facts " + label, error);
            }
        }
        return result;
    }

    private static int parseInt(String value, String label) {
        try { return Integer.parseInt(value); }
        catch (NumberFormatException error) {
            throw new IllegalArgumentException("invalid Village template facts " + label, error);
        }
    }

    private static String sha256(byte[] value) {
        try {
            return java.util.HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }
}
