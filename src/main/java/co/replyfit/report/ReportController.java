package co.replyfit.report;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import co.replyfit.auth.AuthUser;
import co.replyfit.common.ApiException;
import co.replyfit.kafka.EventPublisher;
import co.replyfit.kafka.event.ReportRequestedEvent;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/reports")
@Tag(name = "09. VOC 리포트", description = "주간 VOC 리포트 — 반품 사유 TOP5, 문제 상품, 상세페이지 문구 제안, AI 인사이트 (문의+리뷰 통합 분석)")
public class ReportController {

    public record ReportListItem(Long id, LocalDate weekStart, LocalDate weekEnd,
                                 String status, LocalDateTime createdAt) {
        static ReportListItem from(WeeklyReport report) {
            return new ReportListItem(report.getId(), report.getWeekStart(), report.getWeekEnd(),
                    report.getStatus().name(), report.getCreatedAt());
        }
    }

    public record ReportDetail(Long id, LocalDate weekStart, LocalDate weekEnd,
                               String status, JsonNode payload, LocalDateTime createdAt) {
    }

    public record GenerateRequest(LocalDate weekStart) {
    }

    public record GenerateResponse(Long reportId, String status, String message) {
    }

    private final WeeklyReportRepository reportRepository;
    private final ReportService reportService;
    private final EventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    public ReportController(WeeklyReportRepository reportRepository,
                            ReportService reportService,
                            EventPublisher eventPublisher,
                            ObjectMapper objectMapper) {
        this.reportRepository = reportRepository;
        this.reportService = reportService;
        this.eventPublisher = eventPublisher;
        this.objectMapper = objectMapper;
    }

    @Operation(summary = "리포트 목록 조회 (최신 주 우선)")
    @GetMapping
    public ResponseEntity<List<ReportListItem>> list(@AuthenticationPrincipal AuthUser me) {
        return ResponseEntity.ok(reportRepository.findByUserIdOrderByWeekStartDesc(me.id())
                .stream().map(ReportListItem::from).toList());
    }

    @Operation(summary = "리포트 상세 조회",
            description = "status가 READY일 때 payload(요약·카테고리 분포·반품 사유 TOP5·문제 상품·문구 제안·AI 인사이트)가 채워집니다. GENERATING이면 3초 간격 폴링을 권장합니다.")
    @GetMapping("/{id}")
    public ResponseEntity<ReportDetail> detail(@AuthenticationPrincipal AuthUser me,
                                               @PathVariable Long id) {
        WeeklyReport report = getOwned(me.id(), id);
        JsonNode payload = null;
        if (report.getPayload() != null) {
            try {
                payload = objectMapper.readTree(report.getPayload());
            } catch (Exception e) {
                throw new IllegalStateException("리포트 데이터를 읽을 수 없습니다.", e);
            }
        }
        return ResponseEntity.ok(new ReportDetail(report.getId(), report.getWeekStart(),
                report.getWeekEnd(), report.getStatus().name(), payload, report.getCreatedAt()));
    }

    /** 주간 리포트 생성 요청 → Kafka 워커가 비동기로 집계 + AI 인사이트 생성 */
    @Operation(summary = "주간 리포트 생성 요청 (비동기)",
            description = "weekStart 미지정 시 이번 주(월요일 기준). 같은 주 리포트가 이미 READY면 재생성하지 않고 기존 ID를 반환합니다.")
    @PostMapping("/generate")
    public ResponseEntity<GenerateResponse> generate(@AuthenticationPrincipal AuthUser me,
                                                     @RequestBody(required = false) GenerateRequest request) {
        LocalDate weekStart = (request == null || request.weekStart() == null)
                ? LocalDate.now().with(java.time.DayOfWeek.MONDAY)
                : request.weekStart();
        WeeklyReport report = reportService.createPending(me.id(), weekStart);
        if (report.getStatus() != WeeklyReport.Status.READY) {
            eventPublisher.publishReportRequested(new ReportRequestedEvent(me.id(), report.getId()));
        }
        return ResponseEntity.accepted().body(new GenerateResponse(
                report.getId(), report.getStatus().name(),
                report.getStatus() == WeeklyReport.Status.READY
                        ? "이미 생성된 리포트입니다."
                        : "리포트를 생성하고 있습니다. 잠시 후 확인해 주세요."));
    }

    private WeeklyReport getOwned(Long userId, Long reportId) {
        WeeklyReport report = reportRepository.findById(reportId)
                .orElseThrow(() -> ApiException.notFound("리포트를 찾을 수 없습니다."));
        if (!report.getUser().getId().equals(userId)) {
            throw ApiException.forbidden("접근 권한이 없습니다.");
        }
        return report;
    }
}
