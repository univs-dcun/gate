package ai.univs.gate.modules.project.application.input;

public record UpdateLivenessSettingsInput(
        Long projectId,
        boolean livenessRecordingEnabled,
        boolean livenessIdentifyingEnabled,
        boolean livenessVerifyingEnabled
) {
}
