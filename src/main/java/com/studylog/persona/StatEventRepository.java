package com.studylog.persona;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;

public interface StatEventRepository extends JpaRepository<StatEvent, Long> {

    /** 按用户汇总各维度累计点数，返回 [stat, sum(delta)]。 */
    @Query("select e.stat, sum(e.delta) from StatEvent e where e.username = :u group by e.stat")
    List<Object[]> totals(@Param("u") String u);

    /** 某维度在时间区间内的累计点数（左闭右开）。 */
    @Query("select coalesce(sum(e.delta), 0) from StatEvent e " +
            "where e.username = :u and e.stat = :s and e.createdAt >= :from and e.createdAt < :to")
    Integer sumBetween(@Param("u") String u, @Param("s") String s,
                       @Param("from") OffsetDateTime from, @Param("to") OffsetDateTime to);

    /** 来源去重：同一来源（类型 + id + 维度 + 行为人）只计一次。 */
    boolean existsBySourceTypeAndSourceIdAndStatAndActor(String sourceType, long sourceId,
                                                         String stat, String actor);

    /** 某人是否拿过某类来源的加分（用于「第一条记录」这类终身一次的奖励）。 */
    boolean existsByUsernameAndSourceType(String username, String sourceType);

    /** 某维度某日的事件明细，返回 [sourceType, delta]，用于按来源分组计算每日额度。 */
    @Query("select e.sourceType, e.delta from StatEvent e " +
            "where e.username = :u and e.stat = :s and e.createdAt >= :from and e.createdAt < :to")
    List<Object[]> dailyBySource(@Param("u") String u, @Param("s") String s,
                                 @Param("from") OffsetDateTime from, @Param("to") OffsetDateTime to);

    /** 本人还未看到的加分，按维度汇总，返回 [stat, sum(delta)]（登录后合并播放用）。 */
    @Query("select e.stat, sum(e.delta) from StatEvent e " +
            "where e.username = :u and e.seen = false group by e.stat")
    List<Object[]> unseenGains(@Param("u") String u);

    /** 标记本人全部未读加分为已见。 */
    @Modifying
    @Query("update StatEvent e set e.seen = true where e.username = :u and e.seen = false")
    int markSeen(@Param("u") String u);

    /** 历史回填完成后把全部事件标记为已见，避免老用户下次登录时一次性弹出陈年加分。 */
    @Modifying
    @Query("update StatEvent e set e.seen = true where e.seen = false")
    int markAllSeen();
}
