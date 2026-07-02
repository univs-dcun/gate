package ai.univs.gate.shared.web;

import ai.univs.gate.shared.exception.CustomGateException;
import ai.univs.gate.shared.web.enums.ErrorType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.function.BooleanSupplier;

/**
 * 비활성화된 생체인증 modality(face/palm)의 API 호출을 차단한다.
 * 적용 경로는 {@link WebMvcConfig}에서 modality 별로 등록하며,
 * 비활성 상태에서는 표준 에러({@link ErrorType#FEATURE_NOT_ENABLED})로 응답한다.
 */
@RequiredArgsConstructor
public class FeatureGateInterceptor implements HandlerInterceptor {

    private final BooleanSupplier featureEnabled;

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler
    ) {
        if (!featureEnabled.getAsBoolean()) {
            throw new CustomGateException(ErrorType.FEATURE_NOT_ENABLED);
        }

        return true;
    }
}
