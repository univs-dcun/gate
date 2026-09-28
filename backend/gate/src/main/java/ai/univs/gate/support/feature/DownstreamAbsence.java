package ai.univs.gate.support.feature;

import ai.univs.gate.shared.exception.CustomFeignException;
import java.util.Set;

/**
 * 하위 서비스가 "그런 특징점 없다" 고 답했는가 (UG-338).
 *
 * <p>삭제를 요청했는데 이 답이 오면 <b>이미 원하던 상태</b>다. 실패로 세면 수렴하지 않는다 — 다시 불러도
 * 매번 같은 답이 오기 때문이다. 이 판정을 쓰는 곳이 셋이라 한 곳에 둔다.
 *
 * <ul>
 *   <li>UG-303 {@code ProjectDataPurgeService} — 부분 실패 뒤 재시도가 수렴하게
 *   <li>UG-338 {@code DeleteFaceFeatureUseCase} — 클라이언트의 삭제 재시도가 수렴하게
 *   <li>UG-338 {@code OrphanRegistrationReconciler} — 등록이 하위에 닿지 않았던 행을 닫게
 *       ({@link #등록이_닿지_않았다} 로 더 넓게 본다)
 * </ul>
 *
 * <p><b>face 만 확인됐다.</b> match 가 {@code INVALID_FACE_ID}(MATCH-004)를 던지고 face 가 코드·유형을
 * 그대로 전파한다. palm 은 SmartFace 가 없는 id 에 무엇을 돌려주는지 확인하지 못했다 — 여기에 넣지
 * 않았으므로 palm 의 "없음" 은 실패로 남는다. 실제 로그에서 유형을 확인하면 이 목록에 추가한다.
 */
public final class DownstreamAbsence {

    private static final Set<String> 없음을_뜻하는_유형 = Set.of("INVALID_FACE_ID");

    private DownstreamAbsence() {
    }

    public static boolean 이미_없다(CustomFeignException e) {
        return 없음을_뜻하는_유형.contains(e.getType());
    }

    /**
     * 등록이 <b>확정되지 않은</b> 행에서만 쓰는 넓은 판정 — {@link #이미_없다} 에 브랜치 없음
     * ({@code EMPTY_GALLERY}, MATCH-001)을 더한다 (UG-338 반박 리뷰).
     *
     * <p>match 는 브랜치를 첫 등록 때 만들고 지우지 않는다. 그래서 새 프로젝트의 첫 등록이 match 에 닿지 않았다면
     * 정리 잡의 삭제는 이것을 받는다 — 정상적인 "없음" 이다.
     *
     * <p><b>삭제 유스케이스·퍼지에는 쓰지 않는다.</b> 거기서 대상은 gate 에 <b>성공 등록된</b> 특징점이다 — 그
     * 브랜치가 없다는 답은 match 데이터 유실이나 face 가 다른 match 를 보는 설정 오류라는 뜻이다. "없음" 으로
     * 받으면 gate 만 지우고 원래 match 에 템플릿이 남는다. 그 경우는 실패로 드러나야 한다.
     */
    public static boolean 등록이_닿지_않았다(CustomFeignException e) {
        return 이미_없다(e) || "EMPTY_GALLERY".equals(e.getType());
    }
}
