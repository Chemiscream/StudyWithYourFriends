package com.studylog.repo;

import com.studylog.model.RecordFavorite;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface RecordFavoriteRepository extends JpaRepository<RecordFavorite, Long> {
    Optional<RecordFavorite> findByRecordIdAndUsername(Long recordId, String username);

    // 派生 deleteBy* 默认不带事务，必须显式声明
    @Transactional
    void deleteByRecordId(Long recordId);

    /** 某用户收藏的记录（按收藏时间倒序，用于「我的收藏」弹窗）。 */
    List<RecordFavorite> findByUsernameOrderByCreatedAtDesc(String username, Pageable pageable);

    /** 批量查某用户在多条记录中已收藏的记录 id。 */
    @Query("SELECT f.recordId FROM RecordFavorite f WHERE f.recordId IN :ids AND f.username = :username")
    List<Long> findFavoritedRecordIds(@Param("ids") Collection<Long> ids, @Param("username") String username);
}
