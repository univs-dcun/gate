package ai.univs.match.application.usecase;

import ai.univs.match.application.input.DescriptorDetail;
import ai.univs.match.application.input.UpdateInput;
import ai.univs.match.application.result.MatchResult;
import ai.univs.match.application.service.DuplicateService;
import ai.univs.match.domain.entity.Branch;
import ai.univs.match.domain.entity.Descriptor;
import ai.univs.match.infrastructure.persistence.BranchRepository;
import ai.univs.match.infrastructure.persistence.DescriptorRepository;
import ai.univs.match.shared.exception.CustomFaceMatcherException;
import ai.univs.match.shared.web.enums.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Base64;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("UpdateUseCase")
@ExtendWith(MockitoExtension.class)
class UpdateUseCaseTest {

    @Mock
    private BranchRepository branchRepository;

    @Mock
    private DescriptorRepository descriptorRepository;

    @Mock
    private DuplicateService duplicateService;

    @InjectMocks
    private UpdateUseCase updateUseCase;

    private static final String BRANCH_NAME = "testBranch";
    private static final String FACE_ID = "face-001";

    private Branch branch;
    private String base64Descriptor;
    private UpdateInput input;

    @BeforeEach
    void setUp() {
        base64Descriptor = createBase64Descriptor(59);
        branch = Branch.builder().id(1L).branchName(BRANCH_NAME).build();
        input = new UpdateInput(BRANCH_NAME, FACE_ID, base64Descriptor);
    }

    @Nested
    @DisplayName("브랜치 조회")
    class BranchLookup {

        @Test
        @DisplayName("브랜치가 존재하지 않으면 EMPTY_GALLERY 예외를 던진다")
        void whenBranchNotFound_thenThrowEmptyGallery() {
            when(branchRepository.findByBranchName(BRANCH_NAME)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> updateUseCase.execute(input))
                    .isInstanceOf(CustomFaceMatcherException.class)
                    .satisfies(ex -> assertThat(((CustomFaceMatcherException) ex).getErrorType())
                            .isEqualTo(ErrorType.EMPTY_GALLERY));
        }

        @Test
        @DisplayName("브랜치가 없으면 특징점 조회 및 중복 검사를 수행하지 않는다")
        void whenBranchNotFound_thenNeverProceedsFurther() {
            when(branchRepository.findByBranchName(BRANCH_NAME)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> updateUseCase.execute(input));

            verify(descriptorRepository, never()).findByFaceIdAndBranch(any(), any());
            verify(duplicateService, never()).checkDuplicateDescriptor(any(), any(), anyString(), anyBoolean());
        }
    }

    @Nested
    @DisplayName("특징점 조회")
    class DescriptorLookup {

        @BeforeEach
        void setUp() {
            when(branchRepository.findByBranchName(BRANCH_NAME)).thenReturn(Optional.of(branch));
        }

        @Test
        @DisplayName("faceId에 해당하는 특징점이 없으면 INVALID_FACE_ID 예외를 던진다")
        void whenDescriptorNotFound_thenThrowInvalidFaceId() {
            when(descriptorRepository.findByFaceIdAndBranch(FACE_ID, branch)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> updateUseCase.execute(input))
                    .isInstanceOf(CustomFaceMatcherException.class)
                    .satisfies(ex -> assertThat(((CustomFaceMatcherException) ex).getErrorType())
                            .isEqualTo(ErrorType.INVALID_FACE_ID));
        }

        @Test
        @DisplayName("특징점이 없으면 중복 검사를 수행하지 않는다")
        void whenDescriptorNotFound_thenNeverChecksDuplicate() {
            when(descriptorRepository.findByFaceIdAndBranch(FACE_ID, branch)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> updateUseCase.execute(input));

            verify(duplicateService, never()).checkDuplicateDescriptor(any(), any(), anyString(), anyBoolean());
        }
    }

    @Nested
    @DisplayName("중복 검사")
    class DuplicateCheck {

        private Descriptor existingDescriptor;

        @BeforeEach
        void setUp() {
            existingDescriptor = Descriptor.builder().id(10L).faceId(FACE_ID).branch(branch).build();
            when(branchRepository.findByBranchName(BRANCH_NAME)).thenReturn(Optional.of(branch));
            when(descriptorRepository.findByFaceIdAndBranch(FACE_ID, branch))
                    .thenReturn(Optional.of(existingDescriptor));
        }

        @Test
        @DisplayName("중복 검사를 isUpdate=true, faceId와 함께 호출한다")
        void whenExecuted_thenCallsDuplicateCheckWithIsUpdateTrue() {
            updateUseCase.execute(input);

            verify(duplicateService).checkDuplicateDescriptor(
                    eq(branch), any(DescriptorDetail.class), eq(FACE_ID), eq(true));
        }

        @Test
        @DisplayName("중복 검사 실패 시 예외를 그대로 전파한다")
        void whenDuplicateCheckFails_thenPropagatesException() {
            doThrow(new CustomFaceMatcherException(ErrorType.ALREADY_REGISTERED_DESCRIPTOR))
                    .when(duplicateService)
                    .checkDuplicateDescriptor(any(), any(), anyString(), anyBoolean());

            assertThatThrownBy(() -> updateUseCase.execute(input))
                    .isInstanceOf(CustomFaceMatcherException.class)
                    .satisfies(ex -> assertThat(((CustomFaceMatcherException) ex).getErrorType())
                            .isEqualTo(ErrorType.ALREADY_REGISTERED_DESCRIPTOR));
        }
    }

    @Nested
    @DisplayName("특징점 업데이트")
    class DescriptorUpdate {

        private Descriptor spyDescriptor;

        @BeforeEach
        void setUp() {
            Descriptor original = Descriptor.builder()
                    .id(10L)
                    .faceId(FACE_ID)
                    .branch(branch)
                    .descriptorVersion(59)
                    .build();
            spyDescriptor = spy(original);

            when(branchRepository.findByBranchName(BRANCH_NAME)).thenReturn(Optional.of(branch));
            when(descriptorRepository.findByFaceIdAndBranch(FACE_ID, branch))
                    .thenReturn(Optional.of(spyDescriptor));
        }

        @Test
        @DisplayName("중복 검사 통과 후 특징점 데이터를 업데이트한다")
        void whenDuplicateCheckPasses_thenUpdatesDescriptor() {
            updateUseCase.execute(input);

            verify(spyDescriptor).updateDescriptor(any(byte[].class), any(byte[].class), any(byte[].class));
        }

        @Test
        @DisplayName("업데이트 시 input descriptor를 파싱한 데이터로 갱신한다")
        void whenUpdated_thenDescriptorFieldsAreUpdatedWithParsedData() {
            DescriptorDetail expected = DescriptorDetail.from(base64Descriptor);

            updateUseCase.execute(input);

            assertThat(spyDescriptor.getDescriptor()).isEqualTo(expected.descriptor());
            assertThat(spyDescriptor.getDescriptorType()).isEqualTo(expected.descriptorType());
            assertThat(spyDescriptor.getDescriptorBody()).isEqualTo(expected.descriptorBody());
        }

        @Test
        @DisplayName("실제 조회된 branch의 branchName을 MatchResult에 담아 반환한다")
        void whenUpdated_thenReturnsMatchResultWithBranchNameFromBranch() {
            MatchResult result = updateUseCase.execute(input);

            assertThat(result.branchName()).isEqualTo(branch.getBranchName());
        }

        @Test
        @DisplayName("실제 조회된 descriptor의 faceId를 MatchResult에 담아 반환한다")
        void whenUpdated_thenReturnsMatchResultWithFaceIdFromDescriptor() {
            MatchResult result = updateUseCase.execute(input);

            assertThat(result.faceId()).isEqualTo(spyDescriptor.getFaceId());
        }

        @Test
        @DisplayName("중복 검사 실패 시 특징점 데이터를 업데이트하지 않는다")
        void whenDuplicateCheckFails_thenNeverUpdatesDescriptor() {
            doThrow(new CustomFaceMatcherException(ErrorType.ALREADY_REGISTERED_DESCRIPTOR))
                    .when(duplicateService)
                    .checkDuplicateDescriptor(any(), any(), anyString(), anyBoolean());

            assertThatThrownBy(() -> updateUseCase.execute(input));

            verify(spyDescriptor, never()).updateDescriptor(any(), any(), any());
        }
    }

    static String createBase64Descriptor(int version) {
        byte[] bytes = new byte[520];
        bytes[4] = (byte) version;
        return Base64.getEncoder().encodeToString(bytes);
    }
}
