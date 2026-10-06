package ai.univs.gate.shared.web;

import ai.univs.gate.modules.feature.domain.enums.FeatureType;
import ai.univs.gate.shared.auth.UserContextInterceptor;
import ai.univs.gate.shared.modality.ModalityGateInterceptor;
import ai.univs.gate.shared.modality.ModalityProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Slf4j
@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(ModalityProperties.class)
public class WebMvcConfig implements WebMvcConfigurer {

    private final UserContextInterceptor userContextInterceptor;
    private final ModalityProperties modalityProperties;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(userContextInterceptor);

        // UG-223: 거절 메시지는 요청 언어로 나간다 — UG-351 부터 언어는 LocaleResolver 가 요청 헤더로 바로 정하므로
        // (인터셉터 순서와 무관하다) 어느 인터셉터에서 메시지를 만들어도 같다.
        for (FeatureType modality : FeatureType.values()) {
            registry.addInterceptor(new ModalityGateInterceptor(modality, modalityProperties))
                    .addPathPatterns(ModalityGateInterceptor.pathsOf(modality));

            if (!modalityProperties.isEnabled(modality)) {
                // 꺼진 것을 기동 로그에 한 줄 남긴다. "왜 palm 등록이 거절되나" 를 설정에서 찾게 한다.
                log.info("{} 동작 API 가 꺼져 있다 (gate.features.{}=false) — 조회는 그대로 열려 있다",
                        modality, modality.name().toLowerCase());
            }
        }
    }
}
