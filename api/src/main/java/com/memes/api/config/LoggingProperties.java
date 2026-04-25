package com.memes.api.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConfigurationProperties(prefix = "app.logging")
@Data
public class LoggingProperties {
    private int maxBodySize = 1000;
    private boolean logRequestBody = false;
    private boolean logResponseBody = false;
    private List<String> maskHeaders = List.of("X-Api-Key", "Authorization");
}
