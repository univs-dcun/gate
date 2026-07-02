package ai.univs.gate.shared.web;

import ai.univs.gate.shared.auth.UserContextInterceptor;
import ai.univs.gate.shared.locale.LocaleConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    // face modality API 경로 (feature + demo)
    private static final String[] FACE_PATHS = {
            "/api/v1/feature/face", "/api/v1/feature/face/**",
            "/api/v1/demo/feature/face", "/api/v1/demo/feature/face/**", "/api/v1/demo/feature/faces"
    };

    // palm modality API 경로 (feature + demo)
    private static final String[] PALM_PATHS = {
            "/api/v1/feature/palm", "/api/v1/feature/palm/**",
            "/api/v1/demo/feature/palm", "/api/v1/demo/feature/palm/**", "/api/v1/demo/feature/palms"
    };

    private final UserContextInterceptor userContextInterceptor;
    private final LocaleConfig localeConfig;
    private final GateFeatureProperties featureProperties;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(localeConfig.localeChangeInterceptor());
        registry.addInterceptor(userContextInterceptor);
        registry.addInterceptor(new FeatureGateInterceptor(featureProperties::isFace))
                .addPathPatterns(FACE_PATHS);
        registry.addInterceptor(new FeatureGateInterceptor(featureProperties::isPalm))
                .addPathPatterns(PALM_PATHS);
    }
}
