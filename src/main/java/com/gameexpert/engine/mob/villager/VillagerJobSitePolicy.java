package com.gameexpert.engine.mob.villager;

import com.gameexpert.engine.FurnaceVariant;
import com.gameexpert.terrain.Blocks;

/**
 * Which registered blocks are villager job sites, which profession each grants, and when a
 * durable job-site claim must be released.
 *
 * <p><b>Vanilla station set.</b> Minecraft Java 1.21.4 has exactly thirteen job site blocks and
 * WebCraft now registers all thirteen (IDs 521–533), so the table below is the vanilla POI table
 * verbatim — no substitution and no divergence. The earlier stand-ins (furnace, crafting table,
 * campfire, enchanting table) are <b>not</b> vanilla job sites and no longer grant a profession;
 * a villager standing next to a furnace stays unemployed exactly as in vanilla. Every
 * behavioural number around the station (claim range, reset condition, trade lock) stays vanilla.
 *
 * <p><b>Consequence to know.</b> Village generation does not place any of the thirteen (they are
 * placement-only so the golden terrain hash stays fixed), so village villagers only take a job
 * once a player puts a station down. That is the vanilla rule applied to a world whose villages
 * were generated without job sites, not a behaviour choice made here.
 *
 * <p>Stateless. The claim ledger owns occupancy; this class only decides facts about blocks.
 */
public final class VillagerJobSitePolicy {
    /** Durable claim lane name; distinct from every structure kind and from GLITCH_SIGNAL. */
    public static final String CLAIM_KIND = "VILLAGER_JOB_SITE";
    /**
     * Durable claim lane code for the standalone numeric claim key. Distinct from
     * {@code GlitchSignalPolicy.CLAIM_KIND_CODE} (0x1000_0000).
     */
    public static final long CLAIM_KIND_CODE = 0x2000_0000L;
    /** Bumping this labels new decisions only; nothing already claimed is invalidated. */
    public static final int POLICY_VERSION = 1;

    /**
     * Vanilla {@code AssignProfessionFromJobSite}: the potential job site must be within two
     * blocks of the villager before the profession is taken.
     */
    public static final double CLAIM_RANGE_BLOCKS = 2.0;
    /**
     * A claim outside the acquisition radius can never be re-reached by its owner, so it is
     * released. WebCraft divergence: vanilla revalidates the POI ticket through the PoiManager
     * instead of a distance cutoff; the bound reuses the vanilla acquisition radius (48).
     */
    public static final int CLAIM_RELEASE_DISTANCE_BLOCKS =
            VillagerBrainRules.POI_SEARCH_RADIUS_BLOCKS;

    /**
     * Share of naturally generated villagers that are born nitwits and can never take a job.
     *
     * <p>WebCraft divergence: villagers only ever appear from an accepted village site here, so
     * "natural spawn" means one entry of that site's occupant roster. The draw is derived from
     * {@code (worldSeed, siteKey, rosterIndex)} instead of the roster's own random lane, so
     * adding it leaves every existing roster (villager count, golem, illager rolls) byte-identical
     * and both authorities decide the same villagers.
     */
    public static final int NATURAL_NITWIT_PERCENT = 10;

    /** Is this entry of an accepted site's occupant roster born a nitwit? */
    public static boolean naturalSpawnNitwit(int worldSeed, long siteKey, int rosterIndex) {
        int state = mixNitwit(worldSeed ^ (int) siteKey ^ mixNitwit(rosterIndex + 1));
        return Integer.remainderUnsigned(state, 100) < NATURAL_NITWIT_PERCENT;
    }

    private static int mixNitwit(int value) {
        int mixed = value;
        mixed = (mixed ^ mixed >>> 16) * 0x7feb352d;
        mixed = (mixed ^ mixed >>> 15) * 0x846ca68b;
        return mixed ^ mixed >>> 16;
    }

    /**
     * Job professions. NONE/NITWIT mirror {@link VillagerBrainRules.ProfessionState}; the rest
     * are the thirteen vanilla employed professions. Names match
     * {@link VillagerTradeRules.Profession} one-for-one so
     * {@link VillagerProfessionSource#tradeProfession} is a pure rename.
     *
     * <p>The first six constants keep their original declaration order because
     * {@link VillagerTradeState} persists the profession by {@code name()} and the claim ledger
     * compares ordinals; appending is safe, reordering is not.
     */
    public enum Profession {
        NONE, NITWIT, ARMORER, BUTCHER, LIBRARIAN, TOOLSMITH,
        CARTOGRAPHER, CLERIC, FARMER, FISHERMAN, FLETCHER, LEATHERWORKER, MASON, SHEPHERD,
        WEAPONSMITH
    }

    /** Why a durable job-site claim was given up. */
    public enum ReleaseReason { DEATH, BLOCK_REMOVED, OUT_OF_RANGE }

    private VillagerJobSitePolicy() {
    }

    /**
     * Station identity for a block id, or {@code 0} when the block is not a job site.
     *
     * <p>The thirteen station IDs are one contiguous range, so the test is a range compare and no
     * station can be added to one authority and forgotten in the other.
     *
     * <p><b>[FURNACE-VARIANT] Lit normalisation.</b> Two of the thirteen — blast_furnace and
     * smoker — <i>do</i> have runtime lit twins now, exactly the way {@link Blocks#FURNACE} has
     * {@link Blocks#FURNACE_LIT}, and their IDs (841/842) sit outside the contiguous 521–533
     * range. Without normalisation the range compare would return {@code 0} the moment a
     * villager's blast furnace started smelting, {@code releaseReason} would read that as
     * {@link ReleaseReason#BLOCK_REMOVED}, and the armorer would lose their job site every time
     * the block they work at lit up. So the lit ID is folded back to its unlit original first,
     * and the station code a claim carries is always the unlit ID — the identity a claim was
     * taken under can never change underneath it.
     *
     * <p>The fold is {@link FurnaceVariant#baseBlockId}, the same one the drop table, the mining
     * time and the blast resistance use, so there is exactly one place that knows 841 is a
     * blast furnace.
     */
    public static int stationCode(int blockId) {
        int id = FurnaceVariant.baseBlockId(blockId & 0xFFFF);
        return id >= Blocks.BARREL && id <= Blocks.STONECUTTER ? id : 0;
    }

    public static boolean isJobSiteBlock(int blockId) {
        return stationCode(blockId) != 0;
    }

    /** Every registered station code, ascending. The thirteen vanilla job site blocks. */
    public static int[] stationCodes() {
        int[] codes = new int[Blocks.STONECUTTER - Blocks.BARREL + 1];
        for (int index = 0; index < codes.length; index++) codes[index] = Blocks.BARREL + index;
        return codes;
    }

    /**
     * Profession granted by a station code, or {@link Profession#NONE} when it is not one.
     * This is the vanilla {@code VillagerProfession} → {@code PoiType} table read backwards.
     */
    public static Profession professionFor(int stationCode) {
        return switch (stationCode) {
            case Blocks.BARREL -> Profession.FISHERMAN;
            case Blocks.BLAST_FURNACE -> Profession.ARMORER;
            case Blocks.BREWING_STAND -> Profession.CLERIC;
            case Blocks.CARTOGRAPHY_TABLE -> Profession.CARTOGRAPHER;
            case Blocks.CAULDRON -> Profession.LEATHERWORKER;
            case Blocks.COMPOSTER -> Profession.FARMER;
            case Blocks.FLETCHING_TABLE -> Profession.FLETCHER;
            case Blocks.GRINDSTONE -> Profession.WEAPONSMITH;
            case Blocks.LECTERN -> Profession.LIBRARIAN;
            case Blocks.LOOM -> Profession.SHEPHERD;
            case Blocks.SMITHING_TABLE -> Profession.TOOLSMITH;
            case Blocks.SMOKER -> Profession.BUTCHER;
            case Blocks.STONECUTTER -> Profession.MASON;
            default -> Profession.NONE;
        };
    }

    /** Profession granted by a block id. */
    public static Profession professionForBlock(int blockId) {
        return professionFor(stationCode(blockId));
    }

    public static VillagerBrainRules.ProfessionState professionState(Profession profession) {
        return switch (profession) {
            case NONE -> VillagerBrainRules.ProfessionState.NONE;
            case NITWIT -> VillagerBrainRules.ProfessionState.NITWIT;
            default -> VillagerBrainRules.ProfessionState.ASSIGNED;
        };
    }

    /** Squared distance form so callers never take a square root. */
    public static boolean withinClaimRange(double distanceSquared) {
        return distanceSquared >= 0.0
                && distanceSquared <= CLAIM_RANGE_BLOCKS * CLAIM_RANGE_BLOCKS;
    }

    /**
     * Vanilla {@code AssignProfessionFromJobSite}: an unemployed adult villager standing within
     * two blocks of an unclaimed job site takes that profession. Babies never take a job and
     * NITWIT is not an unemployed state.
     */
    public static boolean assignmentEligible(
            VillagerBrainRules.ProfessionState currentProfession,
            int age,
            boolean stationUnclaimed,
            double distanceSquared,
            int blockId) {
        return currentProfession == VillagerBrainRules.ProfessionState.NONE
                && !VillagerBrainRules.isBaby(age)
                && stationUnclaimed
                && isJobSiteBlock(blockId)
                && withinClaimRange(distanceSquared);
    }

    /**
     * Vanilla: the first completed trade locks the profession permanently, so losing the job site
     * afterwards never resets it. Equivalent to the {@code level > 1 || xp > 0} test that
     * {@link VillagerBrainRules#professionResetEligible} already encodes.
     */
    public static boolean lockedByTrade(int completedTrades) {
        return completedTrades >= 1;
    }

    /** A locked profession survives every release reason; an unlocked one resets to NONE. */
    public static boolean releaseResetsProfession(boolean lockedByTrade) {
        return !lockedByTrade;
    }

    public static boolean outOfClaimRange(double distanceSquared) {
        return !(distanceSquared >= 0.0)
                || distanceSquared > (double) CLAIM_RELEASE_DISTANCE_BLOCKS
                        * CLAIM_RELEASE_DISTANCE_BLOCKS;
    }

    /** The one place that decides whether a live claim must be given up this evaluation. */
    public static ReleaseReason releaseReason(
            boolean ownerAlive, int currentBlockId, int claimedStationCode,
            double distanceSquared) {
        if (!ownerAlive) return ReleaseReason.DEATH;
        if (stationCode(currentBlockId) != claimedStationCode) return ReleaseReason.BLOCK_REMOVED;
        if (outOfClaimRange(distanceSquared)) return ReleaseReason.OUT_OF_RANGE;
        return null;
    }

    /**
     * Vertical half-window of the job-site POI scan. WebCraft divergence, identical to the HOME
     * lane's {@link VillagerSocietyRules#HOME_SEARCH_VERTICAL_REACH}: vanilla asks the PoiManager
     * for a section-indexed sphere, WebCraft walks the world directly and must bound the column.
     */
    public static final int POI_SEARCH_VERTICAL_REACH =
            VillagerSocietyRules.HOME_SEARCH_VERTICAL_REACH;
    /** Block-read budget of one job-site scan; same bound and meaning as the HOME lane's. */
    public static final int POI_SCAN_BLOCK_BUDGET = VillagerSocietyRules.HOME_SCAN_BLOCK_BUDGET;

    /** Reads one block id for the POI scan. */
    @FunctionalInterface
    public interface StationProbe {
        int blockAt(int x, int y, int z);
    }

    /** True when nobody owns that station cell yet. */
    @FunctionalInterface
    public interface CellFreeProbe {
        boolean isFree(int stationCode, int x, int y, int z);
    }

    /**
     * Vanilla {@code AcquirePoi(JOB_SITE)}: the nearest unoccupied job site within the 48-block
     * acquisition radius becomes the villager's potential job site. This raw HOME-lane ring walk
     * is the deterministic fallback when resident POI-index coverage is incomplete — same order,
     * same budget, both authorities.
     *
     * @return {@code {stationCode, x, y, z}} or {@code null} when nothing is in reach
     */
    public static int[] nearestFreeStation(int originX, int originY, int originZ,
            StationProbe blocks, CellFreeProbe free) {
        int[] found = VillagerSocietyRules.nearestMatchingCell(originX, originY, originZ,
                VillagerBrainRules.POI_SEARCH_RADIUS_BLOCKS, POI_SEARCH_VERTICAL_REACH,
                POI_SCAN_BLOCK_BUDGET,
                (x, y, z) -> {
                    int stationCode = stationCode(blocks.blockAt(x, y, z));
                    return stationCode != 0 && free.isFree(stationCode, x, y, z);
                });
        if (found == null) return null;
        return new int[] { stationCode(blocks.blockAt(found[0], found[1], found[2])),
                found[0], found[1], found[2] };
    }

    /**
     * Squared distance from a villager to the centre of a station cell. Vanilla measures the
     * villager position against the POI block position, so the block centre is the reference.
     */
    public static double distanceSquaredToCell(double x, double y, double z,
            int cellX, int cellY, int cellZ) {
        double dx = x - (cellX + 0.5);
        double dy = y - (cellY + 0.5);
        double dz = z - (cellZ + 0.5);
        return dx * dx + dy * dy + dz * dz;
    }

    /**
     * Durable identity of one station cell inside the claim lane. Keyed by the numeric lane code
     * so both authorities build the same string from the same numbers.
     */
    public static String identityKey(int stationCode, int x, int y, int z) {
        return CLAIM_KIND_CODE + ":" + stationCode + ':' + x + ':' + y + ':' + z;
    }
}
