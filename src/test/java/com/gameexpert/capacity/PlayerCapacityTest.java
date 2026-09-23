package com.gameexpert.capacity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class PlayerCapacityTest {

    private PlayerCapacity capacity(int max) {
        return new PlayerCapacity(new MockEnvironment().withProperty("webcraft.players.max", String.valueOf(max)));
    }

    @Test
    void holdsThreePlayersByDefaultAndRefusesTheFourth() {
        PlayerCapacity capacity = new PlayerCapacity(new MockEnvironment());
        assertThat(capacity.max()).isEqualTo(3);
        assertThat(capacity.acquire("a")).isTrue();
        assertThat(capacity.acquire("b")).isTrue();
        assertThat(capacity.acquire("c")).isTrue();
        assertThat(capacity.full()).isTrue();
        assertThat(capacity.acquire("d")).isFalse();
        assertThat(capacity.live()).isEqualTo(3);
    }

    @Test
    void freesTheSeatWhenTheSessionEndsAndCountsEachSessionOnce() {
        PlayerCapacity capacity = capacity(2);
        assertThat(capacity.acquire("a")).isTrue();
        assertThat(capacity.acquire("a")).as("같은 사람이 다시 들어와도 자리를 더 쓰지 않는다").isTrue();
        assertThat(capacity.live()).isEqualTo(1);

        assertThat(capacity.acquire("b")).isTrue();
        assertThat(capacity.acquire("c")).isFalse();

        capacity.release("a");
        assertThat(capacity.live()).isEqualTo(1);
        capacity.release("a");
        assertThat(capacity.live()).as("이미 돌려준 자리를 두 번 빼지 않는다").isEqualTo(1);
        assertThat(capacity.acquire("c")).isTrue();
    }

    @Test
    void refusesAFullServerAtTheAddressWithoutTouchingTheGame() throws Exception {
        PlayerCapacity capacity = capacity(1);
        capacity.acquire("a");
        PlayerCapacityFilter filter = new PlayerCapacityFilter(capacity);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/ws/worlds/1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(response.getHeader("Retry-After")).isEqualTo("10");
        assertThat(response.getContentAsString()).contains("SERVER_FULL");
        assertThat(chain.getRequest()).as("게임 쪽으로는 넘어가지 않는다").isNull();
    }

    @Test
    void letsAReturningPlayerBackInEvenWhenFull() throws Exception {
        PlayerCapacity capacity = capacity(1);
        capacity.acquire("steve");
        PlayerCapacityFilter filter = new PlayerCapacityFilter(capacity);

        MockHttpServletRequest returning = new MockHttpServletRequest("GET", "/ws/worlds/1");
        returning.setParameter("nickname", "steve");
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(returning, new MockHttpServletResponse(), chain);
        assertThat(chain.getRequest()).as("끊겼다 돌아오는 사람은 자기 자리로 들어온다").isNotNull();

        MockHttpServletRequest stranger = new MockHttpServletRequest("GET", "/ws/worlds/1");
        stranger.setParameter("nickname", "alex");
        MockHttpServletResponse refused = new MockHttpServletResponse();
        filter.doFilter(stranger, refused, new MockFilterChain());
        assertThat(refused.getStatus()).isEqualTo(503);
    }

    @Test
    void answersTheSeatQuestionFromMemoryWithoutTouchingTheGame() throws Exception {
        PlayerCapacity capacity = capacity(1);
        capacity.acquire("steve");
        PlayerCapacityFilter filter = new PlayerCapacityFilter(capacity);

        MockHttpServletRequest stranger = new MockHttpServletRequest("GET", PlayerCapacityFilter.CAPACITY_PATH);
        stranger.setParameter("nickname", "alex");
        MockHttpServletResponse full = new MockHttpServletResponse();
        MockFilterChain untouched = new MockFilterChain();
        filter.doFilter(stranger, full, untouched);
        assertThat(full.getStatus()).isEqualTo(200);
        assertThat(full.getContentAsString()).isEqualTo("{\"full\":true,\"live\":1,\"max\":1}");
        assertThat(untouched.getRequest()).as("게임 쪽으로는 넘어가지 않는다").isNull();

        MockHttpServletRequest returning = new MockHttpServletRequest("GET", PlayerCapacityFilter.CAPACITY_PATH);
        returning.setParameter("nickname", "steve");
        MockHttpServletResponse mine = new MockHttpServletResponse();
        filter.doFilter(returning, mine, new MockFilterChain());
        assertThat(mine.getContentAsString()).as("자리를 쥔 사람에게는 가득 차 있지 않다").contains("\"full\":false");
    }

    @Test
    void letsOtherRequestsAndFreeSeatsThrough() throws Exception {
        PlayerCapacity capacity = capacity(1);
        PlayerCapacityFilter filter = new PlayerCapacityFilter(capacity);

        MockFilterChain open = new MockFilterChain();
        filter.doFilter(new MockHttpServletRequest("GET", "/ws/worlds/1"), new MockHttpServletResponse(), open);
        assertThat(open.getRequest()).isNotNull();

        capacity.acquire("a");
        MockFilterChain rest = new MockFilterChain();
        filter.doFilter(new MockHttpServletRequest("GET", "/worlds"), new MockHttpServletResponse(), rest);
        assertThat(rest.getRequest()).as("게임 접속이 아닌 요청은 막지 않는다").isNotNull();
    }
}
