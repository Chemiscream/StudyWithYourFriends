package com.studylog.repo;

import com.studylog.model.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    List<Notification> findByRecipientOrderByCreatedAtDesc(String recipient);

    long countByRecipientAndReadFalse(String recipient);

    Optional<Notification> findByIdAndRecipient(Long id, String recipient);

    // 派生 deleteBy* 默认不带事务，必须显式声明
    @Transactional
    void deleteByRecipient(String recipient);

    @Transactional
    void deleteByRecordId(Long recordId);

    @Transactional
    void deleteByCommentId(Long commentId);
}
