package com.krish.aiprocessor;

import com.krish.aiprocessor.config.OpenAiProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(OpenAiProperties.class)
public class AiProcessorApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiProcessorApplication.class, args);
    }
}
