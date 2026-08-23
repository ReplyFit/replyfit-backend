package co.replyfit.dashboard;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;

import co.replyfit.inquiry.InquiryRepository;
import co.replyfit.inquiry.InquiryStatus;
import co.replyfit.inquiry.dto.InquiryDtos.InquiryListItem;
import co.replyfit.review.ReviewRepository;
import co.replyfit.review.Sentiment;

@Service
public class DashboardService {

    /** 문의 1건 수동 응대 대비 절감 시간(분) — 사업계획서 근거: 하루 30분~1시간 절감 */
    private static final int SAVED_MINUTES_PER_DRAFT = 4;

    public record CategorySlice(String category, String label, long count) {
    }

    public record WeekPoint(String week, long inquiries) {
    }

    public record DashboardStats(
            long totalInquiries,
            long pendingDrafts,
            long needsReview,
            long completed,
            long totalReviews,
            Double averageRating,
            long negativeReviews,
            long estimatedSavedMinutes,
            List<CategorySlice> categoryDistribution,
            List<WeekPoint> weeklyTrend,
            List<InquiryListItem> recentInquiries) {
    }

    private final InquiryRepository inquiryRepository;
    private final ReviewRepository reviewRepository;
    private final DashboardCache cache;
    private final ObjectMapper objectMapper;

    public DashboardService(InquiryRepository inquiryRepository,
                            ReviewRepository reviewRepository,
                            DashboardCache cache,
                            ObjectMapper objectMapper) {
        this.inquiryRepository = inquiryRepository;
        this.reviewRepository = reviewRepository;
        this.cache = cache;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public DashboardStats getStats(Long userId) {
        // Redis 캐시 우선 조회 (60초 TTL, 워커가 초안 생성 시 무효화)
        String cached = cache.get(userId);
        if (cached != null) {
            try {
                return objectMapper.readValue(cached, DashboardStats.class);
            } catch (Exception ignored) {
                // 캐시 역직렬화 실패 시 새로 계산
            }
        }
        DashboardStats stats = compute(userId);
        try {
            cache.put(userId, objectMapper.writeValueAsString(stats));
        } catch (Exception ignored) {
            // 캐시 저장 실패는 무시
        }
        return stats;
    }

    private DashboardStats compute(Long userId) {
        long total = inquiryRepository.countByUserId(userId);
        long pending = inquiryRepository.countByUserIdAndStatusIn(userId,
                List.of(InquiryStatus.DRAFTED));
        long needsReview = inquiryRepository.countByUserIdAndStatusIn(userId,
                List.of(InquiryStatus.NEEDS_REVIEW));
        long completed = inquiryRepository.countByUserIdAndStatusIn(userId,
                List.of(InquiryStatus.APPROVED, InquiryStatus.SENT));

        List<CategorySlice> categories = inquiryRepository.countByCategory(userId).stream()
                .map(row -> new CategorySlice(row.getCategory().name(), row.getCategory().getLabel(), row.getCnt()))
                .toList();

        List<WeekPoint> trend = new ArrayList<>();
        LocalDate monday = LocalDate.now().with(java.time.DayOfWeek.MONDAY);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("M/d");
        for (int i = 7; i >= 0; i--) {
            LocalDate weekStart = monday.minusWeeks(i);
            LocalDateTime from = weekStart.atStartOfDay();
            LocalDateTime to = weekStart.plusDays(7).atStartOfDay();
            trend.add(new WeekPoint(
                    formatter.format(weekStart),
                    inquiryRepository.countByUserIdAndReceivedAtBetween(userId, from, to)));
        }

        List<InquiryListItem> recent = inquiryRepository.findTop5ByUserIdOrderByReceivedAtDesc(userId)
                .stream().map(InquiryListItem::from).toList();

        long drafted = pending + needsReview + completed;
        return new DashboardStats(
                total, pending, needsReview, completed,
                reviewRepository.countByUserId(userId),
                reviewRepository.averageRating(userId),
                reviewRepository.countByUserIdAndSentiment(userId, Sentiment.NEGATIVE),
                drafted * SAVED_MINUTES_PER_DRAFT,
                categories, trend, recent);
    }
}
