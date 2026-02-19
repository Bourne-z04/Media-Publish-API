package com.hui.bilibili.repository;

import com.hui.bilibili.model.entity.BiliCookie;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * B站 Cookie 数据访问层
 */
@Repository
public interface BiliCookieRepository extends JpaRepository<BiliCookie, Long> {

    /**
     * 根据 B站用户 ID 查询 Cookie
     */
    Optional<BiliCookie> findByUserId(String userId);

    /**
     * 根据 B站用户 ID 删除 Cookie
     */
    void deleteByUserId(String userId);

    /**
     * 检查用户 Cookie 是否存在
     */
    boolean existsByUserId(String userId);
}
