package com.hui.bilibili.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * biliup 服务连接配置
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "biliup")
public class BiliupConfig {

    /**
     * biliup HTTP 服务的基础 URL
     * 默认值: http://biliup:19159
     */
    private String baseUrl = "http://biliup:19159";

    /**
     * 请求超时时间（毫秒）
     */
    private int connectTimeout = 5000;

    /**
     * 读取超时时间（毫秒）
     */
    private int readTimeout = 300000;

    /**
     * 视频文件存储路径（共享 volume）
     */
    private String videoStoragePath = "/data/videos";

    /**
     * biliup --auth 认证用户名（固定为 biliup）
     */
    private String authUsername = "biliup";

    /**
     * biliup --auth 认证密码
     */
    private String authPassword = "hui123456";
}
