package com.gameexpert.engine.mob.villager;

import com.gameexpert.engine.mob.villager.VillagerGossipRules.GossipType;
import com.gameexpert.engine.mob.villager.VillagerGossipRules.ReputationEvent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * 주민 한 마리가 플레이어별로 들고 있는 사회 기억이다. 바닐라
 * {@code net.minecraft.world.entity.ai.gossip.GossipContainer} 와 같은 연산을 갖는다.
 *
 * <p>플레이어 식별자는 이 프로젝트의 다른 몹 상태(anger target 등)와 같이 닉네임 문자열이다.
 * 정렬된 {@link TreeMap}/{@link EnumMap} 을 쓰므로 순회 순서가 두 권위에서 같고, 따라서
 * {@link #encode()} 가 만드는 영속 문자열도 바이트 단위로 같다.
 */
public final class VillagerGossips {

    /** 전파 추첨용 난수 공급자. RNG 소유권은 호출자에게 둔다. */
    @FunctionalInterface
    public interface IntBound {
        /** {@code [0, bound)} 의 정수. */
        int nextInt(int bound);
    }

    /** 한 항목: 대상 플레이어, 종류, 값. */
    public record Entry(String playerKey, GossipType type, int value) {
        public int weightedValue() {
            return VillagerGossipRules.weightedValue(type, value);
        }
    }

    private static final String CODEC_VERSION = "1";

    private final TreeMap<String, EnumMap<GossipType, Integer>> byPlayer = new TreeMap<>();

    /** 지금 기억하고 있는 플레이어 수. */
    public int trackedPlayerCount() {
        return byPlayer.size();
    }

    public boolean isEmpty() {
        return byPlayer.isEmpty();
    }

    public int value(String playerKey, GossipType type) {
        EnumMap<GossipType, Integer> entries = byPlayer.get(playerKey);
        if (entries == null) return 0;
        Integer value = entries.get(type);
        return value == null ? 0 : value;
    }

    /** 상한을 넘기지 않고 더한다. 임계값 미만이 되면 항목을 버린다. */
    public void add(String playerKey, GossipType type, int amount) {
        if (playerKey == null || playerKey.isEmpty()) {
            throw new IllegalArgumentException("gossip player key must be non-empty");
        }
        put(playerKey, type,
                VillagerGossipRules.clampStoredValue(
                        type, VillagerGossipRules.mergeForAddition(type, value(playerKey, type), amount)));
    }

    /** 계기 하나가 올리는 모든 종류를 한 번에 적용한다. */
    public void applyReputationEvent(String playerKey, ReputationEvent event) {
        for (GossipType type : VillagerGossipRules.gossipTypesFor(event)) {
            add(playerKey, type, VillagerGossipRules.gossipGain(event, type));
        }
    }

    public void remove(String playerKey, GossipType type) {
        put(playerKey, type, 0);
    }

    public void removePlayer(String playerKey) {
        byPlayer.remove(playerKey);
    }

    /** 모든 종류의 가중합. 음수면 값이 오르고 양수면 할인이 된다. */
    public int reputation(String playerKey) {
        EnumMap<GossipType, Integer> entries = byPlayer.get(playerKey);
        if (entries == null) return 0;
        int total = 0;
        for (Map.Entry<GossipType, Integer> entry : entries.entrySet()) {
            total += VillagerGossipRules.weightedValue(entry.getKey(), entry.getValue());
        }
        return total;
    }

    /** 24000틱마다 한 번 도는 감쇠. */
    public void decay() {
        byPlayer.entrySet().removeIf(playerEntry -> {
            EnumMap<GossipType, Integer> entries = playerEntry.getValue();
            entries.entrySet().removeIf(entry -> {
                int decayed = VillagerGossipRules.decayedValue(entry.getKey(), entry.getValue());
                if (decayed == 0) return true;
                entry.setValue(decayed);
                return false;
            });
            return entries.isEmpty();
        });
    }

    /** 정렬된 전체 항목. 순서는 (플레이어 키, 종류 선언순)이다. */
    public List<Entry> unpack() {
        List<Entry> unpacked = new ArrayList<>();
        for (Map.Entry<String, EnumMap<GossipType, Integer>> player : byPlayer.entrySet()) {
            for (Map.Entry<GossipType, Integer> entry : player.getValue().entrySet()) {
                unpacked.add(new Entry(player.getKey(), entry.getKey(), entry.getValue()));
            }
        }
        return unpacked;
    }

    /**
     * 가중치 |value*weight| 로 {@code draws} 번 추첨한다(중복은 한 번만 남는다).
     * 바닐라 {@code GossipContainer#selectGossipsForTransfer} 와 같은 누적 이분 탐색이다.
     */
    public List<Entry> selectForTransfer(IntBound random, int draws) {
        List<Entry> all = unpack();
        if (all.isEmpty() || draws <= 0) return List.of();
        int[] cumulative = new int[all.size()];
        int total = 0;
        for (int index = 0; index < all.size(); index++) {
            total += Math.abs(all.get(index).weightedValue());
            cumulative[index] = total;
        }
        if (total <= 0) return List.of();
        Set<Entry> selected = new LinkedHashSet<>();
        for (int draw = 0; draw < draws; draw++) {
            int roll = random.nextInt(total);
            if (roll < 0 || roll >= total) {
                throw new IllegalArgumentException("gossip transfer draw must be in [0," + total + ")");
            }
            selected.add(all.get(indexFor(cumulative, roll)));
        }
        List<Entry> result = new ArrayList<>(selected);
        result.sort(Comparator.comparing(Entry::playerKey).thenComparing(entry -> entry.type().ordinal()));
        return result;
    }

    /** 상대에게서 뽑아온 항목을 전파 감쇠 후 큰 쪽으로 병합한다. */
    public void transferFrom(VillagerGossips source, IntBound random, int draws) {
        for (Entry entry : source.selectForTransfer(random, draws)) {
            int transferred = VillagerGossipRules.transferredValue(entry.type(), entry.value());
            if (transferred == 0) continue;
            put(entry.playerKey(), entry.type(),
                    VillagerGossipRules.mergeForTransfer(
                            value(entry.playerKey(), entry.type()), transferred));
        }
    }

    /**
     * 영속용 문자열. 비어 있으면 {@code null} 이라 저장 컬럼이 그대로 비고, 항목이 있으면
     * {@code 1|<len>:<name>:<code><value>;...} 이다. 이름 길이 접두사가 있어 어떤 문자가 들어와도
     * 탈출 문자가 필요 없고, 정렬이 고정이라 두 권위가 같은 문자열을 만든다.
     */
    public String encode() {
        if (byPlayer.isEmpty()) return null;
        StringBuilder builder = new StringBuilder(CODEC_VERSION).append('|');
        for (Entry entry : unpack()) {
            builder.append(entry.playerKey().length()).append(':')
                    .append(entry.playerKey()).append(':')
                    .append(typeCode(entry.type())).append(entry.value()).append(';');
        }
        return builder.toString();
    }

    /** 손상된 문자열은 예외 없이 빈 기억으로 읽는다. 평판 하나 때문에 월드가 못 열려선 안 된다. */
    public static VillagerGossips decode(String encoded) {
        VillagerGossips gossips = new VillagerGossips();
        if (encoded == null || encoded.isEmpty()) return gossips;
        if (!encoded.startsWith(CODEC_VERSION + "|")) return gossips;
        int cursor = CODEC_VERSION.length() + 1;
        while (cursor < encoded.length()) {
            int nameLengthEnd = encoded.indexOf(':', cursor);
            if (nameLengthEnd < 0) return gossips;
            int nameLength;
            try {
                nameLength = Integer.parseInt(encoded.substring(cursor, nameLengthEnd));
            } catch (NumberFormatException failure) {
                return gossips;
            }
            int nameStart = nameLengthEnd + 1;
            int nameEnd = nameStart + nameLength;
            if (nameLength <= 0 || nameEnd + 1 >= encoded.length()) return gossips;
            if (encoded.charAt(nameEnd) != ':') return gossips;
            String name = encoded.substring(nameStart, nameEnd);
            GossipType type = typeFor(encoded.charAt(nameEnd + 1));
            if (type == null) return gossips;
            int valueEnd = encoded.indexOf(';', nameEnd + 2);
            if (valueEnd < 0) return gossips;
            int value;
            try {
                value = Integer.parseInt(encoded.substring(nameEnd + 2, valueEnd));
            } catch (NumberFormatException failure) {
                return gossips;
            }
            int stored = VillagerGossipRules.clampStoredValue(type, value);
            if (stored > 0) gossips.put(name, type, stored);
            cursor = valueEnd + 1;
        }
        return gossips;
    }

    private void put(String playerKey, GossipType type, int value) {
        if (value <= 0) {
            EnumMap<GossipType, Integer> entries = byPlayer.get(playerKey);
            if (entries == null) return;
            entries.remove(type);
            if (entries.isEmpty()) byPlayer.remove(playerKey);
            return;
        }
        byPlayer.computeIfAbsent(playerKey, key -> new EnumMap<>(GossipType.class)).put(type, value);
    }

    private static int indexFor(int[] cumulative, int roll) {
        int low = 0;
        int high = cumulative.length - 1;
        while (low < high) {
            int middle = (low + high) >>> 1;
            if (roll < cumulative[middle]) high = middle;
            else low = middle + 1;
        }
        return low;
    }

    static char typeCode(GossipType type) {
        return switch (type) {
            case MAJOR_NEGATIVE -> 'B';
            case MINOR_NEGATIVE -> 'b';
            case MINOR_POSITIVE -> 'a';
            case MAJOR_POSITIVE -> 'A';
            case TRADING -> 'T';
        };
    }

    static GossipType typeFor(char code) {
        return switch (code) {
            case 'B' -> GossipType.MAJOR_NEGATIVE;
            case 'b' -> GossipType.MINOR_NEGATIVE;
            case 'a' -> GossipType.MINOR_POSITIVE;
            case 'A' -> GossipType.MAJOR_POSITIVE;
            case 'T' -> GossipType.TRADING;
            default -> null;
        };
    }
}
