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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/uploads")
@Tag(name = "03. CSV 업로드", description = "문의·리뷰 CSV 업로드. 이름·주문번호·연락처는 저장 전에 즉시 마스킹되며 원본은 저장되지 않습니다. (최대 20MB, UTF-8)")
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
    @Operation(summary = "문의 CSV 업로드 (비동기 AI 처리)",
            description = "필수 열: 문의내용 · 선택: 문의일시, 채널, 고객명, 주문번호, 상품명. "
                    + "202와 함께 jobId가 반환되며, GET /api/jobs/{jobId}로 처리 진행률을 폴링하세요.")
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
    @Operation(summary = "리뷰 CSV 업로드 (동기 처리)",
            description = "필수 열: 리뷰내용 · 선택: 작성일시, 상품명, 평점. 감성 판정과 이슈 키워드 추출까지 즉시 완료됩니다.")
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
