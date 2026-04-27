package ai.univs.auth.shared.web.config;

import ai.univs.auth.shared.locale.LocaleConfig;
import ai.univs.auth.shared.web.ctx.ClientRequestInterceptor;
import ai.univs.auth.shared.web.ctx.TimeZoneInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final LocaleConfig localeConfig;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(localeConfig.localeChangeInterceptor());
        registry.addInterceptor(new TimeZoneInterceptor());
        registry.addInterceptor(new ClientRequestInterceptor());
    }
}
