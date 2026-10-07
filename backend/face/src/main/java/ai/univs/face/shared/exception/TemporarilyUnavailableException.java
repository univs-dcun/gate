package ai.univs.face.shared.exception;

import ai.univs.face.shared.web.enums.ErrorType;
import lombok.Getter;

/**
 * 하위 서비스(match)가 "지금은 처리할 여력이 없다" 고 알려 왔다 (UG-359).
 *
 * <p>match 는 자기 DB 커넥션 풀이 모자라면 503 + {@code TEMPORARILY_UNAVAILABLE} 로 답한다. 예전 디코더는 그것을
 * 다른 5xx 와 똑같이 {@link UpstreamCallException} 으로 만들어 400 {@code SWAGGER-005} 로 넘겼고, gate 는 다시
 * 400 {@code PJ-005} 로 내보냈다 — 클라이언트는 "다시 보내면 되는 실패" 인지 알 수 없었다.
 *
 * <p>이 예외는 그 신호를 위로 그대로 전한다. 전용 핸들러가 503 + {@code SWAGGER-006} 으로 내보내고, gate 의
 * 디코더가 그것을 다시 {@code PJ-006} 으로 바꾼다 — match → face → gate 로 한 번도 뭉개지지 않고 올라간다.
 *
 * <p>{@link CustomFaceException} 을 상속하는 이유: 유스케이스의 {@code catch (RuntimeException e)} 가
 * {@code FaceHistoryRecorder.recordFailure} 로 이력을 남길 때 {@code getErrorType()} 에서 실패 유형을 읽는다. 그래서
 * 이력의 {@code failure_message} 에 {@code TEMPORARILY_UNAVAILABLE} 이 남는다. {@link UpstreamCallException} 을
 * 상속하지 않는 것은 그쪽 핸들러가 400/500 + ERROR 로 처리하기 때문이다 — 혼잡을 장애로 기록하지 않는다.
 */
@Getter
public class TemporarilyUnavailableException extends CustomFaceException {

    /** 어느 호출이었는지 (Feign methodKey). 로그용이다 — 응답에는 넣지 않는다. */
    private final String operation;

    public TemporarilyUnavailableException(String operation) {
        super(ErrorType.TEMPORARILY_UNAVAILABLE);
        this.operation = operation;
    }
}
