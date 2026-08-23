package co.replyfit.draft;

import java.time.LocalDateTime;

import co.replyfit.inquiry.Inquiry;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

/**
 * AI가 생성한 답변 초안. AI는 직접 발송하지 않으며 셀러가 승인·발송한다.
 */
@Entity
@Table(name = "answer_drafts")
public class AnswerDraft {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "inquiry_id", unique = true)
    private Inquiry inquiry;

    /** 현재 답변 내용 (셀러가 수정 가능) */
    @Column(nullable = false, columnDefinition = "text")
    private String content;

    /** AI가 생성한 원본 초안 (감사 추적용) */
    @Column(nullable = false, columnDefinition = "text")
    private String aiContent;

    /** 초안에 인용된 정책 ID 목록 (쉼표 구분) */
    @Column(length = 500)
    private String citedPolicyIds;

    /** 초안을 생성한 모델/엔진 이름 */
    @Column(length = 100)
    private String generatedBy;

    /** 정책 검증 로직이 남긴 경고 (정책에 없는 수치 등) */
    @Column(columnDefinition = "text")
    private String guardNote;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    private LocalDateTime approvedAt;

    private LocalDateTime sentAt;

    protected AnswerDraft() {
    }

    public AnswerDraft(Inquiry inquiry, String aiContent, String citedPolicyIds,
                       String generatedBy, String guardNote) {
        this.inquiry = inquiry;
        this.content = aiContent;
        this.aiContent = aiContent;
        this.citedPolicyIds = citedPolicyIds;
        this.generatedBy = generatedBy;
        this.guardNote = guardNote;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    public Long getId() {
        return id;
    }

    public Inquiry getInquiry() {
        return inquiry;
    }

    public String getContent() {
        return content;
    }

    public String getAiContent() {
        return aiContent;
    }

    public String getCitedPolicyIds() {
        return citedPolicyIds;
    }

    public String getGeneratedBy() {
        return generatedBy;
    }

    public String getGuardNote() {
        return guardNote;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public LocalDateTime getApprovedAt() {
        return approvedAt;
    }

    public LocalDateTime getSentAt() {
        return sentAt;
    }

    public void editContent(String content) {
        this.content = content;
        this.updatedAt = LocalDateTime.now();
    }

    public void regenerate(String aiContent, String citedPolicyIds, String generatedBy, String guardNote) {
        this.content = aiContent;
        this.aiContent = aiContent;
        this.citedPolicyIds = citedPolicyIds;
        this.generatedBy = generatedBy;
        this.guardNote = guardNote;
        this.approvedAt = null;
        this.sentAt = null;
        this.updatedAt = LocalDateTime.now();
    }

    public void approve() {
        this.approvedAt = LocalDateTime.now();
        this.updatedAt = this.approvedAt;
    }

    public void markSent() {
        this.sentAt = LocalDateTime.now();
        this.updatedAt = this.sentAt;
    }
}
