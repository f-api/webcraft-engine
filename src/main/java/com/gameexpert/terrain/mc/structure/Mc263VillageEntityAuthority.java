package com.gameexpert.terrain.mc.structure;

import com.gameexpert.terrain.mc.Mc263RegistryDraw;
import com.gameexpert.terrain.mc.feature.Mc263WorldGenRegionRandom;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/**
 * Pinned 26.3-snapshot-7 Village structure-entity authority.
 *
 * <p>Ten authenticated {@code finalizeSpawn} programs, one per Village structure-entity type, are
 * selected by {@code templateKey} plus {@code entityOrdinal}/{@code entityKey}. Each program
 * consumes the transaction-owned {@link Mc263WorldGenRegionRandom} directly &mdash; no copy and no
 * reseed &mdash; in the exact operation order recorded by
 * {@code levelRngFinalization.finalizeSpawnConsumptionRanges}, then emits the UUID-excluded full
 * canonical binary entity NBT byte-for-byte. Hierarchy summaries and caller hashes are never used
 * as payload authority; every byte is produced by the pinned program.</p>
 *
 * <p>{@code minecraft:armor_stand} is not a {@code Mob} and legitimately consumes zero
 * {@code finalizeSpawn} level RNG. {@code minecraft:zombie_villager} carries two fields that are
 * derived from the entity's own {@code RandomSource} rather than the level RNG; those are excluded
 * from the canonical payload and declared as {@link EntityRandomDerivedField} rules.</p>
 */
public final class Mc263VillageEntityAuthority {
    /** SHA-256 of {@code mc263/village-settlement-evidence-v1.json}. */
    public static final String SETTLEMENT_EVIDENCE_SHA256 =
            "3aae372fd8323858ec51fd347ae555946bbdddd0e774e07844ef9f16cc08dedd";
    /** SHA-256 of the unpacked canonical evidence document. */
    public static final String UNPACKED_EVIDENCE_SHA256 =
            "d81bcfd3112d859b5dc4f993def00320d1c019714858d4bafaebd305400fdfa9";
    /** Authenticated ENTS record count. */
    public static final int AUTHENTICATED_ENTITY_RECORDS = 85;

    public static final String SPAWN_REASON = "minecraft:structure";

    private static final int TAG_END = 0;
    private static final int TAG_BYTE = 1;
    private static final int TAG_SHORT = 2;
    private static final int TAG_INT = 3;
    private static final int TAG_LONG = 4;
    private static final int TAG_FLOAT = 5;
    private static final int TAG_DOUBLE = 6;
    private static final int TAG_STRING = 8;
    private static final int TAG_LIST = 9;
    private static final int TAG_COMPOUND = 10;

    public static final String CAT = "minecraft:cat";
    public static final String IRON_GOLEM = "minecraft:iron_golem";
    public static final String CAMEL = "minecraft:camel";
    public static final String VILLAGER = "minecraft:villager";
    public static final String HORSE = "minecraft:horse";
    public static final String SHEEP = "minecraft:sheep";
    public static final String COW = "minecraft:cow";
    public static final String PIG = "minecraft:pig";
    public static final String ARMOR_STAND = "minecraft:armor_stand";
    public static final String ZOMBIE_VILLAGER = "minecraft:zombie_villager";

    /** The ten authenticated Village structure-entity types, in evidence-cardinality order. */
    public static final List<String> AUTHENTICATED_ENTITY_TYPES = List.of(
            VILLAGER, CAT, IRON_GOLEM, SHEEP, COW, PIG, ARMOR_STAND, ZOMBIE_VILLAGER, CAMEL, HORSE);

    /** {@code Attributes.FOLLOW_RANGE} random-spawn-bonus triangle deviation. */
    private static final double FOLLOW_RANGE_DEVIATION =
            Double.longBitsToDouble(0x3fbd66cf41f212d8L);
    /** {@code Mob#finalizeSpawn} left-handed threshold. */
    private static final float LEFT_HANDED_THRESHOLD = 0.05F;

    /**
     * {@code SheepColorSpawnRules}: every one of the three pinned spawn configurations
     * ({@code TEMPERATE}, {@code WARM}, {@code COLD}) registers the same weights in the same
     * order &mdash; four {@code single} colour entries, then one {@code commonColors} entry. Only
     * the colours differ between configurations, so the draw arity is biome-independent and is
     * derived from this table rather than written beside it.
     */
    private static final int[] SHEEP_SINGLE_COLOR_ENTRY_WEIGHTS = {5, 5, 5, 3};
    /** Weight of the trailing {@code commonColors(base)} entry of every spawn configuration. */
    private static final int SHEEP_COMMON_COLOR_ENTRY_WEIGHT = 82;
    /** {@code commonColors}: the configuration's base colour weight, then {@code DyeColor.PINK}. */
    private static final int SHEEP_COMMON_COLOR_BASE_WEIGHT = 499;
    private static final int SHEEP_COMMON_COLOR_PINK_WEIGHT = 1;
    /** First {@code WeightedList} index the {@code commonColors} entry owns. */
    private static final int SHEEP_COMMON_COLOR_ENTRY_OFFSET =
            sum(SHEEP_SINGLE_COLOR_ENTRY_WEIGHTS);
    /** {@code WeightedList.totalWeight} of one spawn configuration. */
    private static final int SHEEP_CONFIGURATION_TOTAL_WEIGHT =
            SHEEP_COMMON_COLOR_ENTRY_OFFSET + SHEEP_COMMON_COLOR_ENTRY_WEIGHT;
    /** {@code WeightedList.totalWeight} of one {@code commonColors} list. */
    private static final int SHEEP_COMMON_COLOR_TOTAL_WEIGHT =
            SHEEP_COMMON_COLOR_BASE_WEIGHT + SHEEP_COMMON_COLOR_PINK_WEIGHT;
    /** {@code DyeColor.PINK.getId()}; the only sheep colour this evidence binds without a biome. */
    private static final int PINK_DYE_ID = 6;

    private static int sum(int[] weights) {
        int total = 0;
        for (int weight : weights) total += weight;
        return total;
    }

    /**
     * Registries the pinned Village facts resource carries no row family for. They are declared
     * here in registration order and with no separate count: {@link Mc263RegistryDraw#uniform}
     * derives every draw bound from the list itself.
     */
    private static final List<String> CAT_SOUND_VARIANTS =
            List.of("minecraft:classic", "minecraft:royal");
    private static final List<String> COW_VARIANTS = List.of("minecraft:cold");
    private static final List<String> COW_SOUND_VARIANTS =
            List.of("minecraft:classic", "minecraft:moody");
    private static final List<String> PIG_VARIANTS = List.of("minecraft:cold");

    /**
     * {@code Zombie} fields derived from the entity's own {@code RandomSource}. They are excluded
     * from the canonical payload and republished as declared derivation rules.
     */
    public static final List<EntityRandomDerivedField> ZOMBIE_VILLAGER_RANDOM_DERIVED_FIELDS =
            List.of(
                    new EntityRandomDerivedField(
                            "attributes[id=minecraft:knockback_resistance]"
                                    + ".modifiers[id=minecraft:random_spawn_bonus].amount",
                            "TAG_Double",
                            "net.minecraft.world.entity.monster.zombie.Zombie#handleAttributes",
                            "this.random.nextDouble() * 0.05 added as minecraft:random_spawn_bonus"
                                    + " add_value"),
                    new EntityRandomDerivedField(
                            "attributes[id=minecraft:spawn_reinforcements].base",
                            "TAG_Double",
                            "net.minecraft.world.entity.monster.zombie.Zombie"
                                    + "#randomizeReinforcementsChance",
                            "this.random.nextDouble() * minecraft:spawn_reinforcements maximum"));

    private Mc263VillageEntityAuthority() { }

    /** Level difficulty authenticated by the evidence generation environment. */
    public enum Difficulty { EASY }

    /**
     * Generates one structure entity, consuming {@code callerRandom} directly.
     *
     * @throws IllegalStateException when the caller RNG is not the exact predecessor at commit.
     */
    public static GeneratedEntity generate(EntityRequest request,
            Mc263WorldGenRegionRandom callerRandom) {
        Objects.requireNonNull(request, "Village entity request");
        Objects.requireNonNull(callerRandom, "Village entity caller WGR");

        synchronized (callerRandom) {
            Mc263WorldGenRegionRandom.State predecessor = callerRandom.snapshot();
            Mc263WorldGenRegionRandom candidate = callerRandom.forkForTransaction();
            Counting random = new Counting(candidate);

            Program program = new Program(request);
            runFinalizeProgram(request, program, random);

            byte[] canonical = encode(request.entityKey(), program);
            GeneratedEntity generated = new GeneratedEntity(request, canonical,
                    random.consumptions(), predecessor, candidate.snapshot());
            if (!callerRandom.commitIfExactPredecessor(predecessor, candidate)) {
                throw new IllegalStateException("stale Village entity WGR predecessor");
            }
            return generated;
        }
    }

    /** Runs the pinned finalize program without touching any caller RNG (test/preflight seam). */
    static byte[] canonicalNbt(EntityRequest request, Mc263WorldGenRegionRandom isolated,
            int[] consumptionsOut) {
        Program program = new Program(request);
        Counting random = new Counting(isolated);
        runFinalizeProgram(request, program, random);
        consumptionsOut[0] = random.consumptions();
        return encode(request.entityKey(), program);
    }

    private static void runFinalizeProgram(EntityRequest request, Program p, Counting random) {
        String key = request.entityKey();
        switch (key) {
            case CAT -> {
                mobFinalize(request, p, random);
                p.catVariant = Mc263RegistryDraw.uniform(
                        Mc263VillageTemplateEntityFacts.pinned().catVariantKeysInRegistryOrder(),
                        "Village cat variant", random::nextInt);
                p.catSoundVariant = Mc263RegistryDraw.uniform(
                        CAT_SOUND_VARIANTS, "Village cat sound variant", random::nextInt);
            }
            case IRON_GOLEM -> mobFinalize(request, p, random);
            case CAMEL -> mobFinalize(request, p, random);
            case VILLAGER -> mobFinalize(request, p, random);
            case ZOMBIE_VILLAGER -> {
                mobFinalize(request, p, random);
                requireEasyDifficulty(request);
                for (int slot = 0; slot < 5; slot++) {
                    // Mob#populateDefaultEquipmentSlots: five authenticated draws that admit no
                    // equipment at the authenticated EASY special multiplier.
                    random.nextFloat();
                }
            }
            case HORSE -> {
                int variant = random.nextInt(7);
                int markings = random.nextInt(5);
                p.horseVariant = variant | (markings << 8);
                double maxHealth = 15.0D + random.nextInt(8) + random.nextInt(9);
                double movementSpeed = ((double) 0.45F
                        + random.nextDouble() * 0.3D
                        + random.nextDouble() * 0.3D
                        + random.nextDouble() * 0.3D) * 0.25D;
                double jumpStrength = (double) 0.4F
                        + random.nextDouble() * 0.2D
                        + random.nextDouble() * 0.2D
                        + random.nextDouble() * 0.2D;
                p.horseMaxHealthBits = Double.doubleToRawLongBits(maxHealth);
                p.horseMovementSpeedBits = Double.doubleToRawLongBits(movementSpeed);
                p.horseJumpStrengthBits = Double.doubleToRawLongBits(jumpStrength);
                mobFinalize(request, p, random);
            }
            case SHEEP -> {
                // Sheep#finalizeSpawn:317 -> Sheep#getRandomSheepColor:284 ->
                // SheepColorSpawnRules.getSheepColor -> WeightedList.getRandomOrThrow, whose Flat
                // selector maps the single draw onto the entries in cumulative registration order.
                // The nested commonColors draw is taken only when the first draw selects the
                // trailing commonColors entry, and inside that nested list the base colour occupies
                // every index below DyeColor.PINK's single trailing weight.
                int configurationEntry = random.nextInt(SHEEP_CONFIGURATION_TOTAL_WEIGHT);
                if (configurationEntry < SHEEP_COMMON_COLOR_ENTRY_OFFSET) {
                    // A single-colour entry: no nested draw, and the colour is the configuration's
                    // own colour, which this evidence does not bind to a biome.
                    p.sheepColor = requireInt(request.facts().sheepColor(), "sheepColor");
                } else {
                    int commonEntry = random.nextInt(SHEEP_COMMON_COLOR_TOTAL_WEIGHT);
                    p.sheepColor = commonEntry < SHEEP_COMMON_COLOR_BASE_WEIGHT
                            ? requireInt(request.facts().sheepColor(), "sheepColor")
                            : PINK_DYE_ID;
                }
                mobFinalize(request, p, random);
            }
            case COW -> {
                p.cowVariant = Mc263RegistryDraw.uniform(
                        COW_VARIANTS, "Village cow variant", random::nextInt);
                p.cowSoundVariant = Mc263RegistryDraw.uniform(
                        COW_SOUND_VARIANTS, "Village cow sound variant", random::nextInt);
                mobFinalize(request, p, random);
            }
            case PIG -> {
                p.pigVariant = Mc263RegistryDraw.uniform(
                        PIG_VARIANTS, "Village pig variant", random::nextInt);
                p.pigSoundVariant = Mc263RegistryDraw.uniform(
                        Mc263VillageTemplateEntityFacts.pinned()
                                .pigSoundVariantKeysInRegistryOrder(),
                        "Village pig sound variant", random::nextInt);
                mobFinalize(request, p, random);
            }
            case ARMOR_STAND -> {
                // Not a Mob: zero finalizeSpawn level-RNG consumption.
                require(request.facts().templateFollowRangeBonusBits() == null,
                        "armor stand carries no follow-range attribute");
            }
            default -> throw new IllegalArgumentException(
                    "unsupported Village structure entity: " + key);
        }
    }

    /**
     * {@code Mob#finalizeSpawn}: the follow-range random-spawn-bonus triangle is drawn only when
     * the template entity does not already carry that modifier, then one left-handed draw.
     */
    private static void mobFinalize(EntityRequest request, Program p, Counting random) {
        Long template = request.facts().templateFollowRangeBonusBits();
        if (template == null) {
            double first = random.nextDouble();
            double second = random.nextDouble();
            p.followRangeBonusBits =
                    Double.doubleToRawLongBits(FOLLOW_RANGE_DEVIATION * (first - second));
        } else {
            p.followRangeBonusBits = template;
        }
        p.leftHanded = random.nextFloat() < LEFT_HANDED_THRESHOLD ? 1 : 0;
    }

    private static void requireEasyDifficulty(EntityRequest request) {
        require(request.facts().difficulty() == Difficulty.EASY,
                "unauthenticated Village entity difficulty: " + request.facts().difficulty());
    }

    private static int requireInt(Integer value, String what) {
        if (value == null) {
            throw new IllegalArgumentException("Village entity fact is unavailable: " + what);
        }
        return value;
    }

    private static byte[] encode(String entityKey, Program p) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            switch (entityKey) {
                case CAT -> writeCat(out, p);
                case IRON_GOLEM -> writeIronGolem(out, p);
                case CAMEL -> writeCamel(out, p);
                case VILLAGER -> writeVillager(out, p);
                case HORSE -> writeHorse(out, p);
                case SHEEP -> writeSheep(out, p);
                case COW -> writeCow(out, p);
                case PIG -> writePig(out, p);
                case ARMOR_STAND -> writeArmorStand(out, p);
                case ZOMBIE_VILLAGER -> writeZombieVillager(out, p);
                default -> throw new IllegalArgumentException(
                        "unsupported Village structure entity: " + entityKey);
            }
            out.flush();
            return bytes.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Village canonical entity NBT encoding failed", exception);
        }
    }

    /** Counts finalizeSpawn RNG operations against the transaction-owned WGR. */
    private static final class Counting {
        private final Mc263WorldGenRegionRandom random;
        private int consumptions;

        private Counting(Mc263WorldGenRegionRandom random) {
            this.random = random;
        }

        private int nextInt(int bound) {
            consumptions++;
            return random.nextInt(bound);
        }

        private float nextFloat() {
            consumptions++;
            return random.nextFloat();
        }

        private double nextDouble() {
            consumptions++;
            return random.nextDouble();
        }

        private int consumptions() {
            return consumptions;
        }
    }

    /** Mutable per-entity program state consumed by the pinned canonical NBT writers. */
    private static final class Program {
        private final long[] positionBits;
        private final int[] rotationBits;
        private final long[] motionBits;
        private final int[] poseHeadBits;
        private final int[] poseBodyBits;
        private final String armorStandEquipmentSlot;
        private final String armorStandEquipmentItem;
        private final int ageTicks;
        private final String villagerProfession;
        private final String villagerType;

        private int leftHanded;
        private long followRangeBonusBits;
        private String catVariant;
        private String catSoundVariant;
        private String cowVariant;
        private String cowSoundVariant;
        private String pigVariant;
        private String pigSoundVariant;
        private int sheepColor;
        private int horseVariant;
        private long horseMaxHealthBits;
        private long horseMovementSpeedBits;
        private long horseJumpStrengthBits;

        private Program(EntityRequest request) {
            this.positionBits = request.positionBits();
            this.rotationBits = request.rotationBits();
            this.motionBits = request.motionBits();
            TemplateFacts facts = request.facts();
            this.poseHeadBits = facts.poseHeadBits();
            this.poseBodyBits = facts.poseBodyBits();
            this.armorStandEquipmentSlot = facts.armorStandEquipmentSlot();
            this.armorStandEquipmentItem = facts.armorStandEquipmentItem();
            this.ageTicks = facts.ageTicks() == null ? 0 : facts.ageTicks();
            this.villagerProfession = facts.villagerProfession();
            this.villagerType = facts.villagerType();
        }
    }

    /** One field the authority must emit from the entity's own RandomSource, not the level RNG. */
    public record EntityRandomDerivedField(String path, String tagType, String source,
            String derivation) { }

    /**
     * Authenticated template-side facts that the level RNG does not determine. Each field is a
     * property of the template entity NBT or of the authenticated generation environment.
     */
    public static final class TemplateFacts {
        private final Long templateFollowRangeBonusBits;
        private final Integer ageTicks;
        private final String villagerProfession;
        private final String villagerType;
        private final Integer sheepColor;
        private final int[] poseHeadBits;
        private final int[] poseBodyBits;
        private final String armorStandEquipmentSlot;
        private final String armorStandEquipmentItem;
        private final Difficulty difficulty;

        public TemplateFacts(Long templateFollowRangeBonusBits, Integer ageTicks,
                String villagerProfession, String villagerType, Integer sheepColor,
                int[] poseHeadBits, int[] poseBodyBits, String armorStandEquipmentSlot,
                String armorStandEquipmentItem, Difficulty difficulty) {
            this.templateFollowRangeBonusBits = templateFollowRangeBonusBits;
            this.ageTicks = ageTicks;
            this.villagerProfession = villagerProfession;
            this.villagerType = villagerType;
            this.sheepColor = sheepColor;
            this.poseHeadBits = poseHeadBits == null ? null : poseHeadBits.clone();
            this.poseBodyBits = poseBodyBits == null ? null : poseBodyBits.clone();
            this.armorStandEquipmentSlot = armorStandEquipmentSlot;
            this.armorStandEquipmentItem = armorStandEquipmentItem;
            this.difficulty = Objects.requireNonNull(difficulty, "Village entity difficulty");
        }

        public Long templateFollowRangeBonusBits() { return templateFollowRangeBonusBits; }
        public Integer ageTicks() { return ageTicks; }
        public String villagerProfession() { return villagerProfession; }
        public String villagerType() { return villagerType; }
        public Integer sheepColor() { return sheepColor; }
        public int[] poseHeadBits() { return poseHeadBits == null ? null : poseHeadBits.clone(); }
        public int[] poseBodyBits() { return poseBodyBits == null ? null : poseBodyBits.clone(); }
        public String armorStandEquipmentSlot() { return armorStandEquipmentSlot; }
        public String armorStandEquipmentItem() { return armorStandEquipmentItem; }
        public Difficulty difficulty() { return difficulty; }
    }

    /** One authenticated structure-entity finalize request. */
    public static final class EntityRequest {
        private final String templateKey;
        private final int entityOrdinal;
        private final String entityKey;
        private final long[] positionBits;
        private final int[] rotationBits;
        private final long[] motionBits;
        private final TemplateFacts facts;

        public EntityRequest(String templateKey, int entityOrdinal, String entityKey,
                long[] positionBits, int[] rotationBits, long[] motionBits, TemplateFacts facts) {
            this.templateKey = Objects.requireNonNull(templateKey, "Village entity template key");
            if (entityOrdinal < 0) {
                throw new IllegalArgumentException("negative Village entity ordinal");
            }
            this.entityOrdinal = entityOrdinal;
            this.entityKey = Objects.requireNonNull(entityKey, "Village entity key");
            if (!AUTHENTICATED_ENTITY_TYPES.contains(entityKey)) {
                throw new IllegalArgumentException(
                        "unsupported Village structure entity: " + entityKey);
            }
            this.positionBits = requireLength(positionBits, 3, "positionBits");
            this.rotationBits = requireLength(rotationBits, 2, "rotationBits");
            this.motionBits = requireLength(motionBits, 3, "motionBits");
            this.facts = Objects.requireNonNull(facts, "Village entity template facts");
        }

        public String templateKey() { return templateKey; }
        public int entityOrdinal() { return entityOrdinal; }
        public String entityKey() { return entityKey; }
        public long[] positionBits() { return positionBits.clone(); }
        public int[] rotationBits() { return rotationBits.clone(); }
        public long[] motionBits() { return motionBits.clone(); }
        public TemplateFacts facts() { return facts; }
    }

    /** Typed ENTS payload produced by one pinned finalize program. */
    public static final class GeneratedEntity {
        private final String templateKey;
        private final int entityOrdinal;
        private final String entityKey;
        private final byte[] canonicalNbt;
        private final String canonicalNbtSha256;
        private final long[] positionBits;
        private final int[] rotationBits;
        private final long[] motionBits;
        private final int finalizeSpawnConsumptionCount;
        private final List<EntityRandomDerivedField> entityRandomDerivedFields;
        private final Mc263WorldGenRegionRandom.State predecessor;
        private final Mc263WorldGenRegionRandom.State successor;

        private GeneratedEntity(EntityRequest request, byte[] canonicalNbt, int consumptions,
                Mc263WorldGenRegionRandom.State predecessor,
                Mc263WorldGenRegionRandom.State successor) {
            this.templateKey = request.templateKey();
            this.entityOrdinal = request.entityOrdinal();
            this.entityKey = request.entityKey();
            this.canonicalNbt = canonicalNbt;
            this.canonicalNbtSha256 = sha256(canonicalNbt);
            this.positionBits = request.positionBits();
            this.rotationBits = request.rotationBits();
            this.motionBits = request.motionBits();
            this.finalizeSpawnConsumptionCount = consumptions;
            this.entityRandomDerivedFields = ZOMBIE_VILLAGER.equals(request.entityKey())
                    ? ZOMBIE_VILLAGER_RANDOM_DERIVED_FIELDS : List.of();
            this.predecessor = predecessor;
            this.successor = successor;
        }

        public String templateKey() { return templateKey; }
        public int entityOrdinal() { return entityOrdinal; }
        public String entityKey() { return entityKey; }
        public String spawnReason() { return SPAWN_REASON; }
        public byte[] canonicalNbt() { return canonicalNbt.clone(); }
        public String canonicalNbtSha256() { return canonicalNbtSha256; }
        public long[] positionBits() { return positionBits.clone(); }
        public int[] rotationBits() { return rotationBits.clone(); }
        public long[] motionBits() { return motionBits.clone(); }
        public int finalizeSpawnConsumptionCount() { return finalizeSpawnConsumptionCount; }
        public List<EntityRandomDerivedField> entityRandomDerivedFields() {
            return entityRandomDerivedFields;
        }
        public Mc263WorldGenRegionRandom.State predecessor() { return predecessor; }
        public Mc263WorldGenRegionRandom.State successor() { return successor; }
    }

    private static long[] requireLength(long[] values, int length, String what) {
        Objects.requireNonNull(values, what);
        if (values.length != length) {
            throw new IllegalArgumentException("Village entity " + what + " arity drift");
        }
        return values.clone();
    }

    private static int[] requireLength(int[] values, int length, String what) {
        Objects.requireNonNull(values, what);
        if (values.length != length) {
            throw new IllegalArgumentException("Village entity " + what + " arity drift");
        }
        return values.clone();
    }

    private static void header(DataOutputStream out, int type, String name) throws IOException {
        out.writeByte(type);
        out.writeUTF(name);
    }

    private static void tagByte(DataOutputStream out, String name, int value) throws IOException {
        header(out, TAG_BYTE, name);
        out.writeByte(value);
    }

    private static void tagShort(DataOutputStream out, String name, int value) throws IOException {
        header(out, TAG_SHORT, name);
        out.writeShort(value);
    }

    private static void tagInt(DataOutputStream out, String name, int value) throws IOException {
        header(out, TAG_INT, name);
        out.writeInt(value);
    }

    private static void tagLong(DataOutputStream out, String name, long value) throws IOException {
        header(out, TAG_LONG, name);
        out.writeLong(value);
    }

    private static void tagFloatBits(DataOutputStream out, String name, int bits)
            throws IOException {
        header(out, TAG_FLOAT, name);
        out.writeInt(bits);
    }

    private static void tagDoubleBits(DataOutputStream out, String name, long bits)
            throws IOException {
        header(out, TAG_DOUBLE, name);
        out.writeLong(bits);
    }

    private static void tagString(DataOutputStream out, String name, String value)
            throws IOException {
        header(out, TAG_STRING, name);
        out.writeUTF(Objects.requireNonNull(value, "Village entity NBT string " + name));
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }

    private static void writeCat(DataOutputStream out, Program p) throws IOException {
        out.writeByte(TAG_COMPOUND);
        out.writeUTF("");
        tagByte(out, "AgeLocked", 0);
        header(out, TAG_COMPOUND, "Brain");
        header(out, TAG_COMPOUND, "memories");
        out.writeByte(TAG_END);
        out.writeByte(TAG_END);
        tagByte(out, "Sitting", 0);
        tagByte(out, "Invulnerable", 0);
        tagByte(out, "FallFlying", 0);
        tagInt(out, "ForcedAge", 0);
        tagString(out, "sound_variant", p.catSoundVariant);
        tagInt(out, "PortalCooldown", 0);
        tagFloatBits(out, "AbsorptionAmount", 0x00000000);
        tagInt(out, "InLove", 0);
        tagShort(out, "DeathTime", 0);
        tagString(out, "variant", p.catVariant);
        tagByte(out, "PersistenceRequired", 1);
        tagString(out, "id", "minecraft:cat");
        tagInt(out, "Age", 0);
        tagByte(out, "CollarColor", 14);
        header(out, TAG_LIST, "Motion");
        out.writeByte(6);
        out.writeInt(3);
        out.writeLong(p.motionBits[0]);
        out.writeLong(p.motionBits[1]);
        out.writeLong(p.motionBits[2]);
        tagFloatBits(out, "Health", 0x41200000);
        tagByte(out, "LeftHanded", p.leftHanded);
        tagDoubleBits(out, "fall_distance", 0x0000000000000000L);
        tagShort(out, "Air", 300);
        tagByte(out, "OnGround", 1);
        header(out, TAG_LIST, "Rotation");
        out.writeByte(5);
        out.writeInt(2);
        out.writeInt(p.rotationBits[0]);
        out.writeInt(p.rotationBits[1]);
        tagInt(out, "current_impulse_context_reset_grace_time", 0);
        header(out, TAG_LIST, "Pos");
        out.writeByte(6);
        out.writeInt(3);
        out.writeLong(p.positionBits[0]);
        out.writeLong(p.positionBits[1]);
        out.writeLong(p.positionBits[2]);
        tagShort(out, "Fire", -1);
        tagByte(out, "CanPickUpLoot", 0);
        header(out, TAG_LIST, "attributes");
        out.writeByte(10);
        out.writeInt(7);
        tagString(out, "id", "minecraft:movement_speed");
        tagDoubleBits(out, "base", 0x3fd3333340000000L);
        out.writeByte(TAG_END);
        tagString(out, "id", "minecraft:armor_toughness");
        tagDoubleBits(out, "base", 0x0000000000000000L);
        out.writeByte(TAG_END);
        tagString(out, "id", "minecraft:follow_range");
        header(out, TAG_LIST, "modifiers");
        out.writeByte(10);
        out.writeInt(1);
        tagDoubleBits(out, "amount", p.followRangeBonusBits);
        tagString(out, "id", "minecraft:random_spawn_bonus");
        tagString(out, "operation", "add_multiplied_base");
        out.writeByte(TAG_END);
        tagDoubleBits(out, "base", 0x4030000000000000L);
        out.writeByte(TAG_END);
        tagString(out, "id", "minecraft:max_health");
        tagDoubleBits(out, "base", 0x4024000000000000L);
        out.writeByte(TAG_END);
        tagString(out, "id", "minecraft:attack_knockback");
        tagDoubleBits(out, "base", 0x0000000000000000L);
        out.writeByte(TAG_END);
        tagString(out, "id", "minecraft:armor");
        tagDoubleBits(out, "base", 0x0000000000000000L);
        out.writeByte(TAG_END);
        tagString(out, "id", "minecraft:knockback_resistance");
        tagDoubleBits(out, "base", 0x0000000000000000L);
        out.writeByte(TAG_END);
        tagShort(out, "HurtTime", 0);
        out.writeByte(TAG_END);
    }

    private static void writeIronGolem(DataOutputStream out, Program p) throws IOException {
        out.writeByte(TAG_COMPOUND);
        out.writeUTF("");
        header(out, TAG_LIST, "Motion");
        out.writeByte(6);
        out.writeInt(3);
        out.writeLong(p.motionBits[0]);
        out.writeLong(p.motionBits[1]);
        out.writeLong(p.motionBits[2]);
        header(out, TAG_COMPOUND, "Brain");
        header(out, TAG_COMPOUND, "memories");
        out.writeByte(TAG_END);
        out.writeByte(TAG_END);
        tagFloatBits(out, "Health", 0x42c80000);
        tagByte(out, "PlayerCreated", 0);
        tagByte(out, "Invulnerable", 0);
        tagByte(out, "FallFlying", 0);
        tagByte(out, "LeftHanded", p.leftHanded);
        tagDoubleBits(out, "fall_distance", 0x0000000000000000L);
        tagShort(out, "Air", 300);
        tagByte(out, "OnGround", 1);
        tagInt(out, "PortalCooldown", 0);
        tagFloatBits(out, "AbsorptionAmount", 0x00000000);
        header(out, TAG_LIST, "Rotation");
        out.writeByte(5);
        out.writeInt(2);
        out.writeInt(p.rotationBits[0]);
        out.writeInt(p.rotationBits[1]);
        tagInt(out, "current_impulse_context_reset_grace_time", 0);
        tagLong(out, "anger_end_time", -1L);
        header(out, TAG_LIST, "Pos");
        out.writeByte(6);
        out.writeInt(3);
        out.writeLong(p.positionBits[0]);
        out.writeLong(p.positionBits[1]);
        out.writeLong(p.positionBits[2]);
        tagShort(out, "DeathTime", 0);
        tagShort(out, "Fire", -1);
        tagByte(out, "CanPickUpLoot", 0);
        header(out, TAG_LIST, "attributes");
        out.writeByte(10);
        out.writeInt(7);
        tagString(out, "id", "minecraft:movement_speed");
        tagDoubleBits(out, "base", 0x3fd0000000000000L);
        out.writeByte(TAG_END);
        tagString(out, "id", "minecraft:armor_toughness");
        tagDoubleBits(out, "base", 0x0000000000000000L);
        out.writeByte(TAG_END);
        tagString(out, "id", "minecraft:follow_range");
        header(out, TAG_LIST, "modifiers");
        out.writeByte(10);
        out.writeInt(1);
        tagDoubleBits(out, "amount", p.followRangeBonusBits);
        tagString(out, "id", "minecraft:random_spawn_bonus");
        tagString(out, "operation", "add_multiplied_base");
        out.writeByte(TAG_END);
        tagDoubleBits(out, "base", 0x4030000000000000L);
        out.writeByte(TAG_END);
        tagString(out, "id", "minecraft:max_health");
        tagDoubleBits(out, "base", 0x4059000000000000L);
        out.writeByte(TAG_END);
        tagString(out, "id", "minecraft:attack_knockback");
        tagDoubleBits(out, "base", 0x0000000000000000L);
        out.writeByte(TAG_END);
        tagString(out, "id", "minecraft:armor");
        tagDoubleBits(out, "base", 0x0000000000000000L);
        out.writeByte(TAG_END);
        tagString(out, "id", "minecraft:knockback_resistance");
        tagDoubleBits(out, "base", 0x3ff0000000000000L);
        out.writeByte(TAG_END);
        tagByte(out, "PersistenceRequired", 1);
        tagString(out, "id", "minecraft:iron_golem");
        tagShort(out, "HurtTime", 0);
        out.writeByte(TAG_END);
    }

    private static void writeCamel(DataOutputStream out, Program p) throws IOException {
        out.writeByte(TAG_COMPOUND);
        out.writeUTF("");
        tagByte(out, "AgeLocked", 0);
        header(out, TAG_COMPOUND, "Brain");
        header(out, TAG_COMPOUND, "memories");
        out.writeByte(TAG_END);
        out.writeByte(TAG_END);
        tagByte(out, "Tame", 1);
        tagByte(out, "Invulnerable", 0);
        tagByte(out, "FallFlying", 0);
        tagInt(out, "ForcedAge", 0);
        tagInt(out, "PortalCooldown", 0);
        tagFloatBits(out, "AbsorptionAmount", 0x00000000);
        tagByte(out, "Bred", 0);
        tagInt(out, "InLove", 0);
        tagByte(out, "EatingHaystack", 0);
        tagShort(out, "DeathTime", 0);
        tagByte(out, "PersistenceRequired", 0);
        tagString(out, "id", "minecraft:camel");
        tagInt(out, "Age", 0);
        header(out, TAG_LIST, "Motion");
        out.writeByte(6);
        out.writeInt(3);
        out.writeLong(p.motionBits[0]);
        out.writeLong(p.motionBits[1]);
        out.writeLong(p.motionBits[2]);
        tagFloatBits(out, "Health", 0x42000000);
        tagByte(out, "LeftHanded", p.leftHanded);
        tagDoubleBits(out, "fall_distance", 0x0000000000000000L);
        tagShort(out, "Air", 300);
        tagByte(out, "OnGround", 0);
        header(out, TAG_LIST, "Rotation");
        out.writeByte(5);
        out.writeInt(2);
        out.writeInt(p.rotationBits[0]);
        out.writeInt(p.rotationBits[1]);
        tagInt(out, "current_impulse_context_reset_grace_time", 0);
        tagLong(out, "LastPoseTick", 0L);
        header(out, TAG_LIST, "Pos");
        out.writeByte(6);
        out.writeInt(3);
        out.writeLong(p.positionBits[0]);
        out.writeLong(p.positionBits[1]);
        out.writeLong(p.positionBits[2]);
        tagShort(out, "Fire", 0);
        tagInt(out, "Temper", 0);
        tagByte(out, "CanPickUpLoot", 0);
        header(out, TAG_LIST, "attributes");
        out.writeByte(10);
        out.writeInt(1);
        tagString(out, "id", "minecraft:follow_range");
        header(out, TAG_LIST, "modifiers");
        out.writeByte(10);
        out.writeInt(1);
        tagDoubleBits(out, "amount", p.followRangeBonusBits);
        tagString(out, "id", "minecraft:random_spawn_bonus");
        tagString(out, "operation", "add_multiplied_base");
        out.writeByte(TAG_END);
        tagDoubleBits(out, "base", 0x4030000000000000L);
        out.writeByte(TAG_END);
        tagShort(out, "HurtTime", 0);
        out.writeByte(TAG_END);
    }

    private static void writeVillager(DataOutputStream out, Program p) throws IOException {
        out.writeByte(TAG_COMPOUND);
        out.writeUTF("");
        tagByte(out, "AgeLocked", 0);
        header(out, TAG_COMPOUND, "Brain");
        header(out, TAG_COMPOUND, "memories");
        out.writeByte(TAG_END);
        out.writeByte(TAG_END);
        tagByte(out, "FoodLevel", 0);
        tagByte(out, "Invulnerable", 0);
        tagByte(out, "FallFlying", 0);
        tagInt(out, "ForcedAge", 0);
        header(out, TAG_LIST, "Gossips");
        out.writeByte(0);
        out.writeInt(0);
        tagInt(out, "PortalCooldown", 0);
        tagFloatBits(out, "AbsorptionAmount", 0x00000000);
        tagLong(out, "LastRestock", 0L);
        tagShort(out, "DeathTime", 0);
        tagInt(out, "Xp", 0);
        tagLong(out, "LastGossipDecay", 0L);
        tagByte(out, "PersistenceRequired", 0);
        tagString(out, "id", "minecraft:villager");
        tagInt(out, "Age", p.ageTicks);
        header(out, TAG_LIST, "Motion");
        out.writeByte(6);
        out.writeInt(3);
        out.writeLong(p.motionBits[0]);
        out.writeLong(p.motionBits[1]);
        out.writeLong(p.motionBits[2]);
        tagFloatBits(out, "Health", 0x41a00000);
        tagByte(out, "LeftHanded", p.leftHanded);
        tagDoubleBits(out, "fall_distance", 0x0000000000000000L);
        tagShort(out, "Air", 300);
        tagByte(out, "OnGround", 1);
        header(out, TAG_LIST, "Rotation");
        out.writeByte(5);
        out.writeInt(2);
        out.writeInt(p.rotationBits[0]);
        out.writeInt(p.rotationBits[1]);
        tagInt(out, "current_impulse_context_reset_grace_time", 0);
        tagInt(out, "RestocksToday", 0);
        header(out, TAG_LIST, "Pos");
        out.writeByte(6);
        out.writeInt(3);
        out.writeLong(p.positionBits[0]);
        out.writeLong(p.positionBits[1]);
        out.writeLong(p.positionBits[2]);
        tagShort(out, "Fire", -1);
        tagByte(out, "CanPickUpLoot", 1);
        header(out, TAG_COMPOUND, "VillagerData");
        tagString(out, "profession", p.villagerProfession);
        tagInt(out, "level", 1);
        tagString(out, "type", p.villagerType);
        out.writeByte(TAG_END);
        header(out, TAG_LIST, "attributes");
        out.writeByte(10);
        out.writeInt(1);
        tagString(out, "id", "minecraft:follow_range");
        header(out, TAG_LIST, "modifiers");
        out.writeByte(10);
        out.writeInt(1);
        tagDoubleBits(out, "amount", p.followRangeBonusBits);
        tagString(out, "id", "minecraft:random_spawn_bonus");
        tagString(out, "operation", "add_multiplied_base");
        out.writeByte(TAG_END);
        tagDoubleBits(out, "base", 0x4030000000000000L);
        out.writeByte(TAG_END);
        tagByte(out, "VillagerDataFinalized", 1);
        tagShort(out, "HurtTime", 0);
        header(out, TAG_LIST, "Inventory");
        out.writeByte(0);
        out.writeInt(0);
        out.writeByte(TAG_END);
    }

    private static void writeHorse(DataOutputStream out, Program p) throws IOException {
        out.writeByte(TAG_COMPOUND);
        out.writeUTF("");
        tagByte(out, "AgeLocked", 0);
        header(out, TAG_COMPOUND, "Brain");
        header(out, TAG_COMPOUND, "memories");
        out.writeByte(TAG_END);
        out.writeByte(TAG_END);
        tagByte(out, "Tame", 0);
        tagByte(out, "Invulnerable", 0);
        tagByte(out, "FallFlying", 0);
        tagInt(out, "ForcedAge", 0);
        tagInt(out, "PortalCooldown", 0);
        tagFloatBits(out, "AbsorptionAmount", 0x00000000);
        tagByte(out, "Bred", 0);
        tagInt(out, "InLove", 0);
        tagByte(out, "EatingHaystack", 0);
        tagShort(out, "DeathTime", 0);
        tagByte(out, "PersistenceRequired", 1);
        tagString(out, "id", "minecraft:horse");
        tagInt(out, "Age", 0);
        header(out, TAG_LIST, "Motion");
        out.writeByte(6);
        out.writeInt(3);
        out.writeLong(p.motionBits[0]);
        out.writeLong(p.motionBits[1]);
        out.writeLong(p.motionBits[2]);
        tagFloatBits(out, "Health", 0x41980000);
        tagByte(out, "LeftHanded", p.leftHanded);
        tagDoubleBits(out, "fall_distance", 0x0000000000000000L);
        tagShort(out, "Air", 300);
        tagByte(out, "OnGround", 1);
        header(out, TAG_LIST, "Rotation");
        out.writeByte(5);
        out.writeInt(2);
        out.writeInt(p.rotationBits[0]);
        out.writeInt(p.rotationBits[1]);
        tagInt(out, "current_impulse_context_reset_grace_time", 0);
        tagInt(out, "Variant", p.horseVariant);
        header(out, TAG_LIST, "Pos");
        out.writeByte(6);
        out.writeInt(3);
        out.writeLong(p.positionBits[0]);
        out.writeLong(p.positionBits[1]);
        out.writeLong(p.positionBits[2]);
        tagShort(out, "Fire", -1);
        tagInt(out, "Temper", 0);
        tagByte(out, "CanPickUpLoot", 0);
        header(out, TAG_LIST, "attributes");
        out.writeByte(10);
        out.writeInt(8);
        tagString(out, "id", "minecraft:movement_speed");
        tagDoubleBits(out, "base", p.horseMovementSpeedBits);
        out.writeByte(TAG_END);
        tagString(out, "id", "minecraft:armor_toughness");
        tagDoubleBits(out, "base", 0x0000000000000000L);
        out.writeByte(TAG_END);
        tagString(out, "id", "minecraft:jump_strength");
        tagDoubleBits(out, "base", p.horseJumpStrengthBits);
        out.writeByte(TAG_END);
        tagString(out, "id", "minecraft:follow_range");
        header(out, TAG_LIST, "modifiers");
        out.writeByte(10);
        out.writeInt(1);
        tagDoubleBits(out, "amount", p.followRangeBonusBits);
        tagString(out, "id", "minecraft:random_spawn_bonus");
        tagString(out, "operation", "add_multiplied_base");
        out.writeByte(TAG_END);
        tagDoubleBits(out, "base", 0x4030000000000000L);
        out.writeByte(TAG_END);
        tagString(out, "id", "minecraft:max_health");
        tagDoubleBits(out, "base", p.horseMaxHealthBits);
        out.writeByte(TAG_END);
        tagString(out, "id", "minecraft:attack_knockback");
        tagDoubleBits(out, "base", 0x0000000000000000L);
        out.writeByte(TAG_END);
        tagString(out, "id", "minecraft:armor");
        tagDoubleBits(out, "base", 0x0000000000000000L);
        out.writeByte(TAG_END);
        tagString(out, "id", "minecraft:knockback_resistance");
        tagDoubleBits(out, "base", 0x0000000000000000L);
        out.writeByte(TAG_END);
        tagShort(out, "HurtTime", 0);
        out.writeByte(TAG_END);
    }

    private static void writeSheep(DataOutputStream out, Program p) throws IOException {
        out.writeByte(TAG_COMPOUND);
        out.writeUTF("");
        tagByte(out, "AgeLocked", 0);
        header(out, TAG_COMPOUND, "Brain");
        header(out, TAG_COMPOUND, "memories");
        out.writeByte(TAG_END);
        out.writeByte(TAG_END);
        tagByte(out, "Invulnerable", 0);
        tagByte(out, "FallFlying", 0);
        tagInt(out, "ForcedAge", 0);
        tagInt(out, "PortalCooldown", 0);
        tagFloatBits(out, "AbsorptionAmount", 0x00000000);
        tagInt(out, "InLove", 0);
        tagShort(out, "DeathTime", 0);
        tagByte(out, "PersistenceRequired", 1);
        tagString(out, "id", "minecraft:sheep");
        tagInt(out, "Age", 0);
        header(out, TAG_LIST, "Motion");
        out.writeByte(6);
        out.writeInt(3);
        out.writeLong(p.motionBits[0]);
        out.writeLong(p.motionBits[1]);
        out.writeLong(p.motionBits[2]);
        tagFloatBits(out, "Health", 0x41000000);
        tagByte(out, "Color", p.sheepColor);
        tagByte(out, "LeftHanded", p.leftHanded);
        tagDoubleBits(out, "fall_distance", 0x0000000000000000L);
        tagShort(out, "Air", 300);
        tagByte(out, "OnGround", 1);
        header(out, TAG_LIST, "Rotation");
        out.writeByte(5);
        out.writeInt(2);
        out.writeInt(p.rotationBits[0]);
        out.writeInt(p.rotationBits[1]);
        tagInt(out, "current_impulse_context_reset_grace_time", 0);
        header(out, TAG_LIST, "Pos");
        out.writeByte(6);
        out.writeInt(3);
        out.writeLong(p.positionBits[0]);
        out.writeLong(p.positionBits[1]);
        out.writeLong(p.positionBits[2]);
        tagShort(out, "Fire", -1);
        tagByte(out, "CanPickUpLoot", 0);
        header(out, TAG_LIST, "attributes");
        out.writeByte(10);
        out.writeInt(7);
        tagString(out, "id", "minecraft:movement_speed");
        tagDoubleBits(out, "base", 0x3fcd70a3e0000000L);
        out.writeByte(TAG_END);
        tagString(out, "id", "minecraft:armor_toughness");
        tagDoubleBits(out, "base", 0x0000000000000000L);
        out.writeByte(TAG_END);
        tagString(out, "id", "minecraft:follow_range");
        header(out, TAG_LIST, "modifiers");
        out.writeByte(10);
        out.writeInt(1);
        tagDoubleBits(out, "amount", p.followRangeBonusBits);
        tagString(out, "id", "minecraft:random_spawn_bonus");
        tagString(out, "operation", "add_multiplied_base");
        out.writeByte(TAG_END);
        tagDoubleBits(out, "base", 0x4030000000000000L);
        out.writeByte(TAG_END);
        tagString(out, "id", "minecraft:max_health");
        tagDoubleBits(out, "base", 0x4020000000000000L);
        out.writeByte(TAG_END);
        tagString(out, "id", "minecraft:attack_knockback");
        tagDoubleBits(out, "base", 0x0000000000000000L);
        out.writeByte(TAG_END);
        tagString(out, "id", "minecraft:armor");
        tagDoubleBits(out, "base", 0x0000000000000000L);
        out.writeByte(TAG_END);
        tagString(out, "id", "minecraft:knockback_resistance");
        tagDoubleBits(out, "base", 0x0000000000000000L);
        out.writeByte(TAG_END);
        tagByte(out, "Sheared", 0);
        tagShort(out, "HurtTime", 0);
        out.writeByte(TAG_END);
    }

    private static void writeCow(DataOutputStream out, Program p) throws IOException {
        out.writeByte(TAG_COMPOUND);
        out.writeUTF("");
        tagByte(out, "AgeLocked", 0);
        header(out, TAG_COMPOUND, "Brain");
        header(out, TAG_COMPOUND, "memories");
        out.writeByte(TAG_END);
        out.writeByte(TAG_END);
        tagByte(out, "Invulnerable", 0);
        tagByte(out, "FallFlying", 0);
        tagInt(out, "ForcedAge", 0);
        tagString(out, "sound_variant", p.cowSoundVariant);
        tagInt(out, "PortalCooldown", 0);
        tagFloatBits(out, "AbsorptionAmount", 0x00000000);
        tagInt(out, "InLove", 0);
        tagShort(out, "DeathTime", 0);
        tagString(out, "variant", p.cowVariant);
        tagByte(out, "PersistenceRequired", 1);
        tagString(out, "id", "minecraft:cow");
        tagInt(out, "Age", 0);
        header(out, TAG_LIST, "Motion");
        out.writeByte(6);
        out.writeInt(3);
        out.writeLong(p.motionBits[0]);
        out.writeLong(p.motionBits[1]);
        out.writeLong(p.motionBits[2]);
        tagFloatBits(out, "Health", 0x41200000);
        tagByte(out, "LeftHanded", p.leftHanded);
        tagDoubleBits(out, "fall_distance", 0x0000000000000000L);
        tagShort(out, "Air", 300);
        tagByte(out, "OnGround", 1);
        header(out, TAG_LIST, "Rotation");
        out.writeByte(5);
        out.writeInt(2);
        out.writeInt(p.rotationBits[0]);
        out.writeInt(p.rotationBits[1]);
        tagInt(out, "current_impulse_context_reset_grace_time", 0);
        header(out, TAG_LIST, "Pos");
        out.writeByte(6);
        out.writeInt(3);
        out.writeLong(p.positionBits[0]);
        out.writeLong(p.positionBits[1]);
        out.writeLong(p.positionBits[2]);
        tagShort(out, "Fire", -1);
        tagByte(out, "CanPickUpLoot", 0);
        header(out, TAG_LIST, "attributes");
        out.writeByte(10);
        out.writeInt(7);
        tagString(out, "id", "minecraft:movement_speed");
        tagDoubleBits(out, "base", 0x3fc99999a0000000L);
        out.writeByte(TAG_END);
        tagString(out, "id", "minecraft:armor_toughness");
        tagDoubleBits(out, "base", 0x0000000000000000L);
        out.writeByte(TAG_END);
        tagString(out, "id", "minecraft:follow_range");
        header(out, TAG_LIST, "modifiers");
        out.writeByte(10);
        out.writeInt(1);
        tagDoubleBits(out, "amount", p.followRangeBonusBits);
        tagString(out, "id", "minecraft:random_spawn_bonus");
        tagString(out, "operation", "add_multiplied_base");
        out.writeByte(TAG_END);
        tagDoubleBits(out, "base", 0x4030000000000000L);
        out.writeByte(TAG_END);
        tagString(out, "id", "minecraft:max_health");
        tagDoubleBits(out, "base", 0x4024000000000000L);
        out.writeByte(TAG_END);
        tagString(out, "id", "minecraft:attack_knockback");
        tagDoubleBits(out, "base", 0x0000000000000000L);
        out.writeByte(TAG_END);
        tagString(out, "id", "minecraft:armor");
        tagDoubleBits(out, "base", 0x0000000000000000L);
        out.writeByte(TAG_END);
        tagString(out, "id", "minecraft:knockback_resistance");
        tagDoubleBits(out, "base", 0x0000000000000000L);
        out.writeByte(TAG_END);
        tagShort(out, "HurtTime", 0);
        out.writeByte(TAG_END);
    }

    private static void writePig(DataOutputStream out, Program p) throws IOException {
        out.writeByte(TAG_COMPOUND);
        out.writeUTF("");
        tagByte(out, "AgeLocked", 0);
        header(out, TAG_COMPOUND, "Brain");
        header(out, TAG_COMPOUND, "memories");
        out.writeByte(TAG_END);
        out.writeByte(TAG_END);
        tagByte(out, "Invulnerable", 0);
        tagByte(out, "FallFlying", 0);
        tagInt(out, "ForcedAge", 0);
        tagString(out, "sound_variant", p.pigSoundVariant);
        tagInt(out, "PortalCooldown", 0);
        tagFloatBits(out, "AbsorptionAmount", 0x00000000);
        tagInt(out, "InLove", 0);
        tagShort(out, "DeathTime", 0);
        tagString(out, "variant", p.pigVariant);
        tagByte(out, "PersistenceRequired", 1);
        tagString(out, "id", "minecraft:pig");
        tagInt(out, "Age", 0);
        header(out, TAG_LIST, "Motion");
        out.writeByte(6);
        out.writeInt(3);
        out.writeLong(p.motionBits[0]);
        out.writeLong(p.motionBits[1]);
        out.writeLong(p.motionBits[2]);
        tagFloatBits(out, "Health", 0x41200000);
        tagByte(out, "LeftHanded", p.leftHanded);
        tagDoubleBits(out, "fall_distance", 0x0000000000000000L);
        tagShort(out, "Air", 300);
        tagByte(out, "OnGround", 1);
        header(out, TAG_LIST, "Rotation");
        out.writeByte(5);
        out.writeInt(2);
        out.writeInt(p.rotationBits[0]);
        out.writeInt(p.rotationBits[1]);
        tagInt(out, "current_impulse_context_reset_grace_time", 0);
        header(out, TAG_LIST, "Pos");
        out.writeByte(6);
        out.writeInt(3);
        out.writeLong(p.positionBits[0]);
        out.writeLong(p.positionBits[1]);
        out.writeLong(p.positionBits[2]);
        tagShort(out, "Fire", -1);
        tagByte(out, "CanPickUpLoot", 0);
        header(out, TAG_LIST, "attributes");
        out.writeByte(10);
        out.writeInt(7);
        tagString(out, "id", "minecraft:movement_speed");
        tagDoubleBits(out, "base", 0x3fd0000000000000L);
        out.writeByte(TAG_END);
        tagString(out, "id", "minecraft:armor_toughness");
        tagDoubleBits(out, "base", 0x0000000000000000L);
        out.writeByte(TAG_END);
        tagString(out, "id", "minecraft:follow_range");
        header(out, TAG_LIST, "modifiers");
        out.writeByte(10);
        out.writeInt(1);
        tagDoubleBits(out, "amount", p.followRangeBonusBits);
        tagString(out, "id", "minecraft:random_spawn_bonus");
        tagString(out, "operation", "add_multiplied_base");
        out.writeByte(TAG_END);
        tagDoubleBits(out, "base", 0x4030000000000000L);
        out.writeByte(TAG_END);
        tagString(out, "id", "minecraft:max_health");
        tagDoubleBits(out, "base", 0x4024000000000000L);
        out.writeByte(TAG_END);
        tagString(out, "id", "minecraft:attack_knockback");
        tagDoubleBits(out, "base", 0x0000000000000000L);
        out.writeByte(TAG_END);
        tagString(out, "id", "minecraft:armor");
        tagDoubleBits(out, "base", 0x0000000000000000L);
        out.writeByte(TAG_END);
        tagString(out, "id", "minecraft:knockback_resistance");
        tagDoubleBits(out, "base", 0x0000000000000000L);
        out.writeByte(TAG_END);
        tagShort(out, "HurtTime", 0);
        out.writeByte(TAG_END);
    }

    private static void writeArmorStand(DataOutputStream out, Program p) throws IOException {
        out.writeByte(TAG_COMPOUND);
        out.writeUTF("");
        header(out, TAG_COMPOUND, "Brain");
        header(out, TAG_COMPOUND, "memories");
        out.writeByte(TAG_END);
        out.writeByte(TAG_END);
        tagByte(out, "Invulnerable", 0);
        tagByte(out, "FallFlying", 0);
        tagByte(out, "ShowArms", 0);
        tagInt(out, "PortalCooldown", 0);
        tagFloatBits(out, "AbsorptionAmount", 0x00000000);
        tagInt(out, "DisabledSlots", 0);
        tagShort(out, "DeathTime", 0);
        header(out, TAG_COMPOUND, "Pose");
        header(out, TAG_LIST, "Head");
        out.writeByte(5);
        out.writeInt(3);
        out.writeInt(p.poseHeadBits[0]);
        out.writeInt(p.poseHeadBits[1]);
        out.writeInt(p.poseHeadBits[2]);
        header(out, TAG_LIST, "Body");
        out.writeByte(5);
        out.writeInt(3);
        out.writeInt(p.poseBodyBits[0]);
        out.writeInt(p.poseBodyBits[1]);
        out.writeInt(p.poseBodyBits[2]);
        out.writeByte(TAG_END);
        tagString(out, "id", "minecraft:armor_stand");
        tagByte(out, "Invisible", 0);
        header(out, TAG_LIST, "Motion");
        out.writeByte(6);
        out.writeInt(3);
        out.writeLong(p.motionBits[0]);
        out.writeLong(p.motionBits[1]);
        out.writeLong(p.motionBits[2]);
        tagByte(out, "Small", 0);
        tagFloatBits(out, "Health", 0x41a00000);
        header(out, TAG_COMPOUND, "equipment");
        header(out, TAG_COMPOUND, p.armorStandEquipmentSlot);
        tagInt(out, "count", 1);
        tagString(out, "id", p.armorStandEquipmentItem);
        out.writeByte(TAG_END);
        out.writeByte(TAG_END);
        tagDoubleBits(out, "fall_distance", 0x0000000000000000L);
        tagShort(out, "Air", 300);
        tagByte(out, "OnGround", 1);
        header(out, TAG_LIST, "Rotation");
        out.writeByte(5);
        out.writeInt(2);
        out.writeInt(p.rotationBits[0]);
        out.writeInt(p.rotationBits[1]);
        tagInt(out, "current_impulse_context_reset_grace_time", 0);
        header(out, TAG_LIST, "Pos");
        out.writeByte(6);
        out.writeInt(3);
        out.writeLong(p.positionBits[0]);
        out.writeLong(p.positionBits[1]);
        out.writeLong(p.positionBits[2]);
        tagShort(out, "Fire", -1);
        tagByte(out, "NoBasePlate", 0);
        header(out, TAG_LIST, "attributes");
        out.writeByte(10);
        out.writeInt(5);
        tagString(out, "id", "minecraft:movement_speed");
        tagDoubleBits(out, "base", 0x3fe6666660000000L);
        out.writeByte(TAG_END);
        tagString(out, "id", "minecraft:armor_toughness");
        tagDoubleBits(out, "base", 0x0000000000000000L);
        out.writeByte(TAG_END);
        tagString(out, "id", "minecraft:max_health");
        tagDoubleBits(out, "base", 0x4034000000000000L);
        out.writeByte(TAG_END);
        tagString(out, "id", "minecraft:armor");
        tagDoubleBits(out, "base", 0x0000000000000000L);
        out.writeByte(TAG_END);
        tagString(out, "id", "minecraft:knockback_resistance");
        tagDoubleBits(out, "base", 0x0000000000000000L);
        out.writeByte(TAG_END);
        tagShort(out, "HurtTime", 0);
        out.writeByte(TAG_END);
    }

    private static void writeZombieVillager(DataOutputStream out, Program p) throws IOException {
        out.writeByte(TAG_COMPOUND);
        out.writeUTF("");
        header(out, TAG_COMPOUND, "Brain");
        header(out, TAG_COMPOUND, "memories");
        out.writeByte(TAG_END);
        out.writeByte(TAG_END);
        tagByte(out, "IsBaby", 0);
        tagByte(out, "Invulnerable", 0);
        tagByte(out, "FallFlying", 0);
        header(out, TAG_LIST, "Gossips");
        out.writeByte(0);
        out.writeInt(0);
        tagInt(out, "PortalCooldown", 0);
        tagFloatBits(out, "AbsorptionAmount", 0x00000000);
        tagInt(out, "InWaterTime", -1);
        tagShort(out, "DeathTime", 0);
        tagInt(out, "Xp", 0);
        tagByte(out, "PersistenceRequired", 1);
        tagString(out, "id", "minecraft:zombie_villager");
        tagInt(out, "ConversionTime", -1);
        header(out, TAG_LIST, "Motion");
        out.writeByte(6);
        out.writeInt(3);
        out.writeLong(p.motionBits[0]);
        out.writeLong(p.motionBits[1]);
        out.writeLong(p.motionBits[2]);
        tagFloatBits(out, "Health", 0x41a00000);
        tagByte(out, "LeftHanded", p.leftHanded);
        tagDoubleBits(out, "fall_distance", 0x0000000000000000L);
        tagShort(out, "Air", 300);
        tagByte(out, "OnGround", 1);
        header(out, TAG_LIST, "Rotation");
        out.writeByte(5);
        out.writeInt(2);
        out.writeInt(p.rotationBits[0]);
        out.writeInt(p.rotationBits[1]);
        tagInt(out, "current_impulse_context_reset_grace_time", 0);
        header(out, TAG_LIST, "Pos");
        out.writeByte(6);
        out.writeInt(3);
        out.writeLong(p.positionBits[0]);
        out.writeLong(p.positionBits[1]);
        out.writeLong(p.positionBits[2]);
        tagByte(out, "CanBreakDoors", 0);
        tagShort(out, "Fire", -1);
        tagByte(out, "CanPickUpLoot", 0);
        header(out, TAG_COMPOUND, "VillagerData");
        tagString(out, "profession", p.villagerProfession);
        tagInt(out, "level", 1);
        tagString(out, "type", "minecraft:plains");
        out.writeByte(TAG_END);
        header(out, TAG_LIST, "attributes");
        out.writeByte(10);
        out.writeInt(4);
        tagString(out, "id", "minecraft:movement_speed");
        tagDoubleBits(out, "base", 0x3fcd70a3e0000000L);
        out.writeByte(TAG_END);
        tagString(out, "id", "minecraft:follow_range");
        header(out, TAG_LIST, "modifiers");
        out.writeByte(10);
        out.writeInt(1);
        tagDoubleBits(out, "amount", p.followRangeBonusBits);
        tagString(out, "id", "minecraft:random_spawn_bonus");
        tagString(out, "operation", "add_multiplied_base");
        out.writeByte(TAG_END);
        tagDoubleBits(out, "base", 0x4041800000000000L);
        out.writeByte(TAG_END);
        tagString(out, "id", "minecraft:knockback_resistance");
        header(out, TAG_LIST, "modifiers");
        out.writeByte(10);
        out.writeInt(1);
        tagString(out, "id", "minecraft:random_spawn_bonus");
        tagString(out, "operation", "add_value");
        out.writeByte(TAG_END);
        tagDoubleBits(out, "base", 0x0000000000000000L);
        out.writeByte(TAG_END);
        tagString(out, "id", "minecraft:spawn_reinforcements");
        out.writeByte(TAG_END);
        tagByte(out, "VillagerDataFinalized", 1);
        tagShort(out, "HurtTime", 0);
        tagInt(out, "DrownedConversionTime", -1);
        out.writeByte(TAG_END);
    }}
