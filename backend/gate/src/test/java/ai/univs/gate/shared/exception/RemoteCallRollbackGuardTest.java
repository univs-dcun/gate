package ai.univs.gate.shared.exception;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * UG-280 재발 방지.
 *
 * <p>매칭 경로는 "먼저 이력 행을 저장하고, 하위 서비스를 호출하고, 결과로 행을 갱신한다" 는 모양을
 * 공유한다. 그 트랜잭션이 {@code REQUIRES_NEW} 인데 하위 서비스 실패 시 롤백되면 이력 행이
 * 사라진다 — 정상 동작에서는 드러나지 않고 <b>장애 때만</b> 드러나는 종류의 결함이다.
 *
 * <p>초기 버전은 {@code noRollbackFor} 가 이미 있는 줄만 훑었다. 반박 리뷰에서 그 방식으로는 가장
 * 흔한 재발 경로를 못 잡는다는 지적이 나왔다 — 새 UseCase 를 쓰는 사람은 선언을 복사한 뒤 절반을
 * 지우는 게 아니라 {@code noRollbackFor} 를 <b>아예 안 쓴다.</b> 그래서 지금은 {@code REQUIRES_NEW}
 * 를 기준으로 훑는다.
 *
 * <p>또한 애노테이션을 여러 줄로 나눠 쓰면 오탐이 나던 문제도 없앴다 (Google Java Style 포매터가
 * 열 제한에서 줄을 접는다). 이제 {@code @Transactional(...)} 괄호 블록 전체를 본다.
 *
 * <p>런타임 롤백 동작이 아니라 선언을 검사하는 이유는, 목 기반 단위 테스트로는 트랜잭션 롤백을
 * 확인할 수 없기 때문이다 ({@code @Transactional} 은 프록시가 적용하므로 목 테스트에서는 아예 돌지
 * 않는다). 각 UseCase 의 사유 기록은 별도 단위 테스트가 담당한다.
 */
@DisplayName("매칭 트랜잭션 롤백 가드 (UG-280)")
class RemoteCallRollbackGuardTest {

    private static final Path SOURCE_ROOT = Path.of("src/main/java");
    private static final String REQUIRED = "RemoteCallException";

    /** 엉뚱한 트리를 훑고 조용히 통과하는 것을 막는다. 현재 11곳. */
    private static final int MIN_SITES = 10;

    private static final Pattern TRANSACTIONAL = Pattern.compile("@Transactional\\s*\\(");

    private record Site(Path file, int line, String annotation, String fileText) {
        boolean isRequiresNew() {
            return annotation.contains("REQUIRES_NEW");
        }

        String describe() {
            return "%s:%d".formatted(file, line);
        }
    }

    /** {@code @Transactional(...)} 을 괄호 짝을 세어 통째로 잘라낸다 — 줄바꿈 위치와 무관하다. */
    private static List<Site> findTransactionalSites() throws IOException {
        List<Site> sites = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(SOURCE_ROOT)) {
            for (Path p : paths.filter(f -> f.toString().endsWith(".java")).toList()) {
                String text = Files.readString(p);
                Matcher m = TRANSACTIONAL.matcher(text);
                while (m.find()) {
                    int depth = 0;
                    int i = m.end() - 1;
                    for (; i < text.length(); i++) {
                        char c = text.charAt(i);
                        if (c == '(') {
                            depth++;
                        } else if (c == ')') {
                            depth--;
                            if (depth == 0) {
                                break;
                            }
                        }
                    }
                    String annotation = text.substring(m.start(), Math.min(i + 1, text.length()));
                    int line = (int) text.substring(0, m.start()).lines().count() + 1;
                    sites.add(new Site(p, line, annotation, text));
                }
            }
        }
        return sites;
    }

    @Nested
    @DisplayName("선언 검사")
    class Declarations {

        @Test
        @DisplayName("REQUIRES_NEW 트랜잭션은 모두 noRollbackFor 에 RemoteCallException 을 포함한다")
        void requiresNew_모두_원격실패를_포함한다() throws IOException {
            List<Site> requiresNew = findTransactionalSites().stream()
                    .filter(Site::isRequiresNew)
                    .toList();

            assertThat(requiresNew)
                    .as("REQUIRES_NEW 를 찾지 못했다면 SOURCE_ROOT(%s)가 잘못됐을 가능성이 크다", SOURCE_ROOT)
                    .hasSizeGreaterThanOrEqualTo(MIN_SITES);

            List<String> bad = requiresNew.stream()
                    .filter(s -> !s.annotation().contains(REQUIRED))
                    .map(Site::describe)
                    .toList();

            assertThat(bad)
                    .as("""
                            REQUIRES_NEW 인데 noRollbackFor 에 RemoteCallException 이 없는 곳이 있다 (UG-280).
                            하위 서비스가 실패하면 이 트랜잭션이 롤백되어 앞서 저장한 매칭 이력 행이 사라진다 —
                            장애를 가장 관측해야 할 때 기록이 없어진다. 선언을 다음 형태로 맞출 것:
                              noRollbackFor = {CustomFeignException.class, RemoteCallException.class}
                            이력을 쓰지 않는 REQUIRES_NEW 라면 그 사실을 주석으로 남기고 이 테스트를 함께
                            고칠 것 — 조용히 빼지 말 것.""")
                    .isEmpty();
        }

        @Test
        @DisplayName("RemoteCallException 을 선언한 파일은 그 예외를 catch 해 실패 사유를 남긴다")
        void 선언한_곳은_사유도_남긴다() throws IOException {
            // 행이 커밋돼도 failure_type 이 NULL 이면 응답을 받지 못하고 끊긴 요청과 구분되지 않는다.
            // UG-280 이 고친 것의 절반이 이쪽이라 선언만으로는 부족하다.
            List<String> missingCatch = findTransactionalSites().stream()
                    .filter(s -> s.annotation().contains(REQUIRED))
                    .filter(s -> !s.fileText().contains("catch (RemoteCallException"))
                    .map(Site::describe)
                    .distinct()
                    .toList();

            assertThat(missingCatch)
                    .as("""
                            noRollbackFor 에는 RemoteCallException 이 있는데 catch 가 없다.
                            행은 커밋되지만 failure_type 이 NULL 로 남아 원인을 알 수 없다.
                            Feign 호출을 감싸 matchHistory.fail(...) 을 남기고 rethrow 할 것.""")
                    .isEmpty();
        }

        @Test
        @DisplayName("여러 줄로 나눠 쓴 애노테이션도 정상 인식한다")
        void 여러줄_애노테이션_허용() throws IOException {
            // 괄호 짝을 세어 잘라내므로 줄바꿈 위치와 무관하다. 이 레포의 선언은 이미 여러 줄에
            // 걸쳐 있고, 한 줄만 보던 초기 버전은 포매터가 줄을 접으면 오탐을 냈다.
            List<Site> multiline = findTransactionalSites().stream()
                    .filter(s -> s.annotation().contains("\n"))
                    .toList();

            assertThat(multiline).isNotEmpty();
            assertThat(multiline.stream().filter(Site::isRequiresNew).toList())
                    .allSatisfy(s -> assertThat(s.annotation()).contains(REQUIRED));
        }
    }

    @Nested
    @DisplayName("예외 계층")
    class Hierarchy {

        @Test
        @DisplayName("RemoteCallException 은 BusinessException 하위여야 한다 — 응답 계약 유지의 전제")
        void 응답_계약_유지() {
            // BusinessException 을 벗어나면 GlobalExceptionHandler 가 잡지 못해 500 이 나가고,
            // 기존 PJ-005 400 계약이 깨진다. 고객 코드가 PJ-005 로 분기하고 있다.
            assertThat(BusinessException.class).isAssignableFrom(RemoteCallException.class);
            assertThat(new RemoteCallException(503).getErrorType().getCode()).isEqualTo("PJ-005");
        }

        @Test
        @DisplayName("RemoteCallException 은 CustomGateException 과 별개 계층이어야 한다")
        void 별개_계층() {
            // 같은 계층이면 noRollbackFor 에 넣는 순간 CustomGateException 까지 커밋을 허용하게 되고,
            // FaceFeatureService 처럼 특징점과 이력을 함께 쓰는 경로에서 반쯤 등록된 특징점이 남는다.
            assertThat(CustomGateException.class.isAssignableFrom(RemoteCallException.class)).isFalse();
            assertThat(RemoteCallException.class.isAssignableFrom(CustomGateException.class)).isFalse();
        }

        @Test
        @DisplayName("응답을 받지 못한 실패는 NO_RESPONSE 로 구분된다")
        void 응답없음_구분() {
            assertThat(new RemoteCallException(RemoteCallException.NO_RESPONSE).isNoResponse()).isTrue();
            assertThat(new RemoteCallException(503).isNoResponse()).isFalse();
        }
    }

    @Nested
    @DisplayName("응답 없는 실패 경로")
    class NoResponsePath {

        @Test
        @DisplayName("Feign 호출은 모두 RemoteCalls 를 거친다 — ErrorDecoder 가 잡지 못하는 실패가 있다")
        void 모든_Feign_호출이_래핑된다() throws IOException {
            // ErrorDecoder 는 상태 코드 300 이상의 "응답이 도착했을 때만" 불린다. 연결 거부·타임아웃은
            // Feign 이 RetryableException 을 던지고, 그것은 BusinessException 계열이 아니라
            // noRollbackFor 에 걸리지 않는다. RemoteCalls 가 그 경계에서 번역한다.
            List<String> unwrapped = new ArrayList<>();
            try (Stream<Path> paths = Files.walk(SOURCE_ROOT)) {
                List<Path> services = paths
                        .filter(f -> f.getFileName().toString().matches("(Face|Palm)Service\\.java"))
                        .toList();
                assertThat(services).hasSize(2);
                for (Path p : services) {
                    for (String line : Files.readString(p).split("\n")) {
                        if (line.contains("Client.") && !line.contains("RemoteCalls.")) {
                            unwrapped.add(p + " — " + line.trim());
                        }
                    }
                }
            }

            assertThat(unwrapped)
                    .as("""
                            RemoteCalls 로 감싸지 않은 Feign 호출이 있다 (UG-280 반박 리뷰).
                            연결 거부·읽기 타임아웃은 ErrorDecoder 를 거치지 않으므로 RetryableException 이
                            그대로 올라오고, noRollbackFor 에 걸리지 않아 매칭 이력 행이 사라진다.
                            RemoteCalls.of("<서비스>.<메서드>", () -> client.xxx(...)) 형태로 감쌀 것.""")
                    .isEmpty();
        }
    }
}
