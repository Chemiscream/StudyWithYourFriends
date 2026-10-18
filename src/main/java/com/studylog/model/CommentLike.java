package com.studylog.model;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

/** 留言点赞：一条留言同一用户只能赞一次（联合唯一键）。 */
@Entity
@Table(name = "comment_likes",
        uniqueConstraints = @UniqueConstraint(name = "uq_like_comment_user", columnNames = {"comment_id", "username"}),
        indexes = @Index(name = "idx_likes_comment", columnList = "comment_id"))
public class CommentLike {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "comment_id", nullable = false)
    private Long commentId;

    @Column(nullable = false, length = 32)
    private String username;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getCommentId() { return commentId; }
    public void setCommentId(Long commentId) { this.commentId = commentId; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
