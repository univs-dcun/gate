package ai.univs.match.shared.exception;

import ai.univs.match.shared.locale.MessageService;
import ai.univs.match.shared.web.dto.Errors;
import ai.univs.match.shared.web.dto.ResponseApi;
import ai.univs.match.shared.web.enums.ErrorType;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
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

import java.sql.SQLTransientConnectionException;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    /** 503 에 싣는 {@code Retry-After} 초 (UG-359). gate 와 같은 값이다. */
    public static final String RETRY_AFTER_SECONDS = "1";

    private final MessageService messageService;

    /**
     * 오류의 성격에 맞는 수준으로 기록한다 (UG-299).
     *
     * <p>gate 가 UG-290 에서 도입한 규칙을 그대로 가져왔다. 이 서비스는 그때 손대지 않아 모든
     * 예외가 {@code log.error} + 스택트레이스로 남고 있었다. 지원하지 않는 descriptor 버전이
     * 하나 들어와도 ERROR 에 90여 줄이 붙는다 — 원인이 클라이언트 입력인데 서버 오류로 기록되니,
     * 에러 대시보드를 붙이면 오탐이 쌓여 진짜 5xx 가 묻힌다.
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

    @ExceptionHandler(CustomFaceMatcherException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ResponseApi<?> handleFaceMatcherCustomException(CustomFaceMatcherException ex) {
        logByStatus(ex.getErrorType(), ex, null);

        return getExceptionResponse(ex.getErrorType());
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

        // 어느 필드가 문제였는지 따로 모은다. i18n 해석 결과만 남기면 "MUST NOT BE BLANK" 처럼
        // 필드를 알 수 없는 줄이 된다. 거부값(rejected value)은 일부러 빼놓는다 — descriptor
        // 원문이 그대로 들어온다.
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
     * catch-all 의 맨 앞에서 원인 사슬을 본다({@link PoolExhaustion}). 풀 고갈이면 503 + {@code SWAGGER-006} +
     * {@code Retry-After} 이고, gate 가 그것을 받아 자기 클라이언트에게 {@code PJ-006} 으로 전한다. 아니면 예전과
     * 똑같이 500 + ERROR 스택트레이스다.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ResponseApi<?>> handleGlobalException(Exception ex) {
        Optional<SQLTransientConnectionException> poolTimeout = PoolExhaustion.find(ex);
        if (poolTimeout.isPresent()) {
            logPoolExhaustion(poolTimeout.get(), ex);
            return temporarilyUnavailable();
        }

        logByStatus(ErrorType.INTERNAL_SERVER_ERROR, ex, ex.getMessage());

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(getExceptionResponse(ErrorType.INTERNAL_SERVER_ERROR));
    }

    /**
     * 풀 고갈의 로그 수준을 원인 유무로 가른다 (UG-359). 기준은 {@link PoolExhaustion#isCongestion} 설명 참고.
     *
     * <p>혼잡이면 스택트레이스를 남기지 않는다. 버스트에서는 이 줄이 요청 수만큼 나오고 호출 스택은 매번 같은
     * 트랜잭션 진입 경로다. 대신 Hikari 의 메시지(풀 이름·active·idle·waiting)를 그대로 싣는다.
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
     * 503 + {@code SWAGGER-006} + {@code Retry-After} (UG-359). gate 의 같은 이름 메서드와 같은 모양이다.
     *
     * <p>본문의 {@code errors.type} 이 gate 와의 계약이다 — gate 의 디코더가 503 이면서 이 유형일 때만 "잠시 뒤
     * 다시" 로 읽는다. {@code Retry-After} 는 gate 가 쓰지 않지만 HTTP 503 의 관례라 함께 싣는다.
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
