package ai.univs.gate.support.webhook;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** {@link WebhookProperties} 를 등록한다 (UG-111). */
@Configuration
@EnableConfigurationProperties(WebhookProperties.class)
class WebhookConfiguration {
}
