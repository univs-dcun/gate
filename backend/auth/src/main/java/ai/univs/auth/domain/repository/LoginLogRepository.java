package ai.univs.auth.domain.repository;

import ai.univs.auth.domain.entity.LoginLog;

public interface LoginLogRepository {

    LoginLog save(LoginLog loginLog);
}
