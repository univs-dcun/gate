package ai.univs.auth.shared.exception;

import ai.univs.auth.application.exception.CustomAuthException;
import ai.univs.auth.shared.web.dto.Errors;
import ai.univs.auth.shared.web.dto.ResponseApi;
import ai.univs.auth.shared.web.enums.ErrorType;
import ai.univs.auth.support.message.MessageService;
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

    @ExceptionHandler(BusinessException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ResponseApi<?> handleBusinessException(BusinessException ex) {
        log.error("BusinessException: {}", ex.getMessage(), ex);
        return getExceptionResponse(ex.getErrorType());
    }

    @ExceptionHandler(CustomAuthException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ResponseApi<?> handleCustomAuthException(CustomAuthException ex) {
        log.error("CustomAuthException: {}", ex.getMessage(), ex);
        return getExceptionResponse(ex.getProcessType());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ResponseApi<?> handleMethodArgumentNotValidException(MethodArgumentNotValidException ex) {
        log.error("MethodArgumentNotValidException: {}", ex.getMessage(), ex);

        StringBuilder messageBuilder = new StringBuilder();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String message = messageService.getMessage(error.getDefaultMessage());
            messageBuilder.append(message).append(" ");
        });

        return getExceptionResponse(ErrorType.INVALID_INPUT, messageBuilder.toString().trim());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ResponseApi<?> handleHttpMessageNotReadableException(HttpMessageNotReadableException ex) {
        log.error("HttpMessageNotReadableException: {}", ex.getMessage(), ex);

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
        log.error("MethodArgumentTypeMismatchException: {}", ex.getMessage(), ex);
        return getExceptionResponse(ErrorType.INVALID_INPUT);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ResponseApi<?> handleNoResourceFoundException(NoResourceFoundException ex) {
        log.error("NoResourceFoundException: {}", ex.getMessage(), ex);
        return getExceptionResponse(ErrorType.NOT_FOUND);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    @ResponseStatus(HttpStatus.METHOD_NOT_ALLOWED)
    public ResponseApi<?> handleHttpRequestMethodNotSupportedException(HttpRequestMethodNotSupportedException ex) {
        log.error("HttpRequestMethodNotSupportedException: {}", ex.getMessage(), ex);
        return getExceptionResponse(ErrorType.METHOD_NOT_ALLOWED);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    @ResponseStatus(HttpStatus.METHOD_NOT_ALLOWED)
    public ResponseApi<?> handleHttpMediaTypeNotSupportedException(HttpMediaTypeNotSupportedException ex) {
        log.error("HttpMediaTypeNotSupportedException: {}", ex.getMessage(), ex);
        return getExceptionResponse(ErrorType.METHOD_NOT_ALLOWED);
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ResponseApi<?> handleGlobalException(Exception ex) {
        log.error("Unhandled exception: {}", ex.getMessage(), ex);
        return getExceptionResponse(ErrorType.INTERNAL_SERVER_ERROR);
    }

    private ResponseApi<?> getExceptionResponse(ErrorType errorType) {
        return getExceptionResponse(errorType, messageService.getMessage(errorType));
    }

    private ResponseApi<?> getExceptionResponse(ErrorType errorType, String message) {
        return ResponseApi.fail(Errors.from(errorType, message));
    }
}
