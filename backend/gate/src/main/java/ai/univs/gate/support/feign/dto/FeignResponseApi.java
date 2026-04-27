package ai.univs.gate.support.feign.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FeignResponseApi<T> {
    private boolean success;
    private T data;
    private FeignErrors errors;
}
