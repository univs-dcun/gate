package ai.univs.gate.modules.user.api.dto;

import ai.univs.gate.shared.web.dto.CustomPage;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UsersResponseDTO {

    private List<UserResponseDTO> content;
    private CustomPage page;
}
