package ai.univs.gate.modules.api_key.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import ai.univs.gate.modules.api_key.domain.entity.ApiKey;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 어댑터가 {@code is_active} 를 어느 값으로 넘기는지 못박는다 (UG-288 델타 리뷰).
 *
 * <p>이 클래스는 테스트가 하나도 없었다. 그래서
 * {@code findAllByProjectIdAndIsActive(projectId, true)} 를 {@code false} 로 바꿔도 전 테스트가
 * 초록이었다. 그 변이는 <b>UG-288 의 원래 증상을 그대로 복원한다</b> — 삭제할 때 이미 꺼진 키만
 * 다시 끄고 살아 있는 키는 그대로 두므로, 삭제된 프로젝트의 키로 등록·매칭이 계속 된다.
 *
 * <p>메서드 이름이 {@code ...AndIsActive(id, boolean)} 라 참/거짓 어느 쪽도 컴파일된다는 점이
 * 위험하다. 이름이 {@code ...AndIsActiveTrue} 였다면 애초에 불가능한 변이다.
 *
 * <p>쿼리가 실제로 그 행을 가져오는지는 여기서 볼 수 없다 — 그건 JPA 슬라이스 테스트의 몫이다
 * (UG-300). 여기서 지키는 것은 "어느 인자로 물어보는가" 까지다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UG-288: API 키 리포지토리 어댑터")
class ApiKeyRepositoryImplTest {

    private static final long PROJECT = 42L;

    @Mock
    private ApiKeyJpaRepository apiKeyJpaRepository;

    @InjectMocks
    private ApiKeyRepositoryImpl apiKeyRepositoryImpl;

    @Test
    @DisplayName("활성 키 전체 조회는 is_active = true 로 묻는다")
    void 활성_전체조회는_true로_묻는다() {
        ApiKey 활성키 = ApiKey.builder().isActive(true).build();
        given(apiKeyJpaRepository.findAllByProjectIdAndIsActive(PROJECT, true))
                .willReturn(List.of(활성키));

        assertThat(apiKeyRepositoryImpl.findAllActiveByProjectId(PROJECT)).containsExactly(활성키);
    }

    @Test
    @DisplayName("활성 키 단건 조회도 is_active = true 로 묻는다")
    void 활성_단건조회도_true로_묻는다() {
        ApiKey 활성키 = ApiKey.builder().isActive(true).build();
        given(apiKeyJpaRepository.findByProjectIdAndIsActive(PROJECT, true))
                .willReturn(Optional.of(활성키));

        assertThat(apiKeyRepositoryImpl.findActiveByProjectId(PROJECT)).contains(활성키);
    }

    @Test
    @DisplayName("키 문자열 조회도 is_active = true 로 묻는다")
    void 키_문자열_조회도_true로_묻는다() {
        ApiKey 활성키 = ApiKey.builder().isActive(true).build();
        given(apiKeyJpaRepository.findByApiKeyAndIsActive("univs_live_abcdefghijklmnop", true))
                .willReturn(Optional.of(활성키));

        assertThat(apiKeyRepositoryImpl.findByApiKeyAndIsActiveTrue("univs_live_abcdefghijklmnop"))
                .contains(활성키);
    }
}
