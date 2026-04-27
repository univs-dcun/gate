package ai.univs.auth.shared.locale;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.SessionLocaleResolver;

import java.util.Locale;

@Configuration
public class LocaleConfig {

    @Bean
    public LocaleResolver localeResolver() {
        var localeResolver = new SessionLocaleResolver();
        localeResolver.setDefaultLocale(Locale.KOREA);
        return localeResolver;
    }

    @Bean
    public HandlerInterceptor localeChangeInterceptor() {
        return new HandlerInterceptor() {
            private final LocaleResolver localeResolver = localeResolver();

            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
                String lang = request.getHeader("Accept-Language");
                Locale locale = (lang != null) ? Locale.forLanguageTag(lang) : Locale.KOREA;
                localeResolver.setLocale(request, response, locale);
                return true;
            }
        };
    }

    @Bean
    public MessageSource messageSource() {
        var messageSource = new ReloadableResourceBundleMessageSource();
        messageSource.setBasename("classpath:messages");
        messageSource.setDefaultEncoding("UTF-8");
        messageSource.setUseCodeAsDefaultMessage(true);
        return messageSource;
    }
}
