package ai.univs.gate.support.privacy;

/**
 * 정리 대상 인증 이력 한 건 — id 와 <b>그 시도에서 올린 프로브 이미지</b> 경로 (UG-282).
 *
 * <p>{@code Object[]} 대신 레코드를 쓰는 이유는 {@code imagePath} 가 무엇인지 타입이 말해 주게
 * 하려는 것이다. 이 필드에 {@code feature_image_path} 가 들어오면 살아 있는 특징점의 사진이
 * 지워진다 — 배열 두 번째 칸이었다면 그 실수가 컴파일러에게도 읽는 사람에게도 보이지 않는다.
 *
 * @param id 지울 {@code match_history} 행
 * @param probeImagePath {@code matched_feature_image_path} — 이 행만 가리키는 파일이다.
 *                       동의를 받지 않은 프로젝트에서는 빈 문자열이다.
 */
public record MatchHistoryPurgeTarget(Long id, String probeImagePath) {}
