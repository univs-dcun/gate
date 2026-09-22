package ai.univs.gate.modules.feature.application.usecase.face;

import ai.univs.gate.modules.api_key.domain.entity.ApiKey;
import ai.univs.gate.modules.feature.application.input.face.IdentifyCandidatesByDescriptorInput;
import ai.univs.gate.modules.feature.application.result.face.IdentifyCandidatesByDescriptorResult;
import ai.univs.gate.modules.feature.domain.entity.BiometricFeature;
import ai.univs.gate.modules.feature.domain.entity.MatchHistory;
import ai.univs.gate.modules.feature.domain.enums.FeatureType;
import ai.univs.gate.modules.feature.domain.enums.MatchType;
import ai.univs.gate.modules.feature.domain.repository.BiometricFeatureRepository;
import ai.univs.gate.modules.feature.infrastructure.client.face.dto.IdentifyCandidatesFaceFeignRequestDTO;
import ai.univs.gate.modules.feature.infrastructure.client.face.dto.IdentifyCandidatesFaceFeignResponseDTO;
import ai.univs.gate.modules.project.domain.entity.Project;
import ai.univs.gate.modules.project.domain.entity.ProjectSettings;
import ai.univs.gate.shared.exception.CustomFeignException;
import ai.univs.gate.shared.exception.RemoteCallException;
import ai.univs.gate.shared.web.enums.ErrorType;
import ai.univs.gate.support.api_key.ApiKeyService;
import ai.univs.gate.support.history.HistoryRecorder;
import ai.univs.gate.support.feature.face.FaceService;
import ai.univs.gate.support.project.ProjectSettingsService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 특징점 기반 1:N <b>후보 목록</b> 매칭 (UG-314).
 *
 * <p>{@link IdentifyByDescriptorUseCase} 를 기저로 하고 두 가지가 다르다 — 임계치를 클라이언트가
 * 정하고, 결과가 목록이다.
 *
 * <p><b>이력은 요청 하나에 한 행이다</b> (패턴 A). {@code MatchHistory} 는 "요청 하나 = featureId
 * 하나 + similarity 하나" 를 전제하므로 후보를 N행으로 넣으면 {@code transactionUuid} 단건 조회와
 * 대시보드 통계가 함께 깨진다. 대표값으로 <b>최상위 후보</b>를 남기고 목록은 응답에만 둔다.
 *
 * <p><b>스케일.</b> 클라이언트는 백분율(0 초과 100 이하)을 보내고 응답도 백분율로 받는다. 하위
 * 서비스는 0.0 ~ 1.0 만 다루므로 경계에서 나누고, 되받은 유사도는 {@code MatchHistory} 가
 * 저장하면서 곱한다 — 기존 API 들과 같은 규약이다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IdentifyCandidatesByDescriptorUseCase {

    private static final BigDecimal PERCENT = BigDecimal.valueOf(100);
    private final HistoryRecorder historyRecorder;

    private final BiometricFeatureRepository biometricFeatureRepository;
    private final ProjectSettingsService projectSettingsService;
    private final ApiKeyService apiKeyService;
    private final FaceService faceService;

    /**
     * 트랜잭션을 열지 않는다 (UG-293 반박 리뷰).
     *
     * <p>이 유스케이스는 {@link HistoryRecorder} 밖에서 아무것도 쓰지 않는다. 그런데 초판은
     * {@code @Transactional} 을 남겨 뒀고, 그러면 요청 하나가 <b>커넥션 두 개</b>를 동시에
     * 쥔다 — 바깥 트랜잭션이 조회 시점에 하나를 잡아 끝까지 붙들고, 그 안에서
     * {@code REQUIRES_NEW} 인 이력 기록이 두 번째를 요구한다.
     *
     * <p>리뷰가 풀 크기 1로 재현했다. 기본 풀은 10이고 Tomcat 스레드는 200이므로, 동시 10건이
     * 각자 첫 번째를 쥔 채 두 번째를 기다리면 아무도 진행하지 못하고 전원 타임아웃까지 멈춘다.
     * 바깥 트랜잭션이 face 서비스 호출까지 품고 있어 그 창이 넓다.
     *
     * <p>읽기는 각 리포지토리 호출이 자기 트랜잭션을 연다. 지연 연관은 {@code ApiKeyService} 가
     * 자기 경계 안에서 초기화해 돌려주므로(UG-335) 여기서 트랜잭션이 없어도 읽을 수 있다.
     */
    public IdentifyCandidatesByDescriptorResult execute(IdentifyCandidatesByDescriptorInput input) {
        ApiKey findApiKey = apiKeyService.findOwnedByApiKey(input.apiKey(), input.accountId());
        Project project = findApiKey.getProject();

        ProjectSettings findProjectSettings = projectSettingsService.findByProject(project);

        MatchHistory matchHistory = MatchHistory.builder()
                .project(project)
                .matchType(MatchType.IDENTIFY)
                .featureType(FeatureType.FACE)
                .matchTime(LocalDateTime.now(ZoneOffset.UTC))
                .checkLiveness(false)
                .success(false)
                .transactionUuid(input.transactionUuid())
                .consentSnapshot(findProjectSettings.getConsentEnabled())
                .build();
        matchHistory = historyRecorder.start(matchHistory);

        var request = IdentifyCandidatesFaceFeignRequestDTO.builder()
                .branchName(project.getBranchName())
                .descriptor(input.descriptor())
                .threshold(도메인_스케일로(input.thresholdPercent()))
                .maxCandidates(input.maxCandidates())
                .transactionUuid(input.transactionUuid())
                // UG-277 과 같은 이유. 이 경로는 인증 전용이라 소유 검증이 호출자 == 소유자를
                // 보장하고, X-Account-Id 가 없을 때 null.toString() 이 되는 것을 피한다.
                .clientId(project.getAccountId().toString())
                .build();

        IdentifyCandidatesFaceFeignResponseDTO data;
        try {
            data = faceService.identifyCandidatesByDescriptor(request);
        } catch (CustomFeignException e) {
            matchHistory.fail(BigDecimal.ZERO, e.getType());
            historyRecorder.fail(matchHistory);
            throw e;
        } catch (RemoteCallException e) {
            matchHistory.failUpstream(e);
            historyRecorder.fail(matchHistory);
            throw e;
        }

        List<IdentifyCandidatesFaceFeignResponseDTO.Candidate> 후보 = 후보들(data);
        if (후보.isEmpty()) {
            // 0 이 아니라 최근접 유사도를 남긴다. 기존 1:N 도 그렇게 하고 있고, 0 으로 눕히면
            // "아무도 근접하지 않았다" 와 "아깝게 미달했다" 가 이력에서 같아 보인다.
            matchHistory.fail(최근접_유사도(data), ErrorType.NOT_MATCH.name());
            historyRecorder.fail(matchHistory);
            return IdentifyCandidatesByDescriptorResult.failResult(
                    matchHistory, input.thresholdPercent());
        }

        Map<String, BiometricFeature> found = gate에_살아있는_특징점(후보, project.getId());

        // 하위 서비스는 찾았는데 gate 에는 하나도 없는 경우다. 데이터가 어긋난 상태라
        // 매칭 실패와 구분되어야 하므로 INVALID_USER 로 남긴다.
        if (found.isEmpty()) {
            matchHistory.fail(최근접_유사도(data), ErrorType.INVALID_USER.name());
            historyRecorder.fail(matchHistory);
            return IdentifyCandidatesByDescriptorResult.failResult(
                    matchHistory, input.thresholdPercent());
        }

        List<IdentifyCandidatesByDescriptorResult.Candidate> candidates = 후보.stream()
                .filter(candidate -> found.containsKey(candidate.getFaceId()))
                .map(candidate -> new IdentifyCandidatesByDescriptorResult.Candidate(
                        found.get(candidate.getFaceId()).getFeatureId(),
                        found.get(candidate.getFaceId()).getDescription(),
                        백분율로(candidate.getSimilarity())))
                .toList();

        // 이력의 대표값은 최상위 후보다. MatchHistory 가 백분율로 바꿔 저장한다.
        // 방금 조회한 엔티티를 그대로 쓴다 — 다시 조회하면 그 사이 삭제됐을 때 예외가 나고,
        // 그 예외는 noRollbackFor 에 없어 REQUIRES_NEW 트랜잭션째 롤백되어 이력 행이 사라진다.
        IdentifyCandidatesByDescriptorResult.Candidate top = candidates.getFirst();
        matchHistory.success(found.get(top.featureId()), 도메인_스케일_유사도(후보, top.featureId()));
        historyRecorder.succeed(matchHistory);

        return IdentifyCandidatesByDescriptorResult.successResult(
                matchHistory, input.thresholdPercent(), candidates);
    }

    /**
     * 후보 featureId 를 한 번에 조회해 featureId → 엔티티 로 돌려준다.
     *
     * <p>하위 서비스에는 있는데 gate 에서 삭제된 faceId 는 <b>그 후보만 빼고</b> 진행한다. 한
     * 명 때문에 목록 전체를 실패시키는 것은 이 API 의 용도에 맞지 않는다. 다만 두 저장소가
     * 어긋났다는 신호이므로 조용히 넘기지 않고 WARN 으로 남긴다.
     */
    private Map<String, BiometricFeature> gate에_살아있는_특징점(
            List<IdentifyCandidatesFaceFeignResponseDTO.Candidate> 후보, Long projectId) {

        List<String> featureIds = 후보.stream()
                .map(IdentifyCandidatesFaceFeignResponseDTO.Candidate::getFaceId)
                .toList();

        Map<String, BiometricFeature> found = new LinkedHashMap<>();
        biometricFeatureRepository
                .findAllByFeatureIdInAndProjectIdAndTypeAndIsDeletedFalse(
                        featureIds, projectId, FeatureType.FACE)
                .forEach(feature -> found.put(feature.getFeatureId(), feature));

        List<String> missing = featureIds.stream().filter(id -> !found.containsKey(id)).toList();
        if (!missing.isEmpty()) {
            log.warn("1:N 후보 중 gate 에 없는 특징점을 건너뛴다 — face·match 에는 있는데 gate 에서 "
                            + "삭제된 상태다. projectId={}, 건너뛴 featureIds={}, 후보 {}건 중 {}건",
                    projectId, missing, 후보.size(), missing.size());
        }

        return found;
    }

    /**
     * 하위 서비스가 알려준 최근접 유사도 (0.0 ~ 1.0). 없으면 {@code null} 이고,
     * {@link MatchHistory#fail} 이 null 을 그대로 null 로 남긴다 — 0.00 은 "근접자가 0% 였다"
     * 는 거짓말이 된다.
     */
    private static BigDecimal 최근접_유사도(IdentifyCandidatesFaceFeignResponseDTO data) {
        if (data == null || data.getNearestSimilarity() == null) return null;
        return new BigDecimal(data.getNearestSimilarity());
    }

    /** 하위 서비스가 준 0.0 ~ 1.0 유사도를 그대로 찾아 준다 — MatchHistory 가 곱해 저장한다. */
    private static BigDecimal 도메인_스케일_유사도(
            List<IdentifyCandidatesFaceFeignResponseDTO.Candidate> 후보, String featureId) {
        return 후보.stream()
                .filter(candidate -> featureId.equals(candidate.getFaceId()))
                .findFirst()
                .map(candidate -> new BigDecimal(candidate.getSimilarity()))
                .orElse(BigDecimal.ZERO);
    }

    /**
     * 백분율 → 도메인 스케일.
     *
     * <p>{@code double} 로 나누지 않는다. 85.33 / 100 은 이진 부동소수점에서 정확히 표현되지 않고,
     * 유사도가 소수점 5자리 반올림 후 비교되는 경로라 경계값 판정이 흔들릴 수 있다.
     */
    private static double 도메인_스케일로(BigDecimal thresholdPercent) {
        return thresholdPercent.divide(PERCENT, 10, RoundingMode.HALF_UP).doubleValue();
    }

    /** 도메인 스케일 → 백분율. {@code MatchHistory.toPercent} 와 같은 스케일·반올림이다. */
    private static BigDecimal 백분율로(String similarity) {
        return new BigDecimal(similarity).multiply(PERCENT).setScale(2, RoundingMode.HALF_UP);
    }

    private static List<IdentifyCandidatesFaceFeignResponseDTO.Candidate> 후보들(
            IdentifyCandidatesFaceFeignResponseDTO data) {
        return data == null || data.getCandidates() == null ? List.of() : data.getCandidates();
    }
}
