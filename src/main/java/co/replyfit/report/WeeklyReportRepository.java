package co.replyfit.report;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface WeeklyReportRepository extends JpaRepository<WeeklyReport, Long> {

    List<WeeklyReport> findByUserIdOrderByWeekStartDesc(Long userId);

    Optional<WeeklyReport> findByUserIdAndWeekStart(Long userId, LocalDate weekStart);
}
