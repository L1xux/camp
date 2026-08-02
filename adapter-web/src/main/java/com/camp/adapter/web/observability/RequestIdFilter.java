package com.camp.adapter.web.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** 요청마다 request_id 를 MDC 에 넣는다. 이후 모든 로그가 이 값을 달고 나간다. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Request-Id";

    // 헤더 값을 그대로 MDC 에 넣으면 개행을 넣어 로그를 위조할 수 있으므로 형식을 제한한다.
    private static final Pattern ALLOWED = Pattern.compile("[A-Za-z0-9_-]{1,64}");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String requestId = resolve(request.getHeader(HEADER));
        MDC.put(CampJsonLogFormatter.REQUEST_ID, requestId);
        response.setHeader(HEADER, requestId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(CampJsonLogFormatter.REQUEST_ID);
        }
    }

    private String resolve(String header) {
        boolean usable = header != null && ALLOWED.matcher(header).matches();
        return usable ? header : UUID.randomUUID().toString();
    }
}
