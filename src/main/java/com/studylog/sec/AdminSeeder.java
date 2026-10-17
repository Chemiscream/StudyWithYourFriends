package com.studylog.sec;

import com.studylog.model.User;
import com.studylog.repo.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * 启动时确保内置管理员账号存在。
 * 凭据来自外部文件 admin-credentials.properties（不入 git），
 * 首次启动会把旧管理员账号迁移为配置的账号名并重置密码。
 */
@Component
public class AdminSeeder implements CommandLineRunner {

    @Value("${admin.username:}")
    private String adminUsername;

    @Value("${admin.password:}")
    private String adminPassword;

    private final UserRepository users;

    public AdminSeeder(UserRepository users) {
        this.users = users;
    }

    @Override
    public void run(String... args) {
        if (adminUsername.isBlank() || adminPassword.isBlank()) {
            System.out.println("[AdminSeeder] 未配置 admin.username/admin.password（admin-credentials.properties），跳过管理员初始化");
            return;
        }
        User admin = users.findByUsername(adminUsername)
                // 账号改名迁移：配置账号不存在时，接管已有的管理员账号
                .orElseGet(() -> users.findFirstByAdminTrue().orElseGet(() -> {
                    User u = new User();
                    u.setUsername(adminUsername);
                    u.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
                    u.setColor("b83b3b");
                    return u;
                }));
        boolean changed = false;
        if (!adminUsername.equals(admin.getUsername())) { admin.setUsername(adminUsername); changed = true; }
        if (!admin.isAdmin()) { admin.setAdmin(true); changed = true; }
        // 始终按配置密码重置一次，保证管理员密码可恢复
        if (!Passwords.verify(adminPassword, admin.getPasswordHash())) {
            admin.setPasswordHash(Passwords.hash(adminPassword));
            changed = true;
        }
        if (admin.getId() == null) users.save(admin);
        else if (changed) users.save(admin);
    }
}
