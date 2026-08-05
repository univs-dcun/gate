package ai.univs.gate.support.api_key;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * UG-281: 소유 검증을 건너뛰는 경로가 인증 쪽으로 새어 들어오는 것을 막는다.
 *
 * <p>{@code ApiKeyService} 는 검증 없는 조회를 {@code findByApiKeyUnverified} 라는 이름으로 남겨 뒀다.
 * 무인증 데모 경로에는 대조할 accountId 가 없어 이것이 필요하기 때문이다. 문제는 이름만으로는 오용을
 * 막지 못한다는 점이다 — 새 인증 API 를 만드는 사람이 자동완성에서 집어들면 그 엔드포인트만 조용히
 * 테넌트 격리가 뚫린 채로 배포된다. 리뷰에서 잡히리라 기대할 수 없다.
 *
 * <p>그래서 소스를 직접 훑어 규칙 위반이면 실패시킨다. 컴파일러가 못 하는 일을 테스트가 대신한다.
 *
 * <p><b>세 방향을 모두 본다.</b> 처음에는 {@code findByApiKeyUnverified} 호출처만 셌는데, 반박 리뷰의
 * 변이 테스트에서 등가 우회 두 가지가 그대로 살아남았다.
 *
 * <ol>
 *   <li>인증 경로에서 {@code findByApiKeyUnverified} 직접 호출
 *   <li>인증 경로에서 {@code findByApiKey(CallerType.DEMO, ...)} 호출 — ①과 효과가 같다
 *   <li>인증 DTO 의 {@code CallerType.API} 를 {@code DEMO} 로 바꾸기 — 한 글자로 격리가 사라진다
 * </ol>
 *
 * <p>반대 방향(데모에 {@code CallerType.API})도 본다. 그쪽은 보안이 아니라 장애다 — 데모는 accountId 로
 * {@code 0L} 을 넘기므로 검증을 받으면 QR 데모의 등록·매칭·라이브니스가 전부 400 이 된다.
 *
 * <p>데모에 새 엔드포인트를 추가하는 것은 정상이므로 허용 목록은 <b>패키지</b> 단위다. 인증 경로에서
 * 정말로 검증을 건너뛰어야 하는 상황이 생기면, 이 테스트를 고치면서 사유를 여기 적으면 된다.
 * 무엇을 왜 허용했는지가 한곳에 남는 것이 이 테스트의 목적이다.
 */
@DisplayName("UG-281: 소유 검증 우회 경로의 호출처 제한")
class ApiKeyOwnershipGuardTest {

    private static final String UNVERIFIED = "findByApiKeyUnverified";
    private static final String DEMO_LITERAL = "CallerType.DEMO";
    private static final String API_LITERAL = "CallerType.API";

    private static final String DEMO_PACKAGE = "ai/univs/gate/facade/demo/";
    private static final String API_KEY_SERVICE = "ai/univs/gate/support/api_key/ApiKeyService.java";

    /** 소유 검증을 건너뛰어도 되는 곳. 무인증 데모 경로와 그 분기를 구현한 서비스 자신뿐이다. */
    private static final List<String> ALLOWED = List.of(DEMO_PACKAGE, API_KEY_SERVICE);

    private static final Path SOURCE_ROOT = Path.of("src/main/java");

    /**
     * 스캔이 실제로 소스 트리를 봤는지 확인하는 하한선.
     *
     * <p>{@link #SOURCE_ROOT} 가 상대 경로라, 다른 작업 디렉토리에서 실행하면 엉뚱한 트리를 훑고
     * "위반 0건" 으로 <b>조용히 통과</b>할 수 있다. 없는 디렉토리면 예외가 나지만, 존재하되 다른
     * 트리면 그렇지 않다. 그 경우 이 테스트는 가짜 안전감만 준다.
     *
     * <p>현재 300개가 넘으므로 100은 넉넉한 하한이다.
     */
    private static final int MIN_SCANNED_FILES = 100;

    @Test
    @DisplayName("facade.demo 밖에서는 findByApiKeyUnverified 를 부르지 않는다")
    void 검증없는_조회는_데모_전용() throws IOException {
        assertThat(violations(UNVERIFIED, outsideDemo()))
                .as("인증 경로에서 소유 검증을 건너뛰면 계정 A 가 계정 B 의 API 키로 B 의 갤러리에 "
                        + "접근할 수 있다. findOwnedByApiKey 또는 findByApiKey(CallerType, ...) 를 쓸 것")
                .isEmpty();
    }

    @Test
    @DisplayName("facade.demo 밖에서는 CallerType.DEMO 를 쓰지 않는다 — 등가 우회 차단")
    void 데모_구분자는_데모_전용() throws IOException {
        // findByApiKey(CallerType.DEMO, ...) 는 ApiKeyService 안에서 그대로
        // findByApiKeyUnverified 로 흐른다. 이름 기반 스캔만으로는 보이지 않는다.
        assertThat(violations(DEMO_LITERAL, outsideDemo()))
                .as("인증 경로에서 CallerType.DEMO 를 넘기면 소유 검증이 꺼진다 — "
                        + "findByApiKeyUnverified 를 직접 부르는 것과 효과가 같다")
                .isEmpty();
    }

    @Test
    @DisplayName("facade.demo 안에서는 CallerType.API 를 쓰지 않는다 — 데모 장애 방지")
    void 인증_구분자는_데모에_없다() throws IOException {
        // 반대 방향. 보안이 아니라 가용성 문제다.
        assertThat(violations(API_LITERAL, rel -> rel.startsWith(DEMO_PACKAGE)))
                .as("데모는 무인증이라 대조할 accountId 가 없고 0L 을 넘긴다. CallerType.API 를 "
                        + "넘기면 소유 검증에 걸려 QR 데모 전 기능이 400 이 된다")
                .isEmpty();
    }

    @Test
    @DisplayName("소유 검증을 우회하는 옛 이름이 되살아나지 않았다")
    void 옛_이름_부활_금지() throws IOException {
        // findByApiKey(String) 하나만 있던 시절이 이 결함의 원인이었다. 편의를 위해 되살리면
        // 모든 호출처가 다시 '검증 없음' 이 기본값이 된다.
        String service = Files.readString(SOURCE_ROOT.resolve(API_KEY_SERVICE));

        assertThat(service)
                .as("소유 검증 없는 1-인자 findByApiKey(String) 는 되살리지 않는다. "
                        + "검증 없는 조회가 필요하면 이름에 드러나는 findByApiKeyUnverified 를 쓴다")
                .doesNotContain("public ApiKey findByApiKey(String apiKey)");
    }

    private static Predicate<String> outsideDemo() {
        return rel -> ALLOWED.stream().noneMatch(rel::startsWith);
    }

    private List<String> violations(String literal, Predicate<String> isViolation) throws IOException {
        List<String> scanned;
        try (Stream<Path> files = Files.walk(SOURCE_ROOT)) {
            scanned = files
                    .filter(p -> p.toString().endsWith(".java"))
                    .map(p -> SOURCE_ROOT.relativize(p).toString().replace('\\', '/'))
                    .sorted()
                    .toList();
        }

        assertThat(scanned)
                .as("소스 트리를 제대로 훑지 못했다. 작업 디렉토리가 gate 모듈 루트인지 확인할 것 — "
                        + "엉뚱한 트리를 훑으면 위반 0건으로 조용히 통과한다")
                .hasSizeGreaterThan(MIN_SCANNED_FILES);

        return scanned.stream()
                .filter(rel -> contains(SOURCE_ROOT.resolve(rel), literal))
                .filter(isViolation)
                .toList();
    }

    private boolean contains(Path file, String literal) {
        try {
            // 단순 포함 검사다. 주석·문자열 안의 언급도 위반으로 세지만(오탐), 놓치는 쪽보다 낫다.
            // 리플렉션이나 메서드 참조도 이름 문자열이 남아 잡힌다.
            //
            // javadoc 은 {@link CallerType#DEMO} 처럼 '#' 을 쓰므로 'CallerType.DEMO' 리터럴 검사에
            // 걸리지 않는다. 설명이 필요하면 그 형식을 쓸 것.
            return Files.readString(file).contains(literal);
        } catch (IOException e) {
            throw new IllegalStateException("소스를 읽을 수 없다: " + file, e);
        }
    }
}
