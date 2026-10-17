package com.studylog.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/** AI 日报：每天一份，按上海日期归档全站学习统计 + AI 生成的趣味摘要。 */
@Entity
@Table(name = "daily_reports", indexes = {
        @Index(name = "idx_daily_report_date", columnList = "report_date", unique = true)
})
public class DailyReport {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "report_date", nullable = false)
    private LocalDate reportDate;

    /** 统计数据 JSON：total / ranking / words */
    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String content;

    /** AI 生成的趣味摘要，可空（模型离线时留空） */
    @Column(columnDefinition = "TEXT")
    private String summary;

    /** AI 生成的一句话副标题（不超过20字），用于列表/面板预览 */
    @Column(name = "subtitle", length = 100)
    private String subtitle;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public LocalDate getReportDate() { return reportDate; }
    public void setReportDate(LocalDate reportDate) { this.reportDate = reportDate; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public String getSubtitle() { return subtitle; }
    public void setSubtitle(String subtitle) { this.subtitle = subtitle; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
