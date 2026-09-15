package com.gameexpert.engine.mob;

import com.gameexpert.engine.inventory.PlayerInventory;
import java.util.List;

/** Java 1.21.4 Armadillo body and breeding identity; shell state is authority-owned here. */
public final class Armadillo extends AnimalMob {
    public enum ShellState { IDLE, ROLLING, SCARED, UNROLLING }

    static final int ROLLING_MC_TICKS = 10;
    static final int SCARED_MIN_MC_TICKS = 50;
    static final int UNROLLING_MC_TICKS = 30;
    static final int DANGER_MEMORY_MC_TICKS = 80;

    private ShellState shellState = ShellState.IDLE;
    private int stateMcTicks;
    private int scuteMcTicks;
    private int dangerMcTicks;
    // Transient fluid contact is rebuilt by the authority before shell AI.
    private boolean touchingLiquid;

    public Armadillo(long id, double x, double y, double z) {
        this(id, x, y, z, 0);
    }

    Armadillo(long id, double x, double y, double z, int worldSeed) {
        super(MobType.ARMADILLO, id, x, y, z, null);
        scuteMcTicks = initialScuteMcTicks(id);
    }

    /** Same append-free initialization used by both authorities and legacy-row backfill. */
    public static int initialScuteMcTicks(long mobId) {
        int hash = (int) (mobId ^ (mobId >>> 32));
        hash ^= hash >>> 16;
        hash *= 0x7feb352d;
        hash ^= hash >>> 15;
        return 6_000 + (int) (Integer.toUnsignedLong(hash) % 6_000L);
    }

    public ShellState shellState() { return shellState; }
    public int stateMcTicks() { return stateMcTicks; }
    public int scuteMcTicks() { return scuteMcTicks; }
    int dangerMcTicks() { return dangerMcTicks; }

    public void restoreArmadilloState(ShellState state, int scuteTicks) {
        restoreArmadilloState(state, 0, 0, scuteTicks);
    }

    public void restoreArmadilloState(ShellState state, int stateTicks,
                                      int dangerTicks, int scuteTicks) {
        if (state == null || stateTicks < 0 || dangerTicks < 0
                || dangerTicks > DANGER_MEMORY_MC_TICKS
                || scuteTicks < 0 || scuteTicks > 11_999) {
            throw new IllegalArgumentException("invalid persisted Armadillo state");
        }
        shellState = state;
        stateMcTicks = stateTicks;
        dangerMcTicks = dangerTicks;
        scuteMcTicks = scuteTicks;
        syncVisual();
    }

    @Override
    public void onHurt(String attackerNickname, double attackerX, double attackerZ) {
        clearLoveMode();
        dangerMcTicks = DANGER_MEMORY_MC_TICKS;
        if (!touchingLiquid && (shellState == ShellState.IDLE || shellState == ShellState.UNROLLING)) {
            enterShellState(ShellState.ROLLING);
        }
    }

    @Override
    protected double adjustIncomingDamage(double rawAmount) {
        return shellState == ShellState.SCARED
                ? Math.max(0.0, (rawAmount - 1.0) * 0.5)
                : rawAmount;
    }

    @Override
    public List<MobEvent> tick(MobWorldView world, MobRandom rng) {
        if (isDead()) return List.of();
        touchingLiquid = bodyTouchesWater(world) || bodyTouchesLava(world);
        if (touchingLiquid) {
            dangerMcTicks = 0;
            enterShellState(ShellState.IDLE);
        }
        List<MobEvent> events = List.of();
        for (int virtualTick = 0; virtualTick < 2; virtualTick++) {
            if (scuteMcTicks > 0) scuteMcTicks--;
            if (scuteMcTicks == 0) {
                events = appendEvent(events, new MobEvent.DropItem(
                        PlayerInventory.ARMADILLO_SCUTE, 1, x, y + 0.5, z));
                scuteMcTicks = 6_000 + rng.nextInt(6_000);
            }
        }
        if (shellState == ShellState.IDLE && dangerMcTicks == 0) {
            for (MobEvent event : super.tick(world, rng)) events = appendEvent(events, event);
            return events;
        }
        for (int virtualTick = 0; virtualTick < 2; virtualTick++) {
            tickShellState();
        }
        if (shellState == ShellState.IDLE) {
            for (MobEvent event : super.tick(world, rng)) events = appendEvent(events, event);
            return events;
        }
        state = MobState.IDLE;
        MobPhysics.tickMove(this, world, 0.0, 0.0, 0.0, MoveMode.WALK);
        return events;
    }

    private void tickShellState() {
        if (dangerMcTicks > 0) dangerMcTicks--;
        stateMcTicks++;
        switch (shellState) {
            case IDLE -> {
                if (dangerMcTicks > 0) enterShellState(ShellState.ROLLING);
            }
            case ROLLING -> {
                if (stateMcTicks >= ROLLING_MC_TICKS) enterShellState(ShellState.SCARED);
            }
            case SCARED -> {
                if (stateMcTicks >= SCARED_MIN_MC_TICKS && dangerMcTicks == 0) {
                    enterShellState(ShellState.UNROLLING);
                }
            }
            case UNROLLING -> {
                if (dangerMcTicks > 0) enterShellState(ShellState.ROLLING);
                else if (stateMcTicks >= UNROLLING_MC_TICKS) enterShellState(ShellState.IDLE);
            }
        }
    }

    private void enterShellState(ShellState next) {
        if (shellState == next) return;
        shellState = next;
        stateMcTicks = 0;
        syncVisual();
    }

    private void syncVisual() {
        // Shell pose is encoded in visualFlags(). It remains stable through long danger windows
        // without manufacturing a new action sequence every authority tick.
    }

    @Override
    public int visualFlags() {
        int stateFlag = switch (shellState) {
            case IDLE -> 0;
            case ROLLING -> Mob.VISUAL_ARMADILLO_ROLLING;
            case SCARED -> Mob.VISUAL_ARMADILLO_SCARED;
            case UNROLLING -> Mob.VISUAL_ARMADILLO_UNROLLING;
        };
        boolean hiddenInShell = switch (shellState) {
            case IDLE -> false;
            case ROLLING -> stateMcTicks > 5;
            case SCARED -> true;
            case UNROLLING -> stateMcTicks < 26;
        };
        return hiddenInShell ? stateFlag | Mob.VISUAL_ARMADILLO_HIDDEN_IN_SHELL : stateFlag;
    }
}
