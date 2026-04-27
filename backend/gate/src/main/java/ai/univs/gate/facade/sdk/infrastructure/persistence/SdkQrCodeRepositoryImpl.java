package ai.univs.gate.facade.sdk.infrastructure.persistence;

import ai.univs.gate.facade.sdk.domain.entity.SdkQrCode;
import ai.univs.gate.facade.sdk.domain.repository.SdkQrCodeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class SdkQrCodeRepositoryImpl implements SdkQrCodeRepository {

    private final SdkQrCodeJpaRepository sdkQrCodeJpaRepository;

    @Override
    public SdkQrCode save(SdkQrCode sdkQrCode) {
        return sdkQrCodeJpaRepository.save(sdkQrCode);
    }

    @Override
    public Optional<SdkQrCode> findByCode(String code) {
        return sdkQrCodeJpaRepository.findById(code);
    }
}
