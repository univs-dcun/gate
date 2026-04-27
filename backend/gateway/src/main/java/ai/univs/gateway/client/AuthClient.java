package ai.univs.gateway.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Map;

@Service
public class AuthClient {

    private final WebClient webClient;

    public AuthClient(WebClient.Builder builder,
                      @Value("${auth.service.url:http://auth-service}") String baseUrl
    ) {
        this.webClient = builder.baseUrl(baseUrl).build();
    }

    public Mono<AuthResponseDTO> validateToken(String accessToken, String language) {
        return webClient.post()
                .uri("/api/v1/auth/token/validate")
                .contentType(MediaType.APPLICATION_JSON)
                .headers(httpHeaders -> {
                    if (language != null) {
                        httpHeaders.add(HttpHeaders.ACCEPT_LANGUAGE, language);
                    }
                })
                .bodyValue(Map.of("accessToken", accessToken))
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<ResponseApi<AuthResponseDTO>>() {})
                .map(ResponseApi::getData);
    }
}
