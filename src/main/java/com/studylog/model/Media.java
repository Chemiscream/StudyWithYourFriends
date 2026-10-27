package com.studylog.model;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

/**
 * 媒体文件索引：图片/视频落盘到 media/ 目录，正文只保存相对 URL。
 * sha1 相同的文件只存一份（不同记录引用同一 URL 时共用）。
 */
@Entity
@Table(name = "media", indexes = {
        @Index(name = "uk_media_sha1", columnList = "sha1", unique = true),
        @Index(name = "idx_media_created", columnList = "created_at")
})
public class Media {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 文件内容的 SHA-1（40 位十六进制），用于去重与回收引用判断。 */
    @Column(nullable = false, length = 40)
    private String sha1;

    /** 相对 media 根目录的路径，如 ab/cd/<sha1>.png。 */
    @Column(nullable = false, length = 128)
    private String path;

    /** 缩略图相对路径；无法生成（如 webp/视频）时为空，前端直接用原图。 */
    @Column(length = 128)
    private String thumb;

    @Column(nullable = false, length = 64)
    private String mime;

    @Column(name = "file_size", nullable = false)
    private Long size;

    private Integer width;
    private Integer height;

    @Column(nullable = false, length = 32)
    private String uploader;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getSha1() { return sha1; }
    public void setSha1(String sha1) { this.sha1 = sha1; }
    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }
    public String getThumb() { return thumb; }
    public void setThumb(String thumb) { this.thumb = thumb; }
    public String getMime() { return mime; }
    public void setMime(String mime) { this.mime = mime; }
    public Long getSize() { return size; }
    public void setSize(Long size) { this.size = size; }
    public Integer getWidth() { return width; }
    public void setWidth(Integer width) { this.width = width; }
    public Integer getHeight() { return height; }
    public void setHeight(Integer height) { this.height = height; }
    public String getUploader() { return uploader; }
    public void setUploader(String uploader) { this.uploader = uploader; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
