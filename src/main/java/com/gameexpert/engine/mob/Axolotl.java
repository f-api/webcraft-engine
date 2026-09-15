package com.gameexpert.engine.mob;

/** 물속 양서류. 서버 권위 색상 변종을 영속 스냅샷에 보존한다. */
public final class Axolotl extends AquaticAnimalMob {
    private final String variant;

    public Axolotl(long id, double x, double y, double z, String variant) {
        super(id, MobType.AXOLOTL, x, y, z);
        if (!MobType.AXOLOTL.acceptsVariant(variant)) {
            throw new IllegalArgumentException("Invalid axolotl variant: " + variant);
        }
        this.variant = variant;
    }

    private double pendingEntityDamage;
    private double healthBeforeHit;

    @Override public boolean damage(double amount, long tickNo, String cause, double armorDelta) {
        double before = exactHealth();
        boolean accepted = super.damage(amount, tickNo, cause, armorDelta);
        if (accepted && !isDead() && ("mob".equals(cause) || "arrow".equals(cause))
                && amount < before && !"play_dead".equals(actionKind())) {
            pendingEntityDamage = amount;
            healthBeforeHit = before;
        }
        return accepted;
    }

    @Override public java.util.List<MobEvent> tick(MobWorldView world, MobRandom rng) {
        boolean water = bodyWaterPresence(world) == BODY_WATER_WET;
        if (pendingEntityDamage > 0) {
            double damage = pendingEntityDamage;
            pendingEntityDamage = 0;
            if (rng.nextInt(3) == 0 && (rng.nextInt(3) < damage || healthBeforeHit / type.maxHp() < 0.5)
                    && water && !isDead() && !"play_dead".equals(actionKind())) {
                markVisualAction("play_dead", 100);
                statusEffects().applyMcTicks(com.gameexpert.engine.effect.StatusEffect.REGENERATION, 0, 200);
            }
        }
        if ("play_dead".equals(actionKind())) {
            if (water && !isDead()) {
                state = MobState.IDLE;
                return java.util.List.of();
            }
            synchronizeVisualAction("none", "idle", 0);
        }
        return super.tick(world, rng);
    }

    @Override
    public String variant() {
        return variant;
    }
}
