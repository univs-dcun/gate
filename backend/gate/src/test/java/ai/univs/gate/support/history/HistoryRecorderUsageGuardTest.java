package ai.univs.gate.support.history;

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
 * 이력 기록기를 <b>제대로 쓰고 있는가</b> (UG-293 반박 리뷰).
 *
 * <p>{@code HistoryRecorderSliceTest} 는 기록기 <b>자체</b>가 호출자 롤백에서 살아남는지를
 * 실제 트랜잭션으로 본다. 그런데 그것만으로는 <b>사용처</b>가 지켜지지 않는다 — 리뷰가 40여 개
 * {@code succeed}/{@code fail} 호출 중 넷을 지우고 전체 빌드를 돌렸는데 전부 초록이었다.
 *
 * <p>왜 단위 테스트가 못 잡는가. 유스케이스 테스트는 목을 쓰고, 목이 같은 인스턴스를
 * 돌려주므로 전이 결과가 <b>메모리에서</b> 보인다. "커밋됐는가" 는 보지 않는다.
 *
 * <p>무엇을 잃는가. 전이가 커밋되지 않으면 행은 {@code success=false, failure_type=null} 로
 * 남는다. 성공한 인증이 <b>감사 기록상 일어나지 않은 일</b>이 된다 — 은행권 분쟁에서
 * "이 사람이 인증했는가" 에 아니오로 답하게 된다.
 *
 * <p>그래서 폐기한 선언 검사가 지키던 자리를 이 검사가 대신한다. 옛 검사는 소스에서
 * {@code noRollbackFor} 선언을 찾았고, 이 검사는 기록기 사용 규약을 찾는다.
 */
@DisplayName("UG-293: 이력 기록기 사용 규약")
class HistoryRecorderUsageGuardTest {

    private static final Path SOURCE_ROOT = Path.of("src/main/java");

    /** 스캔이 엉뚱한 트리를 훑고 조용히 통과하지 않도록. 현재 13곳이 기록기를 쓴다. */
    private static final int MIN_USERS = 10;

    /** 이력 엔티티의 상태 전이 메서드. 하나라도 빠지면 그 전이가 커밋되지 않는다. */
    private static final Pattern TRANSITION = Pattern.compile(
            "\\b(\\w*[Hh]istory)\\.(fail|failUpstream|success|successById|successRegister"
                    + "|successDelete|updateBiometricFeature)\\s*\\(");

    private static final Pattern RECORDER_COMMIT =
            Pattern.compile("historyRecorder\\.(succeed|fail)\\s*\\(");

    /**
     * 전이 뒤에는 반드시 커밋 호출이 온다.
     *
     * <p>실패 전이는 {@code fail}(별도 트랜잭션), 성공 전이는 {@code succeed}(호출자 트랜잭션
     * 합류)로 갈린다. 여기서는 <b>둘 중 하나가 바로 뒤에 오는가</b>만 본다 — 어느 쪽인지는
     * 아래 테스트가 본다.
     */
    @Test
    @DisplayName("이력 상태 전이 뒤에는 반드시 커밋 호출이 온다")
    void 전이_뒤에_커밋이_온다() throws IOException {
        List<String> violations = new ArrayList<>();
        List<Path> scanned = 소스들();

        for (Path file : scanned) {
            String text = Files.readString(file);
            if (!text.contains("historyRecorder")) {
                continue;
            }
            Matcher m = TRANSITION.matcher(text);
            while (m.find()) {
                String rest = text.substring(m.end());
                String nextLine = rest.contains("\n")
                        ? rest.substring(rest.indexOf('\n') + 1).split("\n")[0]
                        : "";
                if (!RECORDER_COMMIT.matcher(nextLine).find()) {
                    violations.add("%s:%d %s".formatted(
                            SOURCE_ROOT.relativize(file),
                            text.substring(0, m.start()).split("\n", -1).length,
                            m.group(0)));
                }
            }
        }

        assertThat(violations)
                .as("""
                        이력 상태를 바꾸고 커밋하지 않으면 그 전이는 사라진다. 행은 남지만
                        success=false, failure_type=null 인 채로다 — 성공한 인증이 감사
                        기록상 일어나지 않은 일이 된다.

                        실패 전이 뒤에는 historyRecorder.fail(...),
                        성공 전이 뒤에는 historyRecorder.succeed(...) 를 둘 것.""")
                .isEmpty();
    }

    /**
     * 성공과 실패의 커밋 경계가 뒤바뀌지 않았는지.
     *
     * <p>둘은 의도적으로 다르다. 실패 사유는 호출자가 롤백해도 남아야 하므로 별도 트랜잭션이고
     * ({@code REQUIRES_NEW}), 성공은 <b>성공했다고 말하는 그 일</b>과 원자적이어야 하므로
     * 호출자 트랜잭션에 합류한다({@code REQUIRED}).
     *
     * <p>뒤바뀌면 조용히 잘못된다 — 성공을 별도로 커밋하면 등록이 롤백돼도 "등록 성공" 이력이
     * 남고, 실패를 합류시키면 롤백과 함께 사유가 지워진다.
     */
    @Test
    @DisplayName("실패 전이는 fail, 성공 전이는 succeed 로 커밋한다")
    void 성공과_실패의_경계가_다르다() throws IOException {
        List<String> violations = new ArrayList<>();

        for (Path file : 소스들()) {
            String text = Files.readString(file);
            if (!text.contains("historyRecorder")) {
                continue;
            }
            Matcher m = TRANSITION.matcher(text);
            while (m.find()) {
                boolean 실패전이 = m.group(2).startsWith("fail");
                String rest = text.substring(m.end());
                String nextLine = rest.contains("\n")
                        ? rest.substring(rest.indexOf('\n') + 1).split("\n")[0]
                        : "";
                Matcher c = RECORDER_COMMIT.matcher(nextLine);
                if (!c.find()) {
                    continue;   // 위 테스트가 잡는다
                }
                boolean 실패커밋 = "fail".equals(c.group(1));
                if (실패전이 != 실패커밋) {
                    violations.add("%s:%d %s → historyRecorder.%s".formatted(
                            SOURCE_ROOT.relativize(file),
                            text.substring(0, m.start()).split("\n", -1).length,
                            m.group(2), c.group(1)));
                }
            }
        }

        assertThat(violations)
                .as("""
                        실패 사유는 호출자가 롤백해도 남아야 한다 (fail, REQUIRES_NEW).
                        성공은 성공했다고 말하는 그 일과 원자적이어야 한다 (succeed, REQUIRED).
                        뒤바꾸면 등록이 롤백돼도 '등록 성공' 이력이 남거나, 롤백과 함께
                        실패 사유가 지워진다.""")
                .isEmpty();
    }

    /**
     * 이력 리포지토리를 직접 주입하지 않는다.
     *
     * <p>{@code MatchHistoryRepository}·{@code FeatureHistoryRepository} 는 여전히 public 이다.
     * 새 유스케이스가 그것을 직접 주입해 {@code save} 하면 이력이 호출자 트랜잭션 안에서
     * 커밋되고, UG-293 이 없애려던 실패 모드가 그대로 돌아온다 — 그리고 아무 테스트도 깨지지
     * 않는다 (리뷰 지적).
     */
    @Test
    @DisplayName("이력 리포지토리는 HistoryRecorder 밖에서 쓰지 않는다")
    void 리포지토리를_직접_쓰지_않는다() throws IOException {
        List<String> violations = new ArrayList<>();

        for (Path file : 소스들()) {
            String rel = SOURCE_ROOT.relativize(file).toString().replace('\\', '/');
            if (rel.contains("/domain/repository/") || rel.contains("/infrastructure/persistence/")
                    || rel.endsWith("support/history/HistoryRecorder.java")) {
                continue;
            }
            String text = Files.readString(file);
            if (text.contains("MatchHistoryRepository") || text.contains("FeatureHistoryRepository")) {
                violations.add(rel);
            }
        }

        assertThat(violations)
                .as("""
                        이력 저장은 HistoryRecorder 를 거쳐야 한다. 리포지토리를 직접 주입해
                        save 하면 이력이 호출자 트랜잭션 안에서 커밋되고, 하위 서비스 실패나
                        우리 코드의 예외에 함께 사라진다 (UG-280 이 세 번 겪은 실패 모드).""")
                .isEmpty();
    }

    @Test
    @DisplayName("기록기를 쓰는 곳이 실제로 있다 — 스캔이 공회전하지 않는다")
    void 스캔이_공회전하지_않는다() throws IOException {
        long users = 소스들().stream()
                .filter(f -> {
                    try {
                        return Files.readString(f).contains("historyRecorder");
                    } catch (IOException e) {
                        throw new IllegalStateException(f.toString(), e);
                    }
                })
                .count();

        assertThat(users)
                .as("기록기를 쓰는 파일을 못 찾았다면 위 검사들은 위반 0건으로 영원히 통과한다")
                .isGreaterThanOrEqualTo(MIN_USERS);
    }

    private static List<Path> 소스들() throws IOException {
        List<Path> files;
        try (Stream<Path> paths = Files.walk(SOURCE_ROOT)) {
            files = paths.filter(p -> p.toString().endsWith(".java")).sorted().toList();
        }
        assertThat(files)
                .as("소스 트리를 못 훑었다 — 작업 디렉터리가 gate 모듈 루트인지 확인할 것")
                .hasSizeGreaterThan(100);
        return files;
    }
}
