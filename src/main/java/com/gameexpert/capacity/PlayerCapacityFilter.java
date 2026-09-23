package com.gameexpert.capacity;

import java.io.IOException;

import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

/**
 * 자리가 없으면 게임 접속을 주소만 보고 돌려보낸다.
 *
 * 월드 조회·플레이어 조회·핸드셰이크 어느 것도 하기 전이라, 가득 찬 서버에 계속 두드려도
 * 안에서 노는 사람들에게 부하가 가지 않는다.
 */
@Component
@RequiredArgsConstructor
public class PlayerCapacityFilter extends OncePerRequestFilter implements Ordered {

    private static final String GAME_SOCKET_PREFIX = "/ws/worlds/";
    /**
     * 입장 전에 클라이언트가 물어보는 자리 확인 주소. 메모리의 카운터 두 개만 읽고 바로 답하므로
     * 가득 찬 서버에 여러 번 물어도 게임·데이터베이스에 닿지 않는다.
     */
    static final String CAPACITY_PATH = "/webcraft/capacity";

    private final PlayerCapacity capacity;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        if (CAPACITY_PATH.equals(path)) {
            String nickname = request.getParameter("nickname");
            boolean full = capacity.full() && !capacity.holds(nickname);
            response.setStatus(HttpServletResponse.SC_OK);
            response.setHeader("Cache-Control", "no-store");
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"full\":" + full + ",\"live\":" + capacity.live()
                    + ",\"max\":" + capacity.max() + "}");
            return;
        }
        // 이미 자리를 쥔 사람이 다시 들어오는 것은 막지 않는다(끊겼다 돌아오는 경우).
        if (path != null && path.startsWith(GAME_SOCKET_PREFIX) && capacity.full()
                && !capacity.holds(request.getParameter("nickname"))) {
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            response.setHeader("Retry-After", "10");
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"error\":\"SERVER_FULL\",\"max\":" + capacity.max() + "}");
            return;
        }
        chain.doFilter(request, response);
    }

    @Override
    public int getOrder() { return Ordered.HIGHEST_PRECEDENCE + 20; }
}
