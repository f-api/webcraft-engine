package com.gameexpert.engine.mob.villager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 종({@code minecraft:bell}) 치기의 권위 규칙과 종 한 칸의 휘발 상태다.
 *
 * <p>바닐라 근거(26.3-snapshot-7 client jar javap):
 * <ul>
 *   <li>{@code BellBlock#useWithoutItem} → {@code onHit(level, state, hit, player, true)}:
 *       {@code isProperHit} 이면 {@code attemptToRing} 후 SUCCESS, 아니면 PASS.
 *       {@code isProperHit}: 누른 면의 축이 Y 이거나 칸 안 높이가 {@code 0.8124F} 를 넘으면 거짓,
 *       그 밖은 걸이 방식(FLOOR 는 facing 축과 같은 축, 벽 둘은 다른 축, CEILING 은 어느 옆면이든).</li>
 *   <li>{@code BellBlock#attemptToRing}: {@code BellBlockEntity#onHit(direction)} 으로 block event 1 을
 *       보내고 {@code BELL_BLOCK}(block.bell.use)을 BLOCKS·volume 2·pitch 1 로 낸다.</li>
 *   <li>{@code BellBlockEntity#triggerEvent(1)}: {@code updateEntities()} 뒤 공명 틱 0, 흔들림 틱 0,
 *       흔들림 켬. {@code updateEntities}: 직전 목록 갱신에서 60틱을 <b>넘겼거나</b> 목록이 없으면 칸
 *       AABB 를 48 부풀린 상자와 겹치는 LivingEntity 로 목록을 새로 만들고, 목록 중 살아 있고 칸 중심
 *       에서 32 미만인 개체에게 {@code HEARD_BELL_TIME = gameTime}.</li>
 *   <li>{@code BellBlockEntity#tick}: 흔들리는 동안 틱을 세고 50에서 멈춘다. 5틱 이상이고 공명 틱이
 *       0이며 목록에 32 미만 raider 가 있으면 공명(block.bell.resonate, volume 1·pitch 1)을 시작하고,
 *       공명 틱이 40 에 이르면 끝 동작(서버: 48 안 raider 에게 GLOWING 60틱)을 부른다.</li>
 * </ul>
 *
 * <p>치기 판정({@code isProperHit})·공명 소리·습격자 발광은 [BLOCK-SHAPES]/[GLOWING] 의
 * {@code WorldTickLoop#ringBell}·{@code BellResonance} 가 소유한다. 이 클래스는 그 치기에서 주민의
 * HEARD_BELL_TIME 목록만 센다. 종 상태는 바닐라와 같이 저장하지 않는다(블록 엔티티의 목록은 NBT 에 없다).
 *
 * <p>정적판 사본은 {@code StandaloneVillagerBellRules.ts} 이다.
 */
public final class VillagerBellRules {

    /** {@code BellBlockEntity.HEAR_BELL_RADIUS}. */
    public static final int HEAR_BELL_RADIUS_BLOCKS = 32;
    /** {@code BellBlockEntity.SEARCH_RADIUS}: 목록 AABB 부풀림. */
    public static final int SEARCH_RADIUS_BLOCKS = 48;
    /** {@code BellBlockEntity.MIN_TICKS_BETWEEN_SEARCHES}. */
    public static final int MIN_TICKS_BETWEEN_SEARCHES = 60;
    /** {@code BellBlockEntity.DURATION}: 흔들림 MC 틱. */
    public static final int SHAKE_DURATION_TICKS = 50;
    /** {@code BellBlockEntity.TICKS_BEFORE_RESONATION}. */
    public static final int TICKS_BEFORE_RESONATION = 5;
    /** {@code BellBlockEntity.MAX_RESONATION_TICKS}. */
    public static final int MAX_RESONATION_TICKS = 40;
    /** {@code BellBlockEntity.HIGHLIGHT_RAIDERS_RADIUS}. */
    public static final int HIGHLIGHT_RAIDERS_RADIUS_BLOCKS = 48;
    /** 종 목록에 들어갈 수 있는 살아 있는 개체 하나. */
    public interface Hearer {
        long id();

        double x();

        double y();

        double z();

        double width();

        double height();

        /** 주민인가(HEARD_BELL_TIME 기억을 가진 뇌). */
        boolean villager();

        /** {@code #minecraft:raiders} 태그인가. */
        boolean raider();
    }

    /** 이번 치기가 만든 결과: 종을 들은 주민 id. */
    public record Ring(List<Long> heardVillagerIds) {}

    /** 한 권위 틱의 종 사건: 공명 시작 좌표와 공명이 끝나 빛나야 할 raider id. */
    public record TickEvents(List<int[]> resonanceStarts, List<Long> glowingRaiderIds) {
        static final TickEvents NONE = new TickEvents(List.of(), List.of());
    }

    /** 살아 있는 개체를 id 로 찾는다(목록은 id 만 들고 있다). */
    @FunctionalInterface
    public interface HearerLookup {
        Hearer find(long id);
    }

    private static final class Bell {
        private final int x;
        private final int y;
        private final int z;
        private long lastRingTimestamp;
        private List<Long> nearby;
        private int ticks;
        private boolean shaking;
        private boolean resonating;
        private int resonationTicks;

        private Bell(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    private final Map<Long, Bell> bells = new java.util.LinkedHashMap<>();

    /**
     * 종을 친다({@code BellBlockEntity#triggerEvent(1)}). 호출자는 이미 {@code isProperHit} 과
     * 리치를 확인했고 소리를 방송한다.
     *
     * @param candidates 활성 개체 전부(목록 갱신 때만 읽는다)
     */
    public Ring ring(int x, int y, int z, long gameTime, List<? extends Hearer> candidates,
            HearerLookup lookup) {
        Bell bell = bells.computeIfAbsent(key(x, y, z), ignored -> new Bell(x, y, z));
        if (bell.nearby == null || gameTime > bell.lastRingTimestamp + MIN_TICKS_BETWEEN_SEARCHES) {
            bell.lastRingTimestamp = gameTime;
            bell.nearby = new ArrayList<>();
            double minX = x - SEARCH_RADIUS_BLOCKS;
            double minY = y - SEARCH_RADIUS_BLOCKS;
            double minZ = z - SEARCH_RADIUS_BLOCKS;
            double maxX = x + 1 + SEARCH_RADIUS_BLOCKS;
            double maxY = y + 1 + SEARCH_RADIUS_BLOCKS;
            double maxZ = z + 1 + SEARCH_RADIUS_BLOCKS;
            for (Hearer hearer : candidates) {
                double half = hearer.width() / 2.0;
                // AABB#intersects: 두 상자가 열린 구간으로 겹친다.
                if (hearer.x() + half > minX && hearer.x() - half < maxX
                        && hearer.y() + hearer.height() > minY && hearer.y() < maxY
                        && hearer.z() + half > minZ && hearer.z() - half < maxZ) {
                    bell.nearby.add(hearer.id());
                }
            }
            bell.nearby.sort(Long::compare);
        }
        List<Long> heard = new ArrayList<>();
        for (long id : bell.nearby) {
            Hearer hearer = lookup.find(id);
            if (hearer == null || !hearer.villager()) continue;
            if (closerToCenterThan(x, y, z, hearer, HEAR_BELL_RADIUS_BLOCKS)) heard.add(id);
        }
        bell.resonationTicks = 0;
        bell.ticks = 0;
        bell.shaking = true;
        return new Ring(heard);
    }

    /**
     * 한 권위 틱({@code BellBlockEntity#serverTick} 을 MC 틱 두 번). 흔들림·공명이 끝났고 목록 캐시
     * (60틱)도 지난 종만 버린다 — 그 뒤의 치기는 바닐라에서도 목록을 새로 만들므로 판정이 같다.
     *
     * @param gameTime 이번 권위 틱의 MC 게임 시각
     */
    public TickEvents tick(long gameTime, HearerLookup lookup) {
        if (bells.isEmpty()) return TickEvents.NONE;
        List<int[]> resonance = null;
        List<Long> glowing = null;
        List<Long> finished = null;
        for (Map.Entry<Long, Bell> entry : bells.entrySet()) {
            Bell bell = entry.getValue();
            for (int mcTick = 0; mcTick < VillagerSocietyRules.MC_TICKS_PER_AUTHORITY_TICK;
                    mcTick++) {
                if (bell.shaking) bell.ticks++;
                if (bell.ticks >= SHAKE_DURATION_TICKS) {
                    bell.shaking = false;
                    bell.ticks = 0;
                }
                if (bell.ticks >= TICKS_BEFORE_RESONATION && bell.resonationTicks == 0
                        && raidersNearby(bell, lookup)) {
                    bell.resonating = true;
                    if (resonance == null) resonance = new ArrayList<>();
                    resonance.add(new int[] { bell.x, bell.y, bell.z });
                }
                if (bell.resonating) {
                    if (bell.resonationTicks < MAX_RESONATION_TICKS) {
                        bell.resonationTicks++;
                    } else {
                        for (long id : bell.nearby) {
                            Hearer hearer = lookup.find(id);
                            if (hearer != null && hearer.raider() && closerToCenterThan(
                                    bell.x, bell.y, bell.z, hearer,
                                    HIGHLIGHT_RAIDERS_RADIUS_BLOCKS)) {
                                if (glowing == null) glowing = new ArrayList<>();
                                glowing.add(id);
                            }
                        }
                        bell.resonating = false;
                    }
                }
            }
            if (!bell.shaking && !bell.resonating
                    && gameTime > bell.lastRingTimestamp + MIN_TICKS_BETWEEN_SEARCHES) {
                if (finished == null) finished = new ArrayList<>();
                finished.add(entry.getKey());
            }
        }
        if (finished != null) finished.forEach(bells::remove);
        if (resonance == null && glowing == null) return TickEvents.NONE;
        return new TickEvents(resonance == null ? List.of() : resonance,
                glowing == null ? List.of() : glowing);
    }

    /** 흔들리는 중인가(테스트·관측). */
    public boolean shaking(int x, int y, int z) {
        Bell bell = bells.get(key(x, y, z));
        return bell != null && bell.shaking;
    }

    /** 종 칸이 사라졌을 때. */
    public void forget(int x, int y, int z) {
        bells.remove(key(x, y, z));
    }

    private static boolean raidersNearby(Bell bell, HearerLookup lookup) {
        for (long id : bell.nearby) {
            Hearer hearer = lookup.find(id);
            if (hearer != null && hearer.raider()
                    && closerToCenterThan(bell.x, bell.y, bell.z, hearer,
                            HEAR_BELL_RADIUS_BLOCKS)) {
                return true;
            }
        }
        return false;
    }

    /** {@code BlockPos.closerToCenterThan(position, d)}. */
    static boolean closerToCenterThan(int x, int y, int z, Hearer hearer, int d) {
        double dx = x + 0.5 - hearer.x();
        double dy = y + 0.5 - hearer.y();
        double dz = z + 0.5 - hearer.z();
        return dx * dx + dy * dy + dz * dz < (double) d * d;
    }

    private static long key(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }
}
