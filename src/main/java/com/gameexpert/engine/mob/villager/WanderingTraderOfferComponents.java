package com.gameexpert.engine.mob.villager;

import com.gameexpert.engine.enchant.EnchantRandom;
import com.gameexpert.engine.enchant.EnchantmentRules;
import com.gameexpert.engine.inventory.ItemComponentCodec;
import com.gameexpert.engine.inventory.ItemComponentData;
import com.gameexpert.engine.inventory.PlayerInventory;
import com.gameexpert.engine.mob.villager.VillagerTradeRules.Offer;

/** Official row modifiers; variant seeds are stored with the selected offer. */
public final class WanderingTraderOfferComponents {
    private WanderingTraderOfferComponents() { }
    public static Offer materialize(Offer base,int poolIndex,int variantSeed) {
        if(poolIndex==92) {
            EnchantRandom random=new EnchantRandom(variantSeed);
            int level=5+random.nextInt(15);
            int modified=EnchantmentRules.modifiedEnchantLevel(random,base.resultItem(),level);
            var enchantments=EnchantmentRules.rollEnchantmentsAt(base.resultItem(),modified,random);
            var components=ItemComponentData.EMPTY.withEnchantments(enchantments);
            var result=new PlayerInventory.StackSnapshot(base.resultItem(),1,PlayerInventory.initialDurability(base.resultItem()),enchantments.word0(),0,0,null,ItemComponentCodec.encode(base.resultItem(),components));
            return new Offer(base.costItem(),Math.min(64,base.costCount()+level),base.costBItem(),base.costBCount(),base.resultItem(),base.resultCount(),base.maxUses(),base.xp(),base.priceMultiplierMilli(),base.costPrototype(),base.costBPrototype(),result);
        }
        if(poolIndex==93) {
            String components=ItemComponentCodec.encode(base.resultItem(),ItemComponentData.EMPTY.withPotionContents("long_invisibility"));
            var result=new PlayerInventory.StackSnapshot(base.resultItem(),1,0,0,0,0,null,components);
            return new Offer(base.costItem(),base.costCount(),base.costBItem(),base.costBCount(),base.resultItem(),base.resultCount(),base.maxUses(),base.xp(),base.priceMultiplierMilli(),base.costPrototype(),base.costBPrototype(),result);
        }
        return base;
    }
}
