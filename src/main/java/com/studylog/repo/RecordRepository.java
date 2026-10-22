package com.studylog.repo;

import com.studylog.model.Record;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RecordRepository extends JpaRepository<Record, Long> {
    /** AI 助手回复「夸我」类留言时，取该用户最近的学习记录作为真实素材。 */
    List<Record> findTop2ByUsernameOrderByCreatedAtDesc(String username);
}
