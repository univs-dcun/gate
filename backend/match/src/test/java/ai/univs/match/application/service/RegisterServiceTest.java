package ai.univs.match.application.service;

import ai.univs.match.application.input.DescriptorDetail;
import ai.univs.match.domain.entity.Branch;
import ai.univs.match.domain.entity.Descriptor;
import ai.univs.match.infrastructure.persistence.BranchRepository;
import ai.univs.match.infrastructure.persistence.DescriptorRepository;
import ai.univs.match.shared.exception.CustomFaceMatcherException;
import ai.univs.match.shared.web.enums.ErrorType;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.sql.SQLException;
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
            when(branchRepository.saveAndFlush(any(Branch.class))).thenAnswer(inv -> inv.getArgument(0));
        }

        @Test
        @DisplayName("새 브랜치를 생성하여 저장한다")
        void whenBranchNotFound_thenSavesNewBranch() {
            registerService.register(BRANCH_NAME, FACE_ID, base64Descriptor);

            ArgumentCaptor<Branch> captor = ArgumentCaptor.forClass(Branch.class);
            verify(branchRepository).saveAndFlush(captor.capture());
            assertThat(captor.getValue().getBranchName()).isEqualTo(BRANCH_NAME);
        }

        @Test
        @DisplayName("새 브랜치에 descriptor를 저장한다")
        void whenBranchNotFound_thenSavesDescriptorWithNewBranch() {
            registerService.register(BRANCH_NAME, FACE_ID, base64Descriptor);

            ArgumentCaptor<Descriptor> captor = ArgumentCaptor.forClass(Descriptor.class);
            verify(descriptorRepository).saveAndFlush(captor.capture());
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

            verify(descriptorRepository, never()).saveAndFlush(any());
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

                verify(descriptorRepository).saveAndFlush(any(Descriptor.class));
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
                verify(descriptorRepository).saveAndFlush(captor.capture());
                assertThat(captor.getValue().getFaceId()).isEqualTo(FACE_ID);
                assertThat(captor.getValue().getBranch()).isEqualTo(existingBranch);
            }

            @Test
            @DisplayName("저장되는 descriptor의 버전이 descriptorDetail의 spec 버전과 일치한다")
            void whenSaving_thenDescriptorVersionMatchesSpec() {
                when(descriptorRepository.countByBranch(existingBranch)).thenReturn(0);

                registerService.register(BRANCH_NAME, FACE_ID, base64Descriptor);

                ArgumentCaptor<Descriptor> captor = ArgumentCaptor.forClass(Descriptor.class);
                verify(descriptorRepository).saveAndFlush(captor.capture());
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

                verify(descriptorRepository, never()).saveAndFlush(any());
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

            verify(branchRepository, never()).saveAndFlush(any());
        }
    }

    // -------------------------------------------------------------------------
    // UG-340: 동시 등록 — 사전 조회를 함께 통과한 두 번째 저장은 제약이 막는다
    // -------------------------------------------------------------------------

    private static DataIntegrityViolationException 제약_위반(String constraint) {
        return new DataIntegrityViolationException("could not execute statement",
                new ConstraintViolationException("duplicate key", new SQLException(
                        "ERROR: duplicate key value violates unique constraint \"" + constraint + "\""), constraint));
    }

    @Nested
    @DisplayName("UG-340: 동시 등록")
    class ConcurrentRegistration {

        @BeforeEach
        void setUp() {
            when(branchRepository.findByBranchName(BRANCH_NAME)).thenReturn(Optional.of(existingBranch));
            when(descriptorRepository.findByFaceIdAndBranch(FACE_ID, existingBranch)).thenReturn(Optional.empty());
            when(descriptorRepository.countByBranch(existingBranch)).thenReturn(0);
        }

        @Test
        @DisplayName("같은 faceId 가 먼저 저장됐으면(유니크 위반) 순차 요청과 같은 ALREADY_REGISTERED_DESCRIPTOR 로 거절한다")
        void 특징점_유니크_위반은_이미_등록됨() {
            when(descriptorRepository.saveAndFlush(any(Descriptor.class)))
                    .thenThrow(제약_위반("uk_descriptor_branch_face"));

            assertThatThrownBy(() -> registerService.register(BRANCH_NAME, FACE_ID, base64Descriptor))
                    .isInstanceOf(CustomFaceMatcherException.class)
                    .extracting(ex -> ((CustomFaceMatcherException) ex).getErrorType())
                    .isEqualTo(ErrorType.ALREADY_REGISTERED_DESCRIPTOR);
        }

        /** NOT NULL·길이 초과 같은 다른 위반을 "이미 등록됨" 으로 바꾸면 프로그래밍 오류가 비즈니스 거절로 숨는다. */
        @Test
        @DisplayName("다른 제약 위반은 바꾸지 않고 그대로 던진다")
        void 다른_위반은_그대로() {
            DataIntegrityViolationException other = 제약_위반("descriptor_face_id_not_null");
            when(descriptorRepository.saveAndFlush(any(Descriptor.class))).thenThrow(other);

            assertThatThrownBy(() -> registerService.register(BRANCH_NAME, FACE_ID, base64Descriptor)).isSameAs(other);
        }
    }

    @Nested
    @DisplayName("UG-340: 새 브랜치 동시 생성")
    class ConcurrentBranchCreation {

        @BeforeEach
        void setUp() {
            when(branchRepository.findByBranchName(BRANCH_NAME)).thenReturn(Optional.empty());
        }

        /**
         * 다른 요청이 같은 브랜치를 먼저 만들었다. 이 트랜잭션은 이미 오류 상태라 이어 갈 수 없다 — 재시도하면
         * 정상 경로를 탄다. 서버 오류 유형이라 gate 는 "결과를 모른다" 로 다룬다(UG-338).
         */
        @Test
        @DisplayName("브랜치 이름 유니크 위반은 INTERNAL_SERVER_ERROR 로 돌려주고 특징점은 저장하지 않는다")
        void 브랜치_경합은_서버_오류() {
            when(branchRepository.saveAndFlush(any(Branch.class))).thenThrow(제약_위반("uk_branch_branch_name"));

            assertThatThrownBy(() -> registerService.register(BRANCH_NAME, FACE_ID, base64Descriptor))
                    .isInstanceOf(CustomFaceMatcherException.class)
                    .extracting(ex -> ((CustomFaceMatcherException) ex).getErrorType())
                    .isEqualTo(ErrorType.INTERNAL_SERVER_ERROR);
            verify(descriptorRepository, never()).saveAndFlush(any());
        }

        @Test
        @DisplayName("브랜치 저장의 다른 위반은 그대로 던진다")
        void 브랜치_다른_위반은_그대로() {
            DataIntegrityViolationException other = 제약_위반("branch_name_not_null");
            when(branchRepository.saveAndFlush(any(Branch.class))).thenThrow(other);

            assertThatThrownBy(() -> registerService.register(BRANCH_NAME, FACE_ID, base64Descriptor)).isSameAs(other);
        }
    }

    static String createBase64Descriptor(int version) {
        byte[] bytes = new byte[520];
        bytes[4] = (byte) version;
        return Base64.getEncoder().encodeToString(bytes);
    }
}
