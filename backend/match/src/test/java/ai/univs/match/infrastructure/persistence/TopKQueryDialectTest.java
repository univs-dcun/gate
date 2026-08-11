package ai.univs.match.infrastructure.persistence;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * UG-314 상위 k건 쿼리가 두 방언에서 <b>같은 것을 뜻하는지</b> 본다.
 *
 * <p>이 쿼리는 실행해 봐야 알 수 있는 것이 아니라 <b>모양이 틀리면 조용히 틀린 답을 준다</b>는
 * 점이 문제다. 오라클에서 {@code ROWNUM} 을 인라인 뷰 안쪽에 두면 정렬 전에 번호가 매겨져
 * "가장 가까운 k건" 이 아니라 "아무 k건" 이 나온다. 예외도 나지 않고 결과 건수도 맞아서
 * 통합 테스트가 없으면 프로덕션에서만 드러난다.
 *
 * <p>정렬 타이브레이커도 같은 성격이다. 거리 동률일 때 순서를 DB 가 정하면 같은 요청이 실행
 * 계획에 따라 다른 후보를 돌려준다. 1건만 쓰던 기존 1:N 에서는 잘 드러나지 않지만 k건을 자르는
 * 순간 "경계에 걸친 후보가 매번 바뀐다" 로 나타난다.
 *
 * <p>실제 DB 는 띄우지 않는다. {@link EntityManager} 를 가로채 <b>런타임에 실제로 넘어가는</b>
 * SQL 문자열을 본다 — 소스 파일을 텍스트로 읽는 방식은 주석이나 죽은 코드에도 반응한다.
 */
@DisplayName("UG-314: 상위 k건 쿼리의 방언별 형태")
class TopKQueryDialectTest {

    private static final Long BRANCH_ID = 7L;
    private static final byte[] BODY = new byte[512];
    private static final int VERSION = 512;
    private static final int LIMIT = 10;

    private EntityManager em;
    private Query query;

    @BeforeEach
    void 픽스처() {
        em = mock(EntityManager.class);
        query = mock(Query.class);

        when(em.createNativeQuery(anyString(), any(Class.class))).thenReturn(query);
        when(query.setParameter(anyString(), any())).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of());
    }

    private String 실행하고_SQL을_받는다(DescriptorCustomRepository repository) {
        repository.oneToManyMatchTopK(BRANCH_ID, BODY, VERSION, LIMIT);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(em).createNativeQuery(captor.capture(), any(Class.class));
        return captor.getValue();
    }

    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("postgresql — LIMIT 을 바인딩 파라미터로 건다")
    void postgresql_LIMIT() {
        String sql = 실행하고_SQL을_받는다(new PostgresqlDescriptorCustomRepositoryImpl(em));

        assertThat(sql)
                .as("건수를 문자열로 이어 붙이면 바인딩이 아니라 SQL 조립이 된다")
                .containsPattern(Pattern.compile("LIMIT\\s+:limit", Pattern.CASE_INSENSITIVE));
        verify(query).setParameter("limit", LIMIT);
    }

    @Test
    @DisplayName("oracle — ROWNUM 을 정렬된 인라인 뷰 '바깥' 에서 자른다")
    void oracle_ROWNUM_위치() {
        String sql = 실행하고_SQL을_받는다(new OracleDescriptorCustomRepositoryImpl(em));

        int 뷰_닫힘 = sql.lastIndexOf(')');
        int rownum = sql.toUpperCase().indexOf("ROWNUM");

        assertThat(rownum).as("ROWNUM 절이 없다").isGreaterThan(-1);
        assertThat(rownum)
                .as("ROWNUM 이 인라인 뷰 안쪽(닫는 괄호 %s 앞)에 있다. 정렬 전에 번호가 매겨져 "
                        + "'가장 가까운 %s건' 이 아니라 '아무 %s건' 이 나온다 — 예외도 안 나고 "
                        + "건수도 맞아서 눈에 띄지 않는다", 뷰_닫힘, LIMIT, LIMIT)
                .isGreaterThan(뷰_닫힘);

        assertThat(sql)
                .as("ROWNUM = 1 을 그대로 두면 limit 이 무시되고 항상 1건만 나온다")
                .containsPattern(Pattern.compile("ROWNUM\\s*<=\\s*:limit", Pattern.CASE_INSENSITIVE));
        verify(query).setParameter("limit", LIMIT);
    }

    @Test
    @DisplayName("두 방언 모두 거리 오름차순 + face_id 타이브레이커로 정렬한다")
    void 양쪽_정렬이_결정적이다() {
        String postgresql = 실행하고_SQL을_받는다(new PostgresqlDescriptorCustomRepositoryImpl(em));

        // 두 번째 실행을 위해 픽스처를 다시 만든다 (verify(em) 가 단일 호출을 전제한다)
        픽스처();
        String oracle = 실행하고_SQL을_받는다(new OracleDescriptorCustomRepositoryImpl(em));

        // 컬럼 별칭이 방언마다 달라(d.face_id vs faceId) 정규식으로 둘 다 받는다.
        Pattern 정렬 = Pattern.compile(
                "ORDER\\s+BY\\s+distance\\s+ASC\\s*,\\s*(?:d\\.)?face_?[Ii]d\\s+ASC",
                Pattern.CASE_INSENSITIVE);

        assertThat(postgresql)
                .as("거리만으로 정렬하면 동률 행의 순서를 DB 가 정한다. 경계에 걸친 후보가 "
                        + "같은 요청에서도 매번 바뀐다")
                .containsPattern(정렬);
        assertThat(oracle).containsPattern(정렬);
    }

    @Test
    @DisplayName("두 방언 모두 branch_id 로 갤러리를 가둔다")
    void 양쪽_모두_브랜치로_가둔다() {
        String postgresql = 실행하고_SQL을_받는다(new PostgresqlDescriptorCustomRepositoryImpl(em));
        verify(query).setParameter(eq("branchId"), eq(BRANCH_ID));
        assertThat(postgresql)
                .as("branch_id 조건이 빠지면 다른 테넌트의 얼굴이 후보로 올라온다")
                .containsPattern(Pattern.compile("branch_id\\s*=\\s*:branchId", Pattern.CASE_INSENSITIVE));

        픽스처();
        String oracle = 실행하고_SQL을_받는다(new OracleDescriptorCustomRepositoryImpl(em));
        verify(query).setParameter(eq("branchId"), eq(BRANCH_ID));
        assertThat(oracle)
                .containsPattern(Pattern.compile("branch_id\\s*=\\s*:branchId", Pattern.CASE_INSENSITIVE));
    }

    @Test
    @DisplayName("두 방언 모두 임계치를 SQL 로 내리지 않는다")
    void 임계치는_SQL에_없다() {
        String postgresql = 실행하고_SQL을_받는다(new PostgresqlDescriptorCustomRepositoryImpl(em));
        픽스처();
        String oracle = 실행하고_SQL을_받는다(new OracleDescriptorCustomRepositoryImpl(em));

        // 임계치를 SQL 로 내리려면 역변환으로 거리 컷을 계산해야 하는데, 그것은 5자리 반올림
        // '전' 값으로 자르는 것이라 기존 1:N 과 경계 판정이 어긋난다. 유사도 판정은 face 몫이다.
        for (String sql : List.of(postgresql, oracle)) {
            assertThat(sql.toLowerCase())
                    .as("임계치가 SQL 로 내려왔다 — 판정 위치가 face-service 와 이원화되면 같은 "
                            + "유사도에 두 API 가 다른 답을 낸다")
                    .doesNotContain("threshold");
        }
    }
}
