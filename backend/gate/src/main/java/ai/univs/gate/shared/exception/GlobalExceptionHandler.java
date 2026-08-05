package ai.univs.gate.shared.exception;

import ai.univs.gate.shared.web.dto.Errors;
import ai.univs.gate.shared.web.dto.ResponseApi;
import ai.univs.gate.shared.web.enums.ErrorType;
import ai.univs.gate.support.message.MessageService;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
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
        log.error("Exception Stacktrace: {}", ex.getMessage(), ex);

        return getExceptionResponse(ex.getErrorType());
    }

    /**
     * 하위 서비스 호출 실패 (UG-280).
     *
     * <p>{@link RemoteCallException} 은 {@link BusinessException} 하위이므로 이 핸들러가 없어도
     * 위 핸들러가 잡는다. 따로 둔 이유는 두 가지다.
     *
     * <ul>
     *   <li>하위 서비스의 상태 코드를 로그에 남긴다. 위 핸들러는 {@code ex.getMessage()} 만
     *       찍는데 그 값은 {@code INTERNAL_SERVER_ERROR} 라 502·503·타임아웃을 구분할 수 없다.
     *   <li>스택트레이스를 남기지 않는다. 원인은 하위 서비스이고 우리 쪽 호출 스택은 매번 같아서
     *       90여 줄이 반복될 뿐이다. 하위 장애 시에는 이 예외가 대량으로 발생한다.
     * </ul>
     *
     * <p>응답 본문·상태 코드는 위 핸들러와 동일하다 ({@code PJ-005}, 400). 클라이언트가 보는
     * 계약을 바꾸지 않기 위해 일부러 맞췄다.
     */
    @ExceptionHandler(RemoteCallException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ResponseApi<?> handleRemoteCallException(RemoteCallException ex) {
        log.error("하위 서비스 호출 실패 — upstreamStatus={}", ex.getUpstreamStatus());

        return getExceptionResponse(ex.getErrorType());
    }

    @ExceptionHandler(CustomFeignException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ResponseApi<?> CustomFeignException(CustomFeignException ex) {
        log.error("Exception Message & Stacktrace: {}", ex.getMessage(), ex);

        Errors errors = new Errors(
                ex.getCode(),
                ex.getType(),
                ex.getMessage());
        return ResponseApi.fail(errors);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ResponseApi<?> handleMethodArgumentNotValidException(MethodArgumentNotValidException ex) {
        log.error("Exception Stacktrace: {}", ex.getMessage(), ex);

        StringBuilder messageBuilder = new StringBuilder();

        // CLIENT_INPUT_ERROR 의 경우 @Valid 검증에서 발생된 1 ~ n 개의 메시지를 합친 StringBuilder 메시지로 사용.
        ex.getBindingResult().getAllErrors().forEach((error) -> {
            String message = messageService.getMessage(error.getDefaultMessage());
            messageBuilder.append(message).append(" ");
        });

        log.error("Validation Message: {}", messageBuilder);

        return getExceptionResponse(ErrorType.INVALID_INPUT, messageBuilder.toString());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ResponseApi<?> handleHttpMessageNotReadableException(HttpMessageNotReadableException ex) {
        log.error("Exception Stacktrace: {}", ex.getMessage(), ex);

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
        log.error("Exception Stacktrace: {}", ex.getMessage(), ex);

        return getExceptionResponse(ErrorType.INVALID_INPUT);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ResponseApi<?> handleNoResourceFoundException(NoResourceFoundException ex) {
        log.error("Exception Stacktrace: {}", ex.getMessage(), ex);

        return getExceptionResponse(ErrorType.NOT_FOUND);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    @ResponseStatus(HttpStatus.METHOD_NOT_ALLOWED)
    public ResponseApi<?> handleHttpRequestMethodNotSupportedException(HttpRequestMethodNotSupportedException ex) {
        log.error("Exception Stacktrace: {}", ex.getMessage(), ex);

        return getExceptionResponse(ErrorType.METHOD_NOT_ALLOWED);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    @ResponseStatus(HttpStatus.METHOD_NOT_ALLOWED)
    public ResponseApi<?> handleHttpMediaTypeNotSupportedException(HttpMediaTypeNotSupportedException ex) {
        log.error("Exception Stacktrace: {}", ex.getMessage(), ex);

        return getExceptionResponse(ErrorType.METHOD_NOT_ALLOWED);
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ResponseApi<?> handleGlobalException(Exception ex) {
        log.error("Exception Stacktrace: {}", ex.getMessage(), ex);

        return getExceptionResponse(ErrorType.INTERNAL_SERVER_ERROR);
    }

    private ResponseApi<?> getExceptionResponse(ErrorType errorType) {
        return getExceptionResponse(errorType, messageService.getMessage(errorType));
    }

    private ResponseApi<?> getExceptionResponse(ErrorType errorType, String message) {
        return ResponseApi.fail(Errors.from(errorType, message));
    }
}
