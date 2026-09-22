package ai.univs.gate.modules.project.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import ai.univs.gate.modules.project.application.input.UpdateProjectInput;
import ai.univs.gate.modules.project.application.result.ProjectResult;
import ai.univs.gate.modules.project.domain.entity.Project;
import ai.univs.gate.modules.project.domain.enums.ProjectStatus;
import ai.univs.gate.support.project.ProjectService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * UG-311: 수정은 삭제와 같은 <b>쓰기 잠금</b> 경로로 프로젝트를 읽어야 한다. 잠금 없는 {@code validateOwnership}
 * 으로 되돌아가면 삭제 커밋 뒤 더티 체킹이 {@code is_deleted} 를 되써 프로젝트가 살아난다.
 * 실제 DB 동작은 {@code ProjectUpdateDeleteRaceSliceTest} 가, 배선은 이 테스트가 못박는다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UpdateProjectUseCase — UG-311 잠금 배선")
class UpdateProjectUseCaseTest {

    @Mock
    private ProjectService projectService;

    @InjectMocks
    private UpdateProjectUseCase useCase;

    @Test
    @DisplayName("프로젝트를 쓰기 잠금으로 읽고(validateOwnershipForUpdate), 잠금 없는 경로는 쓰지 않는다")
    void 잠금_경로로_읽는다() {
        Project project = Project.builder().id(7L).accountId(100L).projectName("old").projectDescription("d")
                .branchName("b").isDeleted(false).status(ProjectStatus.ACTIVE).build();
        given(projectService.validateOwnershipForUpdate(7L, 100L)).willReturn(project);

        ProjectResult result = useCase.execute(new UpdateProjectInput(100L, 7L, "new", "desc", "#112233"));

        verify(projectService).validateOwnershipForUpdate(7L, 100L);
        verify(projectService, never()).validateOwnership(anyLong(), anyLong());
        assertThat(project.getProjectName()).isEqualTo("new");
        assertThat(result.projectName()).isEqualTo("new");
    }
}
