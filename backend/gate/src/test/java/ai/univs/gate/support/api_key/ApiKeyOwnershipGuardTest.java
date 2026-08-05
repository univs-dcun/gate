package ai.univs.gate.support.api_key;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * UG-281: 소유 검증을 건너뛰는 조회가 인증 경로로 새어 들어오는 것을 막는다.
 *
 * <p>{@code ApiKeyService} 는 검증 없는 조회를 {@code findByApiKeyUnverified} 라는 이름으로
 * 남겨 뒀다. 무인증 데모 경로에는 대조할 accountId 가 없어 이것이 필요하기 때문이다. 문제는 이름만으로는
 * 오용을 막지 못한다는 점이다 — 나중에 새 인증 API 를 만드는 사람이 자동완성에서 이것을 집어들면
 * 그 엔드포인트만 조용히 테넌트 격리가 뚫린 채로 배포된다. 리뷰에서 잡히리라 기대할 수 없다.
 *
 * <p>그래서 호출처를 소스에서 직접 세어 {@code facade.demo} 밖이면 실패시킨다. 컴파일러가 못 하는
 * 일을 테스트가 대신한다.
 *
 * <p>데모에 새 엔드포인트를 추가하는 것은 정상이므로 허용 목록은 <b>패키지</b> 단위다. 반대로
 * 인증 경로에서 정말로 검증을 건너뛰어야 하는 상황이 생긴다면, 이 테스트를 고치면서 그 이유를
 * 여기에 적으면 된다. 무엇을 허용했는지가 한곳에 남는 것이 이 테스트의 목적이다.
 */
@DisplayName("UG-281: 검증 없는 API 키 조회의 호출처 제한")
class ApiKeyOwnershipGuardTest {

    private static final String UNVERIFIED = "findByApiKeyUnverified";

    /** 소유 검증을 건너뛰어도 되는 곳. 무인증 데모 경로뿐이다. */
    private static final List<String> ALLOWED_PREFIXES = List.of(
            "ai/univs/gate/facade/demo/",
            "ai/univs/gate/support/api_key/ApiKeyService.java");

    private static final Path SOURCE_ROOT = Path.of("src/main/java");

    @Test
    @DisplayName("facade.demo 밖에서는 findByApiKeyUnverified 를 부르지 않는다")
    void 검증없는_조회는_데모_전용() throws IOException {
        List<String> violations;
        try (Stream<Path> files = Files.walk(SOURCE_ROOT)) {
            violations = files
                    .filter(p -> p.toString().endsWith(".java"))
                    .filter(this::callsUnverified)
                    .map(p -> SOURCE_ROOT.relativize(p).toString().replace('\\', '/'))
                    .filter(rel -> ALLOWED_PREFIXES.stream().noneMatch(rel::startsWith))
                    .sorted()
                    .toList();
        }

        assertThat(violations)
                .as("인증 경로에서 소유 검증을 건너뛰면 계정 A 가 계정 B 의 API 키로 B 의 갤러리에 "
                        + "접근할 수 있다. findOwnedByApiKey 또는 findByApiKey(CallerType, ...) 를 쓸 것")
                .isEmpty();
    }

    @Test
    @DisplayName("소유 검증을 우회하는 옛 이름이 되살아나지 않았다")
    void 옛_이름_부활_금지() throws IOException {
        // findByApiKey(String) 하나만 있던 시절이 이 결함의 원인이었다. 편의를 위해 되살리면
        // 모든 호출처가 다시 '검증 없음' 이 기본값이 된다.
        String service = Files.readString(SOURCE_ROOT.resolve("ai/univs/gate/support/api_key/ApiKeyService.java"));

        assertThat(service)
                .as("소유 검증 없는 1-인자 findByApiKey(String) 는 되살리지 않는다. "
                        + "검증 없는 조회가 필요하면 이름에 드러나는 findByApiKeyUnverified 를 쓴다")
                .doesNotContain("public ApiKey findByApiKey(String apiKey)");
    }

    private boolean callsUnverified(Path file) {
        try {
            String body = Files.readString(file);
            // 선언부(ApiKeyService)는 허용 목록에 있으므로 여기서는 단순 포함 검사로 충분하다.
            return body.contains(UNVERIFIED);
        } catch (IOException e) {
            throw new IllegalStateException("소스를 읽을 수 없다: " + file, e);
        }
    }
}
