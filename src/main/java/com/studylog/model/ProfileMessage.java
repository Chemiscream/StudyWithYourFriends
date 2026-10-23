package com.studylog.model;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

/**
 * 个人档案留言板：访客在某个用户的档案页留下的一句话。
 * 纯文本存储（前端先转义再渲染），与记录留言（comments）是两套独立数据。
 */
@Entity
@Table(name = "profile_messages", indexes = {
        @Index(name = "idx_profile_msg_board", columnList = "profile_username,created_at"),
        @Index(name = "idx_profile_msg_author", columnList = "username")
})
public class ProfileMessage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 留言板主人（档案页归属用户）。 */
    @Column(name = "profile_username", nullable = false, length = 32)
    private String profileUsername;

    /** 留言者。 */
    @Column(nullable = false, length = 32)
    private String username;

    @Column(nullable = false, length = 500)
    private String content;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getProfileUsername() { return profileUsername; }
    public void setProfileUsername(String profileUsername) { this.profileUsername = profileUsername; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
