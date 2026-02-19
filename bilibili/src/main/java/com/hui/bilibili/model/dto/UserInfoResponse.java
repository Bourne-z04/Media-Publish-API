package com.hui.bilibili.model.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户信息响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserInfoResponse {

    /**
     * B站用户 ID
     */
    private Long mid;

    /**
     * 用户名
     */
    private String name;

    /**
     * 头像 URL
     */
    private String face;

    /**
     * 等级
     */
    private Integer level;

    /**
     * VIP 状态
     */
    private Integer vipStatus;
}
