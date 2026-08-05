package ai.univs.gate.facade.feature.infrastructure.persistence;

import ai.univs.gate.modules.feature.domain.enums.FeatureType;

import java.time.LocalDateTime;

/**
 * {@code featureType} 은 String 이 아니라 enum 이다 (UG-269).
 *
 * <p>통합 조회가 {@code bf.type} 을 그대로 select 하기 때문이다. String 으로 두면 QueryDSL 에서
 * {@code stringValue()} 캐스팅이 필요하고, 그 캐스팅이 방언마다 다른 SQL 로 나간다 —
 * 온프레미스가 Oracle 이므로 피하는 편이 안전하다.
 */
public record FeatureRow(
        FeatureType featureType,
        Long featureSeq,
        String description,
        String imagePath,
        String featureId,
        LocalDateTime createdAt
) {}
