package com.studylog.model;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "users", uniqueConstraints = @UniqueConstraint(columnNames = "username"))
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 32)
    private String username;

    @Column(name = "password_hash", nullable = false, length = 200)
    private String passwordHash;

    @Column(nullable = false, length = 16)
    private String color = "4a90d9";

    @Column(name = "is_admin", nullable = false)
    private boolean admin = false;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }
    public boolean isAdmin() { return admin; }
    public void setAdmin(boolean admin) { this.admin = admin; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
