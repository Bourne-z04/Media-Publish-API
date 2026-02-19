package com.hui.bilibili.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.hui.bilibili.client.BiliupClient;
import com.hui.bilibili.config.BiliupConfig;
import com.hui.bilibili.model.dto.PublishRequest;
import com.hui.bilibili.model.dto.PublishResponse;
import com.hui.bilibili.model.dto.UploadResponse;
import com.hui.bilibili.model.dto.UserInfoResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

/**
 * 视频上传发布服务
 * <p>
 * 负责：
 * 1. 将上传的视频文件存储到共享 volume（供 biliup 容器访问）
 * 2. 调用 biliup API 提交发布任务
 * 3. 查询上传状态
 * 4. 获取用户信息
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VideoUploadService {

    private final BiliupClient biliupClient;
    private final BiliupConfig biliupConfig;
    private final CookieStorageService cookieStorageService;

    /**
     * 上传视频文件到服务器共享存储
     *
     * @param videoFile 视频文件
     * @param coverFile 封面文件（可选）
     * @return 文件存储信息
     */
    public UploadResponse uploadFile(MultipartFile videoFile, MultipartFile coverFile) {
        String storagePath = biliupConfig.getVideoStoragePath();
        String fileId = UUID.randomUUID().toString().substring(0, 8);

        try {
            // 确保存储目录存在
            Path storageDir = Paths.get(storagePath);
            if (!Files.exists(storageDir)) {
                Files.createDirectories(storageDir);
            }

            // 保存视频文件
            String videoOriginalName = videoFile.getOriginalFilename();
            String videoExt = getFileExtension(videoOriginalName);
            String videoFileName = fileId + "_video" + videoExt;
            Path videoPath = storageDir.resolve(videoFileName);
            videoFile.transferTo(videoPath.toFile());
            log.info("Video file saved: {}", videoPath);

            // 保存封面文件（可选）
            String coverPathStr = null;
            if (coverFile != null && !coverFile.isEmpty()) {
                String coverOriginalName = coverFile.getOriginalFilename();
                String coverExt = getFileExtension(coverOriginalName);
                String coverFileName = fileId + "_cover" + coverExt;
                Path coverPath = storageDir.resolve(coverFileName);
                coverFile.transferTo(coverPath.toFile());
                coverPathStr = coverPath.toString();
                log.info("Cover file saved: {}", coverPath);
            }

            return UploadResponse.builder()
                    .videoPath(videoPath.toString())
                    .coverPath(coverPathStr)
                    .fileName(videoOriginalName)
                    .fileSize(videoFile.getSize())
                    .build();

        } catch (IOException e) {
            log.error("Failed to save uploaded file", e);
            throw new RuntimeException("文件保存失败: " + e.getMessage(), e);
        }
    }

    /**
     * 发布视频到 B站
     *
     * @param request 发布请求
     * @return 发布任务信息
     */
    public PublishResponse publishVideo(PublishRequest request) {
        // 验证用户 Cookie 是否有效
        if (!cookieStorageService.isCookieValid(request.getUserId())) {
            throw new RuntimeException("用户 Cookie 不存在或已过期，请重新登录");
        }

        // 调用 biliup 提交发布任务
        JsonNode result = biliupClient.submitUpload(request);
        log.debug("Publish response from biliup: {}", result);

        // 解析结果
        String taskId = null;
        String status = "PROCESSING";

        if (result.has("task_id")) {
            taskId = result.get("task_id").asText();
        }
        if (result.has("state")) {
            String state = result.get("state").asText();
            status = mapBiliupState(state);
        }

        return PublishResponse.builder()
                .taskId(taskId)
                .status(status)
                .message("发布任务已提交")
                .build();
    }

    /**
     * 查询上传任务状态
     *
     * @param taskId 任务 ID
     * @return 任务状态
     */
    public PublishResponse getUploadStatus(String taskId) {
        JsonNode result = biliupClient.getUploadStatus(taskId);
        log.debug("Upload status from biliup: {}", result);

        String status = "PROCESSING";
        String message = "处理中";

        if (result.has("state")) {
            String state = result.get("state").asText();
            status = mapBiliupState(state);
            message = mapBiliupMessage(state);
        }

        return PublishResponse.builder()
                .taskId(taskId)
                .status(status)
                .message(message)
                .build();
    }

    /**
     * 获取 B站用户信息
     *
     * @return 用户信息
     */
    public UserInfoResponse getUserInfo() {
        JsonNode result = biliupClient.getUserInfo();
        log.debug("User info from biliup: {}", result);

        // 解析 biliup 返回的用户信息
        JsonNode data = result.has("data") ? result.get("data") : result;

        return UserInfoResponse.builder()
                .mid(data.has("mid") ? data.get("mid").asLong() : null)
                .name(data.has("name") ? data.get("name").asText() : null)
                .face(data.has("face") ? data.get("face").asText() : null)
                .level(data.has("level") ? data.get("level").asInt() : null)
                .vipStatus(data.has("vip") && data.get("vip").has("status")
                        ? data.get("vip").get("status").asInt() : null)
                .build();
    }

    // ==================== 私有方法 ====================

    /**
     * 获取文件扩展名
     */
    private String getFileExtension(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf("."));
    }

    /**
     * 将 biliup 的状态映射为统一状态
     */
    private String mapBiliupState(String biliupState) {
        if (biliupState == null) return "PROCESSING";
        return switch (biliupState) {
            case "已完成", "success" -> "COMPLETED";
            case "进行中", "processing" -> "PROCESSING";
            case "cookies.json不存在" -> "COOKIE_MISSING";
            case "登录失败,请检查cookie" -> "COOKIE_EXPIRED";
            case "视频文件不存在" -> "VIDEO_NOT_FOUND";
            case "任务不存在!" -> "TASK_NOT_FOUND";
            case "上传失败", "failed" -> "FAILED";
            default -> "PROCESSING";
        };
    }

    /**
     * 将 biliup 的状态映射为中文描述
     */
    private String mapBiliupMessage(String biliupState) {
        if (biliupState == null) return "处理中";
        return switch (biliupState) {
            case "已完成", "success" -> "发布成功";
            case "进行中", "processing" -> "视频上传中";
            case "cookies.json不存在" -> "Cookie 文件不存在";
            case "登录失败,请检查cookie" -> "Cookie 已过期，请重新登录";
            case "视频文件不存在" -> "视频文件不存在";
            case "任务不存在!" -> "任务不存在";
            case "上传失败", "failed" -> "上传失败";
            case "读取封面错误" -> "封面路径错误";
            default -> biliupState;
        };
    }
}
