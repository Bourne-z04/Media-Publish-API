package com.hui.bilibili.model.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 登录状态轮询响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LoginStatusResponse {

    /**
     * 登录状态: WAITING / SCANNED / CONFIRMED / EXPIRED
     */
    private String status;

    /**
     * 状态描述信息
     */
    private String message;

    /**
     * B站用户 ID（登录成功时返回）
     */
    private String userId;

    /**
     * B站用户名（登录成功时返回）
     */
    private String username;
}
