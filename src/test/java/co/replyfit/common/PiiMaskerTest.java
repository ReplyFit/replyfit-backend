package co.replyfit.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import co.replyfit.common.PiiMasker.MaskResult;

@DisplayName("PiiMasker — 개인정보 마스킹")
class PiiMaskerTest {

    @Nested
    @DisplayName("이름 마스킹")
    class MaskName {

        @Test
        void 세_글자_이름은_가운데를_가린다() {
            assertThat(PiiMasker.maskName("홍길동")).isEqualTo("홍*동");
        }

        @Test
        void 두_글자_이름은_두번째_글자를_가린다() {
            assertThat(PiiMasker.maskName("김철")).isEqualTo("김*");
        }

        @Test
        void 한_글자_이름은_전체를_가린다() {
            assertThat(PiiMasker.maskName("이")).isEqualTo("*");
        }

        @Test
        void 네_글자_이상은_양끝만_남긴다() {
            assertThat(PiiMasker.maskName("남궁민수")).isEqualTo("남**수");
        }

        @Test
        void null이나_공백은_빈_문자열() {
            assertThat(PiiMasker.maskName(null)).isEmpty();
            assertThat(PiiMasker.maskName("  ")).isEmpty();
        }
    }

    @Nested
    @DisplayName("주문번호 마스킹")
    class MaskOrderNo {

        @Test
        void 끝_다섯_자리만_노출한다() {
            assertThat(PiiMasker.maskOrderNo("20260810-1034567")).isEqualTo("****-**34567");
        }

        @Test
        void 다섯_자리_이하도_안전하게_처리한다() {
            assertThat(PiiMasker.maskOrderNo("123")).isEqualTo("****-**123");
        }

        @Test
        void null이나_공백은_빈_문자열() {
            assertThat(PiiMasker.maskOrderNo(null)).isEmpty();
        }
    }

    @Nested
    @DisplayName("본문 마스킹")
    class MaskContent {

        @Test
        void 하이픈_전화번호를_마스킹한다() {
            MaskResult result = PiiMasker.maskContent("연락처는 010-1234-5678 입니다.", null);
            assertThat(result.masked()).contains("010-****-****").doesNotContain("1234-5678");
            assertThat(result.maskedCount()).isEqualTo(1);
        }

        @Test
        void 하이픈_없는_전화번호도_마스킹한다() {
            MaskResult result = PiiMasker.maskContent("01012345678로 전화주세요", null);
            assertThat(result.masked()).doesNotContain("01012345678");
            assertThat(result.maskedCount()).isGreaterThanOrEqualTo(1);
        }

        @Test
        void 이메일은_첫_글자와_도메인만_남긴다() {
            MaskResult result = PiiMasker.maskContent("메일은 hello@example.com 입니다", null);
            assertThat(result.masked()).contains("h***@example.com").doesNotContain("hello@");
        }

        @Test
        void 주문번호_패턴을_마스킹한다() {
            MaskResult result = PiiMasker.maskContent("주문 20260810-1034567 건이요", null);
            assertThat(result.masked()).doesNotContain("20260810-1034567");
            assertThat(result.masked()).contains("(주문번호)");
        }

        @Test
        void 본문에_등장하는_고객_이름을_치환한다() {
            MaskResult result = PiiMasker.maskContent("김서연입니다. 반품 원해요.", "김서연");
            assertThat(result.masked()).contains("김*연").doesNotContain("김서연");
        }

        @Test
        void 개인정보가_없으면_원문_그대로_카운트는_0() {
            MaskResult result = PiiMasker.maskContent("사이즈 문의드립니다.", "박하늘");
            assertThat(result.masked()).isEqualTo("사이즈 문의드립니다.");
            assertThat(result.maskedCount()).isZero();
        }

        @Test
        void 여러_항목이_섞이면_모두_마스킹하고_건수를_합산한다() {
            MaskResult result = PiiMasker.maskContent(
                    "이민준입니다. 010-9876-5432 / me@test.co.kr 로 연락주세요.", "이민준");
            assertThat(result.masked())
                    .doesNotContain("010-9876-5432")
                    .doesNotContain("me@test.co.kr")
                    .doesNotContain("이민준");
            assertThat(result.maskedCount()).isEqualTo(3);
        }
    }
}
