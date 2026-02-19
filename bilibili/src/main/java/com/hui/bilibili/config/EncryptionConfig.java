package com.hui.bilibili.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * AES-256-GCM 加密配置
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "encryption")
public class EncryptionConfig {

    /**
     * AES 密钥（Base64 编码，32 字节 = 256 位）
     * 生成方式: openssl rand -base64 32
     */
    private String aesSecretKey;
}
