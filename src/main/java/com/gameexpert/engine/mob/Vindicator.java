package com.gameexpert.engine.mob;

import com.gameexpert.engine.inventory.PlayerInventory;

/** Vanilla-style close-range Vindicator authority. */
public final class Vindicator extends MeleeMob {
    public static final int MELEE_ATTACK_INTERVAL_AUTHORITY_TICKS = 10;

    public Vindicator(long id, double x, double y, double z) {
        super(id, MobType.VINDICATOR, x, y, z);
    }

    @Override public short heldItem() { return PlayerInventory.IRON_AXE; }
    @Override public int heldItemDurability() {
        return PlayerInventory.initialDurability(PlayerInventory.IRON_AXE);
    }

    @Override protected double detectRange() { return 12.0; }
    @Override protected double attackRange() { return 2.5; }
    @Override protected int attackDamage() {
        // [RAID-OMEN] Vindicator#applyRaidBuffs: 레이드 도끼의 날카로움(0.5·n + 0.5). 기존 정수 피해 계약처럼 올림한다.
        int sharpness = raidBuffLevel();
        if (sharpness <= 0) return 12;
        return (int) Math.ceil(12
                + com.gameexpert.engine.enchant.EnchantmentRules.sharpnessBonusDamageMilli(sharpness) / 1000.0);
    }
    @Override protected int attackCooldownTicks() {
        return MELEE_ATTACK_INTERVAL_AUTHORITY_TICKS;
    }
    @Override protected boolean climbWalls() { return false; }

}
