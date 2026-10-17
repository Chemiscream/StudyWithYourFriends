package com.studylog.repo;

import com.studylog.model.AiMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface AiMessageRepository extends JpaRepository<AiMessage, Long> {
    List<AiMessage> findByUsernameOrderByCreatedAtAscIdAsc(String username);

    // 派生 deleteBy* 默认不带事务，必须显式声明（同 CommentRepository）
    @Transactional
    void deleteByUsername(String username);
}
