package ai.univs.gate.support.api_key;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Base64;

@Component
public class ApiKeyGenerator {

    @Value("${api-key.prefix}")
    private String apiKeyPrefix;

    @Value("${api-key.length}")
    private int apiKeyLength;

    private static final SecureRandom secureRandom = new SecureRandom();
    private static final Base64.Encoder base64Encoder = Base64.getUrlEncoder().withoutPadding();

    /**
     * API Key 생성 (nid_djka_형식)
     */
    public String generateApiKey() {
        byte[] randomBytes = new byte[apiKeyLength];
        secureRandom.nextBytes(randomBytes);
        String randomString = base64Encoder.encodeToString(randomBytes);
        return apiKeyPrefix + randomString.substring(0, apiKeyLength);
    }

    /**
     * Secret Key 생성
     */
    public String generateSecretKey() {
        byte[] randomBytes = new byte[48];
        secureRandom.nextBytes(randomBytes);
        return base64Encoder.encodeToString(randomBytes);
    }
}