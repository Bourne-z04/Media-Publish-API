package com.hui.bilibili.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hui.bilibili.config.BiliupConfig;
import com.hui.bilibili.model.dto.PublishRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * biliup HTTP 客户端
 * <p>
 * 封装对 biliup 容器 REST API 的所有调用。
 * biliup 官方服务监听端口 19159，提供 /v1/* 系列接口。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BiliupClient {

    private final RestTemplate restTemplate;
    private final BiliupConfig biliupConfig;
    private final ObjectMapper objectMapper;

    // ==================== 登录相关 ====================

    /**
     * 获取登录二维码
     * GET /v1/get_qrcode
     *
     * @return biliup 返回的 JSON（包含 qrcodeUrl 和 qrcodeKey）
     */
    public JsonNode getQrCode() {
        String url = biliupConfig.getBaseUrl() + "/v1/get_qrcode";
        log.info("Requesting QR code from biliup: {}", url);
        try {
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            return objectMapper.readTree(response.getBody());
        } catch (RestClientException e) {
            log.error("Failed to get QR code from biliup", e);
            throw new RuntimeException("biliup 服务不可用: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Failed to parse QR code response", e);
            throw new RuntimeException("解析二维码响应失败", e);
        }
    }

    /**
     * 通过二维码登录
     * POST /v1/login_by_qrcode
     *
     * @param qrcodeKey 二维码登录密钥
     * @return biliup 返回的 JSON（包含登录状态和 Cookie 信息）
     */
    public JsonNode loginByQrCode(String qrcodeKey) {
        String url = biliupConfig.getBaseUrl() + "/v1/login_by_qrcode";
        log.info("Polling login status for qrcodeKey: {}", qrcodeKey);
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            ObjectNode body = objectMapper.createObjectNode();
            body.put("qrcode_key", qrcodeKey);

            HttpEntity<String> entity = new HttpEntity<>(objectMapper.writeValueAsString(body), headers);
            ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);
            return objectMapper.readTree(response.getBody());
        } catch (RestClientException e) {
            log.error("Failed to poll login status from biliup", e);
            throw new RuntimeException("biliup 服务不可用: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Failed to parse login response", e);
            throw new RuntimeException("解析登录响应失败", e);
        }
    }

    // ==================== 用户信息 ====================

    /**
     * 获取当前登录用户信息
     * GET /bili/space/myinfo
     *
     * @return biliup 返回的用户信息 JSON
     */
    public JsonNode getUserInfo() {
        String url = biliupConfig.getBaseUrl() + "/bili/space/myinfo";
        log.info("Requesting user info from biliup: {}", url);
        try {
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            return objectMapper.readTree(response.getBody());
        } catch (RestClientException e) {
            log.error("Failed to get user info from biliup", e);
            throw new RuntimeException("biliup 服务不可用: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Failed to parse user info response", e);
            throw new RuntimeException("解析用户信息失败", e);
        }
    }

    // ==================== 视频上传/发布 ====================

    /**
     * 提交视频发布任务
     * POST /v1/uploads
     *
     * @param request 发布请求参数
     * @return biliup 返回的任务信息 JSON
     */
    public JsonNode submitUpload(PublishRequest request) {
        String url = biliupConfig.getBaseUrl() + "/v1/uploads";
        log.info("Submitting upload task to biliup: title={}, videoPath={}", request.getTitle(), request.getVideoPath());
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            // 构建 biliup 需要的请求体
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

            HttpEntity<String> entity = new HttpEntity<>(objectMapper.writeValueAsString(body), headers);
            ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);

            log.info("Upload task submitted, response: {}", response.getBody());
            return objectMapper.readTree(response.getBody());
        } catch (RestClientException e) {
            log.error("Failed to submit upload task to biliup", e);
            throw new RuntimeException("biliup 服务不可用: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Failed to construct/parse upload request", e);
            throw new RuntimeException("发布请求处理失败", e);
        }
    }

    /**
     * 查询上传任务状态
     * GET /v1/status
     *
     * @param taskId 任务 ID
     * @return biliup 返回的状态 JSON
     */
    public JsonNode getUploadStatus(String taskId) {
        String url = biliupConfig.getBaseUrl() + "/v1/status?task_id=" + taskId;
        log.info("Querying upload status from biliup: taskId={}", taskId);
        try {
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            return objectMapper.readTree(response.getBody());
        } catch (RestClientException e) {
            log.error("Failed to get upload status from biliup", e);
            throw new RuntimeException("biliup 服务不可用: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Failed to parse status response", e);
            throw new RuntimeException("解析状态响应失败", e);
        }
    }

    // ==================== 用户管理 ====================

    /**
     * 获取 biliup 中的用户列表
     * GET /v1/users
     *
     * @return 用户列表 JSON
     */
    public JsonNode getUsers() {
        String url = biliupConfig.getBaseUrl() + "/v1/users";
        log.info("Requesting users list from biliup");
        try {
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            return objectMapper.readTree(response.getBody());
        } catch (RestClientException e) {
            log.error("Failed to get users from biliup", e);
            throw new RuntimeException("biliup 服务不可用: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Failed to parse users response", e);
            throw new RuntimeException("解析用户列表失败", e);
        }
    }

    /**
     * 添加用户到 biliup
     * POST /v1/users
     *
     * @param userData 用户 Cookie JSON 数据
     * @return 操作结果 JSON
     */
    public JsonNode addUser(Map<String, Object> userData) {
        String url = biliupConfig.getBaseUrl() + "/v1/users";
        log.info("Adding user to biliup");
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<String> entity = new HttpEntity<>(objectMapper.writeValueAsString(userData), headers);
            ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);
            return objectMapper.readTree(response.getBody());
        } catch (RestClientException e) {
            log.error("Failed to add user to biliup", e);
            throw new RuntimeException("biliup 服务不可用: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Failed to process add user request", e);
            throw new RuntimeException("添加用户失败", e);
        }
    }

    /**
     * 健康检查 — 检查 biliup 服务是否可用
     *
     * @return true 如果服务可用
     */
    public boolean healthCheck() {
        try {
            String url = biliupConfig.getBaseUrl() + "/v1/status";
            restTemplate.getForEntity(url, String.class);
            return true;
        } catch (Exception e) {
            log.warn("biliup health check failed: {}", e.getMessage());
            return false;
        }
    }
}
