package ai.univs.gate.shared.utils;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.util.StringUtils;

import java.util.Base64;

/**
 * base64 특징점 문자열을 게이트 계층에서 검증한다 (UG-279).
 *
 * <p>검증하지 않으면 match-server 의 {@code DescriptorDetail.from} 에서
 * {@code Base64.getDecoder().decode()} 가 {@code IllegalArgumentException} 을 던지거나
 * 8바이트 헤더를 잘라내는 {@code Arrays.copyOfRange} 가
 * {@code ArrayIndexOutOfBoundsException} 을 던진다. match-server 의 GlobalExceptionHandler 는
 * {@code Exception} 을 포괄 처리하므로 둘 다 500 이 되어 클라이언트는 자기 입력이 잘못됐다는 사실을
 * 알 수 없다.
 *
 * <p>특징점 버전 지원 여부는 검증하지 않는다. match-server 가
 * {@code NOT_SUPPORTED_VERSION} 으로 이미 정상적인 비즈니스 오류를 반환하고, 지원 버전 목록을
 * 두 서비스에 중복 정의하면 버전 추가 시 게이트가 먼저 막는 사고가 난다.
 */
public class DescriptorValidator implements ConstraintValidator<ValidDescriptor, String> {

    /**
     * 8바이트 헤더 + 최소 1바이트 본문. match-server 는 헤더의 5번째 바이트를 버전으로 읽고
     * 9번째 바이트부터를 본문으로 자른다.
     */
    private static final int MIN_DECODED_BYTES = 9;

    /**
     * 방어적 상한. 실제 특징점은 이보다 훨씬 짧다. 1:N 매칭은 갤러리 전수 비교라 입력 크기가
     * 그대로 비용이 되므로 상한이 없으면 안 된다.
     */
    private static final int MAX_ENCODED_LENGTH = 4096;

    @Override
    public boolean isValid(String descriptor, ConstraintValidatorContext context) {
        if (!StringUtils.hasText(descriptor)) {
            return false;
        }

        if (descriptor.length() > MAX_ENCODED_LENGTH) {
            return false;
        }

        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(descriptor);
        } catch (IllegalArgumentException e) {
            return false;
        }

        return decoded.length >= MIN_DECODED_BYTES;
    }
}
