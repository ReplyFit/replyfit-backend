package co.replyfit.upload;

import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import co.replyfit.auth.AuthUser;
import co.replyfit.common.ApiException;
import co.replyfit.dashboard.DashboardCache;
import co.replyfit.kafka.EventPublisher;
import co.replyfit.kafka.event.InquiryUploadedEvent;
import co.replyfit.user.User;
import co.replyfit.user.UserRepository;

@RestController
@RequestMapping("/api/uploads")
public class UploadController {

    public record UploadResponse(String jobId, int total, String message) {
    }

    private final CsvIngestService csvIngestService;
    private final JobProgressService jobProgressService;
    private final EventPublisher eventPublisher;
    private final UserRepository userRepository;
    private final DashboardCache dashboardCache;

    public UploadController(CsvIngestService csvIngestService,
                            JobProgressService jobProgressService,
                            EventPublisher eventPublisher,
                            UserRepository userRepository,
                            DashboardCache dashboardCache) {
        this.csvIngestService = csvIngestService;
        this.jobProgressService = jobProgressService;
        this.eventPublisher = eventPublisher;
        this.userRepository = userRepository;
        this.dashboardCache = dashboardCache;
    }

    /**
     * 문의 CSV 업로드 → 마스킹 후 저장 → Kafka로 AI 파이프라인(분류·초안 생성) 요청.
     */
    @PostMapping(value = "/inquiries", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<UploadResponse> uploadInquiries(@AuthenticationPrincipal AuthUser me,
                                                          @RequestPart("file") MultipartFile file) {
        User user = loadUser(me.id());
        List<Long> ids = csvIngestService.ingestInquiries(user, file);
        String jobId = jobProgressService.create("inquiries", ids.size());
        eventPublisher.publishInquiriesUploaded(new InquiryUploadedEvent(jobId, user.getId(), ids));
        dashboardCache.evict(user.getId());
        return ResponseEntity.accepted().body(new UploadResponse(
                jobId, ids.size(),
                "문의 " + ids.size() + "건이 접수되었습니다. AI가 분류와 답변 초안을 생성하고 있습니다."));
    }

    /**
     * 리뷰 CSV 업로드 → 마스킹·감성 분석 후 저장 (동기 처리).
     */
    @PostMapping(value = "/reviews", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<UploadResponse> uploadReviews(@AuthenticationPrincipal AuthUser me,
                                                        @RequestPart("file") MultipartFile file) {
        User user = loadUser(me.id());
        int count = csvIngestService.ingestReviews(user, file);
        String jobId = jobProgressService.create("reviews", count);
        // 리뷰는 동기 처리 — 작업을 즉시 완료 상태로 만든다.
        for (int i = 0; i < count; i++) {
            jobProgressService.incrementProcessed(jobId);
        }
        jobProgressService.complete(jobId);
        dashboardCache.evict(user.getId());
        return ResponseEntity.ok(new UploadResponse(jobId, count, "리뷰 " + count + "건이 등록되었습니다."));
    }

    private User loadUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> ApiException.unauthorized("사용자를 찾을 수 없습니다."));
    }
}
