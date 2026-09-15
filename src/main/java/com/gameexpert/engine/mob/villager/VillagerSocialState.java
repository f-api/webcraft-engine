package com.gameexpert.engine.mob.villager;

import com.gameexpert.engine.mob.villager.VillagerGossipRules.ReputationEvent;

/**
 * 주민 한 마리의 영속 사회 기억(gossip·감쇠 주기·전파 쿨다운·마을 앵커)을 들고 있는 상태 객체다.
 *
 * <p>지금 무엇을 하는가(활동)는 여기 없다 — 종·습격이 만드는 HIDE/PRE_RAID/RAID 까지 포함한 현재
 * 활동의 정본은 {@link VillagerActivityLedger} 이고, 바닐라와 같이 저장되지 않는다. 영속은
 * {@link #encode()} 한 줄이며 두 권위가 같은 문자열을 만든다.
 */
public final class VillagerSocialState {

    /** 마을 앵커가 없는 줄. 옛 저장본이 그대로 읽히는 스키마 기본값이다. */
    private static final String CODEC_VERSION = "1";
    /** 마을 앵커가 붙은 줄. 필드가 하나 늘어난 것 말고는 v1 과 같다. */
    private static final String CODEC_VERSION_WITH_HOME_VILLAGE = "2";

    private final VillagerGossips gossips;
    private long lastGossipTime = Long.MIN_VALUE;
    private long lastGossipDecayTime = Long.MIN_VALUE;
    private boolean hasHomeVillage;
    private int homeVillageX;
    private int homeVillageY;
    private int homeVillageZ;

    public VillagerSocialState() {
        this(new VillagerGossips());
    }

    private VillagerSocialState(VillagerGossips gossips) {
        this.gossips = gossips;
    }

    public VillagerGossips gossips() { return gossips; }

    public long lastGossipTime() { return lastGossipTime; }

    public long lastGossipDecayTime() { return lastGossipDecayTime; }

    /**
     * 이 주민이 속한 마을의 앵커 {@code {x,y,z}} 또는 {@code null}. 바닐라
     * {@code PoiManager} 마을 인덱스의 자리를 대신하는 단일 원천이며
     * ({@link VillagerBrainRules#wanderedOutOfHomeVillage}), 앵커가 없는 주민은 그 울타리를
     * 받지 않는다.
     */
    public int[] homeVillageAnchor() {
        return hasHomeVillage
                ? new int[] { homeVillageX, homeVillageY, homeVillageZ } : null;
    }

    /**
     * 마을 site 가 이 주민을 놓은 칸을 마을 앵커로 기억한다. 마을은 옮겨 다니지 않으므로 이미
     * 앵커가 있으면 그대로 둔다(두 권위가 같은 첫 값을 갖는다).
     *
     * @return 실제로 기억했으면 true
     */
    public boolean rememberHomeVillage(int x, int y, int z) {
        if (hasHomeVillage) return false;
        hasHomeVillage = true;
        homeVillageX = x;
        homeVillageY = y;
        homeVillageZ = z;
        return true;
    }

    /** 첫 호출은 시각만 기록하고 감쇠하지 않는다(바닐라 {@code maybeDecayGossip} 과 같다). */
    public void maybeDecayGossip(long gameTime) {
        if (lastGossipDecayTime == Long.MIN_VALUE) {
            lastGossipDecayTime = gameTime;
            return;
        }
        if (!VillagerGossipRules.decayDue(gameTime, lastGossipDecayTime)) return;
        gossips.decay();
        lastGossipDecayTime = gameTime;
    }

    /**
     * 치료 트랙이 부를 훅. 좀비 주민 치료는 {@code ReputationEvent.ZOMBIE_VILLAGER_CURED} 하나로
     * MAJOR_POSITIVE +20 과 MINOR_POSITIVE +25 를 함께 남긴다.
     */
    public void applyReputationEvent(String playerKey, ReputationEvent event) {
        gossips.applyReputationEvent(playerKey, event);
    }

    public int reputation(String playerKey) {
        return gossips.reputation(playerKey);
    }

    /**
     * 두 주민이 만났을 때의 전파. 바닐라 {@code Villager#gossip} 과 같이 <b>말을 건 쪽(this)만</b>
     * 상대의 기억을 받아오고, 쿨다운은 양쪽 모두 같은 시각으로 갱신된다. 반대 방향은 상대가
     * 자기 차례에 이 함수를 부를 때 일어난다.
     *
     * @return 실제로 나눴으면 true
     */
    public boolean gossipWith(VillagerSocialState other, long gameTime, VillagerGossips.IntBound random) {
        if (other == this) return false;
        if (!VillagerGossipRules.gossipExchangeDue(gameTime, lastGossipTime, other.lastGossipTime)) {
            return false;
        }
        gossips.transferFrom(other.gossips, random, VillagerGossipRules.GOSSIP_TRANSFER_DRAWS);
        lastGossipTime = gameTime;
        other.lastGossipTime = gameTime;
        return true;
    }

    /**
     * 저장할 것이 없으면 {@code null} 이라 컬럼이 비어 있는 채로 남는다. 마을 앵커가 없는 주민의
     * 문자열은 v1 그대로라 앵커가 생기기 전까지 저장 바이트가 바뀌지 않는다.
     */
    public String encode() {
        String encodedGossips = gossips.encode();
        if (encodedGossips == null && lastGossipTime == Long.MIN_VALUE
                && lastGossipDecayTime == Long.MIN_VALUE && !hasHomeVillage) {
            return null;
        }
        String head = (hasHomeVillage ? CODEC_VERSION_WITH_HOME_VILLAGE : CODEC_VERSION)
                + "|" + encodeTime(lastGossipTime) + "|" + encodeTime(lastGossipDecayTime) + "|"
                // gossip 블롭은 자체 구분자를 갖고 있어 항상 마지막 칸이다. 앵커는 그 앞에 둔다.
                + (hasHomeVillage
                        ? homeVillageX + "," + homeVillageY + "," + homeVillageZ + "|" : "");
        return head + (encodedGossips == null ? "" : encodedGossips);
    }

    /** "아직 없음"은 두 권위가 같은 한 글자로 적는다. Java 의 Long.MIN_VALUE 를 그대로 쓰면
     * TypeScript 쪽 표현과 달라져 저장 문자열이 갈린다. */
    private static String encodeTime(long time) {
        return time == Long.MIN_VALUE ? "-" : Long.toString(time);
    }

    /**
     * 손상되었거나 비어 있으면 새 상태를 준다. 활동은 저장하지 않는다(활동 원장이 첫 틱에 고른다).
     * 마을 앵커 칸이 없는 v1 줄은 "앵커 없음" 이라는 스키마 기본값으로 읽힌다.
     */
    public static VillagerSocialState decode(String encoded) {
        if (encoded == null || encoded.isEmpty()) return new VillagerSocialState();
        boolean withHomeVillage = encoded.startsWith(CODEC_VERSION_WITH_HOME_VILLAGE + "|");
        int fields = withHomeVillage ? 5 : 4;
        String[] parts = encoded.split("\\|", fields);
        if (parts.length != fields) return new VillagerSocialState();
        if (!(withHomeVillage ? CODEC_VERSION_WITH_HOME_VILLAGE : CODEC_VERSION).equals(parts[0])) {
            return new VillagerSocialState();
        }
        long gossipTime;
        long decayTime;
        int[] anchor = null;
        try {
            gossipTime = decodeTime(parts[1]);
            decayTime = decodeTime(parts[2]);
            if (withHomeVillage) {
                anchor = decodeAnchor(parts[3]);
                if (anchor == null) return new VillagerSocialState();
            }
        } catch (NumberFormatException failure) {
            return new VillagerSocialState();
        }
        String encodedGossips = parts[fields - 1];
        VillagerSocialState state = new VillagerSocialState(
                VillagerGossips.decode(encodedGossips.isEmpty() ? null : encodedGossips));
        state.lastGossipTime = gossipTime;
        state.lastGossipDecayTime = decayTime;
        if (anchor != null) state.rememberHomeVillage(anchor[0], anchor[1], anchor[2]);
        return state;
    }

    /** {@code "x,y,z"} 또는 손상되었으면 {@code null}. */
    private static int[] decodeAnchor(String text) {
        String[] parts = text.split(",", -1);
        if (parts.length != 3) return null;
        return new int[] { Integer.parseInt(parts[0]), Integer.parseInt(parts[1]),
                Integer.parseInt(parts[2]) };
    }

    private static long decodeTime(String text) {
        return "-".equals(text) ? Long.MIN_VALUE : Long.parseLong(text);
    }
}
