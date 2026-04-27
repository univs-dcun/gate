package ai.univs.match.application.usecase;

import ai.univs.match.application.result.MatchResult;
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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("DeleteUseCase")
@ExtendWith(MockitoExtension.class)
class DeleteUseCaseTest {

    @Mock
    private BranchRepository branchRepository;

    @Mock
    private DescriptorRepository descriptorRepository;

    @InjectMocks
    private DeleteUseCase deleteUseCase;

    private static final String BRANCH_NAME = "testBranch";
    private static final String FACE_ID = "face-001";

    private Branch branch;
    private Descriptor descriptor;

    @BeforeEach
    void setUp() {
        branch = Branch.builder().id(1L).branchName(BRANCH_NAME).build();
        descriptor = Descriptor.builder().id(10L).faceId(FACE_ID).branch(branch).build();
    }

    @Nested
    @DisplayName("브랜치 조회")
    class BranchLookup {

        @Test
        @DisplayName("브랜치가 존재하지 않으면 EMPTY_GALLERY 예외를 던진다")
        void whenBranchNotFound_thenThrowEmptyGallery() {
            when(branchRepository.findByBranchName(BRANCH_NAME)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> deleteUseCase.execute(BRANCH_NAME, FACE_ID))
                    .isInstanceOf(CustomFaceMatcherException.class)
                    .satisfies(ex -> assertThat(((CustomFaceMatcherException) ex).getErrorType())
                            .isEqualTo(ErrorType.EMPTY_GALLERY));
        }

        @Test
        @DisplayName("브랜치가 존재하지 않으면 descriptor 조회를 하지 않는다")
        void whenBranchNotFound_thenNeverLooksUpDescriptor() {
            when(branchRepository.findByBranchName(BRANCH_NAME)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> deleteUseCase.execute(BRANCH_NAME, FACE_ID));

            verify(descriptorRepository, never()).findByFaceIdAndBranch(any(), any());
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

            assertThatThrownBy(() -> deleteUseCase.execute(BRANCH_NAME, FACE_ID))
                    .isInstanceOf(CustomFaceMatcherException.class)
                    .satisfies(ex -> assertThat(((CustomFaceMatcherException) ex).getErrorType())
                            .isEqualTo(ErrorType.INVALID_FACE_ID));
        }

        @Test
        @DisplayName("faceId에 해당하는 특징점이 없으면 delete를 호출하지 않는다")
        void whenDescriptorNotFound_thenNeverDeletesDescriptor() {
            when(descriptorRepository.findByFaceIdAndBranch(FACE_ID, branch)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> deleteUseCase.execute(BRANCH_NAME, FACE_ID));

            verify(descriptorRepository, never()).delete(any());
        }
    }

    @Nested
    @DisplayName("삭제 성공")
    class DeleteSuccess {

        @BeforeEach
        void setUp() {
            when(branchRepository.findByBranchName(BRANCH_NAME)).thenReturn(Optional.of(branch));
            when(descriptorRepository.findByFaceIdAndBranch(FACE_ID, branch)).thenReturn(Optional.of(descriptor));
        }

        @Test
        @DisplayName("브랜치와 특징점이 모두 존재하면 해당 특징점을 삭제한다")
        void whenAllFound_thenDeletesDescriptor() {
            deleteUseCase.execute(BRANCH_NAME, FACE_ID);

            verify(descriptorRepository).delete(descriptor);
        }

        @Test
        @DisplayName("삭제 시 조회된 브랜치로 특징점을 조회한다")
        void whenExecuted_thenFindsDescriptorByFaceIdAndBranch() {
            deleteUseCase.execute(BRANCH_NAME, FACE_ID);

            verify(descriptorRepository).findByFaceIdAndBranch(FACE_ID, branch);
        }

        @Test
        @DisplayName("실제 조회된 branch의 branchName을 MatchResult에 담아 반환한다")
        void whenDeleted_thenReturnsMatchResultWithBranchNameFromBranch() {
            MatchResult result = deleteUseCase.execute(BRANCH_NAME, FACE_ID);

            assertThat(result.branchName()).isEqualTo(branch.getBranchName());
        }

        @Test
        @DisplayName("실제 조회된 descriptor의 faceId를 MatchResult에 담아 반환한다")
        void whenDeleted_thenReturnsMatchResultWithFaceIdFromDescriptor() {
            MatchResult result = deleteUseCase.execute(BRANCH_NAME, FACE_ID);

            assertThat(result.faceId()).isEqualTo(descriptor.getFaceId());
        }
    }
}
