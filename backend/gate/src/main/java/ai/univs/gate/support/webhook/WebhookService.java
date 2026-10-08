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
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.scheduling.annotation.Scheduled;
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
 *
 * <p><b>꺼져 있는 줄 아는 경로는 대기열에 넣지 않는다</b> (UG-361, {@link WebhookToggleCache}). 대기열이 넘쳐 버린
 * 건수는 {@link #DROP_LOG_INTERVAL} 마다 한 줄로 모아 남긴다 — 건마다 남기면 부하 때 요청 수만큼 WARN 이 쌓인다.
 */
@Slf4j
@Component
public class WebhookService {

    static final String EVENT_ID_HEADER = "X-Gate-Event-Id";
    static final String USER_AGENT = "UNIVS-Gate-Webhook/1.0";
    /** 테스트 전송의 {@code source} (UG-344). 호출 경로(API·데모)가 아니라 콘솔의 시험이다. */
    static final String TEST_SOURCE = "TEST";
    /** NIO 로 고정한다. 연결과 DNS 가 같은 값을 써야 한다 — 한 상수로 묶는다. */
    private static final boolean PREFER_NATIVE = false;
    /** 대기열이 넘쳐 버린 건수를 모아 남기는 간격 (UG-361). */
    static final Duration DROP_LOG_INTERVAL = Duration.ofSeconds(10);
    private static final long NEVER_LOGGED = Long.MIN_VALUE;

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
    private final WebhookToggleCache toggles;
    private final AtomicLong droppedSinceLog = new AtomicLong();
    private final AtomicLong lastDropLogNanos = new AtomicLong(NEVER_LOGGED);

    /** 테스트가 시각을 고정하려고 바꾼다. */
    Clock clock = Clock.systemUTC();
    /** 테스트가 드롭 로그 간격을 앞당기려고 바꾼다. */
    LongSupplier nanoTime = System::nanoTime;

    public WebhookService(WebhookConfigRepository webhookConfigRepository,
                          ObjectMapper objectMapper,
                          WebhookTargetPolicy targetPolicy,
                          WebhookProperties properties,
                          WebhookToggleCache toggles) {
        this.webhookConfigRepository = webhookConfigRepository;
        this.toggles = toggles;
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
        // 웹훅이 없거나 이 경로가 꺼진 프로젝트는 대기열을 쓰지 않는다 (UG-361). 모르면 넣고 전송 스레드가 확인한다.
        WebhookToggleCache.Decision decision = toggles.decide(projectId, source);
        if (decision == WebhookToggleCache.Decision.SKIP) return;
        Instant occurredAt = Instant.now(clock);
        try {
            dispatcher.execute(() -> dispatch(projectId, source, event, transactionUuid, occurredAt, data));
        } catch (RejectedExecutionException e) {
            if (decision == WebhookToggleCache.Decision.PROBE) toggles.probeDropped(projectId);
            recordDrop(projectId, event);
        }
    }

    /**
     * 버린 건수를 모아 {@link #DROP_LOG_INTERVAL} 에 한 줄만 남긴다 (UG-361). 조용하다가 처음 버리면 바로 남긴다.
     * 간격 안에 더 버린 건수는 {@link #flushDrops} 가 다음 간격에 남긴다.
     */
    private void recordDrop(Long projectId, WebhookEvent event) {
        droppedSinceLog.incrementAndGet();
        reportDrops(", latest projectId=" + projectId + ", event=" + event);
    }

    /** 폭주가 끝난 뒤 남은 건수가 다음 드롭까지 묻히지 않게 간격마다 비운다 (반박 리뷰 L-1). */
    @Scheduled(fixedDelay = 10_000L, initialDelay = 10_000L)
    void flushDrops() {
        reportDrops("");
    }

    private void reportDrops(String latest) {
        if (droppedSinceLog.get() == 0) return;
        long now = nanoTime.getAsLong();
        long last = lastDropLogNanos.get();
        if (last != NEVER_LOGGED && now - last < DROP_LOG_INTERVAL.toNanos()) return;
        if (!lastDropLogNanos.compareAndSet(last, now)) return;
        long dropped = droppedSinceLog.getAndSet(0);
        if (dropped > 0) {
            log.warn("Webhook dropped (dispatch queue full): count={} since last report{}", dropped, latest);
        }
    }

    void dispatch(Long projectId, CallerType source, WebhookEvent event,
                  String transactionUuid, Instant occurredAt, Object data) {
        try {
            long generation = toggles.generation();
            WebhookConfig config = webhookConfigRepository.findByProjectId(projectId).orElse(null);
            toggles.remember(projectId, config, generation);
            if (config == null || !WebhookToggleCache.isEnabled(config, source)) return;
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

    /**
     * @param secrets 서명 키들 (UG-344). 비어 있으면 서명 헤더를 붙이지 않는다.
     */
    Mono<Void> deliver(URI target, String eventId, byte[] body, List<String> secrets) {
        return attempt(target, eventId, body, secrets)
                .retryWhen(Retry.backoff(Math.max(0, properties.maxAttempts() - 1), properties.retryBackoff())
                        .filter(WebhookService::isRetryable)
                        .onRetryExhaustedThrow((spec, signal) -> signal.failure()))
                .then();
    }

    /**
     * 한 번의 시도. 재시도는 호출자가 정한다 — 실제 전송은 {@link #deliver}, 테스트 전송은 재시도 없이 이것만.
     *
     * @return 2xx 응답 코드. 2xx 가 아니면 {@link RejectedByReceiverException}
     */
    Mono<Integer> attempt(URI target, String eventId, byte[] body, List<String> secrets) {
        try {
            // IP 리터럴은 리졸버를 거치지 않는다. dispatch 가 이미 봤지만, 이 메서드만 부르는 경로에서도
            // 연결 전에 막는다 — 연결 뒤에 막으면 채널이 남는다.
            targetPolicy.checkWithoutLookup(target.toString());
        } catch (CustomGateException e) {
            return Mono.error(new WebhookTargetPolicy.TargetNotAllowedException(
                    target.getHost(), WebhookTargetPolicy.isDeniedRange(e)));
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
                            ? drained.thenReturn(status)
                            : drained.then(Mono.<Integer>error(new RejectedByReceiverException(status)));
                }))
                // 시도 한 번의 전체 시간 상한. responseTimeout 은 "읽기 사이의 공백" 이라, 1바이트씩
                // 흘려 보내는 수신 서버는 그것만으로 끊기지 않는다(반박 리뷰 W1). 넘으면 구독이
                // 취소되고 연결은 버려진다.
                .timeout(attemptTimeout);
    }

    /**
     * 콘솔 「테스트 전송」 (UG-344). 저장된 URL 로 {@code TEST} 이벤트 한 건을 <b>서명을 붙여, 재시도 없이</b> 보내고
     * 결과를 돌려준다. 요청 스레드에서 기다린다 — 최대 시도 한 번의 상한({@code attemptTimeout}, 기본 약 9초).
     *
     * <p>토글(API·데모)과 무관하게 보낸다. 고객이 직접 누른 시험이기 때문이다. 대신 남용은 호출자
     * ({@code SendWebhookTestUseCase})가 프로젝트별 간격과 동시 실행 수로 막는다.
     */
    public WebhookTestResult sendTest(Long projectId, WebhookConfig config) {
        WebhookConfig signed = withSecret(projectId, config);
        URI target;
        try {
            target = targetPolicy.checkWithoutLookup(signed.getWebhookUrl());
        } catch (CustomGateException e) {
            return WebhookTestResult.notSent(WebhookTargetPolicy.isDeniedRange(e)
                    ? WebhookTestResult.TARGET_DENIED_RANGE : WebhookTestResult.TARGET_NOT_ALLOWED);
        }
        WebhookPayload payload = new WebhookPayload(
                UUID.randomUUID().toString(),
                WebhookEvent.TEST.name(),
                TEST_SOURCE,
                null,
                Instant.now(clock),
                objectMapper.valueToTree(java.util.Map.of("message", "This is a test webhook from UNIVS GATE.")));
        byte[] body;
        try {
            body = objectMapper.writeValueAsBytes(payload);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
        List<String> secrets = signed.activeSecrets(LocalDateTime.ofInstant(Instant.now(clock), ZoneOffset.UTC));
        long started = System.nanoTime();
        try {
            Integer status = attempt(target, payload.eventId(), body, secrets).block(attemptTimeout.plusSeconds(2));
            WebhookTestResult result = WebhookTestResult.success(status, elapsedMs(started), payload.eventId());
            log.info("Webhook test sent: projectId={}, eventId={}", projectId, payload.eventId());
            return result;
        } catch (RuntimeException e) {
            WebhookTestResult result = WebhookTestResult.failed(classify(e), statusOf(e), elapsedMs(started), payload.eventId());
            log.info("Webhook test failed: projectId={}, result={}, host={}, cause={}",
                    projectId, result.result(), target.getHost(), describe(e));
            return result;
        }
    }

    private static long elapsedMs(long startedNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos);
    }

    private static Integer statusOf(Throwable error) {
        for (Throwable t = error; t != null; t = t.getCause()) {
            if (t instanceof RejectedByReceiverException rejected) return rejected.status;
            if (t.getCause() == t) break;
        }
        return null;
    }

    /**
     * 테스트 전송 실패를 화면에 보일 값으로 나눈다 (UG-344). {@link #isRetryable} 과 같은 판별을 쓴다 — 실제 전송이
     * 재시도하지 않는 실패와 화면의 실패 종류가 어긋나지 않게.
     */
    static String classify(Throwable error) {
        if (statusOf(error) != null) return WebhookTestResult.HTTP_ERROR;
        WebhookTargetPolicy.TargetNotAllowedException notAllowed = causeOf(error, WebhookTargetPolicy.TargetNotAllowedException.class);
        if (notAllowed != null) {
            // 온프레미스에서 차단 대역에 걸린 것을 「localhost 불가」 안내와 구분한다 (UG-348 기획 10/2 17:03). 클라우드는 숨긴다.
            return notAllowed.deniedRange ? WebhookTestResult.TARGET_DENIED_RANGE : WebhookTestResult.TARGET_NOT_ALLOWED;
        }
        // DNS 실패는 netty 가 모두 UnknownHostException 으로 감싼다(DnsResolveContext). 원인으로 나눈다 — 조회 시간 초과·
        // SERVFAIL 을 「주소 없음」으로 보이면 고객은 URL 오타로 오해한다 (반박 리뷰 W5). isRetryable 과 같은 경계다.
        if (isNxDomain(error)) return WebhookTestResult.HOST_NOT_FOUND;
        if (hasCause(error, io.netty.resolver.dns.DnsNameResolverTimeoutException.class)) return WebhookTestResult.TIMEOUT;
        if (hasCause(error, DnsErrorCauseException.class)) return WebhookTestResult.CONNECTION_FAILED;
        if (hasCause(error, java.net.UnknownHostException.class)) return WebhookTestResult.HOST_NOT_FOUND;
        if (hasCause(error, SslHandshakeTimeoutException.class)) return WebhookTestResult.TIMEOUT;
        if (hasCause(error, javax.net.ssl.SSLException.class)) return WebhookTestResult.TLS_ERROR;
        if (hasCause(error, TimeoutException.class)
                || hasCause(error, io.netty.handler.timeout.TimeoutException.class)
                || hasCause(error, io.netty.channel.ConnectTimeoutException.class)) {
            return WebhookTestResult.TIMEOUT;
        }
        return WebhookTestResult.CONNECTION_FAILED;
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
        return causeOf(error, type) != null;
    }

    private static <T extends Throwable> T causeOf(Throwable error, Class<T> type) {
        for (Throwable t = error; t != null; t = t.getCause()) {
            if (type.isInstance(t)) return type.cast(t);
            if (t.getCause() == t) break;
        }
        return null;
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
        WebhookTargetPolicy.TargetNotAllowedException notAllowed = causeOf(error, WebhookTargetPolicy.TargetNotAllowedException.class);
        if (notAllowed != null) return notAllowed.deniedRange ? "TARGET_DENIED_RANGE" : "TARGET_NOT_ALLOWED";
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
        long dropped = droppedSinceLog.getAndSet(0);
        if (dropped > 0) log.warn("Webhook dropped (dispatch queue full): count={} since last report", dropped);
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
