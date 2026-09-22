package ai.univs.gate.modules.feature.application.input;

import org.springframework.web.multipart.MultipartFile;

public record CreateFeatureInput(
        Long accountId,
        String apiKey,
        MultipartFile featureImage,
        String description,
        String transactionUuid,
        /** UG-333: 고객사 시스템의 사용자 식별자 (선택). 재등록 전후·얼굴/손바닥 특징점을 한 사람으로 잇는다. */
        String externalKey
) {
}
