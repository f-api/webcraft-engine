package com.gameexpert.engine.sculk;

import java.util.ArrayList;
import java.util.List;

import com.gameexpert.terrain.Blocks;

/**
 * [DEEP-DARK] 스컬크 촉매의 <b>축소 확산 계약</b>. 몹이 촉매 근처에서 죽으면 그 자리 주변이
 * 스컬크로 덮이고, 죽음이 떨어뜨렸을 경험치는 <b>구슬 대신</b> 확산 예산이 된다.
 *
 * <p><b>원본</b>: {@code SculkCatalystBlockEntity} 는 {@code GameEventListener} 로
 * {@code GameEvent.ENTITY_DIE} 를 듣고({@code getListenerRadius() == 8}), 죽은
 * {@code LivingEntity} 의 {@code getExperienceReward()} 를 {@code SculkSpreader.addCursors}
 * 에 charge 로 넣은 뒤 {@code entity.skipDropExperience()} 를 호출해 <b>경험치 구슬 생성을
 * 취소</b>한다. 커서는 {@code SculkBlock.attemptUseCharge} 에서 블록 하나를 스컬크로 바꿀
 * 때마다 charge 1 을 소비하고, 자란 자리 위에는 {@code SculkBlock.getRandomGrowthState} 가
 * {@code random.nextInt(11) == 0} 이면 비명체를, 아니면 감지체를 올린다.
 *
 * <p><b>줄인 것</b>(근거 등급 [C]): 커서 이동·charge 병합·{@code SculkSpreader} 의 다단계
 * 확산 시뮬레이션은 이 저장소에 없다. 대신 <b>한 번에 정산</b>한다 — 사망 지점에서
 * {@link #SPREAD_RADIUS} 안의 변환 가능 블록을 결정적 순서로 훑어 charge 만큼 바꾼다.
 * 총 예산은 {@link #MAX_CHARGE} 로 자른다(바닐라 {@code SculkSpreader} 의 charge 상한
 * {@code MAX_CHARGE = 1000} 과 달리 이 저장소는 한 틱에 전부 커밋하므로 틱 예산이 상한이다).
 * 반경·1/11 성장 확률·XP 흡수는 전부 바닐라 그대로다([B]).
 *
 * <p>상태가 없는 순수 규칙 클래스다. 실제 블록 쓰기·XP 취소는 호출자(양 권위의 사망 훅)가 한다.
 */
public final class SculkCatalystRules {

    private SculkCatalystRules() {}

    /**
     * 촉매가 사망 사건을 듣는 구 반경(블록).
     * [B] {@code SculkCatalystBlockEntity.CatalystListener#getListenerRadius() == 8}.
     */
    public static final int CATALYST_RADIUS = 8;

    /**
     * 사망 지점에서 스컬크가 퍼지는 최대 거리(블록). [C] 바닐라 커서는 charge 를 나눠 들고
     * 여러 틱에 걸쳐 이동하지만 여기서는 한 번에 정산하므로 거리로 대신 자른다.
     */
    public static final int SPREAD_RADIUS = 4;

    /**
     * 사망 하나가 쓸 수 있는 최대 charge. [C] 한 틱 안에서 전부 커밋하므로 틱 예산을 지키는
     * 상한이 필요하다. 바닐라 몹의 경험치는 대부분 5 이하라 실전에서 거의 닿지 않는다.
     */
    public static final int MAX_CHARGE = 20;

    /**
     * 스컬크가 자란 자리에 성장체가 올라갈 확률의 분모. 그중 1 은 비명체, 나머지는 감지체다.
     * [B] {@code SculkBlock.getRandomGrowthState}: {@code random.nextInt(11) == 0} → 비명체.
     */
    public static final int GROWTH_ONE_IN = 11;

    /**
     * 변환된 블록 위에 성장체(감지체·비명체)가 설 확률의 분모. [C] 바닐라는 커서가
     * {@code canPlaceGrowth} 를 만족할 때만 자라며 그 빈도가 이 저장소에는 없다. 스컬크 밭이
     * 감지체로 뒤덮이지 않도록 여기서 한 번 더 거른다.
     */
    public static final int GROWTH_ATTEMPT_ONE_IN = 8;

    /** 한 번의 개화가 만드는 블록 변경 하나. */
    public record Conversion(int x, int y, int z, int blockType) {}

    /** 개화가 읽는 지형. 호출자의 권위 지형을 그대로 감싼다. */
    public interface Terrain {
        /** 비상주·범위 밖이면 음수. */
        int blockAt(int x, int y, int z);
    }

    /**
     * 이 블록이 스컬크로 덮일 수 있는가. [B] 바닐라 {@code BlockTags.SCULK_REPLACEABLE} 중
     * 이 저장소에 존재하는 종만 담는다 — 태그에 없는 블록은 바닐라에서도 안 바뀐다.
     */
    public static boolean isSculkReplaceable(int blockType) {
        return switch (blockType) {
            case Blocks.DIRT, Blocks.GRASS, Blocks.COARSE_DIRT, Blocks.PODZOL, Blocks.MYCELIUM,
                    Blocks.ROOTED_DIRT, Blocks.CLAY, Blocks.MOSS_BLOCK, Blocks.SAND,
                    Blocks.GRAVEL, Blocks.STONE, Blocks.DEEPSLATE, Blocks.TUFF -> true;
            default -> false;
        };
    }

    /** 이 블록이 촉매인가. */
    public static boolean isCatalyst(int blockType) {
        return blockType == Blocks.SCULK_CATALYST;
    }

    /** 촉매가 이 상대 좌표의 사망을 듣는가. 바닐라와 같이 <b>구</b> 반경이다. */
    public static boolean withinCatalystRange(int dx, int dy, int dz) {
        return dx * dx + dy * dy + dz * dz <= CATALYST_RADIUS * CATALYST_RADIUS;
    }

    /**
     * 이 사망이 쓸 확산 예산. 경험치가 0 이면(새끼·비플레이어 처치) 바닐라와 같이
     * 촉매는 아무 일도 하지 않는다 — {@code getExperienceReward() > 0} 가드와 같다. [B]
     */
    public static int charge(int droppedXp) {
        if (droppedXp <= 0) return 0;
        return Math.min(MAX_CHARGE, droppedXp);
    }

    /**
     * 사망 지점에서 유도한 안정 시드. 같은 월드·같은 좌표·같은 사망 순번은 언제나 같은
     * 개화를 낸다 — 두 권위가 같은 결과를 내야 하므로 난수 원천은 이 하나뿐이다.
     */
    public static long bloomSeed(long worldSeed, int x, int y, int z, long deathOrdinal) {
        long hash = worldSeed * 0x9E3779B97F4A7C15L;
        hash ^= (long) x * 0x165667B19E3779F9L;
        hash ^= (long) y * 0x27D4EB2F165667C5L;
        hash ^= (long) z * 0x2545F4914F6CDD1DL;
        hash ^= deathOrdinal * 0x9E3779B97F4A7C15L;
        hash ^= hash >>> 33;
        hash *= 0xFF51AFD7ED558CCDL;
        hash ^= hash >>> 33;
        hash *= 0xC4CEB9FE1A85EC53L;
        return hash ^ hash >>> 33;
    }

    static long stream(long seed, int drawIndex) {
        long z = seed + 0x9E3779B97F4A7C15L * (drawIndex + 1);
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    static int draw(long seed, int drawIndex, int bound) {
        return (int) Long.remainderUnsigned(stream(seed, drawIndex), bound);
    }

    /**
     * 이 사망이 만드는 블록 변경 전부. 사망 지점을 중심으로 거리 → y → x → z 의 <b>고정
     * 순서</b>로 후보를 훑어 charge 만큼 스컬크로 바꾸고, 그 위 칸이 비어 있으면 성장체를
     * 올린다. 순서가 고정이라 두 권위와 재실행이 언제나 같은 목록을 낸다.
     *
     * @param charge {@link #charge(int)} 가 낸 예산
     */
    public static List<Conversion> bloom(long seed, int deathX, int deathY, int deathZ,
            int charge, Terrain terrain) {
        if (charge <= 0) return List.of();
        List<Conversion> conversions = null;
        int spent = 0;
        int draws = 0;
        for (int radius = 0; radius <= SPREAD_RADIUS && spent < charge; radius++) {
            for (int dy = -radius; dy <= radius && spent < charge; dy++) {
                for (int dx = -radius; dx <= radius && spent < charge; dx++) {
                    for (int dz = -radius; dz <= radius && spent < charge; dz++) {
                        // 껍질 순회: 이번 radius 에서 새로 들어온 칸만 본다.
                        if (Math.max(Math.abs(dx), Math.max(Math.abs(dy), Math.abs(dz))) != radius) {
                            continue;
                        }
                        int x = deathX + dx;
                        int y = deathY + dy;
                        int z = deathZ + dz;
                        if (!isSculkReplaceable(terrain.blockAt(x, y, z))) continue;
                        if (conversions == null) conversions = new ArrayList<>(charge * 2);
                        conversions.add(new Conversion(x, y, z, Blocks.SCULK));
                        spent++;
                        int growth = growthAt(seed, draws++, terrain, x, y + 1, z);
                        if (growth != Blocks.AIR) {
                            conversions.add(new Conversion(x, y + 1, z, growth));
                        }
                    }
                }
            }
        }
        return conversions == null ? List.of() : conversions;
    }

    /**
     * 이 자리 위에 설 성장체. 없으면 {@link Blocks#AIR}. 바닐라와 같이 <b>공기 칸에만</b>
     * 세우고, 세운다면 1/11 로 비명체·나머지는 감지체다.
     */
    static int growthAt(long seed, int drawIndex, Terrain terrain, int x, int y, int z) {
        if (terrain.blockAt(x, y, z) != Blocks.AIR) return Blocks.AIR;
        if (draw(seed, drawIndex * 2, GROWTH_ATTEMPT_ONE_IN) != 0) return Blocks.AIR;
        return draw(seed, drawIndex * 2 + 1, GROWTH_ONE_IN) == 0
                ? Blocks.SCULK_SHRIEKER : Blocks.SCULK_SENSOR;
    }

    /**
     * 이 사망이 촉매에 흡수되는가. 흡수되면 경험치 구슬을 만들지 않는다 — 바닐라
     * {@code entity.skipDropExperience()} 와 같은 자리다. [B]
     */
    public static boolean absorbs(int charge, boolean catalystNearby) {
        return catalystNearby && charge > 0;
    }

    /**
     * 사망 지점 반경 안에 촉매가 있는가. 후보 상자만 훑되 판정 자체는 구 반경이다.
     * 정적판 {@code sculkCatalystNear} 와 같은 순회다.
     */
    public static boolean catalystNear(Terrain terrain, int deathX, int deathY, int deathZ) {
        for (int dx = -CATALYST_RADIUS; dx <= CATALYST_RADIUS; dx++) {
            for (int dy = -CATALYST_RADIUS; dy <= CATALYST_RADIUS; dy++) {
                for (int dz = -CATALYST_RADIUS; dz <= CATALYST_RADIUS; dz++) {
                    if (!withinCatalystRange(dx, dy, dz)) continue;
                    if (isCatalyst(terrain.blockAt(deathX + dx, deathY + dy, deathZ + dz))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
