package co.replyfit.inquiry;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import co.replyfit.user.User;

/**
 * 실제 PostgreSQL 컨테이너 기반 검색 쿼리 회귀 테스트.
 *
 * 배경(#14): H2/로컬에서는 통과하지만 PostgreSQL에서는 null 문자열 파라미터가
 * bytea로 바인딩되어 "operator does not exist: text ~~ bytea" 500이 발생했다.
 * 이 테스트는 반드시 실제 PG 위에서 돌아야 의미가 있다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("InquiryRepository.search — PostgreSQL 실쿼리 회귀")
class InquiryRepositorySearchTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private InquiryRepository inquiryRepository;

    @Autowired
    private TestEntityManager entityManager;

    private User seller;

    @BeforeEach
    void setUp() {
        seller = entityManager.persist(new User("seller@test.co", "pw", "판매자", "테스트상점"));
        persistInquiry("사이즈 M 맞을까요?", "린넨 팬츠", InquiryCategory.SIZE);
        persistInquiry("배송 언제 되나요?", "베이직 셔츠", InquiryCategory.SHIPPING);
        persistInquiry("반품하고 싶어요", "린넨 팬츠", InquiryCategory.EXCHANGE_RETURN);
        entityManager.flush();
    }

    private void persistInquiry(String content, String productName, InquiryCategory category) {
        Inquiry inquiry = new Inquiry(seller, "네이버", "김*연", "****-**12345",
                productName, content, 1, LocalDateTime.now());
        inquiry.classify(category, 0.9);
        entityManager.persist(inquiry);
    }

    @Test
    void 검색어_없이_조회해도_500이_나지_않는다_이슈14_회귀() {
        Page<Inquiry> page = inquiryRepository.search(
                seller.getId(), null, null, null, PageRequest.of(0, 10));
        assertThat(page.getTotalElements()).isEqualTo(3);
    }

    @Test
    void 검색어로_본문과_상품명을_함께_검색한다() {
        Page<Inquiry> byContent = inquiryRepository.search(
                seller.getId(), null, null, "배송", PageRequest.of(0, 10));
        assertThat(byContent.getTotalElements()).isEqualTo(1);

        Page<Inquiry> byProduct = inquiryRepository.search(
                seller.getId(), null, null, "린넨", PageRequest.of(0, 10));
        assertThat(byProduct.getTotalElements()).isEqualTo(2);
    }

    @Test
    void 카테고리와_상태_필터가_동작한다() {
        Page<Inquiry> sizeOnly = inquiryRepository.search(
                seller.getId(), InquiryCategory.SIZE, null, null, PageRequest.of(0, 10));
        assertThat(sizeOnly.getTotalElements()).isEqualTo(1);

        Page<Inquiry> received = inquiryRepository.search(
                seller.getId(), null, InquiryStatus.RECEIVED, null, PageRequest.of(0, 10));
        assertThat(received.getTotalElements()).isEqualTo(3);
    }

    @Test
    void 다른_셀러의_문의는_조회되지_않는다() {
        User other = entityManager.persist(new User("other@test.co", "pw", "남", "다른상점"));
        entityManager.flush();
        Page<Inquiry> page = inquiryRepository.search(
                other.getId(), null, null, null, PageRequest.of(0, 10));
        assertThat(page.getTotalElements()).isZero();
    }
}
