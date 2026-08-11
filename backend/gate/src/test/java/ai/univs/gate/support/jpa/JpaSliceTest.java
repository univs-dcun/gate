package ai.univs.gate.support.jpa;

import ai.univs.gate.shared.domain.JpaConfig;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * JPA 슬라이스 테스트의 공통 설정 (UG-300).
 *
 * <p>이 프로젝트에는 스프링 컨텍스트를 띄우는 테스트가 <b>하나도 없었다.</b> H2 가
 * {@code developmentOnly} 로만 선언돼 테스트 클래스패스에 오르지 않았고,
 * {@code src/test/resources} 는 비어 있었다. 그래서 다음이 전부 무검증이었다.
 *
 * <ul>
 *   <li>파생 쿼리 메서드 이름이 의도한 SQL 로 해석되는가 — 속성 경로 오타는 기동 시점에야 드러난다
 *   <li>QueryDSL 리포지토리의 where 절·조인·서브쿼리
 *   <li>연관 매핑·LAZY 로딩·cascade
 *   <li>소프트 삭제·소유 검증처럼 <b>조회 조건에 얹힌 보안 규칙</b>
 * </ul>
 *
 * <p><b>그 부재가 실제로 설계를 왜곡했다.</b> UG-288 에서 "삭제된 프로젝트의 API 키를 거부한다"
 * 를 구현할 때 가장 자연스러운 자리는 조회 쿼리였다. 그런데 그것을 검증할 테스트를 쓸 수 없어
 * 조회 후 자바 조건으로 확인하는 방식으로 되돌렸다. 보안 통제를 "더 나은 자리" 가 아니라
 * "검증 가능한 자리" 에 둔 것이다. 이런 선택이 반복되면 데이터 계층에는 아무 규칙도 두지
 * 못하게 된다.
 *
 * <p><b>왜 어노테이션 하나로 묶는가.</b> {@link DataJpaTest} 만으로는 뜨지 않는다. 두 가지가
 * 더 필요하다.
 * <ul>
 *   <li>{@link JpaConfig} — {@code @EnableJpaAuditing} 이 여기 있다. 없으면
 *       {@code BaseEntity} 의 {@code @CreatedDate}·{@code @CreatedBy} 가 채워지지 않아
 *       {@code NOT NULL} 위반으로 저장이 실패한다.
 *   <li>{@link StubAuditorAware} — 위 설정이 {@code auditorAwareRef = "auditorAwareImpl"} 를
 *       가리키는데, 실제 구현은 {@code @Component} 라 슬라이스 스캔 대상이 아니다. 그리고
 *       그것은 {@code UserContext} ThreadLocal 을 읽으므로 테스트에서 쓰기에 적절하지 않다.
 * </ul>
 * 매번 세 줄을 붙이게 하면 하나를 빠뜨린 테스트가 이유 모를 실패로 남는다.
 *
 * <p><b>이 인프라가 덮지 못하는 것.</b> 정직하게 적어 둔다.
 * <ul>
 *   <li><b>Flyway 마이그레이션과 엔티티의 정합.</b> 스키마를 {@code ddl-auto: create-drop} 으로
 *       엔티티에서 만든다. 마이그레이션 SQL 은 PostgreSQL·Oracle 전용이라 H2 에서 안 돈다.
 *       즉 컬럼을 추가하고 마이그레이션을 빠뜨려도 여기서는 초록이다.
 *   <li><b>방언 차이.</b> H2 의 {@code MODE=PostgreSQL} 은 흉내이지 PostgreSQL 이 아니다.
 *       부분 유니크 인덱스(UG-302), 타입 강제, Oracle 의 빈 문자열 = NULL(UG-297)은 재현되지
 *       않는다.
 * </ul>
 * 티켓은 Testcontainers 로 실제 PostgreSQL 을 띄우는 쪽을 권했고 그것이 더 낫다. 이 개발
 * 환경에서 docker 이미지를 받을 수 없어 채택하지 못했다. 받을 수 있게 되면 이 어노테이션의
 * 내용만 바꾸면 되도록 사용처를 여기 한 곳에 모아 뒀다.
 */
@Documented
@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@DataJpaTest
@ActiveProfiles("test")
@Import({JpaConfig.class, StubAuditorAware.class})
public @interface JpaSliceTest {
}
