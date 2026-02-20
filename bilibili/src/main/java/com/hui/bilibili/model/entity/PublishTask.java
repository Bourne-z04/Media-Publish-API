package com.hui.bilibili.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 发布任务记录实体
 * <p>
 * 由于 biliup 不提供任务 ID 和状态跟踪 API（POST /v1/uploads 返回 {}），
 * 我们自行在 MySQL 中维护发布任务的状态。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "publish_task")
public class PublishTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 任务 ID（UUID，返回给调用方用于查询）
     */
    @Column(name = "task_id", nullable = false, unique = true, length = 36)
    private String taskId;

    /**
     * B站用户 ID
     */
    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;

    /**
     * 视频文件路径（容器内路径）
     */
    @Column(name = "video_path", nullable = false, length = 512)
    private String videoPath;

    /**
     * 视频标题
     */
    @Column(name = "title", nullable = false, length = 256)
    private String title;

    /**
     * 任务状态: SUBMITTED / PROCESSING / COMPLETED / FAILED
     */
    @Column(name = "status", nullable = false, length = 32)
    private String status;

    /**
     * 状态描述信息
     */
    @Column(name = "message", length = 512)
    private String message;

    /**
     * 提交时间
     */
    @Column(name = "submitted_at", nullable = false, updatable = false)
    private LocalDateTime submittedAt;

    /**
     * 完成时间
     */
    @Column(name = "completed_at")
    private LocalDateTime completedAt;
}
