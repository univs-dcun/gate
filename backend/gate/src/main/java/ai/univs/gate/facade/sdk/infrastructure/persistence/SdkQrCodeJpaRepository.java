package ai.univs.gate.facade.sdk.infrastructure.persistence;

import ai.univs.gate.facade.sdk.domain.entity.SdkQrCode;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SdkQrCodeJpaRepository extends JpaRepository<SdkQrCode, String> {
}
