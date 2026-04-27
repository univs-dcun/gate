package ai.univs.gate.facade.sdk.application.result;

public record QrCodeResult(
        String base64QrCode,
        String link
) {
}
