package ai.univs.gate.support.file;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.Base64;

/**
 * 사진 URL 서명 (UG-386).
 *
 * <p>사진 API({@code /api/v1/file})는 게이트웨이 인증 밖에 있고, 콘솔은 {@code <img src>} 로 그려서 헤더를 실을 수 없다.
 * 그래서 URL 자체에 만료 시각과 서명을 붙인다 — 주소가 새도 유효 시간(기본 10분)이 지나면 열리지 않는다.
 *
 * <p>서명 대상은 {@code filePath · projectId · exp} 셋이다. projectId 를 묶어 두는 이유는 파일 API 가 내려주기 직전에
 * <b>그 프로젝트의 현재 동의</b>를 다시 보기 위해서다 — 파일 경로로 소유 프로젝트를 역조회하지 않아도 된다.
 * 서버만 서명할 수 있으므로 projectId 를 바꿔 다른 프로젝트의 동의로 우회할 수 없다.
 *
 * <p>비밀키는 기본값이 없다(UG-307 원칙). 회전할 때는 새 키를 {@code secret} 에, 이전 키를 {@code previous-secret} 에
 * 잠시 둔다 — 바꾸기 직전에 나간 URL 이 유효 시간 동안 계속 열린다.
 */
@Component
public class FileUrlSigner implements ImageUrls.Signer {

    private static final String ALGORITHM = "HmacSHA256";
    /** 같은 비밀키를 다른 용도에 쓰더라도 서명이 섞이지 않게 앞에 붙인다. */
    private static final String DOMAIN = "gate-file-url-v1";
    /** 운영 비밀키로 쓰기에 너무 짧은 값을 막는다 — 실수로 넣은 자리 표시자(예: secretkey)로 기동하지 않게. */
    static final int MIN_SECRET_LENGTH = 32;

    private final byte[] currentKey;
    private final byte[] previousKey;
    private final long ttlSeconds;
    private final Clock clock;

    @Autowired
    public FileUrlSigner(@Value("${file.signed-url.secret}") String secret,
                         @Value("${file.signed-url.previous-secret:}") String previousSecret,
                         @Value("${file.signed-url.ttl-seconds:600}") long ttlSeconds) {
        this(secret, previousSecret, ttlSeconds, Clock.systemUTC());
    }

    FileUrlSigner(String secret, String previousSecret, long ttlSeconds, Clock clock) {
        this.currentKey = requireKey(secret, "file.signed-url.secret");
        this.previousKey = StringUtils.hasText(previousSecret)
                ? requireKey(previousSecret, "file.signed-url.previous-secret")
                : null;
        if (ttlSeconds <= 0) {
            throw new IllegalStateException("file.signed-url.ttl-seconds 는 양수여야 한다: " + ttlSeconds);
        }
        this.ttlSeconds = ttlSeconds;
        this.clock = clock;
    }

    /** {@code filePath=…&pid=…&exp=…&sig=…} — 파일 API 의 쿼리 문자열. */
    @Override
    public String query(String filePath, long projectId) {
        long exp = clock.instant().getEpochSecond() + ttlSeconds;
        return "filePath=" + filePath + "&pid=" + projectId + "&exp=" + exp + "&sig=" + sign(currentKey, filePath, projectId, exp);
    }

    /** 서명이 맞고 만료 전이면 true. 이전 키로 만든 서명도 받는다. */
    public boolean verify(String filePath, long projectId, long exp, String sig) {
        if (!StringUtils.hasText(sig) || exp < clock.instant().getEpochSecond()) {
            return false;
        }
        byte[] given = sig.getBytes(StandardCharsets.US_ASCII);
        if (MessageDigest.isEqual(given, sign(currentKey, filePath, projectId, exp).getBytes(StandardCharsets.US_ASCII))) {
            return true;
        }
        return previousKey != null
                && MessageDigest.isEqual(given, sign(previousKey, filePath, projectId, exp).getBytes(StandardCharsets.US_ASCII));
    }

    private static String sign(byte[] key, String filePath, long projectId, long exp) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(key, ALGORITHM));
            String payload = DOMAIN + '\n' + filePath + '\n' + projectId + '\n' + exp;
            return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("사진 URL 서명을 만들지 못했다", e);
        }
    }

    private static byte[] requireKey(String secret, String name) {
        if (!StringUtils.hasText(secret) || secret.length() < MIN_SECRET_LENGTH) {
            throw new IllegalStateException(name + " 는 " + MIN_SECRET_LENGTH + "자 이상이어야 한다 (UG-386)");
        }
        return secret.getBytes(StandardCharsets.UTF_8);
    }
}
