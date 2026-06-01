package ai.univs.gate.modules.project.application.input;

public record UpdateLivenessSettingsInput(
        Long projectId,
        boolean livenessRegisterEnabled,
        boolean livenessIdentifyingEnabled,
        boolean livenessVerifyingByIdEnabled,
        boolean livenessVerifyingByImageEnabled
) {
}
