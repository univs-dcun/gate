package ai.univs.gate.modules.project.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * UG-288: 삭제가 실제로 커밋되는지를 지키는 가드.
 *
 * <p>반박 리뷰가 찾은 생존 변이다. {@code DeleteProjectUseCase.execute} 에서
 * {@code @Transactional} 을 떼거나 {@code readOnly = true} 로 바꾸면 더티 체킹이 flush 되지 않아
 * <b>UG-288 과 똑같은 실패 모드</b>가 재현된다 — 삭제 API 가 200 을 주면서 아무것도 바꾸지 않는다.
 * 그런데 {@code DeleteProjectUseCaseTest} 는 순수 Mockito 테스트라 엔티티 객체의 필드 변화만 보고,
 * 그 변화가 커밋되는지는 보지 못한다. 두 변이 모두 초록으로 통과했다.
 *
 * <p>제대로 된 답은 JPA 슬라이스 테스트다 (UG-300). 그 인프라가 생기기 전까지, 최소한 애노테이션이
 * 조용히 사라지는 것은 여기서 막는다. 트랜잭션 경계가 실제로 동작하는지는 여전히 검증하지 못한다 —
 * 이 테스트가 지키는 것은 "선언이 남아 있는가" 까지다.
 */
@DisplayName("UG-288: 삭제 트랜잭션 선언 가드")
class DeleteProjectTransactionGuardTest {

    @Test
    @DisplayName("execute 는 쓰기 트랜잭션이어야 한다")
    void 삭제는_쓰기_트랜잭션이다() throws NoSuchMethodException {
        Method execute = DeleteProjectUseCase.class.getMethod("execute", Long.class, Long.class);
        Transactional transactional = execute.getAnnotation(Transactional.class);

        assertThat(transactional)
                .as("@Transactional 이 없으면 project.delete() 와 키 비활성화가 flush 되지 않는다 — "
                        + "삭제 API 가 200 을 주면서 아무 일도 하지 않게 된다 (UG-288 원래 증상)")
                .isNotNull();

        assertThat(transactional.readOnly())
                .as("readOnly = true 면 Hibernate 가 FlushMode.MANUAL 로 내려가 더티 체킹 결과가 "
                        + "커밋되지 않는다. 위와 같은 증상이다")
                .isFalse();

        assertThat(transactional.propagation())
                .as("트랜잭션 없이도 실행될 수 있는 전파 속성이면 같은 문제가 난다")
                .isIn(Propagation.REQUIRED, Propagation.REQUIRES_NEW, Propagation.MANDATORY);
    }
}
