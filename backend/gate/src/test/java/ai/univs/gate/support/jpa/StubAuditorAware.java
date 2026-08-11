package ai.univs.gate.support.jpa;

import java.util.Optional;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.domain.AuditorAware;

/**
 * 슬라이스 테스트용 감사자 (UG-300).
 *
 * <p>{@code JpaConfig} 가 {@code auditorAwareRef = "auditorAwareImpl"} 를 가리키는데, 실제
 * {@code AuditorAwareImpl} 은 {@code @Component} 라 {@code @DataJpaTest} 슬라이스 스캔에 잡히지
 * 않는다. 빈 이름이 맞는 무언가가 없으면 컨텍스트가 아예 뜨지 않는다.
 *
 * <p>실제 구현을 그대로 가져다 쓰지 않는 이유: 그것은 {@code UserContext} ThreadLocal 을 읽는다.
 * 슬라이스 테스트에는 요청이 없어 항상 폴백({@code 0L})으로 떨어지고, 테스트가 ThreadLocal
 * 세팅에 의존하기 시작하면 순서 의존이 생긴다. 여기서는 <b>고정값</b>을 쓴다 — 감사 컬럼이
 * 채워진다는 사실만 필요하고, 누가 채웠는지는 슬라이스의 관심사가 아니다.
 */
@TestConfiguration
public class StubAuditorAware {

    /** 실제 구현과 구분되도록 일부러 {@code 0L}(SYSTEM)이 아닌 값을 쓴다. */
    public static final long 감사자 = 777L;

    /**
     * 빈 이름이 {@code auditorAwareImpl} 이어야 한다 — {@code JpaConfig} 의
     * {@code auditorAwareRef} 가 그 이름을 찾는다. 메서드 이름을 바꾸면 컨텍스트가 뜨지 않는다.
     */
    @Bean
    public AuditorAware<Long> auditorAwareImpl() {
        return () -> Optional.of(감사자);
    }
}
