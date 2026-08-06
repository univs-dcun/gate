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
 * <p><b>이 테스트가 막는 것은 좁다.</b> 아래 규칙 목록에 적힌 문법만 잡는 <b>차단 목록</b>이지
 * 오라클 문법 검사기가 아니다. 목록에 없는 오류는 그대로 통과한다. 실제 오라클 인스턴스로
 * 마이그레이션을 돌려보는 것이 정석이며 이 테스트가 그것을 대체하지 못한다.
 *
 * <p>그런데도 두는 이유는 이 부류의 결함이 <b>납품 시점에야 드러나기</b> 때문이다. CI 도 로컬
 * 개발도 postgresql 로만 돌아서 오라클 파일은 아무도 실행하지 않는다. UG-292 에서 발견된 절 순서
 * 오류는 V8 부터 있었고 아무 신호도 내지 않았다.
 *
 * <p>규칙은 전부 <b>포스트그레스 쌍둥이에서 복사해 올 때 딸려 오는 것</b>들이다 — 실제로 UG-292 가
 * 그렇게 생겼다. 포스트그레스는 허용하고 오라클은 거부하는 문법이라 쌍둥이 쪽에서는 드러나지 않는다.
 *
 * <p><b>주의.</b> 이 테스트의 초록은 여전히 "오라클 납품이 된다" 는 뜻이 아니다. UG-296 이
 * {@code org.flywaydb:flyway-database-oracle} 을 넣어 <b>부팅은</b> 되게 만들었지만
 * ({@code FlywayOracleSupportTest} 가 지킨다), 실제 오라클 인스턴스에서 V1~V22 를 끝까지 돌려
 * 본 적은 아직 없다. 그리고 {@code gate-config} 의 {@code baseline-on-migrate: true} +
 * {@code baseline-version: 21} 이 프로파일 무관으로 걸려 있어, 비어 있지 않은 스키마에서는
 * V1~V21 이 통째로 스킵된다.
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
            return "%s:%d  [%s]  %s".formatted(file.getFileName(), line, rule, text);
        }
    }

    private record Rule(String name, Pattern pattern, String why) {}

    /**
     * 패턴은 모두 {@code \s+} 를 쓴다 — 줄바꿈을 건너뛰기 위해서다. 컬럼 정의를 여러 줄로 나눠
     * 쓰면 한 줄 단위 검사는 통째로 놓친다 (초판의 결함이었다).
     */
    private static final List<Rule> RULES = List.of(
            new Rule("DEFAULT/NOT NULL 절 순서",
                    Pattern.compile("NOT\\s+NULL\\s+DEFAULT", Pattern.CASE_INSENSITIVE),
                    "오라클 column_definition 은 DEFAULT 가 inline constraint 보다 엄격히 앞이다. "
                            + "역순은 ORA-00907. 포스트그레스는 두 순서를 모두 허용한다."),
            new Rule("BIGINT 타입",
                    Pattern.compile("\\bBIGINT\\b", Pattern.CASE_INSENSITIVE),
                    "오라클에 BIGINT 는 없다 (ORA-00902). NUMBER(19) 를 쓸 것. "
                            + "쌍둥이 파일에서 가장 흔한 타입이라 복붙 위험이 크다."),
            new Rule("포스트그레스 전용 타입",
                    Pattern.compile("\\b(BOOLEAN(?!\\s*(;|:=))|SERIAL|BIGSERIAL|TEXT|BYTEA|JSONB)\\b",
                            Pattern.CASE_INSENSITIVE),
                    "오라클에 없는 타입이다. NUMBER(1) / IDENTITY / CLOB / BLOB 으로 쓸 것. "
                            + "PL/SQL 변수 선언(l_x BOOLEAN;)은 유효하므로 제외한다."),
            new Rule("NOW() 함수",
                    Pattern.compile("\\bNOW\\s*\\(", Pattern.CASE_INSENSITIVE),
                    "오라클에 NOW() 는 없다 (ORA-00904). SYSTIMESTAMP / SYSDATE 를 쓸 것."),
            new Rule("TRUE/FALSE 리터럴",
                    Pattern.compile("\\b(TRUE|FALSE)\\b", Pattern.CASE_INSENSITIVE),
                    "오라클 SQL 은 23ai 이전에 불리언 리터럴이 없다. 1 / 0 을 쓸 것."),
            new Rule("ALTER COLUMN",
                    Pattern.compile("\\bALTER\\s+COLUMN\\b", Pattern.CASE_INSENSITIVE),
                    "오라클은 ALTER TABLE ... MODIFY 를 쓴다. ALTER COLUMN 은 포스트그레스 문법이다."),
            new Rule("ADD COLUMN",
                    Pattern.compile("\\bADD\\s+COLUMN\\b", Pattern.CASE_INSENSITIVE),
                    "오라클의 ALTER TABLE 에는 COLUMN 키워드가 없다. ADD col TYPE 또는 ADD (col TYPE)."),
            new Rule("MODIFY COLUMN / CHANGE COLUMN",
                    Pattern.compile("\\b(MODIFY|CHANGE)\\s+COLUMN\\b", Pattern.CASE_INSENSITIVE),
                    "오라클은 MODIFY 뒤에 COLUMN 을 쓰지 않는다."),
            new Rule("IF (NOT) EXISTS",
                    Pattern.compile("\\bIF\\s+(NOT\\s+)?EXISTS\\b", Pattern.CASE_INSENSITIVE),
                    "오라클 DDL 은 IF EXISTS 를 지원하지 않는다."),
            new Rule("LIMIT / OFFSET",
                    Pattern.compile("\\b(LIMIT|OFFSET)\\s+\\d", Pattern.CASE_INSENSITIVE),
                    "오라클은 FETCH FIRST n ROWS ONLY 를 쓴다."),
            new Rule("백틱 식별자",
                    Pattern.compile("`"),
                    "MySQL 문법이다."));

    private static final Pattern LINE_COMMENT = Pattern.compile("--[^\\n]*");
    private static final Pattern BLOCK_COMMENT = Pattern.compile("/\\*.*?\\*/", Pattern.DOTALL);
    private static final Pattern STRING_LITERAL = Pattern.compile("'(?:''|[^'])*'", Pattern.DOTALL);

    /**
     * 주석과 문자열 리터럴을 <b>같은 길이의 공백으로</b> 치환한다.
     *
     * <p>지우지 않고 공백으로 바꾸는 이유는 문자 위치를 보존하기 위해서다. 위반 위치를 줄 번호로
     * 보고해야 하는데, 길이가 달라지면 원문과 어긋난다. 개행은 그대로 둔다.
     */
    private static String scrub(String source) {
        StringBuilder sb = new StringBuilder(source);
        for (Pattern p : List.of(BLOCK_COMMENT, LINE_COMMENT, STRING_LITERAL)) {
            Matcher m = p.matcher(sb.toString());
            while (m.find()) {
                for (int i = m.start(); i < m.end(); i++) {
                    if (sb.charAt(i) != '\n') {
                        sb.setCharAt(i, ' ');
                    }
                }
            }
        }
        return sb.toString();
    }

    private static final Pattern UPDATE_STMT = Pattern.compile("\\bUPDATE\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern FROM_KEYWORD = Pattern.compile("\\bFROM\\b", Pattern.CASE_INSENSITIVE);

    /**
     * 문장 수준 {@code UPDATE ... FROM} 을 찾는다. 오라클은 이 문법이 없고 상관 서브쿼리나
     * {@code MERGE} 를 써야 한다. 포스트그레스 쌍둥이(V20)가 실제로 이 형태다.
     *
     * <p>정규식만으로는 안 된다 — {@code SET x = (SELECT ... FROM ...)} 는 <b>정상</b>이고
     * 오라클 V20 이 바로 그 형태다. 괄호 깊이 0 에서 나타난 {@code FROM} 만 위반이다.
     * (초판은 이 구분을 못 해 정상 SQL 을 위반으로 잡았다.)
     */
    private static List<int[]> findStatementLevelUpdateFrom(String scrubbed) {
        List<int[]> hits = new ArrayList<>();
        Matcher u = UPDATE_STMT.matcher(scrubbed);
        while (u.find()) {
            int depth = 0;
            for (int i = u.end(); i < scrubbed.length(); i++) {
                char c = scrubbed.charAt(i);
                if (c == '(') {
                    depth++;
                } else if (c == ')') {
                    depth--;
                } else if (c == ';' && depth == 0) {
                    break;
                } else if (depth == 0 && (c == 'F' || c == 'f')) {
                    Matcher f = FROM_KEYWORD.matcher(scrubbed);
                    if (f.find(i) && f.start() == i) {
                        hits.add(new int[] {u.start(), f.start()});
                        break;
                    }
                }
            }
        }
        return hits;
    }

    private static int lineOf(String text, int offset) {
        return (int) text.substring(0, offset).chars().filter(c -> c == '\n').count() + 1;
    }

    private static List<Path> sqlFiles(Path dir) throws IOException {
        try (Stream<Path> paths = Files.walk(dir)) {
            return paths.filter(f -> f.toString().endsWith(".sql")).sorted().toList();
        }
    }

    @Test
    @DisplayName("오라클 방언 파일에 오라클이 거부하는 문법이 없다")
    void 오라클_문법_위반이_없다() throws IOException {
        List<Path> files = sqlFiles(ORACLE);
        assertThat(files)
                .as("오라클 마이그레이션을 찾지 못했다면 경로(%s)가 잘못됐을 가능성이 크다", ORACLE)
                .hasSizeGreaterThanOrEqualTo(MIN_FILES);

        List<Violation> violations = new ArrayList<>();
        for (Path f : files) {
            String original = Files.readString(f);
            String scrubbed = scrub(original);
            for (Rule rule : RULES) {
                Matcher m = rule.pattern().matcher(scrubbed);
                while (m.find()) {
                    int line = lineOf(scrubbed, m.start());
                    violations.add(new Violation(f, line, rule.name(),
                            m.group().replaceAll("\\s+", " ")));
                }
            }
            for (int[] hit : findStatementLevelUpdateFrom(scrubbed)) {
                violations.add(new Violation(f, lineOf(scrubbed, hit[1]), "UPDATE ... FROM",
                        "오라클은 UPDATE ... FROM 을 지원하지 않는다 — 상관 서브쿼리나 MERGE 를 쓸 것"));
            }
        }

        assertThat(violations)
                .as("""
                        오라클 방언 마이그레이션에 오라클이 거부하는 문법이 있다 (UG-292).

                        이 부류의 결함은 온프레미스 납품 시점에야 드러난다 — CI 도 로컬도 postgresql 로
                        돌기 때문이다. 마이그레이션이 실패하면 스키마가 그 버전 이전에 멈춘다.

                        규칙별 이유:
                        %s""".formatted(RULES.stream()
                        .map(r -> "  - %s: %s".formatted(r.name(), r.why()))
                        .reduce((a, b) -> a + "\n" + b)
                        .orElse("")))
                .isEmpty();
    }

    @Test
    @DisplayName("여러 줄로 나눠 쓴 컬럼 정의도 검사한다 — 이 검사 자신의 회귀 테스트")
    void 여러줄_정의도_잡는다() {
        // 초판은 파일을 줄 단위로 쪼개 매칭해서, 아래 형태가 규칙 전부를 빠져나갔다.
        // 고치려던 결함과 똑같은 SQL 인데도 통과했다.
        String multiline = """
                CREATE TABLE t (
                    is_deleted   NUMBER(1)
                                 NOT NULL
                                 DEFAULT 0
                );""";

        Rule clauseOrder = RULES.get(0);
        assertThat(clauseOrder.pattern().matcher(scrub(multiline)).find())
                .as("줄바꿈으로 나뉜 NOT NULL / DEFAULT 를 잡지 못하면 이 가드는 의미가 없다")
                .isTrue();
    }

    @Test
    @DisplayName("주석과 문자열 리터럴은 검사하지 않되 줄 번호는 보존한다")
    void 주석은_검사하지_않는다() {
        // oracle/V8:1 의 주석에 BIGINT 가 실제로 들어 있다. 이걸 위반으로 잡으면 오탐이다.
        String source = """
                -- match_history: face_feature_id (BIGINT FK) 제거
                /* BOOLEAN 도 여기서는 설명일 뿐이다 */
                CREATE TABLE t (
                    note VARCHAR2(50) DEFAULT 'NOW() 형태로 쓰지 말 것' NOT NULL,
                    flag NUMBER(1) NOT NULL DEFAULT 0
                );""";
        String scrubbed = scrub(source);

        assertThat(RULES.stream()
                .filter(r -> r.pattern().matcher(scrubbed).find())
                .map(Rule::name))
                .as("주석·문자열 안의 낱말을 위반으로 세면 안 된다")
                .containsExactly("DEFAULT/NOT NULL 절 순서");

        // 공백으로 치환하므로 위치가 밀리지 않는다 — 5행의 위반이 5행으로 보고돼야 한다
        Matcher m = RULES.get(0).pattern().matcher(scrubbed);
        assertThat(m.find()).isTrue();
        assertThat(lineOf(scrubbed, m.start())).isEqualTo(5);
    }

    @Test
    @DisplayName("UPDATE ... FROM 은 문장 수준만 잡고 상관 서브쿼리는 통과시킨다")
    void UPDATE_FROM_은_괄호_깊이를_본다() {
        // 초판은 정규식만 써서 오라클 V20 의 정상 SQL 을 위반으로 잡았다. 두 방향을 모두 못박는다.
        String 상관서브쿼리 = """
                UPDATE match_history mh
                SET feature_seq = (
                    SELECT ff.face_feature_id
                    FROM face_feature ff
                    WHERE ff.feature_id = mh.feature_id
                    AND ROWNUM = 1
                )
                WHERE mh.feature_type = 'FACE';""";
        String 문장수준 = """
                UPDATE match_history mh
                SET feature_seq = ff.face_feature_id
                FROM face_feature ff
                WHERE mh.feature_type = 'FACE';""";

        assertThat(findStatementLevelUpdateFrom(scrub(상관서브쿼리)))
                .as("오라클 V20 이 쓰는 정상 형태다 — 위반으로 잡으면 오탐이다")
                .isEmpty();
        assertThat(findStatementLevelUpdateFrom(scrub(문장수준)))
                .as("포스트그레스 V20 이 쓰는 형태다 — 오라클로 복사해 오면 잡아야 한다")
                .hasSize(1);
    }

    @Test
    @DisplayName("두 방언의 마이그레이션 버전이 정확히 일치한다")
    void 방언_버전이_일치한다() throws IOException {
        // 한쪽에만 있는 버전이 생기면 그 환경에서만 스키마가 어긋난다. 파일이 늘 때 쌍으로
        // 추가하는 것을 강제한다. 다만 이 검사는 파일 "이름" 만 본다 — 이름이 같고 내용이
        // 어긋나는 것은 못 잡는다 (실제로 V8·V14 에 그런 차이가 있다, UG-297).
        List<String> oracle = sqlFiles(ORACLE).stream().map(p -> p.getFileName().toString()).toList();
        List<String> postgresql =
                sqlFiles(POSTGRESQL).stream().map(p -> p.getFileName().toString()).toList();

        assertThat(oracle)
                .as("oracle 쪽에만 있거나 빠진 마이그레이션이 있다")
                .containsExactlyInAnyOrderElementsOf(postgresql);
    }
}
