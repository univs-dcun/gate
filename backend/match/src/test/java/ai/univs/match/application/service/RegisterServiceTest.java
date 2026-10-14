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
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.transaction.PlatformTransactionManager;

import java.sql.SQLException;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("RegisterService")
@ExtendWith(MockitoExtension.class)
class RegisterServiceTest {

    @Mock
    private BranchProvisioner branchProvisioner;

    @Mock
    private BranchRepository branchRepository;

    @Mock
    private DescriptorRepository descriptorRepository;

    @Mock
    private DuplicateService duplicateService;

    /** 기본값(false) 은 "재검사 통과 — 등록 유지" 다. */
    @Mock
    private RecentDuplicateCheck recentDuplicateCheck;

    /** 목이라 실제 트랜잭션은 없다 — 템플릿이 getTransaction·commit·rollback 을 부르는 순서만 본다. */
    @Mock
    private PlatformTransactionManager transactionManager;

    @InjectMocks
    private RegisterService registerService;

    private static final String BRANCH_NAME = "testBranch";
    private static final String FACE_ID = "face-001";
    private static final Long SAVED_ID = 501L;
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

        // 저장하면 id 가 붙는다(IDENTITY) — 커밋 뒤 재검사가 그 id 로 자기 행을 가리킨다 (UG-374)
        lenient().when(descriptorRepository.saveAndFlush(any(Descriptor.class)))
                .thenReturn(Descriptor.builder().id(SAVED_ID).build());
    }

    // -------------------------------------------------------------------------
    // UG-372: 트랜잭션 경계 — 브랜치를 먼저 정하고, 그다음 등록 트랜잭션을 연다
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("UG-372: 트랜잭션 경계")
    class TransactionBoundary {

        @Test
        @DisplayName("브랜치를 정한 뒤에 등록 트랜잭션을 열고, 그 안에서 특징점을 저장한다")
        void 브랜치_먼저_트랜잭션_나중() {
            when(branchProvisioner.findOrCreate(BRANCH_NAME)).thenReturn(existingBranch);
            when(branchRepository.getReferenceById(1L)).thenReturn(existingBranch);

            registerService.register(BRANCH_NAME, FACE_ID, base64Descriptor);

            InOrder order = inOrder(branchProvisioner, transactionManager, descriptorRepository);
            order.verify(branchProvisioner).findOrCreate(BRANCH_NAME);
            order.verify(transactionManager).getTransaction(any());
            order.verify(descriptorRepository).saveAndFlush(any(Descriptor.class));
            order.verify(transactionManager).commit(any());
        }

        /** 깨진 특징점으로 빈 브랜치를 만들지 않는다 — 예전(브랜치 생성 전에 해석)과 같은 순서. */
        @Test
        @DisplayName("특징점 형식이 깨졌으면 브랜치를 정하지도, 트랜잭션을 열지도 않는다")
        void 형식_오류는_브랜치_전에() {
            assertThatThrownBy(() -> registerService.register(BRANCH_NAME, FACE_ID, "not base64 !!"))
                    .isInstanceOf(IllegalArgumentException.class);

            verifyNoInteractions(branchProvisioner, transactionManager, descriptorRepository);
        }

        /** 분리된 엔티티를 물리지 않는다 — 등록 트랜잭션에서 id 로 참조를 다시 얻는다. */
        @Test
        @DisplayName("특징점은 브랜치 id 로 다시 얻은 참조에 묶인다")
        void 브랜치는_id_참조로() {
            Branch detached = Branch.builder().id(7L).branchName(BRANCH_NAME).build();
            Branch reference = Branch.builder().id(7L).branchName(BRANCH_NAME).build();
            when(branchProvisioner.findOrCreate(BRANCH_NAME)).thenReturn(detached);
            when(branchRepository.getReferenceById(7L)).thenReturn(reference);

            registerService.register(BRANCH_NAME, FACE_ID, base64Descriptor);

            ArgumentCaptor<Descriptor> captor = ArgumentCaptor.forClass(Descriptor.class);
            verify(descriptorRepository).saveAndFlush(captor.capture());
            assertThat(captor.getValue().getBranch()).isSameAs(reference);
            assertThat(captor.getValue().getFaceId()).isEqualTo(FACE_ID);
            verify(branchRepository, never()).saveAndFlush(any());
            verify(duplicateService, never()).checkDuplicateDescriptor(any(), any(), any(), any(Boolean.class));
        }

        @Test
        @DisplayName("거절(이미 등록됨)이면 등록 트랜잭션을 롤백한다")
        void 거절은_롤백() {
            when(branchProvisioner.findOrCreate(BRANCH_NAME)).thenReturn(existingBranch);
            when(branchRepository.getReferenceById(1L)).thenReturn(existingBranch);
            when(descriptorRepository.findByFaceIdAndBranch(FACE_ID, existingBranch))
                    .thenReturn(Optional.of(new Descriptor()));

            assertThatThrownBy(() -> registerService.register(BRANCH_NAME, FACE_ID, base64Descriptor))
                    .isInstanceOf(CustomFaceMatcherException.class);

            verify(transactionManager).rollback(any());
            verify(transactionManager, never()).commit(any());
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
            when(branchProvisioner.findOrCreate(BRANCH_NAME)).thenReturn(existingBranch);
            when(branchRepository.getReferenceById(1L)).thenReturn(existingBranch);
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
                when(descriptorRepository.existsByBranchAndDescriptorVersion(existingBranch, 59)).thenReturn(false);

                registerService.register(BRANCH_NAME, FACE_ID, base64Descriptor);

                verify(duplicateService, never()).checkDuplicateDescriptor(any(), any(), any(), any(Boolean.class));
            }

            @Test
            @DisplayName("브랜치에 등록된 descriptor가 없어도 descriptor는 저장한다")
            void whenBranchIsEmpty_thenSavesDescriptor() {
                when(descriptorRepository.existsByBranchAndDescriptorVersion(existingBranch, 59)).thenReturn(false);

                registerService.register(BRANCH_NAME, FACE_ID, base64Descriptor);

                verify(descriptorRepository).saveAndFlush(any(Descriptor.class));
            }

            @Test
            @DisplayName("브랜치에 기존 descriptor가 있으면 올바른 인자로 중복 검사를 수행한다")
            void whenBranchHasDescriptors_thenChecksDuplicate() {
                when(descriptorRepository.existsByBranchAndDescriptorVersion(existingBranch, 59)).thenReturn(true);

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
                when(descriptorRepository.existsByBranchAndDescriptorVersion(existingBranch, 59)).thenReturn(true);

                registerService.register(BRANCH_NAME, FACE_ID, base64Descriptor);

                ArgumentCaptor<Descriptor> captor = ArgumentCaptor.forClass(Descriptor.class);
                verify(descriptorRepository).saveAndFlush(captor.capture());
                assertThat(captor.getValue().getFaceId()).isEqualTo(FACE_ID);
                assertThat(captor.getValue().getBranch()).isEqualTo(existingBranch);
            }

            @Test
            @DisplayName("저장되는 descriptor의 버전이 descriptorDetail의 spec 버전과 일치한다")
            void whenSaving_thenDescriptorVersionMatchesSpec() {
                when(descriptorRepository.existsByBranchAndDescriptorVersion(existingBranch, 59)).thenReturn(false);

                registerService.register(BRANCH_NAME, FACE_ID, base64Descriptor);

                ArgumentCaptor<Descriptor> captor = ArgumentCaptor.forClass(Descriptor.class);
                verify(descriptorRepository).saveAndFlush(captor.capture());
                assertThat(captor.getValue().getDescriptorVersion())
                        .isEqualTo(descriptorDetail.descriptorSpec().getVersion());
            }

            @Test
            @DisplayName("중복 검사에서 ALREADY_REGISTERED_DESCRIPTOR가 발생하면 그대로 전파된다")
            void whenDuplicateCheckFails_thenPropagatesException() {
                when(descriptorRepository.existsByBranchAndDescriptorVersion(existingBranch, 59)).thenReturn(true);
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
                when(descriptorRepository.existsByBranchAndDescriptorVersion(existingBranch, 59)).thenReturn(true);
                doThrow(new CustomFaceMatcherException(ErrorType.ALREADY_REGISTERED_DESCRIPTOR))
                        .when(duplicateService)
                        .checkDuplicateDescriptor(any(), any(), any(), any(Boolean.class));

                assertThatThrownBy(() -> registerService.register(BRANCH_NAME, FACE_ID, base64Descriptor));

                verify(descriptorRepository, never()).saveAndFlush(any());
            }
        }
    }

    // -------------------------------------------------------------------------
    // UG-374: 같은 얼굴 동시 등록 — 커밋 뒤 재검사
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("UG-374: 커밋 뒤 재검사")
    class RecheckAfterCommit {

        @BeforeEach
        void setUp() {
            when(branchProvisioner.findOrCreate(BRANCH_NAME)).thenReturn(existingBranch);
            when(branchRepository.getReferenceById(1L)).thenReturn(existingBranch);
        }

        @Test
        @DisplayName("등록 트랜잭션이 커밋된 뒤에, 저장한 행의 id 로 재검사한다")
        void 커밋_뒤에_저장한_id_로() {
            registerService.register(BRANCH_NAME, FACE_ID, base64Descriptor);

            InOrder order = inOrder(transactionManager, recentDuplicateCheck);
            order.verify(transactionManager).commit(any());
            ArgumentCaptor<DescriptorDetail> detail = ArgumentCaptor.forClass(DescriptorDetail.class);
            order.verify(recentDuplicateCheck).withdrawIfDuplicated(eq(1L), eq(SAVED_ID), detail.capture());
            assertThat(detail.getValue().descriptorSpec()).isEqualTo(descriptorDetail.descriptorSpec());
        }

        @Test
        @DisplayName("재검사가 같은 얼굴을 보고 물렸으면 ALREADY_REGISTERED_DESCRIPTOR(MATCH-003) 에 상대 faceId 를 싣는다 (UG-378)")
        void 물렸으면_이미_등록됨() {
            when(recentDuplicateCheck.withdrawIfDuplicated(eq(1L), eq(SAVED_ID), any()))
                    .thenReturn(Optional.of(new RecentDuplicateCheck.Withdrawal("dup-face")));

            assertThatThrownBy(() -> registerService.register(BRANCH_NAME, FACE_ID, base64Descriptor))
                    .isInstanceOf(CustomFaceMatcherException.class)
                    .satisfies(ex -> {
                        CustomFaceMatcherException e = (CustomFaceMatcherException) ex;
                        assertThat(e.getErrorType()).isEqualTo(ErrorType.ALREADY_REGISTERED_DESCRIPTOR);
                        assertThat(e.getDetails()).containsExactly(Map.entry("faceId", "dup-face"));
                    });
        }

        @Test
        @DisplayName("물렸지만 상대를 모르면 details 없이 MATCH-003 (UG-378)")
        void 상대_모르면_details_없음() {
            when(recentDuplicateCheck.withdrawIfDuplicated(eq(1L), eq(SAVED_ID), any()))
                    .thenReturn(Optional.of(new RecentDuplicateCheck.Withdrawal(null)));

            assertThatThrownBy(() -> registerService.register(BRANCH_NAME, FACE_ID, base64Descriptor))
                    .isInstanceOf(CustomFaceMatcherException.class)
                    .satisfies(ex -> {
                        CustomFaceMatcherException e = (CustomFaceMatcherException) ex;
                        assertThat(e.getErrorType()).isEqualTo(ErrorType.ALREADY_REGISTERED_DESCRIPTOR);
                        assertThat(e.getDetails()).isNull();
                    });
        }

        @Test
        @DisplayName("재검사를 통과하면 그대로 성공한다")
        void 통과하면_성공() {
            when(recentDuplicateCheck.withdrawIfDuplicated(eq(1L), eq(SAVED_ID), any())).thenReturn(Optional.empty());

            registerService.register(BRANCH_NAME, FACE_ID, base64Descriptor);

            verify(transactionManager, never()).rollback(any());
        }

        @Test
        @DisplayName("등록이 거절되면(이미 있는 faceId) 재검사하지 않는다 — 지울 행이 없다")
        void 거절이면_재검사_없음() {
            when(descriptorRepository.findByFaceIdAndBranch(FACE_ID, existingBranch))
                    .thenReturn(Optional.of(new Descriptor()));

            assertThatThrownBy(() -> registerService.register(BRANCH_NAME, FACE_ID, base64Descriptor))
                    .isInstanceOf(CustomFaceMatcherException.class);

            verifyNoInteractions(recentDuplicateCheck);
        }

        /** 실제 PostgreSQL 에서 재현했다 — 같은 얼굴 동시 등록에서 비교 대상이 재검사로 지워지는 사이 */
        @Test
        @DisplayName("중복 검사 직전에 비교할 특징점이 사라지면(EMPTY_GALLERY) 중복 없음으로 보고 저장한다")
        void 사이에_비면_EMPTY_GALLERY_무시() {
            when(descriptorRepository.findByFaceIdAndBranch(FACE_ID, existingBranch)).thenReturn(Optional.empty());
            when(descriptorRepository.existsByBranchAndDescriptorVersion(existingBranch, 59)).thenReturn(true);
            doThrow(new CustomFaceMatcherException(ErrorType.EMPTY_GALLERY))
                    .when(duplicateService).checkDuplicateDescriptor(any(), any(), any(), any(Boolean.class));

            registerService.register(BRANCH_NAME, FACE_ID, base64Descriptor);

            verify(descriptorRepository).saveAndFlush(any(Descriptor.class));
        }

        @Test
        @DisplayName("중복 검사 직전에 비교할 특징점이 사라져 1:N 결과가 없어도(EmptyResultDataAccessException) 저장한다")
        void 사이에_비면_결과_없음_무시() {
            when(descriptorRepository.findByFaceIdAndBranch(FACE_ID, existingBranch)).thenReturn(Optional.empty());
            when(descriptorRepository.existsByBranchAndDescriptorVersion(existingBranch, 59)).thenReturn(true);
            doThrow(new EmptyResultDataAccessException(1))
                    .when(duplicateService).checkDuplicateDescriptor(any(), any(), any(), any(Boolean.class));

            registerService.register(BRANCH_NAME, FACE_ID, base64Descriptor);

            verify(descriptorRepository).saveAndFlush(any(Descriptor.class));
        }

        @Test
        @DisplayName("중복 검사의 다른 오류는 그대로 던진다 — 사라짐만 넘어간다")
        void 다른_오류는_그대로() {
            when(descriptorRepository.findByFaceIdAndBranch(FACE_ID, existingBranch)).thenReturn(Optional.empty());
            when(descriptorRepository.existsByBranchAndDescriptorVersion(existingBranch, 59)).thenReturn(true);
            doThrow(new CustomFaceMatcherException(ErrorType.INTERNAL_SERVER_ERROR))
                    .when(duplicateService).checkDuplicateDescriptor(any(), any(), any(), any(Boolean.class));

            assertThatThrownBy(() -> registerService.register(BRANCH_NAME, FACE_ID, base64Descriptor))
                    .isInstanceOf(CustomFaceMatcherException.class)
                    .extracting(ex -> ((CustomFaceMatcherException) ex).getErrorType())
                    .isEqualTo(ErrorType.INTERNAL_SERVER_ERROR);
            verify(descriptorRepository, never()).saveAndFlush(any());
        }

        /**
         * 함께 고친 결함. 예전에는 "브랜치에 특징점이 있는가" 를 버전 없이 보고 중복 검사(identify)에 들어갔는데, identify 는
         * 같은 버전이 없으면 EMPTY_GALLERY 를 던졌다 — 다른 버전 특징점만 있는 브랜치에서 등록이 실패했다.
         */
        @Test
        @DisplayName("요청과 같은 버전의 특징점이 없으면 중복 검사에 들어가지 않는다 — 다른 버전만 있는 브랜치에서 EMPTY_GALLERY 로 실패하지 않게")
        void 다른_버전만_있으면_중복_검사_안함() {
            String v62 = createBase64Descriptor(62);
            when(descriptorRepository.findByFaceIdAndBranch(FACE_ID, existingBranch)).thenReturn(Optional.empty());
            when(descriptorRepository.existsByBranchAndDescriptorVersion(existingBranch, 62)).thenReturn(false);

            registerService.register(BRANCH_NAME, FACE_ID, v62);

            verify(duplicateService, never()).checkDuplicateDescriptor(any(), any(), any(), any(Boolean.class));
            verify(descriptorRepository).saveAndFlush(any(Descriptor.class));
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
            when(branchProvisioner.findOrCreate(BRANCH_NAME)).thenReturn(existingBranch);
            when(branchRepository.getReferenceById(1L)).thenReturn(existingBranch);
            when(descriptorRepository.findByFaceIdAndBranch(FACE_ID, existingBranch)).thenReturn(Optional.empty());
            when(descriptorRepository.existsByBranchAndDescriptorVersion(existingBranch, 59)).thenReturn(false);
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

    static String createBase64Descriptor(int version) {
        byte[] bytes = new byte[520];
        bytes[4] = (byte) version;
        return Base64.getEncoder().encodeToString(bytes);
    }
}
