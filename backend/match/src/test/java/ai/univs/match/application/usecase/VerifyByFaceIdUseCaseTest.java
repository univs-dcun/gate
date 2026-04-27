package ai.univs.match.application.usecase;

import ai.univs.match.application.input.VerifyByFaceIdInput;
import ai.univs.match.application.result.VerifyResult;
import ai.univs.match.domain.entity.Branch;
import ai.univs.match.domain.entity.Descriptor;
import ai.univs.match.infrastructure.persistence.BranchRepository;
import ai.univs.match.infrastructure.persistence.DescriptorCustomRepository;
import ai.univs.match.infrastructure.persistence.DescriptorRepository;
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
import java.util.Optional;

import static ai.univs.match.shared.utils.SimilarityCalculator.getSimilarityByDistance;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("VerifyByFaceIdUseCase")
@ExtendWith(MockitoExtension.class)
class VerifyByFaceIdUseCaseTest {

    @Mock
    private BranchRepository branchRepository;

    @Mock
    private DescriptorRepository descriptorRepository;

    @Mock
    private DescriptorCustomRepository descriptorCustomRepository;

    @InjectMocks
    private VerifyByFaceIdUseCase verifyByFaceIdUseCase;

    private static final String BRANCH_NAME = "testBranch";
    private static final String FACE_ID = "face-001";

    private Branch branch;
    // 입력 특징점: 버전 59
    private final String inputDescriptor59 = createBase64Descriptor(59);
    // 등록된 특징점 body (512 bytes)
    private final byte[] storedDescriptorBody = new byte[512];

    @BeforeEach
    void setUp() {
        branch = Branch.builder().id(1L).branchName(BRANCH_NAME).build();
    }

    @Nested
    @DisplayName("브랜치 조회")
    class BranchLookup {

        @Test
        @DisplayName("브랜치가 존재하지 않으면 EMPTY_GALLERY 예외를 던진다")
        void whenBranchNotFound_thenThrowEmptyGallery() {
            when(branchRepository.findByBranchName(BRANCH_NAME)).thenReturn(Optional.empty());
            var input = new VerifyByFaceIdInput(BRANCH_NAME, FACE_ID, inputDescriptor59);

            assertThatThrownBy(() -> verifyByFaceIdUseCase.execute(input))
                    .isInstanceOf(CustomFaceMatcherException.class)
                    .satisfies(ex -> assertThat(((CustomFaceMatcherException) ex).getErrorType())
                            .isEqualTo(ErrorType.EMPTY_GALLERY));
        }

        @Test
        @DisplayName("브랜치가 없으면 이후 조회 및 매칭을 수행하지 않는다")
        void whenBranchNotFound_thenNeverProceedsFurther() {
            when(branchRepository.findByBranchName(BRANCH_NAME)).thenReturn(Optional.empty());
            var input = new VerifyByFaceIdInput(BRANCH_NAME, FACE_ID, inputDescriptor59);

            assertThatThrownBy(() -> verifyByFaceIdUseCase.execute(input));

            verify(descriptorRepository, never()).findByFaceIdAndBranch(any(), any());
            verify(descriptorCustomRepository, never()).oneToOneMatch(any(), any(), anyInt());
        }
    }

    @Nested
    @DisplayName("faceId 기반 특징점 조회")
    class DescriptorByFaceIdLookup {

        @BeforeEach
        void setUp() {
            when(branchRepository.findByBranchName(BRANCH_NAME)).thenReturn(Optional.of(branch));
        }

        @Test
        @DisplayName("faceId에 해당하는 특징점이 없으면 INVALID_FACE_ID 예외를 던진다")
        void whenDescriptorNotFoundByFaceId_thenThrowInvalidFaceId() {
            when(descriptorRepository.findByFaceIdAndBranch(FACE_ID, branch)).thenReturn(Optional.empty());
            var input = new VerifyByFaceIdInput(BRANCH_NAME, FACE_ID, inputDescriptor59);

            assertThatThrownBy(() -> verifyByFaceIdUseCase.execute(input))
                    .isInstanceOf(CustomFaceMatcherException.class)
                    .satisfies(ex -> assertThat(((CustomFaceMatcherException) ex).getErrorType())
                            .isEqualTo(ErrorType.INVALID_FACE_ID));
        }

        @Test
        @DisplayName("faceId에 해당하는 특징점이 없으면 매칭을 수행하지 않는다")
        void whenDescriptorNotFoundByFaceId_thenNeverCallsMatch() {
            when(descriptorRepository.findByFaceIdAndBranch(FACE_ID, branch)).thenReturn(Optional.empty());
            var input = new VerifyByFaceIdInput(BRANCH_NAME, FACE_ID, inputDescriptor59);

            assertThatThrownBy(() -> verifyByFaceIdUseCase.execute(input));

            verify(descriptorCustomRepository, never()).oneToOneMatch(any(), any(), anyInt());
        }
    }

    @Nested
    @DisplayName("특징점 버전 일치 검증")
    class VersionCompatibility {

        @BeforeEach
        void setUp() {
            when(branchRepository.findByBranchName(BRANCH_NAME)).thenReturn(Optional.of(branch));
        }

        @Test
        @DisplayName("입력 특징점과 등록된 특징점의 버전이 다르면 DIFFERENT_EXTRACTION_TYPE 예외를 던진다")
        void whenVersionMismatch_thenThrowDifferentExtractionType() {
            // 등록된 특징점: 버전 60, 입력: 버전 59
            Descriptor storedDescriptor = Descriptor.builder()
                    .id(10L).faceId(FACE_ID).branch(branch)
                    .descriptorVersion(60)
                    .descriptorBody(storedDescriptorBody)
                    .build();
            when(descriptorRepository.findByFaceIdAndBranch(FACE_ID, branch))
                    .thenReturn(Optional.of(storedDescriptor));
            var input = new VerifyByFaceIdInput(BRANCH_NAME, FACE_ID, inputDescriptor59); // 버전 59

            assertThatThrownBy(() -> verifyByFaceIdUseCase.execute(input))
                    .isInstanceOf(CustomFaceMatcherException.class)
                    .satisfies(ex -> assertThat(((CustomFaceMatcherException) ex).getErrorType())
                            .isEqualTo(ErrorType.DIFFERENT_EXTRACTION_TYPE));
        }

        @Test
        @DisplayName("버전이 다르면 매칭을 수행하지 않는다")
        void whenVersionMismatch_thenNeverCallsMatch() {
            Descriptor storedDescriptor = Descriptor.builder()
                    .id(10L).faceId(FACE_ID).branch(branch)
                    .descriptorVersion(60)
                    .descriptorBody(storedDescriptorBody)
                    .build();
            when(descriptorRepository.findByFaceIdAndBranch(FACE_ID, branch))
                    .thenReturn(Optional.of(storedDescriptor));
            var input = new VerifyByFaceIdInput(BRANCH_NAME, FACE_ID, inputDescriptor59);

            assertThatThrownBy(() -> verifyByFaceIdUseCase.execute(input));

            verify(descriptorCustomRepository, never()).oneToOneMatch(any(), any(), anyInt());
        }
    }

    @Nested
    @DisplayName("1:1 매칭 성공")
    class MatchSuccess {

        private Descriptor storedDescriptor;

        @BeforeEach
        void setUp() {
            // 등록된 특징점: 버전 59 (입력과 동일)
            storedDescriptor = Descriptor.builder()
                    .id(10L).faceId(FACE_ID).branch(branch)
                    .descriptorVersion(59)
                    .descriptorBody(storedDescriptorBody)
                    .build();

            when(branchRepository.findByBranchName(BRANCH_NAME)).thenReturn(Optional.of(branch));
            when(descriptorRepository.findByFaceIdAndBranch(FACE_ID, branch))
                    .thenReturn(Optional.of(storedDescriptor));
        }

        @Test
        @DisplayName("oneToOneMatch를 버전 512로 호출한다")
        void whenExecuted_thenCallsOneToOneMatchWithVersion512() {
            when(descriptorCustomRepository.oneToOneMatch(any(), any(), eq(512))).thenReturn(1.0);
            var input = new VerifyByFaceIdInput(BRANCH_NAME, FACE_ID, inputDescriptor59);

            verifyByFaceIdUseCase.execute(input);

            verify(descriptorCustomRepository).oneToOneMatch(any(byte[].class), any(byte[].class), eq(512));
        }

        @Test
        @DisplayName("distance 0 이하이면 유사도 1.00000을 반환한다")
        void whenDistanceIsZero_thenReturnMaxSimilarity() {
            when(descriptorCustomRepository.oneToOneMatch(any(), any(), anyInt())).thenReturn(0.0);
            var input = new VerifyByFaceIdInput(BRANCH_NAME, FACE_ID, inputDescriptor59);

            VerifyResult result = verifyByFaceIdUseCase.execute(input);

            assertThat(result.similarity()).isEqualTo("1.00000");
        }

        @Test
        @DisplayName("distance를 Platt scaling으로 변환한 유사도를 반환한다")
        void whenMatchFound_thenReturnsSimilarityConvertedFromDistance() {
            double distance = 1.0;
            when(descriptorCustomRepository.oneToOneMatch(any(), any(), anyInt())).thenReturn(distance);
            var input = new VerifyByFaceIdInput(BRANCH_NAME, FACE_ID, inputDescriptor59);

            VerifyResult result = verifyByFaceIdUseCase.execute(input);

            String expectedSimilarity = getSimilarityByDistance(distance,
                    ai.univs.match.application.enums.DescriptorSpec.VERSION_59);
            assertThat(result.similarity()).isEqualTo(expectedSimilarity);
        }

        @Test
        @DisplayName("등록된 특징점의 body를 타겟으로 매칭을 수행한다")
        void whenExecuted_thenUsesStoredDescriptorBodyAsTarget() {
            when(descriptorCustomRepository.oneToOneMatch(any(), any(), anyInt())).thenReturn(1.0);
            var input = new VerifyByFaceIdInput(BRANCH_NAME, FACE_ID, inputDescriptor59);

            verifyByFaceIdUseCase.execute(input);

            verify(descriptorCustomRepository).oneToOneMatch(
                    any(byte[].class), eq(storedDescriptorBody), anyInt());
        }
    }

    static String createBase64Descriptor(int version) {
        byte[] bytes = new byte[520];
        bytes[4] = (byte) version;
        return Base64.getEncoder().encodeToString(bytes);
    }
}
