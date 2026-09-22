package ai.univs.gate.shared.exception;

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
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * 하위 서비스 실패가 이력을 지우지 못하게 하는 <b>주변 조건</b>을 지킨다 (UG-280, UG-293).
 *
 * <p><b>이 클래스에서 무엇이 빠졌는지가 중요하다.</b> 예전에는 여기에 "선언 검사" 가 있었다 —
 * {@code REQUIRES_NEW} 인 트랜잭션이 모두 {@code noRollbackFor} 에 {@code RemoteCallException}
 * 을 열거했는지 소스에서 문자열로 확인하는 검사였다.
 *
 * <p>그 검사가 지킬 수 있던 것은 "열거한 예외에서는 롤백하지 않는다" 까지다. <b>열거하지 않은
 * 예외</b>에서 무슨 일이 나는지는 보지 못했고, UG-280 의 반박 리뷰가 세 번 연속 찾아낸 것이
 * 정확히 그런 예외들이었다 — {@code RetryableException}, 본문 디코딩 실패, HTTP 200 의 빈
 * {@code data}, 그리고 우리 코드의 NPE.
 *
 * <p>UG-293 이 구조를 바꿔 이력을 호출자 트랜잭션 <b>밖에서</b> 커밋한다
 * ({@code HistoryRecorder}). 열거할 목록 자체가 없어졌으므로 선언 검사도 폐기했고,
 * {@code HistoryRecorderSliceTest} 가 실제 트랜잭션을 열고 롤백시켜 <b>행이 남는지</b> 를
 * 직접 본다.
 *
 * <p>여기 남은 것은 그 구조가 기대는 주변 조건 둘이다.
 * <ul>
 *   <li><b>예외 계층</b> — {@code RemoteCallException} 이 {@code BusinessException} 하위이고
 *       {@code CustomGateException} 과 형제여야 응답 계약이 유지된다.
 *   <li><b>응답 없는 실패 경로</b> — 모든 Feign 호출이 {@code RemoteCalls} 를 거쳐야
 *       {@code ErrorDecoder} 가 잡지 못하는 실패도 {@code RemoteCallException} 이 된다.
 * </ul>
 */
@DisplayName("하위 서비스 실패 처리의 주변 조건 (UG-280, UG-293)")
class RemoteCallRollbackGuardTest {

    private static final Path SOURCE_ROOT = Path.of("src/main/java");
    private static final String REQUIRED = "RemoteCallException";

    /** 엉뚱한 트리를 훑고 조용히 통과하는 것을 막는다. 한 곳을 통째로 지워도 통과하지 않도록 실제 수와 맞춘다. */
    private static final int MIN_SITES = 11;

    /** 같은 목적의 하한선. Feign 호출 지점이 사라지면 래핑 검사가 공회전한다. 현재 15곳. */
    private static final int MIN_FEIGN_CALLS = 15;

    private static final Pattern TRANSACTIONAL = Pattern.compile("@Transactional\\s*\\(");

    /**
     * 애노테이션 원문에서 주석을 지운다.
     *
     * <p>3차 반박 리뷰의 지적. 이 레포의 {@code @Transactional} 선언은 괄호 <b>안</b>에 UG-280 을
     * 설명하는 주석을 달고 있고, 그 주석에 {@code RemoteCallException} 이라는 낱말이 들어 있다.
     * 원문을 그대로 {@code contains} 하면 {@code noRollbackFor} 를 예전 상태로 되돌려도 주석 때문에
     * 계속 통과한다 — 이 가드가 막겠다고 선언한 바로 그 회귀를 못 잡는다.
     */
    private static final Pattern COMMENT = Pattern.compile(
            // 문자열 리터럴을 먼저 매칭해 통째로 보존한다. 이게 없으면 "https://..." 의 // 를
            // 주석으로 오인해 리터럴 뒷부분과 문장 종결 세미콜론까지 지운다 (실제로 이 레포의
            // SwaggerDescriptions·SwaggerConfig 에 그런 리터럴이 있다).
            //
            // UG-326: 리터럴은 "한 글자 = 재귀 한 단계" 가 아니라 런 단위로 소비한다. 예전 (?:\\.|[^"\\])* 는
            // 반복마다 자바 정규식 엔진이 한 프레임씩 재귀해, ActivityLog 의 2,100자 텍스트 블록에서 CI 워커
            // (스택이 로컬보다 작다)만 StackOverflowError 로 죽었다 — dev 빌드 #306·#308 이 그렇게 실패했다.
            // [^"\\]+ 는 이스케이프 사이의 긴 구간을 한 번에 먹으므로 깊이가 이스케이프 개수에 비례한다.
            // 텍스트 블록(""" ... """)은 앞에서 통째로 잡아, 세 따옴표가 빈 리터럴 + 새 리터럴로 쪼개지지 않게 한다.
            "\"\"\"(?:.*?)\"\"\"|\"(?:[^\"\\\\]+|\\\\.)*\"|//[^\\n]*|/\\*.*?\\*/", Pattern.DOTALL);

    /** 주석만 지우고 문자열 리터럴은 그대로 둔다. */
    private static String stripComments(String source) {
        Matcher m = COMMENT.matcher(source);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            m.appendReplacement(out, Matcher.quoteReplacement(
                    m.group().startsWith("\"") ? m.group() : ""));
        }
        m.appendTail(out);
        return out.toString();
    }

    /** {@code noRollbackFor = {A.class, B.class}} 와 중괄호 없는 단일 값 형태를 모두 받는다. */
    private static final Pattern NO_ROLLBACK_FOR =
            Pattern.compile("noRollbackFor\\s*=\\s*(\\{[^}]*}|[\\w.]+)");

    private record Site(Path file, int line, String annotation, String fileText) {
        /** 주석을 지운 애노테이션. 판정은 반드시 이쪽으로 한다. */
        String code() {
            return stripComments(annotation);
        }

        boolean isRequiresNew() {
            return code().contains("REQUIRES_NEW");
        }

        /** {@code noRollbackFor} 의 값 부분만. 선언이 없으면 빈 문자열. */
        String noRollbackFor() {
            Matcher m = NO_ROLLBACK_FOR.matcher(code());
            return m.find() ? m.group(1) : "";
        }

        /** 주석이 아니라 실제 {@code noRollbackFor} 목록에 들어 있는지. */
        boolean declaresRemoteCall() {
            return noRollbackFor().contains(REQUIRED + ".class");
        }

        String describe() {
            return "%s:%d".formatted(file, line);
        }
    }

    private static final Pattern REMOTE_CALLS_BEFORE =
            Pattern.compile("(?s).*RemoteCalls\\s*\\.\\s*\\w+\\s*$");

    /**
     * {@code callStart} 위치의 호출이 {@code RemoteCalls.xxx(...)} 안에 들어 있는지.
     *
     * <p>델타 검증의 지적. 처음에는 "문장 경계까지 거슬러 올라가 한 단계 되감기" 로 판정했는데
     * 오탐과 미탐을 동시에 만들었다 — 다중문 람다로 정상적으로 감싼 호출을 위반으로 잡고
     * ({@code () -> { var r = ...; return client.x(r); }}), 감싼 호출 바로 뒤에 있는 안 감싼
     * 호출은 앞 문장의 {@code RemoteCalls.} 를 보고 통과시켰다.
     *
     * <p>지금은 감싸는 스코프를 실제로 한 겹씩 벗겨 나간다. 여는 괄호를 만나면 그 앞이
     * {@code RemoteCalls.xxx} 인지 보고, 아니면 한 겹 더 바깥으로 간다. 여는 중괄호를 만나면
     * 람다 본문({@code ->} 뒤)일 때만 계속 나가고, 메서드·블록 본문이면 거기서 끝난다.
     */
    private static boolean wrappedByRemoteCalls(String text, int callStart) {
        int paren = 0;
        int brace = 0;
        for (int i = callStart - 1; i >= 0; i--) {
            char c = text.charAt(i);
            if (c == ')') {
                paren++;
            } else if (c == '(') {
                if (paren == 0) {
                    if (REMOTE_CALLS_BEFORE.matcher(text.substring(0, i)).matches()) {
                        return true;
                    }
                    // 다른 호출이었다 — 한 겹 더 바깥을 본다
                } else {
                    paren--;
                }
            } else if (c == '}') {
                brace++;
            } else if (c == '{') {
                if (brace == 0) {
                    // 람다 본문이면 계속 바깥으로, 아니면 여기가 스코프의 끝이다
                    return text.substring(0, i).stripTrailing().endsWith("->")
                            && wrappedByRemoteCalls(text, i);
                }
                brace--;
            }
        }
        return false;
    }

    /**
     * 이력을 쓰지 않는 {@code REQUIRES_NEW} — 이 가드의 대상이 아니다.
     *
     * <p>이 가드가 지키는 것은 "하위 서비스가 실패해도 <b>앞서 저장한 이력 행</b>이 살아남는
     * 다" 이다 (UG-280). 이력을 아예 쓰지 않는 트랜잭션에는 지킬 것이 없다.
     *
     * <p>목록으로 두는 이유는 실패 메시지가 요구하는 그대로다 — "조용히 빼지 말 것".
     * 한 줄을 추가하는 것은 "이 트랜잭션은 이력을 쓰지 않는다" 는 선언이고, 그 선언이 틀리면
     * 아래 단언이 깨진다.
     */
    private static final List<String> 이력_없는_REQUIRES_NEW = List.of(
            // UG-303: 삭제된 프로젝트의 생체 데이터를 지운다. 이력을 남기지 않고, 하위 서비스
            // 실패는 메서드 안에서 잡아 다음 특징점으로 넘어가므로 트랜잭션 경계를 넘지 않는다.
            "ProjectDataPurgeService.java");

    private static boolean 이력을_쓰지_않는다(Site site) {
        String file = site.file().getFileName().toString();
        if (!이력_없는_REQUIRES_NEW.contains(file)) {
            return false;
        }
        // 선언이 사실인지 확인한다. 나중에 이력을 쓰게 되면 면제가 조용히 유지되지 않는다.
        assertThat(site.fileText())
                .as("%s 를 '이력을 쓰지 않는다' 로 면제했는데 이력을 쓰고 있다. "
                        + "noRollbackFor 를 선언하거나 면제를 지울 것", file)
                .doesNotContain("matchHistoryRepository", "featureHistoryRepository")
                .doesNotContain("History.save(");
        return true;
    }

    /** {@code @Transactional(...)} 을 괄호 짝을 세어 통째로 잘라낸다 — 줄바꿈 위치와 무관하다. */
    private static List<Site> findTransactionalSites() throws IOException {
        List<Site> sites = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(SOURCE_ROOT)) {
            for (Path p : paths.filter(f -> f.toString().endsWith(".java")).toList()) {
                String text = Files.readString(p);
                Matcher m = TRANSACTIONAL.matcher(text);
                while (m.find()) {
                    int depth = 0;
                    int i = m.end() - 1;
                    for (; i < text.length(); i++) {
                        char c = text.charAt(i);
                        if (c == '(') {
                            depth++;
                        } else if (c == ')') {
                            depth--;
                            if (depth == 0) {
                                break;
                            }
                        }
                    }
                    String annotation = text.substring(m.start(), Math.min(i + 1, text.length()));
                    int line = (int) text.substring(0, m.start()).lines().count() + 1;
                    sites.add(new Site(p, line, annotation, text));
                }
            }
        }
        return sites;
    }

    @Nested
<<<<<<< HEAD
    @DisplayName("선언 검사")
    class Declarations {

        @Test
        @DisplayName("REQUIRES_NEW 트랜잭션은 모두 noRollbackFor 에 RemoteCallException 을 포함한다")
        void requiresNew_모두_원격실패를_포함한다() throws IOException {
            List<Site> requiresNew = findTransactionalSites().stream()
                    .filter(Site::isRequiresNew)
                    .toList();

            assertThat(requiresNew)
                    .as("REQUIRES_NEW 를 찾지 못했다면 SOURCE_ROOT(%s)가 잘못됐을 가능성이 크다", SOURCE_ROOT)
                    .hasSizeGreaterThanOrEqualTo(MIN_SITES);

            List<String> bad = requiresNew.stream()
                    .filter(s -> !s.declaresRemoteCall())
                    .filter(s -> !이력을_쓰지_않는다(s))
                    .map(Site::describe)
                    .toList();

            assertThat(bad)
                    .as("""
                            REQUIRES_NEW 인데 noRollbackFor 에 RemoteCallException 이 없는 곳이 있다 (UG-280).
                            하위 서비스가 실패하면 이 트랜잭션이 롤백되어 앞서 저장한 매칭 이력 행이 사라진다 —
                            장애를 가장 관측해야 할 때 기록이 없어진다. 선언을 다음 형태로 맞출 것:
                              noRollbackFor = {CustomFeignException.class, RemoteCallException.class}
                            이력을 쓰지 않는 REQUIRES_NEW 라면 그 사실을 주석으로 남기고 이 테스트를 함께
                            고칠 것 — 조용히 빼지 말 것.""")
                    .isEmpty();
        }

        @Test
        @DisplayName("RemoteCallException 을 선언한 파일은 그 예외를 catch 해 실패 사유를 남긴다")
        void 선언한_곳은_사유도_남긴다() throws IOException {
            // 행이 커밋돼도 failure_type 이 NULL 이면 응답을 받지 못하고 끊긴 요청과 구분되지 않는다.
            // UG-280 이 고친 것의 절반이 이쪽이라 선언만으로는 부족하다.
            List<String> missingCatch = findTransactionalSites().stream()
                    .filter(Site::declaresRemoteCall)
                    .filter(s -> !s.fileText().contains("catch (RemoteCallException"))
                    .map(Site::describe)
                    .distinct()
                    .toList();

            assertThat(missingCatch)
                    .as("""
                            noRollbackFor 에는 RemoteCallException 이 있는데 catch 가 없다.
                            행은 커밋되지만 failure_type 이 NULL 로 남아 원인을 알 수 없다.
                            Feign 호출을 감싸 matchHistory.fail(...) 을 남기고 rethrow 할 것.""")
                    .isEmpty();
        }

        @Test
        @DisplayName("여러 줄로 나눠 쓴 애노테이션도 정상 인식한다")
        void 여러줄_애노테이션_허용() throws IOException {
            // 괄호 짝을 세어 잘라내므로 줄바꿈 위치와 무관하다. 이 레포의 선언은 이미 여러 줄에
            // 걸쳐 있고, 한 줄만 보던 초기 버전은 포매터가 줄을 접으면 오탐을 냈다.
            List<Site> multiline = findTransactionalSites().stream()
                    .filter(s -> s.annotation().contains("\n"))
                    .toList();

            assertThat(multiline).isNotEmpty();
            assertThat(multiline.stream().filter(Site::isRequiresNew).toList())
                    .allSatisfy(s -> assertThat(s.noRollbackFor()).contains(REQUIRED + ".class"));
        }

        @Test
        @DisplayName("판정은 주석이 아니라 선언을 본다 — 이 가드 자신의 회귀 테스트")
        void 주석은_선언으로_치지_않는다() {
            // 3차 반박 리뷰: 이 레포의 선언은 괄호 안에 RemoteCallException 을 언급하는 주석을
            // 달고 있다. 원문을 그대로 contains 하면 noRollbackFor 를 되돌려도 통과해 버려서,
            // 가드가 존재하지만 아무것도 막지 못하는 상태가 된다. 파서를 직접 검증한다.
            Site 주석만 = new Site(SOURCE_ROOT, 1, """
                    @Transactional(
                            propagation = Propagation.REQUIRES_NEW,
                            // UG-280: RemoteCallException 이 목록에 있어야 이력이 커밋된다
                            noRollbackFor = {CustomFeignException.class}
                    )""", "");
            Site 선언까지 = new Site(SOURCE_ROOT, 1, """
                    @Transactional(
                            propagation = Propagation.REQUIRES_NEW,
                            // UG-280: RemoteCallException 이 목록에 있어야 이력이 커밋된다
                            noRollbackFor = {CustomFeignException.class, RemoteCallException.class}
                    )""", "");

            assertThat(주석만.isRequiresNew()).isTrue();
            assertThat(주석만.declaresRemoteCall())
                    .as("주석에만 있는 낱말을 선언으로 오인하면 가드가 무력화된다")
                    .isFalse();
            assertThat(선언까지.declaresRemoteCall()).isTrue();

            // 델타 검증의 지적: 위 픽스처만으로는 두 방어(주석 제거 / 값 부분만 추출)가 서로를
            // 가려서, 둘 중 하나만 되돌리는 뮤테이션이 살아남는다. 축을 분리한다.
            //
            // (1) noRollbackFor 밖에 낱말이 있는 형태 — "값만 추출" 이 없으면 통과해 버린다.
            Site 밖에만 = new Site(SOURCE_ROOT, 1, """
                    @Transactional(
                            propagation = Propagation.REQUIRES_NEW,
                            rollbackFor = RemoteCallException.class,
                            noRollbackFor = {CustomFeignException.class}
                    )""", "");
            assertThat(밖에만.declaresRemoteCall())
                    .as("noRollbackFor 가 아닌 다른 속성의 값을 선언으로 세면 안 된다")
                    .isFalse();

            // (2) 블록 주석 형태 — "주석 제거" 가 없으면 통과해 버린다.
            Site 블록주석 = new Site(SOURCE_ROOT, 1, """
                    @Transactional(
                            propagation = Propagation.REQUIRES_NEW,
                            noRollbackFor = {/* RemoteCallException.class 는 뺐다 */
                                    CustomFeignException.class}
                    )""", "");
            assertThat(블록주석.declaresRemoteCall())
                    .as("블록 주석 안의 낱말도 선언이 아니다")
                    .isFalse();
        }
    }

    @Nested
=======
>>>>>>> 8485a05 (refactor(gate): UG-293 이력 보존을 예외 타입 열거에서 커밋 경계로 바꾼다)
    @DisplayName("예외 계층")
    class Hierarchy {

        @Test
        @DisplayName("RemoteCallException 은 BusinessException 하위여야 한다 — 응답 계약 유지의 전제")
        void 응답_계약_유지() {
            // BusinessException 을 벗어나면 GlobalExceptionHandler 가 잡지 못해 500 이 나가고,
            // 기존 PJ-005 400 계약이 깨진다. 고객 코드가 PJ-005 로 분기하고 있다.
            assertThat(BusinessException.class).isAssignableFrom(RemoteCallException.class);
            assertThat(new RemoteCallException(503).getErrorType().getCode()).isEqualTo("PJ-005");
        }

        @Test
        @DisplayName("RemoteCallException 은 CustomGateException 과 별개 계층이어야 한다")
        void 별개_계층() {
            // 같은 계층이면 noRollbackFor 에 넣는 순간 CustomGateException 까지 커밋을 허용하게 되고,
            // FaceFeatureService 처럼 특징점과 이력을 함께 쓰는 경로에서 반쯤 등록된 특징점이 남는다.
            assertThat(CustomGateException.class.isAssignableFrom(RemoteCallException.class)).isFalse();
            assertThat(RemoteCallException.class.isAssignableFrom(CustomGateException.class)).isFalse();
        }

        @Test
        @DisplayName("응답을 받지 못한 실패는 NO_RESPONSE 로 구분된다")
        void 응답없음_구분() {
            assertThat(new RemoteCallException(RemoteCallException.NO_RESPONSE).isNoResponse()).isTrue();
            assertThat(new RemoteCallException(503).isNoResponse()).isFalse();
        }
    }

    @Nested
    @DisplayName("응답 없는 실패 경로")
    class NoResponsePath {

        @Test
        @DisplayName("Feign 호출은 모두 RemoteCalls 를 거친다 — ErrorDecoder 가 잡지 못하는 실패가 있다")
        void 모든_Feign_호출이_래핑된다() throws IOException {
            // ErrorDecoder 는 상태 코드 300 이상의 "응답이 도착했을 때만" 불린다. 연결 거부·타임아웃은
            // Feign 이 RetryableException 을 던지고, 그것은 BusinessException 계열이 아니라
            // noRollbackFor 에 걸리지 않는다. RemoteCalls 가 그 경계에서 번역한다.
            // 3차 반박 리뷰: 예전에는 (Face|Palm)Service.java 두 파일만, 그것도 한 줄 단위로 봤다.
            // 새 @FeignClient 를 다른 클래스에서 부르거나 호출을 줄바꿈하면 그대로 빠져나갔다.
            // 이제 @FeignClient 인터페이스 이름을 트리에서 모은 뒤, src/main 전체를 문장 단위로 본다.
            List<String> clientTypes = new ArrayList<>();
            List<Path> sources;
            try (Stream<Path> paths = Files.walk(SOURCE_ROOT)) {
                sources = paths.filter(f -> f.toString().endsWith(".java")).toList();
            }
            for (Path p : sources) {
                String text = Files.readString(p);
                if (text.contains("@FeignClient")) {
                    clientTypes.add(p.getFileName().toString().replace(".java", ""));
                }
            }
            assertThat(clientTypes)
                    .as("@FeignClient 인터페이스를 하나도 못 찾았다면 이 검사가 무의미하다")
                    .isNotEmpty();

            // 델타 검증의 지적: 필드명을 인터페이스명에서 유추하면(FaceClient → faceClient)
            // 관례를 벗어난 이름을 쓰는 순간 정규식이 0건 매칭하고 검사는 조용히 초록이 된다.
            // 그래서 선언된 "타입" 으로 필드명을 찾는다 — 이름이 무엇이든 걸린다.
            Pattern field = Pattern.compile(
                    "\\b(?:" + String.join("|", clientTypes) + ")\\s+(\\w+)\\s*[;,)=]");
            List<String> fieldNames = new ArrayList<>();
            for (Path p : sources) {
                Matcher fm = field.matcher(stripComments(Files.readString(p)));
                while (fm.find()) {
                    if (!fieldNames.contains(fm.group(1))) {
                        fieldNames.add(fm.group(1));
                    }
                }
            }
            assertThat(fieldNames)
                    .as("@FeignClient 타입으로 선언된 필드를 하나도 못 찾았다 — 검사가 무의미하다")
                    .isNotEmpty();

            Pattern call = Pattern.compile(
                    "\\b(" + String.join("|", fieldNames) + ")\\s*\\.\\s*\\w+\\s*\\(");

            int callSites = 0;
            List<String> unwrapped = new ArrayList<>();
            for (Path p : sources) {
                String text = stripComments(Files.readString(p));
                Matcher m = call.matcher(text);
                while (m.find()) {
                    callSites++;
                    if (!wrappedByRemoteCalls(text, m.start())) {
                        unwrapped.add(p + " — " + text.substring(m.start(), m.end()).strip());
                    }
                }
            }

            // 위반이 0건인 것과 볼 것이 0건인 것은 다르다. 후자를 초록으로 넘기면 이 가드는
            // 존재하되 아무것도 지키지 않는 상태가 된다 — UG-280 3차 리뷰가 잡은 실패 양상이다.
            assertThat(callSites)
                    .as("Feign 호출 지점을 %d 건밖에 못 찾았다. 현재 15건이다 — 검사 범위가 무너졌는지 볼 것",
                            callSites)
                    .isGreaterThanOrEqualTo(MIN_FEIGN_CALLS);

            assertThat(unwrapped)
                    .as("""
                            RemoteCalls 로 감싸지 않은 Feign 호출이 있다 (UG-280 반박 리뷰).
                            연결 거부·읽기 타임아웃은 ErrorDecoder 를 거치지 않으므로 RetryableException 이
                            그대로 올라오고, noRollbackFor 에 걸리지 않아 매칭 이력 행이 사라진다.
                            RemoteCalls.of("<서비스>.<메서드>", () -> client.xxx(...)) 형태로 감쌀 것.""")
                    .isEmpty();
        }
    }
}
