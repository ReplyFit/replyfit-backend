package co.replyfit.auth;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.replyfit.auth.dto.AuthDtos.LoginRequest;
import co.replyfit.auth.dto.AuthDtos.SignupRequest;
import co.replyfit.auth.dto.AuthDtos.TokenResponse;
import co.replyfit.auth.dto.AuthDtos.UserResponse;
import co.replyfit.common.ApiException;
import co.replyfit.user.User;
import co.replyfit.user.UserRepository;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenStore refreshTokenStore;
    private final LoginRateLimiter rateLimiter;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       RefreshTokenStore refreshTokenStore,
                       LoginRateLimiter rateLimiter) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenStore = refreshTokenStore;
        this.rateLimiter = rateLimiter;
    }

    @Transactional
    public TokenResponse signup(SignupRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw ApiException.conflict("이미 가입된 이메일입니다.");
        }
        User user = userRepository.save(new User(
                request.email(),
                passwordEncoder.encode(request.password()),
                request.name(),
                request.storeName()));
        return issueTokens(user);
    }

    @Transactional(readOnly = true)
    public TokenResponse login(LoginRequest request) {
        rateLimiter.checkAllowed(request.email());
        User user = userRepository.findByEmail(request.email())
                .orElse(null);
        if (user == null || !passwordEncoder.matches(request.password(), user.getPassword())) {
            rateLimiter.recordFailure(request.email());
            throw ApiException.unauthorized("이메일 또는 비밀번호가 올바르지 않습니다.");
        }
        rateLimiter.reset(request.email());
        return issueTokens(user);
    }

    @Transactional(readOnly = true)
    public TokenResponse refresh(String refreshToken) {
        Long userId = refreshTokenStore.consume(refreshToken);
        if (userId == null) {
            throw ApiException.unauthorized("세션이 만료되었습니다. 다시 로그인해 주세요.");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.unauthorized("사용자를 찾을 수 없습니다."));
        return issueTokens(user);
    }

    public void logout(String refreshToken) {
        if (refreshToken != null && !refreshToken.isBlank()) {
            refreshTokenStore.revoke(refreshToken);
        }
    }

    @Transactional(readOnly = true)
    public UserResponse me(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.unauthorized("사용자를 찾을 수 없습니다."));
        return UserResponse.from(user);
    }

    private TokenResponse issueTokens(User user) {
        String access = jwtService.createAccessToken(user.getId(), user.getEmail());
        String refresh = refreshTokenStore.issue(user.getId());
        return new TokenResponse(access, refresh, UserResponse.from(user));
    }
}
