package com.gameexpert.world.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gameexpert.common.NotFoundException;
import com.gameexpert.api.persistence.WorldAccess;
import com.gameexpert.world.entity.WorldClockState;
import com.gameexpert.world.repository.WorldClockStateRepository;
import com.gameexpert.api.persistence.WorldStore;

import lombok.RequiredArgsConstructor;

/**
 * 월드 절대 일수와 떠돌이 상인 시도 창을 읽고 쓰는 트랜잭션 경계입니다.
 *
 * <p>쓰기는 {@code world_clock_states} 전용 행으로 갑니다. 예전에는 {@code worlds} 행을 직접
 * 갱신했는데, 그 행은 최종운반체 정산이 틱 스레드에서 {@code FOR UPDATE} 로 잠그는 행과 같아
 * 거의 매 틱 경합했습니다({@link WorldClockState} 의 설명 참고). 읽기는 전용 행이 아직 없는
 * 구형 월드를 위해 {@code worlds} 의 옛 컬럼으로 한 번 되돌아갑니다.
 */
@Service
@RequiredArgsConstructor
public class WorldTimePersistenceService {

    public record TraderWindow(long nextAttemptTick, int chancePercent) { }
    public record ClockState(
            long dayCount, long worldTime, long gameTimeMcTicks, TraderWindow traderWindow) { }

    private final WorldStore worldRepository;
    private final WorldClockStateRepository clockStateRepository;

    @Transactional(readOnly = true)
    public long loadDayCount(Long worldId) {
        WorldClockState state = clockStateRepository.findById(worldId).orElse(null);
        return state != null ? state.getDayCount() : findWorld(worldId).getDayCount();
    }

    @Transactional(readOnly = true)
    public long loadWorldTime(Long worldId) {
        WorldClockState state = clockStateRepository.findById(worldId).orElse(null);
        return state != null ? state.getWorldTime() : findWorld(worldId).getWorldTime();
    }

    @Transactional(readOnly = true)
    public long loadGameTimeMcTicks(Long worldId) {
        WorldClockState state = clockStateRepository.findById(worldId).orElse(null);
        return state != null ? state.getGameTimeMcTicks() : findWorld(worldId).getGameTimeMcTicks();
    }

    @Transactional(readOnly = true)
    public TraderWindow loadTraderWindow(Long worldId) {
        WorldClockState state = clockStateRepository.findById(worldId).orElse(null);
        if (state != null) {
            return new TraderWindow(state.getTraderNextAttemptTick(), state.getTraderChancePercent());
        }
        WorldAccess world = findWorld(worldId);
        if (world.getTraderNextAttemptTick() == null || world.getTraderChancePercent() == null) {
            return null;
        }
        return new TraderWindow(world.getTraderNextAttemptTick(), world.getTraderChancePercent());
    }

    @Transactional
    public void saveDayCount(Long worldId, long dayCount) {
        mutableState(worldId).updateDayCount(dayCount);
    }

    @Transactional
    public void saveTraderWindow(Long worldId, TraderWindow window) {
        if (window == null) throw new IllegalArgumentException("trader window is required");
        mutableState(worldId).updateTraderWindow(window.nextAttemptTick(), window.chancePercent());
    }

    /** The day and trader window share one row and must cross a restart boundary atomically. */
    @Transactional
    public void saveClockState(Long worldId, ClockState state) {
        if (state == null || state.traderWindow() == null) {
            throw new IllegalArgumentException("clock state and trader window are required");
        }
        TraderWindow window = state.traderWindow();
        mutableState(worldId).apply(state.dayCount(), state.worldTime(), state.gameTimeMcTicks(),
                window.nextAttemptTick(), window.chancePercent());
    }

    @Transactional(readOnly = true)
    public String loadRedstoneState(Long worldId) {
        return clockStateRepository.findById(worldId).map(WorldClockState::getRedstoneState).orElse(null);
    }

    /** 시계와 레드스톤 예약은 같은 체크포인트 행에서 원자적으로 갱신한다. */
    @Transactional
    public void saveClockState(Long worldId, ClockState state, String redstoneState) {
        saveClockState(worldId, state);
        mutableState(worldId).updateRedstoneState(redstoneState);
    }

    /**
     * 전용 시계 행을 더티 체킹 대상으로 돌려줍니다. 아직 없으면 옛 {@code worlds} 컬럼을 시드로
     * 삼아 만듭니다. 월드 존재 확인은 이 최초 생성 때만 하므로, 이후 체크포인트는 {@code worlds}
     * 행을 아예 읽지 않습니다 — 그 행 잠금과 겹치지 않는 것이 이 분리의 목적입니다.
     */
    private WorldClockState mutableState(Long worldId) {
        return clockStateRepository.findById(worldId).orElseGet(() -> {
            WorldAccess world = findWorld(worldId);
            long nextAttemptTick = world.getTraderNextAttemptTick() == null
                    ? 0L : world.getTraderNextAttemptTick();
            int chancePercent = world.getTraderChancePercent() == null
                    ? 0 : world.getTraderChancePercent();
            return clockStateRepository.save(new WorldClockState(worldId, world.getDayCount(),
                    world.getWorldTime(), world.getGameTimeMcTicks(),
                    nextAttemptTick, chancePercent));
        });
    }

    private WorldAccess findWorld(Long worldId) {
        return worldRepository.findById(worldId)
                .orElseThrow(() -> new NotFoundException("WORLD_NOT_FOUND"));
    }
}
