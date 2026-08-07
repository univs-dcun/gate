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
                        // cause 는 일부러 싣지 않는다 — 연결 거부의 스택은 매번 같아
                        // 정보가 없다. 상세는 리뷰_보완.연결실패는_cause_없음 참고.
                        assertThat(ex.getCause()).isNull();
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

    @Nested
    @DisplayName("반박 리뷰가 뚫은 지점")
    class 리뷰_보완 {


        /**
         * {@code reason} 이 예외 메시지를 옮기지 않는다.
         *
         * <p>{@code FeignException.getMessage()} 는 요청 URL·본문 일부를 담고, 이 서비스의
         * 본문에는 <b>이미지 바이트나 descriptor</b> 가 들어간다. javadoc 이 그걸 피한다고
         *적어 뒀는데 테스트가 고정하지 않아, 메시지를 덧붙이는 변이가 통과했다(반박 리뷰).
         */
        @Test
        @DisplayName("reason 에 예외 메시지가 섞이지 않는다 — 이미지·descriptor 유출 방지")
        void reason_은_메시지를_담지_않는다() {
            String 민감한내용 = "descriptor=AAAABBBBCCCC-비밀";
            var 원인 = new FeignException.InternalServerError(
                    민감한내용, 더미요청(), 민감한내용.getBytes(StandardCharsets.UTF_8), Map.of());

            assertThatThrownBy(() -> 감싼다(원인).call())
                    .isInstanceOf(UpstreamCallException.class)
                    .satisfies(e -> assertThat(((UpstreamCallException) e).getReason())
                            .doesNotContain("descriptor")
                            .doesNotContain("비밀"));
        }

        /** {@code reason} 형식을 통째로 고정한다 — 초판은 contains 만 봐서 원인 클래스명이 빠져도 통과했다. */
        @Test
        @DisplayName("reason 이 종류와 원인 클래스명을 모두 담는다")
        void reason_형식() {
            var 원인 = new RetryableException(
                    -1, "refused", Request.HttpMethod.POST,
                    new IOException("refused"), (Long) null, 더미요청());

            assertThatThrownBy(() -> 감싼다(원인).call())
                    .satisfies(e -> assertThat(((UpstreamCallException) e).getReason())
                            .isEqualTo("연결 실패·타임아웃 (IOException)"));
        }

        /**
         * 연결 실패에는 cause 를 싣지 않는다.
         *
         * <p>핸들러가 cause 를 slf4j 마지막 인자로 넘겨 그때만 스택트레이스가 붙는다. 연결
         * 거부의 스택은 매번 같은 Feign 경로라 정보가 없다. 초판은 무조건 실어서 프레임이
         * 213 → 223 으로 오히려 늘었다(반박 리뷰 실측).
         */
        @Test
        @DisplayName("연결 실패는 cause 를 싣지 않는다 — 스택트레이스에 정보가 없다")
        void 연결실패는_cause_없음() {
            var 원인 = new RetryableException(
                    -1, "refused", Request.HttpMethod.POST,
                    new IOException("refused"), (Long) null, 더미요청());

            assertThatThrownBy(() -> 감싼다(원인).call())
                    .satisfies(e -> assertThat(e.getCause()).isNull());
        }

        /** 디코딩 실패는 우리 쪽 파싱 문제일 수 있어 스택이 단서가 된다. */
        @Test
        @DisplayName("디코딩 실패는 cause 를 싣는다")
        void 디코딩실패는_cause_있음() {
            var 원인 = new FeignException.InternalServerError(
                    "broken", 더미요청(), new byte[0], Map.of());

            assertThatThrownBy(() -> 감싼다(원인).call())
                    .satisfies(e -> assertThat(e.getCause()).isSameAs(원인));
        }

        /**
         * 원본이 구현하던 인터페이스가 프록시에도 전부 남는다.
         *
         * <p>대상 하나만 넘기면 Feign 이 붙였을 수 있는 다른 인터페이스가 소리 없이 사라진다.
         */
        @Test
        @DisplayName("원본의 인터페이스를 전부 유지한다")
        void 인터페이스를_잃지_않는다() {
            class 둘다 implements ProbeFeign, PlainBean {
                @Override public String call() { return "ok"; }
                @Override public void run() { }
            }

            Object wrapped = translator.postProcessAfterInitialization(new 둘다(), "probe");

            assertThat(wrapped).isInstanceOf(ProbeFeign.class).isInstanceOf(PlainBean.class);
        }

        /** {@code Object.equals} 반사성. 위임하면 Feign 의 equals 구현 때문에 자기 자신과도 다르다. */
        @Test
        @DisplayName("감싼 빈이 자기 자신과 같다")
        void equals_반사성() {
            ProbeFeign raw = new ProbeFeign() {
                @Override public String call() { return "ok"; }
                @Override public void run() { }
            };
            Object wrapped = translator.postProcessAfterInitialization(raw, "probe");

            assertThat(wrapped.equals(wrapped)).isTrue();
        }

    }
}
