package com.gameexpert.engine.raid;

import java.util.ArrayList;
import java.util.List;

import com.gameexpert.engine.mob.Mob;
import com.gameexpert.engine.mob.MobType;
import com.gameexpert.engine.mob.MobWorldView;
import com.gameexpert.engine.mob.PlayerSnapshot;
import com.gameexpert.engine.mob.SpawnRequest;
import com.gameexpert.engine.mob.IllagerCompanionPolicy;
import com.gameexpert.engine.Fluids;
import com.gameexpert.terrain.Blocks;

/**
 * Execution schedule for one explicitly armed procedural raid grammar. One squad owns one exact
 * release tick, while its composition and formation remain a pure function of the armed event
 * identity and village anchor. Merely standing near a village never creates a raid.
 */
public final class ProceduralRaidSchedule {
    public static final int EPOCH_TICKS = 4_096;
    public static final double ACTIVATION_RANGE = 48.0;
    private static final int GENERATOR_VERSION = 1;
    private static final int QA_MAX_IDENTITY_PROBES = 4_096;
    private static final int QA_MIN_DEFENDER_POWER = 8;
    private static final long QA_IDENTITY_STRIDE = 0x632be59bd9b4e019L;
    private long cachedEpoch = Long.MIN_VALUE;
    private long cachedAnchorId = Long.MIN_VALUE;
    private int cachedWorldSeed;
    private long cachedRaidId;
    private RaidPlanGenerator.Plan cachedPlan;
    private long armedAnchorId = Long.MIN_VALUE;
    private long armedStartTick = Long.MIN_VALUE;
    private String armedHeroNickname;
    private int armedRequiredRoleMask;
    private int armedRequiredRoleWindowTicks;
    /** [RAID-OMEN] arm 때 흡수한 습격의 징조 레벨(1..5). 원장이 그 뒤의 흡수를 소유한다. */
    private int armedOmenLevel = 1;
    private boolean ledgerArmed;

    public ProceduralRaidSchedule() {}

    /** Arms one exact village raid after Raid Omen finishes; repeated calls cannot duplicate it. */
    public boolean arm(long anchorId, long worldTick) {
        return arm(anchorId, worldTick, null);
    }

    /**
     * @param heroNickname the Raid Omen player whose entry authorized the raid. It becomes the
     *     victory receipt recipient, mirroring the vanilla persisted hero UUID.
     */
    public boolean arm(long anchorId, long worldTick, String heroNickname) {
        return arm(anchorId, worldTick, heroNickname, 1);
    }

    /**
     * [RAID-OMEN] 징조 레벨을 싣고 arm 한다(바닐라 {@code absorbRaidOmen}: 0 + amplifier + 1). 레벨이 1 을 넘으면
     * 계획의 마지막 단계 뒤에 보너스 wave 가 붙는다({@code Raid#hasBonusWave}).
     */
    public boolean arm(long anchorId, long worldTick, String heroNickname, int omenLevel) {
        if (!armInternal(anchorId, worldTick, heroNickname, 0, 0)) return false;
        armedOmenLevel = Math.max(1, Math.min(RaidLedger.MAX_RAID_OMEN_LEVEL, omenLevel));
        return true;
    }

    /**
     * Arms the real procedural schedule for the reserved content-QA world while constraining its
     * deterministic identity to one whose ordinary squad releases prove the requested roles in
     * the receipt window. No role is injected: every member still comes from the selected grammar
     * and the normal terrain-valid spawn path.
     */
    public boolean armQa(long anchorId, long worldTick, String heroNickname,
            int requiredRoleMask, int releaseWindowTicks) {
        int legalMask = (1 << RaidPlanGenerator.ROLE_COUNT) - 1;
        if (requiredRoleMask <= 0 || (requiredRoleMask & ~legalMask) != 0
                || releaseWindowTicks <= 1 || releaseWindowTicks > EPOCH_TICKS) return false;
        return armInternal(anchorId, worldTick, heroNickname,
                requiredRoleMask, releaseWindowTicks);
    }

    private boolean armInternal(long anchorId, long worldTick, String heroNickname,
            int requiredRoleMask, int releaseWindowTicks) {
        if (armedStartTick != Long.MIN_VALUE) return false;
        armedAnchorId = anchorId;
        armedStartTick = worldTick;
        armedHeroNickname = heroNickname;
        armedRequiredRoleMask = requiredRoleMask;
        armedRequiredRoleWindowTicks = releaseWindowTicks;
        armedOmenLevel = 1;
        ledgerArmed = false;
        cachedEpoch = Long.MIN_VALUE;
        return true;
    }

    /** The raid identity currently armed, or 0 before the grammar has been resolved. */
    public long armedRaidId() {
        return ledgerArmed ? cachedRaidId : 0L;
    }

    /** True only for the still-armed QA constraint that selected this schedule identity. */
    public boolean qaConstraintMatches(long anchorId, int requiredRoleMask,
            int releaseWindowTicks) {
        return armedStartTick != Long.MIN_VALUE && armedAnchorId == anchorId
                && armedRequiredRoleMask == requiredRoleMask
                && armedRequiredRoleWindowTicks == releaseWindowTicks;
    }

    /**
     * Frees the arm slot the moment the armed raid reaches a terminal status.
     *
     * <p>Vanilla has no per-village raid cooldown: {@code Raids.tick} drops a stopped raid from the
     * world and the next Raid Omen creates a new one at the same center. Without this release the
     * slot survived a normal VICTORY/LOSS until the 4096-tick epoch ran out, so every Raid Omen
     * that expired in between was burned for nothing.</p>
     *
     * <p>A forgotten instance ({@link RaidLedger#forget}) counts as resolved: it is only forgotten
     * after its receipt has been persisted.</p>
     *
     * @return true when an armed slot was released by this call.
     */
    public boolean releaseResolved(RaidLedger ledger) {
        if (ledger == null || armedStartTick == Long.MIN_VALUE || !ledgerArmed) return false;
        RaidLedger.Instance armed = ledger.instance(cachedRaidId);
        if (armed != null && armed.ongoing()) return false;
        disarm();
        return true;
    }

    private void disarm() {
        armedStartTick = Long.MIN_VALUE;
        armedAnchorId = Long.MIN_VALUE;
        armedHeroNickname = null;
        armedRequiredRoleMask = 0;
        armedRequiredRoleWindowTicks = 0;
        armedOmenLevel = 1;
        ledgerArmed = false;
    }

    public List<SpawnRequest> releases(MobWorldView world, List<Mob> activeMobs,
            long worldTick) {
        return releases(world, activeMobs, worldTick, null);
    }

    /**
     * @param ledger optional membership ledger. When present the schedule arms one ledger entry as
     *     soon as the grammar resolves and marks every wave whose release tick has arrived, even if
     *     terrain rejected all of that wave's candidates — otherwise a raid could never win.
     */
    public List<SpawnRequest> releases(MobWorldView world, List<Mob> activeMobs,
            long worldTick, RaidLedger ledger) {
        if (armedStartTick == Long.MIN_VALUE) return List.of();
        // A raid that already ended releases its village here too, so a schedule whose owner never
        // observes the resolving transition still cannot outlive its own raid.
        if (releaseResolved(ledger)) return List.of();
        long epoch = armedStartTick;
        long elapsed = worldTick - armedStartTick;
        if (elapsed < 0) return List.of();
        // The epoch must expire even when the anchor is not in this tick's active mob list. The
        // anchor disappears whenever the village villager dies or its chunk stops ticking, and an
        // arm slot that only expires while the anchor is present would stay armed forever, so
        // `arm` would refuse every later Raid Omen for the rest of the world's lifetime.
        // [RAID-OMEN] An ongoing raid still owed its bonus wave keeps the slot until it is released.
        if (elapsed >= EPOCH_TICKS && !bonusWavePending(ledger)) {
            disarm();
            return List.of();
        }
        Mob anchor = mobById(activeMobs, armedAnchorId);
        if (anchor == null) return List.of();
        int offset = (int) elapsed;
        RaidPlanGenerator.Plan plan = planFor(world, activeMobs, anchor, epoch);
        if (plan == null || !plan.isPlannable()) return List.of();
        if (ledger != null && !ledgerArmed) {
            ledger.arm(cachedRaidId, anchor.id, anchor.x, anchor.y, anchor.z, armedHeroNickname,
                    armedStartTick, plan.phaseCount() + (armedOmenLevel > 1 ? 1 : 0), world.worldSeed(),
                    armedOmenLevel);
            ledgerArmed = true;
        }

        List<SpawnRequest> requests = null;
        for (int phaseIndex = 0; phaseIndex < plan.phaseCount(); phaseIndex++) {
            RaidPlanGenerator.Phase phase = plan.phase(phaseIndex);
            int wave = phaseIndex + 1;
            for (int squadIndex = 0; squadIndex < phase.squadCount(); squadIndex++) {
                RaidPlanGenerator.Squad squad = phase.squad(squadIndex);
                if (phase.releaseTick() + squad.releaseOffset() != offset) continue;
                if (ledger != null) ledger.markWaveReleased(cachedRaidId, wave);
                int unitOrdinal = 0;
                for (int role = 0; role < RaidPlanGenerator.ROLE_COUNT; role++) {
                    int roleCount = squad.roleCount(role);
                    for (int member = 0; member < roleCount; member++, unitOrdinal++) {
                        MobType type = roleType(role);
                        SpawnRequest request = spawnRequest(world, anchor, cachedRaidId, squad,
                                unitOrdinal, Math.max(1, squad.unitCount()), type, wave);
                        if (request == null) continue;
                        if (requests == null) requests = new ArrayList<>(squad.unitCount());
                        requests.add(request);
                    }
                }
            }
        }
        List<SpawnRequest> bonus = bonusWaveReleases(world, anchor, plan, offset, ledger);
        if (!bonus.isEmpty()) {
            if (requests == null) requests = new ArrayList<>(bonus.size());
            requests.addAll(bonus);
        }
        return requests == null ? List.of() : requests;
    }

    /** [RAID-OMEN] 원장에 보너스 wave 가 아직 남아 있는가. */
    private boolean bonusWavePending(RaidLedger ledger) {
        if (ledger == null || !ledgerArmed || cachedPlan == null) return false;
        RaidLedger.Instance armed = ledger.instance(cachedRaidId);
        return armed != null && armed.ongoing() && armed.bonusWave()
                && armed.releasedWaves() <= cachedPlan.phaseCount();
    }

    /**
     * [RAID-OMEN] 바닐라 {@code Raid#shouldSpawnBonusGroup}: 마지막 wave 가 나왔고({@code isFinalWave}) 살아 있는
     * 레이더가 없으며 징조 레벨이 1 을 넘으면 한 wave 를 더 부른다. 바닐라 보너스 wave 는
     * {@code spawnsPerWaveBeforeBonus[numGroups]}(정규 마지막보다 한 칸 뒤의 표)를 쓰므로, 문법 레이드는 마지막
     * 단계의 분대 구성을 한 번 더 같은 전개로 부른다. 흡수로 레벨이 뒤늦게 1 을 넘으면 원장의 wave 수도 늘린다.
     */
    private List<SpawnRequest> bonusWaveReleases(MobWorldView world, Mob anchor,
            RaidPlanGenerator.Plan plan, int offset, RaidLedger ledger) {
        if (ledger == null || !ledgerArmed) return List.of();
        RaidLedger.Instance armed = ledger.instance(cachedRaidId);
        if (armed == null || !armed.ongoing() || !armed.bonusWave()) return List.of();
        int bonusWave = plan.phaseCount() + 1;
        if (armed.waveCount() < bonusWave) ledger.raiseWaveCount(cachedRaidId, bonusWave);
        if (armed.releasedWaves() != plan.phaseCount() || offset <= lastReleaseOffset(plan)
                || armed.liveMemberCount() != 0) {
            return List.of();
        }
        ledger.markWaveReleased(cachedRaidId, bonusWave);
        RaidPlanGenerator.Phase last = plan.phase(plan.phaseCount() - 1);
        List<SpawnRequest> requests = new ArrayList<>();
        for (int squadIndex = 0; squadIndex < last.squadCount(); squadIndex++) {
            RaidPlanGenerator.Squad squad = last.squad(squadIndex);
            int unitOrdinal = 0;
            for (int role = 0; role < RaidPlanGenerator.ROLE_COUNT; role++) {
                int roleCount = squad.roleCount(role);
                for (int member = 0; member < roleCount; member++, unitOrdinal++) {
                    SpawnRequest request = spawnRequest(world, anchor, cachedRaidId, squad, unitOrdinal,
                            Math.max(1, squad.unitCount()), roleType(role), bonusWave);
                    if (request != null) requests.add(request);
                }
            }
        }
        return requests;
    }

    /** 계획의 모든 분대 중 가장 늦은 방출 오프셋. */
    static int lastReleaseOffset(RaidPlanGenerator.Plan plan) {
        int last = 0;
        for (int phaseIndex = 0; phaseIndex < plan.phaseCount(); phaseIndex++) {
            RaidPlanGenerator.Phase phase = plan.phase(phaseIndex);
            for (int squadIndex = 0; squadIndex < phase.squadCount(); squadIndex++) {
                last = Math.max(last, phase.releaseTick() + phase.squad(squadIndex).releaseOffset());
            }
        }
        return last;
    }

    /**
     * A selected raid grammar is immutable for its epoch and village anchor. Generating it on
     * every 10 TPS authority tick used to rebuild hundreds of arrays for several minutes even
     * though almost every tick had no due squad. This cache also freezes defender facts at the
     * event boundary, so a later join cannot rewrite already-released phases.
     */
    private RaidPlanGenerator.Plan planFor(MobWorldView world, List<Mob> activeMobs,
            Mob anchor, long epoch) {
        int seed = world.worldSeed();
        if (cachedEpoch == epoch && cachedAnchorId == anchor.id && cachedWorldSeed == seed) {
            return cachedPlan;
        }
        cachedEpoch = epoch;
        cachedAnchorId = anchor.id;
        cachedWorldSeed = seed;
        long baseRaidId = epoch ^ anchor.id * 0x9e3779b97f4a7c15L;
        int defenders = 0;
        int defenderPower = 0;
        for (PlayerSnapshot player : world.players()) {
            if (player.alive()) {
                defenders++;
                defenderPower += 2;
            }
        }
        for (Mob mob : activeMobs) {
            if (mob.type == MobType.IRON_GOLEM && !mob.isDead() && !mob.removed) {
                defenders++;
                defenderPower += 3;
            }
        }
        int admittedDefenders = Math.max(1, defenders);
        int admittedPower = Math.max(1, defenderPower);
        if (armedRequiredRoleMask == 0) {
            cachedRaidId = baseRaidId;
            cachedPlan = generatePlan(seed, cachedRaidId, anchor.id,
                    admittedDefenders, admittedPower);
            return cachedPlan;
        }
        // The reserved fixture equips the defender for every H12 interaction. Give that explicit
        // QA premise enough grammar budget, then select an identity rather than adding members.
        admittedPower = Math.max(admittedPower, QA_MIN_DEFENDER_POWER);
        for (int probe = 0; probe < QA_MAX_IDENTITY_PROBES; probe++) {
            long candidateId = baseRaidId + QA_IDENTITY_STRIDE * probe;
            RaidPlanGenerator.Plan candidate = generatePlan(seed, candidateId, anchor.id,
                    admittedDefenders, admittedPower);
            if (releasesRequiredRolesWithin(candidate, armedRequiredRoleMask,
                    armedRequiredRoleWindowTicks)) {
                cachedRaidId = candidateId;
                cachedPlan = candidate;
                return cachedPlan;
            }
        }
        cachedRaidId = 0L;
        cachedPlan = null;
        return cachedPlan;
    }

    private static RaidPlanGenerator.Plan generatePlan(int seed, long raidId, long anchorId,
            int defenders, int defenderPower) {
        return RaidPlanGenerator.generate(new RaidPlanGenerator.Input(
                GENERATOR_VERSION, seed, raidId, anchorId, defenders, defenderPower,
                RaidPlanGenerator.APPROACH_MASK, 1, 8, true, 4));
    }

    static boolean releasesRequiredRolesWithin(RaidPlanGenerator.Plan plan,
            int requiredRoleMask, int releaseWindowTicks) {
        if (plan == null || !plan.isPlannable()) return false;
        int releasedMask = 0;
        for (RaidPlanGenerator.Phase phase : plan.phases()) {
            for (RaidPlanGenerator.Squad squad : phase.squads()) {
                int releaseTick = phase.releaseTick() + squad.releaseOffset();
                // Offset zero can already have passed when a QA request is handled after mob tick.
                if (releaseTick <= 0 || releaseTick >= releaseWindowTicks) continue;
                for (int role = 0; role < RaidPlanGenerator.ROLE_COUNT; role++) {
                    if (squad.roleCount(role) > 0) releasedMask |= 1 << role;
                }
            }
        }
        return (releasedMask & requiredRoleMask) == requiredRoleMask;
    }

    private static Mob mobById(List<Mob> activeMobs, long id) {
        for (Mob mob : activeMobs) {
            if (mob.id == id && !mob.isDead() && !mob.removed) return mob;
        }
        return null;
    }

    private static SpawnRequest spawnRequest(MobWorldView world, Mob anchor, long raidId,
            RaidPlanGenerator.Squad squad, int ordinal, int unitCount, MobType type, int wave) {
        int approachX = squad.approach() == RaidPlanGenerator.APPROACH_EAST ? 1
                : squad.approach() == RaidPlanGenerator.APPROACH_WEST ? -1 : 0;
        int approachZ = squad.approach() == RaidPlanGenerator.APPROACH_SOUTH ? 1
                : squad.approach() == RaidPlanGenerator.APPROACH_NORTH ? -1 : 0;
        int lateralX = -approachZ;
        int lateralZ = approachX;
        int hash = mix32((int) raidId ^ squad.squadIndex() * 0x632be5ab
                ^ ordinal * 0x85157af5);
        int radius = 18 + Integer.remainderUnsigned(hash, 7);
        int lateral = (ordinal * 2 - unitCount + 1) * 2;
        int x = (int) Math.floor(anchor.x) + approachX * radius + lateralX * lateral;
        int z = (int) Math.floor(anchor.z) + approachZ * radius + lateralZ * lateral;
        if (!world.isChunkActive(Math.floorDiv(x, 16), Math.floorDiv(z, 16))) return null;
        int y = world.motionBlockingNoLeavesHeight(x, z) + 1;
        if (type == MobType.VEX) y = Math.min(Blocks.MAX_Y - 2, y + 2);
        if (!validSpawnCell(world, type, x + 0.5, y, z + 0.5)) return null;
        return new SpawnRequest(type, x + 0.5, y, z + 0.5,
                IllagerCompanionPolicy.Context.RAID, raidId, wave);
    }

    private static boolean validSpawnCell(MobWorldView world, MobType type,
            double x, int y, double z) {
        if (y <= Blocks.MIN_Y || y + type.height() >= Blocks.MAX_Y) return false;
        double half = type.width() * 0.5;
        int minX = (int) Math.floor(x - half);
        int maxX = (int) Math.floor(x + half - 1e-9);
        int minZ = (int) Math.floor(z - half);
        int maxZ = (int) Math.floor(z + half - 1e-9);
        int maxY = (int) Math.ceil(y + type.height()) - 1;
        for (int bx = minX; bx <= maxX; bx++) for (int bz = minZ; bz <= maxZ; bz++) {
            if (type != MobType.VEX) {
                short support = world.getBlock(bx, y - 1, bz);
                if (support < 0 || !world.isSolid(support)) return false;
            }
            for (int by = y; by <= maxY; by++) {
                short block = world.getBlock(bx, by, bz);
                if (block < 0 || world.isSolid(block) || Fluids.isFluid(block)) return false;
            }
        }
        return true;
    }

    private static MobType roleType(int role) {
        return switch (role) {
            case RaidPlanGenerator.ROLE_PILLAGER -> MobType.PILLAGER;
            case RaidPlanGenerator.ROLE_VINDICATOR -> MobType.VINDICATOR;
            case RaidPlanGenerator.ROLE_EVOKER -> MobType.EVOKER;
            case RaidPlanGenerator.ROLE_WITCH -> MobType.WITCH;
            case RaidPlanGenerator.ROLE_VEX -> MobType.VEX;
            case RaidPlanGenerator.ROLE_RAVAGER -> MobType.RAVAGER;
            case RaidPlanGenerator.ROLE_ILLUSIONER -> MobType.ILLUSIONER;
            case RaidPlanGenerator.ROLE_STANDARD_BEARER -> MobType.STANDARD_BEARER;
            case RaidPlanGenerator.ROLE_WEB_TRAPPER -> MobType.WEB_TRAPPER;
            case RaidPlanGenerator.ROLE_BREACHER -> MobType.BREACHER;
            case RaidPlanGenerator.ROLE_DEMOLISHER -> MobType.DEMOLISHER;
            case RaidPlanGenerator.ROLE_BUILDER -> MobType.BUILDER;
            default -> throw new IllegalArgumentException("role=" + role);
        };
    }

    private static int mix32(int value) {
        value ^= value >>> 16;
        value *= 0x7feb352d;
        value ^= value >>> 15;
        value *= 0x846ca68b;
        return value ^ value >>> 16;
    }
}
