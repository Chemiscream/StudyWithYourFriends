package com.studylog.sec;

import com.studylog.model.User;
import com.studylog.repo.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/** 启动时确保内置管理员账号存在。 */
@Component
public class AdminSeeder implements CommandLineRunner {

    public static final String ADMIN_USERNAME = "IamAdmin";
    public static final String ADMIN_PASSWORD = "Sakurabaemma";

    private final UserRepository users;

    public AdminSeeder(UserRepository users) {
        this.users = users;
    }

    @Override
    public void run(String... args) {
        User admin = users.findByUsername(ADMIN_USERNAME).orElseGet(() -> {
            User u = new User();
            u.setUsername(ADMIN_USERNAME);
            u.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
            u.setColor("b83b3b");
            return u;
        });
        boolean changed = false;
        if (!admin.isAdmin()) { admin.setAdmin(true); changed = true; }
        // 始终按配置密码重置一次，保证管理员密码可恢复
        if (!Passwords.verify(ADMIN_PASSWORD, admin.getPasswordHash())) {
            admin.setPasswordHash(Passwords.hash(ADMIN_PASSWORD));
            changed = true;
        }
        if (admin.getId() == null) users.save(admin);
        else if (changed) users.save(admin);
    }
}
