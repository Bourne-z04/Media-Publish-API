package com.hui.bilibili.repository;

import com.hui.bilibili.model.entity.PublishTask;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 发布任务数据访问层
 */
@Repository
public interface PublishTaskRepository extends JpaRepository<PublishTask, Long> {

    /**
     * 根据任务 ID 查询
     */
    Optional<PublishTask> findByTaskId(String taskId);

    /**
     * 根据用户 ID 查询所有任务（按提交时间倒序）
     */
    List<PublishTask> findByUserIdOrderBySubmittedAtDesc(String userId);

    /**
     * 根据用户 ID 查询最近一条任务
     */
    Optional<PublishTask> findTopByUserIdOrderBySubmittedAtDesc(String userId);
}
