package ai.univs.gate.modules.user.application.result;

import ai.univs.gate.shared.usecase.result.CustomPageResult;

import java.util.List;

public record GetUsersResult(
        List<UserResult> users,
        CustomPageResult page
) {

}
