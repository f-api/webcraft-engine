package com.gameexpert.chest.entity;

import com.gameexpert.engine.ChestInventory;
import com.gameexpert.engine.enchant.EnchantmentRules;
import com.gameexpert.engine.enchant.WideEnchantments;
import com.gameexpert.engine.inventory.PlayerInventory;
import com.gameexpert.engine.inventory.ItemComponentData;
import com.gameexpert.engine.inventory.ItemComponentCodec;
import com.gameexpert.engine.persistence.finalcarrier.loot.CanonicalLootContainerKind;
import com.gameexpert.engine.persistence.finalcarrier.loot.CanonicalLootStoredResolution;
import com.gameexpert.terrain.Blocks;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 월드에 설치된 상자 하나의 내용물입니다. 좌표로 식별하며, 서버를 껐다 켜도 내용물이 남습니다.
 *
 * <p>구조는 {@code PlayerWorldState} 의 인벤토리 영속 방식과 같습니다 — 칸 목록을
 * {@code @ElementCollection} 으로 딸린 테이블({@code world_chest_items})에 저장합니다.
 *
 * <p>상자 블록 자체가 거기 있다는 사실은 {@code world_block_diffs} 가 담당하고, 이 엔티티는
 * <b>내용물만</b> 담당합니다. 상자를 부수면 내용물을 드랍한 뒤 이 행도 삭제합니다.
 */
@Getter
@Entity
@org.hibernate.annotations.DynamicUpdate
@Table(
        name = "world_chests",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_world_chest_pos",
                columnNames = { "world_id", "pos_x", "pos_y", "pos_z" }))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorldChest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long worldId;

    @Column(nullable = false)
    private int posX;

    @Column(nullable = false)
    private int posY;

    @Column(nullable = false)
    private int posZ;

    @Column(nullable = false, columnDefinition = "bigint not null default 0")
    private long persistenceRevision;

    /** Exact block-entity capacity (1 pot, 3 ShelfBlock, 5 hopper, 9 dispenser/dropper/crafter, 27 chest). */
    @Column(nullable = false,
            columnDefinition = "integer not null default " + ChestInventory.SLOTS)
    private int containerSize = ChestInventory.SLOTS;

    /** Nullable for old saves/non-hoppers; vanilla TransferCooldown is a signed int. */
    private Integer hopperTransferCooldown;

    @Lob
    private String potItemComponents;

    public void setPotItemComponents(String components) {
        if (components != null) {
            if (containerSize != 1) throw new IllegalArgumentException("pot metadata requires one slot");
            com.gameexpert.engine.inventory.ItemComponentCodec.decode((short) Blocks.DECORATED_POT, components);
        }
        potItemComponents = components;
    }

    public void setHopperTransferCooldown(Integer cooldown) {
        if (cooldown != null && containerSize != 5) {
            throw new IllegalArgumentException("hopper cooldown requires five slots");
        }
        hopperTransferCooldown = cooldown;
    }

    @Column(length = 255)
    private String generatedInstallationId;

    @Column(length = 64)
    private String generatedInstallationFingerprint;

    @Column(length = 255)
    private String canonicalLootInstallationId;

    @Column(length = 64)
    private String canonicalLootInstallationFingerprint;

    @Column(length = 64)
    private String canonicalLootDefinitionFingerprint;

    @Column(length = 64)
    private String canonicalLootResultFingerprint;

    @Lob
    private byte[] canonicalLootResolution;

    public static final int LEGACY_MATERIALIZER_VERSION = 0;
    public static final int STRICT_MATERIALIZER_VERSION = 1;
    /**
     * [ENCHANT-WIDE] Version 2 projects every vanilla 26.3 enchantment key (IDs 0..42). Versions 0
     * and 1 stay frozen: a row installed under them is re-projected on load with its own policy,
     * so widening their key domain would reject previously valid saves.
     */
    public static final int WIDE_MATERIALIZER_VERSION = 2;
    /** Policy selected by every fresh canonical installation. */
    public static final int EXPLORER_MAP_MATERIALIZER_VERSION = 3;
    public static final int COMPONENT_MATERIALIZER_VERSION = 4;
    public static final int CURRENT_MATERIALIZER_VERSION = COMPONENT_MATERIALIZER_VERSION;
    /** Frozen pre-Blast projector domain; never advance these with the live registry. */
    private static final int LEGACY_ENCHANTMENT_COUNT = 15;
    /** Frozen version-1 projector domain (the historical 48-bit mask, IDs 0..15). */
    private static final int STRICT_ENCHANTMENT_COUNT = 16;
    private static final int LEGACY_ITEM_ID_MAX = 2281;

    /** Existing rows retain version 0; only a fresh canonical install atomically selects version 1. */
    @Column(nullable = false, columnDefinition = "integer not null default 0")
    private int canonicalLootMaterializerVersion;

    @ElementCollection(fetch = FetchType.EAGER)
    @OrderBy("slot ASC")
    @CollectionTable(
            name = "world_chest_items",
            joinColumns = @JoinColumn(name = "chest_id"),
            uniqueConstraints = @UniqueConstraint(
                    name = "uk_world_chest_item_slot",
                    columnNames = { "chest_id", "slot" }))
    private List<ChestItem> items = new ArrayList<>();

    public WorldChest(Long worldId, int x, int y, int z) {
        this(worldId, x, y, z, ChestInventory.SLOTS);
    }

    public WorldChest(Long worldId, int x, int y, int z, int containerSize) {
        requireWorldId(worldId);
        requireSupportedContainerSize(containerSize);
        this.worldId = worldId;
        this.posX = x;
        this.posY = y;
        this.posZ = z;
        this.containerSize = containerSize;
    }

    public void requireContainerSize(int expected) {
        requireSupportedContainerSize(expected);
        requireSupportedContainerSize(containerSize);
        if (containerSize != expected) throw new IllegalStateException("container size mismatch");
    }

    public boolean claimGeneratedInstallation(String installationId, String fingerprint,
            int expectedContainerSize) {
        requireContainerSize(expectedContainerSize);
        requireInstallationIdentity(installationId, fingerprint);
        requireGeneratedMetadata();
        if (generatedInstallationId == null) {
            if (!isPristine()) {
                throw new IllegalStateException("generated chest installation requires a pristine entity");
            }
            generatedInstallationId = installationId;
            generatedInstallationFingerprint = fingerprint;
            return true;
        }
        if (installationId.equals(generatedInstallationId)
                && fingerprint.equals(generatedInstallationFingerprint)) return false;
        throw new IllegalStateException("conflicting generated chest installation");
    }

    public void requireSameGeneratedInstallation(String installationId, String fingerprint,
            int expectedContainerSize) {
        requireContainerSize(expectedContainerSize);
        requireInstallationIdentity(installationId, fingerprint);
        requireGeneratedMetadata();
        if (!installationId.equals(generatedInstallationId)
                || !fingerprint.equals(generatedInstallationFingerprint)) {
            throw new IllegalStateException("conflicting generated chest installation");
        }
    }

    /** Installs or byte-exactly replays one canonical first-open result under the row lock. */
    public boolean installCanonicalLoot(String installationId, String installationFingerprint,
            String definitionFingerprint, String resultFingerprint, byte[] resolution,
            List<ChestItem> fixedItems) {
        requireCanonicalLootContainerSize();
        requireInstallationIdentity(installationId, installationFingerprint);
        requireFingerprint(definitionFingerprint, "definition fingerprint");
        requireFingerprint(resultFingerprint, "result fingerprint");
        if (resolution == null || resolution.length == 0 || fixedItems == null) {
            throw new IllegalArgumentException("canonical loot payload is required");
        }
        requireGeneratedMetadata();
        if (generatedInstallationId == null) {
            throw new IllegalStateException("canonical loot cannot claim a player chest");
        }

        CanonicalLootStoredResolution stored = CanonicalLootStoredResolution.decode(resolution);
        if (stored.slots().size() != containerSize
                || !MessageDigest.isEqual(resolution, stored.encode())) {
            throw new IllegalArgumentException("canonical loot resolution is not canonical");
        }
        if (!resultFingerprint.equals(stored.fingerprint(definitionFingerprint))) {
            throw new IllegalStateException("canonical loot result fingerprint mismatch");
        }
        int materializerVersion = materializerVersionForInstallation();
        List<ChestItem> canonicalItems = projectCanonicalLootItems(stored, materializerVersion);
        List<ChestItem> suppliedItems = canonicalizeItems(fixedItems, containerSize);
        if (!sameItems(canonicalItems, suppliedItems)) {
            throw new IllegalStateException("canonical loot resolution does not match fixed items");
        }

        if (hasCanonicalMetadata()) {
            requireCompleteCanonicalMetadata();
            if (!installationId.equals(canonicalLootInstallationId)
                    || !installationFingerprint.equals(canonicalLootInstallationFingerprint)
                    || !definitionFingerprint.equals(canonicalLootDefinitionFingerprint)
                    || !resultFingerprint.equals(canonicalLootResultFingerprint)
                    || !java.security.MessageDigest.isEqual(
                            resolution, canonicalLootResolution)
                    || persistenceRevision < 1L) {
                throw new IllegalStateException("conflicting canonical chest loot replay");
            }
            // The first-open receipt is immutable, but the container contents and revision are
            // expected to evolve through ordinary gameplay.  An unknown-outcome retry proves the
            // receipt above and must not compare against, restore, or advance that mutable state.
            return false;
        }
        if (!isPristine()) {
            throw new IllegalStateException("canonical loot cannot overwrite chest contents");
        }
        canonicalLootMaterializerVersion = materializerVersion;
        canonicalLootInstallationId = installationId;
        canonicalLootInstallationFingerprint = installationFingerprint;
        canonicalLootDefinitionFingerprint = definitionFingerprint;
        canonicalLootResultFingerprint = resultFingerprint;
        canonicalLootResolution = resolution.clone();
        items.clear();
        items.addAll(suppliedItems);
        persistenceRevision = 1L;
        return true;
    }

    /** An unopened generated row has no prior projection, even when its column defaults to zero. */
    public int materializerVersionForInstallation() {
        requireMaterializerVersion(canonicalLootMaterializerVersion);
        return hasCanonicalMetadata() ? canonicalLootMaterializerVersion : CURRENT_MATERIALIZER_VERSION;
    }

    private static void requireMaterializerVersion(int version) {
        if (version != LEGACY_MATERIALIZER_VERSION && version != STRICT_MATERIALIZER_VERSION
                && version != WIDE_MATERIALIZER_VERSION && version != EXPLORER_MAP_MATERIALIZER_VERSION
                && version != COMPONENT_MATERIALIZER_VERSION) {
            throw new IllegalStateException("unknown canonical loot materializer version");
        }
    }

    /** Fresh symbolic loot always uses the current (wide) gameplay projection. */
    public static List<ChestItem> projectCanonicalLootItems(
            CanonicalLootStoredResolution resolution) {
        return projectCanonicalLootItems(resolution, CURRENT_MATERIALIZER_VERSION);
    }

    /** One projector with an explicit durable policy; legacy validation never upgrades old items. */
    public static List<ChestItem> projectCanonicalLootItems(
            CanonicalLootStoredResolution resolution, int materializerVersion) {
        requireMaterializerVersion(materializerVersion);
        boolean legacy = materializerVersion == LEGACY_MATERIALIZER_VERSION;
        if (resolution == null) throw new IllegalArgumentException("canonical LOOT result required");
        List<ChestItem> projected = new ArrayList<>();
        for (int slotIndex = 0; slotIndex < resolution.slots().size(); slotIndex++) {
            CanonicalLootStoredResolution.Slot slot = resolution.slots().get(slotIndex);
            if (slot == null) continue;
            if (materializerVersion < COMPONENT_MATERIALIZER_VERSION
                    && slot.itemKey().equals("minecraft:bamboo_hanging_sign"))
                throw new IllegalStateException("unsupported canonical item " + slot.itemKey());
            int mapId = 0;
            boolean mapComponentSeen = false;
            WideEnchantments enchantments = WideEnchantments.EMPTY;
            String potionKey = null;
            Integer ominousAmplifier = null;
            Integer damage = null;
            String instrument = null;
            CanonicalLootStoredResolution.StewEffect stew = null;
            for (CanonicalLootStoredResolution.Component component : slot.components().values()) {
                if (component == null) throw new IllegalStateException("null canonical component");
                switch (component.kind()) {
                    case "STORED_ENCHANTMENTS", "ENCHANTMENTS" -> {
                        if (legacy && !"STORED_ENCHANTMENTS".equals(component.kind())) {
                            throw new IllegalStateException("unsupported canonical component");
                        }
                        for (var enchantment : component.enchantments().entrySet()) {
                            int enchantmentId = canonicalEnchantmentId(
                                    enchantment.getKey(), materializerVersion);
                            if (legacy) {
                                // Exact old STORED_ENCHANTMENTS policy: known slots only, unknown omitted.
                                if (enchantmentId >= 0 && enchantmentId < LEGACY_ENCHANTMENT_COUNT) {
                                    enchantments = enchantments.with(
                                            enchantmentId, enchantment.getValue());
                                }
                                continue;
                            }
                            if (enchantmentId < 0) {
                                // Preserve the prior book projection policy. The complete named
                                // enchantment remains in the immutable canonical resolution.
                                if ("STORED_ENCHANTMENTS".equals(component.kind())) continue;
                                throw new IllegalStateException(
                                        "unsupported canonical enchantment " + enchantment.getKey());
                            }
                            int level = enchantment.getValue();
                            if (level < 1 || level > EnchantmentRules.maxLevel(enchantmentId)) {
                                throw new IllegalStateException("unsupported canonical enchantment level");
                            }
                            int previous = enchantments.level(enchantmentId);
                            if (previous != 0 && previous != level) {
                                throw new IllegalStateException("conflicting canonical enchantment levels");
                            }
                            enchantments = enchantments.with(enchantmentId, level);
                        }
                    }
                    case "POTION_CONTENTS" -> {
                        if (!isCanonicalPotionItem(slot.itemKey(), legacy)
                                || component.potionKey() == null
                                || potionKey != null && !potionKey.equals(component.potionKey())) {
                            throw new IllegalStateException(
                                    "potion component does not match canonical item");
                        }
                        potionKey = component.potionKey();
                    }
                    case "MAP_ID" -> {
                        if (!"minecraft:map".equals(slot.itemKey())
                                && !isCanonicalMapItem(slot.itemKey())) {
                            throw new IllegalStateException("map component does not match canonical item");
                        }
                        if (mapComponentSeen || component.mapId() == null) {
                            throw new IllegalStateException("canonical map component has no identity");
                        }
                        mapId = component.mapId();
                        mapComponentSeen = true;
                    }
                    case "PENDING_MAP_ID" -> throw new IllegalStateException(
                            "pending map identity reached the gameplay aggregate");
                    // [TRIAL-GAP] 바닐라 set_ominous_bottle_amplifier. 옛 materializer 는 이 컴포넌트를
                    // 몰랐으므로 legacy 정책에서는 여전히 거절한다.
                    case "OMINOUS_BOTTLE_AMPLIFIER" -> {
                        if (legacy || !"minecraft:ominous_bottle".equals(slot.itemKey())
                                || component.amplifier() == null || ominousAmplifier != null) {
                            throw new IllegalStateException("unsupported canonical component");
                        }
                        ominousAmplifier = component.amplifier();
                    }
                    case "DAMAGE" -> {
                        if (materializerVersion < COMPONENT_MATERIALIZER_VERSION || component.damage() == null)
                            throw new IllegalStateException("unsupported canonical component");
                        damage = component.damage();
                    }
                    case "INSTRUMENT" -> {
                        if (materializerVersion < COMPONENT_MATERIALIZER_VERSION
                                || !"minecraft:goat_horn".equals(slot.itemKey()))
                            throw new IllegalStateException("unsupported canonical component");
                        instrument = component.instrumentKey();
                    }
                    case "SUSPICIOUS_STEW_EFFECTS" -> {
                        if (materializerVersion < COMPONENT_MATERIALIZER_VERSION
                                || !"minecraft:suspicious_stew".equals(slot.itemKey())
                                || component.stewEffects().size() != 1)
                            throw new IllegalStateException("unsupported canonical component");
                        stew = component.stewEffects().getFirst();
                    }
                    case "ITEM_NAME", "MAP_DECORATIONS" -> {
                        if ("MAP_DECORATIONS".equals(component.kind())
                                && !"minecraft:map".equals(slot.itemKey())
                                && !isCanonicalMapItem(slot.itemKey())) {
                            throw new IllegalStateException(
                                    "map decorations do not match canonical item");
                        }
                        // The complete canonical form is retained in canonicalLootResolution.
                    }
                    default -> throw new IllegalStateException("unsupported canonical component");
                }
            }
            if (isCanonicalPotionItem(slot.itemKey(), legacy) != (potionKey != null)) {
                throw new IllegalStateException("canonical potion identity is incomplete");
            }
            short itemType = stew != null ? PlayerInventory.SUSPICIOUS_STEW_POPPY
                    : canonicalItemType(slot.itemKey(), potionKey, mapId, legacy);
            if ("minecraft:suspicious_stew".equals(slot.itemKey()) && stew == null)
                throw new IllegalStateException("canonical stew effect is incomplete");
            // Replaying versions 0..2 must reproduce their generic-map item identity exactly.
            if (materializerVersion < EXPLORER_MAP_MATERIALIZER_VERSION
                    && PlayerInventory.isExplorerMap(itemType)) {
                itemType = mapId > 0 ? PlayerInventory.FILLED_MAP : PlayerInventory.MAP;
            }
            // [UTILITY] 전용 ID 가 없는 (형태, 물약) 쌍은 범용 물약 + potionContents 성분이다.
            String potionComponents = legacy || potionKey == null ? null
                    : canonicalPotionComponents(slot.itemKey(), potionKey);
            if (legacy && Short.toUnsignedInt(itemType) > LEGACY_ITEM_ID_MAX) {
                throw new IllegalStateException("canonical item is outside the legacy materializer registry");
            }
            int gameplayMaximum = PlayerInventory.stackMax(itemType);
            if (slot.maximumStackSize() > gameplayMaximum || slot.count() > gameplayMaximum) {
                throw new IllegalStateException(
                        "canonical stack maximum does not match gameplay authority");
            }
            if (!PlayerInventory.isValidMapIdentity(itemType, mapId)) {
                throw new IllegalStateException("canonical map identity does not match gameplay item");
            }
            Integer durability = PlayerInventory.isDurable(itemType)
                    ? PlayerInventory.initialDurability(itemType) : null;
            if (damage != null) {
                int canonicalMaximum = switch (slot.itemKey()) {
                    case "minecraft:stone_axe", "minecraft:stone_pickaxe" -> 131;
                    case "minecraft:iron_axe" -> 250;
                    case "minecraft:diamond_axe", "minecraft:diamond_pickaxe" -> 1561;
                    case "minecraft:golden_axe", "minecraft:golden_pickaxe" -> 32;
                    case "minecraft:shield" -> 336;
                    default -> 0;
                };
                if (durability == null || canonicalMaximum == 0 || damage < 0 || damage >= canonicalMaximum)
                    throw new IllegalStateException("canonical damage is outside item durability");
                // Gameplay tiers have different maxima; retain the generated remaining fraction.
                durability = Math.max(1, (int) ((long) durability * (canonicalMaximum - damage) / canonicalMaximum));
            }
            if (enchantments.hasExtended()
                    && !EnchantmentRules.isValidEnchantmentsForItem(itemType, enchantments)) {
                throw new IllegalStateException("canonical enchantments do not fit the gameplay item");
            }
            // [ENCHANT-WIDE] ID 16 이상은 성분 문자열(WCIC4)이 싣는다. 확장이 없으면 null 그대로라
            // 버전 0·1 의 투영 결과는 바이트 단위로 같다.
            ItemComponentData components = ItemComponentCodec.decode(itemType, potionComponents);
            if (ominousAmplifier != null) components = components.withOminousBottleAmplifier(ominousAmplifier);
            if (instrument != null) components = components.withInstrument(instrument);
            if (stew != null) {
                if (!stew.effectKey().startsWith("minecraft:"))
                    throw new IllegalStateException("invalid canonical stew effect");
                components = components.withSuspiciousStewEffect(
                        stew.effectKey().substring("minecraft:".length()), stew.duration());
            }
            projected.add(new ChestItem(slotIndex, itemType, slot.count(), durability,
                    enchantments.word0() == 0L ? null : enchantments.word0(),
                    mapId == 0 ? null : mapId, null, null,
                    ItemComponentCodec.encode(itemType, components.withEnchantments(enchantments))));
        }
        return List.copyOf(projected);
    }

    /** Compares detached item snapshots by canonical slot order and every identity column. */
    public static boolean sameCanonicalItems(int containerSize, List<ChestItem> left,
            List<ChestItem> right) {
        return sameItems(canonicalizeItems(left, containerSize),
                canonicalizeItems(right, containerSize));
    }

    /** Returns only immutable, detached item values; callers cannot mutate the managed collection. */
    public List<ChestItem> getItems() {
        return List.copyOf(canonicalizeItems(items, containerSize));
    }

    /** 비어 있지 않은 칸만 통째로 교체합니다(틱 스레드가 만든 스냅샷을 그대로 저장). */
    public void replaceItems(List<ChestItem> snapshot) {
        List<ChestItem> canonical = canonicalizeItems(snapshot, containerSize);
        items.clear();
        items.addAll(canonical);
    }

    public boolean replaceItemsIfNewer(List<ChestItem> snapshot, long revision) {
        if (revision < 0) throw new IllegalArgumentException("chest revision must be non-negative");
        List<ChestItem> canonical = canonicalizeItems(snapshot, containerSize);
        if (revision <= persistenceRevision) return false;
        items.clear();
        items.addAll(canonical);
        persistenceRevision = revision;
        return true;
    }

    public byte[] getCanonicalLootResolution() {
        return canonicalLootResolution == null ? null : canonicalLootResolution.clone();
    }

    private static short canonicalItemType(String itemKey, String potionKey, int mapId, boolean legacy) {
        if (itemKey == null || !itemKey.startsWith("minecraft:")) {
            throw new IllegalStateException("unsupported canonical item " + itemKey);
        }
        if (legacy && "minecraft:potion".equals(itemKey)) {
            // 옛 materializer 는 수중 호흡 물약 하나만 알았다(투영 결과를 바이트 그대로 지킨다).
            return switch (potionKey) {
                case "minecraft:water_breathing" -> PlayerInventory.POTION_WATER_BREATHING;
                default -> throw new IllegalStateException("unsupported canonical potion");
            };
        }
        if (isCanonicalPotionItem(itemKey, false)) {
            // [UTILITY] 바닐라 potion_contents 의 모든 키: 전용 ID 가 있으면 그것, 없으면 범용 물약.
            com.gameexpert.engine.effect.PotionCatalog.Form form =
                    com.gameexpert.engine.effect.PotionCatalog.Form.byItemKey(
                            itemKey.substring("minecraft:".length()));
            String key = potionKey == null || !potionKey.startsWith("minecraft:") ? null
                    : potionKey.substring("minecraft:".length());
            if (form == null || !com.gameexpert.engine.effect.PotionCatalog.isKey(key)) {
                throw new IllegalStateException("unsupported canonical potion");
            }
            return com.gameexpert.engine.effect.PotionCatalog.canonicalItemType(form, key);
        }
        if (isCanonicalMapItem(itemKey)) {
            return switch (itemKey) {
                case "minecraft:abandoned_campsite_map", "minecraft:abandoned_camp_map" -> PlayerInventory.ABANDONED_CAMPSITE_MAP;
                case "minecraft:ancient_city_map", "minecraft:buried_ancient_city_map" -> PlayerInventory.ANCIENT_CITY_MAP;
                case "minecraft:buried_treasure_map" -> PlayerInventory.BURIED_TREASURE_MAP;
                case "minecraft:desert_pyramid_map" -> PlayerInventory.DESERT_PYRAMID_MAP;
                case "minecraft:desert_village_map" -> PlayerInventory.DESERT_VILLAGE_MAP;
                case "minecraft:jungle_explorer_map", "minecraft:jungle_pyramid_map" -> PlayerInventory.JUNGLE_EXPLORER_MAP;
                case "minecraft:mineshaft_map", "minecraft:buried_mineshaft_map" -> PlayerInventory.MINESHAFT_MAP;
                case "minecraft:ocean_explorer_map", "minecraft:ocean_monument_map" -> PlayerInventory.OCEAN_EXPLORER_MAP;
                case "minecraft:plains_village_map" -> PlayerInventory.PLAINS_VILLAGE_MAP;
                case "minecraft:savanna_village_map" -> PlayerInventory.SAVANNA_VILLAGE_MAP;
                case "minecraft:snowy_village_map" -> PlayerInventory.SNOWY_VILLAGE_MAP;
                case "minecraft:swamp_explorer_map", "minecraft:swamp_hut_map" -> PlayerInventory.SWAMP_EXPLORER_MAP;
                case "minecraft:taiga_village_map" -> PlayerInventory.TAIGA_VILLAGE_MAP;
                case "minecraft:trial_explorer_map", "minecraft:buried_trial_chambers_map" -> PlayerInventory.TRIAL_EXPLORER_MAP;
                case "minecraft:warm_ocean_ruins_map" -> PlayerInventory.WARM_OCEAN_RUINS_MAP;
                case "minecraft:woodland_explorer_map", "minecraft:woodland_mansion_map" -> PlayerInventory.WOODLAND_EXPLORER_MAP;
                default -> throw new IllegalStateException("unregistered explorer map");
            };
        }
        if ("minecraft:map".equals(itemKey)) {
            return mapId > 0 ? PlayerInventory.FILLED_MAP : PlayerInventory.MAP;
        }
        if (legacy && ("minecraft:golden_leggings".equals(itemKey)
                || "minecraft:golden_boots".equals(itemKey))) {
            throw new IllegalStateException("canonical item has no legacy registry alias");
        }
        try {
            return PlayerInventory.resolveCanonicalSimpleItemType(itemKey);
        } catch (IllegalArgumentException unsupported) {
            throw new IllegalStateException("unsupported canonical item " + itemKey, unsupported);
        }
    }

    /**
     * Version-scoped key domain. Version 2 is the shared live registry
     * ({@link EnchantmentRules#canonicalEnchantmentId}); versions 0/1 keep their frozen 16-key
     * table so re-projection of old rows is byte-identical.
     */
    private static int canonicalEnchantmentId(String key, int materializerVersion) {
        if (materializerVersion >= WIDE_MATERIALIZER_VERSION) {
            return EnchantmentRules.canonicalEnchantmentId(key);
        }
        int frozen = frozenCanonicalEnchantmentId(key);
        return frozen < STRICT_ENCHANTMENT_COUNT ? frozen : -1;
    }

    private static int frozenCanonicalEnchantmentId(String key) {
        return switch (key) {
            case "minecraft:efficiency" -> EnchantmentRules.EFFICIENCY;
            case "minecraft:unbreaking" -> EnchantmentRules.UNBREAKING;
            case "minecraft:sharpness" -> EnchantmentRules.SHARPNESS;
            case "minecraft:protection" -> EnchantmentRules.PROTECTION;
            case "minecraft:blast_protection" -> EnchantmentRules.BLAST_PROTECTION;
            case "minecraft:silk_touch" -> EnchantmentRules.SILK_TOUCH;
            case "minecraft:fortune" -> EnchantmentRules.FORTUNE;
            case "minecraft:power" -> EnchantmentRules.POWER;
            case "minecraft:lure" -> EnchantmentRules.LURE;
            case "minecraft:luck_of_the_sea" -> EnchantmentRules.LUCK_OF_THE_SEA;
            case "minecraft:looting" -> EnchantmentRules.LOOTING;
            case "minecraft:loyalty" -> EnchantmentRules.LOYALTY;
            case "minecraft:riptide" -> EnchantmentRules.RIPTIDE;
            case "minecraft:channeling" -> EnchantmentRules.CHANNELING;
            // [CURSE] 고대 도시 전리품이 실어 오는 두 저주. 게임플레이 마스크에 자리가 생겼다.
            case "minecraft:binding_curse" -> EnchantmentRules.BINDING_CURSE;
            case "minecraft:vanishing_curse" -> EnchantmentRules.VANISHING_CURSE;
            // Unsupported stored-book names remain in the sealed resolution, not the gameplay mask.
            default -> -1;
        };
    }

    /** [UTILITY] potion_contents 를 싣는 바닐라 물약 세 형태(옛 materializer 는 마시는 물약뿐). */
    private static boolean isCanonicalPotionItem(String itemKey, boolean legacy) {
        return "minecraft:potion".equals(itemKey) || !legacy
                && ("minecraft:splash_potion".equals(itemKey)
                        || "minecraft:lingering_potion".equals(itemKey));
    }

    /** [UTILITY] 정본 물약 스택의 성분 문자열(전용 ID 쌍이면 null). */
    private static String canonicalPotionComponents(String itemKey, String potionKey) {
        com.gameexpert.engine.effect.PotionCatalog.Form form =
                com.gameexpert.engine.effect.PotionCatalog.Form.byItemKey(
                        itemKey.substring("minecraft:".length()));
        String key = potionKey.substring("minecraft:".length());
        String contents = com.gameexpert.engine.effect.PotionCatalog.canonicalPotionContents(form, key);
        if (contents == null) return null;
        short item = com.gameexpert.engine.effect.PotionCatalog.canonicalItemType(form, key);
        return com.gameexpert.engine.inventory.ItemComponentCodec.encode(item,
                com.gameexpert.engine.inventory.ItemComponentData.EMPTY.withPotionContents(contents));
    }

    private static boolean isCanonicalMapItem(String itemKey) {
        return switch (itemKey) {
            case "minecraft:abandoned_camp_map",
                    "minecraft:buried_ancient_city_map",
                    "minecraft:buried_mineshaft_map",
                    "minecraft:buried_trial_chambers_map",
                    "minecraft:jungle_pyramid_map",
                    "minecraft:ocean_monument_map",
                    "minecraft:swamp_hut_map",
                    "minecraft:woodland_mansion_map" -> true;
            case "minecraft:abandoned_campsite_map",
                    "minecraft:ancient_city_map",
                    "minecraft:buried_treasure_map",
                    "minecraft:desert_pyramid_map",
                    "minecraft:desert_village_map",
                    "minecraft:jungle_explorer_map",
                    "minecraft:mineshaft_map",
                    "minecraft:ocean_explorer_map",
                    "minecraft:plains_village_map",
                    "minecraft:savanna_village_map",
                    "minecraft:snowy_village_map",
                    "minecraft:swamp_explorer_map",
                    "minecraft:taiga_village_map",
                    "minecraft:trial_explorer_map",
                    "minecraft:warm_ocean_ruins_map",
                    "minecraft:woodland_explorer_map" -> true;
            default -> false;
        };
    }

    private static List<ChestItem> canonicalizeItems(List<ChestItem> source, int containerSize) {
        requireSupportedContainerSize(containerSize);
        if (source == null) throw new IllegalArgumentException("chest items are required");
        boolean[] occupied = new boolean[containerSize];
        List<ChestItem> canonical = new ArrayList<>(source.size());
        for (ChestItem item : source) {
            if (item == null) throw new IllegalArgumentException("chest item is required");
            int slot = item.getSlot();
            if (slot < 0 || slot >= containerSize) {
                throw new IllegalArgumentException("chest item slot is outside the container");
            }
            if (occupied[slot]) throw new IllegalArgumentException("chest item slot is duplicated");
            occupied[slot] = true;
            canonical.add(new ChestItem(slot, item.getItemType(), item.getItemCount(),
                    item.getDurability(), item.getEnchantments(), item.getMapId(),
                    item.getShulkerId(), item.getBucketMobData(), item.getItemComponentData()));
        }
        canonical.sort(Comparator.comparingInt(ChestItem::getSlot));
        return canonical;
    }

    private static boolean sameItems(List<ChestItem> left, List<ChestItem> right) {
        if (left.size() != right.size()) return false;
        for (int index = 0; index < left.size(); index++) {
            if (!Objects.equals(left.get(index), right.get(index))) return false;
        }
        return true;
    }

    private boolean isPristine() {
        return persistenceRevision == 0L && items.isEmpty() && !hasCanonicalMetadata();
    }

    private boolean hasCanonicalMetadata() {
        return canonicalLootMaterializerVersion != LEGACY_MATERIALIZER_VERSION
                || canonicalLootInstallationId != null || canonicalLootInstallationFingerprint != null
                || canonicalLootDefinitionFingerprint != null
                || canonicalLootResultFingerprint != null || canonicalLootResolution != null;
    }

    private void requireGeneratedMetadata() {
        if ((generatedInstallationId == null) != (generatedInstallationFingerprint == null)) {
            throw new IllegalStateException("incomplete generated chest installation identity");
        }
        if (generatedInstallationId != null) {
            requireInstallationIdentity(generatedInstallationId, generatedInstallationFingerprint);
        }
    }

    private void requireCompleteCanonicalMetadata() {
        if (canonicalLootInstallationId == null || canonicalLootInstallationFingerprint == null
                || canonicalLootDefinitionFingerprint == null || canonicalLootResultFingerprint == null
                || canonicalLootResolution == null || canonicalLootResolution.length == 0) {
            throw new IllegalStateException("incomplete canonical chest loot identity");
        }
        requireInstallationIdentity(canonicalLootInstallationId, canonicalLootInstallationFingerprint);
        requireFingerprint(canonicalLootDefinitionFingerprint, "definition fingerprint");
        requireFingerprint(canonicalLootResultFingerprint, "result fingerprint");
    }

    private void validateState() {
        requireWorldId(worldId);
        requireSupportedContainerSize(containerSize);
        setPotItemComponents(potItemComponents);
        if (hopperTransferCooldown != null && containerSize != 5) {
            throw new IllegalArgumentException("hopper cooldown requires five slots");
        }
        if (persistenceRevision < 0L) {
            throw new IllegalArgumentException("chest revision must be non-negative");
        }
        requireGeneratedMetadata();
        List<ChestItem> canonical = canonicalizeItems(items, containerSize);
        requireMaterializerVersion(canonicalLootMaterializerVersion);
        if (!hasCanonicalMetadata()) return;
        requireCompleteCanonicalMetadata();
        if (generatedInstallationId == null) {
            throw new IllegalStateException("canonical loot cannot belong to a player chest");
        }
        CanonicalLootStoredResolution stored = CanonicalLootStoredResolution.decode(
                canonicalLootResolution);
        requireCanonicalLootContainerSize();
        if (stored.slots().size() != containerSize
                || !MessageDigest.isEqual(canonicalLootResolution, stored.encode())) {
            throw new IllegalStateException("non-canonical chest loot resolution");
        }
        if (!canonicalLootResultFingerprint.equals(
                stored.fingerprint(canonicalLootDefinitionFingerprint))) {
            throw new IllegalStateException("canonical chest loot result fingerprint mismatch");
        }
        if (persistenceRevision < 1L
                || persistenceRevision == 1L
                        && !sameItems(canonical, projectCanonicalLootItems(
                                stored, canonicalLootMaterializerVersion))) {
            throw new IllegalStateException("canonical chest loot does not match fixed items");
        }
    }

    @PostLoad
    private void validateAfterLoad() {
        try {
            validateState();
        } catch (RuntimeException malformed) {
            throw new IllegalStateException("invalid world chest state / component payload", malformed);
        }
    }

    @PrePersist
    @PreUpdate
    private void validateBeforeWrite() {
        validateState();
        List<ChestItem> canonical = canonicalizeItems(items, containerSize);
        items.clear();
        items.addAll(canonical);
    }

    private static void requireWorldId(Long value) {
        if (value == null || value <= 0L) throw new IllegalArgumentException("positive world ID required");
    }

    private static void requireSupportedContainerSize(int size) {
        if (!isSupportedContainerSize(size)) {
            throw new IllegalArgumentException("unsupported chest container size: " + size);
        }
    }

    /**
     * Whether a coordinate row of this capacity is persistable. The domain is exactly the set of
     * {@link com.gameexpert.engine.BlockEntityRules#chestStorageSlots} capacities (1 decorated
     * pot, 3 shelf, 5 hopper, 9 dispenser/dropper/crafter, 27 chest/barrel/shulker), so every
     * resident row the owner thread can create can also be written by the save queue.
     */
    public static boolean isSupportedContainerSize(int size) {
        return com.gameexpert.engine.BlockEntityRules.isChestStorageCapacity(size);
    }

    private void requireCanonicalLootContainerSize() {
        if (!CanonicalLootContainerKind.supportsSlots(containerSize)) {
            throw new IllegalStateException("canonical loot is not permitted for this container size");
        }
    }

    private static void requireInstallationIdentity(String installationId, String fingerprint) {
        if (installationId == null || installationId.isBlank() || installationId.length() > 255) {
            throw new IllegalArgumentException("invalid generated installation id");
        }
        if (fingerprint == null || !fingerprint.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("invalid generated installation fingerprint");
        }
    }

    private static void requireFingerprint(String value, String label) {
        if (value == null || !value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("invalid canonical loot " + label);
        }
    }
}
