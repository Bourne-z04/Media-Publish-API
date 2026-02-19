package com.hui.bilibili.controller;

import com.hui.bilibili.model.dto.*;
import com.hui.bilibili.service.QrCodeLoginService;
import com.hui.bilibili.service.VideoUploadService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * B站发布 API 控制器
 * <p>
 * 提供以下端点：
 * - GET  /api/bilibili/qrcode              获取登录二维码
 * - POST /api/bilibili/qrcode/poll         轮询登录状态
 * - GET  /api/bilibili/user/info           获取用户信息
 * - POST /api/bilibili/upload              上传视频文件
 * - POST /api/bilibili/publish             发布视频到 B站
 * - GET  /api/bilibili/upload/status/{id}  查询发布状态
 */
@Slf4j
@RestController
@RequestMapping("/api/bilibili")
@RequiredArgsConstructor
public class BilibiliController {

    private final QrCodeLoginService qrCodeLoginService;
    private final VideoUploadService videoUploadService;

    // ==================== 登录相关 ====================

    /**
     * 检查用户登录状态
     * <p>
     * 前端在用户进入发布页面时调用此接口：
     * - status=CONFIRMED：Cookie 有效，用户可直接发布，无需重新扫码
     * - status=EXPIRED：Cookie 已失效，需要引导用户重新扫码登录
     */
    @GetMapping("/login/status")
    public ApiResult<LoginStatusResponse> checkLoginStatus(@RequestParam String userId) {
        log.info("API: GET /api/bilibili/login/status, userId={}", userId);
        try {
            LoginStatusResponse response = qrCodeLoginService.checkLoginStatus(userId);
            return ApiResult.success(response);
        } catch (Exception e) {
            log.error("检查登录状态失败", e);
            return ApiResult.error(500, "检查登录状态失败: " + e.getMessage());
        }
    }

    /**
     * 获取登录二维码
     * <p>
     * 调用 biliup 获取 B站登录二维码 URL 和 qrcodeKey，
     * 前端使用 qrcodeUrl 生成二维码图片供用户扫码。
     */
    @GetMapping("/qrcode")
    public ApiResult<QrCodeResponse> getQrCode() {
        log.info("API: GET /api/bilibili/qrcode");
        try {
            QrCodeResponse response = qrCodeLoginService.getQrCode();
            return ApiResult.success(response);
        } catch (Exception e) {
            log.error("获取二维码失败", e);
            return ApiResult.error(502, "获取二维码失败: " + e.getMessage());
        }
    }

    /**
     * 等待扫码登录完成（阻塞式）
     * <p>
     * 调用后会阻塞等待用户扫码确认，最长等待 300 秒。
     * 用户应先获取二维码并扫码，然后调用此接口等待结果。
     * 注意：此接口为长轮询，Postman/客户端超时需设置为 5 分钟以上。
     */
    @PostMapping("/qrcode/poll")
    public ApiResult<LoginStatusResponse> pollLoginStatus(@RequestBody Map<String, String> body) {
        String qrcodeKey = body.get("qrcodeKey");
        if (qrcodeKey == null || qrcodeKey.isBlank()) {
            return ApiResult.error(400, "qrcodeKey 不能为空");
        }

        log.info("API: POST /api/bilibili/qrcode/poll, qrcodeKey={}", qrcodeKey);
        try {
            LoginStatusResponse response = qrCodeLoginService.pollLoginStatus(qrcodeKey);
            return ApiResult.success(response);
        } catch (Exception e) {
            log.error("轮询登录状态失败", e);
            return ApiResult.error(500, "轮询登录状态失败: " + e.getMessage());
        }
    }

    // ==================== 用户信息 ====================

    /**
     * 获取用户信息
     * <p>
     * 获取当前登录的 B站用户基本信息（昵称、头像、等级等）。
     */
    @GetMapping("/user/info")
    public ApiResult<UserInfoResponse> getUserInfo(@RequestParam String userId) {
        log.info("API: GET /api/bilibili/user/info, userId={}", userId);
        try {
            UserInfoResponse response = videoUploadService.getUserInfo(userId);
            return ApiResult.success(response);
        } catch (Exception e) {
            log.error("获取用户信息失败", e);
            return ApiResult.error(500, "获取用户信息失败: " + e.getMessage());
        }
    }

    // ==================== 视频上传/发布 ====================

    /**
     * 上传视频文件
     * <p>
     * 将视频文件（和可选的封面图片）上传到服务器的共享存储。
     * 返回服务端文件路径，供后续发布接口使用。
     * <p>
     * Content-Type: multipart/form-data
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResult<UploadResponse> uploadVideo(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "cover", required = false) MultipartFile cover) {

        log.info("API: POST /api/bilibili/upload, fileName={}, fileSize={}",
                file.getOriginalFilename(), file.getSize());

        if (file.isEmpty()) {
            return ApiResult.error(400, "视频文件不能为空");
        }

        try {
            UploadResponse response = videoUploadService.uploadFile(file, cover);
            return ApiResult.success(response);
        } catch (Exception e) {
            log.error("上传视频文件失败", e);
            return ApiResult.error(500, "上传视频文件失败: " + e.getMessage());
        }
    }

    /**
     * 发布视频到 B站
     * <p>
     * 调用 biliup 将已上传的视频发布到 B站。
     * 需要提供 userId（用于读取 Cookie）、videoPath（服务端路径）、
     * 视频标题、简介、标签、分区等信息。
     */
    @PostMapping("/publish")
    public ApiResult<PublishResponse> publishVideo(@Valid @RequestBody PublishRequest request) {
        log.info("API: POST /api/bilibili/publish, userId={}, title={}",
                request.getUserId(), request.getTitle());
        try {
            PublishResponse response = videoUploadService.publishVideo(request);
            return ApiResult.success(response);
        } catch (Exception e) {
            log.error("发布视频失败", e);
            if (e.getMessage().contains("Cookie")) {
                return ApiResult.error(401, e.getMessage());
            }
            return ApiResult.error(500, "发布视频失败: " + e.getMessage());
        }
    }

    /**
     * 查询发布任务状态
     * <p>
     * 根据 taskId 查询视频发布任务的处理进度。
     */
    @GetMapping("/upload/status/{taskId}")
    public ApiResult<PublishResponse> getUploadStatus(@PathVariable String taskId) {
        log.info("API: GET /api/bilibili/upload/status/{}", taskId);
        try {
            PublishResponse response = videoUploadService.getUploadStatus(taskId);
            return ApiResult.success(response);
        } catch (Exception e) {
            log.error("查询上传状态失败", e);
            return ApiResult.error(500, "查询上传状态失败: " + e.getMessage());
        }
    }
}
