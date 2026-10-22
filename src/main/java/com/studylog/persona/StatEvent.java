package com.studylog.persona;

import jakarta.persistence.*;

import java.time.OffsetDateTime;

/**
 * 五维人格计分事件：每次加分写一条，联合唯一键（来源类型 + 来源 id + 维度 + 行为人）防止重复计分。
 * 按上海日期计分的来源（打卡、媒体、回归、连续打卡、@提及、转发）把行为人记为本人，
 * 这样同一 epochDay 在不同用户之间不会冲突。
 * 所有事件只增不减，删除记录/取消点赞也不会回撤。
 */
@Entity
@Table(name = "stat_events",
        uniqueConstraints = @UniqueConstraint(name = "uq_stat_event_source",
                columnNames = {"source_type", "source_id", "stat", "actor"}),
        indexes = @Index(name = "idx_stat_events_username", columnList = "username"))
public class StatEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 32)
    private String username;

    /** 维度：knowledge / courage / dexterity / kindness / charm */
    @Column(nullable = false, length = 16)
    private String stat;

    /** 本次加分（恒为正数）。 */
    @Column(nullable = false)
    private int delta;

    @Column(name = "source_type", nullable = false, length = 32)
    private String sourceType;

    @Column(name = "source_id", nullable = false)
    private long sourceId;

    /** 行为人（如点赞者）；没有行为人或为系统来源时为空字符串，不能为 null。 */
    @Column(nullable = false, length = 32)
    private String actor = "";

    /**
     * 是否已被本人看到。自己行为产生的加分在操作时即时播放过 POINT UP，
     * 记为已见；别人带来的加分（点赞/收藏/留言）保持未读，等本人下次打开站点合并播放。
     */
    @Column(nullable = false)
    private boolean seen;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getStat() { return stat; }
    public void setStat(String stat) { this.stat = stat; }
    public int getDelta() { return delta; }
    public void setDelta(int delta) { this.delta = delta; }
    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }
    public long getSourceId() { return sourceId; }
    public void setSourceId(long sourceId) { this.sourceId = sourceId; }
    public String getActor() { return actor; }
    public void setActor(String actor) { this.actor = actor; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public boolean isSeen() { return seen; }
    public void setSeen(boolean seen) { this.seen = seen; }
}
