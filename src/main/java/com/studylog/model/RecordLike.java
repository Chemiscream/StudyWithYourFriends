package com.studylog.model;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

/** 学习记录点赞：一条记录同一用户只能赞一次（联合唯一键）。 */
@Entity
@Table(name = "record_likes",
        uniqueConstraints = @UniqueConstraint(name = "uq_like_record_user", columnNames = {"record_id", "username"}),
        indexes = @Index(name = "idx_likes_record", columnList = "record_id"))
public class RecordLike {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "record_id", nullable = false)
    private Long recordId;

    @Column(nullable = false, length = 32)
    private String username;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getRecordId() { return recordId; }
    public void setRecordId(Long recordId) { this.recordId = recordId; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
