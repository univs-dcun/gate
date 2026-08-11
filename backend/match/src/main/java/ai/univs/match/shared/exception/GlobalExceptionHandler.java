package ai.univs.match.shared.exception;

import ai.univs.match.shared.locale.MessageService;
import ai.univs.match.shared.web.dto.Errors;
import ai.univs.match.shared.web.dto.ResponseApi;
import ai.univs.match.shared.web.enums.ErrorType;
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
}
