package com.gameexpert.mob.dto;

import com.gameexpert.engine.mob.villager.VillagerTradeState;
import java.util.Arrays;
import java.util.Objects;

/**
 * 주민 한 명의 거래 상태 durable 행이다. 직업 lane({@link VillagerJobClaimSnapshot})이 "무슨
 * 직업인가"를 소유하는 것과 달리 이 lane 은 <b>거래 진행도</b>(직업 레벨·누적 거래 XP·오퍼별
 * 사용 횟수·수요·재입고 시각)를 소유한다. 두 lane 은 저장소도 키도 분리돼 한쪽 복구 실패가
 * 다른 쪽을 건드리지 않는다.
 *
 * <p>오퍼는 값이 아니라 (레벨, 풀 인덱스) 로 저장한다 —
 * {@link VillagerTradeState.Snapshot} 참조. 오퍼 표가 append-only 로 늘어도 과거 주민의 오퍼가
 * 그대로 복원된다.
 *
 * <p><b>스키마 버전</b>: {@link #SCHEMA_VERSION}. 버전이 올라가면
 * {@code com.gameexpert.mob.entity.WorldVillagerTradeState#toSnapshot()} 이 해석할 수 없는 행을
 * null 로 돌려 복원에서 조용히 빠지고, 그 주민은 직업 lane 이 준 직업으로 오퍼를 새로 뽑는다
 * (= 오늘의 동작). 즉 기본값 폴백이며 마이그레이션 스크립트를 요구하지 않는다.
 */
public final class VillagerTradeSnapshot {

    /** 이 빌드가 쓰는 거래 lane 행 포맷. 필드 의미가 바뀔 때만 올린다. */
    public static final int SCHEMA_VERSION = 1;

    private final long mobId;
    private final VillagerTradeState.Snapshot state;
    private final int schemaVersion;

    public VillagerTradeSnapshot(long mobId, VillagerTradeState.Snapshot state) {
        this(mobId, state, SCHEMA_VERSION);
    }

    public VillagerTradeSnapshot(long mobId, VillagerTradeState.Snapshot state, int schemaVersion) {
        this.state = Objects.requireNonNull(state, "state");
        if (mobId <= 0) {
            throw new IllegalArgumentException("mobId must be positive");
        }
        if (schemaVersion <= 0) {
            throw new IllegalArgumentException("schemaVersion must be positive");
        }
        if (state.profession() == null || state.profession().isBlank()) {
            throw new IllegalArgumentException("profession must not be blank");
        }
        this.mobId = mobId;
        this.schemaVersion = schemaVersion;
    }

    public long mobId() { return mobId; }

    public VillagerTradeState.Snapshot state() { return state; }

    public int schemaVersion() { return schemaVersion; }

    /** 행 identity: 주민 한 명당 한 행. */
    public String identityKey() { return "villager-trade:" + mobId; }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof VillagerTradeSnapshot row)) return false;
        VillagerTradeState.Snapshot mine = state;
        VillagerTradeState.Snapshot theirs = row.state;
        return mobId == row.mobId && schemaVersion == row.schemaVersion
                && mine.profession().equals(theirs.profession())
                && mine.level() == theirs.level()
                && mine.totalXp() == theirs.totalXp()
                && mine.lastRestockGameTime() == theirs.lastRestockGameTime()
                && mine.lastRestockCheckDayTime() == theirs.lastRestockCheckDayTime()
                && mine.restocksToday() == theirs.restocksToday()
                && Arrays.equals(mine.offerLevels(), theirs.offerLevels())
                && Arrays.equals(mine.offerPoolIndices(), theirs.offerPoolIndices())
                && Arrays.equals(mine.uses(), theirs.uses())
                && Arrays.equals(mine.demand(), theirs.demand())
                && Arrays.equals(mine.variantSeeds(), theirs.variantSeeds());
    }

    @Override
    public int hashCode() {
        return Objects.hash(mobId, schemaVersion, state.profession(), state.level(),
                state.totalXp(), state.lastRestockGameTime(), state.lastRestockCheckDayTime(),
                state.restocksToday(), Arrays.hashCode(state.offerLevels()),
                Arrays.hashCode(state.offerPoolIndices()), Arrays.hashCode(state.uses()),
                Arrays.hashCode(state.demand()), Arrays.hashCode(state.variantSeeds()));
    }

    @Override
    public String toString() {
        return "VillagerTradeSnapshot[mob=" + mobId + " " + state.profession()
                + " lv" + state.level() + " xp" + state.totalXp()
                + " uses=" + Arrays.toString(state.uses()) + ']';
    }
}
