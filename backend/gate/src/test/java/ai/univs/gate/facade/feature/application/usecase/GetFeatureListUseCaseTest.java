package ai.univs.gate.facade.feature.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import ai.univs.gate.facade.feature.application.input.FeatureListQuery;
import ai.univs.gate.facade.feature.application.result.FeatureListResult;
import ai.univs.gate.facade.feature.domain.enums.FeatureQueryType;
import ai.univs.gate.facade.feature.infrastructure.persistence.FeatureDSLRepository;
import ai.univs.gate.facade.feature.infrastructure.persistence.FeatureRow;
import ai.univs.gate.modules.api_key.domain.entity.ApiKey;
import ai.univs.gate.modules.feature.domain.enums.FeatureType;
import ai.univs.gate.modules.project.domain.entity.Project;
import ai.univs.gate.modules.project.domain.entity.ProjectSettings;
import ai.univs.gate.modules.project.domain.enums.ProjectStatus;
import ai.univs.gate.support.api_key.ApiKeyService;
import ai.univs.gate.support.file.FileService;
import ai.univs.gate.support.project.ProjectSettingsService;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * UG-269.
 *
 * <p>핵심은 {@code featureType=ALL} 이 <b>페이지 크기만큼만</b> 읽는다는 것이다. 예전에는 face·palm
 * 을 각각 {@code 0..offset+pageSize} 까지 읽어 자바 힙에서 병합했고, 상한
 * ({@code page<=1000, pageSize<=1000}) 안에서도 한 요청이 최대 200만 행을 적재할 수 있었다.
 *
 * <p>그래서 이 테스트는 결과 값보다 <b>어떤 쿼리를 어떤 인자로 불렀는지</b>를 본다. 결과만 보면
 * 옛 구현도 같은 값을 돌려주므로 회귀를 잡을 수 없다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("GetFeatureListUseCase 단위 테스트")
class GetFeatureListUseCaseTest {

    private static final Long PROJECT_ID = 1L;
    private static final Long ACCOUNT_ID = 10L;
    private static final String API_KEY = "gate_test-api-key";
    private static final String FILE_SERVER_PATH = "https://gw/api/v1/file?filePath=";

    @Mock private FeatureDSLRepository featureDSLRepository;
    @Mock private ApiKeyService apiKeyService;
    @Mock private ProjectSettingsService projectSettingsService;
    @Mock private FileService fileService;

    @InjectMocks private GetFeatureListUseCase useCase;

    private Project project;

    @BeforeEach
    void setUp() {
        project = Project.builder()
                .id(PROJECT_ID)
                .accountId(ACCOUNT_ID)
                .projectName("gate-project")
                .branchName("branch-1")
                .status(ProjectStatus.ACTIVE)
                .build();
        ApiKey apiKey = ApiKey.builder()
                .id(5L)
                .project(project)
                .apiKey(API_KEY)
                .secretKey("secret")
                .issuedAt(LocalDateTime.now(ZoneOffset.UTC))
                .isActive(true)
                .build();
        given(apiKeyService.findOwnedByApiKey(API_KEY, ACCOUNT_ID)).willReturn(apiKey);
        given(projectSettingsService.findByProject(project)).willReturn(
                ProjectSettings.builder().id(2L).project(project).consentEnabled(true).build());
    }

    private FeatureListQuery query(FeatureQueryType type, int page, int pageSize) {
        return new FeatureListQuery(
                ACCOUNT_ID, API_KEY, type, null, page, pageSize, null, null, null, "Asia/Seoul");
    }

    private static FeatureRow row(FeatureType type, long seq) {
        return new FeatureRow(
                type, seq, "desc-" + seq, "/face/" + seq + ".jpg", "fid-" + seq,
                LocalDateTime.of(2026, 8, 5, 12, 0));
    }

    @Test
    @DisplayName("ALL 은 통합 쿼리 하나만 부르고 offset·limit 을 DB 로 넘긴다")
    void all_pushesPagingToDatabase() {
        // given: 최악의 조합이던 마지막 페이지. 옛 구현은 여기서 face·palm 각각 100만 행을 읽었다.
        FeatureListQuery q = query(FeatureQueryType.ALL, 1000, 1000);
        given(featureDSLRepository.findAllRows(eq(PROJECT_ID), any(), eq(999_000L), eq(1000)))
                .willReturn(List.of(row(FeatureType.FACE, 1L), row(FeatureType.PALM, 2L)));
        given(featureDSLRepository.countAll(PROJECT_ID, q)).willReturn(2_000_000L);
        given(fileService.getFileServerPath()).willReturn(FILE_SERVER_PATH);

        // when
        FeatureListResult result = useCase.execute(q);

        // then: offset 을 0 으로 넘기거나 limit 에 offset+pageSize 를 넘기면 이 검증에서 걸린다
        verify(featureDSLRepository).findAllRows(PROJECT_ID, q, 999_000L, 1000);
        verify(featureDSLRepository).countAll(PROJECT_ID, q);

        // then: 타입별 조회로 되돌아가면(= 자바 힙 병합 부활) 여기서 걸린다
        verify(featureDSLRepository, never()).findFaceRows(any(), any(), anyLongValue(), anyIntValue());
        verify(featureDSLRepository, never()).findPalmRows(any(), any(), anyLongValue(), anyIntValue());
        verify(featureDSLRepository, never()).countFace(any(), any());
        verify(featureDSLRepository, never()).countPalm(any(), any());

        assertThat(result.features()).hasSize(2);
        assertThat(result.page().totalElements()).isEqualTo(2_000_000L);
    }

    @Test
    @DisplayName("ALL 응답의 featureType 은 행마다 실제 타입을 그대로 노출한다")
    void all_projectsTypePerRow() {
        // 상수가 아니라 bf.type 을 select 하므로 한 페이지에 두 타입이 섞여 나올 수 있다
        FeatureListQuery q = query(FeatureQueryType.ALL, 1, 10);
        given(featureDSLRepository.findAllRows(eq(PROJECT_ID), any(), eq(0L), eq(10)))
                .willReturn(List.of(row(FeatureType.PALM, 1L), row(FeatureType.FACE, 2L)));
        given(featureDSLRepository.countAll(PROJECT_ID, q)).willReturn(2L);
        given(fileService.getFileServerPath()).willReturn(FILE_SERVER_PATH);

        FeatureListResult result = useCase.execute(q);

        assertThat(result.features()).extracting("featureType").containsExactly("PALM", "FACE");
    }

    @Test
    @DisplayName("FACE 는 기존대로 타입별 조회를 쓰고 통합 쿼리를 부르지 않는다")
    void face_usesTypedQuery() {
        FeatureListQuery q = query(FeatureQueryType.FACE, 3, 20);
        given(featureDSLRepository.findFaceRows(eq(PROJECT_ID), any(), eq(40L), eq(20)))
                .willReturn(List.of(row(FeatureType.FACE, 1L)));
        given(featureDSLRepository.countFace(PROJECT_ID, q)).willReturn(1L);
        given(fileService.getFileServerPath()).willReturn(FILE_SERVER_PATH);

        useCase.execute(q);

        verify(featureDSLRepository).findFaceRows(PROJECT_ID, q, 40L, 20);
        verify(featureDSLRepository, never()).findAllRows(any(), any(), anyLongValue(), anyIntValue());
        verify(featureDSLRepository, never()).countAll(any(), any());
    }

    @Test
    @DisplayName("동의가 꺼진 프로젝트는 이미지 URL 이 비고 파일 서버 경로를 조회하지 않는다")
    void consentDisabled_noImageUrl() {
        given(projectSettingsService.findByProject(project)).willReturn(
                ProjectSettings.builder().id(2L).project(project).consentEnabled(false).build());
        FeatureListQuery q = query(FeatureQueryType.ALL, 1, 10);
        given(featureDSLRepository.findAllRows(eq(PROJECT_ID), any(), eq(0L), eq(10)))
                .willReturn(List.of(row(FeatureType.FACE, 1L)));
        given(featureDSLRepository.countAll(PROJECT_ID, q)).willReturn(1L);

        FeatureListResult result = useCase.execute(q);

        assertThat(result.features()).extracting("imageUrl").containsExactly("");
        verify(fileService, never()).getFileServerPath();
    }

    // Mockito 의 anyLong()/anyInt() 는 primitive 매처라 import 충돌을 피하려고 감싼다
    private static long anyLongValue() {
        return org.mockito.ArgumentMatchers.anyLong();
    }

    private static int anyIntValue() {
        return org.mockito.ArgumentMatchers.anyInt();
    }
}
