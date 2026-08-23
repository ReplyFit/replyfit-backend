package co.replyfit.inquiry;

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
 * 고객 문의. 업로드 시점에 이미 개인정보가 마스킹된 상태로만 저장된다.
 */
@Entity
@Table(name = "inquiries", indexes = {
        @Index(name = "idx_inquiry_user_received", columnList = "user_id, receivedAt"),
        @Index(name = "idx_inquiry_user_status", columnList = "user_id, status")
})
public class Inquiry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(length = 50)
    private String channel;

    /** 마스킹된 고객명 (예: 김*은) */
    @Column(length = 50)
    private String customerName;

    /** 마스킹된 주문번호 (예: ****-**34567) */
    @Column(length = 50)
    private String orderNo;

    @Column(length = 300)
    private String productName;

    /** 마스킹된 문의 본문 */
    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private InquiryCategory category;

    private Double categoryConfidence;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private InquiryStatus status;

    /** 업로드 시 마스킹 처리된 개인정보 항목 수 */
    @Column(nullable = false)
    private int piiMaskedCount;

    @Column(nullable = false)
    private LocalDateTime receivedAt;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    protected Inquiry() {
    }

    public Inquiry(User user, String channel, String customerName, String orderNo,
                   String productName, String content, int piiMaskedCount, LocalDateTime receivedAt) {
        this.user = user;
        this.channel = channel;
        this.customerName = customerName;
        this.orderNo = orderNo;
        this.productName = productName;
        this.content = content;
        this.piiMaskedCount = piiMaskedCount;
        this.status = InquiryStatus.RECEIVED;
        this.receivedAt = receivedAt;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public String getChannel() {
        return channel;
    }

    public String getCustomerName() {
        return customerName;
    }

    public String getOrderNo() {
        return orderNo;
    }

    public String getProductName() {
        return productName;
    }

    public String getContent() {
        return content;
    }

    public InquiryCategory getCategory() {
        return category;
    }

    public Double getCategoryConfidence() {
        return categoryConfidence;
    }

    public InquiryStatus getStatus() {
        return status;
    }

    public int getPiiMaskedCount() {
        return piiMaskedCount;
    }

    public LocalDateTime getReceivedAt() {
        return receivedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void classify(InquiryCategory category, double confidence) {
        this.category = category;
        this.categoryConfidence = confidence;
    }

    public void changeStatus(InquiryStatus status) {
        this.status = status;
    }
}
