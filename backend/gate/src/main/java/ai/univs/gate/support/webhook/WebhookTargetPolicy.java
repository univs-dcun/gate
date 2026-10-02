package ai.univs.gate.support.webhook;

import ai.univs.gate.shared.exception.CustomGateException;
import ai.univs.gate.shared.web.enums.ErrorType;
import io.netty.util.NetUtil;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 웹훅을 보내도 되는 주소인지 판정한다 (UG-111).
 *
 * <p>웹훅 URL 은 고객이 넣는 값이고, 요청은 gate 서버가 보낸다. 제한이 없으면 누구든 가입해서
 * {@code http://gate-postgres:5432} 나 {@code http://config-server:8888/actuator/...} 같은
 * 내부 주소를 넣어 우리 내부망에 POST 를 보내게 할 수 있다(SSRF).
 *
 * <p>세 곳에서 쓴다.
 * <ul>
 *   <li><b>저장할 때</b> ({@link #validate}) — 호스트 이름까지 풀어 본다. 잘못된 주소를 화면에서
 *       바로 알려 주기 위해서다.
 *   <li><b>보내기 직전</b> ({@link #checkWithoutLookup}) — 형식과 IP 리터럴만 본다. DNS 를
 *       조회하지 않는다 — 응답하지 않는 네임서버 하나가 전송 스레드를 붙잡으면 모든 프로젝트의
 *       웹훅이 밀린다(반박 리뷰 W2).
 *   <li><b>연결할 때</b> ({@link PolicyAddressResolverGroup}) — netty 비동기 DNS 가 푼 주소를
 *       거른다. 저장 뒤 DNS 가 내부 주소로 바뀌는 경우(DNS 리바인딩)가 여기서 막힌다.
 * </ul>
 *
 * <p><b>IP 리터럴은 두 파서로 읽는다</b> (반박 리뷰 B1). JDK 는 {@code [::127.0.0.1]} 을 IPv4
 * 호환 IPv6 주소 {@code ::7f00:1} 로 읽어 루프백이 아니라고 판정하는데, netty 는 같은 문자열을
 * {@code ::ffff:127.0.0.1} 로 바꿔 루프백에 연결한다. 실제로 연결에 쓰는 쪽(netty)의 해석을
 * 함께 검사하고, IPv6 안에 IPv4 가 든 대역은 안의 IPv4 로 다시 판정한다.
 *
 * <p>항상 막는 것: 루프백, 링크 로컬(클라우드 메타데이터 169.254.169.254 포함), 미지정 주소,
 * 멀티캐스트, 0/8, 240/4(예약·브로드캐스트). 사설망(10/8, 172.16/12, 192.168/16, 100.64/10,
 * 198.18/15, fc00::/7, fec0::/10, 64:ff9b:1::/48)은 {@code gate.webhook.allow-private-targets=true} 일 때만
 * 허용한다 — 온프레미스용.
 */
@Component
public class WebhookTargetPolicy {

    /** 호스트 이름 → 주소. 테스트가 섞인 레코드를 흉내 내려고 바꾼다. */
    interface Lookup {
        InetAddress[] resolve(String host) throws UnknownHostException;
    }

    private final boolean allowPrivateTargets;
    private final Lookup lookup;

    /**
     * Spring 이 쓰는 생성자. 생성자가 둘이라 지정하지 않으면 빈을 만들지 못하고 gate-service 기동이 실패한다
     * (UG-111 dev 배포 실패 — 단위 테스트는 직접 생성해 못 잡았다. {@code WebhookBeanWiringTest} 가 지킨다).
     */
    @Autowired
    public WebhookTargetPolicy(WebhookProperties properties) {
        this(properties, InetAddress::getAllByName);
    }

    WebhookTargetPolicy(WebhookProperties properties, Lookup lookup) {
        this.allowPrivateTargets = properties.allowPrivateTargets();
        this.lookup = lookup;
    }

    /**
     * 저장 전에 URL 을 검사한다. 호스트 이름이면 DNS 로 풀어 <b>모든</b> 주소를 본다.
     *
     * @throws CustomGateException {@link ErrorType#WEBHOOK_URL_NOT_ALLOWED} — 형식이 틀렸거나,
     *         http(s) 가 아니거나, 호스트를 찾을 수 없거나, 허용하지 않는 주소로 풀릴 때
     */
    public URI validate(String rawUrl) {
        URI uri = checkWithoutLookup(rawUrl);
        String host = hostOf(uri);
        if (isIpLiteral(host)) return uri;   // checkWithoutLookup 이 이미 봤다
        try {
            InetAddress[] addresses = lookup.resolve(host);
            if (addresses.length == 0) {
                throw new CustomGateException(ErrorType.WEBHOOK_URL_NOT_ALLOWED, MESSAGE_KEY_HOST_NOT_FOUND);
            }
            if (!Arrays.stream(addresses).allMatch(this::isAllowed)) {
                throw rejected();
            }
        } catch (UnknownHostException e) {
            // 주소를 찾지 못한 것은 「내부 주소」와 원인이 다르다 — 같은 문구면 오타 난 도메인을 넣은 사람이 내부망 주소를
            // 넣은 줄로 읽는다 (UG-344 dev 확인 2026-10-02). 코드는 PJ-111 그대로, 문구만 나눈다. JDK 는 조회 시간 초과·
            // SERVFAIL 도 같은 예외로 주므로 문구에 「잠시 후 다시 시도」를 함께 둔다 (반박 리뷰 W1).
            throw new CustomGateException(ErrorType.WEBHOOK_URL_NOT_ALLOWED, MESSAGE_KEY_HOST_NOT_FOUND);
        }
        return uri;
    }

    /**
     * DNS 를 조회하지 않고 볼 수 있는 것만 본다 — 스킴, 형식, IP 리터럴.
     * 호스트 이름의 주소는 연결 단계({@link PolicyAddressResolverGroup})가 거른다.
     */
    URI checkWithoutLookup(String rawUrl) {
        URI uri = parse(rawUrl);
        String host = hostOf(uri);
        if (isIpLiteral(host) && !isLiteralAllowed(host)) {
            throw rejected();
        }
        return uri;
    }

    boolean isAllowed(InetAddress address) {
        InetAddress embedded = embeddedIpv4(address);
        if (embedded != null) return isAllowed(embedded);
        if (address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isMulticastAddress()
                || isReservedIpv4(address)) {
            return false;
        }
        return allowPrivateTargets || !isPrivate(address);
    }

    /** JDK 와 netty 가 같은 리터럴을 다르게 읽을 수 있다. 둘 다 허용될 때만 통과시킨다. */
    private boolean isLiteralAllowed(String host) {
        try {
            InetAddress byJdk = InetAddress.getByName(host);   // 리터럴이라 DNS 를 타지 않는다
            InetAddress byNetty = NetUtil.createInetAddressFromIpAddressString(host);
            return isAllowed(byJdk) && byNetty != null && isAllowed(byNetty);
        } catch (UnknownHostException e) {
            return false;
        }
    }

    /**
     * netty 가 IP 리터럴로 보는가. 그렇다면 reactor-netty 는 DNS 리졸버를 거치지 않고 바로 연결하므로
     * {@link PolicyAddressResolverGroup} 가 볼 기회가 없다 — 여기서 막아야 한다.
     *
     * <p>{@code 2130706433} 같은 비표준 표기는 netty 에게 리터럴이 아니라 호스트 이름이다. 비동기 DNS 로
     * 조회되고(풀리지 않는다), 풀린다면 {@link PolicyAddressResolverGroup} 가 거른다. 저장할 때는 JDK 가 이 표기를
     * 127.0.0.1 로 읽어 {@link #validate} 에서 거절된다.
     */
    private static boolean isIpLiteral(String host) {
        return NetUtil.isValidIpV4Address(host) || NetUtil.isValidIpV6Address(host);
    }

    /** URI 의 IPv6 호스트는 대괄호가 붙어 온다. */
    private static String hostOf(URI uri) {
        String host = uri.getHost();
        return host.startsWith("[") && host.endsWith("]") ? host.substring(1, host.length() - 1) : host;
    }

    private URI parse(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            throw rejected();
        }
        URI uri;
        try {
            uri = new URI(rawUrl.trim());
        } catch (URISyntaxException e) {
            throw rejected();
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https")) {
            throw rejected();
        }
        // getHost() 는 호스트 부분이 표준 문법이 아니면 null 이다 (예: 밑줄이 든 이름).
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw rejected();
        }
        return uri;
    }

    /**
     * IPv6 안에 IPv4 가 든 대역이면 그 IPv4 를 돌려준다. 아니면 null.
     *
     * <p>::/96(IPv4 호환), ::ffff:0:0/96(IPv4 매핑 — JDK 는 보통 Inet4Address 로 바꾸지만 바이트로
     * 만든 주소는 아니다), ::ffff:0:0:0/96(SIIT), 64:ff9b::/96(NAT64), 2002::/16(6to4).
     * {@code ::} 와 {@code ::1} 은 IPv4 호환 형태지만 그 자체로 미지정·루프백이라 여기서 제외한다.
     */
    private static InetAddress embeddedIpv4(InetAddress address) {
        if (!(address instanceof Inet6Address)) return null;
        byte[] b = address.getAddress();
        int from;
        if (allZero(b, 0, 12)) {
            if (allZero(b, 12, 15) && (b[15] == 0 || b[15] == 1)) return null;   // :: , ::1
            from = 12;
        } else if (allZero(b, 0, 10) && (b[10] & 0xff) == 0xff && (b[11] & 0xff) == 0xff) {
            from = 12;
        } else if (allZero(b, 0, 8) && (b[8] & 0xff) == 0xff && (b[9] & 0xff) == 0xff && allZero(b, 10, 12)) {
            from = 12;
        } else if ((b[0] & 0xff) == 0x00 && (b[1] & 0xff) == 0x64 && (b[2] & 0xff) == 0xff && (b[3] & 0xff) == 0x9b
                && allZero(b, 4, 12)) {
            from = 12;
        } else if ((b[0] & 0xff) == 0x20 && (b[1] & 0xff) == 0x02) {
            from = 2;
        } else {
            return null;
        }
        try {
            return InetAddress.getByAddress(Arrays.copyOfRange(b, from, from + 4));
        } catch (UnknownHostException e) {
            return null;   // 길이 4 라 일어나지 않는다
        }
    }

    private static boolean allZero(byte[] b, int from, int to) {
        for (int i = from; i < to; i++) if (b[i] != 0) return false;
        return true;
    }

    /**
     * PJ-111. 사설망을 허용한 설치(온프레미스)에서는 "외부에서 접근할 수 있는 주소" 대신 "루프백·링크 로컬은
     * 안 된다, 사내망 주소로 등록하라" 로 안내한다 — 온프레미스 고객은 사설망 주소를 넣으라는 안내를
     * 받았으므로 기본 문구가 정반대로 읽힌다 (onprem 3.0.7 검증에서 발견). 코드·type 은 같다.
     */
    private CustomGateException rejected() {
        return allowPrivateTargets
                ? new CustomGateException(ErrorType.WEBHOOK_URL_NOT_ALLOWED, MESSAGE_KEY_PRIVATE_ALLOWED)
                : new CustomGateException(ErrorType.WEBHOOK_URL_NOT_ALLOWED);
    }

    static final String MESSAGE_KEY_PRIVATE_ALLOWED = "WEBHOOK_URL_NOT_ALLOWED_PRIVATE_ALLOWED";
    /** 호스트 이름을 DNS 에서 찾지 못했다 (존재하지 않는 도메인, 또는 조회 실패). 설치와 무관하게 같은 안내다. */
    static final String MESSAGE_KEY_HOST_NOT_FOUND = "WEBHOOK_URL_HOST_NOT_FOUND";

    /** 0.0.0.0/8 과 240.0.0.0/4(예약, 255.255.255.255 포함). JDK 판정 메서드가 잡지 않는다. */
    private static boolean isReservedIpv4(InetAddress address) {
        if (!(address instanceof Inet4Address)) return false;
        int first = address.getAddress()[0] & 0xff;
        return first == 0 || first >= 240;
    }

    private static boolean isPrivate(InetAddress address) {
        if (address.isSiteLocalAddress()) return true;   // 10/8, 172.16/12, 192.168/16, fec0::/10
        byte[] b = address.getAddress();
        if (address instanceof Inet4Address) {
            int b0 = b[0] & 0xff, b1 = b[1] & 0xff;
            return (b0 == 100 && (b1 & 0xc0) == 64)      // 100.64.0.0/10 — 통신사 NAT, 컨테이너·VPN 내부
                    || (b0 == 198 && (b1 & 0xfe) == 18); // 198.18.0.0/15 — 벤치마크 대역, 내부망에 쓰인다
        }
        return (b[0] & 0xfe) == 0xfc                      // fc00::/7 — IPv6 사설(ULA)
                // 64:ff9b:1::/48 — 로컬 NAT64(RFC 8215). IPv4 가 든 위치가 접두 길이마다 달라 풀지 않고 사설로 본다
                || ((b[0] & 0xff) == 0x00 && (b[1] & 0xff) == 0x64 && (b[2] & 0xff) == 0xff && (b[3] & 0xff) == 0x9b
                        && (b[4] & 0xff) == 0x00 && (b[5] & 0xff) == 0x01);
    }

    /** 허용하지 않는 주소로 풀렸다. 연결 실패로 올라간다. */
    static final class TargetNotAllowedException extends UnknownHostException {
        TargetNotAllowedException(String host) {
            super("webhook target not allowed: " + host);
        }
    }
}
