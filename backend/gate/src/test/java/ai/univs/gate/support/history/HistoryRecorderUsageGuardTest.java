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
 *
 * <p><b>무엇을 못 잡는지 적어 둔다</b> (3차 리뷰가 변이 17종으로 확인). 소스를 문자열로 훑는
 * 검사의 한계이고, 모르면 있는 것보다 해롭다.
 *
 * <ul>
 *   <li><b>제어 흐름을 모른다.</b> {@code if (조건) historyRecorder.fail(h);} 처럼 조건 안에
 *       넣으면 통과한다. 한 문장 앞만 보기 때문이다.
 *   <li><b>인자 동일성을 안 본다.</b> 엉뚱한 객체를 커밋해도 통과한다.
 *   <li><b>전이와 커밋 사이에 다른 문장을 넣으면 위반으로 잡는다</b> — 오탐이다. 그 둘은
 *       붙여 쓰는 것이 규약이므로 일부러 좁게 뒀다.
 * </ul>
 *
 * <p>실제 동작은 {@code HistoryRecorderSliceTest} 가 트랜잭션을 열어 확인한다. 이 검사는
 * 그 규약을 <b>쓰는 자리</b>에서 빠뜨리지 않았는지만 본다.
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
            String code = 주석과_문자열을_지운다(Files.readString(file));
            if (!code.contains("historyRecorder")) {
                continue;
            }
            Matcher m = TRANSITION.matcher(code);
            while (m.find()) {
                if (!RECORDER_COMMIT.matcher(다음_문장(code, m.end())).find()) {
                    violations.add("%s:%d %s".formatted(
                            SOURCE_ROOT.relativize(file),
                            code.substring(0, m.start()).split("\n", -1).length,
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
            String code = 주석과_문자열을_지운다(Files.readString(file));
            if (!code.contains("historyRecorder")) {
                continue;
            }
            Matcher m = TRANSITION.matcher(code);
            while (m.find()) {
                boolean 실패전이 = m.group(2).startsWith("fail");
                Matcher c = RECORDER_COMMIT.matcher(다음_문장(code, m.end()));
                if (!c.find()) {
                    continue;   // 위 테스트가 잡는다
                }
                boolean 실패커밋 = "fail".equals(c.group(1));
                if (실패전이 != 실패커밋) {
                    violations.add("%s:%d %s → historyRecorder.%s".formatted(
                            SOURCE_ROOT.relativize(file),
                            code.substring(0, m.start()).split("\n", -1).length,
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

    /**
     * {@code from} 이 속한 문장을 끝낸 <b>다음 문장</b>.
     *
     * <p>초판은 "다음 줄" 을 봤다. 리뷰가 두 가지로 깨뜨렸다 — 커밋 호출을 주석 처리하면
     * 통과했고(원문을 그대로 봤으므로), 전이 호출을 두 줄로 나누거나 사이에 빈 줄을 넣으면
     * 정상 코드인데 위반으로 잡혔다. 이 저장소의 전이 호출은 이미 길어서 줄바꿈이 현실적이다.
     *
     * <p>문장 단위로 보면 셋이 한꺼번에 해결된다. 주석·문자열은 미리 지운다.
     */
    private static String 다음_문장(String code, int from) {
        int end = code.indexOf(';', from);
        if (end < 0) {
            return "";
        }
        int next = code.indexOf(';', end + 1);
        return next < 0 ? code.substring(end + 1) : code.substring(end + 1, next + 1);
    }

    /** 주석과 문자열 리터럴을 공백으로. 길이를 유지해 줄 번호가 어긋나지 않게 한다. */
    private static String 주석과_문자열을_지운다(String source) {
        StringBuilder out = new StringBuilder(source.length());
        int i = 0;
        while (i < source.length()) {
            char c = source.charAt(i);
            String two = i + 1 < source.length() ? source.substring(i, i + 2) : "";
            String three = i + 2 < source.length() ? source.substring(i, i + 3) : "";
            if ("//".equals(two)) {
                while (i < source.length() && source.charAt(i) != '\n') {
                    out.append(' ');
                    i++;
                }
            } else if ("/*".equals(two)) {
                int close = source.indexOf("*/", i + 2);
                int stop = close < 0 ? source.length() : close + 2;
                for (; i < stop; i++) {
                    out.append(source.charAt(i) == '\n' ? '\n' : ' ');
                }
            } else if ("\"\"\"".equals(three)) {
                // 텍스트 블록. 이것을 모르면 여는 \"\"\" 를 「빈 문자열 + 새 문자열」로 읽고,
                // 블록 안의 따옴표가 홀수면 패리티가 뒤집혀 <파일의 나머지 전체>를 지운다.
                // 그러면 그 파일에는 전이도 커밋도 없는 것처럼 보여 가드가 조용히 초록이 된다
                // (3차 리뷰가 재현). 자매 가드의 COMMENT 정규식이 UG-326 에서 같은 함정을
                // 이미 막아 뒀는데 그 지식이 여기로 옮겨오지 않았다.
                int close = source.indexOf("\"\"\"", i + 3);
                int stop = close < 0 ? source.length() : close + 3;
                for (; i < stop; i++) {
                    out.append(source.charAt(i) == '\n' ? '\n' : ' ');
                }
            } else if (c == '"' || c == '\'') {
                out.append(' ');
                i++;
                while (i < source.length() && source.charAt(i) != c) {
                    if (source.charAt(i) == '\\') {
                        out.append(' ');
                        i++;
                        if (i < source.length()) {
                            out.append(source.charAt(i) == '\n' ? '\n' : ' ');
                            i++;
                        }
                        continue;
                    }
                    out.append(source.charAt(i) == '\n' ? '\n' : ' ');
                    i++;
                }
                if (i < source.length()) {
                    out.append(' ');
                    i++;
                }
            } else {
                out.append(c);
                i++;
            }
        }
        return out.toString();
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
