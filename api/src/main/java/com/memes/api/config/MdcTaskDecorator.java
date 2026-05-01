package com.memes.api.config;

import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;

import java.util.Map;
import java.util.Optional;

/**
 * Captures the calling thread's MDC (trace.id, http.*, etc.) at task-submission time
 * and restores it on the worker thread so async log lines carry the originating trace ID.
 */
public class MdcTaskDecorator implements TaskDecorator {

    @Override
    public Runnable decorate(Runnable runnable) {
        Map<String, String> callerMdc = MDC.getCopyOfContextMap();
        return () -> {
            try {
                Optional.ofNullable(callerMdc).ifPresent(MDC::setContextMap);
                runnable.run();
            } finally {
                MDC.clear();
            }
        };
    }
}
