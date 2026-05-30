package com.krish.aiprocessor.config;

import io.netty.channel.ChannelOption;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

@Configuration
public class WebClientConfig {

    private final OpenAiProperties openAiProperties;

    public WebClientConfig(OpenAiProperties openAiProperties) {
        this.openAiProperties = openAiProperties;
    }

    @Bean
    public WebClient openAiWebClient() {
        Duration timeout = Duration.ofSeconds(openAiProperties.getTimeoutSeconds());
        HttpClient httpClient = HttpClient.create()
            .responseTimeout(timeout)
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) timeout.toMillis());

        return WebClient.builder()
            .baseUrl(openAiProperties.getBaseUrl())
            .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + openAiProperties.getApiKey())
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .clientConnector(new ReactorClientHttpConnector(httpClient))
            .build();
    }
}
