package co.replyfit.worker;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.replyfit.ai.LlmClient.DraftResult;
import co.replyfit.ai.LlmClient.PolicyRef;
import co.replyfit.ai.PolicyGuard;
import co.replyfit.common.ApiException;
import co.replyfit.draft.AnswerDraft;
import co.replyfit.draft.AnswerDraftRepository;
import co.replyfit.inquiry.Inquiry;
import co.replyfit.inquiry.InquiryCategory;
import co.replyfit.inquiry.InquiryRepository;
import co.replyfit.inquiry.InquiryStatus;
import co.replyfit.policy.PolicyRepository;
import co.replyfit.policy.PolicyType;

/**
 * 문의 처리 파이프라인의 트랜잭션 단위 작업.
 * LLM 호출(느린 작업)은 트랜잭션 밖에서 수행하도록 워커와 역할을 분리했다.
 */
@Service
public class InquiryProcessor {

    public record WorkItem(Long inquiryId, Long userId, String storeName, String channel,
                           String customerName, String productName, String content) {
    }

    private final InquiryRepository inquiryRepository;
    private final AnswerDraftRepository draftRepository;
    private final PolicyRepository policyRepository;

    public InquiryProcessor(InquiryRepository inquiryRepository,
                            AnswerDraftRepository draftRepository,
                            PolicyRepository policyRepository) {
        this.inquiryRepository = inquiryRepository;
        this.draftRepository = draftRepository;
        this.policyRepository = policyRepository;
    }

    @Transactional
    public WorkItem markProcessing(Long inquiryId) {
        Inquiry inquiry = inquiryRepository.findById(inquiryId)
                .orElseThrow(() -> ApiException.notFound("문의를 찾을 수 없습니다: " + inquiryId));
        inquiry.changeStatus(InquiryStatus.PROCESSING);
        return new WorkItem(
                inquiry.getId(),
                inquiry.getUser().getId(),
                inquiry.getUser().getStoreName(),
                inquiry.getChannel(),
                inquiry.getCustomerName(),
                inquiry.getProductName(),
                inquiry.getContent());
    }

    /** 카테고리와 관련성 높은 정책 유형을 골라 인용 후보로 제공한다. */
    @Transactional(readOnly = true)
    public List<PolicyRef> loadRelevantPolicies(Long userId, InquiryCategory category) {
        List<PolicyType> types = new ArrayList<>(switch (category) {
            case SIZE -> List.of(PolicyType.SIZE_GUIDE, PolicyType.EXCHANGE_RETURN);
            case SHIPPING -> List.of(PolicyType.SHIPPING);
            case EXCHANGE_RETURN -> List.of(PolicyType.EXCHANGE_RETURN, PolicyType.SHIPPING);
            case COLOR -> List.of(PolicyType.EXCHANGE_RETURN);
            case RESTOCK -> List.of(PolicyType.RESTOCK);
            case OTHER -> List.of();
        });
        types.add(PolicyType.GENERAL);
        return policyRepository.findByUserIdAndTypeIn(userId, types).stream()
                .map(policy -> new PolicyRef(policy.getId(), policy.getType().getLabel(),
                        policy.getTitle(), policy.getContent()))
                .toList();
    }

    @Transactional
    public void saveResult(Long inquiryId, InquiryCategory category, double confidence,
                           DraftResult draftResult, PolicyGuard.GuardResult guard, String engineName) {
        Inquiry inquiry = inquiryRepository.findById(inquiryId)
                .orElseThrow(() -> ApiException.notFound("문의를 찾을 수 없습니다: " + inquiryId));
        inquiry.classify(category, confidence);

        String citedIds = draftResult.citedPolicyIds().isEmpty()
                ? null
                : String.join(",", draftResult.citedPolicyIds().stream().map(String::valueOf).toList());
        String guardNote = guard.passed() ? null : guard.note();

        draftRepository.findByInquiryId(inquiryId).ifPresentOrElse(
                draft -> draft.regenerate(draftResult.content(), citedIds, engineName, guardNote),
                () -> draftRepository.save(new AnswerDraft(
                        inquiry, draftResult.content(), citedIds, engineName, guardNote)));

        inquiry.changeStatus(guard.passed() ? InquiryStatus.DRAFTED : InquiryStatus.NEEDS_REVIEW);
    }

    @Transactional
    public void markFailed(Long inquiryId) {
        inquiryRepository.findById(inquiryId)
                .ifPresent(inquiry -> inquiry.changeStatus(InquiryStatus.FAILED));
    }
}
