package com.gameexpert.engine.redstone;

import java.util.ArrayList;
import java.util.List;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** [REDSTONE] 월드 시계 행에 저장하는 예약 틱·이동 중 피스톤 스냅샷. */
public final class RedstoneSnapshotCodec {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private RedstoneSnapshotCodec() {}

    public static String encode(RedstoneEngineSnapshot snapshot) {
        return JSON.writeValueAsString(snapshot);
    }

    public static RedstoneEngineSnapshot decode(String json) {
        if (json == null || json.isBlank()) return null;
        JsonNode root = JSON.readTree(json);
        validate(root);
        List<RedstoneScheduledTick> ticks = new ArrayList<>();
        for (JsonNode n : root.path("ticks")) ticks.add(new RedstoneScheduledTick(
                n.path("x").asInt(), n.path("y").asInt(), n.path("z").asInt(), n.path("block").asInt(),
                n.path("triggerTick").asLong(), n.path("priority").asInt(), n.path("subTickOrder").asLong()));
        List<RedstoneMovingBlock> moving = new ArrayList<>();
        for (JsonNode n : root.path("movingBlocks")) moving.add(new RedstoneMovingBlock(
                n.path("x").asInt(), n.path("y").asInt(), n.path("z").asInt(), n.path("movedBlock").asInt(),
                n.path("movedState").asInt(), n.path("direction").asInt(), n.path("extending").asBoolean(),
                n.path("source").asBoolean(), n.path("progress").asDouble(), n.path("progressO").asDouble(), -1));
        List<int[]> daylight = new ArrayList<>();
        for (JsonNode n : root.path("daylightDetectors")) daylight.add(new int[] {
                n.get(0).asInt(), n.get(1).asInt(), n.get(2).asInt()});
        return new RedstoneEngineSnapshot(ticks, moving, daylight, root.path("subTickCounter").asLong());
    }
    private static void validate(JsonNode root) {
        require(root != null && root.isObject() && integer(root.path("subTickCounter"))
                && root.path("subTickCounter").asLong() >= 0);
        JsonNode ticks = root.path("ticks"), moving = root.path("movingBlocks"), detectors = root.path("daylightDetectors");
        require(ticks.isArray() && ticks.size() <= 65_536 && moving.isArray() && moving.size() <= 16_384
                && detectors.isArray() && detectors.size() <= 65_536);
        for (JsonNode n : ticks) require(n.isObject() && position(n.path("x"), n.path("y"), n.path("z"))
                && integer(n.path("block")) && integer(n.path("triggerTick")) && integer(n.path("priority"))
                && n.path("priority").asInt() >= -3 && n.path("priority").asInt() <= 3 && integer(n.path("subTickOrder")));
        for (JsonNode n : moving) require(n.isObject() && position(n.path("x"), n.path("y"), n.path("z"))
                && integer(n.path("movedBlock")) && integer(n.path("movedState")) && n.path("movedState").asInt() >= 0
                && n.path("movedState").asInt() <= 255 && integer(n.path("direction")) && n.path("direction").asInt() >= 0
                && n.path("direction").asInt() <= 5 && n.path("extending").isBoolean() && n.path("source").isBoolean()
                && n.path("progress").isNumber() && Double.isFinite(n.path("progress").asDouble())
                && n.path("progressO").isNumber() && Double.isFinite(n.path("progressO").asDouble()));
        for (JsonNode n : detectors) require(n.isArray() && n.size() == 3 && position(n.get(0), n.get(1), n.get(2)));
    }
    private static boolean integer(JsonNode n) { return n != null && n.isIntegralNumber() && n.canConvertToLong(); }
    private static boolean position(JsonNode x, JsonNode y, JsonNode z) {
        return integer(x) && x.canConvertToInt() && integer(y) && y.asLong() >= -64 && y.asLong() <= 319
                && integer(z) && z.canConvertToInt();
    }
    private static void require(boolean valid) {
        if (!valid) throw new IllegalStateException("Invalid persisted redstone engine state");
    }

}
