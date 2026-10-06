package ai.univs.gate.support.privacy;

import ai.univs.gate.support.file.FileService;
import ai.univs.gate.support.file.FileUtil.DeleteOutcome;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * API 로 삭제한 특징점의 원본 이미지를 파기한다 (UG-347).
 *
 * <p><b>무엇이 문제였나.</b> 특징점 삭제 API 는 face·palm 서비스의 특징점을 지우고 gate 행에 {@code is_deleted} 만 찍었다.
 * 원본 이미지 파일은 그대로 남았고, 회수 경로도 없었다 — {@link ProjectDataPurgeService} 는 삭제된 <b>프로젝트</b>만,
 * {@link HistoryPurgeService} 는 이력 행만 본다. 고객이 「이 사람 지워 달라」고 해도 얼굴 원본이 저장소에 영구히 남았다
 * (개인정보 보호법 제21조: 목적 달성 시 지체 없이 파기).
 *
 * <p><b>두 경로가 같은 메서드를 쓴다</b> (사용자 결정 2026-10-06).
 * <ul>
 *   <li>즉시 — 삭제 유스케이스가 커밋 뒤에 {@link #purgeQuietly} 를 부른다. 실패해도 삭제 API 는 성공이다.
 *   <li>정기 — {@link DeletedFeatureImagePurgeScheduler} 가 한 시간마다 남은 것을 지운다. 즉시 파기의 실패분과
 *       이 기능 이전에 쌓인 삭제분이 여기서 정리된다.
 * </ul>
 * {@code gate.privacy.deleted-feature-image-purge.enabled=false} 는 <b>두 경로를 모두</b> 멈춘다 — 파기 로직에 문제가 보이면
 * 재배포 없이 세울 수 있어야 한다 (반박 리뷰 W4).
 *
 * <p><b>순서: 읽기(짧은 트랜잭션) → 파일 삭제(트랜잭션 밖) → 경로 비우기(짧은 트랜잭션).</b> 파일 I/O 동안 커넥션을 쥐지 않는다
 * (반박 리뷰 N5 — 저장소가 NFS 면 길어진다). 경로를 먼저 비우면 파일 삭제가 실패했을 때 그 파일을 가리키는 행이 없어 영영
 * 남으므로 파일이 먼저다. 경로 비우기가 실패해도 다음 실행이 다시 집고, 이미 지운 파일은 「이미 없음」으로 끝난다.
 *
 * <p><b>저장소를 볼 수 없으면 비우지 않는다.</b> 파일이 있던 폴더조차 없으면 볼륨이 빠졌거나 루트 경로가 어긋났을 수 있다 —
 * 그때 「지웠다」로 표시하면 실제 볼륨의 원본을 가리키는 행이 사라진다 (반박 리뷰 W1).
 *
 * <p><b>이력의 이미지도 함께 사라진다.</b> 등록 이력({@code feature_history})과 인증 이력의 등록 이미지
 * ({@code match_history.feature_image_path})가 같은 파일을 가리킨다. 이력 <b>행</b>은 남고 이미지만 볼 수 없게 된다 — 삭제된
 * 프로젝트 정리(UG-303)와 같은 판단이다. 인증 때 제출한 이미지({@code matching_feature_image_path})는 다른 파일이라 남는다.
 */
@Slf4j
@Service
public class DeletedFeatureImagePurgeService {

    /** 한 건 파기의 결과. */
    public enum Outcome {
        /** 대상이 아니다 — 살아 있음·이미 비움·없음. */
        NOT_TARGET,
        /** 파일을 지우고 경로를 비웠다. */
        PURGED,
        /** 파일은 이미 없었다(폴더는 있음). 경로만 비웠다. */
        ALREADY_GONE,
        /** 살아 있는 특징점이 같은 파일을 써서 파일은 두고 경로만 비웠다. */
        SHARED_KEPT,
        /** 그사이 다른 쪽(즉시 파기·정기 정리)이 먼저 비웠다. */
        RACED
    }

    private final DeletedFeatureImagePurgeRepository repository;
    private final FileService fileService;
    private final boolean enabled;
    /**
     * 짧은 트랜잭션마다 새로 연다. 어노테이션({@code @Transactional(REQUIRES_NEW)})으로 두면 {@link #purgeQuietly} 가 같은
     * 객체의 {@link #purge} 를 부를 때 프록시를 거치지 않아 트랜잭션 없이 돈다 — UPDATE 가 실패하고 그 실패를 purgeQuietly 가
     * 삼켜, 즉시 파기가 조용히 한 번도 일어나지 않는다 (슬라이스 테스트가 잡았다).
     */
    private final TransactionTemplate requiresNew;

    public DeletedFeatureImagePurgeService(DeletedFeatureImagePurgeRepository repository, FileService fileService,
                                           PlatformTransactionManager transactionManager,
                                           @Value("${gate.privacy.deleted-feature-image-purge.enabled:true}") boolean enabled) {
        this.repository = repository;
        this.fileService = fileService;
        this.enabled = enabled;
        this.requiresNew = new TransactionTemplate(transactionManager);
        this.requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 한 특징점의 이미지를 파기한다.
     *
     * @throws RuntimeException 파일에 접근하지 못했거나 저장소를 볼 수 없을 때 — 경로는 남고 다음 실행이 다시 집는다
     */
    public Outcome purge(Long featureSeq) {
        Target target = requiresNew.execute(status -> repository.findImagePathOfDeleted(featureSeq)
                .map(path -> new Target(path, !path.isBlank() && repository.isReferencedByLiveFeature(path)))
                .orElse(null));
        if (target == null) {
            return Outcome.NOT_TARGET;
        }

        Outcome outcome;
        if (target.path().isBlank()) {
            // 지울 파일이 없다 — FileService 는 빈 경로를 거절하므로 그대로 두면 매번 실패하며 다시 집힌다.
            outcome = Outcome.ALREADY_GONE;
        } else if (target.sharedWithLive()) {
            // 이 행은 그 파일의 주인이 아니다 — 파일은 두고 경로만 비워 다음 실행에서 다시 집지 않게 한다.
            log.warn("삭제된 특징점의 이미지를 살아 있는 특징점도 쓴다 — 파일은 남기고 경로만 비운다. featureSeq={}", featureSeq);
            outcome = Outcome.SHARED_KEPT;
        } else {
            DeleteOutcome deleted = fileService.deleteReporting(target.path());
            if (deleted == DeleteOutcome.STORAGE_UNAVAILABLE) {
                throw new IllegalStateException("이미지 저장소를 볼 수 없다(파일이 있던 폴더가 없음) — 경로를 비우지 않는다");
            }
            outcome = deleted == DeleteOutcome.DELETED ? Outcome.PURGED : Outcome.ALREADY_GONE;
        }

        Integer cleared = requiresNew.execute(status -> repository.clearImagePath(featureSeq, target.path()));
        return cleared != null && cleared == 1 ? outcome : Outcome.RACED;
    }

    /** 삭제 유스케이스용. 어떤 실패도 삭제 API 응답을 바꾸지 않는다 — 정기 정리가 다시 시도한다. */
    public void purgeQuietly(Long featureSeq) {
        if (!enabled) {
            return;
        }
        try {
            purge(featureSeq);
        } catch (RuntimeException e) {
            log.warn("삭제한 특징점의 이미지 즉시 파기 실패 — 정기 정리가 다시 시도한다. featureSeq={}, 원인={}",
                    featureSeq, e.getClass().getSimpleName());
        }
    }

    /** 정리 대상 id ({@code afterId} 다음부터). */
    public List<Long> findTargets(long afterId, int limit) {
        return requiresNew.execute(status -> repository.findTargetIds(afterId, limit));
    }

    private record Target(String path, boolean sharedWithLive) {
    }
}
