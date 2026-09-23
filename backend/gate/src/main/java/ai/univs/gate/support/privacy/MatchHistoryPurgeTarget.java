package ai.univs.gate.support.privacy;

import java.util.List;
import java.util.stream.Stream;

/**
 * 정리 대상 인증 이력 한 건과 그 행이 들고 있는 이미지 경로들 (UG-282).
 *
 * <p>여기서 주는 것은 <b>삭제 후보</b>일 뿐 삭제 대상이 아니다. 실제로 지울지는
 * {@link HistoryPurgeService} 가 <b>살아 있는 {@code biometric_feature} 가 그 경로를
 * 가리키는지</b> 로 정한다.
 *
 * <p><b>왜 {@code match_type} 으로 가르지 않는가.</b> 반박 리뷰가 두 번에 걸쳐 같은 결함을
 * 냈고, 두 번째가 그 방식이 원리적으로 안 된다는 것을 보였다.
 *
 * <ul>
 *   <li>초판 — {@code feature_image_path} 는 등록된 특징점의 경로를 복사한 값이니 절대 지우지
 *       않는다고 봤다. {@code VERIFY_IMAGE} 에서 거짓이었다. 그 행의 그 컬럼은 요청에서 올린
 *       <b>신분증 이미지</b>라, 남기면 영구 고아가 된다.
 *   <li>2판 — {@code VERIFY_IMAGE} 만 예외로 뒀다. 레거시 {@code VERIFY} 에서 거짓이었다.
 *       2026-05 이전에는 by-id 와 by-image 가 <b>같은 {@code VERIFY} 값</b>을 썼고(ec9e6d4 가
 *       분리), 앞쪽은 공유 경로, 뒤쪽은 전용 경로다. <b>같은 값의 행이 두 모양을 갖는다</b> —
 *       타입 화이트리스트로는 표현할 수 없다.
 * </ul>
 *
 * <p>그래서 타입이 아니라 <b>참조</b>로 정한다. 규칙 한 문장이고 모든 타입에, 레거시 행에도,
 * 앞으로 추가될 매칭 API 에도 그대로 성립한다 — <b>살아 있는 특징점이 가리키지 않는 파일만
 * 지운다.</b>
 */
public record MatchHistoryPurgeTarget(
        Long id,
        String matchedFeatureImagePath,
        String featureImagePath) {

    /**
     * 이 행이 들고 있는, 비어 있지 않은 이미지 경로들.
     *
     * <p>동의를 받지 않은 프로젝트는 {@code null}
     * ({@code FileService.uploadIfConsent}), 업로드 자체가 꺼진 환경
     * ({@code FILE_ENABLE_UPLOAD=false})은 빈 문자열이다.
     */
    public List<String> candidateImagePaths() {
        return Stream.of(matchedFeatureImagePath, featureImagePath)
                .filter(p -> p != null && !p.isBlank())
                .toList();
    }
}
