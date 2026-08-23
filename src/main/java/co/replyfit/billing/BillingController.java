package co.replyfit.billing;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import co.replyfit.auth.AuthUser;
import co.replyfit.common.ApiException;
import co.replyfit.user.User;
import co.replyfit.user.UserRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

@RestController
@RequestMapping("/api/billing")
public class BillingController {

    public record PlanResponse(String plan, String displayName, String tagline,
                               int monthlyPrice, List<String> features, boolean recommended) {
    }

    public record SubscriptionResponse(String plan, String displayName, String status,
                                       int monthlyPrice, LocalDateTime startedAt,
                                       LocalDateTime nextBillingAt, boolean liveBilling) {
        static SubscriptionResponse from(Subscription subscription) {
            return new SubscriptionResponse(
                    subscription.getPlan().name(),
                    subscription.getPlan().getDisplayName(),
                    subscription.getStatus().name(),
                    subscription.getMonthlyPrice(),
                    subscription.getStartedAt(),
                    subscription.getNextBillingAt(),
                    subscription.getBillingKey() != null && !subscription.getBillingKey().startsWith("stub-"));
        }
    }

    public record SubscribeRequest(@NotNull PlanType plan) {
    }

    private final SubscriptionRepository subscriptionRepository;
    private final UserRepository userRepository;
    private final TossPaymentsClient tossPaymentsClient;

    public BillingController(SubscriptionRepository subscriptionRepository,
                             UserRepository userRepository,
                             TossPaymentsClient tossPaymentsClient) {
        this.subscriptionRepository = subscriptionRepository;
        this.userRepository = userRepository;
        this.tossPaymentsClient = tossPaymentsClient;
    }

    /** 공개 요금제 목록 (랜딩페이지에서도 사용) */
    @GetMapping("/plans")
    public ResponseEntity<List<PlanResponse>> plans() {
        return ResponseEntity.ok(Arrays.stream(PlanType.values())
                .map(plan -> new PlanResponse(plan.name(), plan.getDisplayName(), plan.getTagline(),
                        plan.getMonthlyPrice(), plan.getFeatures(), plan == PlanType.GROWTH))
                .toList());
    }

    @GetMapping("/subscription")
    public ResponseEntity<SubscriptionResponse> subscription(@AuthenticationPrincipal AuthUser me) {
        Subscription subscription = subscriptionRepository.findByUserId(me.id())
                .orElseThrow(() -> ApiException.notFound("구독 정보를 찾을 수 없습니다."));
        return ResponseEntity.ok(SubscriptionResponse.from(subscription));
    }

    @PostMapping("/subscribe")
    @Transactional
    public ResponseEntity<SubscriptionResponse> subscribe(@AuthenticationPrincipal AuthUser me,
                                                          @Valid @RequestBody SubscribeRequest request) {
        User user = userRepository.findById(me.id())
                .orElseThrow(() -> ApiException.unauthorized("사용자를 찾을 수 없습니다."));
        TossPaymentsClient.BillingKeyResult billing =
                tossPaymentsClient.issueBillingKey(user.getId(), request.plan());
        Subscription subscription = subscriptionRepository.findByUserId(me.id())
                .orElseGet(() -> subscriptionRepository.save(new Subscription(
                        user, request.plan(), Subscription.Status.ACTIVE, billing.billingKey())));
        subscription.changePlan(request.plan(), billing.billingKey());
        return ResponseEntity.ok(SubscriptionResponse.from(subscription));
    }

    @PostMapping("/cancel")
    @Transactional
    public ResponseEntity<SubscriptionResponse> cancel(@AuthenticationPrincipal AuthUser me) {
        Subscription subscription = subscriptionRepository.findByUserId(me.id())
                .orElseThrow(() -> ApiException.notFound("구독 정보를 찾을 수 없습니다."));
        subscription.cancel();
        return ResponseEntity.ok(SubscriptionResponse.from(subscription));
    }
}
