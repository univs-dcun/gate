package ai.univs.gate.support.reconcile;

import ai.univs.gate.modules.feature.domain.entity.FeatureHistory;
import ai.univs.gate.modules.feature.domain.enums.FeatureType;
import ai.univs.gate.modules.feature.infrastructure.client.face.dto.DeleteFaceFeignRequestDTO;
import ai.univs.gate.modules.feature.infrastructure.client.palm.dto.DeletePalmFeignRequestDTO;
import ai.univs.gate.modules.project.domain.entity.Project;
import ai.univs.gate.shared.exception.CustomFeignException;
import ai.univs.gate.shared.utils.TransactionUtil;
import ai.univs.gate.support.feature.DownstreamAbsence;
import ai.univs.gate.support.feature.face.FaceService;
import ai.univs.gate.support.feature.palm.PalmService;
import ai.univs.gate.support.history.HistoryRecorder;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 결과를 모르는 등록을 되돌린다 (UG-338).
 *
 * <p><b>무엇이 문제인가.</b> gate 는 원격 등록이 성공한 뒤 자기 DB 에 쓴다. 그 쓰기가 실패하면 — 커넥션을
 * 못 얻거나(UG-336 이후 성공 쓰기가 원격 호출 뒤에 새 커넥션을 요구한다) 배포 중 프로세스가 끊기면
 * (파이프라인이 {@code stop → rm → up}) — 하위에는 특징점이 있는데 gate 에는 없다. 이 고아는 가만히
 * 있지 않는다.
 *
 * <ul>
 *   <li><b>identify 가 그 사람을 못 찾는다.</b> 하위가 고아를 최고 일치로 돌려주면 gate 는 자기 DB 에서
 *       그 id 를 찾지 못해 {@code INVALID_USER} 실패로 끝낸다.
 *   <li><b>재등록이 막힌다.</b> match 는 같은 브랜치에 비슷한 얼굴이 있으면 등록을 중복으로 거절한다.
 *       등록이 실패한 줄 아는 사람이 다시 등록하면 자기 고아 때문에 거절된다.
 * </ul>
 *
 * <p>그래서 하루 한 번이 아니라 <b>몇 분마다</b> 돈다.
 *
 * <p><b>왜 되돌리나(되살리지 않고).</b> 클라이언트는 실패를 받았으니 재시도할 것이다. gate 쪽을 살려 두면
 * 그 재시도가 이중 등록이 된다. 클라이언트가 본 결과(실패)에 맞춰 하위를 지운다.
 *
 * <p><b>삭제는 여기서 다루지 않는다.</b> 결과를 모르는 삭제 행을 여기서 끝까지 밀어붙이면, 오래전에 멈춘
 * 요청을 지금 집행하게 된다 — 클라이언트는 그때 실패를 받고 그 특징점을 계속 쓰고 있었을 수 있다. 대신
 * 삭제 유스케이스가 하위의 "없음" 을 성공으로 받아, 클라이언트의 재시도가 수렴한다.
 *
 * <p><b>무엇을 지우는가.</b> 시작 이력에 gate 가 남긴 발급 id 뿐이다({@code FeatureHistory.register}).
 * 그 id 의 특징점이 gate 에 있으면 — 있을 수 없는 일이지만 — 절대 지우지 않는다.
 *
 * <p><b>트랜잭션.</b> 원격 호출은 어떤 트랜잭션에도 넣지 않는다(UG-336). 조회·닫기는 각자 짧은 경계다.
 *
 * <p><b>설정으로 끌 수 있다</b> ({@code gate.reconcile.registration.enabled}, 기본 켜짐). UG-303·UG-282
 * 와 달리 기본값이 켜짐인 이유: 지우는 것은 <b>gate 가 기록하지 못한</b> 것뿐이고, 클라이언트는 이미
 * 실패를 받았다. 보존 기간을 정할 일이 없다. 반대로 끄면 위의 두 증상이 그대로 남는다.
 *
 * <p>gate 는 환경당 단일 인스턴스 전제다 (On-prem 확인, 2026-09-22). 다중 인스턴스로 가면 같은 행을
 * 두 대가 동시에 집는다 — 하위 삭제는 두 번째가 "없음" 을 받아 무해하지만, 그때는 분산 락을 검토한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrphanRegistrationReconciler {

    /**
     * 결과를 모른다고 볼 경과 시간. 요청 하나의 최대 소요보다 충분히 길어야 한다 — 짧으면 아직 처리 중인
     * 요청을 되돌린다. Feign 연결 5초 + 읽기 5초(재시도 없음), 성공 쓰기의 커넥션 대기(Hikari 기본 30초)를
     * 더해도 1분을 넘지 않는다.
     */
    static final Duration 결과를_모른다고_볼_시간 = Duration.ofMinutes(10);
    // 즉시 되돌리지 않는 이유가 하나 더 있다 (UG-337 반박 리뷰). 타임아웃 직후 삭제를 보내면 아직 커밋되지
    // 않은 하위 등록보다 삭제가 먼저 도착할 수 있다 — 삭제는 "없음" 을 받고, 그 뒤 등록이 커밋돼 고아가
    // 남는다. 10분 뒤라면 하위 쪽 등록도 이미 끝났다(face→match 호출에도 자체 타임아웃이 있다).

    /** 이보다 오래된 행은 다시 집지 않는다 — 정리하지 못한 행을 무한히 반복하지 않는다. */
    static final Duration 다시_보지_않는_나이 = Duration.ofHours(24);

    /** 한 번에 읽는 행 수. 페이지가 꽉 차면 시간 한도 안에서 다음 페이지를 이어 읽는다. */
    static final int 실행당_상한 = 50;

    /**
     * 실행당 시간 한도. 스케줄러는 기본 스레드 하나를 퍼지 잡들과 나눠 쓴다 — 하위가 느리면 50행 × 10초로
     * 한 실행이 8분 넘게 스레드를 쥘 수 있다. 한도를 넘으면 남은 행은 다음 실행(커서가 이어받는다)에 넘긴다.
     *
     * <p>한도 안에서는 페이지를 계속 읽는다 (UG-338 2차 반박 리뷰). 한 실행에 한 페이지만 보면 처리량이
     * 하루 14,400행에 묶인다 — match 장애가 몇 시간 이어져 후보가 수만 행이 되면 한 바퀴가 24시간을 넘기고,
     * 한 번도 보지 못한 행이 창 밖으로 나간다. "없음" 응답은 수 ms 라 한 실행에 수천 행을 볼 수 있다.
     */
    static final Duration 실행당_시간_한도 = Duration.ofMinutes(2);

    private final OrphanRegistrationRepository repository;
    private final HistoryRecorder historyRecorder;
    private final FaceService faceService;
    private final PalmService palmService;

    /** 시간 한도·조회 창의 기준 시각. 테스트가 바꿔 끼운다. */
    private Clock clock = Clock.systemUTC();

    @Value("${gate.reconcile.registration.enabled:true}")
    private boolean enabled;

    /**
     * 다음 실행이 이어서 볼 위치 — 마지막으로 본 이력 id. 한 바퀴를 다 돌면(상한보다 적게 나오면) 0 으로
     * 되감는다. 메모리에만 둔다: 재기동하면 처음부터 다시 돌 뿐 잃는 것이 없다.
     */
    private long cursor;

    @Scheduled(fixedDelay = 5 * 60 * 1000L, initialDelay = 60 * 1000L)
    public void reconcile() {
        if (!enabled) {
            return;
        }
        Instant 시작 = clock.instant();
        LocalDateTime now = LocalDateTime.ofInstant(시작, ZoneOffset.UTC);
        LocalDateTime staleBefore = now.minus(결과를_모른다고_볼_시간);
        LocalDateTime oldest = now.minus(다시_보지_않는_나이);

        int 본 = 0;
        int 닫음 = 0;
        boolean 한도_초과 = false;
        while (!한도_초과) {
            List<FeatureHistory> 페이지 = repository.findStaleRegistrations(staleBefore, oldest, cursor, 실행당_상한);
            for (FeatureHistory row : 페이지) {
                if (시간_초과(시작)) {
                    한도_초과 = true;
                    break;
                }
                본++;
                cursor = row.getId();
                try {
                    if (reconcileOne(row)) {
                        닫음++;
                    }
                } catch (RuntimeException e) {
                    // 존재 확인·닫기의 DB 예외. 한 행 때문에 이 실행의 나머지를 버리지 않는다.
                    log.warn("고아 등록 정리 중 오류 — 다음 바퀴에서 다시 본다. historyId={}, 원인={}",
                            row.getId(), e.getClass().getSimpleName());
                }
            }
            if (한도_초과) {
                break; // 커서는 마지막으로 본 행에 둔다 — 다음 실행이 이어받는다
            }
            if (페이지.size() < 실행당_상한) {
                cursor = 0; // 한 바퀴를 다 돌았다
                break;
            }
            한도_초과 = 시간_초과(시작);
        }
        if (본 > 0) {
            log.info("결과를 모르는 등록 정리. 본={}, 닫음={}, 남음={}", 본, 닫음, 본 - 닫음);
        }
    }

    private boolean 시간_초과(Instant 시작) {
        return Duration.between(시작, clock.instant()).compareTo(실행당_시간_한도) > 0;
    }

    /**
     * 한 행.
     *
     * @return 닫았으면 true. 하위 삭제를 확인하지 못해 남겼으면 false — 다음 실행에서 다시 온다.
     */
    boolean reconcileOne(FeatureHistory row) {
        Project project = row.getProject();

        if (repository.featureExists(row.getFeatureId())) {
            // 성공 이력은 특징점 저장과 한 트랜잭션이다 — 특징점이 있는데 이력이 시작 상태일 수는 없다.
            // 그래도 있다면 모르는 경로다. 하위를 지우면 살아 있는 사용자의 템플릿을 지운다.
            log.error("특징점이 gate 에 있는데 등록 이력이 시작 상태다 — 건드리지 않는다. historyId={}, featureId={}",
                    row.getId(), row.getFeatureId());
            return false;
        }

        try {
            deleteDownstream(project, row);
        } catch (CustomFeignException e) {
            if (!DownstreamAbsence.등록이_닿지_않았다(e)) {
                log.warn("고아 등록 삭제 실패 — 다음 실행에서 재시도한다. historyId={}, featureId={}, 사유={}",
                        row.getId(), row.getFeatureId(), e.getType());
                return false;
            }
            // 하위에 없다 = 원격 등록이 닿지 않았거나 이미 지워졌다. 되돌릴 것이 없으니 닫는다.
        } catch (RuntimeException e) {
            log.warn("고아 등록 삭제 실패 — 다음 실행에서 재시도한다. historyId={}, featureId={}, 원인={}",
                    row.getId(), row.getFeatureId(), e.getClass().getSimpleName());
            return false;
        }

        // 클라이언트는 이 요청을 실패로 받았다 — 이력도 실패로 닫는다. 응답에 나가는 값이라 새 유형을 만들지
        // 않고 기존 값을 쓴다. 되돌렸다는 사실은 아래 로그로 남는다(발급 id 는 행에서 지워진다).
        String 되돌린_id = row.getFeatureId();
        row.markReconciled();
        historyRecorder.fail(row);
        log.warn("결과를 모르는 등록을 되돌렸다. historyId={}, type={}, featureId={}, projectId={}",
                row.getId(), row.getFeatureType(), 되돌린_id, project.getId());
        return true;
    }

    private void deleteDownstream(Project project, FeatureHistory row) {
        String transactionUuid = TransactionUtil.useOrCreate(null);
        String clientId = String.valueOf(project.getAccountId());

        if (row.getFeatureType() == FeatureType.PALM) {
            palmService.deletePalm(new DeletePalmFeignRequestDTO(
                    project.getBranchName(), row.getFeatureId(), transactionUuid, clientId));
        } else {
            faceService.deleteFace(new DeleteFaceFeignRequestDTO(
                    project.getBranchName(), row.getFeatureId(), transactionUuid, clientId));
        }
    }
}
