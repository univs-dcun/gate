package ai.univs.match.config;

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
 * UG-307: 앱 소스의 프로파일 yml 에 DB 접속 정보를 평문으로 두지 않는다.
 *
 * <p>2026-05 부터 {@code application-{postgresql,oracle}.yml} 에 사내 개발·오라클 서버의 URL·계정·비밀번호가
 * 그대로 들어 있었다. {@code src/main/resources} 라 jar 와 컨테이너 이미지에 실리고(온프레미스 납품 이미지에도),
 * 컨테이너에 {@code SPRING_DATASOURCE_*} 환경변수가 빠지면 기동 실패 대신 그 서버로 조용히 붙었다.
 * UG-233 이후 설정의 단일 진실은 gate-config 이고, 실제 값은 환경변수가 준다.
 *
 * <p>규칙: {@code spring.datasource} 블록의 {@code url}·{@code username}·{@code password} 값은
 * {@code ${...}} 플레이스홀더여야 한다. 주석으로 남겨 둔 접속 정보도 금지한다 — match 의 PostgreSQL 파일에
 * PC 별 비밀번호가 주석으로 남아 있던 전례가 있다. 예외는 인메모리 H2 만 쓰는 {@code application-openapi.yml}
 * 같은 로컬 전용 파일인데, 그것도 인메모리 URL 형태일 때만 통과시킨다. {@code ${X:기본값}} 은 폴백이 소스로
 * 되돌아오는 통로라 기본값 없는 플레이스홀더만 허용한다 (반박 리뷰가 통과하는 것을 보여 줬다).
 */
@DisplayName("UG-307: 프로파일 yml 에 평문 DB 접속 정보가 없다")
class DatasourceCredentialGuardTest {

    /** datasource / flyway 블록 안에서 접속 정보를 담는 키. jdbc-url·user 는 Hikari·Flyway 의 별칭이다. */
    private static final Pattern KEY = Pattern.compile("^(\\s*)(url|jdbc-url|jdbcUrl|username|user|password):\\s*(.*)$");
    private static final Pattern BLOCK = Pattern.compile("^(\\s*)(datasource|flyway):\\s*$");
    /** {@code datasource: { url: … }} 같은 flow 스타일은 블록 판정을 우회하므로 아예 금지한다. */
    private static final Pattern FLOW_BLOCK = Pattern.compile("^\\s*(datasource|flyway):\\s*\\{");
    /** {@code spring.datasource.password: x} 같은 점 표기도 같은 규칙으로 본다. */
    private static final Pattern DOTTED_KEY = Pattern.compile("^\\s*spring\\.(datasource|flyway)(\\.hikari)?\\.(url|jdbc-url|jdbcUrl|username|user|password):\\s*(.*)$");
    /** 기본값 없는 플레이스홀더만 허용한다 — {@code ${X:default}} 는 폴백이 소스로 돌아오는 통로라 금지. 따옴표는 허용. */
    private static final Pattern EXACT_PLACEHOLDER = Pattern.compile("^\"?\\$\\{[A-Z0-9_]+\\}\"?$");
    /** 인메모리 H2 만 예외. {@code ;INIT=RUNSCRIPT FROM '…'} 같은 꼬리는 허용 문자 집합 밖이라 걸린다. */
    private static final Pattern H2_MEM = Pattern.compile("^jdbc:h2:mem:[A-Za-z0-9_-]+(;[A-Z_]+=[A-Za-z0-9_.-]+)*$");
    private static final Pattern COMMENTED_SECRET = Pattern.compile("^\\s*#.*\\b(password|passwd|pwd)\\s*:", Pattern.CASE_INSENSITIVE);
    /** 사설 IPv4 — 네 옥텟을 강제하고 앞뒤 경계를 둬 {@code 10.1.2} 같은 버전 문자열은 잡지 않는다. */
    private static final Pattern PRIVATE_IP = Pattern.compile(
            "(?<![\\d.])(?:10\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}|192\\.168\\.\\d{1,3}\\.\\d{1,3}|172\\.(?:1[6-9]|2\\d|3[01])\\.\\d{1,3}\\.\\d{1,3})(?![\\d.])");

    @Test
    @DisplayName("datasource·flyway 의 접속 정보 값은 기본값 없는 ${...} 플레이스홀더다 (주석 속 비밀번호·사설 IP·flow 스타일·점 표기 우회 금지)")
    void 접속_정보는_기본값_없는_플레이스홀더다() throws IOException {
        Path resources = Path.of("src/main/resources");
        List<String> violations = new ArrayList<>();
        try (Stream<Path> files = Files.list(resources)) {
            for (Path f : files.filter(p -> p.getFileName().toString().matches("application.*\\.ya?ml")).toList()) {
                String name = f.getFileName().toString();
                boolean inBlock = false;
                int blockIndent = -1;
                int lineNo = 0;
                for (String line : Files.readAllLines(f)) {
                    lineNo++;
                    if (COMMENTED_SECRET.matcher(line).find()) {
                        violations.add(name + ":" + lineNo + " 주석에 비밀번호가 남아 있다");
                    }
                    if (PRIVATE_IP.matcher(line).find()) {
                        violations.add(name + ":" + lineNo + " 사설 IP 가 들어 있다");
                    }
                    if (FLOW_BLOCK.matcher(line).find()) {
                        violations.add(name + ":" + lineNo + " datasource/flyway 를 flow 스타일로 쓰지 않는다 (검사 우회)");
                    }
                    Matcher dotted = DOTTED_KEY.matcher(line);
                    if (dotted.matches()) {
                        check(violations, name, lineNo, dotted.group(3), dotted.group(4));
                        continue;
                    }
                    Matcher block = BLOCK.matcher(line);
                    if (block.matches()) {
                        inBlock = true;
                        blockIndent = block.group(1).length();
                        continue;
                    }
                    if (!inBlock || line.isBlank() || line.strip().startsWith("#")) {
                        continue;
                    }
                    int indent = line.length() - line.stripLeading().length();
                    if (indent <= blockIndent) {
                        inBlock = false;
                        continue;
                    }
                    Matcher key = KEY.matcher(line);
                    if (key.matches()) {
                        check(violations, name, lineNo, key.group(2), key.group(3));
                    }
                }
            }
        }
        assertThat(violations)
                .as("접속 정보는 환경변수(SPRING_DATASOURCE_*) 또는 config-server 가 준다 — 소스에는 기본값 없는 플레이스홀더만 둔다 (UG-307)")
                .isEmpty();
    }

    private static void check(List<String> violations, String file, int lineNo, String key, String rawValue) {
        String value = rawValue.strip();
        if (value.isEmpty() || EXACT_PLACEHOLDER.matcher(value).matches()) {
            return;
        }
        if (H2_MEM.matcher(value).matches() || value.equals("sa")) {
            return;
        }
        if (value.startsWith("${") || value.startsWith("\"${")) {
            violations.add(file + ":" + lineNo + " " + key + " 플레이스홀더에 기본값이 있다 — 기본값은 소스로 돌아온 폴백이다");
        } else {
            violations.add(file + ":" + lineNo + " " + key + " 가 평문이다");
        }
    }
}
