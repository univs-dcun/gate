package ai.univs.gate.modules.company.api.dto;

public record InitCompanyRequestDTO(
        Long accountId,
        String managerMail
) {
}
