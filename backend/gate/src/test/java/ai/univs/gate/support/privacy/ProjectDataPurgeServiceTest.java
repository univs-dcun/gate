package ai.univs.gate.support.privacy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import ai.univs.gate.modules.feature.domain.entity.BiometricFeature;
import ai.univs.gate.modules.feature.domain.enums.FeatureType;
import ai.univs.gate.modules.feature.infrastructure.client.face.dto.DeleteFaceFeignRequestDTO;
import ai.univs.gate.modules.feature.infrastructure.client.palm.dto.DeletePalmFeignRequestDTO;
import ai.univs.gate.modules.project.domain.entity.Project;
import ai.univs.gate.shared.exception.RemoteCallException;
import ai.univs.gate.support.feature.face.FaceService;
import ai.univs.gate.support.feature.palm.PalmService;
import ai.univs.gate.support.file.FileService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 삭제된 프로젝트의 생체 데이터를 <b>무엇을 어떤 순서로</b> 지우는가 (UG-303).
 *
 * <p>되돌릴 수 없는 삭제라 순서가 중요하다. 하위 서비스(face·palm)에서 먼저 지우고 성공한
 * 뒤에야 gate 의 행을 지운다. 뒤집으면 gate 에는 없고 하위에는 남은 고아가 생기고, 그것은
 * 어느 경로로도 다시 찾을 수 없다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UG-303: 프로젝트 생체 데이터 정리")
class ProjectDataPurgeServiceTest {

    private static final long PROJECT_ID = 42L;

    @Mock private ProjectPurgeRepository projectPurgeRepository;
    @Mock private FaceService faceService;
    @Mock private PalmService palmService;
    @Mock private FileService fileService;

    @InjectMocks private ProjectDataPurgeService purgeService;

    private Project deletedProject() {
        Project project = Project.builder()
                .accountId(100L).branchName("br-303").isDeleted(true).build();
        ReflectionTestUtils.setField(project, "id", PROJECT_ID);
        return project;
    }

    private BiometricFeature feature(long id, FeatureType type, String imagePath) {
        BiometricFeature f = BiometricFeature.builder()
                .type(type).featureId("f-" + id).featureImagePath(imagePath).build();
        ReflectionTestUtils.setField(f, "id", id);
        return f;
    }

    private void given특징점(BiometricFeature... features) {
        given(projectPurgeRepository.findDeletedProject(PROJECT_ID))
                .willReturn(Optional.of(deletedProject()));
        given(projectPurgeRepository.findFeaturesOf(PROJECT_ID)).willReturn(List.of(features));
    }

    @Test
    @DisplayName("얼굴 특징점은 face 서비스에서 지운 뒤 행과 이미지를 지운다")
    void 얼굴_특징점_정리() {
        BiometricFeature face = feature(1, FeatureType.FACE, "img/f1");
        given특징점(face);

        assertThat(purgeService.purgeProject(PROJECT_ID)).isEqualTo(1);

        ArgumentCaptor<DeleteFaceFeignRequestDTO> req =
                ArgumentCaptor.forClass(DeleteFaceFeignRequestDTO.class);
        verify(faceService).deleteFace(req.capture());
        assertThat(req.getValue().getBranchName()).isEqualTo("br-303");
        assertThat(req.getValue().getFaceId()).isEqualTo("f-1");

        verify(fileService).delete("img/f1");
        verify(projectPurgeRepository).deleteFeature(face);
    }

    @Test
    @DisplayName("손바닥 특징점은 palm 서비스로 간다 — 종류를 섞지 않는다")
    void 손바닥_특징점_정리() {
        BiometricFeature palm = feature(2, FeatureType.PALM, "img/p2");
        given특징점(palm);

        purgeService.purgeProject(PROJECT_ID);

        ArgumentCaptor<DeletePalmFeignRequestDTO> req =
                ArgumentCaptor.forClass(DeletePalmFeignRequestDTO.class);
        verify(palmService).deletePalm(req.capture());
        assertThat(req.getValue().getPalmId()).isEqualTo("f-2");
        verify(faceService, never()).deleteFace(any());
    }

    /**
     * 순서 보장. 하위 서비스 삭제가 실패하면 gate 행은 남는다.
     *
     * <p>여기서 지워 버리면 하위에만 남은 고아가 되고, gate 에 행이 없으니 다음 실행에서도
     * 대상이 되지 않는다 — 영원히 남는다.
     */
    @Test
    @DisplayName("하위 서비스 삭제가 실패하면 gate 행을 지우지 않는다")
    void 하위_실패시_행을_남긴다() {
        BiometricFeature face = feature(1, FeatureType.FACE, "img/f1");
        given특징점(face);
        willThrow(new RemoteCallException(503)).given(faceService).deleteFace(any());

        assertThat(purgeService.purgeProject(PROJECT_ID))
                .as("정리한 건수에 세면 안 된다 — 다음 실행에서 다시 와야 한다")
                .isZero();

        verify(projectPurgeRepository, never()).deleteFeature(any());
        verify(fileService, never()).delete(any());
    }

    @Test
    @DisplayName("한 특징점이 실패해도 나머지는 계속 정리한다")
    void 한_건_실패가_나머지를_막지_않는다() {
        BiometricFeature bad = feature(1, FeatureType.FACE, "img/f1");
        BiometricFeature good = feature(2, FeatureType.FACE, "img/f2");
        given특징점(bad, good);
        willThrow(new RemoteCallException(503)).given(faceService)
                .deleteFace(org.mockito.ArgumentMatchers.argThat(r -> "f-1".equals(r.getFaceId())));

        assertThat(purgeService.purgeProject(PROJECT_ID)).isEqualTo(1);

        verify(projectPurgeRepository).deleteFeature(good);
        verify(projectPurgeRepository, never()).deleteFeature(bad);
    }

    /**
     * 이미지 삭제 실패는 특징점 삭제를 막지 않는다.
     *
     * <p>고아 파일은 저장소 문제이지 접근 경로가 있는 상태가 아니다. 여기서 멈추면 특징점이
     * 계속 살아 있어 더 나쁘다.
     */
    @Test
    @DisplayName("이미지 삭제가 실패해도 특징점은 지운다")
    void 이미지_실패는_막지_않는다() {
        BiometricFeature face = feature(1, FeatureType.FACE, "img/f1");
        given특징점(face);
        willThrow(new IllegalStateException("MinIO 장애")).given(fileService).delete(any());

        assertThat(purgeService.purgeProject(PROJECT_ID)).isEqualTo(1);

        verify(projectPurgeRepository).deleteFeature(face);
    }

    @Test
    @DisplayName("이미지 경로가 비면 파일 삭제를 부르지 않는다 — 동의 없는 프로젝트")
    void 이미지가_없으면_건너뛴다() {
        given특징점(feature(1, FeatureType.FACE, ""));

        purgeService.purgeProject(PROJECT_ID);

        verify(fileService, never()).delete(any());
    }

    /**
     * 정리 중에 복구된 프로젝트를 건드리지 않는다.
     *
     * <p>대상 목록을 뽑은 시점과 각 프로젝트를 처리하는 시점 사이에 복구될 수 있다. 처리
     * 직전에 다시 확인한다.
     */
    @Test
    @DisplayName("대상이 더는 삭제 상태가 아니면 아무것도 하지 않는다")
    void 복구된_프로젝트는_건드리지_않는다() {
        given(projectPurgeRepository.findDeletedProject(PROJECT_ID)).willReturn(Optional.empty());

        assertThat(purgeService.purgeProject(PROJECT_ID)).isZero();

        verify(projectPurgeRepository, never()).findFeaturesOf(any());
        verify(faceService, never()).deleteFace(any());
        verify(projectPurgeRepository, never()).deleteFeature(any());
    }
}
