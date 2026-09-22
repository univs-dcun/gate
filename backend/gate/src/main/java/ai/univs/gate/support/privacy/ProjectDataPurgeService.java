package ai.univs.gate.support.privacy;

import ai.univs.gate.modules.feature.domain.entity.BiometricFeature;
import ai.univs.gate.modules.feature.domain.enums.FeatureType;
import ai.univs.gate.modules.feature.infrastructure.client.face.dto.DeleteFaceFeignRequestDTO;
import ai.univs.gate.modules.feature.infrastructure.client.palm.dto.DeletePalmFeignRequestDTO;
import ai.univs.gate.modules.project.domain.entity.Project;
import ai.univs.gate.shared.utils.TransactionUtil;
import ai.univs.gate.support.feature.face.FaceService;
import ai.univs.gate.support.feature.palm.PalmService;
import ai.univs.gate.support.file.FileService;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 삭제된 프로젝트의 생체 데이터를 유예 기간 뒤에 실제로 지운다 (UG-303).
 *
 * <p><b>무엇이 문제였나.</b> {@code DeleteProjectUseCase} 는 프로젝트 행에
 * {@code is_deleted = true} 를 찍고 API 키를 비활성화하는 것까지만 한다. 그 프로젝트에 등록된
 * 얼굴·손바닥 특징점과 MinIO 에 올라간 원본 이미지는 손대지 않는다. 조회 경로가 전부 막혀
 * 접근은 안 되지만 데이터는 남는다.
 *
 * <ul>
 *   <li><b>개인정보.</b> 얼굴·손바닥 특징점과 이미지다. "프로젝트를 지웠다" 는 사용자 입장에서
 *       그 데이터가 없어졌다는 뜻으로 읽힌다.
 *   <li><b>저장소.</b> MinIO 이미지가 회수 경로 없이 영구히 쌓인다.
 *   <li><b>온프레미스.</b> 납품처의 "이 프로젝트 데이터를 지워 달라" 가 수작업이 된다.
 * </ul>
 *
 * <p><b>왜 즉시 삭제가 아닌가.</b> 소프트 삭제를 쓰는 이유가 복구 가능성인데 특징점만 즉시
 * 지우면 앞뒤가 맞지 않는다. 유예 기간을 두면 복구 창을 남기면서 무한 축적을 막는다
 * (티켓 선택지 1). 기준 시각은 {@code projects.deleted_at} 이다 (V32).
 *
 * <p><b>기본값은 "아무것도 하지 않음" 이다.</b>
 * {@code gate.privacy.project-purge.retention-days} 를 주지 않으면 이 서비스는 대상을 조회조차
 * 하지 않는다. 되돌릴 수 없는 개인정보 삭제의 보존 기간은 제품·법무가 정할 값이지 코드가
 * 기본값으로 정할 값이 아니기 때문이다. e-KYC 이력의 법적 보관 의무가 확인되면 그 값을
 * 설정에 넣는 것으로 켜진다.
 *
 * <p><b>무엇을 지우고 무엇을 남기는가.</b>
 * <ul>
 *   <li>지운다 — {@code biometric_feature} 행(물리 삭제), 그 특징점의 MinIO 원본 이미지,
 *       face-service·palm-service 쪽 저장분(각 서비스의 삭제 API 호출).
 *   <li>남긴다 — {@code match_history}·{@code feature_history} 의 감사 기록. 그 행들이 가진
 *       이미지 경로는 시도 시점의 별도 이미지라 이 퍼지의 대상이 아니다. 이력 보존 기간은
 *       UG-282 와 같은 법적 판단이 필요해 별개로 둔다.
 * </ul>
 *
 * <p><b>부분 실패를 어떻게 다루는가.</b> 특징점 하나의 삭제가 실패해도 다음 특징점으로
 * 넘어가고, 그 프로젝트는 다음 실행에서 다시 대상이 된다. 실패한 것만 남으므로 재시도가
 * 자연스럽다. 반대로 한 건 때문에 전체를 중단하면 하위 서비스 한 대의 장애가 정리 전체를
 * 영구히 막는다.
 *
 * <p>하위 서비스 삭제가 성공한 뒤에야 gate 의 행을 지운다. 순서를 뒤집으면 gate 에는 없고
 * 하위에는 남은 고아가 생기고, 그것은 어느 경로로도 다시 찾을 수 없다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectDataPurgeService {

    private final ProjectPurgeRepository projectPurgeRepository;
    private final FaceService faceService;
    private final PalmService palmService;
    private final FileService fileService;

    /**
     * 한 프로젝트의 생체 데이터를 지운다.
     *
     * <p>프로젝트 단위로 트랜잭션을 끊는다({@code REQUIRES_NEW}). 전체를 한 트랜잭션으로 묶으면
     * 마지막 프로젝트의 실패가 앞의 성공을 전부 되돌리고, 누적분이 많은 첫 실행에서 대형
     * 트랜잭션이 된다.
     *
     * @return 실제로 지운 특징점 수
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int purgeProject(Long projectId) {
        Project project = projectPurgeRepository.findDeletedProject(projectId).orElse(null);
        if (project == null) {
            // 이 실행 중에 복구됐거나 이미 정리됐다. 경쟁 상황이지 오류가 아니다.
            return 0;
        }

        List<BiometricFeature> features = projectPurgeRepository.findFeaturesOf(projectId);
        int purged = 0;

        for (BiometricFeature feature : features) {
            if (purgeFeature(project, feature)) {
                purged++;
            }
        }

        log.info("삭제된 프로젝트의 생체 데이터를 정리했다. projectId={}, 대상={}, 정리={}, 남음={}",
                projectId, features.size(), purged, features.size() - purged);
        return purged;
    }

    /** @return 지웠으면 true, 실패해 남겼으면 false */
    private boolean purgeFeature(Project project, BiometricFeature feature) {
        try {
            deleteFromBiometricService(project, feature);
        } catch (RuntimeException e) {
            // 하위 서비스가 실패하면 gate 행을 남긴다 — 다음 실행에서 다시 시도한다.
            // 여기서 예외를 올리면 이 프로젝트의 나머지 특징점도 함께 멈춘다.
            log.warn("하위 서비스 삭제 실패로 남겨 둔다. projectId={}, featureId={}, type={}, 원인={}",
                    project.getId(), feature.getFeatureId(), feature.getType(),
                    e.getClass().getSimpleName());
            return false;
        }

        deleteImage(feature);
        projectPurgeRepository.deleteFeature(feature);
        return true;
    }

    private void deleteFromBiometricService(Project project, BiometricFeature feature) {
        String transactionUuid = TransactionUtil.useOrCreate(null);
        String clientId = String.valueOf(project.getAccountId());

        if (feature.getType() == FeatureType.PALM) {
            palmService.deletePalm(new DeletePalmFeignRequestDTO(
                    project.getBranchName(), feature.getFeatureId(), transactionUuid, clientId));
        } else {
            faceService.deleteFace(new DeleteFaceFeignRequestDTO(
                    project.getBranchName(), feature.getFeatureId(), transactionUuid, clientId));
        }
    }

    /**
     * MinIO 원본 이미지를 지운다.
     *
     * <p>실패해도 특징점 삭제는 진행한다. 이미지만 남은 고아 파일은 저장소 문제이지 접근
     * 경로가 있는 상태가 아니다 — 반대로 여기서 멈추면 특징점이 계속 살아 있어 더 나쁘다.
     *
     * <p>동의를 받지 않은 프로젝트는 애초에 이미지를 올리지 않으므로 경로가 빈 문자열이다
     * ({@code FileService.uploadIfConsent}).
     */
    private void deleteImage(BiometricFeature feature) {
        String path = feature.getFeatureImagePath();
        if (path == null || path.isBlank()) {
            return;
        }
        try {
            fileService.delete(path);
        } catch (RuntimeException e) {
            log.warn("이미지 삭제 실패 — 특징점은 그대로 지운다. featureId={}, 원인={}",
                    feature.getFeatureId(), e.getClass().getSimpleName());
        }
    }

    /** 유예가 지난 삭제 프로젝트 id 목록. 조회만 하므로 호출자의 트랜잭션을 요구하지 않는다. */
    @Transactional(readOnly = true)
    public List<Long> findPurgeTargets(LocalDateTime deletedBefore, int limit) {
        return projectPurgeRepository.findPurgeTargetIds(deletedBefore, limit);
    }
}
