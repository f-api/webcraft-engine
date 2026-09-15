package com.gameexpert.engine.raid;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiPredicate;

/**
 * Durable membership, outcome and reward ledger for explicitly armed procedural raids.
 *
 * <p>The class is deliberately free of world, JPA and protocol types so the standalone Worker can
 * mirror it line by line ({@code StandaloneRaidLedger.ts}) and both authorities produce identical
 * wave counts, boss-bar numbers, statuses and reward seeds from the same observations.</p>
 *
 * <p>Contract sources are documented in {@code docs/MC-REFERENCE.md} "Raid 권위 계약":
 * status set {@code ONGOING/VICTORY/LOSS/STOPPED}, active time only advancing while the anchor is
 * entity-ticking, the 48000 MC tick abort, "생존 raider HP / total HP" boss bar, Vex being excluded
 * from roster/health/bossbar, and the {@code (worldId, raidId, rewardToken)} receipt boundary.</p>
 */
public final class RaidLedger {

    /** WebCraft 10 TPS mirror of the vanilla 48000 MC tick raid abort. */
    public static final int ACTIVE_TIMEOUT_TICKS = 2_400;
    /** Only one victory prize exists per raid identity; the token names that single grant slot. */
    public static final String VICTORY_REWARD_TOKEN = "raid_victory_v1";
    /** Bumped whenever persisted ledger semantics change; stale rows are dropped on load. */
    public static final int LEDGER_VERSION = 1;
    private static final long REWARD_SEED_TAG = 0x9E3779B97F4A7C15L;

    public enum Status { ONGOING, VICTORY, LOSS, STOPPED }

    /**
     * 바닐라 {@code Raid.MAX_CELEBRATION_TICKS}(600 MC 틱)의 10 TPS 환산. 승리·패배한 raid 는 이 시간
     * 동안 월드에 남아 {@code getRaidAt} 에 잡힌 뒤 {@code stop()} 된다.
     */
    public static final int CELEBRATION_TICKS = 300;
    /** 바닐라 {@code Raid.VALID_RAID_RADIUS_SQR}: {@code Raids.getNearbyRaid(pos, 9216)}. */
    public static final int VALID_RAID_RADIUS_SQR = 9_216;
    /** [RAID-OMEN] 바닐라 {@code Raid#getMaxRaidOmenLevel}. */
    public static final int MAX_RAID_OMEN_LEVEL = 5;
    /** [RAID-OMEN] 바닐라 {@code Raid.HERO_OF_THE_VILLAGE_DURATION}(MC 틱). */
    public static final int HERO_OF_THE_VILLAGE_MC_TICKS = 48_000;
    /** [RAID-OMEN] 바닐라 {@code Raid#getNumGroups(NORMAL)}·{@code (EASY)}: 약탈자·변명자 강화의 wave 문턱. */
    public static final int NUM_GROUPS_NORMAL = 5;
    public static final int NUM_GROUPS_EASY = 3;

    /** Outcome of one {@link #advance} call, so callers can broadcast/persist only on real edges. */
    public enum Transition { NONE, ARMED, WAVE_RELEASED, VICTORY, LOSS, STOPPED }

    private final Map<Long, Instance> instances = new LinkedHashMap<>();
    private final Map<Long, Long> raidIdByMember = new LinkedHashMap<>();

    /** One raid instance: identity, anchor, waves, all-time roster and terminal status. */
    public static final class Instance {
        private final long raidId;
        private final long anchorMobId;
        private final double centerX;
        private final double centerY;
        private final double centerZ;
        private final String heroNickname;
        private final long armedTick;
        private final long rewardSeed;
        private int waveCount;
        private int releasedWaves;
        private int activeTicks;
        private Status status = Status.ONGOING;
        private long resolvedTick = -1;
        private final Map<Long, Member> members = new LinkedHashMap<>();
        /** [RAID-OMEN] 바닐라 {@code raidOmenLevel}(0..5). 흡수한 습격의 징조 레벨의 합이다. */
        private int omenLevel = 1;
        /** [RAID-OMEN] 바닐라 {@code heroesOfTheVillage}: 레이더를 죽인 플레이어(닉네임, 들어온 순서). */
        private final java.util.LinkedHashSet<String> heroes = new java.util.LinkedHashSet<>();

        private Instance(long raidId, long anchorMobId, double centerX, double centerY,
                double centerZ, String heroNickname, long armedTick, int waveCount,
                long rewardSeed) {
            this.raidId = raidId;
            this.anchorMobId = anchorMobId;
            this.centerX = centerX;
            this.centerY = centerY;
            this.centerZ = centerZ;
            this.heroNickname = heroNickname;
            this.armedTick = armedTick;
            this.waveCount = Math.max(0, waveCount);
            this.rewardSeed = rewardSeed;
        }

        public long raidId() { return raidId; }
        public long anchorMobId() { return anchorMobId; }
        public double centerX() { return centerX; }
        public double centerY() { return centerY; }
        public double centerZ() { return centerZ; }
        public String heroNickname() { return heroNickname; }
        public long armedTick() { return armedTick; }
        public long rewardSeed() { return rewardSeed; }
        public int waveCount() { return waveCount; }
        public int releasedWaves() { return releasedWaves; }
        public int activeTicks() { return activeTicks; }
        public Status status() { return status; }
        public long resolvedTick() { return resolvedTick; }
        public boolean ongoing() { return status == Status.ONGOING; }
        public boolean started() { return releasedWaves > 0; }
        public boolean allWavesReleased() { return waveCount > 0 && releasedWaves >= waveCount; }
        public int omenLevel() { return omenLevel; }
        public List<String> heroes() { return List.copyOf(heroes); }

        /** [RAID-OMEN] 바닐라 {@code Raid#hasBonusWave}: 징조 레벨이 1 을 넘으면 마지막 뒤에 보너스 wave 가 있다. */
        public boolean bonusWave() { return omenLevel > 1; }

        /** [RAID-OMEN] 바닐라 {@code Raid#getEnchantOdds}: 레벨 2·3·4·5 → 0.1·0.25·0.5·0.75, 그 밖 0. */
        public float enchantOdds() {
            return RaidLedger.enchantOdds(omenLevel);
        }

        /** 레이더 한 명의 합류 때 굳힌 무기 마법 부여 레벨(없으면 0). */
        public int memberEnchantLevel(long mobId) {
            Member member = members.get(mobId);
            return member == null ? 0 : member.enchantLevel;
        }

        /**
         * 바닐라 {@code Raid#isBetweenWaves}: 첫 wave 가 나왔고 살아 있는 raider 가 없으며 다음 wave 를
         * 기다리는 중({@code raidCooldownTicks > 0})이다. WebCraft 의 다음 wave 대기는 "아직 방출하지
         * 않은 wave 가 남아 있음"이다.
         */
        public boolean betweenWaves() {
            return started() && liveMemberCount() == 0 && !allWavesReleased();
        }

        /**
         * 바닐라 {@code Raids.getNearbyRaid} 의 {@code raid.isActive()}: 진행 중이거나, 끝난 뒤 축하
         * 시간({@link #CELEBRATION_TICKS}) 안이다. STOPPED 는 월드에서 곧바로 빠진다.
         */
        public boolean presentAt(long worldTick) {
            return switch (status) {
                case ONGOING -> true;
                case VICTORY, LOSS -> worldTick - resolvedTick < CELEBRATION_TICKS;
                case STOPPED -> false;
            };
        }

        /** Current wave ordinal shown to players: 1-based, clamped to the planned wave count. */
        public int currentWave() {
            if (releasedWaves <= 0) return 0;
            return Math.min(waveCount, releasedWaves);
        }

        /** All-time roster ids in join order. Retired members stay for ledger reconciliation. */
        public List<Long> memberIds() {
            return List.copyOf(members.keySet());
        }

        public List<Long> liveMemberIds() {
            List<Long> live = new ArrayList<>();
            for (Member member : members.values()) {
                if (member.live()) live.add(member.mobId);
            }
            return live;
        }

        public int liveMemberCount() {
            int count = 0;
            for (Member member : members.values()) {
                if (member.live()) count++;
            }
            return count;
        }

        public double totalHealth() {
            double total = 0.0;
            for (Member member : members.values()) total += member.maxHealth;
            return total;
        }

        public double liveHealth() {
            double live = 0.0;
            for (Member member : members.values()) {
                if (member.live()) live += member.health;
            }
            return live;
        }

        /**
         * Vanilla combat boss bar is "생존 raider HP / total HP". Before the first wave lands the
         * roster is empty, and a resolved raid always reports an empty bar rather than a stale one.
         */
        public double bossbarProgress() {
            if (status != Status.ONGOING) return 0.0;
            double total = totalHealth();
            if (total <= 0.0) return 0.0;
            double ratio = liveHealth() / total;
            if (ratio < 0.0) return 0.0;
            return Math.min(1.0, ratio);
        }

        /** The bar exists only for a started, unresolved raid — the same rule on both authorities. */
        public boolean bossbarVisible() {
            return status == Status.ONGOING && releasedWaves > 0 && totalHealth() > 0.0;
        }

        public List<MemberSnapshot> memberSnapshots() {
            List<MemberSnapshot> snapshots = new ArrayList<>(members.size());
            for (Member member : members.values()) {
                snapshots.add(new MemberSnapshot(member.mobId, member.wave, member.maxHealth,
                        member.health, member.retired, member.enchantLevel));
            }
            return snapshots;
        }
    }

    private static final class Member {
        private final long mobId;
        private final int wave;
        private final double maxHealth;
        private double health;
        private boolean retired;
        /** [RAID-OMEN] 합류 때 굳힌 {@code applyRaidBuffs} 마법 부여 레벨(날카로움·빠른 장전, 0 = 없음). */
        private final int enchantLevel;

        private Member(long mobId, int wave, double maxHealth, double health, boolean retired,
                int enchantLevel) {
            this.mobId = mobId;
            this.wave = wave;
            this.maxHealth = maxHealth;
            this.health = health;
            this.retired = retired;
            this.enchantLevel = Math.max(0, enchantLevel);
        }

        private boolean live() { return !retired && health > 1e-9; }
    }

    /** Immutable per-member row for persistence and tests. */
    public record MemberSnapshot(long mobId, int wave, double maxHealth, double health,
            boolean retired, int enchantLevel) {
        /** [RAID-OMEN] 옛 행(마법 부여 칸 없음)은 0 이다. */
        public MemberSnapshot(long mobId, int wave, double maxHealth, double health, boolean retired) {
            this(mobId, wave, maxHealth, health, retired, 0);
        }
    }

    /** Immutable per-raid row for persistence. */
    public record InstanceSnapshot(int ledgerVersion, long raidId, long anchorMobId,
            double centerX, double centerY, double centerZ, String heroNickname,
            long armedTick, long rewardSeed, int waveCount, int releasedWaves,
            int activeTicks, String status, long resolvedTick,
            List<MemberSnapshot> members, int omenLevel, List<String> heroes) {
        /** [RAID-OMEN] 옛 행(징조 레벨·영웅 칸 없음)은 레벨 1·영웅 없음이다. 트라이얼 원장도 이 모양이다. */
        public InstanceSnapshot(int ledgerVersion, long raidId, long anchorMobId,
                double centerX, double centerY, double centerZ, String heroNickname,
                long armedTick, long rewardSeed, int waveCount, int releasedWaves,
                int activeTicks, String status, long resolvedTick, List<MemberSnapshot> members) {
            this(ledgerVersion, raidId, anchorMobId, centerX, centerY, centerZ, heroNickname, armedTick,
                    rewardSeed, waveCount, releasedWaves, activeTicks, status, resolvedTick, members, 1,
                    List.of());
        }
    }

    /** [RAID-OMEN] 바닐라 {@code Raid#getEnchantOdds}. */
    public static float enchantOdds(int omenLevel) {
        return switch (omenLevel) {
            case 2 -> 0.1f;
            case 3 -> 0.25f;
            case 4 -> 0.5f;
            case 5 -> 0.75f;
            default -> 0.0f;
        };
    }

    /**
     * [RAID-OMEN] 바닐라 {@code Pillager#applyRaidBuffs}·{@code Vindicator#applyRaidBuffs}: {@code nextFloat() <=
     * getEnchantOdds()} 이면 약탈자 쇠뇌는 wave &gt; 5 에 빠른 장전 II({@code raid/pillager_post_wave_5}),
     * wave &gt; 3 에 빠른 장전 I({@code raid/pillager_post_wave_3}), 그 밖 없음; 변명자 도끼는 wave &gt; 5 에
     * 날카로움 II, 그 밖 날카로움 I 이다. 난수는 (raid, 몹) 결정적 해시의 float(두 권위 동일)다.
     *
     * @return 마법 부여 레벨(0 = 없음)
     */
    public static int raidBuffEnchantLevel(com.gameexpert.engine.mob.MobType type, int wave, int omenLevel,
            long raidId, long mobId) {
        if (type != com.gameexpert.engine.mob.MobType.PILLAGER
                && type != com.gameexpert.engine.mob.MobType.VINDICATOR) {
            return 0;
        }
        float odds = enchantOdds(omenLevel);
        if (!(buffRoll(raidId, mobId) <= odds)) return 0;
        if (type == com.gameexpert.engine.mob.MobType.PILLAGER) {
            if (wave > NUM_GROUPS_NORMAL) return 2;
            return wave > NUM_GROUPS_EASY ? 1 : 0;
        }
        return wave > NUM_GROUPS_NORMAL ? 2 : 1;
    }

    /** (raid, 몹) 결정적 [0,1) float: 24 비트 해시 / 2^24(바닐라 nextFloat 과 같은 분해능). */
    static float buffRoll(long raidId, long mobId) {
        long h = raidId * 0x9E3779B97F4A7C15L ^ mobId * 0xC2B2AE3D27D4EB4FL;
        h ^= h >>> 33;
        h *= 0xFF51AFD7ED558CCDL;
        h ^= h >>> 33;
        return (int) (h >>> 40) * 5.9604645E-8f;
    }

    /**
     * Deterministic reward roll seed. It is fixed when the raid is armed and copied into the
     * victory receipt, so a crash before payout can never reroll the prize.
     */
    public static long rewardSeed(long worldSeed, long raidId) {
        long hash = worldSeed * REWARD_SEED_TAG;
        hash ^= raidId + 0x165667B19E3779F9L + (hash << 6) + (hash >>> 2);
        hash ^= hash >>> 33;
        hash *= 0xFF51AFD7ED558CCDL;
        hash ^= hash >>> 33;
        hash *= 0xC4CEB9FE1A85EC53L;
        return hash ^ hash >>> 33;
    }

    /** Arms one ledger entry. Repeated arming of the same raid identity is a no-op. */
    public Instance arm(long raidId, long anchorMobId, double centerX, double centerY,
            double centerZ, String heroNickname, long armedTick, int waveCount, long worldSeed) {
        return arm(raidId, anchorMobId, centerX, centerY, centerZ, heroNickname, armedTick, waveCount,
                worldSeed, 1);
    }

    /**
     * [RAID-OMEN] 징조 레벨을 싣고 arm 한다. 바닐라 {@code Raids.createOrExtendRaid} → {@code absorbRaidOmen}:
     * 새 레이드의 레벨은 0 에서 시작해 흡수한 효과의 {@code amplifier + 1} 을 더한다(최대 5).
     */
    public Instance arm(long raidId, long anchorMobId, double centerX, double centerY,
            double centerZ, String heroNickname, long armedTick, int waveCount, long worldSeed,
            int omenLevel) {
        Instance existing = instances.get(raidId);
        if (existing != null) return existing;
        Instance instance = new Instance(raidId, anchorMobId, centerX, centerY, centerZ,
                heroNickname, armedTick, waveCount, rewardSeed(worldSeed, raidId));
        instance.omenLevel = Math.max(0, Math.min(MAX_RAID_OMEN_LEVEL, omenLevel));
        instances.put(raidId, instance);
        return instance;
    }

    /**
     * [RAID-OMEN] 바닐라 {@code Raids.createOrExtendRaid}: 이미 있는 레이드는 시작 전이거나 레벨이 최대 미만일 때
     * {@code absorbRaidOmen} 으로 {@code amplifier + 1} 을 더한다(0..5 로 자른다).
     *
     * @return 흡수했으면 true
     */
    public boolean absorbOmen(long raidId, int amplifier) {
        Instance instance = instances.get(raidId);
        if (instance == null || !instance.ongoing()) return false;
        if (instance.started() && instance.omenLevel >= MAX_RAID_OMEN_LEVEL) return false;
        instance.omenLevel = Math.max(0, Math.min(MAX_RAID_OMEN_LEVEL,
                instance.omenLevel + Math.max(0, amplifier) + 1));
        return true;
    }

    /** [RAID-OMEN] 바닐라 {@code Raid#addHeroOfTheVillage}: 레이더를 죽인 플레이어. */
    public void addHero(long memberMobId, String nickname) {
        if (nickname == null || nickname.isBlank()) return;
        Instance instance = instanceOfMember(memberMobId);
        if (instance == null || !instance.ongoing()) return;
        instance.heroes.add(nickname);
    }

    public Instance instance(long raidId) {
        return instances.get(raidId);
    }

    /**
     * [TRIAL-GAP] 진행 중인 원장의 총 웨이브 수를 올린다(내리지 않는다). 트라이얼 스포너는 바닐라
     * {@code hasFinishedSpawningAllMobs(config, additionalPlayers)} 처럼 매 틱 감지 인원으로 목표
     * 총 소환 수를 다시 셈하고, ACTIVE 동안 감지 집합은 늘기만 하므로 목표도 늘기만 한다.
     * 레이드는 이 메서드를 쓰지 않는다. 정적판 사본은 {@code StandaloneRaidLedger.raiseWaveCount}.
     */
    public boolean raiseWaveCount(long raidId, int waveCount) {
        Instance instance = instances.get(raidId);
        if (instance == null || !instance.ongoing() || waveCount <= instance.waveCount) return false;
        instance.waveCount = waveCount;
        return true;
    }

    public Collection<Instance> instances() {
        return instances.values();
    }

    /** The single unresolved raid, or null. WebCraft arms at most one raid at a time. */
    public Instance ongoing() {
        for (Instance instance : instances.values()) {
            if (instance.ongoing()) return instance;
        }
        return null;
    }

    /**
     * 바닐라 {@code ServerLevel.getRaidAt(pos)} = {@code Raids.getNearbyRaid(pos, 9216)}: 중심 블록과의
     * {@code distSqr} 가 9216 미만인 활성 raid 중 가장 가까운 것(같으면 먼저 arm 된 것), 없으면 null.
     */
    public Instance raidAt(int blockX, int blockY, int blockZ, long worldTick) {
        Instance best = null;
        double bestDistance = VALID_RAID_RADIUS_SQR;
        for (Instance instance : instances.values()) {
            if (!instance.presentAt(worldTick)) continue;
            double dx = Math.floor(instance.centerX) - blockX;
            double dy = Math.floor(instance.centerY) - blockY;
            double dz = Math.floor(instance.centerZ) - blockZ;
            double distance = dx * dx + dy * dy + dz * dz;
            if (distance < bestDistance) {
                best = instance;
                bestDistance = distance;
            }
        }
        return best;
    }

    public Long raidIdOfMember(long mobId) {
        return raidIdByMember.get(mobId);
    }

    /** Highest persisted entity id referenced by an anchor or roster row. */
    public long highestReferencedMobId() {
        long highest = 0L;
        for (Instance instance : instances.values()) {
            highest = Math.max(highest, instance.anchorMobId);
            for (long memberId : instance.members.keySet()) highest = Math.max(highest, memberId);
        }
        return highest;
    }

    /**
     * Brands one released raider into the roster. {@code wave} is the 1-based wave that released it
     * and also advances the released-wave counter, because a wave is only observable through the
     * members it actually put on the ground.
     */
    public boolean join(long raidId, long mobId, int wave, double maxHealth) {
        return join(raidId, mobId, wave, maxHealth, 0);
    }

    /** [RAID-OMEN] 합류 때 굳힌 {@link #raidBuffEnchantLevel} 을 함께 새긴다. */
    public boolean join(long raidId, long mobId, int wave, double maxHealth, int enchantLevelOnJoin) {
        Instance instance = instances.get(raidId);
        if (instance == null || !instance.ongoing()) return false;
        if (instance.members.containsKey(mobId)) return false;
        if (!(maxHealth > 0.0)) return false;
        instance.members.put(mobId, new Member(mobId, wave, maxHealth, maxHealth, false,
                enchantLevelOnJoin));
        raidIdByMember.put(mobId, raidId);
        if (wave > instance.releasedWaves) {
            instance.releasedWaves = Math.min(instance.waveCount, wave);
        }
        return true;
    }

    /**
     * A wave whose spawn candidates were all rejected still counts as released; otherwise a raid
     * could never reach victory when terrain refuses one squad.
     */
    public void markWaveReleased(long raidId, int wave) {
        Instance instance = instances.get(raidId);
        if (instance == null || !instance.ongoing()) return;
        if (wave > instance.releasedWaves) {
            instance.releasedWaves = Math.min(instance.waveCount, wave);
        }
    }

    /** Records the current health of a loaded, living member. Unloaded members are never observed. */
    public void observeMember(long mobId, double health) {
        Instance instance = instanceOfMember(mobId);
        if (instance == null) return;
        Member member = instance.members.get(mobId);
        if (member == null || member.retired) return;
        member.health = Math.max(0.0, Math.min(member.maxHealth, health));
    }

    /**
     * Removes a member from the live roster for good. This is the death/despawn edge: an unloaded
     * chunk must not call it, because the raider is still persisted and will rejoin on reload.
     */
    public void retireMember(long mobId) {
        Instance instance = instanceOfMember(mobId);
        if (instance == null) return;
        Member member = instance.members.get(mobId);
        if (member == null) return;
        member.retired = true;
        member.health = 0.0;
        raidIdByMember.remove(mobId, instance.raidId);
    }

    /**
     * Reconciles a restored ledger after every durable mob row has been loaded. A crash can commit
     * the mob deletion before the later ledger snapshot; leaving that live roster row indexed lets
     * the monotonic allocator's next mob inherit an unrelated raid membership.
     */
    public void retireMissingMembers(BiPredicate<Long, Long> restoredMember) {
        for (Instance instance : instances.values()) {
            for (Member member : instance.members.values()) {
                if (!member.live() || restoredMember.test(instance.raidId, member.mobId)) continue;
                member.retired = true;
                member.health = 0.0;
                raidIdByMember.remove(member.mobId, instance.raidId);
            }
        }
    }

    /**
     * Reattaches a persisted raider after a reload. Health comes from the restored mob row, so the
     * boss bar resumes at the same number instead of being recomputed from full health.
     */
    public boolean rejoin(long raidId, long mobId, double health) {
        Instance instance = instances.get(raidId);
        if (instance == null || !instance.ongoing()) return false;
        Member member = instance.members.get(mobId);
        if (member == null) return false;
        member.retired = false;
        member.health = Math.max(0.0, Math.min(member.maxHealth, health));
        raidIdByMember.put(mobId, raidId);
        return true;
    }

    /**
     * Advances one authority tick of the raid state machine.
     *
     * @param anchorPresent the anchor villager still exists in the authority (alive, not removed)
     * @param anchorTicking the anchor is inside an entity-ticking (active) chunk this tick
     */
    public Transition advance(long raidId, long worldTick, boolean anchorPresent,
            boolean anchorTicking) {
        Instance instance = instances.get(raidId);
        if (instance == null || !instance.ongoing()) return Transition.NONE;
        if (anchorTicking) instance.activeTicks++;
        if (instance.activeTicks >= ACTIVE_TIMEOUT_TICKS) {
            return resolve(instance, Status.STOPPED, worldTick, Transition.STOPPED);
        }
        if (!anchorPresent) {
            // Vanilla `Raid.tick`: a center that is no longer a village is a LOSS once groups have
            // spawned and a plain stop otherwise. The WebCraft anchor is that village fact.
            return instance.started()
                    ? resolve(instance, Status.LOSS, worldTick, Transition.LOSS)
                    : resolve(instance, Status.STOPPED, worldTick, Transition.STOPPED);
        }
        if (instance.allWavesReleased() && instance.liveMemberCount() == 0) {
            return resolve(instance, Status.VICTORY, worldTick, Transition.VICTORY);
        }
        return Transition.NONE;
    }

    private Transition resolve(Instance instance, Status status, long worldTick,
            Transition transition) {
        instance.status = status;
        instance.resolvedTick = worldTick;
        for (Member member : instance.members.values()) {
            member.retired = true;
            member.health = 0.0;
            raidIdByMember.remove(member.mobId, instance.raidId);
        }
        return transition;
    }

    /** Forgets a resolved raid and its member index once its receipt has been persisted. */
    public void forget(long raidId) {
        Instance removed = instances.remove(raidId);
        if (removed == null) return;
        for (Long mobId : removed.members.keySet()) {
            raidIdByMember.remove(mobId, raidId);
        }
    }

    public List<InstanceSnapshot> snapshot() {
        List<InstanceSnapshot> rows = new ArrayList<>(instances.size());
        for (Instance instance : instances.values()) {
            rows.add(new InstanceSnapshot(LEDGER_VERSION, instance.raidId, instance.anchorMobId,
                    instance.centerX, instance.centerY, instance.centerZ, instance.heroNickname,
                    instance.armedTick, instance.rewardSeed, instance.waveCount,
                    instance.releasedWaves, instance.activeTicks, instance.status.name(),
                    instance.resolvedTick, instance.memberSnapshots(), instance.omenLevel,
                    List.copyOf(instance.heroes)));
        }
        return rows;
    }

    /** Restores persisted rows. Unknown versions and malformed rows are skipped, never guessed. */
    public void restore(Collection<InstanceSnapshot> rows) {
        if (rows == null) return;
        for (InstanceSnapshot row : rows) {
            if (row == null || row.ledgerVersion() != LEDGER_VERSION) continue;
            Status status;
            try {
                status = Status.valueOf(row.status());
            } catch (IllegalArgumentException | NullPointerException ignored) {
                continue;
            }
            Instance instance = new Instance(row.raidId(), row.anchorMobId(), row.centerX(),
                    row.centerY(), row.centerZ(), row.heroNickname(), row.armedTick(),
                    row.waveCount(), row.rewardSeed());
            instance.releasedWaves = Math.max(0, Math.min(row.waveCount(), row.releasedWaves()));
            instance.activeTicks = Math.max(0, row.activeTicks());
            instance.status = status;
            instance.resolvedTick = row.resolvedTick();
            instance.omenLevel = Math.max(1, Math.min(MAX_RAID_OMEN_LEVEL, row.omenLevel()));
            if (row.heroes() != null) {
                for (String hero : row.heroes()) {
                    if (hero != null && !hero.isBlank()) instance.heroes.add(hero);
                }
            }
            if (row.members() != null) {
                for (MemberSnapshot member : row.members()) {
                    if (member == null || !(member.maxHealth() > 0.0)) continue;
                    double health = Math.max(0.0, Math.min(member.maxHealth(), member.health()));
                    instance.members.put(member.mobId(), new Member(member.mobId(), member.wave(),
                            member.maxHealth(), health, member.retired(), member.enchantLevel()));
                    if (!member.retired() && health > 1e-9) {
                        raidIdByMember.put(member.mobId(), row.raidId());
                    }
                }
            }
            instances.put(row.raidId(), instance);
        }
    }

    private Instance instanceOfMember(long mobId) {
        Long raidId = raidIdByMember.get(mobId);
        return raidId == null ? null : instances.get(raidId);
    }
}
