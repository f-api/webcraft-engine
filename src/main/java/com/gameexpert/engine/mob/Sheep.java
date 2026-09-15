package com.gameexpert.engine.mob;

import java.util.List;

import com.gameexpert.terrain.Blocks;

/**
 * 양의 전단·염색·풀 먹기 재성장. AI 는 {@link AnimalMob} 을 그대로 쓰고 여기에는
 * 바닐라 {@code Sheep} + {@code EatBlockGoal} 의 상태만 얹는다.
 *
 * <p>전단 드랍과 사망 드랍 모두 바닐라와 같이 색별 양털 블록
 * ({@code Blocks.WOOL_BY_DYE_COLOR}[color])이다. 사망 드랍은
 * {@code MobSystem.animalDrops(..., sheepColor)} 가 같은 표를 색인하며, 바닐라도 색별 loot
 * table {@code entities/sheep/<color>} 이 정한다(계약은 {@code CONTRACT.md} §3).
 */
public final class Sheep extends AnimalMob {
    private boolean sheared;
    private int color;
    /** 남은 풀 먹기 애니메이션 MC 틱. 0 이면 먹고 있지 않다. */
    private int eatMcTicks;

    Sheep(long id, double x, double y, double z) {
        super(MobType.SHEEP, id, x, y, z, null);
        this.color = FarmAnimalRules.initialSheepColor(id);
    }

    public boolean sheared() { return sheared; }

    public int color() { return color; }

    public int eatMcTicks() { return eatMcTicks; }

    /** 바닐라 {@code Sheep.readyForShearing()} — 살아 있고 털이 있고 새끼가 아니어야 한다. */
    public boolean readyForShearing() {
        return !isDead() && !removed && !sheared && !isBaby();
    }

    /** 전단 확정. 호출자는 {@link #readyForShearing()} 을 먼저 확인한다. */
    public void shear() {
        sheared = true;
        setPersistenceRequired(true);
    }

    /**
     * 바닐라 {@code DyeItem.interactLivingEntity} — 살아 있고 <b>털이 있는</b> 양만,
     * 그리고 색이 실제로 바뀔 때만 염료 한 개를 소비한다.
     */
    public boolean dye(int dyeColorId) {
        if (isDead() || removed || sheared || dyeColorId < 0
                || dyeColorId >= FarmAnimalRules.DYE_COLORS || color == dyeColorId) return false;
        color = dyeColorId;
        setPersistenceRequired(true);
        return true;
    }

    void restoreSheepState(int restoredColor, boolean restoredSheared, int restoredEatMcTicks) {
        if (restoredColor < 0 || restoredColor >= FarmAnimalRules.DYE_COLORS
                || restoredEatMcTicks < 0
                || restoredEatMcTicks > FarmAnimalRules.EAT_BLOCK_ANIMATION_MC_TICKS) {
            throw new IllegalArgumentException("invalid persisted Sheep color/shear state");
        }
        color = restoredColor;
        sheared = restoredSheared;
        eatMcTicks = restoredEatMcTicks;
    }

    /** 새끼 양의 색은 부모 색 규칙이 정한다. 번식 경로가 태어난 직후 호출한다. */
    public void setOffspringColor(int offspringColor) {
        if (offspringColor < 0 || offspringColor >= FarmAnimalRules.DYE_COLORS) return;
        color = offspringColor;
    }

    @Override public int visualFlags() {
        int flags = sheared ? Mob.VISUAL_SHEEP_SHEARED : 0;
        flags |= color << Mob.VISUAL_SHEEP_COLOR_SHIFT & Mob.VISUAL_SHEEP_COLOR_MASK;
        if (eatMcTicks > 0) flags |= Mob.VISUAL_SHEEP_EATING;
        return flags;
    }

    @Override
    public List<MobEvent> tick(MobWorldView world, MobRandom rng) {
        List<MobEvent> events = super.tick(world, rng);
        if (isDead()) return events;
        // 서버 틱은 10 TPS 이고 바닐라 goal 은 20 TPS 이므로 MC 틱 커서를 한 틱에 두 번 굴린다.
        for (int step = 0; step < 2; step++) events = tickEatBlockGoal(world, rng, events);
        return events;
    }

    private List<MobEvent> tickEatBlockGoal(MobWorldView world, MobRandom rng,
                                            List<MobEvent> events) {
        if (eatMcTicks > 0) {
            eatMcTicks--;
            if (eatMcTicks > 0) return events;
            return ate(world, events);
        }
        // 도주·번식 접근 중에는 바닐라도 EatBlockGoal 이 서지 않는다.
        if (state == MobState.FLEE || isInLoveMode()) return events;
        int bound = isBaby()
                ? FarmAnimalRules.EAT_BLOCK_BABY_ROLL_BOUND
                : FarmAnimalRules.EAT_BLOCK_ADULT_ROLL_BOUND;
        if (rng.nextInt(bound) != 0) return events;
        if (edibleTarget(world) == 0) return events;
        eatMcTicks = FarmAnimalRules.EAT_BLOCK_ANIMATION_MC_TICKS;
        return events;
    }

    /**
     * 바닐라 {@code EatBlockGoal.canUse()} 의 두 후보. 발 위치의 짧은 풀이 우선이고,
     * 없으면 발밑 잔디 블록이다. 1 = 짧은 풀, 2 = 잔디 블록, 0 = 먹을 것 없음.
     */
    private int edibleTarget(MobWorldView world) {
        int bx = (int) Math.floor(x);
        int by = (int) Math.floor(y + 0.01);
        int bz = (int) Math.floor(z);
        if (world.getBlock(bx, by, bz) == Blocks.TALL_GRASS) return 1;
        return world.getBlock(bx, by - 1, bz) == Blocks.GRASS ? 2 : 0;
    }

    /** 바닐라 {@code Sheep.ate()}: 털이 자라고, 새끼는 60초만큼 성장이 앞당겨진다. */
    private List<MobEvent> ate(MobWorldView world, List<MobEvent> events) {
        int bx = (int) Math.floor(x);
        int by = (int) Math.floor(y + 0.01);
        int bz = (int) Math.floor(z);
        int target = edibleTarget(world);
        if (target == 1) {
            events = appendEvent(events,
                    new MobEvent.ChangeBlock(bx, by, bz, Blocks.TALL_GRASS, Blocks.AIR));
        } else if (target == 2) {
            events = appendEvent(events,
                    new MobEvent.ChangeBlock(bx, by - 1, bz, Blocks.GRASS, Blocks.DIRT));
        } else {
            return events;
        }
        if (isBaby()) {
            ageUpSeconds(FarmAnimalRules.EAT_BLOCK_BABY_AGE_UP_SECONDS,
                    FarmAnimalRules.AUTHORITY_TICKS_PER_SECOND);
        } else {
            sheared = false;
        }
        setPersistenceRequired(true);
        return appendEvent(events, new MobEvent.Sound("eat"));
    }
}
