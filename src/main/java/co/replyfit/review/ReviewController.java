package co.replyfit.review;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import co.replyfit.auth.AuthUser;
import co.replyfit.inquiry.dto.InquiryDtos.PageResponse;

@RestController
@RequestMapping("/api/reviews")
public class ReviewController {

    public record ReviewItem(Long id, String productName, int rating, String content,
                             String sentiment, String sentimentLabel, List<String> issueKeywords,
                             LocalDateTime writtenAt) {
        static ReviewItem from(Review review) {
            return new ReviewItem(
                    review.getId(),
                    review.getProductName(),
                    review.getRating(),
                    review.getContent(),
                    review.getSentiment().name(),
                    review.getSentiment().getLabel(),
                    review.getIssueKeywords() == null
                            ? List.of()
                            : List.of(review.getIssueKeywords().split(",")),
                    review.getWrittenAt());
        }
    }

    public record ReviewSummary(long total, Double averageRating, long negativeCount) {
    }

    private final ReviewRepository reviewRepository;

    public ReviewController(ReviewRepository reviewRepository) {
        this.reviewRepository = reviewRepository;
    }

    @GetMapping
    public ResponseEntity<PageResponse<ReviewItem>> list(
            @AuthenticationPrincipal AuthUser me,
            @RequestParam(required = false) String sentiment,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Sentiment sentimentFilter = null;
        if (sentiment != null && !sentiment.isBlank()) {
            try {
                sentimentFilter = Sentiment.valueOf(sentiment.trim().toUpperCase());
            } catch (IllegalArgumentException ignored) {
                // 잘못된 값이면 필터 미적용
            }
        }
        String query = (q == null || q.isBlank()) ? null : q.trim();
        Page<Review> result = reviewRepository.search(me.id(), sentimentFilter, query,
                PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 100)));
        return ResponseEntity.ok(new PageResponse<>(
                result.getContent().stream().map(ReviewItem::from).toList(),
                result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages()));
    }

    @GetMapping("/summary")
    public ResponseEntity<ReviewSummary> summary(@AuthenticationPrincipal AuthUser me) {
        return ResponseEntity.ok(new ReviewSummary(
                reviewRepository.countByUserId(me.id()),
                reviewRepository.averageRating(me.id()),
                reviewRepository.countByUserIdAndSentiment(me.id(), Sentiment.NEGATIVE)));
    }
}
