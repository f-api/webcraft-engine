package com.gameexpert.engine.trial.persistence;

import com.gameexpert.engine.raid.RaidLedger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/** Strict, versioned storage codec. Server and schema ship together; malformed rows fail hydration. */
public final class TrialPersistenceCodec {
    private static final String LEDGER_VERSION = "WCTL1";
    private static final String POSITION_VERSION = "WCTV1";
    private TrialPersistenceCodec() {}

    public static String encodeLedger(RaidLedger.InstanceSnapshot value) {
        if (value == null) return null;
        StringBuilder encoded = new StringBuilder(256).append(LEDGER_VERSION).append('|')
                .append(value.ledgerVersion()).append('|').append(value.raidId()).append('|')
                .append(value.anchorMobId()).append('|').append(value.centerX()).append('|')
                .append(value.centerY()).append('|').append(value.centerZ()).append('|')
                .append(text(value.heroNickname())).append('|').append(value.armedTick()).append('|')
                .append(value.rewardSeed()).append('|').append(value.waveCount()).append('|')
                .append(value.releasedWaves()).append('|').append(value.activeTicks()).append('|')
                .append(value.status()).append('|').append(value.resolvedTick()).append('|')
                .append(value.members().size());
        for (RaidLedger.MemberSnapshot member : value.members()) {
            encoded.append('|').append(member.mobId()).append(',').append(member.wave())
                    .append(',').append(member.maxHealth()).append(',').append(member.health())
                    .append(',').append(member.retired() ? '1' : '0');
        }
        return encoded.toString();
    }

    public static RaidLedger.InstanceSnapshot decodeLedger(String encoded) {
        if (encoded == null) return null;
        String[] fields = encoded.split("\\|", -1);
        try {
            if (fields.length < 16 || !LEDGER_VERSION.equals(fields[0])) throw malformed();
            int memberCount = integer(fields[15]);
            if (memberCount < 0 || fields.length != 16 + memberCount) throw malformed();
            List<RaidLedger.MemberSnapshot> members = new ArrayList<>(memberCount);
            for (int i = 0; i < memberCount; i++) {
                String[] member = fields[16 + i].split(",", -1);
                if (member.length != 5 || !("0".equals(member[4]) || "1".equals(member[4]))) throw malformed();
                members.add(new RaidLedger.MemberSnapshot(Long.parseLong(member[0]), integer(member[1]),
                        Double.parseDouble(member[2]), Double.parseDouble(member[3]), "1".equals(member[4])));
            }
            return new RaidLedger.InstanceSnapshot(integer(fields[1]), Long.parseLong(fields[2]),
                    Long.parseLong(fields[3]), Double.parseDouble(fields[4]), Double.parseDouble(fields[5]),
                    Double.parseDouble(fields[6]), untext(fields[7]), Long.parseLong(fields[8]),
                    Long.parseLong(fields[9]), integer(fields[10]), integer(fields[11]), integer(fields[12]),
                    fields[13], Long.parseLong(fields[14]), List.copyOf(members));
        } catch (NumberFormatException failure) { throw malformed(failure); }
    }

    public static String encodePositions(List<int[]> positions) {
        StringBuilder encoded = new StringBuilder(POSITION_VERSION);
        for (int[] position : positions) {
            if (position == null || position.length != 3) throw malformed();
            encoded.append('|').append(position[0]).append(',').append(position[1]).append(',').append(position[2]);
        }
        return encoded.toString();
    }

    public static List<int[]> decodePositions(String encoded) {
        String[] fields = encoded.split("\\|", -1);
        if (fields.length == 0 || !POSITION_VERSION.equals(fields[0])) throw malformed();
        List<int[]> positions = new ArrayList<>(Math.max(0, fields.length - 1));
        try {
            for (int i = 1; i < fields.length; i++) {
                String[] xyz = fields[i].split(",", -1);
                if (xyz.length != 3) throw malformed();
                positions.add(new int[] {integer(xyz[0]), integer(xyz[1]), integer(xyz[2])});
            }
            return List.copyOf(positions);
        } catch (NumberFormatException failure) { throw malformed(failure); }
    }

    private static String text(String value) {
        return value == null ? "-" : Base64.getUrlEncoder().withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
    private static String untext(String value) {
        if ("-".equals(value)) return null;
        try { return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8); }
        catch (IllegalArgumentException failure) { throw malformed(failure); }
    }
    private static int integer(String value) { return Integer.parseInt(value); }
    private static IllegalStateException malformed() { return new IllegalStateException("malformed persisted trial state"); }
    private static IllegalStateException malformed(Exception cause) { return new IllegalStateException("malformed persisted trial state", cause); }
}
