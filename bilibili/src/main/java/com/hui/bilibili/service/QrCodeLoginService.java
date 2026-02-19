package com.hui.bilibili.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.hui.bilibili.client.BiliupClient;
import com.hui.bilibili.model.dto.LoginStatusResponse;
import com.hui.bilibili.model.dto.QrCodeResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 扫码登录服务
 * <p>
 * 调用 biliup 的 QR 码登录接口，实现 B站扫码登录流程：
 * 1. 获取二维码 URL + qrcodeKey
 * 2. 前端展示二维码
 * 3. 轮询登录状态
 * 4. 登录成功后提取 Cookie，加密存储到数据库
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QrCodeLoginService {

    private final BiliupClient biliupClient;
    private final CookieStorageService cookieStorageService;

    /**
     * 获取登录二维码
     *
     * @return 二维码 URL 和 qrcodeKey
     */
    public QrCodeResponse getQrCode() {
        JsonNode result = biliupClient.getQrCode();
        log.debug("QR code response from biliup: {}", result);

        // 解析 biliup 返回的二维码信息
        String qrcodeUrl = extractField(result, "url", "qrcode_url", "qrcodeUrl");
        String qrcodeKey = extractField(result, "qrcode_key", "qrcodeKey", "key");

        if (qrcodeUrl == null || qrcodeKey == null) {
            log.error("Invalid QR code response from biliup: {}", result);
            throw new RuntimeException("获取二维码失败：biliup 返回数据格式异常");
        }

        return QrCodeResponse.builder()
                .qrcodeUrl(qrcodeUrl)
                .qrcodeKey(qrcodeKey)
                .build();
    }

    /**
     * 轮询登录状态
     *
     * @param qrcodeKey 二维码登录密钥
     * @return 登录状态
     */
    public LoginStatusResponse pollLoginStatus(String qrcodeKey) {
        JsonNode result = biliupClient.loginByQrCode(qrcodeKey);
        log.debug("Login poll response from biliup: {}", result);

        // 解析 biliup 返回的登录状态
        // biliup 可能返回不同格式，需要兼容处理
        int code = result.has("code") ? result.get("code").asInt() : -1;

        // 检查是否有 data 节点
        JsonNode data = result.has("data") ? result.get("data") : result;

        // 根据返回的 code 判断状态
        if (code == 0 || isLoginSuccess(data)) {
            // 登录成功
            String userId = extractField(data, "mid", "user_id", "userId", "DedeUserID");
            String username = extractField(data, "name", "username", "uname");
            String cookie = extractField(data, "cookie", "cookies", "cookie_info");

            // 如果获取到了 Cookie，存储到数据库
            if (cookie != null && !cookie.isBlank()) {
                cookieStorageService.saveCookie(
                        userId != null ? userId : "unknown",
                        username != null ? username : "unknown",
                        cookie
                );
            }

            return LoginStatusResponse.builder()
                    .status("CONFIRMED")
                    .message("登录成功")
                    .userId(userId)
                    .username(username)
                    .build();
        } else if (isScanned(data)) {
            return LoginStatusResponse.builder()
                    .status("SCANNED")
                    .message("已扫码，等待确认")
                    .build();
        } else if (isExpired(data)) {
            return LoginStatusResponse.builder()
                    .status("EXPIRED")
                    .message("二维码已过期，请重新获取")
                    .build();
        } else {
            return LoginStatusResponse.builder()
                    .status("WAITING")
                    .message("等待扫码")
                    .build();
        }
    }

    // ==================== 私有方法 ====================

    /**
     * 从 JSON 中提取字段值（兼容多种字段名）
     */
    private String extractField(JsonNode node, String... fieldNames) {
        if (node == null) return null;
        for (String name : fieldNames) {
            if (node.has(name) && !node.get(name).isNull()) {
                return node.get(name).asText();
            }
        }
        // 递归搜索 data 节点
        if (node.has("data")) {
            return extractField(node.get("data"), fieldNames);
        }
        return null;
    }

    private boolean isLoginSuccess(JsonNode data) {
        if (data == null) return false;
        // 检查常见的成功标志
        if (data.has("status") && data.get("status").asInt() == 0) return true;
        if (data.has("cookie") || data.has("cookies")) return true;
        if (data.has("mid")) return true;
        return false;
    }

    private boolean isScanned(JsonNode data) {
        if (data == null) return false;
        if (data.has("status") && data.get("status").asInt() == 1) return true;
        if (data.has("message") && data.get("message").asText().contains("扫码")) return true;
        return false;
    }

    private boolean isExpired(JsonNode data) {
        if (data == null) return false;
        if (data.has("status") && data.get("status").asInt() == 2) return true;
        if (data.has("code") && data.get("code").asInt() == 86038) return true;
        if (data.has("message") && data.get("message").asText().contains("过期")) return true;
        return false;
    }
}
