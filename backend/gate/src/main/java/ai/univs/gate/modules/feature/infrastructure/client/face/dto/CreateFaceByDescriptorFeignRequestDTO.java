package ai.univs.gate.modules.feature.infrastructure.client.face.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * descriptor 기반 등록 요청 (UG-279).
 *
 * <p>{@link CreateFaceFeignRequestDTO} 와 달리 이미지와 라이브니스/다중 얼굴 플래그가 없다.
 * face-service 는 descriptor 를 받으면 추출 단계를 건너뛰므로 검사할 이미지가 존재하지 않는다.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateFaceByDescriptorFeignRequestDTO {

    private String branchName;
    private String descriptor;
    private String transactionUuid;
    private String clientId;
}
