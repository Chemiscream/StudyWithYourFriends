package com.studylog.repo;

import com.studylog.model.Record;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;

public interface RecordRepository extends JpaRepository<Record, Long> {
    /** AI 助手回复时，取相关用户最近的学习记录作为真实素材（多取一条便于排除当前记录）。 */
    List<Record> findTop3ByUsernameOrderByCreatedAtDesc(String username);

    /** 该用户最近 5 条记录（五维人格快照用）。 */
    List<Record> findTop5ByUsernameOrderByCreatedAtDesc(String username);

    /** 该记录之前（按自增 id 更早）该用户已有多少条记录，用于判断是否史上第一条。 */
    long countByUsernameAndIdLessThan(String username, Long id);

    /** 该记录的上一条记录（按自增 id 倒序取一条）。 */
    @Query("select r from Record r where r.username = :username and r.id < :id order by r.id desc")
    List<Record> findPrevious(@Param("username") String username,
                              @Param("id") Long id,
                              Pageable pageable);

    /** 该用户在指定时间点之前的上海日期（去重），用于计算连续打卡天数。 */
    @Query(value = "SELECT DISTINCT DATE(created_at + INTERVAL 8 HOUR) FROM records " +
            "WHERE username = :username AND created_at < :before", nativeQuery = true)
    List<Object> findDaysBefore(@Param("username") String username,
                                @Param("before") OffsetDateTime before);
}
