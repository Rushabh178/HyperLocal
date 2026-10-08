package com.HyperLocalSystem.app.security;

import com.HyperLocalSystem.app.auth.dto.AuthTokens;
import com.HyperLocalSystem.app.auth.entity.RefreshToken;
import com.HyperLocalSystem.app.auth.repository.RefreshTokenRepository;
import com.HyperLocalSystem.app.common.exception.ApiException;
import com.HyperLocalSystem.app.config.AppProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Issues short-lived JWT access tokens plus opaque refresh tokens.
 * Refresh tokens rotate on every use; presenting an already-rotated token is treated as theft
 * and revokes every session of that user.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TokenService {

    public static final String ROLE_CLAIM = "role";

    private final JwtEncoder jwtEncoder;
    private final RefreshTokenRepository refreshTokens;
    private final AppProperties properties;

    @Transactional
    public AuthTokens issue(UUID userId, Role role) {
        Instant now = Instant.now();
        AppProperties.Jwt jwt = properties.jwt();

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(jwt.issuer())
                .subject(userId.toString())
                .issuedAt(now)
                .expiresAt(now.plus(jwt.accessTokenTtl()))
                .claim(ROLE_CLAIM, role.name())
                .build();
        String accessToken = jwtEncoder
                .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();

        String refreshToken = Hashing.randomToken();
        refreshTokens.save(new RefreshToken(userId, role, Hashing.sha256Hex(refreshToken), now.plus(jwt.refreshTokenTtl())));

        return new AuthTokens(accessToken, refreshToken, "Bearer", jwt.accessTokenTtl().toSeconds());
    }

    /**
     * Exchanges a refresh token for a new token pair.
     *
     * @param accountCheck runs before issuing, e.g. to reject blocked accounts; it may throw ApiException
     */
    @Transactional(noRollbackFor = ApiException.class)
    public AuthTokens rotate(String rawRefreshToken, Role expectedRole, Consumer<UUID> accountCheck) {
        Instant now = Instant.now();
        RefreshToken stored = refreshTokens.findByTokenHash(Hashing.sha256Hex(rawRefreshToken))
                .filter(token -> token.getRole() == expectedRole)
                .orElseThrow(TokenService::invalidRefreshToken);

        if (stored.getRevokedAt() != null) {
            log.warn("Reuse of rotated refresh token for {} {}; revoking all sessions", expectedRole, stored.getUserId());
            refreshTokens.revokeAllForUser(stored.getUserId(), expectedRole, now);
            throw invalidRefreshToken();
        }
        if (stored.getExpiresAt().isBefore(now)) {
            throw invalidRefreshToken();
        }

        stored.setRevokedAt(now);
        accountCheck.accept(stored.getUserId());
        return issue(stored.getUserId(), expectedRole);
    }

    /** Logout of one device. Unknown tokens are ignored so logout is always idempotent. */
    @Transactional
    public void revoke(String rawRefreshToken, Role role) {
        refreshTokens.findByTokenHash(Hashing.sha256Hex(rawRefreshToken))
                .filter(token -> token.getRole() == role && token.getRevokedAt() == null)
                .ifPresent(token -> token.setRevokedAt(Instant.now()));
    }

    /** Logout of every device, e.g. after a password change. */
    @Transactional
    public void revokeAll(UUID userId, Role role) {
        refreshTokens.revokeAllForUser(userId, role, Instant.now());
    }

    private static ApiException invalidRefreshToken() {
        return ApiException.unauthorized("INVALID_REFRESH_TOKEN", "Refresh token is invalid or expired. Please sign in again.");
    }
}
