package ai.univs.face.shared.exception;

import ai.univs.face.shared.locale.MessageService;
import ai.univs.face.shared.web.dto.Errors;
import ai.univs.face.shared.web.dto.ResponseApi;
import ai.univs.face.shared.web.enums.ErrorType;
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

    @ExceptionHandler(CustomFaceException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ResponseApi<?> handleFaceCustomException(CustomFaceException ex) {
        return getExceptionResponse(ex.getErrorType());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ResponseApi<?> handleMethodArgumentNotValidException(MethodArgumentNotValidException ex) {
        log.error("Exception Stacktrace: {}", ex.getMessage(), ex);

        StringBuilder messageBuilder = new StringBuilder();

        // @Valid 검증에서 발생된 1 ~ n 개의 메시지를 합친 StringBuilder 메시지로 사용.
        ex.getBindingResult().getAllErrors().forEach((error) -> {
            String message = messageService.getMessage(error.getDefaultMessage());
            messageBuilder.append(message).append(" ");
        });

        log.error("Validation Message: {}", messageBuilder);

        return getExceptionResponse(ErrorType.INVALID_INPUT, messageBuilder.toString());
    }

    @ExceptionHandler(InvalidFaceImageException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ResponseApi<?> handleInvalidFaceImageException(InvalidFaceImageException ex) {
        String message = messageService.getMessage(ex.getErrorType().name());

        return getExceptionResponse(ErrorType.INVALID_INPUT, message);
    }

    @ExceptionHandler(InvalidFaceModuleException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ResponseApi<?> handleInvalidFaceModuleException(InvalidFaceModuleException ex) {
        log.error("Exception Message: {}", ex.getMessage());

        Errors errors = new Errors(ex.getCode(), ex.getType(), ex.getMessage());
        return getExceptionResponse(errors);
    }

    @ExceptionHandler(CustomFeignException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ResponseApi<?> CustomFeignException(CustomFeignException ex) {
        log.error("Exception Message & Stacktrace: {}", ex.getMessage(), ex);

        Errors errors = new Errors(ex.getCode(), ex.getType(), ex.getMessage());
        return getExceptionResponse(errors);
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

    private ResponseApi<?> getExceptionResponse(Errors errors) {
        return ResponseApi.fail(errors);
    }
}
