package com.gameexpert.ws.handler;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.gameexpert.config.EngineProperties;
import com.gameexpert.engine.PlayerAction;
import com.gameexpert.engine.WorldEngineManager;
import com.gameexpert.ws.WsMessageContext;
import com.gameexpert.api.persistence.WorldStore;
import com.gameexpert.engine.qa.ContentQaFixturePlan;
import com.gameexpert.engine.qa.DenseWorldPerformanceFixturePlan;

import tools.jackson.databind.JsonNode;


/**
 * QA 시딩 명령({@code qaSeed})을 월드 틱 큐에 넣습니다.
 *
 * <p>브라우저 기능 QA는 거래·전단·염색·레이드·석궁처럼 특정 아이템/몹이 있어야만 도달하는
 * 경로를 검증해야 하는데, 일반 플레이로 그 준비 상태를 만들려면 QA 한 회차가 몇 시간짜리
 * 파밍이 됩니다. 그래서 QA 서버에만 여는 시딩 통로를 둡니다.</p>
 *
 * <p>안전 장치는 두 겹입니다.</p>
 * <ul>
 *   <li>서버: {@code game.qa-seeding} 이 기본 false 이므로 일반 배포 서버는 이 메시지를
 *       조용히 무시합니다(액션 큐에 아무것도 넣지 않습니다).</li>
 *   <li>클라이언트: 이 메시지를 보내는 코드는 {@code __DEBUG__} 분기 안에만 있어 프로덕션
 *       번들에서 dead-code 로 제거됩니다.</li>
 * </ul>
 *
 * <p>시딩은 권위 상태를 직접 조립하지 않습니다. 실제 게임플레이가 쓰는 인벤토리
 * {@code addItem}, 몹 런타임 스폰, 월드 시계 API 만 호출하므로 시딩으로 만든 상태와 평소
 * 플레이로 만든 상태는 구분되지 않습니다({@code WorldTickLoop#applyQaSeed}).</p>
 */
@Component
public class QaSeedWsHandler implements EngineMessageHandler {

    private static final java.util.Set<String> FINAL_SCENE_SCENARIOS = java.util.Set.of(
            "H12a", "H12b", "H12c-glide", "H12c-wall", "H12c-firework",
            "H12d-visual", "H12d-feedback", "H12e", "H12f", "H12g");
    private static final java.util.regex.Pattern FINAL_SCENE_NONCE =
            java.util.regex.Pattern.compile("[A-Za-z0-9._:-]{8,128}");
    private static final java.util.Set<String> FINAL_SCENE_REQUEST_FIELDS = java.util.Set.of(
            "type", "command", "argument", "amount", "x", "y", "z",
            "finalSceneScenario", "finalSceneActionNonce");

    private final WorldEngineManager engineManager;
    private final EngineProperties properties;
    private final WorldStore worlds;

    /** 단위 테스트와 비-fixture QA 명령용 축약 생성자. fixture 권한은 저장소 없이는 절대 생기지 않는다. */
    public QaSeedWsHandler(WorldEngineManager engineManager, EngineProperties properties) {
        this(engineManager, properties, null);
    }

    @Autowired
    public QaSeedWsHandler(WorldEngineManager engineManager, EngineProperties properties,
            WorldStore worlds) {
        this.engineManager = engineManager;
        this.properties = properties;
        this.worlds = worlds;
    }

    @Override
    public String type() {
        return "qaSeed";
    }

    @Override
    public void handle(WsMessageContext context, JsonNode message) {
        if (!properties.qaSeeding()) return;
        String command = WsFields.text(message, "command");
        String argument = WsFields.text(message, "argument");
        if ("mobAuditStage".equals(command)) {
            var stage = PlayerAction.MobAuditStage.fromWire(argument);
            JsonNode id = message.get("mobId");
            if (stage == null || worlds == null || id == null || !id.isIntegralNumber()
                    || !id.canConvertToLong() || id.longValue() <= 0
                    || id.longValue() > 9_007_199_254_740_991L) return;
            var world = worlds.findById(context.worldId()).orElse(null);
            if (world == null || !properties.isContentQaWorld(world.getName())
                    || world.getSeed() != EngineProperties.CONTENT_QA_WORLD_SEED) return;
            engineManager.enqueue(context.worldId(), new PlayerAction.QaMobAuditStage(
                    context.nickname(), stage, id.longValue(), true));
            return;
        }
        double x = WsFields.finiteNumber(message, "x");
        double y = WsFields.finiteNumber(message, "y");
        double z = WsFields.finiteNumber(message, "z");
        if ("performanceFixture".equals(command)) {
            enqueuePerformanceFixture(context, message, argument, x, y, z);
            return;
        }
        boolean fixtureAuthorized = false;
        String finalSceneScenario = null;
        String finalSceneActionNonce = null;
        if ("ecologyAuditStage".equals(command)) {
            if (worlds == null || !("sniffer-egg-next".equals(argument)
                    || "dried-ghast-next".equals(argument))) return;
            for (String coordinate : new String[] {"x", "y", "z"}) {
                JsonNode value = message.get(coordinate);
                if (value == null || !value.isNumber() || !Double.isFinite(value.doubleValue())
                        || value.doubleValue() != Math.rint(value.doubleValue())) return;
            }
            JsonNode expected = message.get("amount");
            int maxState = "sniffer-egg-next".equals(argument) ? 2 : 3;
            if (x < 0 || x > 31 || z < 0 || z > 31 || y < 72 || y > 76
                    || expected == null || !expected.isIntegralNumber() || !expected.canConvertToInt()
                    || expected.intValue() < 0 || expected.intValue() > maxState) return;
            var world = worlds.findById(context.worldId()).orElse(null);
            if (world == null || !EngineProperties.CONTENT_QA_WORLD_NAME.equals(world.getName())
                    || world.getSeed() != EngineProperties.CONTENT_QA_WORLD_SEED) return;
            fixtureAuthorized = true;
        }
        if ("auditStage".equals(command)) {
            if (!"copper-next".equals(argument) || worlds == null
                    || x != 1 || y != 72 || z != 25) return;
            JsonNode expected = message.get("amount");
            if (expected == null || !expected.isIntegralNumber() || !expected.canConvertToInt()
                    || expected.intValue() < com.gameexpert.terrain.Blocks.COPPER_CHEST
                    || expected.intValue() >= com.gameexpert.terrain.Blocks.OXIDIZED_COPPER_CHEST) return;
            var world = worlds.findById(context.worldId()).orElse(null);
            if (world == null || !properties.isContentQaWorld(world.getName())
                    || world.getSeed() != EngineProperties.CONTENT_QA_WORLD_SEED) return;
            fixtureAuthorized = true;
        }
        if ("fixture".equals(command)) {
            if (!ContentQaFixturePlan.FIXTURE_ID.equals(argument) || worlds == null) return;
            var world = worlds.findById(context.worldId()).orElse(null);
            if (world == null || !properties.isContentQaWorld(world.getName())) return;
            try {
                ContentQaFixturePlan.create(exactInt(x), exactInt(y), exactInt(z));
            } catch (IllegalArgumentException malformedAnchor) {
                return;
            }
            fixtureAuthorized = true;
            JsonNode scenarioNode = message.get("finalSceneScenario");
            JsonNode nonceNode = message.get("finalSceneActionNonce");
            if (scenarioNode != null || nonceNode != null) {
                java.util.HashSet<String> fields = new java.util.HashSet<>();
                message.propertyNames().forEach(fields::add);
                if (scenarioNode == null || nonceNode == null || !scenarioNode.isTextual()
                        || !nonceNode.isTextual()
                        || !fields.equals(FINAL_SCENE_REQUEST_FIELDS)
                        || !FINAL_SCENE_SCENARIOS.contains(scenarioNode.textValue())
                        || !FINAL_SCENE_NONCE.matcher(nonceNode.textValue()).matches()) return;
                finalSceneScenario = scenarioNode.textValue();
                finalSceneActionNonce = nonceNode.textValue();
            }
        }
        engineManager.enqueue(context.worldId(), new PlayerAction.QaSeed(
                context.nickname(),
                command,
                argument,
                WsFields.integer(message, "amount"),
                x, y, z, fixtureAuthorized, finalSceneScenario, finalSceneActionNonce));
    }

    private void enqueuePerformanceFixture(WsMessageContext context, JsonNode message,
            String fixtureId, double x, double y, double z) {
        if (worlds == null || !DenseWorldPerformanceFixturePlan.WORLD_NAME.equals(fixtureId)) return;
        var world = worlds.findById(context.worldId()).orElse(null);
        if (world == null || !properties.isPerformanceQaWorld(world.getName())
                || world.getSeed() != DenseWorldPerformanceFixturePlan.WORLD_SEED) return;
        try {
            int anchorX = exactInt(x);
            int floorY = exactInt(y);
            int anchorZ = exactInt(z);
            String variant = WsFields.text(message, "variant");
            PlayerAction.PerformanceFixtureOperation operation = switch (
                    WsFields.text(message, "operation")) {
                case "install" -> PlayerAction.PerformanceFixtureOperation.INSTALL;
                case "start" -> PlayerAction.PerformanceFixtureOperation.START;
                default -> throw new IllegalArgumentException("unknown performance fixture operation");
            };
            String checksumText = WsFields.text(message, "checksum");
            long checksum = parseCanonicalUnsignedLong(checksumText);
            var plan = DenseWorldPerformanceFixturePlan.create(
                    variant, anchorX, floorY, anchorZ);
            if (checksum != plan.checksum()) return;
            engineManager.enqueue(context.worldId(), new PlayerAction.QaPerformanceFixture(
                    context.nickname(), operation, plan));
        } catch (IllegalArgumentException malformed) {
            // Malformed QA input is deliberately indistinguishable from a disabled fixture lane.
        }
    }

    private static long parseCanonicalUnsignedLong(String value) {
        long parsed = Long.parseUnsignedLong(value);
        if (!Long.toUnsignedString(parsed).equals(value)) {
            throw new IllegalArgumentException("fixture checksum must be canonical unsigned decimal");
        }
        return parsed;
    }

    private static int exactInt(double value) {
        if (value != Math.rint(value) || value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("fixture anchor must be an exact int");
        }
        return (int) value;
    }
}
