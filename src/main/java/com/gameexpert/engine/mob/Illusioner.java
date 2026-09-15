package com.gameexpert.engine.mob;

import java.util.List;

import com.gameexpert.engine.effect.StatusEffects;
import com.gameexpert.engine.inventory.PlayerInventory;

/**
 * Event-only bow caster; it is never admitted by natural spawning.
 *
 * <p>Two vanilla spells are folded into the single tick function. The mirror spell (goal 4) makes
 * the Illusioner invisible for 1,200 MC ticks and preempts the bow goal for the 20 MC tick cast.
 * The blindness spell (goal 5) is carried by the existing tipped-arrow path: the arrow that leaves
 * the bow blinds one target for 400 MC ticks, gated by the vanilla 180 MC tick casting interval and
 * by the vanilla {@code lastTargetId} rule that never blinds the same target twice.
 *
 * <p>The four mirror images are a client-only presentation of the invisible flag. They are never
 * entities, never carry authority state, and never appear in persistence or the spawn protocol.
 */
public final class Illusioner extends Pillager {
    private int invisibilityMcTicks;
    private int mirrorCooldownMcTicks;
    private int blindnessCooldownMcTicks;
    private int castMcTicks;
    private String blindTargetKey;

    public Illusioner(long id, double x, double y, double z) {
        super(id, MobType.ILLUSIONER, x, y, z);
    }

    @Override public short heldItem() { return PlayerInventory.BOW; }
    @Override public int heldItemDurability() {
        return PlayerInventory.initialDurability(PlayerInventory.BOW);
    }

    @Override protected double targetRange() { return 18.0; }

    @Override
    public int visualFlags() {
        return invisibilityMcTicks > 0 ? Mob.VISUAL_ILLUSIONER_INVISIBLE : 0;
    }

    public int invisibilityMcTicks() { return invisibilityMcTicks; }
    public int mirrorCooldownMcTicks() { return mirrorCooldownMcTicks; }
    public int blindnessCooldownMcTicks() { return blindnessCooldownMcTicks; }
    public int castMcTicks() { return castMcTicks; }
    public String blindTargetKey() { return blindTargetKey; }

    /** Durable restore of the two spell timers; every counter is MC ticks. */
    public void restoreIllusionerState(int invisibilityMcTicks, int mirrorCooldownMcTicks,
            int blindnessCooldownMcTicks, int castMcTicks, String blindTargetKey) {
        validate(invisibilityMcTicks, MobEffectRules.ILLUSIONER_INVISIBILITY_MC_TICKS);
        validate(mirrorCooldownMcTicks, MobEffectRules.ILLUSIONER_MIRROR_INTERVAL_MC_TICKS);
        validate(blindnessCooldownMcTicks,
                MobEffectRules.ILLUSIONER_BLINDNESS_INTERVAL_MC_TICKS);
        validate(castMcTicks, MobEffectRules.ILLUSIONER_CAST_MC_TICKS);
        if (blindTargetKey != null && blindTargetKey.isBlank()) {
            throw new IllegalArgumentException("blank Illusioner blind target key");
        }
        this.invisibilityMcTicks = invisibilityMcTicks;
        this.mirrorCooldownMcTicks = mirrorCooldownMcTicks;
        this.blindnessCooldownMcTicks = blindnessCooldownMcTicks;
        this.castMcTicks = castMcTicks;
        this.blindTargetKey = blindTargetKey;
    }

    private static void validate(int value, int limit) {
        if (value < 0 || value > limit) {
            throw new IllegalArgumentException("invalid persisted Illusioner counter");
        }
    }

    @Override
    public List<MobEvent> tick(MobWorldView world, MobRandom rng) {
        if (isDead()) return super.tick(world, rng);
        invisibilityMcTicks = decay(invisibilityMcTicks);
        mirrorCooldownMcTicks = decay(mirrorCooldownMcTicks);
        blindnessCooldownMcTicks = decay(blindnessCooldownMcTicks);
        castMcTicks = decay(castMcTicks);
        // 바닐라 IllusionerMirrorSpellGoal: 표적이 있고 아직 투명하지 않으며 시전 간격이 끝났을 때만.
        if (hasTrackedTarget() && invisibilityMcTicks == 0 && mirrorCooldownMcTicks == 0) {
            invisibilityMcTicks = MobEffectRules.ILLUSIONER_INVISIBILITY_MC_TICKS;
            mirrorCooldownMcTicks = MobEffectRules.ILLUSIONER_MIRROR_INTERVAL_MC_TICKS;
            castMcTicks = MobEffectRules.ILLUSIONER_CAST_MC_TICKS;
            markVisualAction("illusion",
                    MobEffectRules.ILLUSIONER_CAST_MC_TICKS
                            / StatusEffects.MC_TICKS_PER_SERVER_TICK);
        }
        return super.tick(world, rng);
    }

    @Override protected boolean mayShoot() { return castMcTicks == 0; }

    @Override
    protected ProjectileEffect arrowEffect(MobWorldView world, String targetKey) {
        if (targetKey == null || blindnessCooldownMcTicks > 0
                || targetKey.equals(blindTargetKey)) return null;
        blindnessCooldownMcTicks = MobEffectRules.ILLUSIONER_BLINDNESS_INTERVAL_MC_TICKS;
        blindTargetKey = targetKey;
        return MobEffectRules.ILLUSIONER_BLINDNESS_ARROW;
    }

    private static int decay(int mcTicks) {
        return Math.max(0, mcTicks - StatusEffects.MC_TICKS_PER_SERVER_TICK);
    }
}
