package ai.univs.gate.support.tx;

import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 콜백을 즉시 실행하고, <b>지금 그 안에 있는지</b> 를 기록하는 단위 테스트용 템플릿 (UG-336).
 *
 * <p>Mockito 단위 테스트에는 트랜잭션 관리자가 없다. {@code @InjectMocks} 가 null 을 넣으면
 * 성공 경로가 NPE 로 끝나고, 목(mock)을 넣으면 콜백이 아예 실행되지 않아 성공 쓰기가 사라진다.
 * 둘 다 검증하려는 동작을 지운다.
 *
 * <p>{@link #isActive()} 가 이 클래스의 존재 이유다. UG-336 은 메서드 전체의 트랜잭션을 떼고
 * <b>특징점 쓰기와 성공 이력만</b> 한 트랜잭션으로 묶었다. "둘 다 호출됐다" 만 보면 그 둘이
 * 템플릿 밖으로 새어 나가도(= 따로 커밋돼도) 테스트가 초록이다. 목의 응답 안에서 이 값을 읽으면
 * 호출 시점에 경계 안이었는지를 단언할 수 있다.
 */
public class RecordingTransactionTemplate extends TransactionTemplate {

    private boolean active;
    private int executions;

    @Override
    public <T> T execute(TransactionCallback<T> action) throws TransactionException {
        if (active) {
            throw new IllegalStateException("중첩 실행은 이 테스트 템플릿이 다루지 않는다");
        }
        active = true;
        executions++;
        try {
            return action.doInTransaction(new SimpleTransactionStatus());
        } finally {
            active = false;
        }
    }

    /** 지금 콜백 안에서 실행 중인가. */
    public boolean isActive() {
        return active;
    }

    /** 지금까지 몇 번 경계를 열었나. */
    public int executions() {
        return executions;
    }
}
