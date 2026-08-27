package co.replyfit.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import co.replyfit.ai.LlmClient;
import co.replyfit.inquiry.Inquiry;
import co.replyfit.inquiry.InquiryCategory;
import co.replyfit.inquiry.InquiryRepository;
import co.replyfit.review.Review;
import co.replyfit.review.ReviewRepository;
import co.replyfit.review.Sentiment;
import co.replyfit.user.User;
import co.replyfit.user.UserRepository;

/**
 * 주간 리포트 집계의 특성화 테스트.
 *
 * 프론트가 소비하는 payload는 JSON 계약이므로, 내부 구조를 어떻게 바꾸든
 * 직렬화 결과가 같아야 한다. 그래서 자바 객체가 아니라 **직렬화한 JSON**에
 * 대해 단언한다 — 이 테스트가 통과하는 한 리팩터링은 계약을 깨지 않는다.
 */
class ReportServiceTest {

    private static final LocalDate WEEK_START = LocalDate.of(2026, 8, 24);
    private static final LocalDate WEEK_END = LocalDate.of(2026, 8, 30);
    private static final long USER_ID = 1L;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private WeeklyReportRepository reportRepository;
    private LlmClient llmClient;
    private InquiryRepository inquiryRepository;
    private ReviewRepository reviewRepository;
    private ReportService service;

    @BeforeEach
    void setUp() {
        reportRepository = mock(WeeklyReportRepository.class);
        llmClient = mock(LlmClient.class);
        inquiryRepository = mock(InquiryRepository.class);
        reviewRepository = mock(ReviewRepository.class);
        service = new ReportService(
                reportRepository,
                inquiryRepository,
                reviewRepository,
                mock(UserRepository.class),
                llmClient,
                objectMapper);
    }

    /** 반환 타입이 아니라 <b>직렬화 결과</b>가 계약이므로 var로 받는다. */
    private JsonNode aggregate() throws Exception {
        var payload = service.buildAggregates(USER_ID, WEEK_START, WEEK_END);
        return objectMapper.readTree(objectMapper.writeValueAsString(payload));
    }

    /** 문의 6건 · 리뷰 4건 — 집계 4종을 모두 자극하는 픽스처 */
    private void givenFixture() {
        User user = new User("demo@replyfit.co", "x", "데모", "데모스토어");
        LocalDateTime at = WEEK_START.atTime(10, 0);

        Inquiry size1 = inquiry(user, "린넨 와이드 팬츠", "사이즈가 작아요", at);
        size1.classify(InquiryCategory.SIZE, 0.9);
        Inquiry size2 = inquiry(user, "린넨 와이드 팬츠", "핏이 작네요", at);
        size2.classify(InquiryCategory.SIZE, 0.9);
        Inquiry size3 = inquiry(user, "린넨 와이드 팬츠", "치수가 크게 나왔어요", at);
        size3.classify(InquiryCategory.SIZE, 0.9);

        Inquiry return1 = inquiry(user, "크롭 니트 가디건", "색상이 달라 반품할게요", at);
        return1.classify(InquiryCategory.EXCHANGE_RETURN, 0.9);
        Inquiry return2 = inquiry(user, "크롭 니트 가디건", "배송이 너무 늦어 취소합니다", at);
        return2.classify(InquiryCategory.EXCHANGE_RETURN, 0.9);

        // 상품명 없는 문의 — null 가드 경로
        Inquiry shipping = inquiry(user, null, "언제 배송되나요", at);
        shipping.classify(InquiryCategory.SHIPPING, 0.9);

        when(inquiryRepository.findByUserIdAndReceivedAtBetween(eq(USER_ID), any(), any()))
                .thenReturn(List.of(size1, size2, size3, return1, return2, shipping));

        when(reviewRepository.findByUserIdAndWrittenAtBetween(eq(USER_ID), any(), any()))
                .thenReturn(List.of(
                        new Review(user, "린넨 와이드 팬츠", 2, "사이즈가 너무 작게 나왔어요",
                                Sentiment.NEGATIVE, "사이즈", at),
                        new Review(user, "린넨 와이드 팬츠", 5, "핏이 예뻐요",
                                Sentiment.POSITIVE, null, at),
                        new Review(user, "크롭 니트 가디건", 1, "색상이 사진과 달라요",
                                Sentiment.NEGATIVE, "색상", at),
                        // 상품명 없는 리뷰 — null 가드 경로
                        new Review(user, null, 3, "보통이에요", Sentiment.NEUTRAL, null, at)));
    }

    /** id가 부여된 사용자 — buildAggregates가 getId()로 조회 키를 만든다 */
    private static User persistedUser() {
        User user = mock(User.class);
        when(user.getId()).thenReturn(USER_ID);
        return user;
    }

    private static Inquiry inquiry(User user, String productName, String content, LocalDateTime at) {
        return new Inquiry(user, "네이버", "홍*동", "****-**34567", productName, content, 2, at);
    }

    @Test
    @DisplayName("payload 최상위 키 집합이 프론트 ReportPayload 계약과 일치한다")
    void payloadKeys() throws Exception {
        givenFixture();
        JsonNode json = aggregate();

        assertThat(json.fieldNames()).toIterable().containsExactlyInAnyOrder(
                "weekStart", "weekEnd", "summary",
                "categoryDistribution", "returnReasonsTop5",
                "problemProducts", "copySuggestions");
        assertThat(json.path("weekStart").asText()).isEqualTo("2026-08-24");
        assertThat(json.path("weekEnd").asText()).isEqualTo("2026-08-30");
    }

    @Test
    @DisplayName("summary — 건수·평균평점 반올림·최다 카테고리")
    void summary() throws Exception {
        givenFixture();
        JsonNode summary = aggregate().path("summary");

        assertThat(summary.path("totalInquiries").asInt()).isEqualTo(6);
        assertThat(summary.path("totalReviews").asInt()).isEqualTo(4);
        assertThat(summary.path("negativeReviews").asLong()).isEqualTo(2);
        // (2 + 5 + 1 + 3) / 4 = 2.75 → 소수 첫째 자리 반올림
        assertThat(summary.path("averageRating").asDouble()).isEqualTo(2.8);
        assertThat(summary.path("topCategory").asText()).isEqualTo("사이즈");
    }

    @Test
    @DisplayName("categoryDistribution — 건수 내림차순, 라벨 포함")
    void categoryDistribution() throws Exception {
        givenFixture();
        JsonNode slices = aggregate().path("categoryDistribution");

        assertThat(slices).hasSize(3);
        assertThat(slices.get(0).path("category").asText()).isEqualTo("SIZE");
        assertThat(slices.get(0).path("label").asText()).isEqualTo("사이즈");
        assertThat(slices.get(0).path("count").asLong()).isEqualTo(3);
        assertThat(slices.get(1).path("category").asText()).isEqualTo("EXCHANGE_RETURN");
        assertThat(slices.get(1).path("count").asLong()).isEqualTo(2);
        assertThat(slices.get(2).path("category").asText()).isEqualTo("SHIPPING");
        assertThat(slices.get(2).path("count").asLong()).isEqualTo(1);
    }

    @Test
    @DisplayName("returnReasonsTop5 — 교환/반품 문의와 부정 리뷰를 함께 집계")
    void returnReasons() throws Exception {
        givenFixture();
        JsonNode reasons = aggregate().path("returnReasonsTop5");

        // 색상/실물 차이 = 반품 문의 1 + 부정 리뷰 1
        assertThat(reasons.get(0).path("reason").asText()).isEqualTo("색상/실물 차이");
        assertThat(reasons.get(0).path("count").asLong()).isEqualTo(2);
        assertThat(reasons).hasSize(3);
        assertThat(reasons.findValuesAsText("reason"))
                .containsExactly("색상/실물 차이", "배송 지연/불만", "사이즈가 맞지 않음");
    }

    @Test
    @DisplayName("problemProducts — 부정 건수 내림차순, 평균평점 반올림, 대표 이슈")
    void problemProducts() throws Exception {
        givenFixture();
        JsonNode products = aggregate().path("problemProducts");

        assertThat(products).hasSize(2);

        // 크롭 니트: 부정 리뷰 1 + 교환/반품 문의 2 = 3
        JsonNode worst = products.get(0);
        assertThat(worst.path("productName").asText()).isEqualTo("크롭 니트 가디건");
        assertThat(worst.path("negativeCount").asLong()).isEqualTo(3);
        assertThat(worst.path("averageRating").asDouble()).isEqualTo(1.0);
        assertThat(worst.path("topIssue").asText()).isEqualTo("교환/반품");

        // 린넨 팬츠: 부정 리뷰 1건, 평점 (2+5)/2 = 3.5
        JsonNode second = products.get(1);
        assertThat(second.path("productName").asText()).isEqualTo("린넨 와이드 팬츠");
        assertThat(second.path("negativeCount").asLong()).isEqualTo(1);
        assertThat(second.path("averageRating").asDouble()).isEqualTo(3.5);
        assertThat(second.path("topIssue").asText()).isEqualTo("사이즈");
    }

    @Test
    @DisplayName("copySuggestions — 문제 상품마다 대표 이슈에 맞는 문구 제안")
    void copySuggestions() throws Exception {
        givenFixture();
        JsonNode suggestions = aggregate().path("copySuggestions");

        assertThat(suggestions).hasSize(2);
        assertThat(suggestions.findValuesAsText("productName"))
                .containsExactly("크롭 니트 가디건", "린넨 와이드 팬츠");
        // 사이즈 이슈에는 사이즈 가이드 문구가 나가야 한다
        assertThat(suggestions.get(1).path("suggestion").asText()).contains("사이즈 가이드");
    }

    @Test
    @DisplayName("generate — 저장되는 최종 payload에 집계 + aiInsights + generatedBy가 모두 담긴다")
    void generatePayload() throws Exception {
        givenFixture();
        WeeklyReport report = new WeeklyReport(persistedUser(), WEEK_START, WEEK_END);
        when(reportRepository.findById(7L)).thenReturn(Optional.of(report));

        service.generate(7L, "시드 인사이트");

        assertThat(report.getStatus()).isEqualTo(WeeklyReport.Status.READY);
        JsonNode json = objectMapper.readTree(report.getPayload());
        assertThat(json.fieldNames()).toIterable().containsExactlyInAnyOrder(
                "weekStart", "weekEnd", "summary",
                "categoryDistribution", "returnReasonsTop5",
                "problemProducts", "copySuggestions",
                "aiInsights", "generatedBy");
        assertThat(json.path("aiInsights").asText()).isEqualTo("시드 인사이트");
        assertThat(json.path("generatedBy").asText()).isEqualTo("seed");
        // 집계 내용도 그대로 실려야 한다
        assertThat(json.path("summary").path("totalInquiries").asInt()).isEqualTo(6);
        assertThat(json.path("problemProducts")).hasSize(2);
    }

    @Test
    @DisplayName("generate — LLM 사용 시 generatedBy에 클라이언트 이름이 기록된다")
    void generateRecordsLlmName() throws Exception {
        givenFixture();
        WeeklyReport report = new WeeklyReport(persistedUser(), WEEK_START, WEEK_END);
        when(reportRepository.findById(7L)).thenReturn(Optional.of(report));
        when(llmClient.reportInsights(any())).thenReturn("AI 인사이트");
        when(llmClient.name()).thenReturn("openai:gpt-5-mini");

        service.generate(7L, null);

        JsonNode json = objectMapper.readTree(report.getPayload());
        assertThat(json.path("aiInsights").asText()).isEqualTo("AI 인사이트");
        assertThat(json.path("generatedBy").asText()).isEqualTo("openai:gpt-5-mini");
    }

    @Test
    @DisplayName("리뷰가 없는 상품은 평균평점이 null이다 (프론트 계약: number | null)")
    void problemProductWithoutReviews() throws Exception {
        User user = new User("demo@replyfit.co", "x", "데모", "데모스토어");
        Inquiry onlyReturn = inquiry(user, "신상 원피스", "반품하고 싶어요", WEEK_START.atTime(10, 0));
        onlyReturn.classify(InquiryCategory.EXCHANGE_RETURN, 0.9);
        when(inquiryRepository.findByUserIdAndReceivedAtBetween(eq(USER_ID), any(), any()))
                .thenReturn(List.of(onlyReturn));
        when(reviewRepository.findByUserIdAndWrittenAtBetween(eq(USER_ID), any(), any()))
                .thenReturn(List.of());

        JsonNode product = aggregate().path("problemProducts").get(0);

        assertThat(product.path("productName").asText()).isEqualTo("신상 원피스");
        assertThat(product.path("negativeCount").asLong()).isEqualTo(1);
        assertThat(product.path("averageRating").isNull()).isTrue();
        assertThat(product.path("topIssue").asText()).isEqualTo("교환/반품");
    }

    @Test
    @DisplayName("데이터가 없어도 빈 구조를 반환한다 (없는 주차 리포트)")
    void emptyWeek() throws Exception {
        when(inquiryRepository.findByUserIdAndReceivedAtBetween(eq(USER_ID), any(), any()))
                .thenReturn(List.of());
        when(reviewRepository.findByUserIdAndWrittenAtBetween(eq(USER_ID), any(), any()))
                .thenReturn(List.of());

        JsonNode json = aggregate();

        assertThat(json.path("summary").path("totalInquiries").asInt()).isZero();
        assertThat(json.path("summary").path("averageRating").asDouble()).isZero();
        assertThat(json.path("summary").path("topCategory").asText()).isEqualTo("없음");
        assertThat(json.path("categoryDistribution")).isEmpty();
        assertThat(json.path("returnReasonsTop5")).isEmpty();
        assertThat(json.path("problemProducts")).isEmpty();
        assertThat(json.path("copySuggestions")).isEmpty();
    }
}
