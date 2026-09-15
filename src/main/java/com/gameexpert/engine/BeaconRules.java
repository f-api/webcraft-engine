package com.gameexpert.engine;

import com.gameexpert.engine.effect.StatusEffect;
import com.gameexpert.engine.inventory.PlayerInventory;
import com.gameexpert.terrain.Blocks;
import java.util.ArrayList;
import java.util.List;

/**
 * [BEACON] 신호기 판정. <b>전부 순수 함수</b>다 — 좌표 표본 함수와 정수만 받고 가변 상태·난수·시계가
 * 없다. 정적판 {@code client/src/world/BeaconRules.ts} 가 같은 규칙이며, 클라이언트 빔 렌더러도 그
 * 파일의 {@code beaconBeamSections} 로 같은 구획을 파생한다(바닐라도 빔 구획을 클라이언트 월드에서
 * 따로 계산한다 — {@code BeaconBlockEntity.tick} 이 양쪽에서 돈다).
 *
 * <h2>근거 (고정 26.3-snapshot-7 jar, javap)</h2>
 * <ul>
 *   <li>{@code BeaconBlockEntity.BEACON_EFFECTS} = [[SPEED, HASTE], [RESISTANCE, JUMP_BOOST],
 *       [STRENGTH], [REGENERATION]] — 단계 n 의 효과는 피라미드 n 층 이상에서 고를 수 있다.</li>
 *   <li>{@code updateBase}: 층 i(1..4)는 {@code y - i} 평면의 (2i+1)² 칸이 전부
 *       {@code #minecraft:beacon_base_blocks}(철·금·에메랄드·다이아몬드·네더라이트 블록)여야 하고,
 *       한 층이 비면 그 위층 수에서 멈춘다. {@code y - i < minY} 이면 거기서 끝난다.</li>
 *   <li>{@code tick}: 빔 구획은 신호기 칸 자신(BeaconBeamBlock, WHITE)부터 위로 훑는다.
 *       {@code BeaconBeamBlock}(색유리·색유리판·신호기)은 구획 색을 바꾸고 — 두 번째 구획은 순수 색,
 *       그 뒤로는 직전 구획 색과 {@code ARGB.average} 한 새 구획, 같은 색이면 높이만 +1 —,
 *       그 밖의 칸은 {@code getLightDampening() >= 15} 이면서 기반암이 아니면 빔을 끊는다(구획 비움).</li>
 *   <li>{@code gameTime % 80 == 0} 에만 층을 다시 세고({@code beamSections} 가 비지 않았을 때) 효과를
 *       준다. 10 TPS 서버 틱 1회 = MC 2틱이라 {@link #UPDATE_INTERVAL_SERVER_TICKS} = 40 이다
 *       (콘딧의 {@code gameTime % 40} → 서버 틱 20 과 같은 환산).</li>
 *   <li>{@code applyEffects}: 반경 {@code levels * 10 + 10}, 지속 {@code (9 + levels * 2) * 20} MC 틱,
 *       상자 {@code new AABB(pos).inflate(r).expandTowards(0, level.getHeight(), 0)} 안의 플레이어만.
 *       주 효과 앰프는 {@code levels >= 4 && primary == secondary} 일 때 1, 보조 효과
 *       (4층 · 주 효과와 다를 때)는 앰프 0. 둘 다 ambient · 표시 입자다.</li>
 *   <li>{@code validateEffects}: 보조가 있으면 4층 필요, 각 효과의 요구 층 ≤ levels, 주 효과는 재생 불가,
 *       보조는 재생이거나 주 효과와 같아야 한다.</li>
 * </ul>
 *
 * <h2>저장 — 블록 state 바이트</h2>
 * 바닐라 블록 엔티티가 영속하는 값은 {@code primary_effect} · {@code secondary_effect} 둘뿐이다
 * ({@code Levels} 는 쓰지만 {@code loadAdditional} 이 읽지 않아 적재 때 0 에서 다시 센다.
 * {@code CustomName}/{@code lock} 은 이 저장소의 설치 블록 어디에도 없는 축이라 다루지 않는다).
 * 두 값은 합쳐 6 비트라 신호기의 엔진 state 바이트에 담는다 — 비트 0..2 주 효과 코드, 비트 3..5
 * 보조 효과 코드. 블록과 같은 diff 행(Spring {@code WorldBlockDiff} · 정적판 월드 변이)에 원자적으로
 * 저장·전송되고, 칸이 어떤 경로로든 바뀌면 함께 사라진다. 새 블록이라 옛 저장과 충돌이 없다.
 */
public final class BeaconRules {

    private BeaconRules() {
    }

    /** {@code BeaconBlockEntity.MAX_LEVELS}. */
    public static final int MAX_LEVELS = 4;
    /** {@code BeaconBlockEntity.LEVELS_NEEDED_FOR_SECONDARY}. */
    public static final int LEVELS_NEEDED_FOR_SECONDARY = 4;
    /** 층 재계산 · 효과 부여 주기(MC 틱, {@code gameTime % 80}). */
    public static final int UPDATE_INTERVAL_MC_TICKS = 80;
    /** 위 주기의 10 TPS 서버 틱 환산. */
    public static final int UPDATE_INTERVAL_SERVER_TICKS =
            UPDATE_INTERVAL_MC_TICKS / com.gameexpert.engine.effect.StatusEffects.MC_TICKS_PER_SERVER_TICK;
    /** 신호기 자신의 빔 색({@code BeaconBlock.getColor} = DyeColor.WHITE 의 textureDiffuseColor). */
    public static final int WHITE_BEAM_COLOR = 0xF9FFFE;

    /**
     * {@code DyeColor.getTextureDiffuseColor()} 서수 순서(흰·주황·자홍·하늘·노랑·연두·분홍·회색·
     * 연회색·청록·보라·파랑·갈색·초록·빨강·검정). 색유리 1141..1156 · 색유리판 1157..1172 가 같은 순서다.
     */
    private static final int[] DYE_DIFFUSE = {
            0xF9FFFE, 0xF9801D, 0xC74EBD, 0x3AB3DA, 0xFED83D, 0x80C71F, 0xF38BAA, 0x474F52,
            0x9D9D97, 0x169C9C, 0x8932B8, 0x3C44AA, 0x835432, 0x5E7C16, 0xB02E26, 0x1D1D21,
    };

    /**
     * 신호기 효과. 코드(1..6)는 state 바이트와 프로토콜 이름의 짝이며 {@code BEACON_EFFECTS} 순서다.
     * {@code tier} 는 {@code getRequiredLevelsFor} 의 반환값(단계 번호 = 요구 층 수)이다.
     */
    public enum Power {
        SPEED(1, 1, StatusEffect.SPEED),
        HASTE(2, 1, StatusEffect.HASTE),
        RESISTANCE(3, 2, StatusEffect.RESISTANCE),
        JUMP_BOOST(4, 2, StatusEffect.JUMP_BOOST),
        STRENGTH(5, 3, StatusEffect.STRENGTH),
        REGENERATION(6, 4, StatusEffect.REGENERATION);

        private final int code;
        private final int tier;
        private final StatusEffect effect;

        Power(int code, int tier, StatusEffect effect) {
            this.code = code;
            this.tier = tier;
            this.effect = effect;
        }

        public int code() { return code; }
        public int tier() { return tier; }
        public StatusEffect effect() { return effect; }
        /** 프로토콜 이름. 상태이상 이름(바닐라 레지스트리 경로)과 같다. */
        public String wireName() { return effect.protocolName(); }

        public static Power fromCode(int code) {
            for (Power power : values()) if (power.code == code) return power;
            return null;
        }

        public static Power fromWireName(String name) {
            if (name == null) return null;
            for (Power power : values()) if (power.wireName().equals(name)) return power;
            return null;
        }
    }

    private static final int CODE_MASK = 0x7;
    private static final int SECONDARY_SHIFT = 3;
    /** 신호기 state 에서 의미가 있는 비트(주 3 + 보조 3). */
    public static final int STATE_MASK = 0x3F;

    /** state 바이트의 주 효과. 없거나 알 수 없는 코드면 null({@code loadEffect} 의 VALID_EFFECTS 거름). */
    public static Power primary(int state) {
        return Power.fromCode(state & CODE_MASK);
    }

    /** state 바이트의 보조 효과. */
    public static Power secondary(int state) {
        return Power.fromCode((state >> SECONDARY_SHIFT) & CODE_MASK);
    }

    /** 두 효과를 state 바이트로 담는다. */
    public static int encodeState(Power primary, Power secondary) {
        return (primary == null ? 0 : primary.code)
                | (secondary == null ? 0 : secondary.code) << SECONDARY_SHIFT;
    }

    /** 저장·요청 state 를 의미 비트만 남긴다(모르는 코드는 효과 없음으로 읽히므로 비트째 지운다). */
    public static int normalizeState(int state) {
        int meaningful = state & STATE_MASK;
        return encodeState(primary(meaningful), secondary(meaningful));
    }

    /** {@code #minecraft:beacon_base_blocks}: 네더라이트 · 에메랄드 · 다이아몬드 · 금 · 철 블록. */
    public static boolean isBaseBlock(int id) {
        return id == Blocks.NETHERITE_BLOCK || id == Blocks.EMERALD_BLOCK
                || id == Blocks.DIAMOND_BLOCK || id == Blocks.GOLD_BLOCK || id == Blocks.IRON_BLOCK;
    }

    /** {@code #minecraft:beacon_payment_items}: 네더라이트 주괴 · 에메랄드 · 다이아몬드 · 금 · 철 주괴. */
    public static boolean isPaymentItem(int itemType) {
        return itemType == PlayerInventory.NETHERITE_INGOT || itemType == PlayerInventory.EMERALD
                || itemType == PlayerInventory.DIAMOND || itemType == PlayerInventory.GOLD_INGOT
                || itemType == PlayerInventory.IRON_INGOT;
    }

    /** 절대 좌표 블록 표본. */
    @FunctionalInterface
    public interface BlockSampler {
        int blockAt(int x, int y, int z);
    }

    /** 절대 좌표 칸이 빔을 끊는가(바닐라 {@code getLightDampening() >= 15} 이면서 기반암이 아님). */
    @FunctionalInterface
    public interface BeamBlocker {
        boolean blocks(int x, int y, int z, int blockId);
    }

    /** {@code updateBase}: 피라미드 층 수(0..4). */
    public static int levels(BlockSampler sampler, int x, int y, int z, int minY) {
        int levels = 0;
        for (int layer = 1; layer <= MAX_LEVELS; layer++) {
            int by = y - layer;
            if (by < minY) break;
            boolean complete = true;
            for (int bx = x - layer; bx <= x + layer && complete; bx++) {
                for (int bz = z - layer; bz <= z + layer; bz++) {
                    if (!isBaseBlock(sampler.blockAt(bx, by, bz))) {
                        complete = false;
                        break;
                    }
                }
            }
            if (!complete) break;
            levels = layer;
        }
        return levels;
    }

    /** {@code BeaconBeamBlock.getColor().getTextureDiffuseColor()} 의 RGB. 빔 블록이 아니면 -1. */
    public static int beamColor(int id) {
        if (id == Blocks.BEACON) return WHITE_BEAM_COLOR;
        if (Blocks.isStainedGlass(id)) return DYE_DIFFUSE[id - Blocks.WHITE_STAINED_GLASS];
        if (Blocks.isStainedGlassPane(id)) return DYE_DIFFUSE[id - Blocks.WHITE_STAINED_GLASS_PANE];
        return -1;
    }

    /** {@code ARGB.average}: 채널별 정수 평균(알파는 둘 다 255 라 그대로). */
    public static int averageColor(int a, int b) {
        int r = (((a >> 16) & 0xFF) + ((b >> 16) & 0xFF)) / 2;
        int g = (((a >> 8) & 0xFF) + ((b >> 8) & 0xFF)) / 2;
        int bl = ((a & 0xFF) + (b & 0xFF)) / 2;
        return r << 16 | g << 8 | bl;
    }

    /** 빔 구획 하나(색 RGB · 높이 블록 수). */
    public static final class Section {
        private final int color;
        private final int height;

        public Section(int color, int height) {
            this.color = color;
            this.height = height;
        }

        public int color() { return color; }
        public int height() { return height; }

        @Override
        public boolean equals(Object other) {
            return other instanceof Section section && section.color == color && section.height == height;
        }

        @Override
        public int hashCode() {
            return 31 * color + height;
        }

        @Override
        public String toString() {
            return "Section[color=" + Integer.toHexString(color) + ", height=" + height + "]";
        }
    }

    /**
     * 빔 구획 목록. 신호기 칸(y)부터 {@code topY} 까지 한 번에 훑는다 — 바닐라는 틱당 10 칸씩 나눠
     * 훑지만 결과(완료 시점의 구획)는 같은 칸 배치에 같다. 빔이 끊기면 빈 목록이다.
     *
     * <p>{@code topY} 는 바닐라 {@code WORLD_SURFACE} 높이맵 이상이면 된다 — 그 위는 전부 공기라
     * 마지막 구획 높이만 늘고 색은 그대로다(클라 렌더러는 마지막 구획을 BeaconRenderer.MAX_RENDER_Y = 2048 로 그린다).
     */
    public static List<Section> beamSections(BlockSampler sampler, BeamBlocker blocker,
            int x, int y, int z, int topY) {
        List<Section> sections = new ArrayList<>();
        int color = -1;
        int height = 0;
        for (int cy = y; cy <= topY; cy++) {
            int id = sampler.blockAt(x, cy, z);
            int beam = beamColor(id);
            if (beam >= 0) {
                if (sections.size() + (color >= 0 ? 1 : 0) <= 1) {
                    // 신호기 자신이 첫 구획, 그 위 첫 색유리가 순수 색 두 번째 구획이다.
                    if (color >= 0) sections.add(new Section(color, height));
                    color = beam;
                    height = 1;
                } else if (beam == color) {
                    height++;
                } else {
                    sections.add(new Section(color, height));
                    color = averageColor(color, beam);
                    height = 1;
                }
            } else if (color < 0 || blocker.blocks(x, cy, z, id)) {
                return List.of();
            } else {
                height++;
            }
        }
        if (color >= 0) sections.add(new Section(color, height));
        return List.copyOf(sections);
    }

    /** {@code getRequiredLevelsFor}: 효과가 속한 단계 번호. null 은 0. */
    private static int requiredLevels(Power power) {
        return power == null ? 0 : power.tier;
    }

    /** {@code BeaconBlockEntity.validateEffects}. */
    public static boolean validateEffects(Power primary, Power secondary, int levels) {
        if (secondary != null && levels < LEVELS_NEEDED_FOR_SECONDARY) return false;
        int primaryLevels = requiredLevels(primary);
        int secondaryLevels = requiredLevels(secondary);
        if (primaryLevels > levels || secondaryLevels > levels) return false;
        if (primaryLevels >= MAX_LEVELS) return false;
        return secondaryLevels == 0 || secondaryLevels >= MAX_LEVELS || primary == secondary;
    }

    /** 이번 서버 틱이 신호기 맥박 틱인가({@code gameTime % 80 == 0} 의 10 TPS 환산 {@code tick % 40 == 0}). */
    public static boolean pulseDue(long serverTick) {
        return Math.floorMod(serverTick, UPDATE_INTERVAL_SERVER_TICKS) == 0;
    }

    /** 효과 반경({@code levels * 10 + 10}). */
    public static int radius(int levels) {
        return levels * 10 + 10;
    }

    /** 효과 지속(MC 틱, {@code (9 + levels * 2) * 20}). */
    public static int durationMcTicks(int levels) {
        return (9 + levels * 2) * 20;
    }

    /** 주 효과 앰프: 4층이고 보조가 주 효과와 같으면 1(레벨 II). */
    public static int primaryAmplifier(int levels, Power primary, Power secondary) {
        return levels >= MAX_LEVELS && primary != null && primary == secondary ? 1 : 0;
    }

    /** 보조 효과를 따로 주는가(4층 · 보조가 있고 주 효과와 다를 때). */
    public static boolean appliesSecondary(int levels, Power primary, Power secondary) {
        return levels >= MAX_LEVELS && secondary != null && primary != secondary;
    }

    /**
     * 효과 상자와 개체 상자가 겹치는가. 효과 상자는 {@code new AABB(pos).inflate(r)
     * .expandTowards(0, worldHeight, 0)} 이고 {@code getEntitiesOfClass} 는 엄격 부등호 겹침이다.
     *
     * @param worldHeight {@code level.getHeight()} (오버월드 384)
     */
    public static boolean reaches(int levels, int bx, int by, int bz, int worldHeight,
            double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        double r = radius(levels);
        return minX < bx + 1 + r && maxX > bx - r
                && minY < by + 1 + r + worldHeight && maxY > by - r
                && minZ < bz + 1 + r && maxZ > bz - r;
    }

}
