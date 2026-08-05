package ai.univs.gate.shared.exception;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * UG-280 재발 방지.
 *
 * <p>{@code noRollbackFor} 를 선언하는 곳은 "이 예외가 나도 지금까지 쓴 것은 남겨라" 라고 말하는
 * 지점이다. 매칭 경로에서 그 대상은 <b>매칭 이력 행</b>이고, 하위 서비스 5xx 는 이력이 가장
 * 필요한 상황이다. 그런데 목록에 {@link RemoteCallException} 을 빼면 그때만 행이 사라진다 —
 * 정상 동작에서는 드러나지 않고 장애 때만 드러나는 종류의 결함이다.
 *
 * <p>이 테스트는 소스를 훑어 {@code noRollbackFor} 가 있는 모든 지점에 {@code RemoteCallException}
 * 이 함께 있는지 본다. 새 매칭 UseCase 를 만들며 기존 선언을 복사해 오면 자동으로 지켜지고,
 * 일부만 적으면 여기서 걸린다.
 *
 * <p>런타임 동작이 아니라 선언을 검사하는 이유는, 트랜잭션 롤백 여부를 단위 테스트로 확인할 수
 * 없기 때문이다 ({@code @Transactional} 은 프록시가 적용하므로 목 기반 테스트에서는 아예 돌지
 * 않는다). 각 UseCase 의 사유 기록은 별도 단위 테스트가 담당한다.
 */
@DisplayName("noRollbackFor 선언 가드 (UG-280)")
class RemoteCallRollbackGuardTest {

    private static final Path SOURCE_ROOT = Path.of("src/main/java");
    private static final String MARKER = "noRollbackFor";
    private static final String REQUIRED = "RemoteCallException";

    /** 엉뚱한 트리를 훑고 조용히 통과하는 것을 막는다. */
    private static final int MIN_DECLARATIONS = 10;

    private record Declaration(Path file, int line, String text) {}

    private static List<Declaration> findDeclarations() throws IOException {
        List<Declaration> found = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(SOURCE_ROOT)) {
            for (Path p : paths.filter(f -> f.toString().endsWith(".java")).toList()) {
                List<String> lines = Files.readAllLines(p);
                for (int i = 0; i < lines.size(); i++) {
                    String line = lines.get(i);
                    // 주석 줄은 제외한다 — 설명에 단어가 등장하는 것은 선언이 아니다
                    String trimmed = line.trim();
                    if (trimmed.startsWith("//") || trimmed.startsWith("*")) {
                        continue;
                    }
                    if (line.contains(MARKER)) {
                        found.add(new Declaration(p, i + 1, line));
                    }
                }
            }
        }
        return found;
    }

    @Test
    @DisplayName("noRollbackFor 를 선언한 모든 지점이 RemoteCallException 을 포함한다")
    void 모든_선언이_원격실패를_포함한다() throws IOException {
        List<Declaration> declarations = findDeclarations();

        assertThat(declarations)
                .as("noRollbackFor 선언을 찾지 못했다면 SOURCE_ROOT(%s)가 잘못됐을 가능성이 크다", SOURCE_ROOT)
                .hasSizeGreaterThanOrEqualTo(MIN_DECLARATIONS);

        List<String> missing = declarations.stream()
                .filter(d -> !d.text().contains(REQUIRED))
                .map(d -> "%s:%d — %s".formatted(d.file(), d.line(), d.text().trim()))
                .toList();

        assertThat(missing)
                .as("""
                        noRollbackFor 에 RemoteCallException 이 빠진 곳이 있다 (UG-280).
                        하위 서비스가 5xx 를 내면 이 지점의 REQUIRES_NEW 트랜잭션이 롤백되어
                        매칭 이력 행이 사라진다 — 장애를 가장 관측해야 할 때 기록이 없어진다.
                        선언을 다음 형태로 맞출 것:
                          noRollbackFor = {CustomFeignException.class, RemoteCallException.class}""")
                .isEmpty();
    }

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
}
