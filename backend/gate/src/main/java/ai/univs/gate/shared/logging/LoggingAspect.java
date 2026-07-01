package ai.univs.gate.shared.logging;

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
            "faceData"
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
            log.error("[EXCEPTION] {} {} | {}.{} | duration={}ms | exception={}",
                    httpMethod, uri, controller, methodName, System.currentTimeMillis() - start, ex.getMessage());
            throw ex;
        }
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