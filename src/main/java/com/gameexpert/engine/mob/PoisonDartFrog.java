package com.gameexpert.engine.mob;

import java.util.List;

/** Original amphibious creature that defensively poisons contact and hunts hosted fireflies. */
public final class PoisonDartFrog extends AnimalMob {
    static final class FireflyFeedRequest {
        private final long sequence;
        private final int x;
        private final int y;
        private final int z;

        FireflyFeedRequest(long sequence, int x, int y, int z) {
            this.sequence = sequence;
            this.x = x;
            this.y = y;
            this.z = z;
        }

        long sequence() { return sequence; }
        int x() { return x; }
        int y() { return y; }
        int z() { return z; }
    }

    private int feedCooldownMcTicks;
    private int defensiveTicks;
    private int contactCooldownTicks;
    private long feedSequence;
    private boolean hasHostTarget;
    private int hostX;
    private int hostY;
    private int hostZ;
    private int hostSearchCooldown;
    private FireflyFeedRequest pendingFeed;

    PoisonDartFrog(long id, double x, double y, double z, String variant) {
        super(MobType.POISON_DART_FROG, id, x, y, z, variant);
    }

    @Override
    public void onHurt(String attackerNickname, double attackerX, double attackerZ) {
        super.onHurt(attackerNickname, attackerX, attackerZ);
        defensiveTicks = PoisonDartFrogRules.DEFENSIVE_WINDOW_TICKS;
        setPersistenceRequired(true);
    }

    @Override
    public List<MobEvent> tick(MobWorldView world, MobRandom rng) {
        feedCooldownMcTicks = Math.max(0, feedCooldownMcTicks - 2);
        if (defensiveTicks > 0) defensiveTicks--;
        if (contactCooldownTicks > 0) contactCooldownTicks--;
        if (hostSearchCooldown > 0) hostSearchCooldown--;
        return super.tick(world, rng);
    }

    @Override
    protected double[] scheduledWalk(MobWorldView world) {
        if (!hasHostTarget || feedCooldownMcTicks > 0 || pendingFeed != null) return null;
        if (PoisonDartFrogRules.hostReached(x, y, z, hostX, hostY, hostZ)) {
            pendingFeed = new FireflyFeedRequest(++feedSequence, hostX, hostY, hostZ);
            hasHostTarget = false;
            setPersistenceRequired(true);
            return new double[] {0.0, 0.0};
        }
        synchronizeVisualAction("work", "active", 2);
        return towardHoriz(hostX + 0.5, hostZ + 0.5, type.baseSpeed());
    }

    boolean shouldSearchFireflyHost() {
        return feedCooldownMcTicks == 0 && pendingFeed == null && !hasHostTarget
                && hostSearchCooldown == 0;
    }

    void setFireflyHost(int x, int y, int z) {
        if (feedCooldownMcTicks > 0 || pendingFeed != null) return;
        hasHostTarget = true;
        hostX = x;
        hostY = y;
        hostZ = z;
    }

    void noFireflyHostFound() { hostSearchCooldown = 10; }
    FireflyFeedRequest pendingFireflyFeed() { return pendingFeed; }

    boolean confirmFireflyFeed(long sequence, boolean hostStillConsumable) {
        if (pendingFeed == null || pendingFeed.sequence() != sequence) return false;
        pendingFeed = null;
        if (!hostStillConsumable) {
            hostSearchCooldown = 10;
            return false;
        }
        feedCooldownMcTicks = PoisonDartFrogRules.FEED_COOLDOWN_MC_TICKS;
        synchronizeVisualAction("eat", "active", PoisonDartFrogRules.EAT_ACTION_TICKS);
        setPersistenceRequired(true);
        return true;
    }

    PoisonDartFrogRules.PoisonPlan planDefensiveContact(
            com.gameexpert.engine.Difficulty difficulty, boolean touching) {
        return PoisonDartFrogRules.defensiveContact(
                difficulty, defensiveTicks > 0, touching, contactCooldownTicks == 0);
    }

    public void confirmDefensiveContact() {
        contactCooldownTicks = PoisonDartFrogRules.CONTACT_COOLDOWN_TICKS;
        synchronizeVisualAction("attack", "active", PoisonDartFrogRules.POISON_ACTION_TICKS);
        setPersistenceRequired(true);
    }

    int feedCooldownMcTicks() { return feedCooldownMcTicks; }
    int defensiveTicks() { return defensiveTicks; }
    int contactCooldownTicks() { return contactCooldownTicks; }
    long feedSequence() { return feedSequence; }
    long pendingFeedSequence() { return pendingFeed == null ? 0 : pendingFeed.sequence(); }
    int pendingFeedX() { return pendingFeed == null ? 0 : pendingFeed.x(); }
    int pendingFeedY() { return pendingFeed == null ? 0 : pendingFeed.y(); }
    int pendingFeedZ() { return pendingFeed == null ? 0 : pendingFeed.z(); }

    void restoreSpeciesState(int feedCooldown, int defensive, int contactCooldown, long sequence,
            long pendingSequence, int pendingX, int pendingY, int pendingZ) {
        if (feedCooldown < 0 || feedCooldown > PoisonDartFrogRules.FEED_COOLDOWN_MC_TICKS
                || defensive < 0 || defensive > PoisonDartFrogRules.DEFENSIVE_WINDOW_TICKS
                || contactCooldown < 0
                || contactCooldown > PoisonDartFrogRules.CONTACT_COOLDOWN_TICKS
                || sequence < 0 || pendingSequence < 0
                || (pendingSequence != 0 && pendingSequence != sequence)
                || (pendingSequence != 0 && feedCooldown != 0)
                || (pendingSequence == 0 && (pendingX != 0 || pendingY != 0 || pendingZ != 0))) {
            throw new IllegalArgumentException("invalid poison dart frog persistence state");
        }
        feedCooldownMcTicks = feedCooldown;
        defensiveTicks = defensive;
        contactCooldownTicks = contactCooldown;
        feedSequence = sequence;
        pendingFeed = pendingSequence == 0 ? null
                : new FireflyFeedRequest(pendingSequence, pendingX, pendingY, pendingZ);
    }
}
