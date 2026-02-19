package com.hui.bilibili.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * B站 Cookie 加密存储实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "bili_cookie")
public class BiliCookie {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * B站用户 ID
     */
    @Column(name = "user_id", nullable = false, unique = true, length = 64)
    private String userId;

    /**
     * B站用户名
     */
    @Column(name = "username", length = 128)
    private String username;

    /**
     * AES-256-GCM 加密后的 Cookie 字符串
     */
    @Column(name = "encrypted_cookie", nullable = false, columnDefinition = "TEXT")
    private String encryptedCookie;

    /**
     * 创建时间
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * 过期时间
     */
    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
