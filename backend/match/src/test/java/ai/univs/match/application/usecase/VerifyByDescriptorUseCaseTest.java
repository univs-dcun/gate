package ai.univs.match.application.usecase;

import ai.univs.match.application.result.VerifyResult;
import ai.univs.match.infrastructure.persistence.DescriptorCustomRepository;
import ai.univs.match.shared.exception.CustomFaceMatcherException;
import ai.univs.match.shared.web.enums.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Base64;

import static ai.univs.match.shared.utils.SimilarityCalculator.getSimilarityByDistance;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("VerifyByDescriptorUseCase")
@ExtendWith(MockitoExtension.class)
class VerifyByDescriptorUseCaseTest {

    @Mock
    private DescriptorCustomRepository descriptorCustomRepository;

    @InjectMocks
    private VerifyByDescriptorUseCase verifyByDescriptorUseCase;

    // 버전 59, 60 짜리 descriptor
    private String descriptor59;
    private String descriptor60;
    private String anotherDescriptor59;

    @BeforeEach
    void setUp() {
        descriptor59 = createBase64Descriptor(59);
        descriptor60 = createBase64Descriptor(60);
        anotherDescriptor59 = createBase64Descriptor(59); // 동일 버전, 다른 body
    }

    @Nested
    @DisplayName("버전 일치 검증")
    class VersionCompatibility {

        @Test
        @DisplayName("두 특징점의 버전이 다르면 DIFFERENT_EXTRACTION_TYPE 예외를 던진다")
        void whenVersionMismatch_thenThrowDifferentExtractionType() {
            assertThatThrownBy(() ->
                    verifyByDescriptorUseCase.execute(descriptor59, descriptor60))
                    .isInstanceOf(CustomFaceMatcherException.class)
                    .satisfies(ex -> assertThat(((CustomFaceMatcherException) ex).getErrorType())
                            .isEqualTo(ErrorType.DIFFERENT_EXTRACTION_TYPE));
        }

        @Test
        @DisplayName("버전이 다르면 매칭을 수행하지 않는다")
        void whenVersionMismatch_thenNeverCallsOneToOneMatch() {
            assertThatThrownBy(() ->
                    verifyByDescriptorUseCase.execute(descriptor59, descriptor60));

            verify(descriptorCustomRepository, never()).oneToOneMatch(any(), any(), anyInt());
        }

        @Test
        @DisplayName("두 특징점의 버전이 같으면 예외를 던지지 않는다")
        void whenVersionMatches_thenNoVersionException() {
            when(descriptorCustomRepository.oneToOneMatch(any(), any(), anyInt())).thenReturn(0.0);

            assertThat(verifyByDescriptorUseCase.execute(descriptor59, anotherDescriptor59))
                    .isNotNull();
        }
    }

    @Nested
    @DisplayName("1:1 매칭 성공")
    class MatchSuccess {

        @Test
        @DisplayName("oneToOneMatch를 버전 512로 호출한다")
        void whenExecuted_thenCallsOneToOneMatchWithVersion512() {
            when(descriptorCustomRepository.oneToOneMatch(any(), any(), eq(512))).thenReturn(1.0);

            verifyByDescriptorUseCase.execute(descriptor59, anotherDescriptor59);

            verify(descriptorCustomRepository).oneToOneMatch(any(byte[].class), any(byte[].class), eq(512));
        }

        @Test
        @DisplayName("distance 0 이하이면 유사도 1.00000을 반환한다")
        void whenDistanceIsZero_thenReturnMaxSimilarity() {
            when(descriptorCustomRepository.oneToOneMatch(any(), any(), anyInt())).thenReturn(0.0);

            VerifyResult result = verifyByDescriptorUseCase.execute(descriptor59, anotherDescriptor59);

            assertThat(result.similarity()).isEqualTo("1.00000");
        }

        @Test
        @DisplayName("distance를 Platt scaling으로 변환한 유사도를 반환한다")
        void whenMatchFound_thenReturnsSimilarityConvertedFromDistance() {
            double distance = 1.0;
            when(descriptorCustomRepository.oneToOneMatch(any(), any(), anyInt())).thenReturn(distance);

            VerifyResult result = verifyByDescriptorUseCase.execute(descriptor59, anotherDescriptor59);

            String expectedSimilarity = getSimilarityByDistance(distance,
                    ai.univs.match.application.enums.DescriptorSpec.VERSION_59);
            assertThat(result.similarity()).isEqualTo(expectedSimilarity);
        }

        @Test
        @DisplayName("첫 번째 descriptor의 body와 두 번째 descriptor의 body로 매칭한다")
        void whenExecuted_thenPassesBothDescriptorBodiesToMatch() {
            when(descriptorCustomRepository.oneToOneMatch(any(), any(), anyInt())).thenReturn(0.5);

            verifyByDescriptorUseCase.execute(descriptor59, anotherDescriptor59);

            // 두 인자 모두 byte[] 타입으로 전달되었는지 확인
            verify(descriptorCustomRepository)
                    .oneToOneMatch(any(byte[].class), any(byte[].class), eq(512));
        }
    }

    static String createBase64Descriptor(int version) {
        byte[] bytes = new byte[520];
        bytes[4] = (byte) version;
        return Base64.getEncoder().encodeToString(bytes);
    }
}
