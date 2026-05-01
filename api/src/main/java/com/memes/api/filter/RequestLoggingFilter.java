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

    private static final String MDC_REQUEST_ID = "requestId";

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

        String requestId = UUID.randomUUID().toString().substring(0, 8);
        MDC.put(MDC_REQUEST_ID, requestId);

        ContentCachingRequestWrapper wrappedRequest =
                new ContentCachingRequestWrapper(request, loggingProperties.getMaxBodySize());
        ContentCachingResponseWrapper wrappedResponse = new ContentCachingResponseWrapper(response);

        long startTime = System.currentTimeMillis();
        try {
            logIncomingRequest(wrappedRequest, requestId);
            chain.doFilter(wrappedRequest, wrappedResponse);
            long duration = System.currentTimeMillis() - startTime;
            logRequestBodyIfEnabled(wrappedRequest, requestId);
            logOutgoingResponse(wrappedResponse, requestId, duration);
        } finally {
            wrappedResponse.copyBodyToResponse();
            MDC.remove(MDC_REQUEST_ID);
        }
    }

    private void logIncomingRequest(ContentCachingRequestWrapper request, String requestId) {
        String queryString = Optional.ofNullable(request.getQueryString())
                .map(q -> "?" + q)
                .orElse("");
        String clientIp = Optional.ofNullable(request.getHeader("X-Forwarded-For"))
                .orElseGet(request::getRemoteAddr);
        String userAgent = Optional.ofNullable(request.getHeader("User-Agent")).orElse("-");

        log.info("[{}] --> {} {}{} from={} ua={}",
                requestId,
                request.getMethod(),
                request.getRequestURI(),
                queryString,
                clientIp,
                userAgent);
    }

    private void logRequestBodyIfEnabled(ContentCachingRequestWrapper request, String requestId) {
        if (!loggingProperties.isLogRequestBody()) {
            return;
        }
        if (!isTextContentType(request.getContentType())) {
            return;
        }
        byte[] body = request.getContentAsByteArray();
        if (body.length == 0) {
            return;
        }
        String bodyText = maskSensitiveValues(
                truncate(new String(body, StandardCharsets.UTF_8), loggingProperties.getMaxBodySize()));
        log.info("[{}] request body: {}", requestId, bodyText);
    }

    private void logOutgoingResponse(ContentCachingResponseWrapper response, String requestId, long duration) {
        int status = response.getStatus();
        int bodySize = response.getContentAsByteArray().length;

        if (status >= 500) {
            log.error("[{}] <-- {} in {}ms (body={}B)", requestId, status, duration, bodySize);
        } else if (status >= 400) {
            log.warn("[{}] <-- {} in {}ms (body={}B)", requestId, status, duration, bodySize);
        } else {
            log.info("[{}] <-- {} in {}ms (body={}B)", requestId, status, duration, bodySize);
        }

        if (!loggingProperties.isLogResponseBody() || bodySize == 0) {
            return;
        }
        if (!isTextContentType(response.getContentType())) {
            return;
        }
        String bodyText = maskSensitiveValues(
                truncate(new String(response.getContentAsByteArray(), StandardCharsets.UTF_8),
                        loggingProperties.getMaxBodySize()));
        log.info("[{}] response body: {}", requestId, bodyText);
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
