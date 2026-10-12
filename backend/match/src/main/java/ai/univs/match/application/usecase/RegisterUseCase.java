package ai.univs.match.application.usecase;

import ai.univs.match.application.result.MatchResult;
import ai.univs.match.application.service.RegisterService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class RegisterUseCase {

    private final RegisterService registerService;

    /**
     * {@code @Transactional} 을 두지 않는다 (UG-372). 트랜잭션은 {@link RegisterService} 가 브랜치를 정한 뒤에 연다 — 여기서
     * 열면 새 브랜치 생성이 바깥 커넥션을 쥔 채 커넥션을 하나 더 빌려, 첫 등록이 몰릴 때 풀이 교착된다.
     */
    public MatchResult execute(String branchName, String descriptor) {
        String faceId = UUID.randomUUID().toString();
        registerService.register(
                branchName,
                faceId,
                descriptor
        );

        return new MatchResult(branchName, faceId);
    }
}
