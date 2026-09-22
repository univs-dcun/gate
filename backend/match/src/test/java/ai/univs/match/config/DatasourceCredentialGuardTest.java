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
 * 같은 로컬 전용 파일인데, 그것도 실제 서버 주소가 아닌 경우에만 통과시킨다.
 */
@DisplayName("UG-307: 프로파일 yml 에 평문 DB 접속 정보가 없다")
class DatasourceCredentialGuardTest {

    private static final Pattern KEY = Pattern.compile("^(\\s*)(url|username|password):\\s*(.*)$");
    private static final Pattern DATASOURCE = Pattern.compile("^(\\s*)datasource:\\s*$");
    private static final Pattern COMMENTED_SECRET = Pattern.compile("^\\s*#.*\\b(password|passwd|pwd)\\s*:", Pattern.CASE_INSENSITIVE);
    private static final Pattern PRIVATE_IP = Pattern.compile("\\b(10\\.|192\\.168\\.|172\\.(1[6-9]|2\\d|3[01])\\.)\\d+\\.\\d+");

    @Test
    @DisplayName("spring.datasource 의 url·username·password 는 ${...} 플레이스홀더다 (주석 속 접속 정보·사설 IP 도 금지)")
    void datasource_값은_플레이스홀더다() throws IOException {
        Path resources = Path.of("src/main/resources");
        List<String> violations = new ArrayList<>();
        try (Stream<Path> files = Files.list(resources)) {
            for (Path f : files.filter(p -> p.getFileName().toString().matches("application.*\\.ya?ml")).toList()) {
                boolean inDatasource = false;
                int dsIndent = -1;
                int lineNo = 0;
                for (String line : Files.readAllLines(f)) {
                    lineNo++;
                    if (COMMENTED_SECRET.matcher(line).find()) {
                        violations.add(f.getFileName() + ":" + lineNo + " 주석에 비밀번호가 남아 있다");
                    }
                    if (PRIVATE_IP.matcher(line).find()) {
                        violations.add(f.getFileName() + ":" + lineNo + " 사설 IP 가 들어 있다");
                    }
                    Matcher ds = DATASOURCE.matcher(line);
                    if (ds.matches()) {
                        inDatasource = true;
                        dsIndent = ds.group(1).length();
                        continue;
                    }
                    if (!inDatasource || line.isBlank() || line.strip().startsWith("#")) {
                        continue;
                    }
                    int indent = line.length() - line.stripLeading().length();
                    if (indent <= dsIndent) {
                        inDatasource = false;
                        continue;
                    }
                    Matcher key = KEY.matcher(line);
                    if (key.matches()) {
                        String value = key.group(3).strip();
                        boolean placeholder = value.startsWith("${");
                        boolean inMemory = value.startsWith("jdbc:h2:mem:") || value.equals("sa") || value.isEmpty();
                        if (!placeholder && !inMemory) {
                            violations.add(f.getFileName() + ":" + lineNo + " " + key.group(2) + " 가 평문이다");
                        }
                    }
                }
            }
        }
        assertThat(violations)
                .as("접속 정보는 환경변수(SPRING_DATASOURCE_*) 또는 config-server 가 준다 — 소스에 두지 않는다 (UG-307)")
                .isEmpty();
    }
}
