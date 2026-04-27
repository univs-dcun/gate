package ai.univs.auth.infrastructure.persistence;

import ai.univs.auth.domain.entity.LoginLog;
import ai.univs.auth.domain.repository.LoginLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class LoginLogRepositoryImpl implements LoginLogRepository {

    private final LoginLogJpaRepository loginLogJpaRepository;

    @Override
    public LoginLog save(LoginLog loginLog) {
        return loginLogJpaRepository.save(loginLog);
    }
}
