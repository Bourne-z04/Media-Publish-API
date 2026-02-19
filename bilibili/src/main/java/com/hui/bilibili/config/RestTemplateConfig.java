package com.hui.bilibili.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * RestTemplate 配置
 */
@Configuration
public class RestTemplateConfig {

    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder, BiliupConfig biliupConfig) {
        return builder
                .connectTimeout(Duration.ofMillis(biliupConfig.getConnectTimeout()))
                .readTimeout(Duration.ofMillis(biliupConfig.getReadTimeout()))
                .build();
    }
}
