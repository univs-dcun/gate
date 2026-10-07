package ai.univs.face.config;

import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * open-in-view 가 꺼져 있는가 (UG-358). 켜지면 요청 범위 EntityManager 가 트랜잭션 사이(fxp·match 호출 중)에도 살아 있어,
 * 읽기 유스케이스의 트랜잭션을 나눈 의미가 흐려진다. 반박 리뷰에서 이 값을 되돌려도 아무 테스트가 잡지 못했다.
 *
 * <p>프로파일과 무관한 첫 문서(공통)에 있어야 한다 — local·postgresql·oracle 모두에 적용되게.
 */
@DisplayName("UG-358: face 는 open-in-view 를 끈다")
class OpenInViewDisabledTest {

    @Test
    void 공통_문서에서_open_in_view_가_false() throws IOException {
        List<PropertySource<?>> documents = new YamlPropertySourceLoader()
                .load("application.yml", new ClassPathResource("application.yml"));

        PropertySource<?> common = documents.getFirst();
        assertThat(common.getProperty("spring.config.activate.on-profile")).isNull();
        assertThat(String.valueOf(common.getProperty("spring.jpa.open-in-view"))).isEqualTo("false");
    }
}
