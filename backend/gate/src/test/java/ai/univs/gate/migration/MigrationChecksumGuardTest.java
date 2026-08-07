package ai.univs.gate.migration;

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
 * 이미 적용된 마이그레이션 파일은 <b>한 글자도</b> 바뀌면 안 된다 (UG-312).
 *
 * <p>Flyway 는 파일 내용 전체를 체크섬으로 잡는다. 적용이 끝난 파일을 고치면 — 주석 한 줄,
 * 공백 하나라도 — 그 환경의 다음 기동에서 {@code validate} 가 실패한다.
 * {@code validate-on-migrate} 기본값이 true 이고 컨테이너는 {@code restart: unless-stopped} 라
 * 애플리케이션이 뜨지 못한 채 크래시 루프가 된다. 서비스 전면 중단이고, 되돌리려면 배포를
 * 되돌리거나 각 환경에서 {@code flyway repair} 를 손으로 돌려야 한다 — 온프레미스 고객사까지
 * 포함해서다.
 *
 * <p><b>가정이 아니라 실제로 밟았다.</b> UG-312 초판이 V24 의 주석을 고쳤다. 재발급 기능을
 * 제거하면서 그 기능을 가리키던 서술이 낡았기 때문인데, V24 는 몇 시간 전 dev 에 배포된
 * 뒤였다. SQL 은 한 글자도 안 건드렸으니 리뷰에서도 눈에 띄기 어렵다 — 정확히 그래서 자동
 * 검사가 필요하다.
 *
 * <p>낡은 주석은 고치지 않고 둔다. 마이그레이션 파일은 코드가 아니라 <b>이미 일어난 일의
 * 기록</b>이라 낡는 것이 정상이다. 살아 있는 규칙은 테스트와 프로덕션 코드에 둔다.
 *
 * <p>지문은 {@code src/test/resources/migration-checksums.txt} 에 있다. 파일로 뺀 이유는
 * diff 때문이다 — 자바 맵에 넣으면 어느 마이그레이션이 바뀌었는지 리뷰에서 읽어내기 어렵다.
 *
 * <p>gate 뿐 아니라 face·match·palm 까지 본다. 네 서비스가 같은 함정을 공유하는데 검사가
 * 하나뿐인 것은 {@code DialectSchemaParityTest} 와 같은 판단이다.
 *
 * <p><b>다만 이 검사는 gate 빌드에서만 돈다</b> (반박 리뷰 지적). 젠킨스 파이프라인이 변경
 * 경로로 서비스를 골라 빌드하므로, {@code backend/face/**} 만 바꾸는 PR 은 이 테스트를 한 번도
 * 돌리지 않는다. 결과가 두 가지로 갈린다:
 *
 * <ul>
 *   <li>face 가 <b>적용된 파일을 고쳐도</b> 그 PR 에서는 안 잡힌다. 뒤늦게 gate 를 건드린
 *       PR 이 잡는다 — 없는 것보다는 낫지만 늦다.
 *   <li>face 가 <b>정상적으로</b> 새 마이그레이션을 추가하면, 그 뒤 처음 gate 를 건드린
 *       무관한 PR 이 "새 파일" 로 빨간불을 받는다. 지연되고 오귀속되는 실패다.
 * </ul>
 *
 * <p>그래서 실패 메시지가 "새 파일이면 정상" 을 먼저 말한다. 각 서비스가 자기 것을 즉시
 * 검사하도록 옮기는 것이 옳고, UG-313 으로 분리했다.
 */
@DisplayName("UG-312: 적용된 마이그레이션 파일 불변")
class MigrationChecksumGuardTest {

    private static final String RECORD = "/migration-checksums.txt";

    private static final List<String> SERVICES = List.of("gate", "face", "match", "palm");

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
                        배포됐다면 되돌리고, 정말 바꿔야 하는 내용이면 새 버전(V25...)으로 낸다.
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
    @DisplayName("네 서비스의 두 방언을 모두 덮고 있다")
    void 가드가_공회전하지_않는다() {
        Map<String, String> 실제 = 실제_지문();

        assertThat(실제).as("마이그레이션 파일을 한 개도 못 찾았다 — 경로가 어긋났다").isNotEmpty();
        for (String service : SERVICES) {
            assertThat(실제.keySet())
                    .as("%s 의 마이그레이션을 한 개도 못 찾았다", service)
                    .anyMatch(name -> name.startsWith(service + "/"));
        }
        assertThat(실제.keySet())
                .as("오라클 쪽이 통째로 빠지면 온프레미스만 조용히 무방비가 된다")
                .anyMatch(name -> name.contains("/oracle/"));
        assertThat(기록을_읽는다())
                .as("기록이 비면 '내용변경' 을 영원히 못 잡는다")
                .isNotEmpty();
    }

    // ─────────────────────────────────────────────────────────────────────────

    /** {@code 서비스/방언/파일명 → sha256}. */
    private static Map<String, String> 실제_지문() {
        Map<String, String> result = new LinkedHashMap<>();
        for (String service : SERVICES) {
            Path base = 모노레포_루트()
                    .resolve("backend").resolve(service)
                    .resolve("src/main/resources/db/migration");
            if (!Files.isDirectory(base)) {
                continue;
            }
            try (Stream<Path> dialects = Files.list(base)) {
                for (Path dialect : dialects.filter(Files::isDirectory).sorted().toList()) {
                    try (Stream<Path> files = Files.list(dialect)) {
                        for (Path sql : files.filter(p -> p.toString().endsWith(".sql"))
                                .sorted().toList()) {
                            result.put("%s/%s/%s".formatted(
                                            service, dialect.getFileName(), sql.getFileName()),
                                    sha256(sql));
                        }
                    }
                }
            } catch (IOException e) {
                throw new UncheckedIOException("마이그레이션 폴더를 못 읽었다: " + base, e);
            }
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

    /** 테스트의 작업 디렉터리는 {@code backend/gate} 다. 못 찾으면 통과가 아니라 예외로 끝낸다. */
    private static Path 모노레포_루트() {
        for (Path p = Path.of("").toAbsolutePath(); p != null; p = p.getParent()) {
            if (Files.isDirectory(p.resolve("backend/gate/src/main/resources/db/migration"))) {
                return p;
            }
        }
        throw new IllegalStateException(
                "모노레포 루트를 찾지 못했다. 작업 디렉터리=" + Path.of("").toAbsolutePath());
    }
}
