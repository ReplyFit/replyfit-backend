package co.replyfit.inquiry.dto;

import java.time.LocalDateTime;
import java.util.List;

import co.replyfit.draft.AnswerDraft;
import co.replyfit.inquiry.Inquiry;
import co.replyfit.inquiry.InquiryCategory;
import co.replyfit.inquiry.InquiryStatus;
import co.replyfit.policy.Policy;

public final class InquiryDtos {

    private InquiryDtos() {
    }

    public record InquiryListItem(
            Long id,
            String channel,
            String customerName,
            String productName,
            String contentPreview,
            String category,
            String categoryLabel,
            String status,
            String statusLabel,
            LocalDateTime receivedAt) {

        public static InquiryListItem from(Inquiry inquiry) {
            String content = inquiry.getContent();
            String preview = content.length() > 80 ? content.substring(0, 80) + "…" : content;
            InquiryCategory category = inquiry.getCategory();
            InquiryStatus status = inquiry.getStatus();
            return new InquiryListItem(
                    inquiry.getId(),
                    inquiry.getChannel(),
                    inquiry.getCustomerName(),
                    inquiry.getProductName(),
                    preview,
                    category == null ? null : category.name(),
                    category == null ? "분류중" : category.getLabel(),
                    status.name(),
                    status.getLabel(),
                    inquiry.getReceivedAt());
        }
    }

    public record CitedPolicy(Long id, String type, String typeLabel, String title, String content) {
        public static CitedPolicy from(Policy policy) {
            return new CitedPolicy(policy.getId(), policy.getType().name(),
                    policy.getType().getLabel(), policy.getTitle(), policy.getContent());
        }
    }

    public record DraftView(
            Long id,
            String content,
            String aiContent,
            String generatedBy,
            String guardNote,
            List<CitedPolicy> citedPolicies,
            LocalDateTime updatedAt,
            LocalDateTime approvedAt,
            LocalDateTime sentAt) {

        public static DraftView from(AnswerDraft draft, List<Policy> citedPolicies) {
            return new DraftView(
                    draft.getId(),
                    draft.getContent(),
                    draft.getAiContent(),
                    draft.getGeneratedBy(),
                    draft.getGuardNote(),
                    citedPolicies.stream().map(CitedPolicy::from).toList(),
                    draft.getUpdatedAt(),
                    draft.getApprovedAt(),
                    draft.getSentAt());
        }
    }

    public record InquiryDetail(
            Long id,
            String channel,
            String customerName,
            String orderNo,
            String productName,
            String content,
            String category,
            String categoryLabel,
            Double categoryConfidence,
            String status,
            String statusLabel,
            int piiMaskedCount,
            LocalDateTime receivedAt,
            DraftView draft) {

        public static InquiryDetail from(Inquiry inquiry, DraftView draft) {
            InquiryCategory category = inquiry.getCategory();
            InquiryStatus status = inquiry.getStatus();
            return new InquiryDetail(
                    inquiry.getId(),
                    inquiry.getChannel(),
                    inquiry.getCustomerName(),
                    inquiry.getOrderNo(),
                    inquiry.getProductName(),
                    inquiry.getContent(),
                    category == null ? null : category.name(),
                    category == null ? "분류중" : category.getLabel(),
                    inquiry.getCategoryConfidence(),
                    status.name(),
                    status.getLabel(),
                    inquiry.getPiiMaskedCount(),
                    inquiry.getReceivedAt(),
                    draft);
        }
    }

    public record PageResponse<T>(List<T> items, int page, int size, long totalElements, int totalPages) {
    }
}
