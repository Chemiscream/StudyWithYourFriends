package com.studylog.repo;

import com.studylog.model.DailyReport;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DailyReportRepository extends JpaRepository<DailyReport, Long> {
    Optional<DailyReport> findByReportDate(LocalDate date);

    /** 按日期倒序分页，用于列表弹窗 */
    Page<DailyReport> findByReportDateBeforeOrderByReportDateDesc(LocalDate before, Pageable pageable);

    boolean existsByReportDate(LocalDate date);

    /** 所有已有日报的日期，供补生成任务判断哪些天缺失 */
    @Query("SELECT r.reportDate FROM DailyReport r")
    List<LocalDate> findAllReportDates();
}
