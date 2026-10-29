package com.studylog.model;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

/** 个人备忘录：每个账号私有的小条目，可勾选完成。 */
@Entity
@Table(name = "memos", indexes = @Index(name = "idx_memo_user", columnList = "username"))
public class Memo {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 32)
    private String username;

    @Column(nullable = false, length = 500)
    private String content;

    /** 是否已完成（勾选后保留在列表里，置灰显示）。 */
    @Column(nullable = false)
    private boolean done;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public boolean isDone() { return done; }
    public void setDone(boolean done) { this.done = done; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
