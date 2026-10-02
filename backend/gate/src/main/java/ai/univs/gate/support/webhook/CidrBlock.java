package ai.univs.gate.support.webhook;

import io.netty.util.NetUtil;
import java.net.InetAddress;

/**
 * 설정으로 받은 주소 대역 하나 (UG-348). {@code 203.0.113.0/24}, {@code 2001:db8::/32}, 접두 길이 없는
 * 단일 주소({@code 203.0.113.7} = /32, IPv6 는 /128)를 받는다.
 *
 * <p><b>잘못된 값은 기동 실패다.</b> 차단 목록은 보안 설정이라, 오타 하나를 조용히 건너뛰면 막았다고 믿는
 * 주소가 열린 채 남는다. 호스트 이름도 받지 않는다 — 기동할 때 DNS 를 타면 그 순간의 주소만 막힌다.
 * 접두 길이 뒤에 0 이 아닌 비트가 있으면(예: {@code 203.0.113.7/2}) 의도와 다른 넓은 대역이 막히므로 거절한다.
 */
final class CidrBlock {

    private final byte[] network;
    private final int prefixLength;

    private CidrBlock(byte[] network, int prefixLength) {
        this.network = network;
        this.prefixLength = prefixLength;
    }

    static CidrBlock parse(String raw) {
        String value = raw.trim();
        int slash = value.indexOf('/');
        String ip = slash < 0 ? value : value.substring(0, slash);
        if (ip.startsWith("[") && ip.endsWith("]")) {
            ip = ip.substring(1, ip.length() - 1);
        }
        if (!NetUtil.isValidIpV4Address(ip) && !NetUtil.isValidIpV6Address(ip)) {
            throw invalid(raw, "IP 주소가 아니다");
        }
        byte[] address = NetUtil.createByteArrayFromIpAddressString(ip);
        int bits = address.length * 8;
        int prefix = bits;
        if (slash >= 0) {
            String length = value.substring(slash + 1);
            if (!length.matches("\\d{1,3}")) {
                throw invalid(raw, "접두 길이가 숫자가 아니다");
            }
            prefix = Integer.parseInt(length);
            if (prefix > bits) {
                throw invalid(raw, "접두 길이가 " + bits + " 보다 크다");
            }
        }
        for (int i = prefix; i < bits; i++) {
            if (bit(address, i)) {
                throw invalid(raw, "접두 길이 뒤에 0 이 아닌 비트가 있다 — 네트워크 주소로 적는다");
            }
        }
        return new CidrBlock(address, prefix);
    }

    /** IPv4 대역은 IPv4 주소만, IPv6 대역은 IPv6 주소만 맞춘다. IPv6 안에 든 IPv4 는 호출하는 쪽이 풀어 다시 묻는다. */
    boolean contains(InetAddress address) {
        byte[] candidate = address.getAddress();
        if (candidate.length != network.length) {
            return false;
        }
        for (int i = 0; i < prefixLength; i++) {
            if (bit(candidate, i) != bit(network, i)) {
                return false;
            }
        }
        return true;
    }

    private static boolean bit(byte[] bytes, int index) {
        return (bytes[index / 8] & (0x80 >>> (index % 8))) != 0;
    }

    private static IllegalArgumentException invalid(String raw, String reason) {
        return new IllegalArgumentException("gate.webhook.denied-cidrs 값이 잘못됐다: '" + raw + "' (" + reason + ")");
    }
}
