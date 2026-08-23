package co.replyfit.report;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;

import co.replyfit.ai.LlmClient;
import co.replyfit.common.ApiException;
import co.replyfit.inquiry.Inquiry;
import co.replyfit.inquiry.InquiryCategory;
import co.replyfit.inquiry.InquiryRepository;
import co.replyfit.review.Review;
import co.replyfit.review.ReviewRepository;
import co.replyfit.review.Sentiment;
import co.replyfit.user.User;
import co.replyfit.user.UserRepository;

/**
 * 주간 VOC 리포트 생성 (MVP 핵심 기능 ④).
 *
 * 문의와 리뷰를 함께 분석해 반품 사유 TOP5, 문제 상품,
 * 수정할 상세페이지 문구까지 제시한다.
 */
@Service
public class ReportService {

    private static final Logger log = LoggerFactory.getLogger(ReportService.class);

    private final WeeklyReportRepository reportRepository;
    private final InquiryRepository inquiryRepository;
    private final ReviewRepository reviewRepository;
    private final UserRepository userRepository;
    private final LlmClient llmClient;
    private final ObjectMapper objectMapper;

    public ReportService(WeeklyReportRepository reportRepository,
                         InquiryRepository inquiryRepository,
                         ReviewRepository reviewRepository,
                         UserRepository userRepository,
                         LlmClient llmClient,
                         ObjectMapper objectMapper) {
        this.reportRepository = reportRepository;
        this.inquiryRepository = inquiryRepository;
        this.reviewRepository = reviewRepository;
        this.userRepository = userRepository;
        this.llmClient = llmClient;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public WeeklyReport createPending(Long userId, LocalDate weekStart) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("사용자를 찾을 수 없습니다."));
        LocalDate normalizedStart = weekStart.with(java.time.DayOfWeek.MONDAY);
        return reportRepository.findByUserIdAndWeekStart(userId, normalizedStart)
                .map(existing -> {
                    if (existing.getStatus() == WeeklyReport.Status.READY) {
                        return existing;
                    }
                    return existing;
                })
                .orElseGet(() -> reportRepository.save(
                        new WeeklyReport(user, normalizedStart, normalizedStart.plusDays(6))));
    }

    /** 워커(또는 시더)가 호출하는 실제 리포트 생성 로직 */
    @Transactional
    public void generate(Long reportId, String insightsOverride) {
        WeeklyReport report = reportRepository.findById(reportId)
                .orElseThrow(() -> ApiException.notFound("리포트를 찾을 수 없습니다."));
        try {
            Map<String, Object> payload = buildAggregates(
                    report.getUser().getId(), report.getWeekStart(), report.getWeekEnd());
            String aggregateJson = objectMapper.writeValueAsString(payload);
            String insights = insightsOverride != null
                    ? insightsOverride
                    : llmClient.reportInsights(aggregateJson);
            payload.put("aiInsights", insights);
            payload.put("generatedBy", insightsOverride != null ? "seed" : llmClient.name());
            report.complete(objectMapper.writeValueAsString(payload));
        } catch (Exception e) {
            log.error("Report generation failed for reportId={}", reportId, e);
            report.fail();
        }
    }

    Map<String, Object> buildAggregates(Long userId, LocalDate weekStart, LocalDate weekEnd) {
        LocalDateTime from = weekStart.atStartOfDay();
        LocalDateTime to = weekEnd.plusDays(1).atStartOfDay();
        List<Inquiry> inquiries = inquiryRepository.findByUserIdAndReceivedAtBetween(userId, from, to);
        List<Review> reviews = reviewRepository.findByUserIdAndWrittenAtBetween(userId, from, to);

        // 1) 카테고리 분포
        Map<InquiryCategory, Long> byCategory = new LinkedHashMap<>();
        for (Inquiry inquiry : inquiries) {
            InquiryCategory category = inquiry.getCategory() == null
                    ? InquiryCategory.OTHER : inquiry.getCategory();
            byCategory.merge(category, 1L, Long::sum);
        }
        List<Map<String, Object>> categoryDistribution = byCategory.entrySet().stream()
                .sorted(Map.Entry.<InquiryCategory, Long>comparingByValue().reversed())
                .map(entry -> Map.<String, Object>of(
                        "category", entry.getKey().name(),
                        "label", entry.getKey().getLabel(),
                        "count", entry.getValue()))
                .toList();

        // 2) 반품 사유 TOP5 — 교환/반품 문의 + 부정 리뷰를 함께 분석
        Map<String, Long> reasons = new LinkedHashMap<>();
        for (Inquiry inquiry : inquiries) {
            if (inquiry.getCategory() == InquiryCategory.EXCHANGE_RETURN) {
                reasons.merge(returnReasonOf(inquiry.getContent()), 1L, Long::sum);
            }
        }
        for (Review review : reviews) {
            if (review.getSentiment() == Sentiment.NEGATIVE) {
                reasons.merge(returnReasonOf(review.getContent()), 1L, Long::sum);
            }
        }
        List<Map<String, Object>> returnReasonsTop5 = reasons.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(5)
                .map(entry -> Map.<String, Object>of("reason", entry.getKey(), "count", entry.getValue()))
                .toList();

        // 3) 문제 상품 — 부정 피드백(부정 리뷰 + 교환/반품 문의)이 집중된 상품
        Map<String, ProductIssue> products = new LinkedHashMap<>();
        for (Review review : reviews) {
            if (review.getProductName() == null) {
                continue;
            }
            ProductIssue issue = products.computeIfAbsent(review.getProductName(), ProductIssue::new);
            issue.ratingSum += review.getRating();
            issue.ratingCount++;
            if (review.getSentiment() == Sentiment.NEGATIVE) {
                issue.negativeCount++;
                issue.addKeywords(review.getIssueKeywords());
            }
        }
        for (Inquiry inquiry : inquiries) {
            if (inquiry.getProductName() != null
                    && inquiry.getCategory() == InquiryCategory.EXCHANGE_RETURN) {
                ProductIssue issue = products.computeIfAbsent(inquiry.getProductName(), ProductIssue::new);
                issue.negativeCount++;
                issue.addKeywords("교환/반품");
            }
        }
        List<Map<String, Object>> problemProducts = products.values().stream()
                .filter(issue -> issue.negativeCount > 0)
                .sorted(Comparator.comparingLong((ProductIssue issue) -> issue.negativeCount).reversed())
                .limit(3)
                .map(ProductIssue::toMap)
                .toList();

        // 4) 상세페이지 문구 제안 (규칙 기반 초안 — AI 인사이트가 이를 보강)
        List<Map<String, Object>> copySuggestions = new ArrayList<>();
        for (Map<String, Object> product : problemProducts) {
            String topIssue = (String) product.get("topIssue");
            copySuggestions.add(Map.of(
                    "productName", product.get("productName"),
                    "suggestion", suggestionFor(topIssue)));
        }

        long negativeReviews = reviews.stream()
                .filter(review -> review.getSentiment() == Sentiment.NEGATIVE).count();
        double avgRating = reviews.isEmpty() ? 0
                : reviews.stream().mapToInt(Review::getRating).average().orElse(0);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("weekStart", weekStart.toString());
        payload.put("weekEnd", weekEnd.toString());
        payload.put("summary", Map.of(
                "totalInquiries", inquiries.size(),
                "totalReviews", reviews.size(),
                "averageRating", Math.round(avgRating * 10) / 10.0,
                "negativeReviews", negativeReviews,
                "topCategory", categoryDistribution.isEmpty()
                        ? "없음" : categoryDistribution.get(0).get("label")));
        payload.put("categoryDistribution", categoryDistribution);
        payload.put("returnReasonsTop5", returnReasonsTop5);
        payload.put("problemProducts", problemProducts);
        payload.put("copySuggestions", copySuggestions);
        return payload;
    }

    private static String returnReasonOf(String content) {
        if (content == null) {
            return "기타";
        }
        if (content.contains("작") || content.contains("크") || content.contains("사이즈") || content.contains("핏")) {
            return "사이즈가 맞지 않음";
        }
        if (content.contains("색")) {
            return "색상/실물 차이";
        }
        if (content.contains("배송") || content.contains("늦")) {
            return "배송 지연/불만";
        }
        if (content.contains("재질") || content.contains("품질") || content.contains("퀄리티")
                || content.contains("보풀") || content.contains("불량")) {
            return "품질/재질 불만족";
        }
        if (content.contains("변심") || content.contains("취소")) {
            return "단순 변심";
        }
        return "기타";
    }

    private static String suggestionFor(String topIssue) {
        if (topIssue == null) {
            return "상세페이지 상단에 실측 사이즈표와 교환/반품 기준을 명확히 안내해 보세요.";
        }
        return switch (topIssue) {
            case "사이즈" -> "\"평소 55 사이즈라면 M을, 66 사이즈라면 L을 권장드립니다\"처럼 "
                    + "구체적 사이즈 가이드를 상세페이지 상단에 추가해 보세요.";
            case "색상" -> "\"화면 설정에 따라 실물 색상과 차이가 있을 수 있으며, 실측 촬영 컷을 함께 확인해 주세요\" "
                    + "문구와 자연광 실물 사진을 추가해 보세요.";
            case "배송" -> "\"주문 후 평균 N일 내 출고됩니다(주말·공휴일 제외)\" 문구를 구매 버튼 근처에 배치해 보세요.";
            case "품질" -> "소재·두께·안감 정보를 표로 정리하고 세탁 관리 방법을 함께 안내해 보세요.";
            default -> "교환/반품 기준과 절차를 상세페이지 하단에 단계별로 안내해 보세요.";
        };
    }

    private static final class ProductIssue {
        final String productName;
        long negativeCount;
        long ratingSum;
        long ratingCount;
        final Map<String, Long> keywords = new LinkedHashMap<>();

        ProductIssue(String productName) {
            this.productName = productName;
        }

        void addKeywords(String csv) {
            if (csv == null) {
                return;
            }
            for (String keyword : csv.split(",")) {
                if (!keyword.isBlank()) {
                    keywords.merge(keyword.trim(), 1L, Long::sum);
                }
            }
        }

        Map<String, Object> toMap() {
            String topIssue = keywords.entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey)
                    .orElse("기타");
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("productName", productName);
            map.put("negativeCount", negativeCount);
            map.put("averageRating", ratingCount == 0 ? null
                    : Math.round((double) ratingSum / ratingCount * 10) / 10.0);
            map.put("topIssue", topIssue);
            return map;
        }
    }
}
