package ai.univs.gate.modules.user.application.usecase;

import ai.univs.gate.modules.user.application.input.CreateUserInput;
import ai.univs.gate.modules.user.application.result.UserResult;
import ai.univs.gate.shared.web.enums.CallerType;
import ai.univs.gate.support.file.FileService;
import ai.univs.gate.support.user.CreateUserServiceResult;
import ai.univs.gate.support.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class CreateUserUseCase {

    private final UserService userService;
    private final FileService fileService;

    @Transactional
    public UserResult execute(CreateUserInput input) {
        CreateUserServiceResult result = userService.createUser(
                CallerType.API,
                input.accountId(),
                input.apiKey(),
                input.faceImage(),
                input.description(),
                input.username(),
                input.transactionUuid());

        return UserResult.from(result.user(), result.livenessChecked(), fileService.getFileServerPath());
    }
}
