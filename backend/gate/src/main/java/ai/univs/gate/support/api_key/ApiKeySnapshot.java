package ai.univs.gate.support.api_key;

import ai.univs.gate.modules.api_key.domain.entity.ApiKey;
import ai.univs.gate.modules.project.domain.entity.Project;
import ai.univs.gate.modules.project.domain.enums.ProjectStatus;
import java.time.LocalDateTime;

/**
 * 캐시에 두는 API 키·프로젝트의 불변 사본 (UG-364).
 *
 * <p>엔티티를 그대로 캐시에 두면 여러 요청 스레드가 한 객체를 나눠 쓰고, 누군가 세터를 부르면 다른 요청에 번진다. 그래서 값만
 * 담고, 꺼낼 때마다 {@link #toApiKey} 로 <b>새 사본</b>을 만든다. 사본은 영속성 컨텍스트 밖(준영속)이다.
 *
 * <ul>
 *   <li>프로젝트에는 연관 관계가 없어 지연 로딩이 일어나지 않는다. 사본을 다른 엔티티의 {@code @ManyToOne} 참조로 저장하는
 *       것은 안전하다 — 식별자만 쓴다.
 *   <li>감사 컬럼({@code createdAt}·{@code createdBy} 등)은 담지 않는다. 조회 경로의 호출자는 읽지 않는다.
 *   <li>사본을 저장·병합하면 안 된다(삭제 여부 등을 낡은 값으로 되쓴다). 프로젝트를 저장하는 곳은 생성뿐이다.
 *   <li>객체 동일성으로 비교하지 않는다 — 사본은 요청마다 다른 객체다. 프로젝트 비교는 id 로 한다.
 * </ul>
 *
 * <p>조회 조건이 「활성 키 + 삭제되지 않은 프로젝트」라 {@code isActive=true}·{@code isDeleted=false} 인 것만 담긴다.
 */
public record ApiKeySnapshot(
        Long apiKeyId,
        String apiKey,
        String secretKey,
        LocalDateTime issuedAt,
        LocalDateTime expiresAt,
        Long projectId,
        Long accountId,
        String projectName,
        String projectDescription,
        String branchName,
        ProjectStatus status,
        String colorTag) {

    /** 조회한 엔티티에서 값을 옮긴다. 프로젝트가 이미 읽혀 있어야 한다(조회가 함께 가져온다). */
    static ApiKeySnapshot of(ApiKey key) {
        Project project = key.getProject();
        return new ApiKeySnapshot(key.getId(), key.getApiKey(), key.getSecretKey(), key.getIssuedAt(), key.getExpiresAt(),
                project.getId(), project.getAccountId(), project.getProjectName(), project.getProjectDescription(),
                project.getBranchName(), project.getStatus(), project.getColorTag());
    }

    /** 요청마다 새 사본. */
    ApiKey toApiKey() {
        Project project = Project.builder()
                .id(projectId)
                .accountId(accountId)
                .projectName(projectName)
                .projectDescription(projectDescription)
                .branchName(branchName)
                .isDeleted(false)
                .status(status)
                .colorTag(colorTag)
                .build();
        return ApiKey.builder()
                .id(apiKeyId)
                .project(project)
                .apiKey(apiKey)
                .secretKey(secretKey)
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .isActive(true)
                .build();
    }
}
