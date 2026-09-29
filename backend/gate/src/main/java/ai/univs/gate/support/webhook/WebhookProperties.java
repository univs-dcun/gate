package ai.univs.gate.support.webhook;

import java.time.Duration;
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
 * <p>나머지 값은 수신 서버 하나가 느리거나 죽어도 다른 프로젝트의 전송과 gate 요청 처리가
 * 영향을 받지 않게 하는 상한이다.
 */
@ConfigurationProperties(prefix = "gate.webhook")
public record WebhookProperties(
        @DefaultValue("false") boolean allowPrivateTargets,
        @DefaultValue("3s") Duration connectTimeout,
        @DefaultValue("5s") Duration responseTimeout,
        /** 첫 시도를 포함한 총 시도 횟수. 1 이면 재시도하지 않는다. */
        @DefaultValue("3") int maxAttempts,
        /** 재시도 간격의 시작값. 시도마다 지수로 늘어난다(지터 포함). */
        @DefaultValue("1s") Duration retryBackoff,
        /** 동시에 열어 둘 연결 수 상한. 넘으면 대기열에서 기다린다. */
        @DefaultValue("50") int maxConnections,
        /** 연결을 기다리는 전송 수 상한. 넘으면 그 전송은 버린다(로그만 남긴다). */
        @DefaultValue("500") int maxPending,
        /** 설정 조회·페이로드 조립을 기다리는 작업 수 상한. 넘으면 버린다. */
        @DefaultValue("1000") int queueCapacity) {
}
