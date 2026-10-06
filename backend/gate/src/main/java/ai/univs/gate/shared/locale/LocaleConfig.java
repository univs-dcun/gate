package ai.univs.gate.shared.locale;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;
import org.springframework.web.servlet.LocaleResolver;

import java.util.Locale;

@Slf4j
@Configuration
public class LocaleConfig {

    /** 세션 없이 요청 헤더로만 정한다 (UG-351). 헤더가 없으면 ENGLISH. */
    @Bean
    public LocaleResolver localeResolver() {
        return new HeaderLocaleResolver(Locale.ENGLISH);
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
