package com.gameexpert.engine.mob.villager;

import com.gameexpert.engine.inventory.ItemComponentCodec;
import com.gameexpert.engine.inventory.PlayerInventory;

/** Merchant component predicates and lossless result stacks. */
public final class VillagerTradeStacks {
    private VillagerTradeStacks() { }
    public static PlayerInventory.StackSnapshot plain(short type) {
        return type==0 ? PlayerInventory.StackSnapshot.EMPTY : new PlayerInventory.StackSnapshot(type,1,PlayerInventory.initialDurability(type),0,0,0, type == PlayerInventory.TROPICAL_FISH_BUCKET
                ? com.gameexpert.engine.mob.BucketMobPayloadCodec.encode(
                        com.gameexpert.engine.mob.MobType.TROPICAL_FISH, "anemone", 0, null) : null, null);
    }
    public static PlayerInventory.StackSnapshot count(PlayerInventory.StackSnapshot p,int count) {
        return count==0 ? PlayerInventory.StackSnapshot.EMPTY : new PlayerInventory.StackSnapshot(p.itemType(),count,p.durability(),p.enchantments(),p.mapId(),p.shulkerId(),p.bucketMobData(),p.itemComponentData());
    }
    public static boolean matches(PlayerInventory.StackSnapshot required,PlayerInventory.StackSnapshot actual) {
        if(actual==null || actual.count()<=0) return false;
        if(required.itemType()==PlayerInventory.WATER_BOTTLE) {
            return actual.itemType()==PlayerInventory.WATER_BOTTLE || actual.itemType()==PlayerInventory.CONTENTS_POTION && "water".equals(actual.itemComponents().potionContents());
        }
        if(required.itemType()!=actual.itemType())return false;
        if(required.enchantments()!=0 && (actual.enchantments()&required.enchantments())!=required.enchantments())return false;
        if(required.mapId()!=0 && required.mapId()!=actual.mapId() || required.shulkerId()!=0 && required.shulkerId()!=actual.shulkerId())return false;
        if(required.bucketMobData()!=null && !required.bucketMobData().equals(actual.bucketMobData()))return false;
        if(required.itemComponentData()!=null) {
            var expected=ItemComponentCodec.decode(required.itemType(),required.itemComponentData());
            if(expected.potionContents()!=null) return expected.potionContents().equals(actual.itemComponents().potionContents());
            return required.itemComponentData().equals(actual.itemComponentData());
        }
        return true;
    }
    public static PlayerInventory.StackSnapshot at(com.gameexpert.engine.inventory.ContainerAccess inventory,int slot) {
        if(inventory.itemType(slot)==0 || inventory.count(slot)==0)return PlayerInventory.StackSnapshot.EMPTY;
        return new PlayerInventory.StackSnapshot(inventory.itemType(slot),inventory.count(slot),inventory.durability(slot),inventory.enchantments(slot),inventory.mapId(slot),inventory.shulkerId(slot),inventory.bucketMobData(slot),inventory.itemComponentData(slot));
    }
    public static PlayerInventory.StackSnapshot at(PlayerInventory inventory,int slot) {
        if(inventory.itemType(slot)==0 || inventory.count(slot)==0)return PlayerInventory.StackSnapshot.EMPTY;
        return new PlayerInventory.StackSnapshot(inventory.itemType(slot),inventory.count(slot),inventory.durability(slot),inventory.enchantments(slot),inventory.mapId(slot),inventory.shulkerId(slot),inventory.bucketMobData(slot),inventory.itemComponentData(slot));
    }
}
