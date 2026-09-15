package com.gameexpert.engine.trial;

import com.gameexpert.engine.enchant.EnchantmentRules;
import com.gameexpert.engine.inventory.PlayerInventory;
import com.gameexpert.engine.raid.RaidRewardReceipt;

/**
 * [TRIAL] 금고 축소 계약 — <b>플레이어 1인당 정확히 한 번</b> 열린다.
 *
 * <p><b>왜 직접 보상이 아니라 금고인가(근거)</b>
 * <ul>
 *   <li>이미 착지한 중복 청구 방지 계약은 {@code RaidRewardReceipt} 의
 *       {@code (worldId, raidId, rewardToken)} 유일 제약과 {@code PENDING → GRANTED}
 *       조건부 전이 하나뿐이다({@code mob/entity/WorldRaidReceipt}).</li>
 *   <li>그 계약의 키에는 <b>토큰 자리</b>가 있고 수령자 닉네임 칸이 따로 있다. 토큰에
 *       닉네임을 실으면 "같은 금고 · 플레이어마다 한 행" 이 키 변경 없이 그대로 나온다 —
 *       {@link #rewardToken}.</li>
 *   <li>직접 보상(완료 즉시 지급)은 수령자가 하나뿐이라 같은 계약으로 다인 파티를
 *       표현할 수 없다. 표현하려면 새 키 스키마가 필요하고, 그것이야말로 "기존 계약에
 *       맞는 쪽" 이 아니다. 그래서 <b>금고를 채택</b>했다.</li>
 * </ul>
 *
 * <p>열쇠: 바닐라 {@code VaultConfig.keyItem} — 일반 금고(trial_chambers/reward/vault.nbt)는
 * {@code minecraft:trial_key}, 불길한 금고(ominous_vault.nbt, {@code ominous=true})는
 * {@code minecraft:ominous_trial_key} 다({@code Mc263TrialChambersGrammar} 의 금고 sidecar 가 같은
 * 짝을 못박는다).
 *
 * <p>[TRIAL-GAP] 전리품은 바닐라 {@code VaultConfig.lootTable} 그대로 일반 금고는
 * {@code chests/trial_chambers/reward}, 불길한 금고는 {@code reward_ominous} 를 굴린다
 * ({@link TrialLootTables#vaultReward}, 시드 = 이 플레이어의 영수증 {@link #rewardSeed}). 열쇠 정산은
 * 굴린 스택마다 배출 outbox 행({@link #ejectToken})을 영수증과 같은 트랜잭션에 쓰고, 금고가
 * EJECTING 에서 한 행씩 지면 아이템으로 갚는다. 아래의 옛 축소 풀({@link #roll})은 이 변경 전에
 * 기록된 대기 보상 행만 읽는다.</p>
 */
public final class TrialVaultContract {

    private TrialVaultContract() {}

    /**
     * 블록 상태 하위 2비트. {@code client/src/world/packs/p20-trial.ts} 가 이 서수로 타일을
     * 고르므로 순서를 바꾸면 렌더가 어긋난다.
     */
    public enum State {
        INACTIVE, ACTIVE, UNLOCKING, EJECTING;

        public int stateBits() {
            return ordinal();
        }
    }

    /** 블록 상태의 {@link State} 비트(하위 2비트). */
    public static final int STATE_MASK = 0x03;
    /**
     * 블록 상태의 바닐라 {@code facing}(N 0 · E 1 · S 2 · W 3) 비트 2..3. 구조물 carrier 가 싣고
     * ({@code CarrierStateProjection}) 권위의 상태 전이는 건드리지 않는다.
     */
    public static final int FACING_MASK = 0x0c;
    /** 블록 상태의 바닐라 {@code ominous} 비트. carrier 가 싣는다(렌더·열쇠 판정이 읽는다). */
    public static final int OMINOUS = 0x10;

    /**
     * 바닐라 {@code VaultState.LightLevel}: INACTIVE 는 HALF_LIT(6), ACTIVE · UNLOCKING · EJECTING
     * 은 LIT(12) 다(26.3-snapshot-7 javap {@code VaultState.<clinit>} · {@code
     * VaultState$LightLevel.<clinit>} · {@code Blocks.lambda$static$422}).
     */
    public static int lightLevel(int blockState) {
        return (blockState & STATE_MASK) == State.INACTIVE.ordinal() ? 6 : 12;
    }

    /** 이 금고가 요구하는 열쇠. 불길한 금고는 불길한 트라이얼 열쇠다. */
    public static short requiredKey(int blockState) {
        return (blockState & OMINOUS) != 0 ? PlayerInventory.OMINOUS_TRIAL_KEY
                : PlayerInventory.TRIAL_KEY;
    }

    /** 금고 열쇠 두 종 중 하나인가. */
    public static boolean isVaultKey(short itemType) {
        return itemType == PlayerInventory.TRIAL_KEY || itemType == PlayerInventory.OMINOUS_TRIAL_KEY;
    }

    /**
     * [TRIAL-GAP] 금고 활성 반경. 바닐라 {@code VaultConfig.DEFAULT} 의 {@code activationRange 4.0}
     * (26.3 javap {@code VaultConfig.<init>()}: ldc2_w 4.0, 4.5). 트라이얼 챔버 금고 구조물 NBT 는
     * 이 값을 덮지 않는다.
     */
    public static final double ACTIVATION_RANGE = 4.0;
    /** [TRIAL-GAP] 금고 비활성 반경. 바닐라 {@code VaultConfig.DEFAULT} 의 {@code deactivationRange 4.5}. */
    public static final double DEACTIVATION_RANGE = 4.5;

    /** 금고 하나를 여는 데 드는 열쇠 개수. [B] 바닐라는 트라이얼 열쇠 1 개다. */
    public static final int KEY_COST = 1;

    /**
     * 옛 4-상태 계약이 승리 1회마다 배출하던 트라이얼 열쇠 개수. 지금은 옛 대기 보상 행
     * ({@code WorldTrialSite.reward_count} 기본값)만 이 값을 뜻하고, 새 배출은
     * {@link TrialSpawnerContract#ejectedReward} 가 바닐라 배출 표로 고른다.
     */
    public static final int KEY_REWARD_COUNT = 1;


    /** 이 금고의 identity. 좌표 하나가 곧 금고 하나다. */
    public static long vaultId(long worldSeed, int x, int y, int z) {
        return TrialSpawnerContract.spawnerSeed(worldSeed ^ 0x5A17_4EC5_1F3B_9D27L, x, y, z);
    }

    /**
     * 이 금고 · 이 플레이어의 보상 토큰. 영수증 유일 제약이 {@code (worldId, raidId, token)}
     * 이므로 토큰에 닉네임을 실으면 "금고마다 플레이어마다 한 번" 이 그대로 된다.
     */
    public static String rewardToken(String nickname) {
        if (nickname == null || nickname.isBlank()) {
            throw new IllegalArgumentException("nickname is required");
        }
        return TOKEN_PREFIX + nickname;
    }

    /** 토큰 접두어. 레이드 승리 토큰과 절대 겹치지 않게 자기 이름을 앞에 둔다. */
    public static final String TOKEN_PREFIX = "trial_vault_v1:";

    /**
     * [TRIAL-GAP] 금고 배출 outbox 행의 토큰 접두어. 인벤토리 자동 지급 lane 은 이 접두어 행을
     * 건드리지 않는다 — 금고가 지면으로 배출해야 하는 전리품이기 때문이다.
     */
    public static final String EJECT_TOKEN_PREFIX = "trial_vault_eject_v1:";

    /** 배출 outbox 한 행의 토큰({@code prefix + 닉네임 + ":" + 굴림 순번}). */
    public static String ejectToken(String nickname, int index) {
        if (nickname == null || nickname.isBlank() || index < 0) {
            throw new IllegalArgumentException("vault ejection token requires nickname and index");
        }
        return EJECT_TOKEN_PREFIX + nickname + ":" + index;
    }

    public static boolean isEjectToken(String token) {
        return token != null && token.startsWith(EJECT_TOKEN_PREFIX);
    }

    /** 배출 토큰의 굴림 순번. 형식이 틀리면 −1. */
    public static int ejectTokenIndex(String token) {
        if (!isEjectToken(token)) return -1;
        int colon = token.lastIndexOf(':');
        try {
            return Integer.parseInt(token.substring(colon + 1));
        } catch (NumberFormatException invalid) {
            return -1;
        }
    }

    /** 금고 보상 영수증 토큰의 닉네임. 금고 영수증이 아니면 null. */
    public static String rewardTokenNickname(String token) {
        return token != null && token.startsWith(TOKEN_PREFIX)
                ? token.substring(TOKEN_PREFIX.length()) : null;
    }

    /**
     * 보상 롤 시드. 금고 identity 와 수령자를 함께 섞어 <b>플레이어마다 다른</b> 보상이
     * 나오게 하고, 같은 조합은 재시도·재접속·크래시 복구에서 언제나 같은 값을 낸다.
     */
    public static long rewardSeed(long vaultId, String nickname) {
        long hash = vaultId * 0x9E3779B97F4A7C15L;
        for (int index = 0; index < nickname.length(); index++) {
            hash ^= nickname.charAt(index);
            hash *= 0x100000001B3L;
        }
        hash ^= hash >>> 33;
        hash *= 0xFF51AFD7ED558CCDL;
        hash ^= hash >>> 33;
        hash *= 0xC4CEB9FE1A85EC53L;
        return hash ^ hash >>> 33;
    }

    /**
     * 아직 지급되지 않은 이 플레이어의 영수증. 권위는 이 행을 승리 확정과 같은 영속
     * 경계에서 쓰고, 실제 지급은 두 번째 경계에서 {@code PENDING → GRANTED} 를 뒤집는다.
     */
    public static RaidRewardReceipt pending(long vaultId, String nickname, long recordedTick) {
        String token = rewardToken(nickname);
        return new RaidRewardReceipt(vaultId, token, rewardSeed(vaultId, nickname), nickname,
                recordedTick, RaidRewardReceipt.STATE_PENDING);
    }

    // ── 전리품 추첨 ────────────────────────────────────────────────
    // 구조는 `RaidVictoryReward` 와 같다: 시드 하나 · draw 자리마다 splitmix64 · 만분율.
    // 값만 트라이얼 챔버의 것이다. 정적판 `StandaloneTrialVault.ts` 가 같은 리터럴·같은
    // draw 순서를 쓰며 vitest 추적이 두 권위의 동일성을 고정한다.

    /**
     * 무지급 상한. 금고는 열쇠를 소비하므로 <b>항상</b> 무언가를 준다 — 0 이다.
     * 상수를 두는 이유는 레이드 보상과 같은 형태를 유지해 두 표를 나란히 읽게 하기 위함이다.
     */
    public static final int NOTHING_THRESHOLD = 0;
    /** 전리품 풀 종 수. */
    public static final int POOL_SIZE = 9;

    private static final int DRAW_POOL_SLOT = 0;
    private static final int DRAW_COUNT = 1;
    private static final int DRAW_GEAR_BASE = 2;
    private static final int DRAW_GEAR_ENCHANT = 3;
    private static final int DRAW_GEAR_LEVEL = 4;

    /** 풀 슬롯 서수. append-only 이며 바꾸면 과거 시드의 보상이 달라진다. */
    public static final int SLOT_EMERALD = 0;
    public static final int SLOT_IRON_INGOT = 1;
    public static final int SLOT_GOLD_INGOT = 2;
    public static final int SLOT_DIAMOND = 3;
    public static final int SLOT_GOLDEN_APPLE = 4;
    public static final int SLOT_HONEYCOMB = 5;
    public static final int SLOT_QUARTZ = 6;
    public static final int SLOT_ENCHANTED_GEAR = 7;
    /**
     * [GOLD-FOOD] 꿀이 든 병. 바닐라 트라이얼 챔버 금고/보상 상자에도 실제로 드는 항목이라
     * (Java 24%) 이 저장소에서 <b>가장 바닐라에 가까운</b> 꿀 획득 경로다 — 원천인 벌집·
     * 벌집 상자 블록이 없어 제작·채집으로는 얻을 수 없다. 낱개가 아니라 뭉치로 준다.
     */
    public static final int SLOT_HONEY_BOTTLE = 8;

    /** 낱개가 아니라 뭉치로 주는 슬롯의 최소 개수와 폭. */
    public static final int STACK_MIN_COUNT = 2;
    public static final int STACK_COUNT_SPREAD = 4;

    /**
     * 인챈트 장비 후보. 레이드 보상과 같은 이유로 기존 낮은 티어 장비만 담고, 각 행은
     * {@code {itemType, enchantId, maxLevel, enchantId, maxLevel}} 이다.
     */
    private static final int[][] GEAR_BASES = {
        {PlayerInventory.IRON_SWORD, EnchantmentRules.SHARPNESS, 5, EnchantmentRules.UNBREAKING, 3},
        {PlayerInventory.IRON_PICKAXE, EnchantmentRules.EFFICIENCY, 5, EnchantmentRules.FORTUNE, 3},
        {PlayerInventory.IRON_HELMET, EnchantmentRules.PROTECTION, 4,
                EnchantmentRules.UNBREAKING, 3},
        {PlayerInventory.BOW, EnchantmentRules.POWER, 5, EnchantmentRules.UNBREAKING, 3},
    };

    public static final int GEAR_BASE_COUNT = GEAR_BASES.length;
    public static final int GEAR_ENCHANT_COUNT = 2;

    /** 한 번의 개봉이 주는 전부. {@code itemType == 0} 이면 무지급이다. */
    public record Prize(short itemType, int count, int durability, long enchantments) {

        public boolean isNothing() {
            return itemType == PlayerInventory.EMPTY || count <= 0;
        }
    }

    public static final Prize NOTHING = new Prize((short) PlayerInventory.EMPTY, 0, 0, 0);

    static long stream(long rewardSeed, int drawIndex) {
        long z = rewardSeed + 0x9E3779B97F4A7C15L * (drawIndex + 1);
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    static int draw(long rewardSeed, int drawIndex, int bound) {
        return (int) Long.remainderUnsigned(stream(rewardSeed, drawIndex), bound);
    }

    /** 이 시드가 뽑는 보상. 같은 시드는 언제나 같은 {@link Prize} 다. */
    public static Prize roll(long rewardSeed) {
        return poolPrize(rewardSeed, draw(rewardSeed, DRAW_POOL_SLOT, POOL_SIZE));
    }

    static Prize poolPrize(long rewardSeed, int slot) {
        int bulk = STACK_MIN_COUNT + draw(rewardSeed, DRAW_COUNT, STACK_COUNT_SPREAD);
        return switch (slot) {
            case SLOT_EMERALD -> stack(PlayerInventory.EMERALD, bulk);
            case SLOT_IRON_INGOT -> stack(PlayerInventory.IRON_INGOT, bulk);
            case SLOT_GOLD_INGOT -> stack(PlayerInventory.GOLD_INGOT, bulk);
            case SLOT_DIAMOND -> stack(PlayerInventory.DIAMOND, 1);
            case SLOT_GOLDEN_APPLE -> stack(PlayerInventory.GOLDEN_APPLE, 1);
            case SLOT_HONEYCOMB -> stack(PlayerInventory.HONEYCOMB, bulk);
            case SLOT_QUARTZ -> stack(PlayerInventory.QUARTZ, bulk);
            case SLOT_ENCHANTED_GEAR -> enchantedGear(rewardSeed);
            case SLOT_HONEY_BOTTLE -> stack(PlayerInventory.HONEY_BOTTLE, bulk);
            default -> NOTHING;
        };
    }

    private static Prize stack(short itemType, int count) {
        return new Prize(itemType, count, PlayerInventory.initialDurability(itemType), 0);
    }

    private static Prize enchantedGear(long rewardSeed) {
        int[] base = GEAR_BASES[draw(rewardSeed, DRAW_GEAR_BASE, GEAR_BASE_COUNT)];
        int choice = draw(rewardSeed, DRAW_GEAR_ENCHANT, GEAR_ENCHANT_COUNT);
        int enchantId = base[1 + choice * 2];
        int maxLevel = base[2 + choice * 2];
        int level = 1 + draw(rewardSeed, DRAW_GEAR_LEVEL, maxLevel);
        short itemType = (short) base[0];
        return new Prize(itemType, 1, PlayerInventory.initialDurability(itemType),
                EnchantmentRules.withEnchantLevel(EnchantmentRules.EMPTY_ENCHANTMENTS,
                        enchantId, level));
    }

    /** 가드 테스트가 후보 표를 그대로 검증할 수 있게 노출한다. */
    public static int[][] gearBases() {
        int[][] copy = new int[GEAR_BASES.length][];
        for (int index = 0; index < GEAR_BASES.length; index++) {
            copy[index] = GEAR_BASES[index].clone();
        }
        return copy;
    }
}
