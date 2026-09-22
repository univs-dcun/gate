package ai.univs.gate.modules.feature.domain.entity;

import static org.assertj.core.api.Assertions.assertThat;

import ai.univs.gate.shared.exception.RemoteCallException;
import ai.univs.gate.shared.web.enums.ErrorType;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * 하위 서비스 실패의 원인이 이력에 남는가 (UG-294).
 *
 * <p>UG-280 이후 face-service·match-server 실패도 이력에 기록된다. 그런데 기록되는 값이
 * 하나뿐이었다 — {@link RemoteCallException#getErrorType()} 이 항상
 * {@link ErrorType#INTERNAL_SERVER_ERROR} 라, 아래가 전부 같은 {@code failure_type} 으로 남았다.
 *
 * <ul>
 *   <li>하위 서비스가 502 / 503 / 504 를 응답
 *   <li>연결 거부 · 읽기 타임아웃 (응답 없음)
 *   <li>본문 디코딩 실패
 *   <li>HTTP 200 인데 envelope 의 {@code data} 가 빈 경우
 * </ul>
 *
 * <p>"장애를 가장 관측해야 할 때 기록이 남게 한다" 가 UG-280 의 문제의식인데, 남은 기록으로
 * 원인을 구분할 수 없었다. 예외 객체는 상태 코드를 이미 들고 있었고 로그에만 쓰였다.
 *
 * <p><b>왜 {@code failure_type} 을 세분화하지 않았는가.</b> 그 값은 클라이언트 응답에 나간다 —
 * {@code IdentifyResponseDTO} 등 5개 DTO 의 필드이고 {@code MessageService} 가 i18n 메시지 키로
 * 쓴다. 새 값을 만들면 고객이 보는 값이 늘고 대응 메시지가 없어 키가 그대로 노출된다. 그래서
 * 컬럼을 따로 뒀다. 이 테스트가 그 경계를 지킨다 — 기존 값은 그대로, 새 정보는 새 컬럼에.
 */
@DisplayName("UG-294: 하위 서비스 실패 상태 코드 기록")
class HistoryUpstreamStatusTest {

    @Nested
    @DisplayName("MatchHistory")
    class 인증_이력 {

        @Test
        @DisplayName("응답 코드가 있는 실패는 그 코드를 남긴다")
        void 상태코드를_남긴다() {
            MatchHistory history = MatchHistory.builder().build();
            RemoteCallException e = new RemoteCallException(503, "face.identify", null);

            history.failUpstream(e.getErrorType().name(), e.getUpstreamStatus());

            assertThat(history.getUpstreamStatus())
                    .as("이게 없으면 502·타임아웃·디코딩 실패가 전부 같은 기록으로 남는다")
                    .isEqualTo(503);
        }

        @Test
        @DisplayName("응답을 받지 못한 실패는 0 으로 남는다 — null 과 구분된다")
        void 무응답은_0이다() {
            MatchHistory history = MatchHistory.builder().build();
            RemoteCallException e = new RemoteCallException(RemoteCallException.NO_RESPONSE);

            history.failUpstream(e.getErrorType().name(), e.getUpstreamStatus());

            assertThat(history.getUpstreamStatus())
                    .as("0 은 '응답 없음', null 은 '하위 서비스 실패가 아님' 이다. "
                            + "둘을 같게 만들면 이 컬럼의 의미가 사라진다")
                    .isEqualTo(0);
        }

        @Test
        @DisplayName("failure_type 은 기존과 같은 값이다 — 응답 계약 불변")
        void 실패유형은_그대로다() {
            MatchHistory history = MatchHistory.builder().build();

            history.failUpstream(ErrorType.INTERNAL_SERVER_ERROR.name(), 502);

            assertThat(history.getFailureType())
                    .as("이 값이 바뀌면 고객 응답과 i18n 메시지 키가 함께 바뀐다 — "
                            + "컬럼을 따로 둔 이유가 사라진다")
                    .isEqualTo(ErrorType.INTERNAL_SERVER_ERROR.name());
        }

        @Test
        @DisplayName("하위 서비스 실패가 아닌 실패는 상태 코드를 남기지 않는다")
        void 우리쪽_실패는_null이다() {
            MatchHistory history = MatchHistory.builder().build();

            history.fail(BigDecimal.ZERO, ErrorType.NOT_MATCH.name());

            assertThat(history.getUpstreamStatus())
                    .as("일치하는 사용자가 없는 것은 하위 서비스 장애가 아니다. "
                            + "여기에 0 이 들어가면 장애 집계가 오염된다")
                    .isNull();
        }
    }

    @Nested
    @DisplayName("FeatureHistory")
    class 특징점_이력 {

        @Test
        @DisplayName("삭제 실패도 상태 코드를 남긴다")
        void 상태코드를_남긴다() {
            FeatureHistory history = FeatureHistory.builder().build();

            history.failUpstream(ErrorType.INTERNAL_SERVER_ERROR.name(), 504);

            assertThat(history.getUpstreamStatus()).isEqualTo(504);
            assertThat(history.getFailureType()).isEqualTo(ErrorType.INTERNAL_SERVER_ERROR.name());
        }

        @Test
        @DisplayName("하위 서비스 실패가 아닌 실패는 상태 코드를 남기지 않는다")
        void 우리쪽_실패는_null이다() {
            FeatureHistory history = FeatureHistory.builder().build();

            history.fail(ErrorType.NOT_FOUND.name());

            assertThat(history.getUpstreamStatus()).isNull();
        }
    }

    /**
     * 하위 서비스 실패를 잡는 자리가 새 메서드를 쓰는가.
     *
     * <p>위 단위 테스트는 엔티티가 값을 담는다는 것만 본다. 실제로 <b>호출처가 그것을 부르는지</b>
     * 는 보지 못한다. {@code catch (RemoteCallException e)} 안에서 옛 {@code fail(...)} 을 그대로
     * 쓰면 컬럼은 영원히 {@code null} 이고 아무 테스트도 깨지지 않는다.
     *
     * <p>그래서 소스를 훑는다. 이 저장소의 다른 가드들과 같은 방식이다
     * ({@code ApiKeyOwnershipGuardTest}, {@code RemoteCallRollbackGuardTest}).
     */
    @Nested
    @DisplayName("호출처")
    class 호출처_가드 {

        private static final Path SOURCE_ROOT = Path.of("src/main/java");

        @Test
        @DisplayName("RemoteCallException 을 잡는 자리는 failUpstream 을 쓴다")
        void 캐치블록은_failUpstream을_쓴다() throws IOException {
            List<String> violations;
            List<Path> scanned;
            try (Stream<Path> files = Files.walk(SOURCE_ROOT)) {
                scanned = files.filter(p -> p.toString().endsWith(".java")).sorted().toList();
            }

            assertThat(scanned)
                    .as("소스 트리를 못 훑었다 — 작업 디렉터리가 gate 모듈 루트인지 확인할 것")
                    .hasSizeGreaterThan(100);

            violations = scanned.stream()
                    .filter(HistoryUpstreamStatusTest::catchesRemoteCallWithPlainFail)
                    .map(p -> SOURCE_ROOT.relativize(p).toString())
                    .toList();

            assertThat(violations)
                    .as("catch (RemoteCallException e) 바로 다음 줄에서 옛 fail(...) 을 쓰면 "
                            + "upstream_status 가 비어 UG-294 가 무효가 된다. "
                            + "failUpstream(..., e.getUpstreamStatus()) 를 쓸 것")
                    .isEmpty();
        }

        @Test
        @DisplayName("실제로 failUpstream 을 쓰는 곳이 있다 — 스캔이 공회전하지 않는다")
        void 스캔이_공회전하지_않는다() throws IOException {
            long count;
            try (Stream<Path> files = Files.walk(SOURCE_ROOT)) {
                count = files.filter(p -> p.toString().endsWith(".java"))
                        .filter(p -> read(p).contains("failUpstream("))
                        .count();
            }
            assertThat(count)
                    .as("한 곳도 안 쓰면 위 가드는 '위반 0건' 으로 영원히 통과한다")
                    .isGreaterThanOrEqualTo(10);
        }
    }

    /**
     * {@code catch (RemoteCallException e)} 블록 <b>안에서만</b> 옛 {@code fail(...)} 을 쓰는지.
     *
     * <p>초판은 catch 문자열 뒤 400자를 잘라 봤는데, 정상 흐름의
     * {@code matchHistory.fail(similarity, MISMATCH)} 까지 삼켜 다섯 파일이 거짓 양성으로
     * 걸렸다. 블록의 끝은 중괄호로 정확히 찾는다.
     */
    private static boolean catchesRemoteCallWithPlainFail(Path file) {
        String source = read(file);
        int from = 0;
        while (true) {
            int at = source.indexOf("catch (RemoteCallException e)", from);
            if (at < 0) {
                return false;
            }
            String block = blockAfter(source, at);
            if (block.contains("History.fail(") || block.contains("history.fail(")) {
                return true;
            }
            from = at + 1;
        }
    }

    /** {@code at} 이후 첫 {@code &#123;} 부터 짝이 맞는 {@code &#125;} 까지. */
    private static String blockAfter(String source, int at) {
        int open = source.indexOf('{', at);
        if (open < 0) {
            return "";
        }
        int depth = 0;
        for (int i = open; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return source.substring(open, i + 1);
                }
            }
        }
        return source.substring(open);
    }

    private static String read(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException e) {
            throw new IllegalStateException("소스를 읽을 수 없다: " + file, e);
        }
    }
}
