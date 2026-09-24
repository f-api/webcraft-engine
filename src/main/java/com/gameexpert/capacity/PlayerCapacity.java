package com.gameexpert.capacity;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * 이 서버가 동시에 받는 플레이어 수의 상한입니다.
 *
 * 작은 인스턴스에서는 사람이 늘수록 월드 생성이 CPU를 다 쓰기 때문에, 들어올 수 있는 인원을
 * 미리 막아 두는 편이 모두에게 낫다. 자리는 사람(닉네임) 단위로 쥔다 — 접속이 끊겨 다시
 * 들어오는 사람은 자기 자리를 그대로 쓰므로, 재접속이 자기 자리에 막히지 않는다.
 * 값은 {@code webcraft.players.max} 로 바꾼다(기본 3).
 */
@Component
public class PlayerCapacity {

    private final int max;
    private final boolean fixedWorld;
    private final AtomicInteger live = new AtomicInteger();
    private final Set<String> holders = ConcurrentHashMap.newKeySet();

    public PlayerCapacity(Environment environment) {
        this.max = Math.max(1, environment.getProperty("webcraft.players.max", Integer.class, 3));
        this.fixedWorld = environment.getProperty("webcraft.worlds.fixed", Boolean.class, false);
    }

    public int max() { return max; }

    /** 월드를 새로 만들거나 지울 수 없는 서버인가. 로비가 해당 버튼을 숨기는 데 쓴다. */
    public boolean fixedWorld() { return fixedWorld; }

    public int live() { return live.get(); }

    /** 지금 새 사람이 들어올 자리가 없는가. 거절은 이 값만 보고 바로 끝낸다. */
    public boolean full() { return live.get() >= max; }

    /** 이 사람이 이미 자리를 쥐고 있는가(재접속은 막지 않는다). */
    public boolean holds(String player) { return player != null && holders.contains(player); }

    /** 이 사람에게 자리를 준다. 이미 자리를 쥔 사람이면 그대로 참이다. */
    public boolean acquire(String player) {
        if (player == null) {
            return !full();
        }
        if (holders.contains(player)) {
            return true;
        }
        while (true) {
            int now = live.get();
            if (now >= max) {
                return false;
            }
            if (live.compareAndSet(now, now + 1)) {
                holders.add(player);
                return true;
            }
        }
    }

    /** 사람이 나가면 자리를 돌려준다. 자리를 쥐지 않았던 사람은 아무 일도 하지 않는다. */
    public void release(String player) {
        if (player != null && holders.remove(player)) {
            live.updateAndGet(value -> value > 0 ? value - 1 : 0);
        }
    }
}
