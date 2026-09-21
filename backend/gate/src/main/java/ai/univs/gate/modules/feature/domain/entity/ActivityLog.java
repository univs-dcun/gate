package ai.univs.gate.modules.feature.domain.entity;

import ai.univs.gate.modules.feature.domain.enums.ActivitySource;
import ai.univs.gate.modules.feature.domain.enums.ActivityType;
import ai.univs.gate.modules.feature.domain.enums.FeatureType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.Subselect;
import org.hibernate.annotations.Synchronize;

/**
 * 인증 시도(match_history)와 특징점 사건(feature_history)을 한 목록으로 읽는 읽기 전용 엔티티 (UG-326).
 *
 * <p><b>왜 UNION 인가.</b> 화면은 하나(로그 상세)고 두 이력을 시간순으로 섞어 보여준다. 애플리케이션에서
 * 두 결과를 합치면 페이징이 깨진다 — 각 10건을 받아 합친 20건의 상위 10건이 실제 상위 10건이라는 보장이
 * 없다. {@code UNION ALL → ORDER BY → LIMIT} 은 DB 안에서 일어나야 한다.
 *
 * <p><b>왜 DB 뷰가 아니라 {@code @Subselect} 인가.</b> 뷰면 PG·Oracle 마이그레이션 두 벌과 방언 대조가
 * 늘고, H2 슬라이스에서는 {@code ddl-auto} 가 뷰 자리에 빈 테이블을 만들어 SQL 자체를 검증할 수 없다.
 * 인라인 서브셀렉트는 Hibernate 가 파생 테이블로 감싸 그 위에 QueryDSL where·order·paging 을 얹는다.
 * SQL 은 세 DB 에 공통인 문법만 쓴다 — {@code CAST(x AS VARCHAR(20))}, {@code ||},
 * {@code CAST(NULL AS DECIMAL(5,2))}. PG·Oracle 옵티마이저 모두 UNION ALL 가지 안으로 조건을 밀어 넣는다.
 * 열거형 컬럼(feature_type·match_type/action_type)은 양쪽 가지에서 VARCHAR 로 CAST 한다 — UNION 의 결과 타입은
 * 첫 가지에서 정해지는데, H2 는 Hibernate DDL 이 만든 ENUM 제약을 그대로 물려 둘째 가지의 'DELETE' 를 거부했다.
 * 운영(Flyway, VARCHAR)에서는 무해한 no-op 이고, 어느 DB 에서든 결과 타입을 명시하는 편이 맞다.
 *
 * <p><b>match_history 의 REGISTER 는 제외한다.</b> UG-325 가 그 행들을 feature_history 로 복사했고 V27 이
 * 원본을 지운다. 그래도 필터를 둔 이유는 어떤 경로로든 남은 REGISTER 행이 두 번 세어지지 않게 하려는
 * 것이다.
 *
 * <p>{@code id} 는 두 테이블이 공유하는 사건 시퀀스 {@code activity_seq} (UG-328, V29) — 통합 목록에서 유일하고
 * 시간순으로 단조 증가한다. 응답의 {@code sequence} 가 이것이고, {@code matchingHistoryId} 는 원 테이블의
 * 숫자 id({@link #sourceId})로 인증 API 응답의 같은 이름 필드와 뜻이 같다.
 */
@Entity
@Immutable
@Subselect("""
        SELECT mh.activity_seq                  AS activity_seq,
               'MATCH'                          AS source,
               mh.match_history_id              AS source_id,
               mh.project_id                    AS project_id,
               CAST(mh.feature_type AS VARCHAR(10))   AS feature_type,
               CAST(mh.match_type AS VARCHAR(20))     AS activity_type,
               mh.match_time                    AS event_time,
               mh.check_liveness                AS check_liveness,
               mh.success                       AS success,
               mh.feature_id                    AS feature_id,
               mh.feature_seq                   AS feature_seq,
               mh.user_description              AS user_description,
               mh.similarity                    AS similarity,
               mh.feature_image_path            AS feature_image_path,
               mh.matched_feature_image_path    AS matched_feature_image_path,
               mh.failure_type                  AS failure_type,
               mh.transaction_uuid              AS transaction_uuid,
               mh.consent_snapshot              AS consent_snapshot,
               mh.created_at                    AS created_at
          FROM match_history mh
         WHERE mh.match_type <> 'REGISTER'
        UNION ALL
        SELECT fh.activity_seq,
               'FEATURE',
               fh.feature_history_id,
               fh.project_id,
               CAST(fh.feature_type AS VARCHAR(10)),
               CAST(fh.action_type AS VARCHAR(20)),
               fh.created_at,
               fh.check_liveness,
               fh.success,
               fh.feature_id,
               fh.feature_seq,
               fh.user_description,
               CAST(NULL AS DECIMAL(5,2)),
               fh.feature_image_path,
               CAST(NULL AS VARCHAR(255)),
               fh.failure_type,
               fh.transaction_uuid,
               fh.consent_snapshot,
               fh.created_at
          FROM feature_history fh
        """)
@Synchronize({"match_history", "feature_history"})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ActivityLog {

    /** UG-328: 두 테이블이 공유하는 사건 시퀀스 (V29). 통합 목록의 일련번호이자 정렬 키. */
    @Id
    @Column(name = "activity_seq")
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "source")
    private ActivitySource source;

    @Column(name = "source_id")
    private Long sourceId;

    @Column(name = "project_id")
    private Long projectId;

    @Enumerated(EnumType.STRING)
    @Column(name = "feature_type")
    private FeatureType featureType;

    @Enumerated(EnumType.STRING)
    @Column(name = "activity_type")
    private ActivityType activityType;

    /** 인증은 match_time, 특징점 사건은 created_at. 목록 정렬은 {@link #createdAt} 으로 통일한다. */
    @Column(name = "event_time")
    private LocalDateTime eventTime;

    @Column(name = "check_liveness")
    private Boolean checkLiveness;

    @Column(name = "success")
    private Boolean success;

    @Column(name = "feature_id")
    private String featureId;

    @Column(name = "feature_seq")
    private Long featureSeq;

    @Column(name = "user_description")
    private String userDescription;

    @Column(name = "similarity")
    private BigDecimal similarity;

    @Column(name = "feature_image_path")
    private String featureImagePath;

    @Column(name = "matched_feature_image_path")
    private String matchedFeatureImagePath;

    @Column(name = "failure_type")
    private String failureType;

    @Column(name = "transaction_uuid")
    private String transactionUuid;

    @Column(name = "consent_snapshot")
    private Boolean consentSnapshot;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
