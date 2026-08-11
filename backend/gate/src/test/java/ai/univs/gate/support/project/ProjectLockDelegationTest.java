package ai.univs.gate.support.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import ai.univs.gate.modules.project.domain.entity.Project;
import ai.univs.gate.modules.project.domain.repository.ProjectRepository;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * UG-302: 잠금이 <b>실제로 배선돼 있는지</b>.
 *
 * <p>{@code ProjectLockQueryTest} 는 리포지터리 메서드에 {@code @Lock} 이 붙어 있는지만 본다.
 * 반박 리뷰가 그 사이의 구멍을 변이로 증명했다 — {@code ProjectService} 나
 * {@code ProjectRepositoryImpl} 이 <b>잠그지 않는 쪽에 위임</b>하도록 한 줄만 바꾸면
 * 애노테이션은 그대로인 채 잠금이 사라지는데, 383개 테스트가 전부 초록이었다. 리뷰어가 실제
 * H2 로 2스레드를 돌려 그 상태에서 활성 키가 <b>2개</b>가 되는 것을 확인했다.
 *
 * <p>즉 이 수정은 세 조각이 이어져야 성립한다 — 애노테이션 · 구현체 위임 · 서비스 선택. 앞의
 * 하나만 못박고 있었다. 여기서 뒤의 둘을 못박는다.
 *
 * <p>진짜 동시성 테스트가 더 강한 검증이라는 점은 그대로다. 리뷰어가 {@code build.gradle} 에
 * {@code testRuntimeOnly 'com.h2database:h2'} 한 줄이면 오늘 가능함을 보였는데, 그 인프라는
 * UG-300(PR #175)이 세우고 있어 여기서 중복해 만들지 않았다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UG-302: 프로젝트 잠금 배선")
class ProjectLockDelegationTest {

    private static final long PROJECT = 42L;
    private static final long ACCOUNT = 7L;

    @Mock
    private ProjectRepository projectRepository;

    @InjectMocks
    private ProjectService projectService;

    private static Project 프로젝트() {
        return Project.builder().id(PROJECT).accountId(ACCOUNT).isDeleted(false).build();
    }

    @Test
    @DisplayName("validateOwnershipForUpdate 는 잠그는 조회를 쓴다")
    void 잠그는_경로는_잠그는_조회를_쓴다() {
        given(projectRepository.findForUpdateByIdAndIsDeletedFalse(PROJECT))
                .willReturn(Optional.of(프로젝트()));

        assertThat(projectService.validateOwnershipForUpdate(PROJECT, ACCOUNT)).isNotNull();

        verify(projectRepository)
                .findForUpdateByIdAndIsDeletedFalse(PROJECT);
        verify(projectRepository, never())
                .findByIdAndIsDeletedFalse(PROJECT);
    }

    /**
     * 반대 방향도 못박는다. 읽기 경로가 잠그기 시작하면 쓰기가 도는 동안 상세 조회가 대기한다 —
     * 잠금을 넓히는 실수는 좁히는 실수만큼 조용하다.
     */
    @Test
    @DisplayName("validateOwnership 은 잠그지 않는 조회를 쓴다")
    void 읽기_경로는_잠그지_않는_조회를_쓴다() {
        given(projectRepository.findByIdAndIsDeletedFalse(PROJECT))
                .willReturn(Optional.of(프로젝트()));

        assertThat(projectService.validateOwnership(PROJECT, ACCOUNT)).isNotNull();

        verify(projectRepository).findByIdAndIsDeletedFalse(PROJECT);
        verify(projectRepository, never()).findForUpdateByIdAndIsDeletedFalse(PROJECT);
    }
}
