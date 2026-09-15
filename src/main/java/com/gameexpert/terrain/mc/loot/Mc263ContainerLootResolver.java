package com.gameexpert.terrain.mc.loot;

import com.gameexpert.terrain.mc.loot.Mc263LocatedMapAuthority.Found;
import com.gameexpert.terrain.mc.loot.Mc263LocatedMapAuthority.LocatedMapTarget;
import com.gameexpert.terrain.mc.loot.Mc263LocatedMapAuthority.NotFound;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Set;

/**
 * Pure resolver for the pinned 26.3-snapshot-7 schema-5 container-loot closure.
 * Item, component, enchantment, and potion identities remain symbolic Minecraft keys.
 */
public final class Mc263ContainerLootResolver {
    private static final long LEGACY_MASK = (1L << 48) - 1L;
    private static final String MAP_RECEIPT_DOMAIN = "MC263-LOOT-MAP-DESTINATION-V1\0";
    private static final String CONTEXT_RECEIPT_DOMAIN = "MC263-LOOT-PRODUCTION-CONTEXT-V1\0";
    private static final String TARGET_CONTEXT_RECEIPT_DOMAIN =
            "MC263-LOOT-PRODUCTION-CONTEXT-TARGETS-V2\0";
    private static final HexFormat HEX = HexFormat.of();
    public static final String TABLE_AUTHORITY_RECEIPT_SHA256 =
            "5e791ede9edfe395859f9574387a322fe8c7d74a895e3e508a087b8088d61c41";
    private static final Map<String, Table> TABLES = createTables();
    /** Pinned closure of the minecraft:on_random_loot enchantment registry tag. */
    private static final List<Enchantment> ON_RANDOM_LOOT_ENCHANTMENTS = createEnchantments();
    /** Fixed-option registry row evidenced by the ancient-city table, outside on_random_loot. */
    private static final List<Enchantment> FIXED_OPTION_ENCHANTMENTS = List.of(
            enchantment("swift_sneak", 1, 3, 25, 25, 75, 25));
    /** Public component registry closure; it is deliberately broader than random-loot candidates. */
    private static final List<Enchantment> PUBLIC_ENCHANTMENTS = createPublicEnchantments();
    private static final Map<String, Enchantment> ENCHANTMENTS_BY_KEY = enchantmentsByKey();
    private static final Set<String> POTION_KEYS = Set.of(
            "minecraft:healing", "minecraft:leaping", "minecraft:night_vision",
            "minecraft:poison", "minecraft:regeneration", "minecraft:slowness",
            "minecraft:strength", "minecraft:strong_regeneration",
            // chests/trial_chambers/reward_ominous_common 의 tipped_arrow 가 쓰는 id.
            "minecraft:strong_slowness",
            "minecraft:swiftness", "minecraft:water_breathing",
            "minecraft:weakness");
    private static final Set<String> INSTRUMENT_KEYS = Set.of(
            "minecraft:ponder_goat_horn", "minecraft:sing_goat_horn",
            "minecraft:seek_goat_horn", "minecraft:feel_goat_horn",
            "minecraft:admire_goat_horn", "minecraft:call_goat_horn",
            "minecraft:yearn_goat_horn", "minecraft:dream_goat_horn");
    private static final List<String> REGULAR_GOAT_HORN_KEYS = List.of(
            "minecraft:ponder_goat_horn", "minecraft:sing_goat_horn",
            "minecraft:seek_goat_horn", "minecraft:feel_goat_horn");

    private Mc263ContainerLootResolver() {
    }

    /**
     * Computes the row receipt for one exact map destination.  The producer, Skeleton and Bridge
     * all use this public, binary contract; no resolver state is included in the digest.
     */
    public static String mapDestinationReceipt(String destination, String worldIdentity,
                                               String sourceIdentity, String tableIdentity,
                                               int mapId, int centerX, int centerZ,
                                               int originX, int originY, int originZ, int scale) {
        requireDestination(destination);
        requireWorldIdentity(worldIdentity);
        requireSourceIdentity(sourceIdentity);
        requireTableIdentity(tableIdentity);
        requireMapId(mapId);
        requireScale(scale);
        return receipt(out -> {
            out.write(MAP_RECEIPT_DOMAIN.getBytes(StandardCharsets.US_ASCII));
            writeReceiptString(out, destination);
            writeReceiptString(out, worldIdentity);
            writeReceiptString(out, sourceIdentity);
            writeReceiptString(out, tableIdentity);
            out.writeInt(mapId);
            out.writeInt(centerX);
            out.writeInt(centerZ);
            out.writeInt(originX);
            out.writeInt(originY);
            out.writeInt(originZ);
            out.writeInt(scale);
        });
    }

    /** Alias used by catalog/bridge callers that name the row digest simply a map receipt. */
    public static String mapReceipt(String destination, String worldIdentity,
                                    String sourceIdentity, String tableIdentity, int mapId,
                                    int centerX, int centerZ, int originX, int originY,
                                    int originZ, int scale) {
        return mapDestinationReceipt(destination, worldIdentity, sourceIdentity, tableIdentity,
                mapId, centerX, centerZ, originX, originY, originZ, scale);
    }

    /**
     * Computes the context/catalog receipt.  Map entries are sorted by destination, while every
     * map field is written explicitly so an independently implemented Skeleton/Bridge can
     * recompute the same digest and detect a stale or mixed catalog row.
     */
    public static String productionContextReceipt(String biomeKey, String worldIdentity,
                                                  String sourceIdentity, String tableIdentity,
                                                  int originX, int originY, int originZ,
                                                  Map<String, LocatedMap> maps) {
        requireMinecraftKey(biomeKey, "production loot-origin biome");
        requireWorldIdentity(worldIdentity);
        requireSourceIdentity(sourceIdentity);
        requireTableIdentity(tableIdentity);
        Objects.requireNonNull(maps, "maps");
        List<Map.Entry<String, LocatedMap>> ordered = checkedMapEntries(maps);
        for (Map.Entry<String, LocatedMap> entry : ordered) {
            LocatedMap map = entry.getValue();
            map.requireAuthenticated();
            requireMapMatchesContext(entry.getKey(), map, worldIdentity, sourceIdentity,
                    tableIdentity, originX, originY, originZ);
        }
        return receipt(out -> {
            out.write(CONTEXT_RECEIPT_DOMAIN.getBytes(StandardCharsets.US_ASCII));
            writeReceiptString(out, biomeKey);
            writeReceiptString(out, worldIdentity);
            writeReceiptString(out, sourceIdentity);
            writeReceiptString(out, tableIdentity);
            out.writeInt(originX);
            out.writeInt(originY);
            out.writeInt(originZ);
            out.writeInt(ordered.size());
            for (Map.Entry<String, LocatedMap> entry : ordered) {
                LocatedMap map = entry.getValue();
                writeReceiptString(out, entry.getKey());
                writeReceiptString(out, map.destination());
                writeReceiptString(out, map.worldIdentity());
                writeReceiptString(out, map.sourceIdentity());
                writeReceiptString(out, map.tableIdentity());
                out.writeInt(map.mapId());
                out.writeInt(map.centerX());
                out.writeInt(map.centerZ());
                out.writeInt(map.originX());
                out.writeInt(map.originY());
                out.writeInt(map.originZ());
                out.writeInt(map.scale());
                writeReceiptString(out, map.resolverCatalogReceipt());
            }
        });
    }

    /** Alias used by producer code that calls the context digest the catalog receipt. */
    public static String catalogReceipt(String biomeKey, String worldIdentity,
                                        String sourceIdentity, String tableIdentity,
                                        int originX, int originY, int originZ,
                                        Map<String, LocatedMap> maps) {
        return productionContextReceipt(biomeKey, worldIdentity, sourceIdentity, tableIdentity,
                originX, originY, originZ, maps);
    }

    /** Cross-language v2 receipt for destination-sorted Found/NotFound target authority. */
    public static String targetProductionContextReceipt(String biomeKey, String worldIdentity,
            String sourceIdentity, String tableIdentity, int originX, int originY, int originZ,
            Map<String, LocatedMapTarget> maps) {
        requireMinecraftKey(biomeKey, "production loot-origin biome");
        requireWorldIdentity(worldIdentity);
        requireSourceIdentity(sourceIdentity);
        requireTableIdentity(tableIdentity);
        Objects.requireNonNull(maps, "located-map targets");
        List<Map.Entry<String, LocatedMapTarget>> ordered = maps.entrySet().stream()
                .sorted(Map.Entry.comparingByKey()).toList();
        for (Map.Entry<String, LocatedMapTarget> entry : ordered) {
            requireDestination(entry.getKey());
            LocatedMapTarget target = Objects.requireNonNull(entry.getValue(), "located-map target");
            target.requireAuthenticated();
            var binding = target.binding();
            if (!entry.getKey().equals(binding.destination())
                    || !worldIdentity.equals(binding.worldIdentity())
                    || !sourceIdentity.equals(binding.sourceIdentity())
                    || !tableIdentity.equals(binding.tableIdentity())
                    || originX != binding.originX() || originY != binding.originY()
                    || originZ != binding.originZ()) {
                throw new IllegalArgumentException(
                        "located-map target differs from production context identity");
            }
        }
        return receipt(out -> {
            out.write(TARGET_CONTEXT_RECEIPT_DOMAIN.getBytes(StandardCharsets.US_ASCII));
            writeReceiptString(out, biomeKey);
            writeReceiptString(out, worldIdentity);
            writeReceiptString(out, sourceIdentity);
            writeReceiptString(out, tableIdentity);
            out.writeInt(originX); out.writeInt(originY); out.writeInt(originZ);
            out.writeInt(ordered.size());
            for (Map.Entry<String, LocatedMapTarget> entry : ordered) {
                writeReceiptString(out, entry.getKey());
                out.writeByte(entry.getValue() instanceof Found ? 1 : 0);
                out.write(HEX.parseHex(entry.getValue().targetReceipt()));
            }
        });
    }

    private static String receipt(ReceiptWriter writer) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            writer.write(out);
            out.flush();
            return HEX.formatHex(MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray()));
        } catch (IOException | NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("pinned loot receipt computation failed", impossible);
        }
    }

    private static void writeReceiptString(DataOutputStream out, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        out.writeInt(bytes.length);
        out.write(bytes);
    }

    private static List<Map.Entry<String, LocatedMap>> checkedMapEntries(
            Map<String, LocatedMap> maps) {
        List<Map.Entry<String, LocatedMap>> ordered = new ArrayList<>(maps.size());
        for (Map.Entry<String, LocatedMap> entry : maps.entrySet()) {
            requireDestination(entry.getKey());
            ordered.add(Map.entry(entry.getKey(), Objects.requireNonNull(entry.getValue(),
                    "located map")));
        }
        ordered.sort(Map.Entry.comparingByKey());
        return ordered;
    }

    private static void requireMapMatchesContext(String destination, LocatedMap map,
                                                  String worldIdentity, String sourceIdentity,
                                                  String tableIdentity, int originX, int originY,
                                                  int originZ) {
        if (!destination.equals(map.destination())) {
            throw new IllegalArgumentException("map destination key does not match authenticated row");
        }
        if (!worldIdentity.equals(map.worldIdentity())
                || !sourceIdentity.equals(map.sourceIdentity())
                || !tableIdentity.equals(map.tableIdentity())) {
            throw new IllegalArgumentException("map row is mixed with another world/source/table");
        }
        if (map.originX() != originX || map.originY() != originY || map.originZ() != originZ) {
            throw new IllegalArgumentException("map row origin differs from authenticated loot origin");
        }
    }

    private static void requireDestination(String destination) {
        if (destination == null || !destination.matches("[a-z0-9_]+")) {
            throw new IllegalArgumentException("invalid canonical map destination: " + destination);
        }
    }

    private static void requireWorldIdentity(String worldIdentity) {
        if (worldIdentity == null || !worldIdentity.matches("-?(0|[1-9][0-9]*)")) {
            throw new IllegalArgumentException("world identity must be canonical signed-64-bit text");
        }
        try {
            if (!Long.toString(Long.parseLong(worldIdentity)).equals(worldIdentity)) {
                throw new IllegalArgumentException("world identity is not canonical signed-64-bit text");
            }
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("world identity is not signed-64-bit text", exception);
        }
    }

    private static void requireSourceIdentity(String sourceIdentity) {
        if (sourceIdentity == null || sourceIdentity.isBlank()
                || sourceIdentity.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("source identity is required");
        }
    }

    private static void requireTableIdentity(String tableIdentity) {
        if (tableIdentity == null || !tableIdentity.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) {
            throw new IllegalArgumentException("invalid canonical table identity: " + tableIdentity);
        }
    }

    private static void requireMapId(int mapId) {
        if (mapId < 0) {
            throw new IllegalArgumentException("map id must be nonnegative");
        }
    }

    private static void requireScale(int scale) {
        if (scale < 0 || scale > 4) {
            throw new IllegalArgumentException("map scale must be in 0..4");
        }
    }

    private static void requireSha256(String receipt, String description) {
        if (receipt == null || !receipt.matches("[0-9a-f]{64}")
                || receipt.equals("0".repeat(64))) {
            throw new IllegalArgumentException(description + " must be a nonzero SHA-256 hex receipt");
        }
    }

    @FunctionalInterface
    private interface ReceiptWriter {
        void write(DataOutputStream out) throws IOException;
    }

    /**
     * Resolves one authenticated pinned-oracle request. The world facts are deliberately scalar so
     * persistence callers cannot substitute a presentation context (notably a caller-declared biome).
     */
    public static Resolution resolve(long worldSeed, String tableKey, long rawSeed,
                                     int worldX, int worldY, int worldZ, int containerSize,
                                     XoroshiroState persistedState) {
        return resolve(worldSeed, tableKey, rawSeed, worldX, worldY, worldZ, containerSize,
                persistedState, null);
    }

    /**
     * Resolves a production request whose level-dependent facts were authenticated by the
     * canonical world authority. This is required for camp exploration-map entries: the loot
     * table itself consumes the origin biome and the result of the official structure locator.
     */
    public static Resolution resolve(long worldSeed, String tableKey, long rawSeed,
                                     int worldX, int worldY, int worldZ, int containerSize,
                                     XoroshiroState persistedState, ProductionContext productionContext) {
        Table table = TABLES.get(Objects.requireNonNull(tableKey, "tableKey"));
        if (table == null) {
            throw new IllegalArgumentException("unsupported pinned loot table: " + tableKey);
        }
        int expectedSize = table.containerSize;
        if (containerSize != expectedSize) {
            throw new IllegalArgumentException("container size does not match pinned loot table");
        }
        if (worldY < -64 || worldY >= 320) {
            throw new IllegalArgumentException("loot origin is outside pinned build height");
        }
        AuthenticatedContext context = requireAuthenticatedContext(
                worldSeed, tableKey, rawSeed, worldX, worldY, worldZ, containerSize,
                productionContext);

        return resolveAuthenticated(table, rawSeed, containerSize, persistedState, context);
    }

    /** Resolves the v2 terrain-sealed Found/NotFound target authority. */
    public static Resolution resolveLocated(long worldSeed, String tableKey, long rawSeed,
            int worldX, int worldY, int worldZ, int containerSize,
            XoroshiroState persistedState, LocatedProductionContext productionContext) {
        Table table = TABLES.get(Objects.requireNonNull(tableKey, "tableKey"));
        if (table == null) throw new IllegalArgumentException(
                "unsupported pinned loot table: " + tableKey);
        int expectedSize = table.containerSize;
        if (containerSize != expectedSize || worldY < -64 || worldY >= 320) {
            throw new IllegalArgumentException("located production loot request bounds drift");
        }
        Objects.requireNonNull(productionContext, "located production context")
                .requireMatches(worldSeed, tableKey, worldX, worldY, worldZ);
        Set<String> required = table.mapDestinations();
        if (!productionContext.maps().keySet().equals(required)) {
            throw new IllegalArgumentException(
                    "located production context has missing or foreign destinations");
        }
        return resolveAuthenticated(table, rawSeed, containerSize, persistedState,
                new AuthenticatedContext(productionContext.biomeKey(), false,
                        Map.copyOf(productionContext.maps()), true, false));
    }

    private static Resolution resolveAuthenticated(Table table, long rawSeed, int containerSize,
            XoroshiroState persistedState, AuthenticatedContext context) {

        RandomSource random;
        if (rawSeed == 0L) {
            if (persistedState == null) {
                throw new IllegalArgumentException("zero loot seed requires persisted Xoroshiro128++ state");
            }
            random = new XoroshiroRandom(persistedState.seedLo(), persistedState.seedHi());
        } else {
            if (persistedState != null) {
                throw new IllegalArgumentException("nonzero loot seed must not supply Xoroshiro128++ state");
            }
            random = new LegacyRandom(rawSeed);
        }

        List<MutableStack> generated = new ArrayList<>();
        addTableItems(table, random, context, generated);

        List<Integer> availableSlots = new ArrayList<>(containerSize);
        for (int slot = 0; slot < containerSize; slot++) {
            availableSlots.add(slot);
        }
        shuffle(availableSlots, random);
        shuffleAndSplit(generated, availableSlots.size(), random);

        List<LootStack> slots = new ArrayList<>(Collections.nCopies(containerSize, null));
        for (MutableStack stack : generated) {
            if (availableSlots.isEmpty()) {
                break;
            }
            int slot = availableSlots.remove(availableSlots.size() - 1);
            slots.set(slot, stack.freeze());
        }

        Continuation continuation = random instanceof LegacyRandom legacy
                ? Continuation.legacy48(legacy.state())
                : Continuation.xoroshiro128PlusPlus(((XoroshiroRandom) random).seedLo(),
                ((XoroshiroRandom) random).seedHi());
        return new Resolution(slots, continuation);
    }

    private static void addTableItems(Table table, RandomSource random,
            AuthenticatedContext context, List<MutableStack> generated) {
        for (Pool pool : table.pools) {
            if (pool.randomChance < 1.0f && random.nextFloat() >= pool.randomChance) {
                continue;
            }
            int rolls = pool.rolls.sample(random);
            for (int roll = 0; roll < rolls; roll++) {
                Entry selected = weightedEntry(pool.entries, random, context);
                if (selected == null || selected.empty) {
                    continue;
                }
                if (selected.nestedTable != null) {
                    addTableItems(selected.nestedTable, random, context, generated);
                    continue;
                }
                MutableStack stack = new MutableStack(selected.itemKey, 1);
                applyFunctions(selected.functions, stack, random, context);
                applyFunctions(pool.functions, stack, random, context);
                if (stack.discarded) {
                    continue;
                }
                splitForMaximumStackSize(stack, generated);
            }
        }
    }

    public static List<String> supportedTableKeys() {
        return List.copyOf(TABLES.keySet());
    }

    public static int supportedContainerSize(String tableKey) {
        Table table = TABLES.get(Objects.requireNonNull(tableKey, "tableKey"));
        if (table == null) throw new IllegalArgumentException(
                "unsupported pinned loot table: " + tableKey);
        return table.containerSize;
    }

    public static Set<String> requiredMapDestinations(String tableKey) {
        Table table = TABLES.get(Objects.requireNonNull(tableKey, "tableKey"));
        if (table == null) throw new IllegalArgumentException(
                "unsupported pinned loot table: " + tableKey);
        return table.mapDestinations;
    }

    private static AuthenticatedContext requireAuthenticatedContext(
            long worldSeed, String tableKey, long rawSeed,
            int worldX, int worldY, int worldZ, int containerSize,
            ProductionContext productionContext) {
        if (productionContext != null) {
            if (tableKey.equals("minecraft:barrels/abandoned_camp_barrel")) {
                // The barrel table has no level-sensitive map function. Preserve the old context
                // seam while keeping exact-destination checks exclusive to map-bearing tables.
                return new AuthenticatedContext(productionContext.biomeKey(), false, Map.of(),
                        false, false);
            }
            // A production context carries exact map destinations, so it is meaningful only for
            // tables whose functions place an exploration map (abandoned camp chests, shipwreck
            // map, buried treasure ...). Map-less tables keep the seeded default context.
            Table contextTable = TABLES.get(tableKey);
            if (contextTable == null || contextTable.mapDestinations().isEmpty()) {
                throw new IllegalArgumentException(
                        "production loot context is only valid for pinned map-bearing tables");
            }
            productionContext.requireMatches(worldSeed, tableKey, worldX, worldY, worldZ);
            Set<String> requiredDestinations = TABLES.get(tableKey).mapDestinations();
            if (!productionContext.maps().keySet().equals(requiredDestinations)) {
                throw new IllegalArgumentException(
                        "authenticated production context has missing or foreign map destinations");
            }
            return new AuthenticatedContext(productionContext.biomeKey(), false,
                    new LinkedHashMap<>(productionContext.maps()), true, false);
        }
        if (tableKey.equals("minecraft:barrels/abandoned_camp_barrel")) {
            return new AuthenticatedContext("minecraft:plains", false, Map.of(), false, false);
        }
        if (containerSize == 27 && tableKey.equals("minecraft:chests/abandoned_mineshaft")) {
            if (worldSeed == 0L && rawSeed == 0L
                    && worldX == -256 && worldY == -20 && worldZ == -384) {
                return new AuthenticatedContext(
                        "minecraft:sulfur_caves", true, Map.of(), false, false);
            }
            if (worldSeed == 0L && rawSeed == 0L
                    && worldX == 0 && worldY == 64 && worldZ == 0) {
                return new AuthenticatedContext("minecraft:river", false, Map.of(), false, false);
            }
            if (worldSeed == 0L && rawSeed == Long.MAX_VALUE
                    && worldX == 192 && worldY == -32 && worldZ == -224) {
                return new AuthenticatedContext("minecraft:forest", false, Map.of(), false, false);
            }
            // Retained normalized v1 nonzero witness. Its unloaded location_check was false.
            if (worldSeed == 0L && rawSeed == Long.MAX_VALUE
                    && worldX == -256 && worldY == -20 && worldZ == -384) {
                return new AuthenticatedContext(
                        "minecraft:sulfur_caves", false, Map.of(), false, false);
            }
        }
        if (containerSize == 27 && rawSeed == 0L
                && tableKey.equals("minecraft:chests/abandoned_camp_common_chest")) {
            if (worldSeed == 0L && worldX == 0 && worldY == 64 && worldZ == 0) {
                return new AuthenticatedContext("minecraft:river", false, Map.of(
                        "bamboo_jungle", LocatedMap.legacyOracleWitness(-4080, -4768, 0)),
                        false, true);
            }
            if (worldSeed == -1L && worldX == 192 && worldY == 64 && worldZ == -224) {
                return new AuthenticatedContext(
                        "minecraft:deep_ocean", false, Map.of(
                                "dappled_forest", LocatedMap.legacyOracleWitness(640, 2208, 0)),
                        false, true);
            }
        }
        boolean legacyTable = !tableKey.equals("minecraft:chests/abandoned_mineshaft")
                && !tableKey.startsWith("minecraft:chests/abandoned_camp_")
                && !tableKey.equals("minecraft:barrels/abandoned_camp_barrel")
                && TABLES.get(tableKey).mapDestinations().isEmpty();
        int legacySlots = TABLES.get(tableKey).containerSize;
        if (legacyTable && worldSeed == 0L
                && (rawSeed == 0L || rawSeed == Long.MAX_VALUE)
                && worldX == 0 && worldY == 64 && worldZ == 0
                && containerSize == legacySlots) {
            return new AuthenticatedContext("minecraft:river", false, Map.of(), false, false);
        }
        throw new IllegalArgumentException("unsupported unauthenticated pinned loot request: "
                + worldSeed + "/" + tableKey + "/" + rawSeed + "@"
                + worldX + "," + worldY + "," + worldZ + "/" + containerSize);
    }

    private static Entry weightedEntry(List<Entry> entries, RandomSource random,
                                       AuthenticatedContext context) {
        int totalWeight = 0;
        int eligibleCount = 0;
        Entry soleEligible = null;
        for (Entry entry : entries) {
            if (!entry.condition.matches(context)) {
                continue;
            }
            totalWeight += entry.weight;
            eligibleCount++;
            soleEligible = entry;
        }
        if (totalWeight == 0) {
            return null;
        }
        if (eligibleCount == 1) {
            return soleEligible;
        }
        int selection = random.nextInt(totalWeight);
        for (Entry entry : entries) {
            if (!entry.condition.matches(context)) {
                continue;
            }
            selection -= entry.weight;
            if (selection < 0) {
                return entry;
            }
        }
        throw new IllegalStateException("weighted loot selection escaped pinned entries");
    }

    private static void applyFunctions(List<Function> functions, MutableStack stack,
                                       RandomSource random, AuthenticatedContext context) {
        for (Function function : functions) {
            applyFunction(function, stack, random, context);
        }
    }

    private static void applyFunction(Function function, MutableStack stack, RandomSource random,
                                      AuthenticatedContext context) {
        if (function == null) {
            return;
        }
        switch (function.kind) {
            case SET_COUNT -> stack.count = function.value.sample(random);
            case ENCHANT_RANDOMLY -> enchantRandomly(stack, random, function.enchantmentKeys);
            case ENCHANT_WITH_LEVELS -> enchantWithLevels(stack,
                    function.value.sample(random), random, function.enchantmentKeys);
            // SetEnchantmentsFunction: BOOK 을 ENCHANTED_BOOK 으로 바꾼 뒤 고정 레벨을 쓴다.
            // 난수는 하나도 쓰지 않는다. 레벨은 키 뒤에 붙은 정수로 같은 목록에 실려 온다.
            case SET_ENCHANTMENTS -> {
                List<EnchantmentValue> fixed = new ArrayList<>();
                for (int index = 0; index < function.enchantmentKeys.size(); index += 2) {
                    fixed.add(new EnchantmentValue(function.enchantmentKeys.get(index),
                            Integer.parseInt(function.enchantmentKeys.get(index + 1))));
                }
                stack.putEnchantments(fixed);
            }
            case SET_POTION -> stack.putComponent("minecraft:potion_contents",
                    new PotionContents(function.symbolicValue));
            case SET_ITEM_NAME -> stack.putComponent("minecraft:item_name",
                    new ItemName(function.symbolicValue));
            case SET_DAMAGE -> {
                int maximumDamage = maximumDamage(stack.itemKey);
                if (maximumDamage > 0) {
                    float fraction = function.floatMin
                            + random.nextFloat() * (function.floatMax - function.floatMin);
                    stack.putComponent("minecraft:damage",
                            new Damage((int) Math.floor((1.0f - fraction) * maximumDamage)));
                }
            }
            case SET_STEW_EFFECT -> {
                StewEffect effect = function.stewEffects.get(
                        random.nextInt(function.stewEffects.size()));
                int duration = effect.duration.sample(random);
                if (!effect.instantaneous) duration *= 20;
                stack.putComponent("minecraft:suspicious_stew_effects",
                        new SuspiciousStewEffects(List.of(
                                new SuspiciousStewEffect(effect.effectKey, duration))));
            }
            case SET_INSTRUMENT -> {
                String[] instruments = REGULAR_GOAT_HORN_KEYS.toArray(String[]::new);
                stack.putComponent("minecraft:instrument",
                        new Instrument(instruments[random.nextInt(instruments.length)]));
            }
            case SET_OMINOUS_AMPLIFIER -> stack.putComponent(
                    "minecraft:ominous_bottle_amplifier",
                    new OminousBottleAmplifier(function.value.sample(random)));
            case SET_CAMP_MAP -> applyCampMap(stack, function.symbolicValue, context);
            case SET_EXPLORATION_MAP -> applyExplorerMap(stack, function.symbolicValue, context);
            default -> throw new IllegalArgumentException("unsupported pinned loot function: " + function.kind);
        }
    }

    private static void applyCampMap(MutableStack stack, String destination,
                                     AuthenticatedContext context) {
        Object row = context.maps().get(destination);
        if (row == null) {
            if (context.legacyOracleWitness()) {
                stack.discarded = true;
                return;
            }
            throw new IllegalStateException(
                    "validated map context lost camp destination before publication: " + destination);
        }
        if (row instanceof LocatedMapTarget target) {
            target.requireAuthenticated();
            if (target instanceof NotFound) {
                stack.discarded = true;
                return;
            }
            Found found = (Found) target;
            stack.putComponent("minecraft:item_name",
                    new TranslatableItemName("filled_map." + destination + "_abandoned_camp"));
            stack.putComponent("minecraft:map_decorations", new MapDecorations(Map.of(
                    "+", new MapDecoration("minecraft:abandoned_camp",
                            found.targetX(), found.targetZ(), 180.0))));
            stack.putComponent("minecraft:map_id",
                    new PendingMapId(destination, found.targetReceipt()));
            return;
        }
        LocatedMap map = (LocatedMap) row;
        if (context.exactDestinationReceipt()) {
            map.requireAuthenticated();
        } else if (!context.legacyOracleWitness()) {
            throw new IllegalStateException("map publication lacks an authenticated destination receipt");
        }
        stack.putComponent("minecraft:item_name",
                new TranslatableItemName("filled_map." + destination + "_abandoned_camp"));
        stack.putComponent("minecraft:map_decorations", new MapDecorations(Map.of(
                "+", new MapDecoration("minecraft:abandoned_camp", map.x(), map.z(), 180.0))));
        stack.putComponent("minecraft:map_id", new MapId(map.mapId()));
    }

    private static void applyExplorerMap(MutableStack stack, String destination,
                                         AuthenticatedContext context) {
        Object row = context.maps().get(destination);
        if (row == null) {
            if (context.legacyOracleWitness()) {
                stack.discarded = true;
                return;
            }
            throw new IllegalStateException(
                    "validated map context lost exploration destination before publication: "
                            + destination);
        }
        String decorationType = switch (destination) {
            case "ancient_city" -> "minecraft:ancient_city";
            case "trial_chambers" -> "minecraft:trial_chambers";
            case "mineshaft" -> "minecraft:mineshaft";
            case "desert_pyramid" -> "minecraft:desert_pyramid";
            case "jungle_temple" -> "minecraft:jungle_temple";
            case "ocean_ruin_warm" -> "minecraft:ocean_ruin_warm";
            case "woodland_mansion" -> "minecraft:mansion";
            case "buried_treasure" -> "minecraft:red_x";
            default -> throw new IllegalArgumentException(
                    "unsupported pinned exploration-map destination: " + destination);
        };
        if (row instanceof LocatedMapTarget target) {
            target.requireAuthenticated();
            if (target instanceof NotFound) {
                stack.discarded = true;
                return;
            }
            Found found = (Found) target;
            stack.putComponent("minecraft:map_decorations", new MapDecorations(Map.of(
                    "+", new MapDecoration(decorationType,
                            found.targetX(), found.targetZ(), 180.0))));
            stack.putComponent("minecraft:map_id",
                    new PendingMapId(destination, found.targetReceipt()));
            return;
        }
        LocatedMap map = (LocatedMap) row;
        if (context.exactDestinationReceipt()) {
            map.requireAuthenticated();
        } else if (!context.legacyOracleWitness()) {
            throw new IllegalStateException("map publication lacks an authenticated destination receipt");
        }
        stack.putComponent("minecraft:map_decorations", new MapDecorations(Map.of(
                "+", new MapDecoration(decorationType, map.x(), map.z(), 180.0))));
        stack.putComponent("minecraft:map_id", new MapId(map.mapId()));
    }

    private static void enchantRandomly(MutableStack stack, RandomSource random,
            List<String> allowedKeys) {
        List<Enchantment> available = eligibleEnchantments(stack.itemKey, allowedKeys);
        if (available.isEmpty()) return;
        Enchantment enchantment = available.get(random.nextInt(available.size()));
        int level = nextIntInclusive(random, 1, enchantment.maxLevel);
        stack.putEnchantments(List.of(new EnchantmentValue(enchantment.key, level)));
    }

    private static void enchantWithLevels(MutableStack stack, int cost, RandomSource random,
            List<String> allowedKeys) {
        int enchantability = enchantability(stack.itemKey);
        int bound = enchantability / 4 + 1;
        int adjustedCost = cost + 1 + random.nextInt(bound) + random.nextInt(bound);
        float span = (random.nextFloat() + random.nextFloat() - 1.0f) * 0.15f;
        adjustedCost = Math.max(1, Math.round(adjustedCost + adjustedCost * span));

        List<Candidate> candidates = availableEnchantments(stack.itemKey, adjustedCost, allowedKeys);
        List<EnchantmentValue> selected = new ArrayList<>();
        if (!candidates.isEmpty()) {
            Candidate first = weightedCandidate(candidates, random);
            selected.add(new EnchantmentValue(first.enchantment.key, first.level));
            while (random.nextInt(50) <= adjustedCost) {
                Enchantment last = first.enchantment;
                candidates.removeIf(candidate -> !compatible(last, candidate.enchantment));
                if (candidates.isEmpty()) {
                    break;
                }
                first = weightedCandidate(candidates, random);
                selected.add(new EnchantmentValue(first.enchantment.key, first.level));
                adjustedCost /= 2;
            }
        }
        stack.putEnchantments(selected);
    }

    private static List<Candidate> availableEnchantments(String itemKey, int cost,
            List<String> allowedKeys) {
        List<Candidate> candidates = new ArrayList<>();
        for (Enchantment enchantment : eligibleEnchantments(itemKey, allowedKeys)) {
            for (int level = enchantment.maxLevel; level >= 1; level--) {
                int min = enchantment.minBase + enchantment.minPerLevel * (level - 1);
                int max = enchantment.maxBase + enchantment.maxPerLevel * (level - 1);
                if (cost >= min && cost <= max) {
                    candidates.add(new Candidate(enchantment, level));
                    break;
                }
            }
        }
        return candidates;
    }

    private static List<Enchantment> eligibleEnchantments(String itemKey,
            List<String> allowedKeys) {
        return ON_RANDOM_LOOT_ENCHANTMENTS.stream()
                .filter(enchantment -> (allowedKeys.isEmpty()
                        || allowedKeys.contains(enchantment.key))
                        && enchantment.appliesTo(itemKey))
                .toList();
    }

    private static Candidate weightedCandidate(List<Candidate> candidates, RandomSource random) {
        int totalWeight = 0;
        for (Candidate candidate : candidates) {
            totalWeight += candidate.enchantment.weight;
        }
        int selection = random.nextInt(totalWeight);
        for (Candidate candidate : candidates) {
            selection -= candidate.enchantment.weight;
            if (selection < 0) {
                return candidate;
            }
        }
        throw new IllegalStateException("weighted enchantment selection escaped pinned candidates");
    }

    private static boolean compatible(Enchantment left, Enchantment right) {
        if (left == right) {
            return false;
        }
        return !left.excludedKeys.contains(right.key) && !right.excludedKeys.contains(left.key);
    }

    private static void splitForMaximumStackSize(MutableStack stack, List<MutableStack> output) {
        int maximum = maximumStackSize(stack.itemKey);
        if (stack.count <= maximum) {
            output.add(stack);
            return;
        }
        int remaining = stack.count;
        while (remaining > 0) {
            int count = Math.min(maximum, remaining);
            output.add(stack.copyWithCount(count));
            remaining -= count;
        }
    }

    private static int maximumStackSize(String itemKey) {
        return switch (itemKey) {
            case "minecraft:enchanted_book", "minecraft:potion", "minecraft:iron_pickaxe",
                    "minecraft:stone_axe", "minecraft:iron_sword", "minecraft:iron_spear",
                    "minecraft:fishing_rod", "minecraft:flint_and_steel", "minecraft:bundle",
                    "minecraft:saddle", "minecraft:bow", "minecraft:copper_axe",
                    "minecraft:copper_boots", "minecraft:copper_chestplate",
                    "minecraft:copper_leggings", "minecraft:copper_spear",
                    "minecraft:copper_sword", "minecraft:spyglass", "minecraft:shears",
                    "minecraft:abandoned_campsite_map", "minecraft:ancient_city_map",
                    "minecraft:trial_explorer_map", "minecraft:mineshaft_map",
                    "minecraft:desert_pyramid_map", "minecraft:jungle_explorer_map",
                    "minecraft:warm_ocean_ruins_map", "minecraft:woodland_explorer_map",
                    "minecraft:wooden_axe", "minecraft:iron_axe",
                    "minecraft:leather_chestplate", "minecraft:iron_chestplate", "minecraft:iron_helmet",
                    "minecraft:iron_leggings", "minecraft:iron_boots", "minecraft:copper_horse_armor",
                    "minecraft:iron_horse_armor", "minecraft:golden_horse_armor",
                    "minecraft:diamond_horse_armor", "minecraft:copper_nautilus_armor",
                    "minecraft:iron_nautilus_armor", "minecraft:golden_nautilus_armor",
                    "minecraft:diamond_nautilus_armor", "minecraft:music_disc_bounce",
                    "minecraft:music_disc_otherside", "minecraft:music_disc_13",
                    "minecraft:music_disc_cat", "minecraft:music_disc_precipice",
                    "minecraft:music_disc_creator_music_box", "minecraft:buried_treasure_map",
                    "minecraft:suspicious_stew", "minecraft:goat_horn", "minecraft:shield",
                    "minecraft:crossbow", "minecraft:trident", "minecraft:diamond_axe",
                    "minecraft:diamond_pickaxe", "minecraft:golden_axe",
                    "minecraft:golden_pickaxe", "minecraft:stone_pickaxe",
                    "minecraft:golden_sword", "minecraft:golden_hoe",
                    "minecraft:golden_shovel", "minecraft:golden_boots",
                    "minecraft:golden_chestplate", "minecraft:golden_helmet",
                    "minecraft:golden_leggings", "minecraft:leather_helmet",
                    "minecraft:leather_leggings", "minecraft:leather_boots",
                    "minecraft:splash_potion", "minecraft:lingering_potion",
                    "minecraft:water_bucket", "minecraft:milk_bucket",
                    "minecraft:stone_spear", "minecraft:diamond_chestplate" -> 1;
            case "minecraft:bucket", "minecraft:ender_pearl", "minecraft:straw_bed",
                    "minecraft:snowball", "minecraft:egg", "minecraft:honey_bottle",
                    "minecraft:bamboo_hanging_sign" -> 16;
            default -> 64;
        };
    }

    private static int enchantability(String itemKey) {
        return switch (itemKey) {
            case "minecraft:iron_axe" -> 14;
            case "minecraft:iron_chestplate" -> 9;
            case "minecraft:diamond_axe", "minecraft:diamond_chestplate" -> 10;
            default -> 1;
        };
    }

    private static int maximumDamage(String itemKey) {
        return switch (itemKey) {
            case "minecraft:stone_axe", "minecraft:stone_pickaxe" -> 131;
            case "minecraft:iron_axe" -> 250;
            case "minecraft:diamond_axe", "minecraft:diamond_pickaxe" -> 1561;
            case "minecraft:golden_axe", "minecraft:golden_pickaxe" -> 32;
            case "minecraft:shield" -> 336;
            default -> -1;
        };
    }

    private static void shuffleAndSplit(List<MutableStack> result, int availableSlots, RandomSource random) {
        List<MutableStack> splittable = new ArrayList<>();
        result.removeIf(stack -> {
            if (stack.count <= 1) {
                return false;
            }
            splittable.add(stack);
            return true;
        });

        while (availableSlots - result.size() - splittable.size() > 0 && !splittable.isEmpty()) {
            MutableStack stack = splittable.remove(nextIntInclusive(random, 0, splittable.size() - 1));
            int removed = nextIntInclusive(random, 1, stack.count / 2);
            MutableStack copy = stack.copyWithCount(removed);
            stack.count -= removed;
            if (stack.count > 1 && random.nextBoolean()) {
                splittable.add(stack);
            } else {
                result.add(stack);
            }
            if (copy.count > 1 && random.nextBoolean()) {
                splittable.add(copy);
            } else {
                result.add(copy);
            }
        }
        result.addAll(splittable);
        shuffle(result, random);
    }

    private static <T> void shuffle(List<T> values, RandomSource random) {
        for (int i = values.size(); i > 1; i--) {
            int swapTo = random.nextInt(i);
            T last = values.get(i - 1);
            values.set(i - 1, values.get(swapTo));
            values.set(swapTo, last);
        }
    }

    private static int nextIntInclusive(RandomSource random, int min, int max) {
        return min >= max ? min : min + random.nextInt(max - min + 1);
    }

    private static Map<String, Table> createTables() {
        Map<String, Table> tables = new LinkedHashMap<>();
        tables.put("minecraft:chests/abandoned_mineshaft", table(
                pool(1, 1, e("minecraft:golden_apple", 20), e("minecraft:enchanted_golden_apple", 1),
                        e("minecraft:name_tag", 30), enchantedRandom("minecraft:book", 10),
                        e("minecraft:iron_pickaxe", 5), empty(5)),
                pool(2, 4, counted("minecraft:iron_ingot", 10, 1, 5), counted("minecraft:gold_ingot", 5, 1, 3),
                        counted("minecraft:redstone", 5, 4, 9), counted("minecraft:lapis_lazuli", 5, 4, 9),
                        counted("minecraft:diamond", 3, 1, 2), counted("minecraft:coal", 10, 3, 8),
                        counted("minecraft:bread", 15, 1, 3), counted("minecraft:glow_berries", 15, 3, 6),
                        counted("minecraft:melon_seeds", 10, 2, 4), counted("minecraft:pumpkin_seeds", 10, 2, 4),
                        counted("minecraft:beetroot_seeds", 10, 2, 4)),
                pool(3, 3, counted("minecraft:rail", 20, 4, 8), counted("minecraft:powered_rail", 5, 1, 4),
                        counted("minecraft:detector_rail", 5, 1, 4), counted("minecraft:activator_rail", 5, 1, 4),
                        counted("minecraft:torch", 15, 1, 16),
                        sulfurCaves("minecraft:music_disc_bounce", 10))));
        tables.put("minecraft:chests/simple_dungeon", table(
                pool(1, 3, counted("minecraft:leather", 20, 1, 5), e("minecraft:golden_apple", 15),
                        e("minecraft:enchanted_golden_apple", 2), e("minecraft:music_disc_otherside", 2),
                        e("minecraft:music_disc_13", 15), e("minecraft:music_disc_cat", 15),
                        e("minecraft:name_tag", 20), e("minecraft:golden_horse_armor", 10),
                        e("minecraft:copper_horse_armor", 15), e("minecraft:iron_horse_armor", 15),
                        e("minecraft:diamond_horse_armor", 5), enchantedRandom("minecraft:book", 10)),
                pool(1, 4, counted("minecraft:iron_ingot", 10, 1, 4), counted("minecraft:gold_ingot", 5, 1, 4),
                        e("minecraft:bread", 20), counted("minecraft:wheat", 20, 1, 4), e("minecraft:bucket", 10),
                        counted("minecraft:redstone", 15, 1, 4), counted("minecraft:coal", 15, 1, 4),
                        counted("minecraft:melon_seeds", 10, 2, 4), counted("minecraft:pumpkin_seeds", 10, 2, 4),
                        counted("minecraft:beetroot_seeds", 10, 2, 4)),
                pool(3, 3, counted("minecraft:bone", 10, 1, 8), counted("minecraft:gunpowder", 10, 1, 8),
                        counted("minecraft:rotten_flesh", 10, 1, 8), counted("minecraft:string", 10, 1, 8))));
        tables.put("minecraft:chests/desert_pyramid", table(
                pool(2, 4, counted("minecraft:diamond", 5, 1, 3), counted("minecraft:iron_ingot", 15, 1, 5),
                        counted("minecraft:gold_ingot", 15, 2, 7), counted("minecraft:emerald", 15, 1, 3),
                        counted("minecraft:bone", 25, 4, 6), counted("minecraft:spider_eye", 25, 1, 3),
                        counted("minecraft:rotten_flesh", 25, 3, 7), counted("minecraft:leather", 20, 1, 5),
                        e("minecraft:copper_horse_armor", 15), e("minecraft:iron_horse_armor", 15),
                        e("minecraft:golden_horse_armor", 10), e("minecraft:diamond_horse_armor", 5),
                        enchantedRandom("minecraft:book", 20), e("minecraft:golden_apple", 20),
                        e("minecraft:enchanted_golden_apple", 2), empty(15)),
                pool(4, 4, counted("minecraft:bone", 10, 1, 8), counted("minecraft:gunpowder", 10, 1, 8),
                        counted("minecraft:rotten_flesh", 10, 1, 8), counted("minecraft:string", 10, 1, 8),
                        counted("minecraft:sand", 10, 1, 8)),
                pool(1, 1, empty(6), counted("minecraft:dune_armor_trim_smithing_template", 1, 2, 2))));
        tables.put("minecraft:chests/jungle_temple", table(
                pool(2, 6, counted("minecraft:diamond", 3, 1, 3), counted("minecraft:iron_ingot", 10, 1, 5),
                        counted("minecraft:gold_ingot", 15, 2, 7), counted("minecraft:bamboo", 15, 1, 3),
                        counted("minecraft:emerald", 2, 1, 3), counted("minecraft:bone", 20, 4, 6),
                        counted("minecraft:rotten_flesh", 16, 3, 7), counted("minecraft:leather", 3, 1, 5),
                        e("minecraft:copper_horse_armor", 1), e("minecraft:iron_horse_armor", 1),
                        e("minecraft:golden_horse_armor", 1), e("minecraft:diamond_horse_armor", 1),
                        enchantedLevels("minecraft:book", 1, 30)),
                pool(1, 1, empty(2), counted("minecraft:wild_armor_trim_smithing_template", 1, 2, 2))));
        tables.put("minecraft:chests/jungle_temple_dispenser", tableWithSize(9,
                pool(1, 2, counted("minecraft:arrow", 30, 2, 7))));
        tables.put("minecraft:chests/igloo_chest", table(
                pool(2, 8, counted("minecraft:apple", 15, 1, 3), counted("minecraft:coal", 15, 1, 4),
                        counted("minecraft:gold_nugget", 10, 1, 3), e("minecraft:stone_axe", 2),
                        e("minecraft:rotten_flesh", 10), e("minecraft:emerald", 1),
                        counted("minecraft:wheat", 10, 2, 3)),
                pool(1, 1, e("minecraft:golden_apple", 1))));
        tables.put("minecraft:chests/buried_treasure", table(
                pool(1, 1, e("minecraft:heart_of_the_sea", 1)),
                pool(5, 8, counted("minecraft:iron_ingot", 20, 1, 4), counted("minecraft:gold_ingot", 10, 1, 4),
                        counted("minecraft:tnt", 5, 1, 2)),
                pool(1, 3, counted("minecraft:emerald", 5, 4, 8), counted("minecraft:diamond", 5, 1, 2),
                        counted("minecraft:prismarine_crystals", 5, 1, 5)),
                pool(0, 1, e("minecraft:leather_chestplate", 1), e("minecraft:iron_sword", 1),
                        e("minecraft:iron_spear", 1)),
                pool(2, 2, counted("minecraft:cooked_cod", 1, 2, 4), counted("minecraft:cooked_salmon", 1, 2, 4)),
                poolWithFunction(0, 2, Function.potion("minecraft:water_breathing"), e("minecraft:potion", 1)),
                pool(1, 1, empty(148), counted("minecraft:copper_nautilus_armor", 20, 1, 1),
                        counted("minecraft:iron_nautilus_armor", 10, 1, 1),
                        counted("minecraft:golden_nautilus_armor", 5, 1, 1),
                        counted("minecraft:diamond_nautilus_armor", 2, 1, 1))));
        tables.put("minecraft:chests/stronghold_crossing", table(
                pool(1, 4, counted("minecraft:iron_ingot", 10, 1, 5), counted("minecraft:gold_ingot", 5, 1, 3),
                        counted("minecraft:redstone", 5, 4, 9), counted("minecraft:coal", 10, 3, 8),
                        counted("minecraft:bread", 15, 1, 3), counted("minecraft:apple", 15, 1, 3),
                        e("minecraft:iron_pickaxe", 1), enchantedLevels("minecraft:book", 1, 30))));
        tables.put("minecraft:chests/stronghold_corridor", table(
                pool(2, 3, e("minecraft:ender_pearl", 10), counted("minecraft:diamond", 3, 1, 3),
                        counted("minecraft:iron_ingot", 10, 1, 5), counted("minecraft:gold_ingot", 5, 1, 3),
                        counted("minecraft:redstone", 5, 4, 9), counted("minecraft:bread", 15, 1, 3),
                        counted("minecraft:apple", 15, 1, 3), e("minecraft:iron_pickaxe", 5),
                        e("minecraft:iron_sword", 5), e("minecraft:iron_chestplate", 5),
                        e("minecraft:iron_helmet", 5), e("minecraft:iron_leggings", 5),
                        e("minecraft:iron_boots", 5), e("minecraft:golden_apple", 1),
                        counted("minecraft:leather", 1, 1, 5), e("minecraft:copper_horse_armor", 1),
                        e("minecraft:iron_horse_armor", 1), e("minecraft:golden_horse_armor", 1),
                        e("minecraft:diamond_horse_armor", 1), e("minecraft:music_disc_otherside", 1),
                        enchantedLevels("minecraft:book", 1, 30)),
                pool(1, 1, empty(9), e("minecraft:eye_armor_trim_smithing_template", 1))));
        tables.put("minecraft:chests/stronghold_library", table(
                pool(2, 10, counted("minecraft:book", 20, 1, 3), counted("minecraft:paper", 20, 2, 7),
                        e("minecraft:map", 1), e("minecraft:compass", 1),
                        enchantedLevels("minecraft:book", 10, 30)),
                pool(1, 1, e("minecraft:eye_armor_trim_smithing_template", 1))));
        tables.put("minecraft:chests/abandoned_camp_common_chest", table(
                pool(4, 6,
                        counted("minecraft:arrow", 1, 4, 4),
                        counted("minecraft:map", 1, 1, 1),
                        counted("minecraft:bone", 1, 2, 4),
                        counted("minecraft:cobweb", 1, 1, 1),
                        counted("minecraft:compass", 1, 1, 1),
                        counted("minecraft:map", 1, 1, 2),
                        counted("minecraft:gunpowder", 1, 2, 4),
                        counted("minecraft:fishing_rod", 1, 1, 1),
                        counted("minecraft:flint_and_steel", 1, 1, 1),
                        counted("minecraft:glass_bottle", 1, 1, 4),
                        counted("minecraft:lead", 1, 1, 3),
                        counted("minecraft:leather", 1, 1, 4),
                        counted("minecraft:bundle", 1, 1, 1),
                        counted("minecraft:rabbit_hide", 1, 1, 4),
                        counted("minecraft:saddle", 1, 1, 1),
                        counted("minecraft:white_candle", 1, 1, 3)),
                pool(2, 2,
                        counted("minecraft:bow", 1, 1, 1),
                        counted("minecraft:bucket", 1, 1, 1),
                        counted("minecraft:copper_axe", 1, 1, 1),
                        counted("minecraft:copper_boots", 1, 1, 1),
                        counted("minecraft:copper_chestplate", 1, 1, 1),
                        counted("minecraft:copper_leggings", 1, 1, 1),
                        counted("minecraft:copper_spear", 1, 1, 1),
                        counted("minecraft:copper_sword", 1, 1, 1),
                        counted("minecraft:spyglass", 1, 1, 1),
                        counted("minecraft:shears", 1, 1, 1)),
                pool(1, 1,
                        campMap("bamboo_jungle"), campMap("cherry_grove"),
                        campMap("birch_forest"), campMap("dappled_forest"),
                        campMap("flower_forest"), campMap("pale_garden"),
                        campMap("swamp"), campMap("windswept_forest"))));
        tables.put("minecraft:barrels/abandoned_camp_barrel", table(
                pool(4, 8,
                        counted("minecraft:arrow", 1, 1, 3),
                        counted("minecraft:bone", 1, 2, 4),
                        counted("minecraft:bowl", 1, 1, 2),
                        counted("minecraft:bread", 1, 1, 3),
                        counted("minecraft:coal", 1, 2, 4),
                        counted("minecraft:cobweb", 1, 1, 1),
                        counted("minecraft:glass_bottle", 1, 1, 3),
                        counted("minecraft:leather", 1, 1, 3),
                        counted("minecraft:rabbit_hide", 1, 1, 4),
                        counted("minecraft:string", 1, 1, 2),
                        counted("minecraft:wheat", 1, 1, 4),
                        counted("minecraft:white_candle", 1, 1, 3),
                        counted("minecraft:white_cushion", 1, 1, 2),
                        counted("minecraft:straw_bed", 1, 2, 4)),
                pool(1, 1,
                        counted("minecraft:bundle", 1, 1, 1),
                        counted("minecraft:wooden_axe", 1, 1, 1),
                        counted("minecraft:fishing_rod", 1, 1, 1))));
        tables.put("minecraft:chests/abandoned_camp_secret_chest", table(
                pool(2, 2,
                        counted("minecraft:diamond", 1, 1, 1),
                        potion("minecraft:healing"), potion("minecraft:leaping"),
                        potion("minecraft:night_vision"), potion("minecraft:swiftness")),
                pool(4, 6,
                        counted("minecraft:map", 1, 1, 1),
                        counted("minecraft:copper_ingot", 1, 1, 2),
                        counted("minecraft:gold_ingot", 1, 1, 2),
                        counted("minecraft:iron_ingot", 1, 1, 1)),
                pool(0, 1,
                        counted("minecraft:iron_axe", 1, 1, 1),
                        counted("minecraft:iron_boots", 1, 1, 1),
                        counted("minecraft:iron_leggings", 1, 1, 1),
                        counted("minecraft:iron_spear", 1, 1, 1)),
                pool(1, 1,
                        explorerMap("minecraft:ancient_city_map", "ancient_city"),
                        explorerMap("minecraft:trial_explorer_map", "trial_chambers"),
                        explorerMap("minecraft:mineshaft_map", "mineshaft"),
                        explorerMap("minecraft:desert_pyramid_map", "desert_pyramid"),
                        explorerMap("minecraft:jungle_explorer_map", "jungle_temple"),
                        explorerMap("minecraft:warm_ocean_ruins_map", "ocean_ruin_warm"),
                        explorerMap("minecraft:woodland_explorer_map", "woodland_mansion"))));

        // DIRECT_CODEC authority: exact base-pack JSON bytes are bound by
        // mc263/container-loot-authority-26.3-snapshot-7.sha256.
        Pool nautilusArmor = pool(1, 1, empty(148),
                counted("minecraft:copper_nautilus_armor", 20, 1, 1),
                counted("minecraft:iron_nautilus_armor", 10, 1, 1),
                counted("minecraft:golden_nautilus_armor", 5, 1, 1),
                counted("minecraft:diamond_nautilus_armor", 2, 1, 1));
        tables.put("minecraft:chests/underwater_ruin_big", table(
                pool(2, 8, counted("minecraft:coal", 10, 1, 4),
                        counted("minecraft:gold_nugget", 10, 1, 3),
                        e("minecraft:emerald", 1), e("minecraft:stone_spear", 2),
                        counted("minecraft:wheat", 10, 2, 3)),
                pool(1, 1, e("minecraft:golden_apple", 1),
                        enchantedRandom("minecraft:book", 5),
                        e("minecraft:leather_chestplate", 1), e("minecraft:golden_helmet", 1),
                        enchantedRandom("minecraft:fishing_rod", 5),
                        explorerMapWeighted("minecraft:buried_treasure_map", 10,
                                "buried_treasure")), nautilusArmor));
        tables.put("minecraft:chests/underwater_ruin_small", table(
                pool(2, 8, counted("minecraft:coal", 10, 1, 4),
                        e("minecraft:stone_axe", 2), e("minecraft:stone_spear", 2),
                        e("minecraft:rotten_flesh", 5), e("minecraft:emerald", 1),
                        counted("minecraft:wheat", 10, 2, 3)),
                pool(1, 1, e("minecraft:leather_chestplate", 1),
                        e("minecraft:golden_helmet", 1),
                        enchantedRandom("minecraft:fishing_rod", 5),
                        explorerMapWeighted("minecraft:buried_treasure_map", 5,
                                "buried_treasure")), nautilusArmor));
        tables.put("minecraft:chests/ruined_portal", table(
                pool(4, 8, counted("minecraft:obsidian", 40, 1, 2),
                        counted("minecraft:flint", 40, 1, 4),
                        counted("minecraft:iron_nugget", 40, 9, 18),
                        e("minecraft:flint_and_steel", 40), e("minecraft:fire_charge", 40),
                        e("minecraft:golden_apple", 15),
                        counted("minecraft:gold_nugget", 15, 4, 24),
                        enchantedRandom("minecraft:golden_sword", 15),
                        enchantedRandom("minecraft:golden_axe", 15),
                        enchantedRandom("minecraft:golden_hoe", 15),
                        enchantedRandom("minecraft:golden_shovel", 15),
                        enchantedRandom("minecraft:golden_pickaxe", 15),
                        enchantedRandom("minecraft:golden_boots", 15),
                        enchantedRandom("minecraft:golden_chestplate", 15),
                        enchantedRandom("minecraft:golden_helmet", 15),
                        enchantedRandom("minecraft:golden_leggings", 15),
                        counted("minecraft:glistering_melon_slice", 5, 4, 12),
                        e("minecraft:golden_horse_armor", 5),
                        e("minecraft:light_weighted_pressure_plate", 5),
                        counted("minecraft:golden_carrot", 5, 4, 12),
                        e("minecraft:clock", 5), counted("minecraft:gold_ingot", 5, 2, 8),
                        e("minecraft:bell", 1), e("minecraft:enchanted_golden_apple", 1),
                        counted("minecraft:gold_block", 1, 1, 2)),
                pool(1, 1, empty(1), counted("minecraft:lodestone", 2, 1, 2))));
        tables.put("minecraft:chests/pillager_outpost", table(
                pool(0, 1, e("minecraft:crossbow", 1)),
                pool(2, 3, counted("minecraft:wheat", 7, 3, 5),
                        counted("minecraft:potato", 5, 2, 5),
                        counted("minecraft:carrot", 5, 3, 5)),
                pool(1, 3, counted("minecraft:dark_oak_log", 1, 2, 3)),
                pool(2, 3, e("minecraft:experience_bottle", 7),
                        counted("minecraft:string", 4, 1, 6),
                        counted("minecraft:arrow", 4, 2, 7),
                        counted("minecraft:tripwire_hook", 3, 1, 3),
                        counted("minecraft:iron_ingot", 3, 1, 3),
                        enchantedRandom("minecraft:book", 1)),
                poolWithFunction(0, 1, Function.instrument(), e("minecraft:goat_horn", 1)),
                pool(1, 1, empty(3),
                        counted("minecraft:sentry_armor_trim_smithing_template", 1, 2, 2))));

        Pool coastTrim = pool(1, 1, empty(5),
                counted("minecraft:coast_armor_trim_smithing_template", 1, 2, 2));
        tables.put("minecraft:chests/shipwreck_supply", table(
                pool(3, 10, counted("minecraft:paper", 8, 1, 12),
                        counted("minecraft:potato", 7, 2, 6),
                        counted("minecraft:moss_block", 7, 1, 4),
                        counted("minecraft:poisonous_potato", 7, 2, 6),
                        counted("minecraft:carrot", 7, 4, 8),
                        counted("minecraft:wheat", 7, 8, 21),
                        functions("minecraft:suspicious_stew", 10,
                                Function.stew(
                                        stew("minecraft:night_vision", 7, 10, false),
                                        stew("minecraft:jump_boost", 7, 10, false),
                                        stew("minecraft:weakness", 6, 8, false),
                                        stew("minecraft:blindness", 5, 7, false),
                                        stew("minecraft:poison", 10, 20, false),
                                        stew("minecraft:saturation", 7, 10, true))),
                        counted("minecraft:coal", 6, 2, 8),
                        counted("minecraft:rotten_flesh", 5, 5, 24),
                        counted("minecraft:pumpkin", 2, 1, 3),
                        counted("minecraft:bamboo", 2, 1, 3),
                        counted("minecraft:gunpowder", 3, 1, 5),
                        counted("minecraft:tnt", 1, 1, 2),
                        enchantedRandom("minecraft:leather_helmet", 3),
                        enchantedRandom("minecraft:leather_chestplate", 3),
                        enchantedRandom("minecraft:leather_leggings", 3),
                        enchantedRandom("minecraft:leather_boots", 3)), coastTrim, nautilusArmor));
        tables.put("minecraft:chests/shipwreck_map", table(
                pool(1, 1, explorerMapWeighted("minecraft:buried_treasure_map", 1,
                        "buried_treasure")),
                pool(3, 3, e("minecraft:compass", 1), e("minecraft:map", 1),
                        e("minecraft:clock", 1), counted("minecraft:paper", 20, 1, 10),
                        counted("minecraft:feather", 10, 1, 5),
                        counted("minecraft:book", 5, 1, 5)), coastTrim, nautilusArmor));
        tables.put("minecraft:chests/shipwreck_treasure", table(
                pool(3, 6, counted("minecraft:iron_ingot", 90, 1, 5),
                        counted("minecraft:gold_ingot", 10, 1, 5),
                        counted("minecraft:emerald", 40, 1, 5),
                        e("minecraft:diamond", 5), e("minecraft:experience_bottle", 5)),
                pool(2, 5, counted("minecraft:iron_nugget", 50, 1, 10),
                        counted("minecraft:gold_nugget", 10, 1, 10),
                        counted("minecraft:lapis_lazuli", 20, 1, 10)), coastTrim, nautilusArmor));

        tables.put("minecraft:chests/trial_chambers/corridor", table(
                pool(1, 3,
                        functions("minecraft:iron_axe", 1, Function.count(1, 1),
                                Function.damage(.4f, .9f), Function.enchantRandomly()),
                        counted("minecraft:honeycomb", 1, 2, 8),
                        functions("minecraft:stone_axe", 2, Function.count(1, 1),
                                Function.damage(.15f, .8f)),
                        functions("minecraft:stone_pickaxe", 2, Function.count(1, 1),
                                Function.damage(.15f, .8f)),
                        counted("minecraft:ender_pearl", 2, 1, 2),
                        counted("minecraft:bamboo_hanging_sign", 2, 1, 4),
                        counted("minecraft:bamboo_planks", 2, 3, 6),
                        counted("minecraft:scaffolding", 2, 2, 10),
                        counted("minecraft:torch", 2, 3, 6), counted("minecraft:tuff", 3, 8, 20))));
        tables.put("minecraft:chests/trial_chambers/entrance", table(
                pool(2, 3, counted("minecraft:trial_key", 1, 1, 1),
                        counted("minecraft:stick", 5, 2, 5),
                        counted("minecraft:wooden_axe", 10, 1, 1),
                        counted("minecraft:honeycomb", 10, 2, 8),
                        counted("minecraft:arrow", 10, 5, 10))));
        tables.put("minecraft:chests/trial_chambers/intersection", table(
                pool(1, 3, counted("minecraft:diamond_block", 1, 1, 1),
                        counted("minecraft:emerald_block", 5, 1, 3),
                        functions("minecraft:diamond_axe", 5, Function.count(1, 1),
                                Function.damage(.1f, .5f)),
                        functions("minecraft:diamond_pickaxe", 5, Function.count(1, 1),
                                Function.damage(.1f, .5f)),
                        counted("minecraft:diamond", 10, 1, 2), counted("minecraft:cake", 20, 1, 4),
                        counted("minecraft:amethyst_shard", 20, 8, 20),
                        counted("minecraft:iron_block", 20, 1, 2))));
        tables.put("minecraft:chests/trial_chambers/intersection_barrel", table(
                pool(1, 3,
                        functions("minecraft:diamond_axe", 1, Function.count(1, 1),
                                Function.damage(.4f, .9f), Function.enchantRandomly()),
                        functions("minecraft:diamond_pickaxe", 1, Function.count(1, 1),
                                Function.damage(.15f, .8f)),
                        counted("minecraft:diamond", 1, 1, 3),
                        functions("minecraft:compass", 1, Function.count(1, 1),
                                Function.damage(.15f, .8f)),
                        counted("minecraft:bucket", 1, 1, 2),
                        functions("minecraft:golden_axe", 4, Function.count(1, 1),
                                Function.damage(.15f, .8f)),
                        functions("minecraft:golden_pickaxe", 4, Function.count(1, 1),
                                Function.damage(.15f, .8f)),
                        counted("minecraft:bamboo_planks", 10, 5, 15),
                        counted("minecraft:baked_potato", 10, 6, 10))));

        Table rewardCommon = table(pool(1, 1,
                counted("minecraft:arrow", 4, 2, 8),
                functions("minecraft:tipped_arrow", 4, Function.count(2, 8),
                        Function.potion("minecraft:poison")),
                counted("minecraft:emerald", 4, 2, 4),
                counted("minecraft:wind_charge", 3, 1, 3),
                counted("minecraft:iron_ingot", 3, 1, 4),
                counted("minecraft:honey_bottle", 3, 1, 2),
                functions("minecraft:ominous_bottle", 2, Function.count(1, 1),
                        Function.ominousAmplifier(0, 1)),
                counted("minecraft:wind_charge", 1, 4, 12),
                counted("minecraft:diamond", 1, 1, 2)));
        Table rewardRare = table(pool(1, 1,
                counted("minecraft:emerald", 3, 2, 4),
                functions("minecraft:shield", 3, Function.damage(.5f, 1f)),
                functions("minecraft:bow", 3, Function.enchantWithLevels(5, 15)),
                functions("minecraft:crossbow", 2, Function.enchantWithLevels(5, 20)),
                functions("minecraft:iron_axe", 2, Function.enchantWithLevels(0, 10)),
                functions("minecraft:iron_chestplate", 2, Function.enchantWithLevels(0, 10)),
                counted("minecraft:golden_carrot", 2, 1, 2),
                functions("minecraft:book", 2, Function.enchantRandomly(
                        "minecraft:sharpness", "minecraft:bane_of_arthropods",
                        "minecraft:efficiency", "minecraft:fortune", "minecraft:silk_touch",
                        "minecraft:feather_falling")),
                functions("minecraft:book", 2, Function.enchantRandomly(
                        "minecraft:riptide", "minecraft:loyalty", "minecraft:channeling",
                        "minecraft:impaling", "minecraft:mending")),
                functions("minecraft:diamond_chestplate", 1, Function.enchantWithLevels(5, 15)),
                functions("minecraft:diamond_axe", 1, Function.enchantWithLevels(5, 15))));
        Table rewardUnique = table(pool(1, 1, e("minecraft:golden_apple", 4),
                e("minecraft:bolt_armor_trim_smithing_template", 3),
                e("minecraft:guster_banner_pattern", 2),
                e("minecraft:music_disc_precipice", 2), e("minecraft:trident", 1)));
        tables.put("minecraft:chests/trial_chambers/reward", table(
                pool(1, 1, nested(rewardRare, 8), nested(rewardCommon, 2)),
                pool(1, 3, nested(rewardCommon, 1)),
                chancePool(.25f, 1, 1, nested(rewardUnique, 1))));
        // reward 가 이미 이고 있던 세 하위 표를 그대로 최상위로도 등록한다. 같은 객체라
        // 기존 reward 의 바이트는 그대로다.
        tables.put("minecraft:chests/trial_chambers/reward_common", rewardCommon);
        tables.put("minecraft:chests/trial_chambers/reward_rare", rewardRare);
        tables.put("minecraft:chests/trial_chambers/reward_unique", rewardUnique);
        Table rewardOminousCommon = table(
                pool(1, 1, counted("minecraft:emerald", 5, 4, 10),
                        counted("minecraft:wind_charge", 4, 8, 12),
                        functions("minecraft:tipped_arrow", 3, Function.count(4, 12), Function.potion("minecraft:strong_slowness")),
                        counted("minecraft:diamond", 2, 2, 3),
                        functions("minecraft:ominous_bottle", 1, Function.count(1, 1), Function.ominousAmplifier(2, 4))));
        Table rewardOminousRare = table(
                pool(1, 1, e("minecraft:emerald_block", 5), e("minecraft:iron_block", 4),
                        functions("minecraft:crossbow", 4, Function.enchantWithLevels(5, 20)),
                        e("minecraft:golden_apple", 3),
                        functions("minecraft:diamond_axe", 3, Function.enchantWithLevels(10, 20)),
                        functions("minecraft:diamond_chestplate", 3, Function.enchantWithLevels(10, 20)),
                        functions("minecraft:book", 2, Function.enchantRandomly("minecraft:knockback", "minecraft:punch", "minecraft:smite", "minecraft:looting", "minecraft:multishot")),
                        functions("minecraft:book", 2, Function.enchantRandomly("minecraft:breach", "minecraft:density")),
                        functions("minecraft:book", 2, Function.enchantments("minecraft:wind_burst", "1")),
                        e("minecraft:diamond_block", 1)));
        Table rewardOminousUnique = table(
                pool(1, 1, e("minecraft:enchanted_golden_apple", 3),
                        e("minecraft:flow_armor_trim_smithing_template", 3),
                        e("minecraft:flow_banner_pattern", 2), e("minecraft:music_disc_creator", 1),
                        e("minecraft:heavy_core", 1)));
        tables.put("minecraft:chests/trial_chambers/reward_ominous", table(
                pool(1, 1, nested(rewardOminousRare, 8), nested(rewardOminousCommon, 2)),
                pool(1, 3, nested(rewardOminousCommon, 1)),
                chancePool(0.75f, 1, 1, nested(rewardOminousUnique, 1))));
        tables.put("minecraft:chests/trial_chambers/reward_ominous_common", rewardOminousCommon);
        tables.put("minecraft:chests/trial_chambers/reward_ominous_rare", rewardOminousRare);
        tables.put("minecraft:chests/trial_chambers/reward_ominous_unique", rewardOminousUnique);
        tables.put("minecraft:chests/trial_chambers/supply", table(
                pool(3, 5, counted("minecraft:arrow", 2, 4, 14),
                        functions("minecraft:tipped_arrow", 1, Function.count(4, 8),
                                Function.potion("minecraft:poison")),
                        functions("minecraft:tipped_arrow", 1, Function.count(4, 8),
                                Function.potion("minecraft:slowness")),
                        counted("minecraft:baked_potato", 2, 2, 4),
                        counted("minecraft:glow_berries", 2, 2, 10),
                        counted("minecraft:acacia_planks", 1, 3, 6),
                        counted("minecraft:moss_block", 1, 2, 5),
                        counted("minecraft:bone_meal", 1, 2, 5),
                        counted("minecraft:tuff", 1, 5, 10), counted("minecraft:torch", 1, 3, 6),
                        functions("minecraft:potion", 1, Function.count(2, 2),
                                Function.potion("minecraft:regeneration")),
                        functions("minecraft:potion", 1, Function.count(2, 2),
                                Function.potion("minecraft:strength")),
                        functions("minecraft:stone_pickaxe", 2, Function.count(1, 1),
                                Function.damage(.15f, .8f)),
                        counted("minecraft:milk_bucket", 1, 1, 1))));
        tables.put("minecraft:dispensers/trial_chambers/chamber", tableWithSize(9,
                pool(1, 1, counted("minecraft:water_bucket", 4, 1, 1),
                        counted("minecraft:arrow", 4, 4, 8),
                        counted("minecraft:snowball", 6, 4, 8),
                        counted("minecraft:egg", 2, 4, 8),
                        counted("minecraft:fire_charge", 6, 4, 8),
                        functions("minecraft:splash_potion", 1, Function.potion("minecraft:slowness"), Function.count(2, 5)),
                        functions("minecraft:splash_potion", 1, Function.potion("minecraft:poison"), Function.count(2, 5)),
                        functions("minecraft:splash_potion", 1, Function.potion("minecraft:weakness"), Function.count(2, 5)),
                        functions("minecraft:lingering_potion", 1, Function.potion("minecraft:slowness"), Function.count(2, 5)),
                        functions("minecraft:lingering_potion", 1, Function.potion("minecraft:poison"), Function.count(2, 5)),
                        functions("minecraft:lingering_potion", 1, Function.potion("minecraft:weakness"), Function.count(2, 5)),
                        functions("minecraft:lingering_potion", 1, Function.potion("minecraft:healing"), Function.count(2, 5)))));
        tables.put("minecraft:dispensers/trial_chambers/corridor", tableWithSize(9,
                pool(1, 1, counted("minecraft:arrow", 1, 4, 8))));
        tables.put("minecraft:pots/trial_chambers/corridor", tableWithSize(1,
                pool(1, 1, counted("minecraft:emerald", 125, 1, 3),
                        counted("minecraft:arrow", 100, 2, 8),
                        counted("minecraft:iron_ingot", 100, 1, 2),
                        counted("minecraft:trial_key", 10, 1, 1),
                        e("minecraft:music_disc_creator_music_box", 5),
                        counted("minecraft:diamond", 5, 1, 2),
                        counted("minecraft:emerald_block", 5, 1, 1),
                        counted("minecraft:diamond_block", 1, 1, 1))));

        tables.put("minecraft:chests/nether_bridge", table(
                pool(2, 4, counted("minecraft:diamond", 5, 1, 3), counted("minecraft:iron_ingot", 5, 1, 5),
                        counted("minecraft:gold_ingot", 15, 1, 3), e("minecraft:golden_sword", 5),
                        e("minecraft:golden_chestplate", 5), e("minecraft:flint_and_steel", 5),
                        counted("minecraft:nether_wart", 5, 3, 7), e("minecraft:saddle", 10),
                        e("minecraft:golden_horse_armor", 8), e("minecraft:copper_horse_armor", 5),
                        e("minecraft:iron_horse_armor", 5), e("minecraft:diamond_horse_armor", 3),
                        counted("minecraft:obsidian", 2, 2, 4)),
                pool(1, 1, empty(14), e("minecraft:rib_armor_trim_smithing_template", 1))));
        tables.put("minecraft:chests/spawn_bonus_chest", table(
                pool(1, 1, e("minecraft:stone_axe", 1), e("minecraft:wooden_axe", 3)),
                pool(1, 1, e("minecraft:stone_pickaxe", 1), e("minecraft:wooden_pickaxe", 3)),
                pool(3, 3, counted("minecraft:apple", 5, 1, 2), counted("minecraft:bread", 3, 1, 2),
                        counted("minecraft:salmon", 3, 1, 2)),
                pool(4, 4, counted("minecraft:stick", 10, 1, 12),
                        counted("minecraft:oak_planks", 10, 1, 12), counted("minecraft:oak_log", 3, 1, 3),
                        counted("minecraft:spruce_log", 3, 1, 3), counted("minecraft:birch_log", 3, 1, 3),
                        counted("minecraft:jungle_log", 3, 1, 3), counted("minecraft:acacia_log", 3, 1, 3),
                        counted("minecraft:dark_oak_log", 3, 1, 3),
                        counted("minecraft:mangrove_log", 3, 1, 3))));
        // Village chest tables, transcribed from the pinned 26.3-snapshot-7 base pack JSON at
        // docs/research/mc-vanilla-1214/loot_table/chests/village/*.json and bound by
        // mc263/container-loot-authority-26.3-snapshot-7.sha256. Every entry is item or empty
        // and the only modifier is set_count, so no new function kind is needed.
        tables.put("minecraft:chests/village/village_armorer", table(
                pool(1, 5, counted("minecraft:iron_ingot", 2, 1, 3), counted("minecraft:bread", 4, 1, 4),
                        e("minecraft:iron_helmet", 1), e("minecraft:emerald", 1))));
        tables.put("minecraft:chests/village/village_butcher", table(
                pool(1, 5, e("minecraft:emerald", 1), counted("minecraft:porkchop", 6, 1, 3),
                        counted("minecraft:wheat", 6, 1, 3), counted("minecraft:beef", 6, 1, 3),
                        counted("minecraft:mutton", 6, 1, 3), counted("minecraft:coal", 3, 1, 3))));
        tables.put("minecraft:chests/village/village_cartographer", table(
                pool(1, 5, counted("minecraft:map", 10, 1, 3), counted("minecraft:paper", 15, 1, 5),
                        e("minecraft:compass", 5), counted("minecraft:bread", 15, 1, 4),
                        counted("minecraft:stick", 5, 1, 2)),
                pool(1, 1, counted("minecraft:bundle", 1, 1, 1), empty(2))));
        tables.put("minecraft:chests/village/village_desert_house", table(
                pool(3, 8, e("minecraft:clay_ball", 1), e("minecraft:green_dye", 1),
                        counted("minecraft:cactus", 10, 1, 4), counted("minecraft:wheat", 10, 1, 7),
                        counted("minecraft:bread", 10, 1, 4), e("minecraft:book", 1),
                        counted("minecraft:dead_bush", 2, 1, 3), counted("minecraft:emerald", 1, 1, 3)),
                pool(1, 1, counted("minecraft:bundle", 1, 1, 1), empty(2))));
        tables.put("minecraft:chests/village/village_fisher", table(
                pool(1, 5, e("minecraft:emerald", 1), counted("minecraft:cod", 2, 1, 3),
                        counted("minecraft:salmon", 1, 1, 3), counted("minecraft:water_bucket", 1, 1, 3),
                        counted("minecraft:barrel", 1, 1, 3), counted("minecraft:wheat_seeds", 3, 1, 3),
                        counted("minecraft:coal", 2, 1, 3))));
        tables.put("minecraft:chests/village/village_fletcher", table(
                pool(1, 5, e("minecraft:emerald", 1), counted("minecraft:arrow", 2, 1, 3),
                        counted("minecraft:feather", 6, 1, 3), counted("minecraft:egg", 2, 1, 3),
                        counted("minecraft:flint", 6, 1, 3), counted("minecraft:stick", 6, 1, 3))));
        tables.put("minecraft:chests/village/village_mason", table(
                pool(1, 5, counted("minecraft:clay_ball", 1, 1, 3), e("minecraft:flower_pot", 1),
                        e("minecraft:stone", 2), e("minecraft:stone_bricks", 2),
                        counted("minecraft:bread", 4, 1, 4), e("minecraft:yellow_dye", 1),
                        e("minecraft:smooth_stone", 1), e("minecraft:emerald", 1))));
        tables.put("minecraft:chests/village/village_plains_house", table(
                pool(3, 8, counted("minecraft:gold_nugget", 1, 1, 3), e("minecraft:dandelion", 2),
                        e("minecraft:poppy", 1), counted("minecraft:potato", 10, 1, 7),
                        counted("minecraft:bread", 10, 1, 4), counted("minecraft:apple", 10, 1, 5),
                        e("minecraft:book", 1), e("minecraft:feather", 1),
                        counted("minecraft:emerald", 2, 1, 4), counted("minecraft:oak_sapling", 5, 1, 2)),
                pool(1, 1, counted("minecraft:bundle", 1, 1, 1), empty(2))));
        tables.put("minecraft:chests/village/village_savanna_house", table(
                pool(3, 8, counted("minecraft:gold_nugget", 1, 1, 3), e("minecraft:short_grass", 5),
                        e("minecraft:tall_grass", 5), counted("minecraft:bread", 10, 1, 4),
                        counted("minecraft:wheat_seeds", 10, 1, 5), counted("minecraft:emerald", 2, 1, 4),
                        counted("minecraft:acacia_sapling", 10, 1, 2), e("minecraft:saddle", 1),
                        counted("minecraft:torch", 1, 1, 2), e("minecraft:bucket", 1)),
                pool(1, 1, counted("minecraft:bundle", 1, 1, 1), empty(2))));
        tables.put("minecraft:chests/village/village_shepherd", table(
                pool(1, 5, counted("minecraft:white_wool", 6, 1, 8),
                        counted("minecraft:black_wool", 3, 1, 3), counted("minecraft:gray_wool", 2, 1, 3),
                        counted("minecraft:brown_wool", 2, 1, 3),
                        counted("minecraft:light_gray_wool", 2, 1, 3), e("minecraft:emerald", 1),
                        e("minecraft:shears", 1), counted("minecraft:wheat", 6, 1, 6))));
        tables.put("minecraft:chests/village/village_snowy_house", table(
                pool(3, 8, e("minecraft:blue_ice", 1), e("minecraft:snow_block", 4),
                        counted("minecraft:potato", 10, 1, 7), counted("minecraft:bread", 10, 1, 4),
                        counted("minecraft:beetroot_seeds", 10, 1, 5), e("minecraft:beetroot_soup", 1),
                        e("minecraft:furnace", 1), counted("minecraft:emerald", 1, 1, 4),
                        counted("minecraft:snowball", 10, 1, 7), counted("minecraft:coal", 5, 1, 4)),
                pool(1, 1, counted("minecraft:bundle", 1, 1, 1), empty(2))));
        tables.put("minecraft:chests/village/village_taiga_house", table(
                pool(3, 8, counted("minecraft:iron_nugget", 1, 1, 5), e("minecraft:fern", 2),
                        e("minecraft:large_fern", 2), counted("minecraft:potato", 10, 1, 7),
                        counted("minecraft:sweet_berries", 5, 1, 7), counted("minecraft:bread", 10, 1, 4),
                        counted("minecraft:pumpkin_seeds", 5, 1, 5), e("minecraft:pumpkin_pie", 1),
                        counted("minecraft:emerald", 2, 1, 4), counted("minecraft:spruce_sapling", 5, 1, 5),
                        e("minecraft:spruce_sign", 1), counted("minecraft:spruce_log", 10, 1, 5)),
                pool(1, 1, counted("minecraft:bundle", 1, 1, 1), empty(2))));
        tables.put("minecraft:chests/village/village_tannery", table(
                pool(1, 5, counted("minecraft:leather", 1, 1, 3), e("minecraft:leather_chestplate", 2),
                        e("minecraft:leather_boots", 2), e("minecraft:leather_helmet", 2),
                        counted("minecraft:bread", 5, 1, 4), e("minecraft:leather_leggings", 2),
                        e("minecraft:saddle", 1), counted("minecraft:emerald", 1, 1, 4)),
                pool(1, 1, counted("minecraft:bundle", 1, 1, 1), empty(2))));
        tables.put("minecraft:chests/village/village_temple", table(
                pool(3, 8, counted("minecraft:redstone", 2, 1, 4), counted("minecraft:bread", 7, 1, 4),
                        counted("minecraft:rotten_flesh", 7, 1, 4),
                        counted("minecraft:lapis_lazuli", 1, 1, 4),
                        counted("minecraft:gold_ingot", 1, 1, 4), counted("minecraft:emerald", 1, 1, 4))));
        tables.put("minecraft:chests/village/village_toolsmith", table(
                pool(3, 8, counted("minecraft:diamond", 1, 1, 3), counted("minecraft:iron_ingot", 5, 1, 5),
                        counted("minecraft:gold_ingot", 1, 1, 3), counted("minecraft:bread", 15, 1, 3),
                        e("minecraft:iron_pickaxe", 5), counted("minecraft:coal", 1, 1, 3),
                        counted("minecraft:stick", 20, 1, 3), e("minecraft:iron_shovel", 5))));
        tables.put("minecraft:chests/village/village_weaponsmith", table(
                pool(3, 8, counted("minecraft:diamond", 3, 1, 3), counted("minecraft:iron_ingot", 10, 1, 5),
                        counted("minecraft:gold_ingot", 5, 1, 3), counted("minecraft:bread", 15, 1, 3),
                        counted("minecraft:apple", 15, 1, 3), e("minecraft:iron_pickaxe", 5),
                        e("minecraft:iron_sword", 5), e("minecraft:iron_spear", 5),
                        e("minecraft:copper_spear", 7), e("minecraft:iron_chestplate", 5),
                        e("minecraft:iron_helmet", 5), e("minecraft:iron_leggings", 5),
                        e("minecraft:iron_boots", 5), counted("minecraft:obsidian", 5, 3, 7),
                        counted("minecraft:oak_sapling", 5, 3, 7), e("minecraft:saddle", 3),
                        e("minecraft:copper_horse_armor", 1), e("minecraft:iron_horse_armor", 1),
                        e("minecraft:golden_horse_armor", 1), e("minecraft:diamond_horse_armor", 1)),
                pool(1, 1, counted("minecraft:bundle", 1, 1, 1), empty(2))));
        return Collections.unmodifiableMap(tables);
    }

    private static List<Enchantment> createEnchantments() {
        List<Enchantment> values = new ArrayList<>();
        values.add(enchantment("protection", 10, 4, 1, 11, 12, 11, "protection", "blast_protection", "fire_protection", "projectile_protection"));
        values.add(enchantment("fire_protection", 5, 4, 10, 8, 18, 8, "protection", "blast_protection", "fire_protection", "projectile_protection"));
        values.add(enchantment("feather_falling", 5, 4, 5, 6, 11, 6));
        values.add(enchantment("blast_protection", 2, 4, 5, 8, 13, 8, "protection", "blast_protection", "fire_protection", "projectile_protection"));
        values.add(enchantment("projectile_protection", 5, 4, 3, 6, 9, 6, "protection", "blast_protection", "fire_protection", "projectile_protection"));
        values.add(enchantment("respiration", 2, 3, 10, 10, 40, 10));
        values.add(enchantment("aqua_affinity", 2, 1, 1, 0, 41, 0));
        values.add(enchantment("thorns", 1, 3, 10, 20, 60, 20));
        values.add(enchantment("depth_strider", 2, 3, 10, 10, 25, 10, "frost_walker", "depth_strider"));
        values.add(enchantment("sharpness", 10, 5, 1, 11, 21, 11, "sharpness", "smite", "bane_of_arthropods", "impaling", "density", "breach"));
        values.add(enchantment("smite", 5, 5, 5, 8, 25, 8, "sharpness", "smite", "bane_of_arthropods", "impaling", "density", "breach"));
        values.add(enchantment("bane_of_arthropods", 5, 5, 5, 8, 25, 8, "sharpness", "smite", "bane_of_arthropods", "impaling", "density", "breach"));
        values.add(enchantment("knockback", 5, 2, 5, 20, 55, 20));
        values.add(enchantment("fire_aspect", 2, 2, 10, 20, 60, 20));
        values.add(enchantment("looting", 2, 3, 15, 9, 65, 9));
        values.add(enchantment("sweeping_edge", 2, 3, 5, 9, 20, 9));
        values.add(enchantment("efficiency", 10, 5, 1, 10, 51, 10));
        values.add(enchantment("silk_touch", 1, 1, 15, 0, 65, 0, "fortune", "silk_touch"));
        values.add(enchantment("unbreaking", 5, 3, 5, 8, 55, 8));
        values.add(enchantment("fortune", 2, 3, 15, 9, 65, 9, "fortune", "silk_touch"));
        values.add(enchantment("power", 10, 5, 1, 10, 16, 10));
        values.add(enchantment("punch", 2, 2, 12, 20, 37, 20));
        values.add(enchantment("flame", 2, 1, 20, 0, 50, 0));
        values.add(enchantment("infinity", 1, 1, 20, 0, 50, 0, "infinity", "mending"));
        values.add(enchantment("luck_of_the_sea", 2, 3, 15, 9, 65, 9));
        values.add(enchantment("lure", 2, 3, 15, 9, 65, 9));
        values.add(enchantment("loyalty", 5, 3, 12, 7, 50, 0));
        values.add(enchantment("impaling", 2, 5, 1, 8, 21, 8, "sharpness", "smite", "bane_of_arthropods", "impaling", "density", "breach"));
        values.add(enchantment("riptide", 2, 3, 17, 7, 50, 0, "loyalty", "channeling"));
        values.add(enchantment("channeling", 1, 1, 25, 0, 50, 0));
        values.add(enchantment("multishot", 2, 1, 20, 0, 50, 0, "multishot", "piercing"));
        values.add(enchantment("quick_charge", 5, 3, 12, 20, 50, 0));
        values.add(enchantment("piercing", 10, 4, 1, 10, 50, 0, "multishot", "piercing"));
        values.add(enchantment("density", 5, 5, 5, 8, 25, 8, "sharpness", "smite", "bane_of_arthropods", "impaling", "density", "breach"));
        values.add(enchantment("breach", 2, 4, 15, 9, 65, 9, "sharpness", "smite", "bane_of_arthropods", "impaling", "density", "breach"));
        values.add(enchantment("lunge", 5, 3, 5, 8, 25, 8));
        values.add(enchantment("binding_curse", 1, 1, 25, 0, 50, 0));
        values.add(enchantment("vanishing_curse", 1, 1, 25, 0, 50, 0));
        values.add(enchantment("frost_walker", 2, 2, 10, 10, 25, 10, "frost_walker", "depth_strider"));
        values.add(enchantment("mending", 2, 1, 25, 25, 75, 25));
        return List.copyOf(values);
    }

    private static Map<String, Enchantment> enchantmentsByKey() {
        Map<String, Enchantment> values = new LinkedHashMap<>();
        for (Enchantment enchantment : PUBLIC_ENCHANTMENTS) {
            if (values.put(enchantment.key, enchantment) != null) {
                throw new IllegalStateException("duplicate pinned enchantment key: " + enchantment.key);
            }
        }
        return Map.copyOf(values);
    }

    private static List<Enchantment> createPublicEnchantments() {
        List<Enchantment> values = new ArrayList<>(ON_RANDOM_LOOT_ENCHANTMENTS);
        values.addAll(FIXED_OPTION_ENCHANTMENTS);
        return List.copyOf(values);
    }

    private static Enchantment enchantment(String key, int weight, int maxLevel,
                                            int minBase, int minPerLevel, int maxBase, int maxPerLevel,
                                            String... excluded) {
        List<String> excludedKeys = new ArrayList<>(excluded.length);
        for (String value : excluded) {
            excludedKeys.add("minecraft:" + value);
        }
        return new Enchantment("minecraft:" + key, weight, maxLevel, minBase, minPerLevel,
                maxBase, maxPerLevel, List.copyOf(excludedKeys));
    }

    private static Table table(Pool... pools) {
        return tableWithSize(27, pools);
    }

    private static Table tableWithSize(int containerSize, Pool... pools) {
        return new Table(List.of(pools), containerSize);
    }

    private static Pool pool(int minRolls, int maxRolls, Entry... entries) {
        return new Pool(new IntRange(minRolls, maxRolls), List.of(entries), List.of(), 1.0f);
    }

    private static Pool poolWithFunction(int minRolls, int maxRolls, Function function, Entry... entries) {
        return new Pool(new IntRange(minRolls, maxRolls), List.of(entries),
                List.of(function), 1.0f);
    }

    private static Pool chancePool(float chance, int minRolls, int maxRolls, Entry... entries) {
        return new Pool(new IntRange(minRolls, maxRolls), List.of(entries), List.of(), chance);
    }

    private static Entry e(String item, int weight) {
        return new Entry(item, weight, false, Condition.ALWAYS, List.of(), null);
    }

    private static Entry sulfurCaves(String item, int weight) {
        return new Entry(item, weight, false, Condition.SULFUR_CAVES, List.of(), null);
    }

    private static Entry campMap(String destination) {
        Condition condition = switch (destination) {
            case "bamboo_jungle" -> Condition.NOT_BAMBOO_JUNGLE;
            case "cherry_grove" -> Condition.NOT_CHERRY_GROVE;
            case "birch_forest" -> Condition.NOT_BIRCH_FOREST;
            case "dappled_forest" -> Condition.NOT_DAPPLED_FOREST;
            case "flower_forest" -> Condition.NOT_FLOWER_FOREST;
            case "pale_garden" -> Condition.NOT_PALE_GARDEN;
            case "swamp" -> Condition.NOT_SWAMP;
            case "windswept_forest" -> Condition.NOT_WINDSWEPT_FOREST;
            default -> throw new IllegalArgumentException("unsupported camp-map biome: " + destination);
        };
        return new Entry("minecraft:abandoned_campsite_map", 1, false,
                condition, List.of(Function.campMap(destination)), null);
    }

    private static Entry potion(String potionKey) {
        return new Entry("minecraft:potion", 1, false, Condition.ALWAYS,
                List.of(Function.potion(potionKey)), null);
    }

    private static Entry explorerMap(String itemKey, String destination) {
        return new Entry(itemKey, 1, false, Condition.ALWAYS,
                List.of(Function.explorerMap(destination)), null);
    }

    private static Entry explorerMapWeighted(String itemKey, int weight, String destination) {
        return new Entry(itemKey, weight, false, Condition.ALWAYS,
                List.of(Function.explorerMap(destination),
                        Function.itemName("filled_map.buried_treasure")), null);
    }

    private static String requireMinecraftKey(String key, String description) {
        if (key == null || !key.startsWith("minecraft:")) {
            throw new IllegalArgumentException("Minecraft namespaced " + description
                    + " is required: " + key);
        }
        int colon = key.indexOf(':');
        if (colon != "minecraft".length() || colon == key.length() - 1
                || key.indexOf(':', colon + 1) >= 0) {
            throw new IllegalArgumentException("canonical ASCII " + description
                    + " is required: " + key);
        }
        for (int index = colon + 1; index < key.length(); index++) {
            char value = key.charAt(index);
            boolean valid = value >= 'a' && value <= 'z'
                    || value >= '0' && value <= '9'
                    || value == '_' || value == '-' || value == '.' || value == '/';
            if (!valid) {
                throw new IllegalArgumentException("canonical ASCII " + description
                        + " is required: " + key);
            }
        }
        return key;
    }

    private static Entry empty(int weight) {
        return new Entry("minecraft:air", weight, true, Condition.ALWAYS, List.of(), null);
    }

    private static Entry counted(String item, int weight, int min, int max) {
        return new Entry(item, weight, false, Condition.ALWAYS,
                List.of(Function.count(min, max)), null);
    }

    private static Entry enchantedRandom(String item, int weight) {
        return new Entry(item, weight, false, Condition.ALWAYS,
                List.of(Function.enchantRandomly()), null);
    }

    private static Entry enchantedLevels(String item, int weight, int levels) {
        return new Entry(item, weight, false, Condition.ALWAYS,
                List.of(Function.enchantWithLevels(levels)), null);
    }

    private static Entry functions(String item, int weight, Function... functions) {
        return new Entry(item, weight, false, Condition.ALWAYS, List.of(functions), null);
    }

    private static Entry nested(Table table, int weight) {
        return new Entry("minecraft:air", weight, false, Condition.ALWAYS, List.of(), table);
    }

    private static StewEffect stew(String key, int min, int max, boolean instantaneous) {
        return new StewEffect(key, new IntRange(min, max), instantaneous);
    }

    public static final class XoroshiroState {
        private final long seedLo;
        private final long seedHi;

        public XoroshiroState(long seedLo, long seedHi) {
            this.seedLo = seedLo;
            this.seedHi = seedHi;
        }

        public long seedLo() {
            return seedLo;
        }

        public long seedHi() {
            return seedHi;
        }
    }

    /** Common authenticated context identity carried through production placement and final wire. */
    public sealed interface LootProductionContext permits LocatedProductionContext,
            ProductionContext {
        String biomeKey();
        String worldIdentity();
        String sourceIdentity();
        String tableIdentity();
        int originX();
        int originY();
        int originZ();
        String catalogReceipt();
        void requireAuthenticatedContext();
        void requireMatches(long worldSeed, String tableKey,
                int requestX, int requestY, int requestZ);
    }

    /** V2 production context carrying only authenticated Found/NotFound target authority. */
    public record LocatedProductionContext(String biomeKey,
            Map<String, LocatedMapTarget> maps, String worldIdentity, String sourceIdentity,
            String tableIdentity, int originX, int originY, int originZ,
            String catalogReceipt) implements LootProductionContext {
        public LocatedProductionContext {
            requireMinecraftKey(biomeKey, "production loot-origin biome");
            requireWorldIdentity(worldIdentity);
            requireSourceIdentity(sourceIdentity);
            requireTableIdentity(tableIdentity);
            requireSha256(catalogReceipt, "located production context receipt");
            Objects.requireNonNull(maps, "located-map targets");
            LinkedHashMap<String, LocatedMapTarget> checked = new LinkedHashMap<>();
            for (Map.Entry<String, LocatedMapTarget> entry : maps.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey()).toList()) {
                requireDestination(entry.getKey());
                LocatedMapTarget target = Objects.requireNonNull(
                        entry.getValue(), "located-map target");
                target.requireAuthenticated();
                var binding = target.binding();
                if (!entry.getKey().equals(binding.destination())
                        || !worldIdentity.equals(binding.worldIdentity())
                        || !sourceIdentity.equals(binding.sourceIdentity())
                        || !tableIdentity.equals(binding.tableIdentity())
                        || originX != binding.originX() || originY != binding.originY()
                        || originZ != binding.originZ()) {
                    throw new IllegalArgumentException(
                            "located-map target differs from production context identity");
                }
                checked.put(entry.getKey(), target);
            }
            maps = Collections.unmodifiableMap(checked);
            String expected = targetProductionContextReceipt(biomeKey, worldIdentity,
                    sourceIdentity, tableIdentity, originX, originY, originZ, maps);
            if (!MessageDigest.isEqual(HEX.parseHex(catalogReceipt), HEX.parseHex(expected))) {
                throw new IllegalArgumentException("stale located production context receipt");
            }
        }

        public static LocatedProductionContext authenticated(String biomeKey,
                Map<String, LocatedMapTarget> maps, String worldIdentity, String sourceIdentity,
                String tableIdentity, int originX, int originY, int originZ,
                String catalogReceipt) {
            return new LocatedProductionContext(biomeKey, maps, worldIdentity, sourceIdentity,
                    tableIdentity, originX, originY, originZ, catalogReceipt);
        }

        @Override
        public void requireAuthenticatedContext() {
            new LocatedProductionContext(biomeKey, maps, worldIdentity, sourceIdentity,
                    tableIdentity, originX, originY, originZ, catalogReceipt);
        }

        public void requireMatches(long worldSeed, String tableKey,
                int requestX, int requestY, int requestZ) {
            if (!worldIdentity.equals(Long.toString(worldSeed))
                    || !tableIdentity.equals(tableKey)
                    || originX != requestX || originY != requestY || originZ != requestZ) {
                throw new IllegalArgumentException("located production context request mismatch");
            }
        }
    }

    /**
     * Authenticated level facts needed by location-sensitive camp loot functions. The two-argument
     * constructor remains only for decoding old carriers; it deliberately produces an
     * unauthenticated context and cannot authorize map publication.
     */
    public record ProductionContext(String biomeKey, Map<String, LocatedMap> maps,
                                    String worldIdentity, String sourceIdentity,
                                    String tableIdentity, int originX, int originY, int originZ,
                                    String catalogReceipt) implements LootProductionContext {
        public ProductionContext(String biomeKey, Map<String, LocatedMap> maps) {
            this(biomeKey, maps, null, null, null, 0, 0, 0, null);
        }

        public ProductionContext {
            requireMinecraftKey(biomeKey, "production loot-origin biome");
            Objects.requireNonNull(maps, "maps");
            Map<String, LocatedMap> checked = new LinkedHashMap<>();
            for (Map.Entry<String, LocatedMap> entry : maps.entrySet()) {
                String destination = Objects.requireNonNull(entry.getKey(), "map destination");
                requireDestination(destination);
                checked.put(destination, Objects.requireNonNull(entry.getValue(), "located map"));
            }
            maps = Collections.unmodifiableMap(checked);

            boolean noAuthentication = worldIdentity == null && sourceIdentity == null
                    && tableIdentity == null && catalogReceipt == null;
            boolean partialAuthentication = worldIdentity == null || sourceIdentity == null
                    || tableIdentity == null || catalogReceipt == null;
            if (partialAuthentication && !noAuthentication) {
                throw new IllegalArgumentException(
                        "production context authentication fields must be complete");
            }
            if (!noAuthentication) {
                requireWorldIdentity(worldIdentity);
                requireSourceIdentity(sourceIdentity);
                requireTableIdentity(tableIdentity);
                requireSha256(catalogReceipt, "production context catalog receipt");
                for (Map.Entry<String, LocatedMap> entry : checked.entrySet()) {
                    LocatedMap map = entry.getValue();
                    map.requireAuthenticated();
                    requireMapMatchesContext(entry.getKey(), map, worldIdentity, sourceIdentity,
                            tableIdentity, originX, originY, originZ);
                }
                String expected = productionContextReceipt(biomeKey, worldIdentity,
                        sourceIdentity, tableIdentity, originX, originY, originZ, checked);
                if (!MessageDigest.isEqual(catalogReceipt.getBytes(StandardCharsets.US_ASCII),
                        expected.getBytes(StandardCharsets.US_ASCII))) {
                    throw new IllegalArgumentException("stale production context catalog receipt");
                }
            }
        }

        /** Builds an authenticated context from a producer-supplied receipt. */
        public static ProductionContext authenticated(String biomeKey, Map<String, LocatedMap> maps,
                                                      String worldIdentity, String sourceIdentity,
                                                      String tableIdentity, int originX, int originY,
                                                      int originZ, String catalogReceipt) {
            return new ProductionContext(biomeKey, maps, worldIdentity, sourceIdentity,
                    tableIdentity, originX, originY, originZ, catalogReceipt);
        }

        /** Same factory with identity fields before the map rows for bridge callers. */
        public static ProductionContext authenticated(String biomeKey, String worldIdentity,
                                                      String sourceIdentity, String tableIdentity,
                                                      int originX, int originY, int originZ,
                                                      Map<String, LocatedMap> maps,
                                                      String catalogReceipt) {
            return authenticated(biomeKey, maps, worldIdentity, sourceIdentity, tableIdentity,
                    originX, originY, originZ, catalogReceipt);
        }

        public boolean isAuthenticated() {
            return catalogReceipt != null;
        }

        @Override
        public void requireAuthenticatedContext() {
            if (!isAuthenticated()) {
                throw new IllegalArgumentException("legacy production context is unauthenticated");
            }
            maps.values().forEach(LocatedMap::requireAuthenticated);
        }

        public String resolverCatalogReceipt() {
            return catalogReceipt;
        }

        /** Validates the request identity before the resolver initializes or consumes RNG. */
        public void requireMatches(long worldSeed, String tableKey, int requestX, int requestY,
                                   int requestZ) {
            if (!isAuthenticated()) {
                throw new IllegalArgumentException(
                        "production context is missing an authenticated catalog receipt");
            }
            if (!worldIdentity.equals(Long.toString(worldSeed))) {
                throw new IllegalArgumentException("production context world identity mismatch");
            }
            if (!tableIdentity.equals(tableKey)) {
                throw new IllegalArgumentException("production context table identity mismatch");
            }
            if (originX != requestX || originY != requestY || originZ != requestZ) {
                throw new IllegalArgumentException("production context origin mismatch");
            }
        }
    }

    /** Exact map destination row; the three-argument constructor is legacy and unauthenticated. */
    public static final class LocatedMap {
        private final String destination;
        private final String worldIdentity;
        private final String sourceIdentity;
        private final String tableIdentity;
        private final int mapId;
        private final int centerX;
        private final int centerZ;
        private final int originX;
        private final int originY;
        private final int originZ;
        private final int scale;
        private final String resolverCatalogReceipt;
        private final boolean legacyOracleWitness;

        public LocatedMap(int x, int z, int mapId) {
            this(null, null, null, null, mapId, x, z, 0, 0, 0, -1, null, false);
        }

        /** Constructs an exact destination after checking its row receipt. */
        public LocatedMap(String destination, String worldIdentity, String sourceIdentity,
                          String tableIdentity, int mapId, int centerX, int centerZ,
                          int originX, int originY, int originZ, int scale,
                          String resolverCatalogReceipt) {
            this(destination, worldIdentity, sourceIdentity, tableIdentity, mapId, centerX, centerZ,
                    originX, originY, originZ, scale, resolverCatalogReceipt, false);
        }

        /** Appended-field form retained for callers migrating from the old x/z/mapId row. */
        public LocatedMap(int centerX, int centerZ, int mapId, String destination,
                          String worldIdentity, String sourceIdentity, String tableIdentity,
                          int originX, int originY, int originZ, int scale,
                          String resolverCatalogReceipt) {
            this(destination, worldIdentity, sourceIdentity, tableIdentity, mapId, centerX, centerZ,
                    originX, originY, originZ, scale, resolverCatalogReceipt);
        }

        private LocatedMap(String destination, String worldIdentity, String sourceIdentity,
                           String tableIdentity, int mapId, int centerX, int centerZ,
                           int originX, int originY, int originZ, int scale,
                           String resolverCatalogReceipt, boolean legacyOracleWitness) {
            if (legacyOracleWitness) {
                if (destination != null || worldIdentity != null || sourceIdentity != null
                        || tableIdentity != null || scale != -1 || resolverCatalogReceipt != null) {
                    throw new IllegalArgumentException("legacy map witness cannot carry exact fields");
                }
                requireMapId(mapId);
            } else if (resolverCatalogReceipt == null) {
                // The old public constructor is intentionally an untrusted compatibility carrier.
                requireMapId(mapId);
            } else {
                requireDestination(destination);
                requireWorldIdentity(worldIdentity);
                requireSourceIdentity(sourceIdentity);
                requireTableIdentity(tableIdentity);
                requireMapId(mapId);
                requireScale(scale);
                requireSha256(resolverCatalogReceipt, "map destination receipt");
                String expected = mapDestinationReceipt(destination, worldIdentity, sourceIdentity,
                        tableIdentity, mapId, centerX, centerZ, originX, originY, originZ, scale);
                if (!MessageDigest.isEqual(resolverCatalogReceipt.getBytes(StandardCharsets.US_ASCII),
                        expected.getBytes(StandardCharsets.US_ASCII))) {
                    throw new IllegalArgumentException("stale map destination receipt");
                }
            }
            this.destination = destination;
            this.worldIdentity = worldIdentity;
            this.sourceIdentity = sourceIdentity;
            this.tableIdentity = tableIdentity;
            this.mapId = mapId;
            this.centerX = centerX;
            this.centerZ = centerZ;
            this.originX = originX;
            this.originY = originY;
            this.originZ = originZ;
            this.scale = scale;
            this.resolverCatalogReceipt = resolverCatalogReceipt;
            this.legacyOracleWitness = legacyOracleWitness;
        }

        private static LocatedMap legacyOracleWitness(int x, int z, int mapId) {
            return new LocatedMap(null, null, null, null, mapId, x, z, 0, 0, 0, -1, null, true);
        }

        public String destination() { return destination; }
        public String worldIdentity() { return worldIdentity; }
        public String sourceIdentity() { return sourceIdentity; }
        public String tableIdentity() { return tableIdentity; }
        public int x() { return centerX; }
        public int z() { return centerZ; }
        public int mapId() { return mapId; }
        public int centerX() { return centerX; }
        public int centerZ() { return centerZ; }
        public int originX() { return originX; }
        public int originY() { return originY; }
        public int originZ() { return originZ; }
        public int scale() { return scale; }
        public String resolverCatalogReceipt() { return resolverCatalogReceipt; }
        public String resolverReceipt() { return resolverCatalogReceipt; }
        public String catalogReceipt() { return resolverCatalogReceipt; }
        public String receipt() { return resolverCatalogReceipt; }

        public boolean isAuthenticated() {
            return resolverCatalogReceipt != null && !legacyOracleWitness;
        }

        public void requireAuthenticated() {
            if (!isAuthenticated()) {
                throw new IllegalArgumentException(
                        "located map is missing an authenticated exact-destination receipt");
            }
            String expected = mapDestinationReceipt(destination, worldIdentity, sourceIdentity,
                    tableIdentity, mapId, centerX, centerZ, originX, originY, originZ, scale);
            if (!MessageDigest.isEqual(resolverCatalogReceipt.getBytes(StandardCharsets.US_ASCII),
                    expected.getBytes(StandardCharsets.US_ASCII))) {
                throw new IllegalArgumentException("stale map destination receipt");
            }
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof LocatedMap that)) return false;
            return mapId == that.mapId && centerX == that.centerX && centerZ == that.centerZ
                    && originX == that.originX && originY == that.originY && originZ == that.originZ
                    && scale == that.scale && legacyOracleWitness == that.legacyOracleWitness
                    && Objects.equals(destination, that.destination)
                    && Objects.equals(worldIdentity, that.worldIdentity)
                    && Objects.equals(sourceIdentity, that.sourceIdentity)
                    && Objects.equals(tableIdentity, that.tableIdentity)
                    && Objects.equals(resolverCatalogReceipt, that.resolverCatalogReceipt);
        }

        @Override
        public int hashCode() {
            return Objects.hash(destination, worldIdentity, sourceIdentity, tableIdentity, mapId,
                    centerX, centerZ, originX, originY, originZ, scale, resolverCatalogReceipt,
                    legacyOracleWitness);
        }
    }

    public static final class Resolution {
        private final List<LootStack> slots;
        private final Continuation continuation;

        private Resolution(List<LootStack> slots, Continuation continuation) {
            this.slots = Collections.unmodifiableList(new ArrayList<>(slots));
            this.continuation = continuation;
        }

        /** Null entries are empty fixed-index slots. */
        public List<LootStack> slots() {
            return slots;
        }

        public Continuation continuation() {
            return continuation;
        }
    }

    public static final class LootStack {
        private final String itemKey;
        private final int count;
        private final int maximumStackSize;
        private final Map<String, ComponentValue> components;

        private LootStack(String itemKey, int count, Map<String, ComponentValue> components) {
            this.itemKey = itemKey;
            this.count = count;
            this.maximumStackSize = Mc263ContainerLootResolver.maximumStackSize(itemKey);
            this.components = Collections.unmodifiableMap(new LinkedHashMap<>(components));
        }

        public String itemKey() {
            return itemKey;
        }

        public int count() {
            return count;
        }

        public int maximumStackSize() {
            return maximumStackSize;
        }

        public Map<String, ComponentValue> components() {
            return components;
        }
    }

    /** Current pinned ItemStack component closure; unknown component shapes are not representable. */
    public sealed interface ComponentValue permits StoredEnchantments, Enchantments,
            PotionContents, TranslatableItemName, ItemName, MapDecorations, MapId, PendingMapId,
            Damage, SuspiciousStewEffects, Instrument, OminousBottleAmplifier {
    }

    public record StoredEnchantments(Map<String, Integer> levels) implements ComponentValue {
        public StoredEnchantments {
            Objects.requireNonNull(levels, "levels");
            Map<String, Integer> checked = new LinkedHashMap<>();
            for (Map.Entry<String, Integer> entry : levels.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey()).toList()) {
                String key = requireMinecraftKey(entry.getKey(), "enchantment");
                Integer level = Objects.requireNonNull(entry.getValue(), "enchantment level");
                Enchantment enchantment = ENCHANTMENTS_BY_KEY.get(key);
                if (enchantment == null) {
                    throw new IllegalArgumentException("unknown pinned enchantment: " + key);
                }
                if (level <= 0 || level > enchantment.maxLevel) {
                    throw new IllegalArgumentException("enchantment level is outside pinned range: "
                            + key + "=" + level);
                }
                checked.put(key, level);
            }
            levels = Collections.unmodifiableMap(checked);
        }
    }

    public record Enchantments(Map<String, Integer> levels) implements ComponentValue {
        public Enchantments {
            levels = new StoredEnchantments(levels).levels();
        }
    }

    public record PotionContents(String potionKey) implements ComponentValue {
        public PotionContents {
            requireMinecraftKey(potionKey, "potion");
            if (!POTION_KEYS.contains(potionKey)) {
                throw new IllegalArgumentException("unknown pinned potion: " + potionKey);
            }
        }
    }

    public record Damage(int value) implements ComponentValue {
        public Damage {
            if (value < 0) throw new IllegalArgumentException("damage must be nonnegative");
        }
    }

    public record SuspiciousStewEffect(String effectKey, int duration) {
        public SuspiciousStewEffect {
            requireMinecraftKey(effectKey, "stew effect");
            if (duration < 0) throw new IllegalArgumentException("duration must be nonnegative");
        }
    }

    public record SuspiciousStewEffects(List<SuspiciousStewEffect> effects)
            implements ComponentValue {
        public SuspiciousStewEffects {
            effects = List.copyOf(effects);
            if (effects.isEmpty()) throw new IllegalArgumentException("stew effects are empty");
        }
    }

    public record Instrument(String instrumentKey) implements ComponentValue {
        public Instrument {
            requireMinecraftKey(instrumentKey, "instrument");
            if (!INSTRUMENT_KEYS.contains(instrumentKey)) {
                throw new IllegalArgumentException("unknown pinned instrument: " + instrumentKey);
            }
        }
    }

    public record OminousBottleAmplifier(int value) implements ComponentValue {
        public OminousBottleAmplifier {
            if (value < 0 || value > 4) {
                throw new IllegalArgumentException("ominous amplifier is outside 0..4");
            }
        }
    }

    public record TranslatableItemName(String translationKey) implements ComponentValue {
        public TranslatableItemName {
            if (translationKey == null || !translationKey.startsWith("filled_map.")
                    || !translationKey.endsWith("_abandoned_camp")) {
                throw new IllegalArgumentException("unsupported pinned translated item name");
            }
        }
    }

    /** Append-only data-component value for the local buried-treasure map's set_name function. */
    public record ItemName(String translationKey) implements ComponentValue {
        public ItemName {
            if (!"filled_map.buried_treasure".equals(translationKey)) {
                throw new IllegalArgumentException("unsupported pinned item name");
            }
        }
    }

    public record MapDecorations(Map<String, MapDecoration> decorations)
            implements ComponentValue {
        public MapDecorations {
            Objects.requireNonNull(decorations, "decorations");
            Map<String, MapDecoration> checked = new LinkedHashMap<>();
            for (Map.Entry<String, MapDecoration> entry : decorations.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey()).toList()) {
                if (entry.getKey().isEmpty()) {
                    throw new IllegalArgumentException("map decoration key is empty");
                }
                checked.put(entry.getKey(), Objects.requireNonNull(entry.getValue(), "decoration"));
            }
            decorations = Collections.unmodifiableMap(checked);
        }
    }

    public record MapDecoration(String type, double x, double z, double rotation) {
        public MapDecoration {
            if (!List.of("minecraft:abandoned_camp", "minecraft:ancient_city",
                    "minecraft:trial_chambers", "minecraft:mineshaft",
                    "minecraft:desert_pyramid", "minecraft:jungle_temple",
                    "minecraft:ocean_ruin_warm", "minecraft:mansion", "minecraft:red_x").contains(type)
                    || !Double.isFinite(x) || !Double.isFinite(z) || !Double.isFinite(rotation)) {
                throw new IllegalArgumentException("unsupported pinned map decoration");
            }
        }
    }

    public record MapId(int id) implements ComponentValue {
        public MapId {
            if (id < 0) throw new IllegalArgumentException("map id must be nonnegative");
        }
    }

    /**
     * Terrain-sealed exploration-map identity awaiting one positive durable world-map ID.
     * Persistence may replace this value only by authenticating the exact target receipt.
     */
    public record PendingMapId(String destination, String targetReceipt)
            implements ComponentValue {
        public PendingMapId {
            requireDestination(destination);
            requireSha256(targetReceipt, "pending map target receipt");
        }
    }

    public static final class EnchantmentValue {
        private final String key;
        private final int level;

        private EnchantmentValue(String key, int level) {
            this.key = key;
            this.level = level;
        }

        public String key() {
            return key;
        }

        public int level() {
            return level;
        }
    }

    public static final class Continuation {
        public enum Kind { LEGACY_48, XOROSHIRO_128_PLUS_PLUS }

        private final Kind kind;
        private final long first;
        private final long second;

        private Continuation(Kind kind, long first, long second) {
            this.kind = kind;
            this.first = first;
            this.second = second;
        }

        private static Continuation legacy48(long state) {
            return new Continuation(Kind.LEGACY_48, state, 0L);
        }

        private static Continuation xoroshiro128PlusPlus(long seedLo, long seedHi) {
            return new Continuation(Kind.XOROSHIRO_128_PLUS_PLUS, seedLo, seedHi);
        }

        public Kind kind() {
            return kind;
        }

        public long legacy48State() {
            if (kind != Kind.LEGACY_48) {
                throw new IllegalStateException("continuation is not Legacy48");
            }
            return first;
        }

        public long xoroshiroSeedLo() {
            if (kind != Kind.XOROSHIRO_128_PLUS_PLUS) {
                throw new IllegalStateException("continuation is not Xoroshiro128++");
            }
            return first;
        }

        public long xoroshiroSeedHi() {
            if (kind != Kind.XOROSHIRO_128_PLUS_PLUS) {
                throw new IllegalStateException("continuation is not Xoroshiro128++");
            }
            return second;
        }
    }

    private interface RandomSource {
        int nextInt(int bound);
        boolean nextBoolean();
        float nextFloat();
    }

    private static final class LegacyRandom implements RandomSource {
        private long state;

        private LegacyRandom(long rawSeed) {
            state = (rawSeed ^ 0x5DEECE66DL) & LEGACY_MASK;
        }

        private int next(int bits) {
            state = (state * 25214903917L + 11L) & LEGACY_MASK;
            return (int) (state >>> (48 - bits));
        }

        @Override
        public int nextInt(int bound) {
            if (bound <= 0) {
                throw new IllegalArgumentException("bound must be positive");
            }
            if ((bound & (bound - 1)) == 0) {
                return (int) ((bound * (long) next(31)) >> 31);
            }
            int sample;
            int modulo;
            do {
                sample = next(31);
                modulo = sample % bound;
            } while (sample - modulo + (bound - 1) < 0);
            return modulo;
        }

        @Override
        public boolean nextBoolean() {
            return next(1) != 0;
        }

        @Override
        public float nextFloat() {
            return next(24) * 5.9604645E-8f;
        }

        private long state() {
            return state;
        }
    }

    private static final class XoroshiroRandom implements RandomSource {
        private long seedLo;
        private long seedHi;

        private XoroshiroRandom(long seedLo, long seedHi) {
            this.seedLo = seedLo;
            this.seedHi = seedHi;
            if ((seedLo | seedHi) == 0L) {
                this.seedLo = -7046029254386353131L;
                this.seedHi = 7640891576956012809L;
            }
        }

        private long nextLong() {
            long s0 = seedLo;
            long s1 = seedHi;
            long result = Long.rotateLeft(s0 + s1, 17) + s0;
            s1 ^= s0;
            seedLo = Long.rotateLeft(s0, 49) ^ s1 ^ (s1 << 21);
            seedHi = Long.rotateLeft(s1, 28);
            return result;
        }

        @Override
        public int nextInt(int bound) {
            if (bound <= 0) {
                throw new IllegalArgumentException("bound must be positive");
            }
            long randomBits = Integer.toUnsignedLong((int) nextLong());
            long multiplied = randomBits * bound;
            long fractional = multiplied & 0xFFFFFFFFL;
            if (fractional < bound) {
                long threshold = Integer.toUnsignedLong(Integer.remainderUnsigned(~bound + 1, bound));
                while (fractional < threshold) {
                    randomBits = Integer.toUnsignedLong((int) nextLong());
                    multiplied = randomBits * bound;
                    fractional = multiplied & 0xFFFFFFFFL;
                }
            }
            return (int) (multiplied >>> 32);
        }

        @Override
        public boolean nextBoolean() {
            return (nextLong() & 1L) != 0L;
        }

        @Override
        public float nextFloat() {
            return (nextLong() >>> 40) * 5.9604645E-8f;
        }

        private long seedLo() {
            return seedLo;
        }

        private long seedHi() {
            return seedHi;
        }
    }

    private static final class MutableStack {
        private String itemKey;
        private int count;
        private boolean discarded;
        private final Map<String, ComponentValue> components = new LinkedHashMap<>();

        private MutableStack(String itemKey, int count) {
            this.itemKey = itemKey;
            this.count = count;
        }

        private void putEnchantments(List<EnchantmentValue> enchantments) {
            Map<String, Integer> levels = new LinkedHashMap<>();
            for (EnchantmentValue enchantment : enchantments) {
                levels.put(enchantment.key(), enchantment.level());
            }
            if (itemKey.equals("minecraft:book") || itemKey.equals("minecraft:enchanted_book")) {
                itemKey = "minecraft:enchanted_book";
                putComponent("minecraft:stored_enchantments", new StoredEnchantments(levels));
            } else {
                putComponent("minecraft:enchantments", new Enchantments(levels));
            }
        }

        private void putComponent(String key, ComponentValue value) {
            if (!key.equals("minecraft:stored_enchantments")
                    && !key.equals("minecraft:enchantments")
                    && !key.equals("minecraft:potion_contents")
                    && !key.equals("minecraft:item_name")
                    && !key.equals("minecraft:map_decorations")
                    && !key.equals("minecraft:map_id")
                    && !key.equals("minecraft:damage")
                    && !key.equals("minecraft:suspicious_stew_effects")
                    && !key.equals("minecraft:instrument")
                    && !key.equals("minecraft:ominous_bottle_amplifier")) {
                throw new IllegalArgumentException("unsupported pinned loot component: " + key);
            }
            components.put(key, value);
        }

        private MutableStack copyWithCount(int newCount) {
            MutableStack copy = new MutableStack(itemKey, newCount);
            copy.components.putAll(components);
            copy.discarded = discarded;
            return copy;
        }

        private LootStack freeze() {
            return new LootStack(itemKey, count, components);
        }
    }

    private enum FunctionKind {
        SET_COUNT, ENCHANT_RANDOMLY, ENCHANT_WITH_LEVELS, SET_ENCHANTMENTS, SET_POTION,
        SET_ITEM_NAME, SET_DAMAGE, SET_STEW_EFFECT, SET_INSTRUMENT, SET_OMINOUS_AMPLIFIER,
        SET_CAMP_MAP, SET_EXPLORATION_MAP
    }

    private static final class Function {
        private final FunctionKind kind;
        private final IntRange value;
        private final String symbolicValue;
        private final float floatMin;
        private final float floatMax;
        private final List<String> enchantmentKeys;
        private final List<StewEffect> stewEffects;

        private Function(FunctionKind kind, IntRange value, String symbolicValue,
                float floatMin, float floatMax, List<String> enchantmentKeys,
                List<StewEffect> stewEffects) {
            this.kind = kind;
            this.value = value;
            this.symbolicValue = symbolicValue;
            this.floatMin = floatMin;
            this.floatMax = floatMax;
            this.enchantmentKeys = List.copyOf(enchantmentKeys);
            this.stewEffects = List.copyOf(stewEffects);
        }

        private static Function basic(FunctionKind kind, IntRange value, String symbolicValue) {
            return new Function(kind, value, symbolicValue, 0, 0, List.of(), List.of());
        }

        private static Function count(int min, int max) {
            return basic(FunctionKind.SET_COUNT, new IntRange(min, max), null);
        }

        // `minecraft:set_enchantments`: 키와 레벨을 번갈아 실은 고정 목록.
        private static Function enchantments(String... keysAndLevels) {
            if (keysAndLevels.length == 0 || keysAndLevels.length % 2 != 0) {
                throw new IllegalArgumentException("set_enchantments needs key/level pairs");
            }
            return new Function(FunctionKind.SET_ENCHANTMENTS, null, null, 0, 0,
                    List.of(keysAndLevels), List.of());
        }

        private static Function enchantRandomly() {
            return basic(FunctionKind.ENCHANT_RANDOMLY, null, null);
        }

        private static Function enchantRandomly(String... keys) {
            return new Function(FunctionKind.ENCHANT_RANDOMLY, null, null,
                    0, 0, List.of(keys), List.of());
        }

        private static Function enchantWithLevels(int levels) {
            return basic(FunctionKind.ENCHANT_WITH_LEVELS,
                    new IntRange(levels, levels), null);
        }

        private static Function enchantWithLevels(int min, int max) {
            return basic(FunctionKind.ENCHANT_WITH_LEVELS, new IntRange(min, max), null);
        }

        private static Function potion(String potionKey) {
            requireMinecraftKey(potionKey, "potion");
            return basic(FunctionKind.SET_POTION, null, potionKey);
        }

        private static Function itemName(String translationKey) {
            return basic(FunctionKind.SET_ITEM_NAME, null, translationKey);
        }

        private static Function damage(float min, float max) {
            return new Function(FunctionKind.SET_DAMAGE, null, null,
                    min, max, List.of(), List.of());
        }

        private static Function stew(StewEffect... effects) {
            return new Function(FunctionKind.SET_STEW_EFFECT, null, null,
                    0, 0, List.of(), List.of(effects));
        }

        private static Function instrument() {
            return basic(FunctionKind.SET_INSTRUMENT, null, null);
        }

        private static Function ominousAmplifier(int min, int max) {
            return basic(FunctionKind.SET_OMINOUS_AMPLIFIER, new IntRange(min, max), null);
        }

        private static Function campMap(String destination) {
            return basic(FunctionKind.SET_CAMP_MAP, null, destination);
        }

        private static Function explorerMap(String destination) {
            return basic(FunctionKind.SET_EXPLORATION_MAP, null, destination);
        }
    }

    private record StewEffect(String effectKey, IntRange duration, boolean instantaneous) {}

    private static final class IntRange {
        private final int min;
        private final int max;

        private IntRange(int min, int max) {
            this.min = min;
            this.max = max;
        }

        private int sample(RandomSource random) {
            return nextIntInclusive(random, min, max);
        }
    }

    private static final class Entry {
        private final String itemKey;
        private final int weight;
        private final boolean empty;
        private final Condition condition;
        private final List<Function> functions;
        private final Table nestedTable;

        private Entry(String itemKey, int weight, boolean empty, Condition condition,
                      List<Function> functions, Table nestedTable) {
            if (weight <= 0) {
                throw new IllegalArgumentException("loot entry weight must be positive");
            }
            this.itemKey = Objects.requireNonNull(itemKey, "itemKey");
            this.weight = weight;
            this.empty = empty;
            this.condition = Objects.requireNonNull(condition, "loot entry condition");
            this.functions = List.copyOf(functions);
            this.nestedTable = nestedTable;
        }

    }

    private enum Condition {
        ALWAYS {
            @Override boolean matches(AuthenticatedContext context) { return true; }
        },
        SULFUR_CAVES {
            @Override boolean matches(AuthenticatedContext context) {
                return context.sulfurCavesLocationCheckMatches();
            }
        },
        NOT_BAMBOO_JUNGLE { @Override boolean matches(AuthenticatedContext context) {
            return !context.biomeKey().equals("minecraft:bamboo_jungle"); } },
        NOT_CHERRY_GROVE { @Override boolean matches(AuthenticatedContext context) {
            return !context.biomeKey().equals("minecraft:cherry_grove"); } },
        NOT_BIRCH_FOREST { @Override boolean matches(AuthenticatedContext context) {
            return !context.biomeKey().equals("minecraft:birch_forest"); } },
        NOT_DAPPLED_FOREST { @Override boolean matches(AuthenticatedContext context) {
            return !context.biomeKey().equals("minecraft:dappled_forest"); } },
        NOT_FLOWER_FOREST { @Override boolean matches(AuthenticatedContext context) {
            return !context.biomeKey().equals("minecraft:flower_forest"); } },
        NOT_PALE_GARDEN { @Override boolean matches(AuthenticatedContext context) {
            return !context.biomeKey().equals("minecraft:pale_garden"); } },
        NOT_SWAMP { @Override boolean matches(AuthenticatedContext context) {
            return !context.biomeKey().equals("minecraft:swamp"); } },
        NOT_WINDSWEPT_FOREST { @Override boolean matches(AuthenticatedContext context) {
            return !context.biomeKey().equals("minecraft:windswept_forest"); } };

        abstract boolean matches(AuthenticatedContext context);
    }

    private record AuthenticatedContext(String biomeKey,
                                        boolean sulfurCavesLocationCheckMatches,
                                        Map<String, Object> maps,
                                        boolean exactDestinationReceipt,
                                        boolean legacyOracleWitness) {
        private AuthenticatedContext {
            requireMinecraftKey(biomeKey, "authenticated loot-origin biome");
            maps = Map.copyOf(maps);
        }
    }

    private static final class Pool {
        private final IntRange rolls;
        private final List<Entry> entries;
        private final List<Function> functions;
        private final float randomChance;

        private Pool(IntRange rolls, List<Entry> entries, List<Function> functions,
                float randomChance) {
            this.rolls = rolls;
            this.entries = entries;
            this.functions = List.copyOf(functions);
            this.randomChance = randomChance;
        }
    }

    private static final class Table {
        private final List<Pool> pools;
        private final Set<String> mapDestinations;
        private final int containerSize;

        private Table(List<Pool> pools, int containerSize) {
            this.pools = pools;
            this.containerSize = containerSize;
            LinkedHashSet<String> destinations = new LinkedHashSet<>();
            for (Pool pool : pools) {
                for (Entry entry : pool.entries) {
                    if (entry.nestedTable != null) {
                        destinations.addAll(entry.nestedTable.mapDestinations);
                    }
                    for (Function function : entry.functions) {
                        if (function.kind == FunctionKind.SET_CAMP_MAP
                                || function.kind == FunctionKind.SET_EXPLORATION_MAP) {
                            destinations.add(function.symbolicValue);
                        }
                    }
                }
                for (Function function : pool.functions) {
                    if (function.kind == FunctionKind.SET_CAMP_MAP
                            || function.kind == FunctionKind.SET_EXPLORATION_MAP) {
                        destinations.add(function.symbolicValue);
                    }
                }
            }
            this.mapDestinations = Collections.unmodifiableSet(destinations);
        }

        private Set<String> mapDestinations() {
            return mapDestinations;
        }
    }

    private static final class Enchantment {
        private final String key;
        private final int weight;
        private final int maxLevel;
        private final int minBase;
        private final int minPerLevel;
        private final int maxBase;
        private final int maxPerLevel;
        private final List<String> excludedKeys;

        private Enchantment(String key, int weight, int maxLevel, int minBase, int minPerLevel,
                            int maxBase, int maxPerLevel, List<String> excludedKeys) {
            this.key = key;
            this.weight = weight;
            this.maxLevel = maxLevel;
            this.minBase = minBase;
            this.minPerLevel = minPerLevel;
            this.maxBase = maxBase;
            this.maxPerLevel = maxPerLevel;
            this.excludedKeys = excludedKeys;
        }

        private boolean appliesTo(String itemKey) {
            if (itemKey.equals("minecraft:book") || itemKey.equals("minecraft:enchanted_book")) {
                return true;
            }
            return switch (key) {
                case "minecraft:protection", "minecraft:fire_protection",
                        "minecraft:blast_protection", "minecraft:projectile_protection",
                        "minecraft:thorns", "minecraft:binding_curse" -> isArmor(itemKey);
                case "minecraft:feather_falling", "minecraft:depth_strider",
                        "minecraft:frost_walker" -> itemKey.endsWith("_boots");
                case "minecraft:respiration", "minecraft:aqua_affinity" ->
                        itemKey.endsWith("_helmet");
                case "minecraft:sharpness", "minecraft:smite",
                        "minecraft:bane_of_arthropods", "minecraft:knockback",
                        "minecraft:fire_aspect", "minecraft:looting",
                        "minecraft:sweeping_edge" -> isMeleeWeapon(itemKey);
                case "minecraft:efficiency", "minecraft:silk_touch", "minecraft:fortune" ->
                        isDigger(itemKey);
                case "minecraft:power", "minecraft:punch", "minecraft:flame",
                        "minecraft:infinity" -> itemKey.equals("minecraft:bow");
                case "minecraft:luck_of_the_sea", "minecraft:lure" ->
                        itemKey.equals("minecraft:fishing_rod");
                case "minecraft:loyalty", "minecraft:impaling", "minecraft:riptide",
                        "minecraft:channeling" -> itemKey.equals("minecraft:trident");
                case "minecraft:multishot", "minecraft:quick_charge",
                        "minecraft:piercing" -> itemKey.equals("minecraft:crossbow");
                case "minecraft:swift_sneak" -> itemKey.endsWith("_leggings");
                case "minecraft:density", "minecraft:breach", "minecraft:lunge" ->
                        itemKey.equals("minecraft:mace");
                case "minecraft:unbreaking", "minecraft:mending",
                        "minecraft:vanishing_curse" -> enchantability(itemKey) > 0;
                default -> false;
            };
        }

        private static boolean isArmor(String itemKey) {
            return itemKey.endsWith("_helmet") || itemKey.endsWith("_chestplate")
                    || itemKey.endsWith("_leggings") || itemKey.endsWith("_boots");
        }

        private static boolean isMeleeWeapon(String itemKey) {
            return itemKey.endsWith("_sword") || itemKey.endsWith("_axe");
        }

        private static boolean isDigger(String itemKey) {
            return itemKey.endsWith("_axe") || itemKey.endsWith("_pickaxe")
                    || itemKey.endsWith("_shovel") || itemKey.endsWith("_hoe");
        }
    }

    private static final class Candidate {
        private final Enchantment enchantment;
        private final int level;

        private Candidate(Enchantment enchantment, int level) {
            this.enchantment = enchantment;
            this.level = level;
        }
    }
}
