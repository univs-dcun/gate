package ai.univs.face.infrastructure.feign.extract.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExtractFeignResponseApi<T> {

    private String code;
    private String message;
    private T data;
}

