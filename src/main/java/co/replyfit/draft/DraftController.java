package co.replyfit.draft;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import co.replyfit.auth.AuthUser;
import co.replyfit.common.ApiException;
import co.replyfit.dashboard.DashboardCache;
import co.replyfit.inquiry.InquiryStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

/**
 * 답변 초안 승인 흐름 — "AI 초안 + 셀러 승인 발송" (5단계 처리 흐름의 마지막 단계).
 * AI는 직접 발송하지 않으며, 셀러가 승인 → 복사 → 판매 채널에 직접 발송한다.
 */
@RestController
@RequestMapping("/api/drafts")
@Tag(name = "06. 답변 초안", description = "AI 초안의 셀러 승인 흐름 — 수정 → 승인 → 복사 후 판매 채널에서 직접 발송 → 발송 완료 표시. AI는 직접 발송하지 않습니다.")
public class DraftController {

    public record EditRequest(@NotBlank String content) {
    }

    public record DraftActionResponse(Long draftId, String inquiryStatus, String message) {
    }

    private final AnswerDraftRepository draftRepository;
    private final DashboardCache dashboardCache;

    public DraftController(AnswerDraftRepository draftRepository, DashboardCache dashboardCache) {
        this.draftRepository = draftRepository;
        this.dashboardCache = dashboardCache;
    }

    @Operation(summary = "초안 수정", description = "셀러가 내용을 고칩니다. AI 원본(aiContent)은 그대로 보존됩니다.")
    @PutMapping("/{id}")
    @Transactional
    public ResponseEntity<DraftActionResponse> edit(@AuthenticationPrincipal AuthUser me,
                                                    @PathVariable Long id,
                                                    @Valid @RequestBody EditRequest request) {
        AnswerDraft draft = getOwned(me.id(), id);
        draft.editContent(request.content());
        return ResponseEntity.ok(new DraftActionResponse(
                draft.getId(), draft.getInquiry().getStatus().name(), "초안이 수정되었습니다."));
    }

    @Operation(summary = "초안 승인", description = "문의 상태가 APPROVED로 전환됩니다. 이후 복사해서 판매 채널에 발송하세요.")
    @PostMapping("/{id}/approve")
    @Transactional
    public ResponseEntity<DraftActionResponse> approve(@AuthenticationPrincipal AuthUser me,
                                                       @PathVariable Long id) {
        AnswerDraft draft = getOwned(me.id(), id);
        draft.approve();
        draft.getInquiry().changeStatus(InquiryStatus.APPROVED);
        dashboardCache.evict(me.id());
        return ResponseEntity.ok(new DraftActionResponse(
                draft.getId(), InquiryStatus.APPROVED.name(),
                "초안이 승인되었습니다. 복사해서 판매 채널에 발송해 주세요."));
    }

    @Operation(summary = "발송 완료 표시", description = "승인된 초안만 가능합니다(미승인 시 400). 문의 상태가 SENT로 전환됩니다.")
    @PostMapping("/{id}/mark-sent")
    @Transactional
    public ResponseEntity<DraftActionResponse> markSent(@AuthenticationPrincipal AuthUser me,
                                                        @PathVariable Long id) {
        AnswerDraft draft = getOwned(me.id(), id);
        if (draft.getApprovedAt() == null) {
            throw ApiException.badRequest("먼저 초안을 승인해 주세요.");
        }
        draft.markSent();
        draft.getInquiry().changeStatus(InquiryStatus.SENT);
        dashboardCache.evict(me.id());
        return ResponseEntity.ok(new DraftActionResponse(
                draft.getId(), InquiryStatus.SENT.name(), "발송 완료로 표시했습니다."));
    }

    private AnswerDraft getOwned(Long userId, Long draftId) {
        AnswerDraft draft = draftRepository.findById(draftId)
                .orElseThrow(() -> ApiException.notFound("초안을 찾을 수 없습니다."));
        if (!draft.getInquiry().getUser().getId().equals(userId)) {
            throw ApiException.forbidden("접근 권한이 없습니다.");
        }
        return draft;
    }
}
