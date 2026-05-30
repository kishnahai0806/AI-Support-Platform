package com.krish.supportapi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import com.krish.supportapi.config.JwtProperties;

@SpringBootApplication
@EnableConfigurationProperties(JwtProperties.class)
public class SupportApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(SupportApiApplication.class, args);
    }
}