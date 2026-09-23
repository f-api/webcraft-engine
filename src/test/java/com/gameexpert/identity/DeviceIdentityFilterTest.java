package com.gameexpert.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;

class DeviceIdentityFilterTest {

    private static final Pattern NICKNAME_RULE = Pattern.compile("[A-Za-z0-9_]{2,12}");
    private static final String KEY = "AbCdEfGhIjKlMnOpQrSt";

    private final DeviceNicknames nicknames = mock(DeviceNicknames.class);
    private final DeviceIdentityFilter filter = new DeviceIdentityFilter(nicknames);

    @Test
    void issuesTheCookieOnceAndLeavesTheFirstRequestAlone() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/ws/worlds/1");
        request.setQueryString("nickname=steve");
        request.setParameter("nickname", "steve");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        Cookie cookie = response.getCookie(DeviceIdentityFilter.COOKIE_NAME);
        assertThat(cookie).isNotNull();
        assertThat(cookie.getValue()).matches("[A-Za-z0-9_-]{16,64}");
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.getMaxAge()).isEqualTo(DeviceIdentityFilter.COOKIE_MAX_AGE_SECONDS);
        assertThat(((HttpServletRequest) chain.getRequest()).getParameter("nickname")).isEqualTo("steve");
    }

    @Test
    void rewritesTheWebSocketNicknameForASecondDevice() throws Exception {
        when(nicknames.storedFor(anyString(), anyString())).thenReturn("steve_a1b2");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/ws/worlds/1");
        request.setQueryString("nickname=steve&debug=1");
        request.setParameter("nickname", "steve");
        request.setParameter("debug", "1");
        request.setCookies(new Cookie(DeviceIdentityFilter.COOKIE_NAME, KEY));
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        HttpServletRequest seen = (HttpServletRequest) chain.getRequest();
        assertThat(seen.getParameter("nickname")).isEqualTo("steve_a1b2");
        assertThat(seen.getParameter("debug")).isEqualTo("1");
        assertThat(seen.getQueryString()).contains("nickname=steve_a1b2").contains("debug=1");
    }

    @Test
    void rewritesTheRegistrationBodyAndItsLength() throws Exception {
        when(nicknames.storedFor(anyString(), anyString())).thenReturn("steve_a1b2");
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/players");
        request.setContentType("application/json;charset=UTF-8");
        request.setContent("{\"nickname\":\"steve\"}".getBytes(StandardCharsets.UTF_8));
        request.setCookies(new Cookie(DeviceIdentityFilter.COOKIE_NAME, KEY));
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        HttpServletRequest seen = (HttpServletRequest) chain.getRequest();
        String body = new String(seen.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(body).isEqualTo("{\"nickname\":\"steve_a1b2\"}");
        assertThat(seen.getContentLength()).isEqualTo(body.getBytes(StandardCharsets.UTF_8).length);
        assertThat(seen.getHeader("Content-Length")).isEqualTo(String.valueOf(seen.getContentLength()));
    }

    @Test
    void leavesTheFirstClaimerAndForeignShapesUntouched() throws Exception {
        when(nicknames.storedFor(anyString(), anyString())).thenAnswer(call -> call.getArgument(1));
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/players");
        request.setContentType("application/json");
        request.setContent("{\"nickname\":\"steve\"}".getBytes(StandardCharsets.UTF_8));
        request.setCookies(new Cookie(DeviceIdentityFilter.COOKIE_NAME, KEY));
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, new MockHttpServletResponse(), chain);
        assertThat(new String(((HttpServletRequest) chain.getRequest()).getInputStream().readAllBytes(),
                StandardCharsets.UTF_8)).isEqualTo("{\"nickname\":\"steve\"}");

        MockHttpServletRequest form = new MockHttpServletRequest("POST", "/players");
        form.setContentType("application/x-www-form-urlencoded");
        form.setContent("nickname=steve".getBytes(StandardCharsets.UTF_8));
        form.setCookies(new Cookie(DeviceIdentityFilter.COOKIE_NAME, KEY));
        MockFilterChain formChain = new MockFilterChain();
        filter.doFilter(form, new MockHttpServletResponse(), formChain);
        assertThat(formChain.getRequest()).isSameAs(form);
    }

    @Test
    void tagsStayInsideTheAssignmentNicknameRule() {
        for (int attempt = 0; attempt < 12; attempt++) {
            String tagged = DeviceNicknames.tagged("abcdefghijkl", DeviceNicknames.hash(KEY), attempt);
            assertThat(tagged).matches(NICKNAME_RULE).hasSizeLessThanOrEqualTo(DeviceNicknames.MAX_LENGTH);
            assertThat(DeviceNicknames.tagged("ab", DeviceNicknames.hash(KEY), attempt)).matches(NICKNAME_RULE);
        }
        assertThat(DeviceNicknames.tagged("steve", DeviceNicknames.hash(KEY), 0))
                .isNotEqualTo(DeviceNicknames.tagged("steve", DeviceNicknames.hash("other-device-key"), 0));
        assertThat(DeviceNicknames.tagged("steve", DeviceNicknames.hash(KEY), 0))
                .isNotEqualTo(DeviceNicknames.tagged("steve", DeviceNicknames.hash(KEY), 1));
    }
}
