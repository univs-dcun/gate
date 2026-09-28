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
}
