package co.replyfit.common;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 개인정보 마스킹 유틸리티.
 *
 * 사업계획서의 처리 흐름 2단계 — 문의·리뷰 업로드 즉시 이름·주문번호·연락처를
 * 마스킹하며, 원문은 어디에도 저장하지 않는다. (수집 최소화 원칙)
 */
public final class PiiMasker {

    private static final Pattern PHONE = Pattern.compile("01[016789][-.\\s]?\\d{3,4}[-.\\s]?\\d{4}");
    private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    // 8자리 이상 숫자(하이픈 포함 가능)는 주문번호로 간주
    private static final Pattern ORDER_NO = Pattern.compile("\\d{4,}[-]?\\d{4,}[-]?\\d{0,6}");

    private PiiMasker() {
    }

    public record MaskResult(String masked, int maskedCount) {
    }

    /** 홍길동 → 홍*동, 김철 → 김*, 이 → * */
    public static String maskName(String name) {
        if (name == null || name.isBlank()) {
            return "";
        }
        String trimmed = name.trim();
        if (trimmed.length() == 1) {
            return "*";
        }
        if (trimmed.length() == 2) {
            return trimmed.charAt(0) + "*";
        }
        return trimmed.charAt(0) + "*".repeat(trimmed.length() - 2) + trimmed.charAt(trimmed.length() - 1);
    }

    /** 20260812-0034567 → ****-**34567 (끝 5자리만 노출) */
    public static String maskOrderNo(String orderNo) {
        if (orderNo == null || orderNo.isBlank()) {
            return "";
        }
        String trimmed = orderNo.trim();
        int visible = Math.min(5, trimmed.length());
        return "****-**" + trimmed.substring(trimmed.length() - visible);
    }

    /** 본문 내 전화번호·이메일·주문번호 패턴 및 고객 이름을 마스킹한다. */
    public static MaskResult maskContent(String content, String customerName) {
        if (content == null || content.isBlank()) {
            return new MaskResult("", 0);
        }
        int count = 0;
        String result = content;

        Matcher phone = PHONE.matcher(result);
        StringBuilder sb = new StringBuilder();
        while (phone.find()) {
            count++;
            phone.appendReplacement(sb, "010-****-****");
        }
        phone.appendTail(sb);
        result = sb.toString();

        Matcher email = EMAIL.matcher(result);
        sb = new StringBuilder();
        while (email.find()) {
            count++;
            String found = email.group();
            String domain = found.substring(found.indexOf('@'));
            email.appendReplacement(sb, Matcher.quoteReplacement(found.charAt(0) + "***" + domain));
        }
        email.appendTail(sb);
        result = sb.toString();

        Matcher order = ORDER_NO.matcher(result);
        sb = new StringBuilder();
        while (order.find()) {
            count++;
            order.appendReplacement(sb, "****(주문번호)");
        }
        order.appendTail(sb);
        result = sb.toString();

        if (customerName != null && customerName.trim().length() >= 2 && result.contains(customerName.trim())) {
            result = result.replace(customerName.trim(), maskName(customerName));
            count++;
        }
        return new MaskResult(result, count);
    }
}
