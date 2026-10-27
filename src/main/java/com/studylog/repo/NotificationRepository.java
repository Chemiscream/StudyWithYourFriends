package com.studylog.repo;

import com.studylog.model.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    List<Notification> findByRecipientOrderByCreatedAtDesc(String recipient);

    long countByRecipientAndReadFalse(String recipient);

    /** 一键已读：把该用户的全部未读通知标记为已读，返回更新条数。 */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("update Notification n set n.read = true where n.recipient = :recipient and n.read = false")
    int markAllRead(@Param("recipient") String recipient);

    Optional<Notification> findByIdAndRecipient(Long id, String recipient);

    // 派生 deleteBy* 默认不带事务，必须显式声明
    @Transactional
    void deleteByRecipient(String recipient);

    @Transactional
    void deleteByRecordId(Long recordId);

    @Transactional
    void deleteByCommentId(Long commentId);

    /** 删除档案留言板留言时撤回对应通知。 */
    @Transactional
    void deleteByBoardId(Long boardId);

    /** 取消记录点赞时撤回对应的点赞通知（避免点赞/取消/再点赞刷通知）。 */
    @Transactional
    void deleteByRecipientAndActorAndTypeAndRecordId(String recipient, String actor, String type, Long recordId);

    /** 取消留言点赞时撤回对应的点赞通知。 */
    @Transactional
    void deleteByRecipientAndActorAndTypeAndCommentId(String recipient, String actor, String type, Long commentId);
}
