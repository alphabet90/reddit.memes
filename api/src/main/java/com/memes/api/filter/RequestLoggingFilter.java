package com.memes.api.filter;

import com.memes.api.config.LoggingProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
@RequiredArgsConstructor
public class RequestLoggingFilter extends OncePerRequestFilter {

    private final LoggingProperties loggingProperties;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return uri.startsWith("/actuator/");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {

        String traceId = extractTraceId(request);

        ContentCachingRequestWrapper wrappedRequest =
                new ContentCachingRequestWrapper(request, loggingProperties.getMaxBodySize());
        ContentCachingResponseWrapper wrappedResponse = new ContentCachingResponseWrapper(response);

        // Populate ECS MDC fields — available on every log line emitted during this request
        MDC.put("trace.id", traceId);
        MDC.put("http.request.method", request.getMethod());
        MDC.put("url.path", request.getRequestURI());
        Optional.ofNullable(request.getQueryString()).ifPresent(qs -> MDC.put("url.query", qs));
        MDC.put("client.ip", Optional.ofNullable(request.getHeader("X-Forwarded-For"))
                .orElseGet(request::getRemoteAddr));
        MDC.put("user_agent.original", Optional.ofNullable(request.getHeader("User-Agent")).orElse("-"));
        // Echo trace ID back so callers can correlate logs without access to Kibana
        wrappedResponse.setHeader("X-Trace-Id", formatUuid(traceId));

        long startTime = System.currentTimeMillis();
        try {
            logIncomingRequest(wrappedRequest, traceId);
            chain.doFilter(wrappedRequest, wrappedResponse);
            long duration = System.currentTimeMillis() - startTime;
            MDC.put("http.response.status_code", String.valueOf(wrappedResponse.getStatus()));
            MDC.put("http.response.body.bytes", String.valueOf(wrappedResponse.getContentAsByteArray().length));
            MDC.put("event.duration", String.valueOf(duration * 1_000_000L));
            logRequestBody(wrappedRequest, traceId);
            logOutgoingResponse(wrappedResponse, traceId, duration);
        } finally {
            wrappedResponse.copyBodyToResponse();
            MDC.clear();
        }
    }

    private void logIncomingRequest(ContentCachingRequestWrapper request, String traceId) {
        String queryString = Optional.ofNullable(request.getQueryString())
                .map(q -> "?" + q)
                .orElse("");
        String clientIp = Optional.ofNullable(request.getHeader("X-Forwarded-For"))
                .orElseGet(request::getRemoteAddr);
        String userAgent = Optional.ofNullable(request.getHeader("User-Agent")).orElse("-");

        log.info("[{}] --> {} {}{} from={} ua={}",
                traceId,
                request.getMethod(),
                request.getRequestURI(),
                queryString,
                clientIp,
                userAgent);
    }

    private void logRequestBody(ContentCachingRequestWrapper request, String traceId) {
        if (!isTextContentType(request.getContentType())) {
            return;
        }
        byte[] body = request.getContentAsByteArray();
        if (body.length == 0) {
            return;
        }
        String bodyText = maskSensitiveValues(
                truncate(new String(body, StandardCharsets.UTF_8), loggingProperties.getMaxBodySize()));
        log.info("[{}] request body: {}", traceId, bodyText);
    }

    private void logOutgoingResponse(ContentCachingResponseWrapper response, String traceId, long duration) {
        int status = response.getStatus();
        int bodySize = response.getContentAsByteArray().length;

        if (status >= 500) {
            log.error("[{}] <-- {} in {}ms (body={}B)", traceId, status, duration, bodySize);
        } else if (status >= 400) {
            log.warn("[{}] <-- {} in {}ms (body={}B)", traceId, status, duration, bodySize);
        } else {
            log.info("[{}] <-- {} in {}ms (body={}B)", traceId, status, duration, bodySize);
        }

        if (bodySize == 0) {
            return;
        }
        if (!isTextContentType(response.getContentType())) {
            return;
        }
        String bodyText = maskSensitiveValues(
                truncate(new String(response.getContentAsByteArray(), StandardCharsets.UTF_8),
                        loggingProperties.getMaxBodySize()));
        log.info("[{}] response body: {}", traceId, bodyText);
    }

    // Extracts trace ID from W3C traceparent header, or generates a fresh UUID v4 (32-char hex).
    private String extractTraceId(HttpServletRequest request) {
        return Optional.ofNullable(request.getHeader("traceparent"))
                .flatMap(this::parseTraceparent)
                .orElseGet(() -> UUID.randomUUID().toString().replace("-", ""));
    }

    // Lenient parse: takes segment [1] from "version-traceId-parentId-flags".
    private Optional<String> parseTraceparent(String header) {
        String[] parts = header.split("-", -1);
        if (parts.length < 4) {
            return Optional.empty();
        }
        String traceId = parts[1];
        if (traceId.length() != 32 || !traceId.matches("[0-9a-f]+")) {
            return Optional.empty();
        }
        return Optional.of(traceId);
    }

    private String formatUuid(String hex32) {
        return hex32.substring(0, 8) + "-"
                + hex32.substring(8, 12) + "-"
                + hex32.substring(12, 16) + "-"
                + hex32.substring(16, 20) + "-"
                + hex32.substring(20);
    }

    private boolean isTextContentType(String contentType) {
        return Optional.ofNullable(contentType)
                .map(ct -> ct.startsWith("text/")
                        || ct.contains("application/json")
                        || ct.contains("application/xml")
                        || ct.contains("application/x-www-form-urlencoded"))
                .orElse(false);
    }

    private String maskSensitiveValues(String body) {
        String result = body;
        for (String header : loggingProperties.getMaskHeaders()) {
            result = result.replaceAll(
                    "(?i)(\"?" + Pattern.quote(header) + "\"?\\s*[:=]\\s*\")[^\"]+\"",
                    "$1[MASKED]\"");
        }
        return result;
    }

    private String truncate(String value, int maxLength) {
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength) + "...[truncated]";
    }
}
