package com.hui.bilibili.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 视频发布请求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PublishRequest {

    /**
     * B站用户 ID（用于读取 Cookie）
     */
    @NotBlank(message = "userId 不能为空")
    private String userId;

    /**
     * 服务端视频文件路径
     */
    @NotBlank(message = "videoPath 不能为空")
    private String videoPath;

    /**
     * 服务端封面图片路径（可选）
     */
    private String coverPath;

    /**
     * 视频标题（≤80字符）
     */
    @NotBlank(message = "title 不能为空")
    private String title;

    /**
     * 视频简介
     */
    @NotBlank(message = "desc 不能为空")
    private String desc;

    /**
     * 标签，逗号分隔
     */
    @NotBlank(message = "tag 不能为空")
    private String tag;

    /**
     * 分区 ID
     */
    @NotNull(message = "tid 不能为空")
    private Integer tid;

    /**
     * 版权: 1=自制, 2=转载
     */
    @NotNull(message = "copyright 不能为空")
    private Integer copyright;

    /**
     * 转载来源 URL（转载时必填）
     */
    private String source;

    /**
     * 粉丝动态内容
     */
    private String dynamic;

    /**
     * 定时发布时间戳（10位，需 > 当前时间4h）
     */
    private Long dtime;

    /**
     * 杜比音效: 0=关，1=开
     */
    private Integer dolby;

    /**
     * 是否开启字幕
     */
    private Boolean openSubtitle;

    /**
     * 精选评论
     */
    private Boolean upSelectionReply;

    /**
     * 关闭评论
     */
    private Boolean upCloseReply;

    /**
     * 关闭弹幕
     */
    private Boolean upCloseDanmu;
}
