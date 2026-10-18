package com.studylog.model;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

/** 站内通知：有人评论你的记录或回复你的评论时生成。 */
@Entity
@Table(name = "notifications", indexes = {
        @Index(name = "idx_notif_recipient", columnList = "recipient,is_read"),
        @Index(name = "idx_notif_record", columnList = "record_id")
})
public class Notification {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 接收通知的用户名。 */
    @Column(nullable = false, length = 32)
    private String recipient;

    /** 触发通知的用户名（评论/回复者）。 */
    @Column(nullable = false, length = 32)
    private String actor;

    /** comment=评论了你的记录；reply=回复了你的评论。 */
    @Column(nullable = false, length = 16)
    private String type;

    @Column(name = "record_id", nullable = false)
    private Long recordId;

    @Column(name = "comment_id", nullable = false)
    private Long commentId;

    /** 留言内容摘要（截断），便于列表直接展示。 */
    @Column(length = 120)
    private String preview;

    @Column(name = "is_read", nullable = false)
    private boolean read;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getRecipient() { return recipient; }
    public void setRecipient(String recipient) { this.recipient = recipient; }
    public String getActor() { return actor; }
    public void setActor(String actor) { this.actor = actor; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public Long getRecordId() { return recordId; }
    public void setRecordId(Long recordId) { this.recordId = recordId; }
    public Long getCommentId() { return commentId; }
    public void setCommentId(Long commentId) { this.commentId = commentId; }
    public String getPreview() { return preview; }
    public void setPreview(String preview) { this.preview = preview; }
    public boolean isRead() { return read; }
    public void setRead(boolean read) { this.read = read; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
