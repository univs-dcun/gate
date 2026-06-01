package ai.univs.palm.application.usecase;

import ai.univs.palm.application.input.IdentifyInput;
import ai.univs.palm.application.result.IdentifyResult;
import ai.univs.palm.domain.ActionType;
import ai.univs.palm.domain.MatchType;
import ai.univs.palm.domain.PalmHistory;
import ai.univs.palm.domain.PalmLiveness;
import ai.univs.palm.domain.PalmMatch;
import ai.univs.palm.domain.repository.PalmHistoryRepository;
import ai.univs.palm.domain.repository.PalmMatchRepository;
import ai.univs.palm.infrastructure.feign.PalmFeign;
import ai.univs.palm.infrastructure.feign.dto.IdentifyFeignRequestDTO;
import ai.univs.palm.infrastructure.feign.dto.IdentifyFeignResponseDTO;
import ai.univs.palm.infrastructure.feign.dto.LivenessFeignRequestDTO;
import ai.univs.palm.infrastructure.feign.dto.LivenessFeignResponseDTO;
import ai.univs.palm.infrastructure.repository.PalmLivenessJpaRepository;
import ai.univs.palm.shared.exception.CustomFeignException;
import ai.univs.palm.shared.exception.InvalidPalmModuleException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;

import static ai.univs.palm.shared.web.enums.ErrorType.NOT_MATCH;

@Slf4j
@Component
@RequiredArgsConstructor
public class IdentifyUseCase {

    private final PalmFeign palmFeign;
    private final PalmHistoryRepository faceHistoryRepository;
    private final PalmMatchRepository faceMatchRepository;
    private final PalmLivenessJpaRepository palmLivenessJpaRepository;

    private static final String PALM_DETECTOR_RESOURCE_ID = "cpu";
    private static final String SPOOF_DETECTOR_RESOURCE_ID = "liveness_any_remote";
    private static final int LIVENESS_THRESHOLD = 85;
    private static final int MATCH_THRESHOLD = 20;

    @Transactional(noRollbackFor = InvalidPalmModuleException.class)
    public IdentifyResult execute(IdentifyInput input) {
        // 매칭 요청 이력 저장
        PalmHistory palmHistory = PalmHistory.create(
                ActionType.MATCH,
                "",
                input.transactionUuid(),
                input.clientId(),
                input.checkLiveness());
        faceHistoryRepository.save(palmHistory);

        try {
            // checkLiveness=true일 경우: 선 라이브니스 통과 시에만 매칭 진행
            if (input.checkLiveness()) {
                performLivenessCheck(input, palmHistory);
            }

            // SmartFace SearchByPalm 요청
            String base64Image = Base64.getEncoder().encodeToString(getImageBytes(input.palmImage()));

            IdentifyFeignRequestDTO request = new IdentifyFeignRequestDTO(
                    new IdentifyFeignRequestDTO.ImageDTO(base64Image),
                    List.of(input.branchName())
            );

            // SmartFace 응답: 감지된 팜 배열 — 각 항목의 matchResults 에 매칭 결과 포함
            List<IdentifyFeignResponseDTO> results = palmFeign.identify(request);

            // 모든 matchResults 중 score 가 가장 높은 후보 추출 (score >= MATCH_THRESHOLD 여부와 무관)
            IdentifyFeignResponseDTO.MatchResultDTO bestResult = results.stream()
                    .filter(r -> r.getMatchResults() != null && !r.getMatchResults().isEmpty())
                    .flatMap(r -> r.getMatchResults().stream())
                    .max(Comparator.comparingDouble(IdentifyFeignResponseDTO.MatchResultDTO::getScore))
                    .orElse(null);

            double bestScore = bestResult != null ? bestResult.getScore() : 0.0;
            String bestPalmId = bestResult != null ? bestResult.getWatchlistMemberId() : "";
            boolean matched = bestScore >= MATCH_THRESHOLD;

            // 매칭 결과 항상 저장 (성공/실패 모두)
            PalmMatch palmMatch = PalmMatch.create(
                    palmHistory,
                    bestPalmId,
                    bestScore,
                    (double) MATCH_THRESHOLD,
                    MatchType.IDENTIFY,
                    input.clientId());
            faceMatchRepository.save(palmMatch);

            if (!matched) {
                // 점수 미달 → 이력 실패 처리 후 HTTP 200 + result=false 반환 (예외 아님)
                palmHistory.fail(NOT_MATCH.name(), input.clientId());
                return new IdentifyResult(
                        input.transactionUuid(),
                        bestPalmId,
                        String.valueOf(bestScore),
                        String.valueOf(MATCH_THRESHOLD),
                        false);
            }

            // 매칭 성공 이력 갱신
            palmHistory.successMatch(true, input.clientId());

            return new IdentifyResult(
                    input.transactionUuid(),
                    bestPalmId,
                    String.valueOf(bestScore),
                    String.valueOf(MATCH_THRESHOLD),
                    true);

        } catch (InvalidPalmModuleException e) {
            // 라이브니스 실패 예외 → 이력 처리 완료 후 재전파
            throw e;
        } catch (CustomFeignException e) {
            // SmartFace 매칭 에러 (팜 미감지 등)
            log.warn("Palm module identify failed: [{}] {}", e.getType(), e.getMessage());
            palmHistory.fail(e.getMessage(), input.clientId());
            throw new InvalidPalmModuleException(e.getCode(), e.getType(), e.getMessage());
        }
    }

    /**
     * 라이브니스 사전 체크
     * - 통과(passed=true): 이력 저장 후 정상 반환 (매칭 계속 진행)
     * - 미통과(passed=false): 이력 저장 후 InvalidPalmModuleException 발생 (매칭 중단)
     * - SmartFace 에러: 이력 저장 후 InvalidPalmModuleException 발생 (매칭 중단)
     */
    private void performLivenessCheck(IdentifyInput input, PalmHistory palmHistory) {
        String base64Image = Base64.getEncoder().encodeToString(getImageBytes(input.palmImage()));

        LivenessFeignRequestDTO livenessRequest = new LivenessFeignRequestDTO(
                new LivenessFeignRequestDTO.ImageDTO(base64Image),
                PALM_DETECTOR_RESOURCE_ID,
                List.of(SPOOF_DETECTOR_RESOURCE_ID),
                new LivenessFeignRequestDTO.SpoofCheckConfigDTO(LIVENESS_THRESHOLD)
        );

        try {
            LivenessFeignResponseDTO livenessResponse = palmFeign.liveness(livenessRequest);
            LivenessFeignResponseDTO.LivenessSpoofCheckDTO spoofCheck = livenessResponse.getLivenessSpoofCheck();

            // 라이브니스 결과 저장
            saveLiveness(palmHistory, spoofCheck.isPerformed(), spoofCheck.isPassed(),
                    spoofCheck.getScore(), input.clientId());

            if (!spoofCheck.isPassed()) {
                // 라이브니스 미통과 → 매칭 중단
                palmHistory.fail("Liveness check not passed", input.clientId());
                throw new InvalidPalmModuleException(
                        "PALM-LIVENESS",
                        "LIVENESS_NOT_PASSED",
                        "Liveness check not passed");
            }

        } catch (CustomFeignException e) {
            // SmartFace 라이브니스 에러 (팜 미감지 등) → 매칭 중단
            log.warn("Palm module liveness failed during identify: [{}] {}", e.getType(), e.getMessage());
            saveLiveness(palmHistory, false, false, 0.0, input.clientId());
            palmHistory.fail(e.getMessage(), input.clientId());
            throw new InvalidPalmModuleException(e.getCode(), e.getType(), e.getMessage());
        }
    }

    private void saveLiveness(PalmHistory palmHistory, boolean performed, boolean passed,
                              double score, String clientId) {
        PalmLiveness palmLiveness = PalmLiveness.builder()
                .palmHistory(palmHistory)
                .performed(performed)
                .passed(passed)
                .score(score)
                .createdBy(clientId)
                .createdAt(LocalDateTime.now(ZoneOffset.UTC))
                .modifiedBy(clientId)
                .modifiedAt(LocalDateTime.now(ZoneOffset.UTC))
                .build();
        palmLivenessJpaRepository.save(palmLiveness);
    }

    private byte[] getImageBytes(MultipartFile image) {
        try {
            return image.getBytes();
        } catch (Exception e) {
            throw new RuntimeException("Failed to read palm image bytes", e);
        }
    }
}
