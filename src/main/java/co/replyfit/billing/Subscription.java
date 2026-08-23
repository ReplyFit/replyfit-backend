package co.replyfit.billing;

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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "subscriptions")
public class Subscription {

    public enum Status {
        TRIAL, ACTIVE, CANCELED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", unique = true)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PlanType plan;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @Column(nullable = false)
    private int monthlyPrice;

    /**
     * 토스페이먼츠 빌링키 (현재는 스텁 — '26.12 정기결제 연동 시 실제 값으로 대체).
     * 파일럿 기간에는 계좌이체 수동 청구.
     */
    @Column(length = 200)
    private String billingKey;

    @Column(nullable = false)
    private LocalDateTime startedAt;

    private LocalDateTime nextBillingAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    protected Subscription() {
    }

    public Subscription(User user, PlanType plan, Status status, String billingKey) {
        this.user = user;
        this.plan = plan;
        this.status = status;
        this.monthlyPrice = plan.getMonthlyPrice();
        this.billingKey = billingKey;
        this.startedAt = LocalDateTime.now();
        this.nextBillingAt = this.startedAt.plusMonths(1);
        this.updatedAt = this.startedAt;
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public PlanType getPlan() {
        return plan;
    }

    public Status getStatus() {
        return status;
    }

    public int getMonthlyPrice() {
        return monthlyPrice;
    }

    public String getBillingKey() {
        return billingKey;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public LocalDateTime getNextBillingAt() {
        return nextBillingAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void changePlan(PlanType plan, String billingKey) {
        this.plan = plan;
        this.monthlyPrice = plan.getMonthlyPrice();
        this.billingKey = billingKey;
        this.status = Status.ACTIVE;
        this.nextBillingAt = LocalDateTime.now().plusMonths(1);
        this.updatedAt = LocalDateTime.now();
    }

    public void cancel() {
        this.status = Status.CANCELED;
        this.nextBillingAt = null;
        this.updatedAt = LocalDateTime.now();
    }
}
