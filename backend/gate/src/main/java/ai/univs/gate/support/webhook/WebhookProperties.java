package ai.univs.gate.support.webhook;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 웹훅 전송 설정 (UG-111).
 *
 * <p><b>{@code allowPrivateTargets} 는 기본 꺼짐이다.</b> 클라우드는 누구나 가입해 URL 을 넣을 수
 * 있으므로, 사설망 주소를 허용하면 gate 가 우리 내부 서비스(DB·config·discovery 등)로 POST 를
 * 보내는 통로가 된다(SSRF). 온프레미스는 고객사 내부망의 수신 서버가 정상 대상이라 켜야 한다 —
 * compose 환경변수 {@code GATE_WEBHOOK_ALLOW_PRIVATE_TARGETS=true}. 켜도 루프백·링크 로컬·
 * 멀티캐스트는 막는다 ({@link WebhookTargetPolicy}).
 *
 * <p><b>공용 {@code gate-service.yml} 에 넣지 않는다.</b> onprem 저장소가 공용 파일을 통째로
 * 복사하므로, 거기 {@code true} 가 들어가면 클라우드까지 열린다. 배포 쪽이 환경변수로 준다.
 *
 * <p><b>{@code deniedCidrs} 는 설정한 대역을 항상 막는다</b> (UG-348). 사설망 허용 여부와 상관없다. 클라우드에서는
 * gate 서버 자신의 공인 주소를 넣는다 — 공인 주소라 기본 정책을 통과하므로, 콘솔 「테스트 전송」이 돌려주는 응답
 * 코드·시간으로 우리 서버의 포트를 탐색할 수 있다. 값은 환경별 설정(gate-config 의 환경 파일, 온프레미스는
 * {@code GATE_WEBHOOK_DENIED_CIDRS})에만 둔다. 기본은 비어 있다.
 *
 * <p>나머지 값은 수신 서버 하나가 느리거나 죽어도 다른 프로젝트의 전송과 gate 요청 처리가
 * 영향을 받지 않게 하는 상한이다.
 */
@ConfigurationProperties(prefix = "gate.webhook")
public record WebhookProperties(
        @DefaultValue("false") boolean allowPrivateTargets,
        @DefaultValue("3s") Duration connectTimeout,
        /** 읽기 사이의 최대 공백. 시도 한 번의 전체 상한은 connectTimeout + responseTimeout + 1초다. */
        @DefaultValue("5s") Duration responseTimeout,
        /** 첫 시도를 포함한 총 시도 횟수. 1 이면 재시도하지 않는다. */
        @DefaultValue("3") int maxAttempts,
        /** 재시도 간격의 시작값. 시도마다 지수로 늘어난다(지터 포함). */
        @DefaultValue("1s") Duration retryBackoff,
        /** 수신 주소 하나당 동시에 열어 둘 연결 수 상한 (reactor-netty 풀은 원격 주소별이다). */
        @DefaultValue("50") int maxConnections,
        /** 수신 주소 하나당 연결을 기다리는 전송 수 상한. 넘으면 그 전송은 버린다(로그만 남긴다). */
        @DefaultValue("500") int maxPending,
        /** 설정 조회·페이로드 조립을 기다리는 작업 수 상한. 넘으면 버린다. */
        @DefaultValue("1000") int queueCapacity,
        /** 항상 막을 대역. {@code 203.0.113.0/24}, {@code 2001:db8::/32}, 단일 주소. 잘못된 값은 기동 실패다 ({@link CidrBlock}). */
        @DefaultValue List<String> deniedCidrs) {
}
