package ai.univs.gate.shared.utils;

import ai.univs.gate.modules.feature.api.dto.face.CreateFaceFeatureByDescriptorRequestDTO;
import ai.univs.gate.modules.feature.api.dto.face.IdentifyByDescriptorRequestDTO;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UG-279: descriptor 검증.
 *
 * <p>검증이 없으면 match-server 의 {@code DescriptorDetail.from} 이 터진다 — 비 base64 는
 * {@code IllegalArgumentException}, 8바이트 미만은 {@code ArrayIndexOutOfBoundsException}. 둘 다
 * match-server 의 {@code @ExceptionHandler(Exception.class)} 에 걸려 500 이 되므로, 클라이언트는
 * 자기 입력이 잘못됐다는 사실조차 알 수 없다. 게이트에서 400 으로 끊어야 한다.
 *
 * <p>검증기 클래스만 직접 호출하면 애노테이션이 DTO 에 실제로 붙어 있는지는 확인되지 않는다.
 * 그래서 Bean Validation 을 통째로 돌려 DTO 필드에서 위반이 나오는지를 본다.
 */
@DisplayName("UG-279: descriptor 는 게이트 계층에서 걸러야 한다")
class DescriptorValidationTest {

    /** 8바이트 헤더 + 1바이트 본문 — match-server 가 잘라 쓸 수 있는 최소 크기. */
    private static final String MIN_VALID = Base64.getEncoder().encodeToString(new byte[9]);

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

    @Nested
    @DisplayName("검증기 단독 동작")
    class 검증기 {

        private final DescriptorValidator sut = new DescriptorValidator();

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = { "   " })
        @DisplayName("비어 있으면 거부한다")
        void 공백(String descriptor) {
            assertEquals(false, sut.isValid(descriptor, null));
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "not base64 at all!",
                "AAAA====",       // 잘못된 패딩
                "@@@@@@@@@@@@",
        })
        @DisplayName("base64 로 디코딩할 수 없으면 거부한다")
        void 비base64(String descriptor) {
            assertEquals(false, sut.isValid(descriptor, null),
                    "match-server 의 Base64.getDecoder().decode 에서 500 이 된다");
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "AA==",           // 1바이트
                "AAAAAAAAAAA=",   // 8바이트 — 헤더만 있고 본문이 없다
        })
        @DisplayName("디코딩 결과가 9바이트 미만이면 거부한다")
        void 너무_짧음(String descriptor) {
            assertEquals(false, sut.isValid(descriptor, null),
                    "match-server 의 Arrays.copyOfRange(legacy, 8, len) 에서 500 이 된다");
        }

        @Test
        @DisplayName("9바이트면 통과한다")
        void 최소_길이() {
            assertEquals(true, sut.isValid(MIN_VALID, null));
        }

        @Test
        @DisplayName("상한을 넘으면 거부한다 — 1:N 은 갤러리 전수 비교라 입력 크기가 곧 비용이다")
        void 상한_초과() {
            String tooLong = Base64.getEncoder().encodeToString(new byte[4096]);
            assertTrue(tooLong.length() > 4096, "테스트 전제: 상한(4096자)을 넘겨야 한다");
            assertEquals(false, sut.isValid(tooLong, null));
        }
    }

    @Nested
    @DisplayName("DTO 에 애노테이션이 실제로 붙어 있다")
    class DTO {

        @Test
        @DisplayName("등록 요청 — 잘못된 descriptor 는 INVALID_DESCRIPTOR 위반이 된다")
        void 등록() {
            var request = new CreateFaceFeatureByDescriptorRequestDTO("not base64!", null);

            Set<ConstraintViolation<CreateFaceFeatureByDescriptorRequestDTO>> violations =
                    validator.validate(request);

            assertEquals(1, violations.size(), "descriptor 위반이 잡히지 않았다 — 애노테이션 확인");
            assertEquals("INVALID_DESCRIPTOR", violations.iterator().next().getMessage());
        }

        @Test
        @DisplayName("1:N 요청 — 잘못된 descriptor 는 INVALID_DESCRIPTOR 위반이 된다")
        void 매칭() {
            var request = new IdentifyByDescriptorRequestDTO("not base64!", null);

            Set<ConstraintViolation<IdentifyByDescriptorRequestDTO>> violations =
                    validator.validate(request);

            assertEquals(1, violations.size(), "descriptor 위반이 잡히지 않았다 — 애노테이션 확인");
            assertEquals("INVALID_DESCRIPTOR", violations.iterator().next().getMessage());
        }

        @Test
        @DisplayName("정상 descriptor 는 위반이 없다")
        void 정상() {
            assertTrue(validator.validate(
                    new CreateFaceFeatureByDescriptorRequestDTO(MIN_VALID, "tx-1")).isEmpty());
            assertTrue(validator.validate(
                    new IdentifyByDescriptorRequestDTO(MIN_VALID, "tx-1")).isEmpty());
        }
    }
}
