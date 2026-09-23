package com.gameexpert.capacity;

import java.io.IOException;

import org.springframework.core.Ordered;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 이미 있는 월드 하나로만 운영할 때, 월드를 새로 만들거나 지우지 못하게 막는다.
 *
 * 작은 서버에서는 월드를 새로 만드는 순간 지형 생성이 CPU를 다 쓰고, 실수로 지우면 되돌릴
 * 수 없다. 켜면 만들기·지우기 요청을 주소만 보고 돌려보내고, 플레이에 필요한 조회와 접속은
 * 그대로 둔다. {@code webcraft.worlds.fixed=true} 일 때만 동작하며 기본은 꺼짐이다.
 */
@Component
public class FixedWorldFilter extends OncePerRequestFilter implements Ordered {

    private final boolean fixed;

    public FixedWorldFilter(Environment environment) {
        this.fixed = environment.getProperty("webcraft.worlds.fixed", Boolean.class, false);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        if (fixed && path != null && path.startsWith("/worlds")) {
            String method = request.getMethod();
            if ("POST".equals(method) && "/worlds".equals(stripSlash(path))) {
                refuse(response, "WORLD_CREATION_DISABLED");
                return;
            }
            if ("DELETE".equals(method)) {
                refuse(response, "WORLD_DELETION_DISABLED");
                return;
            }
        }
        chain.doFilter(request, response);
    }

    private static String stripSlash(String path) {
        return path.length() > 1 && path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
    }

    private static void refuse(HttpServletResponse response, String code) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"error\":\"" + code + "\"}");
    }

    @Override
    public int getOrder() { return Ordered.HIGHEST_PRECEDENCE + 21; }
}
