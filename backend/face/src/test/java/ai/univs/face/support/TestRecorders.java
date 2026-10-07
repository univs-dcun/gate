package ai.univs.face.support;

import ai.univs.face.application.service.FaceHistoryRecorder;
import ai.univs.face.domain.repository.FaceHistoryRepository;
import ai.univs.face.domain.repository.FaceMatchRepository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 단위 테스트용 이력 기록기 (UG-358). 저장소는 목이고, {@code finish} 의 트랜잭션 템플릿은 아무것도 하지 않고 본문만 돌린다.
 */
public final class TestRecorders {

    private TestRecorders() {
    }

    public static FaceHistoryRecorder of(FaceHistoryRepository histories, FaceMatchRepository matches) {
        return new FaceHistoryRecorder(histories, matches, new TransactionTemplate(new NoOpTransactionManager()));
    }

    /** 트랜잭션을 실제로 열지 않는 관리자 — 커밋·롤백은 아무것도 하지 않는다. */
    public static class NoOpTransactionManager implements PlatformTransactionManager {

        @Override
        public TransactionStatus getTransaction(TransactionDefinition definition) {
            return new SimpleTransactionStatus();
        }

        @Override
        public void commit(TransactionStatus status) {
        }

        @Override
        public void rollback(TransactionStatus status) {
        }
    }
}
