package ai.univs.gate.shared.logging;

import ai.univs.gate.shared.exception.BusinessException;
import ai.univs.gate.shared.exception.CustomFeignException;
import ai.univs.gate.shared.web.enums.ErrorType;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.multipart.MultipartFile;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.*;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class LoggingAspect {

    private static final Set<String> SENSITIVE_KEYS = Set.of(
            "token",
            "refreshToken",
            "secret",
            "password",
            "passwordConfirm",
            "newPassword",
            "oldPassword",
            "passwordResetToken",
            "faceData",
            // UG-279: descriptor 는 생체 정보에서 파생된 템플릿이다. 마스킹은 키 이름
            // contains 매칭이라 targetDescriptor/descriptorBody 도 함께 걸린다.
            // 없으면 descriptor 기반 등록·1:N 이 매 요청 base64 전문을 INFO 로 남기고,
            // 결과적으로 등록된 전 가입자의 템플릿이 ./logs 볼륨에 평문으로 축적된다.
            "descriptor"
    );

    private final HttpServletRequest request;

    @Pointcut("within(@org.springframework.web.bind.annotation.RestController *)")
    public void restControllerMethods() {
    }

    @Around("restControllerMethods()")
    public Object logAround(ProceedingJoinPoint joinPoint) throws Throwable {
        String clientIp = getClientIpAddress(request);
        String httpMethod = request.getMethod();
        String uri = request.getRequestURI();
        String controller = joinPoint.getSignature().getDeclaringType().getSimpleName();
        String methodName = joinPoint.getSignature().getName();
        Map<String, Object> requestData = extractRequestData(joinPoint);

        log.info("[REQUEST] {} {} | {}.{} | ip={} | data={}",
                httpMethod, uri, controller, methodName, clientIp, requestData);

        long start = System.currentTimeMillis();
        try {
            Object result = joinPoint.proceed();
            log.info("[RESPONSE] {} {} | {}.{} | duration={}ms",
                    httpMethod, uri, controller, methodName, System.currentTimeMillis() - start);
            return result;
        } catch (Throwable ex) {
            // UG-290/291: 이 줄은 [REQUEST] 의 종결선이다 — 소요시간이 여기에만 있으므로 지우면
            // 요청이 어떻게 끝났는지 알 수 없게 된다. 대신 두 가지를 바꿨다.
            //
            //  1. 수준을 오류 성격에 맞춘다. 클라이언트 입력이 원인인 4xx 를 ERROR 로 남기면
            //     에러 대시보드에 오탐이 쌓여 진짜 5xx 가 묻힌다.
            //  2. 예외 상세는 여기서 늘리지 않는다. GlobalExceptionHandler 가 코드·메시지·
            //     스택트레이스를 담당하고, 이 줄은 "어느 요청이 얼마 만에 어떻게 끝났는가" 만 맡는다.
            //     두 줄이 남지만 겹치는 내용은 예외 이름 하나뿐이다.
            String line = "[EXCEPTION] {} {} | {}.{} | duration={}ms | exception={}";
            Object[] args = {httpMethod, uri, controller, methodName,
                    System.currentTimeMillis() - start, ex.getMessage()};
            if (isClientError(ex)) {
                log.warn(line, args);
            } else {
                log.error(line, args);
            }
            throw ex;
        }
    }

    /**
     * 이 예외가 클라이언트 입력 문제인지 (UG-290).
     *
     * <p>판정 기준은 {@link ErrorType#getStatus()} 다. 값이 다 채워져 있는데도 프로덕션 코드에서
     * 한 번도 읽히지 않던 죽은 코드였다 — 별도 분류를 새로 만들기보다 이미 정확한 값을 살려 쓴다.
     *
     * <p>{@link CustomFeignException} 은 {@code ErrorType} 매핑이 없지만, 만들어지는 지점인
     * {@code CommonErrorDecoder} 가 상태 코드 400~499 일 때만 이 타입을 반환하므로 언제나 4xx 다.
     *
     * <p>그 밖은 5xx 로 본다. 분류를 모르는 예외를 조용한 쪽으로 보내면 진짜 장애를 놓친다.
     */
    private boolean isClientError(Throwable ex) {
        if (ex instanceof BusinessException businessException) {
            return businessException.getErrorType().getStatus().is4xxClientError();
        }
        return ex instanceof CustomFeignException;
    }

    private Map<String, Object> extractRequestData(ProceedingJoinPoint joinPoint) {
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        Object[] args = joinPoint.getArgs();
        Annotation[][] paramAnnotations = method.getParameterAnnotations();

        Map<String, Object> requestData = new LinkedHashMap<>();

        for (int i = 0; i < args.length; i++) {
            Object arg = args[i];
            Annotation[] annotations = paramAnnotations[i];

            boolean isRequestBody = Arrays.stream(annotations)
                    .anyMatch(a -> a.annotationType().equals(RequestBody.class));
            boolean isRequestHeader = Arrays.stream(annotations)
                    .anyMatch(a -> a.annotationType().equals(RequestHeader.class));

            if (isRequestBody && arg != null) {
                requestData.put("body", sanitize(arg));
            } else if (isRequestHeader && arg != null) {
                requestData.put("header", arg);
            } else if (arg instanceof MultipartFile file) {
                requestData.put("file", summarizeFile(file));
            } else if (arg != null && isPojo(arg)) {
                requestData.put("modelAttribute", sanitize(arg));
            }
        }

        return requestData;
    }

    private Object sanitize(Object arg) {
        if (arg instanceof MultipartFile file) {
            return summarizeFile(file);
        }

        if (arg instanceof Map<?, ?> mapArg) {
            Map<String, Object> sanitized = new LinkedHashMap<>();
            mapArg.forEach((key, value) -> {
                String keyStr = String.valueOf(key);
                sanitized.put(keyStr, isSensitive(keyStr) ? "[PROTECTED]" : value);
            });
            return sanitized;
        }

        try {
            Map<String, Object> result = new LinkedHashMap<>();
            for (var field : arg.getClass().getDeclaredFields()) {
                field.setAccessible(true);
                String name = field.getName();
                Object value = field.get(arg);
                if (value instanceof MultipartFile file) {
                    result.put(name, summarizeFile(file));
                } else {
                    result.put(name, isSensitive(name) ? "[PROTECTED]" : value);
                }
            }
            return result;
        } catch (Exception e) {
            return "[UNREADABLE]";
        }
    }

    private Map<String, Object> summarizeFile(MultipartFile file) {
        return Map.of(
                "filename", Objects.requireNonNull(file.getOriginalFilename()),
                "size", file.getSize(),
                "contentType", Objects.requireNonNull(file.getContentType()),
                "content", "[FILE CONTENT OMITTED]"
        );
    }

    private boolean isSensitive(String fieldName) {
        return SENSITIVE_KEYS.stream()
                .anyMatch(key -> fieldName.toLowerCase().contains(key.toLowerCase()));
    }

    private String getClientIpAddress(HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader != null && !xfHeader.isBlank()) {
            return xfHeader.split(",")[0];
        }
        return request.getRemoteAddr();
    }

    private boolean isPojo(Object arg) {
        Package argPackage = arg.getClass().getPackage();
        if (argPackage == null) return false;
        String packageName = argPackage.getName();
        return !packageName.startsWith("java.") && !packageName.startsWith("jakarta.")
                && !(arg instanceof String);
    }
}