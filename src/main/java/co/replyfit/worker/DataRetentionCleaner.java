package co.replyfit.worker;

import java.time.LocalDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import co.replyfit.draft.AnswerDraftRepository;
import co.replyfit.inquiry.InquiryRepository;
import co.replyfit.review.ReviewRepository;

/**
 * 개인정보 보관기간 제한 — 보존 기한이 지난 문의·리뷰를 매일 삭제한다.
 * (사업계획서 보안 설계: 수집 최소화 · 보관기간 제한 · 삭제 기능)
 */
@Component
@Profile("worker")
public class DataRetentionCleaner {

    private static final Logger log = LoggerFactory.getLogger(DataRetentionCleaner.class);

    private final InquiryRepository inquiryRepository;
    private final ReviewRepository reviewRepository;
    private final AnswerDraftRepository draftRepository;
    private final int retentionDays;

    public DataRetentionCleaner(InquiryRepository inquiryRepository,
                                ReviewRepository reviewRepository,
                                AnswerDraftRepository draftRepository,
                                @Value("${replyfit.data-retention.days}") int retentionDays) {
        this.inquiryRepository = inquiryRepository;
        this.reviewRepository = reviewRepository;
        this.draftRepository = draftRepository;
        this.retentionDays = retentionDays;
    }

    @Scheduled(cron = "0 30 4 * * *", zone = "Asia/Seoul")
    @Transactional
    public void purgeExpiredData() {
        LocalDateTime threshold = LocalDateTime.now().minusDays(retentionDays);
        draftRepository.deleteByInquiryOlderThan(threshold);
        int inquiries = inquiryRepository.deleteOlderThan(threshold);
        int reviews = reviewRepository.deleteOlderThan(threshold);
        if (inquiries > 0 || reviews > 0) {
            log.info("Data retention purge: {} inquiries, {} reviews (older than {} days)",
                    inquiries, reviews, retentionDays);
        }
    }
}
