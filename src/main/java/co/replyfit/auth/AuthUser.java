package co.replyfit.auth;

/** SecurityContext에 저장되는 인증 주체 */
public record AuthUser(Long id, String email) {
}
