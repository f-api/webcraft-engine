package com.gameexpert.engine.mob.villager;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 주민이 실제로 직업을 얻는 런타임 경로다. 상태(원장)는 {@link VillagerJobClaimLedger},
 * 블록 사실은 {@link VillagerJobSitePolicy}, 일정·반경·거리 상수는 {@link VillagerBrainRules}
 * 가 갖고 있고, 이 클래스는 그 셋을 매 틱 이어 붙이기만 한다. 새 규칙을 여기서 만들지 않는다.
 *
 * <p>바닐라 근거(Java 1.21.4):
 * <ul>
 *   <li>{@code AcquirePoi(JOB_SITE)}: 미취업 주민이 48블록 안의 비점유 직업지를 POTENTIAL_JOB_SITE
 *       기억으로 잡는다.</li>
 *   <li>{@code SetWalkTargetFromBlockMemory} / {@code WorkAtPoi}: 그 기억을 향해 0.4 배속으로
 *       걷는다.</li>
 *   <li>{@code AssignProfessionFromJobSite}: 성체가 그 직업지 <b>2블록</b> 안에 들어오고 칸이
 *       여전히 비어 있으면 그 직업을 얻는다. 아기와 NITWIT 은 취업하지 않는다.</li>
 *   <li>거래 1회로 직업이 잠기고, 직업지가 사라지거나 영원히 닿을 수 없게 되면 칸을 놓는다.</li>
 * </ul>
 *
 * <p>WebCraft divergence(계약):
 * <ul>
 *   <li>후보 탐색은 HOME 침대 lane 과 같은 링 서수를 쓴다. 완전한 상주 범위는 파생 POI
 *       인덱스가 고르고, 범위를 모르면 {@link VillagerSocietyRules#nearestMatchingCell}로
 *       돌아간다. 비용을 묶기 위해 <b>권위 틱당 한 명</b>만 탐색한다.</li>
 *   <li>바닐라는 POTENTIAL_JOB_SITE 걷기를 idle/work 행동 묶음이 소유하지만, WebCraft 주민은
 *       행동 묶음이 없어 "REST 가 아닌 동안" 걷는 것으로 근사한다. 취업한 주민의 직업지 걷기는
 *       바닐라와 같이 WORK 일정이 소유하고, 그 밖의 시간대(IDLE·MEET·PLAY)에는 core package
 *       {@code GoToClosestVillage} 자리에 해당하는 울타리 걷기만 한다
 *       ({@link VillagerBrainRules#wanderedOutOfPoiMemory}).</li>
 *   <li>POI 기억이 하나도 없는 주민(미취업·후보 없음)의 울타리는 그 기억 대신 마을 앵커를 쓴다
 *       ({@link VillagerBrainRules#wanderedOutOfHomeVillage}). 앵커는 마을 site 가 주민을 놓은
 *       칸이며 주민 행에 영속한다. 앵커가 없는 주민(옛 저장본·테스트 월드·번식으로 태어난 새끼)은
 *       울타리가 없다 — 새끼는 마을 안에서 태어나 곧바로 HOME 침대를 갖는다.</li>
 *   <li>POTENTIAL_JOB_SITE 기억은 영속하지 않는다. 재접속 뒤 같은 스캔이 같은 후보를 다시
 *       고르므로 결과가 달라지지 않는다. 실제 점유(직업)는 원장이 영속한다.</li>
 * </ul>
 *
 * <p>모든 판정은 mob id 오름차순으로 이루어져 두 권위가 같은 틱에 같은 결론을 낸다.
 */
public final class VillagerJobAssignment {

    /** 이번 틱 판정 대상 주민 하나. */
    public interface JobVillager {
        long id();

        double x();

        double y();

        double z();

        boolean baby();

        /** 살아 있는가. 죽은 주민의 행은 이 틱에 삭제된다. */
        boolean alive();

        /**
         * 이 주민의 현재 활동. 정본은 {@link VillagerActivityLedger} 이며 권위 배선이 그 값을 넘긴다.
         * 원장이 없는 순수 테스트 월드는 막 만들어진 뇌의 일정 값이다.
         *
         * @param dayTime MC 일정 축(0..23999)의 시각
         */
        default VillagerBrainRules.Activity activity(long dayTime) {
            return VillagerActivityLedger.fallbackActivity(dayTime, baby());
        }

        /** 생성 직업 NITWIT 과 작업장 없이 거래를 마친 주민도 새 직업을 얻지 않는다. */
        default boolean canAcquireJobSite() {
            return true;
        }

        /**
         * 이 주민이 속한 마을의 앵커 {@code {x,y,z}} 또는 {@code null}. 영속 원천은
         * {@link VillagerSocialState#homeVillageAnchor()} 이고, 앵커가 없는 주민(옛 저장본·테스트
         * 월드)은 마을 울타리를 받지 않는다 — 스키마 기본값이다.
         */
        default int[] homeVillageAnchor() {
            return null;
        }
    }

    /** 직업지 판정이 보는 월드. */
    public interface JobWorld {
        int blockAt(int x, int y, int z);

        /**
         * Resident POI-index hook. {@code covered=false} tells this lane to retain the established
         * block probe, so an unloaded boundary can never turn a partial index into a decision.
         */
        default VillagerPoiIndex.SearchResult nearestFreeStation(int originX, int originY,
                int originZ, VillagerPoiIndex.FreeCell free) {
            return new VillagerPoiIndex.SearchResult(false, null);
        }

        /** 0..11999 월드 시계. 일정은 {@link VillagerSocietyRules#mcDayTime}로 환산한다. */
        long worldTime();

        /**
         * 월드가 지나온 날 수. {@link VillagerSocietyRules#mcGameTime} 로 단조 증가 절대 틱을
         * 만들며, 재입고 일일 한도가 이 값을 본다. 시계가 없는 순수 테스트 월드는 0 이다.
         */
        default long dayCount() {
            return 0L;
        }
    }

    /** 이번 틱에 새로 배정된 직업 하나. */
    public record Assigned(long mobId, VillagerJobSitePolicy.Profession profession,
            int stationCode, int x, int y, int z) {}

    /**
     * 이번 틱 결과. {@code walkTargets} 는 mob id → {@code {x,y,z}} 직업지 좌표이고,
     * {@code workedAtJobSite} 는 이번 틱 자기 작업대를 실제로 쓴 주민 id 다(바닐라
     * {@code WorkAtPoi.start}). 재입고는 그 id 로 거래 lane 이 확정한다.
     */
    public record Outcome(Map<Long, int[]> walkTargets, List<Assigned> assigned,
            List<Long> released, List<Long> workedAtJobSite) {}

    private static final Outcome EMPTY =
            new Outcome(Map.of(), List.of(), List.of(), List.of());

    private final VillagerJobClaimLedger ledger;
    /** POTENTIAL_JOB_SITE 기억: mob id → {@code {stationCode, x, y, z}}. 휘발성이다. */
    private final Map<Long, int[]> potentialSites = new HashMap<>();
    /** 실패한 탐색의 백오프: mob id → {@code {다음 탐색 틱, 직전 지연(MC 틱)}}. */
    private final Map<Long, long[]> scanBackoff = new HashMap<>();
    /** 바닐라 {@code LAST_WORKED_AT_POI} 기억: mob id → 마지막 근무 gameTime. 휘발성이다. */
    private final Map<Long, Long> lastWorkedAtPoi = new HashMap<>();
    private int scanCursor;
    /** 이 lane 이 돈 권위 틱 수. 두 권위가 같은 횟수만큼 호출하므로 같은 값을 본다. */
    private long tickCounter;

    public VillagerJobAssignment(VillagerJobClaimLedger ledger) {
        this.ledger = ledger;
    }

    public VillagerJobClaimLedger ledger() {
        return ledger;
    }

    /** 테스트·진단용. 아직 점유로 승격되지 않은 후보 좌표 또는 {@code null}. */
    public int[] potentialSiteOf(long mobId) {
        int[] site = potentialSites.get(mobId);
        return site == null ? null : site.clone();
    }

    /** 주민이 영구히 사라졌을 때. 원장 행과 휘발 기억을 함께 버린다. */
    public void forget(long mobId) {
        potentialSites.remove(mobId);
        scanBackoff.remove(mobId);
        lastWorkedAtPoi.remove(mobId);
        ledger.forgetMob(mobId);
    }

    /**
     * 활성 청크의 주민만 넘긴다. 하나의 권위 틱에서 (1) 살아 있는 점유의 재검증, (2) 후보 한 건
     * 탐색, (3) 2블록 안 후보의 점유 승격, (4) 이번 틱 걷기 목표를 순서대로 확정한다.
     */
    public Outcome tick(JobWorld world, List<? extends JobVillager> villagers) {
        if (villagers == null || villagers.isEmpty()) return EMPTY;
        List<JobVillager> ordered = new ArrayList<>(villagers);
        ordered.sort(Comparator.comparingLong(JobVillager::id));
        tickCounter++;
        long dayTime = VillagerSocietyRules.mcDayTime(world.worldTime());
        long gameTime = VillagerSocietyRules.mcGameTime(world.dayCount(), world.worldTime());

        Map<Long, int[]> walkTargets = new LinkedHashMap<>();
        List<Assigned> assigned = new ArrayList<>();
        List<Long> released = new ArrayList<>();
        List<Long> worked = new ArrayList<>();
        JobVillager scanCandidate = null;
        int scanIndex = -1;

        for (int offset = 0; offset < ordered.size(); offset++) {
            int index = Math.floorMod(scanCursor + offset, ordered.size());
            JobVillager villager = ordered.get(index);
            long id = villager.id();
            if (!villager.alive()) {
                if (ledger.claimOf(id) != null) released.add(id);
                forget(id);
                continue;
            }

            int[] site = ledger.jobSiteOf(id);
            if (site != null) {
                double distanceSquared = VillagerJobSitePolicy.distanceSquaredToCell(
                        villager.x(), villager.y(), villager.z(), site[1], site[2], site[3]);
                VillagerJobSitePolicy.ReleaseReason reason = ledger.revalidate(id, true,
                        world.blockAt(site[1], site[2], site[3]), distanceSquared);
                if (reason != null) {
                    released.add(id);
                    potentialSites.remove(id);
                    continue;
                }
                // 바닐라 WorkAtPoi: 취업한 주민의 "근무"는 WORK 일정만 소유한다. 그 밖의
                // 시간대는 아래 GoToClosestVillage 자리가 울타리만 지킨다.
                if (destinationOf(dayTime, villager)
                        == VillagerBrainRules.ActivityDestination.JOB_SITE) {
                    walkTargets.put(id, new int[] { site[1], site[2], site[3] });
                    // 그 작업대 1.73블록 안에 서 있으면 이번 틱이 곧 바닐라 WorkAtPoi.start 다.
                    if (VillagerBrainRules.withinWorkstationRange(distanceSquared)
                            && VillagerBrainRules.workAtPoiDue(gameTime, lastWorkedOf(id))) {
                        lastWorkedAtPoi.put(id, gameTime);
                        worked.add(id);
                    }
                } else if (fenceApplies(villager.activity(dayTime))
                        && VillagerBrainRules.wanderedOutOfPoiMemory(
                                VillagerBrainRules.ActivityDestination.JOB_SITE,
                                villager.x() - (site[1] + 0.5),
                                villager.y() - (site[2] + 0.5),
                                villager.z() - (site[3] + 0.5))) {
                    // 바닐라 core package GoToClosestVillage: 지금 일정은 이 주민을 어디로도
                    // 보내지 않는다(IDLE·MEET·PLAY). 그 시간대의 배회가 마을 밖으로 데려가면
                    // 안 된다. 이 자리가 없던 동안 표류가 해제 반경을 넘겨, 돌아갈 직업지
                    // 자체를 잃었다.
                    walkTargets.put(id, new int[] { site[1], site[2], site[3] });
                }
                continue;
            }

            // 행은 있는데 칸이 없다 = NITWIT 이거나 거래로 잠긴 뒤 칸을 놓은 주민이다.
            // 둘 다 새 직업지를 잡지 않는다(NITWIT 은 영원히, 잠긴 주민은 직업이 이미 확정).
            if (ledger.claimOf(id) != null) continue;
            if (villager.baby()) continue;
            if (!villager.canAcquireJobSite()) {
                potentialSites.remove(id);
                scanBackoff.remove(id);
                continue;
            }

            int[] potential = potentialSites.get(id);
            if (potential != null && !stillClaimable(world, potential)) {
                potentialSites.remove(id);
                potential = null;
            }
            if (potential == null) {
                // 바닐라 JitteredLinearRetry: 빈손으로 끝난 탐색은 곧바로 다시 돌지 않는다.
                if (scanCandidate == null && scanDue(id)) {
                    scanCandidate = villager;
                    scanIndex = index;
                }
                continue;
            }

            double distanceSquared = VillagerJobSitePolicy.distanceSquaredToCell(
                    villager.x(), villager.y(), villager.z(),
                    potential[1], potential[2], potential[3]);
            int blockId = world.blockAt(potential[1], potential[2], potential[3]);
            if (VillagerJobSitePolicy.assignmentEligible(
                    ledger.professionStateOf(id), villager.baby() ? -1 : 0,
                    ledger.isFreeStation(blockId, potential[1], potential[2], potential[3]),
                    distanceSquared, blockId)) {
                VillagerJobSitePolicy.Profession granted = ledger.claimJobSite(
                        id, blockId, potential[1], potential[2], potential[3]);
                potentialSites.remove(id);
                if (granted != null) {
                    assigned.add(new Assigned(id, granted, potential[0],
                            potential[1], potential[2], potential[3]));
                    if (destinationOf(dayTime, villager)
                            == VillagerBrainRules.ActivityDestination.JOB_SITE) {
                        walkTargets.put(id,
                                new int[] { potential[1], potential[2], potential[3] });
                    }
                }
                continue;
            }
            // 아직 멀다: 바닐라 GoToPotentialJobSite 처럼 후보를 향해 걷는다. 그 행동은 비핵심
            // 활동이 IDLE·WORK·PLAY 일 때만 시작한다(종·습격의 사건 활동과 MEET·REST 는 걷지 않는다).
            if (VillagerBrainRules.potentialJobSiteWalkAllowed(villager.activity(dayTime))) {
                walkTargets.put(id, new int[] { potential[1], potential[2], potential[3] });
            }
        }

        // 바닐라 core package GoToClosestVillage 의 나머지 절반이다. 위의 어느 가지도 POI 기억이
        // 하나도 없는 주민(미취업이고 후보 직업지도 아직 없는 주민, NITWIT 포함)에게는 걷기
        // 목표를 주지 않아 그 배회만 무제한으로 남아 있었다. 마을 앵커를 아는 주민은 바닐라
        // ServerLevel#isVillage 와 같은 구역 경계 밖으로 나가면 앵커로 돌아온다. 판정은 mob id
        // 오름차순이라 두 권위가 같은 틱에 같은 목표를 만든다.
        for (JobVillager villager : ordered) {
            long id = villager.id();
            if (!villager.alive() || walkTargets.containsKey(id)) continue;
            if (ledger.jobSiteOf(id) != null || potentialSites.containsKey(id)) continue;
            int[] anchor = villager.homeVillageAnchor();
            if (anchor == null) continue;
            // REST 일정의 주민은 애초에 배회하지 않으므로(Villager#mayWander) 되돌릴 표류가 없다.
            // 종·습격의 사건 활동은 원장의 은신처 걷기가 이동을 소유한다.
            if (!fenceApplies(villager.activity(dayTime))) continue;
            if (!VillagerBrainRules.wanderedOutOfHomeVillage(anchor[0], anchor[1], anchor[2],
                    villager.x(), villager.y(), villager.z())) {
                continue;
            }
            walkTargets.put(id, new int[] { anchor[0], anchor[1], anchor[2] });
        }

        if (scanCandidate != null) {
            scanCursor = scanIndex + 1;
            int originX = (int) Math.floor(scanCandidate.x());
            int originY = (int) Math.floor(scanCandidate.y());
            int originZ = (int) Math.floor(scanCandidate.z());
            VillagerPoiIndex.SearchResult indexed = world.nearestFreeStation(
                    originX, originY, originZ,
                    (stationCode, x, y, z) -> !ledger.isClaimed(stationCode, x, y, z));
            int[] found = indexed.covered() ? indexed.position()
                    : VillagerJobSitePolicy.nearestFreeStation(
                            originX, originY, originZ, world::blockAt,
                            (stationCode, x, y, z) ->
                                    !ledger.isClaimed(stationCode, x, y, z));
            if (found != null) {
                potentialSites.put(scanCandidate.id(), found);
                scanBackoff.remove(scanCandidate.id());
            } else {
                scheduleRetry(scanCandidate.id());
            }
        }

        if (walkTargets.isEmpty() && assigned.isEmpty() && released.isEmpty()
                && worked.isEmpty()) {
            return EMPTY;
        }
        return new Outcome(walkTargets, assigned, released, worked);
    }

    /** 마지막 근무 시각 또는 "아직 없음". */
    private long lastWorkedOf(long mobId) {
        Long last = lastWorkedAtPoi.get(mobId);
        return last == null ? Long.MIN_VALUE : last;
    }

    /** 이 주민을 이번 틱에 다시 탐색해도 되는가(실패 백오프가 끝났는가). */
    private boolean scanDue(long mobId) {
        long[] backoff = scanBackoff.get(mobId);
        return backoff == null || VillagerBrainRules.poiRetryDue(tickCounter, backoff[0]);
    }

    /**
     * 실패한 탐색의 다음 시각을 잡는다. 지연은 바닐라
     * {@link VillagerBrainRules#poiFailureBackoffTicks}(40+nextInt(40), 400 상한, MC 틱)이며,
     * 권위 틱 하나가 MC 틱 둘이므로 절반으로 환산한다. 난수 대신 mob id 와 직전 지연에서
     * 뽑은 결정적 지터를 써서 두 권위가 같은 시각에 같은 결론을 낸다(WebCraft divergence).
     */
    private void scheduleRetry(long mobId) {
        long[] backoff = scanBackoff.get(mobId);
        int previousDelay = backoff == null ? 0 : (int) backoff[1];
        int jitter = (int) Math.floorMod(mobId * 31L + previousDelay,
                VillagerBrainRules.POI_FAILURE_BACKOFF_JITTER_SPAN_TICKS);
        int delayMcTicks = VillagerBrainRules.poiFailureBackoffTicks(previousDelay, jitter);
        long delayAuthorityTicks = Math.max(1L,
                delayMcTicks / VillagerSocietyRules.MC_TICKS_PER_AUTHORITY_TICK);
        scanBackoff.put(mobId, new long[] { tickCounter + delayAuthorityTicks, delayMcTicks });
    }

    /** 기억해 둔 후보가 아직 같은 스테이션이고 아무도 잡지 않았는가. */
    private boolean stillClaimable(JobWorld world, int[] potential) {
        int stationCode = VillagerJobSitePolicy.stationCode(
                world.blockAt(potential[1], potential[2], potential[3]));
        return stationCode == potential[0]
                && !ledger.isClaimed(stationCode, potential[1], potential[2], potential[3]);
    }

    private static VillagerBrainRules.ActivityDestination destinationOf(
            long dayTime, JobVillager villager) {
        return VillagerBrainRules.activityDestination(villager.activity(dayTime));
    }

    /**
     * 마을 울타리 걷기(기존 {@code GoToClosestVillage} 자리)가 이 활동에서 도는가. 일정 활동 중
     * REST 를 뺀 넷이다 — REST 는 배회하지 않고, HIDE/PRE_RAID/RAID 는 활동 원장의 걷기가 소유한다.
     */
    private static boolean fenceApplies(VillagerBrainRules.Activity activity) {
        return activity.scheduled() && activity != VillagerBrainRules.Activity.REST;
    }
}
