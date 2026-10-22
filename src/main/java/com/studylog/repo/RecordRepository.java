package com.studylog.repo;

import com.studylog.model.Record;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RecordRepository extends JpaRepository<Record, Long> {
    /** AI 助手回复时，取相关用户最近的学习记录作为真实素材（多取一条便于排除当前记录）。 */
    List<Record> findTop3ByUsernameOrderByCreatedAtDesc(String username);
}
