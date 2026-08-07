package ai.univs.palm.shared.feign;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.univs.palm.shared.exception.CustomFeignException;
import ai.univs.palm.shared.exception.UpstreamCallException;
import feign.FeignException;
import feign.Request;
import feign.RetryableException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.openfeign.FeignClient;

/**
 * UG-308: 응답을 받지 못한 Feign 실패가 {@link UpstreamCallException} 으로 번역된다.
 *
 * <p>번역이 없으면 {@code RetryableException} 이 {@code handleGlobalException} 까지 떨어져
 * ERROR + 90여 줄 스택트레이스가 남고 클라이언트는 500 을 받는다. 다른 실패 경로는 전부
 * 400 이라 <b>정말 죽었을 때만</b> 응답이 달라지는 상태였다.
 *
 * <p>진짜 {@link FeignFailureTranslator} 에 {@code @FeignClient} 인터페이스 구현을 넣어
 * 돌린다. 스프링 컨텍스트는 띄우지 않는다 — {@code BeanPostProcessor} 는 그냥 메서드다.
 */
@DisplayName("UG-308: Feign 실패 번역")
class FeignFailureTranslatorTest {

    private final FeignFailureTranslator translator = new FeignFailureTranslator();

    /** 실제 Feign 클라이언트와 같은 모양. 어노테이션이 붙어 있어야 래핑 대상이 된다. */
    @FeignClient(name = "probe")
    interface ProbeFeign {
        String call();
        void run();
    }

    /** 어노테이션이 없는 평범한 빈. 감싸이면 안 된다. */
    interface PlainBean {
        String call();
    }

    private static Request 더미요청() {
        return Request.create(Request.HttpMethod.POST, "http://probe/x",
                Map.of(), new byte[0], StandardCharsets.UTF_8, null);
    }

    private ProbeFeign 감싼다(RuntimeException 던질것) {
        ProbeFeign raw = new ProbeFeign() {
            @Override
            public String call() {
                throw 던질것;
            }

            @Override
            public void run() {
                throw 던질것;
            }
        };
        return (ProbeFeign) translator.postProcessAfterInitialization(raw, "probeFeign");
    }

    @Nested
    @DisplayName("번역한다")
    class 번역 {

        @Test
        @DisplayName("연결 거부·타임아웃(RetryableException) → NO_RESPONSE")
        void 연결_실패() {
            var 원인 = new RetryableException(
                    -1, "Connection refused", Request.HttpMethod.POST,
                    new IOException("Connection refused"), (Long) null, 더미요청());

            assertThatThrownBy(() -> 감싼다(원인).call())
                    .isInstanceOf(UpstreamCallException.class)
                    .satisfies(e -> {
                        var ex = (UpstreamCallException) e;
                        // 0 은 '상태 코드라는 것이 없었다' 는 뜻이다. 502·503 과 갈라야 한다 —
                        // 전자는 죽은 것이고 후자는 살아서 오류를 응답한 것이다.
                        assertThat(ex.getUpstreamStatus()).isEqualTo(UpstreamCallException.NO_RESPONSE);
                        assertThat(ex.getOperation()).isEqualTo("ProbeFeign#call");
                        assertThat(ex.getReason()).contains("연결 실패·타임아웃");
                        assertThat(ex.getCause()).isSameAs(원인);
                    });
        }

        @Test
        @DisplayName("본문 디코딩 실패(FeignException) → NO_RESPONSE. HTTP 200 에서도 난다")
        void 디코딩_실패() {
            var 원인 = new FeignException.InternalServerError(
                    "broken body", 더미요청(), new byte[0], Map.of());

            assertThatThrownBy(() -> 감싼다(원인).call())
                    .isInstanceOf(UpstreamCallException.class)
                    .satisfies(e -> assertThat(((UpstreamCallException) e).getReason())
                            .contains("응답 처리 실패"));
        }

        @Test
        @DisplayName("void 메서드도 감싼다")
        void void_메서드() {
            var 원인 = new RetryableException(
                    -1, "timeout", Request.HttpMethod.POST,
                    new IOException("timeout"), (Long) null, 더미요청());

            assertThatThrownBy(() -> 감싼다(원인).run())
                    .isInstanceOf(UpstreamCallException.class);
        }
    }

    @Nested
    @DisplayName("삼키지 않는다")
    class 통과 {

        /**
         * 디코더가 만든 예외는 그대로 올라가야 한다. 여기서 삼키면 4xx 오류가 전부
         * "ML 모듈이 죽었다" 로 둔갑하고, 라이브니스 오류 흡수 판정도 깨진다.
         */
        @Test
        @DisplayName("CommonErrorDecoder 가 만든 CustomFeignException")
        void 디코더_예외는_그대로() {
            var 원인 = new CustomFeignException("X-001", "SOME_TYPE", "메시지");

            assertThatThrownBy(() -> 감싼다(원인).call()).isSameAs(원인);
        }

        @Test
        @DisplayName("이미 번역된 UpstreamCallException")
        void 상위예외는_그대로() {
            var 원인 = new UpstreamCallException(503, "op", "reason");

            assertThatThrownBy(() -> 감싼다(원인).call()).isSameAs(원인);
        }

        /** 우리 코드의 버그를 "원격 호출 실패" 로 분류하면 원인이 가려진다. */
        @Test
        @DisplayName("우리 코드의 RuntimeException")
        void 우리_버그는_그대로() {
            var 원인 = new IllegalStateException("우리 버그");

            assertThatThrownBy(() -> 감싼다(원인).call()).isSameAs(원인);
        }
    }

    @Nested
    @DisplayName("감쌀 대상 판정")
    class 대상 {

        @Test
        @DisplayName("@FeignClient 인터페이스 구현은 감싼다")
        void 페인은_감싼다() {
            ProbeFeign raw = new ProbeFeign() {
                @Override public String call() { return "ok"; }
                @Override public void run() { }
            };

            Object wrapped = translator.postProcessAfterInitialization(raw, "probeFeign");

            assertThat(wrapped).isNotSameAs(raw);
            assertThat(((ProbeFeign) wrapped).call()).isEqualTo("ok");
        }

        /** 애플리케이션의 모든 빈이 이 후처리기를 지난다. 무관한 빈을 감싸면 안 된다. */
        @Test
        @DisplayName("일반 빈은 그대로 둔다")
        void 일반빈은_그대로() {
            PlainBean raw = () -> "ok";

            assertThat(translator.postProcessAfterInitialization(raw, "plain")).isSameAs(raw);
        }

        @Test
        @DisplayName("인터페이스가 없는 클래스도 그대로 둔다")
        void 클래스_빈도_그대로() {
            Object raw = new Object();

            assertThat(translator.postProcessAfterInitialization(raw, "obj")).isSameAs(raw);
        }
    }

    /**
     * {@code NO_RESPONSE} 가 실제 상태 코드와 겹치지 않아야 한다.
     *
     * <p>다른 테스트들은 이 상수를 <b>기대값으로도 쓰기 때문에</b> 값이 502 로 바뀌어도 전부
     * 초록이다 — 동어반복이다(변이 심기로 확인). 이 상수의 존재 이유는 "상태 코드가 없었다" 를
     * 상태 코드 자리에 담는 것이므로, 실제 코드와 충돌하지 않는다는 성질 자체를 못박는다.
     * 502 로 바뀌면 로그에서 '죽었다' 와 '오류를 응답했다' 가 구분되지 않는다.
     */
    @Test
    @DisplayName("NO_RESPONSE 는 실제 HTTP 상태 코드와 겹치지 않는다")
    void NO_RESPONSE_는_충돌하지_않는다() {
        assertThat(UpstreamCallException.NO_RESPONSE)
                .as("HTTP 상태 코드는 100 미만이 존재하지 않는다. 그 대역 밖이어야 한다")
                .isEqualTo(0)
                .isLessThan(100);
    }
}
