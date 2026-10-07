package ai.univs.face.application.service;

import ai.univs.face.domain.ActionType;
import ai.univs.face.domain.FaceHistory;
import ai.univs.face.domain.FaceMatch;
import ai.univs.face.domain.MatchType;
import ai.univs.face.domain.repository.FaceHistoryRepository;
import ai.univs.face.domain.repository.FaceMatchRepository;
import ai.univs.face.shared.exception.InvalidFaceImageException;
import ai.univs.face.shared.exception.InvalidFaceModuleException;
import ai.univs.face.shared.exception.UpstreamCallException;
import ai.univs.face.shared.web.enums.ErrorType;
import java.lang.reflect.Method;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * UG-358 이력 기록기. 유스케이스가 트랜잭션 없이 돌게 되면서 이력은 이 빈이 짧게 나눠 커밋한다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UG-358: FaceHistoryRecorder")
class FaceHistoryRecorderTest {

    private static final String CLIENT = "client-A";

    @Mock private FaceHistoryRepository faceHistoryRepository;
    @Mock private FaceMatchRepository faceMatchRepository;

    private FaceHistoryRecorder recorder;
    private FaceHistory history;

    @BeforeEach
    void setUp() {
        recorder = new FaceHistoryRecorder(faceHistoryRepository, faceMatchRepository);
        history = FaceHistory.create(ActionType.MATCH, "", "txn-358", CLIENT, false, false);
    }

    @Test
    @DisplayName("5xx·타임아웃(UpstreamCallException) — 예전에는 행이 롤백됐다. 이제 사유를 채워 남긴다")
    void upstream_실패는_사유를_채워_남긴다() {
        UpstreamCallException cause = new UpstreamCallException(503, "MatchFeign#identify", "Service Unavailable");

        recorder.recordFailure(history, cause, CLIENT);

        verify(faceHistoryRepository).save(history);
        assertThat(history.getFailureMessage()).isEqualTo(ErrorType.INTERNAL_SERVER_ERROR.name());
    }

    @Test
    @DisplayName("우리 쪽 예외(NPE 등)도 행이 남고 사유는 INTERNAL_SERVER_ERROR")
    void 일반_예외도_남긴다() {
        recorder.recordFailure(history, new NullPointerException("data"), CLIENT);

        verify(faceHistoryRepository).save(history);
        assertThat(history.getFailureMessage()).isEqualTo(ErrorType.INTERNAL_SERVER_ERROR.name());
    }

    @Test
    @DisplayName("이미 적힌 사유(FACE_NOT_FOUND 등)는 덮어쓰지 않는다")
    void 적힌_사유는_그대로() {
        history.fail("FACE_NOT_FOUND", CLIENT);

        recorder.recordFailure(history, new InvalidFaceModuleException("FACE-001", "FACE_NOT_FOUND", "x"), CLIENT);

        verify(faceHistoryRepository).save(history);
        assertThat(history.getFailureMessage()).isEqualTo("FACE_NOT_FOUND");
    }

    @Test
    @DisplayName("사유 없이 던진 모듈·이미지 예외는 그 타입을 사유로 쓴다")
    void 예외_타입을_사유로() {
        recorder.recordFailure(history, new InvalidFaceModuleException("-777", "SPOOF", "x"), CLIENT);
        assertThat(history.getFailureMessage()).isEqualTo("SPOOF");

        FaceHistory other = FaceHistory.create(ActionType.MATCH, "", "txn-358b", CLIENT, false, false);
        recorder.recordFailure(other, new InvalidFaceImageException(ErrorType.NO_DOUBLE_SIMILARITY), CLIENT);
        assertThat(other.getFailureMessage()).isEqualTo(ErrorType.NO_DOUBLE_SIMILARITY.name());
    }

    @Test
    @DisplayName("성공 결과 커밋이 실패한 경우는 「성공 + 실패 사유」로 섞어 남기지 않는다 — 시작 상태로 둔다")
    void 성공_커밋_실패는_건드리지_않는다() {
        history.successMatch(true, CLIENT);

        recorder.recordFailure(history, new DataAccessResourceFailureException("db down"), CLIENT);

        verify(faceHistoryRepository, never()).save(any());
        assertThat(history.getFailureMessage()).isNull();
    }

    @Test
    @DisplayName("실패 기록이 실패해도(DB 장애) 원래 예외를 가리지 않는다 — 억제된 예외로 붙는다")
    void 기록_실패는_원래_예외를_가리지_않는다() {
        DataAccessResourceFailureException dbDown = new DataAccessResourceFailureException("db down");
        given(faceHistoryRepository.save(any())).willThrow(dbDown);
        UpstreamCallException cause = new UpstreamCallException(0, "MatchFeign#identify", "연결 실패");

        recorder.recordFailure(history, cause, CLIENT);

        assertThat(cause.getSuppressed()).containsExactly(dbDown);
    }

    @Test
    @DisplayName("finish — 매칭 결과 행과 이력을 함께 저장한다(결과 행이 먼저)")
    void finish는_결과행과_이력을_저장한다() {
        FaceMatch match = FaceMatch.create(history, "", 0.3, 0.85, MatchType.IDENTIFY, CLIENT);

        recorder.finish(history, match);

        InOrder order = inOrder(faceMatchRepository, faceHistoryRepository);
        order.verify(faceMatchRepository).save(match);
        order.verify(faceHistoryRepository).save(history);
    }

    @Test
    @DisplayName("커밋 메서드는 REQUIRED 트랜잭션이다 — REQUIRES_NEW 면 바깥 트랜잭션과 겹칠 때 커넥션 둘을 쥔다(gate UG-336)")
    void 커밋_메서드는_REQUIRED() throws NoSuchMethodException {
        for (Method method : new Method[] {
                FaceHistoryRecorder.class.getMethod("start", FaceHistory.class),
                FaceHistoryRecorder.class.getMethod("finish", FaceHistory.class, FaceMatch.class),
                FaceHistoryRecorder.class.getMethod("fail", FaceHistory.class)}) {
            Transactional tx = method.getAnnotation(Transactional.class);
            assertThat(tx).as(method.getName()).isNotNull();
            assertThat(tx.propagation()).as(method.getName()).isEqualTo(Propagation.REQUIRED);
        }
    }
}
