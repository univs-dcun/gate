package ai.univs.gate.shared.exception;

import ai.univs.gate.shared.web.dto.Errors;
import ai.univs.gate.shared.web.dto.ResponseApi;
import ai.univs.gate.shared.web.enums.ErrorType;
import ai.univs.gate.support.message.MessageService;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.sql.SQLException;
import java.sql.SQLTransientConnectionException;
import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    /** 503 에 싣는 {@code Retry-After} 초 (UG-359). 문서(gate-api-docs.html PJ-006)와 같은 값이다. */
    public static final String RETRY_AFTER_SECONDS = "1";

    private final MessageService messageService;

    @ExceptionHandler({
            BusinessException.class,
    })
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ResponseApi<?> handleBusinessException(BusinessException ex) {
        logByStatus(ex.getErrorType(), ex, null);

        return getExceptionResponse(ex.getErrorType(), messageService.getMessage(ex.getMessageKey()));
    }

    /**
     * 오류의 성격에 맞는 수준으로 기록한다 (UG-290).
     *
     * <p>예전에는 모든 예외를 {@code log.error} + 스택트레이스로 남겼다. 잘못된 API 키로 한 번
     * 호출하면 ERROR 두 줄에 90여 줄 스택트레이스가 붙었다 — 원인이 클라이언트 입력인데 서버 오류로
     * 기록되니, 에러 대시보드를 붙이면 오탐이 쌓여 진짜 5xx 가 묻힌다.
     *
     * <p>판정 기준은 {@link ErrorType#getStatus()} 다. 이 필드는 값이 다 채워져 있는데도
     * 프로덕션 코드에서 한 번도 읽히지 않는 죽은 코드였다 (UG-280 조사에서 확인). 별도 분류를
     * 새로 만드는 것보다 이미 정확한 값을 살려 쓰는 편이 낫다.
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
     * 어느 요청이었는지 붙인다 (UG-291).
     *
     * <p>이 핸들러는 예외 객체는 알지만 요청은 모르고, {@code LoggingAspect} 는 반대다. 두 지점이
     * 서로를 모른 채 같은 사건을 기록해 왔다. 여기에 경로를 붙이면 이 한 줄만으로 원인을 특정할 수
     * 있어, 컨트롤러 진입 전에 터진 예외(바인딩·검증)도 추적 가능해진다.
     *
     * <p>{@code MDC} 가 아니라 {@code RequestContextHolder} 를 쓴 이유는 이 클래스 안에서 끝나기
     * 때문이다 — 인터셉터·애스펙트에 MDC 키를 심고 정리 시점을 맞추는 결합이 생기지 않는다.
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
     * 하위 서비스 호출 실패 (UG-280).
     *
     * <p>{@link RemoteCallException} 은 {@link BusinessException} 하위이므로 이 핸들러가 없어도
     * 위 핸들러가 잡는다. 따로 둔 이유는 두 가지다.
     *
     * <ul>
     *   <li>하위 서비스의 상태 코드를 로그에 남긴다. 위 핸들러가 찍는 {@link ErrorType#name()} 은
     *       {@code INTERNAL_SERVER_ERROR} 고정이라 502·503·타임아웃을 구분할 수 없다.
     *   <li>스택트레이스를 남기지 않는다. 원인은 하위 서비스이고 우리 쪽 호출 스택은 매번 같아서
     *       90여 줄이 반복될 뿐이다. 하위 장애 시에는 이 예외가 대량으로 발생한다.
     * </ul>
     *
     * <p>응답 본문·상태 코드는 위 핸들러와 동일하다 ({@code PJ-005}, 400). 클라이언트가 보는
     * 계약을 바꾸지 않기 위해 일부러 맞췄다.
     *
     * <p>{@code ErrorType} 이 {@code INTERNAL_SERVER_ERROR}(5xx) 이므로 UG-290 의 분류상 ERROR 로
     * 남는다. 원인이 우리 쪽 인프라·하위 서비스이므로 그게 맞다 — 클라이언트 입력 문제가 아니다.
     *
     * <p><b>예외 하나 (UG-359).</b> 하위가 503 + {@code TEMPORARILY_UNAVAILABLE} 로 "잠시 뒤 다시" 를 알려 온
     * 경우({@link RemoteCallException#isTemporarilyUnavailable()})는 400 이 아니라 503 + {@code PJ-006} +
     * {@code Retry-After} 다. 상태 코드가 갈리므로 {@code @ResponseStatus} 대신 {@link ResponseEntity} 로
     * 돌려준다. 나머지 하위 실패(다른 5xx·응답 없음)는 예전 그대로 400 {@code PJ-005} 다.
     */
    @ExceptionHandler(RemoteCallException.class)
    public ResponseEntity<ResponseApi<?>> handleRemoteCallException(RemoteCallException ex) {
        // UG-359: 하위(face)가 "잠시 뒤 다시" 를 알려 왔다. 그쪽 풀이 붐볐을 뿐이므로 WARN 이고 스택트레이스는
        // 없다 — DB 에 닿지 못하는 것이었다면 그 서비스가 자기 로그에 ERROR 로 남겼다. 여기서도 ERROR 를 남기면
        // 버스트 한 번에 하위·gate 양쪽 대시보드가 같은 사건으로 가득 찬다.
        if (ex.isTemporarilyUnavailable()) {
            log.warn("[{}] 하위 서비스가 일시적으로 처리할 수 없다고 알렸다 {} — operation={}, upstreamStatus={}",
                    ex.getErrorType().getCode(), requestInfo(), ex.getOperation(), ex.getUpstreamStatus());
            return temporarilyUnavailable();
        }

        log.error("[{}] 하위 서비스 호출 실패 {} — operation={}, upstreamStatus={}{}",
                ex.getErrorType().getCode(),
                requestInfo(),
                ex.getOperation(),
                ex.getUpstreamStatus(),
                ex.isNoResponse() ? " (응답 없음 — 연결 거부·타임아웃)" : "");

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(getExceptionResponse(ex.getErrorType()));
    }

    /**
     * 하위 서비스가 우리 포맷으로 돌려준 오류 (UG-290).
     *
     * <p>{@link CustomFeignException} 은 {@link BusinessException} 계열이 아니라 자체
     * {@code code}/{@code type} 을 들고 있어 {@link ErrorType} 매핑이 없다. 이 예외가 만들어지는
     * 지점({@code CommonErrorDecoder})이 상태 코드 400~499 일 때만이므로 <b>HTTP 상태로는</b>
     * 언제나 4xx 다.
     *
     * <p><b>그렇다고 클라이언트 잘못인 것은 아니다.</b> 반박 리뷰가 짚은 부분이다. face·palm 은
     * 자기 쪽 5xx 를 {@code CustomFaceException(INTERNAL_SERVER_ERROR)} 로 감싼 뒤
     * {@code @ResponseStatus(BAD_REQUEST)} 로 내려보내고, 그 과정에서 로그를 남기지 않는다.
     * 즉 ML 매처가 전면 장애여도 gate 에는 400 으로 도착한다. HTTP 상태만 보고 WARN 으로 내리면
     * <b>어느 서비스에서도 ERROR 가 한 줄도 남지 않는다.</b>
     *
     * <p>그래서 {@code type} 을 본다. 우리 서비스들은 모두 {@code Errors.from(errorType, ...)} 으로
     * {@code type} 에 {@code ErrorType.name()} 을 싣는다 — 하위가 자기 문제라고 말한 것을 그대로
     * 읽는 셈이다. 판정 불가일 때는 4xx 로 둔다. 여기서 잘못 올리면 라이브니스 오류처럼 정상
     * 흐름에서 흡수되는 것까지 ERROR 가 된다.
     */
    /**
     * {@code LoggingAspect} 가 같은 기준을 쓰도록 공개한다 — 두 지점이 갈리면 한쪽만 조용해진다.
     * 판정 집합은 {@link UpstreamErrorTypes} 에 있다 (UG-338: 등록 이력도 같은 기준을 쓴다).
     */
    public static boolean isUpstreamServerError(String type) {
        return UpstreamErrorTypes.isServerError(type);
    }

    @ExceptionHandler(CustomFeignException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ResponseApi<?> CustomFeignException(CustomFeignException ex) {
        if (isUpstreamServerError(ex.getType())) {
            log.error("[{}] 하위 서비스가 자기 오류를 알렸다 — {} {} — {}",
                    ex.getCode(), ex.getType(), requestInfo(), ex.getMessage());
        } else {
            log.warn("[{}] {} {} — {}", ex.getCode(), ex.getType(), requestInfo(), ex.getMessage());
        }

        Errors errors = new Errors(
                ex.getCode(),
                ex.getType(),
                ex.getMessage());
        return ResponseApi.fail(errors);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ResponseApi<?> handleMethodArgumentNotValidException(MethodArgumentNotValidException ex) {
        StringBuilder messageBuilder = new StringBuilder();

        // CLIENT_INPUT_ERROR 의 경우 @Valid 검증에서 발생된 1 ~ n 개의 메시지를 합친 StringBuilder 메시지로 사용.
        ex.getBindingResult().getAllErrors().forEach((error) -> {
            String message = messageService.getMessage(error.getDefaultMessage());
            messageBuilder.append(message).append(" ");
        });

        // 반박 리뷰: 예전 로그는 ex.getMessage() 를 통째로 찍어 "어느 필드가" 를 담고 있었다.
        // i18n 해석 결과만 남기면 "MUST NOT BE BLANK" 처럼 필드를 알 수 없는 줄이 된다
        // (메시지 키 없이 @NotBlank 만 쓴 DTO 가 여럿 있다). 필드명만 따로 모은다.
        //
        // 거부값(rejected value)은 일부러 빼놓는다. 그쪽에는 비밀번호·descriptor 가 들어올 수
        // 있는데 LoggingAspect 의 마스킹은 이 경로에 적용되지 않는다.
        String fields = ex.getBindingResult().getAllErrors().stream()
                .map(error -> error instanceof FieldError fieldError ? fieldError.getField() : error.getObjectName())
                .distinct()
                .collect(Collectors.joining(", "));
        logByStatus(ErrorType.INVALID_INPUT, ex,
                "fields=[%s] %s".formatted(fields, messageBuilder.toString().strip()));

        return getExceptionResponse(ErrorType.INVALID_INPUT, messageBuilder.toString());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ResponseApi<?> handleHttpMessageNotReadableException(HttpMessageNotReadableException ex) {
        logByStatus(ErrorType.INVALID_INPUT, ex, ex.getMessage());

        Throwable cause = ex.getCause();
        if (cause instanceof InvalidFormatException ife
                && ife.getTargetType() != null
                && ife.getTargetType().isEnum()) {

            String fieldName = ife.getPath().isEmpty()
                    ? "unknown"
                    : ife.getPath().get(ife.getPath().size() - 1).getFieldName();
            String acceptedValues = Arrays.stream(ife.getTargetType().getEnumConstants())
                    .map(Object::toString)
                    .collect(Collectors.joining(", "));
            String message = messageService.getMessage(ErrorType.INVALID_INPUT)
                    + String.format(" (%s: [%s])", fieldName, acceptedValues);

            return getExceptionResponse(ErrorType.INVALID_INPUT, message);
        }

        return getExceptionResponse(ErrorType.INVALID_INPUT);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ResponseApi<?> handleMethodArgumentTypeMismatchException(MethodArgumentTypeMismatchException ex) {
        logByStatus(ErrorType.INVALID_INPUT, ex, ex.getMessage());

        return getExceptionResponse(ErrorType.INVALID_INPUT);
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

    /**
     * 나머지 전부 — 그리고 DB 커넥션 풀 고갈 (UG-359).
     *
     * <p>풀 고갈은 전용 예외 타입이 없다. Spring 이 트랜잭션을 열다 났으면
     * {@code CannotCreateTransactionException}, JDBC 접근 중이면 {@code DataAccessException} 계열로 감싸므로
     * 타입별 핸들러를 걸면 경로 하나를 빠뜨리기 쉽다. 그래서 catch-all 의 맨 앞에서 원인 사슬을 본다
     * ({@link PoolExhaustion}). 아니면 예전과 똑같이 500 + ERROR 스택트레이스다.
     *
     * <p>{@code BusinessException}·{@code RemoteCallException} 은 자기 핸들러가 먼저 잡으므로 여기 오지
     * 않는다 — 그 흐름은 바뀌지 않는다.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ResponseApi<?>> handleGlobalException(Exception ex) {
        Optional<SQLTransientConnectionException> poolTimeout = PoolExhaustion.find(ex);
        if (poolTimeout.isPresent()) {
            logPoolExhaustion(poolTimeout.get(), ex);
            return temporarilyUnavailable();
        }
        // DB 가 상한 안에 응답하지 않았다 (UG-367) — 멈췄거나 극도로 느리다. 다시 보내면 될 수 있는 실패라 503 으로 같게 알린다.
        Optional<SQLException> dbReadTimeout = PoolExhaustion.findReadTimeout(ex);
        if (dbReadTimeout.isPresent()) {
            log.error("[{}] DB 응답 시간 초과 {} — {}",
                    ErrorType.TEMPORARILY_UNAVAILABLE.getCode(), requestInfo(), dbReadTimeout.get().getMessage(), ex);
            return temporarilyUnavailable();
        }

        logByStatus(ErrorType.INTERNAL_SERVER_ERROR, ex, ex.getMessage());

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(getExceptionResponse(ErrorType.INTERNAL_SERVER_ERROR));
    }

    /**
     * 풀 고갈의 로그 수준을 원인 유무로 가른다 (UG-359). 기준은 {@link PoolExhaustion#isCongestion} 설명 참고.
     *
     * <p>혼잡이면 스택트레이스를 남기지 않는다. 버스트에서는 이 줄이 요청 수만큼 나오고, 호출 스택은 매번 같은
     * 트랜잭션 진입 경로라 정보가 없다. 대신 Hikari 의 메시지(풀 이름·active·idle·waiting)를 그대로 싣는다 —
     * 풀 크기를 조정할 때 필요한 숫자가 거기 있다.
     */
    private void logPoolExhaustion(SQLTransientConnectionException poolTimeout, Exception ex) {
        ErrorType errorType = ErrorType.TEMPORARILY_UNAVAILABLE;
        if (PoolExhaustion.isCongestion(poolTimeout)) {
            log.warn("[{}] DB 커넥션 풀 혼잡 {} — {}",
                    errorType.getCode(), requestInfo(), poolTimeout.getMessage());
            return;
        }
        log.error("[{}] DB 커넥션을 새로 만들지 못한다 {} — {}",
                errorType.getCode(), requestInfo(), poolTimeout.getMessage(), ex);
    }

    /**
     * 503 + {@code PJ-006} + {@code Retry-After} (UG-359).
     *
     * <p>{@code Retry-After} 는 초 단위다. 풀 대기({@code connection-timeout})가 이미 지나간 뒤라 곧바로 다시
     * 보내면 같은 혼잡에 부딪히기 쉽다 — 1초 쉬고 오라는 신호다. 값은 고정이다. 실제 대기열 길이로 계산하려면
     * 풀 상태를 읽어야 하는데, 그 정확도가 클라이언트 재시도 품질을 바꾸지는 않는다.
     */
    private ResponseEntity<ResponseApi<?>> temporarilyUnavailable() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header(HttpHeaders.RETRY_AFTER, RETRY_AFTER_SECONDS)
                .body(getExceptionResponse(ErrorType.TEMPORARILY_UNAVAILABLE));
    }

    private ResponseApi<?> getExceptionResponse(ErrorType errorType) {
        return getExceptionResponse(errorType, messageService.getMessage(errorType));
    }

    private ResponseApi<?> getExceptionResponse(ErrorType errorType, String message) {
        return ResponseApi.fail(Errors.from(errorType, message));
    }
}
