package co.replyfit.ai;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import co.replyfit.ai.LlmClient.DraftContext;

/**
 * 정책 검증 로직 (AI 계층 구성 요소).
 *
 * 초안에 등장하는 수치성 표현(N일, N%, N원 등)이 등록된 정책·법정 안내·문의 원문에
 * 실제로 존재하는지 검사한다. 출처 없는 수치가 발견되면 경고를 남기고
 * 해당 문의를 "검토 필요" 상태로 돌려 환각·오안내를 차단한다.
 */
public final class PolicyGuard {

    private static final Pattern NUMERIC_CLAIM =
            Pattern.compile("\\d+(?:~\\d+)?\\s*(?:영업일|일 이내|일이내|일|시간|주일|개월|%|퍼센트|원|만원|천원)");

    private PolicyGuard() {
    }

    public record GuardResult(boolean passed, String note) {
    }

    public static GuardResult verify(String draft, DraftContext context) {
        if (draft == null || draft.isBlank()) {
            return new GuardResult(false, "초안이 비어 있습니다.");
        }
        StringBuilder corpus = new StringBuilder();
        context.policies().forEach(p -> corpus.append(p.content()).append('\n').append(p.title()).append('\n'));
        if (context.legalNotice() != null) {
            corpus.append(context.legalNotice()).append('\n');
        }
        corpus.append(context.inquiryContent());
        String source = normalize(corpus.toString());

        Set<String> unknownClaims = new LinkedHashSet<>();
        Matcher matcher = NUMERIC_CLAIM.matcher(draft);
        while (matcher.find()) {
            String claim = normalize(matcher.group());
            if (!source.contains(claim)) {
                unknownClaims.add(matcher.group().trim());
            }
        }
        if (unknownClaims.isEmpty()) {
            return new GuardResult(true, null);
        }
        List<String> list = new ArrayList<>(unknownClaims);
        return new GuardResult(false,
                "등록된 정책·법정 안내에서 확인되지 않는 수치 표현이 포함되어 있습니다: "
                        + String.join(", ", list)
                        + " — 발송 전 셀러 확인이 필요합니다.");
    }

    private static String normalize(String text) {
        return text.replaceAll("\\s+", "");
    }
}
