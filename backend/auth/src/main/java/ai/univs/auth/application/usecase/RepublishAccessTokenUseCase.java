package ai.univs.auth.application.usecase;

import ai.univs.auth.application.exception.AccountNotFoundException;
import ai.univs.auth.application.exception.InvalidRefreshTokenException;
import ai.univs.auth.application.result.TokenResult;
import ai.univs.auth.application.service.JwtTokenProvider;
import ai.univs.auth.domain.entity.Account;
import ai.univs.auth.domain.entity.RefreshToken;
import ai.univs.auth.domain.repository.AccountRepository;
import ai.univs.auth.domain.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class RepublishAccessTokenUseCase {

    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenRepository refreshTokenRepository;
    private final AccountRepository accountRepository;

    @Transactional(readOnly = true)
    public TokenResult execute(String refreshToken) {
        jwtTokenProvider.validateRefreshToken(refreshToken);

        String jti = jwtTokenProvider.getJtiFromToken(refreshToken);
        Long accountId = jwtTokenProvider.getAccountIdFromToken(refreshToken);

        RefreshToken storedToken = refreshTokenRepository.findByJti(jti)
                .orElseThrow(InvalidRefreshTokenException::new);

        if (!storedToken.isValid()) {
            throw new InvalidRefreshTokenException();
        }

        Account account = accountRepository.findById(accountId)
                .orElseThrow(AccountNotFoundException::new);

        String newAccessToken = jwtTokenProvider.createAccessToken(account);

        log.info("Access token republished: accountId={}", accountId);
        return TokenResult.of(newAccessToken);
    }
}
