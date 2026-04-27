package ai.univs.gate.facade.sdk.domain.repository;

import ai.univs.gate.facade.sdk.domain.entity.SdkQrCode;

import java.util.Optional;

public interface SdkQrCodeRepository {

    SdkQrCode save(SdkQrCode sdkQrCode);

    Optional<SdkQrCode> findByCode(String code);
}
