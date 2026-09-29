package ai.univs.gate.support.webhook;

import ai.univs.gate.shared.exception.CustomGateException;
import ai.univs.gate.shared.web.enums.ErrorType;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * 웹훅을 보내도 되는 주소인지 판정한다 (UG-111).
 *
 * <p>웹훅 URL 은 고객이 넣는 값이고, 요청은 gate 서버가 보낸다. 제한이 없으면 누구든 가입해서
 * {@code http://gate-postgres:5432} 나 {@code http://config-server:8888/actuator/...} 같은
 * 내부 주소를 넣어 우리 내부망에 POST 를 보내게 할 수 있다(SSRF).
 *
 * <p>두 곳에서 쓴다.
 * <ul>
 *   <li><b>저장할 때</b> ({@link #validate}) — 잘못된 주소를 화면에서 바로 알려 준다.
 *   <li><b>보낼 때</b> — 저장 뒤에 DNS 가 내부 주소로 바뀔 수 있다(DNS 리바인딩). 그래서
 *       {@link WebhookService} 는 보내기 직전에 다시 확인하고, 실제 연결에 쓰는 주소도
 *       {@link PolicyAddressResolverGroup} 가 이 정책으로 거른다.
 * </ul>
 *
 * <p>항상 막는 것: 루프백, 링크 로컬(클라우드 메타데이터 169.254.169.254 포함), 미지정 주소,
 * 멀티캐스트. 사설망(10/8, 172.16/12, 192.168/16, 100.64/10, fc00::/7)은
 * {@code gate.webhook.allow-private-targets=true} 일 때만 허용한다 — 온프레미스용.
 */
@Component
public class WebhookTargetPolicy {

    private final boolean allowPrivateTargets;

    public WebhookTargetPolicy(WebhookProperties properties) {
        this.allowPrivateTargets = properties.allowPrivateTargets();
    }

    /**
     * 저장·전송 전에 URL 을 검사하고 파싱한 결과를 돌려준다.
     *
     * @throws CustomGateException {@link ErrorType#WEBHOOK_URL_NOT_ALLOWED} — 형식이 틀렸거나,
     *         http(s) 가 아니거나, 호스트를 찾을 수 없거나, 허용하지 않는 주소로 풀릴 때
     */
    public URI validate(String rawUrl) {
        URI uri = parse(rawUrl);
        try {
            resolveAllowed(uri.getHost());
        } catch (UnknownHostException e) {   // TargetNotAllowedException 도 여기로 온다
            throw new CustomGateException(ErrorType.WEBHOOK_URL_NOT_ALLOWED);
        }
        return uri;
    }

    /**
     * 호스트를 풀어서 <b>모든</b> 주소가 허용될 때만 돌려준다.
     *
     * <p>하나라도 막힌 주소가 섞이면 거절한다. 공인 주소 하나와 내부 주소 하나를 함께 돌려주는
     * 레코드로 우회하지 못하게 하기 위해서다.
     */
    List<InetAddress> resolveAllowed(String host) throws UnknownHostException {
        List<InetAddress> addresses = List.of(InetAddress.getAllByName(host));
        for (InetAddress address : addresses) {
            if (!isAllowed(address)) {
                throw new TargetNotAllowedException(host);
            }
        }
        return addresses;
    }

    boolean isAllowed(InetAddress address) {
        if (address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isMulticastAddress()
                || isUnspecifiedOrBroadcast(address)) {
            return false;
        }
        return allowPrivateTargets || !isPrivate(address);
    }

    private static URI parse(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            throw new CustomGateException(ErrorType.WEBHOOK_URL_NOT_ALLOWED);
        }
        URI uri;
        try {
            uri = new URI(rawUrl.trim());
        } catch (URISyntaxException e) {
            throw new CustomGateException(ErrorType.WEBHOOK_URL_NOT_ALLOWED);
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https")) {
            throw new CustomGateException(ErrorType.WEBHOOK_URL_NOT_ALLOWED);
        }
        // getHost() 는 호스트 부분이 표준 문법이 아니면 null 이다 (예: 밑줄이 든 이름).
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw new CustomGateException(ErrorType.WEBHOOK_URL_NOT_ALLOWED);
        }
        return uri;
    }

    /** 0.0.0.0/8 과 255.255.255.255. JDK 판정 메서드가 0.0.0.0 외의 0/8 은 잡지 않는다. */
    private static boolean isUnspecifiedOrBroadcast(InetAddress address) {
        if (!(address instanceof Inet4Address)) return false;
        byte[] b = address.getAddress();
        return (b[0] & 0xff) == 0
                || ((b[0] & 0xff) == 255 && (b[1] & 0xff) == 255 && (b[2] & 0xff) == 255 && (b[3] & 0xff) == 255);
    }

    private static boolean isPrivate(InetAddress address) {
        if (address.isSiteLocalAddress()) return true;   // 10/8, 172.16/12, 192.168/16, fec0::/10
        byte[] b = address.getAddress();
        if (address instanceof Inet4Address) {
            // 100.64.0.0/10 — 통신사 NAT. 컨테이너·VPN 환경에서 내부 주소로 쓰인다.
            return (b[0] & 0xff) == 100 && (b[1] & 0xc0) == 64;
        }
        if (address instanceof Inet6Address) {
            return (b[0] & 0xfe) == 0xfc;                // fc00::/7 — IPv6 사설(ULA)
        }
        return false;
    }

    /** 허용하지 않는 주소로 풀렸다. 전송 경로(리졸버)에서 연결 실패로 올라간다. */
    static final class TargetNotAllowedException extends UnknownHostException {
        TargetNotAllowedException(String host) {
            super("webhook target not allowed: " + host);
        }
    }
}
