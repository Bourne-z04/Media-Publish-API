package com.hui.bilibili.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/**
 * RestTemplate 配置
 */
@Configuration
public class RestTemplateConfig {

    @Bean
    public RestTemplate restTemplate(BiliupConfig biliupConfig) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(java.time.Duration.ofMillis(biliupConfig.getConnectTimeout()));
        factory.setReadTimeout(java.time.Duration.ofMillis(biliupConfig.getReadTimeout()));
        RestTemplate restTemplate = new RestTemplate(factory);
        return restTemplate;
    }
}
