package co.replyfit.auth;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import co.replyfit.auth.dto.AuthDtos.LoginRequest;
import co.replyfit.auth.dto.AuthDtos.LogoutRequest;
import co.replyfit.auth.dto.AuthDtos.RefreshRequest;
import co.replyfit.auth.dto.AuthDtos.SignupRequest;
import co.replyfit.auth.dto.AuthDtos.TokenResponse;
import co.replyfit.auth.dto.AuthDtos.UserResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "01. 인증", description = "회원가입·로그인·토큰 관리 — 액세스 토큰(JWT 30분) + 리프레시 토큰(Redis 14일, 회전 방식)")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @Operation(summary = "회원가입", description = "가입 즉시 Starter 무료 체험 구독이 생성되고 토큰이 발급됩니다.")
    @PostMapping("/signup")
    public ResponseEntity<TokenResponse> signup(@Valid @RequestBody SignupRequest request) {
        return ResponseEntity.ok(authService.signup(request));
    }

    @Operation(summary = "로그인", description = "10분 내 5회 실패 시 잠금(429). 데모 계정: demo@replyfit.co / demo1234!")
    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @Operation(summary = "토큰 갱신", description = "리프레시 토큰은 1회용 — 사용 즉시 폐기되고 새 토큰 쌍이 발급됩니다(회전).")
    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return ResponseEntity.ok(authService.refresh(request.refreshToken()));
    }

    @Operation(summary = "로그아웃", description = "서버에 저장된 리프레시 토큰을 폐기합니다.")
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestBody(required = false) LogoutRequest request) {
        authService.logout(request == null ? null : request.refreshToken());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "내 정보 조회")
    @GetMapping("/me")
    public ResponseEntity<UserResponse> me(@AuthenticationPrincipal AuthUser me) {
        return ResponseEntity.ok(authService.me(me.id()));
    }
}
