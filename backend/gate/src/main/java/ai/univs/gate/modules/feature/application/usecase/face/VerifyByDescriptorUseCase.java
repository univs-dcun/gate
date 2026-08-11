package ai.univs.gate.modules.feature.application.usecase.face;

import ai.univs.gate.modules.api_key.domain.entity.ApiKey;
import ai.univs.gate.modules.feature.application.input.face.VerifyByDescriptorInput;
import ai.univs.gate.modules.feature.application.result.face.VerifyByDescriptorResult;
import ai.univs.gate.modules.feature.domain.entity.MatchHistory;
import ai.univs.gate.modules.feature.domain.enums.FeatureType;
import ai.univs.gate.modules.feature.domain.enums.MatchType;
import ai.univs.gate.modules.feature.domain.repository.MatchHistoryRepository;
import ai.univs.gate.modules.feature.infrastructure.client.face.dto.VerifyFaceByDescriptorFeignRequestDTO;
import ai.univs.gate.modules.feature.infrastructure.client.face.dto.VerifyFaceByDescriptorFeignResponseDTO;
import ai.univs.gate.modules.project.domain.entity.Project;
import ai.univs.gate.shared.exception.CustomFeignException;
import ai.univs.gate.shared.exception.RemoteCallException;
import ai.univs.gate.shared.web.enums.ErrorType;
import ai.univs.gate.support.api_key.ApiKeyService;
import ai.univs.gate.support.feature.face.FaceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * descriptor 기반 1:1 확인.
 *
 * <p>UG-279: 이 엔드포인트는 그동안 {@link MatchHistory} 를 남기지 않는 유일한 매칭 경로였다.
 * descriptor 계열 전체에 이력을 남기기로 결정하면서 저장을 추가했다. <b>응답 계약은 바꾸지 않았다</b>
 * — 이력은 내부에만 쌓인다.
 *
 * <p>매칭 타입은 {@link MatchType#VERIFY_DESCRIPTOR} 다. VERIFY_ID(촬영)·VERIFY_IMAGE(사진) 는
 * 둘 다 이미지 기반이라 의미상 재사용할 수 없었다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VerifyByDescriptorUseCase {

    private final ApiKeyService apiKeyService;
    private final FaceService faceService;
    private final MatchHistoryRepository matchHistoryRepository;

    @Transactional(
            propagation = Propagation.REQUIRES_NEW,
            // UG-280: RemoteCallException 이 목록에 있어야 하위 서비스 5xx 에도
            // 매칭 이력 행이 커밋된다. CustomGateException 을 넣지 않는 이유는
            // 그러면 모든 비즈니스 예외에 커밋을 허용해 버리기 때문이다.
            noRollbackFor = {CustomFeignException.class, RemoteCallException.class}
    )
    public VerifyByDescriptorResult execute(VerifyByDescriptorInput input) {
        ApiKey findApiKey = apiKeyService.findOwnedByApiKey(input.apiKey(), input.accountId());
        Project project = findApiKey.getProject();

        // UG-279: consentSnapshot 을 채우려고 projectSettingsService.findByProject 를 부르지
        // 않는다. 그 메서드는 project_settings 행이 없으면 SETTINGS_NOT_FOUND(PJ-106) 를 던지고,
        // 이 엔드포인트는 이력 저장을 추가하기 전까지 api_key 조회만 했다. 즉 호출을 넣는 순간
        // 지금까지 200 을 받던 요청이 400 이 될 수 있는 새 실패 지점이 생긴다 — 정상 생성 경로
        // (CreateProjectUseCase)는 항상 settings 를 만들지만, 마이그레이션에 백필이 없어
        // 외부에서 만들어진 프로젝트나 부분 복구된 DB 에서는 행이 없을 수 있다.
        //
        // 얻는 것이 nullable boolean 하나뿐인데 대가가 프로덕션 엔드포인트의 하드 실패라 남는
        // 장사가 아니다. 게다가 동의(consent)는 "이미지를 보관해도 되는가" 를 뜻하는데 descriptor
        // 경로는 이미지를 저장하지 않으므로 스냅샷할 대상 자체가 없다. NULL 이 "해당 없음" 을
        // 정확히 표현한다 (consent_snapshot 은 nullable).
        MatchHistory matchHistory = MatchHistory.builder()
                .project(project)
                .matchType(MatchType.VERIFY_DESCRIPTOR)
                .featureType(FeatureType.FACE)
                .matchTime(LocalDateTime.now(ZoneOffset.UTC))
                .checkLiveness(false)
                .success(false)
                .transactionUuid(input.transactionUuid())
                .build();
        matchHistoryRepository.save(matchHistory);

        var feignRequest = new VerifyFaceByDescriptorFeignRequestDTO(
                input.descriptor(),
                input.targetDescriptor(),
                input.transactionUuid(),
                // UG-277: 프로젝트 소유자 accountId 를 보낸다. 이 경로는 데모 DTO 가 없어 인증 전용이며,
                // 소유 검증이 호출자 == 소유자를 보장하므로 값이 달라지지 않는다. 호출자 값을 쓰지
                // 않는 이유는 X-Account-Id 가 없을 때 null.toString() 이 되기 때문이다 — 기본
                // ENFORCE 에서는 소유 검증이 먼저 거부하므로(Long.equals(null) 은 false) 도달하지
                // 않지만, mode=LOG_ONLY 로 되돌린 동안에는 통과해 여기서 터진다.
                project.getAccountId().toString());

        VerifyFaceByDescriptorFeignResponseDTO response;
        try {
            response = faceService.verifyDescriptor(feignRequest);
        } catch (CustomFeignException e) {
            // noRollbackFor 덕에 이력 행은 커밋되지만, 여기서 실패 사유를 남기지 않으면
            // failure_type 이 NULL 로 남아 "미완료 요청" 과 구분되지 않는다.
            // 같은 기능의 IdentifyByDescriptorUseCase / FaceFeatureService 와 동일한 처리다.
            matchHistory.fail(BigDecimal.ZERO, e.getType());
            throw e;
        } catch (RemoteCallException e) {
            // UG-280: 하위 서비스 5xx. 예전에는 CustomGateException 이라 noRollbackFor 에
            // 걸리지 않아 트랜잭션이 롤백되고 이 이력 행 자체가 사라졌다.
            matchHistory.fail(BigDecimal.ZERO, e.getErrorType().name());
            throw e;
        }

        BigDecimal similarity = toSimilarity(response.getSimilarity());
        if (response.isResult()) {
            // 1:1 확인은 성공해도 등록된 사용자 정보를 특정하지 않는다 (이미지 기반과 동일).
            matchHistory.success(similarity);
        } else {
            // 이미지 기반 1:1 두 경로(FaceVerifyByFeatureIdUseCase, FaceVerifyByFeatureImageUseCase)
            // 가 쓰는 코드와 같아야 한다. NOT_MATCH 는 1:N 전용이다 — 섞이면 운영자가
            // "1:1 불일치" 를 한 조건으로 집계할 수 없다.
            matchHistory.fail(similarity, ErrorType.MISMATCH.name());
        }

        // UG-283: 응답을 face 원값이 아니라 MatchHistory 에서 만든다. descriptor 1:N 과 같은
        // 구조·같은 유사도 스케일(백분율)을 내보내기 위해서다.
        return VerifyByDescriptorResult.from(matchHistory);
    }

    /**
     * face-service 는 유사도를 문자열("0.85123")로 반환한다. 파싱 실패로 요청 자체를 깨뜨리지
     * 않는다 — 유사도는 이력에만 쓰이는 부가 정보이고, {@code MatchHistory} 는 null 을 허용한다.
     */
    private BigDecimal toSimilarity(String similarity) {
        try {
            return new BigDecimal(similarity);
        } catch (NumberFormatException | NullPointerException e) {
            log.warn("특징점 1:1 확인 유사도를 해석할 수 없어 이력에 남기지 않는다. similarity={}", similarity);
            return null;
        }
    }
}
