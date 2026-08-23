package co.replyfit.auth.dto;

import co.replyfit.user.User;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record SignupRequest(
            @NotBlank @Email String email,
            @NotBlank @Size(min = 8, max = 100) String password,
            @NotBlank @Size(max = 100) String name,
            @NotBlank @Size(max = 200) String storeName) {
    }

    public record LoginRequest(
            @NotBlank @Email String email,
            @NotBlank String password) {
    }

    public record RefreshRequest(@NotBlank String refreshToken) {
    }

    public record LogoutRequest(String refreshToken) {
    }

    public record UserResponse(Long id, String email, String name, String storeName) {
        public static UserResponse from(User user) {
            return new UserResponse(user.getId(), user.getEmail(), user.getName(), user.getStoreName());
        }
    }

    public record TokenResponse(String accessToken, String refreshToken, UserResponse user) {
    }
}
