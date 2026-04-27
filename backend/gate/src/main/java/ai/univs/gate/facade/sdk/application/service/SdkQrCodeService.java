package ai.univs.gate.facade.sdk.application.service;

import ai.univs.gate.facade.sdk.domain.entity.SdkQrCode;
import ai.univs.gate.facade.sdk.domain.repository.SdkQrCodeRepository;
import ai.univs.gate.shared.exception.CustomGateException;
import ai.univs.gate.shared.web.enums.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SdkQrCodeService {

    private final SdkQrCodeRepository sdkQrCodeRepository;

    @Transactional(readOnly = true)
    public String getToken(String code) {
        SdkQrCode qrCode = sdkQrCodeRepository.findByCode(code)
                .orElseThrow(() -> new CustomGateException(ErrorType.INVALID_QR_CODE));

        // QR 만료 체크
        if (qrCode.isExpired()) {
            throw new CustomGateException(ErrorType.EXPIRED_QR_CODE);
        }

        // 이미 사용된 QR 체크
        if (Boolean.TRUE.equals(qrCode.getIsUsed())) {
            throw new CustomGateException(ErrorType.INVALID_QR_CODE);
        }

        return qrCode.getToken();
    }

    @Transactional
    public void consumeCode(String code) {
        SdkQrCode qrCode = sdkQrCodeRepository.findByCode(code)
                .orElseThrow(() -> new CustomGateException(ErrorType.INVALID_QR_CODE));

        // QR 사용 완료 처리
        qrCode.markAsUsed();
    }
}
