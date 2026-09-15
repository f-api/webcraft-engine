package com.gameexpert.engine;

import java.util.List;
import java.util.SplittableRandom;
import java.util.function.IntBinaryOperator;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

import com.gameexpert.ws.GameTransport;
import com.gameexpert.ws.dto.WsMessages.LightningEvent;
import com.gameexpert.ws.dto.WsMessages.WeatherState;

/** 서버가 월드별 날씨 전이와 번개 위치를 한 번만 결정하는 작은 상태 머신. */
final class WeatherSystem {

    static final long CORRECTION_INTERVAL_TICKS = 100;
    /**
     * 바닐라의 낙뢰는 로드된 청크 안에서만 후보를 고른다. WebCraft는 그 로드 범위를 무작위로 고른
     * 플레이어 기준 ±STRIKE_RADIUS 블록으로 근사하며, 이 반경은 절대 좌표가 아니라 상대 오프셋이다.
     */
    static final int STRIKE_RADIUS = 128;
    private static final long CLEAR_MIN_TICKS = 6_000;
    private static final long CLEAR_MAX_TICKS_EXCLUSIVE = 36_001;
    private static final long RAIN_MIN_TICKS = 3_000;
    private static final long RAIN_MAX_TICKS_EXCLUSIVE = 6_001;
    private static final long THUNDER_MIN_TICKS = 900;
    private static final long THUNDER_MAX_TICKS_EXCLUSIVE = 3_901;

    private final Long worldId;
    private final GameTransport broadcaster;
    private final SplittableRandom random;
    /**
     * 낙뢰가 꽂힐 컬럼의 지면 높이(y). 바닐라는 살아 있는 월드의 MOTION_BLOCKING 하이트맵을 쓰므로
     * 나무·눈층·물·플레이어 건축물 위에 친다. 밀도 표면(생성 시점 지형)을 쓰면 30블록 탑 위의
     * 벼락이 지면에서 렌더된다. 정적판 {@code StandaloneWeatherAuthority} 도 같은 자리에 같은 함수를
     * 주입받으므로, 여기와 저기가 같은 하이트맵을 가리키는지가 파리티의 전부다.
     */
    private final IntBinaryOperator strikeHeight;
    /**
     * 후보 타격점 보정. 기본은 항등이며 구리 트랙의 피뢰침 유인이 여기에 붙는다. 훅은 RNG 소비가
     * 모두 끝난 뒤에 호출되므로 훅을 붙여도 양 권위의 난수열은 갈라지지 않는다.
     */
    private LightningStrike.Hook strikeHook = LightningStrike.Hook.NONE;
    /** 확정 타격점의 게임플레이 효과(피해·발화·변신). 연출만 필요한 호출부는 NONE 을 준다. */
    private final LightningStrike.Effects strikeEffects;

    private String kind = "clear";
    private long changeAtWorldTick;
    private long nextLightningTick = Long.MAX_VALUE;
    private final LongSupplier eventIds;
    /** 낙뢰 후보({x, z}) 공급자. 호출부가 정본 순서(닉네임 정렬)를 보장해야 결정론이 성립한다. */
    private final Supplier<List<double[]>> strikeTargets;
    private volatile WeatherState snapshot;

    WeatherSystem(Long worldId, int seed, GameTransport broadcaster, LongSupplier eventIds,
            Supplier<List<double[]>> strikeTargets, IntBinaryOperator strikeHeight) {
        this(worldId, seed, broadcaster, eventIds, strikeTargets, strikeHeight,
                LightningStrike.Effects.NONE);
    }

    WeatherSystem(Long worldId, int seed, GameTransport broadcaster, LongSupplier eventIds,
            Supplier<List<double[]>> strikeTargets, IntBinaryOperator strikeHeight,
            LightningStrike.Effects strikeEffects) {
        this.strikeEffects = strikeEffects == null ? LightningStrike.Effects.NONE : strikeEffects;
        this.worldId = worldId;
        this.broadcaster = broadcaster;
        this.eventIds = eventIds;
        this.strikeTargets = strikeTargets;
        this.strikeHeight = strikeHeight;
        this.random = new SplittableRandom(mixSeed(worldId, seed));
        this.changeAtWorldTick = clearEndAfter(0);
        publishSnapshot();
    }

    /**
     * 타격점 보정 훅을 건다. 구리 트랙이 피뢰침 유인을 붙이는 지점이며, 게임플레이 효과는
     * 훅이 돌려준 <b>최종</b> 좌표에 적용된다.
     */
    void setStrikeHook(LightningStrike.Hook hook) {
        this.strikeHook = hook == null ? LightningStrike.Hook.NONE : hook;
    }

    WeatherState snapshot() {
        return snapshot;
    }

    boolean isRaining() {
        return !"clear".equals(kind);
    }

    void tick(long worldTick) {
        boolean transitioned = false;
        if (worldTick >= changeAtWorldTick) {
            transition(worldTick);
            transitioned = true;
        }
        // 후보가 없는 틱(전원 퇴장)은 RNG를 소비하지 않고 재예약도 하지 않는다. 다음 후보가 생기는
        // 즉시 같은 순번의 낙뢰가 이어져 양 권위의 난수열이 갈라지지 않는다.
        if ("thunder".equals(kind) && worldTick >= nextLightningTick && strike()) {
            nextLightningTick = worldTick + random.nextLong(80, 241);
        }
        if (transitioned || worldTick % CORRECTION_INTERVAL_TICKS == 0) {
            publishSnapshot();
            broadcast(snapshot);
        }
    }

    /**
     * [QA5-1] QA 시딩({@code qaSeed command:"weather"})이 부르는 강제 전이.
     *
     * <p>바닐라 {@code /weather <clear|rain|thunder>} 는 지속 인자를 생략하면 그 종류의 정상
     * 분포에서 지속을 다시 뽑아 즉시 전이한다. 여기도 같다 — 종류만 지정으로 대체하고, 지속과
     * 낙뢰 예약은 {@link #transition(long)} 과 <b>같은 순서·같은 분포</b>로 다시 뽑는다.
     * 그래서 강제 이후의 월드도 평소 굴러온 월드와 구분되지 않는다(qa-seeding 의 계약).
     *
     * <p>다만 이 호출 자체는 난수를 소비하므로 강제한 월드와 강제하지 않은 월드의 난수열은
     * 그 지점부터 갈라진다. QA 서버에서만 열리는 경로라 프로덕션 결정론에는 영향이 없다.
     */
    void qaForceWeather(String forced, long worldTick) {
        if (!"clear".equals(forced) && !"rain".equals(forced) && !"thunder".equals(forced)) {
            throw new IllegalArgumentException("알 수 없는 QA 날씨: " + forced);
        }
        kind = forced;
        if ("clear".equals(kind)) {
            changeAtWorldTick = clearEndAfter(worldTick);
            nextLightningTick = Long.MAX_VALUE;
        } else {
            changeAtWorldTick = worldTick + ("thunder".equals(kind)
                    ? random.nextLong(THUNDER_MIN_TICKS, THUNDER_MAX_TICKS_EXCLUSIVE)
                    : random.nextLong(RAIN_MIN_TICKS, RAIN_MAX_TICKS_EXCLUSIVE));
            nextLightningTick = "thunder".equals(kind)
                    ? worldTick + random.nextLong(40, 161) : Long.MAX_VALUE;
        }
        publishSnapshot();
        broadcast(snapshot);
    }

    private void transition(long worldTick) {
        if ("clear".equals(kind)) {
            kind = random.nextInt(3) == 0 ? "thunder" : "rain";
            changeAtWorldTick = worldTick + ("thunder".equals(kind)
                    ? random.nextLong(THUNDER_MIN_TICKS, THUNDER_MAX_TICKS_EXCLUSIVE)
                    : random.nextLong(RAIN_MIN_TICKS, RAIN_MAX_TICKS_EXCLUSIVE));
            nextLightningTick = "thunder".equals(kind)
                    ? worldTick + random.nextLong(40, 161) : Long.MAX_VALUE;
        } else {
            kind = "clear";
            changeAtWorldTick = clearEndAfter(worldTick);
            nextLightningTick = Long.MAX_VALUE;
        }
    }

    private long clearEndAfter(long worldTick) {
        return worldTick + random.nextLong(CLEAR_MIN_TICKS, CLEAR_MAX_TICKS_EXCLUSIVE);
    }

    /**
     * 무작위 후보 하나를 고르고 그 주변 상대 좌표에 친다. 후보가 없으면 아무것도 하지 않는다.
     *
     * <p>파이프라인은 <b>후보 결정 → 훅 보정 → 효과 적용 → 방송</b> 순서다. 난수는 후보 결정에서만
     * 소비하므로 훅/효과를 붙여도 난수열은 그대로다. 방송과 효과는 같은(=보정된) 좌표를 쓴다.
     */
    private boolean strike() {
        List<double[]> targets = strikeTargets.get();
        if (targets.isEmpty()) return false;
        double[] target = targets.get(random.nextInt(targets.size()));
        int x = (int) Math.floor(target[0]) + random.nextInt(-STRIKE_RADIUS, STRIKE_RADIUS + 1);
        int z = (int) Math.floor(target[1]) + random.nextInt(-STRIKE_RADIUS, STRIKE_RADIUS + 1);
        LightningStrike candidate =
                LightningStrike.atBlock(x, strikeHeight.applyAsInt(x, z) + 1, z);
        LightningStrike strike = strikeHook.redirect(candidate);
        if (strike == null) strike = candidate;
        strikeEffects.apply(strike);
        broadcast(new LightningEvent(eventIds.getAsLong(), strike.x(), strike.y(), strike.z()));
        return true;
    }

    private void publishSnapshot() {
        snapshot = new WeatherState(kind);
    }

    private void broadcast(Object message) {
        if (broadcaster != null) broadcaster.broadcast(worldId, message);
    }

    private static long mixSeed(Long worldId, int seed) {
        long id = worldId == null ? 0 : worldId;
        return ((long) seed << 32) ^ id ^ 0x574541544845524cL;
    }
}
