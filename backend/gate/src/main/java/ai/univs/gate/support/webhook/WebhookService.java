package ai.univs.gate.support.webhook;

import ai.univs.gate.modules.webhook.domain.entity.WebhookConfig;
import ai.univs.gate.modules.webhook.domain.repository.WebhookConfigRepository;
import ai.univs.gate.shared.exception.CustomGateException;
import ai.univs.gate.shared.web.enums.CallerType;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.channel.ChannelOption;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;
import reactor.netty.resources.LoopResources;
import reactor.util.retry.Retry;

/**
 * 매칭·라이브니스 결과를 프로젝트의 웹훅 URL 로 보낸다 (UG-111).
 *
 * <p><b>요청 처리를 막지 않는다.</b> 설정 조회와 본문 조립은 전용 스레드 두 개에서, 전송은
 * 전용 이벤트 루프에서 비동기로 한다. 대기열이 차거나 연결 대기가 넘치면 그 전송은 버리고
 * 로그만 남긴다 — 웹훅 실패로 매칭 응답이 늦어지거나 실패하는 일은 없어야 한다.
 *
 * <p><b>전달 보장은 "최소 한 번"이다.</b> 연결 실패·타임아웃·5xx·429 는 재시도하고, 그 밖의
 * 4xx 는 수신 측이 거절한 것이라 재시도하지 않는다. 재시도해도 {@code eventId} 는 같으므로
 * 수신 측이 중복을 거른다. 재시도가 다 실패하면 버린다 (저장·재전송 없음).
 *
 * <p>UG-111 이전에는 타임아웃이 없어 응답하지 않는 수신 서버 하나가 {@code @Async} 기본
 * 스레드를 붙잡았고, 대기열도 무제한이었다. URL 검사도 없어 내부 주소로 요청을 보낼 수 있었다.
 */
@Slf4j
@Component
public class WebhookService {

    static final String EVENT_ID_HEADER = "X-Gate-Event-Id";
    static final String USER_AGENT = "UNIVS-Gate-Webhook/1.0";

    private final WebhookConfigRepository webhookConfigRepository;
    private final ObjectMapper objectMapper;
    private final WebhookTargetPolicy targetPolicy;
    private final WebhookProperties properties;
    private final ThreadPoolExecutor dispatcher;
    private final LoopResources loops;
    private final ConnectionProvider connections;
    private final WebClient webClient;

    /** 테스트가 시각을 고정하려고 바꾼다. */
    Clock clock = Clock.systemUTC();

    public WebhookService(WebhookConfigRepository webhookConfigRepository,
                          ObjectMapper objectMapper,
                          WebhookTargetPolicy targetPolicy,
                          WebhookProperties properties) {
        this.webhookConfigRepository = webhookConfigRepository;
        this.objectMapper = objectMapper;
        this.targetPolicy = targetPolicy;
        this.properties = properties;
        this.dispatcher = new ThreadPoolExecutor(
                2, 2, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(properties.queueCapacity()),
                namedDaemonThreads("webhook-dispatch-"));
        this.loops = LoopResources.create("webhook", 2, true);
        this.connections = ConnectionProvider.builder("webhook")
                .maxConnections(properties.maxConnections())
                .pendingAcquireMaxCount(properties.maxPending())
                .pendingAcquireTimeout(Duration.ofSeconds(10))
                .build();
        HttpClient httpClient = HttpClient.create(connections)
                .runOn(loops)
                .resolver(new PolicyAddressResolverGroup(targetPolicy))
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) properties.connectTimeout().toMillis())
                .responseTimeout(properties.responseTimeout())
                .followRedirect(false);   // 공인 주소가 내부 주소로 리다이렉트하는 우회를 막는다
        this.webClient = WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }

    /**
     * 설정이 있고 그 호출 경로(API·데모)가 켜져 있으면 보낸다. 바로 돌아온다.
     *
     * @param data 그 API 의 응답 {@code data} 와 같은 객체
     */
    public void send(Long projectId, CallerType source, WebhookEvent event, String transactionUuid, Object data) {
        Instant occurredAt = Instant.now(clock);
        try {
            dispatcher.execute(() -> dispatch(projectId, source, event, transactionUuid, occurredAt, data));
        } catch (RejectedExecutionException e) {
            log.warn("Webhook dropped (dispatch queue full): projectId={}, event={}", projectId, event);
        }
    }

    void dispatch(Long projectId, CallerType source, WebhookEvent event,
                  String transactionUuid, Instant occurredAt, Object data) {
        try {
            WebhookConfig config = webhookConfigRepository.findByProjectId(projectId).orElse(null);
            if (config == null || !isEnabled(config, source)) return;

            URI target;
            try {
                // 저장할 때 검사했어도 다시 본다 — 그 사이 DNS 가 내부 주소로 바뀌었을 수 있다.
                target = targetPolicy.validate(config.getWebhookUrl());
            } catch (CustomGateException e) {
                log.warn("Webhook skipped (target not allowed): projectId={}, event={}", projectId, event);
                return;
            }

            WebhookPayload payload = new WebhookPayload(
                    UUID.randomUUID().toString(),
                    event.name(),
                    source.name(),
                    transactionUuid,
                    occurredAt,
                    objectMapper.valueToTree(data));
            // 앱의 ObjectMapper 로 직렬화한다 — WebClient 기본 코덱은 별도 매퍼라 날짜·null 처리가
            // 응답 본문과 달라질 수 있다. 바이트로 한 번 만들어 재시도에도 그대로 쓴다.
            byte[] body = objectMapper.writeValueAsBytes(payload);

            deliver(target, payload.eventId(), body).subscribe(
                    ignored -> { },
                    error -> log.warn("Webhook delivery failed: projectId={}, event={}, eventId={}, host={}, cause={}",
                            projectId, event, payload.eventId(), target.getHost(), describe(error)),
                    () -> log.info("Webhook sent: projectId={}, event={}, eventId={}",
                            projectId, event, payload.eventId()));
        } catch (Exception e) {
            // 여기서 새는 예외는 전송 스레드를 죽이지 않지만 흔적도 없이 사라진다. 원인을 남긴다.
            log.error("Webhook dispatch error: projectId={}, event={}", projectId, event, e);
        }
    }

    Mono<Void> deliver(URI target, String eventId, byte[] body) {
        return webClient.post()
                .uri(target)
                .contentType(MediaType.APPLICATION_JSON)
                .header(EVENT_ID_HEADER, eventId)
                .header(HttpHeaders.USER_AGENT, USER_AGENT)
                .bodyValue(body)
                .exchangeToMono(response -> {
                    int status = response.statusCode().value();
                    Mono<Void> drained = response.releaseBody();
                    return response.statusCode().is2xxSuccessful()
                            ? drained
                            : drained.then(Mono.error(new RejectedByReceiverException(status)));
                })
                .retryWhen(Retry.backoff(Math.max(0, properties.maxAttempts() - 1), properties.retryBackoff())
                        .filter(WebhookService::isRetryable)
                        .onRetryExhaustedThrow((spec, signal) -> signal.failure()));
    }

    private static boolean isEnabled(WebhookConfig config, CallerType source) {
        return switch (source) {
            case API  -> Boolean.TRUE.equals(config.getApiEnabled());
            case DEMO -> Boolean.TRUE.equals(config.getDemoEnabled());
        };
    }

    /**
     * 다시 보내면 나아질 수 있는 실패인가.
     *
     * <p>막힌 주소({@link WebhookTargetPolicy.TargetNotAllowedException})는 연결 오류처럼 올라오지만
     * 재시도해도 같으므로 제외한다. 3xx 는 리다이렉트를 따르지 않으므로 실패로 보고 재시도하지 않는다.
     */
    static boolean isRetryable(Throwable error) {
        if (hasCause(error, WebhookTargetPolicy.TargetNotAllowedException.class)) return false;
        if (error instanceof RejectedByReceiverException rejected) {
            return rejected.status >= 500 || rejected.status == 429;
        }
        // 응답 타임아웃은 netty 의 ReadTimeoutException 으로 온다 — IOException 도 JDK TimeoutException 도 아니다.
        return hasCause(error, IOException.class)
                || hasCause(error, TimeoutException.class)
                || hasCause(error, io.netty.handler.timeout.TimeoutException.class);
    }

    private static boolean hasCause(Throwable error, Class<? extends Throwable> type) {
        for (Throwable t = error; t != null; t = t.getCause()) {
            if (type.isInstance(t)) return true;
            if (t.getCause() == t) break;
        }
        return false;
    }

    /** URL 전체는 남기지 않는다 — 쿼리에 수신 측 토큰이 들어 있는 경우가 흔하다. */
    private static String describe(Throwable error) {
        if (error instanceof RejectedByReceiverException rejected) return "HTTP " + rejected.status;
        return error.getClass().getSimpleName();
    }

    private static java.util.concurrent.ThreadFactory namedDaemonThreads(String prefix) {
        AtomicInteger seq = new AtomicInteger();
        return runnable -> {
            Thread thread = new Thread(runnable, prefix + seq.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }

    @PreDestroy
    void shutdown() {
        dispatcher.shutdown();
        connections.disposeLater().block(Duration.ofSeconds(5));
        loops.disposeLater().block(Duration.ofSeconds(5));
    }

    /** 수신 서버가 2xx 가 아닌 응답을 줬다. */
    static final class RejectedByReceiverException extends RuntimeException {
        final int status;

        RejectedByReceiverException(int status) {
            super("webhook receiver responded " + status);
            this.status = status;
        }
    }
}
