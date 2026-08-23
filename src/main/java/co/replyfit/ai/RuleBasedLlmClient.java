package co.replyfit.ai;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import co.replyfit.inquiry.InquiryCategory;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 키워드·템플릿 기반 폴백 엔진.
 *
 * ANTHROPIC_API_KEY 없이도 전체 파이프라인(분류 → 초안 → 리포트)이 동작하도록 하는
 * 개발·데모용 구현이자, LLM API 장애·거부 시의 안전 폴백이다.
 */
public class RuleBasedLlmClient implements LlmClient {

    private static final Map<InquiryCategory, List<String>> KEYWORDS = new LinkedHashMap<>();

    static {
        KEYWORDS.put(InquiryCategory.EXCHANGE_RETURN,
                List.of("교환", "반품", "환불", "취소", "반송", "맞교환"));
        KEYWORDS.put(InquiryCategory.SIZE,
                List.of("사이즈", "크기", "핏", "기장", "어깨", "허리", "총장", "치수", "정사이즈", "몸무게", "라지", "스몰"));
        KEYWORDS.put(InquiryCategory.RESTOCK,
                List.of("재입고", "품절", "입고", "재고", "언제 들어"));
        KEYWORDS.put(InquiryCategory.SHIPPING,
                List.of("배송", "출고", "송장", "택배", "언제 와", "언제 도착", "언제쯤", "발송"));
        KEYWORDS.put(InquiryCategory.COLOR,
                List.of("색상", "컬러", "색깔", "색감", "화면과", "실물"));
    }

    @Override
    public ClassificationResult classify(String productName, String content) {
        if (content == null || content.isBlank()) {
            return new ClassificationResult(InquiryCategory.OTHER, 0.3);
        }
        InquiryCategory best = InquiryCategory.OTHER;
        int bestHits = 0;
        for (Map.Entry<InquiryCategory, List<String>> entry : KEYWORDS.entrySet()) {
            int hits = 0;
            for (String keyword : entry.getValue()) {
                if (content.contains(keyword)) {
                    hits++;
                }
            }
            // 교환/반품 의도는 강한 신호 — "색상이 달라 교환 원해요"처럼 다른 카테고리
            // 키워드와 함께 나와도 실제 처리(반품 안내)가 필요한 문의로 우선 분류한다.
            if (entry.getKey() == InquiryCategory.EXCHANGE_RETURN) {
                hits *= 2;
            }
            if (hits > bestHits) {
                bestHits = hits;
                best = entry.getKey();
            }
        }
        double confidence = bestHits == 0 ? 0.4 : Math.min(0.6 + bestHits * 0.1, 0.9);
        return new ClassificationResult(best, confidence);
    }

    @Override
    public DraftResult generateDraft(DraftContext ctx) {
        StringBuilder sb = new StringBuilder();
        List<Long> cited = new ArrayList<>();

        sb.append("안녕하세요 ").append(ctx.customerName()).append(" 고객님, ")
                .append(ctx.storeName()).append("입니다.\n");
        if (ctx.productName() != null && !ctx.productName().isBlank()) {
            sb.append("문의 주신 [").append(ctx.productName()).append("] 관련해 안내드립니다.\n\n");
        } else {
            sb.append("문의 주신 내용 관련해 안내드립니다.\n\n");
        }

        if (ctx.policies().isEmpty()) {
            sb.append("정확한 기준은 스토어 정책 확인 후 안내드리겠습니다. [셀러 확인 필요]\n");
        } else {
            for (PolicyRef policy : ctx.policies()) {
                sb.append("■ ").append(policy.title()).append('\n')
                        .append(policy.content()).append("\n\n");
                cited.add(policy.id());
            }
        }

        if (ctx.legalNotice() != null && !ctx.legalNotice().isBlank()) {
            sb.append(ctx.legalNotice()).append("\n\n");
        }

        sb.append("추가로 궁금하신 점 있으시면 언제든 문의 남겨 주세요. 감사합니다.");
        return new DraftResult(sb.toString(), cited);
    }

    @Override
    public String reportInsights(String aggregateJson) {
        StringBuilder sb = new StringBuilder();
        sb.append("### 이번 주 요약\n");
        sb.append("이번 주 접수된 문의·리뷰 데이터를 기반으로 자동 생성된 요약입니다. ");
        sb.append("반품 사유 상위 항목과 부정 리뷰가 집중된 상품을 우선 점검해 주세요.\n\n");
        sb.append("### 개선 액션\n");
        int index = 1;
        try {
            JsonNode root = new com.fasterxml.jackson.databind.ObjectMapper().readTree(aggregateJson);
            JsonNode reasons = root.path("returnReasonsTop5");
            if (reasons.isArray() && reasons.size() > 0) {
                JsonNode top = reasons.get(0);
                sb.append(index++).append(". 반품 사유 1위 \"").append(top.path("reason").asText())
                        .append("\"(").append(top.path("count").asLong())
                        .append("건) — 해당 사유를 줄일 수 있도록 상세페이지 안내 문구를 보강하세요.\n");
            }
            JsonNode products = root.path("problemProducts");
            if (products.isArray() && products.size() > 0) {
                Iterator<JsonNode> it = products.elements();
                while (it.hasNext() && index <= 4) {
                    JsonNode p = it.next();
                    sb.append(index++).append(". [").append(p.path("productName").asText())
                            .append("] 부정 피드백 ").append(p.path("negativeCount").asLong())
                            .append("건 — 주요 이슈: ").append(p.path("topIssue").asText("기타"))
                            .append(". 상세페이지에 \"실측 사이즈표와 모델 착용 정보\"를 상단에 배치해 보세요.\n");
                }
            }
        } catch (Exception ignored) {
            // 집계 파싱 실패 시 일반 가이드만 제공
        }
        sb.append(index).append(". 자주 반복되는 문의는 스토어 정책(배송·교환/반품·사이즈 가이드)에 등록해 두면 "
                + "AI 초안 품질이 높아집니다.\n");
        return sb.toString();
    }

    @Override
    public String name() {
        return "rule-based";
    }
}
