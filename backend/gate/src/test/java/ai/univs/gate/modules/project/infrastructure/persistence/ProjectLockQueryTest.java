package ai.univs.gate.modules.project.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.LockModeType;
import java.lang.reflect.Method;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Lock;

/**
 * UG-302: 프로젝트 잠금 조회가 실제로 <b>행 잠금</b>을 건다.
 *
 * <p>{@code RegenerateApiKeyUseCase} 와 {@code DeleteProjectUseCase} 의 단위 테스트는
 * {@code validateOwnershipForUpdate} 를 <b>부르는지</b>까지만 본다. 그 아래에서
 * {@code @Lock} 이 사라져도 목(mock)에는 아무 차이가 없어 전부 초록이다. 그런데 그
 * 애노테이션 한 줄이 이 수정의 전부다 — 없으면 이름만 {@code ForUpdate} 인 평범한 조회가 되어
 * 동시 재발급이 다시 활성 키 2개를 만든다.
 *
 * <p>동시성 자체를 재현하려면 진짜 DB 와 스레드 두 개가 필요하다. 이 프로젝트에는 아직 그
 * 인프라가 없다(UG-300 이 슬라이스 테스트 기반을 만드는 중이다). 그전까지는 잠금 <b>선언</b>을
 * 못박는 것이 실효 있는 최소 방어다.
 */
@DisplayName("UG-302: 프로젝트 잠금 조회 선언")
class ProjectLockQueryTest {

    @Test
    @DisplayName("findForUpdateByIdAndIsDeletedFalse 에 PESSIMISTIC_WRITE 가 걸려 있다")
    void 쓰기_잠금이_선언돼_있다() throws NoSuchMethodException {
        Method method = ProjectJpaRepository.class
                .getMethod("findForUpdateByIdAndIsDeletedFalse", Long.class);

        Lock lock = method.getAnnotation(Lock.class);
        assertThat(lock)
                .as("이 애노테이션이 없으면 이름만 ForUpdate 인 평범한 조회다 — 동시 재발급이 "
                        + "활성 키를 2개로 만들고, 그 순간 상세 조회와 재발급이 둘 다 막힌다")
                .isNotNull();
        assertThat(lock.value())
                .as("PESSIMISTIC_READ 는 다른 읽기를 막지 않으므로 두 요청이 함께 통과한다")
                .isEqualTo(LockModeType.PESSIMISTIC_WRITE);
    }

    /**
     * 애노테이션이 붙은 메서드가 실제로 <b>불리는지</b> (반박 리뷰 지적).
     *
     * <p>구현체가 잠그지 않는 쪽에 위임하도록 한 줄만 바꾸면 애노테이션은 그대로인 채 잠금이
     * 사라진다. 리뷰어가 그 변이를 심고 383개 테스트가 전부 초록인 것을, 그리고 그 상태에서
     * 실제 H2 2스레드로 활성 키가 2개가 되는 것을 확인했다.
     */
    @Test
    @DisplayName("구현체가 잠그는 조회에 위임한다")
    void 구현체가_잠그는_쪽에_위임한다() {
        ProjectJpaRepository jpa = org.mockito.Mockito.mock(ProjectJpaRepository.class);
        ProjectRepositoryImpl impl = new ProjectRepositoryImpl(
                jpa, org.mockito.Mockito.mock(ProjectDSLRepository.class));
        org.mockito.BDDMockito.given(jpa.findForUpdateByIdAndIsDeletedFalse(1L))
                .willReturn(java.util.Optional.empty());

        impl.findForUpdateByIdAndIsDeletedFalse(1L);

        org.mockito.Mockito.verify(jpa).findForUpdateByIdAndIsDeletedFalse(1L);
        org.mockito.Mockito.verify(jpa, org.mockito.Mockito.never())
                .findByIdAndIsDeletedFalse(1L);
    }

    /**
     * 잠그지 않는 조회도 그대로 남아 있어야 한다.
     *
     * <p>상세 조회 같은 읽기 경로까지 잠그면 재발급이 도는 동안 조회가 막힌다. 두 메서드가
     * 공존하는 것이 의도다 — 하나로 합치면 그 균형이 깨진다.
     */
    @Test
    @DisplayName("잠그지 않는 조회는 잠금 없이 남아 있다")
    void 읽기_경로는_잠그지_않는다() throws NoSuchMethodException {
        Method method = ProjectJpaRepository.class
                .getMethod("findByIdAndIsDeletedFalse", Long.class);

        assertThat(method.getAnnotation(Lock.class))
                .as("읽기 경로까지 잠그면 재발급 중에 프로젝트 조회가 대기한다")
                .isNull();
    }
}
