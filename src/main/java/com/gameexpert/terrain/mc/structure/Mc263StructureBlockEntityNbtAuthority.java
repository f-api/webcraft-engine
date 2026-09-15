package com.gameexpert.terrain.mc.structure;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.SequencedMap;

/**
 * Family-agnostic canonical block-entity NBT authority for the pinned 26.3 FEATURES product.
 *
 * <p>Every generated family (village, ancient city, woodland mansion, trial chambers, pillager
 * outpost, abandoned camp, …) creates its block entities from the same vanilla
 * save-with-full-metadata rendering: the block-entity type decides the tag program, and the
 * template DATA facts plus the live placement position and loot seed decide the values. This class
 * owns that rendering exactly once, so a family adapter only has to translate its authenticated
 * template DATA row into one {@link Facts} value.
 *
 * <p>Production discipline: nothing here reads a settlement/BENT corpus. The corpora
 * (village 75 rows, ancient city 69 raw bodies, woodland mansion marker chests, trial chambers
 * 136 rows) are verification oracles bound in tests, never inputs on the production path.
 *
 * <p>Field order is vanilla's, not alphabetical and not the codec's declaration order, and must
 * not be "tidied": {@code net.minecraft.nbt.CompoundTag} backs its tags with a plain
 * {@code java.util.HashMap}, so a saved compound is written in that map's iteration order over its
 * key set. Every program below therefore emits its keys in {@code HashMap} order — a property
 * {@code Mc263StructureBlockEntityNbtAuthorityTest} derives from a real {@code HashMap} rather than
 * transcribing, and the per-family corpora then confirm byte-for-byte.
 */
public final class Mc263StructureBlockEntityNbtAuthority {
    private static final int END = 0;
    private static final int BYTE = 1;
    private static final int INT = 3;
    private static final int LONG = 4;
    private static final int FLOAT = 5;
    private static final int STRING = 8;
    private static final int LIST = 9;
    private static final int COMPOUND = 10;
    private static final int INT_ARRAY = 11;

    private Mc263StructureBlockEntityNbtAuthority() {}

    /** One authenticated block-entity DATA fact, already resolved to its canonical semantic. */
    public sealed interface Facts {
        String blockEntityType();
    }

    /** Bell, lectern, banner, skeleton skull and every other value-free positioned entity. */
    public record Positioned(String blockEntityType) implements Facts {
        public Positioned { requireType(blockEntityType); }
    }

    /**
     * Copper golem statue custom data.
     *
     * <p>Pinned-jar {@code javap -p -c} shows
     * {@code CopperGolemStatueBlockEntity} does not override {@code saveAdditional}; its full save
     * therefore contains only {@code BlockEntity.saveWithoutMetadata}'s empty
     * {@code components}, followed by {@code saveMetadata}'s {@code id/x/y/z}. The statue pose,
     * facing, waterlogging, weathering and waxed identity are block-state facts, not NBT fields.
     * {@code CompoundTag}'s {@code HashMap} iteration emits those save fields as
     * {@code components,x,y,z,id}.</p>
     */
    public record CopperGolemStatue(String blockEntityType) implements Facts {
        public CopperGolemStatue { requireType(blockEntityType); }
    }

    /** Deferred-loot container: the table is DATA, the seed is a live placement draw. */
    public record LootContainer(String blockEntityType, String lootTable, long lootSeed)
            implements Facts {
        public LootContainer {
            requireType(blockEntityType);
            Objects.requireNonNull(lootTable, "canonical loot table");
        }
    }

    /** Item container with resolved contents; an empty list is the vanilla empty-list encoding. */
    public record ItemContainer(String blockEntityType, List<ItemStack> items) implements Facts {
        public ItemContainer {
            requireType(blockEntityType);
            items = List.copyOf(items);
        }
    }

    /** One resolved slot of an {@link ItemContainer} or furnace. */
    public record ItemStack(int slot, String item, int count) {
        public ItemStack {
            Objects.requireNonNull(item, "canonical item id");
            if (slot < 0 || slot > 127 || count <= 0) {
                throw new IllegalArgumentException("canonical item slot/count out of domain");
            }
        }
    }

    /** Comparator with its authenticated saved output signal. */
    public record Comparator(String blockEntityType, int outputSignal) implements Facts {
        public Comparator { requireType(blockEntityType); }
    }

    /** Freshly placed sculk sensor: no listener source, no vibration. */
    public record SculkSensor(String blockEntityType) implements Facts {
        public SculkSensor { requireType(blockEntityType); }
    }

    /** Freshly placed sculk catalyst: no spreading cursors yet. */
    public record SculkCatalyst(String blockEntityType) implements Facts {
        public SculkCatalyst { requireType(blockEntityType); }
    }

    /** Freshly placed sculk shrieker with its saved warning level. */
    public record SculkShrieker(String blockEntityType, int warningLevel) implements Facts {
        public SculkShrieker { requireType(blockEntityType); }
    }

    /** Campfire with its four-slot cooking schedule. */
    public record Campfire(String blockEntityType, List<ItemStack> items,
            int[] cookingTimes, int[] cookingTotalTimes) implements Facts {
        public Campfire {
            requireType(blockEntityType);
            items = List.copyOf(items);
            cookingTimes = cookingTimes.clone();
            cookingTotalTimes = cookingTotalTimes.clone();
        }
    }

    /** Brewing stand in its freshly placed state. */
    public record BrewingStand(String blockEntityType, List<ItemStack> items, int fuel,
            int brewTime, int totalBrewTime, int totalFuel) implements Facts {
        public BrewingStand {
            requireType(blockEntityType);
            items = List.copyOf(items);
        }
    }

    /** Furnace family (furnace, smoker, blast furnace) with its authenticated burn state. */
    public record Furnace(String blockEntityType, List<ItemStack> items, int litTotalTime,
            int litTimeRemaining, int cookingTimeSpent, int cookingTotalTime,
            SequencedMap<String, Integer> recipesUsed) implements Facts {
        public Furnace {
            requireType(blockEntityType);
            items = List.copyOf(items);
            Objects.requireNonNull(recipesUsed, "canonical RecipesUsed");
        }
    }

    /**
     * Hopper: an {@link ItemContainer} plus {@code TransferCooldown}.
     *
     * <p>{@code net.minecraft.world.level.block.entity.HopperBlockEntity} is the only container in
     * the pinned build that adds {@code TransferCooldown} to the container program.</p>
     */
    public record Hopper(String blockEntityType, List<ItemStack> items, int transferCooldown)
            implements Facts {
        public Hopper {
            requireType(blockEntityType);
            items = List.copyOf(items);
        }
    }

    /** One {@code count}/{@code id} item compound (a non-slotted single stack). */
    public record Item(String item, int count) {
        public Item {
            Objects.requireNonNull(item, "canonical item id");
            if (count <= 0) throw new IllegalArgumentException("canonical item count out of domain");
        }
    }

    /**
     * Decorated-pot decorations.
     *
     * <p>{@code net.minecraft.world.level.block.entity.PotDecorations} declares exactly the four
     * faces {@code back}, {@code left}, {@code right}, {@code front}; the emitted order below is
     * that key set in {@code CompoundTag}'s map order, not the codec's declaration order.</p>
     */
    public record PotSherds(String left, String back, String right, String front) {
        public PotSherds {
            Objects.requireNonNull(left, "canonical left sherd");
            Objects.requireNonNull(back, "canonical back sherd");
            Objects.requireNonNull(right, "canonical right sherd");
            Objects.requireNonNull(front, "canonical front sherd");
        }
    }

    /**
     * Decorated pot holding a resolved single item.
     *
     * <p>{@code net.minecraft.world.level.block.entity.DecoratedPotBlockEntity} saves {@code item}
     * and {@code sherds}.</p>
     */
    public record DecoratedPot(String blockEntityType, Item item, PotSherds sherds)
            implements Facts {
        public DecoratedPot {
            requireType(blockEntityType);
            Objects.requireNonNull(item, "canonical decorated-pot item");
            Objects.requireNonNull(sherds, "canonical decorated-pot sherds");
        }
    }

    /**
     * Decorated pot whose contents are a deferred loot table.
     *
     * <p>{@code DecoratedPotBlockEntity} is a {@code RandomizableContainer}, so
     * {@code trySaveLootTable} contributes {@code LootTable} to the same program the sherds are
     * written into. A decorated pot draws no placement seed, so no {@code LootTableSeed} is
     * written.</p>
     */
    public record LootDecoratedPot(String blockEntityType, String lootTable, PotSherds sherds)
            implements Facts {
        public LootDecoratedPot {
            requireType(blockEntityType);
            Objects.requireNonNull(lootTable, "canonical loot table");
            Objects.requireNonNull(sherds, "canonical decorated-pot sherds");
        }
    }

    /**
     * Freshly placed vault.
     *
     * <p>{@code net.minecraft.world.level.block.entity.vault.VaultBlockEntity} saves
     * {@code config}, {@code server_data} and {@code shared_data}; a generated vault has neither
     * server nor shared state yet, so both are empty compounds. Inside {@code config},
     * {@code net.minecraft.world.level.block.entity.vault.VaultConfig}'s codec builds
     * {@code loot_table}, {@code activation_range} and {@code deactivation_range} against
     * {@code VaultConfig.DEFAULT} (elided when equal) while {@code key_item} is a plain field that
     * is always written and {@code override_loot_table_to_display} is an absent Optional.
     * {@code VaultConfig.DEFAULT}'s loot table is {@code BuiltInLootTables.TRIAL_CHAMBERS_REWARD}.
     */
    public record Vault(String blockEntityType, Item keyItem, String lootTable) implements Facts {
        /** {@code VaultConfig.DEFAULT.lootTable()} — elided from {@code config} when unchanged. */
        public static final String DEFAULT_LOOT_TABLE = "minecraft:chests/trial_chambers/reward";

        public Vault {
            requireType(blockEntityType);
            Objects.requireNonNull(keyItem, "canonical vault key item");
            Objects.requireNonNull(lootTable, "canonical vault loot table");
        }
    }

    /**
     * Freshly placed trial spawner.
     *
     * <p>{@code net.minecraft.world.level.block.entity.trialspawner.TrialSpawner$FullConfig} saves
     * {@code normal_config} and {@code ominous_config}; a generated spawner carries only its two
     * registry-key configs and no runtime state.</p>
     */
    public record TrialSpawner(String blockEntityType, String normalConfig, String ominousConfig)
            implements Facts {
        public TrialSpawner {
            requireType(blockEntityType);
            Objects.requireNonNull(normalConfig, "canonical normal_config");
            Objects.requireNonNull(ominousConfig, "canonical ominous_config");
        }
    }

    /**
     * The {@code trySaveLootTable} custom-data payload on its own — no position, no {@code id}.
     *
     * <p>{@code net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity} writes
     * {@code LootTable} and, only when the seed is non-zero, {@code LootTableSeed}. A structure
     * piece that creates a chest through {@code StructurePiece.createChest} hands the world exactly
     * this custom-data compound rather than a saved block entity.</p>
     */
    public record LootTableCustomData(String blockEntityType, String lootTable, long lootSeed)
            implements Facts {
        public LootTableCustomData {
            requireType(blockEntityType);
            Objects.requireNonNull(lootTable, "canonical loot table");
        }
    }

    /**
     * The ominous banner a pillager outpost's watchtower loads onto its white wall banner.
     *
     * <p>{@code net.minecraft.world.level.block.entity.BannerBlockEntity} saves {@code patterns}
     * (its {@code BannerPatternLayers}) plus the {@code components} that
     * {@code Raid.getOminousBannerInstance} stamped onto the loaded item —
     * {@code minecraft:item_name} ({@code block.minecraft.ominous_banner}) and
     * {@code minecraft:rarity} ({@code uncommon}). The banner carries no custom name, so no
     * {@code CustomName} tag is written.</p>
     */
    public record OminousBanner(String blockEntityType) implements Facts {
        public OminousBanner { requireType(blockEntityType); }
    }

    /** Blank sign: four empty front/back message lines, black, unlit, unwaxed. */
    public record Sign(String blockEntityType) implements Facts {
        public Sign { requireType(blockEntityType); }
    }

    /**
     * Renders the exact save-with-full-metadata payload created after template placement.
     *
     * @param x world X of the placed block entity
     * @param y world Y of the placed block entity
     * @param z world Z of the placed block entity
     * @param facts the authenticated DATA facts for this block entity
     */
    /**
     * Renders a position-free program — a payload vanilla hands to the world as block custom data
     * rather than as a saved block entity.
     *
     * @param facts the authenticated DATA facts for this payload
     */
    public static byte[] render(Facts facts) {
        Objects.requireNonNull(facts, "canonical block-entity facts");
        if (!(facts instanceof LootTableCustomData)) {
            throw new IllegalArgumentException(
                    "canonical block-entity program needs a position: " + facts.blockEntityType());
        }
        return render(0, 0, 0, facts);
    }

    public static byte[] render(int x, int y, int z, Facts facts) {
        Objects.requireNonNull(facts, "canonical block-entity facts");
        byte[] rendered = root(out -> {
            switch (facts) {
                case Positioned value -> {
                    emptyCompound(out, "components");
                    position(out, x, y, z);
                    string(out, "id", value.blockEntityType());
                }
                case CopperGolemStatue value -> {
                    emptyCompound(out, "components");
                    position(out, x, y, z);
                    string(out, "id", value.blockEntityType());
                }
                case LootContainer value -> {
                    string(out, "LootTable", value.lootTable());
                    emptyCompound(out, "components");
                    position(out, x, y, z);
                    string(out, "id", value.blockEntityType());
                    longTag(out, "LootTableSeed", value.lootSeed());
                }
                case ItemContainer value -> {
                    emptyCompound(out, "components");
                    integer(out, "x", x);
                    integer(out, "y", y);
                    items(out, value.items());
                    integer(out, "z", z);
                    string(out, "id", value.blockEntityType());
                }
                case Comparator value -> {
                    emptyCompound(out, "components");
                    position(out, x, y, z);
                    string(out, "id", value.blockEntityType());
                    integer(out, "OutputSignal", value.outputSignal());
                }
                case SculkSensor value -> {
                    emptyCompound(out, "components");
                    integer(out, "last_vibration_frequency", 0);
                    vibrationListener(out);
                    position(out, x, y, z);
                    string(out, "id", value.blockEntityType());
                }
                case SculkCatalyst value -> {
                    emptyList(out, "cursors");
                    emptyCompound(out, "components");
                    position(out, x, y, z);
                    string(out, "id", value.blockEntityType());
                }
                case SculkShrieker value -> {
                    integer(out, "warning_level", value.warningLevel());
                    emptyCompound(out, "components");
                    vibrationListener(out);
                    position(out, x, y, z);
                    string(out, "id", value.blockEntityType());
                }
                case Campfire value -> {
                    emptyCompound(out, "components");
                    integer(out, "x", x);
                    integer(out, "y", y);
                    items(out, value.items());
                    integer(out, "z", z);
                    intArray(out, "CookingTimes", value.cookingTimes());
                    intArray(out, "CookingTotalTimes", value.cookingTotalTimes());
                    string(out, "id", value.blockEntityType());
                }
                case BrewingStand value -> {
                    emptyCompound(out, "components");
                    integer(out, "Fuel", value.fuel());
                    floatTag(out, "speed_multiplier", 1.0F);
                    integer(out, "x", x);
                    integer(out, "y", y);
                    items(out, value.items());
                    integer(out, "z", z);
                    string(out, "id", value.blockEntityType());
                    integer(out, "BrewTime", value.brewTime());
                    integer(out, "total_brew_time", value.totalBrewTime());
                    integer(out, "total_fuel", value.totalFuel());
                }
                case Furnace value -> {
                    integer(out, "lit_total_time", value.litTotalTime());
                    emptyCompound(out, "components");
                    integer(out, "cooking_time_spent", value.cookingTimeSpent());
                    floatTag(out, "speed_multiplier", 1.0F);
                    integer(out, "x", x);
                    integer(out, "y", y);
                    integer(out, "cooking_total_time", value.cookingTotalTime());
                    items(out, value.items());
                    integer(out, "z", z);
                    string(out, "id", value.blockEntityType());
                    integer(out, "lit_time_remaining", value.litTimeRemaining());
                    tag(out, COMPOUND, "RecipesUsed");
                    for (Map.Entry<String, Integer> used : value.recipesUsed().entrySet()) {
                        integer(out, used.getKey(), used.getValue());
                    }
                    out.writeByte(END);
                }
                case Hopper value -> {
                    emptyCompound(out, "components");
                    integer(out, "TransferCooldown", value.transferCooldown());
                    integer(out, "x", x);
                    integer(out, "y", y);
                    items(out, value.items());
                    integer(out, "z", z);
                    string(out, "id", value.blockEntityType());
                }
                case DecoratedPot value -> {
                    item(out, "item", value.item());
                    emptyCompound(out, "components");
                    sherds(out, value.sherds());
                    position(out, x, y, z);
                    string(out, "id", value.blockEntityType());
                }
                case LootDecoratedPot value -> {
                    string(out, "LootTable", value.lootTable());
                    emptyCompound(out, "components");
                    sherds(out, value.sherds());
                    position(out, x, y, z);
                    string(out, "id", value.blockEntityType());
                }
                case Vault value -> {
                    emptyCompound(out, "components");
                    emptyCompound(out, "server_data");
                    position(out, x, y, z);
                    string(out, "id", value.blockEntityType());
                    tag(out, COMPOUND, "config");
                    item(out, "key_item", value.keyItem());
                    if (!Vault.DEFAULT_LOOT_TABLE.equals(value.lootTable())) {
                        string(out, "loot_table", value.lootTable());
                    }
                    out.writeByte(END);
                    emptyCompound(out, "shared_data");
                }
                case TrialSpawner value -> {
                    emptyCompound(out, "components");
                    string(out, "normal_config", value.normalConfig());
                    position(out, x, y, z);
                    string(out, "id", value.blockEntityType());
                    string(out, "ominous_config", value.ominousConfig());
                }
                case LootTableCustomData value -> {
                    string(out, "LootTable", value.lootTable());
                    if (value.lootSeed() != 0L) {
                        longTag(out, "LootTableSeed", value.lootSeed());
                    }
                }
                case OminousBanner value -> {
                    ominousBannerComponents(out);
                    ominousBannerPatterns(out);
                    position(out, x, y, z);
                    string(out, "id", value.blockEntityType());
                }
                case Sign value -> {
                    signText(out, "back_text");
                    emptyCompound(out, "components");
                    byteTag(out, "is_waxed", 0);
                    position(out, x, y, z);
                    string(out, "id", value.blockEntityType());
                    signText(out, "front_text");
                }
            }
        });
        if (rendered.length < 4 || rendered[0] != COMPOUND
                || rendered[rendered.length - 1] != END) {
            throw new IllegalStateException(
                    "canonical block-entity authority emitted invalid binary NBT for "
                            + facts.blockEntityType());
        }
        return rendered;
    }

    private static void requireType(String blockEntityType) {
        Objects.requireNonNull(blockEntityType, "canonical block-entity type");
        if (!blockEntityType.startsWith("minecraft:") || blockEntityType.length() < 11) {
            throw new IllegalArgumentException(
                    "canonical block-entity type is required: " + blockEntityType);
        }
    }

    /** Vanilla's freshly placed vibration listener: no selected event, no delay. */
    private static void vibrationListener(DataOutputStream out) throws IOException {
        tag(out, COMPOUND, "listener");
        tag(out, COMPOUND, "selector");
        longTag(out, "tick", -1L);
        out.writeByte(END);
        integer(out, "event_delay", 0);
        out.writeByte(END);
    }

    private static void emptyList(DataOutputStream out, String name) throws IOException {
        tag(out, LIST, name);
        out.writeByte(END);
        out.writeInt(0);
    }

    /**
     * One blank side of a sign: {@code net.minecraft.world.level.block.entity.SignText} saves
     * {@code messages}, {@code color} and {@code has_glowing_text}, and omits
     * {@code filtered_messages} while it equals {@code messages}.
     */
    private static void signText(DataOutputStream out, String name) throws IOException {
        tag(out, COMPOUND, name);
        byteTag(out, "has_glowing_text", 0);
        string(out, "color", "black");
        stringList(out, "messages", "{\"text\":\"\"}", 4);
        out.writeByte(END);
    }

    /**
     * {@code net.minecraft.world.entity.raid.Raid.getOminousBannerInstance}'s pattern layers, in
     * the order that {@code BannerPatternLayers} stores and saves them.
     */
    private static final String[][] OMINOUS_BANNER_PATTERNS = {
        {"cyan", "minecraft:rhombus"},
        {"light_gray", "minecraft:stripe_bottom"},
        {"gray", "minecraft:stripe_center"},
        {"light_gray", "minecraft:border"},
        {"black", "minecraft:stripe_middle"},
        {"light_gray", "minecraft:half_horizontal"},
        {"light_gray", "minecraft:circle"},
        {"black", "minecraft:border"},
    };

    /** The two item components {@code getOminousBannerInstance} sets on the loaded banner. */
    private static void ominousBannerComponents(DataOutputStream out) throws IOException {
        tag(out, COMPOUND, "components");
        tag(out, COMPOUND, "minecraft:item_name");
        string(out, "translate", "block.minecraft.ominous_banner");
        out.writeByte(END);
        string(out, "minecraft:rarity", "uncommon");
        out.writeByte(END);
    }

    private static void ominousBannerPatterns(DataOutputStream out) throws IOException {
        tag(out, LIST, "patterns");
        out.writeByte(COMPOUND);
        out.writeInt(OMINOUS_BANNER_PATTERNS.length);
        for (String[] layer : OMINOUS_BANNER_PATTERNS) {
            string(out, "color", layer[0]);
            string(out, "pattern", layer[1]);
            out.writeByte(END);
        }
    }

    private static void item(DataOutputStream out, String name, Item value) throws IOException {
        tag(out, COMPOUND, name);
        integer(out, "count", value.count());
        string(out, "id", value.item());
        out.writeByte(END);
    }

    private static void sherds(DataOutputStream out, PotSherds values) throws IOException {
        tag(out, COMPOUND, "sherds");
        sherd(out, "left", values.left());
        sherd(out, "back", values.back());
        sherd(out, "right", values.right());
        sherd(out, "front", values.front());
        out.writeByte(END);
    }

    private static void sherd(DataOutputStream out, String face, String id) throws IOException {
        tag(out, COMPOUND, face);
        string(out, "id", id);
        out.writeByte(END);
    }

    private static void items(DataOutputStream out, List<ItemStack> values) throws IOException {
        tag(out, LIST, "Items");
        out.writeByte(values.isEmpty() ? END : COMPOUND);
        out.writeInt(values.size());
        for (ItemStack item : values) {
            integer(out, "count", item.count());
            tag(out, BYTE, "Slot");
            out.writeByte(item.slot());
            string(out, "id", item.item());
            out.writeByte(END);
        }
    }

    private static byte[] root(Writer writer) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(384);
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeByte(COMPOUND);
            out.writeUTF("");
            writer.write(out);
            out.writeByte(END);
            out.flush();
            return bytes.toByteArray();
        } catch (IOException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static void position(DataOutputStream out, int x, int y, int z) throws IOException {
        integer(out, "x", x);
        integer(out, "y", y);
        integer(out, "z", z);
    }

    private static void tag(DataOutputStream out, int type, String name) throws IOException {
        out.writeByte(type);
        out.writeUTF(name);
    }

    private static void string(DataOutputStream out, String name, String value) throws IOException {
        tag(out, STRING, name);
        out.writeUTF(value);
    }

    private static void integer(DataOutputStream out, String name, int value) throws IOException {
        tag(out, INT, name);
        out.writeInt(value);
    }

    private static void longTag(DataOutputStream out, String name, long value) throws IOException {
        tag(out, LONG, name);
        out.writeLong(value);
    }

    private static void floatTag(DataOutputStream out, String name, float value)
            throws IOException {
        tag(out, FLOAT, name);
        out.writeFloat(value);
    }

    private static void byteTag(DataOutputStream out, String name, int value) throws IOException {
        tag(out, BYTE, name);
        out.writeByte(value);
    }

    private static void emptyCompound(DataOutputStream out, String name) throws IOException {
        tag(out, COMPOUND, name);
        out.writeByte(END);
    }

    private static void intArray(DataOutputStream out, String name, int[] values)
            throws IOException {
        tag(out, INT_ARRAY, name);
        out.writeInt(values.length);
        for (int value : values) out.writeInt(value);
    }

    private static void stringList(DataOutputStream out, String name, String value, int count)
            throws IOException {
        tag(out, LIST, name);
        out.writeByte(STRING);
        out.writeInt(count);
        for (int index = 0; index < count; index++) out.writeUTF(value);
    }

    @FunctionalInterface
    private interface Writer {
        void write(DataOutputStream out) throws IOException;
    }
}
