package com.gameexpert.engine.mob;

import java.util.Optional;

import com.gameexpert.engine.Difficulty;

/**
 * Java 1.21.4 {@code Pufferfish} 의 팽창과 접촉 피해를 10 TPS 권위에서 재사용하는 순수 계약.
 *
 * <p>공식 1.21.4 서버의 {@code Pufferfish.java} 를 매핑과 함께 대조했다. PuffGoal 은 권위
 * AABB 를 축마다 2블록 확장하고, 생존 중이며 관전자·창조 플레이어가 아니고
 * {@code #not_scary_for_pufferfish} 태그에 없는 생물을 위협으로 본다. 목표를 얻으면
 * {@code inflateCounter=1/deflateTimer=0}, 잃으면 inflateCounter=0 이 된다. 본체 틱은 작은
 * 상태를 즉시 중간으로, inflateCounter&gt;40 에서 완전 팽창으로, deflateTimer&gt;60 과
 * &gt;100 에서 차례로 수축시킨다.
 *
 * <p>이 클래스는 공간 조회·피해 커밋·프로토콜 방송을 하지 않는다. 중앙 런타임은 조회 결과를
 * {@link #isScaryTarget(boolean, boolean, boolean, boolean)} 로 거른 뒤
 * {@link #advanceAuthorityTick(State, boolean)} 을 호출하고, 접촉 시
 * {@link #planContact(PuffStage, Difficulty, boolean, boolean)} 결과를 한 번 커밋한다.
 */
public final class PufferfishRules {
    public static final int MC_TICKS_PER_AUTHORITY_TICK = 2;
    public static final double THREAT_BOX_INFLATION = 2.0;
    public static final double MOB_CONTACT_BOX_INFLATION = 0.3;
    public static final int FULL_INFLATE_COUNTER_EXCLUSIVE = 40;
    public static final int MID_DEFLATE_TIMER_EXCLUSIVE = 60;
    public static final int SMALL_DEFLATE_TIMER_EXCLUSIVE = 100;
    public static final int POISON_AMPLIFIER = 0;

    private PufferfishRules() {}

    public enum PuffStage {
        SMALL(0, 0.5),
        MID(1, 0.7),
        FULL(2, 1.0);

        private final int id;
        private final double dimensionScale;

        PuffStage(int id, double dimensionScale) {
            this.id = id;
            this.dimensionScale = dimensionScale;
        }

        public int id() { return id; }
        public double dimensionScale() { return dimensionScale; }

        public static PuffStage fromId(int id) {
            return switch (id) {
                case 0 -> SMALL;
                case 1 -> MID;
                case 2 -> FULL;
                default -> throw new IllegalArgumentException("invalid Pufferfish PuffState: " + id);
            };
        }
    }

    /** PuffState 는 저장되지만 두 카운터는 바닐라처럼 일시적이다. */
    public record State(PuffStage stage, int inflateCounter, int deflateTimer) {
        public State {
            if (stage == null) throw new IllegalArgumentException("stage is required");
            if (inflateCounter < 0 || deflateTimer < 0) {
                throw new IllegalArgumentException("Pufferfish timers must be non-negative");
            }
        }

        public static State small() { return new State(PuffStage.SMALL, 0, 0); }

        /** 바닐라 NBT 는 PuffState 만 저장하므로 로드 때 일시 카운터는 0에서 다시 시작한다. */
        public static State restored(int puffState) {
            return new State(PuffStage.fromId(puffState), 0, 0);
        }
    }

    public enum Transition {
        NONE,
        INFLATE,
        DEFLATE
    }

    public record Step(State state, Transition transition) {}

    /**
     * {@code TargetingConditions.forNonCombat().ignoreInvisibilityTesting().ignoreLineOfSight()} 와
     * SCARY_MOB selector 의 사실 입력. {@code notScaryTag} 는 중앙의 정확한 엔티티 태그 표가 준다.
     */
    public static boolean isScaryTarget(boolean alive, boolean spectator, boolean creativePlayer,
                                        boolean notScaryTag) {
        return alive && !spectator && !creativePlayer && !notScaryTag;
    }

    /** 공식 1.21.4 {@code minecraft:not_scary_for_pufferfish} 의 현재 등록 종 교집합. */
    public static boolean notScaryForPufferfish(MobType type) {
        if (type == null) return false;
        return switch (type) {
            case TURTLE, GUARDIAN, ELDER_GUARDIAN, COD, PUFFERFISH, SALMON,
                    TROPICAL_FISH, DOLPHIN, SQUID, GLOW_SQUID, TADPOLE -> true;
            default -> false;
        };
    }

    /** 10 TPS 권위 한 틱은 Minecraft 본체 틱 두 번과 같은 순서로 전개한다. */
    public static Step advanceAuthorityTick(State state, boolean threatened) {
        return advanceMcTicks(state, threatened, MC_TICKS_PER_AUTHORITY_TICK);
    }

    /** 테스트와 재생 가능한 catch-up 용. 한 호출 안에서 발생한 마지막 가청 전이만 반환한다. */
    public static Step advanceMcTicks(State initial, boolean threatened, int elapsedMcTicks) {
        if (initial == null) throw new IllegalArgumentException("state is required");
        if (elapsedMcTicks < 0) throw new IllegalArgumentException("elapsedMcTicks must be non-negative");
        State state = initial;
        Transition transition = Transition.NONE;
        for (int i = 0; i < elapsedMcTicks; i++) {
            Step next = advanceOneMcTick(state, threatened);
            state = next.state();
            if (next.transition() != Transition.NONE) transition = next.transition();
        }
        return new Step(state, transition);
    }

    private static Step advanceOneMcTick(State state, boolean threatened) {
        int inflate = state.inflateCounter();
        int deflate = state.deflateTimer();
        PuffStage stage = state.stage();
        Transition transition = Transition.NONE;

        // PuffGoal.start / stop runs before the entity's state tick.
        if (threatened && inflate == 0) {
            inflate = 1;
            deflate = 0;
        } else if (!threatened && inflate > 0) {
            inflate = 0;
        }

        if (inflate > 0) {
            if (stage == PuffStage.SMALL) {
                stage = PuffStage.MID;
                transition = Transition.INFLATE;
            } else if (inflate > FULL_INFLATE_COUNTER_EXCLUSIVE && stage == PuffStage.MID) {
                stage = PuffStage.FULL;
                transition = Transition.INFLATE;
            }
            // FULL 이후의 무한 증가가 상태를 바꾸지 않으므로 비교에 필요한 최솟값으로 포화한다.
            inflate = Math.min(FULL_INFLATE_COUNTER_EXCLUSIVE + 1, inflate + 1);
        } else if (stage != PuffStage.SMALL) {
            if (deflate > MID_DEFLATE_TIMER_EXCLUSIVE && stage == PuffStage.FULL) {
                stage = PuffStage.MID;
                transition = Transition.DEFLATE;
            } else if (deflate > SMALL_DEFLATE_TIMER_EXCLUSIVE && stage == PuffStage.MID) {
                stage = PuffStage.SMALL;
                transition = Transition.DEFLATE;
            }
            deflate = Math.min(SMALL_DEFLATE_TIMER_EXCLUSIVE + 2, deflate + 1);
        }
        return new Step(new State(stage, inflate, deflate), transition);
    }

    public record ContactPlan(int damage, int poisonTicks, int poisonAmplifier) {}

    /**
     * 플레이어 충돌 또는 몹의 0.3블록 확장 AABB 접촉이 확인된 뒤의 원자적 결과. 작은 상태와
     * 죽은/비접촉 대상은 아무 동작도 계획하지 않는다. 독은 난이도와 무관한 60×PuffState MC 틱이다.
     */
    public static Optional<ContactPlan> planContact(PuffStage stage, Difficulty difficulty,
                                                    boolean targetAlive, boolean touching) {
        if (stage == null || difficulty == null) throw new IllegalArgumentException("stage/difficulty required");
        if (stage == PuffStage.SMALL || !targetAlive || !touching) return Optional.empty();
        int rawDamage = 1 + stage.id();
        int poisonTicks = 60 * stage.id() / MC_TICKS_PER_AUTHORITY_TICK;
        return Optional.of(new ContactPlan(
                difficulty.scaleContactDamage(rawDamage), poisonTicks, POISON_AMPLIFIER));
    }
}
