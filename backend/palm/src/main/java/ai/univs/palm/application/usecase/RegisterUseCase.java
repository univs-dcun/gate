package ai.univs.palm.application.usecase;

import ai.univs.palm.application.input.RegisterInput;
import ai.univs.palm.application.result.RegisterResult;
import ai.univs.palm.domain.ActionType;
import ai.univs.palm.domain.PalmHistory;
import ai.univs.palm.domain.PalmLiveness;
import ai.univs.palm.domain.repository.PalmHistoryRepository;
import ai.univs.palm.infrastructure.feign.PalmFeign;
import ai.univs.palm.infrastructure.feign.dto.IdentifyFeignRequestDTO;
import ai.univs.palm.infrastructure.feign.dto.IdentifyFeignResponseDTO;
import ai.univs.palm.infrastructure.feign.dto.LivenessFeignRequestDTO;
import ai.univs.palm.infrastructure.feign.dto.LivenessFeignResponseDTO;
import ai.univs.palm.infrastructure.feign.dto.RegisterFeignRequestDTO;
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
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RegisterUseCase {

    private final PalmFeign palmFeign;
    private final PalmHistoryRepository palmHistoryRepository;
    private final PalmLivenessJpaRepository palmLivenessJpaRepository;

    private static final String PALM_DETECTOR_RESOURCE_ID = "cpu";
    private static final String SPOOF_DETECTOR_RESOURCE_ID = "liveness_any_remote";
    private static final int LIVENESS_THRESHOLD = 85;
    private static final double DUPLICATE_PALM_THRESHOLD = 80.0;

    @Transactional(noRollbackFor = InvalidPalmModuleException.class)
    public RegisterResult execute(RegisterInput input) {
        // 등록 요청 이력 저장
        PalmHistory palmHistory = PalmHistory.create(
                ActionType.ADD,
                "",
                input.transactionUuid(),
                input.clientId(),
                input.checkLiveness());
        palmHistoryRepository.save(palmHistory);

        try {
            // 동일 팜 중복 등록 방지: 유사도 >= 80이면 이미 등록된 사용자로 간주
            checkDuplicatePalm(input, palmHistory);

            // checkLiveness=true일 경우: 선 라이브니스 통과 시에만 등록 진행
            if (input.checkLiveness()) {
                performLivenessCheck(input, palmHistory);
            }

            // palmId 생성 (우리 서버에서 UUID 생성)
            String palmId = UUID.randomUUID().toString();
            String base64Image = Base64.getEncoder().encodeToString(getImageBytes(input.palmImage()));

            // SmartFace WatchlistMember 등록 요청
            RegisterFeignRequestDTO request = new RegisterFeignRequestDTO(
                    palmId,
                    List.of(new RegisterFeignRequestDTO.RegistrationImageDTO("Palm", "Unspecified", base64Image)),
                    List.of(input.branchName())
            );
            palmFeign.register(request);

            // 등록 성공 이력 갱신
            palmHistory.successRegister(true, palmId, input.clientId());

            return new RegisterResult(input.branchName(), palmId, input.transactionUuid());

        } catch (InvalidPalmModuleException e) {
            // 라이브니스 실패로 발생한 예외 → 이력 처리 완료 후 재전파
            throw e;
        } catch (CustomFeignException e) {
            // SmartFace 팜 등록 에러
            log.warn("Palm module register failed: [{}] {}", e.getType(), e.getMessage());
            palmHistory.fail(e.getMessage(), input.clientId());
            throw new InvalidPalmModuleException(e.getCode(), e.getType(), e.getMessage());
        }
    }

    /**
     * 동일 팜 중복 등록 사전 체크
     * - 유사도 >= DUPLICATE_PALM_THRESHOLD: InvalidPalmModuleException 발생 (등록 중단)
     * - 유사도 < DUPLICATE_PALM_THRESHOLD 또는 매칭 없음: 정상 반환 (등록 계속 진행)
     * - SmartFace 에러 (watchlist 없음 등): 무시하고 등록 계속 진행
     */
    private void checkDuplicatePalm(RegisterInput input, PalmHistory palmHistory) {
        String base64Image = Base64.getEncoder().encodeToString(getImageBytes(input.palmImage()));

        IdentifyFeignRequestDTO identifyRequest = new IdentifyFeignRequestDTO(
                new IdentifyFeignRequestDTO.ImageDTO(base64Image),
                List.of(input.branchName())
        );

        try {
            List<IdentifyFeignResponseDTO> results = palmFeign.identify(identifyRequest);

            double bestScore = results.stream()
                    .filter(r -> r.getMatchResults() != null && !r.getMatchResults().isEmpty())
                    .flatMap(r -> r.getMatchResults().stream())
                    .max(Comparator.comparingDouble(IdentifyFeignResponseDTO.MatchResultDTO::getScore))
                    .map(IdentifyFeignResponseDTO.MatchResultDTO::getScore)
                    .orElse(0.0);

            if (bestScore >= DUPLICATE_PALM_THRESHOLD) {
                palmHistory.fail("Already registered palm user", input.clientId());
                throw new InvalidPalmModuleException(
                        "PALM-201",
                        "ALREADY_REGISTERED_PALM",
                        "Already registered palm user.");
            }
        } catch (CustomFeignException e) {
            // watchlist 미존재 등 → 등록된 사용자 없음, 등록 진행
            log.debug("Palm identify skipped during duplicate check: [{}] {}", e.getType(), e.getMessage());
        }
    }

    /**
     * 라이브니스 사전 체크
     * - 통과(passed=true): 이력 저장 후 정상 반환 (등록 계속 진행)
     * - 미통과(passed=false): 이력 저장 후 InvalidPalmModuleException 발생 (등록 중단)
     * - SmartFace 에러: 이력 저장 후 InvalidPalmModuleException 발생 (등록 중단)
     */
    private void performLivenessCheck(RegisterInput input, PalmHistory palmHistory) {
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
                // 라이브니스 미통과 → 등록 중단
                palmHistory.fail("Liveness check not passed", input.clientId());
                throw new InvalidPalmModuleException(
                        "PALM-LIVENESS",
                        "LIVENESS_NOT_PASSED",
                        "Liveness check not passed");
            }

        } catch (CustomFeignException e) {
            // SmartFace 라이브니스 에러 (팜 미감지 등) → 등록 중단
            log.warn("Palm module liveness failed during register: [{}] {}", e.getType(), e.getMessage());
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
