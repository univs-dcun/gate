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
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
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
import io.netty.channel.socket.DatagramChannel;
import io.netty.handler.codec.dns.DnsResponseCode;
import io.netty.handler.ssl.SslHandshakeTimeoutException;
import io.netty.resolver.dns.DnsAddressResolverGroup;
import io.netty.resolver.dns.DnsErrorCauseException;
import io.netty.resolver.dns.DnsServerAddressStreamProviders;
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
    /** NIO 로 고정한다. 연결과 DNS 가 같은 값을 써야 한다 — 한 상수로 묶는다. */
    private static final boolean PREFER_NATIVE = false;

    private final WebhookConfigRepository webhookConfigRepository;
    private final ObjectMapper objectMapper;
    private final WebhookTargetPolicy targetPolicy;
    private final WebhookProperties properties;
    private final ThreadPoolExecutor dispatcher;
    private final LoopResources loops;
    private final ConnectionProvider connections;
    private final WebClient webClient;
    private final Duration attemptTimeout;
    private final PolicyAddressResolverGroup resolvers;

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
        // DNS 의 UDP 채널 종류를 연결에 쓰는 이벤트 루프에서 유도한다 — 둘이 어긋나면 Linux(epoll)에서
        // "incompatible event loop type" 으로 호스트 이름 웹훅이 전부 실패한다 (3차 반박 리뷰 W2).
        Class<? extends DatagramChannel> datagramType =
                loops.onChannelClass(DatagramChannel.class, loops.onClient(PREFER_NATIVE));
        this.resolvers = new PolicyAddressResolverGroup(
                new DnsAddressResolverGroup(datagramType, DnsServerAddressStreamProviders.platformDefault()),
                targetPolicy);
        HttpClient httpClient = HttpClient.create(connections)
                // DNS 리졸버의 UDP 채널과 같은 이벤트 루프 종류를 쓴다 (위 datagramType).
                .runOn(loops, PREFER_NATIVE)
                // 호스트 이름은 netty 비동기 DNS 로 풀고, 푼 주소를 정책으로 거른다. 연결에 실제로 쓰는
                // 주소를 보므로 DNS 리바인딩도 여기서 막힌다. 해석을 실패시키는 방식이어야 막힌 연결의
                // 채널이 닫힌다 (PolicyAddressResolverGroup 주석, 2차 반박 리뷰 B1).
                .resolver(resolvers)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) properties.connectTimeout().toMillis())
                .responseTimeout(properties.responseTimeout())
                .followRedirect(false);   // 공인 주소가 내부 주소로 리다이렉트하는 우회를 막는다
        this.attemptTimeout = properties.connectTimeout().plus(properties.responseTimeout()).plusSeconds(1);
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
            config = withSecret(projectId, config);

            URI target;
            try {
                // 저장할 때 검사했어도 다시 본다. 여기서는 DNS 를 조회하지 않는다 — 응답하지 않는
                // 네임서버가 전송 스레드 둘을 붙잡으면 모든 프로젝트의 웹훅이 밀린다(반박 리뷰 W2).
                // 호스트 이름이 푼 주소는 연결 단계(PolicyAddressResolverGroup)가 거른다.
                target = targetPolicy.checkWithoutLookup(config.getWebhookUrl());
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

            List<String> secrets = config.activeSecrets(LocalDateTime.ofInstant(Instant.now(clock), ZoneOffset.UTC));
            deliver(target, payload.eventId(), body, secrets).subscribe(
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

    /**
     * UG-344 이전에 만든 설정은 키가 없다. 화면에서 조회하기 전에도 서명이 붙도록 여기서 채운다 — 조건부 갱신이라 화면
     * 쪽과 동시에 채워도 하나로 정해진다. 채우지 못하면(DB 오류) 서명 없이 보낸다: 웹훅을 아예 버리는 것보다 낫고,
     * 수신 측은 서명이 없으면 거절하면 된다.
     */
    private WebhookConfig withSecret(Long projectId, WebhookConfig config) {
        if (config.getWebhookSecret() != null) return config;
        try {
            webhookConfigRepository.assignSecretIfAbsent(config.getId(), WebhookSecrets.generate());
            return webhookConfigRepository.findByProjectId(projectId).orElse(config);
        } catch (Exception e) {
            log.warn("Webhook secret assignment failed, sending unsigned: projectId={}, cause={}", projectId, e.toString());
            return config;
        }
    }

    Mono<Void> deliver(URI target, String eventId, byte[] body) {
        return deliver(target, eventId, body, List.of());
    }

    /**
     * @param secrets 서명 키들 (UG-344). 비어 있으면 서명 헤더를 붙이지 않는다.
     */
    Mono<Void> deliver(URI target, String eventId, byte[] body, List<String> secrets) {
        try {
            // IP 리터럴은 리졸버를 거치지 않는다. dispatch 가 이미 봤지만, 이 메서드만 부르는 경로에서도
            // 연결 전에 막는다 — 연결 뒤에 막으면 채널이 남는다.
            targetPolicy.checkWithoutLookup(target.toString());
        } catch (CustomGateException e) {
            return Mono.error(new WebhookTargetPolicy.TargetNotAllowedException(target.getHost()));
        }
        // 시도마다 새로 만든다 — 서명의 타임스탬프가 실제 보낸 시각이어야 수신 측의 시간 허용 범위 검사가 재시도를
        // 오래된 요청으로 오판하지 않는다 (UG-344).
        return Mono.defer(() -> webClient.post()
                .uri(target)
                .contentType(MediaType.APPLICATION_JSON)
                .header(EVENT_ID_HEADER, eventId)
                .header(HttpHeaders.USER_AGENT, USER_AGENT)
                .headers(headers -> {
                    if (!secrets.isEmpty()) {
                        headers.set(WebhookSignature.HEADER,
                                WebhookSignature.header(Instant.now(clock).getEpochSecond(), body, secrets));
                    }
                })
                .bodyValue(body)
                .exchangeToMono(response -> {
                    int status = response.statusCode().value();
                    Mono<Void> drained = response.releaseBody();
                    return response.statusCode().is2xxSuccessful()
                            ? drained
                            : drained.then(Mono.error(new RejectedByReceiverException(status)));
                }))
                // 시도 한 번의 전체 시간 상한. responseTimeout 은 "읽기 사이의 공백" 이라, 1바이트씩
                // 흘려 보내는 수신 서버는 그것만으로 끊기지 않는다(반박 리뷰 W1). 넘으면 구독이
                // 취소되고 연결은 버려진다.
                .timeout(attemptTimeout)
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
     * 시도 한 번의 전체 시간 초과({@code Mono.timeout})는 JDK {@link TimeoutException} 으로 온다.
     */
    static boolean isRetryable(Throwable error) {
        // 막힌 주소·없는 호스트(NXDOMAIN)·인증서 오류는 다시 보내도 같다. 그 밖의 DNS 실패(조회 타임아웃,
        // SERVFAIL)도 UnknownHostException 으로 오지만 일시적일 수 있어 재시도한다 (2차 반박 리뷰 W2).
        if (hasCause(error, WebhookTargetPolicy.TargetNotAllowedException.class)) return false;
        if (isNxDomain(error)) return false;
        if (hasCause(error, SslHandshakeTimeoutException.class)) return true;
        if (hasCause(error, javax.net.ssl.SSLException.class)) return false;
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

    private static boolean isNxDomain(Throwable error) {
        for (Throwable t = error; t != null; t = t.getCause()) {
            if (t instanceof DnsErrorCauseException dns && DnsResponseCode.NXDOMAIN.equals(dns.getCode())) return true;
            if (t.getCause() == t) break;
        }
        return false;
    }

    /**
     * URL 전체는 남기지 않는다 — 쿼리에 수신 측 토큰이 들어 있는 경우가 흔하다.
     * 가장 안쪽 원인을 붙여 NXDOMAIN·DNS 타임아웃·연결 거부를 로그에서 구분한다.
     */
    static String describe(Throwable error) {
        if (error instanceof RejectedByReceiverException rejected) return "HTTP " + rejected.status;
        // 온프레미스에서 allow-private-targets 를 빠뜨렸을 때 연결 거부와 구분돼야 한다 (2차 반박 리뷰 W3)
        if (hasCause(error, WebhookTargetPolicy.TargetNotAllowedException.class)) return "TARGET_NOT_ALLOWED";
        Throwable root = error;
        while (root.getCause() != null && root.getCause() != root) root = root.getCause();
        String detail = root instanceof DnsErrorCauseException dns ? "DNS " + dns.getCode() : root.getClass().getSimpleName();
        return root == error ? detail : error.getClass().getSimpleName() + "/" + detail;
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
        try {
            // 대기열에 남은 전송을 잠깐 흘려보낸다. 넘치면 버린다 — 재기동이 웹훅을 기다리지 않는다.
            dispatcher.awaitTermination(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        resolvers.close();
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
