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

@RestController
@RequestMapping("/api/inquiries")
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

    @GetMapping
    public ResponseEntity<PageResponse<InquiryListItem>> list(
            @AuthenticationPrincipal AuthUser me,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(inquiryService.search(me.id(), category, status, q, page, size));
    }

    @GetMapping("/{id}")
    public ResponseEntity<InquiryDetail> detail(@AuthenticationPrincipal AuthUser me,
                                                @PathVariable Long id) {
        return ResponseEntity.ok(inquiryService.getDetail(me.id(), id));
    }

    /** AI 초안 재생성 — Kafka 파이프라인을 다시 태운다. */
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
