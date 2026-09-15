package com.gameexpert.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.Accessors;

/**
 * 여러 WS 스레드가 넣고 단일 월드 틱 스레드가 비우는 MPSC 액션 큐.
 *
 * <p>이동과 플레이 액션은 도착 순서를 함께 보존하고, 부활은 마지막에 처리합니다.
 * 뒤늦게 도착한 이동이 앞선 투척·공격의 위치와 방향을 덮어쓰면 안 됩니다.
 *
 * <p><b>용량 상한</b>: 큐는 무제한이 아닙니다. 한 세션이 틱 소비 속도보다 빠르게 액션을 밀어 넣어
 * 힙과 틱 스레드를 고갈시키는 것을 막기 위해 (1) 플레이어별 상한과 (2) 월드 전체 상한을 함께
 * 강제합니다. 플레이어별 상한이 있어야 한 세션이 전체 예산을 독점해 다른 접속자의 액션까지
 * 굶기는 상황을 막을 수 있습니다. 상한을 넘긴 액션은 조용히 버리지 않고 {@code false}를 돌려
 * 호출자가 클라이언트에 거부를 알리도록 합니다.
 *
 * <p>정상 플레이의 큐 깊이는 틱(100ms)당 이동 1~2개 수준이므로 아래 기본값은 정상 입력을
 * 자르지 않으면서 폭주만 잘라 냅니다.
 */
public final class ActionQueue {

    /** 월드 전체가 한 틱 사이에 보유할 수 있는 최대 액션 수. */
    public static final int DEFAULT_MAX_TOTAL = 4096;
    /** 한 플레이어가 한 틱 사이에 보유할 수 있는 최대 액션 수. */
    public static final int DEFAULT_MAX_PER_PLAYER = 256;
    /** 계정 추적 맵이 무한히 커지지 않도록 하는 서로 다른 닉네임 상한. */
    private static final int MAX_TRACKED_PLAYERS = 512;

    private static final Drained EMPTY = new Drained(List.of(), List.of(), List.of(), List.of());

    private final ConcurrentLinkedQueue<PlayerAction> queue = new ConcurrentLinkedQueue<>();
    private final AtomicInteger queued = new AtomicInteger();
    /** 닉네임별 미소비 액션 수. 항목은 제거하지 않고 0으로 되돌려 카운터 경합을 피합니다. */
    private final Map<String, AtomicInteger> queuedPerPlayer = new ConcurrentHashMap<>();

    private final int maxTotal;
    private final int maxPerPlayer;

    public ActionQueue() {
        this(DEFAULT_MAX_TOTAL, DEFAULT_MAX_PER_PLAYER);
    }

    ActionQueue(int maxTotal, int maxPerPlayer) {
        this.maxTotal = maxTotal;
        this.maxPerPlayer = maxPerPlayer;
    }

    /**
     * 액션을 큐에 넣습니다.
     *
     * @return 상한 안에서 수용했으면 {@code true}, 플레이어별/전체 상한을 넘겨 거부했으면 {@code false}
     */
    public boolean enqueue(PlayerAction action) {
        AtomicInteger perPlayer = counterFor(action.nickname());
        if (perPlayer == null) return false;
        if (perPlayer.incrementAndGet() > maxPerPlayer) {
            perPlayer.decrementAndGet();
            return false;
        }
        if (queued.incrementAndGet() > maxTotal) {
            queued.decrementAndGet();
            perPlayer.decrementAndGet();
            return false;
        }
        queue.add(action);
        return true;
    }

    /** 현재 미소비 액션 수(테스트·진단용). */
    public int size() {
        return queued.get();
    }

    /** owner가 입력 lease를 닫은 뒤 해당 연결의 남은 액션만 폐기한다. 다른 플레이어는 유지한다. */
    void discardPlayer(String nickname) {
        String expected = key(nickname);
        for (PlayerAction action : queue) {
            if (key(action.nickname()).equals(expected) && queue.remove(action)) release(action);
        }
    }

    public Drained drain() {
        // 점유 상한은 소비 중의 재유입을 제한하지 않는다. 턴 시작 시 처리 개수 예산을
        // 고정하여 생산자가 반환된 슬롯을 채우더라도 이번 drain이 늘어나지 않게 한다.
        int remaining = Math.min(maxTotal, queued.get());
        if (remaining <= 0) return EMPTY;
        PlayerAction action = queue.poll();
        if (action == null) return EMPTY;
        ArrayList<PlayerAction.Move> moves = null;
        ArrayList<PlayerAction> gameplayActions = null;
        ArrayList<PlayerAction.Respawn> respawns = null;
        ArrayList<PlayerAction> orderedActions = null;
        do {
            release(action);
            if (action instanceof PlayerAction.Move move) {
                moves = append(moves, move);
                orderedActions = append(orderedActions, move);
            } else if (action instanceof PlayerAction.Respawn respawn) {
                respawns = append(respawns, respawn);
            } else {
                gameplayActions = append(gameplayActions, action);
                orderedActions = append(orderedActions, action);
            }
        } while (--remaining > 0 && (action = queue.poll()) != null);
        return new Drained(orEmpty(moves), orEmpty(gameplayActions), orEmpty(respawns),
                orEmpty(orderedActions));
    }

    private void release(PlayerAction action) {
        queued.decrementAndGet();
        AtomicInteger perPlayer = queuedPerPlayer.get(key(action.nickname()));
        if (perPlayer != null) perPlayer.decrementAndGet();
    }

    /** 추적 상한을 넘긴 새 닉네임은 카운터를 만들지 않고 거부합니다. */
    private AtomicInteger counterFor(String nickname) {
        String key = key(nickname);
        AtomicInteger existing = queuedPerPlayer.get(key);
        if (existing != null) return existing;
        if (queuedPerPlayer.size() >= MAX_TRACKED_PLAYERS) return null;
        return queuedPerPlayer.computeIfAbsent(key, ignored -> new AtomicInteger());
    }

    private static String key(String nickname) {
        return nickname == null ? "" : nickname.toLowerCase(Locale.ROOT);
    }

    private static <T> ArrayList<T> append(ArrayList<T> list, T value) {
        ArrayList<T> target = list == null ? new ArrayList<>() : list;
        target.add(value);
        return target;
    }

    private static <T> List<T> orEmpty(ArrayList<T> list) {
        return list == null ? List.of() : list;
    }

    @Getter
    @Accessors(fluent = true)
    @AllArgsConstructor
    public static class Drained {
        private final List<PlayerAction.Move> moves;
        private final List<PlayerAction> gameplayActions;
        private final List<PlayerAction.Respawn> respawns;
        private final List<PlayerAction> orderedActions;
    }
}
