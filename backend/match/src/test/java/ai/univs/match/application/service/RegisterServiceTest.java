package ai.univs.match.application.service;

import ai.univs.match.application.input.DescriptorDetail;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("RegisterService")
@ExtendWith(MockitoExtension.class)
class RegisterServiceTest {

    @Mock
    private BranchRepository branchRepository;

    @Mock
    private DescriptorRepository descriptorRepository;

    @Mock
    private DuplicateService duplicateService;

    @InjectMocks
    private RegisterService registerService;

    private static final String BRANCH_NAME = "testBranch";
    private static final String FACE_ID = "face-001";
    private String base64Descriptor;
    private DescriptorDetail descriptorDetail;
    private Branch existingBranch;

    @BeforeEach
    void setUp() {
        base64Descriptor = createBase64Descriptor(59);
        descriptorDetail = DescriptorDetail.from(base64Descriptor);

        existingBranch = Branch.builder()
                .id(1L)
                .branchName(BRANCH_NAME)
                .build();
    }

    // -------------------------------------------------------------------------
    // 브랜치가 존재하지 않는 경우
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("브랜치가 존재하지 않을 때")
    class WhenBranchDoesNotExist {

        @BeforeEach
        void setUp() {
            when(branchRepository.findByBranchName(BRANCH_NAME)).thenReturn(Optional.empty());
            when(branchRepository.save(any(Branch.class))).thenAnswer(inv -> inv.getArgument(0));
        }

        @Test
        @DisplayName("새 브랜치를 생성하여 저장한다")
        void whenBranchNotFound_thenSavesNewBranch() {
            registerService.register(BRANCH_NAME, FACE_ID, base64Descriptor);

            ArgumentCaptor<Branch> captor = ArgumentCaptor.forClass(Branch.class);
            verify(branchRepository).save(captor.capture());
            assertThat(captor.getValue().getBranchName()).isEqualTo(BRANCH_NAME);
        }

        @Test
        @DisplayName("새 브랜치에 descriptor를 저장한다")
        void whenBranchNotFound_thenSavesDescriptorWithNewBranch() {
            registerService.register(BRANCH_NAME, FACE_ID, base64Descriptor);

            ArgumentCaptor<Descriptor> captor = ArgumentCaptor.forClass(Descriptor.class);
            verify(descriptorRepository).save(captor.capture());
            assertThat(captor.getValue().getFaceId()).isEqualTo(FACE_ID);
        }

        @Test
        @DisplayName("중복 검사를 수행하지 않는다")
        void whenBranchNotFound_thenNeverChecksDuplicate() {
            registerService.register(BRANCH_NAME, FACE_ID, base64Descriptor);

            verify(duplicateService, never()).checkDuplicateDescriptor(any(), any(), any(), any(Boolean.class));
        }
    }

    // -------------------------------------------------------------------------
    // 브랜치가 존재하는 경우
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("브랜치가 존재할 때")
    class WhenBranchExists {

        @BeforeEach
        void setUp() {
            when(branchRepository.findByBranchName(BRANCH_NAME)).thenReturn(Optional.of(existingBranch));
        }

        @Test
        @DisplayName("동일한 faceId가 이미 등록되어 있으면 ALREADY_REGISTERED_DESCRIPTOR 예외를 던진다")
        void whenFaceIdAlreadyRegistered_thenThrowException() {
            when(descriptorRepository.findByFaceIdAndBranch(FACE_ID, existingBranch))
                    .thenReturn(Optional.of(new Descriptor()));

            assertThatThrownBy(() -> registerService.register(BRANCH_NAME, FACE_ID, base64Descriptor))
                    .isInstanceOf(CustomFaceMatcherException.class)
                    .satisfies(ex ->
                            assertThat(((CustomFaceMatcherException) ex).getErrorType())
                                    .isEqualTo(ErrorType.ALREADY_REGISTERED_DESCRIPTOR));
        }

        @Test
        @DisplayName("동일한 faceId가 이미 등록되어 있으면 descriptor를 저장하지 않는다")
        void whenFaceIdAlreadyRegistered_thenNeverSavesDescriptor() {
            when(descriptorRepository.findByFaceIdAndBranch(FACE_ID, existingBranch))
                    .thenReturn(Optional.of(new Descriptor()));

            assertThatThrownBy(() -> registerService.register(BRANCH_NAME, FACE_ID, base64Descriptor));

            verify(descriptorRepository, never()).save(any());
        }

        @Nested
        @DisplayName("faceId가 신규일 때")
        class WhenFaceIdIsNew {

            @BeforeEach
            void setUp() {
                when(descriptorRepository.findByFaceIdAndBranch(FACE_ID, existingBranch))
                        .thenReturn(Optional.empty());
            }

            @Test
            @DisplayName("브랜치에 등록된 descriptor가 없으면 중복 검사를 수행하지 않는다")
            void whenBranchIsEmpty_thenSkipsDuplicateCheck() {
                when(descriptorRepository.countByBranch(existingBranch)).thenReturn(0);

                registerService.register(BRANCH_NAME, FACE_ID, base64Descriptor);

                verify(duplicateService, never()).checkDuplicateDescriptor(any(), any(), any(), any(Boolean.class));
            }

            @Test
            @DisplayName("브랜치에 등록된 descriptor가 없어도 descriptor는 저장한다")
            void whenBranchIsEmpty_thenSavesDescriptor() {
                when(descriptorRepository.countByBranch(existingBranch)).thenReturn(0);

                registerService.register(BRANCH_NAME, FACE_ID, base64Descriptor);

                verify(descriptorRepository).save(any(Descriptor.class));
            }

            @Test
            @DisplayName("브랜치에 기존 descriptor가 있으면 올바른 인자로 중복 검사를 수행한다")
            void whenBranchHasDescriptors_thenChecksDuplicate() {
                when(descriptorRepository.countByBranch(existingBranch)).thenReturn(3);

                registerService.register(BRANCH_NAME, FACE_ID, base64Descriptor);

                // DescriptorDetail은 byte[] 필드를 포함한 record라 equals()가 참조 동일성 기반
                // → ArgumentCaptor로 실제 전달된 인자의 필드를 직접 검증
                ArgumentCaptor<DescriptorDetail> detailCaptor = ArgumentCaptor.forClass(DescriptorDetail.class);
                verify(duplicateService).checkDuplicateDescriptor(
                        eq(existingBranch), detailCaptor.capture(), isNull(), eq(false));

                assertThat(detailCaptor.getValue().descriptorSpec())
                        .isEqualTo(descriptorDetail.descriptorSpec());
            }

            @Test
            @DisplayName("중복 검사를 통과하면 descriptor를 저장한다")
            void whenDuplicateCheckPasses_thenSavesDescriptor() {
                when(descriptorRepository.countByBranch(existingBranch)).thenReturn(3);

                registerService.register(BRANCH_NAME, FACE_ID, base64Descriptor);

                ArgumentCaptor<Descriptor> captor = ArgumentCaptor.forClass(Descriptor.class);
                verify(descriptorRepository).save(captor.capture());
                assertThat(captor.getValue().getFaceId()).isEqualTo(FACE_ID);
                assertThat(captor.getValue().getBranch()).isEqualTo(existingBranch);
            }

            @Test
            @DisplayName("저장되는 descriptor의 버전이 descriptorDetail의 spec 버전과 일치한다")
            void whenSaving_thenDescriptorVersionMatchesSpec() {
                when(descriptorRepository.countByBranch(existingBranch)).thenReturn(0);

                registerService.register(BRANCH_NAME, FACE_ID, base64Descriptor);

                ArgumentCaptor<Descriptor> captor = ArgumentCaptor.forClass(Descriptor.class);
                verify(descriptorRepository).save(captor.capture());
                assertThat(captor.getValue().getDescriptorVersion())
                        .isEqualTo(descriptorDetail.descriptorSpec().getVersion());
            }

            @Test
            @DisplayName("중복 검사에서 ALREADY_REGISTERED_DESCRIPTOR가 발생하면 그대로 전파된다")
            void whenDuplicateCheckFails_thenPropagatesException() {
                when(descriptorRepository.countByBranch(existingBranch)).thenReturn(3);
                doThrow(new CustomFaceMatcherException(ErrorType.ALREADY_REGISTERED_DESCRIPTOR))
                        .when(duplicateService)
                        .checkDuplicateDescriptor(any(), any(), any(), any(Boolean.class));

                assertThatThrownBy(() -> registerService.register(BRANCH_NAME, FACE_ID, base64Descriptor))
                        .isInstanceOf(CustomFaceMatcherException.class)
                        .satisfies(ex ->
                                assertThat(((CustomFaceMatcherException) ex).getErrorType())
                                        .isEqualTo(ErrorType.ALREADY_REGISTERED_DESCRIPTOR));
            }

            @Test
            @DisplayName("중복 검사 실패 시 descriptor를 저장하지 않는다")
            void whenDuplicateCheckFails_thenNeverSavesDescriptor() {
                when(descriptorRepository.countByBranch(existingBranch)).thenReturn(3);
                doThrow(new CustomFaceMatcherException(ErrorType.ALREADY_REGISTERED_DESCRIPTOR))
                        .when(duplicateService)
                        .checkDuplicateDescriptor(any(), any(), any(), any(Boolean.class));

                assertThatThrownBy(() -> registerService.register(BRANCH_NAME, FACE_ID, base64Descriptor));

                verify(descriptorRepository, never()).save(any());
            }
        }
    }

    // -------------------------------------------------------------------------
    // 브랜치 저장 여부 검증
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("브랜치 저장 동작")
    class BranchSaveBehavior {

        @Test
        @DisplayName("브랜치가 이미 존재하면 branchRepository.save를 호출하지 않는다")
        void whenBranchExists_thenNeverSavesBranch() {
            when(branchRepository.findByBranchName(BRANCH_NAME)).thenReturn(Optional.of(existingBranch));
            when(descriptorRepository.findByFaceIdAndBranch(FACE_ID, existingBranch))
                    .thenReturn(Optional.empty());
            when(descriptorRepository.countByBranch(existingBranch)).thenReturn(0);

            registerService.register(BRANCH_NAME, FACE_ID, base64Descriptor);

            verify(branchRepository, never()).save(any());
        }
    }

    static String createBase64Descriptor(int version) {
        byte[] bytes = new byte[520];
        bytes[4] = (byte) version;
        return Base64.getEncoder().encodeToString(bytes);
    }
}
