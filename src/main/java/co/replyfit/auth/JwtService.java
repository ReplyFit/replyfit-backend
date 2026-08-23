package co.replyfit.auth;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@Service
public class JwtService {

    private final SecretKey key;
    private final long accessValidityMillis;

    public JwtService(@Value("${replyfit.jwt.secret}") String secret,
                      @Value("${replyfit.jwt.access-token-validity-minutes}") long accessMinutes) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessValidityMillis = accessMinutes * 60_000;
    }

    public String createAccessToken(Long userId, String email) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("email", email)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + accessValidityMillis))
                .signWith(key)
                .compact();
    }

    /** 유효하지 않으면 null 반환 */
    public AuthUser parse(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return new AuthUser(Long.valueOf(claims.getSubject()), claims.get("email", String.class));
        } catch (JwtException | IllegalArgumentException e) {
            return null;
        }
    }
}
