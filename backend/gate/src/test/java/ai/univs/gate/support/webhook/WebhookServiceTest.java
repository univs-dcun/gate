package ai.univs.gate.support.webhook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ai.univs.gate.modules.webhook.domain.entity.WebhookConfig;
import ai.univs.gate.modules.webhook.domain.repository.WebhookConfigRepository;
import ai.univs.gate.shared.web.enums.CallerType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntSupplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 웹훅 전송 (UG-111). 실제 HTTP 서버(JDK 내장)에 보낸다.
 *
 * <p>로컬 서버는 루프백이라 운영 정책이면 막힌다. 그래서 전송 동작을 보는 테스트는 루프백을
 * 허용하는 정책을 쓰고, 막히는지 보는 테스트만 운영 정책을 쓴다.
 */
@DisplayName("UG-111: 웹훅 전송")
class WebhookServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-29T05:00:00Z");

    private final ObjectMapper objectMapper = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build();
    private final WebhookConfigRepository repository = mock(WebhookConfigRepository.class);

    private HttpServer server;
    private final List<Received> received = new CopyOnWriteArrayList<>();
    private IntSupplier status = () -> 200;
    private final AtomicInteger redirected = new AtomicInteger();
    private final AtomicInteger dripStarted = new AtomicInteger();
    private volatile long delayMillis = 0;
    private WebhookService service;

    record Received(String eventId, String userAgent, String contentType, JsonNode body, String signature, byte[] raw) { }

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/hook", exchange -> {
            byte[] raw = exchange.getRequestBody().readAllBytes();
            received.add(new Received(
                    exchange.getRequestHeaders().getFirst(WebhookService.EVENT_ID_HEADER),
                    exchange.getRequestHeaders().getFirst("User-Agent"),
                    exchange.getRequestHeaders().getFirst("Content-Type"),
                    objectMapper.readTree(new String(raw, StandardCharsets.UTF_8)),
                    exchange.getRequestHeaders().getFirst(WebhookSignature.HEADER),
                    raw));
            try {
                if (delayMillis > 0) Thread.sleep(delayMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            int code = status.getAsInt();
            if (code / 100 == 3) {
                // 리다이렉트를 켜면 따라갈 곳이 있어야 테스트가 의미가 있다 (반박 리뷰 M24)
                exchange.getResponseHeaders().add("Location", "http://127.0.0.1:" + server.getAddress().getPort() + "/other");
            }
            exchange.sendResponseHeaders(code, -1);
            exchange.close();
        });
        server.createContext("/other", exchange -> {
            redirected.incrementAndGet();
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        server.createContext("/drip", exchange -> {
            dripStarted.incrementAndGet();
            exchange.sendResponseHeaders(200, 0);   // chunked — 본문을 조금씩 흘려 보낸다
            try (var out = exchange.getResponseBody()) {
                for (int i = 0; i < 100; i++) {
                    out.write('x');
                    out.flush();
                    Thread.sleep(100);
                }
            } catch (IOException | InterruptedException e) {
                // 클라이언트가 끊었다 — 기대한 동작
            }
            exchange.close();
        });
        // 기본 실행기는 스레드 하나라, 늦게 응답하는 요청이 다음 요청(재시도)의 접수를 막는다.
        server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
        if (service != null) service.shutdown();
    }

    private String url() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/hook";
    }

    /** 루프백을 허용하는 정책 — 로컬 서버로 보내려면 필요하다. */
    private static WebhookTargetPolicy loopbackAllowed(WebhookProperties props) {
        return new WebhookTargetPolicy(props) {
            @Override
            boolean isAllowed(InetAddress address) {
                return true;
            }
        };
    }

    private static WebhookProperties props(int maxAttempts, Duration responseTimeout) {
        return new WebhookProperties(false, Duration.ofSeconds(2), responseTimeout,
                maxAttempts, Duration.ofMillis(20), 10, 100, 100);
    }

    private WebhookService service(WebhookTargetPolicy policy, WebhookProperties props) {
        service = new WebhookService(repository, objectMapper, policy, props);
        service.clock = Clock.fixed(NOW, ZoneOffset.UTC);
        return service;
    }

    private void configure(String url, boolean demo, boolean api) {
        WebhookConfig config = WebhookConfig.builder().webhookUrl(url).demoEnabled(demo).apiEnabled(api).build();
        when(repository.findByProjectId(1L)).thenReturn(Optional.of(config));
    }

    private void awaitReceived(int count) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5_000;
        while (received.size() < count && System.currentTimeMillis() < deadline) Thread.sleep(20);
        assertThat(received).hasSize(count);
    }

    /** 보내지 않았음을 확인한다 — 비동기라 잠깐 기다려 본다. */
    private void assertNothingSent() throws InterruptedException {
        Thread.sleep(300);
        assertThat(received).isEmpty();
    }

    @Test
    @DisplayName("본문에 eventId·event·source·transactionUuid·occurredAt·data 가 실리고, 헤더 eventId 와 같다")
    void 본문과_헤더() throws Exception {
        WebhookProperties props = props(3, Duration.ofSeconds(5));
        configure(url(), false, true);

        service(loopbackAllowed(props), props)
                .send(1L, CallerType.API, WebhookEvent.IDENTIFY, "tx-1", Map.of("result", true));

        awaitReceived(1);
        Received r = received.getFirst();
        assertThat(r.body().get("event").asText()).isEqualTo("IDENTIFY");
        assertThat(r.body().get("source").asText()).isEqualTo("API");
        assertThat(r.body().get("transactionUuid").asText()).isEqualTo("tx-1");
        assertThat(r.body().get("occurredAt").asText()).isEqualTo("2026-09-29T05:00:00Z");
        assertThat(r.body().get("data").get("result").asBoolean()).isTrue();
        assertThat(r.body().get("eventId").asText()).isNotBlank().isEqualTo(r.eventId());
        assertThat(r.userAgent()).isEqualTo(WebhookService.USER_AGENT);
        assertThat(r.contentType()).startsWith("application/json");
    }

    private void configureSigned(String url, String secret, String previous, java.time.LocalDateTime previousExpiresAt) {
        WebhookConfig config = WebhookConfig.builder().id(9L).webhookUrl(url).demoEnabled(false).apiEnabled(true)
                .webhookSecret(secret).previousWebhookSecret(previous).previousSecretExpiresAt(previousExpiresAt).build();
        when(repository.findByProjectId(1L)).thenReturn(Optional.of(config));
    }

    @Test
    @DisplayName("UG-344: 서명 헤더 — t 는 보낸 시각, v1 은 받은 본문 바이트 그대로의 HMAC 이다")
    void 서명() throws Exception {
        WebhookProperties props = props(1, Duration.ofSeconds(5));
        configureSigned(url(), "whsec_k", null, null);

        service(loopbackAllowed(props), props)
                .send(1L, CallerType.API, WebhookEvent.IDENTIFY, "tx-s", Map.of("result", true));

        awaitReceived(1);
        Received r = received.getFirst();
        long t = NOW.getEpochSecond();
        assertThat(r.signature()).isEqualTo("t=" + t + ",v1=" + WebhookSignature.sign("whsec_k", t, r.raw()));
    }

    @Test
    @DisplayName("UG-344: 재발급 후 겹치는 동안에는 v1 이 둘 — 새 키가 먼저, 만료가 지나면 하나")
    void 겹치는_기간() throws Exception {
        WebhookProperties props = props(1, Duration.ofSeconds(5));
        java.time.LocalDateTime now = java.time.LocalDateTime.ofInstant(NOW, ZoneOffset.UTC);
        configureSigned(url(), "whsec_new", "whsec_old", now.plusHours(1));
        WebhookService s = service(loopbackAllowed(props), props);

        s.send(1L, CallerType.API, WebhookEvent.IDENTIFY, "tx-1", Map.of());
        awaitReceived(1);
        long t = NOW.getEpochSecond();
        byte[] raw = received.getFirst().raw();
        assertThat(received.getFirst().signature()).isEqualTo("t=" + t
                + ",v1=" + WebhookSignature.sign("whsec_new", t, raw) + ",v1=" + WebhookSignature.sign("whsec_old", t, raw));

        configureSigned(url(), "whsec_new", "whsec_old", now);   // 만료 시각 == 지금 → 이미 지났다
        s.send(1L, CallerType.API, WebhookEvent.IDENTIFY, "tx-2", Map.of());
        awaitReceived(2);
        assertThat(received.get(1).signature()).doesNotContain(WebhookSignature.sign("whsec_old", t, received.get(1).raw()));
        assertThat(received.get(1).signature().split(",v1=")).hasSize(2);
    }

    @Test
    @DisplayName("UG-344: 키가 없던 설정은 보내기 전에 채우고, 다시 읽은 키로 서명한다")
    void 키_채우고_서명() throws Exception {
        WebhookProperties props = props(1, Duration.ofSeconds(5));
        WebhookConfig 없음 = WebhookConfig.builder().id(9L).webhookUrl(url()).demoEnabled(false).apiEnabled(true).build();
        WebhookConfig 채워짐 = WebhookConfig.builder().id(9L).webhookUrl(url()).demoEnabled(false).apiEnabled(true)
                .webhookSecret("whsec_assigned").build();
        when(repository.findByProjectId(1L)).thenReturn(Optional.of(없음), Optional.of(채워짐));

        service(loopbackAllowed(props), props).send(1L, CallerType.API, WebhookEvent.IDENTIFY, "tx", Map.of());

        awaitReceived(1);
        org.mockito.Mockito.verify(repository).assignSecretIfAbsent(org.mockito.ArgumentMatchers.eq(9L),
                org.mockito.ArgumentMatchers.startsWith("whsec_"));
        Received r = received.getFirst();
        assertThat(r.signature()).endsWith(WebhookSignature.sign("whsec_assigned", NOW.getEpochSecond(), r.raw()));
    }

    @Test
    @DisplayName("UG-344: 키를 채우다 DB 가 실패하면 서명 없이라도 보낸다 — 웹훅을 버리지 않는다")
    void 키_채우기_실패() throws Exception {
        WebhookProperties props = props(1, Duration.ofSeconds(5));
        configure(url(), false, true);
        when(repository.assignSecretIfAbsent(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString()))
                .thenThrow(new IllegalStateException("db down"));

        service(loopbackAllowed(props), props).send(1L, CallerType.API, WebhookEvent.IDENTIFY, "tx", Map.of());

        awaitReceived(1);
        assertThat(received.getFirst().signature()).isNull();
    }

    @Test
    @DisplayName("데모 호출은 데모 토글이 켜져 있을 때 보낸다 — UG-111 이전에는 토글과 무관하게 보내지 않았다")
    void 데모_토글() throws Exception {
        WebhookProperties props = props(1, Duration.ofSeconds(5));
        configure(url(), true, false);

        service(loopbackAllowed(props), props)
                .send(1L, CallerType.DEMO, WebhookEvent.LIVENESS, "tx-d", Map.of());

        awaitReceived(1);
        assertThat(received.getFirst().body().get("source").asText()).isEqualTo("DEMO");
    }

    @Test
    @DisplayName("그 호출 경로의 토글이 꺼져 있으면 보내지 않는다")
    void 토글_꺼짐() throws Exception {
        WebhookProperties props = props(1, Duration.ofSeconds(5));
        configure(url(), false, true);
        WebhookService s = service(loopbackAllowed(props), props);

        s.send(1L, CallerType.DEMO, WebhookEvent.LIVENESS, "tx", Map.of());

        assertNothingSent();
    }

    @Test
    @DisplayName("설정이 없으면 보내지 않는다")
    void 설정_없음() throws Exception {
        WebhookProperties props = props(1, Duration.ofSeconds(5));
        when(repository.findByProjectId(1L)).thenReturn(Optional.empty());

        service(loopbackAllowed(props), props).send(1L, CallerType.API, WebhookEvent.IDENTIFY, "tx", Map.of());

        assertNothingSent();
    }

    @Test
    @DisplayName("저장된 URL 이 내부 주소면 보내기 직전에 막는다 (운영 정책)")
    void 내부_주소는_보내지_않는다() throws Exception {
        WebhookProperties props = props(1, Duration.ofSeconds(5));
        configure(url(), true, true);   // 127.0.0.1

        service(new WebhookTargetPolicy(props), props)
                .send(1L, CallerType.API, WebhookEvent.IDENTIFY, "tx", Map.of());

        assertNothingSent();
    }

    @Test
    @DisplayName("IPv4 호환 IPv6 리터럴로 루프백에 닿지 못한다 — netty 는 [::127.0.0.1] 을 ::ffff:127.0.0.1 로 연결한다 (반박 리뷰 B1)")
    void IPv4_호환_리터럴() throws Exception {
        WebhookProperties props = props(1, Duration.ofSeconds(5));
        configure("http://[::127.0.0.1]:" + server.getAddress().getPort() + "/hook", true, true);

        service(new WebhookTargetPolicy(props), props)
                .send(1L, CallerType.API, WebhookEvent.IDENTIFY, "tx", Map.of());

        assertNothingSent();
    }

    @Test
    @DisplayName("호스트 이름이 내부 주소로 풀리면 연결 단계의 리졸버가 막는다 — DNS 리바인딩 대비")
    void 리졸버가_막는다() throws Exception {
        WebhookProperties props = props(3, Duration.ofSeconds(5));
        WebhookService s = service(new WebhookTargetPolicy(props), props);
        URI target = URI.create("http://localhost:" + server.getAddress().getPort() + "/hook");

        assertThatThrownBy(() -> s.deliver(target, "e", "{}".getBytes(), List.of()).block(Duration.ofSeconds(10)))
                .satisfies(e -> assertThat(WebhookService.isRetryable(e)).isFalse());
        assertThat(received).isEmpty();
    }

    @Test
    @DisplayName("막힌 호스트로 계속 보내도 소켓이 새지 않는다 — 해석 단계에서 막아야 채널이 닫힌다 (2차 반박 리뷰 B1)")
    void 막힌_주소_소켓_누수() {
        WebhookProperties props = props(1, Duration.ofSeconds(5));
        WebhookService s = service(new WebhookTargetPolicy(props), props);
        URI blocked = URI.create("http://localhost:" + server.getAddress().getPort() + "/hook");   // hosts 파일 → 동기 해석
        var os = java.lang.management.ManagementFactory.getOperatingSystemMXBean();
        org.junit.jupiter.api.Assumptions.assumeTrue(os instanceof com.sun.management.UnixOperatingSystemMXBean);
        var unix = (com.sun.management.UnixOperatingSystemMXBean) os;

        s.deliver(blocked, "warm", "{}".getBytes(), List.of()).onErrorComplete().block(Duration.ofSeconds(10));   // 풀·루프 기동
        long before = unix.getOpenFileDescriptorCount();
        for (int i = 0; i < 200; i++) {
            s.deliver(blocked, "e" + i, "{}".getBytes(), List.of()).onErrorComplete().block(Duration.ofSeconds(10));
        }
        long after = unix.getOpenFileDescriptorCount();

        assertThat(received).isEmpty();
        assertThat(after - before).as("fd 증가 (수정 전: 200회에 +100 이상)").isLessThan(20);
    }

    @Test
    @DisplayName("deliver 만 불러도 IP 리터럴 내부 주소에는 연결하지 않는다 — 연결 뒤에 막으면 채널이 남는다")
    void deliver_리터럴_사전검사() {
        WebhookProperties props = props(3, Duration.ofSeconds(5));
        WebhookService s = service(new WebhookTargetPolicy(props), props);

        assertThatThrownBy(() -> s.deliver(URI.create(url()), "e", "{}".getBytes(), List.of()).block(Duration.ofSeconds(10)))
                .hasCauseInstanceOf(WebhookTargetPolicy.TargetNotAllowedException.class)   // block() 이 검사 예외를 감싼다
                .satisfies(e -> assertThat(WebhookService.isRetryable(e)).isFalse());
        assertThat(received).isEmpty();
    }

    @Test
    @DisplayName("5xx 는 재시도하고, 재시도해도 eventId 는 같다")
    void 서버_오류_재시도() {
        AtomicInteger calls = new AtomicInteger();
        status = () -> calls.incrementAndGet() < 3 ? 503 : 200;
        WebhookProperties props = props(3, Duration.ofSeconds(5));

        service(loopbackAllowed(props), props)
                .deliver(URI.create(url()), "evt-1", "{\"a\":1}".getBytes(), List.of()).block(Duration.ofSeconds(10));

        assertThat(received).hasSize(3);
        assertThat(received).extracting(Received::eventId).containsOnly("evt-1");
    }

    @Test
    @DisplayName("UG-344: 재시도마다 서명을 새로 만든다 — t 가 실제 보낸 시각이라 수신 측 시간 검사가 재시도를 오래된 요청으로 보지 않는다")
    void 재시도_서명() {
        AtomicInteger calls = new AtomicInteger();
        status = () -> calls.incrementAndGet() < 3 ? 503 : 200;
        WebhookProperties props = props(3, Duration.ofSeconds(5));
        WebhookService s = service(loopbackAllowed(props), props);
        java.util.concurrent.atomic.AtomicLong seconds = new java.util.concurrent.atomic.AtomicLong(NOW.getEpochSecond());
        s.clock = new Clock() {
            @Override public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
            @Override public Clock withZone(java.time.ZoneId zone) { return this; }
            @Override public Instant instant() { return Instant.ofEpochSecond(seconds.getAndAdd(10)); }
        };

        s.deliver(URI.create(url()), "evt-1", "{\"a\":1}".getBytes(), List.of("whsec_k")).block(Duration.ofSeconds(10));

        assertThat(received).hasSize(3);
        assertThat(received).extracting(Received::signature).doesNotHaveDuplicates();
        for (Received r : received) {
            long t = Long.parseLong(r.signature().substring(2, r.signature().indexOf(',')));
            assertThat(r.signature()).isEqualTo("t=" + t + ",v1=" + WebhookSignature.sign("whsec_k", t, r.raw()));
        }
    }

    @Test
    @DisplayName("429 는 재시도한다")
    void 요청_제한_재시도() {
        AtomicInteger calls = new AtomicInteger();
        status = () -> calls.incrementAndGet() < 2 ? 429 : 204;
        WebhookProperties props = props(3, Duration.ofSeconds(5));

        service(loopbackAllowed(props), props)
                .deliver(URI.create(url()), "e", "{}".getBytes(), List.of()).block(Duration.ofSeconds(10));

        assertThat(received).hasSize(2);
    }

    @Test
    @DisplayName("그 밖의 4xx 는 수신 측이 거절한 것이라 재시도하지 않는다")
    void 클라이언트_오류는_한_번() {
        status = () -> 400;
        WebhookProperties props = props(3, Duration.ofSeconds(5));
        WebhookService s = service(loopbackAllowed(props), props);

        assertThatThrownBy(() -> s.deliver(URI.create(url()), "e", "{}".getBytes(), List.of()).block(Duration.ofSeconds(10)))
                .isInstanceOf(WebhookService.RejectedByReceiverException.class);
        assertThat(received).hasSize(1);
    }

    @Test
    @DisplayName("3xx 는 따라가지 않고 실패로 본다 — 공인 주소가 내부로 리다이렉트하는 우회를 막는다")
    void 리다이렉트는_따르지_않는다() {
        status = () -> 302;
        WebhookProperties props = props(3, Duration.ofSeconds(5));
        WebhookService s = service(loopbackAllowed(props), props);

        assertThatThrownBy(() -> s.deliver(URI.create(url()), "e", "{}".getBytes(), List.of()).block(Duration.ofSeconds(10)))
                .isInstanceOf(WebhookService.RejectedByReceiverException.class);
        assertThat(received).hasSize(1);
        assertThat(redirected).hasValue(0);
    }

    @Test
    @DisplayName("본문을 1바이트씩 흘려 보내는 수신 서버도 시도당 전체 시간 상한에서 끊는다 (반박 리뷰 W1)")
    void 흘려_보내기() {
        // 읽기 공백 300ms 보다 자주(100ms) 1바이트씩 보낸다 — responseTimeout 만으로는 10초간 끊기지 않는다.
        // 시도당 상한 = connect 200ms + response 300ms + 1초 = 1.5초.
        // 시도 2번 — 상한이 시도마다 걸리는지(전체 한 번이 아닌지) 본다 (2차 반박 리뷰 S2).
        WebhookProperties props = new WebhookProperties(false, Duration.ofMillis(200), Duration.ofMillis(300),
                2, Duration.ofMillis(20), 10, 100, 100);
        WebhookService s = service(loopbackAllowed(props), props);
        URI drip = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/drip");

        long started = System.nanoTime();
        assertThatThrownBy(() -> s.deliver(drip, "e", "{}".getBytes(), List.of()).block(Duration.ofSeconds(10)))
                .satisfies(e -> assertThat(WebhookService.isRetryable(e)).isTrue());
        long elapsedMillis = (System.nanoTime() - started) / 1_000_000;

        assertThat(dripStarted).hasValue(2);
        assertThat(elapsedMillis).isBetween(2_500L, 7_000L);
    }

    @Test
    @DisplayName("응답이 늦으면 타임아웃 후 재시도하고, 다 실패하면 포기한다")
    void 타임아웃() {
        delayMillis = 700;
        WebhookProperties props = props(2, Duration.ofMillis(200));
        WebhookService s = service(loopbackAllowed(props), props);

        assertThatThrownBy(() -> s.deliver(URI.create(url()), "e", "{}".getBytes(), List.of()).block(Duration.ofSeconds(10)))
                .satisfies(e -> assertThat(WebhookService.isRetryable(e)).isTrue());
        assertThat(received).hasSize(2);
    }

    @Test
    @DisplayName("재시도 판정: 연결·타임아웃·5xx·429 만 재시도한다")
    void 재시도_판정() {
        assertThat(WebhookService.isRetryable(new WebhookService.RejectedByReceiverException(500))).isTrue();
        assertThat(WebhookService.isRetryable(new WebhookService.RejectedByReceiverException(429))).isTrue();
        assertThat(WebhookService.isRetryable(new WebhookService.RejectedByReceiverException(404))).isFalse();
        assertThat(WebhookService.isRetryable(new WebhookService.RejectedByReceiverException(301))).isFalse();
        assertThat(WebhookService.isRetryable(new RuntimeException(new java.net.ConnectException()))).isTrue();
        assertThat(WebhookService.isRetryable(new RuntimeException(new TimeoutException()))).isTrue();
        assertThat(WebhookService.isRetryable(
                new RuntimeException(new WebhookTargetPolicy.TargetNotAllowedException("h")))).isFalse();
        assertThat(WebhookService.isRetryable(new IllegalStateException("x"))).isFalse();
        // 다시 보내도 같은 실패 — 막힌 주소, 인증서 오류
        assertThat(WebhookService.isRetryable(
                new RuntimeException(new javax.net.ssl.SSLHandshakeException("bad cert")))).isFalse();
        // 일시적일 수 있다 — 조회 타임아웃·SERVFAIL(UnknownHostException 으로 온다), TLS 핸드셰이크 타임아웃
        assertThat(WebhookService.isRetryable(new RuntimeException(new java.net.UnknownHostException("timeout")))).isTrue();
        assertThat(WebhookService.isRetryable(new RuntimeException(
                new io.netty.handler.ssl.SslHandshakeTimeoutException("slow")))).isTrue();
    }
}
