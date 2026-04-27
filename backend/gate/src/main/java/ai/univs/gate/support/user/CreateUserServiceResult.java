package ai.univs.gate.support.user;

import ai.univs.gate.modules.user.domain.entity.User;

public record CreateUserServiceResult(
        User user,
        boolean livenessChecked
) {

}
