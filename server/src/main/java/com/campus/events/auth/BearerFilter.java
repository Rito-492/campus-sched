package com.campus.events.auth;

import com.campus.events.foundation.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class BearerFilter extends OncePerRequestFilter {
    private final SessionService sessions;
    private final ObjectMapper mapper;
    public BearerFilter(SessionService sessions, ObjectMapper mapper) { this.sessions = sessions; this.mapper = mapper; }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                              FilterChain chain) throws ServletException, IOException {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        boolean publicRoute = "GET".equals(request.getMethod()) && path.equals("/api/v1/health")
                || "POST".equals(request.getMethod()) && path.equals("/api/v1/auth/login");
        if (publicRoute) { chain.doFilter(request, response); return; }
        try {
            String header = request.getHeader("Authorization");
            if (header == null || !header.startsWith("Bearer ") || header.substring(7).isBlank()) throw ApiException.unauthenticated();
            var actor = sessions.authenticate(header.substring(7));
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(actor, null, List.of()));
            if (List.of("POST", "PATCH", "PUT").contains(request.getMethod())
                    && !path.equals("/api/v1/auth/logout")
                    && !path.matches("/api/v1/activities/[^/]+/(schedule-preview|alternatives|change-preview)")) {
                String key = request.getHeader("Idempotency-Key");
                if (key == null || !key.matches("(?i)[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")) {
                    throw ApiException.invalid("Idempotency-Key必须是UUID");
                }
            }
            chain.doFilter(request, response);
        } catch (ApiException ex) {
            var result = Responses.error(ex.status(), ex.code(), ex.getMessage(), ex.details());
            response.setStatus(result.httpStatus()); response.setContentType("application/json;charset=UTF-8");
            mapper.writeValue(response.getOutputStream(), result.body());
        } catch (RuntimeException ex) {
            org.slf4j.LoggerFactory.getLogger(BearerFilter.class).error("认证请求失败，requestId={}", RequestIds.current(), ex);
            if (response.isCommitted()) throw ex;
            response.resetBuffer(); response.setStatus(500); response.setContentType("application/json;charset=UTF-8");
            mapper.writeValue(response.getOutputStream(), Responses.error(500, "INTERNAL_ERROR", "服务暂时无法完成操作", java.util.Map.of()).body());
        } finally { SecurityContextHolder.clearContext(); }
    }
}
