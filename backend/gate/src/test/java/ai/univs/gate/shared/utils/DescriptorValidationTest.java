package ai.univs.gate.shared.utils;

import ai.univs.gate.modules.feature.api.dto.face.CreateFaceFeatureByDescriptorRequestDTO;
import ai.univs.gate.modules.feature.api.dto.face.IdentifyByDescriptorRequestDTO;
import ai.univs.gate.modules.feature.api.dto.face.VerifyByDescriptorRequestDTO;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Base64;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UG-279: descriptor 는 게이트 계층에서 정확한 길이로 끊어야 한다.
 *
 * <p>느슨한 검증(예: "9바이트 이상")이 위험한 이유는 500 이 아니라 <b>갤러리 오염</b>이다.
 * match-server 의 {@code RegisterService} 는 본문 길이를 검사하지 않고 {@code descriptor_body} 에
 * 영구 저장하며, 매칭 경로들은 비교 바이트 수를 {@code 512} 로 하드코딩한다. 짧은 본문이 한 행이라도
 * 저장되면 그 프로젝트의 <b>모든 정상 1:N</b> 이 그 행에 대해 잘못된 크기로 비교를 수행한다.
 * descriptor 기반 등록 API 는 클라이언트가 준 값이 갤러리에 영구 저장되는 최초의 경로다.
 *
 * <p>검증기 클래스만 직접 호출하면 애노테이션이 DTO 에 실제로 붙어 있는지는 확인되지 않는다.
 * 그래서 Bean Validation 을 통째로 돌려 DTO 필드에서 위반이 나오는지도 함께 본다.
 */
@DisplayName("UG-279: descriptor 는 정확히 520바이트여야 한다")
class DescriptorValidationTest {

    /**
     * 실제 특징점과 같은 모양의 픽스처 — 8바이트 헤더 + 512바이트 본문. 5번째 바이트는 버전이고
     * {@code DescriptorSpec.VERSION_59} 를 쓴다. 이전 픽스처는 9바이트 + 버전 0 이어서,
     * 검증기는 통과하지만 match-server 가 {@code NOT_SUPPORTED_VERSION} 으로 거부하는
     * end-to-end 로는 절대 성립하지 않는 값이었다.
     */
    private static final String VALID = encode(descriptorBytes(520));

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void 검증기_생성() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void 검증기_해제() {
        factory.close();
    }

    private static byte[] descriptorBytes(int length) {
        byte[] bytes = new byte[length];
        if (length > 4) bytes[4] = 59;
        return bytes;
    }

    private static String encode(byte[] bytes) {
        return Base64.getEncoder().encodeToString(bytes);
    }

    @Test
    @DisplayName("전제 확인 — 520바이트는 base64 696자로 인코딩된다")
    void 픽스처_전제() {
        assertEquals(696, VALID.length(),
                "픽스처가 기대 길이를 벗어났다 — 검증기 상수와 어긋나면 나머지 테스트가 무의미해진다");
    }

    @Nested
    @DisplayName("검증기 단독 동작")
    class 검증기 {

        private final DescriptorValidator sut = new DescriptorValidator();

        @Test
        @DisplayName("정확히 520바이트면 통과한다")
        void 정상() {
            assertTrue(sut.isValid(VALID, null));
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = { "   " })
        @DisplayName("비어 있으면 거부한다")
        void 공백(String descriptor) {
            assertFalse(sut.isValid(descriptor, null));
        }

        @Test
        @DisplayName("길이는 맞지만 base64 가 아니면 거부한다")
        void 비base64() {
            assertFalse(sut.isValid("@".repeat(696), null),
                    "match-server 의 Base64.getDecoder().decode 에서 500 이 된다");
        }

        @ParameterizedTest
        @ValueSource(ints = { 1, 8, 9, 519, 521, 1040 })
        @DisplayName("520바이트가 아니면 길고 짧고를 가리지 않고 거부한다")
        void 길이_불일치(int length) {
            assertFalse(sut.isValid(encode(descriptorBytes(length)), null),
                    length + "바이트가 통과했다 — 갤러리에 저장되면 해당 프로젝트의 1:N 이 통째로 망가진다");
        }

        @Test
        @DisplayName("회귀 방지 — 짧은 본문 + 유효한 버전 바이트 조합을 막는다")
        void 갤러리_오염_PoC() {
            // 디코딩 결과 00 00 00 00 3B 00 00 00 00 (9바이트).
            // 5번째 바이트가 0x3B(59)라 match-server 의 DescriptorSpec.fromVersion 을 통과하고,
            // 본문은 1바이트만 남는다. 예전 ">= 9바이트" 규칙은 이 값을 통과시켰다.
            String poc = "AAAAADsAAAAA";

            assertEquals(9, Base64.getDecoder().decode(poc).length, "PoC 전제: 9바이트로 디코딩된다");
            assertEquals(59, Base64.getDecoder().decode(poc)[4], "PoC 전제: 버전 바이트가 59다");
            assertFalse(sut.isValid(poc, null),
                    "본문 1바이트 특징점이 갤러리에 영구 저장되는 경로가 열린다");
        }
    }

    @Nested
    @DisplayName("DTO 에 애노테이션이 실제로 붙어 있다")
    class DTO {

        @Test
        @DisplayName("등록 요청")
        void 등록() {
            assertSingleDescriptorViolation(validator.validate(
                    new CreateFaceFeatureByDescriptorRequestDTO("not base64!", null)));
            assertTrue(validator.validate(
                    new CreateFaceFeatureByDescriptorRequestDTO(VALID, "tx-1")).isEmpty());
        }

        @Test
        @DisplayName("1:N 요청")
        void 매칭() {
            assertSingleDescriptorViolation(validator.validate(
                    new IdentifyByDescriptorRequestDTO("not base64!", null)));
            assertTrue(validator.validate(
                    new IdentifyByDescriptorRequestDTO(VALID, "tx-1")).isEmpty());
        }

        @Test
        @DisplayName("1:1 요청 — 기존 엔드포인트에도 적용됐다 (두 필드 모두)")
        void 확인() {
            Set<ConstraintViolation<VerifyByDescriptorRequestDTO>> violations =
                    validator.validate(new VerifyByDescriptorRequestDTO("zz", "zz", null));

            assertEquals(2, violations.size(),
                    "descriptor 와 targetDescriptor 둘 다 걸려야 한다 — 예전에는 @NotBlank 라 \"zz\" 가 통과해 500 이 났다");
            violations.forEach(v -> assertEquals("INVALID_DESCRIPTOR", v.getMessage()));

            assertTrue(validator.validate(
                    new VerifyByDescriptorRequestDTO(VALID, VALID, "tx-1")).isEmpty());
        }

        @Test
        @DisplayName("1:1 요청 — transactionUuid 37자 이상은 400 으로 끊는다")
        void 확인_트랜잭션_길이() {
            var request = new VerifyByDescriptorRequestDTO(VALID, VALID, "a".repeat(37));

            var violations = validator.validate(request);

            assertEquals(1, violations.size(),
                    "예전에는 @Length 가 없어 VARCHAR(36) INSERT 실패로 500 이 났다");
            assertEquals("INVALID_TRANSACTION_UUID_LENGTH", violations.iterator().next().getMessage());
        }

        private <T> void assertSingleDescriptorViolation(Set<ConstraintViolation<T>> violations) {
            assertEquals(1, violations.size(), "descriptor 위반이 잡히지 않았다 — 애노테이션 확인");
            assertEquals("INVALID_DESCRIPTOR", violations.iterator().next().getMessage());
        }
    }
}
