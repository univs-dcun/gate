package ai.univs.auth.application.service;

import ai.univs.auth.application.exception.*;
import ai.univs.auth.application.result.RefreshTokenResult;
import ai.univs.auth.domain.entity.Account;
import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.UUID;

@Component
public class JwtTokenProvider {

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${jwt.access-token-expiry}")
    private long accessTokenExpiry;

    @Value("${jwt.refresh-token-expiry}")
    private long refreshTokenExpiry;

    public String createAccessToken(Account account) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + accessTokenExpiry);

        return Jwts.builder()
                .setSubject(account.getAccountId().toString())
                .claim("email", account.getEmail())
                .claim("type", "access")
                .setIssuedAt(now)
                .setExpiration(expiryDate)
                .signWith(getSignKey())
                .compact();
    }

    public RefreshTokenResult createRefreshToken(Long accountId) {
        String jti = UUID.randomUUID().toString();
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + refreshTokenExpiry);

        String token = Jwts.builder()
                .setId(jti)
                .setSubject(accountId.toString())
                .claim("type", "refresh")
                .setIssuedAt(now)
                .setExpiration(expiryDate)
                .signWith(getSignKey())
                .compact();

        LocalDateTime expiryDateTime = expiryDate.toInstant()
                .atZone(ZoneOffset.UTC)
                .toLocalDateTime();

        return new RefreshTokenResult(token, jti, expiryDateTime);
    }

    public Claims validateToken(String token) {
        try {
            return Jwts.parserBuilder()
                    .setSigningKey(getSignKey())
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
        } catch (ExpiredJwtException e) {
            throw new TokenExpiredException();
        } catch (UnsupportedJwtException | MalformedJwtException e) {
            throw new InvalidAccessTokenException();
        }
    }

    public void validateAccessToken(String token) {
        Claims claims = validateToken(token);
        String type = claims.get("type", String.class);
        if (!"access".equals(type)) {
            throw new InvalidTokenTypeException();
        }
    }

    public void validateRefreshToken(String token) {
        Claims claims;
        try {
            claims = validateToken(token);
        } catch (Exception e) {
            throw new InvalidRefreshTokenException();
        }
        String type = claims.get("type", String.class);
        if (!"refresh".equals(type)) {
            throw new InvalidRefreshTokenTypeException();
        }
    }

    public Long getAccountIdFromToken(String token) {
        Claims claims = validateToken(token);
        return Long.parseLong(claims.getSubject());
    }

    public String getEmailFromToken(String token) {
        Claims claims = validateToken(token);
        return claims.get("email", String.class);
    }

    public String getJtiFromToken(String token) {
        Claims claims = validateToken(token);
        return claims.getId();
    }

    private Key getSignKey() {
        byte[] keyBytes = Decoders.BASE64.decode(jwtSecret);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
