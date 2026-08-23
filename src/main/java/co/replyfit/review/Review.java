package co.replyfit.review;

import java.time.LocalDateTime;

import co.replyfit.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * 상품 리뷰. 문의와 함께 분석해 주간 VOC 리포트의 재료가 된다.
 */
@Entity
@Table(name = "reviews", indexes = {
        @Index(name = "idx_review_user_written", columnList = "user_id, writtenAt")
})
public class Review {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(length = 300)
    private String productName;

    @Column(nullable = false)
    private int rating;

    /** 마스킹된 리뷰 본문 */
    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Sentiment sentiment;

    /** 리뷰에서 추출한 이슈 키워드 (쉼표 구분: 사이즈,색상 …) */
    @Column(length = 200)
    private String issueKeywords;

    @Column(nullable = false)
    private LocalDateTime writtenAt;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    protected Review() {
    }

    public Review(User user, String productName, int rating, String content,
                  Sentiment sentiment, String issueKeywords, LocalDateTime writtenAt) {
        this.user = user;
        this.productName = productName;
        this.rating = rating;
        this.content = content;
        this.sentiment = sentiment;
        this.issueKeywords = issueKeywords;
        this.writtenAt = writtenAt;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public String getProductName() {
        return productName;
    }

    public int getRating() {
        return rating;
    }

    public String getContent() {
        return content;
    }

    public Sentiment getSentiment() {
        return sentiment;
    }

    public String getIssueKeywords() {
        return issueKeywords;
    }

    public LocalDateTime getWrittenAt() {
        return writtenAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
