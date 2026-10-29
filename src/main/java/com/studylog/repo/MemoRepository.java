package com.studylog.repo;

import com.studylog.model.Memo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/** 个人备忘录：全部查询都以当前登录用户名为界，天然隔离账号。 */
public interface MemoRepository extends JpaRepository<Memo, Long> {

    /** 未完成的排前面，同组内按创建时间倒序（最新写的在最上面）。 */
    List<Memo> findByUsernameOrderByDoneAscCreatedAtDesc(String username);

    Optional<Memo> findByIdAndUsername(Long id, String username);

    long countByUsername(String username);
}
