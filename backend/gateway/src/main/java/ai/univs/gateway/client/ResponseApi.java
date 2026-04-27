package ai.univs.gateway.client;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ResponseApi<T> {

    private boolean success;
    private T data;
    private Object errors;
}
