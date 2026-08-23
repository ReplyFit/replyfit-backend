package co.replyfit.worker;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import co.replyfit.ai.LegalNotices;
import co.replyfit.ai.LlmClient;
import co.replyfit.ai.LlmClient.ClassificationResult;
import co.replyfit.ai.LlmClient.DraftContext;
import co.replyfit.ai.LlmClient.DraftResult;
import co.replyfit.ai.LlmClient.PolicyRef;
import co.replyfit.ai.PolicyGuard;
import co.replyfit.ai.PromptTemplates;
import co.replyfit.dashboard.DashboardCache;
import co.replyfit.kafka.KafkaTopics;
import co.replyfit.kafka.event.InquiryUploadedEvent;
import co.replyfit.upload.JobProgressService;
import co.replyfit.worker.InquiryProcessor.WorkItem;

/**
 * Kafka 워커 — 문의 AI 파이프라인 (worker 프로필에서만 활성화).
 *
 * 처리 흐름(사업계획서 5단계 중 3~4단계):
 *   업로드된 문의 → ③ 유형 자동분류 → ④ 정책 기반 답변 초안 생성 → 정책 검증 → 저장
 */
@Component
@Profile("worker")
public class InquiryPipelineWorker {

    private static final Logger log = LoggerFactory.getLogger(InquiryPipelineWorker.class);

    private final InquiryProcessor processor;
    private final LlmClient llmClient;
    private final JobProgressService jobProgressService;
    private final DashboardCache dashboardCache;

    public InquiryPipelineWorker(InquiryProcessor processor,
                                 LlmClient llmClient,
                                 JobProgressService jobProgressService,
                                 DashboardCache dashboardCache) {
        this.processor = processor;
        this.llmClient = llmClient;
        this.jobProgressService = jobProgressService;
        this.dashboardCache = dashboardCache;
    }

    @KafkaListener(topics = KafkaTopics.INQUIRIES_UPLOADED)
    public void onInquiriesUploaded(InquiryUploadedEvent event) {
        log.info("Processing inquiry batch jobId={} count={}", event.jobId(), event.inquiryIds().size());
        for (Long inquiryId : event.inquiryIds()) {
            try {
                processOne(inquiryId);
                jobProgressService.incrementProcessed(event.jobId());
            } catch (Exception e) {
                log.error("Failed to process inquiry {}", inquiryId, e);
                processor.markFailed(inquiryId);
                jobProgressService.incrementFailed(event.jobId());
            }
            // 프론트엔드가 폴링 중 — 문의 하나 처리할 때마다 대시보드 캐시 무효화
            dashboardCache.evict(event.userId());
        }
        jobProgressService.complete(event.jobId());
        log.info("Completed inquiry batch jobId={}", event.jobId());
    }

    private void processOne(Long inquiryId) {
        // 1) 트랜잭션: 상태 전환 + 스냅샷 확보
        WorkItem item = processor.markProcessing(inquiryId);

        // 2) LLM 호출(트랜잭션 밖): 유형 자동분류
        ClassificationResult classification = llmClient.classify(item.productName(), item.content());

        // 3) 카테고리 관련 정책 로드
        List<PolicyRef> policies = processor.loadRelevantPolicies(item.userId(), classification.category());

        // 4) LLM 호출(트랜잭션 밖): 정책 기반 답변 초안 생성
        String legalNotice = PromptTemplates.needsLegalNotice(classification.category())
                ? LegalNotices.WITHDRAWAL_RIGHT : null;
        DraftContext context = new DraftContext(
                item.storeName(), item.customerName(), item.productName(), item.channel(),
                item.content(), classification.category(), policies, legalNotice);
        DraftResult draft = llmClient.generateDraft(context);

        // 5) 정책 검증 — 출처 없는 수치 표현이 있으면 '검토 필요'로 전환
        PolicyGuard.GuardResult guard = PolicyGuard.verify(draft.content(), context);

        // 6) 트랜잭션: 결과 저장
        processor.saveResult(inquiryId, classification.category(), classification.confidence(),
                draft, guard, llmClient.name());
    }
}
