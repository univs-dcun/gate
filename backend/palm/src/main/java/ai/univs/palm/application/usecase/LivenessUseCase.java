package ai.univs.palm.application.usecase;

import ai.univs.palm.application.input.LivenessInput;
import ai.univs.palm.application.result.LivenessResult;
import ai.univs.palm.domain.ActionType;
import ai.univs.palm.domain.PalmHistory;
import ai.univs.palm.domain.PalmLiveness;
import ai.univs.palm.domain.repository.PalmHistoryRepository;
import ai.univs.palm.infrastructure.feign.PalmFeign;
import ai.univs.palm.infrastructure.feign.dto.LivenessFeignRequestDTO;
import ai.univs.palm.infrastructure.feign.dto.LivenessFeignResponseDTO;
import ai.univs.palm.infrastructure.repository.PalmLivenessJpaRepository;
import ai.univs.palm.shared.exception.CustomFeignException;
import ai.univs.palm.shared.exception.InvalidPalmModuleException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LivenessUseCase {

    private final PalmHistoryRepository palmHistoryRepository;
    private final PalmLivenessJpaRepository palmLivenessJpaRepository;
    private final PalmFeign palmFeign;

    private static final String PALM_DETECTOR_RESOURCE_ID = "cpu";
    private static final String SPOOF_DETECTOR_RESOURCE_ID = "liveness_any_remote";
    private static final int THRESHOLD = 85;

    @Transactional(noRollbackFor = InvalidPalmModuleException.class)
    public LivenessResult execute(LivenessInput input) {
        // 라이브니스 요청 이력 저장
        PalmHistory palmHistory = PalmHistory.create(
                ActionType.LIVENESS,
                "",
                input.transactionUuid(),
                input.clientId(),
                true);
        palmHistoryRepository.save(palmHistory);

        // PalmFeign - liveness 요청 객체 생성
        String base64Image = Base64.getEncoder().encodeToString(getImageBytes(input));
        LivenessFeignRequestDTO request = new LivenessFeignRequestDTO(
                new LivenessFeignRequestDTO.ImageDTO(base64Image),
                PALM_DETECTOR_RESOURCE_ID,
                List.of(SPOOF_DETECTOR_RESOURCE_ID),
                new LivenessFeignRequestDTO.SpoofCheckConfigDTO(THRESHOLD)
        );

        try {
            // PalmFeign - liveness 호출
            log.info("[Liveness] 요청 시작 - transactionUuid={}, clientId={}", input.transactionUuid(), input.clientId());
            LivenessFeignResponseDTO response = palmFeign.liveness(request);
            LivenessFeignResponseDTO.LivenessSpoofCheckDTO spoofCheck = response.getLivenessSpoofCheck();

            // liveness 결과 PalmLiveness 엔티티 세팅 후 DB 저장
            PalmLiveness palmLiveness = PalmLiveness.builder()
                    .palmHistory(palmHistory)
                    .performed(spoofCheck.isPerformed())
                    .passed(spoofCheck.isPassed())
                    .score(spoofCheck.getScore())
                    .createdBy(input.clientId())
                    .createdAt(LocalDateTime.now(ZoneOffset.UTC))
                    .modifiedBy(input.clientId())
                    .modifiedAt(LocalDateTime.now(ZoneOffset.UTC))
                    .build();
            palmLivenessJpaRepository.save(palmLiveness);

            // 라이브니스 요청 이력 갱신 (passed 여부에 따라 result 기록)
            palmHistory.successLiveness(spoofCheck.isPassed(), input.clientId());

            log.info("[Liveness] 결과 - passed={}, score={}", spoofCheck.isPassed(), spoofCheck.getScore());
            return new LivenessResult(spoofCheck.isPassed(), spoofCheck.getScore(), THRESHOLD, null);

        } catch (CustomFeignException e) {
            // 팜 모듈 에러 (No palm detected 등) → 라이브니스 실패로 처리
            // CustomFeignException 을 밖으로 전파하지 않고 여기서 흡수하므로
            // 트랜잭션이 정상 커밋되어 palm_history, palm_liveness 가 저장됨
            log.warn("Palm module liveness failed: [{}] {}", e.getType(), e.getMessage());

            // 라이브니스 실패 이력 저장 (팜 미감지 등 모듈 에러)
            PalmLiveness palmLiveness = PalmLiveness.builder()
                    .palmHistory(palmHistory)
                    .performed(false)
                    .passed(false)
                    .score(0.0)
                    .createdBy(input.clientId())
                    .createdAt(LocalDateTime.now(ZoneOffset.UTC))
                    .modifiedBy(input.clientId())
                    .modifiedAt(LocalDateTime.now(ZoneOffset.UTC))
                    .build();
            palmLivenessJpaRepository.save(palmLiveness);

            palmHistory.fail(e.getMessage(), input.clientId());

            return new LivenessResult(false, 0, THRESHOLD, e.getMessage());
        }
    }

    private byte[] getImageBytes(LivenessInput input) {
        try {
            return input.palmImage().getBytes();
        } catch (Exception e) {
            throw new RuntimeException("Failed to read palm image bytes", e);
        }
    }
}
