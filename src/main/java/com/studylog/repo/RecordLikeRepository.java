package com.studylog.repo;

import com.studylog.model.RecordLike;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface RecordLikeRepository extends JpaRepository<RecordLike, Long> {
    Optional<RecordLike> findByRecordIdAndUsername(Long recordId, String username);

    long countByRecordId(Long recordId);

    // 派生 deleteBy* 默认不带事务，必须显式声明
    @Transactional
    void deleteByRecordId(Long recordId);

    /** 批量统计多条记录的点赞数，返回 [recordId, count]。 */
    @Query("SELECT l.recordId, COUNT(l) FROM RecordLike l WHERE l.recordId IN :ids GROUP BY l.recordId")
    List<Object[]> countByRecordIds(@Param("ids") Collection<Long> ids);

    /** 批量查某用户在多条记录中已赞的记录 id。 */
    @Query("SELECT l.recordId FROM RecordLike l WHERE l.recordId IN :ids AND l.username = :username")
    List<Long> findLikedRecordIds(@Param("ids") Collection<Long> ids, @Param("username") String username);
}
