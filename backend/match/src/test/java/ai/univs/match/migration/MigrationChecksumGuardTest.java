package ai.univs.match.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 이미 적용된 마이그레이션 파일은 <b>한 글자도</b> 바뀌면 안 된다 (UG-312, UG-313 으로 이 서비스에 분산).
 *
 * <p>Flyway 는 파일 내용 전체를 체크섬으로 잡는다. 적용이 끝난 파일을 고치면 — 주석 한 줄,
 * 공백 하나라도 — 그 환경의 다음 기동에서 {@code validate} 가 실패한다.
 * {@code validate-on-migrate} 기본값이 true 이고 컨테이너는 {@code restart: unless-stopped} 라
 * 애플리케이션이 뜨지 못한 채 크래시 루프가 된다. 서비스 전면 중단이고, 되돌리려면 배포를
 * 되돌리거나 각 환경에서 {@code flyway repair} 를 손으로 돌려야 한다 — 온프레미스 고객사까지
 * 포함해서다.
 *
 * <p><b>가정이 아니라 실제로 밟았다.</b> UG-312 초판이 gate 의 V24 주석을 고쳤다. 기능을
 * 제거하면서 그 기능을 가리키던 서술이 낡았기 때문인데, V24 는 몇 시간 전 dev 에 배포된
 * 뒤였다. SQL 은 한 글자도 안 건드렸으니 리뷰에서도 눈에 띄기 어렵다 — 정확히 그래서 자동
 * 검사가 필요하다.
 *
 * <p>낡은 주석은 고치지 않고 둔다. 마이그레이션 파일은 코드가 아니라 <b>이미 일어난 일의
 * 기록</b>이라 낡는 것이 정상이다. 살아 있는 규칙은 테스트와 프로덕션 코드에 둔다.
 *
 * <p><b>왜 서비스마다 하나씩인가 (UG-313).</b> 원래는 gate 의 테스트 하나가 네 서비스를 모두
 * 봤다. 그런데 젠킨스 파이프라인이 변경 경로로 빌드 대상을 고르므로
 * ({@code univsServicePipeline.groovy} 의 {@code file.startsWith(SERVICE_PATH + '/')}),
 * {@code backend/match/**} 만 바꾸는 PR 은 gate 테스트를 한 번도 돌리지 않았다. 두 방향으로 어긋난다:
 *
 * <ul>
 *   <li>gate 아닌 서비스가 <b>적용된 파일을 고쳐도</b> 그 PR 에서는 안 잡혔다. 뒤늦게 gate 를
 *       건드린 PR 이 잡는다 — 그 사이에 배포되면 크래시 루프다.
 *   <li>반대로 그 서비스가 <b>정상적으로</b> 새 마이그레이션을 추가하면, 그 뒤 처음 gate 를
 *       건드린 <b>무관한</b> PR 이 빨간불을 받았다. 지연되고 오귀속되는 실패이고, 빌드 성공이
 *       머지 조건이라 그 사람이 막힌다.
 * </ul>
 *
 * <p>그래서 이 테스트는 <b>match 의 마이그레이션만</b> 본다. 형제 서비스 폴더를 아예 읽지
 * 않으므로 이 서비스를 단독 레포로 떼도(UG-249 전례) 그대로 돈다.
 *
 * <p>지문은 {@code src/test/resources/migration-checksums.txt} 에 있다. 파일로 뺀 이유는
 * diff 때문이다 — 자바 맵에 넣으면 어느 마이그레이션이 바뀌었는지 리뷰에서 읽어내기 어렵다.
 */
@DisplayName("UG-312: 적용된 마이그레이션 파일 불변 (match)")
class MigrationChecksumGuardTest {

    private static final String RECORD = "/migration-checksums.txt";

    /** 이 테스트는 자기 서비스만 본다 (UG-313). 형제 폴더는 각자의 가드가 본다. */
    private static final String SERVICE = "match";

    @Test
    @DisplayName("기록된 지문과 실제 파일이 모두 일치한다")
    void 적용된_파일이_바뀌지_않았다() {
        Map<String, String> 기록 = 기록을_읽는다();
        Map<String, String> 실제 = 실제_지문();

        List<String> 바뀐_파일 = new ArrayList<>();
        기록.forEach((name, expected) -> {
            String actual = 실제.get(name);
            if (actual == null) {
                바뀐_파일.add("사라짐  %s — 기록에는 있는데 파일이 없다".formatted(name));
            } else if (!actual.equals(expected)) {
                바뀐_파일.add("내용변경 %s".formatted(name));
            }
        });
        실제.keySet().stream()
                .filter(name -> !기록.containsKey(name))
                .forEach(name -> 바뀐_파일.add("새 파일 %s — 기록에 줄을 추가할 것".formatted(name)));

        assertThat(바뀐_파일)
                .as("""
                        Flyway 는 마이그레이션 파일의 내용 전체를 체크섬으로 잡는다. 이미 적용된 \
                        파일을 고치면 주석 한 줄이라도 그 환경의 다음 기동에서 validate 가 \
                        실패하고, restart: unless-stopped 라 크래시 루프가 된다.

                        · "새 파일" 이면 정상이다 — migration-checksums.txt 에 줄을 추가한다.
                        · "내용변경" 이면 이미 적용된 파일을 고쳤을 가능성이 크다. dev 에 한 번이라도 \
                        배포됐다면 되돌리고, 정말 바꿔야 하는 내용이면 새 버전으로 낸다.
                        · "사라짐" 이면 파일을 지웠다는 뜻이다. 적용한 환경에서는 missing 으로 \
                        validate 가 실패하므로 삭제도 같은 사고다.

                        어느 쪽이든 지문 갱신은 <판단>이다. 검사를 통과시키려고 기계적으로 \
                        고치지 말 것 — 파일 갱신 방법은 migration-checksums.txt 머리말에 있다.""")
                .isEmpty();
    }

    /**
     * 검사가 공회전하지 않는지.
     *
     * <p>경로가 어긋나 대상이 0개가 되면 위 검사는 영원히 초록이고, 그 상태가 "아무것도 안
     * 바뀌었다" 와 구분되지 않는다. 기록 파일이 비어도 마찬가지다.
     */
    @Test
    @DisplayName("이 서비스의 두 방언을 모두 덮고 있다")
    void 가드가_공회전하지_않는다() {
        Map<String, String> 실제 = 실제_지문();

        assertThat(실제).as("마이그레이션 파일을 한 개도 못 찾았다 — 경로가 어긋났다").isNotEmpty();
        assertThat(실제.keySet())
                .as("오라클 쪽이 통째로 빠지면 온프레미스만 조용히 무방비가 된다")
                .anyMatch(name -> name.contains("/oracle/"));
        assertThat(실제.keySet())
                .as("postgresql 쪽이 빠지면 클라우드 배포가 무방비가 된다")
                .anyMatch(name -> name.contains("/postgresql/"));

        Map<String, String> 기록 = 기록을_읽는다();
        assertThat(기록).as("기록이 비면 '내용변경' 을 영원히 못 잡는다").isNotEmpty();
        assertThat(기록.keySet())
                .as("이 기록은 %s 것만 담는다 — 형제 서비스는 각자의 가드가 본다 (UG-313)", SERVICE)
                .allMatch(name -> name.startsWith(SERVICE + "/"));
    }

    // ─────────────────────────────────────────────────────────────────────────

    /** {@code 서비스/방언/파일명 → sha256}. 서비스 접두사는 기록 파일을 그대로 읽히게 하려고 남긴다. */
    private static Map<String, String> 실제_지문() {
        Map<String, String> result = new LinkedHashMap<>();
        Path base = 마이그레이션_폴더();
        try (Stream<Path> dialects = Files.list(base)) {
            for (Path dialect : dialects.filter(Files::isDirectory).sorted().toList()) {
                try (Stream<Path> files = Files.list(dialect)) {
                    for (Path sql : files.filter(p -> p.toString().endsWith(".sql"))
                            .sorted().toList()) {
                        result.put("%s/%s/%s".formatted(
                                        SERVICE, dialect.getFileName(), sql.getFileName()),
                                sha256(sql));
                    }
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("마이그레이션 폴더를 못 읽었다: " + base, e);
        }
        return result;
    }

    private static Map<String, String> 기록을_읽는다() {
        Map<String, String> result = new LinkedHashMap<>();
        try (InputStream in = MigrationChecksumGuardTest.class.getResourceAsStream(RECORD)) {
            if (in == null) {
                throw new IllegalStateException("지문 기록을 못 찾았다: " + RECORD);
            }
            for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n")) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                String[] parts = trimmed.split("\\s+");
                if (parts.length != 2) {
                    throw new IllegalStateException("지문 기록의 형식이 깨졌다: " + line);
                }
                if (result.put(parts[0], parts[1]) != null) {
                    throw new IllegalStateException("지문 기록에 같은 파일이 두 번 있다: " + parts[0]);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(RECORD, e);
        }
        return result;
    }

    private static String sha256(Path file) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(Files.readAllBytes(file)));
        } catch (IOException e) {
            throw new UncheckedIOException(file.toString(), e);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * 이 서비스의 마이그레이션 폴더. 테스트의 작업 디렉터리는 {@code backend/match} 이므로 보통
     * 첫 줄에서 끝난다. 모노레포 루트에서 돌리는 경우를 위해 위로도 한 번 찾아본다.
     * 못 찾으면 통과가 아니라 예외로 끝낸다 — 조용한 0건이 가장 위험하다.
     */
    private static Path 마이그레이션_폴더() {
        Path relative = Path.of("src/main/resources/db/migration");
        Path cwd = Path.of("").toAbsolutePath();
        if (Files.isDirectory(cwd.resolve(relative))) {
            return cwd.resolve(relative);
        }
        for (Path p = cwd; p != null; p = p.getParent()) {
            Path viaRoot = p.resolve("backend").resolve(SERVICE).resolve(relative);
            if (Files.isDirectory(viaRoot)) {
                return viaRoot;
            }
        }
        throw new IllegalStateException(
                "%s 의 마이그레이션 폴더를 찾지 못했다. 작업 디렉터리=%s".formatted(SERVICE, cwd));
    }
}
