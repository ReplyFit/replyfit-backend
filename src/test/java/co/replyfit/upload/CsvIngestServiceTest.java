package co.replyfit.upload;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;

import co.replyfit.common.ApiException;
import co.replyfit.inquiry.Inquiry;
import co.replyfit.inquiry.InquiryRepository;
import co.replyfit.review.Review;
import co.replyfit.review.ReviewRepository;
import co.replyfit.review.Sentiment;
import co.replyfit.user.User;

@DisplayName("CsvIngestService — CSV 파싱·마스킹 파이프라인")
class CsvIngestServiceTest {

    private InquiryRepository inquiryRepository;
    private ReviewRepository reviewRepository;
    private CsvIngestService service;
    private final User user = new User("seller@test.co", "pw", "판매자", "테스트상점");

    @BeforeEach
    void setUp() {
        inquiryRepository = mock(InquiryRepository.class);
        reviewRepository = mock(ReviewRepository.class);
        when(inquiryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(reviewRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        service = new CsvIngestService(inquiryRepository, reviewRepository);
    }

    private static MockMultipartFile csv(String content) {
        return new MockMultipartFile("file", "upload.csv", "text/csv",
                content.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void 문의_CSV를_파싱해_마스킹된_상태로_저장한다() {
        var file = csv("""
                문의일시,채널,고객명,주문번호,상품명,문의내용
                2026-08-20 10:12,네이버,김서연,20260819-1034567,린넨 팬츠,"사이즈 M 맞을까요? 010-1234-5678로 연락주세요"
                """);

        service.ingestInquiries(user, file);

        ArgumentCaptor<Inquiry> captor = ArgumentCaptor.forClass(Inquiry.class);
        org.mockito.Mockito.verify(inquiryRepository).save(captor.capture());
        Inquiry saved = captor.getValue();

        assertThat(saved.getCustomerName()).isEqualTo("김*연");
        assertThat(saved.getOrderNo()).isEqualTo("****-**34567");
        assertThat(saved.getContent()).contains("010-****-****").doesNotContain("1234-5678");
        assertThat(saved.getChannel()).isEqualTo("네이버");
        assertThat(saved.getProductName()).isEqualTo("린넨 팬츠");
        assertThat(saved.getPiiMaskedCount()).isGreaterThanOrEqualTo(3);
        assertThat(saved.getReceivedAt().getHour()).isEqualTo(10);
    }

    @Test
    void 헤더_동의어도_매핑한다() {
        var file = csv("""
                날짜,구매자명,내용
                2026-08-20,박하늘,배송 언제 되나요?
                """);

        service.ingestInquiries(user, file);

        ArgumentCaptor<Inquiry> captor = ArgumentCaptor.forClass(Inquiry.class);
        org.mockito.Mockito.verify(inquiryRepository).save(captor.capture());
        assertThat(captor.getValue().getCustomerName()).isEqualTo("박*늘");
        assertThat(captor.getValue().getContent()).isEqualTo("배송 언제 되나요?");
        assertThat(captor.getValue().getChannel()).isEqualTo("기타");
    }

    @Test
    void 문의내용_열이_없으면_안내와_함께_거부한다() {
        var file = csv("""
                고객명,메모
                김서연,이 파일은 형식이 다릅니다
                """);

        assertThatThrownBy(() -> service.ingestInquiries(user, file))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("문의내용");
    }

    @Test
    void 리뷰는_평점_기반으로_감성을_판정한다() {
        var file = csv("""
                작성일시,상품명,평점,리뷰내용
                2026-08-20,린넨 팬츠,5,핏이 너무 예뻐요
                2026-08-20,린넨 팬츠,1,사이즈가 너무 작아요. 실망입니다
                2026-08-20,셔츠,3,무난해요
                """);

        int count = service.ingestReviews(user, file);
        assertThat(count).isEqualTo(3);

        ArgumentCaptor<Review> captor = ArgumentCaptor.forClass(Review.class);
        org.mockito.Mockito.verify(reviewRepository, org.mockito.Mockito.times(3)).save(captor.capture());
        List<Review> saved = captor.getAllValues();

        assertThat(saved.get(0).getSentiment()).isEqualTo(Sentiment.POSITIVE);
        assertThat(saved.get(1).getSentiment()).isEqualTo(Sentiment.NEGATIVE);
        assertThat(saved.get(1).getIssueKeywords()).contains("사이즈");
        assertThat(saved.get(2).getSentiment()).isEqualTo(Sentiment.NEUTRAL);
    }

    @Test
    void 평점이_비정상이면_중립값으로_보정한다() {
        var file = csv("""
                상품명,평점,리뷰내용
                셔츠,십점만점,그럭저럭입니다
                """);

        service.ingestReviews(user, file);

        ArgumentCaptor<Review> captor = ArgumentCaptor.forClass(Review.class);
        org.mockito.Mockito.verify(reviewRepository).save(captor.capture());
        assertThat(captor.getValue().getRating()).isEqualTo(3);
    }

    @Test
    void 이슈_키워드를_리뷰_본문에서_추출한다() {
        assertThat(CsvIngestService.extractIssueKeywords("사이즈가 크고 색상도 달라요"))
                .contains("사이즈").contains("색상");
        assertThat(CsvIngestService.extractIssueKeywords("배송이 늦었어요")).contains("배송");
        assertThat(CsvIngestService.extractIssueKeywords("보풀이 심해요")).contains("불량");
        assertThat(CsvIngestService.extractIssueKeywords("잘 입고 있어요")).isNull();
    }
}
