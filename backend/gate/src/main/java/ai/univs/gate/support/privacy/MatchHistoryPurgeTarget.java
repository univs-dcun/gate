package ai.univs.gate.support.privacy;

import ai.univs.gate.modules.feature.domain.enums.MatchType;
import java.util.List;
import java.util.stream.Stream;

/**
 * 정리 대상 인증 이력 한 건과, <b>그 행이 지워질 때 함께 사라져야 하는 파일들</b> (UG-282).
 *
 * <p>이 레코드가 존재하는 이유는 {@link #ownedImagePaths()} 한 곳이다. 이력 행이 가진 두 이미지
 * 경로 중 <b>어느 쪽이 이 행만의 것인가</b> 는 {@code match_type} 에 따라 다르고, 그 판단을
 * 서비스 안에 흩뿌리면 다음 매칭 API 가 추가될 때 조용히 틀린다.
 *
 * <ul>
 *   <li>{@code matched_feature_image_path} — 그 시도에서 올린 프로브 이미지. 요청마다 새
 *       UUID 로 저장되므로({@code FileUtil.createImagePath}) 이 행만 가리킨다. <b>항상</b>
 *       이 행의 것이다.
 *   <li>{@code feature_image_path} — <b>대개는</b> 등록된 {@code biometric_feature} 의 경로를
 *       복사해 둔 값이다({@code MatchHistory.updateBiometricFeature}). 살아 있는 특징점과 다른
 *       이력 행이 같은 파일을 가리키므로 <b>지우면 안 된다.</b>
 * </ul>
 *
 * <p><b>예외가 하나 있고, 반박 리뷰가 그것을 잡았다.</b> {@link MatchType#VERIFY_IMAGE} 는
 * 사진 두 장을 받아 맞춰 보는 1:1 이라 등록된 특징점이 개입하지 않는다.
 * {@code FaceVerifyByFeatureImageUseCase} 는 {@code feature_image_path} 에 <b>그 요청에서 올린
 * 신분증(문서) 이미지</b>를 넣고, 성공 전이도 1:1 변형({@code success(BigDecimal)})이라 그 값이
 * 덮어써지지 않는다. 즉 이 행에서는 그 경로도 이 행만의 것이다.
 *
 * <p>그 한 줄을 놓치면 행이 지워진 뒤 <b>신분증 사진이 아무도 가리키지 않는 파일로 영구히
 * 남는다</b> — 개인정보를 지우는 것이 목적인 기능에서 가장 나쁜 실패이고, 하필 e-KYC 에서 가장
 * 민감한 파일이다.
 *
 * <p>{@link MatchType#REGISTER} 은 애초에 조회에서 빠진다 — 이유는
 * {@link HistoryPurgeRepository#findMatchHistoryToPurge} 참고.
 */
public record MatchHistoryPurgeTarget(
        Long id,
        MatchType matchType,
        String matchedFeatureImagePath,
        String featureImagePath) {

    /**
     * 이 행 말고는 아무도 가리키지 않는 파일들.
     *
     * <p>비어 있는 경로는 걸러 낸다. 동의를 받지 않은 프로젝트는 {@code null}
     * ({@code FileService.uploadIfConsent} 가 그렇게 돌려준다), 업로드 자체가 꺼진 환경은 빈
     * 문자열이다.
     */
    public List<String> ownedImagePaths() {
        Stream<String> paths = matchType == MatchType.VERIFY_IMAGE
                ? Stream.of(matchedFeatureImagePath, featureImagePath)
                : Stream.of(matchedFeatureImagePath);

        return paths.filter(p -> p != null && !p.isBlank()).toList();
    }
}
