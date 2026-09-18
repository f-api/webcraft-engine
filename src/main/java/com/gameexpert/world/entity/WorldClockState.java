package com.gameexpert.world.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 월드 시계(절대 일수·하루 시각·게임 틱)와 떠돌이 상인 시도 창을 담는 <b>전용</b> 행입니다.
 *
 * <p>이 값들은 원래 {@code worlds} 행에 함께 있었습니다. 그런데 시계 체크포인트는 거의 매 틱
 * 그 행을 UPDATE 하며 잠금을 쥐고, 같은 행을 최종운반체 정산이 {@code SELECT ... FOR UPDATE}
 * 로 잠그려 하기 때문에 둘이 상시 경합했습니다. 정산은 틱 스레드에서 동기로 돌아, 무대기
 * 잠금을 쓰기 전에는 innodb_lock_wait_timeout(50초) 동안 월드 전체가 멈췄고, 무대기로 바꾼
 * 뒤에도 정산이 계속 밀려났습니다.
 *
 * <p>그래서 자주 쓰는 시계 값만 이 행으로 분리합니다. 시계 쓰기와 정산 잠금이 서로 다른 행을
 * 만지므로 둘은 이제 원천적으로 경합하지 않습니다. 월드 식별·시드·베이스라인처럼 거의 바뀌지
 * 않는 값은 {@code worlds} 에 그대로 둡니다.
 *
 * <p>PK 는 {@code worlds.id} 를 그대로 씁니다(공유 PK). 행이 아직 없는 구형 월드는 최초 저장
 * 때 만들어지고, 그 전까지는 {@code worlds} 의 옛 컬럼을 읽어 이어받습니다.
 */
@Getter
@Entity
@org.hibernate.annotations.DynamicUpdate
@Table(name = "world_clock_states")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorldClockState {

    /** {@code worlds.id} 와 같은 값. 생성 전략 없이 호출부가 지정합니다. */
    @Id
    @Column(name = "world_id", nullable = false)
    private Long worldId;

    @Column(nullable = false)
    private long dayCount;

    @Column(nullable = false)
    private long worldTime;

    @Column(nullable = false)
    private long gameTimeMcTicks;

    @Column(nullable = false)
    private long traderNextAttemptTick;

    @Column(nullable = false)
    private int traderChancePercent;

    @Column(columnDefinition = "LONGTEXT")
    private String redstoneState;

    public void updateRedstoneState(String redstoneState) { this.redstoneState = redstoneState; }

    public WorldClockState(Long worldId, long dayCount, long worldTime, long gameTimeMcTicks,
            long traderNextAttemptTick, int traderChancePercent) {
        this.worldId = java.util.Objects.requireNonNull(worldId, "worldId");
        apply(dayCount, worldTime, gameTimeMcTicks, traderNextAttemptTick, traderChancePercent);
    }

    /** 검증 규칙은 옛 {@code World.updateClock}/{@code updateTraderWindow} 와 같게 둡니다. */
    public void apply(long dayCount, long worldTime, long gameTimeMcTicks,
            long traderNextAttemptTick, int traderChancePercent) {
        if (dayCount < 0 || worldTime < 0 || worldTime >= 12_000 || gameTimeMcTicks < 0) {
            throw new IllegalArgumentException("world clock values are out of range");
        }
        if (traderChancePercent < 0 || traderChancePercent > 100) {
            throw new IllegalArgumentException("trader chance percent must be between 0 and 100");
        }
        this.dayCount = dayCount;
        this.worldTime = worldTime;
        this.gameTimeMcTicks = gameTimeMcTicks;
        this.traderNextAttemptTick = traderNextAttemptTick;
        this.traderChancePercent = traderChancePercent;
    }

    public void updateDayCount(long dayCount) {
        if (dayCount < 0) throw new IllegalArgumentException("dayCount must be non-negative");
        this.dayCount = dayCount;
    }

    public void updateTraderWindow(long nextAttemptTick, int chancePercent) {
        if (chancePercent < 0 || chancePercent > 100) {
            throw new IllegalArgumentException("trader chance percent must be between 0 and 100");
        }
        this.traderNextAttemptTick = nextAttemptTick;
        this.traderChancePercent = chancePercent;
    }
}
