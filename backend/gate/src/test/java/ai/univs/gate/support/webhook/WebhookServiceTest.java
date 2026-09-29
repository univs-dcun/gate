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
    private volatile long delayMillis = 0;
    private WebhookService service;

    record Received(String eventId, String userAgent, String contentType, JsonNode body) { }

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/hook", exchange -> {
            byte[] raw = exchange.getRequestBody().readAllBytes();
            received.add(new Received(
                    exchange.getRequestHeaders().getFirst(WebhookService.EVENT_ID_HEADER),
                    exchange.getRequestHeaders().getFirst("User-Agent"),
                    exchange.getRequestHeaders().getFirst("Content-Type"),
                    objectMapper.readTree(new String(raw, StandardCharsets.UTF_8))));
            try {
                if (delayMillis > 0) Thread.sleep(delayMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            exchange.sendResponseHeaders(status.getAsInt(), -1);
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
    @DisplayName("호스트 이름이 내부 주소로 풀리면 연결 단계의 리졸버가 막는다 — DNS 리바인딩 대비")
    void 리졸버가_막는다() throws Exception {
        WebhookProperties props = props(3, Duration.ofSeconds(5));
        WebhookService s = service(new WebhookTargetPolicy(props), props);
        URI target = URI.create("http://localhost:" + server.getAddress().getPort() + "/hook");

        assertThatThrownBy(() -> s.deliver(target, "e", "{}".getBytes()).block(Duration.ofSeconds(10)))
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
                .deliver(URI.create(url()), "evt-1", "{\"a\":1}".getBytes()).block(Duration.ofSeconds(10));

        assertThat(received).hasSize(3);
        assertThat(received).extracting(Received::eventId).containsOnly("evt-1");
    }

    @Test
    @DisplayName("429 는 재시도한다")
    void 요청_제한_재시도() {
        AtomicInteger calls = new AtomicInteger();
        status = () -> calls.incrementAndGet() < 2 ? 429 : 204;
        WebhookProperties props = props(3, Duration.ofSeconds(5));

        service(loopbackAllowed(props), props)
                .deliver(URI.create(url()), "e", "{}".getBytes()).block(Duration.ofSeconds(10));

        assertThat(received).hasSize(2);
    }

    @Test
    @DisplayName("그 밖의 4xx 는 수신 측이 거절한 것이라 재시도하지 않는다")
    void 클라이언트_오류는_한_번() {
        status = () -> 400;
        WebhookProperties props = props(3, Duration.ofSeconds(5));
        WebhookService s = service(loopbackAllowed(props), props);

        assertThatThrownBy(() -> s.deliver(URI.create(url()), "e", "{}".getBytes()).block(Duration.ofSeconds(10)))
                .isInstanceOf(WebhookService.RejectedByReceiverException.class);
        assertThat(received).hasSize(1);
    }

    @Test
    @DisplayName("3xx 는 따라가지 않고 실패로 본다 — 공인 주소가 내부로 리다이렉트하는 우회를 막는다")
    void 리다이렉트는_따르지_않는다() {
        status = () -> 302;
        WebhookProperties props = props(3, Duration.ofSeconds(5));
        WebhookService s = service(loopbackAllowed(props), props);

        assertThatThrownBy(() -> s.deliver(URI.create(url()), "e", "{}".getBytes()).block(Duration.ofSeconds(10)))
                .isInstanceOf(WebhookService.RejectedByReceiverException.class);
        assertThat(received).hasSize(1);
    }

    @Test
    @DisplayName("응답이 늦으면 타임아웃 후 재시도하고, 다 실패하면 포기한다")
    void 타임아웃() {
        delayMillis = 700;
        WebhookProperties props = props(2, Duration.ofMillis(200));
        WebhookService s = service(loopbackAllowed(props), props);

        assertThatThrownBy(() -> s.deliver(URI.create(url()), "e", "{}".getBytes()).block(Duration.ofSeconds(10)))
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
    }
}
