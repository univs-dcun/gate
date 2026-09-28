package ai.univs.gate.shared.modality;

import ai.univs.gate.modules.feature.domain.enums.FeatureType;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 이 배포가 제공하는 생체 인증 방식 (UG-223).
 *
 * <p>온프레미스는 face·palm 중 하나만 납품될 수 있다. 지금까지의 온프레미스 납품은 전부 face
 * 전용이다(palm-service 없음). 끈 쪽의 <b>동작</b> API 는 {@link ModalityGateInterceptor} 가
 * 거절한다.
 *
 * <p><b>기본값은 둘 다 켜짐이다.</b> 클라우드는 둘 다 배포돼 있고, 이 기능이 생기기 전과 동작이
 * 같아야 한다. 끄는 쪽이 설정을 준다 — 온프레미스는 compose 환경변수
 * {@code GATE_FEATURES_PALM=false} 로 준다.
 *
 * <p><b>공용 {@code gate-service.yml} 에 넣지 않는다.</b> 공용 파일은 모든 환경이 읽고 onprem
 * 저장소도 통째로 복사한다 — 거기 {@code false} 가 들어가면 클라우드까지 꺼진다. 켜고 끄는 결정은
 * 배포 쪽 소유다.
 *
 * <p>기동 시점에 읽는다. 바꾸면 재기동해야 한다.
 */
@ConfigurationProperties(prefix = "gate.features")
public record ModalityProperties(
        @DefaultValue("true") boolean face,
        @DefaultValue("true") boolean palm) {

    public boolean isEnabled(FeatureType modality) {
        return switch (modality) {
            case FACE -> face;
            case PALM -> palm;
        };
    }
}
