package ai.univs.match.application.usecase;

import ai.univs.match.application.input.DescriptorDetail;
import ai.univs.match.application.result.IdentifyResult;
import ai.univs.match.application.service.IdentifyService;
import ai.univs.match.domain.entity.Branch;
import ai.univs.match.infrastructure.persistence.BranchRepository;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("IdentifyUseCase")
@ExtendWith(MockitoExtension.class)
class IdentifyUseCaseTest {

    @Mock
    private BranchRepository branchRepository;

    @Mock
    private IdentifyService identifyService;

    @InjectMocks
    private IdentifyUseCase identifyUseCase;

    private static final String BRANCH_NAME = "testBranch";
    private Branch branch;
    private String base64Descriptor;

    @BeforeEach
    void setUp() {
        branch = Branch.builder().id(1L).branchName(BRANCH_NAME).build();
        base64Descriptor = createBase64Descriptor(59);
    }

    @Nested
    @DisplayName("브랜치 조회")
    class BranchLookup {

        @Test
        @DisplayName("브랜치가 존재하지 않으면 EMPTY_GALLERY 예외를 던진다")
        void whenBranchNotFound_thenThrowEmptyGallery() {
            when(branchRepository.findByBranchName(BRANCH_NAME)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> identifyUseCase.execute(BRANCH_NAME, base64Descriptor))
                    .isInstanceOf(CustomFaceMatcherException.class)
                    .satisfies(ex -> assertThat(((CustomFaceMatcherException) ex).getErrorType())
                            .isEqualTo(ErrorType.EMPTY_GALLERY));
        }

        @Test
        @DisplayName("브랜치가 없으면 identify를 수행하지 않는다")
        void whenBranchNotFound_thenNeverCallsIdentifyService() {
            when(branchRepository.findByBranchName(BRANCH_NAME)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> identifyUseCase.execute(BRANCH_NAME, base64Descriptor));

            verify(identifyService, never()).identify(any(), any());
        }
    }

    @Nested
    @DisplayName("1:N 매칭 성공")
    class IdentifySuccess {

        @BeforeEach
        void setUp() {
            when(branchRepository.findByBranchName(BRANCH_NAME)).thenReturn(Optional.of(branch));
        }

        @Test
        @DisplayName("조회된 브랜치로 IdentifyService.identify를 호출한다")
        void whenBranchFound_thenCallsIdentifyServiceWithBranch() {
            when(identifyService.identify(eq(branch), any(DescriptorDetail.class)))
                    .thenReturn(new IdentifyResult("face-001", "0.95000"));

            identifyUseCase.execute(BRANCH_NAME, base64Descriptor);

            verify(identifyService).identify(eq(branch), any(DescriptorDetail.class));
        }

        @Test
        @DisplayName("descriptor를 파싱한 DescriptorDetail을 IdentifyService에 전달한다")
        void whenBranchFound_thenPassesParsedDescriptorDetailToService() {
            when(identifyService.identify(any(), any()))
                    .thenReturn(new IdentifyResult("face-001", "0.95000"));

            identifyUseCase.execute(BRANCH_NAME, base64Descriptor);

            ArgumentCaptor<DescriptorDetail> captor = ArgumentCaptor.forClass(DescriptorDetail.class);
            verify(identifyService).identify(any(), captor.capture());

            DescriptorDetail expected = DescriptorDetail.from(base64Descriptor);
            assertThat(captor.getValue().descriptorSpec()).isEqualTo(expected.descriptorSpec());
        }

        @Test
        @DisplayName("IdentifyService의 결과를 그대로 반환한다")
        void whenIdentifyServiceReturns_thenReturnsResultAsIs() {
            IdentifyResult expected = new IdentifyResult("face-001", "0.95000");
            when(identifyService.identify(any(), any())).thenReturn(expected);

            IdentifyResult result = identifyUseCase.execute(BRANCH_NAME, base64Descriptor);

            assertThat(result.faceId()).isEqualTo("face-001");
            assertThat(result.similarity()).isEqualTo("0.95000");
        }
    }

    static String createBase64Descriptor(int version) {
        byte[] bytes = new byte[520];
        bytes[4] = (byte) version;
        return Base64.getEncoder().encodeToString(bytes);
    }
}
