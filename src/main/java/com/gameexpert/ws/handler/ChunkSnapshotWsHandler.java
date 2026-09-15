package com.gameexpert.ws.handler;

import java.util.Set;

import org.springframework.stereotype.Component;

import com.gameexpert.engine.WorldEngineManager;
import com.gameexpert.terrain.Blocks;
import com.gameexpert.ws.WsMessageContext;
import com.gameexpert.ws.dto.WsMessages.PlayerPose;

import lombok.RequiredArgsConstructor;
import tools.jackson.databind.JsonNode;

/** revision gap을 발견한 클라이언트가 resident authoritative snapshot을 다시 요청하는 진입점입니다. */
@Component
@RequiredArgsConstructor
public class ChunkSnapshotWsHandler implements EngineMessageHandler {

    /** 지연된 요청의 실행 시점에도 런타임이 같은 스트리밍 범위를 재확인한다. */
    public static final int MAX_STREAMING_CHUNK_DISTANCE = 32;

    /** 입장 확정 위치(x). 아직 틱 pose 가 없는 동안의 스트리밍 범위 기준이다. */
    public static final String ATTR_ENTRY_X = "ws.entryX";

    /** 입장 확정 위치(z). */
    public static final String ATTR_ENTRY_Z = "ws.entryZ";

    private final WorldEngineManager engineManager;

    @Override
    public String type() {
        return "chunkSnapshotRequest";
    }

    @Override
    public Set<String> supportedTypes() {
        return Set.of("chunkSnapshotRequest", "chunkSnapshotCancel");
    }

    @Override
    public void handle(WsMessageContext context, JsonNode message) {
        int chunkX = WsFields.integer(message, "cx");
        int chunkZ = WsFields.integer(message, "cz");
        switch (message.path("type").asString()) {
            case "chunkSnapshotRequest" -> {
                if (!withinStreamingRange(context, chunkX, chunkZ)) return;
                engineManager.requestChunkSnapshot(context.worldId(), context.session(), chunkX, chunkZ);
            }
            case "chunkSnapshotCancel" ->
                engineManager.cancelChunkSnapshot(context.worldId(), context.session(), chunkX, chunkZ);
            default -> throw new IllegalArgumentException("알 수 없는 청크 snapshot 메시지입니다.");
        }
    }

    /**
     * 요청 청크가 이 세션의 스트리밍 범위 안인가. 미완료 요청은 좌표별로 남아 있으므로, 범위를 두지
     * 않으면 임의 좌표를 계속 요청하는 것만으로 대기 목록이 무제한으로 커집니다.
     *
     * <p>적응형 원경과 이동 중 pose 지연을 포함한 전송 범위입니다. 서버의 게임플레이
     * 시뮬레이션 반경은 이 상수와 독립적으로 유지합니다.
     */
    private boolean withinStreamingRange(WsMessageContext context, int chunkX, int chunkZ) {
        PlayerPose pose = engineManager.playerPose(context.worldId(), context.nickname());
        // welcome 직후 등 아직 틱 상태가 없는 순간에도 기준을 놓지 않는다. 그러면 입장 전에
        // 임의 좌표를 무제한으로 요청해 대기 목록을 부풀릴 수 있다. 이때는 welcome 이 확정한
        // 입장 좌표를 중심으로 삼고(정상 진입의 스폰 3×3·주변 요청은 모두 이 안이다),
        // 그 기준마저 없으면 요청을 버린다.
        Double centerXCoordinate = pose != null
                ? Double.valueOf(pose.getX()) : entryCoordinate(context, ATTR_ENTRY_X);
        Double centerZCoordinate = pose != null
                ? Double.valueOf(pose.getZ()) : entryCoordinate(context, ATTR_ENTRY_Z);
        if (centerXCoordinate == null || centerZCoordinate == null) return false;
        int centerX = Math.floorDiv((int) Math.floor(centerXCoordinate.doubleValue()), Blocks.CHUNK_X);
        int centerZ = Math.floorDiv((int) Math.floor(centerZCoordinate.doubleValue()), Blocks.CHUNK_Z);
        // 뺄셈은 long 으로 한다. int 차이는 Integer.MIN_VALUE 요청에서 넘쳐 절댓값이 음수가 되고,
        // 그러면 가장 먼 좌표가 오히려 통과한다.
        return Math.abs((long) chunkX - centerX) <= MAX_STREAMING_CHUNK_DISTANCE
                && Math.abs((long) chunkZ - centerZ) <= MAX_STREAMING_CHUNK_DISTANCE;
    }

    /** welcome 이 세션에 기록한 입장 좌표(없으면 null). */
    private static Double entryCoordinate(WsMessageContext context, String attribute) {
        if (context.session() == null) return null;
        Object value = context.session().getAttributes().get(attribute);
        return value instanceof Number number ? number.doubleValue() : null;
    }
}
