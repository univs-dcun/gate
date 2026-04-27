package ai.univs.match.application.service;

import ai.univs.match.application.enums.DescriptorSpec;
import ai.univs.match.application.input.DescriptorDetail;
import ai.univs.match.application.result.IdentifyResult;
import ai.univs.match.domain.entity.Branch;
import ai.univs.match.infrastructure.persistence.DescriptorCustomRepository;
import ai.univs.match.infrastructure.persistence.DescriptorRepository;
import ai.univs.match.infrastructure.persistence.projection.MatchResultProjection;
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

@DisplayName("IdentifyService")
@ExtendWith(MockitoExtension.class)
class IdentifyServiceTest {

    @Mock
    private DescriptorRepository descriptorRepository;

    @Mock
    private DescriptorCustomRepository descriptorCustomRepository;

    @InjectMocks
    private IdentifyService identifyService;

    private Branch branch;
    private DescriptorDetail descriptorDetail;

    @BeforeEach
    void setUp() {
        branch = Branch.builder()
                .id(1L)
                .branchName("testBranch")
                .build();

        descriptorDetail = DescriptorDetail.from(createBase64Descriptor(59));
    }

    @Nested
    @DisplayName("identify")
    class Identify {

        @Test
        @DisplayName("브랜치에 매칭 대상이 없으면 EMPTY_GALLERY 예외를 던진다")
        void whenNoDescriptorsInBranch_thenThrowEmptyGallery() {
            when(descriptorRepository.countByBranchAndDescriptorVersion(branch, 59)).thenReturn(0);

            assertThatThrownBy(() -> identifyService.identify(branch, descriptorDetail))
                    .isInstanceOf(CustomFaceMatcherException.class)
                    .satisfies(ex -> assertThat(((CustomFaceMatcherException) ex).getErrorType())
                            .isEqualTo(ErrorType.EMPTY_GALLERY));
        }

        @Test
        @DisplayName("매칭 대상이 없으면 oneToManyMatch를 호출하지 않는다")
        void whenNoDescriptorsInBranch_thenNeverCallsOneToManyMatch() {
            when(descriptorRepository.countByBranchAndDescriptorVersion(branch, 59)).thenReturn(0);

            assertThatThrownBy(() -> identifyService.identify(branch, descriptorDetail));

            verify(descriptorCustomRepository, never()).oneToManyMatch(any(), any(), anyInt());
        }

        @Test
        @DisplayName("매칭 대상이 있으면 branchId와 버전 512로 oneToManyMatch를 호출한다")
        void whenDescriptorsExist_thenCallsOneToManyMatchWithBranchIdAndVersion512() {
            when(descriptorRepository.countByBranchAndDescriptorVersion(branch, 59)).thenReturn(3);
            when(descriptorCustomRepository.oneToManyMatch(eq(1L), any(byte[].class), eq(512)))
                    .thenReturn(new MatchResultProjection("face-001", 1.0));

            identifyService.identify(branch, descriptorDetail);

            verify(descriptorCustomRepository).oneToManyMatch(eq(1L), any(byte[].class), eq(512));
        }

        @Test
        @DisplayName("oneToManyMatch 결과의 faceId를 IdentifyResult에 담아 반환한다")
        void whenMatchFound_thenReturnsFaceIdFromProjection() {
            when(descriptorRepository.countByBranchAndDescriptorVersion(branch, 59)).thenReturn(1);
            when(descriptorCustomRepository.oneToManyMatch(eq(1L), any(byte[].class), eq(512)))
                    .thenReturn(new MatchResultProjection("face-001", 1.0));

            IdentifyResult result = identifyService.identify(branch, descriptorDetail);

            assertThat(result.faceId()).isEqualTo("face-001");
        }

        @Test
        @DisplayName("distance가 0 이하이면 유사도 1.00000을 반환한다")
        void whenDistanceIsZero_thenReturnMaxSimilarity() {
            when(descriptorRepository.countByBranchAndDescriptorVersion(branch, 59)).thenReturn(1);
            when(descriptorCustomRepository.oneToManyMatch(eq(1L), any(byte[].class), eq(512)))
                    .thenReturn(new MatchResultProjection("face-001", 0.0));

            IdentifyResult result = identifyService.identify(branch, descriptorDetail);

            assertThat(result.similarity()).isEqualTo("1.00000");
        }

        @Test
        @DisplayName("distance를 DescriptorSpec의 Platt scaling으로 변환한 유사도를 반환한다")
        void whenMatchFound_thenSimilarityIsConvertedFromDistance() {
            double distance = 1.0;
            when(descriptorRepository.countByBranchAndDescriptorVersion(branch, 59)).thenReturn(1);
            when(descriptorCustomRepository.oneToManyMatch(eq(1L), any(byte[].class), eq(512)))
                    .thenReturn(new MatchResultProjection("face-001", distance));

            IdentifyResult result = identifyService.identify(branch, descriptorDetail);

            String expectedSimilarity = getSimilarityByDistance(distance, DescriptorSpec.VERSION_59);
            assertThat(result.similarity()).isEqualTo(expectedSimilarity);
        }

        @Test
        @DisplayName("descriptorSpec 버전에 맞는 카운트 쿼리를 실행한다")
        void whenCalled_thenCountsWithCorrectVersion() {
            DescriptorDetail detail60 = DescriptorDetail.from(createBase64Descriptor(60));
            Branch branch2 = Branch.builder().id(2L).branchName("branch2").build();

            when(descriptorRepository.countByBranchAndDescriptorVersion(branch2, 60)).thenReturn(1);
            when(descriptorCustomRepository.oneToManyMatch(eq(2L), any(byte[].class), eq(512)))
                    .thenReturn(new MatchResultProjection("face-002", 0.5));

            identifyService.identify(branch2, detail60);

            verify(descriptorRepository).countByBranchAndDescriptorVersion(branch2, 60);
        }
    }

    // 테스트용 유효한 Base64 descriptor 생성 (8 bytes header + 512 bytes body)
    static String createBase64Descriptor(int version) {
        byte[] bytes = new byte[520];
        bytes[4] = (byte) version;
        return Base64.getEncoder().encodeToString(bytes);
    }
}
