package co.replyfit.report;

import java.time.LocalDate;
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
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * 주간 VOC 리포트 — 반품 사유 TOP5, 문제 상품, 상세페이지 문구 제안까지 담는다.
 */
@Entity
@Table(name = "weekly_reports")
public class WeeklyReport {

    public enum Status {
        GENERATING, READY, FAILED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false)
    private LocalDate weekStart;

    @Column(nullable = false)
    private LocalDate weekEnd;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    /** 리포트 본문(JSON) */
    @Column(columnDefinition = "text")
    private String payload;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    protected WeeklyReport() {
    }

    public WeeklyReport(User user, LocalDate weekStart, LocalDate weekEnd) {
        this.user = user;
        this.weekStart = weekStart;
        this.weekEnd = weekEnd;
        this.status = Status.GENERATING;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public LocalDate getWeekStart() {
        return weekStart;
    }

    public LocalDate getWeekEnd() {
        return weekEnd;
    }

    public Status getStatus() {
        return status;
    }

    public String getPayload() {
        return payload;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void complete(String payload) {
        this.payload = payload;
        this.status = Status.READY;
    }

    public void fail() {
        this.status = Status.FAILED;
    }
}
