package ai.univs.match.shared.exception;

import ai.univs.match.shared.locale.MessageService;
import ai.univs.match.shared.web.dto.Errors;
import ai.univs.match.shared.web.dto.ResponseApi;
import ai.univs.match.shared.web.enums.ErrorType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private final MessageService messageService;

    @ExceptionHandler(CustomFaceMatcherException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ResponseApi<?> handleFaceMatcherCustomException(CustomFaceMatcherException ex) {
        log.error("Exception Stacktrace: {}", ex.getMessage(), ex);

        return getExceptionResponse(ex.getErrorType());
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
