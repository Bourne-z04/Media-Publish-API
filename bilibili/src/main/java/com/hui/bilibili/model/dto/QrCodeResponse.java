package com.hui.bilibili.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 二维码响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QrCodeResponse {

    /**
     * 二维码 URL，前端用于生成二维码图片
     */
    private String qrcodeUrl;

    /**
     * 登录密钥，用于轮询登录状态
     */
    private String qrcodeKey;
}
