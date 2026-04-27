package ai.univs.gate.modules.user.application.usecase;

import ai.univs.gate.modules.api_key.domain.entity.ApiKey;
import ai.univs.gate.modules.project.domain.entity.Project;
import ai.univs.gate.modules.user.application.input.UserQuery;
import ai.univs.gate.modules.user.application.result.GetUsersResult;
import ai.univs.gate.modules.user.application.result.UserResult;
import ai.univs.gate.modules.user.domain.entity.User;
import ai.univs.gate.modules.user.domain.repository.UserRepository;
import ai.univs.gate.shared.auth.UserContext;
import ai.univs.gate.shared.usecase.result.CustomPageResult;
import ai.univs.gate.support.api_key.ApiKeyService;
import ai.univs.gate.support.file.FileService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;

@Component
@RequiredArgsConstructor
public class GetUsersUseCase {

    private final UserRepository userRepository;
    private final ApiKeyService apiKeyService;
    private final FileService fileService;

    @Transactional(readOnly = true)
    public GetUsersResult execute(UserQuery query)  {
        UserContext userContext = UserContext.get();

        ApiKey apiKey = apiKeyService.findByApiKey(userContext.getApiKey());
        Project project = apiKey.getProject();
        long totalCount = userRepository.countByProjectIdAndIsDeletedFalse(project.getId());
        Page<User> users = userRepository.findAllByQuery(query, project.getId());

        if (users.isEmpty()) {
            return new GetUsersResult(Collections.emptyList(), CustomPageResult.of(Page.empty(), totalCount));
        }

        List<UserResult> contents = users.stream()
                .map(user -> UserResult.from(user, fileService.getFileServerPath()))
                .toList();
        CustomPageResult page = CustomPageResult.from(
                users.getPageable(),
                users.getTotalElements(),
                users.getTotalPages(),
                totalCount);

        return new GetUsersResult(contents, page);
    }
}
