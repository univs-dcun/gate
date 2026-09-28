package ai.univs.gate.modules.feature.domain.entity;

import static org.assertj.core.api.Assertions.assertThat;

import ai.univs.gate.modules.feature.domain.enums.FeatureType;
import ai.univs.gate.modules.project.domain.entity.Project;
import ai.univs.gate.shared.exception.RemoteCallException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * 등록 행의 발급 id 가 언제 남고 언제 지워지는가 (UG-338).
 *
 * <p>정리 잡은 <b>"등록 행에 id 가 남아 있고 성공이 아니면 결과를 모른다"</b> 는 불변식 하나로 대상을
 * 고른다. 이 불변식이 깨지는 방향은 둘이다.
 *
 * <ul>
 *   <li>결과를 모르는 행에서 id 를 지우면 — 고아를 영영 못 찾는다 (identify 실패·재등록 거절이 남는다)
 *   <li>확정 실패 행에 id 를 남기면 — 로그 상세에 "실패인데 id 가 있다" 로 나가고, 정리 잡이 헛되이 집는다
 * </ul>
 */
@DisplayName("UG-338: 등록 이력의 발급 id")
class FeatureHistoryIssuedIdTest {

    private static final String 발급 = "0f8fad5b-d9cb-469f-a165-70867728950e";
    private static final Project 프로젝트 = Project.builder().id(1L).accountId(9L).projectName("p").branchName("b").build();

    private static FeatureHistory 시작_행() {
        return FeatureHistory.register(프로젝트, FeatureType.FACE, false, null, "tx", true, 발급);
    }

    @Test
    @DisplayName("시작 행은 발급 id 를 들고 있다")
    void 시작_행은_id_를_든다() {
        assertThat(시작_행().getFeatureId()).isEqualTo(발급);
    }

    @Test
    @DisplayName("하위가 코드를 주며 거절하면(fail) id 를 지운다 — 그 id 로 등록된 것이 없다")
    void 확정_실패는_id_를_지운다() {
        FeatureHistory h = 시작_행();
        h.fail("FACE_NOT_FOUND");

        assertThat(h.getFeatureId()).isNull();
        assertThat(h.getFailureType()).isEqualTo("FACE_NOT_FOUND");
    }

    /**
     * UG-338 반박 리뷰: face·palm 은 그 아래 모듈의 5xx 를 400 + 오류 유형으로 바꿔 돌려준다. 코드가 있어도
     * 결과를 모른다 — match 가 커밋한 뒤 응답 중 실패했을 수 있다.
     */
    @ParameterizedTest
    @ValueSource(strings = {"INTERNAL_SERVER_ERROR", "SERVER_ERROR", "INTERNAL_ERROR"})
    @DisplayName("하위가 자기 오류 유형을 알리면 fail 이어도 id 를 남긴다")
    void 하위_오류_유형은_id_를_남긴다(String type) {
        FeatureHistory h = 시작_행();
        h.fail(type);

        assertThat(h.getFeatureId()).isEqualTo(발급);
        assertThat(h.getFailureType()).isEqualTo(type);
    }

    /**
     * <b>응답이 없으면 id 를 남긴다.</b> 읽기 타임아웃은 하위가 등록을 끝냈는데 응답만 늦은 경우일 수 있다.
     * 이 행에서 id 를 지우면 정리 잡이 그 고아를 찾지 못한다.
     */
    @Test
    @DisplayName("응답 없음·5xx(failUpstream) 는 id 를 남긴다 — 결과를 모른다")
    void 결과를_모르면_id_를_남긴다() {
        FeatureHistory h = 시작_행();
        h.failUpstream(new RemoteCallException(RemoteCallException.NO_RESPONSE, "face.createFace", new RuntimeException("read timeout")));

        assertThat(h.getFeatureId()).as("정리 잡이 이 id 로 하위를 지운다").isEqualTo(발급);
        assertThat(h.getFailureType()).isEqualTo("INTERNAL_SERVER_ERROR");
    }

    @Test
    @DisplayName("정리 잡이 닫으면 id 를 지우고, 시작 상태였으면 INTERNAL_SERVER_ERROR 로 닫는다")
    void 정리_잡이_닫는다() {
        FeatureHistory h = 시작_행();
        h.markReconciled();

        assertThat(h.getFeatureId()).isNull();
        assertThat(h.getFailureType()).isEqualTo("INTERNAL_SERVER_ERROR");
        assertThat(h.isSuccess()).isFalse();
    }

    @Test
    @DisplayName("정리 잡이 닫아도 이미 있던 하위 실패 사유는 덮지 않는다")
    void 기존_사유는_유지() {
        FeatureHistory h = 시작_행();
        h.failUpstream(new RemoteCallException(503, "face.createFace", null));
        h.markReconciled();

        assertThat(h.getFeatureId()).isNull();
        assertThat(h.getUpstreamStatus()).isEqualTo(503);
    }

    /** 삭제 행의 id 는 지우려던 실제 특징점이다 — 실패해도 남아야 무엇을 지우려 했는지 안다. */
    @Test
    @DisplayName("삭제 행은 실패해도 id 를 남긴다")
    void 삭제_행은_건드리지_않는다() {
        BiometricFeature f = BiometricFeature.builder().id(7L).project(프로젝트).type(FeatureType.FACE).featureId(발급).build();
        FeatureHistory h = FeatureHistory.delete(프로젝트, f, "tx");
        h.fail("SOMETHING");

        assertThat(h.getFeatureId()).isEqualTo(발급);
    }
}
