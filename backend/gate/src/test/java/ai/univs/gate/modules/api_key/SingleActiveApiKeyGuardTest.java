package ai.univs.gate.modules.api_key;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 프로젝트 하나에 API 키 하나 — 활성 키를 만드는 곳은 <b>프로젝트 생성 한 곳뿐</b>이다 (UG-312).
 *
 * <p>제품 결정이다. 프로젝트를 지우면 복구할 수 없고, 그래서 키 재발급 기능도 두지 않는다.
 * "프로젝트 1개 = API 키 1개" 가 그대로 성립한다.
 *
 * <p>DB 쪽은 V24 의 부분 유니크 인덱스가 막는다. 이 테스트는 <b>그 인덱스에 걸릴 코드가 애초에
 * 들어오지 못하게</b> 한다. 둘의 역할이 다르다 — 인덱스는 운영에서 500 으로 드러나고, 이 검사는
 * 커밋 전에 드러난다.
 *
 * <p><b>왜 이 검사가 필요한가.</b> 제거된 {@code RegenerateApiKeyUseCase} 는 "기존 키 비활성화 →
 * 새 키 삽입" 이었는데, 그 순서가 V24 인덱스와 양립하지 않았다. {@code deactivate()} 는 더티
 * 마킹일 뿐이고 {@code @GeneratedValue(IDENTITY)} 라 {@code save()} 가 INSERT 를 즉시
 * 내보내므로, 그 시점에 기존 행이 아직 활성이라 제약에 걸린다 (반박 리뷰가 실측). 명시적
 * {@code flush()} 로 막을 수는 있지만 <b>기억에 의존하는 방어</b>다.
 *
 * <p>지금은 그 경로가 없다. 다시 생기면 같은 함정을 다시 밟게 되므로, 들어오는 순간 여기서
 * 멈춘다. 새 경로가 정말 필요하면 이 테스트를 고치면서 V24 와의 상호작용을 함께 생각하게 된다 —
 * 그게 이 검사의 목적이다.
 *
 * <p><b>한계.</b> {@link #허용} 목록을 넓히면 이 검사는 조용히 통과한다 — 변이 심기로 확인했다.
 * 차단 목록·허용 목록을 쓰는 검사가 공통으로 갖는 성질이고, 테스트 자신을 고치는 것은 진단이
 * 아니라 결정이므로 diff 에서 눈에 띄는 편이 낫다. 그래서 목록을 상수 하나로 두어 변경이
 * 리뷰에 반드시 보이게 했다.
 */
@DisplayName("UG-312: 활성 API 키를 만드는 곳은 하나뿐")
class SingleActiveApiKeyGuardTest {

    private static final Path SOURCE_ROOT = Path.of("src/main/java");

    /** 유일하게 허용되는 자리. 프로젝트와 키를 같은 트랜잭션에서 함께 만든다. */
    private static final String 허용 = "CreateProjectUseCase.java";

    /** {@code ApiKey} 를 활성 상태로 만드는 표현들. */
    private static final List<Pattern> 활성화_표현 = List.of(
            Pattern.compile("\\.isActive\\s*\\(\\s*true\\s*\\)"),
            Pattern.compile("\\.isActive\\s*\\(\\s*Boolean\\.TRUE\\s*\\)"),
            Pattern.compile("\\.setIsActive\\s*\\(\\s*true\\s*\\)"),
            Pattern.compile("\\bactivate\\s*\\(\\s*\\)"));

    @Test
    @DisplayName("프로젝트 생성 외에는 활성 API 키를 만들지 않는다")
    void 활성키_생성은_한_곳뿐이다() {
        List<String> 위반 = 프로덕션_소스().stream()
                .filter(SingleActiveApiKeyGuardTest::활성화한다)
                .map(p -> p.getFileName().toString())
                .filter(name -> !허용.equals(name))
                .sorted()
                .toList();

        assertThat(위반)
                .as("""
                        프로젝트당 활성 API 키는 하나다 (V24 부분 유니크 인덱스가 DB 에서 강제한다).

                        여기 걸렸다면 활성 키를 새로 만드는 경로가 생긴 것이다. 그 경로가 기존 키를
                        비활성화한 뒤 새 키를 넣는 모양이라면, 비활성화를 flush 하지 않는 한 INSERT 가
                        먼저 나가 인덱스에 걸린다 — 제거된 재발급 기능이 정확히 그 함정이었다.

                        정말 필요한 경로라면 이 테스트의 허용 목록을 고치면서 V24 와의 상호작용을
                        함께 검토할 것.""")
                .isEmpty();
    }

    /**
     * 가드가 실제로 무언가를 보고 있는지.
     *
     * <p>경로 오타나 패턴 실수로 대상이 0개가 되면 위 검사는 영원히 초록이고, 그 상태가 "위반
     * 없음" 과 구분되지 않는다.
     */
    @Test
    @DisplayName("허용된 한 곳은 실제로 찾아낸다")
    void 가드가_공회전하지_않는다() {
        List<String> 활성화하는_파일 = 프로덕션_소스().stream()
                .filter(SingleActiveApiKeyGuardTest::활성화한다)
                .map(p -> p.getFileName().toString())
                .toList();

        assertThat(활성화하는_파일)
                .as("활성 키를 만드는 곳을 한 군데도 못 찾으면 패턴이 망가진 것이다 — "
                        + "프로젝트 생성은 반드시 활성 키를 만든다")
                .contains(허용);
    }

    private static boolean 활성화한다(Path file) {
        String body = 읽는다(file).replaceAll("(?s)/\\*.*?\\*/", " ").replaceAll("//[^\n]*", " ");
        if (!body.contains("ApiKey")) {
            return false;
        }
        return 활성화_표현.stream().anyMatch(p -> p.matcher(body).find());
    }

    private static List<Path> 프로덕션_소스() {
        try (Stream<Path> paths = Files.walk(SOURCE_ROOT)) {
            return paths.filter(p -> p.toString().endsWith(".java")).sorted().toList();
        } catch (IOException e) {
            throw new UncheckedIOException("소스 트리를 못 읽었다: " + SOURCE_ROOT, e);
        }
    }

    private static String 읽는다(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException e) {
            throw new UncheckedIOException(file.toString(), e);
        }
    }
}
