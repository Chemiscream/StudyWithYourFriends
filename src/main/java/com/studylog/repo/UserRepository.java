package com.studylog.repo;

import com.studylog.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
    boolean existsByUsername(String username);
    Optional<User> findFirstByAdminTrue();
    List<User> findAllByOrderByUsername();
    // 管理员账号对普通用户隐藏（@提及不出现、不通知）
    List<User> findAllByAdminFalseOrderByUsername();
    boolean existsByUsernameAndAdminTrue(String username);
}
