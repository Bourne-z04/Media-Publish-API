package com.hui.bilibili.service;

import tools.jackson.databind.JsonNode;
import com.hui.bilibili.client.BiliupClient;
import com.hui.bilibili.config.BiliupConfig;
import com.hui.bilibili.model.dto.LoginStatusResponse;
import com.hui.bilibili.model.dto.QrCodeResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

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
    private final BiliupConfig biliupConfig;

    /**
     * 检查用户是否需要重新扫码登录
     * <p>
     * 检查逻辑：
     * 1. 数据库中是否有未过期的 Cookie 记录
     * 2. 通过 biliup API 验证 cookie 是否仍可用于 B站
     *
     * @param userId B站用户 mid
     * @return 登录状态（CONFIRMED=有效无需重登, EXPIRED=需要重新扫码）
     */
    public LoginStatusResponse checkLoginStatus(String userId) {
        // 1. 检查数据库
        if (!cookieStorageService.isCookieValid(userId)) {
            log.info("用户 Cookie 不存在或已过期, userId={}", userId);
            return LoginStatusResponse.builder()
                    .status("EXPIRED")
                    .message("登录已过期，请重新扫码")
                    .userId(userId)
                    .build();
        }

        // 2. 确保 cookie 文件存在（如有必要从数据库恢复）
        String filename = "data/" + userId + ".json";
        Path cookiePath = Path.of(biliupConfig.getCookieStoragePath(), filename);
        if (!Files.exists(cookiePath)) {
            try {
                String cookieContent = cookieStorageService.getCookie(userId);
                Path parentDir = cookiePath.getParent();
                if (!Files.exists(parentDir)) {
                    Files.createDirectories(parentDir);
                }
                Files.writeString(cookiePath, cookieContent);
                biliupClient.registerBiliUser(filename);
                log.info("Cookie 文件已从数据库恢复, userId={}", userId);
            } catch (Exception e) {
                log.error("恢复 cookie 文件失败, userId={}: {}", userId, e.getMessage());
                cookieStorageService.deleteCookie(userId);
                return LoginStatusResponse.builder()
                        .status("EXPIRED")
                        .message("Cookie 恢复失败，请重新扫码")
                        .userId(userId)
                        .build();
            }
        }

        // 3. 通过 biliup 调用 B站 API 验证 cookie 是否仍有效
        try {
            JsonNode userInfo = biliupClient.getUserInfo(filename);
            JsonNode data = userInfo.has("data") ? userInfo.get("data") : userInfo;
            String name = data.has("name") ? data.get("name").asText() : null;

            log.info("Cookie 验证通过, userId={}, name={}", userId, name);
            return LoginStatusResponse.builder()
                    .status("CONFIRMED")
                    .message("登录有效")
                    .userId(userId)
                    .username(name)
                    .build();
        } catch (Exception e) {
            log.warn("Cookie B站验证失败, userId={}: {}", userId, e.getMessage());
            cookieStorageService.deleteCookie(userId);
            return LoginStatusResponse.builder()
                    .status("EXPIRED")
                    .message("B站登录已失效，请重新扫码")
                    .userId(userId)
                    .build();
        }
    }

    /**
     * 获取登录二维码
     *
     * @return 二维码 URL 和 qrcodeKey
     */
    public QrCodeResponse getQrCode() {
        JsonNode result = biliupClient.getQrCode();
        log.debug("QR code response from biliup: {}", result);

        // 解析 biliup 返回的二维码信息
        // biliup 返回格式: {"code":0,"data":{"auth_code":"...","url":"..."}}
        String qrcodeUrl = extractField(result, "url", "qrcode_url", "qrcodeUrl");
        String qrcodeKey = extractField(result, "auth_code", "qrcode_key", "qrcodeKey", "key");

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
     * 等待二维码登录完成（阻塞式，最长 300 秒）
     * <p>
     * 调用 biliup 的阻塞式长轮询接口，内部每秒向 B站查询扫码状态。
     * 用户扫码确认后返回成功，超时则抛出异常。
     *
     * @param qrcodeKey 从 getQrCode() 获取的 auth_code
     * @return 登录结果
     */
    public LoginStatusResponse pollLoginStatus(String qrcodeKey) {
        JsonNode result = biliupClient.loginByQrCode(qrcodeKey);
        log.debug("Login response from biliup: {}", result);

        // biliup 成功返回格式: {"filename":"data/{mid}.json"}
        if (result != null && result.has("filename")) {
            String filename = result.get("filename").asText();
            // 从 "data/{mid}.json" 中提取 B站用户 mid
            String userId = filename;
            if (filename.contains("/")) {
                userId = filename.substring(filename.lastIndexOf('/') + 1);
            }
            if (userId.endsWith(".json")) {
                userId = userId.substring(0, userId.length() - 5);
            }

            // 注册 cookie 文件到 biliup 用户列表（key=bilibili-cookies）
            biliupClient.registerBiliUser(filename);

            // 从共享 volume 读取 cookie 文件，加密后持久化到 MySQL
            // 这样即使 biliup 容器重建，也能从数据库恢复 cookie
            persistCookieToDatabase(userId, filename);

            log.info("B站扫码登录成功, userId={}, cookieFile={}", userId, filename);

            return LoginStatusResponse.builder()
                    .status("CONFIRMED")
                    .message("登录成功")
                    .userId(userId)
                    .build();
        }

        // 超时（用户未扫码）
        if (result != null && result.has("status")
                && "TIMEOUT".equals(result.get("status").asText())) {
            return LoginStatusResponse.builder()
                    .status("EXPIRED")
                    .message("二维码已过期，请重新获取")
                    .build();
        }

        // 返回了非预期格式
        log.warn("Unexpected login response: {}", result);
        return LoginStatusResponse.builder()
                .status("EXPIRED")
                .message("二维码已过期或登录失败")
                .build();
    }

    // ==================== 私有方法 ====================

    /**
     * 将 biliup 生成的 cookie 文件内容读取并加密存储到 MySQL
     * <p>
     * biliup 将 B站 cookie 存储在 /opt/data/{mid}.json（容器内），
     * 通过共享 volume 在 bilibili-api 容器中可访问为 /biliup-data/data/{mid}.json。
     * 读取后加密存入数据库，下次用户发布时无需重新扫码。
     *
     * @param userId   B站用户 mid
     * @param filename biliup 返回的 cookie 文件名，如 "data/3494365068528056.json"
     */
    private void persistCookieToDatabase(String userId, String filename) {
        try {
            Path cookiePath = Path.of(biliupConfig.getCookieStoragePath(), filename);
            if (Files.exists(cookiePath)) {
                String cookieContent = Files.readString(cookiePath);
                cookieStorageService.saveCookie(userId, userId, cookieContent);
                log.info("Cookie 已持久化到数据库, userId={}, file={}", userId, cookiePath);
            } else {
                // cookie 文件可能需要一点时间生成，延迟重试
                log.warn("Cookie 文件暂未生成: {}，将延迟 2 秒后重试", cookiePath);
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
                if (Files.exists(cookiePath)) {
                    String cookieContent = Files.readString(cookiePath);
                    cookieStorageService.saveCookie(userId, userId, cookieContent);
                    log.info("Cookie 已持久化到数据库（延迟重试成功）, userId={}", userId);
                } else {
                    log.warn("Cookie 文件仍不存在: {}，跳过数据库持久化（biliup 内部仍有效）", cookiePath);
                }
            }
        } catch (IOException e) {
            log.error("读取 cookie 文件失败, userId={}: {}", userId, e.getMessage());
        }
    }

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
}
