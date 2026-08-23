package co.replyfit.inquiry;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import co.replyfit.auth.AuthUser;
import co.replyfit.dashboard.DashboardCache;
import co.replyfit.inquiry.dto.InquiryDtos.InquiryDetail;
import co.replyfit.inquiry.dto.InquiryDtos.InquiryListItem;
import co.replyfit.inquiry.dto.InquiryDtos.PageResponse;
import co.replyfit.kafka.EventPublisher;
import co.replyfit.kafka.event.InquiryUploadedEvent;
import co.replyfit.upload.JobProgressService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/inquiries")
@Tag(name = "05. 문의", description = "고객 문의 조회. 상태 흐름: RECEIVED(접수) → PROCESSING(AI 처리중) → DRAFTED(초안 생성)/NEEDS_REVIEW(검토 필요) → APPROVED(승인) → SENT(발송 완료)")
public class InquiryController {

    public record RegenerateResponse(String jobId, String message) {
    }

    private final InquiryService inquiryService;
    private final JobProgressService jobProgressService;
    private final EventPublisher eventPublisher;
    private final DashboardCache dashboardCache;

    public InquiryController(InquiryService inquiryService,
                             JobProgressService jobProgressService,
                             EventPublisher eventPublisher,
                             DashboardCache dashboardCache) {
        this.inquiryService = inquiryService;
        this.jobProgressService = jobProgressService;
        this.eventPublisher = eventPublisher;
        this.dashboardCache = dashboardCache;
    }

    @Operation(summary = "문의 목록 조회 (필터·검색·페이징)")
    @GetMapping
    public ResponseEntity<PageResponse<InquiryListItem>> list(
            @AuthenticationPrincipal AuthUser me,
            @Parameter(description = "카테고리 필터: SIZE·SHIPPING·EXCHANGE_RETURN·COLOR·RESTOCK·OTHER")
            @RequestParam(required = false) String category,
            @Parameter(description = "상태 필터: RECEIVED·PROCESSING·DRAFTED·NEEDS_REVIEW·APPROVED·SENT·FAILED")
            @RequestParam(required = false) String status,
            @Parameter(description = "본문·상품명 검색어")
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(inquiryService.search(me.id(), category, status, q, page, size));
    }

    @Operation(summary = "문의 상세 조회", description = "AI 답변 초안과 초안에 인용된 정책 목록을 함께 반환합니다.")
    @GetMapping("/{id}")
    public ResponseEntity<InquiryDetail> detail(@AuthenticationPrincipal AuthUser me,
                                                @PathVariable Long id) {
        return ResponseEntity.ok(inquiryService.getDetail(me.id(), id));
    }

    /** AI 초안 재생성 — Kafka 파이프라인을 다시 태운다. */
    @Operation(summary = "AI 초안 재생성 (비동기)",
            description = "202와 함께 jobId 반환. 재분류부터 다시 수행하며, 기존 승인 이력은 초기화됩니다.")
    @PostMapping("/{id}/regenerate")
    public ResponseEntity<RegenerateResponse> regenerate(@AuthenticationPrincipal AuthUser me,
                                                         @PathVariable Long id) {
        Inquiry inquiry = inquiryService.getOwned(me.id(), id);
        String jobId = jobProgressService.create("regenerate", 1);
        eventPublisher.publishInquiriesUploaded(
                new InquiryUploadedEvent(jobId, me.id(), List.of(inquiry.getId())));
        dashboardCache.evict(me.id());
        return ResponseEntity.accepted()
                .body(new RegenerateResponse(jobId, "AI가 답변 초안을 다시 생성하고 있습니다."));
    }
}
