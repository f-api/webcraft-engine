package com.gameexpert.engine.dragon;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * [DRAGON] 엔드 차원 드래곤전의 영속 상태 — 바닐라 {@code EnderDragonFight.CODEC}(NeedsStateScanning ·
 * DragonKilled · PreviouslyKilled · IsRespawning/RespawnTime · Dragon · ExitPortalLocation · Gateways ·
 * RespawnCrystals)와 드래곤 개체의 {@code DragonPhase}·{@code DragonDeathTime}, 수정 개체의 {@code beam_target}·
 * 무적 플래그를 한 행에 모은다. Spring 은 {@code world_dragon_fights.state} 문자열, 정적판은 메타데이터 행
 * {@code dragonFight:<worldId>} 의 같은 필드 JSON 으로 저장한다({@link #encode}/{@link #decode}).
 */
public final class DragonFightState {
    public boolean needsStateScanning;
    /** 이 기능 이전 월드의 첫 적재 이주(활성 포털 + 첫 관문 복원)가 남았다. */
    public boolean legacyMigration;
    public boolean dragonKilled;
    public boolean previouslyKilled;
    public boolean gatewaysInitialized;
    public long dragonMobId;
    public final List<Integer> gateways = new ArrayList<>();
    public boolean hasExitPortal;
    public int exitPortalY;
    public int respawnStage = DragonFight.STAGE_NONE;
    public int respawnTime;
    public final List<Long> respawnCrystals = new ArrayList<>();
    public int aliveCrystals;
    public int ticksSinceDragonSeen;
    public int ticksSinceCrystalsScanned;
    public int ticksSinceLastPlayerScan;
    public long fightRandomState = -1L;
    // ── 드래곤 개체(몹 행은 위치·체력만 싣는다) ──
    public int dragonPhase = DragonPhase.HOLDING_PATTERN.id();
    public int dragonDeathTime;
    public float dragonYRot;
    public long dragonRandomState = -1L;
    // ── 수정 개체 ──
    public final Map<Long, int[]> crystalBeams = new LinkedHashMap<>();
    public final Set<Long> invulnerableCrystals = new LinkedHashSet<>();

    /** 한 줄 {@code key=value;…} 인코딩(키 순서 고정). */
    public String encode() {
        StringBuilder out = new StringBuilder("v=1");
        put(out, "scan", needsStateScanning ? 1 : 0);
        put(out, "legacy", legacyMigration ? 1 : 0);
        put(out, "killed", dragonKilled ? 1 : 0);
        put(out, "prev", previouslyKilled ? 1 : 0);
        put(out, "gwInit", gatewaysInitialized ? 1 : 0);
        put(out, "dragon", dragonMobId);
        out.append(";gw=").append(joinInts(gateways));
        put(out, "exit", hasExitPortal ? exitPortalY : Integer.MIN_VALUE);
        put(out, "stage", respawnStage);
        put(out, "stageTime", respawnTime);
        out.append(";respawn=").append(joinLongs(respawnCrystals));
        put(out, "alive", aliveCrystals);
        put(out, "seen", ticksSinceDragonSeen);
        put(out, "scanT", ticksSinceCrystalsScanned);
        put(out, "playerT", ticksSinceLastPlayerScan);
        put(out, "rng", fightRandomState);
        put(out, "phase", dragonPhase);
        put(out, "death", dragonDeathTime);
        out.append(";yRot=").append(Float.floatToIntBits(dragonYRot));
        put(out, "drng", dragonRandomState);
        StringBuilder beams = new StringBuilder();
        for (Map.Entry<Long, int[]> entry : crystalBeams.entrySet()) {
            if (!beams.isEmpty()) beams.append(',');
            int[] target = entry.getValue();
            beams.append(entry.getKey()).append(':').append(target[0]).append(':').append(target[1]).append(':')
                    .append(target[2]);
        }
        out.append(";beams=").append(beams);
        out.append(";inv=").append(joinLongs(invulnerableCrystals));
        return out.toString();
    }

    public static DragonFightState decode(String encoded) {
        DragonFightState state = new DragonFightState();
        if (encoded == null || encoded.isBlank()) return state;
        Map<String, String> fields = new LinkedHashMap<>();
        for (String part : encoded.split(";")) {
            int eq = part.indexOf('=');
            if (eq > 0) fields.put(part.substring(0, eq), part.substring(eq + 1));
        }
        if (!"1".equals(fields.get("v"))) throw new IllegalArgumentException("unknown dragon fight version");
        state.needsStateScanning = flag(fields, "scan");
        state.legacyMigration = flag(fields, "legacy");
        state.dragonKilled = flag(fields, "killed");
        state.previouslyKilled = flag(fields, "prev");
        state.gatewaysInitialized = flag(fields, "gwInit");
        state.dragonMobId = longOf(fields, "dragon", 0L);
        for (String value : list(fields.get("gw"))) state.gateways.add(Integer.parseInt(value));
        int exit = (int) longOf(fields, "exit", Integer.MIN_VALUE);
        state.hasExitPortal = exit != Integer.MIN_VALUE;
        state.exitPortalY = state.hasExitPortal ? exit : 0;
        state.respawnStage = (int) longOf(fields, "stage", DragonFight.STAGE_NONE);
        state.respawnTime = (int) longOf(fields, "stageTime", 0);
        for (String value : list(fields.get("respawn"))) state.respawnCrystals.add(Long.parseLong(value));
        state.aliveCrystals = (int) longOf(fields, "alive", 0);
        state.ticksSinceDragonSeen = (int) longOf(fields, "seen", 0);
        state.ticksSinceCrystalsScanned = (int) longOf(fields, "scanT", 0);
        state.ticksSinceLastPlayerScan = (int) longOf(fields, "playerT", 0);
        state.fightRandomState = longOf(fields, "rng", -1L);
        state.dragonPhase = (int) longOf(fields, "phase", DragonPhase.HOLDING_PATTERN.id());
        state.dragonDeathTime = (int) longOf(fields, "death", 0);
        state.dragonYRot = Float.intBitsToFloat((int) longOf(fields, "yRot", 0));
        state.dragonRandomState = longOf(fields, "drng", -1L);
        for (String value : list(fields.get("beams"))) {
            String[] parts = value.split(":");
            state.crystalBeams.put(Long.parseLong(parts[0]), new int[] {
                Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3])});
        }
        for (String value : list(fields.get("inv"))) state.invulnerableCrystals.add(Long.parseLong(value));
        return state;
    }

    private static void put(StringBuilder out, String key, long value) {
        out.append(';').append(key).append('=').append(value);
    }

    private static boolean flag(Map<String, String> fields, String key) {
        return "1".equals(fields.get(key));
    }

    private static long longOf(Map<String, String> fields, String key, long fallback) {
        String value = fields.get(key);
        return value == null || value.isEmpty() ? fallback : Long.parseLong(value);
    }

    private static List<String> list(String value) {
        List<String> out = new ArrayList<>();
        if (value == null || value.isEmpty()) return out;
        for (String part : value.split(",")) {
            if (!part.isEmpty()) out.add(part);
        }
        return out;
    }

    private static String joinInts(List<Integer> values) {
        StringBuilder out = new StringBuilder();
        for (int value : values) {
            if (!out.isEmpty()) out.append(',');
            out.append(value);
        }
        return out.toString();
    }

    private static String joinLongs(Iterable<Long> values) {
        StringBuilder out = new StringBuilder();
        for (long value : values) {
            if (!out.isEmpty()) out.append(',');
            out.append(value);
        }
        return out.toString();
    }
}
