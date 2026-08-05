package ai.univs.gate.modules.feature.application.result.face;

import ai.univs.gate.modules.feature.domain.entity.MatchHistory;
import ai.univs.gate.modules.feature.domain.enums.MatchType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * descriptor 기반 1:1 확인 결과.
 *
 * <p>UG-283: {@link IdentifyByDescriptorResult}(descriptor 1:N)와 <b>필드 구성을 동일하게</b>
 * 맞췄다. descriptor 계열 API 를 쓰는 클라이언트가 1:1 과 1:N 에서 서로 다른 응답 모양을 다뤄야 할
 * 이유가 없다.
 *
 * <p>이전 구조는 {@code (transactionUuid, similarity(String), result)} 3필드였다. 파괴적 변경이지만
 * 이 엔드포인트는 실사용 케이스가 없어 정리했다. similarity 는 face 원값 문자열("0.99803")이 아니라
 * 1:N 과 같이 {@link MatchHistory} 에 저장된 백분율({@code 99.80})을 쓴다.
 *
 * <p>{@code featureId} 는 <b>항상 빈 문자열</b>이다. 1:1 은 두 descriptor 를 직접 비교하는 것이라
 * 갤러리에서 등록 특징점을 찾지 않는다. 구조를 맞추려고 필드는 두되 값은 없다 — 이미지 기반 1:1 도
 * 같은 방식이다({@code MatchHistory.success(BigDecimal)} 오버로드가 featureId 를 비운다).
 *
 * <p>1:N 과 DTO 를 <b>공유하지 않고 분리</b>해 둔 이유는 향후 1:1 에서 featureId 를 뺄 수 있어야
 * 하기 때문이다. 대신 두 응답 DTO 의 필드 구성이 어긋나면 실패하는 테스트로 동기화를 강제한다
 * ({@code DescriptorResponseShapeTest}).
 */
public record VerifyByDescriptorResult(
        Long matchingHistoryId,
        Long projectId,
        MatchType matchType,
        LocalDateTime matchingTime,
        Boolean success,
        String featureId,
        BigDecimal similarity,
        String failureType,
        String transactionUuid
) {

    public static VerifyByDescriptorResult from(MatchHistory matchHistory) {
        return new VerifyByDescriptorResult(
                matchHistory.getId(),
                matchHistory.getProject().getId(),
                matchHistory.getMatchType(),
                matchHistory.getMatchTime(),
                matchHistory.getSuccess(),
                // 1:1 은 등록 사용자를 특정하지 않는다. MatchHistory 에는 null 이 들어 있으므로
                // 1:N 의 실패 응답과 같은 표현("")으로 통일한다.
                "",
                matchHistory.getSimilarity(),
                // 성공 시 MatchHistory.failureType 은 설정되지 않아 null 이다. 1:N 은 성공 응답에서
                // ""(빈 문자열)을 내보내므로 여기서도 맞춘다.
                matchHistory.getFailureType() != null ? matchHistory.getFailureType() : "",
                matchHistory.getTransactionUuid());
    }
}
