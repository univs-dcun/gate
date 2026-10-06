package ai.univs.gate.support.privacy;

import ai.univs.gate.support.file.FileService;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
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
 *
 * <p><b>순서: 파일을 먼저 지우고 경로를 비운다.</b> 반대로 하면 경로를 비운 뒤 파일 삭제가 실패했을 때 그 파일을 가리키는
 * 행이 없어 영영 남는다. 지금 순서에서는 경로 비우기가 실패해도 다음 실행이 다시 집고, 파일 삭제는 없는 파일에도 성공한다.
 *
 * <p><b>이력의 이미지도 함께 사라진다.</b> 등록 이력({@code feature_history})과 인증 이력의 매칭 이미지가 같은 파일을
 * 가리킨다. 이력 <b>행</b>은 남고 이미지만 볼 수 없게 된다 — 삭제된 프로젝트 정리(UG-303)와 같은 판단이다.
 */
@Slf4j
@Service
public class DeletedFeatureImagePurgeService {

    private final DeletedFeatureImagePurgeRepository repository;
    private final FileService fileService;
    /**
     * 한 건마다 새 트랜잭션. 어노테이션({@code @Transactional(REQUIRES_NEW)})으로 두면 {@link #purgeQuietly} 가 같은 객체의
     * {@link #purge} 를 부를 때 프록시를 거치지 않아 트랜잭션 없이 돈다 — UPDATE 가 실패하고 그 실패를 purgeQuietly 가 삼켜,
     * 즉시 파기가 조용히 한 번도 일어나지 않는다 (슬라이스 테스트가 잡았다).
     */
    private final TransactionTemplate requiresNew;

    public DeletedFeatureImagePurgeService(DeletedFeatureImagePurgeRepository repository, FileService fileService,
                                           PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.fileService = fileService;
        this.requiresNew = new TransactionTemplate(transactionManager);
        this.requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * 한 특징점의 이미지를 파기한다. 대상이 아니면(살아 있음·이미 비움·없음) 아무것도 하지 않는다.
     *
     * <p>호출자 트랜잭션과 분리한다 — 정리 잡이 한 건 실패로 나머지까지 롤백하지 않게, 삭제 유스케이스가 자기 트랜잭션
     * 밖에서 부를 수 있게. 파일 삭제가 실패하면 예외로 트랜잭션이 롤백되어 경로가 남는다.
     *
     * @return 경로를 비웠으면 true
     */
    public boolean purge(Long featureSeq) {
        return Boolean.TRUE.equals(requiresNew.execute(status -> purgeInTransaction(featureSeq)));
    }

    private boolean purgeInTransaction(Long featureSeq) {
        String path = repository.findImagePathOfDeleted(featureSeq).orElse(null);
        if (path == null) {
            return false;
        }
        if (path.isBlank()) {
            // 지울 파일이 없다 — FileService 는 빈 경로를 거절하므로 그대로 두면 매번 실패하며 다시 집힌다.
            return repository.clearImagePath(featureSeq, path) == 1;
        }
        if (repository.isReferencedByLiveFeature(path)) {
            // 이 행은 그 파일의 주인이 아니다 — 파일은 두고 경로만 비워 다음 실행에서 다시 집지 않게 한다.
            log.warn("삭제된 특징점의 이미지를 살아 있는 특징점도 쓴다 — 파일은 남기고 경로만 비운다. featureSeq={}", featureSeq);
        } else {
            // 실패하면 예외가 그대로 나가고 경로는 남는다 — 다음 실행이 다시 집는다.
            fileService.delete(path);
        }
        return repository.clearImagePath(featureSeq, path) == 1;
    }

    /** 삭제 유스케이스용. 어떤 실패도 삭제 API 응답을 바꾸지 않는다 — 정기 정리가 다시 시도한다. */
    public void purgeQuietly(Long featureSeq) {
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
}
