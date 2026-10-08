package ai.univs.gate.support.webhook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import ai.univs.gate.facade.demo.application.service.DemoRedisPublisher;
import ai.univs.gate.modules.webhook.domain.repository.WebhookConfigRepository;
import ai.univs.gate.support.notify.UseCaseNotifyService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.InetAddress;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * 웹훅 빈이 Spring 컨텍스트에서 실제로 만들어지는가 (UG-111).
 *
 * <p>단위 테스트는 전부 객체를 직접 생성해서, {@link WebhookTargetPolicy} 에 생성자가 둘이라 Spring 이
 * 고르지 못하는 문제를 놓쳤다. dev 파이프라인(Contract Check 의 앱 기동)에서야 드러났고, 그대로 배포됐다면
 * gate-service 가 기동하지 못했다. 컨텍스트를 띄워 배선과 설정 바인딩을 본다.
 */
@DisplayName("UG-111: 웹훅 빈 배선과 설정 바인딩")
class WebhookBeanWiringTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(WebhookConfiguration.class, WebhookTargetPolicy.class,
                    WebhookService.class, WebhookToggleCache.class, UseCaseNotifyService.class)
            .withBean(WebhookConfigRepository.class, () -> mock(WebhookConfigRepository.class))
            .withBean(DemoRedisPublisher.class, () -> mock(DemoRedisPublisher.class))
            .withBean(ObjectMapper.class, ObjectMapper::new);

    @Test
    @DisplayName("기본값: 컨텍스트가 뜨고 사설망은 막는다")
    void 기본값() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(WebhookService.class).hasSingleBean(UseCaseNotifyService.class);
            WebhookTargetPolicy policy = context.getBean(WebhookTargetPolicy.class);
            assertThat(policy.isAllowed(InetAddress.getByName("10.0.0.1"))).isFalse();
            assertThat(policy.isAllowed(InetAddress.getByName("8.8.8.8"))).isTrue();
        });
    }

    @Test
    @DisplayName("GATE_WEBHOOK_ALLOW_PRIVATE_TARGETS=true 에 해당하는 속성이 정책에 닿는다 (온프레미스)")
    void 사설망_허용_바인딩() {
        runner.withPropertyValues("gate.webhook.allow-private-targets=true").run(context -> {
            assertThat(context).hasNotFailed();
            WebhookTargetPolicy policy = context.getBean(WebhookTargetPolicy.class);
            assertThat(policy.isAllowed(InetAddress.getByName("10.0.0.1"))).isTrue();
            assertThat(policy.isAllowed(InetAddress.getByName("127.0.0.1"))).isFalse();
        });
    }
}
