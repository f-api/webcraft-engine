package com.gameexpert.engine.trial;

/**
 * [TRIAL] 트라이얼 스포너 소환 풀. <b>표 하나</b>가 정본이고 종 등록 웨이브가 늘어날 때
 * 행만 추가한다 — 분기를 늘리지 않는다.
 *
 * <p>이 웨이브의 계약은 "무엇을 소환할지" 까지다. 역할 서수 → 실제 몹 종 사상은 다음
 * 웨이브의 배선이 소유한다(이 웨이브에서 {@code MobType}·{@code MobSpawner} 는 다른
 * 트랙이 잡고 있어 건드릴 수 없다). 그래서 여기서는 종 enum 을 참조하지 않고 역할 서수만
 * 확정한다 — 서수는 append-only 이며 바꾸면 과거 시드의 웨이브 구성이 달라진다.
 *
 * <p>근거: [B] 위키 «Trial Spawner» Java 판 — 바닐라 트라이얼 스포너는 좀비·해골·거미·
 * 동굴거미·슬라임·좀벌레·독거미·브리즈 중 스포너마다 하나의 종을 골라 반복 소환한다.
 * 이 저장소는 좀벌레·독거미 대신 등록된 허스크·스트레이를 쓰고 브리즈까지 양 권위에
 * 연결했다. 아래 표의 {@link Role#registered} 가 false 인 행은 선택에서 제외된다.
 */
public final class TrialSpawnerPool {

    private TrialSpawnerPool() {}

    /**
     * 소환 역할. 서수는 append-only 다. {@code registered} 는 "이 저장소에 그 종이 이미
     * 등록됐는가" 이고, false 인 행은 {@link #select} 가 건너뛴다 — 표를 지우지 않고
     * 비활성만 하기 때문에 종이 착지하는 순간 한 줄만 바꾸면 합류한다.
     */
    public enum Role {
        ZOMBIE(true),
        SKELETON(true),
        SPIDER(true),
        CAVE_SPIDER(true),
        HUSK(true),
        STRAY(true),
        SLIME(true),
        /**
         * 브리즈({@code MobType.BREEZE}, stableId 94). [WAVE-86-97] 종 등록 웨이브가 이
         * 한 줄을 true 로 바꿨다 — 바닐라에도 브리즈는 자연 스폰 항목이 없고 시련 소환기가
         * 유일한 출처이기 때문이다. 표의 <b>구성과 서수는 그대로</b>라 다른 역할의 서수가
         * 밀리지 않지만, 선택 가능한 역할 수가 7 → 8 로 늘어 기존 site 의 소환 종이 달라진다.
         * 정적판 사본은 {@code StandaloneTrialSpawner.ts} 의 같은 줄이다.
         */
        BREEZE(true);

        private final boolean registered;

        Role(boolean registered) {
            this.registered = registered;
        }

        public boolean registered() {
            return registered;
        }
    }

    private static final Role[] ALL = Role.values();

    /** 현재 선택 가능한 역할 수. 등록된 행만 센다. */
    public static int selectableCount() {
        int count = 0;
        for (Role role : ALL) {
            if (role.registered) count++;
        }
        return count;
    }

    /**
     * 이 스포너가 반복 소환할 역할 하나. 바닐라와 같이 스포너마다 종이 하나로 고정되고
     * (웨이브마다 바뀌지 않는다), 선택은 스포너 좌표에서 유도한 시드만 읽는다.
     *
     * @param spawnerSeed {@link TrialSpawnerContract#spawnerSeed} 가 좌표에서 만든 값
     */
    public static Role select(long spawnerSeed) {
        int selectable = selectableCount();
        if (selectable <= 0) {
            throw new IllegalStateException("trial spawner pool has no registered role");
        }
        int index = (int) Long.remainderUnsigned(spawnerSeed, selectable);
        for (Role role : ALL) {
            if (!role.registered) continue;
            if (index == 0) return role;
            index--;
        }
        throw new IllegalStateException("unreachable trial spawner role selection");
    }
}
