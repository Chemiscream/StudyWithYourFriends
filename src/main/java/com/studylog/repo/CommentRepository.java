package com.studylog.repo;

import com.studylog.model.Comment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface CommentRepository extends JpaRepository<Comment, Long> {
    List<Comment> findByRecordIdInOrderByCreatedAtAsc(Collection<Long> recordIds);
    void deleteByRecordId(Long recordId);
}
