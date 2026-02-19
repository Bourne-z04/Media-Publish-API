package com.hui.bilibili.model.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 发布任务响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PublishResponse {

    /**
     * 任务 ID
     */
    private String taskId;

    /**
     * 任务状态: PROCESSING / COMPLETED / FAILED
     */
    private String status;

    /**
     * 状态描述信息
     */
    private String message;
}
