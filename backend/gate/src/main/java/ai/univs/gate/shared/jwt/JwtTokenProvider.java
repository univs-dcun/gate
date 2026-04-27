package ai.univs.gate.shared.jwt;

import ai.univs.gate.shared.exception.InvalidAccessTokenException;
import ai.univs.gate.shared.exception.TokenExpiredException;
import ai.univs.gate.shared.web.enums.ErrorType;
import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.Date;
import java.util.UUID;

@Component
public class JwtTokenProvider {

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${jwt.create-user-token-expiry}")
    private long createUserTokenExpiry; // 5분 (300000ms)

    // Create User Token 생성
    public String createQrCodeTokenForCreatingUser(String accountId, String apiKey) {
        String jti = UUID.randomUUID().toString();
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + createUserTokenExpiry);

        return Jwts.builder()
                .setId(jti)
                .setSubject(accountId)
                .claim("apiKey", apiKey)
                .claim("type", "user_registration")
                .setIssuedAt(now)
                .setExpiration(expiryDate)
                .signWith(getSignKey())
                .compact();
        }

    // Verify Token 생성
    public String createQrCodeTokenForVerifyByFaceId(String accountId, String apiKey, String faceId) {
        String jti = UUID.randomUUID().toString();
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + createUserTokenExpiry);

        return Jwts.builder()
                .setId(jti)
                .setSubject(accountId)
                .claim("apiKey", apiKey)
                .claim("faceId", faceId)
                .claim("type", "faceId_verification")
                .setIssuedAt(now)
                .setExpiration(expiryDate)
                .signWith(getSignKey())
                .compact();
    }

    // Identify Token 생성
    public String createQrCodeTokenForIdentify(String accountId, String apiKey) {
        String jti = UUID.randomUUID().toString();
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + createUserTokenExpiry);

        return Jwts.builder()
                .setId(jti)
                .setSubject(accountId)
                .claim("apiKey", apiKey)
                .claim("type", "faceId_identify")
                .setIssuedAt(now)
                .setExpiration(expiryDate)
                .signWith(getSignKey())
                .compact();
    }

    // Token 검증 및 파싱
    public Claims validateToken(String token) {
        try {
            return Jwts
                    .parserBuilder()
                    .setSigningKey(getSignKey())
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
        } catch (ExpiredJwtException e) {
            throw new TokenExpiredException(ErrorType.EXPIRATION_TOKEN);
        } catch (UnsupportedJwtException | MalformedJwtException e) {
            throw new InvalidAccessTokenException(ErrorType.INVALID_TOKEN);
        }
    }
    // Account ID 추출
    public Long getAccountIdFromToken(String token) {
        Claims claims = validateToken(token);
        return Long.parseLong(claims.getSubject());
    }

    // API Key 추출
    public String getApiKeyFromToken(String token) {
        Claims claims = validateToken(token);
        return claims.get("apiKey", String.class);
    }

    // faceId 추출
    public String getFaceIdFromToken(String token) {
        Claims claims = validateToken(token);
        return claims.get("faceId", String.class);
    }

    private Key getSignKey() {
        byte[] keyBytes = Decoders.BASE64.decode(jwtSecret);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
