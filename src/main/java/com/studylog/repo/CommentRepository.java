package com.studylog.repo;

import com.studylog.model.Comment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;

public interface CommentRepository extends JpaRepository<Comment, Long> {
    List<Comment> findByRecordIdInOrderByCreatedAtAsc(Collection<Long> recordIds);

    // 派生 deleteBy* 默认不带事务，em.remove 会抛 TransactionRequiredException，必须显式声明
    @Transactional
    void deleteByRecordId(Long recordId);
}
