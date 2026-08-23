package co.replyfit.policy;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import co.replyfit.auth.AuthUser;
import co.replyfit.common.ApiException;
import co.replyfit.user.User;
import co.replyfit.user.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 스토어 정책 등록·관리 (MVP 핵심 기능 ①).
 * 여기 등록된 정책 문구만 AI 답변 초안에 인용된다.
 */
@RestController
@RequestMapping("/api/policies")
@Tag(name = "02. 스토어 정책", description = "AI 답변 초안은 여기 등록된 정책 문구만 인용합니다. 유형: SHIPPING(배송)·EXCHANGE_RETURN(교환/반품)·SIZE_GUIDE(사이즈)·RESTOCK(재입고)·GENERAL(일반)")
public class PolicyController {

    public record PolicyRequest(
            @NotNull PolicyType type,
            @NotBlank @Size(max = 200) String title,
            @NotBlank String content) {
    }

    public record PolicyResponse(Long id, String type, String typeLabel, String title,
                                 String content, LocalDateTime updatedAt) {
        static PolicyResponse from(Policy policy) {
            return new PolicyResponse(policy.getId(), policy.getType().name(),
                    policy.getType().getLabel(), policy.getTitle(), policy.getContent(),
                    policy.getUpdatedAt());
        }
    }

    private final PolicyRepository policyRepository;
    private final UserRepository userRepository;

    public PolicyController(PolicyRepository policyRepository, UserRepository userRepository) {
        this.policyRepository = policyRepository;
        this.userRepository = userRepository;
    }

    @Operation(summary = "내 정책 목록 조회")
    @GetMapping
    public ResponseEntity<List<PolicyResponse>> list(@AuthenticationPrincipal AuthUser me) {
        return ResponseEntity.ok(policyRepository.findByUserIdOrderByTypeAscUpdatedAtDesc(me.id())
                .stream().map(PolicyResponse::from).toList());
    }

    @Operation(summary = "정책 등록", description = "등록 즉시 다음 초안 생성부터 인용 대상이 됩니다.")
    @PostMapping
    @Transactional
    public ResponseEntity<PolicyResponse> create(@AuthenticationPrincipal AuthUser me,
                                                 @Valid @RequestBody PolicyRequest request) {
        User user = userRepository.findById(me.id())
                .orElseThrow(() -> ApiException.unauthorized("사용자를 찾을 수 없습니다."));
        Policy policy = policyRepository.save(
                new Policy(user, request.type(), request.title(), request.content()));
        return ResponseEntity.ok(PolicyResponse.from(policy));
    }

    @Operation(summary = "정책 수정")
    @PutMapping("/{id}")
    @Transactional
    public ResponseEntity<PolicyResponse> update(@AuthenticationPrincipal AuthUser me,
                                                 @PathVariable Long id,
                                                 @Valid @RequestBody PolicyRequest request) {
        Policy policy = getOwned(me.id(), id);
        policy.update(request.type(), request.title(), request.content());
        return ResponseEntity.ok(PolicyResponse.from(policy));
    }

    @Operation(summary = "정책 삭제")
    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        Policy policy = getOwned(me.id(), id);
        policyRepository.delete(policy);
        return ResponseEntity.noContent().build();
    }

    private Policy getOwned(Long userId, Long policyId) {
        Policy policy = policyRepository.findById(policyId)
                .orElseThrow(() -> ApiException.notFound("정책을 찾을 수 없습니다."));
        if (!policy.getUser().getId().equals(userId)) {
            throw ApiException.forbidden("접근 권한이 없습니다.");
        }
        return policy;
    }
}
