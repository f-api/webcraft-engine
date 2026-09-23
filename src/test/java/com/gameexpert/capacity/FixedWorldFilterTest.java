package com.gameexpert.capacity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class FixedWorldFilterTest {

    private FixedWorldFilter filter(boolean fixed) {
        return new FixedWorldFilter(new MockEnvironment().withProperty("webcraft.worlds.fixed", String.valueOf(fixed)));
    }

    @Test
    void refusesNewWorldsAndDeletionsWhenTheServerRunsOneWorld() throws Exception {
        MockHttpServletResponse created = new MockHttpServletResponse();
        MockFilterChain createChain = new MockFilterChain();
        filter(true).doFilter(new MockHttpServletRequest("POST", "/worlds"), created, createChain);
        assertThat(created.getStatus()).isEqualTo(403);
        assertThat(created.getContentAsString()).contains("WORLD_CREATION_DISABLED");
        assertThat(createChain.getRequest()).as("월드 생성은 게임 쪽으로 넘어가지 않는다").isNull();

        MockHttpServletResponse deleted = new MockHttpServletResponse();
        MockFilterChain deleteChain = new MockFilterChain();
        filter(true).doFilter(new MockHttpServletRequest("DELETE", "/worlds/1"), deleted, deleteChain);
        assertThat(deleted.getStatus()).isEqualTo(403);
        assertThat(deleted.getContentAsString()).contains("WORLD_DELETION_DISABLED");
        assertThat(deleteChain.getRequest()).isNull();
    }

    @Test
    void leavesPlayAloneWhileLocked() throws Exception {
        for (MockHttpServletRequest allowed : new MockHttpServletRequest[] {
                new MockHttpServletRequest("GET", "/worlds"),
                new MockHttpServletRequest("GET", "/worlds/1/chats"),
                new MockHttpServletRequest("POST", "/players"),
                new MockHttpServletRequest("GET", "/ws/worlds/1")}) {
            MockFilterChain chain = new MockFilterChain();
            filter(true).doFilter(allowed, new MockHttpServletResponse(), chain);
            assertThat(chain.getRequest()).as(allowed.getMethod() + " " + allowed.getRequestURI()).isNotNull();
        }
    }

    @Test
    void staysOutOfTheWayByDefault() throws Exception {
        MockFilterChain chain = new MockFilterChain();
        MockHttpServletResponse response = new MockHttpServletResponse();
        new FixedWorldFilter(new MockEnvironment())
                .doFilter(new MockHttpServletRequest("POST", "/worlds"), response, chain);
        assertThat(chain.getRequest()).as("기본값은 꺼짐이라 학생 과제의 월드 생성은 그대로다").isNotNull();
        assertThat(response.getStatus()).isEqualTo(200);
    }
}
