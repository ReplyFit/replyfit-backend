package co.replyfit.seed;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import co.replyfit.ai.LegalNotices;
import co.replyfit.ai.LlmClient.DraftContext;
import co.replyfit.ai.LlmClient.DraftResult;
import co.replyfit.ai.LlmClient.PolicyRef;
import co.replyfit.ai.RuleBasedLlmClient;
import co.replyfit.billing.PlanType;
import co.replyfit.billing.Subscription;
import co.replyfit.billing.SubscriptionRepository;
import co.replyfit.common.PiiMasker;
import co.replyfit.draft.AnswerDraft;
import co.replyfit.draft.AnswerDraftRepository;
import co.replyfit.inquiry.Inquiry;
import co.replyfit.inquiry.InquiryCategory;
import co.replyfit.inquiry.InquiryRepository;
import co.replyfit.inquiry.InquiryStatus;
import co.replyfit.policy.Policy;
import co.replyfit.policy.PolicyRepository;
import co.replyfit.policy.PolicyType;
import co.replyfit.report.ReportService;
import co.replyfit.report.WeeklyReport;
import co.replyfit.report.WeeklyReportRepository;
import co.replyfit.review.Review;
import co.replyfit.review.ReviewRepository;
import co.replyfit.review.Sentiment;
import co.replyfit.user.User;
import co.replyfit.user.UserRepository;

/**
 * 데모 계정·샘플 데이터 시더 (개발·시연용).
 *
 * 계정: demo@replyfit.co / demo1234!
 * REPLYFIT_SEED_DEMO=false 로 끌 수 있다.
 */
@Component
@Profile("api")
@ConditionalOnProperty(name = "replyfit.seed.demo", havingValue = "true")
public class DemoDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private final UserRepository userRepository;
    private final PolicyRepository policyRepository;
    private final InquiryRepository inquiryRepository;
    private final AnswerDraftRepository draftRepository;
    private final ReviewRepository reviewRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final WeeklyReportRepository reportRepository;
    private final ReportService reportService;
    private final PasswordEncoder passwordEncoder;
    private final RuleBasedLlmClient draftEngine = new RuleBasedLlmClient();

    public DemoDataSeeder(UserRepository userRepository,
                          PolicyRepository policyRepository,
                          InquiryRepository inquiryRepository,
                          AnswerDraftRepository draftRepository,
                          ReviewRepository reviewRepository,
                          SubscriptionRepository subscriptionRepository,
                          WeeklyReportRepository reportRepository,
                          ReportService reportService,
                          PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.policyRepository = policyRepository;
        this.inquiryRepository = inquiryRepository;
        this.draftRepository = draftRepository;
        this.reviewRepository = reviewRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.reportRepository = reportRepository;
        this.reportService = reportService;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (userRepository.existsByEmail("demo@replyfit.co")) {
            return;
        }
        log.info("Seeding demo data (demo@replyfit.co / demo1234!)");

        User user = userRepository.save(new User(
                "demo@replyfit.co", passwordEncoder.encode("demo1234!"), "박지은", "지은상점"));
        subscriptionRepository.save(new Subscription(
                user, PlanType.GROWTH, Subscription.Status.ACTIVE, null));

        // 1) 스토어 정책
        Policy shipping = policyRepository.save(new Policy(user, PolicyType.SHIPPING, "기본 배송 안내",
                "주문 후 평균 2~3일 내 출고되며, 출고 후 1~2일 내 수령하실 수 있습니다. "
                        + "주말·공휴일 주문 건은 다음 영업일에 순차 출고됩니다. 5만원 이상 구매 시 무료배송입니다."));
        Policy exchange = policyRepository.save(new Policy(user, PolicyType.EXCHANGE_RETURN, "교환/반품 기준",
                "상품 수령일로부터 7일 이내 교환/반품 신청이 가능합니다. 단순 변심의 경우 왕복 배송비 6,000원이 "
                        + "부과되며, 상품 하자·오배송은 저희가 배송비를 부담합니다. 착용 흔적·세탁·향수 냄새가 있는 경우 "
                        + "교환/반품이 어렵습니다."));
        Policy size = policyRepository.save(new Policy(user, PolicyType.SIZE_GUIDE, "사이즈 안내",
                "상세페이지 하단 실측 사이즈표를 기준으로 안내드립니다. 평소 55 사이즈는 S~M, 66 사이즈는 L을 "
                        + "권장드립니다. 니트·린넨 소재는 세탁 후 약 1cm 내외 수축될 수 있습니다."));
        policyRepository.save(new Policy(user, PolicyType.RESTOCK, "재입고 안내",
                "인기 상품은 통상 2~3주 내 재입고됩니다. 재입고 알림 신청 시 입고 즉시 알림톡을 보내드립니다."));
        policyRepository.save(new Policy(user, PolicyType.GENERAL, "고객 응대 기본 안내",
                "평일 오전 10시~오후 5시에 순차적으로 답변드리고 있습니다. 주말·공휴일 문의는 다음 영업일에 "
                        + "답변드립니다."));

        // 2) 문의 + 초안 (지난 2주 분포)
        seedInquiry(user, "네이버", "김서연", "20260810-1034567", "린넨 와이드 팬츠",
                "165cm 52kg인데 사이즈 M이 맞을까요? 허리는 여유있게 입고 싶어요.",
                InquiryCategory.SIZE, InquiryStatus.SENT, 12, List.of(size, exchange));
        seedInquiry(user, "쿠팡", "이민준", "20260811-2045678", "베이직 셔츠 (화이트)",
                "어제 주문했는데 언제 배송되나요? 금요일 전에 받아야 해서요.",
                InquiryCategory.SHIPPING, InquiryStatus.SENT, 11, List.of(shipping));
        seedInquiry(user, "네이버", "박하늘", "20260812-3056789", "크롭 니트 가디건",
                "색상이 화면이랑 너무 달라요. 교환하고 싶은데 배송비는 어떻게 되나요?",
                InquiryCategory.EXCHANGE_RETURN, InquiryStatus.APPROVED, 10, List.of(exchange, shipping));
        seedInquiry(user, "자사몰", "최유진", "20260812-4067890", "미니 크로스백",
                "재입고 언제 되나요? 블랙 색상 기다리고 있어요.",
                InquiryCategory.RESTOCK, InquiryStatus.DRAFTED, 9, List.of());
        seedInquiry(user, "네이버", "정수아", "20260813-5078901", "플리츠 스커트",
                "실물 색감이 사진보다 어두운가요? 아이보리랑 베이지 중에 고민이에요.",
                InquiryCategory.COLOR, InquiryStatus.DRAFTED, 8, List.of(exchange));
        seedInquiry(user, "쿠팡", "한지우", "20260814-6089012", "오버핏 맨투맨",
                "기장이 너무 길어서 반품하고 싶어요. 태그는 안 뜯었습니다.",
                InquiryCategory.EXCHANGE_RETURN, InquiryStatus.NEEDS_REVIEW, 7, List.of(exchange),
                "등록된 정책·법정 안내에서 확인되지 않는 수치 표현이 포함되어 있습니다: 14일 — 발송 전 셀러 확인이 필요합니다.");
        seedInquiry(user, "네이버", "오세린", "20260815-7090123", "린넨 와이드 팬츠",
                "총장이 어떻게 되나요? 키 158이라 길면 수선해야 할 것 같아요.",
                InquiryCategory.SIZE, InquiryStatus.DRAFTED, 6, List.of(size));
        seedInquiry(user, "자사몰", "강도윤", "20260816-8001234", "베이직 셔츠 (화이트)",
                "송장번호가 조회가 안 돼요. 확인 부탁드립니다. 연락처는 010-1234-5678 입니다.",
                InquiryCategory.SHIPPING, InquiryStatus.DRAFTED, 5, List.of(shipping));
        seedInquiry(user, "네이버", "윤채원", "20260817-9012345", "크롭 니트 가디건",
                "보풀이 너무 잘 생겨요. 환불 가능한가요?",
                InquiryCategory.EXCHANGE_RETURN, InquiryStatus.RECEIVED, 4, null);
        seedInquiry(user, "쿠팡", "임서준", "20260818-0123456", "와이드 데님 팬츠",
                "사이즈 교환 가능할까요? L로 시켰는데 허리가 커요.",
                InquiryCategory.EXCHANGE_RETURN, InquiryStatus.RECEIVED, 3, null);
        seedInquiry(user, "네이버", "송예은", "20260819-1234567", "플리츠 스커트",
                "허리 밴딩인가요? 임산부도 입을 수 있을지 궁금해요.",
                InquiryCategory.SIZE, InquiryStatus.RECEIVED, 2, null);
        seedInquiry(user, "자사몰", "백지호", "20260820-2345678", "미니 크로스백",
                "스트랩 길이 조절 되나요? 크로스로 메고 싶습니다.",
                InquiryCategory.OTHER, InquiryStatus.RECEIVED, 1, null);

        // 3) 리뷰
        seedReview(user, "린넨 와이드 팬츠", 5, "핏이 너무 예뻐요! 여름에 시원하게 입기 좋아요.", 13, Sentiment.POSITIVE, null);
        seedReview(user, "린넨 와이드 팬츠", 2, "사이즈가 너무 작아요. 한 치수 크게 시키세요.", 12, Sentiment.NEGATIVE, "사이즈");
        seedReview(user, "크롭 니트 가디건", 1, "색상이 사진과 완전 달라요. 실망했습니다.", 11, Sentiment.NEGATIVE, "색상");
        seedReview(user, "크롭 니트 가디건", 2, "두 번 입었는데 보풀이 심해요. 품질이 아쉽네요.", 10, Sentiment.NEGATIVE, "품질,불량");
        seedReview(user, "베이직 셔츠 (화이트)", 4, "기본템으로 좋아요. 배송도 빨랐습니다.", 9, Sentiment.POSITIVE, null);
        seedReview(user, "오버핏 맨투맨", 3, "무난한데 기장이 좀 길어요. 참고하세요.", 8, Sentiment.NEUTRAL, "사이즈");
        seedReview(user, "와이드 데님 팬츠", 5, "인생 데님입니다. 색감 최고예요.", 7, Sentiment.POSITIVE, null);
        seedReview(user, "플리츠 스커트", 2, "배송이 일주일 넘게 걸렸어요. 너무 늦네요.", 6, Sentiment.NEGATIVE, "배송");
        seedReview(user, "미니 크로스백", 4, "가볍고 수납도 괜찮아요.", 5, Sentiment.POSITIVE, null);
        seedReview(user, "린넨 와이드 팬츠", 1, "세탁했더니 줄어들었어요. 반품하고 싶어요.", 4, Sentiment.NEGATIVE, "사이즈,품질");
        seedReview(user, "크롭 니트 가디건", 5, "색 조합이 예쁘고 따뜻해요.", 3, Sentiment.POSITIVE, null);
        seedReview(user, "베이직 셔츠 (화이트)", 3, "비침이 조금 있어요. 이너 필수입니다.", 2, Sentiment.NEUTRAL, "품질");

        // 4) 지난주 VOC 리포트 (완성 상태로 시드)
        LocalDate lastMonday = LocalDate.now().with(java.time.DayOfWeek.MONDAY).minusWeeks(1);
        WeeklyReport report = reportRepository.save(new WeeklyReport(user, lastMonday, lastMonday.plusDays(6)));
        reportService.generate(report.getId(),
                "### 이번 주 요약\n지난주 문의는 사이즈·교환/반품 유형에 집중되었습니다. "
                        + "린넨 와이드 팬츠와 크롭 니트 가디건에 부정 피드백이 몰려 있어 상세페이지 보강이 필요합니다.\n\n"
                        + "### 개선 액션\n"
                        + "1. 반품 사유 1위 \"사이즈가 맞지 않음\" — 린넨 와이드 팬츠 상세페이지 상단에 "
                        + "\"평소 55는 S~M, 66은 L 권장\" 문구를 추가하세요.\n"
                        + "2. 크롭 니트 가디건 색상 불만 2건 — 자연광 실물 촬영 컷과 "
                        + "\"모니터 설정에 따라 색상 차이가 있을 수 있습니다\" 안내를 추가하세요.\n"
                        + "3. 크롭 니트 가디건 보풀 이슈 — 소재 특성과 세탁 관리법(니트 전용 세제, 손세탁)을 안내하고, "
                        + "보풀 제거기 동봉 이벤트를 검토하세요.\n"
                        + "4. 배송 불만 1건 — 출고 지연 시 알림톡 자동 발송을 검토하세요.");

        log.info("Demo data seeded: 12 inquiries, 12 reviews, 5 policies, 1 weekly report");
    }

    private void seedInquiry(User user, String channel, String rawName, String rawOrderNo,
                             String productName, String content, InquiryCategory category,
                             InquiryStatus status, int daysAgo, List<Policy> citedPolicies) {
        seedInquiry(user, channel, rawName, rawOrderNo, productName, content, category, status,
                daysAgo, citedPolicies, null);
    }

    private void seedInquiry(User user, String channel, String rawName, String rawOrderNo,
                             String productName, String content, InquiryCategory category,
                             InquiryStatus status, int daysAgo, List<Policy> citedPolicies,
                             String guardNote) {
        PiiMasker.MaskResult masked = PiiMasker.maskContent(content, rawName);
        Inquiry inquiry = new Inquiry(user, channel, PiiMasker.maskName(rawName),
                PiiMasker.maskOrderNo(rawOrderNo), productName, masked.masked(),
                masked.maskedCount() + 2, LocalDateTime.now().minusDays(daysAgo).withHour(11));
        inquiry.classify(category, 0.88);
        inquiry.changeStatus(status);
        inquiryRepository.save(inquiry);

        if (status == InquiryStatus.RECEIVED || citedPolicies == null) {
            return;
        }
        List<PolicyRef> refs = citedPolicies.stream()
                .map(policy -> new PolicyRef(policy.getId(), policy.getType().getLabel(),
                        policy.getTitle(), policy.getContent()))
                .toList();
        DraftContext context = new DraftContext(user.getStoreName(), PiiMasker.maskName(rawName),
                productName, channel, masked.masked(), category, refs,
                category == InquiryCategory.EXCHANGE_RETURN ? LegalNotices.WITHDRAWAL_RIGHT : null);
        DraftResult draft = draftEngine.generateDraft(context);
        AnswerDraft answerDraft = new AnswerDraft(inquiry, draft.content(),
                draft.citedPolicyIds().isEmpty() ? null
                        : String.join(",", draft.citedPolicyIds().stream().map(String::valueOf).toList()),
                "rule-based", guardNote);
        if (status == InquiryStatus.APPROVED || status == InquiryStatus.SENT) {
            answerDraft.approve();
        }
        if (status == InquiryStatus.SENT) {
            answerDraft.markSent();
        }
        draftRepository.save(answerDraft);
    }

    private void seedReview(User user, String productName, int rating, String content,
                            int daysAgo, Sentiment sentiment, String keywords) {
        reviewRepository.save(new Review(user, productName, rating, content, sentiment, keywords,
                LocalDateTime.now().minusDays(daysAgo).withHour(20)));
    }
}
