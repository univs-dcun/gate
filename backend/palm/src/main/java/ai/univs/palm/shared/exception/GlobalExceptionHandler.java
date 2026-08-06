package ai.univs.palm.shared.exception;

import ai.univs.palm.shared.locale.MessageService;
import ai.univs.palm.shared.web.dto.Errors;
import ai.univs.palm.shared.web.dto.ResponseApi;
import ai.univs.palm.shared.web.enums.ErrorType;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
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
