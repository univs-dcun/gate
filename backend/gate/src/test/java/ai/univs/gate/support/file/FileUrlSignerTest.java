package ai.univs.gate.support.file;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("사진 URL 서명 (UG-386)")
class FileUrlSignerTest {

    private static final String KEY = "k".repeat(FileUrlSigner.MIN_SECRET_LENGTH);
    private static final String OTHER_KEY = "o".repeat(FileUrlSigner.MIN_SECRET_LENGTH);
    private static final Instant NOW = Instant.parse("2026-10-15T01:00:00Z");
    private static final String PATH = "/face/20261015/abc.jpg";

    private static FileUrlSigner signer(String key, String previous, Instant now) {
        return new FileUrlSigner(key, previous, 600, Clock.fixed(now, ZoneOffset.UTC));
    }

    private static Map<String, String> params(String query) {
        Map<String, String> map = new LinkedHashMap<>();
        for (String pair : query.split("&")) {
            int eq = pair.indexOf('=');
            map.put(pair.substring(0, eq), URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8));
        }
        return map;
    }

    @Test
    @DisplayName("쿼리에 filePath · pid · exp(지금 + 10분) · sig 가 실린다")
    void 쿼리_형태() {
        Map<String, String> p = params(signer(KEY, "", NOW).query(PATH, 7L));

        assertThat(p).containsEntry("filePath", PATH).containsEntry("pid", "7")
                .containsEntry("exp", String.valueOf(NOW.getEpochSecond() + 600));
        assertThat(p.get("sig")).matches("[A-Za-z0-9_-]{43}");
    }

    @Test
    @DisplayName("만든 서명은 유효 시간 안에 검증된다")
    void 정상() {
        FileUrlSigner s = signer(KEY, "", NOW);
        Map<String, String> p = params(s.query(PATH, 7L));

        assertThat(s.verify(PATH, 7L, Long.parseLong(p.get("exp")), p.get("sig"))).isTrue();
    }

    @Test
    @DisplayName("경로 · 프로젝트 · 만료 시각 · 서명 중 하나라도 바꾸면 거절한다")
    void 변조() {
        FileUrlSigner s = signer(KEY, "", NOW);
        Map<String, String> p = params(s.query(PATH, 7L));
        long exp = Long.parseLong(p.get("exp"));
        String sig = p.get("sig");

        assertThat(s.verify("/face/20261015/other.jpg", 7L, exp, sig)).as("다른 파일").isFalse();
        assertThat(s.verify(PATH, 8L, exp, sig)).as("다른 프로젝트 — 동의 검사 우회").isFalse();
        assertThat(s.verify(PATH, 7L, exp + 3600, sig)).as("만료 늘리기").isFalse();
        assertThat(s.verify(PATH, 7L, exp, sig.substring(1) + "A")).as("서명 변조").isFalse();
        assertThat(s.verify(PATH, 7L, exp, "")).as("빈 서명").isFalse();
        assertThat(s.verify(PATH, 7L, exp, null)).as("서명 없음").isFalse();
    }

    @Test
    @DisplayName("만료 시각이 지나면 거절한다")
    void 만료() {
        Map<String, String> p = params(signer(KEY, "", NOW).query(PATH, 7L));
        FileUrlSigner later = signer(KEY, "", NOW.plusSeconds(601));

        assertThat(later.verify(PATH, 7L, Long.parseLong(p.get("exp")), p.get("sig"))).isFalse();
    }

    @Test
    @DisplayName("키를 바꿔도 이전 키로 만든 URL 은 previous-secret 로 받는다 — 모르는 키는 거절")
    void 키_회전() {
        Map<String, String> issuedWithOld = params(signer(OTHER_KEY, "", NOW).query(PATH, 7L));
        long exp = Long.parseLong(issuedWithOld.get("exp"));

        assertThat(signer(KEY, OTHER_KEY, NOW).verify(PATH, 7L, exp, issuedWithOld.get("sig"))).isTrue();
        assertThat(signer(KEY, "", NOW).verify(PATH, 7L, exp, issuedWithOld.get("sig"))).isFalse();
    }

    @Test
    @DisplayName("비밀키가 없거나 짧으면 기동하지 않는다 — 자리 표시자(secretkey)로 운영되지 않게")
    void 약한_키() {
        assertThatThrownBy(() -> signer("", "", NOW)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> signer("secretkey", "", NOW)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> signer(KEY, "short", NOW)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new FileUrlSigner(KEY, "", 0, Clock.systemUTC())).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("ImageUrls: 동의가 꺼져 있거나 경로가 비면 빈 값, 켜져 있으면 base + ? + 서명 쿼리")
    void imageUrls() {
        FileUrlSigner s = signer(KEY, "", NOW);

        assertThat(new ImageUrls("https://gw/api/v1/file", s, 7L, false).of(PATH)).isEmpty();
        assertThat(new ImageUrls("https://gw/api/v1/file", s, 7L, true).of("")).isEmpty();
        assertThat(new ImageUrls("https://gw/api/v1/file", s, 7L, true).of(null)).isEmpty();
        assertThat(new ImageUrls("https://gw/api/v1/file", s, 7L, true).of(PATH))
                .startsWith("https://gw/api/v1/file?filePath=%2Fface%2F20261015%2Fabc.jpg&pid=7&exp=").contains("&sig=");
    }

    @Test
    @DisplayName("경로는 인코딩해 싣는다 — 확장자에 & # + 가 섞여도 쿼리가 깨지지 않고, 디코딩한 값으로 검증된다")
    void 경로_인코딩() {
        FileUrlSigner s = signer(KEY, "", NOW);
        String odd = "/face/20261015/a b&c#d+e.jpg";

        Map<String, String> q = params(s.query(odd, 7L));
        assertThat(q).containsOnlyKeys("filePath", "pid", "exp", "sig");
        assertThat(q.get("filePath")).isEqualTo(odd);
        assertThat(s.verify(q.get("filePath"), 7L, Long.parseLong(q.get("exp")), q.get("sig"))).isTrue();
    }
}
