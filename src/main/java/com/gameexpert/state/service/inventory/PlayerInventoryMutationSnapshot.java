package com.gameexpert.state.service.inventory;

import com.gameexpert.engine.ChestInventory;
import com.gameexpert.engine.effect.StatusEffects;
import com.gameexpert.engine.inventory.ArmorSlot;
import com.gameexpert.engine.inventory.PlayerInventory;
import java.util.List;
import java.util.Arrays;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** DB 트랜잭션 하나에 넣을 플레이어의 완전한 권위 스냅샷. */
public record PlayerInventoryMutationSnapshot(
        Long playerId, Long worldId, long revision,
        double x, double y, double z, float yaw, float pitch, int health,
        short[] itemTypes, int[] counts, int[] durabilities, long[] enchantments,
        int[] mapIds, int[] shulkerIds, String[] bucketMobData, String[] itemComponentData,
        short[] equippedTypes, int[] equippedDurabilities, long[] equippedEnchantments,
        String[] equippedItemComponentData,
        PlayerInventory.StackSnapshot offhand,
        Integer spawnX, Integer spawnY, Integer spawnZ,
        int hunger, int saturationMilli, int xpTotal, int enchantSeed,
        long timeSinceRestMcTicks, ChestInventory.Snapshot enderChest,
        List<StatusEffects.PersistentEffect> statusEffects,
        StatusEffects.PersistentPlayerEffectClocks effectClocks,
        int selectedSlot, int fireTicks, int fireDamageAccum,
        com.gameexpert.state.service.PlayerDimensionIdentity dimensionIdentity) {

    /** Existing overworld receipts keep their exact revision-zero identity and fingerprint. */
    public PlayerInventoryMutationSnapshot(
        Long playerId, Long worldId, long revision,
        double x, double y, double z, float yaw, float pitch, int health,
        short[] itemTypes, int[] counts, int[] durabilities, long[] enchantments,
        int[] mapIds, int[] shulkerIds, String[] bucketMobData, String[] itemComponentData,
        short[] equippedTypes, int[] equippedDurabilities, long[] equippedEnchantments,
        String[] equippedItemComponentData,
        PlayerInventory.StackSnapshot offhand,
        Integer spawnX, Integer spawnY, Integer spawnZ,
        int hunger, int saturationMilli, int xpTotal, int enchantSeed,
        long timeSinceRestMcTicks, ChestInventory.Snapshot enderChest,
        List<StatusEffects.PersistentEffect> statusEffects,
        StatusEffects.PersistentPlayerEffectClocks effectClocks,
        int selectedSlot, int fireTicks, int fireDamageAccum) {
        this(playerId, worldId, revision, x, y, z, yaw, pitch, health, itemTypes, counts, durabilities, enchantments, mapIds, shulkerIds, bucketMobData, itemComponentData, equippedTypes, equippedDurabilities, equippedEnchantments, equippedItemComponentData, offhand, spawnX, spawnY, spawnZ, hunger, saturationMilli, xpTotal, enchantSeed, timeSinceRestMcTicks, enderChest, statusEffects, effectClocks, selectedSlot, fireTicks, fireDamageAccum, initialDimension(worldId));
    }

    private static com.gameexpert.state.service.PlayerDimensionIdentity initialDimension(Long worldId) {
        if (worldId == null) throw new IllegalArgumentException("world identity required");
        return new com.gameexpert.state.service.PlayerDimensionIdentity(worldId, worldId, 0);
    }

    public PlayerInventoryMutationSnapshot withDimensionIdentity(com.gameexpert.state.service.PlayerDimensionIdentity identity) {
        return new PlayerInventoryMutationSnapshot(playerId, worldId, revision, x, y, z, yaw, pitch, health, itemTypes, counts, durabilities, enchantments, mapIds, shulkerIds, bucketMobData, itemComponentData, equippedTypes, equippedDurabilities, equippedEnchantments, equippedItemComponentData, offhand, spawnX, spawnY, spawnZ, hunger, saturationMilli, xpTotal, enchantSeed, timeSinceRestMcTicks, enderChest, statusEffects, effectClocks, selectedSlot, fireTicks, fireDamageAccum, identity);
    }

    /** 구성요소 없는 장착 스택을 명시적으로 만드는 테스트·단순 정산용 생성자. */
    public PlayerInventoryMutationSnapshot(
            Long playerId, Long worldId, long revision,
            double x, double y, double z, float yaw, float pitch, int health,
            short[] itemTypes, int[] counts, int[] durabilities, long[] enchantments,
            int[] mapIds, int[] shulkerIds, String[] bucketMobData, String[] itemComponentData,
            short[] equippedTypes, int[] equippedDurabilities, long[] equippedEnchantments,
            PlayerInventory.StackSnapshot offhand,
            Integer spawnX, Integer spawnY, Integer spawnZ,
            int hunger, int saturationMilli, int xpTotal, int enchantSeed,
            long timeSinceRestMcTicks, ChestInventory.Snapshot enderChest,
            List<StatusEffects.PersistentEffect> statusEffects,
            StatusEffects.PersistentPlayerEffectClocks effectClocks,
            int selectedSlot, int fireTicks, int fireDamageAccum) {
        this(playerId, worldId, revision, x, y, z, yaw, pitch, health, itemTypes, counts,
                durabilities, enchantments, mapIds, shulkerIds, bucketMobData,
                itemComponentData, equippedTypes, equippedDurabilities, equippedEnchantments,
                new String[ArmorSlot.values().length], offhand, spawnX, spawnY, spawnZ,
                hunger, saturationMilli, xpTotal, enchantSeed, timeSinceRestMcTicks, enderChest,
                statusEffects, effectClocks, selectedSlot, fireTicks, fireDamageAccum);
    }

    public PlayerInventoryMutationSnapshot {
        if (dimensionIdentity == null || worldId == null
                || dimensionIdentity.runtimeWorldId() != worldId.longValue()) {
            throw new IllegalArgumentException("snapshot spatial identity must match dimension lease");
        }
        if (playerId == null || worldId == null || revision <= 0
                || revision == Long.MAX_VALUE) {
            throw new IllegalArgumentException("player/world and positive revision are required");
        }
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                || !Float.isFinite(yaw) || !Float.isFinite(pitch)) {
            throw new IllegalArgumentException("player pose must contain only finite values");
        }
        itemTypes = copy(itemTypes, PlayerInventory.SLOTS, "itemTypes");
        counts = copy(counts, PlayerInventory.SLOTS, "counts");
        durabilities = copy(durabilities, PlayerInventory.SLOTS, "durabilities");
        enchantments = copy(enchantments, PlayerInventory.SLOTS, "enchantments");
        mapIds = copy(mapIds, PlayerInventory.SLOTS, "mapIds");
        shulkerIds = copy(shulkerIds, PlayerInventory.SLOTS, "shulkerIds");
        bucketMobData = copy(bucketMobData, PlayerInventory.SLOTS, "bucketMobData");
        itemComponentData = copy(itemComponentData, PlayerInventory.SLOTS, "itemComponentData");
        equippedTypes = copy(equippedTypes, ArmorSlot.values().length, "equippedTypes");
        equippedDurabilities = copy(
                equippedDurabilities, ArmorSlot.values().length, "equippedDurabilities");
        equippedEnchantments = copy(
                equippedEnchantments, ArmorSlot.values().length, "equippedEnchantments");
        equippedItemComponentData = copy(equippedItemComponentData,
                ArmorSlot.values().length, "equippedItemComponentData");
        if (statusEffects == null || effectClocks == null || enderChest == null || offhand == null) {
            throw new IllegalArgumentException("complete player snapshot fields are required");
        }
        if (selectedSlot < 0 || selectedSlot >= PlayerInventory.HOTBAR_SLOTS
                || fireTicks < 0 || fireDamageAccum < 0) {
            throw new IllegalArgumentException("selected slot and fire clocks are invalid");
        }
        requireChestLength(enderChest, PlayerInventory.ENDER_CHEST_SLOTS, "enderChest");
        statusEffects = List.copyOf(statusEffects);
        enderChest = new ChestInventory.Snapshot(
                enderChest.itemTypes().clone(), enderChest.counts().clone(),
                enderChest.durabilities().clone(), enderChest.enchantments().clone(),
                enderChest.mapIds().clone(), enderChest.shulkerIds().clone(),
                enderChest.bucketMobData().clone(), enderChest.itemComponentData().clone());
    }

    private static short[] copy(short[] value, int length, String name) {
        if (value == null || value.length != length) {
            throw new IllegalArgumentException(name + " must have length " + length);
        }
        return value.clone();
    }

    private static int[] copy(int[] value, int length, String name) {
        if (value == null || value.length != length) {
            throw new IllegalArgumentException(name + " must have length " + length);
        }
        return value.clone();
    }

    private static long[] copy(long[] value, int length, String name) {
        if (value == null || value.length != length) {
            throw new IllegalArgumentException(name + " must have length " + length);
        }
        return value.clone();
    }


    private static String[] copy(String[] value, int length, String name) {
        if (value == null || value.length != length) {
            throw new IllegalArgumentException(name + " must have length " + length);
        }
        return value.clone();
    }

    private static void requireChestLength(
            ChestInventory.Snapshot value, int length, String name) {
        if (value.itemTypes().length != length || value.counts().length != length
                || value.durabilities().length != length
                || value.enchantments().length != length || value.mapIds().length != length
                || value.shulkerIds().length != length || value.bucketMobData().length != length
                || value.itemComponentData().length != length) {
            throw new IllegalArgumentException(name + " arrays must have length " + length);
        }
    }

    @Override public short[] itemTypes() { return itemTypes.clone(); }
    @Override public int[] counts() { return counts.clone(); }
    @Override public int[] durabilities() { return durabilities.clone(); }
    @Override public long[] enchantments() { return enchantments.clone(); }
    @Override public int[] mapIds() { return mapIds.clone(); }
    @Override public int[] shulkerIds() { return shulkerIds.clone(); }
    @Override public String[] bucketMobData() { return bucketMobData.clone(); }
    @Override public String[] itemComponentData() { return itemComponentData.clone(); }
    @Override public short[] equippedTypes() { return equippedTypes.clone(); }
    @Override public int[] equippedDurabilities() { return equippedDurabilities.clone(); }
    @Override public long[] equippedEnchantments() { return equippedEnchantments.clone(); }
    @Override public String[] equippedItemComponentData() {
        return equippedItemComponentData.clone();
    }
    @Override public ChestInventory.Snapshot enderChest() {
        return new ChestInventory.Snapshot(
                enderChest.itemTypes().clone(), enderChest.counts().clone(),
                enderChest.durabilities().clone(), enderChest.enchantments().clone(),
                enderChest.mapIds().clone(), enderChest.shulkerIds().clone(),
                enderChest.bucketMobData().clone(), enderChest.itemComponentData().clone());
    }

    /** 멱등성 영수증이 같은 revision의 서로 다른 완전 스냅샷 재사용을 거절하기 위한 지문. */
    public String fingerprint() {
        ChestInventory.Snapshot ender = enderChest();
        String canonical = playerId + ":" + worldId + ":" + revision + ":" + x + ":" + y
                + ":" + z + ":" + yaw + ":" + pitch + ":" + health
                + ":" + Arrays.toString(itemTypes) + ":" + Arrays.toString(counts)
                + ":" + Arrays.toString(durabilities) + ":" + Arrays.toString(enchantments)
                + ":" + Arrays.toString(mapIds) + ":" + Arrays.toString(shulkerIds)
                + ":" + Arrays.toString(bucketMobData) + ":" + Arrays.toString(equippedTypes)
                + ":" + Arrays.toString(itemComponentData)
                + ":" + Arrays.toString(equippedDurabilities) + ":"
                + Arrays.toString(equippedEnchantments) + ":" + offhand.itemType() + ":"
                + Arrays.toString(equippedItemComponentData) + ":"
                + offhand.count() + ":" + offhand.durability() + ":"
                + offhand.enchantments() + ":" + offhand.mapId() + ":"
                + offhand.shulkerId() + ":" + offhand.bucketMobData() + ":"
                + offhand.itemComponentData() + ":" + spawnX + ":"
                + spawnY + ":" + spawnZ + ":" + hunger + ":" + saturationMilli + ":"
                + xpTotal + ":" + enchantSeed + ":" + timeSinceRestMcTicks + ":"
                + Arrays.toString(ender.itemTypes()) + ":" + Arrays.toString(ender.counts()) + ":"
                + Arrays.toString(ender.durabilities()) + ":"
                + Arrays.toString(ender.enchantments()) + ":" + Arrays.toString(ender.mapIds())
                + ":" + Arrays.toString(ender.shulkerIds()) + ":"
                + Arrays.toString(ender.bucketMobData()) + ":" + statusEffects + ":"
                + Arrays.toString(ender.itemComponentData()) + ":"
                + effectClocks + ":" + selectedSlot + ":" + fireTicks + ":" + fireDamageAccum;
        if (dimensionIdentity.rootWorldId() != worldId || dimensionIdentity.travelRevision() != 0) {
            canonical += "|dimension:" + dimensionIdentity.rootWorldId() + ":" + dimensionIdentity.travelRevision();
        }
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }
}
