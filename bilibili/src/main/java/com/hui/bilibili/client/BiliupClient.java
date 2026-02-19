package com.hui.bilibili.client;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import com.hui.bilibili.config.BiliupConfig;
import com.hui.bilibili.model.dto.PublishRequest;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * biliup HTTP 客户端
 * <p>
 * 封装对 biliup 容器 REST API 的所有调用。
 * biliup 官方服务监听端口 19159，提供 /v1/* 系列接口。
 * 当 biliup 使用 --auth 模式启动时，自动注册/登录并维护 session cookie。
 */
@Slf4j
@Component
public class BiliupClient {

    private final RestTemplate restTemplate;
    private final BiliupConfig biliupConfig;
    private final ObjectMapper objectMapper;

    /** biliup session cookie (biliup.sid) */
    private final AtomicReference<String> sessionCookie = new AtomicReference<>();

    public BiliupClient(RestTemplate restTemplate, BiliupConfig biliupConfig, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.biliupConfig = biliupConfig;
        this.objectMapper = objectMapper;
    }

    /**
     * 启动时自动注册/登录 biliup
     */
    @PostConstruct
    public void initAuth() {
        try {
            authenticate();
        } catch (Exception e) {
            log.warn("biliup auth initialization failed (will retry on first request): {}", e.getMessage());
        }
    }

    /**
     * 注册或登录 biliup，获取 session cookie
     */
    private synchronized void authenticate() {
        String baseUrl = biliupConfig.getBaseUrl();
        String username = biliupConfig.getAuthUsername();
        String password = biliupConfig.getAuthPassword();
        String body = String.format("{\"username\":\"%s\",\"password\":\"%s\"}", username, password);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> entity = new HttpEntity<>(body, headers);

        // 检查用户是否已注册
        try {
            restTemplate.getForEntity(baseUrl + "/v1/users/" + username, String.class);
            log.info("biliup user '{}' exists, logging in...", username);
        } catch (RestClientException e) {
            // 404 = 用户不存在，需要注册
            log.info("biliup user '{}' not found, registering...", username);
            try {
                ResponseEntity<String> regResp = restTemplate.postForEntity(
                        baseUrl + "/v1/users/register", entity, String.class);
                String cookie = extractSessionCookie(regResp);
                if (cookie != null) {
                    sessionCookie.set(cookie);
                    log.info("biliup user registered and session obtained");
                    return;
                }
            } catch (Exception ex) {
                log.error("biliup registration failed", ex);
            }
        }

        // 登录
        try {
            ResponseEntity<String> loginResp = restTemplate.postForEntity(
                    baseUrl + "/v1/users/login", entity, String.class);
            String cookie = extractSessionCookie(loginResp);
            if (cookie != null) {
                sessionCookie.set(cookie);
                log.info("biliup login successful, session obtained");
            } else {
                log.warn("biliup login succeeded but no session cookie returned");
            }
        } catch (Exception e) {
            log.error("biliup login failed", e);
            throw new RuntimeException("biliup 认证失败: " + e.getMessage(), e);
        }
    }

    /**
     * 从响应头中提取 biliup.sid cookie
     */
    private String extractSessionCookie(ResponseEntity<?> response) {
        List<String> cookies = response.getHeaders().get(HttpHeaders.SET_COOKIE);
        if (cookies != null) {
            for (String c : cookies) {
                if (c.startsWith("biliup.sid=")) {
                    String sid = c.split(";")[0]; // "biliup.sid=xxx"
                    log.debug("Extracted session cookie: {}", sid);
                    return sid;
                }
            }
        }
        return null;
    }

    /**
     * 创建带 session cookie 的请求头
     */
    private HttpHeaders createAuthHeaders() {
        HttpHeaders headers = new HttpHeaders();
        String cookie = sessionCookie.get();
        if (cookie != null) {
            headers.set(HttpHeaders.COOKIE, cookie);
        }
        return headers;
    }

    /**
     * 执行带认证的 GET 请求，401 时自动重试
     */
    private JsonNode authGet(String url) {
        try {
            HttpEntity<Void> entity = new HttpEntity<>(createAuthHeaders());
            ResponseEntity<String> resp = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            return objectMapper.readTree(resp.getBody());
        } catch (RestClientException e) {
            if (e.getMessage() != null && e.getMessage().contains("401")) {
                log.info("biliup session expired, re-authenticating...");
                authenticate();
                HttpEntity<Void> entity = new HttpEntity<>(createAuthHeaders());
                ResponseEntity<String> resp = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
                try { return objectMapper.readTree(resp.getBody()); } catch (Exception ex) { throw new RuntimeException(ex); }
            }
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 执行带认证的 POST 请求，401 时自动重试
     */
    private JsonNode authPost(String url, String jsonBody) {
        HttpHeaders headers = createAuthHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        try {
            HttpEntity<String> entity = new HttpEntity<>(jsonBody, headers);
            ResponseEntity<String> resp = restTemplate.postForEntity(url, entity, String.class);
            return objectMapper.readTree(resp.getBody());
        } catch (RestClientException e) {
            if (e.getMessage() != null && e.getMessage().contains("401")) {
                log.info("biliup session expired, re-authenticating...");
                authenticate();
                headers = createAuthHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                HttpEntity<String> entity = new HttpEntity<>(jsonBody, headers);
                ResponseEntity<String> resp = restTemplate.postForEntity(url, entity, String.class);
                try { return objectMapper.readTree(resp.getBody()); } catch (Exception ex) { throw new RuntimeException(ex); }
            }
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // ==================== 登录相关 ====================

    /**
     * 获取登录二维码
     * GET /v1/get_qrcode
     */
    public JsonNode getQrCode() {
        String url = biliupConfig.getBaseUrl() + "/v1/get_qrcode";
        log.info("Requesting QR code from biliup: {}", url);
        try {
            return authGet(url);
        } catch (Exception e) {
            log.error("Failed to get QR code from biliup", e);
            throw new RuntimeException("获取二维码失败: " + e.getMessage(), e);
        }
    }

    /**
     * 通过二维码登录（阻塞式长轮询，最长等待 300 秒）
     * POST /v1/login_by_qrcode
     * <p>
     * biliup 要求请求体为完整 QR 码响应格式：{"code":0,"data":{"auth_code":"xxx"},"message":"0","ttl":1}
     * 内部会提取 body["data"]["auth_code"]，然后循环轮询 B站直到用户扫码或超时。
     * 成功返回 {"filename":"data/{mid}.json"}
     */
    public JsonNode loginByQrCode(String authCode) {
        String url = biliupConfig.getBaseUrl() + "/v1/login_by_qrcode";
        log.info("Starting QR code login (blocking, up to 300s) for authCode: {}", authCode);
        try {
            // 构造 biliup 期望的完整 QR 码响应格式
            ObjectNode data = objectMapper.createObjectNode();
            data.put("auth_code", authCode);
            ObjectNode body = objectMapper.createObjectNode();
            body.put("code", 0);
            body.set("data", data);
            body.put("message", "0");
            body.put("ttl", 1);
            return authPost(url, objectMapper.writeValueAsString(body));
        } catch (ResourceAccessException e) {
            // 超时 = 用户未在有效期内扫码
            log.warn("QR code login timed out (user did not scan in time)");
            ObjectNode timeout = objectMapper.createObjectNode();
            timeout.put("status", "TIMEOUT");
            return timeout;
        } catch (Exception e) {
            log.error("QR code login failed", e);
            throw new RuntimeException("二维码登录失败: " + e.getMessage(), e);
        }
    }

    // ==================== 用户信息 ====================

    /**
     * 获取当前登录用户信息
     * GET /bili/space/myinfo
     */
    public JsonNode getUserInfo() {
        String url = biliupConfig.getBaseUrl() + "/bili/space/myinfo";
        log.info("Requesting user info from biliup: {}", url);
        try {
            return authGet(url);
        } catch (Exception e) {
            log.error("Failed to get user info from biliup", e);
            throw new RuntimeException("获取用户信息失败: " + e.getMessage(), e);
        }
    }

    // ==================== 视频上传/发布 ====================

    /**
     * 提交视频发布任务
     * POST /v1/uploads
     */
    public JsonNode submitUpload(PublishRequest request) {
        String url = biliupConfig.getBaseUrl() + "/v1/uploads";
        log.info("Submitting upload task to biliup: title={}, videoPath={}", request.getTitle(), request.getVideoPath());
        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("copyright", request.getCopyright() != null ? request.getCopyright() : 1);
            body.put("source", request.getSource() != null ? request.getSource() : "");
            body.put("tid", request.getTid());
            body.put("cover", "");
            body.put("title", request.getTitle());
            body.put("desc", request.getDesc());
            body.put("dynamic", request.getDynamic() != null ? request.getDynamic() : "");
            body.put("tag", request.getTag());
            body.put("video_path", request.getVideoPath());

            if (request.getCoverPath() != null && !request.getCoverPath().isBlank()) {
                body.put("cover_path", request.getCoverPath());
            }
            if (request.getDtime() != null && request.getDtime() > 0) {
                body.put("dtime", request.getDtime());
            }
            if (request.getDolby() != null) {
                body.put("dolby", request.getDolby());
            }
            if (request.getOpenSubtitle() != null) {
                body.put("open_subtitle", request.getOpenSubtitle());
            }
            body.put("up_selection_reply", request.getUpSelectionReply() != null && request.getUpSelectionReply());
            body.put("up_close_reply", request.getUpCloseReply() != null && request.getUpCloseReply());
            body.put("up_close_danmu", request.getUpCloseDanmu() != null && request.getUpCloseDanmu());

            JsonNode result = authPost(url, objectMapper.writeValueAsString(body));
            log.info("Upload task submitted successfully");
            return result;
        } catch (Exception e) {
            log.error("Failed to submit upload task to biliup", e);
            throw new RuntimeException("发布请求处理失败: " + e.getMessage(), e);
        }
    }

    /**
     * 查询上传任务状态
     * GET /v1/status
     */
    public JsonNode getUploadStatus(String taskId) {
        String url = biliupConfig.getBaseUrl() + "/v1/status?task_id=" + taskId;
        log.info("Querying upload status from biliup: taskId={}", taskId);
        try {
            return authGet(url);
        } catch (Exception e) {
            log.error("Failed to get upload status from biliup", e);
            throw new RuntimeException("查询上传状态失败: " + e.getMessage(), e);
        }
    }

    // ==================== 用户管理 ====================

    /**
     * 获取 biliup 中的用户列表
     * GET /v1/users
     */
    public JsonNode getUsers() {
        String url = biliupConfig.getBaseUrl() + "/v1/users";
        log.info("Requesting users list from biliup");
        try {
            return authGet(url);
        } catch (Exception e) {
            log.error("Failed to get users from biliup", e);
            throw new RuntimeException("获取用户列表失败: " + e.getMessage(), e);
        }
    }

    /**
     * 添加用户到 biliup
     * POST /v1/users
     */
    public JsonNode addUser(Map<String, Object> userData) {
        String url = biliupConfig.getBaseUrl() + "/v1/users";
        log.info("Adding user to biliup");
        try {
            return authPost(url, objectMapper.writeValueAsString(userData));
        } catch (Exception e) {
            log.error("Failed to add user to biliup", e);
            throw new RuntimeException("添加用户失败: " + e.getMessage(), e);
        }
    }

    /**
     * 健康检查 — 检查 biliup 服务是否可用
     */
    public boolean healthCheck() {
        try {
            authGet(biliupConfig.getBaseUrl() + "/v1/status");
            return true;
        } catch (Exception e) {
            log.warn("biliup health check failed: {}", e.getMessage());
            return false;
        }
    }
}
