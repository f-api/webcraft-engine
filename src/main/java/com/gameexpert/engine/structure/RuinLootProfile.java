package com.gameexpert.engine.structure;

/** Shared deterministic three-level reward decision used by placement, loot and metrics. */
public final class RuinLootProfile {
    public enum Tier { NONE, MUNDANE, SPECIAL }

    /** 구조물 유형별 보유율과, 보유한 loot 안에서의 조건부 SPECIAL 비율. */
    public enum Profile {
        SMALL(1_600, 400),
        TOMB(2_200, 800),
        MINESHAFT(2_500, 1_000),
        FLOODED(2_700, 1_500),
        DUNGEON(2_800, 1_500),
        GENERAL(2_200, 1_000),
        GLITCH_DUNGEON(2_850, 2_000),
        /**
         * [SHIPWRECK] 바닐라 난파선 상자는 템플릿 마커가 있으면 <b>항상</b> 채워지고
         * MUNDANE/SPECIAL 계층이 없다. 생성기가 이미 상자 개수를 정하므로 보유율 100%,
         * SPECIAL 0% 로 두고 실제 내용은 `ExplorationLoot.fillShipwreck` 의 바닐라 표가 낸다.
         */
        SHIPWRECK(10_000, 0),
        /**
         * [DEEP-DARK] 딥다크 도시 상자. 생성기가 이미 상자 개수를 정하므로(방마다 최대 하나,
         * 제단에 하나) 보유율 100% · SPECIAL 0% 로 두고 실제 내용은
         * `ExplorationLoot.fillDeepDarkCity` 의 전용 표가 낸다 — 난파선과 같은 계약이다.
         */
        DEEP_DARK(10_000, 0),
        /**
         * [OCEAN-RUINS] 바닐라 해저 유적 상자는 템플릿에 있으면 <b>항상</b> 채워지고
         * MUNDANE/SPECIAL 계층이 없다. 생성기가 유적 한 채에 상자 하나를 놓으므로 보유율
         * 100 % · SPECIAL 0 % 로 두고 실제 내용은 `ExplorationLoot.fillOceanRuin` 의 바닐라
         * 표(`underwater_ruin_small` / `underwater_ruin_big`)가 낸다.
         */
        OCEAN_RUINS(10_000, 0),
        /**
         * [BURIED-TREASURE] 묻힌 보물은 구조물 자체가 상자 하나다. 보유율 100 % ·
         * SPECIAL 0 % 이고 내용은 `ExplorationLoot.fillBuriedTreasure` 의 바닐라 6개 pool 이
         * 낸다 — 첫 pool 이 바다의 심장을 <b>확정</b>으로 준다.
         */
        BURIED_TREASURE(10_000, 0);

        private final int ownershipLimit;
        private final int specialLimit;

        Profile(int ownershipLimit, int specialLimit) {
            this.ownershipLimit = ownershipLimit;
            this.specialLimit = specialLimit;
        }

        public double ownershipRate() { return ownershipLimit / 10_000.0; }
    }

    private static final int SCALE = 10_000;
    private static final int OWNERSHIP_SALT = 0x4f91c72d;
    private static final int CONTENT_SALT = 0x713ad9e5;

    private RuinLootProfile() {}

    public static Profile profile(StructureSiteDescriptor.Kind kind) {
        return switch (kind) {
            case SMALL_RUIN, CAMPING_SITE, CAMP -> Profile.SMALL;
            case TOMB_RUIN -> Profile.TOMB;
            case MINER_CAMP, MINESHAFT -> Profile.MINESHAFT;
            case FLOODED_RUIN -> Profile.FLOODED;
            case DUNGEON -> Profile.DUNGEON;
            case GLITCH_DUNGEON -> Profile.GLITCH_DUNGEON;
            case MEDIUM_RUIN, GENERAL_RUIN, UNDERGROUND_RUIN -> Profile.GENERAL;
            case SHIPWRECK -> Profile.SHIPWRECK;
            case OCEAN_RUINS -> Profile.OCEAN_RUINS;
            case BURIED_TREASURE -> Profile.BURIED_TREASURE;
            case VILLAGE, PILLAGER_OUTPOST, DESERT_TOMB, METEOR_CRATER, RUINED_TOWER,
                    ALTAR, OCEAN_RUIN, UNDERWATER_RUIN,
                    UNDERGROUND_DUNGEON, BURIED_RUIN, SEALED_CHAMBER, UNDERGROUND_CITY,
                    UNDERGROUND_PRISON, MONSTER_SPAWN_ZONE,
                    // [MONUMENT] 바닐라 해저 신전에는 상자가 없다 — 전리품 표도 없다.
                    // 대저택은 ExplorationLoot 의 전용 바닐라 pool 분기만 사용한다.
                    // [TRIAL] 트라이얼 챔버는 상자를 놓지 않는다 — 전리품은 금고가
                    // 영수증으로 1인 1회 배출하므로 좌표 해시 전리품 표를 쓰지 않는다.
                    // [SULFUR] 유황 동굴 지대에도 상자가 없다 — 보상은 광석·수정 채굴이다.
                    // [GROVE] 제거 ID와 얼룩덜룩한 숲 지대에는 상자가 없다.
                    // [CORAL-REEF] 산호초에도 상자가 없다 — 보상은 산호·해초 채집이다.
                    OCEAN_MONUMENT, WOODLAND_MANSION, TRIAL_CHAMBER,
                    SULFUR_CAVERN, BIRCH_GROVE, DAPPLED_FOREST, CORAL_REEF,
                    RUINED_PORTAL -> unconfigured(kind);
            case DEEP_DARK_CITY -> Profile.DEEP_DARK;
        };
    }

    private static Profile unconfigured(StructureSiteDescriptor.Kind kind) {
        throw new IllegalStateException(kind + " loot profile is not configured");
    }

    public static Tier tier(int seed, int x, int z, StructureSiteDescriptor.Kind kind) {
        return tier(seed, x, z, profile(kind));
    }

    public static Tier tier(int seed, int x, int z, Profile profile) {
        return ownershipRoll(seed, x, z, profile) < profile.ownershipLimit
                ? contentTier(seed, x, z, profile) : Tier.NONE;
    }

    public static boolean hasLoot(int seed, int x, int z, StructureSiteDescriptor.Kind kind) {
        return tier(seed, x, z, kind) != Tier.NONE;
    }

    public static boolean hasLoot(int seed, int x, int z, Profile profile) {
        return tier(seed, x, z, profile) != Tier.NONE;
    }

    /** Content class for an already-owned loot anchor; NONE is decided only by {@link #tier}. */
    public static Tier contentTier(int seed, int x, int z, StructureSiteDescriptor.Kind kind) {
        return contentTier(seed, x, z, profile(kind));
    }

    public static Tier contentTier(int seed, int x, int z, Profile profile) {
        return contentRoll(seed, x, z, profile) < profile.specialLimit ? Tier.SPECIAL : Tier.MUNDANE;
    }

    public static int contentRoll(int seed, int x, int z, Profile profile) {
        return laneRoll(seed, x, z, profile, CONTENT_SALT);
    }

    private static int ownershipRoll(int seed, int x, int z, Profile profile) {
        return laneRoll(seed, x, z, profile, OWNERSHIP_SALT);
    }

    private static int laneRoll(int seed, int x, int z, Profile profile, int salt) {
        int lane = StructureHash.hash3(StructureHash.seedSalt(seed, salt),
                x, profile.ordinal(), z);
        return (int) Long.remainderUnsigned(Integer.toUnsignedLong(lane), SCALE);
    }
}
