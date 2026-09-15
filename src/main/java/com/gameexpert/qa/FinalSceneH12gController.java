package com.gameexpert.qa;

import com.gameexpert.common.InvalidRequestException;
import com.gameexpert.config.EngineProperties;
import com.gameexpert.api.persistence.PlayerStore;
import com.gameexpert.api.persistence.WorldAccess;
import com.gameexpert.api.persistence.WorldStore;
import com.gameexpert.ws.SessionAttributes;
import com.gameexpert.api.SessionRegistry;
import jakarta.servlet.http.HttpServletRequest;
import java.io.InputStream;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.socket.WebSocketSession;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/** Loopback-QA, outcome-only Spring H12g boundary. */
@RestController
public final class FinalSceneH12gController {
    public static final String PATH = "/worlds/{worldId}/debug/final-scene-h12g/execute";
    static final int MAX_REQUEST_BYTES = 1_024;
    private static final Pattern NICKNAME = Pattern.compile("[A-Za-z0-9_]{2,12}");
    private static final Pattern WORLD = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,29}");
    private static final ObjectMapper REQUEST_JSON = JsonMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY,
                    DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .build();

    private final EngineProperties properties;
    private final FinalSceneH12gOutcomeService outcomes;
    private final PlayerStore players;
    private final WorldStore worlds;
    private final SessionRegistry sessions;

    public FinalSceneH12gController(EngineProperties properties,
            FinalSceneH12gOutcomeService outcomes, PlayerStore players,
            WorldStore worlds, SessionRegistry sessions) {
        this.properties = Objects.requireNonNull(properties, "engine properties");
        this.outcomes = Objects.requireNonNull(outcomes, "H12g outcome service");
        this.players = Objects.requireNonNull(players, "player repository");
        this.worlds = Objects.requireNonNull(worlds, "world repository");
        this.sessions = Objects.requireNonNull(sessions, "world session registry");
    }

    @PostMapping(PATH)
    public ResponseEntity<FinalSceneH12gOutcomeService.Outcome> execute(
            @PathVariable long worldId, HttpServletRequest request) {
        if (!isQaLoopback(request)) return ResponseEntity.notFound().build();
        Request input = readRequest(request);
        WorldAccess world = requireWorldAndPlayer(worldId, input);
        SessionRegistry.Entry entry = requireActiveSession(worldId, input.nickname());
        return ResponseEntity.ok(outcomes.execute(new FinalSceneH12gOutcomeService.ActiveBinding(
                worldId, world.getName(), input.nickname(), entry.connectionId())));
    }

    private WorldAccess requireWorldAndPlayer(long worldId, Request input) {
        if (worldId <= 0L || players.findByNickname(input.nickname()).isEmpty()) {
            throw invalidBody();
        }
        WorldAccess world = worlds.findById(worldId).orElseThrow(FinalSceneH12gController::invalidBody);
        if (!input.world().equals(world.getName())) throw invalidBody();
        return world;
    }

    private SessionRegistry.Entry requireActiveSession(long worldId, String nickname) {
        SessionRegistry.Entry entry = sessions.get(worldId, nickname);
        if (entry == null) throw invalidBody();
        WebSocketSession session = entry.session();
        Object sessionNickname = session.getAttributes().get(
                SessionAttributes.ATTR_NICKNAME);
        Object sessionWorld = session.getAttributes().get(
                SessionAttributes.ATTR_WORLD_ID);
        if (!session.isOpen() || !nickname.equals(sessionNickname)
                || !(sessionWorld instanceof Long exactWorld) || exactWorld != worldId
                || !entry.connectionId().equals(session.getId())) throw invalidBody();
        return entry;
    }

    private static Request readRequest(HttpServletRequest request) {
        JsonNode body = readBody(request);
        requireExactKeys(body, "scenario", "authority", "world", "nickname", "durationMs");
        String scenario = requiredText(body, "scenario");
        String authority = requiredText(body, "authority");
        String world = requiredText(body, "world");
        String nickname = requiredText(body, "nickname");
        long durationMs = requiredLong(body, "durationMs");
        if (!"H12g".equals(scenario) || !"spring".equals(authority)
                || !WORLD.matcher(world).matches() || !NICKNAME.matcher(nickname).matches()
                || durationMs != 60_000L) throw invalidBody();
        return new Request(world, nickname);
    }

    private boolean isQaLoopback(HttpServletRequest request) {
        return properties.qaSeeding() && request != null
                && isNumericLoopback(request.getRemoteAddr());
    }

    private static boolean isNumericLoopback(String peer) {
        if (peer == null || peer.isEmpty()) return false;
        if (peer.equals("::1") || peer.equals("0:0:0:0:0:0:0:1")) return true;
        String[] octets = peer.split("\\.", -1);
        if (octets.length != 4) return false;
        for (int index = 0; index < octets.length; index++) {
            String octet = octets[index];
            if (octet.isEmpty() || octet.length() > 3
                    || (octet.length() > 1 && octet.charAt(0) == '0')) return false;
            int value = 0;
            for (int character = 0; character < octet.length(); character++) {
                char digit = octet.charAt(character);
                if (digit < '0' || digit > '9') return false;
                value = value * 10 + digit - '0';
                if (value > 255) return false;
            }
            if (index == 0 && value != 127) return false;
        }
        return true;
    }

    private static JsonNode readBody(HttpServletRequest request) {
        try {
            requireRequestBinding(request);
            long declaredLength = request.getContentLengthLong();
            if (declaredLength <= 0L || declaredLength > MAX_REQUEST_BYTES) throw invalidBody();
            byte[] encoded;
            try (InputStream input = request.getInputStream()) {
                encoded = input.readNBytes(MAX_REQUEST_BYTES + 1);
                if (encoded.length != declaredLength || encoded.length > MAX_REQUEST_BYTES
                        || input.read() != -1) throw invalidBody();
            }
            JsonNode body = REQUEST_JSON.readTree(decodeRequestBody(encoded));
            if (body == null || !body.isObject()) throw invalidBody();
            return body;
        } catch (InvalidRequestException failure) {
            throw failure;
        } catch (Exception failure) {
            throw invalidBody();
        }
    }

    private static String decodeRequestBody(byte[] encoded) throws CharacterCodingException {
        return StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(encoded)).toString();
    }

    private static void requireRequestBinding(HttpServletRequest request) {
        if (request == null || !"application/json".equals(singleHeader(request, "Content-Type"))) {
            throw invalidBody();
        }
        String origin = singleHeader(request, "Origin");
        if (origin == null) throw invalidBody();
        try {
            URI supplied = URI.create(origin);
            int suppliedPort = supplied.getPort() >= 0 ? supplied.getPort()
                    : supplied.getScheme().equals("https") ? 443 : 80;
            if (supplied.getUserInfo() != null || (supplied.getPath() != null
                    && !supplied.getPath().isEmpty()) || supplied.getQuery() != null
                    || supplied.getFragment() != null
                    || !request.getScheme().equals(supplied.getScheme())
                    || supplied.getHost() == null
                    || !request.getServerName().equalsIgnoreCase(supplied.getHost())
                    || request.getServerPort() != suppliedPort) throw invalidBody();
        } catch (InvalidRequestException invalid) {
            throw invalid;
        } catch (RuntimeException malformedOrigin) {
            throw invalidBody();
        }
    }

    private static String singleHeader(HttpServletRequest request, String name) {
        Enumeration<String> values = request.getHeaders(name);
        if (values == null || !values.hasMoreElements()) return null;
        String value = values.nextElement();
        return values.hasMoreElements() ? null : value;
    }

    private static void requireExactKeys(JsonNode object, String... keys) {
        if (object == null || !object.isObject() || object.size() != keys.length) {
            throw invalidBody();
        }
        Set<String> expected = Set.of(keys);
        for (Map.Entry<String, JsonNode> field : object.properties()) {
            if (!expected.contains(field.getKey())) throw invalidBody();
        }
    }

    private static String requiredText(JsonNode object, String field) {
        JsonNode value = object.get(field);
        if (value == null || !value.isString()) throw invalidBody();
        return value.asString();
    }

    private static long requiredLong(JsonNode object, String field) {
        JsonNode value = object.get(field);
        if (value == null || !value.isIntegralNumber() || !value.canConvertToLong()) {
            throw invalidBody();
        }
        return value.longValue();
    }

    private static InvalidRequestException invalidBody() {
        return new InvalidRequestException("INVALID_REQUEST_BODY");
    }

    private record Request(String world, String nickname) { }
}
