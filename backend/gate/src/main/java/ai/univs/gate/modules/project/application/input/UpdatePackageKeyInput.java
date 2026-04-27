package ai.univs.gate.modules.project.application.input;

public record UpdatePackageKeyInput(
        Long accountId,
        Long projectId,
        String packageKey
) {
}
