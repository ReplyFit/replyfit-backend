package co.replyfit.ai;

import java.util.List;

import co.replyfit.inquiry.InquiryCategory;

/**
 * AI 계층 추상화 — LLM API 호출 모듈 + 프롬프트 템플릿 + 정책 검증 로직 (사업계획서 기술 구성 3).
 *
 * 구현체:
 *  - {@link AnthropicLlmClient}: Anthropic Claude API 호출 (ANTHROPIC_API_KEY 필요)
 *  - {@link RuleBasedLlmClient}: 키워드/템플릿 기반 폴백 (API 키 없이 동작, 데모·개발용)
 */
public interface LlmClient {

    record ClassificationResult(InquiryCategory category, double confidence) {
    }

    record PolicyRef(Long id, String typeLabel, String title, String content) {
    }

    record DraftContext(
            String storeName,
            String customerName,
            String productName,
            String channel,
            String inquiryContent,
            InquiryCategory category,
            List<PolicyRef> policies,
            String legalNotice) {
    }

    record DraftResult(String content, List<Long> citedPolicyIds) {
    }

    /** 문의 유형 자동분류 (MVP 핵심 기능 ②) */
    ClassificationResult classify(String productName, String content);

    /** 정책 기반 답변 초안 생성 (MVP 핵심 기능 ③) — 등록된 정책 문구만 인용 */
    DraftResult generateDraft(DraftContext context);

    /** 주간 VOC 리포트 개선 인사이트 생성 (MVP 핵심 기능 ④) */
    String reportInsights(String aggregateJson);

    /** 엔진 식별자 (초안 감사 추적용) */
    String name();
}
