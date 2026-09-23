package ai.univs.gate.support.privacy;

import ai.univs.gate.modules.feature.domain.entity.BiometricFeature;
import ai.univs.gate.modules.feature.domain.enums.FeatureType;
import ai.univs.gate.modules.feature.infrastructure.client.face.dto.DeleteFaceFeignRequestDTO;
import ai.univs.gate.modules.feature.infrastructure.client.palm.dto.DeletePalmFeignRequestDTO;
import ai.univs.gate.modules.project.domain.entity.Project;
import ai.univs.gate.shared.exception.CustomFeignException;
import ai.univs.gate.shared.utils.TransactionUtil;
import ai.univs.gate.support.feature.face.FaceService;
import ai.univs.gate.support.feature.palm.PalmService;
import ai.univs.gate.support.file.FileService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
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
 *   <li><b>저장소.</b> 원본 이미지가 회수 경로 없이 영구히 쌓인다.
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
 *   <li>지운다 — {@code biometric_feature} 행(물리 삭제), 그 특징점의 원본 이미지 파일,
 *       face-service·palm-service 쪽 저장분(각 서비스의 삭제 API 호출).
 *   <li>남긴다 — {@code match_history}·{@code feature_history} 의 감사 <b>행</b>. 보존 기간이
 *       UG-282 와 같은 법적 판단을 필요로 해 별개로 둔다.
 * </ul>
 *
 * <p><b>이력 행의 이미지는 함께 사라진다</b> (반박 리뷰 지적). 등록 사건의
 * {@code feature_history} 행은 {@code biometric_feature} 와 <b>같은 경로 문자열</b>을 갖는다
 * ({@code FaceFeatureService} 가 하나의 {@code imagePath} 를 양쪽에 넣는다). 즉 행은 남고
 * 그 행이 가리키는 파일은 없어진다. 개인정보를 지우는 것이 이 기능의 목적이므로 그 편이 맞고,
 * 외래 키가 없어(V25 의 스냅샷 설계) 행이 깨지지도 않는다. 다만 "이력은 남는다" 를 "이미지도
 * 남는다" 로 읽으면 안 된다.
 *
 * <ul>
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

    /**
     * 특징점 하나를 지운다.
     *
     * @return 지웠으면 true, 실패해 남겼으면 false
     */
    private boolean purgeFeature(Project project, BiometricFeature feature) {
        try {
            deleteFromBiometricService(project, feature);
        } catch (CustomFeignException e) {
            if (!알려진_없음_응답(e)) {
                log.warn("하위 서비스 삭제 실패로 남겨 둔다. projectId={}, featureId={}, type={}, 사유={}",
                        project.getId(), feature.getFeatureId(), feature.getType(), e.getType());
                return false;
            }
            // 하위에 이미 없다 = 우리가 지우려던 상태다. 실패로 세면 영원히 재시도만 한다.
            log.info("하위 서비스에 이미 없다 — 정리를 계속한다. projectId={}, featureId={}, 사유={}",
                    project.getId(), feature.getFeatureId(), e.getType());
        } catch (RuntimeException e) {
            // 그 외 실패는 gate 행을 남긴다 — 다음 실행에서 다시 시도한다.
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

    /**
     * 하위 서비스 호출.
     *
     * <p><b>이미 소프트 삭제된 특징점은 부르지 않는다</b> (반박 리뷰 지적). 제품 API 의 삭제
     * ({@code DeleteFaceFeatureUseCase})는 하위 삭제가 <b>성공한 뒤에야</b>
     * {@code biometricFeature.delete()} 를 찍는다. 즉 {@code is_deleted = true} 인 특징점은
     * 하위에 이미 없다. 그런데도 부르면 match 가 {@code INVALID_FACE_ID}(MATCH-004, 400)를
     * 돌려주고, 그것을 실패로 세면 <b>제품 API 로 삭제된 특징점은 영원히 정리되지 않는다.</b>
     * 리뷰가 체인을 끝까지 따라가 확인했다.
     */
    private void deleteFromBiometricService(Project project, BiometricFeature feature) {
        if (feature.isDeleted()) {
            return;
        }

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
     * 하위 서비스가 "그런 특징점 없다" 고 답한 경우.
     *
     * <p>위의 소프트 삭제 건너뛰기로 대부분 걸러지지만, <b>부분 실패 뒤 재시도</b>가 남는다 —
     * 하위 삭제는 성공했는데 그 뒤 커밋이 실패하면 행이 살아 있는 채로 다음 실행에 다시 온다.
     * 그때도 없음 응답을 실패로 세면 수렴하지 않는다.
     *
     * <p>face 경로는 코드가 확인됐다. match 가 {@code INVALID_FACE_ID}(MATCH-004)를 던지고
     * face 가 {@code code}·{@code type} 을 그대로 전파한다.
     *
     * <p><b>palm 은 미확인이다.</b> 벤더 모듈의 응답 코드를 확인하지 못했다. palm 쪽은 위
     * 소프트 삭제 건너뛰기에만 기댄다 — 부분 실패 재시도에서 막히면 로그의 사유를 보고 이
     * 목록에 추가할 것.
     */
    private boolean 알려진_없음_응답(CustomFeignException e) {
        return 없음을_뜻하는_사유.contains(e.getType());
    }

    private static final Set<String> 없음을_뜻하는_사유 = Set.of("INVALID_FACE_ID");

    /**
     * MinIO 원본 이미지를 지운다.
     *
     * <p>실패해도 특징점 삭제는 진행한다. 이미지만 남은 고아 파일은 저장소 문제이지 접근
     * 경로가 있는 상태가 아니다 — 반대로 여기서 멈추면 특징점이 계속 살아 있어 더 나쁘다.
     *
     * <p>동의를 받지 않은 프로젝트는 애초에 이미지를 올리지 않으므로 경로가 비어 있다 —
     * {@code FileService.uploadIfConsent} 는 {@code null} 을, 업로드 자체가 꺼진 환경
     * ({@code FILE_ENABLE_UPLOAD=false})은 빈 문자열을 돌려준다. 아래 검사가 둘 다 막는다.
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
