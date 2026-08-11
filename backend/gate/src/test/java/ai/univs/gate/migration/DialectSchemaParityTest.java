package ai.univs.gate.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 두 방언 마이그레이션이 <b>같은 스키마</b>를 만드는지 (UG-297).
 *
 * <p>{@code OracleMigrationSyntaxTest.방언_버전이_일치한다} 는 파일 <b>이름</b>만 비교한다.
 * 이름이 같고 내용이 어긋나는 것은 못 잡는데, 실제로 어긋난 곳이 있었다 — postgresql/V8 은
 * {@code created_at ... DEFAULT NOW()} 인데 oracle/V8 에는 기본값이 없다.
 *
 * <p>이 부류는 <b>납품 시점에야 드러난다.</b> CI 도 로컬도 postgresql 로만 돌아서 오라클 파일은
 * 아무도 실행하지 않는다. 게다가 이건 문법 오류가 아니라 조용한 의미 차이라, 실제 오라클로
 * 돌려 봐도 마이그레이션은 성공하고 나중에 데이터가 달라진다.
 *
 * <p><b>무엇을 비교하는가.</b> 파일 쌍마다 그 파일이 선언하는 컬럼 이름·NULL 허용성·기본값을
 * 뽑아 대조한다. 타입은 보지 않는다 — {@code BIGINT} ↔ {@code NUMBER(19)},
 * {@code BOOLEAN} ↔ {@code NUMBER(1)} 은 의도된 대응이고, 그것까지 맞추려면 타입 사전이
 * 필요한데 그 사전이 틀리면 오탐만 늘어난다.
 *
 * <p><b>누적 재생이 아니라 파일 단위 비교다.</b> 마이그레이션을 처음부터 적용해 최종 스키마를
 * 만들어 비교하는 방법도 있지만, 그러려면 rename·drop 추적이 정확해야 하고 한 곳이 틀리면
 * 이후 전부가 어긋나 보인다. 파일 단위로 보면 어긋난 <b>파일 이름</b>이 그대로 실패 메시지에
 * 나온다.
 *
 * <p><b>이 테스트가 보지 않는 것.</b> 재실행 안전성은 비교 대상이 아니다 — postgresql/V14 는
 * {@code ADD COLUMN IF NOT EXISTS}, oracle/V14 는 그냥 {@code ADD} 다. 오라클 DDL 에
 * {@code IF NOT EXISTS} 가 없으니 문법상 다른 수가 없고, 만드는 스키마는 같다. Flyway 가 적용된
 * 버전을 다시 돌리지 않으므로 실질 차이도 없다 — 손으로 SQL 을 두 번 실행할 때만 갈린다.
 * 인덱스·제약 이름도 보지 않는다 (방언별 명명 규칙이 달라 오탐이 크다).
 *
 * <p><b>초록이 "오라클 납품이 된다" 는 뜻은 아니다.</b> {@code OracleMigrationSyntaxTest} 와
 * 마찬가지로, 실제 오라클 인스턴스에서 V1~V22 를 끝까지 돌려 보는 것을 대체하지 못한다.
 *
 * <p><b>알려진 차이는 아래 {@link #ALLOWED} 에 이유와 함께 적는다.</b> SQL 파일에 주석으로
 * 남기지 않는 이유는 Flyway 가 파일 내용을 체크섬으로 잡기 때문이다 — 이미 적용된 파일은
 * 주석 한 줄만 고쳐도 기존 환경에서 {@code validate} 가 실패한다
 * ({@code validate-on-migrate} 는 설정하지 않았으므로 기본값 true 다).
 */
@DisplayName("UG-297: 두 방언 마이그레이션의 스키마 대조")
class DialectSchemaParityTest {

    /** 이 서비스들의 {@code db/migration/{postgresql,oracle}} 를 모두 본다. */
    private static final List<String> SERVICES = List.of("gate", "face", "match", "palm");

    /**
     * 판단을 마친 차이. 키는 {@code 서비스/파일 테이블.컬럼 항목}, 값은 그렇게 두는 이유다.
     *
     * <p>여기 없는 차이가 생기면 테스트가 실패한다. 새 차이를 발견하면 <b>고치거나</b>, 의도된
     * 것이면 이유를 적어 여기 넣는다. "일단 넣고 보기" 를 막으려고 이유를 필수로 뒀다.
     */
    private static final Map<String, String> ALLOWED = Map.of(
            "gate/V8__add_palm_feature.sql palm_feature.created_at default",
            "postgresql 은 DEFAULT NOW(), oracle 은 기본값 없음. 둘 다 NOT NULL 이라 오라클에서는 "
                    + "값을 넣지 않으면 삽입이 실패한다. 실무상 무해하다 — palm_feature 는 V21 에서 "
                    + "biometric_feature 로 통합됐고 어떤 엔티티도 이 테이블에 매핑돼 있지 않다. "
                    + "즉 애플리케이션이 이 테이블에 INSERT 하지 않는다. 신규 오라클 설치에서는 "
                    + "V8~V21 이 연달아 돌아 테이블이 빈 채로 통합되므로 삽입 자체가 없다. "
                    + "파일을 고치지 않는 이유는 Flyway 체크섬이다 (클래스 javadoc 참고).",

            "gate/V8__add_palm_feature.sql palm_feature.updated_at default",
            "created_at 과 같다.",

            "palm/V1__init.sql palm_history.result default",
            "oracle 만 DEFAULT 0 을 갖는다 (postgresql 은 기본값 없이 NOT NULL). 방향이 반대라 "
                    + "오라클 쪽이 더 관대하다. Hibernate 는 @DynamicInsert 가 없으면 매핑된 컬럼을 "
                    + "전부 INSERT 문에 싣기 때문에 이 기본값은 어차피 발동하지 않는다 — "
                    + "그 전제를 dynamic_insert_는_없다() 가 지킨다.",

            "palm/V1__init.sql palm_history.check_liveness default",
            "palm_history.result 와 같다.",

            "palm/V1__init.sql palm_liveness.performed default",
            "palm_history.result 와 같다.",

            "palm/V1__init.sql palm_liveness.passed default",
            "palm_history.result 와 같다.");

    // ─────────────────────────────────────────────────────────────────────────
    // 테스트
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("같은 버전의 두 방언 파일이 같은 컬럼·NULL 허용성·기본값을 선언한다")
    void 방언_내용이_일치한다() throws IOException {
        List<String> differences = new ArrayList<>();
        int compared = 0;

        for (String service : SERVICES) {
            Path base = 서비스_마이그레이션_루트(service);
            for (Path pg : sqlFiles(base.resolve("postgresql"))) {
                Path oracle = base.resolve("oracle").resolve(pg.getFileName());
                if (!Files.exists(oracle)) {
                    // 이름 대응은 OracleMigrationSyntaxTest 가 지킨다. 여기서는 비교만 건너뛴다.
                    continue;
                }
                compared++;
                differences.addAll(비교한다(
                        "%s/%s".formatted(service, pg.getFileName()),
                        parse(Files.readString(pg)),
                        parse(Files.readString(oracle))));
            }
        }

        assertThat(compared)
                .as("비교한 파일 쌍이 없다면 경로 해석이 깨진 것이다 — 이 테스트는 조용히 통과하면 안 된다")
                .isGreaterThanOrEqualTo(27);

        assertThat(differences)
                .as("""
                        두 방언이 서로 다른 스키마를 만든다 (UG-297).

                        같은 파일명이라 눈으로는 대응돼 보이지만 내용이 어긋난다. postgresql 로만
                        돌아가는 CI 는 이것을 절대 잡지 못하고, 오라클로 실제 실행해도 마이그레이션
                        자체는 성공한다 — 나중에 데이터가 달라질 뿐이다.

                        의도된 차이라면 ALLOWED 에 이유와 함께 등록할 것.""")
                .isEmpty();
    }

    /**
     * {@code DEFAULT ''} 컬럼 목록을 못박는다.
     *
     * <p>오라클은 빈 문자열을 NULL 로 취급하므로, 같은 {@code DEFAULT ''} 가 postgresql 에서는
     * 빈 문자열, 오라클에서는 NULL 을 넣는다. 파일 내용이 <b>같기 때문에</b> 위 대조로는 절대
     * 드러나지 않는 차이다.
     *
     * <p>지금은 무해하다 — {@link #dynamic_insert_는_없다()} 가 지키는 전제 때문에 이 기본값은
     * 발동하지 않는다. 그래도 목록을 고정해 두는 이유는, 새 컬럼이 이 부류로 추가될 때
     * 오라클에서만 NULL 이 된다는 점을 <b>추가하는 시점에</b> 알게 하기 위해서다.
     */
    @Test
    @DisplayName("빈 문자열 기본값 컬럼 목록이 그대로다 — 오라클에서는 NULL 이 된다")
    void 빈문자열_기본값_목록이_고정돼_있다() throws IOException {
        Set<String> found = new TreeSet<>();
        for (String service : SERVICES) {
            Path oracle = 서비스_마이그레이션_루트(service).resolve("oracle");
            for (Path f : sqlFiles(oracle)) {
                Effects e = parse(Files.readString(f));
                e.declared.forEach((key, spec) -> {
                    if ("''".equals(spec.defaultValue())) {
                        found.add(service + "." + key);
                    }
                });
            }
        }

        assertThat(found)
                .as("""
                        오라클은 '' 를 NULL 로 취급한다. postgresql 에서 빈 문자열이 들어가는 자리에
                        오라클에서는 NULL 이 들어간다는 뜻이다.

                        지금 이 목록의 컬럼들은 전부 NULL 허용이라 제약 위반은 나지 않고,
                        Hibernate 가 컬럼을 생략하지 않으므로 기본값 자체가 발동하지도 않는다
                        (dynamic_insert_는_없다() 참고).

                        목록이 늘었다면 새 컬럼이 이 부류에 들어온 것이다. 그 컬럼을 읽는 코드가
                        NULL 을 견디는지 확인하고 목록을 갱신할 것 — 코드가 StringUtils.hasText 를
                        쓰면 차이가 없고, isEmpty()/equals("") 로 분기하면 오라클에서만 달라진다.""")
                .containsExactlyInAnyOrderElementsOf(빈문자열_기본값_기준선());
    }

    /**
     * {@code @DynamicInsert}/{@code @DynamicUpdate} 가 없어야 한다.
     *
     * <p>위 두 테스트가 "무해하다" 고 판정한 근거가 전부 이 전제 위에 있다. Hibernate 는 기본적으로
     * 매핑된 컬럼을 <b>전부</b> INSERT 문에 싣는다. 그래서 컬럼 기본값은 애초에 발동하지 않고,
     * 두 방언의 기본값 차이도 실제 데이터에 영향을 주지 않는다.
     *
     * <p>{@code @DynamicInsert} 를 붙이면 그 전제가 무너진다 — null 인 필드가 INSERT 문에서
     * 빠지고 그 순간 컬럼 기본값이 발동해, postgresql 은 빈 문자열을 오라클은 NULL 을 넣는다.
     * 성능 최적화로 흔히 붙이는 애노테이션이라 무심코 들어오기 쉽다.
     */
    @Test
    @DisplayName("@DynamicInsert / @DynamicUpdate 가 없다 — 위 두 판정의 전제다")
    void dynamic_insert_는_없다() throws IOException {
        List<String> hits = new ArrayList<>();
        for (String service : SERVICES) {
            Path src = 서비스_루트(service).resolve("src/main/java");
            if (!Files.isDirectory(src)) {
                continue;
            }
            try (Stream<Path> paths = Files.walk(src)) {
                for (Path f : paths.filter(p -> p.toString().endsWith(".java")).toList()) {
                    if (DYNAMIC_WRITE.matcher(Files.readString(f)).find()) {
                        hits.add(service + "/" + src.relativize(f));
                    }
                }
            }
        }

        assertThat(hits)
                .as("""
                        @DynamicInsert 가 붙으면 null 필드가 INSERT 문에서 빠지고 컬럼 기본값이
                        발동한다. 그 순간 DEFAULT '' 컬럼이 postgresql 에서는 빈 문자열,
                        오라클에서는 NULL 이 되어 두 환경의 동작이 갈린다 (UG-297).

                        정말 필요하다면 붙이되, 빈문자열_기본값_목록이_고정돼_있다() 가 나열하는
                        컬럼들을 먼저 점검하고 이 테스트를 그에 맞게 고칠 것.""")
                .isEmpty();
    }

    /**
     * 완전수식 형태({@code @org.hibernate.annotations.DynamicInsert})까지 잡는다.
     *
     * <p>초판은 {@code contains("@DynamicInsert")} 였는데, 그러면 패키지를 앞에 붙여 쓴 형태를
     * 놓친다 — 변이 심기에서 그 형태가 그대로 살아남았다. import 를 쓰는 게 보통이지만
     * 완전수식도 유효한 자바다.
     */
    private static final Pattern DYNAMIC_WRITE =
            Pattern.compile("@(?:[\\w.]+\\.)?Dynamic(Insert|Update)\\b");

    /** 기준선을 별도 메서드로 둔다 — 실패 메시지에서 기대값이 한눈에 보이게 하려고. */
    private static Set<String> 빈문자열_기본값_기준선() {
        return new TreeSet<>(List.of(
                // 파일이 선언한 시점의 이름이다 — V7 이 users → face_feature 로, V21 이
                // match_history 의 face_* 를 feature_* 로 바꾸지만 이 테스트는 파일 단위로
                // 보므로 개명 이후 이름이 아니라 선언 당시 이름이 남는다.
                "gate.users.description",
                "gate.users.face_image_path",
                "gate.users.username",
                "gate.companies.business_number",
                "gate.companies.business_type",
                "gate.companies.company_name",
                "gate.companies.employee_count",
                "gate.companies.main_service",
                "gate.companies.manager_mail",
                "gate.companies.manager_name",
                "gate.companies.manager_number",
                "gate.match_history.face_id",
                "gate.match_history.face_image_path",
                "gate.match_history.failure_reason",
                "gate.match_history.failure_type",
                "gate.match_history.match_face_image_path",
                "gate.match_history.user_description",
                "gate.match_history.username",
                "gate.projects.project_description",
                "face.face_history.face_id",
                "face.face_history.failure_message",
                "face.face_history.transaction_uuid",
                "face.face_match.face_id",
                "palm.palm_history.failure_message",
                "palm.palm_history.palm_id",
                "palm.palm_history.transaction_uuid",
                "palm.palm_match.palm_id"));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 비교
    // ─────────────────────────────────────────────────────────────────────────

    private static List<String> 비교한다(String file, Effects pg, Effects oracle) {
        List<String> out = new ArrayList<>();

        for (String key : union(pg.declared.keySet(), oracle.declared.keySet())) {
            ColumnSpec a = pg.declared.get(key);
            ColumnSpec b = oracle.declared.get(key);
            if (a == null || b == null) {
                report(out, file, key, "선언", a == null ? "없음" : a.toString(),
                        b == null ? "없음" : b.toString());
                continue;
            }
            if (a.nullable() != b.nullable()) {
                report(out, file, key, "nullable", String.valueOf(a.nullable()),
                        String.valueOf(b.nullable()));
            }
            if (!java.util.Objects.equals(a.defaultValue(), b.defaultValue())) {
                report(out, file, key, "default", String.valueOf(a.defaultValue()),
                        String.valueOf(b.defaultValue()));
            }
        }

        비교한다(out, file, "변경", pg.modified, oracle.modified);
        비교한다(out, file, "삭제", asMap(pg.dropped), asMap(oracle.dropped));
        비교한다(out, file, "테이블생성", asMap(pg.createdTables), asMap(oracle.createdTables));
        비교한다(out, file, "테이블삭제", asMap(pg.droppedTables), asMap(oracle.droppedTables));
        비교한다(out, file, "컬럼이름변경", asMap(pg.renamed), asMap(oracle.renamed));
        return out;
    }

    private static void 비교한다(
            List<String> out, String file, String kind, Map<String, String> a, Map<String, String> b) {
        for (String key : union(a.keySet(), b.keySet())) {
            String x = a.get(key);
            String y = b.get(key);
            if (!java.util.Objects.equals(x, y)) {
                report(out, file, key, kind, x == null ? "없음" : x, y == null ? "없음" : y);
            }
        }
    }

    private static void report(
            List<String> out, String file, String key, String kind, String pg, String oracle) {
        String allowKey = "%s %s %s".formatted(file, key, kind);
        String reason = ALLOWED.get(allowKey);
        if (reason != null) {
            return;
        }
        out.add("%s  |  postgresql=%s  oracle=%s%n      (의도된 것이면 ALLOWED 에 \"%s\" 로 등록)"
                .formatted(allowKey, pg, oracle, allowKey));
    }

    private static Set<String> union(Set<String> a, Set<String> b) {
        Set<String> all = new TreeSet<>(a);
        all.addAll(b);
        return all;
    }

    private static Map<String, String> asMap(Set<String> values) {
        Map<String, String> m = new TreeMap<>();
        values.forEach(v -> m.put(v, "있음"));
        return m;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 경로
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * 모노레포 루트를 찾는다. 테스트의 작업 디렉터리는 {@code backend/gate} 다.
     *
     * <p>모듈 밖을 보는 테스트라 경로가 어긋나면 조용히 0건을 비교하게 된다. 그래서 못 찾으면
     * 통과가 아니라 예외로 끝낸다.
     */
    private static Path 모노레포_루트() {
        for (Path p = Path.of("").toAbsolutePath(); p != null; p = p.getParent()) {
            if (Files.isDirectory(p.resolve("backend/gate/src/main/resources/db/migration"))) {
                return p;
            }
        }
        throw new IllegalStateException(
                "모노레포 루트를 찾지 못했다. 작업 디렉터리=" + Path.of("").toAbsolutePath());
    }

    private static Path 서비스_루트(String service) {
        return 모노레포_루트().resolve("backend").resolve(service);
    }

    private static Path 서비스_마이그레이션_루트(String service) {
        Path p = 서비스_루트(service).resolve("src/main/resources/db/migration");
        if (!Files.isDirectory(p)) {
            throw new IllegalStateException("마이그레이션 디렉터리가 없다: " + p);
        }
        return p;
    }

    private static List<Path> sqlFiles(Path dir) throws IOException {
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (Stream<Path> paths = Files.list(dir)) {
            return paths.filter(f -> f.toString().endsWith(".sql")).sorted().toList();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 파서
    // ─────────────────────────────────────────────────────────────────────────

    private record ColumnSpec(boolean nullable, String defaultValue) {
        @Override
        public String toString() {
            return "%s%s".formatted(nullable ? "NULL" : "NOT NULL",
                    defaultValue == null ? "" : " DEFAULT " + defaultValue);
        }
    }

    /** 한 파일이 선언하는 스키마 변경. */
    private static final class Effects {
        final Map<String, ColumnSpec> declared = new LinkedHashMap<>();
        final Map<String, String> modified = new LinkedHashMap<>();
        final Set<String> dropped = new LinkedHashSet<>();
        final Set<String> createdTables = new LinkedHashSet<>();
        final Set<String> droppedTables = new LinkedHashSet<>();
        final Set<String> renamed = new LinkedHashSet<>();
    }

    private static final String IDENT =
            "(?:\"[^\"]+\"|[A-Za-z_][A-Za-z0-9_$#]*)(?:\\.(?:\"[^\"]+\"|[A-Za-z_][A-Za-z0-9_$#]*))?";

    /**
     * 오라클 쪽이 {@code EXECUTE IMMEDIATE '<DDL>'} 로 감싼 DDL 을 꺼낸다.
     *
     * <p>oracle/V9 가 {@code DROP TABLE IF EXISTS} 대신 PL/SQL 블록으로 같은 일을 한다. 문자열
     * 리터럴은 뒤에서 통째로 지워지므로, 꺼내지 않으면 "postgresql 만 테이블을 지운다" 는
     * 오탐이 난다.
     */
    private static final Pattern EXECUTE_IMMEDIATE =
            Pattern.compile("EXECUTE\\s+IMMEDIATE\\s+'((?:''|[^'])*)'", Pattern.CASE_INSENSITIVE);

    static Effects parse(String source) {
        Effects effects = new Effects();
        for (String statement : statements(unwrapExecuteImmediate(source))) {
            apply(effects, statement);
        }
        return effects;
    }

    private static String unwrapExecuteImmediate(String source) {
        Matcher m = EXECUTE_IMMEDIATE.matcher(source);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            // 앞뒤로 세미콜론을 붙여 독립 문장으로 만든다. 붙이지 않으면 감싸고 있던
            // BEGIN 과 한 문장으로 이어져 "^DROP TABLE" 앵커에 걸리지 않는다.
            m.appendReplacement(sb,
                    Matcher.quoteReplacement("; " + m.group(1).replace("''", "'") + " ;"));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /** 주석과 문자열 리터럴을 지우고 세미콜론으로 자른다. */
    private static List<String> statements(String source) {
        String scrubbed = source
                .replaceAll("(?s)/\\*.*?\\*/", " ")
                .replaceAll("--[^\\n]*", " ");

        List<String> out = new ArrayList<>();
        StringBuilder buf = new StringBuilder();
        int depth = 0;
        for (int i = 0; i < scrubbed.length(); i++) {
            char c = scrubbed.charAt(i);
            if (c == '\'') {
                int j = i + 1;
                while (j < scrubbed.length()) {
                    if (scrubbed.charAt(j) == '\'') {
                        if (j + 1 < scrubbed.length() && scrubbed.charAt(j + 1) == '\'') {
                            j += 2;
                            continue;
                        }
                        break;
                    }
                    j++;
                }
                buf.append(scrubbed, i, Math.min(j + 1, scrubbed.length()));
                i = j;
                continue;
            }
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
            }
            if (c == ';' && depth == 0) {
                out.add(normalize(buf.toString()));
                buf.setLength(0);
                continue;
            }
            buf.append(c);
        }
        out.add(normalize(buf.toString()));
        out.removeIf(String::isEmpty);
        return out;
    }

    private static String normalize(String s) {
        return s.trim().replaceAll("\\s+", " ");
    }

    private static final Pattern CREATE_TABLE = Pattern.compile(
            "^CREATE\\s+TABLE\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?(" + IDENT + ")\\s*\\((.*)\\)$",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern DROP_TABLE = Pattern.compile(
            "^DROP\\s+TABLE\\s+(?:IF\\s+EXISTS\\s+)?(" + IDENT + ")", Pattern.CASE_INSENSITIVE);
    private static final Pattern ALTER_TABLE = Pattern.compile(
            "^ALTER\\s+TABLE\\s+(" + IDENT + ")\\s+(.*)$",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private static void apply(Effects effects, String statement) {
        Matcher m = CREATE_TABLE.matcher(statement);
        if (m.matches()) {
            String table = ident(m.group(1));
            effects.createdTables.add(table);
            for (String part : splitTopLevel(m.group(2))) {
                column(part).ifPresent(c ->
                        effects.declared.put(table + "." + c.name, c.spec));
            }
            return;
        }
        m = DROP_TABLE.matcher(statement);
        if (m.find()) {
            effects.droppedTables.add(ident(m.group(1)));
            return;
        }
        m = ALTER_TABLE.matcher(statement);
        if (m.matches()) {
            alter(effects, ident(m.group(1)), m.group(2).trim());
        }
    }

    private static final Pattern RENAME_COLUMN = Pattern.compile(
            "^RENAME\\s+COLUMN\\s+(" + IDENT + ")\\s+TO\\s+(" + IDENT + ")", Pattern.CASE_INSENSITIVE);
    private static final Pattern RENAME_TABLE =
            Pattern.compile("^RENAME\\s+TO\\s+(" + IDENT + ")", Pattern.CASE_INSENSITIVE);
    private static final Pattern ADD_CONSTRAINT = Pattern.compile(
            "^ADD\\s+(CONSTRAINT|PRIMARY|UNIQUE|FOREIGN|CHECK)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern ADD_COLUMNS = Pattern.compile(
            "^ADD\\s+(?:COLUMN\\s+)?(?:IF\\s+NOT\\s+EXISTS\\s+)?(.*)$",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern DROP_COLUMNS = Pattern.compile(
            "^DROP\\s+(?:COLUMN\\s+)?(?:IF\\s+EXISTS\\s+)?(.*)$",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    /** postgresql {@code ALTER COLUMN}, oracle {@code MODIFY} — 둘 다 기존 컬럼 변경이다. */
    private static final Pattern MODIFY_COLUMNS = Pattern.compile(
            "^(?:ALTER\\s+COLUMN|ALTER|MODIFY)\\s+(.*)$",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private static void alter(Effects effects, String table, String body) {
        Matcher m = RENAME_COLUMN.matcher(body);
        if (m.find()) {
            effects.renamed.add("%s:%s->%s".formatted(table, ident(m.group(1)), ident(m.group(2))));
            return;
        }
        m = RENAME_TABLE.matcher(body);
        if (m.find()) {
            effects.renamed.add("%s=>%s".formatted(table, ident(m.group(1))));
            return;
        }
        if (ADD_CONSTRAINT.matcher(body).find() || body.toUpperCase(Locale.ROOT).startsWith("DROP CONSTRAINT")) {
            // 제약은 이 테스트의 범위가 아니다 (이름 규칙이 방언별로 달라 오탐이 크다).
            return;
        }
        m = DROP_COLUMNS.matcher(body);
        if (m.matches()) {
            for (String name : splitTopLevel(strip(m.group(1)))) {
                effects.dropped.add(table + "." + ident(name.replaceAll("(?i)\\bCASCADE\\b", "").trim()));
            }
            return;
        }
        m = ADD_COLUMNS.matcher(body);
        if (m.matches()) {
            for (String part : splitTopLevel(strip(m.group(1)))) {
                column(part).ifPresent(c -> effects.declared.put(table + "." + c.name, c.spec));
            }
            return;
        }
        m = MODIFY_COLUMNS.matcher(body);
        if (m.matches()) {
            for (String part : splitTopLevel(strip(m.group(1)))) {
                Matcher one = Pattern.compile("^(" + IDENT + ")\\s*(.*)$", Pattern.DOTALL).matcher(part.trim());
                if (!one.matches()) {
                    continue;
                }
                String action = normalize(one.group(2));
                String upper = action.toUpperCase(Locale.ROOT);
                List<String> notes = new ArrayList<>();
                if (upper.contains("SET NOT NULL") || upper.matches(".*\\bNOT NULL\\b.*")) {
                    notes.add("NOT NULL");
                } else if (upper.contains("DROP NOT NULL")) {
                    notes.add("NULL");
                }
                Matcher d = Pattern.compile("(?:SET\\s+)?DEFAULT\\s+(.+?)(?=\\s+NOT\\s+NULL\\b|$)",
                        Pattern.CASE_INSENSITIVE).matcher(action);
                if (d.find()) {
                    notes.add("DEFAULT " + defaultValue(d.group(1)));
                }
                if (upper.contains("DROP DEFAULT")) {
                    notes.add("DEFAULT 제거");
                }
                if (!notes.isEmpty()) {
                    effects.modified.put(table + "." + ident(one.group(1)), String.join(" ", notes));
                }
            }
        }
    }

    /** {@code ADD (a ..., b ...)} 처럼 통째로 감싼 괄호를 벗긴다. */
    private static String strip(String s) {
        String t = s.trim();
        if (t.startsWith("(") && t.endsWith(")")) {
            return t.substring(1, t.length() - 1);
        }
        return t;
    }

    private record Column(String name, ColumnSpec spec) {}

    private static final Pattern TABLE_LEVEL = Pattern.compile(
            "^(CONSTRAINT|PRIMARY\\s+KEY|UNIQUE|FOREIGN\\s+KEY|CHECK|KEY|INDEX)\\b",
            Pattern.CASE_INSENSITIVE);

    private static java.util.Optional<Column> column(String definition) {
        String d = definition.trim();
        if (d.isEmpty() || TABLE_LEVEL.matcher(d).find()) {
            return java.util.Optional.empty();
        }
        Matcher m = Pattern.compile("^(" + IDENT + ")\\s+(.*)$", Pattern.DOTALL).matcher(d);
        if (!m.matches()) {
            return java.util.Optional.empty();
        }
        String rest = normalize(m.group(2));
        boolean notNull = Pattern.compile("\\bNOT\\s+NULL\\b", Pattern.CASE_INSENSITIVE)
                .matcher(rest).find();
        Matcher def = Pattern.compile(
                "\\bDEFAULT\\s+(.+?)(?=\\s+NOT\\s+NULL\\b|\\s+PRIMARY\\b|\\s+UNIQUE\\b"
                        + "|\\s+CHECK\\b|\\s+REFERENCES\\b|$)",
                Pattern.CASE_INSENSITIVE).matcher(rest);
        String value = def.find() ? defaultValue(def.group(1)) : null;
        return java.util.Optional.of(new Column(ident(m.group(1)), new ColumnSpec(!notNull, value)));
    }

    /**
     * 방언 차이를 흡수해 기본값을 비교 가능한 형태로 만든다.
     *
     * <ul>
     *   <li>{@code NOW()} ↔ {@code SYSTIMESTAMP} ↔ {@code CURRENT_TIMESTAMP} → {@code <now>}
     *   <li>{@code TRUE}/{@code FALSE} ↔ {@code 1}/{@code 0} — 오라클에 불리언 리터럴이 없다
     *   <li>{@code 0.00} ↔ {@code 0} — 숫자는 값으로 비교한다
     *   <li>{@code DEFAULT NULL} → 기본값 없음과 같게 본다
     * </ul>
     */
    private static String defaultValue(String raw) {
        String v = raw.trim().replaceAll(",$", "").trim();
        String u = v.toUpperCase(Locale.ROOT);
        if (u.equals("NULL")) {
            return null;
        }
        if (u.equals("NOW()") || u.equals("SYSTIMESTAMP") || u.equals("SYSDATE")
                || u.equals("CURRENT_TIMESTAMP") || u.equals("LOCALTIMESTAMP")) {
            return "<now>";
        }
        if (u.equals("TRUE")) {
            return "1";
        }
        if (u.equals("FALSE")) {
            return "0";
        }
        try {
            double d = Double.parseDouble(v);
            return d == Math.rint(d) ? String.valueOf((long) d) : String.valueOf(d);
        } catch (NumberFormatException ignored) {
            return v;
        }
    }

    /** 스키마 접두사와 따옴표를 벗기고 소문자로 맞춘다 ({@code public.t}, {@code "DESCRIPTOR"}). */
    private static String ident(String raw) {
        String v = raw.trim();
        int dot = v.lastIndexOf('.');
        if (dot >= 0 && v.indexOf('"') != 0) {
            v = v.substring(dot + 1);
        } else if (dot >= 0 && v.chars().filter(c -> c == '"').count() == 4) {
            v = v.substring(dot + 1);
        }
        return v.replace("\"", "").toLowerCase(Locale.ROOT);
    }

    private static List<String> splitTopLevel(String s) {
        List<String> parts = new ArrayList<>();
        StringBuilder buf = new StringBuilder();
        int depth = 0;
        for (char c : s.toCharArray()) {
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
            }
            if (c == ',' && depth == 0) {
                parts.add(buf.toString().trim());
                buf.setLength(0);
            } else {
                buf.append(c);
            }
        }
        parts.add(buf.toString().trim());
        parts.removeIf(String::isEmpty);
        return parts;
    }

    /** 체크 예외를 감싸 스트림 안에서 쓰기 위한 도우미. */
    @SuppressWarnings("unused")
    private static String read(Path p) {
        try {
            return Files.readString(p);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
