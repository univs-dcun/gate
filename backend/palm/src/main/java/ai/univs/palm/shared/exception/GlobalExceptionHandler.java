package ai.univs.palm.shared.exception;

import ai.univs.palm.shared.locale.MessageService;
import ai.univs.palm.shared.web.dto.Errors;
import ai.univs.palm.shared.web.dto.ResponseApi;
import ai.univs.palm.shared.web.enums.ErrorType;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private final MessageService messageService;

    /**
     * 오류의 성격에 맞는 수준으로 기록한다 (UG-299).
     *
     * <p>gate 가 UG-290 에서 도입한 규칙을 그대로 가져왔다. 이 서비스는 그때 손대지 않아 모든
     * 예외가 {@code log.error} + 스택트레이스로 남고 있었다. 손바닥이 잡히지 않은 사진 한 장이 들어와도
     * ERROR 에 90여 줄이 붙는다 — 원인이 클라이언트 입력인데 서버 오류로 기록되니, 에러 대시보드를
     * 붙이면 오탐이 쌓여 진짜 5xx 가 묻힌다.
     *
     * <p>판정 기준은 {@link ErrorType#getStatus()} 다.
     *
     * <ul>
     *   <li>4xx — 클라이언트 입력이 원인이다. WARN 으로 남기고 스택트레이스는 생략한다. 우리 쪽
     *       호출 스택은 매번 같아서 정보가 없다.
     *   <li>5xx — 우리 쪽 문제다. ERROR + 스택트레이스를 유지한다.
     * </ul>
     *
     * <p>{@code detail} 은 예외 종류별로 덧붙일 정보다 (없으면 {@code null}).
     */
    private void logByStatus(ErrorType errorType, Exception ex, String detail) {
        String suffix = detail == null ? "" : " — " + detail;

        if (errorType.getStatus().is4xxClientError()) {
            log.warn("[{}] {} {}{}", errorType.getCode(), errorType.name(), requestInfo(), suffix);
            return;
        }
        log.error("[{}] {} {}{}", errorType.getCode(), errorType.name(), requestInfo(), suffix, ex);
    }

    /**
     * 어느 요청이었는지 붙인다 (UG-299).
     *
     * <p>이 핸들러는 예외 객체는 알지만 요청은 모른다. 경로가 없으면 컨트롤러 진입 전에 터진
     * 예외(바인딩·검증)가 어느 엔드포인트에서 났는지 알 방법이 없다.
     */
    private String requestInfo() {
        if (RequestContextHolder.getRequestAttributes()
                instanceof ServletRequestAttributes attributes) {
            HttpServletRequest request = attributes.getRequest();
            return "%s %s".formatted(request.getMethod(), request.getRequestURI());
        }
        return "(요청 정보 없음)";
    }

    /**
     * 이 서비스의 대표 예외 (UG-299).
     *
     * <p><b>여기에는 로그 호출이 아예 없었다.</b> 그리고 {@code CommonErrorDecoder} 가 ML 모듈의
     * 3xx/5xx 를 {@code CustomPalmException(INTERNAL_SERVER_ERROR)} 로 감싸 던진다. 즉
     * <b>ML 매처가 전면 장애여도 palm 서비스 로그에는 한 줄도 남지 않았고</b>, gate 에는
     * {@code @ResponseStatus(BAD_REQUEST)} 때문에 400 으로 도착했다. 어느 서비스에서도 ERROR 가
     * 남지 않는 구간이 있었던 셈이다.
     *
     * <p>gate 쪽에서는 UG-290 이 {@code CustomFeignException.type} 이
     * {@code INTERNAL_SERVER_ERROR} 계열이면 ERROR 로 올리도록 막아 뒀다. 그것은 증상 쪽 대응이고,
     * 원인은 이 서비스가 자기 장애를 기록하지 않는다는 것이었다.
     */
    @ExceptionHandler(CustomPalmException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ResponseApi<?> handlePalmCustomException(CustomPalmException ex) {
        logByStatus(ex.getErrorType(), ex, null);

        return getExceptionResponse(ex.getErrorType());
    }

    /**
     * ML 모듈 호출 실패 (UG-299 반박 리뷰).
     *
     * <p>{@link UpstreamCallException} 은 {@link CustomPalmException} 하위이므로 이 핸들러가
     * 없어도 위 핸들러가 잡는다. 따로 둔 이유는 두 가지다.
     *
     * <ul>
     *   <li>하위 모듈의 상태 코드를 남긴다. 위 핸들러가 찍는 {@code ErrorType.name()} 은
     *       {@code INTERNAL_SERVER_ERROR} 고정이라 502·503·리다이렉트를 구분할 수 없다.
     *   <li><b>한 번만 남긴다.</b> 처음에는 {@code CommonErrorDecoder} 에서 찍었는데, 그러면
     *       디코더 한 줄 + 이 핸들러 한 줄로 ERROR 가 둘이 됐다. ML 모듈이 죽어서 초당 50 요청이
     *       실패하면 초당 ERROR 100 줄이다. UG-291 이 gate 에서 없앤 이중 기록과 같은 문제다.
     * </ul>
     *
     * <p>스택트레이스는 {@code cause} 가 있을 때만 남긴다. 하위 모듈이 오류를 <b>응답한</b>
     * 경우는 우리 호출 스택이 매번 같아 정보가 없고, 응답을 <b>해석하지 못한</b> 경우는 우리 쪽
     * 파싱 문제일 수 있어 단서가 된다.
     *
     * <p>응답 본문은 위 핸들러와 동일하다 ({@code SWAGGER-005}). 상태 코드는 UG-308 에서
     * 갈렸다 — 하위 모듈이 오류를 <b>응답한</b> 경우는 UG-299 가 맞춘 400 그대로고, 응답을
     * <b>못 받은</b> 경우({@code NO_RESPONSE})는 500 이다. 두 값 모두 UG-308 이전 거동과
     * 같으므로 클라이언트가 보는 계약은 바뀌지 않는다. 이유는 아래 분기 주석에 있다.
     */
    @ExceptionHandler(UpstreamCallException.class)
    public ResponseEntity<ResponseApi<?>> handleUpstreamCallException(UpstreamCallException ex) {
        // cause 를 마지막 인자로 넘긴다. slf4j 는 마지막 인자가 Throwable 일 때만 스택트레이스로
        // 취급하므로, null 이면 자리표시자 개수를 넘는 여분 인자로 조용히 무시된다. 즉 분기를
        // 따로 쓸 필요가 없다 — 실제로 if/else 로 나눠 봤더니 관측 가능한 차이가 없었다.
        log.error("[{}] ML 모듈 호출 실패 {} — operation={}, upstreamStatus={}, reason={}",
                ex.getErrorType().getCode(), requestInfo(),
                ex.getOperation(), ex.getUpstreamStatus(), ex.getReason(), ex.getCause());

        // UG-308: 응답을 못 받은 실패만 5xx 로 남긴다. 나머지(하위 모듈이 오류를 응답한
        // 경우)는 UG-299 가 정한 대로 400 그대로다.
        //
        // 처음에는 전부 400 으로 통일했다 — 티켓 항목 4가 "다른 실패 경로와 같아지니
        // 일관성이 좋아진다" 고 봤기 때문이다. 반박 리뷰가 그 '일관성' 이 정확히 해로운
        // 지점임을 실측으로 보여 줬다.
        //
        // gate 의 CommonErrorDecoder 는 4xx 를 CustomFeignException 으로, 5xx 를
        // RemoteCallException 으로 가른다. 그리고 gate 의 IdentifyPalmUseCase 는 전자를
        // catch 해서 **정상 결과로 반환**한다 (rethrow 하지 않는 유일한 지점). 즉 400 으로
        // 바꾸는 순간 팜 모듈 전면 장애가 HTTP 200 "매칭 실패" 로 둔갑한다 — 은행·출입문
        // 게이트 제품에서 생체 모듈이 죽은 것이 단순 불일치로 집계된다.
        //
        // 부수적으로 두 가지가 더 따라온다. 400 이면 gate 가 우리 SWAGGER-005 를 그대로
        // 외부에 노출하고(원래는 gate 자신의 PJ-005), gate 쪽 로그에서 operation·
        // upstreamStatus 가 사라진다.
        //
        // 500 을 그대로 두면 이 변경은 계약을 전혀 건드리지 않는다 — 예전에도
        // handleGlobalException 이 500 이었다. 순수하게 관측성만 좋아진다.
        // (502 Bad Gateway 가 의미상 더 정확하지만, 계약을 바꾸지 않는 쪽을 택했다.)
        HttpStatus status = ex.getUpstreamStatus() == UpstreamCallException.NO_RESPONSE
                ? HttpStatus.INTERNAL_SERVER_ERROR
                : HttpStatus.BAD_REQUEST;

        return ResponseEntity.status(status).body(getExceptionResponse(ex.getErrorType()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ResponseApi<?> handleMethodArgumentNotValidException(MethodArgumentNotValidException ex) {
        StringBuilder messageBuilder = new StringBuilder();

        // @Valid 검증에서 발생된 1 ~ n 개의 메시지를 합친 StringBuilder 메시지로 사용.
        ex.getBindingResult().getAllErrors().forEach((error) -> {
            String message = messageService.getMessage(error.getDefaultMessage());
            messageBuilder.append(message).append(" ");
        });

        // 어느 필드가 문제였는지 따로 모은다. i18n 해석 결과만 남기면 "MUST NOT BE BLANK" 처럼
        // 필드를 알 수 없는 줄이 된다. 거부값(rejected value)은 일부러 빼놓는다 — 이미지 바이트나
        // descriptor 가 들어올 수 있다.
        String fields = ex.getBindingResult().getAllErrors().stream()
                .map(error -> error instanceof FieldError fieldError
                        ? fieldError.getField()
                        : error.getObjectName())
                .distinct()
                .collect(Collectors.joining(", "));
        logByStatus(ErrorType.INVALID_INPUT, ex,
                "fields=[%s] %s".formatted(fields, messageBuilder.toString().strip()));

        return getExceptionResponse(ErrorType.INVALID_INPUT, messageBuilder.toString());
    }

    @ExceptionHandler(InvalidPalmImageException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ResponseApi<?> handleInvalidPalmImageException(InvalidPalmImageException ex) {
        // 이 핸들러에도 로그가 없었다. 손바닥 미검출 같은 정상적인 거부라 WARN 이 맞지만,
        // 한 줄도 없으면 "왜 거부됐는지" 를 사후에 확인할 수 없다.
        logByStatus(ex.getErrorType(), ex, null);

        String message = messageService.getMessage(ex.getErrorType().name());

        return getExceptionResponse(ErrorType.INVALID_INPUT, message);
    }

    @ExceptionHandler(InvalidPalmModuleException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ResponseApi<?> handleInvalidPalmModuleException(InvalidPalmModuleException ex) {
        logUpstream(ex.getCode(), ex.getType(), ex.getMessage());

        Errors errors = new Errors(ex.getCode(), ex.getType(), ex.getMessage());
        return getExceptionResponse(errors);
    }

    @ExceptionHandler(CustomFeignException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ResponseApi<?> CustomFeignException(CustomFeignException ex) {
        logUpstream(ex.getCode(), ex.getType(), ex.getMessage());

        Errors errors = new Errors(ex.getCode(), ex.getType(), ex.getMessage());
        return getExceptionResponse(errors);
    }

    /**
     * 하위(ML) 모듈이 자기 오류라고 말한 타입.
     *
     * <p>gate 의 {@code GlobalExceptionHandler.UPSTREAM_SERVER_ERROR_TYPES} 와 같은 기준이다.
     * 한쪽만 바뀌면 그 계층에서만 조용해진다.
     */
    private static final Set<String> UPSTREAM_SERVER_ERROR_TYPES =
            Set.of("INTERNAL_SERVER_ERROR", "SERVER_ERROR", "INTERNAL_ERROR");

    /**
     * ML 모듈이 자기 포맷으로 돌려준 오류 (UG-299).
     *
     * <p>{@link ErrorType} 매핑이 없어 {@link #logByStatus} 를 쓸 수 없다. 이 예외가 만들어지는
     * 지점({@code CommonErrorDecoder})이 상태 코드 400~499 일 때만이므로 HTTP 상태로는 언제나
     * 4xx 지만, 그렇다고 클라이언트 잘못인 것은 아니다. {@code type} 이 하위가 자기 문제라고
     * 말하는 값이면 ERROR 로 올린다. 판정 불가일 때는 WARN 으로 둔다 — 여기서 잘못 올리면
     * 라이브니스 실패처럼 정상 흐름에서 흡수되는 것까지 ERROR 가 된다.
     *
     * <p>스택트레이스는 남기지 않는다. 원인은 하위 모듈이고 우리 쪽 호출 스택은 매번 같다.
     * 하위 장애 시에는 이 예외가 대량으로 발생한다.
     */
    private void logUpstream(String code, String type, String message) {
        if (type != null && UPSTREAM_SERVER_ERROR_TYPES.contains(type)) {
            log.error("[{}] 하위 모듈이 자기 오류를 알렸다 — {} {} — {}",
                    code, type, requestInfo(), message);
            return;
        }
        log.warn("[{}] {} {} — {}", code, type, requestInfo(), message);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ResponseApi<?> handleNoResourceFoundException(NoResourceFoundException ex) {
        logByStatus(ErrorType.NOT_FOUND, ex, null);

        return getExceptionResponse(ErrorType.NOT_FOUND);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    @ResponseStatus(HttpStatus.METHOD_NOT_ALLOWED)
    public ResponseApi<?> handleHttpRequestMethodNotSupportedException(HttpRequestMethodNotSupportedException ex) {
        logByStatus(ErrorType.METHOD_NOT_ALLOWED, ex, ex.getMessage());

        return getExceptionResponse(ErrorType.METHOD_NOT_ALLOWED);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    @ResponseStatus(HttpStatus.METHOD_NOT_ALLOWED)
    public ResponseApi<?> handleHttpMediaTypeNotSupportedException(HttpMediaTypeNotSupportedException ex) {
        logByStatus(ErrorType.METHOD_NOT_ALLOWED, ex, ex.getMessage());

        return getExceptionResponse(ErrorType.METHOD_NOT_ALLOWED);
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ResponseApi<?> handleGlobalException(Exception ex) {
        logByStatus(ErrorType.INTERNAL_SERVER_ERROR, ex, ex.getMessage());

        return getExceptionResponse(ErrorType.INTERNAL_SERVER_ERROR);
    }

    private ResponseApi<?> getExceptionResponse(ErrorType errorType) {
        return getExceptionResponse(errorType, messageService.getMessage(errorType));
    }

    private ResponseApi<?> getExceptionResponse(ErrorType errorType, String message) {
        return ResponseApi.fail(Errors.from(errorType, message));
    }

    private ResponseApi<?> getExceptionResponse(Errors errors) {
        return ResponseApi.fail(errors);
    }
}
