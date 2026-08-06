package ai.univs.gate.shared.exception;

import ai.univs.gate.shared.web.dto.Errors;
import ai.univs.gate.shared.web.dto.ResponseApi;
import ai.univs.gate.shared.web.enums.ErrorType;
import ai.univs.gate.support.message.MessageService;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Arrays;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private final MessageService messageService;

    @ExceptionHandler({
            BusinessException.class,
    })
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ResponseApi<?> handleBusinessException(BusinessException ex) {
        logByStatus(ex.getErrorType(), ex, null);

        return getExceptionResponse(ex.getErrorType());
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
     */
    @ExceptionHandler(RemoteCallException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ResponseApi<?> handleRemoteCallException(RemoteCallException ex) {
        log.error("[{}] 하위 서비스 호출 실패 {} — operation={}, upstreamStatus={}{}",
                ex.getErrorType().getCode(),
                requestInfo(),
                ex.getOperation(),
                ex.getUpstreamStatus(),
                ex.isNoResponse() ? " (응답 없음 — 연결 거부·타임아웃)" : "");

        return getExceptionResponse(ex.getErrorType());
    }

    /**
     * 하위 서비스가 우리 포맷으로 돌려준 4xx (UG-290).
     *
     * <p>{@link CustomFeignException} 은 {@link BusinessException} 계열이 아니라 자체
     * {@code code}/{@code type} 을 들고 있어 {@link ErrorType} 매핑이 없다. 다만 이 예외가 만들어지는
     * 지점({@code CommonErrorDecoder})이 <b>상태 코드 400~499 일 때만</b>이므로 언제나 4xx 다.
     * 라이브니스 오류처럼 정상 흐름에서 흡수되는 경우도 여기 포함된다.
     */
    @ExceptionHandler(CustomFeignException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ResponseApi<?> CustomFeignException(CustomFeignException ex) {
        log.warn("[{}] {} {} — {}", ex.getCode(), ex.getType(), requestInfo(), ex.getMessage());

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

        logByStatus(ErrorType.INVALID_INPUT, ex, messageBuilder.toString().strip());

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
