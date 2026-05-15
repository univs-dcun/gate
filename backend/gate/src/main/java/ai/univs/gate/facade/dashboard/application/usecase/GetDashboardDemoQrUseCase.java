package ai.univs.gate.facade.dashboard.application.usecase;

import ai.univs.gate.support.qr.QrCodeService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class GetDashboardDemoQrUseCase {

    private final QrCodeService qrCodeService;

    @Value("${gate.dashboard.qr-url}")
    private String dashboardQrUrl;

    public byte[] execute() {
        return qrCodeService.generateForUrl(dashboardQrUrl);
    }
}
