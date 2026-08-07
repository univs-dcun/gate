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
 * 프로젝트 하나에 API 키 하나 — {@code ApiKey} 행을 만드는 곳은 <b>프로젝트 생성 한 곳뿐</b>이다
 * (UG-312).
 *
 * <p>제품 결정이다. 프로젝트를 지우면 복구할 수 없고, 그래서 키 재발급 기능도 두지 않는다.
 * "프로젝트 1개 = API 키 1개" 가 그대로 성립한다.
 *
 * <p><b>왜 필요한가.</b> 제거된 {@code RegenerateApiKeyUseCase} 는 "기존 키 비활성화 → 새 키
 * 삽입" 이었는데, 그 순서가 V24 부분 유니크 인덱스와 양립하지 않았다. {@code deactivate()} 는
 * 더티 마킹일 뿐이고 {@code @GeneratedValue(IDENTITY)} 라 {@code save()} 가 INSERT 를 즉시
 * 내보내므로, 그 시점에 기존 행이 아직 활성이라 제약에 걸린다 (반박 리뷰가 실측). 명시적
 * {@code flush()} 로 막을 수는 있지만 기억에 의존하는 방어다. 같은 모양이 다시 들어오면
 * 커밋 전에 멈추는 편이 낫다.
 *
 * <p><b>이 검사의 성격 — 트립와이어이지 증명이 아니다.</b> 소스 텍스트를 정규식으로 훑을 뿐이라
 * 우회는 얼마든지 가능하다. 반박 리뷰가 초판에 변이 10종을 심어 <b>9종이 통과</b>하는 것을
 * 실측했다. 이번 판에서 그중 리터럴 형태·생성자·세터·빌더 기본값·벌크 UPDATE·허용 목록
 * 파일명 충돌을 막았지만, 리플렉션이나 새로 만든 우회 표현까지 막지는 못한다.
 *
 * <p>따라서 이 테스트의 초록은 <b>"활성 키 경로가 하나임의 보증" 이 아니다.</b> 진짜 방어선은
 * V24 인덱스이고, 이것은 그 위반을 운영이 아니라 CI 에서 먼저 보게 하려는 장치다. 초록을
 * 보증으로 읽지 말 것.
 *
 * <p><b>남은 구멍.</b> {@link #허용} 을 넓히면 조용히 통과한다. 다만 목록이 상수 하나라
 * 넓히는 변경은 diff 에 반드시 보인다 — 테스트를 고치는 것은 진단이 아니라 결정이므로 그게
 * 맞다.
 */
@DisplayName("UG-312: 활성 API 키를 만드는 곳은 하나뿐")
class SingleActiveApiKeyGuardTest {

    private static final Path SOURCE_ROOT = Path.of("src/main/java");

    private static final Path ENTITY = SOURCE_ROOT.resolve(
            "ai/univs/gate/modules/api_key/domain/entity/ApiKey.java");

    /**
     * 유일하게 허용되는 자리. 프로젝트와 키를 같은 트랜잭션에서 함께 만든다.
     *
     * <p>파일명이 아니라 <b>경로</b>로 비교한다. 파일명만 보면 다른 패키지에 같은 이름의
     * 클래스를 만드는 것만으로 뚫린다 (반박 리뷰 실측).
     */
    private static final String 허용 =
            "ai/univs/gate/modules/project/application/usecase/CreateProjectUseCase.java";

    /**
     * {@code ApiKey} 행을 만들거나 활성 상태로 바꾸는 표현들.
     *
     * <p>인자를 <b>보지 않는다.</b> {@code .isActive(true)} 만 잡으면
     * {@code boolean on = true; .isActive(on)} 한 줄로 뚫린다 — 텍스트 검사로는 변수의 값을
     * 알 수 없으니, 인자와 무관하게 "여기서 ApiKey 를 만든다" 자체를 금지하는 편이 정확하다.
     * 빈 괄호는 제외한다 ({@code result.isActive()} 같은 레코드 접근자다).
     */
    private static final List<Pattern> 생성_표현 = List.of(
            Pattern.compile("\\bApiKey\\s*\\.\\s*builder\\s*\\("),
            Pattern.compile("\\bnew\\s+ApiKey\\s*\\("),
            Pattern.compile("\\bsetIsActive\\s*\\("),
            Pattern.compile("\\.isActive\\s*\\(\\s*[^)\\s]"));

    /**
     * 애노테이션은 <b>정규식</b>으로 찾는다. {@code contains("@Modifying")} 로 두면
     * {@code @org.springframework.data.jpa.repository.Modifying} 라고 쓰는 것만으로 뚫린다 —
     * 이 세션에서 실제로 그 변이가 살아남았고, {@code @DynamicInsert} 검사에서도 같은 실수를
     * 한 적이 있다.
     */
    private static final Pattern MODIFYING = Pattern.compile("@(?:[\\w.]+\\.)?Modifying\\b");

    private static final Pattern 활성으로_되쓴다 =
            Pattern.compile("is_?[Aa]ctive\\s*=\\s*(true|TRUE|1)\\b");

    @Test
    @DisplayName("프로젝트 생성 외에는 ApiKey 를 만들지 않는다")
    void 활성키_생성은_한_곳뿐이다() {
        List<String> 위반 = 프로덕션_소스().stream()
                .filter(SingleActiveApiKeyGuardTest::생성한다)
                .map(SingleActiveApiKeyGuardTest::상대경로)
                .filter(path -> !허용.equals(path))
                .sorted()
                .toList();

        assertThat(위반)
                .as("""
                        프로젝트당 활성 API 키는 하나다 (V24 부분 유니크 인덱스가 DB 에서 강제한다).

                        여기 걸렸다면 ApiKey 를 만드는 경로가 새로 생긴 것이다. 그 경로가 기존 키를
                        비활성화한 뒤 새 키를 넣는 모양이라면, 비활성화를 flush 하지 않는 한 INSERT 가
                        먼저 나가 인덱스에 걸린다 — 제거된 재발급 기능이 정확히 그 함정이었다.

                        정말 필요한 경로라면 이 테스트의 허용 목록을 고치면서 V24 와의 상호작용을
                        함께 검토할 것.""")
                .isEmpty();
    }

    /**
     * 엔티티가 스스로 활성이 되지 않는지 (반박 리뷰 지적).
     *
     * <p>{@code @Builder.Default private Boolean isActive = true;} 한 줄이면 <b>모든</b> 빌더가
     * 활성 키를 만든다. 그러면 어느 파일에도 {@code .isActive(...)} 가 없으니 위 검사는 전부
     * 초록이다. 되살아난 {@code activate()} 도 같은 자리에서 막는다 — 초판은 파일 전체에서
     * {@code activate()} 를 찾다가 무관한 {@code project.activate()} 에 오탐했다.
     */
    @Test
    @DisplayName("ApiKey 엔티티는 기본값으로 활성이 되지 않는다")
    void 엔티티가_스스로_활성이_되지_않는다() {
        String entity = 본문만(읽는다(ENTITY));

        assertThat(entity)
                .as("isActive 에 기본값을 주면 모든 빌더가 활성 키를 만든다 — "
                        + "생성 경로 검사가 통째로 무력해진다")
                .doesNotContainPattern("@Builder\\s*\\.\\s*Default")
                .doesNotContainPattern("isActive\\s*=\\s*(true|Boolean\\.TRUE)\\s*;");
        assertThat(entity)
                .as("UG-312 에서 지운 activate() 다. 되살리면 재발급 경로가 함께 돌아온다")
                .doesNotContainPattern("\\bvoid\\s+activate\\s*\\(");
    }

    /**
     * 벌크 UPDATE 로 되살리지 않는지 (반박 리뷰 지적).
     *
     * <p>{@code @Modifying @Query("UPDATE ApiKey a SET a.isActive = true ...")} 는 엔티티를
     * 만들지도 세터를 부르지도 않으므로 위 두 검사를 모두 비켜 간다. 영속성 컨텍스트를 우회하니
     * 사고가 나면 원인 추적도 더 어렵다.
     */
    @Test
    @DisplayName("벌크 UPDATE 로 활성 상태를 되살리지 않는다")
    void 벌크_업데이트로_되살리지_않는다() {
        List<String> 위반 = 프로덕션_소스().stream()
                .filter(file -> {
                    String body = 본문만(읽는다(file));
                    return MODIFYING.matcher(body).find() && 활성으로_되쓴다.matcher(body).find();
                })
                .map(SingleActiveApiKeyGuardTest::상대경로)
                .sorted()
                .toList();

        assertThat(위반)
                .as("벌크 UPDATE 는 엔티티 생성도 세터 호출도 아니라 다른 검사를 전부 비켜 간다")
                .isEmpty();
    }

    /**
     * 가드가 실제로 무언가를 보고 있는지.
     *
     * <p>경로 오타나 패턴 실수로 대상이 0개가 되면 위 검사들은 영원히 초록이고, 그 상태가
     * "위반 없음" 과 구분되지 않는다.
     */
    @Test
    @DisplayName("허용된 한 곳은 실제로 찾아낸다")
    void 가드가_공회전하지_않는다() {
        List<String> 생성하는_파일 = 프로덕션_소스().stream()
                .filter(SingleActiveApiKeyGuardTest::생성한다)
                .map(SingleActiveApiKeyGuardTest::상대경로)
                .toList();

        assertThat(생성하는_파일)
                .as("ApiKey 를 만드는 곳을 한 군데도 못 찾으면 패턴이 망가진 것이다 — "
                        + "프로젝트 생성은 반드시 활성 키를 만든다")
                .contains(허용);
        assertThat(Files.exists(ENTITY))
                .as("엔티티 경로가 어긋나면 기본값 검사가 빈 문자열을 훑는다: %s", ENTITY)
                .isTrue();
    }

    // ─────────────────────────────────────────────────────────────────────────

    private static boolean 생성한다(Path file) {
        String body = 본문만(읽는다(file));
        if (!body.contains("ApiKey")) {
            return false;
        }
        return 생성_표현.stream().anyMatch(p -> p.matcher(body).find());
    }

    /** 주석을 걷어낸다. 주석 속 예시 코드에 걸리면 진단이 오답을 가리킨다. */
    private static String 본문만(String source) {
        return source.replaceAll("(?s)/\\*.*?\\*/", " ").replaceAll("//[^\n]*", " ");
    }

    private static String 상대경로(Path file) {
        return SOURCE_ROOT.relativize(file).toString().replace('\\', '/');
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
