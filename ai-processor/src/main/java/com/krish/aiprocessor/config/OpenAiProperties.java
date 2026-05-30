package com.krish.aiprocessor.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "openai")
@Getter
@Setter
public class OpenAiProperties {

    private String apiKey;

    private String model;

    private String baseUrl;

    private int maxTokens;

    private double temperature;

    private int timeoutSeconds;

    private int maxRetries;
}
