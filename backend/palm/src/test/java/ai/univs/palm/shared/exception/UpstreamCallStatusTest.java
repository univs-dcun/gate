package ai.univs.palm.shared.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import ai.univs.palm.shared.web.dto.ResponseApi;
import ai.univs.palm.shared.web.enums.ErrorType;
import ai.univs.palm.shared.locale.MessageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;

/**
 * UG-308: 응답을 못 받은 상류 실패는 <b>5xx</b> 로 나간다.
 *
 * <p>이 커밋의 BLOCKER 였다. 초판은 티켓 항목 4를 따라 모든 상류 실패를 400 으로 통일했다 —
 * "다른 실패 경로와 같아지니 일관성이 좋아진다" 는 이유였다. 반박 리뷰가 그 '일관성' 이
 * 정확히 해로운 지점임을 실제 코드로 증명했다.
 *
 * <p>gate 의 {@code CommonErrorDecoder} 는 4xx 를 {@code CustomFeignException} 으로, 5xx 를
 * {@code RemoteCallException} 으로 가른다. 그리고 gate 의 {@code IdentifyPalmUseCase} 는
 * 전자를 catch 해서 <b>정상 결과로 반환</b>한다 (gate 전체에서 rethrow 하지 않는 유일한 지점).
 * 즉 400 으로 바꾸는 순간 팜 모듈 전면 장애가 HTTP 200 "매칭 실패" 로 둔갑한다 — 은행·출입문
 * 게이트 제품에서 생체 모듈이 죽은 것이 단순 불일치로 집계된다.
 *
 * <p><b>핸들러를 직접 호출한다.</b> 처음에는 테스트 안에 같은 삼항식을 복제해 뒀는데, 핸들러를
 * 400 고정으로 되돌리는 변이가 그대로 통과했다 — 동어반복이었다. 프로덕션 코드를 실행해야
 * 이 단언에 의미가 있다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UG-308: 상류 실패 응답 상태")
class UpstreamCallStatusTest {

    @Mock
    private MessageService messageService;

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler(messageService);
        given(messageService.getMessage(any(ErrorType.class))).willReturn("메시지");
    }

    @Test
    @DisplayName("응답을 못 받은 실패(NO_RESPONSE)는 500 이다 — 4xx 면 gate 가 200 으로 삼킨다")
    void 응답없음은_5xx() {
        ResponseEntity<ResponseApi<?>> response = handler.handleUpstreamCallException(
                new UpstreamCallException(UpstreamCallException.NO_RESPONSE, "MatchFeign#identify", "연결 실패"));

        // 두 단언은 서로 다른 것을 지킨다 (델타 리뷰 지적).
        //
        // 5xx 는 <b>안전 성질</b>이다 — gate 의 CommonErrorDecoder 가 4xx 를
        // CustomFeignException 으로 가르고 IdentifyPalmUseCase 가 그것을 정상 결과로
        // 흡수하므로, 4xx 가 되는 순간 모듈 전면 장애가 HTTP 200 "매칭 실패" 로 둔갑한다.
        //
        // 500 은 <b>계약</b>이다. 502·503 도 안전 성질은 만족하지만 클라이언트가 보는 상태가
        // 달라진다. 이 커밋은 "계약을 한 바이트도 바꾸지 않는다" 를 근거로 문서 동기화를
        // 생략했으므로 그 근거를 여기서 못박는다 — 초판은 is5xx 만 봐서 502·503 변이가
        // 살아남았고, 그러면 커밋 메시지의 논거가 무방비였다.
        assertThat(response.getStatusCode())
                .as("4xx 면 gate 의 IdentifyPalmUseCase 가 장애를 정상 결과로 흡수한다")
                .matches(HttpStatusCode::is5xxServerError, "5xx");
        assertThat(response.getStatusCode())
                .as("UG-308 이전에도 이 경로는 500 이었다 — 이 커밋은 계약을 바꾸지 않는다")
                .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    /**
     * 하위 모듈이 오류를 <b>응답한</b> 경우는 UG-299 가 정한 400 그대로다.
     *
     * <p>이 경로까지 5xx 로 올리면 UG-299 가 지킨 계약이 깨진다 — 그쪽은 클라이언트가 보는
     * 응답을 바꾸지 않으려고 일부러 400 에 맞췄다.
     */
    @Test
    @DisplayName("실제 상태 코드가 있는 실패는 400 그대로다")
    void 응답있음은_400() {
        ResponseEntity<ResponseApi<?>> response = handler.handleUpstreamCallException(
                new UpstreamCallException(503, "MatchFeign#identify", "Service Unavailable"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    /** 본문은 두 경우 모두 같다 — 상태 코드만 갈린다. */
    @Test
    @DisplayName("본문 오류 코드는 두 경우 모두 같다")
    void 본문은_같다() {
        var 응답없음 = handler.handleUpstreamCallException(
                new UpstreamCallException(UpstreamCallException.NO_RESPONSE, "op", "r"));
        var 응답있음 = handler.handleUpstreamCallException(
                new UpstreamCallException(503, "op", "r"));

        assertThat(응답없음.getBody().errors().code())
                .isEqualTo(응답있음.getBody().errors().code())
                .isEqualTo(ErrorType.INTERNAL_SERVER_ERROR.getCode());
    }
}
