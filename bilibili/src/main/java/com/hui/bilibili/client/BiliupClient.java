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
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

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
        return authGet(URI.create(url));
    }

    /**
     * 执行带认证的 GET 请求（URI 版本，避免二次编码）
     */
    private JsonNode authGet(URI uri) {
        try {
            HttpEntity<Void> entity = new HttpEntity<>(createAuthHeaders());
            ResponseEntity<String> resp = restTemplate.exchange(uri, HttpMethod.GET, entity, String.class);
            return objectMapper.readTree(resp.getBody());
        } catch (RestClientException e) {
            if (e.getMessage() != null && e.getMessage().contains("401")) {
                log.info("biliup session expired, re-authenticating...");
                authenticate();
                HttpEntity<Void> entity = new HttpEntity<>(createAuthHeaders());
                ResponseEntity<String> resp = restTemplate.exchange(uri, HttpMethod.GET, entity, String.class);
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
     * 获取 B站用户信息
     * GET /bili/space/myinfo?user={cookieFilePath}
     *
     * @param cookieFilePath cookie 文件路径，格式如 "data/3494365068528056.json"
     */
    public JsonNode getUserInfo(String cookieFilePath) {
        URI uri = UriComponentsBuilder.fromUriString(biliupConfig.getBaseUrl() + "/bili/space/myinfo")
                .queryParam("user", cookieFilePath)
                .build()
                .encode()
                .toUri();
        log.info("Requesting user info from biliup: {}", uri);
        try {
            return authGet(uri);
        } catch (Exception e) {
            log.error("Failed to get user info from biliup", e);
            throw new RuntimeException("获取用户信息失败: " + e.getMessage(), e);
        }
    }

    /**
     * 注册 B站 Cookie 文件到 biliup 用户列表
     * POST /v1/users  body: {"key":"bilibili-cookies","value":"data/{mid}.json"}
     *
     * @param cookieFilePath QR 登录返回的 filename，如 "data/3494365068528056.json"
     */
    public void registerBiliUser(String cookieFilePath) {
        String url = biliupConfig.getBaseUrl() + "/v1/users";
        log.info("Registering B站 cookie file in biliup: {}", cookieFilePath);
        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("key", "bilibili-cookies");
            body.put("value", cookieFilePath);
            authPost(url, objectMapper.writeValueAsString(body));
            log.info("B站 cookie file registered successfully");
        } catch (Exception e) {
            log.warn("Failed to register B站 cookie file: {}", e.getMessage());
        }
    }

    // ==================== 视频上传/发布 ====================

    /**
     * 提交视频发布任务
     * POST /v1/uploads
     * <p>
     * biliup 期望的请求体格式（PostUploads struct）：
     * {
     *   "files": ["/data/videos/xxx.mp4"],
     *   "params": {
     *     "template_name": "default",
     *     "title": "视频标题",
     *     "tid": 17,
     *     "copyright": 1,
     *     "tags": ["tag1", "tag2"],
     *     "description": "简介",
     *     "user_cookie": "data/{mid}.json",
     *     ...
     *   }
     * }
     */
    public JsonNode submitUpload(PublishRequest request) {
        String url = biliupConfig.getBaseUrl() + "/v1/uploads";
        log.info("Submitting upload task to biliup: title={}, videoPath={}", request.getTitle(), request.getVideoPath());
        try {
            // 构建 params（对应 biliup 的 UploadStreamer）
            ObjectNode params = objectMapper.createObjectNode();
            params.put("id", 0); // UploadStreamer 主键，ad-hoc 上传用 0
            params.put("template_name", request.getTitle());
            params.put("title", request.getTitle());
            params.put("tid", request.getTid());
            params.put("copyright", request.getCopyright() != null ? request.getCopyright() : 1);
            params.put("description", request.getDesc() != null ? request.getDesc() : "");
            params.put("dynamic", request.getDynamic() != null ? request.getDynamic() : "");
            params.put("copyright_source", request.getSource() != null ? request.getSource() : "");

            // tags: biliup 期望 Vec<String>，从逗号分隔的字符串转换
            var tagsArray = params.putArray("tags");
            if (request.getTag() != null && !request.getTag().isBlank()) {
                for (String tag : request.getTag().split(",")) {
                    tagsArray.add(tag.trim());
                }
            }

            // cookie 文件路径（biliup 容器中的相对路径）
            params.put("user_cookie", "data/" + request.getUserId() + ".json");

            if (request.getCoverPath() != null && !request.getCoverPath().isBlank()) {
                params.put("cover_path", request.getCoverPath());
            }
            if (request.getDtime() != null && request.getDtime() > 0) {
                params.put("dtime", request.getDtime());
            }
            if (request.getDolby() != null) {
                params.put("dolby", request.getDolby());
            }
            if (request.getUpSelectionReply() != null) {
                params.put("up_selection_reply", request.getUpSelectionReply());
            }
            if (request.getUpCloseReply() != null) {
                params.put("up_close_reply", request.getUpCloseReply());
            }
            if (request.getUpCloseDanmu() != null) {
                params.put("up_close_danmu", request.getUpCloseDanmu());
            }

            // 构建顶层请求体：{ "files": [...], "params": {...} }
            ObjectNode body = objectMapper.createObjectNode();
            var filesArray = body.putArray("files");
            filesArray.add(request.getVideoPath());
            body.set("params", params);

            String jsonBody = objectMapper.writeValueAsString(body);
            log.debug("Upload request body: {}", jsonBody);

            JsonNode result = authPost(url, jsonBody);
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
