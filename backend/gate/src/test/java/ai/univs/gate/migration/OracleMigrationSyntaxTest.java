package ai.univs.gate.migration;

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
import org.junit.jupiter.api.Test;

/**
 * 오라클 방언 마이그레이션의 문법 가드 (UG-292).
 *
 * <p>이 결함은 <b>온프레미스 납품 시점에야 드러난다.</b> CI 도 로컬 개발도 postgresql 로 돌기
 * 때문에 오라클 파일은 아무도 실행하지 않는다. UG-292 에서 발견된 절 순서 오류는 V8 부터 있었고,
 * 그대로였다면 오라클 환경에서 V8 이후 마이그레이션이 통째로 실패했을 것이다 — V21 은
 * face_feature/palm_feature 를 biometric_feature 로 통합하는 마이그레이션이라, 실패하면 스키마가
 * 통합 이전에 머물고 현재 코드가 아예 동작하지 않는다.
 *
 * <p>실제 오라클 인스턴스로 마이그레이션을 돌려보는 것이 정석이지만 지금은 그 환경이 없다. 그
 * 전까지 최소한 <b>정적으로 잡을 수 있는 것</b>은 여기서 잡는다. 오라클을 CI 에 붙이게 되면 이
 * 테스트는 그쪽으로 대체된다.
 */
@DisplayName("오라클 마이그레이션 문법 가드 (UG-292)")
class OracleMigrationSyntaxTest {

    private static final Path ORACLE = Path.of("src/main/resources/db/migration/oracle");
    private static final Path POSTGRESQL = Path.of("src/main/resources/db/migration/postgresql");

    /** 방언 파일이 통째로 사라졌는데 조용히 통과하는 것을 막는다. 현재 22개. */
    private static final int MIN_FILES = 22;

    private record Violation(Path file, int line, String rule, String text) {
        @Override
        public String toString() {
            return "%s:%d  [%s]  %s".formatted(file.getFileName(), line, rule, text.strip());
        }
    }

    /**
     * 오라클에서만 틀리는 문법들. 포스트그레스는 대부분 허용하므로 쌍둥이 파일을 복사해 오면
     * 그대로 딸려 온다 — 실제로 UG-292 가 그렇게 생겼다.
     */
    private record Rule(String name, Pattern pattern, String why) {}

    private static final List<Rule> RULES = List.of(
            new Rule("DEFAULT/NOT NULL 절 순서",
                    Pattern.compile("NOT\\s+NULL\\s+DEFAULT", Pattern.CASE_INSENSITIVE),
                    "오라클은 DEFAULT 가 NOT NULL 보다 앞이어야 한다. 이 순서는 ORA-00907 을 낸다. "
                            + "포스트그레스는 두 순서를 모두 허용해서 쌍둥이 파일에서는 드러나지 않는다."),
            new Rule("ADD COLUMN",
                    Pattern.compile("\\bADD\\s+COLUMN\\b", Pattern.CASE_INSENSITIVE),
                    "오라클의 ALTER TABLE 에는 COLUMN 키워드가 없다. ADD col TYPE 또는 ADD (col TYPE)."),
            new Rule("IF (NOT) EXISTS",
                    Pattern.compile("\\bIF\\s+(NOT\\s+)?EXISTS\\b", Pattern.CASE_INSENSITIVE),
                    "오라클 DDL 은 IF EXISTS 를 지원하지 않는다."),
            new Rule("포스트그레스 전용 타입",
                    Pattern.compile("\\b(BOOLEAN|SERIAL|BIGSERIAL|TEXT|BYTEA|JSONB)\\b",
                            Pattern.CASE_INSENSITIVE),
                    "오라클에 없는 타입이다. NUMBER(1) / IDENTITY / CLOB / BLOB 으로 쓸 것."),
            new Rule("MODIFY COLUMN / CHANGE COLUMN",
                    Pattern.compile("\\b(MODIFY|CHANGE)\\s+COLUMN\\b", Pattern.CASE_INSENSITIVE),
                    "오라클은 MODIFY 뒤에 COLUMN 을 쓰지 않는다."),
            new Rule("LIMIT / OFFSET",
                    Pattern.compile("\\b(LIMIT|OFFSET)\\s+\\d", Pattern.CASE_INSENSITIVE),
                    "오라클은 FETCH FIRST n ROWS ONLY 를 쓴다."),
            new Rule("백틱 식별자",
                    Pattern.compile("`"),
                    "MySQL 문법이다."));

    private static List<Path> sqlFiles(Path dir) throws IOException {
        try (Stream<Path> paths = Files.walk(dir)) {
            return paths.filter(f -> f.toString().endsWith(".sql")).sorted().toList();
        }
    }

    /** 주석과 문자열 리터럴은 검사 대상이 아니다 — 한글 설명에 우연히 걸리는 것을 막는다. */
    private static String scrub(String line) {
        String withoutComment = line.replaceAll("--.*$", "");
        return withoutComment.replaceAll("'(?:''|[^'])*'", "''");
    }

    @Test
    @DisplayName("오라클 방언 파일에 오라클이 거부하는 문법이 없다")
    void 오라클_문법_위반이_없다() throws IOException {
        List<Path> files = sqlFiles(ORACLE);
        assertThat(files)
                .as("오라클 마이그레이션을 찾지 못했다면 SOURCE 경로(%s)가 잘못됐을 가능성이 크다", ORACLE)
                .hasSizeGreaterThanOrEqualTo(MIN_FILES);

        List<Violation> violations = new ArrayList<>();
        for (Path f : files) {
            String[] lines = Files.readString(f).split("\n");
            for (int i = 0; i < lines.length; i++) {
                String scrubbed = scrub(lines[i]);
                for (Rule rule : RULES) {
                    Matcher m = rule.pattern().matcher(scrubbed);
                    if (m.find()) {
                        violations.add(new Violation(f, i + 1, rule.name(), lines[i]));
                    }
                }
            }
        }

        assertThat(violations)
                .as("""
                        오라클 방언 마이그레이션에 오라클이 거부하는 문법이 있다 (UG-292).

                        이 결함은 온프레미스 납품 시점에야 드러난다 — CI 도 로컬도 postgresql 로 돌기
                        때문이다. 마이그레이션이 실패하면 스키마가 그 버전 이전에 멈추고 애플리케이션이
                        기동하지 못한다.

                        규칙별 이유:
                        %s""".formatted(RULES.stream()
                        .map(r -> "  - %s: %s".formatted(r.name(), r.why()))
                        .reduce((a, b) -> a + "\n" + b)
                        .orElse("")))
                .isEmpty();
    }

    @Test
    @DisplayName("두 방언의 마이그레이션 버전이 정확히 일치한다")
    void 방언_버전이_일치한다() throws IOException {
        // 한쪽에만 있는 버전이 생기면 그 환경에서만 스키마가 어긋난다. 파일이 늘 때 쌍으로
        // 추가하는 것을 강제한다.
        List<String> oracle = sqlFiles(ORACLE).stream().map(p -> p.getFileName().toString()).toList();
        List<String> postgresql =
                sqlFiles(POSTGRESQL).stream().map(p -> p.getFileName().toString()).toList();

        assertThat(oracle)
                .as("oracle 쪽에만 있거나 빠진 마이그레이션이 있다")
                .containsExactlyInAnyOrderElementsOf(postgresql);
    }
}
