package com.hui.bilibili.service;

import com.hui.bilibili.model.entity.BiliCookie;
import com.hui.bilibili.repository.BiliCookieRepository;
import com.hui.bilibili.util.AesEncryptUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Cookie 加密存储服务
 * <p>
 * 负责将 B站登录获取的 Cookie 加密存储到 MySQL，
 * 并在需要时解密取出供 biliup 使用。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CookieStorageService {

    private final BiliCookieRepository cookieRepository;
    private final AesEncryptUtil aesEncryptUtil;

    /**
     * 保存或更新用户 Cookie（加密后存储）
     *
     * @param userId   B站用户 ID
     * @param username B站用户名
     * @param cookie   明文 Cookie 字符串
     */
    @Transactional
    public void saveCookie(String userId, String username, String cookie) {
        log.info("Saving cookie for user: {} ({})", userId, username);

        String encryptedCookie = aesEncryptUtil.encrypt(cookie);

        Optional<BiliCookie> existing = cookieRepository.findByUserId(userId);
        if (existing.isPresent()) {
            BiliCookie entity = existing.get();
            entity.setUsername(username);
            entity.setEncryptedCookie(encryptedCookie);
            entity.setExpiresAt(LocalDateTime.now().plusDays(30)); // Cookie 一般 30 天有效
            cookieRepository.save(entity);
            log.info("Updated cookie for user: {}", userId);
        } else {
            BiliCookie entity = BiliCookie.builder()
                    .userId(userId)
                    .username(username)
                    .encryptedCookie(encryptedCookie)
                    .expiresAt(LocalDateTime.now().plusDays(30))
                    .build();
            cookieRepository.save(entity);
            log.info("Created cookie for user: {}", userId);
        }
    }

    /**
     * 获取解密后的 Cookie
     *
     * @param userId B站用户 ID
     * @return 解密后的 Cookie 字符串
     */
    public String getCookie(String userId) {
        BiliCookie entity = cookieRepository.findByUserId(userId)
                .orElseThrow(() -> new RuntimeException("用户 Cookie 不存在: " + userId));

        // 检查是否过期
        if (entity.getExpiresAt() != null && entity.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("用户 Cookie 已过期，请重新登录: " + userId);
        }

        return aesEncryptUtil.decrypt(entity.getEncryptedCookie());
    }

    /**
     * 检查用户 Cookie 是否存在且有效
     *
     * @param userId B站用户 ID
     * @return true 如果 Cookie 存在且未过期
     */
    public boolean isCookieValid(String userId) {
        Optional<BiliCookie> entity = cookieRepository.findByUserId(userId);
        if (entity.isEmpty()) {
            return false;
        }
        BiliCookie cookie = entity.get();
        return cookie.getExpiresAt() == null || cookie.getExpiresAt().isAfter(LocalDateTime.now());
    }

    /**
     * 删除用户 Cookie
     *
     * @param userId B站用户 ID
     */
    @Transactional
    public void deleteCookie(String userId) {
        cookieRepository.deleteByUserId(userId);
        log.info("Deleted cookie for user: {}", userId);
    }
}
