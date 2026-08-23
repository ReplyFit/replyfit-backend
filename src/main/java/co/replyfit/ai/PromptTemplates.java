package co.replyfit.ai;

import java.util.stream.Collectors;

import co.replyfit.ai.LlmClient.DraftContext;
import co.replyfit.inquiry.InquiryCategory;

/**
 * 프롬프트 템플릿 (AI 계층 구성 요소).
 */
public final class PromptTemplates {

    private PromptTemplates() {
    }

    public static final String CLASSIFY_SYSTEM = """
            당신은 의류·잡화 온라인 쇼핑몰의 고객 문의를 분류하는 전문가입니다.
            문의 내용을 읽고 아래 카테고리 중 정확히 하나로 분류하세요.

            - SIZE: 사이즈, 핏, 기장, 치수, 키/몸무게 대비 추천 등
            - SHIPPING: 배송 일정, 출고, 송장, 배송 지연 등
            - EXCHANGE_RETURN: 교환, 반품, 환불, 주문 취소 등
            - COLOR: 색상 차이, 실물 색감, 색상 추천 등
            - RESTOCK: 재입고 일정, 품절 상품 문의 등
            - OTHER: 위에 해당하지 않는 문의

            반드시 아래 JSON 형식으로만 답하세요. 다른 텍스트를 포함하지 마세요.
            {"category": "SIZE", "confidence": 0.95}""";

    public static String classifyUser(String productName, String content) {
        return "상품명: " + (productName == null ? "(미상)" : productName) + "\n문의 내용: " + content;
    }

    public static final String DRAFT_SYSTEM = """
            당신은 의류·잡화 온라인 셀러의 CS 답변 초안을 작성하는 도우미입니다.
            리플핏 서비스의 원칙을 반드시 지키세요.

            [원칙]
            1. 답변에서 배송기간·반품기한·비용 등 구체적 수치나 조건은 아래 "등록된 스토어 정책"에 \
               적힌 문구에서만 인용합니다. 정책에 없는 수치·기한·약속을 절대 만들어내지 마세요.
            2. 관련 정책이 등록되어 있지 않으면 "정확한 기준은 스토어 정책 확인 후 안내드리겠습니다"라고 쓰고, \
               셀러가 확인해야 할 부분임을 [셀러 확인 필요] 표시로 남기세요.
            3. 법정 안내 문구가 주어진 경우, 판매자 정책과 구분해 그대로 포함하세요.
            4. 존댓말로 친절하고 간결하게, 4~8문장 이내로 작성하세요.
            5. 고객 이름은 이미 마스킹되어 있습니다. 마스킹된 형태 그대로 사용하세요.
            6. 이 초안은 셀러가 검토·승인 후 직접 발송합니다. AI가 발송하지 않습니다.

            반드시 아래 JSON 형식으로만 답하세요.
            {"draft": "안녕하세요 ...", "citedPolicyIds": [1, 2]}""";

    public static String draftUser(DraftContext ctx) {
        String policies = ctx.policies().isEmpty()
                ? "(등록된 정책 없음)"
                : ctx.policies().stream()
                        .map(p -> "- [정책 ID %d | %s] %s\n%s".formatted(p.id(), p.typeLabel(), p.title(), p.content()))
                        .collect(Collectors.joining("\n\n"));
        StringBuilder sb = new StringBuilder();
        sb.append("[스토어] ").append(ctx.storeName()).append('\n');
        sb.append("[문의 카테고리] ").append(ctx.category() == null ? "미분류" : ctx.category().getLabel()).append('\n');
        sb.append("[고객명(마스킹됨)] ").append(ctx.customerName()).append('\n');
        sb.append("[상품명] ").append(ctx.productName() == null ? "(미상)" : ctx.productName()).append('\n');
        sb.append("[문의 내용]\n").append(ctx.inquiryContent()).append("\n\n");
        sb.append("[등록된 스토어 정책]\n").append(policies).append("\n\n");
        if (ctx.legalNotice() != null && !ctx.legalNotice().isBlank()) {
            sb.append("[법정 안내 문구 — 판매자 정책과 분리해 포함할 것]\n").append(ctx.legalNotice()).append('\n');
        }
        return sb.toString();
    }

    public static final String REPORT_SYSTEM = """
            당신은 의류·잡화 온라인 셀러의 VOC(고객의 소리) 분석 컨설턴트입니다.
            주어진 주간 집계 데이터(JSON)를 바탕으로 셀러가 바로 실행할 수 있는 개선 액션을 제안하세요.

            [작성 규칙]
            1. "이번 주 요약" 2~3문장으로 시작하세요.
            2. 개선 액션 3~5개를 번호 목록으로 제시하세요. 각 액션은 근거 데이터(건수·비율)를 함께 적으세요.
            3. 반품·불만이 집중된 상품이 있으면, 상세페이지에 추가할 구체적 문구를 따옴표로 제안하세요.
               (예: "허리는 정사이즈, 기장은 한 치수 크게 권장드립니다")
            4. 데이터에 없는 수치를 만들어내지 마세요.
            5. 전체를 한국어 마크다운으로 작성하세요.""";

    public static String reportUser(String aggregateJson) {
        return "이번 주 집계 데이터:\n```json\n" + aggregateJson + "\n```";
    }

    /** 카테고리 → 인용 우선순위가 높은 정책 유형 매핑에 쓰이는 안내 */
    public static boolean needsLegalNotice(InquiryCategory category) {
        return category == InquiryCategory.EXCHANGE_RETURN;
    }
}
