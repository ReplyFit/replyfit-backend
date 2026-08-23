package co.replyfit.inquiry;

import java.util.ArrayList;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.replyfit.common.ApiException;
import co.replyfit.draft.AnswerDraft;
import co.replyfit.draft.AnswerDraftRepository;
import co.replyfit.inquiry.dto.InquiryDtos.DraftView;
import co.replyfit.inquiry.dto.InquiryDtos.InquiryDetail;
import co.replyfit.inquiry.dto.InquiryDtos.InquiryListItem;
import co.replyfit.inquiry.dto.InquiryDtos.PageResponse;
import co.replyfit.policy.Policy;
import co.replyfit.policy.PolicyRepository;

@Service
public class InquiryService {

    private final InquiryRepository inquiryRepository;
    private final AnswerDraftRepository draftRepository;
    private final PolicyRepository policyRepository;

    public InquiryService(InquiryRepository inquiryRepository,
                          AnswerDraftRepository draftRepository,
                          PolicyRepository policyRepository) {
        this.inquiryRepository = inquiryRepository;
        this.draftRepository = draftRepository;
        this.policyRepository = policyRepository;
    }

    @Transactional(readOnly = true)
    public PageResponse<InquiryListItem> search(Long userId, String category, String status,
                                                String q, int page, int size) {
        InquiryCategory categoryFilter = parseEnum(category, InquiryCategory.class);
        InquiryStatus statusFilter = parseEnum(status, InquiryStatus.class);
        String query = (q == null || q.isBlank()) ? null : q.trim();
        Page<Inquiry> result = inquiryRepository.search(userId, categoryFilter, statusFilter, query,
                PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 100)));
        return new PageResponse<>(
                result.getContent().stream().map(InquiryListItem::from).toList(),
                result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public InquiryDetail getDetail(Long userId, Long inquiryId) {
        Inquiry inquiry = getOwned(userId, inquiryId);
        DraftView draftView = draftRepository.findByInquiryId(inquiryId)
                .map(draft -> DraftView.from(draft, loadCitedPolicies(draft)))
                .orElse(null);
        return InquiryDetail.from(inquiry, draftView);
    }

    @Transactional(readOnly = true)
    public Inquiry getOwned(Long userId, Long inquiryId) {
        Inquiry inquiry = inquiryRepository.findById(inquiryId)
                .orElseThrow(() -> ApiException.notFound("문의를 찾을 수 없습니다."));
        if (!inquiry.getUser().getId().equals(userId)) {
            throw ApiException.forbidden("접근 권한이 없습니다.");
        }
        return inquiry;
    }

    private List<Policy> loadCitedPolicies(AnswerDraft draft) {
        if (draft.getCitedPolicyIds() == null || draft.getCitedPolicyIds().isBlank()) {
            return List.of();
        }
        List<Long> ids = new ArrayList<>();
        for (String part : draft.getCitedPolicyIds().split(",")) {
            try {
                ids.add(Long.valueOf(part.trim()));
            } catch (NumberFormatException ignored) {
                // 잘못된 항목은 건너뜀
            }
        }
        return policyRepository.findAllById(ids);
    }

    private static <E extends Enum<E>> E parseEnum(String value, Class<E> type) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Enum.valueOf(type, value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
