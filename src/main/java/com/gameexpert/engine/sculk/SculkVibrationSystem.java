package com.gameexpert.engine.sculk;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * [DEEP-DARK] {@link SculkVibrationRules} 를 실제 틱에 태우는 <b>사슬 배선</b>.
 *
 * <p>규칙 클래스는 상태가 없다 — 이 클래스가 그 규칙을 "진동 → 감지체 활성 → 비명체 →
 * 어둠 → 경고 단계 영속" 한 줄로 잇는다. 두 권위가 같은 사슬을 돌려야 하므로 정적판
 * {@code StandaloneSculkVibrationSystem.ts} 가 같은 순서·같은 분기의 손 사본을 유지한다.
 *
 * <p><b>원본 대비 무엇을 줄였는가</b>
 * <ul>
 *   <li>바닐라 {@code SculkSensorBlock} 은 {@code VibrationSystem.Listener} 로 진동을 받아
 *       레드스톤 신호 세기를 내고, {@code SculkShriekerBlockEntity#tryShriek} 는 그 신호나
 *       플레이어 접촉으로 깨어난다. 이 저장소에는 레드스톤이 없어 <b>감지체가 반경 안의
 *       비명체를 직접</b> 깨운다({@link SculkVibrationRules} 문서의 축소 계약).</li>
 *   <li>바닐라 {@code SculkShriekerBlockEntity#canRespond} 는 플레이어당 쿨다운
 *       ({@code TRY_SHRIEK_COOLDOWN = 200} 틱)과 {@code warning_level} 을 블록 엔티티에
 *       들고 있다. 이 저장소에는 블록 엔티티가 없으므로 경고 단계는 <b>블록 상태 바이트</b>에
 *       (화로 좌표 부속 선례와 같은 자리에) 싣고, 플레이어별 쿨다운만 이 시스템이 들고 있다.</li>
 *   <li>바닐라 {@code SculkShriekerBlockEntity#tryShriek} 는 비명 뒤
 *       {@code MobEffects.DARKNESS, 200, 0, false, false} 를 반경 40 안의 플레이어에게
 *       주고({@code shriek} → {@code applyDarkness}), 경고 4단계에서 워든을 소환한다.
 *       어둠 길이는 위키 본문의 12초(240 틱)를 {@link SculkVibrationRules#DARKNESS_MC_TICKS}
 *       에 이미 고정해 두었고 이 클래스는 그 한 값만 읽는다.</li>
 * </ul>
 *
 * <p>시간 단위는 전부 <b>MC 틱</b>이다. 권위 틱(10 TPS)을 MC 틱으로 접는 것은 호출자 몫이며
 * 기존 {@code StatusEffects.MC_TICKS_PER_SERVER_TICK} 규약과 같다.
 */
public final class SculkVibrationSystem {

    /** 감지체 클릭 사운드의 월드 사운드 kind. */
    public static final String SOUND_SENSOR_CLICK = "sculk_sensor_click";
    /** 비명체 비명 사운드의 월드 사운드 kind. */
    public static final String SOUND_SHRIEK = "sculk_shriek";

    /**
     * 진동 하나를 들을 수 있는 감지체를 찾을 때 훑는 정육면체의 한 변. 반경 8 의 구를
     * 포함하는 최소 상자다. 실제 판정은 {@link SculkVibrationRules#withinListenRange} 의
     * 구 반경이므로 상자는 후보 열거에만 쓴다.
     */
    private static final int SCAN_SPAN = SculkVibrationRules.LISTEN_RADIUS * 2 + 1;

    /** 섹션 인덱스 한 변(청크 섹션과 같은 16). */
    private static final int SECTION = 16;

    /**
     * 섹션 부속 인덱스의 상한. 한 플레이어의 9×9 활성 청크 전체 높이는 1,944개
     * 섹션이므로, 그보다 작은 상한은 청크 활성화 중 방금 만든 인덱스를 다시 버리고 첫
     * 발걸음에서 16³ 스캔을 재발시킨다. 4,096은 두 활성 영역을 덮으면서도 빈 섹션은 공유
     * EMPTY 배열만 가리켜 메모리 비용이 작다. 상한을 넘으면 가장 오래 안 쓴 섹션부터 버린다.
     */
    private static final int SECTION_CACHE_LIMIT = 4_096;

    /** 이 저장소가 실제 권위 사실로 가진 진동 하나. */
    public record Vibration(SculkVibrationRules.Event event, int x, int y, int z,
            String sourceNickname) {}

    /** 어둠·쿨다운 판정에 필요한 플레이어 한 명의 최소 사실. */
    public record PlayerRef(String nickname, double x, double y, double z) {}

    /**
     * 이 시스템이 월드에 요구하는 전부. 좁게 유지해 두 권위와 테스트가 같은 사슬을
     * 서로 다른 월드 구현 위에서 돌린다.
     */
    public interface World {
        /** 동일한 권위 진동을 이동형 청취자에게도 전달한다. */
        default void notifyMobileListeners(Vibration vibration) {}

        /** 비상주·범위 밖이면 음수. */
        int blockAt(int x, int y, int z);

        /**
         * 상주 섹션을 한 번에 읽을 수 있는 권위의 빠른 경로. 테스트/간이 월드는
         * {@code false}를 돌려 기존 blockAt 순회를 그대로 사용한다.
         */
        default boolean scanSection(int sectionX, int sectionY, int sectionZ,
                FixtureVisitor visitor) {
            return false;
        }

        int stateAt(int x, int y, int z, int blockType);

        /** 상태를 쓰고 클라 렌더까지 밀어낸다(감지체 반짝임 비트가 이 경로로 나간다). */
        void setState(int x, int y, int z, int blockType, int state);

        /** 지금 이 월드에 있는 플레이어 전부. */
        List<PlayerRef> players();

        void grantDarkness(String nickname, SculkVibrationRules.DarknessGrant grant);

        void playSound(String kind, int x, int y, int z, int blockType);
    }

    @FunctionalInterface
    public interface FixtureVisitor {
        void visit(int x, int y, int z, int blockType);
    }

    private final World world;
    private final SculkVibrationRules.WardenSummonSink wardenSink;

    /** 이번 틱에 들어온 진동. 틱 끝에서 비운다. */
    private final List<Vibration> pending = new ArrayList<>();

    /** 활성 감지체 → 활성화된 MC 틱. */
    private final Map<Long, Long> activeSensors = new LinkedHashMap<>();

    /** 비명체 좌표 → 마지막 비명 MC 틱(경고 감쇠 기준). */
    private final Map<Long, Long> lastShriek = new HashMap<>();

    /**
     * 비명체 좌표 → (닉네임 → 그 플레이어가 마지막으로 깨운 MC 틱). 바닐라
     * {@code SculkShriekerBlockEntity} 가 블록 엔티티에 들고 있는 쿨다운과 같은 자리다.
     * 닉네임 키라 재접속해도 같은 행을 본다 — 세션 식별자를 쓰면 재접속이 쿨다운을 지운다.
     */
    private final Map<Long, Map<String, Long>> shriekCooldown = new HashMap<>();

    /** 섹션 → 그 안의 감지체·비명체 좌표. 청크 활성화·블록 변경이 정확히 무효화한다. */
    private final LinkedHashMap<Long, long[]> sectionFixtures =
            new LinkedHashMap<>(64, 0.75f, true);

    public SculkVibrationSystem(World world) {
        this(world, SculkVibrationRules.WardenSummonSink.NO_OP);
    }

    public SculkVibrationSystem(World world, SculkVibrationRules.WardenSummonSink wardenSink) {
        this.world = world;
        this.wardenSink = wardenSink;
    }

    /**
     * 권위가 관측한 진동 하나를 이번 틱 대기열에 넣는다. 새 프로토콜은 없다 — 호출자는
     * 이미 자기 권위로 처리하고 있는 이동·블록 편집·착탄에서 이것을 파생시킨다.
     */
    public void emit(SculkVibrationRules.Event event, int x, int y, int z, String sourceNickname) {
        if (event == null) return;
        pending.add(new Vibration(event, x, y, z, sourceNickname));
    }

    /**
     * 이 좌표의 블록이 바뀌었다. 이미 인덱싱된 섹션이면 그 한 좌표만 증분 갱신한다.
     * 감지체·비명체가 부서지면 그 자리의 활성·경고 회계도 함께 잊는다.
     */
    public void invalidate(int x, int y, int z) {
        int block = world.blockAt(x, y, z);
        long key = posKey(x, y, z);
        long sectionKey = sectionKey(x, y, z);
        long[] cachedFixtures = sectionFixtures.get(sectionKey);
        if (cachedFixtures != null) {
            boolean fixture = SculkVibrationRules.isSensor(block)
                    || SculkVibrationRules.isShrieker(block);
            int found = -1;
            for (int index = 0; index < cachedFixtures.length; index++) {
                if (cachedFixtures[index] == key) {
                    found = index;
                    break;
                }
            }
            if (fixture && found < 0) {
                long[] updated = new long[cachedFixtures.length + 1];
                System.arraycopy(cachedFixtures, 0, updated, 0, cachedFixtures.length);
                updated[cachedFixtures.length] = key;
                sectionFixtures.put(sectionKey, updated);
            } else if (!fixture && found >= 0) {
                if (cachedFixtures.length == 1) {
                    sectionFixtures.put(sectionKey, EMPTY);
                } else {
                    long[] updated = new long[cachedFixtures.length - 1];
                    System.arraycopy(cachedFixtures, 0, updated, 0, found);
                    System.arraycopy(cachedFixtures, found + 1, updated, found,
                            cachedFixtures.length - found - 1);
                    sectionFixtures.put(sectionKey, updated);
                }
            }
        }
        if (!SculkVibrationRules.isSensor(block)) activeSensors.remove(key);
        if (!SculkVibrationRules.isShrieker(block)) {
            lastShriek.remove(key);
            shriekCooldown.remove(key);
        }
    }

    /**
     * 한 청크가 새로 상주했다. 비상주였을 때 만든 빈/부분 부속 인덱스를 버리고 청크의 모든
     * 섹션을 미리 채운다. 섹션의 수평 크기가 청크와 같은 16이므로 다른 청크의 캐시는
     * 건드리지 않는다.
     */
    public void onChunkActivated(int chunkX, int chunkZ) {
        int minSectionY = Math.floorDiv(com.gameexpert.terrain.Blocks.MIN_Y, SECTION);
        int maxSectionY = Math.floorDiv(com.gameexpert.terrain.Blocks.MAX_Y, SECTION);
        for (int sectionY = minSectionY; sectionY <= maxSectionY; sectionY++) {
            sectionFixtures.remove(packSection(chunkX, sectionY, chunkZ));
            // 발걸음이 들어온 틱에 16³ 첫 스캔을 몰아넣지 않는다. production World는
            // TerrainAccessor의 연속 배열 경로를 사용하므로 청크 활성화 비용도 작고 일정하다.
            sectionFixtures(chunkX, sectionY, chunkZ);
        }
    }

    /** 대기 중인 진동 수(진단·테스트용). */
    public int pendingVibrations() {
        return pending.size();
    }

    /** 지금 활성인 감지체 수(진단·테스트용). */
    public int activeSensorCount() {
        return activeSensors.size();
    }

    /**
     * 사슬 한 틱. 순서는 두 권위가 같아야 한다:
     * ① 식은 감지체 끄기 → ② 진동 → 감지체 활성 → ③ 활성 감지체 → 비명체 → 어둠·소환.
     *
     * @param mcTick 월드가 시작된 뒤 누적된 MC 틱
     */
    public void tick(long mcTick) {
        coolSensors(mcTick);
        if (pending.isEmpty()) return;
        List<Vibration> batch = List.copyOf(pending);
        pending.clear();
        // 한 틱에 여러 진동이 같은 감지체를 깨워도 활성화는 한 번이다. 바닐라도 활성 중인
        // 감지체는 새 진동을 받지 않는다(VibrationSystem 의 listener 는 쿨다운 중이다).
        List<Long> awoken = new ArrayList<>();
        for (Vibration vibration : batch) {
            world.notifyMobileListeners(vibration);
            for (long fixture : fixturesNear(vibration.x(), vibration.y(), vibration.z())) {
                int fx = unpackX(fixture);
                int fy = unpackY(fixture);
                int fz = unpackZ(fixture);
                if (!SculkVibrationRules.isSensor(world.blockAt(fx, fy, fz))) continue;
                if (!SculkVibrationRules.withinListenRange(
                        fx - vibration.x(), fy - vibration.y(), fz - vibration.z())) {
                    continue;
                }
                if (activeSensors.containsKey(fixture)) continue;
                activateSensor(fx, fy, fz, mcTick);
                awoken.add(fixture);
            }
        }
        for (long sensor : awoken) {
            wakeShriekers(unpackX(sensor), unpackY(sensor), unpackZ(sensor), mcTick);
        }
    }

    private void coolSensors(long mcTick) {
        if (activeSensors.isEmpty()) return;
        var iterator = activeSensors.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Long, Long> entry = iterator.next();
            if (!SculkVibrationRules.sensorCooledDown(mcTick - entry.getValue())) continue;
            long key = entry.getKey();
            int x = unpackX(key);
            int y = unpackY(key);
            int z = unpackZ(key);
            iterator.remove();
            int block = world.blockAt(x, y, z);
            if (!SculkVibrationRules.isSensor(block)) continue;
            world.setState(x, y, z, block, SculkVibrationRules.sensorState(false));
        }
    }

    private void activateSensor(int x, int y, int z, long mcTick) {
        int block = world.blockAt(x, y, z);
        activeSensors.put(posKey(x, y, z), mcTick);
        world.setState(x, y, z, block, SculkVibrationRules.sensorState(true));
        world.playSound(SOUND_SENSOR_CLICK, x, y, z, block);
    }

    /** 활성 감지체가 같은 반경 안의 비명체를 깨운다(레드스톤 경유 없음 — 축소 계약). */
    private void wakeShriekers(int sensorX, int sensorY, int sensorZ, long mcTick) {
        for (long fixture : fixturesNear(sensorX, sensorY, sensorZ)) {
            int x = unpackX(fixture);
            int y = unpackY(fixture);
            int z = unpackZ(fixture);
            int block = world.blockAt(x, y, z);
            if (!SculkVibrationRules.isShrieker(block)) continue;
            if (!SculkVibrationRules.withinListenRange(x - sensorX, y - sensorY, z - sensorZ)) {
                continue;
            }
            shriek(x, y, z, block, mcTick);
        }
    }

    /**
     * 비명 한 번. 바닐라 {@code SculkShriekerBlockEntity#tryShriek} 와 같은 순서다 —
     * 쿨다운을 통과한 플레이어가 하나라도 있어야 울리고, 울리면 경고 단계를 올린 뒤
     * 반경 안의 플레이어에게 어둠을 준다. 소환 판정은 <b>올리기 전</b> 단계로 묻는다.
     */
    private void shriek(int x, int y, int z, int block, long mcTick) {
        long key = posKey(x, y, z);
        List<PlayerRef> nearby = playersWithinDarkness(x, y, z);
        Map<String, Long> cooldown = shriekCooldown.computeIfAbsent(key, ignored -> new HashMap<>());
        List<PlayerRef> responders = null;
        for (PlayerRef player : nearby) {
            Long last = cooldown.get(player.nickname());
            if (last != null && !SculkVibrationRules.shriekCooledDown(mcTick - last)) continue;
            if (responders == null) responders = new ArrayList<>(nearby.size());
            responders.add(player);
        }
        if (responders == null) return;
        for (PlayerRef player : responders) {
            cooldown.put(player.nickname(), mcTick);
        }

        int storedState = world.stateAt(x, y, z, block);
        if (!SculkVibrationRules.canSummon(storedState)) {
            // [WORLD-GEOMETRY] 설치한 비명체(can_summon=false): 바닐라 canRespond 가 거짓이라 울기만 한다.
            world.playSound(SOUND_SHRIEK, x, y, z, block);
            return;
        }
        int stored = SculkVibrationRules.warningLevel(storedState);
        Long previousShriek = lastShriek.get(key);
        int current = previousShriek == null ? stored
                : SculkVibrationRules.decayedWarningLevel(stored, mcTick - previousShriek);
        boolean summons = SculkVibrationRules.summonsWarden(current);
        int next = SculkVibrationRules.nextWarningLevel(current);

        lastShriek.put(key, mcTick);
        world.setState(x, y, z, block, SculkVibrationRules.shriekerState(next, true));
        world.playSound(SOUND_SHRIEK, x, y, z, block);

        SculkVibrationRules.DarknessGrant grant = SculkVibrationRules.darknessGrant();
        for (PlayerRef player : nearby) {
            world.grantDarkness(player.nickname(), grant);
        }
        if (summons) wardenSink.summonWarden(x, y, z, aggroTarget(responders, x, y, z));
    }

    /**
     * 소환 어그로 대상. <b>명단 순서에 기대지 않는다</b> — 여기 Java 권위의
     * {@code world.players()} 는 {@code ConcurrentHashMap} 순회(해시 버킷) 순서고 정적판은
     * 접속(삽입) 순서라, "responders 중 첫 번째"는 같은 명단에도 두 권위에서 서로 다른 답을
     * 준다. 비명체 좌표에서 가장 가까운 responder(동률이면 닉네임 사전순)로 고르면 명단 순서와
     * 무관하게 같은 답이 나온다. 거리는 {@link #playersWithinDarkness} 와 같이 바닥값 좌표
     * 정수 차의 제곱합이고, 닉네임 비교는 {@code String#compareTo}(UTF-16 코드 단위) —
     * 정적판 사본의 {@code <} 비교와 같은 순서다.
     */
    private static String aggroTarget(List<PlayerRef> responders, int x, int y, int z) {
        PlayerRef best = null;
        long bestDistance = 0;
        for (PlayerRef player : responders) {
            long dx = (long) Math.floor(player.x()) - x;
            long dy = (long) Math.floor(player.y()) - y;
            long dz = (long) Math.floor(player.z()) - z;
            long distance = dx * dx + dy * dy + dz * dz;
            if (best == null || distance < bestDistance
                    || (distance == bestDistance
                            && player.nickname().compareTo(best.nickname()) < 0)) {
                best = player;
                bestDistance = distance;
            }
        }
        return best.nickname();
    }

    private List<PlayerRef> playersWithinDarkness(int x, int y, int z) {
        List<PlayerRef> near = null;
        for (PlayerRef player : world.players()) {
            int dx = (int) Math.floor(player.x()) - x;
            int dy = (int) Math.floor(player.y()) - y;
            int dz = (int) Math.floor(player.z()) - z;
            if (!SculkVibrationRules.withinDarknessRange(dx, dy, dz)) continue;
            if (near == null) near = new ArrayList<>();
            near.add(player);
        }
        return near == null ? List.of() : near;
    }

    // ── 부속 인덱스 ────────────────────────────────────────────────
    // 진동마다 17³ 블록을 다시 읽으면 발소리 하나가 5천 회 조회가 된다. 섹션(16³) 단위로
    // 감지체·비명체 좌표만 뽑아 캐시하고, 블록이 바뀐 섹션만 버린다. 비상주 결과도
    // 캐시하되 청크 활성화 콜백에서 그 청크의 섹션을 버린다. 경계에서 발걸음마다 같은
    // 16³ 셀을 다시 읽지 않으면서도 새로 적재된 감지체를 놓치지 않는다.

    private long[] fixturesNear(int x, int y, int z) {
        int minX = x - SculkVibrationRules.LISTEN_RADIUS;
        int minY = y - SculkVibrationRules.LISTEN_RADIUS;
        int minZ = z - SculkVibrationRules.LISTEN_RADIUS;
        int maxX = minX + SCAN_SPAN - 1;
        int maxY = minY + SCAN_SPAN - 1;
        int maxZ = minZ + SCAN_SPAN - 1;
        long[] found = null;
        int size = 0;
        for (int sx = Math.floorDiv(minX, SECTION); sx <= Math.floorDiv(maxX, SECTION); sx++) {
            for (int sy = Math.floorDiv(minY, SECTION); sy <= Math.floorDiv(maxY, SECTION); sy++) {
                for (int sz = Math.floorDiv(minZ, SECTION); sz <= Math.floorDiv(maxZ, SECTION); sz++) {
                    long[] section = sectionFixtures(sx, sy, sz);
                    if (section.length == 0) continue;
                    if (found == null) found = new long[section.length];
                    else if (size + section.length > found.length) {
                        long[] grown = new long[size + section.length];
                        System.arraycopy(found, 0, grown, 0, size);
                        found = grown;
                    }
                    System.arraycopy(section, 0, found, size, section.length);
                    size += section.length;
                }
            }
        }
        if (found == null) return EMPTY;
        if (size == found.length) return found;
        long[] trimmed = new long[size];
        System.arraycopy(found, 0, trimmed, 0, size);
        return trimmed;
    }

    private static final long[] EMPTY = new long[0];

    private static final class FixtureCollector implements FixtureVisitor {
        private long[] fixtures;
        private int size;

        @Override
        public void visit(int x, int y, int z, int blockType) {
            if (!SculkVibrationRules.isSensor(blockType)
                    && !SculkVibrationRules.isShrieker(blockType)) return;
            if (fixtures == null) fixtures = new long[8];
            else if (size == fixtures.length) {
                long[] grown = new long[size * 2];
                System.arraycopy(fixtures, 0, grown, 0, size);
                fixtures = grown;
            }
            fixtures[size++] = posKey(x, y, z);
        }

        private long[] result() {
            if (fixtures == null) return EMPTY;
            if (size == fixtures.length) return fixtures;
            long[] result = new long[size];
            System.arraycopy(fixtures, 0, result, 0, size);
            return result;
        }
    }

    private long[] sectionFixtures(int sectionX, int sectionY, int sectionZ) {
        long key = packSection(sectionX, sectionY, sectionZ);
        long[] cached = sectionFixtures.get(key);
        if (cached != null) return cached;
        FixtureCollector collector = new FixtureCollector();
        if (world.scanSection(sectionX, sectionY, sectionZ, collector)) {
            long[] result = collector.result();
            cacheSection(key, result);
            return result;
        }
        long[] scratch = null;
        int size = 0;
        int baseX = sectionX * SECTION;
        int baseY = sectionY * SECTION;
        int baseZ = sectionZ * SECTION;
        for (int dx = 0; dx < SECTION; dx++) {
            for (int dy = 0; dy < SECTION; dy++) {
                for (int dz = 0; dz < SECTION; dz++) {
                    int x = baseX + dx;
                    int y = baseY + dy;
                    int z = baseZ + dz;
                    int block = world.blockAt(x, y, z);
                    if (block < 0) continue;
                    if (!SculkVibrationRules.isSensor(block)
                            && !SculkVibrationRules.isShrieker(block)) {
                        continue;
                    }
                    if (scratch == null) scratch = new long[8];
                    else if (size == scratch.length) {
                        long[] grown = new long[size * 2];
                        System.arraycopy(scratch, 0, grown, 0, size);
                        scratch = grown;
                    }
                    scratch[size++] = posKey(x, y, z);
                }
            }
        }
        long[] result;
        if (scratch == null) result = EMPTY;
        else if (size == scratch.length) result = scratch;
        else {
            result = new long[size];
            System.arraycopy(scratch, 0, result, 0, size);
        }
        cacheSection(key, result);
        return result;
    }

    private void cacheSection(long key, long[] result) {
        sectionFixtures.put(key, result);
        if (sectionFixtures.size() > SECTION_CACHE_LIMIT) {
            var iterator = sectionFixtures.keySet().iterator();
            iterator.next();
            iterator.remove();
        }
    }

    private static long sectionKey(int x, int y, int z) {
        return packSection(Math.floorDiv(x, SECTION), Math.floorDiv(y, SECTION),
                Math.floorDiv(z, SECTION));
    }

    private static long packSection(int sectionX, int sectionY, int sectionZ) {
        return ((long) (sectionX & 0x3F_FFFF) << 42)
                | ((long) (sectionY & 0xFFFFF) << 22)
                | (sectionZ & 0x3F_FFFF);
    }

    /** 좌표 하나를 long 하나로. x·z 는 22비트, y 는 20비트라 월드 범위를 모두 담는다. */
    static long posKey(int x, int y, int z) {
        return ((long) (x & 0x3F_FFFF) << 42)
                | ((long) (y & 0xFFFFF) << 22)
                | (z & 0x3F_FFFF);
    }

    static int unpackX(long key) {
        return signExtend((int) (key >>> 42), 22);
    }

    static int unpackY(long key) {
        return signExtend((int) ((key >>> 22) & 0xFFFFF), 20);
    }

    static int unpackZ(long key) {
        return signExtend((int) (key & 0x3F_FFFF), 22);
    }

    private static int signExtend(int value, int bits) {
        int shift = 32 - bits;
        return value << shift >> shift;
    }
}
