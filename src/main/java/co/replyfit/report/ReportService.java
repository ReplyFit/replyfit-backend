package co.replyfit.report;

import java.time.LocalDate;
import java.time.LocalDateTime;
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
 *
 * 집계 결과는 프론트가 소비하는 JSON 계약이므로 레코드로 표현한다
 * (Jackson이 컴포넌트명 그대로 직렬화 — 키 오타가 컴파일에 잡힌다).
 */
@Service
public class ReportService {

    private static final Logger log = LoggerFactory.getLogger(ReportService.class);

    /** 문제 상품·문구 제안에 노출할 상품 수 */
    private static final int PROBLEM_PRODUCT_LIMIT = 3;
    /** 반품 사유 노출 개수 */
    private static final int RETURN_REASON_LIMIT = 5;

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

    /* ---------- 프론트가 소비하는 payload 계약 ---------- */

    public record CategorySlice(String category, String label, long count) {
    }

    public record ReturnReason(String reason, long count) {
    }

    /** averageRating은 리뷰가 없는 상품에서 null (프론트 계약: number | null) */
    public record ProblemProduct(String productName, long negativeCount,
                                 Double averageRating, String topIssue) {
    }

    public record CopySuggestion(String productName, String suggestion) {
    }

    public record Summary(int totalInquiries, int totalReviews, double averageRating,
                          long negativeReviews, String topCategory) {
    }

    public record Aggregates(String weekStart, String weekEnd, Summary summary,
                             List<CategorySlice> categoryDistribution,
                             List<ReturnReason> returnReasonsTop5,
                             List<ProblemProduct> problemProducts,
                             List<CopySuggestion> copySuggestions) {
    }

    /** 집계에 AI 인사이트를 얹은 최종 저장 형태 */
    public record Payload(String weekStart, String weekEnd, Summary summary,
                          List<CategorySlice> categoryDistribution,
                          List<ReturnReason> returnReasonsTop5,
                          List<ProblemProduct> problemProducts,
                          List<CopySuggestion> copySuggestions,
                          String aiInsights, String generatedBy) {

        static Payload of(Aggregates a, String aiInsights, String generatedBy) {
            return new Payload(a.weekStart(), a.weekEnd(), a.summary(),
                    a.categoryDistribution(), a.returnReasonsTop5(),
                    a.problemProducts(), a.copySuggestions(), aiInsights, generatedBy);
        }
    }

    /* ---------- 공개 API ---------- */

    @Transactional
    public WeeklyReport createPending(Long userId, LocalDate weekStart) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("사용자를 찾을 수 없습니다."));
        LocalDate normalizedStart = weekStart.with(java.time.DayOfWeek.MONDAY);
        return reportRepository.findByUserIdAndWeekStart(userId, normalizedStart)
                .orElseGet(() -> reportRepository.save(
                        new WeeklyReport(user, normalizedStart, normalizedStart.plusDays(6))));
    }

    /** 워커(또는 시더)가 호출하는 실제 리포트 생성 로직 */
    @Transactional
    public void generate(Long reportId, String insightsOverride) {
        WeeklyReport report = reportRepository.findById(reportId)
                .orElseThrow(() -> ApiException.notFound("리포트를 찾을 수 없습니다."));
        try {
            Aggregates aggregates = buildAggregates(
                    report.getUser().getId(), report.getWeekStart(), report.getWeekEnd());
            String insights = insightsOverride != null
                    ? insightsOverride
                    : llmClient.reportInsights(objectMapper.writeValueAsString(aggregates));
            String generatedBy = insightsOverride != null ? "seed" : llmClient.name();
            report.complete(objectMapper.writeValueAsString(
                    Payload.of(aggregates, insights, generatedBy)));
        } catch (Exception e) {
            log.error("Report generation failed for reportId={}", reportId, e);
            report.fail();
        }
    }

    /* ---------- 집계 ---------- */

    Aggregates buildAggregates(Long userId, LocalDate weekStart, LocalDate weekEnd) {
        LocalDateTime from = weekStart.atStartOfDay();
        LocalDateTime to = weekEnd.plusDays(1).atStartOfDay();
        List<Inquiry> inquiries = inquiryRepository.findByUserIdAndReceivedAtBetween(userId, from, to);
        List<Review> reviews = reviewRepository.findByUserIdAndWrittenAtBetween(userId, from, to);

        List<CategorySlice> categories = categoryDistribution(inquiries);
        List<ProblemProduct> products = problemProducts(inquiries, reviews);

        return new Aggregates(
                weekStart.toString(),
                weekEnd.toString(),
                summary(inquiries, reviews, categories),
                categories,
                returnReasonsTop5(inquiries, reviews),
                products,
                copySuggestions(products));
    }

    /** 카테고리별 문의 건수 — 많은 순 */
    private static List<CategorySlice> categoryDistribution(List<Inquiry> inquiries) {
        Map<InquiryCategory, Long> counts = new LinkedHashMap<>();
        for (Inquiry inquiry : inquiries) {
            InquiryCategory category = inquiry.getCategory() == null
                    ? InquiryCategory.OTHER : inquiry.getCategory();
            counts.merge(category, 1L, Long::sum);
        }
        return counts.entrySet().stream()
                .sorted(Map.Entry.<InquiryCategory, Long>comparingByValue().reversed())
                .map(entry -> new CategorySlice(
                        entry.getKey().name(), entry.getKey().getLabel(), entry.getValue()))
                .toList();
    }

    /** 반품 사유 TOP5 — 교환/반품 문의와 부정 리뷰를 함께 분석 */
    private static List<ReturnReason> returnReasonsTop5(List<Inquiry> inquiries, List<Review> reviews) {
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
        return reasons.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(RETURN_REASON_LIMIT)
                .map(entry -> new ReturnReason(entry.getKey(), entry.getValue()))
                .toList();
    }

    /** 부정 피드백(부정 리뷰 + 교환/반품 문의)이 집중된 상품 */
    private static List<ProblemProduct> problemProducts(List<Inquiry> inquiries, List<Review> reviews) {
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
        return products.values().stream()
                .filter(issue -> issue.negativeCount > 0)
                .sorted(Comparator.comparingLong((ProductIssue issue) -> issue.negativeCount).reversed())
                .limit(PROBLEM_PRODUCT_LIMIT)
                .map(ProductIssue::toRecord)
                .toList();
    }

    /** 문제 상품마다 대표 이슈에 맞는 상세페이지 문구 제안 (AI 인사이트가 이를 보강) */
    private static List<CopySuggestion> copySuggestions(List<ProblemProduct> products) {
        return products.stream()
                .map(product -> new CopySuggestion(
                        product.productName(), suggestionFor(product.topIssue())))
                .toList();
    }

    private static Summary summary(List<Inquiry> inquiries, List<Review> reviews,
                                   List<CategorySlice> categories) {
        long negativeReviews = reviews.stream()
                .filter(review -> review.getSentiment() == Sentiment.NEGATIVE).count();
        double avgRating = reviews.isEmpty() ? 0
                : reviews.stream().mapToInt(Review::getRating).average().orElse(0);
        return new Summary(
                inquiries.size(),
                reviews.size(),
                roundToTenth(avgRating),
                negativeReviews,
                categories.isEmpty() ? "없음" : categories.get(0).label());
    }

    private static double roundToTenth(double value) {
        return Math.round(value * 10) / 10.0;
    }

    /* ---------- 규칙 기반 분류 ---------- */

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

    /** 집계 중 상품별 누적 상태 — 완성되면 {@link ProblemProduct}로 변환된다 */
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

        ProblemProduct toRecord() {
            String topIssue = keywords.entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey)
                    .orElse("기타");
            Double averageRating = ratingCount == 0 ? null
                    : roundToTenth((double) ratingSum / ratingCount);
            return new ProblemProduct(productName, negativeCount, averageRating, topIssue);
        }
    }
}
