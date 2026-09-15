package com.gameexpert.engine.mob.villager;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 주민끼리 사회 기억을 나누는 한 틱의 <b>짝짓기 판정</b>이다. 상태(원장·쿨다운)는
 * {@link VillagerSocialState}, 값 규칙은 {@link VillagerGossipRules} 가 갖고 있고 이 클래스는
 * "이번 틱 누구와 누가 이야기하는가"만 확정한다. 실제 전파는 호출자가 확정된 짝에 대해
 * {@link VillagerSocialState#gossipWith} 를 부르며 일어난다.
 *
 * <p>바닐라 근거(Java 1.21.4): MEET 일정의 {@code InteractWith.of(EntityType.VILLAGER, 8,
 * INTERACTION_TARGET, speed, 2)} 로 두 주민이 서로를 상호작용 대상으로 잡고, 그때
 * {@code Villager#gossip} 이 양쪽 쿨다운(1200틱)을 확인한 뒤 {@code gossips.transferFrom(other,
 * random, 10)} 을 돌린다.
 *
 * <p>WebCraft divergence(계약, 두 권위 동일):
 * <ul>
 *   <li>INTERACTION_TARGET 기억이 없으므로 후보 탐색 없이 매 틱 2블록 안의 짝을 직접 본다.</li>
 *   <li>한 주민은 한 틱에 최대 한 번만 이야기한다(바닐라도 상호작용 대상이 하나다).</li>
 *   <li>짝은 mob id 오름차순으로 확정되어 두 권위가 같은 순서로 같은 짝을 만든다.</li>
 *   <li>누가 듣는가(=상대 기억을 받아오는가)는 바닐라에서 엔티티 틱 순서가 정하지만, 여기서는
 *       {@link VillagerGossipRules#lowerIdListens} 가 (gameTime, 두 id)에서 결정적으로 뽑는다.</li>
 * </ul>
 */
public final class VillagerGossipExchange {

    /** 이번 틱 판정 대상 주민 하나. */
    public interface GossipParticipant {
        long id();

        double x();

        double y();

        double z();

        boolean baby();

        /** 마지막으로 gossip 을 나눈 시각. 아직 없으면 {@code Long.MIN_VALUE}. */
        long lastGossipTime();

        /**
         * 이 주민의 현재 활동. 정본은 {@link VillagerActivityLedger} 이며 권위 배선이 그 값을 넘긴다.
         * 원장이 없는 순수 테스트 월드는 막 만들어진 뇌의 일정 값이다.
         *
         * @param dayTime MC 일정 축(0..23999)의 시각
         */
        default VillagerBrainRules.Activity activity(long dayTime) {
            return VillagerActivityLedger.fallbackActivity(dayTime, baby());
        }
    }

    /**
     * 이번 틱 확정된 대화 한 건. {@code listenerId} 가 말을 건 쪽(=상대 기억을 받아오는 쪽)이고
     * {@code tellerId} 가 그 상대다. 방향은 {@link VillagerGossipRules#lowerIdListens} 가 정한다.
     */
    public record Exchange(long listenerId, long tellerId) {}

    private VillagerGossipExchange() {}

    /**
     * 이번 틱에 실제로 일어날 대화를 확정한다. 호출자는 돌려받은 순서 그대로
     * {@code listener.gossipWith(teller, gameTime, VillagerGossipRules.transferRandom(...))} 를
     * 부르면 된다.
     *
     * @param gameTime 단조 증가하는 절대 틱
     * @param dayTime  하루 안의 틱(0~23999). MEET 일정 판정에 쓴다
     */
    public static List<Exchange> plan(long gameTime, long dayTime,
            List<? extends GossipParticipant> participants) {
        if (participants == null || participants.size() < 2) return List.of();
        List<GossipParticipant> ordered = new ArrayList<>();
        for (GossipParticipant participant : participants) {
            if (participant == null) continue;
            // 바닐라: MEET 일정에서만 만나 이야기한다. 아기 일정에는 MEET 이 없다.
            if (participant.activity(dayTime) != VillagerBrainRules.Activity.MEET) {
                continue;
            }
            // 개인 쿨다운은 상대와 무관하다. 여기서 먼저 거르면 방금 대화한 대규모 집단의
            // 거리 O(V²)를 전부 없애면서도 아래 id-순서 greedy pairing은 그대로다.
            if (!VillagerGossipRules.gossipExchangeDue(
                    gameTime, participant.lastGossipTime(), Long.MIN_VALUE)) {
                continue;
            }
            ordered.add(participant);
        }
        if (ordered.size() < 2) return List.of();
        ordered.sort(Comparator.comparingLong(GossipParticipant::id));

        boolean[] spoken = new boolean[ordered.size()];
        List<Exchange> exchanges = new ArrayList<>();
        for (int left = 0; left < ordered.size(); left++) {
            if (spoken[left]) continue;
            GossipParticipant first = ordered.get(left);
            for (int right = left + 1; right < ordered.size(); right++) {
                if (spoken[right]) continue;
                GossipParticipant second = ordered.get(right);
                if (!VillagerGossipRules.withinGossipRange(distanceSquared(first, second))) {
                    continue;
                }
                spoken[left] = true;
                spoken[right] = true;
                boolean lowerListens = VillagerGossipRules.lowerIdListens(
                        gameTime, first.id(), second.id());
                exchanges.add(lowerListens
                        ? new Exchange(first.id(), second.id())
                        : new Exchange(second.id(), first.id()));
                break;
            }
        }
        return exchanges;
    }

    private static double distanceSquared(GossipParticipant left, GossipParticipant right) {
        double dx = left.x() - right.x();
        double dy = left.y() - right.y();
        double dz = left.z() - right.z();
        return dx * dx + dy * dy + dz * dz;
    }
}
