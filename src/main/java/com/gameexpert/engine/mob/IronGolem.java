package com.gameexpert.engine.mob;

import java.util.List;

/** Empty-village guardian: neutral until directly hit by a player. */
public final class IronGolem extends MeleeMob {
    private String provokedBy;
    public IronGolem(long id,double x,double y,double z){super(id,MobType.IRON_GOLEM,x,y,z);}
    public void provoke(String nickname){ if(nickname!=null) { provokedBy=nickname; forceTarget(nickname); } }
    public boolean provoked(){ return provokedBy!=null; }
    @Override protected double detectRange(){return 16;}
    @Override protected double attackRange(){return 2.0;}
    @Override protected int attackDamage(){return 8;}
    /** Integer protocol approximation of Java normal difficulty's 7.5..21.5 points. */
    @Override protected int attackDamage(MobRandom rng){return 8 + rng.nextInt(14);}
    @Override protected int attackCooldownTicks(){return 12;}
    @Override protected boolean climbWalls(){return false;}
    @Override protected boolean hostile(MobWorldView world){return provokedBy!=null;}
    @Override public List<MobEvent> tick(MobWorldView world,MobRandom rng){
        if(provokedBy!=null) forceTarget(provokedBy);
        return super.tick(world,rng);
    }
    @Override public void onHurt(String nickname,double attackerX,double attackerZ){ provoke(nickname); }
    /**
     * {@code IronGolem#getCrackiness}: {@code Crackiness.GOLEM.byFraction(getHealth() / getMaxHealth())}
     * in float arithmetic (HIGH &lt; 0.25, MEDIUM &lt; 0.5, LOW &lt; 0.75, else NONE), which the
     * client draws as the crackiness overlay.
     */
    @Override public int visualFlags() {
        return crackLevel(exactHealth(), maxHp()) << Mob.VISUAL_IRON_GOLEM_CRACK_SHIFT
                & Mob.VISUAL_IRON_GOLEM_CRACK_MASK;
    }
    static int crackLevel(double health, double maxHealth) {
        if (!(maxHealth > 0)) return 0;
        float fraction = (float) health / (float) maxHealth;
        if (fraction < 0.25F) return 3;
        if (fraction < 0.5F) return 2;
        return fraction < 0.75F ? 1 : 0;
    }
    /** Java knockback resistance 1.0: ordinary combat knockback has no effect. */
    @Override public void applyKnockback(double baseX,double baseZ,boolean grounded,
                                         double bonusX,double bonusZ) {
        // Fully resisted.
    }
    @Override public void applyExplosionKnockback(double x,double y,double z) {
        // Fully resisted.
    }
}
