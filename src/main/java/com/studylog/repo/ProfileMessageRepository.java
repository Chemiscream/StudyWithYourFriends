package com.studylog.repo;

import com.studylog.model.ProfileMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface ProfileMessageRepository extends JpaRepository<ProfileMessage, Long> {

    /** 留言板首屏：最新的 N 条。 */
    List<ProfileMessage> findByProfileUsernameOrderByIdDesc(String profileUsername, Pageable pageable);

    /** 「加载更多」：比游标 id 更早的 N 条（按 id 倒序，等价于按时间倒序）。 */
    List<ProfileMessage> findByProfileUsernameAndIdLessThanOrderByIdDesc(String profileUsername, Long before, Pageable pageable);

    long countByProfileUsername(String profileUsername);

    // 派生 deleteBy* 默认不带事务，必须显式声明
    @Transactional
    void deleteByProfileUsername(String profileUsername);
}
