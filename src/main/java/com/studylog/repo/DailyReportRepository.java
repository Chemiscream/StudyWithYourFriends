package com.studylog.repo;

import com.studylog.model.DailyReport;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface DailyReportRepository extends JpaRepository<DailyReport, Long> {
    Optional<DailyReport> findByReportDate(LocalDate date);

    /** 按日期倒序分页，用于列表弹窗 */
    Page<DailyReport> findByReportDateBeforeOrderByReportDateDesc(LocalDate before, Pageable pageable);

    boolean existsByReportDate(LocalDate date);
}
