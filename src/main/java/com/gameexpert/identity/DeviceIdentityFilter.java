package com.gameexpert.identity;

import java.io.ByteArrayInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

/**
 * 브라우저마다 오래 남는 쿠키를 주고, 그 기기가 쓸 닉네임으로 요청을 바꿔 준다.
 *
 * 쿠키가 아직 없는 요청은 건드리지 않고 쿠키만 내려준다. 그래서 쿠키를 보내지 않는
 * 테스트나 API 호출은 지금까지와 똑같이 동작하고, 브라우저로 들어온 접속만 기기별
 * 닉네임을 쓴다. 뒤쪽(앱)에서 보는 닉네임이 이미 기기별로 갈라져 있으므로 접속 등록,
 * 세션, 저장, 채팅까지 별도 처리가 필요 없다.
 */
@Component
@RequiredArgsConstructor
public class DeviceIdentityFilter extends OncePerRequestFilter implements Ordered {

    public static final String COOKIE_NAME = "wc_device";
    static final int COOKIE_MAX_AGE_SECONDS = 10 * 365 * 24 * 60 * 60;

    private static final Pattern DEVICE_KEY = Pattern.compile("[A-Za-z0-9_-]{16,64}");
    private static final Pattern NICKNAME_FIELD =
            Pattern.compile("(\"nickname\"\\s*:\\s*\")([^\"\\\\]{1,64})(\")");
    private static final Pattern NICKNAME_RULE = Pattern.compile("[A-Za-z0-9_]{1,12}");
    private static final int MAX_BODY_BYTES = 64 * 1024;

    private final DeviceNicknames nicknames;
    private final SecureRandom random = new SecureRandom();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String deviceKey = deviceKey(request);
        if (deviceKey == null) {
            issueCookie(request, response);
            chain.doFilter(request, response);
            return;
        }
        String deviceHash = DeviceNicknames.hash(deviceKey);
        HttpServletRequest rewritten = rewriteQuery(request, deviceHash);
        rewritten = rewriteBody(rewritten, deviceHash);
        chain.doFilter(rewritten, response);
    }

    private String deviceKey(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (COOKIE_NAME.equals(cookie.getName()) && cookie.getValue() != null
                    && DEVICE_KEY.matcher(cookie.getValue()).matches()) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private void issueCookie(HttpServletRequest request, HttpServletResponse response) {
        byte[] value = new byte[16];
        random.nextBytes(value);
        Cookie cookie = new Cookie(COOKIE_NAME, Base64.getUrlEncoder().withoutPadding().encodeToString(value));
        cookie.setPath("/");
        cookie.setHttpOnly(true);
        cookie.setMaxAge(COOKIE_MAX_AGE_SECONDS);
        cookie.setSecure(request.isSecure());
        cookie.setAttribute("SameSite", "Lax");
        response.addCookie(cookie);
    }

    /** WebSocket 접속처럼 주소에 닉네임이 실려 오는 요청. */
    private HttpServletRequest rewriteQuery(HttpServletRequest request, String deviceHash) {
        String query = request.getQueryString();
        if (query == null || !query.contains("nickname=")) {
            return request;
        }
        Map<String, String> values = new LinkedHashMap<>();
        for (String pair : query.split("&")) {
            int equals = pair.indexOf('=');
            if (equals < 0) {
                values.put(decode(pair), "");
            } else {
                values.put(decode(pair.substring(0, equals)), decode(pair.substring(equals + 1)));
            }
        }
        String display = values.get("nickname");
        String stored = storedOrNull(deviceHash, display);
        if (stored == null) {
            return request;
        }
        values.put("nickname", stored);
        StringBuilder rebuilt = new StringBuilder();
        values.forEach((name, value) -> {
            if (!rebuilt.isEmpty()) rebuilt.append('&');
            rebuilt.append(encode(name)).append('=').append(encode(value));
        });
        Map<String, String[]> parameters = new HashMap<>(request.getParameterMap());
        parameters.put("nickname", new String[] {stored});
        return new RewrittenRequest(request, rebuilt.toString(), parameters, null);
    }

    /** 접속자 등록처럼 JSON 본문에 닉네임이 실려 오는 요청. */
    private HttpServletRequest rewriteBody(HttpServletRequest request, String deviceHash) throws IOException {
        String method = request.getMethod();
        String contentType = request.getContentType();
        if (contentType == null || !contentType.toLowerCase().startsWith("application/json")
                || !("POST".equals(method) || "PUT".equals(method) || "PATCH".equals(method))) {
            return request;
        }
        int declared = request.getContentLength();
        if (declared > MAX_BODY_BYTES) {
            return request;
        }
        byte[] body = request.getInputStream().readAllBytes();
        if (body.length == 0 || body.length > MAX_BODY_BYTES) {
            return new RewrittenRequest(request, null, null, body);
        }
        String text = new String(body, StandardCharsets.UTF_8);
        Matcher matcher = NICKNAME_FIELD.matcher(text);
        StringBuilder rewritten = new StringBuilder();
        boolean changed = false;
        while (matcher.find()) {
            String stored = storedOrNull(deviceHash, matcher.group(2));
            String replacement = stored == null ? matcher.group(2) : stored;
            changed |= stored != null && !stored.equals(matcher.group(2));
            matcher.appendReplacement(rewritten,
                    Matcher.quoteReplacement(matcher.group(1) + replacement + matcher.group(3)));
        }
        matcher.appendTail(rewritten);
        byte[] result = changed ? rewritten.toString().getBytes(StandardCharsets.UTF_8) : body;
        return new RewrittenRequest(request, null, null, result);
    }

    private String storedOrNull(String deviceHash, String display) {
        if (display == null || !NICKNAME_RULE.matcher(display).matches()) {
            return null;
        }
        String stored = nicknames.storedFor(deviceHash, display);
        return stored.equals(display) ? null : stored;
    }

    private static String decode(String value) {
        return java.net.URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private static String encode(String value) {
        return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    @Override
    public int getOrder() { return Ordered.HIGHEST_PRECEDENCE + 10; }

    /** 바꾼 질의 문자열이나 본문을 뒤쪽에 그대로 보여 주는 요청 껍데기입니다. */
    static final class RewrittenRequest extends HttpServletRequestWrapper {

        private final String query;
        private final Map<String, String[]> parameters;
        private final byte[] body;

        RewrittenRequest(HttpServletRequest request, String query, Map<String, String[]> parameters, byte[] body) {
            super(request);
            this.query = query;
            this.parameters = parameters;
            this.body = body;
        }

        @Override public String getQueryString() { return query != null ? query : super.getQueryString(); }

        @Override public String getParameter(String name) {
            if (parameters == null) return super.getParameter(name);
            String[] values = parameters.get(name);
            return values == null || values.length == 0 ? null : values[0];
        }

        @Override public String[] getParameterValues(String name) {
            return parameters == null ? super.getParameterValues(name) : parameters.get(name);
        }

        @Override public Map<String, String[]> getParameterMap() {
            return parameters == null ? super.getParameterMap() : java.util.Collections.unmodifiableMap(parameters);
        }

        @Override public java.util.Enumeration<String> getParameterNames() {
            return parameters == null ? super.getParameterNames()
                    : java.util.Collections.enumeration(parameters.keySet());
        }

        @Override public int getContentLength() { return body == null ? super.getContentLength() : body.length; }

        @Override public long getContentLengthLong() {
            return body == null ? super.getContentLengthLong() : body.length;
        }

        @Override public String getHeader(String name) {
            if (body != null && "content-length".equalsIgnoreCase(name)) {
                return String.valueOf(body.length);
            }
            return super.getHeader(name);
        }

        @Override public ServletInputStream getInputStream() throws IOException {
            if (body == null) return super.getInputStream();
            ByteArrayInputStream source = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override public int read() { return source.read(); }
                @Override public int read(byte[] buffer, int offset, int length) {
                    return source.read(buffer, offset, length);
                }
                @Override public boolean isFinished() { return source.available() == 0; }
                @Override public boolean isReady() { return true; }
                @Override public void setReadListener(ReadListener listener) { }
            };
        }

        @Override public BufferedReader getReader() throws IOException {
            return body == null ? super.getReader()
                    : new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
        }
    }
}
