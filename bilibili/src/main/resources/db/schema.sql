-- ============================================
-- Bilibili Cookie 存储表
-- Database: hui_bilibili
-- ============================================

CREATE DATABASE IF NOT EXISTS hui_bilibili
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE hui_bilibili;

CREATE TABLE IF NOT EXISTS bili_cookie (
    id              BIGINT          NOT NULL AUTO_INCREMENT  COMMENT '主键 ID',
    user_id         VARCHAR(64)     NOT NULL                 COMMENT 'B站用户 ID',
    username        VARCHAR(128)    DEFAULT NULL             COMMENT 'B站用户名',
    encrypted_cookie TEXT           NOT NULL                 COMMENT 'AES-256-GCM 加密后的 Cookie',
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    expires_at      DATETIME        DEFAULT NULL             COMMENT '过期时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_id (user_id),
    INDEX idx_expires_at (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='B站 Cookie 加密存储表';
