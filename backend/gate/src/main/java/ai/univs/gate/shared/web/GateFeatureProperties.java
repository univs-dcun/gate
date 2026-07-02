package ai.univs.gate.shared.web;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 생체인증 modality(face/palm) 제공 여부 설정.
 * 온프레미스 설치 시 특정 modality만 배포되는 경우 해당 기능을 비활성화한다.
 * 기본값은 모두 true 로, 설정이 없으면 기존(클라우드) 동작과 동일하다.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "gate.features")
public class GateFeatureProperties {

    private boolean face = true;
    private boolean palm = true;
}
